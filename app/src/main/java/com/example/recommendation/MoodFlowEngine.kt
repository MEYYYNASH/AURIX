package com.example.recommendation

import com.example.model.Song
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * MoodFlow — Intelligent Mood Shuffle Engine.
 *
 * Replaces plain uniform random shuffle with a mood-aware weighted roulette:
 * 1. Builds a Current Mood Profile from the active song, optionally blending the last
 *    3 songs (0.60 current / 0.25 prev-1 / 0.15 prev-2).
 * 2. Excludes the last 20 played songs (plus the active song) from the candidate pool.
 * 3. Scores candidates via: `1 - weightedDistance(70% mood vector, 15% energy, 15% BPM)`
 *    modulated by skip penalties and completion boosts.
 * 4. Chooses a selection bucket probabilistically:
 *    60% same mood, 25% similar genre/artist, 10% favorites, 5% discovery.
 * 5. Performs weighted random selection inside the chosen bucket (score as weight).
 * 6. Falls back to normal random selection if the filtered candidate pool is empty.
 */
object MoodFlowEngine {

  const val MAX_LAST_PLAYED_EXCLUSION = 20

  /**
   * Builds a target [CurrentMoodProfile] from [currentSong] and up to 2 prior songs
   * in [previousSongs] (newest first: previousSongs[0] = immediately before currentSong).
   */
  fun buildCurrentMoodProfile(
    currentSong: Song,
    previousSongs: List<Song> = emptyList(),
    weights: MoodFlowWeights = MoodFlowWeights()
  ): CurrentMoodProfile {
    val enrichedCurrent = TrackMetadataClassifier.enrich(currentSong)
    val recentChain = buildList {
      add(enrichedCurrent to weights.blendWeights.first)
      previousSongs.getOrNull(0)?.let { prev1 ->
        add(TrackMetadataClassifier.enrich(prev1) to weights.blendWeights.second)
      }
      previousSongs.getOrNull(1)?.let { prev2 ->
        add(TrackMetadataClassifier.enrich(prev2) to weights.blendWeights.third)
      }
    }

    val weightSum = recentChain.sumOf { it.second }.coerceAtLeast(1e-6)

    var happy = 0.0
    var sad = 0.0
    var calm = 0.0
    var energetic = 0.0
    var romantic = 0.0
    var energy = 0.0
    var bpm = 0.0

    for ((song, w) in recentChain) {
      val nw = w / weightSum
      happy += song.happy * nw
      sad += song.sad * nw
      calm += song.calm * nw
      energetic += song.energetic * nw
      romantic += song.romantic * nw
      energy += song.energy * nw
      bpm += song.bpm.coerceIn(50, 190) * nw
    }

    return CurrentMoodProfile(
      mood = MoodVector(
        happy = happy.toFloat().coerceIn(0f, 1f),
        sad = sad.toFloat().coerceIn(0f, 1f),
        calm = calm.toFloat().coerceIn(0f, 1f),
        energetic = energetic.toFloat().coerceIn(0f, 1f),
        romantic = romantic.toFloat().coerceIn(0f, 1f)
      ),
      energy = energy.toFloat().coerceIn(0f, 1f),
      bpm = bpm.toFloat().coerceIn(50f, 190f),
      primaryGenre = enrichedCurrent.genre,
      primaryArtist = enrichedCurrent.artist
    )
  }

  /**
   * Computes raw sonic/mood match in [0.0, 1.0]:
   *   `1 - (0.70 * moodDistance + 0.15 * energyDistance + 0.15 * bpmDistance)`
   */
  fun computeBaseMatchScore(
    profile: CurrentMoodProfile,
    candidate: Song,
    weights: MoodFlowWeights = MoodFlowWeights()
  ): Double {
    val enriched = TrackMetadataClassifier.enrich(candidate)
    val candMood = MoodVector.fromSong(enriched)

    // Average L1 distance across the 5 mood dimensions [0..1]
    val moodDistance = (
      abs(profile.mood.happy - candMood.happy) +
        abs(profile.mood.sad - candMood.sad) +
        abs(profile.mood.calm - candMood.calm) +
        abs(profile.mood.energetic - candMood.energetic) +
        abs(profile.mood.romantic - candMood.romantic)
      ) / 5.0

    val energyDistance = abs(profile.energy - enriched.energy).toDouble()
    val bpmDistance = (abs(profile.bpm - enriched.bpm).toDouble() / 100.0).coerceIn(0.0, 1.0)

    val totalWeight = (weights.moodWeight + weights.energyWeight + weights.bpmWeight)
      .coerceAtLeast(1e-6)
    val weightedDistance = (
      (weights.moodWeight * moodDistance) +
        (weights.energyWeight * energyDistance) +
        (weights.bpmWeight * bpmDistance)
      ) / totalWeight

    return (1.0 - weightedDistance).coerceIn(0.0, 1.0)
  }

  /**
   * Computes adjusted selection weight by applying skip penalties and completion boosts
   * from [playHistory] on top of [computeBaseMatchScore].
   */
  fun computeAdjustedScore(
    profile: CurrentMoodProfile,
    candidate: Song,
    playHistory: List<RichPlayRecord>,
    weights: MoodFlowWeights = MoodFlowWeights()
  ): Pair<Double, Int> {
    val baseMatch = computeBaseMatchScore(profile, candidate, weights)
    val matchPercent = (baseMatch * 100.0).roundToInt().coerceIn(1, 99)

    val historyForSong = playHistory
      .filter { it.song.id == candidate.id }
      .sortedByDescending { it.playedAtMs }

    if (historyForSong.isEmpty()) {
      return baseMatch.coerceAtLeast(0.01) to matchPercent
    }

    val skipRate = historyForSong.count { it.skipped }.toDouble() / historyForSong.size.toDouble()
    val completionRate = historyForSong.count { it.completed || it.completedPercent >= 0.80f }
      .toDouble() / historyForSong.size.toDouble()
    val lastWasSkipped = historyForSong.firstOrNull()?.skipped == true

    // Penalize recently skipped tracks; boost consistently completed tracks
    val skipMultiplier = (1.0 - skipRate * 0.55) * (if (lastWasSkipped) 0.70 else 1.0)
    val completionMultiplier = 1.0 + (completionRate * 0.25)

    val adjusted = (baseMatch * skipMultiplier * completionMultiplier).coerceAtLeast(0.005)
    return adjusted to matchPercent
  }

  /**
   * Picks the next song according to MoodFlow's bucketed weighted shuffle algorithm.
   */
  fun pickNextSong(
    currentSong: Song,
    allSongs: List<Song>,
    playHistory: List<RichPlayRecord>,
    favoriteIds: Set<String> = emptySet(),
    recentPlayedSongs: List<Song> = emptyList(),
    weights: MoodFlowWeights = MoodFlowWeights(),
    random: Random = Random.Default
  ): MoodFlowPick? {
    val distinctPool = allSongs
      .asSequence()
      .map { TrackMetadataClassifier.enrich(it) }
      .distinctBy { it.id }
      .toList()

    if (distinctPool.isEmpty()) return null

    val last20PlayedIds = playHistory
      .sortedByDescending { it.playedAtMs }
      .map { it.song.id }
      .distinct()
      .take(MAX_LAST_PLAYED_EXCLUSION)
      .toSet()

    val candidatePool = distinctPool.filter { song ->
      song.id != currentSong.id && song.id !in last20PlayedIds
    }

    val profile = buildCurrentMoodProfile(
      currentSong = currentSong,
      previousSongs = recentPlayedSongs.filter { it.id != currentSong.id }.take(2),
      weights = weights
    )

    // Fallback to normal random if the filtered candidate pool is empty
    if (candidatePool.isEmpty()) {
      val fallbackCandidates = distinctPool.filter { it.id != currentSong.id }.ifEmpty { distinctPool }
      val chosen = fallbackCandidates[random.nextInt(fallbackCandidates.size)]
      val (_, pct) = computeAdjustedScore(profile, chosen, playHistory, weights)
      return MoodFlowPick(
        song = chosen,
        matchPercent = pct,
        score = pct / 100.0,
        bucket = MoodFlowBucket.RANDOM_FALLBACK,
        reason = "Shuffle · $pct% mood match"
      )
    }

    val scoredCandidates = candidatePool.map { song ->
      val (adjScore, matchPct) = computeAdjustedScore(profile, song, playHistory, weights)
      val playCount = playHistory.count { it.song.id == song.id }
      ScoredCandidate(song = song, score = adjScore, matchPercent = matchPct, playCount = playCount)
    }

    // Partition candidates into the 4 MoodFlow buckets
    val sortedByScore = scoredCandidates.sortedByDescending { it.score }
    val sameMoodCount = max(1, minOf(sortedByScore.size, max(3, (sortedByScore.size * 0.45).roundToInt())))
    val sameMoodBucket = sortedByScore.take(sameMoodCount)

    val normCurrentArtist = normalizeArtist(profile.primaryArtist)
    val similarBucket = scoredCandidates.filter { cand ->
      cand.song.genre.equals(profile.primaryGenre, ignoreCase = true) ||
        (normCurrentArtist.isNotBlank() && normalizeArtist(cand.song.artist) == normCurrentArtist)
    }

    val favoritesBucket = scoredCandidates.filter { it.song.id in favoriteIds }

    val minPlays = scoredCandidates.minOf { it.playCount }
    val discoveryBucket = scoredCandidates.filter { it.playCount <= max(1, minPlays) }

    val requestedBucket = selectBucket(random = random, weights = weights)
    val (resolvedBucket, activeBucketItems) = resolveNonEmptyBucket(
      requested = requestedBucket,
      sameMood = sameMoodBucket,
      similar = similarBucket,
      favorites = favoritesBucket,
      discovery = discoveryBucket,
      allScored = sortedByScore
    )

    val picked = weightedRandomSelect(activeBucketItems, random)
    val reason = when (resolvedBucket) {
      MoodFlowBucket.SAME_MOOD -> "MoodFlow · ${picked.matchPercent}% match"
      MoodFlowBucket.SIMILAR_GENRE_ARTIST -> "Similar ${profile.primaryGenre} vibe · ${picked.matchPercent}%"
      MoodFlowBucket.FAVORITES -> "From your Favorites · ${picked.matchPercent}% match"
      MoodFlowBucket.DISCOVERY -> "Fresh discovery · ${picked.matchPercent}% match"
      MoodFlowBucket.RANDOM_FALLBACK -> "Shuffle · ${picked.matchPercent}% match"
    }

    return MoodFlowPick(
      song = picked.song,
      matchPercent = picked.matchPercent,
      score = picked.score,
      bucket = resolvedBucket,
      reason = reason
    )
  }

  /**
   * Selects a MoodFlow bucket using the configured 60% / 25% / 10% / 5% roulette probabilities.
   */
  fun selectBucket(
    random: Random = Random.Default,
    weights: MoodFlowWeights = MoodFlowWeights()
  ): MoodFlowBucket {
    val pSame = weights.sameMoodProbability.coerceAtLeast(0.0)
    val pSim = weights.similarGenreArtistProbability.coerceAtLeast(0.0)
    val pFav = weights.favoritesProbability.coerceAtLeast(0.0)
    val pDisc = weights.discoveryProbability.coerceAtLeast(0.0)
    val total = (pSame + pSim + pFav + pDisc).coerceAtLeast(1e-6)

    val roll = random.nextDouble() * total
    return when {
      roll < pSame -> MoodFlowBucket.SAME_MOOD
      roll < pSame + pSim -> MoodFlowBucket.SIMILAR_GENRE_ARTIST
      roll < pSame + pSim + pFav -> MoodFlowBucket.FAVORITES
      else -> MoodFlowBucket.DISCOVERY
    }
  }

  /**
   * Weighted random roulette selection inside a bucket using `score` as weight.
   */
  internal fun weightedRandomSelect(
    items: List<ScoredCandidate>,
    random: Random = Random.Default
  ): ScoredCandidate {
    if (items.size == 1) return items.first()
    val totalWeight = items.sumOf { it.score.coerceAtLeast(0.001) }
    if (totalWeight <= 0.0) {
      return items[random.nextInt(items.size)]
    }
    var cursor = random.nextDouble() * totalWeight
    for (item in items) {
      cursor -= item.score.coerceAtLeast(0.001)
      if (cursor <= 0.0) return item
    }
    return items.last()
  }

  private fun resolveNonEmptyBucket(
    requested: MoodFlowBucket,
    sameMood: List<ScoredCandidate>,
    similar: List<ScoredCandidate>,
    favorites: List<ScoredCandidate>,
    discovery: List<ScoredCandidate>,
    allScored: List<ScoredCandidate>
  ): Pair<MoodFlowBucket, List<ScoredCandidate>> {
    val requestedItems = when (requested) {
      MoodFlowBucket.SAME_MOOD -> sameMood
      MoodFlowBucket.SIMILAR_GENRE_ARTIST -> similar
      MoodFlowBucket.FAVORITES -> favorites
      MoodFlowBucket.DISCOVERY -> discovery
      MoodFlowBucket.RANDOM_FALLBACK -> allScored
    }
    if (requestedItems.isNotEmpty()) return requested to requestedItems

    // Graceful fallback chain when a specialized bucket (e.g. Favorites) has no eligible candidates
    if (sameMood.isNotEmpty()) return MoodFlowBucket.SAME_MOOD to sameMood
    if (similar.isNotEmpty()) return MoodFlowBucket.SIMILAR_GENRE_ARTIST to similar
    if (discovery.isNotEmpty()) return MoodFlowBucket.DISCOVERY to discovery
    return MoodFlowBucket.RANDOM_FALLBACK to allScored
  }

  internal data class ScoredCandidate(
    val song: Song,
    val score: Double,
    val matchPercent: Int,
    val playCount: Int
  )

  private fun normalizeArtist(artist: String): String {
    return artist.trim().lowercase(Locale.ROOT)
  }
}
