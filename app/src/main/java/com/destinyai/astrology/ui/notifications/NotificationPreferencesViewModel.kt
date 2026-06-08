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
    val isLoading: Boolean = false,
    val isSaved: Boolean = false,
    val error: String? = null,
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

    fun loadPrefs() {
        viewModelScope.launch {
            val email = prefs.getUserEmail() ?: return@launch
            _uiState.update { it.copy(isLoading = true) }
            try {
                val dto = api.getNotificationPrefs(email)
                val alerts = prefs.getAlertItems()
                _uiState.update {
                    it.copy(
                        dailyInsight = dto.dailyInsight,
                        transits = dto.transits,
                        compatibility = dto.compatibility,
                        pushEnabled = prefs.getNotifPushEnabled(),
                        emailEnabled = prefs.getNotifEmailEnabled(),
                        inAppEnabled = prefs.getNotifInAppEnabled(),
                        alertItems = alerts,
                        isLoading = false,
                    )
                }
            } catch (_: Exception) {
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

    // ── Legacy channel methods ────────────────────────────────────────────────

    fun setDailyInsight(enabled: Boolean) = _uiState.update { it.copy(dailyInsight = enabled) }
    fun setTransits(enabled: Boolean) = _uiState.update { it.copy(transits = enabled) }
    fun setCompatibility(enabled: Boolean) = _uiState.update { it.copy(compatibility = enabled) }

    // ── R2-S7 channel toggles ─────────────────────────────────────────────────

    fun setPushEnabled(enabled: Boolean) {
        _uiState.update { it.copy(pushEnabled = enabled) }
        viewModelScope.launch { prefs.setNotifPushEnabled(enabled) }
    }

    fun setEmailEnabled(enabled: Boolean) {
        _uiState.update { it.copy(emailEnabled = enabled) }
        viewModelScope.launch { prefs.setNotifEmailEnabled(enabled) }
    }

    fun setInAppEnabled(enabled: Boolean) {
        _uiState.update { it.copy(inAppEnabled = enabled) }
        viewModelScope.launch { prefs.setNotifInAppEnabled(enabled) }
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
                        timezone = java.util.TimeZone.getDefault().id,
                    ),
                )
                prefs.saveAlertItems(s.alertItems)
                _uiState.update { it.copy(isLoading = false, isSaved = true) }
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
