package com.example.data

import android.net.Uri
import com.example.model.AudioFormatOption
import com.example.model.StreamCandidate
import com.example.model.StreamDownloadMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.Collections
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request as NpRequest
import org.schabi.newpipe.extractor.downloader.Response as NpResponse
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

object StreamHeaderRegistry {
  private val userAgents = ConcurrentHashMap<String, String>()

  fun register(streamUrl: String, userAgent: String?) {
    if (streamUrl.isBlank() || userAgent.isNullOrBlank()) return
    if (userAgents.size > 200) {
      userAgents.clear()
    }
    userAgents[streamUrl] = userAgent
  }

  fun resolveUserAgent(streamUrl: String): String? {
    return userAgents[streamUrl]
  }
}

data class YoutubeSearchItem(
  val videoId: String,
  val rawTitle: String,
  val author: String,
  val thumbnailUrl: String,
  val durationSeconds: Long
)

data class ExtractedYoutubeData(
  val videoId: String,
  val title: String,
  val author: String,
  val thumbnailUrl: String,
  val durationSeconds: Long,
  val audioFormats: List<AudioFormatOption>,
  val extractorError: String? = null
)

data class ExtractedYoutubePlaylist(
  val playlistId: String,
  val originalUrl: String,
  val title: String,
  val author: String,
  val thumbnailUrl: String,
  val totalCount: Long,
  val tracks: List<YoutubeSearchItem>
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

private class NewPipeOkHttpDownloader(
  private val httpClient: OkHttpClient
) : Downloader() {
  companion object {
    const val DESKTOP_FIREFOX_UA =
      "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0"
    const val DESKTOP_CHROME_UA =
      "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.36"
  }

  override fun execute(request: NpRequest): NpResponse {
    val httpMethod = request.httpMethod()
    val url = request.url()
    val headers = request.headers()
    val dataToSend = request.dataToSend()

    val contentType = headers.entries
      .firstOrNull { it.key.equals("Content-Type", ignoreCase = true) }
      ?.value?.firstOrNull()
      ?.toMediaTypeOrNull()

    val body = when {
      dataToSend != null -> dataToSend.toRequestBody(contentType)
      httpMethod.equals("POST", ignoreCase = true) ||
        httpMethod.equals("PUT", ignoreCase = true) -> ByteArray(0).toRequestBody(null)
      else -> null
    }

    val reqBuilder = try {
      Request.Builder()
        .url(url)
        .method(httpMethod, body)
        .header("User-Agent", DESKTOP_FIREFOX_UA)
        .apply {
          for ((key, values) in headers) {
            if (values.isEmpty()) continue
            if (key.equals("User-Agent", ignoreCase = true)) {
              header("User-Agent", values.first())
            } else {
              removeHeader(key)
              for (v in values) {
                addHeader(key, v)
              }
            }
          }
        }
    } catch (e: IllegalArgumentException) {
      throw IOException("Invalid request parameters for URL: $url", e)
    }

    httpClient.newCall(reqBuilder.build()).execute().use { resp ->
      if (resp.code == 429) {
        throw ReCaptchaException("reCaptcha Challenge requested", url)
      }
      val responseBody = readBoundedResponseBody(resp)
      val finalUrl = resp.request.url.toString()
      return NpResponse(
        resp.code,
        resp.message,
        resp.headers.toMultimap(),
        responseBody,
        finalUrl
      )
    }
  }

  private fun readBoundedResponseBody(resp: Response, maxChars: Int = 2_500_000): String {
    val body = resp.body ?: return ""
    val contentLength = body.contentLength()
    if (contentLength > maxChars.toLong()) return ""
    return body.charStream().use { reader ->
      val buf = CharArray(8192)
      val sb = StringBuilder()
      while (sb.length < maxChars) {
        val read = reader.read(buf, 0, minOf(buf.size, maxChars - sb.length))
        if (read <= 0) break
        sb.append(buf, 0, read)
      }
      sb.toString()
    }
  }
}

object YoutubeExtractor {
  private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

  private val client = OkHttpClient.Builder()
    .connectTimeout(10, TimeUnit.SECONDS)
    .readTimeout(20, TimeUnit.SECONDS)
    .followRedirects(true)
    .followSslRedirects(true)
    .build()

  private val newPipeHttpClient = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    .followRedirects(true)
    .followSslRedirects(true)
    .build()

  private val probeClient = OkHttpClient.Builder()
    .connectTimeout(6, TimeUnit.SECONDS)
    .readTimeout(8, TimeUnit.SECONDS)
    .followRedirects(true)
    .followSslRedirects(true)
    .build()

  private data class CachedSearchEntry(
    val items: List<YoutubeSearchItem>,
    val timestampMs: Long
  )

  private val searchCache = Collections.synchronizedMap(
    object : LinkedHashMap<String, CachedSearchEntry>(32, 0.75f, true) {
      override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CachedSearchEntry>?): Boolean {
        return size > 24
      }
    }
  )

  private suspend fun executeCancellable(
    httpClient: OkHttpClient,
    request: Request
  ): Response = suspendCancellableCoroutine { cont ->
    val call = httpClient.newCall(request)
    cont.invokeOnCancellation {
      runCatching { call.cancel() }
    }
    call.enqueue(object : Callback {
      override fun onFailure(call: Call, e: IOException) {
        if (cont.isActive) cont.resumeWithException(e)
      }

      override fun onResponse(call: Call, response: Response) {
        if (cont.isActive) {
          cont.resume(response)
        } else {
          runCatching { response.close() }
        }
      }
    })
  }

  private inline fun <T> cancellableRunCatching(block: () -> T): Result<T> {
    return try {
      Result.success(block())
    } catch (ce: CancellationException) {
      throw ce
    } catch (t: Throwable) {
      Result.failure(t)
    }
  }

  private fun readBoundedBody(resp: Response, maxChars: Int = 2_500_000): String {
    val body = resp.body ?: return ""
    val contentLength = body.contentLength()
    if (contentLength > maxChars.toLong()) return ""
    return body.charStream().use { reader ->
      val buf = CharArray(8192)
      val sb = StringBuilder()
      while (sb.length < maxChars) {
        val read = reader.read(buf, 0, minOf(buf.size, maxChars - sb.length))
        if (read <= 0) break
        sb.append(buf, 0, read)
      }
      sb.toString()
    }
  }

  @Volatile
  private var newPipeInitialized = false

  @Synchronized
  private fun ensureNewPipeInitialized() {
    if (!newPipeInitialized) {
      NewPipe.init(NewPipeOkHttpDownloader(newPipeHttpClient), Localization.DEFAULT)
      newPipeInitialized = true
    }
  }

  private val innerTubeProfiles = listOf(
    InnerTubeClientProfile(
      clientName = "ANDROID_VR",
      clientVersion = "1.61.48",
      userAgent = "com.google.android.apps.youtube.vr.oculus/1.61.48 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip",
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

  fun inferPrimaryUserAgentForUrl(streamUrl: String): String? {
    if (!streamUrl.contains("googlevideo.com", ignoreCase = true) &&
      !streamUrl.contains("youtube.com", ignoreCase = true)
    ) {
      return null
    }
    return inferUserAgentsForStreamUrl(streamUrl).firstOrNull()
  }

  private fun inferUserAgentsForStreamUrl(streamUrl: String): List<String> {
    val clientParam = Regex("""[?&]c=([^&]+)""", RegexOption.IGNORE_CASE)
      .find(streamUrl)
      ?.groupValues
      ?.getOrNull(1)
      ?.uppercase(Locale.US)
      .orEmpty()

    return when (clientParam) {
      "IOS" -> listOf(
        "com.google.ios.youtube/20.10.4 (iPhone16,2; U; CPU iOS 18_3_2 like Mac OS X;)",
        NewPipeOkHttpDownloader.DESKTOP_FIREFOX_UA
      )
      "ANDROID_VR" -> listOf(
        "com.google.android.apps.youtube.vr.oculus/1.61.48 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip",
        NewPipeOkHttpDownloader.DESKTOP_FIREFOX_UA
      )
      "ANDROID", "ANDROID_MUSIC", "ANDROID_TESTSUITE" -> listOf(
        "com.google.android.youtube/20.10.38 (Linux; U; Android 14) gzip",
        NewPipeOkHttpDownloader.DESKTOP_FIREFOX_UA
      )
      else -> listOf(
        NewPipeOkHttpDownloader.DESKTOP_FIREFOX_UA,
        NewPipeOkHttpDownloader.DESKTOP_CHROME_UA,
        "AURIX-Android"
      )
    }.distinct()
  }

  private fun probeRequestOk(req: Request, allow416: Boolean = false): Boolean {
    return runCatching {
      probeClient.newCall(req).execute().use { resp ->
        if (allow416 && resp.code == 416) return@use true
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

  private fun probeModeAtOffsets(
    url: String,
    userAgent: String,
    mode: StreamDownloadMode,
    contentLengthBytes: Long
  ): Boolean {
    val secondOffset = when {
      contentLengthBytes > 600_000L -> 524_288L to 525_311L
      contentLengthBytes <= 0L && mode != StreamDownloadMode.DIRECT -> 262_144L to 263_167L
      else -> null
    }
    val offsets = if (secondOffset != null) {
      listOf(0L to 1023L, secondOffset)
    } else {
      listOf(0L to 1023L)
    }

    return runCatching {
      offsets.mapIndexed { index, (start, end) ->
        val req = when (mode) {
          StreamDownloadMode.QUERY_RANGE -> Request.Builder()
            .url(appendRangeQueryParam(url, start, end))
            .header("User-Agent", userAgent)
            .header("Accept", "*/*")
            .build()
          StreamDownloadMode.HEADER_RANGE -> Request.Builder()
            .url(url)
            .header("User-Agent", userAgent)
            .header("Accept", "*/*")
            .header("Range", "bytes=$start-$end")
            .build()
          StreamDownloadMode.DIRECT -> Request.Builder()
            .url(url)
            .header("User-Agent", userAgent)
            .header("Accept", "*/*")
            .build()
        }
        probeRequestOk(req, allow416 = (index > 0 && contentLengthBytes <= 0L))
      }.all { it }
    }.getOrDefault(false)
  }

  /**
   * Tests a stream URL at BOTH start (0-1023) AND chunk 1 (524288-525311 when >600KB),
   * catching HTTP 403 streams that only allow initial probe bytes before rejecting full chunks.
   */
  fun probeWorkingDownloadMode(
    url: String,
    userAgent: String,
    contentLengthBytes: Long = -1L
  ): StreamDownloadMode? {
    if (url.isBlank()) return null
    val isGoogleVideo = url.contains("googlevideo.com", ignoreCase = true)

    return runCatching {
      if (isGoogleVideo) {
        val cParam = Regex("""[?&]c=([^&]+)""", RegexOption.IGNORE_CASE)
          .find(url)?.groupValues?.getOrNull(1)?.uppercase(Locale.US).orEmpty()
        val orderedModes = if (cParam.startsWith("WEB") || cParam.startsWith("TV") || cParam == "MWEB") {
          listOf(StreamDownloadMode.HEADER_RANGE, StreamDownloadMode.QUERY_RANGE, StreamDownloadMode.DIRECT)
        } else {
          listOf(StreamDownloadMode.QUERY_RANGE, StreamDownloadMode.HEADER_RANGE, StreamDownloadMode.DIRECT)
        }
        orderedModes.firstOrNull { mode ->
          probeModeAtOffsets(url, userAgent, mode, contentLengthBytes)
        }
      } else {
        val hReq = Request.Builder()
          .url(url)
          .header("User-Agent", userAgent)
          .header("Accept", "*/*")
          .header("Range", "bytes=0-1023")
          .build()
        if (probeRequestOk(hReq)) return@runCatching StreamDownloadMode.DIRECT

        val dReq = Request.Builder()
          .url(url)
          .header("User-Agent", userAgent)
          .header("Accept", "*/*")
          .build()
        if (probeRequestOk(dReq)) return@runCatching StreamDownloadMode.DIRECT

        null
      }
    }.getOrNull()
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
        val body = readBoundedBody(resp, 512_000)
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
    if (trimmed.isBlank()) return null

    val parsedUri = runCatching { java.net.URI(trimmed) }.getOrNull()
    val host = parsedUri?.host.orEmpty().lowercase(Locale.US)
    val path = parsedUri?.path.orEmpty()
    val segments = path.split('/').filter { it.isNotBlank() }
    val query = parsedUri?.rawQuery.orEmpty()
    val vQueryParam = Regex("""(?:^|&)v=([^&]+)""")
      .find(query)
      ?.groupValues
      ?.getOrNull(1)

    val rawId = when {
      host.contains("youtu.be") -> segments.firstOrNull()
      !vQueryParam.isNullOrBlank() -> vQueryParam
      segments.size >= 2 && segments[0] in listOf("shorts", "embed", "live", "v") -> segments[1]
      else -> {
        Regex("""(?:youtu\.be/|youtube\.com/(?:watch\?.*v=|shorts/|embed/|live/))([A-Za-z0-9_-]{8,16})""")
          .find(trimmed)
          ?.groupValues
          ?.getOrNull(1)
      }
    }
    return rawId
      ?.substringBefore('?')
      ?.substringBefore('&')
      ?.trim()
      ?.takeIf { it.length in 8..16 }
  }

  fun extractPlaylistId(rawUrl: String): String? {
    val trimmed = rawUrl.trim()
    if (trimmed.isBlank()) return null

    val parsedUri = runCatching { java.net.URI(trimmed) }.getOrNull()
    val query = parsedUri?.rawQuery.orEmpty()
    val fromUri = Regex("""(?:^|&)list=([^&#]+)""")
      .find(query)
      ?.groupValues
      ?.getOrNull(1)
    val rawListId = fromUri ?: Regex("""[?&]list=([A-Za-z0-9_-]{6,120})""")
      .find(trimmed)
      ?.groupValues
      ?.getOrNull(1)

    return rawListId
      ?.substringBefore('&')
      ?.substringBefore('#')
      ?.trim()
      ?.takeIf { it.matches(Regex("""^[A-Za-z0-9_-]{6,120}$""")) }
  }

  fun isDirectPlaylistUrl(rawUrl: String): Boolean {
    val trimmed = rawUrl.trim()
    val listId = extractPlaylistId(trimmed) ?: return false
    if (listId.isBlank()) return false
    val parsedUri = runCatching { java.net.URI(trimmed) }.getOrNull()
    val path = parsedUri?.path.orEmpty().lowercase(Locale.US)
    if (path.contains("/playlist")) return true
    return extractVideoId(trimmed) == null
  }

  suspend fun extractPlaylist(
    rawUrlOrId: String,
    limit: Int = 100
  ): ExtractedYoutubePlaylist? = withContext(Dispatchers.IO) {
    val trimmed = rawUrlOrId.trim()
    val playlistId = extractPlaylistId(trimmed)
      ?: trimmed.takeIf { it.matches(Regex("""^[A-Za-z0-9_-]{10,120}$""")) }
      ?: return@withContext null
    val normalizedUrl = "https://www.youtube.com/playlist?list=$playlistId"

    currentCoroutineContext().ensureActive()
    val npResult = try {
      fetchPlaylistViaNewPipe(playlistId, normalizedUrl, limit)
    } catch (ce: CancellationException) {
      throw ce
    } catch (_: Throwable) {
      null
    }
    if (npResult != null && npResult.tracks.isNotEmpty()) {
      return@withContext npResult
    }

    for (base in basePipedMirrors.take(4)) {
      currentCoroutineContext().ensureActive()
      val piped = try {
        fetchPlaylistViaPiped(base, playlistId, normalizedUrl, limit)
      } catch (ce: CancellationException) {
        throw ce
      } catch (_: Throwable) {
        null
      }
      if (piped != null && piped.tracks.isNotEmpty()) {
        return@withContext piped
      }
    }

    for (base in getInvidiousMirrors().take(4)) {
      currentCoroutineContext().ensureActive()
      val inv = try {
        fetchPlaylistViaInvidious(base, playlistId, normalizedUrl, limit)
      } catch (ce: CancellationException) {
        throw ce
      } catch (_: Throwable) {
        null
      }
      if (inv != null && inv.tracks.isNotEmpty()) {
        return@withContext inv
      }
    }

    null
  }

  private fun fetchPlaylistViaNewPipe(
    playlistId: String,
    normalizedUrl: String,
    limit: Int
  ): ExtractedYoutubePlaylist? {
    ensureNewPipeInitialized()
    val info = PlaylistInfo.getInfo(ServiceList.YouTube, normalizedUrl)
    val collected = LinkedHashMap<String, YoutubeSearchItem>()

    for (stream in info.relatedItems) {
      if (collected.size >= limit) break
      mapStreamInfoItem(stream)?.let { item ->
        collected.putIfAbsent(item.videoId, item)
      }
    }

    var nextPage = info.nextPage
    var pageCount = 0
    while (nextPage != null && collected.size < limit && pageCount < 5) {
      pageCount++
      val more = cancellableRunCatching {
        PlaylistInfo.getMoreItems(ServiceList.YouTube, normalizedUrl, nextPage)
      }.getOrNull() ?: break
      val items = more.items.orEmpty()
      if (items.isEmpty()) break
      for (stream in items) {
        if (collected.size >= limit) break
        mapStreamInfoItem(stream)?.let { item ->
          collected.putIfAbsent(item.videoId, item)
        }
      }
      nextPage = more.nextPage
    }

    val tracks = collected.values.toList()
    if (tracks.isEmpty()) return null

    val thumb = info.thumbnails
      .maxByOrNull { it.width * it.height }
      ?.url
      ?.takeIf { it.isNotBlank() }
      ?: info.banners.maxByOrNull { it.width * it.height }?.url?.takeIf { it.isNotBlank() }
      ?: tracks.firstOrNull()?.thumbnailUrl.orEmpty()

    return ExtractedYoutubePlaylist(
      playlistId = playlistId,
      originalUrl = normalizedUrl,
      title = info.name.orEmpty().trim().ifBlank { "YouTube Playlist" },
      author = info.uploaderName.orEmpty().trim().ifBlank { "YouTube" },
      thumbnailUrl = thumb,
      totalCount = info.streamCount.takeIf { it > 0L } ?: tracks.size.toLong(),
      tracks = tracks
    )
  }

  private suspend fun fetchPlaylistViaPiped(
    base: String,
    playlistId: String,
    normalizedUrl: String,
    limit: Int
  ): ExtractedYoutubePlaylist? {
    val req = cancellableRunCatching {
      Request.Builder()
        .url("$base/playlists/$playlistId")
        .header("User-Agent", "AURIX-Android")
        .build()
    }.getOrNull() ?: return null

    return executeCancellable(probeClient, req).use { resp ->
      if (!resp.isSuccessful) return@use null
      val body = readBoundedBody(resp)
      if (body.isBlank()) return@use null
      val root = JSONObject(body)
      val arr = root.optJSONArray("relatedStreams") ?: return@use null
      val collected = LinkedHashMap<String, YoutubeSearchItem>()
      for (i in 0 until minOf(arr.length(), limit)) {
        val obj = arr.optJSONObject(i) ?: continue
        val itemUrl = obj.optString("url").trim()
        val fullUrl = if (itemUrl.startsWith("http")) itemUrl else "https://www.youtube.com$itemUrl"
        val videoId = extractVideoId(fullUrl) ?: continue
        val title = obj.optString("title").trim()
        if (title.isBlank()) continue
        val durSec = obj.optLong("duration", 0L).coerceAtLeast(0L)
        if (durSec <= 0L) continue
        val thumb = obj.optString("thumbnail").trim()
          .ifBlank { "https://i.ytimg.com/vi/$videoId/hqdefault.jpg" }
        collected.putIfAbsent(
          videoId,
          YoutubeSearchItem(
            videoId = videoId,
            rawTitle = title,
            author = obj.optString("uploaderName").trim().ifBlank { "YouTube" },
            thumbnailUrl = thumb,
            durationSeconds = durSec
          )
        )
      }
      val tracks = collected.values.toList()
      if (tracks.isEmpty()) return@use null
      val playlistThumb = root.optString("thumbnailUrl").trim()
        .ifBlank { tracks.firstOrNull()?.thumbnailUrl.orEmpty() }
      ExtractedYoutubePlaylist(
        playlistId = playlistId,
        originalUrl = normalizedUrl,
        title = root.optString("name").trim().ifBlank { "YouTube Playlist" },
        author = root.optString("uploader").trim().ifBlank { "YouTube" },
        thumbnailUrl = playlistThumb,
        totalCount = root.optLong("videos", tracks.size.toLong()).takeIf { it > 0L } ?: tracks.size.toLong(),
        tracks = tracks
      )
    }
  }

  private suspend fun fetchPlaylistViaInvidious(
    base: String,
    playlistId: String,
    normalizedUrl: String,
    limit: Int
  ): ExtractedYoutubePlaylist? {
    val req = cancellableRunCatching {
      Request.Builder()
        .url("$base/api/v1/playlists/$playlistId")
        .header("User-Agent", "AURIX-Android")
        .build()
    }.getOrNull() ?: return null

    return executeCancellable(probeClient, req).use { resp ->
      if (!resp.isSuccessful) return@use null
      val body = readBoundedBody(resp)
      if (body.isBlank()) return@use null
      val root = JSONObject(body)
      val arr = root.optJSONArray("videos") ?: return@use null
      val collected = LinkedHashMap<String, YoutubeSearchItem>()
      for (i in 0 until minOf(arr.length(), limit)) {
        val obj = arr.optJSONObject(i) ?: continue
        val videoId = obj.optString("videoId").trim().takeIf { it.length in 8..16 } ?: continue
        val title = obj.optString("title").trim()
        if (title.isBlank()) continue
        val durSec = obj.optLong("lengthSeconds", 0L).coerceAtLeast(0L)
        if (durSec <= 0L) continue
        val thumb = bestThumbnailUrl(obj.optJSONArray("videoThumbnails"))
          ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
        collected.putIfAbsent(
          videoId,
          YoutubeSearchItem(
            videoId = videoId,
            rawTitle = title,
            author = obj.optString("author").trim().ifBlank { "YouTube" },
            thumbnailUrl = thumb,
            durationSeconds = durSec
          )
        )
      }
      val tracks = collected.values.toList()
      if (tracks.isEmpty()) return@use null
      val playlistThumb = root.optString("playlistThumbnail").trim()
        .ifBlank { tracks.firstOrNull()?.thumbnailUrl.orEmpty() }
      ExtractedYoutubePlaylist(
        playlistId = playlistId,
        originalUrl = normalizedUrl,
        title = root.optString("title").trim().ifBlank { "YouTube Playlist" },
        author = root.optString("author").trim().ifBlank { "YouTube" },
        thumbnailUrl = playlistThumb,
        totalCount = root.optLong("videoCount", tracks.size.toLong()).takeIf { it > 0L } ?: tracks.size.toLong(),
        tracks = tracks
      )
    }
  }

  suspend fun searchSongs(
    query: String,
    limit: Int = 25
  ): List<YoutubeSearchItem> = withContext(Dispatchers.IO) {
    val trimmed = query.trim()
    if (trimmed.isBlank()) return@withContext emptyList()

    val cacheKey = "${trimmed.lowercase(Locale.US)}:$limit"
    val now = System.currentTimeMillis()
    searchCache[cacheKey]?.let { cached ->
      if (now - cached.timestampMs <= 300_000L && cached.items.isNotEmpty()) {
        return@withContext cached.items
      }
    }

    suspend fun cacheAndReturn(results: List<YoutubeSearchItem>): List<YoutubeSearchItem> {
      if (results.isNotEmpty()) {
        searchCache[cacheKey] = CachedSearchEntry(results, System.currentTimeMillis())
      }
      return results
    }

    try {
      currentCoroutineContext().ensureActive()

      // 1. Direct InnerTube Search (fast, lightweight JSON, immediately cancellable)
      val innerTubeResults = try {
        searchViaInnerTube(trimmed, limit)
      } catch (ce: CancellationException) {
        throw ce
      } catch (_: Throwable) {
        emptyList()
      }
      if (innerTubeResults.isNotEmpty()) return@withContext cacheAndReturn(innerTubeResults)

      currentCoroutineContext().ensureActive()

      // 2. NewPipeExtractor YouTube Search
      val newPipeResults = try {
        searchViaNewPipe(trimmed, limit)
      } catch (ce: CancellationException) {
        throw ce
      } catch (_: Throwable) {
        emptyList()
      }
      if (newPipeResults.isNotEmpty()) return@withContext cacheAndReturn(newPipeResults)

      // 3. Piped public mirrors
      for (base in basePipedMirrors.take(4)) {
        currentCoroutineContext().ensureActive()
        val pipedResults = try {
          searchViaPiped(base, trimmed, limit)
        } catch (ce: CancellationException) {
          throw ce
        } catch (_: Throwable) {
          emptyList()
        }
        if (pipedResults.isNotEmpty()) return@withContext cacheAndReturn(pipedResults)
      }

      // 4. Invidious public mirrors
      for (base in getInvidiousMirrors().take(4)) {
        currentCoroutineContext().ensureActive()
        val invResults = try {
          searchViaInvidious(base, trimmed, limit)
        } catch (ce: CancellationException) {
          throw ce
        } catch (_: Throwable) {
          emptyList()
        }
        if (invResults.isNotEmpty()) return@withContext cacheAndReturn(invResults)
      }

      emptyList()
    } catch (ce: CancellationException) {
      throw ce
    } catch (_: Throwable) {
      emptyList()
    }
  }

  /**
   * Fetches trending tracks from TikTok-related YouTube music charts & playlists
   * primarily using NewPipeExtractor (SearchInfo + PlaylistInfo), with InnerTube/mirror fallback.
   */
  suspend fun fetchTikTokViralChartTracks(
    limit: Int = 28
  ): List<YoutubeSearchItem> = withContext(Dispatchers.IO) {
    val collected = LinkedHashMap<String, YoutubeSearchItem>()

    // 1. Discover TikTok viral music chart playlists via NewPipe SearchInfo + PlaylistInfo
    cancellableRunCatching {
      ensureNewPipeInitialized()
      val playlistQueries = listOf(
        "TikTok Viral Hits Chart 2026 playlist",
        "TikTok Trending Songs Chart official playlist"
      )
      for (pq in playlistQueries) {
        if (collected.size >= limit) break
        currentCoroutineContext().ensureActive()
        val qh = cancellableRunCatching {
          ServiceList.YouTube.searchQHFactory.fromQuery(pq, listOf("playlists"), "")
        }.getOrNull() ?: ServiceList.YouTube.searchQHFactory.fromQuery(pq)
        val searchInfo = cancellableRunCatching { SearchInfo.getInfo(ServiceList.YouTube, qh) }.getOrNull() ?: continue
        val playlistUrls = searchInfo.relatedItems
          .filterIsInstance<PlaylistInfoItem>()
          .mapNotNull { it.url?.takeIf { u -> u.isNotBlank() } }
          .take(2)

        for (pUrl in playlistUrls) {
          if (collected.size >= limit) break
          currentCoroutineContext().ensureActive()
          val playlistInfo = cancellableRunCatching { PlaylistInfo.getInfo(ServiceList.YouTube, pUrl) }.getOrNull() ?: continue
          for (stream in playlistInfo.relatedItems) {
            if (collected.size >= limit) break
            mapStreamInfoItem(stream)?.let { ytItem ->
              collected.putIfAbsent(ytItem.videoId, ytItem)
            }
          }
        }
      }
    }

    // 2. Supplement via direct NewPipeExtractor SearchInfo across TikTok chart queries
    val chartQueries = listOf(
      "TikTok Viral Hits Chart 2026 official audio",
      "TikTok Trending Music Chart songs official",
      "TikTok Top Viral Songs official audio"
    )
    for (q in chartQueries) {
      if (collected.size >= limit) break
      currentCoroutineContext().ensureActive()
      val npItems = cancellableRunCatching { searchViaNewPipe(q, 14) }.getOrNull().orEmpty()
      for (item in npItems) {
        collected.putIfAbsent(item.videoId, item)
        if (collected.size >= limit) break
      }
    }

    // 3. Fallback to InnerTube / mirrors only if NewPipe could not reach enough tracks
    if (collected.size < 8) {
      for (q in chartQueries) {
        if (collected.size >= limit) break
        currentCoroutineContext().ensureActive()
        val fallbackItems = cancellableRunCatching { searchSongs(q, 14) }.getOrNull().orEmpty()
        for (item in fallbackItems) {
          collected.putIfAbsent(item.videoId, item)
          if (collected.size >= limit) break
        }
      }
    }

    collected.values.take(limit)
  }

  private suspend fun searchViaInnerTube(query: String, limit: Int): List<YoutubeSearchItem> {
    currentCoroutineContext().ensureActive()
    val clientObj = JSONObject().apply {
      put("clientName", "WEB")
      put("clientVersion", "2.20250201.00.00")
      put("hl", "en")
      put("gl", "US")
    }
    val payload = JSONObject().apply {
      put("query", query)
      put("context", JSONObject().put("client", clientObj))
    }
    val request = cancellableRunCatching {
      Request.Builder()
        .url("https://www.youtube.com/youtubei/v1/search?prettyPrint=false")
        .post(payload.toString().toRequestBody(jsonMediaType))
        .header("User-Agent", NewPipeOkHttpDownloader.DESKTOP_CHROME_UA)
        .header("Content-Type", "application/json")
        .build()
    }.getOrNull() ?: return emptyList()

    return cancellableRunCatching {
      executeCancellable(client, request).use { resp ->
        if (!resp.isSuccessful) return emptyList()
        val body = readBoundedBody(resp)
        if (body.isBlank()) return emptyList()
        currentCoroutineContext().ensureActive()
        val root = JSONObject(body)
        val collected = mutableListOf<YoutubeSearchItem>()
        val visitedNodes = intArrayOf(0)
        collectVideoRenderers(root, collected, limit, depth = 0, visitedNodes = visitedNodes)
        collected.distinctBy { it.videoId }.take(limit)
      }
    }.getOrDefault(emptyList())
  }

  private fun collectVideoRenderers(
    node: Any?,
    out: MutableList<YoutubeSearchItem>,
    limit: Int,
    depth: Int = 0,
    visitedNodes: IntArray = intArrayOf(0)
  ) {
    if (depth > 28 || out.size >= limit || node == null || visitedNodes[0] > 6000) return
    visitedNodes[0]++
    when (node) {
      is JSONObject -> {
        val vr = node.optJSONObject("videoRenderer")
        if (vr != null) {
          parseVideoRenderer(vr)?.let { out.add(it) }
        }
        val keys = node.keys()
        while (keys.hasNext() && out.size < limit && visitedNodes[0] <= 6000) {
          val k = keys.next()
          if (k != "videoRenderer") {
            collectVideoRenderers(node.opt(k), out, limit, depth + 1, visitedNodes)
          }
        }
      }
      is JSONArray -> {
        for (i in 0 until node.length()) {
          if (out.size >= limit || visitedNodes[0] > 6000) break
          collectVideoRenderers(node.opt(i), out, limit, depth + 1, visitedNodes)
        }
      }
    }
  }

  private fun parseVideoRenderer(vr: JSONObject): YoutubeSearchItem? {
    val videoId = vr.optString("videoId").trim().takeIf { it.length in 8..16 } ?: return null
    val title = extractRunsOrSimpleText(vr.optJSONObject("title")).trim()
    if (title.isBlank()) return null

    val author = extractRunsOrSimpleText(vr.optJSONObject("ownerText"))
      .ifBlank { extractRunsOrSimpleText(vr.optJSONObject("longBylineText")) }
      .ifBlank { extractRunsOrSimpleText(vr.optJSONObject("shortBylineText")) }
      .trim()

    val lengthText = extractRunsOrSimpleText(vr.optJSONObject("lengthText"))
    val durationSec = parseDurationTextSeconds(lengthText)
    // Skip live streams without fixed duration
    if (durationSec <= 0L && lengthText.isBlank()) return null

    val thumb = bestThumbnailUrl(vr.optJSONObject("thumbnail")?.optJSONArray("thumbnails"))
      ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"

    return YoutubeSearchItem(
      videoId = videoId,
      rawTitle = title,
      author = author.ifBlank { "YouTube" },
      thumbnailUrl = thumb,
      durationSeconds = durationSec
    )
  }

  private fun extractRunsOrSimpleText(obj: JSONObject?): String {
    if (obj == null) return ""
    val simple = obj.optString("simpleText").trim()
    if (simple.isNotBlank()) return simple
    val runs = obj.optJSONArray("runs") ?: return ""
    val sb = StringBuilder()
    for (i in 0 until runs.length()) {
      sb.append(runs.optJSONObject(i)?.optString("text").orEmpty())
    }
    return sb.toString()
  }

  private fun searchViaNewPipe(query: String, limit: Int): List<YoutubeSearchItem> {
    ensureNewPipeInitialized()
    val queryHandler = ServiceList.YouTube.searchQHFactory.fromQuery(query)
    val searchInfo = SearchInfo.getInfo(ServiceList.YouTube, queryHandler)
    val out = mutableListOf<YoutubeSearchItem>()
    for (item in searchInfo.relatedItems) {
      if (out.size >= limit) break
      if (item !is StreamInfoItem) continue
      mapStreamInfoItem(item)?.let { out.add(it) }
    }
    return out.distinctBy { it.videoId }
  }

  private fun mapStreamInfoItem(item: StreamInfoItem): YoutubeSearchItem? {
    if (item.streamType == StreamType.LIVE_STREAM || item.streamType == StreamType.AUDIO_LIVE_STREAM) {
      return null
    }
    val videoId = extractVideoId(item.url.orEmpty()) ?: return null
    val title = item.name.orEmpty().trim()
    if (title.isBlank()) return null
    val durSec = item.duration.coerceAtLeast(0L)
    if (durSec <= 0L) return null
    val thumb = item.thumbnails
      .maxByOrNull { it.width * it.height }
      ?.url
      ?.takeIf { it.isNotBlank() }
      ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
    return YoutubeSearchItem(
      videoId = videoId,
      rawTitle = title,
      author = item.uploaderName.orEmpty().trim().ifBlank { "YouTube" },
      thumbnailUrl = thumb,
      durationSeconds = durSec
    )
  }

  private suspend fun searchViaPiped(base: String, query: String, limit: Int): List<YoutubeSearchItem> {
    val encoded = URLEncoder.encode(query, "UTF-8")
    for (filter in listOf("music_songs", "videos")) {
      currentCoroutineContext().ensureActive()
      val req = cancellableRunCatching {
        Request.Builder()
          .url("$base/search?q=$encoded&filter=$filter")
          .header("User-Agent", "AURIX-Android")
          .build()
      }.getOrNull() ?: continue
      val items = try {
        executeCancellable(probeClient, req).use { resp ->
          if (!resp.isSuccessful) return@use emptyList<YoutubeSearchItem>()
          val body = readBoundedBody(resp)
          if (body.isBlank()) return@use emptyList<YoutubeSearchItem>()
          val arr = JSONObject(body).optJSONArray("items") ?: return@use emptyList<YoutubeSearchItem>()
          val out = mutableListOf<YoutubeSearchItem>()
          for (i in 0 until minOf(arr.length(), limit)) {
            val obj = arr.optJSONObject(i) ?: continue
            val itemUrl = obj.optString("url").trim()
            val fullUrl = if (itemUrl.startsWith("http")) itemUrl else "https://www.youtube.com$itemUrl"
            val videoId = extractVideoId(fullUrl) ?: continue
            val title = obj.optString("title").trim()
            if (title.isBlank()) continue
            val durSec = obj.optLong("duration", 0L)
            if (durSec <= 0L) continue
            val thumb = obj.optString("thumbnail").trim()
              .ifBlank { "https://i.ytimg.com/vi/$videoId/hqdefault.jpg" }
            out.add(
              YoutubeSearchItem(
                videoId = videoId,
                rawTitle = title,
                author = obj.optString("uploaderName").trim().ifBlank { "YouTube" },
                thumbnailUrl = thumb,
                durationSeconds = durSec
              )
            )
          }
          out
        }
      } catch (ce: CancellationException) {
        throw ce
      } catch (_: Throwable) {
        emptyList()
      }
      if (items.isNotEmpty()) return items.distinctBy { it.videoId }
    }
    return emptyList()
  }

  private suspend fun searchViaInvidious(base: String, query: String, limit: Int): List<YoutubeSearchItem> {
    currentCoroutineContext().ensureActive()
    val encoded = URLEncoder.encode(query, "UTF-8")
    val req = cancellableRunCatching {
      Request.Builder()
        .url("$base/api/v1/search?q=$encoded&type=video")
        .header("User-Agent", "AURIX-Android")
        .build()
    }.getOrNull() ?: return emptyList()
    return cancellableRunCatching {
      executeCancellable(probeClient, req).use { resp ->
        if (!resp.isSuccessful) return emptyList()
        val body = readBoundedBody(resp)
        if (body.isBlank()) return emptyList()
        val arr = JSONArray(body)
        val out = mutableListOf<YoutubeSearchItem>()
        for (i in 0 until minOf(arr.length(), limit)) {
          val obj = arr.optJSONObject(i) ?: continue
          val videoId = obj.optString("videoId").trim().takeIf { it.length in 8..16 } ?: continue
          val title = obj.optString("title").trim()
          if (title.isBlank()) continue
          val durSec = obj.optLong("lengthSeconds", 0L)
          if (durSec <= 0L) continue
          val thumb = bestThumbnailUrl(obj.optJSONArray("videoThumbnails"))
            ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
          out.add(
            YoutubeSearchItem(
              videoId = videoId,
              rawTitle = title,
              author = obj.optString("author").trim().ifBlank { "YouTube" },
              thumbnailUrl = thumb,
              durationSeconds = durSec
            )
          )
        }
        out.distinctBy { it.videoId }
      }
    }.getOrDefault(emptyList())
  }

  fun parseDurationTextSeconds(raw: String): Long {
    val clean = raw.trim()
    if (clean.isBlank()) return 0L
    val parts = clean.split(':').mapNotNull { it.trim().toLongOrNull() }
    if (parts.isEmpty()) return 0L
    return when (parts.size) {
      2 -> parts[0] * 60L + parts[1]
      3 -> parts[0] * 3600L + parts[1] * 60L + parts[2]
      else -> 0L
    }
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

    // 1. Primary: NewPipeExtractor (full JS signature & n-parameter deobfuscation + client rotation)
    if ("NEWPIPE" !in skipClients) {
      currentCoroutineContext().ensureActive()
      val npAttempt = cancellableRunCatching { fetchFromNewPipeExtractor(videoId) }.getOrNull()
      if (npAttempt != null) {
        if (npAttempt.title.isNotBlank()) lastTitle = npAttempt.title
        if (npAttempt.author.isNotBlank()) lastAuthor = npAttempt.author
        if (npAttempt.thumbnailUrl.isNotBlank()) lastThumb = npAttempt.thumbnailUrl
        if (npAttempt.durationSeconds > 0L) lastDurationSec = npAttempt.durationSeconds
        if (!npAttempt.extractorError.isNullOrBlank()) lastReason = npAttempt.extractorError

        val verifiedNp = verifyFormatList(npAttempt.adaptiveFormats)
        if (verifiedNp.isNotEmpty()) {
          return@withContext ExtractedYoutubeData(
            videoId = videoId,
            title = lastTitle,
            author = lastAuthor,
            thumbnailUrl = lastThumb,
            durationSeconds = lastDurationSec,
            audioFormats = verifiedNp,
            extractorError = null
          )
        }
        progressiveBackups.addAll(npAttempt.progressiveFormats)
      }
    }

    // 2. Direct InnerTube clients (verified at both byte 0 and chunk 1)
    for (profile in innerTubeProfiles) {
      if (profile.clientName in skipClients) continue
      currentCoroutineContext().ensureActive()
      val attempt = cancellableRunCatching { fetchFromInnerTube(videoId, profile) }.getOrNull() ?: continue
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

    // 3. Piped public instances (server-side deciphered & proxied)
    for (base in basePipedMirrors) {
      currentCoroutineContext().ensureActive()
      val attempt = cancellableRunCatching { fetchFromPiped(base, videoId) }.getOrNull() ?: continue
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

    // 4. Invidious public instances (checks direct URL AND Invidious `local=true` server proxy)
    for (base in getInvidiousMirrors()) {
      currentCoroutineContext().ensureActive()
      val attempt = cancellableRunCatching { fetchFromInvidious(base, videoId) }.getOrNull() ?: continue
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

    // 5. Direct Invidious local=true proxy endpoints
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

    // 6. Progressive MP4 fallback (itag=18)
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
   * Used when a selected format hits 403 during download: resolves a fresh verified format
   * from an alternative client/mirror matching the requested extension or best working audio format.
   */
  suspend fun extractFreshWorkingFormat(
    videoId: String,
    preferredExtension: String,
    failedFormatId: String? = null
  ): AudioFormatOption? = withContext(Dispatchers.IO) {
    val skipSet = buildSet {
      if (failedFormatId?.startsWith("np_", ignoreCase = true) == true) {
        add("NEWPIPE")
      }
      innerTubeProfiles.forEach { profile ->
        if (failedFormatId?.contains("_${profile.clientName.lowercase(Locale.US)}_", ignoreCase = true) == true) {
          add(profile.clientName)
        }
      }
    }
    val fresh = extractFullAudio(videoId, skipClients = skipSet)
      ?: if (skipSet.isNotEmpty()) extractFullAudio(videoId) else null
    fresh?.audioFormats?.firstOrNull { it.extension.equals(preferredExtension, ignoreCase = true) }
      ?: fresh?.audioFormats?.firstOrNull()
  }

  private fun fetchFromNewPipeExtractor(videoId: String): InnerTubeAttempt {
    ensureNewPipeInitialized()
    val watchUrl = "https://www.youtube.com/watch?v=$videoId"
    val info = StreamInfo.getInfo(ServiceList.YouTube, watchUrl)

    val title = info.name.orEmpty().trim()
    val author = info.uploaderName.orEmpty().trim()
    val durSec = info.duration.coerceAtLeast(0L)
    val thumbUrl = info.thumbnails
      .maxByOrNull { it.width * it.height }
      ?.url
      ?.takeIf { it.isNotBlank() }
      ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"

    val parsedAdaptive = mutableListOf<AudioFormatOption>()
    info.audioStreams.forEachIndexed { idx, stream ->
      if (stream.deliveryMethod != DeliveryMethod.PROGRESSIVE_HTTP) return@forEachIndexed
      val url = stream.content.orEmpty().trim()
      if (url.isBlank()) return@forEachIndexed

      val mediaFmt = stream.format
      val isM4a = mediaFmt == MediaFormat.M4A || mediaFmt?.suffix.equals("m4a", ignoreCase = true)
      val isMp3 = mediaFmt == MediaFormat.MP3 || mediaFmt?.suffix.equals("mp3", ignoreCase = true)
      val ext = when {
        isM4a -> "m4a"
        isMp3 -> "mp3"
        else -> "webm"
      }
      val fmtLabel = ext.uppercase(Locale.US)
      val codecName = when {
        isM4a -> "AAC"
        isMp3 -> "MP3"
        else -> "Opus"
      }
      val bitrate = stream.averageBitrate.let { if (it > 0) it else 128 }
      val contentLength = stream.itagItem?.contentLength?.takeIf { it > 0L } ?: -1L
      val kbpsLabel = "$bitrate kbps $codecName"
      val durFormatted = formatDurationShort(durSec)
      val sizeText = if (contentLength > 0L) {
        String.format(Locale.US, " • %.1f MB", contentLength / (1024f * 1024f))
      } else ""

      val candidateUas = inferUserAgentsForStreamUrl(url)
      val primaryUa = candidateUas.firstOrNull() ?: NewPipeOkHttpDownloader.DESKTOP_FIREFOX_UA
      val extraBackups = candidateUas.drop(1).map { ua ->
        StreamCandidate(url = url, userAgent = ua, downloadMode = StreamDownloadMode.HEADER_RANGE)
      }

      parsedAdaptive.add(
        AudioFormatOption(
          id = "np_${stream.itag}_${bitrate}_$idx",
          formatLabel = fmtLabel,
          qualityLabel = kbpsLabel,
          detailLabel = "Full Song ($durFormatted)$sizeText",
          extension = ext,
          mimeType = mediaFmt?.mimeType ?: if (isM4a) "audio/mp4" else "audio/webm",
          streamUrl = url,
          durationSeconds = durSec,
          contentLengthBytes = contentLength,
          requiredUserAgent = primaryUa,
          fallbackCandidates = extraBackups
        )
      )
    }

    val sortedAdaptive = parsedAdaptive
      .sortedWith(
        compareByDescending<AudioFormatOption> { it.extension == "m4a" }
          .thenByDescending { extractBitrateNumber(it.qualityLabel) }
      )
      .distinctBy { "${it.formatLabel}_${it.qualityLabel}" }

    val parsedProgressive = mutableListOf<AudioFormatOption>()
    info.videoStreams.forEachIndexed { idx, vStream ->
      if (vStream.deliveryMethod != DeliveryMethod.PROGRESSIVE_HTTP) return@forEachIndexed
      val url = vStream.content.orEmpty().trim()
      if (url.isBlank()) return@forEachIndexed
      val contentLength = vStream.itagItem?.contentLength?.takeIf { it > 0L } ?: -1L
      val durFormatted = formatDurationShort(durSec)
      val sizeText = if (contentLength > 0L) {
        String.format(Locale.US, " • %.1f MB", contentLength / (1024f * 1024f))
      } else ""
      val candidateUas = inferUserAgentsForStreamUrl(url)
      parsedProgressive.add(
        AudioFormatOption(
          id = "np_prog_${vStream.itag}_$idx",
          formatLabel = "MP4",
          qualityLabel = "128 kbps AAC (Standard)",
          detailLabel = "Full Song ($durFormatted)$sizeText",
          extension = "mp4",
          mimeType = "audio/mp4",
          streamUrl = url,
          durationSeconds = durSec,
          contentLengthBytes = contentLength,
          requiredUserAgent = candidateUas.firstOrNull() ?: NewPipeOkHttpDownloader.DESKTOP_FIREFOX_UA,
          fallbackCandidates = candidateUas.drop(1).map { ua ->
            StreamCandidate(url = url, userAgent = ua, downloadMode = StreamDownloadMode.HEADER_RANGE)
          }
        )
      )
    }

    return InnerTubeAttempt(
      title = title,
      author = author,
      thumbnailUrl = thumbUrl,
      durationSeconds = durSec,
      adaptiveFormats = sortedAdaptive,
      progressiveFormats = parsedProgressive,
      extractorError = null
    )
  }

  private fun verifyFormatList(formats: List<AudioFormatOption>): List<AudioFormatOption> {
    if (formats.isEmpty()) return emptyList()

    val verified = mutableListOf<AudioFormatOption>()
    // Group by extension ("m4a", "webm", "mp4") so a block on M4A doesn't hide working WEBM, or vice versa.
    val modeByExt = mutableMapOf<String, Pair<StreamDownloadMode, String>?>()

    for (fmt in formats) {
      val extKey = fmt.extension.lowercase(Locale.US)
      if (!modeByExt.containsKey(extKey)) {
        val primaryUa = fmt.requiredUserAgent ?: NewPipeOkHttpDownloader.DESKTOP_FIREFOX_UA
        var foundMode = probeWorkingDownloadMode(fmt.streamUrl, primaryUa, fmt.contentLengthBytes)
        var workingUrl = fmt.streamUrl
        var workingUa = primaryUa

        if (foundMode == null) {
          for (cand in fmt.fallbackCandidates) {
            val candMode = probeWorkingDownloadMode(cand.url, cand.userAgent, fmt.contentLengthBytes)
            if (candMode != null) {
              foundMode = candMode
              workingUrl = cand.url
              workingUa = cand.userAgent
              break
            }
          }
        }
        modeByExt[extKey] = foundMode?.let { it to workingUa }
        if (foundMode != null) {
          StreamHeaderRegistry.register(workingUrl, workingUa)
          verified.add(
            fmt.copy(
              streamUrl = workingUrl,
              requiredUserAgent = workingUa,
              downloadMode = foundMode
            )
          )
        }
      } else {
        val extResolved = modeByExt[extKey]
        if (extResolved != null) {
          val (extMode, extUa) = extResolved
          val resolvedUa = fmt.requiredUserAgent ?: extUa
          StreamHeaderRegistry.register(fmt.streamUrl, resolvedUa)
          verified.add(fmt.copy(requiredUserAgent = resolvedUa, downloadMode = extMode))
        }
      }
    }

    return verified
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
        val mode = probeWorkingDownloadMode(proxyUrl, "AURIX-Android", -1L)
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
      .build()

    client.newCall(request).execute().use { resp ->
      if (!resp.isSuccessful) return null
      val body = readBoundedBody(resp)
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
      val body = readBoundedBody(resp)
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
      val body = readBoundedBody(resp)
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
