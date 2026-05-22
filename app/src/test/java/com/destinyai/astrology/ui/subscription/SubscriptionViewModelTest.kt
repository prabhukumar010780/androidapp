package com.destinyai.astrology.ui.subscription

import app.cash.turbine.test
import com.destinyai.astrology.data.local.prefs.UserPreferences
import com.destinyai.astrology.data.remote.AstroApiService
import com.destinyai.astrology.data.remote.PlanDto
import com.destinyai.astrology.data.remote.StatusResponse
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
class SubscriptionViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var api: AstroApiService
    private lateinit var prefs: UserPreferences
    private lateinit var vm: SubscriptionViewModel

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
        coEvery { prefs.getUserEmail() } returns "u@x.com"
        vm = SubscriptionViewModel(api, prefs)
    }

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
}
