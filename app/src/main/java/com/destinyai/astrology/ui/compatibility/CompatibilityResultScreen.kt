package com.destinyai.astrology.ui.compatibility

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.view.View
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cyclone
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import com.destinyai.astrology.R
import com.destinyai.astrology.domain.model.CompatibilityResult
import com.destinyai.astrology.domain.model.KutaDetail
import com.destinyai.astrology.services.AppEvents
import com.destinyai.astrology.ui.theme.CanelaFontFamily
import com.destinyai.astrology.ui.theme.CosmicBackground
import com.destinyai.astrology.ui.theme.CreamDim
import com.destinyai.astrology.ui.theme.CreamText
import com.destinyai.astrology.ui.theme.Gold
import com.destinyai.astrology.ui.theme.NavyDeep
import com.destinyai.astrology.ui.theme.NavySurface
import com.destinyai.astrology.ui.theme.NavyVariant
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.DateFormat
import java.util.Date
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun CompatibilityResultScreen(
    result: CompatibilityResult,
    onBack: () -> Unit,
    onNewAnalysis: () -> Unit,
    isFromComparison: Boolean = false,
    onCharts: (() -> Unit)? = null,
    onOpenSettings: (() -> Unit)? = null,
    onOpenProfile: (() -> Unit)? = null,
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
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // iOS parity (CompatibilityResultView.swift:240): HapticManager.shared.play(.medium)
    // fires on the View Full Report tap.
    val haptics = LocalHapticFeedback.current

    // iOS parity (CompatibilityResultView.swift:328-333):
    // .onReceive(NotificationCenter.default.publisher(for: .openProfileSettings))
    // hooks the global notification bus and routes the result screen into the
    // Profile destination. On Android, AppEvents is the equivalent SharedFlow bus.
    val appEvents = remember(context) {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            CompatResultAppEventsEntryPoint::class.java,
        ).appEvents()
    }
    LaunchedEffect(appEvents, onOpenProfile) {
        appEvents.openProfileSettings.collect {
            onOpenProfile?.invoke()
        }
    }

    var contentVisible by remember { mutableStateOf(false) }
    val contentAlpha by animateFloatAsState(
        targetValue = if (contentVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 400),
        label = "content_fade_in",
    )
    LaunchedEffect(Unit) { contentVisible = true }

    // R2-CM12+CM13: Bitmap share helper
    fun shareBitmap() {
        scope.launch {
            val shareView = ComposeView(context).apply {
                setContent {
                    ShareCardView(
                        boyName = result.boyName,
                        girlName = result.girlName,
                        totalScore = result.totalScore,
                        maxScore = result.maxScore,
                        percentage = result.adjustedPercentage,
                        isRecommended = result.isRecommended,
                        adjustedScore = result.adjustedScore,
                        forSharing = true,
                    )
                }
            }
            val width = 1080
            val height = 1080
            shareView.measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
            )
            shareView.layout(0, 0, width, height)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)
            shareView.draw(canvas)

            val sessionTag = result.boyName.take(4) + result.girlName.take(4)
            val pngFile = File(context.cacheDir, "share-$sessionTag.png")
            val pdfFile = File(context.cacheDir, "share-$sessionTag.pdf")
            withContext(Dispatchers.IO) {
                FileOutputStream(pngFile).use { bitmap.compress(Bitmap.CompressFormat.PNG, 90, it) }
                // iOS parity (CompatibilityResultSheets.swift:107-152): include
                // a branded PDF alongside the PNG so partners get the same
                // bundle the iOS share sheet attaches.
                runCatching {
                    val pdfBytes = buildCompatibilityPdfBytes(result, emptyList())
                    FileOutputStream(pdfFile).use { it.write(pdfBytes) }
                }
            }
            val authority = "${context.packageName}.fileprovider"
            val pngUri: Uri = FileProvider.getUriForFile(context, authority, pngFile)
            val pdfUri: Uri? = if (pdfFile.exists() && pdfFile.length() > 0L) {
                runCatching { FileProvider.getUriForFile(context, authority, pdfFile) }.getOrNull()
            } else null

            val streamUris = ArrayList<Uri>().apply {
                add(pngUri)
                pdfUri?.let { add(it) }
            }

            val intent = Intent(if (streamUris.size > 1) Intent.ACTION_SEND_MULTIPLE else Intent.ACTION_SEND).apply {
                type = if (streamUris.size > 1) "*/*" else "image/png"
                if (streamUris.size > 1) {
                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, streamUris)
                } else {
                    putExtra(Intent.EXTRA_STREAM, pngUri)
                }
                putExtra(
                    Intent.EXTRA_TEXT,
                    "${result.boyName} ♡ ${result.girlName} — Analysed with Destiny AI Astrology",
                )
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(
                Intent.createChooser(intent, context.getString(R.string.compat_share_compat_chooser))
            )
        }
    }

    CosmicBackground(modifier = modifier.graphicsLayer { alpha = contentAlpha }) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
                ResultHeader(
                    boyName = result.boyName,
                    girlName = result.girlName,
                    subtitle = resultScreenSubtitle(result.boyCity ?: "", result.girlCity ?: ""),
                    onBack = onBack,
                    onNewAnalysis = if (isFromComparison) null else onNewAnalysis,
                    onHistoryTap = { showHistorySheet = true },
                    onChartTap = onCharts,
                    onShareTap = { shareBitmap() },
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

                    // iOS parity (CompatibilityResultView.swift:177-178): the
                    // AshtakootGlassGrid + "System Checks" header are
                    // intentionally omitted — kuta details live in the orbit
                    // tooltip above and the section header was removed to save
                    // vertical space.

                    val mangalStatus = deriveMangalStatus(result)
                    DoshaStatusRow(
                        title = "Mangal Dosha",
                        iconLabel = "🔥",
                        iconVector = Icons.Filled.Whatshot,
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
                        iconVector = Icons.Filled.Cyclone,
                        statusText = kalsarpaStatus.first,
                        statusColor = kalsarpaStatus.second,
                        onClick = { showKalsarpaDetail = true },
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )

                    Spacer(Modifier.height(8.dp))

                    DoshaStatusRow(
                        title = "Additional Yogas",
                        iconLabel = "✨",
                        iconVector = Icons.Filled.AutoAwesome,
                        statusText = "View All",
                        statusColor = CreamDim,
                        onClick = { showYogasDetail = true },
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )

                    Spacer(Modifier.height(20.dp))

                    ShimmerButton(
                        text = "View Full Report",
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            showFullReport = true
                        },
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
        val historyVm: CompatibilityViewModel = androidx.hilt.navigation.compose.hiltViewModel()
        CompatibilityHistoryScreen(
            viewModel = historyVm,
            onBack = { showHistorySheet = false },
            onItemSelect = { item ->
                historyVm.loadFromHistory(item)
                showHistorySheet = false
            },
            onGroupSelect = { group ->
                historyVm.loadFromGroup(group)
                showHistorySheet = false
            },
            onOpenSettings = onOpenSettings?.let {
                {
                    showHistorySheet = false
                    it()
                }
            },
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

// Mirrors iOS AppHeader.swift MatchResultHeader exactly:
// back chevron | history clock | Spacer | "Name ⊕ Name" | Spacer | globe | square.and.pencil
@Composable
private fun ResultHeader(
    boyName: String,
    girlName: String,
    subtitle: String = "",   // kept for call-site compat, not displayed (no subtitle in iOS)
    onBack: () -> Unit,
    onNewAnalysis: (() -> Unit)? = null,
    onHistoryTap: (() -> Unit)? = null,
    onChartTap: (() -> Unit)? = null,
    onShareTap: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Back button
        IconButton(onClick = onBack) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.compat_back_a11y),
                tint = Gold,
                modifier = Modifier.size(22.dp),
            )
        }
        // History button
        if (onHistoryTap != null) {
            IconButton(
                onClick = onHistoryTap,
                modifier = Modifier.semantics { contentDescription = "compat_history_button" },
            ) {
                Icon(
                    Icons.Filled.History,
                    contentDescription = stringResource(R.string.compat_history_a11y),
                    tint = Gold,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        Spacer(Modifier.weight(1f))

        // Center: "Name ⊕ Name" — mirrors iOS HStack(spacing: 4) with match_icon
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = firstNameFrom(boyName),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Gold,
                maxLines = 1,
            )
            Image(
                painter = painterResource(R.drawable.match_icon),
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                contentScale = ContentScale.Fit,
            )
            Text(
                text = firstNameFrom(girlName),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Gold,
                maxLines = 1,
            )
        }

        Spacer(Modifier.weight(1f))

        // Charts button — globe.asia.australia equivalent
        if (onChartTap != null) {
            IconButton(onClick = onChartTap) {
                Icon(
                    Icons.Filled.Public,
                    contentDescription = stringResource(R.string.compat_charts_a11y),
                    tint = Gold,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        // New match button — square.and.pencil equivalent
        if (onNewAnalysis != null) {
            IconButton(onClick = onNewAnalysis) {
                Icon(
                    Icons.Filled.EditNote,
                    contentDescription = stringResource(R.string.compat_new_analysis_a11y),
                    tint = Gold,
                    modifier = Modifier.size(22.dp),
                )
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
        // Header: tinted vector icon + label/subtitle + score badge + close button.
        // Mirrors iOS OrbitTooltipView.tooltipHeader (ScoreBadge has 4 variants).
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(statusColor.copy(alpha = 0.1f))
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            kutaVectorIcons[kuta.key]?.let { vector ->
                Icon(
                    imageVector = vector,
                    contentDescription = null,
                    tint = statusColor,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(10.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = kuta.label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = CreamText,
                )
                Text(
                    text = kutaScoreSubtitle(kuta),
                    style = MaterialTheme.typography.labelSmall,
                    color = CreamDim,
                    fontSize = 10.sp,
                )
            }
            ScoreBadge(kuta = kuta)
            Spacer(Modifier.width(6.dp))
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(28.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Close",
                    tint = CreamDim,
                    modifier = Modifier.size(16.dp),
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
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(52.dp),
        shape = RoundedCornerShape(26.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            contentColor = Color(0xFF0D0D1A),
            disabledContainerColor = NavyVariant,
            disabledContentColor = Color(0xFF718096),
        ),
        contentPadding = PaddingValues(0.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    if (enabled) Brush.linearGradient(listOf(Gold, Color(0xFFF5D060), Gold))
                    else Brush.linearGradient(listOf(NavyVariant, NavyVariant)),
                    RoundedCornerShape(26.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = if (enabled) Color(0xFF0D0D1A) else Color(0xFF718096),
                fontSize = 16.sp,
            )
        }
    }
}

// FloatingContextButton lives in FloatingContextButton.kt

// ─── Ask Destiny Dialog ───────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
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
    // iOS parity (CompatibilityResultSheets.swift:1056-1073): inline error
    // banner shown above the input bar; tap to dismiss.
    val errorMessage by viewModel.followUpError.collectAsState()
    var inputText by remember { mutableStateOf("") }
    val scrollState = androidx.compose.foundation.lazy.rememberLazyListState()
    val scope = rememberCoroutineScope()
    var scrollJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    // iOS parity (CompatibilityResultSheets.swift): explicit IME dismissal on
    // send / quick-question taps so the keyboard collapses immediately like
    // SwiftUI's .onSubmit { focus = nil }.
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    fun dismissIme() {
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
    }

    // iOS parity (CompatibilityResultSheets.swift:1261-1269): response-length selector
    var showStyleSelector by remember { mutableStateOf(false) }
    val responseLength by viewModel.responseLength.collectAsState(initial = "standard")

    // iOS parity (CompatibilityResultSheets.swift:1599-1604): localized cosmic
    // progress message keys cycled every 1.5 s while a follow-up is in-flight.
    val cosmicMessageResIds = remember {
        intArrayOf(
            R.string.compat_progress_connecting,
            R.string.compat_progress_mapping_sky,
            R.string.compat_progress_reading_planets,
            R.string.compat_progress_planetary_voice,
            R.string.compat_progress_chart_secrets,
            R.string.compat_progress_deeper_patterns,
            R.string.compat_progress_river_of_time,
            R.string.compat_progress_cosmic_windows,
            R.string.compat_progress_destiny_shaped,
            R.string.compat_progress_oracle_weaving,
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
        // iOS parity (CompatibilityResultSheets.swift:1082-1089 .onAppear):
        // load any persisted follow-up chat for this session BEFORE sending an
        // optional initial prompt — do NOT clear messages on open.
        if (result != null) {
            viewModel.setCompatibilityResult(result)
            // iOS parity (CompatibilityResultSheets.swift:1112-1154): the
            // sessionId is set by the calling flow on result hydration; the VM
            // also sets it during analyze(). Loading is a no-op when no
            // matching history row exists.
            viewModel.loadStoredFollowUpMessages()
        }
        if (initialPrompt != null) {
            viewModel.sendFollowUp(initialPrompt)
        }
    }

    // iOS parity (CompatibilityResultSheets.swift): a single coalesced
    // DispatchWorkItem reschedules whenever any of the six triggers fire —
    // messages count, last message length, last message text, info flag,
    // loading state, and error state — so multiple rapid changes collapse
    // into one debounced scroll-to-bottom instead of competing animations.
    val lastMessageLength = vmMessages.lastOrNull()?.text?.length ?: 0
    val lastMessageText = vmMessages.lastOrNull()?.text ?: ""
    val lastIsInfo = vmMessages.lastOrNull()?.isInfo ?: false
    LaunchedEffect(
        vmMessages.size,
        lastMessageLength,
        lastMessageText,
        lastIsInfo,
        isLoading,
        errorMessage,
    ) {
        requestScroll()
    }

    // iOS parity (CompatibilityResultSheets.swift:1216-1222): localized
    // fallback questions for the empty state — captured here so they are
    // accessible from the non-composable remember block below.
    RememberDefaultStrings()

    val displaySuggestions = remember(vmMessages) {
        val lastAiSuggestions = vmMessages.lastOrNull { !it.isUser }?.suggestions ?: emptyList()
        followUpSuggestionsToDisplay(
            apiSuggestions = lastAiSuggestions,
            defaultSuggestions = suggestions.ifEmpty {
                listOf(
                    // Fall back to localized defaults parity with iOS hardcoded
                    // strings on first show (no API suggestions yet).
                    LocalContextDefaultStrings.q_strengths,
                    LocalContextDefaultStrings.q_challenges,
                    LocalContextDefaultStrings.q_marriage,
                )
            },
        )
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = null,
        containerColor = Color.Transparent,
    ) {
        // ModalBottomSheet passes infinite height — we must bound it explicitly.
        // fillMaxHeight() resolves against the window, giving LazyColumn a finite constraint.
        CosmicBackground(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
            Column(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
                // Header — mirrors iOS AskDestinySheet header: centered title + trailing Done
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Leading spacer to balance trailing Done button
                    Spacer(Modifier.width(64.dp))
                    Text(
                        text = stringResource(R.string.ask_destiny_title),
                        fontFamily = CanelaFontFamily,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = CreamText,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                    )
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.done_action), color = Gold, fontWeight = FontWeight.SemiBold)
                    }
                }

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
                        // iOS parity (AskDestinySheet.welcomeView): sparkles orb + serif title + subtitle + gold pill suggestions
                        Column(
                            modifier = Modifier
                                .fillParentMaxSize()
                                .padding(horizontal = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Spacer(Modifier.weight(1f))

                            // Gold sparkles orb — mirrors iOS ZStack Circle + sparkles SF symbol
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(CircleShape)
                                    .background(Gold.copy(alpha = 0.10f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.AutoAwesome,
                                    contentDescription = null,
                                    tint = Gold,
                                    modifier = Modifier.size(32.dp),
                                )
                            }

                            Spacer(Modifier.height(16.dp))

                            // Title — mirrors iOS Text(ask_about_match_title) in Canela serif bold 18sp
                            Text(
                                text = stringResource(R.string.compat_ask_about_pair, boyName, girlName),
                                fontFamily = CanelaFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = CreamText,
                                textAlign = TextAlign.Center,
                            )

                            Spacer(Modifier.height(8.dp))

                            // Subtitle — mirrors iOS Text(ask_destiny_welcome)
                            Text(
                                text = stringResource(R.string.compat_ask_anything_prompt),
                                fontSize = 14.sp,
                                color = CreamDim,
                                textAlign = TextAlign.Center,
                            )

                            Spacer(Modifier.height(24.dp))

                            // Suggestion pills — mirrors iOS quickQuestionButton: gold pill, gold text
                            Column(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                displaySuggestions.forEach { suggestion ->
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(20.dp))
                                            .background(Gold.copy(alpha = 0.10f))
                                            .border(1.dp, Gold.copy(alpha = 0.30f), RoundedCornerShape(20.dp))
                                            .clickable {
                                                dismissIme()
                                                viewModel.sendFollowUp(suggestion)
                                            }
                                            .padding(horizontal = 16.dp, vertical = 10.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = suggestion,
                                            fontSize = 13.sp,
                                            color = Gold,
                                            textAlign = TextAlign.Center,
                                        )
                                    }
                                }
                            }

                            Spacer(Modifier.weight(1f))
                        }
                    }
                } else {
                    items(vmMessages) { msg ->
                        // For AI replies, find the most recent user message above this one to pair as the "query".
                        val lastQuery = if (!msg.isUser) {
                            val idx = vmMessages.indexOf(msg)
                            vmMessages.subList(0, idx).lastOrNull { it.isUser }?.text.orEmpty()
                        } else ""
                        AskChatBubble(
                            isUser = msg.isUser,
                            text = msg.text,
                            isInfo = msg.isInfo,
                            timestampMs = msg.timestampMs,
                            executionTimeMs = msg.executionTimeMs,
                            queryForRating = lastQuery,
                            onRate = { rating ->
                                viewModel.submitCompatRating(
                                    query = lastQuery,
                                    responseText = msg.text,
                                    rating = rating,
                                )
                            },
                        )
                    }
                    if (isLoading) {
                        item {
                            // iOS parity (CompatibilityResultSheets.swift:1599-1625
                            // CosmicProgressView): localized stepped animation
                            // instead of a single hardcoded loading line.
                            CompatCosmicProgressView(
                                stepResId = cosmicMessageResIds[cosmicStepIndex % cosmicMessageResIds.size],
                            )
                        }
                    }
                }
                // Follow-up suggestion chips after last AI reply
                if (vmMessages.isNotEmpty() && !isLoading) {
                    item {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                        ) {
                            displaySuggestions.forEach { suggestion ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(Gold.copy(alpha = 0.10f))
                                        .border(1.dp, Gold.copy(alpha = 0.30f), RoundedCornerShape(20.dp))
                                        .clickable {
                                            dismissIme()
                                            viewModel.sendFollowUp(suggestion)
                                        }
                                        .padding(horizontal = 16.dp, vertical = 10.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = suggestion,
                                        fontSize = 13.sp,
                                        color = Gold,
                                        textAlign = TextAlign.Center,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // iOS parity (CompatibilityResultSheets.swift:1056-1073): inline
            // error banner — tap to clear errorMessage.
            AnimatedVisibility(
                visible = errorMessage != null,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                val msg = errorMessage ?: ""
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFC53030).copy(alpha = 0.85f))
                        .clickable { viewModel.dismissFollowUpError() }
                        .semantics { contentDescription = "compat_error_banner" }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.WarningAmber,
                        contentDescription = null,
                        tint = CreamText,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodySmall,
                        color = CreamText,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = null,
                        tint = CreamText.copy(alpha = 0.85f),
                        modifier = Modifier.size(14.dp),
                    )
                }
            }

            HorizontalDivider(color = Gold.copy(alpha = 0.1f))

            // Input bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(NavyDeep)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // iOS parity (CompatibilityResultSheets.swift:1261-1269): leading style/length selector.
                IconButton(
                    onClick = { showStyleSelector = true },
                    enabled = !isLoading,
                    modifier = Modifier
                        .size(40.dp)
                        .semantics { contentDescription = "compat_response_length_button" },
                ) {
                    Icon(
                        Icons.Filled.Tune,
                        contentDescription = stringResource(R.string.compat_response_length_a11y),
                        tint = if (isLoading) CreamDim.copy(alpha = 0.4f) else Gold,
                    )
                }
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = {
                        Text(
                            stringResource(R.string.ask_question_placeholder),
                            color = CreamDim.copy(alpha = 0.5f),
                        )
                    },
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
                            dismissIme()
                            viewModel.sendFollowUp(trimmed)
                            inputText = ""
                        }
                    },
                    enabled = inputText.isNotBlank() && !isLoading,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(if (inputText.isNotBlank() && !isLoading) Gold else NavyVariant)
                        .semantics { contentDescription = "compat_send_button" },
                ) {
                    Text(
                        "↑",
                        fontSize = 20.sp,
                        color = if (inputText.isNotBlank() && !isLoading) Color(0xFF0D0D1A) else CreamDim.copy(alpha = 0.4f),
                    )
                }
                }
            }
        }
    }

    // iOS parity (CompatibilityResultSheets.swift:1261-1269): ResponseLengthSheet bottom sheet.
    if (showStyleSelector) {
        CompatResponseLengthSheet(
            current = responseLength,
            onSelect = {
                viewModel.setResponseLength(it)
                showStyleSelector = false
            },
            onDismiss = { showStyleSelector = false },
        )
    }
}

/**
 * iOS parity (CompatibilityResultSheets.swift:1599-1625 CosmicProgressView):
 * localized cosmic progress indicator shown while a follow-up call is in-flight.
 * The step text is rotated by the caller every 1.5 s.
 */
@Composable
private fun CompatCosmicProgressView(stepResId: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp)
            .semantics { contentDescription = "compat_cosmic_progress" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(14.dp),
            color = Gold,
            strokeWidth = 2.dp,
        )
        Text(
            text = stringResource(stepResId),
            style = MaterialTheme.typography.bodySmall,
            color = Gold.copy(alpha = 0.85f),
        )
    }
}

/**
 * Default localized fallback questions read at composition time. Held in an
 * object so they can be referenced from non-composable lambdas (e.g. inside
 * remember blocks) without re-fetching the context on every recomposition.
 */
private object LocalContextDefaultStrings {
    val q_strengths get() = ResultStringsHolder.qStrengths
    val q_challenges get() = ResultStringsHolder.qChallenges
    val q_marriage get() = ResultStringsHolder.qMarriage
}

/** Captured at first composition by [RememberDefaultStrings]. */
private object ResultStringsHolder {
    var qStrengths: String = ""
    var qChallenges: String = ""
    var qMarriage: String = ""
}

@Composable
internal fun RememberDefaultStrings() {
    ResultStringsHolder.qStrengths = stringResource(R.string.compat_default_q_strengths)
    ResultStringsHolder.qChallenges = stringResource(R.string.compat_default_q_challenges)
    ResultStringsHolder.qMarriage = stringResource(R.string.compat_default_q_marriage)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CompatResponseLengthSheet(
    current: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = NavySurface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .semantics { contentDescription = "compat_response_length_sheet" },
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(R.string.compat_response_style_title),
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = Gold,
            )
            CompatLengthOption(
                title = stringResource(R.string.response_length_concise),
                desc = stringResource(R.string.compat_response_length_concise_desc),
                value = "concise",
                isSelected = current == "concise" || current == "short",
                onSelect = onSelect,
            )
            CompatLengthOption(
                title = stringResource(R.string.response_length_expanded),
                desc = stringResource(R.string.compat_response_length_detailed_desc),
                value = "detailed",
                isSelected = current == "detailed" || current == "standard",
                onSelect = onSelect,
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun CompatLengthOption(
    title: String,
    desc: String,
    value: String,
    isSelected: Boolean,
    onSelect: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) Gold.copy(alpha = 0.12f) else Color.Transparent)
            .border(
                width = 1.dp,
                color = if (isSelected) Gold.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.08f),
                shape = RoundedCornerShape(12.dp),
            )
            .clickable { onSelect(value) }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, color = CreamText, fontWeight = FontWeight.Medium)
            Text(desc, fontSize = 12.sp, color = CreamDim)
        }
        if (isSelected) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = Gold, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun AskChatBubble(
    isUser: Boolean,
    text: String,
    isInfo: Boolean = false,
    timestampMs: Long = 0L,
    executionTimeMs: Long = 0L,
    queryForRating: String = "",
    onRate: (Int) -> Unit = {},
) {
    val clipboard = LocalClipboardManager.current
    var copied by remember(text) { mutableStateOf(false) }
    var ratedStars by remember(text) { mutableIntStateOf(0) }
    val showCopy = !isUser && text.length > 50
    val showRating = !isUser && text.length > 50
    val showMeta = !isUser && !isInfo

    // iOS parity (CompatibilityResultSheets.swift typewriter reveal): on iOS the
    // AI bubble streams character-by-character and the metadata row (timestamp /
    // copy / stars) is gated until the typewriter completes. Compose's shared
    // MarkdownText doesn't support char-stream rendering without re-parsing
    // markdown every frame, so we approximate with a fade-in over the bubble
    // and a delayed metadata reveal proportional to text length (mirrors how
    // long a typewriter at ~25 chars/sec would have taken). User and info
    // bubbles skip the gating entirely so they remain instant.
    val metaRevealMillis = remember(text, isUser, isInfo) {
        if (isUser || isInfo) 0
        else (text.length * 40).coerceIn(300, 1800)
    }
    var metaRevealed by remember(text) { mutableStateOf(isUser || isInfo) }
    LaunchedEffect(text, isUser, isInfo) {
        if (metaRevealMillis > 0) {
            kotlinx.coroutines.delay(metaRevealMillis.toLong())
            metaRevealed = true
        } else {
            metaRevealed = true
        }
    }
    val bubbleAlpha by animateFloatAsState(
        targetValue = if (metaRevealed || isUser || isInfo) 1f else 0.85f,
        animationSpec = tween(durationMillis = 240),
        label = "ask_bubble_alpha",
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { alpha = bubbleAlpha },
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
                when {
                    isUser -> {
                        Text(
                            text = text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Gold,
                            lineHeight = 20.sp,
                        )
                    }
                    isInfo -> {
                        // iOS parity (CompatibilityResultSheets.swift:1714-1731):
                        // info-type messages render in italic gold (used for
                        // redirect/lookup placeholders and backend hints).
                        Text(
                            text = text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Gold,
                            lineHeight = 20.sp,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                        )
                    }
                    else -> {
                        // iOS parity (CompatibilityResultSheets.swift:1737-1741
                        // MarkdownTextView): render full markdown — bold,
                        // italic, lists, and headers — instead of a
                        // bold-only fallback. Uses the shared MarkdownText
                        // composable from ChatScreen.kt that mirrors
                        // MarkdownTextView.swift block-by-block.
                        com.destinyai.astrology.ui.chat.MarkdownText(
                            content = text,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }

        // iOS parity (CompatibilityResultSheets.swift:1764-1806 metadataRow):
        // timestamp · execution time · copy · stars below long AI replies.
        // Gated by the typewriter-equivalent reveal so meta only appears once
        // the AI text would have finished streaming on iOS.
        if (showMeta && (showCopy || showRating || timestampMs > 0L)) {
            AnimatedVisibility(
                visible = metaRevealed,
                enter = fadeIn(animationSpec = tween(durationMillis = 200)),
                exit = fadeOut(),
            ) {
                Row(
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (timestampMs > 0L) {
                    Text(
                        text = formatBubbleTimestamp(timestampMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = CreamDim.copy(alpha = 0.7f),
                    )
                }
                if (timestampMs > 0L && executionTimeMs > 0L) {
                    Text(
                        text = " • ",
                        style = MaterialTheme.typography.labelSmall,
                        color = CreamDim.copy(alpha = 0.5f),
                    )
                }
                if (executionTimeMs > 0L) {
                    Text(
                        text = formatBubbleExecTime(executionTimeMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = CreamDim.copy(alpha = 0.7f),
                    )
                }
                Spacer(Modifier.weight(1f))
                if (showCopy) {
                    IconButton(
                        onClick = {
                            clipboard.setText(AnnotatedString(text))
                            copied = true
                        },
                        modifier = Modifier
                            .size(28.dp)
                            .semantics { contentDescription = "compat_copy_button" },
                    ) {
                        Icon(
                            imageVector = if (copied) Icons.Filled.Check else Icons.Filled.ContentCopy,
                            contentDescription = if (copied)
                                stringResource(R.string.compat_copied_a11y)
                            else
                                stringResource(R.string.compat_copy_a11y),
                            tint = if (copied) Gold else CreamDim,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
                if (showRating) {
                    Row(
                        modifier = Modifier.semantics { contentDescription = "compat_inline_rating" },
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        for (star in 1..5) {
                            IconButton(
                                onClick = {
                                    if (ratedStars == 0) {
                                        ratedStars = star
                                        onRate(star)
                                    }
                                },
                                enabled = ratedStars == 0,
                                modifier = Modifier.size(24.dp),
                            ) {
                                Icon(
                                    imageVector = if (star <= ratedStars) Icons.Filled.Star else Icons.Filled.StarBorder,
                                    contentDescription = "$star star",
                                    tint = if (star <= ratedStars) Gold else CreamDim.copy(alpha = 0.5f),
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                        }
                    }
                }
                }
            }
        }
    }
}

private fun formatBubbleTimestamp(timestampMs: Long): String {
    val fmt = DateFormat.getTimeInstance(DateFormat.SHORT)
    return fmt.format(Date(timestampMs))
}

private fun formatBubbleExecTime(ms: Long): String {
    val seconds = ms / 1000.0
    return when {
        seconds < 1.0 -> "${ms}ms"
        seconds < 60.0 -> "%.1fs".format(seconds)
        else -> {
            val m = (seconds / 60).toInt()
            val s = (seconds % 60).toInt()
            "${m}m ${s}s"
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

internal fun formatScore(value: Double): String {
    // iOS calls Double.formatted() which is locale-aware. Mirror that on Android using
    // NumberFormat in the user's default locale so digits/decimal separators match.
    val nf = java.text.NumberFormat.getInstance(java.util.Locale.getDefault())
    return if (value % 1.0 == 0.0) {
        nf.format(value.toLong())
    } else {
        nf.apply {
            minimumFractionDigits = 0
            maximumFractionDigits = 1
        }.format(value)
    }
}

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

/**
 * Hilt EntryPoint exposing the application-scoped [AppEvents] bus to the
 * non-Hilt result composable. Mirrors HomeSoundEntryPoint in HomeScreen.kt and
 * lets the result screen subscribe to [AppEvents.openProfileSettings] without
 * adding the bus to [CompatibilityViewModel]'s constructor.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface CompatResultAppEventsEntryPoint {
    fun appEvents(): AppEvents
}
