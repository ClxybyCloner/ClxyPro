package com.clxypro.vpn.vpn

import com.clxypro.vpn.core.AppState
import com.clxypro.vpn.core.Prefs
import org.json.JSONObject
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket

/**
 * Asks an echo service which address the tunnel hands to the outside world and
 * where it lives. The request goes through the tunnel's exit SOCKS5 — the
 * address the service passes in (the Psiphon exit's 127.0.0.1:10829 while it
 * runs, else the core's own port): the tun builder keeps this app off the
 * tunnel, so a direct connection would report the provider's address instead
 * of the exit's. The hostname travels to the proxy unresolved, so the DNS
 * lookup happens on the far side too.
 *
 * NOTE (2026-11-27, rc6): probing aether's own SOCKS port while the exit ran
 * measured the MASQUE egress instead — a Cloudflare WARP IP that ip-api
 * reads as "Tehran, Iran" — and the Home screen showed it under a US flag.
 * The probe must follow core.proxyAddress, the same address the bridge and
 * the reachability checks already use.
 */
object ExitProbe {
    private const val HOST = "ip-api.com"
    private const val PATH = "/json/?fields=status,query,country,city"
    private const val ATTEMPTS = 3
    private const val RETRY_DELAY_MS = 4_000L
    private const val TIMEOUT_MS = 10_000

    /** Every start and every stop bumps this, so a late answer cannot land in a newer session. */
    @Volatile
    private var generation = 0

    fun start(proxyAddress: String) {
        val token = ++generation
        AppState.update { it.copy(exitIp = "", exitPlace = "") }
        Thread({ probe(token, proxyAddress) }, "exit-probe").apply { isDaemon = true }.start()
    }

    fun cancel() {
        generation++
    }

    private fun probe(token: Int, proxyAddress: String) {
        repeat(ATTEMPTS) { attempt ->
            if (token != generation) return
            if (attempt > 0) {
                runCatching { Thread.sleep(RETRY_DELAY_MS) }
                if (token != generation) return
            }
            // The tunnel may be reported up before its first packet goes through.
            val found = runCatching { fetch(proxyAddress) }.getOrNull() ?: return@repeat
            if (token != generation) return
            AppState.update { it.copy(exitIp = found.first, exitPlace = found.second) }
            return
        }
    }

    private fun fetch(proxyAddress: String): Pair<String, String> {
        val host = proxyAddress.substringBeforeLast(':').ifEmpty { Prefs.LOOPBACK }
        val port = proxyAddress.substringAfterLast(':').toIntOrNull() ?: Prefs.socksPort
        val proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress(host, port))
        Socket(proxy).use { sock ->
            sock.connect(InetSocketAddress.createUnresolved(HOST, 80), TIMEOUT_MS)
            sock.soTimeout = TIMEOUT_MS
            val request = "GET $PATH HTTP/1.0\r\nHost: $HOST\r\nUser-Agent: ClxyPro\r\n" +
                "Accept: application/json\r\nConnection: close\r\n\r\n"
            sock.outputStream.write(request.toByteArray())
            sock.outputStream.flush()
            val answer = String(sock.getInputStream().readBytes(), Charsets.UTF_8)
            val json = JSONObject(answer.substringAfter("\r\n\r\n", answer))
            check(json.optString("status") == "success")
            val ip = json.optString("query")
            check(ip.isNotBlank())
            val place = listOf(json.optString("city"), json.optString("country"))
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
                .joinToString(", ")
            return ip to place
        }
    }
}
