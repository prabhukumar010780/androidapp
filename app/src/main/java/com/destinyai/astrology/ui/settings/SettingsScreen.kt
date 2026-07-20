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
import com.destinyai.astrology.ui.theme.AppType
import com.destinyai.astrology.ui.theme.CanelaFontFamily
import com.destinyai.astrology.ui.theme.CosmicBackground
import com.destinyai.astrology.ui.theme.CreamDim
import com.destinyai.astrology.ui.theme.CreamText
import com.destinyai.astrology.ui.theme.Gold
import com.destinyai.astrology.ui.theme.NavySurface
import com.destinyai.astrology.ui.theme.Radius
import com.destinyai.astrology.ui.theme.Spacing

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
                    .padding(horizontal = Spacing.xl)
                    .navigationBarsPadding()
                    .padding(top = Spacing.xs, bottom = Spacing.xl),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Astrology Settings link
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Radius.card))
                        .background(NavySurface)
                        .border(0.5.dp, Gold.copy(alpha = 0.2f), RoundedCornerShape(Radius.card))
                        .clickable(onClick = onNavigateToAstrologySettings)
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = stringResource(R.string.astrology_settings),
                        fontSize = AppType.body,
                        lineHeight = AppType.bodyLh,
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
                        // iOS parity (ResponseStyleManager ContentStyle): the ONLY valid
                        // backend response_style tokens are "guidance" (Essentials) and
                        // "astrology" (Complete Chart Details). Using brief/balanced/detailed
                        // here wrote an invalid token that silently broke the backend style
                        // resolver and clobbered the onboarding choice.
                        listOf(
                            "guidance" to stringResource(R.string.content_style_essentials),
                            "astrology" to stringResource(R.string.content_style_complete),
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
                        .clip(RoundedCornerShape(Radius.card))
                        .background(NavySurface)
                        .border(0.5.dp, Gold.copy(alpha = 0.2f), RoundedCornerShape(Radius.card))
                        .clickable { showLanguageSheet = true }
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = stringResource(R.string.settings_language_section),
                        fontSize = AppType.body,
                        lineHeight = AppType.bodyLh,
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

                // iOS parity (D5): iOS owns notification channels/categories solely in
                // NotificationPreferences — it never writes coarse daily/transits/compat
                // category flags. The 3 legacy toggles + their Save button were removed
                // because saving them overwrote server-side channel state iOS leaves
                // untouched (cross-platform drift). The Personalized Alerts row below is
                // the single source of truth, matching iOS.

                // iOS parity: NotificationPreferencesSheet is presented from ProfileView; on Android
                // we expose it via a row in Settings so users can manage personalized alerts.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Radius.card))
                        .background(NavySurface)
                        .border(0.5.dp, Gold.copy(alpha = 0.2f), RoundedCornerShape(Radius.card))
                        .clickable(onClick = onNavigateToNotificationPrefs)
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.personalized_alerts_title),
                            fontSize = AppType.body,
                            lineHeight = AppType.bodyLh,
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
            .clip(RoundedCornerShape(Radius.card))
            .background(NavySurface)
            .border(0.5.dp, Gold.copy(alpha = 0.2f), RoundedCornerShape(Radius.card))
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
        Text(text = label, fontSize = AppType.body, lineHeight = AppType.bodyLh, color = CreamText)
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
