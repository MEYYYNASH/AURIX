package com.example

import com.example.model.Song
import com.example.recommendation.BecauseYouListenedEngine
import com.example.recommendation.RichPlayRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BecauseYouListenedEngineTest {

  private fun vocalSong(
    id: String,
    title: String = "Track $id",
    artist: String = "Artist $id",
    genre: String = "Indie",
    bpm: Int = 88,
    energy: Float = 0.35f,
    sad: Float = 0.75f,
    chill: Float = 0.70f,
    hasVocals: Boolean = true,
    language: String = "en"
  ): Song = Song(
    id = id,
    title = title,
    artist = artist,
    artworkUrl = "",
    durationSeconds = 210L,
    streamUrl = "https://example.com/$id.m4a",
    genre = genre,
    bpm = bpm,
    energy = energy,
    sad = sad,
    chill = chill,
    calm = chill,
    happy = (1f - sad).coerceIn(0f, 1f),
    energetic = energy,
    romantic = 0.45f,
    hasVocals = hasVocals,
    language = language,
    hasMoodMetadata = true
  )

  @Test
  fun `filter excludes instrumental songs upbeat songs disliked songs and last 20 played`() {
    val now = 1_700_000_000_000L
    val last20 = (1..20).map { i ->
      val s = vocalSong(id = "played_$i", artist = "HistoryArtist_$i", sad = 0.8f, chill = 0.8f)
      RichPlayRecord(
        song = s,
        playedAtMs = now - (i * 60_000L),
        completedPercent = 0.95f,
        completed = true,
        skipped = false,
        replayed = false
      )
    }

    val eligibleSad = vocalSong(id = "eligible_sad", title = "Rainy Letters", artist = "Luna", sad = 0.85f, chill = 0.40f, hasVocals = true)
    val eligibleChill = vocalSong(id = "eligible_chill", title = "Warm Tea", artist = "Sol", sad = 0.20f, chill = 0.88f, hasVocals = true)
    val instrumentalTrack = vocalSong(id = "inst_1", title = "Piano Solo", artist = "Keys", sad = 0.90f, chill = 0.90f, hasVocals = false)
    val upbeatNonSadNonChill = vocalSong(id = "upbeat_1", title = "Club Anthem", artist = "DJ", sad = 0.10f, chill = 0.20f, energy = 0.92f, bpm = 132, hasVocals = true)
    val dislikedTrack = vocalSong(id = "disliked_1", title = "Skipped Sad", artist = "Nox", sad = 0.90f, chill = 0.90f, hasVocals = true)

    val catalog = last20.map { it.song } + listOf(
      eligibleSad,
      eligibleChill,
      instrumentalTrack,
      upbeatNonSadNonChill,
      dislikedTrack
    )

    val result = BecauseYouListenedEngine.recommend(
      catalog = catalog,
      playHistory = last20,
      favorites = emptyList(),
      feedbackBySongId = mapOf("disliked_1" to false),
      nowMs = now
    )

    val allRecommendedIds = (result.sadVibes + result.chillMix).map { it.song.id }.toSet()

    assertTrue("Eligible sad vocal track should be recommended", "eligible_sad" in allRecommendedIds)
    assertTrue("Eligible chill vocal track should be recommended", "eligible_chill" in allRecommendedIds)
    assertFalse("Instrumental track must be excluded", "inst_1" in allRecommendedIds)
    assertFalse("Low-sad and low-chill track must be excluded", "upbeat_1" in allRecommendedIds)
    assertFalse("Disliked track must be excluded", "disliked_1" in allRecommendedIds)
    (1..20).forEach { i ->
      assertFalse("Last 20 played track played_$i must be excluded", "played_$i" in allRecommendedIds)
    }
  }

  @Test
  fun `taste profile ignores plays older than 30 days and penalizes skipped tracks`() {
    val now = 1_700_000_000_000L
    val oldPlay = RichPlayRecord(
      song = vocalSong(id = "old", genre = "Phonk", artist = "OldArtist", bpm = 148, energy = 0.95f, sad = 0.1f, chill = 0.1f),
      playedAtMs = now - (BecauseYouListenedEngine.TASTE_WINDOW_MS + 10_000L),
      completedPercent = 1.0f,
      completed = true,
      replayed = true
    )
    val completedIndie = (1..6).map { i ->
      RichPlayRecord(
        song = vocalSong(id = "indie_$i", genre = "Indie", artist = "Phoebe", bpm = 82, energy = 0.30f, sad = 0.85f, chill = 0.78f),
        playedAtMs = now - (i * 3_600_000L),
        completedPercent = 0.98f,
        completed = true,
        replayed = true
      )
    }
    val skippedRock = (1..5).map { i ->
      RichPlayRecord(
        song = vocalSong(id = "rock_$i", genre = "Rock", artist = "LoudBand", bpm = 140, energy = 0.90f, sad = 0.25f, chill = 0.20f),
        playedAtMs = now - (i * 7_200_000L),
        completedPercent = 0.05f,
        completed = false,
        skipped = true
      )
    }

    val profile = BecauseYouListenedEngine.buildUserTasteProfile(
      playHistory = listOf(oldPlay) + completedIndie + skippedRock,
      favorites = emptyList(),
      nowMs = now
    )

    assertFalse(profile.isColdStart)
    assertEquals(11, profile.totalRecentPlays)
    assertTrue("Indie affinity should dominate over skipped Rock", (profile.topGenres["indie"] ?: 0.0) > (profile.topGenres["rock"] ?: 0.0))
    assertFalse("Older than 30 days Phonk play should be outside window", profile.topGenres.containsKey("phonk"))
    assertTrue("Average sad mood should reflect completed Indie plays", profile.avgSad >= 0.75f)
    assertTrue("Average BPM should stay near 82 BPM", profile.avgBpm in 75f..95f)
  }

  @Test
  fun `scoring prefers mood genre artist and energy bpm alignment and provides reason`() {
    val now = 1_700_000_000_000L
    val history = (1..12).map { i ->
      RichPlayRecord(
        song = vocalSong(
          id = "hist_$i",
          title = "Midnight Rain $i",
          artist = "Luna",
          genre = "R&B",
          bpm = 84,
          energy = 0.34f,
          sad = 0.84f,
          chill = 0.80f
        ),
        playedAtMs = now - (i * 86_400_000L / 2),
        completedPercent = 0.95f,
        completed = true,
        replayed = true
      )
    }

    val strongMatch = vocalSong(
      id = "match_strong",
      title = "Empty Coffee Shop",
      artist = "Luna",
      genre = "R&B",
      bpm = 85,
      energy = 0.35f,
      sad = 0.86f,
      chill = 0.82f
    )
    val weakMatch = vocalSong(
      id = "match_weak",
      title = "Fast Synth Sad",
      artist = "Unknown DJ",
      genre = "Electronic",
      bpm = 138,
      energy = 0.88f,
      sad = 0.51f,
      chill = 0.25f
    )

    val result = BecauseYouListenedEngine.recommend(
      catalog = history.map { it.song } + listOf(weakMatch, strongMatch),
      playHistory = history,
      favorites = emptyList(),
      nowMs = now
    )

    assertEquals(2, result.sadVibes.size)
    assertEquals("match_strong", result.sadVibes.first().song.id)
    assertTrue(result.sadVibes.first().matchPercent > result.sadVibes.last().matchPercent)
    assertTrue(
      "Expected 'Because you listened to ...' reason, got: ${result.sadVibes.first().reason}",
      result.sadVibes.first().reason.startsWith("Because you listened to ")
    )
  }

  @Test
  fun `diversity caps consecutive tracks per artist at 2 and caps list sizes at 20`() {
    val now = 1_700_000_000_000L
    val sameArtistTracks = (1..15).map { i ->
      vocalSong(
        id = "same_$i",
        title = "Same $i",
        artist = "Dominant Artist",
        genre = "R&B",
        sad = 0.90f,
        chill = 0.88f
      )
    }
    val otherArtistTracks = (1..15).map { i ->
      vocalSong(
        id = "other_$i",
        title = "Other $i",
        artist = "Artist_${i % 6}",
        genre = "Pop",
        sad = 0.65f,
        chill = 0.65f
      )
    }

    val result = BecauseYouListenedEngine.recommend(
      catalog = sameArtistTracks + otherArtistTracks,
      playHistory = emptyList(),
      favorites = listOf(sameArtistTracks.first()),
      nowMs = now
    )

    assertTrue(result.isColdStart)
    assertEquals(20, result.sadVibes.size)
    assertEquals(20, result.chillMix.size)

    fun assertMaxTwoConsecutive(list: List<String>) {
      for (i in 2 until list.size) {
        val sameThree = list[i].equals(list[i - 1], ignoreCase = true) &&
          list[i - 1].equals(list[i - 2], ignoreCase = true)
        assertFalse("Found 3 consecutive songs by ${list[i]} at index $i", sameThree)
      }
    }
    assertMaxTwoConsecutive(result.sadVibes.map { it.song.artist })
    assertMaxTwoConsecutive(result.chillMix.map { it.song.artist })
  }

  @Test
  fun `cold start uses favorites and popularity scores when history under 10 plays`() {
    val favSeed = vocalSong(id = "fav_seed", title = "Ocean Tears", artist = "Sora", genre = "Indie", sad = 0.88f, chill = 0.84f)
    val popularPick = vocalSong(id = "pop_sad", title = "Viral Sad Ballad", artist = "Mina", genre = "Pop", sad = 0.82f, chill = 0.60f)
    val unpopularPick = vocalSong(id = "unpop_sad", title = "Obscure Track", artist = "Zed", genre = "Rock", sad = 0.52f, chill = 0.30f)

    val result = BecauseYouListenedEngine.recommend(
      catalog = listOf(unpopularPick, popularPick),
      playHistory = emptyList(),
      favorites = listOf(favSeed),
      popularityBySongId = mapOf("pop_sad" to 100, "unpop_sad" to 1)
    )

    assertTrue(result.isColdStart)
    assertEquals("pop_sad", result.sadVibes.first().song.id)
    assertTrue(result.sadVibes.first().reason.contains("favorited", ignoreCase = true))
  }
}
