package com.destinyai.astrology.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.destinyai.astrology.ui.theme.Gold
import com.destinyai.astrology.ui.theme.GoldChampagne
import com.destinyai.astrology.ui.theme.GoldLight
import com.destinyai.astrology.ui.theme.NavyDeep
import com.destinyai.astrology.ui.theme.NavySurface

/**
 * Reusable rotating celestial orb.
 *
 * Used on Splash, Auth, and Onboarding screens for visual parity with iOS.
 * A slow [infiniteRepeatable] rotation drives a full 360° sweep. The fill
 * is a radial gradient: bright champagne-gold at centre fading to NavyDeep
 * at the rim, with a faint outer glow ring.
 *
 * @param size   Diameter of the orb.
 * @param rotationDurationMs  Time for one full 360° rotation (default 30 s).
 */
@Composable
fun CelestialOrb(
    modifier: Modifier = Modifier,
    size: Dp = 120.dp,
    rotationDurationMs: Int = 30_000,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "orbRotation")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = rotationDurationMs, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "orbAngle",
    )

    Canvas(modifier = modifier.size(size)) {
        val radius = this.size.minDimension / 2f
        val centre = this.size.center

        // Outer glow ring
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Gold.copy(alpha = 0.18f),
                    Color.Transparent,
                ),
                center = centre,
                radius = radius * 1.35f,
            ),
            radius = radius * 1.35f,
            center = centre,
        )

        // Main orb with rotation — the gradient rotates giving a moving shimmer
        rotate(degrees = rotation, pivot = centre) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        GoldChampagne.copy(alpha = 0.90f),
                        GoldLight.copy(alpha = 0.75f),
                        Gold.copy(alpha = 0.60f),
                        NavySurface.copy(alpha = 0.80f),
                        NavyDeep,
                    ),
                    center = Offset(x = centre.x - radius * 0.25f, y = centre.y - radius * 0.25f),
                    radius = radius,
                ),
                radius = radius,
                center = centre,
            )
        }

        // Hairline gold rim
        drawCircle(
            color = Gold.copy(alpha = 0.35f),
            radius = radius,
            center = centre,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5f),
        )
    }
}

