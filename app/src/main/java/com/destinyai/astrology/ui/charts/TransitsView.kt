package com.destinyai.astrology.ui.charts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
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

// Map planet API names to localized string resources (iOS TransitsView.swift:48 —
// "planet_<name>".localized)
private val transitPlanetNameResMap: Map<String, Int> = mapOf(
    "Sun" to R.string.planet_sun,
    "Moon" to R.string.planet_moon,
    "Mars" to R.string.planet_mars,
    "Mercury" to R.string.planet_mercury,
    "Jupiter" to R.string.planet_jupiter,
    "Venus" to R.string.planet_venus,
    "Saturn" to R.string.planet_saturn,
    "Rahu" to R.string.planet_rahu,
    "Ketu" to R.string.planet_ketu,
)

@Composable
fun TransitsView(transitResponse: TransitResponse?) {
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
                stringResource(R.string.transits_year_fmt, transitResponse?.year ?: 2024),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF0B0F19),
            )
        }

        if (transitResponse?.transits != null) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                transitResponse.transits.keys.sorted().forEach { planet ->
                    val events = transitResponse.transits[planet] ?: return@forEach
                    TransitPlanetSection(
                        planet = planet,
                        events = events,
                    )
                }
            }
        } else {
            Text(stringResource(R.string.select_year_transits), fontSize = 14.sp, color = Color.Gray)
        }
    }
}

@Composable
private fun TransitPlanetSection(
    planet: String,
    events: List<TransitEvent>,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // iOS parity: TransitsView.swift:48 — planet name header (no toggle)
        val planetLabel = transitPlanetNameResMap[planet]?.let { stringResource(it) } ?: planet
        Text(
            planetLabel,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF0B0F19),
        )
        // iOS parity: events always shown inline, no AnimatedVisibility wrapper
        events.forEach { event ->
            TransitEventRow(event = event)
        }
        Spacer(Modifier.height(4.dp))
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
            "$signName (${stringResource(R.string.house_short_fmt, event.houseFromLagna)})",
            fontSize = 13.sp,
            color = Color(0xFF0B0F19),
        )
    }
}
