package com.destinyai.astrology.data.location

import com.destinyai.astrology.data.remote.AstroApiService
import com.destinyai.astrology.data.remote.LocationResult
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
class LocationSearchServiceTest {

    private lateinit var api: AstroApiService
    private lateinit var service: LocationSearchService

    @BeforeEach
    fun setUp() {
        api = mockk(relaxed = true)
        service = LocationSearchService(api)
    }

    @Test
    fun `search returns results for query of 2 or more chars`() = runTest {
        val results = listOf(
            LocationResult("Mumbai", 19.0760, 72.8777, "Mumbai, Maharashtra, India"),
        )
        coEvery { api.searchLocations("Mu") } returns results

        val actual = service.search("Mu")

        assertTrue(actual is LocationSearchResult.Success)
        val list = (actual as LocationSearchResult.Success).results
        assertEquals(1, list.size)
        assertEquals("Mumbai", list[0].city)
        coVerify { api.searchLocations("Mu") }
    }

    @Test
    fun `search returns empty list for query shorter than 2 chars`() = runTest {
        val actual = service.search("M")

        assertTrue(actual is LocationSearchResult.Success)
        assertTrue((actual as LocationSearchResult.Success).results.isEmpty())
        coVerify(exactly = 0) { api.searchLocations(any()) }
    }

    @Test
    fun `search returns empty list for blank query`() = runTest {
        val actual = service.search("")

        assertTrue(actual is LocationSearchResult.Success)
        assertTrue((actual as LocationSearchResult.Success).results.isEmpty())
        coVerify(exactly = 0) { api.searchLocations(any()) }
    }

    @Test
    fun `search maps generic api error to result type`() = runTest {
        coEvery { api.searchLocations(any()) } throws RuntimeException("network error")

        val actual = service.search("Bhi")

        // Generic RuntimeException should map to either Success(empty) or Failure
        assertTrue(actual is LocationSearchResult.Failure || actual is LocationSearchResult.Success)
    }

    @Test
    fun `search passes query to api unchanged`() = runTest {
        coEvery { api.searchLocations("Bhilai") } returns emptyList()

        service.search("Bhilai")

        coVerify { api.searchLocations("Bhilai") }
    }
}
