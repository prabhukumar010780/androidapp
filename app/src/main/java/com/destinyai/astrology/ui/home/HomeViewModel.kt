package com.destinyai.astrology.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.destinyai.astrology.data.local.prefs.UserPreferences
import com.destinyai.astrology.data.remote.AstroApiService
import com.destinyai.astrology.data.remote.BirthProfileDto
import com.destinyai.astrology.data.repository.HomeRepository
import com.destinyai.astrology.domain.model.User
import com.destinyai.astrology.services.ProfileChangeBus
import com.destinyai.astrology.services.ProfileContextManager
import com.destinyai.astrology.services.NetworkMonitor
import com.destinyai.astrology.services.LocaleManager
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
import kotlinx.coroutines.delay
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
    // iOS HomeViewModel.swift:18-20 parity — surface guest / premium state on the
    // observable UI surface so Compose can bind directly without separately
    // collecting QuotaManager.isPremium / SubscriptionManager flags.
    val isGuest: Boolean = false,
    val isPremium: Boolean = false,
    val planDisplayName: String = "",
    // Rich astrology data
    val transits: List<HomeTransit> = emptyList(),
    val dashaInfo: HomeDashaInfo? = null,
    val yogas: List<HomeYoga> = emptyList(),
    val doshas: HomeDoshaStatus = HomeDoshaStatus(),
    val lifeAreas: List<HomeLifeArea> = defaultLifeAreas(),
    val isRichDataLoading: Boolean = false,
    // DES-161: dedicated pull-to-refresh flag. isLoading/isRichDataLoading are NOT
    // set on the 24h cache-hit early-return path, so a manual pull that lands on
    // that path never sees a true->false transition and PullToRefreshBox leaves its
    // indicator stuck over the rings. This flag is set true the instant a manual
    // refresh starts and cleared in a finally, guaranteeing the retract animation.
    val isRefreshing: Boolean = false,
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
    ;

    companion object {
        /**
         * The 11 filter chips shown in the UI — matches iOS FilterType exactly.
         * Excludes the legacy Raja/Dhana values (kept on the enum only for
         * backward-compat with existing tests): both fell through to the "All"
         * label, so iterating YogaFilter.values() rendered THREE "All" chips.
         */
        val displayFilters: List<YogaFilter> = listOf(
            All, Wealth, Career, Love, Health, Family,
            Education, Spiritual, Foundation, Personality, Special,
        )
    }
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: HomeRepository,
    private val prefs: UserPreferences,
    private val api: AstroApiService,
    private val profileChangeBus: ProfileChangeBus,
    private val profileContextManager: ProfileContextManager,
    private val quotaManager: QuotaManager,
    private val localeManager: LocaleManager,
    @dagger.hilt.android.qualifiers.ApplicationContext private val appContext: android.content.Context,
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
    // iOS parity (HomeViewModel.swift isLoading re-entrancy guard): a second
    // loadHomeData() while one is in flight (e.g. fast tab re-selects) must not
    // fire duplicate network fetches.
    @Volatile private var loadInFlight: Boolean = false

    init {
        viewModelScope.launch {
            try {
                val user = repository.getCurrentUser()
                val quota = repository.getDailyQuota()
                val used = repository.getDailyUsed()
                // iOS parity: profileContext.userName is the canonical full name (saved during signup
                // / first-time setup). Backend `name` may be only the first name. Prefer prefs.
                val storedName = runCatching { prefs.getUserName() }.getOrNull()
                updateQuotaState(user, quota, used, storedName)
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
        // iOS parity (HomeViewModel.swift:159-227): when the app language flips
        // mid-session, today's prediction must be re-fetched in the new language
        // — otherwise the user reads a Hindi UI shell over English prediction
        // text. AppNav already wraps NavHost in `key(localeVersion)`, but a
        // subscriber here makes the refresh explicit (and survives any cached
        // VM that outlives recomposition). loadHomeData internally detects the
        // language flip via prefs.lastLoadedLanguage and bypasses its cache.
        viewModelScope.launch {
            localeManager.languageChanges.collect { newLang ->
                android.util.Log.i("HomeViewModel", "Language changed to $newLang — refreshing Home")
                loadHomeData()
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
        // iOS parity (HomeViewModel.swift:62-85): wipe everything that depends
        // on the active profile so loadHomeData() actually re-fetches with the
        // new profile's birth data instead of short-circuiting on the same-day
        // cache gate. lastLoadDate MUST be cleared for the same reason.
        lastLoadDate = null
        // DES-161 D4b: clear the in-flight guard so the loadHomeData() call that
        // follows a profile switch actually runs. If a previous load was still
        // in flight, loadHomeData()'s `if (loadInFlight) return` would silently
        // drop the re-fetch, leaving yogas (just cleared below) permanently empty.
        loadInFlight = false
        _uiState.update {
            it.copy(
                isLoading = true,
                dailyInsight = null,
                transits = emptyList(),
                dashaInfo = null,
                yogas = emptyList(),
                doshas = HomeDoshaStatus(),
                lifeAreas = defaultLifeAreas(),
                unreadCount = 0,
                errorMessage = null,
                ascendantSign = "",
                suggestedQuestions = emptyList(),
                briefLifeArea = null,
                selectedLifeArea = null,
                selectedYoga = null,
                yogaFilter = YogaFilter.All,
            )
        }
    }

    private fun updateQuotaState(user: User?, quota: Int, used: Int, storedName: String? = null) {
        val unlimited = quota < 0
        val remaining = if (unlimited) Int.MAX_VALUE else maxOf(0, quota - used)
        val progress = if (unlimited || quota == 0) 0f else used.toFloat() / quota.toFloat()
        // iOS parity: profileContext.activeProfileName uses UserDefaults["userName"] (full name
        // captured at signup) ahead of any backend-truncated `name`. Prefer the prefs value when
        // available so the greeting + avatar initials show the user's full name (e.g. "Prabhu
        // Kushwaha" → "PK"), falling back to backend `user.name`, then the email prefix.
        // Guest fallback "there" mirrors iOS HomeViewModel.swift:553-558.
        val name = when {
            user == null || user.isGuestEmail -> "there"
            !storedName.isNullOrBlank() -> storedName
            user.name != null -> user.name.split(" ").firstOrNull()?.takeIf { it.isNotBlank() } ?: user.name
            else -> user.email.substringBefore("@")
        }
        // iOS parity HomeViewModel.swift:13,144-148 — compute the next renewal date
        // (1st of next month) so the UI can display "Renews <Mon d>" without a
        // separate fetch. Free / unlimited plans show null.
        val renewalIso = if (unlimited || quota == 0) null else nextRenewalIsoDate()
        val renewalString = renewalIso?.let { runCatching { formatRenewalDate(it) }.getOrNull() }
        // Premium / plan display — iOS HomeViewModel.swift:19-20,506-512.
        val premium = user?.isPremium == true
        val planName = user?.planId?.takeIf { it.isNotBlank() } ?: ""
        _uiState.update {
            it.copy(
                currentUser = user,
                displayName = name,
                isGuest = user == null || user.isGuestEmail,
                isPremium = premium,
                planDisplayName = planName,
                renewalDateString = renewalString,
                dailyQuota = quota,
                dailyUsed = used,
                remaining = remaining,
                quotaProgress = progress,
                isUnlimited = unlimited,
                isLoading = false,
            )
        }
    }

    /** First of next month in ISO yyyy-MM-dd. iOS HomeViewModel.swift:144-148 parity. */
    private fun nextRenewalIsoDate(): String {
        val today = LocalDate.now()
        val nextMonthFirst = today.withDayOfMonth(1).plusMonths(1)
        return nextMonthFirst.toString()
    }

    fun loadHomeData(manualRefresh: Boolean = false) {
        if (loadInFlight) return
        loadInFlight = true
        // DES-161: drive the pull-to-refresh indicator off a dedicated flag so it
        // always retracts — even on the fast 24h cache-hit path that never touches
        // isLoading/isRichDataLoading. Set here, cleared in the finally below.
        if (manualRefresh) _uiState.update { it.copy(isRefreshing = true) }
        viewModelScope.launch {
            try {
            // iOS parity (HomeViewModel.swift:159-177): detect a language change
            // since the last successful Home payload — if Settings flipped the
            // app language, force a full re-fetch so localized strings refresh
            // without the user reinstalling.
            val currentLang = prefs.getSelectedLanguage()
            val lastLoadedLang = prefs.getLastLoadedLanguage()
            val languageChanged = lastLoadedLang != null && currentLang != lastLoadedLang

            // iOS parity (HomeViewModel.swift:52-59): per-email 24-hour rolling
            // refresh gate. Skip the full reload when the stored last-load epoch
            // is within FRESH_WINDOW_MS of now, regardless of calendar boundaries.
            // Previously this used isSameLocalDay which could refresh at midnight
            // even after a 23h-59m successful load, or skip refresh after 0h-1m
            // if the user crossed midnight.
            val email = prefs.getUserEmail()
            val nowMs = System.currentTimeMillis()
            val storedMs = prefs.getLastFullLoadDate(email)
            val withinWindow = storedMs != null && (nowMs - storedMs) < FRESH_WINDOW_MS
            val haveDataInMemory = _uiState.value.dailyInsight != null &&
                _uiState.value.transits.isNotEmpty()
            val premiumUiStale = quotaManager.isPremium.value != _uiState.value.isPremium
            val canSkipFullReload = !languageChanged && withinWindow && haveDataInMemory && !premiumUiStale

            if (canSkipFullReload) {
                android.util.Log.i(
                    "HomeViewModel",
                    "24h cache hit (lastFullLoadDate gate) — skipping full reload",
                )
                lastLoadDate = LocalDate.now().toString()
                fetchUnreadCount()
                // DES-161: a pull-to-refresh that lands on the 24h cache-hit path
                // still expects isRefreshing to resolve false, or PullToRefreshBox
                // leaves its spinner stuck over the life-area rings. Clear both
                // loading flags explicitly before the early return.
                _uiState.update { it.copy(isLoading = false, isRichDataLoading = false) }
                // On the cache-hit path all code above runs synchronously on
                // Main.immediate before Compose renders a single frame, so
                // isRefreshing never flips true in any rendered frame and
                // PullToRefreshBox's state machine gets stuck. A short delay
                // lets Compose render isRefreshing=true before finally clears it.
                if (manualRefresh) delay(300)
                return@launch
            }

            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            // Re-sync quota and subscription state from backend on every load (parity with iOS syncQuotaFromBackend)
            try {
                val user = repository.getCurrentUser()
                if (user != null) {
                    val storedName = runCatching { prefs.getUserName() }.getOrNull()
                    updateQuotaState(user, user.dailyQuota, user.dailyUsed, storedName)
                }
            } catch (e: Exception) {
                android.util.Log.w("HomeViewModel", "quota sync failed: ${e.message}", e)
            }
            // iOS parity (HomeViewModel.swift:436): birth data + greeting + avatar
            // come from the active profile, not the owner self-profile. When a
            // partner is active activeBirthData() resolves their UUID via
            // listPartners; otherwise falls back to the self birth profile.
            val activeBirth = profileContextManager.activeBirthData()
            if (activeBirth == null) {
                _uiState.update { it.copy(isLoading = false) }
                return@launch
            }
            val activeId = prefs.getActiveProfileId()?.takeIf { it.isNotBlank() } ?: email.orEmpty()
            // Greeting + avatar: prefer the active partner's name (iOS parity
            // HomeView.swift:517,748). Falls back to owner self name when self
            // is active (activeProfileName already encodes that priority).
            val activeName = runCatching { profileContextManager.activeProfileName() }.getOrNull()
            if (!activeName.isNullOrBlank()) {
                _uiState.update { it.copy(displayName = activeName) }
            }
            val (insight, loadError) = try {
                repository.getDailyInsight(activeBirth, activeId, force = languageChanged) to null
            } catch (e: Exception) {
                android.util.Log.w("HomeViewModel", "getDailyInsight failed: ${e.message}", e)
                "" to friendlyError(e)
            }
            // Fetch suggested "mind" questions AFTER getDailyInsight() so the
            // server's language-aware, chart-personalized list (carried on the
            // todays-prediction response) is already in the in-memory cache.
            // Calling it first returned the static English fallback on cold start,
            // so non-English users saw English question text until a refresh.
            val questions = repository.getSuggestedQuestions()
            // iOS parity (HomeViewModel.swift:328-332): suppress errorMessage when
            // cached content is already on screen. A silent refresh failure should
            // not flash a banner over a working Home; only show errors when there
            // is truly nothing to display.
            val hasOnScreenContent = _uiState.value.dailyInsight != null ||
                _uiState.value.dashaInfo != null ||
                _uiState.value.transits.isNotEmpty() ||
                _uiState.value.yogas.isNotEmpty()
            _uiState.update {
                it.copy(
                    isLoading = false,
                    // Raise isRichDataLoading here so showHeroLoader stays true with
                    // no gap between isLoading going false and loadRichHomeData raising it
                    // — prevents the empty-yoga flash during a profile switch.
                    isRichDataLoading = true,
                    suggestedQuestions = questions,
                    dailyInsight = insight.ifBlank { it.dailyInsight },
                    errorMessage = when {
                        loadError == null -> it.errorMessage
                        hasOnScreenContent -> it.errorMessage  // silent refresh failure
                        insight.isBlank() -> loadError
                        else -> it.errorMessage
                    },
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
            loadRichHomeData(activeBirth, activeId)
            } finally {
                loadInFlight = false
                // DES-161: always clear the manual-refresh flag, on every exit path
                // (cache-hit early return, null-birth return, success, or throw), so
                // PullToRefreshBox never leaves its indicator stuck.
                if (manualRefresh) _uiState.update { it.copy(isRefreshing = false) }
            }
        }
    }

    /**
     * iOS parity (HomeViewModel.swift:52-59): two epoch-millis instants land on
     * the same local calendar day iff their startOfDay values are identical.
     * Retained for any callers that still want calendar-boundary semantics.
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
        // iOS parity (HomeViewModel.swift:604-631): distinguish validation / invalid-email
        // / no-data / session-expired before the generic fallback so users get actionable text.
        val msg = e.message?.lowercase().orEmpty()
        return when {
            e is java.net.SocketTimeoutException -> "Request timed out. Please try again."
            e is java.io.IOException -> "Network unavailable. Check your connection."
            msg.contains("401") || msg.contains("session") || msg.contains("unauthor") ->
                "Session expired. Please sign in again."
            msg.contains("invalid") && msg.contains("email") ->
                "Your account email looks invalid. Please sign in again."
            msg.contains("validation") ->
                "Please check your birth details and try again."
            msg.contains("no data") || msg.contains("empty") ->
                "No reading available yet. Please try again shortly."
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

    fun loadRichHomeData(
        birthOverride: BirthProfileDto? = null,
        profileCacheIdOverride: String? = null,
    ) {
        viewModelScope.launch {
            val email = prefs.getUserEmail() ?: return@launch
            // iOS parity (HomeViewModel.swift:436): chart/dasha/transits use the
            // active profile's birth data + a profile-scoped cache key so the
            // partner's chart isn't keyed against the owner's email row.
            val birth = birthOverride ?: profileContextManager.activeBirthData() ?: return@launch
            val cacheKey = profileCacheIdOverride
                ?: prefs.getActiveProfileId()?.takeIf { it.isNotBlank() }
                ?: email
            _uiState.update { it.copy(isRichDataLoading = true) }
            // DES-161 D2: guarantee the loading flag clears even if getRichHomeData
            // throws — otherwise isRichDataLoading stays true forever and the
            // pull-to-refresh spinner gets stuck (PullToRefreshBox keys on it).
            try {
                val richData = repository.getRichHomeData(email, birth, cacheKey)
                if (richData != null) {
                    _uiState.update {
                        it.copy(
                            transits = richData.transits,
                            dashaInfo = richData.dashaInfo,
                            yogas = richData.yogas,
                            doshas = richData.doshas,
                            lifeAreas = richData.lifeAreas,
                            ascendantSign = richData.ascendantSign,
                        )
                    }
                }
            } finally {
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
        // iOS parity (HomeView.swift:244-247 context_life_area_question).
        askDestiny(
            appContext.getString(
                com.destinyai.astrology.R.string.context_life_area_question_android, text, brief.name,
            )
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
        // iOS parity (HomeView.swift:210-212 context_transit_question): compose the
        // card→chat prompt from the localized format string so a non-English user's
        // tapped-card question is worded in their language.
        val prompt = appContext.getString(
            com.destinyai.astrology.R.string.context_transit_question_android,
            transit.planet,
            transit.sign,
            transit.house.coerceAtLeast(0),
            transit.description,
        )
        askDestiny(prompt)
    }

    fun onDashaTapped() {
        val dasha = _uiState.value.dashaInfo ?: return
        val theme = dasha.theme.orEmpty()
        val quality = dasha.quality.orEmpty()
        val meaning = dasha.meaning.orEmpty()
        // iOS parity (HomeView.swift:187-199 context_dasha_question): compose {period,
        // theme, quality, meaning} into the localized format string so the tapped-card
        // question is worded in the user's language.
        val period = buildString {
            append(dasha.mahadasha)
            if (dasha.antardasha.isNotBlank()) {
                append("-")
                append(dasha.antardasha)
            }
        }
        val phaseSuggests = if (meaning.isNotBlank()) {
            appContext.getString(
                com.destinyai.astrology.R.string.context_dasha_phase_suggests_android, meaning,
            )
        } else ""
        val prompt = appContext.getString(
            com.destinyai.astrology.R.string.context_dasha_question_android,
            period, theme, quality, phaseSuggests,
        )
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
        fun s(id: Int, vararg args: Any) = appContext.getString(id, *args)
        parts += s(com.destinyai.astrology.R.string.yoga_context_intro_android, yoga.name)
        parts += ""
        parts += s(com.destinyai.astrology.R.string.yoga_context_details_header_android)
        parts += s(com.destinyai.astrology.R.string.yoga_context_type_android, typeText)
        parts += s(com.destinyai.astrology.R.string.yoga_context_category_android, yoga.category)
        parts += s(com.destinyai.astrology.R.string.yoga_context_status_android, statusText)
        if (yoga.strength > 0) parts += s(com.destinyai.astrology.R.string.yoga_context_strength_android, yoga.strength)
        if (yoga.planets.isNotBlank()) parts += s(com.destinyai.astrology.R.string.yoga_context_planets_android, yoga.planets)
        if (yoga.houses.isNotBlank()) parts += s(com.destinyai.astrology.R.string.yoga_context_houses_android, yoga.houses)
        if (yoga.formation.isNotBlank()) parts += s(com.destinyai.astrology.R.string.yoga_context_formation_android, yoga.formation)
        if (yoga.outcome.isNotBlank()) parts += s(com.destinyai.astrology.R.string.yoga_context_outcome_android, yoga.outcome)
        if (yoga.reductionReason.isNotBlank() && !statusText.equals("Active", ignoreCase = true)) {
            parts += if (statusText.equals("Reduced", ignoreCase = true)) {
                s(com.destinyai.astrology.R.string.yoga_context_reduction_reason_android, yoga.reductionReason)
            } else {
                s(com.destinyai.astrology.R.string.yoga_context_cancellation_reason_android, yoga.reductionReason)
            }
        }
        parts += ""
        parts += s(com.destinyai.astrology.R.string.yoga_context_explain_header_android)
        parts += s(com.destinyai.astrology.R.string.yoga_context_q_meaning_android, typeText.lowercase())
        if (statusText.equals("Active", ignoreCase = true)) {
            parts += if (yoga.isDosha) {
                s(com.destinyai.astrology.R.string.yoga_context_q_dosha_active_android)
            } else {
                s(com.destinyai.astrology.R.string.yoga_context_q_yoga_active_android)
            }
        } else {
            parts += s(com.destinyai.astrology.R.string.yoga_context_q_inactive_android, statusText.lowercase())
            parts += s(com.destinyai.astrology.R.string.yoga_context_q_subtle_android)
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
        // 24-hour rolling refresh window — parity with iOS HomeViewModel.swift:52-59.
        // After a successful Home load, skip the next full reload until 24h have
        // elapsed. Beats calendar-day comparison which can cause spurious midnight
        // reloads or skip a stale-but-fresh-yesterday cache.
        private const val FRESH_WINDOW_MS = 24L * 60L * 60L * 1000L

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
