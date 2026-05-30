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

// Predict endpoint BirthData — matches backend BirthData schema exactly
data class PredictBirthDataDto(
    @SerializedName("dob") val dob: String,
    @SerializedName("time") val time: String,
    @SerializedName("city_of_birth") val cityOfBirth: String? = null,
    @SerializedName("latitude") val latitude: Double,
    @SerializedName("longitude") val longitude: Double,
    @SerializedName("ayanamsa") val ayanamsa: String = "lahiri",
    @SerializedName("house_system") val houseSystem: String = "whole_sign",
)

data class PredictRequest(
    @SerializedName("query") val query: String,
    @SerializedName("user_email") val userEmail: String,
    @SerializedName("birth_data") val birthData: PredictBirthDataDto,
    @SerializedName("session_id") val sessionId: String? = null,
    @SerializedName("conversation_id") val conversationId: String? = null,
    @SerializedName("language") val language: String = "en",
    @SerializedName("response_style") val responseStyle: String? = null,
    @SerializedName("response_length") val responseLength: String? = null,
    @SerializedName("platform") val platform: String = "android",
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

// Compatibility birth details — matches backend BirthDetails / iOS BirthDetails exactly
data class CompatibilityBirthDetailsDto(
    @SerializedName("dob") val dob: String,
    @SerializedName("time") val time: String,
    @SerializedName("lat") val lat: Double,
    @SerializedName("lon") val lon: Double,
    @SerializedName("name") val name: String = "Native",
    @SerializedName("place") val place: String = "",
)

data class CompatibilityRequestDto(
    @SerializedName("boy") val boy: CompatibilityBirthDetailsDto,
    @SerializedName("girl") val girl: CompatibilityBirthDetailsDto,
    @SerializedName("session_id") val sessionId: String? = null,
    @SerializedName("user_email") val userEmail: String? = null,
    @SerializedName("comparison_group_id") val comparisonGroupId: String? = null,
    @SerializedName("partner_index") val partnerIndex: Int? = null,
    @SerializedName("language") val language: String = "en",
)

data class HardNoFlagsDto(
    @SerializedName("is_recommended") val isRecommended: Boolean = true,
    @SerializedName("rejection_reasons") val rejectionReasons: List<String> = emptyList(),
    @SerializedName("cancelled_doshas_summary") val cancelledDoshasSummary: String? = null,
)

data class DoshaSummaryDto(
    @SerializedName("total_doshas") val totalDoshas: Int? = null,
    @SerializedName("cancelled_count") val cancelledCount: Int? = null,
    @SerializedName("active_count") val activeCount: Int? = null,
)

data class CompatibilityResponseDto(
    @SerializedName("session_id") val sessionId: String? = null,
    @SerializedName("status") val status: String = "",
    @SerializedName("prediction_id") val predictionId: String? = null,
    @SerializedName("llm_analysis") val llmAnalysis: String? = null,
    @SerializedName("hard_no_flags") val hardNoFlags: HardNoFlagsDto? = null,
    @SerializedName("adjusted_total_score") val adjustedTotalScore: Double? = null,
    @SerializedName("adjusted_category") val adjustedCategory: String? = null,
    @SerializedName("dosha_summary") val doshaSummary: DoshaSummaryDto? = null,
    @SerializedName("comparison_group_id") val comparisonGroupId: String? = null,
    @SerializedName("partner_index") val partnerIndex: Int? = null,
    @SerializedName("follow_up_suggestions") val followUpSuggestions: List<String>? = null,
    @SerializedName("analysis_data") val analysisData: Map<String, Any>? = null,
)

data class CompatibilityFollowUpRequest(
    @SerializedName("query") val query: String,
    @SerializedName("session_id") val sessionId: String,
    @SerializedName("user_email") val userEmail: String,
    @SerializedName("language") val language: String = "en",
    @SerializedName("response_style") val responseStyle: String? = null,
    @SerializedName("response_length") val responseLength: String? = null,
)

data class CompatibilityFollowUpResponse(
    @SerializedName("status") val status: String? = null,
    @SerializedName("target") val target: String? = null,
    @SerializedName("answer") val answer: String? = null,
    @SerializedName("message") val message: String? = null,
    @SerializedName("redirect_query") val redirectQuery: String? = null,
    @SerializedName("reason") val reason: String? = null,
    @SerializedName("follow_up_suggestions") val followUpSuggestions: List<String>? = null,
)

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

data class VerifyRequest(
    @SerializedName("signed_transaction") val signedTransaction: String,
    @SerializedName("platform") val platform: String = "android",
    @SerializedName("user_email") val userEmail: String,
    @SerializedName("product_id") val productId: String,
)

data class VerifyResponse(
    @SerializedName("success") val success: Boolean = false,
    @SerializedName("plan_id") val planId: String? = null,
    @SerializedName("is_premium") val isPremium: Boolean = false,
    @SerializedName("message") val message: String? = null,
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

data class LocationResult(
    @SerializedName("city") val city: String,
    @SerializedName("latitude") val latitude: Double,
    @SerializedName("longitude") val longitude: Double,
    @SerializedName("display_name") val displayName: String,
)

data class AnalyticsConsentRequest(
    @SerializedName("email") val email: String,
    @SerializedName("consent") val consent: Boolean,
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

    // Purchase Verification (Google Play Billing)
    @POST("subscription/verify")
    suspend fun verifyPurchase(@Body req: VerifyRequest): VerifyResponse

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
    @POST("vedic/api/compatibility/analyze/stream")
    @Streaming
    suspend fun streamCompatibilityAnalysis(@Body request: CompatibilityRequestDto): okhttp3.ResponseBody

    @POST("vedic/api/compatibility/follow-up")
    suspend fun compatibilityFollowUp(@Body request: CompatibilityFollowUpRequest): CompatibilityFollowUpResponse

    // Feedback
    @POST("feedback/submit")
    suspend fun submitFeedback(@Body request: FeedbackRequest): SuccessResponse

    // Profiles (multi-profile)
    @POST("subscription/profiles/switch")
    suspend fun switchProfile(@Body request: Map<String, String>): RegisterResponse

    @GET("subscription/profiles/active")
    suspend fun getActiveProfile(@Query("email") email: String): RegisterResponse

    // Analytics Consent
    @POST("subscription/analytics-consent")
    suspend fun updateAnalyticsConsent(@Body request: AnalyticsConsentRequest): SuccessResponse

    // Location Search
    @GET("api/v2/location/search")
    suspend fun searchLocations(@Query("query") query: String): List<LocationResult>

    // Chart Data (Vedic birth chart with planets, houses, nakshatra, D9)
    @POST("vedic/api/chart-data/")
    suspend fun getChartData(@Body request: com.destinyai.astrology.ui.charts.ChartDataRequest): com.destinyai.astrology.ui.charts.ChartApiResponse
}
