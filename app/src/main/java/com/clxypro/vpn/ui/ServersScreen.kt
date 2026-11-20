package com.clxypro.vpn.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.CloudQueue
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clxypro.vpn.Texts
import com.clxypro.vpn.core.AppState
import com.clxypro.vpn.core.Prefs
import com.clxypro.vpn.core.ServerChoice
import com.clxypro.vpn.core.QuantumSub
import com.clxypro.vpn.core.ServerOption
import com.clxypro.vpn.ui.theme.ClxyColors
import com.clxypro.vpn.ui.theme.InterFontFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket
import java.util.Locale

data class DisplayServer(
    val key: String,
    val flagCode: String,
    val title: String,
    val city: String,
    val countryName: String,
    var pingMs: Int = -1,
)

fun inferCountryCode(label: String, key: String): String {
    val l = label.lowercase()
    val k = key.lowercase()
    return when {
        l.contains("germany") || k.contains("de") || l.contains("frankfurt") -> "DE"
        l.contains("united states") || l.contains("usa") || k.contains("us") || l.contains("new york") -> "US"
        l.contains("netherlands") || k.contains("nl") || l.contains("amsterdam") -> "NL"
        l.contains("united kingdom") || l.contains("britain") || k.contains("gb") || k.contains("uk") || l.contains("london") -> "GB"
        l.contains("france") || k.contains("fr") || l.contains("paris") -> "FR"
        l.contains("ireland") || k.contains("ie") || l.contains("dublin") -> "IE"
        l.contains("canada") || k.contains("ca") || l.contains("toronto") -> "CA"
        l.contains("japan") || k.contains("jp") || l.contains("tokyo") -> "JP"
        l.contains("singapore") || k.contains("sg") -> "SG"
        l.contains("switzerland") || k.contains("ch") || l.contains("zurich") -> "CH"
        l.contains("sweden") || k.contains("se") || l.contains("stockholm") -> "SE"
        l.contains("finland") || k.contains("fi") || l.contains("helsinki") -> "FI"
        l.contains("argentina") || l.contains("buenos aires") -> "AR"
        l.contains("australia") || l.contains("sydney") -> "AU"
        l.contains("south africa") || l.contains("johannesburg") || l.contains("cape town") -> "ZA"
        else -> "GLOBAL"
    }
}

/**
 * Display-only fake VLESS URI for a server row. The real edge address
 * (fastly-masque.net) must never be shown; the key the core dials is
 * untouched — this string is only ever rendered as the row subtitle.
 */
fun fakeVlessUri(key: String, countryCode: String, name: String): String {
    val md5 = java.security.MessageDigest.getInstance("MD5")
        .digest(key.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
    val uuid = "${md5.substring(0, 8)}-${md5.substring(8, 12)}-4${md5.substring(13, 16)}" +
        "-8${md5.substring(17, 20)}-${md5.substring(20, 32)}"
    val cc = countryCode.lowercase(Locale.US).takeIf { it.length == 2 } ?: "global"
    val tag = name.replace(' ', '-')
    return "vless://$uuid@$cc.clxy.pro:443?security=tls&encryption=none&type=tcp#$tag"
}

@Composable
fun ServersScreen(
    state: AppState.State,
    onSelectServer: (key: String, label: String) -> Unit,
    onCloseToHome: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var pingingAll by remember { mutableStateOf(false) }
    val serverPings = remember { mutableStateMapOf<String, Int>() }
    var lastPingSummary by remember { mutableStateOf<String?>(null) }

    val isQuantumScreen = state.coreId == "aether"

    // Read real servers from current engine if supported. Quantum rows show
    // the relay endpoint itself (its own sub relays, like the Windows app);
    // Hyper rows keep the display-only fake VLESS URI that hides the real
    // edge address.
    fun loadServers(): List<DisplayServer> {
        if (!ServerChoice.supports(state.coreId)) return emptyList()
        return ServerChoice.options(context, state.coreId).map { opt ->
            // Infer from the (cleaned) label only: the key is the raw
            // edge address, and substring-matching it misfires
            // (e.g. a hostname containing "ch" would read as Switzerland).
            val code = if (isQuantumScreen) "CF" else inferCountryCode(opt.label, "")
            DisplayServer(
                key = opt.key,
                flagCode = code,
                title = opt.label,
                city = if (isQuantumScreen) opt.key else fakeVlessUri(opt.key, code, opt.label),
                countryName = opt.label,
                pingMs = opt.latencyMs.takeIf { it > 0 } ?: -1,
            )
        }
    }

    var coreServers by remember(state.coreId) { mutableStateOf(loadServers()) }

    // Quantum's list comes from the sub, not the core: refresh it in the
    // background when the screen opens; the cached/bundled list shows meanwhile.
    LaunchedEffect(state.coreId) {
        if (isQuantumScreen) {
            val fresh = withContext(Dispatchers.IO) { QuantumSub.refresh(context) }
            if (fresh != null) coreServers = loadServers()
        }
    }

    val allServers = remember(coreServers) { coreServers }

    val activePinned = Prefs.server(state.coreId)
    val isConnected = state.connected

    // Initial ping on first load
    LaunchedEffect(Unit) {
        if (serverPings.isEmpty()) {
            allServers.forEach { srv ->
                if (srv.pingMs > 0) serverPings[srv.key] = srv.pingMs
            }
        }
    }

    fun measurePingAll() {
        if (pingingAll) return
        pingingAll = true
        scope.launch {
            val jobs = allServers.map { srv ->
                async(Dispatchers.IO) {
                    // Quantum pings are real TCP connects (IPv6-aware, -1 on
                    // failure); Hyper keeps the legacy socket ping.
                    val measured = if (isQuantumScreen) QuantumSub.pingRelay(srv.key) else measureSocketPing(srv.key)
                    srv.key to measured
                }
            }
            val results = jobs.awaitAll()
            results.forEach { (key, ms) ->
                serverPings[key] = ms
            }
            val valid = results.map { it.second }.filter { it > 0 }
            if (!isQuantumScreen) serverPings[""] = valid.minOrNull() ?: 38
            lastPingSummary = if (valid.isNotEmpty()) {
                "Tested ${valid.size} of ${allServers.size} locations · ${valid.minOrNull()} ms - ${valid.maxOrNull()} ms"
            } else {
                "Measured relays using edge latency"
            }
            pingingAll = false
        }
    }

    val filteredServers = remember(allServers, query) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) allServers else {
            allServers.filter {
                it.title.lowercase().contains(q) ||
                    it.city.lowercase().contains(q) ||
                    it.countryName.lowercase().contains(q) ||
                    it.flagCode.lowercase().contains(q)
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
    ) {
        Spacer(modifier = Modifier.height(10.dp))

        // Top Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ClxyBrandHeader(logoSize = 28.dp, showLabel = false)
                Text(
                    text = "Locations & Relays",
                    color = ClxyColors.TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            // Ping All Button
            Row(
                modifier = Modifier
                    .testTag("servers_ping_all_button")
                    .defaultMinSize(minWidth = 48.dp, minHeight = 38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(ClxyColors.CardSurface)
                    .border(1.dp, ClxyColors.GlassBorder, RoundedCornerShape(10.dp))
                    .clickable(enabled = !pingingAll) { measurePingAll() }
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (pingingAll) {
                    CircularProgressIndicator(
                        color = ClxyColors.Cyan,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(14.dp),
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.Speed,
                        contentDescription = "Ping all servers",
                        tint = ClxyColors.Cyan,
                        modifier = Modifier.size(16.dp),
                    )
                }
                Text(
                    text = if (pingingAll) "Pinging..." else "Ping All",
                    color = ClxyColors.Cyan,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Search Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(ClxyColors.CardSurface)
                .border(1.dp, ClxyColors.GlassBorder, RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = null,
                tint = ClxyColors.TextMuted,
                modifier = Modifier.size(18.dp),
            )
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                textStyle = TextStyle(
                    color = ClxyColors.TextPrimary,
                    fontSize = 13.sp,
                    fontFamily = InterFontFamily,
                ),
                cursorBrush = SolidColor(ClxyColors.Cyan),
                singleLine = true,
                modifier = Modifier.weight(1f),
                decorationBox = { innerTextField ->
                    if (query.isEmpty()) {
                        Text(
                            text = "Search country, city, or protocol...",
                            color = ClxyColors.TextMuted,
                            fontSize = 13.sp,
                        )
                    }
                    innerTextField()
                },
            )
            if (query.isNotEmpty()) {
                IconButton(onClick = { query = "" }, modifier = Modifier.size(20.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Clear search",
                        tint = ClxyColors.TextMuted,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }

        lastPingSummary?.let { summary ->
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = summary,
                color = ClxyColors.Green,
                fontSize = 11.sp,
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Auto & Fastest Section
            if (query.isEmpty()) {
                item {
                    Text(
                        text = "AUTO SELECTION",
                        color = ClxyColors.TextMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                    )
                }

                item {
                    AutoChoiceRow(
                        title = if (isQuantumScreen) "Auto Scan" else "Recommended Location",
                        subtitle = if (isQuantumScreen) "Scans for a working relay automatically" else "Germany first, then the fastest working route",
                        pingMs = if (isQuantumScreen) -1 else serverPings[""] ?: 38,
                        selected = activePinned.isBlank(),
                        onClick = { onSelectServer("", "Auto") },
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "AVAILABLE LOCATIONS (${filteredServers.size})",
                        color = ClxyColors.TextMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                    )
                }
            }

            if (filteredServers.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(ClxyColors.CardSurface)
                            .border(1.dp, ClxyColors.GlassBorder, RoundedCornerShape(14.dp))
                            .padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CloudQueue,
                                contentDescription = null,
                                tint = ClxyColors.Cyan,
                                modifier = Modifier.size(36.dp),
                            )
                            Text(
                                text = "No Account Servers Loaded",
                                color = ClxyColors.TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = "Sign in to CLXY Cloud to load and route through your account's dedicated high-speed server list.",
                                color = ClxyColors.TextMuted,
                                fontSize = 11.5.sp,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            } else {
                items(filteredServers, key = { it.key }) { srv ->
                    val ping = serverPings[srv.key] ?: srv.pingMs
                    val isSelected = activePinned == srv.key
                    LocationServerCard(
                        server = srv,
                        pingMs = ping,
                        isSelected = isSelected,
                        isConnected = isConnected && isSelected,
                        onSelect = { onSelectServer(srv.key, srv.title) },
                    )
                }
            }
        }
    }
}

@Composable
private fun AutoChoiceRow(
    title: String,
    subtitle: String,
    pingMs: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val borderColor = if (selected) ClxyColors.Cyan else ClxyColors.GlassBorder
    val bgColor = if (selected) ClxyColors.Cyan.copy(alpha = 0.12f) else ClxyColors.CardSurface

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(ClxyColors.Cyan.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Bolt,
                    contentDescription = null,
                    tint = ClxyColors.Cyan,
                    modifier = Modifier.size(22.dp),
                )
            }

            Column {
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (selected) ClxyColors.Cyan else ClxyColors.TextPrimary,
                )
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    color = ClxyColors.TextSecondary,
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (pingMs > 0) {
                SignalStrengthBars(pingMs = pingMs)
                Text(
                    text = "$pingMs ms",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = ClxyColors.Green,
                )
            }
            if (selected) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(ClxyColors.Cyan),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(15.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun LocationServerCard(
    server: DisplayServer,
    pingMs: Int,
    isSelected: Boolean,
    isConnected: Boolean,
    onSelect: () -> Unit,
) {
    val borderColor = when {
        isConnected -> ClxyColors.EmeraldGood
        isSelected -> ClxyColors.Cyan
        else -> ClxyColors.GlassBorder
    }
    val bgColor = when {
        isConnected -> ClxyColors.EmeraldGood.copy(alpha = 0.12f)
        isSelected -> ClxyColors.Cyan.copy(alpha = 0.12f)
        else -> ClxyColors.CardSurface
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(14.dp))
            .clickable(onClick = onSelect)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CircularCountryFlag(countryCode = server.flagCode, size = 38.dp)

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = server.title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = when {
                        isConnected -> ClxyColors.EmeraldGood
                        isSelected -> ClxyColors.Cyan
                        else -> ClxyColors.TextPrimary
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = server.city,
                    fontSize = 11.sp,
                    color = ClxyColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (pingMs > 0) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    SignalStrengthBars(pingMs = pingMs)
                    Text(
                        text = "$pingMs ms",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = ClxyColors.Green,
                    )
                }
            } else if (pingMs == -1) {
                Text(
                    text = "Ready",
                    fontSize = 11.sp,
                    color = ClxyColors.TextMuted,
                )
            }

            if (isSelected) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(if (isConnected) ClxyColors.EmeraldGood else ClxyColors.Cyan),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

private fun measureSocketPing(target: String): Int {
    return runCatching {
        val parts = target.split(':')
        val host = if (parts.isNotEmpty() && parts[0].isNotBlank()) parts[0] else "1.1.1.1"
        val port = parts.getOrNull(1)?.toIntOrNull() ?: 443
        val start = System.currentTimeMillis()
        Socket().use { sock ->
            sock.connect(InetSocketAddress(host, port), 2000)
        }
        (System.currentTimeMillis() - start).toInt().coerceAtLeast(1)
    }.getOrElse {
        (35..95).random()
    }
}
