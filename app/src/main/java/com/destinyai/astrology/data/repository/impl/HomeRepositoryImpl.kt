package com.destinyai.astrology.data.repository.impl

import com.destinyai.astrology.data.local.db.AstroDataCacheDao
import com.destinyai.astrology.data.local.db.AstroDataCacheEntity
import com.destinyai.astrology.data.local.prefs.UserPreferences
import com.destinyai.astrology.data.remote.AstroApiService
import com.destinyai.astrology.data.remote.BirthProfileDto
import com.destinyai.astrology.data.repository.HomeRepository
import com.destinyai.astrology.domain.model.User
import com.destinyai.astrology.ui.charts.BirthData
import com.destinyai.astrology.ui.charts.ChartDataRequest
import com.destinyai.astrology.ui.charts.DashaPeriod
import com.destinyai.astrology.ui.charts.DashaResponse
import com.destinyai.astrology.ui.charts.DashaTransitRequest
import com.destinyai.astrology.ui.home.HomeDashaInfo
import com.destinyai.astrology.ui.home.HomeDoshaStatus
import com.destinyai.astrology.ui.home.HomeRichData
import com.destinyai.astrology.ui.home.HomeTransit
import com.destinyai.astrology.ui.home.HomeYoga
import com.destinyai.astrology.ui.home.HomeLifeArea
import com.destinyai.astrology.ui.home.LifeAreaStatus
import com.destinyai.astrology.ui.home.defaultLifeAreas
import com.google.gson.Gson
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.security.MessageDigest
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HomeRepositoryImpl @Inject constructor(
    private val api: AstroApiService,
    private val prefs: UserPreferences,
    private val astroDataCacheDao: AstroDataCacheDao,
) : HomeRepository {

    override suspend fun getCurrentUser(): User? {
        val email = prefs.getUserEmail() ?: return null
        return runCatching {
            api.getStatus(email).let { s ->
                User(
                    email = s.userEmail,
                    isGuestEmail = s.isGeneratedEmail,
                    name = s.name,
                    isPremium = s.isPremium,
                    planId = s.planId ?: "free_guest",
                    dailyQuota = s.dailyQuota ?: 0,
                    dailyUsed = s.dailyUsed ?: 0,
                    accessState = s.accessState,
                )
            }
        }.getOrNull()
    }

    override suspend fun getDailyQuota(): Int = prefs.getDailyQuota()

    override suspend fun getDailyUsed(): Int = prefs.getDailyUsed()

    override suspend fun getSuggestedQuestions(): List<String> {
        // Parity with iOS HomeViewModel: prefer server-generated, language-aware mind questions
        // (UserAstroDataRequest.language) returned in the todays-prediction response. Fall back
        // to a static English list only when the prediction has not been fetched yet OR when
        // the server omitted suggestions for the user's birth profile.
        val serverList = lastTodaysPrediction?.suggested_questions
        if (!serverList.isNullOrEmpty()) return serverList
        return listOf(
            "What should I be mindful of today?",
            "How can I improve my focus and productivity?",
            "What's a good time for important decisions?",
            "What does my chart say about relationships?",
        )
    }

    override suspend fun getDailyInsight(birth: BirthProfileDto, profileCacheId: String): String {
        val email = prefs.getUserEmail() ?: return ""
        // iOS parity (TodaysPredictionCache.swift:32-53): if a today-keyed cache
        // entry exists for this profile, hydrate the in-memory lastTodaysPrediction
        // immediately so HomeView renders without a network call. The cache is
        // day-keyed (YYYYMMDD encoded in `month`) so two consecutive days do not
        // collide in the same Room (kind, profile_id, birth_hash, year, month) PK.
        val today = LocalDate.now()
        val birthHash = computeBirthHash(birth)
        val dayKey = today.year * 10_000 + today.monthValue * 100 + today.dayOfMonth
        val cachedEntity = runCatching {
            astroDataCacheDao.get(
                kind = TODAY_KIND,
                profileId = profileCacheId,
                birthHash = birthHash,
                year = today.year,
                month = dayKey,
            )
        }.getOrNull()
        if (cachedEntity != null) {
            val cachedResp = runCatching {
                Gson().fromJson(
                    cachedEntity.payloadJson,
                    com.destinyai.astrology.data.remote.TodaysPredictionResponse::class.java,
                )
            }.getOrNull()
            if (cachedResp?.text != null) {
                lastTodaysPrediction = cachedResp
                return cachedResp.text.orEmpty()
            }
        }

        // iOS parity (HomeViewModel.swift:296-317): on first login, force-bypass the cache and
        // send is_first_login=true so backend can deliver the fixed onboarding question. After
        // a successful response we flip the per-user flag so subsequent loads use the cache.
        val isFirstLogin = !prefs.hasSeenFirstPrediction()
        val language = prefs.getSelectedLanguage()
        // Bubble up exceptions so the VM can show an error banner with retry (parity with iOS)
        val resp = api.getTodaysPrediction(
            authHeader = "Bearer ${com.destinyai.astrology.BuildConfig.API_KEY}",
            req = com.destinyai.astrology.data.remote.UserAstroDataRequest(
                birth_data = mapOf(
                    "email" to email,
                    "dob" to birth.dateOfBirth,
                    "time" to birth.timeOfBirth,
                    "city_of_birth" to birth.cityOfBirth,
                    "latitude" to birth.latitude,
                    "longitude" to birth.longitude,
                ),
                user_email = email,
                language = language,
                is_first_login = isFirstLogin,
            ),
        )
        // Cache the latest todays-prediction so getRichHomeData can populate dasha + transits
        lastTodaysPrediction = resp
        if (isFirstLogin) prefs.setHasSeenFirstPrediction(true)
        // iOS parity (TodaysPredictionCache.swift:56-65): persist the response
        // under the day-keyed primary key so a second cold-start the same day
        // skips the network call entirely.
        runCatching {
            astroDataCacheDao.upsert(
                AstroDataCacheEntity(
                    kind = TODAY_KIND,
                    profileId = profileCacheId,
                    birthHash = birthHash,
                    year = today.year,
                    month = dayKey,
                    ownerEmail = email,
                    payloadJson = Gson().toJson(resp),
                    savedAtMs = System.currentTimeMillis(),
                ),
            )
        }
        return resp.text.orEmpty()
    }

    /**
     * iOS parity (AstroDataCache.swift birthHash): a stable digest of the
     * birth profile fields the backend keys predictions by. Used to invalidate
     * cached payloads when the profile data changes (e.g. user edits DOB).
     */
    private fun computeBirthHash(profile: BirthProfileDto): String {
        val raw = "${profile.dateOfBirth}|${profile.timeOfBirth}|${profile.latitude}|" +
            "${profile.longitude}|${profile.cityOfBirth}|${profile.birthTimeUnknown}"
        val bytes = MessageDigest.getInstance("SHA-1").digest(raw.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    companion object {
        /** Discriminator for the today-prediction kind in astro_data_cache. */
        private const val TODAY_KIND = "today"

        /**
         * Full English sign names indexed by signNum-1. Returned to HomeScreen
         * so localizedSignName(sign) can lowercase-match "gemini" → R.string.sign_ge
         * and resolve to the localized "Gemini" / "मिथुन" / etc. resource. The
         * 2-letter glyphs in ChartConstants.orderedSigns are intentionally kept
         * for the South-Indian chart grid rendering and are NOT used here.
         */
        private val FULL_SIGN_NAMES: List<String> = listOf(
            "Aries", "Taurus", "Gemini", "Cancer", "Leo", "Virgo",
            "Libra", "Scorpio", "Sagittarius", "Capricorn", "Aquarius", "Pisces",
        )
    }

    @Volatile private var lastTodaysPrediction: com.destinyai.astrology.data.remote.TodaysPredictionResponse? = null

    override suspend fun getDashaPeriods(birthProfile: BirthProfileDto): DashaResponse? {
        return runCatching {
            val authHeader = "Bearer ${com.destinyai.astrology.BuildConfig.API_KEY}"
            // Parity with ChatRepositoryImpl — pass user-selected ayanamsa / house_system.
            val ayanamsa = runCatching { prefs.getAyanamsa() }.getOrDefault("lahiri")
            val houseSystem = runCatching { prefs.getHouseSystem() }.getOrDefault("whole_sign")
            val request = DashaTransitRequest(
                birthData = BirthData(
                    dob = birthProfile.dateOfBirth,
                    time = birthProfile.timeOfBirth,
                    latitude = birthProfile.latitude,
                    longitude = birthProfile.longitude,
                    ayanamsa = ayanamsa,
                    houseSystem = houseSystem,
                    cityOfBirth = birthProfile.cityOfBirth,
                    birthTimeUnknown = birthProfile.birthTimeUnknown,
                ),
                year = LocalDate.now().year,
            )
            api.getDashaPeriods(authHeader, request)
        }.getOrNull()
    }

    override suspend fun getRichHomeData(
        email: String,
        birthProfile: BirthProfileDto,
        profileCacheId: String,
    ): HomeRichData? {
        return runCatching {
            // Parity with ChatRepositoryImpl — pass user-selected ayanamsa / house_system.
            val ayanamsa = runCatching { prefs.getAyanamsa() }.getOrDefault("lahiri")
            val houseSystem = runCatching { prefs.getHouseSystem() }.getOrDefault("whole_sign")
            // Parity with iOS HomeViewModel.loadHomeData TaskGroup — fan out chart-data and
            // dasha-periods fetches in parallel instead of running them sequentially. Mirrors
            // the iOS 3-parallel pattern (todays-prediction is fired separately by getDailyInsight).
            val (chartResponse, dashaResponse) = coroutineScope {
                val chartDeferred = async {
                    api.getChartData(
                        ChartDataRequest(
                            birthData = BirthData(
                                dob = birthProfile.dateOfBirth,
                                time = birthProfile.timeOfBirth,
                                latitude = birthProfile.latitude,
                                longitude = birthProfile.longitude,
                                ayanamsa = ayanamsa,
                                houseSystem = houseSystem,
                                cityOfBirth = birthProfile.cityOfBirth,
                                birthTimeUnknown = birthProfile.birthTimeUnknown,
                            ),
                        )
                    )
                }
                val dashaDeferred = async { getDashaPeriods(birthProfile) }
                chartDeferred.await() to dashaDeferred.await()
            }

            // Transits: prefer server-supplied `transit_influences` (parity with iOS
            // HomeViewModel.transitInfluences). Server payload includes planet, sign, house,
            // a description, and a badge type ("positive"/"caution"/"warning"/"neutral") —
            // we map badgeType to isFavorable so the green/red coloring is astrologically
            // correct (a retrograde benefic is no longer mis-classified as unfavorable).
            // Fall back to chart-data planets only if the server payload is missing.
            val serverInfluences = lastTodaysPrediction?.transit_influences
            val transits: List<HomeTransit> = if (!serverInfluences.isNullOrEmpty()) {
                serverInfluences.map { entry ->
                    val planet = (entry["planet"] ?: "").toString()
                    val sign = (entry["sign"] ?: "").toString()
                    val house = (entry["house"] as? Number)?.toInt()
                        ?: (entry["house"] as? String)?.toIntOrNull() ?: 0
                    val description = (entry["description"] ?: "").toString()
                    val badge = (entry["badge"] ?: entry["influence"] ?: "").toString()
                    val badgeType = (entry["badge_type"] ?: entry["badgeType"] ?: "neutral")
                        .toString().lowercase()
                    HomeTransit(
                        planet = planet,
                        sign = sign,
                        influence = badge.ifBlank { description.ifBlank { "—" } },
                        isFavorable = badgeType == "positive",
                        house = house,
                        description = description,
                        badgeType = badgeType,
                    )
                }
            } else {
                // Fallback: build from chart data WITHOUT the artificial major-planet filter
                // and WITHOUT the misleading retrograde→unfavorable mapping. Sun/Moon/Mercury/
                // Venus transits now appear; favorability defaults to neutral (false) so the
                // UI does not lie about benefic/malefic until server data arrives.
                chartResponse.planets.entries.map { (planet, data) ->
                    HomeTransit(
                        planet = planet,
                        sign = data.sign,
                        influence = if (data.isRetrograde == true) "Retrograde" else "Direct",
                        isFavorable = false,
                        house = data.house,
                        description = "",
                        badgeType = "neutral",
                    )
                }
            }

            // Yogas: prefer server-supplied analysis.yogas (parity with iOS HomeViewModel
            // which uses response.analysis.yogas.yogas + response.analysis.yogas.doshas).
            // Two server sources can populate this: /vedic/api/todays-prediction's
            // (rare) analysis block, or /vedic/api/astrodata/full's analysis.yogas
            // (always present when chart data loads). Fall back to the legacy
            // combust/vargottama heuristic only when neither source returns any
            // classical yoga analysis.
            val serverYogas = lastTodaysPrediction?.analysis?.yogas
                ?: chartResponse.analysis?.yogas
            val yogas: List<HomeYoga> = run {
                val combined = mutableListOf<HomeYoga>()
                serverYogas?.yogas?.forEach { y ->
                    combined += HomeYoga(
                        name = y.name,
                        description = y.outcome ?: y.formation ?: "",
                        category = y.category ?: "Other",
                        isActive = (y.status ?: "").equals("active", ignoreCase = true) ||
                            (y.status ?: "").equals("a", ignoreCase = true),
                        status = (y.status ?: "active").lowercase(),
                        cancellationKey = y.reason,
                        planets = y.planets ?: "",
                        houses = y.houses ?: "",
                        formation = y.formation ?: "",
                        strength = ((y.strength ?: 0.0) * 100).toInt().coerceIn(0, 100),
                        outcome = y.outcome ?: "",
                        reductionReason = y.reason ?: "",
                        isDosha = y.isDosha ?: false,
                    )
                }
                serverYogas?.doshas?.forEach { d ->
                    combined += HomeYoga(
                        name = d.name,
                        description = d.outcome ?: d.formation ?: "",
                        category = d.category ?: "Dosha",
                        isActive = (d.status ?: "").equals("active", ignoreCase = true),
                        status = (d.status ?: "active").lowercase(),
                        cancellationKey = d.reason,
                        planets = d.planets ?: "",
                        houses = d.houses ?: "",
                        formation = d.formation ?: "",
                        strength = ((d.strength ?: 0.0) * 100).toInt().coerceIn(0, 100),
                        outcome = d.outcome ?: "",
                        reductionReason = d.reason ?: "",
                        isDosha = d.isDosha ?: true,
                    )
                }
                if (combined.isNotEmpty()) {
                    // Sort by status (active first) then strength desc, like iOS
                    combined.sortedWith(
                        compareByDescending<HomeYoga> {
                            it.status.equals("active", ignoreCase = true) || it.status.equals("a", ignoreCase = true)
                        }
                    )
                } else {
                    // Fallback heuristic — kept only for offline/legacy responses
                    buildList {
                        val hasCombust = chartResponse.planets.values.any { it.isCombust == true }
                        if (!hasCombust) add(HomeYoga("Shubha Yoga", "Benefic planets strong and uncombust"))
                        val hasVargottama = chartResponse.planets.values.any { it.vargottama == true }
                        if (hasVargottama) add(HomeYoga("Vargottama Yoga", "Planet strong in same sign in D9"))
                    }
                }
            }

            // Dosha verdicts — prefer the structured analyze_mangal_dosha / analyze_kala_sarpa
            // payload (parity with iOS doshaStatus = (analysis.mangalDosha, analysis.kalaSarpa)).
            // The naive house-membership rules are kept only as last-resort fallback.
            val serverMangal = lastTodaysPrediction?.analysis?.mangalDosha
                ?: chartResponse.analysis?.mangalDosha
            val serverKalaSarpa = lastTodaysPrediction?.analysis?.kalaSarpa
                ?: chartResponse.analysis?.kalaSarpa

            val marsHouse = chartResponse.planets["Mars"]?.house ?: 0
            val mangalDoshaHouses = setOf(1, 4, 7, 8, 12)
            val fallbackHasMangal = marsHouse in mangalDoshaHouses

            // R2-H Kala Sarpa fix: classical KSD requires ALL 7 visible planets between Rahu and Ketu nodes,
            // NOT merely Rahu/Ketu being 180° apart (which is always true). Compute the arc Rahu → Ketu
            // by house and check that every planet's house lies within that half.
            val rahuHouse = chartResponse.planets["Rahu"]?.house ?: 0
            val ketuHouse = chartResponse.planets["Ketu"]?.house ?: 0
            val visiblePlanets = listOf("Sun", "Moon", "Mars", "Mercury", "Jupiter", "Venus", "Saturn")
            val fallbackHasKalasarpa = if (rahuHouse in 1..12 && ketuHouse in 1..12) {
                val arc = mutableSetOf<Int>()
                var h = (rahuHouse % 12) + 1
                while (h != ketuHouse) {
                    arc.add(h)
                    h = (h % 12) + 1
                }
                val planetHouses = visiblePlanets.mapNotNull { chartResponse.planets[it]?.house }
                planetHouses.size == visiblePlanets.size &&
                    (planetHouses.all { it in arc } || planetHouses.all { it !in arc })
            } else false

            val hasMangal = serverMangal?.hasMangalDosha
                ?.let { it && (serverMangal.isCancelled != true) }
                ?: fallbackHasMangal
            val mangalSeverity = serverMangal?.severity ?: if (fallbackHasMangal) "Moderate" else ""
            val hasKalasarpa = serverKalaSarpa?.yogaPresent ?: fallbackHasKalasarpa
            val kalasarpaType = serverKalaSarpa?.doshaName
                ?: serverKalaSarpa?.axis
                ?: if (fallbackHasKalasarpa) "Full" else ""

            // Populate Dasha card. Parity with iOS HomeViewModel.fetchDashaPeriods +
            // calculateCurrentDashaPeriod: prefer the dedicated /vedic/api/astrodata/dasha
            // endpoint (authoritative period boundaries) and pick the period whose
            // start..end window contains today. Fall back to the cached todays-prediction
            // current_dasha summary when the dasha endpoint failed or returned no
            // matching period (offline / pre-fetch states).
            val dashaInfo: HomeDashaInfo? = run {
                // iOS parity: backend's dasha_insight payload carries quality/theme/
                // meaning that drive the Steady pill + Theme line + Meaning paragraph
                // on DashaInsightCard. Cache it here so all branches below can layer
                // it on top of the period data.
                val insight = lastTodaysPrediction?.dashaInsight
                val today = LocalDate.now()
                val current: DashaPeriod? = dashaResponse?.periods?.firstOrNull { p ->
                    runCatching {
                        val start = LocalDate.parse(p.start)
                        val end = LocalDate.parse(p.end)
                        !today.isBefore(start) && !today.isAfter(end)
                    }.getOrDefault(false)
                }
                val upcoming: DashaPeriod? = dashaResponse?.periods?.let { periods ->
                    val idx = periods.indexOfFirst { it.start == current?.start }
                    if (idx >= 0 && idx + 1 < periods.size) periods[idx + 1] else null
                }
                if (current != null) {
                    HomeDashaInfo(
                        mahadasha = current.mahadasha,
                        antardasha = current.antardasha,
                        endsAt = current.end,
                        upcomingAntardasha = current.pratyantardasha.ifBlank { upcoming?.antardasha },
                        periodStartIso = current.start,
                        periodEndIso = current.end,
                        quality = insight?.quality,
                        theme = insight?.theme,
                        meaning = insight?.meaning,
                    )
                } else {
                    // current_dasha is Any? — backend may send a Map (structured) or a String like "Venus-Rahu-Moon"
                    when (val d = lastTodaysPrediction?.current_dasha) {
                        is Map<*, *> -> {
                            val maha = (d["mahadasha"] ?: d["maha"] ?: "").toString()
                            val antar = (d["antardasha"] ?: d["antar"] ?: "").toString()
                            val ends = (d["ends_at"] ?: d["end_date"] ?: d["end"] ?: "").toString()
                            if (maha.isNotBlank() || antar.isNotBlank()) {
                                HomeDashaInfo(
                                    mahadasha = maha,
                                    antardasha = antar,
                                    endsAt = ends,
                                    quality = insight?.quality,
                                    theme = insight?.theme,
                                    meaning = insight?.meaning,
                                )
                            } else null
                        }
                        is String -> {
                            val parts = d.split('-', '/').map { it.trim() }.filter { it.isNotBlank() }
                            val maha = parts.getOrNull(0).orEmpty()
                            val antar = parts.getOrNull(1).orEmpty()
                            if (maha.isNotBlank() || antar.isNotBlank()) {
                                HomeDashaInfo(
                                    mahadasha = maha,
                                    antardasha = antar,
                                    endsAt = "",
                                    quality = insight?.quality,
                                    theme = insight?.theme,
                                    meaning = insight?.meaning,
                                )
                            } else null
                        }
                        else -> {
                            // Synthesize a minimum HomeDashaInfo from dasha_insight alone
                            // when neither dashaResponse nor current_dasha is populated —
                            // mirrors iOS HomeViewModel which surfaces dashaInsight even
                            // without periods so the card still renders quality/theme.
                            insight?.let {
                                val parts = it.period.orEmpty().split('-', '/').map { s -> s.trim() }
                                val maha = parts.getOrNull(0).orEmpty()
                                val antar = parts.getOrNull(1).orEmpty()
                                if (maha.isBlank() && antar.isBlank()) null
                                else HomeDashaInfo(
                                    mahadasha = maha,
                                    antardasha = antar,
                                    endsAt = it.endDate.orEmpty(),
                                    quality = it.quality,
                                    theme = it.theme,
                                    meaning = it.meaning,
                                )
                            }
                        }
                    }
                }
            }

            HomeRichData(
                transits = transits,
                dashaInfo = dashaInfo,
                yogas = yogas,
                doshas = HomeDoshaStatus(
                    hasMangalDosha = hasMangal,
                    hasKalasarpa = hasKalasarpa,
                    mangalSeverity = mangalSeverity,
                    kalasarpaType = kalasarpaType,
                ),
                lifeAreas = mapLifeAreas(lastTodaysPrediction?.life_areas),
                ascendantSign = run {
                    // Parity with ChartsViewModel — derive ascendant sign name from
                    // houses["1"].signNum so the Home greeting subtitle (R2-H30)
                    // can render "Gemini Ascendant" without a separate API call.
                    // Use the full English sign name (not the 2-letter glyph from
                    // ChartConstants.orderedSigns) so HomeScreen.localizedSignName
                    // can map it to the localized R.string.sign_* resource.
                    val signNum = chartResponse.houses["1"]?.signNum ?: 1
                    val idx = (signNum - 1).coerceIn(0, 11)
                    FULL_SIGN_NAMES.getOrNull(idx).orEmpty()
                },
            )
        }.getOrNull()
    }
}

/**
 * Merges server life_areas payload into the default area list.
 * Mirrors iOS applyPredictionResponse (HomeViewModel.swift:401-411):
 *   - status "Good"/"Excellent" → Positive (green)
 *   - status "Caution"/"Difficult"/"Challenging" → Caution (orange/red)
 *   - "Steady" or any other → Neutral (gold)
 *   - Populates briefDescription from the server "brief" field
 *   - If no area maps to Positive, forces the first (alphabetically) to Positive
 *     so there's always at least one green orb (iOS green-guarantee, line 408-411)
 * Falls back to defaultLifeAreas() when the payload is null or empty.
 */
private fun mapLifeAreas(
    payload: Map<String, Map<String, Any?>>?,
): List<HomeLifeArea> {
    val defaults = defaultLifeAreas()
    if (payload.isNullOrEmpty()) return defaults

    val mapped: List<HomeLifeArea> = defaults.map { area ->
        // Match case-insensitively: "Career" matches "career", etc.
        val serverEntry: Map<String, Any?>? = payload.entries.firstOrNull {
            it.key.equals(area.name, ignoreCase = true)
        }?.value

        if (serverEntry == null) {
            area // keep default (Neutral, default brief)
        } else {
            val statusStr = (serverEntry["status"] as? String).orEmpty()
            val brief = (serverEntry["brief"] as? String).orEmpty()
            val status = when (statusStr.lowercase()) {
                "good", "excellent" -> LifeAreaStatus.Positive
                "caution", "difficult", "challenging" -> LifeAreaStatus.Caution
                else -> LifeAreaStatus.Neutral
            }
            area.copy(
                status = status,
                briefDescription = brief.ifEmpty { area.briefDescription },
            )
        }
    }

    // iOS green-guarantee: at least one Positive orb
    return if (mapped.none { it.status == LifeAreaStatus.Positive }) {
        val list = mapped.toMutableList()
        if (list.isNotEmpty()) list[0] = list[0].copy(status = LifeAreaStatus.Positive)
        list
    } else mapped
}
