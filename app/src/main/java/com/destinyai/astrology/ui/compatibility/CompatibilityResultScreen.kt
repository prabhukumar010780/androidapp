package com.destinyai.astrology.ui.compatibility

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.destinyai.astrology.domain.model.CompatibilityResult
import com.destinyai.astrology.domain.model.KutaDetail
import com.destinyai.astrology.ui.theme.CosmicBackground
import com.destinyai.astrology.ui.theme.CreamDim
import com.destinyai.astrology.ui.theme.CreamText
import com.destinyai.astrology.ui.theme.Gold
import com.destinyai.astrology.ui.theme.NavySurface
import com.destinyai.astrology.ui.theme.NavyVariant
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun CompatibilityResultScreen(
    result: CompatibilityResult,
    onBack: () -> Unit,
    onNewAnalysis: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showFullReport by remember { mutableStateOf(false) }
    var showMangalDetail by remember { mutableStateOf(false) }
    var showKalsarpaDetail by remember { mutableStateOf(false) }
    var showYogasDetail by remember { mutableStateOf(false) }
    var showAskDestiny by remember { mutableStateOf(false) }
    var askDestinyPrompt by remember { mutableStateOf<String?>(null) }
    var selectedKuta by remember { mutableStateOf<KutaDetail?>(null) }

    CosmicBackground(modifier = modifier) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                ResultHeader(
                    boyName = result.boyName,
                    girlName = result.girlName,
                    onBack = onBack,
                    onNewAnalysis = onNewAnalysis,
                )

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                ) {
                    // Hero: orbit + gauge
                    OrbitAshtakootView(
                        kutas = result.kutas,
                        totalScore = result.adjustedScore ?: result.totalScore,
                        rawScore = result.totalScore,
                        maxScore = result.maxScore,
                        boyName = result.boyName,
                        girlName = result.girlName,
                        selectedKuta = selectedKuta,
                        onKutaSelected = { selectedKuta = it },
                    )

                    Spacer(Modifier.height(8.dp))

                    RecommendationBanner(
                        result = result,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )

                    Spacer(Modifier.height(16.dp))

                    // Glass grid below recommendation
                    AshtakootGlassGrid(
                        kutas = result.kutas,
                        selectedKuta = selectedKuta,
                        onKutaSelected = { selectedKuta = it },
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )

                    Spacer(Modifier.height(20.dp))

                    Text(
                        text = "System Checks",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = Gold.copy(alpha = 0.7f),
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )

                    Spacer(Modifier.height(8.dp))

                    val mangalStatus = deriveMangalStatus(result)
                    DoshaStatusRow(
                        title = "Mangal Dosha",
                        iconLabel = "🔥",
                        statusText = mangalStatus.first,
                        statusColor = mangalStatus.second,
                        onClick = { showMangalDetail = true },
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )

                    Spacer(Modifier.height(8.dp))

                    val kalsarpaStatus = deriveKalsarpaStatus(result)
                    DoshaStatusRow(
                        title = "Kaal Sarp Dosha",
                        iconLabel = "🌪",
                        statusText = kalsarpaStatus.first,
                        statusColor = kalsarpaStatus.second,
                        onClick = { showKalsarpaDetail = true },
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )

                    Spacer(Modifier.height(8.dp))

                    DoshaStatusRow(
                        title = "Additional Yogas",
                        iconLabel = "✨",
                        statusText = "View All",
                        statusColor = CreamDim,
                        onClick = { showYogasDetail = true },
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )

                    Spacer(Modifier.height(20.dp))

                    ShimmerButton(
                        text = "View Full Report",
                        onClick = { showFullReport = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                    )

                    Spacer(Modifier.height(80.dp))
                }
            }

            // Custom gold FAB
            FloatingContextButton(
                onClick = {
                    askDestinyPrompt = null
                    showAskDestiny = true
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(20.dp),
            )

            // Tooltip overlay — above scroll content
            if (selectedKuta != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                        ) { selectedKuta = null },
                    contentAlignment = Alignment.Center,
                ) {
                    selectedKuta?.let { kuta ->
                        OrbitTooltipView(
                            kuta = kuta,
                            boyName = result.boyName,
                            girlName = result.girlName,
                            onDismiss = { selectedKuta = null },
                            onClassicalAnalysis = { prompt ->
                                selectedKuta = null
                                askDestinyPrompt = prompt
                                showAskDestiny = true
                            },
                        )
                    }
                }
            }
        }
    }

    if (showMangalDetail) {
        MangalDoshaScreen(
            boyData = result.mangalBoyData,
            girlData = result.mangalGirlData,
            boyName = result.boyName,
            girlName = result.girlName,
            mangalCompatibility = result.mangalCompatibility,
            onBack = { showMangalDetail = false },
        )
    }

    if (showKalsarpaDetail) {
        KalsarpaDoshaScreen(
            boyData = result.kalsarpaBoyData,
            girlData = result.kalsarpaGirlData,
            boyName = result.boyName,
            girlName = result.girlName,
            onBack = { showKalsarpaDetail = false },
        )
    }

    if (showYogasDetail) {
        AdditionalYogasScreen(
            boyYogas = result.yogasBoyData,
            girlYogas = result.yogasGirlData,
            boyName = result.boyName,
            girlName = result.girlName,
            onBack = { showYogasDetail = false },
        )
    }

    if (showFullReport) {
        FullReportScreen(
            result = result,
            onBack = { showFullReport = false },
        )
    }

    if (showAskDestiny) {
        AskDestinyDialog(
            boyName = result.boyName,
            girlName = result.girlName,
            summary = result.summary,
            suggestions = result.followUpSuggestions,
            initialPrompt = askDestinyPrompt,
            onDismiss = { showAskDestiny = false },
        )
    }
}

// ─── Header ──────────────────────────────────────────────────────────────────

@Composable
private fun ResultHeader(
    boyName: String,
    girlName: String,
    onBack: () -> Unit,
    onNewAnalysis: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = CreamText)
        }
        Text(
            text = "$boyName ♡ $girlName",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = CreamText,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
        )
        TextButton(onClick = onNewAnalysis) {
            Text("New", color = Gold, style = MaterialTheme.typography.labelMedium)
        }
    }
}

// ─── Orbit Ashtakoot View ─────────────────────────────────────────────────────

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
        )

        kutas.forEachIndexed { index, kuta ->
            val angleDeg = index * (360.0 / 8.0) - 90.0
            val angleRad = (angleDeg * PI / 180.0).toFloat()
            val xOffset = with(density) { (orbitRadiusPx * cos(angleRad)).toDp() }
            val yOffset = with(density) { (orbitRadiusPx * sin(angleRad)).toDp() }

            PlanetBubble(
                kuta = kuta,
                isSelected = selectedKuta?.key == kuta.key,
                onSelect = {
                    onKutaSelected(if (selectedKuta?.key == kuta.key) null else kuta)
                },
                modifier = Modifier.offset(x = xOffset, y = yOffset),
            )
        }
    }
}

// ─── Planet Bubble ────────────────────────────────────────────────────────────

private val kutaIcons = mapOf(
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
private fun PlanetBubble(
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

// ─── Synergy Gauge ────────────────────────────────────────────────────────────

@Composable
fun SynergyGaugeView(
    score: Int,
    rawScore: Int,
    maxScore: Int,
    boyName: String,
    girlName: String,
    size: Dp = 160.dp,
    modifier: Modifier = Modifier,
) {
    val hasAdjustment = score != rawScore
    val progress = if (maxScore > 0) score.toFloat() / maxScore.toFloat() else 0f

    val arcColor = when {
        progress >= 0.75f -> Color(0xFF48BB78)
        progress >= 0.5f -> Gold
        else -> Color(0xFFFC8181)
    }

    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 1200, easing = EaseOut),
        label = "arc_progress",
    )

    Box(
        modifier = modifier.size(size),
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
                fontSize = (size.value * 0.28f).sp,
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
    }
}

// ─── Orbit Tooltip View ───────────────────────────────────────────────────────

@Composable
private fun OrbitTooltipView(
    kuta: KutaDetail,
    boyName: String,
    girlName: String,
    onDismiss: () -> Unit,
    onClassicalAnalysis: (String) -> Unit,
) {
    val successColor = Color(0xFF48BB78)
    val errorColor = Color(0xFFFC8181)
    val statusColor = when (kuta.statusTier) { 1 -> successColor; 2 -> errorColor; else -> Gold }
    val displayScore = "${formatScore(kuta.displayScore)}/${formatScore(kuta.maxScore)}"

    Column(
        modifier = Modifier
            .width(300.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(NavySurface)
            .border(1.dp, statusColor.copy(alpha = 0.35f), RoundedCornerShape(18.dp))
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
            ) { /* consume — prevent dismiss */ },
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(statusColor.copy(alpha = 0.1f))
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(kutaIcons[kuta.key] ?: "✦", fontSize = 18.sp)
            Spacer(Modifier.width(10.dp))
            Text(
                text = kuta.label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = CreamText,
                modifier = Modifier.weight(1f),
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(statusColor.copy(alpha = 0.2f))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Text(
                    text = displayScore,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = statusColor,
                )
            }
        }

        // Dosha indicator
        if (kuta.doshaPresent) {
            val doshaColor = if (kuta.doshaCancelled) successColor else errorColor
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(doshaColor.copy(alpha = 0.06f))
                    .padding(horizontal = 16.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (kuta.doshaCancelled) "✓ Dosha cancelled" else "⚠ Dosha active",
                    style = MaterialTheme.typography.labelSmall,
                    color = doshaColor,
                )
                kuta.cancellationReason?.let { reason ->
                    Text(
                        text = " — $reason",
                        style = MaterialTheme.typography.labelSmall,
                        color = CreamDim,
                    )
                }
            }
        }

        // Description
        Text(
            text = KutaTextBuilder.description(kuta.key),
            style = MaterialTheme.typography.bodySmall,
            color = CreamDim,
            lineHeight = 20.sp,
            modifier = Modifier.padding(16.dp),
        )

        HorizontalDivider(color = Gold.copy(alpha = 0.12f))

        // Classical analysis CTA
        TextButton(
            onClick = {
                val prompt = "Tell me more about the ${kuta.label} compatibility between $boyName and $girlName"
                onClassicalAnalysis(prompt)
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
        ) {
            Text(
                text = "See classical analysis →",
                style = MaterialTheme.typography.labelMedium,
                color = Gold,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

// ─── Ashtakoot Glass Grid ─────────────────────────────────────────────────────

@Composable
private fun AshtakootGlassGrid(
    kutas: List<KutaDetail>,
    selectedKuta: KutaDetail?,
    onKutaSelected: (KutaDetail?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = "Ashtakoot Kutas",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = Gold.copy(alpha = 0.7f),
            modifier = Modifier.padding(bottom = 10.dp),
        )
        kutas.chunked(2).forEach { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 8.dp),
            ) {
                row.forEach { kuta ->
                    GlassPill(
                        kuta = kuta,
                        isSelected = selectedKuta?.key == kuta.key,
                        onClick = {
                            onKutaSelected(if (selectedKuta?.key == kuta.key) null else kuta)
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun GlassPill(
    kuta: KutaDetail,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val successColor = Color(0xFF48BB78)
    val errorColor = Color(0xFFFC8181)
    val statusColor = when (kuta.statusTier) { 1 -> successColor; 2 -> errorColor; else -> Gold }
    val borderColor = if (isSelected) statusColor else statusColor.copy(alpha = 0.2f)
    val bgColor = if (isSelected) statusColor.copy(alpha = 0.08f) else NavySurface

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(statusColor),
        )
        Spacer(Modifier.width(6.dp))
        Text(kutaIcons[kuta.key] ?: "✦", fontSize = 13.sp)
        Spacer(Modifier.width(5.dp))
        Text(
            text = kuta.label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = CreamText,
            fontSize = 11.sp,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "${formatScore(kuta.displayScore)}/${formatScore(kuta.maxScore)}",
            style = MaterialTheme.typography.labelSmall,
            color = statusColor,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
        )
    }
}

// ─── Recommendation Banner ────────────────────────────────────────────────────

@Composable
fun RecommendationBanner(
    result: CompatibilityResult,
    modifier: Modifier = Modifier,
) {
    val successColor = Color(0xFF48BB78)
    val errorColor = Color(0xFFFC8181)
    val borderColor = if (result.isRecommended) successColor else errorColor
    val displayScore = result.adjustedScore ?: result.totalScore

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, borderColor.copy(alpha = 0.3f), RoundedCornerShape(14.dp)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(if (result.isRecommended) successColor.copy(alpha = 0.1f) else errorColor.copy(alpha = 0.1f))
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (result.isRecommended) "✓" else "⚠",
                fontSize = 18.sp,
                color = borderColor,
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = if (result.isRecommended) "Recommended" else "Not Recommended",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = borderColor,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "$displayScore/${result.maxScore}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = CreamText,
            )
        }

        Column(modifier = Modifier.padding(14.dp)) {
            if (result.isRecommended) {
                Text(
                    text = AffirmationBuilder.affirmationText(
                        kutas = result.kutas,
                        adjustedScore = result.adjustedScore,
                        totalScore = result.totalScore,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = CreamDim,
                    lineHeight = 20.sp,
                )
            } else {
                if (result.rejectionReasons.isNotEmpty()) {
                    Text(
                        text = "Not recommended because:",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = errorColor,
                    )
                    Spacer(Modifier.height(8.dp))
                    result.rejectionReasons.forEach { reason ->
                        Row(
                            modifier = Modifier.padding(bottom = 6.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Text("⚠ ", color = errorColor, fontSize = 11.sp)
                            Text(
                                text = reason,
                                style = MaterialTheme.typography.bodySmall,
                                color = CreamDim,
                                lineHeight = 18.sp,
                            )
                        }
                    }
                }
            }

            val cancelledCount = result.doshaSummary?.cancelledCount ?: 0
            if (cancelledCount > 0) {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = successColor.copy(alpha = 0.2f))
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.Top) {
                    Text("✓ ", color = successColor, fontSize = 12.sp)
                    Text(
                        text = result.cancelledDoshasSummary
                            ?: "$cancelledCount dosha${if (cancelledCount == 1) "" else "s"} found and cancelled — ${if (cancelledCount == 1) "it doesn't" else "they don't"} affect this match.",
                        style = MaterialTheme.typography.bodySmall,
                        color = successColor.copy(alpha = 0.85f),
                        lineHeight = 18.sp,
                    )
                }
            }
        }
    }
}

// ─── Dosha Status Row ─────────────────────────────────────────────────────────

@Composable
fun DoshaStatusRow(
    title: String,
    iconLabel: String,
    statusText: String,
    statusColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.03f))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Gold.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(iconLabel, fontSize = 18.sp)
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = CreamText,
            )
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(statusColor.copy(alpha = 0.15f))
                    .border(1.dp, statusColor.copy(alpha = 0.3f), RoundedCornerShape(50))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(statusColor),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = statusText.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = statusColor,
                    fontSize = 10.sp,
                )
            }
        }

        Text("›", color = CreamDim, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }
}

// ─── Shimmer Button ───────────────────────────────────────────────────────────

@Composable
fun ShimmerButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(52.dp),
        shape = RoundedCornerShape(26.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            contentColor = Color(0xFF0D0D1A),
        ),
        contentPadding = PaddingValues(0.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(listOf(Gold, Color(0xFFF5D060), Gold)),
                    RoundedCornerShape(26.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF0D0D1A),
                fontSize = 16.sp,
            )
        }
    }
}

// ─── Floating Context Button ──────────────────────────────────────────────────

@Composable
fun FloatingContextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val buttonScale by animateFloatAsState(
        targetValue = if (isPressed) 0.88f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "fab_scale",
    )

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        // Glow aura behind button
        Box(
            modifier = Modifier
                .size(72.dp)
                .drawBehind {
                    drawCircle(color = Gold.copy(alpha = 0.20f), radius = size.minDimension / 2)
                    drawCircle(color = Gold.copy(alpha = 0.10f), radius = size.minDimension / 2 * 0.75f)
                },
        )

        // Main gold circle button
        Box(
            modifier = Modifier
                .scale(buttonScale)
                .size(56.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(Color(0xFFF5D060), Gold),
                        start = Offset(0f, 0f),
                        end = Offset(100f, 100f),
                    )
                )
                .clickable(
                    indication = null,
                    interactionSource = interactionSource,
                    onClick = onClick,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Chat,
                contentDescription = "Ask Destiny",
                tint = Color(0xFF0D0D1A),
                modifier = Modifier.size(24.dp),
            )
        }

        // Outer ring
        Box(
            modifier = Modifier
                .size(68.dp)
                .border(1.dp, Gold.copy(alpha = 0.3f), CircleShape),
        )
    }
}

// ─── Ask Destiny Dialog ───────────────────────────────────────────────────────

@Composable
fun AskDestinyDialog(
    boyName: String,
    girlName: String,
    summary: String,
    suggestions: List<String>,
    initialPrompt: String?,
    onDismiss: () -> Unit,
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(NavySurface)
                .border(1.dp, Gold.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
                .padding(20.dp),
        ) {
            Text(
                text = if (initialPrompt != null) "Ask Destiny" else "Ask About $boyName & $girlName",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = CreamText,
            )
            Spacer(Modifier.height(12.dp))

            if (initialPrompt != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(NavyVariant)
                        .border(1.dp, Gold.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                        .padding(12.dp),
                ) {
                    Text(text = initialPrompt, style = MaterialTheme.typography.bodySmall, color = CreamText)
                }
            } else if (suggestions.isNotEmpty()) {
                Text(
                    text = "Suggested questions:",
                    style = MaterialTheme.typography.labelMedium,
                    color = CreamDim,
                )
                Spacer(Modifier.height(8.dp))
                suggestions.take(4).forEach { suggestion ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(NavyVariant)
                            .border(1.dp, Gold.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
                            .padding(12.dp),
                    ) {
                        Text(text = suggestion, style = MaterialTheme.typography.bodySmall, color = CreamText)
                    }
                }
            } else {
                Text(
                    text = summary.take(300),
                    style = MaterialTheme.typography.bodySmall,
                    color = CreamDim,
                    lineHeight = 20.sp,
                )
            }

            Spacer(Modifier.height(16.dp))
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                Text("Close", color = Gold)
            }
        }
    }
}

// ─── Helpers ──────────────────────────────────────────────────────────────────

private fun formatScore(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else "%.1f".format(value)

private fun deriveMangalStatus(result: CompatibilityResult): Pair<String, Color> {
    val successColor = Color(0xFF48BB78)
    val compat = result.mangalCompatibility
    val category = compat?.get("compatibility_category") as? String
    return if (category?.lowercase() == "excellent") {
        "Excellent" to successColor
    } else if (category != null) {
        category to Color(0xFFFFA500)
    } else {
        val boyHas = result.mangalBoyData?.hasMangalDosha ?: false
        val girlHas = result.mangalGirlData?.hasMangalDosha ?: false
        when {
            !boyHas && !girlHas -> "None" to successColor
            else -> "Present" to Color(0xFFFFA500)
        }
    }
}

private fun deriveKalsarpaStatus(result: CompatibilityResult): Pair<String, Color> {
    val successColor = Color(0xFF48BB78)
    val boyPresent = result.kalsarpaBoyData?.isPresent ?: false
    val girlPresent = result.kalsarpaGirlData?.isPresent ?: false
    return when {
        !boyPresent && !girlPresent -> "Clear" to successColor
        boyPresent && girlPresent -> "Both Present" to Color(0xFFFFA500)
        else -> "Moderate" to Color(0xFFFFD700)
    }
}

// ─── Affirmation Builder ──────────────────────────────────────────────────────

private object AffirmationBuilder {

    private val weightOrder = listOf("nadi", "bhakoot", "gana", "maitri", "yoni", "tara", "vashya")

    private val displayNames = mapOf(
        "nadi" to "Nadi", "bhakoot" to "Bhakoot", "gana" to "Gana",
        "maitri" to "Graha Maitri", "yoni" to "Yoni", "tara" to "Tara", "vashya" to "Vashya",
    )

    private val themes = mapOf(
        "nadi" to "health & progeny",
        "bhakoot" to "love & mutual respect",
        "gana" to "temperament & nature",
        "maitri" to "mental compatibility",
        "yoni" to "physical intimacy",
        "tara" to "destiny & fortune",
        "vashya" to "attraction & influence",
    )

    data class PerfectKoota(val displayName: String, val theme: String)

    fun affirmationText(kutas: List<KutaDetail>, adjustedScore: Int?, totalScore: Int): String {
        val score = adjustedScore ?: totalScore
        val perfect = topPerfectKootas(kutas)

        return when {
            perfect.size >= 3 -> {
                val top = perfect.take(3)
                val names = "${top[0].displayName}, ${top[1].displayName}, and ${top[2].displayName}"
                val t = "${top[0].theme}, ${top[1].theme}, and ${top[2].theme}"
                "$names all score perfectly — $t are exceptionally well aligned."
            }
            perfect.size == 2 -> {
                val names = "${perfect[0].displayName} and ${perfect[1].displayName}"
                val t = "${perfect[0].theme} and ${perfect[1].theme}"
                "$names both score perfectly — $t are strong foundations for this match."
            }
            perfect.size == 1 -> {
                val k = perfect[0]
                "${k.displayName} scores perfectly — strong ${k.theme}. Scoring $score/36, this is a solid match by Vedic standards."
            }
            else -> scoreTierSentence(score)
        }
    }

    private fun topPerfectKootas(kutas: List<KutaDetail>): List<PerfectKoota> {
        val result = mutableListOf<PerfectKoota>()
        for (key in weightOrder) {
            if (result.size >= 3) break
            val kuta = kutas.firstOrNull { it.key.lowercase() == key } ?: continue
            if (kuta.maxScore < 3 || kuta.score != kuta.maxScore) continue
            result.add(
                PerfectKoota(
                    displayName = displayNames[key] ?: key.replaceFirstChar { it.uppercase() },
                    theme = themes[key] ?: key,
                )
            )
        }
        return result
    }

    private fun scoreTierSentence(score: Int): String = when (score) {
        in 28..Int.MAX_VALUE -> "Scoring $score/36 — an excellent match by all Vedic standards."
        in 24..27 -> "Scoring $score/36 — a very good match with strong astrological foundations."
        in 20..23 -> "Scoring $score/36 — a good match with positive compatibility indicators."
        in 16..19 -> "Scoring $score/36 — an average match. Specific areas require mindful attention."
        in 12..15 -> "Scoring $score/36 — below average. Strong commitment and understanding needed."
        else -> "Scoring $score/36 — challenging match. Professional guidance is recommended."
    }
}

// ─── Kuta Text Builder ────────────────────────────────────────────────────────

private object KutaTextBuilder {
    fun description(key: String): String = when (key.lowercase()) {
        "varna" -> "Varna measures spiritual compatibility and the natural harmony between life purposes. When aligned, both partners support each other's growth on their respective paths."
        "vashya" -> "Vashya measures mutual attraction and the natural influence partners hold over each other. Strong Vashya creates a magnetic pull and deep sense of belonging."
        "tara" -> "Tara measures the compatibility of birth stars and its influence on health, fortune, and longevity of the relationship. Favorable Tara brings lasting blessings."
        "yoni" -> "Yoni measures physical and intimate compatibility, reflecting the depth of physical harmony and the potential for a fulfilling intimate connection."
        "maitri" -> "Graha Maitri measures mental compatibility and the friendship between Moon sign lords. Strong Maitri indicates intellectual harmony and mutual respect."
        "gana" -> "Gana measures compatibility of nature — divine, human, or demonic temperament. Matching Ganas create natural understanding and reduce friction in daily life."
        "bhakoot" -> "Bhakoot measures the emotional and mental wavelength between partners, reflecting how well emotions and moods naturally align in the relationship."
        "nadi" -> "Nadi is the most important kuta — measuring genetic and physical compatibility. It is essential for a healthy, long-lasting marriage and for healthy progeny."
        else -> "This kuta measures a key dimension of compatibility in the ancient Vedic tradition of Ashtakoot matching."
    }
}
