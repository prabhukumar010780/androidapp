package com.destinyai.astrology.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.destinyai.astrology.R
import com.destinyai.astrology.services.HapticManager
import com.destinyai.astrology.ui.theme.CanelaFontFamily
import com.destinyai.astrology.ui.theme.CreamDim
import com.destinyai.astrology.ui.theme.CreamText
import com.destinyai.astrology.ui.theme.Gold

/**
 * R2-S5: Language settings as a ModalBottomSheet.
 * Lists all 13 supported languages. On selection, persists via SettingsViewModel
 * and triggers live re-localisation via LocaleManager (R2-S6).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageSettingsSheet(
    onDismiss: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val haptic = remember { HapticManager(context) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
        ) {
            // iOS parity (LanguageSettingsSheet.swift:80): explicit Done button to dismiss
            // even when no selection changed.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.language),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = CanelaFontFamily,
                    color = Gold,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = {
                        haptic.light()
                        onDismiss()
                    },
                    modifier = Modifier.testTag("language_sheet_done"),
                ) {
                    Text(
                        text = stringResource(R.string.done_action),
                        color = Gold,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            HorizontalDivider(color = Gold.copy(alpha = 0.1f), thickness = 0.5.dp)

            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(supportedLanguages) { lang ->
                    val isSelected = state.selectedLanguage == lang.code
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                haptic.light()
                                viewModel.setLanguageWithLocale(lang.code)
                                onDismiss()
                            }
                            .padding(horizontal = 24.dp, vertical = 16.dp)
                            .testTag("language_${lang.code}"),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = lang.nativeName,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isSelected) Gold else CreamText,
                            )
                            if (lang.name != lang.nativeName) {
                                Text(
                                    text = lang.name,
                                    fontSize = 12.sp,
                                    color = CreamDim,
                                    modifier = Modifier.padding(top = 1.dp),
                                )
                            }
                        }
                        if (isSelected) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = null,
                                tint = Gold,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                    if (lang.code != supportedLanguages.last().code) {
                        HorizontalDivider(
                            color = Gold.copy(alpha = 0.08f),
                            thickness = 0.5.dp,
                            modifier = Modifier.padding(horizontal = 24.dp),
                        )
                    }
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}

private data class LangEntry(val code: String, val name: String, val nativeName: String)

private val supportedLanguages = listOf(
    LangEntry("en", "English", "English"),
    LangEntry("hi", "Hindi", "हिंदी"),
    LangEntry("ta", "Tamil", "தமிழ்"),
    LangEntry("te", "Telugu", "తెలుగు"),
    LangEntry("kn", "Kannada", "ಕನ್ನಡ"),
    LangEntry("ml", "Malayalam", "മലയാളം"),
    LangEntry("es", "Spanish", "Español"),
    LangEntry("pt", "Portuguese", "Português"),
    LangEntry("de", "German", "Deutsch"),
    LangEntry("fr", "French", "Français"),
    LangEntry("zh-Hans", "Chinese", "中文"),
    LangEntry("ja", "Japanese", "日本語"),
    LangEntry("ru", "Russian", "Русский"),
)
