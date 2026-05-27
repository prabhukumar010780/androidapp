package com.destinyai.astrology.ui.compatibility

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
    val firstFactor = (mangalCompatibility?.get("cancellation_factors") as? List<String>)?.firstOrNull()
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
                text = firstFactor ?: "The Mangal Dosha has been neutralized by classical exceptions",
                fontSize = 13.sp, color = CreamDim, textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
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

                val boyHasEx = boyData?.exceptions?.isNotEmpty() == true
                val girlHasEx = girlData?.exceptions?.isNotEmpty() == true

                if (boyHasEx || girlHasEx) {
                    Text(
                        text = "Classical exceptions apply that neutralize the dosha effect:",
                        fontSize = 13.sp, color = CreamDim, lineHeight = 20.sp,
                    )
                    Spacer(Modifier.height(10.dp))
                    if (boyHasEx) ExceptionPersonBlock(name = boyName, exceptions = boyData!!.exceptions, isCancelled = true)
                    if (girlHasEx) {
                        if (boyHasEx) HorizontalDivider(
                            modifier = Modifier.padding(vertical = 8.dp),
                            color = Color.White.copy(alpha = 0.1f),
                        )
                        ExceptionPersonBlock(name = girlName, exceptions = girlData!!.exceptions, isCancelled = true)
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
    val boyHasEx = boyData?.exceptions?.isNotEmpty() == true
    val girlHasEx = girlData?.exceptions?.isNotEmpty() == true

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
                    .background(warningOrange.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Text("⚠️", fontSize = 40.sp)
            }
            Text("Attention Required", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = warningOrange)
            Text(
                text = attentionDesc,
                fontSize = 13.sp, color = CreamDim, textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
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
                if (boyHasEx) ExceptionPersonBlock(name = boyName, exceptions = boyData!!.exceptions, isCancelled = false)
                if (girlHasEx) {
                    if (boyHasEx) HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = Color.White.copy(alpha = 0.1f),
                    )
                    ExceptionPersonBlock(name = girlName, exceptions = girlData!!.exceptions, isCancelled = false)
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
    }
}

// ─── Shared Sub-components ────────────────────────────────────────────────────

@Composable
private fun MarsStatusPersonCard(name: String, data: MangalDoshaModel?, modifier: Modifier = Modifier) {
    val successColor = Color(0xFF48BB78)
    val hasDosha = data?.hasMangalDosha ?: false
    val severity = data?.severity?.lowercase()
    val isCancelledByExceptions = hasDosha && data?.exceptions?.isNotEmpty() == true

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
            if (hasDosha && data?.marsHouse != null) {
                Text(
                    "Mars in House ${data.marsHouse}",
                    fontSize = 10.sp, color = CreamDim, textAlign = TextAlign.Center,
                )
            }
            if (hasDosha && !data?.description.isNullOrBlank()) {
                Text(
                    text = (data?.description ?: "").take(80),
                    fontSize = 10.sp, color = CreamDim, textAlign = TextAlign.Center, lineHeight = 14.sp,
                )
            }
        }
    }
}

@Composable
private fun ExceptionPersonBlock(name: String, exceptions: List<String>, isCancelled: Boolean) {
    val successColor = Color(0xFF48BB78)
    val accentColor = if (isCancelled) successColor else Color(0xFFECC94B)

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(if (isCancelled) "✓" else "↓", fontSize = 13.sp, color = accentColor)
            Text(
                text = "$name — ${if (isCancelled) "dosha cancelled" else "exceptions apply"}",
                fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = accentColor,
            )
        }
        exceptions.forEach { ex ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(start = 4.dp),
            ) {
                Text("•", fontSize = 12.sp, color = Gold.copy(alpha = 0.7f))
                Text(ex, fontSize = 12.sp, color = CreamText, lineHeight = 18.sp)
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
