package com.example.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.example.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

object SongDownloader {
  private val client = OkHttpClient.Builder()
    .connectTimeout(20, TimeUnit.SECONDS)
    .readTimeout(120, TimeUnit.SECONDS)
    .followRedirects(true)
    .followSslRedirects(true)
    .build()

  suspend fun downloadSongToDevice(
    context: Context,
    song: Song,
    onProgress: (Float) -> Unit
  ): Result<Uri> = withContext(Dispatchers.IO) {
    val streamUrl = song.streamUrl.ifBlank {
      return@withContext Result.failure(IllegalArgumentException("No audio stream URL available"))
    }

    if (streamUrl.startsWith("content://") || streamUrl.startsWith("file://")) {
      return@withContext Result.failure(IllegalStateException("Track is already saved on device"))
    }

    val safeName = "${song.artist} - ${song.title}"
      .replace(Regex("""[\\/:*?"<>|]"""), "_")
      .trim()
      .take(80)
      .ifBlank { "aurix_track_${song.id}" }
    val fileName = "$safeName.mp3"

    try {
      onProgress(0.05f)
      val request = Request.Builder()
        .url(streamUrl)
        .header("User-Agent", "AURIX-Android")
        .build()

      client.newCall(request).execute().use { response ->
        if (!response.isSuccessful) {
          return@withContext Result.failure(Exception("Download failed (HTTP ${response.code})"))
        }
        val body = response.body ?: return@withContext Result.failure(Exception("Empty audio response"))
        val totalBytes = body.contentLength()
        var downloadedBytes = 0L

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
          val resolver = context.contentResolver
          val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Audio.Media.MIME_TYPE, "audio/mpeg")
            put(MediaStore.Audio.Media.TITLE, song.title)
            put(MediaStore.Audio.Media.ARTIST, song.artist)
            put(MediaStore.Audio.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MUSIC}/AURIX")
            put(MediaStore.Audio.Media.IS_PENDING, 1)
          }

          val uri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
            ?: return@withContext Result.failure(Exception("Unable to create MediaStore entry"))

          try {
            resolver.openOutputStream(uri)?.use { output ->
              body.byteStream().use { input ->
                val buffer = ByteArray(16 * 1024)
                while (true) {
                  val read = input.read(buffer)
                  if (read == -1) break
                  output.write(buffer, 0, read)
                  downloadedBytes += read
                  val progress = if (totalBytes > 0L) (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0.05f, 0.99f) else 0.5f
                  onProgress(progress)
                }
                output.flush()
              }
            } ?: throw Exception("Failed to open output stream")

            values.clear()
            values.put(MediaStore.Audio.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            onProgress(1f)
            return@withContext Result.success(uri)
          } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
          }
        } else {
          val dir = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: context.filesDir
          if (!dir.exists()) dir.mkdirs()
          val outFile = File(dir, fileName)
          FileOutputStream(outFile).use { output ->
            body.byteStream().use { input ->
              val buffer = ByteArray(16 * 1024)
              while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                output.write(buffer, 0, read)
                downloadedBytes += read
                val progress = if (totalBytes > 0L) (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0.05f, 0.99f) else 0.5f
                onProgress(progress)
              }
              output.flush()
            }
          }
          onProgress(1f)
          return@withContext Result.success(Uri.fromFile(outFile))
        }
      }
    } catch (e: Exception) {
      return@withContext Result.failure(e)
    }
  }
}
