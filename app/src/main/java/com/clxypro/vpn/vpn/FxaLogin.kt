package com.clxypro.vpn.vpn

import android.os.SystemClock
import android.util.Base64
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
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
 * 1. Fastly anti-bot challenge solver (PoW, PAT, CAPTCHA, ClientMetrics),
 *    applied reactively like Arman's fxaDo: when the API answers HTTP 406
 *    (empty body), the challenge is solved and the SAME request retried,
 *    up to 5 solves. Solving once up-front is not enough — the login POST
 *    itself can still be challenged, and a bare 406 used to surface as an
 *    empty error string ("rejected this sign-in" with no detail).
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

        Logs.add("sign-in", "Starting native FxA sign-in for $cleanEmail")

        // No up-front challenge solve: like Arman, the challenge is solved
        // only when the API actually answers HTTP 406, and the request is
        // then retried with the fresh cookie (see fxaRequest).
        val cookies = LinkedHashMap<String, String>()

        if (cancelled) return "Sign-in cancelled"

        onStatus("Deriving authentication credentials…")
        val authPw = try {
            deriveAuthPW(cleanEmail, cleanPassword)
        } catch (t: Throwable) {
            Logs.add("sign-in", "Key derivation failed: ${t.message}")
            return "Failed to derive credentials: ${t.message}"
        }

        onStatus("Connecting to CLXY Cloud…")
        val loginResult = loginWithRetries(cleanEmail, cleanPassword, authPw, unblockCode, cookies, onCaptcha, onStatus)
        if (cancelled) return "Sign-in cancelled"

        if (loginResult.isBlocked) {
            // Errno 125: Request blocked, unblock code required
            onStatus("CLXY Cloud security authorization required")
            sendUnblockCode(cleanEmail, cookies, onCaptcha, onStatus)
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
                val verifyErr = verifySession(sessionToken, code, isTotp = false, cookies = cookies, onCaptcha = onCaptcha, onStatus = onStatus)
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
                val verifyErr = verifySession(sessionToken, code, isTotp = true, cookies = cookies, onCaptcha = onCaptcha, onStatus = onStatus)
                if (verifyErr != null) {
                    Logs.add("sign-in", "TOTP verification failed: $verifyErr")
                    return verifyErr
                }
                return finishSuccess(sessionFile, sessionToken, onStatus)
            }
            else -> {
                // If unknown method but session exists, try status check
                if (isSessionVerified(sessionToken, cookies, onCaptcha, onStatus)) {
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
        val error: String? = null,
        val errno: Int = 0,
        /** The account's own email spelling, carried by errno 120. */
        val correctedEmail: String = ""
    )

    private data class FxaCall(val status: Int, val body: String, val error: String?)

    /**
     * Arman's fxaDo, ported: sends an FxA API request and, when the edge
     * answers HTTP 406 (Fastly's bot check — an EMPTY body, no FxA error
     * payload), solves the challenge and retries the SAME request, up to
     * 5 solves within a 90s budget. A bare 406 used to be treated as a
     * login failure with an empty error string, which the UI could only
     * show as the generic "rejected this sign-in" with no detail.
     */
    private fun fxaRequest(
        method: String,
        url: String,
        payload: String,
        hawkAuth: String?,
        cookies: MutableMap<String, String>,
        onCaptcha: (String) -> Unit,
        onStatus: (String) -> Unit
    ): FxaCall {
        val deadline = SystemClock.elapsedRealtime() + CHALLENGE_BUDGET_MS
        for (solved in 0..MAX_CHALLENGE_ATTEMPTS) {
            if (cancelled) return FxaCall(0, "", "Sign-in cancelled")
            val call = sendOnce(method, url, payload, hawkAuth, cookies)
            if (call.error != null) return call
            if (call.status != 406) return call
            if (solved == MAX_CHALLENGE_ATTEMPTS) {
                Logs.add("sign-in", "Fastly still answers HTTP 406 after $solved solved challenges")
                return FxaCall(406, "", "HTTP 406: CLXY Cloud's security check refused this sign-in after $solved challenge solves")
            }
            if (SystemClock.elapsedRealtime() > deadline) {
                return FxaCall(406, "", "HTTP 406: CLXY Cloud's security check timed out while solving its challenge")
            }
            Logs.add("sign-in", "Account server answered HTTP 406 (Fastly bot check); solving challenge (attempt ${solved + 1})")
            onStatus("Solving security challenge…")
            if (!solveFastlyChallenge(cookies, onCaptcha, onStatus)) {
                if (cancelled) return FxaCall(0, "", "Sign-in cancelled")
                Logs.add("sign-in", "Fastly challenge failed (attempt ${solved + 1})")
                return FxaCall(406, "", "HTTP 406: CLXY Cloud's security check refused this sign-in (challenge failed)")
            }
            Logs.add("sign-in", "Fastly challenge solved; retrying request")
        }
        return FxaCall(406, "", "HTTP 406: CLXY Cloud's security check refused this sign-in")
    }

    /** One FxA HTTP request, with Arman's Firefox headers. */
    private fun sendOnce(
        method: String,
        url: String,
        payload: String,
        hawkAuth: String?,
        cookies: Map<String, String>
    ): FxaCall {
        return try {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                doOutput = payload.isNotEmpty()
                connectTimeout = 15_000
                readTimeout = 25_000
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "*/*")
                // Arman: gzip as the sole encoding was associated with
                // HTTP 406 on the HTTP/1.1 path in live A/B tests.
                setRequestProperty("Accept-Encoding", "identity")
                setRequestProperty("User-Agent", USER_AGENT)
                if (hawkAuth != null) setRequestProperty("Authorization", hawkAuth)
                val cookieHdr = formatCookies(cookies)
                if (cookieHdr.isNotEmpty()) setRequestProperty("Cookie", cookieHdr)
            }
            if (payload.isNotEmpty()) {
                conn.outputStream.use { it.write(payload.toByteArray(StandardCharsets.UTF_8)) }
            }
            val status = conn.responseCode
            val body = if (status in 200..299) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else {
                conn.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            }
            conn.disconnect()
            FxaCall(status, body, null)
        } catch (t: Throwable) {
            FxaCall(0, "", t.message ?: "Connection error")
        }
    }

    /**
     * Login with Arman's fallbacks: errno 107 (the server rejects
     * verificationMethod) retries without it; errno 120 (incorrect email
     * case) retries once in the account's own letter case, as Firefox does.
     */
    private fun loginWithRetries(
        email: String,
        password: String,
        authPw: String,
        unblockCode: String,
        cookies: MutableMap<String, String>,
        onCaptcha: (String) -> Unit,
        onStatus: (String) -> Unit
    ): LoginAttemptResult {
        var result = attemptLogin(email, "", authPw, "email-2fa", unblockCode, cookies, onCaptcha, onStatus)
        if (result.errno == 107) {
            Logs.add("sign-in", "Server rejected verificationMethod (errno 107); retrying without it")
            result = attemptLogin(email, "", authPw, "", unblockCode, cookies, onCaptcha, onStatus)
        }
        if (result.errno == 120 && result.correctedEmail.isNotEmpty() && result.correctedEmail != email) {
            Logs.add("sign-in", "Retrying login in the account's own email case")
            val fixedAuthPw = try {
                deriveAuthPW(result.correctedEmail, password)
            } catch (t: Throwable) {
                return LoginAttemptResult(error = "Failed to derive credentials: ${t.message}")
            }
            result = attemptLogin(result.correctedEmail, email, fixedAuthPw, "email-2fa", unblockCode, cookies, onCaptcha, onStatus)
        }
        return result
    }

    private fun attemptLogin(
        email: String,
        originalEmail: String,
        authPw: String,
        verificationMethod: String,
        unblockCode: String,
        cookies: MutableMap<String, String>,
        onCaptcha: (String) -> Unit,
        onStatus: (String) -> Unit
    ): LoginAttemptResult {
        val target = "$FXA_AUTH_SERVER/account/login"
        val payload = JSONObject().apply {
            put("email", email)
            put("authPW", authPw)
            if (originalEmail.isNotEmpty()) put("originalLoginEmail", originalEmail)
            if (verificationMethod.isNotEmpty()) put("verificationMethod", verificationMethod)
            if (unblockCode.isNotBlank()) {
                put("unblockCode", unblockCode.trim().uppercase())
            }
        }.toString()

        val call = fxaRequest("POST", target, payload, hawkAuth = null, cookies, onCaptcha, onStatus)
        if (call.error != null) return LoginAttemptResult(error = call.error)
        if (call.status == 200) {
            val obj = runCatching { JSONObject(call.body) }.getOrNull()
                ?: return LoginAttemptResult(error = "Invalid response from CLXY Cloud (HTTP 200)")
            val token = obj.optString("sessionToken", "")
            // The login response carries only "verified" — there is no
            // "sessionVerified" field here (that lives on
            // /recovery_email/status). Requiring both made isVerified
            // always false, so even verified accounts were asked for a
            // code. Arman branches on Verified alone.
            val verified = obj.optBoolean("verified", false)
            val method = obj.optString("verificationMethod", "email-2fa")
            return LoginAttemptResult(sessionToken = token, isVerified = verified, verificationMethod = method)
        }
        val errObj = runCatching { JSONObject(call.body) }.getOrNull()
        val errno = errObj?.optInt("errno", 0) ?: 0
        val msg = errObj?.optString("message", "").orEmpty()
        if (errno == 125) return LoginAttemptResult(isBlocked = true)
        // Never return an empty error: the UI shows the raw text as the
        // detail line, and errno lets it pick the right friendly message.
        val detail = when {
            msg.isNotEmpty() && errno != 0 -> "$msg (errno $errno, HTTP ${call.status})"
            msg.isNotEmpty() -> "$msg (HTTP ${call.status})"
            errno != 0 -> "CLXY Cloud error (errno $errno, HTTP ${call.status})"
            else -> "CLXY Cloud error (HTTP ${call.status})"
        }
        val corrected = errObj?.optString("email", "").orEmpty()
        return LoginAttemptResult(error = detail, errno = errno, correctedEmail = corrected)
    }

    private fun verifySession(
        sessionToken: String,
        code: String,
        isTotp: Boolean,
        cookies: MutableMap<String, String>,
        onCaptcha: (String) -> Unit,
        onStatus: (String) -> Unit
    ): String? {
        val target = if (isTotp) "$FXA_AUTH_SERVER/session/verify/totp" else "$FXA_AUTH_SERVER/session/verify_code"
        val payload = JSONObject().put("code", code.trim()).toString()

        val (hawkId, hawkKey) = try {
            deriveHawkCredentials(sessionToken, "sessionToken")
        } catch (t: Throwable) {
            return t.message ?: "Verification failed"
        }
        val authHeader = hawkHeader("POST", target, hawkId, hawkKey, payload)
        val call = fxaRequest("POST", target, payload, authHeader, cookies, onCaptcha, onStatus)
        if (call.error != null) return call.error
        if (call.status == 200) return null
        val errObj = runCatching { JSONObject(call.body) }.getOrNull()
        val msg = errObj?.optString("message", "").orEmpty()
        val errno = errObj?.optInt("errno", 0) ?: 0
        return when {
            msg.isNotEmpty() && errno != 0 -> "$msg (errno $errno, HTTP ${call.status})"
            msg.isNotEmpty() -> "$msg (HTTP ${call.status})"
            else -> "Code verification failed (HTTP ${call.status})"
        }
    }

    private fun isSessionVerified(
        sessionToken: String,
        cookies: MutableMap<String, String>,
        onCaptcha: (String) -> Unit,
        onStatus: (String) -> Unit
    ): Boolean {
        val target = "$FXA_AUTH_SERVER/recovery_email/status"
        return try {
            val (hawkId, hawkKey) = deriveHawkCredentials(sessionToken, "sessionToken")
            val authHeader = hawkHeader("GET", target, hawkId, hawkKey, "")
            val call = fxaRequest("GET", target, "", authHeader, cookies, onCaptcha, onStatus)
            if (call.error == null && call.status == 200) {
                JSONObject(call.body).optBoolean("sessionVerified", false)
            } else false
        } catch (_: Throwable) {
            false
        }
    }

    private fun sendUnblockCode(
        email: String,
        cookies: MutableMap<String, String>,
        onCaptcha: (String) -> Unit,
        onStatus: (String) -> Unit
    ) {
        val target = "$FXA_AUTH_SERVER/account/login/send_unblock_code"
        val payload = JSONObject().put("email", email).toString()
        runCatching { fxaRequest("POST", target, payload, hawkAuth = null, cookies, onCaptcha, onStatus) }
    }

    /**
     * Solves the Fastly client challenge handshake and returns true if cleared.
     * Tries the API host first (the host that actually rejected the request),
     * then the site host — Arman's order. The API host usually serves no
     * challenge page at all; the cookie the site host issues is scoped to
     * firefox.com, so it clears the API host too.
     */
    private fun solveFastlyChallenge(
        cookies: MutableMap<String, String>,
        onCaptcha: (String) -> Unit,
        onStatus: (String) -> Unit
    ): Boolean {
        for (base in listOf(FXA_API_HOST, FXA_SITE_URL.trimEnd('/'))) {
            if (cancelled) return false
            if (solveChallengeOnHost(base, cookies, onCaptcha, onStatus)) return true
        }
        return false
    }

    /** Runs the whole challenge flow against one host. False = no challenge page here, or failed. */
    private fun solveChallengeOnHost(
        base: String,
        cookies: MutableMap<String, String>,
        onCaptcha: (String) -> Unit,
        onStatus: (String) -> Unit
    ): Boolean {
        try {
            val pageUrl = "$base/"
            val html = fetchText(pageUrl, cookies) ?: return false
            if (!isChallengePage(html)) return false

            val prefixMatcher = Pattern.compile("""/_fs-ch-[A-Za-z0-9]+""").matcher(html)
            if (!prefixMatcher.find()) return false
            val prefix = prefixMatcher.group(0) ?: return false

            val scriptUrl = "$base$prefix/script.js?reload=true"
            val scriptText = fetchText(scriptUrl, cookies) ?: return false

            val init = parseChallengeInit(scriptText, prefix) ?: return false
            var challenges = init.first
            var token = init.second

            for (round in 0 until 8) {
                if (cancelled) return false
                val answers = JSONArray()
                for (i in 0 until challenges.length()) {
                    val ch = challenges.getJSONObject(i)
                    val ty = ch.optString("ty", "")
                    val data = ch.optJSONObject("data") ?: JSONObject()

                    when (ty) {
                        "pow" -> {
                            val powBase = data.optString("base", "")
                            val targetHash = data.optString("hash", "")
                            val ans = solvePow(powBase, targetHash) ?: return false
                            answers.put(
                                JSONObject()
                                    .put("ty", "pow")
                                    .put("base", powBase)
                                    .put("answer", ans)
                                    .put("hmac", data.optString("hmac", ""))
                                    .put("expires", data.optString("expires", ""))
                            )
                        }
                        "pat" -> {
                            // PAT challenge. A non-200 (401/400 when the
                            // client cannot mint a PAT) posts back an empty
                            // auth, which makes Fastly fall back to
                            // solvable challenges — Arman's behavior.
                            val patUrl = "$base$prefix/pat?token=" + URLEncoder.encode(token, "UTF-8")
                            var auth = ""
                            try {
                                val patConn = (URL(patUrl).openConnection() as HttpURLConnection).apply {
                                    requestMethod = "POST"
                                    doOutput = true
                                    connectTimeout = 5_000
                                    readTimeout = 5_000
                                    setRequestProperty("User-Agent", USER_AGENT)
                                    setRequestProperty("Content-Type", "application/json")
                                    setRequestProperty("Accept", "text/plain")
                                    setRequestProperty("Origin", base)
                                    val cookieHdr = formatCookies(cookies)
                                    if (cookieHdr.isNotEmpty()) setRequestProperty("Cookie", cookieHdr)
                                }
                                patConn.outputStream.use { it.write(ByteArray(0)) }
                                if (patConn.responseCode == 200) {
                                    val patText = patConn.inputStream.bufferedReader().use { it.readText() }
                                    auth = JSONObject(patText).optString("auth", "")
                                }
                                patConn.disconnect()
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
                            // Never prompt with an unreadable image: the
                            // user would sit at a dead-end input box.
                            if (!imgB64.startsWith("data:image/")) {
                                Logs.add("sign-in", "Unrecognized CAPTCHA challenge data")
                                return false
                            }
                            onStatus("Security check: enter the characters below")
                            onCaptcha(imgB64)
                            val answer = awaitAnswer() ?: return false
                            answers.put(JSONObject().put("ty", "captcha").put("answer", answer.trim()))
                        }
                        else -> {
                            Logs.add("sign-in", "Unsupported Fastly challenge type: $ty")
                            return false
                        }
                    }
                }

                // Post-back
                val postBackUrl = "$base$prefix/fst-post-back"
                val pbPayload = JSONObject().put("token", token).put("data", answers).toString()
                val pbConn = (URL(postBackUrl).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    doOutput = true
                    connectTimeout = 10_000
                    readTimeout = 15_000
                    setRequestProperty("User-Agent", USER_AGENT)
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("Accept", "application/json")
                    setRequestProperty("Origin", base)
                    val cookieHdr = formatCookies(cookies)
                    if (cookieHdr.isNotEmpty()) setRequestProperty("Cookie", cookieHdr)
                }
                pbConn.outputStream.use { it.write(pbPayload.toByteArray(StandardCharsets.UTF_8)) }
                extractCookies(pbConn, cookies)
                if (pbConn.responseCode != 200) {
                    pbConn.disconnect()
                    return false
                }

                val pbText = pbConn.inputStream.bufferedReader().use { it.readText() }
                pbConn.disconnect()
                val pbResp = runCatching { JSONObject(pbText) }.getOrNull() ?: return false
                if (pbResp.optString("status") == "success") {
                    // Verify the challenge page clears before trusting the
                    // cookies: a solved cookie can be rejected when the
                    // exit IP rotated between solving and probing.
                    val probe = fetchText(pageUrl, cookies)
                    if (probe == null || isChallengePage(probe)) {
                        Logs.add("sign-in", "Challenge cookie not accepted on probe; retrying on next host")
                        return false
                    }
                    Logs.add("sign-in", "Fastly challenge successfully resolved!")
                    return true
                }
                challenges = pbResp.optJSONArray("ch") ?: break
                token = pbResp.optString("tok", "")
                if (token.isEmpty()) break
            }
            return false
        } catch (t: Throwable) {
            Logs.add("sign-in", "Fastly challenge solver exception: ${t.message}")
            return false
        }
    }

    /** GETs a URL with the Firefox headers, collecting cookies. Null on any failure or non-200. */
    private fun fetchText(url: String, cookies: MutableMap<String, String>): String? {
        return try {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 15_000
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                val cookieHdr = formatCookies(cookies)
                if (cookieHdr.isNotEmpty()) setRequestProperty("Cookie", cookieHdr)
            }
            extractCookies(conn, cookies)
            if (conn.responseCode != 200) {
                conn.disconnect()
                return null
            }
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()
            text
        } catch (_: Throwable) {
            null
        }
    }

    /** Arman's challenge detection: the asset prefix AND the page marker. */
    private fun isChallengePage(html: String): Boolean =
        html.contains("/_fs-ch-") && html.contains("Client Challenge")

    /**
     * Parses the init([...], "token", "prefix") call from the challenge
     * script with a real JSON decoder (Arman's approach): the old regex
     * could not match a call spanning lines or nesting brackets, and never
     * checked the prefix. Prefers the last valid call.
     */
    private fun parseChallengeInit(scriptText: String, prefix: String): Pair<JSONArray, String>? {
        var searchFrom = 0
        var found: Pair<JSONArray, String>? = null
        while (true) {
            val idx = scriptText.indexOf("init", searchFrom)
            if (idx < 0) break
            searchFrom = idx + 4
            // Word boundary: skip "initialize", "reinit", ...
            if (idx > 0) {
                val prev = scriptText[idx - 1]
                if (prev.isLetterOrDigit() || prev == '_' || prev == '$') continue
            }
            var p = idx + 4
            while (p < scriptText.length && scriptText[p].isWhitespace()) p++
            if (p >= scriptText.length || scriptText[p] != '(') continue
            val parsed = runCatching {
                val tok = JSONTokener(scriptText.substring(p + 1))
                val arr = tok.nextValue() as? JSONArray ?: return@runCatching null
                if (tok.nextClean() != ',') return@runCatching null
                val token = tok.nextValue() as? String ?: return@runCatching null
                if (tok.nextClean() != ',') return@runCatching null
                val scriptPrefix = tok.nextValue() as? String ?: return@runCatching null
                if (scriptPrefix != prefix || token.isEmpty() || arr.length() == 0) return@runCatching null
                arr to token
            }.getOrNull() ?: continue
            found = parsed
        }
        return found
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
        private const val FXA_API_HOST = "https://api.accounts.firefox.com"
        private const val FXA_SITE_URL = "https://accounts.firefox.com/"
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0"
        private const val SESSION = "session.txt"
        private const val TOKEN_LENGTH = 64
        private const val ANSWER_TIMEOUT_MS = 300_000L
        private const val POLL_MS = 200L
        /** Arman's fxaDo bounds: max challenge solves per call, 90s budget. */
        private const val MAX_CHALLENGE_ATTEMPTS = 5
        private const val CHALLENGE_BUDGET_MS = 90_000L

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
