package com.destinyai.astrology.ui.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.destinyai.astrology.data.local.prefs.AlertItem
import com.destinyai.astrology.data.local.prefs.UserPreferences
import com.destinyai.astrology.data.remote.AlertItemDto
import com.destinyai.astrology.data.remote.AstroApiService
import com.destinyai.astrology.data.remote.NotificationPrefsRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class NotificationPreferencesUiState(
    // Legacy toggles (kept for API compat)
    val dailyInsight: Boolean = true,
    val transits: Boolean = true,
    val compatibility: Boolean = true,
    // R2-S7: channel toggles
    val pushEnabled: Boolean = true,
    val emailEnabled: Boolean = true,
    val inAppEnabled: Boolean = true,
    // R2-S8: permission
    val isPermissionGranted: Boolean = false,
    // R2-S13c: custom alerts
    val alertItems: List<AlertItem> = emptyList(),
    // iOS parity (preferredTimeUTC default "00:30"): round-tripped delivery time.
    val preferredTimeUtc: String = "00:30",
    val isLoading: Boolean = false,
    val isSaved: Boolean = false,
    val error: String? = null,
    // Batch 6b fix #9: true when channel-toggles or delivery-time differ from the
    // last-loaded server state. Used to prompt the user on Back instead of silently
    // discarding changes.
    val hasUnsavedChanges: Boolean = false,
) {
    /** R2-S13g: true when fewer than 5 custom alerts exist. */
    val canAddMore: Boolean get() = alertItems.size < 5
}

@HiltViewModel
class NotificationPreferencesViewModel @Inject constructor(
    private val api: AstroApiService,
    private val prefs: UserPreferences,
) : ViewModel() {

    private val _uiState = MutableStateFlow(NotificationPreferencesUiState())
    val uiState: StateFlow<NotificationPreferencesUiState> = _uiState

    // Batch 6b fix #9: snapshot of server state at load time used to compute hasUnsavedChanges.
    private var loadedSnapshot: NotificationPreferencesUiState? = null

    private fun markDirty() {
        val snap = loadedSnapshot ?: return
        val s = _uiState.value
        val dirty = s.pushEnabled != snap.pushEnabled ||
            s.emailEnabled != snap.emailEnabled ||
            s.inAppEnabled != snap.inAppEnabled ||
            s.dailyInsight != snap.dailyInsight ||
            s.transits != snap.transits ||
            s.compatibility != snap.compatibility ||
            s.preferredTimeUtc != snap.preferredTimeUtc
        _uiState.update { it.copy(hasUnsavedChanges = dirty) }
    }

    fun loadPrefs() {
        viewModelScope.launch {
            val email = prefs.getUserEmail() ?: return@launch
            _uiState.update { it.copy(isLoading = true) }
            try {
                // iOS parity (applyFromAPI): the server `preferences` dict is the source of
                // truth for channels + alert items — hydrate from it, not local DataStore.
                val resp = api.getNotificationPrefs(email)
                val p = resp.preferences
                fun bool(key: String, default: Boolean) = when (val v = p[key]) {
                    is Boolean -> v
                    is Number -> v.toInt() != 0
                    else -> default
                }
                fun str(key: String): String? = p[key] as? String
                val serverAlerts = parseAlertItems(p["alert_items"])
                _uiState.update {
                    it.copy(
                        dailyInsight = bool("daily_insight", true),
                        transits = bool("transits", true),
                        compatibility = bool("compatibility", true),
                        pushEnabled = bool("push_enabled", true),
                        emailEnabled = bool("email_enabled", true),
                        inAppEnabled = bool("in_app_enabled", true),
                        alertItems = serverAlerts,
                        preferredTimeUtc = str("preferred_time_utc") ?: "00:30",
                        isLoading = false,
                    )
                }
                // Snapshot server state so we can compute hasUnsavedChanges on back.
                loadedSnapshot = _uiState.value
                // Mirror the server truth into local prefs so offline reloads match.
                prefs.setNotifPushEnabled(bool("push_enabled", true))
                prefs.setNotifEmailEnabled(bool("email_enabled", true))
                prefs.setNotifInAppEnabled(bool("in_app_enabled", true))
                prefs.saveAlertItems(serverAlerts)
            } catch (_: Exception) {
                // Network failure only: fall back to local DataStore.
                val alerts = prefs.getAlertItems()
                _uiState.update {
                    it.copy(
                        pushEnabled = prefs.getNotifPushEnabled(),
                        emailEnabled = prefs.getNotifEmailEnabled(),
                        inAppEnabled = prefs.getNotifInAppEnabled(),
                        alertItems = alerts,
                        isLoading = false,
                    )
                }
            }
        }
    }

    /** Parse the server `alert_items` list (a List<Map> from Gson) into AlertItem. */
    private fun parseAlertItems(raw: Any?): List<AlertItem> {
        val list = raw as? List<*> ?: return emptyList()
        return list.mapNotNull { entry ->
            val m = entry as? Map<*, *> ?: return@mapNotNull null
            val text = m["text"] as? String ?: return@mapNotNull null
            AlertItem(
                id = (m["id"] as? String) ?: UUID.randomUUID().toString(),
                text = text,
                frequency = (m["frequency"] as? String) ?: "DAILY",
                frequencyDay = (m["frequency_day"] as? Number)?.toInt(),
            )
        }
    }

    // ── Legacy channel methods ────────────────────────────────────────────────

    fun setDailyInsight(enabled: Boolean) {
        _uiState.update { it.copy(dailyInsight = enabled) }
        markDirty()
    }
    fun setTransits(enabled: Boolean) {
        _uiState.update { it.copy(transits = enabled) }
        markDirty()
    }
    fun setCompatibility(enabled: Boolean) {
        _uiState.update { it.copy(compatibility = enabled) }
        markDirty()
    }

    // ── R2-S7 channel toggles ─────────────────────────────────────────────────

    fun setPushEnabled(enabled: Boolean) {
        _uiState.update { it.copy(pushEnabled = enabled) }
        markDirty()
        viewModelScope.launch { prefs.setNotifPushEnabled(enabled) }
    }

    fun setEmailEnabled(enabled: Boolean) {
        _uiState.update { it.copy(emailEnabled = enabled) }
        markDirty()
        viewModelScope.launch { prefs.setNotifEmailEnabled(enabled) }
    }

    fun setInAppEnabled(enabled: Boolean) {
        _uiState.update { it.copy(inAppEnabled = enabled) }
        markDirty()
        viewModelScope.launch { prefs.setNotifInAppEnabled(enabled) }
    }

    // Batch 6b fix #7: delivery-time picker setter (UTC "HH:mm").
    fun setPreferredTimeUtc(time: String) {
        _uiState.update { it.copy(preferredTimeUtc = time) }
        markDirty()
    }

    // ── R2-S8: permission state update ───────────────────────────────────────

    fun setPermissionGranted(granted: Boolean) {
        _uiState.update { it.copy(isPermissionGranted = granted) }
    }

    // ── R2-S13c-e: custom alert CRUD ─────────────────────────────────────────

    /**
     * Append a new alert. No-ops if already at 5 items.
     */
    fun addAlert(text: String, frequency: String, frequencyDay: Int? = null) {
        val current = _uiState.value.alertItems
        if (current.size >= 5) return
        val updated = current + AlertItem(
            id = UUID.randomUUID().toString(),
            text = text,
            frequency = frequency,
            frequencyDay = frequencyDay,
        )
        _uiState.update { it.copy(alertItems = updated) }
        viewModelScope.launch { prefs.saveAlertItems(updated) }
    }

    fun updateAlert(id: String, text: String, frequency: String, frequencyDay: Int? = null) {
        val updated = _uiState.value.alertItems.map { item ->
            if (item.id == id) item.copy(text = text, frequency = frequency, frequencyDay = frequencyDay) else item
        }
        _uiState.update { it.copy(alertItems = updated) }
        viewModelScope.launch { prefs.saveAlertItems(updated) }
    }

    fun deleteAlert(id: String) {
        val updated = _uiState.value.alertItems.filter { it.id != id }
        _uiState.update { it.copy(alertItems = updated) }
        viewModelScope.launch { prefs.saveAlertItems(updated) }
    }

    // ── iOS parity: error/isSaved consumption helpers ────────────────────────

    /** iOS parity: clear the modal error after the user dismisses the alert. */
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    /**
     * iOS parity: reset the isSaved flag on screen entry (and after consumption)
     * so a stale flag from a previous save doesn't auto-dismiss the screen on re-entry.
     */
    fun resetIsSaved() {
        _uiState.update { it.copy(isSaved = false) }
    }

    // ── Save channel prefs to API ─────────────────────────────────────────────

    fun save() {
        viewModelScope.launch {
            val email = prefs.getUserEmail() ?: return@launch
            val s = _uiState.value
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                // iOS parity (NotificationPreferencesViewModel.swift:145-153): iOS sends
                //   is_enabled, email_enabled, push_enabled, in_app_enabled, alert_items,
                //   preferred_time_utc, timezone
                // and does NOT send the 3 legacy booleans (daily_insight/transits/compatibility),
                // so we leave those null here so Gson omits them and the server keeps its
                // own state for those fields.
                //
                // Frequency rawValue on iOS is uppercase ("DAILY" / "WEEKLY" / "MONTHLY"),
                // matching backend AlertItemRequest enum, so we uppercase here for parity.
                val alertDtos = s.alertItems.map {
                    AlertItemDto(id = it.id, text = it.text, frequency = it.frequency.uppercase(), frequencyDay = it.frequencyDay)
                }
                // iOS parity: master switch is_enabled = any channel on. If every channel is
                // off, the user has effectively disabled notifications — mirror iOS so the
                // backend's master flag stays in sync.
                val masterEnabled = s.pushEnabled || s.emailEnabled || s.inAppEnabled
                api.updateNotificationPrefs(
                    email,
                    NotificationPrefsRequest(
                        isEnabled = masterEnabled,
                        pushEnabled = s.pushEnabled,
                        emailEnabled = s.emailEnabled,
                        inAppEnabled = s.inAppEnabled,
                        alertItems = alertDtos,
                        preferredTimeUtc = s.preferredTimeUtc,
                        timezone = java.util.TimeZone.getDefault().id,
                    ),
                )
                prefs.saveAlertItems(s.alertItems)
                // Batch 6b fix #8: update snapshot so hasUnsavedChanges resets after save.
                loadedSnapshot = s
                _uiState.update { it.copy(isLoading = false, isSaved = true, hasUnsavedChanges = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "Failed to save") }
            }
        }
    }

    fun saveChannels() {
        viewModelScope.launch {
            val s = _uiState.value
            prefs.setNotifPushEnabled(s.pushEnabled)
            prefs.setNotifEmailEnabled(s.emailEnabled)
            prefs.setNotifInAppEnabled(s.inAppEnabled)
        }
    }
}
