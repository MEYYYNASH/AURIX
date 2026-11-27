package com.example.data

import com.example.model.Song
import java.io.File
import java.net.URI
import java.util.Locale

data class DuplicateTrackGroup(
  val signature: String,
  val keeper: Song,
  val duplicates: List<Song>,
  val reclaimableBytes: Long
)

data class LibraryScanReport(
  val totalScanned: Int,
  val missingFileTracks: List<Song>,
  val duplicateGroups: List<DuplicateTrackGroup>,
  val reclaimableBytes: Long
) {
  val duplicateTracks: List<Song>
    get() = duplicateGroups.flatMap { it.duplicates }

  val totalIssuesCount: Int
    get() = missingFileTracks.size + duplicateTracks.size

  val hasIssues: Boolean
    get() = totalIssuesCount > 0

  val reclaimableMbFormatted: String
    get() {
      if (reclaimableBytes <= 0L) return "0.0 MB"
      val mb = reclaimableBytes.toDouble() / (1024.0 * 1024.0)
      return String.format(Locale.US, "%.1f MB", mb)
    }
}

data class LibraryCleanResult(
  val removedMissingCount: Int,
  val removedDuplicateCount: Int,
  val freedBytes: Long,
  val timestampMs: Long = System.currentTimeMillis()
) {
  val totalCleanedCount: Int
    get() = removedMissingCount + removedDuplicateCount
}

object LibraryCleanerEngine {

  private val NOISE_SUFFIX_REGEX = Regex(
    """\s*[\(\[\{]\s*(?:official\s*(?:music\s*)?(?:video|audio|lyric\s*video|visualizer)|lyrics?|hd|hq|4k|audio|mv)\s*[\)\]\}]""",
    RegexOption.IGNORE_CASE
  )

  private val COPY_SUFFIX_REGEX = Regex(
    """\s*(?:\(\d+\)|-\s*copy|\bcopy\b)$""",
    RegexOption.IGNORE_CASE
  )

  fun normalizeTrackSignature(title: String, artist: String): String {
    val cleanTitle = title
      .replace(NOISE_SUFFIX_REGEX, "")
      .replace(COPY_SUFFIX_REGEX, "")
      .lowercase(Locale.ROOT)
      .replace(Regex("""[^\p{L}\p{N}]+"""), " ")
      .trim()

    val cleanArtist = artist
      .lowercase(Locale.ROOT)
      .takeUnless { it == "unknown artist" || it == "unknown" }
      .orEmpty()
      .replace(Regex("""[^\p{L}\p{N}]+"""), " ")
      .trim()

    return "$cleanTitle|$cleanArtist"
  }

  fun resolveLocalFile(streamUrl: String): File? {
    val trimmed = streamUrl.trim()
    if (trimmed.isEmpty()) return null
    return when {
      trimmed.startsWith("file://", ignoreCase = true) -> {
        runCatching {
          val uri = URI(trimmed)
          val path = uri.path
          if (!path.isNullOrBlank()) File(path) else File(trimmed.removePrefix("file://"))
        }.getOrElse {
          File(trimmed.removePrefix("file://").removePrefix("FILE://"))
        }
      }
      trimmed.startsWith("/") -> File(trimmed)
      else -> null
    }
  }

  fun isLocalFileMissing(
    song: Song,
    fileExistencePredicate: (File) -> Boolean = { it.exists() && it.length() > 0L }
  ): Boolean {
    val url = song.streamUrl.trim()
    if (url.isBlank()) return true
    val file = resolveLocalFile(url) ?: return false
    return !fileExistencePredicate(file)
  }

  fun scanLibrary(
    songs: List<Song>,
    fileExistencePredicate: (File) -> Boolean = { it.exists() && it.length() > 0L },
    fileSizeProvider: (File) -> Long = { if (it.exists()) it.length() else 0L }
  ): LibraryScanReport {
    if (songs.isEmpty()) {
      return LibraryScanReport(
        totalScanned = 0,
        missingFileTracks = emptyList(),
        duplicateGroups = emptyList(),
        reclaimableBytes = 0L
      )
    }

    val missing = mutableListOf<Song>()
    val validSongs = mutableListOf<Song>()

    for (song in songs) {
      if (isLocalFileMissing(song, fileExistencePredicate)) {
        missing.add(song)
      } else {
        validSongs.add(song)
      }
    }

    // Group remaining valid songs by normalized (title, artist) signature or identical file path
    val bySignature = LinkedHashMap<String, MutableList<Song>>()
    for (song in validSongs) {
      val file = resolveLocalFile(song.streamUrl)
      val sig = normalizeTrackSignature(song.title, song.artist).takeIf { it != "|" && !it.startsWith("|") }
        ?: (file?.absolutePath ?: song.id)
      bySignature.getOrPut(sig) { mutableListOf() }.add(song)
    }

    val duplicateGroups = mutableListOf<DuplicateTrackGroup>()
    var totalReclaimable = 0L

    for ((sig, group) in bySignature) {
      if (group.size <= 1) continue
      // Pick the highest quality keeper: prefer Favorite -> has custom artwork -> larger file size -> longer duration
      val sorted = group.sortedWith(
        compareByDescending<Song> { it.isFavorite }
          .thenByDescending { it.artworkUrl.isNotBlank() }
          .thenByDescending { song ->
            val f = resolveLocalFile(song.streamUrl)
            if (f != null) fileSizeProvider(f) else 0L
          }
          .thenByDescending { it.durationSeconds }
      )
      val keeper = sorted.first()
      val duplicates = sorted.drop(1)
      val keeperPath = resolveLocalFile(keeper.streamUrl)?.absolutePath
      var groupBytes = 0L
      for (dup in duplicates) {
        val dupFile = resolveLocalFile(dup.streamUrl)
        if (dupFile != null && dupFile.absolutePath != keeperPath) {
          groupBytes += fileSizeProvider(dupFile).coerceAtLeast(0L)
        }
      }
      totalReclaimable += groupBytes
      duplicateGroups.add(
        DuplicateTrackGroup(
          signature = sig,
          keeper = keeper,
          duplicates = duplicates,
          reclaimableBytes = groupBytes
        )
      )
    }

    return LibraryScanReport(
      totalScanned = songs.size,
      missingFileTracks = missing,
      duplicateGroups = duplicateGroups,
      reclaimableBytes = totalReclaimable
    )
  }
}
