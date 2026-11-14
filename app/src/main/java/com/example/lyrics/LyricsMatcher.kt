package com.example.lyrics

import android.content.Context
import android.net.Uri
import com.example.data.LrcLibApiService
import com.example.data.LrcLibResponse
import com.example.data.NetworkClient
import com.example.model.LyricLine
import com.example.model.LyricsResult
import com.example.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.File
import java.io.IOException
import java.nio.charset.Charset
import java.util.Locale
import kotlin.math.abs

sealed interface LyricsMatchOutcome {
  data class Verified(val lyricsResult: LyricsResult, val isLocal: Boolean = false) : LyricsMatchOutcome
  data object NoLyrics : LyricsMatchOutcome
  data object NetworkError : LyricsMatchOutcome
}

data class ScoredLyricsMatch(
  val candidate: LrcLibResponse,
  val titleSimilarity: Double,
  val artistSimilarity: Double,
  val durationScore: Double,
  val totalScore: Double,
  val hasSynced: Boolean
)

object LyricsMatcher {
  private val BRACKET_CONTENT_REGEX = Regex("""\([^)]*\)|\[[^\]]*]""")
  private val FEAT_WORD_REGEX = Regex("""\b(?:feat|ft)\.?\b""", RegexOption.IGNORE_CASE)
  private val FEAT_TAIL_REGEX = Regex("""\b(?:feat|ft)\.?\s+.*$""", RegexOption.IGNORE_CASE)
  private val NOISE_WORDS_REGEX = Regex(
    """\b(?:official|video|audio|lyrics|lyric|mv|m/v|hd|4k|hq)\b""",
    RegexOption.IGNORE_CASE
  )
  // Keeps Unicode letters (\p{L}), combining marks (\p{M}) needed for Khmer vowels/coeng, digits (\p{N}), and spaces
  private val PUNCTUATION_REGEX = Regex("""[^\p{L}\p{M}\p{N}\s]""")
  private val MULTI_SPACE_REGEX = Regex("""\s+""")

  fun normalizeText(raw: String?): String {
    if (raw.isNullOrBlank()) return ""
    var cleaned = raw.lowercase(Locale.ROOT)
    // Remove (...) and [...] sections iteratively in case of nested brackets
    var prev: String
    do {
      prev = cleaned
      cleaned = BRACKET_CONTENT_REGEX.replace(cleaned, " ")
    } while (cleaned != prev)

    cleaned = FEAT_WORD_REGEX.replace(cleaned, " ")
    cleaned = NOISE_WORDS_REGEX.replace(cleaned, " ")
    cleaned = PUNCTUATION_REGEX.replace(cleaned, " ")
    cleaned = MULTI_SPACE_REGEX.replace(cleaned, " ").trim()
    return cleaned
  }

  fun cleanForApiQuery(raw: String?): String {
    val nonNullRaw = raw?.takeIf { it.isNotBlank() } ?: return ""
    var cleaned = nonNullRaw
    var prev: String
    do {
      prev = cleaned
      cleaned = BRACKET_CONTENT_REGEX.replace(cleaned, " ")
    } while (cleaned != prev)
    cleaned = FEAT_TAIL_REGEX.replace(cleaned, " ")
    cleaned = NOISE_WORDS_REGEX.replace(cleaned, " ")
    cleaned = MULTI_SPACE_REGEX.replace(cleaned, " ").trim()
    return cleaned.ifBlank { nonNullRaw.trim() }
  }

  fun songIdentityKey(song: Song): String = songIdentityKey(song.title, song.artist, song.id)

  fun songIdentityKey(title: String, artist: String, fallbackId: String = ""): String {
    val normArtist = normalizeText(artist)
    val normTitle = normalizeText(title)
    val key = "$normArtist|$normTitle"
    return if (normArtist.isBlank() && normTitle.isBlank()) fallbackId else key
  }

  fun similarity(rawA: String?, rawB: String?): Double {
    val a = normalizeText(rawA)
    val b = normalizeText(rawB)
    if (a.isBlank() || b.isBlank()) return 0.0
    if (a == b) return 1.0
    if (a.contains(b) || b.contains(a)) return 0.85

    val wordsA = a.split(' ').filter { it.isNotEmpty() }.toSet()
    val wordsB = b.split(' ').filter { it.isNotEmpty() }.toSet()
    if (wordsA.isEmpty() || wordsB.isEmpty()) return 0.0

    val intersection = wordsA.intersect(wordsB).size.toDouble()
    val union = wordsA.union(wordsB).size.toDouble()
    return if (union > 0.0) intersection / union else 0.0
  }

  fun resolveKnownFullDurationSeconds(song: Song): Double? {
    val isLocalOrFull = song.source == "Local" ||
      song.source == "Imported" ||
      song.source == "Downloaded" ||
      song.streamUrl.startsWith("file://") ||
      song.streamUrl.startsWith("content://")
    return if (isLocalOrFull) {
      song.fullDurationSeconds.takeIf { it > 0L }?.toDouble()
        ?: song.durationSeconds.takeIf { it > 0L }?.toDouble()
    } else {
      song.fullDurationSeconds.takeIf { it > 35L }?.toDouble()
        ?: song.durationSeconds.takeIf { it > 35L }?.toDouble()
    }
  }

  fun evaluateCandidate(
    song: Song,
    candidate: LrcLibResponse,
    overrideDurationSec: Double? = null
  ): ScoredLyricsMatch? {
    if (candidate.instrumental) return null
    val hasSynced = !candidate.syncedLyrics.isNullOrBlank()
    val hasPlain = !candidate.plainLyrics.isNullOrBlank()
    if (!hasSynced && !hasPlain) return null

    val titleSim = similarity(song.title, candidate.trackName)
    if (titleSim < 0.6) return null

    val artistSim = similarity(song.artist, candidate.artistName)
    if (artistSim < 0.4) return null

    val songDurSec = overrideDurationSec?.takeIf { it > 0.0 } ?: resolveKnownFullDurationSeconds(song)
    val candDurSec = candidate.duration?.takeIf { it > 0.0 }
    val durationScore = if (songDurSec != null && candDurSec != null) {
      val diff = abs(songDurSec - candDurSec)
      if (diff > 10.0) return null
      (1.0 - diff / 10.0).coerceIn(0.0, 1.0)
    } else {
      0.5
    }

    val totalScore = (titleSim * 0.50) + (artistSim * 0.30) + (durationScore * 0.20)
    if (totalScore < 0.75) return null

    return ScoredLyricsMatch(
      candidate = candidate,
      titleSimilarity = titleSim,
      artistSimilarity = artistSim,
      durationScore = durationScore,
      totalScore = totalScore,
      hasSynced = hasSynced
    )
  }

  fun selectBestCandidate(
    song: Song,
    candidates: List<LrcLibResponse>,
    overrideDurationSec: Double? = null
  ): LrcLibResponse? {
    return candidates
      .mapNotNull { evaluateCandidate(song, it, overrideDurationSec) }
      .sortedWith(
        compareByDescending<ScoredLyricsMatch> { it.totalScore }
          .thenByDescending { it.hasSynced }
      )
      .firstOrNull()
      ?.candidate
  }

  fun toLyricsResult(
    candidate: LrcLibResponse,
    fallbackTitle: String,
    fallbackArtist: String
  ): LyricsResult? {
    val syncedRaw = candidate.syncedLyrics?.takeIf { it.isNotBlank() }
    val plainRaw = candidate.plainLyrics?.takeIf { it.isNotBlank() }

    if (syncedRaw != null) {
      val doc = LrcParser.parse(syncedRaw)
      if (doc.isSynced && doc.lines.isNotEmpty()) {
        return LyricsResult(
          synced = doc.lines.map { LyricLine(it.timestampMs, it.text) },
          plain = plainRaw,
          matchedTitle = candidate.trackName ?: fallbackTitle,
          matchedArtist = candidate.artistName ?: fallbackArtist,
          rawLrc = syncedRaw
        )
      }
    }

    if (plainRaw != null) {
      return LyricsResult(
        synced = null,
        plain = plainRaw,
        matchedTitle = candidate.trackName ?: fallbackTitle,
        matchedArtist = candidate.artistName ?: fallbackArtist,
        rawLrc = plainRaw
      )
    }

    return null
  }

  suspend fun findVerifiedLyrics(song: Song): LyricsResult? {
    return (matchWithOutcome(song) as? LyricsMatchOutcome.Verified)?.lyricsResult
  }

  suspend fun matchWithOutcome(
    song: Song,
    context: Context? = null,
    overrideDurationSec: Double? = null,
    api: LrcLibApiService = NetworkClient.lrcLibApi
  ): LyricsMatchOutcome = withContext(Dispatchers.IO) {
    if (context != null) {
      val localLyrics = readLocalLyricsIfPresent(context, song)
      if (localLyrics != null) {
        return@withContext LyricsMatchOutcome.Verified(localLyrics, isLocal = true)
      }
    }

    val cleanTrack = cleanForApiQuery(song.title)
    val cleanArtist = cleanForApiQuery(song.artist)
    if (cleanTrack.isBlank() || cleanArtist.isBlank()) {
      return@withContext LyricsMatchOutcome.NoLyrics
    }

    val primaryQueryArtist = cleanArtist
      .substringBefore(',')
      .substringBefore('&')
      .trim()
      .ifBlank { cleanArtist }

    val allCandidates = mutableListOf<LrcLibResponse>()
    var sawNetworkFailure = false

    suspend fun querySearch(track: String, artist: String) {
      if (track.isBlank() || artist.isBlank()) return
      try {
        val res = api.searchLyricsByTrackAndArtist(trackName = track, artistName = artist)
        if (!res.isNullOrEmpty()) {
          allCandidates.addAll(res)
        }
      } catch (http: HttpException) {
        if (http.code() != 404) {
          sawNetworkFailure = true
        }
      } catch (_: IOException) {
        sawNetworkFailure = true
      } catch (_: Exception) {
        sawNetworkFailure = true
      }
    }

    querySearch(cleanTrack, cleanArtist)

    if (allCandidates.isEmpty() && primaryQueryArtist != cleanArtist) {
      querySearch(cleanTrack, primaryQueryArtist)
    }

    if (allCandidates.isEmpty() && (cleanTrack != song.title.trim() || cleanArtist != song.artist.trim())) {
      querySearch(song.title.trim(), song.artist.trim())
    }

    val uniqueCandidates = allCandidates.distinctBy {
      "${it.id}_${it.trackName.orEmpty()}_${it.artistName.orEmpty()}_${it.duration ?: 0.0}"
    }

    if (uniqueCandidates.isEmpty()) {
      return@withContext if (sawNetworkFailure) LyricsMatchOutcome.NetworkError else LyricsMatchOutcome.NoLyrics
    }

    val best = selectBestCandidate(song, uniqueCandidates, overrideDurationSec)
      ?: return@withContext LyricsMatchOutcome.NoLyrics

    val parsed = toLyricsResult(best, song.title, song.artist)
      ?: return@withContext LyricsMatchOutcome.NoLyrics

    LyricsMatchOutcome.Verified(parsed, isLocal = false)
  }

  fun readLocalLyricsIfPresent(context: Context, song: Song): LyricsResult? {
    val url = song.streamUrl
    if (!url.startsWith("file://") && !url.startsWith("content://")) return null

    // 1. Check sibling .lrc file when streamUrl is file://
    if (url.startsWith("file://")) {
      runCatching {
        val path = Uri.parse(url).path ?: return@runCatching
        val audioFile = File(path)
        val lrcFile = File(audioFile.parentFile, "${audioFile.nameWithoutExtension}.lrc")
        if (lrcFile.exists() && lrcFile.isFile && lrcFile.length() in 1L..512_000L) {
          val raw = lrcFile.readText(Charsets.UTF_8)
          parseRawLyricsText(raw, song)?.let { return it }
        }
      }
    }

    // 2. Check embedded ID3v2 USLT lyrics tag (first 512 KB of MP3) safely
    return runCatching {
      val uri = Uri.parse(url)
      val stream = if (url.startsWith("file://")) {
        uri.path?.let { File(it).inputStream() }
      } else {
        context.contentResolver.openInputStream(uri)
      }
      stream?.use { input ->
        val header = ByteArray(10)
        if (input.read(header) != 10) return@use null
        if (header[0] != 'I'.code.toByte() || header[1] != 'D'.code.toByte() || header[2] != '3'.code.toByte()) {
          return@use null
        }
        val tagSize = ((header[6].toInt() and 0x7F) shl 21) or
          ((header[7].toInt() and 0x7F) shl 14) or
          ((header[8].toInt() and 0x7F) shl 7) or
          (header[9].toInt() and 0x7F)
        val readLen = tagSize.coerceIn(0, 512_000)
        if (readLen <= 0) return@use null
        val bytes = ByteArray(readLen)
        var offset = 0
        while (offset < readLen) {
          val n = input.read(bytes, offset, readLen - offset)
          if (n <= 0) break
          offset += n
        }
        extractUsltLyrics(bytes, offset)?.let { raw ->
          parseRawLyricsText(raw, song)
        }
      }
    }.getOrNull()
  }

  private fun parseRawLyricsText(raw: String, song: Song): LyricsResult? {
    val trimmed = raw.trim()
    if (trimmed.isBlank()) return null
    val doc = LrcParser.parse(trimmed)
    return if (doc.isSynced && doc.lines.isNotEmpty()) {
      LyricsResult(
        synced = doc.lines.map { LyricLine(it.timestampMs, it.text) },
        plain = doc.lines.joinToString("\n") { it.text },
        matchedTitle = song.title,
        matchedArtist = song.artist,
        rawLrc = trimmed
      )
    } else if (doc.lines.isNotEmpty()) {
      LyricsResult(
        synced = null,
        plain = doc.lines.joinToString("\n") { it.text },
        matchedTitle = song.title,
        matchedArtist = song.artist,
        rawLrc = trimmed
      )
    } else null
  }

  private fun extractUsltLyrics(data: ByteArray, length: Int): String? {
    var pos = 0
    while (pos + 10 <= length) {
      val frameId = String(data, pos, 4, Charsets.ISO_8859_1)
      if (frameId.all { it == '\u0000' }) break
      val frameSize = ((data[pos + 4].toInt() and 0xFF) shl 24) or
        ((data[pos + 5].toInt() and 0xFF) shl 16) or
        ((data[pos + 6].toInt() and 0xFF) shl 8) or
        (data[pos + 7].toInt() and 0xFF)
      if (frameSize <= 0 || pos + 10 + frameSize > length) break
      if (frameId == "USLT" && frameSize > 5) {
        val payloadStart = pos + 10
        val encoding = data[payloadStart].toInt() and 0xFF
        val charset: Charset = when (encoding) {
          1 -> Charsets.UTF_16
          2 -> Charsets.UTF_16BE
          3 -> Charsets.UTF_8
          else -> Charsets.ISO_8859_1
        }
        // Skip encoding (1 byte) + language (3 bytes) + content descriptor null-terminated string
        var textStart = payloadStart + 4
        if (encoding == 1 || encoding == 2) {
          while (textStart + 1 < payloadStart + frameSize) {
            if (data[textStart] == 0.toByte() && data[textStart + 1] == 0.toByte()) {
              textStart += 2
              break
            }
            textStart += 2
          }
        } else {
          while (textStart < payloadStart + frameSize) {
            if (data[textStart] == 0.toByte()) {
              textStart += 1
              break
            }
            textStart += 1
          }
        }
        val textLen = (payloadStart + frameSize) - textStart
        if (textLen > 0) {
          val decoded = String(data, textStart, textLen, charset).trim('\u0000', ' ', '\n', '\r')
          if (decoded.isNotBlank()) return decoded
        }
      }
      pos += 10 + frameSize
    }
    return null
  }
}
