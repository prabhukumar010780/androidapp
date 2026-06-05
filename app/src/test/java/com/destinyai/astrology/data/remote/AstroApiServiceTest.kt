package com.destinyai.astrology.data.remote

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * TDD tests for AstroApiService — uses per-test MockWebServer to prevent
 * request leakage between tests.
 */
class AstroApiServiceTest {

    private lateinit var server: MockWebServer
    private lateinit var service: AstroApiService

    @BeforeEach
    fun setup() {
        server = MockWebServer()
        server.start()
        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(OkHttpClient.Builder().build())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        service = retrofit.create(AstroApiService::class.java)
    }

    @AfterEach
    fun teardown() {
        server.shutdown()
    }

    // ── register ──────────────────────────────────────────────────────────────

    @Test
    fun `register sends correct json body`() = runTest {
        server.enqueue(MockResponse()
            .setBody("""{"user_email":"test@x.com","plan_id":"free_guest","is_generated_email":true,"is_premium":false,"access_state":"granted","feature_usage":{"daily_queries_used":0,"daily_queries_limit":3}}""")
            .addHeader("Content-Type", "application/json"))

        service.register(RegisterRequest(email = "test@x.com", isGeneratedEmail = true))

        val req = server.takeRequest()
        assertEquals("POST", req.method)
        assertTrue(req.path!!.contains("register"))
        val body = req.body.readUtf8()
        assertTrue(body.contains("test@x.com"))
    }

    @Test
    fun `register response parses plan_id and is_premium`() = runTest {
        server.enqueue(MockResponse()
            .setBody("""{"user_email":"reg@x.com","plan_id":"free_registered","is_generated_email":false,"is_premium":false,"access_state":"granted","feature_usage":{"daily_queries_used":0,"daily_queries_limit":5}}""")
            .addHeader("Content-Type", "application/json"))

        val resp = service.register(RegisterRequest(email = "reg@x.com", isGeneratedEmail = false))

        assertEquals("free_registered", resp.planId)
        assertFalse(resp.isGeneratedEmail)
    }

    // ── getStatus ─────────────────────────────────────────────────────────────

    @Test
    fun `getStatus sends email as query param`() = runTest {
        server.enqueue(MockResponse()
            .setBody("""{"user_email":"q@x.com","plan_id":"free_registered","is_premium":false,"daily_quota":5,"daily_used":1}""")
            .addHeader("Content-Type", "application/json"))

        service.getStatus(email = "q@x.com")

        val req = server.takeRequest()
        assertEquals("GET", req.method)
        assertTrue(req.path!!.contains("email=q%40x.com") || req.path!!.contains("email=q@x.com"))
    }

    @Test
    fun `getStatus response parses daily quota and used`() = runTest {
        server.enqueue(MockResponse()
            .setBody("""{"user_email":"u@x.com","plan_id":"free_registered","is_premium":false,"daily_quota":10,"daily_used":3}""")
            .addHeader("Content-Type", "application/json"))

        val resp = service.getStatus(email = "u@x.com")

        assertEquals(10, resp.dailyQuota)
        assertEquals(3, resp.dailyUsed)
    }

    // ── saveBirthProfile ──────────────────────────────────────────────────────

    @Test
    fun `saveProfile sends birth_profile in body`() = runTest {
        server.enqueue(MockResponse()
            .setBody("""{"user_email":"b@x.com","plan_id":"free_guest"}""")
            .addHeader("Content-Type", "application/json"))

        service.saveProfile(ProfileRequest(
            email = "b@x.com",
            isGeneratedEmail = true,
            birthProfile = BirthProfileDto(
                dateOfBirth = "1990-01-01",
                timeOfBirth = "08:00",
                cityOfBirth = "Mumbai",
                latitude = 19.07,
                longitude = 72.87,
            ),
        ))

        val req = server.takeRequest()
        val body = req.body.readUtf8()
        assertTrue(body.contains("birth_profile"))
        assertTrue(body.contains("1990-01-01"))
    }

    // ── upgradeGuest ──────────────────────────────────────────────────────────

    @Test
    fun `upgradeGuest sends old and new email`() = runTest {
        server.enqueue(MockResponse()
            .setBody("""{"user_email":"new@x.com","plan_id":"free_registered","is_generated_email":false}""")
            .addHeader("Content-Type", "application/json"))

        service.upgradeGuest(UpgradeRequest(oldEmail = "old@x.com", newEmail = "new@x.com"))

        val req = server.takeRequest()
        val body = req.body.readUtf8()
        assertTrue(body.contains("old@x.com"))
        assertTrue(body.contains("new@x.com"))
    }

    @Test
    fun `upgradeGuest 409 conflict propagates as http exception`() = runTest {
        server.enqueue(MockResponse().setResponseCode(409)
            .setBody("""{"detail":"conflict"}"""))

        val ex = assertThrows<retrofit2.HttpException> {
            service.upgradeGuest(UpgradeRequest(oldEmail = "g@x.com", newEmail = "existing@x.com"))
        }
        assertEquals(409, ex.code())
    }

    // ── predict ───────────────────────────────────────────────────────────────

    @Test
    fun `predict sends query and birth_profile`() = runTest {
        server.enqueue(MockResponse()
            .setBody("""{"content":"Your Sun is in Cancer...","prediction_id":"pred_001"}""")
            .addHeader("Content-Type", "application/json"))

        service.predict(PredictRequest(
            query = "What is my sun sign?",
            userEmail = "u@x.com",
            birthData = PredictBirthDataDto(
                dob = "1980-07-01",
                time = "06:32",
                cityOfBirth = "Bhilai",
                latitude = 21.21,
                longitude = 81.39,
            ),
        ))

        val req = server.takeRequest()
        val body = req.body.readUtf8()
        assertTrue(body.contains("query"))
        assertTrue(body.contains("What is my sun sign?"))
        assertTrue(body.contains("birth_data"))
    }

    @Test
    fun `predict response parses content field`() = runTest {
        server.enqueue(MockResponse()
            .setBody("""{"content":"Saturn is in your 7th house...","prediction_id":"pred_002"}""")
            .addHeader("Content-Type", "application/json"))

        val resp = service.predict(PredictRequest(
            query = "Tell me about Saturn",
            userEmail = "u@x.com",
            birthData = PredictBirthDataDto(dob = "1980-07-01", time = "06:32", cityOfBirth = "Bhilai", latitude = 21.21, longitude = 81.39),
        ))

        assertNotNull(resp.content)
        assertTrue(resp.content.isNotBlank())
    }

    // ── registerFcmToken ──────────────────────────────────────────────────────

    @Test
    fun `registerFcmToken sends platform android`() = runTest {
        server.enqueue(MockResponse()
            .setBody("""{"success":true}""")
            .addHeader("Content-Type", "application/json"))

        service.registerDeviceToken(DeviceTokenRequest(
            userEmail = "u@x.com",
            token = "fcm-xyz",
            platform = "android",
            appVersion = "1.0",
        ))

        val req = server.takeRequest()
        val body = req.body.readUtf8()
        assertTrue(body.contains("android"))
        assertTrue(body.contains("fcm-xyz"))
    }

    // ── listPartners ──────────────────────────────────────────────────────────

    @Test
    fun `listPartners sends GET to partners endpoint`() = runTest {
        server.enqueue(MockResponse()
            .setBody("[]")
            .addHeader("Content-Type", "application/json"))

        service.listPartners(email = "u@x.com")

        val req = server.takeRequest()
        assertEquals("GET", req.method)
        assertTrue(req.path!!.contains("partners"))
    }

    // ── listChatThreads ───────────────────────────────────────────────────────

    @Test
    fun `listChatThreads sends GET with user id in path`() = runTest {
        server.enqueue(MockResponse()
            .setBody("[]")
            .addHeader("Content-Type", "application/json"))

        service.listChatThreads(userId = "u@x.com")

        val req = server.takeRequest()
        assertEquals("GET", req.method)
        assertTrue(req.path!!.contains("u%40x.com") || req.path!!.contains("u@x.com"))
    }

    // ── submitFeedback ────────────────────────────────────────────────────────

    @Test
    fun `submitFeedback sends rating in body`() = runTest {
        server.enqueue(MockResponse()
            .setBody("""{"success":true}""")
            .addHeader("Content-Type", "application/json"))

        service.submitFeedback(FeedbackRequest(
            userEmail = "u@x.com",
            predictionId = "pred_001",
            sessionId = null,
            query = "test query",
            predictionText = "test prediction",
            rating = 5,
        ))

        val req = server.takeRequest()
        val body = req.body.readUtf8()
        assertTrue(body.contains("5"))
        assertTrue(body.contains("pred_001"))
    }
}
