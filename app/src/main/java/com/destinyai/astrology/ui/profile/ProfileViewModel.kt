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
                _uiState.update {
                    it.copy(
                        userName = name,
                        email = status.userEmail,
                        isPremium = status.isPremium,
                        planId = status.planId,
                        dailyQuota = status.dailyQuota,
                        dailyUsed = status.dailyUsed,
                        isLoading = false,
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "Failed to load profile") }
            }
        }
    }

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
