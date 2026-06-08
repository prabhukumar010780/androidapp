package com.destinyai.astrology.ui.onboarding

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ShowChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.destinyai.astrology.R
import com.destinyai.astrology.services.HapticManager
import com.destinyai.astrology.ui.components.ShimmerButton
import com.destinyai.astrology.ui.theme.CanelaFontFamily
import com.destinyai.astrology.ui.theme.CosmicBackground
import com.destinyai.astrology.ui.theme.CreamDim
import com.destinyai.astrology.ui.theme.CreamText
import com.destinyai.astrology.ui.theme.Gold
import com.destinyai.astrology.ui.theme.NavySurface

private data class ResponseStyleOption(
    val key: String,
    val labelRes: Int,
    val taglineRes: Int,
    val exampleResponseRes: Int,
    // iOS parity (ResponseStyleOnboardingView.swift:142-144): each card has a leading
    // gold-tinted icon. Mapped from iOS ContentStyle.icon (sparkles / chart.xyaxis.line).
    val icon: ImageVector,
)

// iOS contract: ContentStyle raw values are "guidance" (essentials) and "astrology" (completeChart).
// These keys are sent to backend as `response_style`.
private val styleOptions = listOf(
    ResponseStyleOption(
        key = "guidance",
        labelRes = R.string.content_style_essentials,
        taglineRes = R.string.content_style_essentials_tagline,
        exampleResponseRes = R.string.content_style_essentials_example,
        icon = Icons.Outlined.AutoAwesome, // iOS: "sparkles"
    ),
    ResponseStyleOption(
        key = "astrology",
        labelRes = R.string.content_style_complete,
        taglineRes = R.string.content_style_complete_tagline,
        exampleResponseRes = R.string.content_style_complete_example,
        icon = Icons.Outlined.ShowChart, // iOS: "chart.xyaxis.line"
    ),
)

@Composable
fun ResponseStyleOnboardingScreen(
    isSettingsMode: Boolean = false,
    onContinue: () -> Unit,
    onBack: () -> Unit = {},
    viewModel: ResponseStyleOnboardingViewModel = hiltViewModel(),
) {
    val selected by viewModel.selected.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val haptic = remember { HapticManager(context) }

    LaunchedEffect(Unit) { viewModel.loadCurrent() }

    CosmicBackground {
        Box(modifier = Modifier.fillMaxSize()) {
            // iOS parity (ResponseStyleOnboardingView.swift:23-35): subtle gold radial
            // glow at top above the cosmic background.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .offset(y = (-60).dp)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                Gold.copy(alpha = 0.07f),
                                Color.Transparent,
                            ),
                        ),
                    ),
            )
            Column(modifier = Modifier.fillMaxSize()) {
            if (isSettingsMode) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.response_style_back),
                            tint = CreamDim,
                        )
                    }
                    Text(
                        text = stringResource(R.string.response_style_setting_title),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = CanelaFontFamily,
                        color = Gold,
                        modifier = Modifier.weight(1f),
                    )
                }
            } else {
                Spacer(Modifier.statusBarsPadding())
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    repeat(4) { i ->
                        val isActive = i == 2
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 3.dp)
                                .height(8.dp)
                                .width(if (isActive) 24.dp else 8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (isActive) Gold else CreamDim.copy(alpha = 0.3f)),
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(8.dp))

                val titlePrefix = stringResource(R.string.response_style_onboarding_title_prefix)
                val titleEmphasis = stringResource(R.string.response_style_onboarding_title_emphasis)
                Text(
                    text = buildAnnotatedString {
                        append(titlePrefix)
                        append(" ")
                        withStyle(
                            SpanStyle(
                                color = Gold,
                                fontStyle = FontStyle.Italic,
                                fontWeight = FontWeight.SemiBold,
                            ),
                        ) {
                            append(titleEmphasis)
                        }
                    },
                    fontSize = 24.sp,
                    fontFamily = CanelaFontFamily,
                    color = CreamText,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = if (isSettingsMode) {
                        stringResource(R.string.response_style_settings_subtitle)
                    } else {
                        stringResource(R.string.response_style_onboarding_subtitle)
                    },
                    fontSize = 14.sp,
                    color = CreamDim,
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(28.dp))

                styleOptions.forEach { option ->
                    ResponseStyleCard(
                        option = option,
                        isSelected = selected == option.key,
                        onSelect = {
                            // iOS parity (ResponseStyleOnboardingView.swift:134):
                            // light haptic on each card tap.
                            haptic.light()
                            viewModel.select(option.key)
                        },
                    )
                    Spacer(Modifier.height(14.dp))
                }

                Spacer(Modifier.height(12.dp))

                // iOS parity (ResponseStyleOnboardingView.swift:117-119): Continue
                // button uses the premium gold gradient — ShimmerButton matches it.
                ShimmerButton(
                    text = if (isSettingsMode) {
                        stringResource(R.string.response_style_save_action)
                    } else {
                        stringResource(R.string.response_style_continue_action)
                    },
                    onClick = {
                        haptic.premiumContinue()
                        viewModel.persistSelection()
                        onContinue()
                    },
                )

                Spacer(Modifier.height(40.dp))
            }
        }
        }
    }
}

@Composable
private fun ResponseStyleCard(
    option: ResponseStyleOption,
    isSelected: Boolean,
    onSelect: () -> Unit,
) {
    // iOS parity (ResponseStyleOnboardingView.swift:135, 163, 214):
    // spring animation (response 0.25) on selection state transitions.
    val cardSpring = spring<Color>(stiffness = Spring.StiffnessMedium)
    val dpSpring = spring<androidx.compose.ui.unit.Dp>(stiffness = Spring.StiffnessMedium)
    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected) Gold.copy(alpha = 0.06f) else NavySurface,
        animationSpec = cardSpring,
        label = "card_background",
    )
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) Gold.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.06f),
        animationSpec = cardSpring,
        label = "card_border",
    )
    val radioBorderColor by animateColorAsState(
        targetValue = if (isSelected) Gold else CreamDim.copy(alpha = 0.5f),
        animationSpec = cardSpring,
        label = "radio_border",
    )
    val radioInnerSize by animateDpAsState(
        targetValue = if (isSelected) 14.dp else 0.dp,
        animationSpec = dpSpring,
        label = "radio_inner_size",
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(backgroundColor)
            .border(
                width = 1.5.dp,
                color = borderColor,
                shape = RoundedCornerShape(16.dp),
            )
            .clickable { onSelect() }
            .padding(20.dp),
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // iOS parity (ResponseStyleOnboardingView.swift:142-144): gold-tinted
                // SF Symbol leading the label. We use the matching Material icon.
                Icon(
                    imageVector = option.icon,
                    contentDescription = null,
                    tint = Gold,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(option.labelRes),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = CanelaFontFamily,
                    color = CreamText,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .border(
                            width = 1.5.dp,
                            color = radioBorderColor,
                            shape = CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (radioInnerSize > 0.dp) {
                        Box(
                            modifier = Modifier
                                .size(radioInnerSize)
                                .clip(CircleShape)
                                .background(Gold),
                        )
                    }
                }
            }

            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(option.taglineRes),
                fontSize = 13.sp,
                color = CreamDim,
                lineHeight = 18.sp,
            )

            Spacer(Modifier.height(16.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.Black.copy(alpha = 0.25f))
                    .border(0.5.dp, Color.White.copy(alpha = 0.04f), RoundedCornerShape(10.dp))
                    .padding(14.dp),
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.example_label),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = CreamDim.copy(alpha = 0.5f),
                        letterSpacing = 1.2.sp,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.example_question_career),
                        fontSize = 11.sp,
                        fontStyle = FontStyle.Italic,
                        color = CreamDim.copy(alpha = 0.6f),
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = stringResource(option.exampleResponseRes),
                        fontSize = 12.sp,
                        color = CreamText.copy(alpha = 0.75f),
                        lineHeight = 17.sp,
                    )
                }
            }
        }
    }
}
