package com.destinyai.astrology.ui.compatibility

import android.content.Intent
import com.destinyai.astrology.BuildConfig

/**
 * Debug-only single-shot inbox for E2E partner pre-fill values that mirrors the
 * iOS `UI_TEST_MODE` block in CompatibilityView.swift. MainActivity captures
 * `E2E_PARTNER_*` extras off the launch intent (or `--es ...` adb args) and
 * stashes them here; CompatibilityViewModel.loadUserData() consumes the stash
 * exactly once when the form first hydrates so Appium can skip the
 * location-search and date-picker dance.
 *
 * Stripped at runtime in release builds via [BuildConfig.DEBUG] guards both at
 * the writer (capture) and reader (consume) ends — production code paths see
 * an empty inbox and behave identically to the no-override case.
 */
internal object E2EPartnerOverrides {

    /** Intent action / extras key prefix used by Appium and adb monkey launches. */
    private const val EXTRA_UI_TEST_MODE = "UI_TEST_MODE"
    private const val EXTRA_PARTNER_NAME = "E2E_PARTNER_NAME"
    private const val EXTRA_PARTNER_DOB = "E2E_PARTNER_DOB"
    private const val EXTRA_PARTNER_TIME = "E2E_PARTNER_TIME"
    private const val EXTRA_PARTNER_CITY = "E2E_PARTNER_CITY"
    private const val EXTRA_PARTNER_LAT = "E2E_PARTNER_LAT"
    private const val EXTRA_PARTNER_LON = "E2E_PARTNER_LON"

    data class Snapshot(
        val name: String,
        val dob: String,
        val time: String,
        val city: String,
        val latitude: Double,
        val longitude: Double,
    )

    @Volatile
    private var pending: Snapshot? = null

    /**
     * Capture E2E partner overrides off a launch (or new) intent. No-op in
     * release builds and when [EXTRA_UI_TEST_MODE] is not set on the intent so
     * production behaviour is bit-for-bit unchanged.
     */
    fun captureFromIntent(intent: Intent?) {
        if (!BuildConfig.DEBUG) return
        if (intent == null) return
        val uiTestMode = intent.getBooleanExtra(EXTRA_UI_TEST_MODE, false) ||
            intent.getStringExtra(EXTRA_UI_TEST_MODE)?.equals("true", ignoreCase = true) == true
        if (!uiTestMode) return
        val name = intent.getStringExtra(EXTRA_PARTNER_NAME).orEmpty()
        if (name.isBlank()) return
        pending = Snapshot(
            name = name,
            dob = intent.getStringExtra(EXTRA_PARTNER_DOB).orEmpty(),
            time = intent.getStringExtra(EXTRA_PARTNER_TIME).orEmpty(),
            city = intent.getStringExtra(EXTRA_PARTNER_CITY).orEmpty(),
            latitude = parseDoubleExtra(intent, EXTRA_PARTNER_LAT),
            longitude = parseDoubleExtra(intent, EXTRA_PARTNER_LON),
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
