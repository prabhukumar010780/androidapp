package com.destinyai.astrology.services

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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
    val action = if (shareUsesSendMultiple(attachments)) {
        Intent.ACTION_SEND_MULTIPLE
    } else {
        Intent.ACTION_SEND
    }
    val intent = Intent(action).apply {
        type = shareMimeType(attachments.map { it.mimeType })
        putExtra(Intent.EXTRA_TEXT, shareTextForIntent(text, attachments))
        putExtra(Intent.EXTRA_TITLE, title)
        subject?.takeIf { it.isNotBlank() }?.let { putExtra(Intent.EXTRA_SUBJECT, it) }
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    if (attachments.isNotEmpty()) {
        val mimeTypes = attachments.map { it.mimeType }.distinct().toTypedArray()
        intent.clipData = buildShareClipData(attachments, title, mimeTypes)
        intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes)

        if (shareUsesSendMultiple(attachments)) {
            intent.putParcelableArrayListExtra(
                Intent.EXTRA_STREAM,
                ArrayList(attachments.map { it.uri }),
            )
        } else if (attachments.isNotEmpty()) {
            intent.putExtra(Intent.EXTRA_STREAM, attachments.first().uri)
        }
    }

    return intent
}

fun presentShareChooser(context: Context, shareIntent: Intent, title: String) {
    grantReadPermissionToShareTargets(context, shareIntent)
    val chooser = Intent.createChooser(shareIntent, title).apply {
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        shareIntent.clipData?.let { clipData = it }
    }
    context.startActivity(chooser)
}

internal fun grantReadPermissionToShareTargets(context: Context, shareIntent: Intent) {
    val uris = shareUrisFromIntent(shareIntent)
    if (uris.isEmpty()) return
    @Suppress("DEPRECATION")
    val targets = context.packageManager.queryIntentActivities(
        shareIntent,
        PackageManager.MATCH_DEFAULT_ONLY,
    )
    for (resolveInfo in targets) {
        val packageName = resolveInfo.activityInfo.packageName
        for (uri in uris) {
            context.grantUriPermission(
                packageName,
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
    }
}

@Suppress("DEPRECATION")
internal fun shareUrisFromIntent(shareIntent: Intent): List<Uri> {
    val uris = linkedSetOf<Uri>()
    shareIntent.clipData?.let { clip ->
        for (i in 0 until clip.itemCount) {
            clip.getItemAt(i).uri?.let(uris::add)
        }
    }
    shareIntent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)?.let(uris::add)
    shareIntent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)?.forEach { uri ->
        if (uri != null) uris.add(uri)
    }
    return uris.toList()
}

internal fun shareMimeType(mimeTypes: List<String>): String {
    val distinct = mimeTypes.filter { it.isNotBlank() }.distinct()
    return when (distinct.size) {
        0 -> "text/plain"
        1 -> distinct.first()
        else -> "*/*"
    }
}

/**
 * iOS UIActivityViewController gets [text, image, pdf] as separate items.
 * Android equivalent is ACTION_SEND_MULTIPLE with every file in EXTRA_STREAM.
 * A single ACTION_SEND with only the PNG is why the sheet showed the screenshot
 * and dropped the PDF.
 */
internal fun shareUsesSendMultiple(attachments: List<ShareAttachment>): Boolean =
    attachments.size > 1

/**
 * WhatsApp turns EXTRA_TEXT into a link preview when it contains a URL and then
 * drops attached files. Keep the score copy; drop URL lines if anything is attached.
 */
internal fun shareTextForIntent(text: String, attachments: List<ShareAttachment>): String {
    if (attachments.isEmpty()) return text
    return text.lineSequence()
        .filterNot { line ->
            val lower = line.lowercase()
            lower.contains("destinyaiastrology.com") ||
                lower.contains("http://") ||
                lower.contains("https://")
        }
        .joinToString("\n")
        .trim()
}

private fun buildShareClipData(
    attachments: List<ShareAttachment>,
    title: String,
    mimeTypes: Array<String>,
): ClipData {
    val first = attachments.first()
    return ClipData(
        first.label.ifBlank { title },
        mimeTypes.ifEmpty { arrayOf(first.mimeType) },
        ClipData.Item(first.uri),
    ).apply {
        attachments.drop(1).forEach { attachment ->
            addItem(ClipData.Item(attachment.uri))
        }
    }
}
