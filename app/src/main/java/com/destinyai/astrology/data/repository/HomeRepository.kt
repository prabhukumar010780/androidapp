package com.destinyai.astrology.data.repository

import com.destinyai.astrology.data.remote.BirthProfileDto
import com.destinyai.astrology.domain.model.User
import com.destinyai.astrology.ui.home.HomeRichData

interface HomeRepository {
    suspend fun getCurrentUser(): User?
    suspend fun getDailyQuota(): Int
    suspend fun getDailyUsed(): Int
    suspend fun getSuggestedQuestions(): List<String>
    suspend fun getDailyInsight(): String
    suspend fun getRichHomeData(email: String, birthProfile: BirthProfileDto): HomeRichData?
}
