package com.destinyai.astrology.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.destinyai.astrology.R
import com.destinyai.astrology.ui.theme.AppTheme
import com.destinyai.astrology.ui.theme.Gold
import com.destinyai.astrology.ui.theme.GoldLight

/**
 * Reusable celestial glass orb representing a life area.
 *
 * Mirrors iOS `CelestialOrbView`: status-driven aura, dark glass sphere base,
 * inner glass bubble + specular highlight, gold rim, and uppercase gold tracked
 * title below the orb. Press feedback via scale animation
 * (parity with iOS `ScaleButtonStyle`).
 *
 * @param size              Diameter of the orb.
 * @param rotationDurationMs Time for one full 360° rotation. Only applied when
 *                          [animateRotation] is true. Default 30 s.
 * @param animateRotation   When true, the orb spins continuously. iOS default
 *                          is static; default here is `false` for visual parity.
 * @param title             Optional uppercase gold title rendered below the orb.
 * @param status            One of "Good"/"Excellent", "Steady"/"Neutral",
 *                          "Caution"/"Difficult"/"Challenging" (case-insensitive).
 *                          Drives the outer aura color.
 * @param onClick           Optional click handler. When supplied, the orb gains
 *                          press-scale feedback + light haptic on tap.
 */
@Composable
fun CelestialOrb(
    modifier: Modifier = Modifier,
    size: Dp = 120.dp,
    rotationDurationMs: Int = 30_000,
    animateRotation: Boolean = false,
    title: String? = null,
    status: String? = null,
    onClick: (() -> Unit)? = null,
) {
    val rotation = if (animateRotation) {
        val infiniteTransition = rememberInfiniteTransition(label = "orbRotation")
        val angle by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = rotationDurationMs, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "orbAngle",
        )
        angle
    } else {
        0f
    }

    val auraColor = statusColor(status)

    val accessibilityText = if (title != null && status != null) {
        stringResource(R.string.a11y_orb_status_format, title, status) +
            ". " + stringResource(R.string.a11y_double_tap_for_details)
    } else {
        null
    }

    // Press-scale feedback (mirrors iOS ScaleButtonStyle).
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "orbPressScale",
    )

    val haptics = LocalHapticFeedback.current

    val containerModifier = modifier
        .scale(pressScale)
        .let { base ->
            if (onClick != null) {
                base.clickable(
                    interactionSource = interactionSource,
                    indication = null,
                ) {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                }
            } else {
                base
            }
        }
        .let { base ->
            if (accessibilityText != null) {
                base.semantics { contentDescription = accessibilityText }
            } else {
                base
            }
        }

    Column(
        modifier = containerModifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val radius = this.size.minDimension / 2f
            val centre = this.size.center

            // 1. Status Glow Aura — colour reflects status (green/gold/red)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        auraColor.copy(alpha = 0.50f),
                        auraColor.copy(alpha = 0.20f),
                        Color.Transparent,
                    ),
                    center = centre,
                    radius = radius * 1.35f,
                ),
                radius = radius * 1.35f,
                center = centre,
            )

            // 2. Glass Sphere Base — dark navy gradient (mirrors iOS rgb stops)
            rotate(degrees = rotation, pivot = centre) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(red = 0.18f, green = 0.20f, blue = 0.26f, alpha = 1f),
                            Color(red = 0.12f, green = 0.14f, blue = 0.18f, alpha = 1f),
                            Color(red = 0.08f, green = 0.10f, blue = 0.14f, alpha = 0.6f),
                            Color.Transparent,
                        ),
                        center = centre,
                        radius = radius,
                    ),
                    radius = radius,
                    center = centre,
                )

                // 3. Inner Glass Bubble — 3D depth highlight + shadow
                //    iOS UnitPoint(0.35, 0.35) → offset from center = (-0.30 * radius)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.12f),
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.15f),
                        ),
                        center = Offset(
                            x = centre.x - radius * 0.30f,
                            y = centre.y - radius * 0.30f,
                        ),
                        radius = radius * 0.85f,
                    ),
                    radius = radius * 0.85f,
                    center = centre,
                )

                // 4. Top-Left Specular White Highlight
                //    iOS UnitPoint(0.25, 0.25) → offset from center = (-0.50 * radius)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.40f),
                            Color.White.copy(alpha = 0.10f),
                            Color.Transparent,
                        ),
                        center = Offset(
                            x = centre.x - radius * 0.50f,
                            y = centre.y - radius * 0.50f,
                        ),
                        radius = radius * 0.50f,
                    ),
                    radius = radius * 0.50f,
                    center = centre,
                )
            }

            // 5. Hairline gold rim
            drawCircle(
                color = Gold.copy(alpha = 0.50f),
                radius = radius,
                center = centre,
                style = Stroke(width = 1.5f),
            )
        }

        // 6. Title Below Orb — uppercase gold, letter spacing 1sp
        if (title != null) {
            Text(
                text = title.uppercase(),
                color = GoldLight,
                fontWeight = FontWeight.Medium,
                fontSize = 11.sp,
                letterSpacing = 1.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * Sparkle decoration — Android counterpart to iOS `SparkleDecoration`.
 * Renders a small gold-light sparkle icon at 80% opacity.
 */
@Composable
fun SparkleDecoration(
    modifier: Modifier = Modifier,
    size: Dp = 6.dp,
) {
    Icon(
        imageVector = Icons.Filled.Star,
        contentDescription = null,
        tint = AppTheme.colors.goldLight.copy(alpha = 0.80f),
        modifier = modifier
            .size(size)
            .semantics { contentDescription = "sparkle_decoration" },
    )
}

private fun statusColor(status: String?): Color {
    return when (status?.lowercase()) {
        "good", "excellent" -> Color(red = 0.30f, green = 0.78f, blue = 0.45f, alpha = 1f)
        "caution", "difficult", "challenging" -> Color(red = 0.92f, green = 0.30f, blue = 0.30f, alpha = 1f)
        else -> Gold
    }
}
