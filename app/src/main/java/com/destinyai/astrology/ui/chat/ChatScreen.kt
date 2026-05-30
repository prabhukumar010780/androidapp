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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
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
import com.destinyai.astrology.ui.theme.*
import com.destinyai.astrology.ui.subscription.SubscriptionScreen
import kotlinx.coroutines.delay

private val defaultStarterQuestions = listOf(
    "What should I be mindful of today?",
    "What does my chart say about relationships?",
    "What's a good time for important decisions?",
    "What is my current dasha period?",
)

@Composable
fun ChatScreen(
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    initialQuestion: String? = null,
    starterQuestions: List<String> = emptyList(),
    onNavigateToCharts: (() -> Unit)? = null,
    viewModel: ChatViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    var showHistory by remember { mutableStateOf(false) }

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

    // Handle initial question on first composition
    LaunchedEffect(initialQuestion) {
        if (!initialQuestion.isNullOrBlank()) {
            viewModel.startNewChat()
            viewModel.updateInput(initialQuestion)
            viewModel.sendMessage()
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
                onNewChatTap = { viewModel.startNewChat() },
                onChartTap = { onNavigateToCharts?.invoke() },
            )

            // Message list
            val isNewChat = state.messages.count { !it.isStreaming } <= 1
            val activeStarters = starterQuestions.ifEmpty { defaultStarterQuestions }.take(4)

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                contentPadding = PaddingValues(vertical = 20.dp),
            ) {
                if (isNewChat && !state.isLoading && !state.isStreaming) {
                    item(key = "starters") {
                        StarterQuestionsView(
                            questions = activeStarters,
                            onQuestionTap = { q ->
                                viewModel.updateInput(q)
                                viewModel.sendMessage()
                            },
                        )
                    }
                } else {
                    items(state.messages, key = { it.id }) { message ->
                        MessageBubbleView(
                            message = message,
                            copiedMessageId = state.copiedMessageId,
                            onCopy = { viewModel.copyMessage(message.id) },
                        )
                    }

                    if (showThinkingPillInList(state.isStreaming, state.messages)) {
                        item(key = "streaming") {
                            ThinkingPill()
                        }
                    }

                    if (state.suggestedQuestions.isNotEmpty() && !state.isLoading && !state.isStreaming) {
                        item(key = "suggestions") {
                            FollowUpSuggestionsView(
                                questions = state.suggestedQuestions,
                                onTap = { q ->
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
            )
        }
    }

    if (showHistory) {
        ChatHistorySheet(
            viewModel = viewModel,
            onDismiss = { showHistory = false },
        )
    }

    if (state.showPaywall) {
        SubscriptionScreen(
            onBack = { viewModel.dismissPaywall() },
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
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 4.dp, vertical = 8.dp),
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
            text = "Ask Destiny",
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
            "Ask Destiny",
            fontFamily = CanelaFontFamily,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            color = CreamText,
        )
        Text(
            "Your personal Vedic astrology guide",
            fontSize = 14.sp,
            color = CreamDim,
        )

        Column(
            modifier = Modifier.padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            questions.forEach { q ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Gold.copy(alpha = 0.1f))
                        .border(1.dp, Gold.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
                        .clickable { onQuestionTap(q) }
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                ) {
                    Text(q, fontSize = 13.sp, color = Gold)
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
        // AI message — reading layout
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = assistantContentDescription(message) },
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (message.isStreaming && message.content.isEmpty()) {
                ThinkingPill()
            } else if (message.content.isNotEmpty()) {
                Text(
                    message.content,
                    fontSize = 16.sp,
                    color = Color.White.copy(alpha = 0.92f),
                    lineHeight = 26.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = "reading_body_text" },
                )

                if (!message.isStreaming) {
                    Row(
                        modifier = Modifier.padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
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
                                    if (showCopied) "Copied" else "⎘ Copy",
                                    fontSize = 11.sp,
                                    color = if (showCopied) Gold else Color.White.copy(alpha = 0.3f),
                                )
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        Text(
                            formatMessageTime(message.createdAtMs),
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.2f),
                        )
                    }
                }
            }
        }
    }
}

// ── Thinking pill (streaming loading state) ───────────────────────────────────

@Composable
fun ThinkingPill() {
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
        Text("Thinking…", fontSize = 14.sp, color = CreamDim)
    }
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
            Text("Chat was interrupted", fontSize = 12.sp, color = CreamDim)
            Text(question, fontSize = 13.sp, color = CreamText, maxLines = 1)
        }
        Button(
            onClick = onRetry,
            colors = ButtonDefaults.buttonColors(containerColor = Gold),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
        ) {
            Text("Retry", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0D0D1A))
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
) {
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
                    onClick = { /* ResponseLengthSheet — stub */ },
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
                        .semantics { contentDescription = "chat_input" },
                    decorationBox = { inner ->
                        if (text.isEmpty()) {
                            Text("Ask anything…", color = Color(0xFF718096), fontSize = 16.sp)
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
                    IconButton(onClick = onSend, enabled = canSend) {
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
