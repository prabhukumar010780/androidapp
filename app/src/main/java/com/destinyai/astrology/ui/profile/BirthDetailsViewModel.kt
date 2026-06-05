package com.destinyai.astrology.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.destinyai.astrology.data.local.prefs.UserPreferences
import com.destinyai.astrology.data.remote.AstroApiService
import com.destinyai.astrology.data.remote.ProfileRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BirthDetailsUiState(
    val name: String = "",
    val gender: String = "",
    val dateOfBirth: String = "",
    val timeOfBirth: String = "",
    val cityOfBirth: String = "",
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val saveSuccess: Boolean = false,
    val error: String? = null,
    // snapshot for hasChanges
    val originalName: String = "",
    val originalGender: String = "",
) {
    val hasChanges: Boolean
        get() = name != originalName || gender != originalGender
}

@HiltViewModel
class BirthDetailsViewModel @Inject constructor(
    private val api: AstroApiService,
    private val prefs: UserPreferences,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BirthDetailsUiState())
    val uiState: StateFlow<BirthDetailsUiState> = _uiState.asStateFlow()

    fun loadBirthData() {
        viewModelScope.launch {
            val profile = prefs.getBirthProfile()
            val name = prefs.getUserName() ?: ""
            if (profile != null) {
                val gender = profile.gender ?: ""
                _uiState.update {
                    it.copy(
                        name = name,
                        gender = gender,
                        dateOfBirth = profile.dateOfBirth,
                        timeOfBirth = profile.timeOfBirth,
                        cityOfBirth = profile.cityOfBirth,
                        originalName = name,
                        originalGender = gender,
                    )
                }
            }
        }
    }

    fun setName(name: String) = _uiState.update { it.copy(name = name) }
    fun setGender(gender: String) = _uiState.update { it.copy(gender = gender) }

    fun saveName() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null, saveSuccess = false) }
            try {
                val email = prefs.getUserEmail() ?: run {
                    _uiState.update { it.copy(isSaving = false) }
                    return@launch
                }
                val profile = prefs.getBirthProfile() ?: run {
                    _uiState.update { it.copy(isSaving = false) }
                    return@launch
                }
                val s = _uiState.value
                val updatedProfile = profile.copy(gender = s.gender.ifBlank { null })
                api.saveProfile(
                    ProfileRequest(
                        email = email,
                        userName = s.name.ifBlank { null },
                        isGeneratedEmail = false,
                        birthProfile = updatedProfile,
                    )
                )
                prefs.setUserName(s.name)
                prefs.setBirthProfile(updatedProfile)
                _uiState.update { it.copy(isSaving = false, saveSuccess = true, originalName = s.name, originalGender = s.gender) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false, error = e.message ?: "Save failed") }
            }
        }
    }
}
