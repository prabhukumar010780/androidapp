package com.destinyai.astrology.ui.charts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Loop
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.destinyai.astrology.ui.theme.Gold

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
                    TransitPlanetSection(planet = planet, events = events)
                }
            }
        } else {
            Text("Select a year to view transits.", fontSize = 14.sp, color = Color.Gray)
        }
    }
}

@Composable
private fun TransitPlanetSection(planet: String, events: List<TransitEvent>) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            planet,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF0B0F19),
        )
        events.forEach { event ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 8.dp),
            ) {
                Text(
                    event.date,
                    fontSize = 12.sp,
                    color = Color.Gray,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                )
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Default.ArrowForward, contentDescription = null, modifier = Modifier.size(10.dp), tint = Color.Gray)
                Spacer(Modifier.width(8.dp))
                val signFull = ChartConstants.signFullNames[event.sign] ?: event.sign
                Text(
                    "$signFull (H${event.houseFromLagna})",
                    fontSize = 13.sp,
                    color = Color(0xFF0B0F19),
                )
            }
        }
    }
}
