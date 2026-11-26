package com.example.playback

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

data class CrossfadeSettings(
  val durationSeconds: Int = 0, // 0 = Off, 1..12 seconds
  val fadeOnManualSkip: Boolean = true,
  val isCrossfading: Boolean = false,
  val djAutoMixEnabled: Boolean = false,
  val activeBeatfadeSeconds: Int = 0,
  val activeBpmMatchPercent: Int = 0
) {
  /**
   * Effective transition duration used by PlaybackService:
   * when DJ Auto-Mix is enabled, uses BPM-matched beatfade duration (2–6s),
   * otherwise uses the manual crossfade slider setting.
   */
  val effectiveDurationSeconds: Int
    get() = if (djAutoMixEnabled) {
      activeBeatfadeSeconds.coerceIn(2, 6)
    } else {
      durationSeconds.coerceIn(CrossfadeController.MIN_SECONDS, CrossfadeController.MAX_SECONDS)
    }
}

object CrossfadeController {
  const val MIN_SECONDS = 0
  const val MAX_SECONDS = 12
  const val DEFAULT_DJ_BEATFADE_SECONDS = 4

  private val _state = MutableStateFlow(CrossfadeSettings())
  val state: StateFlow<CrossfadeSettings> = _state.asStateFlow()

  fun restoreSettings(
    durationSeconds: Int,
    fadeOnManualSkip: Boolean,
    djAutoMixEnabled: Boolean = false
  ) {
    _state.value = CrossfadeSettings(
      durationSeconds = durationSeconds.coerceIn(MIN_SECONDS, MAX_SECONDS),
      fadeOnManualSkip = fadeOnManualSkip,
      isCrossfading = false,
      djAutoMixEnabled = djAutoMixEnabled,
      activeBeatfadeSeconds = if (djAutoMixEnabled) DEFAULT_DJ_BEATFADE_SECONDS else 0,
      activeBpmMatchPercent = if (djAutoMixEnabled) 100 else 0
    )
  }

  fun setDurationSeconds(seconds: Int): CrossfadeSettings {
    val clamped = seconds.coerceIn(MIN_SECONDS, MAX_SECONDS)
    _state.update { it.copy(durationSeconds = clamped) }
    return _state.value
  }

  fun setFadeOnManualSkip(enabled: Boolean): CrossfadeSettings {
    _state.update { it.copy(fadeOnManualSkip = enabled) }
    return _state.value
  }

  fun setDjAutoMixEnabled(enabled: Boolean): CrossfadeSettings {
    _state.update { current ->
      current.copy(
        djAutoMixEnabled = enabled,
        activeBeatfadeSeconds = if (enabled) {
          current.activeBeatfadeSeconds.takeIf { it in 2..6 } ?: DEFAULT_DJ_BEATFADE_SECONDS
        } else {
          0
        }
      )
    }
    return _state.value
  }

  /**
   * Updates the active BPM-matched beatfade duration for the upcoming queue transition.
   * Close tempos (including half/double-time harmonic matches) receive a longer, smoother
   * beatfade (5–6s); larger tempo jumps receive a tight 2–3s gapless-style blend.
   */
  fun updateDjTransitionMetrics(currentBpm: Int, nextBpm: Int) {
    val matchPct = computeBpmMatchPercent(currentBpm, nextBpm)
    val beatfadeSec = computeBeatfadeDurationSeconds(currentBpm, nextBpm)
    _state.update { current ->
      current.copy(
        activeBeatfadeSeconds = beatfadeSec,
        activeBpmMatchPercent = matchPct
      )
    }
  }

  fun setCrossfadingActive(active: Boolean) {
    _state.update { it.copy(isCrossfading = active) }
  }

  /**
   * Computes harmonic BPM delta accounting for direct match, half-time (0.5x), and double-time (2x).
   */
  fun effectiveBpmDelta(bpmA: Int, bpmB: Int): Float {
    if (bpmA <= 0 || bpmB <= 0) return 12f
    val a = bpmA.toFloat()
    val b = bpmB.toFloat()
    val direct = abs(a - b)
    val half = abs(a - b * 0.5f)
    val dbl = abs(a - b * 2.0f)
    return min(direct, min(half, dbl))
  }

  /**
   * Returns 0..100 compatibility score between two tempos.
   */
  fun computeBpmMatchPercent(currentBpm: Int, nextBpm: Int): Int {
    if (currentBpm <= 0 || nextBpm <= 0) return 80
    val delta = effectiveBpmDelta(currentBpm, nextBpm)
    return (100f - delta * 2.4f).toInt().coerceIn(35, 100)
  }

  /**
   * Computes optimal DJ beatfade overlap in seconds (2..6s):
   * - <= 4 BPM delta: 6s lush beat-matched blend
   * - <= 10 BPM delta: 5s smooth rhythmic blend
   * - <= 18 BPM delta: 3s brisk transition
   * - > 18 BPM delta: 2s tight gapless handoff
   */
  fun computeBeatfadeDurationSeconds(currentBpm: Int, nextBpm: Int): Int {
    if (currentBpm <= 0 || nextBpm <= 0) return DEFAULT_DJ_BEATFADE_SECONDS
    val delta = effectiveBpmDelta(currentBpm, nextBpm)
    return when {
      delta <= 4f -> 6
      delta <= 10f -> 5
      delta <= 18f -> 3
      else -> 2
    }
  }

  /**
   * Returns (outgoingVolume, incomingVolume) using equal-power sine/cosine curves
   * so perceived loudness stays balanced without a dip in the middle of the crossfade.
   */
  fun equalPowerGains(progress: Float): Pair<Float, Float> {
    val t = progress.coerceIn(0f, 1f)
    val angle = t * (PI / 2.0)
    val outGain = cos(angle).toFloat().coerceIn(0f, 1f)
    val inGain = sin(angle).toFloat().coerceIn(0f, 1f)
    return outGain to inGain
  }
}
