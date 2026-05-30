package com.destinyai.astrology.domain.model

data class ChatMessage(
    val id: String,
    val role: Role,
    val content: String,
    val hasChartData: Boolean = false,
    val isStreaming: Boolean = false,
    val createdAtMs: Long = 0L,
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
)
