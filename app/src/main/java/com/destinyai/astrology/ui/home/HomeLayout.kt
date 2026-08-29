package com.destinyai.astrology.ui.home

/**
 * Home layout tokens. Horizontal gutter unified to 16dp = Spacing.screenH so all
 * body content edges align with the header. Yoga cards keep the ask-more control
 * in the planets/houses row so it cannot cover house numbers.
 */
internal object HomeLayout {
    const val GUTTER_DP = 16 // = Spacing.screenH — matches the header 16dp gutter
    const val YOGA_CARD_WIDTH_DP = 180
    const val YOGA_CARD_HEIGHT_DP = 170
    const val YOGA_ASK_MORE_IN_FLOW = true
}
