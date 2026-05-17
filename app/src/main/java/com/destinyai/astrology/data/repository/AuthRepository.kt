package com.destinyai.astrology.data.repository

import com.destinyai.astrology.domain.model.User

interface AuthRepository {
    suspend fun getSavedUser(): User?
    suspend fun signInWithGoogle(idToken: String): Result<User>
    suspend fun registerGuest(): Result<User>
    suspend fun upgradeGuest(guestEmail: String, newEmail: String): Result<User>
    suspend fun clearSession()
}
