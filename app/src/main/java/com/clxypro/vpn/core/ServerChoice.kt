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
    private val SELECTABLE = setOf("firefox", "lantern", "sstp", "openrung", "sunlion")

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
            "lantern" -> LanternServers.readCache(LanternServers.workDir(context)).map { lanternOption(it) }
            "openrung" -> OpenRungServers.readCache(OpenRungServers.workDir(context)).map { openRungOption(it) }
            "sstp" -> sstpOptions(context)
            "sunlion" -> sunlionOptions(context)
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
    ): List<ServerOption> {
        when (coreId) {
            "firefox" -> ServerList.fetch(Store.binary(context), Store.cacheFile(context), stop)
            "lantern" -> LanternServers.fetch(
                LanternServers.binary(context), LanternServers.workDir(context), stop, log
            )
            "openrung" -> OpenRungServers.fetch(
                OpenRungServers.binary(context), OpenRungServers.workDir(context), stop, log
            )
            "sstp" -> {
                val freshGate = runCatching { VpnGate.fetch(log) }.getOrDefault(emptyList())
                val nonRestricted = freshGate.filterNot(VpnGate::isRestricted)
                if (nonRestricted.isNotEmpty()) {
                    SstpRelays.apply(context, nonRestricted.map { it.endpoint }.distinct().take(40))
                }
            }
            "sunlion" -> {
                val presets = SunAndLionScanner.presets()
                val expanded = EdgeScanner.expand(presets.flatMap { it.ranges }.take(15), EdgeMode.QUICK)
                val hits = EdgeScanner.scan(
                    expanded,
                    presets.flatMap { it.snis }.distinct(),
                    presets.flatMap { it.hosts }.distinct(),
                    concurrency = 6,
                    mode = EdgeMode.QUICK,
                    stop = stop
                )
                if (hits.isNotEmpty()) {
                    val store = SunAndLionStore(context)
                    store.saveScan(
                        SunAndLionCandidates(
                            hits.map { it.ip }.distinct().take(10),
                            hits.map { it.sni }.distinct().take(5),
                            hits.map { it.host }.distinct().take(5)
                        )
                    )
                }
            }
        }
        return options(context, coreId)
    }

    /**
     * The refresh the picker runs: freshest list, then a reachability test of
     * every entry. Only servers that answered are kept, fastest first. What
     * counts as answered is what the core itself needs: a TLS handshake for
     * Firefox and SSTP, a TCP connect for Lantern's mixed protocols.
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
            "lantern" -> LanternServers.probe(
                LanternServers.readCache(LanternServers.workDir(context)), stop, log
            )
                .filter { it.latencyMs >= 0 }
                .map { lanternOption(it.server, it.latencyMs) }
            "openrung" -> OpenRungServers.probe(
                OpenRungServers.readCache(OpenRungServers.workDir(context)), stop, log
            )
                .filter { it.latencyMs >= 0 }
                .map { openRungOption(it.relay, it.latencyMs) }
            "sstp" -> {
                val countries = VpnGate.loadCache(context).associateBy({ it.key }, { it.country })
                SstpScanner.scan(SstpScanner.loadEndpoints(context), stop, log)
                    .filter { it.health == SstpHealth.Alive }
                    .map { sstpOption(it.endpoint, countries[it.endpoint.toString()].orEmpty(), it.latencyMs) }
            }
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

    /** The relay the user pinned for Lantern; null means Auto. */
    fun pinnedLantern(context: Context): RadianceServer? {
        val key = Prefs.server("lantern")
        if (key.isBlank()) return null
        return LanternServers.readCache(LanternServers.workDir(context)).firstOrNull { it.tag == key }
            ?: RadianceServer(key, "", "", "", null)
    }

    /** The relay the user pinned for OpenRung; null means Auto. */
    fun pinnedOpenRung(context: Context): OpenRungRelay? {
        val key = Prefs.server("openrung")
        if (key.isBlank()) return null
        return OpenRungServers.readCache(OpenRungServers.workDir(context)).firstOrNull { it.id == key }
            ?: OpenRungRelay(key, Prefs.serverLabel("openrung"), "", "", "", "", null)
    }

    /** The relay the user pinned for SSTP; null means Auto. */
    fun pinnedSstp(): SstpEndpoint? {
        val key = Prefs.server("sstp")
        if (key.isBlank()) return null
        return SstpEndpoint.parse(key)
    }

    private fun firefoxOption(server: Server, latencyMs: Int = -1) =
        ServerOption(server.address, server.label, server.address, latencyMs = latencyMs)

    private fun lanternOption(server: RadianceServer, latencyMs: Int = -1): ServerOption {
        val (title, detail) = formatLanternServer(server.tag, server.country, server.protocol)
        return ServerOption(
            server.tag,
            title,
            detail,
            server.reachable,
            latencyMs
        )
    }

    private fun formatLanternServer(tag: String, country: String, protocol: String): Pair<String, String> {
        val lower = tag.lowercase()
        val city = when {
            lower.contains("-fra-") || lower.contains("frankfurt") -> "Frankfurt"
            lower.contains("-lon-") || lower.contains("london") -> "London"
            lower.contains("-dub-") || lower.contains("dublin") -> "Dublin"
            lower.contains("-par-") || lower.contains("paris") -> "Paris"
            lower.contains("-ams-") || lower.contains("amsterdam") -> "Amsterdam"
            lower.contains("-arn-") || lower.contains("stockholm") -> "Stockholm"
            lower.contains("-hel-") || lower.contains("helsinki") -> "Helsinki"
            lower.contains("-mil-") || lower.contains("milan") -> "Milan"
            lower.contains("-mad-") || lower.contains("madrid") -> "Madrid"
            lower.contains("-zrh-") || lower.contains("zurich") -> "Zurich"
            lower.contains("-vie-") || lower.contains("vienna") -> "Vienna"
            lower.contains("-prg-") || lower.contains("prague") -> "Prague"
            lower.contains("-sin-") || lower.contains("singapore") -> "Singapore"
            lower.contains("-nrt-") || lower.contains("tokyo") -> "Tokyo"
            else -> ""
        }
        val countryName = when (country.uppercase()) {
            "DE" -> "Germany"
            "GB", "UK" -> "United Kingdom"
            "IE" -> "Ireland"
            "FR" -> "France"
            "NL" -> "Netherlands"
            "SE" -> "Sweden"
            "FI" -> "Finland"
            "US" -> "United States"
            "JP" -> "Japan"
            "SG" -> "Singapore"
            else -> if (country.isNotBlank()) country else "Global Relay"
        }
        val protoName = when {
            lower.contains("vless") -> "VLESS Vision"
            lower.contains("ss2022") || lower.contains("shadowsocks") -> "Shadowsocks"
            lower.contains("hysteria") || lower.contains("hy2") -> "Hysteria 2"
            protocol.isNotBlank() -> protocol.replaceFirstChar { it.uppercase() }
            else -> "CLXY Relay"
        }
        val title = if (city.isNotBlank()) "$countryName · $city" else countryName
        val shortId = if (tag.length > 8) tag.takeLast(7) else tag
        val detail = "$protoName · #$shortId"
        return title to detail
    }

    private fun sstpOption(endpoint: SstpEndpoint, country: String, latencyMs: Int = -1) = ServerOption(
        endpoint.toString(),
        if (country.isBlank()) endpoint.toString() else "$country · $endpoint",
        "",
        latencyMs = latencyMs
    )

    private fun lanternLabel(server: RadianceServer): String {
        val (title, _) = formatLanternServer(server.tag, server.country, server.protocol)
        return title
    }

    private fun openRungOption(relay: OpenRungRelay, latencyMs: Int = -1) = ServerOption(
        relay.id,
        openRungLabel(relay),
        "${relay.protocol} · ${relay.label}",
        relay.reachable,
        latencyMs
    )

    private fun openRungLabel(relay: OpenRungRelay): String {
        val country = relay.country.ifBlank { relay.label.ifBlank { relay.id } }
        val place = relay.city.ifBlank { relay.label }
        return if (place.isBlank()) country else "$country · $place"
    }

    private fun sstpOptions(context: Context): List<ServerOption> {
        val countries = VpnGate.loadCache(context).associateBy({ it.key }, { it.country })
        return SstpScanner.loadEndpoints(context).map { sstpOption(it, countries[it.toString()].orEmpty()) }
    }

    private fun sunlionOptions(context: Context): List<ServerOption> {
        val store = SunAndLionStore(context)
        val saved = store.load()
        if (saved == null || saved.ips.isEmpty()) {
            return listOf(
                ServerOption("auto_edge", "Cloudflare · Auto Edge", "Fast Anycast CDN Edge", reachable = true),
                ServerOption("fastly_edge", "Fastly · Global Edge", "High Performance CDN", reachable = true),
                ServerOption("gcore_edge", "G-Core · Anycast Edge", "Low Latency Routing", reachable = true)
            )
        }
        return saved.ips.mapIndexed { idx, ip ->
            val host = saved.hosts.getOrNull(idx) ?: saved.snis.getOrNull(idx) ?: "CDN Edge"
            ServerOption(ip, "Edge Node #$ip", host, reachable = true)
        }
    }
}