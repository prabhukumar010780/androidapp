package com.destinyai.astrology.ui.charts

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Loop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.destinyai.astrology.R
import com.destinyai.astrology.ui.theme.Gold

// R2-C8: map sign abbreviations to string resource IDs
fun getSignNameRes(sign: String): Int = when (sign) {
    "Ar" -> R.string.sign_ar
    "Ta" -> R.string.sign_ta
    "Ge" -> R.string.sign_ge
    "Ca" -> R.string.sign_ca
    "Le" -> R.string.sign_le
    "Vi" -> R.string.sign_vi
    "Li" -> R.string.sign_li
    "Sc" -> R.string.sign_sc
    "Sg" -> R.string.sign_sg
    "Cp" -> R.string.sign_cp
    "Aq" -> R.string.sign_aq
    "Pi" -> R.string.sign_pi
    else -> R.string.sign_ar // fallback
}

@Composable
fun TransitsView(transitResponse: TransitResponse?) {
    // R2-C9: per-planet expansion state
    var expanded by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "chart_tab_transits" }
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Header: loop icon + year
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.Loop, contentDescription = null, tint = Gold, modifier = Modifier.size(16.dp))
            Text(
                "Transits ${transitResponse?.year ?: 2024}",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF0B0F19),
            )
        }

        if (transitResponse?.transits != null) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                transitResponse.transits.keys.sorted().forEach { planet ->
                    val events = transitResponse.transits[planet] ?: return@forEach
                    val isExpanded = expanded[planet] ?: false
                    TransitPlanetSection(
                        planet = planet,
                        events = events,
                        isExpanded = isExpanded,
                        onToggle = {
                            expanded = expanded.toMutableMap().apply {
                                this[planet] = !isExpanded
                            }
                        },
                    )
                }
            }
        } else {
            Text("Select a year to view transits.", fontSize = 14.sp, color = Color.Gray)
        }
    }
}

@Composable
private fun TransitPlanetSection(
    planet: String,
    events: List<TransitEvent>,
    isExpanded: Boolean,
    onToggle: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        // R2-C9: collapsible header row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onToggle)
                .padding(vertical = 6.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                "$planet (${events.size} event${if (events.size != 1) "s" else ""})",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF0B0F19),
            )
            Icon(
                imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = if (isExpanded) "Collapse" else "Expand",
                modifier = Modifier.size(18.dp),
                tint = Color.Gray,
            )
        }
        AnimatedVisibility(visible = isExpanded) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                events.forEach { event ->
                    TransitEventRow(event = event)
                }
            }
        }
    }
}

@Composable
private fun TransitEventRow(event: TransitEvent) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(start = 8.dp),
    ) {
        // R2-C7: monospace date
        Text(
            event.date,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            color = Color.Gray,
        )
        Spacer(Modifier.width(8.dp))
        Icon(Icons.Default.ArrowForward, contentDescription = null, modifier = Modifier.size(10.dp), tint = Color.Gray)
        Spacer(Modifier.width(8.dp))
        // R2-C8: localised sign name
        val signName = stringResource(getSignNameRes(event.sign))
        Text(
            "$signName (H${event.houseFromLagna})",
            fontSize = 13.sp,
            color = Color(0xFF0B0F19),
        )
    }
}
