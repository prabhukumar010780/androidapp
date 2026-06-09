package com.destinyai.astrology.ui.charts

import com.google.gson.annotations.SerializedName

// ── Chart domain models ───────────────────────────────────────────────────────

data class D1PlanetPosition(
    val house: Int,
    val sign: String,
    val degree: Double,
    val retrograde: Boolean? = null,
    val vargottama: Boolean? = null,
    val combust: Boolean? = null,
    val nakshatra: String? = null,
    val pada: Int? = null,
)

data class D9PlanetPosition(
    val house: Int?,
    val sign: String,
)

data class ChartData(
    val d1: Map<String, D1PlanetPosition>,
    val d9: Map<String, D9PlanetPosition>,
)

data class PlanetDisplayInfo(
    val id: String,
    val code: String,
    val isRetrograde: Boolean,
    val isVargottama: Boolean,
    val isCombust: Boolean,
    val nakshatra: String? = null,
    val pada: Int? = null,
)

// ── API response DTOs (mirrors iOS UserAstroDataResponse) ────────────────────

data class PlanetApiData(
    @SerializedName("house") val house: Int,
    @SerializedName("sign") val sign: String,
    @SerializedName("degree") val degree: Double,
    @SerializedName("is_retrograde") val isRetrograde: Boolean? = null,
    @SerializedName("vargottama") val vargottama: Boolean? = null,
    @SerializedName("is_combust") val isCombust: Boolean? = null,
)

data class HouseApiData(
    @SerializedName("sign_num") val signNum: Int,
)

data class NakshatraApiData(
    @SerializedName("nakshatra") val nakshatra: String,
    @SerializedName("pada") val pada: Int,
)

data class DivisionalPlanetData(
    @SerializedName("sign") val sign: String? = null,
    @SerializedName("house") val house: String? = null,
)

data class BirthDetailsApiData(
    @SerializedName("dob") val dob: String,
    @SerializedName("time") val time: String,
)

data class ChartApiResponse(
    @SerializedName("planets") val planets: Map<String, PlanetApiData>,
    @SerializedName("houses") val houses: Map<String, HouseApiData>,
    @SerializedName("nakshatra") val nakshatra: Map<String, NakshatraApiData>,
    @SerializedName("divisional_charts") val divisionalCharts: Map<String, DivisionalPlanetData>,
    @SerializedName("birth_details") val birthDetails: BirthDetailsApiData,
    // Server-side yoga + dosha analysis. iOS reads this via UserChartService, then
    // surfaces it on Home (yoga combinations) and Charts (yoga detail). Android
    // mirrors the same payload shape so HomeRepositoryImpl.getRichHomeData can
    // populate the "Positive & Negative Combinations" section without an extra
    // network call. (See backend astrodata/full response: analysis.yogas.yogas[]
    // + analysis.yogas.doshas[] + mangal_dosha + kala_sarpa.)
    @SerializedName("analysis") val analysis: com.destinyai.astrology.data.remote.AstroAnalysisDto? = null,
)

// ── Dasha / Transit DTOs ──────────────────────────────────────────────────────

data class DashaPeriod(
    @SerializedName("mahadasha_lord") val mahadasha: String,
    @SerializedName("antardasha_lord") val antardasha: String,
    @SerializedName("pratyantardasha_lord") val pratyantardasha: String,
    @SerializedName("start") val start: String,
    @SerializedName("end") val end: String,
)

data class DashaResponse(
    @SerializedName("year") val year: Int,
    @SerializedName("dasha_periods") val periods: List<DashaPeriod>,
)

data class TransitEvent(
    @SerializedName("date") val date: String,
    @SerializedName("sign") val sign: String,
    @SerializedName("house_from_lagna") val houseFromLagna: Int,
    @SerializedName("favorable") val favorable: Boolean? = null,
)

data class TransitResponse(
    @SerializedName("year") val year: Int,
    @SerializedName("transits") val transits: Map<String, List<TransitEvent>>,
)

// ── Chart request DTO ─────────────────────────────────────────────────────────

data class ChartDataRequest(
    @SerializedName("birth_data") val birthData: BirthData,
)

data class DashaTransitRequest(
    @SerializedName("birth_data") val birthData: BirthData,
    @SerializedName("year") val year: Int,
)

data class BirthData(
    @SerializedName("dob") val dob: String,
    @SerializedName("time") val time: String,
    @SerializedName("latitude") val latitude: Double,
    @SerializedName("longitude") val longitude: Double,
    @SerializedName("ayanamsa") val ayanamsa: String = "lahiri",
    @SerializedName("house_system") val houseSystem: String = "whole_sign",
    @SerializedName("city_of_birth") val cityOfBirth: String? = null,
    @SerializedName("birth_time_unknown") val birthTimeUnknown: Boolean = false,
)
