package com.destinyai.astrology.ui.auth

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.destinyai.astrology.R
import com.destinyai.astrology.data.local.prefs.UserPreferences
import com.destinyai.astrology.data.repository.AuthRepository
import com.destinyai.astrology.domain.model.User
import com.destinyai.astrology.services.AppStartupService
import com.destinyai.astrology.services.HapticManager
import com.destinyai.astrology.services.SoundManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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
    val isSoundEnabled: Boolean = false,
    /** iOS parity (AuthViewModel.swift:261-270): when set to "waitlist_pending",
     *  AuthScreen routes to WaitlistPendingScreen instead of main. */
    val pendingWaitlist: Boolean = false,
    /** iOS parity (AppStartupService.swift): backend-driven guest CTA visibility.
     *  Default true so first-launch UX before fetch completes is not stricter
     *  than iOS (which also defaults until fetch lands). */
    val allowGuest: Boolean = true,
    /** iOS parity: gate mode passed through for downstream awareness. */
    val gateMode: String = "off",
    /**
     * iOS parity (AuthViewModel.swift fetchAndRestoreProfile + guestNeedsBirthData):
     * computed once per successful sign-in. true => post-auth flow must route to
     * BirthDataScreen; false => route directly to MainScreen.
     */
    val needsBirthData: Boolean = false,
    /**
     * iOS parity (AuthView.swift loadingOverlay during post-sign-in sync window):
     * true between sign-in success and the moment LoginSyncCoordinator.syncAll
     * finishes pulling chat history, subscription state, and server profile.
     * AuthScreen renders the same spinner while this is true so the user
     * never sees Home before the migrated data is ready.
     */
    val isSyncingData: Boolean = false,
)

class ConflictException(val code: String) : Exception(code)
class AccountDeletedException : Exception("account_deleted")

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val repository: AuthRepository,
    private val haptic: HapticManager,
    private val prefs: UserPreferences,
    private val appStartup: AppStartupService,
    private val soundManager: SoundManager,
    // iOS parity (AuthViewModel.swift:316 LoginSyncCoordinator.shared.syncAll):
    // post-sign-in coordinated sync so chat threads, quota, profile and partner
    // rows are fresh BEFORE the user lands on Home.
    private val loginSync: com.destinyai.astrology.services.LoginSyncCoordinator,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState

    /**
     * iOS parity (AuthViewModel.swift): map raw HTTP exception text to a
     * localized, user-friendly fallback. Lets caller surface known errors
     * verbatim (e.g. ConflictException.code) while protecting users from
     * "HTTP 500 Internal Server Error" leakage.
     */
    private fun friendlyAuthError(e: Throwable): String =
        when (e) {
            is ConflictException, is AccountDeletedException -> e.message
                ?: context.getString(R.string.auth_generic_sign_in_failed)
            else -> context.getString(R.string.auth_generic_sign_in_failed)
        }

    init {
        loadSession()
        loadSoundPref()
        loadGateConfig()
        observeSoundPref()
    }

    private fun observeSoundPref() {
        viewModelScope.launch {
            // iOS parity (SoundManager.swift:19-29): keep the toggle in sync
            // across all UI surfaces, since SoundManager owns the source of truth.
            prefs.isSoundEnabledFlow().collect { enabled ->
                _uiState.update { it.copy(isSoundEnabled = enabled) }
            }
        }
    }

    private fun loadGateConfig() {
        viewModelScope.launch {
            appStartup.fetchConfig()
            _uiState.update {
                it.copy(
                    allowGuest = appStartup.allowGuest.value,
                    gateMode = appStartup.gateMode.value,
                )
            }
        }
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
            } catch (e: Exception) {
                android.util.Log.w("AuthViewModel", "loadSession failed: ${e.message}", e)
                // Network unavailable on launch — leave unauthenticated state, don't crash
                _uiState.update { it.copy(isAuthenticated = false, currentUser = null) }
            }
        }
    }

    fun toggleSound() {
        viewModelScope.launch {
            // iOS parity (SoundManager.swift:341-347): SoundManager flips the
            // pref AND updates audio state (pause/resume) so the change is
            // audible immediately, not on the next play() call.
            val newVal = soundManager.toggleSound()
            _uiState.update { it.copy(isSoundEnabled = newVal) }
        }
    }

    /**
     * iOS parity (AuthView.swift:380-384): SoundManager.shared.playButtonTap()
     * is called from the AuthButton wrapper for every auth tap. Exposed here so
     * the composable can fire the same Tibetan-bowl tap without holding a
     * direct SoundManager reference.
     */
    fun playButtonTapSound() {
        soundManager.playButtonTap()
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    /**
     * iOS parity (AuthViewModel.swift:176-179 performSignIn): flip isLoading
     * the moment the auth button is tapped — BEFORE the native Google credential
     * picker opens — so the loading overlay (AuthScreen.kt) is visible during
     * the picker's startup and the subsequent backend round-trip. Without this,
     * the overlay only appears once viewModel.signInWithGoogle(...) runs, which
     * is after the picker has already returned.
     */
    fun beginGoogleSignIn() {
        _uiState.update { it.copy(isLoading = true, error = null) }
    }

    /**
     * iOS parity (AuthViewModel.swift performSignIn final isLoading=false on
     * cancel/throw): clear the loading state when the user dismisses the Google
     * picker without picking an account. Mirrors the silent user-cancel path —
     * no error message, just stop showing the overlay.
     */
    fun cancelGoogleSignIn() {
        _uiState.update { it.copy(isLoading = false) }
    }

    /**
     * Surfaces a Google Sign-In error from the launcher result back to the UI.
     * Used when the OAuth flow returns no ID token (e.g. GOOGLE_SERVER_CLIENT_ID
     * unconfigured) or when GoogleSignIn throws an ApiException, so the user
     * sees feedback instead of a silent no-op.
     */
    fun reportGoogleSignInError(message: String) {
        _uiState.update { it.copy(isLoading = false, error = message) }
    }

    fun signInWithGoogle(
        email: String,
        googleId: String,
        name: String?,
        idToken: String? = null,
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            // iOS parity (AuthViewModel.swift:181-333): if currently a guest,
            // capture guest email + birth profile BEFORE the new sign-in so we
            // can migrate state onto the registered account afterwards.
            val wasGuest = prefs.isGuestUser()
            val guestEmail = if (wasGuest) prefs.getUserEmail() else null
            val guestBirth = if (wasGuest) prefs.getBirthProfile() else null
            repository.signInWithGoogle(email, googleId, name, idToken)
                .onSuccess { user ->
                    haptic.success()
                    // Carry forward guest birth profile + run upgrade in best-effort mode.
                    if (wasGuest && guestEmail != null && user.email != guestEmail) {
                        try {
                            repository.upgradeGuest(guestEmail, user.email).getOrNull()
                        } catch (_: Exception) { /* ignore */ }
                        if (guestBirth != null) {
                            try { prefs.setBirthProfile(guestBirth) } catch (_: Exception) {}
                        }
                    }
                    if (user.accessState == "waitlist_pending") {
                        _uiState.update {
                            it.copy(
                                currentUser = user,
                                isLoading = false,
                                pendingWaitlist = true,
                                isAuthenticated = false,
                            )
                        }
                    } else {
                        prefs.setGuestUser(false)
                        val needsBirth = resolveNeedsBirthData(
                            email = user.email,
                            isGuest = false,
                            wasGuestUpgrade = wasGuest && guestBirth != null,
                        )
                        // iOS parity (AuthViewModel.swift:316 + AuthView loadingOverlay):
                        // run the post-sign-in coordinated sync (chat history, quota,
                        // profile) BEFORE flipping isAuthenticated, so the user sees a
                        // spinner instead of stale data when they land on Home.
                        _uiState.update {
                            it.copy(
                                currentUser = user,
                                isLoading = true,
                                isSyncingData = true,
                            )
                        }
                        runCatching {
                            loginSync.syncAll(
                                userEmail = user.email,
                                previousGuestEmail = if (wasGuest) guestEmail else null,
                            )
                        }
                        _uiState.update {
                            it.copy(
                                isAuthenticated = true,
                                isLoading = false,
                                isSyncingData = false,
                                needsBirthData = needsBirth,
                            )
                        }
                    }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(isLoading = false, error = friendlyAuthError(e), isAuthenticated = false, currentUser = null)
                    }
                }
        }
    }

    /**
     * Reserved: iOS-cross-platform sign-in. No Android UI path currently calls this —
     * the Apple sign-in button has been removed from AuthScreen and GuestSignInPromptScreen.
     * Kept intact so a future deep-link / cross-platform linking flow can wire it up
     * without re-implementing the backend integration.
     *
     * Mirrors iOS AuthViewModel.signInWithApple. The OAuth layer (Custom Tabs /
     * AndroidX Credentials Manager) extracts the apple_id "sub" claim plus the
     * first-login email/name; both email and name are nullable because Apple
     * returns them only on the very first sign-in. The backend resolves the
     * existing user by apple_id when the email is missing.
     */
    @Suppress("unused")
    fun signInWithApple(appleId: String, email: String?, name: String?) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            repository.signInWithApple(appleId, email, name)
                .onSuccess { user ->
                    haptic.success()
                    if (user.accessState == "waitlist_pending") {
                        _uiState.update {
                            it.copy(
                                currentUser = user,
                                isLoading = false,
                                pendingWaitlist = true,
                                isAuthenticated = false,
                            )
                        }
                    } else {
                        val needsBirth = resolveNeedsBirthData(
                            email = user.email,
                            isGuest = false,
                            wasGuestUpgrade = false,
                        )
                        // iOS parity: post-sign-in coordinated sync (chat, quota, profile).
                        _uiState.update {
                            it.copy(currentUser = user, isLoading = true, isSyncingData = true)
                        }
                        runCatching { loginSync.syncAll(userEmail = user.email) }
                        _uiState.update {
                            it.copy(
                                isAuthenticated = true,
                                isLoading = false,
                                isSyncingData = false,
                                needsBirthData = needsBirth,
                            )
                        }
                    }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(isLoading = false, error = friendlyAuthError(e), isAuthenticated = false, currentUser = null)
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
                    // iOS parity: guests always re-enter birth data on a fresh
                    // session unless local prefs already hold one. We never call
                    // /profile for guests (their email is generated locally and
                    // there is no server-side birth_profile to restore).
                    val needsBirth = prefs.getBirthProfile() == null
                    _uiState.update {
                        it.copy(
                            currentUser = user,
                            isAuthenticated = true,
                            isLoading = false,
                            needsBirthData = needsBirth,
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(isLoading = false, error = friendlyAuthError(e), isAuthenticated = false, currentUser = null)
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
                    val needsBirth = resolveNeedsBirthData(
                        email = user.email,
                        isGuest = false,
                        wasGuestUpgrade = true,
                    )
                    // iOS parity: run post-upgrade sync so the migrated chat threads,
                    // quota state, and server profile land before the user sees Home.
                    _uiState.update {
                        it.copy(currentUser = user, isLoading = true, isSyncingData = true)
                    }
                    runCatching {
                        loginSync.syncAll(userEmail = user.email, previousGuestEmail = guestEmail)
                    }
                    _uiState.update {
                        it.copy(
                            isAuthenticated = true,
                            isLoading = false,
                            isSyncingData = false,
                            needsBirthData = needsBirth,
                        )
                    }
                }
                .onFailure { e ->
                    if (e is ConflictException) {
                        _uiState.update { it.copy(isLoading = false, showMergeDialog = true) }
                    } else {
                        _uiState.update { it.copy(isLoading = false, error = friendlyAuthError(e)) }
                    }
                }
        }
    }

    /**
     * Compute the post-auth `needsBirthData` flag with iOS parity:
     *
     *   1. Guest sessions (isGuest=true): always rely on local prefs alone
     *      (mirrors iOS guestNeedsBirthData rule — no server fetch).
     *   2. Guest→registered upgrade with carry-forward (wasGuestUpgrade=true):
     *      local prefs are authoritative because the previous step already
     *      mirrored them onto the registered account.
     *   3. Registered Google/Apple sign-in: fetch the server profile. If it
     *      contains a birth_profile, mirror it onto local prefs (parity with
     *      iOS restoreProfileLocally + setHasBirthData=true) and return false.
     *      If absent and local prefs are also empty → true. Network or 404
     *      errors fall through to the local-pref check so offline sign-in
     *      still works.
     */
    private suspend fun resolveNeedsBirthData(
        email: String,
        isGuest: Boolean,
        wasGuestUpgrade: Boolean,
    ): Boolean {
        if (isGuest || wasGuestUpgrade) {
            return prefs.getBirthProfile() == null
        }
        val profile = try {
            repository.fetchProfile(email)
        } catch (_: Exception) {
            null
        }
        val serverBirth = profile?.birthProfile
        if (serverBirth != null) {
            try {
                prefs.setBirthProfile(serverBirth)
                prefs.setHasBirthData(true)
            } catch (_: Exception) { /* best-effort */ }
            return false
        }
        return prefs.getBirthProfile() == null
    }

    fun logout() {
        viewModelScope.launch {
            repository.clearSession()
            _uiState.update { AuthUiState() }
        }
    }

    /**
     * Suspend variant of [logout] that completes synchronously. Use from a
     * caller-owned coroutine scope (e.g. rememberCoroutineScope tied to the
     * NavHost root) when the caller must guarantee secure storage and prefs
     * are fully cleared BEFORE any navigation/popUpTo destroys this VM.
     *
     * Bug-fix rationale: viewModelScope.launch in [logout] is cancelled the
     * moment the BIRTH_DATA NavBackStackEntry is removed by popUpTo(0), so
     * clearSession() can be killed mid-clear and leave isAuthenticated=true
     * in prefs — causing AuthScreen's fresh VM to skip straight to MAIN.
     */
    suspend fun logoutAndAwait() {
        repository.clearSession()
        _uiState.update { AuthUiState() }
    }
}
