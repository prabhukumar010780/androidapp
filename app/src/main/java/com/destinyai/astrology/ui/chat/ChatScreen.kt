package com.destinyai.astrology.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.destinyai.astrology.R
import com.destinyai.astrology.domain.model.ChatMessage
import com.destinyai.astrology.ui.theme.CosmicBackground
import com.destinyai.astrology.ui.theme.Gold
import com.destinyai.astrology.ui.theme.CreamText
import com.destinyai.astrology.ui.theme.CreamDim
import com.destinyai.astrology.ui.theme.NavySurface
import com.destinyai.astrology.ui.theme.NavyVariant

private val starterQuestions = listOf(
    "What should I be mindful of today?",
    "How can I improve my focus and productivity?",
    "What's a good time for important decisions?",
    "What does my chart say about relationships?",
)

@Composable
fun ChatScreen(
    modifier: Modifier = Modifier,
    onNavigateToHistory: (() -> Unit)? = null,
    onNavigateToCharts: (() -> Unit)? = null,
    viewModel: ChatViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.size - 1)
        }
    }

    CosmicBackground(modifier = modifier) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Chat header
            ChatHeader(
                onHistoryTap = { onNavigateToHistory?.invoke(); viewModel.loadHistory() },
                onNewChatTap = { viewModel.startNewChat() },
                onChartTap = { onNavigateToCharts?.invoke() },
            )

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 16.dp),
            ) {
                items(state.messages, key = { it.id }) { message ->
                    ChatBubble(message = message)
                }

                // Starter questions — show when only welcome message present
                if (state.messages.size == 1) {
                    item {
                        StarterQuestions(
                            questions = starterQuestions,
                            onQuestionTap = { q ->
                                viewModel.updateInput(q)
                                viewModel.sendMessage()
                            },
                        )
                    }
                }

                if (state.isStreaming) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Start,
                        ) {
                            StreamingDots()
                        }
                    }
                }
            }

            if (state.errorMessage != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF3A1A1A))
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = state.errorMessage ?: "",
                        color = Color(0xFFFF8A80),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            // Input bar
            ChatInputBar(
                text = state.inputText,
                onTextChange = viewModel::updateInput,
                onSend = viewModel::sendMessage,
                canSend = state.canSend,
            )
        }
    }
}

@Composable
private fun ChatHeader(
    onHistoryTap: () -> Unit,
    onNewChatTap: () -> Unit,
    onChartTap: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onHistoryTap) {
            Icon(Icons.Filled.History, contentDescription = "History", tint = CreamDim)
        }
        Text(
            text = "Ask Destiny",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = CreamText,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        IconButton(onClick = onChartTap) {
            Icon(Icons.Outlined.PieChart, contentDescription = "Chart", tint = CreamDim)
        }
        IconButton(onClick = onNewChatTap) {
            Icon(Icons.Filled.Add, contentDescription = "New Chat", tint = Gold)
        }
    }
}

@Composable
private fun StarterQuestions(questions: List<String>, onQuestionTap: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        questions.forEach { q ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(NavySurface)
                    .border(0.5.dp, Gold.copy(alpha = 0.25f), RoundedCornerShape(10.dp))
                    .clickable { onQuestionTap(q) }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                Text(
                    text = q,
                    style = MaterialTheme.typography.bodyMedium,
                    color = CreamDim,
                )
            }
        }
    }
}

@Composable
private fun ChatBubble(message: ChatMessage) {
    val isUser = message.role == ChatMessage.Role.USER
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp,
            ),
            color = if (isUser) Gold.copy(alpha = 0.2f) else NavySurface,
            modifier = Modifier.widthIn(max = 280.dp),
        ) {
            Text(
                text = message.content,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isUser) Gold else CreamText,
                modifier = Modifier.padding(12.dp),
                lineHeight = 20.sp,
            )
        }
    }
}

@Composable
private fun StreamingDots() {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(4.dp),
    ) {
        Text(text = "✦", color = Gold.copy(alpha = 0.6f), fontSize = 12.sp)
        CircularProgressIndicator(
            modifier = Modifier.size(14.dp),
            color = Gold.copy(alpha = 0.6f),
            strokeWidth = 1.5.dp,
        )
    }
}

@Composable
private fun ChatInputBar(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    canSend: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(NavySurface)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(24.dp))
                .background(NavyVariant)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            BasicTextField(
                value = text,
                onValueChange = onTextChange,
                textStyle = TextStyle(
                    color = CreamText,
                    fontSize = 15.sp,
                ),
                cursorBrush = SolidColor(Gold),
                modifier = Modifier.fillMaxWidth(),
                maxLines = 4,
                decorationBox = { inner ->
                    if (text.isEmpty()) {
                        Text(
                            text = stringResource(R.string.type_question),
                            color = Color(0xFF718096),
                            fontSize = 15.sp,
                        )
                    }
                    inner()
                },
            )
        }
        Spacer(Modifier.width(8.dp))
        IconButton(
            onClick = onSend,
            enabled = canSend,
        ) {
            Icon(
                Icons.AutoMirrored.Filled.Send,
                contentDescription = "Send",
                tint = if (canSend) Gold else Gold.copy(alpha = 0.25f),
                modifier = Modifier.size(22.dp),
            )
        }
    }
}
