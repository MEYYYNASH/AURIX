package com.example.recommendation

import com.example.model.Song
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * "Because You Listened" Recommendation Engine.
 *
 * Recommends vocal/lyrical tracks with a sad or chill mood tailored to the user's
 * 30-day listening history, completion/replay behavior, favorites, and explicit feedback.
 *
 * Weight breakdown:
 * - 40% Mood similarity (sad & chill closeness to user taste + mix fit)
 * - 20% Genre & artist affinity
 * - 15% Energy & BPM closeness
 * - 15% Completion, replay & favorite history signal
 * - 10% Discovery factor (fresh tracks / under-played artists)
 */
object BecauseYouListenedEngine {

  const val TASTE_WINDOW_MS = 30L * 24L * 60L * 60L * 1000L
  const val COLD_START_THRESHOLD_PLAYS = 10
  const val MAX_LAST_PLAYED_EXCLUSION = 20
  const val MAX_CONSECUTIVE_PER_ARTIST = 2
  const val LIST_LIMIT = 20

  // Scoring weights (sum = 1.00)
  const val WEIGHT_MOOD = 0.40
  const val WEIGHT_GENRE_ARTIST = 0.20
  const val WEIGHT_ENERGY_BPM = 0.15
  const val WEIGHT_COMPLETION_HISTORY = 0.15
  const val WEIGHT_DISCOVERY = 0.10

  private data class PositiveReference(
    val song: Song,
    val weight: Double,
    val isFavoriteOnly: Boolean
  )

  /**
   * Builds a 30-day weighted User Taste Profile.
   * Completed, replayed, favorited, and liked tracks increase influence;
   * skipped and disliked tracks contribute negatively to genre/artist affinity.
   */
  fun buildUserTasteProfile(
    playHistory: List<RichPlayRecord>,
    favorites: List<Song>,
    favoriteIds: Set<String> = favorites.map { it.id }.toSet(),
    feedbackBySongId: Map<String, Boolean> = emptyMap(),
    nowMs: Long = System.currentTimeMillis()
  ): UserTasteProfile {
    val cutoff = nowMs - TASTE_WINDOW_MS
    val recentPlays = playHistory
      .filter { it.playedAtMs >= cutoff }
      .map { it.copy(song = TrackMetadataClassifier.enrich(it.song)) }

    val enrichedFavorites = favorites.map { TrackMetadataClassifier.enrich(it) }
    val isColdStart = recentPlays.size < COLD_START_THRESHOLD_PLAYS

    val genreScores = mutableMapOf<String, Double>()
    val artistScores = mutableMapOf<String, Double>()
    val languageScores = mutableMapOf<String, Double>()

    var weightedSadSum = 0.0
    var weightedChillSum = 0.0
    var weightedEnergySum = 0.0
    var weightedBpmSum = 0.0
    var totalContinuousWeight = 0.0

    for (play in recentPlays) {
      val s = play.song
      val fb = feedbackBySongId[s.id]
      val isFav = s.id in favoriteIds

      // Base weight + positive engagement multipliers
      val positiveWeight = 1.0 +
        (play.completedPercent.coerceIn(0f, 1f) * 1.2) +
        (if (play.completed) 0.8 else 0.0) +
        (if (play.replayed) 1.0 else 0.0) +
        (if (isFav) 1.5 else 0.0) +
        (if (fb == true) 1.2 else 0.0)

      // Skipped & disliked penalty (negative impact)
      val penalty = (if (play.skipped) 1.65 else 0.0) + (if (fb == false) 2.2 else 0.0)
      val netAffinityWeight = positiveWeight - penalty

      val genreKey = s.genre.trim().lowercase(Locale.ROOT)
      val artistKey = normalizeArtist(s.artist)
      val langKey = s.language.trim().lowercase(Locale.ROOT).ifBlank { "en" }

      if (genreKey.isNotBlank()) {
        genreScores[genreKey] = (genreScores[genreKey] ?: 0.0) + netAffinityWeight
      }
      if (artistKey.isNotBlank()) {
        artistScores[artistKey] = (artistScores[artistKey] ?: 0.0) + netAffinityWeight
      }
      languageScores[langKey] = (languageScores[langKey] ?: 0.0) + netAffinityWeight

      // Only non-disliked, non-purely-skipped weights steer continuous mood/energy/BPM averages
      if (fb != false && (!play.skipped || isFav || play.replayed)) {
        val contWeight = positiveWeight.coerceAtLeast(0.1)
        weightedSadSum += s.sad * contWeight
        weightedChillSum += s.chill * contWeight
        weightedEnergySum += s.energy * contWeight
        weightedBpmSum += s.bpm.coerceIn(50, 190) * contWeight
        totalContinuousWeight += contWeight
      }
    }

    // Favorites not recently played still shape the user's baseline profile (and drive cold-start)
    val recentIds = recentPlays.map { it.song.id }.toSet()
    for (fav in enrichedFavorites) {
      if (fav.id !in recentIds && feedbackBySongId[fav.id] != false) {
        val favWeight = 2.2
        val genreKey = fav.genre.trim().lowercase(Locale.ROOT)
        val artistKey = normalizeArtist(fav.artist)
        val langKey = fav.language.trim().lowercase(Locale.ROOT).ifBlank { "en" }

        if (genreKey.isNotBlank()) genreScores[genreKey] = (genreScores[genreKey] ?: 0.0) + favWeight
        if (artistKey.isNotBlank()) artistScores[artistKey] = (artistScores[artistKey] ?: 0.0) + favWeight
        languageScores[langKey] = (languageScores[langKey] ?: 0.0) + favWeight

        weightedSadSum += fav.sad * favWeight
        weightedChillSum += fav.chill * favWeight
        weightedEnergySum += fav.energy * favWeight
        weightedBpmSum += fav.bpm.coerceIn(50, 190) * favWeight
        totalContinuousWeight += favWeight
      }
    }

    val avgSad = if (totalContinuousWeight > 0.0) {
      (weightedSadSum / totalContinuousWeight).toFloat().coerceIn(0f, 1f)
    } else {
      0.65f
    }
    val avgChill = if (totalContinuousWeight > 0.0) {
      (weightedChillSum / totalContinuousWeight).toFloat().coerceIn(0f, 1f)
    } else {
      0.68f
    }
    val avgEnergy = if (totalContinuousWeight > 0.0) {
      (weightedEnergySum / totalContinuousWeight).toFloat().coerceIn(0f, 1f)
    } else {
      0.40f
    }
    val avgBpm = if (totalContinuousWeight > 0.0) {
      (weightedBpmSum / totalContinuousWeight).toFloat().coerceIn(55f, 180f)
    } else {
      92f
    }

    return UserTasteProfile(
      avgSad = avgSad,
      avgChill = avgChill,
      avgEnergy = avgEnergy,
      avgBpm = avgBpm,
      topGenres = normalizePositiveScores(genreScores),
      topArtists = normalizePositiveScores(artistScores),
      topLanguages = normalizePositiveScores(languageScores),
      totalRecentPlays = recentPlays.size,
      isColdStart = isColdStart
    )
  }

  /**
   * Generates "Sad Vibes for You" and "Chill Mix for You" (up to 20 vocal songs each),
   * excluding the last 20 played songs and any disliked songs, with Match %, reasons,
   * cold-start fallback, and max 2 consecutive tracks per artist.
   */
  fun recommend(
    catalog: List<Song>,
    playHistory: List<RichPlayRecord>,
    favorites: List<Song>,
    favoriteIds: Set<String> = favorites.map { it.id }.toSet(),
    feedbackBySongId: Map<String, Boolean> = emptyMap(),
    popularityBySongId: Map<String, Int> = emptyMap(),
    nowMs: Long = System.currentTimeMillis()
  ): BecauseYouListenedResult {
    val profile = buildUserTasteProfile(
      playHistory = playHistory,
      favorites = favorites,
      favoriteIds = favoriteIds,
      feedbackBySongId = feedbackBySongId,
      nowMs = nowMs
    )

    val enrichedCatalog = catalog
      .asSequence()
      .map { TrackMetadataClassifier.enrich(it) }
      .distinctBy { it.id }
      .toList()

    if (enrichedCatalog.isEmpty()) {
      return BecauseYouListenedResult(tasteProfile = profile, isColdStart = profile.isColdStart)
    }

    // Last 20 unique played song IDs (newest first)
    val last20PlayedIds = playHistory
      .sortedByDescending { it.playedAtMs }
      .map { it.song.id }
      .distinct()
      .take(MAX_LAST_PLAYED_EXCLUSION)
      .toSet()

    // Filter candidate pool: must have vocals, must be sad >= 0.5 or chill >= 0.5,
    // and must NOT be in last 20 played or disliked.
    val candidates = enrichedCatalog.filter { song ->
      val disliked = feedbackBySongId[song.id] == false
      val meetsVocalAndMood = song.hasVocals && (song.sad >= 0.5f || song.chill >= 0.5f)
      meetsVocalAndMood && !disliked && song.id !in last20PlayedIds
    }

    if (candidates.isEmpty()) {
      return BecauseYouListenedResult(tasteProfile = profile, isColdStart = profile.isColdStart)
    }

    val playStatsBySongId = playHistory.groupBy { it.song.id }
    val references = buildPositiveReferences(
      playHistory = playHistory,
      favorites = favorites,
      favoriteIds = favoriteIds,
      feedbackBySongId = feedbackBySongId,
      nowMs = nowMs
    )

    val maxPopularity = (popularityBySongId.values.maxOrNull() ?: 1).coerceAtLeast(1)

    val sadCandidates = candidates
      .filter { it.sad >= 0.5f }
      .map { song ->
        scoreCandidate(
          song = song,
          focusSad = true,
          profile = profile,
          references = references,
          playStatsBySongId = playStatsBySongId,
          favoriteIds = favoriteIds,
          feedbackBySongId = feedbackBySongId,
          popularityBySongId = popularityBySongId,
          maxPopularity = maxPopularity
        )
      }
      .sortedByDescending { it.score }

    val chillCandidates = candidates
      .filter { it.chill >= 0.5f }
      .map { song ->
        scoreCandidate(
          song = song,
          focusSad = false,
          profile = profile,
          references = references,
          playStatsBySongId = playStatsBySongId,
          favoriteIds = favoriteIds,
          feedbackBySongId = feedbackBySongId,
          popularityBySongId = popularityBySongId,
          maxPopularity = maxPopularity
        )
      }
      .sortedByDescending { it.score }

    val sadDiversified = enforceArtistDiversity(sadCandidates, MAX_CONSECUTIVE_PER_ARTIST).take(LIST_LIMIT)
    val chillDiversified = enforceArtistDiversity(chillCandidates, MAX_CONSECUTIVE_PER_ARTIST).take(LIST_LIMIT)

    return BecauseYouListenedResult(
      sadVibes = sadDiversified,
      chillMix = chillDiversified,
      tasteProfile = profile,
      isColdStart = profile.isColdStart
    )
  }

  private fun scoreCandidate(
    song: Song,
    focusSad: Boolean,
    profile: UserTasteProfile,
    references: List<PositiveReference>,
    playStatsBySongId: Map<String, List<RichPlayRecord>>,
    favoriteIds: Set<String>,
    feedbackBySongId: Map<String, Boolean>,
    popularityBySongId: Map<String, Int>,
    maxPopularity: Int
  ): VocalRecommendationItem {
    val genreKey = song.genre.trim().lowercase(Locale.ROOT)
    val artistKey = normalizeArtist(song.artist)
    val langKey = song.language.trim().lowercase(Locale.ROOT).ifBlank { "en" }
    val songFeedback = feedbackBySongId[song.id]
    val isFavorite = song.id in favoriteIds

    // 1. Mood similarity (40%)
    val sadDiff = abs(song.sad - profile.avgSad).toDouble()
    val chillDiff = abs(song.chill - profile.avgChill).toDouble()
    val baseMoodSim = (1.0 - ((sadDiff + chillDiff) / 2.0)).coerceIn(0.0, 1.0)
    val focusIntensity = if (focusSad) song.sad.toDouble() else song.chill.toDouble()
    val moodScore = if (profile.isColdStart) {
      // Cold start favors strong sad/chill intensity blended with favorite profile
      (0.55 * focusIntensity + 0.45 * baseMoodSim).coerceIn(0.0, 1.0)
    } else {
      (0.70 * baseMoodSim + 0.30 * focusIntensity).coerceIn(0.0, 1.0)
    }

    // 2. Genre & Artist affinity (20%)
    val genreAff = profile.topGenres[genreKey] ?: 0.0
    val artistAff = profile.topArtists[artistKey] ?: 0.0
    val langBonus = (profile.topLanguages[langKey] ?: 0.0) * 0.15
    val genreArtistScore = (0.55 * genreAff + 0.45 * artistAff + langBonus).coerceIn(0.0, 1.0)

    // 3. Energy & BPM closeness (15%)
    val energyClose = (1.0 - abs(song.energy - profile.avgEnergy).toDouble()).coerceIn(0.0, 1.0)
    val bpmClose = (1.0 - (abs(song.bpm - profile.avgBpm).toDouble() / 60.0)).coerceIn(0.0, 1.0)
    val energyBpmScore = (0.5 * energyClose + 0.5 * bpmClose).coerceIn(0.0, 1.0)

    // 4. Completion / Favorite / Popularity history (15%)
    val trackLogs = playStatsBySongId[song.id].orEmpty()
    val completionScore = if (trackLogs.isNotEmpty()) {
      val avgComp = trackLogs.map { it.completedPercent.coerceIn(0f, 1f).toDouble() }.average()
      val replayRate = trackLogs.count { it.replayed }.toDouble() / trackLogs.size
      val skipRate = trackLogs.count { it.skipped }.toDouble() / trackLogs.size
      (0.55 * avgComp + 0.25 * replayRate - 0.50 * skipRate + (if (isFavorite) 0.35 else 0.0))
        .coerceIn(0.0, 1.0)
    } else {
      val popNorm = (popularityBySongId[song.id] ?: 0).toDouble() / maxPopularity.toDouble()
      when {
        isFavorite && songFeedback == true -> 0.95
        isFavorite -> 0.85
        songFeedback == true -> 0.80
        profile.isColdStart -> (0.45 + 0.55 * popNorm).coerceIn(0.0, 1.0)
        else -> (0.35 + 0.35 * popNorm).coerceIn(0.0, 1.0)
      }
    }

    // 5. Discovery factor (10%)
    val playCount = trackLogs.size
    val artistSeen = (profile.topArtists[artistKey] ?: 0.0) > 0.0
    val discoveryScore = when {
      playCount == 0 && !artistSeen -> 1.0
      playCount == 0 -> 0.78
      playCount == 1 -> 0.55
      else -> (1.0 / (playCount + 1)).coerceIn(0.10, 0.45)
    }

    // Weighted total in 0.0..1.0
    var totalScore = (moodScore * WEIGHT_MOOD) +
      (genreArtistScore * WEIGHT_GENRE_ARTIST) +
      (energyBpmScore * WEIGHT_ENERGY_BPM) +
      (completionScore * WEIGHT_COMPLETION_HISTORY) +
      (discoveryScore * WEIGHT_DISCOVERY)

    // Small cold-start boost for user favorites and popular sad/chill tracks
    if (profile.isColdStart) {
      val popBonus = ((popularityBySongId[song.id] ?: 0).toDouble() / maxPopularity) * 0.14
      val favBonus = if (isFavorite) 0.18 else 0.0
      totalScore = (totalScore + popBonus + favBonus).coerceIn(0.0, 1.0)
    } else if (songFeedback == true) {
      totalScore = (totalScore + 0.06).coerceIn(0.0, 1.0)
    }

    val matchPercent = (totalScore * 100.0).roundToInt().coerceIn(1, 99)
    val reason = buildReason(
      candidate = song,
      focusSad = focusSad,
      references = references,
      isColdStart = profile.isColdStart
    )

    return VocalRecommendationItem(
      song = song,
      matchPercent = matchPercent,
      score = totalScore,
      reason = reason,
      feedback = songFeedback
    )
  }

  /**
   * Enforces artist diversity so at most [maxConsecutive] tracks from the same artist
   * appear back-to-back, while preserving descending score order as closely as possible.
   */
  fun enforceArtistDiversity(
    sortedItems: List<VocalRecommendationItem>,
    maxConsecutive: Int = MAX_CONSECUTIVE_PER_ARTIST
  ): List<VocalRecommendationItem> {
    if (sortedItems.size <= maxConsecutive) return sortedItems

    val remaining = sortedItems.toMutableList()
    val result = ArrayList<VocalRecommendationItem>(sortedItems.size)

    while (remaining.isNotEmpty()) {
      val tailArtists = result.takeLast(maxConsecutive).map { normalizeArtist(it.song.artist) }
      val blockedArtist = if (
        tailArtists.size == maxConsecutive &&
        tailArtists.all { it.isNotBlank() && it == tailArtists.first() }
      ) {
        tailArtists.first()
      } else {
        null
      }

      val pickIndex = if (blockedArtist == null) {
        0
      } else {
        val alternativeIdx = remaining.indexOfFirst { normalizeArtist(it.song.artist) != blockedArtist }
        if (alternativeIdx >= 0) alternativeIdx else 0
      }

      result.add(remaining.removeAt(pickIndex))
    }

    return result
  }

  private fun buildPositiveReferences(
    playHistory: List<RichPlayRecord>,
    favorites: List<Song>,
    favoriteIds: Set<String>,
    feedbackBySongId: Map<String, Boolean>,
    nowMs: Long
  ): List<PositiveReference> {
    val cutoff = nowMs - TASTE_WINDOW_MS
    val bySong = mutableMapOf<String, PositiveReference>()

    playHistory
      .filter { it.playedAtMs >= cutoff && !it.skipped && feedbackBySongId[it.song.id] != false }
      .forEach { play ->
        val enriched = TrackMetadataClassifier.enrich(play.song)
        val w = 1.0 +
          play.completedPercent.coerceIn(0f, 1f) +
          (if (play.replayed) 1.0 else 0.0) +
          (if (play.song.id in favoriteIds) 1.5 else 0.0)
        val existing = bySong[enriched.id]
        if (existing == null || w > existing.weight) {
          bySong[enriched.id] = PositiveReference(enriched, w, isFavoriteOnly = false)
        }
      }

    favorites.forEach { fav ->
      if (feedbackBySongId[fav.id] != false && fav.id !in bySong) {
        bySong[fav.id] = PositiveReference(
          song = TrackMetadataClassifier.enrich(fav),
          weight = 2.0,
          isFavoriteOnly = true
        )
      }
    }

    return bySong.values.sortedByDescending { it.weight }
  }

  private fun buildReason(
    candidate: Song,
    focusSad: Boolean,
    references: List<PositiveReference>,
    isColdStart: Boolean
  ): String {
    val bestRef = references.maxByOrNull { ref ->
      val sameArtist = if (normalizeArtist(ref.song.artist) == normalizeArtist(candidate.artist)) 0.6 else 0.0
      val sameGenre = if (ref.song.genre.equals(candidate.genre, ignoreCase = true)) 0.35 else 0.0
      val moodSim = 1.0 - ((abs(ref.song.sad - candidate.sad) + abs(ref.song.chill - candidate.chill)) / 2.0)
      sameArtist + sameGenre + moodSim + (ref.weight * 0.05)
    }

    if (bestRef != null) {
      return if (bestRef.isFavoriteOnly && isColdStart) {
        "Because you favorited ${bestRef.song.title}"
      } else {
        "Because you listened to ${bestRef.song.title}"
      }
    }

    return if (focusSad) {
      "Popular sad vocal pick"
    } else {
      "Popular chill vocal pick"
    }
  }

  private fun normalizePositiveScores(raw: Map<String, Double>): Map<String, Double> {
    val positives = raw.filterValues { it > 0.0 }
    val maxVal = positives.values.maxOrNull() ?: return emptyMap()
    if (maxVal <= 0.0) return emptyMap()
    return positives.mapValues { (_, v) -> (v / maxVal).coerceIn(0.0, 1.0) }
  }

  private fun normalizeArtist(artist: String): String {
    return artist.trim().lowercase(Locale.ROOT)
  }
}
