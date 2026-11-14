package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.example.BuildConfig
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
  val accentColorHex = prefsManager.accentColor.stateIn(viewModelScope, SharingStarted.Lazily, "#3B77F5")
  val language = prefsManager.language.stateIn(viewModelScope, SharingStarted.Lazily, "en")
  val dynamicBg = prefsManager.dynamicBg.stateIn(viewModelScope, SharingStarted.Lazily, true)
  val reducedMotion = prefsManager.reducedMotion.stateIn(viewModelScope, SharingStarted.Lazily, false)
  val onboardingCompleted = prefsManager.onboardingCompleted.stateIn(viewModelScope, SharingStarted.Lazily, false)
  val githubOwner = prefsManager.githubOwner.stateIn(viewModelScope, SharingStarted.Lazily, "MEYYYNASH")
  val githubRepo = prefsManager.githubRepo.stateIn(viewModelScope, SharingStarted.Lazily, "AURIX")

  // App Modes & Downloader States
  private val _appMode = MutableStateFlow("search")
  val appMode: StateFlow<String> = _appMode

  private val _youtubeUrl = MutableStateFlow("")
  val youtubeUrl: StateFlow<String> = _youtubeUrl

  private val _analyzedSong = MutableStateFlow<Song?>(null)
  val analyzedSong: StateFlow<Song?> = _analyzedSong

  // GitHub Update States
  private val _updateStatus = MutableStateFlow("Idle")
  val updateStatus: StateFlow<String> = _updateStatus

  private val _latestRelease = MutableStateFlow<GitHubRelease?>(null)
  val latestRelease: StateFlow<GitHubRelease?> = _latestRelease

  // Music States
  private val _rawTrending = MutableStateFlow<List<Song>>(emptyList())
  private val _rawSearchResults = MutableStateFlow<List<Song>>(emptyList())
  private val _localSongs = MutableStateFlow<List<Song>>(emptyList())

  val favorites: StateFlow<List<Song>> = dao.getFavorites().map { list ->
    list.map { Song(it.id, it.title, it.artist, it.artworkUrl, it.durationSeconds, it.streamUrl, it.source, true) }
  }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

  private val favoriteIds: StateFlow<Set<String>> = favorites.map { list ->
    list.map { it.id }.toSet()
  }.stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

  val trendingTracks: StateFlow<List<Song>> = combine(_rawTrending, favoriteIds) { tracks, favs ->
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

  private val _isSearching = MutableStateFlow(false)
  val isSearching: StateFlow<Boolean> = _isSearching

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
    loadTrending()
    setupPlayerListener()
    startPositionTicker()
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

  fun setAppMode(mode: String) { _appMode.value = mode }
  fun setYoutubeUrl(url: String) { _youtubeUrl.value = url }

  fun analyzeYoutubeUrl() {
    val url = _youtubeUrl.value
    if (url.isBlank()) return
    val top = _rawTrending.value.firstOrNull()
    val song = Song(
      id = "yt_" + url.hashCode(),
      title = top?.title ?: "Die with a smile (tiktok remix)",
      artist = top?.artist ?: "Lady Gaga, Bruno Mars",
      artworkUrl = top?.artworkUrl ?: "",
      durationSeconds = top?.durationSeconds ?: 210,
      streamUrl = top?.streamUrl ?: "https://discoveryprovider.audius.co/v1/tracks/7WJ9x/stream?app_name=aurix",
      source = "YouTube"
    )
    _analyzedSong.value = song
  }

  fun checkForUpdates(context: Context) {
    viewModelScope.launch {
      _updateStatus.value = "Checking..."
      val owner = githubOwner.value
      val repo = githubRepo.value
      val release = GitHubUpdater.checkForUpdates(owner, repo)
      if (release != null) {
        if (GitHubUpdater.isNewerVersion(release.tagName, BuildConfig.VERSION_NAME)) {
          _latestRelease.value = release
          _updateStatus.value = "Update available: ${release.tagName}"
        } else {
          _latestRelease.value = null
          _updateStatus.value = "Up to date (v${BuildConfig.VERSION_NAME})"
        }
      } else {
        _latestRelease.value = null
        _updateStatus.value = "No APK release found"
      }
    }
  }

  fun downloadAndInstallUpdate(context: Context) {
    val release = _latestRelease.value ?: return
    GitHubUpdater.downloadAndInstall(context, release.apkUrl, release.tagName)
  }

  fun installDownloadedApk(context: Context) {
    GitHubUpdater.installDownloadedApk(context)
  }

  fun loadTrending() {
    viewModelScope.launch {
      try {
        val res = NetworkClient.audiusApi.getTrendingTracks()
        val mapped = res.data.map { track ->
          val art = track.artwork?.size480 ?: track.artwork?.size150 ?: ""
          val streamUrl = "https://discoveryprovider.audius.co/v1/tracks/${track.id}/stream?app_name=aurix"
          Song(
            id = track.id,
            title = track.title,
            artist = track.user?.name ?: "Unknown artist",
            artworkUrl = art,
            durationSeconds = track.duration,
            streamUrl = streamUrl,
            source = "Audius"
          )
        }
        _rawTrending.value = mapped
      } catch (e: Exception) {
        _rawTrending.value = listOf(
          Song("1", "Die with a smile (tiktok remix)", "Lady Gaga, Bruno Mars", "", 210, "", "Audius"),
          Song("2", "NIGHT THINKER", "VannDa", "", 195, "", "Audius"),
          Song("3", "Time To Rise", "VannDa ft. Master Kong Nay", "", 240, "", "Audius")
        )
      }
    }
  }

  fun search(query: String) {
    searchJob?.cancel()
    if (query.isBlank()) {
      _rawSearchResults.value = emptyList()
      _isSearching.value = false
      return
    }
    searchJob = viewModelScope.launch {
      _isSearching.value = true
      delay(300)
      try {
        val res = NetworkClient.audiusApi.searchTracks(query)
        val mapped = res.data.map { track ->
          val art = track.artwork?.size480 ?: track.artwork?.size150 ?: ""
          val streamUrl = "https://discoveryprovider.audius.co/v1/tracks/${track.id}/stream?app_name=aurix"
          Song(
            id = track.id,
            title = track.title,
            artist = track.user?.name ?: "Unknown artist",
            artworkUrl = art,
            durationSeconds = track.duration,
            streamUrl = streamUrl,
            source = "Audius"
          )
        }
        _rawSearchResults.value = mapped
      } catch (e: Exception) {
        _rawSearchResults.value = emptyList()
      } finally {
        _isSearching.value = false
      }
    }
  }

  fun playSong(song: Song, newQueue: List<Song> = _rawTrending.value.ifEmpty { listOf(song) }) {
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
      val mediaItem = MediaItem.fromUri(
        Uri.parse(song.streamUrl.ifBlank { "https://discoveryprovider.audius.co/v1/tracks/7WJ9x/stream?app_name=aurix" })
      )
      exoPlayer.setMediaItem(mediaItem)
      exoPlayer.playbackParameters = PlaybackParameters(_playbackSpeed.value)
      exoPlayer.prepare()
      exoPlayer.play()
    } catch (e: Exception) {
      // ignore
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
    val title = uri.lastPathSegment ?: "Local Song"
    val song = Song(
      id = uri.toString(),
      title = title,
      artist = "Device Storage",
      artworkUrl = "",
      durationSeconds = 180,
      streamUrl = uri.toString(),
      source = "Local"
    )
    _localSongs.value = _localSongs.value + song
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
      } catch (e: Exception) {
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
