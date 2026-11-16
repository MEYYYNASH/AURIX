package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.LocaleHelper
import com.example.R
import com.example.data.ApkDownloadState
import com.example.data.UpdateCheckStatus
import com.example.model.YoutubeFormatSheetState
import com.example.recommendation.MoodCategory
import com.example.ui.theme.*
import com.example.viewmodel.AurixViewModel

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
  object Home : Screen("home", "Home", Icons.Outlined.Home)
  object Search : Screen("search", "Search", Icons.Outlined.Search)
  object Library : Screen("library", "Library", Icons.Outlined.Folder)
  object Settings : Screen("settings", "Settings", Icons.Outlined.Settings)
  object Album : Screen("album", "Album", Icons.Outlined.Folder)
  object Insights : Screen("insights", "Insights", Icons.Outlined.Home)
  data class PlaylistDetail(val key: String) : Screen("playlist_$key", "Playlist", Icons.Outlined.Home)
}

private val AurixEase = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1.0f)

@Composable
fun AurixApp(viewModel: AurixViewModel) {
  val currentLanguage by viewModel.language.collectAsStateWithLifecycle()
  val baseContext = LocalContext.current
  val baseConfig = LocalConfiguration.current
  val localizedConfig = remember(baseConfig, currentLanguage) {
    LocaleHelper.localizedConfiguration(baseConfig, currentLanguage)
  }
  val localizedContext = remember(baseContext, localizedConfig, currentLanguage) {
    LocaleHelper.wrapActivityContext(baseContext, localizedConfig, currentLanguage)
  }

  CompositionLocalProvider(
    LocalConfiguration provides localizedConfig,
    LocalContext provides localizedContext
  ) {
    AurixAppContent(viewModel = viewModel, currentLanguage = currentLanguage)
  }
}

@Composable
private fun AurixAppContent(
  viewModel: AurixViewModel,
  currentLanguage: String
) {
  val isAppInitialized by viewModel.isAppInitialized.collectAsStateWithLifecycle()
  val launchSplashDismissed by viewModel.launchSplashDismissed.collectAsStateWithLifecycle()
  val onboardingCompleted by viewModel.onboardingCompleted.collectAsStateWithLifecycle()

  val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
  val accentHex by viewModel.accentColorHex.collectAsStateWithLifecycle()
  val dynamicBg by viewModel.dynamicBg.collectAsStateWithLifecycle()
  val reducedMotion by viewModel.reducedMotion.collectAsStateWithLifecycle()

  val systemDark = isSystemInDarkTheme()
  val amoledTheme = themeMode == "amoled"
  val darkTheme = when (themeMode) {
    "light" -> false
    "dark", "amoled" -> true
    else -> systemDark
  }
  val accentColor = parseHexColor(accentHex, darkTheme)

  AurixTheme(darkTheme = darkTheme, amoledTheme = amoledTheme, accentColor = accentColor) {
    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(if (darkTheme) Color(0xFF17181D) else Color(0xFFECEDF2))
    ) {
      if (!isAppInitialized) {
        Box(modifier = Modifier.fillMaxSize())
      } else if (!onboardingCompleted) {
        OnboardingScreen(onFinished = { viewModel.setOnboardingCompleted(true) })
      } else {
        AurixMainShell(
          viewModel = viewModel,
          currentLanguage = currentLanguage,
          dynamicBg = dynamicBg,
          reducedMotion = reducedMotion,
          darkTheme = darkTheme,
          amoledTheme = amoledTheme
        )
      }

      if (!launchSplashDismissed) {
        AurixLaunchOverlay(
          isAppInitialized = isAppInitialized,
          darkTheme = darkTheme,
          reducedMotion = reducedMotion,
          onDismissed = { viewModel.markLaunchSplashShown() }
        )
      }
    }
  }
}

@Composable
private fun AurixMainShell(
  viewModel: AurixViewModel,
  currentLanguage: String,
  dynamicBg: Boolean,
  reducedMotion: Boolean,
  darkTheme: Boolean,
  amoledTheme: Boolean
) {
  var currentScreen by remember { mutableStateOf<Screen>(Screen.Home) }
  var libraryTab by remember { mutableIntStateOf(0) }
  var showNowPlaying by remember { mutableStateOf(false) }
  val context = LocalContext.current

  val searchMode by viewModel.searchMode.collectAsStateWithLifecycle()
  val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
  val youtubeUrl by viewModel.youtubeUrl.collectAsStateWithLifecycle()
  val youtubeAnalysis by viewModel.youtubeAnalysis.collectAsStateWithLifecycle()
  val selectedYoutubeFormatId by viewModel.selectedYoutubeFormatId.collectAsStateWithLifecycle()
  val youtubeDownloadState by viewModel.youtubeDownloadState.collectAsStateWithLifecycle()
  val isAnalyzingYoutube by viewModel.isAnalyzingYoutube.collectAsStateWithLifecycle()
  val youtubeError by viewModel.youtubeError.collectAsStateWithLifecycle()
  val resolvingSongId by viewModel.resolvingSongId.collectAsStateWithLifecycle()
  val formatSheetState by viewModel.formatSheetState.collectAsStateWithLifecycle()

  val installedAppInfo by viewModel.installedAppInfo.collectAsStateWithLifecycle()
  val updateCheckStatus by viewModel.updateCheckStatus.collectAsStateWithLifecycle()
  val updateStatus by viewModel.statusText.collectAsStateWithLifecycle()
  val logoState by viewModel.logoState.collectAsStateWithLifecycle()
  val latestRelease by viewModel.latestRelease.collectAsStateWithLifecycle()
  val apkDownloadState by viewModel.apkDownloadState.collectAsStateWithLifecycle()
  val songDownloadProgress by viewModel.songDownloadProgress.collectAsStateWithLifecycle()
  val downloadedSongIds by viewModel.downloadedSongIds.collectAsStateWithLifecycle()
  val githubOwner by viewModel.githubOwner.collectAsStateWithLifecycle()
  val githubRepo by viewModel.githubRepo.collectAsStateWithLifecycle()

  val featuredTracks by viewModel.featuredTracks.collectAsStateWithLifecycle()
  val isLoadingFeatured by viewModel.isLoadingFeatured.collectAsStateWithLifecycle()
  val featuredError by viewModel.featuredError.collectAsStateWithLifecycle()
  val selectedDailyFeed by viewModel.selectedDailyFeed.collectAsStateWithLifecycle()
  val dailyUpdatedLabel by viewModel.dailyUpdatedLabel.collectAsStateWithLifecycle()
  val dailyUpdatedJustNow by viewModel.dailyUpdatedJustNow.collectAsStateWithLifecycle()
  val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
  val isSearching by viewModel.isSearching.collectAsStateWithLifecycle()
  val searchError by viewModel.searchError.collectAsStateWithLifecycle()
  val searchUiState by viewModel.searchUiState.collectAsStateWithLifecycle()

  val localSongs by viewModel.localSongs.collectAsStateWithLifecycle()
  val favorites by viewModel.favorites.collectAsStateWithLifecycle()
  val recentlyPlayed by viewModel.recentlyPlayed.collectAsStateWithLifecycle()
  val currentSong by viewModel.currentSong.collectAsStateWithLifecycle()
  val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
  val currentPosition by viewModel.currentPosition.collectAsStateWithLifecycle()
  val durationMs by viewModel.durationMs.collectAsStateWithLifecycle()
  val lyrics by viewModel.lyrics.collectAsStateWithLifecycle()
  val lyricOffsetMs by viewModel.lyricOffsetMs.collectAsStateWithLifecycle()
  val shuffleEnabled by viewModel.shuffleEnabled.collectAsStateWithLifecycle()
  val repeatEnabled by viewModel.repeatEnabled.collectAsStateWithLifecycle()
  val playbackSpeed by viewModel.playbackSpeed.collectAsStateWithLifecycle()
  val sleepMinutes by viewModel.sleepMinutes.collectAsStateWithLifecycle()

  val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
  val accentHex by viewModel.accentColorHex.collectAsStateWithLifecycle()
  val verifiedLyricsIds by viewModel.verifiedLyricsIds.collectAsStateWithLifecycle()
  val equalizerState by viewModel.equalizerState.collectAsStateWithLifecycle()
  val crossfadeSettings by viewModel.crossfadeSettings.collectAsStateWithLifecycle()
  val hiddenSongIds by viewModel.hiddenSongIds.collectAsStateWithLifecycle()
  val blockedArtists by viewModel.blockedArtists.collectAsStateWithLifecycle()
  val albumTracks by viewModel.albumTracks.collectAsStateWithLifecycle()
  val activeAlbumInfo by viewModel.activeAlbumInfo.collectAsStateWithLifecycle()
  val libraryAlbums by viewModel.libraryAlbums.collectAsStateWithLifecycle()
  val allSongsForAlbumPicker by viewModel.allSongsForAlbumPicker.collectAsStateWithLifecycle()
  val isAlbumAddedToLibrary by viewModel.isAlbumAddedToLibrary.collectAsStateWithLifecycle()
  val isAlbumDownloaded by viewModel.isAlbumDownloaded.collectAsStateWithLifecycle()
  var albumDetailSong by remember { mutableStateOf<com.example.model.Song?>(null) }
  var songForAlbumPicker by remember { mutableStateOf<com.example.model.Song?>(null) }
  val smartRecommendations by viewModel.smartRecommendations.collectAsStateWithLifecycle()
  val discoverWeekly by viewModel.discoverWeekly.collectAsStateWithLifecycle()
  val moodPlaylists by viewModel.moodPlaylists.collectAsStateWithLifecycle()
  val musicDna by viewModel.musicDna.collectAsStateWithLifecycle()
  val mostPlayedFilter by viewModel.mostPlayedFilter.collectAsStateWithLifecycle()
  val mostPlayed by viewModel.mostPlayed.collectAsStateWithLifecycle()
  val trendingTracks by viewModel.trendingTracks.collectAsStateWithLifecycle()
  val risingArtists by viewModel.risingArtists.collectAsStateWithLifecycle()

  val isNowPlayingOpen = showNowPlaying && currentSong != null

  // Request runtime notification permission once when playback starts on Android 13+
  var askedNotificationPermission by remember { mutableStateOf(false) }
  val notificationPermissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission()
  ) { }
  LaunchedEffect(currentSong != null) {
    if (
      currentSong != null &&
      !askedNotificationPermission &&
      Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    ) {
      askedNotificationPermission = true
      val granted = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.POST_NOTIFICATIONS
      ) == PackageManager.PERMISSION_GRANTED
      if (!granted) {
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
      }
    }
  }

  // Automatically refresh recommendations if a new calendar day starts while app is backgrounded
  val lifecycleOwner = LocalLifecycleOwner.current
  DisposableEffect(lifecycleOwner) {
    val observer = LifecycleEventObserver { _, event ->
      if (event == Lifecycle.Event.ON_RESUME) {
        viewModel.refreshDailyFeedIfNewDay()
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
  }

  // System Back returns cleanly to Home from any secondary tab
  BackHandler(enabled = currentScreen != Screen.Home && !isNowPlayingOpen) {
    currentScreen = Screen.Home
  }

  val pageScale by animateFloatAsState(
    targetValue = if (isNowPlayingOpen) 0.94f else 1.0f,
    animationSpec = if (reducedMotion) snap() else tween(380, easing = AurixEase),
    label = "page_scale"
  )
  val pageAlpha by animateFloatAsState(
    targetValue = if (isNowPlayingOpen) 0.50f else 1.0f,
    animationSpec = if (reducedMotion) snap() else tween(380, easing = AurixEase),
    label = "page_alpha"
  )
  val pageCornerRadius by animateDpAsState(
    targetValue = if (isNowPlayingOpen) 28.dp else 0.dp,
    animationSpec = if (reducedMotion) snap() else tween(380, easing = AurixEase),
    label = "page_corner"
  )

  var retainedSheetSong by remember { mutableStateOf(currentSong) }
  if (currentSong != null) {
    retainedSheetSong = currentSong
  }

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(Color.Black)
  ) {
      // Main App Shell (GPU graphicsLayer prevents clipping/alpha relayout flicker)
      Box(
        modifier = Modifier
          .fillMaxSize()
          .graphicsLayer {
            scaleX = pageScale
            scaleY = pageScale
            alpha = pageAlpha
            if (pageCornerRadius > 0.5.dp) {
              shape = RoundedCornerShape(pageCornerRadius)
              clip = true
            }
          }
          .background(MaterialTheme.colorScheme.background)
      ) {
        DynamicArtworkBackdrop(
          song = currentSong,
          enabled = dynamicBg && !amoledTheme,
          darkTheme = darkTheme,
          reducedMotion = reducedMotion
        )

        val topTabs = remember { listOf(Screen.Home, Screen.Search, Screen.Library, Screen.Settings) }
        AnimatedContent(
          targetState = currentScreen,
          modifier = Modifier.fillMaxSize(),
          transitionSpec = {
            if (reducedMotion) {
              fadeIn(snap()) togetherWith fadeOut(snap())
            } else {
              val initialTabIdx = topTabs.indexOf(initialState)
              val targetTabIdx = topTabs.indexOf(targetState)
              val isEnteringDetail = initialTabIdx >= 0 && targetTabIdx < 0
              val isExitingDetail = initialTabIdx < 0 && targetTabIdx >= 0
              when {
                isEnteringDetail -> {
                  (fadeIn(tween(280, easing = AurixEase)) +
                    slideInHorizontally(tween(300, easing = AurixEase)) { it / 8 } +
                    scaleIn(tween(300, easing = AurixEase), initialScale = 0.97f)) togetherWith
                    (fadeOut(tween(200, easing = AurixEase)) +
                      slideOutHorizontally(tween(260, easing = AurixEase)) { -it / 16 })
                }
                isExitingDetail -> {
                  (fadeIn(tween(260, easing = AurixEase)) +
                    slideInHorizontally(tween(280, easing = AurixEase)) { -it / 14 }) togetherWith
                    (fadeOut(tween(200, easing = AurixEase)) +
                      slideOutHorizontally(tween(260, easing = AurixEase)) { it / 8 } +
                      scaleOut(tween(240, easing = AurixEase), targetScale = 0.97f))
                }
                initialTabIdx >= 0 && targetTabIdx >= 0 && initialTabIdx != targetTabIdx -> {
                  val forward = targetTabIdx > initialTabIdx
                  (fadeIn(tween(260, easing = AurixEase)) +
                    slideInHorizontally(tween(280, easing = AurixEase)) { if (forward) it / 14 else -it / 14 }) togetherWith
                    (fadeOut(tween(180, easing = AurixEase)) +
                      slideOutHorizontally(tween(220, easing = AurixEase)) { if (forward) -it / 18 else it / 18 })
                }
                else -> {
                  (fadeIn(tween(260, easing = AurixEase)) +
                    slideInVertically(tween(280, easing = AurixEase)) { it / 28 }) togetherWith
                    fadeOut(tween(180))
                }
              }.using(SizeTransform(clip = false))
            }
          },
          contentKey = { it.route },
          label = "tab_content"
        ) { screen ->
          when (screen) {
            is Screen.Home -> HomeScreen(
              featuredTracks = featuredTracks,
              isLoadingFeatured = isLoadingFeatured,
              featuredError = featuredError,
              selectedFeed = selectedDailyFeed,
              dailyUpdatedLabel = dailyUpdatedLabel,
              dailyUpdatedJustNow = dailyUpdatedJustNow,
              favoritesCount = favorites.size,
              localSongsCount = localSongs.size,
              recentlyPlayed = recentlyPlayed,
              currentSong = currentSong,
              isPlaying = isPlaying,
              darkTheme = darkTheme,
              reducedMotion = reducedMotion,
              verifiedLyricsIds = verifiedLyricsIds,
              onCheckLyrics = { viewModel.ensureLyricsVerified(it) },
              onSelectFeed = { viewModel.selectDailyFeed(it) },
              onRefreshDailyFeed = { viewModel.loadDailyRecommendations(selectedDailyFeed, forceRefresh = true) },
              onOpenSearch = {
                viewModel.setSearchMode("youtube_search")
                currentScreen = Screen.Search
              },
              onOpenLibraryTab = { tab ->
                libraryTab = tab
                currentScreen = Screen.Library
              },
              onOpenAlbum = {
                viewModel.openAlbum(null)
                currentScreen = Screen.Album
              },
              smartRecommendations = smartRecommendations,
              discoverWeekly = discoverWeekly,
              moodPlaylists = moodPlaylists,
              musicDna = musicDna,
              onOpenInsights = { currentScreen = Screen.Insights },
              onOpenDiscoverWeekly = { currentScreen = Screen.PlaylistDetail("discover_weekly") },
              onOpenMoodPlaylist = { mood -> currentScreen = Screen.PlaylistDetail("mood_${mood.id}") },
              onSongClick = { song, queue -> viewModel.playSong(song, queue) },
              onFavoriteClick = { viewModel.toggleFavorite(it) }
            )
            is Screen.Insights -> MusicInsightsScreen(
              musicDna = musicDna,
              mostPlayedFilter = mostPlayedFilter,
              mostPlayed = mostPlayed,
              trendingTracks = trendingTracks,
              risingArtists = risingArtists,
              currentSong = currentSong,
              isPlaying = isPlaying,
              darkTheme = darkTheme,
              reducedMotion = reducedMotion,
              verifiedLyricsIds = verifiedLyricsIds,
              onBack = { currentScreen = Screen.Home },
              onSelectMostPlayedFilter = { viewModel.setMostPlayedFilter(it) },
              onCheckLyrics = { viewModel.ensureLyricsVerified(it) },
              onSongClick = { song, queue -> viewModel.playSong(song, queue) },
              onFavoriteClick = { viewModel.toggleFavorite(it) }
            )
            is Screen.PlaylistDetail -> {
              if (screen.key == "discover_weekly") {
                val dw = discoverWeekly
                val dwTracks = dw?.tracks.orEmpty()
                val dwSubtitle = if (dw != null && dwTracks.isNotEmpty()) {
                  stringResource(
                    id = R.string.home_discover_weekly_ready,
                    dwTracks.size,
                    dw.matchCount,
                    dw.discoveryCount
                  )
                } else {
                  stringResource(id = R.string.home_discover_weekly_empty)
                }
                PlaylistDetailScreen(
                  title = stringResource(id = R.string.home_discover_weekly_title),
                  subtitle = dwSubtitle,
                  badgeLabel = dw?.weekKey ?: "WEEKLY MIX",
                  tracks = dwTracks,
                  emptyMessage = stringResource(id = R.string.home_discover_weekly_empty),
                  showRefreshButton = dwTracks.isNotEmpty(),
                  currentSong = currentSong,
                  isPlaying = isPlaying,
                  darkTheme = darkTheme,
                  reducedMotion = reducedMotion,
                  verifiedLyricsIds = verifiedLyricsIds,
                  onBack = { currentScreen = Screen.Home },
                  onRefresh = { viewModel.refreshDiscoverWeekly() },
                  onPlayAll = { viewModel.playPlaylist(dwTracks) },
                  onShuffleAll = { viewModel.shuffleAlbum(dwTracks) },
                  onCheckLyrics = { viewModel.ensureLyricsVerified(it) },
                  onSongClick = { song, queue -> viewModel.playSong(song, queue) },
                  onFavoriteClick = { viewModel.toggleFavorite(it) }
                )
              } else {
                val moodId = screen.key.removePrefix("mood_")
                val matchedMood = MoodCategory.entries.firstOrNull { it.id == moodId } ?: MoodCategory.CHILL
                val moodPlaylist = moodPlaylists.firstOrNull { it.mood == matchedMood }
                val moodTracks = moodPlaylist?.tracks.orEmpty()
                PlaylistDetailScreen(
                  title = "${matchedMood.displayName} Mix",
                  subtitle = "${matchedMood.description} · ${matchedMood.bpmRange.first}–${matchedMood.bpmRange.last} BPM",
                  badgeLabel = stringResource(id = R.string.home_moods_title),
                  tracks = moodTracks,
                  emptyMessage = stringResource(id = R.string.playlist_mood_empty),
                  showRefreshButton = false,
                  currentSong = currentSong,
                  isPlaying = isPlaying,
                  darkTheme = darkTheme,
                  reducedMotion = reducedMotion,
                  verifiedLyricsIds = verifiedLyricsIds,
                  onBack = { currentScreen = Screen.Home },
                  onPlayAll = { viewModel.playPlaylist(moodTracks) },
                  onShuffleAll = { viewModel.shuffleAlbum(moodTracks) },
                  onCheckLyrics = { viewModel.ensureLyricsVerified(it) },
                  onSongClick = { song, queue -> viewModel.playSong(song, queue) },
                  onFavoriteClick = { viewModel.toggleFavorite(it) }
                )
              }
            }
            is Screen.Album -> AlbumTrackListSection(
              albumTitle = activeAlbumInfo.title,
              artistName = activeAlbumInfo.artist,
              albumYear = activeAlbumInfo.year,
              coverUrl = activeAlbumInfo.coverUrl,
              albumInfo = activeAlbumInfo,
              tracks = activeAlbumInfo.tracks,
              currentSong = currentSong,
              isPlaying = isPlaying,
              isShuffleEnabled = shuffleEnabled,
              isAddedToLibrary = isAlbumAddedToLibrary,
              isAlbumDownloaded = isAlbumDownloaded,
              hasCurrentSong = currentSong != null,
              reducedMotion = reducedMotion,
              onAddToLibraryClick = {
                viewModel.toggleAlbumInLibrary(activeAlbumInfo.tracks, activeAlbumInfo.title)
              },
              onDownloadAlbumClick = { viewModel.downloadAlbum(context, activeAlbumInfo.tracks) },
              onPlayClick = { viewModel.playAlbumFromStart(activeAlbumInfo.tracks) },
              onShuffleClick = { viewModel.shuffleAlbum(activeAlbumInfo.tracks) },
              onTrackClick = { song -> viewModel.onAlbumTrackClick(song, activeAlbumInfo.tracks) },
              onTrackFavoriteClick = { song -> viewModel.toggleFavorite(song) },
              onTrackDownloadClick = { song -> viewModel.downloadSong(context, song) },
              onTrackDetailsClick = { song -> albumDetailSong = song },
              onImportAudioFiles = { uris -> viewModel.importLocalSongs(uris, activeAlbumInfo) },
              availableSongsToAdd = allSongsForAlbumPicker,
              onAddSongsToAlbum = { songs -> viewModel.addSongsToAlbum(activeAlbumInfo.albumId, songs) },
              onRemoveTrackFromAlbum = { song -> viewModel.removeSongFromAlbum(activeAlbumInfo, song) },
              onDeleteAlbum = { alsoDeleteLocal ->
                viewModel.deleteAlbum(activeAlbumInfo, alsoDeleteLocal)
                libraryTab = 3
                currentScreen = Screen.Library
              },
              onSaveCoverPhoto = { uri, ox, oy, zoom ->
                viewModel.saveAlbumCoverPhoto(activeAlbumInfo.albumId, uri, ox, oy, zoom)
              },
              onSaveCoverMix = { sources ->
                viewModel.saveAlbumCoverMix(activeAlbumInfo.albumId, sources)
              },
              onSaveCoverAppIcon = { viewModel.saveAlbumCoverAppIcon(activeAlbumInfo.albumId) },
              onResetCover = { viewModel.resetAlbumCover(activeAlbumInfo.albumId) }
            )
            is Screen.Search -> SearchScreen(
              searchMode = searchMode,
              searchQuery = searchQuery,
              searchResults = searchResults,
              isSearching = isSearching,
              searchError = searchError,
              youtubeUrl = youtubeUrl,
              youtubeAnalysis = youtubeAnalysis,
              selectedYoutubeFormatId = selectedYoutubeFormatId,
              youtubeDownloadState = youtubeDownloadState,
              isAnalyzingYoutube = isAnalyzingYoutube,
              youtubeError = youtubeError,
              currentSong = currentSong,
              isPlaying = isPlaying,
              darkTheme = darkTheme,
              reducedMotion = reducedMotion,
              songDownloadProgress = songDownloadProgress,
              downloadedSongIds = downloadedSongIds,
              resolvingSongId = resolvingSongId,
              formatSheetState = formatSheetState,
              verifiedLyricsIds = verifiedLyricsIds,
              onCheckLyrics = { viewModel.ensureLyricsVerified(it) },
              onSearchModeChanged = { viewModel.setSearchMode(it) },
              onSearchQueryChanged = { viewModel.search(it) },
              onYoutubeUrlChanged = { viewModel.setYoutubeUrl(it) },
              onAnalyzeYoutube = { viewModel.analyzeYoutubeUrl() },
              onSelectYoutubeFormat = { viewModel.selectYoutubeFormat(it) },
              onDownloadYoutube = { viewModel.downloadAnalyzedTrack(context) },
              onPlayAnalyzedYoutube = { viewModel.playAnalyzedYoutubeTrack() },
              onOpenMySongs = {
                libraryTab = 0
                currentScreen = Screen.Library
              },
              onSongClick = { song, queue -> viewModel.playSong(song, queue) },
              onFavoriteClick = { viewModel.toggleFavorite(it) },
              onDownloadClick = { viewModel.downloadSong(context, it) },
              onYoutubeSongDownloadClick = { viewModel.openYoutubeFormatSheet(it) },
              onCloseFormatSheet = { viewModel.closeYoutubeFormatSheet() },
              onSelectFormatSheetOption = { viewModel.selectFormatSheetOption(it) },
              onPlayFromFormatSheet = { viewModel.playFromFormatSheet() },
              onDownloadFromFormatSheet = { viewModel.downloadFromFormatSheet(context) },
              onRetryFormatSheet = {
                val errState = formatSheetState as? YoutubeFormatSheetState.Error
                if (errState != null) {
                  viewModel.openYoutubeFormatSheet(errState.song)
                }
              },
              searchUiState = searchUiState,
              onClearSearch = { viewModel.clearSearch() },
              onRetrySearch = { viewModel.retrySearch() },
              onImmediateSearch = { viewModel.search(it, immediate = true) }
            )
            is Screen.Library -> LibraryScreen(
              localSongs = localSongs,
              favorites = favorites,
              recentlyPlayed = recentlyPlayed,
              albums = libraryAlbums,
              allAvailableSongs = allSongsForAlbumPicker,
              currentSong = currentSong,
              isPlaying = isPlaying,
              darkTheme = darkTheme,
              reducedMotion = reducedMotion,
              verifiedLyricsIds = verifiedLyricsIds,
              selectedTab = libraryTab,
              onTabSelected = { libraryTab = it },
              onCheckLyrics = { viewModel.ensureLyricsVerified(it) },
              onAddLocalSong = { viewModel.addLocalSong(it) },
              onAddLocalSongs = { viewModel.importLocalSongs(it) },
              onCustomizeSongPhoto = { songId, imageUri -> viewModel.updateLocalSongArtwork(songId, imageUri) },
              onDeleteLocalSong = { viewModel.deleteLocalSong(it) },
              onRemoveRecentSong = { viewModel.removeFromHistory(it) },
              onClearRecentHistory = { viewModel.clearHistory() },
              onOpenInsights = { currentScreen = Screen.Insights },
              onAlbumClick = { album ->
                viewModel.openAlbum(album.albumId)
                currentScreen = Screen.Album
              },
              onCreateAlbum = { title, artist, year ->
                val createdId = viewModel.createAlbum(title, artist, year)
                if (createdId != null) {
                  currentScreen = Screen.Album
                }
              },
              onAddSongsToAlbum = { album, songs ->
                viewModel.addSongsToAlbum(album.albumId, songs)
              },
              onImportAudioToAlbum = { uris, album ->
                viewModel.importLocalSongs(uris, album)
              },
              onDeleteAlbum = { album, alsoDeleteLocal ->
                viewModel.deleteAlbum(album, alsoDeleteLocal)
              },
              onAddSongToAlbumClick = { song -> songForAlbumPicker = song },
              onSongClick = { song ->
                val listQueue = when (libraryTab) {
                  0 -> localSongs
                  1 -> favorites
                  else -> recentlyPlayed
                }
                viewModel.playSong(song, listQueue.ifEmpty { listOf(song) })
              },
              onFavoriteClick = { viewModel.toggleFavorite(it) }
            )
            is Screen.Settings -> SettingsScreen(
              currentTheme = themeMode,
              currentAccentHex = accentHex,
              currentLanguage = currentLanguage,
              dynamicBg = dynamicBg,
              reducedMotion = reducedMotion,
              darkTheme = darkTheme,
              hasCurrentSong = currentSong != null,
              githubOwner = githubOwner,
              githubRepo = githubRepo,
              installedAppInfo = installedAppInfo,
              updateCheckStatus = updateCheckStatus,
              updateStatus = updateStatus,
              latestRelease = latestRelease,
              apkDownloadState = apkDownloadState,
              onGithubOwnerChanged = { viewModel.setGithubOwner(it) },
              onGithubRepoChanged = { viewModel.setGithubRepo(it) },
              onCheckUpdate = { viewModel.checkForUpdates(it) },
              onDownloadUpdate = { viewModel.downloadAndInstallUpdate(it) },
              onInstallUpdate = { viewModel.installDownloadedApk(it) },
              onOpenGitHubRepo = { viewModel.openGitHubRepo(it) },
              onThemeChanged = { viewModel.setThemeMode(it) },
              onAccentChanged = { viewModel.setAccentColor(it) },
              onLanguageChanged = { viewModel.setLanguage(it) },
              onDynamicBgChanged = { viewModel.setDynamicBg(it) },
              onReducedMotionChanged = { viewModel.setReducedMotion(it) },
              equalizerState = equalizerState,
              onToggleEqualizer = { viewModel.setEqualizerEnabled(it) },
              onSelectEqPreset = { viewModel.selectEqualizerPreset(it) },
              onEqBandLevelChanged = { idx, level -> viewModel.setEqualizerBandLevel(idx, level) },
              onEqBassBoostChanged = { viewModel.setBassBoostStrength(it) },
              onResetEq = { viewModel.resetEqualizer() },
              crossfadeSettings = crossfadeSettings,
              onCrossfadeSecondsChanged = { viewModel.setCrossfadeSeconds(it) },
              onCrossfadeManualSkipChanged = { viewModel.setCrossfadeManualSkip(it) },
              hiddenSongsCount = hiddenSongIds.size,
              blockedArtistsCount = blockedArtists.size,
              onClearHiddenAndBlocked = { viewModel.clearHiddenAndBlocked() },
              logoState = logoState,
              onStartUpdateLogoIntro = { viewModel.startUpdateLogoIntro() }
            )
          }
        }

        // Subtle atmospheric gradient scrim so scrolling list rows fade gracefully behind the floating dock
        val targetScrimBase = when {
          currentScreen is Screen.Album -> Color(0xFF0E1120)
          darkTheme -> Color(0xFF0A0E1A)
          else -> Color(0xFFEAF0FF)
        }
        val dockScrimBase by animateColorAsState(
          targetValue = targetScrimBase,
          animationSpec = if (reducedMotion) snap() else tween(280, easing = AurixEase),
          label = "dock_scrim_base"
        )
        val dockScrimHeight by animateDpAsState(
          targetValue = if (currentSong != null) 216.dp else 140.dp,
          animationSpec = if (reducedMotion) snap() else tween(300, easing = AurixEase),
          label = "dock_scrim_height"
        )
        Box(
          modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .height(dockScrimHeight)
            .background(
              Brush.verticalGradient(
                colors = listOf(
                  Color.Transparent,
                  dockScrimBase.copy(alpha = 0.68f),
                  dockScrimBase.copy(alpha = 0.94f)
                )
              )
            )
        )

        // Bottom Floating MiniPlayer + Liquid Glass Navigation Bar (steady during NowPlaying sheet slide)
        val screens = remember { listOf(Screen.Home, Screen.Search, Screen.Library, Screen.Settings) }
        val navTabs = listOf(
          TabItem(stringResource(id = R.string.nav_home), Screen.Home.icon),
          TabItem(stringResource(id = R.string.nav_search), Screen.Search.icon),
          TabItem(stringResource(id = R.string.nav_library), Screen.Library.icon),
          TabItem(stringResource(id = R.string.nav_settings), Screen.Settings.icon)
        )
        val selectedIndex = screens.indexOf(currentScreen).coerceAtLeast(0)

        Column(
          modifier = Modifier
            .align(Alignment.BottomCenter)
            .navigationBarsPadding()
            .padding(bottom = 14.dp),
          horizontalAlignment = Alignment.CenterHorizontally
        ) {
          AnimatedVisibility(
            visible = currentSong != null,
            enter = if (reducedMotion) fadeIn(snap()) else slideInVertically(tween(340, easing = AurixEase)) { it / 2 } + fadeIn(tween(240)),
            exit = if (reducedMotion) fadeOut(snap()) else slideOutVertically(tween(260, easing = AurixEase)) { it / 2 } + fadeOut(tween(180))
          ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
              MiniPlayer(
                song = currentSong,
                isPlaying = isPlaying,
                currentPositionMs = currentPosition,
                durationMs = durationMs,
                darkTheme = darkTheme,
                reducedMotion = reducedMotion,
                hasLyrics = currentSong?.id?.let { it in verifiedLyricsIds } == true,
                onCheckLyrics = { viewModel.ensureLyricsVerified(it) },
                onPlayPauseClick = { viewModel.togglePlayPause() },
                onFavoriteClick = { currentSong?.let { viewModel.toggleFavorite(it) } },
                onClick = { showNowPlaying = true }
              )
              Spacer(modifier = Modifier.height(10.dp))
            }
          }

          LiquidGlassTabBar(
            tabs = navTabs,
            selectedIndex = selectedIndex,
            onSelect = { index -> currentScreen = screens[index] },
            reducedMotion = reducedMotion
          )
        }
      }

      // Now Playing Full-Screen Sheet
      AnimatedVisibility(
        visible = isNowPlayingOpen,
        enter = if (reducedMotion) fadeIn(snap()) else slideInVertically(tween(380, easing = AurixEase)) { it } + fadeIn(tween(220)),
        exit = if (reducedMotion) fadeOut(snap()) else slideOutVertically(tween(340, easing = AurixEase)) { it } + fadeOut(tween(200))
      ) {
        retainedSheetSong?.let { song ->
          NowPlayingSheet(
            song = song,
            isPlaying = isPlaying,
            currentPositionMs = currentPosition,
            positionProvider = { viewModel.getLivePlaybackPositionMs() },
            durationMs = durationMs,
            lyricsResult = lyrics,
            lyricOffsetMs = lyricOffsetMs,
            dynamicBg = dynamicBg,
            reducedMotion = reducedMotion,
            shuffleEnabled = shuffleEnabled,
            repeatEnabled = repeatEnabled,
            playbackSpeed = playbackSpeed,
            sleepMinutes = sleepMinutes,
            onPlayPause = { viewModel.togglePlayPause() },
            onNext = { viewModel.nextTrack() },
            onPrevious = { viewModel.previousTrack() },
            onToggleShuffle = { viewModel.toggleShuffle() },
            onToggleRepeat = { viewModel.toggleRepeat() },
            onCycleSpeed = { viewModel.cyclePlaybackSpeed() },
            onCycleSleep = { viewModel.cycleSleepTimer() },
            onSeek = { viewModel.seekTo(it) },
            onFavorite = { viewModel.toggleFavorite(song) },
            onClose = { showNowPlaying = false },
            onLineClick = { viewModel.seekTo(it) },
            onAlignLine = { viewModel.alignLyricsToTimestamp(it) },
            onAdjustOffset = { viewModel.adjustLyricOffset(it) },
            onResetOffset = { viewModel.resetLyricOffset() },
            onRetryLyrics = { viewModel.retryLyrics() },
            hasLyrics = song.id in verifiedLyricsIds,
            equalizerState = equalizerState,
            onToggleEqualizer = { viewModel.setEqualizerEnabled(it) },
            onSelectEqPreset = { viewModel.selectEqualizerPreset(it) },
            onEqBandLevelChanged = { idx, level -> viewModel.setEqualizerBandLevel(idx, level) },
            onEqBassBoostChanged = { viewModel.setBassBoostStrength(it) },
            onResetEq = { viewModel.resetEqualizer() },
            onHideSong = { viewModel.hideSong(song) },
            onBlockArtist = { viewModel.blockArtist(song.artist) },
            onCheckLyrics = { viewModel.ensureLyricsVerified(it) },
            onDeleteSong = if (
              localSongs.any { it.id == song.id } ||
              song.source == "Local" ||
              song.source == "Downloaded" ||
              song.streamUrl.startsWith("file://") ||
              song.streamUrl.startsWith("content://")
            ) {
              {
                viewModel.deleteLocalSong(song)
                showNowPlaying = false
              }
              } else {
                null
              },
            onAddToAlbum = { targetSong -> songForAlbumPicker = targetSong },
            similarSongsProvider = { target -> viewModel.getSimilarSongs(target) },
            onPlaySimilarSong = { similar ->
              val queue = listOf(similar) + viewModel.getSimilarSongs(similar).map { it.song }
              viewModel.playSong(similar, queue)
            }
          )
        }
      }

      albumDetailSong?.let { detailTarget ->
        SongDetailsSheet(
          song = detailTarget,
          hasLyrics = detailTarget.id in verifiedLyricsIds,
          isPlaying = currentSong?.id == detailTarget.id && isPlaying,
          reducedMotion = reducedMotion,
          similarSongs = remember(detailTarget) { viewModel.getSimilarSongs(detailTarget) },
          onSelectSimilarSong = { similar ->
            albumDetailSong = null
            val queue = listOf(similar) + viewModel.getSimilarSongs(similar).map { it.song }
            viewModel.playSong(similar, queue)
          },
          onDismiss = { albumDetailSong = null },
          onPlay = {
            albumDetailSong = null
            viewModel.playSong(detailTarget, albumTracks)
          },
          onFavorite = { viewModel.toggleFavorite(detailTarget) },
          onShare = {
            albumDetailSong = null
            val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
              type = "text/plain"
              putExtra(android.content.Intent.EXTRA_TEXT, "${detailTarget.title} — ${detailTarget.artist}")
            }
            context.startActivity(android.content.Intent.createChooser(shareIntent, null))
          },
          onHideSong = {
            albumDetailSong = null
            viewModel.hideSong(detailTarget)
          },
          onBlockArtist = {
            albumDetailSong = null
            viewModel.blockArtist(detailTarget.artist)
          },
          onAddToAlbum = {
            albumDetailSong = null
            songForAlbumPicker = detailTarget
          }
        )
      }

      songForAlbumPicker?.let { pickerSong ->
        AddSongToAlbumSheet(
          song = pickerSong,
          albums = libraryAlbums,
          onDismiss = { songForAlbumPicker = null },
          onSelectAlbum = { album -> viewModel.addSongToAlbum(album.albumId, pickerSong) },
          onCreateAlbumWithSong = { title, artist, year ->
            viewModel.createAlbumWithSong(title, artist, year, pickerSong)
          }
        )
      }

      // Optional Mandatory Critical Update Dialog
      val mandatoryUpdate = (updateCheckStatus as? UpdateCheckStatus.UpdateAvailable)?.takeIf { it.isMandatory }
      if (mandatoryUpdate != null) {
        val isDownloading = apkDownloadState is ApkDownloadState.Downloading
        val isValidated = apkDownloadState is ApkDownloadState.ValidatedReady
        AlertDialog(
          onDismissRequest = {},
          properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
          title = { Text("Critical Update Required (${mandatoryUpdate.release.tagName})") },
          text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
              Text(
                "Installed: v${mandatoryUpdate.installed.versionName} (code ${mandatoryUpdate.installed.versionCode})\n" +
                  "Latest: ${mandatoryUpdate.release.tagName} (code ${mandatoryUpdate.release.versionCode})"
              )
              if (mandatoryUpdate.release.body.isNotBlank()) {
                Text(mandatoryUpdate.release.body, style = MaterialTheme.typography.bodySmall)
              }
              if (isDownloading) {
                val dl = apkDownloadState as ApkDownloadState.Downloading
                LinearProgressIndicator(
                  progress = { if (dl.progress >= 0f) dl.progress else 0.35f },
                  modifier = Modifier.fillMaxWidth()
                )
              }
              if (apkDownloadState is ApkDownloadState.Error) {
                Text(
                  text = (apkDownloadState as ApkDownloadState.Error).message,
                  color = MaterialTheme.colorScheme.error,
                  style = MaterialTheme.typography.bodySmall
                )
              }
            }
          },
          confirmButton = {
            Button(
              enabled = !isDownloading,
              onClick = {
                if (isValidated) viewModel.installDownloadedApk(context)
                else viewModel.downloadAndInstallUpdate(context)
              }
            ) {
              Text(
                when {
                  isDownloading -> "Downloading..."
                  isValidated -> "Install Update"
                  else -> "Update Now"
                }
              )
            }
          }
        )
      }
    }
}

fun parseHexColor(hex: String, darkTheme: Boolean = false): Color {
  if (hex.equals("#007AFF", ignoreCase = true) || hex.equals("#3B77F5", ignoreCase = true) || hex.equals("#0A84FF", ignoreCase = true)) {
    return if (darkTheme) AccentBlueDark else AccentBlue
  }
  return try {
    Color(android.graphics.Color.parseColor(hex))
  } catch (e: Exception) {
    if (darkTheme) AccentBlueDark else AccentBlue
  }
}
