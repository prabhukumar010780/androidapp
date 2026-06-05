package com.destinyai.astrology.ui.splash

import app.cash.turbine.test
import com.destinyai.astrology.data.local.prefs.SecureStorage
import com.destinyai.astrology.data.local.prefs.UserPreferences
import com.destinyai.astrology.data.remote.AstroApiService
import com.destinyai.astrology.services.AppStartupService
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SplashViewModelTest {

    private lateinit var prefs: UserPreferences
    private lateinit var secure: SecureStorage
    private lateinit var api: AstroApiService
    private lateinit var appStartup: AppStartupService
    private lateinit var vm: SplashViewModel

    @BeforeEach
    fun setUp() {
        prefs = mockk(relaxed = true)
        secure = mockk(relaxed = true)
        api = mockk(relaxed = true)
        appStartup = mockk(relaxed = true)
    }

    private fun buildVm() = SplashViewModel(prefs, secure, api, appStartup)

    // ── Initial state ─────────────────────────────────────────────────────────

    @Test
    fun `initial state is Splash`() = runTest {
        coEvery { prefs.getUserEmail() } returns null
        vm = buildVm()

        vm.uiState.test {
            assertEquals(SplashDestination.Splash, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── Navigation routing (no delay in tests — resolve() is called directly) ─

    @Test
    fun `resolveDestination returns LanguageSelection when language not set`() = runTest {
        coEvery { prefs.hasCompletedLanguageSelection() } returns false
        coEvery { prefs.hasSeenOnboarding() } returns false
        coEvery { prefs.isAuthenticated() } returns false
        vm = buildVm()

        assertEquals(SplashDestination.LanguageSelection, vm.resolveDestination())
    }

    @Test
    fun `resolveDestination returns Onboarding when language done but no onboarding`() = runTest {
        coEvery { prefs.hasCompletedLanguageSelection() } returns true
        coEvery { prefs.hasSeenOnboarding() } returns false
        coEvery { prefs.isAuthenticated() } returns false
        vm = buildVm()

        assertEquals(SplashDestination.Onboarding, vm.resolveDestination())
    }

    @Test
    fun `resolveDestination returns Auth when onboarding done but not authenticated`() = runTest {
        coEvery { prefs.hasCompletedLanguageSelection() } returns true
        coEvery { prefs.hasSeenOnboarding() } returns true
        coEvery { prefs.isAuthenticated() } returns false
        vm = buildVm()

        assertEquals(SplashDestination.Auth, vm.resolveDestination())
    }

    @Test
    fun `resolveDestination returns WaitlistPending when access state is waitlist_pending`() = runTest {
        coEvery { prefs.hasCompletedLanguageSelection() } returns true
        coEvery { prefs.hasSeenOnboarding() } returns true
        coEvery { prefs.isAuthenticated() } returns true
        coEvery { prefs.getLastAccessState() } returns "waitlist_pending"
        vm = buildVm()

        assertEquals(SplashDestination.WaitlistPending, vm.resolveDestination())
    }

    @Test
    fun `resolveDestination returns BirthData when authenticated but no birth data`() = runTest {
        coEvery { prefs.hasCompletedLanguageSelection() } returns true
        coEvery { prefs.hasSeenOnboarding() } returns true
        coEvery { prefs.isAuthenticated() } returns true
        coEvery { prefs.getLastAccessState() } returns "granted"
        coEvery { prefs.hasBirthData() } returns false
        vm = buildVm()

        assertEquals(SplashDestination.BirthData, vm.resolveDestination())
    }

    @Test
    fun `resolveDestination returns BirthData for guest without birth data`() = runTest {
        coEvery { prefs.hasCompletedLanguageSelection() } returns true
        coEvery { prefs.hasSeenOnboarding() } returns true
        coEvery { prefs.isAuthenticated() } returns true
        coEvery { prefs.getLastAccessState() } returns "granted"
        coEvery { prefs.hasBirthData() } returns false
        coEvery { prefs.isGuestUser() } returns true
        vm = buildVm()

        assertEquals(SplashDestination.BirthData, vm.resolveDestination())
    }

    @Test
    fun `resolveDestination returns Main when fully onboarded`() = runTest {
        coEvery { prefs.hasCompletedLanguageSelection() } returns true
        coEvery { prefs.hasSeenOnboarding() } returns true
        coEvery { prefs.isAuthenticated() } returns true
        coEvery { prefs.getLastAccessState() } returns "granted"
        coEvery { prefs.hasBirthData() } returns true
        coEvery { prefs.isGuestUser() } returns false
        vm = buildVm()

        assertEquals(SplashDestination.Main, vm.resolveDestination())
    }

    // ── navigate() emits the resolved destination ──────────────────────────────

    @Test
    fun `navigate emits Main for fully onboarded user`() = runTest {
        coEvery { prefs.hasCompletedLanguageSelection() } returns true
        coEvery { prefs.hasSeenOnboarding() } returns true
        coEvery { prefs.isAuthenticated() } returns true
        coEvery { prefs.getLastAccessState() } returns "granted"
        coEvery { prefs.hasBirthData() } returns true
        coEvery { prefs.isGuestUser() } returns false
        vm = buildVm()

        vm.uiState.test {
            assertEquals(SplashDestination.Splash, awaitItem())  // initial
            vm.navigate()
            assertEquals(SplashDestination.Main, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `navigate emits Auth for fresh install`() = runTest {
        coEvery { prefs.hasCompletedLanguageSelection() } returns true
        coEvery { prefs.hasSeenOnboarding() } returns true
        coEvery { prefs.isAuthenticated() } returns false
        vm = buildVm()

        vm.uiState.test {
            awaitItem()  // Splash
            vm.navigate()
            assertEquals(SplashDestination.Auth, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
