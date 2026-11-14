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
  val playedAt: Long = System.currentTimeMillis()
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
  val addedAt: Long = System.currentTimeMillis()
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

  @Query("DELETE FROM local_songs WHERE id = :songId")
  suspend fun deleteLocalSong(songId: String)

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

@Database(
  entities = [
    FavoriteSongEntity::class,
    PlayHistoryEntity::class,
    LocalSongEntity::class,
    LyricOffsetEntity::class,
    LyricsCacheEntity::class,
    BadLyricsEntity::class
  ],
  version = 4,
  exportSchema = false
)
abstract class AurixDatabase : RoomDatabase() {
  abstract fun aurixDao(): AurixDao
}
