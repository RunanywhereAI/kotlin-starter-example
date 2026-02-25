package com.runanywhere.kotlin_starter_example.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

@Immutable
data class AppColors(
    val background: Color,
    val surface: Color,
    val surfaceContainer: Color,
    val surfaceElevated: Color,
    val border: Color,
    val accent: Color,
    val accentSubtle: Color,
    val success: Color,
    val warning: Color,
    val error: Color,
    val info: Color,
    val tintBlue: Color,
    val tintPurple: Color,
    val tintPink: Color,
    val tintGreen: Color,
    val tintOrange: Color,
    val tintCyan: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val isDark: Boolean,
)

// ── Dark palette ── inspired by Linear, Raycast, Arc
private val DarkAppColors = AppColors(
    background = Color(0xFF101014),
    surface = Color(0xFF18181C),
    surfaceContainer = Color(0xFF1F1F24),
    surfaceElevated = Color(0xFF26262C),
    border = Color(0xFF2E2E36),
    accent = Color(0xFF6C8EEF),
    accentSubtle = Color(0x1F6C8EEF),
    success = Color(0xFF5CB176),
    warning = Color(0xFFD4A04A),
    error = Color(0xFFD46A6A),
    info = Color(0xFF6C8EEF),
    tintBlue = Color(0xFF6C8EEF),
    tintPurple = Color(0xFF9B7AE8),
    tintPink = Color(0xFFD472A4),
    tintGreen = Color(0xFF5CB176),
    tintOrange = Color(0xFFD4A04A),
    tintCyan = Color(0xFF56B5C4),
    textPrimary = Color(0xFFE8E8ED),
    textSecondary = Color(0xFF9898A4),
    textTertiary = Color(0xFF5E5E6E),
    isDark = true,
)

// ── Light palette ── clean, airy, iOS-inspired
private val LightAppColors = AppColors(
    background = Color(0xFFF6F6FA),
    surface = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFEEEEF3),
    surfaceElevated = Color(0xFFFFFFFF),
    border = Color(0xFFDCDCE4),
    accent = Color(0xFF4A6FE5),
    accentSubtle = Color(0x144A6FE5),
    success = Color(0xFF3A9259),
    warning = Color(0xFFB87D1A),
    error = Color(0xFFC24444),
    info = Color(0xFF4A6FE5),
    tintBlue = Color(0xFF4A6FE5),
    tintPurple = Color(0xFF7B5BD6),
    tintPink = Color(0xFFC0508A),
    tintGreen = Color(0xFF3A9259),
    tintOrange = Color(0xFFB87D1A),
    tintCyan = Color(0xFF3098A8),
    textPrimary = Color(0xFF1A1A1F),
    textSecondary = Color(0xFF6E6E7A),
    textTertiary = Color(0xFFA0A0AC),
    isDark = false,
)

val LocalAppColors = staticCompositionLocalOf { DarkAppColors }

object AppTheme {
    val colors: AppColors
        @Composable
        get() = LocalAppColors.current
}

// ── Material color schemes ──

private val DarkColorScheme = darkColorScheme(
    primary = DarkAppColors.accent,
    secondary = DarkAppColors.tintPurple,
    tertiary = DarkAppColors.tintPink,
    background = DarkAppColors.background,
    surface = DarkAppColors.surface,
    surfaceVariant = DarkAppColors.surfaceContainer,
    surfaceContainerHigh = DarkAppColors.surfaceElevated,
    outline = DarkAppColors.border,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = DarkAppColors.textPrimary,
    onSurface = DarkAppColors.textPrimary,
    onSurfaceVariant = DarkAppColors.textSecondary,
    error = DarkAppColors.error,
    onError = Color.White,
    errorContainer = DarkAppColors.error.copy(alpha = 0.12f),
    onErrorContainer = DarkAppColors.error,
)

private val LightColorScheme = lightColorScheme(
    primary = LightAppColors.accent,
    secondary = LightAppColors.tintPurple,
    tertiary = LightAppColors.tintPink,
    background = LightAppColors.background,
    surface = LightAppColors.surface,
    surfaceVariant = LightAppColors.surfaceContainer,
    surfaceContainerHigh = LightAppColors.surfaceElevated,
    outline = LightAppColors.border,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = LightAppColors.textPrimary,
    onSurface = LightAppColors.textPrimary,
    onSurfaceVariant = LightAppColors.textSecondary,
    error = LightAppColors.error,
    onError = Color.White,
    errorContainer = LightAppColors.error.copy(alpha = 0.08f),
    onErrorContainer = LightAppColors.error,
)

@Composable
fun KotlinStarterTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val appColors = if (darkTheme) DarkAppColors else LightAppColors
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            @Suppress("DEPRECATION")
            window.statusBarColor = appColors.background.toArgb()
            @Suppress("DEPRECATION")
            window.navigationBarColor = appColors.background.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(LocalAppColors provides appColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            content = content
        )
    }
}
