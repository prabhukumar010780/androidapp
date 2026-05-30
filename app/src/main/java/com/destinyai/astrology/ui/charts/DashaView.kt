package com.destinyai.astrology.ui.charts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HourglassEmpty
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
fun DashaView(dashaResponse: DashaResponse?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "chart_tab_dasha" }
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Header: hourglass + year
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.HourglassEmpty, contentDescription = null, tint = Gold, modifier = Modifier.size(16.dp))
            Text(
                "Vimshottari Dasha ${dashaResponse?.year ?: 2024}",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF0B0F19),
            )
        }

        if (dashaResponse?.periods != null) {
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White),
            ) {
                dashaResponse.periods.forEachIndexed { idx, period ->
                    DashaRow(period = period)
                    if (idx < dashaResponse.periods.lastIndex) {
                        Divider(color = Color.Gray.copy(alpha = 0.15f))
                    }
                }
            }
        } else {
            Text("Select a year to view dasha periods.", fontSize = 14.sp, color = Color.Gray)
        }
    }
}

@Composable
private fun DashaRow(period: DashaPeriod) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "${period.mahadasha} - ${period.antardasha}",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF0B0F19),
            )
            Text(
                period.pratyantardasha,
                fontSize = 12.sp,
                color = Color.Gray,
            )
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(period.start, fontSize = 12.sp, color = Color.Gray)
            Text(period.end, fontSize = 12.sp, color = Color.Gray)
        }
    }
}
