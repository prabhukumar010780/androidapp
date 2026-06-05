package com.destinyai.astrology.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.destinyai.astrology.data.local.prefs.UserPreferences
import com.destinyai.astrology.data.remote.AlertItemDto
import com.destinyai.astrology.data.remote.AstroApiService
import com.destinyai.astrology.data.remote.NotificationPrefsRequest
import com.destinyai.astrology.services.LocaleManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val chartStyle: String = "north",
    val responseStyle: String = "balanced",
    val selectedLanguage: String = "en",
    val notifDailyInsight: Boolean = true,
    val notifTransits: Boolean = true,
    val notifCompatibility: Boolean = true,
    val isLoading: Boolean = false,
    val isSaved: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val api: AstroApiService,
    private val prefs: UserPreferences,
    private val localeManager: LocaleManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState

    fun loadSettings() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    chartStyle = prefs.getChartStyle(),
                    responseStyle = prefs.getResponseStyle(),
                    selectedLanguage = prefs.getSelectedLanguage(),
                    notifDailyInsight = prefs.getNotifDailyInsight(),
                    notifTransits = prefs.getNotifTransits(),
                    notifCompatibility = prefs.getNotifCompatibility(),
                )
            }
        }
    }

    fun setChartStyle(style: String) {
        _uiState.update { it.copy(chartStyle = style) }
        viewModelScope.launch { prefs.setChartStyle(style) }
    }

    fun setResponseStyle(style: String) {
        _uiState.update { it.copy(responseStyle = style) }
        viewModelScope.launch { prefs.setResponseStyle(style) }
    }

    fun setLanguage(lang: String) {
        _uiState.update { it.copy(selectedLanguage = lang) }
        viewModelScope.launch { prefs.setSelectedLanguage(lang) }
    }

    /** R2-S6: persist + trigger live re-localisation via LocaleManager. */
    fun setLanguageWithLocale(lang: String) {
        _uiState.update { it.copy(selectedLanguage = lang) }
        viewModelScope.launch {
            prefs.setSelectedLanguage(lang)
            localeManager.applyLocale(lang)
        }
    }

    fun setNotifDailyInsight(enabled: Boolean) = _uiState.update { it.copy(notifDailyInsight = enabled) }
    fun setNotifTransits(enabled: Boolean) = _uiState.update { it.copy(notifTransits = enabled) }
    fun setNotifCompatibility(enabled: Boolean) = _uiState.update { it.copy(notifCompatibility = enabled) }

    fun saveNotifPrefs() {
        viewModelScope.launch {
            val s = _uiState.value
            val email = prefs.getUserEmail() ?: return@launch
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                // Map Settings toggles to backend channel schema (parity with iOS).
                // dailyInsight = master push toggle. Inbox + email default ON unless dailyInsight off.
                // CRITICAL: Personalized alerts are managed in NotificationPreferencesScreen — Settings
                // must preserve them. Load the current list and forward it instead of wiping with [].
                val tz = java.util.TimeZone.getDefault().id
                val existingAlerts = prefs.getAlertItems().map {
                    AlertItemDto(
                        id = it.id,
                        text = it.text,
                        frequency = it.frequency.uppercase(),
                        frequencyDay = it.frequencyDay,
                    )
                }
                api.updateNotificationPrefs(
                    email,
                    NotificationPrefsRequest(
                        dailyInsight = s.notifDailyInsight,
                        transits = s.notifTransits,
                        compatibility = s.notifCompatibility,
                        isEnabled = s.notifDailyInsight || s.notifTransits || s.notifCompatibility,
                        pushEnabled = s.notifDailyInsight,
                        emailEnabled = s.notifDailyInsight,
                        inAppEnabled = s.notifDailyInsight,
                        alertItems = existingAlerts,
                        frequency = "DAILY",
                        timezone = tz,
                    )
                )
                prefs.setNotifPrefs(s.notifDailyInsight, s.notifTransits, s.notifCompatibility)
                _uiState.update { it.copy(isLoading = false, isSaved = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "Failed to save") }
            }
        }
    }
}
