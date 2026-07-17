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
            // Send the API key as Bearer — this endpoint uses require_api_key and rejects
            // session JWTs. Explicit header so AuthInterceptor won't override with a JWT.
            LocationSearchResult.Success(
                api.searchLocations("Bearer ${com.destinyai.astrology.BuildConfig.API_KEY}", query),
            )
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
        } catch (e: com.destinyai.astrology.data.remote.ApiException) {
            // ErrorInterceptor converts non-2xx into ApiException (an IOException subclass).
            // Map by statusCode so a 401/403 is shown as Auth ("sign in again"), NOT as a
            // false "No internet connection" — which is what happened here (guest JWT → 401).
            val reason = when (e.statusCode) {
                401, 403 -> LocationSearchResult.Reason.Auth
                in 500..599 -> LocationSearchResult.Reason.Server
                else -> LocationSearchResult.Reason.Server
            }
            Log.w("LocationSearchService", "ApiException ${e.statusCode} on location search", e)
            LocationSearchResult.Failure(reason, e.serverMessage)
        } catch (e: IOException) {
            Log.w("LocationSearchService", "Network failure on location search", e)
            LocationSearchResult.Failure(LocationSearchResult.Reason.Network, e.message)
        } catch (e: Exception) {
            Log.e("LocationSearchService", "Unexpected location search failure", e)
            LocationSearchResult.Failure(LocationSearchResult.Reason.Unknown, e.message)
        }
    }
}
