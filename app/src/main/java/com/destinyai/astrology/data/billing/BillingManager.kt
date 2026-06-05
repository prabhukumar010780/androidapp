package com.destinyai.astrology.data.billing

import android.app.Activity
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.ProductDetailsResponseListener
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesResponseListener
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.destinyai.astrology.BuildConfig
import com.destinyai.astrology.data.local.prefs.UserPreferences
import com.destinyai.astrology.data.remote.AstroApiService
import com.destinyai.astrology.data.remote.VerifyRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

@Singleton
class BillingManager @Inject constructor(
    private val billingClient: BillingClient,
    private val api: AstroApiService,
    private val prefs: UserPreferences,
) {

    companion object {
        val PRODUCT_IDS = listOf(
            "com.daa.core.monthly",
            "com.daa.core.yearly",
            "com.daa.plus.monthly",
            "com.daa.plus.yearly",
        )
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _products = MutableStateFlow<List<ProductDetails>>(emptyList())
    val products: StateFlow<List<ProductDetails>> = _products.asStateFlow()

    private val _purchasedProductIds = MutableStateFlow<Set<String>>(emptySet())
    val purchasedProductIds: StateFlow<Set<String>> = _purchasedProductIds.asStateFlow()

    /** iOS parity (SubscriptionManager.swift:27-28, 501-555): pending upgrade
     *  product id (e.g. user on Core scheduled to switch to Plus at renewal).
     *  Sourced from backend VerifyResponse.pending_upgrade_product_id which the
     *  server derives from Play Developer API webhook events (or StoreKit
     *  renewalInfo.autoRenewPreference on iOS). */
    private val _pendingUpgradeProductId = MutableStateFlow<String?>(null)
    val pendingUpgradeProductId: StateFlow<String?> = _pendingUpgradeProductId.asStateFlow()

    private val _pendingUpgradeEffectiveDate = MutableStateFlow<Long?>(null)
    val pendingUpgradeEffectiveDate: StateFlow<Long?> = _pendingUpgradeEffectiveDate.asStateFlow()

    /** True when any Plus product has a trial offer available AND user does not already
     *  hold a Plus entitlement. Re-evaluates whenever products or entitlements change. */
    val isPlusTrialEligible: StateFlow<Boolean> = combine(
        _products,
        _purchasedProductIds,
    ) { products, purchasedIds ->
        val hasPlusOffer = products.any { product ->
            product.productId.contains("plus", ignoreCase = true) &&
                product.subscriptionOfferDetails?.any { offer ->
                    offer.offerId?.contains("trial", ignoreCase = true) == true
                } == true
        }
        val alreadyHasPlus = purchasedIds.any { it.contains("plus", ignoreCase = true) }
        hasPlusOffer && !alreadyHasPlus
    }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), false)

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _subscriptionConflict = MutableStateFlow<SubscriptionConflict?>(null)
    val subscriptionConflict: StateFlow<SubscriptionConflict?> = _subscriptionConflict.asStateFlow()

    /** Mirrors iOS conflictDetectedThisSession (SubscriptionManager.swift:660) —
     *  ensures the cross-account conflict alert fires at most once per app
     *  session even if multiple verify calls reject for the same reason.
     *  Exposed as a StateFlow so the UI can render a persistent inline banner
     *  (BUG-2 parity — banner must NOT vanish when the conflict popup is
     *  dismissed; only sign-out clears it). */
    private val _conflictDetectedThisSession = MutableStateFlow(false)
    val conflictDetectedThisSession: StateFlow<Boolean> =
        _conflictDetectedThisSession.asStateFlow()

    /** iOS parity (SubscriptionManager.swift:459-460): re-entry guard +
     *  5-second debounce prevent multiple call sites (sign-in + scenePhase
     *  active + view appear) from running concurrent reconciles. */
    private val isReconciling = AtomicBoolean(false)
    private var lastReconcileTime: Long = 0L

    /** iOS parity (SubscriptionManager.swift:71): per-transaction in-flight
     *  set keyed by purchaseToken. Prevents two concurrent /verify calls for
     *  the same purchase from firing duplicate conflict popups. */
    private val verifyInFlight = mutableSetOf<String>()
    private val verifyInFlightLock = Any()

    /** iOS parity (SubscriptionManager.swift:79-125): foreground sync timer.
     *  Ticks every 60s while the app is in foreground; reconcile is itself
     *  debounced (5s) so the actual network call happens at most once per
     *  foreground burst. Surfaces backend webhook-driven cancellations
     *  without requiring an app restart. */
    private var foregroundSyncJob: Job? = null
    private val foregroundTickIntervalMs = 60_000L
    private var lifecycleObserverRegistered = false

    private val lifecycleObserver = object : DefaultLifecycleObserver {
        override fun onStart(owner: LifecycleOwner) {
            startForegroundSyncTimer()
            // Immediate reconcile on resume (debounced internally).
            scope.launch { reconcileEntitlements() }
        }

        override fun onStop(owner: LifecycleOwner) {
            stopForegroundSyncTimer()
        }
    }

    /** Idempotent — safe to call multiple times. Wired from app init. */
    fun observeAppLifecycle() {
        if (lifecycleObserverRegistered) return
        lifecycleObserverRegistered = true
        runCatching {
            ProcessLifecycleOwner.get().lifecycle.addObserver(lifecycleObserver)
        }
    }

    private fun startForegroundSyncTimer() {
        foregroundSyncJob?.cancel()
        foregroundSyncJob = scope.launch {
            while (isActive) {
                delay(foregroundTickIntervalMs)
                if (!isActive) return@launch
                // reconcileEntitlements has its own 5s debounce — safe to fire often.
                runCatching { reconcileEntitlements() }
            }
        }
    }

    private fun stopForegroundSyncTimer() {
        foregroundSyncJob?.cancel()
        foregroundSyncJob = null
    }

    /** Discrete result of the most recent restorePurchases() call.
     *  UI observes this to surface success / empty / error feedback per Apple HIG
     *  + Play Subscription policy. Cleared after consumption via [clearRestoreResult]. */
    private val _restoreResult = MutableStateFlow<RestoreResult?>(null)
    val restoreResult: StateFlow<RestoreResult?> = _restoreResult.asStateFlow()

    fun clearRestoreResult() {
        _restoreResult.value = null
    }

    /** iOS parity (SubscriptionView.swift:113-117 alert OK button) — clear the
     *  surfaced error after the user dismisses it, so it does not persist
     *  forever in the screen's LazyColumn. */
    fun clearError() {
        _errorMessage.value = null
    }

    /** True while a direct in-app purchase is being processed. Suppresses the
     *  externalPlanChangeAlert so the user does not see the celebration twice
     *  (iOS QuotaManager.swift:325).
     *
     *  SUBSCRIPTION-GAP-4: read externally by QuotaManager.syncStatus so the
     *  webhook-driven plan-flip detection there can suppress the alert when
     *  the change originated from the in-app purchase flow. */
    @Volatile
    internal var directPurchaseInProgress: Boolean = false
        private set

    /** Public read-only accessor for QuotaManager (SUBSCRIPTION-GAP-4). */
    val isDirectPurchaseInProgress: Boolean get() = directPurchaseInProgress

    // ── External plan change alert (iOS QuotaManager.swift:622-639 parity) ──

    /** Prior observed plan id. Transition to a new paid plan while
     *  directPurchaseInProgress=false signals an external activation
     *  (Play promo redemption / family share / website purchase). */
    @Volatile
    private var previousObservedPlanId: String? = null

    /** Emits the new plan id once when an external plan change is detected. */
    private val _externalPlanChangeAlert = MutableStateFlow<String?>(null)
    val externalPlanChangeAlert: StateFlow<String?> = _externalPlanChangeAlert.asStateFlow()

    fun clearExternalPlanChangeAlert() {
        _externalPlanChangeAlert.value = null
    }

    /** iOS parity (SubscriptionManager.swift:501-563): pending upgrade
     *  detection. When the user has scheduled a tier change (e.g. Core → Plus
     *  effective at next renewal), expose the target productId + effective
     *  date so ProfileScreen can render the "Upgrading on …" notice.
     *
     *  Play Billing v7 does NOT directly expose autoRenewProductId on a
     *  Purchase, so we infer pending upgrades from reconcile state: when
     *  multiple PURCHASED entries exist during the upgrade overlap window
     *  (one auto-renewing, one not), the auto-renewing one is the pending
     *  target and the soon-to-expire one's expiry is the effective date. */

    /** Mirrors iOS computed pendingUpgradePlanId (SubscriptionManager.swift:558-563). */
    val pendingUpgradePlanId: StateFlow<String?> = combine(
        _pendingUpgradeProductId,
        _purchasedProductIds,
    ) { upgradeProductId, _ ->
        when {
            upgradeProductId == null -> null
            upgradeProductId.contains(".core.") -> "core"
            upgradeProductId.contains(".plus.") -> "plus"
            else -> null
        }
    }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), null)

    /** True when the user is eligible to see "Start Free Trial" — Plus offer
     *  exists, no active subscription, no conflict. Mirrors iOS
     *  shouldShowTrialButton (SubscriptionManager.swift:319-330) which the iOS
     *  call site (SubscriptionView.swift:323-329) gates on
     *  `subscriptionConflict != nil || conflictDetectedThisSession`.
     *
     *  SUBSCRIPTION-GAP-3 fix: include _conflictDetectedThisSession in the
     *  combine. The session flag persists past the popup-dismissal reset of
     *  _subscriptionConflict, so without it the trial CTA reappears the moment
     *  the user dismisses the cross-account conflict alert. */
    val shouldShowTrialButton: StateFlow<Boolean> = combine(
        isPlusTrialEligible,
        _purchasedProductIds,
        _subscriptionConflict,
        _conflictDetectedThisSession,
    ) { eligible, purchased, conflict, sessionConflict ->
        eligible && purchased.isEmpty() && conflict == null && !sessionConflict
    }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), false)

    // ── Internal helpers (visible for testing) ─────────────────────────────────

    internal fun setConflict(conflict: SubscriptionConflict?) {
        _subscriptionConflict.value = conflict
    }

    fun clearConflict() {
        _subscriptionConflict.value = null
    }

    /** Mirrors iOS SubscriptionManager.resetForAccountSwitch() — clear all
     *  per-user subscription state on logout so a different user signing in on
     *  the same device cannot see the previous user's entitlements. */
    fun resetForAccountSwitch() {
        _purchasedProductIds.value = emptySet()
        _subscriptionConflict.value = null
        _restoreResult.value = null
        _errorMessage.value = null
        _isLoading.value = false
        _externalPlanChangeAlert.value = null
        _pendingUpgradeProductId.value = null
        _pendingUpgradeEffectiveDate.value = null
        _conflictDetectedThisSession.value = false
        previousObservedPlanId = null
        directPurchaseInProgress = false
        isReconciling.set(false)
        lastReconcileTime = 0L
        scope.launch { prefs.setSubscription(false, "") }
    }

    /** Mirrors iOS QuotaManager.resetForSignOut() — alias used by sign-out
     *  flows. Identical semantics to [resetForAccountSwitch]. */
    fun resetForSignOut() = resetForAccountSwitch()

    // ── Connection ─────────────────────────────────────────────────────────────

    fun startConnection() {
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingResponseCode.OK) {
                    scope.launch { queryProductDetails() }
                    scope.launch { reconcileEntitlements() }
                }
            }

            override fun onBillingServiceDisconnected() {
                // Retry connection with simple back-off
                scope.launch {
                    delay(2_000)
                    startConnection()
                }
            }
        })
    }

    // ── Product details ─────────────────────────────────────────────────────────

    suspend fun queryProductDetails(retryCount: Int = 3) {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                PRODUCT_IDS.map { id ->
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(id)
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build()
                },
            )
            .build()

        var attempt = 0
        var lastError: String? = null
        while (attempt < retryCount) {
            val result = queryProductDetailsAsync(params)
            if (result.first.responseCode == BillingResponseCode.OK) {
                _products.value = result.second ?: emptyList()
                return
            }
            lastError = result.first.debugMessage
            attempt++
            if (attempt < retryCount) delay(1_000L * attempt) // 1s, 2s backoff
        }
        _errorMessage.value = lastError ?: "Failed to load products"
    }

    private suspend fun queryProductDetailsAsync(
        params: QueryProductDetailsParams,
    ): Pair<BillingResult, List<ProductDetails>?> =
        suspendCancellableCoroutine { cont ->
            billingClient.queryProductDetailsAsync(params) { result, productDetailsList ->
                cont.resume(Pair(result, productDetailsList))
            }
        }

    // ── Launch billing flow ─────────────────────────────────────────────────────

    fun launchBillingFlow(activity: Activity, productDetails: ProductDetails, offerToken: String?) {
        _isLoading.value = true
        _errorMessage.value = null
        // Mark as direct purchase so externalPlanChangeAlert is suppressed when
        // verifyWithBackend flips status — the success UI is shown by the normal
        // purchase confirmation flow instead (iOS QuotaManager.swift:325).
        directPurchaseInProgress = true

        val productDetailsParamsList = listOf(
            BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(productDetails)
                .apply {
                    if (offerToken != null) setOfferToken(offerToken)
                    else {
                        // Use first available offer token if present
                        val token = productDetails.subscriptionOfferDetails
                            ?.firstOrNull()
                            ?.offerToken
                        if (token != null) setOfferToken(token)
                    }
                }
                .build(),
        )

        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(productDetailsParamsList)
            .build()

        val result = billingClient.launchBillingFlow(activity, flowParams)
        if (result.responseCode != BillingResponseCode.OK) {
            _isLoading.value = false
            directPurchaseInProgress = false
            // iOS parity: handle the "already subscribed" recovery path by
            // auto-reconciling rather than showing the raw error (iOS
            // SubscriptionManager.swift:246-260).
            if (result.responseCode == BillingResponseCode.ITEM_ALREADY_OWNED) {
                _errorMessage.value = null
                scope.launch { reconcileEntitlements() }
            } else {
                _errorMessage.value = result.debugMessage
            }
        }
    }

    // ── Handle purchases from PurchasesUpdatedListener ─────────────────────────

    fun processPurchases(purchases: List<Purchase>) {
        // Finding 8 — skip superseded transactions (iOS isUpgraded parity at
        // SubscriptionManager.swift:419-422). When Play returns both the
        // soon-to-expire prior subscription AND the new upgrade during the
        // overlap window, drop the older auto-renew=false purchase so
        // _purchasedProductIds reflects only the new tier.
        val newest = purchases
            .filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
            .let { active ->
                if (active.size <= 1) active
                else {
                    val autoRenewing = active.filter { it.isAutoRenewing }
                    if (autoRenewing.isNotEmpty()) {
                        // Keep only the most recently purchased auto-renewing entry
                        listOf(autoRenewing.maxByOrNull { it.purchaseTime } ?: autoRenewing.first())
                    } else {
                        listOf(active.maxByOrNull { it.purchaseTime } ?: active.first())
                    }
                }
            }

        purchases.forEach { purchase ->
            when (purchase.purchaseState) {
                Purchase.PurchaseState.PURCHASED -> {
                    if (newest.contains(purchase)) {
                        scope.launch { handlePurchase(purchase) }
                    }
                }
                Purchase.PurchaseState.PENDING -> {
                    // Finding 7 — Ask-to-Buy / SCA bank confirmation. Surface
                    // a non-error message so the user does not retry.
                    // iOS SubscriptionManager.swift:229-237.
                    _isLoading.value = false
                    directPurchaseInProgress = false
                    _errorMessage.value = "pending_purchase"
                }
                else -> Unit
            }
        }
    }

    private suspend fun handlePurchase(purchase: Purchase) {
        val email = prefs.getUserEmail() ?: return
        val productId = purchase.products.firstOrNull() ?: return

        // Finding 3 — security: skip sandbox / license-tester purchases on
        // production builds so they cannot grant real entitlements (iOS
        // SubscriptionManager.swift:606-611).
        if (shouldSkipForProd(purchase)) {
            _isLoading.value = false
            directPurchaseInProgress = false
            return
        }

        // iOS parity: verify with backend FIRST. Only acknowledge after the
        // backend confirms entitlement (success=true). On failure, leave the
        // purchase un-acknowledged — Google Play will auto-retry delivery for
        // up to 3 days, mirroring StoreKit's Transaction.updates replay.
        val verified = verifyWithBackend(
            purchaseToken = purchase.purchaseToken,
            productId = productId,
            userEmail = email,
        )

        if (verified && !purchase.isAcknowledged) {
            acknowledgePurchase(purchase.purchaseToken)
        }
    }

    // ── Acknowledge ─────────────────────────────────────────────────────────────

    suspend fun acknowledgePurchase(purchaseToken: String) {
        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchaseToken)
            .build()

        suspendCancellableCoroutine { cont ->
            billingClient.acknowledgePurchase(params) { result ->
                if (result.responseCode != BillingResponseCode.OK) {
                    _errorMessage.value = result.debugMessage
                }
                cont.resume(Unit)
            }
        }
    }

    // ── Verify with backend ─────────────────────────────────────────────────────

    suspend fun verifyWithBackend(purchaseToken: String, productId: String, userEmail: String): Boolean {
        // iOS parity (SubscriptionManager.swift:71, 595-601): per-transaction
        // in-flight guard. Reconcile + PurchasesUpdatedListener can race-fire
        // verify for the same purchase token; without this guard the backend
        // sees duplicate /verify calls and may emit two conflict popups.
        val flightKey = "$purchaseToken|$userEmail"
        synchronized(verifyInFlightLock) {
            if (verifyInFlight.contains(flightKey)) {
                return false
            }
            verifyInFlight.add(flightKey)
        }
        return try {
            val response = api.verifyPurchase(
                VerifyRequest(
                    signedTransaction = purchaseToken,
                    // Backend subscription_router.verify_purchase only branches on
                    // apple|google|stripe — sending "android" returns HTTP 400.
                    platform = "google",
                    userEmail = userEmail,
                    productId = productId,
                    // iOS parity — guard prod backend from sandbox/test-track purchases.
                    environment = if (BuildConfig.DEBUG) "Sandbox" else "Production",
                ),
            )
            if (response.success) {
                val previous = previousObservedPlanId
                _purchasedProductIds.value = _purchasedProductIds.value + productId
                val planId = response.planId ?: productId
                prefs.setSubscription(true, planId)

                // iOS parity (SubscriptionManager.swift:501-555): track scheduled
                // Core→Plus auto-renew preference change so the UI can render a
                // "Scheduled" badge and effective date.
                _pendingUpgradeProductId.value = response.pendingUpgradeProductId
                // Server may return ISO8601 string OR epoch millis-as-string. Try parse;
                // fall back to null. Reconcile-derived path (line 626) sets the Long
                // directly from purchaseTime; backend path may override here.
                _pendingUpgradeEffectiveDate.value = response.pendingUpgradeEffectiveDate
                    ?.toLongOrNull()
                    ?: response.pendingUpgradeEffectiveDate
                        ?.let { runCatching { java.time.Instant.parse(it).toEpochMilli() }.getOrNull() }

                // Finding 1 — externalPlanChangeAlert. If the plan id transitions
                // to a new paid plan and we are NOT in the middle of a direct
                // in-app purchase, this was an external activation (Play promo
                // code redemption / family-share / website purchase). Surface a
                // one-shot alert. iOS QuotaManager.swift:622-639.
                if (previous != null && previous != planId && !directPurchaseInProgress) {
                    _externalPlanChangeAlert.value = planId
                }
                previousObservedPlanId = planId
                true
            } else {
                // iOS parity: surface cross-account conflict exactly once per
                // session (SubscriptionManager.swift:650-664). Other failures
                // bubble up via _errorMessage for the UI to display.
                if (response.error == "transaction_belongs_to_different_user") {
                    if (!_conflictDetectedThisSession.value) {
                        _conflictDetectedThisSession.value = true
                        setConflict(SubscriptionConflict(productId))
                    }
                } else {
                    _errorMessage.value = response.message ?: "Purchase verification failed"
                }
                false
            }
        } catch (e: Exception) {
            _errorMessage.value = e.message ?: "Verification failed"
            false
        } finally {
            _isLoading.value = false
            // Direct purchase has resolved (success or failure) — clear the
            // flag so subsequent external activations can fire the alert.
            directPurchaseInProgress = false
            synchronized(verifyInFlightLock) {
                verifyInFlight.remove(flightKey)
            }
        }
    }

    /** Finding 3 helper — true when [purchase] looks like a sandbox / license-test
     *  purchase that should be skipped on production builds. Mirrors iOS
     *  SubscriptionManager.swift:606-611. Real Play orderIds start with "GPA.". */
    private fun shouldSkipForProd(purchase: Purchase): Boolean {
        if (BuildConfig.DEBUG) return false
        val orderId = purchase.orderId
        return orderId.isNullOrEmpty() || !orderId.startsWith("GPA.")
    }

    // ── Reconcile entitlements ──────────────────────────────────────────────────

    suspend fun reconcileEntitlements() {
        // iOS parity (SubscriptionManager.swift:462-477): re-entry guard +
        // 5-second debounce. Multiple call sites (init, resume, restore) can
        // race-fire reconcile; without this guard each verifies every active
        // purchase in parallel, hitting backend N times.
        if (!isReconciling.compareAndSet(false, true)) {
            return
        }
        try {
            val now = System.currentTimeMillis()
            if (now - lastReconcileTime < 5_000L) {
                return
            }
            lastReconcileTime = now

            _isLoading.value = true
            _errorMessage.value = null
            _restoreResult.value = null

            val email = prefs.getUserEmail() ?: run {
                _isLoading.value = false
                _restoreResult.value = RestoreResult.Error("Not signed in")
                return
            }

            val params = QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.SUBS)
                .build()

            val (result, purchases) = queryPurchasesAsync(params)

            if (result.responseCode != BillingResponseCode.OK) {
                _errorMessage.value = result.debugMessage
                _isLoading.value = false
                _restoreResult.value = RestoreResult.Error(result.debugMessage ?: "Restore failed")
                return
            }

            val activePurchases = purchases?.filter {
                it.purchaseState == Purchase.PurchaseState.PURCHASED &&
                    !shouldSkipForProd(it)
            } ?: emptyList()

            // Finding 8 — skip superseded transactions during Core→Plus
            // upgrade overlap (iOS SubscriptionManager.swift:484-486). Keep
            // the most recently purchased auto-renewing entry.
            val effectivePurchases = if (activePurchases.size <= 1) {
                activePurchases
            } else {
                val autoRenewing = activePurchases.filter { it.isAutoRenewing }
                if (autoRenewing.isNotEmpty()) {
                    listOf(autoRenewing.maxByOrNull { it.purchaseTime } ?: autoRenewing.first())
                } else {
                    listOf(activePurchases.maxByOrNull { it.purchaseTime } ?: activePurchases.first())
                }
            }

            // Finding 4 — pending upgrade detection (iOS SubscriptionManager.swift:501-563).
            // During an upgrade overlap window Play returns BOTH the soon-to-
            // expire prior subscription (isAutoRenewing=false) AND the new
            // auto-renewing target. Surface the new productId + the prior
            // purchase's expiry as the effective date.
            val expiringEntry = activePurchases.firstOrNull { !it.isAutoRenewing }
            val targetEntry = activePurchases.firstOrNull { it.isAutoRenewing }
            if (
                activePurchases.size > 1 &&
                expiringEntry != null &&
                targetEntry != null &&
                expiringEntry.products.firstOrNull() != targetEntry.products.firstOrNull()
            ) {
                _pendingUpgradeProductId.value = targetEntry.products.firstOrNull()
                // Play Billing v7 does not expose subscription expiry on
                // Purchase directly; surface the prior purchase time as a
                // best-effort hint (UI can format relative to "next renewal").
                _pendingUpgradeEffectiveDate.value = expiringEntry.purchaseTime
            } else {
                _pendingUpgradeProductId.value = null
                _pendingUpgradeEffectiveDate.value = null
            }

            // Reset purchased set and re-verify each
            _purchasedProductIds.value = emptySet()

            effectivePurchases.forEach { purchase ->
                val productId = purchase.products.firstOrNull() ?: return@forEach
                verifyWithBackend(
                    purchaseToken = purchase.purchaseToken,
                    productId = productId,
                    userEmail = email,
                )
            }

            if (effectivePurchases.isEmpty()) {
                _isLoading.value = false
                _restoreResult.value = RestoreResult.NoPurchases
            } else if (_purchasedProductIds.value.isEmpty()) {
                // Verify call(s) failed for every active purchase
                _restoreResult.value = RestoreResult.Error(
                    _errorMessage.value ?: "Could not restore purchases",
                )
            } else {
                _restoreResult.value = RestoreResult.Success
            }
        } finally {
            // Finding 5 — always reset _isLoading on exit (iOS
            // SubscriptionManager.swift:286). Without this, a cancelled
            // coroutine in verifyWithBackend leaves the spinner stuck.
            _isLoading.value = false
            isReconciling.set(false)
        }
    }

    private suspend fun queryPurchasesAsync(
        params: QueryPurchasesParams,
    ): Pair<BillingResult, List<Purchase>?> =
        suspendCancellableCoroutine { cont ->
            billingClient.queryPurchasesAsync(params) { result, purchases ->
                cont.resume(Pair(result, purchases))
            }
        }

    // ── PurchasesUpdatedListener factory helper ──────────────────────────────

    fun buildPurchasesUpdatedListener(): PurchasesUpdatedListener =
        PurchasesUpdatedListener { result, purchases ->
            when (result.responseCode) {
                BillingResponseCode.OK -> {
                    if (purchases != null) {
                        processPurchases(purchases)
                    }
                }
                BillingResponseCode.USER_CANCELED -> {
                    _isLoading.value = false
                }
                BillingResponseCode.ITEM_ALREADY_OWNED -> {
                    // iOS parity (SubscriptionManager.swift:246-260): Apple's
                    // "you're already subscribed" error → trigger reconcile so
                    // backend records the existing entitlement. Surface a
                    // user-friendly message rather than the raw debug string.
                    _isLoading.value = false
                    _errorMessage.value =
                        "You're already subscribed. Activating in the app now…"
                    scope.launch { reconcileEntitlements() }
                }
                BillingResponseCode.ITEM_UNAVAILABLE,
                BillingResponseCode.BILLING_UNAVAILABLE,
                BillingResponseCode.SERVICE_UNAVAILABLE,
                BillingResponseCode.SERVICE_DISCONNECTED -> {
                    _isLoading.value = false
                    _errorMessage.value =
                        "Google Play is temporarily unavailable. Please try again."
                }
                else -> {
                    _isLoading.value = false
                    _errorMessage.value = result.debugMessage
                }
            }
        }
}
