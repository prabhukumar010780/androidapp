package com.destinyai.astrology.services

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Centralized Haptic Feedback Manager — Android parity with iOS HapticManager.
 *
 * Mirrors iOS HapticManager.swift: master enable/disable gate, primitive haptics,
 * Core-Haptics-equivalent textures (Heartbeat / Shimmer / HeavyImpact via
 * VibrationEffect.createWaveform with amplitude arrays on API 26+), and the
 * full choreographed/semantic API surface used by shared cross-platform UI.
 */
@Singleton
class HapticManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /**
     * Master switch to enable/disable ALL haptics app-wide.
     * Defaults to `false` to match iOS (all haptics silenced until user opts in).
     */
    @Volatile
    var isEnabled: Boolean = false

    private val mainHandler = Handler(Looper.getMainLooper())

    private val vibrator: Vibrator by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            manager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    // MARK: - Primitive haptics

    /** Light tap feedback — e.g. button press */
    fun light() {
        if (!isEnabled) return
        vibrate(VibrationEffect.EFFECT_TICK)
    }

    /** Medium click feedback — e.g. toggle */
    fun medium() {
        if (!isEnabled) return
        vibrate(VibrationEffect.EFFECT_CLICK)
    }

    /** Heavy impact — e.g. confirmation. Mirrors iOS UIImpactFeedbackGenerator(.heavy) */
    fun heavy() {
        if (!isEnabled) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK))
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(80L, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(80L)
        }
    }

    /** Soft tap — subtle feedback. Mirrors iOS UIImpactFeedbackGenerator(.soft) */
    fun soft() {
        if (!isEnabled) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(20L, 80))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(20L)
        }
    }

    /** Selection-changed feedback. Mirrors iOS UISelectionFeedbackGenerator */
    fun selection() {
        if (!isEnabled) return
        vibrate(VibrationEffect.EFFECT_TICK)
    }

    /** Success pattern — e.g. auth success (R2-A10) */
    fun success() {
        if (!isEnabled) return
        vibratePattern(longArrayOf(0, 50, 50, 50))
    }

    /** Error pattern — e.g. validation failure */
    fun error() {
        if (!isEnabled) return
        vibratePattern(longArrayOf(0, 100, 60, 100))
    }

    /** Warning notification feedback. Mirrors iOS UINotificationFeedbackGenerator(.warning) */
    fun warning() {
        if (!isEnabled) return
        vibratePattern(longArrayOf(0, 80, 40, 80))
    }

    // MARK: - Premium "Living App" Textures (Core Haptics equivalents)

    /**
     * Simulates a living "Heartbeat" (Lub-Dub).
     * Used during AI processing or deep analysis.
     * Two transients with decreasing amplitude, ~120ms apart — mirrors iOS playHeartbeat().
     */
    fun playHeartbeat() {
        if (!isEnabled) return
        if (!vibrator.hasVibrator()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // timings: wait, lub, gap, dub
            val timings = longArrayOf(0L, 40L, 80L, 30L)
            // amplitudes: 0..255, lub=full, dub=80% intensity
            val amplitudes = intArrayOf(0, 255, 0, 204)
            vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(longArrayOf(0L, 40L, 80L, 30L), -1)
        }
    }

    /**
     * Simulates a "Golden Shimmer" / "Purr".
     * Continuous high-frequency texture for ~400ms — mirrors iOS playShimmer() success/unlock.
     */
    fun playShimmer() {
        if (!isEnabled) return
        if (!vibrator.hasVibrator()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Rapid micro-pulses to approximate continuous high-sharpness buzz
            val timings = longArrayOf(0L, 20L, 10L, 20L, 10L, 20L, 10L, 20L, 10L, 20L, 10L, 20L, 10L, 20L)
            val amplitudes = intArrayOf(0, 180, 0, 180, 0, 180, 0, 180, 0, 180, 0, 180, 0, 180)
            vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(longArrayOf(0L, 20L, 10L, 20L, 10L, 20L, 10L, 20L, 10L, 20L, 10L, 20L, 10L, 20L), -1)
        }
    }

    /**
     * Simulates "Heavy Impact" (Gold Tablet drop).
     * Strong, low-end dull thud — mirrors iOS playHeavyImpact() with zero sharpness.
     */
    fun playHeavyImpact() {
        if (!isEnabled) return
        if (!vibrator.hasVibrator()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Single sustained heavy thud — full amplitude, longer duration for "dull" feel
            vibrator.vibrate(VibrationEffect.createOneShot(60L, 255))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(60L)
        }
    }

    // MARK: - Choreographed / Semantic API (parity with iOS call sites)

    /** Premium button press — soft tap. Mirrors iOS premiumButtonPress(). */
    fun premiumButtonPress() {
        soft()
    }

    /** Premium continue — medium + delayed soft secondary tap for "depth". */
    fun premiumContinue() {
        if (!isEnabled) return
        medium()
        mainHandler.postDelayed({ soft() }, 100L)
    }

    /** Premium success — Shimmer texture, with notify+heavy fallback when unavailable. */
    fun premiumSuccess() {
        if (!isEnabled) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            playShimmer()
        } else {
            success()
            mainHandler.postDelayed({ heavy() }, 150L)
        }
    }

    /** Premium slide transition — selection feedback. */
    fun premiumSlideTransition() {
        selection()
    }

    /** Premium card select — light tap. */
    fun premiumCardSelect() {
        light()
    }

    // MARK: - Semantic Aliases (Soul of the App)

    fun playButtonTap() {
        premiumButtonPress()
    }

    fun playSuccess() {
        premiumSuccess()
    }

    // MARK: - Internals

    private fun vibrate(predefined: Int) {
        if (!vibrator.hasVibrator()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vibrator.vibrate(VibrationEffect.createPredefined(predefined))
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // API 26–28: predefined effects not available; fall back to a short waveform
            vibrator.vibrate(VibrationEffect.createOneShot(50L, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            // API 24–25: deprecated vibrate(long) API
            @Suppress("DEPRECATION")
            vibrator.vibrate(50L)
        }
    }

    private fun vibratePattern(pattern: LongArray) {
        if (!vibrator.hasVibrator()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
        } else {
            // API 24–25: deprecated vibrate(long[], int) API
            @Suppress("DEPRECATION")
            vibrator.vibrate(pattern, -1)
        }
    }
}
