package com.clxypro.vpn.ui

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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Tune
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.clxypro.vpn.BuildConfig
import com.clxypro.vpn.R
import com.clxypro.vpn.Texts
import com.clxypro.vpn.core.AppState
import com.clxypro.vpn.core.GithubUpdates
import com.clxypro.vpn.core.Prefs
import com.clxypro.vpn.core.ServerChoice
import com.clxypro.vpn.core.SstpStore
import com.clxypro.vpn.core.StatusText
import com.clxypro.vpn.core.SunAndLionStore
import com.clxypro.vpn.ui.theme.ClxyColors
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
    onOpenAccounts: () -> Unit = {},
    onOpenSignIn: () -> Unit = onOpenSettings,
    onOpenLogs: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val phase = phaseOf(state)
    val isConnected = phase == Phase.On
    val isConnecting = phase == Phase.Connecting || phase == Phase.Disconnecting
    val isQuantum = state.coreId == "aether"

    var showSstpScanDialog by remember { mutableStateOf(false) }
    var showPrimeScanDialog by remember { mutableStateOf(false) }

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

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ClxyColors.ObsidianBg)
    ) {
        // Subtle ambient top light (Cyan in Hyper, Purple in Quantum)
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 10.dp)
                .size(320.dp, 160.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            (if (isQuantum) ClxyColors.QuantumPurple else ClxyColors.CyanBright).copy(alpha = 0.08f),
                            Color.Transparent,
                        ),
                    ),
                    shape = CircleShape,
                )
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
            // 1. Top Header Bar (Action buttons on left, Brand Title in center, Alpha on right)
            StitchTopBar(
                isQuantum = isQuantum,
                onOpenSettings = onOpenSettings,
                onOpenLogs = onOpenLogs,
            )

            Spacer(Modifier.height(14.dp))

            // 2. Segmented Mode Switcher (CLXY Hyper vs CLXY Quantum)
            StitchModeSwitcher(
                isQuantum = isQuantum,
                enabled = !state.running && !state.busy && !state.disconnecting,
                onSelectHyper = {
                    Prefs.coreId = "firefox"
                    AppState.update { it.copy(coreId = "firefox") }
                },
                onSelectQuantum = {
                    Prefs.coreId = "aether"
                    AppState.update { it.copy(coreId = "aether") }
                },
            )

            Spacer(Modifier.height(14.dp))

            // 3. Server / Route Card (Hyper shows location selector; Quantum shows Anycast MASQUE route)
            if (isQuantum) {
                QuantumRouteCard(
                    latencyMs = if (state.latencyMs > 0) state.latencyMs else 24,
                )
            } else {
                HyperServerCard(
                    coreId = state.coreId,
                    exitPlace = state.exitPlace,
                    exitIp = state.exitIp,
                    latencyMs = if (state.latencyMs > 0) state.latencyMs else 42,
                    enabled = !state.running && !state.busy && !state.disconnecting,
                    onClick = onOpenServers,
                )
            }

            Spacer(Modifier.height(18.dp))

            // 4. Central Connect Hero (Outer Button + Standby View / Glowing Cyber Globe / Quantum Orb)
            StitchConnectHero(
                phase = phase,
                isQuantum = isQuantum,
                onClick = { handleConnect() },
            )

            Spacer(Modifier.height(12.dp))

            // 5. Connection Security Status Badge
            StitchStatusBadge(
                phase = phase,
                isQuantum = isQuantum,
                state = state,
                onClick = onOpenLogs,
            )

            Spacer(Modifier.height(18.dp))

            // 6. Bottom User Card
            // In Hyper: Multi-account rotation & 5GB usage quota progress bar
            // In Quantum: Unified feature card (Unlimited Traffic ∞ & Zero Login)
            if (isQuantum) {
                QuantumFeatureCard()
            } else {
                HyperQuotaCard(
                    onOpenAccounts = onOpenAccounts,
                    onOpenSignIn = onOpenSignIn,
                )
            }

            Spacer(Modifier.height(10.dp))

            // Version stamp
            Text(
                text = stringResource(R.string.about_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
                style = MaterialTheme.typography.labelSmall,
                color = ClxyColors.TextDim.copy(alpha = 0.5f),
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 72.dp),
            )
        }

        // Fast Scan / Optimization Dialogs if triggered
        if (showSstpScanDialog) {
            Dialog(onDismissRequest = { showSstpScanDialog = false }) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = ClxyColors.ObsidianCard,
                    border = BorderStroke(1.dp, ClxyColors.CyanBright.copy(alpha = 0.5f)),
                    modifier = Modifier.padding(16.dp),
                ) {
                    Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Rounded.Bolt, contentDescription = null, tint = ClxyColors.CyanBright, modifier = Modifier.size(40.dp))
                        Spacer(Modifier.height(10.dp))
                        Text("بهینه‌سازی سرورها", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "سرورهای فعال برای اتصال سریع کش می‌شوند تا در دفعات بعدی اتصال آنی باشد.",
                            fontSize = 12.sp,
                            color = ClxyColors.TextSecondary,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = {
                                showSstpScanDialog = false
                                onToggle()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = ClxyColors.CyanBright),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().height(46.dp),
                        ) {
                            Text("اسکن و اتصال", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        if (showPrimeScanDialog) {
            Dialog(onDismissRequest = { showPrimeScanDialog = false }) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = ClxyColors.ObsidianCard,
                    border = BorderStroke(1.dp, ClxyColors.CyanBright.copy(alpha = 0.5f)),
                    modifier = Modifier.padding(16.dp),
                ) {
                    Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Rounded.Speed, contentDescription = null, tint = ClxyColors.CyanBright, modifier = Modifier.size(40.dp))
                        Spacer(Modifier.height(10.dp))
                        Text("بهینه‌سازی مسیرهای لبه", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "مسیرهای CDN فرانتینگ برای دسترسی سریع‌تر کش می‌شوند.",
                            fontSize = 12.sp,
                            color = ClxyColors.TextSecondary,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = {
                                showPrimeScanDialog = false
                                onToggle()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = ClxyColors.CyanBright),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().height(46.dp),
                        ) {
                            Text("بهینه‌سازی و اتصال", color = Color.Black, fontWeight = FontWeight.Bold)
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

// -------------------------------------------------------------
// 1. TOP BAR
// -------------------------------------------------------------
@Composable
private fun StitchTopBar(
    isQuantum: Boolean,
    onOpenSettings: () -> Unit,
    onOpenLogs: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        // Left: Settings & Log Action Buttons
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF12141D))
                    .border(1.dp, Color(0x15FFFFFF), RoundedCornerShape(12.dp)),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Tune,
                    contentDescription = "تنظیمات",
                    tint = ClxyColors.TextDim,
                    modifier = Modifier.size(18.dp),
                )
            }
            IconButton(
                onClick = onOpenLogs,
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF12141D))
                    .border(1.dp, Color(0x15FFFFFF), RoundedCornerShape(12.dp)),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Terminal,
                    contentDescription = "لاگ سیستم",
                    tint = ClxyColors.TextDim,
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        // Center: Brand Title
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = "CLXY",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = (-0.5).sp,
                    color = Color.White,
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(5.dp))
                        .background(
                            if (isQuantum) ClxyColors.QuantumPurple.copy(alpha = 0.15f)
                            else ClxyColors.CyanBright.copy(alpha = 0.15f),
                        )
                        .border(
                            0.8.dp,
                            if (isQuantum) ClxyColors.QuantumPurple.copy(alpha = 0.35f)
                            else ClxyColors.CyanBright.copy(alpha = 0.35f),
                            RoundedCornerShape(5.dp),
                        )
                        .padding(horizontal = 6.dp, vertical = 1.dp),
                ) {
                    Text(
                        text = "Pro",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = if (isQuantum) ClxyColors.QuantumViolet else ClxyColors.CyanBright,
                    )
                }
            }
            Text(
                text = if (isQuantum) "QUANTUM AETHER" else "HYPER MULTI-CLOUD",
                fontSize = 9.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.6.sp,
                color = ClxyColors.TextDim,
                modifier = Modifier.padding(top = 1.dp),
            )
        }

        // Right: Alpha Badge with online dot
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    if (isQuantum) ClxyColors.QuantumPurple.copy(alpha = 0.14f)
                    else Color(0xFF12141D),
                )
                .border(
                    1.dp,
                    if (isQuantum) ClxyColors.QuantumPurple.copy(alpha = 0.28f)
                    else Color(0x15FFFFFF),
                    RoundedCornerShape(12.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "α",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = if (isQuantum) ClxyColors.QuantumViolet else Color(0xFFE2E8F0),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .size(5.dp)
                    .background(ClxyColors.EmeraldPing, CircleShape),
            )
        }
    }
}

// -------------------------------------------------------------
// 2. SEGMENTED MODE SWITCHER
// -------------------------------------------------------------
@Composable
private fun StitchModeSwitcher(
    isQuantum: Boolean,
    enabled: Boolean,
    onSelectHyper: () -> Unit,
    onSelectQuantum: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF0F1118),
        border = BorderStroke(1.dp, Color(0x12FFFFFF)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // Option 1: CLXY Hyper
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (!isQuantum) Color(0xFF181C28) else Color.Transparent)
                    .border(
                        1.dp,
                        if (!isQuantum) Color(0x22FFFFFF) else Color.Transparent,
                        RoundedCornerShape(12.dp),
                    )
                    .clickable(enabled = enabled && isQuantum) { onSelectHyper() }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(
                                if (!isQuantum) ClxyColors.CyanBright else ClxyColors.TextDim.copy(alpha = 0.4f),
                                CircleShape,
                            ),
                    )
                    Text(
                        text = "CLXY Hyper",
                        fontSize = 12.sp,
                        fontWeight = if (!isQuantum) FontWeight.Bold else FontWeight.Normal,
                        fontFamily = FontFamily.Monospace,
                        color = if (!isQuantum) Color.White else ClxyColors.TextDim,
                    )
                }
            }

            // Option 2: CLXY Quantum
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (isQuantum) ClxyColors.QuantumPurple.copy(alpha = 0.20f)
                        else Color.Transparent,
                    )
                    .border(
                        1.dp,
                        if (isQuantum) ClxyColors.QuantumPurple.copy(alpha = 0.35f)
                        else Color.Transparent,
                        RoundedCornerShape(12.dp),
                    )
                    .clickable(enabled = enabled && !isQuantum) { onSelectQuantum() }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(
                                if (isQuantum) ClxyColors.QuantumViolet else ClxyColors.TextDim.copy(alpha = 0.4f),
                                CircleShape,
                            ),
                    )
                    Text(
                        text = if (isQuantum) "CLXY Quantum (اتر)" else "CLXY Quantum",
                        fontSize = 12.sp,
                        fontWeight = if (isQuantum) FontWeight.Bold else FontWeight.Normal,
                        color = if (isQuantum) Color(0xFFE9D5FF) else ClxyColors.TextDim,
                    )
                }
            }
        }
    }
}

// -------------------------------------------------------------
// 3. SERVER / ROUTE CARDS
// -------------------------------------------------------------
@Composable
private fun HyperServerCard(
    coreId: String,
    exitPlace: String,
    exitIp: String,
    latencyMs: Int,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val serverLabel = Prefs.serverLabel(coreId).ifBlank {
        if (exitPlace.isNotBlank()) exitPlace else "فرانکفورت، آلمان"
    }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = ClxyColors.ObsidianCard,
        border = BorderStroke(1.dp, ClxyColors.ObsidianBorder),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f),
            ) {
                // ISO / Flag Badge
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(ClxyColors.SubcardSurface)
                        .border(1.dp, Color(0x14FFFFFF), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "DE",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = Color.White,
                    )
                }

                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = serverLabel,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0x18FFFFFF))
                                .border(0.6.dp, Color(0x20FFFFFF), RoundedCornerShape(4.dp))
                                .padding(horizontal = 5.dp, vertical = 1.dp),
                        ) {
                            Text(
                                text = "VLESS",
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = ClxyColors.TextDim,
                            )
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = "سریع‌ترین گره پیشنهادی",
                            fontSize = 11.sp,
                            color = ClxyColors.TextDim,
                        )
                        Icon(
                            imageVector = Icons.Rounded.KeyboardArrowDown,
                            contentDescription = null,
                            tint = ClxyColors.TextDim,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }

            // Ping Badge
            Column(horizontalAlignment = Alignment.End) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(ClxyColors.EmeraldPing, CircleShape),
                    )
                    Text(
                        text = "$latencyMs",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = Color.White,
                    )
                    Text(
                        text = "ms",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = ClxyColors.TextDim,
                    )
                }
                Text(
                    text = "پینگ مستقیم",
                    fontSize = 10.sp,
                    color = ClxyColors.TextDim,
                )
            }
        }
    }
}

@Composable
private fun QuantumRouteCard(latencyMs: Int) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFF11131A),
        border = BorderStroke(1.dp, ClxyColors.ObsidianBorder),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f),
            ) {
                // Purple Lightning Icon Box
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(ClxyColors.QuantumPurple.copy(alpha = 0.15f))
                        .border(1.dp, ClxyColors.QuantumPurple.copy(alpha = 0.25f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Bolt,
                        contentDescription = null,
                        tint = ClxyColors.QuantumViolet,
                        modifier = Modifier.size(22.dp),
                    )
                }

                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = "مسیر Anycast کوانتومی",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFF2E1065))
                                .border(0.6.dp, ClxyColors.QuantumPurple.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 5.dp, vertical = 1.dp),
                        ) {
                            Text(
                                text = "MASQUE",
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFFD8B4FE),
                            )
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "اتصال خودکار به کم‌ترافیک‌ترین لبه",
                        fontSize = 11.sp,
                        color = ClxyColors.TextDim,
                    )
                }
            }

            // Right side: QUIC / H3 + ping
            Column(horizontalAlignment = Alignment.End) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0x18FFFFFF))
                        .border(0.6.dp, Color(0x1AFFFFFF), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 1.dp),
                ) {
                    Text(
                        text = "QUIC / H3",
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        color = ClxyColors.TextDim,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(ClxyColors.EmeraldPing, CircleShape),
                    )
                    Text(
                        text = "$latencyMs ms",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = ClxyColors.EmeraldPing,
                    )
                }
            }
        }
    }
}

// -------------------------------------------------------------
// 4. CENTRAL CONNECT HERO BUTTON
// -------------------------------------------------------------
@Composable
private fun StitchConnectHero(
    phase: Phase,
    isQuantum: Boolean,
    onClick: () -> Unit,
) {
    val isConnected = phase == Phase.On
    val isConnecting = phase == Phase.Connecting || phase == Phase.Disconnecting

    val transition = rememberInfiniteTransition(label = "heroPulse")
    val pulseHalo by transition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            tween(2200, easing = FastOutSlowInEasing),
            RepeatMode.Reverse,
        ),
        label = "pulseHalo",
    )
    val spinArc by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            tween(2000, easing = LinearEasing),
        ),
        label = "spinArc",
    )
    val beamOffset by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(1800, easing = LinearEasing),
        ),
        label = "beamOffset",
    )

    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    val accentColor = if (isQuantum) ClxyColors.QuantumPurple else ClxyColors.CyanBright

    Box(
        modifier = Modifier
            .size(196.dp)
            .scale(if (pressed) 0.96f else 1f),
        contentAlignment = Alignment.Center,
    ) {
        // Atmospheric Halo (Glowing when connected or connecting)
        if (isConnected || isConnecting) {
            Box(
                modifier = Modifier
                    .size(200.dp)
                    .scale(pulseHalo)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                accentColor.copy(alpha = if (isConnected) 0.22f else 0.14f),
                                Color.Transparent,
                            ),
                        ),
                        shape = CircleShape,
                    )
            )
        }

        // Outer Clickable Button Frame (176dp x 176dp)
        Box(
            modifier = Modifier
                .size(174.dp)
                .clip(CircleShape)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFF131722), Color(0xFF0B0D14)),
                    ),
                )
                .border(
                    width = 1.5.dp,
                    color = if (isConnected) accentColor.copy(alpha = 0.45f) else Color(0x1AFFFFFF),
                    shape = CircleShape,
                )
                .clickable(
                    enabled = phase != Phase.Disconnecting,
                    interactionSource = interaction,
                    indication = null,
                    onClick = onClick,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (isConnected) {
                // CONNECTED VIEW:
                if (isQuantum) {
                    // Quantum Warp Orb View
                    QuantumConnectedOrb(beamOffset = beamOffset)
                } else {
                    // Glowing Cyber Globe with flight arch from Tehran to Frankfurt!
                    CyberGlobeView(beamOffset = beamOffset)
                }
            } else if (isConnecting) {
                // CONNECTING VIEW: Smooth rotating cyber arc
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val radius = size.minDimension / 2f
                    val center = Offset(size.width / 2f, size.height / 2f)
                    rotate(spinArc) {
                        drawArc(
                            color = accentColor,
                            startAngle = -90f,
                            sweepAngle = 140f,
                            useCenter = false,
                            topLeft = Offset(center.x - radius * 0.84f, center.y - radius * 0.84f),
                            size = Size(radius * 1.68f, radius * 1.68f),
                            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
                        )
                    }
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator(
                        color = accentColor,
                        strokeWidth = 2.5.dp,
                        modifier = Modifier.size(36.dp),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "در حال اتصال...",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                }
            } else {
                // STANDBY VIEW: Minimalist Power Icon & Text
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(Color(0x0AFFFFFF)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PowerSettingsNew,
                            contentDescription = "اتصال",
                            tint = accentColor,
                            modifier = Modifier.size(30.dp),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "اتصال",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFF1F5F9),
                    )
                    Text(
                        text = "CONNECT",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.4.sp,
                        color = ClxyColors.TextDim,
                    )
                }
            }
        }
    }
}

// -------------------------------------------------------------
// CYBER GLOBE COMPOSABLE (Hyper connected state)
// -------------------------------------------------------------
@Composable
private fun CyberGlobeView(beamOffset: Float) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val r = size.minDimension / 2f - 4.dp.toPx()

            // 1. Globe Base Sphere
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF192336), Color(0xFF0E121C), Color(0xFF07090F)),
                    center = Offset(center.x - r * 0.15f, center.y - r * 0.25f),
                    radius = r * 1.2f,
                ),
                radius = r,
                center = center,
            )
            drawCircle(
                color = Color(0x5500F0FF),
                radius = r,
                center = center,
                style = Stroke(width = 1.2.dp.toPx()),
            )

            // 2. Latitude and Longitude Grid Lines
            // Equator dashed
            drawLine(
                color = Color(0x3000F0FF),
                start = Offset(center.x - r, center.y),
                end = Offset(center.x + r, center.y),
                strokeWidth = 0.8.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f), 0f),
            )
            // Latitude upper & lower arcs
            drawOval(
                color = Color(0x12FFFFFF),
                topLeft = Offset(center.x - r, center.y - r * 0.45f),
                size = Size(r * 2f, r * 0.9f),
                style = Stroke(width = 0.7.dp.toPx()),
            )
            // Longitude center meridian
            drawOval(
                color = Color(0x12FFFFFF),
                topLeft = Offset(center.x - r * 0.45f, center.y - r),
                size = Size(r * 0.9f, r * 2f),
                style = Stroke(width = 0.7.dp.toPx()),
            )

            // 3. Stylized Continents (Europe / Middle East outline)
            val continentPath = Path().apply {
                moveTo(center.x - r * 0.45f, center.y - r * 0.35f)
                quadraticBezierTo(center.x - r * 0.2f, center.y - r * 0.5f, center.x + r * 0.1f, center.y - r * 0.4f)
                quadraticBezierTo(center.x + r * 0.45f, center.y - r * 0.25f, center.x + r * 0.35f, center.y + r * 0.05f)
                quadraticBezierTo(center.x + r * 0.1f, center.y + r * 0.25f, center.x - r * 0.15f, center.y + r * 0.15f)
                close()
            }
            drawPath(
                path = continentPath,
                color = Color(0x401A2538),
            )
            drawPath(
                path = continentPath,
                color = Color(0x2800F0FF),
                style = Stroke(width = 0.6.dp.toPx()),
            )

            // 4. Flight Path: Origin (Tehran: x=center.x + r*0.35, y=center.y + r*0.05)
            //               Destination (Frankfurt: x=center.x - r*0.35, y=center.y - r*0.35)
            val tehran = Offset(center.x + r * 0.32f, center.y + r * 0.06f)
            val frankfurt = Offset(center.x - r * 0.30f, center.y - r * 0.34f)
            val control = Offset(center.x - r * 0.05f, center.y - r * 0.65f)

            val archPath = Path().apply {
                moveTo(tehran.x, tehran.y)
                quadraticBezierTo(control.x, control.y, frankfurt.x, frankfurt.y)
            }
            // Guide dashed arch
            drawPath(
                path = archPath,
                color = Color(0x3500F0FF),
                style = Stroke(width = 1.4.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f), 0f)),
            )

            // Animated Beam Pulse
            val t = beamOffset
            // Quadratic Bezier interpolation: B(t) = (1-t)^2 P0 + 2(1-t)t P1 + t^2 P2
            val bx = (1 - t) * (1 - t) * tehran.x + 2 * (1 - t) * t * control.x + t * t * frankfurt.x
            val by = (1 - t) * (1 - t) * tehran.y + 2 * (1 - t) * t * control.y + t * t * frankfurt.y
            drawCircle(
                color = ClxyColors.CyanBright,
                radius = 3.dp.toPx(),
                center = Offset(bx, by),
            )

            // Nodes
            // Origin node (Tehran)
            drawCircle(color = Color(0x4000F0FF), radius = 6.dp.toPx(), center = tehran)
            drawCircle(color = Color(0xFF090A0F), radius = 3.dp.toPx(), center = tehran)
            drawCircle(color = ClxyColors.CyanBright, radius = 1.5.dp.toPx(), center = tehran)

            // Destination node (Frankfurt)
            drawCircle(color = Color(0x4010B981), radius = 7.dp.toPx(), center = frankfurt)
            drawCircle(color = Color(0xFF090A0F), radius = 3.5.dp.toPx(), center = frankfurt)
            drawCircle(color = ClxyColors.EmeraldPing, radius = 1.8.dp.toPx(), center = frankfurt)
        }

        // Overlay Telemetry Pill
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 12.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xE6090C14))
                .border(0.8.dp, Color(0x4000F0FF), RoundedCornerShape(20.dp))
                .padding(horizontal = 8.dp, vertical = 3.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Box(modifier = Modifier.size(5.dp).background(ClxyColors.EmeraldPing, CircleShape))
                Text(
                    text = "DE ⇄ IR",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = ClxyColors.CyanBright,
                )
                Text(
                    text = "· AES-256",
                    fontSize = 8.sp,
                    fontFamily = FontFamily.Monospace,
                    color = ClxyColors.TextDim,
                )
            }
        }

        // Quick Disconnect Prompt
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 10.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0x99000000))
                .padding(horizontal = 7.dp, vertical = 2.dp),
        ) {
            Text(
                text = "لمس جهت قطع",
                fontSize = 8.sp,
                color = ClxyColors.TextDim,
            )
        }
    }
}

// -------------------------------------------------------------
// QUANTUM CONNECTED ORB COMPOSABLE
// -------------------------------------------------------------
@Composable
private fun QuantumConnectedOrb(beamOffset: Float) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val r = size.minDimension / 2f - 4.dp.toPx()

            // Quantum Sphere
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF2E1065), Color(0xFF1E103A), Color(0xFF090A0F)),
                    center = center,
                    radius = r,
                ),
                radius = r,
                center = center,
            )
            drawCircle(
                color = ClxyColors.QuantumPurple.copy(alpha = 0.5f),
                radius = r,
                center = center,
                style = Stroke(width = 1.2.dp.toPx()),
            )

            // Dynamic warp rings
            drawCircle(
                color = ClxyColors.QuantumViolet.copy(alpha = 0.25f),
                radius = r * (0.4f + 0.4f * beamOffset),
                center = center,
                style = Stroke(width = 1.dp.toPx()),
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "∞",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = ClxyColors.QuantumViolet,
            )
        }

        // Telemetry Pill
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 12.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xE6090C14))
                .border(0.8.dp, ClxyColors.QuantumPurple.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                .padding(horizontal = 8.dp, vertical = 3.dp),
        ) {
            Text(
                text = "MASQUE QUIC · ZERO-RTT",
                fontSize = 8.5.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = Color(0xFFE9D5FF),
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 10.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0x99000000))
                .padding(horizontal = 7.dp, vertical = 2.dp),
        ) {
            Text(
                text = "لمس جهت قطع",
                fontSize = 8.sp,
                color = ClxyColors.TextDim,
            )
        }
    }
}

// -------------------------------------------------------------
// 5. STATUS BADGE
// -------------------------------------------------------------
@Composable
private fun StitchStatusBadge(
    phase: Phase,
    isQuantum: Boolean,
    state: AppState.State,
    onClick: () -> Unit,
) {
    val dotColor = when (phase) {
        Phase.On -> ClxyColors.EmeraldPing
        Phase.Connecting, Phase.Disconnecting -> ClxyColors.AmberWarning
        Phase.Error -> ClxyColors.RedDisconnectStart
        Phase.Off -> if (isQuantum) ClxyColors.QuantumPurple else ClxyColors.CyanBright
    }

    val statusText = when (phase) {
        Phase.On -> if (isQuantum) "متصل به Anycast کوانتوم • MASQUE" else "متصل · رمزنگاری پیشرفته فعال"
        Phase.Connecting -> if (isQuantum) "در حال اتصال به مسیر Anycast..." else "در حال برقراری تونل امن..."
        Phase.Disconnecting -> "در حال قطع اتصال..."
        Phase.Error -> "خطا در برقراری اتصال · لمس برای لاگ"
        Phase.Off -> if (isQuantum) "آماده اتصال کوانتومی • Ether Warp" else "آماده اتصال · تونل محافظت‌شده"
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = ClxyColors.ObsidianCard,
        border = BorderStroke(1.dp, ClxyColors.ObsidianBorder),
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(dotColor, CircleShape),
            )
            Text(
                text = statusText,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = if (phase == Phase.On) Color.White else ClxyColors.TextDim,
            )
        }
    }
}

// -------------------------------------------------------------
// 6. BOTTOM CARDS (Hyper Quota vs Quantum Feature)
// -------------------------------------------------------------
@Composable
private fun HyperQuotaCard(
    onOpenAccounts: () -> Unit,
    onOpenSignIn: () -> Unit,
) {
    val context = LocalContext.current
    val active = Store.activeAccount(context)
    val accounts = Store.accounts(context)
    val (fraction, usageText) = Store.activeAccountQuotaUsage(context)

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = ClxyColors.ObsidianCard,
        border = BorderStroke(1.dp, ClxyColors.ObsidianBorder),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenAccounts),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(ClxyColors.CyanBright, CircleShape),
                    )
                    Text(
                        text = "حساب فعال:",
                        fontSize = 12.sp,
                        color = ClxyColors.TextDim,
                    )
                    Text(
                        text = active?.email?.ifBlank { "user_71@clxy" } ?: "user_71@clxy",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = Color.White,
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0x18FFFFFF))
                        .border(0.6.dp, Color(0x20FFFFFF), RoundedCornerShape(6.dp))
                        .padding(horizontal = 7.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = if (accounts.isNotEmpty()) "${accounts.size} حساب متصل" else "چرخش خودکار فعال",
                        fontSize = 10.sp,
                        color = ClxyColors.TextDim,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Progress Bar (6dp height)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color(0xFF1E293B)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(ClxyColors.CyanBright),
                )
            }

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = usageText,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = Color.White,
                )
                Text(
                    text = "${(fraction * 100).toInt()}% مصرف شده",
                    fontSize = 10.5.sp,
                    color = ClxyColors.TextDim,
                )
            }
        }
    }
}

@Composable
private fun QuantumFeatureCard() {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFF11131A),
        border = BorderStroke(1.dp, ClxyColors.ObsidianBorder),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Row 1: Unlimited
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "∞",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = ClxyColors.QuantumViolet,
                    )
                    Text(
                        text = "ترافیک نامحدود کوانتوم (Unlimited)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White,
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(ClxyColors.QuantumPurple.copy(alpha = 0.15f))
                        .border(0.8.dp, ClxyColors.QuantumPurple.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = "نامحدود",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFD8B4FE),
                    )
                }
            }

            // Subtle Divider
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(0.6.dp)
                    .background(Color(0x14FFFFFF)),
            )

            // Row 2: Zero Login
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        tint = ClxyColors.EmeraldPing,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = "بدون نیاز به ثبت نام و ورود اکانت",
                        fontSize = 12.sp,
                        color = ClxyColors.TextSecondary,
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFF064E3B))
                        .border(0.6.dp, ClxyColors.EmeraldPing.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 1.dp),
                ) {
                    Text(
                        text = "ZERO LOGIN",
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = ClxyColors.EmeraldPing,
                    )
                }
            }
        }
    }
}
