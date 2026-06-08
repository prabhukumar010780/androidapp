package com.destinyai.astrology.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.destinyai.astrology.data.local.db.CompatibilityHistoryDao
import com.destinyai.astrology.data.local.db.CompatibilityHistoryEntity
import com.destinyai.astrology.data.local.prefs.UserPreferences
import com.destinyai.astrology.data.repository.ChatRepository
import com.destinyai.astrology.domain.model.ChatThread
import com.destinyai.astrology.domain.model.CompatChatMessageData
import com.destinyai.astrology.domain.model.CompatibilityHistoryItem
import com.destinyai.astrology.domain.model.ComparisonGroup
import com.destinyai.astrology.domain.model.CompatibilityResult
import com.google.gson.Gson
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

/**
 * Unified history item — mirrors iOS UnifiedHistoryItem (HistoryViewModel.swift:5-25).
 * Chats, single matches, and multi-partner match groups are all sortable into a
 * single date-bucketed feed via [timestampMs].
 */
sealed class UnifiedHistoryItem {
    abstract val id: String
    abstract val timestampMs: Long
    abstract val isPinned: Boolean

    data class Chat(val thread: ChatThread) : UnifiedHistoryItem() {
        override val id: String get() = "chat_${thread.id}"
        override val timestampMs: Long get() = thread.updatedAtMs
        override val isPinned: Boolean get() = thread.isPinned
    }

    data class Match(val item: CompatibilityHistoryDisplayItem) : UnifiedHistoryItem() {
        override val id: String get() = "match_${item.sessionId}"
        override val timestampMs: Long get() = item.timestampMs
        override val isPinned: Boolean get() = item.isPinned
    }

    data class MatchGroup(val group: CompatibilityGroup) : UnifiedHistoryItem() {
        override val id: String get() = "group_${group.id}"
        override val timestampMs: Long get() = group.timestampMs
        override val isPinned: Boolean get() = group.isPinned
    }
}

data class CompatibilityHistoryDisplayItem(
    val sessionId: String,
    val boyName: String,
    val girlName: String,
    val totalScore: Int,
    val maxScore: Int,
    val displayDate: String,
    val isPinned: Boolean = false,
    // Mirrors iOS ComparisonGroup linkage — when present, this row is part of a
    // multi-partner group (HistoryView.swift:350-353, 428-447).
    val comparisonGroupId: String? = null,
    val timestampMs: Long = 0L,
    // Mirrors iOS HistoryView.swift:413 (`match.chatMessages.filter { $0.isUser }.count`) —
    // count of follow-up user questions on this match. Render the chat-bubble badge
    // when > 0.
    val userQuestionCount: Int = 0,
)

/**
 * Mirrors iOS `ComparisonGroup` (CompatibilityHistoryItem.swift:127-172) — one
 * user evaluated against multiple partners, displayed as a single grouped row
 * with best-score + partner-count badge in the History list.
 */
data class CompatibilityGroup(
    val id: String,
    val timestampMs: Long,
    val userName: String,
    val items: List<CompatibilityHistoryDisplayItem>,
    val isPinned: Boolean,
) {
    val partnerCount: Int get() = items.size
    val bestItem: CompatibilityHistoryDisplayItem?
        get() = items.maxByOrNull { it.totalScore }
}

/**
 * Date-bucket key — mirrors iOS HistoryViewModel.formatSectionDate which produces
 * "Today" / "Yesterday" / "This Week" / "<MMMM d, yyyy>" headers, NOT a fixed
 * 4-value enum. Older items each get their own dated section.
 */
sealed class HistorySectionKey : Comparable<HistorySectionKey> {
    abstract val sortMs: Long

    object Today : HistorySectionKey() {
        override val sortMs: Long get() = Long.MAX_VALUE
    }

    object Yesterday : HistorySectionKey() {
        override val sortMs: Long get() = Long.MAX_VALUE - 1
    }

    object ThisWeek : HistorySectionKey() {
        override val sortMs: Long get() = Long.MAX_VALUE - 2
    }

    /** Earlier sections — one per calendar day, label = formatted MMMM d, yyyy. */
    data class Day(val startOfDayMs: Long) : HistorySectionKey() {
        override val sortMs: Long get() = startOfDayMs
    }

    override fun compareTo(other: HistorySectionKey): Int =
        // Newest first.
        other.sortMs.compareTo(this.sortMs)
}

data class HistoryUiState(
    val threads: List<ChatThread> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    // Mirrors iOS HistoryView.swift:171-190 — paged display window over the full thread list.
    val displayedThreadCount: Int = 20,
    val searchText: String = "",
    val error: String? = null,
    // Retained only for any caller still toggling a tab; the new UI is a single
    // unified feed (mirrors iOS) and ignores this value.
    val selectedTab: Int = 0,
    val compatibilityItems: List<CompatibilityHistoryDisplayItem> = emptyList(),
    val compatibilitySearchText: String = "",
    val isCompatibilityLoading: Boolean = false,
    // Mirrors iOS HistoryView.historyDisabledView (HistoryView.swift:21-22, 73-107)
    val isHistoryEnabled: Boolean = true,
) {
    val filteredThreads: List<ChatThread>
        get() {
            val matched = if (searchText.isBlank()) threads
            else threads.filter { it.title.contains(searchText, ignoreCase = true) }
            return matched.take(displayedThreadCount)
        }

    val hasMoreThreads: Boolean
        get() {
            val matched = if (searchText.isBlank()) threads
            else threads.filter { it.title.contains(searchText, ignoreCase = true) }
            return displayedThreadCount < matched.size
        }

    /**
     * Collapse compatibility items that share `comparisonGroupId` into a single
     * `CompatibilityGroup` row with best-score + partner-count badge — matches
     * iOS HistoryViewModel.unifiedItems (.matchGroup case).
     */
    val compatibilityGroups: List<CompatibilityGroup>
        get() {
            val source = filteredCompatibilityItems
            return source
                .filter { !it.comparisonGroupId.isNullOrBlank() }
                .groupBy { it.comparisonGroupId!! }
                .filter { (_, items) -> items.size >= 2 }
                .map { (groupId, items) ->
                    val sorted = items.sortedByDescending { it.timestampMs }
                    CompatibilityGroup(
                        id = groupId,
                        timestampMs = sorted.firstOrNull()?.timestampMs ?: 0L,
                        userName = sorted.firstOrNull()?.boyName.orEmpty(),
                        items = sorted,
                        isPinned = sorted.any { it.isPinned },
                    )
                }
                .sortedByDescending { it.timestampMs }
        }

    /**
     * Compatibility singletons — items NOT part of any 2+ partner group, so
     * the existing single-row UI continues to render them.
     */
    val compatibilitySingletons: List<CompatibilityHistoryDisplayItem>
        get() {
            val source = filteredCompatibilityItems
            val grouped = source
                .filter { !it.comparisonGroupId.isNullOrBlank() }
                .groupBy { it.comparisonGroupId!! }
            val groupedIds = grouped.filter { it.value.size >= 2 }.keys
            return source.filter { it.comparisonGroupId.isNullOrBlank() || it.comparisonGroupId !in groupedIds }
        }

    private val filteredCompatibilityItems: List<CompatibilityHistoryDisplayItem>
        get() {
            // Use the unified searchText so a single query filters both kinds —
            // mirrors iOS HistoryViewModel.filteredGroupedItems.
            val q = searchText.ifBlank { compatibilitySearchText }
            return if (q.isBlank()) compatibilityItems
            else compatibilityItems.filter {
                it.boyName.contains(q, ignoreCase = true) ||
                    it.girlName.contains(q, ignoreCase = true)
            }
        }

    /**
     * Single unified feed merging chats, single matches, and match groups —
     * mirrors iOS HistoryView.swift:158-178 + HistoryViewModel.filteredGroupedItems.
     * Sorted newest-first, then bucketed into date sections (Today / Yesterday /
     * This Week / per-day for older items).
     */
    val unifiedSections: Map<HistorySectionKey, List<UnifiedHistoryItem>>
        get() {
            val merged = buildList<UnifiedHistoryItem> {
                filteredThreads.forEach { add(UnifiedHistoryItem.Chat(it)) }
                compatibilityGroups.forEach { add(UnifiedHistoryItem.MatchGroup(it)) }
                compatibilitySingletons.forEach { add(UnifiedHistoryItem.Match(it)) }
            }.sortedByDescending { it.timestampMs }

            val now = System.currentTimeMillis()
            val sections = linkedMapOf<HistorySectionKey, MutableList<UnifiedHistoryItem>>()
            for (item in merged) {
                val key = sectionKeyFor(item.timestampMs, now)
                sections.getOrPut(key) { mutableListOf() }.add(item)
            }
            return sections.toSortedMap()
        }
}

private fun sectionKeyFor(itemMs: Long, nowMs: Long): HistorySectionKey {
    if (itemMs <= 0L) return HistorySectionKey.Day(0L)
    val cal = Calendar.getInstance()
    cal.timeInMillis = nowMs
    cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
    val startOfToday = cal.timeInMillis
    val startOfYesterday = startOfToday - 24L * 60L * 60L * 1000L
    val startOfWeek = startOfToday - 7L * 24L * 60L * 60L * 1000L
    return when {
        itemMs >= startOfToday -> HistorySectionKey.Today
        itemMs >= startOfYesterday -> HistorySectionKey.Yesterday
        itemMs >= startOfWeek -> HistorySectionKey.ThisWeek
        else -> {
            val itemCal = Calendar.getInstance()
            itemCal.timeInMillis = itemMs
            itemCal.set(Calendar.HOUR_OF_DAY, 0); itemCal.set(Calendar.MINUTE, 0)
            itemCal.set(Calendar.SECOND, 0); itemCal.set(Calendar.MILLISECOND, 0)
            HistorySectionKey.Day(itemCal.timeInMillis)
        }
    }
}

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val repository: ChatRepository,
    private val compatibilityHistoryDao: CompatibilityHistoryDao,
    private val prefs: UserPreferences,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState

    private var currentOwnerEmail: String? = null

    init {
        // Mirrors iOS: HistorySettingsManager.shared.isHistoryEnabled drives the
        // disabled-state UI on the History screen.
        prefs.isHistoryEnabledFlow
            .onEach { enabled -> _uiState.update { it.copy(isHistoryEnabled = enabled) } }
            .launchIn(viewModelScope)

        // Mirrors iOS HistoryView.swift:62-66 — reload visible history when the
        // user switches the active profile so each profile sees its own threads.
        prefs.activeProfileIdFlow
            .onEach {
                // drop pinned-cache-key so subsequent loadHistory() rebinds correctly
                currentOwnerEmail = null
                loadHistory()
            }
            .launchIn(viewModelScope)
    }

    /**
     * Clears the surfaced error after the user dismisses the Snackbar — mirrors
     * the iOS pattern of single-shot error toasts (HistoryView.swift:53-58).
     */
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun loadHistory() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                // Cache the active email so the unified feed always pulls the
                // matching compatibility list.
                val email = prefs.getUserEmail()
                if (email != null && currentOwnerEmail == null) {
                    currentOwnerEmail = email
                    bindCompatibilityFlow(email)
                }
                val threads = repository.loadHistory()
                _uiState.update { it.copy(threads = threads, isLoading = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "Failed to load history") }
            }
        }
    }

    fun loadCompatibilityHistory(ownerEmail: String) {
        currentOwnerEmail = ownerEmail
        bindCompatibilityFlow(ownerEmail)
    }

    private fun bindCompatibilityFlow(ownerEmail: String) {
        compatibilityHistoryDao.observeAll(ownerEmail)
            .onEach { entities ->
                val items = entities.map { it.toDisplayItem() }
                _uiState.update { it.copy(compatibilityItems = items, isCompatibilityLoading = false) }
            }
            .launchIn(viewModelScope)
    }

    fun setTab(index: Int) {
        // Retained for backward compat with any caller still passing a tab index.
        // The new unified feed pulls from both sources regardless.
        _uiState.update { it.copy(selectedTab = index) }
        loadHistory()
        val email = currentOwnerEmail
        if (email != null) {
            loadCompatibilityHistory(email)
        } else {
            viewModelScope.launch {
                prefs.getUserEmail()?.let { loadCompatibilityHistory(it) }
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

    fun deleteCompatibilityGroup(groupId: String) {
        viewModelScope.launch {
            try {
                val ids = _uiState.value.compatibilityItems
                    .filter { it.comparisonGroupId == groupId }
                    .map { it.sessionId }
                ids.forEach { compatibilityHistoryDao.delete(it) }
                _uiState.update { state ->
                    state.copy(compatibilityItems = state.compatibilityItems.filterNot { it.comparisonGroupId == groupId })
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "Failed to delete group") }
            }
        }
    }

    fun pinThread(threadId: String) {
        viewModelScope.launch {
            try {
                val target = _uiState.value.threads.firstOrNull { it.id == threadId } ?: return@launch
                val newPinned = !target.isPinned
                // Persist locally + best-effort server sync (mirrors iOS
                // ChatHistorySyncService.swift `is_pinned` round-trip).
                repository.setThreadPinned(threadId, newPinned)
                _uiState.update { state ->
                    state.copy(
                        threads = state.threads.map { thread ->
                            if (thread.id == threadId) thread.copy(isPinned = newPinned) else thread
                        },
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "Failed to pin thread") }
            }
        }
    }

    fun pinCompatibilityItem(sessionId: String) {
        viewModelScope.launch {
            try {
                val target = _uiState.value.compatibilityItems.firstOrNull { it.sessionId == sessionId } ?: return@launch
                compatibilityHistoryDao.setPin(sessionId, !target.isPinned)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "Failed to pin item") }
            }
        }
    }

    fun pinCompatibilityGroup(groupId: String) {
        viewModelScope.launch {
            try {
                val groupItems = _uiState.value.compatibilityItems.filter { it.comparisonGroupId == groupId }
                if (groupItems.isEmpty()) return@launch
                val newPinned = !groupItems.any { it.isPinned }
                groupItems.forEach { compatibilityHistoryDao.setPin(it.sessionId, newPinned) }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "Failed to pin group") }
            }
        }
    }

    fun setSearchText(text: String) =
        _uiState.update { it.copy(searchText = text, displayedThreadCount = 20) }

    fun loadMoreIfNeeded(currentIndex: Int) {
        val state = _uiState.value
        if (state.isLoadingMore || !state.hasMoreThreads) return
        // Trigger when within 5 items of the end (mirrors iOS HistoryView.swift:171-175).
        if (currentIndex >= state.displayedThreadCount - 5) {
            viewModelScope.launch {
                _uiState.update { it.copy(isLoadingMore = true) }
                kotlinx.coroutines.delay(150) // brief async tick so spinner renders
                _uiState.update {
                    it.copy(
                        displayedThreadCount = it.displayedThreadCount + 20,
                        isLoadingMore = false,
                    )
                }
            }
        }
    }

    fun setCompatibilitySearchText(text: String) =
        _uiState.update { it.copy(compatibilitySearchText = text) }

    private fun CompatibilityHistoryEntity.toDisplayItem(): CompatibilityHistoryDisplayItem {
        // Mirrors iOS HistoryView.swift:413 — derive userQuestionCount from
        // `match.chatMessages.filter { $0.isUser }.count` rather than hardcoding 0.
        // We hydrate the full domain object first so the count reflects real chat
        // activity once chatMessages are populated (entity currently stores them
        // only via in-memory result hydration; once a chat-messages column lands
        // this naturally lights up).
        val domain = toDomainItem()
        val userCount = domain.chatMessages.count { it.isUser }
        return CompatibilityHistoryDisplayItem(
            sessionId = sessionId,
            boyName = boyName,
            girlName = girlName,
            totalScore = totalScore,
            maxScore = maxScore,
            displayDate = formatTimestamp(timestampMs),
            isPinned = isPinned,
            comparisonGroupId = comparisonGroupId,
            timestampMs = timestampMs,
            userQuestionCount = userCount,
        )
    }

    /**
     * Hydrate the full [CompatibilityHistoryItem] for a saved session. Mirrors iOS
     * `CompatibilityHistoryService.shared.get(sessionId:)` — used by the History
     * screen when the user taps a row so the navigation callback receives the
     * complete object (not the lite display variant).
     */
    suspend fun getCompatibilityItem(sessionId: String): CompatibilityHistoryItem? =
        compatibilityHistoryDao.getById(sessionId)?.toDomainItem()

    /**
     * Hydrate every saved session in a comparison group. Mirrors iOS
     * `HistoryView.handleSelection` for `.matchGroup` — the navigation callback
     * receives a fully-rehydrated [ComparisonGroup] instead of a stripped lite
     * payload.
     */
    suspend fun getCompatibilityGroup(groupId: String): ComparisonGroup? {
        val items = compatibilityHistoryDao.getByGroupId(groupId).map { it.toDomainItem() }
        if (items.isEmpty()) return null
        return ComparisonGroup(
            id = groupId,
            timestamp = items.first().timestampMs,
            userName = items.first().boyName,
            items = items,
        )
    }

    private fun CompatibilityHistoryEntity.toDomainItem(): CompatibilityHistoryItem =
        CompatibilityHistoryItem(
            sessionId = sessionId,
            timestampMs = timestampMs,
            boyName = boyName,
            boyDob = boyDob,
            boyCity = boyCity,
            boyTime = boyTime,
            girlName = girlName,
            girlDob = girlDob,
            girlCity = girlCity,
            girlTime = girlTime,
            totalScore = totalScore,
            maxScore = maxScore,
            isPinned = isPinned,
            comparisonGroupId = comparisonGroupId,
            partnerIndex = partnerIndex,
            chatMessages = emptyList<CompatChatMessageData>(),
            result = resultJson.takeIf { it.isNotEmpty() }?.let {
                runCatching { gson.fromJson(it, CompatibilityResult::class.java) }.getOrNull()
            },
        )

    private val gson = Gson()

    private fun formatTimestamp(ms: Long): String {
        val sdf = java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.ENGLISH)
        return sdf.format(java.util.Date(ms))
    }
}
