package com.clxypro.vpn.core

import android.content.Context
import com.clxypro.vpn.vpn.Health
import com.clxypro.vpn.vpn.Server
import com.clxypro.vpn.vpn.ServerList
import com.clxypro.vpn.vpn.Store
import java.util.concurrent.ConcurrentHashMap

/** One selectable server: [key] is what the core dials, the rest is shown. */
data class ServerOption(
    val key: String,
    val label: String,
    val detail: String,
    val reachable: Boolean? = null,
    /** Measured round-trip in ms; -1 until a test has measured it. */
    val latencyMs: Int = -1
)

/**
 * Manual server choice for the cores whose servers are a named list: Firefox's
 * free exits, Lantern's radiance relays and SSTP's VPN Gate relays. The choice
 * is kept per core in [Prefs]; an empty key means Auto — scan, rank, dial.
 */
object ServerChoice {
    private val SELECTABLE = setOf("firefox")

    // The last test per core, kept beyond the picker's own lifetime so reopening
    // it shows the same order, speeds and reachable set instead of the raw list.
    private val testedCache = ConcurrentHashMap<String, List<ServerOption>>()
    private val testedSource = ConcurrentHashMap<String, List<String>>()

    fun supports(coreId: String): Boolean = coreId in SELECTABLE

    /**
     * The cached test result, but only while the underlying list is unchanged;
     * a freshly installed relay list must not be hidden behind an old test.
     */
    fun lastTested(context: Context, coreId: String): List<ServerOption>? {
        if (testedSource[coreId] != options(context, coreId).map { it.key }) return null
        return testedCache[coreId]
    }

    /** The last list this core produced, without running anything. */
    fun options(context: Context, coreId: String): List<ServerOption> {
        val list = when (coreId) {
            "firefox" -> ServerList.readCache(Store.cacheFile(context)).map { firefoxOption(it) }
            else -> emptyList()
        }
        return list.distinctBy { it.key }.sortedWith(compareBy({ it.label.lowercase() }, { it.key }))
    }

    /**
     * Re-reads the list from the core's own source.
     */
    fun refresh(
        context: Context,
        coreId: String,
        stop: () -> Boolean,
        log: (String) -> Unit
    /**
     * The refresh the picker runs: freshest list, then a reachability test of
     * every entry. Only servers that answered are kept, fastest first.
     */
    fun tested(
        context: Context,
        coreId: String,
        stop: () -> Boolean,
        log: (String) -> Unit
    ): List<ServerOption> {
        refresh(context, coreId, stop, log)
        val list = when (coreId) {
            "firefox" -> ServerList.probe(ServerList.readCache(Store.cacheFile(context)), stop)
                .filter { it.health == Health.Alive }
                .map { firefoxOption(it.server, it.latencyMs) }
            else -> emptyList()
        }
        val result = list.distinctBy { it.key }
            .sortedWith(compareBy({ it.latencyMs }, { it.label.lowercase() }, { it.key }))
        testedSource[coreId] = options(context, coreId).map { it.key }
        testedCache[coreId] = result
        return result
    }

    /** The exit the user pinned for Firefox; null means Auto. */
    fun pinnedFirefox(context: Context): Server? {
        val key = Prefs.server("firefox")
        if (key.isBlank()) return null
        ServerList.readCache(Store.cacheFile(context)).firstOrNull { it.address == key }?.let { return it }
        // The address is dialled as-is even when the cached list no longer names
        // it; the label was kept with the choice so the UI can still show it.
        return Server("", Prefs.serverLabel("firefox").ifBlank { key }, "", key)
    }

    private fun firefoxOption(server: Server, latencyMs: Int = -1) =
        ServerOption(server.address, server.label, server.address, latencyMs = latencyMs)
}