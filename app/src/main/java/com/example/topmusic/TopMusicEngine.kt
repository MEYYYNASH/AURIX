package com.example.topmusic

import java.util.Locale

object TopMusicEngine {

  /**
   * Strips any ignored country chart entries (such as India "IN") everywhere in the pipeline.
   */
  fun sanitizeEntries(entries: List<TopMusicRawEntry>): List<TopMusicRawEntry> {
    return entries.mapNotNull { entry ->
      val cleanRanks = entry.countryRanks
        .map { it.copy(countryCode = it.countryCode.trim().uppercase(Locale.US)) }
        .filter { !TopMusicRegionsConfig.isIgnoredCountry(it.countryCode) && it.rankToday > 0 }
      if (cleanRanks.isEmpty()) null else entry.copy(countryRanks = cleanRanks)
    }
  }

  /**
   * Per country points: 21 - rank (top 20 only).
   */
  fun pointsForRank(rank: Int): Int {
    return if (rank in 1..20) 21 - rank else 0
  }

  /**
   * Smoothed points for Weekly Top Songs: uses average of today's and previous rank.
   */
  fun smoothedPointsForRank(smoothedRank: Double): Double {
    return if (smoothedRank > 0.0 && smoothedRank <= 20.0) 21.0 - smoothedRank else 0.0
  }

  /**
   * Total = sum of points * (1 + 0.25 * (countries charting - 1))
   */
  fun computeRegionalScore(pointsSum: Double, countriesCharting: Int): Double {
    if (countriesCharting <= 0 || pointsSum <= 0.0) return 0.0
    val multiplier = 1.0 + 0.25 * (countriesCharting - 1).coerceAtLeast(0)
    return pointsSum * multiplier
  }

  fun multiplierForCountries(countriesCharting: Int): Double {
    if (countriesCharting <= 1) return 1.0
    return 1.0 + 0.25 * (countriesCharting - 1)
  }

  fun rankTracks(
    rawEntries: List<TopMusicRawEntry>,
    region: TopMusicRegion,
    filter: TopMusicFilter,
    searchQuery: String = ""
  ): List<TopMusicRankedItem> {
    val sanitized = sanitizeEntries(rawEntries)
    val allowedCountries = TopMusicRegionsConfig.countriesForRegion(region)
    val query = searchQuery.trim().lowercase(Locale.ROOT)
    val isKhmerTab = region == TopMusicRegion.KHMER

    val candidates = sanitized
      .filter { it.chartType == filter.chartType }
      .mapNotNull { entry ->
        val regionRanks = entry.countryRanks
          .filter { it.countryCode in allowedCountries }
          .sortedBy { it.rankToday }

        if (regionRanks.isEmpty()) return@mapNotNull null

        if (query.isNotBlank()) {
          val titleMatch = entry.title.lowercase(Locale.ROOT).contains(query)
          val artistMatch = entry.artist.lowercase(Locale.ROOT).contains(query)
          if (!titleMatch && !artistMatch) return@mapNotNull null
        }

        val totalGained = regionRanks.sumOf { it.placesGained }
        val hasNewEntry = regionRanks.any { it.isNewEntry }

        when (filter) {
          TopMusicFilter.RISING_FAST -> if (totalGained <= 0) return@mapNotNull null
          TopMusicFilter.NEW_TODAY -> if (!hasNewEntry) return@mapNotNull null
          else -> Unit
        }

        val useSmoothed = filter == TopMusicFilter.TOP_SONGS
        val breakdowns = regionRanks.map { cr ->
          val pts = if (useSmoothed) {
            smoothedPointsForRank(cr.smoothedRank)
          } else {
            pointsForRank(cr.rankToday).toDouble()
          }
          CountryRankBreakdown(
            countryCode = cr.countryCode,
            flagEmoji = TopMusicRegionsConfig.flagForCountry(cr.countryCode),
            countryName = TopMusicRegionsConfig.countryDisplayName(cr.countryCode),
            rankToday = cr.rankToday,
            previousRank = cr.previousRank,
            smoothedRank = cr.smoothedRank,
            movement = cr.signedMovement,
            isNewEntry = cr.isNewEntry,
            points = pts
          )
        }

        val basePoints = breakdowns.sumOf { it.points }
        val countriesCount = breakdowns.size
        val khmerRank = if (isKhmerTab) regionRanks.first().rankToday else null
        val khmerSmoothed = if (isKhmerTab) regionRanks.first().smoothedRank else 0.0
        val bestSingleRank = regionRanks.minOf { it.rankToday }
        val avgSmoothedRank = regionRanks.map { it.smoothedRank }.average()

        val multiplier = if (isKhmerTab) 1.0 else multiplierForCountries(countriesCount)
        val finalScore = if (isKhmerTab) {
          // Khmer tab uses Cambodia rank directly, with no cross-country bonus
          breakdowns.first().points
        } else {
          computeRegionalScore(basePoints, countriesCount)
        }

        val badge = when (filter) {
          TopMusicFilter.RISING_FAST -> TopMusicBadge.PlacesGained(totalGained)
          TopMusicFilter.NEW_TODAY -> TopMusicBadge.NewEntry
          else -> {
            if (isKhmerTab) {
              val first = regionRanks.first()
              when {
                first.isNewEntry -> TopMusicBadge.NewEntry
                first.placesGained > 0 -> TopMusicBadge.PlacesGained(first.placesGained)
                else -> TopMusicBadge.KhmerRankMove(first.signedMovement)
              }
            } else {
              when {
                totalGained > 0 && countriesCount == 1 -> TopMusicBadge.PlacesGained(totalGained)
                hasNewEntry && countriesCount == 1 -> TopMusicBadge.NewEntry
                else -> TopMusicBadge.CountryCount(countriesCount)
              }
            }
          }
        }

        val explanation = ScoreExplanation(
          isKhmerDirect = isKhmerTab,
          basePoints = basePoints,
          countriesCharting = countriesCount,
          multiplier = multiplier,
          finalScore = finalScore,
          bestSingleCountryRank = bestSingleRank,
          averageSmoothedRank = if (isKhmerTab) khmerSmoothed else avgSmoothedRank,
          khmerDirectRank = khmerRank
        )

        TopMusicRankedItem(
          rank = 0, // Assigned after sorting
          videoId = entry.videoId,
          title = entry.title,
          artist = entry.artist,
          artworkUrl = entry.artworkUrl,
          durationSeconds = entry.durationSeconds,
          chartType = entry.chartType,
          badge = badge,
          countryBreakdowns = breakdowns,
          scoreExplanation = explanation,
          totalPlacesGained = totalGained,
          hasNewEntryInRegion = hasNewEntry
        )
      }

    val comparator: Comparator<TopMusicRankedItem> = when (filter) {
      TopMusicFilter.TRENDING_TODAY,
      TopMusicFilter.NEW_TODAY -> {
        if (isKhmerTab) {
          compareBy<TopMusicRankedItem> { it.scoreExplanation.khmerDirectRank ?: Int.MAX_VALUE }
            .thenBy { it.title }
        } else {
          compareByDescending<TopMusicRankedItem> { it.scoreExplanation.finalScore }
            .thenBy { it.scoreExplanation.bestSingleCountryRank }
            .thenBy { it.title }
        }
      }
      TopMusicFilter.RISING_FAST -> {
        compareByDescending<TopMusicRankedItem> { it.totalPlacesGained }
          .thenByDescending { it.scoreExplanation.finalScore }
          .thenBy { it.scoreExplanation.bestSingleCountryRank }
      }
      TopMusicFilter.TOP_SONGS -> {
        if (isKhmerTab) {
          compareBy<TopMusicRankedItem> { it.scoreExplanation.averageSmoothedRank }
            .thenBy { it.scoreExplanation.bestSingleCountryRank }
            .thenBy { it.title }
        } else {
          compareByDescending<TopMusicRankedItem> { it.scoreExplanation.finalScore }
            .thenBy { it.scoreExplanation.averageSmoothedRank }
            .thenBy { it.title }
        }
      }
      TopMusicFilter.TOP_MUSIC_VIDEOS -> {
        if (isKhmerTab) {
          compareBy<TopMusicRankedItem> { it.scoreExplanation.khmerDirectRank ?: Int.MAX_VALUE }
            .thenBy { it.title }
        } else {
          compareBy<TopMusicRankedItem> { it.scoreExplanation.bestSingleCountryRank }
            .thenByDescending { it.scoreExplanation.finalScore }
            .thenBy { it.title }
        }
      }
    }

    return candidates
      .sortedWith(comparator)
      .mapIndexed { index, item ->
        item.copy(rank = index + 1)
      }
  }
}
