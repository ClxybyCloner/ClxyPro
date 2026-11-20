package com.clxypro.vpn.vpn

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.clxypro.vpn.R
import com.clxypro.vpn.core.CaBundle
import com.clxypro.vpn.core.EngineProc
import com.clxypro.vpn.core.FailureText
import com.clxypro.vpn.core.LocalPorts
import com.clxypro.vpn.core.Prefs
import java.io.File

/**
 * The ffvpn command line, bundled as libffvpn.so and exec'd from
 * nativeLibraryDir. [connect] returns once an exit is carrying traffic through
 * the engine's local SOCKS5 proxy; the caller then opens the TUN and hands it
 * to the tun2socks bridge.
 */
class FirefoxEngine(private val context: Context) {
    private val workDir: File = Store.workDir(context)
    private val binary: File = Store.binary(context)

    @Volatile
    private var child: Process? = null

    @Volatile
    private var stopping = false

    @Volatile
    private var listening = false

    @Volatile
    private var lastLine = ""

    /**
     * Elapsed time when the engine started its /session/status check, 0 while
     * it is not waiting on one. The check either answers in ~2 s or stalls
     * for the engine's whole 30 s timeout, so the poll loop cuts a stalled
     * check short instead of burning the full 30 s on every attempt.
     */
    @Volatile
    private var sessionCheckAt = 0L
    private var oauthCheckAt = 0L

    /** What the engine dialled last; the UI shows its country and latency. */
    @Volatile
    var selected: Candidate? = null
        private set

    /** The SOCKS port the engine actually bound (1080 unless it was busy). */
    @Volatile
    var socksPortUsed: Int = Prefs.socksPort
        private set

    /** Germany by country code, falling back to the country name. */
    private fun isGermany(server: Server): Boolean =
        server.country.equals("de", ignoreCase = true) ||
            server.countryName.equals("Germany", ignoreCase = true)

    /**
     * Blocking. Returns null on success, otherwise a human-readable error.
     * [onStage] receives the same stage names the other cores report, so the
     * service can show one kind of progress line for every engine. A [pinned]
     * exit is dialled alone: no list refresh, no probing, no fallback.
     */
    fun connect(onStage: (String) -> Unit, pinned: Server? = null): String? {
        stopping = false
        if (!binary.exists()) return context.getString(R.string.error_engine_missing)
        if (!Store.hasSession(context)) return context.getString(R.string.error_sign_in)
        // A previous run's orphaned engine still holds the SOCKS port when
        // the app was killed without a disconnect; reap it before dialling.
        EngineProc.reapOrphanedEngines { Logs.add("vpn", it) }

        val ranked: List<Candidate>
        if (pinned != null) {
            Logs.add("vpn", "dialing the chosen server ${pinned.label}")
            ranked = listOf(Candidate(pinned, Health.Alive, 0))
        } else {
            onStage("reading list")
            var servers = ServerList.fetch(binary, Store.cacheFile(context)) { stopping }
            if (stopping) return null
            if (servers.isEmpty()) servers = ServerList.readCache(Store.cacheFile(context))
            if (servers.isEmpty()) return context.getString(R.string.error_no_exits)

            onStage("checking servers")
            // Default exit is Germany (2026-11-21: the engine's latency-fastest
            // pick landed on a dead edge twice, while Germany connects in ~2 s).
            // Germany goes first; the ranked list stays behind it as fallback.
            val ordered = ServerList.rank(ServerList.probe(servers) { stopping }, Store.lastServer(context))
            val germanAt = ordered.indexOfFirst { isGermany(it.server) }
            val germanyFirst = if (germanAt > 0) {
                Logs.add("vpn", "default exit Germany first")
                listOf(ordered[germanAt]) + ordered.filterIndexed { i, _ -> i != germanAt }
            } else ordered
            ranked = germanyFirst.take(MAX_CANDIDATES)
            if (ranked.isEmpty()) return context.getString(R.string.error_no_exits)
        }

        var lastError: String? = null
        for (candidate in ranked) {
            if (stopping) return null
            onStage("dialing ${candidate.server.label}")
            val error = runCandidate(candidate)
            if (stopping) return null
            if (error == null) {
                selected = candidate
                Store.setLastServer(context, candidate.server.address)
                val latencyNote = if (candidate.latencyMs > 0) " (${candidate.latencyMs} ms)" else ""
                Logs.add("vpn", "using ${candidate.server.label}$latencyNote")
                return null
            }
            lastError = error
            Log.w(TAG, "${candidate.server.address} failed: $error")
            Logs.add("vpn", "${candidate.server.address} failed: $error")
        }
        return lastError ?: context.getString(R.string.error_no_exits)
    }

    /** Starts the engine for one exit and proves traffic through it. */
    private fun runCandidate(candidate: Candidate): String? {
        destroyChild()
        listening = false
        lastLine = ""
        sessionCheckAt = 0L
        oauthCheckAt = 0L

        // Never bind a port that is already taken: the engine would exit(1)
        // and every retry would fail the same way. The bridge, watchdog and
        // reachability checks all read the actual port back via the core.
        val port = LocalPorts.resolve(Prefs.socksPort) { Logs.add("vpn", it) }
        socksPortUsed = port
        val args = mutableListOf(
            binary.absolutePath,
            "--session-token-file", Store.sessionFile(context).absolutePath,
            "--server", candidate.server.address,
            "--socks", "127.0.0.1:$port"
        )

        val builder = ProcessBuilder(args)
            .directory(workDir)
            .redirectErrorStream(true)
        CaBundle.applyTo(builder)

        val process = try {
            builder.start()
        } catch (t: Throwable) {
            return t.message ?: t.javaClass.simpleName
        }
        child = process
        pump(process)

        val startedAt = SystemClock.elapsedRealtime()
        while (!stopping) {
            if (listening) {
                if (Traffic.serves(port, TRAFFIC_TIMEOUT_MS)) return null
                destroyChild()
                return context.getString(R.string.error_no_traffic)
            }
            if (!process.isAlive) {
                val code = runCatching { process.exitValue() }.getOrNull()
                // Wide enough for the tail that tells a timeout from a real
                // rejection ("context deadline exceeded" vs "401") — at 140
                // chars the line was cut mid-URL and FailureText could not
                // tell them apart.
                return "engine exited ($code) ${lastLine.take(400)}".trim()
            }
            // The session check either answers in ~2 s or stalls for the
            // engine's whole 30 s timeout (seen 2026-11-20: three 30 s
            // stalls, then a 2 s pass). Cut a stalled check short at 12 s
            // and let the retry ladder try again — a 2-minute connect
            // becomes ~40 s. Once "session OK" appears the cutoff is off.
            val checkAt = sessionCheckAt
            if (checkAt > 0L &&
                SystemClock.elapsedRealtime() - checkAt >= SESSION_CHECK_CUTOFF_MS
            ) {
                destroyChild()
                return FailureText.SESSION_TIMEOUT
            }
            // Same stall, one step later: /oauth/authorization also hangs
            // the engine's whole 30 s timeout on a filtered path (seen
            // 2026-11-21, after the session check itself was cached).
            // The desktop core caps the OAuth step at 15 s; do the same
            // here. Once "Entitlement:" appears the cutoff is off.
            val oauthAt = oauthCheckAt
            if (oauthAt > 0L &&
                SystemClock.elapsedRealtime() - oauthAt >= OAUTH_CUTOFF_MS
            ) {
                destroyChild()
                return FailureText.OAUTH_TIMEOUT
            }
            if (SystemClock.elapsedRealtime() - startedAt >= PER_SERVER_MS) {
                destroyChild()
                return context.getString(R.string.error_no_proxy, PER_SERVER_MS / 1000)
            }
            try {
                Thread.sleep(POLL_MS)
            } catch (_: InterruptedException) {
                break
            }
        }
        destroyChild()
        return null
    }

    private fun pump(process: Process) {
        val thread = Thread {
            runCatching {
                val reader = process.inputStream.bufferedReader()
                while (true) {
                    val line = runCatching { reader.readLine() }.getOrNull() ?: break
                    lastLine = line.trim()
                    if (lastLine.contains("Using cached session token")) {
                        sessionCheckAt = SystemClock.elapsedRealtime()
                    }
                    if (lastLine.contains("session OK")) {
                        sessionCheckAt = 0L
                        oauthCheckAt = SystemClock.elapsedRealtime()
                    }
                    if (lastLine.contains("Entitlement:")) oauthCheckAt = 0L
                    if (listeningLine.containsMatchIn(lastLine)) listening = true
                    if (quotaLine.containsMatchIn(lastLine)) {
                        Store.setQuota(context, lastLine.substringAfter("Bandwidth:").trim())
                    }
                    if (lastLine.isNotBlank()) {
                        Log.d(TAG, "engine: ${lastLine.take(200)}")
                        Logs.add("engine", lastLine)
                    }
                }
            }
        }.apply { isDaemon = true; name = "ffvpn-log"; start() }
        EngineProc.attach(process, thread)
    }

    /** True while the engine process still runs; used to notice a dead tunnel. */
    fun isAlive(): Boolean = child?.isAlive == true

    fun stop() {
        stopping = true
        destroyChild()
    }

    private fun destroyChild() {
        val process = child ?: return
        child = null
        EngineProc.shutdown(process, { Logs.add("vpn", it) }, "ffvpn")
        lastLine = ""
        listening = false
    }

    private companion object {
        const val TAG = "ffvpn"
        const val MAX_CANDIDATES = 8
        // The engine dials an exit for up to 30 s and its account calls can time
        // out twice before that, so a short window drops exits that still work.
        const val PER_SERVER_MS = 60_000L
        // Six times the ~2 s a healthy session check takes, a third of the
        // engine's 30 s stall.
        const val SESSION_CHECK_CUTOFF_MS = 12_000L
        /** Cap on the OAuth step (session OK → Entitlement), mirroring the desktop core's 15 s. */
        const val OAUTH_CUTOFF_MS = 15_000L
        const val TRAFFIC_TIMEOUT_MS = 30_000
        const val POLL_MS = 200L
        val listeningLine = Regex("""SOCKS5 proxy listening on""")
        val quotaLine = Regex("""Bandwidth: .*""")
    }
}
