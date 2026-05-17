package com.destinyai.astrology.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.destinyai.astrology.data.local.prefs.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LanguageSelectionViewModel @Inject constructor(
    private val prefs: UserPreferences,
) : ViewModel() {

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
    }

    fun confirmSelection() {
        val code = _selectedCode.value ?: "en"
        viewModelScope.launch {
            prefs.setSelectedLanguage(code)
            prefs.setLanguageSelectionComplete(true)
        }
    }
}
