package com.example.data

import com.example.lyrics.LrcCodec
import com.example.lyrics.LyricsEditorDraft
import kotlinx.coroutines.flow.Flow

/**
 * Offline Room-backed repository for Developer Mode Custom Lyrics, drafts, and per-song offsets.
 * Never modifies or overwrites LRCLIB records in `lyrics_cache`.
 */
class CustomLyricsRepository(private val dao: AurixDao) {

  val customLyricsSongIds: Flow<List<String>> = dao.observeCustomLyricsSongIds()
  val allCustomLyrics: Flow<List<CustomLyricsEntity>> = dao.observeAllCustomLyrics()
  val draftCount: Flow<Int> = dao.observeCustomLyricsDraftCount()

  fun observeCustomLyrics(songId: String): Flow<CustomLyricsEntity?> =
    dao.observeCustomLyrics(songId)

  suspend fun getCustomLyrics(songId: String): CustomLyricsEntity? =
    dao.getCustomLyrics(songId)

  suspend fun saveCustomLyrics(
    songId: String,
    lrcText: String,
    title: String,
    artist: String,
    globalOffsetMs: Long
  ) {
    val now = System.currentTimeMillis()
    dao.upsertCustomLyrics(
      CustomLyricsEntity(
        songId = songId,
        lrcText = lrcText,
        source = LrcCodec.SOURCE_CUSTOM,
        title = title,
        artist = artist,
        updatedAt = now
      )
    )
    val clampedOffset = globalOffsetMs.coerceIn(LrcCodec.MIN_GLOBAL_OFFSET_MS, LrcCodec.MAX_GLOBAL_OFFSET_MS)
    if (clampedOffset == 0L) {
      dao.deleteLyricOffset(songId)
    } else {
      dao.saveLyricOffset(LyricOffsetEntity(songId = songId, offsetMs = clampedOffset))
    }
    dao.deleteCustomLyricsDraft(songId)
  }

  /**
   * Deletes ONLY custom lyrics for [songId], leaving cached LRCLIB data completely intact.
   */
  suspend fun deleteCustomLyrics(songId: String) {
    dao.deleteCustomLyrics(songId)
    dao.deleteCustomLyricsDraft(songId)
  }

  suspend fun getDraft(songId: String): Pair<LyricsEditorDraft, Long>? {
    val entity = dao.getCustomLyricsDraft(songId) ?: return null
    val decoded = LrcCodec.decodeDraft(entity.draftJson) ?: return null
    return decoded to entity.updatedAt
  }

  suspend fun saveDraft(songId: String, draft: LyricsEditorDraft) {
    val encoded = LrcCodec.encodeDraft(draft)
    dao.upsertCustomLyricsDraft(
      CustomLyricsDraftEntity(
        songId = songId,
        draftJson = encoded,
        step = draft.step.coerceIn(0, 1),
        globalOffsetMs = draft.globalOffsetMs.coerceIn(
          LrcCodec.MIN_GLOBAL_OFFSET_MS,
          LrcCodec.MAX_GLOBAL_OFFSET_MS
        ),
        updatedAt = System.currentTimeMillis()
      )
    )
  }

  suspend fun clearDraft(songId: String) {
    dao.deleteCustomLyricsDraft(songId)
  }

  suspend fun getGlobalOffsetMs(songId: String): Long {
    return (dao.getLyricOffset(songId) ?: 0L).coerceIn(
      LrcCodec.MIN_GLOBAL_OFFSET_MS,
      LrcCodec.MAX_GLOBAL_OFFSET_MS
    )
  }

  suspend fun saveGlobalOffsetMs(songId: String, offsetMs: Long) {
    val clamped = offsetMs.coerceIn(LrcCodec.MIN_GLOBAL_OFFSET_MS, LrcCodec.MAX_GLOBAL_OFFSET_MS)
    if (clamped == 0L) {
      dao.deleteLyricOffset(songId)
    } else {
      dao.saveLyricOffset(LyricOffsetEntity(songId = songId, offsetMs = clamped))
    }
  }
}
