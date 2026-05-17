package com.destinyai.astrology.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.destinyai.astrology.data.repository.ChatRepository
import com.destinyai.astrology.domain.model.ChatThread
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HistoryUiState(
    val threads: List<ChatThread> = emptyList(),
    val isLoading: Boolean = false,
    val searchText: String = "",
    val error: String? = null,
) {
    val filteredThreads: List<ChatThread>
        get() = if (searchText.isBlank()) threads
        else threads.filter { it.title.contains(searchText, ignoreCase = true) }
}

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val repository: ChatRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState

    fun loadHistory() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val threads = repository.loadHistory()
                _uiState.update { it.copy(threads = threads, isLoading = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "Failed to load history") }
            }
        }
    }

    fun deleteThread(threadId: String) {
        viewModelScope.launch {
            try {
                repository.deleteThread(threadId)
                _uiState.update { state ->
                    state.copy(threads = state.threads.filterNot { it.id == threadId })
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "Failed to delete thread") }
            }
        }
    }

    fun setSearchText(text: String) = _uiState.update { it.copy(searchText = text) }
}
