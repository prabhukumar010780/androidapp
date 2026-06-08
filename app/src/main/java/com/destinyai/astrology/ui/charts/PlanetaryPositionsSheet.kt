package com.destinyai.astrology.ui.charts

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.destinyai.astrology.R
import com.destinyai.astrology.services.HapticManager
import com.destinyai.astrology.ui.theme.CanelaFontFamily
import com.destinyai.astrology.ui.theme.CosmicBackground
import com.destinyai.astrology.ui.theme.CreamDim
import com.destinyai.astrology.ui.theme.CreamText
import com.destinyai.astrology.ui.theme.Gold
import com.destinyai.astrology.ui.theme.NavySurface
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * R2-C1: Standalone ModalBottomSheet showing planetary positions.
 * R2-C2: GlassSegmentedControl at the top for North/South chart-style switching.
 *
 * iOS parity: the sheet is self-contained and shows its own loading / error / retry
 * UX so the user can recover when the parent chart fetch failed.
 * Mirrors `PlanetaryPositionsSheet.swift` (loadData / error view / Retry button).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanetaryPositionsSheet(
    state: ChartsUiState,
    currentChartStyle: String,
    onChartStyleChanged: (String) -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val planetOrder = listOf("Sun", "Moon", "Mars", "Mercury", "Jupiter", "Venus", "Saturn", "Rahu", "Ketu")
    val context = LocalContext.current
    val haptic = remember { HapticManager(context) }
    var showStyleMenu by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        CosmicBackground(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 24.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
            // Header — mirrors iOS toolbar: leading slider menu + centered title + trailing Done
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Leading: slider icon with dropdown menu (mirrors iOS Menu { Button(north) Button(south) })
                Box {
                    IconButton(onClick = { showStyleMenu = true }) {
                        Icon(Icons.Filled.Tune, contentDescription = stringResource(R.string.settings_title), tint = Gold)
                    }
                    DropdownMenu(
                        expanded = showStyleMenu,
                        onDismissRequest = { showStyleMenu = false },
                        containerColor = NavySurface,
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.north_indian), color = CreamText) },
                            onClick = { onChartStyleChanged("north"); showStyleMenu = false },
                            leadingIcon = if (currentChartStyle == "north") ({
                                Icon(Icons.Filled.Check, contentDescription = null, tint = Gold, modifier = Modifier.size(16.dp))
                            }) else null,
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.south_indian), color = CreamText) },
                            onClick = { onChartStyleChanged("south"); showStyleMenu = false },
                            leadingIcon = if (currentChartStyle == "south") ({
                                Icon(Icons.Filled.Check, contentDescription = null, tint = Gold, modifier = Modifier.size(16.dp))
                            }) else null,
                        )
                    }
                }
                // Centered title
                Text(
                    stringResource(R.string.planetary_positions),
                    fontFamily = CanelaFontFamily,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = CreamText,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                // Trailing Done
                TextButton(
                    onClick = { haptic.light(); onDismiss() },
                    modifier = Modifier.testTag("planetary_positions_done"),
                ) {
                    Text(stringResource(R.string.done), color = Gold)
                }
            }

            when {
                !state.hasData -> {
                    // iOS parity (PlanetaryPositionsSheet.swift:322): no profile -> errorMessage inside the sheet.
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            stringResource(R.string.no_birth_profile_found),
                            color = CreamDim,
                            fontSize = 14.sp,
                        )
                    }
                }
                state.isLoading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = Gold, modifier = Modifier.size(40.dp))
                            Spacer(Modifier.height(16.dp))
                            Text(
                                stringResource(R.string.calculating_chart),
                                color = CreamDim,
                                fontSize = 14.sp,
                            )
                        }
                    }
                }
                state.errorMessage != null -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = Color.Red.copy(alpha = 0.8f),
                                modifier = Modifier.size(40.dp),
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                stringResource(R.string.failed_to_load_chart),
                                color = CreamText,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                state.errorMessage.orEmpty(),
                                color = CreamDim,
                                fontSize = 12.sp,
                            )
                            Spacer(Modifier.height(16.dp))
                            Button(
                                onClick = onRetry,
                                colors = ButtonDefaults.buttonColors(containerColor = Gold),
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.retry), color = Color(0xFF0D0D1A))
                            }
                        }
                    }
                }
                state.chartApiData != null -> {
                    val chartApiData = state.chartApiData

                    // Minimal birth info line (iOS parity: PlanetaryPositionsSheet.swift:59)
                    MinimalBirthInfoRow(
                        dob = state.dateOfBirth,
                        time = state.timeOfBirth,
                        city = state.cityOfBirth,
                        ascendantSign = state.ascendantSign?.let { ChartConstants.signFullNames[it] ?: it },
                        timeUnknown = state.timeUnknown,
                    )

                    // Chart visualization — centered horizontally (fixed-size chart inside full-width column)
                    val chartData = mapToChartData(chartApiData)
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        if (currentChartStyle == "north") {
                            NorthIndianChartView(
                                chartData = chartData,
                                ascendantSign = state.ascendantSign,
                            )
                        } else {
                            SouthIndianChartView(
                                chartData = chartData,
                                chartType = ChartType.D1,
                                ascendantSign = state.ascendantSign,
                            )
                        }
                    }

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

                    // Badge legend — iOS parity: badgeLegend
                    BadgeLegend()
                }
            }
            }
        }
    }
}

// ── Minimal birth info header (sheet-local, mirrors ChartsScreen.MinimalBirthInfo) ──

@Composable
private fun MinimalBirthInfoRow(
    dob: String,
    time: String,
    city: String,
    ascendantSign: String?,
    timeUnknown: Boolean,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(formatSheetBirthDate(dob), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = CreamText, maxLines = 1)
        if (!timeUnknown) {
            Text("•", color = Gold.copy(alpha = 0.6f))
            Text(formatSheetBirthTime(time), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = CreamText, maxLines = 1)
        }
        if (city.isNotEmpty()) {
            Text("•", color = Gold.copy(alpha = 0.6f))
            // city gets remaining space and ellipsises — keeps ascendant always visible
            Text(
                city,
                fontSize = 14.sp,
                color = CreamDim,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        } else if (ascendantSign != null) {
            Spacer(Modifier.weight(1f))
        }
        if (ascendantSign != null) {
            Text("•", color = Gold.copy(alpha = 0.6f))
            Text(
                stringResource(R.string.ascendant_short_fmt, ascendantSign),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = Gold,
                maxLines = 1,
            )
        }
    }
}

private fun formatSheetBirthDate(dob: String): String =
    try {
        val d = LocalDate.parse(dob, DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        d.format(DateTimeFormatter.ofPattern("MMMM d, yyyy"))
    } catch (_: Exception) { dob }

private fun formatSheetBirthTime(time: String): String =
    try {
        val t = LocalTime.parse(time, DateTimeFormatter.ofPattern("HH:mm"))
        t.format(DateTimeFormatter.ofPattern("h:mm a"))
    } catch (_: Exception) { time }
