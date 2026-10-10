package com.destinyai.astrology.ui.compatibility

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Regression test for the compatibility share-card capture clipping bug.
 *
 * The off-screen capture ([captureComposableAsBitmap]) measures the ComposeView at
 * EXACTLY 1080x1080 PX. ShareCardView is authored for a 1080**dp** surface. On a
 * high-density device (density 3.0) a 1080px buffer is only ~360dp of layout space,
 * so without a density pin the ~580dp card overflows and the bottom (score "/36",
 * stars, rating, footer) is clipped off the exported PNG — producing the tester's
 * "shows 31 not 31/36, no stars/footer" screenshot.
 *
 * The fix is [CaptureAtDesignDensity], which pins LocalDensity so 1080dp == 1080px.
 * These tests run at density 3.0 (480dpi) — the device the tester hit — and assert:
 *  - CONTROL (no pin): the raw card clips its bottom (documents the bug).
 *  - FIXED (production seam): every element lays out within the 1080px bounds.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-480dpi") // density 3.0 -> 1080px == 360dp
class ShareCardCaptureTest {

    @get:Rule
    val compose = createComposeRule()

    private val cardPx = 1080
    private val cardDp = 360 // 1080px at density 3.0

    /** bottom edge (px, in the capture buffer) of the first node matching [text], or null if absent. */
    private fun bottomPxOf(text: String): Float? =
        compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes()
            .firstOrNull()?.boundsInRoot?.takeIf { it.height > 0f }?.bottom

    @Test
    fun control_rawCard_clips_bottom_at_density_3() {
        compose.setContent { Box(Modifier.size(cardDp.dp)) { SampleCard() } }
        compose.waitForIdle()
        // "31" survives near the bottom; the fraction/footer are pushed past 1080px and lost.
        assertTrue("score should still render", (bottomPxOf("31") ?: 0f) in 1f..cardPx.toFloat())
        assertTrue("CONTROL expected the raw card to clip '/36'", bottomPxOf("/36") == null)
        assertTrue("CONTROL expected the raw card to clip the footer", bottomPxOf("destinyaiastrology.com") == null)
    }

    @Test
    fun fixed_captureAtDesignDensity_rendersWholeCard_at_density_3() {
        compose.setContent {
            Box(Modifier.size(cardDp.dp)) {
                CaptureAtDesignDensity(widthPx = cardPx) { SampleCard() }
            }
        }
        compose.waitForIdle()

        val score = bottomPxOf("31")
        val slash = bottomPxOf("/36")
        val rating = bottomPxOf("VERY GOOD")
        val footer = bottomPxOf("destinyaiastrology.com")

        assertTrue("score '31' missing", score != null)
        assertTrue("score fraction '/36' missing — would show bare 31", slash != null)
        assertTrue("rating missing", rating != null)
        assertTrue("footer missing — card bottom clipped", footer != null)
        // Everything fits within the 1080px exported bitmap.
        assertTrue("footer below card bounds (clipped)", footer!! <= cardPx.toFloat())
    }
}

@androidx.compose.runtime.Composable
private fun SampleCard() {
    ShareCardView(
        boyName = "Vamshi",
        girlName = "Soumya 01",
        totalScore = 31,
        maxScore = 36,
        percentage = 0.86, // -> rating "Very Good"
        isRecommended = true,
        adjustedScore = null,
        forSharing = true,
    )
}
