package com.destinyai.astrology.ui.compatibility

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.destinyai.astrology.R
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
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    Column(
        modifier = modifier.semantics { contentDescription = "ashtakoot_glass_grid" },
    ) {
        Text(
            text = stringResource(R.string.ashtakoot_kutas_section),
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
                        onClick = {
                            // iOS GlassPill has no isSelected highlight — tap only opens the sheet.
                            // Still notify parent so other surfaces (orbit) can sync if needed.
                            onKutaSelected(kuta)
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
            sheetState = sheetState,
            containerColor = NavySurface,
            // iOS parity: explicit capsule grip at the top of the sheet (AshtakootGlassGrid.swift:190-193).
            dragHandle = {
                Box(
                    modifier = Modifier
                        .padding(top = 10.dp, bottom = 4.dp)
                        .width(40.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.White.copy(alpha = 0.30f))
                        .semantics { contentDescription = "kuta_sheet_drag_handle" },
                )
            },
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
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // iOS parity (AshtakootGlassGrid.swift:195-197): a single 48pt SF Symbol tinted statusColor at the top.
        kutaVectorIcons[kuta.key]?.let { vector ->
            Icon(
                imageVector = vector,
                contentDescription = "kuta_${kuta.key}_icon",
                tint = statusColor,
                modifier = Modifier
                    .size(48.dp)
                    .semantics { contentDescription = "kuta_detail_icon_${kuta.key}" },
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
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
        // iOS parity (AshtakootGlassGrid.swift:187-215): the kuta detail sheet shows ONLY
        // icon, label, score, and description. Cancellation status is NOT rendered here —
        // it surfaces on the global cancelled-doshas summary banner in CompatibilityResultScreen
        // and the orbit/comparison badges. Keeping the sheet minimal preserves iOS parity and
        // avoids duplicating the same status across multiple surfaces.
        // iOS uses a localized placeholder when no rich description is provided. Mirror that
        // fallback chain so we never display the empty string on Android either.
        val descriptionText = kuta.description.ifBlank {
            kuta.plainEnglishSummary?.takeIf { it.isNotBlank() }
                ?: stringResource(R.string.kuta_detail_placeholder_format_android, kuta.label)
        }
        Text(
            text = descriptionText,
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
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val statusColor = glassPillStatusColor(
        doshaPresent = kuta.doshaPresent,
        doshaCancelled = kuta.doshaCancelled,
        score = kuta.score,
        maxScore = kuta.maxScore,
        key = kuta.key,
    )
    // iOS GlassPill has no selection highlight — uses a fixed 30% statusColor stroke.
    val borderColor by animateColorAsState(
        targetValue = statusColor.copy(alpha = 0.3f),
        animationSpec = tween(durationMillis = 200),
        label = "glass_pill_border",
    )
    val borderWidth by animateDpAsState(
        targetValue = 1.dp,
        animationSpec = tween(durationMillis = 200),
        label = "glass_pill_border_width",
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            // ultraThinMaterial parity stub — translucent navy + faint gradient overlay until
            // an AGSL blur is wired in. Strict glass sampling is tracked separately.
            .background(NavySurface.copy(alpha = 0.55f))
            .border(borderWidth, borderColor, RoundedCornerShape(12.dp))
            .clickable {
                // iOS triggers HapticManager.play(.light) — closest Android equivalent is TextHandleMove.
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .padding(horizontal = 10.dp, vertical = 10.dp)
            .semantics { contentDescription = "kuta_pill_${kuta.key}" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(statusColor),
        )
        Spacer(Modifier.width(6.dp))
        // iOS parity: tinted SF Symbol — Android draws the matching vector tinted with statusColor.
        kutaVectorIcons[kuta.key]?.let { vector ->
            Icon(
                imageVector = vector,
                contentDescription = null,
                tint = CreamDim,
                modifier = Modifier.size(14.dp),
            )
        }
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
