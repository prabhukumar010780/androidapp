package com.destinyai.astrology.data.repository.impl

import android.util.Log
import com.destinyai.astrology.data.remote.AstroApiService
import com.destinyai.astrology.data.remote.CompatibilityRequestDto
import com.destinyai.astrology.data.repository.CompatibilityRepository
import com.destinyai.astrology.data.repository.SseEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import com.google.gson.JsonParser

private const val TAG = "CompatRepo"

@Singleton
class CompatibilityRepositoryImpl @Inject constructor(
    @Named("streaming") private val api: AstroApiService,
) : CompatibilityRepository {

    /**
     * SSE collector for /vedic/api/compatibility/analyze/stream.
     *
     * Parity with iOS CompatibilityService.analyzeWithProgress:
     * - Recognised events: step_start, step_done, final_json, error, done.
     * - step_done/done are no-ops that we log for diagnostics; iOS does the same.
     * - On EOF without seeing final_json, surface a typed Error so the UI does not
     *   silently fall through to the generic "Analysis failed" fallback in the VM.
     */
    override fun streamAnalysis(request: CompatibilityRequestDto): Flow<SseEvent> = flow {
        Log.d(TAG, "streamAnalysis: opening SSE — boy=${request.boy.name} girl=${request.girl.name} session=${request.sessionId}")
        val body = try {
            api.streamCompatibilityAnalysis(request)
        } catch (e: Exception) {
            Log.e(TAG, "streamAnalysis: HTTP open failed: ${e.message}", e)
            emit(SseEvent.Error(e.message ?: "Network error opening compatibility stream", "network_error"))
            return@flow
        }

        var sawFinal = false
        var lineCount = 0
        try {
            body.byteStream().bufferedReader().use { reader ->
                var line: String?
                var currentEvent = ""
                while (reader.readLine().also { line = it } != null) {
                    val raw = line ?: continue
                    lineCount++
                    when {
                        raw.startsWith("event: ") -> {
                            currentEvent = raw.removePrefix("event: ").trim()
                            Log.d(TAG, "SSE event=$currentEvent")
                        }
                        raw.startsWith("data: ") -> {
                            val data = raw.removePrefix("data: ").trim()
                            when (currentEvent) {
                                "step_start" -> {
                                    val stepName = try {
                                        JsonParser.parseString(data).asJsonObject.get("step")?.asString ?: data
                                    } catch (_: Exception) { data }
                                    Log.d(TAG, "SSE step_start step=$stepName")
                                    emit(SseEvent.Step(stepName))
                                }
                                "step_done" -> {
                                    // No-op — iOS only acts on step_start; keep for log visibility.
                                    Log.d(TAG, "SSE step_done data=${data.take(120)}")
                                }
                                "thought" -> {
                                    // No-op — Android UI doesn't surface LLM token stream yet.
                                }
                                "done" -> {
                                    Log.d(TAG, "SSE done data=$data — ending flow")
                                }
                                "final_json" -> {
                                    sawFinal = true
                                    Log.d(TAG, "SSE final_json received (length=${data.length})")
                                    emit(SseEvent.FinalJson(data))
                                    return@flow
                                }
                                "error" -> {
                                    // Parity with chat SSE error parsing — extract `reason` and `code`
                                    // so VM can route to QuotaExhaustedDialog instead of a red banner.
                                    var msg = data
                                    var reason: String? = null
                                    try {
                                        val obj = JsonParser.parseString(data).asJsonObject
                                        msg = obj.get("message")?.asString
                                            ?: obj.get("error")?.asString
                                            ?: data
                                        reason = obj.get("reason")?.takeIf { !it.isJsonNull }?.asString
                                            ?: obj.get("code")?.takeIf { !it.isJsonNull }?.asString
                                    } catch (_: Exception) {}
                                    Log.w(TAG, "SSE error msg=$msg reason=$reason")
                                    emit(SseEvent.Error(msg, reason))
                                    return@flow
                                }
                                else -> {
                                    if (currentEvent.isNotEmpty()) {
                                        Log.d(TAG, "SSE unhandled event=$currentEvent data=${data.take(120)}")
                                    }
                                }
                            }
                        }
                    }
                }
                Log.d(TAG, "streamAnalysis: reader EOF after $lineCount lines, sawFinal=$sawFinal")
            }
        } catch (e: Exception) {
            Log.e(TAG, "streamAnalysis: read failed after $lineCount lines: ${e.message}", e)
            emit(SseEvent.Error(e.message ?: "Stream read failed", "stream_read_failed"))
            return@flow
        }

        if (!sawFinal) {
            // Stream ended cleanly without final_json — surface as a typed error so the VM
            // doesn't fall through to the generic "Analysis failed" fallback.
            Log.w(TAG, "streamAnalysis: stream ended without final_json (read $lineCount lines)")
            emit(SseEvent.Error("Connection ended before analysis completed", "stream_incomplete"))
        }
    }.flowOn(Dispatchers.IO)
    // ^ flowOn(IO) is REQUIRED — bufferedReader.readLine() blocks on SocketInputStream.read()
    // and must not run on the main thread (otherwise NetworkOnMainThreadException). The flow
    // is otherwise built and collected on whichever dispatcher the caller uses (typically
    // viewModelScope.launch, which inherits Main from the main loop). Without flowOn(IO)
    // the very first readLine() throws and "Analysis failed" surfaces in the UI in <100ms.
}
