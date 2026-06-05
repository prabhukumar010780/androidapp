package com.destinyai.astrology.ui.compatibility

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.destinyai.astrology.domain.model.KutaDetail
import com.destinyai.astrology.ui.theme.CreamDim
import com.destinyai.astrology.ui.theme.CreamText
import com.destinyai.astrology.ui.theme.Gold
import com.destinyai.astrology.ui.theme.NavySurface

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AshtakootGlassGrid(
    kutas: List<KutaDetail>,
    selectedKuta: KutaDetail?,
    onKutaSelected: (KutaDetail?) -> Unit,
    modifier: Modifier = Modifier,
) {
    // iOS parity (AshtakootGlassGrid.swift:148-216): pill tap opens a description sheet.
    var detailKuta by remember { mutableStateOf<KutaDetail?>(null) }

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
                            // Toggle parent selection (highlight) AND open the details sheet,
                            // matching the iOS behavior where tapping reveals a description sheet.
                            onKutaSelected(if (selectedKuta?.key == kuta.key) null else kuta)
                            detailKuta = kuta
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }

    detailKuta?.let { kuta ->
        ModalBottomSheet(
            onDismissRequest = { detailKuta = null },
            containerColor = NavySurface,
        ) {
            KutaDetailSheetContent(kuta = kuta)
        }
    }
}

@Composable
private fun KutaDetailSheetContent(kuta: KutaDetail) {
    val statusColor = glassPillStatusColor(
        doshaPresent = kuta.doshaPresent,
        doshaCancelled = kuta.doshaCancelled,
        score = kuta.score,
        maxScore = kuta.maxScore,
        key = kuta.key,
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .semantics { contentDescription = "kuta_detail_sheet" },
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(kutaIcons[kuta.key] ?: "✦", fontSize = 24.sp)
            Spacer(Modifier.width(10.dp))
            Text(
                kuta.label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = CreamText,
                modifier = Modifier.weight(1f),
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(statusColor.copy(alpha = 0.18f))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text(
                    "${formatScore(kuta.displayScore)}/${formatScore(kuta.maxScore)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = statusColor,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        if (kuta.doshaPresent) {
            val doshaColor = if (kuta.doshaCancelled) Color(0xFF48BB78) else Color(0xFFFC8181)
            Text(
                if (kuta.doshaCancelled) "✓ Dosha cancelled" else "⚠ Dosha active",
                style = MaterialTheme.typography.bodySmall,
                color = doshaColor,
            )
        }
        Text(
            text = kuta.description.ifBlank { kuta.plainEnglishSummary.orEmpty() },
            style = MaterialTheme.typography.bodyMedium,
            color = CreamDim,
            lineHeight = 20.sp,
        )
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
fun GlassPill(
    kuta: KutaDetail,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val successColor = Color(0xFF48BB78)
    val errorColor = Color(0xFFFC8181)
    val statusColor = glassPillStatusColor(
        doshaPresent = kuta.doshaPresent,
        doshaCancelled = kuta.doshaCancelled,
        score = kuta.score,
        maxScore = kuta.maxScore,
        key = kuta.key,
    )
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

// Pure color logic — iOS parity: nadi/bhakoot at 0 always red regardless of dosha flag

internal fun glassPillStatusColor(
    doshaPresent: Boolean,
    doshaCancelled: Boolean,
    score: Double,
    maxScore: Double,
    key: String,
): Color {
    val label = glassPillStatusColorLabel(doshaPresent, doshaCancelled, score, maxScore, key)
    return when (label) {
        "green" -> Color(0xFF48BB78)
        "red" -> Color(0xFFFC8181)
        else -> Color(0xFFFFD700)
    }
}

internal fun glassPillStatusColorLabel(
    doshaPresent: Boolean,
    doshaCancelled: Boolean,
    score: Double,
    maxScore: Double,
    key: String,
): String {
    if (doshaPresent && doshaCancelled) return "green"
    if (doshaPresent && !doshaCancelled) return "red"
    // nadi / bhakoot at zero are always red (critical doshas)
    if (score == 0.0 && (key == "nadi" || key == "bhakoot")) return "red"
    val ratio = if (maxScore > 0) score / maxScore else 0.0
    return when {
        ratio >= 0.8 || ratio == 1.0 -> "green"
        ratio < 0.25 -> "red"
        else -> "yellow"
    }
}
