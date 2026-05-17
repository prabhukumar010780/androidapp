package com.destinyai.astrology.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = Color(0xFFD4AF37),
    onPrimary = Color(0xFF1A1A2E),
    primaryContainer = Color(0xFF2C2C4E),
    onPrimaryContainer = Color(0xFFD4AF37),
    secondary = Color(0xFFAA8800),
    background = Color(0xFF0D0D1A),
    onBackground = Color(0xFFF0E8D0),
    surface = Color(0xFF1A1A2E),
    onSurface = Color(0xFFF0E8D0),
    surfaceVariant = Color(0xFF2C2C4E),
    onSurfaceVariant = Color(0xFFBBAA88),
    error = Color(0xFFFF6B6B),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF8B6914),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF5E6A3),
    onPrimaryContainer = Color(0xFF3B2800),
    secondary = Color(0xFF6B5000),
    background = Color(0xFFF8F4E8),
    onBackground = Color(0xFF1A1200),
    surface = Color.White,
    onSurface = Color(0xFF1A1200),
    surfaceVariant = Color(0xFFF0E8D0),
    onSurfaceVariant = Color(0xFF5C4A1E),
    error = Color(0xFFCC0000),
)

@Composable
fun DestinyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
