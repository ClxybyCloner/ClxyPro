package com.clxypro.vpn.vpn

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** One line of the in-app log. */
data class LogLine(val time: String, val tag: String, val message: String) {
    override fun toString(): String = "$time $tag: $message"
}

/**
 * What the app and the engine did in this run, for the log screen in Settings.
 * It stays in memory only: enough to see why a connect or a sign-in went the
 * way it did, and nothing left behind on the device.
 */
object Logs {
    private const val LIMIT = 500
    private const val MAX_CHARS = 400

    private val clock = SimpleDateFormat("HH:mm:ss", Locale.US)
    private val _lines = MutableStateFlow<List<LogLine>>(emptyList())
    val lines: StateFlow<List<LogLine>> = _lines.asStateFlow()

    @Synchronized
    fun add(tag: String, message: String) {
        val text = sanitize(message.trim())
        if (text.isEmpty()) return
        val line = LogLine(clock.format(Date()), sanitize(tag), text.take(MAX_CHARS))
        _lines.value = (_lines.value + line).takeLast(LIMIT)
    }

    /**
     * The word Firefox — and its FxA/ffvpn shortenings — never reaches any
     * user-visible text. Everything the log screen shows passes through
     * [add], and the services reuse this for the failure line the
     * notification shows, so the engine's own words ("FxA session OK",
     * ".../ffvpn/session.txt", "core=firefox") are rebranded at the edge
     * instead of at every call site. Case is preserved: Firefox→Clxy,
     * FIREFOX→CLXY, firefox→clxy.
     */
    fun sanitize(text: String): String {
        if (text.isEmpty()) return text
        var out = text
        out = FIREFOX.replace(out) { rebrand(it.value) }
        // "FxA" reads as an acronym, so it becomes CLXY rather than Clxy.
        out = out.replace("FxA", "CLXY")
        out = FXA.replace(out) { rebrand(it.value) }
        out = FFVPN.replace(out) { rebrand(it.value) }
        return out
    }

    private fun rebrand(match: String): String = when {
        match.all { it.isUpperCase() } -> "CLXY"
        match.first().isUpperCase() -> "Clxy"
        else -> "clxy"
    }

    private val FIREFOX = Regex("firefox", RegexOption.IGNORE_CASE)
    private val FXA = Regex("fxa", RegexOption.IGNORE_CASE)
    private val FFVPN = Regex("ffvpn", RegexOption.IGNORE_CASE)

    @Synchronized
    fun clear() {
        _lines.value = emptyList()
    }

    /** The whole log as plain text, for sharing it out of the app. */
    fun text(): String = _lines.value.joinToString("\n")
}
