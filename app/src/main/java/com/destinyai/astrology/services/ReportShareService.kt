package com.destinyai.astrology.services

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Mirrors iOS ReportShareService, CompatibilityPDFRenderer, ComparisonPDFRenderer.
 *
 * iOS result share (CompatibilityResultSheets.presentNativeShareSheet) attaches
 * share text (includes destinyaiastrology.com link) + score-card image + PDF.
 * iOS comparison share (ComparisonOverviewView.shareComparisonReport) attaches
 * share text + PDF only.
 *
 * Bitmap/PDF rendering stays in the Compose screens that own the visuals; this
 * type only builds cache files and the share Intent.
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

data class ShareAttachment(
    val uri: Uri,
    val mimeType: String,
    val label: String,
)

fun buildDestinyShareIntent(
    text: String,
    attachments: List<ShareAttachment>,
    subject: String? = null,
    title: String = "Destiny AI Astrology",
): Intent {
    val action = if (attachments.size > 1) Intent.ACTION_SEND_MULTIPLE else Intent.ACTION_SEND
    val intent = Intent(action).apply {
        type = shareMimeType(attachments.map { it.mimeType })
        putExtra(Intent.EXTRA_TEXT, text)
        putExtra(Intent.EXTRA_TITLE, title)
        subject?.takeIf { it.isNotBlank() }?.let { putExtra(Intent.EXTRA_SUBJECT, it) }
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    if (attachments.isNotEmpty()) {
        val mimeTypes = attachments.map { it.mimeType }.distinct().toTypedArray()
        intent.clipData = buildShareClipData(attachments, title, mimeTypes)
        intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes)

        if (attachments.size == 1) {
            intent.putExtra(Intent.EXTRA_STREAM, attachments.first().uri)
        } else {
            intent.putParcelableArrayListExtra(
                Intent.EXTRA_STREAM,
                ArrayList(attachments.map { it.uri }),
            )
        }
    }

    return intent
}

fun presentShareChooser(context: Context, shareIntent: Intent, title: String) {
    val chooser = Intent.createChooser(shareIntent, title).apply {
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        shareIntent.clipData?.let { clipData = it }
    }
    context.startActivity(chooser)
}

internal fun shareMimeType(mimeTypes: List<String>): String {
    val distinct = mimeTypes.filter { it.isNotBlank() }.distinct()
    return when (distinct.size) {
        0 -> "text/plain"
        1 -> distinct.first()
        else -> "*/*"
    }
}

private fun buildShareClipData(
    attachments: List<ShareAttachment>,
    title: String,
    mimeTypes: Array<String>,
): ClipData {
    val first = attachments.first()
    return ClipData(
        first.label.ifBlank { title },
        mimeTypes,
        ClipData.Item(first.uri),
    ).apply {
        attachments.drop(1).forEach { attachment ->
            addItem(ClipData.Item(attachment.uri))
        }
    }
}
