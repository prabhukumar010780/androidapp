package com.destinyai.astrology.ui.compatibility

import android.content.Context
import com.destinyai.astrology.R
import com.destinyai.astrology.domain.model.KutaDetail

internal object AffirmationBuilder {

    // Ordered weight list (descending). iOS parity (AffirmationBuilder.swift:42):
    // Varna (max=1) is intentionally absent — 7 kootas, not 8.
    val weightOrder = listOf("nadi", "bhakoot", "gana", "maitri", "yoni", "tara", "vashya")

    // Sanskrit proper names — same in all languages, not localized (iOS parity).
    private val displayNames = mapOf(
        "nadi" to "Nadi", "bhakoot" to "Bhakoot", "gana" to "Gana",
        "maitri" to "Graha Maitri", "yoni" to "Yoni", "tara" to "Tara", "vashya" to "Vashya",
    )

    // iOS parity (AffirmationBuilder.swift:54-62): themes are localized via kuta_theme_* keys.
    private fun themeFor(context: Context, key: String): String = when (key) {
        "nadi" -> context.getString(R.string.kuta_theme_health_progeny)
        "bhakoot" -> context.getString(R.string.kuta_theme_love)
        "gana" -> context.getString(R.string.kuta_theme_temperament)
        "maitri" -> context.getString(R.string.kuta_theme_mental)
        "yoni" -> context.getString(R.string.kuta_theme_intimacy)
        "tara" -> context.getString(R.string.kuta_theme_destiny)
        "vashya" -> context.getString(R.string.kuta_theme_attraction)
        else -> key
    }

    data class PerfectKoota(val displayName: String, val theme: String)

    fun affirmationText(context: Context, kutas: List<KutaDetail>, adjustedScore: Int?, totalScore: Int): String {
        val score = adjustedScore ?: totalScore
        val perfect = topPerfectKootas(context, kutas)

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
            else -> scoreTierSentence(context, score)
        }
    }

    private fun topPerfectKootas(context: Context, kutas: List<KutaDetail>): List<PerfectKoota> {
        val result = mutableListOf<PerfectKoota>()
        for (key in weightOrder) {
            if (result.size >= 3) break
            val kuta = kutas.firstOrNull { it.key.lowercase() == key } ?: continue
            if (kuta.maxScore < 3 || kuta.score != kuta.maxScore) continue
            result.add(
                PerfectKoota(
                    displayName = displayNames[key] ?: key.replaceFirstChar { it.uppercase() },
                    theme = themeFor(context, key),
                )
            )
        }
        return result
    }

    // iOS parity (AffirmationBuilder.swift:86-102): tier sentences via ashtakoot_tier_* keys.
    private fun scoreTierSentence(context: Context, score: Int): String {
        val s = score.toString()
        val res = when (score) {
            in 28..Int.MAX_VALUE -> R.string.ashtakoot_tier_excellent
            in 24..27 -> R.string.ashtakoot_tier_very_good
            in 20..23 -> R.string.ashtakoot_tier_good
            in 16..19 -> R.string.ashtakoot_tier_average
            in 12..15 -> R.string.ashtakoot_tier_below_average
            else -> R.string.ashtakoot_tier_low
        }
        return context.getString(res, s)
    }
}

internal fun affirmationBuildText(
    context: Context,
    kutas: List<KutaDetail>,
    adjustedScore: Int?,
    totalScore: Int,
): String = AffirmationBuilder.affirmationText(context, kutas, adjustedScore, totalScore)
