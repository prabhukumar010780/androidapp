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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.destinyai.astrology.domain.model.ChatMessage
import com.destinyai.astrology.R
import com.destinyai.astrology.ui.charts.ChartsViewModel
import com.destinyai.astrology.ui.charts.PlanetaryPositionsSheet
import com.destinyai.astrology.ui.components.OfflineBanner
import com.destinyai.astrology.ui.theme.*
import com.destinyai.astrology.ui.subscription.SubscriptionScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Default starter question string-resource IDs (English fallbacks live in res/values/strings.xml).
// Consumed inside @Composable scope so stringResource(...) can resolve them.
// iOS parity: ChatView.swift fallbackQuestions — marriage / career / finance / health.
private val defaultStarterQuestionResIds = listOf(
    R.string.chat_starter_marriage,
    R.string.chat_starter_career_direction,
    R.string.chat_starter_finance,
    R.string.chat_starter_health_check,
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
    // Cat 10: connectivity for the offline banner (parity with Home).
    val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()
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

    // iOS parity (ChatView.swift:622-642 pin-to-top on Send): when the user sends a
    // message, scroll THAT message to the TOP so the answer streams below it — do NOT
    // auto-scroll to the bottom (the "answer dumped at bottom" behavior iOS deliberately
    // removed, ChatView.swift:651-703). animateScrollToItem(index) places the item's top at
    // the viewport top — the Compose analog of iOS scrollTo(id, anchor: .top). Keyed on the
    // last user-message id so it fires once per Send, not on every token/assistant append.
    val lastUserMsgId = remember(state.messages) {
        state.messages.lastOrNull { it.role == ChatMessage.Role.USER }?.id
    }
    LaunchedEffect(lastUserMsgId) {
        val id = lastUserMsgId ?: return@LaunchedEffect
        val mIndex = state.messages.indexOfFirst { it.id == id }
        if (mIndex < 0) return@LaunchedEffect
        val leadingCount = if (state.hasOlderMessages) 1 else 0
        delay(80) // let the new rows + tail spacer commit (mirrors iOS 0.05s defer)
        listState.animateScrollToItem(leadingCount + mIndex)
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
                .semantics(mergeDescendants = false) { contentDescription = "chat_screen" },
            // Cat 4 adaptability: center children so adaptiveContentWidth() caps the
            // message list + input bar to a readable column on tablets/foldables
            // instead of stretching full-bleed. No-op on phones.
            horizontalAlignment = Alignment.CenterHorizontally,
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
            // Prefer the home-screen starters loaded by the VM (getSuggestedQuestions(),
            // server-personalized — parity with the Home tab), then any caller-supplied
            // starterQuestions, then the static defaults.
            val activeStarters = state.starterQuestions
                .ifEmpty { starterQuestions }
                .ifEmpty { defaultStarters }
                .take(4)

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .adaptiveContentWidth()
                    .padding(horizontal = Spacing.screenH)
                    .nestedScroll(keyboardDismissNestedScroll)
                    .alpha(chatAlphaAnim)
                    .testTag("chat_messages_list"),
                verticalArrangement = Arrangement.spacedBy(Spacing.xl),
                contentPadding = PaddingValues(vertical = Spacing.xl),
            ) {
                if (isNewChat && !state.isLoading && !state.isStreaming) {
                    item(key = "starters") {
                        // Center the empty/new-chat state on the FULL list viewport
                        // instead of letting it jam under the header with a tall void
                        // below (premium empty-state pattern).
                        Box(
                            modifier = Modifier.fillParentMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
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
                            // FIX D: prefer backend-driven cosmicProgressStep when present;
                            // fall back to canned index rotation when not.
                            val pillLabel = state.cosmicProgressStep
                                ?: cosmicProgressLabel(state.cosmicProgressIndex)
                            // E2E: loading_indicator marker — present ONLY while the
                            // list-level cosmic-progress pill is shown (isStreaming, before
                            // the streaming bubble exists). Tests wait for this to disappear.
                            Box(modifier = Modifier.semantics { contentDescription = "loading_indicator" }) {
                                ThinkingPill(cosmicStep = pillLabel)
                            }
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

                    // iOS parity (ChatView.swift:502-546 reserved tail-space): an
                    // always-present spacer below the conversation. During generation it
                    // reserves ~70% of the viewport so pin-to-top-on-send has room to scroll
                    // the just-sent question to the top; it collapses to 0 at rest. Height is
                    // ANIMATED (not the item added/removed) so the stream→done transition is a
                    // non-disruptive layout pass, avoiding a re-anchor jump to the bottom.
                    item(key = "tailSpacer") {
                        val screenHeightDp = LocalConfiguration.current.screenHeightDp.dp
                        // ~45% reserve is enough headroom to pin the just-sent question to
                        // the top; 70% left a large dead void at rest on tall screens.
                        val target = if (state.isLoading || state.isStreaming) screenHeightDp * 0.45f else 0.dp
                        val h by animateDpAsState(
                            targetValue = target,
                            animationSpec = tween(350),
                            label = "tail_spacer",
                        )
                        Spacer(Modifier.height(h))
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

            // Cat 10: offline banner above the input bar so a user who loses
            // connectivity mid-conversation gets clear feedback (parity with Home).
            if (!isOnline) {
                OfflineBanner(modifier = Modifier.adaptiveContentWidth())
            }

            // Input bar — capped to the same content width as the message list so
            // the two align on tablets/foldables instead of the bar spanning full-bleed.
            Box(modifier = Modifier.adaptiveContentWidth()) {
                ChatInputBar(
                    text = state.inputText,
                    onTextChange = viewModel::updateInput,
                    onSend = viewModel::sendMessage,
                    canSend = state.canSend,
                    isLoading = state.isLoading || state.isStreaming,
                    onStop = viewModel::stopGeneration,
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
            reason = state.quotaReason,
            supportEmail = state.quotaSupportEmail,
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
            // 16dp edge margin to match the message list + rest of the app (was 4dp).
            .padding(horizontal = Spacing.screenH, vertical = Spacing.sm),
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
                    fontSize = AppType.caption,
                    lineHeight = AppType.captionLh,
                    color = Gold,
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // iOS parity (AppHeader.swift:140-188): show back chevron + history clock
            // BOTH together when entering chat from a tab (the chat tab hides the
            // bottom bar, so the back chevron is the only return affordance).
            if (onBack != null) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.semantics { contentDescription = "chat_back_button" },
                ) {
                    Icon(
                        Icons.Default.ChevronLeft,
                        contentDescription = null,
                        tint = Gold,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
            IconButton(
                onClick = onHistoryTap,
                modifier = Modifier.semantics { contentDescription = "chat_history_button" },
            ) {
                // iOS uses clock.arrow.circlepath — Material's Restore is the
                // closest visual equivalent (clock + counter-clockwise arrow).
                Icon(
                    Icons.Default.Restore,
                    contentDescription = null,
                    tint = Gold,
                    modifier = Modifier.size(22.dp),
                )
            }
            // Gap 104 — centered destiny_home logo replaces the "Ask Destiny" title text,
            // matching iOS ChatHeader brand-logo treatment (parity with HomeScreen header).
            androidx.compose.foundation.Image(
                painter = androidx.compose.ui.res.painterResource(id = R.drawable.destiny_home),
                contentDescription = stringResource(R.string.ask_destiny),
                contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                modifier = Modifier
                    .weight(1f)
                    .height(28.dp),
            )
            IconButton(
                onClick = onChartTap,
                modifier = Modifier.semantics { contentDescription = "chat_chart_button" },
            ) {
                // DES-161 D3b: use a chart glyph, not a globe. Icons.Default.Public
                // (a globe) was a poor stand-in for the birth-chart button; PieChart
                // reads clearly as "chart".
                Icon(
                    Icons.Outlined.PieChart,
                    contentDescription = null,
                    tint = Gold,
                    modifier = Modifier.size(22.dp),
                )
            }
            IconButton(
                onClick = onNewChatTap,
                modifier = Modifier.semantics { contentDescription = "new_chat_button" },
            ) {
                // iOS uses square.and.pencil — Material's Edit (compose pencil)
                // is the closest equivalent. Replaces the previous Add (+) glyph.
                Icon(
                    Icons.Default.Edit,
                    contentDescription = null,
                    tint = Gold,
                    modifier = Modifier.size(22.dp),
                )
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
            fontWeight = FontWeight.Bold,
            color = CreamText,
        )
        Text(
            stringResource(R.string.chat_welcome_subtitle),
            fontSize = 14.sp,
            color = CreamDim,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp),
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
                        .heightIn(min = TouchMin)
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center,
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
    val haptic = LocalHapticFeedback.current
    var showCopied by remember(copiedMessageId, message.id) {
        mutableStateOf(copiedMessageId == message.id)
    }
    // Issue 43 — local 1.5s auto-revert mirroring iOS behavior.
    LaunchedEffect(showCopied) {
        if (showCopied) {
            delay(1500)
            showCopied = false
        }
    }

    if (isUser) {
        // Issue 42 — spoken "You said: <message>" prefix matching iOS accessibilityLabel.
        val youSaid = stringResource(R.string.a11y_you_said, message.content)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = youSaid },
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
        // Issue 62 — spoken "Destiny said: <content>" prefix matching iOS accessibilityLabel.
        val destinySaid = stringResource(R.string.a11y_destiny_said, message.content)
        val aiContentDesc = if (isWelcome) "ai_message" else destinySaid
        // E2E: chat_message_assistant marker on the assistant bubble. Wraps the bubble
        // Column (which already carries the human a11y label aiContentDesc) so the E2E id
        // is added WITHOUT clobbering the spoken "Destiny said…" label. Multiple instances.
        Box(modifier = Modifier.semantics { contentDescription = "chat_message_assistant" }) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = aiContentDesc },
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (message.isStreaming && message.content.isEmpty()) {
                // E2E: loading_indicator marker — the inline thinking pill shown while a
                // streaming assistant bubble has no content yet. Present ONLY during
                // streaming; disappears once content arrives / streaming completes.
                Box(modifier = Modifier.semantics { contentDescription = "loading_indicator" }) {
                    ThinkingPill()
                }
            } else if (message.content.isNotEmpty()) {
                // Markdown-aware text renderer (Gap 5) — replaces raw Text() so **bold**,
                // *italic*, `code`, and "- " list items render with proper styling.
                // Issues 31/33 — wrap in SelectionContainer so long-press selection/copy works
                // on rendered markdown blocks, matching iOS UITextView selection.
                SelectionContainer {
                    MarkdownText(
                        content = message.content,
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics { contentDescription = "reading_body_text" },
                    )
                }

                // Tool-calls chips (Gap 3) — wand-and-stars icon + names.
                if (message.toolCalls.isNotEmpty()) {
                    ToolCallsChips(tools = message.toolCalls)
                }

                // Sources chips (Gap 3) — book icon + names.
                if (message.sources.isNotEmpty()) {
                    SourcesChips(sources = message.sources)
                }

                // DepthLayersView (Gap 4) — "Why this prediction" + optional "Timing window".
                if (!message.advice.isNullOrBlank() || !message.timing.isNullOrBlank()) {
                    DepthLayersView(
                        whyContent = message.advice,
                        timingContent = message.timing,
                    )
                }

                if (!message.isStreaming) {
                    // DES-161: single-row footer matching iOS MessageBubble.metadataRowWithRating —
                    // [time • exec] ←spacer→ [Copy] [Rating]. Compact, premium, no crowding.
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Leading: timestamp • exec time (iOS order).
                        Text(
                            formatMessageTime(message.createdAtMs),
                            fontSize = AppType.caption,
                            lineHeight = AppType.captionLh,
                            // A11y: 0.55 alpha clears WCAG AA (~5:1) at caption size;
                            // 0.3 was 2.65:1 and failed contrast.
                            color = Color.White.copy(alpha = 0.55f),
                        )
                        if (message.executionTimeMs > 0.0) {
                            Text(
                                "• ${formatExecutionTime(message.executionTimeMs)}",
                                fontSize = AppType.caption,
                                lineHeight = AppType.captionLh,
                                color = Color.White.copy(alpha = 0.55f),
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        // Trailing: compact icon-only Copy, then the rating stars.
                        if (!isWelcome && message.content.length > 50) {
                            // A11y: 48dp tap target (Material min) with a small 16dp visible
                            // glyph — keeps the compact look without the sub-target hit area.
                            // E2E: copy_message_button marker wraps the clickable copy Box
                            // (which keeps its existing copy_button id) so the new E2E id is
                            // added without clobbering the existing contentDescription.
                            Box(modifier = Modifier.semantics { contentDescription = "copy_message_button" }) {
                            Box(
                                modifier = Modifier
                                    .size(TouchMin)
                                    .clip(CircleShape)
                                    .clickable {
                                        // Issue 41/63 — haptic feedback on copy tap matches iOS .light impact.
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        val clip = ClipData.newPlainText("response", message.content)
                                        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                                            .setPrimaryClip(clip)
                                        onCopy()
                                        showCopied = true
                                    }
                                    .semantics { contentDescription = "copy_button" },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = if (showCopied) Icons.Default.Check else Icons.Default.ContentCopy,
                                    contentDescription = null,
                                    tint = if (showCopied) Gold else Color.White.copy(alpha = 0.55f),
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                            } // close copy_message_button wrapper Box
                            MessageRatingRow(
                                rating = message.rating,
                                onRate = onRate,
                            )
                        }
                    }
                }
            }
        }
        } // close chat_message_assistant wrapper Box
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
    // FIX F: iOS parity (MarkdownTextView.swift:38-40) — render as plain Text above 40KB
    // to avoid the markdown parser stalling the UI on pathological server responses.
    if (content.length > 40 * 1024) {
        Text(
            text = content,
            modifier = modifier,
            fontSize = 16.sp,
            color = Color.White.copy(alpha = 0.92f),
            lineHeight = 26.sp,
        )
        return
    }
    // Issue 34 — cache the parsed AnnotatedString so streaming chunks with identical text
    // skip the full re-parse cost.
    val annotated = remember(content) { buildMarkdownAnnotated(content) }
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
        // Bullet lines — iOS parity: indent the bullet in from the left margin and
        // give a wider gap after the dot so content isn't cramped against it
        // (iOS renders bullets as inset rows with .padding(.leading) + 10pt gap).
        if (line.trimStart().startsWith("- ") || line.trimStart().startsWith("* ")) {
            line = "    •  " + line.trimStart().removePrefix("- ").removePrefix("* ")
        }
        // Heading lines — render as bold larger weight
        var isHeading = line.trimStart().startsWith("#")
        if (isHeading) {
            line = line.trimStart().trimStart('#').trimStart()
        }
        // iOS parity (MarkdownTextView renderBoldLabel): a STANDALONE bold line
        // — the whole trimmed line is `**…**` with no trailing content and no colon —
        // is a section title (e.g. "**Key timing windows**", "**What makes this powerful**")
        // and renders GOLD like a heading, not plain bold-white. Also `**Label:** …`
        // gives a gold label. The backend emits section titles this way (not as `#`),
        // so without this they read as ordinary bold text (the "header not gold" gap).
        if (!isHeading) {
            val t = line.trim()
            val standaloneBold = t.length >= 5 && t.startsWith("**") && t.endsWith("**") &&
                t.indexOf("**", 2) == t.length - 2
            if (standaloneBold) {
                line = t.removeSurrounding("**").trim()
                isHeading = true
            }
        }
        // DES-161 B6b: blockquote closing statement (`> **_..._**`). The backend ends
        // each reading with a `>`-prefixed closing summary BEFORE the follow-up block.
        // The parser previously left the literal "> " prefix and gave it no emphasis,
        // so the summary read as broken/"missing". Strip the marker and render the
        // line in gold italic (parity with iOS blockquote closing statement).
        val isQuote = line.trimStart().startsWith(">")
        if (isQuote) {
            line = line.trimStart().removePrefix(">").trimStart()
        }
        appendInlineMarkdown(builder, line, headingBold = isHeading, quote = isQuote)
        if (idx != lines.lastIndex) builder.append("\n")
    }
    return builder.toAnnotatedString()
}

private fun appendInlineMarkdown(
    builder: androidx.compose.ui.text.AnnotatedString.Builder,
    rawText: String,
    headingBold: Boolean = false,
    quote: Boolean = false,
) {
    // Issue 32 — sanitize unclosed bold/italic markers and replace pipe chars with middle-dots
    // so the lightweight inline parser doesn't render dangling formatting symbols.
    val text = sanitizeForInlineParsing(rawText)
    // DES-161 B6b: a blockquote closing statement renders in gold italic across the
    // whole line, regardless of inner **/_ markers.
    if (quote) {
        builder.pushStyle(
            androidx.compose.ui.text.SpanStyle(
                color = Gold,
                fontStyle = FontStyle.Italic,
                fontWeight = FontWeight.Normal,
            ),
        )
        builder.append(text.replace("*", "").replace("_", "").trim())
        builder.pop()
        return
    }
    var i = 0
    while (i < text.length) {
        // **bold**
        if (i + 1 < text.length && text[i] == '*' && text[i + 1] == '*') {
            val end = text.indexOf("**", i + 2)
            if (end > 0) {
                // When inside a heading context, bold spans also render in Gold
                // (parity with iOS MarkdownTextView which colours all heading content gold).
                val boldStyle = if (headingBold)
                    androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.Bold, color = Gold)
                else
                    androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.Bold)
                builder.pushStyle(boldStyle)
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
            // DES-161 B6a: render headings in Gold (parity with iOS gold heading
            // text), not just bold white — the MarkdownText Text() applies white to
            // everything, so a bold-only span left headings indistinguishable.
            builder.pushStyle(androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.Bold, color = Gold))
            builder.append(text[i])
            builder.pop()
        } else {
            builder.append(text[i])
        }
        i++
    }
}

// ── Tool-calls / sources chips ────────────────────────────────────────────────

/**
 * Issue 32 — close dangling bold/italic markers and replace pipe characters with a
 * middle-dot so the inline parser cannot leak partial formatting tokens. Mirrors
 * iOS sanitizeForInlineParsing (Markdown rendering helper).
 */
internal fun sanitizeForInlineParsing(input: String): String {
    var result = input.replace('|', '·')
    val boldCount = Regex("\\*\\*").findAll(result).count()
    if (boldCount % 2 != 0) result += "**"
    // Count single asterisks that are NOT part of "**" — close any odd italic.
    val singleAsterisks = result.length - result.replace("*", "").length -
        (Regex("\\*\\*").findAll(result).count() * 2)
    if (singleAsterisks % 2 != 0) result += "*"
    return result
}

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
                fontSize = AppType.caption,
                lineHeight = AppType.captionLh,
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
                fontSize = AppType.caption,
                lineHeight = AppType.captionLh,
                color = CreamDim,
            )
        }
    }
}

// ── DepthLayersView (Why this prediction) ─────────────────────────────────────

@Composable
private fun DepthLayersView(whyContent: String?, timingContent: String? = null) {
    var whyExpanded by remember { mutableStateOf(false) }
    var timingExpanded by remember { mutableStateOf(false) }
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
        if (!whyContent.isNullOrBlank()) {
            DepthLayerRow(
                title = stringResource(R.string.depth_why_this_prediction),
                body = whyContent,
                expanded = whyExpanded,
                onToggle = { whyExpanded = !whyExpanded },
                rowTag = "depth_why_row",
                bodyTag = "depth_why_expanded_content",
            )
        }
        if (!timingContent.isNullOrBlank()) {
            // Issue 59 — Timing window row, mirrors iOS DepthLayersView second row.
            DepthLayerRow(
                title = stringResource(R.string.depth_timing_window),
                body = timingContent,
                expanded = timingExpanded,
                onToggle = { timingExpanded = !timingExpanded },
                rowTag = "depth_timing_row",
                bodyTag = "depth_timing_expanded_content",
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

@Composable
private fun DepthLayerRow(
    title: String,
    body: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    rowTag: String,
    bodyTag: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
            .semantics { contentDescription = rowTag }
            .heightIn(min = TouchMin)
            .padding(vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            fontSize = 12.sp,
            fontWeight = FontWeight.Normal,
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
    AnimatedVisibility(
        visible = expanded,
        enter = fadeIn(animationSpec = tween(200, easing = FastOutSlowInEasing)) +
            slideInVertically(
                initialOffsetY = { -it / 4 },
                animationSpec = tween(200, easing = FastOutSlowInEasing),
            ),
        exit = fadeOut(animationSpec = tween(200, easing = FastOutSlowInEasing)),
    ) {
        Text(
            text = body,
            fontSize = 14.sp,
            color = Color.White.copy(alpha = 0.65f),
            lineHeight = 20.sp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
                .semantics { contentDescription = bodyTag },
        )
    }
}

// ── Inline message rating (5-star tap + thank-you state) ──────────────────────

@Composable
private fun MessageRatingRow(rating: Int, onRate: (Int) -> Unit) {
    val haptic = LocalHapticFeedback.current
    // Issues 46/47/57 — local optimistic state so stars fill immediately on tap, before
    // the VM persists the rating. isSubmitting drives the spinner + disabled visuals.
    var selectedRating by remember(rating) { mutableStateOf(rating) }
    var isSubmitting by remember { mutableStateOf(false) }
    val hasSubmitted = rating > 0

    // Issue 55/58 — fire confirm haptic when persisted rating becomes non-zero.
    LaunchedEffect(rating) {
        if (rating > 0 && isSubmitting) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
        if (rating > 0) isSubmitting = false
    }

    // Issue 50/59 — animate between rated and not-rated states with a spring fade.
    AnimatedContent(
        targetState = hasSubmitted,
        transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(250)) },
        label = "rating_state",
        modifier = Modifier.semantics { contentDescription = "message_rating_row" },
    ) { submitted ->
        Row(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (submitted) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = Color(0xFF48BB78),
                    modifier = Modifier.size(11.dp),
                )
                Spacer(Modifier.width(4.dp))
                // Issue 49/61 — render localized "Rated" / "Thanks for your feedback" text.
                Text(
                    text = stringResource(R.string.rated_status),
                    fontSize = AppType.caption,
                    lineHeight = AppType.captionLh,
                    color = CreamDim,
                )
                Spacer(Modifier.width(4.dp))
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
                    fontSize = AppType.caption,
                    lineHeight = AppType.captionLh,
                    color = CreamDim,
                )
                Spacer(Modifier.width(4.dp))
                (1..5).forEach { star ->
                    // Issue 52 — localized accessibility label for each star.
                    val starA11y = stringResource(R.string.a11y_star_rating, star)
                    // A11y: each star sits in a 32×44dp centered tap target (clears the
                    // WCAG 2.5.8 24dp min and gives a 44dp-tall row) while the visible
                    // glyph stays a compact 18dp so the cluster reads tight, not scattered.
                    Box(
                        modifier = Modifier
                            .size(width = 32.dp, height = 44.dp)
                            .alpha(if (isSubmitting) 0.5f else 1f)
                            .clickable(enabled = !isSubmitting) {
                                // Issue 54 — light haptic on tap.
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                // Issue 46/57 — optimistic fill before VM round-trip.
                                selectedRating = star
                                isSubmitting = true
                                onRate(star)
                            }
                            .semantics { contentDescription = starA11y },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            if (star <= selectedRating) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = null,
                            tint = if (star <= selectedRating) Gold else Color.White.copy(alpha = 0.6f),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
                if (isSubmitting) {
                    // Issue 47 — small inline spinner while persistence is in flight.
                    Spacer(Modifier.width(4.dp))
                    CircularProgressIndicator(
                        modifier = Modifier.size(10.dp),
                        color = Gold,
                        strokeWidth = 1.dp,
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
    val label = cosmicStep ?: stringResource(R.string.thinking)
    val a11yLabel = if (cosmicStep != null) {
        stringResource(R.string.cosmic_progress_a11y, label)
    } else {
        stringResource(R.string.a11y_destiny_thinking)
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(NavySurface)
            .border(1.dp, Gold.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .semantics { contentDescription = a11yLabel },
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AnimatedDots()
        // Issue 10 — crossfade label transitions (400ms) for parity with iOS easeInOut.
        AnimatedContent(
            targetState = label,
            transitionSpec = {
                (fadeIn(animationSpec = tween(400)))
                    .togetherWith(fadeOut(animationSpec = tween(400)))
            },
            label = "cosmic_label",
        ) { value ->
            Text(value, fontSize = 14.sp, color = CreamDim)
        }
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
    // Issue 94/95/96: scale-based bounce (1.0 → 1.5), 8dp size, NavyPrimary@40% color
    // matches iOS TypingIndicator.swift more closely.
    val scales = (0..2).map { i ->
        infiniteTransition.animateFloat(
            initialValue = 1.0f,
            targetValue = 1.5f,
            animationSpec = infiniteRepeatable(
                animation = tween(400, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
                initialStartOffset = StartOffset(i * 150),
            ),
            label = "dot$i",
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        // Gap 7 — vertical gold gradient brush on dots, matches iOS TypingIndicator gradient.
        val goldBrush = Brush.verticalGradient(
            listOf(Gold, Gold.copy(alpha = 0.65f)),
        )
        scales.forEach { scale ->
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .graphicsLayer {
                        scaleX = scale.value
                        scaleY = scale.value
                    }
                    .clip(CircleShape)
                    .background(goldBrush),
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
                    .heightIn(min = TouchMin)
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
    onStop: () -> Unit = {},
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
            // Edge-to-edge (enableEdgeToEdge in MainActivity): the chat tab hides the
            // global tab bar, so this input bar is the bottom-most content and must
            // supply its own insets. imePadding() lifts it above the soft keyboard;
            // navigationBarsPadding() keeps it above the gesture pill when the
            // keyboard is down. Without these it clipped behind the gesture nav.
            .imePadding()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .padding(bottom = 4.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        // Issue 4 — track focus so the gold glow + thicker border render when the input
        // is active, mirroring iOS focused-shadow treatment.
        var isFocused by remember { mutableStateOf(false) }
        val focusBorderWidth by animateFloatAsState(
            targetValue = if (isFocused) 1.5f else 1f,
            animationSpec = tween(200),
            label = "focus_border",
        )
        val focusBorderAlpha by animateFloatAsState(
            targetValue = if (isFocused) 0.55f else 0.25f,
            animationSpec = tween(200),
            label = "focus_border_alpha",
        )
        // Full pill wrapping slider + text + send
        Row(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(24.dp))
                .background(NavyInput)
                .border(
                    width = focusBorderWidth.dp,
                    color = Gold.copy(alpha = focusBorderAlpha),
                    shape = RoundedCornerShape(24.dp),
                ),
            verticalAlignment = Alignment.Bottom,
        ) {
            // Style/slider button (left inside pill, hidden when loading)
            if (!isLoading) {
                IconButton(
                    onClick = onStyleTap,
                    modifier = Modifier
                        .size(TouchMin)
                        .semantics { contentDescription = "style_selector_button" },
                ) {
                    Icon(Icons.Default.Tune, contentDescription = null, tint = Gold, modifier = Modifier.size(18.dp))
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp, vertical = 11.dp),
            ) {
                BasicTextField(
                    value = text,
                    onValueChange = onTextChange,
                    textStyle = TextStyle(color = CreamText, fontSize = 16.sp),
                    cursorBrush = SolidColor(Gold),
                    maxLines = 5,
                    // Issue 2 — pop the IME's Send action and submit on Enter when canSend.
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = { if (canSend) dismissAndSend() },
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        // Mirrors iOS ChatView.swift:407-409 .onChange(of: isInputFocused) —
                        // expose focus state so ChatScreen can auto-scroll to the latest
                        // message when the user taps into the input bar.
                        .onFocusChanged {
                            isFocused = it.isFocused
                            onInputFocusChanged(it.isFocused)
                        }
                        .semantics { contentDescription = "chat_input" },
                    decorationBox = { inner ->
                        // DES-161 B1: overlay placeholder and input in a Box so the
                        // decoration height stays constant. Previously they stacked
                        // sequentially, doubling the box height when empty and making
                        // the text jump/shift out of the pill on send + stream-start.
                        // fillMaxWidth ensures the placeholder occupies the full input
                        // width (iOS parity: TextField placeholder is inside the field).
                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                            if (text.isEmpty()) {
                                Text(stringResource(R.string.chat_ask_anything_placeholder), color = Color(0xFF718096), fontSize = 16.sp)
                            }
                            inner()
                        }
                    },
                )
            }

            // Send / stop button (right inside pill). While a generation is in flight
            // we show a Stop button (iOS ChatInputBar.swift:47-56 chat_stop_button) so
            // the user can cancel instead of waiting on a non-interactive spinner.
            Box(
                modifier = Modifier
                    .size(TouchMin)
                    .semantics { contentDescription = if (isLoading) "chat_stop_button" else "send_button" },
                contentAlignment = Alignment.Center,
            ) {
                if (isLoading) {
                    IconButton(onClick = onStop) {
                        Icon(
                            Icons.Filled.Stop,
                            contentDescription = null,
                            tint = Gold,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                } else {
                    IconButton(onClick = dismissAndSend, enabled = canSend) {
                        // Issue 3 — spring-animate icon tint between canSend transitions
                        // so the gold/dim swap mirrors iOS .symbolEffect.
                        AnimatedContent(
                            targetState = canSend,
                            transitionSpec = {
                                (fadeIn(spring(stiffness = Spring.StiffnessMedium)) +
                                    scaleIn(initialScale = 0.85f, animationSpec = spring()))
                                    .togetherWith(fadeOut(animationSpec = tween(150)))
                            },
                            label = "send_icon",
                        ) { enabled ->
                            Icon(
                                Icons.AutoMirrored.Filled.Send,
                                contentDescription = null,
                                tint = if (enabled) Gold else Gold.copy(alpha = 0.25f),
                                modifier = Modifier.size(20.dp),
                            )
                        }
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
 *
 * iOS parity (ChatView.swift:361 cosmicProgressSteps): iOS attaches the cosmic progress
 * indicator to the *streaming message bubble itself*, so it's only visible while a streaming
 * message is in flight. Android creates the streaming assistant message lazily — only on the
 * first SSE chunk — so this list-level pill fills the gap between user-send and first chunk.
 *
 * The predicate must ignore the welcome assistant message (always present, never streaming)
 * which is why we check `messages.none { it.isStreaming }` instead of looking at content —
 * the welcome message has non-empty content and would suppress the pill on every chat after
 * the first turn.
 */
internal fun showThinkingPillInList(isStreaming: Boolean, messages: List<ChatMessage>): Boolean =
    isStreaming && messages.none { it.isStreaming }

// ── Helpers ───────────────────────────────────────────────────────────────────

private val MESSAGE_TIME_FORMATTER: java.time.format.DateTimeFormatter =
    java.time.format.DateTimeFormatter.ofPattern("h:mm a", java.util.Locale.getDefault())

private fun formatMessageTime(ms: Long): String {
    if (ms == 0L) return ""
    // Issue 40 — reuse a static DateTimeFormatter instead of allocating a Calendar
    // per call (saves an instance for every message render).
    val zoned = java.time.Instant.ofEpochMilli(ms).atZone(java.time.ZoneId.systemDefault())
    return zoned.format(MESSAGE_TIME_FORMATTER)
}

// ── Response length sheet (mirrors iOS ResponseLengthSheet) ──────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ResponseLengthSheet(
    current: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    // Issue 71/72 — wrap onSelect so the radio-fill animation has 150ms before the sheet
    // dismisses, plus medium-impact haptic on tap to match iOS.
    val handleSelect: (String) -> Unit = { value ->
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        scope.launch {
            delay(150)
            onSelect(value)
        }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = NavySurface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                // Issue 73 — pin to ~280dp like iOS presentationDetents.
                .heightIn(min = 280.dp)
                .semantics { contentDescription = "response_length_sheet" },
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Issue 65/69/111 — explicit close (X) IconButton in the header for parity with iOS.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.response_style_setting_title),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = CanelaFontFamily,
                    color = Gold,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.semantics { contentDescription = "response_length_close" },
                ) {
                    Icon(Icons.Default.Close, contentDescription = null, tint = CreamDim)
                }
            }
            ResponseLengthOption(
                title = stringResource(R.string.response_length_concise),
                desc = stringResource(R.string.response_length_concise_desc),
                value = "concise",
                isSelected = current == "concise" || current == "short",
                onSelect = handleSelect,
            )
            ResponseLengthOption(
                title = stringResource(R.string.response_length_expanded),
                desc = stringResource(R.string.response_length_expanded_desc),
                value = "detailed",
                isSelected = current == "detailed" || current == "standard",
                onSelect = handleSelect,
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
            Text(title, fontSize = 15.sp, color = CreamText, fontWeight = FontWeight.Normal)
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
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .semantics { contentDescription = "quota_exhausted_guest_sheet" },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Default.Lock, contentDescription = null, tint = Gold, modifier = Modifier.size(40.dp))
            Text(
                stringResource(R.string.guest_sign_up_header),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = CanelaFontFamily,
                color = Gold,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Text(
                stringResource(R.string.guest_sign_up_body),
                fontSize = 14.sp,
                color = CreamDim,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Spacer(Modifier.height(4.dp))
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(
                    R.string.feature_ask_more_questions,
                    R.string.feature_save_birth_chart,
                    R.string.feature_get_daily_insights,
                    R.string.feature_unlock_destiny_matching,
                    R.string.feature_follow_up_match_report,
                ).forEach { resId ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Gold,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            stringResource(resId),
                            fontSize = 14.sp,
                            color = CreamText,
                        )
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Button(
                onClick = onSignIn,
                colors = ButtonDefaults.buttonColors(containerColor = Gold),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "sign_in_button" },
            ) {
                Text(
                    stringResource(R.string.sign_up_button),
                    color = Color(0xFF0D0D1A),
                    fontWeight = FontWeight.Bold,
                )
            }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.not_now), color = CreamDim)
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
    reason: String? = null,
    supportEmail: String? = null,
    onUpgrade: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    // Issue 11 — branch on server-supplied reason. fair_use_violation surfaces a
    // "Usage Restricted / Contact Support" sheet instead of the upgrade interstitial,
    // matching iOS QuotaExhaustedView.fairUseBranch.
    val isFairUse = reason == "fair_use_violation"
    // iOS parity (QuotaExhaustedView.v2Headline/v2Subheadline): a lapsed paid user
    // (subscription_expired) sees "Your subscription has ended" + a "Renew" CTA, and a
    // daily-limit hit gets a dedicated "Daily limit reached" title — instead of the
    // generic upgrade interstitial.
    val isSubscriptionExpired = reason == "subscription_expired"
    val isDailyLimit = reason == "daily_limit_reached"
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
                if (isFairUse) Icons.Default.Warning else Icons.Default.Lock,
                contentDescription = null,
                tint = Gold,
                modifier = Modifier.size(40.dp),
            )
            Text(
                stringResource(
                    when {
                        isFairUse -> R.string.usage_restricted_title
                        isSubscriptionExpired -> R.string.subscription_expired_title
                        isDailyLimit -> R.string.quota_daily_limit_title
                        else -> R.string.quota_upgrade_header
                    },
                ),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = CanelaFontFamily,
                color = Gold,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Text(
                customMessage.ifBlank {
                    stringResource(
                        when {
                            isFairUse -> R.string.fair_use_violation_message
                            isSubscriptionExpired -> R.string.subscription_expired_body
                            else -> R.string.upgrade_to_keep_going
                        },
                    )
                },
                fontSize = 14.sp,
                color = CreamDim,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            if (isFairUse) {
                val email = supportEmail?.takeIf { it.isNotBlank() }
                    ?: stringResource(R.string.fair_use_support_email)
                val subject = stringResource(R.string.fair_use_email_subject)
                Button(
                    onClick = {
                        runCatching {
                            val intent = android.content.Intent(android.content.Intent.ACTION_SENDTO).apply {
                                data = android.net.Uri.parse(
                                    "mailto:$email?subject=" +
                                        android.net.Uri.encode(subject),
                                )
                            }
                            context.startActivity(intent)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Gold),
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = "fair_use_contact_support_button" },
                ) {
                    Text(
                        stringResource(R.string.fair_use_contact_support),
                        color = Color(0xFF0D0D1A),
                        fontWeight = FontWeight.Bold,
                    )
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(
                        R.string.feature_unlimited_questions,
                        R.string.feature_unlimited_matching,
                        R.string.feature_multiple_profiles,
                        R.string.feature_daily_personalized_insights,
                    ).forEach { resId ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Gold,
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                stringResource(resId),
                                fontSize = 14.sp,
                                color = CreamText,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Button(
                    onClick = onUpgrade,
                    colors = ButtonDefaults.buttonColors(containerColor = Gold),
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = "quota_exhausted_upgrade_button" },
                ) {
                    Text(
                        stringResource(if (isSubscriptionExpired) R.string.subscription_expired_cta else R.string.choose_plan_button),
                        color = Color(0xFF0D0D1A),
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.not_now), color = CreamDim)
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
