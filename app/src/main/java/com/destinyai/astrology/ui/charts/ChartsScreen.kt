package com.destinyai.astrology.ui.charts

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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

@Composable
fun ChartsScreen(
    onBack: () -> Unit,
    viewModel: ChartsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showStyleMenu by remember { mutableStateOf(false) }
    var showPlanetaryPositions by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val haptic = remember { HapticManager(context) }

    LaunchedEffect(Unit) { viewModel.loadChartData() }

    // R2-C1: Planetary Positions bottom sheet — self-contained loading/error/retry
    // mirrors iOS PlanetaryPositionsSheet.swift so the sheet is reachable even when
    // the parent chart fetch failed.
    if (showPlanetaryPositions) {
        PlanetaryPositionsSheet(
            state = state,
            currentChartStyle = state.chartStyle,
            onChartStyleChanged = { viewModel.setChartStyle(it) },
            onRetry = { viewModel.retry() },
            onDismiss = { showPlanetaryPositions = false },
        )
    }

    CosmicBackground {
        Column(modifier = Modifier.fillMaxSize().semantics { contentDescription = "chart_screen" }) {
            // Navigation bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = {
                        haptic.light()
                        onBack()
                    },
                    modifier = Modifier.testTag("charts_back_button"),
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.back_action),
                        tint = CreamDim,
                    )
                }
                Text(
                    text = stringResource(R.string.birth_chart),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = CanelaFontFamily,
                    color = Gold,
                    modifier = Modifier.weight(1f),
                )
                // Planet positions sheet button (R2-C1) — always enabled; sheet
                // shows its own loading/error/retry to mirror iOS behaviour.
                IconButton(
                    onClick = { showPlanetaryPositions = true },
                ) {
                    Icon(Icons.Default.GridView, contentDescription = stringResource(R.string.planet_positions_action), tint = Gold)
                }
                // Chart style menu (North / South toggle)
                Box {
                    IconButton(onClick = { showStyleMenu = true }) {
                        Icon(Icons.Default.Tune, contentDescription = stringResource(R.string.chart_style_action), tint = Gold)
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
                                        if (state.chartStyle == key) {
                                            Spacer(Modifier.width(8.dp))
                                            Text("✓", color = Gold)
                                        }
                                    }
                                },
                                onClick = {
                                    viewModel.setChartStyle(key)
                                    showStyleMenu = false
                                },
                            )
                        }
                    }
                }
            }

            when {
                state.isLoading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = Gold, modifier = Modifier.size(40.dp))
                            Spacer(Modifier.height(16.dp))
                            Text(stringResource(R.string.calculating_chart), color = CreamDim, fontSize = 14.sp)
                        }
                    }
                }
                state.errorMessage != null -> {
                    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = Color.Red.copy(alpha = 0.8f), modifier = Modifier.size(40.dp))
                            Spacer(Modifier.height(12.dp))
                            Text(stringResource(R.string.failed_to_load_chart), color = CreamText, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(4.dp))
                            Text(state.errorMessage.orEmpty(), color = CreamDim, fontSize = 12.sp)
                            Spacer(Modifier.height(16.dp))
                            Button(
                                onClick = { viewModel.retry() },
                                colors = ButtonDefaults.buttonColors(containerColor = Gold),
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.retry), color = Color(0xFF0D0D1A))
                            }
                        }
                    }
                }
                !state.hasData -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(NavySurface)
                            .border(1.dp, Gold.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                            .padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("🪐", fontSize = 48.sp)
                            Spacer(Modifier.height(12.dp))
                            Text(
                                stringResource(R.string.no_birth_chart_yet),
                                fontFamily = CanelaFontFamily,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Gold,
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                stringResource(R.string.no_birth_chart_yet_desc),
                                color = CreamDim,
                                fontSize = 14.sp,
                            )
                        }
                    }
                }
                state.chartApiData != null -> {
                    val chart = state.chartApiData ?: return@Column
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        Spacer(Modifier.height(4.dp))

                        // Minimal birth info line
                        MinimalBirthInfo(
                            dob = state.dateOfBirth,
                            time = state.timeOfBirth,
                            city = state.cityOfBirth,
                            ascendantSign = state.ascendantSign?.let { ChartConstants.signFullNames[it] ?: it },
                            timeUnknown = state.timeUnknown,
                        )

                        // Chart visualization (North or South)
                        val chartData = mapToChartData(chart)
                        AnimatedVisibility(visible = true) {
                            if (state.chartStyle == "north") {
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

                        // Planetary grid header + rows
                        val planetOrder = listOf("Sun", "Moon", "Mars", "Mercury", "Jupiter", "Venus", "Saturn", "Rahu", "Ketu")
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                stringResource(R.string.planetary_positions),
                                fontFamily = CanelaFontFamily,
                                fontSize = 18.sp,
                                color = CreamText,
                                modifier = Modifier.semantics { contentDescription = "chart_tab_planets" },
                            )
                            planetOrder.forEach { name ->
                                val pData = chart.planets[name]
                                if (pData != null) {
                                    PremiumPlanetRow(
                                        name = name,
                                        data = pData,
                                        nakshatra = chart.nakshatra[name],
                                    )
                                }
                            }
                        }

                        // Badge legend
                        BadgeLegend()

                        // Gap 2: Dasha + Transits removed from Charts to match iOS
                        // (iOS PlanetaryPositionsSheet.swift:50-73 only shows minimalBirthInfo +
                        // chartVisualSection + planetaryGrid + badgeLegend). Dasha and Transits
                        // are surfaced on Home (HomeScreen) where iOS also surfaces them.

                        Spacer(Modifier.height(32.dp))
                    }
                }
            }
        }
    }
}

// ── Minimal birth info header ─────────────────────────────────────────────────

@Composable
private fun MinimalBirthInfo(
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
        Text(formatBirthDate(dob), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = CreamText)
        if (!timeUnknown) {
            Text("•", color = Gold.copy(alpha = 0.6f))
            Text(formatBirthTime(time), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = CreamText)
        }
        if (city.isNotEmpty()) {
            Text("•", color = Gold.copy(alpha = 0.6f))
            Text(city, fontSize = 14.sp, color = CreamDim, maxLines = 1)
        }
        if (ascendantSign != null) {
            Text("•", color = Gold.copy(alpha = 0.6f))
            Text(stringResource(R.string.ascendant_short_fmt, ascendantSign), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Gold)
        }
    }
}

// ── Premium planet row ────────────────────────────────────────────────────────

// Map planet API names to localized string resources (mirrors DashaView.kt pattern,
// iOS PlanetaryPositionsSheet.swift:449 — "planet_<name>".localized)
private val premiumPlanetNameResMap: Map<String, Int> = mapOf(
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
fun PremiumPlanetRow(
    name: String,
    data: PlanetApiData,
    nakshatra: NakshatraApiData?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "planet_position_row" }
            .clip(RoundedCornerShape(16.dp))
            .background(
                androidx.compose.ui.graphics.Brush.linearGradient(
                    listOf(
                        Color(0x1A1A2038),
                        Color(0x0D0D0F1E),
                    )
                )
            )
            .border(
                1.dp,
                androidx.compose.ui.graphics.Brush.linearGradient(
                    listOf(Gold.copy(alpha = 0.3f), Gold.copy(alpha = 0.05f), Color.White.copy(alpha = 0.05f))
                ),
                RoundedCornerShape(16.dp),
            )
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Planet icon circle
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.3f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(ChartConstants.planetSymbol(name), fontSize = 22.sp, color = Gold)
        }

        // Name + badges + sign/degree
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                val localizedName = premiumPlanetNameResMap[name]?.let { stringResource(it) } ?: name
                Text(localizedName, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = CreamText)
                if (data.isRetrograde == true) ChartBadge("R", Color.Red)
                if (data.isCombust == true) ChartBadge("C", Color(0xFFFF8C00))
                if (data.vargottama == true) ChartBadge("V", Color(0xFF9C27B0))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(ChartConstants.signFullNames[data.sign] ?: data.sign, fontSize = 14.sp, color = Gold)
                Text("•", color = CreamDim.copy(alpha = 0.5f), fontSize = 10.sp)
                Text(ChartConstants.formatDegree(data.degree), fontSize = 13.sp, color = CreamDim)
            }
        }

        // House capsule + nakshatra/pada
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Gold.copy(alpha = 0.15f))
                    .border(1.dp, Gold.copy(alpha = 0.3f), RoundedCornerShape(50))
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            ) {
                Text(stringResource(R.string.house_short_fmt, data.house), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Gold)
            }
            if (nakshatra != null) {
                Text(nakshatra.nakshatra, fontSize = 11.sp, color = CreamDim)
                Text(stringResource(R.string.pada_num_fmt, nakshatra.pada), fontSize = 10.sp, color = CreamDim.copy(alpha = 0.7f))
            }
        }
    }
}

// ── Badge ─────────────────────────────────────────────────────────────────────

@Composable
fun ChartBadge(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.2f))
            .border(0.5.dp, color.copy(alpha = 0.4f), RoundedCornerShape(50))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(text, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = color.copy(alpha = 0.9f))
    }
}

// ── Badge legend ──────────────────────────────────────────────────────────────

@Composable
fun BadgeLegend() {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ChartBadge("R", Color.Red)
            Text("Retrograde", fontSize = 12.sp, color = CreamDim.copy(alpha = 0.6f))
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ChartBadge("C", Color(0xFFFF8C00))
            Text("Combust", fontSize = 12.sp, color = CreamDim.copy(alpha = 0.6f))
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ChartBadge("V", Color(0xFF9C27B0))
            Text("Vargottama", fontSize = 12.sp, color = CreamDim.copy(alpha = 0.6f))
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

private fun formatBirthDate(dob: String): String =
    try {
        val d = LocalDate.parse(dob, DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        d.format(DateTimeFormatter.ofPattern("MMMM d, yyyy"))
    } catch (_: Exception) { dob }

private fun formatBirthTime(time: String): String =
    try {
        val t = LocalTime.parse(time, DateTimeFormatter.ofPattern("HH:mm"))
        t.format(DateTimeFormatter.ofPattern("h:mm a"))
    } catch (_: Exception) { time }

fun mapToChartData(response: ChartApiResponse): ChartData {
    val d1 = response.planets.mapValues { (_, p) ->
        D1PlanetPosition(
            house = p.house,
            sign = p.sign,
            degree = p.degree,
            retrograde = p.isRetrograde,
            vargottama = p.vargottama,
            combust = p.isCombust,
        )
    }
    val d9 = response.divisionalCharts.mapNotNull { (name, div) ->
        val sign = div.sign ?: return@mapNotNull null
        name to D9PlanetPosition(house = div.house?.toIntOrNull(), sign = sign)
    }.toMap()
    return ChartData(d1 = d1, d9 = d9)
}
