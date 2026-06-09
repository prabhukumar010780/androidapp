package com.destinyai.astrology.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.destinyai.astrology.data.local.prefs.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AstrologySettingsUiState(
    val ayanamsa: String = "lahiri",
    val houseSystem: String = "whole_sign",
    val chartStyle: String = "north",
)

@HiltViewModel
class AstrologySettingsViewModel @Inject constructor(
    private val prefs: UserPreferences,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AstrologySettingsUiState())
    val uiState: StateFlow<AstrologySettingsUiState> = _uiState.asStateFlow()

    fun load() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    ayanamsa = prefs.getAyanamsa(),
                    houseSystem = prefs.getHouseSystem(),
                    chartStyle = prefs.getChartStyle(),
                )
            }
        }
    }

    // iOS parity: AstrologySettingsSheet.swift uses @AppStorage which persists on every tap.
    // Android mirrors this by writing to DataStore inside each setter so users never lose
    // changes when they back out without pressing Save.
    fun setAyanamsa(value: String) {
        _uiState.update { it.copy(ayanamsa = value) }
        viewModelScope.launch { prefs.saveAyanamsa(value) }
    }

    fun setHouseSystem(value: String) {
        _uiState.update { it.copy(houseSystem = value) }
        viewModelScope.launch { prefs.saveHouseSystem(value) }
    }

    fun setChartStyle(value: String) {
        _uiState.update { it.copy(chartStyle = value) }
        viewModelScope.launch { prefs.setChartStyle(value) }
    }
}
