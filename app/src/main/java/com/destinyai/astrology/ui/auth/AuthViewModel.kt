package com.destinyai.astrology.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.destinyai.astrology.data.local.prefs.UserPreferences
import com.destinyai.astrology.data.repository.AuthRepository
import com.destinyai.astrology.domain.model.User
import com.destinyai.astrology.services.HapticManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AuthUiState(
    val currentUser: User? = null,
    val isAuthenticated: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
    val showMergeDialog: Boolean = false,
    val forceLogout: Boolean = false,
    val isSoundEnabled: Boolean = true,
)

class ConflictException(val code: String) : Exception(code)
class AccountDeletedException : Exception("account_deleted")

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val repository: AuthRepository,
    private val haptic: HapticManager,
    private val prefs: UserPreferences,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState

    init {
        loadSession()
        loadSoundPref()
    }

    private fun loadSoundPref() {
        viewModelScope.launch {
            val enabled = prefs.isSoundEnabled()
            _uiState.update { it.copy(isSoundEnabled = enabled) }
        }
    }

    private fun loadSession() {
        viewModelScope.launch {
            try {
                val user = repository.getSavedUser()
                _uiState.update {
                    it.copy(currentUser = user, isAuthenticated = user != null)
                }
            } catch (e: AccountDeletedException) {
                _uiState.update { it.copy(forceLogout = true) }
            }
        }
    }

    fun toggleSound() {
        viewModelScope.launch {
            val newVal = !_uiState.value.isSoundEnabled
            prefs.setSoundEnabled(newVal)
            _uiState.update { it.copy(isSoundEnabled = newVal) }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun signInWithGoogle(idToken: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            repository.signInWithGoogle(idToken)
                .onSuccess { user ->
                    haptic.success()
                    _uiState.update {
                        it.copy(currentUser = user, isAuthenticated = true, isLoading = false)
                    }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(isLoading = false, error = e.message, isAuthenticated = false, currentUser = null)
                    }
                }
        }
    }

    fun continueAsGuest() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            repository.registerGuest()
                .onSuccess { user ->
                    haptic.success()
                    _uiState.update {
                        it.copy(currentUser = user, isAuthenticated = true, isLoading = false)
                    }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(isLoading = false, error = e.message, isAuthenticated = false, currentUser = null)
                    }
                }
        }
    }

    fun upgradeGuest(guestEmail: String, newEmail: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            repository.upgradeGuest(guestEmail, newEmail)
                .onSuccess { user ->
                    haptic.success()
                    _uiState.update {
                        it.copy(currentUser = user, isAuthenticated = true, isLoading = false)
                    }
                }
                .onFailure { e ->
                    if (e is ConflictException) {
                        _uiState.update { it.copy(isLoading = false, showMergeDialog = true) }
                    } else {
                        _uiState.update { it.copy(isLoading = false, error = e.message) }
                    }
                }
        }
    }

    fun logout() {
        viewModelScope.launch {
            repository.clearSession()
            _uiState.update { AuthUiState() }
        }
    }
}
