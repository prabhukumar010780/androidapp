package com.destinyai.astrology.data.repository.impl

import com.destinyai.astrology.data.local.prefs.UserPreferences
import com.destinyai.astrology.data.remote.AstroApiService
import com.destinyai.astrology.data.remote.PredictBirthDataDto
import com.destinyai.astrology.data.repository.HomeRepository
import com.destinyai.astrology.domain.model.User
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
                    userId = email,
                    birthData = PredictBirthDataDto(
                        dateOfBirth = birth.dateOfBirth,
                        timeOfBirth = birth.timeOfBirth,
                        cityOfBirth = birth.cityOfBirth,
                        latitude = birth.latitude,
                        longitude = birth.longitude,
                    ),
                )
            )
            resp.text
        }.getOrDefault("")
    }
}
