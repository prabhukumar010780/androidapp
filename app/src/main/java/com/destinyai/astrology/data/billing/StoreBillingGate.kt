package com.destinyai.astrology.data.billing

/**
 * One Destiny account has one live store entitlement.
 *
 * If /subscription/status says the user is already premium on a *known other*
 * store, do not open Play Billing. Same-store Core→Plus is allowed when
 * [accountPlatform] is google. Null / manual / unknown must NOT block — those
 * are legacy Apple rows; fail-open keeps live iOS and Play upgrades safe.
 */
object StoreBillingGate {
    const val THIS_STORE = "google"
    const val ERROR_OTHER_PLATFORM = "active_subscription_on_other_platform"
    private val OTHER_STORES = setOf("apple", "google", "stripe") - THIS_STORE

    fun shouldBlockPurchase(isPremium: Boolean, accountPlatform: String?): Boolean {
        if (!isPremium) return false
        val platform = accountPlatform?.trim()?.lowercase().orEmpty()
        return platform in OTHER_STORES
    }
}
