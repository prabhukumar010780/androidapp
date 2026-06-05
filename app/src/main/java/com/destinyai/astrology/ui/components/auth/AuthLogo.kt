package com.destinyai.astrology.ui.components.auth

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.destinyai.astrology.R
import com.destinyai.astrology.ui.components.rememberBioRhythmWithHaptic
import com.destinyai.astrology.ui.theme.AuthDimens
import com.destinyai.astrology.ui.theme.Gold
import com.destinyai.astrology.ui.theme.GoldLight

/**
 * Animated auth-screen logo — pixel-equivalent port of iOS AuthView's
 * `logoSection` (AuthView.swift:128-167).
 *
 * Layered, all centered on the same point:
 *   1. Outer pulsing radial glow (gold, 140dp, blurred shadow ring).
 *   2. Counter-rotating ring (gold stroke @ 0.3 alpha, 110dp).
 *   3. Single orbiting dot (5dp, GoldLight) on the ring's edge.
 *   4. The Destiny logo image (70dp), shadowed gold and offset 6dp on
 *      the X axis for optical correction of the 'D' shape.
 *
 * The whole logo "breathes" with a 60 BPM scale pulse (the iOS
 * `bioRhythm` modifier). The orbit and the breath are decoupled — the
 * ring rotates linearly while the logo gently scales up/down.
 *
 * @param entranceScale 0..1 multiplier for the entrance spring. The
 *   parent screen drives this to grow the logo from 0.6 → 1.0 once at
 *   first composition. Default 1f means "fully presented".
 * @param bioRhythmActive when true, the logo pulses at 60bpm. Disabled
 *   while the auth flow is loading so the spinning ProgressView reads
 *   as the dominant motion.
 */
@Composable
fun AuthLogo(
    modifier: Modifier = Modifier,
    entranceScale: Float = 1f,
    bioRhythmActive: Boolean = true,
) {
    val infinite = rememberInfiniteTransition(label = "auth-logo")

    // Continuous orbit rotation, 30s per revolution (matches iOS).
    val orbit by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = AuthDimens.orbitRotationDurationMs,
                easing = LinearEasing,
            ),
            repeatMode = RepeatMode.Restart,
        ),
        label = "orbit",
    )

    // 60 BPM breathing pulse (1s per beat). Scale 1.0 -> 1.05 -> 1.0.
    // iOS parity: BioRhythmModifier.pulse() fires HapticManager.playHeartbeat()
    // on every beat. rememberBioRhythmWithHaptic() drives the same haptic tick.
    val pulse = rememberBioRhythmWithHaptic(active = bioRhythmActive)

    Box(
        modifier = modifier
            .size(AuthDimens.glowSize)
            .scale(entranceScale * pulse),
        contentAlignment = Alignment.Center,
    ) {
        // Layer 1 — outer pulsing radial glow.
        // Implemented as a soft golden disc behind everything.
        Canvas(modifier = Modifier.size(AuthDimens.glowSize)) {
            val center = Offset(size.width / 2f, size.height / 2f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Gold.copy(alpha = 0.25f), Color.Transparent),
                    center = center,
                    radius = AuthDimens.glowSize.toPx() / 2f,
                ),
                radius = AuthDimens.glowSize.toPx() / 2f,
                center = center,
            )
        }

        // Layer 2 — rotating ring.
        Canvas(modifier = Modifier.size(AuthDimens.ringSize)) {
            val center = Offset(size.width / 2f, size.height / 2f)
            rotate(degrees = orbit, pivot = center) {
                drawCircle(
                    color = Gold.copy(alpha = 0.3f),
                    radius = AuthDimens.ringSize.toPx() / 2f,
                    center = center,
                    style = Stroke(width = 1f.dp.toPx()),
                )
            }
        }

        // Layer 3 — orbiting dot at ring edge, rotating in lock-step.
        Canvas(modifier = Modifier.size(AuthDimens.ringSize)) {
            val center = Offset(size.width / 2f, size.height / 2f)
            rotate(degrees = orbit, pivot = center) {
                drawCircle(
                    color = GoldLight,
                    radius = AuthDimens.dotSize.toPx() / 2f,
                    center = Offset(
                        x = center.x + AuthDimens.ringSize.toPx() / 2f,
                        y = center.y,
                    ),
                )
            }
        }

        // Layer 4 — the logo image with gold drop shadow.
        Image(
            painter = painterResource(R.drawable.logo_gold),
            contentDescription = "Destiny Logo",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .size(AuthDimens.logoSize)
                .shadow(
                    elevation = 15.dp,
                    shape = CircleShape,
                    ambientColor = Gold.copy(alpha = 0.5f),
                    spotColor = Gold.copy(alpha = 0.5f),
                )
                .clip(CircleShape)
                .semantics { contentDescription = "Destiny Logo" },
        )
    }
}
