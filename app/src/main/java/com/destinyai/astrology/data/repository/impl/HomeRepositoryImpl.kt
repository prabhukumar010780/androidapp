package com.destinyai.astrology.data.repository.impl

import com.destinyai.astrology.data.local.prefs.UserPreferences
import com.destinyai.astrology.data.remote.AstroApiService
import com.destinyai.astrology.data.remote.BirthProfileDto
import com.destinyai.astrology.data.remote.PredictBirthDataDto
import com.destinyai.astrology.data.repository.HomeRepository
import com.destinyai.astrology.domain.model.User
import com.destinyai.astrology.ui.charts.ChartDataRequest
import com.destinyai.astrology.ui.home.HomeDashaInfo
import com.destinyai.astrology.ui.home.HomeDoshaStatus
import com.destinyai.astrology.ui.home.HomeRichData
import com.destinyai.astrology.ui.home.HomeTransit
import com.destinyai.astrology.ui.home.HomeYoga
import com.destinyai.astrology.ui.home.defaultLifeAreas
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HomeRepositoryImpl @Inject constructor(
    private val api: AstroApiService,
    private val prefs: UserPreferences,
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
                    planId = s.planId,
                    dailyQuota = s.dailyQuota,
                    dailyUsed = s.dailyUsed,
                    accessState = s.accessState,
                )
            }
        }.getOrNull()
    }

    override suspend fun getDailyQuota(): Int = prefs.getDailyQuota()

    override suspend fun getDailyUsed(): Int = prefs.getDailyUsed()

    override suspend fun getSuggestedQuestions(): List<String> = listOf(
        "What should I be mindful of today?",
        "How can I improve my focus and productivity?",
        "What's a good time for important decisions?",
        "What does my chart say about relationships?",
    )

    override suspend fun getDailyInsight(): String {
        val email = prefs.getUserEmail() ?: return ""
        val birth = prefs.getBirthProfile() ?: return ""
        return runCatching {
            val resp = api.predict(
                com.destinyai.astrology.data.remote.PredictRequest(
                    query = "Give me a brief daily insight for today",
                    userEmail = email,
                    birthData = PredictBirthDataDto(
                        dob = birth.dateOfBirth,
                        time = birth.timeOfBirth,
                        cityOfBirth = birth.cityOfBirth,
                        latitude = birth.latitude,
                        longitude = birth.longitude,
                    ),
                )
            )
            resp.text
        }.getOrDefault("")
    }

    override suspend fun getRichHomeData(email: String, birthProfile: BirthProfileDto): HomeRichData? {
        return runCatching {
            val chartResponse = api.getChartData(
                ChartDataRequest(
                    dob = birthProfile.dateOfBirth,
                    time = birthProfile.timeOfBirth,
                    latitude = birthProfile.latitude,
                    longitude = birthProfile.longitude,
                )
            )

            // R2-H22: only keep major planets in transits
            val majorPlanets = setOf("Saturn", "Jupiter", "Rahu", "Ketu", "Mars")
            val transits = chartResponse.planets.entries
                .filter { (planet, _) -> planet in majorPlanets }
                .map { (planet, data) ->
                    HomeTransit(
                        planet = planet,
                        sign = data.sign,
                        influence = if (data.isRetrograde == true) "Retrograde" else "Direct",
                        isFavorable = data.isRetrograde != true,
                    )
                }

            // Yoga detection from chart data — placeholder using combust/vargottama flags
            val yogas = buildList {
                val hasCombust = chartResponse.planets.values.any { it.isCombust == true }
                if (!hasCombust) add(HomeYoga("Shubha Yoga", "Benefic planets strong and uncombust"))
                val hasVargottama = chartResponse.planets.values.any { it.vargottama == true }
                if (hasVargottama) add(HomeYoga("Vargottama Yoga", "Planet strong in same sign in D9"))
            }

            // Dosha detection — Mars in 1/4/7/8/12 indicates Mangal Dosha
            val marsHouse = chartResponse.planets["Mars"]?.house ?: 0
            val mangalDoshaHouses = setOf(1, 4, 7, 8, 12)
            val hasMangal = marsHouse in mangalDoshaHouses

            // Kalasarpa — Rahu and Ketu on opposite sides, all planets between them
            val rahuHouse = chartResponse.planets["Rahu"]?.house ?: 0
            val ketuHouse = chartResponse.planets["Ketu"]?.house ?: 0
            val hasKalasarpa = rahuHouse != 0 && ketuHouse != 0 &&
                Math.abs(rahuHouse - ketuHouse) == 6

            HomeRichData(
                transits = transits,
                yogas = yogas,
                doshas = HomeDoshaStatus(
                    hasMangalDosha = hasMangal,
                    hasKalasarpa = hasKalasarpa,
                    mangalSeverity = if (hasMangal) "Moderate" else "",
                    kalasarpaType = if (hasKalasarpa) "Full" else "",
                ),
                lifeAreas = defaultLifeAreas(),
            )
        }.getOrNull()
    }
}
