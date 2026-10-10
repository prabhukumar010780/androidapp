package com.destinyai.astrology.services

import android.content.Intent
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Robolectric contract test for [buildDestinyShareIntent] — the file-vs-text boundary
 * that the compatibility share report depends on.
 *
 * The tester's Symptom #1 was "WhatsApp gets the score TEXT only, no FILE". That happens
 * exactly when the share intent carries no EXTRA_STREAM — i.e. when attachments is empty.
 * The three Compose share sites now abort (visible toast) instead of sending with an empty
 * list, but THIS test locks the lower-level guarantee they rely on: a NON-empty attachment
 * list must always produce a stream-bearing ACTION_SEND(/_MULTIPLE), and an empty list is
 * the (now unreachable from the UI) text-only degrade. Unlike the sibling
 * [ReportShareServiceTest] (pure JVM, isReturnDefaultValues=true — cannot read intent
 * state), Robolectric gives a real shadowed Intent so EXTRA_STREAM / action / type are
 * actually asserted.
 *
 * NOTE: this guards the intent-construction contract, not the Composable abort control
 * flow itself (that lives in CompatibilityResultScreen/FullReportScreen/ComparisonOverviewView
 * and would need a Compose UI test to exercise). If a maintainer reintroduces an
 * empty-list fall-through at a call site, the "empty ⇒ text/plain, no stream" case below
 * documents precisely the broken behavior that would ship.
 */
@RunWith(RobolectricTestRunner::class)
// NATIVE to match the sibling Robolectric graphics tests (ShareCardCapture*Test); mixing
// NATIVE and LEGACY graphics Robolectric classes in one JVM fork breaks RenderNode native
// loading. This test renders nothing, so the mode is otherwise irrelevant here.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class BuildDestinyShareIntentTest {

    private val pngUri: Uri = Uri.parse("content://com.destinyai.astrology.fileprovider/cache/share-card.png")
    private val pdfUri: Uri = Uri.parse("content://com.destinyai.astrology.fileprovider/cache/share-report.pdf")

    @Suppress("DEPRECATION")
    private fun Intent.stream(): Uri? = getParcelableExtra(Intent.EXTRA_STREAM)

    @Test
    fun singleImageAttachment_deliversFile_notTextOnly() {
        val intent = buildDestinyShareIntent(
            text = "Vamshi & Soumya scored 31/36 — destinyaiastrology.com",
            attachments = listOf(ShareAttachment(pngUri, "image/png", "Compatibility score card")),
        )
        assertEquals("single file must use ACTION_SEND", Intent.ACTION_SEND, intent.action)
        assertEquals("image/png", intent.type)
        assertEquals("the PNG must be attached as a file (EXTRA_STREAM)", pngUri, intent.stream())
        assertEquals(
            "caption text must still ride along",
            "Vamshi & Soumya scored 31/36 — destinyaiastrology.com",
            intent.getStringExtra(Intent.EXTRA_TEXT),
        )
        assertTrue(
            "read-grant flag must be set so the target can open the file",
            intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0,
        )
        assertNotNull("clipData must grant the uri to the chooser", intent.clipData)
    }

    @Test
    fun singlePdfAttachment_deliversFile() {
        val intent = buildDestinyShareIntent(
            text = "caption",
            attachments = listOf(ShareAttachment(pdfUri, "application/pdf", "Comparison report")),
        )
        assertEquals(Intent.ACTION_SEND, intent.action)
        assertEquals("application/pdf", intent.type)
        assertEquals(pdfUri, intent.stream())
    }

    @Test
    fun multipleAttachments_useSendMultiple_withAllStreams() {
        val intent = buildDestinyShareIntent(
            text = "caption",
            attachments = listOf(
                ShareAttachment(pngUri, "image/png", "card"),
                ShareAttachment(pdfUri, "application/pdf", "report"),
            ),
        )
        assertEquals(Intent.ACTION_SEND_MULTIPLE, intent.action)
        @Suppress("DEPRECATION")
        val streams = intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
        assertEquals("both files must be attached", 2, streams?.size)
    }

    @Test
    fun emptyAttachments_isTheTextOnlyDegrade_noFile() {
        // This is the exact Symptom #1 shape the fix makes unreachable from the UI:
        // no stream, text/plain -> WhatsApp shows caption only, no attachment.
        val intent = buildDestinyShareIntent(text = "caption only", attachments = emptyList())
        assertEquals(Intent.ACTION_SEND, intent.action)
        assertEquals("text/plain", intent.type)
        assertNull("empty attachments must NOT carry a file stream", intent.stream())
    }
}
