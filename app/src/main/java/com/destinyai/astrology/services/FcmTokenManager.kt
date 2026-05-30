package com.destinyai.astrology.services

import com.destinyai.astrology.BuildConfig
import com.destinyai.astrology.data.local.prefs.UserPreferences
import com.destinyai.astrology.data.remote.AstroApiService
import com.destinyai.astrology.data.remote.DeviceTokenRequest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FcmTokenManager @Inject constructor(
    private val api: AstroApiService,
    private val prefs: UserPreferences,
) {
    suspend fun registerToken(token: String, appVersion: String) {
        prefs.setFcmToken(token)
        val email = prefs.getUserEmail() ?: return
        try {
            api.registerDeviceToken(
                DeviceTokenRequest(
                    userEmail = email,
                    token = token,
                    platform = "android",
                    appVersion = appVersion,
                )
            )
            prefs.setFcmTokenRegistered(true)
        } catch (_: Exception) {}
    }
}
