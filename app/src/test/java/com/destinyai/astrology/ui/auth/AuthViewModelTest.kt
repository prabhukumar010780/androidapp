package com.destinyai.astrology.ui.auth

import app.cash.turbine.test
import com.destinyai.astrology.data.repository.AuthRepository
import com.destinyai.astrology.domain.model.User
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*

/**
 * TDD test shell for AuthViewModel.
 * These tests define the contract. All fail until AuthViewModel is implemented.
 *
 * Mirrors: iOS AuthViewModelTests.swift
 */
@ExperimentalCoroutinesApi
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AuthViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var repository: AuthRepository
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
        viewModel = AuthViewModel(repository)
    }

    // --- Session state ---

    @Test
    fun `init loads saved session from keystore`() = runTest {
        val savedUser = User(email = "test@example.com", isGuestEmail = false)
        coEvery { repository.getSavedUser() } returns savedUser

        val vm = AuthViewModel(repository)

        vm.uiState.test {
            val state = awaitItem()
            assertEquals(savedUser.email, state.currentUser?.email)
        }
    }

    @Test
    fun `unauthenticated state shown when no saved session`() = runTest {
        coEvery { repository.getSavedUser() } returns null

        val vm = AuthViewModel(repository)

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
        coEvery { repository.signInWithGoogle("valid-id-token") } returns Result.success(user)

        viewModel.signInWithGoogle("valid-id-token")

        coVerify(exactly = 1) { repository.signInWithGoogle("valid-id-token") }
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
        coEvery { repository.signInWithGoogle(any()) } returns Result.success(user)

        viewModel.signInWithGoogle("google-id-token")

        viewModel.uiState.test {
            val state = awaitItem()
            assertTrue(state.isAuthenticated)
            assertEquals(user.email, state.currentUser?.email)
        }
    }

    @Test
    fun `google sign-in failure sets error state`() = runTest {
        coEvery { repository.signInWithGoogle(any()) } returns Result.failure(Exception("Network error"))

        viewModel.signInWithGoogle("bad-token")

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

        val vm = AuthViewModel(repository)

        vm.uiState.test {
            val state = awaitItem()
            assertTrue(state.forceLogout)
        }
    }
}
