package com.example

import com.example.playback.AudioEqualizerController
import com.example.playback.EqPreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AudioEqualizerControllerTest {

  @Before
  fun setUp() {
    AudioEqualizerController.reset()
  }

  @Test
  fun `selecting bass boost preset enables eq and sets preset bands`() {
    val state = AudioEqualizerController.selectPreset(EqPreset.BASS_BOOST)
    assertTrue(state.enabled)
    assertEquals(EqPreset.BASS_BOOST.id, state.presetId)
    assertEquals(EqPreset.BASS_BOOST.normalizedBandsMb, state.bandLevelsMb)
    assertEquals(EqPreset.BASS_BOOST.bassBoostStrength, state.bassBoostStrength)
  }

  @Test
  fun `adjusting individual band switches preset to custom and clamps level`() {
    AudioEqualizerController.selectPreset(EqPreset.FLAT)
    val state = AudioEqualizerController.setBandLevel(0, 9999)
    assertTrue(state.enabled)
    assertEquals(EqPreset.CUSTOM.id, state.presetId)
    assertEquals(state.maxLevelMb, state.bandLevelsMb[0])
  }

  @Test
  fun `selecting lofi and acoustic presets configures eq bands and virtualizer`() {
    val lofi = AudioEqualizerController.selectPreset(EqPreset.LOFI)
    assertTrue(lofi.enabled)
    assertEquals(EqPreset.LOFI.id, lofi.presetId)
    assertEquals(EqPreset.LOFI.normalizedBandsMb, lofi.bandLevelsMb)
    assertEquals(EqPreset.LOFI.virtualizerStrength, lofi.virtualizerStrength)

    val acoustic = AudioEqualizerController.selectPreset(EqPreset.ACOUSTIC)
    assertTrue(acoustic.enabled)
    assertEquals(EqPreset.ACOUSTIC.id, acoustic.presetId)
    assertEquals(EqPreset.ACOUSTIC.normalizedBandsMb, acoustic.bandLevelsMb)
    assertEquals(EqPreset.ACOUSTIC.virtualizerStrength, acoustic.virtualizerStrength)
  }

  @Test
  fun `adjusting virtualizer switches preset to custom and clamps strength`() {
    AudioEqualizerController.selectPreset(EqPreset.FLAT)
    val state = AudioEqualizerController.setVirtualizerStrength(1500)
    assertTrue(state.enabled)
    assertEquals(EqPreset.CUSTOM.id, state.presetId)
    assertEquals(1000, state.virtualizerStrength)
  }

  @Test
  fun `reset restores flat disabled defaults`() {
    AudioEqualizerController.selectPreset(EqPreset.CLUB)
    AudioEqualizerController.setBassBoostStrength(900)
    AudioEqualizerController.setVirtualizerStrength(700)
    val state = AudioEqualizerController.reset()
    assertFalse(state.enabled)
    assertEquals(EqPreset.FLAT.id, state.presetId)
    assertEquals(0, state.bassBoostStrength)
    assertEquals(0, state.virtualizerStrength)
    assertTrue(state.bandLevelsMb.all { it == 0 })
  }
}
