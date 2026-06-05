package com.destinyai.astrology.data.location

import android.util.Log
import com.destinyai.astrology.data.remote.AstroApiService
import com.destinyai.astrology.data.remote.LocationResult
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Result wrapper for location search calls.
 *
 * Mirrors the iOS LocationSearchService pattern of distinguishing
 * "no matches" from "the network or the backend itself failed".
 * BirthDataViewModel renders these states differently in
 * LocationSearchSheet — see GAP-2 in the parity audit.
 */
sealed class LocationSearchResult {
    /** Successful response — list may legitimately be empty (no matches). */
    data class Success(val results: List<LocationResult>) : LocationSearchResult()

    /** Network or auth/backend failure — UI should surface a retry message. */
    data class Failure(val reason: Reason, val message: String? = null) : LocationSearchResult()

    enum class Reason {
        Network, // IOException — no connectivity
        Auth, // HTTP 401/403 — missing or invalid API key
        Server, // HTTP 5xx or upstream Google Places failure
        Unknown,
    }
}

@Singleton
class LocationSearchService @Inject constructor(
    private val api: AstroApiService,
) {
    /**
     * Search the backend `/api/v2/location/search` endpoint.
     *
     * Returns Success(emptyList()) for queries shorter than 2 characters so
     * callers can use a single result-type branch in the UI.
     */
    suspend fun search(query: String): LocationSearchResult {
        if (query.length < 2) return LocationSearchResult.Success(emptyList())
        return try {
            LocationSearchResult.Success(api.searchLocations(query))
        } catch (e: HttpException) {
            // GAP-2: distinguish auth from generic server errors so the UI can
            // tell the user "sign in again" vs "try again later".
            val reason = when (e.code()) {
                401, 403 -> LocationSearchResult.Reason.Auth
                in 500..599 -> LocationSearchResult.Reason.Server
                else -> LocationSearchResult.Reason.Server
            }
            Log.w("LocationSearchService", "HTTP ${e.code()} from /api/v2/location/search", e)
            LocationSearchResult.Failure(reason, e.message())
        } catch (e: IOException) {
            Log.w("LocationSearchService", "Network failure on location search", e)
            LocationSearchResult.Failure(LocationSearchResult.Reason.Network, e.message)
        } catch (e: Exception) {
            Log.e("LocationSearchService", "Unexpected location search failure", e)
            LocationSearchResult.Failure(LocationSearchResult.Reason.Unknown, e.message)
        }
    }
}
