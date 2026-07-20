package com.destinyai.astrology.ui.theme

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Single source of truth for spacing, radius, touch, and type scale.
 *
 * Premium-UI polish pass (2026-07): the app previously scattered ad-hoc dp/sp
 * literals (5/6/9/10/12/14/20/44dp, 6/8/9/10/11/13sp) across every screen and
 * double-counted spacing (a container's spacedBy PLUS a per-child Spacer),
 * producing ~36dp dead gaps and inconsistent screen-edge margins. These tokens
 * enforce one 8dp-grid rhythm, one edge margin, one radius family, a 48dp touch
 * floor, and a legible type scale so the layout reads compact and consistent
 * like a premium app.
 *
 * Rule of thumb: choose ONE source of spacing per container. Let a Column/
 * LazyColumn `verticalArrangement = Arrangement.spacedBy(...)` own inter-sibling
 * rhythm; do NOT also add leading/trailing Spacers.
 */
object Spacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp

    /** The one horizontal screen-edge margin for BOTH headers and body content. */
    val screenH = 16.dp

    /** Section-to-next-section vertical gap. */
    val sectionGap = 24.dp

    /** Section header to its first item. */
    val headerToContent = 12.dp

    /** Gap between sibling cards in a group. */
    val cardGap = 12.dp

    /** Gap between chat messages. */
    val messageGap = 16.dp

    /** Card internal padding (use [heroCardPadding] only for plan/hero cards). */
    val cardPadding = 16.dp
    val heroCardPadding = 20.dp
}

/** Corner-radius family. Retire ad-hoc 10/14/24dp. */
object Radius {
    val card = 16.dp
    val hero = 20.dp
    val button = 12.dp
    val chip = 12.dp

    /** Deliberate auth-button radius (Google / guest / support / waitlist CTAs). */
    val authButton = 14.dp
}

/** Minimum tappable size. Keep the glyph/visual small; expand the hit area to this. */
val TouchMin = 48.dp

/**
 * Type scale. Body floor is 14sp, caption floor 12sp — retire 6/8/9/10/11/13sp.
 * Multi-line text should set lineHeight ~1.4x (values baked in below).
 */
object AppType {
    val screenTitle = 26.sp
    val screenTitleLh = 32.sp
    val sectionHeader = 18.sp
    val sectionHeaderLh = 24.sp
    val cardTitle = 17.sp
    val cardTitleLh = 24.sp
    val body = 16.sp
    val bodyLh = 22.sp
    val secondary = 14.sp
    val secondaryLh = 20.sp
    val caption = 12.sp
    val captionLh = 16.sp
}
