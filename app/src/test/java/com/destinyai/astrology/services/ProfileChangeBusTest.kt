package com.destinyai.astrology.services

import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@ExperimentalCoroutinesApi
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ProfileChangeBusTest {

    private val bus = ProfileChangeBus()

    @Test
    fun `emit delivers email to collector`() = runTest {
        bus.events.test {
            bus.emit("new@example.com")
            assertEquals("new@example.com", awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `multiple emits deliver in order`() = runTest {
        bus.events.test {
            bus.emit("first@example.com")
            bus.emit("second@example.com")
            assertEquals("first@example.com", awaitItem())
            assertEquals("second@example.com", awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `independent bus instances do not share events`() = runTest {
        val busA = ProfileChangeBus()
        val busB = ProfileChangeBus()

        busB.events.test {
            busA.emit("a@example.com")
            // busB should not receive busA's event
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }
}
