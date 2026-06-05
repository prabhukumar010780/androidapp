package com.destinyai.astrology.data.repository

import com.destinyai.astrology.data.remote.BirthProfileDto
import com.destinyai.astrology.data.remote.ProfileResponse
import com.destinyai.astrology.data.remote.StatusResponse
import com.destinyai.astrology.data.remote.SuccessResponse

/**
 * Android parity for iOS [ProfileService]. Centralises the
 * /subscription/profile, /subscription/status, /subscription/account/delete,
 * /subscription/analytics-consent, /subscription/upgrade and
 * /subscription/partners (self-profile bootstrap) endpoints behind a single
 * repository so callers (ViewModels, WorkManager, ProfileChangeBus) do not
 * import [com.destinyai.astrology.data.remote.AstroApiService] directly.
 *
 * Mirrors iOS [ProfileService] one-for-one:
 *  - [fetchProfile]              ↔ ProfileService.fetchProfile
 *  - [saveProfile] (DTO)         ↔ ProfileService.saveProfile (BirthProfileData)
 *  - [saveProfile] (raw fields)  ↔ ProfileService.saveProfile (BirthData overload)
 *  - [restoreProfileLocally]     ↔ ProfileService.restoreProfileLocally
 *  - [deleteAccount]             ↔ ProfileService.deleteAccount
 *  - [getUserStatus]             ↔ ProfileService.getUserStatus
 *  - [updateAnalyticsConsent]    ↔ ProfileService.updateAnalyticsConsent
 *  - [createSelfPartnerProfile]  ↔ ProfileService.createSelfPartnerProfile
 *  - [upgradeGuestToRegistered]  ↔ ProfileService.upgradeGuestToRegistered
 */
interface ProfileRepository {

    /**
     * GET /subscription/profile?email=... — returns null on 404 (no profile yet)
     * or any non-fatal error. Mirrors iOS [ProfileService.fetchProfile] which
     * returns nil on 404 so callers fall through to local prefs.
     */
    suspend fun fetchProfile(email: String): ProfileResponse?

    /**
     * POST /subscription/profile — DTO overload that mirrors iOS
     * `saveProfile(email:, userName:, userType:, isGeneratedEmail:, birthProfile:)`.
     * Throws [BirthDataTakenException] on 409. Returns the freshly persisted profile.
     */
    suspend fun saveProfile(
        email: String,
        userName: String?,
        userType: String,
        isGeneratedEmail: Boolean,
        birthProfile: BirthProfileDto,
        appleId: String? = null,
        googleId: String? = null,
    ): ProfileResponse

    /**
     * POST /subscription/profile — raw-fields overload that mirrors iOS
     * `saveProfile(email:, userName:, birthData:, isGuest:, gender:)`. Used by
     * BirthDataViewModel and by guest→registered carry-forward where a typed
     * BirthData object is the natural input shape.
     */
    suspend fun saveProfile(
        email: String,
        userName: String,
        dateOfBirth: String,
        timeOfBirth: String,
        cityOfBirth: String,
        latitude: Double,
        longitude: Double,
        isGuest: Boolean,
        gender: String = "",
        birthTimeUnknown: Boolean = false,
        placeId: String? = null,
        appleId: String? = null,
        googleId: String? = null,
    ): ProfileResponse

    /**
     * Mirror server profile data into local prefs (DataStore + SecureStorage)
     * after [fetchProfile] / [saveProfile]. iOS parity: also boots the self
     * partner profile via [createSelfPartnerProfile] when a birth profile exists.
     * Returns true on successful local restore (false when no birthProfile present).
     */
    suspend fun restoreProfileLocally(profile: ProfileResponse): Boolean

    /**
     * POST /subscription/account/delete — soft-delete. Throws
     * [AccountDeletionBlockedException] on 403 ACTIVE_SUBSCRIPTION semantics.
     */
    suspend fun deleteAccount(email: String): SuccessResponse

    /**
     * GET /subscription/status — drives waitlist auto-recheck on Android, and
     * mirrors iOS [ProfileService.getUserStatus]. Includes analytics_consent so
     * Profile screen can render the correct toggle state on launch.
     */
    suspend fun getUserStatus(email: String): StatusResponse

    /**
     * POST /subscription/analytics-consent — toggle the analytics-consent flag
     * server-side. Best-effort caller pattern: failures must not block UI.
     */
    suspend fun updateAnalyticsConsent(email: String, consent: Boolean): SuccessResponse

    /**
     * POST /subscription/partners with is_self=true. Idempotent: skips when a
     * self profile already exists for [email]. Best-effort.
     */
    suspend fun createSelfPartnerProfile(
        email: String,
        userName: String,
        birthProfile: BirthProfileDto,
    )

    /**
     * POST /subscription/upgrade — migrate guest chat history to a registered
     * account. iOS-parity error semantics: 409 with detail.error =
     * "archived_guest" → [ArchivedGuestError]; 409 with detail.error =
     * "registered_user_conflict" → [RegisteredUserConflictError].
     * Returns the upgraded user's email when reachable; returns null on
     * non-fatal failures so the upgrade can be retried later (iOS treats
     * upgrade as a best-effort migration).
     */
    suspend fun upgradeGuestToRegistered(oldEmail: String, newEmail: String): String?
}

/**
 * iOS parity (ProfileService.swift:537-544): server returned 403 because the
 * account has an active subscription that must be cancelled before deletion.
 * The `detail` string is the localized message returned by the backend.
 */
class AccountDeletionBlockedException(message: String) : Exception(message)

/**
 * iOS parity (ProfileService.swift:299-308): birth data is already linked to
 * another registered user. The optional [existingEmail] / [provider] fields let
 * the UI render a friendly "this birth data is linked to your Apple/Google
 * account, please sign in" message.
 */
class BirthDataTakenException(
    val existingEmail: String?,
    val provider: String?,
) : Exception("birth_data_taken")
