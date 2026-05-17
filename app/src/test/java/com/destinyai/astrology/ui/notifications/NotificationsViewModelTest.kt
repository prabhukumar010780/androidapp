package com.destinyai.astrology.ui.notifications

import app.cash.turbine.test
import com.destinyai.astrology.data.local.prefs.UserPreferences
import com.destinyai.astrology.data.remote.AstroApiService
import com.destinyai.astrology.data.remote.NotificationDto
import com.destinyai.astrology.data.remote.NotificationListResponse
import com.destinyai.astrology.data.remote.UnreadCountResponse
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
class NotificationsViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var api: AstroApiService
    private lateinit var prefs: UserPreferences
    private lateinit var vm: NotificationsViewModel

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
        vm = NotificationsViewModel(api, prefs)
    }

    @Test
    fun `initial state has empty notifications`() = runTest {
        vm.uiState.test {
            val s = awaitItem()
            assertTrue(s.notifications.isEmpty())
            assertEquals(0, s.unreadCount)
            assertFalse(s.isLoading)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `loadNotifications populates state`() = runTest {
        coEvery { api.listNotifications("u@x.com") } returns NotificationListResponse(
            notifications = listOf(
                NotificationDto("n1", "Daily Insight", "Your reading is ready", false, "2026-05-17T09:00:00Z"),
                NotificationDto("n2", "Transit Alert", "Mars enters Aries", true, "2026-05-16T09:00:00Z"),
            ),
            total = 2,
        )
        coEvery { api.getUnreadCount("u@x.com") } returns UnreadCountResponse(1)

        vm.loadNotifications()

        vm.uiState.test {
            val s = awaitItem()
            assertEquals(2, s.notifications.size)
            assertEquals(1, s.unreadCount)
            assertFalse(s.isLoading)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `loadNotifications sets error on failure`() = runTest {
        coEvery { api.listNotifications(any()) } throws RuntimeException("error")

        vm.loadNotifications()

        vm.uiState.test {
            assertNotNull(awaitItem().error)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `loadNotifications does nothing when no email`() = runTest {
        coEvery { prefs.getUserEmail() } returns null

        vm.loadNotifications()

        coVerify(exactly = 0) { api.listNotifications(any()) }
    }

    @Test
    fun `markAllRead clears unreadCount and marks all notifications read`() = runTest {
        coEvery { api.listNotifications("u@x.com") } returns NotificationListResponse(
            notifications = listOf(
                NotificationDto("n1", "Title", "Body", false, "2026-05-17T09:00:00Z"),
            ),
        )
        coEvery { api.getUnreadCount("u@x.com") } returns UnreadCountResponse(1)
        vm.loadNotifications()

        vm.markAllRead()

        vm.uiState.test {
            val s = awaitItem()
            assertEquals(0, s.unreadCount)
            assertTrue(s.notifications.all { it.isRead })
            cancelAndIgnoreRemainingEvents()
        }
        coVerify { api.markAllRead("u@x.com") }
    }

    @Test
    fun `markRead marks single notification as read and updates count`() = runTest {
        coEvery { api.listNotifications("u@x.com") } returns NotificationListResponse(
            notifications = listOf(
                NotificationDto("n1", "T1", "B1", false, "2026-05-17T09:00:00Z"),
                NotificationDto("n2", "T2", "B2", false, "2026-05-17T10:00:00Z"),
            ),
        )
        coEvery { api.getUnreadCount("u@x.com") } returns UnreadCountResponse(2)
        vm.loadNotifications()

        vm.markRead("n1")

        vm.uiState.test {
            val s = awaitItem()
            assertTrue(s.notifications.first { it.id == "n1" }.isRead)
            assertFalse(s.notifications.first { it.id == "n2" }.isRead)
            assertEquals(1, s.unreadCount)
            cancelAndIgnoreRemainingEvents()
        }
        coVerify { api.markNotificationRead("n1") }
    }
}
