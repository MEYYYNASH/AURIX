package com.example.recommendation

import com.example.model.Song
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

data class MonthlyRecapTrackEntry(
  val song: Song,
  val playCount: Int
)

data class MonthlyRecapSummary(
  val monthLabel: String,
  val totalPlays: Int,
  val totalListeningMinutes: Int,
  val uniqueArtistsCount: Int,
  val uniqueSongsCount: Int,
  val longestStreakDays: Int,
  val peakHour: Int,
  val peakVibeBadge: String,
  val topArtistName: String,
  val topArtistPlays: Int,
  val topArtistArtworkUrl: String,
  val topTrack: MonthlyRecapTrackEntry?,
  val topTracks: List<MonthlyRecapTrackEntry>,
  val dominantMood: String,
  val topGenres: List<Pair<String, Int>>,
  val energyPercent: Int,
  val chillPercent: Int,
  val romanticPercent: Int,
  val happyPercent: Int,
  val favoritesCount: Int
) {
  val hasData: Boolean
    get() = totalPlays > 0
}

/**
 * Deterministic monthly listening recap calculator powering AURIX's swipeable
 * Monthly Music DNA Recap Story. Uses the past 30 days of play history (falling
 * back gracefully to all-time logs if the current month is just getting started).
 */
object MonthlyRecapCalculator {
  private const val DAY_MS = 24L * 60L * 60L * 1000L
  private const val MONTH_WINDOW_MS = 30L * DAY_MS

  fun buildRecap(
    playLogs: List<PlayLogRecord>,
    richHistory: List<RichPlayRecord>,
    musicDna: MusicDnaProfile,
    favorites: List<Song>,
    nowMs: Long = System.currentTimeMillis()
  ): MonthlyRecapSummary {
    val monthLabel = SimpleDateFormat("MMMM yyyy", Locale.US).format(Date(nowMs))
    val cutoff = nowMs - MONTH_WINDOW_MS
    val recentLogs = playLogs.filter { it.playedAtMs >= cutoff }
    val activeLogs = if (recentLogs.isNotEmpty()) recentLogs else playLogs
    val recentRich = richHistory.filter { it.playedAtMs >= cutoff }
    val activeRich = if (recentRich.isNotEmpty()) recentRich else richHistory

    val fallbackDominantMood = musicDna.moodDistribution.firstOrNull()?.mood?.takeIf { it.isNotBlank() } ?: "Chill"

    if (activeLogs.isEmpty() && activeRich.isEmpty()) {
      return MonthlyRecapSummary(
        monthLabel = monthLabel,
        totalPlays = 0,
        totalListeningMinutes = 0,
        uniqueArtistsCount = 0,
        uniqueSongsCount = 0,
        longestStreakDays = 0,
        peakHour = 21,
        peakVibeBadge = "Night Owl",
        topArtistName = favorites.firstOrNull()?.artist?.ifBlank { "AURIX" } ?: "AURIX",
        topArtistPlays = 0,
        topArtistArtworkUrl = favorites.firstOrNull()?.artworkUrl.orEmpty(),
        topTrack = favorites.firstOrNull()?.let { MonthlyRecapTrackEntry(it, 0) },
        topTracks = favorites.take(5).map { MonthlyRecapTrackEntry(it, 0) },
        dominantMood = fallbackDominantMood,
        topGenres = emptyList(),
        energyPercent = 64,
        chillPercent = 58,
        romanticPercent = 34,
        happyPercent = 52,
        favoritesCount = favorites.size
      )
    }

    val effectiveLogs = if (activeLogs.isNotEmpty()) {
      activeLogs
    } else {
      activeRich.map { rec ->
        val durMs = (rec.song.durationSeconds.coerceIn(60L, 420L) * 1000L * rec.completedPercent.coerceIn(0.35f, 1f)).toLong()
        PlayLogRecord(
          song = rec.song,
          playedAtMs = rec.playedAtMs,
          listenedDurationMs = durMs
        )
      }
    }

    val enrichedEntries = effectiveLogs.map { rec ->
      TrackMetadataClassifier.enrich(rec.song) to rec.playedAtMs
    }

    val totalPlays = effectiveLogs.size
    val loggedSec = effectiveLogs.sumOf { (it.listenedDurationMs / 1000L).coerceAtLeast(0L) }
    val fallbackSec = effectiveLogs.sumOf { (it.song.durationSeconds.takeIf { d -> d > 0L } ?: 195L).coerceIn(60L, 420L) }
    val totalListeningSec = max(loggedSec, (fallbackSec * 0.65).toLong())
    val totalMinutes = max(1, (totalListeningSec / 60L).toInt())

    // Unique counts
    val uniqueSongs = enrichedEntries.map { it.first.id }.distinct().size
    val uniqueArtists = enrichedEntries
      .map { it.first.artist.trim().lowercase(Locale.ROOT) }
      .filter { it.isNotBlank() && it != "unknown artist" }
      .distinct()
      .size

    // Longest consecutive listening day streak
    val longestStreak = computeLongestDayStreak(effectiveLogs.map { it.playedAtMs })

    // Peak listening hour
    val hourCounts = IntArray(24)
    val cal = Calendar.getInstance()
    effectiveLogs.forEach { rec ->
      cal.timeInMillis = rec.playedAtMs
      hourCounts[cal.get(Calendar.HOUR_OF_DAY).coerceIn(0, 23)]++
    }
    var peakHour = 21
    var maxHourCount = -1
    for (h in 0..23) {
      if (hourCounts[h] > maxHourCount) {
        maxHourCount = hourCounts[h]
        peakHour = h
      }
    }
    val peakVibeBadge = when (peakHour) {
      in 5..11 -> "Morning Glow"
      in 12..16 -> "Afternoon Flow"
      in 17..20 -> "Evening Chill"
      else -> "Night Owl"
    }

    // Top artist
    val artistGroups = enrichedEntries
      .filter { it.first.artist.isNotBlank() && !it.first.artist.equals("Unknown Artist", ignoreCase = true) }
      .groupBy { it.first.artist.trim().lowercase(Locale.ROOT) }
    val topArtistEntry = artistGroups.values.maxByOrNull { it.size }
    val topArtistSong = topArtistEntry?.maxByOrNull { it.second }?.first
    val topArtistName = topArtistSong?.artist ?: "AURIX"
    val topArtistPlays = topArtistEntry?.size ?: 0
    val topArtistArt = topArtistEntry
      ?.firstOrNull { it.first.artworkUrl.isNotBlank() }?.first?.artworkUrl
      .orEmpty()

    // Top 5 tracks
    val topTracks = enrichedEntries
      .groupBy { it.first.id }
      .values
      .map { group ->
        val rep = group.maxByOrNull { it.second }!!.first
        MonthlyRecapTrackEntry(rep, group.size)
      }
      .sortedWith(
        compareByDescending<MonthlyRecapTrackEntry> { it.playCount }
          .thenBy { it.song.title }
      )
      .take(5)

    // Top genres
    val genreCounts = enrichedEntries
      .groupingBy { it.first.genre.ifBlank { "Pop" } }
      .eachCount()
      .entries
      .sortedByDescending { it.value }
      .take(4)
      .map { it.key to it.value }

    // Mood percentages
    val avgEnergy = enrichedEntries.map { it.first.energy }.average().toFloat().coerceIn(0f, 1f)
    val avgChill = enrichedEntries.map { it.first.chill }.average().toFloat().coerceIn(0f, 1f)
    val avgRomantic = enrichedEntries.map { it.first.romantic }.average().toFloat().coerceIn(0f, 1f)
    val avgHappy = enrichedEntries.map { it.first.happy }.average().toFloat().coerceIn(0f, 1f)

    val dominantMood = enrichedEntries
      .groupingBy { it.first.mood.ifBlank { "Chill" } }
      .eachCount()
      .maxByOrNull { it.value }
      ?.key
      ?: fallbackDominantMood

    return MonthlyRecapSummary(
      monthLabel = monthLabel,
      totalPlays = totalPlays,
      totalListeningMinutes = totalMinutes,
      uniqueArtistsCount = uniqueArtists,
      uniqueSongsCount = uniqueSongs,
      longestStreakDays = longestStreak,
      peakHour = peakHour,
      peakVibeBadge = peakVibeBadge,
      topArtistName = topArtistName,
      topArtistPlays = topArtistPlays,
      topArtistArtworkUrl = topArtistArt,
      topTrack = topTracks.firstOrNull(),
      topTracks = topTracks,
      dominantMood = dominantMood,
      topGenres = genreCounts,
      energyPercent = (avgEnergy * 100f).roundToInt().coerceIn(0, 100),
      chillPercent = (avgChill * 100f).roundToInt().coerceIn(0, 100),
      romanticPercent = (avgRomantic * 100f).roundToInt().coerceIn(0, 100),
      happyPercent = (avgHappy * 100f).roundToInt().coerceIn(0, 100),
      favoritesCount = favorites.size
    )
  }

  fun computeLongestDayStreak(timestampsMs: List<Long>): Int {
    if (timestampsMs.isEmpty()) return 0
    val cal = Calendar.getInstance()
    val dayKeys = timestampsMs
      .map { ts ->
        cal.timeInMillis = ts
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        (cal.timeInMillis / DAY_MS).toInt()
      }
      .distinct()
      .sorted()

    var longest = 1
    var current = 1
    for (i in 1 until dayKeys.size) {
      if (dayKeys[i] == dayKeys[i - 1] + 1) {
        current++
        if (current > longest) longest = current
      } else {
        current = 1
      }
    }
    return longest
  }
}
