package com.destinyai.astrology.ui.splash

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.destinyai.astrology.R
import com.destinyai.astrology.ui.theme.CanelaFontFamily
import com.destinyai.astrology.ui.theme.CosmicBackground
import com.destinyai.astrology.ui.theme.Gold
import com.destinyai.astrology.ui.theme.CreamText
import com.destinyai.astrology.ui.theme.CreamDim

@Composable
fun SplashScreen(
    onNavigateToLanguage: () -> Unit,
    onNavigateToOnboarding: () -> Unit,
    onNavigateToAuth: () -> Unit,
    onNavigateToWaitlist: () -> Unit,
    onNavigateToBirthData: () -> Unit,
    onNavigateToMain: () -> Unit,
    viewModel: SplashViewModel = hiltViewModel(),
) {
    val destination by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.navigate() }

    LaunchedEffect(destination) {
        when (destination) {
            SplashDestination.LanguageSelection -> onNavigateToLanguage()
            SplashDestination.Onboarding -> onNavigateToOnboarding()
            SplashDestination.Auth -> onNavigateToAuth()
            SplashDestination.WaitlistPending -> onNavigateToWaitlist()
            SplashDestination.BirthData -> onNavigateToBirthData()
            SplashDestination.Main -> onNavigateToMain()
            SplashDestination.Splash -> {}
        }
    }

    CosmicBackground {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // Logo: gold circle with dark logo inside (matches iOS)
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(Gold),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(R.drawable.logo),
                    contentDescription = "Destiny Logo",
                    modifier = Modifier.size(56.dp),
                    contentScale = ContentScale.Fit,
                )
            }

            Spacer(Modifier.height(24.dp))

            // "DESTINY" — Canela bold, white (matches iOS)
            Text(
                text = stringResource(R.string.destiny_app_title),
                fontSize = 42.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = CanelaFontFamily,
                letterSpacing = 8.sp,
                color = Color.White,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(6.dp))

            // "AI ASTROLOGY" — gold subtitle
            Text(
                text = "AI ASTROLOGY",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 4.sp,
                color = Gold.copy(alpha = 0.85f),
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(6.dp))

            // "The world's most advanced AI astrology"
            Text(
                text = stringResource(R.string.get_cosmic_guidance),
                fontSize = 11.sp,
                color = CreamText.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(64.dp))

            // 3 animated gold loading dots
            AnimatedDots()

            Spacer(Modifier.height(16.dp))

            // "Aligning the stars..."
            Text(
                text = stringResource(R.string.aligning_stars),
                fontSize = 13.sp,
                fontStyle = FontStyle.Italic,
                color = CreamText.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
            )
        }
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

    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        listOf(dot1Alpha, dot2Alpha, dot3Alpha).forEach { alpha ->
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .alpha(alpha)
                    .background(Gold),
            )
        }
    }
}
