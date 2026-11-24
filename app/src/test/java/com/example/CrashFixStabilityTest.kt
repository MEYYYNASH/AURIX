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
  fun release196_metadataAndVersionCodeAreConsistent() {
    assertEquals("1.9.6", BuildConfig.VERSION_NAME)
    assertEquals(10960, BuildConfig.VERSION_CODE)
    assertTrue("Release 1.9.6 versionCode must exceed 1.9.5 (10950)", BuildConfig.VERSION_CODE > 10950)
    assertEquals(420, com.example.ui.screens.SELECT_BAR_OPEN_DURATION_MS)
    assertEquals(600, com.example.ui.screens.SELECT_CIRCLE_OPEN_DURATION_MS)
    assertEquals(380L, com.example.ui.screens.SELECT_CLOSE_CLEAR_DELAY_MS)
    assertEquals(6, com.example.ui.screens.OrbiRunner.entries.size)
    assertEquals(5, com.example.data.OrbiAppIcon.entries.size)
    assertEquals(com.example.data.OrbiAppIcon.MIDNIGHT, com.example.data.OrbiAppIcon.fromId("midnight"))
    assertEquals(com.example.data.OrbiAppIcon.CLASSIC, com.example.data.OrbiAppIcon.fromId("unknown"))
  }

  @Test
  fun libraryGenreClassifier_resolvesAndNormalizesGenresDeterministically() {
    val lofiTrack = Song(
      id = "t_lofi",
      title = "Late Night Lo-Fi Chillhop Study",
      artist = "Aura Beats",
      artworkUrl = "",
      durationSeconds = 140L,
      streamUrl = ""
    )
    val phonkTrack = Song(
      id = "t_phonk",
      title = "Midnight Drift Phonk",
      artist = "Kord",
      artworkUrl = "",
      durationSeconds = 135L,
      streamUrl = ""
    )
    assertEquals("Lo-fi", com.example.ui.screens.resolveLibraryTrackGenre(lofiTrack))
    assertEquals("Phonk", com.example.ui.screens.resolveLibraryTrackGenre(phonkTrack))
    assertEquals("lo_fi", com.example.ui.screens.libraryGenreTagKey("Lo-fi"))
    assertEquals("r_b", com.example.ui.screens.libraryGenreTagKey("R&B"))
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
