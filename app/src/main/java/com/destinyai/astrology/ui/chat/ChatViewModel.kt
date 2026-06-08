package com.destinyai.astrology.ui.chat

import android.content.Context
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.destinyai.astrology.R
import com.destinyai.astrology.data.local.prefs.UserPreferences
import com.destinyai.astrology.data.remote.AstroApiService
import com.destinyai.astrology.data.repository.ChatRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import com.destinyai.astrology.data.repository.ChatStreamEvent
import com.destinyai.astrology.domain.model.ChatMessage
import com.destinyai.astrology.domain.model.ChatThread
import com.destinyai.astrology.services.ProfileChangeBus
import com.destinyai.astrology.services.QuotaManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class ChatUiState(
    val sessionId: String? = null,
    val messages: List<ChatMessage> = emptyList(),
    val inputText: String = "",
    val canSend: Boolean = false,
    val canAskQuestion: Boolean = true,
    val isLoading: Boolean = false,
    val isStreaming: Boolean = false,
    val threads: List<ChatThread> = emptyList(),
    val activeThreadId: String? = null,
    val copiedMessageId: String? = null,
    val showPaywall: Boolean = false,
    val errorMessage: String? = null,
    val suggestedQuestions: List<String> = emptyList(),
    val interruptedQuestion: String? = null,
    // Mirrors iOS HistorySettingsManager.isHistoryEnabled — when false the history sheet shows
    // a 'history turned off' empty state and persistence calls are skipped.
    val isHistoryEnabled: Boolean = true,
    // Mirrors iOS QuotaExhaustedView guest branching (ChatView.swift:93-109, 180-191) — when the
    // current session is a guest the paywall sheet shows a sign-in path that preserves birth data
    // before forcing auth; account users see the upgrade path.
    val isGuestUser: Boolean = false,
    // Mirrors iOS onSignIn navigation hook — set when the user requests sign-out from the
    // quota-exhausted sheet so the host screen can route to AuthScreen.
    val navigateToAuth: Boolean = false,
    // Mirrors iOS cosmic progress timer (ChatViewModel.startCosmicProgressTimer:545-562) —
    // index into the 10-step rotation; UI resolves it to a localized string. Null = no rotation.
    val cosmicProgressIndex: Int? = null,
    val cosmicProgressStep: String? = null,
    // ── Profile context indicator (parity with iOS ChatHeader Gold capsule) ──
    /** True when the active profile is the signed-in user's own self profile. */
    val isUsingSelfProfile: Boolean = true,
    /** Display name for the active profile — rendered in the "Viewing as <name>" capsule. */
    val activeProfileName: String = "",
    // ── Pagination (parity with iOS WindowManager) ──
    /** True when older messages exist for the active thread. Drives the inline "Load earlier" button. */
    val hasOlderMessages: Boolean = false,
    /** True while a "Load earlier" fetch is in flight; UI shows a small spinner. */
    val isLoadingOlder: Boolean = false,
    // ── Account quota interstitial (parity with iOS QuotaExhaustedView for non-guest path) ──
    /** Custom server-supplied quota message body. Empty = use default upgrade copy. */
    val quotaDetails: String = "",
    /**
     * Mirrors iOS QuotaExhaustedView fair-use detection — server-supplied `reason` code
     * (e.g. "fair_use_violation", "upgrade_required") that the sheet uses to branch
     * between the upgrade interstitial and the "Usage Restricted / Contact Support" copy.
     */
    val quotaReason: String? = null,
    /** Server-supplied plan id when `reason=upgrade_required`, used for analytics. */
    val quotaPlanId: String? = null,
    /** Optional support email passed through to the fair-use mailto handler. */
    val quotaSupportEmail: String? = null,
    /** Toggle for the upgrade interstitial sheet (account users only). */
    val showQuotaExhaustedAccountSheet: Boolean = false,
    /** Set when the user taps Upgrade in the interstitial — host opens SubscriptionScreen. */
    val navigateToSubscription: Boolean = false,
)

class UpgradeRequiredException : Exception("upgrade_required")

// Mirrors iOS StreamingPredictionService quota-error handling. Backend SSE error
// events carry a `reason` field; map to typed exceptions so the VM can show the
// correct user-facing message (string resource) instead of the raw server text.
class DailyLimitException(message: String? = null) : Exception(message ?: "daily_limit_reached")
class GuestLimitException(message: String? = null) : Exception(message ?: "overall_limit_reached")

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val repository: ChatRepository,
    // iOS parity (ChatView.swift signOutAndReauth): used by requestSignInFromQuota
    // to perform a partial sign-out so AuthScreen routes to login UI without
    // bouncing back to Main.
    private val authRepository: com.destinyai.astrology.data.repository.AuthRepository,
    private val api: AstroApiService,
    private val prefs: UserPreferences,
    private val quotaManager: QuotaManager,
    private val profileChangeBus: ProfileChangeBus,
    private val profileContextManager: com.destinyai.astrology.services.ProfileContextManager,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState

    // Mirrors iOS ChatViewModel.lastSentQuery / streamingTask — we cancel the active
    // stream when the app backgrounds and remember the question for the Retry banner.
    private var streamJob: Job? = null
    private var cosmicProgressJob: Job? = null
    private var lastSentQuery: String? = null

    // Mirrors iOS ChatViewModel.pendingDisplayLabel (ChatView.swift:11-12,118,146):
    // when a contextual home query is opened (e.g. "Today's outlook" expands to a long
    // prompt), the user bubble should show the SHORT label, not the raw question text.
    // Set on the first sendMessage() then cleared so subsequent sends use raw input.
    var pendingDisplayLabel: String? = null

    // Mirrors iOS pageSize=20 (ChatView.swift:512-644) — incremental history pagination state.
    private var historyOffset: Int = 0
    private var historyEndReached: Boolean = false
    private var historyLoading: Boolean = false

    private companion object {
        const val HISTORY_PAGE_SIZE: Int = 20
    }

    private val lifecycleObserver = object : DefaultLifecycleObserver {
        override fun onStop(owner: LifecycleOwner) {
            // App backgrounded mid-stream → cancel, mark interrupted, scrub orphan bubble.
            handleBackgroundExpiry()
        }

        override fun onStart(owner: LifecycleOwner) {
            // App foregrounded → re-sync quota so canAskQuestion is accurate.
            handleAppForeground()
        }
    }

    // Mirrors iOS ChatViewModel.addWelcomeMessage() (244-263): formats `chat_welcome_greeting`
    // with the active profile's first name (defaulting to a generic greeting until prefs load).
    private var profileFirstName: String = ""

    private fun buildWelcomeMessage(): ChatMessage = ChatMessage(
        id = "welcome",
        role = ChatMessage.Role.ASSISTANT,
        content = if (profileFirstName.isNotBlank()) {
            appContext.getString(R.string.chat_welcome_greeting, profileFirstName)
        } else {
            "Namaste! I'm your Vedic astrology guide. Ask me anything about your chart, destiny, or daily insights."
        },
    )

    private val welcomeMessage: ChatMessage
        get() = buildWelcomeMessage()

    init {
        _uiState.update {
            it.copy(
                sessionId = UUID.randomUUID().toString(),
                messages = listOf(welcomeMessage),
            )
        }
        // Load active profile name from prefs and re-render welcome message once available.
        viewModelScope.launch {
            val name = prefs.getUserName().orEmpty().trim()
            if (name.isNotEmpty()) {
                profileFirstName = name.substringBefore(' ')
                _uiState.update { state ->
                    val msgs = state.messages
                    if (msgs.size == 1 && msgs.first().id == "welcome") {
                        state.copy(messages = listOf(welcomeMessage))
                    } else {
                        state
                    }
                }
            }
        }
        // Subscribe to follow-up suggestions emitted by the repository's terminal `answer` event.
        viewModelScope.launch {
            repository.progressEvents.collect { ev ->
                when (ev) {
                    is ChatStreamEvent.FollowUpSuggestions ->
                        _uiState.update { it.copy(suggestedQuestions = ev.suggestions) }
                    is ChatStreamEvent.Metadata -> {
                        // Patch the most recent assistant message with tool/source/advice/exec/trace
                        // metadata so the reading layout can render chips, depth layers, exec pill
                        // and inline rating (parity with iOS ChatViewModel hydrate-on-answer flow).
                        _uiState.update { state ->
                            val msgs = state.messages.toMutableList()
                            val idx = msgs.indexOfLast { it.role == ChatMessage.Role.ASSISTANT }
                            if (idx >= 0) {
                                val msg = msgs[idx]
                                msgs[idx] = msg.copy(
                                    toolCalls = if (ev.toolCalls.isNotEmpty()) ev.toolCalls else msg.toolCalls,
                                    sources = if (ev.sources.isNotEmpty()) ev.sources else msg.sources,
                                    advice = ev.advice ?: msg.advice,
                                    timing = ev.timing ?: msg.timing,
                                    executionTimeMs = if (ev.executionTimeMs > 0.0) ev.executionTimeMs else msg.executionTimeMs,
                                    traceId = ev.traceId ?: msg.traceId,
                                )
                            }
                            state.copy(messages = msgs)
                        }
                    }
                    else -> Unit
                }
            }
        }
        // Mirrors iOS HistorySettingsManager.shared.isHistoryEnabled — observe the toggle so the
        // history sheet renders the disabled empty state immediately when the user flips it.
        viewModelScope.launch {
            prefs.isHistoryEnabledFlow.collect { enabled ->
                _uiState.update { it.copy(isHistoryEnabled = enabled) }
            }
        }
        // Mirrors iOS QuotaExhaustedView guest detection (ChatView.swift:93-109) — observe the
        // guest flag so the paywall can branch between sign-in and upgrade actions.
        viewModelScope.launch {
            prefs.isGuestUserFlow.collect { isGuest ->
                _uiState.update { it.copy(isGuestUser = isGuest) }
            }
        }
        // Mirrors iOS observeAppLifecycle() — cancel stream on background, recover on foreground.
        runCatching {
            ProcessLifecycleOwner.get().lifecycle.addObserver(lifecycleObserver)
        }
        // Mirrors iOS .activeProfileChanged → handleProfileSwitch (ChatViewModel.swift:170-194):
        // when the active profile flips, cancel stream, clear messages, reset session, reload threads
        // and welcome greeting so chat is scoped to the new profile.
        viewModelScope.launch {
            profileChangeBus.events.collect {
                handleProfileSwitch()
            }
        }
        // iOS parity (ChatView.swift:169-171): observe DataStore activeProfileId so the chat
        // also resets when ProfileContextManager is mutated outside the bus (e.g. deep link).
        viewModelScope.launch {
            prefs.activeProfileIdFlow
                .drop(1)
                .collect {
                    handleProfileSwitch()
                }
        }
        // iOS parity (ChatHeader Gold capsule): the "Viewing as <name>" indicator hinges on
        // whether the active profile equals the signed-in user.  Compute it from the persisted
        // active profile id + user email and refresh on every change so the banner stays
        // accurate when the user switches between self and partner profiles.
        viewModelScope.launch {
            kotlinx.coroutines.flow.combine(
                prefs.activeProfileIdFlow,
                kotlinx.coroutines.flow.flow { emit(prefs.getUserEmail() ?: "") },
            ) { activeId, email -> activeId to email }
                .collect { (activeId, email) ->
                    refreshProfileContextIndicator(activeId, email)
                }
        }
    }

    /**
     * Reads the active partner profile name and updates the "Viewing as <name>"
     * capsule. iOS parity (AppHeader.swift:122-138 + ProfileContextManager.swift:36-43):
     * the capsule is hidden when `isUsingSelf` (i.e. the active profile is the
     * account owner's own profile, even if it's stored as a partner row with a
     * UUID id and `isSelf=true`). Comparing activeId to email alone is wrong —
     * the primary profile's id is a UUID, not the email.
     */
    private suspend fun refreshProfileContextIndicator(activeId: String?, email: String) {
        val isSelf = runCatching { profileContextManager.isUsingSelfProfile() }
            .getOrDefault(activeId.isNullOrBlank() || activeId == email)
        val name = if (isSelf) {
            ""
        } else {
            runCatching { profileContextManager.activeProfileName() }
                .getOrNull()
                .orEmpty()
                .substringBefore(' ')
        }
        _uiState.update { it.copy(isUsingSelfProfile = isSelf, activeProfileName = name) }
    }

    /**
     * Mirrors iOS NotificationCenter.activeProfileChanged → handleProfileSwitch.
     * Public so the host screen can call it directly when nav arrives with an
     * embedded profile id (deep link from a notification, for example).
     */
    fun handleProfileSwitch() {
        streamJob?.cancel()
        streamJob = null
        stopCosmicProgressTimer()
        viewModelScope.launch {
            // iOS parity (ChatViewModel.swift:247): the welcome greeting uses the
            // **active** profile name (partner when one is selected), not the
            // owner's. Falls back to owner's prefs name for self.
            val activeName = runCatching { profileContextManager.activeProfileName() }
                .getOrNull()
                .orEmpty()
                .ifBlank { prefs.getUserName().orEmpty() }
                .trim()
            profileFirstName = if (activeName.isNotEmpty()) activeName.substringBefore(' ') else ""
            _uiState.update {
                it.copy(
                    sessionId = UUID.randomUUID().toString(),
                    messages = listOf(welcomeMessage),
                    inputText = "",
                    canSend = false,
                    isStreaming = false,
                    activeThreadId = null,
                    suggestedQuestions = emptyList(),
                    interruptedQuestion = null,
                    threads = emptyList(),
                )
            }
            val history = repository.loadHistory()
            _uiState.update { it.copy(threads = history) }
        }
    }

    override fun onCleared() {
        runCatching {
            ProcessLifecycleOwner.get().lifecycle.removeObserver(lifecycleObserver)
        }
        super.onCleared()
    }

    fun updateInput(text: String) {
        _uiState.update { state ->
            state.copy(
                inputText = text,
                canSend = text.isNotBlank() && !state.isLoading && !state.isStreaming && state.canAskQuestion,
            )
        }
    }

    fun sendMessage() {
        val state = _uiState.value
        if (!state.canAskQuestion) return
        val input = state.inputText.trim()
        if (input.isBlank()) return
        lastSentQuery = input

        streamJob = viewModelScope.launch {
            // Pre-flight quota check before invoking streaming prediction (mirrors iOS canAsk).
            // Routed through QuotaManager so the same gating + per-feature logic is shared with
            // any future call site (compatibility, profile-add, etc.).
            val email = prefs.getUserEmail()
            if (email != null) {
                try {
                    val access = quotaManager.canAccessFeature(QuotaManager.FeatureID.AI_QUESTIONS, email)
                    if (!access.canAccess) {
                        when (access.reason) {
                            "daily_limit_reached" -> _uiState.update {
                                it.copy(
                                    canAskQuestion = false,
                                    canSend = false,
                                    errorMessage = "Daily question limit reached. Resets at ${access.resetAt}",
                                )
                            }
                            "overall_limit_reached" -> {
                                // iOS parity (ChatViewModel.swift:339-349): overall-limit must surface the
                                // QuotaExhaustedView sheet — guest path shows sign-in CTA, account path
                                // shows the upgrade interstitial. NEVER fall back to a red error banner.
                                if (_uiState.value.isGuestUser) {
                                    _uiState.update {
                                        it.copy(
                                            canAskQuestion = false,
                                            canSend = false,
                                            showPaywall = true,
                                        )
                                    }
                                } else {
                                    _uiState.update {
                                        it.copy(
                                            canAskQuestion = false,
                                            canSend = false,
                                            showQuotaExhaustedAccountSheet = true,
                                            quotaDetails = access.upgradeCta?.message ?: "",
                                            quotaReason = "overall_limit_reached",
                                            quotaPlanId = access.planId,
                                        )
                                    }
                                }
                            }
                            "upgrade_required", "feature_not_available" -> {
                                // iOS QuotaExhaustedView (ChatView.swift:93-112) shows BOTH guests and
                                // account users an interstitial sheet first; the upgrade SubscriptionScreen
                                // is only opened after the user taps "Upgrade".
                                if (_uiState.value.isGuestUser) {
                                    _uiState.update {
                                        it.copy(
                                            canAskQuestion = false,
                                            canSend = false,
                                            showPaywall = true,
                                        )
                                    }
                                } else {
                                    _uiState.update {
                                        it.copy(
                                            canAskQuestion = false,
                                            canSend = false,
                                            showQuotaExhaustedAccountSheet = true,
                                            quotaDetails = access.reason ?: "",
                                            quotaReason = access.reason,
                                        )
                                    }
                                }
                            }
                            else -> _uiState.update {
                                it.copy(
                                    canAskQuestion = false,
                                    canSend = false,
                                    errorMessage = "Unable to send question right now.",
                                )
                            }
                        }
                        return@launch
                    }
                } catch (e: Exception) {
                    _uiState.update { it.copy(errorMessage = e.message) }
                    return@launch
                }
            }

            val userMsg = ChatMessage(
                id = UUID.randomUUID().toString(),
                role = ChatMessage.Role.USER,
                // Mirrors iOS pendingDisplayLabel (ChatView.swift:11-12, 118, 146): if a
                // short label was supplied by a contextual home query, show that in the
                // user bubble instead of the raw expanded question. Consumed once.
                content = pendingDisplayLabel?.takeIf { it.isNotBlank() } ?: input,
                createdAtMs = System.currentTimeMillis(),
            )
            pendingDisplayLabel = null
            _uiState.update {
                it.copy(
                    messages = it.messages + userMsg,
                    inputText = "",
                    canSend = false,
                    isStreaming = true,
                    errorMessage = null,
                    suggestedQuestions = emptyList(),
                )
            }
            // Mirrors iOS startCosmicProgressTimer — rotate cosmic step every 1.5s while streaming.
            startCosmicProgressTimer()

            val assistantId = UUID.randomUUID().toString()
            var accumulated = ""

            repository.sendMessage(_uiState.value.sessionId ?: "", input).collect { result ->
                result
                    .onSuccess { chunk ->
                        accumulated += chunk
                        val assistantMsg = ChatMessage(
                            id = assistantId,
                            role = ChatMessage.Role.ASSISTANT,
                            content = accumulated,
                            isStreaming = true,
                            createdAtMs = System.currentTimeMillis(),
                        )
                        _uiState.update { s ->
                            val msgs = s.messages.filterNot { it.id == assistantId } + assistantMsg
                            s.copy(messages = msgs)
                        }
                    }
                    .onFailure { e ->
                        // Mirrors iOS quota-error mapping (StreamingPredictionService).
                        stopCosmicProgressTimer()
                        when (e) {
                            is UpgradeRequiredException, is GuestLimitException -> {
                                if (_uiState.value.isGuestUser) {
                                    _uiState.update { it.copy(isStreaming = false, showPaywall = true) }
                                } else {
                                    _uiState.update {
                                        it.copy(
                                            isStreaming = false,
                                            showQuotaExhaustedAccountSheet = true,
                                            quotaDetails = e.message ?: "",
                                            quotaReason = if (e is UpgradeRequiredException) "upgrade_required" else "overall_limit_reached",
                                        )
                                    }
                                }
                            }
                            is DailyLimitException ->
                                _uiState.update {
                                    it.copy(
                                        isStreaming = false,
                                        errorMessage = e.message,
                                        interruptedQuestion = lastSentQuery,
                                        messages = it.messages.filterNot { m -> m.id == assistantId },
                                    )
                                }
                            else ->
                                _uiState.update {
                                    it.copy(
                                        isStreaming = false,
                                        errorMessage = e.message
                                            ?: "Unable to reach the prediction service. Please try again.",
                                        interruptedQuestion = lastSentQuery,
                                        messages = it.messages.filterNot { m -> m.id == assistantId },
                                    )
                                }
                        }
                    }
            }

            // Mark last assistant message as no longer streaming
            stopCosmicProgressTimer()
            _uiState.update { s ->
                s.copy(
                    isStreaming = false,
                    messages = s.messages.map { msg ->
                        if (msg.id == assistantId) msg.copy(isStreaming = false) else msg
                    },
                )
            }

            // Record successful feature usage so backend quota counters stay accurate
            // (mirrors iOS recordFeatureUsage call site after successful sendQuery).
            // Only record when we actually accumulated a response — failed streams skip.
            if (email != null && accumulated.isNotBlank()) {
                runCatching {
                    quotaManager.recordFeatureUsage(QuotaManager.FeatureID.AI_QUESTIONS, email)
                }
            }
        }
    }

    fun startNewChat() {
        _uiState.update {
            it.copy(
                sessionId = UUID.randomUUID().toString(),
                messages = listOf(welcomeMessage),
                inputText = "",
                canSend = false,
                activeThreadId = null,
            )
        }
        viewModelScope.launch {
            val history = repository.loadHistory()
            _uiState.update { it.copy(threads = history) }
        }
    }

    /**
     * Mirrors iOS ChatViewModel.loadDefaultState (ChatView.swift:154-157):
     * on plain open (no deep-link question, no thread id), resume the most recent
     * thread for the active profile so the user lands back where they left off.
     * Falls back to a fresh new-chat state when no history exists.
     */
    fun loadDefaultState() {
        viewModelScope.launch {
            val threads = runCatching { repository.loadHistory() }.getOrElse { emptyList() }
            _uiState.update { it.copy(threads = threads) }
            val latest = threads.maxByOrNull { it.updatedAtMs }
            if (latest != null) {
                val messages = runCatching { repository.loadThread(latest.id) }.getOrElse { emptyList() }
                if (messages.isNotEmpty()) {
                    val older = messages.size >= HISTORY_PAGE_SIZE
                    _uiState.update {
                        it.copy(
                            activeThreadId = latest.id,
                            messages = messages,
                            hasOlderMessages = older,
                        )
                    }
                }
            }
        }
    }

    fun copyMessage(messageId: String) {
        _uiState.update { it.copy(copiedMessageId = messageId) }
    }

    fun loadHistory() {
        viewModelScope.launch {
            // Reset to first page (mirrors iOS loadFirstPage in ChatView.swift:512-644).
            historyOffset = 0
            historyEndReached = false
            val history = repository.loadHistoryPaginated(0, HISTORY_PAGE_SIZE)
            historyOffset = history.size
            historyEndReached = history.size < HISTORY_PAGE_SIZE
            _uiState.update { it.copy(threads = history) }
        }
    }

    /**
     * Mirrors iOS loadMore (ChatView.swift:512-644) — appends the next page of history threads
     * when the LazyColumn reaches near-end. No-op when already loading or end reached.
     */
    fun loadMoreHistory() {
        if (historyEndReached || historyLoading) return
        historyLoading = true
        viewModelScope.launch {
            try {
                val page = repository.loadHistoryPaginated(historyOffset, HISTORY_PAGE_SIZE)
                if (page.isEmpty() || page.size < HISTORY_PAGE_SIZE) historyEndReached = true
                if (page.isNotEmpty()) {
                    historyOffset += page.size
                    _uiState.update { state ->
                        // Filter out any entries already present (paranoia against stale offsets).
                        val existingIds = state.threads.map { it.id }.toSet()
                        val newOnes = page.filterNot { existingIds.contains(it.id) }
                        state.copy(threads = state.threads + newOnes)
                    }
                }
            } finally {
                historyLoading = false
            }
        }
    }

    fun openThread(threadId: String) {
        viewModelScope.launch {
            val messages = repository.loadThread(threadId)
            // Heuristic mirroring iOS WindowManager: if the loaded slice already has 20+ messages
            // assume there are older ones still on disk/server. UI flips false after a successful
            // loadOlderMessages() returns an empty page.
            val older = messages.size >= HISTORY_PAGE_SIZE
            _uiState.update { it.copy(activeThreadId = threadId, messages = messages, hasOlderMessages = older) }
        }
    }

    /**
     * Mirrors iOS WindowManager.loadOlderMessages — fetch the next page of older
     * messages and PREPEND them to the visible list. Drives the inline "Load earlier
     * messages" button at the top of the chat scroll.
     */
    fun loadOlderMessages() {
        val state = _uiState.value
        if (state.isLoadingOlder || !state.hasOlderMessages) return
        val threadId = state.activeThreadId ?: return
        val earliest = state.messages.minOfOrNull { it.createdAtMs.takeIf { ms -> ms > 0L } ?: Long.MAX_VALUE }
            ?: System.currentTimeMillis()
        _uiState.update { it.copy(isLoadingOlder = true) }
        viewModelScope.launch {
            val older = runCatching {
                repository.loadOlderMessages(threadId, earliest, HISTORY_PAGE_SIZE)
            }.getOrElse { emptyList() }
            _uiState.update { s ->
                s.copy(
                    isLoadingOlder = false,
                    hasOlderMessages = older.size >= HISTORY_PAGE_SIZE,
                    messages = older + s.messages,
                )
            }
        }
    }

    /**
     * Mirrors iOS InlineMessageRating.selectRating(_:) → FeedbackService.submit.
     * Optimistically latches the local rating on the message so the UI immediately
     * shows the "thank-you" check + filled stars; the server submission is best-effort.
     */
    fun submitRating(messageId: String, rating: Int) {
        if (rating !in 1..5) return
        val state = _uiState.value
        val msg = state.messages.firstOrNull { it.id == messageId } ?: return
        val userQuery = state.messages
            .lastOrNull { it.role == ChatMessage.Role.USER && it.createdAtMs <= msg.createdAtMs }
            ?.content
            .orEmpty()
        // Optimistic local update
        _uiState.update { s ->
            s.copy(messages = s.messages.map { if (it.id == messageId) it.copy(rating = rating) else it })
        }
        viewModelScope.launch {
            val email = prefs.getUserEmail()
            runCatching {
                repository.submitRating(
                    traceId = msg.traceId,
                    sessionId = state.sessionId,
                    userEmail = email,
                    query = userQuery,
                    responseText = msg.content,
                    rating = rating,
                )
            }
        }
    }

    fun setSuggestedQuestions(questions: List<String>) {
        _uiState.update { it.copy(suggestedQuestions = questions) }
    }

    fun dismissSuggestedQuestions() {
        _uiState.update { it.copy(suggestedQuestions = emptyList()) }
    }

    fun setInterruptedQuestion(question: String) {
        _uiState.update { it.copy(interruptedQuestion = question) }
    }

    fun retryInterruptedQuestion() {
        val question = _uiState.value.interruptedQuestion ?: return
        _uiState.update { it.copy(interruptedQuestion = null) }
        updateInput(question)
        sendMessage()
    }

    fun dismissPaywall() {
        _uiState.update { it.copy(showPaywall = false) }
    }

    /** Mirrors iOS QuotaExhaustedView dismiss for the account-user (non-guest) path. */
    fun dismissQuotaExhaustedAccountSheet() {
        _uiState.update { it.copy(showQuotaExhaustedAccountSheet = false) }
    }

    /**
     * Mirrors iOS QuotaExhaustedView "Upgrade" tap (ChatView.swift:93-112): close the
     * interstitial then surface SubscriptionScreen as a separate sheet.
     */
    fun requestUpgradeFromQuotaSheet() {
        _uiState.update { it.copy(showQuotaExhaustedAccountSheet = false, navigateToSubscription = true) }
    }

    fun consumeNavigateToSubscription() {
        _uiState.update { it.copy(navigateToSubscription = false) }
    }

    /**
     * Mirrors iOS QuotaExhaustedView.onSignIn → signOutAndReauth() (ChatView.swift:97, 180-191):
     * partial sign-out (preserves birth data) so AuthScreen lands on the login UI instead of
     * bouncing back to Main via its LaunchedEffect(state.isAuthenticated). The new registered
     * account flow re-uses the preserved guest birth data automatically.
     */
    fun requestSignInFromQuota() {
        viewModelScope.launch {
            // iOS parity (ChatView.swift:187-188): clear the auth/session state to
            // trigger AuthScreen's login UI; AuthScreen.loadSession() returning a
            // null user is what makes state.isAuthenticated=false stick.
            runCatching { authRepository.signOutPreserveBirthData() }
            _uiState.update { it.copy(showPaywall = false, navigateToAuth = true) }
        }
    }

    fun consumeNavigateToAuth() {
        _uiState.update { it.copy(navigateToAuth = false) }
    }

    // Mirrors iOS UserDefaults "userResponseLength" — surfaced as a Flow so the
    // ChatInputBar slider/sheet can render the persisted choice and update it.
    val responseLength: kotlinx.coroutines.flow.Flow<String>
        get() = prefs.responseLengthFlow

    fun setResponseLength(value: String) {
        viewModelScope.launch { runCatching { prefs.setResponseLength(value) } }
    }

    fun pinThread(threadId: String) {
        // Optimistically toggle local state, then persist via repository (DB + best-effort API).
        // Mirrors iOS ChatViewModel.togglePinThread(id:) which delegates to dataManager.
        val newPinned = _uiState.value.threads.firstOrNull { it.id == threadId }?.let { !it.isPinned } ?: return
        _uiState.update { state ->
            state.copy(
                threads = state.threads.map { thread ->
                    if (thread.id == threadId) thread.copy(isPinned = newPinned) else thread
                },
            )
        }
        viewModelScope.launch {
            runCatching { repository.setThreadPinned(threadId, newPinned) }
        }
    }

    fun deleteThread(threadId: String) {
        // Optimistically remove from list, then persist deletion via repository (DB + API).
        // Mirrors iOS ChatViewModel.deleteThread(id:) which calls dataManager.deleteThread.
        _uiState.update { state ->
            state.copy(threads = state.threads.filterNot { it.id == threadId })
        }
        viewModelScope.launch {
            runCatching { repository.deleteThread(threadId) }
        }
    }

    // Mirrors iOS handleBackgroundExpiry — cancels active stream, sets interruptedQuestion
    // for the Retry banner, scrubs orphan streaming assistant bubble.
    private fun handleBackgroundExpiry() {
        stopCosmicProgressTimer()
        val job = streamJob
        if (job != null && job.isActive) {
            job.cancel()
            streamJob = null
            val q = lastSentQuery
            _uiState.update { s ->
                s.copy(
                    isStreaming = false,
                    interruptedQuestion = q,
                    messages = s.messages.filterNot { it.isStreaming },
                )
            }
        }
    }

    // Mirrors iOS startCosmicProgressTimer (1.5s cadence cycling 10 messages).
    private fun startCosmicProgressTimer() {
        cosmicProgressJob?.cancel()
        cosmicProgressJob = viewModelScope.launch {
            var i = 0
            _uiState.update { it.copy(cosmicProgressIndex = i) }
            while (true) {
                delay(1500)
                i = (i + 1) % 10
                _uiState.update { it.copy(cosmicProgressIndex = i) }
            }
        }
    }

    private fun stopCosmicProgressTimer() {
        cosmicProgressJob?.cancel()
        cosmicProgressJob = null
        _uiState.update { it.copy(cosmicProgressIndex = null) }
    }

    // Mirrors iOS handleAppForeground — re-sync quota so canAskQuestion is accurate
    // and no permanent "streaming" bubbles remain after returning from background.
    //
    // Hardened against transient backend failures (network error, 401, misrouted
    // BuildConfig URL): only flip canAskQuestion to true on an explicit successful
    // allow. Any thrown exception or a `false` response from a hiccupy backend is
    // ignored, leaving the prior state intact so the user is never permanently
    // stranded with input disabled by a transient backend issue.
    private fun handleAppForeground() {
        viewModelScope.launch {
            val email = prefs.getUserEmail() ?: return@launch
            try {
                val access = quotaManager.canAccessFeature(QuotaManager.FeatureID.AI_QUESTIONS, email)
                if (access.canAccess) {
                    _uiState.update { it.copy(canAskQuestion = true) }
                }
                // Explicit non-allow: leave canAskQuestion alone here. The real
                // gating happens in sendMessage() where the reason (daily limit,
                // upgrade required, etc.) is mapped to the proper UI state.
            } catch (_: Exception) {
                // Backend unreachable or auth failed — preserve prior state.
            }
        }
    }
}
