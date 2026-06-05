package com.destinyai.astrology.services

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.unmockkAll
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@ExperimentalCoroutinesApi
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NetworkMonitorTest {

    private lateinit var context: Context
    private lateinit var connectivityManager: ConnectivityManager

    @BeforeEach
    fun setUp() {
        // Mock NetworkRequest.Builder so the callbackFlow body doesn't NPE
        // on the JVM stub (addCapability returns null under isReturnDefaultValues=true).
        mockkConstructor(NetworkRequest.Builder::class)
        every { anyConstructed<NetworkRequest.Builder>().addCapability(any()) } answers {
            self as NetworkRequest.Builder
        }
        every { anyConstructed<NetworkRequest.Builder>().build() } returns mockk(relaxed = true)

        connectivityManager = mockk(relaxed = true)
        context = mockk(relaxed = true)
        every {
            context.getSystemService(Context.CONNECTIVITY_SERVICE)
        } returns connectivityManager
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `isOnline emits false when no active network`() = runTest {
        every { connectivityManager.activeNetwork } returns null

        val monitor = NetworkMonitor(context)
        val result = monitor.isOnline.first()

        assertFalse(result)
    }

    @Test
    fun `isOnline emits false when network has no INTERNET capability`() = runTest {
        val network = mockk<android.net.Network>(relaxed = true)
        val caps = mockk<NetworkCapabilities>(relaxed = true)
        every { connectivityManager.activeNetwork } returns network
        every { connectivityManager.getNetworkCapabilities(network) } returns caps
        every { caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) } returns false

        val monitor = NetworkMonitor(context)
        val result = monitor.isOnline.first()

        assertFalse(result)
    }

    @Test
    fun `isOnline emits true when network has INTERNET capability`() = runTest {
        val network = mockk<android.net.Network>(relaxed = true)
        val caps = mockk<NetworkCapabilities>(relaxed = true)
        every { connectivityManager.activeNetwork } returns network
        every { connectivityManager.getNetworkCapabilities(network) } returns caps
        every { caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) } returns true

        val monitor = NetworkMonitor(context)
        val result = monitor.isOnline.first()

        assertTrue(result)
    }
}
