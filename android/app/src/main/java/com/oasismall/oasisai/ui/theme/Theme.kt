package com.oasismall.oasisai.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = OasisBrandOrange,
    onPrimary = Color.White,
    primaryContainer = OasisBrandOrangeDark,
    onPrimaryContainer = Color.White,
    secondary = OasisGreen,
    onSecondary = Color.White,
    background = OasisSurfaceDark,
    surface = OasisSurfaceContainerDark,
    surfaceVariant = Color(0xFF2A2A2A),
    onSurface = Color(0xFFE8E8E8),
    onSurfaceVariant = Color(0xFFB0B0B0),
    error = OasisRed,
)

private val LightColorScheme = lightColorScheme(
    primary = OasisBrandOrange,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE8DC),
    onPrimaryContainer = OasisBrandOrangeDark,
    secondary = OasisGreen,
    onSecondary = Color.White,
    background = OasisSand,
    surface = Color.White,
    surfaceVariant = Color(0xFFEEE8E0),
    error = OasisRed,
)

@Composable
fun OasisAITheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}
