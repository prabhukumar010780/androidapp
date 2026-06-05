package com.destinyai.astrology.services

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Mirrors iOS NotificationRouter — translates push notification `type` extras
 * into a [NotificationDeepLink] consumed by AppNav. Holds the pending deep
 * link in a StateFlow so MainActivity can publish from `onCreate`/`onNewIntent`
 * before Compose collects in AppNav.
 */
sealed class NotificationDeepLink {
    object Home : NotificationDeepLink()
    data class Chat(
        val prefill: String = "",
        val autoSubmit: Boolean = false,
        val newThread: Boolean = false,
    ) : NotificationDeepLink()
    object Match : NotificationDeepLink()
    object Settings : NotificationDeepLink()
}

object NotificationRouter {
    private val _pendingDeepLink = MutableStateFlow<NotificationDeepLink?>(null)
    val pendingDeepLink: StateFlow<NotificationDeepLink?> = _pendingDeepLink

    /** Routes by notification type, optionally prefilling chat input. */
    fun route(
        type: String?,
        prefill: String = "",
        autoSubmit: Boolean = false,
        newThread: Boolean = false,
    ) {
        val key = type?.uppercase().orEmpty()
        _pendingDeepLink.value = when (key) {
            "DAILY_PREDICTION_READY", "DAILY_PREDICTION",
            "TRANSIT_ALERT", "LIFE_ALERT", "CUSTOM_ALERT", "WELCOME" ->
                NotificationDeepLink.Chat(prefill = prefill, autoSubmit = autoSubmit, newThread = newThread)
            "COMPATIBILITY_READY" -> NotificationDeepLink.Match
            "SUBSCRIPTION_EXPIRING" -> NotificationDeepLink.Settings
            else -> NotificationDeepLink.Home
        }
    }

    fun consume(): NotificationDeepLink? {
        val current = _pendingDeepLink.value
        _pendingDeepLink.value = null
        return current
    }
}
