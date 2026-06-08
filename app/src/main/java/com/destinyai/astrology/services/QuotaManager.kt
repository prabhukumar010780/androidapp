package com.destinyai.astrology.services

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.destinyai.astrology.BuildConfig
import com.destinyai.astrology.data.billing.BillingManager
import com.destinyai.astrology.data.remote.AstroApiService
import com.destinyai.astrology.data.remote.FeatureAccessResponse
import com.destinyai.astrology.data.remote.PlanDto
import com.destinyai.astrology.data.remote.RegisterRequest
import com.destinyai.astrology.data.remote.UseFeatureRequest
import com.destinyai.astrology.data.remote.UseFeatureResponse
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.Lazy
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Mirrors iOS ExternalPlanChange — emitted when the backend reports a plan
 * change that originated outside the app (e.g. auto-renew, server-side upgrade).
 */
data class ExternalPlanChange(
    val previousPlanId: String?,
    val newPlanId: String,
    val newPlanDisplayName: String,
    val expiresAt: String?,
    val willAutoRenew: Boolean?,
)

/**
 * Android counterpart to iOS QuotaManager.swift.
 *
 * Manages user quota and subscription status — feature access checks, usage recording,
 * cached subscription state, and plan fetching. Mirrors iOS behavior exactly so daily
 * AI question limits, free-plan caps, and per-feature quotas are enforceable on Android.
 *
 * Endpoints used:
 *  - GET  /subscription/can-access   → canAccessFeature(), canAsk()
 *  - POST /subscription/use          → recordFeatureUsage()
 *  - GET  /subscription/status       → syncStatus()
 *  - POST /subscription/register     → registerUser()
 *  - GET  /subscription/plans        → fetchPlans()
 */
@Singleton
class QuotaManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: AstroApiService,
    // SUBSCRIPTION-GAP-4: read directPurchaseInProgress to suppress the
    // externalPlanChangeAlert when the plan flip originated from the in-app
    // purchase flow (mirrors iOS QuotaManager.swift:617-639). Wrapped in Lazy
    // so QuotaManager <-> BillingManager don't form an eager dependency cycle
    // at Hilt graph construction.
    private val billingManager: Lazy<BillingManager>,
) {
    /** Feature IDs matching backend (mirrors iOS FeatureID enum). */
    enum class FeatureID(val raw: String) {
        AI_QUESTIONS("ai_questions"),
        COMPATIBILITY("compatibility"),
        HISTORY("history"),
        PROFILES("multiple_profile_match"),
        SWITCH_PROFILE("switch_profile"),
        MAINTAIN_PROFILE("maintain_profile"),
        ALERTS("alerts"),
        EARLY_ACCESS("early_access"),
        ;

        companion object {
            fun fromRaw(raw: String?): FeatureID? = values().firstOrNull { it.raw == raw }
        }
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences("quota_manager_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    // ── State (StateFlow mirror of iOS @Published properties) ─────────────────

    private val _isPremium = MutableStateFlow(prefs.getBoolean(KEY_IS_PREMIUM, false))
    val isPremium: StateFlow<Boolean> = _isPremium.asStateFlow()

    private val _availableFeatures = MutableStateFlow(loadCachedFeatures())
    val availableFeatures: StateFlow<List<String>> = _availableFeatures.asStateFlow()

    private val _availablePlans = MutableStateFlow(loadCachedPlans())
    val availablePlans: StateFlow<List<PlanDto>> = _availablePlans.asStateFlow()

    private val _currentPlanId = MutableStateFlow(prefs.getString(KEY_CURRENT_PLAN_ID, null))
    val currentPlanId: StateFlow<String?> = _currentPlanId.asStateFlow()

    private val _subscriptionStatus = MutableStateFlow(prefs.getString(KEY_SUBSCRIPTION_STATUS, null))
    val subscriptionStatus: StateFlow<String?> = _subscriptionStatus.asStateFlow()

    private val _subscriptionExpiresAt = MutableStateFlow(prefs.getString(KEY_SUBSCRIPTION_EXPIRES_AT, null))
    val subscriptionExpiresAt: StateFlow<String?> = _subscriptionExpiresAt.asStateFlow()

    private val _autoRenewStatus = MutableStateFlow(
        if (prefs.contains(KEY_AUTO_RENEW)) prefs.getBoolean(KEY_AUTO_RENEW, false) else null,
    )
    val autoRenewStatus: StateFlow<Boolean?> = _autoRenewStatus.asStateFlow()

    /** iOS parity (HomeViewModel.swift:20, SubscriptionManager.swift:407):
     *  cached server-side display name (e.g. "Plus Yearly") shown by HomeViewModel
     *  before the first network sync completes. */
    private val _currentPlanDisplayName =
        MutableStateFlow(prefs.getString(KEY_CURRENT_PLAN_DISPLAY_NAME, null))
    val currentPlanDisplayName: StateFlow<String?> = _currentPlanDisplayName.asStateFlow()

    private val _totalQuestionsAsked = MutableStateFlow(prefs.getInt(KEY_TOTAL_QUESTIONS, 0))
    val totalQuestionsAsked: StateFlow<Int> = _totalQuestionsAsked.asStateFlow()

    /** Mirrors iOS QuotaManager.externalPlanChangeAlert — surfaces an alert when the
     *  backend reports the user's subscription was renewed/upgraded outside the app. */
    private val _externalPlanChangeAlert = MutableStateFlow<ExternalPlanChange?>(null)
    val externalPlanChangeAlert: StateFlow<ExternalPlanChange?> = _externalPlanChangeAlert.asStateFlow()

    fun clearExternalPlanChangeAlert() {
        _externalPlanChangeAlert.value = null
    }

    /** SUBSCRIPTION-GAP-4 (iOS QuotaManager.swift:617-639) — last plan id we
     *  observed inside syncStatus. A transition to a new paid plan while
     *  directPurchaseInProgress=false signals a webhook-driven external
     *  activation (offer-code redemption / family share / web purchase). */
    @Volatile
    private var previousObservedPlanId: String? = prefs.getString(KEY_CURRENT_PLAN_ID, null)

    /** Last syncStatus() time (epoch ms) — drives the 5-minute cooldown. */
    private var lastSyncTimeMs: Long = 0L
    private val syncCooldownMs: Long = 5 * 60 * 1000L

    private val authHeader: String get() = "Bearer ${BuildConfig.API_KEY}"

    // ── Feature Access ─────────────────────────────────────────────────────────

    /**
     * Authoritative feature access check (mirrors iOS canAccessFeature).
     * @param count Usages to check (for multi-partner pass partners.size).
     */
    suspend fun canAccessFeature(
        feature: FeatureID,
        email: String,
        count: Int = 1,
    ): FeatureAccessResponse {
        return api.canAccessFeatureFull(authHeader, email, feature.raw, count)
    }

    /**
     * Simple bool wrapper for ChatViewModel.send() gating (mirrors iOS canAsk).
     * Defaults to ai_questions feature. Returns false on network error.
     */
    suspend fun canAsk(email: String, feature: FeatureID = FeatureID.AI_QUESTIONS): Boolean {
        return try {
            canAccessFeature(feature, email).canAccess
        } catch (e: Exception) {
            Log.e(TAG, "canAsk error: ${e.message}")
            false
        }
    }

    /**
     * iOS parity (Services/QuotaManager.swift:782-816 canAddProfile) —
     * shared source-of-truth for the partner-add gate. Combines the
     * `MAINTAIN_PROFILE` feature access check with the local profile count
     * so PartnersViewModel does not have to re-derive limit math from the
     * raw FeatureAccessResponse. Returns Allowed (proceed) or Blocked
     * (showQuotaUpgradePrompt with the server-reported limit). Fails open
     * on network errors to match iOS catch behavior.
     */
    sealed class CanAddProfileResult {
        object Allowed : CanAddProfileResult()
        data class Blocked(val limit: Int) : CanAddProfileResult()
    }

    suspend fun canAddProfile(email: String, currentCount: Int): CanAddProfileResult {
        return try {
            val response = canAccessFeature(FeatureID.MAINTAIN_PROFILE, email)
            if (!response.canAccess) {
                return CanAddProfileResult.Blocked(limit = 0)
            }
            val limit = response.limits?.get("overall")?.limit ?: -1
            if (limit != -1 && currentCount >= limit) {
                CanAddProfileResult.Blocked(limit = limit)
            } else {
                CanAddProfileResult.Allowed
            }
        } catch (e: Exception) {
            // Fail-open parity with iOS canAddProfile catch (Services/QuotaManager.swift:813-816).
            Log.w(TAG, "canAddProfile error, allowing: ${e.message}")
            CanAddProfileResult.Allowed
        }
    }

    /** Cached sync check using last-seen feature list (mirrors iOS `var canAsk: Bool`). */
    fun hasFeature(feature: FeatureID): Boolean =
        _availableFeatures.value.contains(feature.raw)

    val canAskCached: Boolean get() = hasFeature(FeatureID.AI_QUESTIONS)

    val isFreePlan: Boolean
        get() = _currentPlanId.value.let { it == "free_guest" || it == "free_registered" || it == null }

    val isPlus: Boolean get() = _currentPlanId.value == "plus"

    // ── Record Usage ───────────────────────────────────────────────────────────

    /**
     * Record successful feature usage (mirrors iOS recordFeatureUsage).
     * Call AFTER the gated action succeeds, not before.
     */
    suspend fun recordFeatureUsage(feature: FeatureID, email: String): UseFeatureResponse {
        val response = api.useFeature(
            authHeader,
            UseFeatureRequest(email = email, featureId = feature.raw),
        )
        if (response.success) {
            val newTotal = _totalQuestionsAsked.value + 1
            _totalQuestionsAsked.value = newTotal
            prefs.edit().putInt(KEY_TOTAL_QUESTIONS, newTotal).apply()
        }
        return response
    }

    // ── Plans ──────────────────────────────────────────────────────────────────

    /** Fetch available subscription plans for paywall (mirrors iOS fetchPlans). */
    suspend fun fetchPlans(): List<PlanDto> {
        val plans = api.getPlans()
        _availablePlans.value = plans
        cachePlans(plans)
        return plans
    }

    /** Paid plans only (for paywall). */
    val paidPlans: List<PlanDto> get() = _availablePlans.value.filterNot { it.isFree }

    // ── Registration & Sync ────────────────────────────────────────────────────

    /**
     * Register user with backend (mirrors iOS registerUser).
     * The full status payload returned by /subscription/register is consumed by
     * AuthRepository today; QuotaManager simply delegates so callers can pick whichever
     * code path they already use, then forwards the result through updateFromStatus().
     *
     * iOS parity (QuotaManager.swift:489-557):
     *   - 409 detail.error == "archived_guest"          → ArchivedGuestError(upgraded_to_email, provider)
     *   - 409 detail.error == "registered_user_conflict" → RegisteredUserConflictError(masked_email, provider)
     *   - 403 detail.error == "account_deleted"          → AccountDeletedError(message)
     */
    suspend fun registerUser(email: String, isGeneratedEmail: Boolean) {
        val response = try {
            api.register(
                RegisterRequest(email = email, isGeneratedEmail = isGeneratedEmail),
            )
        } catch (e: retrofit2.HttpException) {
            val raw = e.response()?.errorBody()?.string().orEmpty()
            when (e.code()) {
                409 -> {
                    val errorKind = parseDetailField(raw, "error")
                    val provider = parseDetailField(raw, "provider")
                    when (errorKind) {
                        "archived_guest" -> throw com.destinyai.astrology.ui.auth.ArchivedGuestError(
                            upgradedToEmail = parseDetailField(raw, "upgraded_to_email"),
                            provider = provider,
                            msg = parseDetailField(raw, "message") ?: "archived_guest",
                        )
                        "registered_user_conflict" -> throw com.destinyai.astrology.ui.auth.RegisteredUserConflictError(
                            maskedEmail = parseDetailField(raw, "masked_email"),
                            provider = provider,
                        )
                        else -> throw e
                    }
                }
                403 -> {
                    if (parseDetailField(raw, "error") == "account_deleted") {
                        throw com.destinyai.astrology.ui.auth.AccountDeletedError(
                            serverMessage = parseDetailField(raw, "message"),
                        )
                    }
                    throw e
                }
                else -> throw e
            }
        }
        // RegisterResponse is leaner than iOS SubscriptionStatus — update what we have.
        _isPremium.value = response.isPremium
        _currentPlanId.value = response.planId
        prefs.edit()
            .putBoolean(KEY_IS_PREMIUM, response.isPremium)
            .putString(KEY_CURRENT_PLAN_ID, response.planId)
            .apply()
        lastSyncTimeMs = System.currentTimeMillis()
    }

    private fun parseDetailField(raw: String, field: String): String? = runCatching {
        if (raw.isBlank()) return@runCatching null
        val root = com.google.gson.JsonParser.parseString(raw)
        if (!root.isJsonObject) return@runCatching null
        val detail = root.asJsonObject.get("detail") ?: return@runCatching null
        when {
            detail.isJsonObject -> detail.asJsonObject.get(field)
                ?.takeIf { it.isJsonPrimitive }?.asString
            detail.isJsonPrimitive && field == "error" -> detail.asString
            else -> null
        }
    }.getOrNull()

    /**
     * Sync status from /subscription/status (mirrors iOS syncStatus).
     * Short-circuits if the last sync was within syncCooldownMs unless force=true.
     *
     * iOS parity (QuotaManager.swift:362-372 + SubscriptionManager.swift:403-407):
     * persists subscription_status / subscription_expires_at / auto_renew_status
     * / plan_display_name so cold-start UI can render plan/expiry/auto-renew
     * badges before the next sync lands.
     */
    suspend fun syncStatus(email: String, force: Boolean = false) {
        if (!force && lastSyncTimeMs > 0) {
            val age = System.currentTimeMillis() - lastSyncTimeMs
            if (age < syncCooldownMs) {
                Log.d(TAG, "syncStatus skipped — last sync ${age / 1000}s ago")
                return
            }
        }
        val status = api.getStatus(email)

        // SUBSCRIPTION-GAP-4 (iOS QuotaManager.swift:617-639): detect
        // webhook-driven external plan flips. If the plan id transitions to a
        // new paid plan while no in-app purchase is mid-flight, surface a
        // one-shot ExternalPlanChange alert. Webhook sources covered:
        // offer-code redemption, family share, web purchase, server-initiated
        // upgrade. Suppressed when BillingManager is in the middle of a direct
        // purchase to avoid double-celebration.
        val previous = previousObservedPlanId
        val newPlanId = status.planId ?: ""
        val isPaidPlan = newPlanId.isNotBlank() &&
            !newPlanId.equals("free_guest", ignoreCase = true) &&
            !newPlanId.equals("free_registered", ignoreCase = true)
        val transitionedToPaid = previous != null && previous != newPlanId && isPaidPlan
        val directPurchase = runCatching { billingManager.get().isDirectPurchaseInProgress }
            .getOrDefault(false)
        if (transitionedToPaid && !directPurchase) {
            _externalPlanChangeAlert.value = ExternalPlanChange(
                previousPlanId = previous,
                newPlanId = newPlanId,
                newPlanDisplayName = status.planDisplayName ?: newPlanId,
                expiresAt = status.subscriptionExpiresAt,
                willAutoRenew = status.autoRenewStatus,
            )
        }
        previousObservedPlanId = newPlanId

        _isPremium.value = status.isPremium
        _currentPlanId.value = status.planId
        _subscriptionStatus.value = status.subscriptionStatus
        _subscriptionExpiresAt.value = status.subscriptionExpiresAt
        _autoRenewStatus.value = status.autoRenewStatus
        _currentPlanDisplayName.value = status.planDisplayName
        prefs.edit().apply {
            putBoolean(KEY_IS_PREMIUM, status.isPremium)
            putString(KEY_CURRENT_PLAN_ID, status.planId)
            if (status.subscriptionStatus != null) {
                putString(KEY_SUBSCRIPTION_STATUS, status.subscriptionStatus)
            } else {
                remove(KEY_SUBSCRIPTION_STATUS)
            }
            if (status.subscriptionExpiresAt != null) {
                putString(KEY_SUBSCRIPTION_EXPIRES_AT, status.subscriptionExpiresAt)
            } else {
                remove(KEY_SUBSCRIPTION_EXPIRES_AT)
            }
            if (status.autoRenewStatus != null) {
                putBoolean(KEY_AUTO_RENEW, status.autoRenewStatus)
            } else {
                remove(KEY_AUTO_RENEW)
            }
            if (status.planDisplayName != null) {
                putString(KEY_CURRENT_PLAN_DISPLAY_NAME, status.planDisplayName)
            } else {
                remove(KEY_CURRENT_PLAN_DISPLAY_NAME)
            }
        }.apply()
        lastSyncTimeMs = System.currentTimeMillis()
    }

    /**
     * Wipe all subscription state on sign-out so account A's Plus state can't bleed
     * into account B (mirrors iOS resetForSignOut).
     */
    fun resetForSignOut() {
        _isPremium.value = false
        _currentPlanId.value = null
        _subscriptionStatus.value = null
        _subscriptionExpiresAt.value = null
        _autoRenewStatus.value = null
        _currentPlanDisplayName.value = null
        _availableFeatures.value = emptyList()
        _totalQuestionsAsked.value = 0
        // SUBSCRIPTION-GAP-4: clear the observed plan tracker so the new
        // user's first syncStatus does not falsely fire externalPlanChangeAlert
        // against the previous user's plan id.
        previousObservedPlanId = null
        _externalPlanChangeAlert.value = null
        lastSyncTimeMs = 0L
        prefs.edit().clear().apply()
    }

    // ── Cache helpers ──────────────────────────────────────────────────────────

    private fun loadCachedFeatures(): List<String> {
        val raw = prefs.getString(KEY_CACHED_FEATURES, null) ?: return emptyList()
        return runCatching {
            gson.fromJson<List<String>>(raw, object : TypeToken<List<String>>() {}.type)
                ?: emptyList()
        }.getOrElse { emptyList() }
    }

    private fun loadCachedPlans(): List<PlanDto> {
        val raw = prefs.getString(KEY_CACHED_PLANS, null) ?: return emptyList()
        return runCatching {
            gson.fromJson<List<PlanDto>>(raw, object : TypeToken<List<PlanDto>>() {}.type)
                ?: emptyList()
        }.getOrElse { emptyList() }
    }

    private fun cachePlans(plans: List<PlanDto>) {
        prefs.edit().putString(KEY_CACHED_PLANS, gson.toJson(plans)).apply()
    }

    companion object {
        private const val TAG = "QuotaManager"
        private const val KEY_IS_PREMIUM = "isPremium"
        private const val KEY_CURRENT_PLAN_ID = "currentPlanId"
        private const val KEY_SUBSCRIPTION_STATUS = "subscriptionStatus"
        private const val KEY_SUBSCRIPTION_EXPIRES_AT = "subscriptionExpiresAt"
        private const val KEY_AUTO_RENEW = "autoRenewStatus"
        private const val KEY_CURRENT_PLAN_DISPLAY_NAME = "currentPlanDisplayName"
        private const val KEY_TOTAL_QUESTIONS = "totalQuestionsAsked"
        private const val KEY_CACHED_PLANS = "cachedAvailablePlans"
        private const val KEY_CACHED_FEATURES = "cachedAvailableFeatures"
    }
}
