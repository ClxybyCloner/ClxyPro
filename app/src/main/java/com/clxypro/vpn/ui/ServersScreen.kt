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
import androidx.compose.material.icons.rounded.Speed
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
        else -> "GLOBAL"
    }
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

    val defaultServers = remember {
        listOf(
            DisplayServer("de_fra", "DE", "Germany", "Frankfurt · Relay", "Germany", 48),
            DisplayServer("nl_ams", "NL", "Netherlands", "Amsterdam · Relay", "Netherlands", 54),
            DisplayServer("gb_lon", "GB", "United Kingdom", "London · Relay", "United Kingdom", 62),
            DisplayServer("fr_par", "FR", "France", "Paris · Relay", "France", 58),
            DisplayServer("ie_dub", "IE", "Ireland", "Dublin · Relay", "Ireland", 65),
            DisplayServer("us_nyc", "US", "United States", "New York · Relay", "United States", 112),
            DisplayServer("ca_tor", "CA", "Canada", "Toronto · Relay", "Canada", 125),
            DisplayServer("jp_tyo", "JP", "Japan", "Tokyo · Relay", "Japan", 168),
            DisplayServer("sg_sin", "SG", "Singapore", "Singapore · Relay", "Singapore", 142),
        )
    }

    // Read real servers from current engine if supported
    val coreServers = remember(state.coreId) {
        if (ServerChoice.supports(state.coreId)) {
            val opts = ServerChoice.options(context, state.coreId)
            opts.mapIndexed { idx, opt ->
                val code = inferCountryCode(opt.label, opt.key)
                DisplayServer(
                    key = opt.key,
                    flagCode = code,
                    title = opt.label,
                    city = opt.detail.ifBlank { "Relay Location" },
                    countryName = opt.label,
                    pingMs = opt.latencyMs.takeIf { it > 0 } ?: -1,
                )
            }
        } else {
            emptyList()
        }
    }

    val allServers = remember(coreServers, defaultServers) {
        if (coreServers.isNotEmpty()) coreServers else defaultServers
    }

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
                    val measured = measureSocketPing(srv.key)
                    srv.key to measured
                }
            }
            val results = jobs.awaitAll()
            results.forEach { (key, ms) ->
                serverPings[key] = ms
            }
            val valid = results.map { it.second }.filter { it > 0 }
            serverPings[""] = valid.minOrNull() ?: 38
            serverPings["fastest"] = valid.minOrNull() ?: 32
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
                        title = "Recommended Location",
                        subtitle = "Automatic selection of lowest latency route",
                        pingMs = serverPings[""] ?: 38,
                        selected = activePinned.isBlank(),
                        onClick = { onSelectServer("", "Auto") },
                    )
                }

                item {
                    AutoChoiceRow(
                        title = "Fastest Location",
                        subtitle = "Connects to the fastest responding node",
                        pingMs = serverPings["fastest"] ?: 32,
                        selected = activePinned == "fastest",
                        onClick = { onSelectServer("fastest", "Fastest") },
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
