package com.destinyai.astrology.ui.subscription

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.billingclient.api.ProductDetails
import com.destinyai.astrology.data.billing.BillingManager
import com.destinyai.astrology.data.billing.SubscriptionConflict
import com.destinyai.astrology.data.local.prefs.UserPreferences
import com.destinyai.astrology.data.remote.AstroApiService
import com.destinyai.astrology.data.remote.PlanDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
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

@HiltViewModel
class SubscriptionViewModel @Inject constructor(
    private val api: AstroApiService,
    private val prefs: UserPreferences,
    private val billingManager: BillingManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SubscriptionUiState())

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

    /** Passthrough loading from BillingManager for UI spinner. */
    val isLoading: StateFlow<Boolean> = billingManager.isLoading

    /** True when a Plus free trial offer is available. */
    val isPlusTrialEligible: StateFlow<Boolean> = billingManager.isPlusTrialEligible

    fun loadPlans() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val plans = api.getPlans()
                _uiState.update { it.copy(plans = plans, isLoading = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "Failed to load plans") }
            }
        }
    }

    fun loadCurrentPlan() {
        viewModelScope.launch {
            val email = prefs.getUserEmail() ?: return@launch
            try {
                val status = api.getStatus(email)
                _uiState.update {
                    it.copy(
                        currentPlanId = status.planId,
                        isPremium = status.isPremium,
                    )
                }
            } catch (_: Exception) {}
        }
    }

    /** Launch Google Play billing flow for the given product. */
    fun purchase(productDetails: ProductDetails, activity: Activity, offerToken: String? = null) {
        billingManager.launchBillingFlow(activity, productDetails, offerToken)
    }

    /** Re-query Play and re-verify every active subscription with backend. */
    fun restorePurchases() {
        viewModelScope.launch {
            billingManager.reconcileEntitlements()
        }
    }
}
