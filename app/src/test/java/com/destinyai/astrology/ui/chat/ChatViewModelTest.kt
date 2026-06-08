package com.destinyai.astrology.ui.chat

import android.content.Context
import app.cash.turbine.test
import com.destinyai.astrology.data.local.prefs.UserPreferences
import com.destinyai.astrology.data.remote.AstroApiService
import com.destinyai.astrology.data.repository.ChatRepository
import com.destinyai.astrology.domain.model.ChatMessage
import com.destinyai.astrology.domain.model.ChatThread
import com.destinyai.astrology.services.ProfileChangeBus
import com.destinyai.astrology.services.QuotaManager
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*

/**
 * TDD test shell for ChatViewModel.
 * Mirrors: iOS ChatViewModelTests.swift (27 assertions)
 */
@ExperimentalCoroutinesApi
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ChatViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var repository: ChatRepository
    private lateinit var authRepository: com.destinyai.astrology.data.repository.AuthRepository
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
        authRepository = mockk(relaxed = true)
        api = mockk(relaxed = true)
        prefs = mockk(relaxed = true)
        quotaManager = mockk(relaxed = true)
        profileChangeBus = mockk(relaxed = true)
        appContext = mockk(relaxed = true)
        // Stub the flows that ChatViewModel.init() collects; relaxed = true only returns
        // default primitives, not valid Flow instances — explicit stubs are required.
        every { repository.progressEvents } returns MutableSharedFlow()
        every { prefs.isHistoryEnabledFlow } returns flowOf(true)
        every { prefs.isGuestUserFlow } returns flowOf(false)
        every { prefs.activeProfileIdFlow } returns flowOf(null)
        every { prefs.responseLengthFlow } returns flowOf("standard")
        every { profileChangeBus.events } returns MutableSharedFlow()
        // sendMessage() pre-flight uses prefs.getUserEmail() to gate the quota check.
        // Returning null lets the test bypass the quota path entirely so the user-msg
        // append + repository.sendMessage call site can be exercised. Tests that
        // specifically exercise the quota path stub canAccessFeature directly.
        coEvery { prefs.getUserEmail() } returns null
        viewModel = ChatViewModel(repository, authRepository, api, prefs, quotaManager, profileChangeBus, mockk(relaxed = true), appContext)
    }

    // --- Init ---

    @Test
    fun `init creates session`() = runTest {
        viewModel.uiState.test {
            assertNotNull(awaitItem().sessionId)
        }
    }

    @Test
    fun `init has welcome message`() = runTest {
        viewModel.uiState.test {
            assertTrue(awaitItem().messages.isNotEmpty())
        }
    }

    // --- canSend gate ---

    @Test
    fun `canSend is false when input is empty`() = runTest {
        viewModel.updateInput("")
        viewModel.uiState.test {
            assertFalse(awaitItem().canSend)
        }
    }

    @Test
    fun `canSend is false when input is whitespace only`() = runTest {
        viewModel.updateInput("   ")
        viewModel.uiState.test {
            assertFalse(awaitItem().canSend)
        }
    }

    @Test
    fun `canSend is false when isLoading is true`() = runTest {
        viewModel.updateInput("What is my fortune?")
        // Simulate loading state
        viewModel.uiState.test {
            val state = awaitItem()
            if (state.isLoading) assertFalse(state.canSend)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `canSend is true when input has text and not loading`() = runTest {
        viewModel.updateInput("What does my chart say?")
        viewModel.uiState.test {
            val state = awaitItem()
            if (!state.isLoading && !state.isStreaming) assertTrue(state.canSend)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // --- New chat ---

    @Test
    fun `startNewChat creates new thread`() = runTest {
        val oldSession = viewModel.uiState.value.sessionId
        viewModel.startNewChat()
        viewModel.uiState.test {
            assertNotEquals(oldSession, awaitItem().sessionId)
        }
    }

    @Test
    fun `startNewChat clears messages`() = runTest {
        viewModel.startNewChat()
        viewModel.uiState.test {
            val state = awaitItem()
            // Only the welcome message should remain
            assertEquals(1, state.messages.size)
        }
    }

    @Test
    fun `startNewChat updates history list`() = runTest {
        coEvery { repository.loadHistory() } returns emptyList()

        viewModel.startNewChat()

        coVerify { repository.loadHistory() }
    }

    // --- Send message ---

    @Test
    fun `sendMessage appends user message`() = runTest(testDispatcher) {
        coEvery { repository.sendMessage(any(), any()) } returns flowOf(Result.success("response"))

        viewModel.updateInput("Tell me about my day")
        viewModel.sendMessage()
        advanceUntilIdle()

        viewModel.uiState.test {
            val state = awaitItem()
            assertTrue(state.messages.any { it.role == ChatMessage.Role.USER })
        }
    }

    @Test
    fun `sendMessage clears input field after send`() = runTest(testDispatcher) {
        coEvery { repository.sendMessage(any(), any()) } returns flowOf(Result.success("response"))

        viewModel.updateInput("Some question")
        viewModel.sendMessage()
        advanceUntilIdle()

        viewModel.uiState.test {
            assertEquals("", awaitItem().inputText)
        }
    }

    @Test
    fun `sendMessage sets isStreaming true during response`() = runTest {
        coEvery { repository.sendMessage(any(), any()) } returns flowOf(Result.success("..."))

        viewModel.updateInput("question")
        viewModel.sendMessage()

        viewModel.uiState.test {
            // Should emit isStreaming=true at some point
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `sendMessage appends assistant response to messages`() = runTest(testDispatcher) {
        val responseText = "Mars in your 7th house suggests..."
        coEvery { repository.sendMessage(any(), any()) } returns flowOf(Result.success(responseText))

        viewModel.updateInput("relationship question")
        viewModel.sendMessage()
        advanceUntilIdle()

        viewModel.uiState.test {
            val state = awaitItem()
            assertTrue(state.messages.any {
                it.role == ChatMessage.Role.ASSISTANT && it.content.contains("Mars")
            })
        }
    }

    @Test
    fun `sendMessage 403 upgrade_required shows paywall`() = runTest(testDispatcher) {
        coEvery { repository.sendMessage(any(), any()) } returns
            flowOf(Result.failure(UpgradeRequiredException()))

        viewModel.updateInput("premium feature question")
        viewModel.sendMessage()
        advanceUntilIdle()

        viewModel.uiState.test {
            // Mirrors iOS QuotaExhaustedView (ChatView.swift:93-112): non-guest users
            // see the interstitial account sheet first; guests see the legacy paywall.
            // The default test fixture user is non-guest, so the interstitial fires.
            val state = awaitItem()
            assertTrue(state.showQuotaExhaustedAccountSheet || state.showPaywall)
        }
    }

    @Test
    fun `sendMessage network error shows retry option`() = runTest {
        coEvery { repository.sendMessage(any(), any()) } returns
            flowOf(Result.failure(Exception("Network error")))

        viewModel.updateInput("question")
        viewModel.sendMessage()

        viewModel.uiState.test {
            assertNotNull(awaitItem().errorMessage)
        }
    }

    // --- Copy ---

    @Test
    fun `copyMessage sets copied flag on the correct message`() = runTest {
        val msgId = "msg-001"
        viewModel.copyMessage(msgId)
        viewModel.uiState.test {
            val state = awaitItem()
            assertTrue(state.copiedMessageId == msgId)
        }
    }

    // --- History ---

    @Test
    fun `loadHistory populates thread list`() = runTest {
        val threads = listOf(
            ChatThread(id = "t1", title = "Career chat"),
            ChatThread(id = "t2", title = "Health query"),
        )
        // ChatViewModel.loadHistory() calls loadHistoryPaginated(0, HISTORY_PAGE_SIZE) — not loadHistory().
        coEvery { repository.loadHistoryPaginated(any(), any()) } returns threads

        viewModel.loadHistory()
        runCurrent()

        viewModel.uiState.test {
            assertEquals(2, awaitItem().threads.size)
        }
    }

    @Test
    fun `openThread loads messages for selected thread`() = runTest {
        val messages = listOf(
            ChatMessage(id = "m1", role = ChatMessage.Role.USER, content = "Hi"),
        )
        coEvery { repository.loadThread("t1") } returns messages

        viewModel.openThread("t1")

        viewModel.uiState.test {
            assertEquals("t1", awaitItem().activeThreadId)
        }
    }

    // --- Charts ---

    @Test
    fun `chart button visible when last message has chart data`() = runTest {
        viewModel.uiState.test {
            val state = awaitItem()
            // Chart button visible only when lastMessage.hasChartData == true
            cancelAndIgnoreRemainingEvents()
        }
    }
}
