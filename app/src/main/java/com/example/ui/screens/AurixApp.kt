package com.example.ui.screens

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ErrorOutline
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.LocaleHelper
import com.example.R
import com.example.data.UpdateStage
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
  val appError by viewModel.appError.collectAsStateWithLifecycle()
  val hostContext = LocalContext.current

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
          launchSplashDismissed = launchSplashDismissed,
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

      val activeError = appError
      if (activeError != null) {
        AlertDialog(
          onDismissRequest = { viewModel.dismissAppError() },
          modifier = Modifier.testTag("app_error_dialog"),
          icon = {
            Icon(
              imageVector = Icons.Rounded.ErrorOutline,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.error
            )
          },
          title = {
            Text(activeError.title.ifBlank { stringResource(R.string.error_dialog_default_title) })
          },
          text = {
            Column(
              modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 260.dp)
                .verticalScroll(rememberScrollState()),
              verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              Text(
                text = activeError.message,
                style = MaterialTheme.typography.bodyMedium
              )
              val detailsText = activeError.details
              if (!detailsText.isNullOrBlank()) {
                Surface(
                  color = MaterialTheme.colorScheme.surfaceVariant,
                  shape = RoundedCornerShape(10.dp)
                ) {
                  SelectionContainer {
                    Text(
                      text = detailsText,
                      style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                      ),
                      modifier = Modifier.padding(10.dp)
                    )
                  }
                }
              }
            }
          },
          dismissButton = {
            val detailsText = activeError.details
            if (!detailsText.isNullOrBlank()) {
              TextButton(
                onClick = {
                  runCatching {
                    val clipboard = hostContext.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    val copyPayload = "${activeError.title}\n${activeError.message}\n$detailsText"
                    clipboard?.setPrimaryClip(ClipData.newPlainText("AURIX Error", copyPayload))
                    Toast.makeText(
                      hostContext,
                      hostContext.getString(R.string.error_dialog_copied),
                      Toast.LENGTH_SHORT
                    ).show()
                  }
                },
                modifier = Modifier
                  .defaultMinSize(minHeight = 48.dp)
                  .testTag("app_error_copy_button")
              ) {
                Icon(
                  imageVector = Icons.Rounded.ContentCopy,
                  contentDescription = null,
                  modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.error_dialog_copy))
              }
            }
          },
          confirmButton = {
            Button(
              onClick = { viewModel.dismissAppError() },
              modifier = Modifier
                .defaultMinSize(minHeight = 48.dp)
                .testTag("app_error_dismiss_button")
            ) {
              Text(stringResource(R.string.error_dialog_dismiss))
            }
          }
        )
      }
    }
  }
}

@Composable
private fun AurixMainShell(
  viewModel: AurixViewModel,
  currentLanguage: String,
  launchSplashDismissed: Boolean,
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
  val youtubePlaylist by viewModel.youtubePlaylist.collectAsStateWithLifecycle()
  val selectedYoutubeFormatId by viewModel.selectedYoutubeFormatId.collectAsStateWithLifecycle()
  val youtubeDownloadState by viewModel.youtubeDownloadState.collectAsStateWithLifecycle()
  val isAnalyzingYoutube by viewModel.isAnalyzingYoutube.collectAsStateWithLifecycle()
  val youtubeError by viewModel.youtubeError.collectAsStateWithLifecycle()
  val resolvingSongId by viewModel.resolvingSongId.collectAsStateWithLifecycle()
  val formatSheetState by viewModel.formatSheetState.collectAsStateWithLifecycle()

  val updateUiState by viewModel.updateUiState.collectAsStateWithLifecycle()
  val logoState by viewModel.logoState.collectAsStateWithLifecycle()
  val showWhatsNewSheet by viewModel.showWhatsNewSheet.collectAsStateWithLifecycle()
  val songDownloadProgress by viewModel.songDownloadProgress.collectAsStateWithLifecycle()
  val downloadedSongIds by viewModel.downloadedSongIds.collectAsStateWithLifecycle()
  val githubOwner by viewModel.githubOwner.collectAsStateWithLifecycle()
  val githubRepo by viewModel.githubRepo.collectAsStateWithLifecycle()

  val featuredTracks by viewModel.featuredTracks.collectAsStateWithLifecycle()
  val isLoadingFeatured by viewModel.isLoadingFeatured.collectAsStateWithLifecycle()
  val featuredError by viewModel.featuredError.collectAsStateWithLifecycle()
  val popularYoutubeSongs by viewModel.popularYoutubeSongs.collectAsStateWithLifecycle()
  val tiktokViralNewPipeSongs by viewModel.tiktokViralNewPipeSongs.collectAsStateWithLifecycle()
  val isLoadingTiktokViralNewPipe by viewModel.isLoadingTiktokViralNewPipe.collectAsStateWithLifecycle()
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
  val sleepRemainingSeconds by viewModel.sleepRemainingSeconds.collectAsStateWithLifecycle()
  val sleepEndOfTrack by viewModel.sleepEndOfTrack.collectAsStateWithLifecycle()
  val queue by viewModel.queue.collectAsStateWithLifecycle()

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
  var showNavPreviewSheet by remember { mutableStateOf(false) }
  var showDownloadQueueSheet by remember { mutableStateOf(false) }
  val downloadQueueItems by viewModel.downloadQueueItems.collectAsStateWithLifecycle()
  val playlistSelectionMode by viewModel.playlistSelectionMode.collectAsStateWithLifecycle()
  val selectedPlaylistTrackIds by viewModel.selectedPlaylistTrackIds.collectAsStateWithLifecycle()
  val autoCleanYoutubeTitles by viewModel.autoCleanYoutubeTitles.collectAsStateWithLifecycle()
  val nowPlayingVisualizerEnabled by viewModel.nowPlayingVisualizerEnabled.collectAsStateWithLifecycle()
  val spectrumBars by viewModel.spectrumBars.collectAsStateWithLifecycle()
  val navStyle by viewModel.navStyle.collectAsStateWithLifecycle()
  val smartRecommendations by viewModel.smartRecommendations.collectAsStateWithLifecycle()
  val moodFlowEnabled by viewModel.moodFlowEnabled.collectAsStateWithLifecycle()
  val moodFlowCurrentPick by viewModel.moodFlowCurrentPick.collectAsStateWithLifecycle()
  val discoverWeekly by viewModel.discoverWeekly.collectAsStateWithLifecycle()
  val moodPlaylists by viewModel.moodPlaylists.collectAsStateWithLifecycle()
  val musicDna by viewModel.musicDna.collectAsStateWithLifecycle()
  val mostPlayedFilter by viewModel.mostPlayedFilter.collectAsStateWithLifecycle()
  val mostPlayed by viewModel.mostPlayed.collectAsStateWithLifecycle()
  val trendingTracks by viewModel.trendingTracks.collectAsStateWithLifecycle()
  val risingArtists by viewModel.risingArtists.collectAsStateWithLifecycle()
  val artistMixes by viewModel.artistMixes.collectAsStateWithLifecycle()
  val myMixes by viewModel.myMixes.collectAsStateWithLifecycle()
  val dailyDiscovery by viewModel.dailyDiscovery.collectAsStateWithLifecycle()
  val favoritesMix by viewModel.favoritesMix.collectAsStateWithLifecycle()
  val newForYouItems by viewModel.newForYouItems.collectAsStateWithLifecycle()
  val recentPlayEntries by viewModel.recentPlayEntries.collectAsStateWithLifecycle()
  val recentlyAddedSongs by viewModel.recentlyAddedSongs.collectAsStateWithLifecycle()

  val isNowPlayingOpen = showNowPlaying && currentSong != null
  val pendingNotificationNav by viewModel.pendingNotificationNav.collectAsStateWithLifecycle()

  LaunchedEffect(pendingNotificationNav) {
    val nav = pendingNotificationNav ?: return@LaunchedEffect
    when (nav.screenRoute) {
      com.example.data.AppNotificationHelper.SCREEN_SETTINGS -> currentScreen = Screen.Settings
      com.example.data.AppNotificationHelper.SCREEN_LIBRARY -> {
        libraryTab = nav.libraryTab
        currentScreen = Screen.Library
      }
    }
    showNowPlaying = false
    viewModel.consumeNotificationNav()
  }

  // Request runtime notification permission once on Android 13+ so update alerts & background downloads show notifications
  var askedNotificationPermission by remember { mutableStateOf(false) }
  val notificationPermissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission()
  ) { }
  LaunchedEffect(launchSplashDismissed, currentSong != null, songDownloadProgress.isNotEmpty()) {
    if (
      (launchSplashDismissed || currentSong != null || songDownloadProgress.isNotEmpty()) &&
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

  // Automatically refresh recommendations on new day and notify UpdateManager on resume
  val lifecycleOwner = LocalLifecycleOwner.current
  DisposableEffect(lifecycleOwner) {
    val observer = LifecycleEventObserver { _, event ->
      if (event == Lifecycle.Event.ON_RESUME) {
        viewModel.refreshDailyFeedIfNewDay()
        viewModel.onUpdateHostResumed()
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
              popularYoutubeSongs = popularYoutubeSongs,
              tiktokViralChartSongs = tiktokViralNewPipeSongs,
              isLoadingTiktokViralChart = isLoadingTiktokViralNewPipe,
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
              resolvingSongId = resolvingSongId,
              downloadedSongIds = downloadedSongIds,
              songDownloadProgress = songDownloadProgress,
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
              onOpenAllTiktokViral = { currentScreen = Screen.PlaylistDetail("tiktok_viral_charts") },
              onRefreshTiktokViral = { viewModel.loadTiktokViralNewPipeSongs(forceRefresh = true) },
              onYoutubeSongDownloadClick = { viewModel.openYoutubeFormatSheet(it) },
              smartRecommendations = smartRecommendations,
              discoverWeekly = discoverWeekly,
              moodPlaylists = moodPlaylists,
              musicDna = musicDna,
              artistMixes = artistMixes,
              myMixes = myMixes,
              dailyDiscovery = dailyDiscovery,
              favoritesMix = favoritesMix,
              newForYouItems = newForYouItems,
              recentPlayEntries = recentPlayEntries,
              recentlyAddedCount = recentlyAddedSongs.size,
              onOpenInsights = { currentScreen = Screen.Insights },
              onOpenDiscoverWeekly = { currentScreen = Screen.PlaylistDetail("discover_weekly") },
              onOpenMoodPlaylist = { mood -> currentScreen = Screen.PlaylistDetail("mood_${mood.id}") },
              onOpenArtistMix = { mix -> currentScreen = Screen.PlaylistDetail(mix.id) },
              onOpenPersonalMix = { mix -> currentScreen = Screen.PlaylistDetail(mix.id) },
              onPlayTracks = { tracks -> viewModel.playPlaylist(tracks) },
              onShuffleTracks = { tracks -> viewModel.shuffleAlbum(tracks) },
              onSaveMix = { title, tracks -> viewModel.saveMixAsPlaylist(title, tracks) },
              onShareMix = { title, subtitle, tracks -> viewModel.shareMix(context, title, subtitle, tracks) },
              onRefreshMixes = { viewModel.refreshPersonalMixes() },
              onOpenNewForYouItem = { item ->
                val itemAlbum = item.album
                val itemSong = item.song
                if (itemAlbum != null) {
                  viewModel.openFeaturedAlbum(itemAlbum)
                  currentScreen = Screen.Album
                } else if (itemSong != null) {
                  val queue = newForYouItems.mapNotNull { it.song }.ifEmpty { listOf(itemSong) }
                  viewModel.playSong(itemSong, queue)
                }
              },
              onRemoveRecentSong = { viewModel.removeFromHistory(it) },
              onOpenDailyDiscovery = { currentScreen = Screen.PlaylistDetail("daily_discovery") },
              onOpenFavoritesMix = { currentScreen = Screen.PlaylistDetail("favorites_mix") },
              onOpenRecentlyAdded = { currentScreen = Screen.PlaylistDetail("recently_added") },
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
              when {
                screen.key == "tiktok_viral_charts" -> {
                  PlaylistDetailScreen(
                    title = stringResource(id = R.string.home_tiktok_viral_charts_title),
                    subtitle = stringResource(id = R.string.home_tiktok_viral_charts_sub),
                    badgeLabel = stringResource(id = R.string.home_tiktok_newpipe_badge),
                    tracks = tiktokViralNewPipeSongs,
                    emptyMessage = stringResource(id = R.string.home_tiktok_viral_empty),
                    showRefreshButton = true,
                    currentSong = currentSong,
                    isPlaying = isPlaying,
                    darkTheme = darkTheme,
                    reducedMotion = reducedMotion,
                    verifiedLyricsIds = verifiedLyricsIds,
                    resolvingSongId = resolvingSongId,
                    downloadedSongIds = downloadedSongIds,
                    songDownloadProgress = songDownloadProgress,
                    onBack = { currentScreen = Screen.Home },
                    onRefresh = { viewModel.loadTiktokViralNewPipeSongs(forceRefresh = true) },
                    onPlayAll = { viewModel.playPlaylist(tiktokViralNewPipeSongs) },
                    onShuffleAll = { viewModel.shuffleAlbum(tiktokViralNewPipeSongs) },
                    onCheckLyrics = { viewModel.ensureLyricsVerified(it) },
                    onSongClick = { song, queue -> viewModel.playSong(song, queue) },
                    onFavoriteClick = { viewModel.toggleFavorite(it) },
                    onYoutubeSongDownloadClick = { viewModel.openYoutubeFormatSheet(it) }
                  )
                }
                screen.key == "discover_weekly" -> {
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
                    onFavoriteClick = { viewModel.toggleFavorite(it) },
                    onSaveMix = if (dwTracks.isNotEmpty()) {
                      { viewModel.saveMixAsPlaylist("Discover Weekly", dwTracks) }
                    } else null,
                    onShareMix = if (dwTracks.isNotEmpty()) {
                      { viewModel.shareMix(context, "Discover Weekly", dwSubtitle, dwTracks) }
                    } else null
                  )
                }
                screen.key.startsWith("artist_mix_") -> {
                  val matchedMix = artistMixes.firstOrNull { it.id == screen.key }
                  val mixTitle = matchedMix?.artistName ?: stringResource(id = R.string.home_artist_mixes_title)
                  val mixSub = matchedMix?.subtitle ?: stringResource(id = R.string.mix_empty_loading)
                  val mixTracks = matchedMix?.tracks.orEmpty()
                  PlaylistDetailScreen(
                    title = mixTitle,
                    subtitle = mixSub,
                    badgeLabel = matchedMix?.genreTag?.ifBlank { "ARTIST MIX" } ?: "ARTIST MIX",
                    tracks = mixTracks,
                    emptyMessage = stringResource(id = R.string.mix_empty_loading),
                    showRefreshButton = false,
                    currentSong = currentSong,
                    isPlaying = isPlaying,
                    darkTheme = darkTheme,
                    reducedMotion = reducedMotion,
                    verifiedLyricsIds = verifiedLyricsIds,
                    onBack = { currentScreen = Screen.Home },
                    onPlayAll = { viewModel.playPlaylist(mixTracks) },
                    onShuffleAll = { viewModel.shuffleAlbum(mixTracks) },
                    onCheckLyrics = { viewModel.ensureLyricsVerified(it) },
                    onSongClick = { song, queue -> viewModel.playSong(song, queue) },
                    onFavoriteClick = { viewModel.toggleFavorite(it) },
                    onSaveMix = if (mixTracks.isNotEmpty()) {
                      { viewModel.saveMixAsPlaylist("$mixTitle Mix", mixTracks) }
                    } else null,
                    onShareMix = if (mixTracks.isNotEmpty()) {
                      { viewModel.shareMix(context, "$mixTitle Mix", mixSub, mixTracks) }
                    } else null
                  )
                }
                screen.key.startsWith("my_mix_") -> {
                  val matchedMix = myMixes.firstOrNull { it.id == screen.key }
                  val mixTitle = matchedMix?.title ?: stringResource(id = R.string.home_my_mix_title)
                  val mixSub = matchedMix?.subtitle ?: stringResource(id = R.string.mix_empty_loading)
                  val mixTracks = matchedMix?.tracks.orEmpty()
                  PlaylistDetailScreen(
                    title = mixTitle,
                    subtitle = mixSub,
                    badgeLabel = matchedMix?.badgeLabel ?: "MY MIX",
                    tracks = mixTracks,
                    emptyMessage = stringResource(id = R.string.mix_empty_loading),
                    showRefreshButton = true,
                    currentSong = currentSong,
                    isPlaying = isPlaying,
                    darkTheme = darkTheme,
                    reducedMotion = reducedMotion,
                    verifiedLyricsIds = verifiedLyricsIds,
                    onBack = { currentScreen = Screen.Home },
                    onRefresh = { viewModel.refreshPersonalMixes() },
                    onPlayAll = { viewModel.playPlaylist(mixTracks) },
                    onShuffleAll = { viewModel.shuffleAlbum(mixTracks) },
                    onCheckLyrics = { viewModel.ensureLyricsVerified(it) },
                    onSongClick = { song, queue -> viewModel.playSong(song, queue) },
                    onFavoriteClick = { viewModel.toggleFavorite(it) },
                    onSaveMix = if (mixTracks.isNotEmpty()) {
                      { viewModel.saveMixAsPlaylist(mixTitle, mixTracks) }
                    } else null,
                    onShareMix = if (mixTracks.isNotEmpty()) {
                      { viewModel.shareMix(context, mixTitle, mixSub, mixTracks) }
                    } else null
                  )
                }
                screen.key == "daily_discovery" -> {
                  val mix = dailyDiscovery
                  val mixTracks = mix?.tracks.orEmpty()
                  val mixSub = mix?.subtitle ?: stringResource(id = R.string.mix_empty_loading)
                  PlaylistDetailScreen(
                    title = stringResource(id = R.string.home_daily_discovery_title),
                    subtitle = mixSub,
                    badgeLabel = mix?.badgeLabel ?: "DISCOVERY",
                    tracks = mixTracks,
                    emptyMessage = stringResource(id = R.string.mix_empty_loading),
                    showRefreshButton = true,
                    currentSong = currentSong,
                    isPlaying = isPlaying,
                    darkTheme = darkTheme,
                    reducedMotion = reducedMotion,
                    verifiedLyricsIds = verifiedLyricsIds,
                    onBack = { currentScreen = Screen.Home },
                    onRefresh = { viewModel.refreshPersonalMixes() },
                    onPlayAll = { viewModel.playPlaylist(mixTracks) },
                    onShuffleAll = { viewModel.shuffleAlbum(mixTracks) },
                    onCheckLyrics = { viewModel.ensureLyricsVerified(it) },
                    onSongClick = { song, queue -> viewModel.playSong(song, queue) },
                    onFavoriteClick = { viewModel.toggleFavorite(it) },
                    onSaveMix = if (mixTracks.isNotEmpty()) {
                      { viewModel.saveMixAsPlaylist("Daily Discovery", mixTracks) }
                    } else null,
                    onShareMix = if (mixTracks.isNotEmpty()) {
                      { viewModel.shareMix(context, "Daily Discovery", mixSub, mixTracks) }
                    } else null
                  )
                }
                screen.key == "favorites_mix" -> {
                  val mix = favoritesMix
                  val mixTracks = mix?.tracks.orEmpty()
                  val mixSub = mix?.subtitle ?: stringResource(id = R.string.home_favorites)
                  PlaylistDetailScreen(
                    title = stringResource(id = R.string.home_favorites_mix_title),
                    subtitle = mixSub,
                    badgeLabel = mix?.badgeLabel ?: "FAVORITES MIX",
                    tracks = mixTracks,
                    emptyMessage = stringResource(id = R.string.mix_empty_loading),
                    showRefreshButton = false,
                    currentSong = currentSong,
                    isPlaying = isPlaying,
                    darkTheme = darkTheme,
                    reducedMotion = reducedMotion,
                    verifiedLyricsIds = verifiedLyricsIds,
                    onBack = { currentScreen = Screen.Home },
                    onPlayAll = { viewModel.playPlaylist(mixTracks) },
                    onShuffleAll = { viewModel.shuffleAlbum(mixTracks) },
                    onCheckLyrics = { viewModel.ensureLyricsVerified(it) },
                    onSongClick = { song, queue -> viewModel.playSong(song, queue) },
                    onFavoriteClick = { viewModel.toggleFavorite(it) },
                    onSaveMix = if (mixTracks.isNotEmpty()) {
                      { viewModel.saveMixAsPlaylist("Favorites Mix", mixTracks) }
                    } else null,
                    onShareMix = if (mixTracks.isNotEmpty()) {
                      { viewModel.shareMix(context, "Favorites Mix", mixSub, mixTracks) }
                    } else null
                  )
                }
                screen.key == "recently_added" -> {
                  val recentTracks = recentlyAddedSongs
                  val sub = stringResource(id = R.string.home_recently_added_sub, recentTracks.size)
                  PlaylistDetailScreen(
                    title = stringResource(id = R.string.home_recently_added_title),
                    subtitle = sub,
                    badgeLabel = "LIBRARY",
                    tracks = recentTracks,
                    emptyMessage = stringResource(id = R.string.mix_empty_loading),
                    showRefreshButton = false,
                    currentSong = currentSong,
                    isPlaying = isPlaying,
                    darkTheme = darkTheme,
                    reducedMotion = reducedMotion,
                    verifiedLyricsIds = verifiedLyricsIds,
                    onBack = { currentScreen = Screen.Home },
                    onPlayAll = { viewModel.playPlaylist(recentTracks) },
                    onShuffleAll = { viewModel.shuffleAlbum(recentTracks) },
                    onCheckLyrics = { viewModel.ensureLyricsVerified(it) },
                    onSongClick = { song, queue -> viewModel.playSong(song, queue) },
                    onFavoriteClick = { viewModel.toggleFavorite(it) }
                  )
                }
                else -> {
                  val moodId = screen.key.removePrefix("mood_")
                  val matchedMood = MoodCategory.entries.firstOrNull { it.id == moodId } ?: MoodCategory.CHILL
                  val moodPlaylist = moodPlaylists.firstOrNull { it.mood == matchedMood }
                  val moodTracks = moodPlaylist?.tracks.orEmpty()
                  val moodSub = "${matchedMood.description} · ${matchedMood.bpmRange.first}–${matchedMood.bpmRange.last} BPM"
                  PlaylistDetailScreen(
                    title = "${matchedMood.displayName} Mix",
                    subtitle = moodSub,
                    badgeLabel = stringResource(id = R.string.home_mood_mix_title),
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
                    onFavoriteClick = { viewModel.toggleFavorite(it) },
                    onSaveMix = if (moodTracks.isNotEmpty()) {
                      { viewModel.saveMixAsPlaylist("${matchedMood.displayName} Mix", moodTracks) }
                    } else null,
                    onShareMix = if (moodTracks.isNotEmpty()) {
                      { viewModel.shareMix(context, "${matchedMood.displayName} Mix", moodSub, moodTracks) }
                    } else null
                  )
                }
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
              onImmediateSearch = { viewModel.search(it, immediate = true) },
              onReportError = { title, err -> viewModel.reportError(title, err) },
              youtubePlaylist = youtubePlaylist,
              onPlayPlaylist = { shuffle -> viewModel.playLoadedPlaylist(shuffle) },
              onDownloadPlaylist = { viewModel.downloadLoadedPlaylist(context) },
              onSavePlaylist = { viewModel.saveLoadedPlaylist() },
              onOpenSavedPlaylistAlbum = { albumId ->
                viewModel.openAlbum(albumId)
                currentScreen = Screen.Album
              },
              onOpenPlaylistUrl = { url -> viewModel.openPlaylistFromUrl(url) },
              onClearYoutubeResult = { viewModel.clearYoutubeLinkResult() },
              downloadQueueItems = downloadQueueItems,
              onOpenDownloadQueue = { showDownloadQueueSheet = true },
              playlistSelectionMode = playlistSelectionMode,
              selectedPlaylistTrackIds = selectedPlaylistTrackIds,
              onTogglePlaylistSelectionMode = { viewModel.togglePlaylistSelectionMode() },
              onTogglePlaylistTrackSelection = { viewModel.togglePlaylistTrackSelection(it) },
              onSelectAllPlaylistTracks = { viewModel.selectAllPlaylistTracks() },
              onClearPlaylistTrackSelection = { viewModel.clearPlaylistTrackSelection() },
              onDownloadSelectedPlaylistTracks = { viewModel.downloadSelectedPlaylistTracks(context) },
              onSaveSelectedPlaylistTracks = { viewModel.saveSelectedPlaylistTracks() },
              onCancelPlaylistDownloads = { viewModel.cancelLoadedPlaylistDownloads(context) }
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
                viewModel.createAlbum(title, artist, year)
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
              updateUiState = updateUiState,
              onGithubOwnerChanged = { viewModel.setGithubOwner(it) },
              onGithubRepoChanged = { viewModel.setGithubRepo(it) },
              onCheckUpdate = { viewModel.checkForUpdates(it) },
              onDownloadOrInstallUpdate = { viewModel.downloadAndInstallUpdate(it) },
              onPauseDownload = { viewModel.pauseUpdateDownload() },
              onCancelDownload = { viewModel.cancelUpdateDownload() },
              onRetryUpdate = { viewModel.retryUpdateAction() },
              onOpenInstallSettings = { viewModel.openUnknownAppsSettings() },
              onRestartApp = { viewModel.restartApp(it) },
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
              onEqVirtualizerChanged = { viewModel.setVirtualizerStrength(it) },
              onResetEq = { viewModel.resetEqualizer() },
              crossfadeSettings = crossfadeSettings,
              onCrossfadeSecondsChanged = { viewModel.setCrossfadeSeconds(it) },
              onCrossfadeManualSkipChanged = { viewModel.setCrossfadeManualSkip(it) },
              hiddenSongsCount = hiddenSongIds.size,
              blockedArtistsCount = blockedArtists.size,
              onClearHiddenAndBlocked = { viewModel.clearHiddenAndBlocked() },
              logoState = logoState,
              onStartUpdateLogoIntro = { viewModel.startUpdateLogoIntro() },
              navStyle = navStyle,
              onOpenNavPreview = { showNavPreviewSheet = true },
              nowPlayingVisualizerEnabled = nowPlayingVisualizerEnabled,
              onNowPlayingVisualizerChanged = { viewModel.setNowPlayingVisualizerEnabled(it) },
              autoCleanYoutubeTitles = autoCleanYoutubeTitles,
              onAutoCleanYoutubeTitlesChanged = { viewModel.setAutoCleanYoutubeTitles(it) }
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
                onSwipeNext = { viewModel.nextTrack() },
                onSwipePrevious = { viewModel.previousTrack() },
                onClick = { showNowPlaying = true }
              )
              Spacer(modifier = Modifier.height(10.dp))
            }
          }

          val sampledSongArtColors = rememberArtworkColors(
            artworkUrl = currentSong?.artworkUrl.orEmpty(),
            fallbackTitle = currentSong?.title.orEmpty()
          )
          val songArtColors = if (currentSong != null) sampledSongArtColors else null
          val tabBarAmbient = if (dynamicBg && !amoledTheme) songArtColors else null
          if (AurixNavStyle.fromId(navStyle) == AurixNavStyle.BLUR) {
            LiquidBlurSplitTabBar(
              tabs = navTabs,
              selectedIndex = selectedIndex,
              onSelect = { index -> currentScreen = screens[index] },
              reducedMotion = reducedMotion,
              ambientColors = tabBarAmbient
            )
          } else {
            LiquidGlassTabBar(
              tabs = navTabs,
              selectedIndex = selectedIndex,
              onSelect = { index -> currentScreen = screens[index] },
              reducedMotion = reducedMotion,
              ambientColors = tabBarAmbient
            )
          }
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
            sleepRemainingSeconds = sleepRemainingSeconds,
            sleepEndOfTrack = sleepEndOfTrack,
            onPlayPause = { viewModel.togglePlayPause() },
            onNext = { viewModel.nextTrack() },
            onPrevious = { viewModel.previousTrack() },
            onToggleShuffle = { viewModel.toggleShuffle() },
            onToggleRepeat = { viewModel.toggleRepeat() },
            onCycleSpeed = { viewModel.cyclePlaybackSpeed() },
            onSetPlaybackSpeed = { viewModel.setPlaybackSpeed(it) },
            onCycleSleep = { viewModel.cycleSleepTimer() },
            onSetSleepMinutes = { viewModel.setSleepTimerMinutes(it) },
            onSetSleepEndOfTrack = { viewModel.setSleepEndOfTrack() },
            onCancelSleep = { viewModel.cancelSleepTimer() },
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
            onEqVirtualizerChanged = { viewModel.setVirtualizerStrength(it) },
            onResetEq = { viewModel.resetEqualizer() },
            queue = queue,
            onJumpToQueueIndex = { viewModel.jumpToQueueIndex(it) },
            onMoveQueueItem = { from, to -> viewModel.moveQueueItem(from, to) },
            onRemoveQueueItem = { viewModel.removeQueueItem(it) },
            onClearUpcomingQueue = { viewModel.clearUpcomingQueue() },
            onPlayNextSong = { viewModel.playNext(it) },
            onAddToQueueSong = { viewModel.addToQueue(it) },
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
            },
            moodFlowEnabled = moodFlowEnabled,
            moodFlowPick = moodFlowCurrentPick,
            onToggleMoodFlow = { viewModel.toggleMoodFlow() },
            visualizerEnabled = nowPlayingVisualizerEnabled,
            onToggleVisualizer = { viewModel.setNowPlayingVisualizerEnabled(it) },
            spectrumBars = spectrumBars
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
            runCatching {
              context.startActivity(android.content.Intent.createChooser(shareIntent, null))
            }.onFailure { err ->
              viewModel.reportError("Unable to Share Track", err)
            }
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
          },
          onPlayNext = { viewModel.playNext(detailTarget) },
          onAddToQueue = { viewModel.addToQueue(detailTarget) }
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

      // YouTube Format Picker Sheet when invoked from Home or Popular YouTube Detail
      if (currentScreen !is Screen.Search && formatSheetState !is YoutubeFormatSheetState.Closed) {
        YoutubeFormatPickerSheet(
          state = formatSheetState,
          darkTheme = darkTheme,
          reducedMotion = reducedMotion,
          onDismiss = { viewModel.closeYoutubeFormatSheet() },
          onSelectFormat = { viewModel.selectFormatSheetOption(it) },
          onPlayNow = { viewModel.playFromFormatSheet() },
          onDownload = { viewModel.downloadFromFormatSheet(context) },
          onRetry = {
            val errState = formatSheetState as? YoutubeFormatSheetState.Error
            if (errState != null) {
              viewModel.openYoutubeFormatSheet(errState.song)
            }
          },
          onOpenMySongs = {
            viewModel.closeYoutubeFormatSheet()
            libraryTab = 0
            currentScreen = Screen.Library
          }
        )
      }

      // One-time What's New bottom sheet on Home after first launch on new version
      if (launchSplashDismissed && showWhatsNewSheet && currentScreen is Screen.Home && !isNowPlayingOpen) {
        WhatsNewBottomSheet(
          reducedMotion = reducedMotion,
          onOpenUpdatesTab = { currentScreen = Screen.Settings },
          onPreviewNavigation = { showNavPreviewSheet = true },
          onDismiss = { viewModel.dismissWhatsNewSheet() }
        )
      }

      if (showNavPreviewSheet) {
        NavigationPreviewBottomSheet(
          currentNavStyle = navStyle,
          currentSong = currentSong,
          darkTheme = darkTheme,
          reducedMotion = reducedMotion,
          onApplyNavStyle = { viewModel.setNavStyle(it) },
          onDismiss = { showNavPreviewSheet = false }
        )
      }

      if (showDownloadQueueSheet) {
        DownloadQueueSheet(
          items = downloadQueueItems,
          darkTheme = darkTheme,
          onDismiss = { showDownloadQueueSheet = false },
          onCancelItem = { viewModel.cancelDownloadItem(it) },
          onRetryItem = { viewModel.retryDownloadItem(context, it) },
          onCancelAll = { viewModel.cancelAllDownloads() },
          onRetryAllFailed = { viewModel.retryAllFailedDownloads(context) },
          onClearFinished = { viewModel.clearFinishedDownloads() }
        )
      }

      // Optional Mandatory Critical Update Dialog
      val mandatoryRelease = updateUiState.latestRelease?.takeIf {
        it.isMandatory &&
          it.versionCode > updateUiState.installed.versionCode &&
          updateUiState.stage != UpdateStage.Idle &&
          updateUiState.stage != UpdateStage.Done
      }
      if (mandatoryRelease != null) {
        val isDownloading = updateUiState.stage == UpdateStage.Downloading && !updateUiState.isPaused
        val isValidated = updateUiState.isValidatedReady
        AlertDialog(
          onDismissRequest = {},
          properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
          title = { Text("Critical Update Required (${mandatoryRelease.tagName})") },
          text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
              Text(
                "Installed: v${updateUiState.installed.versionName} (code ${updateUiState.installed.versionCode})\n" +
                  "Latest: ${mandatoryRelease.tagName} (code ${mandatoryRelease.versionCode})"
              )
              if (mandatoryRelease.formattedNotes.isNotBlank()) {
                Text(mandatoryRelease.formattedNotes, style = MaterialTheme.typography.bodySmall)
              }
              if (isDownloading) {
                LinearProgressIndicator(
                  progress = { if (updateUiState.progress >= 0f) updateUiState.progress else 0.35f },
                  modifier = Modifier.fillMaxWidth()
                )
              }
              val errorMsg = updateUiState.errorMessage
              if (updateUiState.stage == UpdateStage.Error && !errorMsg.isNullOrBlank()) {
                Text(
                  text = errorMsg,
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
                  else -> "Download & Install"
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
