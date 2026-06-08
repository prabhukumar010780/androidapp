package com.destinyai.astrology.ui.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.destinyai.astrology.data.local.prefs.UserPreferences
import com.destinyai.astrology.data.remote.AstroApiService
import com.destinyai.astrology.data.remote.NotificationDto
import com.destinyai.astrology.services.QuotaManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject

data class NotificationsUiState(
    val notifications: List<NotificationDto> = emptyList(),
    val unreadCount: Int = 0,
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isRefreshing: Boolean = false,
    val hasMore: Boolean = false,
    val currentPage: Int = 1,
    val pageSize: Int = 20,
    val isGuest: Boolean = false,
    val hasAlertsFeature: Boolean = false,
    val error: String? = null,
    /** iOS parity gap fix — surface mark/markAll failures so the UI can render a Snackbar
     *  instead of silently swallowing the error. Cleared via [clearMarkError]. */
    val markErrorKind: MarkErrorKind? = null,
)

enum class MarkErrorKind { MARK_READ, MARK_ALL_READ }

@HiltViewModel
class NotificationsViewModel @Inject constructor(
    private val api: AstroApiService,
    private val prefs: UserPreferences,
    private val quotaManager: QuotaManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(NotificationsUiState())
    val uiState: StateFlow<NotificationsUiState> = _uiState

    fun loadNotifications() {
        viewModelScope.launch {
            val email = prefs.getUserEmail() ?: return@launch
            val isGuest = prefs.isGuestUser() || email.endsWith("@daa.com")
            val hasAlerts = quotaManager.hasFeature(QuotaManager.FeatureID.ALERTS)
            _uiState.update {
                it.copy(
                    isLoading = true,
                    isRefreshing = true,
                    error = null,
                    currentPage = 1,
                    isGuest = isGuest,
                    hasAlertsFeature = hasAlerts,
                )
            }
            try {
                val pageSize = _uiState.value.pageSize
                val response = api.listNotifications(email, page = 1, pageSize = pageSize)
                val unread = api.getUnreadCount(email)
                _uiState.update {
                    it.copy(
                        notifications = response.notifications,
                        unreadCount = unread.count,
                        hasMore = response.hasMore ?: (response.notifications.size >= pageSize),
                        isLoading = false,
                        isRefreshing = false,
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        error = e.message ?: "Failed to load",
                    )
                }
            }
        }
    }

    /** Mirrors iOS NotificationInboxService.loadMore() — appends next page if available. */
    fun loadMore() {
        val s = _uiState.value
        if (s.isLoading || s.isLoadingMore || !s.hasMore) return
        viewModelScope.launch {
            val email = prefs.getUserEmail() ?: return@launch
            _uiState.update { it.copy(isLoadingMore = true) }
            try {
                val nextPage = s.currentPage + 1
                val response = api.listNotifications(email, page = nextPage, pageSize = s.pageSize)
                _uiState.update {
                    it.copy(
                        notifications = it.notifications + response.notifications,
                        currentPage = nextPage,
                        hasMore = response.hasMore ?: (response.notifications.size >= s.pageSize),
                        isLoadingMore = false,
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoadingMore = false, error = e.message) }
            }
        }
    }

    fun refresh() = loadNotifications()

    fun markAllRead() {
        viewModelScope.launch {
            val email = prefs.getUserEmail() ?: return@launch
            try {
                api.markAllRead(email)
                val now = nowIso8601()
                _uiState.update { state ->
                    state.copy(
                        unreadCount = 0,
                        // iOS parity (NotificationInboxService.swift:194-215): mirror
                        // status="READ" + readAt timestamp alongside isRead so any
                        // server-side state checks stay aligned with the client cache.
                        notifications = state.notifications.map {
                            it.copy(isRead = true, status = "READ", readAt = now)
                        },
                    )
                }
            } catch (_: Exception) {
                _uiState.update { it.copy(markErrorKind = MarkErrorKind.MARK_ALL_READ) }
            }
        }
    }

    fun markRead(notificationId: String) {
        viewModelScope.launch {
            try {
                api.markNotificationRead(notificationId)
                val now = nowIso8601()
                _uiState.update { state ->
                    val updated = state.notifications.map {
                        if (it.id == notificationId) {
                            it.copy(isRead = true, status = "READ", readAt = now)
                        } else it
                    }
                    val unread = updated.count { !it.isRead }
                    state.copy(notifications = updated, unreadCount = unread)
                }
            } catch (_: Exception) {
                _uiState.update { it.copy(markErrorKind = MarkErrorKind.MARK_READ) }
            }
        }
    }

    /** Clear the transient mark/markAll error after the Snackbar dismisses or the user retries. */
    fun clearMarkError() {
        _uiState.update { it.copy(markErrorKind = null) }
    }

    private fun nowIso8601(): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        fmt.timeZone = TimeZone.getTimeZone("UTC")
        return fmt.format(Date())
    }
}
