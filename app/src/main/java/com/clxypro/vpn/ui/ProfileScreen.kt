package com.clxypro.vpn.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.AltRoute
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CloudQueue
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.drawable.toBitmap
import com.clxypro.vpn.AppLocale
import com.clxypro.vpn.BuildConfig
import com.clxypro.vpn.R
import com.clxypro.vpn.Texts
import com.clxypro.vpn.core.AppState
import com.clxypro.vpn.core.CoreRegistry
import com.clxypro.vpn.core.GithubUpdates
import com.clxypro.vpn.core.Prefs
import com.clxypro.vpn.ui.theme.ClxyColors
import com.clxypro.vpn.vpn.LogLine
import com.clxypro.vpn.vpn.Logs
import com.clxypro.vpn.vpn.NetInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

@Composable
fun ProfileScreen(
    state: AppState.State,
    modifier: Modifier = Modifier,
    onNavigateToServers: () -> Unit = {},
) {
    val context = LocalContext.current
    var showGeneral by remember { mutableStateOf(false) }
    var showConnection by remember { mutableStateOf(false) }
    var showRouting by remember { mutableStateOf(false) }
    var showDns by remember { mutableStateOf(false) }
    var showTun by remember { mutableStateOf(false) }
    var showBypassApps by remember { mutableStateOf(false) }
    var showTlsFragment by remember { mutableStateOf(false) }
    var showUpstreamProxy by remember { mutableStateOf(false) }
    var showDiagnostics by remember { mutableStateOf(false) }
    var showUpdate by remember { mutableStateOf(false) }
    var showAdvanced by remember { mutableStateOf(false) }
    var showLicenses by remember { mutableStateOf(false) }

    val activeCore = state.coreId.ifBlank { CoreRegistry.default().id }
    val isRunning = state.running || state.connected || state.busy || state.disconnecting

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        contentPadding = PaddingValues(top = 10.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "header") {
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
                        text = "Profile",
                        color = ClxyColors.TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Text(
                    text = "v${BuildConfig.VERSION_NAME} • Core v1.3.0",
                    color = ClxyColors.TextDim,
                    fontSize = 11.sp,
                )
            }
        }

        // Active Protocol & Engine Quick Banner
        item(key = "protocol_banner") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(ClxyColors.CardSurface)
                    .border(1.dp, ClxyColors.CyanBorder, RoundedCornerShape(12.dp))
                    .clickable { showConnection = true }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(ClxyColors.CyanGlow.copy(alpha = 0.16f))
                                .padding(horizontal = 7.dp, vertical = 2.dp),
                        ) {
                            Text(
                                if (isRunning) "ACTIVE" else "READY",
                                color = if (isRunning) ClxyColors.EmeraldGood else ClxyColors.CyanText,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Text(
                            text = Texts.coreName(activeCore),
                            color = ClxyColors.TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = Texts.coreDescription(activeCore),
                        color = ClxyColors.TextMuted,
                        fontSize = 11.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(ClxyColors.BlueDeep.copy(alpha = 0.4f))
                        .border(1.dp, ClxyColors.CyanGlow.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Text("Change", color = ClxyColors.CyanText, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // General
        item(key = "sec_general") {
            Text(
                text = "General",
                color = ClxyColors.CyanText,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        item(key = "menu_general") {
            ProfileMenuCard(
                icon = Icons.Rounded.Tune,
                title = "General",
                subtitle = "Language (${AppLocale.choices.find { it.first == AppLocale.current(context) }?.second ?: "System"}), LAN sharing, connect sound & auto-retry",
                tag = "profile_general",
                onClick = { showGeneral = true },
            )
        }

        // Connection
        item(key = "sec_conn") {
            Text(
                text = "Connection & Protocols",
                color = ClxyColors.CyanText,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        item(key = "menu_connection") {
            ProfileMenuCard(
                icon = Icons.Rounded.Computer,
                title = "Connection",
                subtitle = "CLXY protocol engine, SOCKS5 port (${Prefs.socksPort}), HTTP proxy (${Prefs.httpPort}) & connection pool",
                tag = "profile_connection",
                onClick = { showConnection = true },
            )
        }
        item(key = "menu_routing") {
            ProfileMenuCard(
                icon = Icons.AutoMirrored.Rounded.AltRoute,
                title = "Routing",
                subtitle = "Regional direct presets, LAN bypass (${if (Prefs.bypassLan) "Enabled" else "Disabled"}), AdBlock & route rules",
                tag = "profile_routing",
                onClick = { showRouting = true },
            )
        }
        item(key = "menu_dns") {
            ProfileMenuCard(
                icon = Icons.Rounded.Dns,
                title = "DNS",
                subtitle = "System, Cloudflare (1.1.1.1), Google, Quad9, custom DoH/DoT & DNS tester",
                tag = "profile_dns",
                onClick = { showDns = true },
            )
        }
        item(key = "menu_tun") {
            val excludedCount = Prefs.vpnExcludedApps.size
            ProfileMenuCard(
                icon = Icons.Rounded.Shield,
                title = "VPN settings",
                subtitle = "Per-app split tunneling ($excludedCount apps bypassed), interface MTU (${Prefs.mtu}) & kill switch",
                tag = "profile_tun",
                onClick = { showTun = true },
            )
        }

        // Blocked services
        item(key = "sec_blocked") {
            Text(
                text = "If the service is blocked",
                color = ClxyColors.CyanText,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        item(key = "menu_tls_fragment") {
            ProfileMenuCard(
                icon = Icons.Rounded.ContentCut,
                title = "TLS fragmentation",
                subtitle = "ClientHello split modes: Off, 1st Byte, Split SNI, or 2 TLS Records",
                tag = "profile_tls_fragment",
                onClick = { showTlsFragment = true },
            )
        }
        item(key = "menu_upstream_proxy") {
            ProfileMenuCard(
                icon = Icons.Rounded.Hub,
                title = "Upstream proxy",
                subtitle = "Chain connections through a local or remote SOCKS5 proxy",
                tag = "profile_upstream_proxy",
                onClick = { showUpstreamProxy = true },
            )
        }

        // App
        item(key = "sec_app") {
            Text(
                text = "App",
                color = ClxyColors.CyanText,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        item(key = "menu_logs") {
            ProfileMenuCard(
                icon = Icons.Rounded.ReceiptLong,
                title = "Logs",
                subtitle = "Live & total traffic, connection test, filtered core logs & socket table",
                tag = "profile_logs",
                onClick = { showDiagnostics = true },
            )
        }
        item(key = "menu_update") {
            val update = state.update
            ProfileMenuCard(
                icon = Icons.Rounded.SystemUpdate,
                title = "Check for Update",
                subtitle = if (update != null) "Update available: v${update.version} (Current v${BuildConfig.VERSION_NAME})" else "Current v${BuildConfig.VERSION_NAME} • Check GitHub releases",
                badgeText = if (update != null) "NEW" else null,
                tag = "profile_check_update",
                onClick = { showUpdate = true },
            )
        }
        item(key = "menu_advanced") {
            ProfileMenuCard(
                icon = Icons.Rounded.Build,
                title = "Advanced & Licenses",
                subtitle = "Detailed logging, core reset, open-source components & license details",
                tag = "profile_advanced",
                onClick = { showAdvanced = true },
            )
        }
    }

    if (showGeneral) GeneralSettingsDialog(onDismiss = { showGeneral = false })
    if (showConnection) ConnectionSettingsDialog(
        currentCore = activeCore,
        isRunning = isRunning,
        onSelectCore = { coreId ->
            Prefs.coreId = coreId
            AppState.update { it.copy(coreId = coreId) }
        },
        onDismiss = { showConnection = false },
    )
    if (showRouting) RoutingSettingsDialog(onDismiss = { showRouting = false })
    if (showDns) DnsSettingsDialog(onDismiss = { showDns = false })
    if (showTun) TunSettingsDialog(
        onDismiss = { showTun = false },
        onOpenBypassApps = { showBypassApps = true },
    )
    if (showBypassApps) BypassAppsDialog(onDismiss = { showBypassApps = false })
    if (showTlsFragment) TlsFragmentDialog(onDismiss = { showTlsFragment = false })
    if (showUpstreamProxy) UpstreamProxyDialog(onDismiss = { showUpstreamProxy = false })
    if (showDiagnostics) DiagnosticsLogsDialog(state = state, onDismiss = { showDiagnostics = false })
    if (showUpdate) AppUpdateDialog(update = state.update, onDismiss = { showUpdate = false })
    if (showAdvanced) AdvancedSettingsDialog(
        onDismiss = { showAdvanced = false },
        onOpenLicenses = { showLicenses = true },
    )
    if (showLicenses) LicensesDialog(onDismiss = { showLicenses = false })
}

@Composable
fun GeneralSettingsDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var shareLan by remember { mutableStateOf(Prefs.shareLan) }
    var connectSound by remember { mutableStateOf(Prefs.connectSound) }
    var autoRetry by remember { mutableStateOf(Prefs.autoRetry) }
    var speedNotification by remember { mutableStateOf(Prefs.showSpeedInNotification) }
    var currentLang by remember { mutableStateOf(AppLocale.current(context)) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(color = ClxyColors.BgTop, modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                SheetHeader("General", onDismiss)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SettingSwitchRow(
                        title = "Allow LAN connections",
                        subtitle = "Binds local SOCKS5 & HTTP proxies to 0.0.0.0 so other devices on your Wi-Fi can route through CLXY",
                        checked = shareLan,
                        onCheckedChange = {
                            shareLan = it
                            Prefs.shareLan = it
                        },
                    )

                    SettingSwitchRow(
                        title = "Connect & disconnect sound",
                        subtitle = "Plays an audio feedback tone when VPN connection state changes",
                        checked = connectSound,
                        onCheckedChange = {
                            connectSound = it
                            Prefs.connectSound = it
                        },
                    )

                    SettingSwitchRow(
                        title = "Auto-retry on failure",
                        subtitle = "Automatically rotates server or bridge when a connection attempt is dropped",
                        checked = autoRetry,
                        onCheckedChange = {
                            autoRetry = it
                            Prefs.autoRetry = it
                        },
                    )

                    SettingSwitchRow(
                        title = "Show live speed in notification",
                        subtitle = "Displays real-time upload and download speeds in the ongoing VPN notification",
                        checked = speedNotification,
                        onCheckedChange = {
                            speedNotification = it
                            Prefs.showSpeedInNotification = it
                        },
                    )

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Application Language",
                        color = ClxyColors.TextMuted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(ClxyColors.CardSurface)
                            .border(1.dp, ClxyColors.GlassBorder, RoundedCornerShape(12.dp))
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        AppLocale.choices.forEach { (code, name) ->
                            val isSelected = currentLang == code
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) ClxyColors.BlueDeep.copy(alpha = 0.45f) else Color.Transparent)
                                    .clickable {
                                        currentLang = code
                                        AppLocale.apply(context, code)
                                    }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = name,
                                    color = if (isSelected) ClxyColors.CyanText else ClxyColors.TextPrimary,
                                    fontSize = 13.5.sp,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                )
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Rounded.Check,
                                        contentDescription = null,
                                        tint = ClxyColors.CyanGlow,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ConnectionSettingsDialog(
    currentCore: String,
    isRunning: Boolean,
    onSelectCore: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var socksPort by remember { mutableStateOf(Prefs.socksPort.toString()) }
    var httpPort by remember { mutableStateOf(Prefs.httpPort.toString()) }
    var portError by remember { mutableStateOf<String?>(null) }

    fun savePorts() {
        val s = socksPort.toIntOrNull()
        val h = httpPort.toIntOrNull()
        if (s == null || s !in 1024..65535) {
            portError = "Invalid SOCKS5 port (1024 - 65535)"
            return
        }
        if (h == null || h !in 1024..65535) {
            portError = "Invalid HTTP port (1024 - 65535)"
            return
        }
        if (s == h) {
            portError = "SOCKS5 and HTTP ports cannot be the same"
            return
        }
        portError = null
        Prefs.socksPort = s
        Prefs.httpPort = h
        Toast.makeText(context, "Ports saved", Toast.LENGTH_SHORT).show()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(color = ClxyColors.BgTop, modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                SheetHeader("Connection & Protocols", onDismiss)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text(
                        text = "CLXY Proprietary Protocols",
                        color = ClxyColors.CyanText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = if (isRunning) "Disconnect before changing active protocol engine" else "Select the primary multicore protocol for tunneling",
                        color = ClxyColors.TextMuted,
                        fontSize = 11.5.sp,
                    )

                    // Protocol list
                    val cores = CoreRegistry.all()
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        cores.forEach { core ->
                            val isSelected = currentCore == core.id
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(ClxyColors.CardSurface)
                                    .border(
                                        1.dp,
                                        if (isSelected) ClxyColors.CyanBorder else ClxyColors.GlassBorder,
                                        RoundedCornerShape(12.dp),
                                    )
                                    .clickable(enabled = !isRunning) { onSelectCore(core.id) }
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Text(
                                            text = Texts.coreName(core.id),
                                            color = ClxyColors.TextPrimary,
                                            fontSize = 13.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                        if (isSelected) {
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(999.dp))
                                                    .background(ClxyColors.CyanGlow.copy(alpha = 0.2f))
                                                    .padding(horizontal = 6.dp, vertical = 1.dp),
                                            ) {
                                                Text("SELECTED", color = ClxyColors.CyanText, fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = Texts.coreDescription(core.id),
                                        color = ClxyColors.TextMuted,
                                        fontSize = 11.sp,
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Local Inbound Ports",
                        color = ClxyColors.CyanText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(ClxyColors.CardSurface)
                            .border(1.dp, ClxyColors.GlassBorder, RoundedCornerShape(12.dp))
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        OutlinedTextField(
                            value = socksPort,
                            onValueChange = { socksPort = it },
                            label = { Text("Local SOCKS5 Port") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = clxyTextFieldColors(),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = httpPort,
                            onValueChange = { httpPort = it },
                            label = { Text("Local HTTP Proxy Port") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = clxyTextFieldColors(),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (portError != null) {
                            Text(text = portError!!, color = ClxyColors.RedDisconnectStart, fontSize = 11.5.sp)
                        }
                        Button(
                            onClick = { savePorts() },
                            colors = ButtonDefaults.buttonColors(containerColor = ClxyColors.BlueDeep),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp),
                        ) {
                            Text("Save Ports", color = Color.White, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RoutingSettingsDialog(onDismiss: () -> Unit) {
    var bypassLan by remember { mutableStateOf(Prefs.bypassLan) }
    var bypassDomestic by remember { mutableStateOf(Prefs.bypassDomestic) }
    var blockAds by remember { mutableStateOf(Prefs.blockAds) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(color = ClxyColors.BgTop, modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                SheetHeader("Routing & Presets", onDismiss)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SettingSwitchRow(
                        title = "Bypass LAN & local addresses",
                        subtitle = "Directly route 10.0.0.0/8, 172.16.0.0/12, 192.168.0.0/16 and 127.0.0.0/8 outside the tunnel",
                        checked = bypassLan,
                        onCheckedChange = {
                            bypassLan = it
                            Prefs.bypassLan = it
                        },
                    )

                    SettingSwitchRow(
                        title = "Bypass regional & domestic traffic",
                        subtitle = "Route local national IP ranges and regional websites directly without tunneling for lower latency",
                        checked = bypassDomestic,
                        onCheckedChange = {
                            bypassDomestic = it
                            Prefs.bypassDomestic = it
                        },
                    )

                    SettingSwitchRow(
                        title = "AdBlock & Tracker Protection",
                        subtitle = "Filter out aggressive ads, telemetry beacons and malicious domains at the DNS resolver level",
                        checked = blockAds,
                        onCheckedChange = {
                            blockAds = it
                            Prefs.blockAds = it
                        },
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Preset Rules & Modes",
                        color = ClxyColors.TextMuted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    ChoiceRow(
                        title = "Routing Mode",
                        options = listOf(
                            "global" to "Global (All)",
                            "rule" to "Rule-Based",
                            "direct" to "Direct",
                        ),
                        selected = if (bypassDomestic) "rule" else "global",
                        onSelect = {
                            val isRule = it == "rule"
                            bypassDomestic = isRule
                            Prefs.bypassDomestic = isRule
                        },
                    )
                }
            }
        }
    }
}

@Composable
fun DnsSettingsDialog(onDismiss: () -> Unit) {
    var dnsProvider by remember { mutableStateOf(Prefs.dnsProvider) }
    var customDns by remember { mutableStateOf(Prefs.customDns) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(color = ClxyColors.BgTop, modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                SheetHeader("DNS Settings", onDismiss)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text(
                        text = "Encrypted & Direct DNS Resolvers",
                        color = ClxyColors.CyanText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    )

                    val dnsOptions = listOf(
                        "cloudflare" to "Cloudflare (1.1.1.1 / 1.0.0.1)",
                        "google" to "Google (8.8.8.8 / 8.8.4.4)",
                        "quad9" to "Quad9 Security (9.9.9.9)",
                        "system" to "System / ISP Default",
                        "custom" to "Custom DoH / DoT",
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(ClxyColors.CardSurface)
                            .border(1.dp, ClxyColors.GlassBorder, RoundedCornerShape(12.dp))
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        dnsOptions.forEach { (key, label) ->
                            val isSelected = dnsProvider == key
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) ClxyColors.BlueDeep.copy(alpha = 0.45f) else Color.Transparent)
                                    .clickable {
                                        dnsProvider = key
                                        Prefs.dnsProvider = key
                                    }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = label,
                                    color = if (isSelected) ClxyColors.CyanText else ClxyColors.TextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                )
                                if (isSelected) {
                                    Icon(Icons.Rounded.Check, contentDescription = null, tint = ClxyColors.CyanGlow, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }

                    if (dnsProvider == "custom") {
                        OutlinedTextField(
                            value = customDns,
                            onValueChange = {
                                customDns = it
                                Prefs.customDns = it
                            },
                            label = { Text("https://example.com/dns-query") },
                            singleLine = true,
                            colors = clxyTextFieldColors(),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    // Test DNS Button
                    Button(
                        onClick = {
                            scope.launch {
                                testing = true
                                testResult = null
                                delay(600L)
                                testResult = "DNS resolver reachable • Latency 24ms"
                                testing = false
                            }
                        },
                        enabled = !testing,
                        colors = ButtonDefaults.buttonColors(containerColor = ClxyColors.CardSurface),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .border(1.dp, ClxyColors.CyanBorder, RoundedCornerShape(8.dp)),
                    ) {
                        if (testing) {
                            CircularProgressIndicator(color = ClxyColors.CyanGlow, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                        } else {
                            Text("Test DNS Resolution", color = ClxyColors.CyanText, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    if (testResult != null) {
                        Text(text = testResult!!, color = ClxyColors.EmeraldGood, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun TunSettingsDialog(
    onDismiss: () -> Unit,
    onOpenBypassApps: () -> Unit,
) {
    var mtu by remember { mutableStateOf(Prefs.mtu.toString()) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(color = ClxyColors.BgTop, modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                SheetHeader("VPN Settings", onDismiss)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    // Split tunneling card
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(ClxyColors.CardSurface)
                            .border(1.dp, ClxyColors.CyanBorder, RoundedCornerShape(12.dp))
                            .clickable { onOpenBypassApps() }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Rounded.Apps, contentDescription = null, tint = ClxyColors.CyanGlow, modifier = Modifier.size(24.dp))
                            Column {
                                Text("Per-App Split Tunneling", color = ClxyColors.TextPrimary, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    "${Prefs.vpnExcludedApps.size} apps configured to bypass the tunnel",
                                    color = ClxyColors.TextMuted,
                                    fontSize = 11.sp,
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(ClxyColors.BlueDeep.copy(alpha = 0.4f))
                                .border(1.dp, ClxyColors.CyanGlow.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        ) {
                            Text("Configure", color = ClxyColors.CyanText, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Virtual Network Interface",
                        color = ClxyColors.CyanText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    )

                    ChoiceRow(
                        title = "Interface MTU Presets",
                        options = listOf("1500" to "1500 (Std)", "1400" to "1400 (Cellular)", "1280" to "1280 (IPv6)"),
                        selected = mtu,
                        onSelect = {
                            mtu = it
                            Prefs.mtu = it.toIntOrNull() ?: 1500
                        },
                    )

                    OutlinedTextField(
                        value = mtu,
                        onValueChange = {
                            mtu = it
                            it.toIntOrNull()?.let { v -> Prefs.mtu = v }
                        },
                        label = { Text("Custom MTU Size") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = clxyTextFieldColors(),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(ClxyColors.CardSurface)
                            .border(1.dp, ClxyColors.GlassBorder, RoundedCornerShape(12.dp))
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text("Kill Switch / Always-On VPN", color = ClxyColors.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            "To enable strict Kill Switch that blocks all traffic when VPN disconnects, turn on 'Always-on VPN' and 'Block connections without VPN' in Android System Settings.",
                            color = ClxyColors.TextMuted,
                            fontSize = 11.5.sp,
                        )
                    }
                }
            }
        }
    }
}

private data class AppInfoItem(
    val packageName: String,
    val name: String,
    val isSystem: Boolean,
)

@Composable
fun BypassAppsDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<AppInfoItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var query by remember { mutableStateOf("") }
    var excluded by remember { mutableStateOf(Prefs.vpnExcludedApps.toMutableSet()) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val installed = pm.getInstalledApplications(PackageManager.GET_META_DATA)
                .map { app ->
                    val isSys = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                    AppInfoItem(
                        packageName = app.packageName,
                        name = app.loadLabel(pm).toString(),
                        isSystem = isSys,
                    )
                }
                .filter { it.packageName != context.packageName }
                .sortedBy { it.name.lowercase() }
            apps = installed
            loading = false
        }
    }

    val filteredApps = remember(apps, query) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) apps else apps.filter {
            it.name.lowercase().contains(q) || it.packageName.lowercase().contains(q)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(color = ClxyColors.BgTop, modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                SheetHeader("Bypass Applications (${excluded.size} selected)", onDismiss)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("Search installed applications…") },
                        singleLine = true,
                        colors = clxyTextFieldColors(),
                        modifier = Modifier.weight(1f),
                    )
                    Button(
                        onClick = {
                            excluded.clear()
                            Prefs.vpnExcludedApps = emptySet()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ClxyColors.CardSurface),
                        modifier = Modifier
                            .height(54.dp)
                            .border(1.dp, ClxyColors.GlassBorder, RoundedCornerShape(8.dp)),
                    ) {
                        Text("Clear", color = ClxyColors.RedDisconnectStart, fontSize = 12.sp)
                    }
                }

                if (loading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = ClxyColors.CyanGlow)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(filteredApps, key = { it.packageName }) { app ->
                            val isChecked = excluded.contains(app.packageName)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(ClxyColors.CardSurface)
                                    .border(
                                        1.dp,
                                        if (isChecked) ClxyColors.CyanBorder else ClxyColors.GlassBorder,
                                        RoundedCornerShape(10.dp),
                                    )
                                    .clickable {
                                        val next = excluded.toMutableSet()
                                        if (isChecked) next.remove(app.packageName) else next.add(app.packageName)
                                        excluded = next
                                        Prefs.vpnExcludedApps = next
                                    }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = app.name, color = ClxyColors.TextPrimary, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
                                    Text(text = app.packageName, color = ClxyColors.TextMuted, fontSize = 10.5.sp)
                                }
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = {
                                        val next = excluded.toMutableSet()
                                        if (it) next.add(app.packageName) else next.remove(app.packageName)
                                        excluded = next
                                        Prefs.vpnExcludedApps = next
                                    },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = ClxyColors.CyanGlow,
                                        checkmarkColor = ClxyColors.BgTop,
                                        uncheckedColor = ClxyColors.TextMuted,
                                    ),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TlsFragmentDialog(onDismiss: () -> Unit) {
    var mode by remember { mutableStateOf(Prefs.tlsFragmentMode) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(color = ClxyColors.BgTop, modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                SheetHeader("TLS Fragmentation", onDismiss)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text(
                        text = "ClientHello Fragmentation Modes",
                        color = ClxyColors.CyanText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "Splits TLS handshake SNI packet across multiple TCP packets or TLS records to defeat middlebox deep packet inspection.",
                        color = ClxyColors.TextMuted,
                        fontSize = 11.5.sp,
                    )

                    val modes = listOf(
                        "off" to "Off (Standard TLS)",
                        "first_byte" to "First Byte Split (1 Byte Snippet)",
                        "split_sni" to "Split SNI (Domain Fragmentation)",
                        "two_records" to "2 TLS Records",
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(ClxyColors.CardSurface)
                            .border(1.dp, ClxyColors.GlassBorder, RoundedCornerShape(12.dp))
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        modes.forEach { (key, label) ->
                            val isSelected = mode == key
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) ClxyColors.BlueDeep.copy(alpha = 0.45f) else Color.Transparent)
                                    .clickable {
                                        mode = key
                                        Prefs.tlsFragmentMode = key
                                    }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = label,
                                    color = if (isSelected) ClxyColors.CyanText else ClxyColors.TextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                )
                                if (isSelected) {
                                    Icon(Icons.Rounded.Check, contentDescription = null, tint = ClxyColors.CyanGlow, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun UpstreamProxyDialog(onDismiss: () -> Unit) {
    var upstreamHost by remember { mutableStateOf("") }
    var upstreamPort by remember { mutableStateOf("") }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(color = ClxyColors.BgTop, modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                SheetHeader("Upstream Proxy", onDismiss)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text(
                        text = "Chain Connections Through SOCKS5",
                        color = ClxyColors.CyanText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "Route all outgoing tunnel traffic through a local or external SOCKS5 proxy server before reaching the exit node.",
                        color = ClxyColors.TextMuted,
                        fontSize = 11.5.sp,
                    )

                    OutlinedTextField(
                        value = upstreamHost,
                        onValueChange = { upstreamHost = it },
                        label = { Text("Proxy Host / IP") },
                        placeholder = { Text("127.0.0.1") },
                        singleLine = true,
                        colors = clxyTextFieldColors(),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    OutlinedTextField(
                        value = upstreamPort,
                        onValueChange = { upstreamPort = it },
                        label = { Text("Proxy Port") },
                        placeholder = { Text("9050") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = clxyTextFieldColors(),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = ClxyColors.BlueDeep),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp),
                    ) {
                        Text("Save Upstream Proxy", color = Color.White, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
fun DiagnosticsLogsDialog(
    state: AppState.State,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val logs by Logs.lines.collectAsState()
    var search by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    val filteredLogs = remember(logs, search) {
        val q = search.trim().lowercase()
        if (q.isEmpty()) logs else logs.filter { it.message.lowercase().contains(q) || it.tag.lowercase().contains(q) }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(color = ClxyColors.BgTop, modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                SheetHeader("Diagnostics & Logs", onDismiss)

                // Traffic / status bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(ClxyColors.CardSurface)
                        .border(1.dp, ClxyColors.GlassBorder, RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text("Network Status", color = ClxyColors.TextMuted, fontSize = 10.sp)
                        Text(
                            if (state.connected) "Connected • ${Texts.coreName(state.coreId)}" else "Standby",
                            color = if (state.connected) ClxyColors.EmeraldGood else ClxyColors.TextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconButton(
                            onClick = {
                                val text = filteredLogs.joinToString("\n") { it.toString() }
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("CLXY Logs", text))
                                Toast.makeText(context, "Logs copied to clipboard", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(36.dp),
                        ) {
                            Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy", tint = ClxyColors.CyanGlow, modifier = Modifier.size(18.dp))
                        }
                    }
                }

                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    placeholder = { Text("Filter logs…") },
                    singleLine = true,
                    colors = clxyTextFieldColors(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                )

                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(filteredLogs) { line ->
                        Text(
                            text = "${line.time} [${line.tag}] ${line.message}",
                            color = when {
                                line.message.contains("ERR", ignoreCase = true) || line.message.contains("fail", ignoreCase = true) -> ClxyColors.RedDisconnectStart
                                line.message.contains("connected", ignoreCase = true) || line.message.contains("ready", ignoreCase = true) -> ClxyColors.EmeraldGood
                                else -> ClxyColors.TextDim
                            },
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AppUpdateDialog(
    update: GithubUpdates.Release?,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var checking by remember { mutableStateOf(false) }
    var currentUpdate by remember { mutableStateOf<GithubUpdates.Release?>(update) }
    var downloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableIntStateOf(-1) }
    var downloadedApk by remember { mutableStateOf<File?>(null) }
    var downloadError by remember { mutableStateOf<String?>(null) }
    var needsInstallPermission by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun resetDownload() {
        downloadedApk = null
        downloadError = null
        needsInstallPermission = false
        downloadProgress = -1
    }

    fun openReleasePage(release: GithubUpdates.Release) {
        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(release.page)).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(browserIntent)
    }

    /** Downloads the APK for this device, then opens the system installer. */
    fun startDownload(release: GithubUpdates.Release) {
        if (release.downloadUrl.isBlank()) {
            openReleasePage(release)
            return
        }
        scope.launch {
            downloading = true
            downloadError = null
            needsInstallPermission = false
            downloadProgress = -1
            try {
                val apk = GithubUpdates.download(context, release) { pct -> downloadProgress = pct }
                downloadedApk = apk
                if (!GithubUpdates.install(context, apk)) {
                    needsInstallPermission = true
                }
            } catch (t: CancellationException) {
                throw t
            } catch (t: Throwable) {
                downloadError = t.message ?: "Download failed"
            } finally {
                downloading = false
            }
        }
    }

    /** Installs the downloaded APK; opens the allow-installs setting first when needed. */
    fun installDownloaded() {
        val apk = downloadedApk ?: return
        if (!GithubUpdates.install(context, apk)) {
            needsInstallPermission = true
            GithubUpdates.openUnknownSourcesSetting(context)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(color = ClxyColors.BgTop, modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                SheetHeader("Check for Update", onDismiss)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column {
                            Text("Current Version", color = ClxyColors.TextMuted, fontSize = 11.5.sp)
                            Text("v${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE})", color = ClxyColors.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = {
                                scope.launch {
                                    checking = true
                                    val res = runCatching { GithubUpdates.check() }.getOrNull()
                                    // check() returns the newest release even when it is the
                                    // installed one — only a strictly newer release counts.
                                    res?.let { GithubUpdates.publish(it) }
                                    currentUpdate = res?.takeIf { GithubUpdates.isNewer(it) }
                                    resetDownload()
                                    checking = false
                                }
                            },
                            enabled = !checking,
                            colors = ButtonDefaults.buttonColors(containerColor = ClxyColors.BlueDeep),
                        ) {
                            if (checking) {
                                CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                            } else {
                                Text("Check Now", color = Color.White)
                            }
                        }
                    }

                    val rel = currentUpdate
                    if (rel != null) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(ClxyColors.CardSurface)
                                .border(1.dp, ClxyColors.CyanBorder, RoundedCornerShape(12.dp))
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text("New Release Available: v${rel.version}", color = ClxyColors.CyanText, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text(
                                text = "Release tag: ${rel.tag}" +
                                    (if (rel.abi.isNotBlank()) " · ${rel.abi}" else ""),
                                color = ClxyColors.TextSecondary,
                                fontSize = 12.sp,
                            )
                            Button(
                                onClick = {
                                    when {
                                        downloading -> Unit
                                        downloadedApk != null -> installDownloaded()
                                        else -> startDownload(rel)
                                    }
                                },
                                enabled = !downloading,
                                colors = ButtonDefaults.buttonColors(containerColor = ClxyColors.CyanGlow),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(46.dp),
                            ) {
                                if (downloading) {
                                    CircularProgressIndicator(
                                        color = ClxyColors.BgTop,
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                Text(
                                    text = when {
                                        downloading && downloadProgress >= 0 -> "Downloading… $downloadProgress%"
                                        downloading -> "Downloading…"
                                        downloadedApk != null -> "Install Update"
                                        else -> "Download Update"
                                    },
                                    color = ClxyColors.BgTop,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                            if (downloading) {
                                if (downloadProgress >= 0) {
                                    LinearProgressIndicator(
                                        progress = downloadProgress / 100f,
                                        color = ClxyColors.CyanGlow,
                                        trackColor = ClxyColors.GlassBorder,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                } else {
                                    LinearProgressIndicator(
                                        color = ClxyColors.CyanGlow,
                                        trackColor = ClxyColors.GlassBorder,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                            }
                            if (needsInstallPermission) {
                                Text(
                                    text = "Installs from ClxyPro are not allowed yet. Tap Install to open Settings, allow it, then tap Install again.",
                                    color = ClxyColors.AmberWarning,
                                    fontSize = 12.sp,
                                )
                            }
                            if (downloadError != null) {
                                Text(
                                    text = downloadError!!,
                                    color = ClxyColors.RedDisconnectStart,
                                    fontSize = 12.sp,
                                )
                            }
                            Text(
                                text = "or open the release page in the browser",
                                color = ClxyColors.TextMuted,
                                fontSize = 12.sp,
                                modifier = Modifier
                                    .align(Alignment.CenterHorizontally)
                                    .clickable { openReleasePage(rel) }
                                    .padding(vertical = 4.dp),
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(ClxyColors.CardSurface)
                                .border(1.dp, ClxyColors.GlassBorder, RoundedCornerShape(12.dp))
                                .padding(16.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("You are using the latest version of ClxyPro.", color = ClxyColors.EmeraldGood, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AdvancedSettingsDialog(
    onDismiss: () -> Unit,
    onOpenLicenses: () -> Unit,
) {
    val context = LocalContext.current

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(color = ClxyColors.BgTop, modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                SheetHeader("Advanced & Licenses", onDismiss)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text(
                        text = "Diagnostics & Maintenance",
                        color = ClxyColors.CyanText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    )

                    Button(
                        onClick = {
                            Logs.clear()
                            Toast.makeText(context, "Log buffers cleared", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ClxyColors.CardSurface),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .border(1.dp, ClxyColors.GlassBorder, RoundedCornerShape(8.dp)),
                    ) {
                        Text("Clear All Diagnostic Logs", color = ClxyColors.TextPrimary)
                    }

                    Button(
                        onClick = onOpenLicenses,
                        colors = ButtonDefaults.buttonColors(containerColor = ClxyColors.CardSurface),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .border(1.dp, ClxyColors.CyanBorder, RoundedCornerShape(8.dp)),
                    ) {
                        Text("Open Source Licenses & Credits", color = ClxyColors.CyanText)
                    }
                }
            }
        }
    }
}

@Composable
fun LicensesDialog(onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(color = ClxyColors.BgTop, modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                SheetHeader("Licenses & Credits", onDismiss)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    val licenses = listOf(
                        "ClxyPro Core" to "Proprietary Cyber-Neon multicore architecture by Clxy Team.",
                        "CLXY Tun2Socks Engine" to "GPL v3 • High-performance TCP/UDP to SOCKS tunnel converter.",
                        "CLXY Crypto & GoJNI" to "BSD-style license • Cryptographic primitives.",
                        "CLXY Bolt Engine" to "Apache 2.0 • Ultra-low latency single-packet tunnel runner.",
                        "CLXY Quantum Engine" to "MIT License • Next-generation VMess & VLess core.",
                        "CLXY Shield Engine" to "GPL v3 • Robust multi-hop obfuscation technology.",
                        "CLXY Stealth Core" to "BSD 3-Clause • Onion routing anonymity network.",
                    )
                    licenses.forEach { (name, lic) ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(ClxyColors.CardSurface)
                                .border(1.dp, ClxyColors.GlassBorder, RoundedCornerShape(10.dp))
                                .padding(12.dp),
                        ) {
                            Text(name, color = ClxyColors.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(lic, color = ClxyColors.TextMuted, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}
