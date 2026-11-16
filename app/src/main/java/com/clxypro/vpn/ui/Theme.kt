package com.clxypro.vpn.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.clxypro.vpn.core.Prefs
import com.clxypro.vpn.core.rtl
import com.clxypro.vpn.ui.theme.ClxyColors
import com.clxypro.vpn.ui.theme.Typography

private val ClxyDarkColorScheme = darkColorScheme(
    primary = ClxyColors.CyanGlow,
    onPrimary = ClxyColors.BgTop,
    primaryContainer = ClxyColors.BlueDeep,
    onPrimaryContainer = ClxyColors.TextPrimary,
    secondary = ClxyColors.BluePrimary,
    onSecondary = ClxyColors.TextPrimary,
    secondaryContainer = ClxyColors.CardSurfaceSolid,
    onSecondaryContainer = ClxyColors.TextPrimary,
    tertiary = ClxyColors.PurplePrimary,
    onTertiary = ClxyColors.TextPrimary,
    background = ClxyColors.BgTop,
    onBackground = ClxyColors.TextPrimary,
    surface = ClxyColors.CardSurfaceSolid,
    onSurface = ClxyColors.TextPrimary,
    surfaceVariant = ClxyColors.ModalSurface,
    onSurfaceVariant = ClxyColors.TextMuted,
    outline = ClxyColors.GlassBorder,
    error = ClxyColors.RedDisconnectStart,
    onError = ClxyColors.TextPrimary,
)

object ClxyThemeTokens {
    val background = Brush.verticalGradient(
        listOf(ClxyColors.BgTop, ClxyColors.BgMid, ClxyColors.BgBottom)
    )
    val idle = ClxyColors.TextDim
    val halo = Brush.radialGradient(
        listOf(ClxyColors.CyanGlow.copy(alpha = 0.15f), Color.Transparent)
    )
}

/** Shared alias so other screens can use the cosmic sky */
object Lotus {
    val background = ClxyThemeTokens.background
    val idle = ClxyThemeTokens.idle
    val halo = ClxyThemeTokens.halo
}

@Composable
fun ClxyTheme(content: @Composable () -> Unit) {
    val direction = if (Prefs.language.rtl) LayoutDirection.Rtl else LayoutDirection.Ltr
    CompositionLocalProvider(LocalLayoutDirection provides direction) {
        MaterialTheme(
            colorScheme = ClxyDarkColorScheme,
            typography = Typography,
        ) {
            ProvideAppTextToolbar(content)
        }
    }
}

@Composable

