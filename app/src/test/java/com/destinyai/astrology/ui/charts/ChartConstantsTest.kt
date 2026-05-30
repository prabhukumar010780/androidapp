package com.destinyai.astrology.ui.charts

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ChartConstantsTest {

    // ── Sign full names ───────────────────────────────────────────────────────

    @Test
    fun `signFullNames contains all 12 signs with correct full names`() {
        assertEquals("Aries", ChartConstants.signFullNames["Ar"])
        assertEquals("Taurus", ChartConstants.signFullNames["Ta"])
        assertEquals("Gemini", ChartConstants.signFullNames["Ge"])
        assertEquals("Cancer", ChartConstants.signFullNames["Ca"])
        assertEquals("Leo", ChartConstants.signFullNames["Le"])
        assertEquals("Virgo", ChartConstants.signFullNames["Vi"])
        assertEquals("Libra", ChartConstants.signFullNames["Li"])
        assertEquals("Scorpio", ChartConstants.signFullNames["Sc"])
        assertEquals("Sagittarius", ChartConstants.signFullNames["Sg"])
        assertEquals("Capricorn", ChartConstants.signFullNames["Cp"])
        assertEquals("Aquarius", ChartConstants.signFullNames["Aq"])
        assertEquals("Pisces", ChartConstants.signFullNames["Pi"])
    }

    @Test
    fun `signFullNames returns null for unknown abbreviation`() {
        assertNull(ChartConstants.signFullNames["XX"])
    }

    // ── Planet short codes ────────────────────────────────────────────────────

    @Test
    fun `planetShortCodes maps all 9 planets correctly`() {
        assertEquals("Su", ChartConstants.planetShortCodes["Sun"])
        assertEquals("Mo", ChartConstants.planetShortCodes["Moon"])
        assertEquals("Ma", ChartConstants.planetShortCodes["Mars"])
        assertEquals("Me", ChartConstants.planetShortCodes["Mercury"])
        assertEquals("Ju", ChartConstants.planetShortCodes["Jupiter"])
        assertEquals("Ve", ChartConstants.planetShortCodes["Venus"])
        assertEquals("Sa", ChartConstants.planetShortCodes["Saturn"])
        assertEquals("Ra", ChartConstants.planetShortCodes["Rahu"])
        assertEquals("Ke", ChartConstants.planetShortCodes["Ketu"])
    }

    // ── Sign numbers ──────────────────────────────────────────────────────────

    @Test
    fun `signNumbers maps Aries to 1 and Pisces to 12`() {
        assertEquals(1, ChartConstants.signNumbers["Ar"])
        assertEquals(12, ChartConstants.signNumbers["Pi"])
    }

    @Test
    fun `signNumbers maps Gemini to 3`() {
        assertEquals(3, ChartConstants.signNumbers["Ge"])
    }

    // ── Ordered signs ─────────────────────────────────────────────────────────

    @Test
    fun `orderedSigns has exactly 12 entries starting with Ar`() {
        assertEquals(12, ChartConstants.orderedSigns.size)
        assertEquals("Ar", ChartConstants.orderedSigns[0])
        assertEquals("Pi", ChartConstants.orderedSigns[11])
    }

    // ── North Indian sign rotation ────────────────────────────────────────────

    @Test
    fun `north indian house1 sign equals ascendant sign number`() {
        // Ascendant = Gemini (sign 3), house 1 → sign 3
        val ascNum = 3 // Ge
        val result = ChartConstants.northIndianSignForHouse(house = 1, ascNum = ascNum)
        assertEquals(3, result)
    }

    @Test
    fun `north indian house2 sign is ascendant plus 1`() {
        val ascNum = 3
        val result = ChartConstants.northIndianSignForHouse(house = 2, ascNum = ascNum)
        assertEquals(4, result) // Cancer
    }

    @Test
    fun `north indian sign wraps around after 12`() {
        // Ascendant = Capricorn (10), house 4 → (10 + 4 - 2) % 12 + 1 = 12 % 12 + 1 = 1
        val ascNum = 10
        val result = ChartConstants.northIndianSignForHouse(house = 4, ascNum = ascNum)
        assertEquals(1, result) // Aries
    }

    @Test
    fun `north indian sign wraps for ascendant 12 house 1 equals 12`() {
        val ascNum = 12 // Pisces
        val result = ChartConstants.northIndianSignForHouse(house = 1, ascNum = ascNum)
        assertEquals(12, result)
    }

    // ── South Indian layout ───────────────────────────────────────────────────

    @Test
    fun `southIndianLayout has 4 rows and 4 cols`() {
        assertEquals(4, ChartConstants.southIndianLayout.size)
        for (row in ChartConstants.southIndianLayout) {
            assertEquals(4, row.size)
        }
    }

    @Test
    fun `southIndianLayout center 2x2 is null`() {
        assertNull(ChartConstants.southIndianLayout[1][1])
        assertNull(ChartConstants.southIndianLayout[1][2])
        assertNull(ChartConstants.southIndianLayout[2][1])
        assertNull(ChartConstants.southIndianLayout[2][2])
    }

    @Test
    fun `southIndianLayout top row has 4 signs`() {
        val topRow = ChartConstants.southIndianLayout[0]
        assertTrue(topRow.all { it != null })
    }

    @Test
    fun `southIndianLayout has Pisces at row 0 col 0`() {
        // Standard South Indian fixed layout: Pi-Ar-Ta-Ge (top row)
        assertEquals("Pi", ChartConstants.southIndianLayout[0][0])
    }

    @Test
    fun `southIndianLayout has Aries at row 0 col 1`() {
        assertEquals("Ar", ChartConstants.southIndianLayout[0][1])
    }

    // ── Planet symbol ─────────────────────────────────────────────────────────

    @Test
    fun `planetSymbol returns unicode glyphs for all 9 planets`() {
        assertEquals("☉", ChartConstants.planetSymbol("Sun"))
        assertEquals("☽", ChartConstants.planetSymbol("Moon"))
        assertEquals("♂", ChartConstants.planetSymbol("Mars"))
        assertEquals("☿", ChartConstants.planetSymbol("Mercury"))
        assertEquals("♃", ChartConstants.planetSymbol("Jupiter"))
        assertEquals("♀", ChartConstants.planetSymbol("Venus"))
        assertEquals("♄", ChartConstants.planetSymbol("Saturn"))
        assertEquals("☊", ChartConstants.planetSymbol("Rahu"))
        assertEquals("☋", ChartConstants.planetSymbol("Ketu"))
    }

    @Test
    fun `planetSymbol returns star for unknown planet`() {
        assertEquals("⋆", ChartConstants.planetSymbol("Unknown"))
    }

    // ── Degree formatter ──────────────────────────────────────────────────────

    @Test
    fun `formatDegree formats whole degrees with zero minutes`() {
        assertEquals("15°00'", ChartConstants.formatDegree(15.0))
    }

    @Test
    fun `formatDegree formats degrees with minutes`() {
        assertEquals("15°30'", ChartConstants.formatDegree(15.5))
    }

    @Test
    fun `formatDegree rounds minutes correctly`() {
        // 10.75 → 10°45'
        assertEquals("10°45'", ChartConstants.formatDegree(10.75))
    }
}
