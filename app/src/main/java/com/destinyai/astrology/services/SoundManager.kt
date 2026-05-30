package com.destinyai.astrology.services

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.destinyai.astrology.R
import com.destinyai.astrology.data.local.prefs.UserPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

/**
 * R2-Z2: Manages short UI sound effects using SoundPool.
 *
 * Sound resources live in res/raw/:
 *   sound_tap.ogg, sound_send.ogg, sound_success.ogg, sound_error.ogg
 *
 * If a resource is missing (0 returned by load) the play call is silently skipped —
 * SoundPool treats soundId 0 as a no-op, so the app never crashes while assets are
 * being added incrementally.
 *
 * Sound playback is gated by UserPreferences.isSoundEnabled() (default: true).
 */
@Singleton
class SoundManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: UserPreferences,
) {
    private val audioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    private val soundPool: SoundPool = SoundPool.Builder()
        .setMaxStreams(4)
        .setAudioAttributes(audioAttributes)
        .build()

    private val soundTap: Int = loadSound(R.raw.sound_tap)
    private val soundSend: Int = loadSound(R.raw.sound_send)
    private val soundSuccess: Int = loadSound(R.raw.sound_success)
    private val soundError: Int = loadSound(R.raw.sound_error)

    /** Light tap — e.g. button press */
    fun playTap() = play(soundTap)

    /** Message sent */
    fun playSend() = play(soundSend)

    /** Operation succeeded */
    fun playSuccess() = play(soundSuccess)

    /** Operation failed / validation error */
    fun playError() = play(soundError)

    fun release() {
        soundPool.release()
    }

    private fun loadSound(resId: Int): Int =
        try {
            soundPool.load(context, resId, 1)
        } catch (_: Exception) {
            0 // Resource not yet present — degrade gracefully
        }

    private fun play(soundId: Int) {
        if (soundId == 0) return
        val enabled = runBlocking { prefs.isSoundEnabled() }
        if (!enabled) return
        soundPool.play(soundId, 1f, 1f, 1, 0, 1f)
    }
}
