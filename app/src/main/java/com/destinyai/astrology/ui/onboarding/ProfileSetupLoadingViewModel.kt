package com.destinyai.astrology.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.destinyai.astrology.data.local.prefs.UserPreferences
import com.destinyai.astrology.data.repository.HomeRepository
import com.destinyai.astrology.services.HapticManager
import com.destinyai.astrology.services.SoundManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Mirrors iOS ProfileSetupLoadingView.performSetup() — prefetches the user's
 * full chart data and today's prediction so the Home screen lands with a warm
 * cache instead of a cold spinner.
 *
 * Phases match iOS exactly:
 *   1. CALCULATING_CHART  -> fetch chart (UserChartService.fetchFullChartData)
 *   2. ANALYZING_PLANETS  -> short pacing pause (matches iOS 800ms)
 *   3. GENERATING_INSIGHTS-> fetch today's prediction (PredictionService.getTodaysPrediction)
 *   4. COMPLETE           -> trigger onComplete()
 *
 * Failures are logged (matches iOS print) but do NOT block navigation.
 */
@HiltViewModel
class ProfileSetupLoadingViewModel @Inject constructor(
    private val homeRepository: HomeRepository,
    private val prefs: UserPreferences,
    private val hapticManager: HapticManager,
    private val soundManager: SoundManager,
) : ViewModel() {

    enum class Phase { CALCULATING_CHART, ANALYZING_PLANETS, GENERATING_INSIGHTS, COMPLETE }

    data class UiState(
        val phaseIndex: Int = 0,
        val progress: Float = 0f,
        val isComplete: Boolean = false,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun startSetup(onComplete: () -> Unit) {
        viewModelScope.launch {
            // Bio-Sync heartbeat haptic loop during fetch — mirrors iOS performSetup()
            // line 107-114 (heartbeat ticks ~1.2s during processing).
            val heartbeatJob: Job = launch {
                while (isActive) {
                    hapticManager.playHeartbeat()
                    delay(1200)
                }
            }

            // Phase 1 — calculating chart (target progress 0.3)
            _state.value = UiState(phaseIndex = 0, progress = 0.3f)
            val email = prefs.getUserEmail()
            val birth = prefs.getBirthProfile()

            if (email != null && birth != null) {
                // Run chart prefetch in parallel with phase pacing — matches iOS
                // which awaits chart, then spaces the next phase by 800ms.
                val chartJob = async(Dispatchers.IO) {
                    runCatching { homeRepository.getRichHomeData(email, birth) }
                        .onFailure { android.util.Log.w("ProfileSetup", "Chart fetch failed: ${it.message}") }
                }
                chartJob.await()
            } else {
                android.util.Log.w("ProfileSetup", "Missing email or birth profile — skipping prefetch")
            }

            // Phase 2 — analyzing planets (target 0.6, 800ms pause matches iOS)
            _state.value = _state.value.copy(phaseIndex = 1, progress = 0.6f)
            delay(800)

            // Phase 3 — generating insights (target 0.9), fetch today's prediction
            _state.value = _state.value.copy(phaseIndex = 2, progress = 0.9f)
            runCatching {
                withContext(Dispatchers.IO) { homeRepository.getDailyInsight() }
            }.onFailure { android.util.Log.w("ProfileSetup", "Prediction fetch failed: ${it.message}") }

            // Stop heartbeat before completion — mirrors iOS line 153 heartbeatTask.cancel()
            heartbeatJob.cancel()

            // Phase 4 — complete; success haptic + sound parity with iOS line 157-158
            _state.value = _state.value.copy(phaseIndex = 3, progress = 1.0f, isComplete = true)
            hapticManager.playSuccess()
            soundManager.playSuccess()
            delay(1000) // matches iOS 1s settle
            onComplete()
        }
    }
}
