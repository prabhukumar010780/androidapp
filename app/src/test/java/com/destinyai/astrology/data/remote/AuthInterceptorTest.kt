package com.destinyai.astrology.data.remote

import com.destinyai.astrology.data.local.prefs.SessionTokenStore
import io.mockk.every
import io.mockk.mockk
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class AuthInterceptorTest {

    private lateinit var server: MockWebServer
    private lateinit var store: SessionTokenStore

    @BeforeEach
    fun setup() {
        server = MockWebServer()
        server.start()
        store = mockk()
    }

    @AfterEach
    fun teardown() {
        server.shutdown()
    }

    private fun clientWith(): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(store, apiKey = "API_KEY", userAgent = "DestinyAI-Android/1.0"))
            .build()

    @Test
    fun `fresh session attaches session jwt bearer`() {
        every { store.currentSessionJwt() } returns "SESS_JWT"
        server.enqueue(MockResponse().setBody("{}"))

        clientWith().newCall(Request.Builder().url(server.url("/x")).build()).execute().close()
        val recorded = server.takeRequest()
        assertEquals("Bearer SESS_JWT", recorded.getHeader("Authorization"))
        assertEquals("API_KEY", recorded.getHeader("X-API-Key"))
        assertEquals("DestinyAI-Android/1.0", recorded.getHeader("User-Agent"))
    }

    @Test
    fun `stale but present session still attaches jwt (Authenticator refreshes on 401)`() {
        // SECURITY: never silently downgrade an authenticated user to API-key scope
        // while a session exists — attach the (stale) JWT and let the server 401 +
        // Authenticator refresh.
        every { store.currentSessionJwt() } returns "STALE_JWT"
        server.enqueue(MockResponse().setBody("{}"))

        clientWith().newCall(Request.Builder().url(server.url("/x")).build()).execute().close()
        val recorded = server.takeRequest()
        assertEquals("Bearer STALE_JWT", recorded.getHeader("Authorization"))
    }

    @Test
    fun `no session at all falls back to api key bearer`() {
        every { store.currentSessionJwt() } returns null
        server.enqueue(MockResponse().setBody("{}"))

        clientWith().newCall(Request.Builder().url(server.url("/x")).build()).execute().close()
        val recorded = server.takeRequest()
        assertEquals("Bearer API_KEY", recorded.getHeader("Authorization"))
    }

    @Test
    fun `explicit Authorization header is not overridden`() {
        every { store.currentSessionJwt() } returns "SESS_JWT"
        server.enqueue(MockResponse().setBody("{}"))

        clientWith().newCall(
            Request.Builder().url(server.url("/x")).header("Authorization", "Bearer EXPLICIT").build()
        ).execute().close()
        val recorded = server.takeRequest()
        assertEquals("Bearer EXPLICIT", recorded.getHeader("Authorization"))
    }
}
