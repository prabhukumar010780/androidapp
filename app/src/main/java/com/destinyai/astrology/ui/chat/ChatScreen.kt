package com.destinyai.astrology.ui.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.destinyai.astrology.domain.model.ChatMessage
import com.destinyai.astrology.R
import com.destinyai.astrology.ui.charts.ChartsViewModel
import com.destinyai.astrology.ui.charts.PlanetaryPositionsSheet
import com.destinyai.astrology.ui.theme.*
import com.destinyai.astrology.ui.subscription.SubscriptionScreen
import kotlinx.coroutines.delay

// Default starter question string-resource IDs (English fallbacks live in res/values/strings.xml).
// Consumed inside @Composable scope so stringResource(...) can resolve them.
private val defaultStarterQuestionResIds = listOf(
    R.string.chat_starter_today,
    R.string.chat_starter_relationships,
    R.string.chat_starter_decisions,
    R.string.chat_starter_dasha,
)

@Composable
fun ChatScreen(
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    initialQuestion: String? = null,
    initialDisplayLabel: String? = null,
    initialThreadId: String? = null,
    starterQuestions: List<String> = emptyList(),
    onNavigateToCharts: (() -> Unit)? = null,
    onNavigateToAuth: (() -> Unit)? = null,
    onNavigateToSettings: (() -> Unit)? = null,
    viewModel: ChatViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    var showHistory by remember { mutableStateOf(false) }
    var showResponseLengthSheet by remember { mutableStateOf(false) }
    // Mirrors iOS ChatView.swift:23, 90-92 — chart icon presents a modal sheet,
    // not a full-screen push. Keeps parity with iOS PlanetaryPositionsSheet UX.
    var showChartSheet by remember { mutableStateOf(false) }
    val currentResponseLength by viewModel.responseLength.collectAsStateWithLifecycle(initialValue = "standard")

    // Mirrors iOS ChatView.swift:194-210 — fade-out 200ms, swap, fade-in 250ms.
    // Wraps the messages container so startNewChatWithTransition feels identical
    // to the SwiftUI .easeOut/.easeIn opacity animation.
    var chatTransitionAlpha by remember { mutableFloatStateOf(1.0f) }
    val chatAlphaAnim by animateFloatAsState(
        targetValue = chatTransitionAlpha,
        animationSpec = tween(
            durationMillis = if (chatTransitionAlpha == 0.0f) 200 else 250,
            easing = if (chatTransitionAlpha == 0.0f) FastOutSlowInEasing else LinearOutSlowInEasing,
        ),
        label = "chat_transition_alpha",
    )

    // iOS parity: HapticManager.shared.play(.medium) on new-chat tap, .light on starter/follow-up.
    val haptic = LocalHapticFeedback.current

    // Drives the fade-out → reset → fade-in sequence. Set to true from the new-chat tap;
    // the LaunchedEffect below waits for the fade-out then calls startNewChat + fades in.
    var pendingNewChatReset by remember { mutableStateOf(false) }
    LaunchedEffect(pendingNewChatReset) {
        if (pendingNewChatReset) {
            delay(220) // matches iOS easeOut(0.2) + tiny buffer
            viewModel.startNewChat()
            chatTransitionAlpha = 1f
            pendingNewChatReset = false
        }
    }

    // Mirrors iOS ChatView.swift:407-409 — focus-triggered auto-scroll. The flag is set
    // by ChatInputBar.onInputFocusChanged and consumed here after a 300ms delay so the
    // IME animation completes before the scroll fires.
    var inputJustFocused by remember { mutableStateOf(false) }
    LaunchedEffect(inputJustFocused) {
        if (inputJustFocused) {
            delay(300)
            val last = listState.layoutInfo.totalItemsCount.coerceAtLeast(1) - 1
            listState.animateScrollToItem(last)
            inputJustFocused = false
        }
    }

    // Mirrors iOS ChatView.swift:172-176 .onDisappear { isInputFocused = false; resignFirstResponder() }
    // — when the screen leaves the hierarchy force-hide IME so the keyboard never bleeds into
    // the next screen on OEM Android variants.
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    DisposableEffect(Unit) {
        onDispose {
            keyboardController?.hide()
            focusManager.clearFocus(force = true)
        }
    }

    // Mirrors iOS ChatView.swift:381 .scrollDismissesKeyboard(.interactively) — drag-down
    // gesture in the message list interactively dismisses the IME so messages stay visible
    // while the user scrolls.
    val keyboardDismissNestedScroll = remember(keyboardController, focusManager) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y < -2f) {
                    keyboardController?.hide()
                    focusManager.clearFocus()
                }
                return Offset.Zero
            }
        }
    }

    // Scroll to bottom when new messages arrive or streaming changes
    LaunchedEffect(state.messages.size, state.isStreaming) {
        if (state.messages.isNotEmpty()) {
            delay(80)
            listState.animateScrollToItem(listState.layoutInfo.totalItemsCount.coerceAtLeast(1) - 1)
        }
    }
    LaunchedEffect(state.suggestedQuestions.size) {
        if (state.suggestedQuestions.isNotEmpty()) {
            delay(150)
            listState.animateScrollToItem(listState.layoutInfo.totalItemsCount.coerceAtLeast(1) - 1)
        }
    }

    // Mirrors iOS ChatView (initialThreadId path: ChatView.swift:14-15, 126-138, 149-153) —
    // when the screen is opened from a deep link / history-tap with a thread id, load that
    // thread instead of the default new-chat experience.
    LaunchedEffect(initialThreadId) {
        if (!initialThreadId.isNullOrBlank()) {
            viewModel.openThread(initialThreadId)
        }
    }

    // Handle initial question on first composition
    LaunchedEffect(initialQuestion) {
        if (!initialQuestion.isNullOrBlank()) {
            viewModel.startNewChat()
            // Mirrors iOS ChatView.swift:118,146 — short label for user bubble when
            // expanded contextual queries arrive from Home.
            viewModel.pendingDisplayLabel = initialDisplayLabel
            viewModel.updateInput(initialQuestion)
            viewModel.sendMessage()
        }
    }

    // Mirrors iOS ChatView.swift:154-157 — on plain open (no question, no thread id),
    // resume the most recent thread for the active profile so the user lands back where
    // they left off. No-op when either deep-link prop is supplied.
    LaunchedEffect(Unit) {
        if (initialQuestion.isNullOrBlank() && initialThreadId.isNullOrBlank()) {
            viewModel.loadDefaultState()
        }
    }

    CosmicBackground(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .semantics { contentDescription = "chat_screen" },
        ) {
            ChatHeader(
                onBack = onBack,
                onHistoryTap = { showHistory = true },
                // Mirrors iOS startNewChatWithTransition (ChatView.swift:194-210):
                // medium haptic, fade-out 200ms, swap thread, fade-in 250ms.
                onNewChatTap = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    chatTransitionAlpha = 0f
                    // Defer the actual reset until the fade-out animation has progressed.
                    // 250ms matches iOS DispatchQueue.main.asyncAfter(deadline: .now() + 0.25).
                    // Use a coroutine here via LaunchedEffect-equivalent: schedule via state ping.
                    pendingNewChatReset = true
                },
                // Mirrors iOS ChatView.swift:23, 90-92 — chart icon presents a modal sheet,
                // not a full-screen Charts route. Replaces the previous push navigation.
                onChartTap = { showChartSheet = true },
                isUsingSelfProfile = state.isUsingSelfProfile,
                activeProfileName = state.activeProfileName,
            )

            // Message list
            val isNewChat = state.messages.count { !it.isStreaming } <= 1
            val defaultStarters = defaultStarterQuestionResIds.map { stringResource(it) }
            val activeStarters = starterQuestions.ifEmpty { defaultStarters }.take(4)

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp)
                    .nestedScroll(keyboardDismissNestedScroll)
                    .alpha(chatAlphaAnim)
                    .testTag("chat_messages_list"),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                contentPadding = PaddingValues(vertical = 20.dp),
            ) {
                if (isNewChat && !state.isLoading && !state.isStreaming) {
                    item(key = "starters") {
                        StarterQuestionsView(
                            questions = activeStarters,
                            onQuestionTap = { q ->
                                // iOS parity (ChatView.swift:262): light haptic on starter tap.
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                keyboardController?.hide()
                                focusManager.clearFocus()
                                viewModel.updateInput(q)
                                viewModel.sendMessage()
                            },
                        )
                    }
                } else {
                    // Mirrors iOS ChatView (344-353): when older messages exist for the
                    // active thread surface a "Load earlier messages" button at the top.
                    if (state.hasOlderMessages) {
                        item(key = "load_earlier") {
                            LoadEarlierMessagesButton(
                                isLoading = state.isLoadingOlder,
                                onClick = { viewModel.loadOlderMessages() },
                            )
                        }
                    }
                    items(state.messages, key = { it.id }) { message ->
                        MessageBubbleView(
                            message = message,
                            copiedMessageId = state.copiedMessageId,
                            onCopy = { viewModel.copyMessage(message.id) },
                            onRate = { stars -> viewModel.submitRating(message.id, stars) },
                        )
                    }

                    if (showThinkingPillInList(state.isStreaming, state.messages)) {
                        item(key = "streaming") {
                            ThinkingPill(cosmicStep = cosmicProgressLabel(state.cosmicProgressIndex))
                        }
                    }

                    // Mirrors iOS ChatView.swift:367-370 — follow-up suggestions slide in
                    // from below + fade in (.opacity.combined(with: .move(edge: .bottom))).
                    item(key = "suggestions") {
                        AnimatedVisibility(
                            visible = state.suggestedQuestions.isNotEmpty() && !state.isLoading && !state.isStreaming,
                            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                        ) {
                            FollowUpSuggestionsView(
                                questions = state.suggestedQuestions,
                                onTap = { q ->
                                    // iOS parity (ChatView.swift:302): light haptic on follow-up tap.
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    keyboardController?.hide()
                                    focusManager.clearFocus()
                                    viewModel.updateInput(q)
                                    viewModel.dismissSuggestedQuestions()
                                    viewModel.sendMessage()
                                },
                            )
                        }
                    }
                }
            }

            // Error banner
            AnimatedVisibility(
                visible = state.errorMessage != null,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
            ) {
                ErrorBanner(message = state.errorMessage ?: "")
            }

            // Interrupted question recovery banner
            AnimatedVisibility(
                visible = state.interruptedQuestion != null && !state.isStreaming && !state.isLoading,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
            ) {
                InterruptedBanner(
                    question = state.interruptedQuestion ?: "",
                    onRetry = { viewModel.retryInterruptedQuestion() },
                )
            }

            // Input bar
            ChatInputBar(
                text = state.inputText,
                onTextChange = viewModel::updateInput,
                onSend = viewModel::sendMessage,
                canSend = state.canSend,
                isLoading = state.isLoading || state.isStreaming,
                onStyleTap = { showResponseLengthSheet = true },
                // Mirrors iOS ChatView.swift:407-409 — when the input bar gains focus, scroll
                // to the latest message after a 300ms delay so the keyboard animation completes
                // before the scroll fires.
                onInputFocusChanged = { focused ->
                    if (focused) inputJustFocused = true
                },
            )
        }
    }

    if (showHistory) {
        ChatHistorySheet(
            viewModel = viewModel,
            onDismiss = { showHistory = false },
            onNavigateToSettings = onNavigateToSettings,
        )
    }

    // Mirrors iOS ChatView.swift:90-92 .sheet(isPresented: $showChart) { PlanetaryPositionsSheet() }.
    // Chart icon presents a modal sheet rather than pushing the Charts route — chart icon =
    // peek at planet positions, not full chart navigation.
    if (showChartSheet) {
        val chartsVm: ChartsViewModel = hiltViewModel()
        val chartsState by chartsVm.uiState.collectAsStateWithLifecycle()
        LaunchedEffect(Unit) { chartsVm.loadChartData() }
        PlanetaryPositionsSheet(
            state = chartsState,
            currentChartStyle = chartsState.chartStyle,
            onChartStyleChanged = { chartsVm.setChartStyle(it) },
            onRetry = { chartsVm.retry() },
            onDismiss = { showChartSheet = false },
        )
    }

    if (state.showPaywall || state.navigateToSubscription) {
        // Mirrors iOS QuotaExhaustedView (ChatView.swift:93-109, 180-191): guests see a sign-in
        // path that preserves their birth data.  Account users either reached this branch via
        // the upgrade button on the QuotaExhaustedAccountSheet (navigateToSubscription=true)
        // or via direct paywall trigger (showPaywall=true) — both routes open SubscriptionScreen.
        if (state.isGuestUser && state.showPaywall) {
            QuotaExhaustedGuestSheet(
                onSignIn = { viewModel.requestSignInFromQuota() },
                onDismiss = { viewModel.dismissPaywall() },
            )
        } else {
            SubscriptionScreen(
                onBack = {
                    viewModel.dismissPaywall()
                    viewModel.consumeNavigateToSubscription()
                },
            )
        }
    }
    // Mirrors iOS QuotaExhaustedView for non-guest users (ChatView.swift:93-112): an interstitial
    // "icon + message + Upgrade" sheet appears BEFORE SubscriptionScreen.  Tapping Upgrade closes
    // the interstitial and toggles navigateToSubscription so the SubscriptionScreen branch above
    // takes over on the next composition.
    if (state.showQuotaExhaustedAccountSheet) {
        QuotaExhaustedAccountSheet(
            customMessage = state.quotaDetails,
            onUpgrade = { viewModel.requestUpgradeFromQuotaSheet() },
            onDismiss = { viewModel.dismissQuotaExhaustedAccountSheet() },
        )
    }
    // Mirrors iOS onSignIn navigation (ChatView.swift:93-109) — consume the flag exactly once.
    LaunchedEffect(state.navigateToAuth) {
        if (state.navigateToAuth) {
            onNavigateToAuth?.invoke()
            viewModel.consumeNavigateToAuth()
        }
    }
    if (showResponseLengthSheet) {
        ResponseLengthSheet(
            current = currentResponseLength,
            onSelect = { v ->
                viewModel.setResponseLength(v)
                showResponseLengthSheet = false
            },
            onDismiss = { showResponseLengthSheet = false },
        )
    }
}

// ── Chat header ───────────────────────────────────────────────────────────────

@Composable
private fun ChatHeader(
    onBack: (() -> Unit)?,
    onHistoryTap: () -> Unit,
    onNewChatTap: () -> Unit,
    onChartTap: () -> Unit,
    isUsingSelfProfile: Boolean = true,
    activeProfileName: String = "",
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 4.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Profile-context capsule — parity with iOS AppHeader.swift:118-138.
        // Hidden when active profile is self.
        if (!isUsingSelfProfile && activeProfileName.isNotBlank()) {
            Row(
                modifier = Modifier
                    .padding(bottom = 4.dp)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(50))
                    .background(Gold.copy(alpha = 0.15f))
                    .border(
                        width = 1.dp,
                        color = Gold.copy(alpha = 0.3f),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(50),
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .semantics { contentDescription = "profile_context_indicator" },
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Default.Person,
                    contentDescription = null,
                    tint = Gold,
                    modifier = Modifier.size(12.dp),
                )
                Text(
                    text = stringResource(R.string.viewing_as_label, activeProfileName),
                    fontSize = 11.sp,
                    color = Gold,
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.semantics { contentDescription = "chat_back_button" },
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = CreamDim)
                }
            } else {
                IconButton(
                    onClick = onHistoryTap,
                    modifier = Modifier.semantics { contentDescription = "chat_history_button" },
                ) {
                    Icon(Icons.Default.History, contentDescription = null, tint = CreamDim)
                }
            }
            Text(
                text = stringResource(R.string.ask_destiny),
                modifier = Modifier.weight(1f),
                fontFamily = CanelaFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp,
                color = Gold,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            IconButton(
                onClick = onChartTap,
                modifier = Modifier.semantics { contentDescription = "chat_chart_button" },
            ) {
                Icon(Icons.Outlined.PieChart, contentDescription = null, tint = CreamDim)
            }
            IconButton(
                onClick = onNewChatTap,
                modifier = Modifier.semantics { contentDescription = "new_chat_button" },
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = Gold)
            }
        }
    }
}

// ── Starter questions (new chat empty state) ──────────────────────────────────

@Composable
private fun StarterQuestionsView(questions: List<String>, onQuestionTap: (String) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.height(24.dp))

        // Sparkle circle
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(Gold.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Gold, modifier = Modifier.size(32.dp))
        }

        Text(
            stringResource(R.string.ask_destiny),
            fontFamily = CanelaFontFamily,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            color = CreamText,
        )
        Text(
            stringResource(R.string.chat_personal_guide_subtitle),
            fontSize = 14.sp,
            color = CreamDim,
        )

        Column(
            modifier = Modifier.padding(top = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            questions.forEach { q ->
                Box(
                    modifier = Modifier
                        .wrapContentWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(Gold.copy(alpha = 0.1f))
                        .border(1.dp, Gold.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
                        .clickable { onQuestionTap(q) }
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                ) {
                    Text(q, fontSize = 13.sp, color = Gold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            }
        }
    }
}

// ── Message bubble ────────────────────────────────────────────────────────────

@Composable
fun MessageBubbleView(
    message: ChatMessage,
    copiedMessageId: String?,
    onCopy: () -> Unit,
    onRate: (Int) -> Unit = {},
) {
    val isUser = message.role == ChatMessage.Role.USER
    val context = LocalContext.current
    var showCopied by remember(copiedMessageId, message.id) {
        mutableStateOf(copiedMessageId == message.id)
    }

    if (isUser) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "user_message" },
            horizontalArrangement = Arrangement.End,
        ) {
            Spacer(Modifier.width(60.dp))
            Box(
                modifier = Modifier
                    .clip(
                        RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 4.dp)
                    )
                    .background(
                        Brush.linearGradient(listOf(Gold, Gold.copy(alpha = 0.85f)))
                    )
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Text(
                    message.content,
                    fontSize = 17.sp,
                    color = Color(0xFF0D0D1A),
                    lineHeight = 24.sp,
                )
            }
        }
    } else {
        // AI message — reading layout (parity with iOS MessageBubble + ReadingMessageView).
        val isWelcome = message.id == "welcome"
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = if (isWelcome) "ai_message" else assistantContentDescription(message) },
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (message.isStreaming && message.content.isEmpty()) {
                ThinkingPill()
            } else if (message.content.isNotEmpty()) {
                // Markdown-aware text renderer (Gap 5) — replaces raw Text() so **bold**,
                // *italic*, `code`, and "- " list items render with proper styling.
                MarkdownText(
                    content = message.content,
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = "reading_body_text" },
                )

                // Tool-calls chips (Gap 3) — wand-and-stars icon + names.
                if (message.toolCalls.isNotEmpty()) {
                    ToolCallsChips(tools = message.toolCalls)
                }

                // Sources chips (Gap 3) — book icon + names.
                if (message.sources.isNotEmpty()) {
                    SourcesChips(sources = message.sources)
                }

                // DepthLayersView (Gap 4) — "Why this prediction" expandable row.
                if (!message.advice.isNullOrBlank()) {
                    DepthLayersView(whyContent = message.advice)
                }

                if (!message.isStreaming) {
                    Row(
                        modifier = Modifier.padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (message.content.length > 50) {
                            TextButton(
                                onClick = {
                                    val clip = ClipData.newPlainText("response", message.content)
                                    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                                        .setPrimaryClip(clip)
                                    onCopy()
                                    showCopied = true
                                },
                                modifier = Modifier.semantics { contentDescription = "copy_button" },
                                contentPadding = PaddingValues(0.dp),
                            ) {
                                Text(
                                    if (showCopied) stringResource(R.string.chat_copied) else stringResource(R.string.chat_copy_action),
                                    fontSize = 11.sp,
                                    color = if (showCopied) Gold else Color.White.copy(alpha = 0.3f),
                                )
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        // Execution time pill (Gap 6) — "• 1.4s"
                        if (message.executionTimeMs > 0.0) {
                            Text(
                                "• ${formatExecutionTime(message.executionTimeMs)}",
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.3f),
                            )
                        }
                        Text(
                            formatMessageTime(message.createdAtMs),
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.2f),
                        )
                        // Inline rating (Gap 2) — only on substantial assistant messages,
                        // skipped on the welcome bubble to mirror iOS isWelcomeMessage gate.
                        if (!isWelcome && message.content.length > 50) {
                            MessageRatingRow(
                                rating = message.rating,
                                onRate = onRate,
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Markdown rendering (lightweight inline parser — no external deps) ─────────

/**
 * Renders **bold**, *italic*, `code`, # heading, and "- " bullet markers using
 * AnnotatedString. Mirrors the subset of MarkdownTextView the iOS chat bubble
 * relies on without pulling a Compose-Markdown library.
 */
@Composable
fun MarkdownText(content: String, modifier: Modifier = Modifier) {
    val annotated = buildMarkdownAnnotated(content)
    Text(
        text = annotated,
        modifier = modifier,
        fontSize = 16.sp,
        color = Color.White.copy(alpha = 0.92f),
        lineHeight = 26.sp,
    )
}

internal fun buildMarkdownAnnotated(raw: String): androidx.compose.ui.text.AnnotatedString {
    val builder = androidx.compose.ui.text.AnnotatedString.Builder()
    val lines = raw.split("\n")
    lines.forEachIndexed { idx, originalLine ->
        var line = originalLine
        // Bullet lines
        if (line.trimStart().startsWith("- ") || line.trimStart().startsWith("* ")) {
            line = "• " + line.trimStart().removePrefix("- ").removePrefix("* ")
        }
        // Heading lines — render as bold larger weight
        val isHeading = line.trimStart().startsWith("#")
        if (isHeading) {
            line = line.trimStart().trimStart('#').trimStart()
        }
        appendInlineMarkdown(builder, line, headingBold = isHeading)
        if (idx != lines.lastIndex) builder.append("\n")
    }
    return builder.toAnnotatedString()
}

private fun appendInlineMarkdown(
    builder: androidx.compose.ui.text.AnnotatedString.Builder,
    text: String,
    headingBold: Boolean = false,
) {
    var i = 0
    while (i < text.length) {
        // **bold**
        if (i + 1 < text.length && text[i] == '*' && text[i + 1] == '*') {
            val end = text.indexOf("**", i + 2)
            if (end > 0) {
                builder.pushStyle(
                    androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.Bold),
                )
                builder.append(text.substring(i + 2, end))
                builder.pop()
                i = end + 2
                continue
            }
        }
        // *italic* (single asterisk)
        if (text[i] == '*') {
            val end = text.indexOf('*', i + 1)
            if (end > 0) {
                builder.pushStyle(
                    androidx.compose.ui.text.SpanStyle(fontStyle = FontStyle.Italic),
                )
                builder.append(text.substring(i + 1, end))
                builder.pop()
                i = end + 1
                continue
            }
        }
        // `code`
        if (text[i] == '`') {
            val end = text.indexOf('`', i + 1)
            if (end > 0) {
                builder.pushStyle(
                    androidx.compose.ui.text.SpanStyle(
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        background = Color.White.copy(alpha = 0.08f),
                    ),
                )
                builder.append(text.substring(i + 1, end))
                builder.pop()
                i = end + 1
                continue
            }
        }
        if (headingBold) {
            builder.pushStyle(androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.Bold))
            builder.append(text[i])
            builder.pop()
        } else {
            builder.append(text[i])
        }
        i++
    }
}

// ── Tool-calls / sources chips ────────────────────────────────────────────────

@Composable
private fun ToolCallsChips(tools: List<String>) {
    Row(
        modifier = Modifier
            .padding(top = 4.dp)
            .semantics { contentDescription = "tool_calls_chips" },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Default.AutoAwesome,
            contentDescription = null,
            tint = Gold,
            modifier = Modifier.size(10.dp),
        )
        tools.forEach { tool ->
            Text(
                tool,
                fontSize = 11.sp,
                color = CreamDim,
            )
        }
    }
}

@Composable
private fun SourcesChips(sources: List<String>) {
    Row(
        modifier = Modifier
            .padding(top = 4.dp)
            .semantics { contentDescription = "sources_chips" },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Default.MenuBook,
            contentDescription = null,
            tint = Gold,
            modifier = Modifier.size(10.dp),
        )
        sources.forEach { source ->
            Text(
                source,
                fontSize = 11.sp,
                color = CreamDim,
            )
        }
    }
}

// ── DepthLayersView (Why this prediction) ─────────────────────────────────────

@Composable
private fun DepthLayersView(whyContent: String) {
    var expanded by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "depth_layers_view" },
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Color.White.copy(alpha = 0.06f)),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .semantics { contentDescription = "depth_why_row" }
                .padding(vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.depth_why_this_prediction),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = if (expanded) Gold.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.45f),
                modifier = Modifier.weight(1f),
            )
            Icon(
                if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.25f),
                modifier = Modifier.size(14.dp),
            )
        }
        if (expanded) {
            Text(
                text = whyContent,
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.65f),
                lineHeight = 20.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
                    .semantics { contentDescription = "depth_expanded_content" },
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Color.White.copy(alpha = 0.05f)),
        )
    }
}

// ── Inline message rating (5-star tap + thank-you state) ──────────────────────

@Composable
private fun MessageRatingRow(rating: Int, onRate: (Int) -> Unit) {
    Row(
        modifier = Modifier.semantics { contentDescription = "message_rating_row" },
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (rating > 0) {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = null,
                tint = Color(0xFF48BB78),
                modifier = Modifier.size(11.dp),
            )
            Spacer(Modifier.width(2.dp))
            (1..5).forEach { star ->
                Icon(
                    if (star <= rating) Icons.Default.Star else Icons.Default.StarBorder,
                    contentDescription = null,
                    tint = if (star <= rating) Gold else Color.White.copy(alpha = 0.3f),
                    modifier = Modifier.size(11.dp),
                )
            }
        } else {
            Text(
                text = stringResource(R.string.rate_action),
                fontSize = 10.sp,
                color = CreamDim,
            )
            Spacer(Modifier.width(2.dp))
            (1..5).forEach { star ->
                IconButton(
                    onClick = { onRate(star) },
                    modifier = Modifier
                        .size(18.dp)
                        .semantics { contentDescription = "rate_star_$star" },
                ) {
                    Icon(
                        Icons.Default.StarBorder,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.size(13.dp),
                    )
                }
            }
        }
    }
}

// ── "Load earlier messages" (parity with iOS WindowManager) ───────────────────

@Composable
private fun LoadEarlierMessagesButton(isLoading: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .semantics { contentDescription = "load_earlier_messages_button" },
        contentAlignment = Alignment.Center,
    ) {
        TextButton(onClick = onClick, enabled = !isLoading) {
            if (isLoading) {
                CircularProgressIndicator(
                    color = Gold,
                    strokeWidth = 1.5.dp,
                    modifier = Modifier.size(14.dp),
                )
            } else {
                Text(
                    stringResource(R.string.load_earlier_messages),
                    fontSize = 13.sp,
                    color = Gold,
                )
            }
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

internal fun formatExecutionTime(ms: Double): String {
    val seconds = ms / 1000.0
    return when {
        seconds < 1.0 -> "${ms.toInt()}ms"
        seconds < 60.0 -> "%.1fs".format(seconds)
        else -> {
            val mins = (seconds / 60).toInt()
            val secs = (seconds.toInt() % 60)
            "${mins}m ${secs}s"
        }
    }
}

// ── Thinking pill (streaming loading state) ───────────────────────────────────

@Composable
fun ThinkingPill(cosmicStep: String? = null) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(NavySurface)
            .border(1.dp, Gold.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .semantics { contentDescription = "streaming_indicator" },
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AnimatedDots()
        Text(cosmicStep ?: stringResource(R.string.thinking), fontSize = 14.sp, color = CreamDim)
    }
}

/** Resolves the current cosmic-progress index to its localized string. */
@Composable
private fun cosmicProgressLabel(index: Int?): String? {
    if (index == null) return null
    val resId = when (index % 10) {
        0 -> R.string.cosmic_progress_1
        1 -> R.string.cosmic_progress_2
        2 -> R.string.cosmic_progress_3
        3 -> R.string.cosmic_progress_4
        4 -> R.string.cosmic_progress_5
        5 -> R.string.cosmic_progress_6
        6 -> R.string.cosmic_progress_7
        7 -> R.string.cosmic_progress_8
        8 -> R.string.cosmic_progress_9
        else -> R.string.cosmic_progress_10
    }
    return stringResource(resId)
}

// ── Animated 3-dot bounce indicator ──────────────────────────────────────────

@Composable
fun AnimatedDots() {
    val infiniteTransition = rememberInfiniteTransition(label = "dots")
    val offsets = (0..2).map { i ->
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = -4f,
            animationSpec = infiniteRepeatable(
                animation = tween(400, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
                initialStartOffset = StartOffset(i * 150),
            ),
            label = "dot$i",
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        offsets.forEach { offset ->
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .offset(y = offset.value.dp)
                    .clip(CircleShape)
                    .background(Gold),
            )
        }
    }
}

// ── Follow-up suggested questions ────────────────────────────────────────────

@Composable
private fun FollowUpSuggestionsView(questions: List<String>, onTap: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        questions.forEach { q ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(NavySurface)
                    .border(0.5.dp, Gold.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                    .clickable { onTap(q) }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(q, fontSize = 14.sp, color = CreamDim, modifier = Modifier.weight(1f))
                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Gold.copy(alpha = 0.5f), modifier = Modifier.size(16.dp))
            }
        }
    }
}

// ── Error banner ──────────────────────────────────────────────────────────────

@Composable
private fun ErrorBanner(message: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFFB71C1C).copy(alpha = 0.85f))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Warning, contentDescription = null, tint = CreamText, modifier = Modifier.size(16.dp))
        Text(message, fontSize = 14.sp, color = CreamText)
    }
}

// ── Interrupted banner (background expiry) ───────────────────────────────────

@Composable
private fun InterruptedBanner(question: String, onRetry: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Gold.copy(alpha = 0.12f))
            .border(1.dp, Gold.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.PauseCircle, contentDescription = null, tint = Gold, modifier = Modifier.size(18.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.chat_was_interrupted), fontSize = 12.sp, color = CreamDim)
            Text(question, fontSize = 13.sp, color = CreamText, maxLines = 1)
        }
        Button(
            onClick = onRetry,
            colors = ButtonDefaults.buttonColors(containerColor = Gold),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
        ) {
            Text(stringResource(R.string.retry), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0D0D1A))
        }
    }
}

// ── Input bar ─────────────────────────────────────────────────────────────────

@Composable
private fun ChatInputBar(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    canSend: Boolean,
    isLoading: Boolean,
    onStyleTap: () -> Unit = {},
    onInputFocusChanged: (Boolean) -> Unit = {},
) {
    // Mirrors iOS ChatView (75-77, 263-264, 303-304): every send/quota/starter path
    // explicitly resigns first responder. Hide the IME and clear focus on send.
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val dismissAndSend: () -> Unit = {
        keyboardController?.hide()
        focusManager.clearFocus()
        onSend()
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF0B0F19))
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .padding(bottom = 4.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        // Full pill wrapping slider + text + send
        Row(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(24.dp))
                .background(NavyInput)
                .border(
                    width = if (false) 1.5.dp else 1.dp,
                    color = Gold.copy(alpha = 0.25f),
                    shape = RoundedCornerShape(24.dp),
                ),
            verticalAlignment = Alignment.Bottom,
        ) {
            // Style/slider button (left inside pill, hidden when loading)
            if (!isLoading) {
                IconButton(
                    onClick = onStyleTap,
                    modifier = Modifier
                        .size(40.dp)
                        .semantics { contentDescription = "style_selector_button" },
                ) {
                    Icon(Icons.Default.Tune, contentDescription = null, tint = Gold, modifier = Modifier.size(18.dp))
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 11.dp),
            ) {
                BasicTextField(
                    value = text,
                    onValueChange = onTextChange,
                    textStyle = TextStyle(color = CreamText, fontSize = 16.sp),
                    cursorBrush = SolidColor(Gold),
                    maxLines = 5,
                    modifier = Modifier
                        .fillMaxWidth()
                        // Mirrors iOS ChatView.swift:407-409 .onChange(of: isInputFocused) —
                        // expose focus state so ChatScreen can auto-scroll to the latest
                        // message when the user taps into the input bar.
                        .onFocusChanged { onInputFocusChanged(it.isFocused) }
                        .semantics { contentDescription = "chat_input" },
                    decorationBox = { inner ->
                        if (text.isEmpty()) {
                            Text(stringResource(R.string.chat_ask_anything_placeholder), color = Color(0xFF718096), fontSize = 16.sp)
                        }
                        inner()
                    },
                )
            }

            // Send / loading button (right inside pill)
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .semantics { contentDescription = "send_button" },
                contentAlignment = Alignment.Center,
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = Gold,
                        strokeWidth = 2.dp,
                    )
                } else {
                    IconButton(onClick = dismissAndSend, enabled = canSend) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = null,
                            tint = if (canSend) Gold else Gold.copy(alpha = 0.25f),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
    }
}

// ── Accessibility logic (pure — unit-testable without Compose) ────────────────

/**
 * Maps an assistant ChatMessage to its semantic contentDescription.
 * - Streaming with no content yet → "streaming_indicator" (ThinkingPill inside)
 * - Streaming with partial content  → "reading_entry" (matches iOS ReadingMessageView)
 * - Finished assistant message      → "ai_message" (welcome / completed prediction)
 */
internal fun assistantContentDescription(message: ChatMessage): String = when {
    message.isStreaming && message.content.isEmpty() -> "streaming_indicator"
    message.isStreaming -> "reading_entry"
    else -> "ai_message"
}

/**
 * True only when streaming has started but no assistant message has accumulated content yet.
 * Once the first chunk arrives the list-level ThinkingPill should disappear — the
 * MessageBubbleView shows the ThinkingPill inline instead until content populates.
 */
internal fun showThinkingPillInList(isStreaming: Boolean, messages: List<ChatMessage>): Boolean =
    isStreaming && messages.none { it.role == ChatMessage.Role.ASSISTANT && it.content.isNotEmpty() }

// ── Helpers ───────────────────────────────────────────────────────────────────

private fun formatMessageTime(ms: Long): String {
    if (ms == 0L) return ""
    val h = java.util.Calendar.getInstance().apply { timeInMillis = ms }.get(java.util.Calendar.HOUR_OF_DAY)
    val m = java.util.Calendar.getInstance().apply { timeInMillis = ms }.get(java.util.Calendar.MINUTE)
    val amPm = if (h < 12) "AM" else "PM"
    val h12 = if (h == 0 || h == 12) 12 else h % 12
    return "%d:%02d %s".format(h12, m, amPm)
}

// ── Response length sheet (mirrors iOS ResponseLengthSheet) ──────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ResponseLengthSheet(
    current: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = NavySurface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .semantics { contentDescription = "response_length_sheet" },
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(R.string.response_style_setting_title),
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = CanelaFontFamily,
                color = Gold,
            )
            ResponseLengthOption(
                title = stringResource(R.string.response_length_concise),
                desc = stringResource(R.string.response_length_concise_desc),
                value = "short",
                isSelected = current == "short",
                onSelect = onSelect,
            )
            ResponseLengthOption(
                title = stringResource(R.string.response_length_expanded),
                desc = stringResource(R.string.response_length_expanded_desc),
                value = "detailed",
                isSelected = current == "detailed" || current == "standard",
                onSelect = onSelect,
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ResponseLengthOption(
    title: String,
    desc: String,
    value: String,
    isSelected: Boolean,
    onSelect: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) Gold.copy(alpha = 0.12f) else Color.Transparent)
            .border(
                width = 1.dp,
                color = if (isSelected) Gold.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.08f),
                shape = RoundedCornerShape(12.dp),
            )
            .clickable { onSelect(value) }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, color = CreamText, fontWeight = FontWeight.Medium)
            Text(desc, fontSize = 12.sp, color = CreamDim)
        }
        if (isSelected) {
            Icon(Icons.Default.Check, contentDescription = null, tint = Gold, modifier = Modifier.size(18.dp))
        }
    }
}

// ── Quota exhausted guest sheet (mirrors iOS QuotaExhaustedView for guest path) ─

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuotaExhaustedGuestSheet(
    onSignIn: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = NavySurface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .semantics { contentDescription = "quota_exhausted_guest_sheet" },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Default.Lock, contentDescription = null, tint = Gold, modifier = Modifier.size(40.dp))
            Text(
                stringResource(R.string.guest_question_limit_reached),
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = CanelaFontFamily,
                color = Gold,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Text(
                stringResource(R.string.sign_in_to_continue_chat),
                fontSize = 14.sp,
                color = CreamDim,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Button(
                onClick = onSignIn,
                colors = ButtonDefaults.buttonColors(containerColor = Gold),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "sign_in_button" },
            ) {
                Text(
                    stringResource(R.string.sign_in_button),
                    color = Color(0xFF0D0D1A),
                    fontWeight = FontWeight.Bold,
                )
            }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.cancel), color = CreamDim)
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

// ── Quota exhausted account sheet (parity with iOS QuotaExhaustedView non-guest path) ─

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuotaExhaustedAccountSheet(
    customMessage: String,
    onUpgrade: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = NavySurface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .semantics { contentDescription = "quota_exhausted_account_sheet" },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                Icons.Default.Lock,
                contentDescription = null,
                tint = Gold,
                modifier = Modifier.size(40.dp),
            )
            Text(
                stringResource(R.string.quota_exhausted_title),
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = CanelaFontFamily,
                color = Gold,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Text(
                customMessage.ifBlank { stringResource(R.string.upgrade_to_keep_going) },
                fontSize = 14.sp,
                color = CreamDim,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Button(
                onClick = onUpgrade,
                colors = ButtonDefaults.buttonColors(containerColor = Gold),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "quota_exhausted_upgrade_button" },
            ) {
                Text(
                    stringResource(R.string.upgrade_action),
                    color = Color(0xFF0D0D1A),
                    fontWeight = FontWeight.Bold,
                )
            }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.cancel), color = CreamDim)
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
