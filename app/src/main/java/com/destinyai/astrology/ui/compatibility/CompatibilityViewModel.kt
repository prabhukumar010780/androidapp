package com.destinyai.astrology.ui.compatibility

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.destinyai.astrology.BuildConfig
import com.destinyai.astrology.data.local.db.CompatibilityHistoryDao
import com.destinyai.astrology.data.local.db.CompatibilityHistoryEntity
import com.destinyai.astrology.data.local.prefs.UserPreferences
import com.destinyai.astrology.data.remote.AstroApiService
import com.destinyai.astrology.data.remote.BirthProfileDto
import com.destinyai.astrology.data.remote.CompatibilityBirthDetailsDto
import com.destinyai.astrology.data.remote.CompatibilityFollowUpRequest
import com.destinyai.astrology.data.remote.CompatibilityRequestDto
import com.destinyai.astrology.data.remote.CreatePartnerRequest
import com.destinyai.astrology.data.remote.PartnerRequest
import com.destinyai.astrology.data.remote.PredictBirthDataDto
import com.destinyai.astrology.data.remote.PredictRequest
import com.destinyai.astrology.data.remote.mapCompatibilityResponse
import com.destinyai.astrology.data.repository.CompatibilityRepository
import com.destinyai.astrology.data.repository.SseEvent
import com.destinyai.astrology.domain.model.AnalysisStep
import com.destinyai.astrology.domain.model.CompatibilityHistoryItem
import com.destinyai.astrology.domain.model.CompatibilityResult
import com.destinyai.astrology.domain.model.ComparisonResult
import com.destinyai.astrology.domain.model.PartnerData
import com.google.gson.Gson
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CompatibilityUiState(
    val personAName: String = "",
    val personALoaded: Boolean = false,
    val personADob: String = "",
    val personATime: String = "",
    val personACity: String = "",
    val personAGender: String = "",
    val personATimeUnknown: Boolean = false,
    val partnerName: String = "",
    val partnerDob: String = "",
    val partnerTime: String = "",
    val partnerCity: String = "",
    val partnerLatitude: Double = 0.0,
    val partnerLongitude: Double = 0.0,
    val partnerGender: String = "",
    val partnerTimeUnknown: Boolean = false,
    val savePartnerToBirthCharts: Boolean = false,
    val partnerFromSaved: Boolean = false,
    val showDatePicker: Boolean = false,
    val showTimePicker: Boolean = false,
    val showLocationSearch: Boolean = false,
    val showStreamingView: Boolean = false,
    val showComparisonOverview: Boolean = false,
    val showPaywall: Boolean = false,
    // iOS parity (CompatibilityView.swift signOutAndReauth + ChatViewModel.kt:55):
    // set when the user taps Sign In on the QuotaExhaustedDialog so the host can
    // consume the flag and navigate to AuthScreen exactly once.
    val navigateToAuth: Boolean = false,
    val showPartnerPicker: Boolean = false,
    val showDuplicateAlert: Boolean = false,
    val duplicateSessionId: String? = null,
    val result: String = "",
    val score: Int? = null,
    val isAnalyzing: Boolean = false,
    val error: String? = null,
    val isLoadingFromSaved: Boolean = false,
    val isPlus: Boolean = false,
) {
    val canAnalyze: Boolean
        get() = personALoaded &&
            partnerName.isNotBlank() &&
            partnerDob.isNotBlank() &&
            (partnerTime.isNotBlank() || partnerTimeUnknown) &&
            partnerCity.isNotBlank()
}

@HiltViewModel
class CompatibilityViewModel @Inject constructor(
    private val api: AstroApiService,
    private val prefs: UserPreferences,
    private val compatibilityRepo: CompatibilityRepository,
    private val historyDao: CompatibilityHistoryDao,
    private val chatRepository: com.destinyai.astrology.data.repository.ChatRepository,
    // iOS parity (ChatView.swift signOutAndReauth): used by requestSignInFromQuota
    // to perform a partial sign-out so AuthScreen routes to login UI without
    // bouncing back to Main.
    private val authRepository: com.destinyai.astrology.data.repository.AuthRepository,
    // iOS parity (CompatibilityView.swift:345-351 + CompatibilityViewModel.swift:178-210):
    // when the active profile flips, "You" reflects the active profile's name +
    // birth data, not the owner's. Bus injection lets us re-run loadUserData()
    // without relying on a screen-side LaunchedEffect(Unit) that fires once.
    private val profileChangeBus: com.destinyai.astrology.services.ProfileChangeBus,
    private val profileContextManager: com.destinyai.astrology.services.ProfileContextManager,
) : ViewModel() {

    init {
        // Reload "You" + saved-history bucket whenever the active profile flips.
        // Mirrors iOS CompatibilityView's `.onChange(of: activeProfileId)` path.
        viewModelScope.launch {
            profileChangeBus.events.collect { loadUserData() }
        }
    }

    private val gson = Gson()

    private val _uiState = MutableStateFlow(CompatibilityUiState())
    val uiState: StateFlow<CompatibilityUiState> = _uiState

    private var personAProfile: BirthProfileDto? = null
    private var personAEmail: String? = null

    // Current analysis step for streaming UI
    private val _currentStep = MutableStateFlow(AnalysisStep.CALCULATING_CHARTS)
    val currentStep: StateFlow<AnalysisStep> = _currentStep

    // Completed compatibility result (used by result screen + follow-up)
    private val _compatibilityResult = MutableStateFlow<CompatibilityResult?>(null)
    val compatibilityResult: StateFlow<CompatibilityResult?> = _compatibilityResult

    // Multi-partner comparison results
    private val _comparisonResults = MutableStateFlow<List<ComparisonResult>>(emptyList())
    val comparisonResults: StateFlow<List<ComparisonResult>> = _comparisonResults

    // iOS parity (CompatibilityViewModel.swift:87): brief "Loaded from history" toast flag.
    // Set true when a saved match is hydrated from local history without an API call;
    // the screen renders an animated transient toast and auto-clears after a short delay.
    private val _historyLoadedToast = MutableStateFlow(false)
    val historyLoadedToast: StateFlow<Boolean> = _historyLoadedToast

    fun showHistoryLoadedToast() {
        _historyLoadedToast.value = true
    }

    fun dismissHistoryLoadedToast() {
        _historyLoadedToast.value = false
    }

    // iOS parity (CompatibilityView.swift:218-227): expose the active profile id so the
    // partner picker can exclude it from saved-partner suggestions.
    val activeProfileId: StateFlow<String?> = prefs.activeProfileIdFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    // iOS parity (CompatibilityResultSheets.swift:1261-1269): response length selector
    // for the Ask Destiny chat. Reuses the global ResponseLengthManager prefs.
    val responseLength: kotlinx.coroutines.flow.Flow<String>
        get() = prefs.responseLengthFlow

    fun setResponseLength(value: String) {
        viewModelScope.launch { runCatching { prefs.setResponseLength(value) } }
    }

    /**
     * iOS parity (CompatibilityResultSheets.swift:1827-1890): submit a 1..5-star
     * inline rating for a compatibility-chat response. Best-effort — UI latches
     * the local thank-you state regardless of network outcome.
     */
    fun submitCompatRating(query: String, responseText: String, rating: Int) {
        viewModelScope.launch {
            runCatching {
                chatRepository.submitRating(
                    traceId = null,
                    sessionId = null,
                    userEmail = null,
                    query = query,
                    responseText = responseText,
                    rating = rating,
                )
            }
        }
    }

    fun loadUserData() {
        viewModelScope.launch {
            val email = prefs.getUserEmail() ?: return@launch
            // iOS parity (CompatibilityViewModel.swift:178-210): the "You" card
            // reflects the **active** profile (partner when one is selected),
            // not the owner. Resolve via ProfileContextManager so partner birth
            // data + name are sent to the Ashtakoot endpoint, not the owner's.
            val profile = profileContextManager.activeBirthData() ?: return@launch
            val name = profileContextManager.activeProfileName().ifBlank {
                prefs.getUserName().orEmpty()
            }
            personAProfile = profile
            personAEmail = email
            _uiState.update {
                it.copy(
                    personAName = name,
                    personALoaded = true,
                    personADob = profile.dateOfBirth,
                    personATime = profile.timeOfBirth,
                    personACity = profile.cityOfBirth,
                    personAGender = profile.gender ?: "",
                    personATimeUnknown = profile.birthTimeUnknown,
                )
            }
            // Load history so cache lookup in analyze() works (parity with iOS).
            loadHistory()
            // iOS parity (CompatibilityView.swift:289-315): when launched under
            // UI_TEST_MODE with E2E_PARTNER_* extras, pre-fill the partner form
            // so Appium tests can skip the location-search + date-picker dance.
            applyE2EPartnerOverridesIfPresent()
        }
    }

    /**
     * Debug-only E2E hook that mirrors the iOS `UI_TEST_MODE` pre-fill block in
     * CompatibilityView.swift. Reads partner fields stashed by MainActivity
     * (which copies them out of the launch intent's extras) and pushes them
     * into the form state so the analyze button is immediately tappable.
     *
     * Stripped from release builds via `BuildConfig.DEBUG` and a no-op when
     * UI_TEST_MODE is not set. Production code paths are unaffected.
     */
    private fun applyE2EPartnerOverridesIfPresent() {
        if (!BuildConfig.DEBUG) return
        val overrides = E2EPartnerOverrides.consume() ?: return
        if (overrides.name.isBlank()) return
        _uiState.update {
            it.copy(
                partnerName = overrides.name,
                partnerDob = overrides.dob.ifBlank { it.partnerDob },
                partnerTime = overrides.time.ifBlank { it.partnerTime },
                partnerCity = overrides.city.ifBlank { it.partnerCity },
                partnerLatitude = overrides.latitude.takeIf { v -> v != 0.0 } ?: it.partnerLatitude,
                partnerLongitude = overrides.longitude.takeIf { v -> v != 0.0 } ?: it.partnerLongitude,
                partnerTimeUnknown = false,
            )
        }
    }

    fun setPartnerName(name: String) = _uiState.update { it.copy(partnerName = name) }
    fun setPartnerDob(dob: String) = _uiState.update { it.copy(partnerDob = dob) }
    fun setPartnerTime(time: String) = _uiState.update { it.copy(partnerTime = time) }

    /**
     * Geocode a free-text city query via the backend's location search endpoint.
     * Returns Triple(displayName, lat, lon) on success, or null on no result / error.
     */
    suspend fun searchLocation(query: String): Triple<String, Double, Double>? {
        if (query.isBlank()) return null
        return try {
            val results = api.searchLocations(query.trim())
            val first = results.firstOrNull() ?: return null
            Triple(
                first.displayName.ifBlank { first.city },
                first.latitude,
                first.longitude,
            )
        } catch (_: Exception) {
            null
        }
    }
    fun setPartnerLocation(city: String, lat: Double, lon: Double) =
        _uiState.update { it.copy(partnerCity = city, partnerLatitude = lat, partnerLongitude = lon) }
    fun setPartnerGender(gender: String) = _uiState.update { it.copy(partnerGender = gender) }
    fun setPartnerTimeUnknown(unknown: Boolean) = _uiState.update { it.copy(partnerTimeUnknown = unknown) }
    fun setShowDatePicker(show: Boolean) = _uiState.update { it.copy(showDatePicker = show) }
    fun setShowTimePicker(show: Boolean) = _uiState.update { it.copy(showTimePicker = show) }
    fun setShowLocationSearch(show: Boolean) = _uiState.update { it.copy(showLocationSearch = show) }
    fun setShowComparisonOverview(show: Boolean) = _uiState.update { it.copy(showComparisonOverview = show) }
    fun dismissPaywall() = _uiState.update { it.copy(showPaywall = false) }
    fun showPaywallSheet() = _uiState.update { it.copy(showPaywall = true) }
    fun dismissError() = _uiState.update { it.copy(error = null) }

    /**
     * Mirrors iOS ChatView.swift signOutAndReauth + CompatibilityView.swift onSignIn:
     * partial sign-out (preserves birth data) so AuthScreen routes to login UI instead
     * of bouncing back to Main.
     */
    fun requestSignInFromQuota() {
        viewModelScope.launch {
            runCatching { authRepository.signOutPreserveBirthData() }
            _uiState.update { it.copy(showPaywall = false, navigateToAuth = true) }
        }
    }

    fun consumeNavigateToAuth() {
        _uiState.update { it.copy(navigateToAuth = false) }
    }
    fun setSavePartnerToBirthCharts(save: Boolean) = _uiState.update { it.copy(savePartnerToBirthCharts = save) }
    fun dismissDuplicateAlert() = _uiState.update { it.copy(showDuplicateAlert = false, duplicateSessionId = null) }
    fun showDuplicateAlert(sessionId: String) = _uiState.update { it.copy(showDuplicateAlert = true, duplicateSessionId = sessionId) }

    /** Reset the partner form back to its blank state. */
    fun resetPartnerForm() {
        _compatibilityResult.value = null  // clear result so a re-analysis always hits fresh API
        _uiState.update {
            it.copy(
                partnerName = "",
                partnerDob = "",
                partnerTime = "",
                partnerCity = "",
                partnerLatitude = 0.0,
                partnerLongitude = 0.0,
                partnerGender = "",
                partnerTimeUnknown = false,
                savePartnerToBirthCharts = false,
                partnerFromSaved = false,
                result = "",
                score = null,
                error = null,
            )
        }
    }

    /**
     * Check whether the current partner form values match an already-saved history entry.
     * Returns the matching sessionId or null when there is no duplicate.
     */
    fun checkForDuplicate(): String? {
        val s = _uiState.value
        if (s.partnerFromSaved) return null
        return _historyItems.value.firstOrNull { item ->
            item.girlDob == s.partnerDob &&
                item.girlTime == s.partnerTime &&
                item.girlCity.equals(s.partnerCity, ignoreCase = true)
        }?.sessionId
    }

    fun showPartnerPicker() = _uiState.update { it.copy(showPartnerPicker = true) }

    fun dismissPartnerPicker() = _uiState.update { it.copy(showPartnerPicker = false) }

    /**
     * Mirrors iOS CompatibilityView .onChange(of: viewModel.girl*) blocks: when the user
     * edits any partner field after loading from a saved chart, drop the "from saved" flag
     * and re-enable the "save partner" checkbox so the modified copy can be persisted.
     */
    fun markPartnerEdited() {
        val s = _uiState.value
        if (!s.partnerFromSaved) return
        _uiState.update { it.copy(partnerFromSaved = false, savePartnerToBirthCharts = true) }
    }

    fun selectSavedPartner(partner: com.destinyai.astrology.data.remote.PartnerDto) {
        _uiState.update {
            it.copy(
                partnerName = partner.name,
                partnerDob = partner.dateOfBirth ?: "",
                partnerTime = partner.timeOfBirth ?: "",
                partnerCity = partner.cityOfBirth ?: "",
                partnerLatitude = partner.latitude ?: 0.0,
                partnerLongitude = partner.longitude ?: 0.0,
                partnerGender = partner.gender,
                partnerTimeUnknown = partner.birthTimeUnknown,
                partnerFromSaved = true,
                showPartnerPicker = false,
            )
        }
    }

    // Saved partners list for picker sheet
    private val _savedPartners = MutableStateFlow<List<com.destinyai.astrology.data.remote.PartnerDto>>(emptyList())
    val savedPartners: StateFlow<List<com.destinyai.astrology.data.remote.PartnerDto>> = _savedPartners

    private val _isSavedPartnersLoading = MutableStateFlow(false)
    val isSavedPartnersLoading: StateFlow<Boolean> = _isSavedPartnersLoading

    fun loadSavedPartners() {
        viewModelScope.launch {
            val email = personAEmail ?: prefs.getUserEmail() ?: return@launch
            _isSavedPartnersLoading.value = true
            try {
                _savedPartners.value = api.listPartners(email)
            } catch (_: Exception) {
                // Silently fail — empty list shown
            } finally {
                _isSavedPartnersLoading.value = false
            }
        }
    }

    fun loadSavedPartner(item: CompatibilityHistoryItem) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingFromSaved = true) }
            _uiState.update {
                it.copy(
                    partnerName = item.girlName,
                    partnerDob = item.girlDob,
                    partnerTime = item.girlTime,
                    partnerCity = item.girlCity,
                    partnerGender = "female",
                )
            }
            _uiState.update { it.copy(isLoadingFromSaved = false) }
        }
    }

    /**
     * Pre-flight quota gate that mirrors iOS QuotaManager.canAccessFeature(.compatibility, ...).
     * Returns true when the user may proceed; otherwise updates UI state with the appropriate
     * banner / paywall marker (matching iOS string contracts) and returns false.
     */
    private suspend fun checkCompatibilityQuota(email: String, count: Int = 1): Boolean {
        return try {
            val authHeader = "Bearer ${BuildConfig.API_KEY}"
            val access = api.canAccessFeature(authHeader, email, "compatibility", count)
            if (access.allowed) {
                true
            } else {
                when (access.reason) {
                    "daily_limit_reached" -> {
                        val msg = access.resets_at?.let { "Daily limit reached. Resets at $it." }
                            ?: "Daily limit reached. Resets tomorrow."
                        _uiState.update {
                            it.copy(isAnalyzing = false, showStreamingView = false, error = msg)
                        }
                    }
                    "overall_limit_reached" -> {
                        // iOS parity (CompatibilityViewModel.swift:501-507): set marker only —
                        // the View renders QuotaExhaustedDialog (interstitial) which routes to
                        // SubscriptionScreen on Upgrade tap. Do NOT pre-set showPaywall — that
                        // would skip the interstitial and jump straight to the subscription view.
                        val marker = if (email.contains("guest") || email.contains("@gen.com")) {
                            "FREE_LIMIT_GUEST"
                        } else {
                            "FREE_LIMIT_REGISTERED"
                        }
                        _uiState.update {
                            it.copy(
                                isAnalyzing = false,
                                showStreamingView = false,
                                showPaywall = false,
                                error = marker,
                            )
                        }
                    }
                    else -> {
                        // upgrade_required / feature_upgrade_required / unknown -> interstitial sheet
                        // mirrors iOS QuotaExhaustedView (CompatibilityViewModel.swift:508-511).
                        _uiState.update {
                            it.copy(
                                isAnalyzing = false,
                                showStreamingView = false,
                                showPaywall = false,
                                error = "FEATURE_UPGRADE_REQUIRED",
                            )
                        }
                    }
                }
                false
            }
        } catch (e: Exception) {
            // Match iOS behaviour: log and proceed; backend will still enforce the cap server-side.
            true
        }
    }

    fun analyze() {
        viewModelScope.launch {
            val s = _uiState.value
            if (!s.canAnalyze) return@launch
            val profile = personAProfile ?: return@launch
            val email = personAEmail ?: return@launch

            // STEP 1: Local cache lookup — if matching match exists, load from history (FREE, no API call)
            // Mirrors iOS CompatibilityHistoryService.findExistingMatch behaviour.
            // Also match on girlName to prevent cross-contamination when different partners
            // share the same DOB/time/city (e.g. testing with multiple people).
            val cached = _historyItems.value.firstOrNull { item ->
                item.boyDob == profile.dateOfBirth &&
                    item.boyTime == profile.timeOfBirth &&
                    item.boyCity.equals(profile.cityOfBirth, ignoreCase = true) &&
                    item.girlDob == s.partnerDob &&
                    item.girlTime == s.partnerTime &&
                    item.girlCity.equals(s.partnerCity, ignoreCase = true) &&
                    item.girlName.equals(s.partnerName, ignoreCase = true)
            }
            if (cached?.result != null) {
                // Re-derive isCancelledByExceptions from mangalCompatibility so cached results
                // correctly show exceptions even if stored before this field was introduced.
                val jointCancels = cached.result.mangalCompatibility?.get("cancellation_occurs") as? Boolean ?: false
                val fixedResult = if (jointCancels) {
                    cached.result.copy(
                        mangalBoyData = cached.result.mangalBoyData?.takeIf { it.hasMangalDosha }
                            ?.copy(isCancelledByExceptions = true) ?: cached.result.mangalBoyData,
                        mangalGirlData = cached.result.mangalGirlData?.takeIf { it.hasMangalDosha }
                            ?.copy(isCancelledByExceptions = true) ?: cached.result.mangalGirlData,
                    )
                } else cached.result
                _compatibilityResult.value = fixedResult
                _uiState.update {
                    it.copy(
                        result = fixedResult.summary,
                        score = fixedResult.totalScore,
                        isAnalyzing = false,
                        showStreamingView = false,
                    )
                }
                // iOS parity (CompatibilityViewModel.swift:466): toast on cache-hit re-open
                _historyLoadedToast.value = true
                return@launch
            }

            // Pre-flight quota check before invoking streaming analysis (parity with iOS QuotaManager)
            if (!checkCompatibilityQuota(email)) return@launch

            _uiState.update { it.copy(isAnalyzing = true, error = null, showStreamingView = true) }
            _currentStep.value = AnalysisStep.CALCULATING_CHARTS

            val appLanguage = prefs.getSelectedLanguage()
            val activeProfileId = prefs.getActiveProfileId()
            // Round coordinates to 6 decimal places (backend Pydantic validator requirement) — parity with iOS
            val roundedBoyLat = Math.round(profile.latitude * 1_000_000.0) / 1_000_000.0
            val roundedBoyLon = Math.round(profile.longitude * 1_000_000.0) / 1_000_000.0
            val roundedGirlLat = Math.round(s.partnerLatitude * 1_000_000.0) / 1_000_000.0
            val roundedGirlLon = Math.round(s.partnerLongitude * 1_000_000.0) / 1_000_000.0
            // Mint sessionId client-side so follow-up Ask Destiny can link to this analysis (parity with iOS)
            val mintedSessionId = "sess_${System.currentTimeMillis()}"
            currentSessionId = mintedSessionId
            val request = CompatibilityRequestDto(
                boy = CompatibilityBirthDetailsDto(
                    dob = profile.dateOfBirth,
                    time = if (profile.birthTimeUnknown) "12:00" else profile.timeOfBirth,
                    lat = roundedBoyLat,
                    lon = roundedBoyLon,
                    name = s.personAName,
                    place = profile.cityOfBirth,
                ),
                girl = CompatibilityBirthDetailsDto(
                    dob = s.partnerDob,
                    time = if (s.partnerTimeUnknown) "12:00" else s.partnerTime,
                    lat = roundedGirlLat,
                    lon = roundedGirlLon,
                    name = s.partnerName,
                    place = s.partnerCity,
                ),
                sessionId = mintedSessionId,
                userEmail = email,
                language = appLanguage,
                profileId = activeProfileId,
            )

            try {
                compatibilityRepo.streamAnalysis(request).collect { event ->
                    when (event) {
                        is SseEvent.Step -> _currentStep.value = mapStepName(event.stepName)
                        is SseEvent.FinalJson -> {
                            val result = try {
                                mapCompatibilityResponse(
                                    json = event.json,
                                    boyName = s.personAName,
                                    girlName = s.partnerName,
                                    boyDob = profile.dateOfBirth,
                                    girlDob = s.partnerDob,
                                    boyCity = profile.cityOfBirth,
                                    girlCity = s.partnerCity,
                                )
                            } catch (e: Exception) {
                                Log.e(
                                    "CompatVM",
                                    "mapCompatibilityResponse failed: ${e.message}",
                                    e,
                                )
                                _uiState.update {
                                    it.copy(
                                        isAnalyzing = false,
                                        showStreamingView = false,
                                        error = "Failed to parse compatibility response: ${e.message ?: e.javaClass.simpleName}",
                                    )
                                }
                                return@collect
                            }
                            _compatibilityResult.value = result
                            saveToHistory(result, email, profile, s)
                            // R2-CM5: persist partner to user's birth charts when checkbox is set
                            if (s.savePartnerToBirthCharts && !s.partnerFromSaved) {
                                savePartnerToBirthCharts(email, s)
                            }
                            _uiState.update {
                                it.copy(
                                    result = result.summary,
                                    score = result.totalScore,
                                    isAnalyzing = false,
                                    showStreamingView = false,
                                )
                            }
                        }
                        is SseEvent.Error -> {
                            // iOS parity (CompatibilityViewModel.swift:601-606 + StreamingPredictionService:192-207):
                            // SSE quota errors route to the QuotaExhaustedDialog interstitial, NOT a red banner.
                            val email = prefs.getUserEmail() ?: ""
                            val marker = when (event.reason) {
                                "daily_limit_reached" -> null // banner
                                "overall_limit_reached" ->
                                    if (email.contains("guest") || email.contains("@gen.com")) "FREE_LIMIT_GUEST"
                                    else "FREE_LIMIT_REGISTERED"
                                "quota_exceeded", "upgrade_required", "feature_not_available" -> "FEATURE_UPGRADE_REQUIRED"
                                else -> null
                            }
                            _uiState.update {
                                it.copy(
                                    isAnalyzing = false,
                                    showStreamingView = false,
                                    showPaywall = false,
                                    error = marker ?: event.message,
                                )
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("CompatVM", "analyze() collector threw: ${e.message}", e)
                _uiState.update { it.copy(isAnalyzing = false, showStreamingView = false, error = e.message ?: "Analysis failed") }
            }
        }
    }

    private fun mapStepName(stepName: String): AnalysisStep = when (stepName) {
        "calculate_charts" -> AnalysisStep.CALCULATING_CHARTS
        "ashtakoot", "ashtakoot_matching" -> AnalysisStep.ASHTAKOOT_MATCHING
        "mangal_compat", "mangal_dosha" -> AnalysisStep.MANGAL_DOSHA
        "yoga_kalsarpa", "formatting" -> AnalysisStep.COLLECTING_YOGAS
        "generating_analysis", "llm" -> AnalysisStep.GENERATING_ANALYSIS
        else -> _currentStep.value
    }

    fun clearResult() {
        _uiState.update { it.copy(result = "", score = null, error = null) }
        _compatibilityResult.value = null
    }

    // -- Compatibility follow-up (Ask Destiny chat)
    private var currentSessionId: String? = null
    private var currentCompatibilityResult: CompatibilityResult? = null

    /**
     * iOS parity (CompatibilityResultSheets.swift:1893-1913 CompatChatMessage):
     * carries timestamp + executionTimeMs metadata so the chat bubble can render
     * the metadata footer (time · exec · copy · stars). [isInfo] flags the
     * redirect/info placeholder so the bubble can render a cosmic progress
     * indicator instead of plain text.
     */
    data class FollowUpMessage(
        val text: String,
        val isUser: Boolean,
        val suggestions: List<String> = emptyList(),
        val timestampMs: Long = System.currentTimeMillis(),
        val executionTimeMs: Long = 0L,
        val isInfo: Boolean = false,
    )

    private val _followUpMessages = MutableStateFlow<List<FollowUpMessage>>(emptyList())
    val followUpMessages: StateFlow<List<FollowUpMessage>> = _followUpMessages

    private val _isFollowUpLoading = MutableStateFlow(false)
    val isFollowUpLoading: StateFlow<Boolean> = _isFollowUpLoading

    /**
     * iOS parity (CompatibilityResultSheets.swift:1056-1073): inline error banner
     * shown above the input bar; user taps to dismiss.
     */
    private val _followUpError = MutableStateFlow<String?>(null)
    val followUpError: StateFlow<String?> = _followUpError

    fun dismissFollowUpError() { _followUpError.value = null }
    fun setFollowUpError(message: String) { _followUpError.value = message }

    fun setSessionId(sessionId: String) { currentSessionId = sessionId }

    fun setCompatibilityResult(result: CompatibilityResult) { currentCompatibilityResult = result }

    fun clearFollowUpMessages() { _followUpMessages.value = emptyList() }

    /**
     * iOS parity (CompatibilityResultSheets.swift:1112-1154 loadStoredMessages):
     * load chat history for the current session, trying both raw and `compat_`
     * prefixed sessionIds. Filters out the initial compatibility report row
     * (markdown table or KEY STRENGTHS marker) so the chat starts at the user's
     * first follow-up question.
     */
    fun loadStoredFollowUpMessages() {
        val sessionId = currentSessionId ?: return
        val prefixed = if (sessionId.startsWith("compat_")) sessionId else "compat_$sessionId"
        val raw = readFollowUpHistory(prefixed) ?: readFollowUpHistory(sessionId)
        if (raw.isNullOrEmpty()) return
        val msgs = runCatching {
            gson.fromJson(raw, Array<FollowUpMessage>::class.java).toList()
        }.getOrNull().orEmpty()
        if (msgs.isEmpty()) return
        val filtered = msgs.filter { m ->
            !(m.text.contains("---|") ||
                m.text.contains("|---") ||
                m.text.contains("KEY STRENGTHS"))
        }
        _followUpMessages.value = filtered
    }

    /**
     * iOS parity (CompatibilityResultSheets.swift:1156-1168 saveMessagesToHistory):
     * persist follow-up chat under prefixed sessionId. Best-effort.
     */
    private fun saveFollowUpHistory() {
        val sessionId = currentSessionId ?: return
        val prefixed = if (sessionId.startsWith("compat_")) sessionId else "compat_$sessionId"
        runCatching { writeFollowUpHistory(prefixed, gson.toJson(_followUpMessages.value)) }
    }

    /**
     * In-memory + optional caller-supplied persistence for follow-up chat
     * history. Mirrors the chat-restore-within-session behaviour iOS gets via
     * CompatibilityHistoryService — full cross-process durability can be added
     * later by wiring a SharedPreferences-backed reader/writer through
     * [setFollowUpHistoryPersistence] without changing this VM.
     */
    private val followUpHistoryStore = mutableMapOf<String, String>()
    private var followUpHistoryReader: ((String) -> String?)? = null
    private var followUpHistoryWriter: ((String, String) -> Unit)? = null

    fun setFollowUpHistoryPersistence(
        reader: (String) -> String?,
        writer: (String, String) -> Unit,
    ) {
        followUpHistoryReader = reader
        followUpHistoryWriter = writer
    }

    private fun readFollowUpHistory(key: String): String? {
        followUpHistoryStore[key]?.let { return it }
        return followUpHistoryReader?.invoke(key)?.also { followUpHistoryStore[key] = it }
    }

    private fun writeFollowUpHistory(key: String, value: String) {
        followUpHistoryStore[key] = value
        followUpHistoryWriter?.invoke(key, value)
    }

    fun sendFollowUp(query: String) {
        viewModelScope.launch {
            val email = personAEmail ?: return@launch
            val sessionId = currentSessionId ?: return@launch
            if (query.isBlank()) return@launch
            // iOS parity (CompatibilityResultSheets.swift:1346-1348): optimistic
            // user-message append — rolled back below on quota/limit errors so
            // the banner does not flash above a stale transcript entry.
            val userMessage = FollowUpMessage(query, isUser = true)
            _followUpMessages.update { it + userMessage }
            _isFollowUpLoading.value = true
            val startMs = System.currentTimeMillis()
            try {
                val response = api.compatibilityFollowUp(
                    CompatibilityFollowUpRequest(
                        query = query,
                        sessionId = sessionId,
                        userEmail = email,
                        responseLength = runCatching { prefs.getResponseLength() }.getOrNull(),
                        responseStyle = runCatching { prefs.getResponseStyle() }.getOrNull(),
                    )
                )
                val elapsed = System.currentTimeMillis() - startMs
                val rawStatus = response.status
                if (rawStatus == "redirect" || rawStatus == "redirect_no_data") {
                    handleFollowUpRedirect(target = response.target, email = email, redirectQuery = response.redirectQuery)
                } else if (response.answer != null) {
                    val answer = response.answer
                    val cleaned = stripFollowUpQuestionsBlock(answer)
                    val embedded = extractFollowUpQuestions(answer)
                    val apiSuggestions = response.followUpSuggestions ?: emptyList()
                    val suggestions = if (apiSuggestions.isNotEmpty()) apiSuggestions else embedded
                    _followUpMessages.update {
                        it + FollowUpMessage(
                            text = cleaned,
                            isUser = false,
                            suggestions = suggestions,
                            executionTimeMs = elapsed,
                        )
                    }
                    saveFollowUpHistory()
                } else if (response.message != null) {
                    val msg = response.message
                    // iOS parity (CompatibilityResultSheets.swift:1417-1424):
                    // when the backend leaks a redirect through the message body
                    // (e.g. "None's chart", "birth details"), fall through to
                    // local data lookup using boy as the default target.
                    val looksLikeRedirect = msg.contains("Redirecting", ignoreCase = true) ||
                        msg.contains("individual analysis", ignoreCase = true) ||
                        msg.contains("None's chart", ignoreCase = true) ||
                        msg.contains("birth details", ignoreCase = true)
                    val result = currentCompatibilityResult
                    if (looksLikeRedirect && result != null) {
                        handleFollowUpRedirectWithLocalData(target = result.boyName, email = email, redirectQuery = null)
                    } else {
                        _followUpMessages.update {
                            it + FollowUpMessage(
                                text = msg,
                                isUser = false,
                                isInfo = true,
                                executionTimeMs = elapsed,
                            )
                        }
                        saveFollowUpHistory()
                    }
                }
            } catch (e: Exception) {
                val msg = (e.message ?: "").lowercase()
                val isQuota = msg.contains("quota") || msg.contains("limit") ||
                    msg.contains("maximum free")
                if (isQuota) {
                    _followUpMessages.update { list -> list.filter { it !== userMessage } }
                }
                _followUpError.value = "Failed to get response. Please try again."
            } finally {
                _isFollowUpLoading.value = false
            }
        }
    }

    private suspend fun handleFollowUpRedirect(target: String?, email: String, redirectQuery: String? = null) {
        val result = currentCompatibilityResult ?: return
        val (displayName, isGirl) = resolveRedirectTarget(target, result.boyName, result.girlName)
        runRedirectPredict(displayName = displayName, isGirl = isGirl, email = email, redirectQuery = redirectQuery)
    }

    /**
     * iOS parity (CompatibilityResultSheets.swift:1437-1451 handleRedirectWithLocalData):
     * fall-through path used when the backend leaks a redirect through the
     * message body instead of the status field. Uses the local result/profile
     * pair (Android equivalent of iOS analysisData).
     */
    private suspend fun handleFollowUpRedirectWithLocalData(
        target: String,
        email: String,
        redirectQuery: String?,
    ) {
        val result = currentCompatibilityResult ?: return
        val (displayName, isGirl) = resolveRedirectTarget(target, result.boyName, result.girlName)
        runRedirectPredict(displayName = displayName, isGirl = isGirl, email = email, redirectQuery = redirectQuery)
    }

    /**
     * iOS parity (CompatibilityResultSheets.swift:1454-1484): multi-token
     * resolver — matches "boy"/"his"/"him" → boy, "girl"/"her"/"she" → girl, and
     * also accepts the actual first name (or a prefix of it) on either side.
     * Falls back to boy on ambiguous targets.
     *
     * Returns Pair(displayFirstName, isGirl).
     */
    internal fun resolveRedirectTarget(
        target: String?,
        boyName: String,
        girlName: String,
    ): Pair<String, Boolean> {
        val boyFirst = boyName.split(' ').firstOrNull().orEmpty().ifBlank { boyName }
        val girlFirst = girlName.split(' ').firstOrNull().orEmpty().ifBlank { girlName }
        if (target.isNullOrBlank()) return boyFirst to false
        val t = target.lowercase().trim()
        val boyLower = boyName.lowercase()
        val girlLower = girlName.lowercase()
        val isBoy = listOf("boy", "him", "his", "he ").any { t.contains(it) } ||
            t == boyLower ||
            boyLower.startsWith(t) ||
            t.startsWith(boyLower)
        val isGirl = listOf("girl", "her", "she ").any { t.contains(it) } ||
            t == girlLower ||
            girlLower.startsWith(t) ||
            t.startsWith(girlLower)
        return when {
            isBoy && !isGirl -> boyFirst to false
            isGirl && !isBoy -> girlFirst to true
            else -> boyFirst to false // ambiguous → default to boy (matches iOS log line)
        }
    }

    private suspend fun runRedirectPredict(
        displayName: String,
        isGirl: Boolean,
        email: String,
        redirectQuery: String?,
    ) {
        val result = currentCompatibilityResult ?: return
        val s = _uiState.value
        val boyProfile = personAProfile

        val dob = if (isGirl) result.girlDob else result.boyDob
        if (dob.isNullOrBlank()) {
            _followUpMessages.update {
                it + FollowUpMessage(
                    text = "Could not retrieve $displayName's birth data for individual analysis.",
                    isUser = false,
                    isInfo = true,
                )
            }
            saveFollowUpHistory()
            return
        }
        val city = if (isGirl) (result.girlCity ?: "") else (result.boyCity ?: "")
        val time: String
        val latitude: Double
        val longitude: Double
        if (isGirl) {
            time = if (s.partnerTimeUnknown || s.partnerTime.isBlank()) "12:00" else s.partnerTime
            latitude = Math.round(s.partnerLatitude * 1_000_000.0) / 1_000_000.0
            longitude = Math.round(s.partnerLongitude * 1_000_000.0) / 1_000_000.0
        } else {
            time = when {
                boyProfile == null -> "12:00"
                boyProfile.birthTimeUnknown || boyProfile.timeOfBirth.isBlank() -> "12:00"
                else -> boyProfile.timeOfBirth
            }
            latitude = boyProfile?.let { Math.round(it.latitude * 1_000_000.0) / 1_000_000.0 } ?: 0.0
            longitude = boyProfile?.let { Math.round(it.longitude * 1_000_000.0) / 1_000_000.0 } ?: 0.0
        }
        val startMs = System.currentTimeMillis()
        try {
            val predictResponse = api.predict(
                PredictRequest(
                    query = redirectQuery ?: "Give a brief individual astrology reading",
                    userEmail = email,
                    birthData = PredictBirthDataDto(
                        dob = dob,
                        time = time,
                        cityOfBirth = city,
                        latitude = latitude,
                        longitude = longitude,
                    ),
                )
            )
            val elapsed = System.currentTimeMillis() - startMs
            val analysisContent = "**Individual Analysis ($displayName):**\n\n${predictResponse.text}"
            val cleaned = stripFollowUpQuestionsBlock(analysisContent)
            val embedded = extractFollowUpQuestions(analysisContent)
            _followUpMessages.update {
                it + FollowUpMessage(
                    text = cleaned,
                    isUser = false,
                    suggestions = embedded,
                    executionTimeMs = elapsed,
                )
            }
            saveFollowUpHistory()
        } catch (e: Exception) {
            _followUpMessages.update {
                it + FollowUpMessage(
                    text = "Failed to get individual analysis: ${e.message ?: "unknown error"}",
                    isUser = false,
                    isInfo = true,
                )
            }
        }
    }

    /**
     * iOS parity (CompatibilityResultSheets.swift:1583-1595 extractFollowUpQuestions):
     * parse `\nFOLLOW_UP_QUESTIONS:` block from the answer body. Used as the
     * fallback source of suggestions when the backend does not include them in
     * the JSON response.
     */
    internal fun extractFollowUpQuestions(content: String): List<String> {
        val markerIndex = content.indexOf("\nFOLLOW_UP_QUESTIONS:")
        if (markerIndex < 0) return emptyList()
        val block = content.substring(markerIndex + "\nFOLLOW_UP_QUESTIONS:".length).trim()
        if (block.isEmpty()) return emptyList()
        return block.split('\n').mapNotNull { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty()) return@mapNotNull null
            val cleaned = trimmed
                .replace(Regex("^[-•*]\\s*"), "")
                .replace(Regex("^\\d+\\.\\s*"), "")
                .trim()
            cleaned.ifEmpty { null }
        }
    }

    /** Strip the embedded FOLLOW_UP_QUESTIONS block from displayed content. */
    internal fun stripFollowUpQuestionsBlock(content: String): String {
        val markerIndex = content.indexOf("\nFOLLOW_UP_QUESTIONS:")
        if (markerIndex < 0) return content
        return content.substring(0, markerIndex).trim()
    }

    // -- History — Room-backed
    private val _historyItems = MutableStateFlow<List<CompatibilityHistoryItem>>(emptyList())
    val historyItems: StateFlow<List<CompatibilityHistoryItem>> = _historyItems

    val isHistoryEnabled: StateFlow<Boolean> = prefs.isHistoryEnabledFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    fun loadHistory() {
        val email = personAEmail ?: return
        historyDao.observeAll(email)
            .onEach { entities -> _historyItems.value = entities.map { it.toDomain() } }
            .launchIn(viewModelScope)
    }

    fun toggleHistoryPin(sessionId: String) {
        viewModelScope.launch {
            val item = _historyItems.value.find { it.sessionId == sessionId } ?: return@launch
            historyDao.setPin(sessionId, !item.isPinned)
        }
    }

    fun deleteHistoryItem(sessionId: String) {
        viewModelScope.launch {
            historyDao.delete(sessionId)
        }
    }

    private suspend fun saveToHistory(
        result: CompatibilityResult,
        email: String,
        profile: BirthProfileDto,
        s: CompatibilityUiState,
        comparisonGroupId: String? = null,
        partnerIndex: Int? = null,
    ) {
        if (!isHistoryEnabled.value) return
        val entity = CompatibilityHistoryEntity(
            sessionId = currentSessionId ?: "sess_${System.currentTimeMillis()}",
            ownerEmail = email,
            timestampMs = System.currentTimeMillis(),
            boyName = s.personAName,
            boyDob = profile.dateOfBirth,
            boyCity = profile.cityOfBirth,
            boyTime = profile.timeOfBirth,
            girlName = result.girlName,
            girlDob = s.partnerDob,
            girlCity = s.partnerCity,
            girlTime = s.partnerTime,
            totalScore = result.totalScore,
            maxScore = result.maxScore,
            isPinned = false,
            comparisonGroupId = comparisonGroupId,
            partnerIndex = partnerIndex,
            resultJson = gson.toJson(result),
        )
        historyDao.upsert(entity)
    }

    private fun CompatibilityHistoryEntity.toDomain() = CompatibilityHistoryItem(
        sessionId = sessionId,
        timestampMs = timestampMs,
        boyName = boyName,
        boyDob = boyDob,
        boyCity = boyCity,
        boyTime = boyTime,
        girlName = girlName,
        girlDob = girlDob,
        girlCity = girlCity,
        girlTime = girlTime,
        totalScore = totalScore,
        maxScore = maxScore,
        isPinned = isPinned,
        comparisonGroupId = comparisonGroupId,
        partnerIndex = partnerIndex,
        result = resultJson.takeIf { it.isNotEmpty() }?.let {
            runCatching { gson.fromJson(it, CompatibilityResult::class.java) }.getOrNull()
        },
    )

    // -- Multi-partner support
    private val _partners = MutableStateFlow<List<PartnerData>>(listOf(PartnerData()))
    val partners: StateFlow<List<PartnerData>> = _partners

    private val _activePartnerIndex = MutableStateFlow(0)
    val activePartnerIndex: StateFlow<Int> = _activePartnerIndex

    private val _failedPartnerIndices = MutableStateFlow<List<Int>>(emptyList())
    val hasFailedPartners: StateFlow<Boolean> = _failedPartnerIndices
        .map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private var currentComparisonGroupId: String? = null

    /**
     * Set the Plus-tier flag — gates multi-partner addPartner() and other Plus-only flows.
     * Wired from QuotaManager subscription state observer (iOS parity:
     * QuotaManager.isPlus). Visible to allow unit tests to drive the gate without
     * spinning up the full subscription stack.
     */
    fun setPlus(isPlus: Boolean) {
        _uiState.update { it.copy(isPlus = isPlus) }
    }

    fun addPartner() {
        // Multi-partner is a Plus-only feature (parity with iOS QuotaManager.isPlus gate).
        if (!_uiState.value.isPlus) {
            _uiState.update { it.copy(showPaywall = true) }
            return
        }
        val current = _partners.value
        if (current.size >= 3) return
        _partners.value = current + PartnerData()
        _activePartnerIndex.value = _partners.value.size - 1
    }

    fun removePartner(at: Int) {
        val current = _partners.value
        if (current.size <= 1) return
        if (!current.indices.contains(at)) return
        _partners.value = current.toMutableList().also { it.removeAt(at) }
        if (_activePartnerIndex.value >= _partners.value.size) {
            _activePartnerIndex.value = _partners.value.size - 1
        }
    }

    fun selectPartner(at: Int) {
        if (!_partners.value.indices.contains(at)) return
        _activePartnerIndex.value = at
    }

    fun analyzeAllPartners() {
        viewModelScope.launch {
            val profile = personAProfile ?: return@launch
            val email = personAEmail ?: return@launch
            val validPartners = _partners.value.filter { it.isComplete }
            if (validPartners.isEmpty()) return@launch

            // Pre-flight quota check before invoking streaming analysis (parity with iOS QuotaManager)
            if (!checkCompatibilityQuota(email, count = validPartners.size)) return@launch

            val groupId = java.util.UUID.randomUUID().toString()
            currentComparisonGroupId = groupId
            _comparisonResults.value = emptyList()
            _failedPartnerIndices.value = emptyList()
            _uiState.update { it.copy(isAnalyzing = true, error = null, showStreamingView = true) }
            _currentStep.value = AnalysisStep.CALCULATING_CHARTS

            val newFailed = mutableListOf<Int>()
            val newResults = mutableListOf<ComparisonResult>()

            validPartners.forEachIndexed { index, partner ->
                val appLanguage = prefs.getSelectedLanguage()
                val activeProfileId = prefs.getActiveProfileId()
                val rBoyLat = Math.round(profile.latitude * 1_000_000.0) / 1_000_000.0
                val rBoyLon = Math.round(profile.longitude * 1_000_000.0) / 1_000_000.0
                val rGirlLat = Math.round(partner.latitude * 1_000_000.0) / 1_000_000.0
                val rGirlLon = Math.round(partner.longitude * 1_000_000.0) / 1_000_000.0
                val request = CompatibilityRequestDto(
                    boy = CompatibilityBirthDetailsDto(
                        dob = profile.dateOfBirth,
                        time = if (profile.birthTimeUnknown) "12:00" else profile.timeOfBirth,
                        lat = rBoyLat,
                        lon = rBoyLon,
                        name = _uiState.value.personAName,
                        place = profile.cityOfBirth,
                    ),
                    girl = CompatibilityBirthDetailsDto(
                        dob = partner.dob,
                        time = partner.time.ifBlank { "12:00" },
                        lat = rGirlLat,
                        lon = rGirlLon,
                        name = partner.name,
                        place = partner.city,
                    ),
                    userEmail = email,
                    comparisonGroupId = groupId,
                    partnerIndex = index,
                    language = appLanguage,
                    profileId = activeProfileId,
                )
                try {
                    compatibilityRepo.streamAnalysis(request).collect { event ->
                        when (event) {
                            is SseEvent.Step -> _currentStep.value = mapStepName(event.stepName)
                            is SseEvent.FinalJson -> {
                                val result = mapCompatibilityResponse(
                                    json = event.json,
                                    boyName = _uiState.value.personAName,
                                    girlName = partner.name,
                                    boyDob = profile.dateOfBirth,
                                    girlDob = partner.dob,
                                    boyCity = profile.cityOfBirth,
                                    girlCity = partner.city,
                                )
                                newResults.add(ComparisonResult(
                                    partner = partner,
                                    totalScore = result.totalScore,
                                    maxScore = result.maxScore,
                                    overallScore = result.adjustedScore ?: result.totalScore,
                                    isRecommended = result.isRecommended,
                                    adjustedScore = result.adjustedScore ?: result.totalScore,
                                    summary = result.summary,
                                ))
                                _comparisonResults.value = newResults.toList()
                            }
                            is SseEvent.Error -> newFailed.add(index)
                        }
                    }
                } catch (_: Exception) {
                    newFailed.add(index)
                }
            }

            _failedPartnerIndices.value = newFailed
            _uiState.update {
                it.copy(
                    isAnalyzing = false,
                    showStreamingView = false,
                    showComparisonOverview = newResults.size > 1,
                )
            }
        }
    }

    fun retryFailedPartners() {
        viewModelScope.launch {
            val profile = personAProfile ?: return@launch
            val email = personAEmail ?: return@launch
            val failedIndices = _failedPartnerIndices.value.toList()
            if (failedIndices.isEmpty()) return@launch

            // Pre-flight quota check before invoking streaming analysis (parity with iOS QuotaManager)
            if (!checkCompatibilityQuota(email, count = failedIndices.size)) return@launch

            _failedPartnerIndices.value = emptyList()

            val groupId = currentComparisonGroupId ?: java.util.UUID.randomUUID().toString()
            val newFailed = mutableListOf<Int>()
            val updatedResults = _comparisonResults.value.toMutableList()

            failedIndices.forEach { index ->
                val partner = _partners.value.getOrNull(index) ?: run {
                    newFailed.add(index); return@forEach
                }
                val appLanguage = prefs.getSelectedLanguage()
                val activeProfileId = prefs.getActiveProfileId()
                val rBoyLat = Math.round(profile.latitude * 1_000_000.0) / 1_000_000.0
                val rBoyLon = Math.round(profile.longitude * 1_000_000.0) / 1_000_000.0
                val rGirlLat = Math.round(partner.latitude * 1_000_000.0) / 1_000_000.0
                val rGirlLon = Math.round(partner.longitude * 1_000_000.0) / 1_000_000.0
                val request = CompatibilityRequestDto(
                    boy = CompatibilityBirthDetailsDto(
                        dob = profile.dateOfBirth,
                        time = if (profile.birthTimeUnknown) "12:00" else profile.timeOfBirth,
                        lat = rBoyLat,
                        lon = rBoyLon,
                        name = _uiState.value.personAName,
                        place = profile.cityOfBirth,
                    ),
                    girl = CompatibilityBirthDetailsDto(
                        dob = partner.dob,
                        time = partner.time.ifBlank { "12:00" },
                        lat = rGirlLat,
                        lon = rGirlLon,
                        name = partner.name,
                        place = partner.city,
                    ),
                    userEmail = email,
                    comparisonGroupId = groupId,
                    partnerIndex = index,
                    language = appLanguage,
                    profileId = activeProfileId,
                )
                try {
                    compatibilityRepo.streamAnalysis(request).collect { event ->
                        when (event) {
                            is SseEvent.Step -> _currentStep.value = mapStepName(event.stepName)
                            is SseEvent.FinalJson -> {
                                val result = mapCompatibilityResponse(
                                    json = event.json,
                                    boyName = _uiState.value.personAName,
                                    girlName = partner.name,
                                    boyDob = profile.dateOfBirth,
                                    girlDob = partner.dob,
                                    boyCity = profile.cityOfBirth,
                                    girlCity = partner.city,
                                )
                                updatedResults.add(ComparisonResult(
                                    partner = partner,
                                    totalScore = result.totalScore,
                                    maxScore = result.maxScore,
                                    overallScore = result.adjustedScore ?: result.totalScore,
                                    isRecommended = result.isRecommended,
                                    adjustedScore = result.adjustedScore ?: result.totalScore,
                                    summary = result.summary,
                                ))
                                _comparisonResults.value = updatedResults.toList()
                            }
                            is SseEvent.Error -> newFailed.add(index)
                        }
                    }
                } catch (_: Exception) {
                    newFailed.add(index)
                }
            }
            _failedPartnerIndices.value = newFailed
        }
    }

    /**
     * Hydrate the form + result state from a saved history entry. Mirrors iOS
     * CompatibilityViewModel.loadFromHistory(_:) — used for both deep-link entry
     * and free re-open of past matches (no LLM call).
     */
    fun loadFromHistory(item: CompatibilityHistoryItem) {
        _uiState.update {
            it.copy(
                partnerName = item.girlName,
                partnerDob = item.girlDob,
                partnerTime = item.girlTime,
                partnerCity = item.girlCity,
                // Don't hardcode gender — leave blank if not stored
                partnerGender = it.partnerGender,
            )
        }
        item.result?.let { saved ->
            _compatibilityResult.value = saved
            _uiState.update {
                it.copy(
                    result = saved.summary,
                    score = saved.totalScore,
                )
            }
        }
        currentSessionId = item.sessionId
        // iOS parity (CompatibilityViewModel.swift:466): surface the transient toast
        _historyLoadedToast.value = true
    }

    /**
     * Hydrate the comparison overview from a saved comparison group (deep-link
     * from Home match-history). Mirrors iOS CompatibilityViewModel.loadFromGroup —
     * sets the comparison results without re-running an LLM analysis.
     */
    fun loadFromGroup(group: com.destinyai.astrology.domain.model.ComparisonGroup) {
        val results = group.items.mapNotNull { item ->
            val saved = item.result ?: return@mapNotNull null
            val partner = PartnerData(
                name = item.girlName,
                dob = item.girlDob,
                time = item.girlTime,
                city = item.girlCity,
            )
            ComparisonResult(
                partner = partner,
                totalScore = saved.totalScore,
                maxScore = item.maxScore,
                overallScore = saved.adjustedScore ?: saved.totalScore,
                isRecommended = saved.isRecommended,
                adjustedScore = saved.adjustedScore ?: saved.totalScore,
                summary = saved.summary,
            )
        }
        _comparisonResults.value = results
        currentComparisonGroupId = group.id
        _uiState.update { it.copy(showComparisonOverview = true) }
    }

    /** When the user taps a partner in the comparison overview, swap the active result. */
    fun selectComparisonResult(index: Int) {
        val results = _comparisonResults.value
        val r = results.getOrNull(index) ?: return
        _uiState.update {
            it.copy(
                result = r.summary,
                score = r.totalScore,
            )
        }
    }

    /**
     * Persist the partner currently in the form to the user's saved birth charts.
     * Best-effort — fire-and-forget; failures are logged but do not block analysis.
     */
    private suspend fun savePartnerToBirthCharts(email: String, s: CompatibilityUiState) {
        try {
            val req = CreatePartnerRequest(
                userEmail = email,
                profile = PartnerRequest(
                    name = s.partnerName,
                    gender = s.partnerGender,
                    dateOfBirth = s.partnerDob,
                    timeOfBirth = if (s.partnerTimeUnknown) null else s.partnerTime,
                    cityOfBirth = s.partnerCity,
                    latitude = s.partnerLatitude,
                    longitude = s.partnerLongitude,
                    birthTimeUnknown = s.partnerTimeUnknown,
                    forCompatibility = true,
                ),
                consentGiven = true,
            )
            api.addPartner(req)
        } catch (_: Exception) {
            // Silently swallow — partner save is best-effort and must not break analysis flow.
        }
    }
}
