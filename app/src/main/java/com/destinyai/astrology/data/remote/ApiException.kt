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
        val parsed = parseMessage(peek)

        val fallback = when (code) {
            in 400..499 -> "Client Error: $code"
            in 500..599 -> "Server Error: $code"
            else -> "Unknown Error: $code"
        }
        val message = parsed ?: fallback
        throw ApiException(code, message)
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
