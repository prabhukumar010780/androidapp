package com.destinyai.astrology.data.remote

import com.google.gson.JsonParser
import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.IOException

/**
 * Typed exception carrying server-extracted message from FastAPI/AstroAPI error bodies.
 * Mirrors iOS NetworkError.serverError(message) parsing in NetworkClient.swift.
 *
 * Recognized server error shapes:
 *   - {"detail": "string"}                        (FastAPI plain)
 *   - {"detail": {"message": "..."}}              (FastAPI nested)
 *   - {"message": "string"}                       (custom)
 */
class ApiException(
    val statusCode: Int,
    val serverMessage: String,
) : IOException(serverMessage)

/**
 * iOS parity (NetworkClient.swift:215-256 quotaErrorIf403): a 403/429 quota rejection
 * from the request path, carrying enough detail for the ViewModel to route to the
 * QuotaExhausted sheet instead of a generic error banner.
 */
class QuotaException(
    val statusCode: Int,
    val reason: String?,
    val upgradeMessage: String?,
    val resetAt: String?,
    val planId: String?,
    val suggestedPlan: String?,
    val isFairUse: Boolean,
) : IOException(upgradeMessage ?: reason ?: "quota_exceeded")

/**
 * Parses 4xx/5xx response bodies into ApiException at the OkHttp layer so that any
 * Retrofit caller (which would otherwise see retrofit2.HttpException with an unread body)
 * receives a typed message containing the server's actual reason.
 *
 * Falls back to "Client Error: <code>" / "Server Error: <code>" / "Unknown Error: <code>"
 * when the body is unparseable, matching iOS behavior.
 */
class ErrorInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        if (response.isSuccessful) return response

        val code = response.code
        // Read body once, then republish so downstream callers can still read it.
        val peek = response.peekBody(MAX_PEEK_BYTES).string()

        // iOS parity (NetworkClient.swift:140-142, quotaErrorIf403): intercept 403/429
        // quota rejections BEFORE the generic 4xx path so the ViewModel can show the
        // paywall/interstitial instead of a red error banner.
        if (code == 403 || code == 429) {
            parseQuota(peek, code)?.let { throw it }
        }

        val parsed = parseMessage(peek)

        val fallback = when (code) {
            in 400..499 -> "Client Error: $code"
            in 500..599 -> "Server Error: $code"
            else -> "Unknown Error: $code"
        }
        val message = parsed ?: fallback
        throw ApiException(code, message)
    }

    private val quotaReasons = setOf(
        "quota_exceeded", "quota_exhausted", "rate_limited", "subscription_expired",
        "daily_limit_reached", "overall_limit_reached", "fair_use_violation",
        "upgrade_required", "feature_not_available",
    )

    private fun parseQuota(body: String, code: Int): QuotaException? {
        if (body.isBlank()) return null
        return try {
            val root = JsonParser.parseString(body)
            if (!root.isJsonObject) return null
            val detail = root.asJsonObject.get("detail")?.takeIf { it.isJsonObject }?.asJsonObject
                ?: root.asJsonObject
            fun str(key: String) = detail.get(key)?.takeIf { it.isJsonPrimitive }?.asString
            fun bool(key: String) = detail.get(key)?.takeIf { it.isJsonPrimitive }?.asBoolean
            val reason = str("reason") ?: str("code")
            val isQuota = reason in quotaReasons || code == 429
            if (!isQuota) return null
            val cta = detail.get("upgrade_cta")?.takeIf { it.isJsonObject }?.asJsonObject
            QuotaException(
                statusCode = code,
                reason = reason,
                upgradeMessage = cta?.get("message")?.takeIf { it.isJsonPrimitive }?.asString ?: str("message"),
                resetAt = str("reset_at"),
                planId = str("plan_id"),
                suggestedPlan = cta?.get("suggested_plan")?.takeIf { it.isJsonPrimitive }?.asString,
                isFairUse = bool("is_fair_use_violation") ?: (reason == "fair_use_violation"),
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun parseMessage(body: String): String? {
        if (body.isBlank()) return null
        return try {
            val root = JsonParser.parseString(body)
            if (!root.isJsonObject) return null
            val obj = root.asJsonObject
            // {"detail": "string"}
            obj.get("detail")?.takeIf { it.isJsonPrimitive }?.asString?.let { return it }
            // {"detail": {"message": "..."}}
            obj.get("detail")?.takeIf { it.isJsonObject }?.asJsonObject
                ?.get("message")?.takeIf { it.isJsonPrimitive }?.asString
                ?.let { return it }
            // {"message": "string"}
            obj.get("message")?.takeIf { it.isJsonPrimitive }?.asString?.let { return it }
            null
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        private const val MAX_PEEK_BYTES: Long = 64L * 1024L
    }
}
