package com.destinyai.astrology.data.billing

import com.android.billingclient.api.BillingClient
import com.destinyai.astrology.data.local.prefs.UserPreferences
import com.destinyai.astrology.data.remote.AstroApiService
import com.destinyai.astrology.data.remote.VerifyResponse
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@ExperimentalCoroutinesApi
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BillingAccountSwitchTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var billingClient: BillingClient
    private lateinit var api: AstroApiService
    private lateinit var prefs: UserPreferences
    private lateinit var manager: BillingManager

    @BeforeAll fun setMainDispatcher() { Dispatchers.setMain(testDispatcher) }
    @AfterAll fun resetMainDispatcher() { Dispatchers.resetMain() }

    @BeforeEach
    fun setUp() {
        billingClient = mockk(relaxed = true)
        api = mockk(relaxed = true)
        prefs = mockk(relaxed = true)
        coEvery { prefs.getUserEmail() } returns "userA@example.com"
        manager = BillingManager(billingClient, api, prefs)
    }

    @Test
    fun `resetForAccountSwitch clears purchasedProductIds`() = runTest {
        coEvery { prefs.getUserEmail() } returns "userA@example.com"
        coEvery { api.verifyPurchase(any()) } returns VerifyResponse(
            success = true,
            planId = "plus",
            pendingUpgradeProductId = null,
            pendingUpgradeEffectiveDate = null,
            error = null,
            message = null,
        )
        manager.verifyWithBackend("token-A", "com.daa.plus.monthly", "userA@example.com")
        assertTrue(manager.purchasedProductIds.value.isNotEmpty())

        manager.resetForAccountSwitch()

        assertTrue(manager.purchasedProductIds.value.isEmpty())
    }

    @Test
    fun `resetForAccountSwitch clears subscriptionConflict`() = runTest {
        manager.setConflict(SubscriptionConflict("com.daa.plus.monthly"))
        assertNotNull(manager.subscriptionConflict.value)

        manager.resetForAccountSwitch()

        assertNull(manager.subscriptionConflict.value)
    }

    @Test
    fun `resetForAccountSwitch clears conflictDetectedThisSession`() = runTest {
        coEvery { prefs.getUserEmail() } returns "userA@example.com"
        coEvery { api.verifyPurchase(any()) } returns VerifyResponse(
            success = false,
            error = "transaction_belongs_to_different_user",
            message = "conflict",
            planId = null,
            pendingUpgradeProductId = null,
            pendingUpgradeEffectiveDate = null,
        )
        manager.verifyWithBackend("token-X", "com.daa.plus.monthly", "userA@example.com")
        assertTrue(manager.conflictDetectedThisSession.value)

        manager.resetForAccountSwitch()

        assertFalse(manager.conflictDetectedThisSession.value)
    }

    @Test
    fun `resetForAccountSwitch clears errorMessage and isLoading`() = runTest {
        manager.resetForAccountSwitch()

        assertNull(manager.errorMessage.value)
        assertFalse(manager.isLoading.value)
    }

    @Test
    fun `resetForSignOut is alias for resetForAccountSwitch`() = runTest {
        manager.setConflict(SubscriptionConflict("com.daa.core.monthly"))
        manager.resetForSignOut()
        assertNull(manager.subscriptionConflict.value)
    }
}
