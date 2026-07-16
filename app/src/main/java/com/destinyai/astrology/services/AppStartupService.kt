package com.destinyai.astrology.services

import com.destinyai.astrology.BuildConfig
import com.destinyai.astrology.data.remote.AstroApiService
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Mirrors iOS AppStartupService (Services/AppStartupService.swift) + AppConfig.swift.
 * Fetches gate + streaming config from backend on launch. Drives guest CTA visibility,
 * gate-mode awareness, and the server-driven streaming kill-switch / cohort rollout /
 * min-version gate. Cached for 15 minutes; transient errors keep prior cached values.
 */
@Singleton
class AppStartupService @Inject constructor(
    private val api: AstroApiService,
) {

    private val _gateMode = MutableStateFlow("off")
    val gateMode: StateFlow<String> = _gateMode.asStateFlow()

    private val _allowGuest = MutableStateFlow(false)
    val allowGuest: StateFlow<Boolean> = _allowGuest.asStateFlow()

    // iOS parity (AppConfig.swift:14-70): streaming controls.
    private val _streamingEnabled = MutableStateFlow(true)
    val streamingEnabled: StateFlow<Boolean> = _streamingEnabled.asStateFlow()

    private val _streamingCohortPercent = MutableStateFlow(100)
    private val _streamingMinAppVersion = MutableStateFlow<String?>(null)

    private var lastFetchedAt: Long? = null
    private val cacheTtlMs: Long = 15 * 60 * 1000 // 15 min

    /** TTL-guarded fetch used at splash / auth. */
    suspend fun fetchConfig() {
        val now = System.currentTimeMillis()
        val last = lastFetchedAt
        if (last != null && now - last < cacheTtlMs) return
        doFetch()
    }

    /**
     * iOS parity (AppStartupService.swift:90-96 refreshAppConfig, C-1 fix): un-TTL-guarded
     * fetch for foreground (ON_RESUME) transitions so a kill-switch / gate-mode flip
     * propagates to an already-running app within the client poll cadence.
     */
    suspend fun refreshConfig() = doFetch()

    private suspend fun doFetch() {
        // iOS parity (AppStartupService.swift:36-53): retry once after 5s on failure to
        // survive Cloud Run cold starts (idle scale-to-zero → 20-60s first response).
        repeat(2) { attempt ->
            try {
                val resp = api.getAppConfig()
                _gateMode.value = resp.gateMode
                _allowGuest.value = resp.allowGuest
                _streamingEnabled.value = resp.streamingEnabled
                _streamingCohortPercent.value = resp.streamingCohortPercent
                _streamingMinAppVersion.value = resp.streamingMinAppVersion
                lastFetchedAt = System.currentTimeMillis()
                return
            } catch (e: Exception) {
                android.util.Log.w("AppStartupService", "fetchConfig attempt ${attempt + 1} failed: ${e.message}")
                if (attempt == 0) delay(5_000)
            }
        }
    }

    /**
     * iOS parity (AppConfig.shouldStreamFor): decide whether to use the streaming path
     * for [userId] — gated on the kill-switch, min-app-version, and an FNV-1a cohort
     * bucket so gradual rollout is deterministic per user.
     */
    fun shouldStreamFor(userId: String?): Boolean {
        if (!_streamingEnabled.value) return false
        if (!versionAllowed(_streamingMinAppVersion.value)) return false
        val cohort = _streamingCohortPercent.value
        if (cohort >= 100) return true
        if (cohort <= 0) return false
        val id = userId?.takeIf { it.isNotBlank() } ?: return true
        return (fnv1a(id) % 100u).toInt() < cohort
    }

    /** True if the current app version >= [minVersion] (or no minimum set). */
    private fun versionAllowed(minVersion: String?): Boolean {
        val min = minVersion?.takeIf { it.isNotBlank() } ?: return true
        return compareVersions(BuildConfig.VERSION_NAME, min) >= 0
    }

    private fun compareVersions(a: String, b: String): Int {
        val pa = a.split(".").map { it.toIntOrNull() ?: 0 }
        val pb = b.split(".").map { it.toIntOrNull() ?: 0 }
        val n = maxOf(pa.size, pb.size)
        for (i in 0 until n) {
            val x = pa.getOrElse(i) { 0 }
            val y = pb.getOrElse(i) { 0 }
            if (x != y) return x.compareTo(y)
        }
        return 0
    }

    /** FNV-1a 32-bit hash (matches iOS AppConfig cohort bucketing). */
    private fun fnv1a(s: String): UInt {
        var hash = 2166136261u
        for (b in s.toByteArray(Charsets.UTF_8)) {
            hash = hash xor (b.toUInt() and 0xFFu)
            hash *= 16777619u
        }
        return hash
    }
}
