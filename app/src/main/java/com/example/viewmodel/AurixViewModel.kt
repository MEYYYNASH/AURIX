package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.room.Room
import com.example.data.*
import com.example.model.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class AurixViewModel(application: Application) : AndroidViewModel(application) {
  private val prefsManager = PreferencesManager(application)
  private val database = Room.databaseBuilder(
    application,
    AurixDatabase::class.java,
    "aurix_db"
  ).build()
  private val dao = database.aurixDao()

  val exoPlayer = ExoPlayer.Builder(application).build()

  // Preferences States
  val themeMode = prefsManager.themeMode.stateIn(viewModelScope, SharingStarted.Lazily, "system")
  val accentColorHex = prefsManager.accentColor.stateIn(viewModelScope, SharingStarted.Lazily, "#007AFF")
  val language = prefsManager.language.stateIn(viewModelScope, SharingStarted.Lazily, "en")
  val dynamicBg = prefsManager.dynamicBg.stateIn(viewModelScope, SharingStarted.Lazily, true)
  val reducedMotion = prefsManager.reducedMotion.stateIn(viewModelScope, SharingStarted.Lazily, false)
  val onboardingCompleted = prefsManager.onboardingCompleted.stateIn(viewModelScope, SharingStarted.Lazily, false)
  val githubOwner = prefsManager.githubOwner.stateIn(viewModelScope, SharingStarted.Lazily, "MEYYYNASH")
  val githubRepo = prefsManager.githubRepo.stateIn(viewModelScope, SharingStarted.Lazily, "AURIX")

  // Search-Only Mode & YouTube Link Matcher States
  private val _searchMode = MutableStateFlow("itunes") // "itunes" | "youtube"
  val searchMode: StateFlow<String> = _searchMode

  private val _searchQuery = MutableStateFlow("")
  val searchQuery: StateFlow<String> = _searchQuery

  private val _youtubeUrl = MutableStateFlow("")
  val youtubeUrl: StateFlow<String> = _youtubeUrl

  private val _analyzedSong = MutableStateFlow<Song?>(null)
  val analyzedSong: StateFlow<Song?> = _analyzedSong

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

  // Song Download States (songId -> 0f..1f)
  private val _songDownloadProgress = MutableStateFlow<Map<String, Float>>(emptyMap())
  val songDownloadProgress: StateFlow<Map<String, Float>> = _songDownloadProgress

  private val _downloadedSongIds = MutableStateFlow<Set<String>>(emptySet())
  val downloadedSongIds: StateFlow<Set<String>> = _downloadedSongIds

  // Music States (Real iTunes Search 30-second Previews & Local Files — zero demo tracks)
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

  private val _localSongs = MutableStateFlow<List<Song>>(emptyList())

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

  val localSongs: StateFlow<List<Song>> = combine(_localSongs, favoriteIds) { tracks, favs ->
    tracks.map { it.copy(isFavorite = it.id in favs) }
  }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

  val recentlyPlayed: StateFlow<List<Song>> = combine(dao.getPlayHistory(), favoriteIds) { list, favs ->
    list.map {
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

  private var sleepTimerJob: Job? = null
  private var searchJob: Job? = null

  init {
    loadFeaturedItunes()
    setupPlayerListener()
    startPositionTicker()
    GitHubUpdater.cleanupObsoleteApks(application, _installedAppInfo.value.versionCode)
    checkForUpdates(application, silent = true)
  }

  private fun startPositionTicker() {
    viewModelScope.launch {
      while (isActive) {
        if (exoPlayer.isPlaying || exoPlayer.playbackState == Player.STATE_READY) {
          _currentPosition.value = exoPlayer.currentPosition.coerceAtLeast(0L)
          val playerDur = exoPlayer.duration
          if (playerDur > 0L) {
            _durationMs.value = playerDur
          }
        }
        delay(250)
      }
    }
  }

  private fun setupPlayerListener() {
    exoPlayer.addListener(object : Player.Listener {
      override fun onIsPlayingChanged(isPlayingVal: Boolean) {
        _isPlaying.value = isPlayingVal
      }

      override fun onPlaybackStateChanged(playbackState: Int) {
        if (playbackState == Player.STATE_ENDED) {
          if (_repeatEnabled.value) {
            exoPlayer.seekTo(0L)
            exoPlayer.play()
          } else {
            nextTrack()
          }
        }
      }
    })
  }

  fun setSearchMode(mode: String) {
    _searchMode.value = mode
  }

  fun setYoutubeUrl(url: String) {
    _youtubeUrl.value = url
    _youtubeError.value = null
  }

  fun analyzeYoutubeUrl() {
    val rawUrl = _youtubeUrl.value.trim()
    if (rawUrl.isBlank()) {
      _youtubeError.value = "Paste a YouTube link first."
      return
    }
    if (!rawUrl.contains("youtube.com", ignoreCase = true) && !rawUrl.contains("youtu.be", ignoreCase = true)) {
      _youtubeError.value = "Enter a valid YouTube URL (youtube.com or youtu.be)."
      return
    }

    viewModelScope.launch {
      _isAnalyzingYoutube.value = true
      _youtubeError.value = null
      _analyzedSong.value = null
      try {
        val info = NetworkClient.youtubeOEmbedApi.getVideoInfo(rawUrl)
        val rawTitle = info.title?.trim().orEmpty()
        val rawAuthor = info.authorName?.trim().orEmpty()
        val cleanedTitle = rawTitle
          .replace(Regex("""\((official|lyric|audio|music\s*video|mv|hd|4k).*?\)""", RegexOption.IGNORE_CASE), "")
          .replace(Regex("""\[(official|lyric|audio|music\s*video|mv|hd|4k).*?\]""", RegexOption.IGNORE_CASE), "")
          .trim()
        val searchTerm = listOf(cleanedTitle, rawAuthor)
          .filter { it.isNotBlank() }
          .joinToString(" ")
          .ifBlank { rawTitle }

        if (searchTerm.isBlank()) {
          _youtubeError.value = "Could not extract track title from link."
          return@launch
        }

        val itunesRes = NetworkClient.itunesApi.searchSongs(term = searchTerm, limit = 8)
        val matched = mapItunesTracks(itunesRes.results).firstOrNull()
        if (matched != null) {
          _analyzedSong.value = matched.copy(
            artworkUrl = matched.artworkUrl.ifBlank { info.thumbnailUrl.orEmpty() },
            source = "iTunes 30s"
          )
        } else {
          _youtubeError.value = "No 30-second iTunes preview found for \"$cleanedTitle\"."
        }
      } catch (e: Exception) {
        _youtubeError.value = "Unable to look up YouTube link. Check your network connection."
      } finally {
        _isAnalyzingYoutube.value = false
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
      Toast.makeText(context, "Saving 30s preview: ${song.title}...", Toast.LENGTH_SHORT).show()

      val result = SongDownloader.downloadSongToDevice(context, song) { progress ->
        _songDownloadProgress.value = _songDownloadProgress.value + (song.id to progress)
      }

      _songDownloadProgress.value = _songDownloadProgress.value - song.id
      result.onSuccess { savedUri ->
        _downloadedSongIds.value = _downloadedSongIds.value + song.id
        val localTrack = song.copy(
          id = savedUri.toString(),
          streamUrl = savedUri.toString(),
          source = "Downloaded"
        )
        if (_localSongs.value.none { it.title == song.title && it.artist == song.artist }) {
          _localSongs.value = listOf(localTrack) + _localSongs.value
        }
        Toast.makeText(context, "Saved to Music/AURIX: ${song.title}", Toast.LENGTH_LONG).show()
      }.onFailure { err ->
        Toast.makeText(context, err.localizedMessage ?: "Download failed", Toast.LENGTH_LONG).show()
      }
    }
  }

  private fun mapItunesTracks(results: List<ItunesTrack>): List<Song> {
    return results
      .filter { !it.previewUrl.isNullOrBlank() && !it.trackName.isNullOrBlank() }
      .distinctBy { it.trackId }
      .map { track ->
        val art = (track.artworkUrl100 ?: track.artworkUrl60)
          ?.replace("100x100bb", "600x600bb")
          ?.replace("60x60bb", "600x600bb")
          ?: ""
        Song(
          id = "itunes_${track.trackId}",
          title = track.trackName ?: "Untitled",
          artist = track.artistName ?: "Unknown Artist",
          artworkUrl = art,
          durationSeconds = 30L,
          streamUrl = track.previewUrl.orEmpty(),
          source = "iTunes 30s"
        )
      }
  }

  fun loadFeaturedItunes(term: String = "top hits") {
    viewModelScope.launch {
      _isLoadingFeatured.value = true
      _featuredError.value = null
      try {
        val res = NetworkClient.itunesApi.searchSongs(term = term, limit = 15)
        _rawFeatured.value = mapItunesTracks(res.results)
      } catch (_: Exception) {
        _rawFeatured.value = emptyList()
        _featuredError.value = "Unable to reach iTunes Search right now."
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
    _rawCurrentSong.value = song
    _queue.value = if (newQueue.any { it.id == song.id }) newQueue else listOf(song) + newQueue
    _currentPosition.value = 0L
    _durationMs.value = (song.durationSeconds * 1000L).coerceAtLeast(1000L)

    viewModelScope.launch {
      dao.insertHistory(
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
      fetchLyrics(song)
    }

    try {
      exoPlayer.stop()
      exoPlayer.clearMediaItems()
      val mediaItem = MediaItem.fromUri(Uri.parse(song.streamUrl))
      exoPlayer.setMediaItem(mediaItem)
      exoPlayer.playbackParameters = PlaybackParameters(_playbackSpeed.value)
      exoPlayer.prepare()
      exoPlayer.play()
    } catch (_: Exception) {
      Toast.makeText(getApplication(), "Unable to play this track.", Toast.LENGTH_SHORT).show()
    }
  }

  fun nextTrack() {
    val q = _queue.value
    if (q.isEmpty()) return
    val current = _rawCurrentSong.value
    val curIdx = q.indexOfFirst { it.id == current?.id }.coerceAtLeast(0)
    val nextIdx = if (_shuffleEnabled.value && q.size > 1) {
      val candidates = q.indices.filter { it != curIdx }
      candidates.random()
    } else {
      (curIdx + 1) % q.size
    }
    playSong(q[nextIdx], q)
  }

  fun previousTrack() {
    if (exoPlayer.currentPosition > 3000L) {
      exoPlayer.seekTo(0L)
      _currentPosition.value = 0L
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
    _shuffleEnabled.value = !_shuffleEnabled.value
  }

  fun toggleRepeat() {
    _repeatEnabled.value = !_repeatEnabled.value
  }

  fun cyclePlaybackSpeed() {
    val nextSpeed = when (_playbackSpeed.value) {
      1.0f -> 1.25f
      1.25f -> 1.5f
      1.5f -> 0.75f
      else -> 1.0f
    }
    _playbackSpeed.value = nextSpeed
    exoPlayer.playbackParameters = PlaybackParameters(nextSpeed)
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
        exoPlayer.pause()
        _sleepMinutes.value = 0
      }
    }
  }

  fun togglePlayPause() {
    if (exoPlayer.isPlaying) {
      exoPlayer.pause()
    } else {
      exoPlayer.play()
    }
  }

  fun seekTo(positionMs: Long) {
    exoPlayer.seekTo(positionMs)
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

    val song = Song(
      id = uri.toString(),
      title = displayTitle?.ifBlank { null } ?: fallbackName,
      artist = artist,
      artworkUrl = "",
      durationSeconds = durationSec,
      streamUrl = uri.toString(),
      source = "Local"
    )
    if (_localSongs.value.none { it.id == song.id }) {
      _localSongs.value = listOf(song) + _localSongs.value
    }
  }

  private fun fetchLyrics(song: Song) {
    _lyrics.value = null
    viewModelScope.launch {
      try {
        val lrc = NetworkClient.lrcLibApi.getLyrics(song.artist, song.title, song.durationSeconds)
        if (lrc != null && !lrc.syncedLyrics.isNullOrBlank()) {
          _lyrics.value = LyricsResult(synced = parseSyncedLyrics(lrc.syncedLyrics), plain = null)
        } else if (!lrc?.plainLyrics.isNullOrBlank()) {
          _lyrics.value = LyricsResult(synced = null, plain = lrc?.plainLyrics)
        } else {
          val fallback = NetworkClient.lrcLibApi.searchLyrics("${song.artist} ${song.title}")
          val firstSynced = fallback?.firstOrNull { !it.syncedLyrics.isNullOrBlank() }
          val firstPlain = fallback?.firstOrNull { !it.plainLyrics.isNullOrBlank() }
          if (firstSynced?.syncedLyrics != null) {
            _lyrics.value = LyricsResult(synced = parseSyncedLyrics(firstSynced.syncedLyrics), plain = null)
          } else {
            _lyrics.value = LyricsResult(synced = null, plain = firstPlain?.plainLyrics ?: "No lyrics found for this song.")
          }
        }
      } catch (_: Exception) {
        _lyrics.value = LyricsResult(synced = null, plain = "No lyrics found for this song.")
      }
    }
  }

  private fun parseSyncedLyrics(raw: String): List<LyricLine> {
    val lines = raw.lines()
    val result = mutableListOf<LyricLine>()
    val regex = Regex("""\[(\d{2}):(\d{2})\.(\d{2,3})\](.*)""")
    for (line in lines) {
      val match = regex.find(line)
      if (match != null) {
        val min = match.groupValues[1].toLong()
        val sec = match.groupValues[2].toLong()
        val millisPart = match.groupValues[3]
        val ms = millisPart.toLong() * if (millisPart.length == 2) 10 else 1
        val totalMs = (min * 60 + sec) * 1000 + ms
        val text = match.groupValues[4].trim()
        result.add(LyricLine(totalMs, text))
      }
    }
    return result.sortedBy { it.timestampMs }
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
    exoPlayer.release()
  }
}
