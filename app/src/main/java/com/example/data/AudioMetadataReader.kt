package com.example.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import com.example.model.AlbumInfo
import com.example.model.CustomCoverMode
import com.example.model.Song
import com.example.model.computeAlbumId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

data class ExtractedAudioMetadata(
  val title: String,
  val artist: String,
  val album: String,
  val trackNumber: Int,
  val year: String,
  val durationSeconds: Long,
  val embeddedArtworkUri: String
)

object AudioMetadataReader {
  private val YEAR_REGEX = Regex("""(19|20)\d{2}""")

  fun stripFileExtension(fileName: String?): String {
    val trimmed = fileName?.trim().orEmpty()
    if (trimmed.isBlank()) return ""
    val dotIdx = trimmed.lastIndexOf('.')
    return if (dotIdx > 0) trimmed.substring(0, dotIdx).trim() else trimmed
  }

  fun parseTrackNumber(raw: String?): Int {
    val clean = raw?.trim().orEmpty()
    if (clean.isEmpty()) return 0
    val firstSegment = clean.substringBefore('/').trim()
    val digits = firstSegment.takeWhile { it.isDigit() }
    return digits.toIntOrNull()?.takeIf { it > 0 } ?: 0
  }

  fun parseReleaseYear(raw: String?): String {
    val clean = raw?.trim().orEmpty()
    if (clean.isEmpty()) return ""
    return YEAR_REGEX.find(clean)?.value.orEmpty()
  }

  fun resolveFallbackTitle(context: Context, uri: Uri): String {
    var displayName: String? = null
    if (uri.scheme == "file") {
      displayName = uri.path?.let { File(it).name }
    } else {
      runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
          if (cursor.moveToFirst()) {
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0) {
              displayName = cursor.getString(idx)
            }
          }
        }
      }
    }
    val fromDisplay = stripFileExtension(displayName)
    if (fromDisplay.isNotBlank()) return fromDisplay

    val fromSegment = stripFileExtension(uri.lastPathSegment?.substringAfterLast('/'))
      .takeIf { it.isNotBlank() && !it.startsWith("audio:") }
    return fromSegment ?: "Local Audio Track"
  }

  suspend fun extractFromUri(
    context: Context,
    uri: Uri,
    songId: String = uri.toString()
  ): ExtractedAudioMetadata = withContext(Dispatchers.IO) {
    val fallbackTitle = resolveFallbackTitle(context, uri)
    var title = fallbackTitle
    var artist = "Unknown Artist"
    var album = "Unknown Album"
    var trackNumber = 0
    var year = ""
    var durationSec = 0L
    var embeddedArtworkUri = ""

    val retriever = MediaMetadataRetriever()
    try {
      retriever.setDataSource(context, uri)
      val metaTitle = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.trim()
      val metaArtist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)?.trim()
      val metaAlbum = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)?.trim()
      val metaTrack = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)
      val metaYear = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR)
        ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE)
      val metaDurationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()

      if (!metaTitle.isNullOrBlank()) title = metaTitle
      if (!metaArtist.isNullOrBlank()) artist = metaArtist
      if (!metaAlbum.isNullOrBlank()) album = metaAlbum
      trackNumber = parseTrackNumber(metaTrack)
      year = parseReleaseYear(metaYear)
      if (metaDurationMs != null && metaDurationMs > 0L) {
        durationSec = (metaDurationMs / 1000L).coerceAtLeast(1L)
      }

      val pictureBytes = runCatching { retriever.embeddedPicture }.getOrNull()
      if (pictureBytes != null && pictureBytes.isNotEmpty()) {
        embeddedArtworkUri = saveEmbeddedPicture(context, songId, pictureBytes).orEmpty()
      }
    } catch (_: Exception) {
    } finally {
      runCatching { retriever.release() }
    }

    ExtractedAudioMetadata(
      title = title,
      artist = artist,
      album = album,
      trackNumber = trackNumber,
      year = year,
      durationSeconds = durationSec,
      embeddedArtworkUri = embeddedArtworkUri
    )
  }

  private fun saveEmbeddedPicture(
    context: Context,
    songId: String,
    bytes: ByteArray
  ): String? {
    return runCatching {
      val dir = File(context.filesDir, "embedded_covers").apply { if (!exists()) mkdirs() }
      val safeHash = songId.hashCode().toUInt().toString(16)
      val outFile = File(dir, "embedded_${safeHash}.jpg")

      val decoded = com.example.ui.image.AurixImagePipeline.decodeHighDensityArtworkBytes(
        context = context,
        bytes = bytes,
        guardGpuTextureLimit = true
      )
      if (decoded != null) {
        FileOutputStream(outFile).use { fos ->
          decoded.compress(Bitmap.CompressFormat.JPEG, 96, fos)
          fos.flush()
        }
        decoded.recycle()
      } else {
        FileOutputStream(outFile).use { fos ->
          fos.write(bytes)
          fos.flush()
        }
      }
      Uri.fromFile(outFile).toString()
    }.getOrNull()
  }

  fun deriveAlbumHeaderFromTracks(
    tracks: List<Song>,
    customCovers: Map<String, AlbumCoverEntity> = emptyMap(),
    fallbackTitle: String = "Unknown Album",
    fallbackArtist: String = "Unknown Artist"
  ): AlbumInfo {
    val first = tracks.firstOrNull()
    val albumTitle = first?.album?.trim()?.takeIf { it.isNotEmpty() } ?: fallbackTitle
    val artistName = first?.artist?.trim()?.takeIf { it.isNotEmpty() } ?: fallbackArtist
    val year = first?.year?.trim().orEmpty()
    val albumId = computeAlbumId(albumTitle, artistName)
    val autoCover = first?.artworkUrl?.trim()?.takeIf { it.isNotEmpty() }
      ?: tracks.firstOrNull { it.artworkUrl.isNotBlank() }?.artworkUrl
    val custom = customCovers[albumId]
    val effectiveCover = custom?.coverPath?.takeIf { it.isNotBlank() } ?: autoCover
    val customMode = custom?.let { CustomCoverMode.fromId(it.mode) }
    val customSources = custom?.sourceUris
      ?.split("\n")
      ?.map { it.trim() }
      ?.filter { it.isNotEmpty() }
      .orEmpty()

    return AlbumInfo(
      albumId = albumId,
      title = albumTitle,
      artist = artistName,
      year = year,
      coverUrl = effectiveCover,
      automaticCoverUrl = autoCover,
      customCoverMode = customMode,
      customSourceUris = customSources,
      tracks = tracks
    )
  }
}
