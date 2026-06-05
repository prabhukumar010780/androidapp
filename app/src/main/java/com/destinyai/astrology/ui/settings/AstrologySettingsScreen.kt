package com.destinyai.astrology.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.destinyai.astrology.R
import com.destinyai.astrology.ui.theme.CanelaFontFamily
import com.destinyai.astrology.ui.theme.CosmicBackground
import com.destinyai.astrology.ui.theme.CreamDim
import com.destinyai.astrology.ui.theme.CreamText
import com.destinyai.astrology.ui.theme.Gold
import com.destinyai.astrology.ui.theme.NavySurface

private val ayanamsaOptions = listOf(
    "lahiri" to R.string.ayanamsa_lahiri_default,
    "raman" to R.string.raman,
    "krishnamurti" to R.string.krishnamurti,
    "fagan_bradley" to R.string.fagan_bradley,
)

// Order mirrors iOS AstrologySettingsSheet.swift:21-31 (equal → whole_sign → placidus → ...)
// for cross-platform picker parity.
private val houseSystemOptions = listOf(
    "equal" to R.string.house_system_equal,
    "whole_sign" to R.string.house_system_whole_sign_default,
    "placidus" to R.string.house_system_placidus,
    "koch" to R.string.house_system_koch,
    "regiomontanus" to R.string.house_system_regiomontanus,
    "campanus" to R.string.house_system_campanus,
    "morinus" to R.string.house_system_morinus,
    "alcabitus" to R.string.house_system_alcabitus,
    "porphyrius" to R.string.house_system_porphyrius,
)

private val chartStyleOptions = listOf(
    "north" to R.string.north_indian,
    "south" to R.string.south_indian,
)

@Composable
fun AstrologySettingsScreen(
    onBack: () -> Unit,
    viewModel: AstrologySettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val haptic = LocalHapticFeedback.current

    LaunchedEffect(Unit) { viewModel.load() }

    CosmicBackground {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onBack()
                }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.astrology_settings_back_cd), tint = CreamDim)
                }
                Text(
                    text = stringResource(R.string.astrology_settings_title),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = CanelaFontFamily,
                    color = Gold,
                    modifier = Modifier.weight(1f),
                )
                // iOS parity (AstrologySettingsSheet.swift:154): trailing toolbar Done button.
                TextButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onBack()
                    },
                    modifier = Modifier.testTag("astrology_settings_done"),
                ) {
                    Text(
                        text = stringResource(R.string.done_action),
                        color = Gold,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Spacer(Modifier.height(4.dp))

                AstroPickerSection(
                    title = stringResource(R.string.ayanamsa),
                    options = ayanamsaOptions,
                    selected = state.ayanamsa,
                    onSelect = viewModel::setAyanamsa,
                    footer = stringResource(R.string.ayanamsa_footer),
                    rowTagPrefix = "ayanamsa",
                )

                AstroPickerSection(
                    title = stringResource(R.string.house_system),
                    options = houseSystemOptions,
                    selected = state.houseSystem,
                    onSelect = viewModel::setHouseSystem,
                    footer = stringResource(R.string.house_system_footer),
                    rowTagPrefix = "house_system",
                )

                AstroPickerSection(
                    title = stringResource(R.string.chart_style_title),
                    options = chartStyleOptions,
                    selected = state.chartStyle,
                    onSelect = viewModel::setChartStyle,
                    footer = stringResource(R.string.chart_style_footer),
                    rowTagPrefix = "chart_style",
                )

                if (state.isSaved) {
                    Text(text = stringResource(R.string.settings_saved), color = Gold, fontSize = 13.sp)
                }
                if (state.error != null) {
                    Text(text = state.error ?: "", color = Color(0xFFFF8A80), fontSize = 13.sp)
                }

                // iOS parity (AstrologySettingsSheet.swift:8-10): @AppStorage auto-persists on every
                // tap — no Save button. Android setters already write to DataStore inside
                // setAyanamsa/setHouseSystem/setChartStyle, so a redundant Save button was removed.

                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun AstroPickerSection(
    title: String,
    options: List<Pair<String, Int>>,
    selected: String,
    onSelect: (String) -> Unit,
    footer: String? = null,
    rowTagPrefix: String? = null,
) {
    // iOS parity (AstrologySettingsSheet.swift:50,84,118): HapticManager.shared.play(.light) on every option tap.
    val haptic = LocalHapticFeedback.current
    Column(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(NavySurface)
                .border(0.5.dp, Gold.copy(alpha = 0.2f), RoundedCornerShape(14.dp)),
        ) {
            Text(
                text = title,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = Gold.copy(alpha = 0.7f),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
            HorizontalDivider(color = Gold.copy(alpha = 0.1f), thickness = 0.5.dp)
            options.forEach { (key, labelRes) ->
                val rowTagModifier = if (rowTagPrefix != null) {
                    Modifier.testTag("${rowTagPrefix}_$key")
                } else {
                    Modifier
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onSelect(key)
                        }
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                        .then(rowTagModifier),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(text = stringResource(labelRes), fontSize = 15.sp, color = CreamText)
                    if (selected == key) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = null,
                            tint = Gold,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
                if (key != options.last().first) {
                    HorizontalDivider(color = Gold.copy(alpha = 0.08f), thickness = 0.5.dp)
                }
            }
        }
        if (footer != null) {
            Text(
                text = footer,
                fontSize = 12.sp,
                color = CreamDim,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
            )
        }
    }
}
