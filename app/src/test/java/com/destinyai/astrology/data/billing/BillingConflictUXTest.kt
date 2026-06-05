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
class BillingConflictUXTest {

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
        coEvery { prefs.getUserEmail() } returns "u@x.com"
        manager = BillingManager(billingClient, api, prefs)
    }

    private val conflictResponse = VerifyResponse(
        success = false,
        error = "transaction_belongs_to_different_user",
        message = "conflict",
        planId = null,
        pendingUpgradeProductId = null,
        pendingUpgradeEffectiveDate = null,
    )

    @Test
    fun `first conflict sets conflictDetectedThisSession to true`() = runTest {
        coEvery { api.verifyPurchase(any()) } returns conflictResponse

        manager.verifyWithBackend("tok1", "com.daa.plus.monthly", "u@x.com")

        assertTrue(manager.conflictDetectedThisSession.value)
        assertNotNull(manager.subscriptionConflict.value)
    }

    @Test
    fun `second conflict in same session does not re-fire subscriptionConflict`() = runTest {
        coEvery { api.verifyPurchase(any()) } returns conflictResponse

        manager.verifyWithBackend("tok1", "com.daa.plus.monthly", "u@x.com")
        manager.clearConflict()

        manager.verifyWithBackend("tok2", "com.daa.plus.monthly", "u@x.com")

        assertNull(manager.subscriptionConflict.value)
        assertTrue(manager.conflictDetectedThisSession.value)
    }

    @Test
    fun `conflict resets after resetForAccountSwitch`() = runTest {
        coEvery { api.verifyPurchase(any()) } returns conflictResponse
        manager.verifyWithBackend("tok1", "com.daa.plus.monthly", "u@x.com")
        assertTrue(manager.conflictDetectedThisSession.value)

        manager.resetForAccountSwitch()

        assertFalse(manager.conflictDetectedThisSession.value)

        manager.verifyWithBackend("tok3", "com.daa.plus.monthly", "u@x.com")
        assertTrue(manager.conflictDetectedThisSession.value)
    }

    @Test
    fun `conflict does NOT fire when success=true`() = runTest {
        coEvery { api.verifyPurchase(any()) } returns VerifyResponse(
            success = true, planId = "plus", error = null, message = null,
            pendingUpgradeProductId = null, pendingUpgradeEffectiveDate = null,
        )

        manager.verifyWithBackend("tok4", "com.daa.plus.monthly", "u@x.com")

        assertFalse(manager.conflictDetectedThisSession.value)
        assertNull(manager.subscriptionConflict.value)
    }
}
