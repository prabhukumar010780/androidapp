package com.destinyai.astrology.ui.charts

import androidx.compose.ui.graphics.Color

object ChartConstants {

    val signFullNames: Map<String, String> = mapOf(
        "Ar" to "Aries",
        "Ta" to "Taurus",
        "Ge" to "Gemini",
        "Ca" to "Cancer",
        "Le" to "Leo",
        "Vi" to "Virgo",
        "Li" to "Libra",
        "Sc" to "Scorpio",
        "Sg" to "Sagittarius",
        "Cp" to "Capricorn",
        "Aq" to "Aquarius",
        "Pi" to "Pisces",
    )

    val planetShortCodes: Map<String, String> = mapOf(
        "Sun" to "Su",
        "Moon" to "Mo",
        "Mars" to "Ma",
        "Mercury" to "Me",
        "Jupiter" to "Ju",
        "Venus" to "Ve",
        "Saturn" to "Sa",
        "Rahu" to "Ra",
        "Ketu" to "Ke",
    )

    val signNumbers: Map<String, Int> = mapOf(
        "Ar" to 1, "Ta" to 2, "Ge" to 3, "Ca" to 4,
        "Le" to 5, "Vi" to 6, "Li" to 7, "Sc" to 8,
        "Sg" to 9, "Cp" to 10, "Aq" to 11, "Pi" to 12,
    )

    val orderedSigns: List<String> = listOf(
        "Ar", "Ta", "Ge", "Ca", "Le", "Vi",
        "Li", "Sc", "Sg", "Cp", "Aq", "Pi",
    )

    // South Indian fixed layout: 4x4 grid, center 2x2 empty (null)
    // Row 0: Pi  Ar  Ta  Ge
    // Row 1: Aq  --  --  Ca
    // Row 2: Cp  --  --  Le
    // Row 3: Sg  Sc  Li  Vi
    val southIndianLayout: Array<Array<String?>> = arrayOf(
        arrayOf("Pi", "Ar", "Ta", "Ge"),
        arrayOf("Aq", null, null, "Ca"),
        arrayOf("Cp", null, null, "Le"),
        arrayOf("Sg", "Sc", "Li", "Vi"),
    )

    fun planetSymbol(name: String): String = when (name) {
        "Sun" -> "☉"
        "Moon" -> "☽"
        "Mars" -> "♂"
        "Mercury" -> "☿"
        "Jupiter" -> "♃"
        "Venus" -> "♀"
        "Saturn" -> "♄"
        "Rahu" -> "☊"
        "Ketu" -> "☋"
        else -> "⋆"
    }

    fun formatDegree(degree: Double): String {
        val d = degree.toInt()
        val m = ((degree - d) * 60).toInt()
        return "%d°%02d'".format(d, m)
    }

    // North Indian: fixed houses, signs rotate from ascendant
    // House 1 sign = ascNum, house 2 = ascNum+1, etc. (wrapping mod 12)
    fun northIndianSignForHouse(house: Int, ascNum: Int): Int {
        val signIndex = (ascNum + house - 2) % 12
        return signIndex + 1
    }

    // R2-C10: Per-planet color — mirrors the iOS palette
    fun planetColor(planet: String): Color = when (planet) {
        "Sun" -> Color(0xFFFFCC00)   // gold
        "Moon" -> Color(0xFFC0C0C0)  // silver
        "Mars" -> Color(0xFFFF3B30)  // red
        "Mercury" -> Color(0xFF34C759) // green
        "Jupiter" -> Color(0xFFFF9500) // yellow-orange
        "Venus" -> Color(0xFFFF2D55)  // pink
        "Saturn" -> Color(0xFF1C2A5E) // dark blue
        "Rahu" -> Color(0xFF6E5CB6)  // purple
        "Ketu" -> Color(0xFF8B6343)  // brown
        else -> Color.Gray
    }
}
