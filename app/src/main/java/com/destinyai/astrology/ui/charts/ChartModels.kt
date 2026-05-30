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
)

// ── Dasha / Transit DTOs ──────────────────────────────────────────────────────

data class DashaPeriod(
    val mahadasha: String,
    val antardasha: String,
    val pratyantardasha: String,
    val start: String,
    val end: String,
)

data class DashaResponse(
    val year: Int,
    val periods: List<DashaPeriod>,
)

data class TransitEvent(
    val date: String,
    val sign: String,
    val houseFromLagna: Int,
)

data class TransitResponse(
    val year: Int,
    val transits: Map<String, List<TransitEvent>>,
)

// ── Chart request DTO ─────────────────────────────────────────────────────────

data class ChartDataRequest(
    @SerializedName("dob") val dob: String,
    @SerializedName("time") val time: String,
    @SerializedName("latitude") val latitude: Double,
    @SerializedName("longitude") val longitude: Double,
)
