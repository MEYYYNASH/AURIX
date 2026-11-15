package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.ApkDownloadState
import com.example.data.UpdateCheckStatus
import com.example.ui.theme.*
import com.example.viewmodel.AurixViewModel

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
  object Home : Screen("home", "Home", Icons.Outlined.Home)
  object Search : Screen("search", "Search", Icons.Outlined.Search)
  object Library : Screen("library", "Library", Icons.Outlined.Folder)
  object Settings : Screen("settings", "Settings", Icons.Outlined.Settings)
}

private val AurixEase = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1.0f)

@Composable
fun AurixApp(viewModel: AurixViewModel) {
  val onboardingCompleted by viewModel.onboardingCompleted.collectAsStateWithLifecycle()
  var splashFinished by remember { mutableStateOf(false) }

  if (!splashFinished) {
    SplashScreen(onTimeout = { splashFinished = true })
    return
  }

  if (!onboardingCompleted) {
    OnboardingScreen(onFinished = { viewModel.setOnboardingCompleted(true) })
    return
  }

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

  val installedAppInfo by viewModel.installedAppInfo.collectAsStateWithLifecycle()
  val updateCheckStatus by viewModel.updateCheckStatus.collectAsStateWithLifecycle()
  val updateStatus by viewModel.updateStatus.collectAsStateWithLifecycle()
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
  val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
  val isSearching by viewModel.isSearching.collectAsStateWithLifecycle()
  val searchError by viewModel.searchError.collectAsStateWithLifecycle()

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
  val currentLanguage by viewModel.language.collectAsStateWithLifecycle()
  val dynamicBg by viewModel.dynamicBg.collectAsStateWithLifecycle()
  val reducedMotion by viewModel.reducedMotion.collectAsStateWithLifecycle()
  val verifiedLyricsIds by viewModel.verifiedLyricsIds.collectAsStateWithLifecycle()
  val equalizerState by viewModel.equalizerState.collectAsStateWithLifecycle()
  val hiddenSongIds by viewModel.hiddenSongIds.collectAsStateWithLifecycle()
  val blockedArtists by viewModel.blockedArtists.collectAsStateWithLifecycle()

  val systemDark = isSystemInDarkTheme()
  val amoledTheme = themeMode == "amoled"
  val darkTheme = when (themeMode) {
    "light" -> false
    "dark", "amoled" -> true
    else -> systemDark
  }

  val accentColor = parseHexColor(accentHex, darkTheme)
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

  AurixTheme(darkTheme = darkTheme, amoledTheme = amoledTheme, accentColor = accentColor) {
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

        AnimatedContent(
          targetState = currentScreen,
          modifier = Modifier.fillMaxSize(),
          transitionSpec = {
            if (reducedMotion) {
              fadeIn(snap()) togetherWith fadeOut(snap())
            } else {
              (fadeIn(tween(300, easing = AurixEase)) + slideInVertically(tween(300, easing = AurixEase)) { it / 24 }) togetherWith
                fadeOut(tween(180))
            }
          },
          label = "tab_content"
        ) { screen ->
          when (screen) {
            is Screen.Home -> HomeScreen(
              featuredTracks = featuredTracks,
              isLoadingFeatured = isLoadingFeatured,
              featuredError = featuredError,
              selectedFeed = selectedDailyFeed,
              dailyUpdatedLabel = dailyUpdatedLabel,
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
                viewModel.setSearchMode("itunes")
                currentScreen = Screen.Search
              },
              onOpenLibraryTab = { tab ->
                libraryTab = tab
                currentScreen = Screen.Library
              },
              onSongClick = { song, queue -> viewModel.playSong(song, queue) },
              onFavoriteClick = { viewModel.toggleFavorite(it) }
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
              verifiedLyricsIds = verifiedLyricsIds,
              onCheckLyrics = { viewModel.ensureLyricsVerified(it) },
              onSearchModeChanged = { viewModel.setSearchMode(it) },
              onSearchQueryChanged = { viewModel.search(it) },
              onYoutubeUrlChanged = { viewModel.setYoutubeUrl(it) },
              onAnalyzeYoutube = { viewModel.analyzeYoutubeUrl() },
              onSelectYoutubeFormat = { viewModel.selectYoutubeFormat(it) },
              onDownloadYoutube = { viewModel.downloadAnalyzedTrack(context) },
              onOpenMySongs = {
                libraryTab = 0
                currentScreen = Screen.Library
              },
              onSongClick = { song, queue -> viewModel.playSong(song, queue) },
              onFavoriteClick = { viewModel.toggleFavorite(it) },
              onDownloadClick = { viewModel.downloadSong(context, it) }
            )
            is Screen.Library -> LibraryScreen(
              localSongs = localSongs,
              favorites = favorites,
              recentlyPlayed = recentlyPlayed,
              currentSong = currentSong,
              isPlaying = isPlaying,
              darkTheme = darkTheme,
              reducedMotion = reducedMotion,
              verifiedLyricsIds = verifiedLyricsIds,
              selectedTab = libraryTab,
              onTabSelected = { libraryTab = it },
              onCheckLyrics = { viewModel.ensureLyricsVerified(it) },
              onAddLocalSong = { viewModel.addLocalSong(it) },
              onCustomizeSongPhoto = { songId, imageUri -> viewModel.updateLocalSongArtwork(songId, imageUri) },
              onDeleteLocalSong = { viewModel.deleteLocalSong(it) },
              onRemoveRecentSong = { viewModel.removeFromHistory(it) },
              onClearRecentHistory = { viewModel.clearHistory() },
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
              hiddenSongsCount = hiddenSongIds.size,
              blockedArtistsCount = blockedArtists.size,
              onClearHiddenAndBlocked = { viewModel.clearHiddenAndBlocked() }
            )
          }
        }

        // Subtle atmospheric gradient scrim so scrolling list rows fade gracefully behind the floating dock
        val dockScrimBase = if (darkTheme) Color(0xFF0A0E1A) else Color(0xFFEAF0FF)
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
        val navTabs = remember { screens.map { TabItem(it.title, it.icon) } }
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
            }
          )
        }
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
