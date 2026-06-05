package com.destinyai.astrology.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.destinyai.astrology.data.local.prefs.SecureStorage
import com.destinyai.astrology.data.local.prefs.UserPreferences
import com.destinyai.astrology.data.repository.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class WaitlistUiState(
    val userEmail: String = "",
    val isSignedOut: Boolean = false,
    // iOS parity (AppRootView.swift:83-85 .task{recheckWaitlistStatus}): when the
    // backend re-promotes the user past the waitlist, this access state changes
    // away from "waitlist_pending" and AppNav uses it to route to BirthData/Main.
    val accessState: String = "waitlist_pending",
    // Resolved when the waitlist is cleared — true if the user already has a
    // birth profile locally (route to MAIN), false if they still need to enter
    // birth data first (route to BIRTH_DATA). Mirrors AppRootView guards on
    // hasBirthData / guestNeedsBirthData after lastAccessState changes.
    val hasBirthDataOnAccess: Boolean = false,
)

@HiltViewModel
class WaitlistPendingViewModel @Inject constructor(
    private val prefs: UserPreferences,
    private val secure: SecureStorage,
    private val profileRepository: ProfileRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(WaitlistUiState())
    val uiState: StateFlow<WaitlistUiState> = _uiState

    private var recheckJob: Job? = null

    fun loadEmail() {
        viewModelScope.launch {
            val email = prefs.getUserEmail() ?: ""
            _uiState.update { it.copy(userEmail = email) }
        }
    }

    /**
     * iOS parity (AppRootView.swift:138-162 recheckWaitlistStatus + .task on
     * WaitlistPendingView): while the user sits on the waitlist screen, poll
     * /subscription/status (ProfileRepository.getUserStatus) periodically so an
     * off-screen approval re-routes them forward without requiring a manual app
     * restart. Failures are silently swallowed so transient network drops don't
     * kick the user out. Cancels any prior job so the screen can call this on
     * recomposition without leaking timers.
     */
    fun startRecheckPolling(intervalMs: Long = RECHECK_INTERVAL_MS) {
        recheckJob?.cancel()
        recheckJob = viewModelScope.launch {
            while (true) {
                recheckOnce()
                if (_uiState.value.accessState != "waitlist_pending") return@launch
                delay(intervalMs)
            }
        }
    }

    suspend fun recheckOnce() {
        if (!prefs.isAuthenticated()) return
        val email = secure.getEmail() ?: prefs.getUserEmail() ?: return
        if (email.isEmpty()) return
        try {
            // iOS parity (ProfileService.getUserStatus): use the lightweight
            // /subscription/status endpoint instead of /subscription/register
            // so the recheck doesn't accidentally re-register the user or
            // trigger any waitlist re-entry side effects on the backend.
            val resp = profileRepository.getUserStatus(email)
            prefs.setLastAccessState(resp.accessState)
            prefs.setAccessState(resp.accessState)
            // Resolve birth-data status now so AppNav can route directly without
            // a second async hop. Mirrors SplashViewModel.resolveDestination's
            // hasBirth check (hasBirthData OR hasCompleteBirthProfile).
            val hasBirth = prefs.hasBirthData() || prefs.hasCompleteBirthProfile()
            _uiState.update {
                it.copy(accessState = resp.accessState, hasBirthDataOnAccess = hasBirth)
            }
        } catch (_: Exception) {
            // Silently ignore — preserve existing access state on network failure.
        }
    }

    fun signOut() {
        viewModelScope.launch {
            recheckJob?.cancel()
            prefs.setAuthenticated(false)
            prefs.setLastAccessState("unknown")
            _uiState.update { it.copy(isSignedOut = true) }
        }
    }

    override fun onCleared() {
        recheckJob?.cancel()
        super.onCleared()
    }

    private companion object {
        // 15s mirrors the iOS recheck cadence (.task fires on appear + periodic).
        const val RECHECK_INTERVAL_MS = 15_000L
    }
}
