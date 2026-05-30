package com.destinyai.astrology.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.destinyai.astrology.data.local.prefs.UserPreferences
import com.destinyai.astrology.data.remote.AstroApiService
import com.destinyai.astrology.data.remote.SwitchProfileRequest
import com.destinyai.astrology.services.ProfileChangeBus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProfileEntry(
    val email: String,
    val name: String,
    val isSelf: Boolean,
)

data class ProfileSwitcherUiState(
    val upgradeRequiredPrompt: Boolean = false,
)

@HiltViewModel
class ProfileSwitcherViewModel @Inject constructor(
    private val api: AstroApiService,
    private val prefs: UserPreferences,
    private val profileChangeBus: ProfileChangeBus,
) : ViewModel() {

    private val _profiles = MutableStateFlow<List<ProfileEntry>>(emptyList())
    val profiles: StateFlow<List<ProfileEntry>> = _profiles.asStateFlow()

    private val _activeEmail = MutableStateFlow<String?>(null)
    val activeEmail: StateFlow<String?> = _activeEmail.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _uiState = MutableStateFlow(ProfileSwitcherUiState())
    val uiState: StateFlow<ProfileSwitcherUiState> = _uiState.asStateFlow()

    init {
        loadProfiles()
    }

    private fun loadProfiles() {
        viewModelScope.launch {
            _isLoading.value = true
            val selfEmail = prefs.getUserEmail() ?: run {
                _isLoading.value = false
                return@launch
            }
            val active = prefs.getActiveProfileEmail() ?: selfEmail
            _activeEmail.value = active

            val selfName = prefs.getUserName() ?: selfEmail
            val entries = mutableListOf(ProfileEntry(email = selfEmail, name = selfName, isSelf = true))

            try {
                val partners = api.listPartners(selfEmail)
                partners.forEach { partner ->
                    entries.add(ProfileEntry(email = partner.id, name = partner.name, isSelf = false))
                }
            } catch (_: Exception) {
                // Partners load failure is non-fatal — show self only
            }

            _profiles.value = entries
            _isLoading.value = false
        }
    }

    fun switchProfile(email: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val selfEmail = prefs.getUserEmail() ?: return@launch
                // Check access state before switching
                val status = api.getStatus(selfEmail)
                if (status.accessState == "upgrade_required") {
                    _uiState.value = ProfileSwitcherUiState(upgradeRequiredPrompt = true)
                    _isLoading.value = false
                    return@launch
                }
                api.switchProfile(SwitchProfileRequest(userEmail = selfEmail, profileId = email))
                prefs.setActiveProfileEmail(email)
                _activeEmail.value = email
                profileChangeBus.emit(email)
            } catch (_: Exception) {
                // Silently ignore — active email not updated
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun dismissUpgradePrompt() {
        _uiState.value = ProfileSwitcherUiState(upgradeRequiredPrompt = false)
    }
}
