package com.example.recap

import com.example.data.AurixDao
import com.example.data.PlayHistoryEntity
import com.example.data.PlayLogEntity
import com.example.data.PreferencesManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlin.math.roundToInt

enum class RecapPeriod {
  MONTHLY,
  YEARLY
}

data class ArtistStat(
  val name: String,
  val plays: Int,
  val sharePercent: Int = 0,
  val isTopOnePercent: Boolean = false
)

data class TrackStat(
  val title: String,
  val artist: String,
  val plays: Int,
  val bestDay: String,
  val longestStreak: Int,
  val replays: Int
)

data class RecapData(
  val period: RecapPeriod,
  val periodKey: String,
  val label: String,
  val topArtists: List<ArtistStat>,
  val topTrack: TrackStat,
  val totalMinutes: Int,
  val dailyAverage: Int,
  val topDay: String,
  val newArtists: Int,
  val totalPlays: Int = 0,
  val hasRealData: Boolean = true,
  val personality: Personality,
  val mood: OrbiMood
)

data class RecapReadyPrompt(
  val period: RecapPeriod,
  val periodKey: String,
  val periodLabel: String
)

/**
 * Computes Monthly and Yearly Music DNA Recap snapshots from AURIX's real Room play logs
 * on a background dispatcher, with an in-memory per-period cache.
 */
class RecapRepository(
  private val dao: AurixDao,
  private val prefsManager: PreferencesManager,
  private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
  private val computeDispatcher: CoroutineDispatcher = Dispatchers.Default
) {
  private val cache = ConcurrentHashMap<RecapPeriod, RecapData>()

  fun invalidateCache() {
    cache.clear()
  }

  suspend fun getRecap(
    period: RecapPeriod,
    forceRefresh: Boolean = false,
    nowMs: Long = System.currentTimeMillis()
  ): RecapData {
    if (!forceRefresh) {
      cache[period]?.let { return it }
    }
    val logs = withContext(ioDispatcher) {
      val playLogs = runCatching { dao.getAllPlayLogsOnce() }.getOrDefault(emptyList())
      if (playLogs.isNotEmpty()) {
        playLogs
      } else {
        // Fallback to play_history entries if play_logs has not accumulated yet
        runCatching { dao.getPlayHistoryOnce() }
          .getOrDefault(emptyList())
          .map { it.toSyntheticPlayLog() }
      }
    }
    val computed = withContext(computeDispatcher) {
      computeRecapData(period = period, allLogs = logs, nowMs = nowMs)
    }
    cache[period] = computed
    return computed
  }

  /**
   * Checks whether we are at the start of a new month or new year (first 7 days) and the user
   * has not yet opened or dismissed that period's recap prompt.
   */
  suspend fun checkRecapReadyPrompt(nowMs: Long = System.currentTimeMillis()): RecapReadyPrompt? {
    return withContext(ioDispatcher) {
      val logs = runCatching { dao.getAllPlayLogsOnce() }.getOrDefault(emptyList())
      if (logs.isEmpty()) return@withContext null

      val cal = Calendar.getInstance().apply { timeInMillis = nowMs }
      val dayOfMonth = cal.get(Calendar.DAY_OF_MONTH)
      val monthOfYear = cal.get(Calendar.MONTH) // 0 = Jan

      // Start of a new year (Jan 1..14) -> surface previous year's Yearly Recap if unseen
      if (monthOfYear == Calendar.JANUARY && dayOfMonth <= 14) {
        val prevYear = cal.get(Calendar.YEAR) - 1
        val yearKey = prevYear.toString()
        val lastSeenYear = runCatching { prefsManager.lastSeenRecapYear.first() }.getOrDefault("")
        if (lastSeenYear != yearKey) {
          val hasPrevYearPlays = logs.any { log ->
            val lc = Calendar.getInstance().apply { timeInMillis = log.playedAt }
            lc.get(Calendar.YEAR) == prevYear
          }
          if (hasPrevYearPlays) {
            return@withContext RecapReadyPrompt(
              period = RecapPeriod.YEARLY,
              periodKey = yearKey,
              periodLabel = yearKey
            )
          }
        }
      }

      // Start of a new month (days 1..7) -> surface previous month's Monthly Recap if unseen
      if (dayOfMonth <= 7) {
        val prevMonthCal = Calendar.getInstance().apply {
          timeInMillis = nowMs
          add(Calendar.MONTH, -1)
        }
        val monthKey = formatMonthKey(prevMonthCal)
        val lastSeenMonth = runCatching { prefsManager.lastSeenRecapMonth.first() }.getOrDefault("")
        if (lastSeenMonth != monthKey) {
          val hasPrevMonthPlays = logs.any { log ->
            val lc = Calendar.getInstance().apply { timeInMillis = log.playedAt }
            lc.get(Calendar.YEAR) == prevMonthCal.get(Calendar.YEAR) &&
              lc.get(Calendar.MONTH) == prevMonthCal.get(Calendar.MONTH)
          }
          if (hasPrevMonthPlays) {
            val label = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(prevMonthCal.time)
            return@withContext RecapReadyPrompt(
              period = RecapPeriod.MONTHLY,
              periodKey = monthKey,
              periodLabel = label
            )
          }
        }
      }

      null
    }
  }

  suspend fun markPromptSeen(prompt: RecapReadyPrompt) {
    withContext(ioDispatcher) {
      prefsManager.markRecapPeriodSeen(
        isYearly = prompt.period == RecapPeriod.YEARLY,
        periodKey = prompt.periodKey
      )
    }
  }

  companion object {
    private const val DAY_MS = 24L * 60L * 60L * 1000L

    /**
     * Pure deterministic computation exposed for unit testing.
     */
    fun computeRecapData(
      period: RecapPeriod,
      allLogs: List<PlayLogEntity>,
      nowMs: Long = System.currentTimeMillis()
    ): RecapData {
      val nowCal = Calendar.getInstance().apply { timeInMillis = nowMs }
      val (windowStartMs, windowEndMs, periodKey, periodLabel) = resolvePeriodWindow(
        period = period,
        allLogs = allLogs,
        nowCal = nowCal
      )

      val periodLogs = allLogs
        .filter { !it.skipped && it.playedAt in windowStartMs..windowEndMs }
        .ifEmpty {
          // If no logs in the strict window yet, use all available non-skipped logs so the user
          // still sees their real listening stats rather than an error state.
          allLogs.filter { !it.skipped }
        }

      if (periodLogs.isEmpty()) {
        val fallbackPersonality = PersonalityClassifier.classify(
          PersonalitySignals(
            totalPlays = 0,
            lateNightRatio = 0f,
            morningRatio = 0f,
            uniqueArtistRatio = 0f,
            newArtistsCount = 0,
            repeatRate = 0f,
            dailyAverageMinutes = 0,
            averageBpm = 100
          )
        )
        return RecapData(
          period = period,
          periodKey = periodKey,
          label = periodLabel,
          topArtists = emptyList(),
          topTrack = TrackStat(
            title = "—",
            artist = "AURIX",
            plays = 0,
            bestDay = "—",
            longestStreak = 0,
            replays = 0
          ),
          totalMinutes = 0,
          dailyAverage = 0,
          topDay = "—",
          newArtists = 0,
          totalPlays = 0,
          hasRealData = false,
          personality = fallbackPersonality,
          mood = OrbiMood.CHILL
        )
      }

      val totalPlays = periodLogs.size

      // 1. Top 3 Artists + honest Top 1% eligibility
      val artistGroups = periodLogs
        .filter { it.artist.isNotBlank() }
        .groupBy { normalizeName(it.artist) }
        .values
        .map { group ->
          val displayName = group.first().artist.trim()
          val count = group.size
          val sharePct = ((count.toFloat() / totalPlays.toFloat()) * 100f).roundToInt().coerceIn(1, 100)
          // Honest Top 1% rule: only claim "top 1%" when the listener has truly heavy dedication:
          // >= 40 plays in a month (or >= 200 plays in a year) AND >= 35% share of total plays.
          val minPlaysForTop1 = if (period == RecapPeriod.YEARLY) 200 else 40
          val isTop1 = count >= minPlaysForTop1 && sharePct >= 35
          ArtistStat(
            name = displayName,
            plays = count,
            sharePercent = sharePct,
            isTopOnePercent = isTop1
          )
        }
        .sortedByDescending { it.plays }

      val topArtists = artistGroups.take(3)

      // 2. Top Track + best day + longest consecutive-day streak + replays
      val topTrackGroup = periodLogs
        .groupBy { it.songId.ifBlank { "${normalizeName(it.title)}|${normalizeName(it.artist)}" } }
        .values
        .maxByOrNull { it.size }
        ?: emptyList()

      val topTrack = if (topTrackGroup.isNotEmpty()) {
        val sample = topTrackGroup.first()
        val bestTrackDay = findBestCalendarDayLabel(topTrackGroup.map { it.playedAt })
        val streak = computeLongestDayStreak(topTrackGroup.map { it.playedAt })
        val explicitReplays = topTrackGroup.count { it.replayed }
        val replays = max(explicitReplays, (topTrackGroup.size - 1).coerceAtLeast(0))
        TrackStat(
          title = sample.title.ifBlank { "Untitled" },
          artist = sample.artist.ifBlank { "Unknown Artist" },
          plays = topTrackGroup.size,
          bestDay = bestTrackDay,
          longestStreak = streak,
          replays = replays
        )
      } else {
        TrackStat("—", "AURIX", 0, "—", 0, 0)
      }

      // 3. Total minutes, daily average, top day, new artists
      val totalListenedMs = periodLogs.sumOf { log ->
        when {
          log.listenedDurationMs > 0L -> log.listenedDurationMs
          log.durationSeconds > 0L -> log.durationSeconds * 1000L
          else -> 180_000L // 3 min fallback when duration wasn't recorded
        }
      }
      val totalMinutes = max(1, (totalListenedMs / 60_000L).toInt())

      val distinctDays = periodLogs.map { dayEpochNumber(it.playedAt) }.toSet()
      val activeDays = distinctDays.size.coerceAtLeast(1)
      val dailyAverage = max(1, (totalMinutes.toFloat() / activeDays.toFloat()).roundToInt())
      val topDay = findBestCalendarDayLabel(periodLogs.map { it.playedAt })

      val priorArtists = allLogs
        .filter { !it.skipped && it.playedAt < windowStartMs && it.artist.isNotBlank() }
        .map { normalizeName(it.artist) }
        .toSet()
      val periodArtistKeys = periodLogs
        .filter { it.artist.isNotBlank() }
        .map { normalizeName(it.artist) }
        .toSet()
      val newArtists = if (priorArtists.isEmpty()) {
        periodArtistKeys.size
      } else {
        periodArtistKeys.count { it !in priorArtists }
      }

      // 4. Hour-of-day, repeat rate, tempo & audio tags for Personality & OrbiMood
      val hourBuckets = IntArray(24)
      periodLogs.forEach { log ->
        val h = Calendar.getInstance().apply { timeInMillis = log.playedAt }.get(Calendar.HOUR_OF_DAY)
        if (h in 0..23) hourBuckets[h]++
      }
      val peakHour = hourBuckets.indices.maxByOrNull { hourBuckets[it] } ?: 20
      val lateNightPlays = hourBuckets[22] + hourBuckets[23] + hourBuckets[0] + hourBuckets[1] + hourBuckets[2] + hourBuckets[3]
      val morningPlays = (5..11).sumOf { hourBuckets[it] }
      val lateNightRatio = lateNightPlays.toFloat() / totalPlays.toFloat()
      val morningRatio = morningPlays.toFloat() / totalPlays.toFloat()

      val uniqueSongs = periodLogs.map { it.songId.ifBlank { it.title } }.toSet().size.coerceAtLeast(1)
      val repeatPlayCount = periodLogs.count { it.replayed } + (totalPlays - uniqueSongs).coerceAtLeast(0)
      val repeatRate = (repeatPlayCount.toFloat() / (totalPlays * 1.35f)).coerceIn(0f, 1f)
      val uniqueArtistRatio = (periodArtistKeys.size.toFloat() / totalPlays.toFloat()).coerceIn(0f, 1f)

      val validBpms = periodLogs.mapNotNull { it.bpm.takeIf { b -> b > 0 } }
      val averageBpm = if (validBpms.isEmpty()) 104 else validBpms.sum() / validBpms.size

      val personality = PersonalityClassifier.classify(
        PersonalitySignals(
          totalPlays = totalPlays,
          lateNightRatio = lateNightRatio,
          morningRatio = morningRatio,
          uniqueArtistRatio = uniqueArtistRatio,
          newArtistsCount = newArtists,
          repeatRate = repeatRate,
          dailyAverageMinutes = dailyAverage,
          averageBpm = averageBpm
        )
      )

      // Top-played subset for OrbiMood audio tag & mood vector weighting
      val topSongIds = periodLogs
        .groupingBy { it.songId }
        .eachCount()
        .entries
        .sortedByDescending { it.value }
        .take(10)
        .map { it.key }
        .toSet()
      val moodSample = periodLogs.filter { it.songId in topSongIds }.ifEmpty { periodLogs }
      val sampleCount = moodSample.size.coerceAtLeast(1).toFloat()
      val dominantTags = moodSample
        .flatMap { listOf(it.genre.lowercase(Locale.ROOT), it.mood.lowercase(Locale.ROOT)) }
        .filter { it.isNotBlank() }
        .groupingBy { it }
        .eachCount()
        .entries
        .sortedByDescending { it.value }
        .take(3)
        .map { it.key }
        .toSet()

      val orbiMood = OrbiMoodClassifier.classify(
        OrbiMoodSignals(
          averageBpm = averageBpm,
          peakHour = peakHour,
          dominantTags = dominantTags,
          energy = moodSample.sumOf { it.energy.toDouble() }.toFloat() / sampleCount,
          chill = moodSample.sumOf { it.chill.toDouble() }.toFloat() / sampleCount,
          calm = moodSample.sumOf { it.calm.toDouble() }.toFloat() / sampleCount,
          sad = moodSample.sumOf { it.sad.toDouble() }.toFloat() / sampleCount,
          romantic = moodSample.sumOf { it.romantic.toDouble() }.toFloat() / sampleCount,
          energetic = moodSample.sumOf { it.energetic.toDouble() }.toFloat() / sampleCount,
          instrumentalRatio = moodSample.count { !it.hasVocals }.toFloat() / sampleCount
        )
      )

      return RecapData(
        period = period,
        periodKey = periodKey,
        label = periodLabel,
        topArtists = topArtists,
        topTrack = topTrack,
        totalMinutes = totalMinutes,
        dailyAverage = dailyAverage,
        topDay = topDay,
        newArtists = newArtists,
        totalPlays = totalPlays,
        hasRealData = true,
        personality = personality,
        mood = orbiMood
      )
    }

    private data class PeriodWindow(
      val startMs: Long,
      val endMs: Long,
      val key: String,
      val label: String
    )

    private fun resolvePeriodWindow(
      period: RecapPeriod,
      allLogs: List<PlayLogEntity>,
      nowCal: Calendar
    ): PeriodWindow {
      val dayOfMonth = nowCal.get(Calendar.DAY_OF_MONTH)
      val monthOfYear = nowCal.get(Calendar.MONTH)

      return when (period) {
        RecapPeriod.MONTHLY -> {
          val curStart = startOfMonth(nowCal)
          val curEnd = nowCal.timeInMillis
          val hasCurrentMonthLogs = allLogs.any { !it.skipped && it.playedAt in curStart..curEnd }
          val targetCal = if (!hasCurrentMonthLogs && dayOfMonth <= 7) {
            (nowCal.clone() as Calendar).apply { add(Calendar.MONTH, -1) }
          } else {
            nowCal.clone() as Calendar
          }
          val start = startOfMonth(targetCal)
          val end = endOfMonth(targetCal).coerceAtMost(nowCal.timeInMillis)
          val label = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(targetCal.time)
          PeriodWindow(start, end, formatMonthKey(targetCal), label)
        }
        RecapPeriod.YEARLY -> {
          val curStart = startOfYear(nowCal)
          val curEnd = nowCal.timeInMillis
          val hasCurrentYearLogs = allLogs.any { !it.skipped && it.playedAt in curStart..curEnd }
          val targetCal = if (!hasCurrentYearLogs && monthOfYear == Calendar.JANUARY && dayOfMonth <= 14) {
            (nowCal.clone() as Calendar).apply { add(Calendar.YEAR, -1) }
          } else {
            nowCal.clone() as Calendar
          }
          val start = startOfYear(targetCal)
          val end = endOfYear(targetCal).coerceAtMost(nowCal.timeInMillis)
          val yearStr = targetCal.get(Calendar.YEAR).toString()
          PeriodWindow(start, end, yearStr, yearStr)
        }
      }
    }

    private fun startOfMonth(cal: Calendar): Long {
      val c = (cal.clone() as Calendar).apply {
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
      }
      return c.timeInMillis
    }

    private fun endOfMonth(cal: Calendar): Long {
      val c = (cal.clone() as Calendar).apply {
        set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
        set(Calendar.HOUR_OF_DAY, 23)
        set(Calendar.MINUTE, 59)
        set(Calendar.SECOND, 59)
        set(Calendar.MILLISECOND, 999)
      }
      return c.timeInMillis
    }

    private fun startOfYear(cal: Calendar): Long {
      val c = (cal.clone() as Calendar).apply {
        set(Calendar.MONTH, Calendar.JANUARY)
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
      }
      return c.timeInMillis
    }

    private fun endOfYear(cal: Calendar): Long {
      val c = (cal.clone() as Calendar).apply {
        set(Calendar.MONTH, Calendar.DECEMBER)
        set(Calendar.DAY_OF_MONTH, 31)
        set(Calendar.HOUR_OF_DAY, 23)
        set(Calendar.MINUTE, 59)
        set(Calendar.SECOND, 59)
        set(Calendar.MILLISECOND, 999)
      }
      return c.timeInMillis
    }

    private fun formatMonthKey(cal: Calendar): String {
      return String.format(Locale.US, "%04d-%02d", cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1)
    }

    private fun dayEpochNumber(timestampMs: Long): Long {
      val cal = Calendar.getInstance().apply {
        timeInMillis = timestampMs
        set(Calendar.HOUR_OF_DAY, 12)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
      }
      return cal.timeInMillis / DAY_MS
    }

    internal fun computeLongestDayStreak(timestamps: List<Long>): Int {
      if (timestamps.isEmpty()) return 0
      val days = timestamps.map { dayEpochNumber(it) }.distinct().sorted()
      var best = 1
      var current = 1
      for (i in 1 until days.size) {
        if (days[i] == days[i - 1] + 1L) {
          current++
          if (current > best) best = current
        } else {
          current = 1
        }
      }
      return best
    }

    private fun findBestCalendarDayLabel(timestamps: List<Long>): String {
      if (timestamps.isEmpty()) return "—"
      val bestGroup = timestamps
        .groupBy { dayEpochNumber(it) }
        .values
        .maxByOrNull { it.size }
        ?: return "—"
      val fmt = SimpleDateFormat("MMM d", Locale.getDefault())
      return fmt.format(Date(bestGroup.first()))
    }

    private fun normalizeName(raw: String): String {
      return raw.trim().lowercase(Locale.ROOT)
    }

    private fun PlayHistoryEntity.toSyntheticPlayLog(): PlayLogEntity {
      return PlayLogEntity(
        songId = songId,
        title = title,
        artist = artist,
        artworkUrl = artworkUrl,
        durationSeconds = durationSeconds,
        streamUrl = streamUrl,
        source = source,
        genre = "",
        bpm = 0,
        mood = "",
        playedAt = playedAt,
        listenedDurationMs = if (durationSeconds > 0L) durationSeconds * 1000L else 180_000L,
        completedPercent = completedPercent,
        completed = completed,
        skipped = skipped,
        replayed = replayed
      )
    }
  }
}
