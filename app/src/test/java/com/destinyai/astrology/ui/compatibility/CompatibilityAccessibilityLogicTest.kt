package com.destinyai.astrology.ui.compatibility

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Tests for pure accessibility-ID helper functions extracted from compatibility screens.
 * All functions must be internal top-level in their respective screen files.
 */
class CompatibilityAccessibilityLogicTest {

    // ── doshaRowContentDescription ────────────────────────────────────────────

    @Test
    fun `mangal dosha row gets mangal_dosha_row id`() {
        assertEquals("mangal_dosha_row", doshaRowContentDescription("Mangal Dosha"))
    }

    @Test
    fun `kalsarpa dosha row gets kalsarpa_dosha_row id`() {
        assertEquals("kalsarpa_dosha_row", doshaRowContentDescription("Kaal Sarp Dosha"))
    }

    @Test
    fun `unknown dosha row gets generic dosha_row id`() {
        assertEquals("dosha_row", doshaRowContentDescription("Additional Yogas"))
    }

    // ── analyzeButtonContentDescription ──────────────────────────────────────

    @Test
    fun `analyze button always returns compat_analyze_button`() {
        assertEquals("compat_analyze_button", analyzeButtonContentDescription())
    }
}
