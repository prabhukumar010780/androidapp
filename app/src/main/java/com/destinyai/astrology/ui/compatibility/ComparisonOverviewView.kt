package com.destinyai.astrology.ui.compatibility

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.Intent
import androidx.compose.animation.core.*
import androidx.compose.ui.platform.LocalContext
import com.destinyai.astrology.domain.model.ComparisonResult
import com.destinyai.astrology.ui.theme.CosmicBackground
import com.destinyai.astrology.ui.theme.CreamDim
import com.destinyai.astrology.ui.theme.CreamText
import com.destinyai.astrology.ui.theme.Gold
import com.destinyai.astrology.ui.theme.NavySurface
import com.destinyai.astrology.ui.theme.NavyVariant

private val SuccessColor = Color(0xFF48BB78)
private val ErrorColor = Color(0xFFFC8181)

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

    // Collect all cancelled kutas across all results for the overlay
    val cancelledKutas = remember(sortedResults) {
        sortedResults.flatMap { result ->
            result.kutaDetails.entries
                .filter { (_, kuta) -> kuta.doshaPresent && kuta.doshaCancelled && !kuta.cancellationReason.isNullOrBlank() }
                .map { (key, kuta) ->
                    Triple(result.partner.name, kootaDisplayNames[key] ?: key, kuta.cancellationReason.orEmpty())
                }
        }
    }

    if (showCancellationAlert && cancelledKutas.isNotEmpty()) {
        CancellationOverlay(
            kutas = cancelledKutas,
            onDismiss = { showCancellationAlert = false },
        )
    }

    val context = LocalContext.current
    val shareText = remember(sortedResults, userName) {
        buildComparisonExportText(userName, sortedResults)
    }

    CosmicBackground(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .semantics { contentDescription = "comparison_overview_screen" },
        ) {
            // Header
            ComparisonHeader(userName = userName, onBack = onBack, onShare = {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, shareText)
                }
                context.startActivity(Intent.createChooser(intent, "Share Results"))
            })

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
                            onTap = { onSelectPartner(index) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                if (hasDosha) {
                    Text(
                        "* after dosha cancellation — tap to learn why",
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
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                }

                // Section 3: Analysis per partner
                sortedResults.forEach { result ->
                    PartnerAnalysisSection(
                        result = result,
                        modifier = Modifier.padding(horizontal = if (sortedResults.size > 2) 6.dp else 16.dp),
                    )
                }

                // Section 4: Export report row (mirrors iOS "Save to Files" button)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(NavySurface)
                        .border(1.dp, Gold.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                        .clickable {
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, shareText)
                                putExtra(Intent.EXTRA_SUBJECT, "$userName — Compatibility Report")
                            }
                            context.startActivity(Intent.createChooser(intent, "Export Report"))
                        }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, tint = Gold, modifier = Modifier.size(20.dp))
                    Text(
                        "Export Report",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = CreamText,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = CreamDim.copy(alpha = 0.4f),
                        modifier = Modifier.size(16.dp))
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
                    Text("New Match", fontSize = 16.sp, fontWeight = FontWeight.Medium)
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
            Text(
                "Comparison Results",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Gold,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
            )
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
        isBest -> "⭐ Best"
        result.isRecommended -> "✅ Yes"
        else -> "❌ No"
    }
    val scoreText = compactPartnerCardScoreText(
        adjustedScore = result.adjustedScore,
        overallScore = result.overallScore,
        maxScore = result.maxScore,
    )

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

        Text(
            badgeText,
            fontSize = 10.sp,
            color = statusColor,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )

        Text(
            scoreText,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = CreamText,
        )

        Text(
            "→ Detail",
            fontSize = 11.sp,
            color = Gold.copy(alpha = 0.7f),
        )
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
    val (borderColor, bgColor, icon, title, subtitle) = if (bestMatch != null) {
        listOf(
            SuccessColor.copy(alpha = 0.4f),
            SuccessColor.copy(alpha = 0.08f),
            "🏆",
            "Best Match: ${bestMatch.partner.name}",
            "Score: ${bestMatch.adjustedScore}/${bestMatch.maxScore} · Recommended",
        )
    } else {
        listOf(
            ErrorColor.copy(alpha = 0.3f),
            ErrorColor.copy(alpha = 0.05f),
            "⚠️",
            "No Recommended Match",
            "All partners have active doshas",
        )
    }

    @Suppress("UNCHECKED_CAST")
    val colors = listOf(borderColor, bgColor, icon, title, subtitle) as List<Any>

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors[1] as Color)
            .border(1.dp, colors[0] as Color, RoundedCornerShape(14.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(colors[2] as String, fontSize = 18.sp)
            Text(
                colors[3] as String,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = CreamText,
                modifier = Modifier.weight(1f),
            )
        }
        Text(colors[4] as String, fontSize = 13.sp, color = CreamDim)

        // comparisonReasonText — iOS equivalent: explains why best match was selected
        if (bestMatch != null) {
            val reasonText = comparisonReasonText(bestMatch, allResults = allResults)
            if (reasonText.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(reasonText, fontSize = 12.sp, color = CreamDim.copy(alpha = 0.8f), lineHeight = 18.sp)
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
    modifier: Modifier = Modifier,
) {
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
            "Kuta Breakdown",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = Gold.copy(alpha = 0.7f),
            letterSpacing = 1.sp,
            modifier = Modifier.padding(bottom = 8.dp),
        )

        // Header row
        Row(modifier = Modifier.fillMaxWidth()) {
            Text("Kuta", fontSize = 11.sp, color = CreamDim.copy(alpha = 0.5f), modifier = Modifier.weight(1f))
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    kootaDisplayNames[key] ?: key,
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
                    Column(
                        modifier = Modifier.weight(1f),
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

        HorizontalDivider(color = Color.White.copy(alpha = 0.06f), modifier = Modifier.padding(vertical = 4.dp))

        // Actual Total row (iOS "Actual Total")
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Actual Total", fontSize = 12.sp, color = CreamDim, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
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
                Text("Adjusted*", fontSize = 12.sp, color = Gold, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, modifier = Modifier.weight(1f))
                results.forEach { r ->
                    Text(
                        "${r.adjustedScore}/${r.maxScore}",
                        fontSize = 12.sp,
                        color = Gold,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

// ── Per-partner analysis section ──────────────────────────────────────────────

@Composable
private fun PartnerAnalysisSection(
    result: ComparisonResult,
    modifier: Modifier = Modifier,
) {
    if (result.summary.isEmpty()) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(NavyVariant)
            .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(14.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(if (result.isRecommended) SuccessColor.copy(alpha = 0.2f) else ErrorColor.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(partnerInitial(result.partner.name), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (result.isRecommended) SuccessColor else ErrorColor)
            }
            Text(result.partner.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = CreamText)
        }
        Text(result.summary, fontSize = 13.sp, color = CreamDim, lineHeight = 20.sp)
    }
}

// ── Pure helpers ──────────────────────────────────────────────────────────────

@Composable
private fun CancellationOverlay(
    kutas: List<Triple<String, String, String>>,
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
                            "Dosha Cancellation",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = CreamText,
                        )
                        Text(
                            "Astrological exceptions apply",
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
