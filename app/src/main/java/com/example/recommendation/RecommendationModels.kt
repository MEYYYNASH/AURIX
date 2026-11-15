package com.example.recommendation

import com.example.model.Song

enum class MostPlayedFilter(val label: String, val windowMs: Long?) {
  WEEK("Week", 7L * 24L * 60L * 60L * 1000L),
  MONTH("Month", 30L * 24L * 60L * 60L * 1000L),
  ALL_TIME("All time", null)
}

enum class MoodCategory(
  val id: String,
  val displayName: String,
  val bpmRange: IntRange,
  val targetMoods: Set<String>,
  val description: String
) {
  CHILL(
    id = "chill",
    displayName = "Chill",
    bpmRange = 70..108,
    targetMoods = setOf("Chill", "Dreamy", "Warm", "Acoustic"),
    description = "Laid-back grooves and mellow rhythms"
  ),
  WORKOUT(
    id = "workout",
    displayName = "Workout",
    bpmRange = 118..175,
    targetMoods = setOf("Energetic", "Hype", "Workout", "Driven"),
    description = "High-energy tracks built for momentum"
  ),
  NIGHT(
    id = "night",
    displayName = "Night",
    bpmRange = 74..118,
    targetMoods = setOf("Night", "Moody", "Atmospheric", "Dreamy"),
    description = "Late-night atmospheric and moody cuts"
  ),
  FOCUS(
    id = "focus",
    displayName = "Focus",
    bpmRange = 64..112,
    targetMoods = setOf("Focus", "Lo-fi", "Calm", "Chill"),
    description = "Steady tempos to keep you locked in"
  )
}

data class PlayLogRecord(
  val id: Long = 0L,
  val song: Song,
  val playedAtMs: Long,
  val listenedDurationMs: Long
)

data class RecommendedTrack(
  val song: Song,
  val reason: String,
  val score: Double
)

sealed interface RankDirection {
  data class Up(val delta: Int) : RankDirection
  data class Down(val delta: Int) : RankDirection
  data object Same : RankDirection
  data object New : RankDirection
}

data class RankedTrack(
  val song: Song,
  val playCount: Int,
  val rank: Int,
  val rankDirection: RankDirection = RankDirection.Same
)

data class MoodPlaylist(
  val mood: MoodCategory,
  val tracks: List<Song>
)

data class SimilarTrack(
  val song: Song,
  val similarityScore: Int,
  val reason: String
)

data class DiscoverWeeklyPlaylist(
  val weekKey: String,
  val generatedAtMs: Long,
  val tracks: List<Song>,
  val matchCount: Int,
  val discoveryCount: Int
)

data class GenreStat(
  val genre: String,
  val playCount: Int,
  val percentage: Int
)

data class MoodStat(
  val mood: String,
  val playCount: Int,
  val percentage: Int
)

data class ArtistStat(
  val artist: String,
  val playCount: Int,
  val artworkUrl: String
)

data class BpmRangeStat(
  val minBpm: Int,
  val maxBpm: Int,
  val averageBpm: Int
) {
  val label: String
    get() = if (minBpm == maxBpm) "$minBpm BPM" else "$minBpm–$maxBpm BPM"
}

data class MusicDnaProfile(
  val hasEnoughHistory: Boolean,
  val totalQualifiedPlays: Int,
  val totalMinutesListened: Long,
  val topGenres: List<GenreStat>,
  val favoriteBpmRange: BpmRangeStat?,
  val favoriteArtists: List<ArtistStat>,
  val listeningHours: List<Int>,
  val peakHourRangeLabel: String,
  val moodDistribution: List<MoodStat>,
  val discoveryPercentage: Int
)

data class RisingArtist(
  val artist: String,
  val recentPlays: Int,
  val previousPlays: Int,
  val growthPercentage: Int,
  val artworkUrl: String
)
