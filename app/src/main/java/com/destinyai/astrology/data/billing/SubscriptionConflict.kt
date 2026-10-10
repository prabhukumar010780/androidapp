package com.destinyai.astrology.data.billing

data class SubscriptionConflict(val productId: String)

/** iOS parity (SubscriptionManager purchase() Transaction result) — a per-transaction
 *  terminal signal keyed by [productId], emitted exactly once when a verify resolves
 *  (success OR failure). SubscriptionViewModel.purchase() awaits THIS, keyed to the
 *  product it is buying, rather than the shared multi-writer BillingManager.isLoading —
 *  so a concurrent reconcile can neither prematurely resolve nor indefinitely pin an
 *  individual purchase's spinner (the "stuck Choose-a-plan spinners" report). */
data class PurchaseOutcome(val productId: String, val success: Boolean)

/** Outcome of [BillingManager.reconcileEntitlements] used to drive UI feedback
 *  for the Restore Purchases action (Apple HIG / Play subscription policy). */
sealed class RestoreResult {
    object Success : RestoreResult()
    object NoPurchases : RestoreResult()
    data class Error(val message: String) : RestoreResult()
}
