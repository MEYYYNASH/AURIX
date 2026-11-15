package com.example.recommendation

import com.example.data.AurixDao
import com.example.data.DiscoverWeeklyEntity
import com.example.data.PlayHistoryEntity
import com.example.data.PlayLogEntity
import com.example.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

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
            song = Song(
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
            ),
            playedAtMs = entity.playedAt,
            listenedDurationMs = entity.listenedDurationMs
          )
        }
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
        listenedDurationMs = listenedDurationMs
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
        playedAt = playedAtMs
      )
    )
    rowId
  }

  suspend fun updatePlayLogDuration(
    playLogId: Long,
    listenedDurationMs: Long
  ) = withContext(Dispatchers.IO) {
    if (playLogId > 0L) {
      dao.updatePlayLogDuration(playLogId, listenedDurationMs)
    }
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
