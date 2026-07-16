package com.destinyai.astrology.services

import android.content.Context
import android.content.SharedPreferences
import com.destinyai.astrology.data.billing.BillingManager
import com.destinyai.astrology.data.remote.AstroApiService
import com.destinyai.astrology.data.remote.FeatureAccessResponse
import com.destinyai.astrology.data.remote.StatusResponse
import dagger.Lazy
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@ExperimentalCoroutinesApi
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class QuotaManagerTest {

    private lateinit var context: Context
    private lateinit var api: AstroApiService
    private lateinit var billingManager: BillingManager
    private lateinit var billingLazy: Lazy<BillingManager>
    private lateinit var sharedPrefs: SharedPreferences
    private lateinit var editor: SharedPreferences.Editor
    private lateinit var manager: QuotaManager

    // StatusResponse field list derived from AstroApiService.kt:
    //   userEmail (required), planId, isGeneratedEmail, isPremium (required),
    //   dailyQuota, dailyUsed, accessState, name, analyticsConsent,
    //   pendingUpgradePlanId, pendingUpgradeDate, subscriptionStatus,
    //   subscriptionExpiresAt, autoRenewStatus, planDisplayName
    private val freeStatus = StatusResponse(
        userEmail = "u@x.com",
        isPremium = false,
        planId = "free_registered",
        planDisplayName = "Free",
        subscriptionStatus = null,
        subscriptionExpiresAt = null,
        autoRenewStatus = null,
        dailyQuota = 3,
        dailyUsed = 0,
        accessState = "granted",
        analyticsConsent = false,
    )

    private val plusStatus = StatusResponse(
        userEmail = "u@x.com",
        isPremium = true,
        planId = "plus",
        planDisplayName = "Plus Yearly",
        subscriptionStatus = "active",
        subscriptionExpiresAt = "2027-01-01",
        autoRenewStatus = true,
        dailyQuota = null,
        dailyUsed = null,
        accessState = "granted",
        analyticsConsent = false,
    )

    @BeforeEach
    fun setUp() {
        editor = mockk(relaxed = true)
        every { editor.putBoolean(any(), any()) } returns editor
        every { editor.putString(any(), any()) } returns editor
        every { editor.putInt(any(), any()) } returns editor
        every { editor.remove(any()) } returns editor
        every { editor.clear() } returns editor
        every { editor.apply() } returns Unit

        sharedPrefs = mockk(relaxed = true)
        every { sharedPrefs.edit() } returns editor
        every { sharedPrefs.getBoolean(any(), any()) } returns false
        every { sharedPrefs.getString(any(), null) } returns null
        every { sharedPrefs.getInt(any(), any()) } returns 0
        every { sharedPrefs.contains(any()) } returns false

        context = mockk(relaxed = true)
        every { context.getSharedPreferences(any(), any()) } returns sharedPrefs

        api = mockk(relaxed = true)
        billingManager = mockk(relaxed = true)
        every { billingManager.isDirectPurchaseInProgress } returns false
        billingLazy = Lazy { billingManager }

        manager = QuotaManager(context, api, billingLazy)
    }

    @Test
    fun `syncStatus respects 5-minute cooldown`() = runTest {
        coEvery { api.getStatus(any()) } returns freeStatus

        manager.syncStatus("u@x.com", force = false)
        manager.syncStatus("u@x.com", force = false)

        coVerify(exactly = 1) { api.getStatus("u@x.com") }
    }

    @Test
    fun `syncStatus force=true bypasses cooldown`() = runTest {
        coEvery { api.getStatus(any()) } returns freeStatus

        manager.syncStatus("u@x.com", force = false)
        manager.syncStatus("u@x.com", force = true)

        coVerify(exactly = 2) { api.getStatus("u@x.com") }
    }

    @Test
    fun `syncStatus emits externalPlanChangeAlert when plan transitions to paid without direct purchase`() = runTest {
        coEvery { api.getStatus(any()) } returns freeStatus
        manager.syncStatus("u@x.com", force = true)
        assertNull(manager.externalPlanChangeAlert.value)

        coEvery { api.getStatus(any()) } returns plusStatus
        manager.syncStatus("u@x.com", force = true)

        assertNotNull(manager.externalPlanChangeAlert.value)
        assertEquals("plus", manager.externalPlanChangeAlert.value?.newPlanId)
    }

    @Test
    fun `syncStatus does NOT emit externalPlanChangeAlert when direct purchase is in progress`() = runTest {
        every { billingManager.isDirectPurchaseInProgress } returns true
        coEvery { api.getStatus(any()) } returns freeStatus
        manager.syncStatus("u@x.com", force = true)

        coEvery { api.getStatus(any()) } returns plusStatus
        manager.syncStatus("u@x.com", force = true)

        assertNull(manager.externalPlanChangeAlert.value)
    }

    @Test
    fun `expired plus status is treated as free (terminal paid status)`() = runTest {
        val expiredPlus = plusStatus.copy(subscriptionStatus = "expired")
        coEvery { api.getStatus(any()) } returns expiredPlus
        manager.syncStatus("u@x.com", force = true)

        // iOS parity (QuotaManager.swift:785-815): plan_id still "plus" but status is
        // terminal → isPlus=false, isFreePlan=true, canUpgrade=true.
        assertFalse(manager.isPlus)
        assertTrue(manager.isFreePlan)
        assertTrue(manager.canUpgrade)
        assertTrue(manager.isInTerminalPaidStatus)
    }

    @Test
    fun `subscriptionStatusDisplayText maps per-status labels`() = runTest {
        coEvery { api.getStatus(any()) } returns plusStatus.copy(subscriptionStatus = "billing_retry")
        manager.syncStatus("u@x.com", force = true)
        assertEquals("Payment Failed", manager.subscriptionStatusDisplayText())

        coEvery { api.getStatus(any()) } returns plusStatus.copy(subscriptionStatus = "grace_period")
        manager.syncStatus("u@x.com", force = true)
        assertEquals("Grace Period", manager.subscriptionStatusDisplayText())
    }

    @Test
    fun `canAsk fails open (returns true) when API throws`() = runTest {
        // iOS parity (QuotaManager.swift:460-473, iOS-6 fix): a transient network error
        // must NOT block a user with remaining quota — fail open; server enforces.
        coEvery { api.canAccessFeatureFull(any(), any(), any(), any()) } throws RuntimeException("network")

        val result = manager.canAsk("u@x.com")

        assertTrue(result)
    }

    @Test
    fun `canAsk returns true when canAccess=true`() = runTest {
        coEvery { api.canAccessFeatureFull(any(), any(), any(), any()) } returns
            FeatureAccessResponse(canAccess = true, reason = null)

        assertTrue(manager.canAsk("u@x.com"))
    }

    @Test
    fun `canAsk returns false when canAccess=false`() = runTest {
        coEvery { api.canAccessFeatureFull(any(), any(), any(), any()) } returns
            FeatureAccessResponse(canAccess = false, reason = "quota_exceeded")

        assertFalse(manager.canAsk("u@x.com"))
    }

    @Test
    fun `resetForSignOut clears all state including externalPlanChangeAlert`() = runTest {
        coEvery { api.getStatus(any()) } returns freeStatus
        manager.syncStatus("u@x.com", force = true)
        coEvery { api.getStatus(any()) } returns plusStatus
        manager.syncStatus("u@x.com", force = true)

        manager.resetForSignOut()

        assertNull(manager.externalPlanChangeAlert.value)
        assertFalse(manager.isPremium.value)
        assertNull(manager.currentPlanId.value)
    }
}
