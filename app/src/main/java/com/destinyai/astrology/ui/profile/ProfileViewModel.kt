package com.destinyai.astrology.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.destinyai.astrology.data.local.prefs.UserPreferences
import com.destinyai.astrology.data.remote.AnalyticsConsentRequest
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
    val showDeleteSheet: Boolean = false,
    val error: String? = null,
    val snackbarMessage: String? = null,
    val historyEnabled: Boolean = true,
    val analyticsConsent: Boolean = true,
    val showProfileSwitcher: Boolean = false,
    val pendingUpgradePlanId: String? = null,
    val pendingUpgradeDate: String? = null,
    val hasActiveSubscription: Boolean = false,
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
                        hasActiveSubscription = status.isPremium,
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "Failed to load profile") }
            }
        }
    }

    fun refreshAll() {
        loadProfile()
    }

    fun toggleHistory(enabled: Boolean) {
        viewModelScope.launch {
            prefs.setHistoryEnabled(enabled)
            _uiState.update { it.copy(historyEnabled = enabled) }
        }
    }

    fun toggleAnalytics(enabled: Boolean) {
        viewModelScope.launch {
            val email = prefs.getUserEmail() ?: run {
                _uiState.update { it.copy(analyticsConsent = enabled) }
                return@launch
            }
            _uiState.update { it.copy(analyticsConsent = enabled) }
            try {
                api.updateAnalyticsConsent(AnalyticsConsentRequest(email = email, consent = enabled))
            } catch (_: Exception) {
                // best-effort; state already flipped locally
            }
        }
    }

    fun clearChatHistory() {
        viewModelScope.launch {
            val email = prefs.getUserEmail() ?: return@launch
            try {
                api.deleteAllChatHistory(email)
                _uiState.update { it.copy(snackbarMessage = "Chat history cleared") }
            } catch (e: Exception) {
                _uiState.update { it.copy(snackbarMessage = "Failed to clear history") }
            }
        }
    }

    fun clearSnackbar() = _uiState.update { it.copy(snackbarMessage = null) }

    fun showProfileSwitcher() = _uiState.update { it.copy(showProfileSwitcher = true) }

    fun dismissProfileSwitcher() = _uiState.update { it.copy(showProfileSwitcher = false) }

    fun showDeleteConfirmation() = _uiState.update { it.copy(showDeleteSheet = true, showDeleteConfirmation = true) }

    fun dismissDeleteConfirmation() = _uiState.update { it.copy(showDeleteSheet = false, showDeleteConfirmation = false) }

    fun confirmDeleteAccount() {
        viewModelScope.launch {
            val email = prefs.getUserEmail() ?: return@launch
            _uiState.update { it.copy(isLoading = true, showDeleteSheet = false, showDeleteConfirmation = false, error = null) }
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
