package com.example.recommendation

import com.example.model.Song
import java.util.Locale
import kotlin.math.abs

/**
 * Normalizes and enriches missing genre, BPM, mood label, 5D MoodFlow vectors
 * (happy, sad, calm, energetic, romantic), chill/energy scores, vocal presence,
 * and track language using source hints and deterministic genre/BPM/keyword heuristics.
 */
object TrackMetadataClassifier {

  private data class MoodPreset(
    val energy: Float,
    val sad: Float,
    val chill: Float,
    val happy: Float,
    val calm: Float,
    val energetic: Float,
    val romantic: Float
  )

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

  private val instrumentalKeywords = listOf(
    "instrumental", "karaoke", "type beat", "backing track", "no vocals",
    "off vocal", "piano solo", "orchestra", "symphony", "ambient study",
    "meditation", "rain sounds", "white noise", "soundtrack", "score"
  )

  private val sadKeywords = listOf(
    "sad", "cry", "tears", "lonely", "alone", "hurt", "broken", "heartbreak",
    "pain", "miss you", "goodbye", "gone", "sorrow", "grief", "melancholy",
    "blue", "ballad", "sorry", "fade", "empty", "regret", "slowed"
  )

  private val chillKeywords = listOf(
    "chill", "lofi", "lo-fi", "rain", "midnight", "dream", "soft", "warm",
    "acoustic", "coffee", "sunset", "breeze", "calm", "sleep", "night",
    "float", "slow", "moon", "easy"
  )

  private val romanticKeywords = listOf(
    "love", "kiss", "heart", "darling", "baby", "forever", "with you",
    "hold me", "romance", "romantic", "mine", "sweet", "crush", "angel"
  )

  private val happyKeywords = listOf(
    "happy", "smile", "sun", "summer", "shine", "bright", "good day",
    "joy", "celebrate", "feel good", "fun", "paradise", "golden"
  )

  private val energeticKeywords = listOf(
    "phonk", "gym", "workout", "run", "hype", "drift", "fire", "lit",
    "party", "club", "drop", "remix", "rage", "hard", "power", "boost"
  )

  fun needsMoodMetadata(song: Song): Boolean {
    if (song.hasMoodMetadata) return false
    return song.happy == 0f &&
      song.sad == 0f &&
      song.calm == 0f &&
      song.energetic == 0f &&
      song.romantic == 0f &&
      song.chill == 0f
  }

  /**
   * Enriches missing genre, BPM, mood label, mood vector, energy, vocal flag, and language.
   * Explicitly populated mood vectors (`hasMoodMetadata == true` or non-zero mood dimensions)
   * are preserved without overwriting caller values.
   */
  fun enrich(song: Song, genreHint: String? = null): Song {
    val rawCombined = buildString {
      append(song.title).append(' ')
      append(song.artist).append(' ')
      append(song.source).append(' ')
      append(genreHint.orEmpty()).append(' ')
      append(song.genre).append(' ')
      append(song.mood)
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

    val resolvedLanguage = song.language.takeIf { it.isNotBlank() && it != "en" }
      ?: inferLanguage(song.title, song.artist, rawCombined)

    val shouldFillMoods = needsMoodMetadata(song)
    val heuristicPreset = inferMoodPreset(
      genre = resolvedGenre,
      bpm = resolvedBpm,
      moodTag = resolvedMood,
      combinedText = rawCombined,
      seed = hashSeed
    )

    val resolvedVocals = if (song.hasMoodMetadata) {
      song.hasVocals
    } else {
      inferHasVocals(resolvedGenre, rawCombined, song.hasVocals)
    }

    return song.copy(
      genre = resolvedGenre,
      bpm = resolvedBpm,
      mood = resolvedMood,
      energy = if (shouldFillMoods) heuristicPreset.energy else song.energy.coerceIn(0f, 1f),
      sad = if (shouldFillMoods) heuristicPreset.sad else song.sad.coerceIn(0f, 1f),
      chill = if (shouldFillMoods) heuristicPreset.chill else song.chill.coerceIn(0f, 1f),
      happy = if (shouldFillMoods) heuristicPreset.happy else song.happy.coerceIn(0f, 1f),
      calm = if (shouldFillMoods) heuristicPreset.calm else song.calm.coerceIn(0f, 1f),
      energetic = if (shouldFillMoods) heuristicPreset.energetic else song.energetic.coerceIn(0f, 1f),
      romantic = if (shouldFillMoods) heuristicPreset.romantic else song.romantic.coerceIn(0f, 1f),
      hasVocals = resolvedVocals,
      language = resolvedLanguage,
      hasMoodMetadata = true
    )
  }

  /**
   * Auto-fills missing mood vector metadata from genre + BPM + keyword heuristics.
   */
  fun enrichMoodMetadata(songs: List<Song>): List<Song> {
    return songs.map { enrich(it) }
  }

  private fun inferMoodPreset(
    genre: String,
    bpm: Int,
    moodTag: String,
    combinedText: String,
    seed: Int
  ): MoodPreset {
    val g = genre.lowercase(Locale.ROOT)
    val m = moodTag.lowercase(Locale.ROOT)
    // Deterministic subtle variation per track [-0.04, +0.04] so ties break naturally
    val micro = ((seed % 9) - 4) * 0.01f

    var energy = when {
      bpm >= 132 -> 0.85f
      bpm >= 116 -> 0.72f
      bpm >= 96 -> 0.54f
      bpm >= 80 -> 0.36f
      else -> 0.24f
    }

    var sad = 0.22f
    var chill = 0.38f
    var happy = 0.32f
    var calm = 0.34f
    var energetic = energy
    var romantic = 0.28f

    when {
      g.contains("lo-fi") || g.contains("lofi") || g.contains("ambient") -> {
        energy = 0.26f; sad = 0.38f; chill = 0.88f; calm = 0.86f; happy = 0.22f; energetic = 0.16f; romantic = 0.34f
      }
      g.contains("instrumental") || g.contains("classical") || g.contains("jazz") -> {
        energy = 0.30f; sad = 0.34f; chill = 0.76f; calm = 0.82f; happy = 0.26f; energetic = 0.20f; romantic = 0.40f
      }
      g.contains("r&b") || g.contains("soul") -> {
        energy = 0.42f; sad = 0.56f; chill = 0.78f; calm = 0.64f; happy = 0.28f; energetic = 0.32f; romantic = 0.72f
      }
      g.contains("indie") || g.contains("acoustic") || g.contains("folk") -> {
        energy = 0.40f; sad = 0.62f; chill = 0.74f; calm = 0.68f; happy = 0.34f; energetic = 0.30f; romantic = 0.54f
      }
      g.contains("phonk") || g.contains("electronic") || g.contains("edm") || g.contains("dance") -> {
        energy = 0.88f; sad = 0.10f; chill = 0.14f; calm = 0.12f; happy = 0.56f; energetic = 0.92f; romantic = 0.16f
      }
      g.contains("rock") || g.contains("metal") || g.contains("punk") -> {
        energy = 0.84f; sad = 0.24f; chill = 0.18f; calm = 0.14f; happy = 0.38f; energetic = 0.86f; romantic = 0.20f
      }
      g.contains("hip") || g.contains("rap") -> {
        energy = 0.64f; sad = 0.34f; chill = 0.52f; calm = 0.36f; happy = 0.34f; energetic = 0.66f; romantic = 0.28f
      }
      else -> {
        // Pop / Ballad / K-Pop / Khmer / General
        if (bpm <= 96) {
          energy = 0.38f; sad = 0.64f; chill = 0.72f; calm = 0.64f; happy = 0.32f; energetic = 0.30f; romantic = 0.60f
        } else {
          energy = 0.62f; sad = 0.28f; chill = 0.48f; calm = 0.40f; happy = 0.64f; energetic = 0.62f; romantic = 0.48f
        }
      }
    }

    // Apply mood label hints
    if (m.contains("sad") || m.contains("moody") || m.contains("night")) {
      sad = (sad + 0.20f).coerceAtMost(0.95f)
      chill = (chill + 0.14f).coerceAtMost(0.95f)
      energy = (energy - 0.08f).coerceAtLeast(0.12f)
    }
    if (m.contains("chill") || m.contains("focus") || m.contains("calm")) {
      chill = (chill + 0.18f).coerceAtMost(0.96f)
      calm = (calm + 0.18f).coerceAtMost(0.96f)
    }
    if (m.contains("energetic") || m.contains("workout") || m.contains("hype")) {
      energetic = (energetic + 0.20f).coerceAtMost(0.98f)
      energy = (energy + 0.18f).coerceAtMost(0.98f)
    }

    // Apply lyrical/title keyword nudges
    if (sadKeywords.any { combinedText.contains(it) }) {
      sad = (sad + 0.28f).coerceAtMost(0.96f)
      chill = (chill + 0.14f).coerceAtMost(0.94f)
      happy = (happy - 0.18f).coerceAtLeast(0.05f)
      energy = (energy - 0.12f).coerceAtLeast(0.14f)
    }
    if (chillKeywords.any { combinedText.contains(it) }) {
      chill = (chill + 0.24f).coerceAtMost(0.96f)
      calm = (calm + 0.22f).coerceAtMost(0.96f)
      energetic = (energetic - 0.12f).coerceAtLeast(0.08f)
    }
    if (romanticKeywords.any { combinedText.contains(it) }) {
      romantic = (romantic + 0.30f).coerceAtMost(0.96f)
      chill = (chill + 0.10f).coerceAtMost(0.92f)
    }
    if (happyKeywords.any { combinedText.contains(it) }) {
      happy = (happy + 0.28f).coerceAtMost(0.96f)
      sad = (sad - 0.16f).coerceAtLeast(0.05f)
    }
    if (energeticKeywords.any { combinedText.contains(it) }) {
      energetic = (energetic + 0.24f).coerceAtMost(0.98f)
      energy = (energy + 0.20f).coerceAtMost(0.98f)
      calm = (calm - 0.15f).coerceAtLeast(0.05f)
    }

    // BPM fine-tuning
    if (bpm <= 88) {
      calm = (calm + 0.08f).coerceAtMost(0.96f)
      chill = (chill + 0.08f).coerceAtMost(0.96f)
    } else if (bpm >= 126) {
      energy = (energy + 0.08f).coerceAtMost(0.98f)
      energetic = (energetic + 0.08f).coerceAtMost(0.98f)
    }

    return MoodPreset(
      energy = (energy + micro).coerceIn(0.05f, 0.98f),
      sad = (sad + micro).coerceIn(0.04f, 0.98f),
      chill = (chill + micro).coerceIn(0.04f, 0.98f),
      happy = (happy - micro).coerceIn(0.04f, 0.98f),
      calm = (calm + micro).coerceIn(0.04f, 0.98f),
      energetic = (energetic - micro).coerceIn(0.04f, 0.98f),
      romantic = (romantic + micro).coerceIn(0.04f, 0.98f)
    )
  }

  private fun inferHasVocals(
    genre: String,
    combinedText: String,
    existingFlag: Boolean
  ): Boolean {
    if (!existingFlag) return false
    val g = genre.lowercase(Locale.ROOT)
    if (g.contains("instrumental") || g.contains("classical")) return false
    if (instrumentalKeywords.any { combinedText.contains(it) }) return false
    return true
  }

  private fun inferLanguage(title: String, artist: String, combinedText: String): String {
    val text = "$title $artist"
    for (ch in text) {
      val code = ch.code
      when {
        code in 0x1780..0x17FF -> return "km"
        code in 0xAC00..0xD7AF -> return "ko"
        code in 0x3040..0x30FF -> return "ja"
        code in 0x4E00..0x9FFF -> return "zh"
      }
    }
    return when {
      combinedText.contains("khmer") -> "km"
      combinedText.contains("k-pop") || combinedText.contains("kpop") -> "ko"
      combinedText.contains("j-pop") || combinedText.contains("jpop") -> "ja"
      combinedText.contains("latin") || combinedText.contains("reggaeton") -> "es"
      else -> "en"
    }
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
