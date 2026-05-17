package com.destinyai.astrology.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.destinyai.astrology.data.local.prefs.UserPreferences
import com.destinyai.astrology.data.remote.AstroApiService
import com.destinyai.astrology.data.remote.BirthProfileDto
import com.destinyai.astrology.data.remote.ProfileRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

data class BirthDataUiState(
    val userName: String = "",
    val dateOfBirth: String = "",
    val timeOfBirth: String = "12:00",
    val cityOfBirth: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val gender: String = "",
    val timeUnknown: Boolean = false,
    val isDateSelected: Boolean = false,
    val isTimeSelected: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
    val showLocationSearch: Boolean = false,
    val birthDataTakenEmail: String? = null,
    val birthDataTakenProvider: String? = null,
    val isSaved: Boolean = false,
)

@HiltViewModel
class BirthDataViewModel @Inject constructor(
    private val api: AstroApiService,
    private val prefs: UserPreferences,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BirthDataUiState())
    val uiState: StateFlow<BirthDataUiState> = _uiState

    val isValid: Boolean
        get() {
            val s = _uiState.value
            return s.userName.isNotBlank() &&
                s.cityOfBirth.isNotBlank() &&
                (s.latitude != 0.0 || s.longitude != 0.0) &&
                s.isDateSelected &&
                !isUnder13(s.dateOfBirth) &&
                (s.isTimeSelected || s.timeUnknown) &&
                s.gender.isNotEmpty()
        }

    fun setUserName(name: String) = _uiState.update { it.copy(userName = name) }

    fun setDateOfBirth(dob: String) = _uiState.update { it.copy(dateOfBirth = dob, isDateSelected = true) }

    fun setTimeOfBirth(time: String) = _uiState.update { it.copy(timeOfBirth = time, isTimeSelected = true) }

    fun setGender(gender: String) = _uiState.update { it.copy(gender = gender) }

    fun setTimeUnknown(unknown: Boolean) = _uiState.update { it.copy(timeUnknown = unknown) }

    fun setLocation(city: String, lat: Double, lng: Double) =
        _uiState.update { it.copy(cityOfBirth = city, latitude = lat, longitude = lng) }

    fun toggleLocationSearch() = _uiState.update { it.copy(showLocationSearch = !it.showLocationSearch) }

    fun clearError() = _uiState.update { it.copy(error = null) }

    // Test helpers — allow clearing selection state without re-triggering setters
    internal fun clearDateSelected() = _uiState.update { it.copy(isDateSelected = false) }
    internal fun clearTimeSelected() = _uiState.update { it.copy(isTimeSelected = false) }

    fun loadSaved() {
        viewModelScope.launch {
            val profile = prefs.getBirthProfile() ?: return@launch
            val name = prefs.getUserName() ?: ""
            _uiState.update {
                it.copy(
                    userName = name,
                    dateOfBirth = profile.dateOfBirth,
                    timeOfBirth = profile.timeOfBirth,
                    cityOfBirth = profile.cityOfBirth,
                    latitude = profile.latitude,
                    longitude = profile.longitude,
                    gender = profile.gender ?: "",
                    timeUnknown = profile.birthTimeUnknown,
                    isDateSelected = profile.dateOfBirth.isNotBlank(),
                    isTimeSelected = profile.timeOfBirth.isNotBlank() && !profile.birthTimeUnknown,
                )
            }
        }
    }

    fun save() {
        viewModelScope.launch {
            val s = _uiState.value
            if (s.cityOfBirth.isBlank()) {
                _uiState.update { it.copy(error = "Please select your city of birth") }
                return@launch
            }
            if (s.latitude == 0.0 && s.longitude == 0.0) {
                _uiState.update { it.copy(error = "Please select a valid city from the search") }
                return@launch
            }

            _uiState.update { it.copy(isLoading = true, error = null) }

            try {
                val email = prefs.getUserEmail() ?: ""
                val isGuest = prefs.isGuestUser()
                val profile = BirthProfileDto(
                    dateOfBirth = s.dateOfBirth,
                    timeOfBirth = s.timeOfBirth,
                    cityOfBirth = s.cityOfBirth.trim(),
                    latitude = s.latitude,
                    longitude = s.longitude,
                    gender = s.gender.ifEmpty { null },
                    birthTimeUnknown = s.timeUnknown,
                )

                api.saveProfile(
                    ProfileRequest(
                        email = email,
                        userName = s.userName.trim().ifEmpty { null },
                        userType = if (isGuest) "guest" else "registered",
                        isGeneratedEmail = isGuest,
                        birthProfile = profile,
                    )
                )

                prefs.setBirthProfile(profile)
                prefs.setHasBirthData(true)
                if (s.userName.isNotBlank()) prefs.setUserName(s.userName.trim())

                _uiState.update { it.copy(isLoading = false, isSaved = true) }
            } catch (e: retrofit2.HttpException) {
                if (e.code() == 409) {
                    val errorJson = e.response()?.errorBody()?.string() ?: ""
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            birthDataTakenEmail = extractJsonField(errorJson, "existing_email"),
                            birthDataTakenProvider = extractJsonField(errorJson, "provider"),
                        )
                    }
                } else {
                    _uiState.update { it.copy(isLoading = false, error = "Failed to save profile") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "Failed to save profile") }
            }
        }
    }

    private fun extractJsonField(json: String, field: String): String? {
        val match = """"$field"\s*:\s*"([^"]+)"""".toRegex().find(json)
        return match?.groupValues?.get(1)
    }

    private fun isUnder13(dob: String): Boolean {
        if (dob.isBlank()) return false
        return try {
            val parts = dob.split("-")
            val dobYear = parts[0].toInt()
            val dobMonth = parts[1].toInt()
            val dobDay = parts[2].toInt()
            val today = Calendar.getInstance()
            var age = today.get(Calendar.YEAR) - dobYear
            val monthDiff = today.get(Calendar.MONTH) + 1 - dobMonth
            if (monthDiff < 0 || (monthDiff == 0 && today.get(Calendar.DAY_OF_MONTH) < dobDay)) age--
            age < 13
        } catch (_: Exception) {
            false
        }
    }
}
