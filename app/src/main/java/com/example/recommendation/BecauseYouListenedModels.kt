package com.example.recommendation

import com.example.model.Song

/**
 * Rich playback interaction record combining song audio/mood attributes with
 * completion, skip, and replay telemetry for recommendation & MoodFlow engines.
 */
data class RichPlayRecord(
  val song: Song,
  val playedAtMs: Long,
  val completedPercent: Float = 0f,
  val completed: Boolean = completedPercent >= 0.80f,
  val skipped: Boolean = false,
  val replayed: Boolean = false
)

/**
 * User taste profile aggregated over the last 30 days of listening history,
 * favorites, and explicit thumbs-up / thumbs-down feedback.
 */
data class UserTasteProfile(
  val avgSad: Float,
  val avgChill: Float,
  val avgEnergy: Float,
  val avgBpm: Float,
  val topGenres: Map<String, Double>,
  val topArtists: Map<String, Double>,
  val topLanguages: Map<String, Double>,
  val totalRecentPlays: Int,
  val isColdStart: Boolean
)

/**
 * Single track recommended by the "Because You Listened" feature.
 */
data class VocalRecommendationItem(
  val song: Song,
  val matchPercent: Int,
  val score: Double,
  val reason: String,
  val feedback: Boolean? = null
)

/**
 * Complete output of the "Because You Listened" engine containing both
 * "Sad Vibes for You" and "Chill Mix for You" (up to 20 tracks each).
 */
data class BecauseYouListenedResult(
  val sadVibes: List<VocalRecommendationItem> = emptyList(),
  val chillMix: List<VocalRecommendationItem> = emptyList(),
  val tasteProfile: UserTasteProfile = UserTasteProfile(
    avgSad = 0.65f,
    avgChill = 0.65f,
    avgEnergy = 0.40f,
    avgBpm = 92f,
    topGenres = emptyMap(),
    topArtists = emptyMap(),
    topLanguages = emptyMap(),
    totalRecentPlays = 0,
    isColdStart = true
  ),
  val isColdStart: Boolean = true
)
