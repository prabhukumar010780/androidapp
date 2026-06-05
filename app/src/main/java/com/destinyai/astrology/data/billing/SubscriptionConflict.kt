package com.destinyai.astrology.data.billing

data class SubscriptionConflict(val productId: String)

/** Outcome of [BillingManager.reconcileEntitlements] used to drive UI feedback
 *  for the Restore Purchases action (Apple HIG / Play subscription policy). */
sealed class RestoreResult {
    object Success : RestoreResult()
    object NoPurchases : RestoreResult()
    data class Error(val message: String) : RestoreResult()
}
