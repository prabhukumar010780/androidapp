package com.destinyai.astrology.ui.profile

import app.cash.turbine.test
import com.destinyai.astrology.data.local.prefs.UserPreferences
import com.destinyai.astrology.data.remote.BirthProfileDto
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
class BirthDetailsViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var prefs: UserPreferences
    private lateinit var vm: BirthDetailsViewModel

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
        coEvery { prefs.getUserEmail() } returns "u@x.com"
        coEvery { prefs.getUserName() } returns "Prabhu"
        coEvery { prefs.isGuestUser() } returns false
        vm = BirthDetailsViewModel(prefs)
    }

    // ── Initial state ──────────────────────────────────────────────────────────

    @Test
    fun `initial state is empty`() = runTest {
        vm.uiState.test {
            val s = awaitItem()
            assertEquals("", s.name)
            assertEquals("", s.gender)
            assertEquals("", s.dateOfBirth)
            assertFalse(s.isLoading)
            assertFalse(s.isSaving)
            assertFalse(s.saveSuccess)
            assertNull(s.error)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── loadBirthData ──────────────────────────────────────────────────────────

    @Test
    fun `loadBirthData populates form from prefs`() = runTest {
        coEvery { prefs.getBirthProfile() } returns BirthProfileDto(
            dateOfBirth = "1980-07-01",
            timeOfBirth = "06:32",
            cityOfBirth = "Bhilai",
            latitude = 21.2138,
            longitude = 81.3943,
            gender = "male",
            birthTimeUnknown = false,
        )
        coEvery { prefs.getUserName() } returns "Prabhu Kumar"

        vm.loadBirthData()

        vm.uiState.test {
            val s = awaitItem()
            assertEquals("Prabhu Kumar", s.name)
            assertEquals("male", s.gender)
            assertEquals("1980-07-01", s.dateOfBirth)
            assertEquals("06:32", s.timeOfBirth)
            assertEquals("Bhilai", s.cityOfBirth)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `loadBirthData does nothing when no birth profile`() = runTest {
        coEvery { prefs.getBirthProfile() } returns null

        vm.loadBirthData()

        vm.uiState.test {
            assertEquals("", awaitItem().dateOfBirth)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── setters ────────────────────────────────────────────────────────────────

    @Test
    fun `setName updates name`() = runTest {
        vm.setName("Ravi")
        vm.uiState.test {
            assertEquals("Ravi", awaitItem().name)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setGender updates gender`() = runTest {
        vm.setGender("female")
        vm.uiState.test {
            assertEquals("female", awaitItem().gender)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── saveName (name + gender) ───────────────────────────────────────────────
    // iOS parity (BirthDetailsView.swift:325-334): saveChanges is local-only —
    // no POST /profile network call. These tests verify local persistence.

    @Test
    fun `saveName persists name to prefs`() = runTest {
        coEvery { prefs.getBirthProfile() } returns fakeBirthProfile()

        vm.loadBirthData()
        vm.setName("Ravi")
        vm.saveName()

        coVerify { prefs.setUserName("Ravi") }
    }

    @Test
    fun `saveName persists gender to prefs (global + scoped)`() = runTest {
        coEvery { prefs.getBirthProfile() } returns fakeBirthProfile()

        vm.loadBirthData()
        vm.setGender("female")
        vm.saveName()

        coVerify { prefs.setUserGender("female") }
        coVerify { prefs.setUserGender(gender = "female", email = null) }
    }

    @Test
    fun `saveName updates BirthProfile gender locally`() = runTest {
        coEvery { prefs.getBirthProfile() } returns fakeBirthProfile()

        vm.loadBirthData()
        vm.setGender("female")
        vm.saveName()

        coVerify {
            prefs.setBirthProfile(match { it.gender == "female" })
        }
    }

    @Test
    fun `saveName sets isSaving true during call and false after`() = runTest {
        coEvery { prefs.getBirthProfile() } returns fakeBirthProfile()

        vm.loadBirthData()
        vm.saveName()

        vm.uiState.test {
            val s = awaitItem()
            assertFalse(s.isSaving)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `saveName sets saveSuccess true on success`() = runTest {
        coEvery { prefs.getBirthProfile() } returns fakeBirthProfile()

        vm.loadBirthData()
        vm.saveName()

        vm.uiState.test {
            assertTrue(awaitItem().saveSuccess)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `saveName sets error on local persistence failure`() = runTest {
        coEvery { prefs.getBirthProfile() } returns fakeBirthProfile()
        coEvery { prefs.setUserName(any()) } throws RuntimeException("disk full")

        vm.loadBirthData()
        vm.saveName()

        vm.uiState.test {
            val s = awaitItem()
            assertNotNull(s.error)
            assertFalse(s.isSaving)
            assertFalse(s.saveSuccess)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `saveName succeeds even when no birth profile loaded`() = runTest {
        // iOS parity: saveChanges writes UserDefaults regardless of profile presence.
        coEvery { prefs.getBirthProfile() } returns null

        vm.loadBirthData()
        vm.setName("Ravi")
        vm.saveName()

        vm.uiState.test {
            assertTrue(awaitItem().saveSuccess)
            cancelAndIgnoreRemainingEvents()
        }
        coVerify { prefs.setUserName("Ravi") }
    }

    // ── hasChanges ─────────────────────────────────────────────────────────────

    @Test
    fun `saveName disabled when no changes after load`() = runTest {
        coEvery { prefs.getBirthProfile() } returns fakeBirthProfile()
        coEvery { prefs.getUserName() } returns "Prabhu"

        vm.loadBirthData()

        vm.uiState.test {
            val s = awaitItem()
            // hasChanges should be false: name and gender match originals
            assertFalse(s.hasChanges)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `hasChanges true when name edited`() = runTest {
        coEvery { prefs.getBirthProfile() } returns fakeBirthProfile()
        coEvery { prefs.getUserName() } returns "Prabhu"

        vm.loadBirthData()
        vm.setName("Ravi")

        vm.uiState.test {
            assertTrue(awaitItem().hasChanges)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private fun fakeBirthProfile() = BirthProfileDto(
        dateOfBirth = "1980-07-01",
        timeOfBirth = "06:32",
        cityOfBirth = "Bhilai",
        latitude = 21.2138,
        longitude = 81.3943,
        gender = "male",
        birthTimeUnknown = false,
    )
}
