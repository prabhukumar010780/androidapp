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
    /** UUID for partners; the user's email for self. Used as the API profileId for switchProfile. */
    val id: String,
    /** The owning account's email — same for every entry. Used for prefs.activeProfileEmail. */
    val email: String,
    val name: String,
    val isSelf: Boolean,
    /** ISO yyyy-MM-dd. Rendered as a "Month dd, yyyy" caption beneath the name. */
    val dateOfBirth: String? = null,
)

data class ProfileSwitcherUiState(
    val upgradeRequiredPrompt: Boolean = false,
    /** Non-null when a non-upgrade switch failure must be surfaced as an alert. iOS parity: ProfileSwitcherSheet.swift:201-205. */
    val switchError: String? = null,
)

@HiltViewModel
class ProfileSwitcherViewModel @Inject constructor(
    private val api: AstroApiService,
    private val prefs: UserPreferences,
    private val profileChangeBus: ProfileChangeBus,
) : ViewModel() {

    private val _profiles = MutableStateFlow<List<ProfileEntry>>(emptyList())
    val profiles: StateFlow<List<ProfileEntry>> = _profiles.asStateFlow()

    private val _activeProfileId = MutableStateFlow<String?>(null)
    val activeProfileId: StateFlow<String?> = _activeProfileId.asStateFlow()

    /** Owner email — kept for callers still keying on the account email. */
    private val _activeEmail = MutableStateFlow<String?>(null)
    val activeEmail: StateFlow<String?> = _activeEmail.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    /**
     * In-flight profile switch — distinct from initial profile-list load.
     * iOS parity: ProfileSwitcherSheet.swift:67-70 — replaces the X button
     * with a ProgressView while profileContext.switchTo() is running.
     */
    private val _isSwitching = MutableStateFlow(false)
    val isSwitching: StateFlow<Boolean> = _isSwitching.asStateFlow()

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
            _activeEmail.value = selfEmail
            // Active selection is keyed by profile id (UUID for partners, email for self).
            // Falls back to the owner email = self profile when nothing has been switched yet.
            val activeId = prefs.getActiveProfileId() ?: selfEmail
            _activeProfileId.value = activeId

            val selfName = prefs.getUserName() ?: selfEmail
            val selfBirth = prefs.getBirthProfile()
            val entries = mutableListOf(
                ProfileEntry(
                    id = selfEmail,
                    email = selfEmail,
                    name = selfName,
                    isSelf = true,
                    dateOfBirth = selfBirth?.dateOfBirth,
                ),
            )

            try {
                val partners = api.listPartners(selfEmail)
                partners.forEach { partner ->
                    entries.add(
                        ProfileEntry(
                            id = partner.id,
                            email = selfEmail,
                            name = partner.name,
                            isSelf = false,
                            dateOfBirth = partner.dateOfBirth,
                        ),
                    )
                }
            } catch (_: Exception) {
                // Partners load failure is non-fatal — show self only
            }

            _profiles.value = entries
            _isLoading.value = false
        }
    }

    fun switchProfile(profileId: String) {
        viewModelScope.launch {
            _isSwitching.value = true
            try {
                val selfEmail = prefs.getUserEmail() ?: return@launch
                // Check access state before switching
                val status = api.getStatus(selfEmail)
                if (status.accessState == "upgrade_required") {
                    _uiState.value = _uiState.value.copy(upgradeRequiredPrompt = true)
                    return@launch
                }
                api.switchProfile(SwitchProfileRequest(userEmail = selfEmail, profileId = profileId))
                // Persist the active profile ID (UUID for partners, email for self).
                // The owner email never changes — never store a UUID into the email field.
                prefs.setActiveProfileId(profileId)
                _activeProfileId.value = profileId
                profileChangeBus.emit(profileId)
            } catch (e: Exception) {
                // iOS parity: ProfileSwitcherSheet.swift:107-113, 201-205. A non-upgrade
                // switch failure must surface as a "profile_switch_failed_title" alert
                // with the underlying error message.
                _uiState.value = _uiState.value.copy(
                    switchError = e.localizedMessage ?: e.message ?: "",
                )
            } finally {
                _isSwitching.value = false
            }
        }
    }

    fun dismissUpgradePrompt() {
        _uiState.value = _uiState.value.copy(upgradeRequiredPrompt = false)
    }

    fun dismissError() {
        _uiState.value = _uiState.value.copy(switchError = null)
    }
}
