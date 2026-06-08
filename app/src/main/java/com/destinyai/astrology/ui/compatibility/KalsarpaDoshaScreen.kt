@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.destinyai.astrology.ui.compatibility

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.destinyai.astrology.R
import com.destinyai.astrology.domain.model.KalaSarpaModel
import com.destinyai.astrology.ui.theme.CosmicBackground
import com.destinyai.astrology.ui.theme.CreamDim
import com.destinyai.astrology.ui.theme.CreamText
import com.destinyai.astrology.ui.theme.Gold
import com.destinyai.astrology.ui.theme.NavySurface
import com.destinyai.astrology.ui.theme.TextTertiary
import kotlin.random.Random

private enum class KalsarpaScenario { NONE, ONE_BOY, ONE_GIRL, BOTH }

/**
 * Issue 8: `onBack` lambda is the Android equivalent of iOS `NavigationStack`-provided
 * back button — both expose the same back affordance with localized
 * `kalsarpa_back_cd` content description.
 *
 * Issue 12: RTL support is handled automatically by Compose via `LocalLayoutDirection`,
 * which mirrors all Row arrangements and Modifier paddings under Arabic/Hebrew locales.
 * No explicit handling is required at this screen level.
 */
@Composable
fun KalsarpaDoshaScreen(
    boyData: KalaSarpaModel?,
    girlData: KalaSarpaModel?,
    boyName: String,
    girlName: String,
    onBack: () -> Unit,
    isLoading: Boolean = false,
    errorMessage: String? = null,
) {
    val boyHas = boyData?.isPresent ?: false
    val girlHas = girlData?.isPresent ?: false

    val scenario = when {
        boyHas && girlHas -> KalsarpaScenario.BOTH
        boyHas -> KalsarpaScenario.ONE_BOY
        girlHas -> KalsarpaScenario.ONE_GIRL
        else -> KalsarpaScenario.NONE
    }

    CosmicBackground {
        // Issue 1: subtle 20-star randomized overlay matching iOS starFieldOverlay.
        StarFieldOverlay()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .semantics { contentDescription = "kalsarpa_dosha_screen" },
        ) {
            // Issue 2: render localized title alongside the back arrow (iOS navigationTitle parity).
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 8.dp)
                    .semantics { contentDescription = "kalsarpa_top_bar" },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.semantics { contentDescription = "kalsarpa_back_button" },
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.kalsarpa_back_cd),
                        tint = Gold,
                    )
                }
                Text(
                    text = stringResource(R.string.kalsarpa_analysis),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = CreamText,
                    modifier = Modifier.semantics { contentDescription = "kalsarpa_analysis_title" },
                )
            }

            when {
                // Issue 11: loading state composable.
                isLoading -> KalsarpaLoadingView()
                // Issue 11: error state composable.
                errorMessage != null -> KalsarpaErrorView(message = errorMessage)
                // Issue 7: empty-state for missing data when both are null (parity with iOS noDataView).
                boyData == null && girlData == null -> KalsarpaNoDataView()
                else -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Spacer(Modifier.height(4.dp))

                    when (scenario) {
                        KalsarpaScenario.NONE -> DivineProtectionView(boyName = boyName, girlName = girlName)
                        KalsarpaScenario.ONE_BOY -> SingleDoshaView(
                            affectedName = boyName, safeName = girlName, affectedData = boyData,
                        )
                        KalsarpaScenario.ONE_GIRL -> SingleDoshaView(
                            affectedName = girlName, safeName = boyName, affectedData = girlData,
                        )
                        KalsarpaScenario.BOTH -> MutualDoshaView(
                            boyName = boyName, girlName = girlName, boyData = boyData, girlData = girlData,
                        )
                    }

                    Spacer(Modifier.height(32.dp))
                }
            }
        }
    }
}

// ─── Issue 1: Star Field Overlay ─────────────────────────────────────────────

@Composable
private fun StarFieldOverlay() {
    // 20 randomized stars — seeded once per composition so positions are stable.
    val stars = remember {
        val rng = Random(42)
        List(20) {
            Triple(
                rng.nextFloat(), // x ratio
                rng.nextFloat(), // y ratio
                Pair(
                    1f + rng.nextFloat(), // radius 1..2 px
                    0.1f + rng.nextFloat() * 0.2f, // alpha 0.1..0.3
                ),
            )
        }
    }
    // Subtle twinkle animation using existing animateFloat.
    val transition = rememberInfiniteTransition(label = "kalsarpa-stars")
    val twinkle by transition.animateFloat(
        initialValue = 0.7f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "twinkle",
    )

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .semantics { contentDescription = "kalsarpa_star_field" },
    ) {
        stars.forEach { (xRatio, yRatio, sizeAlpha) ->
            val (radius, alpha) = sizeAlpha
            drawCircle(
                color = Color.White.copy(alpha = alpha * twinkle),
                radius = radius,
                center = Offset(xRatio * size.width, yRatio * size.height),
            )
        }
    }
}

// ─── Issue 11: Loading / Error / Empty composables ───────────────────────────

@Composable
private fun KalsarpaLoadingView() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(50.dp)
            .semantics { contentDescription = "kalsarpa_loading_view" },
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(color = Gold)
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(R.string.kalsarpa_loading),
            fontSize = 16.sp,
            color = CreamDim,
        )
    }
}

@Composable
private fun KalsarpaErrorView(message: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(50.dp)
            .semantics { contentDescription = "kalsarpa_error_view" },
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(NavySurface),
            contentAlignment = Alignment.Center,
        ) {
            Text("⚠️", fontSize = 32.sp)
        }
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(R.string.kalsarpa_error),
            fontSize = 16.sp,
            color = CreamText,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            message,
            fontSize = 13.sp,
            color = CreamDim,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun KalsarpaNoDataView() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(50.dp)
            .semantics { contentDescription = "kalsarpa_no_data_view" },
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(NavySurface),
            contentAlignment = Alignment.Center,
        ) {
            Text("?", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = TextTertiary)
        }
        Spacer(Modifier.height(20.dp))
        Text(
            stringResource(R.string.no_kalsarpa_data),
            fontSize = 16.sp,
            color = CreamDim,
            textAlign = TextAlign.Center,
        )
    }
}

// ─── Scenario 1: Divine Protection (None) ────────────────────────────────────

@Composable
private fun DivineProtectionView(boyName: String, girlName: String) {
    val successColor = Color(0xFF48BB78)

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Hero
        Box(
            modifier = Modifier.padding(top = 20.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(160.dp)
                    .clip(CircleShape)
                    .background(successColor.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(140.dp)
                        .border(1.dp, successColor.copy(alpha = 0.3f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("🛡️", fontSize = 60.sp)
                }
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 16.dp),
        ) {
            Text(
                stringResource(R.string.kalsarpa_divine_protection_title),
                fontSize = 24.sp, fontWeight = FontWeight.Bold, color = CreamText,
            )
            Text(
                text = stringResource(
                    R.string.kalsarpa_divine_protection_message_named,
                    boyName,
                    girlName,
                ),
                fontSize = 15.sp, color = CreamDim, textAlign = TextAlign.Center, lineHeight = 22.sp,
            )
        }

        // Benefits card
        KalGlassCard {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("✨", fontSize = 16.sp)
                Text(
                    stringResource(R.string.kalsarpa_relationship_benefits),
                    fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = CreamText,
                )
            }
            Spacer(Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                BenefitItem(icon = "❤️", text = stringResource(R.string.kalsarpa_emotional_harmony_short))
                BenefitItem(icon = "⬆️", text = stringResource(R.string.kalsarpa_smooth_progression_short))
                BenefitItem(icon = "☀️", text = stringResource(R.string.kalsarpa_positive_energy_short))
            }
        }
    }
}

@Composable
private fun BenefitItem(icon: String, text: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.width(90.dp),
    ) {
        Text(icon, fontSize = 32.sp)
        Text(text, fontSize = 12.sp, color = CreamDim, textAlign = TextAlign.Center, lineHeight = 16.sp)
    }
}

// ─── Scenario 2: Single Dosha ─────────────────────────────────────────────────

@Composable
private fun SingleDoshaView(
    affectedName: String,
    safeName: String,
    affectedData: KalaSarpaModel?,
) {
    val errorColor = Color(0xFFFC8181)
    val successColor = Color(0xFF48BB78)

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        // Split comparison card
        KalGlassCard {
            Row(modifier = Modifier.fillMaxWidth()) {
                // Affected side
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .clip(CircleShape)
                            .background(errorColor.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("🐍", fontSize = 30.sp)
                    }
                    Text(affectedName, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = CreamText, maxLines = 1)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(errorColor.copy(alpha = 0.1f))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        Text(
                            stringResource(R.string.kalsarpa_has_dosha),
                            fontSize = 10.sp, fontWeight = FontWeight.Bold, color = errorColor,
                        )
                    }
                }

                // Divider
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(80.dp)
                        .background(Color.White.copy(alpha = 0.2f))
                        .align(Alignment.CenterVertically),
                )

                // Safe side
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .clip(CircleShape)
                            .background(successColor.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("🛡️", fontSize = 28.sp)
                    }
                    Text(safeName, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = CreamText, maxLines = 1)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(successColor.copy(alpha = 0.1f))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        Text(
                            stringResource(R.string.kalsarpa_protected),
                            fontSize = 10.sp, fontWeight = FontWeight.Bold, color = successColor,
                        )
                    }
                }
            }
        }

        // Analysis title
        if (affectedData != null) {
            Text(
                text = stringResource(R.string.kalsarpa_analysis_title_named, affectedName),
                fontSize = 20.sp, fontWeight = FontWeight.Bold, color = CreamText,
            )
            DoshaDetailsCard(data = affectedData)
            DoshaRemediesCard(data = affectedData)
        }
    }
}

// ─── Scenario 3: Both Have Dosha ─────────────────────────────────────────────

@Composable
private fun MutualDoshaView(
    boyName: String,
    girlName: String,
    boyData: KalaSarpaModel?,
    girlData: KalaSarpaModel?,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Hero
        Box(
            modifier = Modifier
                .padding(top = 10.dp)
                .size(140.dp)
                .clip(CircleShape)
                .background(Gold.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy((-12).dp)) {
                Text("🐍", fontSize = 50.sp)
                Text("🐍", fontSize = 50.sp)
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 16.dp),
        ) {
            Text(
                stringResource(R.string.kalsarpa_mutual_kalsarpa),
                fontSize = 24.sp, fontWeight = FontWeight.Bold, color = CreamText,
            )
            Text(
                stringResource(R.string.kalsarpa_dosha_samya),
                fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Gold,
            )
            Text(
                text = stringResource(R.string.kalsarpa_mutual_message_short),
                fontSize = 15.sp, color = CreamDim, textAlign = TextAlign.Center, lineHeight = 22.sp,
            )
        }

        // Compact partner rows
        KalGlassCard {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (boyData != null) CompactDoshaRow(name = boyName, data = boyData)
                if (boyData != null && girlData != null) {
                    HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                }
                if (girlData != null) CompactDoshaRow(name = girlName, data = girlData)
            }
        }

        // Shared remedies (iOS combinedRemedies — union of both partners' remedies)
        val sharedRemedies = kalsarpaSharedRemedies(boyData?.remedies ?: emptyList(), girlData?.remedies ?: emptyList())
        if (sharedRemedies.isNotEmpty()) {
            KalGlassCard {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("🔶", fontSize = 16.sp)
                    Text(
                        stringResource(R.string.kalsarpa_shared_remedies),
                        fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = CreamText,
                    )
                }
                Spacer(Modifier.height(12.dp))
                sharedRemedies.forEachIndexed { i, remedy ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(bottom = 8.dp),
                    ) {
                        Text(
                            "${i + 1}.",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Gold,
                            modifier = Modifier.width(20.dp),
                        )
                        Text(remedy, fontSize = 13.sp, color = CreamDim, lineHeight = 20.sp, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactDoshaRow(name: String, data: KalaSarpaModel) {
    val severityColor = when (data.severity?.lowercase()) {
        "mild" -> Color(0xFFECC94B)
        "moderate" -> Color(0xFFFFA500)
        "severe" -> Color(0xFFFC8181)
        else -> CreamDim
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color.Black.copy(alpha = 0.25f))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(name, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = CreamText)
            if (!data.doshaName.isNullOrBlank()) {
                Text(data.doshaName, fontSize = 12.sp, color = Gold)
            }
        }
        if (!data.severity.isNullOrBlank()) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color.Black.copy(alpha = 0.3f))
                    .border(1.dp, severityColor.copy(alpha = 0.3f), RoundedCornerShape(50))
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(severityColor),
                )
                Text(
                    data.severity.replaceFirstChar { it.uppercase() },
                    fontSize = 11.sp, fontWeight = FontWeight.Medium, color = CreamDim,
                )
            }
        }
    }
}

// ─── Dosha Details Card ───────────────────────────────────────────────────────

@Composable
private fun DoshaDetailsCard(data: KalaSarpaModel) {
    KalGlassCard {
        // Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center,
            ) {
                Text("✦", fontSize = 14.sp, color = Gold)
            }
            Text(
                stringResource(R.string.dosha_details),
                fontSize = 15.sp, fontWeight = FontWeight.Bold, color = CreamText,
            )
        }

        Spacer(Modifier.height(12.dp))

        // Dosha name row
        val displayName = data.doshaName ?: data.yogaName ?: "Kaal Sarp"
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Black.copy(alpha = 0.3f))
                .border(1.dp, Gold.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("🐍", fontSize = 24.sp)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    stringResource(R.string.kalsarpa_dosha_full_named, displayName),
                    fontSize = 16.sp, fontWeight = FontWeight.Bold, color = CreamText,
                )
                if (!data.axis.isNullOrBlank()) {
                    Text(data.axis, fontSize = 12.sp, color = CreamDim)
                }
            }
        }

        // Named dosha description (iOS namedDoshaDescription card)
        val doshaDesc = kalsarpaDoshaDescription(displayName)
        if (doshaDesc.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black.copy(alpha = 0.2f))
                    .border(1.dp, Gold.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("📖", fontSize = 14.sp)
                Text(doshaDesc, fontSize = 12.sp, color = CreamDim, lineHeight = 20.sp, modifier = Modifier.weight(1f))
            }
        }

        // Life areas
        if (data.lifeAreas.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("🔮", fontSize = 14.sp)
                Text(
                    stringResource(R.string.affected_areas),
                    fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = CreamDim,
                )
            }
            Spacer(Modifier.height(8.dp))
            // Wrap chips in rows of 3
            data.lifeAreas.chunked(3).forEach { rowAreas ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(bottom = 6.dp),
                ) {
                    rowAreas.forEach { area ->
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(Color.Black.copy(alpha = 0.35f))
                                .border(1.dp, Gold.copy(alpha = 0.3f), RoundedCornerShape(50))
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(areaIcon(area), fontSize = 11.sp)
                            Text(area, fontSize = 11.sp, color = CreamText)
                        }
                    }
                }
            }
        }

        // Severity
        if (!data.severity.isNullOrBlank()) {
            Spacer(Modifier.height(8.dp))
            val intColor = when (data.severity.lowercase()) {
                "mild" -> Color(0xFFECC94B)
                "moderate" -> Color(0xFFFFA500)
                "severe" -> Color(0xFFFC8181)
                else -> CreamDim
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.Black.copy(alpha = 0.3f))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("⚡", fontSize = 16.sp)
                Column {
                    Text(
                        stringResource(R.string.kalsarpa_intensity_label),
                        fontSize = 12.sp, color = CreamDim,
                    )
                    Text(
                        data.severity.replaceFirstChar { it.uppercase() },
                        fontSize = 14.sp, fontWeight = FontWeight.Bold, color = intColor,
                    )
                }
            }
        }

        // Description / analysis
        if (!data.description.isNullOrBlank()) {
            Spacer(Modifier.height(12.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("📝", fontSize = 14.sp)
                Text(
                    stringResource(R.string.analysis_notes),
                    fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = CreamDim,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = data.description,
                fontSize = 13.sp, color = CreamDim, lineHeight = 20.sp,
            )
        }

        // Structured analysis notes (iOS analysisNotes: [String])
        if (!data.analysisNotes.isNullOrEmpty()) {
            Spacer(Modifier.height(12.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("📝", fontSize = 14.sp)
                Text(
                    stringResource(R.string.analysis_notes),
                    fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = CreamDim,
                )
            }
            Spacer(Modifier.height(8.dp))
            data.analysisNotes.forEach { note ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(bottom = 6.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Text("•", fontSize = 13.sp, color = Gold.copy(alpha = 0.7f))
                    Text(note, fontSize = 13.sp, color = CreamDim, lineHeight = 20.sp, modifier = Modifier.weight(1f))
                }
            }
        }

        // Planets involved chips (iOS planetsInvolved)
        if (!data.planetsInvolved.isNullOrEmpty()) {
            Spacer(Modifier.height(12.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("🪐", fontSize = 14.sp)
                Text(
                    stringResource(R.string.planets_involved),
                    fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = CreamDim,
                )
            }
            Spacer(Modifier.height(8.dp))
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                data.planetsInvolved.forEach { planet ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Gold.copy(alpha = 0.1f))
                            .border(1.dp, Gold.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        Text(planet, fontSize = 11.sp, color = Gold)
                    }
                }
            }
        }

        // Peak period
        if (!data.peakPeriod.isNullOrBlank()) {
            Spacer(Modifier.height(14.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("⏳", fontSize = 14.sp)
                Text(
                    stringResource(R.string.peak_period),
                    fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = CreamDim,
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.Black.copy(alpha = 0.3f))
                    .border(1.dp, Gold.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("⚡", fontSize = 16.sp)
                Text(data.peakPeriod, fontSize = 13.sp, color = CreamText, lineHeight = 20.sp, modifier = Modifier.weight(1f))
            }
        }

        // Remedies (moved to separate card outside DoshaDetailsCard — iOS parity)
    }
}

@Composable
private fun DoshaRemediesCard(data: KalaSarpaModel) {
    if (data.remedies.isEmpty()) return
    KalGlassCard {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("🔶", fontSize = 14.sp)
            Text(
                stringResource(R.string.kalsarpa_remedies_title),
                fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = CreamDim,
            )
        }
        Spacer(Modifier.height(8.dp))
        data.remedies.forEachIndexed { i, remedy ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(bottom = 8.dp),
            ) {
                Text(
                    "${i + 1}.",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Gold,
                    modifier = Modifier.width(20.dp),
                )
                Text(remedy, fontSize = 13.sp, color = CreamDim, lineHeight = 20.sp, modifier = Modifier.weight(1f))
            }
        }
    }
}

// ─── Pure Helpers ─────────────────────────────────────────────────────────────

private fun areaIcon(area: String): String = when (area.lowercase()) {
    "mother" -> "👩"
    "home" -> "🏠"
    "emotions" -> "💭"
    "career" -> "💼"
    "health" -> "❤️"
    "wealth" -> "💰"
    "marriage" -> "💒"
    "children" -> "👶"
    "education" -> "📚"
    else -> "•"
}

// ─── Glass Card ───────────────────────────────────────────────────────────────

@Composable
private fun KalGlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 20.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(cornerRadius))
            .background(Color.Black.copy(alpha = 0.45f))
            .background(
                Brush.linearGradient(
                    colors = listOf(Color.White.copy(alpha = 0.08f), Color.White.copy(alpha = 0f)),
                    start = Offset(0f, 0f),
                    end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY),
                )
            )
            .border(
                1.dp,
                Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.4f),
                        Color.White.copy(alpha = 0.1f),
                        Color.White.copy(alpha = 0.05f),
                    ),
                    start = Offset(0f, 0f),
                    end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY),
                ),
                RoundedCornerShape(cornerRadius),
            )
            .padding(20.dp),
    ) {
        content()
    }
}

internal fun kalsarpaSharedRemedies(boyRemedies: List<String>, girlRemedies: List<String>): List<String> =
    (boyRemedies + girlRemedies).distinct().take(3)

// Pure helper — unit testable
internal fun kalsarpaDoshaDescription(yogaName: String): String = when (yogaName) {
    "Anant" -> "Anant Kalsarpa Yoga forms when Rahu is in the first house and Ketu in the seventh. It intensifies ambition and self-reliance, but can bring struggles in partnerships and a restless, driven nature."
    "Kulik" -> "Kulik Kalsarpa Yoga arises when Rahu occupies the second house and Ketu the eighth. It creates challenges around accumulated wealth, family bonds, and speech, while deepening interest in hidden knowledge."
    "Vasuki" -> "Vasuki Kalsarpa Yoga occurs with Rahu in the third house and Ketu in the ninth. It strengthens courage and determination but may bring tensions with siblings and test one's faith and higher beliefs."
    "Shankhpal" -> "Shankhpal Kalsarpa Yoga forms with Rahu in the fourth house and Ketu in the tenth. Home life and inner peace may be disrupted, yet career can see unconventional rise through persistent effort."
    "Padma" -> "Padma Kalsarpa Yoga arises when Rahu is in the fifth house and Ketu in the eleventh. Creativity and intelligence are heightened, but issues around children and speculative gains may surface."
    "Mahapadma" -> "Mahapadma Kalsarpa Yoga occurs with Rahu in the sixth house and Ketu in the twelfth. Enemies and debts may prove challenging, yet strong capacity to overcome adversity and achieve spiritual liberation."
    "Takshak" -> "Takshak Kalsarpa Yoga forms when Rahu occupies the seventh house and Ketu the first. Partnerships and marriage become central karmic lessons, often bringing repeated cycles of union and separation."
    "Karkotak" -> "Karkotak Kalsarpa Yoga arises with Rahu in the eighth house and Ketu in the second. Sudden transformations, inheritance disputes, and hidden fears define the life path, alongside deep occult interest."
    "Shankhachud" -> "Shankhachud Kalsarpa Yoga occurs when Rahu is in the ninth house and Ketu in the third. Karmic challenges with father, religion, and long journeys shape destiny, requiring persistent ethical effort."
    "Ghatak" -> "Ghatak Kalsarpa Yoga forms with Rahu in the tenth house and Ketu in the fourth. Career ambitions can be thwarted by hidden enemies and erratic reputation shifts, demanding resilience and adaptability."
    "Vishdhar" -> "Vishdhar Kalsarpa Yoga arises when Rahu is in the eleventh house and Ketu in the fifth. Gains and friendships are karmic themes; children and speculative ventures require careful attention and patience."
    "Sheshnag" -> "Sheshnag Kalsarpa Yoga occurs with Rahu in the twelfth house and Ketu in the sixth. Losses, exile, and hidden expenses may recur, yet deep spiritual progress and liberation are strongly indicated."
    else -> "This Kalsarpa Yoga pattern creates a specific karmic axis in the chart. All seven planets fall between Rahu and Ketu, focusing life themes intensely on the affected houses and calling for conscious karmic resolution."
}
