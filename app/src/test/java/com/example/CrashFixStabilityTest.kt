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
  fun release216_metadataAndVersionCodeAreConsistent() {
    assertEquals("2.1.6", BuildConfig.VERSION_NAME)
    assertEquals(20106, BuildConfig.VERSION_CODE)
    assertTrue("Release 2.1.6 versionCode must exceed 2.1.5 (20105)", BuildConfig.VERSION_CODE > 20105)
    assertEquals(420, com.example.ui.screens.SELECT_BAR_OPEN_DURATION_MS)
    assertEquals(600, com.example.ui.screens.SELECT_CIRCLE_OPEN_DURATION_MS)
    assertEquals(380L, com.example.ui.screens.SELECT_CLOSE_CLEAR_DELAY_MS)
    assertEquals(6, com.example.ui.screens.OrbiRunner.entries.size)
    assertEquals(5, com.example.data.OrbiAppIcon.entries.size)
    assertEquals(com.example.data.OrbiAppIcon.MIDNIGHT, com.example.data.OrbiAppIcon.fromId("midnight"))
    assertEquals(com.example.data.OrbiAppIcon.CLASSIC, com.example.data.OrbiAppIcon.fromId("unknown"))
  }

  @Test
  fun customLyricsCodecAndDeveloperKeys_workDeterministicallyOffline() {
    assertEquals("developer_mode_unlocked", com.example.data.PreferencesManager.DEVELOPER_MODE_UNLOCKED.name)
    assertEquals("developer_mode_enabled", com.example.data.PreferencesManager.DEVELOPER_MODE_ENABLED.name)
    assertEquals("custom", com.example.lyrics.LrcCodec.SOURCE_CUSTOM)
    assertEquals("[00:00.00]", com.example.lyrics.LrcCodec.formatTimestamp(0L))
    assertEquals("[01:05.43]", com.example.lyrics.LrcCodec.formatTimestamp(65_430L))
    assertEquals("+0.4 s", com.example.lyrics.LrcCodec.formatOffsetSeconds(400L))
    assertEquals("-1.2 s", com.example.lyrics.LrcCodec.formatOffsetSeconds(-1_200L))
    assertEquals("0.0 s", com.example.lyrics.LrcCodec.formatOffsetSeconds(0L))

    val rows = listOf(
      com.example.lyrics.EditableLyricRow(1L, "First line", 12_340L),
      com.example.lyrics.EditableLyricRow(2L, "សួស្តីពិភពលោក", 25_600L)
    )
    val lrc = com.example.lyrics.LrcCodec.writeLrc(
      rows = rows,
      title = "Time To Rise",
      artist = "VannDa",
      globalOffsetMs = 300L,
      includeOffsetTag = true
    )
    assertTrue(lrc.contains("[ti:Time To Rise]"))
    assertTrue(lrc.contains("[ar:VannDa]"))
    assertTrue(lrc.contains("[offset:300]"))
    assertTrue(lrc.contains("[00:12.34]First line"))
    assertTrue(lrc.contains("[00:25.60]សួស្តីពិភពលោក"))

    val (reparsed, reparsedOffset) = com.example.lyrics.LrcCodec.parseForEditor(lrc)
    assertEquals(2, reparsed.size)
    assertEquals(300L, reparsedOffset)
    assertEquals(12_340L, reparsed[0].timestampMs)
    assertEquals("First line", reparsed[0].text)
    assertEquals(25_600L, reparsed[1].timestampMs)
    assertEquals("សួស្តីពិភពលោក", reparsed[1].text)

    // Merging text edits in Step 1 preserves existing timestamps on unchanged rows
    val merged = com.example.lyrics.LrcCodec.mergeWriteTextWithExistingRows(
      plainText = "First line\nInserted middle line\nសួស្តីពិភពលោក",
      previousRows = rows
    )
    assertEquals(3, merged.size)
    assertEquals(12_340L, merged[0].timestampMs)
    assertEquals(null, merged[1].timestampMs)
    assertEquals(25_600L, merged[2].timestampMs)

    // Standard LrcParser round-trip confirms written custom LRC is valid synced LRC
    val parsedDoc = com.example.lyrics.LrcParser.parse(lrc)
    assertTrue(parsedDoc.isSynced)
    assertEquals(2, parsedDoc.lines.size)

    // CustomLyricsEntity default source is "custom"
    val entity = com.example.data.CustomLyricsEntity(songId = "yt_1", lrcText = lrc)
    assertEquals("custom", entity.source)
  }

  @Test
  fun lyricsAnimation_stylesKeysAndWidgetFallbackAreConsistent() {
    assertEquals("lyrics_anim", com.example.data.PreferencesManager.LYRICS_ANIM.name)
    assertEquals("lyrics_widget_anim", com.example.data.PreferencesManager.LYRICS_WIDGET_ANIM.name)
    assertEquals(5, com.example.lyrics.LyricsAnimationStyle.entries.size)
    assertEquals(com.example.lyrics.LyricsAnimationStyle.SLIDE_UP, com.example.lyrics.LyricsAnimationStyle.fromId(null))
    assertEquals(com.example.lyrics.LyricsAnimationStyle.SLIDE_UP, com.example.lyrics.LyricsAnimationStyle.fromId("unknown"))
    assertEquals(com.example.lyrics.LyricsAnimationStyle.BLUR_FADE, com.example.lyrics.LyricsAnimationStyle.fromId("blur_fade"))
    assertEquals(com.example.lyrics.LyricsAnimationStyle.KARAOKE_FILL, com.example.lyrics.LyricsAnimationStyle.fromId("karaoke_fill"))
    assertEquals(com.example.lyrics.LyricsAnimationStyle.TYPEWRITER, com.example.lyrics.LyricsAnimationStyle.fromId("typewriter"))
    assertEquals(com.example.lyrics.LyricsAnimationStyle.POP_ZOOM, com.example.lyrics.LyricsAnimationStyle.fromId("pop_zoom"))

    // Widget fallback: karaoke_fill and typewriter fall back to slide_up; others remain native
    assertTrue(com.example.lyrics.LyricsAnimationStyle.KARAOKE_FILL.isWidgetFallback)
    assertTrue(com.example.lyrics.LyricsAnimationStyle.TYPEWRITER.isWidgetFallback)
    org.junit.Assert.assertFalse(com.example.lyrics.LyricsAnimationStyle.SLIDE_UP.isWidgetFallback)
    org.junit.Assert.assertFalse(com.example.lyrics.LyricsAnimationStyle.BLUR_FADE.isWidgetFallback)
    org.junit.Assert.assertFalse(com.example.lyrics.LyricsAnimationStyle.POP_ZOOM.isWidgetFallback)
    assertEquals(
      com.example.lyrics.LyricsAnimationStyle.SLIDE_UP,
      com.example.lyrics.LyricsAnimationStyle.KARAOKE_FILL.widgetEffectiveStyle()
    )
    assertEquals(
      com.example.lyrics.LyricsAnimationStyle.SLIDE_UP,
      com.example.lyrics.LyricsAnimationStyle.TYPEWRITER.widgetEffectiveStyle()
    )
    assertEquals(
      com.example.lyrics.LyricsAnimationStyle.BLUR_FADE,
      com.example.lyrics.LyricsAnimationStyle.BLUR_FADE.widgetEffectiveStyle()
    )
    assertEquals(
      com.example.lyrics.LyricsAnimationStyle.POP_ZOOM,
      com.example.lyrics.LyricsAnimationStyle.POP_ZOOM.widgetEffectiveStyle()
    )

    // Widget layout mapping for all 5 styles and Reduced Motion instant fallback
    assertEquals(R.layout.widget_aurix_lyrics, com.example.widget.AurixWidgetController.resolveLyricsWidgetLayoutRes("slide_up", false))
    assertEquals(R.layout.widget_aurix_lyrics_fade, com.example.widget.AurixWidgetController.resolveLyricsWidgetLayoutRes("blur_fade", false))
    assertEquals(R.layout.widget_aurix_lyrics, com.example.widget.AurixWidgetController.resolveLyricsWidgetLayoutRes("karaoke_fill", false))
    assertEquals(R.layout.widget_aurix_lyrics, com.example.widget.AurixWidgetController.resolveLyricsWidgetLayoutRes("typewriter", false))
    assertEquals(R.layout.widget_aurix_lyrics_pop, com.example.widget.AurixWidgetController.resolveLyricsWidgetLayoutRes("pop_zoom", false))
    assertEquals(R.layout.widget_aurix_lyrics_instant, com.example.widget.AurixWidgetController.resolveLyricsWidgetLayoutRes("pop_zoom", true))
    assertEquals(R.layout.widget_aurix_lyrics_instant, com.example.widget.AurixWidgetController.resolveLyricsWidgetLayoutRes("slide_up", true))
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

  @Test
  fun widgetLyricsScheduler_neverSkipsOrLagsBehindSong() {
    val lines = listOf(
      com.example.model.LyricLine(5_000L, "Line zero"),
      com.example.model.LyricLine(10_000L, "Line one"),
      com.example.model.LyricLine(14_000L, "Line two"),
      com.example.model.LyricLine(18_500L, "Line three")
    )
    val leadMs = com.example.widget.AurixWidgetController.WIDGET_LYRIC_LEAD_MS
    var posMs = 5_000L
    var active = com.example.widget.AurixWidgetController.resolveSyncedLyricLines(
      syncedLines = lines,
      plainLyrics = null,
      positionMs = posMs,
      leadMs = leadMs
    )
    assertEquals(0, active.activeIndex)
    assertEquals("Line zero", active.currentLine)

    var slot = 0
    for (expectedIdx in 1 until lines.size) {
      val waitMs = com.example.widget.AurixWidgetController.computeDelayUntilNextLyricLineMsForLines(
        syncedLines = lines,
        positionMs = posMs,
        lyricOffsetMs = 0L,
        playbackSpeed = 1f,
        leadMs = leadMs
      )
      assertTrue("Scheduler must return positive delay for line $expectedIdx", waitMs != null && waitMs > 0L)
      posMs += waitMs!!

      // Wake-up must arrive slightly ahead of vocal timestamp (never late behind the song)
      assertTrue(
        "Wake-up ($posMs) should not lag behind timestamp (${lines[expectedIdx].timestampMs})",
        posMs <= lines[expectedIdx].timestampMs
      )

      // On the very first wake-up, resolver MUST advance to expectedIdx (zero skipped/delayed lines)
      val nextSnap = com.example.widget.AurixWidgetController.resolveSyncedLyricLines(
        syncedLines = lines,
        plainLyrics = null,
        positionMs = posMs,
        leadMs = leadMs
      )
      assertEquals(expectedIdx, nextSnap.activeIndex)
      assertEquals(lines[expectedIdx].text, nextSnap.currentLine)

      // Verify ViewFlipper ping-pong alternation
      val nextSlot = com.example.widget.AurixWidgetController.nextLyricFlipperSlot(
        currentSlot = slot,
        animateTransition = true,
        reducedMotion = false
      )
      assertEquals(1 - slot, nextSlot)
      slot = nextSlot
      active = nextSnap
    }

    // After final line, scheduler returns null (zero polling)
    assertNull(
      com.example.widget.AurixWidgetController.computeDelayUntilNextLyricLineMsForLines(
        syncedLines = lines,
        positionMs = posMs,
        lyricOffsetMs = 0L,
        playbackSpeed = 1f,
        leadMs = leadMs
      )
    )
  }

  @Test
  fun widgetCustomizer_defaultsKeysSizeTiersAndDownsampling_areCorrect() {
    val def = com.example.widget.WidgetCustomizerConfig.DEFAULT
    assertEquals(true, def.samePhoto)
    assertEquals(com.example.widget.WidgetFrameStyle.ROUNDED, def.frameStyle)
    assertEquals("#43C7F4", def.frameColorHex)
    assertEquals(com.example.widget.WidgetBgType.PHOTO, def.bgType)
    assertEquals(30, def.blur)
    assertEquals(100, def.darkness)
    assertEquals(9, com.example.widget.WidgetFrameStyle.entries.size)
    assertEquals(4, com.example.widget.WidgetFrameMotion.entries.size)
    assertEquals(5, com.example.widget.WidgetBgType.entries.size)
    assertEquals(6, com.example.widget.WidgetCustomizerConfig.FRAME_ACCENT_COLORS.size)
    assertEquals(6, com.example.widget.WidgetCustomizerConfig.SOLID_BG_COLORS.size)

    // Exact DataStore preference keys
    assertEquals("same_photo", com.example.data.PreferencesManager.WIDGET_SAME_PHOTO.name)
    assertEquals("frame_source", com.example.data.PreferencesManager.WIDGET_FRAME_SOURCE.name)
    assertEquals("bg_source", com.example.data.PreferencesManager.WIDGET_BG_SOURCE.name)
    assertEquals("frame_style", com.example.data.PreferencesManager.WIDGET_FRAME_STYLE.name)
    assertEquals("frame_color", com.example.data.PreferencesManager.WIDGET_FRAME_COLOR.name)
    assertEquals("corner", com.example.data.PreferencesManager.WIDGET_CORNER.name)
    assertEquals("border", com.example.data.PreferencesManager.WIDGET_BORDER.name)
    assertEquals("motion", com.example.data.PreferencesManager.WIDGET_MOTION.name)
    assertEquals("bg_type", com.example.data.PreferencesManager.WIDGET_BG_TYPE.name)
    assertEquals("bg_color", com.example.data.PreferencesManager.WIDGET_BG_COLOR.name)
    assertEquals("blur", com.example.data.PreferencesManager.WIDGET_BLUR.name)
    assertEquals("darkness", com.example.data.PreferencesManager.WIDGET_DARKNESS.name)

    // Widget size tier classification (small / wide 2:1 / large) and Binder-safe RGB_565 byte sizes (< 500 KB)
    assertEquals(
      com.example.widget.WidgetSizeTier.SMALL,
      com.example.widget.WidgetSizeTier.fromDimensionsDp(minWidthDp = 150, minHeightDp = 110)
    )
    assertEquals(
      com.example.widget.WidgetSizeTier.WIDE,
      com.example.widget.WidgetSizeTier.fromDimensionsDp(minWidthDp = 280, minHeightDp = 185)
    )
    assertEquals(
      com.example.widget.WidgetSizeTier.LARGE,
      com.example.widget.WidgetSizeTier.fromDimensionsDp(minWidthDp = 300, minHeightDp = 220)
    )
    com.example.widget.WidgetSizeTier.entries.forEach { tier ->
      val rgb565Bytes = tier.widthPx * tier.heightPx * 2
      assertTrue("${tier.id} RGB_565 bitmap ($rgb565Bytes B) must stay safely under 500 KB Binder budget", rgb565Bytes < 500_000)
    }

    // OOM-safe downsampling calculation
    assertEquals(1, com.example.widget.WidgetPhotoStorage.computeInSampleSize(800, 600, 1024))
    assertEquals(4, com.example.widget.WidgetPhotoStorage.computeInSampleSize(4096, 3072, 1024))
    assertEquals(8, com.example.widget.WidgetPhotoStorage.computeInSampleSize(8192, 6144, 1024))
    assertEquals(800, com.example.widget.WidgetPhotoStorage.CROP_OUTPUT_WIDTH)
    assertEquals(400, com.example.widget.WidgetPhotoStorage.CROP_OUTPUT_HEIGHT)
    assertEquals(90, com.example.widget.WidgetPhotoStorage.JPEG_QUALITY)
  }

  @Test
  fun orbiCompanionStandbyAndCoverStudio_v210LogicIsDeterministic() {
    // Preference keys
    assertEquals("orbi_companion_theme", com.example.data.PreferencesManager.ORBI_COMPANION_THEME.name)
    assertEquals("standby_vinyl_style", com.example.data.PreferencesManager.STANDBY_VINYL_STYLE.name)
    assertEquals(6, com.example.ui.screens.OrbiCompanionTheme.entries.size)
    assertEquals(com.example.ui.screens.OrbiCompanionTheme.AURORA, com.example.ui.screens.OrbiCompanionTheme.fromId("unknown"))

    // Orbi mood reactivity
    val lofiSong = Song("s1", "Midnight Lo-Fi Study", "Chillhop", "", 180L, "")
    val phonkSong = Song("s2", "Tokyo Drift Phonk", "Kordhell", "", 140L, "")
    val loveSong = Song("s3", "Soft Love Ballad", "SZA", "", 210L, "")

    assertEquals(
      com.example.ui.screens.OrbiMood.LOFI_HEADPHONES,
      com.example.ui.screens.resolveOrbiMood(lofiSong, isPlaying = true, sleepTimerActive = false, hourOfDay = 14)
    )
    assertEquals(
      com.example.ui.screens.OrbiMood.PHONK_SHADES,
      com.example.ui.screens.resolveOrbiMood(phonkSong, isPlaying = true, sleepTimerActive = false, hourOfDay = 15)
    )
    assertEquals(
      com.example.ui.screens.OrbiMood.ROMANTIC_HEART,
      com.example.ui.screens.resolveOrbiMood(loveSong, isPlaying = true, sleepTimerActive = false, hourOfDay = 16)
    )
    assertEquals(
      com.example.ui.screens.OrbiMood.NIGHT_SLEEPY,
      com.example.ui.screens.resolveOrbiMood(lofiSong, isPlaying = true, sleepTimerActive = true, hourOfDay = 14)
    )
    assertEquals(
      com.example.ui.screens.OrbiMood.NIGHT_SLEEPY,
      com.example.ui.screens.resolveOrbiMood(null, isPlaying = false, sleepTimerActive = false, hourOfDay = 23)
    )

    // Milestone badges & theme unlock gating
    val starterBadges = com.example.ui.screens.computeOrbiMilestoneBadges(
      totalPlays = 0,
      favoritesCount = 0,
      uniqueArtistsCount = 0,
      dominantMoodOrGenre = "",
      hourOfDay = 12
    )
    assertEquals(6, starterBadges.size)
    assertTrue(starterBadges.none { it.unlocked })
    assertTrue(com.example.ui.screens.isOrbiThemeUnlocked(com.example.ui.screens.OrbiCompanionTheme.AURORA, starterBadges))
    org.junit.Assert.assertFalse(
      com.example.ui.screens.isOrbiThemeUnlocked(com.example.ui.screens.OrbiCompanionTheme.PRISMATIC, starterBadges)
    )

    val veteranBadges = com.example.ui.screens.computeOrbiMilestoneBadges(
      totalPlays = 35,
      favoritesCount = 8,
      uniqueArtistsCount = 7,
      dominantMoodOrGenre = "Chill",
      hourOfDay = 23
    )
    assertTrue(veteranBadges.all { it.unlocked })
    assertTrue(com.example.ui.screens.isOrbiThemeUnlocked(com.example.ui.screens.OrbiCompanionTheme.PRISMATIC, veteranBadges))

    // Official OrbiAppIcon sync + legacy ID mapping
    assertEquals(com.example.data.OrbiAppIcon.CLASSIC, com.example.ui.screens.OrbiCompanionTheme.CLASSIC.officialIcon)
    assertEquals(com.example.data.OrbiAppIcon.MIDNIGHT, com.example.ui.screens.OrbiCompanionTheme.MIDNIGHT.officialIcon)
    assertEquals(com.example.data.OrbiAppIcon.EMBER, com.example.ui.screens.OrbiCompanionTheme.EMBER.officialIcon)
    assertEquals(com.example.data.OrbiAppIcon.AMOLED, com.example.ui.screens.OrbiCompanionTheme.AMOLED.officialIcon)
    assertEquals(com.example.data.OrbiAppIcon.PEARL, com.example.ui.screens.OrbiCompanionTheme.PEARL.officialIcon)
    assertNull(com.example.ui.screens.OrbiCompanionTheme.PRISMATIC.officialIcon)
    assertEquals(com.example.ui.screens.OrbiCompanionTheme.CLASSIC, com.example.ui.screens.OrbiCompanionTheme.fromId("aurora"))
    assertEquals(com.example.ui.screens.OrbiCompanionTheme.EMBER, com.example.ui.screens.OrbiCompanionTheme.fromId("mint"))
    assertEquals(com.example.ui.screens.OrbiCompanionTheme.AMOLED, com.example.ui.screens.OrbiCompanionTheme.fromId("rose"))
    assertEquals(com.example.ui.screens.OrbiCompanionTheme.PEARL, com.example.ui.screens.OrbiCompanionTheme.fromId("sunset"))

    // Interactive mood sticker cycle & beat energy helper
    val firstCycle = com.example.ui.screens.cycleNextOrbiMoodOverride(null)
    assertEquals(com.example.ui.screens.OrbiMood.LOFI_HEADPHONES, firstCycle)
    val lastCycle = com.example.ui.screens.cycleNextOrbiMoodOverride(com.example.ui.screens.OrbiMood.PARTY_SPARK)
    assertNull(lastCycle)
    assertEquals(0f, com.example.ui.screens.computeOrbiBeatEnergy(floatArrayOf(0.8f, 0.7f, 0.6f), isPlaying = false, reducedMotion = false), 0.001f)
    assertEquals(0f, com.example.ui.screens.computeOrbiBeatEnergy(floatArrayOf(0.8f, 0.7f, 0.6f), isPlaying = true, reducedMotion = true), 0.001f)
    assertTrue(com.example.ui.screens.computeOrbiBeatEnergy(floatArrayOf(0.9f, 0.8f, 0.7f, 0.6f), isPlaying = true, reducedMotion = false) > 0.5f)

    // Cover Blur Studio palettes, layouts & monogram helper
    assertEquals(6, com.example.data.CoverStudioPalette.entries.size)
    assertEquals(3, com.example.data.CoverStudioLayout.entries.size)
    assertEquals(com.example.model.CustomCoverMode.STUDIO, com.example.model.CustomCoverMode.fromId("STUDIO"))
    assertEquals("LN", com.example.data.AlbumCoverStorage.buildMonogramText("Late Night"))
    assertEquals("AU", com.example.data.AlbumCoverStorage.buildMonogramText("Aurix"))
    assertEquals("A", com.example.data.AlbumCoverStorage.buildMonogramText("   "))
  }

  @Test
  fun khmerTypography_fontChoicesScriptRunsAndEqualVisualScalesWorkCorrectly() {
    assertEquals("khmer_lyrics_font", com.example.data.PreferencesManager.KHMER_LYRICS_FONT.name)
    assertEquals(3, com.example.ui.theme.KhmerLyricsFont.entries.size)
    assertEquals(
      com.example.ui.theme.KhmerLyricsFont.KANTUMRUY_PRO,
      com.example.ui.theme.KhmerLyricsFont.fromId(null)
    )
    assertEquals(
      com.example.ui.theme.KhmerLyricsFont.KANTUMRUY_PRO,
      com.example.ui.theme.KhmerLyricsFont.fromId("kantumruy_pro")
    )
    assertEquals(
      com.example.ui.theme.KhmerLyricsFont.KOULEN,
      com.example.ui.theme.KhmerLyricsFont.fromId("koulen")
    )
    assertEquals(
      com.example.ui.theme.KhmerLyricsFont.NOTO_SANS_KHMER,
      com.example.ui.theme.KhmerLyricsFont.fromId("noto_sans_khmer")
    )

    val mixedTitle = "មួយជុំភ្នំពេញ Phnom Penh Nights · Suly Pheng"
    assertTrue(com.example.ui.theme.KhmerTypography.containsKhmer(mixedTitle))
    assertTrue(com.example.ui.theme.KhmerTypography.containsLatin(mixedTitle))
    assertTrue(com.example.ui.theme.KhmerTypography.isMixedKhmerAndLatin(mixedTitle))

    val runs = com.example.ui.theme.KhmerTypography.segmentScriptRuns(mixedTitle)
    assertEquals(2, runs.size)
    assertTrue(runs[0].isKhmer)
    org.junit.Assert.assertFalse(runs[1].isKhmer)
    assertEquals(mixedTitle, runs.joinToString("") { it.text })

    val (titleKhScale, titleEnScale) = com.example.ui.theme.KhmerTypography.resolveEqualVisualScales(
      text = mixedTitle,
      isTitle = true,
      khmerLyricsFont = com.example.ui.theme.KhmerLyricsFont.KANTUMRUY_PRO
    )
    assertTrue("Mixed title Khmer scale should boost Dangrek to match English height", titleKhScale > titleEnScale)

    val (koulenKhScale, koulenEnScale) = com.example.ui.theme.KhmerTypography.resolveEqualVisualScales(
      text = mixedTitle,
      isTitle = false,
      khmerLyricsFont = com.example.ui.theme.KhmerLyricsFont.KOULEN
    )
    assertTrue("Mixed lyrics Khmer scale should equalize visual glyph height", koulenKhScale > koulenEnScale)
  }

  @Test
  fun standbyModeAndUpdateDownloadError_workDeterministically() {
    // 10 Standby clock styles + 5 color moods + Khmer date & 12h/24h clock parts
    assertEquals(10, com.example.ui.screens.StandbyClockStyle.entries.size)
    assertEquals(
      com.example.ui.screens.StandbyClockStyle.MINECRAFT,
      com.example.ui.screens.StandbyClockStyle.fromId("minecraft")
    )
    assertEquals(
      com.example.ui.screens.StandbyClockStyle.ROUND,
      com.example.ui.screens.StandbyClockStyle.fromId("round")
    )
    assertEquals(5, com.example.ui.screens.StandbyColorMood.entries.size)
    assertEquals(
      com.example.ui.screens.StandbyColorMood.AUTO,
      com.example.ui.screens.StandbyColorMood.fromId("auto")
    )
    assertEquals(
      com.example.ui.screens.StandbyColorMood.LOFI,
      com.example.ui.screens.StandbyColorMood.fromId("lofi")
    )
    val khmerDate = com.example.ui.screens.formatKhmerStandbyDate(1763942400000L)
    assertTrue("Khmer date chip should contain Khmer weekday prefix", khmerDate.contains("ថ្ងៃ"))

    // UpdateDownloadErrorKind, 30s cooldown on HTTP 404 & release-pinned checksum URL derivation
    val installed = com.example.data.InstalledAppInfo("com.aurix.music", "2.1.6", 20106L, emptySet())
    val state404 = com.example.data.UpdateUiState(
      stage = com.example.data.UpdateStage.Error,
      installed = installed,
      errorMessage = "Release is still building, wait 2-4 minutes and try again.",
      errorKind = com.example.data.UpdateDownloadErrorKind.Http404ReleaseBuilding,
      retryCooldownSeconds = 30
    )
    assertEquals(com.example.data.UpdateMainAction.Retry, state404.mainAction)
    assertEquals(com.example.data.UpdateDownloadErrorKind.Http404ReleaseBuilding, state404.errorKind)
    assertEquals(30, state404.retryCooldownSeconds)
    assertEquals(20106L, com.example.data.GitHubUpdater.deriveVersionCodeFromTag("v2.1.6"))
    assertEquals(
      "https://github.com/MEYYYNASH/AURIX/releases/download/v2.1.6/version.json",
      com.example.data.UpdateManager.deriveReleaseAssetVersionJsonUrl(
        "https://github.com/MEYYYNASH/AURIX/releases/download/v2.1.6/AURIX-v2.1.6.apk"
      )
    )
    assertNull(
      com.example.data.UpdateManager.deriveReleaseAssetVersionJsonUrl(
        "http://127.0.0.1:8080/aurix.apk"
      )
    )
  }

  @Test
  fun bundledKhmerFontsAndWidgetLayoutWidth_areVerified() {
    // 1. Verify UI FontFamily is KantumruyProFamily
    assertEquals(
      com.example.ui.theme.AurixFontFamilies.KantumruyProFamily,
      com.example.ui.theme.AurixFontFamilies.AurixUiFamily
    )

    // 2. Verify middot / dash punctuation stay grouped with Latin run cleanly
    assertTrue(com.example.ui.theme.KhmerTypography.isLatinChar('·'))
    val artistSepRuns = com.example.ui.theme.KhmerTypography.segmentScriptRuns("បទចម្រៀង · Artist")
    assertEquals(2, artistSepRuns.size)
    assertTrue(artistSepRuns[0].isKhmer)
    org.junit.Assert.assertFalse(artistSepRuns[1].isKhmer)

    // 3. Verify widget text column widths & sizes across Small / Wide / Large and real launcher widths
    assertEquals(147f, com.example.widget.AurixWidgetController.computeLyricsTextWidthDp(com.example.widget.WidgetSizeTier.SMALL, false), 0.01f)
    assertEquals(298.5f, com.example.widget.AurixWidgetController.computeLyricsTextWidthDp(com.example.widget.WidgetSizeTier.WIDE, false), 0.01f)
    assertEquals(204.5f, com.example.widget.AurixWidgetController.computeLyricsTextWidthDp(com.example.widget.WidgetSizeTier.WIDE, true), 0.01f)
    assertEquals(224.5f, com.example.widget.AurixWidgetController.computeLyricsTextWidthDp(com.example.widget.WidgetSizeTier.WIDE, true, widgetWidthDp = 360), 0.01f)
    assertEquals(313.5f, com.example.widget.AurixWidgetController.computeLyricsTextWidthDp(com.example.widget.WidgetSizeTier.LARGE, false), 0.01f)
    assertEquals(197.5f, com.example.widget.AurixWidgetController.computeLyricsTextWidthDp(com.example.widget.WidgetSizeTier.LARGE, true), 0.01f)
    assertEquals(312f, com.example.widget.AurixWidgetController.computeLyricsFooterWidthDp(com.example.widget.WidgetSizeTier.WIDE, true), 0.01f)
    assertEquals(14.5f, com.example.widget.AurixWidgetController.computeLyricsActiveTextSizeDp(com.example.widget.WidgetSizeTier.WIDE), 0.01f)
    assertEquals(11.5f, com.example.widget.AurixWidgetController.computeLyricsNextTextSizeDp(com.example.widget.WidgetSizeTier.WIDE), 0.01f)
    assertTrue(
      com.example.widget.AurixWidgetController.computeLyricsActiveTextSizeDp(com.example.widget.WidgetSizeTier.LARGE) >
        com.example.widget.AurixWidgetController.computeLyricsActiveTextSizeDp(com.example.widget.WidgetSizeTier.SMALL)
    )
    assertTrue(
      com.example.widget.AurixWidgetController.computeLyricsNextTextSizeDp(com.example.widget.WidgetSizeTier.LARGE) >
        com.example.widget.AurixWidgetController.computeLyricsNextTextSizeDp(com.example.widget.WidgetSizeTier.SMALL)
    )

    // 4. Verify all bundled .ttf files exist and Khmer fonts actually carry Khmer Unicode glyphs
    val fontDirCandidates = listOf(
      java.io.File("src/main/res/font"),
      java.io.File("app/src/main/res/font")
    )
    val fontDir = fontDirCandidates.firstOrNull { it.isDirectory }
      ?: throw AssertionError("Could not locate res/font directory from ${java.io.File(".").absolutePath}")

    val khmerFontFiles = listOf(
      "dangrek.ttf",
      "koulen.ttf",
      "kantumruy_pro.ttf",
      "kantumruy_pro_semibold.ttf",
      "kantumruy_pro_bold.ttf",
      "noto_sans_khmer.ttf",
      "noto_sans_khmer_semibold.ttf",
      "noto_sans_khmer_bold.ttf"
    )
    val khmerProbeChars = listOf('ក', 'ខ', 'ិ', '្', 'អ')
    for (name in khmerFontFiles) {
      val f = java.io.File(fontDir, name)
      assertTrue("Font file missing: $name", f.isFile && f.length() > 10_000L)
      val awtFont = java.awt.Font.createFont(java.awt.Font.TRUETYPE_FONT, f)
      for (ch in khmerProbeChars) {
        assertTrue("Khmer glyph U+${Integer.toHexString(ch.code)} missing in $name", awtFont.canDisplay(ch))
      }
    }

    val latinFontFiles = listOf(
      "outfit.ttf",
      "outfit_semibold.ttf",
      "outfit_bold.ttf",
      "inter.ttf",
      "inter_medium.ttf",
      "inter_bold.ttf"
    )
    for (name in latinFontFiles) {
      val f = java.io.File(fontDir, name)
      assertTrue("Font file missing: $name", f.isFile && f.length() > 10_000L)
      val awtFont = java.awt.Font.createFont(java.awt.Font.TRUETYPE_FONT, f)
      assertTrue("Latin 'A' missing in $name", awtFont.canDisplay('A'))
      assertTrue("Latin 'a' missing in $name", awtFont.canDisplay('a'))
    }
  }
}
