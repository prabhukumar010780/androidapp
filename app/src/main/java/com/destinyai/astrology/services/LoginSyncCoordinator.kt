package com.destinyai.astrology.services

import android.util.Log
import com.destinyai.astrology.data.local.db.ChatThreadDao
import com.destinyai.astrology.data.local.db.PartnerDao
import com.destinyai.astrology.data.local.prefs.UserPreferences
import com.destinyai.astrology.data.remote.AstroApiService
import com.destinyai.astrology.data.remote.ChatThreadDto
import com.destinyai.astrology.data.repository.AuthRepository
import com.destinyai.astrology.data.repository.ChatRepository
import com.destinyai.astrology.data.repository.HomeRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Android counterpart to iOS LoginSyncCoordinator
 * (ios_app/ios_app/Services/LoginSyncCoordinator.swift) PLUS the
 * fetchAndRestoreProfile work iOS AuthViewModel does inline (lines 624-688).
 *
 * Coordinates all post-sign-in API calls that must complete before the user
 * lands on Home, so the UI never renders stale or missing data:
 *  1. Wipe the prior guest's local Room rows so server-migrated rows can be
 *     re-pulled with correct ownership (parity with iOS DataManager.deleteAllThreads).
 *  2. Server profile fetch (mirrors iOS ProfileService.fetchProfile + restoreProfileLocally).
 *  3. Subscription / quota sync (mirrors iOS QuotaManager.syncStatus).
 *  4. Chart + today's prediction prefetch so Home renders with a warm cache
 *     (mirrors iOS UserChartService.fetchFullChartData + getTodaysPrediction
 *     called inside ProfileSetupLoadingView, which iOS shows transparently
 *     during the sign-in window via AuthView.loadingOverlay).
 *  5. Chat thread list (mirrors iOS ChatHistorySyncService.syncFromServer).
 *
 * iOS runs chat + compat in parallel via `async let`; we mirror that here with
 * `async {…}` so the user pays the slowest single fetch, not the sum. Failures
 * are logged + swallowed — a transient network error after a successful sign-in
 * must not block the UI (matches iOS catch-print-continue pattern).
 */
@Singleton
class LoginSyncCoordinator @Inject constructor(
    private val api: AstroApiService,
    private val chatRepository: ChatRepository,
    private val authRepository: AuthRepository,
    private val homeRepository: HomeRepository,
    private val quotaManager: QuotaManager,
    private val chatThreadDao: ChatThreadDao,
    private val partnerDao: PartnerDao,
    private val prefs: UserPreferences,
) {
    /**
     * Legacy thin shim retained for any caller that just wants the raw thread
     * list (mirrors the original Android stub). New call sites should prefer
     * [syncAll] which performs the full iOS-parity post-sign-in flow.
     */
    suspend fun syncAfterLogin(userId: String): List<ChatThreadDto> = try {
        api.listChatThreads(userId)
    } catch (e: Exception) {
        Log.w(TAG, "syncAfterLogin: thread fetch failed — ${e.message}")
        emptyList()
    }

    /**
     * Run all sync steps for [userEmail]. If [previousGuestEmail] is non-null and
     * differs from [userEmail], the prior guest's local rows are purged so
     * the server-migrated rows aren't shadowed by stale duplicates.
     *
     * Mirrors iOS LoginSyncCoordinator.syncAll + AuthViewModel.fetchAndRestoreProfile
     * (ios_app/ios_app/ViewModels/AuthViewModel.swift:624-688).
     *
     * Suspends until ALL parallel fetches complete so the host can keep the
     * loading overlay up and only navigate forward when this returns.
     */
    suspend fun syncAll(
        userEmail: String,
        previousGuestEmail: String? = null,
    ) = coroutineScope {
        Log.d(TAG, "syncAll start for $userEmail (previousGuest=$previousGuestEmail)")

        // 1. Drop stale guest rows BEFORE pulling server state, mirroring iOS
        //    DataManager.shared.deleteAllThreads(for: guestEmail) at AuthVM:208.
        if (!previousGuestEmail.isNullOrBlank() && previousGuestEmail != userEmail) {
            runCatching {
                chatThreadDao.deleteAllForUser(previousGuestEmail)
                partnerDao.deleteForOwner(previousGuestEmail)
            }.onFailure { Log.w(TAG, "guest row purge failed: ${it.message}", it) }
        }

        // 2. Fan out server fetches in parallel so the user pays the slowest
        //    single fetch, not the sum (iOS uses async let for this exact reason).
        val chatSync = async {
            runCatching { chatRepository.syncThreadsFromApi() }
                .onFailure { Log.w(TAG, "chat sync failed: ${it.message}", it) }
        }
        val quotaSync = async {
            runCatching { quotaManager.syncStatus(userEmail, force = true) }
                .onFailure { Log.w(TAG, "quota sync failed: ${it.message}", it) }
        }
        val profileFetch = async {
            // iOS AuthViewModel.fetchAndRestoreProfile (AuthViewModel.swift:624):
            // pulls server-stored birth profile so the post-sign-in Home greeting
            // uses the canonical name + birth data even if local prefs were cleared.
            runCatching {
                val profile = authRepository.fetchProfile(userEmail) ?: return@runCatching
                profile.userName?.takeIf { it.isNotBlank() }?.let { prefs.setUserName(it) }
            }.onFailure { Log.w(TAG, "profile fetch failed: ${it.message}", it) }
        }
        // iOS parity (ProfileSetupLoadingView phases 1+3 — chart + today's
        // prediction prefetch). On iOS these run inside ProfileSetupLoadingView
        // which is shown WITHIN the loading window. We surface them here so the
        // single AuthView spinner covers the same span.
        val chartPrefetch = async {
            val birth = prefs.getBirthProfile() ?: return@async
            // Login-time prefetch always uses self profile (no switch yet).
            runCatching { homeRepository.getRichHomeData(userEmail, birth, userEmail) }
                .onFailure { Log.w(TAG, "chart prefetch failed: ${it.message}", it) }
        }
        val predictionPrefetch = async {
            val birth = prefs.getBirthProfile() ?: return@async
            runCatching { homeRepository.getDailyInsight(birth, userEmail) }
                .onFailure { Log.w(TAG, "prediction prefetch failed: ${it.message}", it) }
        }

        chatSync.await()
        quotaSync.await()
        profileFetch.await()
        chartPrefetch.await()
        predictionPrefetch.await()
        Log.d(TAG, "syncAll complete for $userEmail")
    }

    private companion object {
        const val TAG = "LoginSyncCoordinator"
    }
}

