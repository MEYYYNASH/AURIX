package com.example

import com.example.model.Song
import com.example.recommendation.*
import org.junit.Assert.*
import org.junit.Test

class RecommendationEngineTest {

  private fun track(
    id: String,
    title: String = "Track $id",
    artist: String = "Artist $id",
    genre: String = "Pop",
    bpm: Int = 110,
    mood: String = "Chill"
  ) = Song(
    id = id,
    title = title,
    artist = artist,
    artworkUrl = "",
    durationSeconds = 210L,
    streamUrl = "https://example.com/$id.m4a",
    genre = genre,
    bpm = bpm,
    mood = mood
  )

  @Test
  fun playSessionTracker_countsPlayOnlyAfter30Seconds() {
    val tracker = PlaySessionTracker()
    val song = track("s1", genre = "Lo-fi", bpm = 82, mood = "Focus")
    tracker.startNewSession(song, nowMs = 1_000_000L)

    // 29 seconds total: must not qualify yet
    assertNull(tracker.onActivePlaybackDelta("s1", 15_000L))
    assertNull(tracker.onActivePlaybackDelta("s1", 14_000L))
    assertFalse(tracker.isQualified)

    // Crosses 30_000ms threshold on next 1.5s tick
    val trigger = tracker.onActivePlaybackDelta("s1", 1_500L)
    assertTrue(trigger is PlaySessionAction.LogQualifiedPlay)
    val logAction = trigger as PlaySessionAction.LogQualifiedPlay
    assertEquals("s1", logAction.song.id)
    assertEquals(30_500L, logAction.listenedDurationMs)
    assertTrue(tracker.isQualified)

    // Wrong songId does not accumulate
    assertNull(tracker.onActivePlaybackDelta("other_song", 5_000L))

    // Attach persisted row id and track duration updates
    tracker.attachPersistedRowId("s1", 1_000_000L, rowId = 42L)
    val update = tracker.onActivePlaybackDelta("s1", 3_000L)
    assertTrue(update is PlaySessionAction.UpdateListenedDuration)
    assertEquals(33_500L, (update as PlaySessionAction.UpdateListenedDuration).listenedDurationMs)
  }

  @Test
  fun emptyHistory_returnsSafeEmptyStates() {
    val catalog = listOf(track("c1"), track("c2"))
    assertTrue(RecommendationEngine.smartRecommendations(emptyList(), catalog).isEmpty())
    assertNull(RecommendationEngine.generateDiscoverWeekly(emptyList(), catalog))
    assertTrue(RecommendationEngine.recentlyPlayed(emptyList()).isEmpty())
    assertTrue(RecommendationEngine.mostPlayed(emptyList(), MostPlayedFilter.WEEK).isEmpty())
    assertTrue(RecommendationEngine.trendingTracks(emptyList()).isEmpty())
    assertTrue(RecommendationEngine.risingArtists(emptyList()).isEmpty())

    val dna = RecommendationEngine.buildMusicDna(emptyList())
    assertFalse(dna.hasEnoughHistory)
    assertEquals(0, dna.totalQualifiedPlays)
    assertNull(dna.favoriteBpmRange)
  }

  @Test
  fun smartRecommendations_suggestUnheardSongsWithReason() {
    val lofiPlayed = track("p1", "Rain Study", "Chillhop Crew", genre = "Lo-fi", bpm = 80, mood = "Focus")
    val lofiUnheard = track("u1", "Coffee Beats", "Nujabes Vibes", genre = "Lo-fi", bpm = 84, mood = "Focus")
    val rockUnheard = track("u2", "Metal Storm", "Loud Band", genre = "Rock", bpm = 148, mood = "Energetic")

    val logs = listOf(
      PlayLogRecord(1L, lofiPlayed, playedAtMs = 1_000_000L, listenedDurationMs = 45_000L),
      PlayLogRecord(2L, lofiPlayed, playedAtMs = 2_000_000L, listenedDurationMs = 60_000L)
    )
    val recs = RecommendationEngine.smartRecommendations(
      playLogs = logs,
      catalog = listOf(lofiPlayed, lofiUnheard, rockUnheard)
    )

    assertEquals(2, recs.size)
    assertEquals("u1", recs.first().song.id)
    assertTrue(recs.first().reason.contains("Lo-fi"))
    assertTrue(recs.none { it.song.id == "p1" })
  }

  @Test
  fun discoverWeekly_generates30Tracks7030SplitAndPersistsSameWeek() {
    val now = 1_760_000_000_000L
    val played = track("played_1", genre = "Lo-fi", bpm = 82, mood = "Focus", artist = "FavArtist")
    val catalog = (1..40).map { i ->
      if (i <= 25) {
        track("cat_$i", genre = "Lo-fi", bpm = 80 + (i % 10), mood = "Focus", artist = "MatchArtist_$i")
      } else {
        track("cat_$i", genre = "Electronic", bpm = 128, mood = "Energetic", artist = "NewArtist_$i")
      }
    }
    val logs = listOf(PlayLogRecord(1L, played, playedAtMs = now - 5_000L, listenedDurationMs = 50_000L))

    val generated = RecommendationEngine.generateDiscoverWeekly(logs, catalog, nowMs = now)
    assertNotNull(generated)
    generated!!
    assertEquals(30, generated.tracks.size)
    assertEquals(21, generated.matchCount)
    assertEquals(9, generated.discoveryCount)

    // Calling again within the same week keeps the snapshot
    val reused = RecommendationEngine.generateDiscoverWeekly(
      playLogs = logs,
      catalog = catalog,
      existingSnapshot = generated,
      nowMs = now + 60_000L,
      forceRefresh = false
    )
    assertEquals(generated.tracks.map { it.id }, reused?.tracks?.map { it.id })
  }

  @Test
  fun recentlyPlayed_keepsLast20UniqueNewestFirst() {
    val logs = (1..25).map { i ->
      PlayLogRecord(
        id = i.toLong(),
        song = track("s_$i"),
        playedAtMs = i * 1_000L,
        listenedDurationMs = 35_000L
      )
    } + PlayLogRecord(
      id = 99L,
      song = track("s_5"),
      playedAtMs = 99_000L,
      listenedDurationMs = 40_000L
    )

    val recent = RecommendationEngine.recentlyPlayed(logs, limit = 20)
    assertEquals(20, recent.size)
    assertEquals("s_5", recent.first().id)
    assertEquals(recent.distinctBy { it.id }.size, recent.size)
  }

  @Test
  fun mostPlayed_filtersByWeekMonthAndAllTime() {
    val now = 100L * 24L * 60L * 60L * 1000L
    val day = 24L * 60L * 60L * 1000L
    val logs = listOf(
      PlayLogRecord(1L, track("a"), now - 2 * day, 40_000L),
      PlayLogRecord(2L, track("a"), now - 1 * day, 40_000L),
      PlayLogRecord(3L, track("b"), now - 15 * day, 40_000L),
      PlayLogRecord(4L, track("b"), now - 16 * day, 40_000L),
      PlayLogRecord(5L, track("b"), now - 17 * day, 40_000L),
      PlayLogRecord(6L, track("c"), now - 60 * day, 40_000L),
      PlayLogRecord(7L, track("c"), now - 61 * day, 40_000L),
      PlayLogRecord(8L, track("c"), now - 62 * day, 40_000L),
      PlayLogRecord(9L, track("c"), now - 63 * day, 40_000L)
    )

    val week = RecommendationEngine.mostPlayed(logs, MostPlayedFilter.WEEK, nowMs = now)
    assertEquals(listOf("a"), week.map { it.song.id })
    assertEquals(2, week.first().playCount)

    val month = RecommendationEngine.mostPlayed(logs, MostPlayedFilter.MONTH, nowMs = now)
    assertEquals(listOf("b", "a"), month.map { it.song.id })

    val allTime = RecommendationEngine.mostPlayed(logs, MostPlayedFilter.ALL_TIME, nowMs = now)
    assertEquals(listOf("c", "b", "a"), allTime.map { it.song.id })
  }

  @Test
  fun moodPlaylists_andSimilarSongs_matchTagsAndBpmWindow() {
    val chillSong = track("chill", genre = "Indie", bpm = 86, mood = "Chill", artist = "Luna")
    val workoutSong = track("gym", genre = "Phonk", bpm = 142, mood = "Energetic", artist = "Vortex")
    val nightSong = track("night", genre = "R&B", bpm = 92, mood = "Night", artist = "Luna")
    val chillNeighbor = track("chill2", genre = "Indie", bpm = 90, mood = "Chill", artist = "Luna")

    val moods = RecommendationEngine.buildMoodPlaylists(
      listOf(chillSong, workoutSong, nightSong, chillNeighbor)
    )
    val chillList = moods.first { it.mood == MoodCategory.CHILL }.tracks.map { it.id }
    val workoutList = moods.first { it.mood == MoodCategory.WORKOUT }.tracks.map { it.id }

    assertTrue("chill" in chillList)
    assertTrue("gym" in workoutList)

    val similar = RecommendationEngine.similarSongs(
      target = chillSong,
      catalog = listOf(chillSong, chillNeighbor, workoutSong, nightSong)
    )
    assertEquals("chill2", similar.first().song.id)
    assertTrue(similar.first().similarityScore >= 80)
  }

  @Test
  fun musicDna_trending_andRisingArtists_computeProfilesAndGrowth() {
    val now = 200L * 24L * 60L * 60L * 1000L
    val day = 24L * 60L * 60L * 1000L

    val rising = track("r1", artist = "Rising Star", genre = "Lo-fi", bpm = 84, mood = "Focus")
    val steady = track("st1", artist = "Steady Artist", genre = "Pop", bpm = 112, mood = "Chill")

    val logs = listOf(
      // Prior 7d window (7..14d ago)
      PlayLogRecord(1L, steady, now - 8 * day, 40_000L),
      PlayLogRecord(2L, steady, now - 9 * day, 40_000L),
      PlayLogRecord(3L, rising, now - 10 * day, 40_000L),
      // Prior trending window (3..6d ago)
      PlayLogRecord(4L, steady, now - 4 * day, 40_000L),
      // Current 3d & 7d window
      PlayLogRecord(5L, rising, now - 1 * day, 60_000L),
      PlayLogRecord(6L, rising, now - 2 * day, 60_000L),
      PlayLogRecord(7L, rising, now - 2 * day + 3600_000L, 60_000L),
      PlayLogRecord(8L, steady, now - 1 * day, 45_000L)
    )

    val dna = RecommendationEngine.buildMusicDna(logs)
    assertTrue(dna.hasEnoughHistory)
    assertEquals(8, dna.totalQualifiedPlays)
    assertEquals(24, dna.listeningHours.size)
    assertTrue(dna.topGenres.isNotEmpty())
    assertNotNull(dna.favoriteBpmRange)
    assertTrue(dna.discoveryPercentage > 0)

    val trending = RecommendationEngine.trendingTracks(logs, nowMs = now)
    assertEquals("r1", trending.first().song.id)
    assertEquals(RankDirection.New, trending.first().rankDirection)
    assertTrue(trending[1].rankDirection is RankDirection.Down)

    val risingArtists = RecommendationEngine.risingArtists(logs, nowMs = now)
    assertEquals("Rising Star", risingArtists.first().artist)
    assertEquals(200, risingArtists.first().growthPercentage) // 1 -> 3 plays = +200%
  }
}
