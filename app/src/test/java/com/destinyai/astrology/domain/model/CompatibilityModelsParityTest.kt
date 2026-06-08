package com.destinyai.astrology.domain.model

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class CompatibilityModelsParityTest {

    // ── YogaItem ───────────────────────────────────────────────────────────────

    @Test
    fun `YogaItem strength defaults from strengthRaw when not provided`() {
        val item = YogaItem(name = "Gaja Kesari", strengthRaw = 85.0)
        assertEquals(85.0, item.strength, 0.001)
    }

    @Test
    fun `YogaItem displayName prefers name`() {
        val item = YogaItem(name = "Gaja Kesari Yoga", yogaKey = "gaja_kesari")
        assertEquals("Gaja Kesari Yoga", item.displayName)
    }

    @Test
    fun `YogaItem isActive returns true for status A`() {
        assertTrue(YogaItem(name = "X", status = "A").isActive)
    }

    @Test
    fun `YogaItem isActive returns true for empty status`() {
        assertTrue(YogaItem(name = "X", status = "").isActive)
    }

    @Test
    fun `YogaItem isActive returns false for status C`() {
        assertFalse(YogaItem(name = "X", status = "C").isActive)
    }

    @Test
    fun `YogaItem isActive returns false for status R`() {
        assertFalse(YogaItem(name = "X", status = "R").isActive)
    }

    @Test
    fun `YogaItem uniquePlanets deduplicates comma-separated planets`() {
        val item = YogaItem(name = "X", planets = "Jupiter, Jupiter, Moon")
        assertEquals("Jupiter, Moon", item.uniquePlanets)
    }

    @Test
    fun `YogaItem uniqueHouses deduplicates houses`() {
        val item = YogaItem(name = "X", houses = "H1, H5, H1")
        assertEquals("H1, H5", item.uniqueHouses)
    }

    @Test
    fun `YogaItem uniquePlanets null when planets null`() {
        assertNull(YogaItem(name = "X").uniquePlanets)
    }

    @Test
    fun `YogaItem localizedName falls back to name when no outcome`() {
        val item = YogaItem(name = "Dhana Yoga", outcome = null)
        assertEquals("Dhana Yoga", item.localizedName)
    }

    // ── DestinyTileType ────────────────────────────────────────────────────────

    @Test
    fun `DestinyTileType from maps Wealth string`() {
        assertEquals(DestinyTileType.WEALTH, DestinyTileType.from("Wealth"))
    }

    @Test
    fun `DestinyTileType from maps Career string`() {
        assertEquals(DestinyTileType.CAREER, DestinyTileType.from("Career"))
    }

    @Test
    fun `DestinyTileType from maps Relationship to love`() {
        assertEquals(DestinyTileType.LOVE, DestinyTileType.from("Relationship"))
    }

    @Test
    fun `DestinyTileType from maps Family string`() {
        assertEquals(DestinyTileType.FAMILY, DestinyTileType.from("Family"))
    }

    @Test
    fun `DestinyTileType from maps Education to wisdom`() {
        assertEquals(DestinyTileType.WISDOM, DestinyTileType.from("Education"))
    }

    @Test
    fun `DestinyTileType from maps Health string`() {
        assertEquals(DestinyTileType.HEALTH, DestinyTileType.from("Health"))
    }

    @Test
    fun `DestinyTileType from maps Spiritual to wisdom`() {
        assertEquals(DestinyTileType.WISDOM, DestinyTileType.from("Spiritual"))
    }

    @Test
    fun `DestinyTileType from maps Pancha Mahapurusha to career`() {
        assertEquals(DestinyTileType.CAREER, DestinyTileType.from("Pancha Mahapurusha"))
    }

    @Test
    fun `DestinyTileType from returns wealth for unknown`() {
        assertEquals(DestinyTileType.WEALTH, DestinyTileType.from(null))
    }

    @Test
    fun `DestinyTileType topicTiles excludes dosha`() {
        assertFalse(DestinyTileType.topicTiles.contains(DestinyTileType.DOSHA))
    }

    @Test
    fun `DestinyTileType topicTiles has 6 entries`() {
        assertEquals(6, DestinyTileType.topicTiles.size)
    }

    // ── YogaDoshaData ──────────────────────────────────────────────────────────

    @Test
    fun `YogaDoshaData activeYogaCount counts active yogas`() {
        val yogas = listOf(
            YogaItem(name = "A", status = "A"),
            YogaItem(name = "B", status = "C"),
            YogaItem(name = "C", status = "A"),
        )
        val data = YogaDoshaData(yogas = yogas)
        assertEquals(2, data.activeYogaCount)
    }

    @Test
    fun `YogaDoshaData activeDoshaCount counts active doshas`() {
        val doshas = listOf(
            YogaItem(name = "D1", status = "A", isDosha = true),
            YogaItem(name = "D2", status = "C", isDosha = true),
        )
        val data = YogaDoshaData(doshas = doshas)
        assertEquals(1, data.activeDoshaCount)
    }

    @Test
    fun `YogaDoshaData allItems merges yogas and doshas`() {
        val yogas = listOf(YogaItem(name = "Y1"))
        val doshas = listOf(YogaItem(name = "D1", isDosha = true))
        val data = YogaDoshaData(yogas = yogas, doshas = doshas)
        assertEquals(2, data.allItems.size)
    }

    @Test
    fun `YogaDoshaData items for WEALTH filters by category`() {
        val items = listOf(
            YogaItem(name = "Dhana Yoga", category = "Wealth"),
            YogaItem(name = "Raja Yoga", category = "Career"),
        )
        val data = YogaDoshaData(yogas = items)
        val result = data.items(DestinyTileType.WEALTH)
        assertEquals(1, result.size)
        assertEquals("Dhana Yoga", result.first().name)
    }

    @Test
    fun `YogaDoshaData items for DOSHA returns all dosha items`() {
        val doshas = listOf(
            YogaItem(name = "Mangal Dosha", isDosha = true, status = "A"),
            YogaItem(name = "Kala Sarpa", isDosha = true, status = "C"),
        )
        val data = YogaDoshaData(doshas = doshas)
        assertEquals(2, data.items(DestinyTileType.DOSHA).size)
    }

    @Test
    fun `YogaDoshaData activeDoshas returns only active dosha items`() {
        val doshas = listOf(
            YogaItem(name = "D1", isDosha = true, status = "A"),
            YogaItem(name = "D2", isDosha = true, status = "C"),
        )
        val data = YogaDoshaData(doshas = doshas)
        assertEquals(1, data.activeDoshas.size)
        assertEquals("D1", data.activeDoshas.first().name)
    }

    // ── ComparisonGroup ────────────────────────────────────────────────────────

    @Test
    fun `ComparisonGroup bestMatch returns highest scoring item`() {
        val items = listOf(
            CompatibilityHistoryItem(boyName = "A", girlName = "X", boyDob = "", boyCity = "", girlDob = "", girlCity = "", totalScore = 28, maxScore = 36),
            CompatibilityHistoryItem(boyName = "A", girlName = "Y", boyDob = "", boyCity = "", girlDob = "", girlCity = "", totalScore = 32, maxScore = 36),
            CompatibilityHistoryItem(boyName = "A", girlName = "Z", boyDob = "", boyCity = "", girlDob = "", girlCity = "", totalScore = 20, maxScore = 36),
        )
        val group = ComparisonGroup(userName = "A", items = items)
        assertEquals("Y", group.bestMatch?.girlName)
    }

    @Test
    fun `ComparisonGroup averageScore computes mean`() {
        val items = listOf(
            CompatibilityHistoryItem(boyName = "A", girlName = "X", boyDob = "", boyCity = "", girlDob = "", girlCity = "", totalScore = 20, maxScore = 36),
            CompatibilityHistoryItem(boyName = "A", girlName = "Y", boyDob = "", boyCity = "", girlDob = "", girlCity = "", totalScore = 30, maxScore = 36),
        )
        val group = ComparisonGroup(userName = "A", items = items)
        assertEquals(25.0, group.averageScore, 0.001)
    }

    @Test
    fun `ComparisonGroup partnerCount returns item count`() {
        val items = listOf(
            CompatibilityHistoryItem(boyName = "A", girlName = "X", boyDob = "", boyCity = "", girlDob = "", girlCity = "", totalScore = 25, maxScore = 36),
            CompatibilityHistoryItem(boyName = "A", girlName = "Y", boyDob = "", boyCity = "", girlDob = "", girlCity = "", totalScore = 30, maxScore = 36),
        )
        val group = ComparisonGroup(userName = "A", items = items)
        assertEquals(2, group.partnerCount)
    }

    @Test
    fun `ComparisonGroup displayTitle includes userName`() {
        val items = listOf(
            CompatibilityHistoryItem(boyName = "Prabhu", girlName = "Asma", boyDob = "", boyCity = "", girlDob = "", girlCity = "", totalScore = 30, maxScore = 36),
        )
        val group = ComparisonGroup(userName = "Prabhu", items = items)
        assertTrue(group.displayTitle.contains("Prabhu"))
    }

    // ── MangalDoshaModel computed props ────────────────────────────────────────

    @Test
    fun `MangalDoshaModel isCancelled true when isCancelledByExceptions flag set`() {
        val model = MangalDoshaModel(
            hasMangalDosha = false,
            severity = "none",
            marsHouse = 7,
            exceptions = listOf("Benefic in 7th"),
            description = null,
            isCancelledByExceptions = true,
        )
        assertTrue(model.isCancelled)
    }

    @Test
    fun `MangalDoshaModel isCancelled false when no exceptions`() {
        val model = MangalDoshaModel(
            hasMangalDosha = true,
            severity = "Severe",
            marsHouse = 7,
            exceptions = emptyList(),
            description = null,
        )
        assertFalse(model.isCancelled)
    }

    @Test
    fun `MangalDoshaModel isReduced true when severity Mild`() {
        val model = MangalDoshaModel(
            hasMangalDosha = true,
            severity = "Mild",
            marsHouse = 4,
            exceptions = emptyList(),
            description = null,
        )
        assertTrue(model.isReduced)
    }

    @Test
    fun `KalaSarpaModel completeness field exists`() {
        val model = KalaSarpaModel(
            isPresent = true,
            yogaName = "Ananta",
            doshaName = "Kala Sarpa Dosha",
            axis = "1-7",
            severity = "High",
            lifeAreas = listOf("Career"),
            description = "Strong",
            completeness = "Full",
            planetsCount = 7,
            planetsInvolved = listOf("Sun", "Moon"),
        )
        assertEquals("Full", model.completeness)
        assertEquals(7, model.planetsCount)
        assertEquals(2, model.planetsInvolved!!.size)
    }
}
