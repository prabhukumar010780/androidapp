package com.destinyai.astrology.ui.charts

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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.destinyai.astrology.R
import com.destinyai.astrology.services.HapticManager
import com.destinyai.astrology.ui.components.GlassSegmentedControl
import com.destinyai.astrology.ui.theme.CosmicBackground
import com.destinyai.astrology.ui.theme.CreamDim
import com.destinyai.astrology.ui.theme.Gold
import com.destinyai.astrology.ui.theme.NavySurface

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChartComparisonSheet(
    boyName: String,
    girlName: String,
    boyChartData: ChartData?,
    girlChartData: ChartData?,
    boyAscendant: String?,
    girlAscendant: String?,
    onDismiss: () -> Unit,
    initialChartStyle: String = "north",
    onChartStyleChanged: (String) -> Unit = {},
) {
    // R2-C3: D1/D9 tab state
    var selectedDivisional by remember { mutableIntStateOf(0) }
    var chartStyle by remember { mutableStateOf(initialChartStyle) }
    // Gap 3: North/South toggle parity with iOS ChartComparisonSheet.swift:50-70
    var showStyleMenu by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val haptic = remember { HapticManager(context) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // iOS parity: ChartComparisonSheet.swift:77-78 .presentationDetents([.large]) +
    // .presentationDragIndicator(.visible) — ModalBottomSheet draws a drag handle
    // and supports pull-to-dismiss out of the box.
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        CosmicBackground {
            Column(modifier = Modifier.fillMaxSize()) {
                // Toolbar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Gap 3: chart-style menu (parity with iOS .topBarLeading Menu)
                    Box {
                        IconButton(onClick = {
                            haptic.light()
                            showStyleMenu = true
                        }) {
                            Icon(
                                Icons.Default.Tune,
                                contentDescription = stringResource(R.string.chart_style_action),
                                tint = Gold,
                            )
                        }
                        DropdownMenu(
                            expanded = showStyleMenu,
                            onDismissRequest = { showStyleMenu = false },
                        ) {
                            listOf(
                                "north" to stringResource(R.string.north_indian),
                                "south" to stringResource(R.string.south_indian),
                            ).forEach { (key, label) ->
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(label)
                                            if (chartStyle == key) {
                                                Spacer(Modifier.width(8.dp))
                                                Text("✓", color = Gold)
                                            }
                                        }
                                    },
                                    onClick = {
                                        haptic.light()
                                        chartStyle = key
                                        onChartStyleChanged(key)
                                        showStyleMenu = false
                                    },
                                )
                            }
                        }
                    }
                    Text(
                        stringResource(R.string.birth_charts),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Gold,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = {
                            haptic.light()
                            onDismiss()
                        },
                        modifier = Modifier.testTag("chart_comparison_done"),
                    ) {
                        Text(stringResource(R.string.done), color = Gold)
                    }
                }

                // R2-C3: GlassSegmentedControl for D1 / D9 switching
                GlassSegmentedControl(
                    options = listOf(stringResource(R.string.d1_rashi), stringResource(R.string.d9_navamsa)),
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
                        Text(stringResource(R.string.boy_chart_not_available), color = CreamDim)
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
                        Text(stringResource(R.string.girl_chart_not_available), color = CreamDim)
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
                    stringResource(R.string.ascendant_label_fmt, ascFull),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Gold,
                )
            }
        }

        // Chart visualization
        if (chartStyle == "north") {
            NorthIndianChartView(chartData = chartData, ascendantSign = ascendant)
        } else {
            SouthIndianChartView(chartData = chartData, chartType = chartType, ascendantSign = ascendant)
        }

        // R2-C4: 3x3 planet grid for D1 only (D9 has no degree/nakshatra/retro fields — mirrors iOS)
        if (chartType == ChartType.D1) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(horizontal = 4.dp)) {
                Text(
                    stringResource(R.string.planet_details),
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
