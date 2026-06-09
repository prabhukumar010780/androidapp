package com.destinyai.astrology.ui.history

import app.cash.turbine.test
import com.destinyai.astrology.data.local.db.CompatibilityHistoryDao
import com.destinyai.astrology.data.local.db.CompatibilityHistoryEntity
import com.destinyai.astrology.data.local.prefs.UserPreferences
import com.destinyai.astrology.data.repository.ChatRepository
import com.destinyai.astrology.domain.model.ChatThread
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
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
class HistoryViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var repository: ChatRepository
    private lateinit var compatibilityHistoryDao: CompatibilityHistoryDao
    private lateinit var prefs: UserPreferences
    private lateinit var vm: HistoryViewModel

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
        repository = mockk(relaxed = true)
        compatibilityHistoryDao = mockk(relaxed = true)
        prefs = mockk(relaxed = true)
        every { compatibilityHistoryDao.observeAll(any()) } returns flowOf(emptyList())
        every { prefs.isHistoryEnabledFlow } returns flowOf(true)
        every { prefs.activeProfileIdFlow } returns flowOf(null)
        coEvery { prefs.getUserEmail() } returns "u@x.com"
        vm = HistoryViewModel(repository, compatibilityHistoryDao, prefs)
    }

    @Test
    fun `initial state has empty threads`() = runTest {
        vm.uiState.test {
            val s = awaitItem()
            assertTrue(s.threads.isEmpty())
            assertFalse(s.isLoading)
            assertEquals("", s.searchText)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `loadHistory populates threads`() = runTest {
        coEvery { repository.loadHistory() } returns listOf(
            ChatThread("t1", "Thread 1"),
            ChatThread("t2", "Thread 2"),
        )

        vm.loadHistory()

        vm.uiState.test {
            val s = awaitItem()
            assertEquals(2, s.threads.size)
            assertEquals("Thread 1", s.threads[0].title)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `loadHistory sets error on failure`() = runTest {
        coEvery { repository.loadHistory() } throws RuntimeException("network error")

        vm.loadHistory()

        vm.uiState.test {
            assertNotNull(awaitItem().error)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `deleteThread removes thread from state`() = runTest {
        coEvery { repository.loadHistory() } returns listOf(
            ChatThread("t1", "Thread 1"),
            ChatThread("t2", "Thread 2"),
        )
        vm.loadHistory()

        vm.deleteThread("t1")

        vm.uiState.test {
            val s = awaitItem()
            assertEquals(1, s.threads.size)
            assertEquals("t2", s.threads[0].id)
            cancelAndIgnoreRemainingEvents()
        }
        coVerify { repository.deleteThread("t1") }
    }

    @Test
    fun `deleteThread sets error when repository throws`() = runTest {
        coEvery { repository.deleteThread(any()) } throws RuntimeException("delete error")

        vm.deleteThread("t1")

        vm.uiState.test {
            assertNotNull(awaitItem().error)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setSearchText updates searchText`() = runTest {
        vm.setSearchText("Mars")
        vm.uiState.test {
            assertEquals("Mars", awaitItem().searchText)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `filteredThreads returns all threads when searchText is blank`() = runTest {
        coEvery { repository.loadHistory() } returns listOf(
            ChatThread("t1", "Today reading"),
            ChatThread("t2", "Mars transit"),
        )
        vm.loadHistory()

        vm.uiState.test {
            assertEquals(2, awaitItem().filteredThreads.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `filteredThreads filters by title case insensitive`() = runTest {
        coEvery { repository.loadHistory() } returns listOf(
            ChatThread("t1", "Today reading"),
            ChatThread("t2", "Mars transit"),
            ChatThread("t3", "mars retrograde"),
        )
        vm.loadHistory()
        vm.setSearchText("mars")

        vm.uiState.test {
            val filtered = awaitItem().filteredThreads
            assertEquals(2, filtered.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // --- P0-4: Compatibility tab tests ---

    @Test
    fun `initial selectedTab is 0`() = runTest {
        vm.uiState.test {
            assertEquals(0, awaitItem().selectedTab)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setTab(1) loads compatibility history`() = runTest {
        val fakeEntity = fakeCompatibilityEntity("sess_1", "Prabhu", "Priya", 28, 36)
        every { compatibilityHistoryDao.observeAll("user@x.com") } returns flowOf(listOf(fakeEntity))

        vm.loadCompatibilityHistory("user@x.com")
        vm.setTab(1)

        vm.uiState.test {
            val s = awaitItem()
            assertEquals(1, s.selectedTab)
            assertEquals(1, s.compatibilityItems.size)
            assertEquals("Priya", s.compatibilityItems[0].girlName)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `deleteCompatibilityItem removes item from state`() = runTest {
        val entity1 = fakeCompatibilityEntity("sess_1", "Prabhu", "Priya", 28, 36)
        val entity2 = fakeCompatibilityEntity("sess_2", "Prabhu", "Anita", 30, 36)
        every { compatibilityHistoryDao.observeAll(any()) } returns flowOf(listOf(entity1, entity2))

        vm.loadCompatibilityHistory("user@x.com")
        vm.deleteCompatibilityItem("sess_1")

        vm.uiState.test {
            val s = awaitItem()
            assertEquals(1, s.compatibilityItems.size)
            assertEquals("sess_2", s.compatibilityItems[0].sessionId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun fakeCompatibilityEntity(
        sessionId: String,
        boyName: String,
        girlName: String,
        totalScore: Int,
        maxScore: Int,
    ) = CompatibilityHistoryEntity(
        sessionId = sessionId,
        ownerEmail = "user@x.com",
        timestampMs = System.currentTimeMillis(),
        boyName = boyName,
        boyDob = "1980-01-01",
        boyCity = "Delhi",
        boyTime = "06:00",
        girlName = girlName,
        girlDob = "1985-03-15",
        girlCity = "Mumbai",
        girlTime = "08:30",
        totalScore = totalScore,
        maxScore = maxScore,
    )
}
