package com.destinyai.astrology.data.repository

import com.destinyai.astrology.domain.model.ChatMessage
import com.destinyai.astrology.domain.model.ChatThread
import kotlinx.coroutines.flow.Flow

interface ChatRepository {
    suspend fun sendMessage(sessionId: String, text: String): Flow<Result<String>>
    suspend fun loadHistory(): List<ChatThread>
    suspend fun loadThread(threadId: String): List<ChatMessage>
}
