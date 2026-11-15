package com.example.recommendation

import com.example.model.Song

sealed interface PlaySessionAction {
  data class LogQualifiedPlay(
    val song: Song,
    val startedAtMs: Long,
    val listenedDurationMs: Long
  ) : PlaySessionAction

  data class UpdateListenedDuration(
    val playLogId: Long,
    val listenedDurationMs: Long
  ) : PlaySessionAction
}

/**
 * Tracks active playback time for the current song and triggers persistence ONLY after
 * the user has listened for at least [RecommendationEngine.MIN_QUALIFIED_PLAY_MS] (30 seconds).
 */
class PlaySessionTracker(
  private val qualificationThresholdMs: Long = RecommendationEngine.MIN_QUALIFIED_PLAY_MS,
  private val updateStepMs: Long = 2_000L
) {
  private var activeSong: Song? = null
  private var startedAtMs: Long = 0L
  private var accumulatedMs: Long = 0L
  private var qualifiedTriggered: Boolean = false
  private var persistedRowId: Long? = null
  private var lastPersistedDurationMs: Long = 0L

  val currentAccumulatedMs: Long
    get() = accumulatedMs

  val isQualified: Boolean
    get() = qualifiedTriggered

  @Synchronized
  fun startNewSession(song: Song, nowMs: Long = System.currentTimeMillis()): PlaySessionAction.UpdateListenedDuration? {
    val flushAction = flushPendingDurationLocked()
    activeSong = TrackMetadataClassifier.enrich(song)
    startedAtMs = nowMs
    accumulatedMs = 0L
    qualifiedTriggered = false
    persistedRowId = null
    lastPersistedDurationMs = 0L
    return flushAction
  }

  @Synchronized
  fun onActivePlaybackDelta(songId: String, deltaMs: Long): PlaySessionAction? {
    val song = activeSong ?: return null
    if (song.id != songId || deltaMs <= 0L) return null

    accumulatedMs += deltaMs
    if (!qualifiedTriggered && accumulatedMs >= qualificationThresholdMs) {
      qualifiedTriggered = true
      lastPersistedDurationMs = accumulatedMs
      return PlaySessionAction.LogQualifiedPlay(
        song = song,
        startedAtMs = startedAtMs,
        listenedDurationMs = accumulatedMs
      )
    }

    val rowId = persistedRowId
    if (qualifiedTriggered && rowId != null && (accumulatedMs - lastPersistedDurationMs) >= updateStepMs) {
      lastPersistedDurationMs = accumulatedMs
      return PlaySessionAction.UpdateListenedDuration(
        playLogId = rowId,
        listenedDurationMs = accumulatedMs
      )
    }
    return null
  }

  @Synchronized
  fun attachPersistedRowId(songId: String, startedAt: Long, rowId: Long): Long? {
    val song = activeSong ?: return null
    if (song.id == songId && startedAtMs == startedAt && rowId > 0L) {
      persistedRowId = rowId
      if (accumulatedMs > lastPersistedDurationMs) {
        lastPersistedDurationMs = accumulatedMs
        return accumulatedMs
      }
    }
    return null
  }

  @Synchronized
  fun flushPendingDuration(): PlaySessionAction.UpdateListenedDuration? {
    return flushPendingDurationLocked()
  }

  @Synchronized
  fun clearSession(): PlaySessionAction.UpdateListenedDuration? {
    val flushAction = flushPendingDurationLocked()
    activeSong = null
    startedAtMs = 0L
    accumulatedMs = 0L
    qualifiedTriggered = false
    persistedRowId = null
    lastPersistedDurationMs = 0L
    return flushAction
  }

  private fun flushPendingDurationLocked(): PlaySessionAction.UpdateListenedDuration? {
    val rowId = persistedRowId
    if (qualifiedTriggered && rowId != null && accumulatedMs > lastPersistedDurationMs) {
      lastPersistedDurationMs = accumulatedMs
      return PlaySessionAction.UpdateListenedDuration(
        playLogId = rowId,
        listenedDurationMs = accumulatedMs
      )
    }
    return null
  }
}
