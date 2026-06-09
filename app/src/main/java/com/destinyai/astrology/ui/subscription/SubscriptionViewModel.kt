package com.destinyai.astrology.ui.subscription

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.billingclient.api.ProductDetails
import com.destinyai.astrology.data.billing.BillingManager
import com.destinyai.astrology.data.billing.RestoreResult
import com.destinyai.astrology.data.billing.SubscriptionConflict
import com.destinyai.astrology.data.local.prefs.UserPreferences
import com.destinyai.astrology.data.remote.AstroApiService
import com.destinyai.astrology.data.remote.PlanDto
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SubscriptionUiState(
    val plans: List<PlanDto> = emptyList(),
    val currentPlanId: String = "",
    val isPremium: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
)

/**
 * iOS parity (SubscriptionView.swift:308-340 + SubscriptionManager.swift:166-175).
 *
 * Backend `/subscription/plans` returns ONE PlanDto per tier ("core", "plus") with
 * both `priceMonthly` and `priceYearly`. iOS, however, drives the paywall from
 * StoreKit products — there are 4 products (com.daa.<tier>.<period>) and each
 * card maps to exactly one product. Without splitting here, Android's
 * `plan.planId.contains("yearly")` check is always false and yearly cards never
 * render the "Best Value" / yearly border / yearly-trial copy.
 *
 * [DisplayPlan] explodes each paid PlanDto into 2 variants — one monthly, one
 * yearly — and carries the original [source] so entitlement lookups still
 * resolve against the backend payload.
 */
data class DisplayPlan(
    val source: PlanDto,
    val tier: String, // "core" | "plus"
    val period: String, // "monthly" | "yearly"
) {
    val isYearly: Boolean get() = period == "yearly"
    val isPlus: Boolean get() = tier == "plus"
    val price: Double get() = if (isYearly) source.priceYearly else source.priceMonthly
    val displayName: String get() = source.displayName
    val dailyQuota: Int get() = source.dailyQuota
    val entitlements get() = source.entitlements
    /** Composite id used purely for matching to ProductDetails / current plan id. */
    val variantId: String get() = "${tier}_${period}"
}

@HiltViewModel
class SubscriptionViewModel @Inject constructor(
    private val api: AstroApiService,
    private val prefs: UserPreferences,
    private val billingManager: BillingManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SubscriptionUiState())

    /** iOS parity (QuotaManager.swift:368-372): server-returned entitlement
     *  feature ids, hydrated from cache on init so UI gating renders instantly
     *  on cold start before /subscription/status returns. */
    private val _cachedAvailableFeatures = MutableStateFlow<List<String>>(emptyList())
    val cachedAvailableFeatures: StateFlow<List<String>> = _cachedAvailableFeatures.asStateFlow()

    private val gson = Gson()

    init {
        // iOS parity (QuotaManager.swift:347-373) — hydrate plan + feature lists
        // synchronously from DataStore on cold start so the Subscription screen
        // renders cards immediately, before the /subscription/plans network
        // call completes (or forever if offline).
        viewModelScope.launch {
            val plansJson = prefs.getCachedAvailablePlansJson()
            if (!plansJson.isNullOrBlank()) {
                runCatching {
                    val type = object : TypeToken<List<PlanDto>>() {}.type
                    gson.fromJson<List<PlanDto>>(plansJson, type)
                }.getOrNull()?.let { cachedPlans ->
                    if (cachedPlans.isNotEmpty()) {
                        _uiState.update { it.copy(plans = cachedPlans) }
                    }
                }
            }
            val featuresJson = prefs.getCachedAvailableFeaturesJson()
            if (!featuresJson.isNullOrBlank()) {
                runCatching {
                    val type = object : TypeToken<List<String>>() {}.type
                    gson.fromJson<List<String>>(featuresJson, type)
                }.getOrNull()?.let { features ->
                    _cachedAvailableFeatures.value = features
                }
            }
        }
    }

    // Merge BillingManager loading/error into uiState so the screen has a single source
    val uiState: StateFlow<SubscriptionUiState> = combine(
        _uiState,
        billingManager.isLoading,
        billingManager.errorMessage,
    ) { state, billingLoading, billingError ->
        state.copy(
            isLoading = state.isLoading || billingLoading,
            error = billingError ?: state.error,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SubscriptionUiState(),
    )

    /** True when at least one Play product is active. */
    val hasActiveSubscription: StateFlow<Boolean> =
        billingManager.purchasedProductIds
            .map { it.isNotEmpty() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** The current active product ID (first in set), or null. */
    val activePlanId: StateFlow<String?> =
        billingManager.purchasedProductIds
            .map { it.firstOrNull() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Passthrough from BillingManager. */
    val subscriptionConflict: StateFlow<SubscriptionConflict?> =
        billingManager.subscriptionConflict

    /** iOS parity (SubscriptionView.swift:308-340) — render ONE card per plan
     *  tied to monthlyProduct(for:planId), matching iOS's one-card-per-PlanInfo
     *  model. Free plan is filtered out here so the screen does not need to
     *  filter again.
     *
     *  GAP FIX (Issues 2, 4): Android previously exploded each PlanDto into 2
     *  variants (monthly + yearly) producing 4 cards versus iOS's 2-card paywall.
     *  Aligned to iOS canonical model: one DisplayPlan per PlanDto, period
     *  pinned to "monthly" so price + product lookup map to monthlyProduct. */
    val displayPlans: StateFlow<List<DisplayPlan>> =
        _uiState
            .map { state ->
                state.plans
                    .filter { !it.isFree }
                    // iOS parity (SubscriptionView.swift:139, 151) — paid plans
                    // are sorted by priceMonthly ascending so Core renders
                    // before Plus regardless of backend ordering.
                    .sortedBy { it.priceMonthly }
                    .map { plan ->
                        val tier = when {
                            plan.planId.contains("plus", ignoreCase = true) -> "plus"
                            else -> "core"
                        }
                        DisplayPlan(source = plan, tier = tier, period = "monthly")
                    }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** iOS parity (SubscriptionView.swift:289-306) — when the backend
     *  currentPlanId is a free placeholder ("free_guest" / "free_registered" /
     *  blank), prefer the StoreKit/Play activePlanId so the user does not see
     *  "Choose Plus" on a plan they already own. Closes the offer-code-redeemed
     *  UX hole during the cross-window between Apple/Play webhook landing and
     *  the next backend `/subscription/status` sync. */
    val effectiveCurrentPlanId: StateFlow<String?> = combine(
        _uiState,
        activePlanId,
        subscriptionConflict,
    ) { state, applePlanId, conflict ->
        val dbPlan = state.currentPlanId
        val dbIsFree = dbPlan.isBlank() ||
            dbPlan.equals("free_guest", ignoreCase = true) ||
            dbPlan.equals("free_registered", ignoreCase = true)
        if (dbIsFree && conflict == null && !applePlanId.isNullOrBlank()) {
            applePlanId
        } else {
            dbPlan
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** iOS parity (SubscriptionView.swift:243-276 BUG-2 fix) — persists across
     *  popup dismissal until logout, so the inline conflict banner does not
     *  vanish when the user taps OK on a conflict alert. */
    val conflictDetectedThisSession: StateFlow<Boolean> =
        billingManager.conflictDetectedThisSession

    /** Passthrough — discrete result of the most recent Restore action. */
    val restoreResult: StateFlow<RestoreResult?> = billingManager.restoreResult

    fun consumeRestoreResult() = billingManager.clearRestoreResult()

    /** iOS parity (SubscriptionView.swift:113-117 .alert OK action) — promote
     *  state.error to a snackbar then call this so the error does not linger
     *  inside the LazyColumn forever. Clears both the local UI state error and
     *  the BillingManager's errorMessage source. */
    fun consumeError() {
        _uiState.update { it.copy(error = null) }
        billingManager.clearError()
    }

    fun clearConflict() = billingManager.clearConflict()

    /** Mirrors iOS SubscriptionManager.resetForAccountSwitch — must be called
     *  on logout from the auth flow. */
    fun resetForAccountSwitch() = billingManager.resetForAccountSwitch()

    /** Passthrough loading from BillingManager for UI spinner. */
    val isLoading: StateFlow<Boolean> = billingManager.isLoading

    /** True when a Plus free trial offer is available. */
    val isPlusTrialEligible: StateFlow<Boolean> = billingManager.isPlusTrialEligible

    /** Mirrors iOS SubscriptionManager.shouldShowTrialButton (319-330) — only
     *  show the Trial CTA when eligible AND no active sub AND no conflict. */
    val shouldShowTrialButton: StateFlow<Boolean> = billingManager.shouldShowTrialButton

    /** Emits the new plan id when an external activation is detected
     *  (Play promo redemption / family-share / website purchase). One-shot. */
    val externalPlanChangeAlert: StateFlow<String?> = billingManager.externalPlanChangeAlert

    fun consumeExternalPlanChangeAlert() = billingManager.clearExternalPlanChangeAlert()

    /** Play Billing ProductDetails (passthrough from BillingManager). */
    val products: StateFlow<List<ProductDetails>> = billingManager.products

    /** iOS parity (SubscriptionView.swift:11-12, 312, 692-696) — track which
     *  specific product is being purchased so only that card shows a spinner.
     *  Using a generic isLoading flag would put the spinner on every plan
     *  button while ANY purchase is in flight. */
    private val _purchasingProductId = MutableStateFlow<String?>(null)
    val purchasingProductId: StateFlow<String?> = _purchasingProductId.asStateFlow()

    /** iOS parity (SubscriptionView.swift:493-500 dismiss after purchase) —
     *  emits true after BillingManager confirms entitlement so the screen can
     *  call onBack() like iOS calls dismiss(). */
    private val _purchaseSuccess = MutableStateFlow(false)
    val purchaseSuccess: StateFlow<Boolean> = _purchaseSuccess.asStateFlow()

    fun consumePurchaseSuccess() {
        _purchaseSuccess.value = false
    }

    /** iOS parity (SubscriptionView.swift:113-117 alert) — emits a one-shot
     *  message when purchase fails (product not available, BillingManager
     *  reports a failure, etc.). Cleared after consumption. */
    private val _purchaseError = MutableStateFlow<String?>(null)
    val purchaseError: StateFlow<String?> = _purchaseError.asStateFlow()

    fun consumePurchaseError() {
        _purchaseError.value = null
    }

    /** iOS parity (SubscriptionView.swift:17, 391-428 isRestoring) — drives
     *  the in-flight spinner on the Restore button. */
    private val _isRestoring = MutableStateFlow(false)
    val isRestoring: StateFlow<Boolean> = _isRestoring.asStateFlow()

    /** iOS parity (SubscriptionView.swift:621-635, 720-725 — pendingUpgradeProductId
     *  + pendingUpgradeEffectiveDate). UI renders a "Scheduled" badge + effective
     *  date when the user has scheduled a Core→Plus auto-renew change. */
    val pendingUpgradeProductId: StateFlow<String?> = billingManager.pendingUpgradeProductId
    val pendingUpgradeEffectiveDate: StateFlow<Long?> = billingManager.pendingUpgradeEffectiveDate

    /** iOS parity (SubscriptionView.swift:118-130 .task block) — re-query Play
     *  products when missing and trigger reconcile when StoreKit reports an
     *  active sub but backend says free, breaking stale-state deadlock without
     *  requiring app restart. */
    fun onScreenOpen() {
        viewModelScope.launch {
            loadPlans()
            loadCurrentPlan()
            if (billingManager.products.value.isEmpty()) {
                billingManager.queryProductDetails()
            }
            if (hasActiveSubscription.value && !uiState.value.isPremium) {
                billingManager.reconcileEntitlements()
                loadCurrentPlan()
            }
        }
    }

    fun loadPlans() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val plans = api.getPlans()
                _uiState.update { it.copy(plans = plans, isLoading = false) }
                // iOS parity (QuotaManager.swift:376-381): persist for instant
                // paywall rendering on next cold start.
                prefs.setCachedAvailablePlansJson(gson.toJson(plans))
                // Derive a flat feature-id list from entitlements so the cached
                // gating data parallels iOS QuotaManager.availableFeatures.
                val features = plans
                    .flatMap { it.entitlements.orEmpty() }
                    .map { it.featureId }
                    .distinct()
                if (features.isNotEmpty()) {
                    _cachedAvailableFeatures.value = features
                    prefs.setCachedAvailableFeaturesJson(gson.toJson(features))
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "Failed to load plans") }
            }
        }
    }

    /** iOS parity (SubscriptionView.swift:183-185 + QuotaManager.syncStatus(force:)).
     *  When [force] is true, bypasses any 60s status cache so a manual
     *  user-initiated refresh sees fresh server state immediately. */
    fun loadCurrentPlan(force: Boolean = false) {
        viewModelScope.launch {
            val email = prefs.getUserEmail() ?: return@launch
            try {
                val status = api.getStatus(email, if (force) true else null)
                _uiState.update {
                    it.copy(
                        currentPlanId = status.planId ?: "",
                        isPremium = status.isPremium,
                    )
                }
            } catch (_: Exception) {}
        }
    }

    /** Launch Google Play billing flow for the given product. iOS parity:
     *  tracks per-card spinner via [purchasingProductId], surfaces errors via
     *  [purchaseError], and emits [purchaseSuccess] once entitlement is
     *  confirmed so the screen can dismiss like SubscriptionView does. */
    fun purchase(productDetails: ProductDetails?, activity: Activity, offerToken: String? = null) {
        // iOS parity (SubscriptionView.swift:478-482) — null product → fallback
        // "product_not_available_error" alert message.
        if (productDetails == null) {
            _purchaseError.value = "product_not_available_error"
            return
        }
        val productId = productDetails.productId
        _purchasingProductId.value = productId
        billingManager.launchBillingFlow(activity, productDetails, offerToken)
        viewModelScope.launch {
            // Wait until BillingManager finishes processing (isLoading flips
            // back to false). Then check whether the productId landed in
            // purchasedProductIds (success) or errorMessage was set (failure).
            try {
                // Skip the initial false → true transition.
                billingManager.isLoading.first { it }
                billingManager.isLoading.first { !it }
            } catch (_: Exception) {}
            val purchased = billingManager.purchasedProductIds.value.contains(productId)
            val err = billingManager.errorMessage.value
            if (purchased) {
                _purchaseSuccess.value = true
            } else if (!err.isNullOrBlank()) {
                _purchaseError.value = err
            }
            _purchasingProductId.value = null
        }
    }

    /** Re-query Play and re-verify every active subscription with backend.
     *  iOS parity (SubscriptionManager.swift:278-293, 671-672): after
     *  reconcile, force-refresh backend status so currentPlanId / isPremium
     *  reflect the just-restored entitlement. */
    fun restorePurchases() {
        viewModelScope.launch {
            _isRestoring.value = true
            try {
                billingManager.reconcileEntitlements()
                loadCurrentPlan()
            } finally {
                _isRestoring.value = false
            }
        }
    }

    /** iOS parity (SubscriptionView.swift:168-192 refreshStatus): force a
     *  reconcile + reload backend status. Wired to the toolbar refresh
     *  button and pull-to-refresh.
     *
     *  GAP FIX (Issues 3, 5): pass force=true to bypass the 60s status cache
     *  on user-initiated refresh, mirroring iOS QuotaManager.syncStatus(force:true). */
    fun refreshStatus() {
        viewModelScope.launch {
            billingManager.reconcileEntitlements()
            loadCurrentPlan(force = true)
            loadPlans()
        }
    }
}
