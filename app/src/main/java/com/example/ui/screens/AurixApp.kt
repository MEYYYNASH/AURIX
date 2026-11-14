package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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

  val appMode by viewModel.appMode.collectAsStateWithLifecycle()
  val youtubeUrl by viewModel.youtubeUrl.collectAsStateWithLifecycle()
  val analyzedSong by viewModel.analyzedSong.collectAsStateWithLifecycle()
  val updateStatus by viewModel.updateStatus.collectAsStateWithLifecycle()
  val latestRelease by viewModel.latestRelease.collectAsStateWithLifecycle()
  val apkDownloadState by viewModel.apkDownloadState.collectAsStateWithLifecycle()
  val songDownloadProgress by viewModel.songDownloadProgress.collectAsStateWithLifecycle()
  val downloadedSongIds by viewModel.downloadedSongIds.collectAsStateWithLifecycle()
  val githubOwner by viewModel.githubOwner.collectAsStateWithLifecycle()
  val githubRepo by viewModel.githubRepo.collectAsStateWithLifecycle()

  val trending by viewModel.trendingTracks.collectAsStateWithLifecycle()
  val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
  val isSearching by viewModel.isSearching.collectAsStateWithLifecycle()
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

  val accentColor = parseHexColor(accentHex)
  val isNowPlayingOpen = showNowPlaying && currentSong != null

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
      // Main App Shell (scales down & dims when Now Playing is open)
      Box(
        modifier = Modifier
          .fillMaxSize()
          .scale(pageScale)
          .alpha(pageAlpha)
          .clip(RoundedCornerShape(if (isNowPlayingOpen) 28.dp else 0.dp))
          .background(MaterialTheme.colorScheme.background)
      ) {
        // Dynamic Artwork Backdrop behind screens when enabled
        DynamicArtworkBackdrop(
          song = currentSong,
          enabled = dynamicBg,
          darkTheme = darkTheme,
          reducedMotion = reducedMotion
        )

        // Screen content with smooth animated transitions
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
              appMode = appMode,
              youtubeUrl = youtubeUrl,
              analyzedSong = analyzedSong,
              trendingTracks = trending,
              currentSong = currentSong,
              isPlaying = isPlaying,
              reducedMotion = reducedMotion,
              songDownloadProgress = songDownloadProgress,
              downloadedSongIds = downloadedSongIds,
              onAppModeChanged = { viewModel.setAppMode(it) },
              onYoutubeUrlChanged = { viewModel.setYoutubeUrl(it) },
              onAnalyzeYoutube = { viewModel.analyzeYoutubeUrl() },
              onSongClick = { viewModel.playSong(it) },
              onMoodClick = { viewModel.search(it); currentScreen = Screen.Search },
              onFavoriteClick = { viewModel.toggleFavorite(it) },
              onDownloadClick = { viewModel.downloadSong(context, it) }
            )
            is Screen.Search -> SearchScreen(
              searchResults = searchResults,
              isSearching = isSearching,
              currentSong = currentSong,
              isPlaying = isPlaying,
              reducedMotion = reducedMotion,
              onSearchQueryChanged = { viewModel.search(it) },
              onSongClick = { viewModel.playSong(it, searchResults) },
              onFavoriteClick = { viewModel.toggleFavorite(it) }
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

        // Bottom Floating MiniPlayer + Glass Navigation Bar
        Column(
          modifier = Modifier
            .align(Alignment.BottomCenter)
            .windowInsetsPadding(WindowInsets.navigationBars)
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

          Spacer(modifier = Modifier.height(10.dp))

          FloatingGlassNavBar(
            currentScreen = currentScreen,
            isPlaying = isPlaying,
            reducedMotion = reducedMotion,
            onScreenSelected = { currentScreen = it },
            onNowPlayingClick = {
              if (currentSong != null) {
                showNowPlaying = true
              } else {
                Toast.makeText(context, "Play a song first", Toast.LENGTH_SHORT).show()
              }
            }
          )
        }
      }

      // Now Playing Full-Screen Sheet with smooth slide-up animation
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
    }
  }
}

@Composable
fun FloatingGlassNavBar(
  currentScreen: Screen,
  isPlaying: Boolean,
  reducedMotion: Boolean,
  onScreenSelected: (Screen) -> Unit,
  onNowPlayingClick: () -> Unit
) {
  val screens = listOf(Screen.Home, Screen.Search, Screen.Library, Screen.Settings)
  val glassOuterBg = MaterialTheme.colorScheme.surface.copy(alpha = 0.86f)
  val glassBorderBrush = Brush.verticalGradient(
    colors = listOf(
      Color.White.copy(alpha = 0.24f),
      Color.White.copy(alpha = 0.05f)
    )
  )

  Surface(
    shape = RoundedCornerShape(999.dp),
    color = glassOuterBg,
    shadowElevation = 18.dp,
    modifier = Modifier
      .padding(horizontal = 16.dp)
      .border(1.dp, glassBorderBrush, RoundedCornerShape(999.dp))
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
      horizontalArrangement = Arrangement.spacedBy(10.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      // Dark inner pill containing the 4 tabs
      Row(
        modifier = Modifier
          .clip(RoundedCornerShape(999.dp))
          .background(Color(0xFF1C1C1C))
          .border(1.5.dp, Color(0xFF2C2C2C), RoundedCornerShape(999.dp))
          .padding(5.dp)
          .animateContentSize(
            animationSpec = if (reducedMotion) snap() else tween(350, easing = AurixEase)
          ),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        screens.forEach { screen ->
          val isSelected = currentScreen == screen
          Row(
            modifier = Modifier
              .height(44.dp)
              .clip(RoundedCornerShape(999.dp))
              .background(if (isSelected) Color.White else Color.Transparent)
              .clickable { onScreenSelected(screen) }
              .padding(horizontal = if (isSelected) 16.dp else 13.dp)
              .testTag("nav_tab_${screen.route}"),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Icon(
              imageVector = screen.icon,
              contentDescription = screen.title,
              tint = if (isSelected) Color(0xFF111111) else Color(0xFFB3B3B3),
              modifier = Modifier.size(21.dp)
            )
            AnimatedVisibility(
              visible = isSelected,
              enter = if (reducedMotion) fadeIn(snap()) else expandHorizontally(tween(300, easing = AurixEase)) + fadeIn(tween(240)),
              exit = if (reducedMotion) fadeOut(snap()) else shrinkHorizontally(tween(220, easing = AurixEase)) + fadeOut(tween(140))
            ) {
              Row {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                  text = screen.title,
                  style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                  color = Color(0xFF111111),
                  maxLines = 1
                )
              }
            }
          }
        }
      }

      // Right circular Now Playing button with animated equalizer bars when playing
      Box(
        modifier = Modifier
          .size(52.dp)
          .clip(CircleShape)
          .background(Color(0xFF1C1C1C))
          .border(1.5.dp, Color(0xFF2C2C2C), CircleShape)
          .clickable { onNowPlayingClick() }
          .testTag("nav_now_playing_fab"),
        contentAlignment = Alignment.Center
      ) {
        if (isPlaying) {
          EqualizerBars(
            isPlaying = true,
            color = Color.White,
            reducedMotion = reducedMotion,
            barWidth = 3.5.dp,
            maxHeight = 18.dp
          )
        } else {
          Icon(
            imageVector = Icons.Rounded.MusicNote,
            contentDescription = "Now Playing",
            tint = Color.White,
            modifier = Modifier.size(22.dp)
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
