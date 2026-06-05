package com.destinyai.astrology.services

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.destinyai.astrology.data.local.prefs.UserPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * iOS parity (SoundManager.swift): procedural Tibetan-bowl / Solfeggio chimes plus a
 * subliminal binaural healing drone, fully generated as PCM in code (no static assets).
 *
 *   - tapBuffer       → 432Hz Tibetan bowl (button tap)
 *   - successBuffer   → 528 / 639 / 963 Solfeggio "Divine Chord" (success)
 *   - chimeBuffer     → 396Hz Root-chakra grounding tone (card select)
 *   - healingDrone    → 528Hz / 538Hz binaural alpha-wave (continuous ambient @ 0.03 vol)
 *
 * Engine lifecycle mirrors iOS:
 *   - sound disabled → never touches the audio session, ambient drone is torn down
 *   - sound enabled  → drone loops forever via a dedicated AudioTrack; SFX play through
 *                       short-lived AudioTrack instances so multiple cues can overlap
 */
@Singleton
class SoundManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: UserPreferences,
) {
    private val sampleRate = 44_100
    private val channelCount = 2

    // Pre-rendered PCM data — Float in [-1, 1] interleaved L,R,L,R,...
    private val tapBuffer: FloatArray = generateTibetanBowlBuffer(
        baseFrequency = 432.0,
        partials = doubleArrayOf(1.0, 2.0, 4.0),
        amplitudes = doubleArrayOf(0.4, 0.1, 0.05),
        duration = 0.4,
        decay = 6.0,
        attackTime = 0.04,
    )
    private val successBuffer: FloatArray = generateDivineChordBuffer(duration = 1.5)
    private val chimeBuffer: FloatArray = generateTibetanBowlBuffer(
        baseFrequency = 396.0,
        partials = doubleArrayOf(1.0, 1.5),
        amplitudes = doubleArrayOf(0.3, 0.1),
        duration = 0.2,
        decay = 10.0,
        attackTime = 0.05,
    )
    private val healingDroneBuffer: FloatArray = generateBinauralDroneBuffer(
        baseFrequency = 528.0,
        beatFrequency = 10.0,
        duration = 5.0,
    )

    // Ambient drone uses a dedicated looping AudioTrack so it can run while SFX overlap.
    private var ambientTrack: AudioTrack? = null
    private var ambientJob: Job? = null

    @Volatile
    private var enabledCached: Boolean = runBlocking { prefs.isSoundEnabled() }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    init {
        // iOS parity (SoundManager.swift:19-29): didSet observer flips engine state.
        scope.launch {
            prefs.isSoundEnabledFlow()
                .distinctUntilChanged()
                .onEach { enabled ->
                    enabledCached = enabled
                    if (enabled) startAmbientDrone() else tearDownEngine()
                }
                .collect()
        }
        if (enabledCached) startAmbientDrone()
    }

    // MARK: - Public API (iOS SoundManager.swift:341-356 parity)

    /** Light tap — e.g. button press. iOS: playButtonTap() */
    fun playTap() = play(tapBuffer, volume = 1.0f)

    /** iOS parity alias for playTap(). */
    fun playButtonTap() = playTap()

    /** Message sent — reuses the tap timbre. */
    fun playSend() = play(tapBuffer, volume = 1.0f)

    /** Operation succeeded — Solfeggio Divine Chord. iOS: playSuccess() */
    fun playSuccess() = play(successBuffer, volume = 1.0f)

    /** Operation failed / validation error — short grounding tone. */
    fun playError() = play(chimeBuffer, volume = 1.0f)

    /** iOS parity (SoundManager.swift:351): card-select uses the deeper grounding tone. */
    fun playCardSelect() = play(chimeBuffer, volume = 1.0f)

    /** iOS parity: silent slide transition. */
    fun playSlideTransition() { /* silent — matches iOS */ }

    /** iOS parity (SoundManager.swift:354): premiumContinue() aliases card-select. */
    fun premiumContinue() = playCardSelect()

    /** iOS parity (SoundManager.swift:355): premiumSuccess() aliases success. */
    fun premiumSuccess() = playSuccess()

    /**
     * iOS parity (SoundManager.swift:341-347): flips persisted flag and updates audio
     * engine state synchronously so a tap immediately after toggling-on is audible.
     */
    suspend fun toggleSound(): Boolean {
        val newVal = !enabledCached
        prefs.setSoundEnabled(newVal)
        enabledCached = newVal
        if (newVal) {
            startAmbientDrone()
            playButtonTap()
        } else {
            tearDownEngine()
        }
        return newVal
    }

    /** iOS parity (SoundManager.swift:129-134): stop ambient + release any held resources. */
    fun release() {
        tearDownEngine()
    }

    /** Observable enabled-state flow — used by UI surfaces to bind a sound-toggle button. */
    fun isSoundEnabledFlow(): kotlinx.coroutines.flow.Flow<Boolean> =
        prefs.isSoundEnabledFlow()

    // MARK: - Playback engine

    private fun play(buffer: FloatArray, volume: Float) {
        if (!enabledCached) return
        if (buffer.isEmpty()) return
        // Short-lived AudioTrack — fire-and-forget so multiple cues can overlap.
        scope.launch {
            try {
                val track = newPcmTrack(buffer.size / channelCount, looping = false)
                val pcm = floatToPcm16(buffer, volume)
                track.write(pcm, 0, pcm.size)
                track.play()
                // Wait for playback to finish, then release.
                val durationMs = (buffer.size.toLong() * 1000L) /
                    (sampleRate.toLong() * channelCount)
                kotlinx.coroutines.delay(durationMs + 100L)
                try { track.stop() } catch (_: Exception) {}
                track.release()
            } catch (_: Exception) {
                // Audio focus / hardware unavailable — silently degrade.
            }
        }
    }

    /** iOS parity (SoundManager.swift:136-145): start the looping subliminal drone. */
    @Synchronized
    private fun startAmbientDrone() {
        if (ambientTrack != null) return
        if (healingDroneBuffer.isEmpty()) return
        try {
            val frames = healingDroneBuffer.size / channelCount
            val track = newPcmTrack(frames, looping = true)
            val pcm = floatToPcm16(healingDroneBuffer, volume = 0.03f)
            track.write(pcm, 0, pcm.size)
            track.setLoopPoints(0, frames, -1) // -1 = loop forever
            track.play()
            ambientTrack = track
        } catch (_: Exception) {
            ambientTrack = null
        }
    }

    /** iOS parity (SoundManager.swift:129-134): stop drone + deactivate audio session. */
    @Synchronized
    private fun tearDownEngine() {
        ambientJob?.cancel()
        ambientJob = null
        ambientTrack?.let {
            try { it.stop() } catch (_: Exception) {}
            try { it.release() } catch (_: Exception) {}
        }
        ambientTrack = null
    }

    private fun newPcmTrack(frames: Int, looping: Boolean): AudioTrack {
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val format = AudioFormat.Builder()
            .setSampleRate(sampleRate)
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
            .build()
        val bufferBytes = frames * channelCount * 2 // 16-bit stereo
        val mode = if (looping) AudioTrack.MODE_STATIC else AudioTrack.MODE_STATIC
        return AudioTrack.Builder()
            .setAudioAttributes(attributes)
            .setAudioFormat(format)
            .setBufferSizeInBytes(bufferBytes)
            .setTransferMode(mode)
            .build()
    }

    private fun floatToPcm16(buffer: FloatArray, volume: Float): ShortArray {
        val out = ShortArray(buffer.size)
        var i = 0
        while (i < buffer.size) {
            val v = (buffer[i] * volume).coerceIn(-1f, 1f)
            out[i] = (v * Short.MAX_VALUE).toInt().toShort()
            i++
        }
        return out
    }

    // MARK: - Procedural synthesis (iOS SoundManager.swift:147-305 parity)

    /** Tibetan-bowl tone with stereo detune + tremolo + exponential decay envelope. */
    private fun generateTibetanBowlBuffer(
        baseFrequency: Double,
        partials: DoubleArray,
        amplitudes: DoubleArray,
        duration: Double,
        decay: Double,
        attackTime: Double,
    ): FloatArray {
        val frameCount = (duration * sampleRate).toInt()
        val out = FloatArray(frameCount * channelCount)
        val beatFreq = 1.0
        for (channel in 0 until channelCount) {
            val detune = if (channel == 0) -0.2 else 0.2
            for (i in 0 until frameCount) {
                val t = i.toDouble() / sampleRate
                var sample = 0.0
                for (idx in partials.indices) {
                    val freq = baseFrequency * partials[idx] + detune
                    val tremolo = 1.0 + 0.05 * sin(2.0 * PI * beatFreq * t)
                    sample += sin(2.0 * PI * freq * t) * amplitudes[idx] * tremolo
                }
                val attack = min(1.0, t / attackTime)
                val release = exp(-decay * t)
                out[i * channelCount + channel] = (sample * attack * release * 0.5).toFloat()
            }
        }
        return out
    }

    /** Solfeggio "Divine Chord" — 528 / 639 / 963 with 4Hz tremolo + soft swell envelope. */
    private fun generateDivineChordBuffer(duration: Double): FloatArray {
        val frameCount = (duration * sampleRate).toInt()
        val out = FloatArray(frameCount * channelCount)
        val freqs = doubleArrayOf(528.0, 639.0, 963.0)
        val amps = doubleArrayOf(0.4, 0.25, 0.1)
        for (channel in 0 until channelCount) {
            val detune = if (channel == 0) -0.5 else 0.5
            for (i in 0 until frameCount) {
                val t = i.toDouble() / sampleRate
                var sample = 0.0
                for (idx in freqs.indices) {
                    val tremolo = 1.0 + 0.1 * sin(2.0 * PI * 4.0 * t)
                    sample += sin(2.0 * PI * (freqs[idx] + detune) * t) * amps[idx] * tremolo
                }
                val attackTime = 0.4
                val attack = min(1.0, t / attackTime)
                val releaseTime = duration - 0.8
                val release = if (t > releaseTime) {
                    max(0.0, 1.0 - (t - releaseTime) / 0.8)
                } else 1.0
                out[i * channelCount + channel] = (sample * attack * release * 0.25).toFloat()
            }
        }
        return out
    }

    /** Subliminal binaural drone — pure sine, 528Hz left / 538Hz right (10Hz alpha beat). */
    private fun generateBinauralDroneBuffer(
        baseFrequency: Double,
        beatFrequency: Double,
        duration: Double,
    ): FloatArray {
        val frameCount = (duration * sampleRate).toInt()
        val out = FloatArray(frameCount * channelCount)
        val leftFreq = baseFrequency
        val rightFreq = baseFrequency + beatFrequency
        for (channel in 0 until channelCount) {
            val freq = if (channel == 0) leftFreq else rightFreq
            for (i in 0 until frameCount) {
                val t = i.toDouble() / sampleRate
                val sample = sin(2.0 * PI * freq * t)
                val fadeLen = 0.1
                val fadeIn = min(1.0, t / fadeLen)
                val fadeOut = if (t > duration - fadeLen) {
                    max(0.0, (duration - t) / fadeLen)
                } else 1.0
                // 0.01 base scale — drone is itself rendered at very low volume and
                // additionally attenuated to 0.03 in startAmbientDrone() to mirror iOS.
                out[i * channelCount + channel] =
                    (sample * fadeIn * fadeOut * 0.01).toFloat()
            }
        }
        return out
    }
}
