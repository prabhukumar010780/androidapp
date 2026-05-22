package com.destinyai.astrology.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.destinyai.astrology.data.local.prefs.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class WaitlistUiState(
    val userEmail: String = "",
    val isSignedOut: Boolean = false,
)

@HiltViewModel
class WaitlistPendingViewModel @Inject constructor(
    private val prefs: UserPreferences,
) : ViewModel() {

    private val _uiState = MutableStateFlow(WaitlistUiState())
    val uiState: StateFlow<WaitlistUiState> = _uiState

    fun loadEmail() {
        viewModelScope.launch {
            val email = prefs.getUserEmail() ?: ""
            _uiState.update { it.copy(userEmail = email) }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            prefs.setAuthenticated(false)
            prefs.setLastAccessState("unknown")
            _uiState.update { it.copy(isSignedOut = true) }
        }
    }
}
