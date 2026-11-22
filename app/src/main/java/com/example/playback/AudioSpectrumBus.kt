package com.example.playback

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object AudioSpectrumBus {
  const val BAR_COUNT = 20

  private val _bars = MutableStateFlow(FloatArray(BAR_COUNT))
  val bars: StateFlow<FloatArray> = _bars.asStateFlow()

  fun updateBars(levels: FloatArray) {
    val copy = FloatArray(BAR_COUNT) { idx ->
      levels.getOrElse(idx) { 0f }.coerceIn(0f, 1f)
    }
    _bars.value = copy
  }

  fun reset() {
    _bars.value = FloatArray(BAR_COUNT)
  }
}
