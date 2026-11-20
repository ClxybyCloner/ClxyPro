package com.clxypro.vpn.core

import android.content.Context
import android.util.Base64
import java.io.File
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.net.URLDecoder

/**
 * Quantum's relay list, ported from the Windows app (ClxyProPc v1.5.25).
 *
 * Source: the Amir subscription — a base64 body of one `masque://` URI per
 * line. 20 lines, 16 unique endpoints (4 duplicates by endpoint). Only the
 * endpoint and the display name are kept: the sub's WARP keys are NEVER used,
 * aether dials with its own identity.toml. This is deliberate and must stay
 * that way.
 *
 * Fetch (10 s timeout) -> on success cache the RAW body and return; on any
 * failure try the cache; with no cache return the bundled list, so a first
 * run with no network still has all 16 relays.
 */
object QuantumSub {
    const val SUB_URL = "https://amir-sub.ashkanfadaeii.workers.dev/sub"

    data class Relay(val endpoint: String, val name: String)

    /** The cached sub body, next to aether's own data. */
    fun cacheFile(context: Context): File =
        File(File(context.filesDir, "aether"), "quantum-sub.txt")

    /**
     * The relay list without touching the network: the cached sub when it
     * parses, else the bundled fallback. Safe to call on the UI thread.
     */
    fun relays(context: Context): List<Relay> {
        val cached = runCatching { cacheFile(context).readText() }.getOrNull()
        val parsed = cached?.let { runCatching { parse(it) }.getOrNull() }.orEmpty()
        return parsed.ifEmpty { BUNDLED }
    }

    /**
     * Blocking network fetch: GET the sub, cache the raw body, return the
     * parsed relays. Returns null on any failure (caller keeps the cached or
     * bundled list). Call off the UI thread.
     */
    fun refresh(context: Context): List<Relay>? {
        val raw = runCatching {
            val connection = URL(SUB_URL).openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 10_000
                connection.readTimeout = 10_000
                connection.requestMethod = "GET"
                if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
                connection.inputStream.bufferedReader().use { it.readText().take(1 shl 20) }
            } finally {
                connection.disconnect()
            }
        }.getOrNull() ?: return null
        val parsed = runCatching { parse(raw) }.getOrNull().orEmpty()
        if (parsed.isEmpty()) return null
        runCatching {
            val file = cacheFile(context)
            file.parentFile?.mkdirs()
            file.writeText(raw)
        }
        return parsed
    }

    /**
     * Base64-decode (either alphabet, padding repaired), then one relay per
     * `masque://` line: endpoint between `@` and the first `?`/`#`, name from
     * the URL-decoded `#fragment` (falls back to the endpoint). Deduped by
     * endpoint, first name wins, sub order kept.
     */
    fun parse(raw: String): List<Relay> {
        val compact = raw.filterNot { it.isWhitespace() }
        val padded = compact + "=".repeat((4 - compact.length % 4) % 4)
        val body = runCatching {
            Base64.decode(padded, Base64.DEFAULT).toString(Charsets.UTF_8)
        }.getOrElse {
            Base64.decode(padded, Base64.URL_SAFE).toString(Charsets.UTF_8)
        }
        val seen = linkedMapOf<String, String>()
        for (line in body.lineSequence()) {
            val trimmed = line.trim()
            if (!trimmed.startsWith("masque://")) continue
            val rest = trimmed.removePrefix("masque://")
            val at = rest.indexOf('@')
            if (at < 0) continue
            val frag = rest.indexOf('#', at)
            val query = rest.indexOf('?', at)
            val end = listOf(frag, query).filter { it >= 0 }.minOrNull() ?: rest.length
            val endpoint = rest.substring(at + 1, end)
            if (endpoint.isBlank()) continue
            val name = if (frag >= 0) decodeComponent(rest.substring(frag + 1)) else endpoint
            seen.putIfAbsent(endpoint, remarkRelayName(name.ifBlank { endpoint }))
        }
        return seen.map { (endpoint, name) -> Relay(endpoint, name) }
    }

    /**
     * The sub's remarks carry the sub owner's name ("Amir 1".."Amir 20"); the
     * app must not show it. Exact "Amir" -> "ClXY", prefix "Amir " -> "ClXY "
     * (number preserved, including the dedupe gaps); anything else passes
     * through unchanged.
     */
    fun remarkRelayName(name: String): String {
        if (name == "Amir") return "ClXY"
        if (name.startsWith("Amir ")) return "ClXY " + name.removePrefix("Amir ")
        return name
    }

    /** Percent-decode only; a literal `+` stays a `+` (URLDecoder alone would turn it into a space). */
    private fun decodeComponent(value: String): String =
        runCatching { URLDecoder.decode(value.replace("+", "%2B"), "UTF-8") }.getOrDefault(value)

    /**
     * Split `host:port`, including bracketed IPv6 `[::1]:port`. Returns null
     * when the endpoint does not parse.
     */
    fun splitEndpoint(endpoint: String): Pair<String, Int>? {
        val trimmed = endpoint.trim()
        if (trimmed.startsWith("[")) {
            val close = trimmed.indexOf(']')
            if (close < 0 || close + 1 >= trimmed.length || trimmed[close + 1] != ':') return null
            val host = trimmed.substring(1, close)
            val port = trimmed.substring(close + 2).toIntOrNull() ?: return null
            if (host.isBlank() || port !in 1..65535) return null
            return host to port
        }
        val colon = trimmed.lastIndexOf(':')
        if (colon <= 0) return null
        val host = trimmed.substring(0, colon)
        val port = trimmed.substring(colon + 1).toIntOrNull() ?: return null
        if (host.isBlank() || port !in 1..65535) return null
        return host to port
    }

    /** Real TCP connect; returns ms, or -1 when the relay does not answer in time. No fake values. */
    fun pingRelay(endpoint: String, timeoutMs: Int = 2000): Int {
        val (host, port) = splitEndpoint(endpoint) ?: return -1
        return runCatching {
            val start = System.currentTimeMillis()
            Socket().use { socket -> socket.connect(InetSocketAddress(host, port), timeoutMs) }
            (System.currentTimeMillis() - start).toInt().coerceAtLeast(1)
        }.getOrDefault(-1)
    }

    /**
     * The 16 unique endpoints from the live sub (verified 2026-11-21), with
     * the renamed remarks. The gaps (8, 9, 12, 13) are the deduped duplicates.
     */
    val BUNDLED: List<Relay> = listOf(
        Relay("162.159.199.2:8095", "ClXY 1"),
        Relay("162.159.198.2:8095", "ClXY 2"),
        Relay("[2a14:7583:f764:64::a29f:c702]:8095", "ClXY 3"),
        Relay("[2a14:7583:f764:64::a29f:c602]:8095", "ClXY 4"),
        Relay("us.v6.free.bestcf.eu.cc:8095", "ClXY 5"),
        Relay("[2606:4700:104::2]:8095", "ClXY 6"),
        Relay("[2606:4700:103::2]:8095", "ClXY 7"),
        Relay("masque1.bestcf.eu.cc:443", "ClXY 10"),
        Relay("masque2.bestcf.eu.cc:443", "ClXY 11"),
        Relay("us.v6.free.bestcf.eu.cc:443", "ClXY 14"),
        Relay("[2602:fc59:b0:64::a29f:c601]:443", "ClXY 15"),
        Relay("162.159.198.160:443", "ClXY 16"),
        Relay("162.159.198.245:4443", "ClXY 17"),
        Relay("162.159.198.41:500", "ClXY 18"),
        Relay("162.159.198.96:4443", "ClXY 19"),
        Relay("162.159.198.171:500", "ClXY 20"),
    )
}
