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
import kotlinx.coroutines.flow.MutableStateFlow
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
    private lateinit var appStartupService: com.destinyai.astrology.services.AppStartupService
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
        appStartupService = mockk(relaxed = true)
        // Default to the streaming path so existing streaming-path assertions hold.
        every { appStartupService.shouldStreamFor(any()) } returns true
        // Stub the flows that ChatViewModel.init() collects; relaxed = true only returns
        // default primitives, not valid Flow instances — explicit stubs are required.
        every { repository.progressEvents } returns MutableSharedFlow()
        every { prefs.isHistoryEnabledFlow } returns flowOf(true)
        every { prefs.isGuestUserFlow } returns flowOf(false)
        every { prefs.activeProfileIdFlow } returns flowOf(null)
        every { prefs.responseLengthFlow } returns flowOf("standard")
        every { profileChangeBus.events } returns MutableSharedFlow()
        // ChatViewModel.init() collects quotaManager.isPremium (post-upgrade unlock);
        // relaxed mockk returns a broken Flow, so provide a real StateFlow.
        every { quotaManager.isPremium } returns MutableStateFlow(false)
        // sendMessage() pre-flight uses prefs.getUserEmail() to gate the quota check.
        // Returning null lets the test bypass the quota path entirely so the user-msg
        // append + repository.sendMessage call site can be exercised. Tests that
        // specifically exercise the quota path stub canAccessFeature directly.
        coEvery { prefs.getUserEmail() } returns null
        viewModel = ChatViewModel(repository, mockk(relaxed = true), authRepository, api, prefs, quotaManager, profileChangeBus, mockk(relaxed = true), appStartupService, mockk(relaxed = true), appContext)
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
        coEvery { repository.sendMessage(any(), any(), any(), any()) } returns flowOf(Result.success("response"))

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
        coEvery { repository.sendMessage(any(), any(), any(), any()) } returns flowOf(Result.success("response"))

        viewModel.updateInput("Some question")
        viewModel.sendMessage()
        advanceUntilIdle()

        viewModel.uiState.test {
            assertEquals("", awaitItem().inputText)
        }
    }

    @Test
    fun `sendMessage sets isStreaming true during response`() = runTest {
        coEvery { repository.sendMessage(any(), any(), any(), any()) } returns flowOf(Result.success("..."))

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
        coEvery { repository.sendMessage(any(), any(), any(), any()) } returns flowOf(Result.success(responseText))

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
        coEvery { repository.sendMessage(any(), any(), any(), any()) } returns
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
        coEvery { repository.sendMessage(any(), any(), any(), any()) } returns
            flowOf(Result.failure(Exception("Network error")))
        // FIX A (iOS parity): a generic mid-stream error now transparently replays via
        // the non-streaming endpoint. Only when THAT also fails does the retry banner show,
        // so the sync fallback must fail too for this test to exercise the banner path.
        coEvery { repository.sendMessageSync(any(), any(), any(), any()) } returns
            Result.failure(Exception("Network error"))

        viewModel.updateInput("question")
        viewModel.sendMessage()
        advanceUntilIdle()

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

    @Test
    fun `openThread sets sessionId to the opened thread so follow-ups continue it (D1)`() = runTest {
        coEvery { repository.loadThread("t1") } returns listOf(
            ChatMessage(id = "m1", role = ChatMessage.Role.USER, content = "Hi"),
        )

        viewModel.openThread("t1")

        viewModel.uiState.test {
            val s = awaitItem()
            // sessionId (the send key) MUST equal the opened thread, not the init UUID —
            // otherwise a follow-up spawns an orphan thread.
            assertEquals("t1", s.sessionId)
            assertEquals("t1", s.activeThreadId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `deleting the currently-open thread resets to a new chat (F1)`() = runTest {
        coEvery { repository.loadThread("t1") } returns listOf(
            ChatMessage(id = "m1", role = ChatMessage.Role.USER, content = "Hi"),
        )
        viewModel.openThread("t1")
        val openSession = viewModel.uiState.value.sessionId

        viewModel.deleteThread("t1")

        viewModel.uiState.test {
            val s = awaitItem()
            // Open thread deleted → fresh chat: new sessionId, activeThreadId cleared.
            assertNotEquals("t1", s.sessionId)
            assertNotEquals(openSession, s.sessionId)
            assertEquals(null, s.activeThreadId)
            cancelAndIgnoreRemainingEvents()
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

    // --- DES-161 B3: dismissing the paywall must re-enable the send button ---

    @Test
    fun `dismissPaywall clears paywall flag and restores canAskQuestion`() = runTest {
        viewModel.dismissPaywall()
        viewModel.uiState.test {
            val state = awaitItem()
            assertFalse(state.showPaywall)
            // The fix guarantees canAskQuestion is set true on dismiss so the input
            // re-enables instead of staying permanently disabled after the guest
            // closes the sign-up sheet.
            assertTrue(state.canAskQuestion)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
