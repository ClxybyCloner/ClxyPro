package com.clxypro.vpn.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clxypro.vpn.Texts
import com.clxypro.vpn.core.EdgeHit
import com.clxypro.vpn.core.EdgeMode
import com.clxypro.vpn.core.EdgeScanner
import com.clxypro.vpn.core.Prefs
import com.clxypro.vpn.core.SstpHealth
import com.clxypro.vpn.core.SstpRelays
import com.clxypro.vpn.core.SstpScanner
import com.clxypro.vpn.core.SunAndLionCandidates
import com.clxypro.vpn.core.SunAndLionScanner
import com.clxypro.vpn.core.SunAndLionStore
import com.clxypro.vpn.core.VpnGate
import com.clxypro.vpn.core.VpnGateServer
import com.clxypro.vpn.ui.theme.ClxyColors
import com.clxypro.vpn.vpn.Logs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Collectors behind the cores: CDN edge scanner for CLXY Prime and public relays for CLXY Ghost.
 * Full Cyber-Neon styling with high contrast text and balanced card spacing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val main = remember { Handler(Looper.getMainLooper()) }
    val presets = remember { SunAndLionScanner.presets() }
    var preset by remember { mutableStateOf(0) }

    var ranges by remember {
        mutableStateOf(presets.firstOrNull()?.ranges?.joinToString("\n").orEmpty())
    }
    var snis by remember {
        mutableStateOf(presets.firstOrNull()?.snis?.joinToString("\n").orEmpty())
    }
    var hosts by remember {
        mutableStateOf(presets.firstOrNull()?.hosts?.joinToString("\n").orEmpty())
    }
    var mode by remember { mutableStateOf(EdgeMode.BALANCED) }
    var concurrency by remember { mutableStateOf(10) }
    var status by remember { mutableStateOf("") }
    var progress by remember { mutableStateOf("") }
    var hits by remember { mutableStateOf<List<EdgeHit>>(emptyList()) }
    var scanning by remember { mutableStateOf(false) }
    val stop = remember { AtomicBoolean(false) }
    val store = remember { SunAndLionStore(context) }

    var gateServers by remember { mutableStateOf<List<VpnGateServer>>(emptyList()) }
    var gateStatus by remember { mutableStateOf("") }
    var gateBusy by remember { mutableStateOf(false) }
    var gateFilter by remember { mutableStateOf(Prefs.vpngateFilterCountries) }
    var gateReplace by remember { mutableStateOf(Prefs.replaceToolLists) }
    var health by remember { mutableStateOf<Map<String, SstpHealth>>(emptyMap()) }
    var latency by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var installedRelays by remember { mutableStateOf(SstpRelays.installedCount(context)) }

    fun usePreset(index: Int) {
        val choice = presets.getOrNull(index) ?: return
        preset = index
        ranges = choice.ranges.joinToString("\n")
        snis = choice.snis.joinToString("\n")
        hosts = choice.hosts.joinToString("\n")
    }

    fun clean(text: String): List<String> =
        text.lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }

    fun scout() {
        if (scanning) return
        scanning = true
        stop.set(false)
        status = ""
        progress = Texts.scoutingSnis
        scope.launch {
            val current = withContext(Dispatchers.IO) {
                val ips = EdgeScanner.expand(clean(ranges), mode)
                val sniList = clean(snis)
                val hostList = clean(hosts)
                if (ips.isEmpty() || sniList.isEmpty()) null
                else EdgeScanner.scout(ips, sniList, hostList, { stop.get() }) { Logs.add("tools", it) }
            }
            scanning = false
            progress = ""
            if (current == null) {
                status = Texts.scanNoHits
            } else {
                if (current.snis.isNotEmpty()) snis = current.snis.joinToString("\n")
                if (current.hosts.isNotEmpty()) hosts = current.hosts.joinToString("\n")
                status = if (current.snis.isEmpty()) {
                    Texts.scoutNoSni
                } else {
                    Texts.scoutResult(current.snis.size, current.hosts.size)
                }
            }
        }
    }

    fun scan() {
        if (scanning) {
            stop.set(true)
            return
        }
        scanning = true
        stop.set(false)
        hits = emptyList()
        status = ""
        progress = Texts.scanning
        scope.launch {
            val ips = withContext(Dispatchers.IO) { EdgeScanner.expand(clean(ranges), mode) }
            val result = withContext(Dispatchers.IO) {
                EdgeScanner.scan(
                    ips = ips,
                    snis = clean(snis),
                    hosts = clean(hosts),
                    concurrency = concurrency,
                    mode = mode,
                    stop = { stop.get() },
                ) { done, usable ->
                    main.post { progress = Texts.scanProgress(done, ips.size, usable) }
                }
            }
            scanning = false
            progress = ""
            hits = result
            status = if (result.isEmpty()) {
                Texts.scanNoHits
            } else {
                Texts.scanFinished(result.size, ips.size)
            }
        }
    }

    fun applyScan() {
        val ips = hits.map { it.ip }.distinct()
        val sniList = hits.map { it.sni }.distinct()
        val hostList = hits.map { it.host }.distinct()
        if (ips.isEmpty() || sniList.isEmpty()) return
        store.saveScan(SunAndLionCandidates(ips, sniList, hostList))
        status = Texts.scannerKept(true, ips.size, sniList.size, hostList.size)
    }

    fun copyHits() {
        val text = hits.joinToString("\n") { "${it.ip}\t${it.sni}\t${it.host}\t${it.latencyMs}ms" }
        val manager = context.getSystemService(ClipboardManager::class.java) ?: return
        manager.setPrimaryClip(ClipData.newPlainText(Texts.ipScanner, text))
        status = Texts.copied
    }

    val shown = if (gateFilter) {
        gateServers.filterNot(VpnGate::isRestricted)
    } else {
        gateServers
    }

    fun fetchGate() {
        if (gateBusy) return
        gateBusy = true
        gateStatus = Texts.fetchingServers
        scope.launch {
            val list = withContext(Dispatchers.IO) {
                VpnGate.fetch { Logs.add("vpngate", it) }
            }
            gateBusy = false
            if (list == null) {
                gateStatus = Texts.fetchFailed
            } else {
                gateServers = list
                health = emptyMap()
                latency = emptyMap()
                gateStatus = Texts.fetchedCount(list.size)
            }
        }
    }

    fun testGate() {
        if (gateBusy || shown.isEmpty()) return
        gateBusy = true
        gateStatus = Texts.testingHealth
        scope.launch {
            val candidates = withContext(Dispatchers.IO) {
                shown.map { it.endpoint }
            }
            val result = withContext(Dispatchers.IO) {
                SstpScanner.scan(candidates, { !gateBusy }) { Logs.add("vpngate", it) }
            }
            gateBusy = false
            val freshHealth = mutableMapOf<String, SstpHealth>()
            val freshLatency = mutableMapOf<String, Int>()
            result.forEach { hit ->
                freshHealth[hit.endpoint.toString()] = hit.health
                freshLatency[hit.endpoint.toString()] = hit.latencyMs
            }
            health = freshHealth
            latency = freshLatency
            val aliveCount = result.count { it.health == SstpHealth.Alive }
            gateStatus = Texts.aliveCount(aliveCount, result.size)
        }
    }

    fun applyGate(maxServers: Int) {
        val working = shown.filter { health[it.endpoint.toString()] == SstpHealth.Alive }
            .sortedBy { latency[it.endpoint.toString()] ?: 9999 }
            .take(maxServers)
        if (working.isEmpty()) return
        val endpoints = working.map { it.endpoint }
        SstpRelays.saveInstalled(context, endpoints, gateReplace)
        installedRelays = SstpRelays.installedCount(context)
        gateStatus = Texts.installedCount(working.size)
    }

    BackHandler(onBack = onBack)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF060916)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars),
        ) {
            TopAppBar(
                title = {
                    Text(
                        text = Texts.tools,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = ClxyColors.TextPrimary,
                    )
                },
                windowInsets = WindowInsets(0, 0, 0, 0),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = Texts.back,
                            tint = ClxyColors.TextPrimary,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                ),
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp)
                    .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = Texts.toolsBody,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = ClxyColors.TextSecondary,
                )

                // 1. IP / Edge Scanner Card (CLXY Prime)
                ToolsCard(
                    title = Texts.ipScanner,
                    subtitle = Texts.toolsHint,
                ) {
                    Text(
                        text = "CDN Presets",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = ClxyColors.TextPrimary,
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        presets.forEachIndexed { index, item ->
                            Chip(item.id, index == preset, scanning) { usePreset(index) }
                        }
                    }

                    Spacer(Modifier.height(4.dp))

                    OutlinedTextField(
                        value = ranges,
                        onValueChange = { ranges = it },
                        label = { Text(Texts.ipRanges) },
                        minLines = 2,
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = TextStyle(color = ClxyColors.TextPrimary, fontSize = 13.sp),
                        colors = toolTextFieldColors(),
                        shape = RoundedCornerShape(12.dp),
                    )

                    Text(
                        text = Texts.ipRangesHint,
                        fontSize = 11.sp,
                        color = ClxyColors.TextMuted,
                    )

                    Spacer(Modifier.height(4.dp))

                    OutlinedTextField(
                        value = snis,
                        onValueChange = { snis = it },
                        label = { Text(Texts.sniValues) },
                        minLines = 2,
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = TextStyle(color = ClxyColors.TextPrimary, fontSize = 13.sp),
                        colors = toolTextFieldColors(),
                        shape = RoundedCornerShape(12.dp),
                    )

                    Spacer(Modifier.height(4.dp))

                    OutlinedTextField(
                        value = hosts,
                        onValueChange = { hosts = it },
                        label = { Text(Texts.httpHosts) },
                        minLines = 2,
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = TextStyle(color = ClxyColors.TextPrimary, fontSize = 13.sp),
                        colors = toolTextFieldColors(),
                        shape = RoundedCornerShape(12.dp),
                    )

                    Text(
                        text = Texts.httpHostsHint,
                        fontSize = 11.sp,
                        color = ClxyColors.TextMuted,
                    )

                    Spacer(Modifier.height(6.dp))

                    Text(
                        text = "Scan Mode",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = ClxyColors.TextPrimary,
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        EdgeMode.values().forEach { item ->
                            Chip(
                                label = when (item) {
                                    EdgeMode.QUICK -> Texts.modeQuick
                                    EdgeMode.BALANCED -> Texts.modeBalanced
                                    EdgeMode.DEEP -> Texts.modeDeep
                                },
                                selected = item == mode,
                                enabled = !scanning,
                            ) { mode = item }
                        }
                    }

                    Text(
                        text = Texts.modeDescription(mode.samplesPerCidr, mode.maxIps),
                        fontSize = 11.sp,
                        color = ClxyColors.TextMuted,
                    )

                    Spacer(Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = Texts.concurrency,
                            fontSize = 13.sp,
                            color = ClxyColors.TextPrimary,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(6, 10, 15).forEach { value ->
                                Chip(value.toString(), concurrency == value, scanning) { concurrency = value }
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        OutlinedButton(
                            onClick = ::scout,
                            enabled = !scanning,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, ClxyColors.Cyan.copy(alpha = 0.6f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = ClxyColors.Cyan),
                        ) {
                            Text(Texts.scoutSnis, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }

                        Button(
                            onClick = ::scan,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (scanning) Color(0xFFFF5252) else ClxyColors.Cyan,
                                contentColor = Color.Black,
                            ),
                        ) {
                            if (scanning) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = Color.Black,
                                    strokeWidth = 2.dp,
                                )
                                Spacer(Modifier.size(6.dp))
                            }
                            Text(
                                if (scanning) Texts.stopScan else Texts.startScan,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }

                    if (progress.isNotBlank()) {
                        Text(
                            text = progress,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = ClxyColors.Cyan,
                        )
                    }

                    if (status.isNotBlank()) {
                        Text(
                            text = status,
                            fontSize = 12.sp,
                            color = ClxyColors.TextSecondary,
                        )
                    }

                    if (hits.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "Discovered Endpoints (${hits.size}):",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = ClxyColors.TextPrimary,
                        )
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF040612))
                                .border(1.dp, ClxyColors.GlassBorder, RoundedCornerShape(10.dp))
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            hits.take(12).forEach { hit ->
                                Text(
                                    text = Texts.edgeRow(
                                        hit.ip, hit.sni, hit.host, hit.latencyMs,
                                        hit.successes, hit.tries,
                                    ),
                                    fontSize = 11.sp,
                                    color = ClxyColors.Green,
                                )
                            }
                        }

                        Spacer(Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            OutlinedButton(
                                onClick = ::applyScan,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, ClxyColors.GlassBorder),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = ClxyColors.TextPrimary),
                            ) {
                                Text(Texts.useTop5, fontSize = 12.sp)
                            }
                            OutlinedButton(
                                onClick = ::copyHits,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, ClxyColors.GlassBorder),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = ClxyColors.TextPrimary),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.ContentCopy,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                    )
                                    Text(Texts.copyAll, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }

                // 2. Relay Collector Card (CLXY Ghost)
                ToolsCard(
                    title = Texts.vpngate,
                    subtitle = Texts.vpngateHint,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = Texts.replaceLists,
                            fontSize = 13.sp,
                            color = ClxyColors.TextPrimary,
                            modifier = Modifier.weight(1f),
                        )
                        Switch(
                            checked = gateReplace,
                            onCheckedChange = {
                                gateReplace = it
                                Prefs.replaceToolLists = it
                            },
                            colors = switchColors(),
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = Texts.vpngateFilter,
                            fontSize = 13.sp,
                            color = ClxyColors.TextPrimary,
                            modifier = Modifier.weight(1f),
                        )
                        Switch(
                            checked = gateFilter,
                            onCheckedChange = {
                                gateFilter = it
                                Prefs.vpngateFilterCountries = it
                            },
                            colors = switchColors(),
                        )
                    }

                    if (gateServers.isNotEmpty() && shown.size != gateServers.size) {
                        Text(
                            text = Texts.filterResult(gateServers.size - shown.size, shown.size),
                            fontSize = 11.sp,
                            color = ClxyColors.TextMuted,
                        )
                    }

                    Spacer(Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        OutlinedButton(
                            onClick = ::fetchGate,
                            enabled = !gateBusy,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, ClxyColors.Cyan.copy(alpha = 0.6f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = ClxyColors.Cyan),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                if (gateBusy) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(14.dp),
                                        color = ClxyColors.Cyan,
                                        strokeWidth = 2.dp,
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Rounded.Refresh,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                                Text(Texts.fetchServers, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }

                        Button(
                            onClick = ::testGate,
                            enabled = !gateBusy && shown.isNotEmpty(),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ClxyColors.Cyan,
                                contentColor = Color.Black,
                            ),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Speed,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                                Text(Texts.testHealth, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    if (gateStatus.isNotBlank()) {
                        Text(
                            text = gateStatus,
                            fontSize = 12.sp,
                            color = ClxyColors.Green,
                        )
                    }

                    if (shown.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF040612))
                                .border(1.dp, ClxyColors.GlassBorder, RoundedCornerShape(10.dp))
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            shown.take(15).forEach { server ->
                                val state = health[server.endpoint.toString()]
                                Text(
                                    text = buildString {
                                        append(server.ip).append(':').append(server.port)
                                        if (server.country.isNotBlank()) append(" · ").append(server.country)
                                        if (server.pingMs > 0) append(" · ").append(server.pingMs).append(" ms")
                                        if (state != null) append(" · ").append(state.name)
                                    },
                                    fontSize = 11.sp,
                                    color = when (state) {
                                        SstpHealth.Alive -> ClxyColors.Green
                                        SstpHealth.TcpOnly -> ClxyColors.AmberWarning
                                        SstpHealth.Dead -> Color(0xFFFF5252)
                                        null -> ClxyColors.TextSecondary
                                    },
                                )
                            }
                        }

                        Spacer(Modifier.height(6.dp))

                        Button(
                            onClick = { applyGate(20) },
                            enabled = shown.isNotEmpty(),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ClxyColors.Cyan.copy(alpha = 0.2f),
                                contentColor = ClxyColors.Cyan,
                            ),
                            border = BorderStroke(1.dp, ClxyColors.Cyan.copy(alpha = 0.5f)),
                        ) {
                            Text(
                                Texts.useTopWorking(installedRelays),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ToolsCard(
    title: String,
    subtitle: String?,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = ClxyColors.CardSurface,
        border = BorderStroke(1.dp, ClxyColors.GlassBorder),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = ClxyColors.TextPrimary,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    color = ClxyColors.TextSecondary,
                )
            }
            content()
        }
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (selected) ClxyColors.Cyan.copy(alpha = 0.18f) else ClxyColors.CardSurface,
        border = BorderStroke(
            1.dp,
            if (selected) ClxyColors.Cyan else ClxyColors.GlassBorder,
        ),
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick),
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) ClxyColors.Cyan else ClxyColors.TextSecondary,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun toolTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = ClxyColors.TextPrimary,
    unfocusedTextColor = ClxyColors.TextPrimary,
    focusedBorderColor = ClxyColors.Cyan,
    unfocusedBorderColor = ClxyColors.GlassBorder,
    focusedLabelColor = ClxyColors.Cyan,
    unfocusedLabelColor = ClxyColors.TextMuted,
    cursorColor = ClxyColors.Cyan,
    focusedContainerColor = ClxyColors.CardSurfaceSolid.copy(alpha = 0.6f),
    unfocusedContainerColor = ClxyColors.CardSurfaceSolid.copy(alpha = 0.4f),
)

@Composable
private fun switchColors() = SwitchDefaults.colors(
    checkedThumbColor = ClxyColors.Cyan,
    checkedTrackColor = ClxyColors.Cyan.copy(alpha = 0.35f),
    uncheckedThumbColor = ClxyColors.TextDim,
    uncheckedTrackColor = ClxyColors.CardSurfaceSolid,
    checkedBorderColor = ClxyColors.Cyan,
    uncheckedBorderColor = ClxyColors.GlassBorder,
)
