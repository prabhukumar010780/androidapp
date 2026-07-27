package com.destinyai.astrology.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Generates a baseline profile for the critical user journey (cold launch → Home
 * render → scroll the feed). The resulting profile is compiled AOT on install,
 * cutting cold-start time — the one code-side lever against the Android Vitals
 * cold-start target (Cat 5). Run: `./gradlew :app:generateReleaseBaselineProfile`.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() {
        rule.collect(packageName = "com.destinyai.astrology") {
            pressHome()
            startActivityAndWait()
            // Let the Home feed settle, then scroll it so the list/render paths are
            // captured in the profile.
            device.waitForIdle()
            repeat(3) {
                device.swipe(
                    device.displayWidth / 2,
                    (device.displayHeight * 0.7).toInt(),
                    device.displayWidth / 2,
                    (device.displayHeight * 0.3).toInt(),
                    10,
                )
                device.waitForIdle()
            }
        }
    }
}
