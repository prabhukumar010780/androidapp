package com.destinyai.astrology.ui.profile

import app.cash.turbine.test
import com.destinyai.astrology.data.local.prefs.UserPreferences
import com.destinyai.astrology.data.remote.AstroApiService
import com.destinyai.astrology.data.remote.PartnerDto
import io.mockk.coEvery
import io.mockk.coVerify
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
class ProfileSwitcherViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var api: AstroApiService
    private lateinit var prefs: UserPreferences
    private lateinit var vm: ProfileSwitcherViewModel

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
        coEvery { prefs.getUserEmail() } returns "self@example.com"
        coEvery { prefs.getUserName() } returns "Prabhu"
        coEvery { prefs.getActiveProfileEmail() } returns null
        coEvery { api.listPartners(any()) } returns emptyList()
        vm = ProfileSwitcherViewModel(api, prefs)
    }

    @Test
    fun `initial profiles contains self entry`() = runTest {
        vm.profiles.test {
            val profiles = awaitItem()
            assertEquals(1, profiles.size)
            assertTrue(profiles[0].isSelf)
            assertEquals("self@example.com", profiles[0].email)
            assertEquals("Prabhu", profiles[0].name)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `profiles include partner entries from api`() = runTest {
        coEvery { api.listPartners("self@example.com") } returns listOf(
            PartnerDto(
                id = "partner-1",
                name = "Anita",
                dateOfBirth = "1990-01-01",
                timeOfBirth = "10:00",
                cityOfBirth = "Delhi",
                latitude = 28.6139,
                longitude = 77.2090,
            ),
        )
        vm = ProfileSwitcherViewModel(api, prefs)

        vm.profiles.test {
            val profiles = awaitItem()
            assertEquals(2, profiles.size)
            assertFalse(profiles[1].isSelf)
            assertEquals("Anita", profiles[1].name)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `activeEmail defaults to self when no active profile email saved`() = runTest {
        vm.activeEmail.test {
            assertEquals("self@example.com", awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `activeEmail uses stored active profile email`() = runTest {
        coEvery { prefs.getActiveProfileEmail() } returns "partner@example.com"
        vm = ProfileSwitcherViewModel(api, prefs)

        vm.activeEmail.test {
            assertEquals("partner@example.com", awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `switchProfile calls api and updates activeEmail`() = runTest {
        vm.switchProfile("partner@example.com")

        vm.activeEmail.test {
            assertEquals("partner@example.com", awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        coVerify { prefs.setActiveProfileEmail("partner@example.com") }
    }

    @Test
    fun `switchProfile silently ignores api failure`() = runTest {
        coEvery {
            api.switchProfile(any())
        } throws RuntimeException("network error")

        vm.switchProfile("partner@example.com")

        // activeEmail should remain unchanged (self) on failure
        vm.activeEmail.test {
            // activeEmail starts as self (null stored → self), stays self on failure
            assertEquals("self@example.com", awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
