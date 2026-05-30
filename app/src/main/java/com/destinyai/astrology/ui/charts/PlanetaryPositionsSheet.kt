package com.destinyai.astrology.ui.charts

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.destinyai.astrology.R
import com.destinyai.astrology.ui.components.GlassSegmentedControl
import com.destinyai.astrology.ui.theme.CanelaFontFamily
import com.destinyai.astrology.ui.theme.CreamText
import com.destinyai.astrology.ui.theme.Gold

/**
 * R2-C1: Standalone ModalBottomSheet showing planetary positions.
 * R2-C2: GlassSegmentedControl at the top for North/South chart-style switching.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanetaryPositionsSheet(
    chartApiData: ChartApiResponse,
    currentChartStyle: String,
    onChartStyleChanged: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val planetOrder = listOf("Sun", "Moon", "Mars", "Mercury", "Jupiter", "Venus", "Saturn", "Rahu", "Ketu")

    // R2-C2: chart-style toggle labels
    val styleOptions = listOf(
        stringResource(R.string.north_indian),
        stringResource(R.string.south_indian),
    )
    val selectedStyleIndex = if (currentChartStyle == "north_indian") 0 else 1

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Sheet title
            Text(
                stringResource(R.string.planetary_positions),
                fontFamily = CanelaFontFamily,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = CreamText,
            )

            // R2-C2: North / South segmented control
            GlassSegmentedControl(
                options = styleOptions,
                selectedIndex = selectedStyleIndex,
                onSelect = { idx ->
                    onChartStyleChanged(if (idx == 0) "north_indian" else "south_indian")
                },
                modifier = Modifier.fillMaxWidth(),
            )

            // Planet rows (same as inline in ChartsScreen)
            planetOrder.forEach { name ->
                val pData = chartApiData.planets[name]
                if (pData != null) {
                    PremiumPlanetRow(
                        name = name,
                        data = pData,
                        nakshatra = chartApiData.nakshatra[name],
                    )
                }
            }
        }
    }
}
