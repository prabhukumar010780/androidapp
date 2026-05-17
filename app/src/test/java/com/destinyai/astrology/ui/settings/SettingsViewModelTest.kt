package com.destinyai.astrology.ui.settings

import app.cash.turbine.test
import com.destinyai.astrology.data.local.prefs.UserPreferences
import com.destinyai.astrology.data.remote.AstroApiService
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
class SettingsViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var api: AstroApiService
    private lateinit var prefs: UserPreferences
    private lateinit var vm: SettingsViewModel

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
        coEvery { prefs.getChartStyle() } returns "north_indian"
        coEvery { prefs.getResponseStyle() } returns "balanced"
        coEvery { prefs.getSelectedLanguage() } returns "en"
        coEvery { prefs.getNotifDailyInsight() } returns true
        coEvery { prefs.getNotifTransits() } returns true
        coEvery { prefs.getNotifCompatibility() } returns true
        vm = SettingsViewModel(api, prefs)
    }

    @Test
    fun `initial state has defaults`() = runTest {
        vm.uiState.test {
            val s = awaitItem()
            assertEquals("north_indian", s.chartStyle)
            assertEquals("balanced", s.responseStyle)
            assertEquals("en", s.selectedLanguage)
            assertTrue(s.notifDailyInsight)
            assertFalse(s.isLoading)
            assertFalse(s.isSaved)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `loadSettings reads all prefs`() = runTest {
        coEvery { prefs.getChartStyle() } returns "south_indian"
        coEvery { prefs.getSelectedLanguage() } returns "hi"
        coEvery { prefs.getNotifTransits() } returns false

        vm.loadSettings()

        vm.uiState.test {
            val s = awaitItem()
            assertEquals("south_indian", s.chartStyle)
            assertEquals("hi", s.selectedLanguage)
            assertFalse(s.notifTransits)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setChartStyle updates state and saves to prefs`() = runTest {
        vm.setChartStyle("south_indian")

        vm.uiState.test {
            assertEquals("south_indian", awaitItem().chartStyle)
            cancelAndIgnoreRemainingEvents()
        }
        coVerify { prefs.setChartStyle("south_indian") }
    }

    @Test
    fun `setResponseStyle updates state and saves to prefs`() = runTest {
        vm.setResponseStyle("detailed")

        vm.uiState.test {
            assertEquals("detailed", awaitItem().responseStyle)
            cancelAndIgnoreRemainingEvents()
        }
        coVerify { prefs.setResponseStyle("detailed") }
    }

    @Test
    fun `setLanguage updates state and saves to prefs`() = runTest {
        vm.setLanguage("hi")

        vm.uiState.test {
            assertEquals("hi", awaitItem().selectedLanguage)
            cancelAndIgnoreRemainingEvents()
        }
        coVerify { prefs.setSelectedLanguage("hi") }
    }

    @Test
    fun `setNotifDailyInsight updates state`() = runTest {
        vm.setNotifDailyInsight(false)
        vm.uiState.test {
            assertFalse(awaitItem().notifDailyInsight)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setNotifTransits updates state`() = runTest {
        vm.setNotifTransits(false)
        vm.uiState.test {
            assertFalse(awaitItem().notifTransits)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setNotifCompatibility updates state`() = runTest {
        vm.setNotifCompatibility(false)
        vm.uiState.test {
            assertFalse(awaitItem().notifCompatibility)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `saveNotifPrefs calls api and saves to prefs on success`() = runTest {
        coEvery { prefs.getUserEmail() } returns "u@x.com"

        vm.saveNotifPrefs()

        coVerify { api.updateNotificationPrefs("u@x.com", any()) }
        coVerify { prefs.setNotifPrefs(any(), any(), any()) }
        vm.uiState.test {
            assertTrue(awaitItem().isSaved)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `saveNotifPrefs does nothing when no user email`() = runTest {
        coEvery { prefs.getUserEmail() } returns null

        vm.saveNotifPrefs()

        coVerify(exactly = 0) { api.updateNotificationPrefs(any(), any()) }
    }

    @Test
    fun `saveNotifPrefs sets error on api failure`() = runTest {
        coEvery { prefs.getUserEmail() } returns "u@x.com"
        coEvery { api.updateNotificationPrefs(any(), any()) } throws Exception("api error")

        vm.saveNotifPrefs()

        vm.uiState.test {
            val s = awaitItem()
            assertNotNull(s.error)
            assertFalse(s.isLoading)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
