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
    private const val ACTIVATE_URL =
        "https://hesabchi.ashkanfadaeii.workers.dev/api/serial/activate?hash="
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

    /** Activation outcome — what the dialog shows after the user submits. */
    sealed interface Activation {
        /** The serial may unlock this device. */
        data object Valid : Activation

        /** No such serial (or expired/revoked) — show "invalid". */
        data object Invalid : Activation

        /** The serial exists but its usage limit is spent — show the limit message. */
        data class LimitReached(val uses: Int, val maxUses: Int) : Activation
    }

    /**
     * Full activation check for a submitted serial. A built-in serial is
     * accepted offline; anything else must be active on the panel first.
     * Either way this device is then registered with the panel
     * (POST /api/serial/activate) — the panel counts distinct devices
     * against the serial's max uses, so a code shared with friends stops
     * working once its limit is spent. A re-activation from the same
     * device (e.g. after reinstalling) succeeds without burning a use.
     * When the panel cannot be reached, the activation still goes through
     * (grace) — the limit only bites when the panel answers.
     * Blocking — call from a background thread.
     */
    fun activate(code: String, deviceId: String): Activation {
        val hash = QuantumSerial.hashOf(code)
        if (!QuantumSerial.isValid(code) && check(hash) !is Result.Active) {
            return Activation.Invalid
        }
        return when (val r = registerActivation(hash, deviceId)) {
            is RegisterResult.LimitReached -> Activation.LimitReached(r.uses, r.maxUses)
            else -> Activation.Valid
        }
    }

    private sealed interface RegisterResult {
        data object Registered : RegisterResult
        data class LimitReached(val uses: Int, val maxUses: Int) : RegisterResult
        data object Unknown : RegisterResult
    }

    /** Registers this device for the serial; see activate(). */
    private fun registerActivation(hash: String, deviceId: String): RegisterResult {
        return try {
            val encoded = java.net.URLEncoder.encode(deviceId, "UTF-8")
            val conn = (URL("$ACTIVATE_URL$hash&device=$encoded").openConnection()
                as HttpURLConnection).apply {
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                requestMethod = "POST"
                setRequestProperty("Accept", "application/json")
            }
            if (conn.responseCode != HttpURLConnection.HTTP_OK) return RegisterResult.Unknown
            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            when {
                json.optBoolean("ok", false) -> RegisterResult.Registered
                json.optString("reason", "") == "limit_reached" ->
                    RegisterResult.LimitReached(
                        json.optInt("uses", 0), json.optInt("max_uses", 0)
                    )
                else -> RegisterResult.Unknown
            }
        } catch (_: Exception) {
            RegisterResult.Unknown
        }
    }
}
