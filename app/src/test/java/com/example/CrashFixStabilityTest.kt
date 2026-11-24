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
  fun release200_metadataAndVersionCodeAreConsistent() {
    assertEquals("2.0.0", BuildConfig.VERSION_NAME)
    assertEquals(20000, BuildConfig.VERSION_CODE)
    assertTrue("Release 2.0.0 versionCode must exceed 1.9.9 (10990)", BuildConfig.VERSION_CODE > 10990)
    assertEquals(420, com.example.ui.screens.SELECT_BAR_OPEN_DURATION_MS)
    assertEquals(600, com.example.ui.screens.SELECT_CIRCLE_OPEN_DURATION_MS)
    assertEquals(380L, com.example.ui.screens.SELECT_CLOSE_CLEAR_DELAY_MS)
    assertEquals(6, com.example.ui.screens.OrbiRunner.entries.size)
    assertEquals(5, com.example.data.OrbiAppIcon.entries.size)
    assertEquals(com.example.data.OrbiAppIcon.MIDNIGHT, com.example.data.OrbiAppIcon.fromId("midnight"))
    assertEquals(com.example.data.OrbiAppIcon.CLASSIC, com.example.data.OrbiAppIcon.fromId("unknown"))
  }

  @Test
  fun libraryRecentSearches_capsAtLastFiveAndDeduplicatesCaseInsensitively() {
    var history = emptyList<String>()
    listOf("VannDa", "Doja Cat", "Kali Uchis", "The Weeknd", "SZA", "Joji").forEach { query ->
      history = com.example.data.PreferencesManager.updateRecentSearchesList(history, query)
    }
    assertEquals(5, history.size)
    assertEquals(listOf("Joji", "SZA", "The Weeknd", "Kali Uchis", "Doja Cat"), history)

    // Re-searching an existing item (case-insensitive) moves it to the top and keeps 5 items
    history = com.example.data.PreferencesManager.updateRecentSearchesList(history, "kali uchis")
    assertEquals(listOf("kali uchis", "Joji", "SZA", "The Weeknd", "Doja Cat"), history)

    val encoded = com.example.data.PreferencesManager.encodeRecentSearches(history)
    val decoded = com.example.data.PreferencesManager.decodeRecentSearches(encoded)
    assertEquals(history, decoded)

    assertEquals(0.40f, com.example.data.PreferencesManager.DEFAULT_DYNAMIC_BG_DIM, 0.0001f)
    assertEquals(0.50f, com.example.data.PreferencesManager.DEFAULT_DYNAMIC_BG_GLASS, 0.0001f)
    assertEquals(60f, com.example.data.PreferencesManager.DEFAULT_DYNAMIC_BG_BLUR, 0.0001f)
    assertTrue(com.example.data.PreferencesManager.isDefaultDynamicBg(0.40f, 0.50f, 60f))
    org.junit.Assert.assertFalse(com.example.data.PreferencesManager.isDefaultDynamicBg(0.55f, 0.50f, 60f))
  }

  @Test
  fun widgetController_resolvesSyncedLyricsResponsiveWidthAndKhmerDetection() {
    val lines = listOf(
      com.example.model.LyricLine(timestampMs = 5_000L, text = "First line"),
      com.example.model.LyricLine(timestampMs = 12_000L, text = "សួស្តីពិភពលោក"),
      com.example.model.LyricLine(timestampMs = 20_000L, text = "Final line")
    )

    val atMid = com.example.widget.AurixWidgetController.resolveSyncedLyricLines(
      syncedLines = lines,
      plainLyrics = null,
      positionMs = 13_500L,
      lyricOffsetMs = 0L,
      noLyricsLabel = "No lyrics for this song"
    )
    assertTrue(atMid.hasLyrics)
    assertEquals(1, atMid.activeIndex)
    assertEquals("សួស្តីពិភពលោក", atMid.currentLine)
    assertEquals("Final line", atMid.nextLine)
    assertTrue(com.example.widget.AurixWidgetController.containsKhmerScript(atMid.currentLine))
    org.junit.Assert.assertFalse(com.example.widget.AurixWidgetController.containsKhmerScript(atMid.nextLine))

    val emptySnap = com.example.widget.AurixWidgetController.resolveSyncedLyricLines(
      syncedLines = emptyList(),
      plainLyrics = "",
      positionMs = 10_000L,
      lyricOffsetMs = 0L,
      noLyricsLabel = "No lyrics for this song"
    )
    org.junit.Assert.assertFalse(emptySnap.hasLyrics)
    assertEquals("No lyrics for this song", emptySnap.currentLine)

    // Responsive width: hide Like below 3 cells (< 180dp)
    org.junit.Assert.assertFalse(com.example.widget.AurixWidgetController.shouldShowLikeButton(150))
    org.junit.Assert.assertFalse(com.example.widget.AurixWidgetController.shouldShowLikeButton(179))
    assertTrue(com.example.widget.AurixWidgetController.shouldShowLikeButton(180))
    assertTrue(com.example.widget.AurixWidgetController.shouldShowLikeButton(250))

    // Smooth ViewFlipper ping-pong slot alternation when animating, and static slot on Reduced Motion
    assertEquals(1, com.example.widget.AurixWidgetController.nextLyricFlipperSlot(0, animateTransition = true, reducedMotion = false))
    assertEquals(0, com.example.widget.AurixWidgetController.nextLyricFlipperSlot(1, animateTransition = true, reducedMotion = false))
    assertEquals(0, com.example.widget.AurixWidgetController.nextLyricFlipperSlot(0, animateTransition = true, reducedMotion = true))
    assertEquals(1, com.example.widget.AurixWidgetController.nextLyricFlipperSlot(1, animateTransition = true, reducedMotion = true))
    assertEquals(0, com.example.widget.AurixWidgetController.nextLyricFlipperSlot(0, animateTransition = false, reducedMotion = false))
    assertEquals(1, com.example.widget.AurixWidgetController.nextLyricFlipperSlot(1, animateTransition = false, reducedMotion = false))
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
