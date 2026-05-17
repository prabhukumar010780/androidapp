package com.destinyai.astrology.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.loadSettings() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SettingsSection(title = "Chart Style") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("north", "south", "east").forEach { style ->
                        FilterChip(
                            selected = state.chartStyle == style,
                            onClick = { viewModel.setChartStyle(style) },
                            label = { Text(style.replaceFirstChar { it.uppercase() }) },
                        )
                    }
                }
            }

            SettingsSection(title = "Response Style") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("brief", "balanced", "detailed").forEach { style ->
                        FilterChip(
                            selected = state.responseStyle == style,
                            onClick = { viewModel.setResponseStyle(style) },
                            label = { Text(style.replaceFirstChar { it.uppercase() }) },
                        )
                    }
                }
            }

            SettingsSection(title = "Language") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("en", "hi", "ta").forEach { lang ->
                        FilterChip(
                            selected = state.selectedLanguage == lang,
                            onClick = { viewModel.setLanguage(lang) },
                            label = { Text(lang.uppercase()) },
                        )
                    }
                }
            }

            SettingsSection(title = "Notifications") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("Daily Insights", style = MaterialTheme.typography.bodyMedium)
                        Switch(
                            checked = state.notifDailyInsight,
                            onCheckedChange = viewModel::setNotifDailyInsight,
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("Transits", style = MaterialTheme.typography.bodyMedium)
                        Switch(
                            checked = state.notifTransits,
                            onCheckedChange = viewModel::setNotifTransits,
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("Compatibility", style = MaterialTheme.typography.bodyMedium)
                        Switch(
                            checked = state.notifCompatibility,
                            onCheckedChange = viewModel::setNotifCompatibility,
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            if (state.error != null) {
                Text(
                    text = state.error ?: "",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            if (state.isSaved) {
                Text(
                    text = "Settings saved",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Button(
                onClick = { viewModel.saveNotifPrefs() },
                enabled = !state.isLoading,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(12.dp),
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text("Save Settings", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}
