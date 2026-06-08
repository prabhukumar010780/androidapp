package com.destinyai.astrology.ui.splash

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.destinyai.astrology.R
import com.destinyai.astrology.services.SoundManager
import com.destinyai.astrology.services.motionParallax
import com.destinyai.astrology.ui.theme.CanelaFontFamily
import com.destinyai.astrology.ui.theme.CreamText
import com.destinyai.astrology.ui.theme.Gold
import com.destinyai.astrology.ui.theme.NavyDeep
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import kotlinx.coroutines.launch

/**
 * Splash overlay — pure presentational composable. Mirrors iOS SplashView, which is
 * rendered inside AppRootView's ZStack overlay and contains no navigation logic.
 * Navigation is now driven from AppNav (parity with AppRootView dismissal timer).
 *
 * iOS parity:
 *   - LiquidGoldBackground (animated 5-blob radial Canvas) — FluidBackground.swift:15-69
 *   - Spring scale-in + opacity entrance, blur-in title, staggered fades — SplashView.swift:8-14, 145-172
 *   - Logo bioRhythm 60 BPM heartbeat — SplashView.swift:70 (.bioRhythm)
 *   - Logo shimmer sweep overlay — FluidBackground.swift:71-90
 *   - Title drop shadow — SplashView.swift:86
 *   - Loading dots scale+alpha pulse — SplashView.swift:110-122
 *   - "Ascension" success chime on appear — SplashView.swift:140
 */
@Composable
fun SplashScreen(soundManager: SoundManager? = null) {
    // iOS parity (SplashView.swift:140): play "Ascension" chime once on splash appearance.
    LaunchedEffect(Unit) {
        soundManager?.playSuccess()
    }

    // ============================================================================
    // Entrance animation states — mirrors iOS @State vars in SplashView.swift:8-14
    // ============================================================================
    val logoScale = remember { Animatable(0.6f) }
    val logoAlpha = remember { Animatable(0f) }
    val titleAlpha = remember { Animatable(0f) }
    val titleBlur = remember { Animatable(10f) }
    val subtitleAlpha = remember { Animatable(0f) }
    val starsAlpha = remember { Animatable(0f) }

    // iOS parity (SplashView.swift:145-172 startAnimations): spring + delayed easeOut fades.
    // All animations run concurrently via launch{} so delays don't compound.
    LaunchedEffect(Unit) {
        // Logo spring (matches AppTheme.Splash.logoAnimationDuration ~ 0.8s, damping 0.6)
        launch {
            logoScale.animateTo(
                targetValue = 1f,
                animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow),
            )
        }
        launch {
            logoAlpha.animateTo(1f, tween(durationMillis = 800, easing = FastOutSlowInEasing))
        }
        // Title cinematic blur-in (1.5s easeOut, ~0.6s delay matching titleFadeDelay)
        launch {
            kotlinx.coroutines.delay(600)
            titleAlpha.animateTo(1f, tween(durationMillis = 1500, easing = LinearOutSlowInEasing))
        }
        launch {
            kotlinx.coroutines.delay(600)
            titleBlur.animateTo(0f, tween(durationMillis = 1500, easing = LinearOutSlowInEasing))
        }
        // Subtitle + tagline + dots (0.5s, ~1.0s delay)
        launch {
            kotlinx.coroutines.delay(1000)
            subtitleAlpha.animateTo(1f, tween(durationMillis = 500, easing = LinearOutSlowInEasing))
        }
        // Stars fade in (1s, ~0.3s delay)
        launch {
            kotlinx.coroutines.delay(300)
            starsAlpha.animateTo(1f, tween(durationMillis = 1000, easing = FastOutLinearInEasing))
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(NavyDeep)) {
        // Layer 1: Liquid Gold animated fluid background (iOS parity: FluidBackground.swift:15-69)
        LiquidGoldBackground()

        // Layer 2: Parallax-style star field (faded in via starsAlpha)
        Box(modifier = Modifier.fillMaxSize().alpha(starsAlpha.value)) {
            StarField()
        }

        // Layer 3: Main content
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // Logo: gold circle with logo + pulsing glow + orbital ring + heartbeat + shimmer
            // iOS parity (SplashView.swift:74): .premiumInertia(intensity: 20) — gravity-driven
            // parallax so the logo lags behind device tilt like a heavy gold object.
            Box(
                modifier = Modifier
                    .size(200.dp)
                    .motionParallax(intensity = 20f)
                    .graphicsLayer {
                        scaleX = logoScale.value
                        scaleY = logoScale.value
                        alpha = logoAlpha.value
                    },
                contentAlignment = Alignment.Center,
            ) {
                // iOS parity (SplashView.swift:36-49): outer (0.2) + inner (0.4) glow stack.
                PulsingGlow(alpha = 0.2f, sizeDp = 220.dp)
                PulsingGlow(alpha = 0.4f, sizeDp = 160.dp)
                OrbitalRing()
                LogoWithShimmerAndHeartbeat()
            }

            Spacer(Modifier.height(24.dp))

            // "DESTINY" title — Canela bold, white, with cinematic blur-in + drop shadow
            Text(
                text = stringResource(R.string.destiny_app_title),
                fontSize = 42.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = CanelaFontFamily,
                letterSpacing = 8.sp,
                color = Color.White,
                textAlign = TextAlign.Center,
                style = TextStyle(
                    shadow = Shadow(
                        color = Color.Black.copy(alpha = 0.3f),
                        offset = Offset(0f, 2f),
                        blurRadius = 2f,
                    ),
                ),
                modifier = Modifier
                    .alpha(titleAlpha.value)
                    .blur(radius = titleBlur.value.dp),
            )

            Spacer(Modifier.height(6.dp))

            Text(
                text = stringResource(R.string.ai_astrology_subtitle),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 4.sp,
                color = Gold.copy(alpha = 0.85f),
                textAlign = TextAlign.Center,
                modifier = Modifier.alpha(subtitleAlpha.value),
            )

            Spacer(Modifier.height(6.dp))

            Text(
                text = stringResource(R.string.worlds_advanced_ai),
                fontSize = 11.sp,
                color = CreamText.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                modifier = Modifier.alpha(subtitleAlpha.value),
            )

            Spacer(Modifier.height(64.dp))

            // 3 animated gold loading dots — alpha + scale pulse
            Box(modifier = Modifier.alpha(subtitleAlpha.value)) {
                AnimatedDots()
            }

            Spacer(Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.aligning_stars),
                fontSize = 13.sp,
                fontStyle = FontStyle.Italic,
                color = CreamText.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
                modifier = Modifier.alpha(subtitleAlpha.value),
            )
        }
    }
}

/**
 * iOS parity (SplashView.swift:36-74) — logo container with shimmer overlay and 60 BPM
 * heartbeat scale modifier. Heartbeat is applied to the inner gold circle so it does not
 * compound with the outer entrance spring scale (iOS .bioRhythm sits below .scaleEffect).
 */
@Composable
private fun LogoWithShimmerAndHeartbeat() {
    // iOS parity (PremiumComponents.swift:428): bioRhythm bpm=60 → 1s period 1.00↔1.03 scale.
    val infiniteTransition = rememberInfiniteTransition(label = "logo_anim")
    val heartbeat by infiniteTransition.animateFloat(
        initialValue = 1.00f,
        targetValue = 1.03f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "heartbeat",
    )
    // iOS parity (FluidBackground.swift:71-90): white-gradient stripe sweeps across logo.
    // AppTheme.Splash.shimmerDuration ~ 2.5s.
    val density = LocalDensity.current
    val shimmerSweep by infiniteTransition.animateFloat(
        initialValue = -200f,
        targetValue = 200f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmer",
    )

    Box(
        modifier = Modifier
            .size(80.dp)
            .clip(CircleShape)
            .background(Gold)
            .graphicsLayer {
                scaleX = heartbeat
                scaleY = heartbeat
            },
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.logo),
            contentDescription = "Destiny Logo",
            modifier = Modifier.size(56.dp),
            contentScale = ContentScale.Fit,
        )
        // Shimmer sweep overlay — masked by parent CircleShape clip.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = with(density) { shimmerSweep.dp.toPx() }
                }
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.White.copy(alpha = 0.3f),
                            Color.Transparent,
                        ),
                    ),
                ),
        )
    }
}

@Composable
private fun AnimatedDots() {
    val infiniteTransition = rememberInfiniteTransition(label = "dots")
    val dot1Alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
            initialStartOffset = StartOffset(0),
        ),
        label = "dot1",
    )
    val dot2Alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
            initialStartOffset = StartOffset(200),
        ),
        label = "dot2",
    )
    val dot3Alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
            initialStartOffset = StartOffset(400),
        ),
        label = "dot3",
    )
    // iOS parity (SplashView.swift:115-121): dot scale 0.5↔1.0 alongside alpha pulse.
    val dot1Scale by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
            initialStartOffset = StartOffset(0),
        ),
        label = "dot1_scale",
    )
    val dot2Scale by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
            initialStartOffset = StartOffset(200),
        ),
        label = "dot2_scale",
    )
    val dot3Scale by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
            initialStartOffset = StartOffset(400),
        ),
        label = "dot3_scale",
    )

    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        listOf(
            dot1Alpha to dot1Scale,
            dot2Alpha to dot2Scale,
            dot3Alpha to dot3Scale,
        ).forEach { (alphaVal, scaleVal) ->
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .graphicsLayer {
                        scaleX = scaleVal
                        scaleY = scaleVal
                    }
                    .clip(CircleShape)
                    .alpha(alphaVal)
                    .background(Gold),
            )
        }
    }
}

/**
 * Liquid Gold animated background — iOS parity (FluidBackground.swift:15-69).
 * Five low-opacity radial gold blobs whose centers oscillate via sin/cos driven
 * by an infiniteTransition phase float, redrawn ~15fps on a Canvas.
 */
@Composable
private fun LiquidGoldBackground() {
    val infiniteTransition = rememberInfiniteTransition(label = "fluid_phase")
    // Continuous time-like phase, 60 seconds per full cycle so motion looks slow + organic.
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 60_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "phase",
    )
    val goldColor = Color(red = 0.83f, green = 0.69f, blue = 0.22f)
    Canvas(modifier = Modifier.fillMaxSize()) {
        for (i in 0 until 5) {
            val baseX = size.width * (0.2f + i * 0.15f)
            val baseY = size.height * (0.3f + i * 0.1f)
            // Multiply phase by tuning factors so each blob drifts slightly differently —
            // mirrors iOS time*0.3 and time*0.25 organic offsets.
            val offsetX = (sin(phase * 3.0 + i * 0.5).toFloat()) * 50f
            val offsetY = (cos(phase * 2.5 + i * 0.7).toFloat()) * 30f
            val radius = 80f + (sin(phase * 2.0 + i.toDouble()).toFloat()) * 20f
            val center = Offset(baseX + offsetX, baseY + offsetY)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        goldColor.copy(alpha = 0.08f),
                        goldColor.copy(alpha = 0.03f),
                        Color.Transparent,
                    ),
                    center = center,
                    radius = radius,
                ),
                radius = radius,
                center = center,
            )
        }
    }
}

/**
 * Canvas-drawn twinkling star field — iOS parity (SplashView.swift:175-206)
 * with 3 depth layers: Far (25 stars, small + dim), Mid (20), Near (15, large + bright).
 * Each layer renders a separate Canvas so they read as independent parallax planes.
 */
@Composable
private fun StarField() {
    Box(modifier = Modifier.fillMaxSize()) {
        StarLayer(
            starCount = 25,
            minSize = 1f,
            maxSize = 1.5f,
            minAlpha = 0.2f,
            maxAlpha = 0.4f,
            seed = 11,
            label = "stars_far",
        )
        StarLayer(
            starCount = 20,
            minSize = 1.5f,
            maxSize = 2.5f,
            minAlpha = 0.4f,
            maxAlpha = 0.6f,
            seed = 22,
            label = "stars_mid",
        )
        StarLayer(
            starCount = 15,
            minSize = 2f,
            maxSize = 3f,
            minAlpha = 0.6f,
            maxAlpha = 0.9f,
            seed = 33,
            label = "stars_near",
        )
    }
}

@Composable
private fun StarLayer(
    starCount: Int,
    minSize: Float,
    maxSize: Float,
    minAlpha: Float,
    maxAlpha: Float,
    seed: Int,
    label: String,
) {
    val infiniteTransition = rememberInfiniteTransition(label = label)
    val twinkle by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "${label}_twinkle",
    )
    val stars = remember(seed, starCount) {
        val rng = Random(seed)
        List(starCount) {
            // x, y, sizeFrac, alphaFrac
            listOf(rng.nextFloat(), rng.nextFloat(), rng.nextFloat(), rng.nextFloat())
        }
    }
    Canvas(modifier = Modifier.fillMaxSize()) {
        stars.forEachIndexed { idx, s ->
            val radius = minSize + s[2] * (maxSize - minSize)
            val baseAlpha = minAlpha + s[3] * (maxAlpha - minAlpha)
            val alpha = if (idx % 3 == 0) baseAlpha * twinkle else baseAlpha
            drawCircle(
                color = Color.White.copy(alpha = alpha),
                radius = radius,
                center = Offset(s[0] * size.width, s[1] * size.height),
            )
        }
    }
}

/**
 * Pulsing radial glow behind the logo — mirrors iOS PulsingGlowView (outer+inner).
 * Stacked with two instances at alpha 0.2 (outer, larger) and 0.4 (inner, smaller)
 * to match SplashView.swift:36-49 double-glow.
 */
@Composable
private fun PulsingGlow(
    alpha: Float = 0.4f,
    sizeDp: androidx.compose.ui.unit.Dp = 180.dp,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "glow")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse",
    )
    Box(
        modifier = Modifier
            .size(sizeDp * pulse)
            .alpha(alpha)
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Gold.copy(alpha = 0.55f),
                        Gold.copy(alpha = 0.15f),
                        Color.Transparent,
                    ),
                ),
                shape = CircleShape,
            ),
    )
}

/**
 * Rotating thin gold ring around the logo — mirrors iOS OrbitalRingsView.
 */
@Composable
private fun OrbitalRing() {
    val infiniteTransition = rememberInfiniteTransition(label = "orbit")
    val angle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "angle",
    )
    Canvas(modifier = Modifier.size(140.dp)) {
        val ringRadius = size.minDimension / 2f - 4f
        val center = Offset(size.width / 2f, size.height / 2f)
        drawCircle(
            color = Gold.copy(alpha = 0.18f),
            radius = ringRadius,
            center = center,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.2f),
        )
        repeat(3) { i ->
            val theta = Math.toRadians((angle + i * 120f).toDouble())
            val dotCenter = Offset(
                x = center.x + (ringRadius * cos(theta)).toFloat(),
                y = center.y + (ringRadius * sin(theta)).toFloat(),
            )
            drawCircle(
                color = Gold.copy(alpha = 0.85f),
                radius = 2.5f,
                center = dotCenter,
            )
        }
    }
}

/** Tiny helper retained for potential future reuse — currently animations launch directly. */
@Suppress("unused")
private suspend inline fun launchAnim(crossinline block: suspend () -> Unit) {
    kotlinx.coroutines.coroutineScope {
        launch { block() }
    }
}

