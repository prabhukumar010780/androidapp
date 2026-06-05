package com.destinyai.astrology.services

import android.content.Context
import com.destinyai.astrology.data.local.prefs.UserPreferences
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

/**
 * SoundManager uses AudioTrack + procedural PCM synthesis (432Hz/528Hz Tibetan Bowl + binaural
 * drone) — identical approach to iOS CoreAudio. Tests verify the public API contract when sound
 * is disabled; AudioTrack is never invoked in that path.
 */
@ExperimentalCoroutinesApi
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SoundManagerTest {

    private lateinit var context: Context
    private lateinit var prefs: UserPreferences

    @BeforeEach
    fun setUp() {
        context = mockk(relaxed = true)
        prefs = mockk(relaxed = true)
        coEvery { prefs.isSoundEnabled() } returns false
        coEvery { prefs.isSoundEnabledFlow() } returns flowOf(false)
        coEvery { prefs.setSoundEnabled(any()) } returns Unit
    }

    @Test
    fun `playTap does not throw when sound is disabled`() {
        val manager = SoundManager(context, prefs)
        assertDoesNotThrow { manager.playTap() }
    }

    @Test
    fun `playSend does not throw when sound is disabled`() {
        val manager = SoundManager(context, prefs)
        assertDoesNotThrow { manager.playSend() }
    }

    @Test
    fun `playSuccess does not throw when sound is disabled`() {
        val manager = SoundManager(context, prefs)
        assertDoesNotThrow { manager.playSuccess() }
    }

    @Test
    fun `playError does not throw when sound is disabled`() {
        val manager = SoundManager(context, prefs)
        assertDoesNotThrow { manager.playError() }
    }

    @Test
    fun `playCardSelect does not throw when sound is disabled`() {
        val manager = SoundManager(context, prefs)
        assertDoesNotThrow { manager.playCardSelect() }
    }

    @Test
    fun `playButtonTap is alias for playTap`() {
        val manager = SoundManager(context, prefs)
        assertDoesNotThrow { manager.playButtonTap() }
        assertDoesNotThrow { manager.playTap() }
    }

    @Test
    fun `premiumContinue and premiumSuccess are aliases and do not throw`() {
        val manager = SoundManager(context, prefs)
        assertDoesNotThrow { manager.premiumContinue() }
        assertDoesNotThrow { manager.premiumSuccess() }
    }

    @Test
    fun `toggleSound returns new enabled state`() = runTest {
        coEvery { prefs.isSoundEnabled() } returns false
        coEvery { prefs.setSoundEnabled(true) } returns Unit
        val manager = SoundManager(context, prefs)

        val result = manager.toggleSound()

        assertTrue(result)
    }

    @Test
    fun `release does not throw`() {
        val manager = SoundManager(context, prefs)
        assertDoesNotThrow { manager.release() }
    }
}
