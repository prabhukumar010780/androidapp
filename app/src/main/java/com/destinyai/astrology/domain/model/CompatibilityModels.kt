package com.destinyai.astrology.domain.model

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
) {
    val displayScore: Double get() = if (doshaPresent && doshaCancelled && adjustedScore != null) adjustedScore else score
    val percentage: Double get() = if (maxScore > 0) score / maxScore else 0.0

    // 0 = gold, 1 = success/green, 2 = error/red
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
)

data class KalaSarpaModel(
    val isPresent: Boolean,
    val yogaName: String?,
    val doshaName: String?,
    val axis: String?,
    val intensity: String?,
    val lifeAreas: List<String>,
    val description: String?,
)

data class MangalDoshaModel(
    val hasMangalDosha: Boolean,
    val severity: String?,
    val marsHouse: Int?,
    val exceptions: List<String>,
    val description: String?,
)

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
) {
    val displayTitle: String get() = "$boyName & $girlName"

    val displayDate: String get() {
        val sdf = SimpleDateFormat("MMM d, yyyy 'at' h:mm a", Locale.getDefault())
        return sdf.format(Date(timestampMs))
    }

    val scorePercentage: Double get() =
        if (maxScore > 0) totalScore.toDouble() / maxScore * 100 else 0.0
}
