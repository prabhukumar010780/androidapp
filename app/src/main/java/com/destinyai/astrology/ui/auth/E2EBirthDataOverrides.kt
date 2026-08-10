package com.destinyai.astrology.ui.auth

import android.content.Intent
import com.destinyai.astrology.BuildConfig

/**
 * Debug-only single-shot inbox for E2E self birth-data pre-fill values. Sibling
 * of [com.destinyai.astrology.ui.compatibility.E2EPartnerOverrides]; captures the
 * `E2E_*` self-profile extras off the launch intent (or `--es ...` adb args) and
 * stashes them so [BirthDataViewModel] can auto-fill the form on first hydration,
 * letting Appium skip the date/time picker + location-search dance and reach Home
 * deterministically.
 *
 * Stripped at runtime in release builds via [BuildConfig.DEBUG] guards at both the
 * writer (capture) and reader (consume) ends — production code paths see an empty
 * inbox and behave bit-for-bit identically to the no-override case.
 */
internal object E2EBirthDataOverrides {

    private const val EXTRA_UI_TEST_MODE = "UI_TEST_MODE"
    private const val EXTRA_USER_NAME = "E2E_USER_NAME"
    private const val EXTRA_USER_EMAIL = "E2E_USER_EMAIL"
    private const val EXTRA_GENDER = "E2E_GENDER"
    private const val EXTRA_DOB = "E2E_DOB"
    private const val EXTRA_TIME = "E2E_TIME"
    private const val EXTRA_CITY = "E2E_CITY"
    private const val EXTRA_LAT = "E2E_LATITUDE"
    private const val EXTRA_LON = "E2E_LONGITUDE"

    data class Snapshot(
        val name: String,
        val gender: String,
        val dob: String,
        val time: String,
        val city: String,
        val latitude: Double,
        val longitude: Double,
    )

    @Volatile
    private var pending: Snapshot? = null

    /**
     * Capture E2E birth-data overrides off a launch (or new) intent. No-op in
     * release builds and when [EXTRA_UI_TEST_MODE] is not set so production
     * behaviour is unchanged. Requires a DOB to be present — otherwise there is
     * nothing useful to pre-fill and the stash stays empty.
     */
    fun captureFromIntent(intent: Intent?) {
        if (!BuildConfig.DEBUG) return
        if (intent == null) return
        val uiTestMode = intent.getBooleanExtra(EXTRA_UI_TEST_MODE, false) ||
            intent.getStringExtra(EXTRA_UI_TEST_MODE)?.equals("true", ignoreCase = true) == true
        if (!uiTestMode) return
        val dob = intent.getStringExtra(EXTRA_DOB).orEmpty()
        if (dob.isBlank()) return
        // Name falls back to the email local-part, then a fixed E2E default, so the
        // form is always valid even if the harness sends only the birth fields.
        val name = intent.getStringExtra(EXTRA_USER_NAME)?.takeIf { it.isNotBlank() }
            ?: intent.getStringExtra(EXTRA_USER_EMAIL)?.substringBefore('@')?.takeIf { it.isNotBlank() }
            ?: "E2E Tester"
        pending = Snapshot(
            name = name,
            gender = intent.getStringExtra(EXTRA_GENDER)?.takeIf { it.isNotBlank() } ?: "other",
            dob = dob,
            time = intent.getStringExtra(EXTRA_TIME).orEmpty(),
            city = intent.getStringExtra(EXTRA_CITY).orEmpty(),
            latitude = parseDoubleExtra(intent, EXTRA_LAT),
            longitude = parseDoubleExtra(intent, EXTRA_LON),
        )
    }

    /** Single-shot read — clears the stash so re-entering the screen does not refill. */
    fun consume(): Snapshot? {
        if (!BuildConfig.DEBUG) return null
        val snap = pending
        pending = null
        return snap
    }

    /** Visible to tests so they can prime the inbox without crafting a real Intent. */
    internal fun setForTest(snapshot: Snapshot?) {
        pending = snapshot
    }

    private fun parseDoubleExtra(intent: Intent, key: String): Double {
        val asDouble = intent.getDoubleExtra(key, Double.NaN)
        if (!asDouble.isNaN()) return asDouble
        val asString = intent.getStringExtra(key) ?: return 0.0
        return asString.toDoubleOrNull() ?: 0.0
    }
}
