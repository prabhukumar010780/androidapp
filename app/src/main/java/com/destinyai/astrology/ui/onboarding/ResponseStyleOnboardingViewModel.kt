package com.destinyai.astrology.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.destinyai.astrology.data.local.prefs.UserPreferences
import com.destinyai.astrology.services.HapticManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Mirrors iOS ContentStyleManager.shared.setStyle(...) — persists the selected
 * response style to DataStore so the chat pipeline can read it back as
 * `response_style` on the next prediction request.
 */
@HiltViewModel
class ResponseStyleOnboardingViewModel @Inject constructor(
    private val prefs: UserPreferences,
    private val hapticManager: HapticManager,
) : ViewModel() {

    private val _selected = MutableStateFlow("guidance")
    val selected: StateFlow<String> = _selected

    fun loadCurrent() {
        viewModelScope.launch {
            _selected.value = prefs.getResponseStyle()
        }
    }

    fun select(style: String) {
        _selected.value = style
        // iOS parity (ResponseStyleOnboardingView.swift:133-137): light haptic on
        // every radio change to give tactile confirmation of the selection.
        hapticManager.light()
    }

    fun persistSelection() {
        // iOS parity (ResponseStyleOnboardingView.swift:100-108): premiumContinue
        // haptic fires on the commit press before the style is persisted.
        hapticManager.premiumContinue()
        viewModelScope.launch {
            prefs.setResponseStyle(_selected.value)
        }
    }
}
