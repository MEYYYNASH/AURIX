package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.topmusic.CountryChartRank
import com.example.topmusic.TOP_MUSIC_SAMPLE_DATA
import com.example.topmusic.TopMusicCacheSnapshot
import com.example.topmusic.TopMusicChartType
import com.example.topmusic.TopMusicEngine
import com.example.topmusic.TopMusicFilter
import com.example.topmusic.TopMusicRawEntry
import com.example.topmusic.TopMusicRegion
import com.example.topmusic.TopMusicRegionsConfig
import com.example.topmusic.TopMusicRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TopMusicEngineTest {

  @Test
  fun `regional score formula gives multi-country hit higher score than single-country number 1`() {
    val multiCountry = TopMusicRawEntry(
      id = "multi",
      videoId = "multi_vid",
      title = "Time To Rise",
      artist = "VannDa",
      artworkUrl = "",
      durationSeconds = 200L,
      chartType = TopMusicChartType.TRENDING,
      countryRanks = listOf(
        CountryChartRank("KH", rankToday = 1, previousRank = 3), // 20 pts
        CountryChartRank("TH", rankToday = 3, previousRank = 5), // 18 pts
        CountryChartRank("VN", rankToday = 5, previousRank = 8)  // 16 pts
      )
    )
    val singleCountryNo1 = TopMusicRawEntry(
      id = "single",
      videoId = "single_vid",
      title = "Solo #1",
      artist = "Solo Artist",
      artworkUrl = "",
      durationSeconds = 200L,
      chartType = TopMusicChartType.TRENDING,
      countryRanks = listOf(
        CountryChartRank("KR", rankToday = 1, previousRank = 2)  // 20 pts
      )
    )

    val ranked = TopMusicEngine.rankTracks(
      rawEntries = listOf(singleCountryNo1, multiCountry),
      region = TopMusicRegion.GLOBAL,
      filter = TopMusicFilter.TRENDING_TODAY
    )

    assertEquals(2, ranked.size)
    assertEquals("multi_vid", ranked[0].videoId)
    assertEquals(81.0, ranked[0].scoreExplanation.finalScore, 0.001)
    assertEquals(1.5, ranked[0].scoreExplanation.multiplier, 0.001)
    assertEquals(20.0, ranked[1].scoreExplanation.finalScore, 0.001)
  }

  @Test
  fun `top 20 cutoff awards zero points beyond rank 20`() {
    assertEquals(20, TopMusicEngine.pointsForRank(1))
    assertEquals(1, TopMusicEngine.pointsForRank(20))
    assertEquals(0, TopMusicEngine.pointsForRank(21))
    assertEquals(0, TopMusicEngine.pointsForRank(50))
  }

  @Test
  fun `khmer tab uses Cambodia rank directly without cross-country bonus`() {
    val ranked = TopMusicEngine.rankTracks(
      rawEntries = TOP_MUSIC_SAMPLE_DATA,
      region = TopMusicRegion.KHMER,
      filter = TopMusicFilter.TRENDING_TODAY
    )

    assertTrue(ranked.isNotEmpty())
    assertTrue(ranked.all { it.scoreExplanation.isKhmerDirect })
    assertTrue(ranked.all { it.scoreExplanation.multiplier == 1.0 })
    assertTrue(ranked.all { item -> item.countryBreakdowns.all { it.countryCode == "KH" } })
    assertEquals(1, ranked.first().scoreExplanation.khmerDirectRank)
  }

  @Test
  fun `cambodia-only songs never appear in Asia or Europe tabs`() {
    val khmerOnlyId = "0y5wX-f3E7A" // Suly Pheng KH-only in sample data
    val asia = TopMusicEngine.rankTracks(TOP_MUSIC_SAMPLE_DATA, TopMusicRegion.ASIA, TopMusicFilter.TRENDING_TODAY)
    val europe = TopMusicEngine.rankTracks(TOP_MUSIC_SAMPLE_DATA, TopMusicRegion.EUROPE, TopMusicFilter.TRENDING_TODAY)

    assertFalse(asia.any { it.videoId == khmerOnlyId })
    assertFalse(europe.any { it.videoId == khmerOnlyId })
  }

  @Test
  fun `india IN chart data is ignored everywhere`() {
    assertTrue(TopMusicRegionsConfig.isIgnoredCountry("IN"))
    assertTrue(TopMusicRegionsConfig.isIgnoredCountry("in"))
    assertFalse("IN" in TopMusicRegionsConfig.countriesForRegion(TopMusicRegion.ASIA))
    assertFalse("IN" in TopMusicRegionsConfig.countriesForRegion(TopMusicRegion.GLOBAL))

    val indiaOnly = TopMusicRawEntry(
      id = "in_only",
      videoId = "in_vid",
      title = "India Only Track",
      artist = "Artist IN",
      artworkUrl = "",
      durationSeconds = 180L,
      chartType = TopMusicChartType.TRENDING,
      countryRanks = listOf(CountryChartRank("IN", rankToday = 1, previousRank = 2))
    )
    val mixedWithIndia = TopMusicRawEntry(
      id = "mixed_in",
      videoId = "mixed_vid",
      title = "Cross Track",
      artist = "Artist Cross",
      artworkUrl = "",
      durationSeconds = 180L,
      chartType = TopMusicChartType.TRENDING,
      countryRanks = listOf(
        CountryChartRank("IN", rankToday = 1, previousRank = 5),
        CountryChartRank("TH", rankToday = 4, previousRank = 4)
      )
    )

    val global = TopMusicEngine.rankTracks(
      rawEntries = listOf(indiaOnly, mixedWithIndia),
      region = TopMusicRegion.GLOBAL,
      filter = TopMusicFilter.TRENDING_TODAY
    )

    assertEquals(1, global.size)
    assertEquals("mixed_vid", global[0].videoId)
    assertEquals(1, global[0].countryBreakdowns.size)
    assertEquals("TH", global[0].countryBreakdowns[0].countryCode)
    assertEquals(17.0, global[0].scoreExplanation.finalScore, 0.001)
  }

  @Test
  fun `rising fast hides non-rising tracks and sorts by places gained`() {
    val rising = TopMusicEngine.rankTracks(
      rawEntries = TOP_MUSIC_SAMPLE_DATA,
      region = TopMusicRegion.KHMER,
      filter = TopMusicFilter.RISING_FAST
    )

    assertTrue(rising.isNotEmpty())
    assertTrue(rising.all { it.totalPlacesGained > 0 })
    val gains = rising.map { it.totalPlacesGained }
    assertEquals(gains.sortedDescending(), gains)
  }

  @Test
  fun `new today shows only entries with null previousRank in active region`() {
    val newEntries = TopMusicEngine.rankTracks(
      rawEntries = TOP_MUSIC_SAMPLE_DATA,
      region = TopMusicRegion.GLOBAL,
      filter = TopMusicFilter.NEW_TODAY
    )

    assertTrue(newEntries.isNotEmpty())
    assertTrue(newEntries.all { it.hasNewEntryInRegion })
    assertTrue(newEntries.all { item -> item.countryBreakdowns.any { it.isNewEntry } })
  }

  @Test
  fun `top songs uses weekly chart with smoothed average rank`() {
    val spiky = TopMusicRawEntry(
      id = "spiky",
      videoId = "spiky_vid",
      title = "Spiky Hit",
      artist = "Artist A",
      artworkUrl = "",
      durationSeconds = 200L,
      chartType = TopMusicChartType.WEEKLY_TOP_SONGS,
      countryRanks = listOf(CountryChartRank("KH", rankToday = 1, previousRank = 11)) // avg = 6.0
    )
    val steady = TopMusicRawEntry(
      id = "steady",
      videoId = "steady_vid",
      title = "Steady Hit",
      artist = "Artist B",
      artworkUrl = "",
      durationSeconds = 200L,
      chartType = TopMusicChartType.WEEKLY_TOP_SONGS,
      countryRanks = listOf(CountryChartRank("KH", rankToday = 2, previousRank = 2)) // avg = 2.0
    )

    val ranked = TopMusicEngine.rankTracks(
      rawEntries = listOf(spiky, steady),
      region = TopMusicRegion.KHMER,
      filter = TopMusicFilter.TOP_SONGS
    )

    assertEquals(2, ranked.size)
    assertEquals("steady_vid", ranked[0].videoId)
    assertEquals(2.0, ranked[0].scoreExplanation.averageSmoothedRank, 0.001)
    assertEquals("spiky_vid", ranked[1].videoId)
    assertEquals(6.0, ranked[1].scoreExplanation.averageSmoothedRank, 0.001)
  }

  @Test
  fun `top music videos sorts by best single-country rank then regional score`() {
    val rank1Single = TopMusicRawEntry(
      id = "mv1",
      videoId = "mv1_vid",
      title = "Peak #1 Single",
      artist = "Artist 1",
      artworkUrl = "",
      durationSeconds = 200L,
      chartType = TopMusicChartType.DAILY_TOP_MUSIC_VIDEOS,
      countryRanks = listOf(CountryChartRank("KR", rankToday = 1, previousRank = 1))
    )
    val rank2Multi = TopMusicRawEntry(
      id = "mv2",
      videoId = "mv2_vid",
      title = "Multi Country #2",
      artist = "Artist 2",
      artworkUrl = "",
      durationSeconds = 200L,
      chartType = TopMusicChartType.DAILY_TOP_MUSIC_VIDEOS,
      countryRanks = listOf(
        CountryChartRank("GB", rankToday = 2, previousRank = 3),
        CountryChartRank("DE", rankToday = 2, previousRank = 4),
        CountryChartRank("FR", rankToday = 3, previousRank = 5)
      )
    )

    val ranked = TopMusicEngine.rankTracks(
      rawEntries = listOf(rank2Multi, rank1Single),
      region = TopMusicRegion.GLOBAL,
      filter = TopMusicFilter.TOP_MUSIC_VIDEOS
    )

    assertEquals(2, ranked.size)
    assertEquals("mv1_vid", ranked[0].videoId)
    assertEquals("mv2_vid", ranked[1].videoId)
  }

  @Test
  fun `search filters within current region tab and filter including khmer text`() {
    val khmerFiltered = TopMusicEngine.rankTracks(
      rawEntries = TOP_MUSIC_SAMPLE_DATA,
      region = TopMusicRegion.KHMER,
      filter = TopMusicFilter.TRENDING_TODAY,
      searchQuery = "ស្នេហ៍"
    )
    assertEquals(1, khmerFiltered.size)
    assertEquals("Suly Pheng", khmerFiltered.first().artist)

    val europeWithKhmerQuery = TopMusicEngine.rankTracks(
      rawEntries = TOP_MUSIC_SAMPLE_DATA,
      region = TopMusicRegion.EUROPE,
      filter = TopMusicFilter.TRENDING_TODAY,
      searchQuery = "ស្នេហ៍"
    )
    assertTrue(europeWithKhmerQuery.isEmpty())
  }

  @Test
  fun `repository returns last cached chart with date when offline and strips IN on decode`(): Unit = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    var isOnline = true
    var currentDate = "2026-11-15"
    val cacheFile = "test_top_music_cache.json"

    val repo = TopMusicRepository(
      context = context,
      cacheFileName = cacheFile,
      connectivityChecker = { isOnline },
      dateProvider = { currentDate }
    )

    // Day 1 online fetch writes cache with date 2026-11-15
    val day1 = repo.loadCharts()
    assertEquals("2026-11-15", day1.dateKey)
    assertFalse(day1.isOffline)
    assertTrue(day1.isSampleData)

    // Day 2 offline returns previous cached date 2026-11-15 with isOffline = true
    isOnline = false
    currentDate = "2026-11-16"
    val offlineDay2 = repo.loadCharts()
    assertEquals("2026-11-15", offlineDay2.dateKey)
    assertTrue(offlineDay2.isOffline)
    assertTrue(offlineDay2.entries.isNotEmpty())

    // Verify JSON decode strips any IN entries
    val encodedWithIn = """
      {
        "dateKey": "2026-11-15",
        "isSampleData": true,
        "updatedAtMillis": 12345,
        "entries": [
          {
            "id": "x",
            "videoId": "vid",
            "title": "Song",
            "artist": "Artist",
            "artworkUrl": "",
            "durationSeconds": 100,
            "chartType": "trending",
            "countryRanks": [
              {"countryCode": "IN", "rankToday": 1, "previousRank": null},
              {"countryCode": "KH", "rankToday": 2, "previousRank": 4}
            ]
          }
        ]
      }
    """.trimIndent()
    val decoded = TopMusicRepository.decodeSnapshotJson(encodedWithIn)!!
    assertEquals(1, decoded.entries.size)
    assertEquals(listOf("KH"), decoded.entries[0].countryRanks.map { it.countryCode })

    context.filesDir.resolve(cacheFile).delete()
  }
}
