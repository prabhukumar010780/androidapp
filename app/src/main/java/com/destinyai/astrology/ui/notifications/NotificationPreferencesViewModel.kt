package com.destinyai.astrology.ui.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.destinyai.astrology.data.local.prefs.UserPreferences
import com.destinyai.astrology.data.remote.AstroApiService
import com.destinyai.astrology.data.remote.NotificationPrefsRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class NotificationPreferencesUiState(
    val dailyInsight: Boolean = true,
    val transits: Boolean = true,
    val compatibility: Boolean = true,
    val isLoading: Boolean = false,
    val isSaved: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class NotificationPreferencesViewModel @Inject constructor(
    private val api: AstroApiService,
    private val prefs: UserPreferences,
) : ViewModel() {

    private val _uiState = MutableStateFlow(NotificationPreferencesUiState())
    val uiState: StateFlow<NotificationPreferencesUiState> = _uiState

    fun loadPrefs() {
        viewModelScope.launch {
            val email = prefs.getUserEmail() ?: return@launch
            _uiState.update { it.copy(isLoading = true) }
            try {
                val dto = api.getNotificationPrefs(email)
                _uiState.update {
                    it.copy(
                        dailyInsight = dto.dailyInsight,
                        transits = dto.transits,
                        compatibility = dto.compatibility,
                        isLoading = false,
                    )
                }
            } catch (_: Exception) {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun setDailyInsight(enabled: Boolean) = _uiState.update { it.copy(dailyInsight = enabled) }
    fun setTransits(enabled: Boolean) = _uiState.update { it.copy(transits = enabled) }
    fun setCompatibility(enabled: Boolean) = _uiState.update { it.copy(compatibility = enabled) }

    fun save() {
        viewModelScope.launch {
            val email = prefs.getUserEmail() ?: return@launch
            val s = _uiState.value
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                api.updateNotificationPrefs(
                    email,
                    NotificationPrefsRequest(s.dailyInsight, s.transits, s.compatibility)
                )
                prefs.setNotifPrefs(s.dailyInsight, s.transits, s.compatibility)
                _uiState.update { it.copy(isLoading = false, isSaved = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "Failed to save") }
            }
        }
    }
}
