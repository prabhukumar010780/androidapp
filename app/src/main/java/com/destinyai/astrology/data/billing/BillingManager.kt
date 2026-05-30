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
import com.destinyai.astrology.data.local.prefs.UserPreferences
import com.destinyai.astrology.data.remote.AstroApiService
import com.destinyai.astrology.data.remote.VerifyRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
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

    /** True when any Plus product has a trial offer available. */
    val isPlusTrialEligible: StateFlow<Boolean> = combine(
        _products,
        flowOf(Unit),
    ) { products, _ ->
        products.any { product ->
            product.productId.contains("plus", ignoreCase = true) &&
                product.subscriptionOfferDetails?.any { offer ->
                    offer.offerId?.contains("trial", ignoreCase = true) == true
                } == true
        }
    }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), false)

    private val _purchasedProductIds = MutableStateFlow<Set<String>>(emptySet())
    val purchasedProductIds: StateFlow<Set<String>> = _purchasedProductIds.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _subscriptionConflict = MutableStateFlow<SubscriptionConflict?>(null)
    val subscriptionConflict: StateFlow<SubscriptionConflict?> = _subscriptionConflict.asStateFlow()

    // ── Internal helpers (visible for testing) ─────────────────────────────────

    internal fun setConflict(conflict: SubscriptionConflict?) {
        _subscriptionConflict.value = conflict
    }

    fun clearConflict() {
        _subscriptionConflict.value = null
    }

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
            _errorMessage.value = result.debugMessage
        }
    }

    // ── Handle purchases from PurchasesUpdatedListener ─────────────────────────

    fun processPurchases(purchases: List<Purchase>) {
        if (purchases.size > 1) {
            // Multiple active subscriptions — signal conflict with first
            val firstProductId = purchases.first().products.firstOrNull() ?: ""
            _subscriptionConflict.value = SubscriptionConflict(firstProductId)
        }

        purchases.forEach { purchase ->
            if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                scope.launch { handlePurchase(purchase) }
            }
        }
    }

    private suspend fun handlePurchase(purchase: Purchase) {
        val email = prefs.getUserEmail() ?: return
        val productId = purchase.products.firstOrNull() ?: return

        // Acknowledge first if needed (must be within 3 days or Google auto-refunds)
        if (!purchase.isAcknowledged) {
            acknowledgePurchase(purchase.purchaseToken)
        }

        verifyWithBackend(
            purchaseToken = purchase.purchaseToken,
            productId = productId,
            userEmail = email,
        )
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

    suspend fun verifyWithBackend(purchaseToken: String, productId: String, userEmail: String) {
        try {
            val response = api.verifyPurchase(
                VerifyRequest(
                    signedTransaction = purchaseToken,
                    platform = "android",
                    userEmail = userEmail,
                    productId = productId,
                ),
            )
            if (response.success) {
                _purchasedProductIds.value = _purchasedProductIds.value + productId
                val planId = response.planId ?: productId
                prefs.setSubscription(true, planId)
            }
        } catch (e: Exception) {
            _errorMessage.value = e.message ?: "Verification failed"
        } finally {
            _isLoading.value = false
        }
    }

    // ── Reconcile entitlements ──────────────────────────────────────────────────

    suspend fun reconcileEntitlements() {
        _isLoading.value = true
        _errorMessage.value = null

        val email = prefs.getUserEmail() ?: run {
            _isLoading.value = false
            return
        }

        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()

        val (result, purchases) = queryPurchasesAsync(params)

        if (result.responseCode != BillingResponseCode.OK) {
            _errorMessage.value = result.debugMessage
            _isLoading.value = false
            return
        }

        val activePurchases = purchases?.filter {
            it.purchaseState == Purchase.PurchaseState.PURCHASED
        } ?: emptyList()

        // Reset purchased set and re-verify each
        _purchasedProductIds.value = emptySet()

        activePurchases.forEach { purchase ->
            val productId = purchase.products.firstOrNull() ?: return@forEach
            verifyWithBackend(
                purchaseToken = purchase.purchaseToken,
                productId = productId,
                userEmail = email,
            )
        }

        if (activePurchases.isEmpty()) {
            _isLoading.value = false
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
                else -> {
                    _isLoading.value = false
                    _errorMessage.value = result.debugMessage
                }
            }
        }
}
