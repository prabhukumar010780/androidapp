package com.destinyai.astrology.ui.compatibility

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.destinyai.astrology.data.local.prefs.UserPreferences
import com.destinyai.astrology.data.remote.AstroApiService
import com.destinyai.astrology.data.remote.BirthProfileDto
import com.destinyai.astrology.data.remote.CompatibilityPersonDto
import com.destinyai.astrology.data.remote.CompatibilityRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CompatibilityUiState(
    val personAName: String = "",
    val personALoaded: Boolean = false,
    val partnerName: String = "",
    val partnerDob: String = "",
    val partnerTime: String = "",
    val partnerCity: String = "",
    val partnerLatitude: Double = 0.0,
    val partnerLongitude: Double = 0.0,
    val result: String = "",
    val score: Int? = null,
    val isAnalyzing: Boolean = false,
    val error: String? = null,
) {
    val canAnalyze: Boolean
        get() = personALoaded &&
            partnerName.isNotBlank() &&
            partnerDob.isNotBlank() &&
            partnerTime.isNotBlank() &&
            partnerCity.isNotBlank() &&
            (partnerLatitude != 0.0 || partnerLongitude != 0.0)
}

@HiltViewModel
class CompatibilityViewModel @Inject constructor(
    private val api: AstroApiService,
    private val prefs: UserPreferences,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CompatibilityUiState())
    val uiState: StateFlow<CompatibilityUiState> = _uiState

    private var personAProfile: BirthProfileDto? = null
    private var personAEmail: String? = null

    fun loadUserData() {
        viewModelScope.launch {
            val email = prefs.getUserEmail() ?: return@launch
            val profile = prefs.getBirthProfile() ?: return@launch
            val name = prefs.getUserName() ?: ""
            personAProfile = profile
            personAEmail = email
            _uiState.update { it.copy(personAName = name, personALoaded = true) }
        }
    }

    fun setPartnerName(name: String) = _uiState.update { it.copy(partnerName = name) }
    fun setPartnerDob(dob: String) = _uiState.update { it.copy(partnerDob = dob) }
    fun setPartnerTime(time: String) = _uiState.update { it.copy(partnerTime = time) }
    fun setPartnerLocation(city: String, lat: Double, lon: Double) =
        _uiState.update { it.copy(partnerCity = city, partnerLatitude = lat, partnerLongitude = lon) }

    fun analyze() {
        viewModelScope.launch {
            val s = _uiState.value
            if (!s.canAnalyze) return@launch
            val profile = personAProfile ?: return@launch
            val email = personAEmail ?: return@launch
            _uiState.update { it.copy(isAnalyzing = true, error = null) }
            try {
                val response = api.analyzeCompatibility(
                    CompatibilityRequest(
                        personA = CompatibilityPersonDto(
                            email = email,
                            name = s.personAName,
                            birthProfile = profile,
                        ),
                        personB = CompatibilityPersonDto(
                            name = s.partnerName,
                            birthProfile = BirthProfileDto(
                                dateOfBirth = s.partnerDob,
                                timeOfBirth = s.partnerTime,
                                cityOfBirth = s.partnerCity,
                                latitude = s.partnerLatitude,
                                longitude = s.partnerLongitude,
                            ),
                        ),
                    )
                )
                _uiState.update {
                    it.copy(
                        result = response.text,
                        score = response.score,
                        isAnalyzing = false,
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isAnalyzing = false, error = e.message ?: "Analysis failed") }
            }
        }
    }

    fun clearResult() = _uiState.update { it.copy(result = "", score = null, error = null) }
}
