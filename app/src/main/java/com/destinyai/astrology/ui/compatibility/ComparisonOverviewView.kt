package com.destinyai.astrology.ui.compatibility

import com.destinyai.astrology.BuildConfig
import com.destinyai.astrology.R
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.compose.animation.core.*
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import com.destinyai.astrology.domain.model.ComparisonResult
import com.destinyai.astrology.ui.chat.MarkdownText
import com.destinyai.astrology.ui.theme.CosmicBackground
import com.destinyai.astrology.ui.theme.CreamDim
import com.destinyai.astrology.ui.theme.CreamText
import com.destinyai.astrology.ui.theme.Gold
import com.destinyai.astrology.ui.theme.NavySurface
import com.destinyai.astrology.ui.theme.NavyVariant
import java.io.File
import java.io.FileOutputStream

private val SuccessColor = Color(0xFF48BB78)
private val ErrorColor = Color(0xFFFC8181)
private val WarningColor = Color(0xFFED8936)

private data class KutaCellOverlay(
    val partnerName: String,
    val kutaName: String,
    val reason: String,
    val title: String,
    val subtitle: String,
)

// iOS parity (ComparisonOverviewView.swift:379-388): friendly area names
// shown in place of raw kuta keys (Health/Love/Temperament/etc.).
@Composable
private fun friendlyKutaLabel(key: String): String = when (key.lowercase()) {
    "nadi" -> stringResource(R.string.area_health)
    "bhakoot" -> stringResource(R.string.area_love)
    "gana" -> stringResource(R.string.area_temperament)
    "maitri" -> stringResource(R.string.area_friendship)
    "yoni" -> stringResource(R.string.area_intimacy)
    "vashya" -> stringResource(R.string.area_dominance)
    "tara" -> stringResource(R.string.area_destiny)
    "varna" -> stringResource(R.string.area_work)
    else -> kootaDisplayNames[key.lowercase()] ?: key.replaceFirstChar { it.uppercase() }
}

@Composable
fun ComparisonOverviewView(
    results: List<ComparisonResult>,
    userName: String,
    onSelectPartner: (Int) -> Unit,
    onBack: () -> Unit,
    onNewMatch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sortedResults = remember(results) {
        val recommended = results.filter { it.isRecommended }.sortedByDescending { it.adjustedScore }
        val rejected = results.filter { !it.isRecommended }.sortedByDescending { it.adjustedScore }
        recommended + rejected
    }
    val bestMatch = sortedResults.firstOrNull { it.isRecommended }
    val hasDosha = sortedResults.any { it.adjustedScore != it.overallScore }

    var showCancellationAlert by remember { mutableStateOf(false) }
    // iOS parity (ComparisonOverviewView.swift:484-494, 526-535): per-cell cancellation/warning popup.
    var selectedCellOverlay by remember { mutableStateOf<KutaCellOverlay?>(null) }
    // iOS parity (ComparisonOverviewView.swift:17, 173, 751-768): gate Save button while
    // PDF renders so rapid taps cannot enqueue duplicate save intents.
    var isGeneratingPDF by remember { mutableStateOf(false) }

    // Collect all cancelled kutas across all results for the overlay
    val cancellationTitle = stringResource(R.string.dosha_cancellation_title)
    val cancellationSubtitle = stringResource(R.string.astrological_exceptions_subtitle)
    val lowScoreTitle = stringResource(R.string.low_score_warning_title)
    val lowScoreSubtitle = stringResource(R.string.attention_required_subtitle)

    val cancelledKutas = remember(sortedResults) {
        sortedResults.flatMap { result ->
            result.kutaDetails.entries
                .filter { (_, kuta) -> kuta.doshaPresent && kuta.doshaCancelled && !kuta.cancellationReason.isNullOrBlank() }
                .map { (key, kuta) ->
                    Triple(
                        result.partner.name,
                        kootaDisplayNames[key] ?: key,
                        formatRejectionReason(kuta.cancellationReason.orEmpty(), userName, result.partner.name),
                    )
                }
        }
    }

    if (showCancellationAlert && cancelledKutas.isNotEmpty()) {
        CancellationOverlay(
            kutas = cancelledKutas,
            title = cancellationTitle,
            subtitle = cancellationSubtitle,
            onDismiss = { showCancellationAlert = false },
        )
    }

    selectedCellOverlay?.let { overlay ->
        CancellationOverlay(
            kutas = listOf(Triple(overlay.partnerName, overlay.kutaName, overlay.reason)),
            title = overlay.title,
            subtitle = overlay.subtitle,
            onDismiss = { selectedCellOverlay = null },
        )
    }

    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val shareText = remember(sortedResults, userName) {
        buildComparisonExportText(userName, sortedResults)
    }

    // iOS parity (ComparisonOverviewView.swift:144-175 + 240-247): generate a PDF
    // for both Save-to-Files and Share, attaching application/pdf via FileProvider.
    val pdfBuilder: () -> Uri? = {
        runCatching { buildComparisonPdf(context, userName, sortedResults) }.getOrNull()
    }
    val sharePdfWithText: () -> Unit = {
        val uri = pdfBuilder()
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_TEXT, shareText)
            putExtra(Intent.EXTRA_SUBJECT, "$userName — Compatibility Report")
            if (uri != null) {
                putExtra(Intent.EXTRA_STREAM, uri)
                clipData = ClipData.newRawUri("Compatibility PDF", uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } else {
                type = "text/plain"
            }
        }
        context.startActivity(Intent.createChooser(intent, "Share Results"))
    }
    val saveToFiles: () -> Unit
    // iOS parity (ComparisonOverviewView.swift:751-768): Save uses the SAF
    // CREATE_DOCUMENT picker so the user can pick a destination folder, just
    // like UIDocumentPickerViewController on iOS — not a share sheet.
    val createDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf"),
    ) { destUri ->
        if (destUri == null) {
            isGeneratingPDF = false
            return@rememberLauncherForActivityResult
        }
        val srcUri = pdfBuilder()
        if (srcUri != null) {
            runCatching {
                context.contentResolver.openInputStream(srcUri)?.use { input ->
                    context.contentResolver.openOutputStream(destUri)?.use { output ->
                        input.copyTo(output)
                    }
                }
            }
        }
        isGeneratingPDF = false
    }
    saveToFiles = {
        if (!isGeneratingPDF) {
            isGeneratingPDF = true
            val safeUser = userName.split(" ").first().filter { it.isLetterOrDigit() }.ifBlank { "compat" }
            val partnerSlug = sortedResults.joinToString("_") { it.partner.name.split(" ").first() }
                .filter { it.isLetterOrDigit() || it == '_' }
                .take(40)
                .ifBlank { "comparison" }
            createDocLauncher.launch("${safeUser}_vs_${partnerSlug}_comparison.pdf")
        }
    }

    CosmicBackground(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .semantics { contentDescription = "comparison_overview_screen" },
        ) {
            // Header
            ComparisonHeader(userName = userName, onBack = onBack, onShare = sharePdfWithText)

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(top = 12.dp, bottom = 100.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                // Section 1: Partner cards row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = if (sortedResults.size > 2) 2.dp else 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(if (sortedResults.size > 2) 4.dp else 10.dp),
                ) {
                    sortedResults.forEachIndexed { index, result ->
                        CompactPartnerCard(
                            result = result,
                            isBest = bestMatch?.id == result.id,
                            onTap = {
                                // iOS parity (ComparisonOverviewView.swift:331-336): light haptic
                                // and resolve the ORIGINAL results index by id so navigation
                                // doesn't open the wrong partner after viability sorting.
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                val originalIndex = results.indexOfFirst { it.id == result.id }
                                if (originalIndex >= 0) {
                                    onSelectPartner(originalIndex)
                                } else {
                                    onSelectPartner(index)
                                }
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                if (hasDosha) {
                    Text(
                        stringResource(R.string.after_dosha_cancellation),
                        fontSize = 11.sp,
                        color = Gold,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .clickable { showCancellationAlert = true },
                        textAlign = TextAlign.Center,
                    )
                }

                // Section 1.5: Recommendation footer
                RecommendationFooter(
                    bestMatch = bestMatch,
                    allResults = sortedResults,
                    allRejected = sortedResults.all { !it.isRecommended },
                    modifier = Modifier.padding(horizontal = if (sortedResults.size > 2) 6.dp else 16.dp),
                )

                // Section 2: Koota breakdown table
                if (sortedResults.size >= 2) {
                    KootaBreakdownTable(
                        results = sortedResults,
                        userName = userName,
                        modifier = Modifier.padding(horizontal = 12.dp),
                        onCancellationCellClick = { partnerName, kutaName, reason ->
                            selectedCellOverlay = KutaCellOverlay(
                                partnerName = partnerName,
                                kutaName = kutaName,
                                reason = reason,
                                title = cancellationTitle,
                                subtitle = cancellationSubtitle,
                            )
                        },
                        onWarningCellClick = { partnerName, kutaName, reason ->
                            // iOS parity (ComparisonOverviewView.swift:526-535):
                            // zero-non-critical cells use a distinct
                            // "Low Score Warning" overlay header.
                            selectedCellOverlay = KutaCellOverlay(
                                partnerName = partnerName,
                                kutaName = kutaName,
                                reason = reason,
                                title = lowScoreTitle,
                                subtitle = lowScoreSubtitle,
                            )
                        },
                    )
                }

                // Section 3: Analysis per partner
                sortedResults.forEach { result ->
                    PartnerAnalysisSection(
                        result = result,
                        userName = userName,
                        modifier = Modifier.padding(horizontal = if (sortedResults.size > 2) 6.dp else 16.dp),
                    )
                }

                // Section 4: Save Report row (mirrors iOS "Save to Files" — generates PDF)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(NavySurface)
                        .border(1.dp, Gold.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                        .clickable(enabled = !isGeneratingPDF, onClick = saveToFiles)
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                        .semantics { contentDescription = "comparison_save_pdf_button" },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        Icons.Default.Download,
                        contentDescription = null,
                        tint = if (isGeneratingPDF) Gold.copy(alpha = 0.4f) else Gold,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        stringResource(R.string.save_to_files),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (isGeneratingPDF) CreamText.copy(alpha = 0.4f) else CreamText,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = CreamDim.copy(alpha = 0.4f),
                        modifier = Modifier.size(16.dp),
                    )
                }

                // Section 5: New match button
                OutlinedButton(
                    onClick = onNewMatch,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = ButtonDefaults.outlinedButtonBorder.copy(
                        brush = Brush.horizontalGradient(listOf(Gold.copy(alpha = 0.3f), Gold.copy(alpha = 0.3f)))
                    ),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Gold),
                ) {
                    Icon(Icons.Default.AddCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.new_match), fontSize = 16.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

// ── Header ───────────────────────────────────────────────────────────────────

@Composable
private fun ComparisonHeader(userName: String, onBack: () -> Unit, onShare: () -> Unit) {
    Column(modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Gold)
            }
            // iOS parity (ComparisonOverviewView.swift:227-234): match_icon image alongside title.
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(
                    painter = painterResource(R.drawable.match_icon),
                    contentDescription = null,
                    modifier = Modifier
                        .size(32.dp)
                        .semantics { contentDescription = "comparison_header_icon" },
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.comparison_results),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Gold,
                    textAlign = TextAlign.Center,
                )
            }
            IconButton(onClick = onShare) {
                Icon(Icons.Default.IosShare, contentDescription = "Share", tint = Gold)
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("👤 $userName", fontSize = 12.sp, color = CreamDim.copy(alpha = 0.6f))
        }
    }
}

// ── Compact Partner Card ──────────────────────────────────────────────────────

@Composable
private fun CompactPartnerCard(
    result: ComparisonResult,
    isBest: Boolean,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val statusColor = if (result.isRecommended) SuccessColor else ErrorColor
    val badgeText = when {
        isBest -> "⭐ " + stringResource(R.string.best_match)
        result.isRecommended -> "✅ " + stringResource(R.string.recommended)
        else -> "❌ " + stringResource(R.string.not_rec)
    }
    val noAdjustmentLabel = stringResource(R.string.no_adjustment)
    val viewDetailsLabel = stringResource(R.string.view_details)

    Column(
        modifier = modifier
            .semantics { contentDescription = "partner_card_${result.partner.name.take(10)}" }
            .clip(RoundedCornerShape(14.dp))
            .background(NavySurface)
            .border(
                1.dp,
                if (isBest) Gold.copy(alpha = 0.6f) else statusColor.copy(alpha = 0.3f),
                RoundedCornerShape(14.dp),
            )
            .clickable(onClick = onTap)
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            result.partner.name.uppercase(),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = CreamText,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )

        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(statusColor),
        )

        // iOS parity (ComparisonOverviewView.swift:283-305): two-line score block.
        // adjusted ≠ overall → adjusted/36* (gold) + actual/36 caption.
        // adjusted == overall → overall/36 actual (gold) + "no_adjustment" caption.
        if (result.adjustedScore != result.overallScore) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "${result.adjustedScore}/36*",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Gold,
                    textAlign = TextAlign.Center,
                )
                Text(
                    "${result.overallScore}/36 actual",
                    fontSize = 10.sp,
                    color = CreamDim.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "${result.overallScore}/36 actual",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Gold,
                    textAlign = TextAlign.Center,
                )
                Text(
                    noAdjustmentLabel,
                    fontSize = 10.sp,
                    color = CreamDim.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center,
                )
            }
        }

        Text(
            badgeText,
            fontSize = 10.sp,
            color = statusColor,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                viewDetailsLabel,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = Gold,
            )
            Text("→", fontSize = 11.sp, color = Gold)
        }
    }
}

// ── Recommendation Footer ─────────────────────────────────────────────────────

@Composable
private fun RecommendationFooter(
    bestMatch: ComparisonResult?,
    allResults: List<ComparisonResult>,
    allRejected: Boolean,
    modifier: Modifier = Modifier,
) {
    // iOS parity (ComparisonOverviewView.swift:650-700): three render branches.
    //   1) allRejected → ⚠️ + no_profiles_meet_threshold + review_individual hint
    //   2) bestMatch present → 🏆 + final_recommendation_label +
    //      comparisonReasonText + (best.oneLiner ?: all_doshas_safe_fallback)
    //   3) bestMatch null but not all rejected → graceful fallback
    val borderColor = when {
        allRejected -> WarningColor.copy(alpha = 0.4f)
        bestMatch != null -> Gold.copy(alpha = 0.5f)
        else -> ErrorColor.copy(alpha = 0.3f)
    }
    val bgColor = when {
        allRejected -> WarningColor.copy(alpha = 0.06f)
        bestMatch != null -> SuccessColor.copy(alpha = 0.08f)
        else -> ErrorColor.copy(alpha = 0.05f)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(14.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        when {
            allRejected -> {
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("⚠️", fontSize = 18.sp)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            stringResource(R.string.no_profiles_meet_threshold),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = WarningColor,
                        )
                        Text(
                            stringResource(R.string.review_individual_analyses_hint),
                            fontSize = 11.sp,
                            color = CreamDim.copy(alpha = 0.7f),
                            lineHeight = 16.sp,
                        )
                    }
                }
            }
            bestMatch != null -> {
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("🏆", fontSize = 18.sp)
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            stringResource(
                                R.string.final_recommendation_label,
                                bestMatch.partner.name,
                                bestMatch.adjustedScore.toString(),
                            ),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Gold,
                        )
                        val reasonText = comparisonReasonText(bestMatch, allResults = allResults)
                        if (reasonText.isNotBlank()) {
                            Text(
                                reasonText,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = CreamText,
                                lineHeight = 17.sp,
                            )
                        }
                        // iOS parity (line 684): best.oneLiner ?? all_doshas_safe_fallback.
                        val subtitle = bestMatch.oneLiner?.takeIf { it.isNotBlank() }
                            ?: stringResource(R.string.all_doshas_safe_fallback)
                        Text(
                            subtitle,
                            fontSize = 12.sp,
                            color = CreamDim,
                            lineHeight = 17.sp,
                        )
                    }
                }
            }
            else -> {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("⚠️", fontSize = 18.sp)
                    Text(
                        stringResource(R.string.no_profiles_meet_threshold),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = ErrorColor,
                    )
                }
            }
        }
    }
}

// ── Koota Breakdown Table ─────────────────────────────────────────────────────

private val kootaKeys = listOf("varna", "vashya", "tara", "yoni", "maitri", "gana", "bhakoot", "nadi")
private val kootaDisplayNames = mapOf(
    "varna" to "Varna", "vashya" to "Vashya", "tara" to "Tara",
    "yoni" to "Yoni", "maitri" to "Maitri", "gana" to "Gana",
    "bhakoot" to "Bhakoot", "nadi" to "Nadi",
)

@Composable
private fun KootaBreakdownTable(
    results: List<ComparisonResult>,
    userName: String,
    modifier: Modifier = Modifier,
    onCancellationCellClick: (partnerName: String, kutaName: String, reason: String) -> Unit = { _, _, _ -> },
    onWarningCellClick: (partnerName: String, kutaName: String, reason: String) -> Unit = { _, _, _ -> },
) {
    val areaLabel = stringResource(R.string.area_label)
    val actualLabel = stringResource(R.string.actual_label)
    val adjustedLabel = stringResource(R.string.adjusted_label)
    val detailedBreakdownTitle = stringResource(R.string.detailed_breakdown_title)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(NavySurface)
            .border(1.dp, Gold.copy(alpha = 0.15f), RoundedCornerShape(14.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            detailedBreakdownTitle,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = Gold.copy(alpha = 0.7f),
            letterSpacing = 1.sp,
            modifier = Modifier.padding(bottom = 8.dp),
        )

        // Header row
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(areaLabel, fontSize = 11.sp, color = CreamDim.copy(alpha = 0.5f), modifier = Modifier.weight(1f))
            results.forEach { r ->
                Text(
                    firstNameFrom(r.partner.name),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = CreamDim,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                )
            }
        }

        HorizontalDivider(color = Color.White.copy(alpha = 0.06f), modifier = Modifier.padding(vertical = 4.dp))

        kootaKeys.forEach { key ->
            // iOS parity (line 379-388): map raw kuta key → friendly area name.
            val friendly = friendlyKutaLabel(key)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    friendly,
                    fontSize = 12.sp,
                    color = CreamDim,
                    modifier = Modifier.weight(1f),
                )
                results.forEach { r ->
                    val kuta = r.kutaDetails[key]
                    val statusIcon = kuta?.let {
                        kutaCellStatusIconV2(
                            doshaPresent = it.doshaPresent,
                            doshaCancelled = it.doshaCancelled,
                            score = it.score,
                            maxScore = it.maxScore,
                        )
                    }
                    val display = kuta?.let {
                        kutaCellScoreDisplay(
                            doshaCancelled = it.doshaCancelled,
                            score = it.score,
                            maxScore = it.maxScore,
                        )
                    } ?: "—"
                    val color = when {
                        kuta == null -> CreamDim.copy(alpha = 0.4f)
                        kuta.doshaPresent && !kuta.doshaCancelled -> Color(0xFFFC8181).copy(alpha = 0.9f)
                        kuta.doshaCancelled -> Color(0xFF48BB78)
                        kuta.score >= kuta.maxScore -> Color(0xFF48BB78)
                        kuta.score == 0.0 -> Color(0xFFED8936)
                        else -> CreamDim.copy(alpha = 0.6f)
                    }
                    val cellClickable: (() -> Unit)? = when {
                        kuta != null && kuta.doshaCancelled && !kuta.cancellationReason.isNullOrBlank() -> {
                            {
                                onCancellationCellClick(
                                    r.partner.name,
                                    friendly,
                                    formatRejectionReason(kuta.cancellationReason, userName, r.partner.name),
                                )
                            }
                        }
                        kuta != null && kuta.score == 0.0 && !(kuta.doshaPresent && !kuta.doshaCancelled) -> {
                            {
                                val raw = kuta.description.ifBlank {
                                    "$friendly compatibility score is 0. This area requires mutual understanding and effort."
                                }
                                onWarningCellClick(
                                    r.partner.name,
                                    friendly,
                                    formatRejectionReason(raw, userName, r.partner.name),
                                )
                            }
                        }
                        else -> null
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .let { m ->
                                if (cellClickable != null) m.clickable(onClick = cellClickable) else m
                            }
                            .semantics {
                                contentDescription = if (cellClickable != null) "kuta_cell_clickable" else "kuta_cell"
                            },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        if (statusIcon != null) Text(statusIcon, fontSize = 10.sp)
                        Text(
                            display,
                            fontSize = 12.sp,
                            color = color,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }

        // iOS parity (ComparisonOverviewView.swift:393-394, 549-610): Manglik row
        // surfacing structured mangalCompatibility data. Renders Active 🚫 when
        // rejected by Mangal, "Cancelled" ✅ when cancellation.occurs, the
        // capitalized compatibility_category when present, "None" ✅ when no
        // data at all, else "View" hint.
        MangalRow(
            results = results,
            modifier = Modifier.padding(vertical = 3.dp),
        )

        HorizontalDivider(color = Color.White.copy(alpha = 0.06f), modifier = Modifier.padding(vertical = 4.dp))

        // Actual Total row (iOS "Actual Total")
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(actualLabel, fontSize = 12.sp, color = CreamDim, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            results.forEach { r ->
                Text(
                    "${r.totalScore}/${r.maxScore}",
                    fontSize = 12.sp,
                    color = CreamText,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                )
            }
        }

        // Adjusted Total row (iOS "Adjusted Total") — only when scores differ
        if (results.any { it.adjustedScore != it.totalScore }) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(adjustedLabel, fontSize = 12.sp, color = Gold, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                results.forEach { r ->
                    Text(
                        "${r.adjustedScore}/${r.maxScore}",
                        fontSize = 12.sp,
                        color = Gold,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

/** iOS parity (ComparisonOverviewView.swift:549-610): Manglik row with structured cancellation/category data. */
@Composable
private fun MangalRow(
    results: List<ComparisonResult>,
    modifier: Modifier = Modifier,
) {
    val manglikLabel = stringResource(R.string.manglik_label)
    val activeLabel = stringResource(R.string.active_label)
    val cancelledLabel = stringResource(R.string.dosha_cancelled_title)
    val noneLabel = stringResource(R.string.none_label)
    val viewLabel = stringResource(R.string.view_label)

    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            manglikLabel,
            fontSize = 12.sp,
            color = CreamDim,
            modifier = Modifier.weight(1f),
        )
        results.forEach { r ->
            val hasMangalRejection = r.rejectionReasons.any { it.contains("Mangal", ignoreCase = true) }
            val mangalCompat = r.mangalCompatibility
            // iOS parity: cancellation.occurs nested under "cancellation".
            val cancellation = mangalCompat?.get("cancellation") as? Map<*, *>
            val cancellationOccurs = cancellation?.get("occurs") as? Boolean
                // Some payloads flatten the field on the parent object.
                ?: (mangalCompat?.get("cancellation_occurs") as? Boolean)
            val compatCategory = mangalCompat?.get("compatibility_category") as? String

            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                when {
                    hasMangalRejection -> {
                        Text(activeLabel, fontSize = 11.sp, color = ErrorColor)
                        Spacer(Modifier.width(2.dp))
                        Text("🚫", fontSize = 8.sp)
                    }
                    cancellationOccurs == true -> {
                        Text(cancelledLabel, fontSize = 11.sp, color = SuccessColor)
                        Spacer(Modifier.width(2.dp))
                        Text("✅", fontSize = 8.sp)
                    }
                    compatCategory != null -> {
                        val display = compatCategory.replaceFirstChar { it.uppercase() }
                        val cat = compatCategory.lowercase()
                        val color = when (cat) {
                            "excellent", "good" -> SuccessColor
                            "moderate" -> WarningColor
                            else -> Color(0xFFE6C200)
                        }
                        Text(display, fontSize = 11.sp, color = color)
                        Spacer(Modifier.width(2.dp))
                        Text(if (cat == "excellent" || cat == "good") "✅" else "⚠️", fontSize = 8.sp)
                    }
                    mangalCompat == null -> {
                        Text(noneLabel, fontSize = 11.sp, color = SuccessColor)
                        Spacer(Modifier.width(2.dp))
                        Text("✅", fontSize = 8.sp)
                    }
                    else -> {
                        Text(viewLabel, fontSize = 11.sp, color = CreamDim.copy(alpha = 0.6f))
                    }
                }
            }
        }
    }
}

// ── Per-partner analysis section ──────────────────────────────────────────────

@Composable
private fun PartnerAnalysisSection(
    result: ComparisonResult,
    userName: String,
    modifier: Modifier = Modifier,
) {
    if (result.summary.isEmpty()) return
    // iOS parity (ComparisonOverviewView.swift:613-647): divider header,
    // MarkdownTextView for the body (FINAL RECOMMENDATION slice), and a
    // status circle + label at the bottom.
    val analysisTitle = stringResource(R.string.analysis_with_partner, result.partner.name.uppercase())
    val statusLabel = if (result.isRecommended) {
        stringResource(R.string.recommended)
    } else {
        stringResource(R.string.not_recommended)
    }
    val statusColor = if (result.isRecommended) SuccessColor else ErrorColor
    val sliced = remember(result.summary) { extractFinalRecommendation(result.summary) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Section divider header
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(
                modifier = Modifier
                    .width(16.dp)
                    .height(1.dp)
                    .background(Gold.copy(alpha = 0.4f)),
            )
            Text(
                analysisTitle,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.2.sp,
                color = Gold.copy(alpha = 0.8f),
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(1.dp)
                    .background(Gold.copy(alpha = 0.4f)),
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(NavyVariant)
                .border(1.dp, Gold.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Markdown-rendered FINAL RECOMMENDATION slice (parity with iOS
            // MarkdownTextView). Falls back to plain text when no marker found.
            MarkdownText(
                content = sliced,
                modifier = Modifier.fillMaxWidth(),
            )

            // Status circle + label
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(statusColor),
                )
                Text(
                    statusLabel,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = statusColor,
                )
            }
        }
    }
}

// ── Pure helpers ──────────────────────────────────────────────────────────────

@Composable
private fun CancellationOverlay(
    kutas: List<Triple<String, String, String>>,
    title: String,
    subtitle: String,
    onDismiss: () -> Unit,
) {
    val animScale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "overlay_scale",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .width(320.dp)
                .clickable(enabled = false, onClick = {})
                .clip(RoundedCornerShape(20.dp))
                .drawBehind {
                    // Radial glow
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(Gold.copy(alpha = 0.08f), Color.Transparent)
                        )
                    )
                }
                .background(NavySurface)
                .border(
                    1.dp,
                    Brush.linearGradient(listOf(Gold.copy(alpha = 0.5f), Gold.copy(alpha = 0.1f))),
                    RoundedCornerShape(20.dp),
                )
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(SuccessColor.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Default.Shield, contentDescription = null, tint = SuccessColor, modifier = Modifier.size(20.dp))
                    }
                    Column {
                        Text(
                            title,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = CreamText,
                        )
                        Text(
                            subtitle,
                            fontSize = 11.sp,
                            color = CreamDim,
                        )
                    }
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                    Text("✕", fontSize = 14.sp, color = CreamDim)
                }
            }

            HorizontalDivider(color = Gold.copy(alpha = 0.3f))

            // Kuta entries
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                kutas.forEach { (partnerName, kutaName, reason) ->
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            "$kutaName — $partnerName",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Gold.copy(alpha = 0.9f),
                        )
                        Text(reason, fontSize = 12.sp, color = CreamDim, lineHeight = 17.sp)
                    }
                }
            }
        }
    }
}

internal fun comparisonReasonText(best: ComparisonResult, allResults: List<ComparisonResult>): String {
    val others = allResults.filter { it.partner.name != best.partner.name }
    if (others.isEmpty()) {
        // Single result — fall back to basic descriptor
        val reasons = mutableListOf<String>()
        if (best.isRecommended) reasons.add("Recommended by Vedic analysis")
        if (best.adjustedScore >= best.maxScore * 0.75) reasons.add("High compatibility score (${best.adjustedScore}/${best.maxScore})")
        val nadiKuta = best.kutaDetails["nadi"]
        if (nadiKuta != null && !nadiKuta.doshaPresent) reasons.add("No Nadi Dosha")
        return reasons.joinToString(" · ")
    }

    val bestAdj = best.adjustedScore
    val bestActual = best.overallScore
    val hasCancellation = bestAdj > bestActual
    val recommendedOthers = others.filter { it.isRecommended }
    val rejectedOthers = others.filter { !it.isRecommended }

    val primary: String? = recommendedOthers.maxByOrNull { it.adjustedScore }?.let { second ->
        val delta = bestAdj - second.adjustedScore
        val secondName = second.partner.name.split(" ").first()
        when {
            delta > 0 && hasCancellation -> "Leads $secondName by $delta pts after dosha cancellation ($bestAdj vs ${second.adjustedScore})"
            delta > 0 -> "Scores $delta points higher than $secondName ($bestAdj vs ${second.adjustedScore})"
            hasCancellation -> "Tied with $secondName — dosha cancellation tips the balance (+${bestAdj - bestActual} pts applied)"
            else -> "Tied with $secondName at $bestAdj"
        }
    }

    val rejectionNote: String? = rejectedOthers.maxByOrNull { it.adjustedScore }?.let { topRej ->
        if (topRej.adjustedScore > bestAdj) {
            val rejName = topRej.partner.name.split(" ").first()
            "$rejName scored ${topRej.adjustedScore} but disqualified by active dosha"
        } else null
    }

    return when {
        primary != null && rejectionNote != null -> "$primary · $rejectionNote"
        primary != null -> primary
        rejectionNote != null -> rejectionNote
        rejectedOthers.isNotEmpty() -> "Only viable match — others disqualified by active doshas"
        else -> ""
    }
}

internal fun stripFollowUpSection(text: String): String {
    val markers = listOf(
        "### 💬 SUGGESTED FOLLOW-UP QUESTIONS",
        "SUGGESTED FOLLOW-UP QUESTIONS",
        "💬 SUGGESTED FOLLOW-UP",
    )
    var result = text
    for (marker in markers) {
        val idx = result.indexOf(marker, ignoreCase = true)
        if (idx >= 0) {
            result = result.substring(0, idx).trimEnd()
            break
        }
    }
    while (result.endsWith("---")) {
        result = result.dropLast(3).trimEnd()
    }
    return result
}

internal fun extractFinalRecommendation(text: String): String {
    val cleaned = stripFollowUpSection(text)
    val marker = "FINAL RECOMMENDATION"
    val idx = cleaned.indexOf(marker, ignoreCase = true)
    return if (idx >= 0) cleaned.substring(idx + marker.length).trimStart() else cleaned
}

internal fun formatRejectionReason(reason: String, userName: String, partnerName: String): String {
    val userFirst = userName.split(" ").first()
    val partnerFirst = partnerName.split(" ").first()
    return reason
        .replace("Boy:", "$userFirst:")
        .replace("Girl:", "$partnerFirst:")
        .replace("Boy is", "$userFirst is")
        .replace("Girl is", "$partnerFirst is")
        .replace("Boy's", "$userFirst's")
        .replace("Girl's", "$partnerFirst's")
        .replace(" Boy ", " $userFirst ")
        .replace(" Girl ", " $partnerFirst ")
}

internal fun buildComparisonExportText(userName: String, results: List<ComparisonResult>): String {
    val best = results.firstOrNull { it.isRecommended }
    val lines = mutableListOf("✨ $userName — Compatibility Results")
    results.forEach { r ->
        val line = "${r.partner.name}: ${r.adjustedScore}/${r.maxScore}${if (r.isRecommended) " ✓" else ""}"
        lines.add(line)
    }
    if (best != null) lines.add("Best match: ${best.partner.name}")
    lines.add("\nAnalyzed with Destiny AI Astrology\n🔗 destinyaiastrology.com")
    return lines.joinToString("\n")
}

internal fun kutaCellStatusIcon(
    doshaPresent: Boolean,
    doshaCancelled: Boolean,
    score: Double,
    maxScore: Double,
): String = when {
    doshaPresent && !doshaCancelled -> "🚫"
    doshaPresent && doshaCancelled -> "⚠️"
    else -> "✅"
}

internal fun kutaCellScoreDisplay(
    doshaCancelled: Boolean,
    score: Double,
    maxScore: Double,
): String = if (doshaCancelled) "0→${maxScore.toInt()}" else "${score.toInt()}/${maxScore.toInt()}"

// 5-state cell machine matching iOS:
// 1. active dosha → 🚫
// 2. cancelled dosha → ✅ (with "0→max" score display)
// 3. full score → ✅
// 4. zero non-critical → ⚠️
// 5. partial score → null (no icon)
internal fun kutaCellStatusIconV2(
    doshaPresent: Boolean,
    doshaCancelled: Boolean,
    score: Double,
    maxScore: Double,
): String? = when {
    doshaPresent && !doshaCancelled -> "🚫"
    doshaPresent && doshaCancelled -> "✅"
    score >= maxScore && maxScore > 0 -> "✅"
    score == 0.0 -> "⚠️"
    else -> null
}

internal fun compactPartnerCardScoreText(
    adjustedScore: Int,
    overallScore: Int,
    maxScore: Int,
): String = if (adjustedScore != overallScore) {
    "$adjustedScore/$maxScore*"
} else {
    "$adjustedScore/$maxScore"
}

internal fun rejectionReasonScoreHighlight(reason: String): Pair<String, String>? {
    val match = Regex("(\\d+/\\d+)").find(reason) ?: return null
    val before = reason.substring(0, match.range.first)
    return before to match.value
}

// ── PDF builder (Android parity with iOS ComparisonPDFRenderer) ───────────────

/**
 * Generates a PDF from the comparison results and returns a content:// URI through
 * the app's FileProvider. Mirrors iOS ComparisonPDFRenderer (text-only document, no
 * embedded fonts) so receiving apps can preview and save the report.
 */
internal fun buildComparisonPdf(
    context: Context,
    userName: String,
    results: List<ComparisonResult>,
): Uri? {
    val doc = PdfDocument()
    val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4 in points
    val page = doc.startPage(pageInfo)
    val canvas = page.canvas
    val title = Paint().apply {
        color = android.graphics.Color.BLACK
        textSize = 18f
        isFakeBoldText = true
    }
    val body = Paint().apply {
        color = android.graphics.Color.DKGRAY
        textSize = 12f
    }
    var y = 60f
    canvas.drawText("$userName — Compatibility Report", 40f, y, title)
    y += 28f
    canvas.drawText("Analyzed with Destiny AI Astrology", 40f, y, body)
    y += 24f
    results.forEach { r ->
        val line = "${r.partner.name}: ${r.adjustedScore}/${r.maxScore}" +
            if (r.isRecommended) " — Recommended" else " — Not Recommended"
        canvas.drawText(line, 40f, y, body)
        y += 18f
        if (r.summary.isNotBlank()) {
            r.summary.chunkedByLine(80).forEach { chunk ->
                canvas.drawText(chunk, 56f, y, body)
                y += 16f
                if (y > 800f) return@forEach
            }
        }
        y += 8f
    }
    doc.finishPage(page)
    val safeUser = userName.filter { it.isLetterOrDigit() }.take(16).ifBlank { "compat" }
    val file = File(context.cacheDir, "compat-$safeUser.pdf")
    return runCatching {
        FileOutputStream(file).use { doc.writeTo(it) }
        doc.close()
        FileProvider.getUriForFile(context, "${BuildConfig.APPLICATION_ID}.fileprovider", file)
    }.getOrElse {
        doc.close()
        null
    }
}

private fun String.chunkedByLine(maxChars: Int): List<String> {
    if (length <= maxChars) return listOf(this)
    val result = mutableListOf<String>()
    var idx = 0
    while (idx < length) {
        val end = minOf(idx + maxChars, length)
        result += substring(idx, end)
        idx = end
    }
    return result
}
