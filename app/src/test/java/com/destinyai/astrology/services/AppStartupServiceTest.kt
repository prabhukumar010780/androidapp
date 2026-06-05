package com.destinyai.astrology.services

import com.destinyai.astrology.data.remote.AppConfigResponse
import com.destinyai.astrology.data.remote.AstroApiService
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
class AppStartupServiceTest {

    private lateinit var api: AstroApiService
    private lateinit var service: AppStartupService

    @BeforeEach
    fun setUp() {
        api = mockk(relaxed = true)
        service = AppStartupService(api)
    }

    @Test
    fun `initial gateMode is off and allowGuest is false`() {
        assertEquals("off", service.gateMode.value)
        assertFalse(service.allowGuest.value)
    }

    @Test
    fun `fetchConfig updates gateMode and allowGuest from backend`() = runTest {
        coEvery { api.getAppConfig() } returns AppConfigResponse(
            gateMode = "waitlist",
            allowGuest = true,
        )

        service.fetchConfig()

        assertEquals("waitlist", service.gateMode.value)
        assertTrue(service.allowGuest.value)
    }

    @Test
    fun `fetchConfig respects 15-min TTL cache`() = runTest {
        coEvery { api.getAppConfig() } returns AppConfigResponse(gateMode = "off", allowGuest = false)

        service.fetchConfig()
        service.fetchConfig()

        coVerify(exactly = 1) { api.getAppConfig() }
    }

    @Test
    fun `fetchConfig leaves prior values intact on network error`() = runTest {
        coEvery { api.getAppConfig() } returns AppConfigResponse(gateMode = "promotion", allowGuest = true)
        service.fetchConfig()
        assertEquals("promotion", service.gateMode.value)

        val service2 = AppStartupService(api)
        coEvery { api.getAppConfig() } throws RuntimeException("network down")
        service2.fetchConfig()

        assertEquals("off", service2.gateMode.value)
        assertFalse(service2.allowGuest.value)
    }

    @Test
    fun `fetchConfig with gateMode=waitlist routes correctly`() = runTest {
        coEvery { api.getAppConfig() } returns AppConfigResponse(
            gateMode = "waitlist",
            allowGuest = false,
        )

        service.fetchConfig()

        assertEquals("waitlist", service.gateMode.value)
        assertFalse(service.allowGuest.value)
    }
}
