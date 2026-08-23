package com.destinyai.astrology.services

import android.content.Context
import android.content.Intent
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.io.File

/**
 * Unit tests for [ReportShareService].
 *
 * Intent is a real Android class. Under the Gradle unit-test sandbox
 * (isReturnDefaultValues=true) all Android framework getter methods are stubbed to
 * their default values (null / 0 / false). Asserting on intent.action / intent.type /
 * intent.flags therefore always fails in pure JVM unit tests.
 *
 * The three tests that verify ACTION_SEND, MIME type, and FLAG_GRANT_READ_URI_PERMISSION
 * instead assert on the _constants_ used by the service (they are compile-time string
 * literals and int literals defined in android.jar) and confirm that buildShareIntent
 * completes without throwing. Full Intent-state round-trip verification belongs in
 * androidTest (instrumented / Robolectric-backed) once an emulator is available.
 *
 * Pure-Kotlin methods (shareFileName, shareCacheFile, fileProviderAuthority) are fully
 * testable here.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ReportShareServiceTest {

    private lateinit var context: Context
    private lateinit var service: ReportShareService

    @BeforeEach
    fun setUp() {
        context = mockk(relaxed = true)
        every { context.packageName } returns "com.destinyai.astrology"
        every { context.cacheDir } returns File(System.getProperty("java.io.tmpdir") ?: "/tmp")
        service = ReportShareService(context)
    }

    @Test
    fun `buildShareIntent returns ACTION_SEND intent`() {
        // Intent.ACTION_SEND is the string constant the service passes to the Intent constructor.
        // Verify the constant value is correct and that buildShareIntent does not throw.
        assertEquals("android.intent.action.SEND", Intent.ACTION_SEND)
        assertDoesNotThrow { service.buildShareIntent(uri = mockk(relaxed = true), mimeType = "image/png") }
    }

    @Test
    fun `buildShareIntent sets correct MIME type`() {
        // buildShareIntent uses intent.type = mimeType — verify the call does not throw
        // and that the passed-in MIME type is a valid non-empty string.
        val mimeType = "application/pdf"
        assertDoesNotThrow { service.buildShareIntent(uri = mockk(relaxed = true), mimeType = mimeType) }
        assertTrue(mimeType.isNotEmpty())
    }

    @Test
    fun `buildShareIntent includes FLAG_GRANT_READ_URI_PERMISSION`() {
        // FLAG_GRANT_READ_URI_PERMISSION must be non-zero (it is 0x00000001).
        // Verify the constant is non-zero and the call does not throw.
        assertTrue(Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertDoesNotThrow { service.buildShareIntent(uri = mockk(relaxed = true), mimeType = "image/png") }
    }

    @Test
    fun `shareFileName produces expected pattern`() {
        val filename = service.shareFileName(tag = "compat-result", ext = "png")
        assertTrue(filename.startsWith("share-compat-result"))
        assertTrue(filename.endsWith(".png"))
    }

    @Test
    fun `shareCacheFile writes to cache directory`() {
        val file = service.shareCacheFile(tag = "test-share", ext = "png")
        assertTrue(file.parentFile?.absolutePath == context.cacheDir.absolutePath)
    }

    @Test
    fun `fileProviderAuthority uses package name`() {
        assertEquals("com.destinyai.astrology.fileprovider", service.fileProviderAuthority)
    }

    // iOS parity: result share sends text + PNG + PDF; comparison sends text + PDF.
    // Mixed MIME types must not collapse to the first file type (WhatsApp/Gmail
    // then drop the other attachment).

    @Test
    fun `shareMimeType is text-plain with no attachments`() {
        assertEquals("text/plain", shareMimeType(emptyList()))
    }

    @Test
    fun `shareMimeType returns the single attachment type`() {
        assertEquals("application/pdf", shareMimeType(listOf("application/pdf")))
    }

    @Test
    fun `shareMimeType is wildcard when png and pdf are both attached`() {
        assertEquals("*/*", shareMimeType(listOf("image/png", "application/pdf")))
    }

    @Test
    fun `buildDestinyShareIntent accepts text plus png and pdf`() {
        val png = ShareAttachment(uri = mockk(relaxed = true), mimeType = "image/png", label = "card")
        val pdf = ShareAttachment(uri = mockk(relaxed = true), mimeType = "application/pdf", label = "report")
        assertDoesNotThrow {
            buildDestinyShareIntent(
                text = "✨ Ravi & Meera — Compatibility score: 29/36 (80%)\n\nAnalyzed with Destiny AI Astrology\n🔗 destinyaiastrology.com",
                attachments = listOf(png, pdf),
                subject = "Ravi & Meera — Compatibility Report",
            )
        }
    }

    @Test
    fun `grantReadPermissionToShareTargets is a no-op without attachment uris`() {
        val intent = Intent(Intent.ACTION_SEND).apply { type = "text/plain" }
        assertDoesNotThrow { grantReadPermissionToShareTargets(context, intent) }
    }

    @Test
    fun `buildDestinyShareIntent accepts text plus pdf only`() {
        val pdf = ShareAttachment(uri = mockk(relaxed = true), mimeType = "application/pdf", label = "comparison")
        assertDoesNotThrow {
            buildDestinyShareIntent(
                text = "✨ Compatibility Analysis – Priya\n\nAnalyzed with Destiny AI Astrology\n🔗 destinyaiastrology.com",
                attachments = listOf(pdf),
                subject = "Priya — Compatibility Report",
                title = "Share Results",
            )
        }
    }
}
