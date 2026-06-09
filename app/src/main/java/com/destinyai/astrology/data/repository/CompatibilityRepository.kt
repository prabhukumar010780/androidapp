package com.destinyai.astrology.data.repository

import com.destinyai.astrology.data.remote.CompatibilityRequestDto
import com.destinyai.astrology.domain.model.CompatibilityResult
import kotlinx.coroutines.flow.Flow

sealed class SseEvent {
    data class Step(val stepName: String) : SseEvent()
    data class FinalJson(val json: String) : SseEvent()
    /**
     * Error event from backend SSE stream. `reason` mirrors iOS quota mapping
     * ("daily_limit_reached", "overall_limit_reached", "quota_exceeded",
     * "feature_not_available", "fair_use_violation"); null = generic error.
     * Compat VM uses this to route to the QuotaExhaustedDialog instead of a banner.
     */
    data class Error(val message: String, val reason: String? = null) : SseEvent()
}

interface CompatibilityRepository {
    fun streamAnalysis(request: CompatibilityRequestDto): Flow<SseEvent>
}
