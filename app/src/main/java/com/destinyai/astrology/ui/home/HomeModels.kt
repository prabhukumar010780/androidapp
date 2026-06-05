package com.destinyai.astrology.ui.home

data class HomeTransit(
    val planet: String,
    val sign: String,
    val influence: String,
    val isFavorable: Boolean,
    // Optional richer fields populated from server `transit_influences` payload
    // (parity with iOS TransitInfluencesSection rendering and chat-context build).
    val house: Int = 0,
    val description: String = "",
    val badgeType: String = "neutral", // positive | caution | warning | neutral
)

data class HomeDashaInfo(
    val mahadasha: String,
    val antardasha: String,
    val endsAt: String,
    // R2-H12: upcoming antardasha for "Next Up" line
    val upcomingAntardasha: String? = null,
    // R2-H13/14: progress ring fields
    val periodStartIso: String? = null,
    val periodEndIso: String? = null,
    // R2-H21: theme
    val theme: String? = null,
    // R2-H19: quality label ("Good", "Steady", "Caution")
    val quality: String? = null,
    // Parity with iOS DashaInsightCard — meaning paragraph displayed below theme.
    val meaning: String? = null,
)

data class HomeYoga(
    val name: String,
    val description: String,
    // R2-H24/25: category and status
    val category: String = "Other",
    val isActive: Boolean = true,
    // R2-H31: status for detail popup ("active", "reduced", "cancelled")
    val status: String = "active",
    // R2-H33: cancellation reason key for localized lookup
    val cancellationKey: String? = null,
    // Parity with iOS PremiumYogaCard / YogaDetailPopup — extra fields for the popup
    // and the 170dp detail card.
    val planets: String = "",
    val houses: String = "",
    val formation: String = "",
    val strength: Int = 0, // 0..100
    val outcome: String = "",
    val reductionReason: String = "",
    val isDosha: Boolean = false,
)

data class HomeDoshaStatus(
    val hasMangalDosha: Boolean = false,
    val hasKalasarpa: Boolean = false,
    val mangalSeverity: String = "",
    val kalasarpaType: String = "",
)

data class HomeLifeArea(
    val name: String,
    val emoji: String,
    val questions: List<String>,
    // R2-H10: status dot color
    val status: LifeAreaStatus = LifeAreaStatus.Neutral,
    // R2-H28: one-line brief description
    val briefDescription: String = "",
)

enum class LifeAreaStatus { Positive, Neutral, Caution }

data class HomeRichData(
    val transits: List<HomeTransit> = emptyList(),
    val dashaInfo: HomeDashaInfo? = null,
    val yogas: List<HomeYoga> = emptyList(),
    val doshas: HomeDoshaStatus = HomeDoshaStatus(),
    val lifeAreas: List<HomeLifeArea> = defaultLifeAreas(),
    // R2-H30 parity with iOS HomeView greeting + ascendant subtitle. Empty when unknown.
    val ascendantSign: String = "",
)

fun defaultLifeAreas(): List<HomeLifeArea> = listOf(
    HomeLifeArea(
        name = "Career",
        emoji = "💼",
        briefDescription = "Work, ambition and professional growth",
        questions = listOf(
            "What career path aligns with my chart?",
            "Will I get a promotion this year?",
            "Is this a good time to change jobs?",
        ),
    ),
    HomeLifeArea(
        name = "Love",
        emoji = "❤️",
        briefDescription = "Romance, relationships and partnerships",
        questions = listOf(
            "What does my chart say about romance?",
            "When will I meet my life partner?",
            "Is my relationship going in the right direction?",
        ),
    ),
    HomeLifeArea(
        name = "Finance",
        emoji = "💰",
        briefDescription = "Wealth, investments and financial flow",
        questions = listOf(
            "Will my financial situation improve this year?",
            "Is this a good time to invest?",
            "What planetary periods favor wealth accumulation?",
        ),
    ),
    HomeLifeArea(
        name = "Health",
        emoji = "🏥",
        briefDescription = "Vitality, wellbeing and body signals",
        questions = listOf(
            "What health areas should I watch?",
            "How can I improve my energy levels?",
            "Are there any health challenges in my chart?",
        ),
    ),
    HomeLifeArea(
        name = "Spiritual",
        emoji = "🕉️",
        briefDescription = "Inner growth, dharma and higher purpose",
        questions = listOf(
            "What is my spiritual path according to my chart?",
            "Which practices will deepen my spiritual growth?",
            "How does my 12th house influence my inner life?",
        ),
    ),
    HomeLifeArea(
        name = "Family",
        emoji = "👨‍👩‍👧",
        briefDescription = "Harmony, lineage and home environment",
        questions = listOf(
            "What does my chart say about family harmony?",
            "How are my family relationships influenced this year?",
            "What is the best time for family decisions?",
        ),
    ),
)
