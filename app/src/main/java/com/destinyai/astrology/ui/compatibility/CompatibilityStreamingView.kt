package com.destinyai.astrology.ui.compatibility

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.destinyai.astrology.domain.model.AnalysisStep
import com.destinyai.astrology.ui.theme.CosmicBackground
import com.destinyai.astrology.ui.theme.CreamDim
import com.destinyai.astrology.ui.theme.CreamText
import com.destinyai.astrology.ui.theme.Gold
import com.destinyai.astrology.ui.theme.NavySurface

// Pure helpers — unit testable
internal fun stepIsCompleted(step: AnalysisStep, current: AnalysisStep): Boolean =
    step.ordinal < current.ordinal

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
        CosmicBackground {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("✨", fontSize = 16.sp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Analyzing Match",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = CreamText,
                        modifier = Modifier.weight(1f),
                    )
                }

                HorizontalDivider(color = Gold.copy(alpha = 0.3f), modifier = Modifier.padding(horizontal = 16.dp))

                // Steps
                Column(
                    modifier = Modifier
                        .weight(1f)
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
        isCompleted -> Color(0xFF48BB78)
        isActive -> Gold
        else -> CreamDim.copy(alpha = 0.4f)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isActive) Gold.copy(alpha = 0.1f) else Color.Transparent)
            .padding(horizontal = 20.dp, vertical = 10.dp),
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
                isCompleted -> Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF48BB78), modifier = Modifier.size(16.dp))
                isActive -> CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Gold, strokeWidth = 2.dp)
                else -> Text(step.icon, fontSize = 14.sp, color = statusColor)
            }
        }

        Spacer(Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = step.title,
                fontSize = 14.sp,
                fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isPending) CreamDim.copy(alpha = 0.4f) else CreamText,
            )
            if (isActive) {
                Text("Processing…", fontSize = 11.sp, color = Gold)
            }
        }

        if (isCompleted) {
            Text("✓", fontSize = 12.sp, color = Color(0xFF48BB78))
        }
    }
}

@Composable
private fun StreamingTextBox(text: String, modifier: Modifier = Modifier) {
    var cursorVisible by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(500)
            cursorVisible = !cursorVisible
        }
    }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .border(1.dp, Gold.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("🧠", fontSize = 12.sp)
            Spacer(Modifier.width(6.dp))
            Text("AI Analysis", fontSize = 11.sp, color = Gold, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(text, fontSize = 13.sp, color = CreamText, lineHeight = 18.sp, modifier = Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .height(14.dp)
                    .background(if (cursorVisible) Gold else Color.Transparent),
            )
        }
    }
}
