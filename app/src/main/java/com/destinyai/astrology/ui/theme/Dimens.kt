package com.destinyai.astrology.ui.theme

import androidx.compose.ui.text.TextStyle
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
 *
 * Full spacing ramp: xs=4 / sm=8 / md=12 / lg=16 / lgPlus=20 / xl=24 / xxl=32 / xxxl=40
 */
object Spacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp

    /** Between lg (16) and xl (24). Use for hero-card padding and auth-form gutters. */
    val lgPlus = 20.dp
    val xl = 24.dp
    val xxl = 32.dp

    /** Hero / large-break token above xxl. Use for full-bleed section padding. */
    val xxxl = 40.dp

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

    /** Hero/plan card internal padding — alias of [lgPlus] (20 dp). */
    val heroCardPadding = lgPlus
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
 * Elevation tiers for differentiated depth. Use these for shadow/elevation params
 * rather than hardcoding raw dp. card < hero < sheet < dialog.
 */
object Elevation {
    val card = 2.dp
    val hero = 6.dp
    val sheet = 12.dp
    val dialog = 24.dp
}

/**
 * Icon-size scale. Snap `.size(N.dp)` on icons to this scale — do NOT hardcode
 * raw dp at call sites. Scale: xs=12 / sm=16 / md=20 / lg=24 / hero=32.
 * Off-scale sizes 18 and 22 should round to sm (16) or md (20) / lg (24)
 * respectively. Remaining call-site burn-down tracked as tech-debt.
 */
object IconSize {
    /** Small badge / decorative glyph (e.g. inline crown badge). */
    val xs = 12.dp
    val sm = 16.dp
    val md = 20.dp
    val lg = 24.dp
    val hero = 32.dp
}

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

    /**
     * Paired TextStyle accessors — recommended API for new call sites so fontSize and
     * lineHeight are always matched. The loose fontSize/lineHeight constants above are
     * kept for the ~27 existing call sites that reference them individually.
     */
    val screenTitleStyle get() = TextStyle(fontSize = screenTitle, lineHeight = screenTitleLh)
    val sectionHeaderStyle get() = TextStyle(fontSize = sectionHeader, lineHeight = sectionHeaderLh)
    val cardTitleStyle get() = TextStyle(fontSize = cardTitle, lineHeight = cardTitleLh)
    val bodyStyle get() = TextStyle(fontSize = body, lineHeight = bodyLh)
    val secondaryStyle get() = TextStyle(fontSize = secondary, lineHeight = secondaryLh)
    val captionStyle get() = TextStyle(fontSize = caption, lineHeight = captionLh)
}
