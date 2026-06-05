package com.destinyai.astrology.services

import com.destinyai.astrology.data.local.prefs.UserPreferences
import com.destinyai.astrology.data.remote.AstroApiService
import com.destinyai.astrology.data.remote.DeviceTokenRequest
import com.destinyai.astrology.data.remote.SuccessResponse
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@ExperimentalCoroutinesApi
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FcmTokenManagerTest {

    private lateinit var api: AstroApiService
    private lateinit var prefs: UserPreferences
    private lateinit var manager: FcmTokenManager

    @BeforeEach
    fun setUp() {
        api = mockk(relaxed = true)
        prefs = mockk(relaxed = true)
        coEvery { prefs.getUserEmail() } returns "u@x.com"
        coEvery { api.registerDeviceToken(any()) } returns SuccessResponse(true)
        manager = FcmTokenManager(api, prefs)
    }

    @Test
    fun `registerToken saves token to prefs`() = runTest {
        manager.registerToken("token-abc-123", "1.0")
        coVerify { prefs.setFcmToken("token-abc-123") }
    }

    @Test
    fun `registerToken calls api with correct platform and token`() = runTest {
        manager.registerToken("token-abc-123", "1.0")
        coVerify {
            api.registerDeviceToken(match { req ->
                req.token == "token-abc-123" &&
                    req.platform == "android" &&
                    req.userEmail == "u@x.com"
            })
        }
    }

    @Test
    fun `registerToken marks token as registered on success`() = runTest {
        manager.registerToken("token-abc-123", "1.0")
        coVerify { prefs.setFcmTokenRegistered(true) }
    }

    @Test
    fun `registerToken does nothing when no user email`() = runTest {
        coEvery { prefs.getUserEmail() } returns null

        manager.registerToken("token-abc-123", "1.0")

        coVerify(exactly = 0) { api.registerDeviceToken(any()) }
    }

    @Test
    fun `registerToken saves token to prefs even when api fails`() = runTest {
        coEvery { api.registerDeviceToken(any()) } throws RuntimeException("network error")

        manager.registerToken("token-abc-123", "1.0")

        coVerify { prefs.setFcmToken("token-abc-123") }
        coVerify(exactly = 0) { prefs.setFcmTokenRegistered(any()) }
    }

    @Test
    fun `registerToken on second call with same token re-registers with backend`() = runTest {
        // MainActivity calls registerToken on every onCreate (and every onStart via FCM token).
        // The second call must POST to backend again — registration is idempotent on server side.
        manager.registerToken("stable-token-xyz", "2.0")
        manager.registerToken("stable-token-xyz", "2.0")  // Second foreground

        // Both calls should hit the API — server handles idempotency
        coVerify(exactly = 2) {
            api.registerDeviceToken(match { it.token == "stable-token-xyz" })
        }
    }

    @Test
    fun `registerToken with new token replaces cached token`() = runTest {
        manager.registerToken("old-token", "1.0")
        manager.registerToken("new-token", "1.0")  // Token rotated by FCM

        coVerify { prefs.setFcmToken("new-token") }
        coVerify {
            api.registerDeviceToken(match { it.token == "new-token" })
        }
    }
}
