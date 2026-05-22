package com.destinyai.astrology.ui.splash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.destinyai.astrology.data.local.prefs.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class SplashDestination {
    Splash,
    LanguageSelection,
    Onboarding,
    Auth,
    WaitlistPending,
    BirthData,
    Main,
}

@HiltViewModel
class SplashViewModel @Inject constructor(
    private val prefs: UserPreferences,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SplashDestination.Splash)
    val uiState: StateFlow<SplashDestination> = _uiState

    suspend fun resolveDestination(): SplashDestination {
        if (!prefs.hasCompletedLanguageSelection()) return SplashDestination.LanguageSelection
        if (!prefs.hasSeenOnboarding()) return SplashDestination.Onboarding
        if (!prefs.isAuthenticated()) return SplashDestination.Auth
        if (prefs.getLastAccessState() == "waitlist_pending") return SplashDestination.WaitlistPending
        val hasBirth = prefs.hasBirthData()
        val isGuest = prefs.isGuestUser()
        if (!hasBirth || (isGuest && !hasBirth)) return SplashDestination.BirthData
        return SplashDestination.Main
    }

    fun navigate() {
        viewModelScope.launch {
            _uiState.value = resolveDestination()
        }
    }
}
