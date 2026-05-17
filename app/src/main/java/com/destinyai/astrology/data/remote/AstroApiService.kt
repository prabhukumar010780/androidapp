package com.destinyai.astrology.data.remote

import com.google.gson.annotations.SerializedName
import retrofit2.http.*

// ── Request DTOs ──────────────────────────────────────────────────────────────

data class RegisterRequest(
    @SerializedName("email") val email: String,
    @SerializedName("is_generated_email") val isGeneratedEmail: Boolean,
    @SerializedName("google_id") val googleId: String? = null,
    @SerializedName("name") val name: String? = null,
)

data class GoogleSignInRequest(
    @SerializedName("id_token") val idToken: String,
    @SerializedName("email") val email: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("google_id") val googleId: String? = null,
)

data class UpgradeRequest(
    @SerializedName("old_email") val oldEmail: String,
    @SerializedName("new_email") val newEmail: String,
)

data class ProfileRequest(
    @SerializedName("email") val email: String,
    @SerializedName("user_name") val userName: String? = null,
    @SerializedName("user_type") val userType: String = "registered",
    @SerializedName("is_generated_email") val isGeneratedEmail: Boolean,
    @SerializedName("birth_profile") val birthProfile: BirthProfileDto,
)

data class BirthProfileDto(
    @SerializedName("date_of_birth") val dateOfBirth: String,
    @SerializedName("time_of_birth") val timeOfBirth: String,
    @SerializedName("city_of_birth") val cityOfBirth: String,
    @SerializedName("latitude") val latitude: Double,
    @SerializedName("longitude") val longitude: Double,
    @SerializedName("gender") val gender: String? = null,
    @SerializedName("birth_time_unknown") val birthTimeUnknown: Boolean = false,
)

// Predict endpoint uses different field names than the subscription/profile endpoint
data class PredictBirthDataDto(
    @SerializedName("dob") val dateOfBirth: String,
    @SerializedName("time") val timeOfBirth: String,
    @SerializedName("city") val cityOfBirth: String,
    @SerializedName("latitude") val latitude: Double,
    @SerializedName("longitude") val longitude: Double,
)

data class PredictRequest(
    @SerializedName("query") val query: String,
    @SerializedName("user_id") val userId: String,
    @SerializedName("birth_data") val birthData: PredictBirthDataDto,
    @SerializedName("session_id") val sessionId: String? = null,
    @SerializedName("domain") val domain: String? = null,
)

data class DeviceTokenRequest(
    @SerializedName("user_email") val userEmail: String,
    @SerializedName("token") val token: String,
    @SerializedName("platform") val platform: String,
    @SerializedName("app_version") val appVersion: String,
)

data class FeedbackRequest(
    @SerializedName("user_email") val userEmail: String,
    @SerializedName("prediction_id") val predictionId: String,
    @SerializedName("rating") val rating: Int,
    @SerializedName("comment") val comment: String? = null,
)

data class PartnerRequest(
    @SerializedName("name") val name: String,
    @SerializedName("date_of_birth") val dateOfBirth: String,
    @SerializedName("time_of_birth") val timeOfBirth: String,
    @SerializedName("city_of_birth") val cityOfBirth: String,
    @SerializedName("latitude") val latitude: Double,
    @SerializedName("longitude") val longitude: Double,
)

data class CreatePartnerRequest(
    @SerializedName("user_email") val userEmail: String,
    @SerializedName("profile") val profile: PartnerRequest,
    @SerializedName("consent_given") val consentGiven: Boolean = true,
)

data class CompatibilityPersonDto(
    @SerializedName("email") val email: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("birth_profile") val birthProfile: BirthProfileDto,
)

data class CompatibilityRequest(
    @SerializedName("person_a") val personA: CompatibilityPersonDto,
    @SerializedName("person_b") val personB: CompatibilityPersonDto,
    @SerializedName("session_id") val sessionId: String? = null,
)

data class CompatibilityResponse(
    @SerializedName("score") val score: Int? = null,
    @SerializedName("content") val content: String? = null,
    @SerializedName("analysis") val analysis: String? = null,
    @SerializedName("prediction") val prediction: String? = null,
) {
    val text: String get() = content ?: analysis ?: prediction ?: ""
}

data class NotificationListResponse(
    @SerializedName("notifications") val notifications: List<NotificationDto> = emptyList(),
    @SerializedName("total") val total: Int = 0,
)

data class NotificationPrefsRequest(
    @SerializedName("daily_insight") val dailyInsight: Boolean = true,
    @SerializedName("transits") val transits: Boolean = true,
    @SerializedName("compatibility") val compatibility: Boolean = true,
)

data class ReadAllRequest(
    @SerializedName("user_email") val userEmail: String,
)

// ── Response DTOs ─────────────────────────────────────────────────────────────

data class RegisterResponse(
    @SerializedName("user_email") val userEmail: String,
    @SerializedName("plan_id") val planId: String,
    @SerializedName("is_generated_email") val isGeneratedEmail: Boolean,
    @SerializedName("is_premium") val isPremium: Boolean,
    @SerializedName("access_state") val accessState: String,
    @SerializedName("daily_quota") val dailyQuota: Int = 3,
    @SerializedName("daily_used") val dailyUsed: Int = 0,
    @SerializedName("name") val name: String? = null,
    @SerializedName("google_id") val googleId: String? = null,
)

data class StatusResponse(
    @SerializedName("user_email") val userEmail: String,
    @SerializedName("plan_id") val planId: String,
    @SerializedName("is_generated_email") val isGeneratedEmail: Boolean = false,
    @SerializedName("is_premium") val isPremium: Boolean,
    @SerializedName("daily_quota") val dailyQuota: Int = 3,
    @SerializedName("daily_used") val dailyUsed: Int = 0,
    @SerializedName("access_state") val accessState: String = "granted",
    @SerializedName("name") val name: String? = null,
)

data class PredictResponse(
    @SerializedName("content") val content: String = "",
    @SerializedName("prediction") val prediction: String? = null,
    @SerializedName("response") val response: String? = null,
    @SerializedName("prediction_id") val predictionId: String? = null,
) {
    val text: String get() = content.ifBlank { prediction ?: response ?: "" }
}

data class ChatThreadDto(
    @SerializedName("thread_id") val threadId: String,
    @SerializedName("title") val title: String,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("updated_at") val updatedAt: String,
)

data class ChatMessageDto(
    @SerializedName("message_id") val messageId: String,
    @SerializedName("role") val role: String,
    @SerializedName("content") val content: String,
    @SerializedName("created_at") val createdAt: String,
)

data class PartnerDto(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("date_of_birth") val dateOfBirth: String,
    @SerializedName("time_of_birth") val timeOfBirth: String,
    @SerializedName("city_of_birth") val cityOfBirth: String,
    @SerializedName("latitude") val latitude: Double,
    @SerializedName("longitude") val longitude: Double,
)

data class NotificationDto(
    @SerializedName("id") val id: String,
    @SerializedName("title") val title: String,
    @SerializedName("body") val body: String,
    @SerializedName("is_read") val isRead: Boolean,
    @SerializedName("created_at") val createdAt: String,
)

data class NotificationPrefsDto(
    @SerializedName("daily_insight") val dailyInsight: Boolean,
    @SerializedName("transits") val transits: Boolean,
    @SerializedName("compatibility") val compatibility: Boolean,
)

data class UnreadCountResponse(
    @SerializedName("count") val count: Int,
)

data class SuccessResponse(
    @SerializedName("success") val success: Boolean = true,
)

data class PlanDto(
    @SerializedName("plan_id") val planId: String,
    @SerializedName("display_name") val displayName: String,
    @SerializedName("is_free") val isFree: Boolean,
    @SerializedName("price_monthly") val priceMonthly: Double,
    @SerializedName("price_yearly") val priceYearly: Double,
    @SerializedName("daily_quota") val dailyQuota: Int,
)

// ── API Service Interface ─────────────────────────────────────────────────────

interface AstroApiService {

    // Auth / Registration
    @POST("subscription/register")
    suspend fun register(@Body request: RegisterRequest): RegisterResponse

    @POST("subscription/register")
    suspend fun signInWithGoogle(@Body request: GoogleSignInRequest): RegisterResponse

    @GET("subscription/status")
    suspend fun getStatus(@Query("email") email: String): StatusResponse

    @POST("subscription/upgrade")
    suspend fun upgradeGuest(@Body request: UpgradeRequest): RegisterResponse

    @POST("subscription/profile")
    suspend fun saveProfile(@Body request: ProfileRequest): RegisterResponse

    @GET("subscription/profile/{email}")
    suspend fun getProfile(@Path("email") email: String): RegisterResponse

    @DELETE("subscription/account/delete")
    suspend fun deleteAccount(@Query("email") email: String): SuccessResponse

    // Subscription Plans
    @GET("subscription/plans")
    suspend fun getPlans(): List<PlanDto>

    // Prediction
    @POST("vedic/api/predict/")
    suspend fun predict(@Body request: PredictRequest): PredictResponse

    @POST("vedic/api/predict/stream")
    @Streaming
    suspend fun streamPredict(@Body request: PredictRequest): okhttp3.ResponseBody

    // Chat History
    @GET("chat-history/threads/{userId}")
    suspend fun listChatThreads(@Path("userId") userId: String): List<ChatThreadDto>

    @GET("chat-history/threads/{userId}/{threadId}")
    suspend fun getChatThread(
        @Path("userId") userId: String,
        @Path("threadId") threadId: String,
    ): List<ChatMessageDto>

    @DELETE("chat-history/threads/{userId}/{threadId}")
    suspend fun deleteChatThread(
        @Path("userId") userId: String,
        @Path("threadId") threadId: String,
    ): SuccessResponse

    @DELETE("chat-history/all/{userId}")
    suspend fun deleteAllChatHistory(@Path("userId") userId: String): SuccessResponse

    // Notifications
    @POST("notifications/device-token")
    suspend fun registerDeviceToken(@Body request: DeviceTokenRequest): SuccessResponse

    @GET("notifications/preferences/{email}")
    suspend fun getNotificationPrefs(@Path("email") email: String): NotificationPrefsDto

    @POST("notifications/preferences/{email}")
    suspend fun updateNotificationPrefs(
        @Path("email") email: String,
        @Body request: NotificationPrefsRequest,
    ): SuccessResponse

    @GET("notifications/list")
    suspend fun listNotifications(@Query("email") email: String): NotificationListResponse

    @GET("notifications/unread-count")
    suspend fun getUnreadCount(@Query("email") email: String): UnreadCountResponse

    @POST("notifications/read-all")
    suspend fun markAllRead(@Query("email") email: String): SuccessResponse

    @PATCH("notifications/{notificationId}/read")
    suspend fun markNotificationRead(@Path("notificationId") id: String): SuccessResponse

    // Partners
    @GET("subscription/partners")
    suspend fun listPartners(@Query("user_email") email: String): List<PartnerDto>

    @POST("subscription/partners")
    suspend fun addPartner(@Body request: CreatePartnerRequest): PartnerDto

    @DELETE("subscription/partners/{partnerId}")
    suspend fun deletePartner(
        @Path("partnerId") partnerId: String,
        @Query("user_email") email: String,
    ): SuccessResponse

    // Compatibility
    @POST("vedic/api/compatibility/analyze")
    suspend fun analyzeCompatibility(@Body request: CompatibilityRequest): CompatibilityResponse

    // Feedback
    @POST("feedback/submit")
    suspend fun submitFeedback(@Body request: FeedbackRequest): SuccessResponse

    // Profiles (multi-profile)
    @POST("subscription/profiles/switch")
    suspend fun switchProfile(@Body request: Map<String, String>): RegisterResponse

    @GET("subscription/profiles/active")
    suspend fun getActiveProfile(@Query("email") email: String): RegisterResponse
}
