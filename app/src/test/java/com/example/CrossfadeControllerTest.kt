package com.example

import com.example.playback.CrossfadeController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CrossfadeControllerTest {

  @Before
  fun setUp() {
    CrossfadeController.restoreSettings(durationSeconds = 0, fadeOnManualSkip = true)
  }

  @Test
  fun restoreSettingsClampsDurationAndUpdatesSkipPreference() {
    CrossfadeController.restoreSettings(durationSeconds = -2, fadeOnManualSkip = false)
    assertEquals(0, CrossfadeController.state.value.durationSeconds)
    assertFalse(CrossfadeController.state.value.fadeOnManualSkip)

    CrossfadeController.restoreSettings(durationSeconds = 99, fadeOnManualSkip = true)
    assertEquals(CrossfadeController.MAX_SECONDS, CrossfadeController.state.value.durationSeconds)
    assertTrue(CrossfadeController.state.value.fadeOnManualSkip)
  }

  @Test
  fun setDurationSecondsClampsAndUpdatesState() {
    val updated = CrossfadeController.setDurationSeconds(6)
    assertEquals(6, updated.durationSeconds)
    assertEquals(6, CrossfadeController.state.value.durationSeconds)

    val capped = CrossfadeController.setDurationSeconds(25)
    assertEquals(12, capped.durationSeconds)
  }

  @Test
  fun setFadeOnManualSkipUpdatesState() {
    val disabled = CrossfadeController.setFadeOnManualSkip(false)
    assertFalse(disabled.fadeOnManualSkip)
    assertFalse(CrossfadeController.state.value.fadeOnManualSkip)

    val enabled = CrossfadeController.setFadeOnManualSkip(true)
    assertTrue(enabled.fadeOnManualSkip)
  }

  @Test
  fun equalPowerGainsSatisfyEndpointsAndSymmetry() {
    val (outStart, inStart) = CrossfadeController.equalPowerGains(0f)
    assertEquals(1f, outStart, 0.001f)
    assertEquals(0f, inStart, 0.001f)

    val (outMid, inMid) = CrossfadeController.equalPowerGains(0.5f)
    assertEquals(0.7071f, outMid, 0.002f)
    assertEquals(0.7071f, inMid, 0.002f)
    assertEquals(1f, outMid * outMid + inMid * inMid, 0.002f)

    val (outEnd, inEnd) = CrossfadeController.equalPowerGains(1f)
    assertEquals(0f, outEnd, 0.001f)
    assertEquals(1f, inEnd, 0.001f)
  }
}
