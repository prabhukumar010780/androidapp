package com.destinyai.astrology.data.billing

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class StoreBillingGateTest {

    @Test
    fun `free user is never blocked`() {
        assertFalse(StoreBillingGate.shouldBlockPurchase(isPremium = false, accountPlatform = "apple"))
        assertFalse(StoreBillingGate.shouldBlockPurchase(isPremium = false, accountPlatform = null))
    }

    @Test
    fun `google plus can still buy on play for same-store upgrade`() {
        assertFalse(StoreBillingGate.shouldBlockPurchase(isPremium = true, accountPlatform = "google"))
        assertFalse(StoreBillingGate.shouldBlockPurchase(isPremium = true, accountPlatform = "Google"))
    }

    @Test
    fun `apple or stripe plus must not open play billing`() {
        assertTrue(StoreBillingGate.shouldBlockPurchase(isPremium = true, accountPlatform = "apple"))
        assertTrue(StoreBillingGate.shouldBlockPurchase(isPremium = true, accountPlatform = "stripe"))
    }

    @Test
    fun `premium with unknown or manual platform is not blocked`() {
        // Live-iOS safety: a null platform is a legacy Apple row, not proof of Google.
        assertFalse(StoreBillingGate.shouldBlockPurchase(isPremium = true, accountPlatform = null))
        assertFalse(StoreBillingGate.shouldBlockPurchase(isPremium = true, accountPlatform = ""))
        assertFalse(StoreBillingGate.shouldBlockPurchase(isPremium = true, accountPlatform = "manual"))
    }
}
