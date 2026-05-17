package com.destinyai.astrology.data.repository

import com.destinyai.astrology.domain.model.User

interface HomeRepository {
    suspend fun getCurrentUser(): User?
    suspend fun getDailyQuota(): Int
    suspend fun getDailyUsed(): Int
    suspend fun getSuggestedQuestions(): List<String>
    suspend fun getDailyInsight(): String
}
