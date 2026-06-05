package com.destinyai.astrology.ui.splash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.destinyai.astrology.data.local.prefs.SecureStorage
import com.destinyai.astrology.data.local.prefs.UserPreferences
import com.destinyai.astrology.data.remote.AstroApiService
import com.destinyai.astrology.data.remote.RegisterRequest
import com.destinyai.astrology.services.AppStartupService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
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

// iOS parity: AppRootView shows the splash overlay for a fixed 2.5s before dismissing
// (AppRootView.swift:117-121). Android must enforce the same minimum so the brand
// reveal + animated dots are actually visible.
private const val MIN_SPLASH_DISPLAY_MS = 2500L

@HiltViewModel
class SplashViewModel @Inject constructor(
    private val prefs: UserPreferences,
    private val secure: SecureStorage,
    private val api: AstroApiService,
    private val appStartup: AppStartupService,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SplashDestination.Splash)
    val uiState: StateFlow<SplashDestination> = _uiState

    /**
     * iOS parity (AppRootView.swift:138-162 recheckWaitlistStatus): when authenticated,
     * re-call subscription/register on every launch to refresh access_state. This
     * re-routes a user whose waitlist was approved (or revoked) while the app was
     * closed. Failures are swallowed — local lastAccessState remains the source of truth.
     */
    private suspend fun recheckWaitlistStatus() {
        if (!prefs.isAuthenticated()) return
        val email = secure.getEmail() ?: prefs.getUserEmail() ?: return
        if (email.isEmpty()) return
        try {
            val resp = api.register(
                RegisterRequest(
                    email = email,
                    isGeneratedEmail = false,
                    googleId = prefs.getGoogleUserId(),
                    name = null,
                ),
            )
            prefs.setLastAccessState(resp.accessState)
            prefs.setAccessState(resp.accessState)
        } catch (_: Exception) {
            // Silently ignore — preserve existing access state on network failure.
        }
    }

    suspend fun resolveDestination(): SplashDestination {
        // iOS parity: AppRootView.swift:124 calls `await appStartup.fetchConfig()`
        // at startup so feature flags / gate config are primed before main UI loads.
        // Failures are swallowed inside fetchConfig — fail open with prior cache.
        appStartup.fetchConfig()
        if (!prefs.hasCompletedLanguageSelection()) return SplashDestination.LanguageSelection
        if (!prefs.hasSeenOnboarding()) return SplashDestination.Onboarding
        if (!prefs.isAuthenticated()) return SplashDestination.Auth
        // Refresh waitlist/access_state from backend before reading local prefs.
        recheckWaitlistStatus()
        if (prefs.getLastAccessState() == "waitlist_pending") return SplashDestination.WaitlistPending
        // iOS parity: warm starts after a sign-in must gate the same way as the
        // post-auth flow. Check both the explicit flag AND the actual birth
        // profile prefs — the flag is set after BirthDataScreen save and after
        // resolveNeedsBirthData restores the server profile, but a stale or
        // missing flag with present birth fields should still route to Main.
        val hasBirth = prefs.hasBirthData() || prefs.hasCompleteBirthProfile()
        if (!hasBirth) return SplashDestination.BirthData
        return SplashDestination.Main
    }

    fun navigate() {
        viewModelScope.launch {
            // Run destination resolution and the minimum splash display window
            // concurrently so the splash always lasts at least MIN_SPLASH_DISPLAY_MS,
            // matching iOS's 2.5s scripted reveal even when resolution is instant.
            val destination = coroutineScope {
                val destDeferred = async { resolveDestination() }
                val delayDeferred = async { delay(MIN_SPLASH_DISPLAY_MS) }
                awaitAll(delayDeferred, destDeferred)
                destDeferred.getCompleted()
            }
            _uiState.value = destination
        }
    }
}
