package com.destinyai.astrology.services

import android.content.Context
import android.os.Build
import android.os.Vibrator
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

/**
 * HapticManager unit tests.
 *
 * Strategy: mock the Context so that getSystemService returns a mock Vibrator.
 * We verify that:
 *   1. Each public method completes without throwing on API 26+ paths (Robolectric SDK = 34).
 *   2. No vibration is attempted when hasVibrator() returns false.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HapticManagerTest {

    private lateinit var context: Context
    private lateinit var vibrator: Vibrator
    private lateinit var manager: HapticManager

    @BeforeEach
    fun setUp() {
        vibrator = mockk(relaxed = true)
        context = mockk(relaxed = true)

        // Route VIBRATOR_SERVICE to our mock (API < 31 path in HapticManager)
        every { context.getSystemService(Context.VIBRATOR_SERVICE) } returns vibrator
        // hasVibrator() returns true by default (relaxed mock returns false for Boolean — set explicitly)
        every { vibrator.hasVibrator() } returns true

        manager = HapticManager(context)
    }

    @Test
    fun `light does not throw`() {
        assertDoesNotThrow { manager.light() }
    }

    @Test
    fun `medium does not throw`() {
        assertDoesNotThrow { manager.medium() }
    }

    @Test
    fun `success does not throw`() {
        assertDoesNotThrow { manager.success() }
    }

    @Test
    fun `error does not throw`() {
        assertDoesNotThrow { manager.error() }
    }

    @Test
    fun `no vibration when hasVibrator returns false`() {
        every { vibrator.hasVibrator() } returns false

        manager.light()
        manager.medium()
        manager.success()
        manager.error()

        // vibrate() should never be called
        verify(exactly = 0) { vibrator.vibrate(any<Long>()) }
    }
}
