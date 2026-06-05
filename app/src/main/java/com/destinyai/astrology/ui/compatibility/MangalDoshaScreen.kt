@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.destinyai.astrology.ui.compatibility

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
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.destinyai.astrology.domain.model.MangalDoshaModel
import com.destinyai.astrology.ui.theme.CosmicBackground
import com.destinyai.astrology.ui.theme.CreamDim
import com.destinyai.astrology.ui.theme.CreamText
import com.destinyai.astrology.ui.theme.Gold
import com.destinyai.astrology.ui.theme.NavySurface

private enum class MarsScenario { SAFE, CANCELLED, EFFECTIVE }

@Composable
fun MangalDoshaScreen(
    boyData: MangalDoshaModel?,
    girlData: MangalDoshaModel?,
    boyName: String,
    girlName: String,
    mangalCompatibility: Map<String, Any>?,
    onBack: () -> Unit,
) {
    val boyHas = boyData?.hasMangalDosha ?: false
    val girlHas = girlData?.hasMangalDosha ?: false
    val isCancelled = mangalCompatibility?.get("cancellation_occurs") as? Boolean ?: false

    val scenario = when {
        !boyHas && !girlHas -> MarsScenario.SAFE
        isCancelled -> MarsScenario.CANCELLED
        else -> MarsScenario.EFFECTIVE
    }

    CosmicBackground {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Gold)
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Spacer(Modifier.height(4.dp))

                when (scenario) {
                    MarsScenario.SAFE -> SafeScenarioView(boyName = boyName, girlName = girlName)
                    MarsScenario.CANCELLED -> CancelledScenarioView(
                        boyName = boyName, girlName = girlName,
                        boyData = boyData, girlData = girlData,
                        mangalCompatibility = mangalCompatibility,
                    )
                    MarsScenario.EFFECTIVE -> EffectiveScenarioView(
                        boyName = boyName, girlName = girlName,
                        boyData = boyData, girlData = girlData,
                        mangalCompatibility = mangalCompatibility,
                    )
                }

                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

// ─── Scenario 1: Safe ────────────────────────────────────────────────────────

@Composable
private fun SafeScenarioView(boyName: String, girlName: String) {
    val successColor = Color(0xFF48BB78)

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Hero
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(vertical = 20.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(successColor.copy(alpha = 0.15f))
                    .border(1.dp, successColor.copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text("🛡️", fontSize = 50.sp)
            }
            Text("Perfectly Safe", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = successColor)
            Text(
                text = "No Mangal Dosha detected for either partner",
                fontSize = 15.sp, color = CreamDim, textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }

        // Side-by-side person cards
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SafePersonCard(name = boyName, modifier = Modifier.weight(1f))
            SafePersonCard(name = girlName, modifier = Modifier.weight(1f))
        }

        // Educational note card
        MarsGlassCard {
            Text("Why is this good?", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = CreamText)
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Neither partner has Mars placed in any of the sensitive houses (1, 4, 7, 8, 12). This means Mars energy will not create friction in the marital relationship, supporting harmony and longevity.",
                fontSize = 13.sp, color = CreamDim, lineHeight = 20.sp,
            )
        }
    }
}

@Composable
private fun SafePersonCard(name: String, modifier: Modifier = Modifier) {
    val successColor = Color(0xFF48BB78)
    MarsGlassCard(modifier = modifier) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(CircleShape)
                    .background(NavySurface)
                    .border(1.dp, Gold.copy(alpha = 0.3f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Person, contentDescription = null, tint = Gold, modifier = Modifier.size(24.dp))
            }
            Text(name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = CreamText, maxLines = 1)
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(successColor.copy(alpha = 0.15f))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text("Non Manglik", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = successColor)
            }
        }
    }
}

// ─── Scenario 2: Cancelled ────────────────────────────────────────────────────

@Composable
private fun CancelledScenarioView(
    boyName: String,
    girlName: String,
    boyData: MangalDoshaModel?,
    girlData: MangalDoshaModel?,
    mangalCompatibility: Map<String, Any>?,
) {
    val cancelBlue = Color(0xFF4299E1)
    @Suppress("UNCHECKED_CAST")
    val cancellationFactors = (mangalCompatibility?.get("cancellation_factors") as? List<String>) ?: emptyList()
    val reason = mangalCompatibility?.get("cancellation_reason") as? String

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Hero
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(CircleShape)
                    .background(cancelBlue.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Text("✅", fontSize = 40.sp)
            }
            Text("Dosha Cancelled", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = cancelBlue)
            Text(
                text = cancellationFactors.firstOrNull() ?: "The Mangal Dosha has been neutralized by classical exceptions",
                fontSize = 13.sp, color = CreamDim, textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }

        // All cancellation factors (iOS shows full list)
        if (cancellationFactors.size > 1) {
            MarsGlassCard {
                Text("Cancellation Factors", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = CreamDim, letterSpacing = 0.5.sp)
                Spacer(Modifier.height(8.dp))
                cancellationFactors.forEach { factor ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(vertical = 2.dp),
                    ) {
                        Text("✓", fontSize = 12.sp, color = cancelBlue)
                        Text(factor, fontSize = 12.sp, color = CreamText, lineHeight = 18.sp)
                    }
                }
            }
        }

        // Cancellation reason card
        if (!reason.isNullOrBlank()) {
            MarsGlassCard {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("ℹ️", fontSize = 16.sp)
                    Text("Why Cancelled", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = CreamText)
                }
                Spacer(Modifier.height(10.dp))

                val boyHasEx = boyData?.isCancelled ?: false
                val girlHasEx = girlData?.isCancelled ?: false

                if (boyHasEx || girlHasEx) {
                    Text(
                        text = "Classical exceptions apply that neutralize the dosha effect:",
                        fontSize = 13.sp, color = CreamDim, lineHeight = 20.sp,
                    )
                    Spacer(Modifier.height(10.dp))
                    if (boyHasEx) ExceptionPersonBlock(name = boyName, exceptions = boyData!!.exceptions, isCancelled = true, impactSummary = boyData.exceptionImpactSummary, intensityFactors = boyData.activeIntensityFactors)
                    if (girlHasEx) {
                        if (boyHasEx) HorizontalDivider(
                            modifier = Modifier.padding(vertical = 8.dp),
                            color = Color.White.copy(alpha = 0.1f),
                        )
                        ExceptionPersonBlock(name = girlName, exceptions = girlData!!.exceptions, isCancelled = true, impactSummary = girlData.exceptionImpactSummary, intensityFactors = girlData.activeIntensityFactors)
                    }
                } else {
                    Text(reason, fontSize = 13.sp, color = CreamText, lineHeight = 20.sp)
                }
            }
        }

        // Side-by-side person cards
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MarsStatusPersonCard(name = boyName, data = boyData, modifier = Modifier.weight(1f))
            MarsStatusPersonCard(name = girlName, data = girlData, modifier = Modifier.weight(1f))
        }
    }
}

// ─── Scenario 3: Effective ────────────────────────────────────────────────────

@Composable
private fun EffectiveScenarioView(
    boyName: String,
    girlName: String,
    boyData: MangalDoshaModel?,
    girlData: MangalDoshaModel?,
    mangalCompatibility: Map<String, Any>?,
) {
    val warningOrange = Color(0xFFED8936)
    val rawDesc = mangalCompatibility?.get("cancellation_reason") as? String
    val attentionDesc = if (!rawDesc.isNullOrBlank()) {
        rawDesc.replace("Girl", girlName).replace("Boy", boyName)
    } else {
        "Mars placement creates friction in marital matters. Awareness and classical remedies can help."
    }
    val desc = mangalCompatibility?.get("description") as? String
        ?: mangalCompatibility?.get("analysis") as? String
    val boyHasEx = boyData?.isCancelled ?: false
    val girlHasEx = girlData?.isCancelled ?: false

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Hero
        val heroTitle = "Attention Required"
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(CircleShape)
                    .background(warningOrange.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Text("⚠️", fontSize = 40.sp)
            }
            Text(heroTitle, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = warningOrange)
            Text(
                text = attentionDesc,
                fontSize = 13.sp, color = CreamDim, textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }

        // Exception impact summary callout (iOS exceptionImpactSummary)
        val impactSummary = listOfNotNull(boyData?.exceptionImpactSummary, girlData?.exceptionImpactSummary)
            .firstOrNull()
        if (!impactSummary.isNullOrBlank()) {
            MarsGlassCard {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("💡", fontSize = 14.sp)
                    Text(impactSummary, fontSize = 13.sp, color = CreamDim, lineHeight = 20.sp, modifier = Modifier.weight(1f))
                }
            }
        }

        // Intensity factors chips (iOS intensityDescriptions)
        val allIntensityFactors = (boyData?.activeIntensityFactors ?: emptyList()) +
            (girlData?.activeIntensityFactors ?: emptyList())
        if (allIntensityFactors.isNotEmpty()) {
            MarsGlassCard {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("Intensity Factors", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = CreamDim, letterSpacing = 0.5.sp, modifier = Modifier.weight(1f))
                    Text(intensityFactorCountLabel(allIntensityFactors.distinct().size), fontSize = 11.sp, color = warningOrange)
                }
                Spacer(Modifier.height(8.dp))
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    allIntensityFactors.distinct().forEach { factor ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(warningOrange.copy(alpha = 0.12f))
                                .border(1.dp, warningOrange.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        ) {
                            Text(factor, fontSize = 11.sp, color = warningOrange)
                        }
                    }
                }
            }
        }

        // Side-by-side person cards
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MarsStatusPersonCard(name = boyName, data = boyData, modifier = Modifier.weight(1f))
            MarsStatusPersonCard(name = girlName, data = girlData, modifier = Modifier.weight(1f))
        }

        // Mitigating exceptions
        if (boyHasEx || girlHasEx) {
            MarsGlassCard {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("🛡️", fontSize = 16.sp)
                    Text("Mitigating Exceptions", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = CreamText)
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Classical Vedic exceptions reduce the Mars effect:",
                    fontSize = 12.sp, color = CreamDim,
                )
                Spacer(Modifier.height(10.dp))
                if (boyHasEx) ExceptionPersonBlock(name = boyName, exceptions = boyData!!.exceptions, isCancelled = false, impactSummary = boyData.exceptionImpactSummary, intensityFactors = boyData.activeIntensityFactors)
                if (girlHasEx) {
                    if (boyHasEx) HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = Color.White.copy(alpha = 0.1f),
                    )
                    ExceptionPersonBlock(name = girlName, exceptions = girlData!!.exceptions, isCancelled = false, impactSummary = girlData.exceptionImpactSummary, intensityFactors = girlData.activeIntensityFactors)
                }
            }
        }

        // Classical analysis
        if (!desc.isNullOrBlank()) {
            MarsGlassCard {
                Text("✨ Classical Analysis", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Gold)
                Spacer(Modifier.height(8.dp))
                Text(desc, fontSize = 13.sp, color = CreamDim, lineHeight = 20.sp)
            }
        }

        // Remedies
        val allRemedies = ((boyData?.remedies ?: emptyList()) + (girlData?.remedies ?: emptyList())).distinct()
        if (allRemedies.isNotEmpty()) {
            MarsGlassCard {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("🔶", fontSize = 16.sp)
                    Text("Remedies", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = CreamText)
                }
                Spacer(Modifier.height(10.dp))
                allRemedies.forEachIndexed { i, remedy ->
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

// ─── Shared Sub-components ────────────────────────────────────────────────────

@Composable
private fun MarsStatusPersonCard(name: String, data: MangalDoshaModel?, modifier: Modifier = Modifier) {
    val successColor = Color(0xFF48BB78)
    val hasDosha = data?.hasMangalDosha ?: false
    val severity = data?.severity?.lowercase()
    val isCancelledByExceptions = data?.isCancelled ?: false
    val isReduced = data?.isReduced ?: false

    val badgeBg = when {
        !hasDosha -> successColor
        isCancelledByExceptions -> successColor
        severity == "severe" || severity == "high" -> Color.Red
        else -> Color(0xFFFFA500)
    }
    val badgeText = when {
        !hasDosha -> "Non Manglik"
        isCancelledByExceptions -> "Cancelled"
        severity?.isNotBlank() == true -> "${severity!!.replaceFirstChar { it.uppercase() }} Manglik"
        else -> "Manglik"
    }
    val borderColor = when {
        !hasDosha -> Color.White
        isCancelledByExceptions -> successColor
        isReduced -> Color.Yellow
        severity == "severe" || severity == "high" -> Color.Red
        else -> Color(0xFFFFA500)
    }

    MarsGlassCard(modifier = modifier, borderColorOverride = borderColor.copy(alpha = 0.6f)) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = CreamText, maxLines = 1)
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(badgeBg)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text(badgeText, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
            if (hasDosha) {
                val positionText = data?.activeDoshaSourcesDisplay
                    ?: data?.marsHouse?.let { "Mars in House $it" }
                if (positionText != null) {
                    Text(positionText, fontSize = 10.sp, color = CreamDim, textAlign = TextAlign.Center)
                }
            }
            if (hasDosha && !data?.description.isNullOrBlank()) {
                Text(
                    text = data?.description ?: "",
                    fontSize = 10.sp, color = CreamDim, textAlign = TextAlign.Center, lineHeight = 14.sp,
                )
            }
        }
    }
}

@Composable
private fun ExceptionPersonBlock(
    name: String,
    exceptions: List<String>,
    isCancelled: Boolean,
    impactSummary: String? = null,
    intensityFactors: List<String> = emptyList(),
) {
    val successColor = Color(0xFF48BB78)
    val accentColor = if (isCancelled) successColor else Color(0xFFECC94B)
    val warningOrange = Color(0xFFED8936)

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(if (isCancelled) "✓" else "↓", fontSize = 13.sp, color = accentColor)
            Text(
                text = if (impactSummary != null) "$name: $impactSummary"
                       else "$name — ${if (isCancelled) "dosha cancelled" else "exceptions apply"}",
                fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = accentColor,
            )
        }
        exceptions.forEach { ex ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(start = 4.dp),
            ) {
                Text("•", fontSize = 12.sp, color = Gold.copy(alpha = 0.7f))
                Text(localizeExceptionKey(ex), fontSize = 12.sp, color = CreamText, lineHeight = 18.sp)
            }
        }
        if (intensityFactors.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(
                intensityFactorCountLabel(intensityFactors.size),
                fontSize = 11.sp, color = warningOrange,
                modifier = Modifier.padding(start = 4.dp),
            )
            intensityFactors.forEach { factor ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(start = 4.dp),
                ) {
                    Text("•", fontSize = 11.sp, color = warningOrange.copy(alpha = 0.7f))
                    Text(factor, fontSize = 11.sp, color = CreamDim, lineHeight = 17.sp)
                }
            }
        }
    }
}

// ─── Glass Card ───────────────────────────────────────────────────────────────

@Composable
private fun MarsGlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 20.dp,
    borderColorOverride: Color? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val borderBrush = if (borderColorOverride != null) {
        Brush.linearGradient(
            colors = listOf(borderColorOverride, borderColorOverride.copy(alpha = 0.2f)),
            start = Offset(0f, 0f),
            end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY),
        )
    } else {
        Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.4f),
                Color.White.copy(alpha = 0.1f),
                Color.White.copy(alpha = 0.05f),
            ),
            start = Offset(0f, 0f),
            end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY),
        )
    }

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
            .border(1.dp, borderBrush, RoundedCornerShape(cornerRadius))
            .padding(20.dp),
    ) {
        content()
    }
}

// Pure helper — unit testable
internal fun localizeExceptionKey(key: String): String = when (key) {
    "same_dosha_match" -> "Same Dosha Match"
    "jupiter_in_1_2_4_7" -> "Jupiter in Key House"
    "mars_with_jupiter" -> "Mars with Jupiter"
    "mars_with_moon" -> "Mars with Moon"
    "venus_in_1_2_4_7" -> "Venus in Key House"
    "mars_in_capricorn" -> "Mars Exalted in Capricorn"
    "ascendant_aries_scorpio" -> "Aries or Scorpio Ascendant"
    "moon_aries_scorpio" -> "Moon in Aries or Scorpio"
    "mars_in_own_sign" -> "Mars in Own Sign"
    "mars_aspects_own_house" -> "Mars Aspects Own House"
    "both_have_dosha" -> "Both Partners Have Dosha"
    "no_dosha" -> "No Dosha Present"
    else -> key.split("_").joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
}

internal fun localizeExceptionKeysInText(text: String): String {
    val knownKeys = listOf(
        "same_dosha_match", "jupiter_in_1_2_4_7", "mars_with_jupiter", "mars_with_moon",
        "venus_in_1_2_4_7", "mars_in_capricorn", "ascendant_aries_scorpio", "moon_aries_scorpio",
        "mars_in_own_sign", "mars_aspects_own_house", "both_have_dosha", "no_dosha",
    )
    var result = text
    knownKeys.forEach { key -> result = result.replace(key, localizeExceptionKey(key)) }
    return result
}

internal fun intensityFactorCountLabel(count: Int): String =
    if (count == 1) "1 intensifying factor" else "$count intensifying factors"
