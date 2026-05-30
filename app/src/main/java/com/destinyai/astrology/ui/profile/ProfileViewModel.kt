package com.destinyai.astrology.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.destinyai.astrology.data.local.prefs.UserPreferences
import com.destinyai.astrology.data.remote.AstroApiService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProfileUiState(
    val userName: String = "",
    val email: String = "",
    val isPremium: Boolean = false,
    val planId: String = "",
    val dailyQuota: Int = 3,
    val dailyUsed: Int = 0,
    val isLoading: Boolean = false,
    val isDeleted: Boolean = false,
    val showDeleteConfirmation: Boolean = false,
    val error: String? = null,
    val historyEnabled: Boolean = true,
    val analyticsConsent: Boolean = true,
    val showProfileSwitcher: Boolean = false,
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val api: AstroApiService,
    private val prefs: UserPreferences,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState

    fun loadProfile() {
        viewModelScope.launch {
            val email = prefs.getUserEmail() ?: return@launch
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val status = api.getStatus(email)
                val name = prefs.getUserName() ?: status.name ?: ""
                val historyEnabled = prefs.isHistoryEnabled()
                _uiState.update {
                    it.copy(
                        userName = name,
                        email = status.userEmail,
                        isPremium = status.isPremium,
                        planId = status.planId,
                        dailyQuota = status.dailyQuota,
                        dailyUsed = status.dailyUsed,
                        isLoading = false,
                        historyEnabled = historyEnabled,
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "Failed to load profile") }
            }
        }
    }

    fun toggleHistory(enabled: Boolean) {
        viewModelScope.launch {
            prefs.setHistoryEnabled(enabled)
            _uiState.update { it.copy(historyEnabled = enabled) }
        }
    }

    fun toggleAnalytics(enabled: Boolean) {
        _uiState.update { it.copy(analyticsConsent = enabled) }
    }

    fun showProfileSwitcher() = _uiState.update { it.copy(showProfileSwitcher = true) }

    fun dismissProfileSwitcher() = _uiState.update { it.copy(showProfileSwitcher = false) }

    fun showDeleteConfirmation() = _uiState.update { it.copy(showDeleteConfirmation = true) }

    fun dismissDeleteConfirmation() = _uiState.update { it.copy(showDeleteConfirmation = false) }

    fun confirmDeleteAccount() {
        viewModelScope.launch {
            val email = prefs.getUserEmail() ?: return@launch
            _uiState.update { it.copy(isLoading = true, showDeleteConfirmation = false, error = null) }
            try {
                api.deleteAccount(email)
                prefs.clearAll()
                _uiState.update { it.copy(isLoading = false, isDeleted = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "Failed to delete account") }
            }
        }
    }
}
