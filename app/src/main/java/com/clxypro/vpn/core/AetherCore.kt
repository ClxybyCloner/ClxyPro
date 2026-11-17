package com.clxypro.vpn.core

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import com.clxypro.vpn.Texts
import com.multicore.vpn.tunnel.Tun2Socks
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Engine for open or lightly filtered networks (CLXY Quantum / Aether).
 * Features intelligent working peer caching: once connected, the working relay endpoint
 * is cached. On subsequent connects, if the cached endpoint is responsive to ping,
 * it performs a lightning-fast reconnect (--quick-reconnect) directly without a full scan.
 */
class AetherCore : VpnCore {
    override val id = "aether"
    override val proxyAddress get() = "$LOOPBACK:$socksPortUsed"

    /** The SOCKS port the engine actually bound (1080 unless it was busy). */
    @Volatile
    private var socksPortUsed: Int = Prefs.socksPort

    @Volatile
    private var context: Context? = null

    @Volatile
    private var listener: ((Int, String, String) -> Unit)? = null

    @Volatile
    private var process: Process? = null

    @Volatile
    private var stopping = true

    @Volatile
    private var vpnStarted = false

    @Volatile
    private var lastLine = ""

    @Volatile
    private var serving = false

    @Volatile
    private var discoveredPeer: String? = null

    private lateinit var prefs: SharedPreferences

    override fun init(context: Context) {
        this.context = context.applicationContext
        prefs = context.applicationContext.getSharedPreferences("aether_store", Context.MODE_PRIVATE)
        workDir.mkdirs()
        AppState.log("engine $id binary=${binary.exists()} abi=${android.os.Build.SUPPORTED_ABIS.firstOrNull()}")
    }

    override fun setSocketProtector(protector: ((Int) -> Boolean)?) = Unit

    override fun setStatusListener(listener: ((Int, String, String) -> Unit)?) {
        this.listener = listener
    }

    override fun connect(): String? {
        val executable = binary
        if (!executable.exists()) return Texts.engineMissing
        workDir.mkdirs()
        stopping = false
        stopVpn()
        discoveredPeer = null
        // Same bind failure as the Firefox core: reap a previous run's
        // orphaned engine, then bind a port that is actually free.
        EngineProc.reapOrphanedEngines { AppState.log("engine $id: $it") }
        socksPortUsed = LocalPorts.resolve(Prefs.socksPort) { AppState.log("engine $id: $it") }

        // 1. FAST RECONNECT: Check if we have a previously cached working peer
        val cached = prefs.getString(KEY_CACHED_PEER, null)
        if (!cached.isNullOrBlank()) {
            emit(1002, "testing cached relay", cached)
            if (isPeerReachable(cached, PING_TIMEOUT_MS)) {
                emit(1002, "fast reconnect", cached)
                AppState.log("engine $id: cached relay $cached is reachable, initiating fast reconnect")
                val fastArgs = commonArgs() + listOf("--quick-reconnect", "--peer", cached, "--masque", "--h2")
                val fastError = runStage(executable, fastArgs, FAST_RECONNECT_MS)
                if (stopping) return null
                if (fastError == null) {
                    emit(1004, "ready", "")
                    AppState.log("engine $id: fast reconnect successful via cached relay $cached")
                    return null
                }
                AppState.log("engine $id: fast reconnect failed ($fastError), proceeding to full scan")
            } else {
                AppState.log("engine $id: cached relay $cached did not answer ping, running scan")
            }
        }

        // 2. HTTP/2 Scan Stage
        emit(1002, "http2 scan", "")
        val first = runStage(executable, http2Args(), HTTP2_STAGE_MS)
        if (stopping) return null
        if (first == null) {
            emit(1004, "ready", "")
            return null
        }
        AppState.log("engine $id http2 stage: $first")

        // 3. Automatic / HTTP/3 Scan Stage
        emit(1002, "automatic scan", "")
        val second = runStage(executable, automaticArgs(), AUTO_STAGE_MS)
        if (stopping) return null
        if (second == null) {
            emit(1004, "ready", "")
            return null
        }
        AppState.log("engine $id automatic stage: $second")
        return Texts.endpointNotFound
    }

    override fun disconnect() {
        stopping = true
        stopVpn()
        destroyProcess()
    }

    override fun startVpn(fd: Int): String? {
        val config = writeTunnelConfig()
        val started = try {
            Tun2Socks.TProxyStartService(config.absolutePath, fd)
        } catch (t: Throwable) {
            AppState.log("engine $id tunnel bridge: ${t.message ?: t.javaClass.simpleName}")
            false
        }
        if (!started) {
            return Texts.tunnelFailed
        }
        vpnStarted = true
        repeat(15) {
            Thread.sleep(100L)
            if (runCatching { Tun2Socks.TProxyIsRunning() }.getOrDefault(false)) {
                AppState.log("engine $id tunnel bridge started")
                return null
            }
        }
        stopVpn()
        return Texts.tunnelFailed
    }

    override fun stopVpn() {
        if (!vpnStarted) return
        vpnStarted = false
        runCatching { Tun2Socks.TProxyStopService() }
            .onFailure { AppState.log("engine $id tunnel stop: ${it.message}") }
    }

    override fun isRunning(): Boolean {
        val alive = process?.isAlive == true
        return alive || (vpnStarted && runCatching { Tun2Socks.TProxyIsRunning() }.getOrDefault(false))
    }

    override fun isVpnRunning(): Boolean =
        vpnStarted && runCatching { Tun2Socks.TProxyIsRunning() }.getOrDefault(false)

    private fun runStage(executable: File, args: List<String>, timeoutMs: Long): String? {
        destroyProcess()
        serving = false
        discoveredPeer = null
        val startedAt = SystemClock.elapsedRealtime()
        val child = try {
            ProcessBuilder(listOf(executable.absolutePath) + args)
                .directory(workDir)
                .redirectErrorStream(true)
                .also { CaBundle.applyTo(it) }
                .start()
        } catch (t: Throwable) {
            return t.message ?: t.javaClass.simpleName
        }
        process = child
        pumpLogs(child)
        while (!stopping) {
            if (!child.isAlive) {
                val code = runCatching { child.exitValue() }.getOrNull()
                return "process exited ($code) ${lastLine.take(160)}".trim()
            }
            if (serving) {
                // If a working peer was found, cache it for instant reconnect next time
                discoveredPeer?.let { peer ->
                    prefs.edit().putString(KEY_CACHED_PEER, peer).apply()
                    AppState.log("engine $id: cached working relay $peer for fast future reconnects")
                }
                return null
            }
            if (SystemClock.elapsedRealtime() - startedAt >= timeoutMs) {
                destroyProcess()
                return "scan timed out after ${timeoutMs / 1000}s"
            }
            try {
                Thread.sleep(250L)
            } catch (_: InterruptedException) {
                break
            }
        }
        destroyProcess()
        return null
    }

    private fun pumpLogs(child: Process) {
        EngineProc.attach(
            child,
            Thread {
                runCatching {
                    val reader = child.inputStream.bufferedReader()
                    while (true) {
                        val line = runCatching { reader.readLine() }.getOrNull() ?: break
                        lastLine = line.trim()
                        if (lastLine.contains(SERVING_MARKER, ignoreCase = true)) {
                            serving = true
                        }

                        // Inspect logs for working peer / endpoint
                        val match = PEER_REGEX.find(lastLine)
                        if (match != null) {
                            val candidate = match.value
                            if (!candidate.startsWith("127.0.0.1") && !candidate.startsWith("0.0.0.0")) {
                                discoveredPeer = candidate
                            }
                        }

                        AppState.log("engine $id: $lastLine")
                    }
                }
            }.apply {
                isDaemon = true
                name = "aether-log"
                start()
            }
        )
    }

    private fun destroyProcess() {
        val child = process ?: return
        process = null
        EngineProc.shutdown(child, { AppState.log("engine $id $it") }, "aether")
        lastLine = ""
        serving = false
    }

    private fun emit(code: Int, message: String, data: String) {
        listener?.invoke(code, message, data)
    }

    private fun isPeerReachable(peer: String, timeoutMs: Int): Boolean {
        return runCatching {
            val parts = peer.split(':')
            val host = parts[0]
            val port = parts.getOrNull(1)?.toIntOrNull() ?: 443
            Socket().use { sock ->
                sock.connect(InetSocketAddress(host, port), timeoutMs)
                true
            }
        }.getOrDefault(false)
    }

    private fun http2Args(): List<String> = commonArgs() + listOf("--masque", "--h2")

    private fun automaticArgs(): List<String> = commonArgs() + listOf("--masque", "--h3")

    private fun commonArgs(): List<String> = listOf(
        "--bind", "$LOOPBACK:$socksPortUsed",
        "--config", File(workDir, IDENTITY_FILE).absolutePath,
        "--log-level", "info",
        "--scan", "balanced"
    )

    private fun writeTunnelConfig(): File {
        val file = File(workDir, TUNNEL_FILE)
        file.writeText(
            """
            tunnel:
              mtu: 1500
              ipv4: 172.19.0.2
              ipv6: 'fd00::2'
              icmp: 'reply'
            socks5:
              address: $LOOPBACK
              port: $socksPortUsed
              udp: 'udp'
            misc:
              task-stack-size: 32768
              log-level: warn
            """.trimIndent()
        )
        return file
    }

    private val appContext: Context
        get() = context ?: error("AetherCore.init() was not called")

    private val workDir: File
        get() = File(appContext.filesDir, "aether")

    private val binary: File
        get() = File(appContext.applicationInfo.nativeLibraryDir, "libaether.so")

    private companion object {
        const val LOOPBACK = "127.0.0.1"
        const val IDENTITY_FILE = "identity.toml"
        const val TUNNEL_FILE = "tunnel.yml"
        const val SERVING_MARKER = "socks5 server listening"
        const val KEY_CACHED_PEER = "cached_working_peer"
        const val PING_TIMEOUT_MS = 1500
        const val FAST_RECONNECT_MS = 12_000L
        const val HTTP2_STAGE_MS = 90_000L
        const val AUTO_STAGE_MS = 240_000L
        val PEER_REGEX = Regex("""(?:\d{1,3}\.){3}\d{1,3}:\d{2,5}""")
    }
}
