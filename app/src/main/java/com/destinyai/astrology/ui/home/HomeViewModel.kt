package com.destinyai.astrology.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.destinyai.astrology.data.local.prefs.UserPreferences
import com.destinyai.astrology.data.remote.AstroApiService
import com.destinyai.astrology.data.repository.HomeRepository
import com.destinyai.astrology.domain.model.User
import com.destinyai.astrology.services.ProfileChangeBus
import com.destinyai.astrology.services.NetworkMonitor
import com.destinyai.astrology.services.QuotaManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

data class HomeUiState(
    val currentUser: User? = null,
    val displayName: String = "Guest",
    val dailyQuota: Int = 0,
    val dailyUsed: Int = 0,
    val remaining: Int = 0,
    val quotaProgress: Float = 0f,
    val isUnlimited: Boolean = false,
    val isLoading: Boolean = false,
    val suggestedQuestions: List<String> = emptyList(),
    val dailyInsight: String? = null,
    val renewalDateString: String? = null,
    // Rich astrology data
    val transits: List<HomeTransit> = emptyList(),
    val dashaInfo: HomeDashaInfo? = null,
    val yogas: List<HomeYoga> = emptyList(),
    val doshas: HomeDoshaStatus = HomeDoshaStatus(),
    val lifeAreas: List<HomeLifeArea> = defaultLifeAreas(),
    val isRichDataLoading: Boolean = false,
    val selectedLifeArea: HomeLifeArea? = null,
    // R2-H3: notification badge unread count
    val unreadCount: Int = 0,
    // R2-H28: brief popup before full sheet
    val briefLifeArea: HomeLifeArea? = null,
    // R2-H24: yoga filter tab
    val yogaFilter: YogaFilter = YogaFilter.All,
    // Tapped yoga (drives YogaDetailPopup) — parity with iOS selectedYogaForPopup
    val selectedYoga: HomeYoga? = null,
    // Error banner state — populated when prediction or rich data load fails
    val errorMessage: String? = null,
    // Parity with iOS HomeView.localizedAscendant subtitle. Empty until rich data loads.
    val ascendantSign: String = "",
)

// R2-H24: yoga filter enum — full parity with iOS YogaHighlightCard.FilterType.
// Legacy Raja/Dhana kept for backwards compatibility with existing tests.
enum class YogaFilter {
    All,
    Wealth,
    Career,
    Love,
    Health,
    Family,
    Education,
    Spiritual,
    Foundation,
    Personality,
    Special,
    Raja,
    Dhana,
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: HomeRepository,
    private val prefs: UserPreferences,
    private val api: AstroApiService,
    private val profileChangeBus: ProfileChangeBus,
    private val quotaManager: QuotaManager,
    networkMonitor: NetworkMonitor,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState

    /**
     * Network connectivity state — drives the OfflineBanner on Home.
     * Parity with iOS HomeView OfflineBanner() observing NetworkMonitor.shared.
     */
    val isOnline: StateFlow<Boolean> = networkMonitor.isOnline
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    // One-shot navigation event: fired when the user taps a Home card and we want to
    // open the Chat tab pre-populated with a contextual prompt. Mirrors iOS
    // HomeView.onQuestionSelected callback into MainTabView.pendingQuestion.
    private val _askDestinyEvents = Channel<String>(Channel.BUFFERED)
    val askDestinyEvents: Flow<String> = _askDestinyEvents.receiveAsFlow()

    /** Emit a prompt to take the user to Chat with that prompt prefilled. */
    fun askDestiny(prompt: String) {
        if (prompt.isBlank()) return
        viewModelScope.launch { _askDestinyEvents.send(prompt) }
    }

    // ISO date (yyyy-MM-dd) of the last successful prediction load — used by onAppForeground()
    // to detect day rollovers and force-refresh (parity with iOS scenePhase + targetDate check).
    @Volatile private var lastLoadDate: String? = null

    init {
        viewModelScope.launch {
            try {
                val user = repository.getCurrentUser()
                val quota = repository.getDailyQuota()
                val used = repository.getDailyUsed()
                updateQuotaState(user, quota, used)
            } catch (e: Exception) {
                android.util.Log.w("HomeViewModel", "init load failed: ${e.message}", e)
                _uiState.update { it.copy(isLoading = false) }
            }
        }
        // Listen for active-profile changes and reset state to avoid showing stale data
        viewModelScope.launch {
            profileChangeBus.events.collect {
                resetForProfileSwitch()
                loadHomeData()
            }
        }
        // Parity with iOS .onChange(of: quotaManager.isPremium): when subscription state flips
        // to premium, force-reload Home so newly-unlocked content (yoga filters, transits)
        // appears immediately. drop(1) skips the StateFlow's initial replay so we don't
        // double-fetch on launch.
        viewModelScope.launch {
            quotaManager.isPremium.drop(1).collect { isPremium ->
                if (isPremium) {
                    android.util.Log.i("HomeViewModel", "User upgraded to premium — refreshing Home")
                    loadHomeData()
                }
            }
        }
    }

    /**
     * Called from HomeScreen when the app moves to foreground (Lifecycle.Event.ON_RESUME).
     * Parity with iOS .onChange(of: scenePhase) — refreshes unread count and forces a Home
     * reload if the cached prediction's date is no longer today.
     *
     * Also invoked when the user returns from the Notifications screen (parity with iOS
     * NotificationInboxView onNavigateToHome closure that triggers loadHomeData(force:true)).
     */
    fun onAppForeground() {
        fetchUnreadCount()
        val today = LocalDate.now().toString()
        val cachedDate = lastLoadDate
        if (cachedDate == null || cachedDate != today) {
            android.util.Log.i("HomeViewModel", "Day rollover or stale cache — refreshing Home")
            loadHomeData()
        }
    }

    private fun resetForProfileSwitch() {
        _uiState.update {
            it.copy(
                dailyInsight = null,
                transits = emptyList(),
                dashaInfo = null,
                yogas = emptyList(),
                doshas = HomeDoshaStatus(),
                lifeAreas = defaultLifeAreas(),
                unreadCount = 0,
                errorMessage = null,
                ascendantSign = "",
            )
        }
    }

    private fun updateQuotaState(user: User?, quota: Int, used: Int) {
        val unlimited = quota < 0
        val remaining = if (unlimited) Int.MAX_VALUE else maxOf(0, quota - used)
        val progress = if (unlimited || quota == 0) 0f else used.toFloat() / quota.toFloat()
        val name = when {
            user == null || user.isGuestEmail -> "Guest"
            user.name != null -> user.name.split(" ").first()
            else -> user.email.substringBefore("@")
        }
        _uiState.update {
            it.copy(
                currentUser = user,
                displayName = name,
                dailyQuota = quota,
                dailyUsed = used,
                remaining = remaining,
                quotaProgress = progress,
                isUnlimited = unlimited,
                isLoading = false,
            )
        }
    }

    fun loadHomeData() {
        viewModelScope.launch {
            // iOS parity (HomeViewModel.swift:159-177): detect a language change
            // since the last successful Home payload — if Settings flipped the
            // app language, force a full re-fetch so localized strings refresh
            // without the user reinstalling.
            val currentLang = prefs.getSelectedLanguage()
            val lastLoadedLang = prefs.getLastLoadedLanguage()
            val languageChanged = lastLoadedLang != null && currentLang != lastLoadedLang

            // iOS parity (HomeViewModel.swift:52-59): per-email cold-start gate.
            // Skip the full reload when the stored startOfDay matches today's
            // startOfDay (same calendar day → reuse cached chart/dasha/transits).
            val email = prefs.getUserEmail()
            val nowMs = System.currentTimeMillis()
            val storedMs = prefs.getLastFullLoadDate(email)
            val sameDay = storedMs != null && isSameLocalDay(storedMs, nowMs)
            val haveDataInMemory = _uiState.value.dailyInsight != null &&
                _uiState.value.transits.isNotEmpty()
            val canSkipFullReload = !languageChanged && sameDay && haveDataInMemory

            if (canSkipFullReload) {
                android.util.Log.i(
                    "HomeViewModel",
                    "Same-day cache hit (lastFullLoadDate gate) — skipping full reload",
                )
                lastLoadDate = LocalDate.now().toString()
                fetchUnreadCount()
                return@launch
            }

            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            // Re-sync quota and subscription state from backend on every load (parity with iOS syncQuotaFromBackend)
            try {
                val user = repository.getCurrentUser()
                if (user != null) {
                    updateQuotaState(user, user.dailyQuota, user.dailyUsed)
                }
            } catch (e: Exception) {
                android.util.Log.w("HomeViewModel", "quota sync failed: ${e.message}", e)
            }
            val questions = repository.getSuggestedQuestions()
            val (insight, loadError) = try {
                repository.getDailyInsight() to null
            } catch (e: Exception) {
                android.util.Log.w("HomeViewModel", "getDailyInsight failed: ${e.message}", e)
                "" to friendlyError(e)
            }
            _uiState.update {
                it.copy(
                    isLoading = false,
                    suggestedQuestions = questions,
                    dailyInsight = insight.ifBlank { null },
                    errorMessage = if (insight.isBlank() && loadError != null) loadError else it.errorMessage,
                )
            }
            // Record the load date for day-rollover detection in onAppForeground()
            if (loadError == null) {
                lastLoadDate = LocalDate.now().toString()
                // Persist the language + per-email epoch-millis so the next
                // cold-start can date-gate as iOS does.
                prefs.setLastLoadedLanguage(currentLang)
                prefs.setLastFullLoadDate(nowMs, email)
            }
            // R2-H3: fetch unread notification count
            fetchUnreadCount()
            loadRichHomeData()
        }
    }

    /**
     * iOS parity (HomeViewModel.swift:52-59): two epoch-millis instants land on
     * the same local calendar day iff their startOfDay values are identical.
     */
    private fun isSameLocalDay(storedMs: Long, nowMs: Long): Boolean {
        val zone = java.time.ZoneId.systemDefault()
        val storedDay = java.time.Instant.ofEpochMilli(storedMs).atZone(zone).toLocalDate()
        val nowDay = java.time.Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
        return storedDay == nowDay
    }

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    private fun friendlyError(e: Throwable): String {
        return when {
            e is java.net.SocketTimeoutException -> "Request timed out. Please try again."
            e is java.io.IOException -> "Network unavailable. Check your connection."
            e.message?.contains("401", ignoreCase = true) == true -> "Session expired. Please sign in again."
            else -> "Couldn't load home data. Please retry."
        }
    }

    // R2-H3: fetch unread count from API
    private fun fetchUnreadCount() {
        viewModelScope.launch {
            val email = prefs.getUserEmail() ?: return@launch
            runCatching {
                val resp = api.getUnreadCount(email)
                _uiState.update { it.copy(unreadCount = resp.count) }
            }
        }
    }

    fun loadRichHomeData() {
        viewModelScope.launch {
            val email = prefs.getUserEmail() ?: return@launch
            val birth = prefs.getBirthProfile() ?: return@launch
            _uiState.update { it.copy(isRichDataLoading = true) }
            val richData = repository.getRichHomeData(email, birth)
            if (richData != null) {
                _uiState.update {
                    it.copy(
                        isRichDataLoading = false,
                        transits = richData.transits,
                        dashaInfo = richData.dashaInfo,
                        yogas = richData.yogas,
                        doshas = richData.doshas,
                        lifeAreas = richData.lifeAreas,
                        ascendantSign = richData.ascendantSign,
                    )
                }
            } else {
                _uiState.update { it.copy(isRichDataLoading = false) }
            }
        }
    }

    fun selectLifeArea(area: HomeLifeArea) {
        // R2-H28: show brief popup first, not the full sheet directly
        _uiState.update { it.copy(briefLifeArea = area, selectedLifeArea = null) }
    }

    /**
     * Parity with iOS HomeView LifeAreaBriefPopup.onAskMore — instead of advancing
     * to the questions sheet, push the brief context straight to chat with a
     * prefilled question. Fixes "Ask More button doesn't navigate to chat" gap.
     */
    fun confirmLifeAreaBrief() {
        val brief = _uiState.value.briefLifeArea ?: return
        _uiState.update { it.copy(briefLifeArea = null) }
        val text = brief.briefDescription.ifBlank { brief.name }
        askDestiny(
            "Today's forecast mentions: '$text' for my ${brief.name}. " +
                "Can you elaborate on what this means for me?"
        )
    }

    fun dismissLifeAreaBrief() {
        _uiState.update { it.copy(briefLifeArea = null) }
    }

    fun dismissLifeArea() {
        _uiState.update { it.copy(selectedLifeArea = null) }
    }

    // R2-H24: yoga filter
    fun setYogaFilter(filter: YogaFilter) {
        _uiState.update { it.copy(yogaFilter = filter) }
    }

    // Yoga detail popup (parity with iOS YogaDetailPopup overlay)
    fun selectYoga(yoga: HomeYoga) {
        _uiState.update { it.copy(selectedYoga = yoga) }
    }

    fun dismissYoga() {
        _uiState.update { it.copy(selectedYoga = null) }
    }

    // ── Click-handler helpers — mirror iOS HomeView contextual question builders ─
    // Each returns a pre-formed prompt that the UI hands off via askDestiny() to
    // the Chat tab, exactly as iOS calls onQuestionSelected.

    fun onSuggestedQuestionTapped(question: String) {
        askDestiny(question)
    }

    fun onTransitTapped(transit: HomeTransit) {
        val prompt = buildString {
            append(transit.planet)
            append(" is currently transiting through ")
            append(transit.sign)
            if (transit.house > 0) {
                append(" in my ")
                append(transit.house)
                append("th house")
            }
            append(".")
            if (transit.description.isNotBlank()) {
                append(" The key indication is: '")
                append(transit.description)
                append("'.")
            }
            append("\n\nPlease share deeper insights on how this transit influences my life and any practical guidance for this period.")
        }
        askDestiny(prompt)
    }

    fun onDashaTapped() {
        val dasha = _uiState.value.dashaInfo ?: return
        val theme = dasha.theme.orEmpty()
        val quality = dasha.quality.orEmpty()
        val meaning = dasha.meaning.orEmpty()
        // Parity with iOS HomeView.swift:187-199 context_dasha_question — composes
        // {period, theme, quality, meaning} into a rich contextual prompt so the chat
        // agent has full dasha context, not just a generic "tell me about my dasha".
        val prompt = buildString {
            append("I am currently in my ")
            append(dasha.mahadasha)
            if (dasha.antardasha.isNotBlank()) {
                append("-")
                append(dasha.antardasha)
            }
            append(" Dasha period")
            if (theme.isNotBlank()) {
                append(", which carries a theme of '")
                append(theme)
                append("'")
            }
            if (quality.isNotBlank()) {
                append(" with a '")
                append(quality)
                append("' overall quality")
            }
            append(".")
            if (meaning.isNotBlank()) {
                append(" This phase suggests: ")
                append(meaning)
            }
            append("\n\nPlease provide a detailed analysis of how this Dasha period shapes my life, what opportunities or challenges to expect, and any recommended remedies.")
        }
        askDestiny(prompt)
    }

    fun onLifeAreaQuestionTapped(area: HomeLifeArea, question: String) {
        // Dismiss any open sheet first then send the question to chat.
        _uiState.update { it.copy(selectedLifeArea = null, briefLifeArea = null) }
        askDestiny(question)
    }

    fun onYogaAskMore(yoga: HomeYoga) {
        // Parity with iOS HomeView.swift:260-312 yoga_context_* keys — composes a
        // rich multi-line prompt with {name, type, category, status, strength,
        // planets, houses, formation, outcome, reason} plus 2-3 follow-up questions
        // so the chat agent has full classical context, not a stub prompt.
        val typeText = if (yoga.isDosha) "Dosha" else "Yoga"
        val statusText = when (yoga.status.lowercase()) {
            "active", "a" -> "Active"
            "reduced", "r" -> "Reduced"
            "cancelled", "canceled", "c" -> "Cancelled"
            else -> if (yoga.isActive) "Active" else "Inactive"
        }
        val parts = mutableListOf<String>()
        parts += "I have ${yoga.name} in my birth chart."
        parts += ""
        parts += "Details:"
        parts += "- Type: $typeText"
        parts += "- Category: ${yoga.category}"
        parts += "- Status: $statusText"
        if (yoga.strength > 0) parts += "- Strength: ${yoga.strength}%"
        if (yoga.planets.isNotBlank()) parts += "- Planets: ${yoga.planets}"
        if (yoga.houses.isNotBlank()) parts += "- Houses: ${yoga.houses}"
        if (yoga.formation.isNotBlank()) parts += "- Formation: ${yoga.formation}"
        if (yoga.outcome.isNotBlank()) parts += "- What it means: ${yoga.outcome}"
        if (yoga.reductionReason.isNotBlank() && !statusText.equals("Active", ignoreCase = true)) {
            val reasonLabel = if (statusText.equals("Reduced", ignoreCase = true)) {
                "Reduction reason"
            } else {
                "Cancellation reason"
            }
            parts += "- $reasonLabel: ${yoga.reductionReason}"
        }
        parts += ""
        parts += "Please explain:"
        parts += "1. What does this ${typeText.lowercase()} mean for me practically?"
        if (statusText.equals("Active", ignoreCase = true)) {
            if (yoga.isDosha) {
                parts += "2. What challenges should I prepare for, and what remedies help?"
            } else {
                parts += "2. How can I best leverage this auspicious combination in my life?"
            }
        } else {
            parts += "2. Since this is ${statusText.lowercase()}, how does that change the impact?"
            parts += "3. Are there still any subtle effects I should be aware of?"
        }
        _uiState.update { it.copy(selectedYoga = null) }
        askDestiny(parts.joinToString("\n"))
    }

    fun decrementQuota() {
        viewModelScope.launch {
            val quota = repository.getDailyQuota()
            val used = repository.getDailyUsed()
            val newUsed = used + 1
            val newRemaining = maxOf(0, quota - newUsed)
            val newProgress = if (quota <= 0) 0f else newUsed.toFloat() / quota
            _uiState.update {
                it.copy(
                    dailyUsed = newUsed,
                    remaining = newRemaining,
                    quotaProgress = newProgress,
                )
            }
        }
    }

    companion object {
        // Parity with iOS HomeView.timeBasedGreeting: 0..<12 morning, 12..<18 afternoon,
        // default evening. NOTE: HomeScreen.timeBasedGreeting() is the on-screen source of
        // truth (uses string resources). This helper is preserved for unit tests and
        // non-Composable callers; it MUST stay in sync with the Composable buckets.
        fun greetingFor(time: LocalTime): String = when {
            time.hour < 12 -> "Good Morning"
            time.hour < 18 -> "Good Afternoon"
            else -> "Good Evening"
        }

        fun formatRenewalDate(isoDate: String): String {
            val date = LocalDate.parse(isoDate)
            val formatter = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH)
            return date.format(formatter)
        }
    }
}
