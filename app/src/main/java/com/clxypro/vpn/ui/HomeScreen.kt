package com.clxypro.vpn.ui

import android.content.Context
import android.net.TrafficStats
import android.os.SystemClock
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.ui.window.Dialog
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clxypro.vpn.Texts
import com.clxypro.vpn.core.AppState
import com.clxypro.vpn.core.Prefs
import com.clxypro.vpn.core.PsiphonExit
import com.clxypro.vpn.core.QuantumSub
import com.clxypro.vpn.ui.theme.ClxyColors
import com.clxypro.vpn.vpn.Store
import com.clxypro.vpn.vpn.cleanDisplayLabel
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
fun HomeScreen(
    state: AppState.State,
    onToggle: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenServers: () -> Unit,
    onOpenSignIn: () -> Unit,
    onOpenLogs: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val isQuantum = state.coreId == "aether"
    val isHyper = !isQuantum

    val isConnected = state.connected
    val isConnecting = state.busy || (state.running && !state.connected && !state.disconnecting)
    val hasFailure = !state.running && (state.diagnosis.isNotEmpty() || state.statusCode < 0)
    var showUpdate by remember { mutableStateOf(false) }
    var psiphonExit by remember { mutableStateOf(Prefs.psiphonExit) }
    var psiphonRegion by remember { mutableStateOf(Prefs.psiphonRegion) }
    var showExitGuide by remember { mutableStateOf(false) }

    val statusColor by animateColorAsState(
        targetValue = when {
            isConnected -> if (isQuantum) ClxyColors.QuantumPurple else ClxyColors.CyanText
            isConnecting -> ClxyColors.AmberWarning
            hasFailure -> ClxyColors.RedDisconnectStart
            else -> ClxyColors.TextDim
        },
        animationSpec = tween(350),
        label = "homeStatusColor",
    )
    val shieldColor by animateColorAsState(
        targetValue = when {
            isConnected -> if (isQuantum) ClxyColors.QuantumViolet else ClxyColors.CyanGlow
            isConnecting -> ClxyColors.AmberWarning
            hasFailure -> ClxyColors.RedDisconnectStart
            else -> ClxyColors.TimerDim
        },
        animationSpec = tween(350),
        label = "homeShieldColor",
    )

    val coreDisplayName = if (isQuantum) "CLXY Quantum (Aether)" else "CLXY Hyper"
    val statusLabel = when {
        isConnected -> "Connection Secure • $coreDisplayName"
        isConnecting -> "Connecting… • $coreDisplayName"
        hasFailure -> "Connection Failed • $coreDisplayName"
        else -> "Disconnected • $coreDisplayName"
    }

    // Live session timer calculation
    var liveElapsed by remember { mutableLongStateOf(0L) }
    LaunchedEffect(isConnected, state.connectedAt) {
        if (isConnected && state.connectedAt > 0L) {
            while (true) {
                liveElapsed = ((SystemClock.elapsedRealtime() - state.connectedAt) / 1000L).coerceAtLeast(0L)
                delay(1000L)
            }
        } else {
            liveElapsed = 0L
        }
    }

    val hrs = liveElapsed / 3600
    val mins = (liveElapsed % 3600) / 60
    val secs = liveElapsed % 60
    val hrsStr = if (hrs > 0) String.format(Locale.US, "%02d:", hrs) else ""
    val minSecStr = String.format(Locale.US, "%02d:%02d", mins, secs)

    // Real-time Speed telemetry
    var downRate by remember { mutableStateOf("0") }
    var upRate by remember { mutableStateOf("0") }

    LaunchedEffect(isConnected) {
        if (!isConnected) {
            downRate = "0"
            upRate = "0"
            return@LaunchedEffect
        }
        fun sampleRx(): Long {
            val total = TrafficStats.getTotalRxBytes()
            if (total > 0L) return total
            val uid = TrafficStats.getUidRxBytes(android.os.Process.myUid())
            return if (uid > 0L) uid else 0L
        }
        fun sampleTx(): Long {
            val total = TrafficStats.getTotalTxBytes()
            if (total > 0L) return total
            val uid = TrafficStats.getUidTxBytes(android.os.Process.myUid())
            return if (uid > 0L) uid else 0L
        }

        var lastRx = sampleRx()
        var lastTx = sampleTx()
        var lastTime = SystemClock.elapsedRealtime()

        while (true) {
            delay(1000L)
            val now = SystemClock.elapsedRealtime()
            val curRx = sampleRx()
            val curTx = sampleTx()

            val elapsedSec = ((now - lastTime) / 1000f).coerceAtLeast(0.1f)
            val rxBytes = (curRx - lastRx).coerceAtLeast(0L)
            val txBytes = (curTx - lastTx).coerceAtLeast(0L)

            // Convert bytes/sec to kilobytes/sec (KB/s): bytes / 1_000.
            // Whole KB/s — at this scale a decimal is sampling noise.
            val downKBpsVal = (rxBytes / 1000f) / elapsedSec
            val upKBpsVal = (txBytes / 1000f) / elapsedSec

            downRate = String.format(Locale.US, "%.0f", downKBpsVal)
            upRate = String.format(Locale.US, "%.0f", upKBpsVal)

            lastRx = curRx
            lastTx = curTx
            lastTime = now
        }
    }

    // Origin flag: the device's real country from its public IP, looked up
    // once at startup (OriginCountry) — never the system locale, which only
    // says what language the phone speaks. "AUTO" draws the generic globe.
    val originCode = state.originCode

    // Destination routing
    val pinned = Prefs.server("firefox")
    // The saved label may predate label cleaning ("CatchAll Anycast / USA");
    // clean it here so the disconnected home never shows the raw name.
    val pinnedLabel = cleanDisplayLabel(Prefs.serverLabel("firefox"))
    // Quantum's pinned relay (Servers screen). The stored label is renamed
    // here too, so a relay pinned by an older version shows its current name.
    val quantumPinned = Prefs.server("aether")
    val quantumPinnedLabel = QuantumSub.remarkRelayName(cleanDisplayLabel(Prefs.serverLabel("aether")))
    val exitRegionName = if (psiphonRegion.isEmpty()) {
        Texts.exitRegionAuto
    } else {
        PsiphonExit.REGIONS.find { it.first == psiphonRegion }
            ?.let { "${PsiphonExit.flag(it.first)} ${it.second}" }
            ?: psiphonRegion
    }
    val destCode = if (isQuantum) {
        if (psiphonExit) {
            // Exit on: the destination is the Psiphon egress country, not the relay.
            if (psiphonRegion.isNotEmpty()) psiphonRegion
            else if (isConnected && state.exitPlace.isNotEmpty()) inferCountryCode(state.exitPlace, "")
            else "GLOBAL"
        } else "CLXY"
    } else {
        // Never take(2) of the exit NAME: "CatchAll Anycast / USA"
        // became "CA" and drew the Canada flag for a USA server.
        if (state.exit.isNotEmpty()) inferCountryCode(state.exit, "")
        else if (pinned.isNotBlank()) inferCountryCode(pinnedLabel, pinned)
        else "REC"
    }
    val destName = if (isQuantum) {
        if (psiphonExit) {
            // A region the user picked is the contract: show its name. The
            // probe's geo of the egress IP can disagree (anycast/WARP IPs
            // geolocate wrong — a Cloudflare egress read as "Tehran, Iran"
            // under a US flag, rc6 2026-11-27), so the probe place is only
            // the fallback for auto mode.
            if (psiphonRegion.isNotEmpty()) {
                PsiphonExit.REGIONS.find { it.first == psiphonRegion }?.second ?: psiphonRegion
            } else if (isConnected && state.exitPlace.isNotEmpty()) state.exitPlace
            else "Psiphon exit · ${Texts.exitRegionAuto}"
        } else if (quantumPinnedLabel.isNotBlank()) quantumPinnedLabel
        else "Auto Scan"
    } else {
        if (state.exit.isNotEmpty()) state.exit
        else if (pinnedLabel.isNotBlank()) pinnedLabel
        else "Auto Route · Recommended"
    }
    val destIp = if (isQuantum) {
        if (psiphonExit) {
            if (isConnected && state.exitIp.isNotEmpty()) state.exitIp
            else "Psiphon exit"
        } else if (quantumPinned.isNotBlank()) quantumPinned
        else "Direct MASQUE Route"
    } else {
        if (state.exitIp.isNotEmpty()) state.exitIp
        else if (isConnected) "Direct Tunnel"
        else "Auto Route"
    }
    val pingMs = state.latencyMs.takeIf { it > 0 } ?: if (isConnected) 42 else -1
    val pingLabel = if (pingMs > 0) "${pingMs}ms" else if (isConnected) "Online" else "Ready"

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val compact = maxHeight < 680.dp
        val topPadding = if (maxHeight > 680.dp) {
            ((maxHeight - 620.dp) * 0.12f).coerceIn(8.dp, 20.dp)
        } else {
            6.dp
        }
        val sectionGap = if (compact) 6.dp else 10.dp
        val cardGap = if (compact) 6.dp else 10.dp
        val timerSizeSp = if (compact) 36.sp else 42.sp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(topPadding))
            ClxyBrandHeader(
                logoSize = if (compact) 26.dp else 28.dp,
                labelSizeSp = if (compact) 14f else 15f,
            )
            Spacer(modifier = Modifier.height(sectionGap))

            // New-release banner: same update dialog as Profile.
            val update = state.update
            if (update != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 380.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(ClxyColors.CardSurface)
                        .border(1.dp, ClxyColors.CyanBorder, RoundedCornerShape(999.dp))
                        .clickable { showUpdate = true }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        Icons.Rounded.SystemUpdate,
                        contentDescription = null,
                        tint = ClxyColors.CyanText,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = "New version v${update.version} available",
                        color = ClxyColors.CyanText,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = null,
                        tint = ClxyColors.CyanText,
                        modifier = Modifier.size(16.dp),
                    )
                }
                Spacer(modifier = Modifier.height(sectionGap))
            }
            if (showUpdate) AppUpdateDialog(update = state.update, onDismiss = { showUpdate = false })

            // Dual Core Switcher (1-tap toggle between Hyper and Aether/Quantum)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 380.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color(0xD10C1328))
                    .border(1.dp, ClxyColors.GlassBorder, RoundedCornerShape(999.dp))
                    .padding(3.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // CLXY Hyper
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(999.dp))
                        .background(if (isHyper) ClxyColors.CyanGlow.copy(alpha = 0.22f) else Color.Transparent)
                        .border(
                            1.dp,
                            if (isHyper) ClxyColors.CyanGlow.copy(alpha = 0.55f) else Color.Transparent,
                            RoundedCornerShape(999.dp)
                        )
                        .clickable(enabled = !state.running && !state.busy && !state.disconnecting) {
                            Prefs.coreId = "firefox"
                            AppState.update { it.copy(coreId = "firefox") }
                        }
                        .padding(vertical = 7.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            Icons.Rounded.Bolt,
                            contentDescription = null,
                            tint = if (isHyper) ClxyColors.CyanText else ClxyColors.TextDim,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = "CLXY Hyper",
                            color = if (isHyper) ClxyColors.CyanText else ClxyColors.TextDim,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                // CLXY Quantum (Aether)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(999.dp))
                        .background(if (isQuantum) ClxyColors.QuantumPurple.copy(alpha = 0.28f) else Color.Transparent)
                        .border(
                            1.dp,
                            if (isQuantum) ClxyColors.QuantumPurple.copy(alpha = 0.65f) else Color.Transparent,
                            RoundedCornerShape(999.dp)
                        )
                        .clickable(enabled = !state.running && !state.busy && !state.disconnecting) {
                            Prefs.coreId = "aether"
                            AppState.update { it.copy(coreId = "aether") }
                        }
                        .padding(vertical = 7.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("🪐", fontSize = 12.sp)
                        Text(
                            text = "CLXY Quantum",
                            color = if (isQuantum) ClxyColors.QuantumPurple else ClxyColors.TextDim,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(sectionGap))

            // Shield + Status Text
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                modifier = Modifier.testTag("home_status_row"),
            ) {
                ShieldIcon(color = shieldColor, sizeDp = 14f)
                AnimatedContent(
                    targetState = statusLabel,
                    transitionSpec = { fadeIn(tween(240)) togetherWith fadeOut(tween(180)) },
                    label = "statusLabelAnim",
                ) { label ->
                    Text(
                        text = label,
                        color = statusColor,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Large Tabular Timer
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.testTag("home_timer"),
            ) {
                if (hrsStr.isNotEmpty()) {
                    Text(
                        text = hrsStr,
                        color = ClxyColors.TimerDim,
                        fontSize = timerSizeSp,
                        fontWeight = FontWeight.Normal,
                        letterSpacing = 0.5.sp,
                    )
                }
                Text(
                    text = minSecStr,
                    color = ClxyColors.TextPrimary,
                    fontSize = timerSizeSp,
                    fontWeight = FontWeight.Normal,
                    letterSpacing = 0.5.sp,
                )
            }

            Spacer(modifier = Modifier.height(sectionGap))

            // Speed stats row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 320.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = downRate,
                            color = ClxyColors.TextPrimary,
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = " KB/s",
                            color = ClxyColors.TextMuted,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Normal,
                        )
                    }
                    Text("Download", color = ClxyColors.TextDim, fontSize = 11.sp)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = upRate,
                            color = ClxyColors.TextPrimary,
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = " KB/s",
                            color = ClxyColors.TextMuted,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Normal,
                        )
                    }
                    Text("Upload", color = ClxyColors.TextDim, fontSize = 11.sp)
                }
            }

            // Flexible Dotted World Map + Main Power Button
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f, fill = true)
                    .fillMaxWidth()
                    .widthIn(max = 420.dp)
                    .padding(vertical = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                val mapHeight = maxHeight.coerceAtMost(220.dp)
                val buttonDiameter = (maxHeight / 1.5f).coerceIn(72.dp, 132.dp)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(mapHeight),
                    contentAlignment = Alignment.Center,
                ) {
                    DottedWorldMap(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("home_world_map"),
                        dotAlpha = if (isConnected || isConnecting) 0.54f else 0.44f,
                        animated = true,
                    )

                    CosmicPowerButton(
                        connected = isConnected,
                        connecting = isConnecting,
                        isQuantum = isQuantum,
                        buttonSize = buttonDiameter,
                        onClick = onToggle,
                    )
                }
            }

            // Psiphon exit (Quantum only): a small toggle button + the region
            // chips, above the route box — nothing exit-related at the top.
            if (isQuantum) {
                Spacer(modifier = Modifier.height(sectionGap))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("home_exit_row"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ExitToggleButton(
                        checked = psiphonExit,
                        onClick = {
                            psiphonExit = !psiphonExit
                            Prefs.psiphonExit = psiphonExit
                        },
                    )
                    Text(
                        text = "${Texts.exitRegion}: $exitRegionName",
                        color = ClxyColors.TextMuted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    // Plain-language guide: what plain Quantum does and what
                    // the Psiphon exit adds — a popup, no jargon.
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .border(1.dp, ClxyColors.CyanGlow.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                            .clickable { showExitGuide = true }
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = Texts.exitGuide,
                            color = ClxyColors.CyanText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    item {
                        ExitRegionChip(
                            label = Texts.exitRegionAuto,
                            isSelected = psiphonRegion.isEmpty(),
                            onClick = {
                                psiphonRegion = ""
                                Prefs.psiphonRegion = ""
                            },
                        )
                    }
                    items(PsiphonExit.REGIONS) { (code, _) ->
                        ExitRegionChip(
                            label = "${PsiphonExit.flag(code)} $code",
                            isSelected = psiphonRegion == code,
                            onClick = {
                                psiphonRegion = code
                                Prefs.psiphonRegion = code
                            },
                        )
                    }
                }
                Spacer(modifier = Modifier.height(sectionGap))
                if (showExitGuide) {
                    ExitGuideDialog(onDismiss = { showExitGuide = false })
                }
            }

            RouteTunnelBox(
                connected = isConnected || isConnecting,
                originCode = originCode,
                originName = "Your Device",
                originIp = "Direct Local",
                destCode = destCode,
                destName = destName,
                destIp = destIp,
            )

            Spacer(modifier = Modifier.height(cardGap))

            // In Hyper mode: show account quota and server selector card.
            // In Quantum (Aether) mode: both are hidden (no location selection, zero login & unlimited).
            if (isHyper) {
                HomeDataUsageCard(
                    quota = state.quota.ifBlank { Store.quota(context).orEmpty() },
                    hasSession = Store.hasSession(context),
                    email = Store.email(context).orEmpty(),
                    onOpenSignIn = onOpenSignIn,
                    modifier = Modifier.widthIn(max = 460.dp),
                )

                Spacer(modifier = Modifier.height(cardGap))

                // Selected Server Card -> navigates to All Servers
                Row(
                    modifier = Modifier
                        .testTag("home_server_card")
                        .fillMaxWidth()
                        .widthIn(max = 460.dp)
                        .defaultMinSize(minHeight = 58.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(ClxyColors.CardSurface)
                        .border(1.dp, ClxyColors.GlassBorder, RoundedCornerShape(12.dp))
                        .clickable(onClick = onOpenServers)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(13.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        CircularCountryFlag(countryCode = destCode, size = 34.dp)
                        Column {
                            Text(
                                text = destName,
                                color = ClxyColors.TextPrimary,
                                fontSize = 14.sp,
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
                                    text = "$destIp • $pingLabel",
                                    color = ClxyColors.TextMuted,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                SignalStrengthBars(pingMs = pingMs)
                            }
                        }
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = "Choose Server",
                        tint = ClxyColors.TextMuted,
                        modifier = Modifier.size(20.dp),
                    )
                }

                Spacer(modifier = Modifier.height(cardGap))
            }

            // Mode Selector (Proxy SOCKS5 | VPN All Device)
            ModePillGroup(
                isVpn = state.mode == AppState.Mode.VPN,
                onSelectMode = { isVpnMode ->
                    val newMode = if (isVpnMode) AppState.Mode.VPN else AppState.Mode.PROXY
                    Prefs.mode = newMode
                    AppState.update { it.copy(mode = newMode) }
                },
                modifier = Modifier.widthIn(max = 460.dp),
            )

            Spacer(modifier = Modifier.height(if (compact) 8.dp else 12.dp))
        }
    }
}

// Plain-language guide for the exit country: what plain Quantum does and
// what the Psiphon exit adds. Written for ordinary users — no jargon.
// The pill in the corner switches the text to Persian on demand.
@Composable
private fun ExitGuideDialog(onDismiss: () -> Unit) {
    var showFa by remember { mutableStateOf(false) }
    val title = if (showFa) Texts.exitGuideTitleFa else Texts.exitGuideTitle
    val body = if (showFa) Texts.exitGuideBodyFa else Texts.exitGuideBody
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(ClxyColors.CardSurface)
                .border(1.dp, ClxyColors.CyanGlow.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = ClxyColors.TextPrimary,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (!Texts.isFa) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .border(1.dp, ClxyColors.CyanGlow.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                            .clickable { showFa = !showFa }
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = if (showFa) Texts.languageName else "فارسی",
                            color = ClxyColors.CyanText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
            Text(
                text = body,
                fontSize = 13.sp,
                color = ClxyColors.TextMuted,
            )
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = Texts.exitGuideClose)
            }
        }
    }
}

@Composable
private fun ExitToggleButton(
    checked: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (checked) ClxyColors.QuantumPurple.copy(alpha = 0.28f) else ClxyColors.CardSurface)
            .border(
                1.dp,
                if (checked) ClxyColors.QuantumPurple.copy(alpha = 0.65f) else ClxyColors.GlassBorder,
                RoundedCornerShape(999.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (checked) ClxyColors.QuantumPurple else ClxyColors.TextDim),
            )
            Text(
                text = Texts.exitNode,
                color = if (checked) ClxyColors.QuantumPurple else ClxyColors.TextDim,
                fontSize = 12.sp,
                fontWeight = if (checked) FontWeight.Bold else FontWeight.Normal,
            )
            Text(
                text = if (checked) "On" else "Off",
                color = if (checked) ClxyColors.QuantumPurple else ClxyColors.TextMuted,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun ExitRegionChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (isSelected) ClxyColors.QuantumPurple.copy(alpha = 0.28f) else ClxyColors.CardSurface)
            .border(
                1.dp,
                if (isSelected) ClxyColors.QuantumPurple.copy(alpha = 0.65f) else ClxyColors.GlassBorder,
                RoundedCornerShape(999.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (isSelected) ClxyColors.QuantumPurple else ClxyColors.TextDim,
            fontSize = 12.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

@Composable
fun ShieldIcon(color: Color, sizeDp: Float = 14f) {
    Canvas(modifier = Modifier.size(width = sizeDp.dp, height = (sizeDp * 1.14f).dp)) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(w * 0.5f, h * 0.03f)
            lineTo(w * 0.11f, h * 0.17f)
            lineTo(w * 0.11f, h * 0.46f)
            cubicTo(w * 0.11f, h * 0.69f, w * 0.27f, h * 0.91f, w * 0.50f, h * 0.97f)
            cubicTo(w * 0.73f, h * 0.91f, w * 0.89f, h * 0.69f, w * 0.89f, h * 0.46f)
            lineTo(w * 0.89f, h * 0.17f)
            close()
        }
        drawPath(path = path, color = color, style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round))
    }
}

@Composable
private fun CosmicPowerButton(
    connected: Boolean,
    connecting: Boolean,
    isQuantum: Boolean,
    buttonSize: Dp = 132.dp,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "powerPressScale",
    )

    val infinite = rememberInfiniteTransition(label = "powerPulse")
    val phase1 by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "phase1",
    )
    val idleBreath by infinite.animateFloat(
        initialValue = 0.98f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "idleBreath",
    )
    val phase2 = (phase1 + 0.5f) % 1f

    val glowPrimary by animateColorAsState(
        targetValue = when {
            connected -> if (isQuantum) ClxyColors.QuantumPurple else ClxyColors.CyanGlow
            connecting -> ClxyColors.AmberWarning
            else -> ClxyColors.BluePrimary.copy(alpha = 0.5f)
        },
        animationSpec = tween(420),
        label = "glowPrimary",
    )
    val glowSecondary by animateColorAsState(
        targetValue = when {
            connected -> if (isQuantum) ClxyColors.QuantumViolet else ClxyColors.PurplePrimary
            connecting -> ClxyColors.BluePrimary
            else -> ClxyColors.PurplePrimary.copy(alpha = 0.4f)
        },
        animationSpec = tween(420),
        label = "glowSecondary",
    )

    Box(
        modifier = Modifier
            .size(buttonSize * 1.46f)
            .scale(pressScale),
        contentAlignment = Alignment.Center,
    ) {
        if (connected || connecting) {
            listOf(phase1, phase2).forEach { p ->
                val ringScale = 0.96f + p * 0.42f
                val ringAlpha = (1f - p) * 0.52f
                Box(
                    modifier = Modifier
                        .size(buttonSize)
                        .scale(ringScale)
                        .clip(CircleShape)
                        .border(
                            width = 2.dp,
                            brush = Brush.linearGradient(
                                listOf(
                                    glowPrimary.copy(alpha = ringAlpha),
                                    glowSecondary.copy(alpha = ringAlpha),
                                ),
                            ),
                            shape = CircleShape,
                        )
                        .background(
                            Brush.radialGradient(
                                listOf(
                                    glowPrimary.copy(alpha = ringAlpha * 0.25f),
                                    Color.Transparent,
                                ),
                            ),
                        ),
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .size(buttonSize * 1.08f)
                    .scale(idleBreath)
                    .clip(CircleShape)
                    .border(
                        1.dp,
                        (if (isQuantum) ClxyColors.QuantumPurple else ClxyColors.CyanGlow).copy(alpha = 0.16f),
                        CircleShape
                    ),
            )
        }

        val coreStart by animateColorAsState(
            targetValue = when {
                connected -> if (isQuantum) ClxyColors.QuantumPurple else ClxyColors.CyanBright
                connecting -> ClxyColors.AmberWarning
                else -> Color(0xFF1E293B)
            },
            animationSpec = tween(380),
            label = "coreStart",
        )
        val coreMid by animateColorAsState(
            targetValue = when {
                connected -> if (isQuantum) Color(0xFF4C1D95) else ClxyColors.BlueDeep
                connecting -> ClxyColors.BluePrimary
                else -> Color(0xFF0F172A)
            },
            animationSpec = tween(380),
            label = "coreMid",
        )
        val coreEnd by animateColorAsState(
            targetValue = when {
                connected -> if (isQuantum) ClxyColors.QuantumViolet else ClxyColors.PurplePrimary
                connecting -> ClxyColors.PurplePrimary
                else -> Color(0xFF1E1B4B)
            },
            animationSpec = tween(380),
            label = "coreEnd",
        )
        val borderColor by animateColorAsState(
            targetValue = if (connected || connecting) Color(0x99BAE6FD) else Color(0x4794A3B8),
            animationSpec = tween(320),
            label = "powerBorder",
        )

        Box(
            modifier = Modifier
                .testTag("home_power_button")
                .size(buttonSize)
                .shadow(
                    elevation = if (connected) 28.dp else 14.dp,
                    shape = CircleShape,
                    ambientColor = if (connected) glowPrimary else Color.Black,
                    spotColor = if (connected) glowSecondary else Color.Black,
                )
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(coreStart, coreMid, coreEnd)))
                .border(1.5.dp, borderColor, CircleShape)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (connecting) {
                CircularProgressIndicator(
                    color = Color.White,
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(buttonSize * 0.33f),
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.PowerSettingsNew,
                    contentDescription = if (connected) "Disconnect VPN" else "Connect VPN",
                    tint = Color.White,
                    modifier = Modifier.size(buttonSize * 0.35f),
                )
            }
        }
    }
}

@Composable
private fun RouteTunnelBox(
    connected: Boolean,
    originCode: String,
    originName: String,
    originIp: String,
    destCode: String,
    destName: String,
    destIp: String,
) {
    val infinite = rememberInfiniteTransition(label = "routeFlow")
    val dashOffset by infinite.animateFloat(
        initialValue = 0f,
        targetValue = -16f,
        animationSpec = infiniteRepeatable(tween(700, easing = LinearEasing)),
        label = "dashOffset",
    )
    val packetProgress by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1150, easing = LinearEasing)),
        label = "packetProgress",
    )
    val boxBorder by animateColorAsState(
        targetValue = if (connected) Color(0x4D38BDF8) else Color(0x2294A3B8),
        animationSpec = tween(320),
        label = "routeBorder",
    )

    Row(
        modifier = Modifier
            .testTag("home_route_tunnel_box")
            .fillMaxWidth()
            .widthIn(max = 380.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(ClxyColors.RouteBoxSurface)
            .border(1.dp, boxBorder, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        // Origin node
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp, Alignment.End),
            modifier = Modifier
                .weight(1f)
                .testTag("home_route_origin"),
        ) {
            Column(
                horizontalAlignment = Alignment.End,
                modifier = Modifier.weight(1f, fill = false),
            ) {
                Text(
                    text = originName,
                    color = ClxyColors.TextPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = originIp,
                    color = ClxyColors.TextMuted,
                    fontSize = 9.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            CircularCountryFlag(
                countryCode = originCode,
                size = 24.dp,
                modifier = Modifier.testTag("home_origin_flag"),
            )
        }

        // Animated dashed arrow + glowing packet bead when connected
        Canvas(
            modifier = Modifier
                .testTag("home_route_arrow")
                .padding(horizontal = 8.dp)
                .width(54.dp)
                .height(14.dp),
        ) {
            val cy = size.height / 2f
            val startX = 2.dp.toPx()
            val endX = size.width - 4.dp.toPx()
            val brush = if (connected) {
                Brush.horizontalGradient(listOf(ClxyColors.CyanGlow, ClxyColors.BluePrimary, Color(0xFFA855F7)))
            } else {
                Brush.horizontalGradient(listOf(ClxyColors.TimerDim, ClxyColors.TimerDim))
            }
            drawLine(
                brush = brush,
                start = Offset(startX, cy),
                end = Offset(endX, cy),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round,
                pathEffect = if (connected) {
                    PathEffect.dashPathEffect(floatArrayOf(8f, 8f), dashOffset)
                } else {
                    PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                },
            )
            if (connected) {
                val beadX = startX + (endX - startX) * packetProgress
                drawCircle(
                    color = ClxyColors.CyanGlow.copy(alpha = 0.35f),
                    radius = 5.dp.toPx(),
                    center = Offset(beadX, cy),
                )
                drawCircle(
                    color = Color.White,
                    radius = 2.2.dp.toPx(),
                    center = Offset(beadX, cy),
                )
            }
            val head = Path().apply {
                moveTo(endX - 5.dp.toPx(), cy - 4.dp.toPx())
                lineTo(endX, cy)
                lineTo(endX - 5.dp.toPx(), cy + 4.dp.toPx())
            }
            drawPath(
                head,
                color = if (connected) Color(0xFFA855F7) else ClxyColors.TimerDim,
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }

        // Destination node
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            modifier = Modifier.weight(1f),
        ) {
            CircularCountryFlag(
                countryCode = destCode,
                size = 24.dp,
                modifier = Modifier.testTag("home_dest_flag"),
            )
            Column {
                Text(
                    text = destName,
                    color = ClxyColors.TextPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = destIp,
                    color = if (connected) ClxyColors.CyanText else ClxyColors.TextMuted,
                    fontSize = 9.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ModePillGroup(
    isVpn: Boolean,
    onSelectMode: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(999.dp))
            .background(Color(0xD10C1328))
            .border(1.dp, Color(0x2994A3B8), RoundedCornerShape(999.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        listOf(
            Triple(true, "VPN Mode", "All Device"),
            Triple(false, "Proxy Mode", "SOCKS5 1080")
        ).forEach { (vpnChoice, label, subtitle) ->
            val active = (vpnChoice == isVpn)
            val pillScale by animateFloatAsState(
                targetValue = if (active) 1.0f else 0.98f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                label = "modeScale_$label",
            )
            val borderCol by animateColorAsState(
                targetValue = if (active) ClxyColors.CyanGlow.copy(alpha = 0.62f) else Color.Transparent,
                animationSpec = tween(240),
                label = "modeBorder_$label",
            )
            val titleCol by animateColorAsState(
                targetValue = if (active) Color.White else ClxyColors.TextMuted,
                animationSpec = tween(220),
                label = "modeText_$label",
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .scale(pillScale)
                    .defaultMinSize(minHeight = 44.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .then(
                        if (active) {
                            Modifier.background(
                                Brush.linearGradient(
                                    listOf(
                                        Color(0x3D00E5FF),
                                        Color(0x4D3B82F6),
                                        Color(0x4D9333EA),
                                    ),
                                ),
                            )
                        } else {
                            Modifier
                        },
                    )
                    .border(1.dp, borderCol, RoundedCornerShape(999.dp))
                    .clickable { onSelectMode(vpnChoice) }
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = label,
                        color = titleCol,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = subtitle,
                        color = if (active) ClxyColors.CyanText else ClxyColors.TextDim,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeDataUsageCard(
    quota: String,
    hasSession: Boolean,
    email: String,
    onOpenSignIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val usedLabel = if (quota.isNotBlank()) quota else if (hasSession) "Plan Active" else "No active account"
    val subtitle = if (email.isNotBlank()) email else if (hasSession) "CLXY Cloud Account" else "Sign in to track quota"

    // Whole-device traffic since boot (TrafficStats totals), refreshed every
    // second so the number next to the quota stays live: "unlimited / 12,4 GB".
    var deviceTotal by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        while (true) {
            deviceTotal = deviceTotalUsage()
            delay(1000L)
        }
    }
    val quotaLine =
        if (quota.isNotBlank() && deviceTotal != null) "$usedLabel / $deviceTotal" else usedLabel

    Row(
        modifier = modifier
            .testTag("home_data_usage_card")
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(ClxyColors.RouteBoxSurface)
            .border(1.dp, ClxyColors.GlassBorder, RoundedCornerShape(12.dp))
            .clickable(enabled = !hasSession, onClick = onOpenSignIn)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Account Data / Quota",
                color = ClxyColors.TextDim,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = quotaLine,
                color = ClxyColors.TextPrimary,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                color = ClxyColors.TextMuted,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Box(
            modifier = Modifier
                .width(1.dp)
                .height(34.dp)
                .background(Color(0x2E94A3B8)),
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 14.dp),
            horizontalAlignment = Alignment.End,
        ) {
            Text(
                text = "CLXY Cloud",
                color = ClxyColors.TextDim,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = if (hasSession) "Connected" else "Add Account",
                color = if (hasSession) ClxyColors.CyanText else ClxyColors.AmberWarning,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = if (hasSession) "Sync Active" else "Tap to sign in",
                color = ClxyColors.TextMuted,
                fontSize = 10.sp,
                maxLines = 1,
            )
        }
    }
}

/** Whole-device Rx+Tx since boot, formatted; null when the device does not report totals. */
private fun deviceTotalUsage(): String? {
    val rx = TrafficStats.getTotalRxBytes()
    val tx = TrafficStats.getTotalTxBytes()
    if (rx < 0L || tx < 0L) return null
    return formatBytes(rx + tx)
}

/** 1024-based, one decimal, comma separator like the speed readout. */
private fun formatBytes(bytes: Long): String {
    if (bytes < 1024L) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var value = bytes / 1024.0
    var unit = 0
    while (value >= 1024.0 && unit < units.size - 1) {
        value /= 1024.0
        unit++
    }
    return String.format(Locale.US, "%.1f %s", value, units[unit]).replace('.', ',')
}
