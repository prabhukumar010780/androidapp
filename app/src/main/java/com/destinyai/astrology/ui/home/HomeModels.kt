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
        name = "Relationship",
        emoji = "❤️",
        briefDescription = "Romance, partnerships and emotional bonds",
        questions = listOf(
            "What does my chart say about my love life?",
            "When will I meet my life partner?",
            "How is my 7th house influencing my relationships?",
        ),
    ),
    HomeLifeArea(
        name = "Finance",
        emoji = "💰",
        briefDescription = "Wealth, income and financial flow",
        questions = listOf(
            "Will my financial situation improve this year?",
            "What planetary periods favor wealth accumulation?",
            "How is my 2nd and 11th house supporting income?",
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
        name = "Family",
        emoji = "👨‍👩‍👧",
        briefDescription = "Harmony, lineage and home environment",
        questions = listOf(
            "What does my chart say about family harmony?",
            "How are my family relationships influenced this year?",
            "What is the best time for family decisions?",
        ),
    ),
    HomeLifeArea(
        name = "Education",
        emoji = "📚",
        briefDescription = "Learning, study and intellectual growth",
        questions = listOf(
            "What does my chart say about higher education?",
            "Is this a favorable time for learning a new skill?",
            "How is my 5th house supporting my studies?",
        ),
    ),
    HomeLifeArea(
        name = "Investment",
        emoji = "📈",
        briefDescription = "Markets, assets and long-term growth",
        questions = listOf(
            "Is this a good time to invest?",
            "Which sectors are favored by my planetary periods?",
            "What does my chart say about long-term wealth growth?",
        ),
    ),
    HomeLifeArea(
        name = "Sudden Events",
        emoji = "⭐",
        briefDescription = "Unexpected turns, breakthroughs and shocks",
        questions = listOf(
            "Are any sudden changes indicated in my chart?",
            "How is Rahu/Ketu influencing unexpected events?",
            "What does my 8th house say about transformation?",
        ),
    ),
)
