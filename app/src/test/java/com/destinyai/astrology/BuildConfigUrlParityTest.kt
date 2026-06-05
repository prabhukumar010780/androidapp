package com.destinyai.astrology

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BuildConfigUrlParityTest {

    @Test
    fun `BuildConfig API_BASE_URL host matches expected Cloud Run host`() {
        val url = BuildConfig.API_BASE_URL
        val host = java.net.URI(url).host

        when {
            url.contains("10.0.2.2") || url.contains("127.0.0.1") ->
                assertEquals("10.0.2.2", host, "Local flavor should point at emulator loopback")
            url.contains("test") ->
                assertEquals(
                    "astroapi-test-dsqvza5jza-ul.a.run.app",
                    host,
                    "Staging flavor must point at astroapi-test Cloud Run service"
                )
            else ->
                assertEquals(
                    "astroapi-prod-dsqvza5jza-ul.a.run.app",
                    host,
                    "Production flavor must point at astroapi-prod Cloud Run service"
                )
        }
    }

    @Test
    fun `BuildConfig API_BASE_URL uses HTTPS in non-local builds`() {
        val url = BuildConfig.API_BASE_URL
        if (!url.contains("10.0.2.2") && !url.contains("127.0.0.1")) {
            assert(url.startsWith("https://")) {
                "Non-local builds must use HTTPS, got: $url"
            }
        }
    }
}
