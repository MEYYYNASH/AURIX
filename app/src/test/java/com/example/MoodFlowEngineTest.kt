package com.example

import com.example.model.Song
import com.example.recommendation.MoodFlowBucket
import com.example.recommendation.MoodFlowEngine
import com.example.recommendation.MoodFlowWeights
import com.example.recommendation.RichPlayRecord
import com.example.recommendation.TrackMetadataClassifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class MoodFlowEngineTest {

  private fun moodSong(
    id: String,
    artist: String = "Artist $id",
    genre: String = "Indie",
    bpm: Int = 90,
    energy: Float = 0.40f,
    happy: Float = 0.20f,
    sad: Float = 0.70f,
    calm: Float = 0.75f,
    energetic: Float = 0.25f,
    romantic: Float = 0.50f
  ): Song = Song(
    id = id,
    title = "Song $id",
    artist = artist,
    artworkUrl = "",
    durationSeconds = 210L,
    streamUrl = "https://example.com/$id.m4a",
    genre = genre,
    bpm = bpm,
    energy = energy,
    sad = sad,
    chill = calm,
    happy = happy,
    calm = calm,
    energetic = energetic,
    romantic = romantic,
    hasMoodMetadata = true
  )

  @Test
  fun `buildCurrentMoodProfile blends current and last 2 tracks with 0_60 0_25 0_15 weights`() {
    val current = moodSong(id = "cur", bpm = 100, energy = 0.50f, happy = 1.0f, sad = 0.0f, calm = 0.0f, energetic = 0.0f, romantic = 0.0f)
    val prev1 = moodSong(id = "p1", bpm = 80, energy = 0.20f, happy = 0.0f, sad = 1.0f, calm = 0.0f, energetic = 0.0f, romantic = 0.0f)
    val prev2 = moodSong(id = "p2", bpm = 140, energy = 0.90f, happy = 0.0f, sad = 0.0f, calm = 1.0f, energetic = 0.0f, romantic = 0.0f)

    val profile = MoodFlowEngine.buildCurrentMoodProfile(
      currentSong = current,
      previousSongs = listOf(prev1, prev2)
    )

    assertEquals(0.60f, profile.mood.happy, 0.001f)
    assertEquals(0.25f, profile.mood.sad, 0.001f)
    assertEquals(0.15f, profile.mood.calm, 0.001f)
    // Energy = 0.60*0.50 + 0.25*0.20 + 0.15*0.90 = 0.30 + 0.05 + 0.135 = 0.485
    assertEquals(0.485f, profile.energy, 0.002f)
    // BPM = 0.60*100 + 0.25*80 + 0.15*140 = 60 + 20 + 21 = 101
    assertEquals(101f, profile.bpm, 0.1f)
  }

  @Test
  fun `base match score uses 70 percent mood 15 percent energy and 15 percent bpm`() {
    val current = moodSong(
      id = "base",
      bpm = 90,
      energy = 0.40f,
      happy = 0.20f,
      sad = 0.80f,
      calm = 0.70f,
      energetic = 0.20f,
      romantic = 0.40f
    )
    val profile = MoodFlowEngine.buildCurrentMoodProfile(current)

    val identicalScore = MoodFlowEngine.computeBaseMatchScore(profile, current)
    val closeCandidate = moodSong(
      id = "close",
      bpm = 92,
      energy = 0.42f,
      happy = 0.22f,
      sad = 0.78f,
      calm = 0.72f,
      energetic = 0.22f,
      romantic = 0.42f
    )
    val distantCandidate = moodSong(
      id = "far",
      bpm = 160,
      energy = 0.95f,
      happy = 0.95f,
      sad = 0.05f,
      calm = 0.05f,
      energetic = 0.95f,
      romantic = 0.05f
    )

    val closeScore = MoodFlowEngine.computeBaseMatchScore(profile, closeCandidate)
    val distantScore = MoodFlowEngine.computeBaseMatchScore(profile, distantCandidate)

    assertEquals(1.0, identicalScore, 0.0001)
    assertTrue("Close candidate ($closeScore) should score higher than distant ($distantScore)", closeScore > distantScore)
    assertTrue(closeScore > 0.95)
    assertTrue(distantScore < 0.40)
  }

  @Test
  fun `computeAdjustedScore penalizes skipped tracks and boosts completed tracks`() {
    val now = 1_700_000_000_000L
    val current = moodSong(id = "cur")
    val profile = MoodFlowEngine.buildCurrentMoodProfile(current)
    val candSkipped = moodSong(id = "cand_skip")
    val candCompleted = moodSong(id = "cand_comp")

    val history = listOf(
      RichPlayRecord(song = candSkipped, playedAtMs = now - 10_000L, completedPercent = 0.1f, completed = false, skipped = true),
      RichPlayRecord(song = candCompleted, playedAtMs = now - 20_000L, completedPercent = 0.98f, completed = true, skipped = false)
    )

    val (skipScore, _) = MoodFlowEngine.computeAdjustedScore(profile, candSkipped, history)
    val (compScore, _) = MoodFlowEngine.computeAdjustedScore(profile, candCompleted, history)

    assertTrue("Completed track ($compScore) should outweigh skipped track ($skipScore)", compScore > skipScore)
  }

  @Test
  fun `selectBucket approximates 60 25 10 5 probability distribution`() {
    val rng = Random(42)
    val counts = mutableMapOf<MoodFlowBucket, Int>()
    val trials = 4_000
    repeat(trials) {
      val bucket = MoodFlowEngine.selectBucket(rng)
      counts[bucket] = (counts[bucket] ?: 0) + 1
    }

    val samePct = (counts[MoodFlowBucket.SAME_MOOD] ?: 0).toDouble() / trials
    val simPct = (counts[MoodFlowBucket.SIMILAR_GENRE_ARTIST] ?: 0).toDouble() / trials
    val favPct = (counts[MoodFlowBucket.FAVORITES] ?: 0).toDouble() / trials
    val discPct = (counts[MoodFlowBucket.DISCOVERY] ?: 0).toDouble() / trials

    assertEquals(0.60, samePct, 0.03)
    assertEquals(0.25, simPct, 0.03)
    assertEquals(0.10, favPct, 0.025)
    assertEquals(0.05, discPct, 0.02)
  }

  @Test
  fun `pickNextSong excludes last 20 played and falls back to random when candidate pool empty`() {
    val now = 1_700_000_000_000L
    val current = moodSong(id = "active", artist = "A1", genre = "Indie")
    val last20Songs = (1..20).map { i -> moodSong(id = "p_$i", artist = "A_$i", genre = "Indie") }
    val history = last20Songs.mapIndexed { idx, song ->
      RichPlayRecord(song = song, playedAtMs = now - (idx * 1000L), completedPercent = 0.9f, completed = true)
    }

    // With fresh tracks outside last 20, last 20 are never picked
    val fresh1 = moodSong(id = "fresh_1", artist = "A1", genre = "Indie", bpm = 92)
    val fresh2 = moodSong(id = "fresh_2", artist = "A2", genre = "Electronic", bpm = 140, energy = 0.9f)
    val poolWithFresh = listOf(current) + last20Songs + listOf(fresh1, fresh2)

    val pickFresh = MoodFlowEngine.pickNextSong(
      currentSong = current,
      allSongs = poolWithFresh,
      playHistory = history,
      favoriteIds = setOf("fresh_1"),
      random = Random(7)
    )
    assertNotNull(pickFresh)
    assertTrue("Expected fresh pick, got ${pickFresh!!.song.id}", pickFresh.song.id in setOf("fresh_1", "fresh_2"))
    assertFalse(last20Songs.any { it.id == pickFresh.song.id })

    // When ONLY last 20 played exist in allSongs, falls back to normal random selection
    val emptyPoolFallback = MoodFlowEngine.pickNextSong(
      currentSong = current,
      allSongs = listOf(current) + last20Songs,
      playHistory = history,
      random = Random(7)
    )
    assertNotNull(emptyPoolFallback)
    assertEquals(MoodFlowBucket.RANDOM_FALLBACK, emptyPoolFallback!!.bucket)
    assertTrue(emptyPoolFallback.song.id != current.id)
  }

  @Test
  fun `TrackMetadataClassifier auto-fills missing mood metadata from genre bpm and keywords`() {
    val missingSadBallad = Song(
      id = "sad_missing",
      title = "Lonely Tears in Rain",
      artist = "Acoustic Singer",
      artworkUrl = "",
      durationSeconds = 210L,
      streamUrl = "https://example.com/sad_missing.m4a",
      genre = "Indie",
      bpm = 78
    )
    val missingWorkoutPhonk = Song(
      id = "phonk_missing",
      title = "Midnight Drift Workout",
      artist = "Phonk Producer",
      artworkUrl = "",
      durationSeconds = 180L,
      streamUrl = "https://example.com/phonk_missing.m4a",
      genre = "Phonk",
      bpm = 142
    )
    val missingInstrumental = Song(
      id = "inst_missing",
      title = "Soft Rain Piano Solo (Instrumental)",
      artist = "Study Beats",
      artworkUrl = "",
      durationSeconds = 200L,
      streamUrl = "https://example.com/inst_missing.m4a",
      genre = "Lo-fi",
      bpm = 80
    )

    val enriched = TrackMetadataClassifier.enrichMoodMetadata(
      listOf(missingSadBallad, missingWorkoutPhonk, missingInstrumental)
    )

    assertTrue(enriched[0].hasMoodMetadata)
    assertTrue(enriched[0].hasVocals)
    assertTrue("Sad ballad should have sad >= 0.70, was ${enriched[0].sad}", enriched[0].sad >= 0.70f)
    assertTrue("Sad indie ballad should have chill >= 0.65, was ${enriched[0].chill}", enriched[0].chill >= 0.65f)

    assertTrue(enriched[1].energetic >= 0.85f)
    assertTrue(enriched[1].energy >= 0.85f)
    assertTrue(enriched[1].sad <= 0.25f)

    assertFalse("Instrumental track should have hasVocals = false", enriched[2].hasVocals)
    assertTrue("Lo-fi instrumental should have high calm", enriched[2].calm >= 0.80f)
  }
}
