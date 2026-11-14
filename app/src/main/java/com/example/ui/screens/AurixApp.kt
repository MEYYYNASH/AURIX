package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.*
import com.example.viewmodel.AurixViewModel

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
  object Home : Screen("home", "Home", Icons.Rounded.Home)
  object Search : Screen("search", "Search", Icons.Rounded.Search)
  object Library : Screen("library", "Library", Icons.Rounded.LibraryMusic)
  object Settings : Screen("settings", "Settings", Icons.Rounded.Settings)
}

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

  val appMode by viewModel.appMode.collectAsStateWithLifecycle()
  val youtubeUrl by viewModel.youtubeUrl.collectAsStateWithLifecycle()
  val analyzedSong by viewModel.analyzedSong.collectAsStateWithLifecycle()
  val updateStatus by viewModel.updateStatus.collectAsStateWithLifecycle()
  val latestRelease by viewModel.latestRelease.collectAsStateWithLifecycle()

  val trending by viewModel.trendingTracks.collectAsStateWithLifecycle()
  val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
  val isSearching by viewModel.isSearching.collectAsStateWithLifecycle()
  val localSongs by viewModel.localSongs.collectAsStateWithLifecycle()
  val favorites by viewModel.favorites.collectAsStateWithLifecycle()
  val recentlyPlayed by viewModel.recentlyPlayed.collectAsStateWithLifecycle()
  val currentSong by viewModel.currentSong.collectAsStateWithLifecycle()
  val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
  val currentPosition by viewModel.currentPosition.collectAsStateWithLifecycle()
  val lyrics by viewModel.lyrics.collectAsStateWithLifecycle()

  val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
  val accentHex by viewModel.accentColorHex.collectAsStateWithLifecycle()
  val dynamicBg by viewModel.dynamicBg.collectAsStateWithLifecycle()
  val reducedMotion by viewModel.reducedMotion.collectAsStateWithLifecycle()

  val systemDark = isSystemInDarkTheme()
  val darkTheme = when (themeMode) {
    "light" -> false
    "dark" -> true
    else -> systemDark
  }

  val accentColor = parseHexColor(accentHex)

  AurixTheme(darkTheme = darkTheme, accentColor = accentColor) {
    Box(modifier = Modifier.fillMaxSize()) {
      Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f)) {
          when (currentScreen) {
            is Screen.Home -> HomeScreen(
              appMode = appMode,
              youtubeUrl = youtubeUrl,
              analyzedSong = analyzedSong,
              trendingTracks = trending,
              favorites = favorites,
              recentlyPlayed = recentlyPlayed,
              localSongs = localSongs,
              onAppModeChanged = { viewModel.setAppMode(it) },
              onYoutubeUrlChanged = { viewModel.setYoutubeUrl(it) },
              onAnalyzeYoutube = { viewModel.analyzeYoutubeUrl() },
              onSongClick = { viewModel.playSong(it) },
              onMoodClick = { viewModel.search(it); currentScreen = Screen.Search },
              onFavoriteClick = { viewModel.toggleFavorite(it) }
            )
            is Screen.Search -> SearchScreen(
              searchResults = searchResults,
              isSearching = isSearching,
              onSearchQueryChanged = { viewModel.search(it) },
              onSongClick = { viewModel.playSong(it) },
              onFavoriteClick = { viewModel.toggleFavorite(it) }
            )
            is Screen.Library -> LibraryScreen(
              localSongs = localSongs,
              favorites = favorites,
              recentlyPlayed = recentlyPlayed,
              onAddLocalSong = { viewModel.addLocalSong(it) },
              onSongClick = { viewModel.playSong(it) },
              onFavoriteClick = { viewModel.toggleFavorite(it) }
            )
            is Screen.Settings -> SettingsScreen(
              currentTheme = themeMode,
              currentAccentHex = accentHex,
              currentLanguage = "en",
              dynamicBg = dynamicBg,
              reducedMotion = reducedMotion,
              updateStatus = updateStatus,
              latestRelease = latestRelease,
              onCheckUpdate = { viewModel.checkForUpdates(it) },
              onDownloadUpdate = { viewModel.downloadAndInstallUpdate(it) },
              onInstallUpdate = { viewModel.installDownloadedApk(it) },
              onThemeChanged = { viewModel.setThemeMode(it) },
              onAccentChanged = { viewModel.setAccentColor(it) },
              onLanguageChanged = { viewModel.setLanguage(it) },
              onDynamicBgChanged = { viewModel.setDynamicBg(it) },
              onReducedMotionChanged = { viewModel.setReducedMotion(it) }
            )
          }
        }
      }

      Column(
        modifier = Modifier
          .align(Alignment.BottomCenter)
          .windowInsetsPadding(WindowInsets.navigationBars)
          .padding(bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        MiniPlayer(
          song = currentSong,
          isPlaying = isPlaying,
          onPlayPauseClick = { viewModel.togglePlayPause() },
          onFavoriteClick = { currentSong?.let { viewModel.toggleFavorite(it) } },
          onClick = { showNowPlaying = true }
        )

        Spacer(modifier = Modifier.height(8.dp))

        Surface(
          modifier = Modifier
            .padding(horizontal = 24.dp)
            .clip(RoundedCornerShape(32.dp)),
          color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
          tonalElevation = 8.dp
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            val screens = listOf(Screen.Home, Screen.Search, Screen.Library, Screen.Settings)
            screens.forEach { screen ->
              val isSelected = currentScreen == screen
              IconButton(
                onClick = { currentScreen = screen },
                modifier = Modifier
                  .clip(CircleShape)
                  .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Color.Transparent)
              ) {
                Icon(
                  imageVector = screen.icon,
                  contentDescription = screen.title,
                  tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
            }

            FloatingActionButton(
              onClick = { showNowPlaying = true },
              shape = CircleShape,
              containerColor = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(48.dp)
            ) {
              Icon(
                imageVector = Icons.Rounded.Equalizer,
                contentDescription = "Now Playing",
                tint = Color.White
              )
            }
          }
        }
      }

      if (showNowPlaying && currentSong != null) {
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
          NowPlayingSheet(
            song = currentSong!!,
            isPlaying = isPlaying,
            currentPositionMs = currentPosition,
            lyricsResult = lyrics,
            onPlayPause = { viewModel.togglePlayPause() },
            onSeek = { viewModel.seekTo(it) },
            onFavorite = { viewModel.toggleFavorite(currentSong!!) },
            onClose = { showNowPlaying = false },
            onLineClick = { viewModel.seekTo(it) }
          )
        }
      }
    }
  }
}

fun parseHexColor(hex: String): Color {
  return try {
    Color(android.graphics.Color.parseColor(hex))
  } catch (e: Exception) {
    Color(0xFF3B77F5)
  }
}
