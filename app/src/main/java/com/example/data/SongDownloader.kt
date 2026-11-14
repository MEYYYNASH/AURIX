package com.example.data

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.media.MediaMetadataRetriever
import com.example.model.AudioFormatOption
import com.example.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

data class DownloadProgressInfo(
  val fraction: Float,
  val downloadedBytes: Long,
  val totalBytes: Long
)

data class SavedAudioResult(
  val uri: Uri,
  val durationSeconds: Long
)

object SongDownloader {
  private val client = OkHttpClient.Builder()
    .connectTimeout(20, TimeUnit.SECONDS)
    .readTimeout(120, TimeUnit.SECONDS)
    .followRedirects(true)
    .followSslRedirects(true)
    .build()

  suspend fun saveCustomCoverImage(
    context: Context,
    songId: String,
    imageUri: Uri
  ): Result<String> = withContext(Dispatchers.IO) {
    try {
      val coversDir = File(context.filesDir, "covers").apply { if (!exists()) mkdirs() }
      val safeHash = songId.hashCode().toUInt().toString(16)
      val outFile = File(coversDir, "cover_${safeHash}_${System.currentTimeMillis()}.jpg")

      val rawBytes = context.contentResolver.openInputStream(imageUri)?.use { it.readBytes() }
        ?: return@withContext Result.failure(Exception("Unable to read selected image"))

      val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
      BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, bounds)
      val maxDim = maxOf(bounds.outWidth, bounds.outHeight)
      var sampleSize = 1
      while (maxDim / sampleSize > 1024) {
        sampleSize *= 2
      }

      val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
      val bitmap = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, decodeOpts)
      if (bitmap != null) {
        FileOutputStream(outFile).use { fos ->
          bitmap.compress(Bitmap.CompressFormat.JPEG, 92, fos)
          fos.flush()
        }
        bitmap.recycle()
      } else {
        FileOutputStream(outFile).use { fos ->
          fos.write(rawBytes)
          fos.flush()
        }
      }

      Result.success(Uri.fromFile(outFile).toString())
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  suspend fun cacheRemoteArtwork(
    context: Context,
    songId: String,
    imageUrl: String
  ): String? = withContext(Dispatchers.IO) {
    if (imageUrl.isBlank() || (!imageUrl.startsWith("http://") && !imageUrl.startsWith("https://"))) {
      return@withContext null
    }
    runCatching {
      val coversDir = File(context.filesDir, "covers").apply { if (!exists()) mkdirs() }
      val safeHash = songId.hashCode().toUInt().toString(16)
      val outFile = File(coversDir, "thumb_${safeHash}_${System.currentTimeMillis()}.jpg")
      val request = Request.Builder()
        .url(imageUrl)
        .header("User-Agent", "AURIX-Android")
        .build()
      client.newCall(request).execute().use { response ->
        if (!response.isSuccessful) return@use null
        val bytes = response.body?.bytes() ?: return@use null
        if (bytes.isEmpty()) return@use null
        FileOutputStream(outFile).use { fos ->
          fos.write(bytes)
          fos.flush()
        }
        Uri.fromFile(outFile).toString()
      }
    }.getOrNull()
  }

  private fun measureDurationSeconds(file: File, fallbackSeconds: Long): Long {
    val retriever = MediaMetadataRetriever()
    return try {
      retriever.setDataSource(file.absolutePath)
      val ms = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
      if (ms > 0L) (ms / 1000L).coerceAtLeast(1L) else fallbackSeconds
    } catch (_: Exception) {
      fallbackSeconds
    } finally {
      runCatching { retriever.release() }
    }
  }

  private fun downloadWithChunks(
    streamUrl: String,
    userAgent: String,
    totalBytes: Long,
    appFile: File,
    onProgress: (DownloadProgressInfo) -> Unit
  ): Boolean {
    if (totalBytes <= 0L) return false
    val chunkSize = 1_048_576L // 1 MB chunks
    var offset = 0L
    FileOutputStream(appFile).use { output ->
      while (offset < totalBytes) {
        val end = minOf(offset + chunkSize - 1L, totalBytes - 1L)
        val req = Request.Builder()
          .url(streamUrl)
          .header("User-Agent", userAgent)
          .header("Accept", "*/*")
          .header("Range", "bytes=$offset-$end")
          .build()
        client.newCall(req).execute().use { resp ->
          if (!resp.isSuccessful && resp.code != 206) {
            return false
          }
          val body = resp.body ?: return false
          body.byteStream().use { input ->
            val buf = ByteArray(16 * 1024)
            while (true) {
              val read = input.read(buf)
              if (read == -1) break
              output.write(buf, 0, read)
              offset += read
              val progress = (offset.toFloat() / totalBytes.toFloat()).coerceIn(0.02f, 0.98f)
              onProgress(DownloadProgressInfo(progress, offset, totalBytes))
            }
          }
        }
      }
      output.flush()
    }
    return appFile.exists() && appFile.length() > 0L
  }

  suspend fun downloadFormatToApp(
    context: Context,
    title: String,
    artist: String,
    format: AudioFormatOption,
    onProgress: (DownloadProgressInfo) -> Unit
  ): Result<SavedAudioResult> = withContext(Dispatchers.IO) {
    val streamUrl = format.streamUrl.ifBlank {
      return@withContext Result.failure(IllegalArgumentException("No downloadable audio stream URL available"))
    }

    val safeName = "$artist - $title"
      .replace(Regex("""[\\/:*?"<>|]"""), "_")
      .trim()
      .take(80)
      .ifBlank { "aurix_audio_${System.currentTimeMillis()}" }
    val ext = format.extension.ifBlank { "m4a" }
    val fileName = "${safeName}_${System.currentTimeMillis()}.$ext"

    val downloadsDir = File(context.filesDir, "downloads").apply { if (!exists()) mkdirs() }
    val appFile = File(downloadsDir, fileName)
    val userAgent = format.requiredUserAgent?.takeIf { it.isNotBlank() } ?: "AURIX-Android"

    try {
      onProgress(DownloadProgressInfo(0.02f, 0L, format.contentLengthBytes))
      val requestBuilder = Request.Builder()
        .url(streamUrl)
        .header("User-Agent", userAgent)
        .header("Accept", "*/*")
      if (streamUrl.contains("googlevideo.com", ignoreCase = true)) {
        requestBuilder.header("Referer", "https://www.youtube.com/")
      }
      val request = requestBuilder.build()

      var directSucceeded = false
      var lastHttpCode = 0
      client.newCall(request).execute().use { response ->
        lastHttpCode = response.code
        if (response.isSuccessful) {
          val body = response.body ?: return@withContext Result.failure(Exception("Empty audio stream"))
          val totalBytes = body.contentLength().let { if (it > 0L) it else format.contentLengthBytes }
          var downloadedBytes = 0L

          FileOutputStream(appFile).use { output ->
            body.byteStream().use { input ->
              val buffer = ByteArray(16 * 1024)
              while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                output.write(buffer, 0, read)
                downloadedBytes += read
                val progress = if (totalBytes > 0L) {
                  (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0.02f, 0.98f)
                } else {
                  0.5f
                }
                onProgress(DownloadProgressInfo(progress, downloadedBytes, totalBytes))
              }
              output.flush()
            }
          }
          directSucceeded = appFile.exists() && appFile.length() > 0L
        }
      }

      if (!directSucceeded) {
        if (appFile.exists()) appFile.delete()
        val chunkedOk = if (format.contentLengthBytes > 0L) {
          runCatching {
            downloadWithChunks(streamUrl, userAgent, format.contentLengthBytes, appFile, onProgress)
          }.getOrDefault(false)
        } else false

        if (!chunkedOk) {
          if (appFile.exists()) appFile.delete()
          return@withContext Result.failure(Exception("Download failed (HTTP $lastHttpCode)"))
        }
      }

      if (!appFile.exists() || appFile.length() == 0L) {
        appFile.delete()
        return@withContext Result.failure(Exception("Downloaded file was empty"))
      }

      val actualDurationSec = measureDurationSeconds(appFile, format.durationSeconds)

      // Also export a copy to shared Music/AURIX for user convenience when format is MediaStore-friendly
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && (ext == "m4a" || ext == "mp3")) {
        runCatching {
          val resolver = context.contentResolver
          val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, "$safeName.$ext")
            put(MediaStore.Audio.Media.MIME_TYPE, format.mimeType)
            put(MediaStore.Audio.Media.TITLE, title)
            put(MediaStore.Audio.Media.ARTIST, artist)
            put(MediaStore.Audio.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MUSIC}/AURIX")
            put(MediaStore.Audio.Media.IS_PENDING, 1)
          }
          val mediaUri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
          if (mediaUri != null) {
            resolver.openOutputStream(mediaUri)?.use { out ->
              appFile.inputStream().use { inp -> inp.copyTo(out) }
              out.flush()
            }
            values.clear()
            values.put(MediaStore.Audio.Media.IS_PENDING, 0)
            resolver.update(mediaUri, values, null, null)
          }
        }
      }

      onProgress(DownloadProgressInfo(1f, appFile.length(), appFile.length()))
      Result.success(SavedAudioResult(Uri.fromFile(appFile), actualDurationSec))
    } catch (e: Exception) {
      if (appFile.exists()) appFile.delete()
      Result.failure(e)
    }
  }

  suspend fun downloadSongToDevice(
    context: Context,
    song: Song,
    onProgress: (Float) -> Unit
  ): Result<SavedAudioResult> {
    val isM4a = song.streamUrl.contains(".m4a", ignoreCase = true)
    val format = AudioFormatOption(
      id = "direct_${song.id}",
      formatLabel = if (isM4a) "M4A" else "MP3",
      qualityLabel = if (isM4a) "256 kbps AAC" else "128 kbps MP3",
      detailLabel = song.source,
      extension = if (isM4a) "m4a" else "mp3",
      mimeType = if (isM4a) "audio/mp4" else "audio/mpeg",
      streamUrl = song.streamUrl,
      durationSeconds = song.durationSeconds
    )
    if (song.streamUrl.startsWith("content://") || song.streamUrl.startsWith("file://")) {
      return Result.failure(IllegalStateException("Track is already saved in the app"))
    }
    return downloadFormatToApp(
      context = context,
      title = song.title,
      artist = song.artist,
      format = format,
      onProgress = { info -> onProgress(info.fraction) }
    )
  }

  fun deleteAppManagedFileIfOwned(context: Context, uriString: String) {
    runCatching {
      val parsed = Uri.parse(uriString)
      if (parsed.scheme == "file") {
        val file = parsed.path?.let { File(it) }
        val appRoot = context.filesDir.canonicalPath
        if (file != null && file.exists() && file.canonicalPath.startsWith(appRoot)) {
          file.delete()
        }
      }
    }
  }
}
