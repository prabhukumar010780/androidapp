package com.destinyai.astrology.services

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

/**
 * Motion / Parallax manager — Android parity with iOS MotionManager.swift.
 *
 * Mirrors the iOS service exactly: disabled by default ("reduced motion" stance),
 * `xOffset` / `yOffset` always 0, and `motionParallax` is a no-op Modifier.
 *
 * If iOS re-enables tilt parallax in the future, we can wire this up with
 * SensorManager + TYPE_GAME_ROTATION_VECTOR; the call-site API stays the same.
 */
class MotionManager(
    private val sensitivity: Float = 0f,
    private val smoothing: Float = 0f,
) {
    var xOffset: Float = 0f
        private set
    var yOffset: Float = 0f
        private set

    /** Disabled per parity with iOS MotionManager.start() (reduced motion). */
    fun start() {
        return
    }

    fun stop() {
        // No-op while motion is disabled.
    }
}

/**
 * Compose extension mirroring iOS `.motionParallax(intensity:)` View modifier.
 * No-op while motion is disabled — avoids allocating sensor listeners at every call site.
 */
@Composable
fun Modifier.motionParallax(intensity: Float = 1f): Modifier {
    // No-op: motion disabled to mirror iOS MotionParallaxModifier behavior.
    val _unused by remember { mutableFloatStateOf(intensity) }
    return this
}
