package com.destinyai.astrology.data.repository

import com.destinyai.astrology.data.remote.CompatibilityRequestDto
import com.destinyai.astrology.domain.model.CompatibilityResult
import kotlinx.coroutines.flow.Flow

sealed class SseEvent {
    data class Step(val stepName: String) : SseEvent()
    data class FinalJson(val json: String) : SseEvent()
    data class Error(val message: String) : SseEvent()
}

interface CompatibilityRepository {
    fun streamAnalysis(request: CompatibilityRequestDto): Flow<SseEvent>
}
