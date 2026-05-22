package com.destinyai.astrology.domain.model

data class ChatMessage(
    val id: String,
    val role: Role,
    val content: String,
    val hasChartData: Boolean = false,
) {
    enum class Role { USER, ASSISTANT, SYSTEM }
}

data class ChatThread(
    val id: String,
    val title: String,
)
