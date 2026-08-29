package com.destinyai.astrology.ui.components

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.destinyai.astrology.ui.theme.CanelaFontFamily
import com.destinyai.astrology.ui.theme.GoldGradient

/**
 * Text rendered with the iOS `goldGradient()` style — a horizontal
 * sweep that starts gold, brightens to a champagne mid-tone, and
 * returns to gold. Mirrors AppTheme.goldGradient on iOS.
 *
 * Batch 6b: consumes the canonical [GoldGradient] brush from DestinyTheme
 * instead of a divergent local definition, so there is one gold ramp app-wide.
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
    BasicText(
        text = text,
        modifier = modifier,
        style = TextStyle(
            brush = GoldGradient,
            fontSize = fontSize,
            fontWeight = fontWeight,
            fontFamily = fontFamily,
            textAlign = textAlign,
        ),
    )
}
