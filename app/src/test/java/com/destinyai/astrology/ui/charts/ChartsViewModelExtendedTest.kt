package com.destinyai.astrology.ui.charts

import app.cash.turbine.test
import com.destinyai.astrology.data.local.prefs.UserPreferences
import com.destinyai.astrology.data.remote.AstroApiService
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
class ChartsViewModelExtendedTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var prefs: UserPreferences
    private lateinit var api: AstroApiService
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
        api = mockk(relaxed = true)
        coEvery { prefs.getChartStyle() } returns "north_indian"
        coEvery { prefs.getBirthProfile() } returns null
        vm = ChartsViewModel(prefs, api)
    }

    // ── Initial state ─────────────────────────────────────────────────────────

    @Test
    fun `initial state has isLoading false before loadChartData called`() = runTest {
        vm.uiState.test {
            val s = awaitItem()
            assertFalse(s.isLoading)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `initial state has no chart API data`() = runTest {
        vm.uiState.test {
            val s = awaitItem()
            assertNull(s.chartApiData)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `initial state errorMessage is null`() = runTest {
        vm.uiState.test {
            val s = awaitItem()
            assertNull(s.errorMessage)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── loadChartData ─────────────────────────────────────────────────────────

    @Test
    fun `loadChartData sets isLoading true then false on success`() = runTest {
        coEvery { prefs.getBirthProfile() } returns BirthProfileDto(
            dateOfBirth = "1980-07-01",
            timeOfBirth = "06:32",
            cityOfBirth = "Bhilai",
            latitude = 21.2138,
            longitude = 81.3943,
        )
        coEvery { api.getChartData(any()) } returns makeChartApiResponse()

        vm.loadChartData()

        vm.uiState.test {
            val s = awaitItem()
            assertFalse(s.isLoading)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `loadChartData populates chartApiData on API success`() = runTest {
        coEvery { prefs.getBirthProfile() } returns BirthProfileDto(
            dateOfBirth = "1980-07-01",
            timeOfBirth = "06:32",
            cityOfBirth = "Bhilai",
            latitude = 21.2138,
            longitude = 81.3943,
        )
        val response = makeChartApiResponse()
        coEvery { api.getChartData(any()) } returns response

        vm.loadChartData()

        vm.uiState.test {
            val s = awaitItem()
            assertNotNull(s.chartApiData)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `loadChartData sets errorMessage on API failure`() = runTest {
        coEvery { prefs.getBirthProfile() } returns BirthProfileDto(
            dateOfBirth = "1980-07-01",
            timeOfBirth = "06:32",
            cityOfBirth = "Bhilai",
            latitude = 21.2138,
            longitude = 81.3943,
        )
        coEvery { api.getChartData(any()) } throws RuntimeException("Network error")

        vm.loadChartData()

        vm.uiState.test {
            val s = awaitItem()
            assertNotNull(s.errorMessage)
            assertFalse(s.isLoading)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── Chart style toggle ────────────────────────────────────────────────────

    @Test
    fun `setChartStyle updates chartStyle in state`() = runTest {
        vm.setChartStyle("south_indian")

        vm.uiState.test {
            assertEquals("south_indian", awaitItem().chartStyle)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setChartStyle persists to prefs`() = runTest {
        vm.setChartStyle("south_indian")
        coVerify { prefs.setChartStyle("south_indian") }
    }

    @Test
    fun `setChartStyle north_indian also persists`() = runTest {
        vm.setChartStyle("north_indian")
        coVerify { prefs.setChartStyle("north_indian") }
    }

    // ── Ascendant resolution ──────────────────────────────────────────────────

    @Test
    fun `loadChartData populates ascendantSign from house 1`() = runTest {
        coEvery { prefs.getBirthProfile() } returns BirthProfileDto(
            dateOfBirth = "1980-07-01",
            timeOfBirth = "06:32",
            cityOfBirth = "Bhilai",
            latitude = 21.2138,
            longitude = 81.3943,
        )
        coEvery { api.getChartData(any()) } returns makeChartApiResponse(house1SignNum = 3) // Gemini

        vm.loadChartData()

        vm.uiState.test {
            val s = awaitItem()
            assertEquals("Ge", s.ascendantSign) // sign 3 = Gemini = "Ge"
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun makeChartApiResponse(house1SignNum: Int = 3) = ChartApiResponse(
        planets = mapOf(
            "Sun" to PlanetApiData(
                house = 1, sign = "Ge", degree = 76.5,
                isRetrograde = false, vargottama = false, isCombust = false,
            ),
        ),
        houses = mapOf("1" to HouseApiData(signNum = house1SignNum)),
        nakshatra = emptyMap(),
        divisionalCharts = emptyMap(),
        birthDetails = BirthDetailsApiData(dob = "1980-07-01", time = "06:32"),
    )
}
