package com.destinyai.astrology.ui.theme

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Adaptive-layout foundation (tablet / foldable / large-screen).
 *
 * The app is phone-first and mirrors iOS, which has no tablet layout. Rather than
 * pull in the material3-window-size-class artifact (extra dependency + BOM
 * coupling), we derive a coarse width class from [LocalConfiguration.screenWidthDp]
 * — the same breakpoints Material 3 uses:
 *   Compact  < 600dp   (phones, portrait)
 *   Medium   600–839dp (small tablets, unfolded foldables, large phones landscape)
 *   Expanded >= 840dp  (tablets, desktop, large foldables)
 *
 * On Medium/Expanded a full-bleed single column stretches cards to unreadable line
 * lengths. The fix everywhere is the same: cap content width and centre it, so the
 * premium card layout keeps phone proportions on a big screen instead of smearing
 * edge-to-edge. [Modifier.adaptiveContentWidth] is that one-liner.
 */
enum class WidthClass { Compact, Medium, Expanded }

/** Coarse width class from the current configuration. Cheap; safe to call in composition. */
@Composable
@ReadOnlyComposable
fun currentWidthClass(): WidthClass {
    val w = LocalConfiguration.current.screenWidthDp
    return when {
        w < 600 -> WidthClass.Compact
        w < 840 -> WidthClass.Medium
        else -> WidthClass.Expanded
    }
}

/** True on tablets / unfolded foldables — width >= 600dp. */
@Composable
@ReadOnlyComposable
fun isLargeScreen(): Boolean = currentWidthClass() != WidthClass.Compact

/**
 * Content-width caps by surface type. Reading-oriented single-column surfaces (home
 * feed, lists, forms, settings) read best at ~640dp; narrow forms (auth) at ~480dp.
 * Below the cap on a phone these are no-ops (widthIn only constrains the upper bound).
 */
object ContentWidth {
    /** Default reading column — lists, feeds, cards, settings, subscription. */
    val standard: Dp = 640.dp

    /** Narrow single-purpose forms — auth / sign-in. */
    val narrow: Dp = 480.dp
}

/**
 * Cap and centre content on large screens, no-op on phones.
 *
 * Apply to the scrolling content container (the Column/LazyColumn's own modifier, or
 * a wrapper), NOT to the screen background — backgrounds should still fill the screen.
 * `fillMaxWidth().widthIn(max = ...)` lets content shrink to the cap while the parent
 * centres it via the caller's horizontalAlignment.
 *
 * Usage:
 *   Column(
 *       modifier = Modifier.fillMaxSize(),
 *       horizontalAlignment = Alignment.CenterHorizontally,
 *   ) {
 *       innerContent(Modifier.adaptiveContentWidth())
 *   }
 */
@Composable
fun Modifier.adaptiveContentWidth(max: Dp = ContentWidth.standard): Modifier =
    this
        .fillMaxWidth()
        .widthIn(max = max)
