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

    // ── setters (iOS @AppStorage parity — each setter persists immediately) ────

    @Test
    fun `setAyanamsa updates state and persists to prefs`() = runTest {
        vm.setAyanamsa("krishnamurti")
        vm.uiState.test {
            assertEquals("krishnamurti", awaitItem().ayanamsa)
            cancelAndIgnoreRemainingEvents()
        }
        coVerify { prefs.saveAyanamsa("krishnamurti") }
    }

    @Test
    fun `setHouseSystem updates state and persists to prefs`() = runTest {
        vm.setHouseSystem("placidus")
        vm.uiState.test {
            assertEquals("placidus", awaitItem().houseSystem)
            cancelAndIgnoreRemainingEvents()
        }
        coVerify { prefs.saveHouseSystem("placidus") }
    }

    @Test
    fun `setChartStyle updates state and persists to prefs`() = runTest {
        vm.setChartStyle("south")
        vm.uiState.test {
            assertEquals("south", awaitItem().chartStyle)
            cancelAndIgnoreRemainingEvents()
        }
        coVerify { prefs.setChartStyle("south") }
    }
}
