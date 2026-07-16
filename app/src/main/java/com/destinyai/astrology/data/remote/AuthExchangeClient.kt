package com.destinyai.astrology.data.remote

import com.destinyai.astrology.data.local.prefs.SessionTokenStore
import retrofit2.HttpException
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

data class ExchangeResult(
    val userEmail: String,
    val sessionJwt: String,
    val sessionExpiresAtMs: Long,
    val refreshExpiresAtMs: Long,
)

/** iOS AuthExchangeClient parity — posts /auth/exchange + /auth/refresh and
 *  persists results into SessionTokenStore. */
@Singleton
class AuthExchangeClient @Inject constructor(
    private val api: AstroApiService,
    private val store: SessionTokenStore,
    @Named("apiKey") private val apiKey: String,
    @Named("userAgent") private val userAgent: String,
) {
    suspend fun signInWithApple(idToken: String, nonce: String?, deviceId: String? = null) =
        exchange(AuthExchangeRequest(idp = "apple", idToken = idToken, nonce = nonce, deviceId = deviceId))

    suspend fun signInWithGoogle(idToken: String, nonce: String?, deviceId: String? = null) =
        exchange(AuthExchangeRequest(idp = "google", idToken = idToken, nonce = nonce, deviceId = deviceId))

    suspend fun signInAsGuest(email: String, isGeneratedEmail: Boolean = true, userName: String? = null) =
        exchange(
            AuthExchangeRequest(
                idp = "guest",
                guestPayload = GuestExchangePayload(email, isGeneratedEmail, userName),
            )
        )

    private suspend fun exchange(body: AuthExchangeRequest): ExchangeResult {
        val resp = try {
            api.authExchange(apiKey, userAgent, body)
        } catch (e: HttpException) {
            throw AuthExchangeError.fromHttp(e.code(), e.response()?.errorBody()?.string())
        }
        val sessMs = parseIso8601Millis(resp.sessionJwtExpiresAt)
            ?: throw AuthExchangeError.Network("bad_timestamp")
        val refMs = parseIso8601Millis(resp.refreshTokenExpiresAt)
            ?: throw AuthExchangeError.Network("bad_timestamp")
        val ok = store.setActiveSession(resp.userEmail, resp.sessionJwt, sessMs, resp.refreshToken, refMs)
        if (!ok) throw AuthExchangeError.ReauthRequired("local_persist_failed")
        return ExchangeResult(resp.userEmail, resp.sessionJwt, sessMs, refMs)
    }

    suspend fun refresh(idToken: String? = null): ExchangeResult {
        val refreshToken = store.currentRefreshToken() ?: throw AuthExchangeError.NoRefreshToken
        val resp = try {
            api.authRefresh(apiKey, userAgent, AuthRefreshRequest(refreshToken = refreshToken, idToken = idToken))
        } catch (e: HttpException) {
            throw AuthExchangeError.fromHttp(e.code(), e.response()?.errorBody()?.string())
        }
        val sessMs = parseIso8601Millis(resp.sessionJwtExpiresAt)
            ?: throw AuthExchangeError.Network("bad_timestamp")
        val refMs = parseIso8601Millis(resp.refreshTokenExpiresAt)
            ?: throw AuthExchangeError.Network("bad_timestamp")
        val ok = store.updateSession(resp.sessionJwt, sessMs, resp.refreshToken, refMs)
        if (!ok) throw AuthExchangeError.ReauthRequired("local_persist_failed")
        return ExchangeResult(store.activeEmail() ?: "", resp.sessionJwt, sessMs, refMs)
    }
}
