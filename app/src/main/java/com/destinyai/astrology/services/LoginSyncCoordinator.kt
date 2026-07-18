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
import kotlinx.coroutines.launch
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
    private val profileRepository: com.destinyai.astrology.data.repository.ProfileRepository,
    private val homeRepository: HomeRepository,
    private val quotaManager: QuotaManager,
    private val chatThreadDao: ChatThreadDao,
    private val chatMessageDao: com.destinyai.astrology.data.local.db.ChatMessageDao,
    private val partnerDao: PartnerDao,
    // iOS parity (LoginSyncCoordinator runs compat sync in parallel with chat): rebuild
    // local compat history from the server so matches survive reinstall / follow to a new device.
    private val compatibilityHistoryDao: com.destinyai.astrology.data.local.db.CompatibilityHistoryDao,
    private val prefs: UserPreferences,
) {
    // Application-lifetime scope for detached background prefetch that must survive after
    // syncAll() returns (so login navigation isn't blocked on slow LLM prefetches).
    private val bgScope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO,
    )

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
                // iOS parity (DataManager.deleteAllThreads cascades messages): delete the
                // guest's chat_messages BEFORE its threads. chat_messages has no FK cascade
                // and ChatMessageDao.deleteAllForUser resolves thread_id via a subquery over
                // chat_threads — so once the threads are gone the messages can NEVER be
                // cleaned up and would resurface if a thread re-migrates under the same id (D6).
                chatMessageDao.deleteAllForUser(previousGuestEmail)
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
            // iOS parity (M4: guest→registered upgrade creates the self partner under the
            // new email so Switch Profile works). A guest-upgrade carries birth data
            // locally but skips the server-profile restore path that normally bootstraps
            // the self partner — do it here. Idempotent: no-ops if a self partner exists.
            runCatching {
                val birth = prefs.getBirthProfile()
                if (birth != null) {
                    profileRepository.createSelfPartnerProfile(
                        email = userEmail,
                        userName = prefs.getUserName() ?: "Me",
                        birthProfile = birth,
                    )
                }
            }.onFailure { Log.w(TAG, "self-partner bootstrap failed: ${it.message}", it) }
        }
        // iOS parity (ProfileSetupLoadingView phases 1+3 — chart + today's prediction
        // prefetch). These are warm-cache optimizations only (Home re-fetches them) and
        // today's-prediction is a 15s+ LLM call, so they run DETACHED on bgScope and never
        // gate login navigation — otherwise the user is stranded on the sign-in spinner.
        bgScope.launch {
            val birth = prefs.getBirthProfile() ?: return@launch
            runCatching { homeRepository.getRichHomeData(userEmail, birth, userEmail) }
                .onFailure { Log.w(TAG, "chart prefetch failed: ${it.message}", it) }
        }
        bgScope.launch {
            val birth = prefs.getBirthProfile() ?: return@launch
            runCatching { homeRepository.getDailyInsight(birth, userEmail) }
                .onFailure { Log.w(TAG, "prediction prefetch failed: ${it.message}", it) }
        }
        // iOS parity (CompatibilityHistoryService.syncFromServer): rebuild compat matches.
        // Also detached — not needed for first render.
        bgScope.launch {
            runCatching { syncCompatibilityFromApi(userEmail) }
                .onFailure { Log.w(TAG, "compat history sync failed: ${it.message}", it) }
        }
        // iOS parity (HistorySettingsManager.fetchSettingsFromServer): history_enabled is
        // fast + gates whether we save chats, so await it with the essentials.
        val historySettingsSync = async {
            runCatching {
                val settings = api.getChatHistorySettings(userEmail)
                prefs.setHistoryEnabled(settings.historyEnabled)
            }.onFailure { Log.w(TAG, "history settings sync failed: ${it.message}", it) }
        }

        // Await only the FAST, essential syncs before returning so the user lands on Home
        // promptly (profile + quota + history-settings gate correct first render).
        chatSync.await()
        quotaSync.await()
        profileFetch.await()
        historySettingsSync.await()
        Log.d(TAG, "syncAll essential sync complete for $userEmail")
    }

    /**
     * iOS parity (CompatibilityHistoryService.syncFromServer): list the user's chat
     * threads, keep the compatibility ones (compat_ id prefix or primary_area), fetch
     * each thread's raw detail (which carries the `metadata` analysis blob), map it via
     * the shared compat mapper, and upsert into CompatibilityHistoryDao. Every upsert is
     * gated on a successfully-parsed non-zero result so a shape mismatch can NEVER create
     * a broken, un-openable match row.
     */
    private suspend fun syncCompatibilityFromApi(email: String) {
        val threads = runCatching { api.listChatThreads(email) }.getOrElse { return }
        val gson = com.google.gson.Gson()
        threads.forEach { dto ->
            val id = dto.threadId.lowercase()
            val looksCompat = id.startsWith("compat_sess_") || id.startsWith("compat_grp_") ||
                id.startsWith("compat_") || dto.title.startsWith("match:", ignoreCase = true)
            if (!looksCompat) return@forEach
            runCatching {
                val body = api.getChatThreadRaw(email, dto.threadId).string()
                val root = com.google.gson.JsonParser.parseString(body).asJsonObject
                val metadata = root.get("metadata")?.takeIf { it.isJsonObject }?.asJsonObject
                    ?: return@runCatching
                val result = com.destinyai.astrology.data.remote.mapCompatibilityResponse(
                    json = gson.toJson(metadata),
                    boyName = "", girlName = "", boyDob = null, girlDob = null,
                    boyCity = null, girlCity = null,
                )
                // Only persist a valid, openable match — never a zero/empty stub.
                if (result.totalScore <= 0) return@runCatching
                // iOS parity (CompatibilityHistoryService.syncFromServer:688-708 reads
                // metadata.comparisonGroupId + metadata.partnerIndex): carry group metadata
                // so a multi-partner comparison rebuilds AS A GROUP after login sync instead
                // of N ungrouped single rows (F3). Backend stores these in the result blob
                // (compatibility_agent/agent.py:737-738).
                val groupId = metadata.get("comparison_group_id")
                    ?.takeIf { !it.isJsonNull }?.asString
                val partnerIdx = metadata.get("partner_index")
                    ?.takeIf { !it.isJsonNull }?.let { runCatching { it.asInt }.getOrNull() }
                compatibilityHistoryDao.upsert(
                    com.destinyai.astrology.data.local.db.CompatibilityHistoryEntity(
                        sessionId = dto.threadId,
                        ownerEmail = email,
                        timestampMs = runCatching { java.time.Instant.parse(dto.updatedAt).toEpochMilli() }.getOrDefault(0L),
                        boyName = result.boyName,
                        boyDob = result.boyDob ?: "",
                        boyCity = result.boyCity ?: "",
                        boyTime = "",
                        girlName = result.girlName,
                        girlDob = result.girlDob ?: "",
                        girlCity = result.girlCity ?: "",
                        girlTime = "",
                        totalScore = result.totalScore,
                        maxScore = result.maxScore,
                        isPinned = dto.isPinned,
                        comparisonGroupId = groupId,
                        partnerIndex = partnerIdx,
                        resultJson = gson.toJson(result),
                    ),
                )
            }
        }
    }

    private companion object {
        const val TAG = "LoginSyncCoordinator"
    }
}

