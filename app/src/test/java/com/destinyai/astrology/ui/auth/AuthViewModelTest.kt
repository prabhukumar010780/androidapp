package com.destinyai.astrology.ui.auth

import app.cash.turbine.test
import android.content.Context
import com.destinyai.astrology.data.local.prefs.UserPreferences
import com.destinyai.astrology.data.repository.AuthRepository
import com.destinyai.astrology.domain.model.User
import com.destinyai.astrology.services.AppStartupService
import com.destinyai.astrology.services.HapticManager
import com.destinyai.astrology.services.SoundManager
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*

/**
 * Unit tests for AuthViewModel.
 * Mirrors: iOS AuthViewModelTests.swift
 */
@ExperimentalCoroutinesApi
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AuthViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var repository: AuthRepository
    private lateinit var haptic: HapticManager
    private lateinit var prefs: UserPreferences
    private lateinit var appStartup: AppStartupService
    private lateinit var soundManager: SoundManager
    private lateinit var loginSync: com.destinyai.astrology.services.LoginSyncCoordinator
    private lateinit var context: Context
    private lateinit var viewModel: AuthViewModel

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
        repository = mockk(relaxed = true)
        haptic = mockk(relaxed = true)
        prefs = mockk(relaxed = true)
        appStartup = mockk(relaxed = true)
        soundManager = mockk(relaxed = true)
        loginSync = mockk(relaxed = true)
        context = mockk(relaxed = true)
        coEvery { prefs.isSoundEnabled() } returns true
        // Stub the flows that AuthViewModel.init() collects/reads — relaxed mocks return
        // Nothing for Flow/StateFlow getters, which crashes the init coroutines before
        // any test can run (kotlinx.coroutines.test.UncaughtExceptionsBeforeTest).
        every { prefs.isSoundEnabledFlow() } returns flowOf(true)
        every { appStartup.allowGuest } returns MutableStateFlow(true)
        every { appStartup.gateMode } returns MutableStateFlow("off")
        viewModel = AuthViewModel(repository, haptic, prefs, appStartup, soundManager, loginSync, context)
    }

    // --- Session state ---

    @Test
    fun `init loads saved session from keystore`() = runTest {
        val savedUser = User(email = "test@example.com", isGuestEmail = false)
        coEvery { repository.getSavedUser() } returns savedUser

        val vm = AuthViewModel(repository, haptic, prefs, appStartup, soundManager, loginSync, context)

        vm.uiState.test {
            val state = awaitItem()
            assertEquals(savedUser.email, state.currentUser?.email)
        }
    }

    @Test
    fun `unauthenticated state shown when no saved session`() = runTest {
        coEvery { repository.getSavedUser() } returns null

        val vm = AuthViewModel(repository, haptic, prefs, appStartup, soundManager, loginSync, context)

        vm.uiState.test {
            val state = awaitItem()
            assertNull(state.currentUser)
            assertFalse(state.isAuthenticated)
        }
    }

    // --- Google Sign-In ---

    @Test
    fun `signInWithGoogle calls repository and updates state on success`() = runTest {
        val user = User(email = "google@example.com", isGuestEmail = false, googleId = "gid_abc")
        coEvery { repository.signInWithGoogle(any(), any(), any(), any()) } returns Result.success(user)

        viewModel.signInWithGoogle(
            email = "google@example.com",
            googleId = "gid_abc",
            name = "G User",
            idToken = "valid-id-token",
        )

        coVerify(exactly = 1) {
            repository.signInWithGoogle("google@example.com", "gid_abc", "G User", "valid-id-token")
        }
        viewModel.uiState.test {
            val state = awaitItem()
            assertTrue(state.isAuthenticated)
            assertEquals(user.email, state.currentUser?.email)
            assertFalse(state.isLoading)
        }
    }

    @Test
    fun `google sign-in success sets authenticated state`() = runTest {
        val user = User(email = "prabhu@gmail.com", isGuestEmail = false, googleId = "gid123")
        coEvery { repository.signInWithGoogle(any(), any(), any(), any()) } returns Result.success(user)

        viewModel.signInWithGoogle(
            email = "prabhu@gmail.com",
            googleId = "gid123",
            name = null,
            idToken = "google-id-token",
        )

        viewModel.uiState.test {
            val state = awaitItem()
            assertTrue(state.isAuthenticated)
            assertEquals(user.email, state.currentUser?.email)
        }
    }

    @Test
    fun `google sign-in failure sets error state`() = runTest {
        coEvery { repository.signInWithGoogle(any(), any(), any(), any()) } returns Result.failure(Exception("Network error"))

        viewModel.signInWithGoogle(
            email = "x@y.z",
            googleId = "g-1",
            name = null,
            idToken = "bad-token",
        )

        viewModel.uiState.test {
            val state = awaitItem()
            assertFalse(state.isAuthenticated)
            assertNotNull(state.error)
        }
    }

    // --- Guest signup ---

    @Test
    fun `guest signup generates email and registers`() = runTest {
        val guestUser = User(email = "guest.abc123@destinyai.app", isGuestEmail = true)
        coEvery { repository.registerGuest() } returns Result.success(guestUser)

        viewModel.continueAsGuest()

        viewModel.uiState.test {
            val state = awaitItem()
            assertTrue(state.isAuthenticated)
            assertTrue(state.currentUser?.isGuestEmail == true)
        }
    }

    @Test
    fun `guest signup failure shows error`() = runTest {
        coEvery { repository.registerGuest() } returns Result.failure(Exception("Server error"))

        viewModel.continueAsGuest()

        viewModel.uiState.test {
            val state = awaitItem()
            assertFalse(state.isAuthenticated)
            assertNotNull(state.error)
        }
    }

    // --- Upgrade ---

    @Test
    fun `upgrade guest to registered migrates history`() = runTest {
        val upgradedUser = User(email = "real@gmail.com", isGuestEmail = false)
        coEvery { repository.upgradeGuest(any(), any()) } returns Result.success(upgradedUser)

        viewModel.upgradeGuest(guestEmail = "guest@app.com", newEmail = "real@gmail.com")

        viewModel.uiState.test {
            val state = awaitItem()
            assertFalse(state.currentUser?.isGuestEmail ?: true)
            assertEquals("real@gmail.com", state.currentUser?.email)
        }
    }

    @Test
    fun `upgrade fails on 409 conflict shows merge dialog`() = runTest {
        coEvery { repository.upgradeGuest(any(), any()) } returns
            Result.failure(ConflictException(code = "birth_data_taken"))

        viewModel.upgradeGuest(guestEmail = "guest@app.com", newEmail = "existing@gmail.com")

        viewModel.uiState.test {
            val state = awaitItem()
            assertTrue(state.showMergeDialog)
        }
    }

    // --- Logout ---

    @Test
    fun `logout clears keystore, room, datastore`() = runTest {
        viewModel.logout()

        coVerify { repository.clearSession() }
        viewModel.uiState.test {
            val state = awaitItem()
            assertNull(state.currentUser)
            assertFalse(state.isAuthenticated)
        }
    }

    // --- Deleted account ---

    @Test
    fun `403 account_deleted on any call forces logout`() = runTest {
        coEvery { repository.getSavedUser() } throws AccountDeletedException()

        val vm = AuthViewModel(repository, haptic, prefs, appStartup, soundManager, loginSync, context)

        vm.uiState.test {
            val state = awaitItem()
            assertTrue(state.forceLogout)
        }
    }

    // --- R2-A10 Haptic on success ---

    @Test
    fun `signInWithGoogle calls haptic success on success`() = runTest {
        val user = User(email = "haptic@test.com", isGuestEmail = false)
        coEvery { repository.signInWithGoogle(any(), any(), any(), any()) } returns Result.success(user)

        viewModel.signInWithGoogle(
            email = "haptic@test.com",
            googleId = "gid-h",
            name = null,
            idToken = "test-token",
        )

        verify(exactly = 1) { haptic.success() }
    }
}
