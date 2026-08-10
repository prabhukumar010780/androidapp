package com.destinyai.astrology.data.remote

import com.destinyai.astrology.data.local.prefs.SessionTokenStore
import com.destinyai.astrology.data.local.prefs.UserPreferences
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import javax.inject.Provider

/**
 * Pins the auto-refresh trigger contract for SessionAuthenticator.
 *
 * Bug (2026-08): the server returns detail.code "session_required" for a
 * time-expired access token, but the Authenticator only refreshed on
 * "session_expired" — so an expired token looped on 401 ("network error")
 * and the refresh never fired. Fix: both "session_required" and
 * "session_expired" are refresh triggers; REAUTH codes still are not.
 */
class SessionAuthenticatorTest {

    private lateinit var server: MockWebServer
    private lateinit var store: SessionTokenStore
    private lateinit var exchange: AuthExchangeClient
    private lateinit var prefs: UserPreferences

    private val staleJwt = "STALE_JWT"
    private val freshJwt = "FRESH_JWT"

    @BeforeEach
    fun setup() {
        server = MockWebServer()
        server.start()
        store = mockk(relaxed = true)
        exchange = mockk(relaxed = true)
        prefs = mockk(relaxed = true)
        every { store.currentSessionJwt() } returns staleJwt
    }

    @AfterEach
    fun teardown() {
        server.shutdown()
    }

    private fun body(code: String) = """{"detail":{"code":"$code"}}"""

    private fun clientWith(): OkHttpClient =
        OkHttpClient.Builder()
            .authenticator(SessionAuthenticator(store, Provider { exchange }, prefs))
            .build()

    /** Enqueue a 401 with the given code, then a 200 for the retried request. */
    private fun enqueue401Then200(code: String) {
        server.enqueue(MockResponse().setResponseCode(401).setBody(body(code)))
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
    }

    private fun callWithStaleBearer() =
        clientWith().newCall(
            Request.Builder()
                .url(server.url("/x"))
                .header("Authorization", "Bearer $staleJwt")
                .build()
        ).execute()

    @Test
    fun `refreshes and retries on session_required (the bug)`() {
        every { runBlocking { exchange.refresh() } } returns
            ExchangeResult("u@x.com", freshJwt, 0L, 0L)
        enqueue401Then200("session_required")

        val resp = callWithStaleBearer()
        assertEquals(200, resp.code)
        resp.close()

        verify { runBlocking { exchange.refresh() } }
        // Retried request carried the refreshed bearer.
        server.takeRequest() // original 401
        val retried = server.takeRequest()
        assertEquals("Bearer $freshJwt", retried.getHeader("Authorization"))
    }

    @Test
    fun `refreshes and retries on session_expired`() {
        every { runBlocking { exchange.refresh() } } returns
            ExchangeResult("u@x.com", freshJwt, 0L, 0L)
        enqueue401Then200("session_expired")

        val resp = callWithStaleBearer()
        assertEquals(200, resp.code)
        resp.close()

        verify { runBlocking { exchange.refresh() } }
    }

    @Test
    fun `does NOT refresh on a REAUTH code, clears session instead`() {
        server.enqueue(MockResponse().setResponseCode(401).setBody(body("session_revoked")))

        val resp = callWithStaleBearer()
        assertEquals(401, resp.code) // no retry
        resp.close()

        verify(exactly = 0) { runBlocking { exchange.refresh() } }
        verify { store.clearActiveSession() }
    }

    @Test
    fun `account_deleted from refresh clears session and does not retry`() {
        // iOS parity (NetworkClient.swift:114-116): .accountDeleted case also clears
        // the stale local session so the dead JWT stops being attached.
        val accountDeletedBody = """{"detail":{"code":"account_deleted"}}"""
        // The 401 triggers the refresh path; mock the refresh to throw AccountDeleted.
        every { runBlocking { exchange.refresh() } } throws
            AuthExchangeError.AccountDeleted("account_deleted")
        server.enqueue(MockResponse().setResponseCode(401).setBody(body("session_expired")))

        val resp = callWithStaleBearer()
        assertEquals(401, resp.code) // null returned → OkHttp surfaces the original 401
        resp.close()

        verify { runBlocking { exchange.refresh() } }
        verify { store.clearActiveSession() }
    }

    @Test
    fun `account_archived from refresh clears session and does not retry`() {
        every { runBlocking { exchange.refresh() } } throws
            AuthExchangeError.AccountDeleted("account_archived")
        server.enqueue(MockResponse().setResponseCode(401).setBody(body("session_expired")))

        val resp = callWithStaleBearer()
        assertEquals(401, resp.code)
        resp.close()

        verify { store.clearActiveSession() }
    }

    @Test
    fun `does NOT refresh when the failing request did not carry the session jwt`() {
        // e.g. an API-key-only request that 401s — not our session to refresh.
        server.enqueue(MockResponse().setResponseCode(401).setBody(body("session_required")))

        val resp = clientWith().newCall(
            Request.Builder()
                .url(server.url("/x"))
                .header("Authorization", "Bearer API_KEY")
                .build()
        ).execute()
        assertEquals(401, resp.code)
        resp.close()

        verify(exactly = 0) { runBlocking { exchange.refresh() } }
    }
}
