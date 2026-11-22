package com.example

import com.example.data.YoutubeTitleCleaner
import com.example.playback.AudioSpectrumBus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class YoutubeTitleCleanerAndQueueTest {

  @Test
  fun stripNoiseTags_removesOfficialTagsAndPreservesSongTitle() {
    assertEquals(
      "Starboy",
      YoutubeTitleCleaner.stripNoiseTags("Starboy [Official Video]")
    )
    assertEquals(
      "Blinding Lights",
      YoutubeTitleCleaner.stripNoiseTags("Blinding Lights (Official Audio)")
    )
    assertEquals(
      "Midnight City",
      YoutubeTitleCleaner.stripNoiseTags("Midnight City (Official Lyric Video) [4K Remastered]")
    )
    assertEquals(
      "Time To Rise",
      YoutubeTitleCleaner.stripNoiseTags("Time To Rise [MV] | VannDa Official")
    )
    assertEquals(
      "After Hours",
      YoutubeTitleCleaner.stripNoiseTags("After Hours (Visualizer) [Explicit]")
    )
    assertEquals(
      "Save Your Tears",
      YoutubeTitleCleaner.stripNoiseTags("Save Your Tears (Official Video")
    )
  }

  @Test
  fun stripNoiseTags_respectsDisabledToggle() {
    val raw = "Starboy [Official Video] (Official Audio)"
    assertEquals(raw, YoutubeTitleCleaner.stripNoiseTags(raw, enabled = false))
  }

  @Test
  fun cleanChannelAuthor_stripsTopicVevoAndOfficial() {
    assertEquals("The Weeknd", YoutubeTitleCleaner.cleanChannelAuthor("The Weeknd - Topic"))
    assertEquals("VannDa", YoutubeTitleCleaner.cleanChannelAuthor("VannDa Official"))
    assertEquals("Drake", YoutubeTitleCleaner.cleanChannelAuthor("DrakeVEVO"))
  }

  @Test
  fun parseTitleAndArtist_splitsAndCleansCorrectly() {
    val (title, artist) = YoutubeTitleCleaner.parseTitleAndArtist(
      rawTitle = "The Weeknd - Die For You (Official Music Video) | XO",
      rawAuthor = "The Weeknd - Topic",
      cleanEnabled = true
    )
    assertEquals("Die For You", title)
    assertEquals("The Weeknd", artist)
  }

  @Test
  fun audioSpectrumBus_clampsLevelsAndResetsCleanly() {
    AudioSpectrumBus.updateBars(FloatArray(AudioSpectrumBus.BAR_COUNT) { idx ->
      if (idx == 0) -0.5f else if (idx == 1) 1.8f else 0.42f
    })
    val updated = AudioSpectrumBus.bars.value
    assertEquals(AudioSpectrumBus.BAR_COUNT, updated.size)
    assertEquals(0f, updated[0], 0.001f)
    assertEquals(1f, updated[1], 0.001f)
    assertEquals(0.42f, updated[2], 0.001f)

    AudioSpectrumBus.reset()
    assertTrue(AudioSpectrumBus.bars.value.all { it == 0f })
  }
}
