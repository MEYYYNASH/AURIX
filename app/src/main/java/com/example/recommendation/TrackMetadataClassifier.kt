package com.example.recommendation

import com.example.model.Song
import java.util.Locale
import kotlin.math.abs

/**
 * Normalizes and enriches missing genre, BPM, and mood metadata on tracks
 * using source hints and deterministic audio-style heuristics.
 */
object TrackMetadataClassifier {

  private val genreRules: List<Pair<List<String>, Triple<String, IntRange, String>>> = listOf(
    listOf("lo-fi", "lofi", "chillhop", "study", "ambient", "sleep", "piano") to
      Triple("Lo-fi", 72..92, "Focus"),
    listOf("phonk", "montagem", "drift", "gym", "workout", "hardstyle", "trap") to
      Triple("Phonk", 128..156, "Energetic"),
    listOf("r&b", "soul", "slowed", "night", "midnight", "late", "moon", "shadow") to
      Triple("R&B", 78..104, "Night"),
    listOf("hip-hop", "hip hop", "rap", "drill") to
      Triple("Hip-Hop", 88..116, "Night"),
    listOf("electronic", "dance", "house", "edm", "techno", "club") to
      Triple("Electronic", 120..136, "Energetic"),
    listOf("indie", "acoustic", "folk", "singer/songwriter", "bossa") to
      Triple("Indie", 82..106, "Chill"),
    listOf("rock", "metal", "punk", "alternative") to
      Triple("Rock", 116..144, "Energetic"),
    listOf("k-pop", "khmer", "asian", "j-pop", "pop") to
      Triple("Pop", 102..126, "Chill"),
    listOf("jazz", "classical", "instrumental", "soundtrack") to
      Triple("Instrumental", 68..98, "Focus")
  )

  fun enrich(song: Song, genreHint: String? = null): Song {
    val rawCombined = buildString {
      append(song.title).append(' ')
      append(song.artist).append(' ')
      append(song.source).append(' ')
      append(genreHint.orEmpty()).append(' ')
      append(song.genre)
    }.lowercase(Locale.ROOT)

    val matchedRule = genreRules.firstOrNull { (keywords, _) ->
      keywords.any { kw -> rawCombined.contains(kw) }
    }?.second

    val resolvedGenre = song.genre.takeIf { it.isNotBlank() }
      ?: genreHint?.takeIf { it.isNotBlank() }
      ?: matchedRule?.first
      ?: "Pop"

    val hashSeed = abs((song.id.ifBlank { song.title + song.artist }).hashCode())
    val bpmBand = matchedRule?.second ?: bpmBandForGenre(resolvedGenre)
    val resolvedBpm = if (song.bpm > 0) {
      song.bpm
    } else {
      val span = (bpmBand.last - bpmBand.first).coerceAtLeast(1)
      bpmBand.first + (hashSeed % span)
    }

    val resolvedMood = song.mood.takeIf { it.isNotBlank() }
      ?: matchedRule?.third
      ?: moodForGenreAndBpm(resolvedGenre, resolvedBpm)

    return song.copy(
      genre = resolvedGenre,
      bpm = resolvedBpm,
      mood = resolvedMood
    )
  }

  private fun bpmBandForGenre(genre: String): IntRange {
    val g = genre.lowercase(Locale.ROOT)
    return when {
      g.contains("lo-fi") || g.contains("lofi") || g.contains("ambient") -> 72..92
      g.contains("phonk") || g.contains("dance") || g.contains("electronic") -> 122..150
      g.contains("rock") || g.contains("metal") -> 116..142
      g.contains("r&b") || g.contains("soul") -> 78..104
      g.contains("hip") || g.contains("rap") -> 86..115
      g.contains("instrumental") || g.contains("classical") || g.contains("jazz") -> 68..98
      else -> 96..126
    }
  }

  private fun moodForGenreAndBpm(genre: String, bpm: Int): String {
    val g = genre.lowercase(Locale.ROOT)
    return when {
      g.contains("lo-fi") || g.contains("instrumental") || g.contains("classical") -> "Focus"
      g.contains("r&b") || g.contains("soul") || g.contains("hip") -> "Night"
      bpm >= 120 -> "Energetic"
      bpm <= 96 -> "Chill"
      else -> "Chill"
    }
  }
}
