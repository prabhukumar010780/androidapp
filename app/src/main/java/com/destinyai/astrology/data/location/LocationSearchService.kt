package com.destinyai.astrology.data.location

import com.destinyai.astrology.data.remote.AstroApiService
import com.destinyai.astrology.data.remote.LocationResult
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocationSearchService @Inject constructor(
    private val api: AstroApiService,
) {
    suspend fun search(query: String): List<LocationResult> =
        if (query.length >= 2) {
            try { api.searchLocations(query) } catch (_: Exception) { emptyList() }
        } else {
            emptyList()
        }
}
