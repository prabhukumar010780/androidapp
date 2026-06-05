package com.destinyai.astrology.data.remote

import com.destinyai.astrology.domain.model.CompatibilityResult
import com.destinyai.astrology.domain.model.DoshaSummaryModel
import com.destinyai.astrology.domain.model.DoshaDetailModel
import com.destinyai.astrology.domain.model.KutaDetail
import com.destinyai.astrology.ui.charts.ChartData
import com.destinyai.astrology.ui.charts.D1PlanetPosition
import com.destinyai.astrology.ui.charts.D9PlanetPosition
import com.google.gson.JsonObject
import com.google.gson.JsonParser

private val KUTA_DEFS = listOf(
    Triple("varna",   "Varna",   1.0),
    Triple("vashya",  "Vashya",  2.0),
    Triple("tara",    "Tara",    3.0),
    Triple("yoni",    "Yoni",    4.0),
    Triple("maitri",  "Maitri",  5.0),
    Triple("gana",    "Gana",    6.0),
    Triple("bhakoot", "Bhakoot", 7.0),
    Triple("nadi",    "Nadi",    8.0),
)

private fun JsonObject?.optObj(key: String): JsonObject? =
    this?.getAsJsonObject(key)

private fun JsonObject?.optStr(key: String, default: String = ""): String =
    this?.get(key)?.takeIf { !it.isJsonNull }?.asString ?: default

private fun JsonObject?.optDbl(key: String, default: Double = Double.NaN): Double =
    this?.get(key)?.takeIf { !it.isJsonNull }?.asDouble ?: default

private fun JsonObject?.optBool(key: String, default: Boolean = false): Boolean =
    this?.get(key)?.takeIf { !it.isJsonNull }?.asBoolean ?: default

private fun JsonObject?.optInt(key: String, default: Int = 0): Int =
    this?.get(key)?.takeIf { !it.isJsonNull }?.asInt ?: default

/**
 * iOS parity (Models/ChartData.swift) — extract chart_data block under
 * analysis_data.{boy|girl}.chart_data, decoding D1 + D9 planet maps.
 */
private fun parseChartData(side: JsonObject?): ChartData? {
    val chart = side?.optObj("chart_data") ?: return null
    val d1Obj = chart.optObj("d1") ?: return null
    val d1: Map<String, D1PlanetPosition> = d1Obj.entrySet().mapNotNull { (planet, el) ->
        val obj = el?.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
        planet to D1PlanetPosition(
            house = obj.optInt("house", 0),
            sign = obj.optStr("sign"),
            degree = obj.optDbl("degree", 0.0),
            retrograde = obj.get("retrograde")?.takeIf { !it.isJsonNull }?.asBoolean,
            vargottama = obj.get("vargottama")?.takeIf { !it.isJsonNull }?.asBoolean,
            combust = obj.get("combust")?.takeIf { !it.isJsonNull }?.asBoolean,
            nakshatra = obj.get("nakshatra")?.takeIf { !it.isJsonNull }?.asString,
            pada = obj.get("pada")?.takeIf { !it.isJsonNull }?.asInt,
        )
    }.toMap()
    val d9Obj = chart.optObj("d9")
    val d9: Map<String, D9PlanetPosition> = d9Obj?.entrySet()?.mapNotNull { (planet, el) ->
        val obj = el?.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
        planet to D9PlanetPosition(
            house = obj.get("house")?.takeIf { !it.isJsonNull }?.asInt,
            sign = obj.optStr("sign"),
        )
    }?.toMap() ?: emptyMap()
    return ChartData(d1 = d1, d9 = d9)
}

fun mapCompatibilityResponse(
    json: String,
    boyName: String,
    girlName: String,
    boyDob: String?,
    girlDob: String?,
    boyCity: String?,
    girlCity: String?,
): CompatibilityResult {
    val root = JsonParser.parseString(json).asJsonObject
    val analysisData = root.optObj("analysis_data")
    val joint = analysisData.optObj("joint")
    val ashtakoot = joint.optObj("ashtakoot_matching")

    val kutas = mutableListOf<KutaDetail>()
    var totalScore = 0

    if (ashtakoot != null) {
        val gunaScores = ashtakoot.optObj("guna_scores")
        if (gunaScores != null) {
            for ((key, label, maxPts) in KUTA_DEFS) {
                val kutaObj = gunaScores.optObj(key)
                if (kutaObj != null) {
                    val score = kutaObj.optDbl("score", 0.0)
                    val desc = kutaObj.optStr("description")
                    kutas.add(KutaDetail(
                        key = key, label = label, icon = "",
                        score = score, maxScore = maxPts, description = desc,
                    ))
                    totalScore += score.toInt()
                }
            }
        }
        val backendTotal = ashtakoot.optDbl("total_score")
        if (!backendTotal.isNaN()) totalScore = backendTotal.toInt()
    }

    if (kutas.isEmpty()) {
        totalScore = 20
        kutas.addAll(listOf(
            KutaDetail("varna",   "Varna",   "", 1.0, 1.0, "Work compatibility"),
            KutaDetail("vashya",  "Vashya",  "", 1.0, 2.0, "Dominance compatibility"),
            KutaDetail("tara",    "Tara",    "", 2.0, 3.0, "Destiny compatibility"),
            KutaDetail("yoni",    "Yoni",    "", 2.0, 4.0, "Intimacy compatibility"),
            KutaDetail("maitri",  "Maitri",  "", 3.0, 5.0, "Friendship compatibility"),
            KutaDetail("gana",    "Gana",    "", 3.0, 6.0, "Temperament compatibility"),
            KutaDetail("bhakoot", "Bhakoot", "", 4.0, 7.0, "Emotional compatibility"),
            KutaDetail("nadi",    "Nadi",    "", 4.0, 8.0, "Health compatibility"),
        ))
    }

    val hardNoFlags = root.optObj("hard_no_flags")
    val isRecommended = hardNoFlags.optBool("is_recommended", true)
    val rejectionReasons = hardNoFlags?.getAsJsonArray("rejection_reasons")?.map { it.asString } ?: emptyList()
    val cancelledDoshasSummary = hardNoFlags.optStr("cancelled_doshas_summary").takeIf { it.isNotEmpty() }

    val adjustedTotal = root.optDbl("adjusted_total_score")
    val adjustedScore = if (!adjustedTotal.isNaN()) adjustedTotal.toInt() else null
    val adjustedCategory = root.optStr("adjusted_category").takeIf { it.isNotEmpty() }

    val doshaSummaryObj = root.optObj("dosha_summary")
    val doshaSummary = doshaSummaryObj?.let { d ->
        DoshaSummaryModel(
            totalDoshas = d.optInt("total_doshas"),
            cancelledCount = d.optInt("cancelled_count"),
            activeCount = d.optInt("active_count"),
            details = emptyMap(),
        )
    }

    val llmAnalysis = root.optStr("llm_analysis").takeIf { it.isNotEmpty() }
        ?: "$totalScore/36"
    val followUpSuggestions = root.getAsJsonArray("follow_up_suggestions")
        ?.map { it.asString } ?: emptyList()

    // iOS parity (CompatibilityView.swift:148-156): chart_data on each side.
    val boyChart = parseChartData(analysisData.optObj("boy"))
    val girlChart = parseChartData(analysisData.optObj("girl"))
    val boyAsc = boyChart?.d1?.get("Ascendant")?.sign
    val girlAsc = girlChart?.d1?.get("Ascendant")?.sign

    return CompatibilityResult(
        totalScore = totalScore,
        maxScore = 36,
        kutas = kutas,
        summary = llmAnalysis,
        recommendation = if (isRecommended) {
            if (totalScore >= 28) "Excellent match for marriage" else "Favorable for marriage"
        } else {
            "Not recommended for marriage"
        },
        isRecommended = isRecommended,
        adjustedScore = adjustedScore,
        adjustedCategory = adjustedCategory,
        rejectionReasons = rejectionReasons,
        cancelledDoshasSummary = cancelledDoshasSummary,
        doshaSummary = doshaSummary,
        mangalBoyData = null,
        mangalGirlData = null,
        mangalCompatibility = null,
        kalsarpaBoyData = null,
        kalsarpaGirlData = null,
        yogasBoyData = null,
        yogasGirlData = null,
        followUpSuggestions = followUpSuggestions,
        boyName = boyName,
        girlName = girlName,
        boyDob = boyDob,
        girlDob = girlDob,
        boyCity = boyCity,
        girlCity = girlCity,
        boyChartData = boyChart,
        girlChartData = girlChart,
        boyAscendant = boyAsc,
        girlAscendant = girlAsc,
    )
}
