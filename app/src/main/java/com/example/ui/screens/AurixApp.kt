package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.CubicBezierEasing
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
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
  var showNowPlaying by remember { mutableStateOf(false) }
  val context = LocalContext.current

  val searchMode by viewModel.searchMode.collectAsStateWithLifecycle()
  val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
  val youtubeUrl by viewModel.youtubeUrl.collectAsStateWithLifecycle()
  val analyzedSong by viewModel.analyzedSong.collectAsStateWithLifecycle()
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
  val shuffleEnabled by viewModel.shuffleEnabled.collectAsStateWithLifecycle()
  val repeatEnabled by viewModel.repeatEnabled.collectAsStateWithLifecycle()
  val playbackSpeed by viewModel.playbackSpeed.collectAsStateWithLifecycle()
  val sleepMinutes by viewModel.sleepMinutes.collectAsStateWithLifecycle()

  val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
  val accentHex by viewModel.accentColorHex.collectAsStateWithLifecycle()
  val currentLanguage by viewModel.language.collectAsStateWithLifecycle()
  val dynamicBg by viewModel.dynamicBg.collectAsStateWithLifecycle()
  val reducedMotion by viewModel.reducedMotion.collectAsStateWithLifecycle()

  val systemDark = isSystemInDarkTheme()
  val darkTheme = when (themeMode) {
    "light" -> false
    "dark" -> true
    else -> systemDark
  }

  val accentColor = parseHexColor(accentHex, darkTheme)
  val isNowPlayingOpen = showNowPlaying && currentSong != null

  // System Back returns cleanly to Home from any secondary tab
  BackHandler(enabled = currentScreen != Screen.Home && !isNowPlayingOpen) {
    currentScreen = Screen.Home
  }

  val pageScale by animateFloatAsState(
    targetValue = if (isNowPlayingOpen) 0.93f else 1.0f,
    animationSpec = if (reducedMotion) snap() else tween(450, easing = AurixEase),
    label = "page_scale"
  )
  val pageAlpha by animateFloatAsState(
    targetValue = if (isNowPlayingOpen) 0.45f else 1.0f,
    animationSpec = if (reducedMotion) snap() else tween(450, easing = AurixEase),
    label = "page_alpha"
  )

  AurixTheme(darkTheme = darkTheme, accentColor = accentColor) {
    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(Color.Black)
    ) {
      // Main App Shell
      Box(
        modifier = Modifier
          .fillMaxSize()
          .scale(pageScale)
          .alpha(pageAlpha)
          .clip(RoundedCornerShape(if (isNowPlayingOpen) 28.dp else 0.dp))
          .background(MaterialTheme.colorScheme.background)
      ) {
        DynamicArtworkBackdrop(
          song = currentSong,
          enabled = dynamicBg,
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
              recentlyPlayed = recentlyPlayed,
              currentSong = currentSong,
              isPlaying = isPlaying,
              darkTheme = darkTheme,
              reducedMotion = reducedMotion,
              songDownloadProgress = songDownloadProgress,
              downloadedSongIds = downloadedSongIds,
              onRetryFeatured = { viewModel.loadFeaturedItunes() },
              onOpenSearch = { query ->
                viewModel.setSearchMode("itunes")
                if (!query.isNullOrBlank()) viewModel.search(query)
                currentScreen = Screen.Search
              },
              onOpenLibrary = { currentScreen = Screen.Library },
              onOpenNowPlaying = { showNowPlaying = true },
              onPlayPauseClick = { viewModel.togglePlayPause() },
              onSongClick = { song, queue -> viewModel.playSong(song, queue) },
              onFavoriteClick = { viewModel.toggleFavorite(it) },
              onDownloadClick = { viewModel.downloadSong(context, it) }
            )
            is Screen.Search -> SearchScreen(
              searchMode = searchMode,
              searchQuery = searchQuery,
              searchResults = searchResults,
              isSearching = isSearching,
              searchError = searchError,
              youtubeUrl = youtubeUrl,
              analyzedSong = analyzedSong,
              isAnalyzingYoutube = isAnalyzingYoutube,
              youtubeError = youtubeError,
              currentSong = currentSong,
              isPlaying = isPlaying,
              darkTheme = darkTheme,
              reducedMotion = reducedMotion,
              songDownloadProgress = songDownloadProgress,
              downloadedSongIds = downloadedSongIds,
              onSearchModeChanged = { viewModel.setSearchMode(it) },
              onSearchQueryChanged = { viewModel.search(it) },
              onYoutubeUrlChanged = { viewModel.setYoutubeUrl(it) },
              onAnalyzeYoutube = { viewModel.analyzeYoutubeUrl() },
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
              reducedMotion = reducedMotion,
              onAddLocalSong = { viewModel.addLocalSong(it) },
              onSongClick = { viewModel.playSong(it) },
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
              onReducedMotionChanged = { viewModel.setReducedMotion(it) }
            )
          }
        }

        // Bottom Floating MiniPlayer + Liquid Glass Navigation Bar
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
            visible = currentSong != null && !isNowPlayingOpen,
            enter = if (reducedMotion) fadeIn(snap()) else slideInVertically(tween(380, easing = AurixEase)) { it } + fadeIn(tween(260)),
            exit = if (reducedMotion) fadeOut(snap()) else slideOutVertically(tween(300, easing = AurixEase)) { it } + fadeOut(tween(200))
          ) {
            MiniPlayer(
              song = currentSong,
              isPlaying = isPlaying,
              currentPositionMs = currentPosition,
              durationMs = durationMs,
              darkTheme = darkTheme,
              reducedMotion = reducedMotion,
              onPlayPauseClick = { viewModel.togglePlayPause() },
              onFavoriteClick = { currentSong?.let { viewModel.toggleFavorite(it) } },
              onClick = { showNowPlaying = true }
            )
          }

          if (currentSong != null && !isNowPlayingOpen) {
            Spacer(modifier = Modifier.height(10.dp))
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
        enter = if (reducedMotion) fadeIn(snap()) else slideInVertically(tween(480, easing = AurixEase)) { it },
        exit = if (reducedMotion) fadeOut(snap()) else slideOutVertically(tween(420, easing = AurixEase)) { it }
      ) {
        currentSong?.let { song ->
          NowPlayingSheet(
            song = song,
            isPlaying = isPlaying,
            currentPositionMs = currentPosition,
            durationMs = durationMs,
            lyricsResult = lyrics,
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
            onLineClick = { viewModel.seekTo(it) }
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
