package com.destinyai.astrology.ui.auth

import app.cash.turbine.test
import com.destinyai.astrology.data.local.prefs.UserPreferences
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
        vm = WaitlistPendingViewModel(prefs)
    }

    @Test
    fun `initial state has empty email and isSignedOut false`() = runTest {
        vm.uiState.test {
            val state = awaitItem()
            assertEquals("", state.userEmail)
            assertFalse(state.isSignedOut)
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
}
