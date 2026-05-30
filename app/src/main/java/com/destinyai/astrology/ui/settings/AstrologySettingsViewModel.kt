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
    val isLoading: Boolean = false,
    val isSaved: Boolean = false,
    val error: String? = null,
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

    fun setAyanamsa(value: String) = _uiState.update { it.copy(ayanamsa = value, isSaved = false) }
    fun setHouseSystem(value: String) = _uiState.update { it.copy(houseSystem = value, isSaved = false) }
    fun setChartStyle(value: String) = _uiState.update { it.copy(chartStyle = value, isSaved = false) }

    fun save() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val s = _uiState.value
                prefs.saveAyanamsa(s.ayanamsa)
                prefs.saveHouseSystem(s.houseSystem)
                prefs.setChartStyle(s.chartStyle)
                _uiState.update { it.copy(isLoading = false, isSaved = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "Save failed") }
            }
        }
    }
}
