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
import com.example.model.StreamCandidate
import com.example.model.StreamDownloadMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
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

private data class DownloadAttemptResult(
  val succeeded: Boolean,
  val httpCode: Int
)

object SongDownloader {
  private const val CHUNK_SIZE_BYTES = 512L * 1024L // 512 KB bounded chunks for YouTube range compliance

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

      val bitmap = com.example.ui.image.AurixImagePipeline.decodeHighDensityArtworkBytes(
        context = context,
        bytes = rawBytes,
        guardGpuTextureLimit = true
      )
      if (bitmap != null) {
        FileOutputStream(outFile).use { fos ->
          bitmap.compress(Bitmap.CompressFormat.JPEG, 96, fos)
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

  private fun isHtmlResponse(contentType: String?): Boolean {
    return contentType?.lowercase(Locale.US)?.contains("text/html") == true
  }

  private fun parseTotalFromContentRange(headerValue: String?): Long {
    if (headerValue.isNullOrBlank()) return -1L
    val slashIdx = headerValue.lastIndexOf('/')
    if (slashIdx == -1 || slashIdx >= headerValue.lastIndex) return -1L
    return headerValue.substring(slashIdx + 1).trim().toLongOrNull() ?: -1L
  }

  private fun downloadDirect(
    streamUrl: String,
    userAgent: String,
    initialTotalBytes: Long,
    appFile: File,
    onProgress: (DownloadProgressInfo) -> Unit
  ): DownloadAttemptResult {
    if (appFile.exists()) appFile.delete()
    val request = Request.Builder()
      .url(streamUrl)
      .header("User-Agent", userAgent)
      .header("Accept", "*/*")
      .build()

    var lastCode = 0
    client.newCall(request).execute().use { response ->
      lastCode = response.code
      if (!response.isSuccessful || isHtmlResponse(response.header("Content-Type"))) {
        return DownloadAttemptResult(false, lastCode)
      }
      val body = response.body ?: return DownloadAttemptResult(false, lastCode)
      val totalBytes = body.contentLength().let { if (it > 0L) it else initialTotalBytes }
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
    }

    val ok = appFile.exists() && appFile.length() > 0L
    if (!ok && appFile.exists()) appFile.delete()
    return DownloadAttemptResult(ok, lastCode)
  }

  private fun downloadWithQueryRangeChunks(
    streamUrl: String,
    userAgent: String,
    initialTotalBytes: Long,
    appFile: File,
    onProgress: (DownloadProgressInfo) -> Unit
  ): DownloadAttemptResult {
    if (appFile.exists()) appFile.delete()
    var knownTotal = initialTotalBytes
    var offset = 0L
    var lastCode = 0

    FileOutputStream(appFile).use { output ->
      while (knownTotal <= 0L || offset < knownTotal) {
        val end = if (knownTotal > 0L) {
          minOf(offset + CHUNK_SIZE_BYTES - 1L, knownTotal - 1L)
        } else {
          offset + CHUNK_SIZE_BYTES - 1L
        }
        val rangedUrl = YoutubeExtractor.appendRangeQueryParam(streamUrl, offset, end)
        val req = Request.Builder()
          .url(rangedUrl)
          .header("User-Agent", userAgent)
          .header("Accept", "*/*")
          .build()

        var chunkBytes = 0L
        var reachedEof = false
        client.newCall(req).execute().use { resp ->
          lastCode = resp.code
          if (resp.code == 416 && offset > 0L) {
            reachedEof = true
            return@use
          }
          if ((!resp.isSuccessful && resp.code != 206) || isHtmlResponse(resp.header("Content-Type"))) {
            return DownloadAttemptResult(false, lastCode)
          }
          val crTotal = parseTotalFromContentRange(resp.header("Content-Range"))
          if (crTotal > 0L) knownTotal = crTotal

          val body = resp.body ?: return DownloadAttemptResult(false, lastCode)
          body.byteStream().use { input ->
            val buf = ByteArray(16 * 1024)
            while (true) {
              val read = input.read(buf)
              if (read == -1) break
              output.write(buf, 0, read)
              offset += read
              chunkBytes += read
              val progress = if (knownTotal > 0L) {
                (offset.toFloat() / knownTotal.toFloat()).coerceIn(0.02f, 0.98f)
              } else {
                (0.15f + (offset / (6f * 1024f * 1024f)) * 0.8f).coerceIn(0.15f, 0.96f)
              }
              onProgress(DownloadProgressInfo(progress, offset, knownTotal))
            }
          }
        }

        if (reachedEof) break
        if (chunkBytes == 0L) {
          if (knownTotal <= 0L && offset > 0L) break
          return DownloadAttemptResult(false, lastCode)
        }
        if (knownTotal <= 0L && chunkBytes < CHUNK_SIZE_BYTES) {
          break
        }
      }
      output.flush()
    }

    val ok = appFile.exists() && appFile.length() > 0L
    if (!ok && appFile.exists()) appFile.delete()
    return DownloadAttemptResult(ok, lastCode)
  }

  private fun downloadWithHeaderRangeChunks(
    streamUrl: String,
    userAgent: String,
    initialTotalBytes: Long,
    appFile: File,
    onProgress: (DownloadProgressInfo) -> Unit
  ): DownloadAttemptResult {
    if (appFile.exists()) appFile.delete()
    var knownTotal = initialTotalBytes
    var offset = 0L
    var lastCode = 0

    FileOutputStream(appFile).use { output ->
      while (knownTotal <= 0L || offset < knownTotal) {
        val end = if (knownTotal > 0L) {
          minOf(offset + CHUNK_SIZE_BYTES - 1L, knownTotal - 1L)
        } else {
          offset + CHUNK_SIZE_BYTES - 1L
        }
        val req = Request.Builder()
          .url(streamUrl)
          .header("User-Agent", userAgent)
          .header("Accept", "*/*")
          .header("Range", "bytes=$offset-$end")
          .build()

        var chunkBytes = 0L
        var fullFile200 = false
        var reachedEof = false

        client.newCall(req).execute().use { resp ->
          lastCode = resp.code
          if (resp.code == 416 && offset > 0L) {
            reachedEof = true
            return@use
          }
          if ((!resp.isSuccessful && resp.code != 206) || isHtmlResponse(resp.header("Content-Type"))) {
            return DownloadAttemptResult(false, lastCode)
          }

          val crTotal = parseTotalFromContentRange(resp.header("Content-Range"))
          if (crTotal > 0L) knownTotal = crTotal

          // If server ignored Range header and sent the whole stream with HTTP 200 on chunk 0
          if (resp.code == 200 && offset == 0L && crTotal <= 0L) {
            fullFile200 = true
            val contentLen = resp.body?.contentLength() ?: -1L
            if (contentLen > 0L) knownTotal = contentLen
          }

          val body = resp.body ?: return DownloadAttemptResult(false, lastCode)
          body.byteStream().use { input ->
            val buf = ByteArray(16 * 1024)
            while (true) {
              val read = input.read(buf)
              if (read == -1) break
              output.write(buf, 0, read)
              offset += read
              chunkBytes += read
              val progress = if (knownTotal > 0L) {
                (offset.toFloat() / knownTotal.toFloat()).coerceIn(0.02f, 0.98f)
              } else {
                (0.15f + (offset / (6f * 1024f * 1024f)) * 0.8f).coerceIn(0.15f, 0.96f)
              }
              onProgress(DownloadProgressInfo(progress, offset, knownTotal))
            }
          }
        }

        if (fullFile200 || reachedEof) break
        if (chunkBytes == 0L) {
          if (knownTotal <= 0L && offset > 0L) break
          return DownloadAttemptResult(false, lastCode)
        }
        if (knownTotal <= 0L && chunkBytes < CHUNK_SIZE_BYTES) {
          break
        }
      }
      output.flush()
    }

    val ok = appFile.exists() && appFile.length() > 0L
    if (!ok && appFile.exists()) appFile.delete()
    return DownloadAttemptResult(ok, lastCode)
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
    val defaultUa = format.requiredUserAgent?.takeIf { it.isNotBlank() } ?: "AURIX-Android"

    try {
      onProgress(DownloadProgressInfo(0.02f, 0L, format.contentLengthBytes))

      val candidates = buildList {
        add(StreamCandidate(streamUrl, defaultUa, format.downloadMode))
        addAll(format.fallbackCandidates)
      }.distinctBy { "${it.url}__${it.userAgent}" }

      var succeeded = false
      var lastHttpCode = 0

      candidateLoop@ for (candidate in candidates) {
        val isGoogleVideo = candidate.url.contains("googlevideo.com", ignoreCase = true)
        val modesToTry = buildList {
          add(candidate.downloadMode)
          if (isGoogleVideo) add(StreamDownloadMode.QUERY_RANGE)
          add(StreamDownloadMode.HEADER_RANGE)
          add(StreamDownloadMode.DIRECT)
        }.distinct()

        for (mode in modesToTry) {
          val attempt = runCatching {
            when (mode) {
              StreamDownloadMode.QUERY_RANGE -> downloadWithQueryRangeChunks(
                streamUrl = candidate.url,
                userAgent = candidate.userAgent,
                initialTotalBytes = format.contentLengthBytes,
                appFile = appFile,
                onProgress = onProgress
              )
              StreamDownloadMode.HEADER_RANGE -> downloadWithHeaderRangeChunks(
                streamUrl = candidate.url,
                userAgent = candidate.userAgent,
                initialTotalBytes = format.contentLengthBytes,
                appFile = appFile,
                onProgress = onProgress
              )
              StreamDownloadMode.DIRECT -> downloadDirect(
                streamUrl = candidate.url,
                userAgent = candidate.userAgent,
                initialTotalBytes = format.contentLengthBytes,
                appFile = appFile,
                onProgress = onProgress
              )
            }
          }.getOrElse {
            if (appFile.exists()) appFile.delete()
            DownloadAttemptResult(false, lastHttpCode)
          }

          if (attempt.httpCode > 0) lastHttpCode = attempt.httpCode
          if (attempt.succeeded && appFile.exists() && appFile.length() > 0L) {
            succeeded = true
            break@candidateLoop
          }
          if (appFile.exists()) appFile.delete()
        }
      }

      if (!succeeded || !appFile.exists() || appFile.length() == 0L) {
        if (appFile.exists()) appFile.delete()
        val msg = if (lastHttpCode > 0) {
          "Download failed (HTTP $lastHttpCode)"
        } else {
          "Download failed. Please try again."
        }
        return@withContext Result.failure(Exception(msg))
      }

      val actualDurationSec = measureDurationSeconds(appFile, format.durationSeconds)

      // Also export a copy to shared Music/AURIX for user convenience when format is MediaStore-friendly
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && (ext == "m4a" || ext == "mp3" || ext == "mp4")) {
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
