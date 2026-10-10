package com.destinyai.astrology.ui.compatibility

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Multi-density regression guard for the compatibility share-card export.
 *
 * The tester hit a 480dpi (density 3.0) device where the exported PNG clipped its
 * bottom — bare "31" with no "/36", stars, rating, or footer. [CaptureAtDesignDensity]
 * fixes this by pinning LocalDensity so the 1080dp card always maps 1:1 onto the
 * 1080px capture buffer, REGARDLESS of the device's real density. This test proves
 * that invariant across a spread of device densities (xhdpi → xxxhdpi): the ambient
 * density must never leak into the exported layout.
 *
 * Unlike [ShareCardCaptureTest] (which locates by translated text), assertions here
 * use stable testTags (SHARE_CARD_*), so the guard is locale-independent and holds
 * for every one of the 13 shipped locales. Each density is driven through Robolectric
 * `@Config` qualifiers — the same harness the sibling test uses — and the capture
 * surface is sized to exactly 1080px (CARD_DP dp at that density), mirroring the
 * production path which measures the ComposeView at a fixed 1080px on any device.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ShareCardCaptureDensityTest {

    @get:Rule
    val compose = createComposeRule()

    private fun bottomPxOfTag(tag: String): Float? =
        compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes()
            .firstOrNull()?.boundsInRoot?.takeIf { it.height > 0f }?.bottom

    /**
     * Renders the share card into a [cardDp]-dp box (which is exactly 1080px at the
     * [@Config]-configured density) through the production [CaptureAtDesignDensity]
     * seam, then asserts every bottom-of-card element renders fully within the 1080px
     * exported bitmap. [cardDp] must equal 1080 / density for the box to be 1080px.
     */
    private fun assertWholeCardRenders(
        cardDp: Int,
        boyName: String = "Vamshi",
        girlName: String = "Soumya 01",
    ) {
        compose.setContent {
            Box(Modifier.size(cardDp.dp)) {
                CaptureAtDesignDensity(widthPx = CARD_PX) {
                    ShareCardView(
                        boyName = boyName,
                        girlName = girlName,
                        totalScore = 31,
                        maxScore = 36,
                        percentage = 0.86,
                        isRecommended = true,
                        adjustedScore = null,
                        forSharing = true,
                    )
                }
            }
        }
        compose.waitForIdle()

        val fraction = bottomPxOfTag(SHARE_CARD_FRACTION_TAG)
        val stars = bottomPxOfTag(SHARE_CARD_STARS_TAG)
        val rating = bottomPxOfTag(SHARE_CARD_RATING_TAG)
        val footer = bottomPxOfTag(SHARE_CARD_FOOTER_TAG)

        // Presence: every bottom-of-card element must render (the "/36" symptom guard).
        assertTrue("score fraction '/36' missing", fraction != null)
        assertTrue("stars missing", stars != null)
        assertTrue("rating missing", rating != null)
        assertTrue("footer missing", footer != null)

        // Upper bound: nothing is pushed past the 1080px exported bitmap (not clipped).
        assertTrue("fraction clipped ($fraction)", fraction!! <= CARD_PX + EPS)
        assertTrue("stars clipped ($stars)", stars!! <= CARD_PX + EPS)
        assertTrue("rating clipped ($rating)", rating!! <= CARD_PX + EPS)
        assertTrue("footer clipped ($footer)", footer!! <= CARD_PX + EPS)

        // Lower bound: footer sits near the card bottom, proving the card actually
        // filled the 1080px surface (guards against a collapsed layout passing trivially)
        // and that the pinned layout is density-independent (same px on every device).
        assertTrue("footer not near card bottom — layout collapsed or density leaked ($footer)", footer >= CARD_PX * 0.9f)
    }

    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h891dp-320dpi") // density 2.0 -> 540dp == 1080px
    fun rendersWholeCard_atDensity2_xhdpi() = assertWholeCardRenders(cardDp = 540)

    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h891dp-480dpi") // density 3.0 -> 360dp == 1080px (tester's device)
    fun rendersWholeCard_atDensity3_xxhdpi() = assertWholeCardRenders(cardDp = 360)

    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h891dp-640dpi") // density 4.0 -> 270dp == 1080px
    fun rendersWholeCard_atDensity4_xxxhdpi() = assertWholeCardRenders(cardDp = 270)

    /**
     * Guards the maxLines=2 + ellipsis fix on the name Texts (ShareCardView). Without it,
     * very long multi-word names wrap unbounded and push the footer off the bottom of the
     * 1080px export. The names below each wrap well past two lines when uncapped; the test
     * asserts the footer still lands within (and near the bottom of) the card — which only
     * holds while the names are capped at two lines.
     */
    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h891dp-480dpi") // density 3.0 (tester's device)
    fun rendersWholeCard_withVeryLongNames_footerNotPushedOff() = assertWholeCardRenders(
        cardDp = 360,
        boyName = "Alexandrina Wilhelmina Konstantin",
        girlName = "Bartholomew Maximilian Theophilus",
    )

    private companion object {
        const val CARD_PX = 1080
        const val EPS = 2f
    }
}
