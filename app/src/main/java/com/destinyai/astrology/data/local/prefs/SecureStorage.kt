package com.destinyai.astrology.data.local.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SecureStorage @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "destiny_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    fun saveEmail(email: String) = prefs.edit().putString(KEY_EMAIL, email).apply()
    fun getEmail(): String? = prefs.getString(KEY_EMAIL, null)

    fun saveAuthToken(token: String) = prefs.edit().putString(KEY_AUTH_TOKEN, token).apply()
    fun getAuthToken(): String? = prefs.getString(KEY_AUTH_TOKEN, null)

    fun saveRefreshToken(token: String) = prefs.edit().putString(KEY_REFRESH_TOKEN, token).apply()
    fun getRefreshToken(): String? = prefs.getString(KEY_REFRESH_TOKEN, null)

    fun saveUserId(userId: String) = prefs.edit().putString(KEY_USER_ID, userId).apply()
    fun getUserId(): String? = prefs.getString(KEY_USER_ID, null)

    // iOS parity (AppleAuthService.swift:108-127): Apple only returns email/name on
    // FIRST sign-in. Persist them into the encrypted store keyed by Apple userId so
    // subsequent logins can recover them. Companion UserPreferences mirrors the same
    // values into DataStore as a fallback (matches iOS UserDefaults dual-store).
    fun saveAppleEmail(userId: String, email: String) =
        prefs.edit().putString("$KEY_APPLE_EMAIL_PREFIX$userId", email).apply()

    fun getAppleEmail(userId: String): String? =
        prefs.getString("$KEY_APPLE_EMAIL_PREFIX$userId", null)

    fun saveAppleName(userId: String, name: String) =
        prefs.edit().putString("$KEY_APPLE_NAME_PREFIX$userId", name).apply()

    fun getAppleName(userId: String): String? =
        prefs.getString("$KEY_APPLE_NAME_PREFIX$userId", null)

    fun clearAll() = prefs.edit().clear().apply()

    private companion object {
        const val KEY_EMAIL = "email"
        const val KEY_AUTH_TOKEN = "auth_token"
        const val KEY_REFRESH_TOKEN = "refresh_token"
        const val KEY_USER_ID = "user_id"
        // iOS parity (AppleAuthService.swift:111,116) — keys are namespaced by Apple userId.
        const val KEY_APPLE_EMAIL_PREFIX = "appleEmail_"
        const val KEY_APPLE_NAME_PREFIX = "appleName_"
    }
}
