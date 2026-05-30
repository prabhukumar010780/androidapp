package com.destinyai.astrology.ui.compatibility

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Public
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
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
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.launch

@Composable
fun CompatibilityResultScreen(
    result: CompatibilityResult,
    onBack: () -> Unit,
    onNewAnalysis: () -> Unit,
    isFromComparison: Boolean = false,
    onCharts: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var showFullReport by remember { mutableStateOf(false) }
    var showMangalDetail by remember { mutableStateOf(false) }
    var showKalsarpaDetail by remember { mutableStateOf(false) }
    var showYogasDetail by remember { mutableStateOf(false) }
    var showAskDestiny by remember { mutableStateOf(false) }
    var showHistorySheet by remember { mutableStateOf(false) }
    var askDestinyPrompt by remember { mutableStateOf<String?>(null) }
    var selectedKuta by remember { mutableStateOf<KutaDetail?>(null) }

    var contentVisible by remember { mutableStateOf(false) }
    val contentAlpha by animateFloatAsState(
        targetValue = if (contentVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 400),
        label = "content_fade_in",
    )
    LaunchedEffect(Unit) { contentVisible = true }

    CosmicBackground(modifier = modifier.graphicsLayer { alpha = contentAlpha }) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                ResultHeader(
                    boyName = result.boyName,
                    girlName = result.girlName,
                    subtitle = resultScreenSubtitle(result.boyCity ?: "", result.girlCity ?: ""),
                    onBack = onBack,
                    onNewAnalysis = if (isFromComparison) null else onNewAnalysis,
                    onHistoryTap = { showHistorySheet = true },
                    onChartTap = onCharts,
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
            boyYogaData = result.boyYogaDoshaData
                ?: result.yogasBoyData?.let { com.destinyai.astrology.ui.compatibility.toYogaDoshaData(it) },
            girlYogaData = result.girlYogaDoshaData
                ?: result.yogasGirlData?.let { com.destinyai.astrology.ui.compatibility.toYogaDoshaData(it) },
            boyName = result.boyName,
            girlName = result.girlName,
            onBack = { showYogasDetail = false },
        )
    }

    if (showHistorySheet) {
        CompatibilityHistoryScreen(
            viewModel = androidx.hilt.navigation.compose.hiltViewModel(),
            onBack = { showHistorySheet = false },
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
            suggestions = if (isFromComparison) emptyList() else result.followUpSuggestions,
            initialPrompt = askDestinyPrompt,
            result = result,
            onDismiss = { showAskDestiny = false },
        )
    }
}

// ─── Header ──────────────────────────────────────────────────────────────────

@Composable
private fun ResultHeader(
    boyName: String,
    girlName: String,
    subtitle: String = "",
    onBack: () -> Unit,
    onNewAnalysis: (() -> Unit)? = null,
    onHistoryTap: (() -> Unit)? = null,
    onChartTap: (() -> Unit)? = null,
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Gold)
            }
            if (onHistoryTap != null) {
                IconButton(onClick = onHistoryTap) {
                    Icon(Icons.Filled.History, contentDescription = "History", tint = Gold)
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "${firstNameFrom(boyName)} ♡ ${firstNameFrom(girlName)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Gold,
                    textAlign = TextAlign.Center,
                )
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = CreamDim,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            if (onChartTap != null) {
                IconButton(onClick = onChartTap) {
                    Icon(Icons.Filled.Public, contentDescription = "Charts", tint = Gold)
                }
            }
            if (onNewAnalysis != null) {
                IconButton(onClick = onNewAnalysis) {
                    Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = "New Analysis", tint = Gold)
                }
            }
        }
    }
}

// OrbitAshtakootView and PlanetBubble live in OrbitAshtakootView.kt

// SynergyGaugeView lives in SynergyGaugeView.kt

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
            text = kutaRichDescription(kuta, boyName, girlName),
            style = MaterialTheme.typography.bodySmall,
            color = CreamDim,
            lineHeight = 20.sp,
            modifier = Modifier.padding(16.dp),
        )

        HorizontalDivider(color = Gold.copy(alpha = 0.12f))

        // Classical analysis CTA
        TextButton(
            onClick = {
                val prompt = kutaClassicalPrompt(kuta, boyName, girlName)
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

// AshtakootGlassGrid and GlassPill live in AshtakootGlassGrid.kt

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
                modifier = Modifier.semantics { contentDescription = "compat_result_score" },
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
                                val prefix = rejectionReasonPrefix(reason)
                                val scoreHighlight = rejectionReasonScoreHighlight(reason)
                                if (prefix != null) {
                                    val suffix = reason.removePrefix(prefix).trimStart(' ', '—', ' ')
                                    val scoreInSuffix = scoreHighlight?.second
                                    androidx.compose.foundation.text.BasicText(
                                        text = buildAnnotatedString {
                                            withStyle(androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.Bold, color = CreamText)) {
                                                append(prefix)
                                            }
                                            if (suffix.isNotEmpty()) {
                                                if (scoreInSuffix != null && suffix.contains(scoreInSuffix)) {
                                                    val parts = suffix.split(scoreInSuffix, limit = 2)
                                                    append(" — ${parts[0]}")
                                                    withStyle(androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.Bold, color = Color(0xFFED8936))) {
                                                        append(scoreInSuffix)
                                                    }
                                                    if (parts.size > 1) append(parts[1])
                                                } else {
                                                    append(" — $suffix")
                                                }
                                            }
                                        },
                                        style = MaterialTheme.typography.bodySmall.copy(color = CreamDim, lineHeight = 18.sp),
                                        modifier = Modifier.weight(1f),
                                    )
                                } else if (scoreHighlight != null) {
                                    val (before, score) = scoreHighlight
                                    val after = reason.removePrefix(before + score)
                                    androidx.compose.foundation.text.BasicText(
                                        text = buildAnnotatedString {
                                            append(before)
                                            withStyle(androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.Bold, color = Color(0xFFED8936))) {
                                                append(score)
                                            }
                                            append(after)
                                        },
                                        style = MaterialTheme.typography.bodySmall.copy(color = CreamDim, lineHeight = 18.sp),
                                        modifier = Modifier.weight(1f),
                                    )
                                } else {
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
            }

            val cancelledCount = result.doshaSummary?.cancelledCount ?: 0
            if (cancelledCount > 0) {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = successColor.copy(alpha = 0.2f))
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.Top) {
                    Text("✓ ", color = successColor, fontSize = 12.sp)
                    val cancelledDoshaText = result.cancelledDoshasSummary
                        ?: fallbackCancelledDoshaText(
                            cancelledDetails = result.doshaSummary?.details?.mapValues { it.value.cancelled } ?: emptyMap(),
                            cancelledCount = cancelledCount,
                        )
                    Text(
                        text = cancelledDoshaText,
                        style = MaterialTheme.typography.bodySmall,
                        color = successColor.copy(alpha = 0.85f),
                        lineHeight = 18.sp,
                    )
                }
            }
        }
    }
}

// DoshaStatusRow and DoshaStatusRowLabel live in DoshaStatusRow.kt

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

// FloatingContextButton lives in FloatingContextButton.kt

// ─── Ask Destiny Dialog ───────────────────────────────────────────────────────

@Composable
fun AskDestinyDialog(
    boyName: String,
    girlName: String,
    summary: String,
    suggestions: List<String>,
    initialPrompt: String?,
    result: CompatibilityResult? = null,
    onDismiss: () -> Unit,
    viewModel: CompatibilityViewModel = androidx.hilt.navigation.compose.hiltViewModel(),
) {
    val vmMessages by viewModel.followUpMessages.collectAsState()
    val isLoading by viewModel.isFollowUpLoading.collectAsState()
    var inputText by remember { mutableStateOf("") }
    val scrollState = androidx.compose.foundation.lazy.rememberLazyListState()
    val scope = rememberCoroutineScope()
    var scrollJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    val cosmicMessages = remember {
        listOf(
            "✦ Reading the stars…",
            "✦ Aligning planetary positions…",
            "✦ Calculating karmic bonds…",
            "✦ Consulting ancient wisdom…",
            "✦ Mapping celestial influences…",
            "✦ Analyzing doshas…",
            "✦ Weighing ashtakoot…",
            "✦ Tracing destiny threads…",
            "✦ Synthesizing cosmic signals…",
            "✦ Preparing your answer…",
        )
    }
    var cosmicStepIndex by remember { mutableIntStateOf(0) }

    LaunchedEffect(isLoading) {
        if (isLoading) {
            cosmicStepIndex = 0
            while (true) {
                kotlinx.coroutines.delay(1500)
                cosmicStepIndex++
            }
        }
    }

    fun requestScroll() {
        scrollJob?.cancel()
        scrollJob = scope.launch {
            kotlinx.coroutines.delay(80)
            if (vmMessages.isNotEmpty()) scrollState.animateScrollToItem(vmMessages.size - 1)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.clearFollowUpMessages()
        if (result != null) viewModel.setCompatibilityResult(result)
        if (initialPrompt != null) {
            viewModel.sendFollowUp(initialPrompt)
        }
    }

    LaunchedEffect(vmMessages.size, isLoading) {
        requestScroll()
    }

    val displaySuggestions = remember(vmMessages) {
        val lastAiSuggestions = vmMessages.lastOrNull { !it.isUser }?.suggestions ?: emptyList()
        followUpSuggestionsToDisplay(
            apiSuggestions = lastAiSuggestions,
            defaultSuggestions = suggestions.ifEmpty {
                listOf(
                    "What are the strongest points in this match?",
                    "What challenges should they watch out for?",
                    "How compatible are they for marriage?",
                )
            },
        )
    }

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .clip(RoundedCornerShape(20.dp))
                .background(NavySurface)
                .border(1.dp, Gold.copy(alpha = 0.3f), RoundedCornerShape(20.dp)),
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Ask About $boyName & $girlName",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = CreamText,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onDismiss) {
                    Text("Done", color = Gold)
                }
            }

            HorizontalDivider(color = Gold.copy(alpha = 0.15f))

            // Messages list
            androidx.compose.foundation.lazy.LazyColumn(
                state = scrollState,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 12.dp),
            ) {
                if (vmMessages.isEmpty() && !isLoading) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                "Ask anything about this compatibility match.",
                                style = MaterialTheme.typography.bodySmall,
                                color = CreamDim,
                                modifier = Modifier.padding(bottom = 4.dp),
                            )
                            displaySuggestions.forEach { suggestion ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(NavyVariant)
                                        .border(1.dp, Gold.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
                                        .clickable { viewModel.sendFollowUp(suggestion) }
                                        .padding(12.dp),
                                ) {
                                    Text(
                                        text = suggestion,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = CreamText,
                                    )
                                }
                            }
                        }
                    }
                } else {
                    items(vmMessages) { msg ->
                        AskChatBubble(isUser = msg.isUser, text = msg.text)
                    }
                    if (isLoading) {
                        item {
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.CenterStart,
                            ) {
                                Text(
                                    cosmicProgressKey(cosmicStepIndex, cosmicMessages),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Gold.copy(alpha = 0.7f),
                                    modifier = Modifier.padding(start = 8.dp, top = 4.dp),
                                )
                            }
                        }
                    }
                }
                // Follow-up suggestion chips after last AI reply
                if (vmMessages.isNotEmpty() && !isLoading) {
                    item {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(top = 8.dp),
                        ) {
                            displaySuggestions.forEach { suggestion ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(NavyVariant)
                                        .border(1.dp, Gold.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                                        .clickable { viewModel.sendFollowUp(suggestion) }
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                ) {
                                    Text(
                                        text = suggestion,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = CreamDim,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = Gold.copy(alpha = 0.1f))

            // Input bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = { Text("Ask a question…", color = CreamDim.copy(alpha = 0.5f)) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(20.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Gold,
                        unfocusedBorderColor = Gold.copy(alpha = 0.2f),
                        focusedTextColor = CreamText,
                        unfocusedTextColor = CreamText,
                        cursorColor = Gold,
                        unfocusedContainerColor = NavyVariant,
                        focusedContainerColor = NavyVariant,
                    ),
                    maxLines = 3,
                    singleLine = false,
                    enabled = !isLoading,
                )
                IconButton(
                    onClick = {
                        val trimmed = inputText.trim()
                        if (trimmed.isNotEmpty()) {
                            viewModel.sendFollowUp(trimmed)
                            inputText = ""
                        }
                    },
                    enabled = inputText.isNotBlank() && !isLoading,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(if (inputText.isNotBlank() && !isLoading) Gold else NavyVariant),
                ) {
                    Text("↑", fontSize = 20.sp, color = if (inputText.isNotBlank() && !isLoading) Color(0xFF0D0D1A) else CreamDim.copy(alpha = 0.4f))
                }
            }
        }
    }
}

@Composable
private fun AskChatBubble(isUser: Boolean, text: String) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .clip(
                    RoundedCornerShape(
                        topStart = 14.dp,
                        topEnd = 14.dp,
                        bottomStart = if (isUser) 14.dp else 4.dp,
                        bottomEnd = if (isUser) 4.dp else 14.dp,
                    )
                )
                .background(if (isUser) Gold.copy(alpha = 0.15f) else NavyVariant)
                .border(
                    1.dp,
                    if (isUser) Gold.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.06f),
                    RoundedCornerShape(
                        topStart = 14.dp,
                        topEnd = 14.dp,
                        bottomStart = if (isUser) 14.dp else 4.dp,
                        bottomEnd = if (isUser) 4.dp else 14.dp,
                    )
                )
                .padding(12.dp),
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isUser) Gold else CreamText,
                lineHeight = 20.sp,
            )
        }
    }
}

// ─── Helpers ──────────────────────────────────────────────────────────────────

internal fun compatibilityScoreLabel(score: Int, maxScore: Int): String = "$score/$maxScore"

internal fun adjustedScoreNote(totalScore: Int, maxScore: Int, adjustedScore: Int): String =
    "Raw: $totalScore/$maxScore · Adjusted: $adjustedScore"

internal fun rejectionReasonPrefix(reason: String): String? = when {
    reason.startsWith("Nadi Dosha is active") -> "Nadi Dosha is active"
    reason.startsWith("Bhakoot Dosha is active") -> "Bhakoot Dosha is active"
    reason.startsWith("Mangal Dosha incompatibility") -> "Mangal Dosha incompatibility"
    reason.startsWith("Adjusted Ashtakoot score") -> "Adjusted Ashtakoot score"
    else -> null
}

private val doshaDisplayNames = mapOf(
    "nadi" to "Nadi", "bhakoot" to "Bhakoot", "gana" to "Gana",
    "maitri" to "Maitri", "yoni" to "Yoni", "tara" to "Tara",
    "vashya" to "Vashya", "varna" to "Varna",
)

private val doshaKeyOrder = listOf("nadi", "bhakoot", "gana", "maitri", "yoni", "tara", "vashya", "varna")

internal fun fallbackCancelledDoshaText(
    cancelledDetails: Map<String, Boolean>,
    cancelledCount: Int,
): String {
    val names = doshaKeyOrder.mapNotNull { key ->
        if (cancelledDetails[key] == true) doshaDisplayNames[key] else null
    }
    return when {
        names.isEmpty() -> "$cancelledCount ${if (cancelledCount == 1) "dosha" else "doshas"} found and cancelled — ${if (cancelledCount == 1) "it doesn't" else "they don't"} affect this match."
        names.size == 1 -> "${names[0]} Dosha found and cancelled — it doesn't count against this match."
        else -> {
            val joined = names.dropLast(1).joinToString(", ") + " and ${names.last()}"
            "$joined Doshas found and cancelled — they don't count against this match."
        }
    }
}

internal fun formatScore(value: Double): String =
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

// AffirmationBuilder lives in AffirmationBuilder.kt

// ─── Pure accessibility helpers ───────────────────────────────────────────────

internal fun doshaRowContentDescription(title: String): String = when (title) {
    "Mangal Dosha" -> "mangal_dosha_row"
    "Kaal Sarp Dosha" -> "kalsarpa_dosha_row"
    else -> "dosha_row"
}

internal fun resultScreenSubtitle(boyCity: String, girlCity: String): String {
    val parts = listOf(boyCity, girlCity).filter { it.isNotBlank() }
    return parts.joinToString(" · ")
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

// ─── Follow-Up Pure Helpers ───────────────────────────────────────────────────

enum class FollowUpResponseStatus { SUCCESS, REDIRECT, BLOCKED, ERROR }

internal fun followUpResponseStatus(status: String?): FollowUpResponseStatus = when (status) {
    "success" -> FollowUpResponseStatus.SUCCESS
    "redirect" -> FollowUpResponseStatus.REDIRECT
    "blocked" -> FollowUpResponseStatus.BLOCKED
    else -> FollowUpResponseStatus.ERROR
}

internal fun followUpDisplayAnswer(status: String?, answer: String?, message: String?): String =
    when (followUpResponseStatus(status)) {
        FollowUpResponseStatus.SUCCESS -> answer ?: message ?: "No response received."
        FollowUpResponseStatus.BLOCKED -> message ?: "This topic is outside the scope of astrology compatibility."
        FollowUpResponseStatus.REDIRECT -> message ?: "Let me look up individual chart details."
        FollowUpResponseStatus.ERROR -> message ?: "Something went wrong. Please try again."
    }

internal fun followUpSuggestionsToDisplay(
    apiSuggestions: List<String>,
    defaultSuggestions: List<String>,
): List<String> {
    val source = if (apiSuggestions.isNotEmpty()) apiSuggestions else defaultSuggestions
    return source.take(4)
}

internal fun affirmationWeightOrder(): List<String> = AffirmationBuilder.weightOrder

internal fun cosmicProgressKey(index: Int, messages: List<String>): String =
    if (messages.isEmpty()) "" else messages[index % messages.size]

internal fun redirectTargetName(target: String?, boyName: String, girlName: String): String {
    if (target == null) return boyName
    val t = target.lowercase()
    return when {
        t.contains(girlName.lowercase()) || t.contains("girl") -> girlName
        else -> boyName
    }
}

// kutaRichDescription and kutaClassicalPrompt live in KutaTextBuilder.kt
