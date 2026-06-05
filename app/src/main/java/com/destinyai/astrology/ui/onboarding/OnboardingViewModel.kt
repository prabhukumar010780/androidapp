package com.destinyai.astrology.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.destinyai.astrology.data.local.prefs.UserPreferences
import com.destinyai.astrology.services.HapticManager
import com.destinyai.astrology.services.SoundManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val prefs: UserPreferences,
    val haptic: HapticManager,
    val sound: SoundManager,
) : ViewModel() {

    private val _currentSlide = MutableStateFlow(0)
    val currentSlide: StateFlow<Int> = _currentSlide

    fun isLastSlide(index: Int): Boolean = index == OnboardingSlide.slides.size - 1

    fun nextSlide(onComplete: () -> Unit) {
        val current = _currentSlide.value
        if (isLastSlide(current)) {
            viewModelScope.launch {
                complete()
                onComplete()
            }
        } else {
            _currentSlide.value = current + 1
        }
    }

    suspend fun complete() {
        prefs.setSeenOnboarding(true)
    }
}
