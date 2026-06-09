package com.destinyai.astrology.ui.components

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.destinyai.astrology.ui.theme.AppTheme
import com.destinyai.astrology.ui.theme.GoldLight
import kotlin.random.Random

private const val STAR_MIN_SIZE_PX = 1f
private const val STAR_MAX_SIZE_PX = 3f
private const val STAR_TWINKLE_DURATION_MS = 4_000
private const val TILT_SENSITIVITY = 1.0f
private const val TILT_SMOOTHING = 0.15f

// Issue 5 — multiply per-star alpha by this to soften peaks (iOS-parity).
private const val STAR_BRIGHTNESS_FACTOR = 0.7f

// Issue 1 / 5 — soft gold glow blur (iOS uses static blurred gold for battery).
private val STAR_BLUR_RADIUS = 0.5.dp

private val PurpleNebulaColor = Color(0xFF6B5BFF)

private data class Star(
    val x: Float,           // fractional 0..1
    val y: Float,           // fractional 0..1
    val radius: Float,      // px
    val phaseOffset: Float, // 0..1 phase offset so stars don't all blink together
)

/**
 * Three rotating, blurred nebulae — matches iOS `CosmicBackgroundView`.
 *
 * Layer 1 — gold nebula: full size, top-left offset, +25° rotation.
 * Layer 2 — purple nebula: 0.7x, bottom-right offset, -17.5° rotation.
 * Layer 3 — secondary gold nebula: 0.5x, center-bottom offset, +12.5° rotation.
 *
 * Each layer applies [Modifier.motionParallax] for tilt parallax (currently a
 * no-op for battery parity with iOS — see the modifier doc comment).
 */
@Composable
fun CosmicNebulae(modifier: Modifier = Modifier) {
    val nebulaSize = AppTheme.Onboarding.Nebula.size
    val nebulaBlur = AppTheme.Onboarding.Nebula.blur

    // Issues 2 / 6 / 13 — decorative background, hide from accessibility tree.
    Box(modifier = modifier.fillMaxSize().clearAndSetSemantics {}) {
        // Layer 1 — gold nebula, top-left, +25°
        Box(
            modifier = Modifier
                .size(nebulaSize)
                .offset(x = (-nebulaSize.value * 0.3f).dp, y = (-nebulaSize.value * 0.2f).dp)
                .rotate(25f)
                .blur(nebulaBlur)
                .motionParallax(intensity = 1.5f)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            GoldLight.copy(alpha = 0.25f),
                            GoldLight.copy(alpha = 0.05f),
                            Color.Transparent,
                        ),
                    ),
                ),
        )

        // Layer 2 — purple nebula, bottom-right, -17.5°
        val purpleSize = nebulaSize * 0.7f
        Box(
            modifier = Modifier
                .size(purpleSize)
                .offset(x = (purpleSize.value * 0.5f).dp, y = (purpleSize.value * 0.6f).dp)
                .rotate(-17.5f)
                .blur(nebulaBlur * 0.8f)
                .motionParallax(intensity = 1.2f)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            PurpleNebulaColor.copy(alpha = 0.30f),
                            PurpleNebulaColor.copy(alpha = 0.05f),
                            Color.Transparent,
                        ),
                    ),
                ),
        )

        // Layer 3 — secondary gold nebula, center-bottom, +12.5°
        val secondarySize = nebulaSize * 0.5f
        Box(
            modifier = Modifier
                .size(secondarySize)
                .offset(x = (secondarySize.value * 0.2f).dp, y = (secondarySize.value * 0.9f).dp)
                .rotate(12.5f)
                .blur(nebulaBlur * 0.6f)
                .motionParallax(intensity = 0.8f)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            GoldLight.copy(alpha = 0.25f),
                            GoldLight.copy(alpha = 0.05f),
                            Color.Transparent,
                        ),
                    ),
                ),
        )
    }
}

/**
 * Animated parallax star field overlay drawn on a [Canvas].
 *
 * Stars (count from [AppTheme.Onboarding.starCount], default 30 — Issues 4 / 16)
 * are randomly placed and twinkle independently via alpha animations driven
 * by a single [rememberInfiniteTransition]. Each star has a phase offset so
 * they don't all blink in unison.
 *
 * Star colour defaults to [GoldLight] (mirrors iOS `AppTheme.Colors.goldLight`),
 * a 0.5dp blur is applied to the canvas for soft-glow parity with iOS, and per-
 * star alpha is multiplied by [STAR_BRIGHTNESS_FACTOR] so peak luminance lands
 * in iOS's 0.21..0.63 band (Issues 1 / 5).
 *
 * Determinism contract (Issue 17): the placement RNG is seeded with `42` so
 * the layout is stable across recompositions and process restarts. iOS uses
 * fixed angles to achieve the same property — both platforms render an
 * equivalent (non-random-per-instance) star field.
 *
 * The animation pauses when the host lifecycle is not at least
 * [Lifecycle.State.RESUMED] to avoid draining battery while the app is
 * backgrounded, mirroring iOS `CosmicBackgroundView`'s static-on-background
 * behaviour.
 *
 * Intended to be placed as an optional overlay on top of [CosmicNebulae] or
 * [OrbitalBackground] (Issue 14 — gated behind a flag at the call site).
 */
@Composable
fun CosmicStarField(
    modifier: Modifier = Modifier,
    starColor: Color = GoldLight,
    starCount: Int = AppTheme.Onboarding.starCount,
) {
    val stars = remember(starCount) {
        // Issue 17 — deterministic seed contract: see KDoc above. Seed 42 is
        // the contract; do NOT change it without updating the KDoc and any
        // golden snapshot tests that depend on stable layout.
        val rng = Random(seed = 42)
        List(starCount) {
            val sizeRange = STAR_MAX_SIZE_PX - STAR_MIN_SIZE_PX
            Star(
                x = rng.nextFloat(),
                y = rng.nextFloat(),
                radius = STAR_MIN_SIZE_PX + rng.nextFloat() * sizeRange,
                phaseOffset = rng.nextFloat(),
            )
        }
    }

    // Pause the twinkle animation when not RESUMED — battery parity with iOS.
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

    val infiniteTransition = rememberInfiniteTransition(label = "starField")

    // Single animation driver 0..1
    val driver by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = STAR_TWINKLE_DURATION_MS,
                easing = LinearEasing,
            ),
            repeatMode = RepeatMode.Restart,
        ),
        label = "starDriver",
    )

    Canvas(
        modifier = modifier
            .fillMaxSize()
            // Issues 2 / 6 / 13 / 18 — decorative, hide from accessibility tree.
            .clearAndSetSemantics {}
            // Issues 1 / 5 — soft gold glow blur for iOS parity.
            .blur(STAR_BLUR_RADIUS)
            .motionParallax(intensity = 0.5f),
    ) {
        // When backgrounded, freeze the driver at 0 so we render a stable
        // (non-animating) frame.
        val effectiveDriver = if (isResumed) driver else 0f
        stars.forEach { star ->
            // Phase-shifted cosine twinkle: alpha varies between 0.15 and 1.0
            // before the iOS-parity STAR_BRIGHTNESS_FACTOR (0.7) trims peaks.
            val phase = (effectiveDriver + star.phaseOffset) % 1f
            val rawAlpha = 0.15f + 0.85f *
                (0.5f + 0.5f * kotlin.math.cos(phase * 2 * Math.PI)).toFloat()
            val alpha = (rawAlpha * STAR_BRIGHTNESS_FACTOR).coerceIn(0f, 1f)

            drawCircle(
                color = starColor.copy(alpha = alpha),
                radius = star.radius,
                center = Offset(x = star.x * size.width, y = star.y * size.height),
            )
        }
    }
}

// region motionParallax — tilt-driven offset modifier

/**
 * Tilt-parallax modifier backed by [Sensor.TYPE_GAME_ROTATION_VECTOR].
 *
 * iOS `motionParallax` is currently a no-op for battery optimisation (it
 * is invoked at 17+ call sites and a live CMMotionManager would compound to
 * meaningful drain). We mirror that policy here: this modifier wires up the
 * sensor only if [SENSOR_PARALLAX_ENABLED] is true. Otherwise it is a true
 * no-op — same shape, zero allocations.
 *
 * When enabled, the rotation vector's pitch/roll is mapped to a small px
 * offset scaled by [intensity] and [OnboardingDimens.tiltSensitivity], with
 * [OnboardingDimens.tiltSmoothing] low-pass filtering.
 */
private const val SENSOR_PARALLAX_ENABLED = false

fun Modifier.motionParallax(intensity: Float = 1.0f): Modifier = composed {
    if (!SENSOR_PARALLAX_ENABLED) {
        return@composed this
    }

    val context = LocalContext.current
    val tiltX = remember { mutableFloatStateOf(0f) }
    val tiltY = remember { mutableFloatStateOf(0f) }

    DisposableEffect(context) {
        val sensorManager = context.getSystemService(android.content.Context.SENSOR_SERVICE)
            as? SensorManager
        val sensor = sensorManager?.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
        val listener = if (sensor != null) {
            buildRotationVectorListener(tiltX, tiltY)
        } else {
            null
        }
        if (sensorManager != null && sensor != null && listener != null) {
            sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        }
        onDispose {
            if (sensorManager != null && listener != null) {
                sensorManager.unregisterListener(listener)
            }
        }
    }

    val sensitivity = TILT_SENSITIVITY * intensity
    this.offset(
        x = (tiltX.floatValue * sensitivity * 20f).dp,
        y = (tiltY.floatValue * sensitivity * 20f).dp,
    )
}

private fun buildRotationVectorListener(
    tiltX: MutableFloatState,
    tiltY: MutableFloatState,
): SensorEventListener = object : SensorEventListener {
    private val rotationMatrix = FloatArray(9)
    private val orientation = FloatArray(3)
    private var smoothedX = 0f
    private var smoothedY = 0f
    private val smoothing = TILT_SMOOTHING

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_GAME_ROTATION_VECTOR) return
        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
        SensorManager.getOrientation(rotationMatrix, orientation)
        // orientation[1] = pitch, orientation[2] = roll, both in radians
        val rawY = orientation[1].coerceIn(-1f, 1f)
        val rawX = orientation[2].coerceIn(-1f, 1f)
        smoothedX += (rawX - smoothedX) * smoothing
        smoothedY += (rawY - smoothedY) * smoothing
        tiltX.floatValue = smoothedX
        tiltY.floatValue = smoothedY
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}

// endregion
