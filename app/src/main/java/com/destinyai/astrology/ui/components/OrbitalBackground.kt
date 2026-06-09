package com.destinyai.astrology.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.destinyai.astrology.ui.theme.GoldAccent
import com.destinyai.astrology.ui.theme.NavyDeep
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Stylized orrery background — five orbits + sun + six orbiting planets.
 *
 * Mirrors iOS `OrbitalBackground` from
 * `ios_app/ios_app/Components/CosmicBackground.swift`. The center sits at
 * `(w/2, h*0.35)` with `maxRadius = min(w, h) * 0.45`. Planets travel an
 * elliptical path with `y = sin * 0.4` for orrery perspective.
 *
 * Each planet animates on its own infinite tween — Mercury 8s through
 * Saturn 40s — paused while the host lifecycle is not [Lifecycle.State.RESUMED]
 * for battery parity with iOS.
 */
@Composable
fun OrbitalBackground(
    modifier: Modifier = Modifier,
    showOrbits: Boolean = true,
    orbitOpacity: Float = 0.08f,
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(NavyDeep),
    ) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }
        val center = Offset(x = widthPx / 2f, y = heightPx * 0.35f)
        val maxRadius = min(widthPx, heightPx) * 0.45f

        // Pause animations when backgrounded.
        val lifecycleOwner = LocalLifecycleOwner.current
        var isResumed by remember { mutableStateOf(true) }
        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME -> isResumed = true
                    Lifecycle.Event.ON_PAUSE -> isResumed = false
                    else -> Unit
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }

        // Orbit rings — drawn in one Canvas pass.
        if (showOrbits) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val ringColor = GoldAccent.copy(alpha = orbitOpacity)
                val stroke = Stroke(width = 1f)
                listOf(0.25f, 0.4f, 0.55f, 0.72f, 0.88f).forEach { f ->
                    drawCircle(
                        color = ringColor,
                        radius = maxRadius * f,
                        center = center,
                        style = stroke,
                    )
                }
            }
        }

        // Central sun — radial outer glow + inner core.
        CentralSun(center = center)

        // Planets — six orbiters at iOS-defined radius, size, color, duration,
        // start angle, and Saturn's ring.
        OrbitingPlanet(
            center = center,
            radius = maxRadius * 0.25f,
            planetSize = 6f,
            planetColor = Color(red = 0.7f, green = 0.6f, blue = 0.5f), // Mercury
            orbitDurationMs = 8_000,
            startAngleDeg = 45f,
            isAnimating = isResumed,
        )
        OrbitingPlanet(
            center = center,
            radius = maxRadius * 0.4f,
            planetSize = 10f,
            planetColor = Color(red = 0.9f, green = 0.7f, blue = 0.4f), // Venus
            orbitDurationMs = 12_000,
            startAngleDeg = 120f,
            isAnimating = isResumed,
        )
        OrbitingPlanet(
            center = center,
            radius = maxRadius * 0.55f,
            planetSize = 8f,
            planetColor = Color(red = 0.85f, green = 0.35f, blue = 0.25f), // Mars
            orbitDurationMs = 18_000,
            startAngleDeg = 200f,
            isAnimating = isResumed,
        )
        OrbitingPlanet(
            center = center,
            radius = maxRadius * 0.72f,
            planetSize = 16f,
            planetColor = Color(red = 0.85f, green = 0.75f, blue = 0.6f), // Jupiter
            orbitDurationMs = 28_000,
            startAngleDeg = 280f,
            isAnimating = isResumed,
        )
        OrbitingPlanet(
            center = center,
            radius = maxRadius * 0.88f,
            planetSize = 14f,
            planetColor = GoldAccent, // Saturn
            orbitDurationMs = 40_000,
            startAngleDeg = 330f,
            hasRing = true,
            isAnimating = isResumed,
        )
        // Moon — small, fast, close, with white glow.
        OrbitingPlanet(
            center = center,
            radius = maxRadius * 0.15f,
            planetSize = 5f,
            planetColor = Color.White.copy(alpha = 0.9f),
            orbitDurationMs = 5_000,
            startAngleDeg = 0f,
            glowColor = Color.White,
            isAnimating = isResumed,
        )
    }
}

@Composable
private fun CentralSun(center: Offset) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        // Outer glow — radial gradient, 132px diameter (matches iOS frame).
        val outerRadius = 66f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    GoldAccent.copy(alpha = 0.30f),
                    GoldAccent.copy(alpha = 0.10f),
                    Color.Transparent,
                ),
                center = center,
                radius = outerRadius,
            ),
            radius = outerRadius,
            center = center,
        )

        // Inner core — 30px diameter with shadow halo (approximated by an
        // additional translucent ring since Compose has no shadow on Canvas).
        drawCircle(
            color = GoldAccent.copy(alpha = 0.5f),
            radius = 25f,
            center = center,
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(GoldAccent, GoldAccent.copy(alpha = 0.8f)),
                center = center,
                radius = 15f,
            ),
            radius = 15f,
            center = center,
        )
    }
}

/**
 * One orbiting planet sprite — body + optional white glow + optional Saturn
 * ring. Driven by an infinite tween of [orbitDurationMs] when [isAnimating]
 * is true, otherwise stays at [startAngleDeg].
 *
 * The orbit is elliptical: y = `sin(theta) * 0.4` for orrery perspective.
 */
@Composable
private fun OrbitingPlanet(
    center: Offset,
    radius: Float,
    planetSize: Float,
    planetColor: Color,
    orbitDurationMs: Int,
    startAngleDeg: Float,
    hasRing: Boolean = false,
    glowColor: Color? = null,
    isAnimating: Boolean = true,
) {
    val transition = rememberInfiniteTransition(label = "orbit")
    val animatedAngle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = orbitDurationMs, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "orbitAngle",
    )

    val angleDeg = startAngleDeg + if (isAnimating) animatedAngle else 0f
    val radians = angleDeg * Math.PI.toFloat() / 180f
    val position = Offset(
        x = center.x + radius * cos(radians),
        y = center.y + radius * sin(radians) * 0.4f, // elliptical
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        // Glow
        if (glowColor != null) {
            drawCircle(
                color = glowColor.copy(alpha = 0.4f),
                radius = planetSize,
                center = position,
            )
        }

        // Planet body — radial gradient with bright top-left to give 3D feel.
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(planetColor, planetColor.copy(alpha = 0.7f)),
                center = Offset(
                    x = position.x - planetSize * 0.2f,
                    y = position.y - planetSize * 0.2f,
                ),
                radius = planetSize,
            ),
            radius = planetSize / 2f,
            center = position,
        )

        // Saturn ring — thin elliptical stroke.
        if (hasRing) {
            val ringW = planetSize * 2.2f
            val ringH = planetSize * 0.6f
            // Use a stroked oval centred on position.
            drawOval(
                color = planetColor.copy(alpha = 0.5f),
                topLeft = Offset(position.x - ringW / 2f, position.y - ringH / 2f),
                size = Size(ringW, ringH),
                style = Stroke(width = 2f),
            )
        }
    }
}

// region MinimalOrbitalBackground — lighter alternative orrery

private val MinimalGradientTop = Color(red = 0.96f, green = 0.95f, blue = 0.98f)
private val MinimalGradientBottom = Color(red = 0.94f, green = 0.93f, blue = 0.96f)

/**
 * Lighter alternative to [OrbitalBackground] — fewer elements, light gradient
 * base, subtle center glow, and just three [SubtleOrbit] dots.
 *
 * Mirrors iOS `MinimalOrbitalBackground` from
 * `ios_app/ios_app/Components/CosmicBackground.swift`. The center sits at
 * `(w/2, h*0.3)` with `maxRadius = min(w, h) * 0.4`, and the orbit's
 * elliptical perspective uses a tighter `y * 0.35` (vs 0.4 in
 * [OrbitalBackground]).
 *
 * Issue 10 — Android parity for the iOS minimal orrery view modifier.
 */
@Composable
fun MinimalOrbitalBackground(modifier: Modifier = Modifier) {
    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
    ) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }
        val center = Offset(x = widthPx / 2f, y = heightPx * 0.3f)
        val maxRadius = min(widthPx, heightPx) * 0.4f

        // Decorative — hide from a11y tree (parity with CosmicNebulae).
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(MinimalGradientTop, MinimalGradientBottom),
                    ),
                )
                .clearAndSetSemantics {},
        )

        // Subtle center glow — 80x80 gold, blurred for soft halo.
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .blur(30.dp),
        ) {
            drawCircle(
                color = GoldAccent.copy(alpha = 0.15f),
                radius = 40f,
                center = center,
            )
        }

        // Three subtle orbiting dots at radii 0.4 / 0.65 / 0.9 of maxRadius,
        // sized 6 / 8 / 10 px, started at 0° / 120° / 240° (iOS spec).
        listOf(0, 1, 2).forEach { index ->
            SubtleOrbit(
                center = center,
                radius = maxRadius * (0.4f + index * 0.25f),
                dotSize = 6f + index * 2f,
                startAngleDeg = index * 120f,
            )
        }
    }
}

/**
 * Single static orbit ring + a glowing gold dot at [startAngleDeg].
 *
 * Mirrors iOS `SubtleOrbit`. Static (no animation) — the iOS version is also
 * effectively static at the call site since it positions by `startAngle` only.
 */
@Composable
private fun SubtleOrbit(
    center: Offset,
    radius: Float,
    dotSize: Float,
    startAngleDeg: Float,
) {
    val radians = startAngleDeg * Math.PI.toFloat() / 180f
    val position = Offset(
        x = center.x + radius * cos(radians),
        y = center.y + radius * sin(radians) * 0.35f, // tighter ellipse than full orrery
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        // Orbit path — very subtle elliptical stroke.
        val ringW = radius * 2f
        val ringH = radius * 2f * 0.35f
        drawOval(
            color = GoldAccent.copy(alpha = 0.06f),
            topLeft = Offset(center.x - ringW / 2f, center.y - ringH / 2f),
            size = Size(ringW, ringH),
            style = Stroke(width = 1f),
        )

        // Dot glow — soft outer halo.
        drawCircle(
            color = GoldAccent.copy(alpha = 0.30f * 0.6f),
            radius = dotSize * 1.25f,
            center = position,
        )

        // Dot core.
        drawCircle(
            color = GoldAccent.copy(alpha = 0.6f),
            radius = dotSize / 2f,
            center = position,
        )
    }
}

// endregion
