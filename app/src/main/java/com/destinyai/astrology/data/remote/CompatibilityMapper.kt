package com.destinyai.astrology.data.remote

import com.destinyai.astrology.domain.model.CompatibilityResult
import com.destinyai.astrology.domain.model.DoshaSummaryModel
import com.destinyai.astrology.domain.model.DoshaDetailModel
import com.destinyai.astrology.domain.model.KalaSarpaModel
import com.destinyai.astrology.domain.model.KutaDetail
import com.destinyai.astrology.domain.model.MangalDoshaModel
import com.destinyai.astrology.domain.model.YogaDoshaData
import com.destinyai.astrology.domain.model.YogaItem
import com.destinyai.astrology.ui.charts.ChartData
import com.destinyai.astrology.ui.charts.D1PlanetPosition
import com.destinyai.astrology.ui.charts.D9PlanetPosition
import com.google.gson.JsonArray
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

private fun JsonObject?.optStr(key: String, default: String = ""): String {
    val el = this?.get(key)?.takeIf { !it.isJsonNull } ?: return default
    // Backend occasionally returns a nested object where a string was expected
    // (e.g. plain_english_summary became a structured object in newer versions).
    // Be defensive and stringify rather than crashing the entire mapper with
    // UnsupportedOperationException — surface the raw JSON instead of "Failed".
    return if (el.isJsonPrimitive) el.asString else el.toString()
}

private fun JsonObject?.optDbl(key: String, default: Double = Double.NaN): Double {
    val el = this?.get(key)?.takeIf { !it.isJsonNull } ?: return default
    return if (el.isJsonPrimitive) try { el.asDouble } catch (_: Exception) { default } else default
}

private fun JsonObject?.optBool(key: String, default: Boolean = false): Boolean {
    val el = this?.get(key)?.takeIf { !it.isJsonNull } ?: return default
    return if (el.isJsonPrimitive) try { el.asBoolean } catch (_: Exception) { default } else default
}

private fun JsonObject?.optInt(key: String, default: Int = 0): Int {
    val el = this?.get(key)?.takeIf { !it.isJsonNull } ?: return default
    return if (el.isJsonPrimitive) try { el.asInt } catch (_: Exception) { default } else default
}

/**
 * Null-safe array accessor — returns null if the key is missing OR explicitly null.
 *
 * The default Gson `JsonObject.getAsJsonArray(key)` throws `ClassCastException` when
 * the value is `JsonNull` (the backend sends `"rejection_reasons": null` and similar
 * for happy paths), which crashes the mapper and surfaces as "Failed to parse" in
 * the UI. Use this everywhere instead.
 */
private fun JsonObject?.optArr(key: String): JsonArray? {
    val el = this?.get(key) ?: return null
    if (el.isJsonNull) return null
    return el.takeIf { it.isJsonArray }?.asJsonArray
}

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

/**
 * Parse yoga + dosha lists from analysis_data.{boy|girl}.raw.yogas.
 *
 * Backend shape (verified live, Jun 2026):
 *   {"yogas": [YogaItem...], "doshas": [YogaItem...]}
 * where each YogaItem has: name, yoga_key, category, planets, houses, status,
 * strength, is_dosha, formation, outcome, reason.
 */
private fun parseYogaItem(obj: JsonObject): YogaItem = YogaItem(
    name = obj.optStr("name"),
    yogaKey = obj.optStr("yoga_key").takeIf { it.isNotEmpty() },
    status = obj.optStr("status", "A").takeIf { it.isNotEmpty() } ?: "A",
    strengthRaw = obj.optDbl("strength", 0.0).takeIf { !it.isNaN() } ?: 0.0,
    category = obj.optStr("category").takeIf { it.isNotEmpty() },
    planets = obj.optStr("planets").takeIf { it.isNotEmpty() },
    houses = obj.optStr("houses").takeIf { it.isNotEmpty() },
    formation = obj.optStr("formation").takeIf { it.isNotEmpty() },
    outcome = obj.optStr("outcome").takeIf { it.isNotEmpty() },
    reason = obj.optStr("reason").takeIf { it.isNotEmpty() },
    isDosha = obj.get("is_dosha")?.takeIf { !it.isJsonNull }?.asBoolean,
)

private fun parseYogaDoshaData(side: JsonObject?): YogaDoshaData? {
    val raw = side?.optObj("raw") ?: return null
    val yogasRoot = raw.optObj("yogas") ?: return null
    val yogasArr = yogasRoot.optArr("yogas")?.mapNotNull {
        it.takeIf { e -> e.isJsonObject }?.asJsonObject?.let(::parseYogaItem)
    }
    val doshasArr = yogasRoot.optArr("doshas")?.mapNotNull {
        it.takeIf { e -> e.isJsonObject }?.asJsonObject?.let(::parseYogaItem)
    }
    if (yogasArr.isNullOrEmpty() && doshasArr.isNullOrEmpty()) return null
    return YogaDoshaData(yogas = yogasArr, doshas = doshasArr)
}

/**
 * Parse Mangal Dosha data from analysis_data.{boy|girl}.raw.mangal_dosha.
 *
 * Backend shape (verified live, Jun 2026):
 *   {"has_mangal_dosha": bool, "severity": "Mild"|"Moderate"|...,
 *    "dosha_score": float, "mars_position": {"house": int, ...},
 *    "exceptions": {<key>: bool, ...}, "intensity_factors": {<key>: bool, ...},
 *    "exception_count": int, "intensity_factor_count": int, "explanation": str,
 *    "remedies": [str...], "dosha_from": {"lagna": bool, "moon": bool, ...}}
 *
 * The exceptions/intensityFactors maps in the model expect ONLY the keys whose
 * value is true (parity with how iOS surfaces them in StatusPersonCard etc.).
 */
private fun parseMangalDosha(side: JsonObject?): MangalDoshaModel? {
    val mangal = side?.optObj("raw")?.optObj("mangal_dosha") ?: return null

    val activeExceptions = mangal.optObj("exceptions")?.entrySet()
        ?.mapNotNull { (k, v) ->
            if (v != null && !v.isJsonNull && v.isJsonPrimitive && v.asJsonPrimitive.isBoolean && v.asBoolean) k else null
        } ?: emptyList()
    val intensityFactors = mangal.optObj("intensity_factors")?.entrySet()
        ?.mapNotNull { (k, v) ->
            if (v != null && !v.isJsonNull && v.isJsonPrimitive && v.asJsonPrimitive.isBoolean) k to v.asBoolean else null
        }?.toMap() ?: emptyMap()
    val remedies = mangal.optArr("remedies")
        ?.mapNotNull { it.takeIf { e -> !e.isJsonNull }?.asString } ?: emptyList()

    // dosha_from: keep the whole map (lagna/moon/venus booleans + mars_house_*)
    val doshaFrom: Map<String, Any>? = mangal.optObj("dosha_from")?.entrySet()
        ?.mapNotNull { (k, v) ->
            when {
                v == null || v.isJsonNull -> null
                v.isJsonPrimitive -> {
                    val p = v.asJsonPrimitive
                    when {
                        p.isBoolean -> k to p.asBoolean
                        p.isNumber -> k to p.asNumber
                        p.isString -> k to p.asString
                        else -> null
                    }
                }
                else -> null
            }
        }?.toMap()

    val marsHouse = mangal.optObj("mars_position")?.optInt("house", -1)
        ?.takeIf { it > 0 }

    val severity = mangal.optStr("severity").takeIf { it.isNotEmpty() }
    val baseScore = mangal.optDbl("dosha_score").takeIf { !it.isNaN() }
    val explanation = mangal.optStr("explanation").takeIf { it.isNotEmpty() }
    val hasDosha = mangal.optBool("has_mangal_dosha")
    val exceptionCount = mangal.optInt("exception_count", -1).takeIf { it >= 0 }

    return MangalDoshaModel(
        hasMangalDosha = hasDosha,
        severity = severity,
        marsHouse = marsHouse,
        exceptions = activeExceptions,
        description = explanation,
        remedies = remedies,
        explanation = explanation,
        intensityFactors = intensityFactors.takeIf { it.isNotEmpty() },
        exceptionCount = exceptionCount,
        // backend doesn't have a dedicated `cancelled` flag here; iOS treats the
        // dosha as cancelled when joint.mangal_compatibility.cancellation_occurs
        // is true. That decision is made at the joint level (see mangalCompatJoint).
        isCancelledByExceptions = false,
        baseScore = baseScore,
        doshaFrom = doshaFrom,
    )
}

/**
 * Parse kala_sarpa data from analysis_data.{boy|girl}.raw.kala_sarpa.
 * Mirrors iOS KalaSarpaData CodingKeys — tries "present" then "yoga_present",
 * "type" then "yoga_type", snake_case for all other keys.
 */
private fun parseKalsarpa(side: JsonObject?): KalaSarpaModel? {
    val ks = side?.optObj("raw")?.optObj("kala_sarpa") ?: return null
    val isPresent = ks.optBool("present").let { present ->
        if (!present) ks.optBool("yoga_present") else present
    }
    val yogaName = ks.optStr("yoga_type").ifBlank { ks.optStr("type").ifBlank { null } }
    val doshaName = ks.optStr("dosha_name").ifBlank { null }
    val axis = ks.optStr("axis").ifBlank { null }
    val severity = ks.optStr("severity").ifBlank { null }
    val lifeAreas = ks.optArr("life_areas")?.map { it.asString } ?: emptyList()
    val description = ks.optStr("description").ifBlank { null }
    val peakPeriod = ks.optStr("peak_period").ifBlank { null }
    val remedies = ks.optArr("remedies")?.map { it.asString } ?: emptyList()
    val completeness = ks.optStr("completeness").ifBlank { null }
    val planetsCount = ks.get("planets_count")?.takeIf { !it.isJsonNull }?.asInt
    val planetsInvolved = ks.optArr("planets_involved")?.map { it.asString }
    val analysisNotes = ks.optArr("analysis_notes")?.map { it.asString }
    return KalaSarpaModel(
        isPresent = isPresent,
        yogaName = yogaName,
        doshaName = doshaName,
        axis = axis,
        severity = severity,
        lifeAreas = lifeAreas,
        description = description,
        peakPeriod = peakPeriod,
        remedies = remedies,
        completeness = completeness,
        planetsCount = planetsCount,
        planetsInvolved = planetsInvolved,
        analysisNotes = analysisNotes,
    )
}

/**
 * Parse the joint mangal_compatibility object as a Map<String, Any> matching the
 * iOS shape that MangalDoshaScreen consumes (it reads keys like
 * `cancellation_occurs`, `cancellation_factors`, `cancellation_reason`,
 * `compatibility_category`).
 */
private fun parseMangalCompatibility(joint: JsonObject?): Map<String, Any>? {
    val mc = joint?.optObj("mangal_compatibility") ?: return null
    val out = mutableMapOf<String, Any>()
    for ((k, v) in mc.entrySet()) {
        if (v == null || v.isJsonNull) continue
        when {
            v.isJsonPrimitive -> {
                val p = v.asJsonPrimitive
                when {
                    p.isBoolean -> out[k] = p.asBoolean
                    p.isNumber -> out[k] = p.asNumber
                    p.isString -> out[k] = p.asString
                }
            }
            v.isJsonArray -> {
                val list = v.asJsonArray.mapNotNull {
                    if (it.isJsonNull) null
                    else if (it.isJsonPrimitive && it.asJsonPrimitive.isString) it.asString
                    else it.toString()
                }
                if (list.isNotEmpty()) out[k] = list
            }
            // Nested objects (boy_dosha, girl_dosha) are passed through as toString-ed JSON.
            // The screen only reads top-level scalar keys + cancellation_factors list, so this
            // is sufficient for parity.
            v.isJsonObject -> out[k] = v.toString()
        }
    }
    return out.takeIf { it.isNotEmpty() }
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

    // iOS parity — parse dosha_summary.details[<key>] up-front so each kuta can
    // be enriched at mapping time (mirrors OrbitAshtakootView.swift orbitItems).
    val doshaSummaryObj = root.optObj("dosha_summary")
    val doshaDetailsObj = doshaSummaryObj?.optObj("details")
    val doshaDetails: Map<String, DoshaDetailModel> = doshaDetailsObj?.entrySet()
        ?.mapNotNull { (k, v) ->
            val obj = v?.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
            k to DoshaDetailModel(
                present = obj.optBool("present"),
                cancelled = obj.optBool("cancelled"),
                reasonShort = obj.optStr("reason_short").takeIf { it.isNotEmpty() },
                reasonsAll = obj.optArr("reasons_all")
                    ?.mapNotNull { it.takeIf { e -> !e.isJsonNull }?.asString } ?: emptyList(),
                plainEnglishSummary = obj.optStr("plain_english_summary").takeIf { it.isNotEmpty() },
                boyValue = obj.optStr("boy_value").takeIf { it.isNotEmpty() },
                girlValue = obj.optStr("girl_value").takeIf { it.isNotEmpty() },
                doshaType = obj.optStr("dosha_type").takeIf { it.isNotEmpty() },
                classicalEffect = obj.optStr("classical_effect").takeIf { it.isNotEmpty() },
                boyConstitution = obj.optStr("boy_constitution").takeIf { it.isNotEmpty() },
                girlConstitution = obj.optStr("girl_constitution").takeIf { it.isNotEmpty() },
                severity = obj.optStr("severity").takeIf { it.isNotEmpty() },
                housePositions = obj.optStr("house_positions").takeIf { it.isNotEmpty() },
                sadbhakootWarning = obj.optStr("sadbhakoot_warning").takeIf { it.isNotEmpty() },
                taraBoyToGirl = obj.get("tara_boy_to_girl")?.takeIf { !it.isJsonNull }?.asInt,
                taraGirlToBoy = obj.get("tara_girl_to_boy")?.takeIf { !it.isJsonNull }?.asInt,
                boyVashya = obj.optStr("boy_vashya").takeIf { it.isNotEmpty() },
                girlVashya = obj.optStr("girl_vashya").takeIf { it.isNotEmpty() },
                boyToGirlScore = obj.get("boy_to_girl_score")?.takeIf { !it.isJsonNull }?.asDouble,
                girlToBoyScore = obj.get("girl_to_boy_score")?.takeIf { !it.isJsonNull }?.asDouble,
                boyVarna = obj.optStr("boy_varna").takeIf { it.isNotEmpty() },
                girlVarna = obj.optStr("girl_varna").takeIf { it.isNotEmpty() },
                complementarityNote = obj.optStr("complementarity_note").takeIf { it.isNotEmpty() },
                boyValueDescription = obj.optStr("boy_value_description").takeIf { it.isNotEmpty() },
                girlValueDescription = obj.optStr("girl_value_description").takeIf { it.isNotEmpty() },
            )
        }?.toMap() ?: emptyMap()

    if (ashtakoot != null) {
        val gunaScores = ashtakoot.optObj("guna_scores")
        if (gunaScores != null) {
            for ((key, label, maxPts) in KUTA_DEFS) {
                val kutaObj = gunaScores.optObj(key)
                if (kutaObj != null) {
                    val score = kutaObj.optDbl("score", 0.0)
                    val desc = kutaObj.optStr("description")
                    val detail = doshaDetails[key]
                    val present = detail?.present == true
                    val cancelled = detail?.cancelled == true
                    val adjusted: Double? = if (present) {
                        if (cancelled) maxPts else 0.0
                    } else null
                    kutas.add(KutaDetail(
                        key = key, label = label, icon = "",
                        score = score, maxScore = maxPts, description = desc,
                        doshaPresent = present,
                        doshaCancelled = cancelled,
                        cancellationReason = detail?.reasonShort,
                        cancellationReasons = detail?.reasonsAll?.takeIf { it.isNotEmpty() },
                        adjustedScore = adjusted,
                        boyValue = detail?.boyValue,
                        girlValue = detail?.girlValue,
                        doshaType = detail?.doshaType,
                        taraBoyToGirl = detail?.taraBoyToGirl,
                        taraGirlToBoy = detail?.taraGirlToBoy,
                        plainEnglishSummary = detail?.plainEnglishSummary,
                        classicalEffect = detail?.classicalEffect,
                        boyConstitution = detail?.boyConstitution,
                        girlConstitution = detail?.girlConstitution,
                        severity = detail?.severity,
                        housePositions = detail?.housePositions,
                        sadbhakootWarning = detail?.sadbhakootWarning,
                        boyVashya = detail?.boyVashya,
                        girlVashya = detail?.girlVashya,
                        boyToGirlScore = detail?.boyToGirlScore,
                        girlToBoyScore = detail?.girlToBoyScore,
                        boyVarna = detail?.boyVarna,
                        girlVarna = detail?.girlVarna,
                        complementarityNote = detail?.complementarityNote,
                        boyValueDescription = detail?.boyValueDescription,
                        girlValueDescription = detail?.girlValueDescription,
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
    val rejectionReasons = hardNoFlags.optArr("rejection_reasons")
        ?.mapNotNull { it.takeIf { e -> !e.isJsonNull }?.asString } ?: emptyList()
    val cancelledDoshasSummary = hardNoFlags.optStr("cancelled_doshas_summary").takeIf { it.isNotEmpty() }

    val adjustedTotal = root.optDbl("adjusted_total_score")
    val adjustedScore = if (!adjustedTotal.isNaN()) adjustedTotal.toInt() else null
    val adjustedCategory = root.optStr("adjusted_category").takeIf { it.isNotEmpty() }

    val doshaSummary = doshaSummaryObj?.let { d ->
        DoshaSummaryModel(
            totalDoshas = d.optInt("total_doshas"),
            cancelledCount = d.optInt("cancelled_count"),
            activeCount = d.optInt("active_count"),
            details = doshaDetails,
        )
    }

    val llmAnalysis = root.optStr("llm_analysis").takeIf { it.isNotEmpty() }
        ?: "$totalScore/36"
    val followUpSuggestions = root.optArr("follow_up_suggestions")
        ?.mapNotNull { it.takeIf { e -> !e.isJsonNull }?.asString } ?: emptyList()

    // iOS parity (CompatibilityView.swift:148-156): chart_data on each side.
    val boyChart = parseChartData(analysisData.optObj("boy"))
    val girlChart = parseChartData(analysisData.optObj("girl"))
    val boyAsc = boyChart?.d1?.get("Ascendant")?.sign
    val girlAsc = girlChart?.d1?.get("Ascendant")?.sign

    // iOS parity (AdditionalYogasSheet.swift): per-side yoga + dosha lists from
    // analysis_data.{boy|girl}.raw.yogas, decoded into the YogaDoshaData domain
    // model the AdditionalYogasScreen consumes. Without this, the Additional
    // Yogas sub-screen renders the empty-state placeholder even when the
    // backend has full yoga data.
    val boyYogaDosha = parseYogaDoshaData(analysisData.optObj("boy"))
    val girlYogaDosha = parseYogaDoshaData(analysisData.optObj("girl"))

    // iOS parity (CompatibilityResultView.swift:188 + MangalDoshaSheet): per-side
    // mangal data + joint mangal_compatibility (cancellation_occurs/reason/category)
    // for the Mangal Dosha sub-screen. Without these the screen renders empty
    // placeholder rather than the Safe / Cancelled / Effective scenario.
    val mangalBoy = parseMangalDosha(analysisData.optObj("boy"))
    val mangalGirl = parseMangalDosha(analysisData.optObj("girl"))
    val mangalCompatJoint = parseMangalCompatibility(joint)
    // Propagate joint cancellation_occurs to each side so MangalDoshaScreen.isCancelled works.
    // iOS derives isCancelled from doshaScore==0 or severity=="none"; Android uses isCancelledByExceptions.
    val jointCancels = mangalCompatJoint?.get("cancellation_occurs") as? Boolean ?: false
    val mangalBoyFinal = if (jointCancels && mangalBoy?.hasMangalDosha == true)
        mangalBoy.copy(isCancelledByExceptions = true) else mangalBoy
    val mangalGirlFinal = if (jointCancels && mangalGirl?.hasMangalDosha == true)
        mangalGirl.copy(isCancelledByExceptions = true) else mangalGirl
    val kalsarpaBoy = parseKalsarpa(analysisData.optObj("boy"))
    val kalsarpaGirl = parseKalsarpa(analysisData.optObj("girl"))

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
        mangalBoyData = mangalBoyFinal,
        mangalGirlData = mangalGirlFinal,
        mangalCompatibility = mangalCompatJoint,
        kalsarpaBoyData = kalsarpaBoy,
        kalsarpaGirlData = kalsarpaGirl,
        yogasBoyData = null,
        yogasGirlData = null,
        boyYogaDoshaData = boyYogaDosha,
        girlYogaDoshaData = girlYogaDosha,
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
