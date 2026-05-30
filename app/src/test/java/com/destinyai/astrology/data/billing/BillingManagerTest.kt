package com.destinyai.astrology.data.billing

import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.destinyai.astrology.data.local.prefs.UserPreferences
import com.destinyai.astrology.data.remote.AstroApiService
import com.destinyai.astrology.data.remote.VerifyRequest
import com.destinyai.astrology.data.remote.VerifyResponse
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@ExperimentalCoroutinesApi
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BillingManagerTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var billingClient: BillingClient
    private lateinit var api: AstroApiService
    private lateinit var prefs: UserPreferences
    private lateinit var manager: BillingManager

    @BeforeAll
    fun setMainDispatcher() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterAll
    fun resetMainDispatcher() {
        Dispatchers.resetMain()
    }

    @BeforeEach
    fun setUp() {
        billingClient = mockk(relaxed = true)
        api = mockk(relaxed = true)
        prefs = mockk(relaxed = true)
        coEvery { prefs.getUserEmail() } returns "test@example.com"
        manager = BillingManager(billingClient, api, prefs)
    }

    // ── Initial state ──────────────────────────────────────────────────────────

    @Test
    fun `initial products state is empty`() = runTest {
        assertTrue(manager.products.value.isEmpty())
    }

    @Test
    fun `initial purchasedProductIds is empty`() = runTest {
        assertTrue(manager.purchasedProductIds.value.isEmpty())
    }

    @Test
    fun `initial isLoading is false`() = runTest {
        assertFalse(manager.isLoading.value)
    }

    @Test
    fun `initial errorMessage is null`() = runTest {
        assertNull(manager.errorMessage.value)
    }

    @Test
    fun `initial subscriptionConflict is null`() = runTest {
        assertNull(manager.subscriptionConflict.value)
    }

    // ── Backend verify call ────────────────────────────────────────────────────

    @Test
    fun `verifyWithBackend calls api with correct parameters`() = runTest {
        coEvery {
            api.verifyPurchase(any())
        } returns VerifyResponse(success = true, planId = "com.daa.core.monthly", isPremium = true)

        manager.verifyWithBackend(
            purchaseToken = "tok_abc",
            productId = "com.daa.core.monthly",
            userEmail = "test@example.com",
        )

        coVerify {
            api.verifyPurchase(
                VerifyRequest(
                    signedTransaction = "tok_abc",
                    platform = "android",
                    userEmail = "test@example.com",
                    productId = "com.daa.core.monthly",
                ),
            )
        }
    }

    @Test
    fun `verifyWithBackend on success updates purchasedProductIds`() = runTest {
        coEvery {
            api.verifyPurchase(any())
        } returns VerifyResponse(success = true, planId = "com.daa.core.monthly", isPremium = true)

        manager.verifyWithBackend(
            purchaseToken = "tok_abc",
            productId = "com.daa.core.monthly",
            userEmail = "test@example.com",
        )

        assertTrue(manager.purchasedProductIds.value.contains("com.daa.core.monthly"))
    }

    @Test
    fun `verifyWithBackend on success persists premium state to prefs`() = runTest {
        coEvery {
            api.verifyPurchase(any())
        } returns VerifyResponse(success = true, planId = "com.daa.core.monthly", isPremium = true)

        manager.verifyWithBackend(
            purchaseToken = "tok_abc",
            productId = "com.daa.core.monthly",
            userEmail = "test@example.com",
        )

        coVerify { prefs.setSubscription(true, "com.daa.core.monthly") }
    }

    @Test
    fun `verifyWithBackend on failure sets errorMessage`() = runTest {
        coEvery { api.verifyPurchase(any()) } throws RuntimeException("Network error")

        manager.verifyWithBackend(
            purchaseToken = "tok_bad",
            productId = "com.daa.core.monthly",
            userEmail = "test@example.com",
        )

        assertNotNull(manager.errorMessage.value)
    }

    @Test
    fun `verifyWithBackend on api failure does not add productId to purchased`() = runTest {
        coEvery { api.verifyPurchase(any()) } throws RuntimeException("Network error")

        manager.verifyWithBackend(
            purchaseToken = "tok_bad",
            productId = "com.daa.core.monthly",
            userEmail = "test@example.com",
        )

        assertFalse(manager.purchasedProductIds.value.contains("com.daa.core.monthly"))
    }

    // ── Purchase processing ────────────────────────────────────────────────────

    @Test
    fun `processPurchases with empty list does nothing`() = runTest {
        manager.processPurchases(emptyList())

        assertTrue(manager.purchasedProductIds.value.isEmpty())
        assertNull(manager.errorMessage.value)
    }

    @Test
    fun `processPurchases with acknowledged purchase verifies with backend`() = runTest {
        val purchase = mockk<Purchase>(relaxed = true)
        every { purchase.purchaseToken } returns "tok_123"
        every { purchase.purchaseState } returns Purchase.PurchaseState.PURCHASED
        every { purchase.isAcknowledged } returns true
        every { purchase.products } returns listOf("com.daa.core.monthly")

        coEvery {
            api.verifyPurchase(any())
        } returns VerifyResponse(success = true, planId = "com.daa.core.monthly", isPremium = true)
        coEvery { prefs.getUserEmail() } returns "test@example.com"

        manager.processPurchases(listOf(purchase))

        coVerify { api.verifyPurchase(any()) }
    }

    // ── SubscriptionConflict ───────────────────────────────────────────────────

    @Test
    fun `conflict is set when two active subscriptions detected`() = runTest {
        val purchase1 = mockk<Purchase>(relaxed = true)
        every { purchase1.purchaseToken } returns "tok_1"
        every { purchase1.purchaseState } returns Purchase.PurchaseState.PURCHASED
        every { purchase1.isAcknowledged } returns true
        every { purchase1.products } returns listOf("com.daa.core.monthly")

        val purchase2 = mockk<Purchase>(relaxed = true)
        every { purchase2.purchaseToken } returns "tok_2"
        every { purchase2.purchaseState } returns Purchase.PurchaseState.PURCHASED
        every { purchase2.isAcknowledged } returns true
        every { purchase2.products } returns listOf("com.daa.plus.monthly")

        coEvery {
            api.verifyPurchase(any())
        } returns VerifyResponse(success = true, isPremium = true)
        coEvery { prefs.getUserEmail() } returns "test@example.com"

        manager.processPurchases(listOf(purchase1, purchase2))

        assertNotNull(manager.subscriptionConflict.value)
    }

    @Test
    fun `clearConflict sets subscriptionConflict to null`() = runTest {
        // Directly mutate via the internal method for test
        manager.setConflict(SubscriptionConflict("com.daa.core.monthly"))
        assertNotNull(manager.subscriptionConflict.value)

        manager.clearConflict()
        assertNull(manager.subscriptionConflict.value)
    }

    // ── Product IDs constant ───────────────────────────────────────────────────

    @Test
    fun `PRODUCT_IDS contains all four expected product IDs`() {
        assertTrue(BillingManager.PRODUCT_IDS.contains("com.daa.core.monthly"))
        assertTrue(BillingManager.PRODUCT_IDS.contains("com.daa.core.yearly"))
        assertTrue(BillingManager.PRODUCT_IDS.contains("com.daa.plus.monthly"))
        assertTrue(BillingManager.PRODUCT_IDS.contains("com.daa.plus.yearly"))
        assertEquals(4, BillingManager.PRODUCT_IDS.size)
    }
}
