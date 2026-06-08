package com.destinyai.astrology.domain.model

import androidx.compose.ui.graphics.Color
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class KutaDetail(
    val key: String,
    val label: String,
    val icon: String,
    val score: Double,
    val maxScore: Double,
    val description: String = "",
    val doshaPresent: Boolean = false,
    val doshaCancelled: Boolean = false,
    val cancellationReason: String? = null,
    val adjustedScore: Double? = null,
    val boyValue: String? = null,
    val girlValue: String? = null,
    val doshaType: String? = null,
    val taraBoyToGirl: Int? = null,
    val taraGirlToBoy: Int? = null,
    val plainEnglishSummary: String? = null,
    // iOS parity (Views/Compatibility/Components/OrbitAshtakootView.swift orbitItems)
    // — full DoshaDetail enrichment surfaced into per-kuta data so the orbit view
    // can render without a separate doshaSummary lookup.
    val cancellationReasons: List<String>? = null,
    val classicalEffect: String? = null,
    val boyConstitution: String? = null,
    val girlConstitution: String? = null,
    val severity: String? = null,
    val housePositions: String? = null,
    val sadbhakootWarning: String? = null,
    val boyVashya: String? = null,
    val girlVashya: String? = null,
    val boyToGirlScore: Double? = null,
    val girlToBoyScore: Double? = null,
    val boyVarna: String? = null,
    val girlVarna: String? = null,
    val complementarityNote: String? = null,
    val boyValueDescription: String? = null,
    val girlValueDescription: String? = null,
) {
    val displayScore: Double get() = if (doshaPresent && doshaCancelled && adjustedScore != null) adjustedScore else score
    val percentage: Double get() = if (maxScore > 0) score / maxScore else 0.0

    val statusTier: Int get() = when {
        doshaPresent && doshaCancelled -> 1
        doshaPresent -> 2
        score >= maxScore -> 1
        score >= maxScore * 0.5 -> 0
        else -> 2
    }
}

data class CompatibilityResult(
    val totalScore: Int,
    val maxScore: Int,
    val kutas: List<KutaDetail>,
    val summary: String,
    val recommendation: String,
    val isRecommended: Boolean,
    val adjustedScore: Int?,
    val adjustedCategory: String?,
    val rejectionReasons: List<String>,
    val cancelledDoshasSummary: String?,
    val doshaSummary: DoshaSummaryModel?,
    val mangalBoyData: MangalDoshaModel?,
    val mangalGirlData: MangalDoshaModel?,
    val mangalCompatibility: Map<String, Any>?,
    val kalsarpaBoyData: KalaSarpaModel?,
    val kalsarpaGirlData: KalaSarpaModel?,
    val yogasBoyData: Map<String, Any>?,
    val yogasGirlData: Map<String, Any>?,
    val followUpSuggestions: List<String>,
    val boyName: String,
    val girlName: String,
    val boyDob: String?,
    val girlDob: String?,
    val boyCity: String?,
    val girlCity: String?,
    val boyYogaDoshaData: YogaDoshaData? = null,
    val girlYogaDoshaData: YogaDoshaData? = null,
    // iOS parity (CompatibilityView.swift:148-161): D1 chart data + ascendant
    // for ChartComparisonSheet on the result screen.
    val boyChartData: com.destinyai.astrology.ui.charts.ChartData? = null,
    val girlChartData: com.destinyai.astrology.ui.charts.ChartData? = null,
    val boyAscendant: String? = null,
    val girlAscendant: String? = null,
) {
    val scorePercentage: Double get() = if (maxScore > 0) totalScore.toDouble() / maxScore else 0.0
    val adjustedPercentage: Double get() = if (maxScore > 0 && adjustedScore != null) adjustedScore.toDouble() / maxScore else scorePercentage
}

data class DoshaSummaryModel(
    val totalDoshas: Int,
    val cancelledCount: Int,
    val activeCount: Int,
    val details: Map<String, DoshaDetailModel>,
)

data class DoshaDetailModel(
    val present: Boolean,
    val cancelled: Boolean,
    val reasonShort: String?,
    val reasonsAll: List<String>,
    val plainEnglishSummary: String?,
    val boyValue: String?,
    val girlValue: String?,
    // iOS parity (Models/SupportModels.swift DoshaDetail) — full transparency
    // payload from dosha_summary.details[<key>], piped into KutaDetail by the
    // mapper so the orbit view's enrichment matches iOS field-for-field.
    val doshaType: String? = null,
    val classicalEffect: String? = null,
    val boyConstitution: String? = null,
    val girlConstitution: String? = null,
    val severity: String? = null,
    val housePositions: String? = null,
    val sadbhakootWarning: String? = null,
    val taraBoyToGirl: Int? = null,
    val taraGirlToBoy: Int? = null,
    val boyVashya: String? = null,
    val girlVashya: String? = null,
    val boyToGirlScore: Double? = null,
    val girlToBoyScore: Double? = null,
    val boyVarna: String? = null,
    val girlVarna: String? = null,
    val complementarityNote: String? = null,
    val boyValueDescription: String? = null,
    val girlValueDescription: String? = null,
)

data class KalaSarpaModel(
    val isPresent: Boolean,
    val yogaName: String?,
    val doshaName: String?,
    val axis: String?,
    /**
     * Severity classification from backend payload `severity` field
     * (none|mild|moderate|severe). Renamed from `intensity` to align with
     * the iOS `KalaSarpaData.severity` parser key — single shared key prevents
     * platform drift if backend payload field names change.
     */
    val severity: String?,
    val lifeAreas: List<String>,
    val description: String?,
    val peakPeriod: String? = null,
    val remedies: List<String> = emptyList(),
    val completeness: String? = null,
    val planetsCount: Int? = null,
    val planetsInvolved: List<String>? = null,
    val analysisNotes: List<String>? = null,
)

data class MangalDoshaModel(
    val hasMangalDosha: Boolean,
    val severity: String?,
    val marsHouse: Int?,
    val exceptions: List<String>,
    val description: String?,
    val remedies: List<String> = emptyList(),
    val explanation: String? = null,
    val intensityFactors: Map<String, Boolean>? = null,
    val exceptionCount: Int? = null,
    val isCancelledByExceptions: Boolean = false,
    val baseScore: Double? = null,
    val doshaFrom: Map<String, Any>? = null,
) {
    val isCancelled: Boolean get() = isCancelledByExceptions || (exceptions.isNotEmpty() && !hasMangalDosha)
    val isReduced: Boolean get() = severity?.lowercase() == "mild"
    val activeExceptions: List<String> get() = exceptions
    val activeIntensityFactors: List<String> get() =
        intensityFactors?.entries?.filter { it.value }?.map { it.key } ?: emptyList()

    val baseSeverityLabel: String get() {
        val base = baseScore ?: return "Unknown"
        return when {
            base >= 0.75 -> "Severe"
            base >= 0.50 -> "High"
            base >= 0.25 -> "Moderate"
            base > 0 -> "Mild"
            else -> "None"
        }
    }

    val exceptionImpactSummary: String? get() {
        val excCount = exceptions.size
        if (excCount == 0) return null
        val plural = if (excCount == 1) "exception" else "exceptions"
        return when {
            isCancelled -> "$excCount $plural fully cancelled this dosha"
            baseScore != null && baseScore > (if (severity?.lowercase() == "mild") 0.25 else 0.0) ->
                "$excCount $plural reduced severity from $baseSeverityLabel to ${severity?.replaceFirstChar { it.uppercase() } ?: "reduced"}"
            else -> "$excCount $plural reduced this dosha"
        }
    }

    val activeDoshaSourcesDisplay: String? get() {
        val from = doshaFrom ?: return null
        val parts = mutableListOf<String>()
        if (from["lagna"] == true) {
            val house = from["mars_house_from_lagna"]
            parts += if (house != null) "House $house (from Lagna)" else "Lagna"
        }
        if (from["moon"] == true) {
            val house = from["mars_house_from_moon"]
            parts += if (house != null) "House $house (from Moon)" else "Moon"
        }
        if (from["venus"] == true) {
            val house = from["mars_house_from_venus"]
            parts += if (house != null) "House $house (from Venus)" else "Venus"
        }
        return if (parts.isEmpty()) null else parts.joinToString(" • ")
    }
}

data class CompatibilityHistoryItem(
    val sessionId: String = UUID.randomUUID().toString(),
    val timestampMs: Long = System.currentTimeMillis(),
    val boyName: String,
    val boyDob: String,
    val boyCity: String,
    val girlName: String,
    val girlDob: String,
    val girlCity: String,
    val totalScore: Int,
    val maxScore: Int,
    val isPinned: Boolean = false,
    val comparisonGroupId: String? = null,
    val partnerIndex: Int? = null,
    val boyTime: String = "",
    val girlTime: String = "",
    val chatMessages: List<CompatChatMessageData> = emptyList(),
    val result: CompatibilityResult? = null,
) {
    val displayTitle: String get() = "$boyName & $girlName"

    val displayDate: String get() {
        val sdf = SimpleDateFormat("MMM d, yyyy 'at' h:mm a", Locale.getDefault())
        return sdf.format(Date(timestampMs))
    }

    val scorePercentage: Double get() =
        if (maxScore > 0) totalScore.toDouble() / maxScore * 100 else 0.0
}

data class CompatChatMessageData(
    val id: String = UUID.randomUUID().toString(),
    val content: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val type: String = "text",
    val executionTimeMs: Long? = null,
)

data class ComparisonGroup(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val userName: String,
    val items: List<CompatibilityHistoryItem>,
) {
    val bestMatch: CompatibilityHistoryItem? get() = items.maxByOrNull { it.totalScore }
    val sortedItems: List<CompatibilityHistoryItem> get() = items.sortedByDescending { it.totalScore }
    val averageScore: Double get() = if (items.isEmpty()) 0.0 else items.map { it.totalScore.toDouble() }.average()
    val partnerCount: Int get() = items.size

    val displayTitle: String get() = when {
        items.size == 1 -> items.first().displayTitle
        else -> "$userName & ${items.size} partners"
    }

    val displayDate: String get() {
        val sdf = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }
}

data class PartnerData(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val dob: String = "",
    val time: String = "",
    val city: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    // iOS parity (PartnerData.swift) — when partner was loaded from a saved
    // birth chart, retain the source partner profile id so the picker can
    // exclude already-selected partners.
    val savedProfileId: String? = null,
) {
    val isComplete: Boolean get() = name.isNotBlank() && city.isNotBlank()
}

data class ComparisonResult(
    val id: String = UUID.randomUUID().toString(),
    val partner: PartnerData,
    val totalScore: Int,
    val maxScore: Int,
    val overallScore: Int,
    val isRecommended: Boolean,
    val adjustedScore: Int,
    val summary: String = "",
    val kutaDetails: Map<String, KutaDetail> = emptyMap(),
    // iOS parity (PartnerData.swift:111-129): one-liner verdict and structured
    // mangal compatibility data surfaced from analysis_data.joint so the
    // comparison overview footer + Manglik row mirror iOS behaviour.
    val oneLiner: String? = null,
    val mangalCompatibility: Map<String, Any>? = null,
    val rejectionReasons: List<String> = emptyList(),
) {
    /** iOS parity (PartnerData.swift:124-129) — short status label shown next to the analysis dot. */
    val statusLabel: String
        get() = if (isRecommended) "Recommended" else "Not Recommended"
}

// ── YogaItem — exact iOS parity ────────────────────────────────────────────────

data class YogaItem(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val yogaKey: String? = null,
    val status: String = "A",
    val strengthRaw: Double = 0.0,
    val category: String? = null,
    val planets: String? = null,
    val houses: String? = null,
    val formation: String? = null,
    val outcome: String? = null,
    val reason: String? = null,
    val isDosha: Boolean? = null,
    // Legacy field — kept for backward compat with raw-map parsing path
    val description: String = "",
) {
    val strength: Double get() = strengthRaw
    val isActive: Boolean get() = status != "C" && status != "R"
    val displayName: String get() = name
    val localizedName: String get() = name
    val localizedOutcome: String? get() = outcome
    val localizedFormation: String? get() = formation

    val uniquePlanets: String? get() = planets?.let { p ->
        p.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            .distinct().joinToString(", ").takeIf { it.isNotEmpty() }
    }

    val uniqueHouses: String? get() = houses?.let { h ->
        h.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            .distinct().joinToString(", ").takeIf { it.isNotEmpty() }
    }

    val statusLabel: String get() = when (status) {
        "A" -> "Active"
        "R" -> "Reduced"
        "C" -> "Cancelled"
        else -> "Active"
    }
}

// ── DestinyTileType — exact iOS parity ────────────────────────────────────────

enum class DestinyTileType {
    WEALTH, CAREER, LOVE, FAMILY, WISDOM, HEALTH, DOSHA;

    val icon: String get() = when (this) {
        WEALTH -> "💰"
        CAREER -> "👑"
        LOVE -> "💕"
        FAMILY -> "🏡"
        WISDOM -> "📚"
        HEALTH -> "🌿"
        DOSHA -> "⚠️"
    }

    val label: String get() = when (this) {
        WEALTH -> "Wealth"
        CAREER -> "Career"
        LOVE -> "Love"
        FAMILY -> "Family"
        WISDOM -> "Wisdom"
        HEALTH -> "Health"
        DOSHA -> "Doshas"
    }

    /**
     * Per-tile accent color — mirrors iOS DestinyTileType.accentColor.
     * Used by MagicTabbar for the active indicator dot fill.
     */
    val accentColor: Color get() = when (this) {
        WEALTH -> Color(0xFFD4AF37) // gold
        CAREER -> Color(0xFFAF52DE) // SwiftUI .purple
        LOVE -> Color(0xFFFF2D55)   // SwiftUI .pink
        FAMILY -> Color(0xFFFF9500) // SwiftUI .orange
        WISDOM -> Color(0xFF32ADE6) // SwiftUI .cyan
        HEALTH -> Color(0xFF34C759) // SwiftUI .green
        DOSHA -> Color(0xFFFF3B30)  // iOS AppTheme.Colors.error
    }

    companion object {
        val topicTiles: List<DestinyTileType> = listOf(WEALTH, CAREER, LOVE, FAMILY, WISDOM, HEALTH)

        fun from(category: String?): DestinyTileType = when (category?.lowercase()?.trim()) {
            "wealth", "dhana", "lakshmi", "kubera", "finance", "money" -> WEALTH
            "career", "raja", "profession", "status", "power", "authority",
            "pancha mahapurusha" -> CAREER
            "love", "relationship", "marriage", "partner", "spouse", "venus",
            "attraction" -> LOVE
            "family", "children", "mother", "father", "home", "parent",
            "putra" -> FAMILY
            "wisdom", "knowledge", "education", "guru", "spiritual",
            "spirituality" -> WISDOM
            "health", "longevity", "vitality", "ayush" -> HEALTH
            "dosha", "obstacle", "malefic" -> DOSHA
            else -> WEALTH
        }
    }
}

// ── YogaDoshaData — exact iOS parity ──────────────────────────────────────────

data class YogaDoshaData(
    val yogas: List<YogaItem>? = null,
    val doshas: List<YogaItem>? = null,
) {
    val allItems: List<YogaItem> get() = (yogas ?: emptyList()) + (doshas ?: emptyList())

    val activeYogaCount: Int get() = yogas?.count { it.isActive } ?: 0
    val activeDoshaCount: Int get() = doshas?.count { it.isActive } ?: 0
    val activeDoshas: List<YogaItem> get() = doshas?.filter { it.isActive } ?: emptyList()

    fun items(for_: DestinyTileType): List<YogaItem> = when (for_) {
        DestinyTileType.DOSHA -> allItems.filter { it.isDosha == true }
        else -> (yogas ?: emptyList()).filter { item ->
            item.category?.let { DestinyTileType.from(it) == for_ } == true
        }
    }

    fun activeItems(for_: DestinyTileType): List<YogaItem> = items(for_).filter { it.isActive }
    fun blockedItems(for_: DestinyTileType): List<YogaItem> = items(for_).filter { !it.isActive }

    fun grouped(): Map<DestinyTileType, List<YogaItem>> =
        DestinyTileType.topicTiles.associateWith { tile -> items(tile) }
}

// ── VariantCounter — deduplicates yoga variants (e.g. Daridra I/II/III) ───────

object VariantCounter {
    private val romanSuffix = Regex("""\s+[IVX]+$""")

    fun calculatePositions(for_: List<YogaItem>): List<YogaItem> {
        val groups = mutableMapOf<String, MutableList<YogaItem>>()
        for (item in for_) {
            val base = item.name.replace(romanSuffix, "").trim()
            groups.getOrPut(base) { mutableListOf() }.add(item)
        }
        return groups.entries.map { (base, variants) ->
            if (variants.size == 1) {
                variants.first()
            } else {
                val strongest = variants.maxByOrNull { it.strengthRaw } ?: variants.first()
                strongest.copy(
                    name = base,
                    yogaKey = strongest.yogaKey,
                )
            }
        }
    }
}

enum class AnalysisStep {
    CALCULATING_CHARTS,
    ASHTAKOOT_MATCHING,
    MANGAL_DOSHA,
    COLLECTING_YOGAS,
    GENERATING_ANALYSIS,
    COMPLETE;

    /**
     * iOS parity (CompatibilityStreamingView.swift:77-106 + Localizable.strings:1430-1435):
     * the streaming modal labels are localized — DO NOT use the hard-coded `title` getter
     * below in UI code; use [titleRes] with `stringResource()` instead. The `title` getter
     * is retained only as a sentinel string for tests and non-Composable call sites.
     */
    @get:androidx.annotation.StringRes
    val titleRes: Int get() = when (this) {
        CALCULATING_CHARTS -> com.destinyai.astrology.R.string.analysis_step_mapping_charts
        ASHTAKOOT_MATCHING -> com.destinyai.astrology.R.string.analysis_step_ashtakoot
        MANGAL_DOSHA -> com.destinyai.astrology.R.string.analysis_step_mangal
        COLLECTING_YOGAS -> com.destinyai.astrology.R.string.analysis_step_yogas
        GENERATING_ANALYSIS -> com.destinyai.astrology.R.string.analysis_step_generating
        COMPLETE -> com.destinyai.astrology.R.string.analysis_step_ready
    }

    val title: String get() = when (this) {
        CALCULATING_CHARTS -> "Mapping birth charts"
        ASHTAKOOT_MATCHING -> "Calculating astrological compatibility"
        MANGAL_DOSHA -> "Checking Manglik compatibility"
        COLLECTING_YOGAS -> "Evaluating yogas and doshas"
        GENERATING_ANALYSIS -> "Preparing your compatibility insights"
        COMPLETE -> "Your compatibility insights are ready"
    }

    val icon: String get() = when (this) {
        CALCULATING_CHARTS -> "globe"
        ASHTAKOOT_MATCHING -> "pie_chart"
        MANGAL_DOSHA -> "warning"
        COLLECTING_YOGAS -> "sparkle"
        GENERATING_ANALYSIS -> "psychology"
        COMPLETE -> "check_circle"
    }
}
