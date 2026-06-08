package com.destinyai.astrology.util

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * Helper to convert backend snake_case keys to localized descriptions.
 *
 * Mirrors iOS `DoshaDescriptions` (see ios_app/Resources/DoshaDescriptions.swift).
 * Used to translate cancellation-rule keys embedded inside reason strings,
 * e.g. "mars_strong_ashtakavarga" -> localized "Mars has strong Ashtakavarga points".
 */
object DoshaDescriptions {

    private val EXCEPTION_REGEX = Regex("(mars_[a-z0-9_]+|moon_strong|chandra_mangala_yoga)")

    /**
     * Resolve a single exception key to its localized string.
     * Mirrors iOS DoshaDescriptions.exception(_:) → ("exception_" + key).localized
     * Falls back to humanizing the key (Title Case words) if no resource found.
     */
    fun exception(context: Context, key: String): String {
        val resourceName = "exception_$key"
        val resId = context.resources.getIdentifier(resourceName, "string", context.packageName)
        if (resId != 0) {
            val localized = context.getString(resId)
            if (localized.isNotEmpty() && localized != resourceName) return localized
        }
        // Fallback: humanize snake_case → "Mars Strong Ashtakavarga"
        return key.split("_").joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
    }

    @Composable
    fun exception(key: String): String {
        val context = LocalContext.current
        return exception(context, key)
    }

    /**
     * Replace any cancellation-rule keys inside [text] with their localized strings.
     * Keys without a matching `exception_<key>` resource are left untouched.
     */
    @Composable
    fun localizeExceptionKeys(text: String): String {
        val context = LocalContext.current
        return localizeExceptionKeys(context, text)
    }

    /**
     * Non-Composable variant for use outside Compose (tests, ViewModels).
     */
    fun localizeExceptionKeys(context: Context, text: String): String {
        if (text.isEmpty()) return text
        var result = text
        // Process matches in reverse to preserve indices.
        val matches = EXCEPTION_REGEX.findAll(text).toList().asReversed()
        for (match in matches) {
            val key = match.value
            val resourceName = "exception_$key"
            val resId = context.resources.getIdentifier(
                resourceName,
                "string",
                context.packageName,
            )
            if (resId != 0) {
                val localized = context.getString(resId)
                if (localized.isNotEmpty() && localized != resourceName) {
                    result = result.replaceRange(match.range, localized)
                }
            }
        }
        return result
    }
}
