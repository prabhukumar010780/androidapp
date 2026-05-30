package com.destinyai.astrology.ui.charts

import app.cash.turbine.test
import com.destinyai.astrology.data.local.prefs.UserPreferences
import com.destinyai.astrology.data.remote.AstroApiService
import com.destinyai.astrology.data.remote.BirthProfileDto
import io.mockk.coEvery
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
class ChartsViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var prefs: UserPreferences
    private lateinit var vm: ChartsViewModel

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
        coEvery { prefs.getChartStyle() } returns "north_indian"
        vm = ChartsViewModel(prefs, mockk(relaxed = true))
    }

    @Test
    fun `initial state has no data`() = runTest {
        vm.uiState.test {
            val s = awaitItem()
            assertFalse(s.hasData)
            assertEquals("", s.dateOfBirth)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `loadChartData sets hasData false when no birth profile`() = runTest {
        coEvery { prefs.getBirthProfile() } returns null

        vm.loadChartData()

        vm.uiState.test {
            assertFalse(awaitItem().hasData)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `loadChartData populates state from prefs`() = runTest {
        coEvery { prefs.getBirthProfile() } returns BirthProfileDto(
            dateOfBirth = "1980-07-01",
            timeOfBirth = "06:32",
            cityOfBirth = "Bhilai",
            latitude = 21.2138,
            longitude = 81.3943,
            gender = "male",
            birthTimeUnknown = false,
        )
        coEvery { prefs.getChartStyle() } returns "south_indian"

        vm.loadChartData()

        vm.uiState.test {
            val s = awaitItem()
            assertTrue(s.hasData)
            assertEquals("1980-07-01", s.dateOfBirth)
            assertEquals("06:32", s.timeOfBirth)
            assertEquals("Bhilai", s.cityOfBirth)
            assertEquals(21.2138, s.latitude, 0.0001)
            assertEquals(81.3943, s.longitude, 0.0001)
            assertEquals("south_indian", s.chartStyle)
            assertFalse(s.timeUnknown)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `loadChartData reflects timeUnknown from profile`() = runTest {
        coEvery { prefs.getBirthProfile() } returns BirthProfileDto(
            dateOfBirth = "1980-07-01",
            timeOfBirth = "12:00",
            cityOfBirth = "Bhilai",
            latitude = 21.21,
            longitude = 81.39,
            birthTimeUnknown = true,
        )

        vm.loadChartData()

        vm.uiState.test {
            assertTrue(awaitItem().timeUnknown)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
