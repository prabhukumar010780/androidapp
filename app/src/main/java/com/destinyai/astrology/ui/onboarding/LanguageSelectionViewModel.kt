package com.destinyai.astrology.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.destinyai.astrology.data.local.prefs.UserPreferences
import com.destinyai.astrology.services.HapticManager
import com.destinyai.astrology.services.LocaleManager
import com.destinyai.astrology.services.SoundManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LanguageSelectionViewModel @Inject constructor(
    private val prefs: UserPreferences,
    private val localeManager: LocaleManager,
    private val soundManager: SoundManager,
    private val hapticManager: HapticManager,
) : ViewModel() {

    /**
     * Sound-on/off StateFlow exposed for the language onboarding sound toggle
     * (parity with iOS LanguageSelectionView SoundManager.shared observer).
     */
    val isSoundEnabled: StateFlow<Boolean> = prefs.isSoundEnabledFlow()
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    /** iOS parity: tapping the speaker icon flips the persisted sound flag. */
    fun toggleSound() {
        viewModelScope.launch { soundManager.toggleSound() }
    }

    val languages: List<LanguageOption> = listOf(
        LanguageOption("en", "English", "English", "A"),
        LanguageOption("hi", "Hindi", "हिन्दी", "अ"),
        LanguageOption("ta", "Tamil", "தமிழ்", "அ"),
        LanguageOption("te", "Telugu", "తెలుగు", "అ"),
        LanguageOption("kn", "Kannada", "ಕನ್ನಡ", "ಅ"),
        LanguageOption("ml", "Malayalam", "മലയാളം", "അ"),
        LanguageOption("es", "Spanish", "Español", "Ñ"),
        LanguageOption("pt", "Portuguese", "Português", "Ç"),
        LanguageOption("de", "German", "Deutsch", "Ö"),
        LanguageOption("fr", "French", "Français", "É"),
        LanguageOption("zh-Hans", "Chinese", "中文", "中"),
        LanguageOption("ja", "Japanese", "日本語", "あ"),
        LanguageOption("ru", "Russian", "Русский", "Я"),
    )

    private val _selectedCode = MutableStateFlow<String?>(null)
    val selectedCode: StateFlow<String?> = _selectedCode

    fun selectLanguage(code: String) {
        _selectedCode.value = code
        // iOS parity (LanguageSelectionView.swift:247-265): light haptic + card-select
        // sound on every language card tap, plus a particle burst overlay (deferred).
        hapticManager.light()
        soundManager.playCardSelect()
        // iOS parity (LanguageSelectionView.swift:258-259): persist appLanguageCode on
        // every tap so backgrounding the app retains the choice without confirmation.
        viewModelScope.launch {
            prefs.setSelectedLanguage(code)
        }
    }

    fun confirmSelection() {
        // iOS parity (LanguageSelectionView.swift:267-268): button is disabled until
        // a code is picked, so this is a hard requirement — assert non-null.
        val code = checkNotNull(_selectedCode.value) {
            "confirmSelection called with no language selected"
        }
        viewModelScope.launch {
            prefs.setSelectedLanguage(code)
            prefs.setLanguageSelectionComplete(true)
            localeManager.applyLocale(code)
            // iOS parity (LanguageSelectionView.swift:267-284): celebratory feedback
            // once the selection is persisted and locale applied.
            hapticManager.premiumSuccess()
            soundManager.playSuccess()
        }
    }
}
