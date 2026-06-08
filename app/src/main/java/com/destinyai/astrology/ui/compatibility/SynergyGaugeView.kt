package com.destinyai.astrology.ui.compatibility

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.Icon
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.destinyai.astrology.R
import com.destinyai.astrology.ui.theme.AppTheme
import com.destinyai.astrology.ui.theme.CreamDim
import com.destinyai.astrology.ui.theme.Gold

@Composable
fun SynergyGaugeView(
    score: Int,
    rawScore: Int,
    maxScore: Int,
    boyName: String,
    girlName: String,
    size: Dp = 200.dp,
    showAvatars: Boolean = true,
    modifier: Modifier = Modifier,
) {
    // Issue 9: standardize on truncated-Int comparison for parity with iOS
    val hasAdjustment = score.toInt() != rawScore.toInt()
    val progress = if (maxScore > 0) score.toFloat() / maxScore.toFloat() else 0f
    val arcBrush = synergyArcBrush(progress.toDouble())

    // Issue 2: spring with damping 0.8, low stiffness, 200ms delay (matches iOS spring(response:1.2))
    var appear by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(200L)
        appear = true
    }
    val animatedProgress by animateFloatAsState(
        targetValue = if (appear) progress else 0f,
        animationSpec = spring(
            dampingRatio = 0.8f,
            stiffness = Spring.StiffnessLow,
        ),
        label = "arc_progress",
    )

    Column(
        modifier = modifier.semantics { contentDescription = "synergy_gauge_view" },
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

                // Issue 12: full 360° background track (matches iOS Circle().trim background)
                drawArc(
                    color = Gold.copy(alpha = 0.15f),
                    startAngle = startAngle,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = Size(this.size.width - inset * 2, this.size.height - inset * 2),
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                )
                // Issues 1 + 13: linear gradient brush with 0.8/0.7 alpha pair (matches iOS LinearGradient)
                drawArc(
                    brush = arcBrush,
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
                    modifier = Modifier.semantics { contentDescription = "synergy_score_value" },
                )
                Text(
                    // Issue 18: localized "out of N" format
                    text = stringResource(R.string.out_of_max_score_format, maxScore),
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
                // Issues 3 + 10: localized labels via stringResource
                Text(
                    text = if (hasAdjustment) {
                        stringResource(R.string.adjusted_label).uppercase()
                    } else {
                        stringResource(R.string.compatibility_label).uppercase()
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = Gold.copy(alpha = 0.7f),
                    fontSize = (size.value * 0.05f).sp,
                    letterSpacing = 1.sp,
                )
                Text(
                    text = stringResource(R.string.score).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = Gold.copy(alpha = 0.7f),
                    fontSize = (size.value * 0.05f).sp,
                    letterSpacing = 1.sp,
                )
            }

            // Issue 4 + 16: SF-Symbol-equivalent icon + localized hint key
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .offset(y = size * 0.48f)
                    .semantics { contentDescription = "tap_orbs_hint" },
            ) {
                Icon(
                    imageVector = Icons.Outlined.TouchApp,
                    contentDescription = null,
                    tint = Gold.copy(alpha = 0.6f),
                    modifier = Modifier.size(11.dp),
                )
                Text(
                    text = stringResource(R.string.tap_orbs_hint),
                    style = MaterialTheme.typography.labelSmall.copy(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic),
                    color = CreamDim.copy(alpha = 0.8f),
                    fontSize = 11.sp,
                )
            }
        }

        if (showAvatars) {
            // Issue 6: negative-spacing overlap matching iOS VStack(spacing: -15)
            Row(modifier = Modifier.offset(y = (-15).dp)) {
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
        // Issue 5: theme token instead of hardcoded 0xFF0D0D1A
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(AppTheme.colors.mainBackground),
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

// Issue 11: firstName helper for parity with iOS (currently unused but present)
internal fun firstName(fullName: String): String =
    fullName.split(' ').firstOrNull()?.takeIf { it.isNotEmpty() } ?: fullName

internal fun synergyArcColor(percentage: Double): Color = when {
    percentage >= 0.75 -> Color(0xFF48BB78)
    percentage >= 0.5 -> Gold
    // Issue 14: align lower-bucket hex with iOS AppTheme.Colors.error (#FF5252)
    else -> Color(0xFFFF5252)
}

// Issues 1 + 13: linear gradient brush with 0.8/0.7 alpha pair, mirroring iOS arcGradient
internal fun synergyArcBrush(percentage: Double): Brush {
    val base = synergyArcColor(percentage)
    return Brush.linearGradient(
        colors = listOf(base.copy(alpha = 0.8f), base.copy(alpha = 0.7f)),
    )
}

internal fun synergyArcColorLabel(percentage: Double): String = when {
    percentage >= 0.75 -> "green"
    percentage >= 0.5 -> "gold"
    else -> "red"
}
