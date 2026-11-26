package com.example.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "favorite_songs")
data class FavoriteSongEntity(
  @PrimaryKey val id: String,
  val title: String,
  val artist: String,
  val artworkUrl: String,
  val durationSeconds: Long,
  val streamUrl: String,
  val source: String,
  val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "play_history")
data class PlayHistoryEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val songId: String,
  val title: String,
  val artist: String,
  val artworkUrl: String,
  val durationSeconds: Long,
  val streamUrl: String,
  val source: String,
  val playedAt: Long = System.currentTimeMillis(),
  val completedPercent: Float = 0f,
  val completed: Boolean = false,
  val skipped: Boolean = false,
  val replayed: Boolean = false
)

@Entity(tableName = "local_songs")
data class LocalSongEntity(
  @PrimaryKey val id: String,
  val title: String,
  val artist: String,
  val artworkUrl: String,
  val durationSeconds: Long,
  val streamUrl: String,
  val source: String,
  val addedAt: Long = System.currentTimeMillis(),
  val album: String = "Unknown Album",
  val trackNumber: Int = 0,
  val year: String = ""
)

@Entity(tableName = "album_covers")
data class AlbumCoverEntity(
  @PrimaryKey val albumId: String,
  val coverPath: String,
  val mode: String,
  val sourceUris: String = "",
  val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "custom_albums")
data class CustomAlbumEntity(
  @PrimaryKey val albumId: String,
  val title: String,
  val artist: String,
  val year: String = "",
  val createdAt: Long = System.currentTimeMillis()
)

@Entity(
  tableName = "album_tracks",
  primaryKeys = ["albumId", "songId"]
)
data class AlbumTrackEntity(
  val albumId: String,
  val songId: String,
  val title: String,
  val artist: String,
  val artworkUrl: String,
  val durationSeconds: Long,
  val streamUrl: String,
  val source: String,
  val genre: String = "",
  val bpm: Int = 0,
  val mood: String = "",
  val albumName: String = "",
  val trackNumber: Int = 0,
  val year: String = "",
  val addedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "deleted_albums")
data class DeletedAlbumEntity(
  @PrimaryKey val albumId: String,
  val deletedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "lyric_offsets")
data class LyricOffsetEntity(
  @PrimaryKey val songId: String,
  val offsetMs: Long
)

@Entity(tableName = "lyrics_cache")
data class LyricsCacheEntity(
  @PrimaryKey val songId: String,
  val songKey: String,
  val hasLyrics: Boolean,
  val rawLrc: String? = null,
  val plainLyrics: String? = null,
  val matchedTitle: String? = null,
  val matchedArtist: String? = null,
  val isReportedBad: Boolean = false,
  val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "bad_lyrics")
data class BadLyricsEntity(
  @PrimaryKey val songKey: String,
  val songId: String,
  val title: String,
  val artist: String,
  val reportedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "custom_lyrics")
data class CustomLyricsEntity(
  @PrimaryKey val songId: String,
  val lrcText: String,
  val source: String = "custom",
  val title: String = "",
  val artist: String = "",
  val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "custom_lyrics_drafts")
data class CustomLyricsDraftEntity(
  @PrimaryKey val songId: String,
  val draftJson: String,
  val step: Int = 0,
  val globalOffsetMs: Long = 0L,
  val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "play_logs")
data class PlayLogEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val songId: String,
  val title: String,
  val artist: String,
  val artworkUrl: String,
  val durationSeconds: Long,
  val streamUrl: String,
  val source: String,
  val genre: String,
  val bpm: Int,
  val mood: String,
  val playedAt: Long = System.currentTimeMillis(),
  val listenedDurationMs: Long = 0L,
  val completedPercent: Float = 0f,
  val completed: Boolean = false,
  val skipped: Boolean = false,
  val replayed: Boolean = false,
  val energy: Float = 0.5f,
  val sad: Float = 0f,
  val chill: Float = 0f,
  val happy: Float = 0f,
  val calm: Float = 0f,
  val energetic: Float = 0f,
  val romantic: Float = 0f,
  val hasVocals: Boolean = true,
  val language: String = "en"
)

@Entity(tableName = "song_metadata")
data class SongMetadataEntity(
  @PrimaryKey val id: String,
  val title: String,
  val artist: String,
  val artworkUrl: String,
  val durationSeconds: Long,
  val streamUrl: String,
  val source: String,
  val genre: String,
  val bpm: Int,
  val mood: String,
  val energy: Float,
  val sad: Float,
  val chill: Float,
  val happy: Float,
  val calm: Float,
  val energetic: Float,
  val romantic: Float,
  val hasVocals: Boolean,
  val language: String,
  val playCount: Int = 0,
  val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "song_feedback")
data class SongFeedbackEntity(
  @PrimaryKey val songId: String,
  val liked: Boolean,
  val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
  tableName = "discover_weekly",
  primaryKeys = ["weekKey", "position"]
)
data class DiscoverWeeklyEntity(
  val weekKey: String,
  val position: Int,
  val songId: String,
  val title: String,
  val artist: String,
  val artworkUrl: String,
  val durationSeconds: Long,
  val streamUrl: String,
  val source: String,
  val genre: String,
  val bpm: Int,
  val mood: String,
  val isDiscovery: Boolean,
  val generatedAt: Long = System.currentTimeMillis()
)

@Dao
interface AurixDao {
  @Query("SELECT * FROM favorite_songs ORDER BY timestamp DESC")
  fun getFavorites(): Flow<List<FavoriteSongEntity>>

  @Query("SELECT * FROM favorite_songs WHERE id = :songId")
  suspend fun getFavoriteById(songId: String): FavoriteSongEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertFavorite(song: FavoriteSongEntity)

  @Query("DELETE FROM favorite_songs WHERE id = :songId")
  suspend fun deleteFavorite(songId: String)

  @Query("UPDATE favorite_songs SET artworkUrl = :artworkUrl WHERE id = :songId")
  suspend fun updateFavoriteArtwork(songId: String, artworkUrl: String)

  @Query("SELECT * FROM play_history ORDER BY playedAt DESC LIMIT 50")
  fun getPlayHistory(): Flow<List<PlayHistoryEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertHistory(history: PlayHistoryEntity)

  @Query("DELETE FROM play_history WHERE songId = :songId")
  suspend fun deleteHistoryBySongId(songId: String)

  @Query("DELETE FROM play_history")
  suspend fun clearPlayHistory()

  @Query("UPDATE play_history SET artworkUrl = :artworkUrl WHERE songId = :songId")
  suspend fun updateHistoryArtwork(songId: String, artworkUrl: String)

  @Transaction
  suspend fun recordPlay(history: PlayHistoryEntity) {
    deleteHistoryBySongId(history.songId)
    insertHistory(history)
  }

  @Query("SELECT * FROM local_songs ORDER BY addedAt DESC")
  fun getLocalSongs(): Flow<List<LocalSongEntity>>

  @Query("SELECT * FROM local_songs WHERE id = :songId")
  suspend fun getLocalSongById(songId: String): LocalSongEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertLocalSong(song: LocalSongEntity)

  @Query("UPDATE local_songs SET artworkUrl = :artworkUrl WHERE id = :songId")
  suspend fun updateLocalSongArtwork(songId: String, artworkUrl: String)

  @Query(
    """
    UPDATE local_songs
    SET title = :title, artist = :artist, album = :album,
        trackNumber = :trackNumber, year = :year,
        artworkUrl = :artworkUrl, durationSeconds = :durationSeconds
    WHERE id = :songId
    """
  )
  suspend fun updateLocalSongMetadata(
    songId: String,
    title: String,
    artist: String,
    album: String,
    trackNumber: Int,
    year: String,
    artworkUrl: String,
    durationSeconds: Long
  )

  @Query("DELETE FROM local_songs WHERE id = :songId")
  suspend fun deleteLocalSong(songId: String)

  @Query("SELECT * FROM album_covers")
  fun observeAlbumCovers(): Flow<List<AlbumCoverEntity>>

  @Query("SELECT * FROM album_covers WHERE albumId = :albumId LIMIT 1")
  suspend fun getAlbumCover(albumId: String): AlbumCoverEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertAlbumCover(entity: AlbumCoverEntity)

  @Query("DELETE FROM album_covers WHERE albumId = :albumId")
  suspend fun deleteAlbumCover(albumId: String)

  @Query("SELECT * FROM custom_albums ORDER BY createdAt DESC")
  fun observeCustomAlbums(): Flow<List<CustomAlbumEntity>>

  @Query("SELECT * FROM custom_albums WHERE albumId = :albumId LIMIT 1")
  suspend fun getCustomAlbum(albumId: String): CustomAlbumEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertCustomAlbum(entity: CustomAlbumEntity)

  @Query("DELETE FROM custom_albums WHERE albumId = :albumId")
  suspend fun deleteCustomAlbum(albumId: String)

  @Query("SELECT * FROM album_tracks ORDER BY addedAt ASC")
  fun observeAllAlbumTracks(): Flow<List<AlbumTrackEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertAlbumTracks(entries: List<AlbumTrackEntity>)

  @Query("DELETE FROM album_tracks WHERE albumId = :albumId AND songId = :songId")
  suspend fun deleteAlbumTrack(albumId: String, songId: String)

  @Query("DELETE FROM album_tracks WHERE albumId = :albumId")
  suspend fun deleteTracksForAlbum(albumId: String)

  @Query("DELETE FROM album_tracks WHERE songId = :songId")
  suspend fun deleteAlbumTracksForSong(songId: String)

  @Query("UPDATE album_tracks SET artworkUrl = :artworkUrl WHERE songId = :songId")
  suspend fun updateAlbumTrackArtwork(songId: String, artworkUrl: String)

  @Query("SELECT albumId FROM deleted_albums")
  fun observeDeletedAlbumIds(): Flow<List<String>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun markAlbumDeleted(entity: DeletedAlbumEntity)

  @Query("DELETE FROM deleted_albums WHERE albumId = :albumId")
  suspend fun unmarkAlbumDeleted(albumId: String)

  @Query("SELECT offsetMs FROM lyric_offsets WHERE songId = :songId")
  suspend fun getLyricOffset(songId: String): Long?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun saveLyricOffset(entity: LyricOffsetEntity)

  @Query("DELETE FROM lyric_offsets WHERE songId = :songId")
  suspend fun deleteLyricOffset(songId: String)

  @Query("SELECT songId FROM lyrics_cache WHERE hasLyrics = 1 AND isReportedBad = 0")
  fun observeVerifiedLyricsSongIds(): Flow<List<String>>

  @Query("SELECT * FROM lyrics_cache WHERE songId = :songId LIMIT 1")
  suspend fun getLyricsCache(songId: String): LyricsCacheEntity?

  @Query("SELECT * FROM lyrics_cache WHERE songKey = :songKey AND isReportedBad = 0 LIMIT 1")
  suspend fun getLyricsCacheByKey(songKey: String): LyricsCacheEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertLyricsCache(entry: LyricsCacheEntity)

  @Query("UPDATE lyrics_cache SET hasLyrics = 0, isReportedBad = 1, rawLrc = NULL, plainLyrics = NULL, updatedAt = :updatedAt WHERE songId = :songId OR songKey = :songKey")
  suspend fun markLyricsCacheBad(songId: String, songKey: String, updatedAt: Long = System.currentTimeMillis())

  @Query("DELETE FROM lyrics_cache WHERE isReportedBad = 1")
  suspend fun clearReportedLyricsCache()

  @Query("SELECT songKey FROM bad_lyrics")
  fun observeBadLyricsKeys(): Flow<List<String>>

  @Query("SELECT COUNT(*) FROM bad_lyrics")
  fun observeBadLyricsCount(): Flow<Int>

  @Query("SELECT EXISTS(SELECT 1 FROM bad_lyrics WHERE songKey = :songKey OR songId = :songId)")
  suspend fun isBadLyrics(songId: String, songKey: String): Boolean

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertBadLyrics(entry: BadLyricsEntity)

  @Query("DELETE FROM bad_lyrics")
  suspend fun clearAllBadLyrics()

  @Query("SELECT songId FROM custom_lyrics WHERE TRIM(lrcText) != ''")
  fun observeCustomLyricsSongIds(): Flow<List<String>>

  @Query("SELECT * FROM custom_lyrics ORDER BY updatedAt DESC")
  fun observeAllCustomLyrics(): Flow<List<CustomLyricsEntity>>

  @Query("SELECT * FROM custom_lyrics WHERE songId = :songId LIMIT 1")
  fun observeCustomLyrics(songId: String): Flow<CustomLyricsEntity?>

  @Query("SELECT * FROM custom_lyrics WHERE songId = :songId LIMIT 1")
  suspend fun getCustomLyrics(songId: String): CustomLyricsEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertCustomLyrics(entity: CustomLyricsEntity)

  @Query("DELETE FROM custom_lyrics WHERE songId = :songId")
  suspend fun deleteCustomLyrics(songId: String)

  @Query("SELECT * FROM custom_lyrics_drafts WHERE songId = :songId LIMIT 1")
  suspend fun getCustomLyricsDraft(songId: String): CustomLyricsDraftEntity?

  @Query("SELECT COUNT(*) FROM custom_lyrics_drafts")
  fun observeCustomLyricsDraftCount(): Flow<Int>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertCustomLyricsDraft(draft: CustomLyricsDraftEntity)

  @Query("DELETE FROM custom_lyrics_drafts WHERE songId = :songId")
  suspend fun deleteCustomLyricsDraft(songId: String)

  @Query("SELECT * FROM play_logs ORDER BY playedAt DESC")
  fun observePlayLogs(): Flow<List<PlayLogEntity>>

  @Query("SELECT * FROM play_logs ORDER BY playedAt DESC")
  suspend fun getAllPlayLogsOnce(): List<PlayLogEntity>

  @Query("SELECT * FROM play_history ORDER BY playedAt DESC")
  suspend fun getPlayHistoryOnce(): List<PlayHistoryEntity>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertPlayLog(entry: PlayLogEntity): Long

  @Query("UPDATE play_logs SET listenedDurationMs = :listenedDurationMs WHERE id = :playLogId")
  suspend fun updatePlayLogDuration(playLogId: Long, listenedDurationMs: Long)

  @Query(
    """
    UPDATE play_logs
    SET listenedDurationMs = :listenedDurationMs,
        completedPercent = :completedPercent,
        completed = :completed,
        skipped = :skipped,
        replayed = :replayed
    WHERE id = :playLogId
    """
  )
  suspend fun updatePlayLogOutcome(
    playLogId: Long,
    listenedDurationMs: Long,
    completedPercent: Float,
    completed: Boolean,
    skipped: Boolean,
    replayed: Boolean
  )

  @Query(
    """
    UPDATE play_history
    SET completedPercent = :completedPercent,
        completed = :completed,
        skipped = :skipped,
        replayed = :replayed
    WHERE songId = :songId
    """
  )
  suspend fun updatePlayHistoryOutcome(
    songId: String,
    completedPercent: Float,
    completed: Boolean,
    skipped: Boolean,
    replayed: Boolean
  )

  @Query("SELECT COUNT(*) FROM play_logs WHERE songId = :songId AND playedAt >= :sinceMs")
  suspend fun countPlaysForSongSince(songId: String, sinceMs: Long): Int

  @Query("UPDATE play_logs SET artworkUrl = :artworkUrl WHERE songId = :songId")
  suspend fun updatePlayLogArtwork(songId: String, artworkUrl: String)

  @Query("DELETE FROM play_logs WHERE songId = :songId")
  suspend fun deletePlayLogsBySongId(songId: String)

  @Query("DELETE FROM play_logs")
  suspend fun clearPlayLogs()

  @Query("SELECT * FROM song_metadata ORDER BY updatedAt DESC")
  fun observeSongMetadata(): Flow<List<SongMetadataEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertSongMetadata(entry: SongMetadataEntity)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertSongMetadataList(entries: List<SongMetadataEntity>)

  @Query("DELETE FROM song_metadata WHERE id LIKE 'itunes_%' OR LOWER(source) LIKE '%itunes%' OR LOWER(streamUrl) LIKE '%itunes.apple.com%'")
  suspend fun purgeItunesMetadata()

  @Query("SELECT * FROM song_feedback")
  fun observeSongFeedback(): Flow<List<SongFeedbackEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertSongFeedback(entry: SongFeedbackEntity)

  @Query("DELETE FROM song_feedback WHERE songId = :songId")
  suspend fun deleteSongFeedback(songId: String)

  @Query("SELECT * FROM discover_weekly ORDER BY position ASC")
  fun observeDiscoverWeekly(): Flow<List<DiscoverWeeklyEntity>>

  @Query("DELETE FROM discover_weekly")
  suspend fun clearDiscoverWeekly()

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertDiscoverWeekly(entries: List<DiscoverWeeklyEntity>)

  @Transaction
  suspend fun replaceDiscoverWeekly(entries: List<DiscoverWeeklyEntity>) {
    clearDiscoverWeekly()
    if (entries.isNotEmpty()) {
      insertDiscoverWeekly(entries)
    }
  }
}

val AURIX_MIGRATION_1_2 = object : Migration(1, 2) {
  override fun migrate(db: SupportSQLiteDatabase) {
    db.execSQL(
      """
      CREATE TABLE IF NOT EXISTS `local_songs` (
        `id` TEXT NOT NULL,
        `title` TEXT NOT NULL,
        `artist` TEXT NOT NULL,
        `artworkUrl` TEXT NOT NULL,
        `durationSeconds` INTEGER NOT NULL,
        `streamUrl` TEXT NOT NULL,
        `source` TEXT NOT NULL,
        `addedAt` INTEGER NOT NULL,
        PRIMARY KEY(`id`)
      )
      """.trimIndent()
    )
  }
}

val AURIX_MIGRATION_2_3 = object : Migration(2, 3) {
  override fun migrate(db: SupportSQLiteDatabase) {
    db.execSQL(
      """
      CREATE TABLE IF NOT EXISTS `lyric_offsets` (
        `songId` TEXT NOT NULL,
        `offsetMs` INTEGER NOT NULL,
        PRIMARY KEY(`songId`)
      )
      """.trimIndent()
    )
  }
}

val AURIX_MIGRATION_3_4 = object : Migration(3, 4) {
  override fun migrate(db: SupportSQLiteDatabase) {
    db.execSQL(
      """
      CREATE TABLE IF NOT EXISTS `lyrics_cache` (
        `songId` TEXT NOT NULL,
        `songKey` TEXT NOT NULL,
        `hasLyrics` INTEGER NOT NULL,
        `rawLrc` TEXT,
        `plainLyrics` TEXT,
        `matchedTitle` TEXT,
        `matchedArtist` TEXT,
        `isReportedBad` INTEGER NOT NULL DEFAULT 0,
        `updatedAt` INTEGER NOT NULL,
        PRIMARY KEY(`songId`)
      )
      """.trimIndent()
    )
    db.execSQL(
      """
      CREATE TABLE IF NOT EXISTS `bad_lyrics` (
        `songKey` TEXT NOT NULL,
        `songId` TEXT NOT NULL,
        `title` TEXT NOT NULL,
        `artist` TEXT NOT NULL,
        `reportedAt` INTEGER NOT NULL,
        PRIMARY KEY(`songKey`)
      )
      """.trimIndent()
    )
  }
}

val AURIX_MIGRATION_4_5 = object : Migration(4, 5) {
  override fun migrate(db: SupportSQLiteDatabase) {
    db.execSQL("DELETE FROM bad_lyrics")
    db.execSQL("DELETE FROM lyrics_cache WHERE isReportedBad = 1")
  }
}

val AURIX_MIGRATION_5_6 = object : Migration(5, 6) {
  override fun migrate(db: SupportSQLiteDatabase) {
    db.execSQL(
      """
      CREATE TABLE IF NOT EXISTS `play_logs` (
        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
        `songId` TEXT NOT NULL,
        `title` TEXT NOT NULL,
        `artist` TEXT NOT NULL,
        `artworkUrl` TEXT NOT NULL,
        `durationSeconds` INTEGER NOT NULL,
        `streamUrl` TEXT NOT NULL,
        `source` TEXT NOT NULL,
        `genre` TEXT NOT NULL,
        `bpm` INTEGER NOT NULL,
        `mood` TEXT NOT NULL,
        `playedAt` INTEGER NOT NULL,
        `listenedDurationMs` INTEGER NOT NULL
      )
      """.trimIndent()
    )
    db.execSQL(
      """
      CREATE TABLE IF NOT EXISTS `discover_weekly` (
        `weekKey` TEXT NOT NULL,
        `position` INTEGER NOT NULL,
        `songId` TEXT NOT NULL,
        `title` TEXT NOT NULL,
        `artist` TEXT NOT NULL,
        `artworkUrl` TEXT NOT NULL,
        `durationSeconds` INTEGER NOT NULL,
        `streamUrl` TEXT NOT NULL,
        `source` TEXT NOT NULL,
        `genre` TEXT NOT NULL,
        `bpm` INTEGER NOT NULL,
        `mood` TEXT NOT NULL,
        `isDiscovery` INTEGER NOT NULL,
        `generatedAt` INTEGER NOT NULL,
        PRIMARY KEY(`weekKey`, `position`)
      )
      """.trimIndent()
    )
  }
}

private fun hasColumn(db: SupportSQLiteDatabase, table: String, column: String): Boolean {
  return runCatching {
    db.query("PRAGMA table_info(`$table`)").use { cursor ->
      val nameIndex = cursor.getColumnIndex("name")
      if (nameIndex < 0) return false
      while (cursor.moveToNext()) {
        if (cursor.getString(nameIndex).equals(column, ignoreCase = true)) {
          return true
        }
      }
      false
    }
  }.getOrDefault(false)
}

private fun execSafeAddColumn(
  db: SupportSQLiteDatabase,
  table: String,
  column: String,
  definition: String
) {
  if (!hasColumn(db, table, column)) {
    runCatching {
      db.execSQL("ALTER TABLE `$table` ADD COLUMN `$column` $definition")
    }
  }
}

val AURIX_MIGRATION_6_7 = object : Migration(6, 7) {
  override fun migrate(db: SupportSQLiteDatabase) {
    execSafeAddColumn(db, "local_songs", "album", "TEXT NOT NULL DEFAULT 'Unknown Album'")
    execSafeAddColumn(db, "local_songs", "trackNumber", "INTEGER NOT NULL DEFAULT 0")
    execSafeAddColumn(db, "local_songs", "year", "TEXT NOT NULL DEFAULT ''")
    db.execSQL(
      """
      CREATE TABLE IF NOT EXISTS `album_covers` (
        `albumId` TEXT NOT NULL,
        `coverPath` TEXT NOT NULL,
        `mode` TEXT NOT NULL,
        `sourceUris` TEXT NOT NULL,
        `updatedAt` INTEGER NOT NULL,
        PRIMARY KEY(`albumId`)
      )
      """.trimIndent()
    )
  }
}

val AURIX_MIGRATION_7_8 = object : Migration(7, 8) {
  override fun migrate(db: SupportSQLiteDatabase) {
    db.execSQL(
      """
      CREATE TABLE IF NOT EXISTS `custom_albums` (
        `albumId` TEXT NOT NULL,
        `title` TEXT NOT NULL,
        `artist` TEXT NOT NULL,
        `year` TEXT NOT NULL DEFAULT '',
        `createdAt` INTEGER NOT NULL,
        PRIMARY KEY(`albumId`)
      )
      """.trimIndent()
    )
    db.execSQL(
      """
      CREATE TABLE IF NOT EXISTS `album_tracks` (
        `albumId` TEXT NOT NULL,
        `songId` TEXT NOT NULL,
        `title` TEXT NOT NULL,
        `artist` TEXT NOT NULL,
        `artworkUrl` TEXT NOT NULL,
        `durationSeconds` INTEGER NOT NULL,
        `streamUrl` TEXT NOT NULL,
        `source` TEXT NOT NULL,
        `genre` TEXT NOT NULL DEFAULT '',
        `bpm` INTEGER NOT NULL DEFAULT 0,
        `mood` TEXT NOT NULL DEFAULT '',
        `albumName` TEXT NOT NULL DEFAULT '',
        `trackNumber` INTEGER NOT NULL DEFAULT 0,
        `year` TEXT NOT NULL DEFAULT '',
        `addedAt` INTEGER NOT NULL,
        PRIMARY KEY(`albumId`, `songId`)
      )
      """.trimIndent()
    )
    db.execSQL(
      """
      CREATE TABLE IF NOT EXISTS `deleted_albums` (
        `albumId` TEXT NOT NULL,
        `deletedAt` INTEGER NOT NULL,
        PRIMARY KEY(`albumId`)
      )
      """.trimIndent()
    )
  }
}

val AURIX_MIGRATION_8_9 = object : Migration(8, 9) {
  override fun migrate(db: SupportSQLiteDatabase) {
    execSafeAddColumn(db, "play_history", "completedPercent", "REAL NOT NULL DEFAULT 0.0")
    execSafeAddColumn(db, "play_history", "completed", "INTEGER NOT NULL DEFAULT 0")
    execSafeAddColumn(db, "play_history", "skipped", "INTEGER NOT NULL DEFAULT 0")
    execSafeAddColumn(db, "play_history", "replayed", "INTEGER NOT NULL DEFAULT 0")

    execSafeAddColumn(db, "play_logs", "completedPercent", "REAL NOT NULL DEFAULT 0.0")
    execSafeAddColumn(db, "play_logs", "completed", "INTEGER NOT NULL DEFAULT 0")
    execSafeAddColumn(db, "play_logs", "skipped", "INTEGER NOT NULL DEFAULT 0")
    execSafeAddColumn(db, "play_logs", "replayed", "INTEGER NOT NULL DEFAULT 0")
    execSafeAddColumn(db, "play_logs", "energy", "REAL NOT NULL DEFAULT 0.5")
    execSafeAddColumn(db, "play_logs", "sad", "REAL NOT NULL DEFAULT 0.0")
    execSafeAddColumn(db, "play_logs", "chill", "REAL NOT NULL DEFAULT 0.0")
    execSafeAddColumn(db, "play_logs", "happy", "REAL NOT NULL DEFAULT 0.0")
    execSafeAddColumn(db, "play_logs", "calm", "REAL NOT NULL DEFAULT 0.0")
    execSafeAddColumn(db, "play_logs", "energetic", "REAL NOT NULL DEFAULT 0.0")
    execSafeAddColumn(db, "play_logs", "romantic", "REAL NOT NULL DEFAULT 0.0")
    execSafeAddColumn(db, "play_logs", "hasVocals", "INTEGER NOT NULL DEFAULT 1")
    execSafeAddColumn(db, "play_logs", "language", "TEXT NOT NULL DEFAULT 'en'")

    db.execSQL(
      """
      CREATE TABLE IF NOT EXISTS `song_metadata` (
        `id` TEXT NOT NULL,
        `title` TEXT NOT NULL,
        `artist` TEXT NOT NULL,
        `artworkUrl` TEXT NOT NULL,
        `durationSeconds` INTEGER NOT NULL,
        `streamUrl` TEXT NOT NULL,
        `source` TEXT NOT NULL,
        `genre` TEXT NOT NULL,
        `bpm` INTEGER NOT NULL,
        `mood` TEXT NOT NULL,
        `energy` REAL NOT NULL,
        `sad` REAL NOT NULL,
        `chill` REAL NOT NULL,
        `happy` REAL NOT NULL,
        `calm` REAL NOT NULL,
        `energetic` REAL NOT NULL,
        `romantic` REAL NOT NULL,
        `hasVocals` INTEGER NOT NULL,
        `language` TEXT NOT NULL,
        `playCount` INTEGER NOT NULL DEFAULT 0,
        `updatedAt` INTEGER NOT NULL,
        PRIMARY KEY(`id`)
      )
      """.trimIndent()
    )

    db.execSQL(
      """
      CREATE TABLE IF NOT EXISTS `song_feedback` (
        `songId` TEXT NOT NULL,
        `liked` INTEGER NOT NULL,
        `updatedAt` INTEGER NOT NULL,
        PRIMARY KEY(`songId`)
      )
      """.trimIndent()
    )
  }
}

val AURIX_MIGRATION_9_10 = object : Migration(9, 10) {
  override fun migrate(db: SupportSQLiteDatabase) {
    db.execSQL(
      """
      CREATE TABLE IF NOT EXISTS `custom_lyrics` (
        `songId` TEXT NOT NULL,
        `lrcText` TEXT NOT NULL,
        `source` TEXT NOT NULL DEFAULT 'custom',
        `title` TEXT NOT NULL DEFAULT '',
        `artist` TEXT NOT NULL DEFAULT '',
        `updatedAt` INTEGER NOT NULL,
        PRIMARY KEY(`songId`)
      )
      """.trimIndent()
    )
    db.execSQL(
      """
      CREATE TABLE IF NOT EXISTS `custom_lyrics_drafts` (
        `songId` TEXT NOT NULL,
        `draftJson` TEXT NOT NULL,
        `step` INTEGER NOT NULL DEFAULT 0,
        `globalOffsetMs` INTEGER NOT NULL DEFAULT 0,
        `updatedAt` INTEGER NOT NULL,
        PRIMARY KEY(`songId`)
      )
      """.trimIndent()
    )
  }
}

@Database(
  entities = [
    FavoriteSongEntity::class,
    PlayHistoryEntity::class,
    LocalSongEntity::class,
    LyricOffsetEntity::class,
    LyricsCacheEntity::class,
    BadLyricsEntity::class,
    CustomLyricsEntity::class,
    CustomLyricsDraftEntity::class,
    PlayLogEntity::class,
    SongMetadataEntity::class,
    SongFeedbackEntity::class,
    DiscoverWeeklyEntity::class,
    AlbumCoverEntity::class,
    CustomAlbumEntity::class,
    AlbumTrackEntity::class,
    DeletedAlbumEntity::class
  ],
  version = 10,
  exportSchema = false
)
abstract class AurixDatabase : RoomDatabase() {
  abstract fun aurixDao(): AurixDao

  companion object {
    @Volatile
    private var INSTANCE: AurixDatabase? = null

    fun getInstance(context: android.content.Context): AurixDatabase {
      return INSTANCE ?: synchronized(this) {
        INSTANCE ?: androidx.room.Room.databaseBuilder(
          context.applicationContext,
          AurixDatabase::class.java,
          "aurix_db"
        )
          .addMigrations(
            AURIX_MIGRATION_1_2,
            AURIX_MIGRATION_2_3,
            AURIX_MIGRATION_3_4,
            AURIX_MIGRATION_4_5,
            AURIX_MIGRATION_5_6,
            AURIX_MIGRATION_6_7,
            AURIX_MIGRATION_7_8,
            AURIX_MIGRATION_8_9,
            AURIX_MIGRATION_9_10
          )
          .fallbackToDestructiveMigration(true)
          .fallbackToDestructiveMigrationOnDowngrade(true)
          .build()
          .also { INSTANCE = it }
      }
    }
  }
}
