package com.example.ui.screens.onboarding

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaPlayer
import android.os.SystemClock
import com.example.model.LyricsResult
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.sin
import kotlin.random.Random

/**
 * LRC-style timestamped lyric line (seconds -> text).
 */
data class OnboardingLyricLine(
  val seconds: Float,
  val text: String
)

/**
 * Audio + Lyric Sync Engine for Onboarding Slide 2.
 *
 * - If [audioSrc] is provided (local file path / content URI / URL), plays it on loop via [MediaPlayer]
 *   and drives the lyric clock directly from the audio's live currentPosition and duration.
 * - If [audioSrc] is null or blank, synthesizes the exact 19.2s (8 bars x 2.4s, 8 steps/bar = 0.3s)
 *   original procedural track with sawtooth pad (900 Hz LP), triangle arpeggio [0,1,2,1,2,1,0,1],
 *   sine bass (steps 0 & 4), pitch-drop kick (150 -> 42 Hz), 1800 Hz bandpass snare (steps 2 & 6),
 *   7500 Hz highpass hi-hat, sine lead from bar 2+, and 0.3s feedback echo (feedback 0.38, wet 0.35).
 */
class OnboardingDemoAudioEngine(
  val audioSrc: String? = null
) {
  companion object {
    const val SAMPLE_RATE = 44100
    const val BARS = 8
    const val BAR_SECONDS = 2.4f
    const val STEPS_PER_BAR = 8
    const val STEP_SECONDS = 0.3f
    const val LOOP_DURATION_SECONDS = BARS * BAR_SECONDS // 19.2s

    const val MASTER_GAIN = 0.2f
    const val AMBIENT_VOLUME = 0.24f
    const val FULL_VOLUME = 0.78f
    const val ECHO_DELAY_SECONDS = 0.3f
    const val ECHO_FEEDBACK = 0.38f
    const val ECHO_WET = 0.35f

    val DEFAULT_LYRICS: List<OnboardingLyricLine> = listOf(
      OnboardingLyricLine(0.0f, "Harbor lights, silver glass"),
      OnboardingLyricLine(2.4f, "Neon rivers, midnight air"),
      OnboardingLyricLine(4.8f, "Trams roll through the rain"),
      OnboardingLyricLine(7.2f, "Skyline humming in my chest"),
      OnboardingLyricLine(9.6f, "Take me where it glows"),
      OnboardingLyricLine(12.0f, "Every street a golden line"),
      OnboardingLyricLine(14.4f, "Hold this night a while"),
      OnboardingLyricLine(16.8f, "Stay with me, Hong Kong")
    )

    private val ARP_PATTERN = intArrayOf(0, 1, 2, 1, 2, 1, 0, 1)
    private val CHORD_BASS_HZ = floatArrayOf(110.0f, 87.3f, 130.8f, 98.0f)
    private val CHORD_PADS_HZ = arrayOf(
      floatArrayOf(220.0f, 261.6f, 329.6f),
      floatArrayOf(174.6f, 220.0f, 261.6f),
      floatArrayOf(261.6f, 329.6f, 392.0f),
      floatArrayOf(196.0f, 246.9f, 293.7f)
    )
    private val LEAD_NOTES_HZ = arrayOf(
      floatArrayOf(440.0f, 523.25f, 659.25f, 587.33f),
      floatArrayOf(523.25f, 440.0f, 392.0f, 440.0f),
      floatArrayOf(523.25f, 659.25f, 783.99f, 659.25f),
      floatArrayOf(587.33f, 493.88f, 440.0f, 392.0f)
    )

    /**
     * Converts a real [LyricsResult] from the app's lyrics provider into timestamped lines
     * compatible with the onboarding karaoke highlight engine.
     */
    fun fromRealLyrics(result: LyricsResult?): List<OnboardingLyricLine> {
      val synced = result?.synced.orEmpty()
      if (synced.isEmpty()) return DEFAULT_LYRICS
      return synced.map { line ->
        OnboardingLyricLine(
          seconds = (line.timestampMs.coerceAtLeast(0L) / 1000f),
          text = line.text
        )
      }
    }

    /**
     * Active line index at time [timeSec].
     */
    fun activeLineIndex(timeSec: Float, lines: List<OnboardingLyricLine>): Int {
      if (lines.isEmpty()) return 0
      var idx = 0
      for (i in lines.indices) {
        if (timeSec >= lines[i].seconds) idx = i else break
      }
      return idx
    }

    /**
     * Exact karaoke fill calculation:
     * - Past lines: 1.0f
     * - Future lines: 0.0f
     * - Active line: (t - lineStart) / (nextLineStart - 0.35 - lineStart)
     */
    fun computeKaraokeFill(
      timeSec: Float,
      lineIndex: Int,
      lines: List<OnboardingLyricLine>,
      totalSeconds: Float = LOOP_DURATION_SECONDS
    ): Float {
      if (lineIndex !in lines.indices) return 0f
      val activeIdx = activeLineIndex(timeSec, lines)
      if (lineIndex < activeIdx) return 1f
      if (lineIndex > activeIdx) return 0f
      val lineStart = lines[lineIndex].seconds
      val nextStart = if (lineIndex + 1 < lines.size) lines[lineIndex + 1].seconds else totalSeconds
      val span = (nextStart - 0.35f - lineStart).coerceAtLeast(0.15f)
      return ((timeSec - lineStart) / span).coerceIn(0f, 1f)
    }

    fun formatSecondsTabular(seconds: Float): String {
      val clamped = seconds.coerceAtLeast(0f).toInt()
      val mins = clamped / 60
      val secs = clamped % 60
      return "$mins:${secs.toString().padStart(2, '0')}"
    }

    /**
     * Pure procedural PCM-16 synthesizer implementing the exact Section 7 Web Audio graph:
     * 8 bars x 2.4s = 19.2s, 8 steps/bar (0.3s each), master gain 0.2, 0.3s feedback echo (0.38 / 0.35 wet).
     */
    fun renderProceduralLoopPcm16(sampleRate: Int = SAMPLE_RATE): ShortArray {
      val totalSamples = (LOOP_DURATION_SECONDS * sampleRate).toInt()
      val dry = FloatArray(totalSamples)
      val rng = Random(2420) // deterministic seed for clean, repeatable hi-hat/snare texture

      val stepSamples = (STEP_SECONDS * sampleRate).toInt()
      val barSamples = (BAR_SECONDS * sampleRate).toInt()
      val twoPi = (2.0 * PI).toFloat()

      // Lowpass coefficient for pad (900 Hz)
      val padRc = 1f / (twoPi * 900f)
      val dt = 1f / sampleRate
      val padAlpha = dt / (padRc + dt)

      // 1) Synthesize Pad across each bar (sawtooth at half pitch, slightly detuned, LP 900 Hz, gain 0.07, 0.35s attack)
      var lpState = 0f
      for (bar in 0 until BARS) {
        val chord = CHORD_PADS_HZ[bar % 4]
        val barStart = bar * barSamples
        val barEnd = (barStart + barSamples).coerceAtMost(totalSamples)
        for (s in barStart until barEnd) {
          val tBar = (s - barStart) * dt
          val attack = (tBar / 0.35f).coerceIn(0f, 1f)
          val release = ((BAR_SECONDS - tBar) / 0.18f).coerceIn(0f, 1f)
          val env = attack * release
          var rawPad = 0f
          for (noteIdx in chord.indices) {
            val baseFreq = chord[noteIdx] * 0.5f
            val f1 = baseFreq * 0.996f
            val f2 = baseFreq * 1.004f
            val p1 = (tBar * f1) - floor(tBar * f1)
            val p2 = (tBar * f2) - floor(tBar * f2)
            rawPad += ((2f * p1 - 1f) + (2f * p2 - 1f)) * 0.5f
          }
          rawPad = (rawPad / chord.size) * env
          lpState += padAlpha * (rawPad - lpState)
          dry[s] += lpState * 0.07f
        }
      }

      // 2) Step-level voices: Arpeggio, Bass, Kick, Snare, Hi-Hat, Lead
      // Highpass filter state for hi-hat (~7500 Hz) and bandpass state for snare (~1800 Hz)
      val hpRc = 1f / (twoPi * 7500f)
      val hpAlpha = hpRc / (hpRc + dt)
      val snareFreq = 1800f
      val snareQ = 2.4f
      val snareW0 = twoPi * snareFreq / sampleRate
      val snareCos = kotlin.math.cos(snareW0.toDouble()).toFloat()
      val snareSin = kotlin.math.sin(snareW0.toDouble()).toFloat()
      val snareAlpha = snareSin / (2f * snareQ)
      val b0 = snareAlpha
      val b2 = -snareAlpha
      val a0 = 1f + snareAlpha
      val a1 = -2f * snareCos
      val a2 = 1f - snareAlpha

      for (bar in 0 until BARS) {
        val chordIndex = bar % 4
        val chord = CHORD_PADS_HZ[chordIndex]
        val bassFreq = CHORD_BASS_HZ[chordIndex]
        val leadNotes = LEAD_NOTES_HZ[chordIndex]

        for (step in 0 until STEPS_PER_BAR) {
          val stepStart = bar * barSamples + step * stepSamples

          // a) Arpeggio: triangle wave stepping through chord [0,1,2,1,2,1,0,1], gain 0.16
          val arpFreq = chord[ARP_PATTERN[step]]
          val arpLen = (0.28f * sampleRate).toInt()
          for (i in 0 until arpLen) {
            val idx = stepStart + i
            if (idx >= totalSamples) break
            val t = i * dt
            val env = (t / 0.012f).coerceIn(0f, 1f) * exp(-t * 9.5f)
            val phase = (t * arpFreq) - floor(t * arpFreq)
            val tri = 4f * kotlin.math.abs(phase - 0.5f) - 1f
            dry[idx] += tri * env * 0.16f
          }

          // b) Bass: sine on steps 0 and 4 (1.1s, gain 0.55)
          if (step == 0 || step == 4) {
            val bassLen = (1.1f * sampleRate).toInt()
            for (i in 0 until bassLen) {
              val idx = stepStart + i
              if (idx >= totalSamples) break
              val t = i * dt
              val env = (t / 0.018f).coerceIn(0f, 1f) * exp(-t * 2.2f)
              dry[idx] += sin(twoPi * bassFreq * t) * env * 0.55f
            }
          }

          // c) Kick: pitch drop 150 -> 42 Hz on even steps except 2 and 6, plus step 2 (steps 0, 2, 4)
          if (step == 0 || step == 2 || step == 4) {
            val kickLen = (0.24f * sampleRate).toInt()
            var kickPhase = 0f
            for (i in 0 until kickLen) {
              val idx = stepStart + i
              if (idx >= totalSamples) break
              val t = i * dt
              val freq = 42f + (150f - 42f) * exp(-t * 28f)
              kickPhase += twoPi * freq * dt
              val env = exp(-t * 14f)
              dry[idx] += sin(kickPhase) * env * 0.65f
            }
          }

          // d) Snare: bandpass noise at 1800 Hz on steps 2 and 6
          if (step == 2 || step == 6) {
            val snareLen = (0.18f * sampleRate).toInt()
            var x1 = 0f
            var x2 = 0f
            var y1 = 0f
            var y2 = 0f
            for (i in 0 until snareLen) {
              val idx = stepStart + i
              if (idx >= totalSamples) break
              val t = i * dt
              val n = rng.nextFloat() * 2f - 1f
              val y = (b0 * n + b2 * x2 - a1 * y1 - a2 * y2) / a0
              x2 = x1
              x1 = n
              y2 = y1
              y1 = y
              val env = exp(-t * 20f)
              dry[idx] += y * env * 0.42f
            }
          }

          // e) Hi-hat: highpass noise at 7500 Hz on every step (alternating gain 0.10 / 0.16)
          val hatGain = if (step % 2 == 0) 0.10f else 0.16f
          val hatLen = (0.07f * sampleRate).toInt()
          var prevIn = 0f
          var prevOut = 0f
          for (i in 0 until hatLen) {
            val idx = stepStart + i
            if (idx >= totalSamples) break
            val t = i * dt
            val n = rng.nextFloat() * 2f - 1f
            val hp = hpAlpha * (prevOut + n - prevIn)
            prevIn = n
            prevOut = hp
            val env = exp(-t * 52f)
            dry[idx] += hp * env * hatGain
          }

          // f) Sine lead melody enters from bar 2 onward on even steps
          if (bar >= 2 && step % 2 == 0) {
            val leadFreq = leadNotes[(step / 2) % leadNotes.size]
            val leadLen = (0.48f * sampleRate).toInt()
            for (i in 0 until leadLen) {
              val idx = stepStart + i
              if (idx >= totalSamples) break
              val t = i * dt
              val env = (t / 0.025f).coerceIn(0f, 1f) * exp(-t * 4.2f)
              dry[idx] += sin(twoPi * leadFreq * t) * env * 0.22f
            }
          }
        }
      }

      // 3) Feedback echo (delay 0.3s, feedback 0.38, wet 0.35) + master gain 0.2
      val delaySamples = (ECHO_DELAY_SECONDS * sampleRate).toInt().coerceAtLeast(1)
      val delayBuf = FloatArray(delaySamples)
      var delayCursor = 0
      val out = ShortArray(totalSamples)
      for (i in 0 until totalSamples) {
        val drySample = dry[i]
        val delayed = delayBuf[delayCursor]
        delayBuf[delayCursor] = drySample + delayed * ECHO_FEEDBACK
        delayCursor = (delayCursor + 1) % delaySamples
        val mixed = (drySample + delayed * ECHO_WET) * MASTER_GAIN * 2.2f
        val clamped = mixed.coerceIn(-0.98f, 0.98f)
        out[i] = (clamped * Short.MAX_VALUE).toInt().toShort()
      }
      return out
    }
  }

  private var audioTrack: AudioTrack? = null
  private var mediaPlayer: MediaPlayer? = null
  private var cachedPcm: ShortArray? = null
  @Volatile private var isPlayingInternal = false
  @Volatile private var currentVolumeInternal = AMBIENT_VOLUME
  @Volatile private var fadeGeneration = 0L
  private var trackStartElapsedMs = 0L
  private var trackStartOffsetSec = 0f

  val isPlaying: Boolean
    get() = isPlayingInternal

  val currentVolume: Float
    get() = currentVolumeInternal

  var totalDurationSeconds: Float = LOOP_DURATION_SECONDS
    private set

  /**
   * Starts audio playback at a gentle ambient volume ([volume], defaulting to [AMBIENT_VOLUME] = 0.24f)
   * with a smooth fade-in so selecting a song feels atmospheric rather than startling.
   */
  fun start(
    initialTimeSec: Float = 0f,
    overrideAudioSrc: String? = null,
    context: android.content.Context? = null,
    volume: Float = AMBIENT_VOLUME,
    fadeInMs: Long = 520L
  ): Boolean {
    stop()
    val targetVol = volume.coerceIn(0.02f, 1f)
    currentVolumeInternal = targetVol
    val gen = ++fadeGeneration
    val customSrc = (overrideAudioSrc ?: audioSrc)?.trim().orEmpty()
    if (customSrc.isNotEmpty()) {
      val startedReal = runCatching {
        val mp = MediaPlayer().apply {
          setAudioAttributes(
            AudioAttributes.Builder()
              .setUsage(AudioAttributes.USAGE_MEDIA)
              .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
              .build()
          )
          if (customSrc.startsWith("content://") && context != null) {
            setDataSource(context, android.net.Uri.parse(customSrc))
          } else if (customSrc.startsWith("http") && context != null) {
            val ua = com.example.data.StreamHeaderRegistry.resolveUserAgent(customSrc)
            if (!ua.isNullOrBlank()) {
              setDataSource(context, android.net.Uri.parse(customSrc), mapOf("User-Agent" to ua))
            } else {
              setDataSource(customSrc)
            }
          } else {
            setDataSource(customSrc)
          }
          isLooping = true
          prepare()
          val durSec = (duration / 1000f).coerceAtLeast(1f)
          totalDurationSeconds = durSec
          val seekMs = ((initialTimeSec % durSec) * 1000f).toInt().coerceIn(0, duration)
          seekTo(seekMs)
          val initialVol = if (fadeInMs > 0L) 0.01f else targetVol
          setVolume(initialVol, initialVol)
          start()
        }
        mediaPlayer = mp
        isPlayingInternal = true
        if (fadeInMs > 0L) {
          startFadeThread(gen = gen, fromVol = 0.01f, toVol = targetVol, durationMs = fadeInMs, stopAfter = false)
        }
        true
      }.getOrElse {
        stop()
        false
      }
      if (startedReal) return true
    }

    return runCatching {
      val pcm = cachedPcm ?: renderProceduralLoopPcm16().also { cachedPcm = it }
      val byteCount = pcm.size * 2
      val track = AudioTrack.Builder()
        .setAudioAttributes(
          AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        )
        .setAudioFormat(
          AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(SAMPLE_RATE)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .build()
        )
        .setTransferMode(AudioTrack.MODE_STATIC)
        .setBufferSizeInBytes(byteCount)
        .build()

      val written = track.write(pcm, 0, pcm.size)
      if (written <= 0) {
        track.release()
        return false
      }
      val startFrame = (((initialTimeSec % LOOP_DURATION_SECONDS) + LOOP_DURATION_SECONDS) % LOOP_DURATION_SECONDS * SAMPLE_RATE)
        .toInt()
        .coerceIn(0, pcm.size - 1)
      track.setLoopPoints(0, pcm.size, -1)
      runCatching { track.setPlaybackHeadPosition(startFrame) }
      val initialVol = if (fadeInMs > 0L) 0.01f else targetVol
      runCatching { track.setVolume(initialVol) }
      track.play()
      audioTrack = track
      totalDurationSeconds = LOOP_DURATION_SECONDS
      trackStartOffsetSec = startFrame.toFloat() / SAMPLE_RATE
      trackStartElapsedMs = SystemClock.elapsedRealtime()
      isPlayingInternal = true
      if (fadeInMs > 0L) {
        startFadeThread(gen = gen, fromVol = 0.01f, toVol = targetVol, durationMs = fadeInMs, stopAfter = false)
      }
      true
    }.getOrElse {
      stop()
      false
    }
  }

  fun setVolume(volume: Float) {
    val clamped = volume.coerceIn(0f, 1f)
    currentVolumeInternal = clamped
    fadeGeneration++
    applyVolumeNow(clamped)
  }

  fun fadeOutAndStop(durationMs: Long = 380L) {
    if (!isPlayingInternal) {
      stop()
      return
    }
    val gen = ++fadeGeneration
    startFadeThread(
      gen = gen,
      fromVol = currentVolumeInternal,
      toVol = 0f,
      durationMs = durationMs.coerceAtLeast(80L),
      stopAfter = true
    )
  }

  private fun applyVolumeNow(vol: Float) {
    val v = vol.coerceIn(0f, 1f)
    runCatching { mediaPlayer?.setVolume(v, v) }
    runCatching { audioTrack?.setVolume(v) }
  }

  private fun startFadeThread(
    gen: Long,
    fromVol: Float,
    toVol: Float,
    durationMs: Long,
    stopAfter: Boolean
  ) {
    Thread {
      val steps = 16
      val stepMs = (durationMs / steps).coerceAtLeast(10L)
      for (i in 1..steps) {
        if (fadeGeneration != gen || !isPlayingInternal) return@Thread
        val t = i.toFloat() / steps
        val vol = fromVol + (toVol - fromVol) * t
        applyVolumeNow(vol)
        try {
          Thread.sleep(stepMs)
        } catch (_: InterruptedException) {
          return@Thread
        }
      }
      if (stopAfter && fadeGeneration == gen) {
        stop()
      }
    }.apply { isDaemon = true }.start()
  }

  /**
   * Returns current playback time in seconds while sound is on.
   */
  fun currentTimeSeconds(): Float {
    val mp = mediaPlayer
    if (mp != null && isPlayingInternal) {
      return runCatching { (mp.currentPosition / 1000f).coerceIn(0f, totalDurationSeconds) }
        .getOrDefault(0f)
    }
    val track = audioTrack
    if (track != null && isPlayingInternal) {
      val elapsedSec = (SystemClock.elapsedRealtime() - trackStartElapsedMs) / 1000f
      return ((trackStartOffsetSec + elapsedSec) % LOOP_DURATION_SECONDS + LOOP_DURATION_SECONDS) % LOOP_DURATION_SECONDS
    }
    return 0f
  }

  fun stop() {
    fadeGeneration++
    isPlayingInternal = false
    runCatching {
      mediaPlayer?.stop()
      mediaPlayer?.release()
    }
    mediaPlayer = null
    runCatching {
      audioTrack?.pause()
      audioTrack?.flush()
      audioTrack?.stop()
      audioTrack?.release()
    }
    audioTrack = null
  }

  fun release() {
    stop()
    cachedPcm = null
  }
}
