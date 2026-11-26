package com.example.ui.screens.onboarding

import android.content.Context
import com.example.data.YoutubeExtractor
import com.example.data.YoutubeTitleCleaner
import com.example.lyrics.LyricsMatchOutcome
import com.example.lyrics.LyricsMatcher
import com.example.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.random.Random

/**
 * A search result row displayed on Slide 1 that matches the featured song's search query.
 */
data class OnboardingSearchRowData(
  val title: String,
  val subtitle: String,
  val badge: String,
  val artworkUrl: String = "",
  val baseHue: Float = 195f,
  val isLocal: Boolean = false,
  val song: Song? = null
)

/**
 * Complete cohesive song showcase shared across all 4 onboarding slides:
 * - Slide 1: Types [searchQuery], reveals matching [searchRows], picks Row 1, raises mini-player for [song].
 * - Slide 2: Shows [song] header + real synced [lyrics] matched to [song] + real audio stream playback.
 * - Slide 3: Shows [song] artwork, title, and artist on the live theme customizer tile.
 * - Slide 4: Shows [song] title & artist inside the glass pill below animated Orbi.
 */
data class OnboardingShowcaseTrack(
  val seedKey: String,
  val song: Song,
  val searchQuery: String,
  val searchRows: List<OnboardingSearchRowData>,
  val lyrics: List<OnboardingLyricLine>,
  val lyricWindowStartSec: Float,
  val durationSeconds: Float,
  val hasVerifiedRealLyrics: Boolean,
  val accentBaseHue: Float,
  val isLiveFetched: Boolean = false
)

internal data class OnboardingSongSeed(
  val key: String,
  val title: String,
  val artist: String,
  val searchQuery: String,
  val altTitle: String,
  val altArtist: String,
  val durationSeconds: Long,
  val baseHue: Float
)

/**
 * Fetches a REAL random song from the user's phone or live YouTube search, pairs it with
 * REAL matching search results and REAL synced lyrics from LRCLIB via [LyricsMatcher],
 * and resolves playable audio streams via [YoutubeExtractor].
 */
object OnboardingShowcaseRepository {

  internal val REAL_SONG_SEEDS: List<OnboardingSongSeed> = listOf(
    OnboardingSongSeed(
      key = "blinding_lights",
      title = "Blinding Lights",
      artist = "The Weeknd",
      searchQuery = "Blinding",
      altTitle = "Blinding Lights (Live)",
      altArtist = "The Weeknd",
      durationSeconds = 200L,
      baseHue = 352f
    ),
    OnboardingSongSeed(
      key = "die_with_a_smile",
      title = "Die With A Smile",
      artist = "Lady Gaga, Bruno Mars",
      searchQuery = "Die With",
      altTitle = "Die With A Smile (Acoustic)",
      altArtist = "Bruno Mars",
      durationSeconds = 251L,
      baseHue = 212f
    ),
    OnboardingSongSeed(
      key = "birds_of_a_feather",
      title = "BIRDS OF A FEATHER",
      artist = "Billie Eilish",
      searchQuery = "Birds of",
      altTitle = "Birds Of A Feather (Slowed)",
      altArtist = "Billie Eilish",
      durationSeconds = 210L,
      baseHue = 198f
    ),
    OnboardingSongSeed(
      key = "starboy",
      title = "Starboy",
      artist = "The Weeknd",
      searchQuery = "Starboy",
      altTitle = "Starboy (Speed Up)",
      altArtist = "The Weeknd",
      durationSeconds = 230L,
      baseHue = 278f
    ),
    OnboardingSongSeed(
      key = "sweater_weather",
      title = "Sweater Weather",
      artist = "The Neighbourhood",
      searchQuery = "Sweater",
      altTitle = "Sweater Weather (Acoustic)",
      altArtist = "The Neighbourhood",
      durationSeconds = 240L,
      baseHue = 224f
    ),
    OnboardingSongSeed(
      key = "golden_hour",
      title = "golden hour",
      artist = "JVKE",
      searchQuery = "golden",
      altTitle = "golden hour (Piano)",
      altArtist = "JVKE",
      durationSeconds = 209L,
      baseHue = 36f
    ),
    OnboardingSongSeed(
      key = "sunflower",
      title = "Sunflower",
      artist = "Post Malone, Swae Lee",
      searchQuery = "Sunflower",
      altTitle = "Sunflower (Spider-Verse)",
      altArtist = "Post Malone",
      durationSeconds = 158L,
      baseHue = 48f
    ),
    OnboardingSongSeed(
      key = "as_it_was",
      title = "As It Was",
      artist = "Harry Styles",
      searchQuery = "As It Was",
      altTitle = "As It Was (Live)",
      altArtist = "Harry Styles",
      durationSeconds = 167L,
      baseHue = 168f
    ),
    OnboardingSongSeed(
      key = "heat_waves",
      title = "Heat Waves",
      artist = "Glass Animals",
      searchQuery = "Heat Waves",
      altTitle = "Heat Waves (Slowed)",
      altArtist = "Glass Animals",
      durationSeconds = 238L,
      baseHue = 294f
    ),
    OnboardingSongSeed(
      key = "night_changes",
      title = "Night Changes",
      artist = "One Direction",
      searchQuery = "Night",
      altTitle = "Night Changes (Acoustic)",
      altArtist = "One Direction",
      durationSeconds = 226L,
      baseHue = 246f
    ),
    OnboardingSongSeed(
      key = "cruel_summer",
      title = "Cruel Summer",
      artist = "Taylor Swift",
      searchQuery = "Cruel",
      altTitle = "Cruel Summer (Live)",
      altArtist = "Taylor Swift",
      durationSeconds = 178L,
      baseHue = 328f
    ),
    OnboardingSongSeed(
      key = "espresso",
      title = "Espresso",
      artist = "Sabrina Carpenter",
      searchQuery = "Espresso",
      altTitle = "Espresso (Working Late)",
      altArtist = "Sabrina Carpenter",
      durationSeconds = 175L,
      baseHue = 24f
    )
  )

  /**
   * Derives a clean, natural search query prefix (4-11 chars) that matches [title].
   */
  fun deriveSearchQueryFromTitle(title: String): String {
    val cleaned = YoutubeTitleCleaner.stripNoiseTags(title).trim()
    if (cleaned.isBlank()) return "Music"
    val words = cleaned.split(Regex("\\s+")).filter { it.isNotBlank() }
    if (words.isEmpty()) return cleaned.take(8)
    val first = words[0]
    return when {
      first.length >= 6 -> first.take(9)
      words.size >= 2 && (first.length + 1 + words[1].length) <= 11 -> "$first ${words[1]}"
      else -> cleaned.take(9).trim()
    }
  }

  /**
   * Computes a deterministic hue (0..360) for graceful gradient fallback behind album artwork.
   */
  fun deterministicHue(seed: String): Float {
    return (abs(seed.hashCode()) % 360).toFloat()
  }

  /**
   * Picks the best lyric window start time (first meaningful vocal line) so Slide 2 immediately
   * showcases real singing lyrics instead of waiting through an instrumental intro.
   */
  fun selectLyricStartWindow(
    lines: List<OnboardingLyricLine>,
    totalDurationSec: Float
  ): Pair<Float, Float> {
    val cleanLines = lines.filter { it.text.isNotBlank() }
    if (cleanLines.isEmpty()) return 0f to totalDurationSec.coerceAtLeast(19.2f)
    val firstVocal = cleanLines.firstOrNull { line ->
      val trimmed = line.text.trim()
      trimmed.length >= 3 && !trimmed.all { it == '♪' || it == '♫' || it == '.' || it == '-' || it == ' ' }
    } ?: cleanLines.first()
    val startSec = firstVocal.seconds.coerceAtLeast(0f)
    val lastLineSec = cleanLines.last().seconds
    val safeDuration = maxOf(totalDurationSec, lastLineSec + 4f, startSec + 12f)
    return startSec to safeDuration
  }

  /**
   * Builds an immediate random showcase on frame 1 before live network search/lyrics complete,
   * pulling randomly from the user's real local library (if present) or the multi-song real pool.
   */
  fun buildInitialRandomShowcase(
    localSongs: List<Song> = emptyList(),
    onPhoneLabel: String = "On your phone",
    excludeSeedKey: String? = null,
    random: Random = Random.Default
  ): OnboardingShowcaseTrack {
    val localFormat = extractLocalAudioFormatBadge(localSongs.firstOrNull())
    val eligibleLocal = localSongs.filter { it.title.isNotBlank() && it.artist.isNotBlank() }

    // 35% chance to spotlight a real local song from the user's device when available
    if (eligibleLocal.isNotEmpty() && random.nextFloat() < 0.35f) {
      val localPool = eligibleLocal.filter { "local_${it.id}" != excludeSeedKey }.ifEmpty { eligibleLocal }
      val pickedLocal = localPool[random.nextInt(localPool.size)]
      val query = deriveSearchQueryFromTitle(pickedLocal.title)
      val hue = deterministicHue("${pickedLocal.title}|${pickedLocal.artist}")
      val fmt = extractLocalAudioFormatBadge(pickedLocal)
      val rows = listOf(
        OnboardingSearchRowData(
          title = pickedLocal.title,
          subtitle = "${pickedLocal.artist} · $onPhoneLabel",
          badge = fmt,
          artworkUrl = pickedLocal.artworkUrl,
          baseHue = hue,
          isLocal = true,
          song = pickedLocal
        ),
        OnboardingSearchRowData(
          title = pickedLocal.title,
          subtitle = "${pickedLocal.artist} · YouTube",
          badge = "HD",
          artworkUrl = pickedLocal.artworkUrl,
          baseHue = (hue + 48f) % 360f,
          isLocal = false,
          song = pickedLocal
        ),
        OnboardingSearchRowData(
          title = "${pickedLocal.title} (Mix)",
          subtitle = "${pickedLocal.artist} · YouTube",
          badge = "HD",
          artworkUrl = "",
          baseHue = (hue + 115f) % 360f,
          isLocal = false,
          song = pickedLocal
        )
      )
      return OnboardingShowcaseTrack(
        seedKey = "local_${pickedLocal.id}",
        song = pickedLocal,
        searchQuery = query,
        searchRows = rows,
        lyrics = emptyList(),
        lyricWindowStartSec = 0f,
        durationSeconds = pickedLocal.durationSeconds.takeIf { it > 10L }?.toFloat()
          ?: OnboardingDemoAudioEngine.LOOP_DURATION_SECONDS,
        hasVerifiedRealLyrics = false,
        accentBaseHue = hue,
        isLiveFetched = false
      )
    }

    val candidates = REAL_SONG_SEEDS.filter { it.key != excludeSeedKey }.ifEmpty { REAL_SONG_SEEDS }
    val seed = candidates[random.nextInt(candidates.size)]
    val placeholderSong = Song(
      id = "seed_${seed.key}",
      title = seed.title,
      artist = seed.artist,
      artworkUrl = "",
      durationSeconds = seed.durationSeconds,
      streamUrl = "",
      source = "YouTube",
      fullDurationSeconds = seed.durationSeconds
    )
    val rows = listOf(
      OnboardingSearchRowData(
        title = seed.title,
        subtitle = "${seed.artist} · YouTube",
        badge = "HD",
        baseHue = seed.baseHue,
        isLocal = false,
        song = placeholderSong
      ),
      OnboardingSearchRowData(
        title = seed.altTitle,
        subtitle = "${seed.altArtist} · YouTube",
        badge = "HD",
        baseHue = (seed.baseHue + 52f) % 360f,
        isLocal = false,
        song = placeholderSong.copy(id = "seed_${seed.key}_alt", title = seed.altTitle, artist = seed.altArtist)
      ),
      OnboardingSearchRowData(
        title = seed.title,
        subtitle = "${seed.artist} · $onPhoneLabel",
        badge = localFormat,
        baseHue = (seed.baseHue + 128f) % 360f,
        isLocal = true,
        song = placeholderSong
      )
    )
    return OnboardingShowcaseTrack(
      seedKey = seed.key,
      song = placeholderSong,
      searchQuery = seed.searchQuery,
      searchRows = rows,
      lyrics = emptyList(),
      lyricWindowStartSec = 0f,
      durationSeconds = seed.durationSeconds.toFloat(),
      hasVerifiedRealLyrics = false,
      accentBaseHue = seed.baseHue,
      isLiveFetched = false
    )
  }

  /**
   * Live loader: picks a random real song (from local library or YouTube), fetches matching real
   * YouTube search results + real album artwork, and verifies real synced lyrics via [LyricsMatcher].
   * Retries up to 4 shuffled candidates until a song with real synced lyrics is matched.
   */
  suspend fun loadLiveRandomShowcase(
    context: Context,
    localSongs: List<Song>,
    onPhoneLabel: String,
    excludeSeedKey: String? = null,
    preferredInitial: OnboardingShowcaseTrack? = null,
    random: Random = Random.Default
  ): OnboardingShowcaseTrack = withContext(Dispatchers.IO) {
    val localFormat = extractLocalAudioFormatBadge(localSongs.firstOrNull())
    val shuffledSeeds = REAL_SONG_SEEDS
      .filter { it.key != excludeSeedKey }
      .shuffled(random)
      .toMutableList()

    // Put preferredInitial's seed first so frame 1 transitions seamlessly when live results arrive
    if (preferredInitial != null && !preferredInitial.seedKey.startsWith("local_")) {
      val idx = shuffledSeeds.indexOfFirst { it.key == preferredInitial.seedKey }
      if (idx > 0) {
        val chosen = shuffledSeeds.removeAt(idx)
        shuffledSeeds.add(0, chosen)
      }
    }

    // Try local song first if preferredInitial selected a real local track
    if (preferredInitial != null && preferredInitial.seedKey.startsWith("local_")) {
      val localAttempt = enrichLocalSongShowcase(
        context = context,
        localSong = preferredInitial.song,
        onPhoneLabel = onPhoneLabel
      )
      if (localAttempt != null && localAttempt.hasVerifiedRealLyrics) {
        return@withContext localAttempt
      }
    }

    var bestFallback: OnboardingShowcaseTrack? = null

    for (seed in shuffledSeeds.take(4)) {
      val candidate = fetchYoutubeSeedWithRealLyrics(
        context = context,
        seed = seed,
        localSongs = localSongs,
        onPhoneLabel = onPhoneLabel,
        defaultLocalBadge = localFormat
      )
      if (candidate != null) {
        if (candidate.hasVerifiedRealLyrics) {
          return@withContext candidate
        }
        if (bestFallback == null) {
          bestFallback = candidate
        }
      }
    }

    // Fallback if offline: return best partial live result or initial random track
    bestFallback ?: preferredInitial ?: buildInitialRandomShowcase(
      localSongs = localSongs,
      onPhoneLabel = onPhoneLabel,
      excludeSeedKey = excludeSeedKey,
      random = random
    )
  }

  private suspend fun enrichLocalSongShowcase(
    context: Context,
    localSong: Song,
    onPhoneLabel: String
  ): OnboardingShowcaseTrack? {
    val outcome = runCatching {
      LyricsMatcher.matchWithOutcome(song = localSong, context = context)
    }.getOrNull()
    val syncedLines = (outcome as? LyricsMatchOutcome.Verified)
      ?.lyricsResult
      ?.synced
      .orEmpty()
      .filter { it.text.isNotBlank() }
      .map { OnboardingLyricLine(it.timestampMs / 1000f, it.text.trim()) }

    val query = deriveSearchQueryFromTitle(localSong.title)
    val hue = deterministicHue("${localSong.title}|${localSong.artist}")
    val fmt = extractLocalAudioFormatBadge(localSong)
    val ytMatches = runCatching {
      YoutubeExtractor.searchSongs("${localSong.title} ${localSong.artist}", limit = 4)
    }.getOrDefault(emptyList())

    val ytRow2 = ytMatches.firstOrNull()?.let { item ->
      val (cleanTitle, cleanArtist) = YoutubeTitleCleaner.parseTitleAndArtist(item.rawTitle, item.author)
      val dur = item.durationSeconds.takeIf { it > 35L } ?: 210L
      val songObj = Song(
        id = "yt_${item.videoId}",
        title = cleanTitle,
        artist = cleanArtist,
        artworkUrl = item.thumbnailUrl,
        durationSeconds = dur,
        streamUrl = "https://www.youtube.com/watch?v=${item.videoId}",
        source = "YouTube",
        fullDurationSeconds = dur
      )
      OnboardingSearchRowData(
        title = cleanTitle,
        subtitle = "$cleanArtist · YouTube",
        badge = "HD",
        artworkUrl = item.thumbnailUrl,
        baseHue = (hue + 52f) % 360f,
        isLocal = false,
        song = songObj
      )
    } ?: OnboardingSearchRowData(
      title = localSong.title,
      subtitle = "${localSong.artist} · YouTube",
      badge = "HD",
      artworkUrl = localSong.artworkUrl,
      baseHue = (hue + 52f) % 360f,
      isLocal = false,
      song = localSong
    )

    val ytRow3 = ytMatches.getOrNull(1)?.let { item ->
      val (cleanTitle, cleanArtist) = YoutubeTitleCleaner.parseTitleAndArtist(item.rawTitle, item.author)
      val dur = item.durationSeconds.takeIf { it > 35L } ?: 210L
      val songObj = Song(
        id = "yt_${item.videoId}",
        title = cleanTitle,
        artist = cleanArtist,
        artworkUrl = item.thumbnailUrl,
        durationSeconds = dur,
        streamUrl = "https://www.youtube.com/watch?v=${item.videoId}",
        source = "YouTube",
        fullDurationSeconds = dur
      )
      OnboardingSearchRowData(
        title = cleanTitle,
        subtitle = "$cleanArtist · YouTube",
        badge = "HD",
        artworkUrl = item.thumbnailUrl,
        baseHue = (hue + 118f) % 360f,
        isLocal = false,
        song = songObj
      )
    } ?: OnboardingSearchRowData(
      title = "${localSong.title} (Acoustic)",
      subtitle = "${localSong.artist} · YouTube",
      badge = "HD",
      artworkUrl = "",
      baseHue = (hue + 118f) % 360f,
      isLocal = false,
      song = localSong
    )

    val durSec = localSong.durationSeconds.takeIf { it > 10L }?.toFloat()
      ?: OnboardingDemoAudioEngine.LOOP_DURATION_SECONDS
    val (winStart, safeDur) = selectLyricStartWindow(syncedLines, durSec)
    val rows = listOf(
      OnboardingSearchRowData(
        title = localSong.title,
        subtitle = "${localSong.artist} · $onPhoneLabel",
        badge = fmt,
        artworkUrl = localSong.artworkUrl,
        baseHue = hue,
        isLocal = true,
        song = localSong
      ),
      ytRow2,
      ytRow3
    )

    return OnboardingShowcaseTrack(
      seedKey = "local_${localSong.id}",
      song = localSong,
      searchQuery = query,
      searchRows = rows,
      lyrics = syncedLines,
      lyricWindowStartSec = winStart,
      durationSeconds = safeDur,
      hasVerifiedRealLyrics = syncedLines.size >= 4,
      accentBaseHue = hue,
      isLiveFetched = true
    )
  }

  private suspend fun fetchYoutubeSeedWithRealLyrics(
    context: Context,
    seed: OnboardingSongSeed,
    localSongs: List<Song>,
    onPhoneLabel: String,
    defaultLocalBadge: String
  ): OnboardingShowcaseTrack? {
    // 1. Live YouTube search for the seed song
    val ytItems = runCatching {
      YoutubeExtractor.searchSongs("${seed.title} ${seed.artist}", limit = 6)
    }.getOrDefault(emptyList())

    val bestYt = ytItems.maxByOrNull { item ->
      val (cleanTitle, cleanArtist) = YoutubeTitleCleaner.parseTitleAndArtist(item.rawTitle, item.author)
      val titleScore = LyricsMatcher.similarity(seed.title, cleanTitle)
      val artistScore = LyricsMatcher.similarity(seed.artist, cleanArtist)
      val durationBonus = if (item.durationSeconds in 90L..420L) 0.2 else 0.0
      titleScore * 0.65 + artistScore * 0.35 + durationBonus
    }

    val featuredSong = if (bestYt != null) {
      val (cleanTitle, cleanArtist) = YoutubeTitleCleaner.parseTitleAndArtist(bestYt.rawTitle, bestYt.author)
      val resolvedTitle = if (LyricsMatcher.similarity(seed.title, cleanTitle) >= 0.5) cleanTitle else seed.title
      val resolvedArtist = if (LyricsMatcher.similarity(seed.artist, cleanArtist) >= 0.35) cleanArtist else seed.artist
      val dur = bestYt.durationSeconds.takeIf { it > 35L } ?: seed.durationSeconds
      Song(
        id = "yt_${bestYt.videoId}",
        title = resolvedTitle,
        artist = resolvedArtist,
        artworkUrl = bestYt.thumbnailUrl,
        durationSeconds = dur,
        streamUrl = "https://www.youtube.com/watch?v=${bestYt.videoId}",
        source = "YouTube",
        fullDurationSeconds = dur
      )
    } else {
      Song(
        id = "seed_${seed.key}",
        title = seed.title,
        artist = seed.artist,
        artworkUrl = "",
        durationSeconds = seed.durationSeconds,
        streamUrl = "",
        source = "YouTube",
        fullDurationSeconds = seed.durationSeconds
      )
    }

    // 2. Real LRCLIB synced lyrics lookup strictly matched to featuredSong
    val outcome = runCatching {
      LyricsMatcher.matchWithOutcome(
        song = featuredSong,
        context = context,
        overrideDurationSec = featuredSong.durationSeconds.takeIf { it > 35L }?.toDouble()
      )
    }.getOrNull()

    val syncedLines = (outcome as? LyricsMatchOutcome.Verified)
      ?.lyricsResult
      ?.synced
      .orEmpty()
      .filter { it.text.isNotBlank() }
      .map { OnboardingLyricLine(it.timestampMs / 1000f, it.text.trim()) }

    val matchingSecondYt = ytItems
      .filter { it.videoId != bestYt?.videoId }
      .firstOrNull { item ->
        val (t, _) = YoutubeTitleCleaner.parseTitleAndArtist(item.rawTitle, item.author)
        t.isNotBlank()
      }

    val row2 = if (matchingSecondYt != null) {
      val (cleanTitle, cleanArtist) = YoutubeTitleCleaner.parseTitleAndArtist(
        matchingSecondYt.rawTitle,
        matchingSecondYt.author
      )
      val dur2 = matchingSecondYt.durationSeconds.takeIf { it > 35L } ?: seed.durationSeconds
      val song2 = Song(
        id = "yt_${matchingSecondYt.videoId}",
        title = cleanTitle,
        artist = cleanArtist,
        artworkUrl = matchingSecondYt.thumbnailUrl,
        durationSeconds = dur2,
        streamUrl = "https://www.youtube.com/watch?v=${matchingSecondYt.videoId}",
        source = "YouTube",
        fullDurationSeconds = dur2
      )
      OnboardingSearchRowData(
        title = cleanTitle,
        subtitle = "$cleanArtist · YouTube",
        badge = "HD",
        artworkUrl = matchingSecondYt.thumbnailUrl,
        baseHue = (seed.baseHue + 52f) % 360f,
        isLocal = false,
        song = song2
      )
    } else {
      OnboardingSearchRowData(
        title = seed.altTitle,
        subtitle = "${seed.altArtist} · YouTube",
        badge = "HD",
        artworkUrl = "",
        baseHue = (seed.baseHue + 52f) % 360f,
        isLocal = false,
        song = featuredSong
      )
    }

    val matchingLocal = localSongs.firstOrNull { local ->
      LyricsMatcher.similarity(local.title, featuredSong.title) >= 0.45
    }
    val row3 = if (matchingLocal != null) {
      OnboardingSearchRowData(
        title = matchingLocal.title,
        subtitle = "${matchingLocal.artist} · $onPhoneLabel",
        badge = extractLocalAudioFormatBadge(matchingLocal),
        artworkUrl = matchingLocal.artworkUrl,
        baseHue = (seed.baseHue + 128f) % 360f,
        isLocal = true,
        song = matchingLocal
      )
    } else {
      OnboardingSearchRowData(
        title = featuredSong.title,
        subtitle = "${featuredSong.artist} · $onPhoneLabel",
        badge = defaultLocalBadge,
        artworkUrl = featuredSong.artworkUrl,
        baseHue = (seed.baseHue + 128f) % 360f,
        isLocal = true,
        song = featuredSong
      )
    }

    val rows = listOf(
      OnboardingSearchRowData(
        title = featuredSong.title,
        subtitle = "${featuredSong.artist} · YouTube",
        badge = "HD",
        artworkUrl = featuredSong.artworkUrl,
        baseHue = seed.baseHue,
        isLocal = false,
        song = featuredSong
      ),
      row2,
      row3
    )

    val durSec = featuredSong.durationSeconds.takeIf { it > 10L }?.toFloat()
      ?: OnboardingDemoAudioEngine.LOOP_DURATION_SECONDS
    val (winStart, safeDur) = selectLyricStartWindow(syncedLines, durSec)

    return OnboardingShowcaseTrack(
      seedKey = seed.key,
      song = featuredSong,
      searchQuery = deriveSearchQueryFromTitle(featuredSong.title),
      searchRows = rows,
      lyrics = syncedLines,
      lyricWindowStartSec = winStart,
      durationSeconds = safeDur,
      hasVerifiedRealLyrics = syncedLines.size >= 4,
      accentBaseHue = seed.baseHue,
      isLiveFetched = bestYt != null || syncedLines.isNotEmpty()
    )
  }

  /**
   * Resolves a directly playable audio URI/URL for Slide 2's opt-in speaker preview.
   */
  suspend fun resolvePlayablePreviewUrl(song: Song): String? = withContext(Dispatchers.IO) {
    val raw = song.streamUrl.trim()
    if (raw.startsWith("content://") || raw.startsWith("file://") || raw.startsWith("/")) {
      return@withContext raw
    }
    if (raw.startsWith("http") && !raw.contains("youtube.com/watch")) {
      return@withContext raw
    }
    val videoId = when {
      song.id.startsWith("yt_") -> song.id.removePrefix("yt_")
      raw.contains("v=") -> raw.substringAfter("v=").substringBefore('&')
      else -> ""
    }.trim()
    if (videoId.length < 8) return@withContext null
    runCatching {
      val format = YoutubeExtractor.extractFreshWorkingFormat(
        videoId = videoId,
        preferredExtension = "m4a"
      )
      val resolved = format?.streamUrl?.takeIf { it.isNotBlank() }
      if (resolved != null && !format.requiredUserAgent.isNullOrBlank()) {
        com.example.data.StreamHeaderRegistry.register(resolved, format.requiredUserAgent)
      }
      resolved
    }.getOrNull()
  }

  /**
   * Live interactive search for Slide 1 ("What's your favourite music?"):
   * combines matching local phone songs with live YouTube results so the user can pick any real song.
   */
  suspend fun searchUserFavoriteSongs(
    query: String,
    localSongs: List<Song>,
    onPhoneLabel: String,
    limit: Int = 6
  ): List<OnboardingSearchRowData> = withContext(Dispatchers.IO) {
    val trimmed = query.trim()
    if (trimmed.isBlank()) return@withContext emptyList()
    val lower = trimmed.lowercase()

    val localMatches = localSongs
      .filter { song ->
        song.title.lowercase().contains(lower) || song.artist.lowercase().contains(lower)
      }
      .take(2)
      .map { song ->
        OnboardingSearchRowData(
          title = song.title,
          subtitle = "${song.artist} · $onPhoneLabel",
          badge = extractLocalAudioFormatBadge(song),
          artworkUrl = song.artworkUrl,
          baseHue = deterministicHue("${song.title}|${song.artist}"),
          isLocal = true,
          song = song
        )
      }

    val ytMatches = runCatching {
      YoutubeExtractor.searchSongs(trimmed, limit = limit)
    }.getOrDefault(emptyList())
      .mapNotNull { item ->
        val (cleanTitle, cleanArtist) = YoutubeTitleCleaner.parseTitleAndArtist(item.rawTitle, item.author)
        if (cleanTitle.isBlank()) return@mapNotNull null
        val dur = item.durationSeconds.takeIf { it > 25L } ?: 210L
        val song = Song(
          id = "yt_${item.videoId}",
          title = cleanTitle,
          artist = cleanArtist.ifBlank { item.author.ifBlank { "YouTube" } },
          artworkUrl = item.thumbnailUrl,
          durationSeconds = dur,
          streamUrl = "https://www.youtube.com/watch?v=${item.videoId}",
          source = "YouTube",
          fullDurationSeconds = dur
        )
        OnboardingSearchRowData(
          title = song.title,
          subtitle = "${song.artist} · YouTube",
          badge = "HD",
          artworkUrl = song.artworkUrl,
          baseHue = deterministicHue("${song.title}|${song.artist}"),
          isLocal = false,
          song = song
        )
      }

    (localMatches + ytMatches)
      .distinctBy { "${it.title.lowercase()}|${it.subtitle.lowercase()}" }
      .take(limit)
  }

  /**
   * Hydrates a user-picked favourite song into a complete [OnboardingShowcaseTrack] with real
   * synced LRCLIB lyrics so Slides 2, 3, and 4 immediately adapt to their chosen song.
   */
  suspend fun buildShowcaseFromSelectedSong(
    context: Context,
    selectedSong: Song,
    currentQuery: String,
    currentRows: List<OnboardingSearchRowData>,
    onPhoneLabel: String
  ): OnboardingShowcaseTrack = withContext(Dispatchers.IO) {
    // If the song is still a placeholder seed id, try resolving a real YouTube track first
    val resolvedSong = if (selectedSong.id.startsWith("seed_")) {
      val ytItems = runCatching {
        YoutubeExtractor.searchSongs("${selectedSong.title} ${selectedSong.artist}", limit = 3)
      }.getOrDefault(emptyList())
      val best = ytItems.firstOrNull()
      if (best != null) {
        val (cleanTitle, cleanArtist) = YoutubeTitleCleaner.parseTitleAndArtist(best.rawTitle, best.author)
        val dur = best.durationSeconds.takeIf { it > 30L } ?: selectedSong.durationSeconds
        Song(
          id = "yt_${best.videoId}",
          title = cleanTitle.ifBlank { selectedSong.title },
          artist = cleanArtist.ifBlank { selectedSong.artist },
          artworkUrl = best.thumbnailUrl.ifBlank { selectedSong.artworkUrl },
          durationSeconds = dur,
          streamUrl = "https://www.youtube.com/watch?v=${best.videoId}",
          source = "YouTube",
          fullDurationSeconds = dur
        )
      } else {
        selectedSong
      }
    } else {
      selectedSong
    }

    val outcome = runCatching {
      LyricsMatcher.matchWithOutcome(
        song = resolvedSong,
        context = context,
        overrideDurationSec = resolvedSong.durationSeconds.takeIf { it > 30L }?.toDouble()
      )
    }.getOrNull()

    val syncedLines = (outcome as? LyricsMatchOutcome.Verified)
      ?.lyricsResult
      ?.synced
      .orEmpty()
      .filter { it.text.isNotBlank() }
      .map { OnboardingLyricLine(it.timestampMs / 1000f, it.text.trim()) }

    val durSec = resolvedSong.durationSeconds.takeIf { it > 10L }?.toFloat()
      ?: OnboardingDemoAudioEngine.LOOP_DURATION_SECONDS
    val (winStart, safeDur) = selectLyricStartWindow(syncedLines, durSec)
    val hue = deterministicHue("${resolvedSong.title}|${resolvedSong.artist}")
    val rows = currentRows.ifEmpty {
      listOf(
        OnboardingSearchRowData(
          title = resolvedSong.title,
          subtitle = "${resolvedSong.artist} · ${if (resolvedSong.source.equals("Local", true)) onPhoneLabel else "YouTube"}",
          badge = if (resolvedSong.source.equals("Local", true)) extractLocalAudioFormatBadge(resolvedSong) else "HD",
          artworkUrl = resolvedSong.artworkUrl,
          baseHue = hue,
          isLocal = resolvedSong.source.equals("Local", true),
          song = resolvedSong
        )
      )
    }

    OnboardingShowcaseTrack(
      seedKey = "picked_${resolvedSong.id}",
      song = resolvedSong,
      searchQuery = currentQuery.ifBlank { deriveSearchQueryFromTitle(resolvedSong.title) },
      searchRows = rows,
      lyrics = syncedLines,
      lyricWindowStartSec = winStart,
      durationSeconds = safeDur,
      hasVerifiedRealLyrics = syncedLines.size >= 4,
      accentBaseHue = hue,
      isLiveFetched = true
    )
  }
}
