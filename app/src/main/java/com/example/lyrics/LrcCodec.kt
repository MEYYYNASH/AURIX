package com.example.lyrics

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale

data class EditableLyricRow(
  val id: Long,
  val text: String,
  val timestampMs: Long? = null
)

data class LyricsEditorDraft(
  val rows: List<EditableLyricRow>,
  val step: Int,
  val globalOffsetMs: Long
)

/**
 * Offline LRC parser, writer, timestamp formatter, draft serializer, and .lrc share/export helper
 * for the Developer Mode Custom Lyrics Editor.
 */
object LrcCodec {
  const val SOURCE_CUSTOM = "custom"
  const val MIN_GLOBAL_OFFSET_MS = -5_000L
  const val MAX_GLOBAL_OFFSET_MS = 5_000L
  const val FINE_STEP_MS = 100L

  private val lineTagRegex = Regex("""\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?]""")
  private val offsetTagRegex = Regex("""^\s*\[offset\s*:\s*([+-]?\d+)\s*]\s*$""", RegexOption.IGNORE_CASE)
  private val metadataTagRegex = Regex("""^\s*\[[a-zA-Z]{1,12}\s*:[^\]]*]\s*$""")
  private val inlineWordTagRegex = Regex("""<\d{1,3}:\d{2}(?:[.:]\d{1,3})?>""")

  /**
   * Formats milliseconds as standard LRC `[mm:ss.xx]` (centiseconds).
   */
  fun formatTimestamp(ms: Long): String {
    val safeMs = ms.coerceAtLeast(0L)
    val totalCentis = (safeMs + 5L) / 10L
    val centis = (totalCentis % 100L).toInt()
    val totalSeconds = totalCentis / 100L
    val seconds = (totalSeconds % 60L).toInt()
    val minutes = (totalSeconds / 60L).toInt()
    return String.format(Locale.US, "[%02d:%02d.%02d]", minutes, seconds, centis)
  }

  /**
   * Formats offset in milliseconds as concise signed seconds (e.g. `+0.4 s`, `-1.2 s`, `0.0 s`).
   */
  fun formatOffsetSeconds(offsetMs: Long): String {
    val clamped = offsetMs.coerceIn(MIN_GLOBAL_OFFSET_MS, MAX_GLOBAL_OFFSET_MS)
    val sec = clamped / 1000.0
    return when {
      clamped > 0L -> String.format(Locale.US, "+%.1f s", sec)
      clamped < 0L -> String.format(Locale.US, "%.1f s", sec)
      else -> "0.0 s"
    }
  }

  /**
   * Parses raw `.lrc` or plain `.txt` content into editable rows.
   * Preserves per-line `[mm:ss.xx]` timestamps when present and extracts optional `[offset:]` metadata.
   */
  fun parseForEditor(rawText: String?): Pair<List<EditableLyricRow>, Long> {
    if (rawText.isNullOrBlank()) return emptyList<EditableLyricRow>() to 0L

    var nextId = 1L
    var parsedOffsetMs = 0L
    val rows = mutableListOf<EditableLyricRow>()

    for (rawLine in rawText.lines()) {
      val trimmed = rawLine.trim()
      if (trimmed.isEmpty()) continue

      offsetTagRegex.find(trimmed)?.let { match ->
        val parsed = match.groupValues[1].toLongOrNull()
        if (parsed != null) {
          parsedOffsetMs = parsed.coerceIn(MIN_GLOBAL_OFFSET_MS, MAX_GLOBAL_OFFSET_MS)
        }
        continue
      }

      if (metadataTagRegex.matches(trimmed)) continue

      val matches = lineTagRegex.findAll(rawLine).toList()
      if (matches.isEmpty()) {
        val cleaned = inlineWordTagRegex.replace(trimmed, "").trim()
        if (cleaned.isNotEmpty()) {
          rows.add(EditableLyricRow(id = nextId++, text = cleaned, timestampMs = null))
        }
      } else {
        val body = inlineWordTagRegex.replace(lineTagRegex.replace(rawLine, ""), "").trim()
        if (body.isEmpty()) continue
        for (match in matches) {
          val tsMs = parseMatchTimestampMs(match)
          rows.add(EditableLyricRow(id = nextId++, text = body, timestampMs = tsMs))
        }
      }
    }

    val anySynced = rows.any { it.timestampMs != null }
    val ordered = if (anySynced && rows.all { it.timestampMs != null }) {
      rows.sortedBy { it.timestampMs ?: Long.MAX_VALUE }
    } else {
      rows
    }
    return ordered to parsedOffsetMs
  }

  /**
   * Reconciles edited plain-text lines from Step 1 (Write) with previous timestamped rows
   * so fixing typos or adding/removing a line does not wipe existing timestamps.
   */
  fun mergeWriteTextWithExistingRows(
    plainText: String,
    previousRows: List<EditableLyricRow>
  ): List<EditableLyricRow> {
    val lines = plainText.lines()
      .map { it.trim() }
      .filter { it.isNotEmpty() }

    if (lines.isEmpty()) return emptyList()

    // Check if the user pasted raw LRC text directly into the Write box
    val containsLrcTag = lines.any { lineTagRegex.containsMatchIn(it) }
    if (containsLrcTag) {
      return parseForEditor(plainText).first
    }

    val usedPrevIndices = HashSet<Int>(previousRows.size)
    val matchedPrevForLine = arrayOfNulls<EditableLyricRow>(lines.size)

    // Pass 1: Claim exact text matches first (same index preferred, then earliest unused)
    // so inserting or deleting a line above never steals timestamps from lines below.
    for (idx in lines.indices) {
      val lineText = lines[idx]
      val sameIdx = previousRows.getOrNull(idx)
      if (sameIdx != null && idx !in usedPrevIndices && sameIdx.text == lineText) {
        usedPrevIndices.add(idx)
        matchedPrevForLine[idx] = sameIdx
      }
    }
    for (idx in lines.indices) {
      if (matchedPrevForLine[idx] != null) continue
      val lineText = lines[idx]
      val matchedIdx = previousRows.indices.firstOrNull { prevIdx ->
        prevIdx !in usedPrevIndices && previousRows[prevIdx].text == lineText
      }
      if (matchedIdx != null) {
        usedPrevIndices.add(matchedIdx)
        matchedPrevForLine[idx] = previousRows[matchedIdx]
      }
    }

    // Pass 2: For remaining lines (in-place edits), reuse same-index timestamp only if unclaimed.
    var nextId = (previousRows.maxOfOrNull { it.id } ?: 0L) + 1L
    return lines.mapIndexed { idx, lineText ->
      val exact = matchedPrevForLine[idx]
      if (exact != null) return@mapIndexed exact

      val sameIdx = previousRows.getOrNull(idx)
      if (sameIdx != null && idx !in usedPrevIndices && sameIdx.timestampMs != null) {
        usedPrevIndices.add(idx)
        return@mapIndexed sameIdx.copy(text = lineText)
      }
      EditableLyricRow(id = nextId++, text = lineText, timestampMs = null)
    }
  }

  /**
   * Converts editor rows into standard LRC text.
   * - When at least one row has a timestamp, writes `[mm:ss.xx]line` sorted chronologically.
   * - When no row has a timestamp, writes plain lyric lines (one per row).
   * - `includeOffsetTag`: keep `false` for internal Room storage (AURIX stores per-song offset in
   *   `lyric_offsets` separately to avoid double-shifting), and set `true` for external `.lrc` export.
   */
  fun writeLrc(
    rows: List<EditableLyricRow>,
    title: String? = null,
    artist: String? = null,
    globalOffsetMs: Long = 0L,
    includeOffsetTag: Boolean = false
  ): String {
    val cleanRows = rows
      .map { it.copy(text = it.text.trim()) }
      .filter { it.text.isNotEmpty() }
    if (cleanRows.isEmpty()) return ""

    val syncedRows = cleanRows.filter { it.timestampMs != null }
    val sb = StringBuilder()

    if (!title.isNullOrBlank()) {
      sb.append("[ti:").append(sanitizeHeaderValue(title)).append("]\n")
    }
    if (!artist.isNullOrBlank()) {
      sb.append("[ar:").append(sanitizeHeaderValue(artist)).append("]\n")
    }
    sb.append("[by:AURIX Custom Lyrics Editor]\n")
    if (includeOffsetTag && globalOffsetMs != 0L) {
      val clamped = globalOffsetMs.coerceIn(MIN_GLOBAL_OFFSET_MS, MAX_GLOBAL_OFFSET_MS)
      sb.append("[offset:").append(clamped).append("]\n")
    }

    if (syncedRows.isEmpty()) {
      cleanRows.forEach { row ->
        sb.append(row.text).append('\n')
      }
    } else {
      val sorted = syncedRows.sortedBy { it.timestampMs ?: 0L }
      sorted.forEach { row ->
        val ts = formatTimestamp(row.timestampMs ?: 0L)
        sb.append(ts).append(row.text).append('\n')
      }
      // Preserve any unstamped trailing rows as plain lines so work isn't lost if saved mid-way
      val unstamped = cleanRows.filter { it.timestampMs == null }
      if (unstamped.isNotEmpty() && syncedRows.size < cleanRows.size) {
        val lastTs = sorted.lastOrNull()?.timestampMs ?: 0L
        unstamped.forEachIndexed { i, row ->
          val fallbackTs = lastTs + (i + 1) * 2_000L
          sb.append(formatTimestamp(fallbackTs)).append(row.text).append('\n')
        }
      }
    }

    return sb.toString().trimEnd()
  }

  fun rowsToPlainText(rows: List<EditableLyricRow>): String {
    return rows.joinToString("\n") { it.text }
  }

  fun encodeDraft(draft: LyricsEditorDraft): String {
    val root = JSONObject()
    root.put("step", draft.step.coerceIn(0, 1))
    root.put("offsetMs", draft.globalOffsetMs.coerceIn(MIN_GLOBAL_OFFSET_MS, MAX_GLOBAL_OFFSET_MS))
    val arr = JSONArray()
    draft.rows.forEach { row ->
      val obj = JSONObject()
      obj.put("id", row.id)
      obj.put("text", row.text)
      if (row.timestampMs != null) {
        obj.put("ts", row.timestampMs)
      }
      arr.put(obj)
    }
    root.put("rows", arr)
    return root.toString()
  }

  fun decodeDraft(rawJson: String?): LyricsEditorDraft? {
    if (rawJson.isNullOrBlank()) return null
    return runCatching {
      val root = JSONObject(rawJson)
      val step = root.optInt("step", 0).coerceIn(0, 1)
      val offsetMs = root.optLong("offsetMs", 0L).coerceIn(MIN_GLOBAL_OFFSET_MS, MAX_GLOBAL_OFFSET_MS)
      val arr = root.optJSONArray("rows") ?: JSONArray()
      val rows = ArrayList<EditableLyricRow>(arr.length())
      for (i in 0 until arr.length()) {
        val obj = arr.optJSONObject(i) ?: continue
        val id = obj.optLong("id", (i + 1).toLong())
        val text = obj.optString("text", "")
        val ts = if (obj.has("ts") && !obj.isNull("ts")) obj.optLong("ts").coerceAtLeast(0L) else null
        rows.add(EditableLyricRow(id = id, text = text, timestampMs = ts))
      }
      LyricsEditorDraft(rows = rows, step = step, globalOffsetMs = offsetMs)
    }.getOrNull()
  }

  fun suggestedLrcFileName(title: String, artist: String): String {
    val raw = listOf(artist.trim(), title.trim())
      .filter { it.isNotEmpty() }
      .joinToString(" - ")
      .ifBlank { "aurix_lyrics" }
    val sanitized = raw
      .replace(Regex("""[\\/:*?"<>|]"""), "_")
      .replace(Regex("""\s+"""), " ")
      .trim()
      .take(64)
      .ifBlank { "aurix_lyrics" }
    return "$sanitized.lrc"
  }

  /**
   * Writes the `.lrc` content into the app's FileProvider cache directory (`shared_lyrics/`)
   * and returns an Android share Intent (`ACTION_SEND`). Zero storage permissions required.
   */
  fun createShareLrcIntent(
    context: Context,
    title: String,
    artist: String,
    lrcContent: String
  ): Intent? {
    if (lrcContent.isBlank()) return null
    return runCatching {
      val dir = File(context.cacheDir, "shared_lyrics").apply { mkdirs() }
      val file = File(dir, suggestedLrcFileName(title, artist))
      file.writeText(lrcContent, Charsets.UTF_8)
      val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file
      )
      Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, suggestedLrcFileName(title, artist))
        putExtra(Intent.EXTRA_TEXT, lrcContent)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
      }
    }.getOrNull()
  }

  private fun parseMatchTimestampMs(match: MatchResult): Long {
    val min = match.groupValues[1].toLongOrNull() ?: 0L
    val sec = match.groupValues[2].toLongOrNull() ?: 0L
    val fracRaw = match.groupValues[3]
    val ms = when (fracRaw.length) {
      0 -> 0L
      1 -> (fracRaw.toLongOrNull() ?: 0L) * 100L
      2 -> (fracRaw.toLongOrNull() ?: 0L) * 10L
      else -> fracRaw.take(3).toLongOrNull() ?: 0L
    }
    return ((min * 60L + sec) * 1000L + ms).coerceAtLeast(0L)
  }

  private fun sanitizeHeaderValue(value: String): String {
    return value.replace("[", "(").replace("]", ")").replace("\n", " ").trim()
  }
}
