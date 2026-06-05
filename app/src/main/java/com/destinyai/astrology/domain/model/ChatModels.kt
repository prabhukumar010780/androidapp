package com.destinyai.astrology.domain.model

/**
 * Mirrors iOS LocalChatMessage. Adds tool/source/advice/execution/trace metadata
 * and a user-supplied rating so the assistant bubble can render the same depth
 * layers, chips, exec-time pill and inline rating that iOS ships.
 */
data class ChatMessage(
    val id: String,
    val role: Role,
    val content: String,
    val hasChartData: Boolean = false,
    val isStreaming: Boolean = false,
    val createdAtMs: Long = 0L,
    // ── Reading-layout metadata (parity with iOS LocalChatMessage) ──
    /** Names of tools the LLM invoked while answering. Renders as wand-and-stars chips. */
    val toolCalls: List<String> = emptyList(),
    /** Source citations the LLM referenced. Renders as book chips. */
    val sources: List<String> = emptyList(),
    /** "Why this is happening" body for DepthLayersView. */
    val advice: String? = null,
    /** End-to-end execution time in milliseconds. >0 renders as "• 1.4s" pill. */
    val executionTimeMs: Double = 0.0,
    /** Server trace id used for inline rating submission. */
    val traceId: String? = null,
    /** 1..5 star rating, 0 = not yet rated. */
    val rating: Int = 0,
) {
    enum class Role { USER, ASSISTANT, SYSTEM }
}

data class ChatThread(
    val id: String,
    val title: String,
    val preview: String = "",
    val messageCount: Int = 0,
    val isPinned: Boolean = false,
    val updatedAtMs: Long = 0L,
    // Mirrors iOS ChatModels.swift:75 — `primaryArea: String?` used to render the
    // per-row icon (briefcase / heart / dollar / sparkles).
    val primaryArea: String? = null,
)
