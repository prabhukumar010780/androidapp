package com.destinyai.astrology.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.destinyai.astrology.R

val Gold = Color(0xFFD4AF37)
val GoldDim = Color(0xFF8A7638)
val GoldSoft = Color(0xFFF2D06B)
val GoldChampagne = Color(0xFFFFF8E1)
val NavyDeep = Color(0xFF0B0F19)      // iOS mainBackground
val NavySurface = Color(0xFF151A29)   // iOS cardBackground
val NavyVariant = Color(0xFF1C2235)   // iOS secondaryBackground
val NavyInput = Color(0xFF121620)     // iOS inputBackground
val CreamText = Color(0xFFFFFFFF)     // iOS textPrimary (white)
val CreamDim = Color(0xFFA0AEC0)      // iOS textSecondary (cool gray)

val GoldGradient = Brush.linearGradient(
    colors = listOf(Color(0xFFD4AF37), Color(0xFFF5D060), Color(0xFFD4AF37)),
    start = Offset(0f, 0f),
    end = Offset(300f, 0f),
)

val CanelaFontFamily = FontFamily(
    Font(R.font.canela_bold, FontWeight.Bold),
    Font(R.font.canela_roman, FontWeight.Normal),
    Font(R.font.canela_light, FontWeight.Light),
)

val PlayfairFontFamily = FontFamily(
    Font(R.font.playfair_display_regular, FontWeight.Normal),
)

private val DestinyTypography = Typography(
    displayLarge = TextStyle(fontFamily = CanelaFontFamily, fontWeight = FontWeight.Bold, fontSize = 57.sp),
    displayMedium = TextStyle(fontFamily = CanelaFontFamily, fontWeight = FontWeight.Bold, fontSize = 45.sp),
    displaySmall = TextStyle(fontFamily = CanelaFontFamily, fontWeight = FontWeight.Bold, fontSize = 36.sp),
    headlineLarge = TextStyle(fontFamily = CanelaFontFamily, fontWeight = FontWeight.Bold, fontSize = 32.sp),
    headlineMedium = TextStyle(fontFamily = CanelaFontFamily, fontWeight = FontWeight.Bold, fontSize = 28.sp),
    headlineSmall = TextStyle(fontFamily = CanelaFontFamily, fontWeight = FontWeight.Normal, fontSize = 24.sp),
    titleLarge = TextStyle(fontFamily = CanelaFontFamily, fontWeight = FontWeight.Bold, fontSize = 22.sp),
    titleMedium = TextStyle(fontFamily = CanelaFontFamily, fontWeight = FontWeight.Normal, fontSize = 16.sp),
    titleSmall = TextStyle(fontFamily = CanelaFontFamily, fontWeight = FontWeight.Normal, fontSize = 14.sp),
    bodyLarge = TextStyle(fontSize = 16.sp),
    bodyMedium = TextStyle(fontSize = 14.sp),
    bodySmall = TextStyle(fontSize = 12.sp),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 12.sp),
    labelSmall = TextStyle(fontSize = 11.sp),
)

private val DarkColors = darkColorScheme(
    primary = Gold,
    onPrimary = NavyDeep,
    primaryContainer = NavyVariant,
    onPrimaryContainer = Gold,
    secondary = GoldDim,
    background = NavyDeep,
    onBackground = CreamText,
    surface = NavySurface,
    onSurface = CreamText,
    surfaceVariant = NavyVariant,
    onSurfaceVariant = CreamDim,
    error = Color(0xFFFF5252),
    onError = Color.White,
)

@Composable
fun DestinyTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        typography = DestinyTypography,
        content = content,
    )
}

@Composable
fun CosmicBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0F1422),
                        NavyDeep,
                        NavyDeep,
                    ),
                ),
            ),
    ) {
        content()
    }
}

