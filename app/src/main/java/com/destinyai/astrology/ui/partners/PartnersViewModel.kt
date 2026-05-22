package com.destinyai.astrology.ui.partners

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.destinyai.astrology.data.local.prefs.UserPreferences
import com.destinyai.astrology.data.remote.AstroApiService
import com.destinyai.astrology.data.remote.CreatePartnerRequest
import com.destinyai.astrology.data.remote.PartnerDto
import com.destinyai.astrology.data.remote.PartnerRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PartnersUiState(
    val partners: List<PartnerDto> = emptyList(),
    val isLoading: Boolean = false,
    val showAddForm: Boolean = false,
    val formName: String = "",
    val formDob: String = "",
    val formTime: String = "",
    val formCity: String = "",
    val formLatitude: Double = 0.0,
    val formLongitude: Double = 0.0,
    val isSaving: Boolean = false,
    val error: String? = null,
) {
    val isFormValid: Boolean
        get() = formName.isNotBlank() &&
            formDob.isNotBlank() &&
            formTime.isNotBlank() &&
            formCity.isNotBlank() &&
            (formLatitude != 0.0 || formLongitude != 0.0)
}

@HiltViewModel
class PartnersViewModel @Inject constructor(
    private val api: AstroApiService,
    private val prefs: UserPreferences,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PartnersUiState())
    val uiState: StateFlow<PartnersUiState> = _uiState

    fun loadPartners() {
        viewModelScope.launch {
            val email = prefs.getUserEmail() ?: return@launch
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val partners = api.listPartners(email)
                _uiState.update { it.copy(partners = partners, isLoading = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "Failed to load") }
            }
        }
    }

    fun addPartner() {
        viewModelScope.launch {
            val email = prefs.getUserEmail() ?: return@launch
            val s = _uiState.value
            if (!s.isFormValid) return@launch
            _uiState.update { it.copy(isSaving = true, error = null) }
            try {
                val partner = api.addPartner(
                    CreatePartnerRequest(
                        userEmail = email,
                        profile = PartnerRequest(
                            name = s.formName.trim(),
                            dateOfBirth = s.formDob,
                            timeOfBirth = s.formTime,
                            cityOfBirth = s.formCity.trim(),
                            latitude = s.formLatitude,
                            longitude = s.formLongitude,
                        ),
                    )
                )
                _uiState.update {
                    it.copy(
                        partners = it.partners + partner,
                        isSaving = false,
                        showAddForm = false,
                        formName = "",
                        formDob = "",
                        formTime = "",
                        formCity = "",
                        formLatitude = 0.0,
                        formLongitude = 0.0,
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false, error = e.message ?: "Failed to add") }
            }
        }
    }

    fun deletePartner(partnerId: String) {
        viewModelScope.launch {
            val email = prefs.getUserEmail() ?: return@launch
            try {
                api.deletePartner(partnerId, email)
                _uiState.update { state ->
                    state.copy(partners = state.partners.filterNot { it.id == partnerId })
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "Failed to delete") }
            }
        }
    }

    fun setFormName(name: String) = _uiState.update { it.copy(formName = name) }
    fun setFormDob(dob: String) = _uiState.update { it.copy(formDob = dob) }
    fun setFormTime(time: String) = _uiState.update { it.copy(formTime = time) }
    fun setFormLocation(city: String, lat: Double, lon: Double) =
        _uiState.update { it.copy(formCity = city, formLatitude = lat, formLongitude = lon) }

    fun toggleAddForm() = _uiState.update { it.copy(showAddForm = !it.showAddForm, error = null) }
}
