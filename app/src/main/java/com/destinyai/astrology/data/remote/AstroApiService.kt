package com.destinyai.astrology.data.remote

import com.google.gson.annotations.SerializedName
import retrofit2.http.*

// ── Request DTOs ──────────────────────────────────────────────────────────────

data class RegisterRequest(
    @SerializedName("email") val email: String,
    @SerializedName("is_generated_email") val isGeneratedEmail: Boolean,
    @SerializedName("google_id") val googleId: String? = null,
    // iOS parity (ProfileService.registerUser): backend /subscription/register accepts
    // apple_id alongside google_id so a SSO user can be looked up by stable provider ID
    // when the email is a placeholder ("Hide My Email" / lookup-by-id flows).
    @SerializedName("apple_id") val appleId: String? = null,
    @SerializedName("name") val name: String? = null,
)

// Mirrors backend RegisterRequest (subscription_router.py:46-51) — backend
// requires `email` (and looks up users by `google_id` when provided). Parity
// with iOS ProfileService.registerUser and AppleSignInRequest above:
// is_generated_email is FALSE because Google Sign-In = registered user, not guest.
// id_token is retained for forward-compat (server may verify it) but the backend
// currently ignores it; email + google_id are what drive the lookup.
data class GoogleSignInRequest(
    @SerializedName("email") val email: String,
    @SerializedName("is_generated_email") val isGeneratedEmail: Boolean = false,
    @SerializedName("google_id") val googleId: String,
    @SerializedName("name") val name: String? = null,
    @SerializedName("id_token") val idToken: String? = null,
)

// Mirrors iOS ProfileService.registerUser(appleId:) — backend looks up user by
// apple_id, returns the stored email (handles "Hide My Email" + new-device).
// is_generated_email is FALSE because Apple Sign-In = registered user, not guest.
data class AppleSignInRequest(
    @SerializedName("email") val email: String,
    @SerializedName("is_generated_email") val isGeneratedEmail: Boolean = false,
    @SerializedName("apple_id") val appleId: String,
    @SerializedName("name") val name: String? = null,
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
    // Backend ProfileRequest also accepts apple_id/google_id for user lookup by SSO ID
    @SerializedName("apple_id") val appleId: String? = null,
    @SerializedName("google_id") val googleId: String? = null,
)

data class BirthProfileDto(
    @SerializedName("date_of_birth") val dateOfBirth: String,
    @SerializedName("time_of_birth") val timeOfBirth: String,
    @SerializedName("city_of_birth") val cityOfBirth: String,
    @SerializedName("latitude") val latitude: Double,
    @SerializedName("longitude") val longitude: Double,
    @SerializedName("gender") val gender: String? = null,
    @SerializedName("birth_time_unknown") val birthTimeUnknown: Boolean = false,
    // iOS parity (BirthDataViewModel.swift:17): persist Google place_id so backend
    // can disambiguate cities with the same name on re-resolution.
    @SerializedName("place_id") val placeId: String? = null,
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

// Mirrors iOS UnifiedFeedbackPayload (FeedbackService.swift) — backend
// UnifiedFeedbackRequest treats query/prediction_text/area/system as required for
// RL training; missing fields cause Pydantic 422 or empty rows in the training set.
data class FeedbackRequest(
    @SerializedName("prediction_id") val predictionId: String?,
    @SerializedName("session_id") val sessionId: String?,
    @SerializedName("conversation_id") val conversationId: String? = null,
    @SerializedName("user_email") val userEmail: String?,
    @SerializedName("query") val query: String,
    @SerializedName("prediction_text") val predictionText: String,
    @SerializedName("area") val area: String = "general",
    @SerializedName("sub_area") val subArea: String? = null,
    @SerializedName("ascendant") val ascendant: String? = null,
    @SerializedName("system") val system: String = "vedic",
    @SerializedName("rating") val rating: Int,
)

// Matches backend PartnerProfileData exactly (subscription_router.py)
data class PartnerRequest(
    @SerializedName("name") val name: String,
    // gender is required by backend (male/female); default empty string for backward compat
    @SerializedName("gender") val gender: String = "",
    @SerializedName("date_of_birth") val dateOfBirth: String,
    @SerializedName("time_of_birth") val timeOfBirth: String? = null,
    @SerializedName("city_of_birth") val cityOfBirth: String? = null,
    @SerializedName("latitude") val latitude: Double? = null,
    @SerializedName("longitude") val longitude: Double? = null,
    @SerializedName("timezone") val timezone: Double? = null,
    @SerializedName("birth_time_unknown") val birthTimeUnknown: Boolean = false,
    @SerializedName("is_self") val isSelf: Boolean = false,
    @SerializedName("for_compatibility") val forCompatibility: Boolean = false,
    @SerializedName("guardian_consent_given") val guardianConsentGiven: Boolean = false,
)

// Matches backend CreatePartnerRequest exactly (subscription_router.py)
data class CreatePartnerRequest(
    @SerializedName("user_email") val userEmail: String,
    @SerializedName("profile") val profile: PartnerRequest,
    @SerializedName("consent_given") val consentGiven: Boolean = true,
    @SerializedName("guardian_consent_given") val guardianConsentGiven: Boolean = false,
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
    @SerializedName("profile_id") val profileId: String? = null,
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
    // Backend NotificationListResponse (notification_router.py:74) emits `total_count`,
    // not `total`. Accept legacy `total` as alternate so older mocked responses still parse.
    @SerializedName(value = "total_count", alternate = ["total"]) val total: Int = 0,
    @SerializedName("has_more") val hasMore: Boolean? = null,
    @SerializedName("page") val page: Int? = null,
    @SerializedName("page_size") val pageSize: Int? = null,
)

// Matches backend NotificationPreferencesRequest (notification_router.py)
// The 3 legacy booleans (daily_insight/transits/compatibility) are Android-internal
// and not part of the backend schema — kept here for ViewModel layer compatibility.
// Backend fields: is_enabled, email_enabled, push_enabled, in_app_enabled,
//   custom_instruction, alert_items, frequency, frequency_day, preferred_time_utc, timezone
data class NotificationPrefsRequest(
    @SerializedName("daily_insight") val dailyInsight: Boolean = true,
    @SerializedName("transits") val transits: Boolean = true,
    @SerializedName("compatibility") val compatibility: Boolean = true,
    // Backend channel toggles
    @SerializedName("is_enabled") val isEnabled: Boolean? = null,
    @SerializedName("push_enabled") val pushEnabled: Boolean? = null,
    @SerializedName("email_enabled") val emailEnabled: Boolean? = null,
    @SerializedName("in_app_enabled") val inAppEnabled: Boolean? = null,
    @SerializedName("custom_instruction") val customInstruction: String? = null,
    @SerializedName("alert_items") val alertItems: List<AlertItemDto>? = null,
    @SerializedName("frequency") val frequency: String? = null,
    @SerializedName("frequency_day") val frequencyDay: Int? = null,
    @SerializedName("preferred_time_utc") val preferredTimeUtc: String? = null,
    @SerializedName("timezone") val timezone: String? = null,
)

data class ReadAllRequest(
    @SerializedName("user_email") val userEmail: String,
)

data class VerifyRequest(
    @SerializedName("signed_transaction") val signedTransaction: String,
    // Backend subscription_router.verify_purchase only accepts apple|google|stripe.
    // Default to "google" for the Android billing flow.
    @SerializedName("platform") val platform: String = "google",
    @SerializedName("user_email") val userEmail: String,
    @SerializedName("product_id") val productId: String,
    // iOS parity — backend uses environment ("Sandbox" | "Production") to gate
    // test-track purchases against real entitlements on prod backend.
    @SerializedName("environment") val environment: String? = null,
)

data class VerifyResponse(
    @SerializedName("success") val success: Boolean = false,
    @SerializedName("plan_id") val planId: String? = null,
    @SerializedName("is_premium") val isPremium: Boolean = false,
    @SerializedName("expires_at") val expiresAt: String? = null,
    @SerializedName("message") val message: String? = null,
    @SerializedName("error") val error: String? = null,
    // iOS parity (SubscriptionManager.swift:27-28, 501-555 — pendingUpgradeProductId
    // and pendingUpgradeEffectiveDate from StoreKit renewalInfo.autoRenewPreference).
    // Backend webhook-derived scheduled Core→Plus change exposed to clients so the
    // UI can render a "Scheduled" badge and effective date.
    @SerializedName("pending_upgrade_product_id") val pendingUpgradeProductId: String? = null,
    @SerializedName("pending_upgrade_effective_date") val pendingUpgradeEffectiveDate: String? = null,
)

// ── Response DTOs ─────────────────────────────────────────────────────────────

// Matches backend AlertItemRequest (notification_router.py)
data class AlertItemDto(
    @SerializedName("id") val id: String? = null,
    @SerializedName("text") val text: String,
    @SerializedName("frequency") val frequency: String = "DAILY",
    @SerializedName("frequency_day") val frequencyDay: Int? = null,
)

// Matches backend ProfileResponse (subscription_router.py)
// Returned by POST /subscription/profile and GET /subscription/profile
data class ProfileResponse(
    @SerializedName("user_email") val userEmail: String,
    @SerializedName("user_name") val userName: String? = null,
    @SerializedName("plan_id") val planId: String? = null,
    @SerializedName("is_generated_email") val isGeneratedEmail: Boolean = false,
    @SerializedName("feature_usage") val featureUsage: Map<String, Any>? = null,
    @SerializedName("is_premium") val isPremium: Boolean = false,
    @SerializedName("subscription_status") val subscriptionStatus: String? = null,
    @SerializedName("subscription_expires_at") val subscriptionExpiresAt: String? = null,
    @SerializedName("birth_profile") val birthProfile: BirthProfileDto? = null,
    // iOS parity (ProfileService.swift ProfileResponse): analytics_consent surfaced
    // via GET /subscription/profile so app can mirror the saved consent on launch
    // without a separate /subscription/status round-trip.
    @SerializedName("analytics_consent") val analyticsConsent: Boolean? = null,
    // iOS parity: access_state ("granted" | "waitlist_pending" | …) drives gate
    // routing post-sign-in. Default null so legacy backend responses still parse.
    @SerializedName("access_state") val accessState: String? = null,
)

// Matches backend PartnerProfileResponse (subscription_router.py)
// Returned by GET /subscription/profiles/active and POST /subscription/partners
data class PartnerProfileResponse(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("gender") val gender: String = "",
    @SerializedName("date_of_birth") val dateOfBirth: String? = null,
    @SerializedName("time_of_birth") val timeOfBirth: String? = null,
    @SerializedName("city_of_birth") val cityOfBirth: String? = null,
    @SerializedName("latitude") val latitude: Double? = null,
    @SerializedName("longitude") val longitude: Double? = null,
    @SerializedName("timezone") val timezone: Double? = null,
    @SerializedName("birth_time_unknown") val birthTimeUnknown: Boolean = false,
    @SerializedName("consent_given") val consentGiven: Boolean = true,
    @SerializedName("guardian_consent_given") val guardianConsentGiven: Boolean = false,
    @SerializedName("for_compatibility") val forCompatibility: Boolean = false,
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("updated_at") val updatedAt: String? = null,
    @SerializedName("last_matched_at") val lastMatchedAt: String? = null,
    @SerializedName("is_self") val isSelf: Boolean = false,
    @SerializedName("is_active") val isActive: Boolean = false,
    @SerializedName("first_switched_at") val firstSwitchedAt: String? = null,
)

// Typed request for POST /subscription/profiles/switch (subscription_router.py)
// Backend expects: user_email (owner's email for plan validation) + profile_id (not email)
data class SwitchProfileRequest(
    @SerializedName("user_email") val userEmail: String,
    @SerializedName("profile_id") val profileId: String,
)

// Matches backend SwitchProfileResponse (subscription_router.py)
data class SwitchProfileResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("active_profile_id") val activeProfileId: String,
    @SerializedName("active_profile_name") val activeProfileName: String,
    @SerializedName("message") val message: String? = null,
)

// Matches backend DeleteAccountRequest (subscription_router.py)
// POST /subscription/account/delete requires a body with confirmation = "DELETE"
data class DeleteAccountRequest(
    @SerializedName("user_email") val userEmail: String,
    @SerializedName("confirmation") val confirmation: String = "DELETE",
)

// Matches backend PreferencesResponse (notification_router.py)
// The preferences field is an untyped dict; Android layer maps it to NotificationPrefsDto
data class NotificationPreferencesResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("preferences") val preferences: Map<String, Any?> = emptyMap(),
)

data class RegisterResponse(
    @SerializedName("user_email") val userEmail: String,
    // Backend /subscription/register returns StatusResponse with Optional[str] plan_id
    // (subscription_router.py:81). Nullable for safety — guest users have no plan yet.
    @SerializedName("plan_id") val planId: String? = null,
    @SerializedName("is_generated_email") val isGeneratedEmail: Boolean,
    @SerializedName("is_premium") val isPremium: Boolean,
    @SerializedName("access_state") val accessState: String,
    // Backend now emits daily_quota / daily_used derived from feature_usage in
    // subscription_router._derive_quota_view (parity fix). Both can be null
    // when no quota gates the user's plan, so use nullable Int.
    @SerializedName("daily_quota") val dailyQuota: Int? = null,
    @SerializedName("daily_used") val dailyUsed: Int? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("google_id") val googleId: String? = null,
)

data class StatusResponse(
    @SerializedName("user_email") val userEmail: String,
    // Backend StatusResponse.plan_id is Optional[str] (subscription_router.py:81).
    // Use nullable String — non-null default would NPE for newly registered users
    // before plan assignment lands.
    @SerializedName("plan_id") val planId: String? = null,
    @SerializedName("is_generated_email") val isGeneratedEmail: Boolean = false,
    @SerializedName("is_premium") val isPremium: Boolean,
    // Backend emits daily_quota / daily_used as Optional[int] — null when no
    // quota gates the plan. Use nullable Int instead of fake defaults so the
    // UI can distinguish "no quota tracked" from a real numeric value.
    @SerializedName("daily_quota") val dailyQuota: Int? = null,
    @SerializedName("daily_used") val dailyUsed: Int? = null,
    @SerializedName("access_state") val accessState: String = "granted",
    @SerializedName("name") val name: String? = null,
    @SerializedName("analytics_consent") val analyticsConsent: Boolean? = null,
    @SerializedName("pending_upgrade_plan_id") val pendingUpgradePlanId: String? = null,
    @SerializedName("pending_upgrade_date") val pendingUpgradeDate: String? = null,
    // iOS parity (QuotaManager.swift:265-281 SubscriptionStatus): server-mirrored
    // subscription metadata used by SubscriptionView/HomeViewModel to render
    // plan/expiry/auto-renew badges on cold start before the next sync lands.
    @SerializedName("subscription_status") val subscriptionStatus: String? = null,
    @SerializedName("subscription_expires_at") val subscriptionExpiresAt: String? = null,
    @SerializedName("auto_renew_status") val autoRenewStatus: Boolean? = null,
    @SerializedName("plan_display_name") val planDisplayName: String? = null,
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
    @SerializedName("is_pinned") val isPinned: Boolean = false,
)

data class ChatHistorySettingsDto(
    @SerializedName("history_enabled") val historyEnabled: Boolean = true,
    @SerializedName("save_conversations") val saveConversations: Boolean = true,
)

data class UpdateChatThreadRequest(
    @SerializedName("is_pinned") val isPinned: Boolean? = null,
    @SerializedName("title") val title: String? = null,
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
    @SerializedName("gender") val gender: String = "",
    @SerializedName("date_of_birth") val dateOfBirth: String? = null,
    @SerializedName("time_of_birth") val timeOfBirth: String? = null,
    @SerializedName("city_of_birth") val cityOfBirth: String? = null,
    @SerializedName("latitude") val latitude: Double? = null,
    @SerializedName("longitude") val longitude: Double? = null,
    @SerializedName("timezone") val timezone: Double? = null,
    @SerializedName("birth_time_unknown") val birthTimeUnknown: Boolean = false,
    @SerializedName("for_compatibility") val forCompatibility: Boolean = false,
    @SerializedName("guardian_consent_given") val guardianConsentGiven: Boolean = false,
    @SerializedName("is_self") val isSelf: Boolean = false,
    @SerializedName("is_active") val isActive: Boolean = false,
    @SerializedName("first_switched_at") val firstSwitchedAt: String? = null,
) {
    /** Mirrors iOS PartnerProfile.isProtected — primary/active/used profiles cannot be edited or deleted. */
    val isProtected: Boolean
        get() = isSelf || isActive || firstSwitchedAt != null
}

data class NotificationDto(
    @SerializedName("id") val id: String,
    // Backend NotificationItem emits `subject`/`preview` (not `title`/`body`).
    // Accept either: legacy clients/mocks may still send `title`/`body`, but
    // the live backend wire fields are `subject` and `preview` (notification_router.py:61-62).
    @SerializedName(value = "subject", alternate = ["title"]) val title: String? = null,
    @SerializedName(value = "preview", alternate = ["body"]) val body: String? = null,
    // Backend NotificationItem (notification_router.py:64) wire field is `read` (bool).
    // Accept legacy `is_read` as alternate. Without this, isRead would silently always
    // default to false on the wire, breaking unread badge / read-state UI.
    @SerializedName(value = "read", alternate = ["is_read"]) val isRead: Boolean = false,
    @SerializedName("created_at") val createdAt: String? = null,
    // iOS parity (NotificationItem in NotificationModels.swift): status + read_at
    // mirrored locally so server-side state and client cache stay aligned after
    // mark-as-read / mark-all-read mutations.
    @SerializedName("status") val status: String? = null,
    @SerializedName("read_at") val readAt: String? = null,
    // iOS parity (NotificationModels.swift:5-29) — fields needed for routing,
    // tone-aware accent color, topic chip, icon switching, and the detail
    // sheet's primary action button (Ask More / Compatibility / Subscription).
    @SerializedName("type") val type: String? = null,
    @SerializedName("channel") val channel: String? = null,
    @SerializedName("subject") val subject: String? = null,
    @SerializedName("preview") val preview: String? = null,
    @SerializedName("action_url") val actionUrl: String? = null,
    @SerializedName("image_url") val imageUrl: String? = null,
    @SerializedName("chat_prompt") val chatPrompt: String? = null,
    @SerializedName("topic") val topic: String? = null,
    @SerializedName("overall_tone") val overallTone: String? = null,
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
    @SerializedName("deleted_count") val deletedCount: Int? = null,
    @SerializedName("message") val message: String? = null,
)

data class PlanDto(
    @SerializedName("plan_id") val planId: String,
    @SerializedName("display_name") val displayName: String,
    @SerializedName("is_free") val isFree: Boolean,
    @SerializedName("price_monthly") val priceMonthly: Double,
    @SerializedName("price_yearly") val priceYearly: Double,
    @SerializedName("daily_quota") val dailyQuota: Int,
    @SerializedName("description") val description: String? = null,
    @SerializedName("entitlements") val entitlements: List<PlanEntitlementDto>? = null,
)

/** iOS parity (PlanEntitlement / FeatureItemRow in SubscriptionView.swift:521-572).
 *  Drives the per-plan checkmark feature list rendered on each plan card. */
data class PlanEntitlementDto(
    @SerializedName("feature_id") val featureId: String,
    @SerializedName("display_name") val displayName: String,
    @SerializedName("description") val description: String? = null,
    @SerializedName("marketing_text") val marketingText: String? = null,
    @SerializedName("daily_limit") val dailyLimit: Int? = null,
    @SerializedName("overall_limit") val overallLimit: Int? = null,
)

data class LocationResult(
    @SerializedName("city") val city: String,
    @SerializedName("latitude") val latitude: Double,
    @SerializedName("longitude") val longitude: Double,
    @SerializedName("display_name") val displayName: String,
    // iOS parity (LocationSearchView passes Google place_id back to BirthDataViewModel.setLocation):
    // capture place_id when backend returns it, otherwise null.
    @SerializedName("place_id") val placeId: String? = null,
)

// Mirrors iOS ProfileService:619-622 body shape — backend Pydantic schema
// expects user_email/analytics_consent (not email/consent), else 422.
data class AnalyticsConsentRequest(
    @SerializedName("user_email") val email: String,
    @SerializedName("analytics_consent") val consent: Boolean,
)

// Today's prediction request — minimal birth_data envelope
// iOS parity (UserAstroDataModels.swift:5-37): top-level user_email/language/is_first_login
// siblings to birth_data so backend can deliver onboarding fixed question + cache by language.
data class UserAstroDataRequest(
    @SerializedName("birth_data") val birth_data: Map<String, Any?>,
    @SerializedName("user_email") val user_email: String? = null,
    @SerializedName("language") val language: String? = null,
    @SerializedName("is_first_login") val is_first_login: Boolean? = null,
)

// Today's prediction response — fields are optional; backend may return any subset.
// `current_dasha` is typed Any? to defensively accept both legacy String shape
// (e.g. "Venus-Rahu-Moon") and the structured Map shape returned by newer backends.
data class TodaysPredictionResponse(
    @SerializedName(value = "text", alternate = ["todays_insight"]) val text: String? = null,
    @SerializedName("current_dasha") val current_dasha: Any? = null,
    @SerializedName("life_areas") val life_areas: Map<String, Map<String, Any?>>? = null,
    @SerializedName("transit_influences") val transit_influences: List<Map<String, Any?>>? = null,
    @SerializedName(value = "suggested_questions", alternate = ["mind_questions"]) val suggested_questions: List<String>? = null,
    @SerializedName("analysis") val analysis: AstroAnalysisDto? = null,
    @SerializedName("insight_area") val insightArea: String? = null,
    @SerializedName("target_date") val targetDate: String? = null,
    @SerializedName("prediction_id") val predictionId: String? = null,
)

// Mirrors iOS AstroAnalysisData — yogas + dosha verdicts surfaced from the prediction endpoint
data class AstroAnalysisDto(
    @SerializedName("yogas") val yogas: YogasContainerDto? = null,
    @SerializedName("mangal_dosha") val mangalDosha: MangalDoshaResultDto? = null,
    @SerializedName("kala_sarpa") val kalaSarpa: KalaSarpaResultDto? = null,
)

data class YogasContainerDto(
    @SerializedName("yogas") val yogas: List<YogaDetailDto>? = null,
    @SerializedName("doshas") val doshas: List<YogaDetailDto>? = null,
)

data class YogaDetailDto(
    @SerializedName("name") val name: String = "",
    @SerializedName("yoga_key") val yogaKey: String? = null,
    @SerializedName("planets") val planets: String? = null,
    @SerializedName("houses") val houses: String? = null,
    @SerializedName("status") val status: String? = null,
    @SerializedName("strength") val strength: Double? = null,
    @SerializedName("is_dosha") val isDosha: Boolean? = null,
    @SerializedName("category") val category: String? = null,
    @SerializedName("formation") val formation: String? = null,
    @SerializedName("outcome") val outcome: String? = null,
    @SerializedName("reason") val reason: String? = null,
)

data class MangalDoshaResultDto(
    @SerializedName("has_mangal_dosha") val hasMangalDosha: Boolean? = null,
    @SerializedName("severity") val severity: String? = null,
    @SerializedName("is_cancelled") val isCancelled: Boolean? = null,
)

data class KalaSarpaResultDto(
    @SerializedName("yoga_present") val yogaPresent: Boolean? = null,
    @SerializedName("dosha_name") val doshaName: String? = null,
    @SerializedName("axis") val axis: String? = null,
)

// Feature gating response — GET /subscription/can-access
// NOTE: Backend wire field is `can_access`; we accept `allowed` as an alternate so existing
// ChatViewModel call sites keep working alongside the new QuotaManager.
data class CanAccessResponse(
    @SerializedName(value = "can_access", alternate = ["allowed"]) val allowed: Boolean,
    @SerializedName("reason") val reason: String? = null,
    @SerializedName("resets_at") val resets_at: String? = null,
)

// Richer feature-access response mirroring iOS FeatureAccessResponse — used by QuotaManager
data class FeatureLimitInfo(
    @SerializedName("used") val used: Int = 0,
    @SerializedName("limit") val limit: Int = 0,
    @SerializedName("remaining") val remaining: Int = 0,
) {
    val isUnlimited: Boolean get() = limit == -1
    val hasRemaining: Boolean get() = isUnlimited || remaining > 0
}

data class UpgradeCtaDto(
    @SerializedName("message") val message: String? = null,
    @SerializedName("suggested_plan") val suggestedPlan: String? = null,
)

data class FeatureAccessResponse(
    @SerializedName("can_access") val canAccess: Boolean,
    @SerializedName("feature") val feature: String? = null,
    @SerializedName("plan_id") val planId: String? = null,
    @SerializedName("reason") val reason: String? = null,
    @SerializedName("requires_quota") val requiresQuota: Boolean? = null,
    @SerializedName("limits") val limits: Map<String, FeatureLimitInfo>? = null,
    @SerializedName("reset_at") val resetAt: String? = null,
    @SerializedName("upgrade_cta") val upgradeCta: UpgradeCtaDto? = null,
)

// POST /subscription/use — record feature usage
data class UseFeatureRequest(
    @SerializedName("email") val email: String,
    @SerializedName("feature_id") val featureId: String,
)

data class UsageInfoDto(
    @SerializedName("daily") val daily: FeatureLimitInfo,
    @SerializedName("overall") val overall: FeatureLimitInfo,
)

data class UseFeatureResponse(
    @SerializedName("success") val success: Boolean = false,
    @SerializedName("feature") val feature: String? = null,
    @SerializedName("usage") val usage: UsageInfoDto? = null,
    @SerializedName("error") val error: String? = null,
)

// Mirrors iOS AppStartupService.ConfigResponse (AppStartupService.swift:15-22).
// Backend GET /api/v2/app/config returns gate config used to hide/show guest
// CTA and to drive gate-mode awareness.
data class AppConfigResponse(
    @SerializedName("gate_mode") val gateMode: String = "off",
    @SerializedName("allow_guest") val allowGuest: Boolean = false,
)

// ── API Service Interface ─────────────────────────────────────────────────────

interface AstroApiService {

    // Auth / Registration
    @POST("subscription/register")
    suspend fun register(@Body request: RegisterRequest): RegisterResponse

    @POST("subscription/register")
    suspend fun signInWithGoogle(@Body request: GoogleSignInRequest): RegisterResponse

    // Mirrors iOS Apple Sign-In flow (ProfileService.registerUser with apple_id) —
    // backend uses /subscription/register and looks up the user by apple_id, returning
    // the stored email even when Apple's "Hide My Email" obscures it client-side.
    @POST("subscription/register")
    suspend fun signInWithApple(@Body request: AppleSignInRequest): RegisterResponse

    @GET("subscription/status")
    suspend fun getStatus(@Query("email") email: String): StatusResponse

    // App-level gate config — mirrors iOS AppStartupService.fetchConfig
    // (AppStartupService.swift:24-45). Drives guest button visibility + gate mode.
    @GET("api/v2/app/config")
    suspend fun getAppConfig(): AppConfigResponse

    @POST("subscription/upgrade")
    suspend fun upgradeGuest(@Body request: UpgradeRequest): RegisterResponse

    @POST("subscription/profile")
    suspend fun saveProfile(@Body request: ProfileRequest): ProfileResponse

    // Backend GET /subscription/profile uses query param, not path segment
    @GET("subscription/profile")
    suspend fun getProfile(@Query("email") email: String): ProfileResponse

    // Backend uses POST /subscription/account/delete with body (not DELETE + query param)
    @POST("subscription/account/delete")
    suspend fun deleteAccount(@Body request: DeleteAccountRequest): SuccessResponse

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

    // Chat History Settings — mirrors iOS HistorySettingsManager
    @GET("chat-history/settings/{userId}")
    suspend fun getChatHistorySettings(@Path("userId") userId: String): ChatHistorySettingsDto

    @PUT("chat-history/settings/{userId}")
    suspend fun updateChatHistorySettings(
        @Path("userId") userId: String,
        @Query("history_enabled") historyEnabled: Boolean,
        @Query("save_conversations") saveConversations: Boolean,
    ): SuccessResponse

    // Pin / unpin a chat thread (server-side persistence)
    @PATCH("chat-history/threads/{userId}/{threadId}")
    suspend fun updateChatThread(
        @Path("userId") userId: String,
        @Path("threadId") threadId: String,
        @Body request: UpdateChatThreadRequest,
    ): SuccessResponse

    // Notifications
    @POST("notifications/device-token")
    suspend fun registerDeviceToken(@Body request: DeviceTokenRequest): SuccessResponse

    // Backend uses query param (not path param): GET /notifications/preferences?user_email=...
    @GET("notifications/preferences")
    suspend fun getNotificationPrefs(@Query("user_email") email: String): NotificationPrefsDto

    // Backend uses PUT (not POST) and query param: PUT /notifications/preferences?user_email=...
    @PUT("notifications/preferences")
    suspend fun updateNotificationPrefs(
        @Query("user_email") email: String,
        @Body request: NotificationPrefsRequest,
    ): SuccessResponse

    @GET("notifications")
    suspend fun listNotifications(
        @Query("user_email") email: String,
        @Query("page") page: Int = 1,
        @Query("page_size") pageSize: Int = 20,
    ): NotificationListResponse

    @GET("notifications/unread-count")
    suspend fun getUnreadCount(@Query("user_email") email: String): UnreadCountResponse

    @POST("notifications/read-all")
    suspend fun markAllRead(@Query("user_email") email: String): SuccessResponse

    // Backend uses POST (not PATCH) — mirrors iOS NotificationInboxService.markAsRead
    @POST("notifications/{notificationId}/read")
    suspend fun markNotificationRead(@Path("notificationId") id: String): SuccessResponse

    // Partners
    @GET("subscription/partners")
    suspend fun listPartners(@Query("user_email") email: String): List<PartnerDto>

    @POST("subscription/partners")
    suspend fun addPartner(@Body request: CreatePartnerRequest): PartnerDto

    // Mirrors iOS PartnerProfileService.updatePartner — PUT /subscription/partners/{id}
    @PUT("subscription/partners/{partnerId}")
    suspend fun updatePartner(
        @Path("partnerId") partnerId: String,
        @Body request: CreatePartnerRequest,
    ): PartnerDto

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

    // Feedback — Authorization: Bearer is added by the global AuthInterceptor in
    // NetworkModule. iOS FeedbackService uses Bearer for parity (post-unification).
    @POST("feedback/submit")
    suspend fun submitFeedback(
        @Body request: FeedbackRequest,
    ): SuccessResponse

    // Profiles (multi-profile)
    // Backend SwitchProfileRequest: { user_email, profile_id } — not target_email
    @POST("subscription/profiles/switch")
    suspend fun switchProfile(@Body request: SwitchProfileRequest): SwitchProfileResponse

    // Backend returns PartnerProfileResponse (not RegisterResponse)
    // Backend GET /subscription/profiles/active expects `user_email` query param
    // (subscription_router.py:1315), not `email`. Sending the wrong key yields 422.
    @GET("subscription/profiles/active")
    suspend fun getActiveProfile(@Query("user_email") email: String): PartnerProfileResponse

    // Analytics Consent
    @POST("subscription/analytics-consent")
    suspend fun updateAnalyticsConsent(@Body request: AnalyticsConsentRequest): SuccessResponse

    // Location Search
    @GET("api/v2/location/search")
    suspend fun searchLocations(@Query("query") query: String): List<LocationResult>

    // Chart Data (Vedic birth chart with planets, houses, nakshatra, D9)
    @POST("vedic/api/astrodata/full")
    suspend fun getChartData(@Body request: com.destinyai.astrology.ui.charts.ChartDataRequest): com.destinyai.astrology.ui.charts.ChartApiResponse

    // Dasha periods for a given year (mirrors iOS UserChartService.fetchDashaPeriods)
    @POST("vedic/api/astrodata/dasha")
    suspend fun getDashaPeriods(
        @Header("Authorization") authHeader: String,
        @Body request: com.destinyai.astrology.ui.charts.DashaTransitRequest,
    ): com.destinyai.astrology.ui.charts.DashaResponse

    // Transits for a given year (mirrors iOS UserChartService.fetchTransits)
    @POST("vedic/api/astrodata/transits")
    suspend fun getTransits(
        @Header("Authorization") authHeader: String,
        @Body request: com.destinyai.astrology.ui.charts.DashaTransitRequest,
    ): com.destinyai.astrology.ui.charts.TransitResponse

    // Today's prediction (Home tab daily snapshot)
    @POST("vedic/api/todays-prediction")
    suspend fun getTodaysPrediction(
        @Header("Authorization") authHeader: String,
        @Body req: UserAstroDataRequest,
    ): TodaysPredictionResponse

    // Feature gating — checks if user can access a given feature within plan quota
    @GET("subscription/can-access")
    suspend fun canAccessFeature(
        @Header("Authorization") authHeader: String,
        @Query("email") email: String,
        @Query("feature") feature: String,
        @Query("count") count: Int? = null,
    ): CanAccessResponse

    // Richer variant returning full quota / upgrade-CTA payload — used by QuotaManager
    @GET("subscription/can-access")
    suspend fun canAccessFeatureFull(
        @Header("Authorization") authHeader: String,
        @Query("email") email: String,
        @Query("feature") feature: String,
        @Query("count") count: Int? = null,
    ): FeatureAccessResponse

    // Record feature usage after a successful gated action (mirrors iOS recordFeatureUsage)
    @POST("subscription/use")
    suspend fun useFeature(
        @Header("Authorization") authHeader: String,
        @Body request: UseFeatureRequest,
    ): UseFeatureResponse
}
