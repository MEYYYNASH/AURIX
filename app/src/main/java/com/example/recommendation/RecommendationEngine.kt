package com.example.recommendation

import com.example.model.Song
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Pure recommendation and listening-analytics engine kept decoupled from UI & Android framework.
 * All recommendations, charts, mood playlists, and Music DNA metrics derive from qualified 30s+ play logs.
 */
object RecommendationEngine {

  const val MIN_QUALIFIED_PLAY_MS = 30_000L
  private const val DAY_MS = 24L * 60L * 60L * 1000L
  private const val WEEK_MS = 7L * DAY_MS

  fun isQualifiedPlay(listenedDurationMs: Long): Boolean {
    return listenedDurationMs >= MIN_QUALIFIED_PLAY_MS
  }

  fun currentWeekKey(nowMs: Long = System.currentTimeMillis()): String {
    val cal = Calendar.getInstance(Locale.US).apply { timeInMillis = nowMs }
    val week = cal.get(Calendar.WEEK_OF_YEAR)
    val year = cal.get(Calendar.YEAR)
    return String.format(Locale.US, "%04d-W%02d", year, week)
  }

  /**
   * 1. Smart Recommendations:
   * Suggests unheard tracks matching the user's genre, artist, BPM, and mood affinity,
   * paired with a concise human-readable reason (e.g. "Because you listen to Lo-fi").
   */
  fun smartRecommendations(
    playLogs: List<PlayLogRecord>,
    catalog: List<Song>,
    limit: Int = 12
  ): List<RecommendedTrack> {
    if (playLogs.isEmpty() || catalog.isEmpty()) return emptyList()

    val enrichedLogs = playLogs.map { it.copy(song = TrackMetadataClassifier.enrich(it.song)) }
    val playedIds = enrichedLogs.map { it.song.id }.toSet()
    val candidates = catalog
      .asSequence()
      .map { TrackMetadataClassifier.enrich(it) }
      .filter { it.id !in playedIds }
      .distinctBy { it.id }
      .toList()

    if (candidates.isEmpty()) return emptyList()

    val genreCounts = enrichedLogs.groupingBy { it.song.genre }.eachCount()
    val artistCounts = enrichedLogs.groupingBy { normalizeArtist(it.song.artist) }.eachCount()
    val moodCounts = enrichedLogs.groupingBy { it.song.mood }.eachCount()
    val avgBpm = enrichedLogs.map { it.song.bpm }.filter { it > 0 }.average().takeIf { !it.isNaN() } ?: 105.0

    val topGenre = genreCounts.maxByOrNull { it.value }?.key
    val topMood = moodCounts.maxByOrNull { it.value }?.key
    val maxGenreCount = (genreCounts.values.maxOrNull() ?: 1).coerceAtLeast(1)
    val maxArtistCount = (artistCounts.values.maxOrNull() ?: 1).coerceAtLeast(1)
    val maxMoodCount = (moodCounts.values.maxOrNull() ?: 1).coerceAtLeast(1)

    return candidates.map { candidate ->
      val genreAffinity = (genreCounts[candidate.genre] ?: 0).toDouble() / maxGenreCount
      val artistAffinity = (artistCounts[normalizeArtist(candidate.artist)] ?: 0).toDouble() / maxArtistCount
      val moodAffinity = (moodCounts[candidate.mood] ?: 0).toDouble() / maxMoodCount
      val bpmDiff = abs(candidate.bpm - avgBpm)
      val bpmAffinity = (1.0 - (bpmDiff / 45.0)).coerceIn(0.0, 1.0)

      val totalScore = (genreAffinity * 38.0) +
        (artistAffinity * 30.0) +
        (moodAffinity * 20.0) +
        (bpmAffinity * 12.0)

      val reason = when {
        artistAffinity >= 0.45 -> "Because you play ${candidate.artist}"
        genreAffinity >= 0.35 -> "Because you listen to ${candidate.genre}"
        moodAffinity >= 0.35 -> "Matches your ${candidate.mood} vibe"
        bpmDiff <= 10.0 -> "Fits your ${avgBpm.roundToInt()} BPM pace"
        topGenre != null -> "Similar energy to $topGenre"
        topMood != null -> "Fits your $topMood mood"
        else -> "Recommended for your taste"
      }

      RecommendedTrack(
        song = candidate,
        reason = reason,
        score = totalScore
      )
    }
      .sortedByDescending { it.score }
      .take(limit)
  }

  /**
   * 2. Discover Weekly:
   * Auto-generates a 30-track playlist (~70% matching taste, ~30% fresh discovery).
   * Respects an existing snapshot until the 7-day week changes (unless forced).
   */
  fun generateDiscoverWeekly(
    playLogs: List<PlayLogRecord>,
    catalog: List<Song>,
    existingSnapshot: DiscoverWeeklyPlaylist? = null,
    nowMs: Long = System.currentTimeMillis(),
    forceRefresh: Boolean = false
  ): DiscoverWeeklyPlaylist? {
    val weekKey = currentWeekKey(nowMs)
    if (
      !forceRefresh &&
      existingSnapshot != null &&
      existingSnapshot.weekKey == weekKey &&
      existingSnapshot.tracks.isNotEmpty()
    ) {
      return existingSnapshot
    }

    val distinctCatalog = catalog
      .map { TrackMetadataClassifier.enrich(it) }
      .distinctBy { it.id }

    if (distinctCatalog.isEmpty()) return null

    val targetSize = minOf(30, distinctCatalog.size)
    val matchTarget = ((targetSize * 0.7).roundToInt()).coerceIn(0, targetSize)
    val discoveryTarget = (targetSize - matchTarget).coerceAtLeast(0)

    if (playLogs.isEmpty()) {
      // New user with zero history: cannot personalize 70/30 yet; return null for honest empty state
      return null
    }

    val recs = smartRecommendations(
      playLogs = playLogs,
      catalog = distinctCatalog,
      limit = distinctCatalog.size
    )
    val playedIds = playLogs.map { it.song.id }.toSet()
    val playedArtists = playLogs.map { normalizeArtist(it.song.artist) }.toSet()
    val playedGenres = playLogs.map { TrackMetadataClassifier.enrich(it.song).genre }.toSet()

    val matchingPool = if (recs.isNotEmpty()) {
      recs.map { it.song }
    } else {
      distinctCatalog
    }
    val matchedTracks = matchingPool.take(matchTarget)
    val usedIds = matchedTracks.map { it.id }.toMutableSet()

    // ~30% discovery: prioritize unheard artists & genres not yet played
    val discoveryCandidates = distinctCatalog
      .filter { it.id !in usedIds }
      .sortedWith(
        compareByDescending<Song> { it.id !in playedIds }
          .thenByDescending { normalizeArtist(it.artist) !in playedArtists }
          .thenByDescending { it.genre !in playedGenres }
      )

    val discoveryTracks = discoveryCandidates.take(discoveryTarget)
    usedIds.addAll(discoveryTracks.map { it.id })

    val remainder = if (matchedTracks.size + discoveryTracks.size < targetSize) {
      distinctCatalog.filter { it.id !in usedIds }
        .take(targetSize - matchedTracks.size - discoveryTracks.size)
    } else {
      emptyList()
    }

    val combined = (matchedTracks + discoveryTracks + remainder).take(targetSize)
    return DiscoverWeeklyPlaylist(
      weekKey = weekKey,
      generatedAtMs = nowMs,
      tracks = combined,
      matchCount = matchedTracks.size,
      discoveryCount = discoveryTracks.size + remainder.size
    )
  }

  /**
   * 3. Recently Played:
   * Last 20 unique songs, newest first.
   */
  fun recentlyPlayed(
    playLogs: List<PlayLogRecord>,
    limit: Int = 20
  ): List<Song> {
    return playLogs
      .sortedByDescending { it.playedAtMs }
      .distinctBy { it.song.id }
      .take(limit)
      .map { TrackMetadataClassifier.enrich(it.song) }
  }

  /**
   * 4. Most Played:
   * Ranks tracks by play count within Week, Month, or All time.
   */
  fun mostPlayed(
    playLogs: List<PlayLogRecord>,
    filter: MostPlayedFilter,
    nowMs: Long = System.currentTimeMillis(),
    limit: Int = 25
  ): List<RankedTrack> {
    val cutoff = filter.windowMs?.let { nowMs - it } ?: Long.MIN_VALUE
    val filtered = playLogs.filter { it.playedAtMs >= cutoff }
    if (filtered.isEmpty()) return emptyList()

    val grouped = filtered.groupBy { it.song.id }
    return grouped.values
      .map { entries ->
        val newest = entries.maxBy { it.playedAtMs }
        TrackMetadataClassifier.enrich(newest.song) to entries.size
      }
      .sortedWith(
        compareByDescending<Pair<Song, Int>> { it.second }
          .thenByDescending { pair ->
            filtered.filter { it.song.id == pair.first.id }.maxOf { it.playedAtMs }
          }
      )
      .take(limit)
      .mapIndexed { index, (song, count) ->
        RankedTrack(
          song = song,
          playCount = count,
          rank = index + 1
        )
      }
  }

  /**
   * 5. Mood Playlists:
   * Auto-built playlists for registered moods (Chill, Workout, Night, Focus) based on mood tags and BPM range.
   */
  fun buildMoodPlaylists(
    catalog: List<Song>,
    moods: List<MoodCategory> = MoodCategory.entries,
    maxPerPlaylist: Int = 20
  ): List<MoodPlaylist> {
    val distinctCatalog = catalog
      .map { TrackMetadataClassifier.enrich(it) }
      .distinctBy { it.id }

    if (distinctCatalog.isEmpty()) {
      return moods.map { MoodPlaylist(it, emptyList()) }
    }

    return moods.map { moodDef ->
      val matched = distinctCatalog
        .map { track ->
          val tagMatch = moodDef.targetMoods.any { target ->
            track.mood.equals(target, ignoreCase = true) ||
              track.title.contains(target, ignoreCase = true) ||
              track.genre.contains(target, ignoreCase = true)
          }
          val bpmMatch = track.bpm in moodDef.bpmRange
          val distanceToBand = when {
            track.bpm < moodDef.bpmRange.first -> moodDef.bpmRange.first - track.bpm
            track.bpm > moodDef.bpmRange.last -> track.bpm - moodDef.bpmRange.last
            else -> 0
          }
          val score = (if (tagMatch) 100 else 0) + (if (bpmMatch) 60 else (30 - distanceToBand).coerceAtLeast(0))
          Triple(track, tagMatch || bpmMatch, score)
        }
        .filter { (_, qualifies, _) -> qualifies }
        .sortedByDescending { (_, _, score) -> score }
        .map { (track, _, _) -> track }
        .take(maxPerPlaylist)

      MoodPlaylist(
        mood = moodDef,
        tracks = matched
      )
    }
  }

  /**
   * 6. Similar Songs:
   * Scores candidates by genre, BPM (±10), mood, and artist, ranked by similarity score (0..100).
   */
  fun similarSongs(
    target: Song,
    catalog: List<Song>,
    limit: Int = 8
  ): List<SimilarTrack> {
    val enrichedTarget = TrackMetadataClassifier.enrich(target)
    val targetArtist = normalizeArtist(enrichedTarget.artist)

    return catalog
      .asSequence()
      .map { TrackMetadataClassifier.enrich(it) }
      .filter { it.id != enrichedTarget.id }
      .distinctBy { it.id }
      .mapNotNull { candidate ->
        val sameGenre = candidate.genre.equals(enrichedTarget.genre, ignoreCase = true)
        val bpmDelta = abs(candidate.bpm - enrichedTarget.bpm)
        val withinBpmWindow = bpmDelta <= 10
        val sameMood = candidate.mood.equals(enrichedTarget.mood, ignoreCase = true)
        val sameArtist = targetArtist.isNotBlank() && normalizeArtist(candidate.artist) == targetArtist

        var score = 0
        if (sameGenre) score += 35
        if (withinBpmWindow) {
          score += 30 - bpmDelta
        } else if (bpmDelta <= 20) {
          score += (20 - bpmDelta)
        }
        if (sameMood) score += 20
        if (sameArtist) score += 15

        if (score < 20) return@mapNotNull null

        val reasons = buildList {
          if (sameArtist) add("Same artist")
          if (sameGenre) add(candidate.genre)
          if (withinBpmWindow) add("${candidate.bpm} BPM")
          if (sameMood) add("${candidate.mood} mood")
        }

        SimilarTrack(
          song = candidate,
          similarityScore = score.coerceIn(0, 100),
          reason = reasons.take(2).joinToString(" · ").ifBlank { "Similar sound" }
        )
      }
      .sortedByDescending { it.similarityScore }
      .take(limit)
      .toList()
  }

  /**
   * 7. Music DNA:
   * Visual listening profile computed strictly from qualified play history.
   */
  fun buildMusicDna(playLogs: List<PlayLogRecord>): MusicDnaProfile {
    if (playLogs.isEmpty()) {
      return MusicDnaProfile(
        hasEnoughHistory = false,
        totalQualifiedPlays = 0,
        totalMinutesListened = 0L,
        topGenres = emptyList(),
        favoriteBpmRange = null,
        favoriteArtists = emptyList(),
        listeningHours = List(24) { 0 },
        peakHourRangeLabel = "—",
        moodDistribution = emptyList(),
        discoveryPercentage = 0
      )
    }

    val enriched = playLogs.map { it.copy(song = TrackMetadataClassifier.enrich(it.song)) }
    val totalPlays = enriched.size
    val totalMinutes = enriched.sumOf {
      val ms = if (it.listenedDurationMs > 0L) it.listenedDurationMs else it.song.durationSeconds * 1000L
      ms
    } / 60_000L

    val topGenres = enriched
      .groupingBy { it.song.genre }
      .eachCount()
      .entries
      .sortedByDescending { it.value }
      .take(5)
      .map { (genre, count) ->
        GenreStat(
          genre = genre,
          playCount = count,
          percentage = ((count * 100f) / totalPlays).roundToInt().coerceIn(1, 100)
        )
      }

    val sortedBpms = enriched.map { it.song.bpm }.filter { it > 0 }.sorted()
    val favoriteBpmRange = if (sortedBpms.isNotEmpty()) {
      val lowIdx = ((sortedBpms.size - 1) * 0.25).roundToInt().coerceIn(0, sortedBpms.lastIndex)
      val highIdx = ((sortedBpms.size - 1) * 0.75).roundToInt().coerceIn(0, sortedBpms.lastIndex)
      val avg = sortedBpms.average().roundToInt()
      BpmRangeStat(
        minBpm = sortedBpms[lowIdx],
        maxBpm = sortedBpms[highIdx],
        averageBpm = avg
      )
    } else null

    val favoriteArtists = enriched
      .groupBy { normalizeArtist(it.song.artist) }
      .values
      .map { group ->
        val rep = group.maxBy { it.playedAtMs }.song
        ArtistStat(
          artist = rep.artist,
          playCount = group.size,
          artworkUrl = rep.artworkUrl
        )
      }
      .sortedByDescending { it.playCount }
      .take(5)

    val hourBuckets = IntArray(24)
    val cal = Calendar.getInstance()
    enriched.forEach { log ->
      cal.timeInMillis = log.playedAtMs
      val hour = cal.get(Calendar.HOUR_OF_DAY).coerceIn(0, 23)
      hourBuckets[hour]++
    }
    val peakHour = hourBuckets.indices.maxByOrNull { hourBuckets[it] } ?: 0
    val peakEndHour = (peakHour + 2) % 24
    val peakHourLabel = String.format(Locale.US, "%02d:00–%02d:00", peakHour, peakEndHour)

    val moodDistribution = enriched
      .groupingBy { it.song.mood }
      .eachCount()
      .entries
      .sortedByDescending { it.value }
      .map { (mood, count) ->
        MoodStat(
          mood = mood,
          playCount = count,
          percentage = ((count * 100f) / totalPlays).roundToInt().coerceIn(1, 100)
        )
      }

    // Discovery percentage: unique tracks & artists introduced relative to total plays
    val uniqueArtists = enriched.map { normalizeArtist(it.song.artist) }.toSet().size
    val uniqueTracks = enriched.map { it.song.id }.toSet().size
    val discoveryShare = ((((uniqueArtists + uniqueTracks) / 2.0) / totalPlays.toDouble()) * 100.0)
      .roundToInt()
      .coerceIn(0, 100)

    return MusicDnaProfile(
      hasEnoughHistory = true,
      totalQualifiedPlays = totalPlays,
      totalMinutesListened = totalMinutes.coerceAtLeast(1L),
      topGenres = topGenres,
      favoriteBpmRange = favoriteBpmRange,
      favoriteArtists = favoriteArtists,
      listeningHours = hourBuckets.toList(),
      peakHourRangeLabel = peakHourLabel,
      moodDistribution = moodDistribution,
      discoveryPercentage = discoveryShare
    )
  }

  /**
   * 8. Trending Tracks:
   * Compares current recent window (last 3 days) against previous window (3..6 days ago),
   * showing rank change indicators (Up / Down / Same / New).
   * Falls back cleanly across all history if recent 3 days is empty.
   */
  fun trendingTracks(
    playLogs: List<PlayLogRecord>,
    nowMs: Long = System.currentTimeMillis(),
    limit: Int = 15
  ): List<RankedTrack> {
    if (playLogs.isEmpty()) return emptyList()

    val recentWindowMs = 3L * DAY_MS
    val currentCutoff = nowMs - recentWindowMs
    val previousCutoff = nowMs - (2L * recentWindowMs)

    val currentLogs = playLogs.filter { it.playedAtMs >= currentCutoff }
    val activeCurrent = if (currentLogs.isNotEmpty()) currentLogs else playLogs
    val previousLogs = playLogs.filter { it.playedAtMs in previousCutoff until currentCutoff }

    val previousRankById: Map<String, Int> = previousLogs
      .groupBy { it.song.id }
      .entries
      .sortedByDescending { it.value.size }
      .mapIndexed { idx, entry -> entry.key to (idx + 1) }
      .toMap()

    return activeCurrent
      .groupBy { it.song.id }
      .entries
      .map { (_, group) ->
        val newest = group.maxBy { it.playedAtMs }
        TrackMetadataClassifier.enrich(newest.song) to group.size
      }
      .sortedWith(
        compareByDescending<Pair<Song, Int>> { it.second }
          .thenByDescending { (song, _) ->
            activeCurrent.filter { it.song.id == song.id }.maxOf { it.playedAtMs }
          }
      )
      .take(limit)
      .mapIndexed { index, (song, count) ->
        val currentRank = index + 1
        val prevRank = previousRankById[song.id]
        val direction = when {
          prevRank == null && previousRankById.isNotEmpty() -> RankDirection.New
          prevRank == null -> RankDirection.Same
          prevRank > currentRank -> RankDirection.Up(prevRank - currentRank)
          prevRank < currentRank -> RankDirection.Down(currentRank - prevRank)
          else -> RankDirection.Same
        }
        RankedTrack(
          song = song,
          playCount = count,
          rank = currentRank,
          rankDirection = direction
        )
      }
  }

  /**
   * 9. Rising Artists:
   * Ranks artists by fastest play growth (current 7d vs previous 7d window) with growth %.
   */
  fun risingArtists(
    playLogs: List<PlayLogRecord>,
    nowMs: Long = System.currentTimeMillis(),
    limit: Int = 10
  ): List<RisingArtist> {
    if (playLogs.isEmpty()) return emptyList()

    val currentStart = nowMs - WEEK_MS
    val previousStart = nowMs - (2L * WEEK_MS)

    val currentByArtist = playLogs
      .filter { it.playedAtMs >= currentStart }
      .groupBy { normalizeArtist(it.song.artist) }

    val previousByArtist = playLogs
      .filter { it.playedAtMs in previousStart until currentStart }
      .groupBy { normalizeArtist(it.song.artist) }

    val activeMap = if (currentByArtist.isNotEmpty()) {
      currentByArtist
    } else {
      playLogs.groupBy { normalizeArtist(it.song.artist) }
    }

    return activeMap.entries
      .filter { it.key.isNotBlank() }
      .map { (normArtist, recentGroup) ->
        val prevCount = previousByArtist[normArtist]?.size ?: 0
        val recentCount = recentGroup.size
        val growthPct = when {
          prevCount == 0 -> recentCount * 100
          else -> (((recentCount - prevCount).toDouble() / prevCount.toDouble()) * 100.0).roundToInt()
        }
        val rep = recentGroup.maxBy { it.playedAtMs }.song
        RisingArtist(
          artist = rep.artist,
          recentPlays = recentCount,
          previousPlays = prevCount,
          growthPercentage = growthPct,
          artworkUrl = rep.artworkUrl
        )
      }
      .filter { it.growthPercentage > 0 || it.recentPlays >= 1 }
      .sortedWith(
        compareByDescending<RisingArtist> { it.growthPercentage }
          .thenByDescending { it.recentPlays }
      )
      .take(limit)
  }

  fun formatRelativeTimestamp(timestampMs: Long, nowMs: Long = System.currentTimeMillis()): String {
    val diffMs = (nowMs - timestampMs).coerceAtLeast(0L)
    val mins = diffMs / 60_000L
    val hours = mins / 60L
    val days = hours / 24L
    return when {
      mins < 1L -> "Just now"
      mins < 60L -> "${mins}m ago"
      hours < 24L -> "${hours}h ago"
      days < 7L -> "${days}d ago"
      else -> SimpleDateFormat("MMM d", Locale.US).format(Date(timestampMs))
    }
  }

  private fun normalizeArtist(artist: String): String {
    return artist.trim().lowercase(Locale.ROOT)
  }
}
