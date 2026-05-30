package com.destinyai.astrology.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.destinyai.astrology.data.local.db.CompatibilityHistoryDao
import com.destinyai.astrology.data.local.db.CompatibilityHistoryEntity
import com.destinyai.astrology.data.repository.ChatRepository
import com.destinyai.astrology.domain.model.ChatThread
import com.destinyai.astrology.domain.model.CompatibilityHistoryItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CompatibilityHistoryDisplayItem(
    val sessionId: String,
    val boyName: String,
    val girlName: String,
    val totalScore: Int,
    val maxScore: Int,
    val displayDate: String,
    val isPinned: Boolean = false,
)

data class HistoryUiState(
    val threads: List<ChatThread> = emptyList(),
    val isLoading: Boolean = false,
    val searchText: String = "",
    val error: String? = null,
    val selectedTab: Int = 0,
    val compatibilityItems: List<CompatibilityHistoryDisplayItem> = emptyList(),
    val isCompatibilityLoading: Boolean = false,
) {
    val filteredThreads: List<ChatThread>
        get() = if (searchText.isBlank()) threads
        else threads.filter { it.title.contains(searchText, ignoreCase = true) }
}

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val repository: ChatRepository,
    private val compatibilityHistoryDao: CompatibilityHistoryDao,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState

    private var currentOwnerEmail: String? = null

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

    fun loadCompatibilityHistory(ownerEmail: String) {
        currentOwnerEmail = ownerEmail
        compatibilityHistoryDao.observeAll(ownerEmail)
            .onEach { entities ->
                val items = entities.map { it.toDisplayItem() }
                _uiState.update { it.copy(compatibilityItems = items, isCompatibilityLoading = false) }
            }
            .launchIn(viewModelScope)
    }

    fun setTab(index: Int) {
        _uiState.update { it.copy(selectedTab = index) }
        when (index) {
            0 -> loadHistory()
            1 -> currentOwnerEmail?.let { loadCompatibilityHistory(it) }
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

    fun deleteCompatibilityItem(sessionId: String) {
        viewModelScope.launch {
            try {
                compatibilityHistoryDao.delete(sessionId)
                _uiState.update { state ->
                    state.copy(compatibilityItems = state.compatibilityItems.filterNot { it.sessionId == sessionId })
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "Failed to delete compatibility item") }
            }
        }
    }

    fun setSearchText(text: String) = _uiState.update { it.copy(searchText = text) }

    private fun CompatibilityHistoryEntity.toDisplayItem() = CompatibilityHistoryDisplayItem(
        sessionId = sessionId,
        boyName = boyName,
        girlName = girlName,
        totalScore = totalScore,
        maxScore = maxScore,
        displayDate = formatTimestamp(timestampMs),
        isPinned = isPinned,
    )

    private fun formatTimestamp(ms: Long): String {
        val sdf = java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.ENGLISH)
        return sdf.format(java.util.Date(ms))
    }
}
