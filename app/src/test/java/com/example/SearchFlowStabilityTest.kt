package com.example

import android.app.Application
import android.content.ComponentName
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import com.example.data.PreferencesManager
import com.example.data.YoutubeExtractor
import com.example.model.AudioFormatOption
import com.example.model.SearchMode
import com.example.model.SearchUiState
import com.example.model.Song
import com.example.model.YoutubeAnalysisResult
import com.example.model.YoutubeDownloadState
import com.example.model.YoutubeFormatSheetState
import com.example.playback.PlaybackService
import com.example.ui.screens.AurixApp
import com.example.ui.screens.SearchScreen
import com.example.ui.theme.AurixTheme
import com.example.viewmodel.AurixViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w412dp-h915dp-xhdpi")
class SearchFlowStabilityTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  @Before
  fun setUp() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val shadowApp = Shadows.shadowOf(app)
    shadowApp.setBindServiceCallsOnServiceConnectedDirectly(false)
    shadowApp.declareComponentUnbindable(ComponentName(app, PlaybackService::class.java))
  }

  private fun sampleSongs(): List<Song> {
    return (1..15).map { idx ->
      Song(
        id = "yt_vid_$idx",
        title = "Found Track #$idx",
        artist = "Artist $idx",
        artworkUrl = if (idx % 2 == 0) "https://i.ytimg.com/vi/vid_$idx/hqdefault.jpg" else "",
        durationSeconds = 195L + idx,
        streamUrl = "https://www.youtube.com/watch?v=vid_$idx",
        source = "YouTube",
        isFavorite = idx == 1,
        fullDurationSeconds = 195L + idx
      )
    }
  }

  @Test
  fun searchScreen_stateTransitionsWithAnimationsAndToggles_doNotCrash() {
    val songs = sampleSongs()
    var searchMode by mutableStateOf("youtube_search")
    var query by mutableStateOf("")
    var uiState by mutableStateOf<SearchUiState>(SearchUiState.Idle)
    var currentSong by mutableStateOf<Song?>(null)
    var darkTheme by mutableStateOf(true)
    var formatSheetState by mutableStateOf<YoutubeFormatSheetState>(YoutubeFormatSheetState.Closed)
    var clickedSong: Song? = null
    var downloadClickedSong: Song? = null

    composeTestRule.setContent {
      AurixTheme(darkTheme = darkTheme) {
        Box(modifier = Modifier.fillMaxSize()) {
          SearchScreen(
            searchMode = searchMode,
            searchQuery = query,
            searchResults = (uiState as? SearchUiState.Results)?.items.orEmpty(),
            isSearching = uiState is SearchUiState.Loading,
            searchError = (uiState as? SearchUiState.Error)?.message,
            youtubeUrl = "",
            youtubeAnalysis = null,
            selectedYoutubeFormatId = null,
            youtubeDownloadState = YoutubeDownloadState.Idle,
            isAnalyzingYoutube = false,
            youtubeError = null,
            currentSong = currentSong,
            isPlaying = currentSong != null,
            darkTheme = darkTheme,
            reducedMotion = false,
            songDownloadProgress = mapOf("yt_vid_2" to 0.45f),
            downloadedSongIds = setOf("yt_vid_3"),
            resolvingSongId = "yt_vid_4",
            formatSheetState = formatSheetState,
            verifiedLyricsIds = setOf("yt_vid_1", "yt_vid_2"),
            onSearchModeChanged = { searchMode = it },
            onSearchQueryChanged = { query = it },
            onYoutubeUrlChanged = {},
            onAnalyzeYoutube = {},
            onSelectYoutubeFormat = {},
            onDownloadYoutube = {},
            onOpenMySongs = {},
            onSongClick = { song, _ ->
              clickedSong = song
              currentSong = song
            },
            onFavoriteClick = {},
            onDownloadClick = { downloadClickedSong = it },
            onYoutubeSongDownloadClick = {
              downloadClickedSong = it
              formatSheetState = YoutubeFormatSheetState.Loading(it, it.id.removePrefix("yt_"))
            },
            onCloseFormatSheet = { formatSheetState = YoutubeFormatSheetState.Closed },
            searchUiState = uiState,
            onClearSearch = {
              query = ""
              uiState = SearchUiState.Idle
            },
            onRetrySearch = {
              uiState = SearchUiState.Loading(SearchMode.YouTubeSearch, query)
            },
            onImmediateSearch = { term ->
              query = term
              uiState = SearchUiState.Loading(SearchMode.YouTubeSearch, term)
            }
          )
        }
      }
    }

    composeTestRule.waitForIdle()

    // Toggle quick tags pill
    composeTestRule.onNodeWithTag("search_toggle_tags_button").performClick()
    composeTestRule.mainClock.advanceTimeBy(300)
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("search_toggle_tags_button").performClick()
    composeTestRule.mainClock.advanceTimeBy(300)
    composeTestRule.waitForIdle()

    // Tap quick tag chip
    composeTestRule.onNodeWithText("Trending").performClick()
    composeTestRule.mainClock.advanceTimeBy(400)
    composeTestRule.waitForIdle()
    assertTrue(uiState is SearchUiState.Loading)

    // Type more into input while Loading
    composeTestRule.onNodeWithTag("search_input_field").performTextInput(" about you")
    composeTestRule.onNodeWithTag("search_input_field").performImeAction()
    composeTestRule.mainClock.advanceTimeBy(400)
    composeTestRule.waitForIdle()

    // Transition Loading -> Results (no currentSong)
    uiState = SearchUiState.Results(songs)
    composeTestRule.mainClock.advanceTimeBy(800)
    composeTestRule.waitForIdle()

    // Click row to set currentSong (enables DynamicArtGlassBackdropLayer)
    composeTestRule.onNodeWithTag("song_row_yt_vid_1").performClick()
    composeTestRule.mainClock.advanceTimeBy(500)
    composeTestRule.waitForIdle()
    assertEquals("yt_vid_1", clickedSong?.id)

    // Toggle blur pill & collapse/expand results list
    composeTestRule.onNodeWithTag("search_blur_toggle_button").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("search_blur_toggle_button").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("search_toggle_results_button").performClick()
    composeTestRule.mainClock.advanceTimeBy(350)
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("search_toggle_results_button").performClick()
    composeTestRule.mainClock.advanceTimeBy(350)
    composeTestRule.waitForIdle()

    // Click favorite button right on the standalone row
    composeTestRule.onNodeWithTag("song_favorite_button_yt_vid_1").performClick()
    composeTestRule.waitForIdle()

    // Long-press row to open context menu, then click download -> opens YoutubeFormatPickerSheet
    composeTestRule.onNodeWithTag("song_row_yt_vid_1").performTouchInput { longClick() }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("song_download_button_yt_vid_1").performClick()
    composeTestRule.waitForIdle()
    assertEquals("yt_vid_1", downloadClickedSong?.id)

    val fmt = AudioFormatOption(
      id = "itag_140",
      formatLabel = "M4A",
      qualityLabel = "128 kbps AAC",
      detailLabel = "Full Song (3:15)",
      extension = "m4a",
      mimeType = "audio/mp4",
      streamUrl = "https://example.com/audio.m4a",
      durationSeconds = 195L
    )
    val analysis = YoutubeAnalysisResult(
      videoId = "vid_1",
      originalUrl = "https://www.youtube.com/watch?v=vid_1",
      videoTitle = "Found Track #1",
      channelName = "Artist 1",
      thumbnailUrl = "",
      matchedTrackTitle = "Found Track #1",
      matchedArtist = "Artist 1",
      durationSeconds = 195L,
      formats = listOf(fmt)
    )
    formatSheetState = YoutubeFormatSheetState.Ready(
      song = songs.first(),
      analysis = analysis,
      selectedFormatId = fmt.id,
      downloadState = YoutubeDownloadState.Idle
    )
    composeTestRule.waitForIdle()
    formatSheetState = YoutubeFormatSheetState.Closed
    composeTestRule.waitForIdle()

    // Transition Results -> Error -> Retry -> Empty -> Idle across Light & Dark themes
    darkTheme = false
    uiState = SearchUiState.Error("Search failed. Check your connection.")
    composeTestRule.mainClock.advanceTimeBy(400)
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("search_retry_button").performClick()
    composeTestRule.mainClock.advanceTimeBy(300)
    composeTestRule.waitForIdle()

    uiState = SearchUiState.Empty
    composeTestRule.mainClock.advanceTimeBy(400)
    composeTestRule.waitForIdle()

    uiState = SearchUiState.Idle
    query = ""
    composeTestRule.mainClock.advanceTimeBy(400)
    composeTestRule.waitForIdle()

    // Switch between YouTube Search and YT Link tabs
    composeTestRule.onNodeWithTag("mode_youtube_button").performClick()
    composeTestRule.mainClock.advanceTimeBy(300)
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("mode_youtube_search_button").performClick()
    composeTestRule.mainClock.advanceTimeBy(300)
    composeTestRule.waitForIdle()
  }

  @Test
  fun aurixAppWithRealViewModel_openSearchTypeAndSwitchModes_doesNotCrash() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val prefs = PreferencesManager(app)
    runBlocking {
      prefs.setLanguage("en")
      prefs.setOnboardingCompleted(true)
      prefs.setLastSeenVersionCode(BuildConfig.VERSION_CODE.toLong())
    }

    val viewModel = AurixViewModel(app)
    runBlocking {
      withTimeout(5_000L) {
        viewModel.isAppInitialized.first { it }
      }
    }
    viewModel.setOnboardingCompleted(true)
    viewModel.markLaunchSplashShown()

    composeTestRule.setContent {
      AurixApp(viewModel = viewModel)
    }

    composeTestRule.waitUntil(timeoutMillis = 8_000L) {
      Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
      composeTestRule.mainClock.advanceTimeBy(100)
      composeTestRule.onAllNodes(hasTestTag("nav_tab_search")).fetchSemanticsNodes().isNotEmpty()
    }

    composeTestRule.onNodeWithTag("nav_tab_search").performClick()
    composeTestRule.mainClock.advanceTimeBy(500)
    composeTestRule.waitForIdle()

    // Type search query character-by-character & submit
    composeTestRule.onNodeWithTag("search_input_field").performTextInput("van")
    composeTestRule.mainClock.advanceTimeBy(120)
    composeTestRule.onNodeWithTag("search_input_field").performTextInput("nda")
    composeTestRule.onNodeWithTag("search_input_field").performImeAction()

    composeTestRule.mainClock.advanceTimeBy(800)
    composeTestRule.waitForIdle()

    // Clear search while searching, then switch to YT Link
    composeTestRule.onNodeWithTag("search_clear_button").performClick()
    composeTestRule.mainClock.advanceTimeBy(400)
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("mode_youtube_button").performClick()
    composeTestRule.mainClock.advanceTimeBy(400)
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("youtube_url_input").performTextInput("https://youtu.be/dQw4w9WgXcQ")
    composeTestRule.onNodeWithTag("analyze_link_button").performClick()
    composeTestRule.mainClock.advanceTimeBy(500)
    composeTestRule.waitForIdle()
  }

  @Test
  fun searchScreen_typingSixSequentialQueriesAndStayingIdle_doesNotCrash() {
    val songs = sampleSongs()
    var query by mutableStateOf("")
    var uiState by mutableStateOf<SearchUiState>(SearchUiState.Idle)
    var currentSong by mutableStateOf<Song?>(songs.first())
    var lyricsChecks = 0

    composeTestRule.setContent {
      AurixTheme(darkTheme = true) {
        Box(modifier = Modifier.fillMaxSize()) {
          SearchScreen(
            searchMode = "youtube_search",
            searchQuery = query,
            searchResults = (uiState as? SearchUiState.Results)?.items.orEmpty(),
            isSearching = uiState is SearchUiState.Loading,
            searchError = null,
            youtubeUrl = "",
            youtubeAnalysis = null,
            selectedYoutubeFormatId = null,
            youtubeDownloadState = YoutubeDownloadState.Idle,
            isAnalyzingYoutube = false,
            youtubeError = null,
            currentSong = currentSong,
            isPlaying = true,
            darkTheme = true,
            reducedMotion = false,
            songDownloadProgress = emptyMap(),
            downloadedSongIds = emptySet(),
            onSearchModeChanged = {},
            onSearchQueryChanged = { query = it },
            onYoutubeUrlChanged = {},
            onAnalyzeYoutube = {},
            onSelectYoutubeFormat = {},
            onDownloadYoutube = {},
            onOpenMySongs = {},
            onSongClick = { song, _ -> currentSong = song },
            onFavoriteClick = {},
            onDownloadClick = {},
            onCheckLyrics = { lyricsChecks++ },
            searchUiState = uiState,
            onClearSearch = {
              query = ""
              uiState = SearchUiState.Idle
            },
            onRetrySearch = {},
            onImmediateSearch = { term ->
              query = term
              uiState = SearchUiState.Loading(SearchMode.YouTubeSearch, term)
            }
          )
        }
      }
    }

    composeTestRule.waitForIdle()

    val multiWordQueries = listOf(
      "vann",
      "da",
      " time",
      " song",
      " khmer",
      " remix"
    )

    multiWordQueries.forEachIndexed { idx, chunk ->
      composeTestRule.onNodeWithTag("search_input_field").performTextInput(chunk)
      uiState = SearchUiState.Loading(SearchMode.YouTubeSearch, query)
      composeTestRule.mainClock.advanceTimeBy(180)
      composeTestRule.waitForIdle()

      uiState = SearchUiState.Results(songs.drop(idx).take(10))
      composeTestRule.mainClock.advanceTimeBy(250)
      composeTestRule.waitForIdle()
    }

    // Stay idle on SearchScreen past debounced lyrics & metadata sync windows (>1.5s)
    composeTestRule.mainClock.advanceTimeBy(1800)
    composeTestRule.waitForIdle()

    assertTrue(lyricsChecks > 0)
    composeTestRule.onNodeWithTag("song_row_yt_vid_6").assertExists()
    composeTestRule.onNodeWithTag("song_favorite_button_yt_vid_6").performClick()
    composeTestRule.waitForIdle()
  }

  @Test
  fun aurixViewModel_searchRapidUpdatesAndInvalidInputs_doNotCrash() = runBlocking {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val viewModel = AurixViewModel(app)
    withTimeout(5_000L) {
      viewModel.isAppInitialized.first { it }
    }

    // Rapid query burst beyond buffer capacity should never throw or deadlock
    repeat(30) { idx ->
      viewModel.search("query_$idx", immediate = idx % 3 == 0)
    }
    viewModel.setSearchMode("youtube_link")
    viewModel.setSearchMode("youtube_search")
    viewModel.clearSearch()
    assertEquals(SearchUiState.Idle, viewModel.searchUiState.value)
    assertNull(viewModel.searchError.value)

    // Invalid YouTube URLs produce clean error state instead of throwing
    viewModel.setYoutubeUrl("")
    viewModel.analyzeYoutubeUrl()
    assertNotNull(viewModel.youtubeError.value)

    viewModel.setYoutubeUrl("https://example.com/not-youtube")
    viewModel.analyzeYoutubeUrl()
    assertNotNull(viewModel.youtubeError.value)

    // Playing a song with blank URL or YouTube watch URL recovers safely
    viewModel.playSong(
      Song(id = "blank_1", title = "Blank", artist = "Nobody", artworkUrl = "", durationSeconds = 120L, streamUrl = "")
    )
    viewModel.playSong(
      Song(
        id = "yt_invalid123",
        title = "YT Song",
        artist = "Artist",
        artworkUrl = "not a ::: valid uri",
        durationSeconds = 180L,
        streamUrl = "https://www.youtube.com/watch?v=invalid123",
        source = "YouTube"
      )
    )

    // Edge cases for YoutubeExtractor URL parsing
    assertNull(YoutubeExtractor.extractVideoId("   "))
    assertNull(YoutubeExtractor.extractVideoId("https://youtube.com/watch?v=bad"))
    assertEquals("dQw4w9WgXcQ", YoutubeExtractor.extractVideoId("https://www.youtube.com/live/dQw4w9WgXcQ?feature=share"))
  }
}
