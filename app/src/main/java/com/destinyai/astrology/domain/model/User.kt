package com.destinyai.astrology.domain.model

data class User(
    val email: String,
    val isGuestEmail: Boolean,
    val name: String? = null,
    val googleId: String? = null,
    val isPremium: Boolean = false,
    val planId: String = "free_guest",
    val dailyQuota: Int = 3,
    val dailyUsed: Int = 0,
    val accessState: String = "granted",
)

data class BirthProfile(
    val dateOfBirth: String,
    val timeOfBirth: String,
    val cityOfBirth: String,
    val latitude: Double,
    val longitude: Double,
)

data class PartnerProfile(
    val id: String,
    val name: String,
    val birthProfile: BirthProfile,
    val ownerEmail: String,
)

data class NotificationItem(
    val id: String,
    val title: String,
    val body: String,
    val isRead: Boolean,
    val createdAt: String,
)

data class SubscriptionPlan(
    val planId: String,
    val displayName: String,
    val isFree: Boolean,
    val priceMonthly: Double,
    val priceYearly: Double,
    val dailyQuota: Int,
)

data class DashaInfo(
    val mahadasha: String,
    val antardasha: String,
    val endDate: String,
    val theme: String,
)

data class TransitInfo(
    val planet: String,
    val sign: String,
    val house: Int,
    val effect: String,
    val isAlert: Boolean,
)

data class YogaInfo(
    val name: String,
    val category: String,
    val isActive: Boolean,
    val description: String,
)

data class AstroData(
    val dasha: DashaInfo?,
    val transits: List<TransitInfo>,
    val yogas: List<YogaInfo>,
    val dailyInsight: String,
    val suggestedQuestions: List<String>,
)
