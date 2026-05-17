package com.destinyai.astrology.ui.subscription

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.destinyai.astrology.data.local.prefs.UserPreferences
import com.destinyai.astrology.data.remote.AstroApiService
import com.destinyai.astrology.data.remote.PlanDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SubscriptionUiState(
    val plans: List<PlanDto> = emptyList(),
    val currentPlanId: String = "",
    val isPremium: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class SubscriptionViewModel @Inject constructor(
    private val api: AstroApiService,
    private val prefs: UserPreferences,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SubscriptionUiState())
    val uiState: StateFlow<SubscriptionUiState> = _uiState

    fun loadPlans() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val plans = api.getPlans()
                _uiState.update { it.copy(plans = plans, isLoading = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "Failed to load plans") }
            }
        }
    }

    fun loadCurrentPlan() {
        viewModelScope.launch {
            val email = prefs.getUserEmail() ?: return@launch
            try {
                val status = api.getStatus(email)
                _uiState.update {
                    it.copy(
                        currentPlanId = status.planId,
                        isPremium = status.isPremium,
                    )
                }
            } catch (_: Exception) {}
        }
    }
}
