package com.destinyai.astrology.ui.compatibility

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
) : ViewModel() {

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
            val profile = prefs.getBirthProfile() ?: return@launch
            val name = prefs.getUserName() ?: ""
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
    fun setSavePartnerToBirthCharts(save: Boolean) = _uiState.update { it.copy(savePartnerToBirthCharts = save) }
    fun dismissDuplicateAlert() = _uiState.update { it.copy(showDuplicateAlert = false, duplicateSessionId = null) }
    fun showDuplicateAlert(sessionId: String) = _uiState.update { it.copy(showDuplicateAlert = true, duplicateSessionId = sessionId) }

    /** Reset the partner form back to its blank state. */
    fun resetPartnerForm() {
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
                        val marker = if (email.contains("guest") || email.contains("@gen.com")) {
                            "FREE_LIMIT_GUEST"
                        } else {
                            "FREE_LIMIT_REGISTERED"
                        }
                        _uiState.update {
                            it.copy(
                                isAnalyzing = false,
                                showStreamingView = false,
                                showPaywall = true,
                                error = marker,
                            )
                        }
                    }
                    else -> {
                        // upgrade_required / feature_upgrade_required / unknown -> paywall sheet
                        _uiState.update {
                            it.copy(
                                isAnalyzing = false,
                                showStreamingView = false,
                                showPaywall = true,
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
            val cached = _historyItems.value.firstOrNull { item ->
                item.boyDob == profile.dateOfBirth &&
                    item.boyTime == profile.timeOfBirth &&
                    item.boyCity.equals(profile.cityOfBirth, ignoreCase = true) &&
                    item.girlDob == s.partnerDob &&
                    item.girlTime == s.partnerTime &&
                    item.girlCity.equals(s.partnerCity, ignoreCase = true)
            }
            if (cached?.result != null) {
                _compatibilityResult.value = cached.result
                _uiState.update {
                    it.copy(
                        result = cached.result.summary,
                        score = cached.result.totalScore,
                        isAnalyzing = false,
                        showStreamingView = false,
                    )
                }
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
                            val result = mapCompatibilityResponse(
                                json = event.json,
                                boyName = s.personAName,
                                girlName = s.partnerName,
                                boyDob = profile.dateOfBirth,
                                girlDob = s.partnerDob,
                                boyCity = profile.cityOfBirth,
                                girlCity = s.partnerCity,
                            )
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
                            _uiState.update { it.copy(isAnalyzing = false, showStreamingView = false, error = event.message) }
                        }
                    }
                }
            } catch (e: Exception) {
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

    data class FollowUpMessage(val text: String, val isUser: Boolean, val suggestions: List<String> = emptyList())

    private val _followUpMessages = MutableStateFlow<List<FollowUpMessage>>(emptyList())
    val followUpMessages: StateFlow<List<FollowUpMessage>> = _followUpMessages

    private val _isFollowUpLoading = MutableStateFlow(false)
    val isFollowUpLoading: StateFlow<Boolean> = _isFollowUpLoading

    fun setSessionId(sessionId: String) { currentSessionId = sessionId }

    fun setCompatibilityResult(result: CompatibilityResult) { currentCompatibilityResult = result }

    fun clearFollowUpMessages() { _followUpMessages.value = emptyList() }

    fun sendFollowUp(query: String) {
        viewModelScope.launch {
            val email = personAEmail ?: return@launch
            val sessionId = currentSessionId ?: return@launch
            if (query.isBlank()) return@launch
            _followUpMessages.update { it + FollowUpMessage(query, isUser = true) }
            _isFollowUpLoading.value = true
            try {
                val response = api.compatibilityFollowUp(
                    CompatibilityFollowUpRequest(
                        query = query,
                        sessionId = sessionId,
                        userEmail = email,
                    )
                )
                if (response.status == "redirect") {
                    handleFollowUpRedirect(target = response.target, email = email)
                } else {
                    val answerText = followUpDisplayAnswer(response.status, response.answer, response.message)
                    val suggestions = response.followUpSuggestions ?: emptyList()
                    _followUpMessages.update { it + FollowUpMessage(answerText, isUser = false, suggestions = suggestions) }
                }
            } catch (e: Exception) {
                _followUpMessages.update { it + FollowUpMessage("Something went wrong. Please try again.", isUser = false) }
            } finally {
                _isFollowUpLoading.value = false
            }
        }
    }

    private suspend fun handleFollowUpRedirect(target: String?, email: String) {
        val result = currentCompatibilityResult ?: return
        val isGirl = target != null && (
            target.lowercase().contains(result.girlName.lowercase()) ||
            target.lowercase().contains("girl")
        )
        val name = if (isGirl) result.girlName else result.boyName
        val dob = (if (isGirl) result.girlDob else result.boyDob) ?: return
        val city = if (isGirl) result.girlCity ?: "" else result.boyCity ?: ""
        try {
            val predictResponse = api.predict(
                PredictRequest(
                    query = "Give a brief individual astrology reading",
                    userEmail = email,
                    birthData = PredictBirthDataDto(
                        dob = dob,
                        time = "",
                        cityOfBirth = city,
                        latitude = 0.0,
                        longitude = 0.0,
                    ),
                )
            )
            val answerText = "**Individual Analysis ($name):**\n${predictResponse.text}"
            _followUpMessages.update { it + FollowUpMessage(answerText, isUser = false) }
        } catch (e: Exception) {
            _followUpMessages.update { it + FollowUpMessage("Could not fetch individual chart for $name.", isUser = false) }
        }
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
