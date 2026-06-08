package com.destinyai.astrology.data.repository

import com.destinyai.astrology.data.remote.ProfileResponse
import com.destinyai.astrology.domain.model.User

interface AuthRepository {
    suspend fun getSavedUser(): User?
    /**
     * Sign in via Google Sign-In. Mirrors [signInWithApple] symmetry — caller
     * extracts email/sub/name from the GoogleIdTokenCredential (or decodes the
     * JWT for the `sub` claim) so the backend can validate POST
     * /subscription/register against its required `email` + optional
     * `google_id` + `name` fields. [idToken] is forwarded for forward-compat
     * server-side verification but is currently ignored by the backend.
     */
    suspend fun signInWithGoogle(
        email: String,
        googleId: String,
        name: String?,
        idToken: String? = null,
    ): Result<User>
    // Reserved: iOS-cross-platform sign-in. No Android UI path. May be wired via
    // deep-link in a future build — keep the interface + impl + DTO + endpoint intact.
    suspend fun signInWithApple(appleId: String, email: String?, name: String?): Result<User>
    suspend fun registerGuest(): Result<User>
    suspend fun upgradeGuest(guestEmail: String, newEmail: String): Result<User>
    /**
     * iOS parity (AuthViewModel.swift fetchAndRestoreProfile): fetch the
     * server-side profile (including birth_profile) for [email]. Returns
     * null on 404 or any network/parse error so callers can fall through
     * to the local-pref path without surfacing transient failures to the user.
     */
    suspend fun fetchProfile(email: String): ProfileResponse?
    suspend fun clearSession()
    suspend fun signOut() = clearSession()
    /**
     * Mirrors iOS QuotaExhaustedView.signOutAndReauth (ChatView.swift:180-191):
     * a partial sign-out used when a guest hits the quota wall and taps Sign In.
     * Clears the secure email (so AuthRepository.getSavedUser() returns null and
     * AuthScreen routes to the login UI instead of bouncing back to Main) and the
     * isAuthenticated prefs flag, BUT preserves guest birth data so performSignIn
     * can carry it forward to the new registered account. Subscription / quota
     * caches are also cleared so account-A state does not leak to account-B.
     */
    suspend fun signOutPreserveBirthData()
}
