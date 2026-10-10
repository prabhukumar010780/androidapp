package com.destinyai.astrology.di

import com.destinyai.astrology.data.local.prefs.SessionTokenStore
import com.destinyai.astrology.data.local.prefs.UserPreferences
import com.destinyai.astrology.data.remote.AuthExchangeClient
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import javax.inject.Provider

/**
 * Fix 4: billing verify (verifyPurchase / reconcileEmpty) must ride a short-timeout
 * OkHttpClient, NOT the 600s prediction-tuned default. A cold Cloud Run verify blocking
 * ~10 minutes holds BillingManager's verifyInFlight de-dup key, so a concurrent reconcile
 * can't re-verify and the purchase spinner stays pinned.
 */
class NetworkModuleBillingTest {

    private fun store() = mockk<SessionTokenStore>(relaxed = true)
    private fun prefs() = mockk<UserPreferences>(relaxed = true)
    private fun authProvider() = Provider { mockk<AuthExchangeClient>(relaxed = true) }

    @Test
    fun `billing OkHttpClient has short bounded timeouts`() {
        val client = NetworkModule.provideBillingOkHttpClient(
            store = store(),
            apiKey = "k",
            userAgent = "ua",
            authExchangeProvider = authProvider(),
            prefs = prefs(),
        )
        assertTrue(client.callTimeoutMillis in 1..30_000, "callTimeout must be bounded, was ${client.callTimeoutMillis}")
        assertTrue(client.readTimeoutMillis in 1..30_000, "readTimeout must be bounded, was ${client.readTimeoutMillis}")
    }

    @Test
    fun `default OkHttpClient keeps the long prediction timeout`() {
        // Regression guard: the billing fix must NOT shorten the prediction client, whose
        // 600s read/call timeout a slow Opus prediction depends on.
        val client = NetworkModule.provideOkHttpClient(
            store = store(),
            apiKey = "k",
            userAgent = "ua",
            authExchangeProvider = authProvider(),
            prefs = prefs(),
        )
        assertEquals(600_000, client.callTimeoutMillis)
    }
}
