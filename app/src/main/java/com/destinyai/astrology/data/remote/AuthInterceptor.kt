package com.destinyai.astrology.data.remote

import com.destinyai.astrology.data.local.prefs.SessionTokenStore
import okhttp3.Interceptor
import okhttp3.Response

/** iOS NetworkClient.authBearer() parity: attach the user's session JWT when a
 *  session exists; fall back to the bundled API key ONLY when there is no session
 *  at all (unauthenticated bootstrap: onboarding, guest-before-mint, /auth/exchange).
 *  Always send X-API-Key + User-Agent. A request that already carries an
 *  Authorization header (e.g. delete-account's explicit session bearer) is untouched.
 *
 *  SECURITY (fail-safe, not fail-open): when a session JWT exists but is stale, we
 *  still attach it rather than silently downgrading to the API key. The server 401s
 *  and SessionAuthenticator refreshes-and-retries once. This guarantees an
 *  authenticated user is never silently downgraded to bundled-API-key scope while a
 *  refresh is still possible. */
class AuthInterceptor(
    private val store: SessionTokenStore,
    private val apiKey: String,
    private val userAgent: String,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val builder = original.newBuilder()
            .header("X-API-Key", apiKey)
            .header("User-Agent", userAgent)

        if (original.header("Authorization") == null) {
            // Prefer the session JWT whenever one exists (fresh OR stale — the
            // Authenticator refreshes on 401). Only when there is NO session at all
            // do we use the API key for unauthenticated bootstrap endpoints.
            val sessionJwt = store.currentSessionJwt()
            val bearer = sessionJwt ?: apiKey
            if (!bearer.isNullOrBlank()) builder.header("Authorization", "Bearer $bearer")
        }
        return chain.proceed(builder.build())
    }
}
