package com.clxypro.vpn.core

import android.content.Context
import android.content.SharedPreferences
import com.clxypro.vpn.vpn.Store
import java.util.Locale

enum class AppLanguage(val code: String) {
    EN("en"),
    FA("fa"),
    RU("ru"),
    ES("es"),
    ZH("zh")
}

/** Only Persian is laid out right to left; the rest share the LTR layout. */
val AppLanguage.rtl: Boolean get() = this == AppLanguage.FA

object Prefs {
    private const val NAME = "settings"
    private const val KEY_MODE = "mode"
    private const val KEY_CORE = "core"
    private const val KEY_SHARE_LAN = "share_lan"
    // The stored key keeps its old name so existing installs keep their choice.
    private const val KEY_CONNECT_VIBRATION = "connect_sound"
    private const val KEY_AUTO_RETRY = "auto_retry"
    private const val KEY_TOR_BRIDGES = "tor_bridges"
    private const val KEY_PSIPHON_CONDUIT = "psiphon_conduit"
    private const val KEY_PSIPHON_EXIT = "psiphon_exit"
    private const val KEY_PSIPHON_REGION = "psiphon_region"
    private const val KEY_TOOLS_REPLACE = "tools_replace"
    private const val KEY_VPNGATE_FILTER = "vpngate_filter"
    private const val KEY_SOCKS_PORT = "socks_port"
    private const val KEY_HTTP_PORT = "http_port"

    const val LOOPBACK = "127.0.0.1"
    const val DEFAULT_SOCKS_PORT = 1080
    const val DEFAULT_HTTP_PORT = 8080
    const val MIN_PORT = 1024
    const val MAX_PORT = 65535

    @Volatile
    private var store: SharedPreferences? = null

    @Volatile
    private var app: Context? = null

    fun init(context: Context) {
        app = context.applicationContext
        store = context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)
    }

    private fun prefs(): SharedPreferences =
        store ?: error("Prefs.init() was not called")

    var mode: AppState.Mode
        get() = runCatching {
            AppState.Mode.valueOf(prefs().getString(KEY_MODE, null).orEmpty())
        }.getOrDefault(AppState.Mode.VPN)
        set(value) {
            prefs().edit().putString(KEY_MODE, value.name).apply()
        }

    /** Empty means "whatever the registry lists first"; the picker always writes an id. */
    var coreId: String
        get() = prefs().getString(KEY_CORE, "").orEmpty()
        set(value) {
            prefs().edit().putString(KEY_CORE, value).apply()
        }

    /**
     * The language picker lives in Store, and its empty tag means "follow the
     * system". This maps that choice onto the four languages the ported screens
     * speak.
     */
    var language: AppLanguage
        get() = AppLanguage.entries.firstOrNull { it.code == resolvedTag() } ?: AppLanguage.EN
        set(value) {
            app?.let { Store.setLanguage(it, value.code) }
        }

    private fun resolvedTag(): String {
        val chosen = app?.let { Store.language(it) }.orEmpty()
        val tag = chosen.ifBlank { Locale.getDefault().language }
        return tag.lowercase(Locale.ROOT).take(2)
    }

    var vpnExcludedApps: Set<String>
        get() = prefs().getStringSet("vpn_excluded_apps", emptySet()).orEmpty().toSet()
        set(value) {
            prefs().edit().putStringSet("vpn_excluded_apps", value.toSet()).apply()
        }

    var shareLan: Boolean
        get() = prefs().getBoolean(KEY_SHARE_LAN, false)
        set(value) {
            prefs().edit().putBoolean(KEY_SHARE_LAN, value).apply()
        }

    /** Serial unlock, shared by Hyper and Quantum — one serial, one unlock,
     *  asked once, on first connect. The stored key keeps its old name so
     *  devices already unlocked stay unlocked. */
    var quantumUnlocked: Boolean
        get() = prefs().getBoolean("quantum_unlocked", false)
        set(value) {
            prefs().edit().putBoolean("quantum_unlocked", value).apply()
        }

    /** Hyper unlocked by a valid serial; asked once, on Hyper's first
     *  connect. Same serials as Quantum, but a separate flag — a Quantum
     *  unlock does not unlock Hyper. */
    var hyperUnlocked: Boolean
        get() = prefs().getBoolean("hyper_unlocked", false)
        set(value) {
            prefs().edit().putBoolean("hyper_unlocked", value).apply()
        }

    /** SHA-256 hex of the activated serial (dashes stripped, uppercased).
     *  Kept so every connect can ask the owner's panel whether this
     *  serial is still good; only the hash is stored, never the serial
     *  itself. Empty on devices unlocked before v1.5.35 — those ask for
     *  the serial once more, on their next connect, to capture it. */
    var serialHash: String
        get() = prefs().getString("serial_hash", "") ?: ""
        set(value) {
            prefs().edit().putString("serial_hash", value).apply()
        }

    var connectVibration: Boolean
        get() = prefs().getBoolean(KEY_CONNECT_VIBRATION, true)
        set(value) {
            prefs().edit().putBoolean(KEY_CONNECT_VIBRATION, value).apply()
        }

    var autoRetry: Boolean
        get() = prefs().getBoolean(KEY_AUTO_RETRY, true)
        set(value) {
            prefs().edit().putBoolean(KEY_AUTO_RETRY, value).apply()
        }

    /** Tor only gets past a blocking network through obfs4 bridges. */
    var torUseBridges: Boolean
        get() = prefs().getBoolean(KEY_TOR_BRIDGES, false)
        set(value) {
            prefs().edit().putBoolean(KEY_TOR_BRIDGES, value).apply()
        }

    /**
     * Off by default: Psiphon's own servers are faster where they are reachable.
     * Conduit is for the links where every one of them is blocked.
     */
    var psiphonConduit: Boolean
        get() = prefs().getBoolean(KEY_PSIPHON_CONDUIT, false)
        set(value) {
            prefs().edit().putBoolean(KEY_PSIPHON_CONDUIT, value).apply()
        }

    /**
     * Psiphon exit behind Quantum: the MASQUE transport stays, Psiphon picks
     * the egress country. Off by default; only the aether core reads it.
     */
    var psiphonExit: Boolean
        get() = prefs().getBoolean(KEY_PSIPHON_EXIT, false)
        set(value) {
            prefs().edit().putBoolean(KEY_PSIPHON_EXIT, value).apply()
        }

    /** Egress region ISO code ("" = fastest, Psiphon picks). */
    var psiphonRegion: String
        get() = prefs().getString(KEY_PSIPHON_REGION, "") ?: ""
        set(value) {
            prefs().edit().putString(KEY_PSIPHON_REGION, value).apply()
        }

    /** Applying a scanned list drops what the core used before, instead of adding to it. */
    var replaceToolLists: Boolean
        get() = prefs().getBoolean(KEY_TOOLS_REPLACE, true)
        set(value) {
            prefs().edit().putBoolean(KEY_TOOLS_REPLACE, value).apply()
        }

    /** Relays hosted in Iran, Russia, China and North Korea never reach the list. */
    var vpngateFilterCountries: Boolean
        get() = prefs().getBoolean(KEY_VPNGATE_FILTER, true)
        set(value) {
            prefs().edit().putBoolean(KEY_VPNGATE_FILTER, value).apply()
        }

    /** SOCKS5 port the app listens on, and the port the engines bind themselves to. */
    var socksPort: Int
        get() = prefs().getInt(KEY_SOCKS_PORT, DEFAULT_SOCKS_PORT)
        set(value) {
            prefs().edit().putInt(KEY_SOCKS_PORT, value.coerceIn(MIN_PORT, MAX_PORT)).apply()
        }

    var httpPort: Int
        get() = prefs().getInt(KEY_HTTP_PORT, DEFAULT_HTTP_PORT)
        set(value) {
            prefs().edit().putInt(KEY_HTTP_PORT, value.coerceIn(MIN_PORT, MAX_PORT)).apply()
        }

    /** The server the user pinned for [coreId]; empty means Auto. */
    fun server(coreId: String): String = prefs().getString("server_$coreId", "").orEmpty()

    /** Kept with the key so the choice can be shown before the list is read again. */
    fun serverLabel(coreId: String): String = prefs().getString("server_label_$coreId", "").orEmpty()

    fun setServer(coreId: String, key: String, label: String) {
        prefs().edit()
            .putString("server_$coreId", key)
            .putString("server_label_$coreId", label)
            .apply()
    }

    var dnsProvider: String
        get() = prefs().getString("dns_provider", "cloudflare").orEmpty()
        set(value) { prefs().edit().putString("dns_provider", value).apply() }

    var customDns: String
        get() = prefs().getString("custom_dns", "").orEmpty()
        set(value) { prefs().edit().putString("custom_dns", value).apply() }

    var mtu: Int
        get() = prefs().getInt("vpn_mtu", 1500)
        set(value) { prefs().edit().putInt("vpn_mtu", value).apply() }

    var bypassLan: Boolean
        get() = prefs().getBoolean("bypass_lan", true)
        set(value) { prefs().edit().putBoolean("bypass_lan", value).apply() }

    var bypassDomestic: Boolean
        get() = prefs().getBoolean("bypass_domestic", false)
        set(value) { prefs().edit().putBoolean("bypass_domestic", value).apply() }

    var blockAds: Boolean
        get() = prefs().getBoolean("block_ads", false)
        set(value) { prefs().edit().putBoolean("block_ads", value).apply() }

    var tlsFragmentMode: String
        get() = prefs().getString("tls_fragment_mode", "off").orEmpty()
        set(value) { prefs().edit().putString("tls_fragment_mode", value).apply() }

    var showSpeedInNotification: Boolean
        get() = prefs().getBoolean("show_speed_notification", true)
        set(value) { prefs().edit().putBoolean("show_speed_notification", value).apply() }

    /** Last version an update notification was shown for; avoids re-notifying. */
    var lastNotifiedUpdate: String
        get() = prefs().getString("last_notified_update", "").orEmpty()
        set(value) { prefs().edit().putString("last_notified_update", value).apply() }

    val socksAddress: String get() = "$LOOPBACK:$socksPort"
    val httpAddress: String get() = "$LOOPBACK:$httpPort"
}
