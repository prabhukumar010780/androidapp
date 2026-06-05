package com.destinyai.astrology.ui.chat

import android.content.Context
import app.cash.turbine.test
import com.destinyai.astrology.data.local.prefs.UserPreferences
import com.destinyai.astrology.data.remote.AstroApiService
import com.destinyai.astrology.data.repository.ChatRepository
import com.destinyai.astrology.domain.model.ChatThread
import com.destinyai.astrology.services.ProfileChangeBus
import com.destinyai.astrology.services.QuotaManager
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*

@ExperimentalCoroutinesApi
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ChatViewModelExtendedTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var repository: ChatRepository
    private lateinit var api: AstroApiService
    private lateinit var prefs: UserPreferences
    private lateinit var quotaManager: QuotaManager
    private lateinit var profileChangeBus: ProfileChangeBus
    private lateinit var appContext: Context
    private lateinit var viewModel: ChatViewModel

    @BeforeAll
    fun setMainDispatcher() = Dispatchers.setMain(testDispatcher)

    @AfterAll
    fun resetMainDispatcher() = Dispatchers.resetMain()

    @BeforeEach
    fun setUp() {
        repository = mockk(relaxed = true)
        api = mockk(relaxed = true)
        prefs = mockk(relaxed = true)
        quotaManager = mockk(relaxed = true)
        profileChangeBus = mockk(relaxed = true)
        appContext = mockk(relaxed = true)
        every { repository.progressEvents } returns MutableSharedFlow()
        every { prefs.isHistoryEnabledFlow } returns flowOf(true)
        every { prefs.isGuestUserFlow } returns flowOf(false)
        every { prefs.activeProfileIdFlow } returns flowOf(null)
        every { prefs.responseLengthFlow } returns flowOf("standard")
        every { profileChangeBus.events } returns MutableSharedFlow()
        coEvery { prefs.getUserEmail() } returns null
        viewModel = ChatViewModel(repository, api, prefs, quotaManager, profileChangeBus, appContext)
    }

    // ── suggestedQuestions ────────────────────────────────────────────────────

    @Test
    fun `initial suggested questions is empty`() = runTest {
        viewModel.uiState.test {
            assertTrue(awaitItem().suggestedQuestions.isEmpty())
        }
    }

    @Test
    fun `dismissSuggestedQuestions clears the list`() = runTest {
        viewModel.setSuggestedQuestions(listOf("Q1", "Q2"))
        viewModel.dismissSuggestedQuestions()
        viewModel.uiState.test {
            assertTrue(awaitItem().suggestedQuestions.isEmpty())
        }
    }

    @Test
    fun `setSuggestedQuestions populates state`() = runTest {
        val qs = listOf("What is my dasha?", "Am I in a good period?")
        viewModel.setSuggestedQuestions(qs)
        viewModel.uiState.test {
            assertEquals(2, awaitItem().suggestedQuestions.size)
        }
    }

    // ── interruptedQuestion ───────────────────────────────────────────────────

    @Test
    fun `initial interrupted question is null`() = runTest {
        viewModel.uiState.test {
            assertNull(awaitItem().interruptedQuestion)
        }
    }

    @Test
    fun `retryInterruptedQuestion clears interruptedQuestion after send`() = runTest {
        coEvery { repository.sendMessage(any(), any()) } returns flowOf(Result.success("reply"))
        viewModel.setInterruptedQuestion("What is my fortune?")
        viewModel.retryInterruptedQuestion()
        viewModel.uiState.test {
            assertNull(awaitItem().interruptedQuestion)
        }
    }

    @Test
    fun `retryInterruptedQuestion is no-op when interruptedQuestion is null`() = runTest {
        viewModel.retryInterruptedQuestion() // must not throw
        viewModel.uiState.test {
            assertNull(awaitItem().interruptedQuestion)
        }
    }

    // ── pin thread ────────────────────────────────────────────────────────────

    @Test
    fun `pinThread marks matching thread as pinned`() = runTest {
        val threads = listOf(
            ChatThread(id = "t1", title = "Career", isPinned = false),
            ChatThread(id = "t2", title = "Health", isPinned = false),
        )
        coEvery { repository.loadHistoryPaginated(any(), any()) } returns threads
        viewModel.loadHistory()
        runCurrent()

        viewModel.pinThread("t1")
        runCurrent()

        viewModel.uiState.test {
            val state = awaitItem()
            assertTrue(state.threads.first { it.id == "t1" }.isPinned)
            assertFalse(state.threads.first { it.id == "t2" }.isPinned)
        }
    }

    @Test
    fun `pinThread unpins already-pinned thread`() = runTest {
        val threads = listOf(ChatThread(id = "t1", title = "Career", isPinned = true))
        coEvery { repository.loadHistoryPaginated(any(), any()) } returns threads
        viewModel.loadHistory()
        runCurrent()

        viewModel.pinThread("t1")
        runCurrent()

        viewModel.uiState.test {
            assertFalse(awaitItem().threads.first { it.id == "t1" }.isPinned)
        }
    }

    // ── ChatThread model fields ───────────────────────────────────────────────

    @Test
    fun `ChatThread has preview field`() {
        val thread = ChatThread(id = "t1", title = "T", preview = "Hello from preview")
        assertEquals("Hello from preview", thread.preview)
    }

    @Test
    fun `ChatThread has messageCount field`() {
        val thread = ChatThread(id = "t1", title = "T", messageCount = 7)
        assertEquals(7, thread.messageCount)
    }

    @Test
    fun `ChatThread isPinned defaults to false`() {
        val thread = ChatThread(id = "t1", title = "T")
        assertFalse(thread.isPinned)
    }

    @Test
    fun `ChatThread has updatedAtMs field`() {
        val ts = System.currentTimeMillis()
        val thread = ChatThread(id = "t1", title = "T", updatedAtMs = ts)
        assertEquals(ts, thread.updatedAtMs)
    }
}
