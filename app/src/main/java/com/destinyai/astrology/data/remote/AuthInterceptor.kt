package com.destinyai.astrology.data.remote

import com.destinyai.astrology.data.local.prefs.SessionTokenStore
import okhttp3.Interceptor
import okhttp3.Response

/** iOS NetworkClient.authBearer() parity: attach the session JWT when fresh,
 *  else fall back to the bundled API key. Always send X-API-Key + User-Agent.
 *  A request that already carries an Authorization header (e.g. delete-account's
 *  explicit session bearer) is left untouched. */
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
            val bearer = if (store.sessionIsFresh() && store.currentSessionJwt() != null) {
                store.currentSessionJwt()
            } else {
                apiKey
            }
            if (!bearer.isNullOrBlank()) builder.header("Authorization", "Bearer $bearer")
        }
        return chain.proceed(builder.build())
    }
}
