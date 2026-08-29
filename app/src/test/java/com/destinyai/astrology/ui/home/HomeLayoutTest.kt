package com.destinyai.astrology.ui.home

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HomeLayoutTest {

    @Test
    fun `home horizontal gutter matches screenH token 16dp`() {
        assertEquals(16, HomeLayout.GUTTER_DP)
    }

    @Test
    fun `yoga card is 180dp wide so planets houses and ask-more fit in one row`() {
        assertEquals(180, HomeLayout.YOGA_CARD_WIDTH_DP)
        assertEquals(170, HomeLayout.YOGA_CARD_HEIGHT_DP)
    }

    @Test
    fun `yoga ask-more sits in flow not over houses`() {
        assertTrue(HomeLayout.YOGA_ASK_MORE_IN_FLOW)
    }
}
