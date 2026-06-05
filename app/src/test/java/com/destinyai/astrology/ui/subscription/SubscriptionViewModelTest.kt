package com.destinyai.astrology.ui.subscription

import android.app.Activity
import app.cash.turbine.test
import com.android.billingclient.api.ProductDetails
import com.destinyai.astrology.data.billing.BillingManager
import com.destinyai.astrology.data.billing.SubscriptionConflict
import com.destinyai.astrology.data.local.prefs.UserPreferences
import com.destinyai.astrology.data.remote.AstroApiService
import com.destinyai.astrology.data.remote.PlanDto
import com.destinyai.astrology.data.remote.StatusResponse
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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
class SubscriptionViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var api: AstroApiService
    private lateinit var prefs: UserPreferences
    private lateinit var billingManager: BillingManager
    private lateinit var vm: SubscriptionViewModel

    // Backing StateFlows exposed by BillingManager mock
    private val productsFlow = MutableStateFlow<List<ProductDetails>>(emptyList())
    private val purchasedIdsFlow = MutableStateFlow<Set<String>>(emptySet())
    private val loadingFlow = MutableStateFlow(false)
    private val errorFlow = MutableStateFlow<String?>(null)
    private val conflictFlow = MutableStateFlow<SubscriptionConflict?>(null)
    private val isPlusTrialEligibleFlow = MutableStateFlow(false)

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
        api = mockk(relaxed = true)
        prefs = mockk(relaxed = true)
        billingManager = mockk(relaxed = true)
        coEvery { prefs.getUserEmail() } returns "u@x.com"

        every { billingManager.products } returns productsFlow
        every { billingManager.purchasedProductIds } returns purchasedIdsFlow
        every { billingManager.isLoading } returns loadingFlow
        every { billingManager.errorMessage } returns errorFlow
        every { billingManager.subscriptionConflict } returns conflictFlow
        every { billingManager.isPlusTrialEligible } returns isPlusTrialEligibleFlow

        productsFlow.value = emptyList()
        purchasedIdsFlow.value = emptySet()
        loadingFlow.value = false
        errorFlow.value = null
        conflictFlow.value = null
        isPlusTrialEligibleFlow.value = false

        vm = SubscriptionViewModel(api, prefs, billingManager)
    }

    // ── Existing tests preserved ───────────────────────────────────────────────

    @Test
    fun `initial state has empty plans`() = runTest {
        vm.uiState.test {
            val s = awaitItem()
            assertTrue(s.plans.isEmpty())
            assertEquals("", s.currentPlanId)
            assertFalse(s.isPremium)
            assertFalse(s.isLoading)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `loadPlans populates plans`() = runTest {
        coEvery { api.getPlans() } returns listOf(
            PlanDto("free_registered", "Free", true, 0.0, 0.0, 3),
            PlanDto("premium_monthly", "Premium Monthly", false, 9.99, 99.99, 999),
        )

        vm.loadPlans()

        vm.uiState.test {
            val s = awaitItem()
            assertEquals(2, s.plans.size)
            assertEquals("free_registered", s.plans[0].planId)
            assertFalse(s.isLoading)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `loadPlans sets error on failure`() = runTest {
        coEvery { api.getPlans() } throws RuntimeException("error")

        vm.loadPlans()

        vm.uiState.test {
            assertNotNull(awaitItem().error)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `loadCurrentPlan sets planId and isPremium`() = runTest {
        coEvery { api.getStatus("u@x.com") } returns StatusResponse(
            userEmail = "u@x.com",
            planId = "premium_monthly",
            isGeneratedEmail = false,
            isPremium = true,
        )

        vm.loadCurrentPlan()

        vm.uiState.test {
            val s = awaitItem()
            assertEquals("premium_monthly", s.currentPlanId)
            assertTrue(s.isPremium)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `loadCurrentPlan does nothing when no email`() = runTest {
        coEvery { prefs.getUserEmail() } returns null

        vm.loadCurrentPlan()

        vm.uiState.test {
            assertEquals("", awaitItem().currentPlanId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `loadCurrentPlan silently ignores api failure`() = runTest {
        coEvery { api.getStatus(any()) } throws RuntimeException("error")

        vm.loadCurrentPlan()

        vm.uiState.test {
            val s = awaitItem()
            assertEquals("", s.currentPlanId)
            assertNull(s.error)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── Billing integration — purchase() ──────────────────────────────────────

    @Test
    fun `purchase delegates to BillingManager launchBillingFlow`() = runTest {
        val activity = mockk<Activity>(relaxed = true)
        val productDetails = mockk<ProductDetails>(relaxed = true)

        vm.purchase(productDetails, activity)

        coVerify { billingManager.launchBillingFlow(activity, productDetails, null) }
    }

    @Test
    fun `purchase sets isLoading true while in flight`() = runTest {
        val activity = mockk<Activity>(relaxed = true)
        val productDetails = mockk<ProductDetails>(relaxed = true)

        loadingFlow.value = true

        vm.isLoading.test {
            assertTrue(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `purchase surfaces BillingManager error into uiState`() = runTest {
        errorFlow.value = "Purchase failed"

        vm.uiState.test {
            val s = awaitItem()
            assertEquals("Purchase failed", s.error)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── Billing integration — restorePurchases() ──────────────────────────────

    @Test
    fun `restorePurchases calls BillingManager reconcileEntitlements`() = runTest {
        vm.restorePurchases()

        coVerify { billingManager.reconcileEntitlements() }
    }

    // ── hasActiveSubscription derived flow ────────────────────────────────────

    @Test
    fun `hasActiveSubscription is false when purchasedProductIds is empty`() = runTest {
        purchasedIdsFlow.value = emptySet()

        vm.hasActiveSubscription.test {
            assertFalse(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `hasActiveSubscription is true when purchasedProductIds has an entry`() = runTest {
        purchasedIdsFlow.value = setOf("com.daa.core.monthly")

        vm.hasActiveSubscription.test {
            assertTrue(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `hasActiveSubscription updates when purchasedProductIds changes`() = runTest {
        purchasedIdsFlow.value = emptySet()

        vm.hasActiveSubscription.test {
            assertFalse(awaitItem())

            purchasedIdsFlow.value = setOf("com.daa.plus.yearly")
            assertTrue(awaitItem())

            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── activePlanId derived flow ──────────────────────────────────────────────

    @Test
    fun `activePlanId is null when no active subscription`() = runTest {
        purchasedIdsFlow.value = emptySet()

        vm.activePlanId.test {
            assertNull(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `activePlanId returns first product when purchased`() = runTest {
        purchasedIdsFlow.value = setOf("com.daa.core.yearly")

        vm.activePlanId.test {
            assertEquals("com.daa.core.yearly", awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── subscriptionConflict passthrough ──────────────────────────────────────

    @Test
    fun `subscriptionConflict is null initially`() = runTest {
        vm.subscriptionConflict.test {
            assertNull(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `subscriptionConflict reflects BillingManager state`() = runTest {
        conflictFlow.value = SubscriptionConflict("com.daa.core.monthly")

        vm.subscriptionConflict.test {
            val conflict = awaitItem()
            assertNotNull(conflict)
            assertEquals("com.daa.core.monthly", conflict?.productId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `subscriptionConflict transitions from set to cleared`() = runTest {
        vm.subscriptionConflict.test {
            assertNull(awaitItem())

            conflictFlow.value = SubscriptionConflict("com.daa.plus.monthly")
            assertEquals("com.daa.plus.monthly", awaitItem()?.productId)

            conflictFlow.value = null
            assertNull(awaitItem())

            cancelAndIgnoreRemainingEvents()
        }
    }
}
