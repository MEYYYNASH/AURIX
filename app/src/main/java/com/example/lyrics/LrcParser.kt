package com.example.lyrics

import java.text.BreakIterator
import kotlin.math.abs

data class LyricWordSegment(
  val startMs: Long,
  val endMs: Long,
  val startCharIndex: Int,
  val endCharIndex: Int,
  val graphemeStart: Int,
  val graphemeEnd: Int
)

data class ParsedLyricLine(
  val timestampMs: Long,
  val endTimestampMs: Long,
  val text: String,
  val words: List<LyricWordSegment>,
  // Sorted UTF-16 char indices marking valid grapheme cluster boundaries (never splits Khmer/emoji)
  val graphemeBoundaries: IntArray
) {
  /**
   * Computes a grapheme-safe char end index in [0..text.length] for progressive word fill
   * with zero heap allocations.
   */
  fun computeRevealCharIndex(positionMs: Long): Int {
    if (words.isEmpty() || text.isEmpty() || graphemeBoundaries.size <= 1) return text.length
    val firstStart = words.first().startMs
    if (positionMs < firstStart) return 0
    val lastWord = words.last()
    if (positionMs >= lastWord.endMs) return text.length

    var activeWordIndex = findWordIndexBinary(positionMs)
    if (activeWordIndex < 0) activeWordIndex = 0
    val word = words[activeWordIndex]

    if (positionMs <= word.startMs) return word.startCharIndex
    if (positionMs >= word.endMs || word.endMs <= word.startMs) return word.endCharIndex

    val graphemeCount = (word.graphemeEnd - word.graphemeStart).coerceAtLeast(1)
    val fraction = ((positionMs - word.startMs).toFloat() / (word.endMs - word.startMs).toFloat())
      .coerceIn(0f, 1f)
    val targetGrapheme = (word.graphemeStart + kotlin.math.round(fraction * graphemeCount).toInt())
      .coerceIn(word.graphemeStart, word.graphemeEnd)

    return graphemeBoundaries[targetGrapheme.coerceIn(0, graphemeBoundaries.lastIndex)]
  }

  private fun findWordIndexBinary(positionMs: Long): Int {
    var low = 0
    var high = words.lastIndex
    var result = -1
    while (low <= high) {
      val mid = (low + high) ushr 1
      if (words[mid].startMs <= positionMs) {
        result = mid
        low = mid + 1
      } else {
        high = mid - 1
      }
    }
    return result
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is ParsedLyricLine) return false
    return timestampMs == other.timestampMs &&
      endTimestampMs == other.endTimestampMs &&
      text == other.text &&
      words == other.words
  }

  override fun hashCode(): Int {
    var result = timestampMs.hashCode()
    result = 31 * result + endTimestampMs.hashCode()
    result = 31 * result + text.hashCode()
    result = 31 * result + words.hashCode()
    return result
  }
}

data class ParsedLyricsDocument(
  val lines: List<ParsedLyricLine>,
  val isSynced: Boolean,
  val offsetMs: Long = 0L
)

object LrcParser {
  private val lineTagRegex = Regex("""\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?]""")
  private val wordTagRegex = Regex("""<(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?>""")
  private val offsetTagRegex = Regex("""^\s*\[offset\s*:\s*([+-]?\d+)\s*]\s*$""", RegexOption.IGNORE_CASE)
  private val metadataTagRegex = Regex("""^\s*\[[a-zA-Z]{1,12}\s*:[^\]]*]\s*$""")
  private val inlineMetadataStripRegex = Regex("""\[[a-zA-Z]{1,12}\s*:[^\]]*]""")

  private data class MutableWord(
    val startMs: Long,
    val startCharIndex: Int,
    val endCharIndex: Int
  )

  private data class RawEntry(
    val timestampMs: Long,
    val text: String,
    val rawWords: List<MutableWord>
  )

  fun parse(rawText: String?): ParsedLyricsDocument {
    if (rawText.isNullOrBlank()) {
      return ParsedLyricsDocument(emptyList(), isSynced = false, offsetMs = 0L)
    }

    val rawLines = rawText.lines()
    var lrcOffsetMs = 0L
    val syncedEntries = mutableListOf<RawEntry>()
    val plainLines = mutableListOf<String>()

    for (rawLine in rawLines) {
      val trimmed = rawLine.trim()
      if (trimmed.isEmpty()) continue

      offsetTagRegex.find(trimmed)?.let { match ->
        lrcOffsetMs = match.groupValues[1].toLongOrNull() ?: lrcOffsetMs
        continue
      }

      if (metadataTagRegex.matches(trimmed)) {
        continue
      }

      val lineMatches = lineTagRegex.findAll(rawLine).toList()
      if (lineMatches.isEmpty()) {
        val cleanedPlain = inlineMetadataStripRegex.replace(trimmed, "").trim()
        if (cleanedPlain.isNotEmpty()) {
          plainLines.add(cleanedPlain)
        }
        continue
      }

      // Extract text after all leading/inline [mm:ss.xx] line tags
      val bodyAfterLineTags = lineTagRegex.replace(rawLine, "").trim()
      if (bodyAfterLineTags.isEmpty()) continue

      val (cleanText, parsedWords) = extractEnhancedWords(bodyAfterLineTags)
      if (cleanText.isBlank()) continue

      val baseTimestampMs = parseTimestampMs(lineMatches.first())
      for (match in lineMatches) {
        val lineTsMs = parseTimestampMs(match)
        val delta = lineTsMs - baseTimestampMs
        val shiftedWords = if (delta == 0L || parsedWords.isEmpty()) {
          parsedWords
        } else {
          parsedWords.map { it.copy(startMs = (it.startMs + delta).coerceAtLeast(0L)) }
        }
        syncedEntries.add(RawEntry(lineTsMs, cleanText, shiftedWords))
      }
    }

    if (syncedEntries.isEmpty()) {
      val staticLines = plainLines.map { text ->
        val boundaries = computeGraphemeBoundaries(text)
        ParsedLyricLine(
          timestampMs = 0L,
          endTimestampMs = 0L,
          text = text,
          words = emptyList(),
          graphemeBoundaries = boundaries
        )
      }
      return ParsedLyricsDocument(staticLines, isSynced = false, offsetMs = 0L)
    }

    // Apply LRC [offset:] tag: positive offset means lyrics appear earlier
    val offsetAdjusted = syncedEntries
      .map { entry ->
        val adjustedTs = (entry.timestampMs - lrcOffsetMs).coerceAtLeast(0L)
        val adjustedWords = entry.rawWords.map { w ->
          w.copy(startMs = (w.startMs - lrcOffsetMs).coerceAtLeast(0L))
        }
        entry.copy(timestampMs = adjustedTs, rawWords = adjustedWords)
      }
      .sortedBy { it.timestampMs }

    val finalized = ArrayList<ParsedLyricLine>(offsetAdjusted.size)
    for (i in offsetAdjusted.indices) {
      val entry = offsetAdjusted[i]
      val nextLineTs = offsetAdjusted.getOrNull(i + 1)?.timestampMs
      val defaultEndTs = nextLineTs ?: (entry.rawWords.lastOrNull()?.startMs?.plus(2500L) ?: (entry.timestampMs + 5000L))
      val boundaries = computeGraphemeBoundaries(entry.text)

      val segments = if (entry.rawWords.isEmpty()) {
        emptyList()
      } else {
        buildList(entry.rawWords.size) {
          for (wIdx in entry.rawWords.indices) {
            val w = entry.rawWords[wIdx]
            val endMs = entry.rawWords.getOrNull(wIdx + 1)?.startMs
              ?: maxOf(defaultEndTs, w.startMs + 600L)
            val safeStartChar = snapToNearestGraphemeBoundary(boundaries, w.startCharIndex)
            val safeEndChar = snapToNearestGraphemeBoundary(boundaries, w.endCharIndex)
              .coerceAtLeast(safeStartChar)
            val gStart = findBoundaryIndex(boundaries, safeStartChar)
            val gEnd = findBoundaryIndex(boundaries, safeEndChar).coerceAtLeast(gStart)

            add(
              LyricWordSegment(
                startMs = w.startMs,
                endMs = endMs.coerceAtLeast(w.startMs),
                startCharIndex = safeStartChar,
                endCharIndex = safeEndChar,
                graphemeStart = gStart,
                graphemeEnd = gEnd
              )
            )
          }
        }
      }

      finalized.add(
        ParsedLyricLine(
          timestampMs = entry.timestampMs,
          endTimestampMs = defaultEndTs.coerceAtLeast(entry.timestampMs),
          text = entry.text,
          words = segments,
          graphemeBoundaries = boundaries
        )
      )
    }

    return ParsedLyricsDocument(finalized, isSynced = true, offsetMs = lrcOffsetMs)
  }

  private fun extractEnhancedWords(body: String): Pair<String, List<MutableWord>> {
    val wordMatches = wordTagRegex.findAll(body).toList()
    if (wordMatches.isEmpty()) {
      return body.trim() to emptyList()
    }

    val cleanBuilder = StringBuilder(body.length)
    val words = mutableListOf<MutableWord>()

    val leadingText = body.substring(0, wordMatches.first().range.first)
    if (leadingText.isNotEmpty()) {
      cleanBuilder.append(leadingText)
    }

    for (i in wordMatches.indices) {
      val match = wordMatches[i]
      val startMs = parseTimestampMs(match)
      val textStartInBody = match.range.last + 1
      val textEndInBody = wordMatches.getOrNull(i + 1)?.range?.first ?: body.length
      val segmentText = if (textEndInBody > textStartInBody) {
        body.substring(textStartInBody, textEndInBody)
      } else {
        ""
      }

      val charStart = cleanBuilder.length
      cleanBuilder.append(segmentText)
      val charEnd = cleanBuilder.length
      if (charEnd > charStart) {
        words.add(MutableWord(startMs = startMs, startCharIndex = charStart, endCharIndex = charEnd))
      }
    }

    val finalText = cleanBuilder.toString().trimEnd()
    val clampedWords = words.mapNotNull { w ->
      val clampedEnd = w.endCharIndex.coerceAtMost(finalText.length)
      if (w.startCharIndex >= clampedEnd) null else w.copy(endCharIndex = clampedEnd)
    }
    return finalText to clampedWords
  }

  private fun parseTimestampMs(match: MatchResult): Long {
    val min = match.groupValues[1].toLongOrNull() ?: 0L
    val sec = match.groupValues[2].toLongOrNull() ?: 0L
    val fracRaw = match.groupValues[3]
    val ms = when (fracRaw.length) {
      0 -> 0L
      1 -> (fracRaw.toLongOrNull() ?: 0L) * 100L
      2 -> (fracRaw.toLongOrNull() ?: 0L) * 10L
      else -> fracRaw.take(3).toLongOrNull() ?: 0L
    }
    return (min * 60L + sec) * 1000L + ms
  }

  private fun computeGraphemeBoundaries(text: String): IntArray {
    if (text.isEmpty()) return intArrayOf(0)
    val iterator = BreakIterator.getCharacterInstance()
    iterator.setText(text)
    val list = ArrayList<Int>(text.length + 1)
    var boundary = iterator.first()
    while (boundary != BreakIterator.DONE) {
      list.add(boundary)
      boundary = iterator.next()
    }
    if (list.isEmpty() || list.last() != text.length) {
      list.add(text.length)
    }
    return list.toIntArray()
  }

  private fun snapToNearestGraphemeBoundary(boundaries: IntArray, index: Int): Int {
    if (boundaries.isEmpty()) return 0
    val clamped = index.coerceIn(0, boundaries.last())
    var low = 0
    var high = boundaries.lastIndex
    while (low <= high) {
      val mid = (low + high) ushr 1
      val value = boundaries[mid]
      if (value == clamped) return value
      if (value < clamped) low = mid + 1 else high = mid - 1
    }
    val upper = boundaries[low.coerceIn(0, boundaries.lastIndex)]
    val lower = boundaries[high.coerceIn(0, boundaries.lastIndex)]
    return if (abs(clamped - lower) <= abs(upper - clamped)) lower else upper
  }

  private fun findBoundaryIndex(boundaries: IntArray, charIndex: Int): Int {
    var low = 0
    var high = boundaries.lastIndex
    while (low <= high) {
      val mid = (low + high) ushr 1
      val v = boundaries[mid]
      when {
        v == charIndex -> return mid
        v < charIndex -> low = mid + 1
        else -> high = mid - 1
      }
    }
    return low.coerceIn(0, boundaries.lastIndex)
  }
}
