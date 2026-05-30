package com.destinyai.astrology.services

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * R2-Z6: Singleton event bus that broadcasts the newly active profile email after a
 * profile switch. ProfileSwitcherViewModel emits; HomeViewModel, ChatViewModel, and
 * CompatibilityViewModel collect.
 */
@Singleton
class ProfileChangeBus @Inject constructor() {

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 1)

    /** Observe profile-switch events. Collect in ViewModels that need to reload on switch. */
    val events: SharedFlow<String> = _events.asSharedFlow()

    /** Emit the new active profile email after a successful switch. */
    suspend fun emit(newEmail: String) {
        _events.emit(newEmail)
    }
}
