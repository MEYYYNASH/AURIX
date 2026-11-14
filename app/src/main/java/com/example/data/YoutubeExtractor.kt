package com.example.data

import android.net.Uri
import com.example.model.AudioFormatOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit

data class ExtractedYoutubeData(
  val videoId: String,
  val title: String,
  val author: String,
  val thumbnailUrl: String,
  val durationSeconds: Long,
  val audioFormats: List<AudioFormatOption>,
  val extractorError: String? = null
)

private data class InnerTubeClientProfile(
  val clientName: String,
  val clientVersion: String,
  val userAgent: String,
  val deviceMake: String? = null,
  val deviceModel: String? = null,
  val osName: String? = null,
  val osVersion: String? = null,
  val androidSdkVersion: Int? = null
)

object YoutubeExtractor {
  private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

  private val client = OkHttpClient.Builder()
    .connectTimeout(10, TimeUnit.SECONDS)
    .readTimeout(14, TimeUnit.SECONDS)
    .followRedirects(true)
    .followSslRedirects(true)
    .build()

  private val innerTubeProfiles = listOf(
    InnerTubeClientProfile(
      clientName = "ANDROID_VR",
      clientVersion = "1.60.19",
      userAgent = "com.google.android.apps.youtube.vr.oculus/1.60.19 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip",
      deviceMake = "Oculus",
      deviceModel = "Quest 3",
      osName = "Android",
      osVersion = "12L",
      androidSdkVersion = 32
    ),
    InnerTubeClientProfile(
      clientName = "IOS",
      clientVersion = "20.10.4",
      userAgent = "com.google.ios.youtube/20.10.4 (iPhone16,2; U; CPU iOS 18_3_2 like Mac OS X;)",
      deviceMake = "Apple",
      deviceModel = "iPhone16,2",
      osName = "iPhone",
      osVersion = "18.3.2.22D82"
    ),
    InnerTubeClientProfile(
      clientName = "ANDROID",
      clientVersion = "20.10.38",
      userAgent = "com.google.android.youtube/20.10.38 (Linux; U; Android 14) gzip",
      deviceMake = "Google",
      deviceModel = "Pixel 8",
      osName = "Android",
      osVersion = "14",
      androidSdkVersion = 34
    )
  )

  private val pipedMirrors = listOf(
    "https://pipedapi.kavin.rocks",
    "https://api.piped.private.coffee",
    "https://pipedapi.adminforge.de",
    "https://pipedapi.leptons.xyz",
    "https://pipedapi.nosebs.ru"
  )

  private val invidiousMirrors = listOf(
    "https://inv.nadeko.net",
    "https://invidious.nerdvpn.de",
    "https://invidious.perennialte.ch",
    "https://yt.artemislena.eu"
  )

  fun extractVideoId(rawUrl: String): String? {
    val trimmed = rawUrl.trim()
    val uri = runCatching { Uri.parse(trimmed) }.getOrNull() ?: return null
    val host = uri.host.orEmpty().lowercase(Locale.US)
    val segments = uri.pathSegments.orEmpty()
    val rawId = when {
      host.contains("youtu.be") -> segments.firstOrNull()
      uri.getQueryParameter("v")?.isNotBlank() == true -> uri.getQueryParameter("v")
      segments.size >= 2 && segments[0] in listOf("shorts", "embed", "live", "v") -> segments[1]
      else -> null
    }
    return rawId
      ?.substringBefore('?')
      ?.substringBefore('&')
      ?.trim()
      ?.takeIf { it.length in 8..16 }
  }

  suspend fun extractFullAudio(videoId: String): ExtractedYoutubeData? = withContext(Dispatchers.IO) {
    if (videoId.isBlank()) return@withContext null

    var lastTitle = ""
    var lastAuthor = ""
    var lastThumb = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
    var lastDurationSec = 0L
    var lastReason: String? = null

    // 1. Primary: Direct YouTube InnerTube Player API (ANDROID_VR -> IOS -> ANDROID)
    for (profile in innerTubeProfiles) {
      val attempt = runCatching { fetchFromInnerTube(videoId, profile) }.getOrNull()
      if (attempt != null) {
        if (attempt.title.isNotBlank()) lastTitle = attempt.title
        if (attempt.author.isNotBlank()) lastAuthor = attempt.author
        if (attempt.thumbnailUrl.isNotBlank()) lastThumb = attempt.thumbnailUrl
        if (attempt.durationSeconds > 0L) lastDurationSec = attempt.durationSeconds
        if (!attempt.extractorError.isNullOrBlank()) lastReason = attempt.extractorError

        if (attempt.audioFormats.isNotEmpty()) {
          return@withContext attempt
        }
      }
    }

    // 2. Fallback: Piped public instances
    for (base in pipedMirrors) {
      val attempt = runCatching { fetchFromPiped(base, videoId) }.getOrNull()
      if (attempt != null) {
        if (attempt.title.isNotBlank()) lastTitle = attempt.title
        if (attempt.author.isNotBlank()) lastAuthor = attempt.author
        if (attempt.thumbnailUrl.isNotBlank()) lastThumb = attempt.thumbnailUrl
        if (attempt.durationSeconds > 0L) lastDurationSec = attempt.durationSeconds

        if (attempt.audioFormats.isNotEmpty()) {
          return@withContext attempt
        }
      }
    }

    // 3. Fallback: Invidious public instances
    for (base in invidiousMirrors) {
      val attempt = runCatching { fetchFromInvidious(base, videoId) }.getOrNull()
      if (attempt != null) {
        if (attempt.title.isNotBlank()) lastTitle = attempt.title
        if (attempt.author.isNotBlank()) lastAuthor = attempt.author
        if (attempt.thumbnailUrl.isNotBlank()) lastThumb = attempt.thumbnailUrl
        if (attempt.durationSeconds > 0L) lastDurationSec = attempt.durationSeconds

        if (attempt.audioFormats.isNotEmpty()) {
          return@withContext attempt
        }
      }
    }

    if (lastTitle.isNotBlank() || !lastReason.isNullOrBlank()) {
      ExtractedYoutubeData(
        videoId = videoId,
        title = lastTitle,
        author = lastAuthor,
        thumbnailUrl = lastThumb,
        durationSeconds = lastDurationSec,
        audioFormats = emptyList(),
        extractorError = lastReason
      )
    } else {
      null
    }
  }

  private fun fetchFromInnerTube(
    videoId: String,
    profile: InnerTubeClientProfile
  ): ExtractedYoutubeData? {
    val clientObj = JSONObject().apply {
      put("clientName", profile.clientName)
      put("clientVersion", profile.clientVersion)
      put("hl", "en")
      put("gl", "US")
      profile.deviceMake?.let { put("deviceMake", it) }
      profile.deviceModel?.let { put("deviceModel", it) }
      profile.osName?.let { put("osName", it) }
      profile.osVersion?.let { put("osVersion", it) }
      profile.androidSdkVersion?.let { put("androidSdkVersion", it) }
    }
    val payload = JSONObject().apply {
      put("videoId", videoId)
      put("context", JSONObject().put("client", clientObj))
      put("contentCheckOk", true)
      put("racyCheckOk", true)
    }

    val request = Request.Builder()
      .url("https://www.youtube.com/youtubei/v1/player?prettyPrint=false")
      .post(payload.toString().toRequestBody(jsonMediaType))
      .header("User-Agent", profile.userAgent)
      .header("Content-Type", "application/json")
      .header("Origin", "https://www.youtube.com")
      .header("Referer", "https://www.youtube.com/watch?v=$videoId")
      .build()

    client.newCall(request).execute().use { resp ->
      if (!resp.isSuccessful) return null
      val body = resp.body?.string().orEmpty()
      if (body.isBlank()) return null

      val root = JSONObject(body)
      val playability = root.optJSONObject("playabilityStatus")
      val status = playability?.optString("status").orEmpty()
      val reason = playability?.optString("reason").orEmpty().ifBlank { null }

      val details = root.optJSONObject("videoDetails")
      val title = details?.optString("title").orEmpty().trim()
      val author = details?.optString("author").orEmpty().trim()
      val lengthSeconds = details?.optString("lengthSeconds")?.toLongOrNull() ?: 0L
      val thumbUrl = bestThumbnailUrl(details?.optJSONObject("thumbnail")?.optJSONArray("thumbnails"))
        ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"

      if (status.isNotBlank() && !status.equals("OK", ignoreCase = true)) {
        return ExtractedYoutubeData(
          videoId = videoId,
          title = title,
          author = author,
          thumbnailUrl = thumbUrl,
          durationSeconds = lengthSeconds,
          audioFormats = emptyList(),
          extractorError = reason
        )
      }

      val streamingData = root.optJSONObject("streamingData") ?: return ExtractedYoutubeData(
        videoId = videoId,
        title = title,
        author = author,
        thumbnailUrl = thumbUrl,
        durationSeconds = lengthSeconds,
        audioFormats = emptyList(),
        extractorError = reason
      )

      val adaptive = streamingData.optJSONArray("adaptiveFormats") ?: JSONArray()
      val parsedFormats = mutableListOf<AudioFormatOption>()

      for (i in 0 until adaptive.length()) {
        val item = adaptive.optJSONObject(i) ?: continue
        val url = item.optString("url").trim()
        if (url.isBlank()) continue

        val fullMime = item.optString("mimeType").trim()
        if (!fullMime.startsWith("audio/", ignoreCase = true)) continue

        val baseMime = fullMime.substringBefore(';').trim()
        val isM4a = baseMime.contains("mp4", ignoreCase = true) || baseMime.contains("m4a", ignoreCase = true)
        val ext = if (isM4a) "m4a" else "webm"
        val fmtLabel = if (isM4a) "M4A" else "WEBM"
        val codecName = when {
          fullMime.contains("mp4a", ignoreCase = true) || isM4a -> "AAC"
          fullMime.contains("opus", ignoreCase = true) -> "Opus"
          else -> "Audio"
        }

        val bitrate = item.optInt("averageBitrate", 0).let {
          if (it > 0) it else item.optInt("bitrate", 0)
        }
        val contentLength = item.optString("contentLength").toLongOrNull()
          ?: item.optLong("contentLength", -1L)
        val approxSec = item.optString("approxDurationMs").toLongOrNull()?.div(1000L) ?: 0L
        val durSec = if (lengthSeconds > 0L) lengthSeconds else approxSec

        val kbpsLabel = if (bitrate > 0) "${(bitrate / 1000).coerceAtLeast(32)} kbps $codecName" else "$codecName Audio"
        val durFormatted = formatDurationShort(durSec)
        val sizeText = if (contentLength > 0L) {
          String.format(Locale.US, " • %.1f MB", contentLength / (1024f * 1024f))
        } else ""
        val itag = item.optInt("itag", i)

        parsedFormats.add(
          AudioFormatOption(
            id = "yt_${profile.clientName.lowercase(Locale.US)}_${itag}_${bitrate}",
            formatLabel = fmtLabel,
            qualityLabel = kbpsLabel,
            detailLabel = "Full Song ($durFormatted)$sizeText",
            extension = ext,
            mimeType = baseMime.ifBlank { if (isM4a) "audio/mp4" else "audio/webm" },
            streamUrl = url,
            durationSeconds = durSec,
            contentLengthBytes = contentLength,
            requiredUserAgent = profile.userAgent
          )
        )
      }

      // Sort cleanly: M4A (highest compatibility) & highest bitrate first, distinct bitrates
      val sorted = parsedFormats
        .sortedWith(
          compareByDescending<AudioFormatOption> { it.extension == "m4a" }
            .thenByDescending { extractBitrateNumber(it.qualityLabel) }
        )
        .distinctBy { "${it.formatLabel}_${it.qualityLabel}" }

      return ExtractedYoutubeData(
        videoId = videoId,
        title = title,
        author = author,
        thumbnailUrl = thumbUrl,
        durationSeconds = lengthSeconds,
        audioFormats = sorted,
        extractorError = reason
      )
    }
  }

  private fun fetchFromPiped(base: String, videoId: String): ExtractedYoutubeData? {
    val req = Request.Builder()
      .url("$base/streams/$videoId")
      .header("User-Agent", "AURIX-Android")
      .build()
    client.newCall(req).execute().use { resp ->
      if (!resp.isSuccessful) return null
      val body = resp.body?.string().orEmpty()
      if (body.isBlank()) return null

      val root = JSONObject(body)
      val title = root.optString("title").trim()
      val author = root.optString("uploader").trim()
      val thumb = root.optString("thumbnailUrl").trim()
        .ifBlank { "https://i.ytimg.com/vi/$videoId/hqdefault.jpg" }
      val durSec = root.optLong("duration", 0L)
      val streams = root.optJSONArray("audioStreams") ?: return null

      val formats = mutableListOf<AudioFormatOption>()
      for (i in 0 until streams.length()) {
        val s = streams.optJSONObject(i) ?: continue
        val url = s.optString("url").trim()
        if (url.isBlank()) continue
        val mime = s.optString("mimeType").substringBefore(';').trim()
        val upperFormat = s.optString("format").uppercase(Locale.US)
        val isM4a = upperFormat.contains("M4A") || mime.contains("mp4", ignoreCase = true)
        val ext = if (isM4a) "m4a" else "webm"
        val fmtLabel = if (isM4a) "M4A" else "WEBM"
        val bitrate = s.optInt("bitrate", 0)
        val kbps = if (bitrate > 0) "${bitrate / 1000} kbps" else s.optString("quality").ifBlank { "Standard" }
        val codec = s.optString("codec").uppercase(Locale.US).ifBlank { if (isM4a) "AAC" else "Opus" }
        val contentLength = s.optLong("contentLength", -1L)
        val durFormatted = formatDurationShort(durSec)
        val sizeText = if (contentLength > 0L) {
          String.format(Locale.US, " • %.1f MB", contentLength / (1024f * 1024f))
        } else ""

        formats.add(
          AudioFormatOption(
            id = "piped_${i}_${ext}_${bitrate}",
            formatLabel = fmtLabel,
            qualityLabel = "$kbps $codec",
            detailLabel = "Full Song ($durFormatted)$sizeText",
            extension = ext,
            mimeType = mime.ifBlank { if (isM4a) "audio/mp4" else "audio/webm" },
            streamUrl = url,
            durationSeconds = durSec,
            contentLengthBytes = contentLength
          )
        )
      }

      val sorted = formats
        .sortedWith(
          compareByDescending<AudioFormatOption> { it.extension == "m4a" }
            .thenByDescending { extractBitrateNumber(it.qualityLabel) }
        )
        .distinctBy { "${it.formatLabel}_${it.qualityLabel}" }

      return ExtractedYoutubeData(
        videoId = videoId,
        title = title,
        author = author,
        thumbnailUrl = thumb,
        durationSeconds = durSec,
        audioFormats = sorted
      )
    }
  }

  private fun fetchFromInvidious(base: String, videoId: String): ExtractedYoutubeData? {
    val req = Request.Builder()
      .url("$base/api/v1/videos/$videoId")
      .header("User-Agent", "AURIX-Android")
      .build()
    client.newCall(req).execute().use { resp ->
      if (!resp.isSuccessful) return null
      val body = resp.body?.string().orEmpty()
      if (body.isBlank()) return null

      val root = JSONObject(body)
      val title = root.optString("title").trim()
      val author = root.optString("author").trim()
      val durSec = root.optLong("lengthSeconds", 0L)
      val thumb = bestThumbnailUrl(root.optJSONArray("videoThumbnails"))
        ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
      val adaptive = root.optJSONArray("adaptiveFormats") ?: return null

      val formats = mutableListOf<AudioFormatOption>()
      for (i in 0 until adaptive.length()) {
        val f = adaptive.optJSONObject(i) ?: continue
        val url = f.optString("url").trim()
        if (url.isBlank()) continue
        val type = f.optString("type").trim()
        if (!type.startsWith("audio/", ignoreCase = true)) continue

        val baseMime = type.substringBefore(';').trim()
        val isM4a = baseMime.contains("mp4", ignoreCase = true) || f.optString("container").equals("m4a", ignoreCase = true)
        val ext = if (isM4a) "m4a" else "webm"
        val fmtLabel = if (isM4a) "M4A" else "WEBM"
        val bitrate = f.optString("bitrate").toIntOrNull() ?: f.optInt("bitrate", 0)
        val contentLength = f.optString("clen").toLongOrNull() ?: -1L
        val codec = f.optString("encoding").uppercase(Locale.US).ifBlank { if (isM4a) "AAC" else "Opus" }
        val kbps = if (bitrate > 0) "${bitrate / 1000} kbps $codec" else "$codec Audio"
        val durFormatted = formatDurationShort(durSec)
        val sizeText = if (contentLength > 0L) {
          String.format(Locale.US, " • %.1f MB", contentLength / (1024f * 1024f))
        } else ""

        formats.add(
          AudioFormatOption(
            id = "inv_${i}_${ext}_${bitrate}",
            formatLabel = fmtLabel,
            qualityLabel = kbps,
            detailLabel = "Full Song ($durFormatted)$sizeText",
            extension = ext,
            mimeType = baseMime.ifBlank { if (isM4a) "audio/mp4" else "audio/webm" },
            streamUrl = url,
            durationSeconds = durSec,
            contentLengthBytes = contentLength
          )
        )
      }

      val sorted = formats
        .sortedWith(
          compareByDescending<AudioFormatOption> { it.extension == "m4a" }
            .thenByDescending { extractBitrateNumber(it.qualityLabel) }
        )
        .distinctBy { "${it.formatLabel}_${it.qualityLabel}" }

      return ExtractedYoutubeData(
        videoId = videoId,
        title = title,
        author = author,
        thumbnailUrl = thumb,
        durationSeconds = durSec,
        audioFormats = sorted
      )
    }
  }

  private fun bestThumbnailUrl(array: JSONArray?): String? {
    if (array == null || array.length() == 0) return null
    var bestUrl: String? = null
    var bestWidth = -1
    for (i in 0 until array.length()) {
      val obj = array.optJSONObject(i) ?: continue
      val url = obj.optString("url").trim()
      val width = obj.optInt("width", 0)
      if (url.isNotBlank() && width >= bestWidth) {
        bestWidth = width
        bestUrl = url
      }
    }
    return bestUrl
  }

  private fun extractBitrateNumber(qualityLabel: String): Int {
    return Regex("""(\d+)""").find(qualityLabel)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
  }

  fun formatDurationShort(seconds: Long): String {
    if (seconds <= 0L) return "Full"
    val min = seconds / 60L
    val sec = seconds % 60L
    return String.format(Locale.US, "%d:%02d", min, sec)
  }
}
