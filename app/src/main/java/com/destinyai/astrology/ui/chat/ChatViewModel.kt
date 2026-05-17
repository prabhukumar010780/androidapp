package com.destinyai.astrology.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.destinyai.astrology.data.repository.ChatRepository
import com.destinyai.astrology.domain.model.ChatMessage
import com.destinyai.astrology.domain.model.ChatThread
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class ChatUiState(
    val sessionId: String? = null,
    val messages: List<ChatMessage> = emptyList(),
    val inputText: String = "",
    val canSend: Boolean = false,
    val isLoading: Boolean = false,
    val isStreaming: Boolean = false,
    val threads: List<ChatThread> = emptyList(),
    val activeThreadId: String? = null,
    val copiedMessageId: String? = null,
    val showPaywall: Boolean = false,
    val errorMessage: String? = null,
)

class UpgradeRequiredException : Exception("upgrade_required")

class ChatViewModel(
    private val repository: ChatRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState

    private val welcomeMessage = ChatMessage(
        id = "welcome",
        role = ChatMessage.Role.ASSISTANT,
        content = "Namaste! I'm your Vedic astrology guide. Ask me anything about your chart, destiny, or daily insights.",
    )

    init {
        _uiState.update {
            it.copy(
                sessionId = UUID.randomUUID().toString(),
                messages = listOf(welcomeMessage),
            )
        }
    }

    fun updateInput(text: String) {
        _uiState.update { state ->
            state.copy(
                inputText = text,
                canSend = text.isNotBlank() && !state.isLoading && !state.isStreaming,
            )
        }
    }

    fun sendMessage() {
        val input = _uiState.value.inputText.trim()
        if (input.isBlank()) return

        val userMsg = ChatMessage(
            id = UUID.randomUUID().toString(),
            role = ChatMessage.Role.USER,
            content = input,
        )
        _uiState.update {
            it.copy(
                messages = it.messages + userMsg,
                inputText = "",
                canSend = false,
                isStreaming = true,
                errorMessage = null,
            )
        }

        viewModelScope.launch {
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
                        )
                        _uiState.update { state ->
                            val msgs = state.messages.filterNot { it.id == assistantId } + assistantMsg
                            state.copy(messages = msgs)
                        }
                    }
                    .onFailure { e ->
                        if (e is UpgradeRequiredException) {
                            _uiState.update { it.copy(isStreaming = false, showPaywall = true) }
                        } else {
                            _uiState.update { it.copy(isStreaming = false, errorMessage = e.message) }
                        }
                    }
            }

            _uiState.update { it.copy(isStreaming = false) }
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

    fun copyMessage(messageId: String) {
        _uiState.update { it.copy(copiedMessageId = messageId) }
    }

    fun loadHistory() {
        viewModelScope.launch {
            val history = repository.loadHistory()
            _uiState.update { it.copy(threads = history) }
        }
    }

    fun openThread(threadId: String) {
        viewModelScope.launch {
            val messages = repository.loadThread(threadId)
            _uiState.update { it.copy(activeThreadId = threadId, messages = messages) }
        }
    }
}
