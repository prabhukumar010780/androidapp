package com.destinyai.astrology.ui.compatibility

import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.destinyai.astrology.ui.theme.CreamDim
import com.destinyai.astrology.ui.theme.Gold

@Composable
fun SynergyGaugeView(
    score: Int,
    rawScore: Int,
    maxScore: Int,
    boyName: String,
    girlName: String,
    size: Dp = 160.dp,
    showAvatars: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val hasAdjustment = score != rawScore
    val progress = if (maxScore > 0) score.toFloat() / maxScore.toFloat() else 0f
    val arcColor = synergyArcColor(progress.toDouble())

    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 1200, easing = EaseOut),
        label = "arc_progress",
    )

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.size(size),
            contentAlignment = Alignment.Center,
        ) {
            val strokeWidth = size.value * 0.08f
            androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                val sweepAngle = 270f
                val startAngle = 135f
                val inset = strokeWidth / 2

                drawArc(
                    color = Gold.copy(alpha = 0.15f),
                    startAngle = startAngle,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = Size(this.size.width - inset * 2, this.size.height - inset * 2),
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                )
                drawArc(
                    color = arcColor,
                    startAngle = startAngle,
                    sweepAngle = sweepAngle * animatedProgress,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = Size(this.size.width - inset * 2, this.size.height - inset * 2),
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "$score",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = Gold,
                    fontSize = (size.value * if (hasAdjustment) 0.28f else 0.32f).sp,
                )
                Text(
                    text = "/ $maxScore",
                    style = MaterialTheme.typography.bodySmall,
                    color = CreamDim,
                    fontSize = (size.value * 0.08f).sp,
                )
                if (hasAdjustment) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "$rawScore",
                            style = MaterialTheme.typography.labelSmall,
                            color = CreamDim.copy(alpha = 0.7f),
                            fontSize = (size.value * 0.065f).sp,
                            textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough,
                        )
                        Text(
                            text = " → $score",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Gold.copy(alpha = 0.8f),
                            fontSize = (size.value * 0.065f).sp,
                        )
                    }
                }
                Text(
                    text = if (hasAdjustment) "ADJUSTED" else "COMPATIBILITY",
                    style = MaterialTheme.typography.labelSmall,
                    color = Gold.copy(alpha = 0.7f),
                    fontSize = (size.value * 0.05f).sp,
                    letterSpacing = 1.sp,
                )
                Text(
                    text = "SCORE",
                    style = MaterialTheme.typography.labelSmall,
                    color = Gold.copy(alpha = 0.7f),
                    fontSize = (size.value * 0.05f).sp,
                    letterSpacing = 1.sp,
                )
            }

            // "Tap orbs" hint — positioned in the bottom gap of the 270° arc
            Text(
                text = "👆 tap orbs",
                style = MaterialTheme.typography.labelSmall.copy(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic),
                color = CreamDim.copy(alpha = 0.8f),
                fontSize = 11.sp,
                modifier = Modifier.offset(y = size * 0.48f),
            )
        }

        if (showAvatars) {
            Spacer(Modifier.height(4.dp))
            Row(modifier = Modifier.offset(x = (-8).dp)) {
                CircleAvatar(name = boyName, isPrimary = true)
                CircleAvatar(
                    name = girlName,
                    isPrimary = false,
                    modifier = Modifier.offset(x = (-15).dp),
                )
            }
        }
    }
}

@Composable
fun CircleAvatar(
    name: String,
    isPrimary: Boolean,
    modifier: Modifier = Modifier,
) {
    val initial = circleAvatarInitial(name)
    Box(
        modifier = modifier.size(64.dp),
        contentAlignment = Alignment.Center,
    ) {
        // Glow (primary only)
        if (isPrimary) {
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .clip(CircleShape)
                    .background(Gold.copy(alpha = 0.2f))
                    .blur(10.dp),
            )
        }
        // Dark background border separator
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(Color(0xFF0D0D1A)),
        )
        // Content circle
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(
                    if (isPrimary)
                        Brush.linearGradient(listOf(Gold.copy(alpha = 0.2f), Gold.copy(alpha = 0.05f)))
                    else
                        Brush.linearGradient(listOf(Color.White.copy(alpha = 0.1f), Color.White.copy(alpha = 0.05f)))
                )
                .border(
                    1.dp,
                    if (isPrimary) Gold.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.2f),
                    CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = initial,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = if (isPrimary) Gold else Color.White,
                fontSize = 24.sp,
            )
        }
    }
}

// Pure helpers — testable without Compose

internal fun circleAvatarInitial(name: String): String =
    if (name.isEmpty()) "?" else name.first().uppercaseChar().toString()

internal fun synergyArcColor(percentage: Double): Color = when {
    percentage >= 0.75 -> Color(0xFF48BB78)
    percentage >= 0.5 -> Gold
    else -> Color(0xFFFC8181)
}

internal fun synergyArcColorLabel(percentage: Double): String = when {
    percentage >= 0.75 -> "green"
    percentage >= 0.5 -> "gold"
    else -> "red"
}
