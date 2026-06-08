package com.destinyai.astrology.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
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
 * 2. 5-stop gold gradient rim (topLeading -> bottomTrailing) at 1.5dp.
 *
 * Mirrors iOS `DivineGlassCard`.
 *
 * @param active Reserved for future animation parity with iOS `active` flag.
 */
@Composable
fun DivineGlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 24.dp,
    active: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    @Suppress("UNUSED_PARAMETER") val activeFlag = active
    val shape = RoundedCornerShape(cornerRadius)
    // 5-stop gradient gold rim mirroring iOS DivineGlassCard.swift
    // (white -> gold -> faded gold -> gold -> white) at 1.5dp.
    // Explicit start/end Offsets enforce topLeading -> bottomTrailing direction
    // for parity with iOS LinearGradient(startPoint:.topLeading, endPoint:.bottomTrailing).
    val goldRimBrush = Brush.linearGradient(
        colorStops = arrayOf(
            0.05f to Color.White.copy(alpha = 0.8f),
            0.20f to Gold,
            0.50f to Gold.copy(alpha = 0.3f),
            0.80f to Gold,
            0.98f to Color.White.copy(alpha = 0.7f),
        ),
        start = Offset.Zero,
        end = Offset.Infinite,
    )

    Box(
        modifier = modifier
            // Outer drop shadow mirroring iOS .shadow(black 0.3, radius 10, y: 6)
            .shadow(
                elevation = 10.dp,
                shape = shape,
                ambientColor = Color.Black.copy(alpha = 0.3f),
                spotColor = Color.Black.copy(alpha = 0.3f),
            )
            .clip(shape)
            // Base translucent fill
            .background(NavySurface.copy(alpha = 0.75f))
            // 5-stop gold gradient rim (1.5dp)
            .drawBehind {
                drawRoundRect(
                    brush = goldRimBrush,
                    size = size,
                    cornerRadius = CornerRadius(cornerRadius.toPx(), cornerRadius.toPx()),
                    style = Stroke(width = 1.5.dp.toPx()),
                )
            }
            // Internal 16dp content padding mirroring iOS .padding(16) on content
            .padding(16.dp),
        content = content,
    )
}
