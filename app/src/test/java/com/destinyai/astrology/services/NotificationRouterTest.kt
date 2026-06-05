package com.destinyai.astrology.services

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NotificationRouterTest {

    @BeforeEach
    fun setUp() {
        NotificationRouter.consume()
    }

    @Test
    fun `DAILY_PREDICTION_READY routes to Chat`() {
        NotificationRouter.route("DAILY_PREDICTION_READY", prefill = "What does today hold?")

        val link = NotificationRouter.pendingDeepLink.value
        assertTrue(link is NotificationDeepLink.Chat)
        assertEquals("What does today hold?", (link as NotificationDeepLink.Chat).prefill)
    }

    @Test
    fun `DAILY_PREDICTION routes to Chat`() {
        NotificationRouter.route("DAILY_PREDICTION")
        assertTrue(NotificationRouter.pendingDeepLink.value is NotificationDeepLink.Chat)
    }

    @Test
    fun `TRANSIT_ALERT routes to Chat`() {
        NotificationRouter.route("TRANSIT_ALERT", prefill = "Saturn transiting")

        val link = NotificationRouter.pendingDeepLink.value
        assertTrue(link is NotificationDeepLink.Chat)
        assertEquals("Saturn transiting", (link as NotificationDeepLink.Chat).prefill)
    }

    @Test
    fun `LIFE_ALERT routes to Chat`() {
        NotificationRouter.route("LIFE_ALERT")
        assertTrue(NotificationRouter.pendingDeepLink.value is NotificationDeepLink.Chat)
    }

    @Test
    fun `CUSTOM_ALERT routes to Chat`() {
        NotificationRouter.route("CUSTOM_ALERT")
        assertTrue(NotificationRouter.pendingDeepLink.value is NotificationDeepLink.Chat)
    }

    @Test
    fun `WELCOME routes to Chat`() {
        NotificationRouter.route("WELCOME")
        assertTrue(NotificationRouter.pendingDeepLink.value is NotificationDeepLink.Chat)
    }

    @Test
    fun `COMPATIBILITY_READY routes to Match`() {
        NotificationRouter.route("COMPATIBILITY_READY")
        assertTrue(NotificationRouter.pendingDeepLink.value is NotificationDeepLink.Match)
    }

    @Test
    fun `SUBSCRIPTION_EXPIRING routes to Settings`() {
        NotificationRouter.route("SUBSCRIPTION_EXPIRING")
        assertTrue(NotificationRouter.pendingDeepLink.value is NotificationDeepLink.Settings)
    }

    @Test
    fun `unknown type routes to Home`() {
        NotificationRouter.route("SOME_UNKNOWN_TYPE")
        assertTrue(NotificationRouter.pendingDeepLink.value is NotificationDeepLink.Home)
    }

    @Test
    fun `null type routes to Home`() {
        NotificationRouter.route(null)
        assertTrue(NotificationRouter.pendingDeepLink.value is NotificationDeepLink.Home)
    }

    @Test
    fun `type matching is case-insensitive`() {
        NotificationRouter.route("daily_prediction_ready")
        assertTrue(NotificationRouter.pendingDeepLink.value is NotificationDeepLink.Chat)
    }

    @Test
    fun `consume clears the pending deep link`() {
        NotificationRouter.route("WELCOME")
        assertNotNull(NotificationRouter.pendingDeepLink.value)

        NotificationRouter.consume()

        assertNull(NotificationRouter.pendingDeepLink.value)
    }

    @Test
    fun `autoSubmit and newThread flags pass through for Chat links`() {
        NotificationRouter.route("DAILY_PREDICTION_READY", autoSubmit = true, newThread = true)

        val link = NotificationRouter.pendingDeepLink.value as NotificationDeepLink.Chat
        assertTrue(link.autoSubmit)
        assertTrue(link.newThread)
    }
}
