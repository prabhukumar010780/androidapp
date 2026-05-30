package com.destinyai.astrology.data.repository.impl

import com.destinyai.astrology.data.remote.AstroApiService
import com.destinyai.astrology.data.remote.CompatibilityRequestDto
import com.destinyai.astrology.data.repository.CompatibilityRepository
import com.destinyai.astrology.data.repository.SseEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton
import com.google.gson.JsonParser

@Singleton
class CompatibilityRepositoryImpl @Inject constructor(
    private val api: AstroApiService,
) : CompatibilityRepository {

    override fun streamAnalysis(request: CompatibilityRequestDto): Flow<SseEvent> = flow {
        val body = api.streamCompatibilityAnalysis(request)
        body.byteStream().bufferedReader().use { reader ->
            var line: String?
            var currentEvent = ""
            while (reader.readLine().also { line = it } != null) {
                val raw = line ?: continue
                when {
                    raw.startsWith("event: ") -> currentEvent = raw.removePrefix("event: ").trim()
                    raw.startsWith("data: ") -> {
                        val data = raw.removePrefix("data: ").trim()
                        when (currentEvent) {
                            "step_start" -> {
                                val stepName = try {
                                    JsonParser.parseString(data).asJsonObject.get("step")?.asString ?: data
                                } catch (_: Exception) { data }
                                emit(SseEvent.Step(stepName))
                            }
                            "final_json" -> {
                                emit(SseEvent.FinalJson(data))
                                return@flow
                            }
                            "error" -> {
                                val msg = try {
                                    JsonParser.parseString(data).asJsonObject.get("message")?.asString ?: data
                                } catch (_: Exception) { data }
                                emit(SseEvent.Error(msg))
                                return@flow
                            }
                        }
                    }
                }
            }
        }
    }
}
