package com.destinyai.astrology.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.destinyai.astrology.data.repository.AuthRepository
import com.destinyai.astrology.domain.model.User
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AuthUiState(
    val currentUser: User? = null,
    val isAuthenticated: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
    val showMergeDialog: Boolean = false,
    val forceLogout: Boolean = false,
)

class ConflictException(val code: String) : Exception(code)
class AccountDeletedException : Exception("account_deleted")

class AuthViewModel(
    private val repository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState

    init {
        loadSession()
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

    fun signInWithGoogle(idToken: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            repository.signInWithGoogle(idToken)
                .onSuccess { user ->
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
