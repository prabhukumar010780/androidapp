package com.destinyai.astrology.ui.compatibility

import com.destinyai.astrology.domain.model.KutaDetail

internal object AffirmationBuilder {

    val weightOrder = listOf("nadi", "bhakoot", "gana", "maitri", "yoni", "tara", "vashya", "varna")

    private val displayNames = mapOf(
        "nadi" to "Nadi", "bhakoot" to "Bhakoot", "gana" to "Gana",
        "maitri" to "Graha Maitri", "yoni" to "Yoni", "tara" to "Tara", "vashya" to "Vashya",
        "varna" to "Varna",
    )

    private val themes = mapOf(
        "nadi" to "health & progeny",
        "bhakoot" to "love & mutual respect",
        "gana" to "temperament & nature",
        "maitri" to "mental compatibility",
        "yoni" to "physical intimacy",
        "tara" to "destiny & fortune",
        "vashya" to "attraction & influence",
        "varna" to "spiritual alignment",
    )

    data class PerfectKoota(val displayName: String, val theme: String)

    fun affirmationText(kutas: List<KutaDetail>, adjustedScore: Int?, totalScore: Int): String {
        val score = adjustedScore ?: totalScore
        val perfect = topPerfectKootas(kutas)

        return when {
            perfect.size >= 3 -> {
                val top = perfect.take(3)
                val names = "${top[0].displayName}, ${top[1].displayName}, and ${top[2].displayName}"
                val t = "${top[0].theme}, ${top[1].theme}, and ${top[2].theme}"
                "$names all score perfectly — $t are exceptionally well aligned."
            }
            perfect.size == 2 -> {
                val names = "${perfect[0].displayName} and ${perfect[1].displayName}"
                val t = "${perfect[0].theme} and ${perfect[1].theme}"
                "$names both score perfectly — $t are strong foundations for this match."
            }
            perfect.size == 1 -> {
                val k = perfect[0]
                "${k.displayName} scores perfectly — strong ${k.theme}. Scoring $score/36, this is a solid match by Vedic standards."
            }
            else -> scoreTierSentence(score)
        }
    }

    private fun topPerfectKootas(kutas: List<KutaDetail>): List<PerfectKoota> {
        val result = mutableListOf<PerfectKoota>()
        for (key in weightOrder) {
            if (result.size >= 3) break
            val kuta = kutas.firstOrNull { it.key.lowercase() == key } ?: continue
            if (kuta.maxScore < 3 || kuta.score != kuta.maxScore) continue
            result.add(
                PerfectKoota(
                    displayName = displayNames[key] ?: key.replaceFirstChar { it.uppercase() },
                    theme = themes[key] ?: key,
                )
            )
        }
        return result
    }

    private fun scoreTierSentence(score: Int): String = when (score) {
        in 28..Int.MAX_VALUE -> "Scoring $score/36 — an excellent match by all Vedic standards."
        in 24..27 -> "Scoring $score/36 — a very good match with strong astrological foundations."
        in 20..23 -> "Scoring $score/36 — a good match with positive compatibility indicators."
        in 16..19 -> "Scoring $score/36 — an average match. Specific areas require mindful attention."
        in 12..15 -> "Scoring $score/36 — below average. Strong commitment and understanding needed."
        else -> "Scoring $score/36 — challenging match. Professional guidance is recommended."
    }
}

internal fun affirmationBuildText(
    kutas: List<KutaDetail>,
    adjustedScore: Int?,
    totalScore: Int,
): String = AffirmationBuilder.affirmationText(kutas, adjustedScore, totalScore)
