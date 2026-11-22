package com.example

import android.app.Application
import android.content.ComponentName
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Search
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import com.example.data.AURIX_PREFS_CORRUPTION_HANDLER
import com.example.data.PreferencesManager
import com.example.model.AudioFormatOption
import com.example.model.Song
import com.example.playback.PlaybackService
import com.example.ui.screens.AlbumTrackListSection
import com.example.ui.screens.AurixBrandLogo
import com.example.ui.screens.AurixLaunchOverlay
import com.example.ui.screens.FormatOptionRow
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.LiquidBlurSplitTabBar
import com.example.ui.screens.LiquidGlassTabBar
import com.example.ui.screens.MiniPlayer
import com.example.ui.screens.OrbitLoader
import com.example.ui.screens.PlayShuffleRow
import com.example.ui.screens.SongDownloadRow
import com.example.ui.screens.TabItem
import com.example.ui.screens.TrackRow
import com.example.ui.screens.UpdateLogo
import com.example.ui.screens.UpdateLogoState
import com.example.viewmodel.AurixViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class StartupStabilityTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  @Before
  fun setUp() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val shadowApp = Shadows.shadowOf(app)
    shadowApp.setBindServiceCallsOnServiceConnectedDirectly(false)
    shadowApp.declareComponentUnbindable(ComponentName(app, PlaybackService::class.java))
  }

  @Test
  fun crashHandler_logsToDiskDelegatesAndDoesNotRestartActivity() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val logFile = File(app.filesDir, "aurix_crash_logs.txt")
    logFile.delete()

    val delegated = AtomicBoolean(false)
    val handler = AurixApplication.createCrashHandler(
      context = app,
      defaultHandler = Thread.UncaughtExceptionHandler { _, _ -> delegated.set(true) }
    )

    handler.uncaughtException(Thread.currentThread(), IllegalStateException("Synthetic crash"))

    assertTrue(delegated.get())
    assertTrue(logFile.exists())
    assertTrue(logFile.readText().contains("Synthetic crash"))
    assertNull(Shadows.shadowOf(app).nextStartedActivity)
    logFile.delete()
  }

  @Test
  fun corruptedDataStore_recoversToDefaultsWithoutCrash() = runBlocking {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val dsDir = File(app.cacheDir, "corrupted_ds_test")
    dsDir.mkdirs()
    val prefsFile = File(dsDir, "test_aurix_prefs.preferences_pb")
    prefsFile.writeBytes(byteArrayOf(0x7F, 0x13, 0x00, 0x55, 0x2A, 0x11))

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    try {
      val isolatedDataStore = PreferenceDataStoreFactory.create(
        corruptionHandler = AURIX_PREFS_CORRUPTION_HANDLER,
        scope = scope,
        produceFile = { prefsFile }
      )
      val prefs = isolatedDataStore.data.first()
      assertEquals("system", prefs[PreferencesManager.THEME_MODE] ?: "system")
      assertEquals("classic", prefs[PreferencesManager.NAV_STYLE] ?: "classic")
    } finally {
      scope.cancel()
      prefsFile.delete()
    }
  }

  @Test
  fun aurixViewModel_initializesAndCompletesStartup() = runBlocking {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val viewModel = AurixViewModel(app)

    val initialized = withTimeout(5_000L) {
      viewModel.isAppInitialized.first { it }
    }
    assertTrue(initialized)
  }

  @Test
  fun zeroAndTinyBoundsComposables_renderSafelyWithoutCrash() {
    val sampleTabs = listOf(
      TabItem("Home", Icons.Outlined.Home),
      TabItem("Search", Icons.Outlined.Search)
    )
    val sampleSong = Song(
      id = "song_1",
      title = "Sample Track",
      artist = "AURIX Artist",
      artworkUrl = "",
      durationSeconds = 180L,
      streamUrl = "https://example.com/audio.mp3"
    )
    val sampleFormat = AudioFormatOption(
      id = "fmt_1",
      formatLabel = "M4A 256kbps",
      qualityLabel = "High",
      detailLabel = "AAC",
      extension = "m4a",
      mimeType = "audio/mp4",
      streamUrl = "https://example.com/audio.m4a",
      durationSeconds = 180L
    )

    composeTestRule.setContent {
      listOf(0.dp, 1.dp).forEach { bound ->
        Box(modifier = Modifier.size(bound)) {
          AurixBrandLogo(size = bound, animated = false, reducedMotion = true)
          OrbitLoader(size = bound, showComet = false, reducedMotion = true)
          UpdateLogo(state = UpdateLogoState.Available, size = bound, reducedMotion = true)
          AurixLaunchOverlay(
            isAppInitialized = false,
            darkTheme = true,
            reducedMotion = true,
            onDismissed = {}
          )
          LiquidBlurSplitTabBar(
            tabs = sampleTabs,
            selectedIndex = 0,
            onSelect = {},
            reducedMotion = true
          )
          LiquidGlassTabBar(
            tabs = sampleTabs,
            selectedIndex = 0,
            onSelect = {},
            reducedMotion = true
          )
          MiniPlayer(
            song = sampleSong,
            isPlaying = false,
            currentPositionMs = 1_000L,
            durationMs = 180_000L,
            darkTheme = true,
            reducedMotion = true,
            onPlayPauseClick = {},
            onFavoriteClick = {},
            onClick = {}
          )
          SongDownloadRow(
            song = sampleSong,
            index = 0,
            reducedMotion = true,
            darkTheme = true,
            onSongClick = {},
            onFavoriteClick = {}
          )
          PlayShuffleRow(
            isShuffleEnabled = false,
            onPlayClick = {},
            onShuffleClick = {}
          )
          TrackRow(
            trackNumber = 1,
            track = sampleSong,
            isCurrentTrack = false,
            isPlaying = false,
            reducedMotion = true,
            onClick = {}
          )
          FormatOptionRow(
            option = sampleFormat,
            selected = true,
            enabled = true,
            darkTheme = true,
            onClick = {}
          )
        }
      }
    }

    composeTestRule.waitForIdle()
  }

  @Test
  fun duplicateSongIds_renderSafelyInLibraryAndAlbumLists() {
    val duplicated = Song(
      id = "dup_id_42",
      title = "Repeated Song",
      artist = "Artist",
      artworkUrl = "https://example.com/cover.jpg",
      durationSeconds = 200L,
      streamUrl = "https://example.com/a.mp3"
    )
    val songs = listOf(duplicated, duplicated, duplicated.copy(title = "Copy Title"))

    composeTestRule.setContent {
      Box(modifier = Modifier.size(360.dp, 640.dp)) {
        LibraryScreen(
          localSongs = songs,
          favorites = songs,
          recentlyPlayed = songs,
          currentSong = null,
          isPlaying = false,
          darkTheme = true,
          reducedMotion = true,
          selectedTab = 0,
          onAddLocalSong = {},
          onCustomizeSongPhoto = { _, _ -> },
          onSongClick = {},
          onFavoriteClick = {}
        )
        AlbumTrackListSection(
          tracks = songs,
          currentSong = null,
          isPlaying = false,
          reducedMotion = true
        )
      }
    }

    composeTestRule.waitForIdle()
  }
}
