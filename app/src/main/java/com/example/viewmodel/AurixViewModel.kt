package com.example.viewmodel

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.room.Room
import com.example.R
import com.example.data.*
import com.example.lyrics.LrcParser
import com.example.lyrics.LyricsMatchOutcome
import com.example.lyrics.LyricsMatcher
import com.example.model.*
import com.example.playback.PlaybackService
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

enum class DailyFeedCategory(
  val id: String,
  val label: String,
  val badgeText: String,
  val subtitle: String
) {
  TIKTOK_VIRAL("tiktok", "TikTok Viral", "TikTok Viral", "Trending sounds on TikTok today"),
  DAILY_CHARTS("charts", "Daily Top", "Daily Chart", "Global top songs updated daily"),
  PHONK_EDITS("phonk", "Phonk & Edits", "Viral Edits", "Trending montage & phonk edits"),
  KHMER_ASIA("khmer", "Khmer & Asia", "Regional Hits", "Popular Khmer & Asian hits")
}

class AurixViewModel(application: Application) : AndroidViewModel(application) {
  private val prefsManager = PreferencesManager(application)
  private val database = Room.databaseBuilder(
    application,
    AurixDatabase::class.java,
    "aurix_db"
  )
    .addMigrations(AURIX_MIGRATION_1_2, AURIX_MIGRATION_2_3, AURIX_MIGRATION_3_4, AURIX_MIGRATION_4_5)
    .build()
  private val dao = database.aurixDao()

  private var controllerFuture: ListenableFuture<MediaController>? = null
  private var mediaController: MediaController? = null
  private var pendingPlayRequest: Pair<Song, List<Song>>? = null

  // Preferences States
  val themeMode = prefsManager.themeMode.stateIn(viewModelScope, SharingStarted.Lazily, "system")
  val accentColorHex = prefsManager.accentColor.stateIn(viewModelScope, SharingStarted.Lazily, "#007AFF")
  val language = prefsManager.language.stateIn(viewModelScope, SharingStarted.Lazily, "en")
  val dynamicBg = prefsManager.dynamicBg.stateIn(viewModelScope, SharingStarted.Lazily, true)
  val reducedMotion = prefsManager.reducedMotion.stateIn(viewModelScope, SharingStarted.Lazily, false)
  val onboardingCompleted = prefsManager.onboardingCompleted.stateIn(viewModelScope, SharingStarted.Lazily, false)
  val githubOwner = prefsManager.githubOwner.stateIn(viewModelScope, SharingStarted.Lazily, "MEYYYNASH")
  val githubRepo = prefsManager.githubRepo.stateIn(viewModelScope, SharingStarted.Lazily, "AURIX")

  // Search-Only Mode & YouTube Analyze/Download States
  private val _searchMode = MutableStateFlow("itunes") // "itunes" | "youtube"
  val searchMode: StateFlow<String> = _searchMode

  private val _searchQuery = MutableStateFlow("")
  val searchQuery: StateFlow<String> = _searchQuery

  private val _youtubeUrl = MutableStateFlow("")
  val youtubeUrl: StateFlow<String> = _youtubeUrl

  private val _youtubeAnalysis = MutableStateFlow<YoutubeAnalysisResult?>(null)
  val youtubeAnalysis: StateFlow<YoutubeAnalysisResult?> = _youtubeAnalysis

  private val _selectedYoutubeFormatId = MutableStateFlow<String?>(null)
  val selectedYoutubeFormatId: StateFlow<String?> = _selectedYoutubeFormatId

  private val _youtubeDownloadState = MutableStateFlow<YoutubeDownloadState>(YoutubeDownloadState.Idle)
  val youtubeDownloadState: StateFlow<YoutubeDownloadState> = _youtubeDownloadState

  private val _isAnalyzingYoutube = MutableStateFlow(false)
  val isAnalyzingYoutube: StateFlow<Boolean> = _isAnalyzingYoutube

  private val _youtubeError = MutableStateFlow<String?>(null)
  val youtubeError: StateFlow<String?> = _youtubeError

  // GitHub Smart Update States (backed by actual Android PackageManager info)
  private val _installedAppInfo = MutableStateFlow(GitHubUpdater.getInstalledAppInfo(application))
  val installedAppInfo: StateFlow<InstalledAppInfo> = _installedAppInfo

  private val _updateCheckStatus = MutableStateFlow<UpdateCheckStatus>(UpdateCheckStatus.Idle)
  val updateCheckStatus: StateFlow<UpdateCheckStatus> = _updateCheckStatus

  private val _updateStatus = MutableStateFlow("Ready to check for updates")
  val updateStatus: StateFlow<String> = _updateStatus

  private val _latestRelease = MutableStateFlow<GitHubRelease?>(null)
  val latestRelease: StateFlow<GitHubRelease?> = _latestRelease

  private val _apkDownloadState = MutableStateFlow<ApkDownloadState>(
    GitHubUpdater.hasValidatedNewerApk(application) ?: ApkDownloadState.Idle
  )
  val apkDownloadState: StateFlow<ApkDownloadState> = _apkDownloadState

  // Song Download States
  private val _songDownloadProgress = MutableStateFlow<Map<String, Float>>(emptyMap())
  val songDownloadProgress: StateFlow<Map<String, Float>> = _songDownloadProgress

  private val _downloadedSongIds = MutableStateFlow<Set<String>>(emptySet())
  val downloadedSongIds: StateFlow<Set<String>> = _downloadedSongIds

  // Daily TikTok Viral & Chart Recommendations (auto-updates daily, zero demo/workout spam)
  private val _selectedDailyFeed = MutableStateFlow(DailyFeedCategory.TIKTOK_VIRAL)
  val selectedDailyFeed: StateFlow<DailyFeedCategory> = _selectedDailyFeed

  private val _dailyUpdatedLabel = MutableStateFlow(formatTodayBadge())
  val dailyUpdatedLabel: StateFlow<String> = _dailyUpdatedLabel

  private val dailyFeedCache = mutableMapOf<Pair<String, DailyFeedCategory>, List<Song>>()
  private var lastFeedDateKey: String = todayDateKey()
  private var feedJob: Job? = null

  private val _rawFeatured = MutableStateFlow<List<Song>>(emptyList())
  private val _isLoadingFeatured = MutableStateFlow(false)
  val isLoadingFeatured: StateFlow<Boolean> = _isLoadingFeatured

  private val _featuredError = MutableStateFlow<String?>(null)
  val featuredError: StateFlow<String?> = _featuredError

  private val _rawSearchResults = MutableStateFlow<List<Song>>(emptyList())
  private val _isSearching = MutableStateFlow(false)
  val isSearching: StateFlow<Boolean> = _isSearching

  private val _searchError = MutableStateFlow<String?>(null)
  val searchError: StateFlow<String?> = _searchError

  val favorites: StateFlow<List<Song>> = dao.getFavorites().map { list ->
    list.map { Song(it.id, it.title, it.artist, it.artworkUrl, it.durationSeconds, it.streamUrl, it.source, true) }
  }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

  private val favoriteIds: StateFlow<Set<String>> = favorites.map { list ->
    list.map { it.id }.toSet()
  }.stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

  val featuredTracks: StateFlow<List<Song>> = combine(_rawFeatured, favoriteIds) { tracks, favs ->
    tracks.map { it.copy(isFavorite = it.id in favs) }
  }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

  val searchResults: StateFlow<List<Song>> = combine(_rawSearchResults, favoriteIds) { tracks, favs ->
    tracks.map { it.copy(isFavorite = it.id in favs) }
  }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

  val localSongs: StateFlow<List<Song>> = combine(dao.getLocalSongs(), favoriteIds) { list, favs ->
    list.map {
      Song(
        id = it.id,
        title = it.title,
        artist = it.artist,
        artworkUrl = it.artworkUrl.ifBlank { APP_ICON_ARTWORK },
        durationSeconds = it.durationSeconds,
        streamUrl = it.streamUrl,
        source = it.source,
        isFavorite = it.id in favs
      )
    }
  }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

  // Deduplicated by songId so playing a track 2-4 times updates its position at the front without spamming
  val recentlyPlayed: StateFlow<List<Song>> = combine(dao.getPlayHistory(), favoriteIds) { list, favs ->
    list.distinctBy { it.songId }.map {
      Song(it.songId, it.title, it.artist, it.artworkUrl, it.durationSeconds, it.streamUrl, it.source, it.songId in favs)
    }
  }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

  private val _rawCurrentSong = MutableStateFlow<Song?>(null)
  val currentSong: StateFlow<Song?> = combine(_rawCurrentSong, favoriteIds) { song, favs ->
    song?.copy(isFavorite = song.id in favs)
  }.stateIn(viewModelScope, SharingStarted.Lazily, null)

  private val _isPlaying = MutableStateFlow(false)
  val isPlaying: StateFlow<Boolean> = _isPlaying

  private val _currentPosition = MutableStateFlow(0L)
  val currentPosition: StateFlow<Long> = _currentPosition

  private val _durationMs = MutableStateFlow(0L)
  val durationMs: StateFlow<Long> = _durationMs

  private val _queue = MutableStateFlow<List<Song>>(emptyList())
  val queue: StateFlow<List<Song>> = _queue

  private val _shuffleEnabled = MutableStateFlow(false)
  val shuffleEnabled: StateFlow<Boolean> = _shuffleEnabled

  private val _repeatEnabled = MutableStateFlow(false)
  val repeatEnabled: StateFlow<Boolean> = _repeatEnabled

  private val _playbackSpeed = MutableStateFlow(1.0f)
  val playbackSpeed: StateFlow<Float> = _playbackSpeed

  private val _sleepMinutes = MutableStateFlow(0)
  val sleepMinutes: StateFlow<Int> = _sleepMinutes

  private val _lyrics = MutableStateFlow<LyricsResult?>(null)
  val lyrics: StateFlow<LyricsResult?> = _lyrics

  private val _lyricOffsetMs = MutableStateFlow(0L)
  val lyricOffsetMs: StateFlow<Long> = _lyricOffsetMs

  val verifiedLyricsIds: StateFlow<Set<String>> = dao.observeVerifiedLyricsSongIds()
    .map { it.toSet() }
    .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

  private val lyricsCheckSemaphore = Semaphore(3)
  private val inFlightLyricsChecks = ConcurrentHashMap<String, Job>()

  private var sleepTimerJob: Job? = null
  private var searchJob: Job? = null
  private var lyricsJob: Job? = null

  private val playerListener = object : Player.Listener {
    override fun onIsPlayingChanged(isPlayingVal: Boolean) {
      _isPlaying.value = isPlayingVal
      mediaController?.let { _currentPosition.value = it.currentPosition.coerceAtLeast(0L) }
    }

    override fun onPositionDiscontinuity(
      oldPosition: Player.PositionInfo,
      newPosition: Player.PositionInfo,
      reason: Int
    ) {
      _currentPosition.value = newPosition.positionMs.coerceAtLeast(0L)
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
      val id = mediaItem?.mediaId ?: return
      val transitionedSong = _queue.value.firstOrNull { it.id == id } ?: return
      if (_rawCurrentSong.value?.id != transitionedSong.id) {
        onTrackActivated(transitionedSong)
      }
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
      val controller = mediaController ?: return
      if (playbackState == Player.STATE_READY && controller.duration > 0L) {
        val realDurMs = controller.duration
        _durationMs.value = realDurMs
        val realDurSec = realDurMs / 1000L
        val cur = _rawCurrentSong.value
        val activeMediaId = controller.currentMediaItem?.mediaId
        if (cur != null && activeMediaId == cur.id && realDurSec > 35L) {
          val prevFull = cur.fullDurationSeconds.coerceAtLeast(cur.durationSeconds)
          if (cur.durationSeconds <= 35L || kotlin.math.abs(prevFull - realDurSec) >= 5L) {
            val updated = cur.copy(
              durationSeconds = realDurSec,
              fullDurationSeconds = realDurSec
            )
            _rawCurrentSong.value = updated
            if (_lyrics.value == null || kotlin.math.abs(prevFull - realDurSec) >= 5L) {
              fetchLyrics(updated)
            }
          }
        }
      }
      _currentPosition.value = controller.currentPosition.coerceAtLeast(0L)
    }
  }

  init {
    viewModelScope.launch(Dispatchers.IO) {
      dao.clearAllBadLyrics()
      dao.clearReportedLyricsCache()
    }
    connectPlaybackController(application)
    loadDailyRecommendations(DailyFeedCategory.TIKTOK_VIRAL)
    startPositionTicker()
    GitHubUpdater.cleanupObsoleteApks(application, _installedAppInfo.value.versionCode)
    checkForUpdates(application, silent = true)
  }

  private fun connectPlaybackController(application: Application) {
    val sessionToken = SessionToken(application, ComponentName(application, PlaybackService::class.java))
    val future = MediaController.Builder(application, sessionToken).buildAsync()
    controllerFuture = future
    future.addListener({
      try {
        val controller = future.get()
        mediaController = controller
        controller.addListener(playerListener)
        _isPlaying.value = controller.isPlaying
        controller.currentMediaItem?.mediaId?.let { activeId ->
          _queue.value.firstOrNull { it.id == activeId }?.let { _rawCurrentSong.value = it }
        }
        val pending = pendingPlayRequest
        if (pending != null) {
          pendingPlayRequest = null
          startQueueOnController(controller, pending.first, pending.second)
        }
      } catch (_: Exception) {
        // Session service connection failed gracefully
      }
    }, ContextCompat.getMainExecutor(application))
  }

  private fun startPositionTicker() {
    viewModelScope.launch {
      while (isActive) {
        val controller = mediaController
        if (controller != null && (controller.isPlaying || controller.playbackState == Player.STATE_READY)) {
          _currentPosition.value = controller.currentPosition.coerceAtLeast(0L)
          val playerDur = controller.duration
          if (playerDur > 0L) {
            _durationMs.value = playerDur
          }
        }
        // 50ms cadence keeps synced lyrics tightly locked to ExoPlayer's audio clock
        delay(if (_isPlaying.value) 50L else 250L)
      }
    }
  }

  private fun onTrackActivated(song: Song) {
    _rawCurrentSong.value = song
    _currentPosition.value = 0L
    _durationMs.value = (song.durationSeconds * 1000L).coerceAtLeast(1000L)
    _lyricOffsetMs.value = 0L

    viewModelScope.launch {
      _lyricOffsetMs.value = dao.getLyricOffset(song.id) ?: 0L
      dao.recordPlay(
        PlayHistoryEntity(
          songId = song.id,
          title = song.title,
          artist = song.artist,
          artworkUrl = song.artworkUrl,
          durationSeconds = song.durationSeconds,
          streamUrl = song.streamUrl,
          source = song.source
        )
      )
    }
    fetchLyrics(song)
  }

  private fun buildMediaItem(song: Song): MediaItem {
    val pkg = getApplication<Application>().packageName
    val resolvedArtworkUri = when {
      song.artworkUrl == APP_ICON_ARTWORK ||
        (song.artworkUrl.isBlank() && (song.source == "Local" || song.source == "Imported")) -> {
        Uri.parse("android.resource://$pkg/${com.example.R.drawable.aurix_clean_logo_1794625921343}")
      }
      song.artworkUrl.isNotBlank() -> Uri.parse(song.artworkUrl)
      else -> null
    }
    val metadata = MediaMetadata.Builder()
      .setTitle(song.title)
      .setArtist(song.artist)
      .apply {
        if (resolvedArtworkUri != null) {
          setArtworkUri(resolvedArtworkUri)
        }
      }
      .build()

    return MediaItem.Builder()
      .setMediaId(song.id)
      .setUri(Uri.parse(song.streamUrl))
      .setMediaMetadata(metadata)
      .build()
  }

  private fun startQueueOnController(controller: MediaController, song: Song, resolvedQueue: List<Song>) {
    val playableQueue = resolvedQueue.filter { it.streamUrl.isNotBlank() }.ifEmpty { listOf(song) }
    val startIndex = playableQueue.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
    val mediaItems = playableQueue.map { buildMediaItem(it) }

    controller.stop()
    controller.setMediaItems(mediaItems, startIndex, 0L)
    controller.playbackParameters = PlaybackParameters(_playbackSpeed.value)
    controller.shuffleModeEnabled = _shuffleEnabled.value
    controller.repeatMode = if (_repeatEnabled.value) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_ALL
    controller.prepare()
    controller.play()
  }

  fun setSearchMode(mode: String) {
    _searchMode.value = mode
  }

  fun setYoutubeUrl(url: String) {
    _youtubeUrl.value = url
    _youtubeError.value = null
  }

  fun selectYoutubeFormat(formatId: String) {
    _selectedYoutubeFormatId.value = formatId
    if (_youtubeDownloadState.value is YoutubeDownloadState.Error) {
      _youtubeDownloadState.value = YoutubeDownloadState.Idle
    }
  }

  private fun cleanChannelAuthor(rawAuthor: String): String {
    return rawAuthor
      .replace(Regex("""\s*-\s*Topic$""", RegexOption.IGNORE_CASE), "")
      .replace(Regex("""VEVO$""", RegexOption.IGNORE_CASE), "")
      .replace(Regex("""Official$""", RegexOption.IGNORE_CASE), "")
      .trim()
  }

  private fun stripYoutubeNoiseTags(rawTitle: String): String {
    return rawTitle
      .replace(
        Regex(
          """[\(\[]\s*(?:official\s*(?:music\s*)?(?:video|audio|lyric\s*video|visualizer)|lyric(?:s|\s*video)?|audio\s*only|full\s*audio|mv|m/v|hd|4k|hq|explicit|clean)\s*[\)\]]""",
          RegexOption.IGNORE_CASE
        ),
        ""
      )
      .replace(Regex("""\s*\|\s*[^|]+$"""), "")
      .replace(Regex("""\s+"""), " ")
      .trim()
  }

  private fun parseYoutubeTitleAndArtist(rawTitle: String, rawAuthor: String): Pair<String, String> {
    val cleanedNoise = stripYoutubeNoiseTags(rawTitle)
    val channelArtist = cleanChannelAuthor(rawAuthor)
    val separators = listOf(" - ", " – ", " — ")
    for (sep in separators) {
      val idx = cleanedNoise.indexOf(sep)
      if (idx > 0 && idx + sep.length < cleanedNoise.length) {
        val left = cleanedNoise.substring(0, idx).trim()
        val right = cleanedNoise.substring(idx + sep.length).trim()
        if (left.isNotBlank() && right.isNotBlank()) {
          return right to left
        }
      }
    }
    return cleanedNoise.ifBlank { rawTitle } to channelArtist
  }

  fun analyzeYoutubeUrl() {
    val rawUrl = _youtubeUrl.value.trim()
    if (rawUrl.isBlank()) {
      _youtubeError.value = "Paste a YouTube URL first."
      return
    }
    if (!rawUrl.contains("youtube.com", ignoreCase = true) && !rawUrl.contains("youtu.be", ignoreCase = true)) {
      _youtubeError.value = "Enter a valid YouTube URL (youtube.com or youtu.be)."
      return
    }

    viewModelScope.launch {
      _isAnalyzingYoutube.value = true
      _youtubeError.value = null
      _youtubeAnalysis.value = null
      _selectedYoutubeFormatId.value = null
      _youtubeDownloadState.value = YoutubeDownloadState.Idle

      try {
        val videoId = YoutubeExtractor.extractVideoId(rawUrl)
        if (videoId.isNullOrBlank()) {
          _youtubeError.value = "Could not find a valid YouTube video ID in this link."
          return@launch
        }

        val oembedDeferred = async {
          runCatching { NetworkClient.youtubeOEmbedApi.getVideoInfo(rawUrl) }.getOrNull()
        }
        val extractedDeferred = async {
          YoutubeExtractor.extractFullAudio(videoId)
        }

        val extracted = extractedDeferred.await()
        val info = oembedDeferred.await()

        val rawTitle = extracted?.title?.takeIf { it.isNotBlank() }
          ?: info?.title?.trim()?.takeIf { it.isNotBlank() }
          .orEmpty()
        val rawAuthor = extracted?.author?.takeIf { it.isNotBlank() }
          ?: info?.authorName?.trim()?.takeIf { it.isNotBlank() }
          .orEmpty()
        val thumbnailUrl = extracted?.thumbnailUrl?.takeIf { it.isNotBlank() }
          ?: info?.thumbnailUrl?.trim()?.takeIf { it.isNotBlank() }
          ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"

        if (rawTitle.isBlank()) {
          _youtubeError.value = "Unable to load video info from this YouTube link."
          return@launch
        }

        val fullFormats = extracted?.audioFormats.orEmpty()
        if (fullFormats.isEmpty()) {
          val reason = extracted?.extractorError?.takeIf { it.isNotBlank() }
          _youtubeError.value = if (reason != null) {
            "Could not extract full YouTube audio ($reason)."
          } else {
            "Could not extract full-length audio stream for this video. Please try another link or retry."
          }
          return@launch
        }

        val (parsedTitle, parsedArtist) = parseYoutubeTitleAndArtist(rawTitle, rawAuthor)
        val cleanedForSearch = cleanTrackTitle(parsedTitle).ifBlank { parsedTitle }
        val searchTerm = listOf(cleanedForSearch, parsedArtist)
          .filter { it.isNotBlank() }
          .joinToString(" ")
          .ifBlank { parsedTitle }

        // Cross-reference metadata only (title, artist, square album art) for accurate lyrics syncing
        val itunesMatch = runCatching {
          NetworkClient.itunesApi.searchSongs(term = searchTerm, limit = 5)
        }.getOrNull()?.results?.let { mapItunesTracks(it) }?.firstOrNull()

        val videoDurSec = extracted?.durationSeconds?.takeIf { it > 0L }
          ?: fullFormats.firstOrNull()?.durationSeconds
          ?: 0L

        val trackTitle = itunesMatch?.title?.takeIf {
          val nMatch = normalizeForMatch(cleanTrackTitle(it))
          val nParsed = normalizeForMatch(cleanedForSearch)
          nMatch.isNotBlank() && (nMatch == nParsed || nMatch.contains(nParsed) || nParsed.contains(nMatch))
        } ?: parsedTitle

        val trackArtist = itunesMatch?.artist?.takeIf { trackTitle == itunesMatch.title }
          ?: parsedArtist.ifBlank { "YouTube Audio" }

        val displayThumb = thumbnailUrl.ifBlank { itunesMatch?.artworkUrl.orEmpty() }

        val analysis = YoutubeAnalysisResult(
          videoId = videoId,
          originalUrl = rawUrl,
          videoTitle = rawTitle,
          channelName = rawAuthor.ifBlank { trackArtist },
          thumbnailUrl = displayThumb,
          matchedTrackTitle = trackTitle,
          matchedArtist = trackArtist,
          durationSeconds = videoDurSec,
          formats = fullFormats
        )
        _youtubeAnalysis.value = analysis
        _selectedYoutubeFormatId.value = fullFormats.first().id
      } catch (_: Exception) {
        _youtubeError.value = "Unable to analyze YouTube link. Check your network connection."
      } finally {
        _isAnalyzingYoutube.value = false
      }
    }
  }

  fun downloadAnalyzedTrack(context: Context) {
    val analysis = _youtubeAnalysis.value ?: return
    val selectedId = _selectedYoutubeFormatId.value
    val format = analysis.formats.firstOrNull { it.id == selectedId } ?: analysis.formats.firstOrNull() ?: return
    if (_youtubeDownloadState.value is YoutubeDownloadState.Downloading) return

    viewModelScope.launch {
      var activeFormat = format
      var formatLabel = "${activeFormat.formatLabel} (${activeFormat.qualityLabel})"
      _youtubeDownloadState.value = YoutubeDownloadState.Downloading(0.02f, 0L, activeFormat.contentLengthBytes, formatLabel)

      var downloadRes = SongDownloader.downloadFormatToApp(
        context = context,
        title = analysis.matchedTrackTitle,
        artist = analysis.matchedArtist,
        format = activeFormat
      ) { progressInfo ->
        _youtubeDownloadState.value = YoutubeDownloadState.Downloading(
          progress = progressInfo.fraction,
          downloadedBytes = progressInfo.downloadedBytes,
          totalBytes = progressInfo.totalBytes,
          formatLabel = formatLabel
        )
      }

      if (downloadRes.isFailure) {
        _youtubeDownloadState.value = YoutubeDownloadState.Downloading(
          progress = 0.04f,
          downloadedBytes = 0L,
          totalBytes = activeFormat.contentLengthBytes,
          formatLabel = "Switching stream mirror..."
        )
        val refreshed = YoutubeExtractor.extractFreshWorkingFormat(
          videoId = analysis.videoId,
          preferredExtension = activeFormat.extension,
          failedFormatId = activeFormat.id
        )
        if (refreshed != null) {
          activeFormat = refreshed
          formatLabel = "${activeFormat.formatLabel} (${activeFormat.qualityLabel})"
          downloadRes = SongDownloader.downloadFormatToApp(
            context = context,
            title = analysis.matchedTrackTitle,
            artist = analysis.matchedArtist,
            format = activeFormat
          ) { progressInfo ->
            _youtubeDownloadState.value = YoutubeDownloadState.Downloading(
              progress = progressInfo.fraction,
              downloadedBytes = progressInfo.downloadedBytes,
              totalBytes = progressInfo.totalBytes,
              formatLabel = formatLabel
            )
          }
        }
      }

      downloadRes.onSuccess { savedAudio ->
        val cachedCover = SongDownloader.cacheRemoteArtwork(
          context = context,
          songId = savedAudio.uri.toString(),
          imageUrl = analysis.thumbnailUrl
        )
        val artwork = cachedCover ?: analysis.thumbnailUrl.ifBlank { APP_ICON_ARTWORK }
        val finalDuration = savedAudio.durationSeconds
          .takeIf { it > 0L }
          ?: analysis.durationSeconds.takeIf { it > 0L }
          ?: activeFormat.durationSeconds

        val entity = LocalSongEntity(
          id = savedAudio.uri.toString(),
          title = analysis.matchedTrackTitle,
          artist = analysis.matchedArtist,
          artworkUrl = artwork,
          durationSeconds = finalDuration,
          streamUrl = savedAudio.uri.toString(),
          source = "Downloaded"
        )
        dao.insertLocalSong(entity)

        val savedSong = Song(
          id = entity.id,
          title = entity.title,
          artist = entity.artist,
          artworkUrl = entity.artworkUrl,
          durationSeconds = entity.durationSeconds,
          streamUrl = entity.streamUrl,
          source = entity.source,
          isFavorite = entity.id in favoriteIds.value,
          fullDurationSeconds = entity.durationSeconds
        )
        _youtubeDownloadState.value = YoutubeDownloadState.Saved(savedSong)
        Toast.makeText(context, "Saved full song (${YoutubeExtractor.formatDurationShort(finalDuration)}): ${savedSong.title}", Toast.LENGTH_LONG).show()
      }.onFailure { err ->
        val msg = err.localizedMessage ?: "Download failed"
        _youtubeDownloadState.value = YoutubeDownloadState.Error(msg)
        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
      }
    }
  }

  fun checkForUpdates(context: Context, silent: Boolean = false) {
    viewModelScope.launch {
      _installedAppInfo.value = GitHubUpdater.getInstalledAppInfo(context)
      _updateCheckStatus.value = UpdateCheckStatus.Checking
      _updateStatus.value = "Checking GitHub releases..."

      val owner = githubOwner.value.trim().ifBlank { "MEYYYNASH" }
      val repo = githubRepo.value.trim().ifBlank { "AURIX" }
      val result = GitHubUpdater.checkForUpdates(context, owner, repo)
      _updateCheckStatus.value = result
      _updateStatus.value = GitHubUpdater.statusTextFor(result)

      when (result) {
        is UpdateCheckStatus.UpdateAvailable -> {
          _latestRelease.value = result.release
          val validated = GitHubUpdater.hasValidatedNewerApk(context)
          if (validated != null && validated.apkVersionCode == result.release.versionCode) {
            _apkDownloadState.value = validated
          } else if (_apkDownloadState.value !is ApkDownloadState.Downloading) {
            _apkDownloadState.value = ApkDownloadState.Idle
          }
        }
        is UpdateCheckStatus.UpToDate -> {
          _latestRelease.value = result.release
          if (_apkDownloadState.value !is ApkDownloadState.Downloading) {
            _apkDownloadState.value = ApkDownloadState.Idle
          }
          if (!silent) {
            Toast.makeText(context, "You are already using the latest version.", Toast.LENGTH_SHORT).show()
          }
        }
        is UpdateCheckStatus.DowngradeBlocked -> {
          _latestRelease.value = result.release
          if (_apkDownloadState.value !is ApkDownloadState.Downloading) {
            _apkDownloadState.value = ApkDownloadState.Idle
          }
          if (!silent) {
            Toast.makeText(context, GitHubUpdater.statusTextFor(result), Toast.LENGTH_LONG).show()
          }
        }
        is UpdateCheckStatus.Error -> {
          if (!silent) {
            Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show()
          }
        }
        else -> Unit
      }
    }
  }

  fun downloadAndInstallUpdate(context: Context) {
    if (_apkDownloadState.value is ApkDownloadState.Downloading) return
    viewModelScope.launch {
      val installed = GitHubUpdater.getInstalledAppInfo(context)
      _installedAppInfo.value = installed

      var currentStatus = _updateCheckStatus.value
      if (currentStatus !is UpdateCheckStatus.UpdateAvailable) {
        _updateCheckStatus.value = UpdateCheckStatus.Checking
        _updateStatus.value = "Verifying latest release..."
        val owner = githubOwner.value.trim().ifBlank { "MEYYYNASH" }
        val repo = githubRepo.value.trim().ifBlank { "AURIX" }
        currentStatus = GitHubUpdater.checkForUpdates(context, owner, repo)
        _updateCheckStatus.value = currentStatus
        _updateStatus.value = GitHubUpdater.statusTextFor(currentStatus)
      }

      when (currentStatus) {
        is UpdateCheckStatus.UpToDate -> {
          _latestRelease.value = currentStatus.release
          Toast.makeText(context, "You are already using the latest version.", Toast.LENGTH_SHORT).show()
          return@launch
        }
        is UpdateCheckStatus.DowngradeBlocked -> {
          _latestRelease.value = currentStatus.release
          val msg = GitHubUpdater.statusTextFor(currentStatus)
          _apkDownloadState.value = ApkDownloadState.Error(msg)
          Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
          return@launch
        }
        is UpdateCheckStatus.Error -> {
          _apkDownloadState.value = ApkDownloadState.Error(currentStatus.message)
          Toast.makeText(context, currentStatus.message, Toast.LENGTH_LONG).show()
          return@launch
        }
        is UpdateCheckStatus.UpdateAvailable -> {
          val targetRelease = currentStatus.release
          _latestRelease.value = targetRelease

          val existingValidated = GitHubUpdater.hasValidatedNewerApk(context)
          if (existingValidated != null && existingValidated.apkVersionCode >= targetRelease.versionCode) {
            _apkDownloadState.value = existingValidated
            val installRes = GitHubUpdater.installDownloadedApk(context)
            _updateStatus.value = installRes.message
            Toast.makeText(context, installRes.message, Toast.LENGTH_LONG).show()
            return@launch
          }

          _updateStatus.value = "Downloading ${targetRelease.tagName}..."
          val finalState = GitHubUpdater.downloadApk(context, targetRelease) { state ->
            _apkDownloadState.value = state
            when (state) {
              is ApkDownloadState.Downloading -> {
                val pct = if (state.progress >= 0f) "${(state.progress * 100).toInt()}%" else "${state.downloadedBytes / (1024 * 1024)} MB"
                _updateStatus.value = "Downloading ${targetRelease.tagName} ($pct)"
              }
              is ApkDownloadState.ValidatedReady -> {
                _updateStatus.value = "Verified ${targetRelease.tagName} (code ${state.apkVersionCode}). Starting installer..."
              }
              is ApkDownloadState.Error -> {
                _updateStatus.value = state.message
              }
              else -> Unit
            }
          }

          if (finalState is ApkDownloadState.ValidatedReady) {
            val installRes = GitHubUpdater.installDownloadedApk(context)
            _updateStatus.value = installRes.message
            Toast.makeText(context, installRes.message, Toast.LENGTH_LONG).show()
          } else if (finalState is ApkDownloadState.Error) {
            Toast.makeText(context, finalState.message, Toast.LENGTH_LONG).show()
          }
        }
        else -> Unit
      }
    }
  }

  fun installDownloadedApk(context: Context) {
    val res = GitHubUpdater.installDownloadedApk(context)
    _updateStatus.value = res.message
    if (!res.launched && !res.requiresPermission) {
      _apkDownloadState.value = ApkDownloadState.Error(res.message)
    }
    Toast.makeText(context, res.message, Toast.LENGTH_LONG).show()
  }

  fun openGitHubRepo(context: Context) {
    try {
      val owner = githubOwner.value.trim().ifBlank { "MEYYYNASH" }
      val repo = githubRepo.value.trim().ifBlank { "AURIX" }
      val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/$owner/$repo/releases")).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
      context.startActivity(intent)
    } catch (_: Exception) {
      Toast.makeText(context, "Unable to open browser", Toast.LENGTH_SHORT).show()
    }
  }

  fun downloadSong(context: Context, song: Song) {
    if (_songDownloadProgress.value.containsKey(song.id)) return
    viewModelScope.launch {
      _songDownloadProgress.value = _songDownloadProgress.value + (song.id to 0.05f)
      Toast.makeText(context, "Saving to app: ${song.title}...", Toast.LENGTH_SHORT).show()

      val result = SongDownloader.downloadSongToDevice(context, song) { progress ->
        _songDownloadProgress.value = _songDownloadProgress.value + (song.id to progress)
      }

      _songDownloadProgress.value = _songDownloadProgress.value - song.id
      result.onSuccess { savedAudio ->
        _downloadedSongIds.value = _downloadedSongIds.value + song.id
        val cachedCover = SongDownloader.cacheRemoteArtwork(context, savedAudio.uri.toString(), song.artworkUrl)
        val entity = LocalSongEntity(
          id = savedAudio.uri.toString(),
          title = song.title,
          artist = song.artist,
          artworkUrl = cachedCover ?: song.artworkUrl.ifBlank { APP_ICON_ARTWORK },
          durationSeconds = savedAudio.durationSeconds.takeIf { it > 0L } ?: song.durationSeconds,
          streamUrl = savedAudio.uri.toString(),
          source = "Downloaded"
        )
        dao.insertLocalSong(entity)
        Toast.makeText(context, "Saved to My songs & Music/AURIX: ${song.title}", Toast.LENGTH_LONG).show()
      }.onFailure { err ->
        Toast.makeText(context, err.localizedMessage ?: "Download failed", Toast.LENGTH_LONG).show()
      }
    }
  }

  private fun todayDateKey(): String {
    return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
  }

  private fun formatTodayBadge(): String {
    return "Updated Today • ${SimpleDateFormat("MMM d", Locale.US).format(Date())}"
  }

  private fun isQualityViralTrack(title: String?, artist: String?): Boolean {
    val t = title.orEmpty().lowercase(Locale.US)
    val a = artist.orEmpty().lowercase(Locale.US)
    if (t.isBlank()) return false
    val blocked = listOf(
      "workout mix",
      "power music",
      "mixxc",
      "karaoke",
      "ringtone",
      "lullaby",
      "originally performed",
      "made famous by",
      "tribute to",
      "8d audio",
      "sleep music",
      "baby sleep",
      "bpm)"
    )
    return blocked.none { kw -> t.contains(kw) || a.contains(kw) }
  }

  private fun mapItunesTracks(results: List<ItunesTrack>, sourceTag: String = "iTunes 30s"): List<Song> {
    return results
      .filter { !it.previewUrl.isNullOrBlank() && !it.trackName.isNullOrBlank() }
      .distinctBy { it.trackId }
      .map { track ->
        val art = (track.artworkUrl100 ?: track.artworkUrl60)
          ?.replace("100x100bb", "600x600bb")
          ?.replace("60x60bb", "600x600bb")
          ?: ""
        val fullDurSec = (track.trackTimeMillis ?: 0L) / 1000L
        Song(
          id = "itunes_${track.trackId}",
          title = track.trackName ?: "Untitled",
          artist = track.artistName ?: "Unknown Artist",
          artworkUrl = art,
          durationSeconds = 30L,
          streamUrl = track.previewUrl.orEmpty(),
          source = sourceTag,
          fullDurationSeconds = if (fullDurSec > 0L) fullDurSec else 30L
        )
      }
  }

  private fun mapDeezerTracks(results: List<DeezerTrack>, sourceTag: String): List<Song> {
    return results
      .filter {
        !it.preview.isNullOrBlank() &&
          !it.title.isNullOrBlank() &&
          isQualityViralTrack(it.title, it.artist?.name)
      }
      .distinctBy { it.id }
      .map { track ->
        val art = track.album?.coverXl
          ?: track.album?.coverBig
          ?: track.album?.coverMedium
          ?: ""
        Song(
          id = "deezer_${track.id}",
          title = track.title ?: "Untitled",
          artist = track.artist?.name ?: "Unknown Artist",
          artworkUrl = art,
          durationSeconds = 30L,
          streamUrl = track.preview.orEmpty(),
          source = sourceTag,
          fullDurationSeconds = if (track.duration > 0L) track.duration else 30L
        )
      }
  }

  private suspend fun fetchDeezerPlaylistFeed(queries: List<String>, sourceTag: String): List<Song> {
    val keywords = listOf("tiktok", "viral", "trending", "hits", "202", "phonk", "khmer")
    for (q in queries) {
      val searchRes = runCatching { NetworkClient.deezerApi.searchPlaylists(query = q, limit = 12) }.getOrNull()
        ?: continue
      val rankedPlaylists = searchRes.data
        .filter { it.id > 0L && it.nbTracks >= 10 && isQualityViralTrack(it.title, "") }
        .sortedByDescending { summary ->
          val titleLower = summary.title.orEmpty().lowercase(Locale.US)
          val matchScore = keywords.count { titleLower.contains(it) } * 100
          val reasonableSizeBonus = if (summary.nbTracks in 15..180) 40 else 0
          matchScore + reasonableSizeBonus
        }

      for (playlist in rankedPlaylists.take(3)) {
        val tracksRes = runCatching { NetworkClient.deezerApi.getPlaylistTracks(playlist.id, limit = 35) }.getOrNull()
          ?: continue
        val mapped = mapDeezerTracks(tracksRes.data, sourceTag)
        if (mapped.size >= 8) {
          return mapped
        }
      }
    }
    return emptyList()
  }

  private suspend fun fetchAppleDailyChartTracks(sourceTag: String = "Daily Chart"): List<Song> {
    val chart = runCatching { NetworkClient.appleChartsApi.getMostPlayed(country = "us", limit = 25) }.getOrNull()
      ?: return emptyList()
    val entries = chart.feed?.results.orEmpty()
    val ids = entries.mapNotNull { it.id?.takeIf { id -> id.isNotBlank() } }
    if (ids.isEmpty()) return emptyList()

    val lookup = runCatching { NetworkClient.itunesApi.lookupTracks(ids.joinToString(",")) }.getOrNull()
      ?: return emptyList()
    val byId = mapItunesTracks(lookup.results, sourceTag).associateBy { it.id.removePrefix("itunes_") }
    return ids.mapNotNull { id -> byId[id] }
      .filter { isQualityViralTrack(it.title, it.artist) }
  }

  fun selectDailyFeed(category: DailyFeedCategory) {
    _selectedDailyFeed.value = category
    loadDailyRecommendations(category = category, forceRefresh = false)
  }

  fun refreshDailyFeedIfNewDay() {
    val today = todayDateKey()
    if (today != lastFeedDateKey) {
      lastFeedDateKey = today
      dailyFeedCache.clear()
      _dailyUpdatedLabel.value = formatTodayBadge()
      loadDailyRecommendations(category = _selectedDailyFeed.value, forceRefresh = true)
    }
  }

  fun loadFeaturedItunes() {
    loadDailyRecommendations(category = _selectedDailyFeed.value, forceRefresh = true)
  }

  fun loadDailyRecommendations(
    category: DailyFeedCategory = _selectedDailyFeed.value,
    forceRefresh: Boolean = false
  ) {
    val today = todayDateKey()
    if (today != lastFeedDateKey) {
      lastFeedDateKey = today
      dailyFeedCache.clear()
    }
    _dailyUpdatedLabel.value = formatTodayBadge()

    val cacheKey = today to category
    if (!forceRefresh) {
      dailyFeedCache[cacheKey]?.takeIf { it.isNotEmpty() }?.let { cached ->
        _rawFeatured.value = cached
        _featuredError.value = null
        _isLoadingFeatured.value = false
        return
      }
    }

    feedJob?.cancel()
    feedJob = viewModelScope.launch {
      _isLoadingFeatured.value = true
      _featuredError.value = null
      try {
        val loaded: List<Song> = when (category) {
          DailyFeedCategory.TIKTOK_VIRAL -> {
            val playlistTracks = fetchDeezerPlaylistFeed(
              queries = listOf("tiktok viral", "tiktok hits 2026", "trending on tiktok"),
              sourceTag = "TikTok Viral"
            )
            if (playlistTracks.isNotEmpty()) {
              playlistTracks
            } else {
              val searchTracks = runCatching {
                NetworkClient.deezerApi.searchTracks("tiktok viral", limit = 25)
              }.getOrNull()?.let { mapDeezerTracks(it.data, "TikTok Viral") }.orEmpty()
              if (searchTracks.size >= 6) searchTracks
              else fetchAppleDailyChartTracks("TikTok Viral").ifEmpty {
                val fallbackRes = NetworkClient.itunesApi.searchSongs(term = "tiktok viral", limit = 25)
                mapItunesTracks(fallbackRes.results, "TikTok Viral").filter { isQualityViralTrack(it.title, it.artist) }
              }
            }
          }
          DailyFeedCategory.DAILY_CHARTS -> {
            val appleTracks = fetchAppleDailyChartTracks("Daily Chart")
            if (appleTracks.size >= 6) {
              appleTracks
            } else {
              runCatching { NetworkClient.deezerApi.getChartTracks(limit = 30) }.getOrNull()
                ?.let { mapDeezerTracks(it.data, "Daily Chart") }
                .orEmpty()
            }
          }
          DailyFeedCategory.PHONK_EDITS -> {
            val phonkPlaylist = fetchDeezerPlaylistFeed(
              queries = listOf("tiktok phonk", "montagem phonk", "phonk viral"),
              sourceTag = "TikTok Phonk"
            )
            if (phonkPlaylist.isNotEmpty()) {
              phonkPlaylist
            } else {
              val itunesPhonk = NetworkClient.itunesApi.searchSongs(term = "montagem phonk", limit = 25)
              mapItunesTracks(itunesPhonk.results, "TikTok Phonk").filter { isQualityViralTrack(it.title, it.artist) }
            }
          }
          DailyFeedCategory.KHMER_ASIA -> {
            val khmerPlaylist = fetchDeezerPlaylistFeed(
              queries = listOf("khmer viral", "cambodia hits", "khmer pop"),
              sourceTag = "Khmer Hits"
            )
            if (khmerPlaylist.isNotEmpty()) {
              khmerPlaylist
            } else {
              val itunesKhmer = NetworkClient.itunesApi.searchSongs(term = "khmer", limit = 25)
              mapItunesTracks(itunesKhmer.results, "Khmer Hits").filter { isQualityViralTrack(it.title, it.artist) }
            }
          }
        }

        if (loaded.isNotEmpty()) {
          dailyFeedCache[cacheKey] = loaded
          _rawFeatured.value = loaded
          _featuredError.value = null
        } else {
          _rawFeatured.value = emptyList()
          _featuredError.value = "Unable to load today's ${category.label} feed. Check your connection."
        }
      } catch (_: Exception) {
        _rawFeatured.value = emptyList()
        _featuredError.value = "Unable to refresh daily sounds right now."
      } finally {
        _isLoadingFeatured.value = false
      }
    }
  }

  fun search(query: String) {
    _searchQuery.value = query
    searchJob?.cancel()
    if (query.isBlank()) {
      _rawSearchResults.value = emptyList()
      _searchError.value = null
      _isSearching.value = false
      return
    }
    searchJob = viewModelScope.launch {
      _isSearching.value = true
      _searchError.value = null
      delay(300)
      try {
        val res = NetworkClient.itunesApi.searchSongs(term = query.trim(), limit = 30)
        _rawSearchResults.value = mapItunesTracks(res.results)
      } catch (_: Exception) {
        _rawSearchResults.value = emptyList()
        _searchError.value = "Network error while searching iTunes."
      } finally {
        _isSearching.value = false
      }
    }
  }

  fun playSong(song: Song, newQueue: List<Song> = _rawFeatured.value.ifEmpty { listOf(song) }) {
    if (song.streamUrl.isBlank()) {
      Toast.makeText(getApplication(), "No playable 30s preview URL for this track.", Toast.LENGTH_SHORT).show()
      return
    }

    val resolvedQueue = if (newQueue.any { it.id == song.id }) newQueue else listOf(song) + newQueue
    _queue.value = resolvedQueue
    onTrackActivated(song)

    val controller = mediaController
    if (controller == null) {
      pendingPlayRequest = song to resolvedQueue
      return
    }

    try {
      startQueueOnController(controller, song, resolvedQueue)
    } catch (_: Exception) {
      Toast.makeText(getApplication(), "Unable to play this track.", Toast.LENGTH_SHORT).show()
    }
  }

  fun nextTrack() {
    val controller = mediaController
    if (controller != null && controller.hasNextMediaItem()) {
      controller.seekToNextMediaItem()
      controller.play()
      return
    }
    val q = _queue.value
    if (q.isEmpty()) return
    val current = _rawCurrentSong.value
    val curIdx = q.indexOfFirst { it.id == current?.id }.coerceAtLeast(0)
    val nextIdx = if (_shuffleEnabled.value && q.size > 1) {
      q.indices.filter { it != curIdx }.random()
    } else {
      (curIdx + 1) % q.size
    }
    playSong(q[nextIdx], q)
  }

  fun previousTrack() {
    val controller = mediaController
    if (controller != null && controller.currentPosition > 3000L) {
      controller.seekTo(0L)
      _currentPosition.value = 0L
      return
    }
    if (controller != null && controller.hasPreviousMediaItem()) {
      controller.seekToPreviousMediaItem()
      controller.play()
      return
    }
    val q = _queue.value
    if (q.isEmpty()) return
    val current = _rawCurrentSong.value
    val curIdx = q.indexOfFirst { it.id == current?.id }.coerceAtLeast(0)
    val prevIdx = (curIdx - 1 + q.size) % q.size
    playSong(q[prevIdx], q)
  }

  fun toggleShuffle() {
    val next = !_shuffleEnabled.value
    _shuffleEnabled.value = next
    mediaController?.shuffleModeEnabled = next
  }

  fun toggleRepeat() {
    val next = !_repeatEnabled.value
    _repeatEnabled.value = next
    mediaController?.repeatMode = if (next) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_ALL
  }

  fun cyclePlaybackSpeed() {
    val nextSpeed = when (_playbackSpeed.value) {
      1.0f -> 1.25f
      1.25f -> 1.5f
      1.5f -> 0.75f
      else -> 1.0f
    }
    _playbackSpeed.value = nextSpeed
    mediaController?.playbackParameters = PlaybackParameters(nextSpeed)
  }

  fun cycleSleepTimer() {
    val nextMin = when (_sleepMinutes.value) {
      0 -> 15
      15 -> 30
      30 -> 60
      else -> 0
    }
    _sleepMinutes.value = nextMin
    sleepTimerJob?.cancel()
    if (nextMin > 0) {
      sleepTimerJob = viewModelScope.launch {
        delay(nextMin * 60_000L)
        mediaController?.pause()
        _sleepMinutes.value = 0
      }
    }
  }

  fun togglePlayPause() {
    val controller = mediaController ?: return
    if (controller.isPlaying) {
      controller.pause()
    } else {
      if (controller.playbackState == Player.STATE_ENDED) {
        controller.seekTo(0L)
      }
      controller.play()
    }
  }

  fun getLivePlaybackPositionMs(): Long {
    return mediaController?.currentPosition?.coerceAtLeast(0L) ?: _currentPosition.value
  }

  fun seekTo(positionMs: Long) {
    mediaController?.seekTo(positionMs)
    _currentPosition.value = positionMs
  }

  fun toggleFavorite(song: Song) {
    viewModelScope.launch {
      val existing = dao.getFavoriteById(song.id)
      if (existing != null) {
        dao.deleteFavorite(song.id)
      } else {
        dao.insertFavorite(
          FavoriteSongEntity(
            id = song.id,
            title = song.title,
            artist = song.artist,
            artworkUrl = song.artworkUrl,
            durationSeconds = song.durationSeconds,
            streamUrl = song.streamUrl,
            source = song.source
          )
        )
      }
    }
  }

  fun addLocalSong(uri: Uri) {
    val appContext = getApplication<Application>()
    viewModelScope.launch {
      val entity = withContext(Dispatchers.IO) {
        try {
          appContext.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION
          )
        } catch (_: Exception) { }

        val existing = dao.getLocalSongById(uri.toString())
        if (existing != null) return@withContext null

        var displayTitle: String? = null
        var artist = "Device Storage"
        var durationSec = 180L

        try {
          appContext.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
              val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
              if (idx >= 0) {
                displayTitle = cursor.getString(idx)?.substringBeforeLast('.')
              }
            }
          }
        } catch (_: Exception) { }

        try {
          val retriever = MediaMetadataRetriever()
          retriever.setDataSource(appContext, uri)
          val metaTitle = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
          val metaArtist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
          val metaDuration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
          if (!metaTitle.isNullOrBlank()) displayTitle = metaTitle
          if (!metaArtist.isNullOrBlank()) artist = metaArtist
          if (metaDuration != null && metaDuration > 0L) durationSec = metaDuration / 1000L
          retriever.release()
        } catch (_: Exception) { }

        val fallbackName = uri.lastPathSegment
          ?.substringAfterLast('/')
          ?.substringBeforeLast('.')
          ?.takeIf { !it.startsWith("audio:") }
          ?: "Local Audio Track"

        LocalSongEntity(
          id = uri.toString(),
          title = displayTitle?.ifBlank { null } ?: fallbackName,
          artist = artist,
          artworkUrl = APP_ICON_ARTWORK,
          durationSeconds = durationSec,
          streamUrl = uri.toString(),
          source = "Local"
        )
      }

      if (entity != null) {
        dao.insertLocalSong(entity)
        Toast.makeText(appContext, "Imported \"${entity.title}\" to My songs", Toast.LENGTH_SHORT).show()
      } else {
        Toast.makeText(appContext, "Song is already in My songs", Toast.LENGTH_SHORT).show()
      }
    }
  }

  fun updateLocalSongArtwork(songId: String, imageUri: Uri) {
    val appContext = getApplication<Application>()
    viewModelScope.launch {
      val result = SongDownloader.saveCustomCoverImage(appContext, songId, imageUri)
      result.onSuccess { savedCoverUri ->
        dao.updateLocalSongArtwork(songId, savedCoverUri)
        dao.updateFavoriteArtwork(songId, savedCoverUri)
        dao.updateHistoryArtwork(songId, savedCoverUri)

        if (_rawCurrentSong.value?.id == songId) {
          _rawCurrentSong.value = _rawCurrentSong.value?.copy(artworkUrl = savedCoverUri)
        }
        _queue.value = _queue.value.map {
          if (it.id == songId) it.copy(artworkUrl = savedCoverUri) else it
        }
        val dlState = _youtubeDownloadState.value
        if (dlState is YoutubeDownloadState.Saved && dlState.song.id == songId) {
          _youtubeDownloadState.value = YoutubeDownloadState.Saved(dlState.song.copy(artworkUrl = savedCoverUri))
        }
        Toast.makeText(appContext, "Custom cover photo saved", Toast.LENGTH_SHORT).show()
      }.onFailure {
        Toast.makeText(appContext, "Unable to update cover photo", Toast.LENGTH_SHORT).show()
      }
    }
  }

  fun deleteLocalSong(song: Song) {
    val appContext = getApplication<Application>()
    viewModelScope.launch {
      withContext(Dispatchers.IO) {
        SongDownloader.deleteAppManagedFileIfOwned(appContext, song.streamUrl)
        SongDownloader.deleteAppManagedFileIfOwned(appContext, song.artworkUrl)
        dao.deleteLocalSong(song.id)
        dao.deleteHistoryBySongId(song.id)
        dao.deleteFavorite(song.id)
        dao.deleteLyricOffset(song.id)
      }

      _downloadedSongIds.value = _downloadedSongIds.value - song.id
      val dlState = _youtubeDownloadState.value
      if (dlState is YoutubeDownloadState.Saved && dlState.song.id == song.id) {
        _youtubeDownloadState.value = YoutubeDownloadState.Idle
      }

      val currentQueue = _queue.value
      val wasCurrent = _rawCurrentSong.value?.id == song.id
      val updatedQueue = currentQueue.filterNot { it.id == song.id }
      _queue.value = updatedQueue

      if (wasCurrent) {
        if (updatedQueue.isNotEmpty()) {
          val oldIdx = currentQueue.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
          val nextSong = updatedQueue[oldIdx.coerceAtMost(updatedQueue.lastIndex)]
          playSong(nextSong, updatedQueue)
        } else {
          mediaController?.stop()
          mediaController?.clearMediaItems()
          _rawCurrentSong.value = null
          _isPlaying.value = false
          _currentPosition.value = 0L
          _durationMs.value = 0L
          _lyrics.value = null
        }
      } else {
        val controller = mediaController
        if (controller != null) {
          for (i in controller.mediaItemCount - 1 downTo 0) {
            if (controller.getMediaItemAt(i).mediaId == song.id) {
              controller.removeMediaItem(i)
            }
          }
        }
      }

      Toast.makeText(appContext, "Deleted \"${song.title}\"", Toast.LENGTH_SHORT).show()
    }
  }

  fun removeFromHistory(song: Song) {
    val appContext = getApplication<Application>()
    viewModelScope.launch {
      withContext(Dispatchers.IO) {
        dao.deleteHistoryBySongId(song.id)
      }
      Toast.makeText(appContext, "Removed \"${song.title}\" from Recent", Toast.LENGTH_SHORT).show()
    }
  }

  fun clearHistory() {
    val appContext = getApplication<Application>()
    viewModelScope.launch {
      withContext(Dispatchers.IO) {
        dao.clearPlayHistory()
      }
      Toast.makeText(appContext, "Cleared recent play history", Toast.LENGTH_SHORT).show()
    }
  }

  fun adjustLyricOffset(deltaMs: Long) {
    val songId = _rawCurrentSong.value?.id ?: return
    val nextOffset = (_lyricOffsetMs.value + deltaMs).coerceIn(-300_000L, 300_000L)
    _lyricOffsetMs.value = nextOffset
    viewModelScope.launch {
      dao.saveLyricOffset(LyricOffsetEntity(songId = songId, offsetMs = nextOffset))
    }
  }

  fun alignLyricsToTimestamp(targetLineTimestampMs: Long) {
    val songId = _rawCurrentSong.value?.id ?: return
    val playerPos = mediaController?.currentPosition?.coerceAtLeast(0L) ?: _currentPosition.value
    val nextOffset = (targetLineTimestampMs - playerPos).coerceIn(-300_000L, 300_000L)
    _lyricOffsetMs.value = nextOffset
    viewModelScope.launch {
      dao.saveLyricOffset(LyricOffsetEntity(songId = songId, offsetMs = nextOffset))
    }
  }

  fun resetLyricOffset() {
    val songId = _rawCurrentSong.value?.id ?: return
    _lyricOffsetMs.value = 0L
    viewModelScope.launch {
      dao.deleteLyricOffset(songId)
    }
  }

  fun retryLyrics() {
    _rawCurrentSong.value?.let { fetchLyrics(it, forceRetry = true) }
  }

  fun ensureLyricsVerified(song: Song) {
    if (song.id.isBlank() || song.title.isBlank() || song.artist.isBlank()) return
    if (song.id in verifiedLyricsIds.value) return
    val existingJob = inFlightLyricsChecks[song.id]
    if (existingJob != null && existingJob.isActive) return

    val job = viewModelScope.launch(Dispatchers.IO) {
      try {
        val songKey = LyricsMatcher.songIdentityKey(song)

        val cached = dao.getLyricsCache(song.id) ?: dao.getLyricsCacheByKey(songKey)?.also { keyHit ->
          if (!keyHit.isReportedBad && keyHit.songId != song.id) {
            dao.insertLyricsCache(keyHit.copy(songId = song.id))
          }
        }
        if (cached != null && !cached.isReportedBad) {
          return@launch
        }

        lyricsCheckSemaphore.withPermit {
          val latestCached = dao.getLyricsCache(song.id)
          if (latestCached != null && !latestCached.isReportedBad) {
            return@withPermit
          }
          val outcome = LyricsMatcher.matchWithOutcome(
            song = song,
            context = getApplication()
          )
          when (outcome) {
            is LyricsMatchOutcome.Verified -> {
              dao.insertLyricsCache(
                LyricsCacheEntity(
                  songId = song.id,
                  songKey = songKey,
                  hasLyrics = true,
                  rawLrc = outcome.lyricsResult.rawLrc,
                  plainLyrics = outcome.lyricsResult.plain,
                  matchedTitle = outcome.lyricsResult.matchedTitle,
                  matchedArtist = outcome.lyricsResult.matchedArtist,
                  isReportedBad = false
                )
              )
            }
            LyricsMatchOutcome.NoLyrics -> {
              dao.insertLyricsCache(
                LyricsCacheEntity(
                  songId = song.id,
                  songKey = songKey,
                  hasLyrics = false,
                  isReportedBad = false
                )
              )
            }
            LyricsMatchOutcome.NetworkError -> {
              // Do NOT cache network errors so they retry later
            }
          }
        }
      } finally {
        inFlightLyricsChecks.remove(song.id)
      }
    }
    inFlightLyricsChecks[song.id] = job
  }

  private fun cleanTrackTitle(raw: String): String {
    return LyricsMatcher.cleanForApiQuery(raw)
  }

  private fun normalizeForMatch(raw: String?): String {
    return LyricsMatcher.normalizeText(raw)
  }

  private fun buildLyricsResultFromCache(cached: LyricsCacheEntity, song: Song): LyricsResult? {
    val rawLrc = cached.rawLrc?.takeIf { it.isNotBlank() }
    val plain = cached.plainLyrics?.takeIf { it.isNotBlank() }
    if (rawLrc != null) {
      val parsed = parseSyncedLyrics(rawLrc)
      if (parsed.isNotEmpty()) {
        return LyricsResult(
          synced = parsed,
          plain = plain,
          matchedTitle = cached.matchedTitle ?: song.title,
          matchedArtist = cached.matchedArtist ?: song.artist,
          rawLrc = rawLrc
        )
      }
    }
    if (plain != null) {
      return LyricsResult(
        synced = null,
        plain = plain,
        matchedTitle = cached.matchedTitle ?: song.title,
        matchedArtist = cached.matchedArtist ?: song.artist,
        rawLrc = rawLrc ?: plain
      )
    }
    return null
  }

  private fun fetchLyrics(song: Song, forceRetry: Boolean = false) {
    lyricsJob?.cancel()
    _lyrics.value = null
    lyricsJob = viewModelScope.launch {
      val appContext = getApplication<Application>()
      val noLyricsMsg = appContext.getString(R.string.no_lyrics_found)
      val songKey = LyricsMatcher.songIdentityKey(song)

      try {
        val activeControllerDurSec = mediaController
          ?.takeIf { it.currentMediaItem?.mediaId == song.id && it.duration > 0L }
          ?.duration?.div(1000L) ?: 0L

        // Serve verified cache if available (reusing exact same result as LyricsTag)
        val cached = withContext(Dispatchers.IO) {
          dao.getLyricsCache(song.id) ?: dao.getLyricsCacheByKey(songKey)
        }
        if (cached != null && !cached.isReportedBad) {
          if (cached.hasLyrics) {
            val fromCache = buildLyricsResultFromCache(cached, song)
            if (fromCache != null) {
              _lyrics.value = fromCache
              if (cached.songId != song.id) {
                withContext(Dispatchers.IO) { dao.insertLyricsCache(cached.copy(songId = song.id)) }
              }
              return@launch
            }
          } else if (!forceRetry && activeControllerDurSec <= 35L) {
            _lyrics.value = LyricsResult(synced = null, plain = noLyricsMsg, rawLrc = null)
            return@launch
          }
        }

        // 3. Query LRCLIB & strictly verify candidates with LyricsMatcher
        val overrideDurSec = activeControllerDurSec.takeIf { it > 35L }?.toDouble()
        val outcome = LyricsMatcher.matchWithOutcome(
          song = song,
          context = appContext,
          overrideDurationSec = overrideDurSec
        )

        when (outcome) {
          is LyricsMatchOutcome.Verified -> {
            withContext(Dispatchers.IO) {
              dao.insertLyricsCache(
                LyricsCacheEntity(
                  songId = song.id,
                  songKey = songKey,
                  hasLyrics = true,
                  rawLrc = outcome.lyricsResult.rawLrc,
                  plainLyrics = outcome.lyricsResult.plain,
                  matchedTitle = outcome.lyricsResult.matchedTitle,
                  matchedArtist = outcome.lyricsResult.matchedArtist,
                  isReportedBad = false
                )
              )
            }
            _lyrics.value = outcome.lyricsResult
          }
          LyricsMatchOutcome.NoLyrics -> {
            withContext(Dispatchers.IO) {
              dao.insertLyricsCache(
                LyricsCacheEntity(
                  songId = song.id,
                  songKey = songKey,
                  hasLyrics = false,
                  isReportedBad = false
                )
              )
            }
            _lyrics.value = LyricsResult(synced = null, plain = noLyricsMsg, rawLrc = null)
          }
          LyricsMatchOutcome.NetworkError -> {
            // Do NOT cache network errors so they retry later
            _lyrics.value = LyricsResult(synced = null, plain = noLyricsMsg, rawLrc = null)
          }
        }
      } catch (_: Exception) {
        _lyrics.value = LyricsResult(synced = null, plain = noLyricsMsg, rawLrc = null)
      }
    }
  }

  private fun parseSyncedLyrics(raw: String): List<LyricLine> {
    return LrcParser.parse(raw).let { doc ->
      if (!doc.isSynced) emptyList()
      else doc.lines.map { LyricLine(it.timestampMs, it.text) }
    }
  }

  fun setThemeMode(mode: String) { viewModelScope.launch { prefsManager.setThemeMode(mode) } }
  fun setAccentColor(hex: String) { viewModelScope.launch { prefsManager.setAccentColor(hex) } }
  fun setLanguage(lang: String) { viewModelScope.launch { prefsManager.setLanguage(lang) } }
  fun setDynamicBg(enabled: Boolean) { viewModelScope.launch { prefsManager.setDynamicBg(enabled) } }
  fun setReducedMotion(enabled: Boolean) { viewModelScope.launch { prefsManager.setReducedMotion(enabled) } }
  fun setOnboardingCompleted(completed: Boolean) { viewModelScope.launch { prefsManager.setOnboardingCompleted(completed) } }
  fun setGithubOwner(owner: String) { viewModelScope.launch { prefsManager.setGithubOwner(owner) } }
  fun setGithubRepo(repo: String) { viewModelScope.launch { prefsManager.setGithubRepo(repo) } }

  override fun onCleared() {
    super.onCleared()
    mediaController?.removeListener(playerListener)
    controllerFuture?.let { MediaController.releaseFuture(it) }
  }
}
