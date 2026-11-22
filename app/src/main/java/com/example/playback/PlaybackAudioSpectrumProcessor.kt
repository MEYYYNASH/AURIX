package com.example.playback

import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sqrt

@OptIn(UnstableApi::class)
class PlaybackAudioSpectrumProcessor : BaseAudioProcessor() {

  private val bandFrequenciesHz = floatArrayOf(
    55f, 80f, 115f, 160f, 220f,
    300f, 410f, 560f, 750f, 1000f,
    1350f, 1800f, 2400f, 3200f, 4200f,
    5500f, 7000f, 8800f, 10800f, 13200f
  )

  private val smoothedBars = FloatArray(AudioSpectrumBus.BAR_COUNT)
  private val sampleWindow = FloatArray(WINDOW_SIZE)
  private var lastEmitMs = 0L

  override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
    if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
      return AudioFormat.NOT_SET
    }
    return inputAudioFormat
  }

  override fun queueInput(inputBuffer: ByteBuffer) {
    val remaining = inputBuffer.remaining()
    if (remaining <= 0) return

    val readSnapshot = inputBuffer.slice()
    val outBuffer = replaceOutputBuffer(remaining)
    outBuffer.put(inputBuffer)
    outBuffer.flip()

    val now = SystemClock.elapsedRealtime()
    if (now - lastEmitMs >= EMIT_INTERVAL_MS) {
      lastEmitMs = now
      analyzeBuffer(readSnapshot)
    }
  }

  private fun analyzeBuffer(buffer: ByteBuffer) {
    val channels = inputAudioFormat.channelCount.coerceAtLeast(1)
    val sampleRate = inputAudioFormat.sampleRate.coerceAtLeast(8000).toFloat()
    val bytesPerFrame = channels * 2
    val totalFrames = buffer.remaining() / bytesPerFrame
    if (totalFrames < 16) return

    val framesToRead = minOf(totalFrames, WINDOW_SIZE)
    val startPos = buffer.position()
    val step = max(1, totalFrames / framesToRead)

    for (i in 0 until framesToRead) {
      val frameOffset = startPos + (i * step * bytesPerFrame)
      if (frameOffset + 1 >= buffer.limit()) {
        sampleWindow[i] = 0f
        continue
      }
      var sum = 0f
      for (ch in 0 until channels) {
        val samplePos = frameOffset + ch * 2
        if (samplePos + 1 < buffer.limit()) {
          val lo = buffer.get(samplePos).toInt() and 0xFF
          val hi = buffer.get(samplePos + 1).toInt()
          val pcm16 = ((hi shl 8) or lo).toShort().toInt()
          sum += pcm16 / 32768f
        }
      }
      // Hann window taper to reduce spectral leakage
      val hann = 0.5f * (1f - cos((2.0 * PI * i) / (framesToRead - 1).coerceAtLeast(1)).toFloat())
      sampleWindow[i] = (sum / channels) * hann
    }

    val n = framesToRead
    val nextBars = FloatArray(AudioSpectrumBus.BAR_COUNT)
    for (b in 0 until AudioSpectrumBus.BAR_COUNT) {
      val targetHz = bandFrequenciesHz[b].coerceAtMost(sampleRate * 0.45f)
      val omega = (2.0 * PI * targetHz / sampleRate)
      val coeff = (2.0 * cos(omega)).toFloat()
      var q0: Float
      var q1 = 0f
      var q2 = 0f
      for (i in 0 until n) {
        q0 = coeff * q1 - q2 + sampleWindow[i]
        q2 = q1
        q1 = q0
      }
      val power = max(0f, q1 * q1 + q2 * q2 - q1 * q2 * coeff)
      val mag = sqrt(power) / (n * 0.35f)
      // Slight high-frequency tilt compensation so treble bars look lively alongside bass
      val tilt = 1f + (b.toFloat() / AudioSpectrumBus.BAR_COUNT) * 0.85f
      val compressed = (ln(1f + mag * tilt * 11f) / ln(12f)).coerceIn(0f, 1f)

      val prev = smoothedBars[b]
      val blended = if (compressed > prev) {
        prev + (compressed - prev) * 0.58f
      } else {
        prev + (compressed - prev) * 0.24f
      }
      smoothedBars[b] = blended.coerceIn(0f, 1f)
      nextBars[b] = smoothedBars[b]
    }
    AudioSpectrumBus.updateBars(nextBars)
  }

  override fun onFlush() {
    smoothedBars.fill(0f)
    AudioSpectrumBus.reset()
  }

  override fun onReset() {
    smoothedBars.fill(0f)
    AudioSpectrumBus.reset()
  }

  private companion object {
    const val WINDOW_SIZE = 128
    const val EMIT_INTERVAL_MS = 34L
  }
}
