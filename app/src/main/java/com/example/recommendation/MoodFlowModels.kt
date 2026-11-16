package com.example.recommendation

import com.example.model.Song

/**
 * 5-dimensional continuous mood representation used by MoodFlow.
 * Every axis is normalized to [0f, 1f].
 */
data class MoodVector(
  val happy: Float,
  val sad: Float,
  val calm: Float,
  val energetic: Float,
  val romantic: Float
) {
  companion object {
    fun fromSong(song: Song): MoodVector = MoodVector(
      happy = song.happy.coerceIn(0f, 1f),
      sad = song.sad.coerceIn(0f, 1f),
      calm = song.calm.coerceIn(0f, 1f),
      energetic = song.energetic.coerceIn(0f, 1f),
      romantic = song.romantic.coerceIn(0f, 1f)
    )
  }
}

/**
 * Active blended target mood profile derived from the current song
 * and up to the 2 preceding played songs (0.6 / 0.25 / 0.15).
 */
data class CurrentMoodProfile(
  val mood: MoodVector,
  val energy: Float,
  val bpm: Float,
  val primaryGenre: String,
  val primaryArtist: String
)

/**
 * Selection bucket chosen by MoodFlow's probability roulette.
 */
enum class MoodFlowBucket(val label: String) {
  SAME_MOOD("Same mood"),
  SIMILAR_GENRE_ARTIST("Similar vibe"),
  FAVORITES("Favorite pick"),
  DISCOVERY("Discovery"),
  RANDOM_FALLBACK("Shuffle")
}

/**
 * Result of picking the next track via MoodFlow.
 */
data class MoodFlowPick(
  val song: Song,
  val matchPercent: Int,
  val score: Double,
  val bucket: MoodFlowBucket,
  val reason: String
)

/**
 * Tunable weights and bucket probabilities for MoodFlow.
 *
 * Tuning guide:
 * - Increase [moodWeight] (default 0.70) for tighter emotional continuity across transitions.
 * - Increase [energyWeight] / [bpmWeight] (defaults 0.15 each) for stricter tempo/energy matching.
 * - Adjust bucket probabilities ([sameMoodProbability], [similarGenreArtistProbability],
 *   [favoritesProbability], [discoveryProbability]) to shift between comfort vs exploration.
 * - Adjust [blendWeights] (default 0.60 / 0.25 / 0.15) to control how strongly previous songs
 *   smooth out abrupt single-track mood shifts.
 */
data class MoodFlowWeights(
  val moodWeight: Double = 0.70,
  val energyWeight: Double = 0.15,
  val bpmWeight: Double = 0.15,
  val sameMoodProbability: Double = 0.60,
  val similarGenreArtistProbability: Double = 0.25,
  val favoritesProbability: Double = 0.10,
  val discoveryProbability: Double = 0.05,
  val blendWeights: Triple<Double, Double, Double> = Triple(0.60, 0.25, 0.15)
)
