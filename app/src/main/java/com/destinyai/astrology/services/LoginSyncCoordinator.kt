package com.destinyai.astrology.services

import android.util.Log
import com.destinyai.astrology.data.remote.AstroApiService
import com.destinyai.astrology.data.remote.ChatThreadDto
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Mirrors iOS LoginSyncCoordinator — fetches thread list exactly once after
 * login and returns it. Isolates the single-fetch invariant so it is testable
 * independently of AuthViewModel.
 */
@Singleton
class LoginSyncCoordinator @Inject constructor(
    private val api: AstroApiService,
) {
    suspend fun syncAfterLogin(userId: String): List<ChatThreadDto> {
        return try {
            api.listChatThreads(userId)
        } catch (e: Exception) {
            Log.w(TAG, "LoginSyncCoordinator: thread sync failed — ${e.message}")
            emptyList()
        }
    }

    private companion object {
        const val TAG = "LoginSyncCoordinator"
    }
}
