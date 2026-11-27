package com.clxypro.vpn.core

import android.content.Context
import android.os.SystemClock
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Psiphon exit behind Quantum (CLXY Quantum = MASQUE transport, Psiphon egress).
 *
 * The wrapper binary (tools/psiphon/, shipped as libpsiphon.so) takes a `-config`
 * JSON file, serves a local SOCKS5 (TCP + UDP via its own udpgw) and a DNS
 * listener, and prints one JSON object per line on stdout:
 * `{"event":"ready"}` once the first tunnel is up,
 * `{"event":"error","message":...}` on a fatal failure. It exits when its stdin
 * closes. The public Psiphon server list comes from
 * app/src/main/assets/psiphon_config.json (verbatim copy of the public config
 * BPB-VPN ships; ClientPlatform/NetworkID are overridden to CLXY at runtime).
 *
 * UDP matters here: the tunnel bridge forwards DNS as SOCKS5 UDP, so if the
 * exit served TCP only, DNS would die — worse than Quantum today. The wrapper
 * serves UDP ASSOCIATE (udpgw.go), and that is what makes this a real exit.
 *
 * Region names are English-only in this first version (flag + English name);
 * localizing 31 country names is a polish step for after the phone test.
 */
class PsiphonExit {
    /** The SOCKS5 port the exit actually serves (0 until started). */
    @Volatile
    var exitSocksPort: Int = 0
        private set

    /** The DNS port the exit actually serves (0 until started). */
    @Volatile
    var exitDnsPort: Int = 0
        private set

    /** True once the exit reported ready and has not been stopped. */
    @Volatile
    var isRunning: Boolean = false
        private set

    /** "127.0.0.1:<port>" — what AetherCore.proxyAddress returns while the exit is on. */
    val exitAddress: String get() = "${Prefs.LOOPBACK}:$exitSocksPort"

    @Volatile
    private var process: Process? = null

    @Volatile
    private var ready = false

    @Volatile
    private var failure: String? = null

    @Volatile
    private var lastLine = ""

    /**
     * Blocking. Starts the exit against the MASQUE transport's SOCKS
     * ([upstreamSocks] like "socks5://127.0.0.1:1080"). Returns null on ready,
     * otherwise a "psiphon: ..." error string (FailureText translates it).
     */
    fun start(
        context: Context,
        upstreamSocks: String,
        region: String,
        timeoutMs: Long = START_TIMEOUT_MS,
        isStopping: () -> Boolean = { false },
    ): String? {
        val binary = File(context.applicationInfo.nativeLibraryDir, "libpsiphon.so")
        if (!binary.exists()) return "psiphon: engine missing (libpsiphon.so not installed)"
        stop()
        workDir(context).mkdirs()
        dataDir(context).mkdirs()
        exitSocksPort = LocalPorts.resolve(PREFERRED_EXIT_SOCKS) { AppState.log("psiphon: $it") }
        exitDnsPort = LocalPorts.resolve(PREFERRED_EXIT_DNS) { AppState.log("psiphon: $it") }
        ready = false
        failure = null
        lastLine = ""
        val configFile = try {
            writeConfig(context, upstreamSocks, region)
        } catch (t: Throwable) {
            return "psiphon: config: ${t.message ?: t.javaClass.simpleName}"
        }
        val child = try {
            ProcessBuilder(listOf(binary.absolutePath, "-config", configFile.absolutePath))
                .directory(workDir(context))
                .redirectErrorStream(true)
                .also { CaBundle.applyTo(it) }
                .start()
        } catch (t: Throwable) {
            return "psiphon: ${t.message ?: t.javaClass.simpleName}"
        }
        process = child
        pumpLogs(child)
        val startedAt = SystemClock.elapsedRealtime()
        while (!isStopping()) {
            if (ready && child.isAlive) {
                isRunning = true
                AppState.log("psiphon: exit ready on $exitAddress (region ${region.ifBlank { "auto" }})")
                return null
            }
            failure?.let {
                shutdown()
                return "psiphon: $it"
            }
            if (!child.isAlive) {
                val code = runCatching { child.exitValue() }.getOrNull()
                shutdown()
                return "psiphon: process exited ($code) ${lastLine.take(160)}".trim()
            }
            if (SystemClock.elapsedRealtime() - startedAt >= timeoutMs) {
                shutdown()
                return "psiphon: exit did not come up in ${timeoutMs / 1000}s"
            }
            try {
                Thread.sleep(250L)
            } catch (_: InterruptedException) {
                break
            }
        }
        shutdown()
        return null
    }

    /** Stops the exit: close stdin (the wrapper exits on its own), force-kill if it lingers. */
    fun stop() {
        val child = process ?: return
        process = null
        isRunning = false
        runCatching { child.outputStream.close() }
        runCatching { child.waitFor(GRACE_MS, TimeUnit.MILLISECONDS) }
        // The reader stays attached until the process is dead (EngineProc rule);
        // shutdown() only closes the streams once it is provably gone.
        EngineProc.shutdown(child, { AppState.log("psiphon: $it") }, "psiphon")
        lastLine = ""
        ready = false
        failure = null
    }

    private fun shutdown() {
        // Same as stop() but from inside start()'s failure paths.
        stop()
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
                        val event = runCatching { JSONObject(lastLine).optString("event") }.getOrDefault("")
                        when (event) {
                            "ready" -> {
                                ready = true
                                AppState.log("psiphon: ready")
                            }
                            "error" -> {
                                val message = runCatching { JSONObject(lastLine).optString("message") }.getOrDefault(lastLine)
                                failure = message.ifBlank { lastLine }
                                AppState.log("psiphon: error: $failure")
                            }
                            else -> {
                                // Psiphon notices stream here too; only log the
                                // ones that look like failures to keep the log readable.
                                if (lastLine.contains("error", ignoreCase = true) ||
                                    lastLine.contains("failed", ignoreCase = true)
                                ) {
                                    AppState.log("psiphon: $lastLine")
                                }
                            }
                        }
                    }
                }
            }.apply {
                isDaemon = true
                name = "psiphon-log"
                start()
            }
        )
    }

    private fun writeConfig(context: Context, upstreamSocks: String, region: String): File {
        val base = context.assets.open("psiphon_config.json").bufferedReader().use { it.readText() }
        val json = JSONObject(base)
        json.put("DataRootDirectory", dataDir(context).absolutePath)
        json.put("EgressRegion", region)
        json.put("UpstreamProxyURL", upstreamSocks)
        json.put("ClxySocksListen", "${Prefs.LOOPBACK}:$exitSocksPort")
        json.put("ClxyDnsListen", "${Prefs.LOOPBACK}:$exitDnsPort")
        json.put("ClientPlatform", "Android_CLXY")
        json.put("NetworkID", "clxy")
        val file = File(workDir(context), CONFIG_FILE)
        file.writeText(json.toString())
        return file
    }

    private fun workDir(context: Context): File = File(context.filesDir, "psiphon")

    private fun dataDir(context: Context): File = File(workDir(context), "data")

    companion object {
        const val START_TIMEOUT_MS = 90_000L
        private const val GRACE_MS = 3_000L
        private const val CONFIG_FILE = "config.json"
        private const val PREFERRED_EXIT_SOCKS = 10829
        private const val PREFERRED_EXIT_DNS = 10830

        /** Egress regions (ISO code to English name), same set BPB-VPN offers. */
        val REGIONS = listOf(
            "AT" to "Austria",
            "AU" to "Australia",
            "BE" to "Belgium",
            "BG" to "Bulgaria",
            "CA" to "Canada",
            "CH" to "Switzerland",
            "CZ" to "Czechia",
            "DE" to "Germany",
            "DK" to "Denmark",
            "EE" to "Estonia",
            "ES" to "Spain",
            "FI" to "Finland",
            "FR" to "France",
            "GB" to "United Kingdom",
            "HR" to "Croatia",
            "HU" to "Hungary",
            "IE" to "Ireland",
            "IN" to "India",
            "IT" to "Italy",
            "JP" to "Japan",
            "LV" to "Latvia",
            "NL" to "Netherlands",
            "NO" to "Norway",
            "PL" to "Poland",
            "PT" to "Portugal",
            "RO" to "Romania",
            "RS" to "Serbia",
            "SE" to "Sweden",
            "SG" to "Singapore",
            "SK" to "Slovakia",
            "US" to "United States",
        )

        /** Flag emoji for an ISO country code, via regional indicators. */
        fun flag(code: String): String {
            if (code.length != 2) return ""
            val sb = StringBuilder()
            code.uppercase().forEach { c ->
                sb.append(Character.toChars(0x1F1E6 + (c - 'A')))
            }
            return sb.toString()
        }
    }
}
