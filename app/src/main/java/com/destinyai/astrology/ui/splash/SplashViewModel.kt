package com.destinyai.astrology.ui.splash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.destinyai.astrology.data.local.prefs.SecureStorage
import com.destinyai.astrology.data.local.prefs.UserPreferences
import com.destinyai.astrology.data.remote.AstroApiService
import com.destinyai.astrology.data.remote.RegisterRequest
import com.destinyai.astrology.data.repository.AuthRepository
import com.destinyai.astrology.services.AppStartupService
import com.destinyai.astrology.ui.auth.AccountDeletedException
import com.destinyai.astrology.ui.auth.AccountDeletedError
import dagger.hilt.android.lifecycle.HiltViewModel
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
    // iOS parity (AppRootView.swift:204-210): on account_deleted detection at launch-time,
    // run the full sign-out teardown (not just a flag flip) so quota/billing/cache are wiped.
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SplashDestination.Splash)
    val uiState: StateFlow<SplashDestination> = _uiState

    /**
     * iOS parity (AppRootView.swift:183-214 recheckWaitlistStatus): when authenticated,
     * re-call subscription/register on every launch to refresh access_state. This
     * re-routes a user whose waitlist was approved (or revoked) while the app was
     * closed.
     *
     * Fix 6 (HIGH): if the server signals account_deleted/account_archived (403 with
     * detail.error="account_deleted", or AccountDeletedException from the register path),
     * run the full sign-out teardown so resolveDestination() routes to Auth — matching
     * iOS AppRootView.swift:204-210 which calls AuthViewModel().signOutAsync().
     *
     * Transient network errors are still swallowed — only the authoritative account_deleted
     * signal triggers sign-out.
     */
    internal suspend fun recheckWaitlistStatus(): Boolean {
        if (!prefs.isAuthenticated()) return false
        val email = secure.getEmail() ?: prefs.getUserEmail() ?: return false
        if (email.isEmpty()) return false
        return try {
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
            false
        } catch (e: AccountDeletedException) {
            // Typed AccountDeletedException from the register path (re-thrown by
            // AuthRepositoryImpl.signInWithGoogle when 403 detail.error="account_deleted").
            // The same type is also thrown directly by QuotaManager.registerUser.
            android.util.Log.w("SplashViewModel", "Account deleted (AccountDeletedException) — forcing sign-out")
            runCatching { authRepository.clearSession() }
            true
        } catch (e: AccountDeletedError) {
            // AccountDeletedError is thrown by QuotaManager.syncStatus / ProfileRepositoryImpl
            // on 403 detail.error="account_deleted". Treat identically.
            android.util.Log.w("SplashViewModel", "Account deleted (AccountDeletedError) — forcing sign-out")
            runCatching { authRepository.clearSession() }
            true
        } catch (e: retrofit2.HttpException) {
            // Attempt direct parse for 403 account_deleted body in case the raw HttpException
            // escapes the above typed paths (e.g. called from api.register directly without
            // the typed wrapper). Matches iOS AppRootView.swift:204 ProfileError.isAccountDeleted.
            if (e.code() == 403 && parseAccountDeletedBody(e)) {
                android.util.Log.w("SplashViewModel", "Account deleted (403 body) — forcing sign-out")
                runCatching { authRepository.clearSession() }
                true
            } else {
                // Transient 403 (rate-limit / auth) or other HTTP — preserve session.
                false
            }
        } catch (_: Exception) {
            // Network unavailable — preserve existing access state.
            false
        }
    }

    /**
     * Parse a 403 HttpException body to detect detail.error == "account_deleted".
     * Mirrors AuthRepositoryImpl.parseAccountDeletedError.
     */
    private fun parseAccountDeletedBody(e: retrofit2.HttpException): Boolean = runCatching {
        val raw = e.response()?.errorBody()?.string().orEmpty()
        if (raw.isBlank()) return@runCatching false
        val root = com.google.gson.JsonParser.parseString(raw)
        if (!root.isJsonObject) return@runCatching false
        val detail = root.asJsonObject.get("detail") ?: return@runCatching false
        val errorField = when {
            detail.isJsonObject -> detail.asJsonObject.get("error")
                ?.takeIf { it.isJsonPrimitive }?.asString
            detail.isJsonPrimitive -> detail.asString
            else -> null
        }
        errorField == "account_deleted"
    }.getOrDefault(false)

    suspend fun resolveDestination(): SplashDestination {
        // iOS parity: AppRootView.swift:124 calls `await appStartup.fetchConfig()`
        // at startup so feature flags / gate config are primed before main UI loads.
        // Failures are swallowed inside fetchConfig — fail open with prior cache.
        appStartup.fetchConfig()
        if (!prefs.hasCompletedLanguageSelection()) return SplashDestination.LanguageSelection
        if (!prefs.hasSeenOnboarding()) return SplashDestination.Onboarding
        if (!prefs.isAuthenticated()) return SplashDestination.Auth
        // Refresh waitlist/access_state from backend before reading local prefs.
        // recheckWaitlistStatus() returns true if an account_deleted sign-out was triggered —
        // in that case isAuthenticated is now false so re-route to Auth immediately.
        val wasForceSignedOut = recheckWaitlistStatus()
        if (wasForceSignedOut || !prefs.isAuthenticated()) return SplashDestination.Auth
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
            val startMs = System.currentTimeMillis()
            val destination = resolveDestination()
            val elapsed = System.currentTimeMillis() - startMs
            // B1: returning users (Main) only need a ~500ms floor so the brand reveal
            // animation completes without making them wait 2.5s. First-run destinations
            // (Onboarding, Auth, BirthData, etc.) still observe the full 2.5s so the
            // scripted logo + dot reveal has time to play — matching iOS SplashView timing.
            val floor = if (destination == SplashDestination.Main) 500L else MIN_SPLASH_DISPLAY_MS
            val remaining = (floor - elapsed).coerceAtLeast(0L)
            if (remaining > 0) delay(remaining)
            _uiState.value = destination
        }
    }
}
