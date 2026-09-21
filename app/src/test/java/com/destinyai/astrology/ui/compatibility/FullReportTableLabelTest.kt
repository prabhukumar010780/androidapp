package com.destinyai.astrology.ui.compatibility

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Guards the Ashtakoot report-table enrichment: the Koota column gains the orb
 * label in parentheses (Varna (Work), Vashya (Attraction), …) so the table
 * relates to the orb wheel. Label resolution is localized in Compose; these
 * pure helpers decide when/what to enrich.
 */
class FullReportTableLabelTest {

    @Test
    fun `koota column header detected case-insensitively and trimmed`() {
        assertTrue(isKootaColumnHeader("Koota"))
        assertTrue(isKootaColumnHeader("koota"))
        assertTrue(isKootaColumnHeader("  Kuta  "))
        assertFalse(isKootaColumnHeader("Score"))
        assertFalse(isKootaColumnHeader("Analysis"))
        assertFalse(isKootaColumnHeader(null))
    }

    @Test
    fun `all eight kutas map to their canonical key`() {
        assertEquals("varna", kutaLabelKey("Varna"))
        assertEquals("vashya", kutaLabelKey("Vashya"))
        assertEquals("tara", kutaLabelKey("Tara"))
        assertEquals("yoni", kutaLabelKey("Yoni"))
        assertEquals("maitri", kutaLabelKey("Maitri"))
        assertEquals("gana", kutaLabelKey("GANA"))
        assertEquals("bhakoot", kutaLabelKey("Bhakoot"))
        assertEquals("nadi", kutaLabelKey("Nadi "))
    }

    @Test
    fun `non-kuta cells and already-labelled cells are skipped`() {
        assertNull(kutaLabelKey("Total"))
        assertNull(kutaLabelKey("Score"))
        // Idempotent: a cell that already carries a "(label)" is not re-enriched.
        assertNull(kutaLabelKey("Varna (Work)"))
    }
}
