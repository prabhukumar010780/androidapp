package com.destinyai.astrology.ui.components

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.destinyai.astrology.ui.theme.CanelaFontFamily
import com.destinyai.astrology.ui.theme.Gold
import com.destinyai.astrology.ui.theme.GoldLight

/**
 * Text rendered with the iOS `goldGradient()` style — a horizontal
 * sweep that starts gold, brightens to a champagne mid-tone, and
 * returns to gold. Mirrors AppTheme.goldGradient on iOS.
 *
 * Typically used for the screen title across Auth, Splash, Language,
 * and Onboarding screens to maintain visual consistency.
 *
 * Compose's standard `Text` doesn't directly accept a `Brush` color
 * pre-1.6, so we render via `BasicText` with a TextStyle whose `brush`
 * is the gradient.
 */
@Composable
fun GoldGradientText(
    text: String,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 28.sp,
    fontWeight: FontWeight = FontWeight.Bold,
    fontFamily: FontFamily = CanelaFontFamily,
    textAlign: TextAlign = TextAlign.Center,
) {
    val brush = Brush.linearGradient(
        // Three-stop sweep matching iOS — gold, light champagne, gold.
        colors = listOf(Gold, GoldLight, Gold),
    )
    BasicText(
        text = text,
        modifier = modifier,
        style = TextStyle(
            brush = brush,
            fontSize = fontSize,
            fontWeight = fontWeight,
            fontFamily = fontFamily,
            textAlign = textAlign,
        ),
    )
}
