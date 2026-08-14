package com.destinyai.astrology.data.remote

import com.google.gson.JsonParser
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset

/** iOS parity: AuthExchangeError enum + ISO8601 robust parse. */
sealed class AuthExchangeError(message: String) : Exception(message) {
    data class IdpRejected(val code: String, val status: Int) :
        AuthExchangeError("Sign-in rejected (code=$code, http=$status)")
    data class ReauthRequired(val code: String) :
        AuthExchangeError("Please sign in again ($code)")
    data class CrossIdpCollision(
        val boundIdp: String?, val attemptedIdp: String?, val userEmail: String?,
    ) : AuthExchangeError("This email is registered with a different sign-in method.")
    /**
     * iOS parity (AuthExchangeClient.swift:181-185): the GDPR soft-delete /
     * account_archived signal returned by /auth/exchange or /auth/refresh.
     * Distinct from ReauthRequired so SessionAuthenticator can clear the stale
     * local session (matching iOS NetworkClient.swift:114-116 .accountDeleted case)
     * while still returning null so the dead JWT is never re-attached.
     */
    data class AccountDeleted(val code: String) :
        AuthExchangeError("Account deleted or archived ($code)")
    data class Network(val msg: String) : AuthExchangeError("Network error: $msg")
    object NoRefreshToken : AuthExchangeError("No refresh token stored")

    companion object {
        private val REAUTH_CODES = setOf(
            "refresh_reused", "refresh_unknown", "refresh_expired",
            "session_revoked", "google_reattest_required",
        )
        // iOS parity (AuthExchangeClient.swift:181-185): account_deleted and
        // account_archived from /auth/exchange or /auth/refresh — the JWT is
        // dead and the session must be cleared, matching .accountDeleted in iOS.
        private val ACCOUNT_DELETED_CODES = setOf("account_deleted", "account_archived")

        fun fromHttp(status: Int, errorBody: String?): AuthExchangeError {
            val detail = runCatching {
                val root = JsonParser.parseString(errorBody.orEmpty())
                if (!root.isJsonObject) return@runCatching null
                root.asJsonObject.get("detail")?.takeIf { it.isJsonObject }?.asJsonObject
            }.getOrNull() ?: return Network("status $status")

            val code = detail.get("code")?.takeIf { it.isJsonPrimitive }?.asString
                ?: return Network("status $status")
            return when {
                code == "cross_idp_collision" -> CrossIdpCollision(
                    boundIdp = detail.get("bound_idp")?.takeIf { it.isJsonPrimitive }?.asString,
                    attemptedIdp = detail.get("attempted_idp")?.takeIf { it.isJsonPrimitive }?.asString,
                    userEmail = detail.get("user_email")?.takeIf { it.isJsonPrimitive }?.asString,
                )
                code in ACCOUNT_DELETED_CODES -> AccountDeleted(code)
                code in REAUTH_CODES -> ReauthRequired(code)
                else -> IdpRejected(code, status)
            }
        }
    }
}

/**
 * Parse the backend's ISO8601 timestamp to epoch-millis. Backend emits an
 * explicit "Z" but older/naive rows may omit it. Returns null on unparseable
 * so the caller FAILS the sign-in instead of defaulting to epoch-0 (which would
 * make sessionIsFresh() always false — the exact iOS 1970 bug that bricked W7).
 */
fun parseIso8601Millis(s: String): Long? {
    val t = s.trim()
    // java.time parsers accept fractional seconds of ANY precision (millis,
    // micros, nanos). The previous SimpleDateFormat approach read ".SSSSSS"
    // microseconds as raw milliseconds, skewing expiry by up to ~16 min.
    // Offset-aware first (handles trailing "Z" and "+hh:mm").
    runCatching { return OffsetDateTime.parse(t).toInstant().toEpochMilli() }
    // Naive timestamp (no zone) → treat as UTC, matching the backend.
    runCatching { return LocalDateTime.parse(t).toInstant(ZoneOffset.UTC).toEpochMilli() }
    return null
}
