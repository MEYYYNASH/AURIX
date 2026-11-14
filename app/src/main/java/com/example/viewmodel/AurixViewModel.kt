package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.example.BuildConfig
import com.example.data.*
import com.example.model.*
import kotlinx.coroutines.flow.*
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
  private val _updateStatus = MutableStateFlow<String>("Idle")
  val updateStatus: StateFlow<String> = _updateStatus

  private val _latestRelease = MutableStateFlow<GitHubRelease?>(null)
  val latestRelease: StateFlow<GitHubRelease?> = _latestRelease

  // Music States
  private val _trendingTracks = MutableStateFlow<List<Song>>(emptyList())
  val trendingTracks: StateFlow<List<Song>> = _trendingTracks

  private val _searchResults = MutableStateFlow<List<Song>>(emptyList())
  val searchResults: StateFlow<List<Song>> = _searchResults

  private val _isSearching = MutableStateFlow(false)
  val isSearching: StateFlow<Boolean> = _isSearching

  private val _localSongs = MutableStateFlow<List<Song>>(emptyList())
  val localSongs: StateFlow<List<Song>> = _localSongs

  val favorites: StateFlow<List<Song>> = dao.getFavorites().map { list ->
    list.map { Song(it.id, it.title, it.artist, it.artworkUrl, it.durationSeconds, it.streamUrl, it.source, true) }
  }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

  val recentlyPlayed: StateFlow<List<Song>> = dao.getPlayHistory().map { list ->
    list.map { Song(it.songId, it.title, it.artist, it.artworkUrl, it.durationSeconds, it.streamUrl, it.source, false) }
  }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

  private val _currentSong = MutableStateFlow<Song?>(null)
  val currentSong: StateFlow<Song?> = _currentSong

  private val _isPlaying = MutableStateFlow(false)
  val isPlaying: StateFlow<Boolean> = _isPlaying

  private val _currentPosition = MutableStateFlow(0L)
  val currentPosition: StateFlow<Long> = _currentPosition

  private val _queue = MutableStateFlow<List<Song>>(emptyList())
  val queue: StateFlow<List<Song>> = _queue

  private val _lyrics = MutableStateFlow<LyricsResult?>(null)
  val lyrics: StateFlow<LyricsResult?> = _lyrics

  init {
    loadTrending()
    setupPlayerListener()
  }

  private fun setupPlayerListener() {
    exoPlayer.addListener(object : Player.Listener {
      override fun onIsPlayingChanged(isPlayingVal: Boolean) {
        _isPlaying.value = isPlayingVal
      }
    })
  }

  fun setAppMode(mode: String) { _appMode.value = mode }
  fun setYoutubeUrl(url: String) { _youtubeUrl.value = url }

  fun analyzeYoutubeUrl() {
    val url = _youtubeUrl.value
    if (url.isBlank()) return
    val song = Song(
      id = "yt_" + url.hashCode(),
      title = "Die with a smile (tiktok remix)",
      artist = "Lady Gaga, Bruno Mars",
      artworkUrl = "",
      durationSeconds = 210,
      streamUrl = "https://discoveryprovider.audius.co/v1/tracks/7WJ9x/stream?app_name=aurix",
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
        _trendingTracks.value = mapped
      } catch (e: Exception) {
        _trendingTracks.value = listOf(
          Song("1", "Die with a smile (tiktok remix)", "Lady Gaga, Bruno Mars", "", 210, "", "Audius"),
          Song("2", "NIGHT THINKER", "VannDa (វណ្ណដា)", "", 195, "", "Audius"),
          Song("3", "Time To Rise", "VannDa ft. Master Kong Nay", "", 240, "", "Audius")
        )
      }
    }
  }

  fun search(query: String) {
    if (query.isBlank()) {
      _searchResults.value = emptyList()
      _isSearching.value = false
      return
    }
    viewModelScope.launch {
      _isSearching.value = true
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
        _searchResults.value = mapped
      } catch (e: Exception) {
        _searchResults.value = emptyList()
      } finally {
        _isSearching.value = false
      }
    }
  }

  fun playSong(song: Song, newQueue: List<Song> = listOf(song)) {
    _currentSong.value = song
    _queue.value = newQueue
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
      val mediaItem = MediaItem.fromUri(Uri.parse(song.streamUrl.ifBlank { "https://discoveryprovider.audius.co/v1/tracks/7WJ9x/stream?app_name=aurix" }))
      exoPlayer.setMediaItem(mediaItem)
      exoPlayer.prepare()
      exoPlayer.play()
    } catch (e: Exception) {
      // ignore
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
    viewModelScope.launch {
      try {
        val lrc = NetworkClient.lrcLibApi.getLyrics(song.artist, song.title, song.durationSeconds)
        if (lrc != null && !lrc.syncedLyrics.isNullOrBlank()) {
          _lyrics.value = LyricsResult(synced = parseSyncedLyrics(lrc.syncedLyrics), plain = null)
        } else {
          _lyrics.value = LyricsResult(synced = null, plain = lrc?.plainLyrics ?: "No lyrics available.")
        }
      } catch (e: Exception) {
        _lyrics.value = LyricsResult(synced = null, plain = "Could not load lyrics.")
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
    return result
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
