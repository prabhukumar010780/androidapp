package com.destinyai.astrology.data.remote

import com.destinyai.astrology.data.local.prefs.SessionTokenStore
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import javax.inject.Provider

/** iOS APIClient 401-session_expired parity: on a 401, refresh the session JWT
 *  ONCE and retry. Only for requests that used the session bearer (not the
 *  API-key fallback, and not an explicit delete-account bearer which surfaces
 *  its own sessionExpired). */
class SessionAuthenticator(
    private val store: SessionTokenStore,
    private val exchangeClient: Provider<AuthExchangeClient>,
) : Authenticator {
    override fun authenticate(route: Route?, response: Response): Request? {
        // Only retry once.
        if (responseCount(response) >= 2) return null
        val staleJwt = store.currentSessionJwt() ?: return null
        val sentBearer = response.request.header("Authorization")
        // Only refresh if the failing request actually used the session JWT.
        if (sentBearer != "Bearer $staleJwt") return null

        val newJwt = runCatching { runBlocking { exchangeClient.get().refresh() }.sessionJwt }
            .getOrNull() ?: return null

        return response.request.newBuilder()
            .header("Authorization", "Bearer $newJwt")
            .build()
    }

    private fun responseCount(response: Response): Int {
        var r: Response? = response
        var c = 1
        while (r?.priorResponse != null) {
            c++
            r = r.priorResponse
        }
        return c
    }
}
