package com.destinyai.astrology.ui.charts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.destinyai.astrology.data.local.prefs.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChartsUiState(
    val hasData: Boolean = false,
    val dateOfBirth: String = "",
    val timeOfBirth: String = "",
    val cityOfBirth: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val chartStyle: String = "north_indian",
    val timeUnknown: Boolean = false,
)

@HiltViewModel
class ChartsViewModel @Inject constructor(
    private val prefs: UserPreferences,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChartsUiState())
    val uiState: StateFlow<ChartsUiState> = _uiState

    fun loadChartData() {
        viewModelScope.launch {
            val profile = prefs.getBirthProfile()
            val chartStyle = prefs.getChartStyle()
            if (profile == null) {
                _uiState.update { it.copy(hasData = false) }
                return@launch
            }
            _uiState.update {
                it.copy(
                    hasData = true,
                    dateOfBirth = profile.dateOfBirth,
                    timeOfBirth = profile.timeOfBirth,
                    cityOfBirth = profile.cityOfBirth,
                    latitude = profile.latitude,
                    longitude = profile.longitude,
                    timeUnknown = profile.birthTimeUnknown,
                    chartStyle = chartStyle,
                )
            }
        }
    }
}
