package com.destinyai.astrology.ui.profile

import app.cash.turbine.test
import com.destinyai.astrology.data.billing.BillingManager
import com.destinyai.astrology.data.local.prefs.UserPreferences
import com.destinyai.astrology.data.remote.AstroApiService
import com.destinyai.astrology.data.remote.DeleteAccountRequest
import com.destinyai.astrology.data.remote.StatusResponse
import com.destinyai.astrology.data.remote.AnalyticsConsentRequest
import com.destinyai.astrology.data.remote.SuccessResponse
import com.destinyai.astrology.data.repository.AuthRepository
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
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@ExperimentalCoroutinesApi
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ProfileViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var api: AstroApiService
    private lateinit var prefs: UserPreferences
    private lateinit var authRepository: AuthRepository
    private lateinit var billingManager: BillingManager
    private lateinit var vm: ProfileViewModel

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
        authRepository = mockk(relaxed = true)
        billingManager = mockk(relaxed = true)
        // BillingManager exposes StateFlow<...> sources that the VM observes in init —
        // stub them with empty flows so the constructor doesn't throw.
        every { billingManager.pendingUpgradePlanId } returns MutableStateFlow<String?>(null)
        every { billingManager.pendingUpgradeEffectiveDate } returns MutableStateFlow<Long?>(null)
        every { billingManager.purchasedProductIds } returns MutableStateFlow<Set<String>>(emptySet())
        coEvery { prefs.getUserEmail() } returns "u@x.com"
        coEvery { prefs.getUserName() } returns "Prabhu"
        vm = ProfileViewModel(api, prefs, authRepository, billingManager)
    }

    @Test
    fun `initial state is empty`() = runTest {
        vm.uiState.test {
            val s = awaitItem()
            assertEquals("", s.email)
            assertEquals("", s.userName)
            assertFalse(s.isPremium)
            assertFalse(s.isLoading)
            assertFalse(s.isDeleted)
            assertFalse(s.showDeleteConfirmation)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `loadProfile populates state from api status`() = runTest {
        coEvery { api.getStatus("u@x.com") } returns StatusResponse(
            userEmail = "u@x.com",
            planId = "premium_monthly",
            isGeneratedEmail = false,
            isPremium = true,
            dailyQuota = 999,
            dailyUsed = 5,
        )

        vm.loadProfile()

        vm.uiState.test {
            val s = awaitItem()
            assertEquals("u@x.com", s.email)
            assertEquals("Prabhu", s.userName)
            assertTrue(s.isPremium)
            assertEquals("premium_monthly", s.planId)
            assertEquals(999, s.dailyQuota)
            assertEquals(5, s.dailyUsed)
            assertFalse(s.isLoading)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `loadProfile sets error on api failure`() = runTest {
        coEvery { api.getStatus(any()) } throws RuntimeException("network error")

        vm.loadProfile()

        vm.uiState.test {
            val s = awaitItem()
            assertNotNull(s.error)
            assertFalse(s.isLoading)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `loadProfile does nothing when no email`() = runTest {
        coEvery { prefs.getUserEmail() } returns null

        vm.loadProfile()

        coVerify(exactly = 0) { api.getStatus(any()) }
    }

    @Test
    fun `showDeleteConfirmation sets showDeleteConfirmation true`() = runTest {
        vm.showDeleteConfirmation()
        vm.uiState.test {
            assertTrue(awaitItem().showDeleteConfirmation)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `dismissDeleteConfirmation clears showDeleteConfirmation`() = runTest {
        vm.showDeleteConfirmation()
        vm.dismissDeleteConfirmation()
        vm.uiState.test {
            assertFalse(awaitItem().showDeleteConfirmation)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `confirmDeleteAccount calls api and clears prefs then sets isDeleted`() = runTest {
        vm.confirmDeleteAccount()

        coVerify { api.deleteAccount(match { it.userEmail == "u@x.com" && it.confirmation == "DELETE" }) }
        coVerify { prefs.clearAll() }
        vm.uiState.test {
            assertTrue(awaitItem().isDeleted)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `confirmDeleteAccount sets error on api failure`() = runTest {
        coEvery { api.deleteAccount(any<DeleteAccountRequest>()) } throws RuntimeException("api error")

        vm.confirmDeleteAccount()

        vm.uiState.test {
            val s = awaitItem()
            // Production code surfaces delete failures via deleteErrorMessage (rendered
            // inline in the delete-confirmation sheet — mirrors iOS ProfileView.swift:844-872).
            // The generic `error` field is reserved for non-delete flows (sign-out, profile
            // load, etc.) so the delete sheet can stay open with its own inline message.
            assertNotNull(s.deleteErrorMessage)
            assertFalse(s.isDeleted)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `confirmDeleteAccount does nothing when no email`() = runTest {
        coEvery { prefs.getUserEmail() } returns null

        vm.confirmDeleteAccount()

        coVerify(exactly = 0) { api.deleteAccount(any<DeleteAccountRequest>()) }
    }

    // ── toggleHistory ──────────────────────────────────────────────────────────

    @Test
    fun `toggleHistory updates historyEnabled`() = runTest {
        vm.toggleHistory(false)

        vm.uiState.test {
            assertFalse(awaitItem().historyEnabled)
            cancelAndIgnoreRemainingEvents()
        }
        coVerify { prefs.setHistoryEnabled(false) }
    }

    // ── loadProfile reads historyEnabled ───────────────────────────────────────

    @Test
    fun `loadProfile reads historyEnabled from prefs`() = runTest {
        coEvery { prefs.isHistoryEnabled() } returns false
        coEvery { api.getStatus("u@x.com") } returns StatusResponse(
            userEmail = "u@x.com",
            planId = "free_registered",
            isGeneratedEmail = false,
            isPremium = false,
        )

        vm.loadProfile()

        vm.uiState.test {
            assertFalse(awaitItem().historyEnabled)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── refreshAll ─────────────────────────────────────────────────────────────

    @Test
    fun `refreshAll calls api getStatus`() = runTest {
        coEvery { api.getStatus("u@x.com") } returns StatusResponse(
            userEmail = "u@x.com",
            planId = "free_registered",
            isGeneratedEmail = false,
            isPremium = false,
        )

        vm.refreshAll()

        coVerify { api.getStatus("u@x.com") }
    }

    // ── toggleAnalytics backed by api ──────────────────────────────────────────

    @Test
    fun `toggleAnalytics calls api updateAnalyticsConsent`() = runTest {
        coEvery { api.updateAnalyticsConsent(any()) } returns SuccessResponse(success = true)

        vm.toggleAnalytics(false)

        coVerify {
            api.updateAnalyticsConsent(match { req ->
                req.email == "u@x.com" && !req.consent
            })
        }
    }
}
