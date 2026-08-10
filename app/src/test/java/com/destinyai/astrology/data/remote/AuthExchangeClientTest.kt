package com.destinyai.astrology.data.remote

import com.destinyai.astrology.data.local.prefs.SessionTokenStore
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class AuthExchangeClientTest {

    private lateinit var api: AstroApiService
    private lateinit var store: SessionTokenStore
    private lateinit var client: AuthExchangeClient

    @BeforeEach
    fun setup() {
        api = mockk()
        store = mockk(relaxed = true)
        client = AuthExchangeClient(api, store, apiKey = "k", userAgent = "DestinyAI-Android/1.0")
    }

    private fun httpError(status: Int, body: String) = retrofit2.HttpException(
        okhttp3.ResponseBody.create(null, body).let { retrofit2.Response.error<Any>(status, it) }
    )

    @Test
    fun `guest exchange persists session and returns result`() = runTest {
        val exp = "2030-01-01T00:00:00Z"
        coEvery { api.authExchange(any(), any(), any()) } returns AuthExchangeResponse(
            sessionJwt = "jwt", sessionJwtExpiresAt = exp,
            refreshToken = "ref", refreshTokenExpiresAt = exp, userEmail = "g@x.com",
        )
        every { store.setActiveSession(any(), any(), any(), any(), any()) } returns true

        val r = client.signInAsGuest(email = "g@x.com")
        assertEquals("g@x.com", r.userEmail)
        assertEquals("jwt", r.sessionJwt)
    }

    @Test
    fun `apple exchange maps 409 collision`() = runTest {
        val body = """{"detail":{"code":"cross_idp_collision","bound_idp":"google"}}"""
        coEvery { api.authExchange(any(), any(), any()) } throws httpError(409, body)
        val thrown = runCatching { client.signInWithApple(idToken = "t", nonce = null) }.exceptionOrNull()
        assertTrue(thrown is AuthExchangeError.CrossIdpCollision)
    }

    @Test
    fun `fromHttp maps account_deleted to AccountDeleted`() {
        val body = """{"detail":{"code":"account_deleted"}}"""
        val err = AuthExchangeError.fromHttp(403, body)
        assertTrue(err is AuthExchangeError.AccountDeleted)
        assertEquals("account_deleted", (err as AuthExchangeError.AccountDeleted).code)
    }

    @Test
    fun `fromHttp maps account_archived to AccountDeleted`() {
        val body = """{"detail":{"code":"account_archived"}}"""
        val err = AuthExchangeError.fromHttp(403, body)
        assertTrue(err is AuthExchangeError.AccountDeleted)
        assertEquals("account_archived", (err as AuthExchangeError.AccountDeleted).code)
    }

    @Test
    fun `account_deleted is not mapped to ReauthRequired`() {
        // Regression: account_deleted must NOT fall into the reauth set (which would be
        // swallowed as a generic reauth signal and miss the dedicated AccountDeleted path
        // in SessionAuthenticator + SplashViewModel).
        val body = """{"detail":{"code":"account_deleted"}}"""
        val err = AuthExchangeError.fromHttp(403, body)
        assertFalse(err is AuthExchangeError.ReauthRequired)
    }

    @Test
    fun `unparseable expiry fails the sign-in`() = runTest {
        coEvery { api.authExchange(any(), any(), any()) } returns AuthExchangeResponse(
            sessionJwt = "jwt", sessionJwtExpiresAt = "garbage",
            refreshToken = "ref", refreshTokenExpiresAt = "garbage", userEmail = "g@x.com",
        )
        val thrown = runCatching { client.signInAsGuest(email = "g@x.com") }.exceptionOrNull()
        assertTrue(thrown is AuthExchangeError.Network)
    }

    @Test
    fun `refresh without stored token throws`() = runTest {
        every { store.currentRefreshToken() } returns null
        val thrown = runCatching { client.refresh() }.exceptionOrNull()
        assertTrue(thrown is AuthExchangeError.NoRefreshToken)
    }
}
