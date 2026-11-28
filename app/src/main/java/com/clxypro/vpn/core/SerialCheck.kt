package com.clxypro.vpn.core

import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

/**
 * Online serial check against the owner's hesabchi panel Worker
 * (hesabchi.ashkanfadaeii.workers.dev, /api/serial/check). The app asks
 * at most once a day — on the first connect after midnight — whether
 * the stored serial is still good: a serial whose member ran out of
 * days comes back "expired", one the owner locked "revoked" — either
 * clears the unlock and asks for a new serial. Every other connect
 * that day goes straight through, so the check never slows the app down.
 *
 * Only the SHA-256 hash of the serial leaves the device, as the `hash`
 * query param; the serial itself is never sent or stored.
 *
 * Grace, by design: no verdict — network down, timeout, rate-limited
 * (429), malformed reply, or a serial the panel does not know at all
 * ("unknown", e.g. a legacy serial not yet linked to a panel member) —
 * connects with the cached unlock, so a server hiccup never bricks a
 * user. The lock only bites when the panel positively says so.
 */
object SerialCheck {
    private const val CHECK_URL =
        "https://hesabchi.ashkanfadaeii.workers.dev/api/serial/check?hash="
    private const val TIMEOUT_MS = 4000

    sealed interface Result {
        /** The panel says the serial is live; daysLeft is the member's remaining days. */
        data class Active(val daysLeft: Int) : Result

        /** The panel knows this serial and it is dead: "expired" or "revoked". */
        data class Locked(val reason: String) : Result

        /** No verdict: offline, timeout, rate-limited, malformed, or unknown serial. */
        data object Unknown : Result
    }

    /** Blocking network call — call from a background thread. */
    fun check(hash: String): Result {
        return try {
            val conn = (URL(CHECK_URL + hash).openConnection() as HttpURLConnection).apply {
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                requestMethod = "GET"
                setRequestProperty("Accept", "application/json")
            }
            if (conn.responseCode != HttpURLConnection.HTTP_OK) return Result.Unknown
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)
            when {
                json.optBoolean("ok", false) -> Result.Active(json.optInt("days_left", -1))
                json.optString("reason", "") == "expired" -> Result.Locked("expired")
                json.optString("reason", "") == "revoked" -> Result.Locked("revoked")
                else -> Result.Unknown
            }
        } catch (_: Exception) {
            Result.Unknown
        }
    }
}
