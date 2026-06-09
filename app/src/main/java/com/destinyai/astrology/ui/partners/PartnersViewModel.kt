package com.destinyai.astrology.ui.partners

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.destinyai.astrology.data.local.prefs.UserPreferences
import com.destinyai.astrology.data.local.db.PartnerDao
import com.destinyai.astrology.data.local.db.PartnerProfileEntity
import com.destinyai.astrology.data.location.LocationSearchResult
import com.destinyai.astrology.data.location.LocationSearchService
import com.destinyai.astrology.data.remote.AstroApiService
import com.destinyai.astrology.data.remote.CreatePartnerRequest
import com.destinyai.astrology.data.remote.LocationResult
import com.destinyai.astrology.data.remote.PartnerDto
import com.destinyai.astrology.data.remote.PartnerRequest
import com.destinyai.astrology.services.ProfileContextManager
import com.destinyai.astrology.services.QuotaManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

data class PartnersUiState(
    val partners: List<PartnerDto> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val showAddForm: Boolean = false,
    val editingPartnerId: String? = null,
    val formName: String = "",
    val formGender: String = "",
    val formDob: String = "",
    val formTime: String = "",
    val formCity: String = "",
    val formLatitude: Double = 0.0,
    val formLongitude: Double = 0.0,
    val formBirthTimeUnknown: Boolean = false,
    // iOS parity (PartnerFormView.swift:69-72): guardian consent + forCompatibility flags.
    val formGuardianConsentGiven: Boolean = false,
    val formForCompatibility: Boolean = true,
    // iOS parity: surface upgrade prompt when free user is at maintain_profile limit.
    val showQuotaUpgradePrompt: Boolean = false,
    val quotaLimit: Int = -1,
    // Picker visibility — mirrors iOS @State showDatePicker / showTimePicker.
    val showDatePicker: Boolean = false,
    val showTimePicker: Boolean = false,
    val showLocationSearch: Boolean = false,
    val locationResults: List<LocationResult> = emptyList(),
    val isSearchingLocation: Boolean = false,
    val isSaving: Boolean = false,
    // iOS parity (PartnerManagerView.swift:357-370, 388-404): protection alert when
    // a protected partner card is tapped — explains why edit is blocked.
    val showProtectionAlertFor: PartnerDto? = null,
    val error: String? = null,
    // One-shot success event surfaced via the parent screen's SnackbarHost
    // (PartnersScreen.kt). Cleared by consumePartnerSuccess() after display.
    // Mirrors iOS sound + haptic confirmations on PartnerManagerView.swift:42-46
    // (add) and :53-57 (edit), but adds a visible toast Android lacked.
    val successEvent: PartnerSuccessEvent? = null,
    // iOS parity (ProfileView.swift:339-347): guests are gated out of
    // PartnerManagerView at the entry point. Track isGuest here as
    // defense-in-depth so PartnersScreen can route guests to AuthScreen if any
    // other code path lands them on this screen.
    val isGuest: Boolean = false,
) {
    /**
     * Mirrors iOS PartnerFormView.isValid (lines 75-81):
     * - Name non-blank, gender non-blank, DOB selected
     * - Time selected OR birthTimeUnknown=true
     * - Under-13 requires guardian consent
     *
     * iOS does NOT require city / lat / lon — place_of_birth is optional and
     * sent as nil when empty (PartnerFormView.swift:385-387). Android relaxed
     * to match (Gap 1).
     */
    val isFormValid: Boolean
        get() = formName.isNotBlank() &&
            formGender.isNotBlank() &&
            formDob.isNotBlank() &&
            formCity.isNotBlank() &&
            (formTime.isNotBlank() || formBirthTimeUnknown) &&
            (!isUnder13(formDob) || formGuardianConsentGiven)

    /** iOS PartnerFormView.swift:54-59 isUnder13. */
    val isUnder13: Boolean get() = isUnder13(formDob)

    /** iOS PartnerFormView.swift:61-66 isUnder18. */
    val isUnder18: Boolean get() = isUnder18(formDob)
}

/** Mirrors iOS PartnerFormView age helpers (lines 54-66) — DOB string `YYYY-MM-DD`. */
private fun ageInYears(dob: String): Int? {
    if (dob.length < 4) return null
    val year = dob.substring(0, 4).toIntOrNull() ?: return null
    val month = dob.substring(5, 7).toIntOrNull() ?: 1
    val day = dob.substring(8, 10).toIntOrNull() ?: 1
    val now = Calendar.getInstance()
    val nowY = now.get(Calendar.YEAR)
    val nowM = now.get(Calendar.MONTH) + 1
    val nowD = now.get(Calendar.DAY_OF_MONTH)
    var age = nowY - year
    if (nowM < month || (nowM == month && nowD < day)) age -= 1
    return age
}

private fun isUnder13(dob: String): Boolean = (ageInYears(dob) ?: 100) < 13
private fun isUnder18(dob: String): Boolean = (ageInYears(dob) ?: 100) < 18

/**
 * One-shot success events emitted by [PartnersViewModel] after a save / delete
 * succeeds. Resolved to a localized string at the screen layer so the VM stays
 * Context-free. Carries the partner's display name so the snackbar can read
 * "Birth chart for X added" the same way iOS reads the new card's title.
 */
sealed class PartnerSuccessEvent {
    data class Added(val partnerName: String) : PartnerSuccessEvent()
    data class Updated(val partnerName: String) : PartnerSuccessEvent()
    data class Deleted(val partnerName: String) : PartnerSuccessEvent()
}

@HiltViewModel
class PartnersViewModel @Inject constructor(
    private val api: AstroApiService,
    private val prefs: UserPreferences,
    private val quotaManager: QuotaManager,
    private val locationSearchService: LocationSearchService,
    private val partnerDao: PartnerDao,
    private val profileContextManager: ProfileContextManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PartnersUiState())
    val uiState: StateFlow<PartnersUiState> = _uiState

    private var locationSearchJob: Job? = null

    /** iOS parity (PartnerProfileService.swift:191-289 SwiftData cache): hydrate from Room first, then refresh. */
    private suspend fun loadFromCache(email: String): List<PartnerDto> {
        return try {
            partnerDao.getPartnersForUser(email).map { e ->
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
        } catch (e: Exception) {
            emptyList()
        }
    }

    private suspend fun saveToCache(email: String, partners: List<PartnerDto>) {
        try {
            partners.forEach { p ->
                partnerDao.insertOrReplace(
                    PartnerProfileEntity(
                        id = p.id,
                        ownerEmail = email,
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
            // Silent — cache is opportunistic; backend remains source of truth.
        }
    }

    fun loadPartners() {
        viewModelScope.launch {
            // iOS parity (ProfileView.swift:339-347): track isGuest so
            // PartnersScreen can defensively route guests back to AuthScreen.
            val isGuest = prefs.isGuestUser()
            _uiState.update { it.copy(isGuest = isGuest) }
            val email = prefs.getUserEmail() ?: return@launch
            // iOS parity: show cached partners immediately while a network refresh runs.
            val cached = loadFromCache(email)
            if (cached.isNotEmpty()) {
                _uiState.update { it.copy(partners = cached, isLoading = false) }
            } else {
                _uiState.update { it.copy(isLoading = true, error = null) }
            }
            try {
                val partners = api.listPartners(email)
                _uiState.update { it.copy(partners = partners, isLoading = false) }
                saveToCache(email, partners)
            } catch (e: Exception) {
                // If we already showed cached data, keep it visible; otherwise surface error.
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = if (cached.isEmpty()) (e.message ?: "Failed to load") else null,
                    )
                }
            }
        }
    }

    /**
     * Mirrors iOS PartnerManagerView.swift:254-257 .refreshable — re-fetches without
     * the full-screen spinner so the pull-to-refresh indicator stays visible.
     */
    fun refresh() {
        viewModelScope.launch {
            val email = prefs.getUserEmail() ?: return@launch
            _uiState.update { it.copy(isRefreshing = true, error = null) }
            try {
                val partners = api.listPartners(email)
                _uiState.update { it.copy(partners = partners, isRefreshing = false) }
                saveToCache(email, partners)
            } catch (e: Exception) {
                _uiState.update { it.copy(isRefreshing = false, error = e.message ?: "Failed to refresh") }
            }
        }
    }

    /**
     * Save handler — covers both create and update paths.
     *
     * Layering note (Cleanup Issues 4 & 5): iOS PartnerFormView passes an `onSave`
     * callback up to PartnerManagerView, which performs the create/update via
     * PartnerProfileService. On Android the form sheet calls `viewModel.addPartner()`
     * directly because the sheet and list share the same PartnersViewModel — there's
     * no second layer to bubble through. The branching on `editingPartnerId` here
     * (line 223) is the Android equivalent of iOS's `service.update(...)` vs
     * `service.create(...)` split inside the parent view's `onSave` closure. Net
     * effect is identical; only the call site differs.
     */
    fun addPartner() {
        viewModelScope.launch {
            val email = prefs.getUserEmail() ?: return@launch
            val s = _uiState.value
            if (!s.isFormValid) return@launch
            _uiState.update { it.copy(isSaving = true, error = null) }
            try {
                // iOS parity (PartnerFormView.swift:365): force forCompatibility=false for under-18.
                val effectiveForCompat = if (s.isUnder18) false else s.formForCompatibility
                val request = CreatePartnerRequest(
                    userEmail = email,
                    profile = PartnerRequest(
                        name = s.formName.trim(),
                        gender = s.formGender,
                        dateOfBirth = s.formDob,
                        timeOfBirth = if (s.formBirthTimeUnknown) null else s.formTime,
                        cityOfBirth = s.formCity.trim(),
                        latitude = s.formLatitude,
                        longitude = s.formLongitude,
                        birthTimeUnknown = s.formBirthTimeUnknown,
                        forCompatibility = effectiveForCompat,
                        guardianConsentGiven = s.formGuardianConsentGiven,
                    ),
                    guardianConsentGiven = s.formGuardianConsentGiven,
                )
                val partner = if (s.editingPartnerId != null) {
                    api.updatePartner(s.editingPartnerId, request)
                } else {
                    api.addPartner(request)
                }
                saveToCache(email, listOf(partner))
                // Invalidate the cached partner-name lookup so Home shows the
                // updated name after rename, and so Add/Edit-then-Switch
                // immediately resolves the new partner without a stale hit.
                profileContextManager.invalidate()
                _uiState.update { state ->
                    val updatedList = if (s.editingPartnerId != null) {
                        state.partners.map { if (it.id == partner.id) partner else it }
                    } else {
                        state.partners + partner
                    }
                    val event = if (s.editingPartnerId != null) {
                        PartnerSuccessEvent.Updated(partner.name)
                    } else {
                        PartnerSuccessEvent.Added(partner.name)
                    }
                    state.copy(
                        partners = updatedList,
                        isSaving = false,
                        showAddForm = false,
                        editingPartnerId = null,
                        formName = "",
                        formGender = "",
                        formDob = "",
                        formTime = "",
                        formCity = "",
                        formLatitude = 0.0,
                        formLongitude = 0.0,
                        formBirthTimeUnknown = false,
                        successEvent = event,
                    )
                }
            } catch (e: retrofit2.HttpException) {
                // iOS parity (PartnerProfileService.swift:91-93): map 409 to duplicate-profile error.
                val message = if (e.code() == 409) {
                    "A birth chart with the same birth data already exists."
                } else {
                    e.message ?: "Failed to save"
                }
                _uiState.update { it.copy(isSaving = false, error = message) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false, error = e.message ?: "Failed to save") }
            }
        }
    }

    fun deletePartner(partnerId: String) {
        viewModelScope.launch {
            val email = prefs.getUserEmail() ?: return@launch
            val target = _uiState.value.partners.firstOrNull { it.id == partnerId }
            // Mirrors iOS PartnerManagerView protected-profile guard (lines 348-371) —
            // prevent deleting self/active/used profiles client-side; backend also returns 403.
            if (target != null && target.isProtected) {
                _uiState.update { it.copy(error = "This birth chart is protected and cannot be deleted.") }
                return@launch
            }
            try {
                api.deletePartner(partnerId, email)
                try { partnerDao.delete(partnerId) } catch (_: Exception) {}
                profileContextManager.invalidate()
                _uiState.update { state ->
                    state.copy(
                        partners = state.partners.filterNot { it.id == partnerId },
                        successEvent = target?.let { PartnerSuccessEvent.Deleted(it.name) },
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "Failed to delete") }
            }
        }
    }

    fun beginEditPartner(partner: PartnerDto) {
        if (partner.isProtected) {
            _uiState.update { it.copy(error = "This birth chart is protected and cannot be edited.") }
            return
        }
        _uiState.update {
            it.copy(
                showAddForm = true,
                editingPartnerId = partner.id,
                formName = partner.name,
                formGender = partner.gender,
                formDob = partner.dateOfBirth ?: "",
                formTime = partner.timeOfBirth ?: "",
                formCity = partner.cityOfBirth ?: "",
                formLatitude = partner.latitude ?: 0.0,
                formLongitude = partner.longitude ?: 0.0,
                formBirthTimeUnknown = partner.birthTimeUnknown,
                formForCompatibility = partner.forCompatibility,
                formGuardianConsentGiven = partner.guardianConsentGiven,
                error = null,
            )
        }
    }

    /**
     * iOS parity (PartnerManagerView.swift:108-125 + QuotaManager.canAddProfile):
     * Pre-flight check before opening the add form. Surfaces upgrade prompt for free
     * users at the maintain_profile limit. On error, fails open (allows form).
     *
     * Delegates the gate logic to QuotaManager.canAddProfile so iOS and Android share
     * a single source of truth for "can the user add another profile" (Cleanup Issue 1).
     */
    fun requestAddPartner() {
        viewModelScope.launch {
            val email = prefs.getUserEmail()
            if (email.isNullOrBlank()) {
                _uiState.update { it.copy(showAddForm = true, editingPartnerId = null, error = null) }
                return@launch
            }
            val current = _uiState.value.partners.size
            when (val result = quotaManager.canAddProfile(email, current)) {
                is QuotaManager.CanAddProfileResult.Blocked -> {
                    _uiState.update {
                        it.copy(showQuotaUpgradePrompt = true, quotaLimit = result.limit)
                    }
                }
                QuotaManager.CanAddProfileResult.Allowed -> {
                    _uiState.update {
                        it.copy(showAddForm = true, editingPartnerId = null, error = null)
                    }
                }
            }
        }
    }

    fun dismissQuotaUpgradePrompt() = _uiState.update { it.copy(showQuotaUpgradePrompt = false) }

    /**
     * iOS parity (PartnerManagerView.swift:78-82): top-level system alert bound to
     * viewModel.showError — list-level errors must surface to the user. Manager
     * dialog dismisses by clearing the field.
     */
    fun clearError() = _uiState.update { it.copy(error = null) }

    /** Consume the one-shot success snackbar event after the screen has shown it. */
    fun consumeSuccessEvent() = _uiState.update { it.copy(successEvent = null) }

    /**
     * iOS parity (PartnerManagerView.swift:386-404): tapping a protected card opens an
     * explanatory alert (primary / active / used) instead of the edit form.
     */
    fun showProtectionAlert(partner: PartnerDto) =
        _uiState.update { it.copy(showProtectionAlertFor = partner) }

    fun dismissProtectionAlert() =
        _uiState.update { it.copy(showProtectionAlertFor = null) }

    fun setFormName(name: String) = _uiState.update { it.copy(formName = name) }
    fun setFormGender(gender: String) = _uiState.update { it.copy(formGender = gender) }
    fun setFormDob(dob: String) = _uiState.update { it.copy(formDob = dob) }
    fun setFormTime(time: String) = _uiState.update { it.copy(formTime = time) }
    fun setFormBirthTimeUnknown(unknown: Boolean) =
        _uiState.update { it.copy(formBirthTimeUnknown = unknown, formTime = if (unknown) "" else it.formTime) }
    fun setFormLocation(city: String, lat: Double, lon: Double) =
        _uiState.update { it.copy(formCity = city, formLatitude = lat, formLongitude = lon) }

    /** iOS parity (PartnerFormView.swift:184-206): guardian consent checkbox. */
    fun setFormGuardianConsent(given: Boolean) =
        _uiState.update { it.copy(formGuardianConsentGiven = given) }

    /** iOS parity (PartnerFormView.swift:207-236): forCompatibility toggle (locked off for under-18). */
    fun setFormForCompatibility(value: Boolean) =
        _uiState.update {
            val locked = it.isUnder18
            it.copy(formForCompatibility = if (locked) false else value)
        }

    fun setShowDatePicker(show: Boolean) = _uiState.update { it.copy(showDatePicker = show) }
    fun setShowTimePicker(show: Boolean) = _uiState.update { it.copy(showTimePicker = show) }
    fun setShowLocationSearch(show: Boolean) = _uiState.update {
        it.copy(showLocationSearch = show, locationResults = if (!show) emptyList() else it.locationResults)
    }

    /** iOS parity (LocationSearchView): debounced lookup + result list. */
    fun searchLocation(query: String) {
        locationSearchJob?.cancel()
        if (query.length < 2) {
            _uiState.update { it.copy(locationResults = emptyList(), isSearchingLocation = false) }
            return
        }
        locationSearchJob = viewModelScope.launch {
            delay(300)
            _uiState.update { it.copy(isSearchingLocation = true) }
            val results = when (val result = locationSearchService.search(query)) {
                is LocationSearchResult.Success -> result.results
                is LocationSearchResult.Failure -> emptyList()
            }
            _uiState.update { it.copy(locationResults = results, isSearchingLocation = false) }
        }
    }

    fun selectLocation(result: LocationResult) = _uiState.update {
        it.copy(
            formCity = result.city,
            formLatitude = result.latitude,
            formLongitude = result.longitude,
            showLocationSearch = false,
            locationResults = emptyList(),
        )
    }

    fun toggleAddForm() = _uiState.update {
        it.copy(
            showAddForm = !it.showAddForm,
            editingPartnerId = null,
            error = null,
            // Reset form when closing
            formName = if (!it.showAddForm) it.formName else "",
            formGender = if (!it.showAddForm) it.formGender else "",
            formDob = if (!it.showAddForm) it.formDob else "",
            formTime = if (!it.showAddForm) it.formTime else "",
            formCity = if (!it.showAddForm) it.formCity else "",
            formLatitude = if (!it.showAddForm) it.formLatitude else 0.0,
            formLongitude = if (!it.showAddForm) it.formLongitude else 0.0,
            formBirthTimeUnknown = if (!it.showAddForm) it.formBirthTimeUnknown else false,
            formGuardianConsentGiven = if (!it.showAddForm) it.formGuardianConsentGiven else false,
            formForCompatibility = if (!it.showAddForm) it.formForCompatibility else true,
        )
    }
}
