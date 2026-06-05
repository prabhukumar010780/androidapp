package com.destinyai.astrology.ui.auth

import app.cash.turbine.test
import com.destinyai.astrology.data.local.prefs.SecureStorage
import com.destinyai.astrology.data.local.prefs.UserPreferences
import com.destinyai.astrology.data.remote.StatusResponse
import com.destinyai.astrology.data.repository.ProfileRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@ExperimentalCoroutinesApi
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class WaitlistPendingViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var prefs: UserPreferences
    private lateinit var secure: SecureStorage
    private lateinit var profileRepository: ProfileRepository
    private lateinit var vm: WaitlistPendingViewModel

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
        prefs = mockk(relaxed = true)
        secure = mockk(relaxed = true)
        profileRepository = mockk(relaxed = true)
        vm = WaitlistPendingViewModel(prefs, secure, profileRepository)
    }

    @Test
    fun `initial state has empty email and isSignedOut false`() = runTest {
        vm.uiState.test {
            val state = awaitItem()
            assertEquals("", state.userEmail)
            assertFalse(state.isSignedOut)
            assertEquals("waitlist_pending", state.accessState)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `loadEmail populates email from prefs`() = runTest {
        coEvery { prefs.getUserEmail() } returns "prabhu@example.com"

        vm.loadEmail()

        vm.uiState.test {
            assertEquals("prabhu@example.com", awaitItem().userEmail)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `loadEmail uses empty string when prefs returns null`() = runTest {
        coEvery { prefs.getUserEmail() } returns null

        vm.loadEmail()

        vm.uiState.test {
            assertEquals("", awaitItem().userEmail)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `signOut sets isAuthenticated false`() = runTest {
        vm.signOut()

        coVerify { prefs.setAuthenticated(false) }
    }

    @Test
    fun `signOut resets lastAccessState to unknown`() = runTest {
        vm.signOut()

        coVerify { prefs.setLastAccessState("unknown") }
    }

    @Test
    fun `signOut sets isSignedOut true in state`() = runTest {
        vm.signOut()

        vm.uiState.test {
            assertTrue(awaitItem().isSignedOut)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `recheckOnce updates accessState when backend grants access`() = runTest {
        coEvery { prefs.isAuthenticated() } returns true
        coEvery { secure.getEmail() } returns "prabhu@example.com"
        coEvery { prefs.hasBirthData() } returns true
        coEvery { prefs.hasCompleteBirthProfile() } returns true
        coEvery { profileRepository.getUserStatus("prabhu@example.com") } returns
            StatusResponse(
                userEmail = "prabhu@example.com",
                planId = "free",
                isPremium = false,
                accessState = "granted",
            )

        vm.recheckOnce()

        vm.uiState.test {
            val state = awaitItem()
            assertEquals("granted", state.accessState)
            assertTrue(state.hasBirthDataOnAccess)
            cancelAndIgnoreRemainingEvents()
        }
        coVerify { prefs.setLastAccessState("granted") }
        coVerify { prefs.setAccessState("granted") }
    }

    @Test
    fun `recheckOnce keeps accessState unchanged on network failure`() = runTest {
        coEvery { prefs.isAuthenticated() } returns true
        coEvery { secure.getEmail() } returns "prabhu@example.com"
        coEvery { profileRepository.getUserStatus(any()) } throws RuntimeException("offline")

        vm.recheckOnce()

        vm.uiState.test {
            assertEquals("waitlist_pending", awaitItem().accessState)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `recheckOnce is a no-op when not authenticated`() = runTest {
        coEvery { prefs.isAuthenticated() } returns false

        vm.recheckOnce()

        coVerify(exactly = 0) { profileRepository.getUserStatus(any()) }
    }
}
