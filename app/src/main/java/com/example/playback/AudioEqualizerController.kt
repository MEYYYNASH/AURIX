package com.example.playback

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.roundToInt

enum class EqPreset(
  val id: String,
  val normalizedBandsMb: List<Int>,
  val bassBoostStrength: Int
) {
  FLAT("flat", listOf(0, 0, 0, 0, 0), 0),
  BASS_BOOST("bass_boost", listOf(520, 300, 20, -60, 140), 650),
  VOCAL("vocal", listOf(-180, 120, 420, 280, 100), 120),
  POP("pop", listOf(-100, 220, 340, 200, -80), 220),
  ROCK("rock", listOf(420, 200, -140, 260, 460), 360),
  CLUB("club", listOf(560, 360, 80, 300, 480), 780),
  CUSTOM("custom", listOf(0, 0, 0, 0, 0), 0);

  companion object {
    val selectablePresets = listOf(FLAT, BASS_BOOST, VOCAL, POP, ROCK, CLUB)

    fun fromId(id: String?): EqPreset {
      return entries.firstOrNull { it.id == id } ?: FLAT
    }
  }
}

data class EqualizerUiState(
  val enabled: Boolean = false,
  val presetId: String = EqPreset.FLAT.id,
  val bandFrequenciesHz: List<Int> = listOf(60, 230, 910, 3600, 14000),
  val bandLevelsMb: List<Int> = listOf(0, 0, 0, 0, 0),
  val minLevelMb: Int = -1500,
  val maxLevelMb: Int = 1500,
  val bassBoostStrength: Int = 0,
  val audioSessionId: Int = 0,
  val hardwareSupported: Boolean = true
)

object AudioEqualizerController {
  private val lock = Any()
  private var equalizer: Equalizer? = null
  private var bassBoost: BassBoost? = null
  private var currentSessionId: Int = 0

  private val _state = MutableStateFlow(EqualizerUiState())
  val state: StateFlow<EqualizerUiState> = _state.asStateFlow()

  fun restoreSettings(
    enabled: Boolean,
    presetId: String,
    bandLevelsMb: List<Int>,
    bassBoostStrength: Int
  ) {
    synchronized(lock) {
      val current = _state.value
      val count = current.bandFrequenciesHz.size.coerceAtLeast(1)
      val sanitizedLevels = if (bandLevelsMb.size == count) {
        bandLevelsMb.map { it.coerceIn(current.minLevelMb, current.maxLevelMb) }
      } else {
        val preset = EqPreset.fromId(presetId)
        resampleLevels(preset.normalizedBandsMb, count, current.minLevelMb, current.maxLevelMb)
      }
      _state.value = current.copy(
        enabled = enabled,
        presetId = EqPreset.fromId(presetId).id,
        bandLevelsMb = sanitizedLevels,
        bassBoostStrength = bassBoostStrength.coerceIn(0, 1000)
      )
      applyToHardwareLocked()
    }
  }

  fun attachAudioSession(sessionId: Int) {
    if (sessionId <= 0) return
    synchronized(lock) {
      if (sessionId == currentSessionId && equalizer != null) {
        applyToHardwareLocked()
        return
      }
      releaseHardwareLocked()
      currentSessionId = sessionId

      var supported = false
      var minMb = -1500
      var maxMb = 1500
      val freqs = mutableListOf<Int>()

      runCatching {
        val eq = Equalizer(0, sessionId)
        val numBands = eq.numberOfBands.toInt().coerceIn(1, 10)
        val range = eq.bandLevelRange
        minMb = range[0].toInt()
        maxMb = range[1].toInt()
        for (i in 0 until numBands) {
          freqs.add((eq.getCenterFreq(i.toShort()) / 1000).coerceAtLeast(20))
        }
        equalizer = eq
        supported = true
      }

      runCatching {
        bassBoost = BassBoost(0, sessionId)
      }

      val prev = _state.value
      val resolvedFreqs = if (freqs.isNotEmpty()) freqs else prev.bandFrequenciesHz
      val resolvedLevels = resampleLevels(prev.bandLevelsMb, resolvedFreqs.size, minMb, maxMb)

      _state.value = prev.copy(
        bandFrequenciesHz = resolvedFreqs,
        bandLevelsMb = resolvedLevels,
        minLevelMb = minMb,
        maxLevelMb = maxMb,
        audioSessionId = sessionId,
        hardwareSupported = supported
      )
      applyToHardwareLocked()
    }
  }

  fun releaseSession() {
    synchronized(lock) {
      releaseHardwareLocked()
      currentSessionId = 0
      _state.update { it.copy(audioSessionId = 0) }
    }
  }

  fun setEnabled(enabled: Boolean): EqualizerUiState {
    synchronized(lock) {
      _state.update { it.copy(enabled = enabled) }
      applyToHardwareLocked()
      return _state.value
    }
  }

  fun selectPreset(preset: EqPreset): EqualizerUiState {
    synchronized(lock) {
      val current = _state.value
      val targetLevels = resampleLevels(
        source = preset.normalizedBandsMb,
        targetCount = current.bandFrequenciesHz.size,
        minMb = current.minLevelMb,
        maxMb = current.maxLevelMb
      )
      _state.update {
        it.copy(
          enabled = if (preset != EqPreset.FLAT) true else it.enabled,
          presetId = preset.id,
          bandLevelsMb = targetLevels,
          bassBoostStrength = preset.bassBoostStrength.coerceIn(0, 1000)
        )
      }
      applyToHardwareLocked()
      return _state.value
    }
  }

  fun setBandLevel(bandIndex: Int, levelMb: Int): EqualizerUiState {
    synchronized(lock) {
      val current = _state.value
      if (bandIndex !in current.bandLevelsMb.indices) return current
      val clamped = levelMb.coerceIn(current.minLevelMb, current.maxLevelMb)
      val nextBands = current.bandLevelsMb.toMutableList().apply {
        this[bandIndex] = clamped
      }
      _state.update {
        it.copy(
          enabled = true,
          presetId = EqPreset.CUSTOM.id,
          bandLevelsMb = nextBands
        )
      }
      applyToHardwareLocked()
      return _state.value
    }
  }

  fun setBassBoostStrength(strength: Int): EqualizerUiState {
    synchronized(lock) {
      val clamped = strength.coerceIn(0, 1000)
      _state.update {
        it.copy(
          enabled = if (clamped > 0) true else it.enabled,
          presetId = EqPreset.CUSTOM.id,
          bassBoostStrength = clamped
        )
      }
      applyToHardwareLocked()
      return _state.value
    }
  }

  fun reset(): EqualizerUiState {
    synchronized(lock) {
      val current = _state.value
      _state.update {
        it.copy(
          enabled = false,
          presetId = EqPreset.FLAT.id,
          bandLevelsMb = List(current.bandFrequenciesHz.size) { 0 },
          bassBoostStrength = 0
        )
      }
      applyToHardwareLocked()
      return _state.value
    }
  }

  private fun applyToHardwareLocked() {
    val st = _state.value
    runCatching {
      equalizer?.let { eq ->
        val count = eq.numberOfBands.toInt()
        for (i in 0 until count) {
          val level = st.bandLevelsMb.getOrElse(i) { 0 }.coerceIn(st.minLevelMb, st.maxLevelMb)
          eq.setBandLevel(i.toShort(), level.toShort())
        }
        eq.enabled = st.enabled
      }
    }
    runCatching {
      bassBoost?.let { bb ->
        if (bb.strengthSupported) {
          bb.setStrength(st.bassBoostStrength.coerceIn(0, 1000).toShort())
        }
        bb.enabled = st.enabled && st.bassBoostStrength > 0
      }
    }
  }

  private fun releaseHardwareLocked() {
    runCatching { equalizer?.release() }
    equalizer = null
    runCatching { bassBoost?.release() }
    bassBoost = null
  }

  private fun resampleLevels(
    source: List<Int>,
    targetCount: Int,
    minMb: Int,
    maxMb: Int
  ): List<Int> {
    if (targetCount <= 0) return emptyList()
    if (source.isEmpty()) return List(targetCount) { 0 }
    if (source.size == targetCount) {
      return source.map { it.coerceIn(minMb, maxMb) }
    }
    if (targetCount == 1) {
      return listOf(source[source.size / 2].coerceIn(minMb, maxMb))
    }
    return List(targetCount) { idx ->
      val ratio = idx.toFloat() / (targetCount - 1).toFloat()
      val srcPos = ratio * (source.size - 1)
      val low = srcPos.toInt().coerceIn(0, source.lastIndex)
      val high = (low + 1).coerceIn(0, source.lastIndex)
      val frac = srcPos - low
      val interpolated = (source[low] + (source[high] - source[low]) * frac).roundToInt()
      interpolated.coerceIn(minMb, maxMb)
    }
  }
}
