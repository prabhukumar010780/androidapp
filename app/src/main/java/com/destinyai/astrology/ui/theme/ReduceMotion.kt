package com.destinyai.astrology.ui.theme

import androidx.compose.runtime.compositionLocalOf

/**
 * CompositionLocal carrying the system "Remove animations" state.
 *
 * `true` when `Settings.Global.ANIMATOR_DURATION_SCALE == 0f` (the user has
 * enabled "Remove animations" in Developer Options / Accessibility). Provided
 * once at the AppNav root via [androidx.compose.runtime.CompositionLocalProvider]
 * so every composable in the tree can read it without threading it manually.
 *
 * Components that run infinite animations (CosmicStarField, ShimmerButton,
 * CelestialOrb, SplashScreen) must freeze their animated output to a stable
 * static frame when this is `true` — see each component for the pattern. (R10)
 */
val LocalReduceMotion = compositionLocalOf { false }
