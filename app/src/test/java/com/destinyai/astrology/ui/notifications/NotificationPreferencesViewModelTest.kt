package com.destinyai.astrology.ui.notifications

import app.cash.turbine.test
import com.destinyai.astrology.data.local.prefs.AlertItem
import com.destinyai.astrology.data.local.prefs.UserPreferences
import com.destinyai.astrology.data.remote.AstroApiService
import com.destinyai.astrology.data.remote.NotificationPrefsDto
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
class NotificationPreferencesViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var api: AstroApiService
    private lateinit var prefs: UserPreferences
    private lateinit var vm: NotificationPreferencesViewModel

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
        coEvery { prefs.getUserEmail() } returns "u@x.com"
        coEvery { prefs.getAlertItems() } returns emptyList()
        coEvery { prefs.getNotifPushEnabled() } returns true
        coEvery { prefs.getNotifEmailEnabled() } returns true
        coEvery { prefs.getNotifInAppEnabled() } returns true
        vm = NotificationPreferencesViewModel(api, prefs)
    }

    @Test
    fun `initial state has all prefs enabled`() = runTest {
        vm.uiState.test {
            val s = awaitItem()
            assertTrue(s.dailyInsight)
            assertTrue(s.transits)
            assertTrue(s.compatibility)
            assertFalse(s.isLoading)
            assertFalse(s.isSaved)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `loadPrefs populates state from api`() = runTest {
        coEvery { api.getNotificationPrefs("u@x.com") } returns
            NotificationPrefsDto(dailyInsight = true, transits = false, compatibility = true)

        vm.loadPrefs()

        vm.uiState.test {
            val s = awaitItem()
            assertTrue(s.dailyInsight)
            assertFalse(s.transits)
            assertTrue(s.compatibility)
            assertFalse(s.isLoading)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `loadPrefs does nothing when no email`() = runTest {
        coEvery { prefs.getUserEmail() } returns null

        vm.loadPrefs()

        coVerify(exactly = 0) { api.getNotificationPrefs(any()) }
    }

    @Test
    fun `setDailyInsight updates state`() = runTest {
        vm.setDailyInsight(false)
        vm.uiState.test {
            assertFalse(awaitItem().dailyInsight)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setTransits updates state`() = runTest {
        vm.setTransits(false)
        vm.uiState.test {
            assertFalse(awaitItem().transits)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setCompatibility updates state`() = runTest {
        vm.setCompatibility(false)
        vm.uiState.test {
            assertFalse(awaitItem().compatibility)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `save calls api and prefs on success`() = runTest {
        vm.save()

        coVerify { api.updateNotificationPrefs("u@x.com", any()) }
        coVerify { prefs.setNotifPrefs(any(), any(), any()) }
        vm.uiState.test {
            assertTrue(awaitItem().isSaved)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `save sets error on failure`() = runTest {
        coEvery { api.updateNotificationPrefs(any(), any()) } throws Exception("api error")

        vm.save()

        vm.uiState.test {
            val s = awaitItem()
            assertNotNull(s.error)
            assertFalse(s.isLoading)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `save does nothing when no email`() = runTest {
        coEvery { prefs.getUserEmail() } returns null

        vm.save()

        coVerify(exactly = 0) { api.updateNotificationPrefs(any(), any()) }
    }

    // ── R2-S13h: custom alert tests ───────────────────────────────────────────

    @Test
    fun `addAlert appends item`() = runTest {
        vm.addAlert("Check my transits", "Daily")

        vm.uiState.test {
            val s = awaitItem()
            assertEquals(1, s.alertItems.size)
            assertEquals("Check my transits", s.alertItems[0].text)
            assertEquals("Daily", s.alertItems[0].frequency)
            assertTrue(s.canAddMore)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `addAlert blocked at 5 items`() = runTest {
        // Fill up to 5
        repeat(5) { i -> vm.addAlert("Alert $i", "Daily") }

        // 6th add should be blocked
        vm.addAlert("Alert 6", "Weekly")

        vm.uiState.test {
            val s = awaitItem()
            assertEquals(5, s.alertItems.size)
            assertFalse(s.canAddMore)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `deleteAlert removes item`() = runTest {
        vm.addAlert("To be deleted", "Weekly")
        val id = vm.uiState.value.alertItems.first().id

        vm.deleteAlert(id)

        vm.uiState.test {
            val s = awaitItem()
            assertTrue(s.alertItems.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `updateAlert mutates by id`() = runTest {
        vm.addAlert("Original text", "Daily")
        val id = vm.uiState.value.alertItems.first().id

        vm.updateAlert(id, "Updated text", "Monthly")

        vm.uiState.test {
            val s = awaitItem()
            assertEquals(1, s.alertItems.size)
            assertEquals("Updated text", s.alertItems[0].text)
            assertEquals("Monthly", s.alertItems[0].frequency)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
