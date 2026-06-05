package com.destinyai.astrology.services

import com.destinyai.astrology.data.remote.AstroApiService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Mirrors iOS AppStartupService (Services/AppStartupService.swift).
 * Fetches gate config from backend on launch. Drives guest CTA visibility +
 * gate-mode awareness in AuthScreen. Cached for 15 minutes; transient errors
 * keep prior cached values intact (defaults: gateMode="off", allowGuest=false).
 */
@Singleton
class AppStartupService @Inject constructor(
    private val api: AstroApiService,
) {

    private val _gateMode = MutableStateFlow("off")
    val gateMode: StateFlow<String> = _gateMode.asStateFlow()

    private val _allowGuest = MutableStateFlow(false)
    val allowGuest: StateFlow<Boolean> = _allowGuest.asStateFlow()

    private var lastFetchedAt: Long? = null
    private val cacheTtlMs: Long = 15 * 60 * 1000 // 15 min

    suspend fun fetchConfig() {
        val now = System.currentTimeMillis()
        val last = lastFetchedAt
        if (last != null && now - last < cacheTtlMs) return
        try {
            val resp = api.getAppConfig()
            _gateMode.value = resp.gateMode
            _allowGuest.value = resp.allowGuest
            lastFetchedAt = now
        } catch (e: Exception) {
            // Leave prior cached values intact on transient network failure.
            android.util.Log.w("AppStartupService", "fetchConfig failed: ${e.message}")
        }
    }
}
