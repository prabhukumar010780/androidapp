package com.destinyai.astrology.data.local.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.destinyai.astrology.data.remote.BirthProfileDto
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "destiny_prefs")

@Singleton
class UserPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val store get() = context.dataStore

    private object Keys {
        val USER_EMAIL = stringPreferencesKey("user_email")
        val USER_NAME = stringPreferencesKey("user_name")
        val IS_ONBOARDING_COMPLETE = booleanPreferencesKey("is_onboarding_complete")
        val HAS_COMPLETED_LANGUAGE_SELECTION = booleanPreferencesKey("hasCompletedLanguageSelection")
        val HAS_SEEN_ONBOARDING = booleanPreferencesKey("hasSeenOnboarding")
        val IS_AUTHENTICATED = booleanPreferencesKey("isAuthenticated")
        val IS_GUEST = booleanPreferencesKey("isGuest")
        val HAS_BIRTH_DATA = booleanPreferencesKey("hasBirthData")
        val LAST_ACCESS_STATE = stringPreferencesKey("lastAccessState")
        val SELECTED_LANGUAGE = stringPreferencesKey("selected_language")
        val CHART_STYLE = stringPreferencesKey("chart_style")
        val RESPONSE_STYLE = stringPreferencesKey("response_style")
        val RESPONSE_LENGTH = stringPreferencesKey("response_length")
        val ACTIVE_PROFILE_EMAIL = stringPreferencesKey("active_profile_email")
        val BIRTH_DOB = stringPreferencesKey("birth_dob")
        val BIRTH_TIME = stringPreferencesKey("birth_time")
        val BIRTH_CITY = stringPreferencesKey("birth_city")
        val BIRTH_LATITUDE = doublePreferencesKey("birth_latitude")
        val BIRTH_LONGITUDE = doublePreferencesKey("birth_longitude")
        val BIRTH_GENDER = stringPreferencesKey("birth_gender")
        val BIRTH_TIME_UNKNOWN = booleanPreferencesKey("birth_time_unknown")
        val DAILY_QUOTA = intPreferencesKey("daily_quota")
        val DAILY_USED = intPreferencesKey("daily_used")
        val LAST_QUOTA_RESET_DATE = stringPreferencesKey("last_quota_reset_date")
        val IS_PREMIUM = booleanPreferencesKey("is_premium")
        val PLAN_ID = stringPreferencesKey("plan_id")
        val ACCESS_STATE = stringPreferencesKey("access_state")
        val NOTIF_DAILY_INSIGHT = booleanPreferencesKey("notif_daily_insight")
        val NOTIF_TRANSITS = booleanPreferencesKey("notif_transits")
        val NOTIF_COMPATIBILITY = booleanPreferencesKey("notif_compatibility")
        val FCM_TOKEN = stringPreferencesKey("fcm_token")
        val FCM_TOKEN_REGISTERED = booleanPreferencesKey("fcm_token_registered")
    }

    suspend fun getUserEmail(): String? =
        store.data.map { it[Keys.USER_EMAIL] }.first()

    suspend fun setUserEmail(email: String) {
        store.edit { it[Keys.USER_EMAIL] = email }
    }

    suspend fun getUserName(): String? =
        store.data.map { it[Keys.USER_NAME] }.first()

    suspend fun setUserName(name: String) {
        store.edit { it[Keys.USER_NAME] = name }
    }

    suspend fun isOnboardingComplete(): Boolean =
        store.data.map { it[Keys.IS_ONBOARDING_COMPLETE] ?: false }.first()

    suspend fun setOnboardingComplete(complete: Boolean) {
        store.edit { it[Keys.IS_ONBOARDING_COMPLETE] = complete }
    }

    // Navigation state — mirrors iOS @AppStorage keys
    suspend fun hasCompletedLanguageSelection(): Boolean =
        store.data.map { it[Keys.HAS_COMPLETED_LANGUAGE_SELECTION] ?: false }.first()

    suspend fun setLanguageSelectionComplete(complete: Boolean) {
        store.edit { it[Keys.HAS_COMPLETED_LANGUAGE_SELECTION] = complete }
    }

    suspend fun hasSeenOnboarding(): Boolean =
        store.data.map { it[Keys.HAS_SEEN_ONBOARDING] ?: false }.first()

    suspend fun setSeenOnboarding(seen: Boolean) {
        store.edit { it[Keys.HAS_SEEN_ONBOARDING] = seen }
    }

    suspend fun isAuthenticated(): Boolean =
        store.data.map { it[Keys.IS_AUTHENTICATED] ?: false }.first()

    suspend fun setAuthenticated(authenticated: Boolean) {
        store.edit { it[Keys.IS_AUTHENTICATED] = authenticated }
    }

    suspend fun isGuestUser(): Boolean =
        store.data.map { it[Keys.IS_GUEST] ?: false }.first()

    suspend fun setGuestUser(isGuest: Boolean) {
        store.edit { it[Keys.IS_GUEST] = isGuest }
    }

    suspend fun hasBirthData(): Boolean =
        store.data.map { it[Keys.HAS_BIRTH_DATA] ?: false }.first()

    suspend fun setHasBirthData(has: Boolean) {
        store.edit { it[Keys.HAS_BIRTH_DATA] = has }
    }

    suspend fun getLastAccessState(): String =
        store.data.map { it[Keys.LAST_ACCESS_STATE] ?: "unknown" }.first()

    suspend fun setLastAccessState(state: String) {
        store.edit { it[Keys.LAST_ACCESS_STATE] = state }
    }

    suspend fun getSelectedLanguage(): String =
        store.data.map { it[Keys.SELECTED_LANGUAGE] ?: "en" }.first()

    suspend fun setSelectedLanguage(lang: String) {
        store.edit { it[Keys.SELECTED_LANGUAGE] = lang }
    }

    suspend fun getChartStyle(): String =
        store.data.map { it[Keys.CHART_STYLE] ?: "north_indian" }.first()

    suspend fun setChartStyle(style: String) {
        store.edit { it[Keys.CHART_STYLE] = style }
    }

    suspend fun getResponseStyle(): String =
        store.data.map { it[Keys.RESPONSE_STYLE] ?: "balanced" }.first()

    suspend fun setResponseStyle(style: String) {
        store.edit { it[Keys.RESPONSE_STYLE] = style }
    }

    suspend fun getBirthProfile(): BirthProfileDto? {
        val prefs = store.data.first()
        val dob = prefs[Keys.BIRTH_DOB] ?: return null
        val time = prefs[Keys.BIRTH_TIME] ?: return null
        val city = prefs[Keys.BIRTH_CITY] ?: return null
        val lat = prefs[Keys.BIRTH_LATITUDE] ?: return null
        val lon = prefs[Keys.BIRTH_LONGITUDE] ?: return null
        val gender = prefs[Keys.BIRTH_GENDER]
        val timeUnknown = prefs[Keys.BIRTH_TIME_UNKNOWN] ?: false
        return BirthProfileDto(dob, time, city, lat, lon, gender, timeUnknown)
    }

    suspend fun setBirthProfile(profile: BirthProfileDto) {
        store.edit {
            it[Keys.BIRTH_DOB] = profile.dateOfBirth
            it[Keys.BIRTH_TIME] = profile.timeOfBirth
            it[Keys.BIRTH_CITY] = profile.cityOfBirth
            it[Keys.BIRTH_LATITUDE] = profile.latitude
            it[Keys.BIRTH_LONGITUDE] = profile.longitude
            if (profile.gender != null) it[Keys.BIRTH_GENDER] = profile.gender
            it[Keys.BIRTH_TIME_UNKNOWN] = profile.birthTimeUnknown
        }
    }

    suspend fun getDailyQuota(): Int =
        store.data.map { it[Keys.DAILY_QUOTA] ?: 3 }.first()

    suspend fun getDailyUsed(): Int =
        store.data.map { it[Keys.DAILY_USED] ?: 0 }.first()

    suspend fun setQuota(quota: Int, used: Int) {
        store.edit {
            it[Keys.DAILY_QUOTA] = quota
            it[Keys.DAILY_USED] = used
        }
    }

    suspend fun isPremium(): Boolean =
        store.data.map { it[Keys.IS_PREMIUM] ?: false }.first()

    suspend fun setSubscription(isPremium: Boolean, planId: String) {
        store.edit {
            it[Keys.IS_PREMIUM] = isPremium
            it[Keys.PLAN_ID] = planId
        }
    }

    suspend fun getAccessState(): String =
        store.data.map { it[Keys.ACCESS_STATE] ?: "granted" }.first()

    suspend fun setAccessState(state: String) {
        store.edit { it[Keys.ACCESS_STATE] = state }
    }

    suspend fun getFcmToken(): String? =
        store.data.map { it[Keys.FCM_TOKEN] }.first()

    suspend fun setFcmToken(token: String) {
        store.edit { it[Keys.FCM_TOKEN] = token }
    }

    suspend fun isFcmTokenRegistered(): Boolean =
        store.data.map { it[Keys.FCM_TOKEN_REGISTERED] ?: false }.first()

    suspend fun setFcmTokenRegistered(registered: Boolean) {
        store.edit { it[Keys.FCM_TOKEN_REGISTERED] = registered }
    }

    suspend fun getNotifDailyInsight(): Boolean =
        store.data.map { it[Keys.NOTIF_DAILY_INSIGHT] ?: true }.first()

    suspend fun getNotifTransits(): Boolean =
        store.data.map { it[Keys.NOTIF_TRANSITS] ?: true }.first()

    suspend fun getNotifCompatibility(): Boolean =
        store.data.map { it[Keys.NOTIF_COMPATIBILITY] ?: true }.first()

    suspend fun setNotifPrefs(dailyInsight: Boolean, transits: Boolean, compatibility: Boolean) {
        store.edit {
            it[Keys.NOTIF_DAILY_INSIGHT] = dailyInsight
            it[Keys.NOTIF_TRANSITS] = transits
            it[Keys.NOTIF_COMPATIBILITY] = compatibility
        }
    }

    suspend fun clearAll() {
        store.edit { it.clear() }
    }
}
