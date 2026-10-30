package com.u707t.tomatosprout.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.u707t.tomatosprout.domain.ThemeMode

private val LightColors = lightColorScheme(
    primary = Palette.Tomato,
    onPrimary = Palette.Card,
    primaryContainer = Palette.TomatoSoft,
    onPrimaryContainer = Color(0xFF7A2B1D),
    secondary = Palette.Sage,
    onSecondary = Palette.Card,
    secondaryContainer = Palette.SageSoft,
    onSecondaryContainer = Color(0xFF2C3A26),
    tertiary = Palette.Apricot,
    onTertiary = Color(0xFF3D2509),
    tertiaryContainer = Palette.ApricotSoft,
    onTertiaryContainer = Color(0xFF4A2C0C),
    background = Palette.Paper,
    onBackground = Palette.Ink,
    surface = Palette.Card,
    onSurface = Palette.Ink,
    surfaceVariant = Palette.PaperDeep,
    onSurfaceVariant = Palette.InkMuted,
    surfaceContainerLowest = Palette.Card,
    surfaceContainerLow = Color(0xFFFDFAF6),
    surfaceContainer = Palette.Paper,
    surfaceContainerHigh = Palette.PaperDeep,
    surfaceContainerHighest = Color(0xFFEDE5DC),
    outline = Palette.LineStrong,
    outlineVariant = Palette.Line,
    error = Palette.Danger,
    onError = Palette.Card,
    errorContainer = Palette.DangerSoft,
    onErrorContainer = Color(0xFF601410),
    inverseSurface = Palette.Ink,
    inverseOnSurface = Palette.Paper,
    inversePrimary = Color(0xFFFFB4A3),
    scrim = Color(0xFF000000),
)

private val DarkColors = darkColorScheme(
    primary = Palette.NightTomato,
    onPrimary = Color(0xFF3A0F08),
    primaryContainer = Color(0xFF4A2119),
    onPrimaryContainer = Color(0xFFFFD9D0),
    secondary = Color(0xFFA9C79C),
    onSecondary = Color(0xFF1B2A16),
    secondaryContainer = Color(0xFF33452C),
    onSecondaryContainer = Color(0xFFD3E8C7),
    tertiary = Color(0xFFE9B27A),
    onTertiary = Color(0xFF3D2509),
    tertiaryContainer = Color(0xFF543515),
    onTertiaryContainer = Color(0xFFFFDCBC),
    background = Palette.NightBg,
    onBackground = Palette.NightInk,
    surface = Palette.NightSurface,
    onSurface = Palette.NightInk,
    surfaceVariant = Palette.NightCard,
    onSurfaceVariant = Palette.NightMuted,
    surfaceContainerLowest = Color(0xFF100D0B),
    surfaceContainerLow = Palette.NightSurface,
    surfaceContainer = Palette.NightCard,
    surfaceContainerHigh = Color(0xFF2A2320),
    surfaceContainerHighest = Color(0xFF332B26),
    outline = Palette.NightLine,
    outlineVariant = Palette.NightLineSoft,
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    inverseSurface = Palette.NightInk,
    inverseOnSurface = Palette.NightBg,
    inversePrimary = Palette.TomatoDeep,
    scrim = Color(0xFF000000),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(Radii.xs),
    small = RoundedCornerShape(Radii.sm),
    medium = RoundedCornerShape(Radii.md),
    large = RoundedCornerShape(Radii.lg),
    extraLarge = RoundedCornerShape(Radii.sheet),
)

/**
 * 把强调色套进固定的纸面配色。
 *
 * 只覆盖 primary 一组角色：纸面、墨色、语义色都不跟着主题色走，
 * 这样切色不会破坏「暖白纸 + 细描边」的整体气质。
 */
private fun ColorScheme.withAccent(accent: AccentTheme, dark: Boolean): ColorScheme {
    val role = accent.role(dark)
    return copy(
        primary = role.primaryColor(),
        onPrimary = role.onPrimaryColor(),
        primaryContainer = role.primaryContainerColor(),
        onPrimaryContainer = role.onPrimaryContainerColor(),
        inversePrimary = role.inversePrimaryColor(),
    )
}

@Composable
fun TomatoTheme(
    mode: ThemeMode,
    accent: AccentTheme = AccentTheme.DEFAULT,
    content: @Composable () -> Unit,
) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = view.context.findActivity()?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    MaterialTheme(
        colorScheme = (if (dark) DarkColors else LightColors).withAccent(accent, dark),
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
