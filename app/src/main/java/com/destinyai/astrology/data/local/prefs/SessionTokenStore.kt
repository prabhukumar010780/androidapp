package com.destinyai.astrology.data.local.prefs

import javax.inject.Inject
import javax.inject.Singleton

/**
 * W7 parity (iOS SessionTokenStore.swift): encrypted at-rest store for the
 * user's session JWT + refresh token + expiries, scoped per email so signing
 * out of one account never leaks tokens into another. Active email tracked in
 * SecureStorage so clearAll() wipes it too.
 *
 * Expiries stored as epoch-millis Long strings. 60s freshness margin mirrors iOS.
 */
@Singleton
class SessionTokenStore @Inject constructor(
    private val secure: SecureStorage,
) {
    fun activeEmail(): String? = secure.getRaw(ACTIVE_EMAIL_KEY)

    fun setActiveSession(
        email: String,
        sessionJwt: String,
        sessionExpiresAtMs: Long,
        refreshToken: String,
        refreshExpiresAtMs: Long,
    ): Boolean {
        val e = email.lowercase()
        return try {
            secure.putRaw(sessionJwtKey(e), sessionJwt)
            secure.putRaw(refreshTokenKey(e), refreshToken)
            secure.putRaw(sessionExpiryKey(e), sessionExpiresAtMs.toString())
            secure.putRaw(refreshExpiryKey(e), refreshExpiresAtMs.toString())
            secure.putRaw(ACTIVE_EMAIL_KEY, e)
            true
        } catch (t: Throwable) {
            secure.removeRaw(sessionJwtKey(e))
            secure.removeRaw(refreshTokenKey(e))
            secure.removeRaw(sessionExpiryKey(e))
            secure.removeRaw(refreshExpiryKey(e))
            false
        }
    }

    fun updateSession(
        sessionJwt: String,
        sessionExpiresAtMs: Long,
        refreshToken: String,
        refreshExpiresAtMs: Long,
    ): Boolean {
        val e = activeEmail() ?: return false
        return try {
            secure.putRaw(sessionJwtKey(e), sessionJwt)
            secure.putRaw(refreshTokenKey(e), refreshToken)
            secure.putRaw(sessionExpiryKey(e), sessionExpiresAtMs.toString())
            secure.putRaw(refreshExpiryKey(e), refreshExpiresAtMs.toString())
            true
        } catch (t: Throwable) {
            clearActiveSession()
            false
        }
    }

    fun currentSessionJwt(): String? =
        activeEmail()?.let { secure.getRaw(sessionJwtKey(it)) }

    fun currentRefreshToken(): String? =
        activeEmail()?.let { secure.getRaw(refreshTokenKey(it)) }

    fun sessionJwt(forEmail: String): String? =
        secure.getRaw(sessionJwtKey(forEmail.lowercase()))

    fun sessionIsFresh(nowMs: Long = System.currentTimeMillis()): Boolean {
        val e = activeEmail() ?: return false
        val raw = secure.getRaw(sessionExpiryKey(e)) ?: return false
        val expiry = raw.toLongOrNull() ?: return false
        return expiry - nowMs > 60_000
    }

    fun clearSession(forEmail: String) {
        val e = forEmail.lowercase()
        secure.removeRaw(sessionJwtKey(e))
        secure.removeRaw(refreshTokenKey(e))
        secure.removeRaw(sessionExpiryKey(e))
        secure.removeRaw(refreshExpiryKey(e))
        if (secure.getRaw(ACTIVE_EMAIL_KEY)?.lowercase() == e) {
            secure.removeRaw(ACTIVE_EMAIL_KEY)
        }
    }

    fun clearActiveSession() {
        val e = activeEmail()
        if (e != null) clearSession(e) else secure.removeRaw(ACTIVE_EMAIL_KEY)
    }

    private companion object {
        const val ACTIVE_EMAIL_KEY = "w7_current_session_email"
        fun sessionJwtKey(email: String) = "w7_session_jwt::$email"
        fun refreshTokenKey(email: String) = "w7_refresh_token::$email"
        fun sessionExpiryKey(email: String) = "w7_session_expires::$email"
        fun refreshExpiryKey(email: String) = "w7_refresh_expires::$email"
    }
}
