package com.destinyai.astrology.ui.charts

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.destinyai.astrology.BuildConfig
import com.destinyai.astrology.data.local.prefs.UserPreferences
import com.destinyai.astrology.data.remote.AstroApiService
import com.destinyai.astrology.data.remote.BirthProfileDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class ChartsUiState(
    val hasData: Boolean = false,
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val dateOfBirth: String = "",
    val timeOfBirth: String = "",
    val cityOfBirth: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val chartStyle: String = "north",
    val timeUnknown: Boolean = false,
    val chartApiData: ChartApiResponse? = null,
    val ascendantSign: String? = null,
    val dashaResponse: DashaResponse? = null,
    val transitResponse: TransitResponse? = null,
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
            // Parity with ChatRepositoryImpl — pass user-selected ayanamsa / house_system so
            // backend calculations reflect the active profile's preferences instead of
            // silently defaulting to lahiri / whole_sign.
            val ayanamsa = runCatching { prefs.getAyanamsa() }.getOrDefault("lahiri")
            val houseSystem = runCatching { prefs.getHouseSystem() }.getOrDefault("whole_sign")
            // iOS parity (PlanetaryPositionsSheet.swift:326): log chart load for active profile.
            Log.d("PlanetaryPositionsSheet", "Loading chart for: ${profile.cityOfBirth}")
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
                        birthData = BirthData(
                            dob = profile.dateOfBirth,
                            time = profile.timeOfBirth,
                            latitude = profile.latitude,
                            longitude = profile.longitude,
                            ayanamsa = ayanamsa,
                            houseSystem = houseSystem,
                            cityOfBirth = profile.cityOfBirth,
                            birthTimeUnknown = profile.birthTimeUnknown,
                        ),
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
                // Mirrors iOS UserChartService.fetchDashaPeriods / fetchTransits — fire after main chart loads
                loadDashaAndTransits(profile, ayanamsa, houseSystem)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = e.message ?: "Failed to load chart")
                }
            }
        }
    }

    private fun loadDashaAndTransits(
        profile: BirthProfileDto,
        ayanamsa: String,
        houseSystem: String,
    ) {
        viewModelScope.launch {
            val year = LocalDate.now().year
            val authHeader = "Bearer ${BuildConfig.API_KEY}"
            val request = DashaTransitRequest(
                birthData = BirthData(
                    dob = profile.dateOfBirth,
                    time = profile.timeOfBirth,
                    latitude = profile.latitude,
                    longitude = profile.longitude,
                    ayanamsa = ayanamsa,
                    houseSystem = houseSystem,
                    cityOfBirth = profile.cityOfBirth,
                    birthTimeUnknown = profile.birthTimeUnknown,
                ),
                year = year,
            )
            try {
                val dasha = api.getDashaPeriods(authHeader, request)
                _uiState.update { it.copy(dashaResponse = dasha) }
            } catch (_: Exception) {
                // Non-fatal — chart still renders without dasha
            }
            try {
                val transits = api.getTransits(authHeader, request)
                _uiState.update { it.copy(transitResponse = transits) }
            } catch (_: Exception) {
                // Non-fatal — chart still renders without transits
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
