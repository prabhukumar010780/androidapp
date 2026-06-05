package com.destinyai.astrology.services

import android.content.Context
import android.content.Intent
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Mirrors iOS ReportShareService, CompatibilityPDFRenderer, ComparisonPDFRenderer.
 * Centralises share-sheet plumbing: ACTION_SEND intent building and cache-file generation.
 * Bitmap/PDF rendering stays in Compose composables that own the visual content.
 */
@Singleton
class ReportShareService @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun buildShareIntent(uri: Uri, mimeType: String): Intent =
        Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

    fun shareCacheFile(tag: String, ext: String): File =
        File(context.cacheDir, shareFileName(tag, ext))

    fun shareFileName(tag: String, ext: String): String =
        "share-$tag-${System.currentTimeMillis()}.$ext"

    val fileProviderAuthority: String get() = "${context.packageName}.fileprovider"
}
