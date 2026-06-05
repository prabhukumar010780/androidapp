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
import com.destinyai.astrology.data.repository.ChatStreamEvent
import com.destinyai.astrology.domain.model.ChatMessage
import com.destinyai.astrology.domain.model.ChatThread
import com.destinyai.astrology.ui.chat.DailyLimitException
import com.destinyai.astrology.ui.chat.GuestLimitException
import com.destinyai.astrology.ui.chat.UpgradeRequiredException
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class ChatRepositoryImpl @Inject constructor(
    private val api: AstroApiService,
    @Named("streaming") private val streamingApi: AstroApiService,
    private val threadDao: ChatThreadDao,
    private val messageDao: ChatMessageDao,
    private val prefs: UserPreferences,
) : ChatRepository {

    private val _progressEvents = MutableSharedFlow<ChatStreamEvent>(extraBufferCapacity = 32)
    override val progressEvents: SharedFlow<ChatStreamEvent> = _progressEvents.asSharedFlow()

    override suspend fun sendMessage(sessionId: String, text: String): Flow<Result<String>> = flow {
        val email = prefs.getUserEmail() ?: run {
            emit(Result.failure(IllegalStateException("No user email")))
            return@flow
        }
        val birthProfile = prefs.getBirthProfile() ?: run {
            emit(Result.failure(IllegalStateException("No birth profile")))
            return@flow
        }
        // Mirrors iOS ChatViewModel.loadBirthData() / sendMessage() — pass user-selected
        // ayanamsa, house system, response style/length, and language so the backend
        // produces output matching the user's Astrology Settings instead of defaults.
        val ayanamsa = runCatching { prefs.getAyanamsa() }.getOrDefault("lahiri")
        val houseSystem = runCatching { prefs.getHouseSystem() }.getOrDefault("whole_sign")
        val responseStyle = runCatching { prefs.getResponseStyle() }.getOrNull()
        val responseLength = runCatching { prefs.getResponseLength() }.getOrNull()
        val language = runCatching { prefs.getSelectedLanguage() }.getOrDefault("en")
        // Persist user message immediately so chat history survives restart (iOS parity).
        // Gated by HistorySettingsManager.isHistoryEnabled — when disabled we skip every save path
        // (matches iOS ChatViewModel:209-219, 260-262 behavior).
        val historyEnabled = runCatching { prefs.isHistoryEnabled() }.getOrDefault(true)
        if (historyEnabled) {
            runCatching {
                threadDao.insert(
                    LocalChatThreadEntity(
                        id = sessionId,
                        ownerEmail = email,
                        title = text.take(60),
                        createdAt = java.time.Instant.now().toString(),
                        updatedAt = java.time.Instant.now().toString(),
                        isPinned = false,
                    ),
                )
                messageDao.insert(
                    LocalChatMessageEntity(
                        id = java.util.UUID.randomUUID().toString(),
                        threadId = sessionId,
                        role = "user",
                        content = text,
                        createdAt = java.time.Instant.now().toString(),
                    ),
                )
            }
        }
        try {
            val body = streamingApi.streamPredict(
                PredictRequest(
                    query = text,
                    userEmail = email,
                    birthData = PredictBirthDataDto(
                        dob = birthProfile.dateOfBirth,
                        time = birthProfile.timeOfBirth,
                        cityOfBirth = birthProfile.cityOfBirth,
                        latitude = birthProfile.latitude,
                        longitude = birthProfile.longitude,
                        ayanamsa = ayanamsa,
                        houseSystem = houseSystem,
                    ),
                    sessionId = sessionId,
                    conversationId = sessionId,
                    language = language,
                    responseStyle = responseStyle,
                    responseLength = responseLength,
                )
            )
            body.byteStream().bufferedReader().use { reader ->
                var line: String?
                var currentEvent = ""
                while (reader.readLine().also { line = it } != null) {
                    val raw = line ?: continue
                    when {
                        raw.startsWith("event: ") -> currentEvent = raw.removePrefix("event: ").trim()
                        raw.startsWith("data: ") -> {
                            val data = raw.removePrefix("data: ").trim()
                            val json: JsonObject? = runCatching {
                                JsonParser.parseString(data).asJsonObject
                            }.getOrNull()
                            // Mirrors iOS StreamingPredictionService.parseEvent — handle 8 SSE event
                            // types so Android shows progress instead of a frozen loader.
                            when (currentEvent) {
                                "thought" -> json?.let {
                                    _progressEvents.emit(
                                        ChatStreamEvent.Thought(
                                            step = it.get("step")?.takeIf { e -> !e.isJsonNull }?.asInt ?: 0,
                                            content = it.get("content")?.takeIf { e -> !e.isJsonNull }?.asString ?: "",
                                            display = it.get("display")?.takeIf { e -> !e.isJsonNull }?.asString ?: "",
                                        )
                                    )
                                }
                                "action" -> json?.let {
                                    _progressEvents.emit(
                                        ChatStreamEvent.Action(
                                            step = it.get("step")?.takeIf { e -> !e.isJsonNull }?.asInt ?: 0,
                                            tool = it.get("tool")?.takeIf { e -> !e.isJsonNull }?.asString ?: "",
                                            display = it.get("display")?.takeIf { e -> !e.isJsonNull }?.asString ?: "",
                                        )
                                    )
                                }
                                "observation" -> json?.let {
                                    _progressEvents.emit(
                                        ChatStreamEvent.Observation(
                                            step = it.get("step")?.takeIf { e -> !e.isJsonNull }?.asInt ?: 0,
                                            display = it.get("display")?.takeIf { e -> !e.isJsonNull }?.asString ?: "",
                                        )
                                    )
                                }
                                "progress_step" -> json?.let {
                                    _progressEvents.emit(
                                        ChatStreamEvent.ProgressStep(
                                            phase = it.get("phase")?.takeIf { e -> !e.isJsonNull }?.asString ?: "",
                                            group = it.get("group")?.takeIf { e -> !e.isJsonNull }?.asInt ?: 0,
                                            groupCount = it.get("group_count")?.takeIf { e -> !e.isJsonNull }?.asInt ?: 1,
                                            isDone = it.get("is_done")?.takeIf { e -> !e.isJsonNull }?.asBoolean ?: false,
                                            displayKey = it.get("display_key")?.takeIf { e -> !e.isJsonNull }?.asString,
                                            elapsedMs = it.get("elapsed_ms")?.takeIf { e -> !e.isJsonNull }?.asInt ?: 0,
                                        )
                                    )
                                }
                                "final_answer" -> json?.let {
                                    val content = it.get("content")?.takeIf { e -> !e.isJsonNull }?.asString ?: ""
                                    _progressEvents.emit(ChatStreamEvent.FinalAnswer(content))
                                }
                                "answer" -> {
                                    val answer = json?.get("answer")?.takeIf { !it.isJsonNull }?.asString ?: data
                                    if (answer.isNotBlank()) emit(Result.success(answer))
                                    // iOS treats terminal answer event as the structured PredictionResponse:
                                    // surface follow_up_suggestions so the FollowUpSuggestionsView can render them.
                                    val suggestionsArr = json?.get("follow_up_suggestions")?.takeIf { !it.isJsonNull }?.asJsonArray
                                    if (suggestionsArr != null) {
                                        val list = suggestionsArr.mapNotNull { e -> runCatching { e.asString }.getOrNull() }
                                        if (list.isNotEmpty()) {
                                            _progressEvents.emit(ChatStreamEvent.FollowUpSuggestions(list))
                                        }
                                    }
                                    // Mirrors iOS PredictionResponse → LocalChatMessage hydration (ChatViewModel.swift
                                    // ~290): tool_calls / sources / advice / execution_time_ms / trace_id are surfaced
                                    // via a Metadata event so the VM can patch the assistant ChatMessage.
                                    if (json != null) {
                                        val toolsArr = json.get("tool_calls")?.takeIf { !it.isJsonNull }?.asJsonArray
                                        val tools = toolsArr?.mapNotNull { e -> runCatching { e.asString }.getOrNull() } ?: emptyList()
                                        val sourcesArr = json.get("sources")?.takeIf { !it.isJsonNull }?.asJsonArray
                                        val sources = sourcesArr?.mapNotNull { e -> runCatching { e.asString }.getOrNull() } ?: emptyList()
                                        val advice = json.get("advice")?.takeIf { !it.isJsonNull }?.asString
                                        val execMs = json.get("execution_time_ms")?.takeIf { !it.isJsonNull }
                                            ?.let { runCatching { it.asDouble }.getOrNull() } ?: 0.0
                                        val traceId = json.get("trace_id")?.takeIf { !it.isJsonNull }?.asString
                                            ?: json.get("prediction_id")?.takeIf { !it.isJsonNull }?.asString
                                        if (tools.isNotEmpty() || sources.isNotEmpty() || !advice.isNullOrBlank() ||
                                            execMs > 0.0 || !traceId.isNullOrBlank()) {
                                            _progressEvents.emit(
                                                ChatStreamEvent.Metadata(
                                                    toolCalls = tools,
                                                    sources = sources,
                                                    advice = advice,
                                                    executionTimeMs = execMs,
                                                    traceId = traceId,
                                                ),
                                            )
                                        }
                                    }
                                    // Persist assistant message locally for history (iOS parity).
                                    // Gated on isHistoryEnabled (mirrors iOS ChatViewModel:311, 448).
                                    if (answer.isNotBlank() && historyEnabled) {
                                        runCatching {
                                            messageDao.insert(
                                                LocalChatMessageEntity(
                                                    id = java.util.UUID.randomUUID().toString(),
                                                    threadId = sessionId,
                                                    role = "assistant",
                                                    content = answer,
                                                    createdAt = java.time.Instant.now().toString(),
                                                ),
                                            )
                                        }
                                    }
                                }
                                "done" -> {
                                    val total = json?.get("total_steps")?.takeIf { !it.isJsonNull }?.asInt ?: 0
                                    _progressEvents.emit(ChatStreamEvent.Done(total))
                                    return@flow
                                }
                                "error" -> {
                                    val errorMsg = json?.get("error")?.takeIf { !it.isJsonNull }?.asString
                                        ?: json?.get("message")?.takeIf { !it.isJsonNull }?.asString
                                        ?: data
                                    val reason = json?.get("reason")?.takeIf { !it.isJsonNull }?.asString
                                    // Surface the SSE `code` field as a fallback when `reason` is
                                    // missing or unrecognized — backends sometimes ship `code:
                                    // "quota_exceeded"` without a `reason`. Either signal must route
                                    // to the QuotaExhaustedAccountSheet (or guest paywall) so the
                                    // user sees the upgrade interstitial instead of a tiny banner.
                                    val code = json?.get("code")?.takeIf { !it.isJsonNull }?.asString
                                    val signal = reason ?: code
                                    // Mirror iOS quota-reason mapping → typed exceptions; VM picks the
                                    // user-facing string resource.
                                    val typed: Throwable = when (signal) {
                                        "daily_limit_reached" -> DailyLimitException(errorMsg)
                                        "overall_limit_reached" -> GuestLimitException(errorMsg)
                                        "user_not_found", "upgrade_required",
                                        "quota_exceeded", "feature_not_available" ->
                                            UpgradeRequiredException()
                                        else -> Exception(errorMsg)
                                    }
                                    emit(Result.failure(typed))
                                    return@flow
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: retrofit2.HttpException) {
            if (e.code() == 402 || e.code() == 429) {
                emit(Result.failure(UpgradeRequiredException()))
            } else {
                emit(Result.failure(e))
            }
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }

    override suspend fun loadHistory(): List<ChatThread> {
        val email = prefs.getUserEmail() ?: return emptyList()
        // Best-effort server pull so threads created on iOS / other devices show up.
        runCatching { syncThreadsFromApi() }
        return threadDao.getThreadsForUser(email).map { it.toDomain() }
    }

    override suspend fun loadHistoryPaginated(offset: Int, limit: Int): List<ChatThread> {
        val email = prefs.getUserEmail() ?: return emptyList()
        // Only sync from API on the first page so we don't repeat full pulls per page.
        if (offset == 0) runCatching { syncThreadsFromApi() }
        return threadDao.getThreadsForUserPaginated(email, limit, offset).map { it.toDomain() }
    }

    /**
     * Map the Room entity to the domain model. Parses ISO timestamps best-effort
     * (the column is a String for backwards compat) so the History screen can
     * group rows by relative date (Today / Yesterday / This Week / Earlier).
     */
    private fun com.destinyai.astrology.data.local.db.LocalChatThreadEntity.toDomain(): ChatThread {
        val updatedMs = parseIsoToMs(updatedAt)
        return ChatThread(
            id = id,
            title = title,
            isPinned = isPinned,
            updatedAtMs = updatedMs,
        )
    }

    private fun parseIsoToMs(s: String): Long {
        if (s.isBlank()) return 0L
        // Try common ISO-8601 shapes — fall back to 0 (Earlier bucket) on failure.
        val patterns = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd'T'HH:mm:ss.SSS",
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd",
        )
        for (p in patterns) {
            try {
                val sdf = java.text.SimpleDateFormat(p, java.util.Locale.US)
                sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
                return sdf.parse(s)?.time ?: continue
            } catch (_: Exception) {
                // try next
            }
        }
        return 0L
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

    override suspend fun setThreadPinned(threadId: String, pinned: Boolean) {
        threadDao.setPin(threadId, pinned)
        val email = prefs.getUserEmail() ?: return
        runCatching {
            api.updateChatThread(
                email,
                threadId,
                com.destinyai.astrology.data.remote.UpdateChatThreadRequest(isPinned = pinned),
            )
        }
    }

    override suspend fun submitRating(
        traceId: String?,
        sessionId: String?,
        userEmail: String?,
        query: String,
        responseText: String,
        rating: Int,
    ): Boolean {
        val email = userEmail ?: prefs.getUserEmail()
        return runCatching {
            api.submitFeedback(
                com.destinyai.astrology.data.remote.FeedbackRequest(
                    predictionId = traceId,
                    sessionId = sessionId,
                    userEmail = email,
                    query = query.ifBlank { "General question" },
                    predictionText = responseText.take(500),
                    rating = rating,
                ),
            )
            true
        }.getOrElse { false }
    }

    override suspend fun loadOlderMessages(
        threadId: String,
        beforeMs: Long,
        limit: Int,
    ): List<ChatMessage> {
        // Best-effort: read from local DAO older than `beforeMs`. Backend pagination
        // (parity with iOS WindowManager.fetchOlderPage) can layer on later — this
        // already lets the "Load earlier messages" button surface persisted history.
        return messageDao.getMessagesForThread(threadId)
            .filter { entity ->
                runCatching { java.time.Instant.parse(entity.createdAt).toEpochMilli() < beforeMs }
                    .getOrElse { true }
            }
            .takeLast(limit)
            .map { entity ->
                ChatMessage(
                    id = entity.id,
                    role = when (entity.role) {
                        "user" -> ChatMessage.Role.USER
                        "assistant" -> ChatMessage.Role.ASSISTANT
                        else -> ChatMessage.Role.SYSTEM
                    },
                    content = entity.content,
                    createdAtMs = runCatching { java.time.Instant.parse(entity.createdAt).toEpochMilli() }
                        .getOrElse { 0L },
                )
            }
    }

    override suspend fun syncThreadsFromApi() {
        val email = prefs.getUserEmail() ?: return
        val apiThreads = runCatching { api.listChatThreads(email) }.getOrElse { return }
        apiThreads.forEach { dto ->
            threadDao.insert(
                LocalChatThreadEntity(
                    id = dto.threadId,
                    ownerEmail = email,
                    title = dto.title,
                    createdAt = dto.createdAt,
                    updatedAt = dto.updatedAt,
                    isPinned = dto.isPinned,
                ),
            )
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
