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
import com.destinyai.astrology.ui.compatibility.firstNameFrom
import com.destinyai.astrology.services.QuotaManager
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
    // iOS parity (ProfileView binds profileContext.activeProfileName, D2/D3): the ACTIVE
    // profile's name (partner when a partner chart is active, else self) — first-name only.
    // Drives the "Viewing X's chart" row + Switch-Profile subtitle. Falls back to userName.
    val activeProfileName: String = "",
    val email: String = "",
    val isPremium: Boolean = false,
    val planId: String = "",
    // Terminal-aware entitlement gates (from QuotaManager, not raw planId). A lapsed Plus
    // user keeps plan_id="plus" on the backend, so raw-string gates would still grant these.
    val isPlusEntitled: Boolean = false,
    val hasSwitchProfile: Boolean = false,
    val hasMaintainProfile: Boolean = false,
    val hasAlerts: Boolean = false,
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
    // iOS parity (QuotaManager.subscriptionStatusDisplayText): per-status capsule label
    // (Active / Expired / Grace Period / Payment Failed / Subscription Revoked / Refunded).
    val subscriptionStatusText: String? = null,
    // iOS parity (ProfileView.showPaidCard, :607-639): render the paid card — with a
    // status-aware Renew/Manage/Contact CTA — for premium users AND lapsed-paid users
    // (expired/canceled/revoked/refunded/billing_retry), instead of dropping a lapsed
    // payer to the generic "Upgrade to Premium" card (M2).
    val showPaidCard: Boolean = false,    // iOS parity (QuotaManager.subscriptionStatusDetailText / subscriptionStatusCTA, M1):
    // per-status body copy + CTA label shown on the paid card. Null CTA = no button
    // (active + auto-renew).
    val subscriptionStatusDetailText: String? = null,
    val subscriptionStatusCTA: String? = null,
    // History-cleared success alert: number of threads deleted. Null = alert hidden.
    // Mirrors iOS ProfileView.clearedThreadCount + showClearSuccessAlert (line 227-243).
    val clearedThreadCount: Int? = null,
    // Delete account in-flight + inline error. Mirrors iOS isDeletingAccount /
    // deleteErrorMessage at ProfileView.swift:844-872. Sheet stays open while
    // isDeleting=true; deleteErrorMessage renders inline above the confirm button.
    val isDeletingAccount: Boolean = false,
    val deleteErrorMessage: String? = null,
    // iOS parity (DeleteAccountSheet shows a proactive "Sign In" affordance when the
    // session is stale/expired, ProfileView.swift:206-215): drives the re-auth CTA (G3).
    val deleteSessionExpired: Boolean = false,
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
    // iOS parity (ProfileView.swift:1059): when the server value is nil, US region
    // defaults to TRUE (opt-out model), non-US defaults to FALSE (GDPR opt-in). The
    // prior `region != "US"` was the exact inverse of iOS for both regions.
    val region = Locale.getDefault().country.uppercase(Locale.ROOT)
    return region == "US"
}

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val api: AstroApiService,
    private val prefs: UserPreferences,
    private val authRepository: AuthRepository,
    private val billingManager: BillingManager,
    private val profileChangeBus: ProfileChangeBus,
    private val profileContextManager: com.destinyai.astrology.services.ProfileContextManager,
    // iOS parity: gate Profile's Plus-only rows (Switch Profile / Manage Charts / Alerts)
    // on the terminal-aware QuotaManager entitlement, not a raw plan_id string (backend
    // keeps plan_id="plus" after expiry, so a lapsed user would still see them entitled).
    private val quotaManager: com.destinyai.astrology.services.QuotaManager,
    // iOS parity (HistorySettingsManager.clearAllHistory step 2-3 at
    // HistorySettingsManager.swift:118-122): after the server DELETE succeeds,
    // wipe local Room mirrors so Chat history sheet + Match list flush
    // immediately. The API call remains authoritative — these DAOs are only
    // used to clear the local cache.
    private val threadDao: com.destinyai.astrology.data.local.db.ChatThreadDao,
    private val messageDao: com.destinyai.astrology.data.local.db.ChatMessageDao,
    private val compatibilityHistoryDao: com.destinyai.astrology.data.local.db.CompatibilityHistoryDao,
    private val sessionStore: com.destinyai.astrology.data.local.prefs.SessionTokenStore,
    @dagger.hilt.android.qualifiers.ApplicationContext private val appContext: android.content.Context,
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
                    // iOS parity (D2/D3): keep the "Viewing X's chart" row + Switch subtitle
                    // in sync when the active profile changes without a full reload.
                    _uiState.update { it.copy(activeProfileName = firstNameFrom(displayName)) }
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
                // Keep QuotaManager (the authoritative entitlement store the other screens
                // read) fresh from the same status, so its terminal-aware isPlus + feature
                // list reflect what Profile is about to render. force=true: user opened Profile.
                runCatching { quotaManager.syncStatus(email, force = true) }
                val name = prefs.getUserName() ?: status.name ?: ""
                // iOS parity (D2/D3): active-profile first name (partner when a partner chart
                // is active, else self). Best-effort — falls back to self name on failure.
                val activeName = runCatching { firstNameFrom(profileContextManager.activeProfileName()) }
                    .getOrDefault(firstNameFrom(name))
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
                // iOS parity (QuotaManager.swift:863-887 subscriptionExpiryDisplayText):
                // prefix the formatted expiry by state — Renews/Ends/Expired/Expires.
                val statusLower = status.subscriptionStatus?.lowercase()
                val expiryMs = status.subscriptionExpiresAt?.takeIf { it.isNotBlank() }?.let { iso ->
                    runCatching { java.time.Instant.parse(iso).toEpochMilli() }.getOrNull()
                }
                val isPast = expiryMs != null && expiryMs <= System.currentTimeMillis()
                val formattedDate = expiryMs?.let {
                    DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(it))
                }
                val expiryDisplay = formattedDate?.let { d ->
                    when {
                        statusLower == "expired" || isPast -> "Expired on $d"
                        statusLower == "grace_period" -> "Ends on $d"
                        statusLower == "active" && status.autoRenewStatus == true -> "Renews on $d"
                        status.autoRenewStatus == false -> "Ends on $d"
                        else -> "Expires on $d"
                    }
                }
                // iOS parity (QuotaManager.swift:903-922 subscriptionStatusDisplayText):
                // per-status capsule label instead of a static "Active".
                val statusText = when (statusLower) {
                    "active" -> "Active"
                    "canceled", "cancelled" -> if (expiryMs != null && expiryMs > System.currentTimeMillis()) "Active" else "Expired"
                    "expired" -> "Expired"
                    "grace_period" -> "Grace Period"
                    "billing_retry" -> "Payment Failed"
                    "revoked" -> "Subscription Revoked"
                    "refunded" -> "Refunded"
                    else -> if (status.isPremium) "Active" else null
                }
                // iOS parity (ProfileView.showPaidCard :620-639): a lapsed-paid user still
                // sees the paid card (with Renew/Manage CTA), not the generic upgrade card.
                val lapsedStatuses = setOf("expired", "canceled", "cancelled", "revoked", "refunded", "billing_retry")
                val showPaidCard = status.isPremium || (statusLower != null && statusLower in lapsedStatuses)
                // iOS parity (QuotaManager.subscriptionStatusDetailText, :927-949): per-status body.
                val statusDetail = when (statusLower) {
                    "active" -> if (status.autoRenewStatus == false) {
                        "Your plan is active and will end at the next renewal date."
                    } else {
                        "Your subscription is active and renews automatically."
                    }
                    "expired" -> "Your subscription has ended. Renew to keep premium features."
                    "grace_period" -> "Google is retrying your payment. Update your payment method to keep your subscription active."
                    "canceled", "cancelled" -> "Auto-renew is off. You'll keep premium features until the period ends."
                    "billing_retry" -> "Your payment failed. Update your payment method in Google Play to restore access."
                    "revoked" -> "Your subscription was revoked. This can happen after a refund or billing dispute. Subscribe again to restore premium features."
                    "refunded" -> "Your purchase was refunded. Contact support if this was unexpected."
                    else -> null
                }
                // iOS parity (QuotaManager.subscriptionStatusCTA, :954-971): CTA label per
                // status; null = no button (active + auto-renew). Canonical English labels
                // — the screen routes on these exact strings (like iOS handleStatusCTA).
                val statusCta = when (statusLower) {
                    "active" -> if (status.autoRenewStatus == false) "Re-enable auto-renew" else null
                    "expired" -> "Renew subscription"
                    "grace_period" -> "Update payment method"
                    "canceled", "cancelled" -> "Manage subscription"
                    "billing_retry" -> "Update payment method"
                    "revoked" -> "Resubscribe"
                    "refunded" -> "Contact support"
                    else -> null
                }
                _uiState.update {
                    it.copy(
                        userName = name,
                        activeProfileName = activeName,
                        email = status.userEmail,
                        isPremium = status.isPremium,
                        planId = status.planId ?: "",
                        // Terminal-aware gates from QuotaManager (not raw planId).
                        isPlusEntitled = quotaManager.isPlus,
                        hasSwitchProfile = quotaManager.hasFeature(QuotaManager.FeatureID.SWITCH_PROFILE) &&
                            !quotaManager.isInTerminalPaidStatus,
                        hasMaintainProfile = quotaManager.hasFeature(QuotaManager.FeatureID.MAINTAIN_PROFILE) &&
                            !quotaManager.isInTerminalPaidStatus,
                        hasAlerts = quotaManager.hasFeature(QuotaManager.FeatureID.ALERTS) &&
                            !quotaManager.isInTerminalPaidStatus,
                        dailyQuota = status.dailyQuota ?: 0,
                        dailyUsed = status.dailyUsed ?: 0,
                        isLoading = false,
                        historyEnabled = historyEnabled,
                        analyticsConsent = analyticsConsent,
                        hasActiveSubscription = status.isPremium && activePurchase,
                        chartStyle = chartStyle,
                        subscriptionExpiryDisplayText = expiryDisplay,
                        subscriptionStatusText = statusText,
                        showPaidCard = showPaidCard,
                        subscriptionStatusDetailText = statusDetail,
                        subscriptionStatusCTA = statusCta,
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
                // iOS parity (ProfileView.swift:1103-1108 reverts on catch, D4): the toggle
                // reflects PERSISTED consent — if the server write fails, flip it back so it
                // doesn't misrepresent the user's actual GDPR/analytics consent state.
                _uiState.update { it.copy(analyticsConsent = !enabled) }
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

    fun showDeleteConfirmation() = _uiState.update { it.copy(showDeleteSheet = true, showDeleteConfirmation = true, deleteErrorMessage = null, deleteSessionExpired = false) }

    fun dismissDeleteConfirmation() = _uiState.update {
        // Block dismiss while a delete is mid-flight — mirrors iOS
        // .interactiveDismissDisabled(isDeleting) at DeleteAccountSheet.swift:164.
        if (it.isDeletingAccount) it else it.copy(showDeleteSheet = false, showDeleteConfirmation = false, deleteErrorMessage = null)
    }

    fun confirmDeleteAccount() {
        viewModelScope.launch {
            val email = prefs.getUserEmail() ?: return@launch
            // iOS ProfileService.swift:579-582 — irreversible action REQUIRES a
            // fresh session JWT. Surface sessionExpired instead of a doomed 401.
            if (!sessionStore.sessionIsFresh() || sessionStore.currentSessionJwt() == null) {
                _uiState.update {
                    it.copy(
                        isDeletingAccount = false,
                        deleteSessionExpired = true,
                        deleteErrorMessage = appContext.getString(
                            com.destinyai.astrology.R.string.delete_session_expired_message,
                        ),
                    )
                }
                return@launch
            }
            val jwt = sessionStore.currentSessionJwt()!!
            // Keep sheet open with spinner; clear any stale error.
            _uiState.update { it.copy(isDeletingAccount = true, deleteErrorMessage = null) }
            try {
                api.deleteAccount("Bearer $jwt", DeleteAccountRequest(userEmail = email))
                // iOS parity (delete success runs the FULL sign-out teardown via
                // AuthViewModel.signOutAsync): bare clearActiveSession()+clearAll() left the
                // deleted account's quota/billing in-memory state and Room rows resident on a
                // shared device (DL-1). clearSession() resets quota + billing and wipes the
                // owner-scoped astro-cache + compat-history; also drop this email's chat
                // threads + messages (not covered by clearSession).
                runCatching {
                    messageDao.deleteAllForUser(email)
                    threadDao.deleteAllForUser(email)
                }
                authRepository.clearSession()
                _uiState.update {
                    it.copy(
                        isDeletingAccount = false,
                        showDeleteSheet = false,
                        showDeleteConfirmation = false,
                        isDeleted = true,
                    )
                }
            } catch (e: retrofit2.HttpException) {
                when (e.code()) {
                    401 -> _uiState.update {
                        it.copy(
                            isDeletingAccount = false,
                            deleteSessionExpired = true,
                            deleteErrorMessage = appContext.getString(
                                com.destinyai.astrology.R.string.delete_session_expired_message,
                            ),
                        )
                    }
                    // Mirrors iOS ProfileService:537-544/609-629 — 403/409 = active
                    // subscription blocks deletion. Parse the server detail message
                    // (dict or string shape); fall back to a localized string.
                    403, 409 -> {
                        val detail = runCatching {
                            val raw = e.response()?.errorBody()?.string().orEmpty()
                            val d = com.google.gson.JsonParser.parseString(raw).asJsonObject.get("detail")
                            when {
                                d?.isJsonObject == true -> d.asJsonObject.get("message")?.asString
                                d?.isJsonPrimitive == true -> d.asString
                                else -> null
                            }
                        }.getOrNull()
                        _uiState.update {
                            it.copy(
                                isDeletingAccount = false,
                                deleteErrorMessage = detail
                                    ?: "Please cancel your subscription before deleting your account.",
                            )
                        }
                    }
                    else -> _uiState.update {
                        it.copy(isDeletingAccount = false, deleteErrorMessage = e.message ?: "Failed to delete account")
                    }
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
