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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
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
    // New-Chat empty-state starter questions — sourced from homeRepository.getSuggestedQuestions()
    // so they match the Home tab exactly (server-personalized). Empty until loaded; the empty
    // state falls back to the static defaults meanwhile.
    val starterQuestions: List<String> = emptyList(),
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

// iOS parity (StreamingPredictionService backpressure event): server is shedding
// load. The VM catches this and transparently replays via the non-streaming endpoint.
class BackpressureException(val retryAfterSeconds: Int = 0) : Exception("backpressure")

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val repository: ChatRepository,
    // Shared source of the home-screen starter questions (getSuggestedQuestions()) so the
    // New-Chat empty state shows the SAME questions the Home tab does (server-personalized,
    // language-aware), not a divergent hardcoded list.
    private val homeRepository: com.destinyai.astrology.data.repository.HomeRepository,
    // iOS parity (ChatView.swift signOutAndReauth): used by requestSignInFromQuota
    // to perform a partial sign-out so AuthScreen routes to login UI without
    // bouncing back to Main.
    private val authRepository: com.destinyai.astrology.data.repository.AuthRepository,
    private val api: AstroApiService,
    private val prefs: UserPreferences,
    private val quotaManager: QuotaManager,
    private val profileChangeBus: ProfileChangeBus,
    private val profileContextManager: com.destinyai.astrology.services.ProfileContextManager,
    private val appStartupService: com.destinyai.astrology.services.AppStartupService,
    // Cat 10: connectivity for the Chat offline banner (parity with HomeViewModel).
    private val networkMonitor: com.destinyai.astrology.services.NetworkMonitor,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState

    // Cat 10: connectivity for the Chat offline banner (parity with HomeViewModel.isOnline).
    val isOnline: StateFlow<Boolean> = networkMonitor.isOnline
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, true)

    // Mirrors iOS ChatViewModel.lastSentQuery / streamingTask — we cancel the active
    // stream when the app backgrounds and remember the question for the Retry banner.
    private var streamJob: Job? = null
    private var cosmicProgressJob: Job? = null
    // iOS parity (ChatViewModel.startSmoothPump:1150-1224): a 60Hz display interpolator
    // reveals the arrived text into the visible bubble at ~70 ch/s (±20% jitter) so bursty
    // backend token frames render as steady human-paced typing. pumpTarget is the full text
    // that has ARRIVED; pumpJob reveals it progressively into the message content.
    private var pumpJob: Job? = null
    @Volatile private var pumpTarget: String = ""
    @Volatile private var pumpRevealed: Int = 0
    private var lastSentQuery: String? = null
    // iOS parity (pendingPostUpgradeQuery): the question blocked by a quota gate, buffered
    // so it auto-resends the moment the user upgrades (isPremium false→true observer above).
    private var pendingPostUpgradeQuery: String? = null
    // iOS parity: idempotency key for the in-flight send, reused by the sync fallback.
    private var currentIdempotencyKey: String? = null

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
        // Load the home-screen starter questions so the New-Chat empty state matches the
        // Home tab (same getSuggestedQuestions() source, server-personalized). Best-effort:
        // on failure the empty state keeps its static default starters.
        viewModelScope.launch {
            val qs = runCatching { homeRepository.getSuggestedQuestions() }.getOrDefault(emptyList())
            if (qs.isNotEmpty()) _uiState.update { it.copy(starterQuestions = qs) }
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
                    is ChatStreamEvent.FollowUpSuggestions -> {
                        // #26: deduplicate against already-asked user messages so the same
                        // question never appears again in the suggestion chips.
                        val askedTexts = _uiState.value.messages
                            .filter { it.role == ChatMessage.Role.USER }
                            .map { it.content.trim().lowercase() }
                            .toSet()
                        val unique = ev.suggestions
                            .distinctBy { it.trim().lowercase() }
                            .filterNot { askedTexts.contains(it.trim().lowercase()) }
                        _uiState.update { it.copy(suggestedQuestions = unique) }
                    }
                    is ChatStreamEvent.ProgressStep -> {
                        // FIX D: iOS parity (ChatViewModel.swift:1332-1344) — map the backend
                        // display_key to a localized step label and override the canned rotation.
                        // Fall back to canned rotation (cosmicProgressIndex) when key is null/unknown.
                        val stepLabel = mapProgressDisplayKey(ev.displayKey)
                        if (stepLabel != null) {
                            _uiState.update { it.copy(cosmicProgressStep = stepLabel) }
                        }
                        // If isDone clear the override so the canned rotation resumes.
                        if (ev.isDone) {
                            _uiState.update { it.copy(cosmicProgressStep = null) }
                        }
                    }
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
        // iOS parity (ChatViewModel pendingPostUpgradeQuery): when the user completes a
        // purchase from the chat paywall, isPremium flips false→true. Re-enable the composer
        // and auto-resend the question that was blocked, without waiting for a foreground/
        // restart. drop(1) skips the initial replay of the current value.
        viewModelScope.launch {
            var wasPremium = quotaManager.isPremium.value
            quotaManager.isPremium.drop(1).collect { nowPremium ->
                if (nowPremium && !wasPremium) {
                    _uiState.update { it.copy(canAskQuestion = true, showPaywall = false) }
                    val replay = pendingPostUpgradeQuery
                    pendingPostUpgradeQuery = null
                    if (!replay.isNullOrBlank()) {
                        updateInput(replay)
                        sendMessage()
                    }
                }
                wasPremium = nowPremium
            }
        }
        // iOS parity (ChatView.swift:169-171): observe DataStore activeProfileId so the chat
        // also resets when ProfileContextManager is mutated outside the bus (e.g. deep link).
        viewModelScope.launch {
            prefs.activeProfileIdFlow
                .distinctUntilChanged()
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
        currentIdempotencyKey = UUID.randomUUID().toString()
        // iOS parity: start a foreground service so the stream survives backgrounding.
        // invokeOnCompletion stops it for any outcome (success / error / cancellation).
        com.destinyai.astrology.services.ChatStreamingForegroundService.start(appContext)

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
                            "daily_limit_reached" -> {
                                // iOS parity (QuotaExhaustedView.swift:206-208): dedicated daily-limit
                                // sheet with a localized "resets" message, not a hardcoded error banner.
                                val resetLabel = formatResetTime(access.resetAt)
                                _uiState.update {
                                    it.copy(
                                        canAskQuestion = false,
                                        canSend = false,
                                        showQuotaExhaustedAccountSheet = true,
                                        quotaReason = "daily_limit_reached",
                                        quotaDetails = resetLabel,
                                        quotaPlanId = access.planId,
                                    )
                                }
                            }
                            "fair_use_violation" -> _uiState.update {
                                // iOS parity (QuotaExhaustedView.swift:29-39): Plus fair-use cap →
                                // "Usage Restricted / Contact Support" sheet, not a generic banner.
                                it.copy(
                                    canAskQuestion = false,
                                    canSend = false,
                                    showQuotaExhaustedAccountSheet = true,
                                    quotaReason = "fair_use_violation",
                                    quotaDetails = access.upgradeCta?.message ?: "",
                                    quotaPlanId = access.planId,
                                )
                            }
                            "subscription_expired" -> _uiState.update {
                                // iOS parity (QuotaExhaustedView.swift:185-234): lapsed paid user →
                                // "Your subscription has ended — Renew" flow, not a generic error.
                                it.copy(
                                    canAskQuestion = false,
                                    canSend = false,
                                    showQuotaExhaustedAccountSheet = true,
                                    quotaReason = "subscription_expired",
                                    quotaDetails = access.upgradeCta?.message ?: "",
                                    quotaPlanId = access.planId,
                                )
                            }
                            "overall_limit_reached" -> {
                                // iOS parity (ChatViewModel.swift:339-349): overall-limit must surface the
                                // QuotaExhaustedView sheet — guest path shows sign-in CTA, account path
                                // shows the upgrade interstitial. NEVER fall back to a red error banner.
                                //
                                // D13: the backend returns reason="overall_limit_reached" + fair-use flag
                                // for a Plus subscriber hitting the lifetime cap. Route that to the
                                // fair-use "Usage Restricted / Contact Support" sheet, NOT a dead-end
                                // upgrade paywall (Plus can't upgrade further). iOS parity.
                                val isFairUse = access.isFairUseViolation ||
                                    access.planId.equals("plus", ignoreCase = true)
                                if (isFairUse) {
                                    _uiState.update {
                                        it.copy(
                                            canAskQuestion = false,
                                            canSend = false,
                                            showQuotaExhaustedAccountSheet = true,
                                            quotaReason = "fair_use_violation",
                                            quotaDetails = access.upgradeCta?.message ?: "",
                                            quotaPlanId = access.planId,
                                        )
                                    }
                                } else if (_uiState.value.isGuestUser) {
                                    pendingPostUpgradeQuery = input
                                    _uiState.update {
                                        it.copy(
                                            canAskQuestion = false,
                                            canSend = false,
                                            showPaywall = true,
                                        )
                                    }
                                } else {
                                    pendingPostUpgradeQuery = input
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
                                // Buffer the blocked question so it auto-resends post-upgrade (C11).
                                pendingPostUpgradeQuery = input
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
                    // iOS parity (QuotaManager.swift:460-473, iOS-6 fix): FAIL OPEN on a
                    // pre-flight network error. The server-side check_and_reserve on the
                    // predict endpoint is the source of truth, so a transient blip must not
                    // block a user with remaining quota — proceed and let the stream enforce.
                    android.util.Log.w("ChatViewModel", "pre-flight quota check failed — proceeding (fail-open): ${e.message}")
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
            // FIX A: set when the stream's onFailure branch fired. The empty-done
            // fallback (case 2) below must NOT run after a real failure — the failure
            // branches already handle recovery (backpressure/generic → sync) or are
            // terminal (upgrade/daily-limit paywall). Re-running sync here would
            // double-handle a quota block or re-charge the query.
            var streamFailed = false
            resetPump()

            // iOS parity (AppConfig.shouldStreamFor + ChatViewModel routing): honor the
            // server-driven streaming kill-switch / cohort / min-version gate. When
            // streaming is disabled for this user, use the non-streaming /predict path.
            val useStreaming = appStartupService.shouldStreamFor(prefs.getUserEmail())
            if (!useStreaming) {
                val result = repository.sendMessageSync(
                    _uiState.value.sessionId ?: "", input, currentIdempotencyKey, assistantId,
                )
                stopCosmicProgressTimer()
                result.onSuccess { answer ->
                    _uiState.update { s ->
                        val msg = ChatMessage(
                            id = assistantId,
                            role = ChatMessage.Role.ASSISTANT,
                            content = answer,
                            isStreaming = false,
                            createdAtMs = System.currentTimeMillis(),
                        )
                        s.copy(messages = s.messages.filterNot { it.id == assistantId } + msg, isStreaming = false)
                    }
                    email?.let { runCatching { quotaManager.recordFeatureUsage(QuotaManager.FeatureID.AI_QUESTIONS, it) } }
                }.onFailure { e ->
                    _uiState.update {
                        it.copy(
                            isStreaming = false,
                            errorMessage = friendlyError(e),
                            interruptedQuestion = lastSentQuery,
                        )
                    }
                }
                return@launch
            }

            repository.sendMessage(_uiState.value.sessionId ?: "", input, currentIdempotencyKey, assistantId).collect { result ->
                result
                    .onSuccess { chunk ->
                        accumulated += chunk
                        // iOS parity: feed the smooth pump; it reveals accumulated text at
                        // ~70 ch/s (or instantly under reduce-motion) into the bubble.
                        feedPump(assistantId, accumulated)
                    }
                    .onFailure { e ->
                        // Mirrors iOS quota-error mapping (StreamingPredictionService).
                        streamFailed = true
                        stopCosmicProgressTimer()
                        // Stop the reveal pump — failure branches render their own final state.
                        pumpJob?.cancel()
                        when (e) {
                            is BackpressureException -> {
                                // iOS parity (ChatViewModel.swift:1011-1019): server shed load —
                                // transparently replay via the non-streaming endpoint using the
                                // SAME idempotency key so quota isn't double-charged.
                                val recovered = runCatching {
                                    repository.sendMessageSync(
                                        _uiState.value.sessionId ?: "", input, currentIdempotencyKey, assistantId,
                                    )
                                }.getOrNull()?.getOrNull()
                                if (!recovered.isNullOrBlank()) {
                                    accumulated = stripFollowUpBlock(recovered)
                                    _uiState.update { s ->
                                        val msg = ChatMessage(
                                            id = assistantId,
                                            role = ChatMessage.Role.ASSISTANT,
                                            content = accumulated,
                                            isStreaming = false,
                                            createdAtMs = System.currentTimeMillis(),
                                        )
                                        s.copy(messages = s.messages.filterNot { it.id == assistantId } + msg, isStreaming = false)
                                    }
                                } else {
                                    _uiState.update {
                                        it.copy(
                                            isStreaming = false,
                                            errorMessage = it.errorMessage ?: "Unable to reach the prediction service. Please try again.",
                                            interruptedQuestion = lastSentQuery,
                                            messages = it.messages.filterNot { m -> m.id == assistantId },
                                        )
                                    }
                                }
                            }
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
                                        errorMessage = e.message ?: friendlyError(e),
                                        interruptedQuestion = lastSentQuery,
                                        messages = it.messages.filterNot { m -> m.id == assistantId },
                                    )
                                }
                            else -> {
                                // FIX A (case 3): iOS parity (ChatViewModel.swift:989-996) —
                                // for any generic mid-stream error that is NOT a quota/limit
                                // signal and NOT user-cancel, transparently replay via the
                                // non-streaming endpoint with the same idempotency key.
                                // Do NOT surface a banner; if sync also fails, then show the error.
                                val isCancellation = e is kotlinx.coroutines.CancellationException ||
                                    e.message?.contains("cancel", ignoreCase = true) == true
                                if (isCancellation) {
                                    _uiState.update {
                                        it.copy(
                                            isStreaming = false,
                                            errorMessage = null,
                                            messages = it.messages.filterNot { m -> m.id == assistantId },
                                        )
                                    }
                                } else {
                                    val recovered = runCatching {
                                        repository.sendMessageSync(
                                            _uiState.value.sessionId ?: "", input, currentIdempotencyKey, assistantId,
                                        )
                                    }.getOrNull()?.getOrNull()
                                    if (!recovered.isNullOrBlank()) {
                                        accumulated = stripFollowUpBlock(recovered)
                                        _uiState.update { s ->
                                            val msg = ChatMessage(
                                                id = assistantId,
                                                role = ChatMessage.Role.ASSISTANT,
                                                content = accumulated,
                                                isStreaming = false,
                                                createdAtMs = System.currentTimeMillis(),
                                            )
                                            s.copy(messages = s.messages.filterNot { it.id == assistantId } + msg, isStreaming = false)
                                        }
                                    } else {
                                        _uiState.update {
                                            it.copy(
                                                isStreaming = false,
                                                errorMessage = friendlyError(e),
                                                interruptedQuestion = lastSentQuery,
                                                messages = it.messages.filterNot { m -> m.id == assistantId },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
            }

            // Mark last assistant message as no longer streaming
            stopCosmicProgressTimer()
            // FIX A (case 2): iOS parity (ChatViewModel.swift:1041-1048) — if the stream
            // emitted a `done` event but we accumulated nothing (empty-done), transparently
            // fall through to the non-streaming endpoint rather than leaving a silent no-answer.
            if (accumulated.isBlank() && !streamFailed) {
                val recovered = runCatching {
                    repository.sendMessageSync(
                        _uiState.value.sessionId ?: "", input, currentIdempotencyKey, assistantId,
                    )
                }.getOrNull()?.getOrNull()
                if (!recovered.isNullOrBlank()) {
                    accumulated = stripFollowUpBlock(recovered)
                    _uiState.update { s ->
                        val msg = ChatMessage(
                            id = assistantId,
                            role = ChatMessage.Role.ASSISTANT,
                            content = accumulated,
                            isStreaming = false,
                            createdAtMs = System.currentTimeMillis(),
                        )
                        s.copy(messages = s.messages.filterNot { it.id == assistantId } + msg, isStreaming = false)
                    }
                    email?.let { runCatching { quotaManager.recordFeatureUsage(QuotaManager.FeatureID.AI_QUESTIONS, it) } }
                    return@launch
                }
            }
            // iOS parity: fast-drain any remaining pumped text so the final answer is
            // fully visible the instant the stream closes (no lingering partial reveal).
            drainPump(assistantId)
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
        streamJob?.invokeOnCompletion {
            com.destinyai.astrology.services.ChatStreamingForegroundService.stop(appContext)
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
                // #18: clear any follow-up chips from the previous conversation so the
                // empty state shows only the fresh starter questions.
                suggestedQuestions = emptyList(),
            )
        }
        viewModelScope.launch {
            val history = repository.loadHistory()
            // #18: re-fetch home questions on every new-chat so the starter questions
            // match the Home screen (same server-personalised source). Best-effort: keep
            // the existing starterQuestions on failure.
            val qs = runCatching { homeRepository.getSuggestedQuestions() }.getOrDefault(emptyList())
            _uiState.update { state ->
                state.copy(
                    threads = history,
                    starterQuestions = qs.ifEmpty { state.starterQuestions },
                )
            }
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
            // If an active conversation is already loaded (more than the welcome
            // message), don't overwrite it. This prevents LaunchedEffect(Unit)
            // re-firing on recomposition after a Settings navigation round-trip
            // from wiping the in-progress chat.
            if (_uiState.value.messages.size > 1) return@launch
            val threads = runCatching { repository.loadHistory() }.getOrElse { emptyList() }
            _uiState.update { it.copy(threads = threads) }
            val latest = threads.maxByOrNull { it.updatedAtMs }
            if (latest != null) {
                val messages = runCatching { repository.loadThread(latest.id) }.getOrElse { emptyList() }
                if (messages.isNotEmpty()) {
                    val older = messages.size >= HISTORY_PAGE_SIZE
                    // #17: rehydrate follow-up chips from the last assistant message in the
                    // auto-loaded thread (mirrors openThread logic). The sync path in
                    // ChatRepositoryImpl replaces DB rows on CONFLICT so followUps may be null
                    // after a sync — the openThread call preserves them only for explicit taps;
                    // loadDefaultState must do the same for the auto-resume case.
                    val followUps = messages.lastOrNull { it.role == ChatMessage.Role.ASSISTANT && it.followUps.isNotEmpty() }
                        ?.followUps.orEmpty()
                    _uiState.update {
                        it.copy(
                            activeThreadId = latest.id,
                            // iOS parity: opening the latest thread on launch must also set
                            // sessionId so a follow-up continues it (D1), not a stale UUID.
                            sessionId = latest.id,
                            messages = messages,
                            hasOlderMessages = older,
                            suggestedQuestions = followUps,
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
            // iOS parity (ChatViewModel.loadThread:351-353): rehydrate follow-up pills
            // from the LAST ASSISTANT MESSAGE's persisted followUps — reading from the
            // loaded messages themselves (same source iOS uses), not a separate DB query.
            // Without this, reopening a thread shows no guided next-question pills.
            val followUps = messages.lastOrNull { it.role == ChatMessage.Role.ASSISTANT && it.followUps.isNotEmpty() }
                ?.followUps.orEmpty()
            _uiState.update {
                it.copy(
                    activeThreadId = threadId,
                    // iOS parity (ChatViewModel.loadThread sets currentThreadId=thread.id):
                    // the send path keys off sessionId, so it MUST become the opened thread
                    // — otherwise a follow-up persists under the stale init UUID and spawns
                    // an orphan thread instead of continuing this one (D1).
                    sessionId = threadId,
                    messages = messages,
                    hasOlderMessages = older,
                    suggestedQuestions = followUps,
                )
            }
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
            // iOS parity: persist the rating locally so filled stars survive reopen.
            runCatching { repository.persistRating(messageId, rating) }
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

    // iOS parity (QuotaExhaustedView reset copy): format an ISO reset timestamp to a
    // short local time (e.g. "12:00 AM"); returns empty on parse failure so the sheet
    // falls back to its generic "resets soon" body.
    private fun formatResetTime(iso: String?): String {
        if (iso.isNullOrBlank()) return ""
        val patterns = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd'T'HH:mm:ss",
        )
        val candidate = if (iso.endsWith("Z") || iso.contains("+")) iso else "${iso}Z"
        for (p in patterns) {
            val parsed = runCatching {
                val fmt = java.text.SimpleDateFormat(p, java.util.Locale.US).apply {
                    timeZone = java.util.TimeZone.getTimeZone("UTC")
                }
                fmt.parse(candidate)
            }.getOrNull()
            if (parsed != null) {
                val out = java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault())
                return out.format(parsed)
            }
        }
        return ""
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
        // DES-161 B3: closing the sign-up sheet must NOT leave the send button
        // permanently disabled. canAskQuestion is set false right before the paywall
        // is shown; restore it on dismiss so the input re-enables (the real quota
        // gate re-fires on the next send attempt).
        _uiState.update { it.copy(showPaywall = false, canAskQuestion = true) }
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
        // iOS parity (ChatViewModel.swift:385-393): if the deleted thread is the one
        // currently open, reset to a fresh chat — otherwise its messages stay on screen
        // and the next send re-persists into the just-deleted (ghost) id (F1).
        if (threadId == _uiState.value.activeThreadId) {
            startNewChat()
        }
        viewModelScope.launch {
            runCatching { repository.deleteThread(threadId) }
        }
    }

    // Mirrors iOS observeAppLifecycle() background handling: if a stream is in
    // flight the foreground service keeps the process alive so we do NOT cancel.
    // Only clean up state if there is no active stream.
    private fun handleBackgroundExpiry() {
        stopCosmicProgressTimer()
        pumpJob?.cancel()
        pumpJob = null
        val job = streamJob
        if (job != null && job.isActive) {
            // Foreground service already started in sendMessage() — stream continues.
            return
        }
    }

    // Mirrors iOS stopGeneration/tearDownGenerationState (ChatViewModel.swift:1360-1364):
    // user tapped the Stop button. Cancel the in-flight stream, stop the cosmic timer,
    // scrub the empty orphan streaming bubble, finalize any partial one, reset state.
    // Unlike background expiry, do NOT set interruptedQuestion — the user chose to stop.
    fun stopGeneration() {
        stopCosmicProgressTimer()
        pumpJob?.cancel()
        pumpJob = null
        streamJob?.cancel()
        streamJob = null
        _uiState.update { s ->
            val kept = s.messages
                .filterNot { it.isStreaming && it.content.isBlank() }
                .map { if (it.isStreaming) it.copy(isStreaming = false) else it }
            s.copy(isLoading = false, isStreaming = false, messages = kept)
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
        _uiState.update { it.copy(cosmicProgressIndex = null, cosmicProgressStep = null) }
    }

    /**
     * FIX D: iOS parity (ChatViewModel.swift:1332-1344) — maps a backend `display_key`
     * from a progress_step SSE event to one of the existing localized cosmic-progress step
     * IDs. The ViewModel stores the resolved string ID name; ChatScreen resolves it via the
     * cosmicProgressStep state field (which takes precedence over the canned rotation index).
     * Returns null for unknown keys so the canned rotation continues unchanged.
     */
    private fun mapProgressDisplayKey(key: String?): String? {
        if (key.isNullOrBlank()) return null
        return when (key.lowercase().trim()) {
            "reading_stars", "chart_reading", "houses" -> appContext.getString(R.string.cosmic_progress_1)
            "aligning_planets", "planets", "planetary_positions" -> appContext.getString(R.string.cosmic_progress_2)
            "dasha", "dasha_period", "dasha_analysis" -> appContext.getString(R.string.cosmic_progress_3)
            "divisional_charts", "divisional", "varga" -> appContext.getString(R.string.cosmic_progress_4)
            "strength", "planet_strength", "shadbala" -> appContext.getString(R.string.cosmic_progress_5)
            "transits", "transit_analysis" -> appContext.getString(R.string.cosmic_progress_6)
            "nakshatra", "nakshatra_analysis" -> appContext.getString(R.string.cosmic_progress_7)
            "yogas", "doshas", "yoga_dosha" -> appContext.getString(R.string.cosmic_progress_8)
            "synthesis", "synthesizing" -> appContext.getString(R.string.cosmic_progress_9)
            "finalizing", "final", "complete" -> appContext.getString(R.string.cosmic_progress_10)
            else -> null
        }
    }

    // ── Smooth typewriter pump (iOS ChatViewModel.startSmoothPump parity) ──────────

    /** iOS parity: reduce-motion → reveal instantly (no typewriter animation). */
    private fun reduceMotionEnabled(): Boolean = runCatching {
        android.provider.Settings.Global.getFloat(
            appContext.contentResolver,
            android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) == 0f
    }.getOrDefault(false)

    /** Feed newly-arrived text to the pump. Reveals instantly under reduce-motion. */
    private fun feedPump(assistantId: String, fullTextSoFar: String) {
        pumpTarget = stripFollowUpBlock(fullTextSoFar)
        if (reduceMotionEnabled()) {
            pumpRevealed = pumpTarget.length
            renderPumpFrame(assistantId, pumpTarget)
            return
        }
        if (pumpJob?.isActive != true) startSmoothPump(assistantId)
    }

    /**
     * iOS parity (CompatibilityResultSheets.displayContent / ChatViewModel finalAnswer
     * reconciliation): the streamed answer text embeds a trailing
     * "FOLLOW_UP_QUESTIONS:" block that the backend also returns structurally via
     * follow_up_suggestions. Those structured suggestions render as tappable chips, so
     * the raw block must be stripped from the displayed/persisted bubble text — otherwise
     * the user sees the follow-ups twice (once as plain text, once as chips).
     */
    private fun stripFollowUpBlock(text: String): String {
        val marker = "\nFOLLOW_UP_QUESTIONS:"
        val idx = text.indexOf(marker, ignoreCase = true)
        return if (idx >= 0) text.substring(0, idx).trimEnd() else text
    }

    /** 60Hz interpolator revealing pumpTarget at ~70 ch/s ±20% jitter, with catch-up
     *  scaling when the backend is far ahead (mirrors iOS BASE_CHARS_PER_SEC + jitter).
     *  FIX C: publishes to UI state only every 3rd frame (~20Hz, mirrors iOS RENDER_EVERY=3)
     *  to bound markdown re-parse cost, while the reveal index advances every frame. */
    private fun startSmoothPump(assistantId: String) {
        pumpJob?.cancel()
        pumpJob = viewModelScope.launch {
            val baseCharsPerSec = 70.0
            val frameMs = 16L // ~60Hz
            var carry = 0.0
            var renderSkip = 0 // FIX C: publish every 3rd frame
            while (true) {
                val remaining = pumpTarget.length - pumpRevealed
                if (remaining <= 0) {
                    kotlinx.coroutines.delay(frameMs)
                    if (pumpTarget.length - pumpRevealed <= 0) continue else continue
                }
                // Catch-up: reveal faster when a large backlog has arrived (bursty frames).
                val catchUp = when {
                    remaining > 400 -> 3.0
                    remaining > 150 -> 2.0
                    else -> 1.0
                }
                val jitter = 0.8 + 0.4 * pseudoRandom() // ±20%
                carry += baseCharsPerSec * catchUp * jitter * (frameMs / 1000.0)
                val step = carry.toInt()
                if (step >= 1) {
                    carry -= step
                    pumpRevealed = (pumpRevealed + step).coerceAtMost(pumpTarget.length)
                    // FIX C: only push visible content to UI every 3rd frame (~20Hz)
                    // to bound markdown re-parse; reveal index still advances every frame.
                    renderSkip++
                    if (renderSkip >= 3) {
                        renderSkip = 0
                        renderPumpFrame(assistantId, pumpTarget.substring(0, pumpRevealed))
                    }
                }
                kotlinx.coroutines.delay(frameMs)
            }
        }
    }

    /** Deterministic-ish jitter without Math.random (avoids test flakiness). */
    private var pumpTick = 0
    private fun pseudoRandom(): Double {
        pumpTick = (pumpTick * 1103515245 + 12345) and 0x7fffffff
        return (pumpTick % 1000) / 1000.0
    }

    /** Fast-drain the remaining buffer instantly (stream closed / stop / error). */
    private fun drainPump(assistantId: String) {
        pumpJob?.cancel()
        pumpJob = null
        if (pumpTarget.isNotEmpty()) {
            pumpRevealed = pumpTarget.length
            renderPumpFrame(assistantId, pumpTarget)
        }
    }

    private fun resetPump() {
        pumpJob?.cancel()
        pumpJob = null
        pumpTarget = ""
        pumpRevealed = 0
    }

    private fun renderPumpFrame(assistantId: String, visible: String) {
        _uiState.update { s ->
            val existing = s.messages.firstOrNull { it.id == assistantId }
            val msg = (existing ?: ChatMessage(
                id = assistantId,
                role = ChatMessage.Role.ASSISTANT,
                content = "",
                isStreaming = true,
                createdAtMs = System.currentTimeMillis(),
            )).copy(content = visible, isStreaming = true)
            s.copy(messages = s.messages.filterNot { it.id == assistantId } + msg)
        }
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

    /**
     * Cat 10: map exceptions to a user-readable sentence so raw Kotlin exception
     * `.message` strings (server stack traces, HTTP body fragments, or a stringified
     * "null") never reach the chat UI. Mirrors HomeViewModel.friendlyError.
     */
    private fun friendlyError(e: Throwable): String {
        val msg = e.message?.lowercase().orEmpty()
        return when {
            e is java.net.SocketTimeoutException -> "Request timed out. Please try again."
            e is java.io.IOException -> "Network unavailable. Check your connection."
            msg.contains("401") || msg.contains("session") || msg.contains("unauthor") ->
                "Session expired. Please sign in again."
            msg.contains("timeout") || msg.contains("timed out") -> "Request timed out. Please try again."
            msg.contains("cancel") -> "Request was interrupted. Please try again."
            else -> "Unable to reach the prediction service. Please try again."
        }
    }
}
