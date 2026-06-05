package com.destinyai.astrology.data.local.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.destinyai.astrology.data.remote.BirthProfileDto
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "destiny_prefs")

/** R2-S13f: custom notification alert item. */
data class AlertItem(
    val id: String,
    val text: String,
    val frequency: String, // "Daily" | "Weekly" | "Monthly"
    // iOS parity (NotificationPreferencesSheet.swift:493/651/669): day index for
    // weekly (1-7 Sun..Sat) / monthly (1-31) alerts. null when unset or for Daily.
    val frequencyDay: Int? = null,
)

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
        // Mirrors iOS active profile id (UUID for partners, email for self). Storing a UUID in
        // ACTIVE_PROFILE_EMAIL would corrupt downstream lookups, so a separate key is used.
        val ACTIVE_PROFILE_ID = stringPreferencesKey("active_profile_id")
        val BIRTH_DOB = stringPreferencesKey("birth_dob")
        val BIRTH_TIME = stringPreferencesKey("birth_time")
        val BIRTH_CITY = stringPreferencesKey("birth_city")
        val BIRTH_LATITUDE = doublePreferencesKey("birth_latitude")
        val BIRTH_LONGITUDE = doublePreferencesKey("birth_longitude")
        val BIRTH_GENDER = stringPreferencesKey("birth_gender")
        val BIRTH_TIME_UNKNOWN = booleanPreferencesKey("birth_time_unknown")
        // iOS parity (BirthDataViewModel.swift:17): persist Google place_id alongside
        // city/lat/lng so we can re-resolve cities deterministically.
        val BIRTH_PLACE_ID = stringPreferencesKey("birth_place_id")
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
        val IS_HISTORY_ENABLED = booleanPreferencesKey("isHistoryEnabled")
        val SOUND_ENABLED = booleanPreferencesKey("sound_enabled")
        val AYANAMSA = stringPreferencesKey("ayanamsa")
        val HOUSE_SYSTEM = stringPreferencesKey("house_system")
        val BACKEND_DATA_REFRESHED = booleanPreferencesKey("backend_data_refreshed")
        val ANALYTICS_CONSENT = booleanPreferencesKey("analytics_consent")
        val NOTIF_PUSH_ENABLED = booleanPreferencesKey("notif_push_enabled")
        val NOTIF_EMAIL_ENABLED = booleanPreferencesKey("notif_email_enabled")
        val NOTIF_IN_APP_ENABLED = booleanPreferencesKey("notif_in_app_enabled")
        val NOTIF_ALERT_ITEMS_JSON = stringPreferencesKey("notif_alert_items_json")
        // iOS parity (BirthDataView.swift:610-616): Apple/Google IDs persisted on
        // sign-in and sent on profile save so backend can match users whose email
        // is a placeholder (Apple Hide-My-Email or "lookup-by-id@placeholder.local").
        val APPLE_USER_ID = stringPreferencesKey("apple_user_id")
        val GOOGLE_USER_ID = stringPreferencesKey("google_user_id")
        // iOS parity (StorageKeys.swift:9 + BirthDataView/Profile flows): standalone
        // "userGender" key — separate from BIRTH_GENDER (which is part of the
        // BirthProfile DTO) because iOS reads/writes it directly on the global
        // UserDefaults key for UI use.
        val USER_GENDER = stringPreferencesKey("user_gender")
        // iOS parity (HomeViewModel.swift:141 / ProfileService.swift:400): "quotaUsed"
        // mirrors backend questions_asked count for cold-start UI display before
        // the first /subscription/status sync lands.
        val QUOTA_USED = intPreferencesKey("quota_used")
        // iOS parity (SubscriptionManager.swift:403-407, QuotaManager.swift:316-319):
        // server-mirrored subscription metadata used by SubscriptionView/HomeViewModel
        // to render plan/status badges before the first network sync completes.
        val SUBSCRIPTION_STATUS = stringPreferencesKey("subscription_status")
        val SUBSCRIPTION_EXPIRES_AT = stringPreferencesKey("subscription_expires_at")
        val AUTO_RENEW_STATUS = booleanPreferencesKey("auto_renew_status")
        val CURRENT_PLAN_DISPLAY_NAME = stringPreferencesKey("current_plan_display_name")
        // iOS parity (HomeViewModel.swift:159-177): records the appLanguageCode that
        // was active during the last successful Home data fetch. When the user
        // changes language in Settings, the next loadHomeData() detects the
        // mismatch via getSelectedLanguage() != getLastLoadedLanguage() and
        // bypasses the cache so the home payload re-fetches with the new locale.
        val LAST_LOADED_LANGUAGE = stringPreferencesKey("lastLoadedLanguage")
        // iOS parity (QuotaManager.swift:335-378): server-returned plan list +
        // entitlement features cached as JSON so the Subscription screen can
        // render plan cards and feature lists instantly on cold start before
        // /subscription/status returns. Cleared on logout.
        val CACHED_AVAILABLE_PLANS_JSON = stringPreferencesKey("cachedAvailablePlans")
        val CACHED_AVAILABLE_FEATURES_JSON = stringPreferencesKey("cachedAvailableFeatures")
    }

    /**
     * iOS parity (StorageKeys.swift:20-23): generate a user-scoped key like
     * "userBirthData_user@example.com" so per-user data does not bleed across
     * accounts on the same device. Falls back to "guest" when no email known.
     */
    private fun scopedKey(base: String, email: String?): String =
        "${base}_${email ?: "guest"}"

    private fun scopedStringKey(base: String, email: String?): Preferences.Key<String> =
        stringPreferencesKey(scopedKey(base, email))

    private fun scopedBooleanKey(base: String, email: String?): Preferences.Key<Boolean> =
        booleanPreferencesKey(scopedKey(base, email))

    private fun scopedIntKey(base: String, email: String?): Preferences.Key<Int> =
        intPreferencesKey(scopedKey(base, email))

    /**
     * iOS parity (HomeViewModel.swift:22-31): per-user "hasSeenFirstPrediction" flag used
     * to (a) bypass the prediction cache so backend delivers the fixed onboarding question
     * exactly once, and (b) avoid resending is_first_login=true on subsequent loads. Key
     * is namespaced by email so a user re-installing or switching accounts gets their own
     * onboarding question.
     */
    private fun hasSeenFirstPredictionKey(email: String?): Preferences.Key<Boolean> =
        booleanPreferencesKey("has_seen_first_prediction_${email ?: "guest"}")

    // ── Per-user scoped values (iOS StorageKeys.userKey parity) ───────────────
    // Mirrors iOS keys: userBirthData_<email>, userGender_<email>,
    // hasBirthData_<email>, quotaUsed_<email>, birthTimeUnknown_<email>.
    // All accessors auto-resolve email from USER_EMAIL when not supplied,
    // matching StorageKeys.userKey(for:email:).

    private suspend fun resolveScopeEmail(explicit: String? = null): String? =
        explicit ?: getUserEmail()

    /**
     * Per-user JSON-encoded BirthData blob (iOS UserDefaults "userBirthData_<email>").
     * Matches iOS AuthViewModel.swift:711-718 dual-store: the JSON blob is the
     * source of truth that downstream features (chart restore, gender lookup)
     * consume.
     */
    suspend fun getUserBirthDataJson(email: String? = null): String? {
        val scope = resolveScopeEmail(email)
        return store.data.map { it[scopedStringKey("userBirthData", scope)] }.first()
    }

    suspend fun setUserBirthDataJson(json: String, email: String? = null) {
        val scope = resolveScopeEmail(email)
        store.edit { it[scopedStringKey("userBirthData", scope)] = json }
    }

    suspend fun clearUserBirthDataJson(email: String? = null) {
        val scope = resolveScopeEmail(email)
        store.edit { it.remove(scopedStringKey("userBirthData", scope)) }
    }

    suspend fun getUserGender(email: String? = null): String? {
        val scope = resolveScopeEmail(email)
        return store.data.map { it[scopedStringKey("userGender", scope)] }.first()
    }

    suspend fun setUserGender(gender: String, email: String? = null) {
        val scope = resolveScopeEmail(email)
        store.edit { it[scopedStringKey("userGender", scope)] = gender }
    }

    suspend fun hasBirthDataScoped(email: String? = null): Boolean {
        val scope = resolveScopeEmail(email)
        return store.data.map { it[scopedBooleanKey("hasBirthData", scope)] ?: false }.first()
    }

    suspend fun setHasBirthDataScoped(has: Boolean, email: String? = null) {
        val scope = resolveScopeEmail(email)
        store.edit { it[scopedBooleanKey("hasBirthData", scope)] = has }
    }

    suspend fun getBirthTimeUnknownScoped(email: String? = null): Boolean {
        val scope = resolveScopeEmail(email)
        return store.data.map { it[scopedBooleanKey("birthTimeUnknown", scope)] ?: false }.first()
    }

    suspend fun setBirthTimeUnknownScoped(unknown: Boolean, email: String? = null) {
        val scope = resolveScopeEmail(email)
        store.edit { it[scopedBooleanKey("birthTimeUnknown", scope)] = unknown }
    }

    suspend fun getQuotaUsedScoped(email: String? = null): Int {
        val scope = resolveScopeEmail(email)
        return store.data.map { it[scopedIntKey("quotaUsed", scope)] ?: 0 }.first()
    }

    suspend fun setQuotaUsedScoped(used: Int, email: String? = null) {
        val scope = resolveScopeEmail(email)
        store.edit { it[scopedIntKey("quotaUsed", scope)] = used }
    }

    /**
     * Clear all per-user scoped values for [email] (iOS StorageKeys.allKeys parity).
     * Used on full reset/account-switch so previous user's chart, gender, and
     * quota counters do not leak into the next sign-in on the same device.
     */
    suspend fun clearScopedKeysFor(email: String) {
        store.edit {
            it.remove(scopedStringKey("userBirthData", email))
            it.remove(scopedStringKey("userGender", email))
            it.remove(scopedBooleanKey("birthTimeUnknown", email))
            it.remove(scopedBooleanKey("hasBirthData", email))
            it.remove(scopedIntKey("quotaUsed", email))
            it.remove(lastFullLoadDateKey(email))
        }
    }

    // ── Apple email/name dual-store fallback (iOS AppleAuthService.swift:108-127) ──
    // Mirrors the iOS UserDefaults fallback layer that sits underneath Keychain.
    // SecureStorage is the primary (encrypted, persists across reinstall on iOS);
    // these DataStore keys are the secondary fallback when SecureStorage is wiped
    // by clearAll() (e.g. sign-out) but the OAuth provider re-issues the same
    // userId without email/name.

    private fun appleUserEmailKey(userId: String): Preferences.Key<String> =
        stringPreferencesKey("appleUserEmail_$userId")

    private fun appleUserNameKey(userId: String): Preferences.Key<String> =
        stringPreferencesKey("appleUserName_$userId")

    suspend fun setAppleEmailFallback(userId: String, email: String) {
        store.edit { it[appleUserEmailKey(userId)] = email }
    }

    suspend fun getAppleEmailFallback(userId: String): String? =
        store.data.map { it[appleUserEmailKey(userId)] }.first()

    suspend fun setAppleNameFallback(userId: String, name: String) {
        store.edit { it[appleUserNameKey(userId)] = name }
    }

    suspend fun getAppleNameFallback(userId: String): String? =
        store.data.map { it[appleUserNameKey(userId)] }.first()

    suspend fun hasSeenFirstPrediction(): Boolean {
        val email = getUserEmail()
        return store.data.map { it[hasSeenFirstPredictionKey(email)] ?: false }.first()
    }

    suspend fun setHasSeenFirstPrediction(seen: Boolean) {
        val email = getUserEmail()
        store.edit { it[hasSeenFirstPredictionKey(email)] = seen }
    }

    suspend fun getUserEmail(): String? =
        store.data.map { it[Keys.USER_EMAIL] }.first()

    suspend fun setUserEmail(email: String) {
        store.edit { it[Keys.USER_EMAIL] = email }
    }

    suspend fun getAppleUserId(): String? =
        store.data.map { it[Keys.APPLE_USER_ID] }.first()

    suspend fun setAppleUserId(id: String) {
        // iOS parity (AuthViewModel.swift:592-603): Apple/Google IDs are mutually
        // exclusive — when an Apple sign-in lands we clear any previously-stored
        // googleUserID so downstream profile-sync flows cannot send both.
        store.edit {
            it[Keys.APPLE_USER_ID] = id
            it.remove(Keys.GOOGLE_USER_ID)
        }
    }

    suspend fun getGoogleUserId(): String? =
        store.data.map { it[Keys.GOOGLE_USER_ID] }.first()

    suspend fun setGoogleUserId(id: String) {
        // iOS parity (AuthViewModel.swift:592-603): mutual exclusion with Apple ID
        // — clear appleUserID when persisting a googleUserID.
        store.edit {
            it[Keys.GOOGLE_USER_ID] = id
            it.remove(Keys.APPLE_USER_ID)
        }
    }

    /**
     * iOS parity (AuthViewModel.swift:600-603): guest sessions clear both
     * provider IDs so a stale appleUserID/googleUserID from a previous account
     * cannot leak into the guest's profile-sync calls.
     */
    suspend fun clearProviderIds() {
        store.edit {
            it.remove(Keys.APPLE_USER_ID)
            it.remove(Keys.GOOGLE_USER_ID)
        }
    }

    // ── userGender — iOS parity (StorageKeys.userGender) ──────────────────────
    suspend fun getUserGender(): String? =
        store.data.map { it[Keys.USER_GENDER] }.first()

    suspend fun setUserGender(gender: String?) {
        store.edit {
            if (gender.isNullOrBlank()) it.remove(Keys.USER_GENDER)
            else it[Keys.USER_GENDER] = gender
        }
    }

    // ── quotaUsed — iOS parity (HomeViewModel.swift:141, ProfileService.swift:400) ─
    suspend fun getQuotaUsed(): Int =
        store.data.map { it[Keys.QUOTA_USED] ?: 0 }.first()

    suspend fun setQuotaUsed(used: Int) {
        store.edit { it[Keys.QUOTA_USED] = used }
    }

    // ── Server-mirrored subscription metadata (iOS parity:
    //     SubscriptionManager.swift:403-407, QuotaManager.swift:316-319) ───────

    suspend fun getSubscriptionStatus(): String? =
        store.data.map { it[Keys.SUBSCRIPTION_STATUS] }.first()

    suspend fun getSubscriptionExpiresAt(): String? =
        store.data.map { it[Keys.SUBSCRIPTION_EXPIRES_AT] }.first()

    suspend fun getAutoRenewStatus(): Boolean? =
        store.data.map { it[Keys.AUTO_RENEW_STATUS] }.first()

    suspend fun getCurrentPlanDisplayName(): String? =
        store.data.map { it[Keys.CURRENT_PLAN_DISPLAY_NAME] }.first()

    /**
     * Persist the full server-mirrored subscription state in one edit so cold-start
     * UI sees a consistent snapshot. Pass null for any field to clear it; matches
     * iOS SubscriptionManager.resetForAccountSwitch behavior.
     */
    suspend fun setSubscriptionMeta(
        subscriptionStatus: String?,
        subscriptionExpiresAt: String?,
        autoRenewStatus: Boolean?,
        currentPlanDisplayName: String?,
    ) {
        store.edit {
            if (subscriptionStatus == null) it.remove(Keys.SUBSCRIPTION_STATUS)
            else it[Keys.SUBSCRIPTION_STATUS] = subscriptionStatus
            if (subscriptionExpiresAt == null) it.remove(Keys.SUBSCRIPTION_EXPIRES_AT)
            else it[Keys.SUBSCRIPTION_EXPIRES_AT] = subscriptionExpiresAt
            if (autoRenewStatus == null) it.remove(Keys.AUTO_RENEW_STATUS)
            else it[Keys.AUTO_RENEW_STATUS] = autoRenewStatus
            if (currentPlanDisplayName == null) it.remove(Keys.CURRENT_PLAN_DISPLAY_NAME)
            else it[Keys.CURRENT_PLAN_DISPLAY_NAME] = currentPlanDisplayName
        }
    }

    /** Clear ALL subscription metadata (iOS resetForAccountSwitch parity). */
    suspend fun clearSubscriptionMeta() {
        store.edit {
            it.remove(Keys.SUBSCRIPTION_STATUS)
            it.remove(Keys.SUBSCRIPTION_EXPIRES_AT)
            it.remove(Keys.AUTO_RENEW_STATUS)
            it.remove(Keys.CURRENT_PLAN_DISPLAY_NAME)
        }
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

    /** Reactive flow mirror of isGuestUser() — for Compose collectAsState. */
    val isGuestUserFlow: kotlinx.coroutines.flow.Flow<Boolean>
        get() = store.data.map { it[Keys.IS_GUEST] ?: false }

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
        store.data.map { it[Keys.CHART_STYLE] ?: "north" }.first()

    suspend fun setChartStyle(style: String) {
        store.edit { it[Keys.CHART_STYLE] = style }
    }

    suspend fun getResponseStyle(): String =
        store.data.map { it[Keys.RESPONSE_STYLE] ?: "guidance" }.first()

    suspend fun setResponseStyle(style: String) {
        store.edit { it[Keys.RESPONSE_STYLE] = style }
    }

    // Mirrors iOS UserDefaults "userResponseLength" — short / standard / detailed.
    suspend fun getResponseLength(): String =
        store.data.map { it[Keys.RESPONSE_LENGTH] ?: "standard" }.first()

    suspend fun setResponseLength(length: String) {
        store.edit { it[Keys.RESPONSE_LENGTH] = length }
    }

    val responseLengthFlow get() = store.data.map { it[Keys.RESPONSE_LENGTH] ?: "standard" }

    suspend fun getBirthProfile(): BirthProfileDto? {
        val prefs = store.data.first()
        val dob = prefs[Keys.BIRTH_DOB] ?: return null
        val time = prefs[Keys.BIRTH_TIME] ?: return null
        val city = prefs[Keys.BIRTH_CITY] ?: return null
        val lat = prefs[Keys.BIRTH_LATITUDE] ?: return null
        val lon = prefs[Keys.BIRTH_LONGITUDE] ?: return null
        val gender = prefs[Keys.BIRTH_GENDER]
        val timeUnknown = prefs[Keys.BIRTH_TIME_UNKNOWN] ?: false
        val placeId = prefs[Keys.BIRTH_PLACE_ID]
        return BirthProfileDto(dob, time, city, lat, lon, gender, timeUnknown, placeId)
    }

    /**
     * iOS parity (AuthViewModel.swift hasBirthData check): true iff dob, time,
     * city, lat AND lon are all present and non-blank. Unlike [getBirthProfile]
     * this also rejects whitespace-only string fields, matching the iOS rule
     * used to decide whether to route the post-auth flow to BirthDataScreen.
     */
    suspend fun hasCompleteBirthProfile(): Boolean {
        val prefs = store.data.first()
        val dob = prefs[Keys.BIRTH_DOB]
        val time = prefs[Keys.BIRTH_TIME]
        val city = prefs[Keys.BIRTH_CITY]
        val lat = prefs[Keys.BIRTH_LATITUDE]
        val lon = prefs[Keys.BIRTH_LONGITUDE]
        return !dob.isNullOrBlank() &&
            !time.isNullOrBlank() &&
            !city.isNullOrBlank() &&
            lat != null &&
            lon != null
    }

    /**
     * Clear the locally-cached birth profile fields. Used on sign-out so the
     * next guest session starts without inheriting the previous user's chart
     * (iOS parity: guestNeedsBirthData rule — guests always re-enter on a
     * fresh session).
     */
    suspend fun clearBirthProfile() {
        store.edit {
            it.remove(Keys.BIRTH_DOB)
            it.remove(Keys.BIRTH_TIME)
            it.remove(Keys.BIRTH_CITY)
            it.remove(Keys.BIRTH_LATITUDE)
            it.remove(Keys.BIRTH_LONGITUDE)
            it.remove(Keys.BIRTH_GENDER)
            it.remove(Keys.BIRTH_TIME_UNKNOWN)
            it.remove(Keys.BIRTH_PLACE_ID)
        }
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
            if (profile.placeId != null) it[Keys.BIRTH_PLACE_ID] = profile.placeId
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

    // History opt-in — mirrors iOS HistorySettingsManager.isHistoryEnabled
    val isHistoryEnabledFlow get() = store.data.map { it[Keys.IS_HISTORY_ENABLED] ?: true }

    suspend fun isHistoryEnabled(): Boolean =
        store.data.map { it[Keys.IS_HISTORY_ENABLED] ?: true }.first()

    suspend fun setHistoryEnabled(enabled: Boolean) {
        store.edit { it[Keys.IS_HISTORY_ENABLED] = enabled }
    }

    suspend fun getActiveProfileEmail(): String? =
        store.data.map { it[Keys.ACTIVE_PROFILE_EMAIL] }.first()

    suspend fun setActiveProfileEmail(email: String) {
        store.edit { it[Keys.ACTIVE_PROFILE_EMAIL] = email }
    }

    /**
     * Active profile id — UUID for partner profiles, email for self.
     * Mirrors iOS ProfileContext.activeProfileId. Never overload the email field with a UUID.
     */
    suspend fun getActiveProfileId(): String? =
        store.data.map { it[Keys.ACTIVE_PROFILE_ID] }.first()

    /**
     * Reactive active-profile-id stream so screens can re-key their data when
     * the user switches profiles (mirrors iOS ProfileContextManager.shared.activeProfileId
     * publisher consumed by HistoryView.swift:62-66).
     */
    val activeProfileIdFlow: kotlinx.coroutines.flow.Flow<String?> =
        store.data.map { it[Keys.ACTIVE_PROFILE_ID] }

    suspend fun setActiveProfileId(profileId: String) {
        store.edit { it[Keys.ACTIVE_PROFILE_ID] = profileId }
    }

    suspend fun getAyanamsa(): String =
        store.data.map { it[Keys.AYANAMSA] ?: "lahiri" }.first()

    suspend fun saveAyanamsa(ayanamsa: String) {
        store.edit { it[Keys.AYANAMSA] = ayanamsa }
    }

    suspend fun getHouseSystem(): String =
        store.data.map { it[Keys.HOUSE_SYSTEM] ?: "whole_sign" }.first()

    suspend fun saveHouseSystem(system: String) {
        store.edit { it[Keys.HOUSE_SYSTEM] = system }
    }

    // Sound feedback — R2-Z2
    suspend fun isSoundEnabled(): Boolean =
        store.data.map { it[Keys.SOUND_ENABLED] ?: false }.first()

    /**
     * iOS parity (SoundManager.swift:19-29): observable sound-enabled flag.
     * SoundManager collects this flow to keep an in-memory cache so play()
     * never blocks the UI thread, and to start/stop the audio engine in
     * response to toggles from any UI surface.
     */
    fun isSoundEnabledFlow(): kotlinx.coroutines.flow.Flow<Boolean> =
        store.data.map { it[Keys.SOUND_ENABLED] ?: false }

    suspend fun setSoundEnabled(enabled: Boolean) {
        store.edit { it[Keys.SOUND_ENABLED] = enabled }
    }

    // Backend data refreshed banner — R2-A6
    suspend fun getBackendDataRefreshed(): Boolean =
        store.data.map { it[Keys.BACKEND_DATA_REFRESHED] ?: false }.first()

    suspend fun setBackendDataRefreshed(refreshed: Boolean) {
        store.edit { it[Keys.BACKEND_DATA_REFRESHED] = refreshed }
    }

    // Analytics consent — R2-A5
    suspend fun getAnalyticsConsent(): Boolean =
        store.data.map { it[Keys.ANALYTICS_CONSENT] ?: false }.first()

    suspend fun setAnalyticsConsent(consent: Boolean) {
        store.edit { it[Keys.ANALYTICS_CONSENT] = consent }
    }

    // ── Notification channel toggles — R2-S7 ─────────────────────────────────

    suspend fun getNotifPushEnabled(): Boolean =
        store.data.map { it[Keys.NOTIF_PUSH_ENABLED] ?: true }.first()

    suspend fun setNotifPushEnabled(enabled: Boolean) {
        store.edit { it[Keys.NOTIF_PUSH_ENABLED] = enabled }
    }

    suspend fun getNotifEmailEnabled(): Boolean =
        store.data.map { it[Keys.NOTIF_EMAIL_ENABLED] ?: true }.first()

    suspend fun setNotifEmailEnabled(enabled: Boolean) {
        store.edit { it[Keys.NOTIF_EMAIL_ENABLED] = enabled }
    }

    suspend fun getNotifInAppEnabled(): Boolean =
        store.data.map { it[Keys.NOTIF_IN_APP_ENABLED] ?: true }.first()

    suspend fun setNotifInAppEnabled(enabled: Boolean) {
        store.edit { it[Keys.NOTIF_IN_APP_ENABLED] = enabled }
    }

    // ── Custom alert items — R2-S13f ──────────────────────────────────────────

    suspend fun getAlertItems(): List<AlertItem> {
        val json = store.data.map { it[Keys.NOTIF_ALERT_ITEMS_JSON] }.first() ?: return emptyList()
        return try {
            val type = object : TypeToken<List<AlertItem>>() {}.type
            Gson().fromJson(json, type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun saveAlertItems(items: List<AlertItem>) {
        val json = Gson().toJson(items)
        store.edit { it[Keys.NOTIF_ALERT_ITEMS_JSON] = json }
    }

    // ── Last loaded language — iOS parity (HomeViewModel.swift:159-177) ───────
    // Tracks the appLanguageCode that produced the last home payload. When the
    // user changes language in Settings, HomeViewModel.loadHomeData() compares
    // getSelectedLanguage() to getLastLoadedLanguage(); a mismatch forces the
    // cache to be bypassed so localized strings refresh without reinstall.

    suspend fun getLastLoadedLanguage(): String? =
        store.data.map { it[Keys.LAST_LOADED_LANGUAGE] }.first()

    suspend fun setLastLoadedLanguage(lang: String) {
        store.edit { it[Keys.LAST_LOADED_LANGUAGE] = lang }
    }

    // ── lastFullLoadDate_<email> — iOS parity (HomeViewModel.swift:52-59) ─────
    // Per-user epoch-millis timestamp of the last successful full home reload
    // (chart + dasha + transits). HomeViewModel uses this to date-gate cold-start
    // network calls: skip the full reload when Calendar startOfDay(now) ==
    // startOfDay(stored). Scoped by email so multi-account devices do not
    // share gating state.

    private fun lastFullLoadDateKey(email: String?): Preferences.Key<Long> =
        longPreferencesKey("lastFullLoadDate_${email ?: "guest"}")

    suspend fun getLastFullLoadDate(email: String? = null): Long? {
        val scope = resolveScopeEmail(email)
        return store.data.map { it[lastFullLoadDateKey(scope)] }.first()
    }

    suspend fun setLastFullLoadDate(epochMillis: Long, email: String? = null) {
        val scope = resolveScopeEmail(email)
        store.edit { it[lastFullLoadDateKey(scope)] = epochMillis }
    }

    suspend fun clearLastFullLoadDate(email: String? = null) {
        val scope = resolveScopeEmail(email)
        store.edit { it.remove(lastFullLoadDateKey(scope)) }
    }

    // ── Cached subscription plans/features JSON — iOS parity
    //     (QuotaManager.swift:335-378, 652-662) ──────────────────────────────
    // SubscriptionViewModel reads these synchronously on init so the plan cards
    // and feature checklist render instantly on cold start, before
    // /subscription/status / /subscription/plans complete (or forever if offline).
    // Persisted as raw JSON; callers serialize with Gson.

    suspend fun getCachedAvailablePlansJson(): String? =
        store.data.map { it[Keys.CACHED_AVAILABLE_PLANS_JSON] }.first()

    suspend fun setCachedAvailablePlansJson(json: String?) {
        store.edit {
            if (json.isNullOrBlank()) it.remove(Keys.CACHED_AVAILABLE_PLANS_JSON)
            else it[Keys.CACHED_AVAILABLE_PLANS_JSON] = json
        }
    }

    suspend fun getCachedAvailableFeaturesJson(): String? =
        store.data.map { it[Keys.CACHED_AVAILABLE_FEATURES_JSON] }.first()

    suspend fun setCachedAvailableFeaturesJson(json: String?) {
        store.edit {
            if (json.isNullOrBlank()) it.remove(Keys.CACHED_AVAILABLE_FEATURES_JSON)
            else it[Keys.CACHED_AVAILABLE_FEATURES_JSON] = json
        }
    }
}
