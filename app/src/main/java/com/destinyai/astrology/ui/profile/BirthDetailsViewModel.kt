package com.destinyai.astrology.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
    val isSaved: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class BirthDetailsViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(BirthDetailsUiState())
    val uiState: StateFlow<BirthDetailsUiState> = _uiState.asStateFlow()

    fun loadBirthData() {
        // TODO: load from DataStore or repository
    }

    fun setName(name: String) = _uiState.update { it.copy(name = name) }
    fun setGender(gender: String) = _uiState.update { it.copy(gender = gender) }

    fun saveName() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            // TODO: call API to update name/gender
            _uiState.update { it.copy(isLoading = false, isSaved = true) }
        }
    }
}
