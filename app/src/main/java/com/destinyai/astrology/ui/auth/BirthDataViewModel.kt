package com.destinyai.astrology.ui.auth

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.destinyai.astrology.R
import com.destinyai.astrology.data.local.prefs.UserPreferences
import com.destinyai.astrology.data.location.LocationSearchResult
import com.destinyai.astrology.data.location.LocationSearchService
import com.destinyai.astrology.data.remote.AnalyticsConsentRequest
import com.destinyai.astrology.data.remote.AstroApiService
import com.destinyai.astrology.data.remote.BirthProfileDto
import com.destinyai.astrology.data.remote.CreatePartnerRequest
import com.destinyai.astrology.data.remote.LocationResult
import com.destinyai.astrology.data.remote.PartnerRequest
import com.destinyai.astrology.data.remote.ProfileRequest
import com.destinyai.astrology.data.remote.RegisterRequest
import com.destinyai.astrology.services.SoundManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject

data class BirthDataUiState(
    val userName: String = "",
    val dateOfBirth: String = "",
    val timeOfBirth: String = "12:00",
    val cityOfBirth: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    // iOS parity (BirthDataViewModel.swift:17): persist Google place_id to
    // disambiguate cities with identical names on re-resolution.
    val placeId: String? = null,
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
    val locationResults: List<LocationResult> = emptyList(),
    val isSearchingLocation: Boolean = false,
    // GAP-2: distinguish "no results" from "network/auth failure" so the
    // location sheet can render an error chip instead of a silent empty list.
    val locationErrorRes: Int? = null,
    val showResponseStyleSheet: Boolean = false,
    val analyticsConsent: Boolean = false,
    val showRefreshedBanner: Boolean = false,
    val isSoundEnabled: Boolean = false,
    // iOS parity (BirthDataView.swift:88-109): top-left back chevron only shown
    // for guest users. Registered users on a linear sign-up flow must not be
    // able to back-navigate to the AuthScreen.
    val isGuest: Boolean = false,
    // iOS parity (BirthDataView.swift:207-222): full-screen profile setup loader
    // shown after a successful save before navigating to home.
    val showProfileSetupLoading: Boolean = false,
)

@HiltViewModel
class BirthDataViewModel @Inject constructor(
    private val api: AstroApiService,
    private val prefs: UserPreferences,
    private val locationSearchService: LocationSearchService,
    private val soundManager: SoundManager,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BirthDataUiState())
    val uiState: StateFlow<BirthDataUiState> = _uiState

    private var locationSearchJob: Job? = null

    init {
        // iOS parity (SoundManager.swift:19-29): observe the source-of-truth
        // flow so the toggle stays in sync with AuthScreen and SoundManager.
        viewModelScope.launch {
            prefs.isSoundEnabledFlow().collect { enabled ->
                _uiState.update { it.copy(isSoundEnabled = enabled) }
            }
        }
    }

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

    fun setLocation(city: String, lat: Double, lng: Double, placeId: String? = null) =
        _uiState.update { it.copy(cityOfBirth = city, latitude = lat, longitude = lng, placeId = placeId) }

    fun toggleLocationSearch() = _uiState.update { it.copy(showLocationSearch = !it.showLocationSearch) }

    fun clearError() = _uiState.update { it.copy(error = null) }

    /**
     * iOS parity (BirthDataView.swift dismiss action on GuestSignInPromptView):
     * clear the birth-data conflict state so the user can return to the
     * BirthDataScreen and try again.
     */
    fun clearBirthDataConflict() = _uiState.update {
        it.copy(birthDataTakenEmail = null, birthDataTakenProvider = null)
    }

    fun searchLocation(query: String) {
        locationSearchJob?.cancel()
        if (query.length < 2) {
            _uiState.update {
                it.copy(
                    locationResults = emptyList(),
                    isSearchingLocation = false,
                    locationErrorRes = null,
                )
            }
            return
        }
        locationSearchJob = viewModelScope.launch {
            delay(300)
            _uiState.update { it.copy(isSearchingLocation = true, locationErrorRes = null) }
            when (val result = locationSearchService.search(query)) {
                is LocationSearchResult.Success -> {
                    _uiState.update {
                        it.copy(
                            locationResults = result.results,
                            isSearchingLocation = false,
                            locationErrorRes = null,
                        )
                    }
                }
                is LocationSearchResult.Failure -> {
                    val msgRes = when (result.reason) {
                        LocationSearchResult.Reason.Network ->
                            R.string.location_search_error_network
                        LocationSearchResult.Reason.Auth ->
                            R.string.location_search_error_auth
                        LocationSearchResult.Reason.Server,
                        LocationSearchResult.Reason.Unknown ->
                            R.string.location_search_error_generic
                    }
                    _uiState.update {
                        it.copy(
                            locationResults = emptyList(),
                            isSearchingLocation = false,
                            locationErrorRes = msgRes,
                        )
                    }
                }
            }
        }
    }

    fun clearLocationResults() = _uiState.update {
        it.copy(locationResults = emptyList(), isSearchingLocation = false, locationErrorRes = null)
    }

    fun dismissResponseStyle() = _uiState.update {
        // iOS parity (BirthDataView.swift:191-205): when ResponseStylePicker dismisses,
        // present ProfileSetupLoadingView before navigating home.
        it.copy(showResponseStyleSheet = false, showProfileSetupLoading = true)
    }

    /**
     * iOS parity (BirthDataView.swift:208-218 ProfileSetupLoadingView.onComplete):
     * after the loader finishes, mark hasBirthData=true and dismiss the loader so
     * AppRoot navigates to MainTab. The view layer observes [BirthDataUiState.isSaved]
     * to fire its onSaved callback.
     */
    fun completeProfileSetupLoading() = _uiState.update {
        it.copy(showProfileSetupLoading = false, isSaved = true)
    }

    fun dismissRefreshedBanner() {
        viewModelScope.launch {
            prefs.setBackendDataRefreshed(false)
            _uiState.update { it.copy(showRefreshedBanner = false) }
        }
    }

    fun setAnalyticsConsent(consent: Boolean) {
        viewModelScope.launch {
            prefs.setAnalyticsConsent(consent)
            _uiState.update { it.copy(analyticsConsent = consent) }
        }
    }

    fun toggleSound() {
        viewModelScope.launch {
            // iOS parity (SoundManager.swift:341-347): delegate to SoundManager
            // so audio state updates immediately, not on next play().
            val newVal = soundManager.toggleSound()
            _uiState.update { it.copy(isSoundEnabled = newVal) }
        }
    }

    // Test helpers — allow clearing selection state without re-triggering setters
    internal fun clearDateSelected() = _uiState.update { it.copy(isDateSelected = false) }
    internal fun clearTimeSelected() = _uiState.update { it.copy(isTimeSelected = false) }

    /**
     * iOS parity (BirthDataView.swift:148-158 sheet onDismiss): the date picker
     * sheet on iOS marks the date as selected when ANY dismissal occurs (OK,
     * cancel, swipe-down). On Android the dialog onDismissListener fires for
     * cancel/back as well as OK, so we mirror that semantic here.
     */
    fun markDateSelected() {
        _uiState.update { it.copy(isDateSelected = true) }
    }

    /** iOS parity (BirthDataView.swift:159-169 sheet onDismiss). */
    fun markTimeSelected() {
        _uiState.update { it.copy(isTimeSelected = true) }
    }

    fun loadSaved() {
        viewModelScope.launch {
            val refreshed = prefs.getBackendDataRefreshed()
            // iOS parity (BirthDataView.swift:140-143): banner is shown once,
            // and the persisted flag is cleared immediately on first read so
            // it never resurfaces after a re-composition or backgrounding.
            if (refreshed) prefs.setBackendDataRefreshed(false)
            val savedConsent = prefs.getAnalyticsConsent()
            val soundEnabled = prefs.isSoundEnabled()
            val profile = prefs.getBirthProfile()
            val isGuest = prefs.isGuestUser()
            // iOS parity (BirthDataViewModel.swift:117-124): only auto-populate name
            // from sign-in for non-guest users. Guests must enter their own name.
            val storedName = prefs.getUserName().orEmpty()
            val name = if (!isGuest && storedName.isNotBlank() && storedName != "Guest") storedName else ""
            if (profile != null) {
                _uiState.update {
                    it.copy(
                        userName = name,
                        dateOfBirth = profile.dateOfBirth,
                        timeOfBirth = profile.timeOfBirth,
                        cityOfBirth = profile.cityOfBirth,
                        latitude = profile.latitude,
                        longitude = profile.longitude,
                        placeId = profile.placeId,
                        gender = profile.gender ?: "",
                        timeUnknown = profile.birthTimeUnknown,
                        isDateSelected = profile.dateOfBirth.isNotBlank(),
                        isTimeSelected = profile.timeOfBirth.isNotBlank() && !profile.birthTimeUnknown,
                        showRefreshedBanner = refreshed,
                        analyticsConsent = savedConsent,
                        isSoundEnabled = soundEnabled,
                        isGuest = isGuest,
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        userName = name,
                        showRefreshedBanner = refreshed,
                        analyticsConsent = savedConsent,
                        isSoundEnabled = soundEnabled,
                        isGuest = isGuest,
                    )
                }
            }
        }
    }

    fun save() {
        viewModelScope.launch {
            // iOS parity (BirthDataView.swift:451-454): SoundManager.shared.playButtonTap()
            // fires before the network/save flow so the user feels the commit immediately.
            soundManager.playButtonTap()
            val s = _uiState.value
            if (s.cityOfBirth.isBlank()) {
                _uiState.update { it.copy(error = context.getString(R.string.birth_data_please_select_city)) }
                return@launch
            }
            if (s.latitude == 0.0 && s.longitude == 0.0) {
                _uiState.update { it.copy(error = context.getString(R.string.birth_data_please_select_valid_city)) }
                return@launch
            }

            _uiState.update { it.copy(isLoading = true, error = null) }

            try {
                val isGuest = prefs.isGuestUser()
                // iOS parity (BirthDataViewModel.swift:94-107 effectiveEmail):
                // generate a deterministic guest email from birth components when
                // the stored email is empty so guests still have a stable identity.
                val storedEmail = prefs.getUserEmail().orEmpty()
                val email = if (storedEmail.isBlank() && isGuest) {
                    generateGuestEmail(
                        dob = s.dateOfBirth,
                        time = s.timeOfBirth,
                        city = s.cityOfBirth,
                        lat = s.latitude,
                        lng = s.longitude,
                    ).also { prefs.setUserEmail(it) }
                } else {
                    storedEmail
                }
                val profile = BirthProfileDto(
                    dateOfBirth = s.dateOfBirth,
                    timeOfBirth = s.timeOfBirth,
                    cityOfBirth = s.cityOfBirth.trim(),
                    latitude = s.latitude,
                    longitude = s.longitude,
                    gender = s.gender.ifEmpty { null },
                    birthTimeUnknown = s.timeUnknown,
                    placeId = s.placeId,
                )

                // iOS parity (BirthDataView.swift:497-535): call /subscription/register
                // BEFORE saveProfile to detect archived guests + early birth-data conflict.
                // Backend assigns plan based on isGeneratedEmail flag and surfaces 403/409.
                if (email.isNotBlank()) {
                    try {
                        api.register(
                            RegisterRequest(
                                email = email,
                                isGeneratedEmail = isGuest,
                            )
                        )
                    } catch (e: retrofit2.HttpException) {
                        when (e.code()) {
                            // iOS parity (QuotaManager.swift:535-545): 403 with detail.error="account_deleted"
                            // is a soft-deleted account — surface the long-form deleted-account string.
                            403 -> {
                                val errorJson = e.response()?.errorBody()?.string() ?: ""
                                if (extractJsonField(errorJson, "error") == "account_deleted") {
                                    _uiState.update {
                                        it.copy(
                                            isLoading = false,
                                            error = context.getString(R.string.account_deleted_error),
                                        )
                                    }
                                    return@launch
                                }
                                // Unknown 403 — fall through to saveProfile best-effort.
                            }
                            // iOS parity (QuotaManager.swift:510-533): 409 carries either
                            //   detail.error="archived_guest"   → upgraded_to_email + provider
                            //   detail.error="registered_user_conflict" → masked_email + provider
                            409 -> {
                                val errorJson = e.response()?.errorBody()?.string() ?: ""
                                val errorKind = extractJsonField(errorJson, "error")
                                val provider = extractJsonField(errorJson, "provider")
                                when (errorKind) {
                                    "archived_guest" -> {
                                        val upgraded = extractJsonField(errorJson, "upgraded_to_email")
                                        _uiState.update {
                                            it.copy(
                                                isLoading = false,
                                                birthDataTakenEmail = upgraded,
                                                birthDataTakenProvider = provider,
                                            )
                                        }
                                        return@launch
                                    }
                                    "registered_user_conflict" -> {
                                        val masked = extractJsonField(errorJson, "masked_email")
                                        _uiState.update {
                                            it.copy(
                                                isLoading = false,
                                                birthDataTakenEmail = masked,
                                                birthDataTakenProvider = provider,
                                            )
                                        }
                                        return@launch
                                    }
                                    else -> {
                                        // Legacy/unknown 409 — fall back to existing_email field.
                                        val conflictEmail = extractJsonField(errorJson, "existing_email")
                                        _uiState.update {
                                            it.copy(
                                                isLoading = false,
                                                birthDataTakenEmail = conflictEmail,
                                                birthDataTakenProvider = provider,
                                            )
                                        }
                                        return@launch
                                    }
                                }
                            }
                            // Other registration failures: continue to saveProfile (best-effort, matches iOS).
                            else -> Unit
                        }
                    } catch (_: Exception) {
                        // Non-HTTP error — continue to saveProfile (iOS parity: best-effort register).
                    }
                }

                // iOS parity (BirthDataView.swift:610-616): include apple_id / google_id
                // so backend can match users whose email is a placeholder.
                val appleId = prefs.getAppleUserId()
                val googleId = prefs.getGoogleUserId()

                api.saveProfile(
                    ProfileRequest(
                        email = email,
                        userName = s.userName.trim().ifEmpty { null },
                        userType = if (isGuest) "guest" else "registered",
                        isGeneratedEmail = isGuest,
                        birthProfile = profile,
                        appleId = appleId?.takeIf { it.isNotBlank() },
                        googleId = googleId?.takeIf { it.isNotBlank() },
                    )
                )

                prefs.setBirthProfile(profile)
                prefs.setHasBirthData(true)
                if (s.userName.isNotBlank()) prefs.setUserName(s.userName.trim())

                // iOS parity (BirthDataView.swift:572-576): for non-US users send the
                // analytics-consent choice to the backend. US users implicitly consent
                // (backend default). Best-effort: failure must not block navigation.
                val isUsLocale = Locale.getDefault().country.equals("US", ignoreCase = true)
                if (!isUsLocale && email.isNotBlank()) {
                    try {
                        api.updateAnalyticsConsent(
                            AnalyticsConsentRequest(email = email, consent = s.analyticsConsent)
                        )
                    } catch (_: Exception) {
                        // Best-effort — ignore consent-sync failure (iOS parity).
                    }
                }

                // iOS parity (BirthDataViewModel.swift:270-273):
                // post-save background sync of chat + compatibility history so a
                // returning user/device sees their past conversations restored.
                // Best-effort: failures must not block navigation. The Android equivalents
                // are not yet implemented; the call sites are reserved for them.
                viewModelScope.launch { runCatching { syncHistoryFromServer(email) } }

                // iOS parity (BirthDataViewModel.swift:277-291):
                // create the self-partner profile (is_self=true) so the user can switch
                // back to themselves in the compatibility flow.
                viewModelScope.launch { runCatching { createSelfPartnerProfile(email, s) } }

                val isFirstSave = prefs.getResponseStyle() == "guidance"
                // iOS parity (BirthDataView.swift:474): showResponseStylePicker is set
                // unconditionally after every successful save, not only on first save.
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isSaved = false,
                        showResponseStyleSheet = true,
                    )
                }
            } catch (e: retrofit2.HttpException) {
                when (e.code()) {
                    409 -> {
                        val errorJson = e.response()?.errorBody()?.string() ?: ""
                        val conflictEmail = extractJsonField(errorJson, "existing_email") ?: ""
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                birthDataTakenEmail = conflictEmail.ifEmpty { null },
                                birthDataTakenProvider = extractJsonField(errorJson, "provider"),
                            )
                        }
                        // RegisteredUserConflictError is the typed signal — state already updated
                    }
                    403 -> {
                        // iOS parity (BirthDataView.swift:525-531 AccountDeletedError):
                        // surface the long-form deleted-account message string.
                        _uiState.update { it.copy(isLoading = false, error = context.getString(R.string.account_deleted_error)) }
                        // AccountDeletedError is the typed signal — state already updated
                    }
                    else -> {
                        _uiState.update { it.copy(isLoading = false, error = context.getString(R.string.birth_data_save_failed)) }
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = context.getString(R.string.birth_data_save_failed)) }
            }
        }
    }

    /**
     * iOS parity (EmailGenerator.generateFromComponents): a deterministic guest email
     * derived from immutable birth components so a guest keeps the same identity
     * across reinstalls. Lowercased, alphanumeric-only, suffixed with @guest.destiny.ai.
     */
    private fun generateGuestEmail(
        dob: String,
        time: String,
        city: String,
        lat: Double,
        lng: Double,
    ): String {
        val seed = "$dob|$time|${city.trim().lowercase()}|" +
            "%.4f|%.4f".format(Locale.US, lat, lng)
        val token = seed.hashCode().toUInt().toString(16).padStart(8, '0')
        val cityToken = city.trim().lowercase().filter { it.isLetterOrDigit() }.take(6).ifEmpty { "guest" }
        return "guest_${cityToken}_$token@guest.destiny.ai"
    }

    /**
     * iOS parity (ChatHistorySyncService + CompatibilityHistoryService syncFromServer
     * fired post-save). The Android equivalents are not yet implemented; this method
     * is the dedicated hook where they will be wired in. Failures are swallowed by
     * the caller's runCatching — best-effort, must not block UI.
     */
    private suspend fun syncHistoryFromServer(email: String) {
        if (email.isBlank()) return
        // TODO(android-history-sync): pull /chat-history/threads/{email} and
        // /compatibility/history/{email} into local storage to mirror iOS behaviour.
        // Intentionally a no-op until the Android sync services land — kept here so
        // the call site exists at the iOS-equivalent moment in the save() flow.
    }

    /**
     * iOS parity (ProfileService.createSelfPartnerProfile, BirthDataViewModel.swift:277-291):
     * create a partner-profile row with is_self=true so the compatibility flow can
     * offer "switch to me" without re-entering birth data. Best-effort.
     */
    private suspend fun createSelfPartnerProfile(email: String, s: BirthDataUiState) {
        if (email.isBlank()) return
        api.addPartner(
            CreatePartnerRequest(
                userEmail = email,
                profile = PartnerRequest(
                    name = s.userName.trim().ifEmpty { "Me" },
                    gender = s.gender.ifEmpty { "" },
                    dateOfBirth = s.dateOfBirth,
                    timeOfBirth = s.timeOfBirth,
                    cityOfBirth = s.cityOfBirth.trim().ifEmpty { null },
                    latitude = s.latitude.takeIf { it != 0.0 },
                    longitude = s.longitude.takeIf { it != 0.0 },
                    birthTimeUnknown = s.timeUnknown,
                    isSelf = true,
                ),
                consentGiven = true,
            )
        )
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
