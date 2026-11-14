package com.example.data

import android.net.Uri
import com.example.model.AudioFormatOption
import com.example.model.StreamCandidate
import com.example.model.StreamDownloadMode
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
    .connectTimeout(8, TimeUnit.SECONDS)
    .readTimeout(12, TimeUnit.SECONDS)
    .followRedirects(true)
    .followSslRedirects(true)
    .build()

  private val probeClient = OkHttpClient.Builder()
    .connectTimeout(6, TimeUnit.SECONDS)
    .readTimeout(8, TimeUnit.SECONDS)
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
    ),
    InnerTubeClientProfile(
      clientName = "ANDROID_TESTSUITE",
      clientVersion = "1.9",
      userAgent = "com.google.android.youtube/1.9 (Linux; U; Android 13) gzip",
      deviceMake = "Google",
      deviceModel = "Pixel 7",
      osName = "Android",
      osVersion = "13",
      androidSdkVersion = 33
    )
  )

  private val basePipedMirrors = listOf(
    "https://pipedapi.kavin.rocks",
    "https://pipedapi-libre.kavin.rocks",
    "https://api.piped.private.coffee",
    "https://pipedapi.adminforge.de",
    "https://pipedapi.leptons.xyz",
    "https://pipedapi.ducks.party",
    "https://pipedapi.nosebs.ru"
  )

  private val baseInvidiousMirrors = listOf(
    "https://inv.nadeko.net",
    "https://invidious.nerdvpn.de",
    "https://invidious.perennialte.ch",
    "https://yt.artemislena.eu"
  )

  @Volatile
  private var cachedInvidiousMirrors: List<String>? = null

  fun appendRangeQueryParam(rawUrl: String, start: Long, end: Long): String {
    val cleanUrl = rawUrl.replace(Regex("""([?&])range=[^&]*"""), "$1").replace("?&", "?").trimEnd('&', '?')
    val separator = if (cleanUrl.contains('?')) '&' else '?'
    return "${cleanUrl}${separator}range=$start-$end"
  }

  private fun probeRequestOk(req: Request): Boolean {
    return runCatching {
      probeClient.newCall(req).execute().use { resp ->
        if (!resp.isSuccessful && resp.code != 206) return@use false
        val cType = resp.header("Content-Type").orEmpty().lowercase(Locale.US)
        if (cType.contains("text/html")) return@use false
        val body = resp.body ?: return@use false
        val buf = ByteArray(64)
        val read = body.byteStream().read(buf)
        read > 0
      }
    }.getOrDefault(false)
  }

  /**
   * Tests a stream URL using tiny 1 KB probes so HTTP 403 URLs are caught and bypassed
   * automatically before returning formats to the UI or failing mid-download.
   */
  fun probeWorkingDownloadMode(url: String, userAgent: String): StreamDownloadMode? {
    if (url.isBlank()) return null
    val isGoogleVideo = url.contains("googlevideo.com", ignoreCase = true)

    if (isGoogleVideo) {
      // 1. Bounded query-param range (native YouTube Android/VR/iOS player protocol)
      val qUrl = appendRangeQueryParam(url, 0L, 1023L)
      val qReq = Request.Builder()
        .url(qUrl)
        .header("User-Agent", userAgent)
        .header("Accept", "*/*")
        .build()
      if (probeRequestOk(qReq)) return StreamDownloadMode.QUERY_RANGE

      // 2. Bounded HTTP Range header
      val hReq = Request.Builder()
        .url(url)
        .header("User-Agent", userAgent)
        .header("Accept", "*/*")
        .header("Range", "bytes=0-1023")
        .build()
      if (probeRequestOk(hReq)) return StreamDownloadMode.HEADER_RANGE

      // 3. Direct GET probe (no Referer)
      val dReq = Request.Builder()
        .url(url)
        .header("User-Agent", userAgent)
        .header("Accept", "*/*")
        .build()
      if (probeRequestOk(dReq)) return StreamDownloadMode.DIRECT

      return null
    } else {
      val hReq = Request.Builder()
        .url(url)
        .header("User-Agent", userAgent)
        .header("Accept", "*/*")
        .header("Range", "bytes=0-1023")
        .build()
      if (probeRequestOk(hReq)) return StreamDownloadMode.DIRECT

      val dReq = Request.Builder()
        .url(url)
        .header("User-Agent", userAgent)
        .header("Accept", "*/*")
        .build()
      if (probeRequestOk(dReq)) return StreamDownloadMode.DIRECT

      return null
    }
  }

  private fun getInvidiousMirrors(): List<String> {
    cachedInvidiousMirrors?.let { return it }
    val discovered = runCatching {
      val req = Request.Builder()
        .url("https://api.invidious.io/instances.json?sort_by=type,users")
        .header("User-Agent", "AURIX-Android")
        .build()
      probeClient.newCall(req).execute().use { resp ->
        if (!resp.isSuccessful) return@use emptyList<String>()
        val body = resp.body?.string().orEmpty()
        val arr = JSONArray(body)
        val out = mutableListOf<String>()
        for (i in 0 until minOf(arr.length(), 20)) {
          val entry = arr.optJSONArray(i) ?: continue
          val domain = entry.optString(0).trim()
          val meta = entry.optJSONObject(1) ?: continue
          val type = meta.optString("type")
          val apiEnabled = meta.optBoolean("api", false)
          val uri = meta.optString("uri").trim().trimEnd('/')
          if (type == "https" && apiEnabled) {
            val base = uri.ifBlank { if (domain.isNotBlank()) "https://$domain" else "" }
            if (base.startsWith("https://")) out.add(base)
          }
        }
        out
      }
    }.getOrDefault(emptyList())

    val combined = (baseInvidiousMirrors + discovered).distinct().take(10)
    if (discovered.isNotEmpty()) {
      cachedInvidiousMirrors = combined
    }
    return combined
  }

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

  suspend fun extractFullAudio(
    videoId: String,
    skipClients: Set<String> = emptySet()
  ): ExtractedYoutubeData? = withContext(Dispatchers.IO) {
    if (videoId.isBlank()) return@withContext null

    var lastTitle = ""
    var lastAuthor = ""
    var lastThumb = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
    var lastDurationSec = 0L
    var lastReason: String? = null

    val progressiveBackups = mutableListOf<AudioFormatOption>()

    // 1. Direct InnerTube clients (probe top format so 403-blocked clients are skipped automatically)
    for (profile in innerTubeProfiles) {
      if (profile.clientName in skipClients) continue
      val attempt = runCatching { fetchFromInnerTube(videoId, profile) }.getOrNull() ?: continue
      if (attempt.title.isNotBlank()) lastTitle = attempt.title
      if (attempt.author.isNotBlank()) lastAuthor = attempt.author
      if (attempt.thumbnailUrl.isNotBlank()) lastThumb = attempt.thumbnailUrl
      if (attempt.durationSeconds > 0L) lastDurationSec = attempt.durationSeconds
      if (!attempt.extractorError.isNullOrBlank()) lastReason = attempt.extractorError

      val verifiedAdaptive = verifyFormatList(attempt.adaptiveFormats)
      if (verifiedAdaptive.isNotEmpty()) {
        return@withContext ExtractedYoutubeData(
          videoId = videoId,
          title = lastTitle,
          author = lastAuthor,
          thumbnailUrl = lastThumb,
          durationSeconds = lastDurationSec,
          audioFormats = verifiedAdaptive,
          extractorError = null
        )
      }
      progressiveBackups.addAll(attempt.progressiveFormats)
    }

    // 2. Piped public instances (server-side deciphered & proxied)
    for (base in basePipedMirrors) {
      val attempt = runCatching { fetchFromPiped(base, videoId) }.getOrNull() ?: continue
      if (attempt.title.isNotBlank()) lastTitle = attempt.title
      if (attempt.author.isNotBlank()) lastAuthor = attempt.author
      if (attempt.thumbnailUrl.isNotBlank()) lastThumb = attempt.thumbnailUrl
      if (attempt.durationSeconds > 0L) lastDurationSec = attempt.durationSeconds

      val verifiedPiped = verifyFormatList(attempt.audioFormats)
      if (verifiedPiped.isNotEmpty()) {
        return@withContext ExtractedYoutubeData(
          videoId = videoId,
          title = lastTitle,
          author = lastAuthor,
          thumbnailUrl = lastThumb,
          durationSeconds = lastDurationSec,
          audioFormats = verifiedPiped
        )
      }
    }

    // 3. Invidious public instances (checks direct URL AND Invidious `local=true` server proxy)
    for (base in getInvidiousMirrors()) {
      val attempt = runCatching { fetchFromInvidious(base, videoId) }.getOrNull() ?: continue
      if (attempt.title.isNotBlank()) lastTitle = attempt.title
      if (attempt.author.isNotBlank()) lastAuthor = attempt.author
      if (attempt.thumbnailUrl.isNotBlank()) lastThumb = attempt.thumbnailUrl
      if (attempt.durationSeconds > 0L) lastDurationSec = attempt.durationSeconds

      val verifiedInv = verifyFormatList(attempt.audioFormats)
      if (verifiedInv.isNotEmpty()) {
        return@withContext ExtractedYoutubeData(
          videoId = videoId,
          title = lastTitle,
          author = lastAuthor,
          thumbnailUrl = lastThumb,
          durationSeconds = lastDurationSec,
          audioFormats = verifiedInv
        )
      }
    }

    // 4. Last-resort Invidious local=true direct proxy endpoints even if video JSON endpoint was limited
    val proxyFallbacks = probeDirectInvidiousProxies(videoId, lastDurationSec)
    if (proxyFallbacks.isNotEmpty()) {
      return@withContext ExtractedYoutubeData(
        videoId = videoId,
        title = lastTitle,
        author = lastAuthor,
        thumbnailUrl = lastThumb,
        durationSeconds = lastDurationSec,
        audioFormats = proxyFallbacks
      )
    }

    // 5. Progressive InnerTube fallback (e.g. itag=18 MP4 AAC when adaptive streams require PO token)
    val verifiedProgressive = verifyFormatList(progressiveBackups)
    if (verifiedProgressive.isNotEmpty()) {
      return@withContext ExtractedYoutubeData(
        videoId = videoId,
        title = lastTitle,
        author = lastAuthor,
        thumbnailUrl = lastThumb,
        durationSeconds = lastDurationSec,
        audioFormats = verifiedProgressive
      )
    }

    if (lastTitle.isNotBlank() || !lastReason.isNullOrBlank()) {
      ExtractedYoutubeData(
        videoId = videoId,
        title = lastTitle,
        author = lastAuthor,
        thumbnailUrl = lastThumb,
        durationSeconds = lastDurationSec,
        audioFormats = emptyList(),
        extractorError = lastReason ?: "Streams returned HTTP 403 across tested mirrors"
      )
    } else {
      null
    }
  }

  /**
   * Used when a selected format hits 403 during download (e.g. expired URL or mid-stream rejection):
   * resolves a fresh verified format matching the requested extension or best working audio format.
   */
  suspend fun extractFreshWorkingFormat(
    videoId: String,
    preferredExtension: String,
    failedFormatId: String? = null
  ): AudioFormatOption? = withContext(Dispatchers.IO) {
    val failedClient = innerTubeProfiles
      .firstOrNull { profile ->
        failedFormatId?.contains("_${profile.clientName.lowercase(Locale.US)}_", ignoreCase = true) == true
      }
      ?.clientName
    val skipSet = if (failedClient != null) setOf(failedClient) else emptySet()
    val fresh = extractFullAudio(videoId, skipClients = skipSet)
      ?: if (skipSet.isNotEmpty()) extractFullAudio(videoId) else null
    fresh?.audioFormats?.firstOrNull { it.extension.equals(preferredExtension, ignoreCase = true) }
      ?: fresh?.audioFormats?.firstOrNull()
  }

  private fun verifyFormatList(formats: List<AudioFormatOption>): List<AudioFormatOption> {
    if (formats.isEmpty()) return emptyList()
    // Probe the primary format of this source first; if it passes, apply its verified downloadMode
    // to sibling formats from the same client/mirror, or probe any fallback candidate if present.
    val first = formats.first()
    val firstUa = first.requiredUserAgent ?: "AURIX-Android"
    val firstMode = probeWorkingDownloadMode(first.streamUrl, firstUa)
    if (firstMode != null) {
      return formats.map { fmt ->
        fmt.copy(downloadMode = firstMode)
      }
    }

    // If primary streamUrl failed (e.g. Invidious direct googlevideo URL 403), try attached fallbackCandidates
    val updated = mutableListOf<AudioFormatOption>()
    for (fmt in formats) {
      val workingBackup = fmt.fallbackCandidates.firstNotNullOfOrNull { cand ->
        val mode = probeWorkingDownloadMode(cand.url, cand.userAgent)
        if (mode != null) cand.copy(downloadMode = mode) else null
      }
      if (workingBackup != null) {
        updated.add(
          fmt.copy(
            streamUrl = workingBackup.url,
            requiredUserAgent = workingBackup.userAgent,
            downloadMode = workingBackup.downloadMode
          )
        )
      }
    }
    return updated
  }

  private fun probeDirectInvidiousProxies(videoId: String, durationSec: Long): List<AudioFormatOption> {
    val itagSpecs = listOf(
      Triple(140, "M4A" to "128 kbps AAC", "m4a" to "audio/mp4"),
      Triple(251, "WEBM" to "160 kbps Opus", "webm" to "audio/webm"),
      Triple(18, "MP4" to "AAC Audio", "mp4" to "audio/mp4")
    )
    for (base in getInvidiousMirrors().take(5)) {
      val verified = mutableListOf<AudioFormatOption>()
      for ((itag, labels, mimePair) in itagSpecs) {
        val proxyUrl = "$base/latest_version?id=$videoId&itag=$itag&local=true"
        val mode = probeWorkingDownloadMode(proxyUrl, "AURIX-Android")
        if (mode != null) {
          val durFormatted = formatDurationShort(durationSec)
          verified.add(
            AudioFormatOption(
              id = "inv_proxy_${itag}",
              formatLabel = labels.first,
              qualityLabel = labels.second,
              detailLabel = "Full Song ($durFormatted) • Proxy",
              extension = mimePair.first,
              mimeType = mimePair.second,
              streamUrl = proxyUrl,
              durationSeconds = durationSec,
              contentLengthBytes = -1L,
              requiredUserAgent = "AURIX-Android",
              downloadMode = mode
            )
          )
        }
      }
      if (verified.isNotEmpty()) return verified
    }
    return emptyList()
  }

  private data class InnerTubeAttempt(
    val title: String,
    val author: String,
    val thumbnailUrl: String,
    val durationSeconds: Long,
    val adaptiveFormats: List<AudioFormatOption>,
    val progressiveFormats: List<AudioFormatOption>,
    val extractorError: String?
  )

  private fun fetchFromInnerTube(
    videoId: String,
    profile: InnerTubeClientProfile
  ): InnerTubeAttempt? {
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
        return InnerTubeAttempt(
          title = title,
          author = author,
          thumbnailUrl = thumbUrl,
          durationSeconds = lengthSeconds,
          adaptiveFormats = emptyList(),
          progressiveFormats = emptyList(),
          extractorError = reason
        )
      }

      val streamingData = root.optJSONObject("streamingData") ?: return InnerTubeAttempt(
        title = title,
        author = author,
        thumbnailUrl = thumbUrl,
        durationSeconds = lengthSeconds,
        adaptiveFormats = emptyList(),
        progressiveFormats = emptyList(),
        extractorError = reason
      )

      val adaptive = streamingData.optJSONArray("adaptiveFormats") ?: JSONArray()
      val parsedAdaptive = mutableListOf<AudioFormatOption>()

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

        parsedAdaptive.add(
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

      val sortedAdaptive = parsedAdaptive
        .sortedWith(
          compareByDescending<AudioFormatOption> { it.extension == "m4a" }
            .thenByDescending { extractBitrateNumber(it.qualityLabel) }
        )
        .distinctBy { "${it.formatLabel}_${it.qualityLabel}" }

      // Parse progressive fallback (e.g. itag 18 MP4 with AAC audio)
      val progressiveArr = streamingData.optJSONArray("formats") ?: JSONArray()
      val parsedProgressive = mutableListOf<AudioFormatOption>()
      for (i in 0 until progressiveArr.length()) {
        val item = progressiveArr.optJSONObject(i) ?: continue
        val url = item.optString("url").trim()
        if (url.isBlank()) continue
        val itag = item.optInt("itag", i)
        val contentLength = item.optString("contentLength").toLongOrNull()
          ?: item.optLong("contentLength", -1L)
        val approxSec = item.optString("approxDurationMs").toLongOrNull()?.div(1000L) ?: 0L
        val durSec = if (lengthSeconds > 0L) lengthSeconds else approxSec
        val durFormatted = formatDurationShort(durSec)
        val sizeText = if (contentLength > 0L) {
          String.format(Locale.US, " • %.1f MB", contentLength / (1024f * 1024f))
        } else ""
        parsedProgressive.add(
          AudioFormatOption(
            id = "yt_prog_${profile.clientName.lowercase(Locale.US)}_${itag}",
            formatLabel = "MP4",
            qualityLabel = "128 kbps AAC (Standard)",
            detailLabel = "Full Song ($durFormatted)$sizeText",
            extension = "mp4",
            mimeType = "audio/mp4",
            streamUrl = url,
            durationSeconds = durSec,
            contentLengthBytes = contentLength,
            requiredUserAgent = profile.userAgent
          )
        )
      }

      return InnerTubeAttempt(
        title = title,
        author = author,
        thumbnailUrl = thumbUrl,
        durationSeconds = lengthSeconds,
        adaptiveFormats = sortedAdaptive,
        progressiveFormats = parsedProgressive,
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
            contentLengthBytes = contentLength,
            requiredUserAgent = "AURIX-Android"
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
        val directUrl = f.optString("url").trim()
        val itag = f.optString("itag").trim()
        val proxyUrl = if (itag.isNotBlank()) "$base/latest_version?id=$videoId&itag=$itag&local=true" else ""
        if (directUrl.isBlank() && proxyUrl.isBlank()) continue

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

        val primary = directUrl.ifBlank { proxyUrl }
        val backups = buildList {
          if (proxyUrl.isNotBlank() && proxyUrl != primary) {
            add(StreamCandidate(url = proxyUrl, userAgent = "AURIX-Android", downloadMode = StreamDownloadMode.DIRECT))
          }
        }

        formats.add(
          AudioFormatOption(
            id = "inv_${i}_${ext}_${bitrate}",
            formatLabel = fmtLabel,
            qualityLabel = kbps,
            detailLabel = "Full Song ($durFormatted)$sizeText",
            extension = ext,
            mimeType = baseMime.ifBlank { if (isM4a) "audio/mp4" else "audio/webm" },
            streamUrl = primary,
            durationSeconds = durSec,
            contentLengthBytes = contentLength,
            requiredUserAgent = "AURIX-Android",
            fallbackCandidates = backups
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
