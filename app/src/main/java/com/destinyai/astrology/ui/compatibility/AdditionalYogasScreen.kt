package com.destinyai.astrology.ui.compatibility

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.destinyai.astrology.ui.theme.CosmicBackground
import com.destinyai.astrology.ui.theme.CreamDim
import com.destinyai.astrology.ui.theme.CreamText
import com.destinyai.astrology.ui.theme.Gold
import com.destinyai.astrology.ui.theme.NavySurface
import com.destinyai.astrology.ui.theme.NavyVariant

@Composable
fun AdditionalYogasScreen(
    boyYogas: Map<String, Any>?,
    girlYogas: Map<String, Any>?,
    boyName: String,
    girlName: String,
    onBack: () -> Unit,
) {
    var selectedPartner by remember { mutableIntStateOf(0) }
    val currentData = if (selectedPartner == 0) boyYogas else girlYogas
    val currentName = if (selectedPartner == 0) boyName else girlName

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

            // Partner tab selector
            PartnerTabRow(
                selectedIndex = selectedPartner,
                firstName = boyName,
                secondName = girlName,
                onSelect = { selectedPartner = it },
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            Spacer(Modifier.height(8.dp))

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (currentData == null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 60.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("✨", fontSize = 48.sp)
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "No yoga data available for $currentName",
                                style = MaterialTheme.typography.bodyMedium,
                                color = CreamDim,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                } else {
                    YogaPersonSection(name = currentName, yogas = currentData)
                }

                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

// ─── Partner Tab Row ──────────────────────────────────────────────────────────

@Composable
private fun PartnerTabRow(
    selectedIndex: Int,
    firstName: String,
    secondName: String,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(NavySurface)
            .border(1.dp, Gold.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        listOf(firstName, secondName).forEachIndexed { index, name ->
            val isSelected = selectedIndex == index
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isSelected) Gold else Color.Transparent)
                    .clickable { onSelect(index) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = name.take(14),
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isSelected) Color(0xFF0D0D1A) else CreamDim,
                    maxLines = 1,
                )
            }
        }
    }
}

// ─── Yoga Section ─────────────────────────────────────────────────────────────

@Composable
@Suppress("UNCHECKED_CAST")
private fun YogaPersonSection(name: String, yogas: Map<String, Any>) {
    val activeYogas = yogas.entries.filter { (_, v) ->
        when (v) {
            is Map<*, *> -> v["yoga_present"] == true || v["is_active"] == true || v["active"] == true
            is Boolean -> v
            else -> false
        }
    }

    if (activeYogas.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 40.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("✨", fontSize = 40.sp)
                Spacer(Modifier.height(10.dp))
                Text("No active yogas detected for $name", color = CreamDim, textAlign = TextAlign.Center)
            }
        }
    } else {
        activeYogas.forEach { (key, value) ->
            YogaRow(yogaKey = key, data = value)
        }
    }
}

@Composable
@Suppress("UNCHECKED_CAST")
private fun YogaRow(yogaKey: String, data: Any) {
    val successColor = Color(0xFF48BB78)
    val displayName = yogaKey.replace("_", " ").split(" ").joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(NavySurface)
            .border(1.dp, Gold.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("✦ ", color = Gold, fontSize = 12.sp)
            Text(
                displayName,
                style = MaterialTheme.typography.labelMedium,
                color = CreamText,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(successColor.copy(alpha = 0.15f))
                    .border(1.dp, successColor.copy(alpha = 0.3f), RoundedCornerShape(50))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            ) {
                Text("ACTIVE", style = MaterialTheme.typography.labelSmall, color = successColor, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
        }

        val desc = (data as? Map<*, *>)?.get("description") as? String
            ?: (data as? Map<*, *>)?.get("effect") as? String
        if (!desc.isNullOrBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(desc, style = MaterialTheme.typography.bodySmall, color = CreamDim, lineHeight = 18.sp)
        }
    }
}
