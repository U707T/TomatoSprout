package com.u707t.tomatosprout.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.u707t.tomatosprout.domain.ThemeMode

val Tomato = Color(0xFFFF6B6B)
val TomatoDeep = Color(0xFFE85555)
val Paper = Color(0xFFF7F7F7)
val Ink = Color(0xFF2B2424)
val Sage = Color(0xFF8FAE83)
val Apricot = Color(0xFFF3B58A)
val Sand = Color(0xFFE9DFD7)

private val LightColors = lightColorScheme(
    primary = Tomato,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE4E4),
    onPrimaryContainer = Color(0xFF5A1D1D),
    secondary = Sage,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE4EEDF),
    onSecondaryContainer = Color(0xFF273521),
    tertiary = Apricot,
    onTertiary = Color(0xFF4A2A10),
    background = Paper,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    surfaceVariant = Color(0xFFF1EAE5),
    onSurfaceVariant = Color(0xFF6B5D56),
    outline = Color(0xFFDED3CC),
    outlineVariant = Color(0xFFEDE4DE),
    error = Color(0xFFB3261E),
    onError = Color.White,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFF8A8A),
    onPrimary = Color(0xFF421010),
    primaryContainer = Color(0xFF5B2020),
    onPrimaryContainer = Color(0xFFFFDADA),
    secondary = Color(0xFFA8C69C),
    onSecondary = Color(0xFF1C2A17),
    secondaryContainer = Color(0xFF34452D),
    onSecondaryContainer = Color(0xFFD5E8CC),
    tertiary = Color(0xFFE0A57B),
    onTertiary = Color(0xFF3F2410),
    background = Color(0xFF161313),
    onBackground = Color(0xFFF2EAE6),
    surface = Color(0xFF211C1B),
    onSurface = Color(0xFFF2EAE6),
    surfaceVariant = Color(0xFF2C2524),
    onSurfaceVariant = Color(0xFFCFC0B9),
    outline = Color(0xFF4B403C),
    outlineVariant = Color(0xFF342C2A),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
)

private val AppTypography = Typography(
    displaySmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 32.sp, lineHeight = 40.sp),
    headlineSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 32.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 24.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun TomatoTheme(mode: ThemeMode, content: @Composable () -> Unit) {
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
        colorScheme = if (dark) DarkColors else LightColors,
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
