package com.clxypro.vpn.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clxypro.vpn.R
import com.clxypro.vpn.core.PsiphonExit
import com.clxypro.vpn.ui.theme.ClxyColors
import com.clxypro.vpn.ui.theme.InterFontFamily
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

val ClxyBrandGradient = Brush.linearGradient(
    colors = listOf(ClxyColors.CyanBright, Color(0xFF60A5FA), ClxyColors.PinkAccent),
)

val ClxyPrimaryButtonBrush = Brush.linearGradient(
    colors = listOf(ClxyColors.CyanGlow, ClxyColors.BluePrimary, ClxyColors.PurplePrimary),
)

val ClxyDisconnectBrush = Brush.linearGradient(
    colors = listOf(ClxyColors.RedDisconnectStart, ClxyColors.RedDisconnectEnd),
)

@Composable
fun ClxyCosmicBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(ClxyColors.BgTop, ClxyColors.BgMid, ClxyColors.BgBottom),
                ),
            ),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(ClxyColors.CyanGlow.copy(alpha = 0.12f), Color.Transparent),
                    center = Offset(size.width * 0.18f, size.height * 0.20f),
                    radius = size.width * 0.65f,
                ),
                center = Offset(size.width * 0.18f, size.height * 0.20f),
                radius = size.width * 0.65f,
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(ClxyColors.PurplePrimary.copy(alpha = 0.15f), Color.Transparent),
                    center = Offset(size.width * 0.84f, size.height * 0.26f),
                    radius = size.width * 0.68f,
                ),
                center = Offset(size.width * 0.84f, size.height * 0.26f),
                radius = size.width * 0.68f,
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(ClxyColors.BluePrimary.copy(alpha = 0.10f), Color.Transparent),
                    center = Offset(size.width * 0.50f, size.height * 0.86f),
                    radius = size.width * 0.72f,
                ),
                center = Offset(size.width * 0.50f, size.height * 0.86f),
                radius = size.width * 0.72f,
            )
        }
        content()
    }
}

@Composable
fun ClxyBrandHeader(
    modifier: Modifier = Modifier,
    logoSize: Dp = 32.dp,
    showLabel: Boolean = true,
    labelSizeSp: Float = 17f,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Image(
            painter = painterResource(id = R.drawable.clxy_logo),
            contentDescription = "ClxyPro logo",
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(width = logoSize, height = logoSize * 0.85f),
        )
        if (showLabel) {
            Text(
                text = "ClxyPro",
                style = TextStyle(
                    fontFamily = InterFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = labelSizeSp.sp,
                    letterSpacing = 0.8.sp,
                    brush = ClxyBrandGradient,
                ),
            )
        }
    }
}

@Composable
fun DottedWorldMap(
    modifier: Modifier = Modifier,
    dotAlpha: Float = 0.38f,
    animated: Boolean = true,
) {
    val wavePhase = if (animated) {
        val infinite = rememberInfiniteTransition(label = "worldMapWave")
        val phase by infinite.animateFloat(
            initialValue = 0f,
            targetValue = (2f * PI).toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 4800, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "wavePhase",
        )
        phase
    } else {
        0f
    }

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val step = (w / 58f).coerceIn(4.5.dp.toPx(), 7.dp.toPx())
        val radius = step * 0.24f

        fun ellipse(nx: Float, ny: Float, cx: Float, cy: Float, rx: Float, ry: Float): Boolean {
            val dx = (nx - cx) / rx
            val dy = (ny - cy) / ry
            return dx * dx + dy * dy <= 1f
        }

        fun insideContinent(nx: Float, ny: Float): Boolean {
            val alaska = ellipse(nx, ny, 0.09f, 0.21f, 0.055f, 0.055f)
            val canadaUs = ellipse(nx, ny, 0.20f, 0.28f, 0.115f, 0.125f)
            val mexico = ellipse(nx, ny, 0.21f, 0.43f, 0.045f, 0.065f)
            val greenland = ellipse(nx, ny, 0.34f, 0.13f, 0.048f, 0.055f)
            val southAmNorth = ellipse(nx, ny, 0.275f, 0.59f, 0.065f, 0.095f)
            val southAmSouth = ellipse(nx, ny, 0.265f, 0.74f, 0.036f, 0.120f)
            val uk = ellipse(nx, ny, 0.435f, 0.25f, 0.018f, 0.030f)
            val europe = ellipse(nx, ny, 0.50f, 0.27f, 0.065f, 0.075f)
            val scandi = ellipse(nx, ny, 0.515f, 0.175f, 0.040f, 0.048f)
            val africaNorth = ellipse(nx, ny, 0.505f, 0.465f, 0.078f, 0.095f)
            val africaSouth = ellipse(nx, ny, 0.530f, 0.625f, 0.048f, 0.125f)
            val madagascar = ellipse(nx, ny, 0.598f, 0.665f, 0.014f, 0.045f)
            val middleEast = ellipse(nx, ny, 0.595f, 0.395f, 0.045f, 0.058f)
            val siberiaWest = ellipse(nx, ny, 0.66f, 0.215f, 0.115f, 0.095f)
            val eastAsia = ellipse(nx, ny, 0.76f, 0.32f, 0.115f, 0.120f)
            val india = ellipse(nx, ny, 0.695f, 0.455f, 0.040f, 0.075f)
            val indochina = ellipse(nx, ny, 0.775f, 0.475f, 0.035f, 0.065f)
            val japan = ellipse(nx, ny, 0.895f, 0.305f, 0.018f, 0.055f)
            val indonesia = ellipse(nx, ny, 0.815f, 0.575f, 0.065f, 0.025f)
            val australia = ellipse(nx, ny, 0.845f, 0.725f, 0.065f, 0.088f)
            val newZealand = ellipse(nx, ny, 0.942f, 0.810f, 0.015f, 0.042f)

            return alaska || canadaUs || mexico || greenland ||
                southAmNorth || southAmSouth ||
                uk || europe || scandi ||
                africaNorth || africaSouth || madagascar ||
                middleEast || siberiaWest || eastAsia || india || indochina ||
                japan || indonesia || australia || newZealand
        }

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    ClxyColors.CyanGlow.copy(alpha = dotAlpha * 0.24f),
                    ClxyColors.BluePrimary.copy(alpha = dotAlpha * 0.12f),
                    Color.Transparent,
                ),
                center = Offset(w * 0.5f, h * 0.48f),
                radius = w * 0.46f,
            ),
            center = Offset(w * 0.5f, h * 0.48f),
            radius = w * 0.46f,
        )

        var y = step * 0.5f
        while (y < h) {
            var x = step * 0.5f
            while (x < w) {
                val nx = x / w
                val ny = y / h
                if (insideContinent(nx, ny)) {
                    val shimmer = if (animated) {
                        0.82f + 0.18f * sin(wavePhase + nx * 7.5f + ny * 4.5f)
                    } else {
                        1f
                    }
                    val a = (dotAlpha * shimmer).coerceIn(0.12f, 0.95f)
                    val color = when {
                        nx < 0.36f -> ClxyColors.CyanGlow.copy(alpha = a)
                        nx < 0.68f -> Color(0xFF60A5FA).copy(alpha = a)
                        else -> Color(0xFFC084FC).copy(alpha = (a + 0.06f).coerceAtMost(1f))
                    }
                    drawCircle(color = color, radius = radius, center = Offset(x, y))
                }
                x += step
            }
            y += step
        }
    }
}

@Composable
fun CircularCountryFlag(
    countryCode: String,
    modifier: Modifier = Modifier,
    size: Dp = 34.dp,
) {
    val code = countryCode.trim().uppercase(Locale.US)
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = this.size.width
            val h = this.size.height
            val circle = Path().apply { addOval(androidx.compose.ui.geometry.Rect(0f, 0f, w, h)) }
            clipPath(circle) {
                when (code) {
                    "", "REC", "AUTO" -> {
                        drawRect(
                            brush = Brush.linearGradient(
                                listOf(Color(0xFF00E5FF), Color(0xFF3B82F6), Color(0xFF9333EA)),
                            ),
                        )
                        drawCircle(
                            color = Color.White.copy(alpha = 0.3f),
                            radius = w * 0.32f,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = w * 0.06f),
                        )
                        drawCircle(color = Color.White, radius = w * 0.12f)
                    }
                    "CLXY" -> {
                        // Brand badge (Quantum/Aether destination), not a country.
                        drawRect(brush = Brush.linearGradient(listOf(Color(0xFF00E5FF), Color(0xFF7C3AED), Color(0xFF9333EA))))
                    }
                    else -> {
                        // Two-letter country codes render as an emoji flag
                        // (Text below); nothing to draw for them here.
                        if (code.length != 2 || !code.all { it.isLetter() }) {
                            val h1 = (code.hashCode() and 0x7FFFFFFF) % 360
                            val c1 = Color.hsl(h1.toFloat(), 0.65f, 0.42f)
                            val c2 = Color.hsl(((h1 + 48) % 360).toFloat(), 0.72f, 0.30f)
                            drawRect(brush = Brush.linearGradient(listOf(c1, c2)))
                        }
                    }
                }
            }
        }
        // Every two-letter country code gets its real flag emoji (the
        // region chips already render these fine); the hand-drawn set only
        // covered 14 countries, so BE/AT/AU/... fell back to a coloured
        // badge with the code on it.
        if (code.length == 2 && code.all { it.isLetter() }) {
            Text(
                text = PsiphonExit.flag(code),
                fontSize = (size.value * 0.72f).sp,
            )
        } else if (code == "CLXY" || code !in setOf("", "REC", "AUTO")) {
            Text(
                text = if (code == "CLXY") "CX" else code.take(2),
                color = Color.White,
                fontSize = (size.value * 0.34f).sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
fun SignalStrengthBars(
    pingMs: Int,
    modifier: Modifier = Modifier,
) {
    val activeBars = when {
        pingMs <= 0 -> 3
        pingMs < 120 -> 4
        pingMs < 240 -> 3
        pingMs < 450 -> 2
        else -> 1
    }
    val activeColor = when {
        pingMs <= 0 || pingMs < 400 -> ClxyColors.CyanGlow
        pingMs < 1000 -> ClxyColors.AmberWarning
        else -> ClxyColors.RedDisconnectStart
    }
    Canvas(modifier = modifier.size(width = 16.dp, height = 11.dp)) {
        val barW = size.width / 5.5f
        val gap = (size.width - barW * 4) / 3f
        val heights = listOf(0.30f, 0.50f, 0.75f, 1.0f)
        heights.forEachIndexed { index, frac ->
            val h = size.height * frac
            val x = index * (barW + gap)
            val y = size.height - h
            drawRoundRect(
                color = if (index < activeBars) activeColor else ClxyColors.BarInactive,
                topLeft = Offset(x, y),
                size = Size(barW, h),
                cornerRadius = CornerRadius(1.dp.toPx(), 1.dp.toPx()),
            )
        }
    }
}

@Composable
fun ClxyToastBanner(
    message: String?,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = message != null,
        enter = fadeIn() + slideInVertically { it / 2 },
        exit = fadeOut() + slideOutVertically { it / 2 },
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier
                .shadow(16.dp, RoundedCornerShape(999.dp))
                .background(Color(0xF20F172E), RoundedCornerShape(999.dp))
                .border(1.dp, ClxyColors.CyanGlow.copy(alpha = 0.35f), RoundedCornerShape(999.dp))
                .padding(horizontal = 18.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(ClxyColors.CyanGlow, CircleShape),
            )
            Text(
                text = message.orEmpty(),
                color = ClxyColors.TextPrimary,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
fun ClxyBottomNavBar(
    activeTab: MainTab,
    onSelectTab: (MainTab) -> Unit,
    isQuantum: Boolean = false,
    onOpenLogs: () -> Unit = {},
    logsOpen: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val activeTint = if (isQuantum) ClxyColors.QuantumPurple else ClxyColors.CyanBright

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = ClxyColors.ObsidianCard.copy(alpha = 0.96f),
            border = BorderStroke(1.dp, ClxyColors.ObsidianBorder),
            shadowElevation = 14.dp,
            modifier = Modifier
                .fillMaxWidth()
                .height(62.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Tab 1: Home
                BottomNavItem(
                    selected = activeTab == MainTab.HOME,
                    label = "Home",
                    activeColor = activeTint,
                    tag = "nav_home_button",
                    icon = { selected, tint ->
                        Icon(
                            imageVector = if (selected) Icons.Filled.Home else Icons.Outlined.Home,
                            contentDescription = "Home",
                            tint = tint,
                            modifier = Modifier.size(20.dp),
                        )
                    },
                    onClick = { onSelectTab(MainTab.HOME) },
                )

                // Tab 2: Servers / Route — both engines (Quantum got its server
                // list in 1.5.21, ported from the Windows app).
                BottomNavItem(
                    selected = activeTab == MainTab.SERVERS,
                    label = "Servers",
                    activeColor = activeTint,
                    tag = "nav_servers_button",
                    icon = { selected, tint ->
                        Icon(
                            imageVector = if (selected) Icons.Filled.Public else Icons.Outlined.Public,
                            contentDescription = "Servers",
                            tint = tint,
                            modifier = Modifier.size(20.dp),
                        )
                    },
                    onClick = { onSelectTab(MainTab.SERVERS) },
                )

                // Tab 3: Logs — opens the diagnostics log dialog (an overlay,
                // not a tab screen), so errors are one tap away from Home.
                BottomNavItem(
                    selected = logsOpen,
                    label = "Logs",
                    activeColor = activeTint,
                    tag = "nav_logs_button",
                    icon = { _, tint ->
                        Icon(
                            imageVector = Icons.Rounded.Terminal,
                            contentDescription = "Logs",
                            tint = tint,
                            modifier = Modifier.size(20.dp),
                        )
                    },
                    onClick = onOpenLogs,
                )

                // Tab 4: Profile / Settings — both engines. Was Hyper-only;
                // Quantum shows it too now, so the bottom menus are identical.
                BottomNavItem(
                    selected = activeTab == MainTab.PROFILE,
                    label = "Profile",
                    activeColor = activeTint,
                    tag = "nav_profile_button",
                    icon = { _, tint ->
                        Icon(
                            imageVector = Icons.Rounded.Tune,
                            contentDescription = "Profile",
                            tint = tint,
                            modifier = Modifier.size(20.dp),
                        )
                    },
                    onClick = { onSelectTab(MainTab.PROFILE) },
                )
            }
        }
    }
}

@Composable
private fun BottomNavItem(
    selected: Boolean,
    label: String,
    activeColor: Color,
    tag: String,
    icon: @Composable (Boolean, Color) -> Unit,
    onClick: () -> Unit,
) {
    val tint by animateColorAsState(
        targetValue = if (selected) activeColor else ClxyColors.TextDim,
        animationSpec = tween(240),
        label = "navTint",
    )
    val iconScale by animateFloatAsState(
        targetValue = if (selected) 1.08f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "navScale",
    )

    Column(
        modifier = Modifier
            .testTag(tag)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier.scale(iconScale),
            contentAlignment = Alignment.Center
        ) {
            icon(selected, tint)
        }
        Spacer(Modifier.height(3.dp))
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = tint,
        )
        if (selected) {
            Spacer(Modifier.height(2.dp))
            Box(
                modifier = Modifier
                    .size(3.dp)
                    .background(activeColor, CircleShape)
            )
        }
    }
}

@Composable
fun ProfileMenuCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    tag: String,
    badgeText: String? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .testTag(tag)
            .fillMaxWidth()
            .defaultMinSize(minHeight = 60.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(ClxyColors.CardSurface)
            .border(1.dp, ClxyColors.GlassBorder, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f),
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(ClxyColors.BlueDeep.copy(alpha = 0.22f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = ClxyColors.CyanGlow, modifier = Modifier.size(19.dp))
            }
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(title, color = ClxyColors.TextPrimary, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
                    if (badgeText != null) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(ClxyColors.CyanGlow.copy(alpha = 0.18f))
                                .padding(horizontal = 6.dp, vertical = 1.dp),
                        ) {
                            Text(badgeText, color = ClxyColors.CyanText, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(subtitle, color = ClxyColors.TextMuted, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = ClxyColors.TextMuted,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
fun SheetHeader(title: String, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ClxyColors.ModalSurface)
            .border(0.5.dp, ClxyColors.GlassBorder)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = title,
            color = ClxyColors.TextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
        )
        IconButton(onClick = onDismiss) {
            Icon(Icons.Rounded.Close, contentDescription = "Close", tint = ClxyColors.TextPrimary)
        }
    }
}

@Composable
fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(ClxyColors.CardSurface)
            .border(1.dp, ClxyColors.GlassBorder, RoundedCornerShape(12.dp))
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
            Text(title, color = ClxyColors.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(2.dp))
            Text(subtitle, color = ClxyColors.TextMuted, fontSize = 11.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = clxySwitchColors(),
        )
    }
}

@Composable
fun ChoiceRow(
    title: String,
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, color = ClxyColors.TextMuted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            options.forEach { (key, label) ->
                val isSelected = selected == key
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) ClxyColors.BlueDeep.copy(alpha = 0.50f) else ClxyColors.CardSurface)
                        .border(1.dp, if (isSelected) ClxyColors.CyanGlow else ClxyColors.GlassBorder, RoundedCornerShape(8.dp))
                        .clickable { onSelect(key) }
                        .padding(horizontal = 6.dp, vertical = 9.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        color = if (isSelected) Color.White else ClxyColors.TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}

@Composable
fun clxySwitchColors() = SwitchDefaults.colors(
    checkedThumbColor = ClxyColors.BgTop,
    checkedTrackColor = ClxyColors.CyanGlow,
    uncheckedThumbColor = ClxyColors.TextMuted,
    uncheckedTrackColor = ClxyColors.BarInactive,
)

@Composable
fun clxyTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = ClxyColors.TextPrimary,
    unfocusedTextColor = ClxyColors.TextPrimary,
    focusedBorderColor = ClxyColors.CyanGlow,
    unfocusedBorderColor = Color(0xFF334155),
    focusedLabelColor = ClxyColors.CyanText,
    unfocusedLabelColor = ClxyColors.TextMuted,
    cursorColor = ClxyColors.CyanGlow,
    focusedContainerColor = Color(0xAA070B19),
    unfocusedContainerColor = Color(0x88070B19),
)

