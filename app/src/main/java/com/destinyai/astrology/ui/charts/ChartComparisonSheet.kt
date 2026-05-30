package com.destinyai.astrology.ui.charts

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tune
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
import com.destinyai.astrology.ui.theme.CosmicBackground
import com.destinyai.astrology.ui.theme.CreamDim
import com.destinyai.astrology.ui.theme.CreamText
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
    var selectedTab by remember { mutableIntStateOf(0) }
    var chartStyle by remember { mutableStateOf(initialChartStyle) }
    var showStyleMenu by remember { mutableStateOf(false) }

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
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box {
                        IconButton(onClick = { showStyleMenu = true }) {
                            Icon(Icons.Default.Tune, contentDescription = "Chart style", tint = Gold)
                        }
                        DropdownMenu(
                            expanded = showStyleMenu,
                            onDismissRequest = { showStyleMenu = false },
                        ) {
                            listOf("north_indian" to "North Indian", "south_indian" to "South Indian").forEach { (key, label) ->
                                DropdownMenuItem(
                                    text = {
                                        Row {
                                            Text(label)
                                            if (chartStyle == key) {
                                                Spacer(Modifier.width(8.dp))
                                                Text("✓", color = Gold)
                                            }
                                        }
                                    },
                                    onClick = {
                                        chartStyle = key
                                        onChartStyleChanged(key)
                                        showStyleMenu = false
                                    },
                                )
                            }
                        }
                    }
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

                // Tab selector
                ChartTabSelector(selectedTab = selectedTab, onTabSelected = { selectedTab = it })

                // Charts
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    if (selectedTab == 0) {
                        // D1
                        if (boyChartData != null) {
                            PersonChartSection(
                                chartData = boyChartData,
                                chartType = ChartType.D1,
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
                                chartType = ChartType.D1,
                                personName = girlName,
                                ascendant = girlAscendant,
                                chartStyle = chartStyle,
                            )
                        } else {
                            Text("Girl chart not available", color = CreamDim)
                        }
                        BadgeLegend()
                    } else {
                        // D9
                        if (boyChartData != null) {
                            PersonChartSection(
                                chartData = boyChartData,
                                chartType = ChartType.D9,
                                personName = boyName,
                                ascendant = boyAscendant,
                                chartStyle = chartStyle,
                            )
                        }
                        if (girlChartData != null) {
                            PersonChartSection(
                                chartData = girlChartData,
                                chartType = ChartType.D9,
                                personName = girlName,
                                ascendant = girlAscendant,
                                chartStyle = chartStyle,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChartTabSelector(selectedTab: Int, onTabSelected: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black.copy(alpha = 0.3f))
            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(12.dp)),
    ) {
        listOf("D1 Rashi", "D9 Navamsa").forEachIndexed { idx, label ->
            val selected = selectedTab == idx
            val bgColor by animateColorAsState(if (selected) Gold else Color.Transparent)
            val textColor by animateColorAsState(if (selected) Color(0xFF0D0D1A) else Color.White.copy(alpha = 0.8f))
            Button(
                onClick = { onTabSelected(idx) },
                modifier = Modifier.weight(1f).padding(4.dp),
                colors = ButtonDefaults.buttonColors(containerColor = bgColor),
                contentPadding = PaddingValues(vertical = 10.dp),
                elevation = ButtonDefaults.buttonElevation(0.dp),
            ) {
                Text(
                    label,
                    fontSize = 14.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    color = textColor,
                )
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
        Row(modifier = Modifier.padding(horizontal = 4.dp)) {
            Text(personName, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Gold)
            if (ascendant != null) {
                Spacer(Modifier.width(6.dp))
                Text(
                    "• Asc: ${ChartConstants.signFullNames[ascendant] ?: ascendant}",
                    fontSize = 13.sp,
                    color = CreamText.copy(alpha = 0.7f),
                )
            }
        }
        if (chartStyle == "north_indian") {
            NorthIndianChartView(chartData = chartData, ascendantSign = ascendant)
        } else {
            SouthIndianChartView(chartData = chartData, chartType = chartType, ascendantSign = ascendant)
        }
        if (chartType == ChartType.D1) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(horizontal = 8.dp)) {
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
}
