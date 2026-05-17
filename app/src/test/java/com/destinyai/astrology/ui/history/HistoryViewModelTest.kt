package com.destinyai.astrology.ui.history

import app.cash.turbine.test
import com.destinyai.astrology.data.repository.ChatRepository
import com.destinyai.astrology.domain.model.ChatThread
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
class HistoryViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var repository: ChatRepository
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
        vm = HistoryViewModel(repository)
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
}
