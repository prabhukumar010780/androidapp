package com.destinyai.astrology.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.destinyai.astrology.data.billing.BillingManager
import com.destinyai.astrology.data.billing.SubscriptionConflict
import com.destinyai.astrology.data.local.db.CompatibilityHistoryDao
import com.destinyai.astrology.data.local.db.CompatibilityHistoryEntity
import com.destinyai.astrology.data.local.prefs.UserPreferences
import com.destinyai.astrology.domain.model.CompatibilityHistoryItem
import com.destinyai.astrology.domain.model.CompatibilityResult
import com.destinyai.astrology.domain.model.ComparisonGroup
import com.destinyai.astrology.services.ExternalPlanChange
import com.destinyai.astrology.services.QuotaManager
import com.google.gson.Gson
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Mirrors iOS MainTabView state hoists — exposes guest status, deep-link
 * router, and root-level alert flows (external plan change, subscription
 * conflict) for MainScreen to render.
 */
@HiltViewModel
class MainScreenViewModel @Inject constructor(
    userPreferences: UserPreferences,
    private val quotaManager: QuotaManager,
    private val billingManager: BillingManager,
    private val historyDao: CompatibilityHistoryDao,
) : ViewModel() {

    private val gson = Gson()

    val isGuestUser: StateFlow<Boolean> = userPreferences.isGuestUserFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /**
     * Mirrors iOS ProfileContextManager.shared.activeProfileId. MainScreen uses
     * this to invalidate the Match tab when the user switches profiles.
     */
    val activeProfileId: StateFlow<String?> = userPreferences.activeProfileIdFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val externalPlanChangeAlert: StateFlow<ExternalPlanChange?> = quotaManager.externalPlanChangeAlert

    val subscriptionConflict: StateFlow<SubscriptionConflict?> = billingManager.subscriptionConflict

    fun clearExternalPlanChangeAlert() {
        quotaManager.clearExternalPlanChangeAlert()
    }

    fun clearSubscriptionConflict() {
        billingManager.clearConflict()
    }

    /**
     * Look up a saved match by sessionId. Used by MainScreen when the user taps
     * a match-history row in HistoryScreen — we hand the resolved
     * CompatibilityHistoryItem to the Match tab via pendingMatchItem (parity
     * with iOS HomeView.onMatchHistorySelected).
     *
     * Synchronous wrapper because callbacks from Compose UI cannot suspend.
     * The DAO query is fast (PK lookup), so blocking briefly is acceptable.
     */
    fun findMatchHistoryItem(sessionId: String): CompatibilityHistoryItem? = runBlocking {
        withContext(Dispatchers.IO) {
            historyDao.getById(sessionId)?.toDomain()
        }
    }

    /**
     * Look up a multi-partner comparison group by id. Mirrors iOS
     * HomeView.onMatchGroupHistorySelected.
     */
    fun findMatchHistoryGroup(groupId: String): ComparisonGroup? = runBlocking {
        withContext(Dispatchers.IO) {
            val items = historyDao.getByGroupId(groupId).map { it.toDomain() }
            if (items.isEmpty()) null
            else ComparisonGroup(
                id = groupId,
                timestamp = items.first().timestampMs,
                userName = items.first().boyName,
                items = items,
            )
        }
    }

    private fun CompatibilityHistoryEntity.toDomain() = CompatibilityHistoryItem(
        sessionId = sessionId,
        timestampMs = timestampMs,
        boyName = boyName,
        boyDob = boyDob,
        boyCity = boyCity,
        boyTime = boyTime,
        girlName = girlName,
        girlDob = girlDob,
        girlCity = girlCity,
        girlTime = girlTime,
        totalScore = totalScore,
        maxScore = maxScore,
        isPinned = isPinned,
        comparisonGroupId = comparisonGroupId,
        partnerIndex = partnerIndex,
        result = resultJson.takeIf { it.isNotEmpty() }?.let {
            runCatching { gson.fromJson(it, CompatibilityResult::class.java) }.getOrNull()
        },
    )
}
