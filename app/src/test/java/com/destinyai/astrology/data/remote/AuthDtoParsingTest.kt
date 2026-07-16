package com.destinyai.astrology.data.remote

import com.google.gson.Gson
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AuthDtoParsingTest {

    private val gson = Gson()

    @Test
    fun `exchange response parses snake_case wire fields`() {
        val json = """
            {"session_jwt":"jwt","session_jwt_expires_at":"2026-06-17T05:17:03.198915Z",
             "refresh_token":"ref","refresh_token_expires_at":"2026-06-24T05:17:03Z",
             "user_email":"a@x.com"}
        """.trimIndent()
        val r = gson.fromJson(json, AuthExchangeResponse::class.java)
        assertEquals("jwt", r.sessionJwt)
        assertEquals("a@x.com", r.userEmail)
        assertNotNull(parseIso8601Millis(r.sessionJwtExpiresAt))
    }

    @Test
    fun `parseIso8601Millis handles Z fractional and naive`() {
        assertNotNull(parseIso8601Millis("2026-06-17T05:17:03.198915Z"))
        assertNotNull(parseIso8601Millis("2026-06-17T05:17:03Z"))
        assertNotNull(parseIso8601Millis("2026-06-17T05:17:03.198915")) // naive -> +Z
        assertNull(parseIso8601Millis("garbage"))
    }

    @Test
    fun `fromHttp maps cross_idp_collision with bound_idp`() {
        val body = """{"detail":{"code":"cross_idp_collision","bound_idp":"google","attempted_idp":"apple","user_email":"a@x.com"}}"""
        val e = AuthExchangeError.fromHttp(409, body)
        assertTrue(e is AuthExchangeError.CrossIdpCollision)
        assertEquals("google", (e as AuthExchangeError.CrossIdpCollision).boundIdp)
    }

    @Test
    fun `fromHttp maps reauth family`() {
        val body = """{"detail":{"code":"refresh_reused"}}"""
        val e = AuthExchangeError.fromHttp(401, body)
        assertTrue(e is AuthExchangeError.ReauthRequired)
    }

    @Test
    fun `fromHttp falls back to IdpRejected`() {
        val body = """{"detail":{"code":"apple_invalid"}}"""
        val e = AuthExchangeError.fromHttp(401, body)
        assertTrue(e is AuthExchangeError.IdpRejected)
    }
}
