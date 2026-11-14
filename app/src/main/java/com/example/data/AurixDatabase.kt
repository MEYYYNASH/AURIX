package com.example.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
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

  @Query("SELECT * FROM play_history ORDER BY playedAt DESC LIMIT 50")
  fun getPlayHistory(): Flow<List<PlayHistoryEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertHistory(history: PlayHistoryEntity)
}

@Database(entities = [FavoriteSongEntity::class, PlayHistoryEntity::class], version = 1, exportSchema = false)
abstract class AurixDatabase : RoomDatabase() {
  abstract fun aurixDao(): AurixDao
}
