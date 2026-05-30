package com.destinyai.astrology.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.destinyai.astrology.ui.theme.Gold
import com.destinyai.astrology.ui.theme.NavySurface

/**
 * Frosted glass-morphism card.
 *
 * Layers applied bottom-to-top:
 * 1. Translucent [NavySurface] fill — simulates frosted backing.
 * 2. Inner radial highlight at ~(0.30, 0.28) drawn via drawBehind — fakes glass refraction.
 * 3. Hairline gold border (0.5 dp, alpha 0.3).
 *
 * Mirrors iOS `DivineGlassCard`.
 */
@Composable
fun DivineGlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 16.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    val shape = RoundedCornerShape(cornerRadius)
    val goldBorderColor = Gold.copy(alpha = 0.3f)

    Box(
        modifier = modifier
            .clip(shape)
            // Base translucent fill
            .background(NavySurface.copy(alpha = 0.75f))
            // Hairline gold border + radial refraction highlight drawn together
            .drawBehind {
                // Gold hairline border
                drawRoundRect(
                    color = goldBorderColor,
                    size = size,
                    cornerRadius = CornerRadius(cornerRadius.toPx(), cornerRadius.toPx()),
                    style = Stroke(width = 0.5.dp.toPx()),
                )
                // Radial refraction highlight positioned at (0.30, 0.28)
                val highlightCenter = Offset(
                    x = size.width * 0.30f,
                    y = size.height * 0.28f,
                )
                val highlightRadius = size.minDimension * 0.6f
                val refractionBrush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.10f),
                        Color.Transparent,
                    ),
                    center = highlightCenter,
                    radius = highlightRadius,
                )
                drawCircle(
                    brush = refractionBrush,
                    radius = highlightRadius,
                    center = highlightCenter,
                )
            },
        content = content,
    )
}
