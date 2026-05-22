package com.destinyai.astrology.ui.onboarding

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.destinyai.astrology.R
import kotlinx.coroutines.launch
import com.destinyai.astrology.ui.theme.CanelaFontFamily
import com.destinyai.astrology.ui.theme.CosmicBackground
import com.destinyai.astrology.ui.theme.Gold
import com.destinyai.astrology.ui.theme.NavyVariant
import com.destinyai.astrology.ui.theme.NavySurface
import com.destinyai.astrology.ui.theme.CreamDim
import com.destinyai.astrology.ui.theme.CreamText

@Composable
fun OnboardingScreen(
    onNavigateToAuth: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val slides = OnboardingSlide.slides
    val pagerState = rememberPagerState(pageCount = { slides.size })
    val isLastSlide = pagerState.currentPage == slides.lastIndex
    val scope = rememberCoroutineScope()

    CosmicBackground {
        Box(modifier = Modifier.fillMaxSize()) {
            // Skip button — shown on all slides except the last
            if (!isLastSlide) {
                TextButton(
                    onClick = onNavigateToAuth,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 56.dp, end = 16.dp),
                ) {
                    Text(
                        text = stringResource(R.string.skip),
                        color = CreamDim,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }

            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(120.dp))

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.weight(1f),
                ) { page ->
                    OnboardingPage(slide = slides[page])
                }

                // Capsule page indicators
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 24.dp),
                ) {
                    slides.indices.forEach { index ->
                        val width by animateDpAsState(
                            targetValue = if (index == pagerState.currentPage) 24.dp else 8.dp,
                            label = "indicatorWidth",
                        )
                        Box(
                            modifier = Modifier
                                .size(width, 8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(
                                    if (index == pagerState.currentPage) Gold
                                    else NavyVariant,
                                ),
                        )
                    }
                }

                // Continue / Get Started button
                Button(
                    onClick = {
                        scope.launch {
                            if (isLastSlide) {
                                viewModel.complete()
                                onNavigateToAuth()
                            } else {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .padding(horizontal = 24.dp),
                    shape = RoundedCornerShape(26.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Gold,
                        contentColor = Color(0xFF0D0D1A),
                    ),
                ) {
                    val label = if (isLastSlide) {
                        stringResource(R.string.get_started)
                    } else {
                        stringResource(R.string.action_continue)
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = label,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp,
                        )
                        if (!isLastSlide) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }

                Spacer(Modifier.height(48.dp))
            }
        }
    }
}

@Composable
private fun OnboardingPage(slide: OnboardingSlide) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (slide.imageRes != null) {
            Image(
                painter = painterResource(slide.imageRes),
                contentDescription = null,
                modifier = Modifier
                    .size(160.dp)
                    .clip(RoundedCornerShape(20.dp)),
                contentScale = ContentScale.Fit,
            )
        }

        Spacer(Modifier.height(32.dp))

        Text(
            text = stringResource(slide.titleRes),
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = CanelaFontFamily,
            color = Gold,
            textAlign = TextAlign.Center,
            lineHeight = 36.sp,
        )

        if (slide.subtitleRes != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(slide.subtitleRes),
                fontSize = 16.sp,
                color = CreamDim,
                textAlign = TextAlign.Center,
            )
        }

        // Stats card for first slide
        if (slide.showStats) {
            Spacer(Modifier.height(24.dp))
            StatsCard()
        }

        // Description for middle slides
        if (slide.descriptionRes != null) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(slide.descriptionRes),
                fontSize = 15.sp,
                color = CreamDim,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp,
            )
        }

        // Features list for last slide
        if (slide.isFeatureSlide) {
            Spacer(Modifier.height(16.dp))
            FeaturesListView()
        }
    }
}

@Composable
private fun StatsCard() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(NavySurface.copy(alpha = 0.9f))
            .then(
                Modifier.padding(1.dp)
            ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(NavySurface)
                .padding(vertical = 24.dp, horizontal = 20.dp),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // 3M+ stat
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Chat,
                            contentDescription = null,
                            tint = Gold,
                            modifier = Modifier.size(24.dp),
                        )
                        Text(
                            text = stringResource(R.string.stat_questions_asked),
                            fontSize = 32.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = CreamText,
                        )
                    }
                    Text(
                        text = stringResource(R.string.questions_asked),
                        fontSize = 14.sp,
                        color = CreamDim,
                    )
                }

                // Gold fade divider
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color.Transparent, Gold.copy(alpha = 0.3f), Color.Transparent)
                            )
                        )
                )

                // Stars + rating
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        repeat(4) {
                            Icon(Icons.Filled.Star, contentDescription = null, tint = Gold, modifier = Modifier.size(18.dp))
                        }
                        Icon(Icons.Outlined.StarOutline, contentDescription = null, tint = Gold.copy(alpha = 0.3f), modifier = Modifier.size(18.dp))
                    }
                    Text(
                        text = stringResource(R.string.stat_rating),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = CreamText,
                    )
                    Text(
                        text = stringResource(R.string.rating_label),
                        fontSize = 11.sp,
                        color = CreamDim,
                    )
                }
            }
        }
    }
}

private data class FeatureItem(
    val icon: ImageVector,
    val titleRes: Int,
    val descRes: Int,
)

@Composable
private fun FeaturesListView() {
    val features = listOf(
        FeatureItem(Icons.AutoMirrored.Filled.Chat, R.string.onboarding_feature1_title, R.string.onboarding_feature1_desc),
        FeatureItem(Icons.Filled.CheckCircle, R.string.onboarding_feature2_title, R.string.onboarding_feature2_desc),
        FeatureItem(Icons.Filled.Favorite, R.string.onboarding_feature3_title, R.string.onboarding_feature3_desc),
        FeatureItem(Icons.Filled.History, R.string.onboarding_feature4_title, R.string.onboarding_feature4_desc),
        FeatureItem(Icons.Filled.Notifications, R.string.onboarding_feature5_title, R.string.onboarding_feature5_desc),
    )

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        features.forEach { feature ->
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Gold.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = feature.icon,
                        contentDescription = null,
                        tint = Gold,
                        modifier = Modifier.size(16.dp),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(feature.titleRes),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = CreamText,
                    )
                    Text(
                        text = stringResource(feature.descRes),
                        fontSize = 14.sp,
                        color = CreamDim,
                        lineHeight = 20.sp,
                    )
                }
            }
        }
    }
}
