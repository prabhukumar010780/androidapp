package com.destinyai.astrology.ui.chat

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import com.destinyai.astrology.ui.theme.Gold
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * DES-161 B6a / B6b — markdown rendering fixes for AMA reading text.
 *
 * B6a: `#` headings must render in Gold (not just bold white), matching iOS.
 * B6b: the `>`-prefixed closing statement (blockquote) must render as gold italic
 *      with the "> " marker stripped, instead of a literal "> " prefix.
 */
class MarkdownRenderingTest {

    @Test
    fun `heading renders in gold`() {
        val annotated = buildMarkdownAnnotated("# What your chart says")
        // The rendered text drops the leading "# ".
        assertEquals("What your chart says", annotated.text)
        // Every span across the heading must carry the Gold color + bold weight.
        val spans = annotated.spanStyles
        assertTrue(spans.isNotEmpty(), "heading should carry style spans")
        assertTrue(spans.all { it.item.color == Gold }, "heading spans must be Gold")
        assertTrue(spans.all { it.item.fontWeight == FontWeight.Bold }, "heading spans must be bold")
    }

    @Test
    fun `blockquote closing statement strips marker and renders gold italic`() {
        val annotated = buildMarkdownAnnotated("> **_Trust the timing; your window opens soon._**")
        // Marker + bold/italic tokens are stripped from the visible text.
        assertEquals("Trust the timing; your window opens soon.", annotated.text)
        val spans = annotated.spanStyles
        assertTrue(spans.isNotEmpty(), "quote should carry a style span")
        assertTrue(spans.all { it.item.color == Gold }, "quote must be Gold")
        assertTrue(spans.all { it.item.fontStyle == FontStyle.Italic }, "quote must be italic")
    }

    @Test
    fun `plain body text carries no color span`() {
        val annotated = buildMarkdownAnnotated("This is a normal sentence.")
        assertEquals("This is a normal sentence.", annotated.text)
        // Body text is colored by the Text() composable, not per-span — so no span
        // should force Gold here.
        assertFalse(annotated.spanStyles.any { it.item.color == Gold })
    }

    @Test
    fun `bold inline markup still renders bold without gold`() {
        val annotated = buildMarkdownAnnotated("A **strong** word.")
        assertEquals("A strong word.", annotated.text)
        val boldSpan = annotated.spanStyles.firstOrNull { it.item.fontWeight == FontWeight.Bold }
        assertTrue(boldSpan != null, "bold markup should produce a bold span")
        // Inline bold is not a heading, so it must not be gold.
        assertEquals(Color.Unspecified, boldSpan!!.item.color)
    }
}
