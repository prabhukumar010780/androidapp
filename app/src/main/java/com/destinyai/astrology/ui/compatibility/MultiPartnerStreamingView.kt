package com.destinyai.astrology.ui.compatibility

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.animation.core.*
import com.destinyai.astrology.R
import com.destinyai.astrology.domain.model.AnalysisStep
import com.destinyai.astrology.domain.model.ComparisonResult
import com.destinyai.astrology.domain.model.PartnerData
import com.destinyai.astrology.ui.theme.CreamDim
import com.destinyai.astrology.ui.theme.CreamText
import com.destinyai.astrology.ui.theme.Gold
import com.destinyai.astrology.ui.theme.NavySurface

// Pure helper — unit testable
internal fun multiPartnerProgressFraction(completed: Int, total: Int): Float =
    if (total <= 0) 0f else completed.toFloat() / total.toFloat()

@Composable
fun MultiPartnerStreamingView(
    isVisible: Boolean,
    partners: List<PartnerData>,
    completedResults: List<ComparisonResult>,
    currentPartnerIndex: Int,
    currentStep: AnalysisStep,
    totalPartners: Int,
    modifier: Modifier = Modifier,
) {
    if (!isVisible) return

    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        // iOS parity: explicit black 0.7 backdrop overlay (issue 1)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.7f)),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(NavySurface)
                    .border(1.dp, Gold.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                // Header
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // iOS parity: tinted gold sparkles icon instead of plain emoji (issue 2)
                    Icon(
                        imageVector = Icons.Filled.AutoAwesome,
                        contentDescription = null,
                        tint = Gold,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.compat_comparing_partners_format, totalPartners),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = CreamText,
                    )
                }

                // Progress bar
                MultiPartnerProgressBar(
                    fraction = multiPartnerProgressFraction(completedResults.size, totalPartners),
                    completedCount = completedResults.size,
                    totalCount = totalPartners,
                )

                // Partner list
                LazyColumn(
                    modifier = Modifier.heightIn(max = 300.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    itemsIndexed(partners) { index, partner ->
                        val isCompleted = completedResults.any { it.partner.id == partner.id }
                        val isActive = index == currentPartnerIndex && !isCompleted
                        MultiPartnerCard(
                            partner = partner,
                            index = index,
                            isCompleted = isCompleted,
                            isActive = isActive,
                            currentStep = currentStep,
                            completedResult = completedResults.firstOrNull { it.partner.id == partner.id },
                        )
                    }
                }

                Text(
                    stringResource(R.string.compat_this_may_take_a_moment),
                    fontSize = 13.sp,
                    color = CreamDim.copy(alpha = 0.6f),
                )
            }
        }
    }
}

@Composable
private fun MultiPartnerProgressBar(fraction: Float, completedCount: Int, totalCount: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.White.copy(alpha = 0.08f)),
            )
            Box(
                modifier = Modifier
                    .width(maxWidth * fraction)
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        Brush.horizontalGradient(listOf(Gold.copy(alpha = 0.8f), Gold)),
                    ),
            )
        }
        Text(
            "$completedCount of $totalCount complete",
            fontSize = 12.sp,
            color = CreamDim,
        )
    }
}

@Composable
private fun MultiPartnerCard(
    partner: PartnerData,
    index: Int,
    isCompleted: Boolean,
    isActive: Boolean,
    currentStep: AnalysisStep,
    completedResult: ComparisonResult?,
) {
    val isPending = !isCompleted && !isActive

    val pulseScale by animateFloatAsState(
        targetValue = if (isActive) 1.08f else 1.0f,
        animationSpec = if (isActive) infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ) else snap(),
        label = "avatar_pulse",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                when {
                    isActive -> Gold.copy(alpha = 0.1f)
                    else -> Color.White.copy(alpha = 0.03f)
                }
            )
            .border(
                1.dp,
                if (isActive) Gold.copy(alpha = 0.5f) else Color.Transparent,
                RoundedCornerShape(12.dp),
            )
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Avatar
        Box(
            modifier = Modifier
                .size(44.dp)
                .scale(if (isActive) pulseScale else 1.0f)
                .clip(CircleShape)
                .background(if (isActive) Gold else Color.White.copy(alpha = 0.08f))
                .then(
                    if (isActive) Modifier.border(2.dp, Gold.copy(alpha = 0.8f), CircleShape)
                    else Modifier
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = partner.name.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = if (isActive) Color(0xFF0D0D1A) else CreamText,
            )
            if (isActive) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .border(2.dp, Gold.copy(alpha = 0.5f), CircleShape),
                )
            }
        }

        // Name + status
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = partner.name.ifEmpty { "Partner ${index + 1}" },
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = if (isPending) CreamDim.copy(alpha = 0.5f) else CreamText,
            )
            when {
                isActive -> Text(currentStep.title, fontSize = 12.sp, color = Gold)
                isPending -> Text(stringResource(R.string.compat_pending_label), fontSize = 12.sp, color = CreamDim.copy(alpha = 0.5f))
            }
        }

        // Trailing indicator
        when {
            isCompleted && completedResult != null -> {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF48BB78), modifier = Modifier.size(20.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Gold.copy(alpha = 0.2f))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        Text("${completedResult.overallScore}/${completedResult.maxScore}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Gold)
                    }
                }
            }
            isActive -> CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Gold, strokeWidth = 2.dp)
            else -> Icon(Icons.Default.Schedule, contentDescription = null, tint = CreamDim.copy(alpha = 0.4f), modifier = Modifier.size(16.dp))
        }
    }
}
