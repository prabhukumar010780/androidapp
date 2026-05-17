package com.destinyai.astrology.ui.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.destinyai.astrology.data.local.prefs.UserPreferences
import com.destinyai.astrology.data.remote.AstroApiService
import com.destinyai.astrology.data.remote.NotificationDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class NotificationsUiState(
    val notifications: List<NotificationDto> = emptyList(),
    val unreadCount: Int = 0,
    val isLoading: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class NotificationsViewModel @Inject constructor(
    private val api: AstroApiService,
    private val prefs: UserPreferences,
) : ViewModel() {

    private val _uiState = MutableStateFlow(NotificationsUiState())
    val uiState: StateFlow<NotificationsUiState> = _uiState

    fun loadNotifications() {
        viewModelScope.launch {
            val email = prefs.getUserEmail() ?: return@launch
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val response = api.listNotifications(email)
                val unread = api.getUnreadCount(email)
                _uiState.update {
                    it.copy(
                        notifications = response.notifications,
                        unreadCount = unread.count,
                        isLoading = false,
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "Failed to load") }
            }
        }
    }

    fun markAllRead() {
        viewModelScope.launch {
            val email = prefs.getUserEmail() ?: return@launch
            try {
                api.markAllRead(email)
                _uiState.update { state ->
                    state.copy(
                        unreadCount = 0,
                        notifications = state.notifications.map { it.copy(isRead = true) },
                    )
                }
            } catch (_: Exception) {}
        }
    }

    fun markRead(notificationId: String) {
        viewModelScope.launch {
            try {
                api.markNotificationRead(notificationId)
                _uiState.update { state ->
                    val updated = state.notifications.map {
                        if (it.id == notificationId) it.copy(isRead = true) else it
                    }
                    val unread = updated.count { !it.isRead }
                    state.copy(notifications = updated, unreadCount = unread)
                }
            } catch (_: Exception) {}
        }
    }
}
