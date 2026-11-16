package com.example.topmusic

import java.util.Locale

enum class TopMusicRegion(val id: String) {
  KHMER("khmer"),
  ASIA("asia"),
  EUROPE("europe"),
  GLOBAL("global")
}

enum class TopMusicChartType(val id: String) {
  TRENDING("trending"),
  DAILY_TOP_MUSIC_VIDEOS("daily_top_music_videos"),
  WEEKLY_TOP_SONGS("weekly_top_songs")
}

enum class TopMusicFilter(val id: String, val chartType: TopMusicChartType) {
  TRENDING_TODAY("trending_today", TopMusicChartType.TRENDING),
  RISING_FAST("rising_fast", TopMusicChartType.TRENDING),
  NEW_TODAY("new_today", TopMusicChartType.TRENDING),
  TOP_SONGS("top_songs", TopMusicChartType.WEEKLY_TOP_SONGS),
  TOP_MUSIC_VIDEOS("top_music_videos", TopMusicChartType.DAILY_TOP_MUSIC_VIDEOS)
}

/**
 * Editable region and country definitions for Top Music.
 * India ("IN") is explicitly excluded everywhere in the app.
 */
object TopMusicRegionsConfig {
  val IGNORED_COUNTRY_CODES: Set<String> = setOf("IN")

  // Cambodia only
  val KHMER_COUNTRIES: List<String> = listOf("KH")

  // Editable Asia country list (India IN intentionally excluded)
  val ASIA_COUNTRIES: List<String> = listOf("TH", "VN", "PH", "KR", "JP", "ID")

  // Editable Europe country list
  val EUROPE_COUNTRIES: List<String> = listOf("GB", "DE", "FR", "TR", "ES")

  // Global combines all allowed countries above
  val GLOBAL_COUNTRIES: List<String>
    get() = (KHMER_COUNTRIES + ASIA_COUNTRIES + EUROPE_COUNTRIES)
      .map { it.trim().uppercase(Locale.US) }
      .filter { it.isNotBlank() && it !in IGNORED_COUNTRY_CODES }
      .distinct()

  fun countriesForRegion(region: TopMusicRegion): Set<String> {
    val raw = when (region) {
      TopMusicRegion.KHMER -> KHMER_COUNTRIES
      TopMusicRegion.ASIA -> ASIA_COUNTRIES
      TopMusicRegion.EUROPE -> EUROPE_COUNTRIES
      TopMusicRegion.GLOBAL -> GLOBAL_COUNTRIES
    }
    return raw
      .map { it.trim().uppercase(Locale.US) }
      .filter { it.isNotBlank() && it !in IGNORED_COUNTRY_CODES }
      .toSet()
  }

  fun isIgnoredCountry(countryCode: String): Boolean {
    return countryCode.trim().uppercase(Locale.US) in IGNORED_COUNTRY_CODES
  }

  fun flagForCountry(countryCode: String): String {
    return when (countryCode.trim().uppercase(Locale.US)) {
      "KH" -> "\uD83C\uDDF0\uD83C\uDDED"
      "TH" -> "\uD83C\uDDF9\uD83C\uDDED"
      "VN" -> "\uD83C\uDDFB\uD83C\uDDF3"
      "PH" -> "\uD83C\uDDF5\uD83C\uDDED"
      "KR" -> "\uD83C\uDDF0\uD83C\uDDF7"
      "JP" -> "\uD83C\uDDEF\uD83C\uDDF5"
      "ID" -> "\uD83C\uDDEE\uD83C\uDDE9"
      "GB" -> "\uD83C\uDDEC\uD83C\uDDE7"
      "DE" -> "\uD83C\uDDE9\uD83C\uDDEA"
      "FR" -> "\uD83C\uDDEB\uD83C\uDDF7"
      "TR" -> "\uD83C\uDDF9\uD83C\uDDF7"
      "ES" -> "\uD83C\uDDEA\uD83C\uDDF8"
      else -> "\uD83C\uDF0D"
    }
  }

  fun countryDisplayName(countryCode: String): String {
    return when (countryCode.trim().uppercase(Locale.US)) {
      "KH" -> "Cambodia"
      "TH" -> "Thailand"
      "VN" -> "Vietnam"
      "PH" -> "Philippines"
      "KR" -> "South Korea"
      "JP" -> "Japan"
      "ID" -> "Indonesia"
      "GB" -> "United Kingdom"
      "DE" -> "Germany"
      "FR" -> "France"
      "TR" -> "Turkey"
      "ES" -> "Spain"
      else -> countryCode.uppercase(Locale.US)
    }
  }
}

data class CountryChartRank(
  val countryCode: String,
  val rankToday: Int,
  val previousRank: Int? = null // null = new entry today
) {
  val isNewEntry: Boolean
    get() = previousRank == null

  val placesGained: Int
    get() = if (previousRank != null && previousRank > rankToday) previousRank - rankToday else 0

  val signedMovement: Int?
    get() = previousRank?.let { it - rankToday }

  val smoothedRank: Double
    get() = if (previousRank != null) (rankToday + previousRank) / 2.0 else rankToday.toDouble()
}

data class TopMusicRawEntry(
  val id: String,
  val videoId: String,
  val title: String,
  val artist: String,
  val artworkUrl: String,
  val durationSeconds: Long,
  val chartType: TopMusicChartType,
  val countryRanks: List<CountryChartRank>
)

sealed interface TopMusicBadge {
  data class PlacesGained(val places: Int) : TopMusicBadge
  data object NewEntry : TopMusicBadge
  data class CountryCount(val count: Int) : TopMusicBadge
  data class KhmerRankMove(val movement: Int?) : TopMusicBadge
}

data class CountryRankBreakdown(
  val countryCode: String,
  val flagEmoji: String,
  val countryName: String,
  val rankToday: Int,
  val previousRank: Int?,
  val smoothedRank: Double,
  val movement: Int?,
  val isNewEntry: Boolean,
  val points: Double
)

data class ScoreExplanation(
  val isKhmerDirect: Boolean,
  val basePoints: Double,
  val countriesCharting: Int,
  val multiplier: Double,
  val finalScore: Double,
  val bestSingleCountryRank: Int,
  val averageSmoothedRank: Double,
  val khmerDirectRank: Int? = null
)

data class TopMusicRankedItem(
  val rank: Int,
  val videoId: String,
  val title: String,
  val artist: String,
  val artworkUrl: String,
  val durationSeconds: Long,
  val chartType: TopMusicChartType,
  val badge: TopMusicBadge,
  val countryBreakdowns: List<CountryRankBreakdown>,
  val scoreExplanation: ScoreExplanation,
  val totalPlacesGained: Int,
  val hasNewEntryInRegion: Boolean
)

data class TopMusicUiState(
  val isLoading: Boolean = true,
  val isOffline: Boolean = false,
  val isSampleData: Boolean = true,
  val lastUpdatedDate: String = "",
  val selectedRegion: TopMusicRegion = TopMusicRegion.KHMER,
  val selectedFilter: TopMusicFilter = TopMusicFilter.TRENDING_TODAY,
  val searchQuery: String = "",
  val items: List<TopMusicRankedItem> = emptyList(),
  val errorMessage: String? = null
)
