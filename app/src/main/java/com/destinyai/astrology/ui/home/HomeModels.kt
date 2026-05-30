package com.destinyai.astrology.ui.home

data class HomeTransit(
    val planet: String,
    val sign: String,
    val influence: String,
    val isFavorable: Boolean,
)

data class HomeDashaInfo(
    val mahadasha: String,
    val antardasha: String,
    val endsAt: String,
)

data class HomeYoga(
    val name: String,
    val description: String,
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
)

data class HomeRichData(
    val transits: List<HomeTransit> = emptyList(),
    val dashaInfo: HomeDashaInfo? = null,
    val yogas: List<HomeYoga> = emptyList(),
    val doshas: HomeDoshaStatus = HomeDoshaStatus(),
    val lifeAreas: List<HomeLifeArea> = defaultLifeAreas(),
)

fun defaultLifeAreas(): List<HomeLifeArea> = listOf(
    HomeLifeArea(
        name = "Career",
        emoji = "💼",
        questions = listOf(
            "What career path aligns with my chart?",
            "Will I get a promotion this year?",
            "Is this a good time to change jobs?",
        ),
    ),
    HomeLifeArea(
        name = "Love",
        emoji = "❤️",
        questions = listOf(
            "What does my chart say about romance?",
            "When will I meet my life partner?",
            "Is my relationship going in the right direction?",
        ),
    ),
    HomeLifeArea(
        name = "Finance",
        emoji = "💰",
        questions = listOf(
            "Will my financial situation improve this year?",
            "Is this a good time to invest?",
            "What planetary periods favor wealth accumulation?",
        ),
    ),
    HomeLifeArea(
        name = "Health",
        emoji = "🏥",
        questions = listOf(
            "What health areas should I watch?",
            "How can I improve my energy levels?",
            "Are there any health challenges in my chart?",
        ),
    ),
    HomeLifeArea(
        name = "Spiritual",
        emoji = "🕉️",
        questions = listOf(
            "What is my spiritual path according to my chart?",
            "Which practices will deepen my spiritual growth?",
            "How does my 12th house influence my inner life?",
        ),
    ),
    HomeLifeArea(
        name = "Family",
        emoji = "👨‍👩‍👧",
        questions = listOf(
            "What does my chart say about family harmony?",
            "How are my family relationships influenced this year?",
            "What is the best time for family decisions?",
        ),
    ),
)
