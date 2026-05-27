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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.destinyai.astrology.ui.theme.CanelaFontFamily
import com.destinyai.astrology.ui.theme.CosmicBackground
import com.destinyai.astrology.ui.theme.CreamDim
import com.destinyai.astrology.ui.theme.CreamText
import com.destinyai.astrology.ui.theme.Gold
import com.destinyai.astrology.ui.theme.NavySurface

private val ayanamsaOptions = listOf(
    "lahiri" to "Lahiri (Default)",
    "raman" to "Raman",
    "krishnamurti" to "Krishnamurti",
    "fagan_bradley" to "Fagan-Bradley",
)

private val houseSystemOptions = listOf(
    "whole_sign" to "Whole Sign (Default)",
    "equal" to "Equal",
    "placidus" to "Placidus",
    "koch" to "Koch",
    "regiomontanus" to "Regiomontanus",
    "campanus" to "Campanus",
)

private val chartStyleOptions = listOf(
    "north" to "North Indian",
    "south" to "South Indian",
)

@Composable
fun AstrologySettingsScreen(
    onBack: () -> Unit,
    viewModel: AstrologySettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

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
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = CreamDim)
                }
                Text(
                    text = "Astrology Settings",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = CanelaFontFamily,
                    color = Gold,
                    modifier = Modifier.weight(1f),
                )
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
                    title = "Ayanamsa",
                    options = ayanamsaOptions,
                    selected = state.ayanamsa,
                    onSelect = viewModel::setAyanamsa,
                )

                AstroPickerSection(
                    title = "House System",
                    options = houseSystemOptions,
                    selected = state.houseSystem,
                    onSelect = viewModel::setHouseSystem,
                )

                AstroPickerSection(
                    title = "Chart Style",
                    options = chartStyleOptions,
                    selected = state.chartStyle,
                    onSelect = viewModel::setChartStyle,
                )

                if (state.isSaved) {
                    Text(text = "Settings saved", color = Gold, fontSize = 13.sp)
                }
                if (state.error != null) {
                    Text(text = state.error ?: "", color = Color(0xFFFF8A80), fontSize = 13.sp)
                }

                Button(
                    onClick = { viewModel.save() },
                    enabled = !state.isLoading,
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Gold,
                        contentColor = Color(0xFF0D0D1A),
                    ),
                ) {
                    if (state.isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color(0xFF0D0D1A), strokeWidth = 2.dp)
                    } else {
                        Text("Save", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    }
                }

                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun AstroPickerSection(
    title: String,
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
) {
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
        options.forEach { (key, label) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(key) }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = label, fontSize = 15.sp, color = CreamText)
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
}
