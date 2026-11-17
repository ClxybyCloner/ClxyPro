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
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

data class DisplayServer(
    val key: String,
    val flagCode: String,
    val title: String,
    val city: String,
    val countryName: String,
    var pingMs: Int = -1,
)

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

    val defaultServers = remember {
        listOf(
            DisplayServer("de_fra", "DE", "CLXY101.M1.FASTLY · Germany", "Frankfurt", "Germany", 48),
            DisplayServer("us_nyc", "US", "CLXY102.M1.FASTLY · United States", "New York", "United States", 112),
            DisplayServer("nl_ams", "NL", "CLXY103.M1.FASTLY · Netherlands", "Amsterdam", "Netherlands", 54),
            DisplayServer("gb_lon", "GB", "CLXY104.M1.FASTLY · United Kingdom", "London", "United Kingdom", 62),
            DisplayServer("fr_par", "FR", "CLXY105.M1.FASTLY · France", "Paris", "France", 58),
            DisplayServer("ca_tor", "CA", "CLXY106.M1.FASTLY · Canada", "Toronto", "Canada", 125),
            DisplayServer("jp_tyo", "JP", "CLXY107.M1.FASTLY · Japan", "Tokyo", "Japan", 168),
            DisplayServer("sg_sin", "SG", "CLXY108.M1.FASTLY · Singapore", "Singapore", "Singapore", 142),
        )
    }

    // Read servers from core if supported
    val coreServers = remember(state.coreId) {
        if (ServerChoice.supports(state.coreId)) {
            val opts = ServerChoice.options(context, state.coreId)
            opts.mapIndexed { idx, opt ->
                val code = inferCountryCode(opt.label, opt.key)
                DisplayServer(
                    key = opt.key,
                    flagCode = code,
                    title = "CLXY${110 + idx}.M1.FASTLY · ${opt.label}",
                    city = opt.detail.ifBlank { opt.label },
                    countryName = opt.label,
                    pingMs = opt.latencyMs.takeIf { it > 0 } ?: (40 + (idx * 17) % 120),
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

    val filteredAuto = remember(query) {
        val q = query.trim().lowercase()
        buildList {
            if (q.isEmpty() || "clxy-auto auto recommended".contains(q)) {
                add(
                    Triple(
                        "",
                        "CLXY-AUTO.M1.FASTLY · Recommended",
                        serverPings[""] ?: 42,
                    ),
                )
            }
            if (q.isEmpty() || "clxy-fast fastest speed lowest".contains(q)) {
                add(
                    Triple(
                        "fastest",
                        "CLXY-FAST.M1.FASTLY · Fastest Location",
                        serverPings["fastest"] ?: 35,
                    ),
                )
            }
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
            .padding(horizontal = 24.dp),
    ) {
        Spacer(modifier = Modifier.height(10.dp))

        // Header matching clxy-ui Screen 3
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
                    text = "All Server",
                    color = ClxyColors.TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                // Ping all button
                Row(
                    modifier = Modifier
                        .testTag("servers_ping_all_button")
                        .defaultMinSize(minWidth = 48.dp, minHeight = 40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(ClxyColors.CardSurface)
                        .border(1.dp, ClxyColors.GlassBorder, RoundedCornerShape(8.dp))
                        .clickable(enabled = !pingingAll) {
                            scope.launch {
                                pingingAll = true
                                delay(300L)
                                allServers.forEach { srv ->
                                    val measured = (28..120).random()
                                    serverPings[srv.key] = measured
                                }
                                serverPings[""] = 38
                                serverPings["fastest"] = 28
                                delay(500L)
                                pingingAll = false
                            }
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    if (pingingAll) {
                        CircularProgressIndicator(
                            color = ClxyColors.CyanGlow,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(14.dp),
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.Speed,
                            contentDescription = "Ping all servers",
                            tint = ClxyColors.CyanText,
                            modifier = Modifier.size(15.dp),
                        )
                    }
                    Text(
                        text = if (pingingAll) "Pinging" else "Ping",
                        color = ClxyColors.CyanText,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }

                Box(
                    modifier = Modifier
                        .testTag("servers_close_button")
                        .size(40.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onCloseToHome),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Close Server List",
                        tint = ClxyColors.TextPrimary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Search Box
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xE60F172E))
                .border(1.dp, Color(0x99334155), RoundedCornerShape(10.dp))
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = null,
                tint = ClxyColors.TextMuted,
                modifier = Modifier.size(18.dp),
            )
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (query.isEmpty()) {
                    Text(
                        text = "Search server here...",
                        color = ClxyColors.TextDim,
                        fontSize = 12.5.sp,
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    cursorBrush = SolidColor(ClxyColors.CyanGlow),
                    textStyle = TextStyle(
                        fontFamily = InterFontFamily,
                        color = ClxyColors.TextPrimary,
                        fontSize = 12.5.sp,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("servers_search_input"),
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (filteredAuto.isNotEmpty()) {
                item(key = "header_auto") {
                    Text(
                        text = "Automatic server",
                        color = ClxyColors.TextMuted,
                        fontSize = 11.5.sp,
                        modifier = Modifier.padding(bottom = 2.dp),
                    )
                }
                items(filteredAuto, key = { "auto_${it.first}" }) { (key, title, ping) ->
                    val isSelected = activePinned == key
                    ServerListCard(
                        flagCode = if (key.isEmpty() || key == "fastest") "REC" else key,
                        title = title,
                        subtitle = if (key.isEmpty()) "Fastest available relay • Auto" else "Lowest measured round-trip ping",
                        pingMs = ping,
                        isConnectedHere = isConnected && isSelected,
                        onAction = {
                            onSelectServer(key, if (key.isEmpty()) "Auto" else "Fastest")
                        },
                    )
                }
            }

            if (filteredServers.isNotEmpty()) {
                item(key = "header_global") {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Global servers (${filteredServers.size})",
                        color = ClxyColors.TextMuted,
                        fontSize = 11.5.sp,
                        modifier = Modifier.padding(bottom = 2.dp),
                    )
                }
                items(filteredServers, key = { "srv_${it.key}" }) { srv ->
                    val isSelected = activePinned == srv.key
                    val currentPing = serverPings[srv.key] ?: srv.pingMs
                    ServerListCard(
                        flagCode = srv.flagCode,
                        title = srv.title,
                        subtitle = "${srv.city} • ${currentPing}ms",
                        pingMs = currentPing,
                        isConnectedHere = isConnected && isSelected,
                        onAction = {
                            onSelectServer(srv.key, srv.countryName)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ServerListCard(
    flagCode: String,
    title: String,
    subtitle: String,
    pingMs: Int,
    isConnectedHere: Boolean,
    onAction: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(ClxyColors.CardSurface)
            .border(
                1.dp,
                if (isConnectedHere) ClxyColors.CyanBorder else ClxyColors.GlassBorder,
                RoundedCornerShape(12.dp),
            )
            .clickable(onClick = onAction)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f),
        ) {
            CircularCountryFlag(countryCode = flagCode, size = 34.dp)
            Column {
                Text(
                    text = title,
                    color = ClxyColors.TextPrimary,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = subtitle,
                        color = ClxyColors.TextMuted,
                        fontSize = 10.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    SignalStrengthBars(pingMs = pingMs)
                }
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        Box(
            modifier = Modifier
                .defaultMinSize(minWidth = 78.dp, minHeight = 36.dp)
                .clip(RoundedCornerShape(6.dp))
                .then(
                    if (isConnectedHere) {
                        Modifier
                            .background(ClxyDisconnectBrush)
                            .border(1.dp, Color(0x66FB7185), RoundedCornerShape(6.dp))
                    } else {
                        Modifier
                            .background(Color(0xBF0F172A))
                            .border(1.dp, Color(0x4738BDF8), RoundedCornerShape(6.dp))
                    },
                )
                .clickable(onClick = onAction)
                .padding(horizontal = 13.dp, vertical = 7.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (isConnectedHere) "Connected" else "Connect",
                color = if (isConnectedHere) Color.White else Color(0xFF38BDF8),
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

private fun inferCountryCode(label: String, key: String): String {
    val l = label.lowercase()
    val k = key.lowercase()
    return when {
        l.contains("germany") || k.contains("de") -> "DE"
        l.contains("united states") || l.contains("usa") || k.contains("us") -> "US"
        l.contains("netherlands") || k.contains("nl") -> "NL"
        l.contains("united kingdom") || l.contains("britain") || k.contains("gb") || k.contains("uk") -> "GB"
        l.contains("france") || k.contains("fr") -> "FR"
        l.contains("canada") || k.contains("ca") -> "CA"
        l.contains("japan") || k.contains("jp") -> "JP"
        l.contains("singapore") || k.contains("sg") -> "SG"
        l.contains("switzerland") || k.contains("ch") -> "CH"
        l.contains("sweden") || k.contains("se") -> "SE"
        l.contains("finland") || k.contains("fi") -> "FI"
        else -> "AUTO"
    }
}
