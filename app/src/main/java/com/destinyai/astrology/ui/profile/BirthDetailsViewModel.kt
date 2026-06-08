package com.destinyai.astrology.ui.profile

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

data class BirthDetailsUiState(
    val name: String = "",
    val gender: String = "",
    val dateOfBirth: String = "",
    val timeOfBirth: String = "",
    val cityOfBirth: String = "",
    // iOS parity (BirthDetailsView.swift:300-313): when the per-user
    // birthTimeUnknown flag is set, surface a "birth_time_unknown" label
    // instead of the formatted time so the UI matches iOS.
    val birthTimeUnknown: Boolean = false,
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
    private val prefs: UserPreferences,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BirthDetailsUiState())
    val uiState: StateFlow<BirthDetailsUiState> = _uiState.asStateFlow()

    fun loadBirthData() {
        viewModelScope.launch {
            val profile = prefs.getBirthProfile()
            val name = prefs.getUserName() ?: ""
            // iOS parity (BirthDetailsView.swift:277-279): gender is read from a
            // user-scoped key ("userGender_<email>"), not just the global one.
            // Fall back to global USER_GENDER, then to BirthProfile.gender so
            // existing installs do not lose data. Pass an explicit email to the
            // scoped overload to disambiguate from the no-arg global accessor.
            val email = prefs.getUserEmail()
            val scopedGender = prefs.getUserGender(email = email)
            val globalGender = if (email == null) prefs.getUserGender(email = null) else null
            // iOS parity (BirthDetailsView.swift:300-301): birthTimeUnknown is
            // a per-user scoped flag, separate from the BirthProfile field.
            val timeUnknownScoped = prefs.getBirthTimeUnknownScoped(email = email)
            if (profile != null) {
                val gender = scopedGender ?: globalGender ?: profile.gender ?: ""
                val timeUnknown = timeUnknownScoped || profile.birthTimeUnknown
                _uiState.update {
                    it.copy(
                        name = name,
                        gender = gender,
                        dateOfBirth = profile.dateOfBirth,
                        timeOfBirth = profile.timeOfBirth,
                        cityOfBirth = profile.cityOfBirth,
                        birthTimeUnknown = timeUnknown,
                        originalName = name,
                        originalGender = gender,
                    )
                }
            } else {
                // Even without a profile, surface name + scoped gender so the
                // editable section is usable offline (iOS parity: loadData()
                // populates name/gender independently of birth-data presence).
                val gender = scopedGender ?: globalGender ?: ""
                _uiState.update {
                    it.copy(
                        name = name,
                        gender = gender,
                        birthTimeUnknown = timeUnknownScoped,
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
            val s = _uiState.value
            // iOS parity (BirthDetailsView.swift:325-334): saveChanges is
            // local-only — it writes UserDefaults and dismisses, with NO
            // POST /profile network call. Mirror that here: persist name +
            // gender (global + scoped) and update the local birth profile
            // gender. Eliminates cross-platform contract drift on /profile.
            try {
                prefs.setUserName(s.name)
                val genderToStore = s.gender.ifBlank { null }
                // Global USER_GENDER (legacy / cross-screen reads).
                prefs.setUserGender(genderToStore)
                // User-scoped key ("userGender_<email>") matching iOS
                // StorageKeys.userKey(for: userGender). Resolved against
                // current email automatically when omitted.
                if (genderToStore != null) {
                    prefs.setUserGender(gender = genderToStore, email = null)
                }
                // Keep local BirthProfile.gender in sync so other screens
                // reading the profile see the updated value (iOS writes the
                // gender through the same UserDefaults-backed model).
                val profile = prefs.getBirthProfile()
                if (profile != null) {
                    prefs.setBirthProfile(profile.copy(gender = genderToStore))
                }
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        saveSuccess = true,
                        originalName = s.name,
                        originalGender = s.gender,
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isSaving = false, error = e.message ?: "Local save failed")
                }
            }
        }
    }

    /** Consume the one-shot error flag after the screen surfaces it via Snackbar. */
    fun clearError() = _uiState.update { it.copy(error = null) }
}
