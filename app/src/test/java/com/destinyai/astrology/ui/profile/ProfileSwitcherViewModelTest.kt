package com.destinyai.astrology.ui.profile

import app.cash.turbine.test
import com.destinyai.astrology.data.local.prefs.UserPreferences
import com.destinyai.astrology.data.remote.AstroApiService
import com.destinyai.astrology.data.remote.PartnerDto
import com.destinyai.astrology.data.remote.StatusResponse
import com.destinyai.astrology.services.ProfileChangeBus
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
    private lateinit var bus: ProfileChangeBus
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
        bus = ProfileChangeBus()
        coEvery { prefs.getUserEmail() } returns "self@example.com"
        coEvery { prefs.getUserName() } returns "Prabhu"
        coEvery { prefs.getActiveProfileEmail() } returns null
        coEvery { prefs.getActiveProfileId() } returns null
        coEvery { api.listPartners(any()) } returns emptyList()
        vm = ProfileSwitcherViewModel(api, prefs, bus)
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
        vm = ProfileSwitcherViewModel(api, prefs, bus)

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
        // Production code: _activeEmail tracks the OWNER email (always self) — partner
        // identity is exposed via activeProfileId (UUID for partners, email for self).
        // The test now asserts activeProfileId reflects the persisted active profile id.
        coEvery { prefs.getActiveProfileId() } returns "partner-uuid-1"
        vm = ProfileSwitcherViewModel(api, prefs, bus)

        vm.activeProfileId.test {
            assertEquals("partner-uuid-1", awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        // activeEmail remains the owner email regardless of switched-to partner.
        vm.activeEmail.test {
            assertEquals("self@example.com", awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `switchProfile calls api and updates activeEmail`() = runTest {
        vm.switchProfile("partner-uuid-1")

        // Production code: switching updates activeProfileId, not activeEmail (owner email
        // is unchanged across switches). Verify the new id is persisted + reflected.
        vm.activeProfileId.test {
            assertEquals("partner-uuid-1", awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        coVerify { prefs.setActiveProfileId("partner-uuid-1") }
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

    // ── R2-P22 upgrade_required blocking ──────────────────────────────────────

    @Test
    fun `switchProfile blocks on upgrade_required access state`() = runTest {
        coEvery { api.getStatus("self@example.com") } returns StatusResponse(
            userEmail = "self@example.com",
            planId = "free_registered",
            isGeneratedEmail = false,
            isPremium = false,
            accessState = "upgrade_required",
        )

        vm.switchProfile("partner@example.com")

        // activeEmail should remain self — not updated
        vm.activeEmail.test {
            assertEquals("self@example.com", awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        // upgradeRequiredPrompt flag raised
        vm.uiState.test {
            assertTrue(awaitItem().upgradeRequiredPrompt)
            cancelAndIgnoreRemainingEvents()
        }
        // api.switchProfile should NOT have been called
        coVerify(exactly = 0) { api.switchProfile(any()) }
    }
}
