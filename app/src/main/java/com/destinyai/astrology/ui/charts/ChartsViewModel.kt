package com.destinyai.astrology.ui.charts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.destinyai.astrology.data.local.prefs.UserPreferences
import com.destinyai.astrology.data.remote.AstroApiService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChartsUiState(
    val hasData: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val dateOfBirth: String = "",
    val timeOfBirth: String = "",
    val cityOfBirth: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val chartStyle: String = "north_indian",
    val timeUnknown: Boolean = false,
    val chartApiData: ChartApiResponse? = null,
    val ascendantSign: String? = null,
)

@HiltViewModel
class ChartsViewModel @Inject constructor(
    private val prefs: UserPreferences,
    private val api: AstroApiService,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChartsUiState())
    val uiState: StateFlow<ChartsUiState> = _uiState

    fun loadChartData() {
        viewModelScope.launch {
            val profile = prefs.getBirthProfile()
            val chartStyle = prefs.getChartStyle()
            if (profile == null) {
                _uiState.update { it.copy(hasData = false, isLoading = false) }
                return@launch
            }
            _uiState.update {
                it.copy(
                    hasData = true,
                    isLoading = true,
                    errorMessage = null,
                    dateOfBirth = profile.dateOfBirth,
                    timeOfBirth = profile.timeOfBirth,
                    cityOfBirth = profile.cityOfBirth,
                    latitude = profile.latitude,
                    longitude = profile.longitude,
                    timeUnknown = profile.birthTimeUnknown,
                    chartStyle = chartStyle,
                )
            }
            try {
                val response = api.getChartData(
                    ChartDataRequest(
                        dob = profile.dateOfBirth,
                        time = profile.timeOfBirth,
                        latitude = profile.latitude,
                        longitude = profile.longitude,
                    )
                )
                val signNum = response.houses["1"]?.signNum ?: 1
                val ascIndex = (signNum - 1).coerceIn(0, 11)
                val ascSign = ChartConstants.orderedSigns[ascIndex]
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        chartApiData = response,
                        ascendantSign = ascSign,
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = e.message ?: "Failed to load chart")
                }
            }
        }
    }

    fun setChartStyle(style: String) {
        viewModelScope.launch {
            prefs.setChartStyle(style)
            _uiState.update { it.copy(chartStyle = style) }
        }
    }

    fun retry() {
        _uiState.update { it.copy(errorMessage = null) }
        loadChartData()
    }
}
