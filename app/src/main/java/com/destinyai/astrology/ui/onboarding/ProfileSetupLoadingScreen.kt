package com.destinyai.astrology.ui.onboarding

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.destinyai.astrology.ui.theme.CanelaFontFamily
import com.destinyai.astrology.ui.theme.CosmicBackground
import com.destinyai.astrology.ui.theme.CreamDim
import com.destinyai.astrology.ui.theme.CreamText
import com.destinyai.astrology.ui.theme.Gold
import kotlinx.coroutines.delay

private val phases = listOf(
    "Calculating your birth chart" to "Mapping planetary positions at your birth moment",
    "Reading your Dasha timeline" to "Identifying which planetary period governs your life now",
    "Analyzing Yogas & Doshas" to "Detecting powerful combinations in your chart",
    "Preparing your destiny" to "Your cosmic profile is ready",
)

@Composable
fun ProfileSetupLoadingScreen(onComplete: () -> Unit) {
    var phaseIndex by remember { mutableIntStateOf(0) }
    var progress by remember { mutableFloatStateOf(0f) }

    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(800, easing = FastOutSlowInEasing),
        label = "progress",
    )

    LaunchedEffect(Unit) {
        for (i in phases.indices) {
            phaseIndex = i
            val targetProgress = (i + 1).toFloat() / phases.size
            progress = targetProgress
            delay(1200L)
        }
        delay(400)
        onComplete()
    }

    val infiniteTransition = rememberInfiniteTransition(label = "rings")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(8000, easing = LinearEasing)),
        label = "rotation",
    )

    CosmicBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // Orbital ring animation
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .rotate(rotation),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.fillMaxSize(),
                    color = Gold.copy(alpha = 0.3f),
                    strokeWidth = 1.dp,
                    strokeCap = StrokeCap.Round,
                    trackColor = Color.Transparent,
                )
                CircularProgressIndicator(
                    modifier = Modifier.size(86.dp).rotate(-rotation * 0.6f),
                    color = Gold.copy(alpha = 0.15f),
                    strokeWidth = 1.dp,
                    strokeCap = StrokeCap.Round,
                    trackColor = Color.Transparent,
                )
                Text(text = "✦", fontSize = 28.sp, color = Gold)
            }

            Spacer(Modifier.height(40.dp))

            // Phase title
            Text(
                text = phases[phaseIndex].first,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = CanelaFontFamily,
                color = CreamText,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = phases[phaseIndex].second,
                fontSize = 15.sp,
                color = CreamDim,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp,
            )

            Spacer(Modifier.height(48.dp))

            // Gold progress bar
            Column(modifier = Modifier.fillMaxWidth()) {
                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier.fillMaxWidth().height(3.dp),
                    color = Gold,
                    trackColor = Gold.copy(alpha = 0.15f),
                    strokeCap = StrokeCap.Round,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Step ${phaseIndex + 1} of ${phases.size}",
                    fontSize = 12.sp,
                    color = CreamDim,
                    modifier = Modifier.align(Alignment.End),
                )
            }
        }
    }
}
