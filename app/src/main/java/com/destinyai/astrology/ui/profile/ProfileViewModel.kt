package com.destinyai.astrology.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.destinyai.astrology.data.billing.BillingManager
import com.destinyai.astrology.data.local.prefs.UserPreferences
import com.destinyai.astrology.data.remote.AnalyticsConsentRequest
import com.destinyai.astrology.data.remote.AstroApiService
import com.destinyai.astrology.data.remote.DeleteAccountRequest
import com.destinyai.astrology.data.repository.AuthRepository
import com.destinyai.astrology.services.ProfileChangeBus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

data class ProfileUiState(
    val userName: String = "",
    val email: String = "",
    val isPremium: Boolean = false,
    val planId: String = "",
    val dailyQuota: Int = 3,
    val dailyUsed: Int = 0,
    val isLoading: Boolean = false,
    val isDeleted: Boolean = false,
    val isSignedOut: Boolean = false,
    val showDeleteConfirmation: Boolean = false,
    val showDeleteSheet: Boolean = false,
    val error: String? = null,
    val snackbarMessage: String? = null,
    val historyEnabled: Boolean = true,
    val analyticsConsent: Boolean = defaultAnalyticsConsent(),
    val showProfileSwitcher: Boolean = false,
    val pendingUpgradePlanId: String? = null,
    val pendingUpgradeDate: String? = null,
    val hasActiveSubscription: Boolean = false,
    // Chart style ("north" / "south") — drives the Chart Style row subtitle and
    // ChartStylePickerSheet. Mirrors iOS ProfileView.swift @AppStorage("chartStyle").
    val chartStyle: String = "north",
    // Plan expiry display string (e.g. "Renews Mar 21, 2026"). Mirrors iOS
    // QuotaManager.subscriptionExpiryDisplayText. Empty when not yet loaded.
    val subscriptionExpiryDisplayText: String? = null,
    // History-cleared success alert: number of threads deleted. Null = alert hidden.
    // Mirrors iOS ProfileView.clearedThreadCount + showClearSuccessAlert (line 227-243).
    val clearedThreadCount: Int? = null,
    // Delete account in-flight + inline error. Mirrors iOS isDeletingAccount /
    // deleteErrorMessage at ProfileView.swift:844-872. Sheet stays open while
    // isDeleting=true; deleteErrorMessage renders inline above the confirm button.
    val isDeletingAccount: Boolean = false,
    val deleteErrorMessage: String? = null,
    // Selected language code (e.g. "en", "hi", "ta") and response style key.
    // Surfaced as live subtitles on Language / Response Style preference rows.
    // Mirrors iOS currentLanguageDisplay (ProfileView.swift:385) and
    // ContentStyleManager.shared.currentStyle.label (line 393).
    val languageCode: String = "en",
    val responseStyle: String = "guidance",
) {
    /**
     * Mirrors iOS ProfileView.isGuestUser (ProfileView.swift:56-59).
     * Guest emails use format: YYYYMMDD_HHMM_CityPrefix_LatInt_LngInt@daa.com
     * Legacy guest emails ended in @gen.com.
     */
    val isGuestUser: Boolean
        get() = email.isEmpty() ||
            email.contains("guest", ignoreCase = true) ||
            email.endsWith("@daa.com", ignoreCase = true) ||
            email.endsWith("@gen.com", ignoreCase = true)
}

/**
 * Mirrors iOS loadAnalyticsConsent (ProfileView.swift:936-948):
 * US users default to opt-OUT (false), non-US users default to opt-IN (true) for GDPR compliance.
 */
private fun defaultAnalyticsConsent(): Boolean {
    val region = Locale.getDefault().country.uppercase(Locale.ROOT)
    return region != "US"
}

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val api: AstroApiService,
    private val prefs: UserPreferences,
    private val authRepository: AuthRepository,
    private val billingManager: BillingManager,
    private val profileChangeBus: ProfileChangeBus,
    // iOS parity (HistorySettingsManager.clearAllHistory step 2-3 at
    // HistorySettingsManager.swift:118-122): after the server DELETE succeeds,
    // wipe local Room mirrors so Chat history sheet + Match list flush
    // immediately. The API call remains authoritative — these DAOs are only
    // used to clear the local cache.
    private val threadDao: com.destinyai.astrology.data.local.db.ChatThreadDao,
    private val messageDao: com.destinyai.astrology.data.local.db.ChatMessageDao,
    private val compatibilityHistoryDao: com.destinyai.astrology.data.local.db.CompatibilityHistoryDao,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState

    /**
     * One-shot event surfaced after a successful Switch Profile so ProfileScreen
     * can show a "Now viewing as <Name>" Snackbar. Carries just the display
     * name; the screen joins it with a localized template.
     */
    private val _profileSwitchedToName = MutableStateFlow<String?>(null)
    val profileSwitchedToName: StateFlow<String?> = _profileSwitchedToName

    fun consumeProfileSwitchedEvent() {
        _profileSwitchedToName.value = null
    }

    init {
        // iOS parity (SubscriptionManager.swift:501-563 +
        // ProfileView.swift:272-283): observe pending upgrade from
        // BillingManager and project into uiState so the "Upgrading on …"
        // notice in ProfileScreen renders.
        viewModelScope.launch {
            combine(
                billingManager.pendingUpgradePlanId,
                billingManager.pendingUpgradeEffectiveDate,
            ) { planId, effective -> planId to effective }
                .collect { (planId, effectiveMillis) ->
                    val formatted = effectiveMillis?.let {
                        DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(it))
                    }
                    _uiState.update {
                        it.copy(
                            pendingUpgradePlanId = planId,
                            pendingUpgradeDate = formatted,
                        )
                    }
                }
        }
        // Surface a "Now viewing as <Name>" snackbar after a successful Switch
        // Profile. The bus emits the new profile id; we resolve it to a display
        // name via the locally-cached partner list (Room) or, for self, the
        // current account name in prefs. Mirrors iOS NotificationCenter
        // .activeProfileChanged handler used to refresh dependent screens.
        viewModelScope.launch {
            profileChangeBus.events.collect { newProfileId ->
                val displayName = resolveProfileDisplayName(newProfileId)
                if (!displayName.isNullOrBlank()) {
                    _profileSwitchedToName.value = displayName
                }
            }
        }
    }

    private suspend fun resolveProfileDisplayName(profileId: String): String? {
        val email = prefs.getUserEmail()
        // Self profile keys on the account email.
        if (email != null && profileId == email) {
            return prefs.getUserName()?.takeIf { it.isNotBlank() } ?: email
        }
        // Otherwise look up the partner row in Room (best-effort; no API call here).
        return try {
            val partners = email?.let { api.listPartners(it) } ?: emptyList()
            partners.firstOrNull { it.id == profileId }?.name
        } catch (_: Exception) {
            null
        }
    }

    fun loadProfile() {
        viewModelScope.launch {
            val email = prefs.getUserEmail() ?: return@launch
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val status = api.getStatus(email)
                val name = prefs.getUserName() ?: status.name ?: ""
                val historyEnabled = prefs.isHistoryEnabled()
                val chartStyle = prefs.getChartStyle()
                val languageCode = prefs.getSelectedLanguage()
                val responseStyle = prefs.getResponseStyle()
                // Mirrors iOS loadAnalyticsConsent — server is source of truth, fall back to
                // Locale-based default (US opt-out, non-US opt-in for GDPR).
                val analyticsConsent = status.analyticsConsent ?: defaultAnalyticsConsent()
                // hasActiveSubscription = isPremium AND a Play Billing purchase is currently
                // active. Conflating premium plan flag with billing state lets users in the
                // grace period bypass the cancel-first guard on Delete Account
                // (DeleteAccountSheet.swift:15-17 iOS parity).
                val activePurchase = billingManager.purchasedProductIds.value.isNotEmpty()
                // Subscription expiry display string — mirrors iOS QuotaManager.subscriptionExpiryDisplayText.
                val expiryDisplay = status.subscriptionExpiresAt?.takeIf { it.isNotBlank() }?.let { iso ->
                    runCatching {
                        val instant = java.time.Instant.parse(iso)
                        val date = Date.from(instant)
                        DateFormat.getDateInstance(DateFormat.MEDIUM).format(date)
                    }.getOrNull()
                }
                _uiState.update {
                    it.copy(
                        userName = name,
                        email = status.userEmail,
                        isPremium = status.isPremium,
                        planId = status.planId ?: "",
                        dailyQuota = status.dailyQuota ?: 0,
                        dailyUsed = status.dailyUsed ?: 0,
                        isLoading = false,
                        historyEnabled = historyEnabled,
                        analyticsConsent = analyticsConsent,
                        hasActiveSubscription = status.isPremium && activePurchase,
                        chartStyle = chartStyle,
                        subscriptionExpiryDisplayText = expiryDisplay,
                        languageCode = languageCode,
                        responseStyle = responseStyle,
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "Failed to load profile") }
            }
        }
    }

    fun refreshAll() {
        // iOS parity (ProfileView.swift:115-126, INV-J4): pull-to-refresh
        // forces a full entitlement reconcile so the user can recover from
        // missed webhooks / offer-code mismatches without contacting support.
        // Order: query Play Billing → reconcile with backend → reload profile.
        viewModelScope.launch {
            runCatching { billingManager.reconcileEntitlements() }
            loadProfile()
        }
    }

    fun toggleHistory(enabled: Boolean) {
        viewModelScope.launch {
            prefs.setHistoryEnabled(enabled)
            _uiState.update { it.copy(historyEnabled = enabled) }
            // Mirrors iOS HistorySettingsManager.syncSettingToServer — persists the toggle to the
            // backend so the predict API also respects it across devices/reinstalls.
            val email = prefs.getUserEmail() ?: return@launch
            if (email.isBlank() || email.contains("guest", ignoreCase = true)) return@launch
            try {
                api.updateChatHistorySettings(
                    userId = email,
                    historyEnabled = enabled,
                    saveConversations = enabled,
                )
            } catch (_: Exception) {
                // Best-effort — local toggle already updated.
            }
        }
    }

    fun toggleAnalytics(enabled: Boolean) {
        viewModelScope.launch {
            val email = prefs.getUserEmail() ?: run {
                _uiState.update { it.copy(analyticsConsent = enabled) }
                return@launch
            }
            _uiState.update { it.copy(analyticsConsent = enabled) }
            try {
                api.updateAnalyticsConsent(AnalyticsConsentRequest(email = email, consent = enabled))
            } catch (_: Exception) {
                // best-effort; state already flipped locally
            }
        }
    }

    fun clearChatHistory() {
        viewModelScope.launch {
            val email = prefs.getUserEmail() ?: return@launch
            try {
                // iOS parity (HistorySettingsManager.clearAllHistory step 1):
                // server DELETE is the source of truth — local wipe only mirrors
                // a successful response. API call: DELETE /chat-history/all/<email>.
                val response = api.deleteAllChatHistory(email)
                // Mirrors iOS clearedThreadCount alert (ProfileView.swift:227-243):
                // backend returns {"deleted_count": N}; surface as a count-aware
                // success dialog (ProfileScreen renders a plurals-formatted alert).
                val count = response.deletedCount ?: 0
                // iOS parity (HistorySettingsManager.swift:118-122): after the
                // server delete succeeds, wipe local mirrors so the Chat history
                // sheet + Match list flush immediately. Best-effort — Room
                // failures must not roll back the user-visible success state.
                runCatching {
                    messageDao.deleteAllForUser(email)
                    threadDao.deleteAllForUser(email)
                    compatibilityHistoryDao.deleteAllForUser(email)
                }
                _uiState.update { it.copy(clearedThreadCount = count) }
            } catch (e: Exception) {
                _uiState.update { it.copy(snackbarMessage = "Failed to clear history") }
            }
        }
    }

    fun dismissClearedThreadAlert() = _uiState.update { it.copy(clearedThreadCount = null) }

    /** Mirrors iOS @AppStorage("chartStyle") writes via ChartStylePickerSheet. */
    fun setChartStyle(style: String) {
        viewModelScope.launch {
            prefs.setChartStyle(style)
            _uiState.update { it.copy(chartStyle = style) }
        }
    }

    fun clearSnackbar() = _uiState.update { it.copy(snackbarMessage = null) }

    fun showProfileSwitcher() = _uiState.update { it.copy(showProfileSwitcher = true) }

    fun dismissProfileSwitcher() = _uiState.update { it.copy(showProfileSwitcher = false) }

    fun showDeleteConfirmation() = _uiState.update { it.copy(showDeleteSheet = true, showDeleteConfirmation = true, deleteErrorMessage = null) }

    fun dismissDeleteConfirmation() = _uiState.update {
        // Block dismiss while a delete is mid-flight — mirrors iOS
        // .interactiveDismissDisabled(isDeleting) at DeleteAccountSheet.swift:164.
        if (it.isDeletingAccount) it else it.copy(showDeleteSheet = false, showDeleteConfirmation = false, deleteErrorMessage = null)
    }

    fun confirmDeleteAccount() {
        viewModelScope.launch {
            val email = prefs.getUserEmail() ?: return@launch
            // Keep sheet open with spinner; clear any stale error.
            _uiState.update { it.copy(isDeletingAccount = true, deleteErrorMessage = null) }
            try {
                api.deleteAccount(DeleteAccountRequest(userEmail = email))
                prefs.clearAll()
                _uiState.update {
                    it.copy(
                        isDeletingAccount = false,
                        showDeleteSheet = false,
                        showDeleteConfirmation = false,
                        isDeleted = true,
                    )
                }
            } catch (e: retrofit2.HttpException) {
                // Mirrors iOS ProfileService:537-544 — 403 = active subscription blocks
                // deletion. Parse the server detail message; fall back to a localized
                // string ("Please cancel your subscription before deleting your account.").
                if (e.code() == 403) {
                    val detail = runCatching {
                        val raw = e.response()?.errorBody()?.string().orEmpty()
                        com.google.gson.JsonParser.parseString(raw)
                            .asJsonObject.get("detail")?.asString
                    }.getOrNull()
                    _uiState.update {
                        it.copy(
                            isDeletingAccount = false,
                            deleteErrorMessage = detail
                                ?: "Please cancel your subscription before deleting your account.",
                        )
                    }
                } else {
                    _uiState.update { it.copy(isDeletingAccount = false, deleteErrorMessage = e.message ?: "Failed to delete account") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isDeletingAccount = false, deleteErrorMessage = e.message ?: "Failed to delete account") }
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                // iOS parity: clear all per-user subscription state BEFORE the
                // session is wiped so the next user signing in on the same
                // device cannot see the previous user's plan badge or
                // entitlements (SubscriptionManager.swift:393-408 +
                // QuotaManager.swift:563-573).
                billingManager.resetForSignOut()
                authRepository.signOut()
                _uiState.update { it.copy(isLoading = false, isSignedOut = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "Failed to sign out") }
            }
        }
    }
}
