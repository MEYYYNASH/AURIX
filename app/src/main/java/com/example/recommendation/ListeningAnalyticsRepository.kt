package com.example.recommendation

import com.example.data.AurixDao
import com.example.data.DiscoverWeeklyEntity
import com.example.data.PlayHistoryEntity
import com.example.data.PlayLogEntity
import com.example.data.SongFeedbackEntity
import com.example.data.SongMetadataEntity
import com.example.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Repository bridging Room persistence ([AurixDao]) with the recommendation,
 * Because You Listened, MoodFlow, and Music DNA engines.
 */
class ListeningAnalyticsRepository(
  private val dao: AurixDao
) {
  fun observeQualifiedPlayLogs(): Flow<List<PlayLogRecord>> {
    return dao.observePlayLogs().map { rows ->
      rows
        .filter { RecommendationEngine.isQualifiedPlay(it.listenedDurationMs) }
        .map { entity ->
          PlayLogRecord(
            id = entity.id,
            song = entity.toDomainSong(),
            playedAtMs = entity.playedAt,
            listenedDurationMs = entity.listenedDurationMs
          )
        }
    }
  }

  /**
   * Observes full listening interaction records (completed %, skipped, replayed)
   * across all logged plays for Because You Listened and MoodFlow.
   */
  fun observeRichPlayHistory(): Flow<List<RichPlayRecord>> {
    return dao.observePlayLogs().map { rows ->
      rows.map { entity ->
        RichPlayRecord(
          song = TrackMetadataClassifier.enrich(entity.toDomainSong()),
          playedAtMs = entity.playedAt,
          completedPercent = entity.completedPercent,
          completed = entity.completed,
          skipped = entity.skipped,
          replayed = entity.replayed
        )
      }
    }
  }

  fun observeSongFeedback(): Flow<Map<String, Boolean>> {
    return dao.observeSongFeedback().map { entries ->
      entries.associate { it.songId to it.liked }
    }
  }

  fun observeStoredCatalogSongs(): Flow<List<Song>> {
    return dao.observeSongMetadata().map { rows ->
      rows.map { it.toDomainSong() }
    }
  }

  fun observeDiscoverWeekly(): Flow<DiscoverWeeklyPlaylist?> {
    return dao.observeDiscoverWeekly().map { rows ->
      if (rows.isEmpty()) {
        null
      } else {
        val sorted = rows.sortedBy { it.position }
        val first = sorted.first()
        val tracks = sorted.map { entity ->
          TrackMetadataClassifier.enrich(
            Song(
              id = entity.songId,
              title = entity.title,
              artist = entity.artist,
              artworkUrl = entity.artworkUrl,
              durationSeconds = entity.durationSeconds,
              streamUrl = entity.streamUrl,
              source = entity.source,
              genre = entity.genre,
              bpm = entity.bpm,
              mood = entity.mood,
              fullDurationSeconds = entity.durationSeconds
            )
          )
        }
        val matchCount = sorted.count { !it.isDiscovery }
        DiscoverWeeklyPlaylist(
          weekKey = first.weekKey,
          generatedAtMs = first.generatedAt,
          tracks = tracks,
          matchCount = matchCount,
          discoveryCount = (tracks.size - matchCount).coerceAtLeast(0)
        )
      }
    }
  }

  suspend fun logQualifiedPlay(
    song: Song,
    playedAtMs: Long,
    listenedDurationMs: Long
  ): Long = withContext(Dispatchers.IO) {
    val enriched = TrackMetadataClassifier.enrich(song)
    val totalMs = (enriched.fullDurationSeconds.coerceAtLeast(enriched.durationSeconds) * 1000L)
      .coerceAtLeast(1L)
    val completedPct = (listenedDurationMs.toFloat() / totalMs.toFloat()).coerceIn(0f, 1f)
    val completed = completedPct >= 0.80f
    val priorCount = dao.countPlaysForSongSince(
      songId = enriched.id,
      sinceMs = playedAtMs - BecauseYouListenedEngine.TASTE_WINDOW_MS
    )
    val replayed = priorCount > 0

    val rowId = dao.insertPlayLog(
      PlayLogEntity(
        songId = enriched.id,
        title = enriched.title,
        artist = enriched.artist,
        artworkUrl = enriched.artworkUrl,
        durationSeconds = enriched.durationSeconds,
        streamUrl = enriched.streamUrl,
        source = enriched.source,
        genre = enriched.genre,
        bpm = enriched.bpm,
        mood = enriched.mood,
        playedAt = playedAtMs,
        listenedDurationMs = listenedDurationMs,
        completedPercent = completedPct,
        completed = completed,
        skipped = false,
        replayed = replayed,
        energy = enriched.energy,
        sad = enriched.sad,
        chill = enriched.chill,
        happy = enriched.happy,
        calm = enriched.calm,
        energetic = enriched.energetic,
        romantic = enriched.romantic,
        hasVocals = enriched.hasVocals,
        language = enriched.language
      )
    )
    dao.recordPlay(
      PlayHistoryEntity(
        songId = enriched.id,
        title = enriched.title,
        artist = enriched.artist,
        artworkUrl = enriched.artworkUrl,
        durationSeconds = enriched.durationSeconds,
        streamUrl = enriched.streamUrl,
        source = enriched.source,
        playedAt = playedAtMs,
        completedPercent = completedPct,
        completed = completed,
        skipped = false,
        replayed = replayed
      )
    )
    dao.upsertSongMetadata(enriched.toMetadataEntity(updatedAt = playedAtMs))
    rowId
  }

  /**
   * Persists early skips (even under 30s) so Because You Listened and MoodFlow learn
   * negative skip preferences without polluting Music DNA's 30s+ qualified charts.
   */
  suspend fun recordEarlySkip(
    song: Song,
    playedAtMs: Long,
    listenedDurationMs: Long,
    durationMs: Long
  ) = withContext(Dispatchers.IO) {
    val enriched = TrackMetadataClassifier.enrich(song)
    val safeDur = durationMs.coerceAtLeast(enriched.durationSeconds * 1000L).coerceAtLeast(1L)
    val pct = (listenedDurationMs.toFloat() / safeDur.toFloat()).coerceIn(0f, 1f)
    dao.insertPlayLog(
      PlayLogEntity(
        songId = enriched.id,
        title = enriched.title,
        artist = enriched.artist,
        artworkUrl = enriched.artworkUrl,
        durationSeconds = enriched.durationSeconds,
        streamUrl = enriched.streamUrl,
        source = enriched.source,
        genre = enriched.genre,
        bpm = enriched.bpm,
        mood = enriched.mood,
        playedAt = playedAtMs,
        listenedDurationMs = listenedDurationMs,
        completedPercent = pct,
        completed = false,
        skipped = true,
        replayed = false,
        energy = enriched.energy,
        sad = enriched.sad,
        chill = enriched.chill,
        happy = enriched.happy,
        calm = enriched.calm,
        energetic = enriched.energetic,
        romantic = enriched.romantic,
        hasVocals = enriched.hasVocals,
        language = enriched.language
      )
    )
    dao.recordPlay(
      PlayHistoryEntity(
        songId = enriched.id,
        title = enriched.title,
        artist = enriched.artist,
        artworkUrl = enriched.artworkUrl,
        durationSeconds = enriched.durationSeconds,
        streamUrl = enriched.streamUrl,
        source = enriched.source,
        playedAt = playedAtMs,
        completedPercent = pct,
        completed = false,
        skipped = true,
        replayed = false
      )
    )
  }

  suspend fun updatePlayLogDuration(
    playLogId: Long,
    listenedDurationMs: Long
  ) = withContext(Dispatchers.IO) {
    if (playLogId > 0L) {
      dao.updatePlayLogDuration(playLogId, listenedDurationMs)
    }
  }

  suspend fun updatePlayOutcome(
    playLogId: Long,
    song: Song,
    listenedDurationMs: Long,
    totalDurationMs: Long,
    skipped: Boolean,
    replayed: Boolean
  ) = withContext(Dispatchers.IO) {
    val safeTotal = totalDurationMs.coerceAtLeast(song.durationSeconds * 1000L).coerceAtLeast(1L)
    val completedPct = (listenedDurationMs.toFloat() / safeTotal.toFloat()).coerceIn(0f, 1f)
    val completed = !skipped && completedPct >= 0.80f
    if (playLogId > 0L) {
      dao.updatePlayLogOutcome(
        playLogId = playLogId,
        listenedDurationMs = listenedDurationMs,
        completedPercent = completedPct,
        completed = completed,
        skipped = skipped,
        replayed = replayed
      )
    }
    dao.updatePlayHistoryOutcome(
      songId = song.id,
      completedPercent = completedPct,
      completed = completed,
      skipped = skipped,
      replayed = replayed
    )
  }

  suspend fun setSongFeedback(song: Song, liked: Boolean?) = withContext(Dispatchers.IO) {
    val enriched = TrackMetadataClassifier.enrich(song)
    dao.upsertSongMetadata(enriched.toMetadataEntity())
    if (liked == null) {
      dao.deleteSongFeedback(enriched.id)
    } else {
      dao.upsertSongFeedback(
        SongFeedbackEntity(
          songId = enriched.id,
          liked = liked
        )
      )
    }
  }

  suspend fun upsertCatalogMetadata(songs: List<Song>) = withContext(Dispatchers.IO) {
    if (songs.isEmpty()) return@withContext
    val entities = songs
      .distinctBy { it.id }
      .map { TrackMetadataClassifier.enrich(it).toMetadataEntity() }
    dao.upsertSongMetadataList(entities)
  }

  suspend fun ensureDiscoverWeekly(
    playLogs: List<PlayLogRecord>,
    catalog: List<Song>,
    existingSnapshot: DiscoverWeeklyPlaylist?,
    forceRefresh: Boolean = false
  ): DiscoverWeeklyPlaylist? = withContext(Dispatchers.Default) {
    val generated = RecommendationEngine.generateDiscoverWeekly(
      playLogs = playLogs,
      catalog = catalog,
      existingSnapshot = existingSnapshot,
      forceRefresh = forceRefresh
    ) ?: return@withContext null

    if (
      existingSnapshot == null ||
      forceRefresh ||
      existingSnapshot.weekKey != generated.weekKey ||
      existingSnapshot.tracks.map { it.id } != generated.tracks.map { it.id }
    ) {
      val entities = generated.tracks.mapIndexed { idx, track ->
        DiscoverWeeklyEntity(
          weekKey = generated.weekKey,
          position = idx,
          songId = track.id,
          title = track.title,
          artist = track.artist,
          artworkUrl = track.artworkUrl,
          durationSeconds = track.durationSeconds,
          streamUrl = track.streamUrl,
          source = track.source,
          genre = track.genre,
          bpm = track.bpm,
          mood = track.mood,
          isDiscovery = idx >= generated.matchCount,
          generatedAt = generated.generatedAtMs
        )
      }
      withContext(Dispatchers.IO) {
        dao.replaceDiscoverWeekly(entities)
      }
    }
    generated
  }
}

private fun PlayLogEntity.toDomainSong(): Song {
  val hasRichVector = sad > 0f || chill > 0f || happy > 0f || calm > 0f || energetic > 0f || romantic > 0f
  return Song(
    id = songId,
    title = title,
    artist = artist,
    artworkUrl = artworkUrl,
    durationSeconds = durationSeconds,
    streamUrl = streamUrl,
    source = source,
    genre = genre,
    bpm = bpm,
    mood = mood,
    fullDurationSeconds = durationSeconds,
    energy = energy,
    sad = sad,
    chill = chill,
    happy = happy,
    calm = calm,
    energetic = energetic,
    romantic = romantic,
    hasVocals = hasVocals,
    language = language,
    hasMoodMetadata = hasRichVector
  )
}

private fun Song.toMetadataEntity(updatedAt: Long = System.currentTimeMillis()): SongMetadataEntity {
  return SongMetadataEntity(
    id = id,
    title = title,
    artist = artist,
    artworkUrl = artworkUrl,
    durationSeconds = durationSeconds,
    streamUrl = streamUrl,
    source = source,
    genre = genre,
    bpm = bpm,
    mood = mood,
    energy = energy,
    sad = sad,
    chill = chill,
    happy = happy,
    calm = calm,
    energetic = energetic,
    romantic = romantic,
    hasVocals = hasVocals,
    language = language,
    updatedAt = updatedAt
  )
}

private fun SongMetadataEntity.toDomainSong(): Song {
  return Song(
    id = id,
    title = title,
    artist = artist,
    artworkUrl = artworkUrl,
    durationSeconds = durationSeconds,
    streamUrl = streamUrl,
    source = source,
    genre = genre,
    bpm = bpm,
    mood = mood,
    fullDurationSeconds = durationSeconds,
    energy = energy,
    sad = sad,
    chill = chill,
    happy = happy,
    calm = calm,
    energetic = energetic,
    romantic = romantic,
    hasVocals = hasVocals,
    language = language,
    hasMoodMetadata = true
  )
}
