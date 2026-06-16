package com.oasismall.oasisai.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

private val VisioShapes = Shapes(
    extraSmall = RoundedCornerShape(2.dp),
    small = RoundedCornerShape(4.dp),
    medium = RoundedCornerShape(6.dp),
    large = RoundedCornerShape(8.dp),
    extraLarge = RoundedCornerShape(10.dp),
)

private val DarkColorScheme = darkColorScheme(
    primary = VisioSteel,
    onPrimary = VisioCharcoal,
    primaryContainer = VisioSurfaceElevated,
    onPrimaryContainer = VisioSteelBright,
    secondary = VisioAccent,
    onSecondary = VisioCharcoal,
    secondaryContainer = Color(0xFF2E3036),
    onSecondaryContainer = VisioSteelBright,
    background = VisioCharcoal,
    surface = VisioSurface,
    surfaceVariant = VisioSurfaceElevated,
    onSurface = VisioSteelBright,
    onSurfaceVariant = VisioSteelMuted,
    outline = Color(0xFF3A3D44),
    error = VisioRed,
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF4A5058),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD8DCE2),
    onPrimaryContainer = Color(0xFF1A1A1E),
    secondary = Color(0xFF5C6570),
    onSecondary = Color.White,
    background = Color(0xFFF0F1F3),
    surface = Color.White,
    surfaceVariant = Color(0xFFE4E6EA),
    onSurface = Color(0xFF1A1A1E),
    onSurfaceVariant = Color(0xFF5A6068),
    outline = Color(0xFFB8BEC6),
    error = VisioRed,
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
        shapes = VisioShapes,
        content = content,
    )
}
