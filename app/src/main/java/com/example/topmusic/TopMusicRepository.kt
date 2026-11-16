package com.example.topmusic

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class TopMusicCacheSnapshot(
  val dateKey: String,
  val entries: List<TopMusicRawEntry>,
  val isSampleData: Boolean,
  val isOffline: Boolean,
  val updatedAtMillis: Long
)

class TopMusicRepository(
  private val context: Context,
  private val cacheFileName: String = "top_music_charts_cache_v1.json",
  private val connectivityChecker: () -> Boolean = { checkOnline(context) },
  private val dateProvider: () -> String = { currentDateKey() }
) {
  private val cacheFile: File
    get() = File(context.filesDir, cacheFileName)

  suspend fun loadCharts(forceRefresh: Boolean = false): TopMusicCacheSnapshot = withContext(Dispatchers.IO) {
    val today = dateProvider()
    val isOnline = connectivityChecker()
    val cached = readCacheFromDisk()

    // If offline: return last cached chart with its date (or persist & return sanitized sample snapshot if first launch)
    if (!isOnline) {
      if (cached != null) {
        return@withContext cached.copy(isOffline = true)
      }
      val sanitized = TopMusicEngine.sanitizeEntries(TOP_MUSIC_SAMPLE_DATA)
      val offlineSnapshot = TopMusicCacheSnapshot(
        dateKey = today,
        entries = sanitized,
        isSampleData = true,
        isOffline = true,
        updatedAtMillis = System.currentTimeMillis()
      )
      writeCacheToDisk(offlineSnapshot)
      return@withContext offlineSnapshot
    }

    // Reuse today's cache unless forced or stale
    if (!forceRefresh && cached != null && cached.dateKey == today && cached.entries.isNotEmpty()) {
      return@withContext cached.copy(isOffline = false)
    }

    // Refresh daily chart snapshot (using TOP_MUSIC_SAMPLE_DATA until a live backend endpoint is configured)
    val refreshedEntries = TopMusicEngine.sanitizeEntries(TOP_MUSIC_SAMPLE_DATA)
    val snapshot = TopMusicCacheSnapshot(
      dateKey = today,
      entries = refreshedEntries,
      isSampleData = true,
      isOffline = false,
      updatedAtMillis = System.currentTimeMillis()
    )
    writeCacheToDisk(snapshot)
    snapshot
  }

  fun readCacheFromDisk(): TopMusicCacheSnapshot? {
    return runCatching {
      if (!cacheFile.exists()) return null
      val raw = cacheFile.readText()
      if (raw.isBlank()) return null
      decodeSnapshotJson(raw)
    }.getOrNull()
  }

  fun writeCacheToDisk(snapshot: TopMusicCacheSnapshot) {
    runCatching {
      cacheFile.parentFile?.mkdirs()
      cacheFile.writeText(encodeSnapshotJson(snapshot))
    }
  }

  companion object {
    fun currentDateKey(): String {
      return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    }

    fun checkOnline(context: Context): Boolean {
      val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
      val network = cm.activeNetwork ?: return false
      val caps = cm.getNetworkCapabilities(network) ?: return false
      return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun encodeSnapshotJson(snapshot: TopMusicCacheSnapshot): String {
      val root = JSONObject()
      root.put("dateKey", snapshot.dateKey)
      root.put("isSampleData", snapshot.isSampleData)
      root.put("updatedAtMillis", snapshot.updatedAtMillis)
      val entriesArr = JSONArray()
      for (entry in TopMusicEngine.sanitizeEntries(snapshot.entries)) {
        val obj = JSONObject()
        obj.put("id", entry.id)
        obj.put("videoId", entry.videoId)
        obj.put("title", entry.title)
        obj.put("artist", entry.artist)
        obj.put("artworkUrl", entry.artworkUrl)
        obj.put("durationSeconds", entry.durationSeconds)
        obj.put("chartType", entry.chartType.id)
        val ranksArr = JSONArray()
        for (rank in entry.countryRanks) {
          if (TopMusicRegionsConfig.isIgnoredCountry(rank.countryCode)) continue
          val rObj = JSONObject()
          rObj.put("countryCode", rank.countryCode.uppercase(Locale.US))
          rObj.put("rankToday", rank.rankToday)
          if (rank.previousRank != null) {
            rObj.put("previousRank", rank.previousRank)
          } else {
            rObj.put("previousRank", JSONObject.NULL)
          }
          ranksArr.put(rObj)
        }
        obj.put("countryRanks", ranksArr)
        entriesArr.put(obj)
      }
      root.put("entries", entriesArr)
      return root.toString()
    }

    fun decodeSnapshotJson(raw: String): TopMusicCacheSnapshot? {
      val root = JSONObject(raw)
      val dateKey = root.optString("dateKey").trim()
      if (dateKey.isBlank()) return null
      val isSampleData = root.optBoolean("isSampleData", true)
      val updatedAtMillis = root.optLong("updatedAtMillis", 0L)
      val entriesArr = root.optJSONArray("entries") ?: return null
      val entries = mutableListOf<TopMusicRawEntry>()

      for (i in 0 until entriesArr.length()) {
        val obj = entriesArr.optJSONObject(i) ?: continue
        val chartTypeId = obj.optString("chartType")
        val chartType = TopMusicChartType.entries.firstOrNull { it.id == chartTypeId } ?: continue
        val ranksArr = obj.optJSONArray("countryRanks") ?: continue
        val ranks = mutableListOf<CountryChartRank>()
        for (j in 0 until ranksArr.length()) {
          val rObj = ranksArr.optJSONObject(j) ?: continue
          val code = rObj.optString("countryCode").trim().uppercase(Locale.US)
          if (code.isBlank() || TopMusicRegionsConfig.isIgnoredCountry(code)) continue
          val rankToday = rObj.optInt("rankToday", -1)
          if (rankToday <= 0) continue
          val prevRank = if (rObj.isNull("previousRank")) null else rObj.optInt("previousRank", -1).takeIf { it > 0 }
          ranks.add(CountryChartRank(countryCode = code, rankToday = rankToday, previousRank = prevRank))
        }
        if (ranks.isEmpty()) continue
        entries.add(
          TopMusicRawEntry(
            id = obj.optString("id"),
            videoId = obj.optString("videoId"),
            title = obj.optString("title"),
            artist = obj.optString("artist"),
            artworkUrl = obj.optString("artworkUrl"),
            durationSeconds = obj.optLong("durationSeconds", 0L),
            chartType = chartType,
            countryRanks = ranks
          )
        )
      }

      return TopMusicCacheSnapshot(
        dateKey = dateKey,
        entries = TopMusicEngine.sanitizeEntries(entries),
        isSampleData = isSampleData,
        isOffline = false,
        updatedAtMillis = updatedAtMillis
      )
    }
  }
}
