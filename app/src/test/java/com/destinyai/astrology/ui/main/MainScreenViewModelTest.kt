package com.destinyai.astrology.ui.main

import app.cash.turbine.test
import com.destinyai.astrology.data.billing.BillingManager
import com.destinyai.astrology.data.billing.SubscriptionConflict
import com.destinyai.astrology.data.local.db.CompatibilityHistoryDao
import com.destinyai.astrology.data.local.prefs.UserPreferences
import com.destinyai.astrology.services.ExternalPlanChange
import com.destinyai.astrology.services.QuotaManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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
class MainScreenViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var prefs: UserPreferences
    private lateinit var quotaManager: QuotaManager
    private lateinit var billingManager: BillingManager
    private lateinit var historyDao: CompatibilityHistoryDao
    private lateinit var vm: MainScreenViewModel

    private val isGuestFlow = MutableStateFlow(false)
    private val activeProfileIdFlow = MutableStateFlow<String?>(null)
    private val externalPlanChangeFlow = MutableStateFlow<ExternalPlanChange?>(null)
    private val subscriptionConflictFlow = MutableStateFlow<SubscriptionConflict?>(null)

    @BeforeAll fun setMainDispatcher() { Dispatchers.setMain(testDispatcher) }
    @AfterAll fun resetMainDispatcher() { Dispatchers.resetMain() }

    @BeforeEach
    fun setUp() {
        prefs = mockk(relaxed = true)
        every { prefs.isGuestUserFlow } returns isGuestFlow
        every { prefs.activeProfileIdFlow } returns activeProfileIdFlow

        quotaManager = mockk(relaxed = true)
        every { quotaManager.externalPlanChangeAlert } returns externalPlanChangeFlow

        billingManager = mockk(relaxed = true)
        every { billingManager.subscriptionConflict } returns subscriptionConflictFlow

        historyDao = mockk(relaxed = true)

        vm = MainScreenViewModel(prefs, quotaManager, billingManager, historyDao)
    }

    @Test
    fun `isGuestUser reflects UserPreferences flow`() = runTest {
        vm.isGuestUser.test {
            assertFalse(awaitItem())
            isGuestFlow.value = true
            assertTrue(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `activeProfileId reflects UserPreferences flow`() = runTest {
        vm.activeProfileId.test {
            assertNull(awaitItem())
            activeProfileIdFlow.value = "partner-123"
            assertEquals("partner-123", awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `externalPlanChangeAlert delegates to QuotaManager`() = runTest {
        vm.externalPlanChangeAlert.test {
            assertNull(awaitItem())

            val alert = ExternalPlanChange(
                previousPlanId = "free_registered",
                newPlanId = "plus",
                newPlanDisplayName = "Plus Yearly",
                expiresAt = "2027-01-01",
                willAutoRenew = true,
            )
            externalPlanChangeFlow.value = alert
            assertEquals("plus", awaitItem()?.newPlanId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `subscriptionConflict delegates to BillingManager`() = runTest {
        vm.subscriptionConflict.test {
            assertNull(awaitItem())

            subscriptionConflictFlow.value = SubscriptionConflict("com.daa.plus.monthly")
            assertNotNull(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `clearExternalPlanChangeAlert calls QuotaManager`() {
        vm.clearExternalPlanChangeAlert()
        verify { quotaManager.clearExternalPlanChangeAlert() }
    }

    @Test
    fun `clearSubscriptionConflict calls BillingManager`() {
        vm.clearSubscriptionConflict()
        verify { billingManager.clearConflict() }
    }
}
