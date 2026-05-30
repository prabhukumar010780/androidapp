package com.destinyai.astrology.ui.charts

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.destinyai.astrology.ui.components.GlassSegmentedControl
import com.destinyai.astrology.ui.theme.CosmicBackground
import com.destinyai.astrology.ui.theme.CreamDim
import com.destinyai.astrology.ui.theme.Gold
import com.destinyai.astrology.ui.theme.NavySurface

@Composable
fun ChartComparisonSheet(
    boyName: String,
    girlName: String,
    boyChartData: ChartData?,
    girlChartData: ChartData?,
    boyAscendant: String?,
    girlAscendant: String?,
    onDismiss: () -> Unit,
    initialChartStyle: String = "north_indian",
    onChartStyleChanged: (String) -> Unit = {},
) {
    // R2-C3: D1/D9 tab state
    var selectedDivisional by remember { mutableIntStateOf(0) }
    var chartStyle by remember { mutableStateOf(initialChartStyle) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        CosmicBackground {
            Column(modifier = Modifier.fillMaxSize()) {
                // Toolbar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Birth Charts",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Gold,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onDismiss) {
                        Text("Done", color = Gold)
                    }
                }

                // R2-C3: GlassSegmentedControl for D1 / D9 switching
                GlassSegmentedControl(
                    options = listOf("D1", "D9"),
                    selectedIndex = selectedDivisional,
                    onSelect = { selectedDivisional = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )

                // Charts
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    val chartType = if (selectedDivisional == 0) ChartType.D1 else ChartType.D9

                    if (boyChartData != null) {
                        PersonChartSection(
                            chartData = boyChartData,
                            chartType = chartType,
                            personName = boyName,
                            ascendant = boyAscendant,
                            chartStyle = chartStyle,
                        )
                    } else {
                        Text("Boy chart not available", color = CreamDim)
                    }

                    if (girlChartData != null) {
                        PersonChartSection(
                            chartData = girlChartData,
                            chartType = chartType,
                            personName = girlName,
                            ascendant = girlAscendant,
                            chartStyle = chartStyle,
                        )
                    } else {
                        Text("Girl chart not available", color = CreamDim)
                    }

                    if (selectedDivisional == 0) BadgeLegend()
                }
            }
        }
    }
}

@Composable
private fun PersonChartSection(
    chartData: ChartData,
    chartType: ChartType,
    personName: String,
    ascendant: String?,
    chartStyle: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // R2-C5: Per-person ascendant header in gold
        Row(
            modifier = Modifier.padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(personName, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Gold)
            if (ascendant != null) {
                val ascFull = ChartConstants.signFullNames[ascendant] ?: ascendant
                Text(
                    "Ascendant: $ascFull",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Gold,
                )
            }
        }

        // Chart visualization
        if (chartStyle == "north_indian") {
            NorthIndianChartView(chartData = chartData, ascendantSign = ascendant)
        } else {
            SouthIndianChartView(chartData = chartData, chartType = chartType, ascendantSign = ascendant)
        }

        // R2-C4: 3x3 planet grid for both D1 and D9
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(horizontal = 4.dp)) {
            Text(
                "Planet Details",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = Gold.copy(alpha = 0.9f),
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            PlanetCardsGrid(chartData = chartData, chartType = chartType)
        }
    }
}
