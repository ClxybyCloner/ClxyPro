package com.clxypro.vpn.core

import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Where the device really is, for the "Your Device" flag on the home card.
 *
 * The flag used to come from the system locale (via the SIM/network ISO
 * with a locale fallback) — but the locale only says what language the
 * phone speaks. A phone set to English (US) in Iran drew the US flag.
 * The locale is never a location signal, so it is not consulted here at
 * all.
 *
 * Instead the public IP's country is read once, at startup, from
 * Cloudflare's trace endpoint (no key, 5 s timeouts). It runs
 * fire-and-forget on a daemon thread so a slow lookup never holds up
 * the cores starting. If the lookup fails (offline, blocked) the origin
 * stays "AUTO" and the card draws the generic globe: no flag is better
 * than a wrong flag.
 *
 * The result is only applied while the tunnel is down. Once connected,
 * the public IP is the exit's, so a late answer must not overwrite the
 * origin with the exit's country.
 */
object OriginCountry {
    private val started = AtomicBoolean(false)

    fun detect() {
        if (!started.compareAndSet(false, true)) return
        Thread {
            val code = runCatching { lookup() }.getOrNull()
            if (code != null && !AppState.state.connected) {
                AppState.update { it.copy(originCode = code) }
            }
        }.apply { isDaemon = true; start() }
    }

    private fun lookup(): String? {
        val conn = (URL(TRACE_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            setRequestProperty("User-Agent", "ClxyPro")
        }
        try {
            if (conn.responseCode != HttpURLConnection.HTTP_OK) return null
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            return LOC_REGEX.find(body)?.groupValues?.getOrNull(1)
                ?.uppercase()?.takeIf { it.length == 2 }
        } finally {
            conn.disconnect()
        }
    }

    private const val TRACE_URL = "https://www.cloudflare.com/cdn-cgi/trace"
    private const val TIMEOUT_MS = 5_000
    private val LOC_REGEX = Regex("(?m)^loc=([A-Za-z]{2})$")
}
