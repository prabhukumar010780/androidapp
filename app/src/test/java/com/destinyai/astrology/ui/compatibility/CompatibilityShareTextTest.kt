package com.destinyai.astrology.ui.compatibility

import com.destinyai.astrology.domain.model.CompatibilityResult
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * DES-161 C3 — the share-sheet text must derive the score fraction AND the
 * percentage from the SAME score. Previously the fraction used the raw
 * totalScore while the percentage used adjustedScore, producing inconsistent
 * text like "16/36 (80%)".
 */
class CompatibilityShareTextTest {

    private fun result(total: Int, max: Int, adjusted: Int?): CompatibilityResult =
        CompatibilityResult(
            totalScore = total,
            maxScore = max,
            kutas = emptyList(),
            summary = "",
            recommendation = "",
            isRecommended = true,
            adjustedScore = adjusted,
            adjustedCategory = null,
            rejectionReasons = emptyList(),
            cancelledDoshasSummary = null,
            doshaSummary = null,
            mangalBoyData = null,
            mangalGirlData = null,
            mangalCompatibility = null,
            kalsarpaBoyData = null,
            kalsarpaGirlData = null,
            yogasBoyData = null,
            yogasGirlData = null,
            followUpSuggestions = emptyList(),
            boyName = "Ravi",
            girlName = "Meera",
            boyDob = null,
            girlDob = null,
            boyCity = null,
            girlCity = null,
        )

    @Test
    fun `uses adjusted score for BOTH fraction and percentage when present`() {
        // Raw 16, adjusted 29 (dosha cancelled). % must be 29/36 ≈ 80%, and the
        // fraction must show 29/36 — NOT the old buggy "16/36 (80%)".
        val text = buildCompatibilityShareText(result(total = 16, max = 36, adjusted = 29))
        assertTrue(text.contains("29/36"), "fraction must use adjusted score: $text")
        assertTrue(text.contains("(80%)"), "percentage must match adjusted score: $text")
        assertTrue(!text.contains("16/36"), "must NOT show raw total in the fraction: $text")
    }

    @Test
    fun `falls back to raw total when no adjusted score`() {
        val text = buildCompatibilityShareText(result(total = 24, max = 36, adjusted = null))
        assertTrue(text.contains("24/36"), "fraction must use raw total: $text")
        assertTrue(text.contains("(66%)"), "percentage must match raw total: $text")
    }

    @Test
    fun `fraction and percentage are always internally consistent`() {
        val text = buildCompatibilityShareText(result(total = 10, max = 36, adjusted = 18))
        // 18/36 == 50%
        assertTrue(text.contains("18/36"), text)
        assertTrue(text.contains("(50%)"), text)
    }

    @Test
    fun `guards against divide-by-zero max score`() {
        val text = buildCompatibilityShareText(result(total = 0, max = 0, adjusted = null))
        assertTrue(text.contains("(0%)"), "zero max must not crash and reads 0%: $text")
    }
}
