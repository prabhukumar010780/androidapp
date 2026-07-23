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
        // Also localize the backend's composed yoga-reason prose, e.g.
        // "Mars is AUSPICIOUS + WEAK (yoga reduced)" — fixed English enum tokens
        // the backend emits as free-form text (not lookup keys). Mirrors iOS
        // DoshaDescriptions.localizeReasonTokens.
        return localizeReasonTokens(context, result)
    }

    // Backend reason-prose token → string-resource key. Whole-word replacement.
    private val REASON_TOKENS: List<Pair<String, String>> = listOf(
        "AUSPICIOUS" to "yoga_token_auspicious",
        "INAUSPICIOUS" to "yoga_token_inauspicious",
        "WEAK" to "yoga_token_weak",
        "STRONG" to "yoga_token_strong",
        "MODERATE" to "yoga_token_moderate",
        "yoga reduced" to "yoga_token_yoga_reduced",
        "yoga cancelled" to "yoga_token_yoga_cancelled",
        "Mars" to "planet_mars", "Moon" to "planet_moon", "Sun" to "planet_sun",
        "Mercury" to "planet_mercury", "Jupiter" to "planet_jupiter",
        "Venus" to "planet_venus", "Saturn" to "planet_saturn",
        "Rahu" to "planet_rahu", "Ketu" to "planet_ketu",
    )

    private fun localizeReasonTokens(context: Context, text: String): String {
        var result = text
        for ((token, key) in REASON_TOKENS) {
            val resId = context.resources.getIdentifier(key, "string", context.packageName)
            if (resId == 0) continue
            val localized = context.getString(resId)
            if (localized.isEmpty() || localized == key) continue
            val regex = Regex("\\b" + Regex.escape(token) + "\\b", RegexOption.IGNORE_CASE)
            result = result.replace(regex, localized)
        }
        return result
    }

    // MARK: - Yoga name / outcome / formation / category / planets localization
    // Mirrors iOS YogaDetail.localizedName/localizedOutcome/localizedFormation/
    // localizedCategory/localizedPlanets. The backend emits NUMBERED variant keys
    // (e.g. "bhagya_yoga_241") but string resources hold only the base key.

    private fun keyCandidates(yogaKey: String): List<String> {
        val base = yogaKey.replace(Regex("_\\d+$"), "")
        return if (base == yogaKey) listOf(yogaKey) else listOf(yogaKey, base)
    }

    /** Look up "<prefix>_<key>" trying the exact key then the number-stripped base. */
    private fun localizedForKey(context: Context, prefix: String, yogaKey: String?): String? {
        if (yogaKey.isNullOrEmpty()) return null
        for (candidate in keyCandidates(yogaKey)) {
            val resName = "${prefix}_$candidate"
            val resId = context.resources.getIdentifier(resName, "string", context.packageName)
            if (resId != 0) {
                val localized = context.getString(resId)
                if (localized.isNotEmpty() && localized != resName) return localized
            }
        }
        return null
    }

    fun localizedYogaName(context: Context, yogaKey: String?, fallback: String): String =
        localizedForKey(context, "yoga_name", yogaKey) ?: fallback

    fun localizedYogaOutcome(context: Context, yogaKey: String?, fallback: String): String =
        localizedForKey(context, "yoga_outcome", yogaKey) ?: fallback

    fun localizedYogaFormation(context: Context, yogaKey: String?, fallback: String): String =
        localizedForKey(context, "yoga_formation", yogaKey) ?: fallback

    /** Localized category tag (e.g. "Relationship" → "人間関係"), raw fallback. */
    fun localizedYogaCategory(context: Context, category: String?): String {
        if (category.isNullOrEmpty()) return category ?: ""
        val resName = "yoga_cat_" + category.lowercase().replace(" ", "_")
        val resId = context.resources.getIdentifier(resName, "string", context.packageName)
        if (resId != 0) {
            val localized = context.getString(resId)
            if (localized.isNotEmpty() && localized != resName) return localized
        }
        return category
    }

    /** Localized, comma-joined planet list; unknown tokens pass through. */
    fun localizedPlanets(context: Context, planets: String): String {
        if (planets.isEmpty()) return planets
        return planets.split(",").map { it.trim() }.joinToString(", ") { p ->
            val resName = "planet_" + p.lowercase()
            val resId = context.resources.getIdentifier(resName, "string", context.packageName)
            if (resId != 0) {
                val localized = context.getString(resId)
                if (localized.isNotEmpty() && localized != resName) return@joinToString localized
            }
            p
        }
    }
}
