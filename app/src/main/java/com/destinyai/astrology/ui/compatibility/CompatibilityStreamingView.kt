package com.destinyai.astrology.ui.compatibility

import androidx.compose.animation.*
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.destinyai.astrology.R
import com.destinyai.astrology.domain.model.AnalysisStep
import com.destinyai.astrology.ui.theme.AppTheme
import com.destinyai.astrology.ui.theme.CreamDim
import com.destinyai.astrology.ui.theme.CreamText
import com.destinyai.astrology.ui.theme.Gold
import com.destinyai.astrology.ui.theme.GoldDim

// Pure helpers — unit testable
internal fun stepIsCompleted(step: AnalysisStep, current: AnalysisStep): Boolean =
    step.ordinal < current.ordinal

private fun iconFor(step: AnalysisStep): ImageVector = when (step) {
    AnalysisStep.CALCULATING_CHARTS -> Icons.Filled.Public
    AnalysisStep.ASHTAKOOT_MATCHING -> Icons.Outlined.PieChart
    AnalysisStep.MANGAL_DOSHA -> Icons.Filled.Warning
    AnalysisStep.COLLECTING_YOGAS -> Icons.Filled.AutoAwesome
    AnalysisStep.GENERATING_ANALYSIS -> Icons.Filled.Psychology
    AnalysisStep.COMPLETE -> Icons.Filled.CheckCircle
}

@Composable
fun CompatibilityStreamingView(
    isVisible: Boolean,
    currentStep: AnalysisStep,
    streamingText: String,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier,
    ) {
        // Issue 1 + 6: iOS-parity dark scrim overlay + centered card framing.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.7f))
                .semantics { contentDescription = "compatibility_streaming_overlay" },
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(AppTheme.colors.cardBackground)
                    .border(
                        width = 1.dp,
                        color = GoldDim.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(20.dp),
                    )
                    .heightIn(max = 460.dp)
                    .semantics { contentDescription = "compatibility_streaming_card" },
            ) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(top = 20.dp, bottom = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Filled.AutoAwesome,
                        contentDescription = null,
                        tint = Gold,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.analyzing_match),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = CreamText,
                        modifier = Modifier.weight(1f),
                    )
                }

                HorizontalDivider(
                    color = Gold.copy(alpha = 0.3f),
                    modifier = Modifier.padding(horizontal = 16.dp),
                )

                // Steps
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 16.dp),
                ) {
                    AnalysisStep.values().forEach { step ->
                        StepRow(
                            step = step,
                            currentStep = currentStep,
                            isCompleted = stepIsCompleted(step, currentStep),
                        )
                    }

                    if (currentStep == AnalysisStep.GENERATING_ANALYSIS && streamingText.isNotEmpty()) {
                        StreamingTextBox(
                            text = streamingText,
                            modifier = Modifier
                                .padding(horizontal = 16.dp)
                                .padding(top = 8.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StepRow(
    step: AnalysisStep,
    currentStep: AnalysisStep,
    isCompleted: Boolean,
) {
    val isActive = step == currentStep
    val isPending = step.ordinal > currentStep.ordinal

    val statusColor = when {
        isCompleted -> AppTheme.colors.success
        isActive -> Gold
        else -> CreamDim.copy(alpha = 0.4f)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isActive) Gold.copy(alpha = 0.1f) else Color.Transparent)
            .padding(horizontal = 20.dp, vertical = 10.dp)
            .semantics { contentDescription = "analysis_step_row_${step.name.lowercase()}" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(statusColor.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            when {
                isCompleted -> Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = AppTheme.colors.success,
                    modifier = Modifier.size(16.dp),
                )
                isActive -> CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = Gold,
                    strokeWidth = 2.dp,
                )
                // Issue 2: tinted vector icons matching iOS SF Symbol parity.
                else -> Icon(
                    imageVector = iconFor(step),
                    contentDescription = null,
                    tint = statusColor,
                    modifier = Modifier.size(16.dp),
                )
            }
        }

        Spacer(Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(step.titleRes),
                fontSize = 14.sp,
                fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isPending) CreamDim.copy(alpha = 0.4f) else CreamText,
            )
            if (isActive) {
                // Issue 3: localized "Processing…" via stringResource.
                Text(
                    text = stringResource(R.string.processing),
                    fontSize = 11.sp,
                    color = Gold,
                )
            }
        }

        if (isCompleted) {
            Text("✓", fontSize = 12.sp, color = AppTheme.colors.success)
        }
    }
}

@Composable
private fun StreamingTextBox(text: String, modifier: Modifier = Modifier) {
    // Animated blinking cursor — iOS parity (easeInOut, repeatForever).
    val infinite = rememberInfiniteTransition(label = "streaming-cursor")
    val cursorAlpha by infinite.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 500),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "cursor-alpha",
    )

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            // Issue 5: shared inputBackground token (parity with iOS).
            .background(AppTheme.colors.inputBackground)
            .border(1.dp, Gold.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            .padding(12.dp)
            .semantics { contentDescription = "streaming_text_box" },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Issue 4: vector icon replaces brain emoji.
            Icon(
                imageVector = Icons.Filled.Psychology,
                contentDescription = null,
                tint = Gold,
                modifier = Modifier.size(12.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.ai_analysis_label),
                fontSize = 11.sp,
                color = Gold,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = text,
                fontSize = 13.sp,
                color = CreamText,
                lineHeight = 18.sp,
                modifier = Modifier.weight(1f),
            )
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .height(14.dp)
                    .background(Gold.copy(alpha = cursorAlpha)),
            )
        }
    }
}
