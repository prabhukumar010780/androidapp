package com.destinyai.astrology.ui.charts

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.destinyai.astrology.ui.theme.CreamDim
import com.destinyai.astrology.ui.theme.CreamText
import com.destinyai.astrology.ui.theme.Gold

// ── PlanetDetailCard ──────────────────────────────────────────────────────────

@Composable
fun PlanetDetailCard(planet: PlanetDisplayInfo, signAbbrev: String?, modifier: Modifier = Modifier) {
    val symbol = ChartConstants.planetSymbol(planet.id)
    val color = ChartConstants.planetColor(planet.id)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 4.dp,
                shape = RoundedCornerShape(12.dp),
                ambientColor = Color.Black.copy(alpha = 0.3f),
                spotColor = Color.Black.copy(alpha = 0.3f),
            )
            .clip(RoundedCornerShape(12.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        Color(red = 0.10f, green = 0.12f, blue = 0.22f, alpha = 0.9f),
                        Color(red = 0.05f, green = 0.06f, blue = 0.12f, alpha = 0.95f),
                    ),
                ),
            )
            .border(
                0.8.dp,
                Brush.linearGradient(listOf(Gold.copy(alpha = 0.3f), Gold.copy(alpha = 0.05f), Color.White.copy(alpha = 0.05f))),
                RoundedCornerShape(12.dp),
            )
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // Header: symbol + code + badges
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(symbol, fontSize = 14.sp, color = color)
            Spacer(Modifier.width(4.dp))
            Text(planet.code, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Gold)
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                if (planet.isRetrograde) StatusDot("R", Color.Red)
                if (planet.isVargottama) StatusDot("V", Color(0xFF9C27B0))
                if (planet.isCombust) StatusDot("C", Color(0xFFFF8C00))
            }
        }

        Divider(color = Color.White.copy(alpha = 0.15f))

        // Sign
        if (signAbbrev != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(Icons.Default.Home, contentDescription = null, modifier = Modifier.size(8.dp), tint = Gold.copy(alpha = 0.8f))
                Text(ChartConstants.signFullNames[signAbbrev] ?: signAbbrev, fontSize = 9.sp, color = CreamText, maxLines = 1)
            }
        }

        // Nakshatra
        if (planet.nakshatra != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(8.dp), tint = Gold.copy(alpha = 0.8f))
                Text(
                    buildString {
                        append(planet.nakshatra)
                        if (planet.pada != null) append(" - ${planet.pada}")
                    },
                    fontSize = 9.sp,
                    color = CreamText.copy(alpha = 0.8f),
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun StatusDot(text: String, color: Color) {
    Box(
        modifier = Modifier
            .size(14.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.2f))
            .border(0.5.dp, color.copy(alpha = 0.5f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, fontSize = 8.sp, fontWeight = FontWeight.Bold, color = color.copy(alpha = 0.9f))
    }
}

// ── PlanetCardsGrid ───────────────────────────────────────────────────────────

private val PLANET_ORDER = listOf("Sun", "Moon", "Mars", "Mercury", "Jupiter", "Venus", "Saturn", "Rahu", "Ketu")

// Non-lazy fixed 3-column grid — safe inside Column(verticalScroll).
// LazyVerticalGrid cannot be nested in verticalScroll (infinite height crash).
@Composable
fun PlanetCardsGrid(chartData: ChartData, chartType: ChartType = ChartType.D1) {
    val items = PLANET_ORDER.mapNotNull { name -> planetDisplayInfo(name, chartData, chartType) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.chunked(3).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                row.forEach { (planet, signAbbrev) ->
                    PlanetDetailCard(
                        planet = planet,
                        signAbbrev = signAbbrev,
                        modifier = Modifier.weight(1f),
                    )
                }
                // Fill trailing empty cells so last row aligns left
                repeat(3 - row.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

private fun planetDisplayInfo(name: String, chartData: ChartData, chartType: ChartType): Pair<PlanetDisplayInfo, String?>? {
    val code = ChartConstants.planetShortCodes[name] ?: name.take(2)
    return when (chartType) {
        ChartType.D1 -> {
            val pos = chartData.d1[name] ?: return null
            PlanetDisplayInfo(
                id = name,
                code = code,
                isRetrograde = pos.retrograde ?: false,
                isVargottama = pos.vargottama ?: false,
                isCombust = pos.combust ?: false,
                nakshatra = pos.nakshatra,
                pada = pos.pada,
            ) to pos.sign
        }
        ChartType.D9 -> {
            val pos = chartData.d9[name] ?: return null
            PlanetDisplayInfo(
                id = name,
                code = code,
                isRetrograde = false,
                isVargottama = false,
                isCombust = false,
            ) to pos.sign
        }
    }
}
