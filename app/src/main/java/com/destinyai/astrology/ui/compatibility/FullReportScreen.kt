package com.destinyai.astrology.ui.compatibility

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.destinyai.astrology.domain.model.CompatibilityResult
import com.destinyai.astrology.ui.theme.CosmicBackground
import com.destinyai.astrology.ui.theme.CreamDim
import com.destinyai.astrology.ui.theme.CreamText
import com.destinyai.astrology.ui.theme.Gold
import com.destinyai.astrology.ui.theme.NavySurface
import com.destinyai.astrology.ui.theme.NavyVariant
import android.content.Intent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class ReportSection(val emoji: String, val title: String, val content: String)

@Composable
fun FullReportScreen(
    result: CompatibilityResult,
    onBack: () -> Unit,
) {
    var showAskDestiny by remember { mutableStateOf(false) }
    val sections = remember(result.summary) { parseSections(result.summary) }
    val context = LocalContext.current

    val shareText = remember(result) {
        val score = result.adjustedScore ?: result.totalScore
        val pct = (score.toDouble() / result.maxScore * 100).toInt()
        "✨ ${result.boyName} & ${result.girlName} — Compatibility score: ${result.totalScore}/${result.maxScore} ($pct%)\n\nAnalyzed with Destiny AI Astrology\n🔗 destinyaiastrology.com"
    }

    CosmicBackground {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Back + Share toolbar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Gold)
                    }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = {
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, shareText)
                        }
                        context.startActivity(Intent.createChooser(intent, "Share Report"))
                    }) {
                        Icon(Icons.Filled.IosShare, contentDescription = "Share", tint = Gold)
                    }
                }

                // Scrollable content
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Spacer(Modifier.height(4.dp))

                    // 1. Branded header card
                    BrandedHeaderCard(result = result)

                    // 2. Action bar — share report
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(NavySurface)
                            .border(1.dp, Gold.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                            .clickable(onClick = {
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, shareText)
                                }
                                context.startActivity(Intent.createChooser(intent, "Share Report"))
                            })
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.SaveAlt, contentDescription = null, tint = CreamText, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(12.dp))
                        Text("Share Report", fontSize = 16.sp, color = CreamText, modifier = Modifier.weight(1f))
                        Icon(Icons.Filled.IosShare, contentDescription = null, tint = CreamDim, modifier = Modifier.size(14.dp))
                    }

                    // 3. Section cards (parsed from LLM output)
                    if (sections.isEmpty()) {
                        SectionCard(emoji = "📋", title = "Analysis", content = result.summary)
                    } else {
                        sections.forEach { section ->
                            SectionCard(
                                emoji = section.emoji,
                                title = section.title,
                                content = replaceGenericLabels(section.content, result.boyName, result.girlName),
                            )
                        }
                    }

                    // 4. AI Disclaimer Footer
                    DisclaimerFooter()

                    Spacer(Modifier.height(80.dp))
                }
            }

            // Floating context button overlays content
            FloatingContextButton(
                onClick = { showAskDestiny = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(20.dp),
            )
        }
    }

    if (showAskDestiny) {
        AskDestinyDialog(
            boyName = result.boyName,
            girlName = result.girlName,
            summary = result.summary,
            suggestions = result.followUpSuggestions,
            initialPrompt = null,
            onDismiss = { showAskDestiny = false },
        )
    }
}

// ─── Branded Header Card ──────────────────────────────────────────────────────

@Composable
private fun BrandedHeaderCard(result: CompatibilityResult) {
    val errorColor = Color(0xFFFC8181)
    val displayPct = if (result.adjustedScore != null && result.adjustedScore != result.totalScore) {
        result.adjustedScore.toDouble() / result.maxScore
    } else {
        result.scorePercentage
    }
    val displayScore = result.adjustedScore ?: result.totalScore
    val stars = starCount(result)
    val rating = ratingText(result)
    val reportDate = remember {
        SimpleDateFormat("MMMM d, yyyy", Locale.getDefault()).format(Date())
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(NavySurface)
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(Gold.copy(alpha = 0.3f), Gold.copy(alpha = 0.1f), Gold.copy(alpha = 0.3f)),
                ),
                shape = RoundedCornerShape(16.dp),
            )
            .padding(vertical = 24.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Brand label
        Text(
            text = "DESTINY AI ASTROLOGY",
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            color = Gold.copy(alpha = 0.6f),
            letterSpacing = 4.sp,
        )

        // Gold gradient divider
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 40.dp)
                .height(1.dp)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(Gold.copy(alpha = 0f), Gold.copy(alpha = 0.5f), Gold.copy(alpha = 0f)),
                    )
                ),
        )

        // Couple names
        Text(
            text = "${result.boyName} & ${result.girlName}",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = CreamText,
        )

        // Birth dates
        if (result.boyDob != null && result.girlDob != null) {
            Text(
                text = "Born: ${result.boyDob}  •  ${result.girlDob}",
                fontSize = 11.sp,
                color = CreamDim,
            )
        }

        Spacer(Modifier.height(4.dp))

        // Score ring
        Box(modifier = Modifier.size(100.dp), contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.size(100.dp)) {
                val strokePx = 3.dp.toPx()
                val inset = strokePx / 2
                val arcSize = Size(size.width - strokePx, size.height - strokePx)
                // Background track
                drawArc(
                    color = Gold.copy(alpha = 0.15f),
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = strokePx),
                )
                // Progress arc
                drawArc(
                    color = Gold,
                    startAngle = -90f,
                    sweepAngle = (displayPct * 360.0).toFloat(),
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = strokePx, cap = StrokeCap.Round),
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "$displayScore",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = CreamText,
                )
                Text(text = "/ ${result.maxScore}", fontSize = 11.sp, color = CreamDim)
            }
        }

        // Star rating
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(5) { idx ->
                Text(text = if (idx < stars) "★" else "☆", fontSize = 14.sp, color = Gold)
            }
        }

        // Rating label
        Text(
            text = rating.uppercase(),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = Gold,
            letterSpacing = 3.sp,
        )

        // Score transparency note
        if (result.adjustedScore != null && result.adjustedScore != result.totalScore) {
            Text(
                text = adjustedScoreNote(result.totalScore, result.maxScore, result.adjustedScore),
                fontSize = 10.sp,
                color = CreamDim.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
            )
        } else {
            Text(
                text = "Ashtakoot Score: ${result.totalScore}/${result.maxScore}",
                fontSize = 10.sp,
                color = CreamDim.copy(alpha = 0.7f),
            )
        }

        // Rejection reasons
        if (!result.isRecommended && result.rejectionReasons.isNotEmpty()) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = "Not recommended due to:",
                fontSize = 10.sp,
                color = errorColor.copy(alpha = 0.7f),
            )
            result.rejectionReasons.forEach { reason ->
                val displayReason = replaceGenericLabels(reason, result.boyName, result.girlName)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.Top,
                ) {
                    Text("✕ ", fontSize = 9.sp, color = errorColor.copy(alpha = 0.7f))
                    Text(
                        text = displayReason,
                        fontSize = 10.sp,
                        color = CreamDim.copy(alpha = 0.8f),
                        lineHeight = 14.sp,
                    )
                }
            }
        }

        // Report date
        Text(
            text = "Report generated $reportDate",
            fontSize = 10.sp,
            color = CreamDim.copy(alpha = 0.6f),
        )
    }
}

// ─── Section Card ─────────────────────────────────────────────────────────────

@Composable
private fun SectionCard(emoji: String, title: String, content: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(NavySurface.copy(alpha = 0.8f))
            .border(1.dp, Gold.copy(alpha = 0.12f), RoundedCornerShape(14.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Section header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(emoji, fontSize = 18.sp)
            Text(
                text = title.uppercase(),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Gold,
                letterSpacing = 1.5.sp,
            )
        }

        // Gold gradient underline
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(Gold.copy(alpha = 0.4f), Gold.copy(alpha = 0f)),
                    )
                ),
        )

        // Content with basic bold markdown
        Text(
            text = parseMarkdownBold(content),
            style = MaterialTheme.typography.bodyMedium,
            color = CreamText,
            lineHeight = 22.sp,
        )
    }
}

// ─── Disclaimer Footer ────────────────────────────────────────────────────────

@Composable
private fun DisclaimerFooter() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(Gold.copy(alpha = 0f), Gold.copy(alpha = 0.3f), Gold.copy(alpha = 0f)),
                    )
                ),
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("ℹ️", fontSize = 11.sp)
            Text(
                text = "AI GENERATED ANALYSIS",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = Gold.copy(alpha = 0.6f),
            )
        }

        Text(
            text = "This report is generated by AI based on classical Vedic astrology principles. " +
                "Results are for guidance and entertainment purposes only. " +
                "Consult a qualified astrologer for major life decisions.",
            fontSize = 10.sp,
            color = CreamDim.copy(alpha = 0.5f),
            lineHeight = 15.sp,
            textAlign = TextAlign.Center,
        )

        Text(
            text = "© 2026 Destiny AI Astrology · destinyaiastrology.com",
            fontSize = 9.sp,
            color = CreamDim.copy(alpha = 0.4f),
        )
    }
}

// ─── Pure Helpers ─────────────────────────────────────────────────────────────

private fun parseSections(summary: String): List<ReportSection> {
    if (summary.isEmpty()) return emptyList()

    val result = mutableListOf<ReportSection>()
    val lines = summary.split("\n")
    var currentEmoji = ""
    var currentTitle = ""
    val currentContent = mutableListOf<String>()
    var inSection = false

    for (line in lines) {
        val trimmed = line.trim()
        when {
            trimmed.startsWith("### ") -> {
                if (inSection && currentTitle.isNotEmpty()) {
                    val content = currentContent.joinToString("\n").trim()
                    if (content.isNotEmpty()) result.add(ReportSection(currentEmoji, currentTitle, content))
                }
                val (emoji, title) = extractEmojiAndTitle(trimmed.removePrefix("### "))
                currentEmoji = emoji
                currentTitle = title
                currentContent.clear()
                inSection = true
            }
            trimmed == "---" -> { /* skip section dividers */ }
            inSection -> currentContent.add(line)
        }
    }

    if (inSection && currentTitle.isNotEmpty()) {
        val content = currentContent.joinToString("\n").trim()
        if (content.isNotEmpty()) result.add(ReportSection(currentEmoji, currentTitle, content))
    }

    return result.filter { !it.title.contains("SUGGESTED FOLLOW-UP", ignoreCase = true) }
}

private fun extractEmojiAndTitle(text: String): Pair<String, String> {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return "📋" to ""
    val spaceIdx = trimmed.indexOf(' ')
    if (spaceIdx <= 0) return "📋" to trimmed
    val firstToken = trimmed.substring(0, spaceIdx)
    val rest = trimmed.substring(spaceIdx + 1).trim()
    // Non-ASCII first codepoint = emoji or special symbol
    return if (firstToken.codePointAt(0) > 127) firstToken to rest else "📋" to trimmed
}

internal fun replaceGenericLabels(text: String, boyName: String, girlName: String): String =
    text
        .replace("**Boy's ", "**$boyName's ")
        .replace("**Boy:**", "**$boyName:**")
        .replace("**Boy:", "**$boyName:")
        .replace("**Boy (", "**$boyName (")
        .replace("Boy's Key", "$boyName's Key")
        .replace("Boy's Yogas", "$boyName's Yogas")
        .replace("Boy's Dasha", "$boyName's Dasha")
        .replace("Boy (Lagna", "$boyName (Lagna")
        .replace("Boy:", "$boyName:")
        .replace("Boy's ", "$boyName's ")
        .replace("**Girl's ", "**$girlName's ")
        .replace("**Girl:**", "**$girlName:**")
        .replace("**Girl:", "**$girlName:")
        .replace("**Girl (", "**$girlName (")
        .replace("Girl's Key", "$girlName's Key")
        .replace("Girl's Yogas", "$girlName's Yogas")
        .replace("Girl's Dasha", "$girlName's Dasha")
        .replace("Girl (Lagna", "$girlName (Lagna")
        .replace("Girl:", "$girlName:")
        .replace("Girl's ", "$girlName's ")

private fun starCount(result: CompatibilityResult): Int {
    if (!result.isRecommended) return 1
    val pct = result.adjustedPercentage * 100
    return when {
        pct >= 90 -> 5
        pct >= 75 -> 4
        pct >= 60 -> 3
        pct >= 50 -> 2
        else -> 1
    }
}

private fun ratingText(result: CompatibilityResult): String {
    if (!result.isRecommended) return "Not Recommended"
    val pct = result.adjustedPercentage * 100
    return when {
        pct >= 90 -> "Excellent"
        pct >= 75 -> "Very Good"
        pct >= 60 -> "Good"
        pct >= 50 -> "Average"
        else -> "Not Recommended"
    }
}

private fun parseMarkdownBold(text: String): AnnotatedString = buildAnnotatedString {
    val parts = text.split("**")
    parts.forEachIndexed { idx, part ->
        if (idx % 2 == 1) {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(part) }
        } else {
            append(part)
        }
    }
}
