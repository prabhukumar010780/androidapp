package com.destinyai.astrology.data.repository.impl

import com.destinyai.astrology.data.local.db.ChatMessageDao
import com.destinyai.astrology.data.local.db.ChatThreadDao
import com.destinyai.astrology.data.local.db.LocalChatMessageEntity
import com.destinyai.astrology.data.local.db.LocalChatThreadEntity
import com.destinyai.astrology.data.local.prefs.UserPreferences
import com.destinyai.astrology.data.remote.AstroApiService
import com.destinyai.astrology.data.remote.PredictBirthDataDto
import com.destinyai.astrology.data.remote.PredictRequest
import com.destinyai.astrology.data.repository.ChatRepository
import com.destinyai.astrology.domain.model.ChatMessage
import com.destinyai.astrology.domain.model.ChatThread
import com.google.gson.JsonParser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.BufferedReader
import java.io.InputStreamReader
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepositoryImpl @Inject constructor(
    private val api: AstroApiService,
    private val threadDao: ChatThreadDao,
    private val messageDao: ChatMessageDao,
    private val prefs: UserPreferences,
) : ChatRepository {

    override suspend fun sendMessage(sessionId: String, text: String): Flow<Result<String>> = flow {
        val email = prefs.getUserEmail() ?: run {
            emit(Result.failure(IllegalStateException("No user email")))
            return@flow
        }
        val birthProfile = prefs.getBirthProfile() ?: run {
            emit(Result.failure(IllegalStateException("No birth profile")))
            return@flow
        }
        try {
            val body = api.streamPredict(
                PredictRequest(
                    query = text,
                    userEmail = email,
                    birthData = PredictBirthDataDto(
                        dob = birthProfile.dateOfBirth,
                        time = birthProfile.timeOfBirth,
                        cityOfBirth = birthProfile.cityOfBirth,
                        latitude = birthProfile.latitude,
                        longitude = birthProfile.longitude,
                    ),
                    sessionId = sessionId,
                    conversationId = sessionId,
                )
            )
            val reader = BufferedReader(InputStreamReader(body.byteStream()))
            var line: String?
            var currentEvent = ""
            while (reader.readLine().also { line = it } != null) {
                val raw = line ?: continue
                when {
                    raw.startsWith("event: ") -> currentEvent = raw.removePrefix("event: ").trim()
                    raw.startsWith("data: ") -> {
                        val data = raw.removePrefix("data: ").trim()
                        when (currentEvent) {
                            "answer" -> {
                                val answer = try {
                                    JsonParser.parseString(data).asJsonObject.get("answer")?.asString ?: ""
                                } catch (_: Exception) { data }
                                if (answer.isNotBlank()) emit(Result.success(answer))
                            }
                            "done" -> return@flow
                            "error" -> {
                                val msg = try {
                                    JsonParser.parseString(data).asJsonObject.get("error")?.asString ?: data
                                } catch (_: Exception) { data }
                                emit(Result.failure(Exception(msg)))
                                return@flow
                            }
                        }
                    }
                }
            }
        } catch (e: retrofit2.HttpException) {
            if (e.code() == 402 || e.code() == 429) {
                emit(Result.failure(com.destinyai.astrology.ui.chat.UpgradeRequiredException()))
            } else {
                emit(Result.failure(e))
            }
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }

    override suspend fun loadHistory(): List<ChatThread> {
        val email = prefs.getUserEmail() ?: return emptyList()
        return threadDao.getThreadsForUser(email).map { entity ->
            ChatThread(id = entity.id, title = entity.title)
        }
    }

    override suspend fun deleteThread(threadId: String) {
        val email = prefs.getUserEmail() ?: return
        runCatching { api.deleteChatThread(email, threadId) }
        threadDao.delete(threadId)
        messageDao.deleteForThread(threadId)
    }

    override suspend fun loadThread(threadId: String): List<ChatMessage> {
        return messageDao.getMessagesForThread(threadId).map { entity ->
            ChatMessage(
                id = entity.id,
                role = when (entity.role) {
                    "user" -> ChatMessage.Role.USER
                    "assistant" -> ChatMessage.Role.ASSISTANT
                    else -> ChatMessage.Role.SYSTEM
                },
                content = entity.content,
            )
        }
    }

    suspend fun syncThreadsFromApi() {
        val email = prefs.getUserEmail() ?: return
        val apiThreads = runCatching { api.listChatThreads(email) }.getOrElse { return }
        apiThreads.forEach { dto ->
            threadDao.insert(LocalChatThreadEntity(
                id = dto.threadId,
                ownerEmail = email,
                title = dto.title,
                createdAt = dto.createdAt,
                updatedAt = dto.updatedAt,
            ))
        }
    }

    suspend fun syncThreadMessagesFromApi(threadId: String) {
        val email = prefs.getUserEmail() ?: return
        val messages = runCatching { api.getChatThread(email, threadId) }.getOrElse { return }
        val entities = messages.map { dto ->
            LocalChatMessageEntity(
                id = dto.messageId,
                threadId = threadId,
                role = dto.role,
                content = dto.content,
                createdAt = dto.createdAt,
            )
        }
        messageDao.insertAll(entities)
    }
}
