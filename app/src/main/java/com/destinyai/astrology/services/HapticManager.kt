package com.destinyai.astrology.services

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HapticManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val vibrator: Vibrator by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            manager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    /** Light tap feedback — e.g. button press */
    fun light() {
        vibrate(VibrationEffect.EFFECT_TICK)
    }

    /** Medium click feedback — e.g. toggle */
    fun medium() {
        vibrate(VibrationEffect.EFFECT_CLICK)
    }

    /** Success pattern — e.g. auth success (R2-A10) */
    fun success() {
        vibratePattern(longArrayOf(0, 50, 50, 50))
    }

    /** Error pattern — e.g. validation failure */
    fun error() {
        vibratePattern(longArrayOf(0, 100, 60, 100))
    }

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
