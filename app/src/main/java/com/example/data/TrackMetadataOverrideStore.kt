package com.example.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.example.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

/**
 * Local non-destructive metadata override for any track (especially local phone files
 * with messy filenames or missing covers). Stored cleanly in internal app storage so
 * zero broad storage permissions are needed.
 */
data class TrackMetadataOverride(
  val songId: String,
  val title: String? = null,
  val artist: String? = null,
  val album: String? = null,
  val year: String? = null,
  val genre: String? = null,
  val artworkPath: String? = null,
  val updatedAtMs: Long = System.currentTimeMillis()
) {
  fun applyTo(song: Song): Song {
    val nextTitle = title?.takeIf { it.isNotBlank() } ?: song.title
    val nextArtist = artist?.takeIf { it.isNotBlank() } ?: song.artist
    val nextYear = year?.takeIf { it.isNotBlank() } ?: song.year
    val nextGenre = genre?.takeIf { it.isNotBlank() } ?: song.genre
    val nextArtwork = artworkPath?.takeIf { it.isNotBlank() } ?: song.artworkUrl
    return song.copy(
      title = nextTitle,
      artist = nextArtist,
      year = nextYear,
      genre = nextGenre,
      artworkUrl = nextArtwork
    )
  }
}

object TrackMetadataOverrideStore {
  private const val OVERRIDES_FILE = "track_metadata_overrides.json"
  private const val COVERS_DIR = "track_custom_covers"

  fun loadAllSync(context: Context): Map<String, TrackMetadataOverride> {
    val file = File(context.filesDir, OVERRIDES_FILE)
    if (!file.exists()) return emptyMap()
    return runCatching {
      val root = JSONObject(file.readText())
      val result = LinkedHashMap<String, TrackMetadataOverride>()
      val keys = root.keys()
      while (keys.hasNext()) {
        val songId = keys.next()
        val obj = root.optJSONObject(songId) ?: continue
        result[songId] = TrackMetadataOverride(
          songId = songId,
          title = obj.optString("title").takeIf { it.isNotBlank() },
          artist = obj.optString("artist").takeIf { it.isNotBlank() },
          album = obj.optString("album").takeIf { it.isNotBlank() },
          year = obj.optString("year").takeIf { it.isNotBlank() },
          genre = obj.optString("genre").takeIf { it.isNotBlank() },
          artworkPath = obj.optString("artworkPath").takeIf { it.isNotBlank() },
          updatedAtMs = obj.optLong("updatedAtMs", 0L)
        )
      }
      result
    }.getOrDefault(emptyMap())
  }

  suspend fun saveAll(context: Context, overrides: Map<String, TrackMetadataOverride>) = withContext(Dispatchers.IO) {
    runCatching {
      val root = JSONObject()
      overrides.forEach { (id, ov) ->
        val obj = JSONObject().apply {
          ov.title?.let { put("title", it) }
          ov.artist?.let { put("artist", it) }
          ov.album?.let { put("album", it) }
          ov.year?.let { put("year", it) }
          ov.genre?.let { put("genre", it) }
          ov.artworkPath?.let { put("artworkPath", it) }
          put("updatedAtMs", ov.updatedAtMs)
        }
        root.put(id, obj)
      }
      val file = File(context.filesDir, OVERRIDES_FILE)
      val tmp = File(context.filesDir, "$OVERRIDES_FILE.tmp")
      tmp.writeText(root.toString())
      if (!tmp.renameTo(file)) {
        file.writeText(root.toString())
        tmp.delete()
      }
    }
  }

  /**
   * Copies a Photo Picker image URI safely into internal app storage as a crisp 720px JPEG
   * so custom covers remain available after app restarts without needing gallery permissions.
   */
  suspend fun importTrackCoverFromUri(
    context: Context,
    songId: String,
    sourceUri: Uri
  ): String? = withContext(Dispatchers.IO) {
    runCatching {
      val dir = File(context.filesDir, COVERS_DIR).apply { if (!exists()) mkdirs() }
      val safeId = songId.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(48)
      val destFile = File(dir, "cover_${safeId}_${System.currentTimeMillis()}.jpg")

      val rawBitmap = context.contentResolver.openInputStream(sourceUri)?.use { input ->
        BitmapFactory.decodeStream(input)
      } ?: return@runCatching null

      val maxSide = 720
      val scaled = if (rawBitmap.width > maxSide || rawBitmap.height > maxSide) {
        val ratio = maxSide.toFloat() / maxOf(rawBitmap.width, rawBitmap.height).toFloat()
        Bitmap.createScaledBitmap(
          rawBitmap,
          (rawBitmap.width * ratio).toInt().coerceAtLeast(1),
          (rawBitmap.height * ratio).toInt().coerceAtLeast(1),
          true
        )
      } else {
        rawBitmap
      }

      FileOutputStream(destFile).use { fos ->
        scaled.compress(Bitmap.CompressFormat.JPEG, 92, fos)
        fos.flush()
      }
      if (scaled !== rawBitmap) rawBitmap.recycle()
      scaled.recycle()
      Uri.fromFile(destFile).toString()
    }.getOrNull()
  }

  fun applyOverridesToSongs(
    songs: List<Song>,
    overrides: Map<String, TrackMetadataOverride>
  ): List<Song> {
    if (overrides.isEmpty() || songs.isEmpty()) return songs
    return songs.map { song -> overrides[song.id]?.applyTo(song) ?: song }
  }
}
