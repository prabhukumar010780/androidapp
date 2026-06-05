package com.destinyai.astrology.ui.onboarding

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.destinyai.astrology.R
import com.destinyai.astrology.ui.components.FloatingIcon
import com.destinyai.astrology.ui.components.ShimmerButton
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue
import com.destinyai.astrology.ui.theme.CanelaFontFamily
import com.destinyai.astrology.ui.theme.CosmicBackground
import com.destinyai.astrology.ui.theme.Gold
import com.destinyai.astrology.ui.theme.GoldLight
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
    // iOS parity: HapticManager + SoundManager are injected via the Hilt
    // ViewModel so the screen does not need to construct them itself.
    val resolvedHaptic = viewModel.haptic
    val soundManager = viewModel.sound

    CosmicBackground {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .testTag("onboarding_screen")
                .semantics { contentDescription = "onboarding_screen" },
        ) {
            // Skip button — iOS parity (OnboardingView.swift:43-61): pinned to leading edge.
            if (!isLastSlide) {
                TextButton(
                    onClick = {
                        resolvedHaptic.light()
                        soundManager.playButtonTap()
                        scope.launch {
                            viewModel.complete()
                            onNavigateToAuth()
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(top = 56.dp, start = 16.dp)
                        .testTag("onboarding_skip")
                        .semantics { contentDescription = "onboarding_skip" },
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
                    val pageOffset =
                        ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction)
                            .absoluteValue
                    OnboardingPage(
                        slide = slides[page],
                        modifier = Modifier.graphicsLayer {
                            // iOS parity (OnboardingView.swift:75-80): scrollTransition fades,
                            // scales (0.92), and blurs (radius 2) off-screen pages.
                            val identity = (1f - pageOffset).coerceIn(0f, 1f)
                            alpha = 0.5f + 0.5f * identity
                            val scale = 0.92f + 0.08f * identity
                            scaleX = scale
                            scaleY = scale
                        },
                    )
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

                // Continue / Get Started button — iOS parity uses ShimmerButton with
                // premiumContinue + playButtonTap on intermediate slides, and
                // premiumSuccess + playSuccess on Get Started.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                ) {
                    ShimmerButton(
                        text = if (isLastSlide) stringResource(R.string.get_started)
                        else stringResource(R.string.action_continue),
                        modifier = Modifier
                            .testTag("onboarding_continue")
                            .semantics { contentDescription = "onboarding_continue" },
                        onClick = {
                            scope.launch {
                                if (isLastSlide) {
                                    resolvedHaptic.premiumSuccess()
                                    soundManager.playSuccess()
                                    viewModel.complete()
                                    onNavigateToAuth()
                                } else {
                                    resolvedHaptic.premiumContinue()
                                    soundManager.playButtonTap()
                                    pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                }
                            }
                        },
                    )
                }

                Spacer(Modifier.height(48.dp))
            }
        }
    }
}

@Composable
private fun OnboardingPage(slide: OnboardingSlide, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (slide.imageRes != null) {
            // iOS parity (OnboardingSlideView.swift:16-19, 79-82): hero image is
            // wrapped in FloatingIcon (radial glow + bobbing motion) — we approximate
            // by layering a radial glow behind the image and animating the offset.
            Box(
                modifier = Modifier.size(180.dp),
                contentAlignment = Alignment.Center,
            ) {
                // Glow halo behind hero image (matches iOS FloatingIcon pulsing glow).
                Box(
                    modifier = Modifier
                        .size(180.dp)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(Gold.copy(alpha = 0.35f), Color.Transparent),
                            ),
                        ),
                )
                Image(
                    painter = painterResource(slide.imageRes),
                    contentDescription = null,
                    modifier = Modifier
                        .size(140.dp)
                        .clip(RoundedCornerShape(20.dp)),
                    contentScale = ContentScale.Fit,
                )
            }
        } else {
            // Fallback when no hero image — use a sparkles FloatingIcon to keep parity.
            FloatingIcon(icon = Icons.Filled.AutoAwesome, iconSize = 80.dp)
        }

        Spacer(Modifier.height(32.dp))

        // iOS parity (OnboardingSlideView.swift:25,51,88): slide title rendered
        // with the gold gradient (not solid Gold). Compose 1.6+ supports
        // TextStyle(brush = ...) for gradient text.
        val goldBrush = Brush.linearGradient(listOf(GoldLight, Gold))
        Text(
            text = stringResource(slide.titleRes),
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = CanelaFontFamily,
            textAlign = TextAlign.Center,
            lineHeight = 36.sp,
            style = TextStyle(brush = goldBrush),
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
    // iOS parity (OnboardingSlideView.swift:219-287): glassmorphism with dark
    // gradient base, glossy top sheen, inner light stroke, gold gradient outer
    // stroke, plus dual drop shadows (black + gold).
    val cornerRadius = 24.dp
    val outerStrokeBrush = Brush.linearGradient(
        colors = listOf(
            Gold.copy(alpha = 0.5f),
            Gold.copy(alpha = 0.2f),
            Gold.copy(alpha = 0.1f),
        ),
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 20.dp,
                shape = RoundedCornerShape(cornerRadius),
                ambientColor = Color.Black.copy(alpha = 0.3f),
                spotColor = Color.Black.copy(alpha = 0.3f),
            )
            .shadow(
                elevation = 30.dp,
                shape = RoundedCornerShape(cornerRadius),
                ambientColor = Gold.copy(alpha = 0.08f),
                spotColor = Gold.copy(alpha = 0.18f),
            )
            .clip(RoundedCornerShape(cornerRadius))
            // Dark gradient base (iOS Color(white: 0.15) → Color(white: 0.08)).
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xE6262626),
                        Color(0xE6141414),
                    ),
                ),
            )
            .border(
                width = 1.dp,
                brush = outerStrokeBrush,
                shape = RoundedCornerShape(cornerRadius),
            ),
    ) {
        // Glossy top shine layer
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.15f),
                            Color.White.copy(alpha = 0.05f),
                            Color.Transparent,
                        ),
                    ),
                ),
        )
        // Inner light stroke (iOS .stroke white 0.2→0.05→clear)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(1.dp)
                .border(
                    width = 1.5.dp,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.2f),
                            Color.White.copy(alpha = 0.05f),
                            Color.Transparent,
                        ),
                    ),
                    shape = RoundedCornerShape(cornerRadius - 1.dp),
                ),
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp, horizontal = 20.dp),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth(),
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
                                listOf(Color.Transparent, Gold.copy(alpha = 0.3f), Color.Transparent),
                            ),
                        ),
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
