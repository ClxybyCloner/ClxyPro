package com.clxypro.vpn.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.clxypro.vpn.BuildConfig
import com.clxypro.vpn.R
import com.clxypro.vpn.Texts
import com.clxypro.vpn.core.AppState
import com.clxypro.vpn.core.CoreRegistry
import com.clxypro.vpn.core.GithubUpdates
import com.clxypro.vpn.core.Prefs
import com.clxypro.vpn.core.ServerChoice
import com.clxypro.vpn.core.SstpStore
import com.clxypro.vpn.core.StatusText
import com.clxypro.vpn.core.SunAndLionStore
import com.clxypro.vpn.ui.theme.ClxyColors
import com.clxypro.vpn.ui.theme.InterFontFamily
import com.clxypro.vpn.vpn.Store
import kotlinx.coroutines.delay

private enum class Phase { Off, Connecting, Disconnecting, On, Error }

@Composable
fun HomeScreen(
    state: AppState.State,
    onToggle: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenTools: () -> Unit,
    onOpenServers: () -> Unit = {},
    onOpenSignIn: () -> Unit = onOpenSettings,
    onOpenLogs: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val phase = phaseOf(state)
    val isConnected = phase == Phase.On
    val isConnecting = phase == Phase.Connecting || phase == Phase.Disconnecting

    var showSstpScanDialog by remember { mutableStateOf(false) }
    var showPrimeScanDialog by remember { mutableStateOf(false) }
    var hasSstpCached by remember(state.coreId) { mutableStateOf(SstpStore(context).hasCached()) }
    var hasPrimeCached by remember(state.coreId) { mutableStateOf(SunAndLionStore(context).load() != null) }

    fun handleConnect() {
        if (!state.connected && !state.running && !state.busy) {
            if (state.coreId == "sstp" && !SstpStore(context).hasCached()) {
                showSstpScanDialog = true
                return
            } else if (state.coreId == "sunlion" && SunAndLionStore(context).load() == null) {
                showPrimeScanDialog = true
                return
            }
        }
        onToggle()
    }

    ClxyCosmicBackground(modifier = modifier) {
        // Dotted world map in background behind upper content
        DottedWorldMap(
            modifier = Modifier
                .fillMaxWidth()
                .height(380.dp)
                .align(Alignment.TopCenter)
                .padding(top = 70.dp),
            dotAlpha = if (isConnected) 0.45f else 0.28f,
            animated = isConnected || isConnecting,
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            // Header
            TopBar(onOpenTools, onOpenSettings, onOpenLogs)

            Spacer(Modifier.height(12.dp))

            // Core selector pills (Firefox, Psiphon, Tor, OpenRung, Lantern, SSTP)
            CorePicker(state, enabled = !state.running && !state.busy && !state.disconnecting)

            if (ServerChoice.supports(state.coreId)) {
                Spacer(Modifier.height(8.dp))
                ServerChip(
                    coreId = state.coreId,
                    enabled = !state.running && !state.busy && !state.disconnecting,
                    onOpenAllServers = onOpenServers,
                )
            }

            if (state.coreId == "sstp" && hasSstpCached) {
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(ClxyColors.EmeraldGood.copy(alpha = 0.12f))
                        .border(0.8.dp, ClxyColors.EmeraldGood.copy(alpha = 0.35f), RoundedCornerShape(20.dp))
                        .clickable {
                            SstpStore(context).setLastEndpoint(null)
                            hasSstpCached = false
                            Toast.makeText(context, "سرور کش‌شده پاک شد · اسکن مجدد فعال شد", Toast.LENGTH_SHORT).show()
                        }
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(Icons.Rounded.Bolt, contentDescription = null, tint = ClxyColors.EmeraldGood, modifier = Modifier.size(12.dp))
                    Text("سرور فعال ذخیره شده · لمس برای اسکن مجدد", fontSize = 10.5.sp, color = ClxyColors.EmeraldGood)
                }
            } else if (state.coreId == "sunlion" && hasPrimeCached) {
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(ClxyColors.CyanGlow.copy(alpha = 0.12f))
                        .border(0.8.dp, ClxyColors.CyanGlow.copy(alpha = 0.35f), RoundedCornerShape(20.dp))
                        .clickable {
                            SunAndLionStore(context).clear()
                            hasPrimeCached = false
                            Toast.makeText(context, "مسیرهای کش‌شده پاک شد · اسکن مجدد فعال شد", Toast.LENGTH_SHORT).show()
                        }
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(Icons.Rounded.Speed, contentDescription = null, tint = ClxyColors.CyanGlow, modifier = Modifier.size(12.dp))
                    Text("مسیر لبه ذخیره شده · لمس برای اسکن مجدد", fontSize = 10.5.sp, color = ClxyColors.CyanGlow)
                }
            }

            Spacer(Modifier.height(14.dp))

            // Main glowing multi-layer Connect Button
            ClxyPowerButton(
                phase = phase,
                onClick = { handleConnect() },
            )

            Spacer(Modifier.height(14.dp))

            // Animated Route Tunnel Box
            RouteTunnelBox(
                connected = isConnected,
                originIp = if (isConnected) "127.0.0.1" else "Local",
                destCode = if (state.exit.isNotBlank()) state.exit else "AUTO",
                destName = if (state.exitPlace.isNotBlank()) state.exitPlace else Texts.coreName(state.coreId),
                destIp = if (state.exitIp.isNotBlank()) state.exitIp else "Protected Tunnel",
                onClick = onOpenServers,
            )

            Spacer(Modifier.height(12.dp))

            // Status block & Diagnostics info
            StatusBlock(state, phase, onOpenLogs)

            if (state.coreId == "firefox" && !Store.hasSession(context)) {
                Spacer(Modifier.height(8.dp))
                AccountChip(onClick = onOpenSignIn)
            }

            state.update?.let { release ->
                Spacer(Modifier.height(6.dp))
                UpdateBadge(release)
            }

            Text(
                text = stringResource(R.string.about_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
                style = MaterialTheme.typography.labelSmall,
                color = ClxyColors.TextDim.copy(alpha = 0.65f),
                modifier = Modifier.padding(top = 10.dp, bottom = 4.dp),
            )
        }

        if (showSstpScanDialog) {
            Dialog(onDismissRequest = { showSstpScanDialog = false }) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = ClxyColors.CardSurface,
                    border = BorderStroke(1.dp, ClxyColors.CyanGlow.copy(alpha = 0.5f)),
                    modifier = Modifier.padding(16.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Rounded.Bolt,
                            contentDescription = null,
                            tint = ClxyColors.CyanGlow,
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "CLXY Ghost · بهینه‌سازی سرورها",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = ClxyColors.TextPrimary
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "سرورهای فعال برای اولین بار اسکن و کش می‌شوند تا در دفعات بعدی اتصال آنی باشد. این اسکن فقط یک‌بار تا زمان فعال بودن سرور انجام می‌شود.",
                            fontSize = 12.5.sp,
                            lineHeight = 18.sp,
                            color = ClxyColors.TextSecondary,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(18.dp))
                        Button(
                            onClick = {
                                showSstpScanDialog = false
                                onToggle()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = ClxyColors.CyanGlow),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) {
                            Text("🚀 اسکن خودکار و اتصال فوری", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = {
                                showSstpScanDialog = false
                                onToggle()
                            },
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, ClxyColors.GlassBorder),
                            modifier = Modifier.fillMaxWidth().height(44.dp)
                        ) {
                            Text("⚡ اتصال سریع مستقیم", color = ClxyColors.TextMuted, fontSize = 13.sp)
                        }
                    }
                }
            }
        }

        if (showPrimeScanDialog) {
            Dialog(onDismissRequest = { showPrimeScanDialog = false }) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = ClxyColors.CardSurface,
                    border = BorderStroke(1.dp, ClxyColors.CyanGlow.copy(alpha = 0.5f)),
                    modifier = Modifier.padding(16.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Rounded.Speed,
                            contentDescription = null,
                            tint = ClxyColors.CyanGlow,
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "CLXY Prime · بهینه‌سازی مسیرهای لبه",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = ClxyColors.TextPrimary
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "مسیرهای CDN فرانتینگ برای بار اول اسکن و کش می‌شوند تا اتصالات بعدی با سرعت حداکثری و بدون تأخیر برقرار شوند.",
                            fontSize = 12.5.sp,
                            lineHeight = 18.sp,
                            color = ClxyColors.TextSecondary,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(18.dp))
                        Button(
                            onClick = {
                                showPrimeScanDialog = false
                                onToggle()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = ClxyColors.CyanGlow),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) {
                            Text("🚀 بهینه‌سازی مسیرها و اتصال", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = {
                                showPrimeScanDialog = false
                                onToggle()
                            },
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, ClxyColors.GlassBorder),
                            modifier = Modifier.fillMaxWidth().height(44.dp)
                        ) {
                            Text("⚡ اتصال پیش‌فرض", color = ClxyColors.TextMuted, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}

private fun phaseOf(state: AppState.State): Phase = when {
    state.disconnecting -> Phase.Disconnecting
    state.connected -> Phase.On
    state.busy || state.running -> Phase.Connecting
    state.statusCode >= 2000 && state.statusCode != StatusText.STOPPED -> Phase.Error
    else -> Phase.Off
}

@Composable
private fun TopBar(
    onOpenTools: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenLogs: () -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        ClxyBrandHeader(
            logoSize = 34.dp,
            showLabel = true,
            labelSizeSp = 18f,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            IconButton(
                onClick = onOpenLogs,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(ClxyColors.CardSurface)
                    .border(0.8.dp, ClxyColors.GlassBorder, CircleShape),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Terminal,
                    contentDescription = "Live Logs",
                    tint = ClxyColors.Cyan,
                    modifier = Modifier.size(20.dp),
                )
            }
            IconButton(
                onClick = onOpenTools,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(ClxyColors.CardSurface)
                    .border(0.8.dp, ClxyColors.GlassBorder, CircleShape),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Widgets,
                    contentDescription = Texts.tools,
                    tint = ClxyColors.CyanGlow,
                    modifier = Modifier.size(20.dp),
                )
            }
            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(ClxyColors.CardSurface)
                    .border(0.8.dp, ClxyColors.GlassBorder, CircleShape),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Tune,
                    contentDescription = stringResource(R.string.cd_settings),
                    tint = ClxyColors.TextSecondary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CorePicker(state: AppState.State, enabled: Boolean) {
    val cores = CoreRegistry.all()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        cores.forEach { core ->
            val selected = core.id == state.coreId
            Surface(
                shape = RoundedCornerShape(50),
                color = if (selected) {
                    ClxyColors.CyanGlow.copy(alpha = 0.16f)
                } else {
                    ClxyColors.CardSurface
                },
                border = BorderStroke(
                    1.dp,
                    if (selected) ClxyColors.CyanGlow.copy(alpha = 0.70f)
                    else ClxyColors.GlassBorder,
                ),
                modifier = Modifier.clickable(enabled = enabled) {
                    Prefs.coreId = core.id
                    AppState.update { it.copy(coreId = core.id) }
                },
            ) {
                Text(
                    text = Texts.coreName(core.id),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    color = if (selected) ClxyColors.CyanBright else ClxyColors.TextMuted,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                )
            }
        }
    }
}

@Composable
private fun ClxyPowerButton(
    phase: Phase,
    onClick: () -> Unit,
) {
    val isConnected = phase == Phase.On
    val isConnecting = phase == Phase.Connecting || phase == Phase.Disconnecting

    val transition = rememberInfiniteTransition(label = "powerPulse")
    val pulse by transition.animateFloat(
        initialValue = 0.97f,
        targetValue = 1.03f,
        animationSpec = infiniteRepeatable(
            tween(1800, easing = FastOutSlowInEasing),
            RepeatMode.Reverse,
        ),
        label = "pulse",
    )
    val spin by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            tween(2400, easing = LinearEasing),
        ),
        label = "spin",
    )

    val coreGradient = when (phase) {
        Phase.On -> Brush.linearGradient(
            listOf(ClxyColors.CyanBright, ClxyColors.BluePrimary, ClxyColors.PurplePrimary),
        )
        Phase.Connecting, Phase.Disconnecting -> Brush.linearGradient(
            listOf(ClxyColors.AmberWarning, ClxyColors.BlueDeep),
        )
        Phase.Error -> Brush.linearGradient(
            listOf(ClxyColors.RedDisconnectStart, ClxyColors.RedDisconnectEnd),
        )
        Phase.Off -> Brush.linearGradient(
            listOf(Color(0xFF131C35), Color(0xFF090E22)),
        )
    }

    val glowColor by animateColorAsState(
        targetValue = when (phase) {
            Phase.On -> ClxyColors.CyanGlow
            Phase.Connecting, Phase.Disconnecting -> ClxyColors.AmberWarning
            Phase.Error -> ClxyColors.RedDisconnectStart
            Phase.Off -> Color.Transparent
        },
        label = "glowColor",
    )

    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    Box(
        modifier = Modifier
            .size(240.dp)
            .scale(if (isConnected) pulse else if (pressed) 0.95f else 1f)
            .clickable(
                enabled = phase != Phase.Disconnecting,
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        // Outer glow & halo canvas
        Canvas(modifier = Modifier.fillMaxSize()) {
            val radius = size.minDimension / 2f
            val center = Offset(size.width / 2f, size.height / 2f)

            if (glowColor != Color.Transparent) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(glowColor.copy(alpha = 0.28f), Color.Transparent),
                        center = center,
                        radius = radius * 1.05f,
                    ),
                    radius = radius * 1.05f,
                    center = center,
                )
            }

            if (isConnecting) {
                rotate(spin) {
                    drawArc(
                        color = ClxyColors.AmberWarning,
                        startAngle = -90f,
                        sweepAngle = 120f,
                        useCenter = false,
                        topLeft = Offset(center.x - radius * 0.92f, center.y - radius * 0.92f),
                        size = Size(radius * 1.84f, radius * 1.84f),
                        style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round),
                    )
                }
            } else if (isConnected) {
                drawCircle(
                    color = ClxyColors.CyanGlow.copy(alpha = 0.55f),
                    radius = radius * 0.88f,
                    center = center,
                    style = Stroke(width = 2.dp.toPx()),
                )
            } else {
                drawCircle(
                    color = ClxyColors.GlassBorder,
                    radius = radius * 0.88f,
                    center = center,
                    style = Stroke(width = 1.5.dp.toPx()),
                )
            }
        }

        // Inner Power Sphere
        Box(
            modifier = Modifier
                .size(160.dp)
                .shadow(
                    elevation = if (isConnected) 24.dp else 8.dp,
                    shape = CircleShape,
                    ambientColor = if (isConnected) ClxyColors.CyanGlow else Color.Black,
                    spotColor = if (isConnected) ClxyColors.PurplePrimary else Color.Black,
                )
                .clip(CircleShape)
                .background(coreGradient)
                .border(
                    width = 2.dp,
                    color = if (isConnected) ClxyColors.CyanBright.copy(alpha = 0.85f) else Color(0x3394A3B8),
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (isConnecting) {
                CircularProgressIndicator(
                    color = Color.White,
                    strokeWidth = 3.5.dp,
                    modifier = Modifier.size(54.dp),
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.PowerSettingsNew,
                    contentDescription = stringResource(R.string.cd_power),
                    tint = if (isConnected) Color.White else ClxyColors.CyanGlow.copy(alpha = 0.75f),
                    modifier = Modifier.size(56.dp),
                )
            }
        }
    }
}

@Composable
private fun RouteTunnelBox(
    connected: Boolean,
    originIp: String,
    destCode: String,
    destName: String,
    destIp: String,
    onClick: () -> Unit = {},
) {
    val infinite = rememberInfiniteTransition(label = "routeFlow")
    val packetProgress by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing)),
        label = "packetProgress",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(ClxyColors.CardSurface)
            .border(
                1.dp,
                if (connected) ClxyColors.CyanGlow.copy(alpha = 0.40f) else ClxyColors.GlassBorder,
                RoundedCornerShape(16.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        // Origin node
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f),
        ) {
            CircularCountryFlag(countryCode = "REC", size = 28.dp)
            Column {
                Text(
                    text = "My Device",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = ClxyColors.TextPrimary,
                )
                Text(
                    text = originIp,
                    style = MaterialTheme.typography.labelSmall,
                    color = ClxyColors.TextDim,
                )
            }
        }

        // Center animated tunnel connector
        Box(
            modifier = Modifier
                .width(56.dp)
                .height(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cy = size.height / 2f
                drawLine(
                    color = if (connected) ClxyColors.CyanGlow.copy(alpha = 0.35f) else Color(0x3364748B),
                    start = Offset(0f, cy),
                    end = Offset(size.width, cy),
                    strokeWidth = 2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f),
                )
                if (connected) {
                    val px = size.width * packetProgress
                    drawCircle(
                        color = ClxyColors.CyanBright,
                        radius = 3.dp.toPx(),
                        center = Offset(px, cy),
                    )
                }
            }
        }

        // Destination node
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            modifier = Modifier.weight(1f),
        ) {
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = destName.take(12),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = ClxyColors.TextPrimary,
                )
                Text(
                    text = destIp.take(14),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (connected) ClxyColors.CyanText else ClxyColors.TextDim,
                )
            }
            CircularCountryFlag(countryCode = destCode, size = 28.dp)
        }
    }
}

@Composable
private fun StatusBlock(state: AppState.State, phase: Phase, onOpenLogs: () -> Unit = {}) {
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(state.connectedAt) {
        while (state.connectedAt > 0L) {
            now = SystemClock.elapsedRealtime()
            delay(1_000)
        }
    }

    val statusColor = when (phase) {
        Phase.On -> ClxyColors.EmeraldGood
        Phase.Connecting, Phase.Disconnecting -> ClxyColors.AmberWarning
        Phase.Error -> ClxyColors.RedDisconnectStart
        Phase.Off -> ClxyColors.TextDim
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(ClxyColors.CardSurface)
            .border(1.dp, ClxyColors.GlassBorder, RoundedCornerShape(16.dp))
            .clickable(onClick = onOpenLogs)
            .padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(9.dp)
                    .background(statusColor, CircleShape),
            )
            Text(
                text = when (phase) {
                    Phase.Off -> Texts.ready.uppercase()
                    Phase.Connecting -> Texts.connecting.uppercase()
                    Phase.Disconnecting -> Texts.disconnecting.uppercase()
                    Phase.On -> Texts.connected.uppercase()
                    Phase.Error -> Texts.failed.uppercase()
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp,
                color = statusColor,
            )
            if (phase == Phase.On && state.latencyMs > 0) {
                Spacer(Modifier.width(4.dp))
                SignalStrengthBars(pingMs = state.latencyMs)
                Text(
                    text = "${state.latencyMs}ms",
                    style = MaterialTheme.typography.labelSmall,
                    color = ClxyColors.CyanText,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        Spacer(Modifier.height(6.dp))

        val detail = when {
            phase == Phase.Disconnecting -> Texts.disconnectingHint
            phase == Phase.Error ->
                StatusText.describe(state.statusCode, state.statusDetail) +
                    if (state.diagnosis.isNotBlank()) " · ${state.diagnosis}" else ""
            phase == Phase.Connecting -> Texts.progress(state.coreId, state.statusDetail)
            phase == Phase.On && state.shareLan && state.lan.isNotBlank() ->
                Texts.lanDetail(state.lan, state.lanHttp)
            phase == Phase.On && state.mode == AppState.Mode.PROXY ->
                Texts.proxyDetail(Prefs.socksAddress, Prefs.httpAddress)
            phase == Phase.On -> Texts.coreActiveDetail(Texts.coreName(state.coreId))
            else -> Texts.detailIdle
        }

        Text(
            text = detail,
            style = MaterialTheme.typography.bodySmall,
            color = ClxyColors.TextMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 8.dp),
        )

        if (phase == Phase.On) {
            Spacer(Modifier.height(6.dp))
            val uptime = if (state.connectedAt > 0L) {
                Texts.sessionUptime(
                    Texts.duration((now - state.connectedAt).coerceAtLeast(0L)),
                )
            } else ""
            if (uptime.isNotBlank()) {
                Text(
                    text = uptime,
                    style = MaterialTheme.typography.labelMedium,
                    color = ClxyColors.CyanText,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun AccountChip(onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(50),
        color = ClxyColors.CyanGlow.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, ClxyColors.CyanGlow.copy(alpha = 0.45f)),
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Text(
            text = stringResource(R.string.account_title) + " · " + stringResource(R.string.action_sign_in),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = ClxyColors.CyanBright,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 9.dp),
        )
    }
}

@Composable
private fun UpdateBadge(release: GithubUpdates.Release) {
    val context = LocalContext.current
    Surface(
        shape = RoundedCornerShape(50),
        color = ClxyColors.AmberWarning.copy(alpha = 0.16f),
        border = BorderStroke(1.dp, ClxyColors.AmberWarning.copy(alpha = 0.50f)),
        modifier = Modifier
            .clickable {
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(release.page)))
                } catch (_: ActivityNotFoundException) {
                    Toast.makeText(context, R.string.update_failed, Toast.LENGTH_LONG).show()
                }
            },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Download,
                contentDescription = null,
                tint = ClxyColors.AmberWarning,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.update_available, release.version),
                style = MaterialTheme.typography.labelSmall,
                color = ClxyColors.AmberWarning,
            )
        }
    }
}
