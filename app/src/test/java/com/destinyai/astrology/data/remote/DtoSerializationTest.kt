package com.destinyai.astrology.data.remote

import com.google.gson.Gson
import com.google.gson.JsonParser
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

/**
 * Pure Gson round-trip tests for network DTOs.
 *
 * Guards against silent regressions in @SerializedName field mappings between
 * Android DTOs and the backend Pydantic schemas in
 * astrology_api/astroapi-v2/app/core/api/ (Python router files).
 *
 * If a backend field is renamed or a Kotlin field's @SerializedName drifts,
 * these tests fail loudly instead of producing nullable round-trips at runtime.
 */
class DtoSerializationTest {

    private val gson = Gson()

    // ── RegisterRequest ───────────────────────────────────────────────────────

    @Test
    fun `RegisterRequest serializes snake_case keys`() {
        val req = RegisterRequest(
            email = "user@x.com",
            isGeneratedEmail = true,
            googleId = "gid-1",
            appleId = null,
            name = "User",
        )
        val json = JsonParser.parseString(gson.toJson(req)).asJsonObject

        assertTrue(json.has("email"))
        assertTrue(json.has("is_generated_email"))
        assertTrue(json.has("google_id"))
        assertTrue(json.has("name"))
        assertFalse(json.has("isGeneratedEmail"), "must use snake_case for backend")
        assertEquals(true, json["is_generated_email"].asBoolean)
    }

    @Test
    fun `RegisterRequest round-trips`() {
        val original = RegisterRequest(
            email = "rt@x.com",
            isGeneratedEmail = false,
            googleId = "g123",
            appleId = "a456",
            name = "RT User",
        )
        val parsed = gson.fromJson(gson.toJson(original), RegisterRequest::class.java)
        assertEquals(original, parsed)
    }

    // ── BirthProfileDto ───────────────────────────────────────────────────────

    @Test
    fun `BirthProfileDto serializes all fields snake_case`() {
        val dto = BirthProfileDto(
            dateOfBirth = "1990-01-15",
            timeOfBirth = "08:30",
            cityOfBirth = "Mumbai",
            latitude = 19.0760,
            longitude = 72.8777,
            gender = "male",
            birthTimeUnknown = false,
            placeId = "ChIJ_abc",
        )
        val json = JsonParser.parseString(gson.toJson(dto)).asJsonObject

        assertEquals("1990-01-15", json["date_of_birth"].asString)
        assertEquals("08:30", json["time_of_birth"].asString)
        assertEquals("Mumbai", json["city_of_birth"].asString)
        assertEquals(19.0760, json["latitude"].asDouble, 0.0001)
        assertEquals(72.8777, json["longitude"].asDouble, 0.0001)
        assertEquals("male", json["gender"].asString)
        assertEquals(false, json["birth_time_unknown"].asBoolean)
        assertEquals("ChIJ_abc", json["place_id"].asString)
    }

    @Test
    fun `BirthProfileDto deserializes from backend snake_case payload`() {
        val backendJson = """
            {
              "date_of_birth": "1980-07-01",
              "time_of_birth": "06:32",
              "city_of_birth": "Bhilai",
              "latitude": 21.21,
              "longitude": 81.39,
              "gender": "male",
              "birth_time_unknown": false,
              "place_id": "ChIJxyz"
            }
        """.trimIndent()
        val dto = gson.fromJson(backendJson, BirthProfileDto::class.java)

        assertEquals("1980-07-01", dto.dateOfBirth)
        assertEquals("06:32", dto.timeOfBirth)
        assertEquals("Bhilai", dto.cityOfBirth)
        assertEquals(21.21, dto.latitude, 0.0001)
        assertEquals(81.39, dto.longitude, 0.0001)
        assertEquals("ChIJxyz", dto.placeId)
    }

    // ── ProfileRequest ────────────────────────────────────────────────────────

    @Test
    fun `ProfileRequest wraps birth_profile under correct key`() {
        val req = ProfileRequest(
            email = "p@x.com",
            userName = "P",
            isGeneratedEmail = false,
            birthProfile = BirthProfileDto(
                dateOfBirth = "1990-01-01",
                timeOfBirth = "08:00",
                cityOfBirth = "Delhi",
                latitude = 28.61,
                longitude = 77.20,
            ),
        )
        val json = JsonParser.parseString(gson.toJson(req)).asJsonObject

        assertTrue(json.has("birth_profile"))
        assertTrue(json.has("user_name"))
        assertTrue(json.has("user_type"))
        assertTrue(json.has("is_generated_email"))
        assertEquals("registered", json["user_type"].asString)
        val nested = json.getAsJsonObject("birth_profile")
        assertEquals("1990-01-01", nested["date_of_birth"].asString)
    }

    // ── PredictBirthDataDto ───────────────────────────────────────────────────

    @Test
    fun `PredictBirthDataDto uses dob and time keys not date_of_birth`() {
        val dto = PredictBirthDataDto(
            dob = "1980-07-01",
            time = "06:32",
            cityOfBirth = "Bhilai",
            latitude = 21.21,
            longitude = 81.39,
        )
        val json = JsonParser.parseString(gson.toJson(dto)).asJsonObject

        assertTrue(json.has("dob"), "predict endpoint expects 'dob' not 'date_of_birth'")
        assertTrue(json.has("time"))
        assertTrue(json.has("city_of_birth"))
        assertEquals("lahiri", json["ayanamsa"].asString)
        assertEquals("whole_sign", json["house_system"].asString)
    }

    // ── UpgradeRequest ────────────────────────────────────────────────────────

    @Test
    fun `UpgradeRequest serializes old_email and new_email`() {
        val req = UpgradeRequest(oldEmail = "guest@x.com", newEmail = "real@x.com")
        val json = JsonParser.parseString(gson.toJson(req)).asJsonObject

        assertEquals("guest@x.com", json["old_email"].asString)
        assertEquals("real@x.com", json["new_email"].asString)
        assertFalse(json.has("oldEmail"))
    }

    // ── DeviceTokenRequest ────────────────────────────────────────────────────

    @Test
    fun `DeviceTokenRequest serializes platform and user_email`() {
        val req = DeviceTokenRequest(
            userEmail = "u@x.com",
            token = "fcm-token-abc",
            platform = "android",
            appVersion = "1.0",
        )
        val json = JsonParser.parseString(gson.toJson(req)).asJsonObject

        assertEquals("u@x.com", json["user_email"].asString)
        assertEquals("android", json["platform"].asString)
        assertEquals("fcm-token-abc", json["token"].asString)
        assertEquals("1.0", json["app_version"].asString)
    }

    // ── FeedbackRequest ───────────────────────────────────────────────────────

    @Test
    fun `FeedbackRequest serializes prediction_id and rating`() {
        val req = FeedbackRequest(
            userEmail = "u@x.com",
            predictionId = "pred-001",
            sessionId = "sess-1",
            query = "q",
            predictionText = "p",
            rating = 5,
        )
        val json = JsonParser.parseString(gson.toJson(req)).asJsonObject

        assertEquals("u@x.com", json["user_email"].asString)
        assertEquals("pred-001", json["prediction_id"].asString)
        assertEquals(5, json["rating"].asInt)
    }

    // ── StatusResponse ────────────────────────────────────────────────────────

    @Test
    fun `StatusResponse parses backend payload`() {
        val backendJson = """
            {
              "user_email": "u@x.com",
              "plan_id": "free_registered",
              "is_premium": false,
              "daily_quota": 5,
              "daily_used": 2
            }
        """.trimIndent()
        val resp = gson.fromJson(backendJson, StatusResponse::class.java)

        assertEquals("u@x.com", resp.userEmail)
        assertEquals("free_registered", resp.planId)
        assertEquals(5, resp.dailyQuota)
        assertEquals(2, resp.dailyUsed)
        assertFalse(resp.isPremium)
    }

    // ── PredictRequest ────────────────────────────────────────────────────────

    @Test
    fun `PredictRequest serializes query and birth_data nested`() {
        val req = PredictRequest(
            query = "When will I marry?",
            userEmail = "u@x.com",
            birthData = PredictBirthDataDto(
                dob = "1990-01-01",
                time = "08:00",
                cityOfBirth = "Mumbai",
                latitude = 19.07,
                longitude = 72.87,
            ),
        )
        val json = JsonParser.parseString(gson.toJson(req)).asJsonObject

        assertEquals("When will I marry?", json["query"].asString)
        assertEquals("u@x.com", json["user_email"].asString)
        assertTrue(json.has("birth_data"))
        val bd = json.getAsJsonObject("birth_data")
        assertEquals("1990-01-01", bd["dob"].asString)
    }
}
