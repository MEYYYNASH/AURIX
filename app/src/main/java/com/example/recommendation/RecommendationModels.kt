package com.example.recommendation

import com.example.model.FeaturedAlbumItem
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
  FOCUS(
    id = "focus",
    displayName = "Focus",
    bpmRange = 64..112,
    targetMoods = setOf("Focus", "Lo-fi", "Calm", "Chill"),
    description = "Steady tempos to keep you locked in"
  ),
  SLEEP(
    id = "sleep",
    displayName = "Sleep",
    bpmRange = 54..90,
    targetMoods = setOf("Calm", "Dreamy", "Sleep", "Ambient", "Lo-fi"),
    description = "Soft, slow ambient tracks for winding down"
  ),
  HAPPY(
    id = "happy",
    displayName = "Happy",
    bpmRange = 104..138,
    targetMoods = setOf("Happy", "Upbeat", "Bright", "Feel-good", "Pop"),
    description = "Bright, uplifting hooks and sunny energy"
  ),
  SAD(
    id = "sad",
    displayName = "Sad",
    bpmRange = 60..98,
    targetMoods = setOf("Sad", "Emotional", "Melancholic", "Heartbreak", "Acoustic"),
    description = "Emotional ballads and reflective vocals"
  ),
  ROMANTIC(
    id = "romantic",
    displayName = "Romantic",
    bpmRange = 68..106,
    targetMoods = setOf("Romantic", "Warm", "Love", "R&B", "Smooth"),
    description = "Warm slow jams and intimate melodies"
  ),
  WORKOUT(
    id = "workout",
    displayName = "Workout",
    bpmRange = 118..175,
    targetMoods = setOf("Energetic", "Hype", "Workout", "Driven"),
    description = "High-energy tracks built for momentum"
  ),
  PARTY(
    id = "party",
    displayName = "Party",
    bpmRange = 115..148,
    targetMoods = setOf("Party", "Dance", "Club", "Energetic", "Hype"),
    description = "Club rhythms and upbeat crowd favorites"
  ),
  NIGHT(
    id = "night",
    displayName = "Night Drive",
    bpmRange = 74..118,
    targetMoods = setOf("Night", "Moody", "Atmospheric", "Dreamy"),
    description = "Late-night atmospheric and moody cuts"
  ),
  RAINY_DAY(
    id = "rainy_day",
    displayName = "Rainy Day",
    bpmRange = 62..102,
    targetMoods = setOf("Acoustic", "Calm", "Sad", "Chill", "Indigo"),
    description = "Cozy acoustics and gentle rainy-day textures"
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

data class ArtistMix(
  val id: String,
  val artistName: String,
  val artworkUrl: String,
  val subtitle: String,
  val genreTag: String,
  val similarArtists: List<String>,
  val tracks: List<Song>,
  val isPersonalized: Boolean
)

data class PersonalMix(
  val id: String,
  val mixNumber: Int,
  val title: String,
  val subtitle: String,
  val badgeLabel: String,
  val accentHex: String,
  val artworkUrl: String,
  val tracks: List<Song>,
  val dominantMood: String,
  val isPersonalized: Boolean
)

enum class NewForYouFilter {
  ALL,
  SONGS,
  ALBUMS,
  ARTIST_RELEASES
}

data class NewForYouItem(
  val id: String,
  val title: String,
  val artist: String,
  val artworkUrl: String,
  val releaseDateLabel: String,
  val badgeLabel: String,
  val filterType: NewForYouFilter,
  val isFromFavoriteArtist: Boolean,
  val song: Song? = null,
  val album: FeaturedAlbumItem? = null
)

data class RecentPlayEntry(
  val song: Song,
  val playedAtMs: Long,
  val timeAgoLabel: String,
  val completedPercent: Float
)
