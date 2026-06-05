package com.destinyai.astrology.ui.auth

import android.content.Context
import app.cash.turbine.test
import com.destinyai.astrology.data.local.prefs.UserPreferences
import com.destinyai.astrology.data.remote.BirthProfileDto
import com.destinyai.astrology.data.remote.ProfileResponse
import com.destinyai.astrology.data.repository.AuthRepository
import com.destinyai.astrology.domain.model.User
import com.destinyai.astrology.services.AppStartupService
import com.destinyai.astrology.services.HapticManager
import com.destinyai.astrology.services.SoundManager
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

/**
 * Verifies the post-auth `needsBirthData` flag drives the AuthScreen branch
 * between MainScreen and BirthDataScreen. Mirrors iOS AuthViewModel +
 * fetchAndRestoreProfile + guestNeedsBirthData rules.
 */
@ExperimentalCoroutinesApi
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AuthViewModelNeedsBirthDataTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var repository: AuthRepository
    private lateinit var haptic: HapticManager
    private lateinit var prefs: UserPreferences
    private lateinit var appStartup: AppStartupService
    private lateinit var soundManager: SoundManager
    private lateinit var context: Context

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
        appStartup = mockk(relaxed = true) {
            every { allowGuest } returns MutableStateFlow(true)
            every { gateMode } returns MutableStateFlow("off")
        }
        soundManager = mockk(relaxed = true)
        context = mockk(relaxed = true) {
            every { getString(any()) } returns "Sign in failed"
        }
        coEvery { prefs.isSoundEnabled() } returns false
        every { prefs.isSoundEnabledFlow() } returns flowOf(false)
        coEvery { repository.getSavedUser() } returns null
    }

    private fun newViewModel(): AuthViewModel =
        AuthViewModel(repository, haptic, prefs, appStartup, soundManager, context)

    private fun sampleBirthProfile(): BirthProfileDto = BirthProfileDto(
        dateOfBirth = "1990-05-15",
        timeOfBirth = "08:30",
        cityOfBirth = "New York",
        latitude = 40.7128,
        longitude = -74.0060,
        gender = "male",
        birthTimeUnknown = false,
    )

    @Test
    fun `registered google sign-in with server birth profile sets needsBirthData=false`() = runTest {
        val user = User(email = "u@example.com", isGuestEmail = false)
        coEvery { prefs.isGuestUser() } returns false
        coEvery { repository.signInWithGoogle(any(), any(), any(), any()) } returns Result.success(user)
        coEvery { repository.fetchProfile("u@example.com") } returns ProfileResponse(
            userEmail = user.email,
            birthProfile = sampleBirthProfile(),
        )

        val vm = newViewModel()
        vm.signInWithGoogle(
            email = "u@example.com",
            googleId = "gid-1",
            name = null,
            idToken = "token",
        )

        vm.uiState.test {
            val state = awaitItem()
            assertTrue(state.isAuthenticated)
            assertFalse(state.needsBirthData)
        }
    }

    @Test
    fun `registered google sign-in with no server birth profile and no local sets needsBirthData=true`() = runTest {
        val user = User(email = "new@example.com", isGuestEmail = false)
        coEvery { prefs.isGuestUser() } returns false
        coEvery { prefs.getBirthProfile() } returns null
        coEvery { repository.signInWithGoogle(any(), any(), any(), any()) } returns Result.success(user)
        coEvery { repository.fetchProfile("new@example.com") } returns ProfileResponse(
            userEmail = user.email,
            birthProfile = null,
        )

        val vm = newViewModel()
        vm.signInWithGoogle(
            email = "new@example.com",
            googleId = "gid-2",
            name = null,
            idToken = "token",
        )

        vm.uiState.test {
            val state = awaitItem()
            assertTrue(state.isAuthenticated)
            assertTrue(state.needsBirthData)
        }
    }

    @Test
    fun `guest with no local birth pref sets needsBirthData=true`() = runTest {
        val guest = User(email = "guest_x@destinyai.app", isGuestEmail = true)
        coEvery { prefs.getBirthProfile() } returns null
        coEvery { repository.registerGuest() } returns Result.success(guest)

        val vm = newViewModel()
        vm.continueAsGuest()

        vm.uiState.test {
            val state = awaitItem()
            assertTrue(state.isAuthenticated)
            assertTrue(state.needsBirthData)
        }
    }

    @Test
    fun `guest with existing local birth pref sets needsBirthData=false`() = runTest {
        val guest = User(email = "guest_y@destinyai.app", isGuestEmail = true)
        coEvery { prefs.getBirthProfile() } returns sampleBirthProfile()
        coEvery { repository.registerGuest() } returns Result.success(guest)

        val vm = newViewModel()
        vm.continueAsGuest()

        vm.uiState.test {
            val state = awaitItem()
            assertTrue(state.isAuthenticated)
            assertFalse(state.needsBirthData)
        }
    }

    @Test
    fun `guest upgrade carry-forward keeps needsBirthData=false when local birth present`() = runTest {
        val upgraded = User(email = "real@example.com", isGuestEmail = false)
        coEvery { prefs.getBirthProfile() } returns sampleBirthProfile()
        coEvery { repository.upgradeGuest(any(), any()) } returns Result.success(upgraded)

        val vm = newViewModel()
        vm.upgradeGuest(guestEmail = "guest_z@destinyai.app", newEmail = "real@example.com")

        vm.uiState.test {
            val state = awaitItem()
            assertTrue(state.isAuthenticated)
            assertFalse(state.needsBirthData)
        }
    }
}
