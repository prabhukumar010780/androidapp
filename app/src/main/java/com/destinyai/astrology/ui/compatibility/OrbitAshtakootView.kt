package com.destinyai.astrology.ui.compatibility

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.destinyai.astrology.domain.model.KutaDetail
import com.destinyai.astrology.ui.theme.Gold
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun OrbitAshtakootView(
    kutas: List<KutaDetail>,
    totalScore: Int,
    rawScore: Int,
    maxScore: Int,
    boyName: String,
    girlName: String,
    selectedKuta: KutaDetail? = null,
    onKutaSelected: (KutaDetail?) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val orbitRadius = 155.dp
    val bubbleSize = 64.dp
    val density = LocalDensity.current
    val orbitRadiusPx = with(density) { orbitRadius.toPx() }

    var hintVisible by remember { mutableStateOf(true) }
    val hasDoshaData = kutas.any { it.doshaPresent }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height((orbitRadius * 2) + bubbleSize + 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        // Orbit ring decorations
        Box(
            modifier = Modifier
                .size(orbitRadius * 2)
                .drawBehind {
                    drawCircle(
                        color = Gold.copy(alpha = 0.15f),
                        radius = size.minDimension / 2,
                        style = Stroke(width = 1.dp.toPx()),
                    )
                    drawCircle(
                        color = Gold.copy(alpha = 0.05f),
                        radius = size.minDimension / 2,
                        style = Stroke(width = 40.dp.toPx()),
                    )
                },
        )

        SynergyGaugeView(
            score = totalScore,
            rawScore = rawScore,
            maxScore = maxScore,
            boyName = boyName,
            girlName = girlName,
            size = 160.dp,
            showAvatars = false,
        )

        kutas.forEachIndexed { index, kuta ->
            val angleDeg = orbitAngleDegrees(index, kutas.size)
            val angleRad = (angleDeg * PI / 180.0).toFloat()
            val xOffset = with(density) { (orbitRadiusPx * cos(angleRad)).toDp() }
            val yOffset = with(density) { (orbitRadiusPx * sin(angleRad)).toDp() }

            PlanetBubble(
                kuta = kuta,
                isSelected = selectedKuta?.key == kuta.key,
                onSelect = {
                    hintVisible = false
                    onKutaSelected(if (selectedKuta?.key == kuta.key) null else kuta)
                },
                modifier = Modifier.offset(x = xOffset, y = yOffset),
            )
        }
    }
}

// ─── Planet Bubble ────────────────────────────────────────────────────────────

internal val kutaIcons = mapOf(
    "varna" to "🏛️",
    "vashya" to "❤️",
    "tara" to "⭐",
    "yoni" to "🔥",
    "maitri" to "🤝",
    "gana" to "🎭",
    "bhakoot" to "💕",
    "nadi" to "💗",
)

@Composable
internal fun PlanetBubble(
    kuta: KutaDetail,
    isSelected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val successColor = Color(0xFF48BB78)
    val errorColor = Color(0xFFFC8181)
    val statusColor = when (kuta.statusTier) { 1 -> successColor; 2 -> errorColor; else -> Gold }

    val displayScore = if (kuta.doshaPresent && kuta.doshaCancelled && kuta.adjustedScore != null)
        kuta.adjustedScore else kuta.score
    val scoreText = "${formatScore(displayScore)}/${formatScore(kuta.maxScore)}"

    val bubbleScale by animateFloatAsState(
        targetValue = if (isSelected) 1.12f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "bubble_scale",
    )

    Box(
        modifier = modifier
            .size(64.dp)
            .scale(bubbleScale)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onSelect,
            ),
        contentAlignment = Alignment.Center,
    ) {
        // Glow aura (outer)
        Box(
            modifier = Modifier
                .size(76.dp)
                .drawBehind {
                    drawCircle(
                        color = statusColor.copy(alpha = if (isSelected) 0.5f else 0.25f),
                        radius = size.minDimension / 2,
                        style = Stroke(width = 1.5.dp.toPx()),
                    )
                    if (isSelected) {
                        drawCircle(
                            color = statusColor.copy(alpha = 0.12f),
                            radius = size.minDimension / 2,
                        )
                    }
                },
        )
        // Glass sphere body
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF2E3342),
                            Color(0xFF1E2233),
                            Color(0xFF141824).copy(alpha = 0.6f),
                        ),
                        radius = 96f,
                    )
                )
                .border(
                    width = if (isSelected) 2.dp else 1.5.dp,
                    brush = Brush.linearGradient(
                        colors = if (isSelected)
                            listOf(statusColor, statusColor.copy(alpha = 0.5f))
                        else
                            listOf(Gold.copy(alpha = 0.6f), Gold.copy(alpha = 0.3f)),
                    ),
                    shape = CircleShape,
                ),
        )
        // Inner highlight
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.08f),
                            Color.Transparent,
                        ),
                        center = Offset(20f, 12f),
                        radius = 36f,
                    )
                ),
        )
        // Content: icon + score + label
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.offset(y = (-2).dp),
        ) {
            Text(
                text = kutaIcons[kuta.key] ?: "✦",
                fontSize = 11.sp,
            )
            Text(
                text = scoreText,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = if (kuta.doshaCancelled) Color(0xFF48BB78) else Gold.copy(alpha = 0.9f),
                fontSize = 9.sp,
                lineHeight = 11.sp,
            )
            Text(
                text = kuta.label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 6.sp,
                maxLines = 1,
            )
        }
        // Dosha badge (top-right)
        if (kuta.doshaPresent) {
            val badgeColor = if (kuta.doshaCancelled) Color(0xFF48BB78) else errorColor
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(badgeColor),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (kuta.doshaCancelled) "✓" else "!",
                    fontSize = 8.sp,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

// Pure geometry helpers — testable without Compose

internal fun orbitAngleDegrees(index: Int, total: Int): Double =
    index * (360.0 / total.coerceAtLeast(1)) - 90.0
