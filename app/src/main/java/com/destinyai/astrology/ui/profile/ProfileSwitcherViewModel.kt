package com.destinyai.astrology.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.destinyai.astrology.data.local.db.PartnerDao
import com.destinyai.astrology.data.local.db.PartnerProfileEntity
import com.destinyai.astrology.data.local.prefs.UserPreferences
import com.destinyai.astrology.data.remote.AstroApiService
import com.destinyai.astrology.data.remote.CreatePartnerRequest
import com.destinyai.astrology.data.remote.PartnerDto
import com.destinyai.astrology.data.remote.PartnerRequest
import com.destinyai.astrology.data.remote.SwitchProfileRequest
import com.destinyai.astrology.services.ProfileChangeBus
import com.destinyai.astrology.services.QuotaManager
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
    /**
     * iOS parity (ProfileSwitcherSheet.swift:231-241): non-null message displayed in
     * the "Profile Limit Reached" alert with Upgrade / OK actions when the user
     * is at the maintain_profile cap.
     */
    val limitMessage: String? = null,
    /**
     * iOS parity (ProfileSwitcherSheet.swift:209-230): inline PartnerFormView sheet
     * surfaced from inside the switcher when the quota check passes.
     */
    val showAddForm: Boolean = false,
)

@HiltViewModel
class ProfileSwitcherViewModel @Inject constructor(
    private val api: AstroApiService,
    private val prefs: UserPreferences,
    private val profileChangeBus: ProfileChangeBus,
    private val quotaManager: QuotaManager,
    private val partnerDao: PartnerDao,
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

    /**
     * iOS parity (ProfileSwitcherSheet.swift:246-283): fetch from server, persist
     * locally, and fall back to Room cache on network failure. Public so the
     * sheet can re-trigger after returning from the Partner Manager.
     */
    fun reloadProfiles() {
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
            val selfEntry = ProfileEntry(
                id = selfEmail,
                email = selfEmail,
                name = selfName,
                isSelf = true,
                dateOfBirth = selfBirth?.dateOfBirth,
            )

            // iOS parity (PartnerProfileService.swift:191-289): server-first with
            // SwiftData fallback. On Android we mirror with Room — try the network
            // and on failure rehydrate from PartnerDao.getPartnersForUser().
            val partners: List<PartnerDto> = try {
                val fetched = api.listPartners(selfEmail)
                savePartnersLocally(selfEmail, fetched)
                fetched
            } catch (_: Exception) {
                loadPartnersFromCache(selfEmail)
            }

            val entries = mutableListOf(selfEntry)
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
            _profiles.value = entries
            _isLoading.value = false
        }
    }

    /**
     * iOS parity (PartnerProfileService.swift:savePartnersLocally) — persist the
     * server-fetched partner list into Room so the next cold-start (or a network
     * failure) can re-render the switcher without hitting the API.
     */
    private suspend fun savePartnersLocally(ownerEmail: String, partners: List<PartnerDto>) {
        try {
            partners.forEach { p ->
                partnerDao.insertOrReplace(
                    PartnerProfileEntity(
                        id = p.id,
                        ownerEmail = ownerEmail,
                        name = p.name,
                        dateOfBirth = p.dateOfBirth ?: "",
                        timeOfBirth = p.timeOfBirth ?: "",
                        cityOfBirth = p.cityOfBirth ?: "",
                        latitude = p.latitude ?: 0.0,
                        longitude = p.longitude ?: 0.0,
                    ),
                )
            }
        } catch (_: Exception) {
            // Cache is opportunistic — backend remains source of truth.
        }
    }

    private suspend fun loadPartnersFromCache(ownerEmail: String): List<PartnerDto> {
        return try {
            partnerDao.getPartnersForUser(ownerEmail).map { e ->
                PartnerDto(
                    id = e.id,
                    name = e.name,
                    dateOfBirth = e.dateOfBirth,
                    timeOfBirth = e.timeOfBirth.takeIf { it.isNotBlank() },
                    cityOfBirth = e.cityOfBirth.takeIf { it.isNotBlank() },
                    latitude = e.latitude,
                    longitude = e.longitude,
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun switchProfile(profileId: String) {
        viewModelScope.launch {
            _isSwitching.value = true
            try {
                val selfEmail = prefs.getUserEmail() ?: return@launch
                // iOS parity (ProfileSwitcherSheet.swift:101-135): rely on the
                // switchProfile call itself to surface upgrade-required failures
                // — iOS does not pre-flight /subscription/status and instead
                // inspects the switch error message. Removing the pre-check
                // saves a network round-trip and keeps the two platforms aligned
                // on a single approach.
                api.switchProfile(SwitchProfileRequest(userEmail = selfEmail, profileId = profileId))
                // Persist the active profile ID (UUID for partners, email for self).
                // The owner email never changes — never store a UUID into the email field.
                prefs.setActiveProfileId(profileId)
                _activeProfileId.value = profileId
                profileChangeBus.emit(profileId)
            } catch (e: Exception) {
                // iOS parity: ProfileSwitcherSheet.swift:108-113 — when the switch
                // fails with an "Upgrade"-style message, route to the subscription
                // sheet; otherwise surface the underlying error in the alert.
                val message = e.localizedMessage ?: e.message ?: ""
                if (message.contains("Upgrade", ignoreCase = true)) {
                    _uiState.value = _uiState.value.copy(upgradeRequiredPrompt = true)
                } else {
                    _uiState.value = _uiState.value.copy(switchError = message)
                }
            } finally {
                _isSwitching.value = false
            }
        }
    }

    /**
     * iOS parity (ProfileSwitcherSheet.swift:287-304 checkAndShowAddForm +
     * QuotaManager.canAddProfile). Pre-flight quota check before opening the
     * inline add-partner form:
     *   - canAccess + within limit → showAddForm
     *   - free user (limit == 0)   → upgradeRequiredPrompt
     *   - core user at limit       → limitMessage with Upgrade/OK
     */
    fun requestAddPartner() {
        viewModelScope.launch {
            val email = prefs.getUserEmail()
            if (email.isNullOrBlank()) {
                _uiState.value = _uiState.value.copy(showAddForm = true)
                return@launch
            }
            try {
                val response = quotaManager.canAccessFeature(
                    QuotaManager.FeatureID.MAINTAIN_PROFILE,
                    email,
                )
                val overall = response.limits?.get("overall")
                val limit = overall?.limit ?: -1
                val current = _profiles.value.count { !it.isSelf }
                when {
                    !response.canAccess && limit == 0 -> {
                        _uiState.value = _uiState.value.copy(upgradeRequiredPrompt = true)
                    }
                    !response.canAccess || (limit != -1 && current >= limit) -> {
                        _uiState.value = _uiState.value.copy(
                            limitMessage = formatLimitMessage(limit),
                        )
                    }
                    else -> {
                        _uiState.value = _uiState.value.copy(showAddForm = true)
                    }
                }
            } catch (_: Exception) {
                // Fail-open parity with iOS canAddProfile catch (QuotaManager.swift:813-816).
                _uiState.value = _uiState.value.copy(showAddForm = true)
            }
        }
    }

    private fun formatLimitMessage(limit: Int): String =
        "You can save up to $limit profiles. Upgrade to Plus for unlimited profiles."

    /**
     * iOS parity (ProfileSwitcherSheet.swift:209-230): create a partner via the
     * backend API from the inline add-partner sheet, then reload the profile
     * list so the new entry appears immediately in the switcher.
     */
    fun createPartner(request: PartnerRequest) {
        viewModelScope.launch {
            val email = prefs.getUserEmail() ?: return@launch
            try {
                val partner = api.addPartner(
                    CreatePartnerRequest(
                        userEmail = email,
                        profile = request,
                        guardianConsentGiven = request.guardianConsentGiven,
                    ),
                )
                savePartnersLocally(email, listOf(partner))
                _uiState.value = _uiState.value.copy(showAddForm = false)
                loadProfiles()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    showAddForm = false,
                    switchError = e.localizedMessage ?: e.message ?: "Failed to save profile",
                )
            }
        }
    }

    fun dismissAddForm() {
        _uiState.value = _uiState.value.copy(showAddForm = false)
    }

    fun dismissLimitMessage() {
        _uiState.value = _uiState.value.copy(limitMessage = null)
    }

    /**
     * iOS parity (ProfileSwitcherSheet.swift:231-241): tapping Upgrade in the
     * limit alert clears the message and surfaces the subscription sheet.
     */
    fun upgradeFromLimit() {
        _uiState.value = _uiState.value.copy(
            limitMessage = null,
            upgradeRequiredPrompt = true,
        )
    }

    fun dismissUpgradePrompt() {
        _uiState.value = _uiState.value.copy(upgradeRequiredPrompt = false)
    }

    fun dismissError() {
        _uiState.value = _uiState.value.copy(switchError = null)
    }
}
