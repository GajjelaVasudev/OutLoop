package com.outloop.prototype.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// ============================================================================
// STITCH CUSTOM COLOR TOKENS
// ============================================================================

@Immutable
data class OutLoopColorTokens(
    val canvasRoot: Color,
    val surfaceContainer: Color,
    val surfaceCard: Color,
    val surfaceBorder: Color,
    val accentLime: Color,
    val onAccentLime: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val statusSuccess: Color,
    val statusWarning: Color,
    val statusError: Color,
    val isDark: Boolean
)

val DarkOutLoopColorTokens = OutLoopColorTokens(
    canvasRoot = StitchDarkCanvasRoot,
    surfaceContainer = StitchDarkSurfaceContainer,
    surfaceCard = StitchDarkSurfaceCard,
    surfaceBorder = StitchDarkSurfaceBorder,
    accentLime = StitchPrimaryLime,
    onAccentLime = StitchOnPrimaryDark,
    textPrimary = StitchDarkTextPrimary,
    textSecondary = StitchDarkTextSecondary,
    textMuted = StitchDarkTextMuted,
    statusSuccess = StitchStatusSuccessDark,
    statusWarning = StitchStatusWarning,
    statusError = StitchStatusErrorDark,
    isDark = true
)

val LightOutLoopColorTokens = OutLoopColorTokens(
    canvasRoot = StitchLightCanvasRoot,
    surfaceContainer = StitchLightSurfaceContainer,
    surfaceCard = StitchLightSurfaceCard,
    surfaceBorder = StitchLightSurfaceBorder,
    accentLime = StitchPrimaryLime,
    onAccentLime = StitchOnPrimaryDark,
    textPrimary = StitchLightTextPrimary,
    textSecondary = StitchLightTextSecondary,
    textMuted = StitchLightTextMuted,
    statusSuccess = StitchStatusSuccessLight,
    statusWarning = StitchStatusWarning,
    statusError = StitchStatusErrorLight,
    isDark = false
)

val LocalOutLoopColors = staticCompositionLocalOf { DarkOutLoopColorTokens }

// Material 3 Color Schemes
private val DarkColorScheme = darkColorScheme(
    primary = StitchPrimaryLime,
    onPrimary = StitchOnPrimaryDark,
    primaryContainer = StitchPrimaryLime,
    onPrimaryContainer = StitchOnPrimaryDark,
    secondary = StitchDarkTextSecondary,
    onSecondary = StitchDarkCanvasRoot,
    background = StitchDarkCanvasRoot,
    onBackground = StitchDarkTextPrimary,
    surface = StitchDarkSurfaceContainer,
    onSurface = StitchDarkTextPrimary,
    surfaceVariant = StitchDarkSurfaceCard,
    onSurfaceVariant = StitchDarkTextSecondary,
    outline = StitchDarkSurfaceBorder,
    error = StitchStatusErrorDark
)

private val LightColorScheme = lightColorScheme(
    primary = StitchLightTextPrimary,
    onPrimary = StitchLightCanvasRoot,
    primaryContainer = StitchPrimaryLime,
    onPrimaryContainer = StitchOnPrimaryDark,
    secondary = StitchLightTextSecondary,
    onSecondary = StitchLightCanvasRoot,
    background = StitchLightCanvasRoot,
    onBackground = StitchLightTextPrimary,
    surface = StitchLightSurfaceContainer,
    onSurface = StitchLightTextPrimary,
    surfaceVariant = StitchLightSurfaceCard,
    onSurfaceVariant = StitchLightTextSecondary,
    outline = StitchLightSurfaceBorder,
    error = StitchStatusErrorLight
)

@Composable
fun OutLoopTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val outLoopColors = if (darkTheme) DarkOutLoopColorTokens else LightOutLoopColorTokens

    CompositionLocalProvider(LocalOutLoopColors provides outLoopColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}

// Convenient accessor object for Compose callers
object OutLoopTheme {
    val colors: OutLoopColorTokens
        @Composable
        get() = LocalOutLoopColors.current

    val typography: androidx.compose.material3.Typography
        @Composable
        get() = MaterialTheme.typography
}
