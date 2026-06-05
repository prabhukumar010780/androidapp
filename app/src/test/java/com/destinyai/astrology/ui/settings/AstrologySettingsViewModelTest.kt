package com.destinyai.astrology.ui.settings

import app.cash.turbine.test
import com.destinyai.astrology.data.local.prefs.UserPreferences
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
class AstrologySettingsViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var prefs: UserPreferences
    private lateinit var vm: AstrologySettingsViewModel

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
        coEvery { prefs.getAyanamsa() } returns "lahiri"
        coEvery { prefs.getHouseSystem() } returns "whole_sign"
        coEvery { prefs.getChartStyle() } returns "north_indian"
        vm = AstrologySettingsViewModel(prefs)
    }

    // ── Initial state ──────────────────────────────────────────────────────────

    @Test
    fun `initial state has defaults from prefs`() = runTest {
        vm.uiState.test {
            val s = awaitItem()
            assertEquals("lahiri", s.ayanamsa)
            assertEquals("whole_sign", s.houseSystem)
            assertFalse(s.isLoading)
            assertFalse(s.isSaved)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── load ───────────────────────────────────────────────────────────────────

    @Test
    fun `load reads ayanamsa and houseSystem from prefs`() = runTest {
        coEvery { prefs.getAyanamsa() } returns "raman"
        coEvery { prefs.getHouseSystem() } returns "equal_houses"

        vm.load()

        vm.uiState.test {
            val s = awaitItem()
            assertEquals("raman", s.ayanamsa)
            assertEquals("equal_houses", s.houseSystem)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `load reads chart style from prefs`() = runTest {
        coEvery { prefs.getChartStyle() } returns "south_indian"

        vm.load()

        vm.uiState.test {
            assertEquals("south_indian", awaitItem().chartStyle)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── setters ────────────────────────────────────────────────────────────────

    @Test
    fun `setAyanamsa updates state and clears isSaved`() = runTest {
        vm.save()  // set isSaved = true first via save
        vm.setAyanamsa("krishnamurti")
        vm.uiState.test {
            val s = awaitItem()
            assertEquals("krishnamurti", s.ayanamsa)
            assertFalse(s.isSaved)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setHouseSystem updates state and clears isSaved`() = runTest {
        vm.setHouseSystem("placidus")
        vm.uiState.test {
            assertEquals("placidus", awaitItem().houseSystem)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setChartStyle updates state`() = runTest {
        vm.setChartStyle("south")
        vm.uiState.test {
            assertEquals("south", awaitItem().chartStyle)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── save ───────────────────────────────────────────────────────────────────

    @Test
    fun `save persists ayanamsa to prefs`() = runTest {
        vm.setAyanamsa("raman")
        vm.save()
        coVerify { prefs.saveAyanamsa("raman") }
    }

    @Test
    fun `save persists houseSystem to prefs`() = runTest {
        vm.setHouseSystem("placidus")
        vm.save()
        coVerify { prefs.saveHouseSystem("placidus") }
    }

    @Test
    fun `save persists chartStyle to prefs`() = runTest {
        vm.setChartStyle("south")
        vm.save()
        coVerify { prefs.setChartStyle("south") }
    }

    @Test
    fun `save sets isSaved true on success`() = runTest {
        vm.save()
        vm.uiState.test {
            assertTrue(awaitItem().isSaved)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `save sets error when prefs throws`() = runTest {
        coEvery { prefs.saveAyanamsa(any()) } throws RuntimeException("disk error")
        vm.save()
        vm.uiState.test {
            val s = awaitItem()
            assertNotNull(s.error)
            assertFalse(s.isLoading)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
