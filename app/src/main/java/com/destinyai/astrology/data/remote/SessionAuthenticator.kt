package com.destinyai.astrology.data.remote

import com.destinyai.astrology.data.local.prefs.SessionTokenStore
import com.destinyai.astrology.data.local.prefs.UserPreferences
import com.google.gson.JsonParser
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import javax.inject.Provider

/** iOS APIClient 401 handling parity (NetworkClient.swift:99-124):
 *  - Refresh + retry ONCE, and ONLY when the 401 body says `session_expired`
 *    (SEC-2 — was refreshing on ANY 401, burning a refresh on authorization/ownership
 *    401s and on session_revoked which the server rejects).
 *  - On a re-auth signal (refresh_reused / refresh_expired / session_revoked / … , or a
 *    failed refresh), CLEAR the local session so the dead JWT stops being attached and the
 *    app routes to re-auth (SEC-1 — was swallowing the signal → refresh loop, user stranded).
 *  - Skip requests tagged with the delete-account marker header so a delete 401 surfaces
 *    session-expired instead of being auto-refreshed (SEC-3). */
class SessionAuthenticator(
    private val store: SessionTokenStore,
    private val exchangeClient: Provider<AuthExchangeClient>,
    private val prefs: UserPreferences,
) : Authenticator {
    override fun authenticate(route: Route?, response: Response): Request? {
        // Only retry once.
        if (responseCount(response) >= 2) return null
        // SEC-3: delete-account carries its own bearer and must surface session-expired
        // to the UI's re-auth flow, not be silently refreshed+retried.
        if (response.request.header(SKIP_REAUTH_HEADER) != null) return null

        val staleJwt = store.currentSessionJwt() ?: return null
        val sentBearer = response.request.header("Authorization")
        // Only act if the failing request actually used the session JWT.
        if (sentBearer != "Bearer $staleJwt") return null

        // Peek the 401 body's detail.code. A time-expired session JWT is
        // refreshable; anything in REAUTH_CODES is not. The server historically
        // returned `session_required` (generic) for an expired access token
        // instead of `session_expired`, so we accept BOTH as refresh triggers —
        // otherwise an expired token surfaces as session_required, the refresh
        // never fires, and the app loops on 401 ("network error"). A REAUTH
        // code (or an unreadable/other code) is still NOT refreshable.
        val code = parseDetailCode(response)
        if (code != null && code !in REFRESHABLE_CODES) {
            if (code in REAUTH_CODES) clearForReauth()
            return null
        }

        // Attempt the one refresh. On failure — including a thrown ReauthRequired or
        // AccountDeleted — clear the session so the interceptor falls back to the API key
        // and Splash/Auth re-routes. iOS parity (NetworkClient.swift:114-116): both
        // .reauthRequired and .accountDeleted cases call clearActiveSession().
        val newJwt = try {
            runBlocking { exchangeClient.get().refresh() }.sessionJwt
        } catch (e: AuthExchangeError.ReauthRequired) {
            clearForReauth()
            return null
        } catch (e: AuthExchangeError.AccountDeleted) {
            // GDPR erasure / account_archived: the JWT is permanently dead.
            // Clear so the interceptor stops attaching it and Splash routes to Auth.
            clearForReauth()
            return null
        } catch (e: Exception) {
            // Transient refresh failure (network): do NOT nuke the session — a later
            // request can retry. Just don't loop on this one.
            return null
        }

        return response.request.newBuilder()
            .header("Authorization", "Bearer $newJwt")
            .build()
    }

    private fun clearForReauth() {
        store.clearActiveSession()
        // IS_AUTHENTICATED is an independent DataStore flag — flip it so the warm-start
        // gate routes to Auth on next launch even though this runs off the main flow.
        runCatching { runBlocking { prefs.setAuthenticated(false) } }
    }

    /** Read the 401 body's `detail.code` without consuming the stream for the caller. */
    private fun parseDetailCode(response: Response): String? = runCatching {
        // peekBody doesn't consume the original response body.
        val body = response.peekBody(64 * 1024).string()
        val root = JsonParser.parseString(body)
        if (!root.isJsonObject) return null
        val detail = root.asJsonObject.get("detail")?.takeIf { it.isJsonObject }?.asJsonObject
            ?: return null
        detail.get("code")?.takeIf { it.isJsonPrimitive }?.asString
    }.getOrNull()

    private fun responseCount(response: Response): Int {
        var r: Response? = response
        var c = 1
        while (r?.priorResponse != null) {
            c++
            r = r.priorResponse
        }
        return c
    }

    private companion object {
        // Header set by the delete-account request so this authenticator skips it (SEC-3).
        const val SKIP_REAUTH_HEADER = "X-Skip-Reauth"
        // 401 detail.code values that mean "the access token expired, refresh it".
        // `session_required` is the generic code the server returns for an
        // expired JWT when identity resolves to None; `session_expired` is the
        // specific code (server now propagates it, but older builds/paths still
        // emit session_required — accept both).
        val REFRESHABLE_CODES = setOf("session_expired", "session_required")
        val REAUTH_CODES = setOf(
            "refresh_reused", "refresh_unknown", "refresh_expired",
            "session_revoked", "google_reattest_required",
        )
    }
}
