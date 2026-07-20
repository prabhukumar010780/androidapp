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
import com.destinyai.astrology.ui.chat.BackpressureException
import com.destinyai.astrology.ui.chat.GuestLimitException
import com.destinyai.astrology.ui.chat.UpgradeRequiredException
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
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
    private val profileContextManager: com.destinyai.astrology.services.ProfileContextManager,
) : ChatRepository {

    private val _progressEvents = MutableSharedFlow<ChatStreamEvent>(extraBufferCapacity = 32)
    override val progressEvents: SharedFlow<ChatStreamEvent> = _progressEvents.asSharedFlow()

    // iOS parity (ChatViewModel.capPersistedContent / ChatHistorySyncService): cap
    // persisted message bodies at 64KB (UTF-8) to protect the markdown renderer from
    // a runaway/oversized backend response. Also strips the trailing
    // "FOLLOW_UP_QUESTIONS:" block — the follow-ups render as tappable chips from the
    // structured field, so keeping the raw block would double them on thread reopen
    // (iOS parity: CompatibilityResultSheets.displayContent).
    private fun capPersistedContent(s: String): String {
        val marker = "\nFOLLOW_UP_QUESTIONS:"
        val idx = s.indexOf(marker, ignoreCase = true)
        val stripped = if (idx >= 0) s.substring(0, idx).trimEnd() else s
        val maxBytes = 64 * 1024
        val bytes = stripped.toByteArray(Charsets.UTF_8)
        if (bytes.size <= maxBytes) return stripped
        // Truncate on a UTF-8 char boundary.
        return String(bytes.copyOf(maxBytes), Charsets.UTF_8)
    }

    override suspend fun sendMessage(sessionId: String, text: String, idempotencyKey: String?): Flow<Result<String>> = flow {
        val email = prefs.getUserEmail() ?: run {
            emit(Result.failure(IllegalStateException("No user email")))
            return@flow
        }
        // iOS parity (ChatViewModel.swift:565-582 + ProfileContextManager.swift:60-77):
        // chat predictions use the **active profile's** birth data — owner when
        // self is active, partner birth data otherwise. Falling back to the
        // owner's prefs.getBirthProfile() ignored the Switch Profile selection.
        val birthProfile = profileContextManager.activeBirthData() ?: run {
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
                val nowIso = java.time.Instant.now().toString()
                // iOS parity: create the thread row ONCE (title from the first message,
                // pin/createdAt preserved), then only bump updated_at on later sends.
                // A blind REPLACE here wiped pin/title/createdAt/primaryArea every send (D2).
                threadDao.insertIfAbsent(
                    LocalChatThreadEntity(
                        id = sessionId,
                        ownerEmail = email,
                        title = text.take(60),
                        createdAt = nowIso,
                        updatedAt = nowIso,
                        isPinned = false,
                        // iOS parity (LocalChatThread.profileId): scope to the active
                        // profile so Switch Profile isolates history. Null when self is active.
                        profileId = prefs.getActiveProfileId()?.takeIf { it.isNotBlank() && it != email },
                    ),
                )
                threadDao.touch(sessionId, nowIso)
                messageDao.insert(
                    LocalChatMessageEntity(
                        id = java.util.UUID.randomUUID().toString(),
                        threadId = sessionId,
                        role = "user",
                        content = capPersistedContent(text),
                        createdAt = java.time.Instant.now().toString(),
                    ),
                )
            }
        }
        try {
            // iOS parity (StreamingPredictionService.swift:52-71): per-send idempotency
            // key so a post-completion retry replays the cached answer instead of
            // re-charging quota. The VM passes the same key to the sync fallback.
            val effectiveKey = idempotencyKey ?: java.util.UUID.randomUUID().toString()
            val body = streamingApi.streamPredict(
                effectiveKey,
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
                    profileId = prefs.getActiveProfileId()?.takeIf { it.isNotBlank() && it != email },
                )
            )
            body.byteStream().bufferedReader().use { reader ->
                var line: String?
                var currentEvent = ""
                // iOS parity: when per-token frames streamed, the terminal `answer`
                // event is metadata-only — don't re-emit the whole text as a chunk.
                var sawToken = false
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
                                "token" -> {
                                    // iOS parity (StreamingPredictionService .token event): emit each
                                    // token chunk incrementally so the answer grows token-by-token.
                                    // The terminal `answer` event is used only for reconciliation +
                                    // metadata (it must NOT re-emit the whole text as a chunk).
                                    val chunk = json?.get("content")?.takeIf { !it.isJsonNull }?.asString
                                        ?: json?.get("token")?.takeIf { !it.isJsonNull }?.asString
                                        ?: ""
                                    if (chunk.isNotEmpty()) {
                                        sawToken = true
                                        emit(Result.success(chunk))
                                    }
                                }
                                "backpressure" -> {
                                    // iOS parity (StreamingPredictionService.swift:292-297): server is
                                    // shedding load. Signal the VM to transparently replay via the
                                    // non-streaming /predict endpoint.
                                    val retryAfter = json?.get("retry_after_seconds")?.takeIf { !it.isJsonNull }
                                        ?.let { v -> runCatching { v.asInt }.getOrNull() } ?: 0
                                    emit(Result.failure(BackpressureException(retryAfter)))
                                    return@flow
                                }
                                "final_answer" -> json?.let {
                                    val content = it.get("content")?.takeIf { e -> !e.isJsonNull }?.asString ?: ""
                                    _progressEvents.emit(ChatStreamEvent.FinalAnswer(content))
                                }
                                "answer" -> {
                                    val answer = json?.get("answer")?.takeIf { !it.isJsonNull }?.asString ?: data
                                    // If per-token frames already streamed the text, don't re-emit
                                    // the whole answer (it would duplicate/replace the reveal). Only
                                    // emit here when no tokens arrived (single-blob backends).
                                    if (answer.isNotBlank() && !sawToken) emit(Result.success(answer))
                                    // iOS treats terminal answer event as the structured PredictionResponse:
                                    // surface follow_up_suggestions so the FollowUpSuggestionsView can render them.
                                    val suggestionsArr = json?.get("follow_up_suggestions")?.takeIf { !it.isJsonNull }?.asJsonArray
                                    val followUps = suggestionsArr?.mapNotNull { e -> runCatching { e.asString }.getOrNull() } ?: emptyList()
                                    if (followUps.isNotEmpty()) {
                                        _progressEvents.emit(ChatStreamEvent.FollowUpSuggestions(followUps))
                                    }
                                    // Mirrors iOS PredictionResponse → LocalChatMessage hydration: tool_calls /
                                    // sources / advice / timing / execution_time_ms / trace_id / area surfaced via
                                    // a Metadata event AND persisted on the assistant row so a reopened thread
                                    // keeps its depth layers, chips, exec pill, and rating binding.
                                    val toolsArr = json?.get("tool_calls")?.takeIf { !it.isJsonNull }?.asJsonArray
                                    val tools = toolsArr?.mapNotNull { e -> runCatching { e.asString }.getOrNull() } ?: emptyList()
                                    val sourcesArr = json?.get("sources")?.takeIf { !it.isJsonNull }?.asJsonArray
                                    val sources = sourcesArr?.mapNotNull { e -> runCatching { e.asString }.getOrNull() } ?: emptyList()
                                    val advice = json?.get("advice")?.takeIf { !it.isJsonNull }?.asString
                                    // `timing` is a structured PredictionTiming OBJECT on the backend
                                    // (predict.py PredictionResponse.timing), not a string. Calling
                                    // .asString on a JsonObject throws UnsupportedOperationException,
                                    // which aborted the stream at the terminal event ("JsonObject" error
                                    // + spurious "Chat was interrupted"). Accept either shape: a plain
                                    // string, or serialize the object back to JSON for the metadata row.
                                    val timing = json?.get("timing")?.takeIf { !it.isJsonNull }?.let { el ->
                                        runCatching { if (el.isJsonPrimitive) el.asString else el.toString() }.getOrNull()
                                    }
                                    val execMs = json?.get("execution_time_ms")?.takeIf { !it.isJsonNull }
                                        ?.let { runCatching { it.asDouble }.getOrNull() } ?: 0.0
                                    val traceId = json?.get("trace_id")?.takeIf { !it.isJsonNull }?.asString
                                        ?: json?.get("prediction_id")?.takeIf { !it.isJsonNull }?.asString
                                    val area = json?.get("life_area")?.takeIf { !it.isJsonNull }?.asString
                                        ?: json?.get("area")?.takeIf { !it.isJsonNull }?.asString
                                    if (tools.isNotEmpty() || sources.isNotEmpty() || !advice.isNullOrBlank() ||
                                        !timing.isNullOrBlank() || execMs > 0.0 || !traceId.isNullOrBlank()) {
                                        _progressEvents.emit(
                                            ChatStreamEvent.Metadata(
                                                toolCalls = tools,
                                                sources = sources,
                                                advice = advice,
                                                timing = timing,
                                                executionTimeMs = execMs,
                                                traceId = traceId,
                                            ),
                                        )
                                    }
                                    // Persist assistant message + metadata locally for history (iOS parity).
                                    // Gated on isHistoryEnabled (mirrors iOS ChatViewModel:311, 448).
                                    if (answer.isNotBlank() && historyEnabled) {
                                        val gson = com.google.gson.Gson()
                                        runCatching {
                                            messageDao.insert(
                                                LocalChatMessageEntity(
                                                    id = java.util.UUID.randomUUID().toString(),
                                                    threadId = sessionId,
                                                    role = "assistant",
                                                    content = capPersistedContent(answer),
                                                    createdAt = java.time.Instant.now().toString(),
                                                    followUps = followUps.takeIf { it.isNotEmpty() }?.let { gson.toJson(it) },
                                                    advice = advice,
                                                    timing = timing,
                                                    toolCalls = tools.takeIf { it.isNotEmpty() }?.let { gson.toJson(it) },
                                                    sources = sources.takeIf { it.isNotEmpty() }?.let { gson.toJson(it) },
                                                    executionTimeMs = execMs.takeIf { it > 0.0 },
                                                    traceId = traceId,
                                                    area = area,
                                                ),
                                            )
                                            // iOS parity (LocalChatThread.primaryArea): tag the thread's area
                                            // for the History row icon.
                                            if (!area.isNullOrBlank()) {
                                                runCatching { threadDao.setPrimaryArea(sessionId, area) }
                                            }
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
            android.util.Log.e("ChatRepo", "HttpException ${e.code()}: ${e.message()}", e)
            if (e.code() == 402 || e.code() == 429) {
                emit(Result.failure(UpgradeRequiredException()))
            } else {
                emit(Result.failure(e))
            }
        } catch (e: Exception) {
            android.util.Log.e("ChatRepo", "stream failed: ${e.javaClass.simpleName}: ${e.message}", e)
            emit(Result.failure(e))
        }
    }.flowOn(Dispatchers.IO)
    // Move upstream blocking work (SSE BufferedReader.readLine, Room insert, OkHttp socket
    // reads) OFF the Main thread. ChatViewModel collects this flow inside
    // viewModelScope.launch which defaults to Main; without flowOn(IO) the SSE loop runs
    // on Main and trips StrictMode's NetworkOnMainThreadException, killing the stream
    // before any chunk reaches the UI ("Unable to reach the prediction service").

    override suspend fun sendMessageSync(
        sessionId: String,
        text: String,
        idempotencyKey: String?,
    ): Result<String> = runCatching {
        val email = prefs.getUserEmail() ?: throw IllegalStateException("No user email")
        val birthProfile = profileContextManager.activeBirthData()
            ?: throw IllegalStateException("No birth profile")
        val ayanamsa = runCatching { prefs.getAyanamsa() }.getOrDefault("lahiri")
        val houseSystem = runCatching { prefs.getHouseSystem() }.getOrDefault("whole_sign")
        val responseStyle = runCatching { prefs.getResponseStyle() }.getOrNull()
        val responseLength = runCatching { prefs.getResponseLength() }.getOrNull()
        val language = runCatching { prefs.getSelectedLanguage() }.getOrDefault("en")
        val historyEnabled = runCatching { prefs.isHistoryEnabled() }.getOrDefault(true)
        // Persist the thread + user message up-front, exactly like the streaming
        // sendMessage path. Previously sendMessageSync only saved the ASSISTANT
        // reply with no thread row or user message, so loadHistory() surfaced
        // nothing → "chat history not saved" on the non-streaming path.
        if (historyEnabled) {
            runCatching {
                val nowIso = java.time.Instant.now().toString()
                threadDao.insertIfAbsent(
                    LocalChatThreadEntity(
                        id = sessionId,
                        ownerEmail = email,
                        title = text.take(60),
                        createdAt = nowIso,
                        updatedAt = nowIso,
                        isPinned = false,
                        profileId = prefs.getActiveProfileId()?.takeIf { it.isNotBlank() && it != email },
                    ),
                )
                threadDao.touch(sessionId, nowIso)
                messageDao.insert(
                    LocalChatMessageEntity(
                        id = java.util.UUID.randomUUID().toString(),
                        threadId = sessionId,
                        role = "user",
                        content = capPersistedContent(text),
                        createdAt = nowIso,
                    ),
                )
            }
        }
        val resp = api.predict(
            idempotencyKey,
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
                profileId = prefs.getActiveProfileId()?.takeIf { it.isNotBlank() && it != email },
            ),
        )
        val answer = resp.text
        if (answer.isBlank()) throw IllegalStateException("Empty prediction")
        if (historyEnabled) {
            runCatching {
                messageDao.insert(
                    LocalChatMessageEntity(
                        id = java.util.UUID.randomUUID().toString(),
                        threadId = sessionId,
                        role = "assistant",
                        content = capPersistedContent(answer),
                        createdAt = java.time.Instant.now().toString(),
                        traceId = resp.predictionId,
                    ),
                )
            }
        }
        // Emit follow-up chips on the non-streaming path too (parity with the
        // streaming terminal `answer` event) so suggestions appear regardless of
        // which path served the response.
        resp.followUpSuggestions?.takeIf { it.isNotEmpty() }?.let {
            _progressEvents.emit(ChatStreamEvent.FollowUpSuggestions(it))
        }
        answer
    }

    override suspend fun loadHistory(): List<ChatThread> {
        val email = prefs.getUserEmail() ?: return emptyList()
        // Best-effort server pull so threads created on iOS / other devices show up.
        runCatching { syncThreadsFromApi() }
        // iOS parity (ChatViewModel.loadHistory filtered by activeProfileId): show only
        // the active profile's threads so Switch Profile isolates history.
        val activeProfile = prefs.getActiveProfileId()?.takeIf { it.isNotBlank() && it != email }
        return threadDao.getThreadsForProfile(email, activeProfile)
            .filterNot { isCompatThread(it) }
            .map { it.toDomainHydrated() }
    }

    override suspend fun loadHistoryPaginated(offset: Int, limit: Int): List<ChatThread> {
        val email = prefs.getUserEmail() ?: return emptyList()
        // Only sync from API on the first page so we don't repeat full pulls per page.
        if (offset == 0) runCatching { syncThreadsFromApi() }
        // Profile-scoped in-memory windowing over the active profile's threads.
        val activeProfile = prefs.getActiveProfileId()?.takeIf { it.isNotBlank() && it != email }
        return threadDao.getThreadsForProfile(email, activeProfile)
            .filterNot { isCompatThread(it) }
            .drop(offset).take(limit).map { it.toDomainHydrated() }
    }

    /**
     * iOS parity (HistoryViewModel.fetchChatItemsPage:158-179): exclude compatibility-
     * session threads from the unified chat feed so a match appears only once (as a
     * match row), not duplicated as a raw chat thread. Matches iOS's id-prefix +
     * primaryArea==compatibility exclusion (title starting 'match:' is still a compat row).
     */
    private fun isCompatThread(t: LocalChatThreadEntity): Boolean {
        val id = t.id.lowercase()
        return id.startsWith("compat_sess_") || id.startsWith("compat_") || id.startsWith("conv_") ||
            t.primaryArea?.equals("compatibility", ignoreCase = true) == true
    }

    override suspend fun loadThreadFollowUps(threadId: String): List<String> {
        val raw = runCatching { messageDao.latestAssistantFollowUps(threadId) }.getOrNull() ?: return emptyList()
        return runCatching {
            com.google.gson.Gson().fromJson(raw, Array<String>::class.java).toList()
        }.getOrDefault(emptyList())
    }

    /**
     * iOS parity (LocalChatThread): the History sheet shows a one-line preview drawn
     * from the latest message in the thread, plus a small message-count badge.
     * Room joins would be cleaner, but the entity-to-domain mapping happens row-by-row
     * and the page size is bounded (20), so two single-row queries per thread is fine.
     */
    private suspend fun com.destinyai.astrology.data.local.db.LocalChatThreadEntity.toDomainHydrated(): ChatThread {
        val updatedMs = parseIsoToMs(updatedAt)
        val count = runCatching { messageDao.countMessagesForThread(id) }.getOrDefault(0)
        val preview = runCatching { messageDao.latestMessageContent(id) }
            .getOrNull()
            ?.replace('\n', ' ')
            ?.trim()
            ?.take(120)
            .orEmpty()
        return ChatThread(
            id = id,
            title = title,
            preview = preview,
            isPinned = isPinned,
            updatedAtMs = updatedMs,
            messageCount = count,
            primaryArea = primaryArea,
        )
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
        // iOS parity (ChatHistorySyncService.syncFromServer): pull server messages
        // first so a thread created on another device / after reinstall opens fully
        // populated instead of empty. Best-effort — falls through to local rows.
        runCatching { syncThreadMessagesFromApi(threadId) }
        return messageDao.getMessagesForThread(threadId).map { it.toDomain() }
    }

    /** Map a persisted message row to the domain model, hydrating the assistant
     *  metadata (follow-ups, advice, timing, tools, sources, exec, trace, rating)
     *  so a reopened thread keeps its rich rendering (iOS LocalChatMessage parity). */
    private fun LocalChatMessageEntity.toDomain(): ChatMessage {
        val gson = com.google.gson.Gson()
        fun parseList(s: String?): List<String> = s?.let {
            runCatching { gson.fromJson(it, Array<String>::class.java).toList() }.getOrNull()
        } ?: emptyList()
        return ChatMessage(
            id = id,
            role = when (role) {
                "user" -> ChatMessage.Role.USER
                "assistant" -> ChatMessage.Role.ASSISTANT
                else -> ChatMessage.Role.SYSTEM
            },
            content = content,
            createdAtMs = runCatching { java.time.Instant.parse(createdAt).toEpochMilli() }.getOrElse { 0L },
            toolCalls = parseList(toolCalls),
            sources = parseList(sources),
            advice = advice,
            timing = timing,
            executionTimeMs = executionTimeMs ?: 0.0,
            traceId = traceId,
            rating = rating ?: 0,
        )
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

    override suspend fun persistRating(messageId: String, rating: Int) {
        runCatching { messageDao.updateRating(messageId, rating) }
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
            .map { it.toDomain() }
    }

    override suspend fun syncThreadsFromApi() {
        val email = prefs.getUserEmail() ?: return
        // getOrNull (not getOrElse{return}) so we can DISTINGUISH a failed fetch from
        // an empty one: a parse/network failure must NOT trigger the destructive prune
        // below (that would wipe all local history on any transient error).
        val response = runCatching { api.listChatThreads(email) }.getOrNull() ?: return
        val apiThreads = response.threads
        apiThreads.forEach { dto ->
            threadDao.insert(
                LocalChatThreadEntity(
                    id = dto.id,
                    ownerEmail = email,
                    title = dto.title.orEmpty(),
                    createdAt = dto.createdAt.orEmpty(),
                    updatedAt = dto.updatedAt.orEmpty(),
                    isPinned = dto.isPinned,
                ),
            )
        }
        // Server is authoritative — drop local threads it no longer lists. Reached
        // only after a SUCCESSFUL parse (the null-guard above returns on failure), so
        // an empty server list genuinely means the account has no threads.
        val keepIds = apiThreads.map { it.id }
        runCatching {
            if (keepIds.isEmpty()) threadDao.deleteAllForUser(email)
            else threadDao.pruneNotIn(email, keepIds)
        }
    }

    suspend fun syncThreadMessagesFromApi(threadId: String) {
        val email = prefs.getUserEmail() ?: return
        val detail = runCatching { api.getChatThread(email, threadId) }.getOrNull() ?: return
        val messages = detail.messages
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
