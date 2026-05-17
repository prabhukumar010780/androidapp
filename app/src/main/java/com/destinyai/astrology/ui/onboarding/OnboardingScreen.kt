package com.destinyai.astrology.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun OnboardingScreen(
    onNavigateToAuth: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val currentSlide by viewModel.currentSlide.collectAsStateWithLifecycle()
    val slides = OnboardingSlide.slides

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(48.dp))
        Text(
            text = slideIcon(currentSlide),
            fontSize = 80.sp,
        )
        Spacer(Modifier.height(32.dp))
        Text(
            text = slideTitle(currentSlide),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = slideDescription(currentSlide),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.weight(1f))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(bottom = 24.dp),
        ) {
            slides.indices.forEach { index ->
                Box(
                    modifier = Modifier
                        .size(if (index == currentSlide) 24.dp else 8.dp, 8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            if (index == currentSlide) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant,
                        ),
                )
            }
        }
        Button(
            onClick = { viewModel.nextSlide(onNavigateToAuth) },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(12.dp),
        ) {
            Text(
                text = if (viewModel.isLastSlide(currentSlide)) "Get Started" else "Next",
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.height(16.dp))
    }
}

private fun slideIcon(index: Int) = when (index) {
    0 -> "✦"
    1 -> "🔮"
    2 -> "🪐"
    else -> "⭐"
}

private fun slideTitle(index: Int) = when (index) {
    0 -> "Your Destiny Awaits"
    1 -> "Clarity from the Stars"
    2 -> "Personalized for You"
    else -> "Everything You Need"
}

private fun slideDescription(index: Int) = when (index) {
    0 -> "Vedic astrology insights powered by AI, tailored to your birth chart."
    1 -> "Daily guidance on career, love, health, and life decisions."
    2 -> "Your unique birth chart drives every prediction."
    else -> "Chat, compatibility, charts, and daily insights — all in one app."
}
