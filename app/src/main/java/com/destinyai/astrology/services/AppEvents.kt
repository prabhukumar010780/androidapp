package com.destinyai.astrology.services

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Global app-wide event bus for cross-feature one-shot signals.
 *
 * Parity with iOS NotificationCenter.default broadcasts (e.g. `.openProfileSettings`).
 * On Android, deep-link handlers and notification routers emit on this bus so Compose
 * screens can observe events without holding direct references to each other.
 *
 * Usage:
 *   - Emit:    `appEvents.emitOpenProfileSettings()`
 *   - Collect: `appEvents.openProfileSettings.collect { ... }`
 */
@Singleton
class AppEvents @Inject constructor() {

    private val _openProfileSettings = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /**
     * Fired when an external trigger (deep-link, notification, settings tap from
     * another screen) wants the Home surface to present the profile sheet.
     * Mirrors iOS `NotificationCenter.default.publisher(for: .openProfileSettings)`.
     */
    val openProfileSettings: SharedFlow<Unit> = _openProfileSettings.asSharedFlow()

    suspend fun emitOpenProfileSettings() {
        _openProfileSettings.emit(Unit)
    }
}
