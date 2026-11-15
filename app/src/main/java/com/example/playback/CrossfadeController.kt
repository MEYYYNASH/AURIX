package com.example.playback

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

data class CrossfadeSettings(
  val durationSeconds: Int = 0, // 0 = Off, 1..12 seconds
  val fadeOnManualSkip: Boolean = true,
  val isCrossfading: Boolean = false
)

object CrossfadeController {
  const val MIN_SECONDS = 0
  const val MAX_SECONDS = 12

  private val _state = MutableStateFlow(CrossfadeSettings())
  val state: StateFlow<CrossfadeSettings> = _state.asStateFlow()

  fun restoreSettings(durationSeconds: Int, fadeOnManualSkip: Boolean) {
    _state.value = CrossfadeSettings(
      durationSeconds = durationSeconds.coerceIn(MIN_SECONDS, MAX_SECONDS),
      fadeOnManualSkip = fadeOnManualSkip,
      isCrossfading = false
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

  fun setCrossfadingActive(active: Boolean) {
    _state.update { it.copy(isCrossfading = active) }
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
