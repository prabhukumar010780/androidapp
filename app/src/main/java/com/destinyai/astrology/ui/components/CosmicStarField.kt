package com.destinyai.astrology.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import kotlin.random.Random

private const val STAR_COUNT = 30

private data class Star(
    val x: Float,       // fractional 0..1
    val y: Float,       // fractional 0..1
    val radius: Float,  // px
    val phaseOffset: Float, // 0..1 phase offset so stars don't all blink together
)

/**
 * Animated parallax star field overlay drawn on a [Canvas].
 *
 * 30 randomly-placed stars twinkle independently via alpha animations
 * driven by a single [rememberInfiniteTransition]. Each star has a phase
 * offset so they don't all blink in unison.
 *
 * Intended to be placed as an overlay on top of [CosmicBackground].
 */
@Composable
fun CosmicStarField(
    modifier: Modifier = Modifier,
    starColor: Color = Color.White,
) {
    val stars = remember {
        val rng = Random(seed = 42) // deterministic seed for stable layout
        List(STAR_COUNT) {
            Star(
                x = rng.nextFloat(),
                y = rng.nextFloat(),
                radius = rng.nextFloat() * 2.5f + 0.8f,
                phaseOffset = rng.nextFloat(),
            )
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "starField")

    // Single animation driver 0..1
    val driver by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "starDriver",
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        stars.forEach { star ->
            // Phase-shifted cosine twinkle: alpha varies between 0.15 and 1.0
            val phase = (driver + star.phaseOffset) % 1f
            val alpha = 0.15f + 0.85f * (0.5f + 0.5f * kotlin.math.cos(phase * 2 * Math.PI)).toFloat()

            drawCircle(
                color = starColor.copy(alpha = alpha),
                radius = star.radius,
                center = Offset(x = star.x * size.width, y = star.y * size.height),
            )
        }
    }
}
