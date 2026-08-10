package com.destinyai.astrology.data.repository

import com.destinyai.astrology.data.remote.BirthProfileDto
import com.destinyai.astrology.domain.model.User
import com.destinyai.astrology.ui.charts.DashaResponse
import com.destinyai.astrology.ui.home.HomeRichData

interface HomeRepository {
    suspend fun getCurrentUser(): User?
    suspend fun getDailyQuota(): Int
    suspend fun getDailyUsed(): Int
    suspend fun getSuggestedQuestions(): List<String>
    /**
     * @param birth The birth profile to query the prediction API with.
     * @param profileCacheId Stable identifier for cache keying. Use the active
     *   profile id (partner UUID, or owner email when self is active). Mirrors
     *   iOS profileScopedKey at ProfileContextManager.swift:147-149 — without
     *   this scope, switching profiles would hit a stale self-cached row.
     * @param force When true, skips the same-day disk-cache read and re-fetches
     *   from the API (still writes the fresh result back to cache). Mirrors iOS
     *   HomeViewModel.fetchTodaysPrediction(force:) / shouldBypassCache logic
     *   (HomeViewModel.swift:181,306-319): used on language change so localized
     *   prediction text, suggested_questions, life-area briefs, and dasha_insight
     *   refresh in the new language without waiting for a day rollover.
     */
    suspend fun getDailyInsight(
        birth: BirthProfileDto,
        profileCacheId: String,
        force: Boolean = false,
    ): String
    suspend fun getRichHomeData(
        email: String,
        birthProfile: BirthProfileDto,
        profileCacheId: String,
    ): HomeRichData?

    /**
     * Fetches the dedicated Vimshottari dasha periods for the given year via
     * POST /vedic/api/astrodata/dasha. Mirrors iOS HomeViewModel.fetchDashaPeriods —
     * called in parallel with the todays-prediction + full-chart fetches so the
     * Home Dasha card renders authoritative period boundaries (start/end ISO),
     * not just the maha/antar lord summary surfaced by the todays-prediction
     * endpoint. Returns null on any failure (non-fatal — callers fall back to
     * the cached todays-prediction current_dasha summary).
     */
    suspend fun getDashaPeriods(birthProfile: BirthProfileDto): DashaResponse?
}
