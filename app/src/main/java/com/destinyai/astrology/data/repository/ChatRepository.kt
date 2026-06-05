package com.destinyai.astrology.data.repository

import com.destinyai.astrology.domain.model.ChatMessage
import com.destinyai.astrology.domain.model.ChatThread
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow

// Mirrors iOS StreamingPredictionService.StreamEvent. The repository emits
// progress on a side channel so the ChatViewModel can drive the same
// reasoning-trace / progress UI that iOS shows during long Opus streams.
sealed class ChatStreamEvent {
    data class Thought(val step: Int, val content: String, val display: String) : ChatStreamEvent()
    data class Action(val step: Int, val tool: String, val display: String) : ChatStreamEvent()
    data class Observation(val step: Int, val display: String) : ChatStreamEvent()
    data class ProgressStep(
        val phase: String,
        val group: Int,
        val groupCount: Int,
        val isDone: Boolean,
        val displayKey: String?,
        val elapsedMs: Int,
    ) : ChatStreamEvent()
    data class FinalAnswer(val content: String) : ChatStreamEvent()
    data class Done(val totalSteps: Int) : ChatStreamEvent()
    // Mirrors iOS PredictionResponse.followUpSuggestions — surfaced when backend
    // emits a terminal `answer` event with structured payload.
    data class FollowUpSuggestions(val suggestions: List<String>) : ChatStreamEvent()

    /**
     * Mirrors iOS PredictionResponse metadata fields surfaced when the backend's
     * terminal `answer` event is parsed. The ViewModel uses these to enrich the
     * assistant ChatMessage with tool-call chips, source chips, advice (used by
     * DepthLayersView), an execution-time pill and a trace id for ratings.
     */
    data class Metadata(
        val toolCalls: List<String> = emptyList(),
        val sources: List<String> = emptyList(),
        val advice: String? = null,
        val executionTimeMs: Double = 0.0,
        val traceId: String? = null,
    ) : ChatStreamEvent()
}

interface ChatRepository {
    suspend fun sendMessage(sessionId: String, text: String): Flow<Result<String>>
    suspend fun loadHistory(): List<ChatThread>
    /**
     * Paginated history load — mirrors iOS dataManager.fetchChatThreadsPaginated (ChatView.swift:512-644).
     * Returns a slice of threads. Use offset/limit so the history sheet can load incrementally.
     */
    suspend fun loadHistoryPaginated(offset: Int, limit: Int): List<ChatThread>
    suspend fun loadThread(threadId: String): List<ChatMessage>
    suspend fun deleteThread(threadId: String)

    /** Sync threads from server into local DB (best-effort). */
    suspend fun syncThreadsFromApi()

    /** Persist pin state locally + best-effort server sync. Returns new pin state. */
    suspend fun setThreadPinned(threadId: String, pinned: Boolean)

    /**
     * Mirrors iOS FeedbackService.submit — submits a 1..5 star rating for a
     * specific assistant message. Best-effort: returns false on transport error,
     * the UI still latches the local "thank-you" state regardless.
     */
    suspend fun submitRating(
        traceId: String?,
        sessionId: String?,
        userEmail: String?,
        query: String,
        responseText: String,
        rating: Int,
    ): Boolean

    /**
     * Mirrors iOS WindowManager.loadOlderMessages — fetches the next page of
     * older messages BEFORE the current head of the message list for the
     * active thread. Used by the inline "Load earlier messages" button.
     */
    suspend fun loadOlderMessages(threadId: String, beforeMs: Long, limit: Int): List<com.destinyai.astrology.domain.model.ChatMessage>

    /** Side channel for non-answer SSE events (thought/action/observation/progress_step/final_answer/done). */
    val progressEvents: SharedFlow<ChatStreamEvent>
}
