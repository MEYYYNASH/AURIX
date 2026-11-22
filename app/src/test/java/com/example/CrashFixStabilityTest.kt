package com.example

import com.example.data.YoutubeExtractor
import com.example.model.Song
import com.example.recommendation.TrackMetadataClassifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CrashFixStabilityTest {

  @Test
  fun release183_metadataAndVersionCodeAreConsistent() {
    assertEquals("1.8.3", BuildConfig.VERSION_NAME)
    assertEquals(10830, BuildConfig.VERSION_CODE)
    assertTrue("Release 1.8.3 versionCode must exceed 1.8.2 (10820)", BuildConfig.VERSION_CODE > 10820)
  }

  @Test
  fun trackMetadataClassifier_handlesIntMinValueHashCodeSafely() {
    val intMinId = "polygenelubricants"
    assertEquals(Int.MIN_VALUE, intMinId.hashCode())

    val rawSong = Song(
      id = intMinId,
      title = "Late Night Test Track",
      artist = "Test Artist",
      artworkUrl = "",
      durationSeconds = 180L,
      streamUrl = ""
    )

    val enriched = TrackMetadataClassifier.enrich(rawSong)
    assertTrue("BPM should be positive even when id hashCode is Int.MIN_VALUE", enriched.bpm > 0)
    assertTrue("Energy should be finite in 0..1", enriched.energy in 0f..1f)
    assertTrue("Sad score should be finite in 0..1", enriched.sad in 0f..1f)
    assertTrue("Chill score should be finite in 0..1", enriched.chill in 0f..1f)
    assertTrue("Happy score should be finite in 0..1", enriched.happy in 0f..1f)
    assertTrue("Calm score should be finite in 0..1", enriched.calm in 0f..1f)
    assertTrue("Energetic score should be finite in 0..1", enriched.energetic in 0f..1f)
    assertTrue("Romantic score should be finite in 0..1", enriched.romantic in 0f..1f)
  }

  @Test
  fun youtubeExtractor_handlesMalformedInputsWithoutThrowing() {
    assertNull(YoutubeExtractor.extractVideoId(""))
    assertNull(YoutubeExtractor.extractVideoId("   "))
    assertNull(YoutubeExtractor.extractVideoId("ht tp://[::bad uri"))
    assertEquals(
      "dQw4w9WgXcQ",
      YoutubeExtractor.extractVideoId("https://youtu.be/dQw4w9WgXcQ?si=test")
    )
    assertNull(
      YoutubeExtractor.probeWorkingDownloadMode(
        url = "not a valid http url",
        userAgent = "AURIX-Test"
      )
    )
    assertNull(
      YoutubeExtractor.probeWorkingDownloadMode(
        url = "",
        userAgent = ""
      )
    )
  }
}
