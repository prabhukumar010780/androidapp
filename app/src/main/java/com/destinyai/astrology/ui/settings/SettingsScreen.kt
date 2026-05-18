package com.destinyai.astrology.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.loadSettings() }

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
                    text = "Settings",
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

                CosmicSettingsSection(title = "Chart Style") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("north", "south", "east").forEach { style ->
                            FilterChip(
                                selected = state.chartStyle == style,
                                onClick = { viewModel.setChartStyle(style) },
                                label = {
                                    Text(
                                        style.replaceFirstChar { it.uppercase() },
                                        color = if (state.chartStyle == style) Color(0xFF0D0D1A) else CreamDim,
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Gold,
                                    containerColor = NavySurface,
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = state.chartStyle == style,
                                    borderColor = Gold.copy(alpha = 0.3f),
                                    selectedBorderColor = Gold,
                                ),
                            )
                        }
                    }
                }

                CosmicSettingsSection(title = "Response Style") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("brief", "balanced", "detailed").forEach { style ->
                            FilterChip(
                                selected = state.responseStyle == style,
                                onClick = { viewModel.setResponseStyle(style) },
                                label = {
                                    Text(
                                        style.replaceFirstChar { it.uppercase() },
                                        color = if (state.responseStyle == style) Color(0xFF0D0D1A) else CreamDim,
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Gold,
                                    containerColor = NavySurface,
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = state.responseStyle == style,
                                    borderColor = Gold.copy(alpha = 0.3f),
                                    selectedBorderColor = Gold,
                                ),
                            )
                        }
                    }
                }

                CosmicSettingsSection(title = "Language") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("en", "hi", "ta").forEach { lang ->
                            FilterChip(
                                selected = state.selectedLanguage == lang,
                                onClick = { viewModel.setLanguage(lang) },
                                label = {
                                    Text(
                                        lang.uppercase(),
                                        color = if (state.selectedLanguage == lang) Color(0xFF0D0D1A) else CreamDim,
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Gold,
                                    containerColor = NavySurface,
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = state.selectedLanguage == lang,
                                    borderColor = Gold.copy(alpha = 0.3f),
                                    selectedBorderColor = Gold,
                                ),
                            )
                        }
                    }
                }

                CosmicSettingsSection(title = "Notifications") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SettingsToggleRow("Daily Insights", state.notifDailyInsight, viewModel::setNotifDailyInsight)
                        SettingsToggleRow("Transits", state.notifTransits, viewModel::setNotifTransits)
                        SettingsToggleRow("Compatibility", state.notifCompatibility, viewModel::setNotifCompatibility)
                    }
                }

                if (state.error != null) {
                    Text(text = state.error ?: "", color = Color(0xFFFF8A80), fontSize = 13.sp)
                }
                if (state.isSaved) {
                    Text(text = "Settings saved", color = Gold, fontSize = 13.sp)
                }

                Button(
                    onClick = { viewModel.saveNotifPrefs() },
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
                        Text("Save Settings", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    }
                }

                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun CosmicSettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(NavySurface)
            .border(0.5.dp, Gold.copy(alpha = 0.2f), RoundedCornerShape(14.dp))
            .padding(16.dp),
    ) {
        Text(text = title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Gold.copy(alpha = 0.7f))
        Spacer(Modifier.height(12.dp))
        content()
    }
}

@Composable
private fun SettingsToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, fontSize = 15.sp, color = CreamText)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Gold,
                checkedTrackColor = Gold.copy(alpha = 0.3f),
            ),
        )
    }
}
