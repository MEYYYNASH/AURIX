package com.example.viewmodel

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.SystemClock
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.room.Room
import com.example.BuildConfig
import com.example.LocaleHelper
import com.example.R
import com.example.data.*
import com.example.lyrics.LrcParser
import com.example.lyrics.LyricsMatchOutcome
import com.example.lyrics.LyricsMatcher
import com.example.model.*
import com.example.playback.AudioEqualizerController
import com.example.playback.CrossfadeController
import com.example.playback.CrossfadeSettings
import com.example.playback.EqPreset
import com.example.playback.EqualizerUiState
import com.example.playback.PlaybackService
import com.example.recommendation.*
import com.example.ui.screens.UpdateLogoState
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.coroutineScope
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
  TIKTOK_VIRAL("tiktok", "Today's Hits", "Today's Hits", "Hot chart hits & previews from iTunes"),
  DAILY_CHARTS("charts", "Daily Top", "Daily Chart", "Global top songs from iTunes & YouTube"),
  PHONK_EDITS("phonk", "Phonk & Edits", "Viral Edits", "Phonk & montage tracks from YouTube"),
  KHMER_ASIA("khmer", "Khmer & Asia", "Regional Hits", "Popular Khmer & Asian hits from iTunes & YouTube")
}

data class NotificationNavTarget(
  val screenRoute: String,
  val libraryTab: Int = 0
)

class AurixViewModel(application: Application) : AndroidViewModel(application) {
  private val prefsManager = PreferencesManager(application)
  private val database = AurixDatabase.getInstance(application)
  private val dao = database.aurixDao()
  private val analyticsRepository = ListeningAnalyticsRepository(dao)
  private val playSessionTracker = PlaySessionTracker()

  private var controllerFuture: ListenableFuture<MediaController>? = null
  private var mediaController: MediaController? = null
  private var pendingPlayRequest: Pair<Song, List<Song>>? = null

  // Preferences States
  val themeMode = prefsManager.themeMode.stateIn(viewModelScope, SharingStarted.Lazily, "system")
  val accentColorHex = prefsManager.accentColor.stateIn(viewModelScope, SharingStarted.Lazily, "#007AFF")
  val language = prefsManager.language.stateIn(
    viewModelScope,
    SharingStarted.Eagerly,
    LocaleHelper.getPersistedLanguage(application)
  )
  val dynamicBg = prefsManager.dynamicBg.stateIn(viewModelScope, SharingStarted.Lazily, true)
  val reducedMotion = prefsManager.reducedMotion.stateIn(viewModelScope, SharingStarted.Lazily, false)
  val onboardingCompleted = prefsManager.onboardingCompleted.stateIn(viewModelScope, SharingStarted.Eagerly, false)
  val githubOwner = prefsManager.githubOwner.stateIn(viewModelScope, SharingStarted.Lazily, "MEYYYNASH")
  val githubRepo = prefsManager.githubRepo.stateIn(viewModelScope, SharingStarted.Lazily, "AURIX")
  val moodFlowEnabled: StateFlow<Boolean> = prefsManager.moodFlowEnabled
    .stateIn(viewModelScope, SharingStarted.Eagerly, true)
  val navStyle: StateFlow<String> = prefsManager.navStyle
    .stateIn(viewModelScope, SharingStarted.Eagerly, "classic")

  // Search-Only Mode & YouTube Analyze/Download States
  private val _searchMode = MutableStateFlow("youtube_search") // "youtube_search" | "youtube"
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

  private val _resolvingSongId = MutableStateFlow<String?>(null)
  val resolvingSongId: StateFlow<String?> = _resolvingSongId

  private val _formatSheetState = MutableStateFlow<YoutubeFormatSheetState>(YoutubeFormatSheetState.Closed)
  val formatSheetState: StateFlow<YoutubeFormatSheetState> = _formatSheetState

  private val youtubeExtractCache = ConcurrentHashMap<String, Pair<Long, ExtractedYoutubeData>>()
  private val retriedPlaybackIds = ConcurrentHashMap.newKeySet<String>()
  private var playResolveJob: Job? = null
  private var formatSheetJob: Job? = null

  // OTA Update Engine (shared app-wide instance so BackgroundDownloadService stays synced)
  private val updateManager = UpdateManagerHolder.getInstance(application)
  val updateUiState: StateFlow<UpdateUiState> = updateManager.uiState

  private val _pendingNotificationNav = MutableStateFlow<NotificationNavTarget?>(null)
  val pendingNotificationNav: StateFlow<NotificationNavTarget?> = _pendingNotificationNav.asStateFlow()

  private val _logoIntroActive = MutableStateFlow(false)
  private var logoIntroPlayed = false
  private var logoTransitionJob: Job? = null

  val logoState: StateFlow<UpdateLogoState> = combine(
    _logoIntroActive,
    updateManager.uiState
  ) { introActive, state ->
    when (state.stage) {
      UpdateStage.Checking -> UpdateLogoState.Checking
      UpdateStage.Available -> UpdateLogoState.Available
      UpdateStage.Downloading -> if (state.isPaused) UpdateLogoState.Available else UpdateLogoState.Downloading
      UpdateStage.Verifying, UpdateStage.Installing -> UpdateLogoState.Installing
      UpdateStage.Done -> UpdateLogoState.Done
      UpdateStage.Error -> UpdateLogoState.Error
      UpdateStage.Idle, UpdateStage.UpToDate -> if (introActive) UpdateLogoState.Intro else UpdateLogoState.Idle
    }
  }.stateIn(viewModelScope, SharingStarted.Eagerly, UpdateLogoState.Idle)

  private val _showWhatsNewSheet = MutableStateFlow(false)
  val showWhatsNewSheet: StateFlow<Boolean> = _showWhatsNewSheet.asStateFlow()

  // Song Download States (mirrored from BackgroundDownloadRegistry so background transfers stay live)
  val songDownloadProgress: StateFlow<Map<String, Float>> = BackgroundDownloadRegistry.songDownloadProgress

  private val _localDeletedOrAddedIds = MutableStateFlow<Set<String>>(emptySet())
  val downloadedSongIds: StateFlow<Set<String>> = combine(
    BackgroundDownloadRegistry.downloadedSongIds,
    _localDeletedOrAddedIds
  ) { registryIds, extraIds ->
    registryIds + extraIds
  }.stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

  // Daily Chart & Viral Recommendations (auto-updates daily, TikTok Viral powered by iTunes)
  private val _selectedDailyFeed = MutableStateFlow(DailyFeedCategory.TIKTOK_VIRAL)
  val selectedDailyFeed: StateFlow<DailyFeedCategory> = _selectedDailyFeed

  private val _dailyUpdatedLabel = MutableStateFlow(formatTodayBadge())
  val dailyUpdatedLabel: StateFlow<String> = _dailyUpdatedLabel

  private val _dailyUpdatedJustNow = MutableStateFlow(false)
  val dailyUpdatedJustNow: StateFlow<Boolean> = _dailyUpdatedJustNow

  private val dailyFeedCache = mutableMapOf<Pair<String, DailyFeedCategory>, List<Song>>()
  private var lastFeedDateKey: String = todayDateKey()
  private var feedJob: Job? = null

  private val _rawFeatured = MutableStateFlow<List<Song>>(emptyList())
  private val _isLoadingFeatured = MutableStateFlow(false)
  val isLoadingFeatured: StateFlow<Boolean> = _isLoadingFeatured

  private val _featuredError = MutableStateFlow<String?>(null)
  val featuredError: StateFlow<String?> = _featuredError

  // Popular Songs from YouTube ("Song List" on Home)
  private val _rawPopularYoutube = MutableStateFlow<List<Song>>(emptyList())
  private val _isLoadingPopularYoutube = MutableStateFlow(false)
  val isLoadingPopularYoutube: StateFlow<Boolean> = _isLoadingPopularYoutube.asStateFlow()

  // TikTok Viral Chart Songs fetched via NewPipeExtractor ("TikTok Viral" on Home)
  private val _rawTiktokViralNewPipe = MutableStateFlow<List<Song>>(emptyList())
  private val _isLoadingTiktokViralNewPipe = MutableStateFlow(false)
  val isLoadingTiktokViralNewPipe: StateFlow<Boolean> = _isLoadingTiktokViralNewPipe.asStateFlow()

  // New Albums from Apple Charts / iTunes ("New Albums" on Home)
  private val _rawNewAlbums = MutableStateFlow<List<FeaturedAlbumItem>>(emptyList())
  private val _isLoadingNewAlbums = MutableStateFlow(false)
  val isLoadingNewAlbums: StateFlow<Boolean> = _isLoadingNewAlbums.asStateFlow()

  private val _remoteAlbumInfo = MutableStateFlow<AlbumInfo?>(null)
  private val _isShowingRemoteAlbum = MutableStateFlow(false)
  private var remoteAlbumJob: Job? = null

  private val _rawSearchResults = MutableStateFlow<List<Song>>(emptyList())
  private val _isSearching = MutableStateFlow(false)
  val isSearching: StateFlow<Boolean> = _isSearching

  private val _searchError = MutableStateFlow<String?>(null)
  val searchError: StateFlow<String?> = _searchError

  private data class SearchDispatch(
    val query: String,
    val mode: SearchMode,
    val debounceMs: Long,
    val nonce: Long
  )

  private val _searchModeEnum = MutableStateFlow(SearchMode.YouTubeSearch)
  private val _searchDispatch = MutableSharedFlow<SearchDispatch>(
    extraBufferCapacity = 16,
    onBufferOverflow = BufferOverflow.DROP_OLDEST
  )
  private var searchDispatchNonce = 0L
  private val _searchUiState = MutableStateFlow<SearchUiState>(SearchUiState.Idle)

  val hiddenSongIds: StateFlow<Set<String>> = prefsManager.hiddenSongIds
    .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())
  val blockedArtists: StateFlow<Set<String>> = prefsManager.blockedArtists
    .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

  private fun normalizeArtistKey(artist: String): String {
    return artist.trim().lowercase(Locale.ROOT)
  }

  private fun isTrackVisible(
    song: Song,
    hiddenIds: Set<String>,
    blocked: Set<String>
  ): Boolean {
    return song.id !in hiddenIds &&
      normalizeArtistKey(song.artist) !in blocked
  }

  private val albumCoverEntities: StateFlow<Map<String, AlbumCoverEntity>> = dao.observeAlbumCovers()
    .map { list -> list.associateBy { it.albumId } }
    .catch { emit(emptyMap()) }
    .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

  private val customAlbumEntities: StateFlow<List<CustomAlbumEntity>> = dao.observeCustomAlbums()
    .catch { emit(emptyList()) }
    .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

  private val customAlbumTrackEntities: StateFlow<List<AlbumTrackEntity>> = dao.observeAllAlbumTracks()
    .catch { emit(emptyList()) }
    .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

  private val deletedAlbumIds: StateFlow<Set<String>> = dao.observeDeletedAlbumIds()
    .map { it.toSet() }
    .catch { emit(emptySet()) }
    .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

  private val _selectedAlbumId = MutableStateFlow<String?>(null)

  private fun effectiveArtworkForSong(song: Song, covers: Map<String, AlbumCoverEntity>): Song {
    val albumName = song.album.ifBlank { "Unknown Album" }
    val artistName = song.artist.ifBlank { "Unknown Artist" }
    val albumId = computeAlbumId(albumName, artistName)
    val custom = covers[albumId]
    if (custom != null && custom.coverPath.isNotBlank()) {
      return song.copy(artworkUrl = custom.coverPath)
    }
    if (song.id.startsWith("album_track_")) {
      val placeholderAlbumId = computeAlbumId("Album Title", "Artist Name")
      val placeholderCustom = covers[placeholderAlbumId]
      if (placeholderCustom != null && placeholderCustom.coverPath.isNotBlank()) {
        return song.copy(artworkUrl = placeholderCustom.coverPath)
      }
    }
    return song
  }

  private val rawFavorites: StateFlow<List<Song>> = combine(
    dao.getFavorites().catch { emit(emptyList()) },
    albumCoverEntities
  ) { list, covers ->
    list.map {
      val base = Song(
        id = it.id,
        title = it.title,
        artist = it.artist,
        artworkUrl = it.artworkUrl,
        durationSeconds = it.durationSeconds,
        streamUrl = it.streamUrl,
        source = it.source,
        isFavorite = true
      )
      TrackMetadataClassifier.enrich(effectiveArtworkForSong(base, covers))
    }
  }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

  private val favoriteIds: StateFlow<Set<String>> = rawFavorites.map { list ->
    list.map { it.id }.toSet()
  }.stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

  val favorites: StateFlow<List<Song>> = combine(
    rawFavorites,
    hiddenSongIds,
    blockedArtists
  ) { list, hidden, blocked ->
    list.filter { isTrackVisible(it, hidden, blocked) }
  }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

  val featuredTracks: StateFlow<List<Song>> = combine(
    _rawFeatured,
    favoriteIds,
    hiddenSongIds,
    blockedArtists
  ) { tracks, favs, hidden, blocked ->
    tracks
      .filter { isTrackVisible(it, hidden, blocked) }
      .map { it.copy(isFavorite = it.id in favs) }
  }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

  val searchResults: StateFlow<List<Song>> = combine(
    _rawSearchResults,
    favoriteIds,
    hiddenSongIds,
    blockedArtists
  ) { tracks, favs, hidden, blocked ->
    tracks
      .filter { isTrackVisible(it, hidden, blocked) }
      .map { it.copy(isFavorite = it.id in favs) }
  }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

  val searchUiState: StateFlow<SearchUiState> = combine(
    _searchUiState,
    favoriteIds,
    hiddenSongIds,
    blockedArtists
  ) { state, favs, hidden, blocked ->
    if (state is SearchUiState.Results) {
      val visible = state.items
        .filter { isTrackVisible(it, hidden, blocked) }
        .map { it.copy(isFavorite = it.id in favs) }
      if (visible.isEmpty()) SearchUiState.Empty else SearchUiState.Results(visible)
    } else {
      state
    }
  }.stateIn(viewModelScope, SharingStarted.Eagerly, SearchUiState.Idle)

  private val rawLocalSongs: StateFlow<List<Song>> = combine(
    dao.getLocalSongs().catch { emit(emptyList()) },
    favoriteIds,
    hiddenSongIds,
    blockedArtists
  ) { list, favs, hidden, blocked ->
    list.map {
      TrackMetadataClassifier.enrich(
        Song(
          id = it.id,
          title = it.title,
          artist = it.artist.ifBlank { "Unknown Artist" },
          artworkUrl = if (it.artworkUrl == APP_ICON_ARTWORK) "" else it.artworkUrl,
          durationSeconds = it.durationSeconds,
          streamUrl = it.streamUrl,
          source = it.source,
          isFavorite = it.id in favs,
          album = it.album.ifBlank { "Unknown Album" },
          trackNumber = it.trackNumber,
          year = it.year
        )
      )
    }.filter { isTrackVisible(it, hidden, blocked) }
  }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

  val localSongs: StateFlow<List<Song>> = combine(
    rawLocalSongs,
    albumCoverEntities
  ) { list, covers ->
    list.map { effectiveArtworkForSong(it, covers) }
  }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

  // Qualified play logs (only plays >= 30 seconds of active playback) filtered by visibility
  val qualifiedPlayLogs: StateFlow<List<PlayLogRecord>> = combine(
    analyticsRepository.observeQualifiedPlayLogs().catch { emit(emptyList()) },
    favoriteIds,
    hiddenSongIds,
    blockedArtists,
    albumCoverEntities
  ) { logs, favs, hidden, blocked, covers ->
    logs
      .filter { isTrackVisible(it.song, hidden, blocked) }
      .map { record ->
        val updatedSong = effectiveArtworkForSong(record.song, covers).copy(
          isFavorite = record.song.id in favs
        )
        record.copy(song = updatedSong)
      }
  }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

  // Last 20 unique songs, newest first, sourced strictly from qualified 30s+ play logs
  val recentlyPlayed: StateFlow<List<Song>> = qualifiedPlayLogs.map { logs ->
    RecommendationEngine.recentlyPlayed(logs, limit = 20)
  }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

  private val rawDiscoverWeekly: StateFlow<DiscoverWeeklyPlaylist?> =
    analyticsRepository.observeDiscoverWeekly()
      .catch { emit(null) }
      .stateIn(viewModelScope, SharingStarted.Eagerly, null)

  val discoverWeekly: StateFlow<DiscoverWeeklyPlaylist?> = combine(
    rawDiscoverWeekly,
    favoriteIds,
    hiddenSongIds,
    blockedArtists
  ) { playlist, favs, hidden, blocked ->
    playlist?.let { dw ->
      val visible = dw.tracks
        .filter { isTrackVisible(it, hidden, blocked) }
        .map { it.copy(isFavorite = it.id in favs) }
      if (visible.isEmpty()) null else dw.copy(tracks = visible)
    }
  }.stateIn(viewModelScope, SharingStarted.Lazily, null)

  val popularYoutubeSongs: StateFlow<List<Song>> = combine(
    _rawPopularYoutube,
    favoriteIds,
    hiddenSongIds,
    blockedArtists
  ) { tracks, favs, hidden, blocked ->
    tracks
      .filter { isTrackVisible(it, hidden, blocked) }
      .map { it.copy(isFavorite = it.id in favs) }
  }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

  val tiktokViralNewPipeSongs: StateFlow<List<Song>> = combine(
    _rawTiktokViralNewPipe,
    favoriteIds,
    hiddenSongIds,
    blockedArtists
  ) { tracks, favs, hidden, blocked ->
    tracks
      .filter { isTrackVisible(it, hidden, blocked) }
      .map { it.copy(isFavorite = it.id in favs) }
  }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

  private val storedCatalogSongs: StateFlow<List<Song>> = analyticsRepository.observeStoredCatalogSongs()
    .catch { emit(emptyList()) }
    .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

  val richPlayHistory: StateFlow<List<RichPlayRecord>> = combine(
    analyticsRepository.observeRichPlayHistory().catch { emit(emptyList()) },
    favoriteIds,
    hiddenSongIds,
    blockedArtists
  ) { logs, favs, hidden, blocked ->
    logs
      .filter { it.song.id !in hidden && normalizeArtistKey(it.song.artist) !in blocked }
      .map { rec -> rec.copy(song = rec.song.copy(isFavorite = rec.song.id in favs)) }
  }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

  private val catalogTracks: StateFlow<List<Song>> = combine(
    featuredTracks,
    popularYoutubeSongs,
    tiktokViralNewPipeSongs,
    localSongs,
    combine(favorites, searchResults, storedCatalogSongs) { favs, search, stored -> favs + search + stored }
  ) { featured, popularYt, tiktokCharts, local, extra ->
    (popularYt + tiktokCharts + featured + local + extra)
      .distinctBy { it.id }
      .map { TrackMetadataClassifier.enrich(it) }
  }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

  private val _moodFlowCurrentPick = MutableStateFlow<MoodFlowPick?>(null)
  val moodFlowCurrentPick: StateFlow<MoodFlowPick?> = _moodFlowCurrentPick.asStateFlow()

  val smartRecommendations: StateFlow<List<RecommendedTrack>> = combine(
    qualifiedPlayLogs,
    catalogTracks
  ) { logs, catalog ->
    RecommendationEngine.smartRecommendations(logs, catalog, limit = 12)
  }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

  private val _mostPlayedFilter = MutableStateFlow(MostPlayedFilter.WEEK)
  val mostPlayedFilter: StateFlow<MostPlayedFilter> = _mostPlayedFilter

  val mostPlayed: StateFlow<List<RankedTrack>> = combine(
    qualifiedPlayLogs,
    _mostPlayedFilter
  ) { logs, filter ->
    RecommendationEngine.mostPlayed(logs, filter, limit = 25)
  }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

  val moodPlaylists: StateFlow<List<MoodPlaylist>> = catalogTracks.map { catalog ->
    RecommendationEngine.buildMoodPlaylists(catalog)
  }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

  val musicDna: StateFlow<MusicDnaProfile> = qualifiedPlayLogs.map { logs ->
    RecommendationEngine.buildMusicDna(logs)
  }.stateIn(
    viewModelScope,
    SharingStarted.Lazily,
    RecommendationEngine.buildMusicDna(emptyList())
  )

  val trendingTracks: StateFlow<List<RankedTrack>> = qualifiedPlayLogs.map { logs ->
    RecommendationEngine.trendingTracks(logs, limit = 15)
  }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

  val risingArtists: StateFlow<List<RisingArtist>> = qualifiedPlayLogs.map { logs ->
    RecommendationEngine.risingArtists(logs, limit = 10)
  }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

  private val songFeedback: StateFlow<Map<String, Boolean>> = analyticsRepository.observeSongFeedback()
    .catch { emit(emptyMap()) }
    .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

  private val _mixRefreshSalt = MutableStateFlow(0)

  val artistMixes: StateFlow<List<ArtistMix>> = combine(
    qualifiedPlayLogs,
    richPlayHistory,
    favorites,
    songFeedback,
    catalogTracks
  ) { logs, rich, favs, fb, catalog ->
    RecommendationEngine.buildArtistMixes(
      playLogs = logs,
      richHistory = rich,
      favorites = favs,
      feedback = fb,
      catalog = catalog,
      limit = 6
    )
  }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

  val myMixes: StateFlow<List<PersonalMix>> = combine(
    qualifiedPlayLogs,
    richPlayHistory,
    favorites,
    songFeedback,
    combine(catalogTracks, _mixRefreshSalt) { catalog, salt -> catalog to salt }
  ) { logs, rich, favs, fb, (catalog, salt) ->
    RecommendationEngine.buildMyMixes(
      playLogs = logs,
      richHistory = rich,
      favorites = favs,
      feedback = fb,
      catalog = catalog,
      refreshSalt = salt,
      maxMixes = 4
    )
  }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

  val dailyDiscovery: StateFlow<PersonalMix?> = combine(
    qualifiedPlayLogs,
    richPlayHistory,
    favorites,
    songFeedback,
    combine(catalogTracks, _mixRefreshSalt) { catalog, salt -> catalog to salt }
  ) { logs, rich, favs, fb, (catalog, salt) ->
    RecommendationEngine.buildDailyDiscovery(
      playLogs = logs,
      richHistory = rich,
      favorites = favs,
      feedback = fb,
      catalog = catalog,
      refreshSalt = salt,
      limit = 20
    )
  }.stateIn(viewModelScope, SharingStarted.Lazily, null)

  val favoritesMix: StateFlow<PersonalMix?> = combine(
    favorites,
    catalogTracks,
    songFeedback
  ) { favs, catalog, fb ->
    RecommendationEngine.buildFavoritesMix(
      favorites = favs,
      catalog = catalog,
      feedback = fb,
      limit = 24
    )
  }.stateIn(viewModelScope, SharingStarted.Lazily, null)

  val recentPlayEntries: StateFlow<List<RecentPlayEntry>> = combine(
    qualifiedPlayLogs,
    richPlayHistory
  ) { logs, rich ->
    RecommendationEngine.buildRecentPlayEntries(
      playLogs = logs,
      richHistory = rich,
      limit = 12
    )
  }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

  val recentlyAddedSongs: StateFlow<List<Song>> = localSongs.map { list ->
    list.take(16)
  }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

  private fun buildDefaultExampleAlbum(
    featured: List<Song>,
    favs: Set<String>,
    hidden: Set<String>,
    covers: Map<String, AlbumCoverEntity>
  ): AlbumInfo {
    val defaultDurations = listOf(192L, 218L, 174L, 245L, 203L)
    val baseTracks = (0 until 5).mapNotNull { idx ->
      val id = "album_track_${idx + 1}"
      if (id in hidden) return@mapNotNull null
      val backing = featured.getOrNull(idx)
      val duration = backing?.fullDurationSeconds?.takeIf { it > 0L }
        ?: backing?.durationSeconds?.takeIf { it > 0L }
        ?: defaultDurations[idx]
      Song(
        id = id,
        title = "Track Title ${idx + 1}",
        artist = "Artist Name",
        artworkUrl = "",
        durationSeconds = duration,
        streamUrl = backing?.streamUrl.orEmpty(),
        source = backing?.source ?: "Album",
        isFavorite = id in favs,
        fullDurationSeconds = duration,
        album = "Album Title",
        trackNumber = idx + 1,
        year = ""
      )
    }
    val rawInfo = AudioMetadataReader.deriveAlbumHeaderFromTracks(
      tracks = baseTracks,
      customCovers = covers,
      fallbackTitle = "Album Title",
      fallbackArtist = "Artist Name"
    )
    val effectiveTracks = rawInfo.tracks.map { effectiveArtworkForSong(it, covers) }
    return rawInfo.copy(tracks = effectiveTracks)
  }

  val allSongsForAlbumPicker: StateFlow<List<Song>> = combine(
    localSongs,
    favorites,
    recentlyPlayed,
    featuredTracks,
    combine(popularYoutubeSongs, tiktokViralNewPipeSongs, searchResults) { popYt, tiktokCharts, search ->
      popYt + tiktokCharts + search
    }
  ) { local, favs, recent, featured, ytAndSearch ->
    (local + favs + recent + featured + ytAndSearch).distinctBy { it.id }
  }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

  private val albumMetaFlow = combine(
    customAlbumEntities,
    customAlbumTrackEntities,
    deletedAlbumIds,
    blockedArtists
  ) { customAlbums, albumTracks, deletedIds, blocked ->
    AlbumMetaSnapshot(customAlbums, albumTracks, deletedIds, blocked)
  }

  private data class AlbumMetaSnapshot(
    val customAlbums: List<CustomAlbumEntity>,
    val albumTracks: List<AlbumTrackEntity>,
    val deletedIds: Set<String>,
    val blockedArtists: Set<String>
  )

  val libraryAlbums: StateFlow<List<AlbumInfo>> = combine(
    rawLocalSongs,
    featuredTracks,
    favoriteIds,
    hiddenSongIds,
    albumCoverEntities
  ) { local, featured, favs, hidden, covers ->
    RawAlbumInputs(local, featured, favs, hidden, covers)
  }.combine(albumMetaFlow) { raw, meta ->
    val local = raw.local
    val featured = raw.featured
    val favs = raw.favs
    val hidden = raw.hidden
    val covers = raw.covers

    val customById = meta.customAlbums.associateBy { it.albumId }
    val customTracksByAlbumId = meta.albumTracks
      .groupBy { it.albumId }
      .mapValues { (albumId, entities) ->
        entities.mapNotNull { entity ->
          val song = TrackMetadataClassifier.enrich(
            Song(
              id = entity.songId,
              title = entity.title,
              artist = entity.artist.ifBlank { "Unknown Artist" },
              artworkUrl = entity.artworkUrl,
              durationSeconds = entity.durationSeconds,
              streamUrl = entity.streamUrl,
              source = entity.source,
              isFavorite = entity.songId in favs,
              genre = entity.genre,
              bpm = entity.bpm,
              mood = entity.mood,
              album = entity.albumName.ifBlank { customById[albumId]?.title.orEmpty() },
              trackNumber = entity.trackNumber,
              year = entity.year
            )
          )
          if (!isTrackVisible(song, hidden, meta.blockedArtists)) return@mapNotNull null
          val albumCover = covers[albumId]?.coverPath?.takeIf { it.isNotBlank() }
          if (albumCover != null) song.copy(artworkUrl = albumCover)
          else effectiveArtworkForSong(song, covers)
        }
      }

    val autoGroups: Map<String, List<Song>> = if (local.isEmpty()) {
      emptyMap()
    } else {
      local.groupBy { song ->
        computeAlbumId(
          albumName = song.album.ifBlank { "Unknown Album" },
          artistName = song.artist.ifBlank { "Unknown Artist" }
        )
      }
    }

    val orderedIds = LinkedHashSet<String>().apply {
      meta.customAlbums.forEach { add(it.albumId) }
      autoGroups.keys.forEach { add(it) }
      customTracksByAlbumId.keys.forEach { add(it) }
    }.filterNot { it in meta.deletedIds }

    val built = orderedIds.mapNotNull { albumId ->
      val customMeta = customById[albumId]
      val autoTracks = autoGroups[albumId].orEmpty().sortedWith(
        compareBy<Song> { if (it.trackNumber > 0) it.trackNumber else Int.MAX_VALUE }
          .thenBy { it.title.lowercase(Locale.ROOT) }
      )
      val explicitTracks = customTracksByAlbumId[albumId].orEmpty()
      val mergedTracks = (autoTracks + explicitTracks).distinctBy { it.id }

      if (customMeta == null && mergedTracks.isEmpty()) return@mapNotNull null

      val header = AudioMetadataReader.deriveAlbumHeaderFromTracks(
        tracks = mergedTracks,
        customCovers = covers,
        fallbackTitle = customMeta?.title ?: "Unknown Album",
        fallbackArtist = customMeta?.artist?.ifBlank { "Unknown Artist" } ?: "Unknown Artist"
      )
      val resolvedTitle = customMeta?.title?.ifBlank { header.title } ?: header.title
      val resolvedArtist = customMeta?.artist?.takeIf { it.isNotBlank() } ?: header.artist
      val resolvedYear = customMeta?.year?.takeIf { it.isNotBlank() } ?: header.year
      val customCover = covers[albumId]?.coverPath?.takeIf { it.isNotBlank() }
      val finalTracks = mergedTracks.map { track ->
        if (customCover != null) track.copy(artworkUrl = customCover)
        else effectiveArtworkForSong(track, covers)
      }
      header.copy(
        albumId = albumId,
        title = resolvedTitle,
        artist = resolvedArtist,
        year = resolvedYear,
        tracks = finalTracks
      )
    }

    val defaultExampleId = computeAlbumId("Album Title", "Artist Name")
    if (built.isEmpty() && local.isEmpty() && defaultExampleId !in meta.deletedIds) {
      listOf(buildDefaultExampleAlbum(featured, favs, hidden, covers))
    } else {
      built
    }
  }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

  private data class RawAlbumInputs(
    val local: List<Song>,
    val featured: List<Song>,
    val favs: Set<String>,
    val hidden: Set<String>,
    val covers: Map<String, AlbumCoverEntity>
  )

  val newAlbums: StateFlow<List<FeaturedAlbumItem>> = combine(
    _rawNewAlbums,
    libraryAlbums,
    blockedArtists
  ) { remote, local, blocked ->
    val remoteFiltered = remote.filter { normalizeArtistKey(it.artist) !in blocked }
    if (remoteFiltered.isNotEmpty()) {
      remoteFiltered
    } else {
      local.map { alb ->
        FeaturedAlbumItem(
          id = alb.albumId,
          collectionId = "",
          title = alb.title,
          artist = alb.artist,
          artworkUrl = alb.coverUrl ?: alb.automaticCoverUrl.orEmpty(),
          year = alb.year,
          source = "Library",
          localAlbumId = alb.albumId
        )
      }
    }
  }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

  val newForYouItems: StateFlow<List<NewForYouItem>> = combine(
    qualifiedPlayLogs,
    favorites,
    catalogTracks,
    newAlbums
  ) { logs, favs, catalog, albums ->
    RecommendationEngine.buildNewForYou(
      playLogs = logs,
      favorites = favs,
      catalog = catalog,
      newAlbums = albums,
      limit = 18
    )
  }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

  val activeAlbumInfo: StateFlow<AlbumInfo> = combine(
    libraryAlbums,
    _selectedAlbumId,
    _isShowingRemoteAlbum,
    _remoteAlbumInfo,
    favoriteIds
  ) { albums, selectedId, showingRemote, remoteAlbum, favs ->
    if (showingRemote && remoteAlbum != null) {
      remoteAlbum.copy(tracks = remoteAlbum.tracks.map { it.copy(isFavorite = it.id in favs) })
    } else {
      val fallback = albums.firstOrNull() ?: AlbumInfo(
        albumId = computeAlbumId("Album Title", "Artist Name"),
        title = "Album Title",
        artist = "Artist Name",
        year = "",
        coverUrl = null,
        automaticCoverUrl = null,
        tracks = emptyList()
      )
      if (selectedId != null) {
        albums.firstOrNull { it.albumId == selectedId } ?: fallback
      } else {
        fallback
      }
    }
  }.stateIn(
    viewModelScope,
    SharingStarted.Eagerly,
    AlbumInfo(
      albumId = computeAlbumId("Album Title", "Artist Name"),
      title = "Album Title",
      artist = "Artist Name",
      year = "",
      coverUrl = null,
      automaticCoverUrl = null,
      tracks = emptyList()
    )
  )

  val albumTracks: StateFlow<List<Song>> = activeAlbumInfo
    .map { it.tracks }
    .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

  val isAlbumAddedToLibrary: StateFlow<Boolean> = combine(
    albumTracks,
    favoriteIds
  ) { tracks, favs ->
    tracks.isNotEmpty() && tracks.all { it.id in favs }
  }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

  val isAlbumDownloaded: StateFlow<Boolean> = combine(
    albumTracks,
    downloadedSongIds
  ) { tracks, downloaded ->
    tracks.isNotEmpty() && tracks.all { it.id in downloaded }
  }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

  private val _rawCurrentSong = MutableStateFlow<Song?>(null)
  val currentSong: StateFlow<Song?> = combine(
    _rawCurrentSong,
    favoriteIds,
    albumCoverEntities
  ) { song, favs, covers ->
    song?.let {
      val withAlbumArt = effectiveArtworkForSong(it, covers)
      withAlbumArt.copy(isFavorite = withAlbumArt.id in favs)
    }
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

  private val _sleepRemainingSeconds = MutableStateFlow(0L)
  val sleepRemainingSeconds: StateFlow<Long> = _sleepRemainingSeconds

  private val _sleepEndOfTrack = MutableStateFlow(false)
  val sleepEndOfTrack: StateFlow<Boolean> = _sleepEndOfTrack

  private val _lyrics = MutableStateFlow<LyricsResult?>(null)
  val lyrics: StateFlow<LyricsResult?> = _lyrics

  private val _lyricOffsetMs = MutableStateFlow(0L)
  val lyricOffsetMs: StateFlow<Long> = _lyricOffsetMs

  val verifiedLyricsIds: StateFlow<Set<String>> = dao.observeVerifiedLyricsSongIds()
    .map { it.toSet() }
    .catch { emit(emptySet()) }
    .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

  val equalizerState: StateFlow<EqualizerUiState> = AudioEqualizerController.state
  val crossfadeSettings: StateFlow<CrossfadeSettings> = CrossfadeController.state

  private val _isAppInitialized = MutableStateFlow(false)
  val isAppInitialized: StateFlow<Boolean> = _isAppInitialized

  private val _launchSplashDismissed = MutableStateFlow(false)
  val launchSplashDismissed: StateFlow<Boolean> = _launchSplashDismissed

  fun markLaunchSplashShown() {
    _launchSplashDismissed.value = true
  }

  private val lyricsCheckSemaphore = Semaphore(3)
  private val inFlightLyricsChecks = ConcurrentHashMap<String, Job>()

  private var sleepTimerJob: Job? = null
  private var searchJob: Job? = null
  private var lyricsJob: Job? = null

  private val playerListener = object : Player.Listener {
    override fun onIsPlayingChanged(isPlayingVal: Boolean) {
      _isPlaying.value = isPlayingVal
      mediaController?.let { _currentPosition.value = it.currentPosition.coerceAtLeast(0L) }
      if (!isPlayingVal) {
        flushActivePlaySessionDuration()
      }
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
      if (_sleepEndOfTrack.value && reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
        _sleepEndOfTrack.value = false
        mediaController?.let { ctrl ->
          ctrl.pause()
          ctrl.volume = 1f
        }
        _isPlaying.value = false
        return
      }
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
      } else if (playbackState == Player.STATE_ENDED && _sleepEndOfTrack.value) {
        _sleepEndOfTrack.value = false
        controller.pause()
        controller.volume = 1f
        _isPlaying.value = false
      } else if (
        playbackState == Player.STATE_ENDED &&
        controller.mediaItemCount <= 1 &&
        _queue.value.size > 1 &&
        !_repeatEnabled.value &&
        _resolvingSongId.value == null
      ) {
        recordActiveSessionOutcome(skipped = false)
        nextTrack(fromNaturalEnd = true)
      }
      _currentPosition.value = controller.currentPosition.coerceAtLeast(0L)
    }

    override fun onPlayerError(error: PlaybackException) {
      _isPlaying.value = false
      flushActivePlaySessionDuration()
      val failing = _rawCurrentSong.value
      if (failing != null && failing.id.startsWith("yt_") && retriedPlaybackIds.add(failing.id)) {
        val videoId = failing.id.removePrefix("yt_")
        youtubeExtractCache.remove(videoId)
        val fallbackQueue = _queue.value
        playResolveJob?.cancel()
        playResolveJob = viewModelScope.launch {
          try {
            _resolvingSongId.value = failing.id
            val extracted = getOrExtractYoutubeData(videoId, forceRefresh = true)
            val bestFmt = pickBestPlayableFormat(extracted?.audioFormats.orEmpty())
            _resolvingSongId.value = null
            if (bestFmt != null) {
              StreamHeaderRegistry.register(bestFmt.streamUrl, bestFmt.requiredUserAgent)
              val refreshedSong = failing.copy(streamUrl = bestFmt.streamUrl)
              val updatedQueue = fallbackQueue.map { if (it.id == refreshedSong.id) refreshedSong else it }
              playSong(refreshedSong, updatedQueue)
            } else {
              runCatching {
                Toast.makeText(getApplication(), "Unable to stream this YouTube track. Try downloading a format.", Toast.LENGTH_SHORT).show()
              }
            }
          } catch (ce: CancellationException) {
            _resolvingSongId.value = null
            throw ce
          } catch (_: Throwable) {
            _resolvingSongId.value = null
            runCatching {
              Toast.makeText(getApplication(), "Unable to stream this YouTube track. Try downloading a format.", Toast.LENGTH_SHORT).show()
            }
          }
        }
      } else if (failing != null && failing.id.startsWith("yt_")) {
        runCatching {
          Toast.makeText(getApplication(), "Stream failed. Try another track or format.", Toast.LENGTH_SHORT).show()
        }
      }
    }
  }

  init {
    viewModelScope.launch(Dispatchers.IO) {
      try {
        runCatching { dao.clearAllBadLyrics() }
        runCatching { dao.clearReportedLyricsCache() }
        val eqEnabled = runCatching { prefsManager.eqEnabled.first() }.getOrDefault(false)
        val eqPreset = runCatching { prefsManager.eqPreset.first() }.getOrDefault("flat")
        val eqBands = runCatching { prefsManager.eqBands.first() }.getOrDefault(emptyList())
        val eqBass = runCatching { prefsManager.eqBassBoost.first() }.getOrDefault(0)
        val eqVirtualizer = runCatching { prefsManager.eqVirtualizer.first() }.getOrDefault(0)
        runCatching {
          AudioEqualizerController.restoreSettings(
            enabled = eqEnabled,
            presetId = eqPreset,
            bandLevelsMb = eqBands,
            bassBoostStrength = eqBass,
            virtualizerStrength = eqVirtualizer
          )
        }
        val crossfadeSec = runCatching { prefsManager.crossfadeSeconds.first() }.getOrDefault(0)
        val crossfadeManualSkip = runCatching { prefsManager.crossfadeManualSkip.first() }.getOrDefault(true)
        runCatching {
          CrossfadeController.restoreSettings(
            durationSeconds = crossfadeSec,
            fadeOnManualSkip = crossfadeManualSkip
          )
        }
        val savedLang = runCatching { prefsManager.language.first() }.getOrDefault("en")
        runCatching { LocaleHelper.saveLanguageSync(application, savedLang) }
        runCatching { prefsManager.onboardingCompleted.first() }
        val lastSeenVersionCode = runCatching { prefsManager.lastSeenVersionCode.first() }.getOrDefault(0L)
        if (lastSeenVersionCode < BuildConfig.VERSION_CODE.toLong()) {
          _showWhatsNewSheet.value = true
        }
      } catch (_: Throwable) {
        // Ensure startup continues even if legacy persisted data fails to read
      } finally {
        _isAppInitialized.value = true
      }
    }
    observeSearchRequests()
    observeDiscoverWeeklyUpdates()
    observeCatalogMetadataSync()
    observeBackgroundDownloads()
    connectPlaybackController(application)
    loadDailyRecommendations(DailyFeedCategory.TIKTOK_VIRAL)
    loadPopularYoutubeSongs()
    loadTiktokViralNewPipeSongs()
    startPositionTicker()
    runCatching { AppNotificationHelper.ensureChannels(application) }
    UpdateCheckScheduler.schedulePeriodicChecks(application)
    if (updateManager.uiState.value.stage == UpdateStage.Idle) {
      checkForUpdates(application, silent = true)
    }
  }

  private fun observeBackgroundDownloads() {
    viewModelScope.launch {
      BackgroundDownloadRegistry.videoDownloadStates.collect { videoMap ->
        val currentAnalysisId = _youtubeAnalysis.value?.videoId
        if (currentAnalysisId != null) {
          val stateForPane = videoMap[currentAnalysisId]
          if (stateForPane != null) {
            _youtubeDownloadState.value = stateForPane
          }
        }
        val currentSheet = _formatSheetState.value
        if (currentSheet is YoutubeFormatSheetState.Ready) {
          val stateForSheet = videoMap[currentSheet.analysis.videoId]
          if (stateForSheet != null && currentSheet.downloadState != stateForSheet) {
            _formatSheetState.value = currentSheet.copy(downloadState = stateForSheet)
          }
        }
      }
    }
    viewModelScope.launch {
      BackgroundDownloadRegistry.feedbackEvents.collect { event ->
        runCatching {
          Toast.makeText(getApplication(), event.message, Toast.LENGTH_LONG).show()
        }
      }
    }
  }

  fun handleNotificationIntent(intent: Intent?) {
    if (intent == null) return
    val openScreen = intent.getStringExtra(AppNotificationHelper.EXTRA_OPEN_SCREEN)
    val updateAction = intent.getStringExtra(AppNotificationHelper.EXTRA_UPDATE_ACTION)
    val libraryTab = intent.getIntExtra(AppNotificationHelper.EXTRA_OPEN_LIBRARY_TAB, 0)

    if (!openScreen.isNullOrBlank()) {
      _pendingNotificationNav.value = NotificationNavTarget(
        screenRoute = openScreen,
        libraryTab = libraryTab
      )
      intent.removeExtra(AppNotificationHelper.EXTRA_OPEN_SCREEN)
    }
    if (!updateAction.isNullOrBlank()) {
      intent.removeExtra(AppNotificationHelper.EXTRA_UPDATE_ACTION)
      when (updateAction) {
        AppNotificationHelper.ACTION_DOWNLOAD_UPDATE -> updateManager.startOrResumeDownload()
        AppNotificationHelper.ACTION_INSTALL_UPDATE -> updateManager.launchSystemInstaller()
        AppNotificationHelper.ACTION_RETRY_UPDATE -> updateManager.retryFailedAction()
        AppNotificationHelper.ACTION_VIEW_UPDATE -> {
          if (updateManager.uiState.value.stage == UpdateStage.Idle) {
            checkForUpdates(getApplication(), silent = false)
          }
        }
      }
    }
  }

  fun consumeNotificationNav() {
    _pendingNotificationNav.value = null
  }

  private fun observeCatalogMetadataSync() {
    viewModelScope.launch {
      combine(
        featuredTracks,
        popularYoutubeSongs,
        tiktokViralNewPipeSongs,
        localSongs,
        searchResults
      ) { f, p, t, l, s ->
        (p + t + f + l + s).distinctBy { it.id }
      }
        .debounce(1_000L)
        .catch { }
        .collectLatest { tracks ->
          if (tracks.isNotEmpty()) {
            runCatching { analyticsRepository.upsertCatalogMetadata(tracks.take(120)) }
          }
        }
    }
  }

  private fun observeDiscoverWeeklyUpdates() {
    viewModelScope.launch {
      combine(qualifiedPlayLogs, catalogTracks) { logs, catalog -> logs to catalog }
        .catch { }
        .collectLatest { (logs, catalog) ->
          if (logs.isNotEmpty() && catalog.isNotEmpty()) {
            runCatching {
              analyticsRepository.ensureDiscoverWeekly(
                playLogs = logs,
                catalog = catalog,
                existingSnapshot = rawDiscoverWeekly.value,
                forceRefresh = false
              )
            }
          }
        }
    }
  }

  private fun connectPlaybackController(application: Application) {
    runCatching {
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
        } catch (_: Throwable) {
          // Session service connection failed gracefully
        }
      }, ContextCompat.getMainExecutor(application))
    }
  }

  private fun startPositionTicker() {
    viewModelScope.launch {
      var lastElapsedMs = SystemClock.elapsedRealtime()
      while (isActive) {
        val nowElapsedMs = SystemClock.elapsedRealtime()
        val deltaMs = (nowElapsedMs - lastElapsedMs).coerceIn(0L, 1_000L)
        lastElapsedMs = nowElapsedMs

        val controller = mediaController
        if (controller != null && (controller.isPlaying || controller.playbackState == Player.STATE_READY)) {
          _currentPosition.value = controller.currentPosition.coerceAtLeast(0L)
          val playerDur = controller.duration
          if (playerDur > 0L) {
            _durationMs.value = playerDur
          }
          if (controller.isPlaying) {
            val activeId = controller.currentMediaItem?.mediaId ?: _rawCurrentSong.value?.id
            if (activeId != null) {
              handleActivePlayTick(activeId, deltaMs)
            }
          }
        }
        // 50ms cadence keeps synced lyrics tightly locked to ExoPlayer's audio clock
        delay(if (_isPlaying.value) 50L else 250L)
      }
    }
  }

  private fun handleActivePlayTick(activeSongId: String, deltaMs: Long) {
    when (val action = playSessionTracker.onActivePlaybackDelta(activeSongId, deltaMs)) {
      is PlaySessionAction.LogQualifiedPlay -> {
        viewModelScope.launch {
          val rowId = analyticsRepository.logQualifiedPlay(
            song = action.song,
            playedAtMs = action.startedAtMs,
            listenedDurationMs = action.listenedDurationMs
          )
          val extraDuration = playSessionTracker.attachPersistedRowId(
            songId = action.song.id,
            startedAt = action.startedAtMs,
            rowId = rowId
          )
          if (extraDuration != null) {
            analyticsRepository.updatePlayLogDuration(rowId, extraDuration)
          }
        }
      }
      is PlaySessionAction.UpdateListenedDuration -> {
        viewModelScope.launch {
          analyticsRepository.updatePlayLogDuration(action.playLogId, action.listenedDurationMs)
        }
      }
      null -> Unit
    }
  }

  private fun flushActivePlaySessionDuration() {
    val action = playSessionTracker.flushPendingDuration() ?: return
    viewModelScope.launch {
      analyticsRepository.updatePlayLogDuration(action.playLogId, action.listenedDurationMs)
    }
  }

  private fun recordActiveSessionOutcome(skipped: Boolean) {
    val snapshot = playSessionTracker.currentSessionSnapshot() ?: return
    val song = snapshot.song
    val listened = snapshot.listenedDurationMs
    if (listened < 1_500L) return
    val totalDur = _durationMs.value.coerceAtLeast(song.durationSeconds * 1000L).coerceAtLeast(1L)
    val rowId = snapshot.persistedRowId
    val alreadyPlayed = richPlayHistory.value.any { it.song.id == song.id }

    viewModelScope.launch {
      if (rowId != null && rowId > 0L) {
        analyticsRepository.updatePlayOutcome(
          playLogId = rowId,
          song = song,
          listenedDurationMs = listened,
          totalDurationMs = totalDur,
          skipped = skipped,
          replayed = alreadyPlayed && !skipped
        )
      } else if (skipped) {
        analyticsRepository.recordEarlySkip(
          song = song,
          playedAtMs = snapshot.startedAtMs,
          listenedDurationMs = listened,
          durationMs = totalDur
        )
      }
    }
  }

  private fun onTrackActivated(song: Song) {
    val enriched = TrackMetadataClassifier.enrich(song)
    val previousSong = _rawCurrentSong.value
    if (previousSong != null && previousSong.id != enriched.id) {
      val prevSnap = playSessionTracker.currentSessionSnapshot()
      if (prevSnap != null && prevSnap.song.id == previousSong.id) {
        val totalDur = _durationMs.value.coerceAtLeast(previousSong.durationSeconds * 1000L).coerceAtLeast(1L)
        val pct = prevSnap.listenedDurationMs.toFloat() / totalDur.toFloat()
        if (pct >= 0.78f) {
          recordActiveSessionOutcome(skipped = false)
        }
      }
    }

    _rawCurrentSong.value = enriched
    _currentPosition.value = 0L
    _durationMs.value = (enriched.durationSeconds * 1000L).coerceAtLeast(1000L)
    _lyricOffsetMs.value = 0L

    // Update MoodFlow live match readout for the newly active track
    if (moodFlowEnabled.value && (_moodFlowCurrentPick.value?.song?.id != enriched.id)) {
      val profile = MoodFlowEngine.buildCurrentMoodProfile(
        currentSong = previousSong ?: enriched,
        previousSongs = recentlyPlayed.value.filter { it.id != enriched.id }.take(2)
      )
      val (_, pct) = MoodFlowEngine.computeAdjustedScore(
        profile = profile,
        candidate = enriched,
        playHistory = richPlayHistory.value
      )
      _moodFlowCurrentPick.value = MoodFlowPick(
        song = enriched,
        matchPercent = pct,
        score = pct / 100.0,
        bucket = if (_shuffleEnabled.value) MoodFlowBucket.SAME_MOOD else MoodFlowBucket.RANDOM_FALLBACK,
        reason = "MoodFlow · $pct% match"
      )
    }

    val flushPrevious = playSessionTracker.startNewSession(enriched)
    viewModelScope.launch {
      if (flushPrevious != null) {
        analyticsRepository.updatePlayLogDuration(flushPrevious.playLogId, flushPrevious.listenedDurationMs)
      }
      _lyricOffsetMs.value = dao.getLyricOffset(enriched.id) ?: 0L
    }
    fetchLyrics(enriched)
  }

  private fun buildMediaItem(song: Song): MediaItem {
    val pkg = getApplication<Application>().packageName
    val resolvedArtworkUri = when {
      song.artworkUrl == APP_ICON_ARTWORK ||
        (song.artworkUrl.isBlank() && (song.source == "Local" || song.source == "Imported")) -> {
        runCatching { Uri.parse("android.resource://$pkg/${com.example.R.drawable.aurix_clean_logo_1794625921343}") }.getOrNull()
      }
      song.artworkUrl.isNotBlank() -> runCatching { Uri.parse(song.artworkUrl) }.getOrNull()
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

    val streamUri = runCatching { Uri.parse(song.streamUrl) }.getOrDefault(Uri.EMPTY)
    return MediaItem.Builder()
      .setMediaId(song.id)
      .setUri(streamUri)
      .setMediaMetadata(metadata)
      .build()
  }

  private fun startQueueOnController(controller: MediaController, song: Song, resolvedQueue: List<Song>) {
    val basePlayable = resolvedQueue
      .filter { it.streamUrl.isNotBlank() && (!isUnresolvedYoutubeWatchUrl(it) || it.id == song.id) }
      .ifEmpty { listOf(song) }

    val useMoodFlowOrder = _shuffleEnabled.value && moodFlowEnabled.value && basePlayable.size > 1
    val orderedQueue = if (useMoodFlowOrder) {
      val remaining = basePlayable.filter { it.id != song.id }
      val nextPick = MoodFlowEngine.pickNextSong(
        currentSong = song,
        allSongs = remaining,
        playHistory = richPlayHistory.value,
        favoriteIds = favoriteIds.value,
        recentPlayedSongs = recentlyPlayed.value
      )
      if (nextPick != null) {
        val rest = remaining.filter { it.id != nextPick.song.id }
        listOf(song, nextPick.song) + rest
      } else {
        basePlayable
      }
    } else {
      basePlayable
    }

    val startIndex = orderedQueue.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
    val mediaItems = orderedQueue.map { buildMediaItem(it) }

    controller.stop()
    controller.volume = 1f
    controller.setMediaItems(mediaItems, startIndex, 0L)
    controller.playbackParameters = PlaybackParameters(_playbackSpeed.value)
    // When MoodFlow is enabled, Aurix orders and selects transitions instead of raw uniform ExoPlayer shuffle
    controller.shuffleModeEnabled = _shuffleEnabled.value && !moodFlowEnabled.value
    controller.repeatMode = if (_repeatEnabled.value) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_ALL
    controller.prepare()
    controller.play()
  }

  fun setSearchMode(mode: String) {
    val normalizedMode = when (mode) {
      "youtube" -> "youtube"
      "itunes" -> "itunes"
      else -> "youtube_search"
    }
    val nextEnum = when (normalizedMode) {
      "youtube" -> SearchMode.YouTubeLink
      "itunes" -> SearchMode.ITunes30s
      else -> SearchMode.YouTubeSearch
    }
    val changed = _searchMode.value != normalizedMode
    _searchMode.value = normalizedMode
    _searchModeEnum.value = nextEnum
    if (changed) {
      val currentQ = if (nextEnum == SearchMode.YouTubeLink) _youtubeUrl.value else _searchQuery.value
      if (currentQ.isBlank()) {
        _searchDispatch.tryEmit(SearchDispatch("", nextEnum, 0L, ++searchDispatchNonce))
      } else if (nextEnum == SearchMode.YouTubeSearch || nextEnum == SearchMode.ITunes30s) {
        _searchDispatch.tryEmit(SearchDispatch(currentQ, nextEnum, 0L, ++searchDispatchNonce))
      } else if (_youtubeAnalysis.value == null && !_isAnalyzingYoutube.value) {
        analyzeYoutubeUrl()
      }
    }
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

        val videoDurSec = extracted?.durationSeconds?.takeIf { it > 0L }
          ?: fullFormats.firstOrNull()?.durationSeconds
          ?: 0L

        val trackTitle = parsedTitle.ifBlank { rawTitle }
        val trackArtist = parsedArtist.ifBlank { "YouTube Audio" }
        val displayThumb = thumbnailUrl

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
        extracted?.let { youtubeExtractCache[videoId] = System.currentTimeMillis() to it }
        _youtubeAnalysis.value = analysis
        _selectedYoutubeFormatId.value = fullFormats.first().id
      } catch (ce: CancellationException) {
        throw ce
      } catch (_: Throwable) {
        _youtubeError.value = "Unable to analyze YouTube link. Check your network connection."
      } finally {
        _isAnalyzingYoutube.value = false
      }
    }
  }

  fun playAnalyzedYoutubeTrack() {
    val analysis = _youtubeAnalysis.value ?: return
    val selectedId = _selectedYoutubeFormatId.value
    val selectedFmt = analysis.formats.firstOrNull { it.id == selectedId }
    val playableFmt = selectedFmt?.takeIf { it.extension.equals("m4a", ignoreCase = true) || it.extension.equals("mp4", ignoreCase = true) }
      ?: pickBestPlayableFormat(analysis.formats)
      ?: selectedFmt
      ?: return
    StreamHeaderRegistry.register(playableFmt.streamUrl, playableFmt.requiredUserAgent)
    val song = TrackMetadataClassifier.enrich(
      Song(
        id = "yt_${analysis.videoId}",
        title = analysis.matchedTrackTitle,
        artist = analysis.matchedArtist,
        artworkUrl = analysis.thumbnailUrl,
        durationSeconds = analysis.durationSeconds,
        streamUrl = playableFmt.streamUrl,
        source = "YouTube",
        isFavorite = "yt_${analysis.videoId}" in favoriteIds.value,
        fullDurationSeconds = analysis.durationSeconds
      )
    )
    playSong(song, listOf(song))
  }

  private fun isUnresolvedYoutubeWatchUrl(song: Song): Boolean {
    return song.id.startsWith("yt_") &&
      (song.streamUrl.contains("youtube.com/watch", ignoreCase = true) ||
        song.streamUrl.contains("youtu.be/", ignoreCase = true))
  }

  private fun pickBestPlayableFormat(formats: List<AudioFormatOption>): AudioFormatOption? {
    if (formats.isEmpty()) return null
    val m4a = formats.filter { it.extension.equals("m4a", ignoreCase = true) }
    if (m4a.isNotEmpty()) return m4a.first()
    val mp4 = formats.filter { it.extension.equals("mp4", ignoreCase = true) }
    if (mp4.isNotEmpty()) return mp4.first()
    return formats.first()
  }

  private suspend fun getOrExtractYoutubeData(
    videoId: String,
    forceRefresh: Boolean = false
  ): ExtractedYoutubeData? {
    val now = System.currentTimeMillis()
    if (!forceRefresh) {
      val cached = youtubeExtractCache[videoId]
      if (cached != null && now - cached.first < 15 * 60_000L && cached.second.audioFormats.isNotEmpty()) {
        return cached.second
      }
    }
    val extracted = YoutubeExtractor.extractFullAudio(videoId)
    if (extracted != null && extracted.audioFormats.isNotEmpty()) {
      youtubeExtractCache[videoId] = now to extracted
    }
    return extracted
  }

  fun openYoutubeFormatSheet(song: Song) {
    val videoId = if (song.id.startsWith("yt_")) {
      song.id.removePrefix("yt_")
    } else {
      YoutubeExtractor.extractVideoId(song.streamUrl)
    }
    if (videoId.isNullOrBlank()) {
      downloadSong(getApplication(), song)
      return
    }

    formatSheetJob?.cancel()
    _formatSheetState.value = YoutubeFormatSheetState.Loading(song = song, videoId = videoId)
    formatSheetJob = viewModelScope.launch {
      try {
        val extracted = getOrExtractYoutubeData(videoId)
        val formats = extracted?.audioFormats.orEmpty()
        if (extracted == null || formats.isEmpty()) {
          val reason = extracted?.extractorError?.takeIf { it.isNotBlank() }
          _formatSheetState.value = YoutubeFormatSheetState.Error(
            song = song,
            videoId = videoId,
            message = reason ?: "Could not load audio formats for this track."
          )
          return@launch
        }
        val durSec = extracted.durationSeconds.takeIf { it > 0L } ?: song.durationSeconds
        val analysis = YoutubeAnalysisResult(
          videoId = videoId,
          originalUrl = "https://www.youtube.com/watch?v=$videoId",
          videoTitle = extracted.title.ifBlank { song.title },
          channelName = extracted.author.ifBlank { song.artist },
          thumbnailUrl = extracted.thumbnailUrl.ifBlank { song.artworkUrl },
          matchedTrackTitle = song.title,
          matchedArtist = song.artist,
          durationSeconds = durSec,
          formats = formats
        )
        _formatSheetState.value = YoutubeFormatSheetState.Ready(
          song = song.copy(durationSeconds = durSec, fullDurationSeconds = durSec),
          analysis = analysis,
          selectedFormatId = formats.first().id,
          downloadState = YoutubeDownloadState.Idle
        )
      } catch (ce: CancellationException) {
        throw ce
      } catch (_: Throwable) {
        _formatSheetState.value = YoutubeFormatSheetState.Error(
          song = song,
          videoId = videoId,
          message = "Unable to load formats. Check your connection."
        )
      }
    }
  }

  fun closeYoutubeFormatSheet() {
    val current = _formatSheetState.value
    if (current is YoutubeFormatSheetState.Ready && current.downloadState is YoutubeDownloadState.Downloading) {
      // Allow closing only if not actively downloading, or keep closing safe without losing state
      formatSheetJob?.cancel()
    } else {
      formatSheetJob?.cancel()
    }
    _formatSheetState.value = YoutubeFormatSheetState.Closed
  }

  fun selectFormatSheetOption(formatId: String) {
    val current = _formatSheetState.value
    if (current is YoutubeFormatSheetState.Ready && current.downloadState !is YoutubeDownloadState.Downloading) {
      _formatSheetState.value = current.copy(
        selectedFormatId = formatId,
        downloadState = if (current.downloadState is YoutubeDownloadState.Error) {
          YoutubeDownloadState.Idle
        } else {
          current.downloadState
        }
      )
    }
  }

  fun playFromFormatSheet() {
    val current = _formatSheetState.value
    if (current is YoutubeFormatSheetState.Ready) {
      val dl = current.downloadState
      if (dl is YoutubeDownloadState.Saved) {
        playSong(dl.song, listOf(dl.song))
        return
      }
      val selected = current.analysis.formats.firstOrNull { it.id == current.selectedFormatId }
      val fmt = selected?.takeIf { it.extension.equals("m4a", ignoreCase = true) || it.extension.equals("mp4", ignoreCase = true) }
        ?: pickBestPlayableFormat(current.analysis.formats)
        ?: selected
      if (fmt != null) {
        StreamHeaderRegistry.register(fmt.streamUrl, fmt.requiredUserAgent)
        val playable = current.song.copy(
          streamUrl = fmt.streamUrl,
          durationSeconds = current.analysis.durationSeconds.takeIf { it > 0L } ?: current.song.durationSeconds,
          fullDurationSeconds = current.analysis.durationSeconds.takeIf { it > 0L } ?: current.song.fullDurationSeconds
        )
        playSong(playable, listOf(playable))
      }
    } else if (current is YoutubeFormatSheetState.Loading) {
      playSong(current.song, listOf(current.song))
    }
  }

  fun downloadFromFormatSheet(context: Context) {
    val current = _formatSheetState.value
    if (current !is YoutubeFormatSheetState.Ready) return
    if (current.downloadState is YoutubeDownloadState.Downloading) return

    val analysis = current.analysis
    val format = analysis.formats.firstOrNull { it.id == current.selectedFormatId }
      ?: analysis.formats.firstOrNull()
      ?: return

    BackgroundDownloadRegistry.enqueueFormatDownload(
      context = context,
      songIdForBadge = current.song.id,
      analysis = analysis,
      initialFormat = format,
      isFavoriteProvider = { id -> id in favoriteIds.value }
    )
  }

  fun downloadAnalyzedTrack(context: Context) {
    val analysis = _youtubeAnalysis.value ?: return
    val selectedId = _selectedYoutubeFormatId.value
    val format = analysis.formats.firstOrNull { it.id == selectedId } ?: analysis.formats.firstOrNull() ?: return
    if (_youtubeDownloadState.value is YoutubeDownloadState.Downloading) return

    BackgroundDownloadRegistry.enqueueFormatDownload(
      context = context,
      songIdForBadge = "yt_${analysis.videoId}",
      analysis = analysis,
      initialFormat = format,
      isFavoriteProvider = { id -> id in favoriteIds.value }
    )
  }

  fun startUpdateLogoIntro() {
    if (logoIntroPlayed) return
    val currentStage = updateManager.uiState.value.stage
    if (currentStage != UpdateStage.Idle && currentStage != UpdateStage.UpToDate) return
    logoIntroPlayed = true
    logoTransitionJob?.cancel()
    _logoIntroActive.value = true
    logoTransitionJob = viewModelScope.launch {
      delay(1700L)
      _logoIntroActive.value = false
    }
  }

  fun dismissWhatsNewSheet() {
    _showWhatsNewSheet.value = false
    viewModelScope.launch {
      prefsManager.setLastSeenVersionCode(BuildConfig.VERSION_CODE.toLong())
    }
  }

  fun checkForUpdates(context: Context = getApplication(), silent: Boolean = false) {
    val owner = githubOwner.value.trim().ifBlank { "MEYYYNASH" }
    val repo = githubRepo.value.trim().ifBlank { "AURIX" }
    updateManager.checkForUpdates(githubOwner = owner, githubRepo = repo, silent = silent)
  }

  fun downloadAndInstallUpdate(context: Context = getApplication()) {
    updateManager.startOrResumeDownload()
  }

  fun pauseUpdateDownload() {
    updateManager.pauseDownload()
  }

  fun cancelUpdateDownload() {
    updateManager.cancelDownload()
  }

  fun retryUpdateAction() {
    updateManager.retryFailedAction()
  }

  fun installDownloadedApk(context: Context = getApplication()) {
    updateManager.launchSystemInstaller()
  }

  fun openUnknownAppsSettings() {
    updateManager.openUnknownSourcesSettings()
  }

  fun onUpdateHostResumed() {
    updateManager.onHostResumed()
  }

  fun openGitHubRepo(context: Context = getApplication()) {
    updateManager.openReleasePage(context)
  }

  fun restartApp(context: Context = getApplication()) {
    updateManager.restartApp(context)
  }

  fun downloadSong(context: Context, song: Song) {
    BackgroundDownloadRegistry.enqueueSongDownload(context, song)
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
    val combined = "$t $a"
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
      "bpm)",
      "ai cover",
      "ai version",
      "ai song",
      "ai music",
      "ai sound",
      "ai generated",
      "ai voice",
      "ai remix",
      "made with ai",
      "suno",
      "udio",
      "tiktok sound",
      "viral sound",
      "meme sound",
      "sound effect",
      "sfx",
      "sped up",
      "slowed",
      "reverb",
      "nightcore",
      "type beat",
      "instrumental"
    )
    if (blocked.any { kw -> combined.contains(kw) }) return false
    if (Regex("""\b(ai|a\.i\.)\b""").containsMatchIn(combined)) return false
    return true
  }

  private fun isSingleQualityYoutubeSong(item: YoutubeSearchItem, parsedTitle: String, parsedArtist: String): Boolean {
    if (item.videoId.isBlank() || parsedTitle.isBlank()) return false
    if (!isQualityViralTrack(parsedTitle, parsedArtist)) return false
    val combined = "${item.rawTitle} ${item.author}".lowercase(Locale.US)
    val compilationKeywords = listOf(
      "#shorts", "shorts", "playlist", "1 hour", "2 hours", "hour mix", "megamix", "full album",
      "top 50", "top 40", "top 20", "nonstop", "compilation", "study with me"
    )
    if (compilationKeywords.any { combined.contains(it) }) return false
    // Require full-length YouTube songs (60s..600s); reject shorts/snippets and long mixes
    if (item.durationSeconds < 60L || item.durationSeconds > 600L) return false
    return true
  }

  private fun mapYoutubeSearchItem(
    item: YoutubeSearchItem,
    sourceTag: String = "YouTube",
    moodHint: String = "",
    genreHint: String? = null
  ): Song {
    val (parsedTitle, parsedArtist) = parseYoutubeTitleAndArtist(item.rawTitle, item.author)
    val resolvedTitle = parsedTitle.ifBlank { item.rawTitle }
    val resolvedArtist = parsedArtist.ifBlank { item.author.ifBlank { "YouTube" } }
    return TrackMetadataClassifier.enrich(
      Song(
        id = "yt_${item.videoId}",
        title = resolvedTitle,
        artist = resolvedArtist,
        artworkUrl = item.thumbnailUrl,
        durationSeconds = item.durationSeconds,
        streamUrl = "https://www.youtube.com/watch?v=${item.videoId}",
        source = sourceTag,
        fullDurationSeconds = item.durationSeconds,
        mood = moodHint,
        genre = genreHint.orEmpty()
      ),
      genreHint = genreHint
    )
  }

  private suspend fun fetchYoutubeFeedTracks(
    queries: List<String>,
    sourceTag: String = "YouTube",
    limitPerQuery: Int = 20,
    moodHint: String = "",
    genreHint: String? = null
  ): List<Song> = withContext(Dispatchers.IO) {
    val collected = LinkedHashMap<String, Song>()
    val seenKeys = HashSet<String>()
    for (q in queries) {
      val results = runCatching { YoutubeExtractor.searchSongs(query = q, limit = limitPerQuery) }.getOrNull().orEmpty()
      for (item in results) {
        val (parsedTitle, parsedArtist) = parseYoutubeTitleAndArtist(item.rawTitle, item.author)
        if (!isSingleQualityYoutubeSong(item, parsedTitle, parsedArtist)) continue
        val song = mapYoutubeSearchItem(
          item = item,
          sourceTag = sourceTag,
          moodHint = moodHint,
          genreHint = genreHint
        )
        val dedupeKey = "${normalizeForMatch(song.title)}::${normalizeArtistKey(song.artist)}"
        if (song.id !in collected && dedupeKey !in seenKeys) {
          collected[song.id] = song
          seenKeys.add(dedupeKey)
        }
      }
      if (collected.size >= 24) break
    }
    collected.values.toList()
  }

  private fun mapItunesTracks(
    results: List<ItunesTrack>,
    sourceTag: String = "iTunes 30s",
    albumOverride: String? = null
  ): List<Song> {
    return results.mapNotNull { track ->
      val preview = track.previewUrl?.trim()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
      val title = track.trackName?.trim()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
      val artist = track.artistName?.trim()?.takeIf { it.isNotBlank() } ?: "Unknown Artist"
      if (!isQualityViralTrack(title, artist)) return@mapNotNull null
      val artworkRaw = track.artworkUrl100?.takeIf { it.isNotBlank() }
        ?: track.artworkUrl60?.takeIf { it.isNotBlank() }
        ?: ""
      val artwork = artworkRaw
        .replace("100x100bb", "600x600bb")
        .replace("60x60bb", "600x600bb")
      val durationSec = ((track.trackTimeMillis ?: 30_000L) / 1000L).coerceAtLeast(1L)
      val year = track.releaseDate?.take(4).orEmpty()
      TrackMetadataClassifier.enrich(
        Song(
          id = "itunes_${track.trackId}",
          title = title,
          artist = artist,
          artworkUrl = artwork,
          durationSeconds = durationSec,
          streamUrl = preview,
          source = sourceTag,
          fullDurationSeconds = durationSec,
          genre = track.primaryGenreName.orEmpty(),
          album = albumOverride ?: track.collectionName.orEmpty(),
          year = year
        ),
        genreHint = track.primaryGenreName
      )
    }
  }

  private suspend fun fetchItunesFeedTracks(
    queries: List<String>,
    sourceTag: String = "iTunes 30s",
    countries: List<String> = listOf("US", "GB"),
    targetCount: Int = 24
  ): List<Song> = withContext(Dispatchers.IO) {
    val collected = LinkedHashMap<String, Song>()
    val seenKeys = HashSet<String>()
    for (country in countries) {
      for (q in queries) {
        val resp = runCatching {
          NetworkClient.itunesApi.searchTracks(term = q, limit = 25, country = country)
        }.getOrNull()
        val mapped = mapItunesTracks(resp?.results.orEmpty(), sourceTag = sourceTag)
        for (song in mapped) {
          val dedupeKey = "${normalizeForMatch(song.title)}::${normalizeArtistKey(song.artist)}"
          if (song.id !in collected && dedupeKey !in seenKeys) {
            collected[song.id] = song
            seenKeys.add(dedupeKey)
          }
        }
        if (collected.size >= targetCount) break
      }
      if (collected.size >= targetCount) break
    }
    collected.values.toList()
  }

  private suspend fun fetchAppleDailyChartTracks(
    sourceTag: String = "iTunes 30s",
    countries: List<String> = listOf("us"),
    fallbackQueries: List<String> = listOf("billboard hot 100", "today's hits", "pop hits 2026")
  ): List<Song> = withContext(Dispatchers.IO) {
    val chartEntries = mutableListOf<AppleChartEntry>()
    val seenChartKeys = HashSet<String>()
    for (country in countries) {
      val entries = runCatching {
        NetworkClient.appleChartsApi.getTopSongs(country).feed?.results.orEmpty()
      }.getOrNull().orEmpty()
      for (entry in entries) {
        val title = entry.name?.trim().orEmpty()
        val artist = entry.artistName?.trim().orEmpty()
        if (title.isBlank() || artist.isBlank()) continue
        if (!isQualityViralTrack(title, artist)) continue
        val key = "${normalizeForMatch(title)}::${normalizeArtistKey(artist)}"
        if (seenChartKeys.add(key)) {
          chartEntries.add(entry)
        }
        if (chartEntries.size >= 24) break
      }
      if (chartEntries.size >= 24) break
    }

    val resolved = mutableListOf<Song>()
    if (chartEntries.isNotEmpty()) {
      val semaphore = Semaphore(4)
      val pairs = coroutineScope {
        chartEntries.map { entry ->
          async {
            semaphore.withPermit {
              val title = entry.name?.trim().orEmpty()
              val artist = entry.artistName?.trim().orEmpty()
              val search = runCatching {
                NetworkClient.itunesApi.searchTracks(term = "$title $artist", limit = 4, country = "US")
              }.getOrNull()
              val mapped = mapItunesTracks(search?.results.orEmpty(), sourceTag = sourceTag)
              val exact = mapped.firstOrNull {
                normalizeForMatch(it.title).contains(normalizeForMatch(title)) ||
                  normalizeArtistKey(it.artist).contains(normalizeArtistKey(artist))
              } ?: mapped.firstOrNull()
              exact?.let { matched ->
                val hiArt = entry.artworkUrl100
                  ?.replace("100x100bb", "600x600bb")
                  ?.takeIf { it.isNotBlank() }
                if (hiArt != null && matched.artworkUrl.isBlank()) matched.copy(artworkUrl = hiArt) else matched
              }
            }
          }
        }.awaitAll()
      }
      val seen = HashSet<String>()
      for (song in pairs) {
        if (song != null) {
          val key = "${normalizeForMatch(song.title)}::${normalizeArtistKey(song.artist)}"
          if (seen.add(key)) resolved.add(song)
        }
      }
    }

    if (resolved.size < 12) {
      val extra = fetchItunesFeedTracks(
        queries = fallbackQueries,
        sourceTag = sourceTag,
        targetCount = 24
      )
      val seenIds = resolved.mapTo(HashSet()) { it.id }
      for (s in extra) {
        if (seenIds.add(s.id)) resolved.add(s)
        if (resolved.size >= 24) break
      }
    }
    resolved
  }

  private fun interleaveTracks(
    first: List<Song>,
    second: List<Song>,
    limit: Int = 24
  ): List<Song> {
    val result = ArrayList<Song>(minOf(first.size + second.size, limit))
    val seenIds = HashSet<String>()
    val maxLen = maxOf(first.size, second.size)
    for (i in 0 until maxLen) {
      if (i < first.size) {
        val track = first[i]
        if (seenIds.add(track.id)) result.add(track)
      }
      if (result.size >= limit) break
      if (i < second.size) {
        val track = second[i]
        if (seenIds.add(track.id)) result.add(track)
      }
      if (result.size >= limit) break
    }
    return result
  }

  fun loadPopularYoutubeSongs(forceRefresh: Boolean = false) {
    if (!forceRefresh && _rawPopularYoutube.value.isNotEmpty()) return
    if (_isLoadingPopularYoutube.value) return
    viewModelScope.launch {
      _isLoadingPopularYoutube.value = true
      try {
        val tracks = fetchYoutubeFeedTracks(
          queries = listOf(
            "top popular songs 2026 official audio",
            "trending music hits 2026 official audio",
            "billboard hot hits pop r&b official audio"
          ),
          sourceTag = "YouTube",
          limitPerQuery = 22
        )
        if (tracks.isNotEmpty()) {
          _rawPopularYoutube.value = tracks
        }
      } catch (ce: CancellationException) {
        throw ce
      } catch (_: Throwable) {
      } finally {
        _isLoadingPopularYoutube.value = false
      }
    }
  }

  fun loadTiktokViralNewPipeSongs(forceRefresh: Boolean = false) {
    if (!forceRefresh && _rawTiktokViralNewPipe.value.isNotEmpty()) return
    if (_isLoadingTiktokViralNewPipe.value) return
    viewModelScope.launch {
      _isLoadingTiktokViralNewPipe.value = true
      try {
        val tracks = withContext(Dispatchers.IO) {
          val rawItems = runCatching { YoutubeExtractor.fetchTikTokViralChartTracks(limit = 32) }
            .getOrNull()
            .orEmpty()
          val collected = LinkedHashMap<String, Song>()
          val seenKeys = HashSet<String>()
          for (item in rawItems) {
            val (parsedTitle, parsedArtist) = parseYoutubeTitleAndArtist(item.rawTitle, item.author)
            if (!isSingleQualityYoutubeSong(item, parsedTitle, parsedArtist)) continue
            val song = mapYoutubeSearchItem(
              item = item,
              sourceTag = "YouTube",
              genreHint = "Pop"
            )
            val dedupeKey = "${normalizeForMatch(song.title)}::${normalizeArtistKey(song.artist)}"
            if (song.id !in collected && dedupeKey !in seenKeys) {
              collected[song.id] = song
              seenKeys.add(dedupeKey)
            }
          }
          collected.values.toList()
        }
        if (tracks.isNotEmpty()) {
          _rawTiktokViralNewPipe.value = tracks
        }
      } catch (ce: CancellationException) {
        throw ce
      } catch (_: Throwable) {
      } finally {
        _isLoadingTiktokViralNewPipe.value = false
      }
    }
  }

  fun loadNewAlbums(forceRefresh: Boolean = false) {
    if (!forceRefresh && _rawNewAlbums.value.isNotEmpty()) return
    if (_isLoadingNewAlbums.value) return
    viewModelScope.launch {
      _isLoadingNewAlbums.value = true
      try {
        val loaded = withContext(Dispatchers.IO) {
          val chartAlbums = runCatching {
            NetworkClient.appleChartsApi.getTopAlbums("us").feed?.results.orEmpty()
          }.getOrNull().orEmpty()

          val fromCharts = chartAlbums.mapNotNull { entry ->
            val colId = entry.id?.trim().orEmpty()
            val title = entry.name?.trim()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val artist = entry.artistName?.trim()?.takeIf { it.isNotBlank() } ?: "Unknown Artist"
            val art = entry.artworkUrl100
              ?.replace("100x100bb", "600x600bb")
              ?.replace("100x100", "600x600")
              .orEmpty()
            FeaturedAlbumItem(
              id = "itunes_album_${colId.ifBlank { computeAlbumId(title, artist) }}",
              collectionId = colId,
              title = title,
              artist = artist,
              artworkUrl = art,
              year = entry.releaseDate?.take(4).orEmpty(),
              source = "iTunes"
            )
          }

          if (fromCharts.isNotEmpty()) {
            fromCharts
          } else {
            val fallbackSearch = runCatching {
              NetworkClient.itunesApi.searchTracks(term = "new album hits 2026", limit = 25, country = "US")
            }.getOrNull()?.results.orEmpty()
            val seenCollections = HashSet<String>()
            fallbackSearch.mapNotNull { tr ->
              val colId = tr.collectionId?.toString().orEmpty()
              val colTitle = tr.collectionName?.trim()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
              val artist = tr.artistName?.trim()?.takeIf { it.isNotBlank() } ?: "Unknown Artist"
              val key = colId.ifBlank { computeAlbumId(colTitle, artist) }
              if (!seenCollections.add(key)) return@mapNotNull null
              val art = tr.artworkUrl100
                ?.replace("100x100bb", "600x600bb")
                .orEmpty()
              FeaturedAlbumItem(
                id = "itunes_album_$key",
                collectionId = colId,
                title = colTitle,
                artist = artist,
                artworkUrl = art,
                year = tr.releaseDate?.take(4).orEmpty(),
                source = "iTunes"
              )
            }
          }
        }
        if (loaded.isNotEmpty()) {
          _rawNewAlbums.value = loaded
        }
      } catch (ce: CancellationException) {
        throw ce
      } catch (_: Throwable) {
      } finally {
        _isLoadingNewAlbums.value = false
      }
    }
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
      loadPopularYoutubeSongs(forceRefresh = true)
      loadTiktokViralNewPipeSongs(forceRefresh = true)
    }
  }

  fun loadDailyRecommendations(
    category: DailyFeedCategory = _selectedDailyFeed.value,
    forceRefresh: Boolean = false
  ) {
    if (forceRefresh && _isLoadingFeatured.value) {
      return
    }
    if (forceRefresh) {
      loadPopularYoutubeSongs(forceRefresh = true)
      loadTiktokViralNewPipeSongs(forceRefresh = true)
    }
    val today = todayDateKey()
    if (today != lastFeedDateKey) {
      lastFeedDateKey = today
      dailyFeedCache.clear()
    }
    if (!forceRefresh) {
      _dailyUpdatedJustNow.value = false
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
            // Strictly from real iTunes / Apple Music viral & top chart tracks (no AI uploads)
            val chartHits = fetchAppleDailyChartTracks(
              sourceTag = "iTunes 30s",
              countries = listOf("us", "gb"),
              fallbackQueries = listOf("billboard hot 100", "today's hits", "pop hits 2026", "r&b hits")
            )
            if (chartHits.size >= 16) {
              chartHits
            } else {
              val extraHits = fetchItunesFeedTracks(
                queries = listOf("pop hits 2026", "billboard hot 100", "dance pop hits", "hip hop hits"),
                sourceTag = "iTunes 30s",
                countries = listOf("US", "GB"),
                targetCount = 24
              )
              (chartHits + extraHits).distinctBy { it.id }.take(24)
            }
          }
          DailyFeedCategory.DAILY_CHARTS -> {
            // Daily Top combined from iTunes + YouTube
            coroutineScope {
              val itunesDeferred = async {
                fetchAppleDailyChartTracks(
                  sourceTag = "iTunes 30s",
                  countries = listOf("us")
                )
              }
              val ytDeferred = async {
                fetchYoutubeFeedTracks(
                  queries = listOf(
                    "top global songs 2026 official audio",
                    "billboard hot 100 hits official audio"
                  ),
                  sourceTag = "YouTube",
                  limitPerQuery = 16
                )
              }
              interleaveTracks(itunesDeferred.await(), ytDeferred.await(), limit = 24)
            }
          }
          DailyFeedCategory.PHONK_EDITS -> {
            // Phonk strictly from full-length YouTube tracks
            fetchYoutubeFeedTracks(
              queries = listOf(
                "brazilian phonk official audio",
                "montagem phonk viral official audio",
                "drift phonk song official audio",
                "phonk house trending music"
              ),
              sourceTag = "YouTube",
              limitPerQuery = 18,
              genreHint = "Phonk"
            )
          }
          DailyFeedCategory.KHMER_ASIA -> {
            // Khmer & Asia combined from iTunes and YouTube
            coroutineScope {
              val itunesDeferred = async {
                fetchItunesFeedTracks(
                  queries = listOf("vannda", "suly pheng", "tena", "khmer pop", "asian pop hits"),
                  sourceTag = "iTunes 30s",
                  countries = listOf("US", "SG"),
                  targetCount = 14
                )
              }
              val ytDeferred = async {
                fetchYoutubeFeedTracks(
                  queries = listOf(
                    "vannda official audio",
                    "suly pheng new song official",
                    "khmer new song 2026 official audio",
                    "asian pop trending song official audio"
                  ),
                  sourceTag = "YouTube",
                  limitPerQuery = 14,
                  genreHint = "Pop"
                )
              }
              interleaveTracks(itunesDeferred.await(), ytDeferred.await(), limit = 24)
            }
          }
        }

        if (loaded.isNotEmpty()) {
          dailyFeedCache[cacheKey] = loaded
          _rawFeatured.value = loaded
          _featuredError.value = null
          if (forceRefresh) {
            _dailyUpdatedJustNow.value = true
          }
        } else {
          _rawFeatured.value = emptyList()
          _featuredError.value = getApplication<Application>().getString(R.string.home_refresh_error)
          _dailyUpdatedJustNow.value = false
        }
      } catch (ce: CancellationException) {
        throw ce
      } catch (_: Throwable) {
        _rawFeatured.value = emptyList()
        _featuredError.value = getApplication<Application>().getString(R.string.home_refresh_error)
        _dailyUpdatedJustNow.value = false
      } finally {
        _isLoadingFeatured.value = false
      }
    }
  }

  private fun observeSearchRequests() {
    viewModelScope.launch {
      _searchDispatch.collectLatest { dispatch ->
        val trimmed = dispatch.query.trim()
        if (trimmed.isEmpty()) {
          _rawSearchResults.value = emptyList()
          _searchError.value = null
          _isSearching.value = false
          _searchUiState.value = SearchUiState.Idle
          return@collectLatest
        }

        if (dispatch.debounceMs > 0L) {
          delay(dispatch.debounceMs)
        }

        _isSearching.value = true
        _searchError.value = null
        _searchUiState.value = SearchUiState.Loading(mode = dispatch.mode, query = trimmed)
        try {
          when (dispatch.mode) {
            SearchMode.YouTubeSearch -> {
              val isLink = (trimmed.contains("youtube.com", ignoreCase = true) || trimmed.contains("youtu.be", ignoreCase = true))
              val pastedVideoId = if (isLink) YoutubeExtractor.extractVideoId(trimmed) else null
              val mapped = if (!pastedVideoId.isNullOrBlank()) {
                val extracted = getOrExtractYoutubeData(pastedVideoId)
                val bestFmt = pickBestPlayableFormat(extracted?.audioFormats.orEmpty())
                if (extracted != null && bestFmt != null) {
                  val (parsedTitle, parsedArtist) = parseYoutubeTitleAndArtist(extracted.title, extracted.author)
                  StreamHeaderRegistry.register(bestFmt.streamUrl, bestFmt.requiredUserAgent)
                  listOf(
                    TrackMetadataClassifier.enrich(
                      Song(
                        id = "yt_$pastedVideoId",
                        title = parsedTitle.ifBlank { extracted.title },
                        artist = parsedArtist.ifBlank { extracted.author.ifBlank { "YouTube" } },
                        artworkUrl = extracted.thumbnailUrl,
                        durationSeconds = extracted.durationSeconds,
                        streamUrl = bestFmt.streamUrl,
                        source = "YouTube",
                        fullDurationSeconds = extracted.durationSeconds
                      )
                    )
                  )
                } else {
                  emptyList()
                }
              } else {
                YoutubeExtractor.searchSongs(query = trimmed, limit = 25).map { item ->
                  mapYoutubeSearchItem(item = item, sourceTag = "YouTube")
                }
              }
              _rawSearchResults.value = mapped
              val visible = mapped
                .filter { isTrackVisible(it, hiddenSongIds.value, blockedArtists.value) }
                .map { it.copy(isFavorite = it.id in favoriteIds.value) }
              _searchUiState.value = if (visible.isEmpty()) {
                SearchUiState.Empty
              } else {
                SearchUiState.Results(visible)
              }
            }
            SearchMode.ITunes30s -> {
              val response = withContext(Dispatchers.IO) {
                NetworkClient.itunesApi.searchTracks(term = trimmed, limit = 25, country = "US")
              }
              val mapped = mapItunesTracks(response.results, sourceTag = "iTunes 30s")
              _rawSearchResults.value = mapped
              val visible = mapped
                .filter { isTrackVisible(it, hiddenSongIds.value, blockedArtists.value) }
                .map { it.copy(isFavorite = it.id in favoriteIds.value) }
              _searchUiState.value = if (visible.isEmpty()) {
                SearchUiState.Empty
              } else {
                SearchUiState.Results(visible)
              }
            }
            SearchMode.YouTubeLink -> {
              val ytErr = _youtubeError.value
              val analysis = _youtubeAnalysis.value
              if (analysis != null) {
                val track = Song(
                  id = "yt_${analysis.videoId}",
                  title = analysis.matchedTrackTitle,
                  artist = analysis.matchedArtist,
                  artworkUrl = analysis.thumbnailUrl,
                  durationSeconds = analysis.durationSeconds,
                  streamUrl = analysis.originalUrl,
                  source = "Downloaded",
                  fullDurationSeconds = analysis.durationSeconds
                )
                _searchUiState.value = SearchUiState.Results(listOf(track))
              } else if (!ytErr.isNullOrBlank()) {
                _searchUiState.value = SearchUiState.Error("Search failed. Check your connection.")
              } else {
                _searchUiState.value = SearchUiState.Empty
              }
            }
          }
        } catch (ce: CancellationException) {
          throw ce
        } catch (_: Throwable) {
          _rawSearchResults.value = emptyList()
          val message = "Search failed. Check your connection."
          _searchError.value = message
          _searchUiState.value = SearchUiState.Error(message)
        } finally {
          _isSearching.value = false
        }
      }
    }
  }

  fun search(query: String, immediate: Boolean = false) {
    _searchQuery.value = query
    searchJob?.cancel()
    if (query.isBlank()) {
      _rawSearchResults.value = emptyList()
      _searchError.value = null
      _isSearching.value = false
      _searchUiState.value = SearchUiState.Idle
      _searchDispatch.tryEmit(SearchDispatch("", _searchModeEnum.value, 0L, ++searchDispatchNonce))
      return
    }
    _searchDispatch.tryEmit(
      SearchDispatch(
        query = query,
        mode = _searchModeEnum.value,
        debounceMs = if (immediate) 0L else 420L,
        nonce = ++searchDispatchNonce
      )
    )
  }

  fun retrySearch() {
    val q = _searchQuery.value
    if (q.isBlank()) {
      _searchUiState.value = SearchUiState.Idle
      return
    }
    search(q, immediate = true)
  }

  fun clearSearch() {
    search("")
  }

  fun playSong(song: Song, newQueue: List<Song> = _rawFeatured.value.ifEmpty { listOf(song) }) {
    if (song.streamUrl.isBlank()) {
      runCatching {
        Toast.makeText(getApplication(), "No playable stream URL for this track.", Toast.LENGTH_SHORT).show()
      }
      return
    }

    val resolvedQueue = if (newQueue.any { it.id == song.id }) newQueue else listOf(song) + newQueue
    _queue.value = resolvedQueue

    if (isUnresolvedYoutubeWatchUrl(song)) {
      playResolveJob?.cancel()
      _rawCurrentSong.value = song
      _resolvingSongId.value = song.id
      val videoId = song.id.removePrefix("yt_")
      playResolveJob = viewModelScope.launch {
        try {
          val extracted = getOrExtractYoutubeData(videoId)
          val bestFmt = pickBestPlayableFormat(extracted?.audioFormats.orEmpty())
          if (bestFmt == null) {
            _resolvingSongId.value = null
            runCatching {
              Toast.makeText(getApplication(), "Could not load YouTube audio stream. Try downloading a format.", Toast.LENGTH_SHORT).show()
            }
            return@launch
          }
          StreamHeaderRegistry.register(bestFmt.streamUrl, bestFmt.requiredUserAgent)
          val resolvedDur = extracted?.durationSeconds?.takeIf { it > 0L }
            ?: bestFmt.durationSeconds.takeIf { it > 0L }
            ?: song.durationSeconds
          val resolvedSong = song.copy(
            streamUrl = bestFmt.streamUrl,
            durationSeconds = resolvedDur,
            fullDurationSeconds = resolvedDur
          )
          val updatedQueue = resolvedQueue.map { if (it.id == resolvedSong.id) resolvedSong else it }
          _queue.value = updatedQueue
          _resolvingSongId.value = null
          retriedPlaybackIds.remove(resolvedSong.id)
          onTrackActivated(resolvedSong)

          val controller = mediaController
          if (controller == null) {
            pendingPlayRequest = resolvedSong to updatedQueue
          } else {
            startQueueOnController(controller, resolvedSong, updatedQueue)
          }
        } catch (ce: CancellationException) {
          _resolvingSongId.value = null
          throw ce
        } catch (_: Throwable) {
          _resolvingSongId.value = null
          runCatching {
            Toast.makeText(getApplication(), "Unable to stream this track right now.", Toast.LENGTH_SHORT).show()
          }
        }
      }
      return
    }

    playResolveJob?.cancel()
    _resolvingSongId.value = null
    retriedPlaybackIds.remove(song.id)
    onTrackActivated(song)

    val controller = mediaController
    if (controller == null) {
      pendingPlayRequest = song to resolvedQueue
      return
    }

    try {
      startQueueOnController(controller, song, resolvedQueue)
    } catch (_: Throwable) {
      runCatching {
        Toast.makeText(getApplication(), "Unable to play this track.", Toast.LENGTH_SHORT).show()
      }
    }
  }

  fun nextTrack(fromNaturalEnd: Boolean = false) {
    val current = _rawCurrentSong.value
    if (!fromNaturalEnd && current != null) {
      val snap = playSessionTracker.currentSessionSnapshot()
      val totalDur = _durationMs.value.coerceAtLeast(current.durationSeconds * 1000L).coerceAtLeast(1L)
      val pct = (snap?.listenedDurationMs ?: 0L).toFloat() / totalDur.toFloat()
      if (pct < 0.50f) {
        recordActiveSessionOutcome(skipped = true)
      } else if (pct >= 0.78f) {
        recordActiveSessionOutcome(skipped = false)
      }
    }

    val q = _queue.value.ifEmpty { catalogTracks.value }
    if (_shuffleEnabled.value && moodFlowEnabled.value && current != null && q.isNotEmpty()) {
      val candidatePool = if (q.size > 1) q else catalogTracks.value.ifEmpty { q }
      val pick = MoodFlowEngine.pickNextSong(
        currentSong = current,
        allSongs = candidatePool,
        playHistory = richPlayHistory.value,
        favoriteIds = favoriteIds.value,
        recentPlayedSongs = recentlyPlayed.value
      )
      if (pick != null) {
        _moodFlowCurrentPick.value = pick
        val nextQueue = if (q.any { it.id == pick.song.id }) q else q + pick.song
        playSong(pick.song, nextQueue)
        return
      }
    }

    val curIdx = q.indexOfFirst { it.id == current?.id }.coerceAtLeast(0)
    val nextQueueSong = q.getOrNull(curIdx + 1)
    if (nextQueueSong != null && isUnresolvedYoutubeWatchUrl(nextQueueSong)) {
      playSong(nextQueueSong, q)
      return
    }

    val controller = mediaController
    if (controller != null && controller.hasNextMediaItem()) {
      controller.seekToNextMediaItem()
      if (controller.playbackState == Player.STATE_IDLE) {
        controller.prepare()
      }
      controller.play()
      return
    }
    if (q.isEmpty()) return
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
      if (controller.playbackState == Player.STATE_IDLE) {
        controller.prepare()
      }
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
    mediaController?.shuffleModeEnabled = next && !moodFlowEnabled.value
  }

  fun toggleMoodFlow() {
    val next = !moodFlowEnabled.value
    viewModelScope.launch {
      prefsManager.setMoodFlowEnabled(next)
    }
    if (next) {
      _shuffleEnabled.value = true
      mediaController?.shuffleModeEnabled = false
      val cur = _rawCurrentSong.value
      val pool = _queue.value.ifEmpty { catalogTracks.value }
      if (cur != null && pool.size > 1) {
        val pick = MoodFlowEngine.pickNextSong(
          currentSong = cur,
          allSongs = pool,
          playHistory = richPlayHistory.value,
          favoriteIds = favoriteIds.value,
          recentPlayedSongs = recentlyPlayed.value
        )
        if (pick != null) {
          _moodFlowCurrentPick.value = pick
        }
      }
    } else {
      mediaController?.shuffleModeEnabled = _shuffleEnabled.value
    }
  }

  fun toggleRepeat() {
    val next = !_repeatEnabled.value
    _repeatEnabled.value = next
    mediaController?.repeatMode = if (next) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_ALL
  }

  fun setPlaybackSpeed(speed: Float) {
    val clamped = speed.coerceIn(0.5f, 2.0f)
    _playbackSpeed.value = clamped
    mediaController?.playbackParameters = PlaybackParameters(clamped)
  }

  fun cyclePlaybackSpeed() {
    val nextSpeed = when {
      _playbackSpeed.value < 0.85f -> 0.9f
      _playbackSpeed.value < 0.95f -> 1.0f
      _playbackSpeed.value < 1.1f -> 1.15f
      _playbackSpeed.value < 1.2f -> 1.25f
      _playbackSpeed.value < 1.4f -> 1.5f
      else -> 0.75f
    }
    setPlaybackSpeed(nextSpeed)
  }

  fun cycleSleepTimer() {
    val nextMin = when {
      _sleepEndOfTrack.value -> 0
      _sleepMinutes.value == 0 -> 15
      _sleepMinutes.value == 15 -> 30
      _sleepMinutes.value == 30 -> 45
      _sleepMinutes.value == 45 -> 60
      else -> 0
    }
    setSleepTimerMinutes(nextMin)
  }

  fun setSleepTimerMinutes(minutes: Int) {
    sleepTimerJob?.cancel()
    _sleepEndOfTrack.value = false
    if (minutes <= 0) {
      _sleepMinutes.value = 0
      _sleepRemainingSeconds.value = 0L
      mediaController?.volume = 1f
      return
    }
    _sleepMinutes.value = minutes
    val totalSeconds = minutes * 60L
    _sleepRemainingSeconds.value = totalSeconds
    mediaController?.volume = 1f
    sleepTimerJob = viewModelScope.launch {
      var remaining = totalSeconds
      while (remaining > 0L) {
        delay(1_000L)
        remaining -= 1L
        _sleepRemainingSeconds.value = remaining
        if (remaining in 1L..8L) {
          val fadeVol = (remaining / 8f).coerceIn(0.05f, 1f)
          mediaController?.volume = fadeVol
        }
      }
      mediaController?.let { controller ->
        controller.pause()
        controller.volume = 1f
      }
      _sleepMinutes.value = 0
      _sleepRemainingSeconds.value = 0L
    }
  }

  fun setSleepEndOfTrack() {
    sleepTimerJob?.cancel()
    _sleepMinutes.value = 0
    _sleepRemainingSeconds.value = 0L
    _sleepEndOfTrack.value = true
    mediaController?.volume = 1f
  }

  fun cancelSleepTimer() {
    sleepTimerJob?.cancel()
    _sleepMinutes.value = 0
    _sleepRemainingSeconds.value = 0L
    _sleepEndOfTrack.value = false
    mediaController?.volume = 1f
  }

  fun playNext(song: Song) {
    if (song.streamUrl.isBlank()) {
      Toast.makeText(getApplication(), "No playable stream URL for this track.", Toast.LENGTH_SHORT).show()
      return
    }
    val active = _rawCurrentSong.value
    val currentQueue = _queue.value
    if (active == null || currentQueue.isEmpty()) {
      playSong(song, listOf(song))
      return
    }
    if (active.id == song.id) {
      val appCtx = localizedAppContext()
      Toast.makeText(appCtx, appCtx.getString(R.string.queue_added_next_toast, song.title), Toast.LENGTH_SHORT).show()
      return
    }
    val cleaned = currentQueue.filterNot { it.id == song.id }
    val activeIdx = cleaned.indexOfFirst { it.id == active.id }.coerceAtLeast(0)
    val insertIdx = (activeIdx + 1).coerceAtMost(cleaned.size)
    val updated = cleaned.toMutableList().apply { add(insertIdx, song) }
    _queue.value = updated

    if (!isUnresolvedYoutubeWatchUrl(song)) {
      mediaController?.let { controller ->
        for (i in controller.mediaItemCount - 1 downTo 0) {
          if (controller.getMediaItemAt(i).mediaId == song.id && i != controller.currentMediaItemIndex) {
            controller.removeMediaItem(i)
          }
        }
        val targetControllerIdx = (controller.currentMediaItemIndex + 1).coerceIn(0, controller.mediaItemCount)
        controller.addMediaItem(targetControllerIdx, buildMediaItem(song))
      }
    }
    val appCtx = localizedAppContext()
    Toast.makeText(appCtx, appCtx.getString(R.string.queue_added_next_toast, song.title), Toast.LENGTH_SHORT).show()
  }

  fun addToQueue(song: Song) {
    if (song.streamUrl.isBlank()) {
      Toast.makeText(getApplication(), "No playable stream URL for this track.", Toast.LENGTH_SHORT).show()
      return
    }
    val active = _rawCurrentSong.value
    val currentQueue = _queue.value
    if (active == null || currentQueue.isEmpty()) {
      playSong(song, listOf(song))
      return
    }
    val cleaned = if (active.id == song.id) currentQueue else currentQueue.filterNot { it.id == song.id }
    val updated = if (active.id == song.id) cleaned else cleaned + song
    _queue.value = updated

    if (active.id != song.id && !isUnresolvedYoutubeWatchUrl(song)) {
      mediaController?.let { controller ->
        for (i in controller.mediaItemCount - 1 downTo 0) {
          if (controller.getMediaItemAt(i).mediaId == song.id && i != controller.currentMediaItemIndex) {
            controller.removeMediaItem(i)
          }
        }
        controller.addMediaItem(buildMediaItem(song))
      }
    }
    val appCtx = localizedAppContext()
    Toast.makeText(appCtx, appCtx.getString(R.string.queue_added_end_toast, song.title), Toast.LENGTH_SHORT).show()
  }

  fun jumpToQueueIndex(index: Int) {
    val currentQueue = _queue.value
    val target = currentQueue.getOrNull(index) ?: return
    val controller = mediaController
    if (controller != null && !isUnresolvedYoutubeWatchUrl(target)) {
      var controllerIdx = -1
      for (i in 0 until controller.mediaItemCount) {
        if (controller.getMediaItemAt(i).mediaId == target.id) {
          controllerIdx = i
          break
        }
      }
      if (controllerIdx >= 0) {
        controller.volume = 1f
        controller.seekTo(controllerIdx, 0L)
        if (controller.playbackState == Player.STATE_IDLE) {
          controller.prepare()
        }
        controller.play()
        return
      }
    }
    playSong(target, currentQueue)
  }

  fun moveQueueItem(fromIndex: Int, toIndex: Int) {
    val currentQueue = _queue.value.toMutableList()
    if (fromIndex !in currentQueue.indices || toIndex !in currentQueue.indices || fromIndex == toIndex) return
    val fromSong = currentQueue[fromIndex]
    val toSong = currentQueue[toIndex]
    val item = currentQueue.removeAt(fromIndex)
    currentQueue.add(toIndex, item)
    _queue.value = currentQueue

    mediaController?.let { controller ->
      var cFrom = -1
      var cTo = -1
      for (i in 0 until controller.mediaItemCount) {
        val mid = controller.getMediaItemAt(i).mediaId
        if (mid == fromSong.id) cFrom = i
        if (mid == toSong.id) cTo = i
      }
      if (cFrom >= 0 && cTo >= 0 && cFrom != cTo) {
        controller.moveMediaItem(cFrom, cTo)
      }
    }
  }

  fun removeQueueItem(index: Int) {
    val currentQueue = _queue.value
    val target = currentQueue.getOrNull(index) ?: return
    if (target.id == _rawCurrentSong.value?.id) return
    val updated = currentQueue.toMutableList().apply { removeAt(index) }
    _queue.value = updated
    mediaController?.let { controller ->
      for (i in controller.mediaItemCount - 1 downTo 0) {
        if (controller.getMediaItemAt(i).mediaId == target.id) {
          controller.removeMediaItem(i)
        }
      }
    }
  }

  fun clearUpcomingQueue() {
    val currentQueue = _queue.value
    val active = _rawCurrentSong.value ?: return
    val activeIdx = currentQueue.indexOfFirst { it.id == active.id }.coerceAtLeast(0)
    val updated = currentQueue.take(activeIdx + 1)
    _queue.value = updated
    mediaController?.let { controller ->
      val currentControllerIdx = controller.currentMediaItemIndex
      for (i in controller.mediaItemCount - 1 downTo (currentControllerIdx + 1).coerceAtLeast(0)) {
        controller.removeMediaItem(i)
      }
    }
  }

  fun togglePlayPause() {
    val controller = mediaController ?: return
    if (controller.isPlaying) {
      controller.pause()
    } else {
      if (_sleepRemainingSeconds.value == 0L || _sleepRemainingSeconds.value > 8L) {
        controller.volume = 1f
      }
      if (controller.playbackState == Player.STATE_IDLE && controller.mediaItemCount > 0) {
        controller.prepare()
      } else if (controller.playbackState == Player.STATE_ENDED) {
        controller.seekTo(0L)
      }
      controller.play()
    }
  }

  fun onAlbumTrackClick(track: Song, tracks: List<Song>) {
    if (_rawCurrentSong.value?.id == track.id) {
      togglePlayPause()
    } else {
      playSong(track, tracks)
    }
  }

  fun playAlbumFromStart(tracks: List<Song>) {
    val first = tracks.firstOrNull() ?: return
    _shuffleEnabled.value = false
    mediaController?.shuffleModeEnabled = false
    playSong(first, tracks)
  }

  fun shuffleAlbum(tracks: List<Song>) {
    if (tracks.isEmpty()) return
    _shuffleEnabled.value = true
    mediaController?.shuffleModeEnabled = !moodFlowEnabled.value
    val activeSong = _rawCurrentSong.value
    val starter = if (moodFlowEnabled.value && activeSong != null && tracks.size > 1) {
      val pick = MoodFlowEngine.pickNextSong(
        currentSong = activeSong,
        allSongs = tracks,
        playHistory = richPlayHistory.value,
        favoriteIds = favoriteIds.value,
        recentPlayedSongs = recentlyPlayed.value
      )
      if (pick != null) {
        _moodFlowCurrentPick.value = pick
        pick.song
      } else {
        tracks.random()
      }
    } else {
      tracks.random()
    }
    playSong(starter, tracks)
  }

  fun openAlbum(albumId: String? = null) {
    remoteAlbumJob?.cancel()
    _isShowingRemoteAlbum.value = false
    _remoteAlbumInfo.value = null
    _selectedAlbumId.value = albumId
  }

  fun openFeaturedAlbum(item: FeaturedAlbumItem) {
    if (item.localAlbumId != null) {
      openAlbum(item.localAlbumId)
      return
    }
    remoteAlbumJob?.cancel()
    _isShowingRemoteAlbum.value = true
    _remoteAlbumInfo.value = AlbumInfo(
      albumId = item.id,
      title = item.title,
      artist = item.artist,
      year = item.year,
      coverUrl = item.artworkUrl.ifBlank { null },
      automaticCoverUrl = item.artworkUrl.ifBlank { null },
      tracks = emptyList()
    )
    remoteAlbumJob = viewModelScope.launch {
      try {
        val tracks = withContext(Dispatchers.IO) {
          val byLookup = if (item.collectionId.isNotBlank()) {
            runCatching {
              NetworkClient.itunesApi.lookupAlbumTracks(collectionId = item.collectionId, limit = 25)
            }.getOrNull()?.results.orEmpty()
          } else emptyList()
          val mapped = mapItunesTracks(byLookup, sourceTag = "iTunes 30s", albumOverride = item.title)
          if (mapped.isNotEmpty()) {
            mapped
          } else {
            val bySearch = runCatching {
              NetworkClient.itunesApi.searchTracks(term = "${item.artist} ${item.title}", limit = 15)
            }.getOrNull()?.results.orEmpty()
            mapItunesTracks(bySearch, sourceTag = "iTunes 30s", albumOverride = item.title)
          }
        }
        _remoteAlbumInfo.value = _remoteAlbumInfo.value?.copy(tracks = tracks)
      } catch (ce: CancellationException) {
        throw ce
      } catch (_: Throwable) {
      }
    }
  }

  fun createAlbum(title: String, artist: String = "", year: String = ""): String? {
    val cleanTitle = title.trim()
    if (cleanTitle.isBlank()) return null
    val cleanArtist = artist.trim().ifBlank { "Unknown Artist" }
    val cleanYear = year.trim()
    val albumId = computeAlbumId(cleanTitle, cleanArtist)
    val appContext = getApplication<Application>()
    viewModelScope.launch {
      withContext(Dispatchers.IO) {
        dao.unmarkAlbumDeleted(albumId)
        dao.upsertCustomAlbum(
          CustomAlbumEntity(
            albumId = albumId,
            title = cleanTitle,
            artist = cleanArtist,
            year = cleanYear
          )
        )
      }
      _selectedAlbumId.value = albumId
      Toast.makeText(appContext, "Created album \"$cleanTitle\"", Toast.LENGTH_SHORT).show()
    }
    return albumId
  }

  fun createAlbumWithSong(title: String, artist: String = "", year: String = "", song: Song): String? {
    val cleanTitle = title.trim()
    if (cleanTitle.isBlank()) return null
    val cleanArtist = artist.trim().ifBlank { song.artist.ifBlank { "Unknown Artist" } }
    val cleanYear = year.trim()
    val albumId = computeAlbumId(cleanTitle, cleanArtist)
    val appContext = getApplication<Application>()
    viewModelScope.launch {
      withContext(Dispatchers.IO) {
        dao.unmarkAlbumDeleted(albumId)
        dao.upsertCustomAlbum(
          CustomAlbumEntity(
            albumId = albumId,
            title = cleanTitle,
            artist = cleanArtist,
            year = cleanYear
          )
        )
        dao.insertAlbumTracks(
          listOf(
            AlbumTrackEntity(
              albumId = albumId,
              songId = song.id,
              title = song.title,
              artist = song.artist.ifBlank { cleanArtist },
              artworkUrl = song.artworkUrl,
              durationSeconds = song.fullDurationSeconds.takeIf { it > 0L } ?: song.durationSeconds,
              streamUrl = song.streamUrl,
              source = song.source,
              genre = song.genre,
              bpm = song.bpm,
              mood = song.mood,
              albumName = cleanTitle,
              trackNumber = song.trackNumber,
              year = cleanYear.ifBlank { song.year }
            )
          )
        )
      }
      Toast.makeText(appContext, "Added \"${song.title}\" to \"$cleanTitle\"", Toast.LENGTH_SHORT).show()
    }
    return albumId
  }

  fun addSongToAlbum(albumId: String, song: Song) {
    addSongsToAlbum(albumId, listOf(song))
  }

  fun addSongsToAlbum(albumId: String, songs: List<Song>) {
    if (songs.isEmpty()) return
    val appContext = getApplication<Application>()
    val albumTitle = libraryAlbums.value.firstOrNull { it.albumId == albumId }?.title
      ?: songs.firstOrNull()?.album?.ifBlank { "Album" }
      ?: "Album"
    val albumArtist = libraryAlbums.value.firstOrNull { it.albumId == albumId }?.artist
      ?: songs.firstOrNull()?.artist?.ifBlank { "Unknown Artist" }
      ?: "Unknown Artist"
    val albumYear = libraryAlbums.value.firstOrNull { it.albumId == albumId }?.year.orEmpty()
    viewModelScope.launch {
      withContext(Dispatchers.IO) {
        dao.unmarkAlbumDeleted(albumId)
        if (dao.getCustomAlbum(albumId) == null) {
          dao.upsertCustomAlbum(
            CustomAlbumEntity(
              albumId = albumId,
              title = albumTitle,
              artist = albumArtist,
              year = albumYear
            )
          )
        }
        val baseTime = System.currentTimeMillis()
        val entries = songs.mapIndexed { idx, song ->
          AlbumTrackEntity(
            albumId = albumId,
            songId = song.id,
            title = song.title,
            artist = song.artist.ifBlank { "Unknown Artist" },
            artworkUrl = song.artworkUrl,
            durationSeconds = song.fullDurationSeconds.takeIf { it > 0L } ?: song.durationSeconds,
            streamUrl = song.streamUrl,
            source = song.source,
            genre = song.genre,
            bpm = song.bpm,
            mood = song.mood,
            albumName = albumTitle,
            trackNumber = song.trackNumber,
            year = song.year,
            addedAt = baseTime + idx
          )
        }
        dao.insertAlbumTracks(entries)
      }
      val msg = if (songs.size == 1) {
        "Added \"${songs.first().title}\" to \"$albumTitle\""
      } else {
        "Added ${songs.size} songs to \"$albumTitle\""
      }
      Toast.makeText(appContext, msg, Toast.LENGTH_SHORT).show()
    }
  }

  fun removeSongFromAlbum(album: AlbumInfo, song: Song) {
    val appContext = getApplication<Application>()
    viewModelScope.launch {
      withContext(Dispatchers.IO) {
        dao.deleteAlbumTrack(album.albumId, song.id)
        val local = dao.getLocalSongById(song.id)
        if (local != null) {
          val localAlbumId = computeAlbumId(
            albumName = local.album.ifBlank { "Unknown Album" },
            artistName = local.artist.ifBlank { "Unknown Artist" }
          )
          if (localAlbumId == album.albumId) {
            dao.updateLocalSongMetadata(
              songId = local.id,
              title = local.title,
              artist = local.artist,
              album = "",
              trackNumber = local.trackNumber,
              year = local.year,
              artworkUrl = local.artworkUrl,
              durationSeconds = local.durationSeconds
            )
          }
        }
      }
      Toast.makeText(appContext, "Removed \"${song.title}\" from \"${album.title}\"", Toast.LENGTH_SHORT).show()
    }
  }

  fun deleteAlbum(album: AlbumInfo, alsoDeleteLocalSongs: Boolean = false) {
    val appContext = getApplication<Application>()
    viewModelScope.launch {
      withContext(Dispatchers.IO) {
        val prevCover = dao.getAlbumCover(album.albumId)
        AlbumCoverStorage.deleteManagedAlbumCover(appContext, prevCover?.coverPath)
        dao.deleteAlbumCover(album.albumId)
        dao.deleteTracksForAlbum(album.albumId)
        dao.deleteCustomAlbum(album.albumId)
        dao.markAlbumDeleted(DeletedAlbumEntity(albumId = album.albumId))

        if (alsoDeleteLocalSongs) {
          album.tracks.forEach { song ->
            val local = dao.getLocalSongById(song.id)
            if (local != null) {
              SongDownloader.deleteAppManagedFileIfOwned(appContext, song.streamUrl)
              SongDownloader.deleteAppManagedFileIfOwned(appContext, song.artworkUrl)
              dao.deleteLocalSong(song.id)
              dao.deleteAlbumTracksForSong(song.id)
              dao.deleteHistoryBySongId(song.id)
              dao.deletePlayLogsBySongId(song.id)
              dao.deleteFavorite(song.id)
              dao.deleteLyricOffset(song.id)
            }
          }
        }
      }
      AlbumBannerCache.clear()
      if (_selectedAlbumId.value == album.albumId) {
        _selectedAlbumId.value = null
      }
      Toast.makeText(appContext, "Deleted \"${album.title}\"", Toast.LENGTH_SHORT).show()
    }
  }

  fun saveAlbumCoverPhoto(
    albumId: String,
    imageSource: String,
    offsetXFraction: Float = 0f,
    offsetYFraction: Float = 0f,
    zoom: Float = 1f
  ) {
    val appContext = getApplication<Application>()
    viewModelScope.launch {
      val prev = dao.getAlbumCover(albumId)
      val result = AlbumCoverStorage.renderAndSavePhotoCover(
        context = appContext,
        albumId = albumId,
        imageSource = imageSource,
        offsetXFraction = offsetXFraction,
        offsetYFraction = offsetYFraction,
        zoom = zoom
      )
      result.onSuccess { savedPath ->
        AlbumCoverStorage.deleteManagedAlbumCover(appContext, prev?.coverPath)
        AlbumBannerCache.clear()
        dao.upsertAlbumCover(
          AlbumCoverEntity(
            albumId = albumId,
            coverPath = savedPath,
            mode = CustomCoverMode.PHOTO.id,
            sourceUris = imageSource
          )
        )
        Toast.makeText(appContext, "Album cover saved", Toast.LENGTH_SHORT).show()
      }.onFailure {
        Toast.makeText(appContext, "Unable to save album cover", Toast.LENGTH_SHORT).show()
      }
    }
  }

  fun saveAlbumCoverMix(albumId: String, imageSources: List<String>) {
    val appContext = getApplication<Application>()
    viewModelScope.launch {
      val prev = dao.getAlbumCover(albumId)
      val clean = imageSources.filter { it.isNotBlank() }.take(4)
      val result = AlbumCoverStorage.renderAndSaveMixCover(
        context = appContext,
        albumId = albumId,
        imageSources = clean
      )
      result.onSuccess { savedPath ->
        AlbumCoverStorage.deleteManagedAlbumCover(appContext, prev?.coverPath)
        AlbumBannerCache.clear()
        dao.upsertAlbumCover(
          AlbumCoverEntity(
            albumId = albumId,
            coverPath = savedPath,
            mode = CustomCoverMode.MIX.id,
            sourceUris = clean.joinToString("\n")
          )
        )
        Toast.makeText(appContext, "Mix cover saved", Toast.LENGTH_SHORT).show()
      }.onFailure {
        Toast.makeText(appContext, "Unable to save mix cover", Toast.LENGTH_SHORT).show()
      }
    }
  }

  fun saveAlbumCoverAppIcon(albumId: String) {
    val appContext = getApplication<Application>()
    viewModelScope.launch {
      val prev = dao.getAlbumCover(albumId)
      val result = AlbumCoverStorage.renderAndSaveAppIconCover(
        context = appContext,
        albumId = albumId
      )
      result.onSuccess { savedPath ->
        AlbumCoverStorage.deleteManagedAlbumCover(appContext, prev?.coverPath)
        AlbumBannerCache.clear()
        dao.upsertAlbumCover(
          AlbumCoverEntity(
            albumId = albumId,
            coverPath = savedPath,
            mode = CustomCoverMode.APP_ICON.id,
            sourceUris = ""
          )
        )
        Toast.makeText(appContext, "App icon cover saved", Toast.LENGTH_SHORT).show()
      }.onFailure {
        Toast.makeText(appContext, "Unable to save app icon cover", Toast.LENGTH_SHORT).show()
      }
    }
  }

  fun resetAlbumCover(albumId: String) {
    val appContext = getApplication<Application>()
    viewModelScope.launch {
      val prev = dao.getAlbumCover(albumId)
      AlbumCoverStorage.deleteManagedAlbumCover(appContext, prev?.coverPath)
      dao.deleteAlbumCover(albumId)
      AlbumBannerCache.clear()
      Toast.makeText(appContext, "Album cover reset", Toast.LENGTH_SHORT).show()
    }
  }

  fun toggleAlbumInLibrary(tracks: List<Song>, albumTitle: String = "Album") {
    if (tracks.isEmpty()) return
    val appContext = getApplication<Application>()
    viewModelScope.launch {
      val currentFavs = favoriteIds.value
      val allAdded = tracks.all { it.id in currentFavs }
      if (allAdded) {
        tracks.forEach { dao.deleteFavorite(it.id) }
        Toast.makeText(appContext, "Removed \"$albumTitle\" from library", Toast.LENGTH_SHORT).show()
      } else {
        tracks.forEach { song ->
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
        Toast.makeText(appContext, "Added \"$albumTitle\" to library", Toast.LENGTH_SHORT).show()
      }
    }
  }

  fun downloadAlbum(context: Context, tracks: List<Song>) {
    val pending = tracks.filter {
      it.streamUrl.isNotBlank() &&
        it.id !in downloadedSongIds.value &&
        !songDownloadProgress.value.containsKey(it.id)
    }
    if (pending.isEmpty()) {
      val msg = if (tracks.any { it.streamUrl.isNotBlank() }) {
        "Album tracks are already saved"
      } else {
        "No downloadable audio stream available yet"
      }
      Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
      return
    }
    pending.forEach { song -> downloadSong(context, song) }
  }

  fun setMostPlayedFilter(filter: MostPlayedFilter) {
    _mostPlayedFilter.value = filter
  }

  fun refreshDiscoverWeekly() {
    viewModelScope.launch {
      analyticsRepository.ensureDiscoverWeekly(
        playLogs = qualifiedPlayLogs.value,
        catalog = catalogTracks.value,
        existingSnapshot = rawDiscoverWeekly.value,
        forceRefresh = true
      )
    }
  }

  fun getSimilarSongs(song: Song): List<SimilarTrack> {
    return RecommendationEngine.similarSongs(
      target = song,
      catalog = catalogTracks.value,
      limit = 6
    )
  }

  fun playPlaylist(tracks: List<Song>, startIndex: Int = 0) {
    val target = tracks.getOrNull(startIndex) ?: tracks.firstOrNull() ?: return
    playSong(target, tracks)
  }

  fun refreshPersonalMixes() {
    _mixRefreshSalt.value++
    val appContext = getApplication<Application>()
    Toast.makeText(appContext, appContext.getString(R.string.mix_refreshed_toast), Toast.LENGTH_SHORT).show()
  }

  fun saveMixAsPlaylist(title: String, tracks: List<Song>) {
    val cleanTitle = title.trim()
    if (cleanTitle.isBlank() || tracks.isEmpty()) return
    val appContext = getApplication<Application>()
    val albumId = computeAlbumId(cleanTitle, "AURIX Mix")
    viewModelScope.launch {
      withContext(Dispatchers.IO) {
        dao.unmarkAlbumDeleted(albumId)
        dao.upsertCustomAlbum(
          CustomAlbumEntity(
            albumId = albumId,
            title = cleanTitle,
            artist = tracks.map { it.artist }.filter { it.isNotBlank() }.distinct().take(2)
              .joinToString(", ").ifBlank { "AURIX Mix" },
            year = ""
          )
        )
        val baseTime = System.currentTimeMillis()
        val entries = tracks.mapIndexed { idx, song ->
          AlbumTrackEntity(
            albumId = albumId,
            songId = song.id,
            title = song.title,
            artist = song.artist.ifBlank { "Unknown Artist" },
            artworkUrl = song.artworkUrl,
            durationSeconds = song.fullDurationSeconds.takeIf { it > 0L } ?: song.durationSeconds,
            streamUrl = song.streamUrl,
            source = song.source,
            genre = song.genre,
            bpm = song.bpm,
            mood = song.mood,
            albumName = cleanTitle,
            trackNumber = idx + 1,
            year = song.year,
            addedAt = baseTime + idx
          )
        }
        dao.insertAlbumTracks(entries)
      }
      Toast.makeText(
        appContext,
        appContext.getString(R.string.mix_saved_toast, cleanTitle),
        Toast.LENGTH_SHORT
      ).show()
    }
  }

  fun shareMix(context: Context, title: String, subtitle: String, tracks: List<Song>) {
    if (tracks.isEmpty()) return
    val preview = tracks.take(8).mapIndexed { idx, s ->
      "${idx + 1}. ${s.title} — ${s.artist}"
    }.joinToString("\n")
    val shareBody = buildString {
      appendLine(title)
      if (subtitle.isNotBlank()) appendLine(subtitle)
      appendLine()
      append(preview)
      if (tracks.size > 8) {
        append("\n+${tracks.size - 8} more on AURIX")
      }
    }
    val sendIntent = Intent(Intent.ACTION_SEND).apply {
      type = "text/plain"
      putExtra(Intent.EXTRA_SUBJECT, title)
      putExtra(Intent.EXTRA_TEXT, shareBody)
    }
    val chooser = Intent.createChooser(sendIntent, title).apply {
      if (context !is android.app.Activity) {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
    }
    runCatching { context.startActivity(chooser) }
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
    importLocalSongs(listOf(uri))
  }

  fun importLocalSongs(uris: List<Uri>, targetAlbum: AlbumInfo? = null) {
    if (uris.isEmpty()) return
    val appContext = getApplication<Application>()
    viewModelScope.launch {
      var addedCount = 0
      var linkedToTargetCount = 0
      var firstAddedTitle: String? = null
      var firstAlbumId: String? = null

      withContext(Dispatchers.IO) {
        if (targetAlbum != null) {
          dao.unmarkAlbumDeleted(targetAlbum.albumId)
          if (dao.getCustomAlbum(targetAlbum.albumId) == null) {
            dao.upsertCustomAlbum(
              CustomAlbumEntity(
                albumId = targetAlbum.albumId,
                title = targetAlbum.title,
                artist = targetAlbum.artist,
                year = targetAlbum.year
              )
            )
          }
        }

        for (uri in uris) {
          try {
            appContext.contentResolver.takePersistableUriPermission(
              uri,
              Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
          } catch (_: Exception) { }

          val existing = dao.getLocalSongById(uri.toString())
          val entity = if (existing != null) {
            existing
          } else {
            val meta = AudioMetadataReader.extractFromUri(
              context = appContext,
              uri = uri,
              songId = uri.toString()
            )
            val created = LocalSongEntity(
              id = uri.toString(),
              title = meta.title,
              artist = meta.artist,
              artworkUrl = meta.embeddedArtworkUri,
              durationSeconds = meta.durationSeconds,
              streamUrl = uri.toString(),
              source = "Local",
              album = targetAlbum?.title ?: meta.album,
              trackNumber = meta.trackNumber,
              year = meta.year.ifBlank { targetAlbum?.year.orEmpty() }
            )
            dao.insertLocalSong(created)
            dao.unmarkAlbumDeleted(computeAlbumId(created.album, created.artist))
            addedCount++
            if (firstAddedTitle == null) {
              firstAddedTitle = created.title
              firstAlbumId = targetAlbum?.albumId ?: computeAlbumId(created.album, created.artist)
            }
            created
          }

          if (targetAlbum != null) {
            dao.insertAlbumTracks(
              listOf(
                AlbumTrackEntity(
                  albumId = targetAlbum.albumId,
                  songId = entity.id,
                  title = entity.title,
                  artist = entity.artist.ifBlank { "Unknown Artist" },
                  artworkUrl = entity.artworkUrl,
                  durationSeconds = entity.durationSeconds,
                  streamUrl = entity.streamUrl,
                  source = entity.source,
                  albumName = targetAlbum.title,
                  trackNumber = entity.trackNumber,
                  year = entity.year
                )
              )
            )
            linkedToTargetCount++
          }
        }
      }

      if (targetAlbum != null && linkedToTargetCount > 0) {
        Toast.makeText(
          appContext,
          "Added $linkedToTargetCount song${if (linkedToTargetCount == 1) "" else "s"} to \"${targetAlbum.title}\"",
          Toast.LENGTH_SHORT
        ).show()
      } else if (addedCount > 0) {
        if (_selectedAlbumId.value == null && firstAlbumId != null) {
          _selectedAlbumId.value = firstAlbumId
        }
        val msg = if (addedCount == 1) {
          "Imported \"$firstAddedTitle\" to My songs"
        } else {
          "Imported $addedCount audio files"
        }
        Toast.makeText(appContext, msg, Toast.LENGTH_SHORT).show()
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
        dao.updatePlayLogArtwork(songId, savedCoverUri)

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
        dao.deleteAlbumTracksForSong(song.id)
        dao.deleteHistoryBySongId(song.id)
        dao.deletePlayLogsBySongId(song.id)
        dao.deleteFavorite(song.id)
        dao.deleteLyricOffset(song.id)
      }

      _localDeletedOrAddedIds.value = _localDeletedOrAddedIds.value - song.id
      val dlState = _youtubeDownloadState.value
      if (dlState is YoutubeDownloadState.Saved && dlState.song.id == song.id) {
        _youtubeDownloadState.value = YoutubeDownloadState.Idle
        BackgroundDownloadRegistry.clearVideoDownloadState(song.id)
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
          playSessionTracker.clearSession()
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
        dao.deletePlayLogsBySongId(song.id)
      }
      Toast.makeText(appContext, "Removed \"${song.title}\" from Recent", Toast.LENGTH_SHORT).show()
    }
  }

  fun clearHistory() {
    val appContext = getApplication<Application>()
    viewModelScope.launch {
      withContext(Dispatchers.IO) {
        dao.clearPlayHistory()
        dao.clearPlayLogs()
        dao.clearDiscoverWeekly()
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

        val cached = runCatching { dao.getLyricsCache(song.id) }.getOrNull() 
          ?: runCatching { dao.getLyricsCacheByKey(songKey) }.getOrNull()?.also { keyHit ->
            if (!keyHit.isReportedBad && keyHit.songId != song.id) {
              runCatching { dao.insertLyricsCache(keyHit.copy(songId = song.id)) }
            }
          }
        if (cached != null && !cached.isReportedBad) {
          return@launch
        }

        lyricsCheckSemaphore.withPermit {
          val latestCached = runCatching { dao.getLyricsCache(song.id) }.getOrNull()
          if (latestCached != null && !latestCached.isReportedBad) {
            return@withPermit
          }
          val outcome = runCatching {
            LyricsMatcher.matchWithOutcome(
              song = song,
              context = getApplication()
            )
          }.getOrElse { LyricsMatchOutcome.NetworkError }

          when (outcome) {
            is LyricsMatchOutcome.Verified -> {
              runCatching {
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
            }
            LyricsMatchOutcome.NoLyrics -> {
              runCatching {
                dao.insertLyricsCache(
                  LyricsCacheEntity(
                    songId = song.id,
                    songKey = songKey,
                    hasLyrics = false,
                    isReportedBad = false
                  )
                )
              }
            }
            LyricsMatchOutcome.NetworkError -> {
              // Do NOT cache network errors so they retry later
            }
          }
        }
      } catch (ce: CancellationException) {
        throw ce
      } catch (_: Throwable) {
        // Suppress any background room/network error during verification
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
      } catch (ce: CancellationException) {
        throw ce
      } catch (_: Throwable) {
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

  private fun localizedAppContext(): Context {
    return LocaleHelper.wrapBaseContext(getApplication(), language.value)
  }

  fun hideSong(song: Song) {
    val appContext = localizedAppContext()
    viewModelScope.launch {
      prefsManager.addHiddenSongId(song.id)
      prunePlaybackQueue { it.id == song.id }
      Toast.makeText(
        appContext,
        appContext.getString(R.string.toast_song_hidden, song.title),
        Toast.LENGTH_SHORT
      ).show()
    }
  }

  fun blockArtist(artist: String) {
    val normalized = normalizeArtistKey(artist)
    if (normalized.isBlank()) return
    val appContext = localizedAppContext()
    viewModelScope.launch {
      prefsManager.addBlockedArtist(normalized)
      prunePlaybackQueue { normalizeArtistKey(it.artist) == normalized }
      Toast.makeText(
        appContext,
        appContext.getString(R.string.toast_artist_blocked, artist),
        Toast.LENGTH_SHORT
      ).show()
    }
  }

  fun clearHiddenAndBlocked() {
    val appContext = localizedAppContext()
    viewModelScope.launch {
      prefsManager.clearHiddenAndBlocked()
      Toast.makeText(
        appContext,
        appContext.getString(R.string.toast_hidden_cleared),
        Toast.LENGTH_SHORT
      ).show()
    }
  }

  private fun prunePlaybackQueue(shouldRemove: (Song) -> Boolean) {
    val currentQueue = _queue.value
    val activeSong = _rawCurrentSong.value
    val currentRemoved = activeSong != null && shouldRemove(activeSong)
    val oldIdx = if (activeSong != null) {
      currentQueue.indexOfFirst { it.id == activeSong.id }.coerceAtLeast(0)
    } else 0
    val updatedQueue = currentQueue.filterNot(shouldRemove)
    _queue.value = updatedQueue

    if (currentRemoved) {
      if (updatedQueue.isNotEmpty()) {
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
        val removedIds = currentQueue.filter(shouldRemove).map { it.id }.toSet()
        for (i in controller.mediaItemCount - 1 downTo 0) {
          if (controller.getMediaItemAt(i).mediaId in removedIds) {
            controller.removeMediaItem(i)
          }
        }
      }
    }
  }

  fun setEqualizerEnabled(enabled: Boolean) {
    val updated = AudioEqualizerController.setEnabled(enabled)
    persistEqualizerState(updated)
  }

  fun selectEqualizerPreset(preset: EqPreset) {
    val updated = AudioEqualizerController.selectPreset(preset)
    persistEqualizerState(updated)
  }

  fun setEqualizerBandLevel(bandIndex: Int, levelMb: Int) {
    val updated = AudioEqualizerController.setBandLevel(bandIndex, levelMb)
    persistEqualizerState(updated)
  }

  fun setBassBoostStrength(strength: Int) {
    val updated = AudioEqualizerController.setBassBoostStrength(strength)
    persistEqualizerState(updated)
  }

  fun setVirtualizerStrength(strength: Int) {
    val updated = AudioEqualizerController.setVirtualizerStrength(strength)
    persistEqualizerState(updated)
  }

  fun resetEqualizer() {
    val updated = AudioEqualizerController.reset()
    persistEqualizerState(updated)
  }

  private fun persistEqualizerState(eqState: EqualizerUiState) {
    viewModelScope.launch {
      prefsManager.saveEqualizerSettings(
        enabled = eqState.enabled,
        presetId = eqState.presetId,
        bandsMb = eqState.bandLevelsMb,
        bassBoostStrength = eqState.bassBoostStrength,
        virtualizerStrength = eqState.virtualizerStrength
      )
    }
  }

  fun setCrossfadeSeconds(seconds: Int) {
    val updated = CrossfadeController.setDurationSeconds(seconds)
    persistCrossfadeSettings(updated)
  }

  fun setCrossfadeManualSkip(enabled: Boolean) {
    val updated = CrossfadeController.setFadeOnManualSkip(enabled)
    persistCrossfadeSettings(updated)
  }

  private fun persistCrossfadeSettings(settings: CrossfadeSettings) {
    viewModelScope.launch {
      prefsManager.saveCrossfadeSettings(
        durationSeconds = settings.durationSeconds,
        fadeOnManualSkip = settings.fadeOnManualSkip
      )
    }
  }

  fun setThemeMode(mode: String) { viewModelScope.launch { prefsManager.setThemeMode(mode) } }
  fun setAccentColor(hex: String) { viewModelScope.launch { prefsManager.setAccentColor(hex) } }
  fun setLanguage(lang: String) {
    val normalized = LocaleHelper.normalizeLanguage(lang)
    LocaleHelper.saveLanguageSync(getApplication(), normalized)
    viewModelScope.launch { prefsManager.setLanguage(normalized) }
  }
  fun setDynamicBg(enabled: Boolean) { viewModelScope.launch { prefsManager.setDynamicBg(enabled) } }
  fun setReducedMotion(enabled: Boolean) { viewModelScope.launch { prefsManager.setReducedMotion(enabled) } }
  fun setNavStyle(style: String) { viewModelScope.launch { prefsManager.setNavStyle(style) } }
  fun setOnboardingCompleted(completed: Boolean) { viewModelScope.launch { prefsManager.setOnboardingCompleted(completed) } }
  fun setGithubOwner(owner: String) { viewModelScope.launch { prefsManager.setGithubOwner(owner) } }
  fun setGithubRepo(repo: String) { viewModelScope.launch { prefsManager.setGithubRepo(repo) } }

  override fun onCleared() {
    val flush = playSessionTracker.clearSession()
    if (flush != null) {
      kotlinx.coroutines.runBlocking(Dispatchers.IO) {
        analyticsRepository.updatePlayLogDuration(flush.playLogId, flush.listenedDurationMs)
      }
    }
    super.onCleared()
    mediaController?.removeListener(playerListener)
    controllerFuture?.let { MediaController.releaseFuture(it) }
  }
}
