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
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onNavigateToAstrologySettings: () -> Unit,
    onNavigateToNotificationPrefs: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showLanguageSheet by remember { mutableStateOf(false) }

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
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.settings_back_cd),
                        tint = CreamDim,
                    )
                }
                Text(
                    text = stringResource(R.string.settings_title),
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

                // Astrology Settings link
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(NavySurface)
                        .border(0.5.dp, Gold.copy(alpha = 0.2f), RoundedCornerShape(14.dp))
                        .clickable(onClick = onNavigateToAstrologySettings)
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = stringResource(R.string.astrology_settings),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = CreamText,
                    )
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForwardIos,
                        contentDescription = null,
                        tint = Gold.copy(alpha = 0.6f),
                        modifier = Modifier.size(16.dp),
                    )
                }

                CosmicSettingsSection(title = stringResource(R.string.settings_response_style_section)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            "brief" to stringResource(R.string.settings_response_style_brief),
                            "balanced" to stringResource(R.string.settings_response_style_balanced),
                            "detailed" to stringResource(R.string.settings_response_style_detailed),
                        ).forEach { (style, label) ->
                            FilterChip(
                                selected = state.responseStyle == style,
                                onClick = { viewModel.setResponseStyle(style) },
                                label = {
                                    Text(
                                        label,
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

                // Language row — opens the full 13-language LanguageSettingsSheet (parity with iOS)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(NavySurface)
                        .border(0.5.dp, Gold.copy(alpha = 0.2f), RoundedCornerShape(14.dp))
                        .clickable { showLanguageSheet = true }
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = stringResource(R.string.settings_language_section),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = CreamText,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = state.selectedLanguage.uppercase(),
                            fontSize = 13.sp,
                            color = Gold.copy(alpha = 0.8f),
                            modifier = Modifier.padding(end = 8.dp),
                        )
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForwardIos,
                            contentDescription = null,
                            tint = Gold.copy(alpha = 0.6f),
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }

                CosmicSettingsSection(title = stringResource(R.string.settings_notifications_section)) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SettingsToggleRow(
                            stringResource(R.string.settings_notif_daily_insights),
                            state.notifDailyInsight,
                            viewModel::setNotifDailyInsight,
                        )
                        SettingsToggleRow(
                            stringResource(R.string.settings_notif_transits),
                            state.notifTransits,
                            viewModel::setNotifTransits,
                        )
                        SettingsToggleRow(
                            stringResource(R.string.settings_notif_compatibility),
                            state.notifCompatibility,
                            viewModel::setNotifCompatibility,
                        )
                    }
                }

                // iOS parity: NotificationPreferencesSheet is presented from ProfileView; on Android
                // we expose it via a row in Settings so users can manage personalized alerts.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(NavySurface)
                        .border(0.5.dp, Gold.copy(alpha = 0.2f), RoundedCornerShape(14.dp))
                        .clickable(onClick = onNavigateToNotificationPrefs)
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.personalized_alerts_title),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = CreamText,
                        )
                        Text(
                            text = stringResource(R.string.personalized_alerts_subtitle),
                            fontSize = 12.sp,
                            color = CreamDim,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForwardIos,
                        contentDescription = null,
                        tint = Gold.copy(alpha = 0.6f),
                        modifier = Modifier.size(16.dp),
                    )
                }

                if (state.error != null) {
                    Text(text = state.error ?: "", color = Color(0xFFFF8A80), fontSize = 13.sp)
                }
                if (state.isSaved) {
                    Text(text = stringResource(R.string.settings_saved), color = Gold, fontSize = 13.sp)
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
                        Text(stringResource(R.string.settings_save_button), fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    }
                }

                Spacer(Modifier.height(32.dp))
            }
        }

        if (showLanguageSheet) {
            LanguageSettingsSheet(
                onDismiss = { showLanguageSheet = false },
                viewModel = viewModel,
            )
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
