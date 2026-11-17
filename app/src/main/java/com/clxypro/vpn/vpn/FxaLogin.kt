package com.clxypro.vpn.vpn

import android.os.SystemClock
import android.util.Base64
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** What the sign-in engine asks for during authentication. */
enum class LoginPrompt { Password, Totp, Captcha, EmailCode, UnblockCode }

/**
 * 100% Native In-App Firefox Accounts (FxA) Client for CLXY Cloud.
 *
 * Implements:
 * 1. Fastly anti-bot challenge solver (PoW, PAT, CAPTCHA, ClientMetrics)
 * 2. PBKDF2 & HKDF password derivation (authPW)
 * 3. HAWK message authentication for session verification (email-2fa & TOTP)
 * 4. Account unblock challenge handling (errno 125)
 * 5. Automatic Remote Settings server-list fetching and caching
 */
class FxaLogin(
    private val binary: File?,
    private val workDir: File
) {
    constructor(workDir: File) : this(null, workDir)

    @Volatile
    private var cancelled = false

    private val answers = LinkedBlockingQueue<String>()

    fun answer(value: String) {
        answers.offer(value.trim())
    }

    fun cancel() {
        cancelled = true
        answers.offer("")
    }

    /**
     * Executes the in-app CLXY Cloud sign-in.
     * Blocks on caller thread (should be Dispatchers.IO).
     * Returns null on success (with session.txt saved), or a human-readable error description.
     */
    fun signIn(
        email: String,
        password: String,
        unblockCode: String = "",
        onPrompt: (LoginPrompt) -> Unit,
        onCaptcha: (String) -> Unit,
        onStatus: (String) -> Unit
    ): String? {
        cancelled = false
        answers.clear()

        val cleanEmail = email.trim()
        val cleanPassword = password.trim()
        val sessionFile = File(workDir, SESSION)

        onStatus("Solving security challenge…")
        Logs.add("sign-in", "Starting native FxA sign-in for $cleanEmail")

        val cookies = LinkedHashMap<String, String>()
        val fastlySolved = solveFastlyChallenge(cookies, onCaptcha, onStatus)
        if (!fastlySolved && !cancelled) {
            Logs.add("sign-in", "Warning: Fastly challenge not cleared; proceeding with direct API")
        }

        if (cancelled) return "Sign-in cancelled"

        onStatus("Deriving authentication credentials…")
        val authPw = try {
            deriveAuthPW(cleanEmail, cleanPassword)
        } catch (t: Throwable) {
            Logs.add("sign-in", "Key derivation failed: ${t.message}")
            return "Failed to derive credentials: ${t.message}"
        }

        onStatus("Connecting to CLXY Cloud…")
        val loginResult = attemptLogin(cleanEmail, authPw, unblockCode, cookies)
        if (cancelled) return "Sign-in cancelled"

        if (loginResult.isBlocked) {
            // Errno 125: Request blocked, unblock code required
            onStatus("CLXY Cloud security authorization required")
            sendUnblockCode(cleanEmail, cookies)
            onPrompt(LoginPrompt.UnblockCode)
            val code = awaitAnswer() ?: return "Sign-in cancelled"
            if (code.isBlank()) return "Authorization code is required"
            return signIn(cleanEmail, cleanPassword, code, onPrompt, onCaptcha, onStatus)
        }

        if (loginResult.error != null) {
            Logs.add("sign-in", "Login rejected: ${loginResult.error}")
            return loginResult.error
        }

        val sessionToken = loginResult.sessionToken
        if (sessionToken.isNullOrEmpty() || sessionToken.length < TOKEN_LENGTH) {
            return "CLXY Cloud did not return a valid session token"
        }

        if (loginResult.isVerified) {
            // Already verified (trusted device / no 2FA)
            return finishSuccess(sessionFile, sessionToken, onStatus)
        }

        // Verification required: Email 2FA or TOTP
        when (loginResult.verificationMethod) {
            "email-2fa" -> {
                onStatus("Confirmation code sent to your email")
                Logs.add("sign-in", "Email 2FA required for $cleanEmail")
                onPrompt(LoginPrompt.EmailCode)
                val code = awaitAnswer() ?: return "Sign-in cancelled"
                if (code.isBlank()) return "Verification code is required"

                onStatus("Verifying code…")
                val verifyErr = verifySession(sessionToken, code, isTotp = false, cookies = cookies)
                if (verifyErr != null) {
                    Logs.add("sign-in", "Email verification failed: $verifyErr")
                    return verifyErr
                }
                return finishSuccess(sessionFile, sessionToken, onStatus)
            }
            "totp-2fa" -> {
                onStatus("Authenticator code required")
                Logs.add("sign-in", "TOTP 2FA required for $cleanEmail")
                onPrompt(LoginPrompt.Totp)
                val code = awaitAnswer() ?: return "Sign-in cancelled"
                if (code.isBlank()) return "Authenticator code is required"

                onStatus("Verifying authenticator code…")
                val verifyErr = verifySession(sessionToken, code, isTotp = true, cookies = cookies)
                if (verifyErr != null) {
                    Logs.add("sign-in", "TOTP verification failed: $verifyErr")
                    return verifyErr
                }
                return finishSuccess(sessionFile, sessionToken, onStatus)
            }
            else -> {
                // If unknown method but session exists, try status check
                if (isSessionVerified(sessionToken)) {
                    return finishSuccess(sessionFile, sessionToken, onStatus)
                }
                return finishSuccess(sessionFile, sessionToken, onStatus)
            }
        }
    }

    private fun finishSuccess(sessionFile: File, sessionToken: String, onStatus: (String) -> Unit): String? {
        try {
            sessionFile.parentFile?.mkdirs()
            sessionFile.writeText(sessionToken.trim())
            Logs.add("sign-in", "Session token saved (${sessionToken.length} chars)")
        } catch (t: Throwable) {
            return "Failed to save session: ${t.message}"
        }

        onStatus("Updating server locations…")
        runCatching { fetchRemoteServers(workDir) }
        onStatus("Connected to CLXY Cloud")
        return null
    }

    private data class LoginAttemptResult(
        val sessionToken: String? = null,
        val isVerified: Boolean = false,
        val verificationMethod: String = "",
        val isBlocked: Boolean = false,
        val error: String? = null
    )

    private fun attemptLogin(
        email: String,
        authPw: String,
        unblockCode: String,
        cookies: Map<String, String>
    ): LoginAttemptResult {
        val target = "$FXA_AUTH_SERVER/account/login"
        val payload = JSONObject().apply {
            put("email", email)
            put("authPW", authPw)
            put("verificationMethod", "email-2fa")
            if (unblockCode.isNotBlank()) {
                put("unblockCode", unblockCode.trim().uppercase())
            }
        }.toString()

        return try {
            val conn = (URL(target).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 15_000
                readTimeout = 25_000
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", USER_AGENT)
                val cookieHdr = formatCookies(cookies)
                if (cookieHdr.isNotEmpty()) {
                    setRequestProperty("Cookie", cookieHdr)
                }
            }

            conn.outputStream.use { it.write(payload.toByteArray(StandardCharsets.UTF_8)) }
            val status = conn.responseCode
            val responseText = if (status in 200..299) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else {
                conn.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            }

            if (status == 200) {
                val obj = JSONObject(responseText)
                val token = obj.optString("sessionToken", "")
                val verified = obj.optBoolean("verified", false) && obj.optBoolean("sessionVerified", false)
                val method = obj.optString("verificationMethod", "email-2fa")
                LoginAttemptResult(sessionToken = token, isVerified = verified, verificationMethod = method)
            } else {
                val errObj = runCatching { JSONObject(responseText) }.getOrNull()
                val errno = errObj?.optInt("errno", 0) ?: 0
                val msg = errObj?.optString("message", "HTTP $status error").orEmpty()
                if (errno == 125) {
                    LoginAttemptResult(isBlocked = true)
                } else {
                    LoginAttemptResult(error = msg)
                }
            }
        } catch (t: Throwable) {
            LoginAttemptResult(error = t.message ?: "Connection error during login")
        }
    }

    private fun verifySession(
        sessionToken: String,
        code: String,
        isTotp: Boolean,
        cookies: Map<String, String>
    ): String? {
        val target = if (isTotp) "$FXA_AUTH_SERVER/session/verify/totp" else "$FXA_AUTH_SERVER/session/verify_code"
        val payload = JSONObject().put("code", code.trim()).toString()

        return try {
            val (hawkId, hawkKey) = deriveHawkCredentials(sessionToken, "sessionToken")
            val authHeader = hawkHeader("POST", target, hawkId, hawkKey, payload)

            val conn = (URL(target).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 15_000
                readTimeout = 25_000
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Authorization", authHeader)
                setRequestProperty("User-Agent", USER_AGENT)
                val cookieHdr = formatCookies(cookies)
                if (cookieHdr.isNotEmpty()) setRequestProperty("Cookie", cookieHdr)
            }

            conn.outputStream.use { it.write(payload.toByteArray(StandardCharsets.UTF_8)) }
            val status = conn.responseCode
            if (status == 200) null else {
                val err = conn.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                val msg = runCatching { JSONObject(err).optString("message") }.getOrNull()
                msg ?: "Code verification failed (HTTP $status)"
            }
        } catch (t: Throwable) {
            t.message ?: "Verification failed"
        }
    }

    private fun isSessionVerified(sessionToken: String): Boolean {
        val target = "$FXA_AUTH_SERVER/recovery_email/status"
        return try {
            val (hawkId, hawkKey) = deriveHawkCredentials(sessionToken, "sessionToken")
            val authHeader = hawkHeader("GET", target, hawkId, hawkKey, "")

            val conn = (URL(target).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 15_000
                setRequestProperty("Authorization", authHeader)
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", "application/json")
            }
            if (conn.responseCode == 200) {
                val txt = conn.inputStream.bufferedReader().use { it.readText() }
                val obj = JSONObject(txt)
                obj.optBoolean("sessionVerified", false)
            } else false
        } catch (_: Throwable) {
            false
        }
    }

    private fun sendUnblockCode(email: String, cookies: Map<String, String>) {
        val target = "$FXA_AUTH_SERVER/account/login/send_unblock_code"
        val payload = JSONObject().put("email", email).toString()
        runCatching {
            val conn = (URL(target).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 10_000
                readTimeout = 15_000
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("User-Agent", USER_AGENT)
                val cookieHdr = formatCookies(cookies)
                if (cookieHdr.isNotEmpty()) setRequestProperty("Cookie", cookieHdr)
            }
            conn.outputStream.use { it.write(payload.toByteArray(StandardCharsets.UTF_8)) }
            conn.responseCode
        }
    }

    /**
     * Solves the Fastly client challenge handshake and returns true if cleared.
     */
    private fun solveFastlyChallenge(
        cookies: MutableMap<String, String>,
        onCaptcha: (String) -> Unit,
        onStatus: (String) -> Unit
    ): Boolean {
        try {
            val pageConn = (URL(FXA_SITE_URL).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 15_000
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            }
            extractCookies(pageConn, cookies)
            val html = pageConn.inputStream.bufferedReader().use { it.readText() }

            val prefixMatcher = Pattern.compile("""/_fs-ch-[A-Za-z0-9]+""").matcher(html)
            if (!prefixMatcher.find()) return true // No challenge present
            val prefix = prefixMatcher.group(0)

            val scriptUrl = "$FXA_SITE_URL$prefix/script.js?reload=true"
            val scriptConn = (URL(scriptUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 15_000
                setRequestProperty("User-Agent", USER_AGENT)
                val cookieHdr = formatCookies(cookies)
                if (cookieHdr.isNotEmpty()) setRequestProperty("Cookie", cookieHdr)
            }
            extractCookies(scriptConn, cookies)
            val scriptText = scriptConn.inputStream.bufferedReader().use { it.readText() }

            val initMatcher = Pattern.compile("""\binit\s*\(\s*(\[.*?\])\s*,\s*"([^"]+)"\s*,\s*"([^"]+)"""").matcher(scriptText)
            var lastChallenges: JSONArray? = null
            var lastToken: String? = null
            while (initMatcher.find()) {
                val cArray = runCatching { JSONArray(initMatcher.group(1)) }.getOrNull()
                val tok = initMatcher.group(2)
                if (cArray != null && tok != null) {
                    lastChallenges = cArray
                    lastToken = tok
                }
            }

            var challenges = lastChallenges ?: return false
            var token = lastToken ?: return false

            for (round in 0 until 8) {
                if (cancelled) return false
                val answers = JSONArray()
                for (i in 0 until challenges.length()) {
                    val ch = challenges.getJSONObject(i)
                    val ty = ch.optString("ty", "")
                    val data = ch.optJSONObject("data") ?: JSONObject()

                    when (ty) {
                        "pow" -> {
                            val base = data.optString("base", "")
                            val targetHash = data.optString("hash", "")
                            val ans = solvePow(base, targetHash) ?: return false
                            answers.put(
                                JSONObject()
                                    .put("ty", "pow")
                                    .put("base", base)
                                    .put("answer", ans)
                                    .put("hmac", data.optString("hmac", ""))
                                    .put("expires", data.optString("expires", ""))
                            )
                        }
                        "pat" -> {
                            // PAT challenge
                            val patUrl = "$FXA_SITE_URL$prefix/pat?token=" + URLEncoder.encode(token, "UTF-8")
                            var auth = ""
                            try {
                                val patConn = (URL(patUrl).openConnection() as HttpURLConnection).apply {
                                    requestMethod = "POST"
                                    doOutput = true
                                    connectTimeout = 5_000
                                    readTimeout = 5_000
                                    setRequestProperty("User-Agent", USER_AGENT)
                                    setRequestProperty("Content-Type", "application/json")
                                    setRequestProperty("Origin", FXA_SITE_URL.trimEnd('/'))
                                    val cookieHdr = formatCookies(cookies)
                                    if (cookieHdr.isNotEmpty()) setRequestProperty("Cookie", cookieHdr)
                                }
                                patConn.outputStream.use { it.write(ByteArray(0)) }
                                if (patConn.responseCode == 200) {
                                    val patText = patConn.inputStream.bufferedReader().use { it.readText() }
                                    auth = JSONObject(patText).optString("auth", "")
                                }
                            } catch (_: Throwable) {}
                            answers.put(JSONObject().put("ty", "pat").put("auth", auth))
                        }
                        "clientmetrics" -> {
                            answers.put(
                                JSONObject()
                                    .put("ty", "clientmetrics")
                                    .put("webdriver", false)
                                    .put("bot_detection_result", JSONObject().put("bot_detected", false).put("bot_kind", JSONObject.NULL))
                                    .put("browser_metrics", JSONObject().put("client_data", "{}").put("error_trace", JSONObject.NULL))
                                    .put("detector_results", JSONObject())
                                    .put("v", 2)
                            )
                        }
                        "captcha" -> {
                            val imgB64 = data.optString("image_b64", "")
                            onStatus("Security check: enter the characters below")
                            onCaptcha(imgB64)
                            val answer = awaitAnswer() ?: return false
                            answers.put(JSONObject().put("ty", "captcha").put("answer", answer.trim()))
                        }
                    }
                }

                // Post-back
                val postBackUrl = "$FXA_SITE_URL$prefix/fst-post-back"
                val pbPayload = JSONObject().put("token", token).put("data", answers).toString()
                val pbConn = (URL(postBackUrl).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    doOutput = true
                    connectTimeout = 10_000
                    readTimeout = 15_000
                    setRequestProperty("User-Agent", USER_AGENT)
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("Accept", "application/json")
                    setRequestProperty("Origin", FXA_SITE_URL.trimEnd('/'))
                    val cookieHdr = formatCookies(cookies)
                    if (cookieHdr.isNotEmpty()) setRequestProperty("Cookie", cookieHdr)
                }
                pbConn.outputStream.use { it.write(pbPayload.toByteArray(StandardCharsets.UTF_8)) }
                extractCookies(pbConn, cookies)

                val pbText = pbConn.inputStream.bufferedReader().use { it.readText() }
                val pbResp = JSONObject(pbText)
                if (pbResp.optString("status") == "success") {
                    Logs.add("sign-in", "Fastly challenge successfully resolved!")
                    return true
                }
                challenges = pbResp.optJSONArray("ch") ?: break
                token = pbResp.optString("tok", "")
            }
            return false
        } catch (t: Throwable) {
            Logs.add("sign-in", "Fastly challenge solver exception: ${t.message}")
            return false
        }
    }

    private fun solvePow(base: String, targetHex: String): String? {
        val alphabet = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        val md = MessageDigest.getInstance("SHA-256")
        val baseBytes = base.toByteArray(StandardCharsets.US_ASCII)
        val cand = ByteArray(baseBytes.size + 2)
        System.arraycopy(baseBytes, 0, cand, 0, baseBytes.size)

        for (i in 0 until alphabet.length) {
            cand[baseBytes.size] = alphabet[i].code.toByte()
            for (j in 0 until alphabet.length) {
                cand[baseBytes.size + 1] = alphabet[j].code.toByte()
                md.reset()
                val digest = md.digest(cand)
                val hex = bytesToHex(digest)
                if (hex.equals(targetHex, ignoreCase = true)) {
                    return "" + alphabet[i] + alphabet[j]
                }
            }
        }
        return null
    }

    private fun extractCookies(conn: HttpURLConnection, cookies: MutableMap<String, String>) {
        val headerFields = conn.headerFields ?: return
        for ((key, values) in headerFields) {
            if (key != null && key.equals("Set-Cookie", ignoreCase = true)) {
                for (v in values) {
                    val cookiePart = v.split(";")[0].trim()
                    val eq = cookiePart.indexOf('=')
                    if (eq > 0) {
                        cookies[cookiePart.substring(0, eq).trim()] = cookiePart.substring(eq + 1).trim()
                    }
                }
            }
        }
    }

    private fun formatCookies(cookies: Map<String, String>): String =
        cookies.entries.joinToString("; ") { "${it.key}=${it.value}" }

    private fun awaitAnswer(): String? {
        val deadline = SystemClock.elapsedRealtime() + ANSWER_TIMEOUT_MS
        while (!cancelled && SystemClock.elapsedRealtime() < deadline) {
            val item = answers.poll(POLL_MS, TimeUnit.MILLISECONDS)
            if (item != null) return item
        }
        return null
    }

    companion object {
        private const val FXA_AUTH_SERVER = "https://api.accounts.firefox.com/v1"
        private const val FXA_SITE_URL = "https://accounts.firefox.com/"
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0"
        private const val SESSION = "session.txt"
        private const val TOKEN_LENGTH = 64
        private const val ANSWER_TIMEOUT_MS = 300_000L
        private const val POLL_MS = 200L

        /** Fetches Remote Settings server-list and writes cache file. */
        fun fetchRemoteServers(workDir: File): List<Server> {
            val url = "https://firefox.settings.services.mozilla.com/v1/buckets/main/collections/vpn-serverlist/records"
            return try {
                val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 10_000
                    readTimeout = 15_000
                    setRequestProperty("User-Agent", USER_AGENT)
                }
                val data = conn.inputStream.bufferedReader().use { it.readText() }
                val obj = JSONObject(data)
                val records = obj.optJSONArray("data") ?: return emptyList()
                val servers = mutableListOf<Server>()
                for (i in 0 until records.length()) {
                    val r = records.optJSONObject(i) ?: continue
                    val code = r.optString("code", "")
                    val name = r.optString("name", "")
                    val cities = r.optJSONArray("cities") ?: continue
                    for (j in 0 until cities.length()) {
                        val c = cities.optJSONObject(j) ?: continue
                        val cityName = c.optString("name", "")
                        val sList = c.optJSONArray("servers") ?: continue
                        for (k in 0 until sList.length()) {
                            val s = sList.optJSONObject(k) ?: continue
                            val host = s.optString("hostname", "")
                            val port = s.optInt("port", 443)
                            if (host.isNotEmpty()) {
                                servers.add(Server(country = code, countryName = name, city = cityName, address = "$host:$port"))
                            }
                        }
                    }
                }
                if (servers.isNotEmpty()) {
                    val cacheFile = File(workDir, "servers.json")
                    val array = JSONArray()
                    servers.forEach {
                        array.put(JSONObject().put("cc", it.country).put("country", it.countryName).put("city", it.city).put("addr", it.address))
                    }
                    cacheFile.writeText(array.toString())
                    Logs.add("servers", "Cached ${servers.size} server locations")
                }
                servers
            } catch (t: Throwable) {
                Logs.add("servers", "Failed to load remote servers: ${t.message}")
                emptyList()
            }
        }

        fun deriveAuthPW(email: String, password: String): String {
            val salt = ("identity.mozilla.com/picl/v1/quickStretch:$email").toByteArray(StandardCharsets.UTF_8)
            val spec = PBEKeySpec(password.toCharArray(), salt, 1000, 32 * 8)
            val skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            val quickStretched = skf.generateSecret(spec).encoded

            val prk = hmacSha256(ByteArray(32), quickStretched)
            val info = "identity.mozilla.com/picl/v1/authPW".toByteArray(StandardCharsets.UTF_8)

            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(prk, "HmacSHA256"))
            mac.update(info)
            mac.update(1.toByte())
            return bytesToHex(mac.doFinal())
        }

        fun deriveHawkCredentials(tokenHex: String, contextStr: String): Pair<String, ByteArray> {
            val tokenBytes = hexToBytes(tokenHex)
            val prk = hmacSha256(ByteArray(32), tokenBytes)
            val info = ("identity.mozilla.com/picl/v1/$contextStr").toByteArray(StandardCharsets.UTF_8)

            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(prk, "HmacSHA256"))

            mac.update(info)
            mac.update(1.toByte())
            val t1 = mac.doFinal()

            mac.update(t1)
            mac.update(info)
            mac.update(2.toByte())
            val t2 = mac.doFinal()

            return Pair(bytesToHex(t1), t2)
        }

        fun hawkHeader(
            method: String,
            rawUrl: String,
            hawkId: String,
            hawkKey: ByteArray,
            payload: String = ""
        ): String {
            val url = URL(rawUrl)
            val nonce = randomHex(6)
            val ts = (System.currentTimeMillis() / 1000L).toString()
            val port = if (url.port > 0) url.port.toString() else (if (url.protocol == "https") "443" else "80")
            val pathAndQuery = url.file.ifEmpty { "/" }

            val payloadHash = if (payload.isNotEmpty()) {
                val md = MessageDigest.getInstance("SHA-256")
                md.update("hawk.1.payload\napplication/json\n".toByteArray(StandardCharsets.UTF_8))
                md.update(payload.toByteArray(StandardCharsets.UTF_8))
                md.update("\n".toByteArray(StandardCharsets.UTF_8))
                bytesToHex(md.digest())
            } else ""

            val parts = listOf(
                "hawk.1.header",
                ts,
                nonce,
                method.uppercase(),
                pathAndQuery,
                url.host,
                port,
                payloadHash,
                "",
                ""
            )
            val normalized = parts.joinToString("\n")
            val mac = bytesToHex(hmacSha256(hawkKey, normalized.toByteArray(StandardCharsets.UTF_8)))

            var hdr = "Hawk id=\"$hawkId\", ts=\"$ts\", nonce=\"$nonce\", mac=\"$mac\""
            if (payloadHash.isNotEmpty()) {
                hdr += ", hash=\"$payloadHash\""
            }
            return hdr
        }

        private fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(key, "HmacSHA256"))
            return mac.doFinal(data)
        }

        private fun bytesToHex(bytes: ByteArray): String {
            val sb = StringBuilder(bytes.size * 2)
            for (b in bytes) {
                sb.append(String.format("%02x", b.toInt() and 0xFF))
            }
            return sb.toString()
        }

        private fun hexToBytes(hex: String): ByteArray {
            val len = hex.length
            val data = ByteArray(len / 2)
            var i = 0
            while (i < len) {
                data[i / 2] = ((Character.digit(hex[i], 16) shl 4) + Character.digit(hex[i + 1], 16)).toByte()
                i += 2
            }
            return data
        }

        private fun randomHex(lenBytes: Int): String {
            val bytes = ByteArray(lenBytes)
            SecureRandom().nextBytes(bytes)
            return bytesToHex(bytes)
        }
    }
}
