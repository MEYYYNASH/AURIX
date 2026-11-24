package com.example.ui.screens

import android.content.ClipboardManager
import android.content.Context
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Analytics
import androidx.compose.material.icons.rounded.BlurOff
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.LibraryAddCheck
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.OndemandVideo
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.DownloadQueueItem
import com.example.data.DownloadQueueStatus
import com.example.model.SearchMode
import com.example.model.SearchUiState
import com.example.model.Song
import com.example.model.YoutubeAnalysisResult
import com.example.model.YoutubeDownloadState
import com.example.model.YoutubeFormatSheetState
import com.example.model.YoutubePlaylistResult
import com.example.ui.theme.AurixTheme
import java.util.Locale

@Composable
fun SearchScreen(
  searchMode: String,
  searchQuery: String,
  searchResults: List<Song>,
  isSearching: Boolean,
  searchError: String?,
  youtubeUrl: String,
  youtubeAnalysis: YoutubeAnalysisResult?,
  selectedYoutubeFormatId: String?,
  youtubeDownloadState: YoutubeDownloadState,
  isAnalyzingYoutube: Boolean,
  youtubeError: String?,
  currentSong: Song?,
  isPlaying: Boolean,
  darkTheme: Boolean,
  reducedMotion: Boolean,
  songDownloadProgress: Map<String, Float>,
  downloadedSongIds: Set<String>,
  resolvingSongId: String? = null,
  formatSheetState: YoutubeFormatSheetState = YoutubeFormatSheetState.Closed,
  verifiedLyricsIds: Set<String> = emptySet(),
  onCheckLyrics: (Song) -> Unit = {},
  onSearchModeChanged: (String) -> Unit,
  onSearchQueryChanged: (String) -> Unit,
  onYoutubeUrlChanged: (String) -> Unit,
  onAnalyzeYoutube: () -> Unit,
  onSelectYoutubeFormat: (String) -> Unit,
  onDownloadYoutube: () -> Unit,
  onPlayAnalyzedYoutube: () -> Unit = {},
  onOpenMySongs: () -> Unit,
  onSongClick: (Song, List<Song>) -> Unit,
  onFavoriteClick: (Song) -> Unit,
  onDownloadClick: (Song) -> Unit,
  onYoutubeSongDownloadClick: (Song) -> Unit = onDownloadClick,
  onCloseFormatSheet: () -> Unit = {},
  onSelectFormatSheetOption: (String) -> Unit = {},
  onPlayFromFormatSheet: () -> Unit = {},
  onDownloadFromFormatSheet: () -> Unit = {},
  onRetryFormatSheet: () -> Unit = {},
  searchUiState: SearchUiState = when {
    isSearching -> SearchUiState.Loading(
      mode = when (searchMode) {
        "youtube" -> SearchMode.YouTubeLink
        else -> SearchMode.YouTubeSearch
      },
      query = searchQuery
    )
    !searchError.isNullOrBlank() -> SearchUiState.Error(searchError)
    searchQuery.isBlank() -> SearchUiState.Idle
    searchResults.isEmpty() -> SearchUiState.Empty
    else -> SearchUiState.Results(searchResults)
  },
  onClearSearch: () -> Unit = { onSearchQueryChanged("") },
  onRetrySearch: () -> Unit = { onSearchQueryChanged(searchQuery) },
  onImmediateSearch: (String) -> Unit = onSearchQueryChanged,
  onReportError: (String, Throwable?) -> Unit = { _, _ -> },
  youtubePlaylist: YoutubePlaylistResult? = null,
  onPlayPlaylist: (Boolean) -> Unit = {},
  onDownloadPlaylist: () -> Unit = {},
  onSavePlaylist: () -> Unit = {},
  onOpenSavedPlaylistAlbum: (String) -> Unit = {},
  onOpenPlaylistUrl: (String) -> Unit = {},
  onClearYoutubeResult: () -> Unit = {},
  downloadQueueItems: List<DownloadQueueItem> = emptyList(),
  onOpenDownloadQueue: () -> Unit = {},
  playlistSelectionMode: Boolean = false,
  selectedPlaylistTrackIds: Set<String> = emptySet(),
  onTogglePlaylistSelectionMode: () -> Unit = {},
  onTogglePlaylistTrackSelection: (String) -> Unit = {},
  onSelectAllPlaylistTracks: () -> Unit = {},
  onClearPlaylistTrackSelection: () -> Unit = {},
  onDownloadSelectedPlaylistTracks: () -> Unit = {},
  onSaveSelectedPlaylistTracks: () -> Unit = {},
  onCancelPlaylistDownloads: () -> Unit = {}
) {
  val context = LocalContext.current
  val keyboardController = LocalSoftwareKeyboardController.current
  val quickTags = remember { listOf("Trending", "Khmer", "TikTok Viral", "Phonk", "Pop", "R&B", "Lo-Fi") }
  val activeModeEnum = when (searchMode) {
    "youtube" -> SearchMode.YouTubeLink
    else -> SearchMode.YouTubeSearch
  }
  val hasPlayingSong = currentSong != null
  val (artC1, artC2) = rememberArtworkColors(
    artworkUrl = currentSong?.artworkUrl.orEmpty(),
    fallbackTitle = currentSong?.title.orEmpty()
  )
  var showQuickTags by rememberSaveable { mutableStateOf(true) }
  var showResultsPanel by rememberSaveable { mutableStateOf(true) }
  var dynamicBlurGlass by rememberSaveable { mutableStateOf(true) }

  Box(
    modifier = Modifier.fillMaxSize(),
    contentAlignment = Alignment.TopCenter
  ) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .widthIn(max = 680.dp)
        .padding(horizontal = 20.dp)
        .windowInsetsPadding(WindowInsets.statusBars)
    ) {
      Spacer(modifier = Modifier.height(14.dp))

      // Clean Editorial Glass Header
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        AurixBrandLogo(
          size = 38.dp,
          cornerRadius = 12.dp,
          animated = false,
          reducedMotion = reducedMotion
        )
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = stringResource(id = R.string.search_title),
            style = MaterialTheme.typography.headlineMedium.copy(
              fontSize = 26.sp,
              lineHeight = 32.sp,
              fontWeight = FontWeight.ExtraBold
            ),
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
          Text(
            text = stringResource(id = R.string.search_subtitle),
            style = MaterialTheme.typography.bodySmall.copy(lineHeight = 17.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }
        if (downloadQueueItems.isNotEmpty()) {
          val activeQueueCount = downloadQueueItems.count { it.isActive }
          val hasFailed = downloadQueueItems.any { it.status == DownloadQueueStatus.FAILED }
          val pillAccent = when {
            activeQueueCount > 0 -> Color(0xFF2F8BFF)
            hasFailed -> Color(0xFFF59E0B)
            else -> Color(0xFF10B981)
          }
          Surface(
            onClick = onOpenDownloadQueue,
            shape = CircleShape,
            color = pillAccent.copy(alpha = if (darkTheme) 0.18f else 0.14f),
            border = BorderStroke(1.dp, pillAccent.copy(alpha = 0.45f)),
            modifier = Modifier
              .minimumInteractiveComponentSize()
              .testTag("open_download_queue_button")
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
              if (activeQueueCount > 0) {
                CircularProgressIndicator(
                  modifier = Modifier.size(14.dp),
                  strokeWidth = 2.dp,
                  color = pillAccent
                )
              } else {
                Icon(
                  imageVector = Icons.Rounded.Download,
                  contentDescription = null,
                  tint = pillAccent,
                  modifier = Modifier.size(16.dp)
                )
              }
              Text(
                text = if (activeQueueCount > 0) {
                  stringResource(id = R.string.download_queue_pill_active, activeQueueCount)
                } else {
                  stringResource(id = R.string.download_queue_pill_ready, downloadQueueItems.size)
                },
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = pillAccent,
                maxLines = 1
              )
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(14.dp))

      // Liquid Glass 2-Mode Switcher (YouTube Search | YT Link)
      GlassSegmentedBar(
        options = listOf(
          stringResource(id = R.string.search_mode_yt_search) to Icons.Rounded.PlayCircle,
          stringResource(id = R.string.search_mode_youtube) to Icons.Rounded.Link
        ),
        selectedIndex = when (searchMode) {
          "youtube" -> 1
          else -> 0
        },
        darkTheme = darkTheme,
        onSelect = { idx ->
          val next = when (idx) {
            1 -> "youtube"
            else -> "youtube_search"
          }
          onSearchModeChanged(next)
        },
        testTags = listOf("mode_youtube_search_button", "mode_youtube_button"),
        reducedMotion = reducedMotion
      )

      Spacer(modifier = Modifier.height(14.dp))

      val modeEase = remember { CubicBezierEasing(0.22f, 1.0f, 0.36f, 1.0f) }
      AnimatedContent(
        targetState = searchMode,
        modifier = Modifier
          .weight(1f)
          .fillMaxWidth(),
        transitionSpec = {
          if (reducedMotion) {
            fadeIn(snap()) togetherWith fadeOut(snap())
          } else {
            val goingToLink = targetState == "youtube" && initialState != "youtube"
            (
              slideInHorizontally(
                animationSpec = tween(durationMillis = 330, easing = modeEase)
              ) { fullWidth ->
                if (goingToLink) (fullWidth * 0.24f).toInt() else -(fullWidth * 0.24f).toInt()
              } +
                fadeIn(
                  animationSpec = tween(durationMillis = 250, delayMillis = 35, easing = modeEase)
                ) +
                scaleIn(
                  animationSpec = tween(durationMillis = 330, easing = modeEase),
                  initialScale = 0.96f
                )
              ) togetherWith (
              slideOutHorizontally(
                animationSpec = tween(durationMillis = 240, easing = modeEase)
              ) { fullWidth ->
                if (goingToLink) -(fullWidth * 0.14f).toInt() else (fullWidth * 0.14f).toInt()
              } +
                fadeOut(
                  animationSpec = tween(durationMillis = 170, easing = modeEase)
                ) +
                scaleOut(
                  animationSpec = tween(durationMillis = 240, easing = modeEase),
                  targetScale = 0.96f
                )
              ) using SizeTransform(clip = false)
          }
        },
        contentKey = { mode -> if (mode == "youtube") "youtube_link" else "youtube_search" },
        label = "search_mode_content"
      ) { mode ->
        if (mode == "youtube") {
          YoutubeLinkGlassPane(
            youtubeUrl = youtubeUrl,
            youtubeAnalysis = youtubeAnalysis,
            youtubePlaylist = youtubePlaylist,
            selectedYoutubeFormatId = selectedYoutubeFormatId,
            youtubeDownloadState = youtubeDownloadState,
            isAnalyzingYoutube = isAnalyzingYoutube,
            youtubeError = youtubeError,
            currentSong = currentSong,
            isPlaying = isPlaying,
            resolvingSongId = resolvingSongId,
            darkTheme = darkTheme,
            reducedMotion = reducedMotion,
            songDownloadProgress = songDownloadProgress,
            downloadedSongIds = downloadedSongIds,
            verifiedLyricsIds = verifiedLyricsIds,
            onCheckLyrics = onCheckLyrics,
            onYoutubeUrlChanged = onYoutubeUrlChanged,
            onAnalyzeYoutube = {
              keyboardController?.hide()
              onAnalyzeYoutube()
            },
            onSelectYoutubeFormat = onSelectYoutubeFormat,
            onPlayAnalyzedYoutube = onPlayAnalyzedYoutube,
            onDownloadYoutube = onDownloadYoutube,
            onPlaySaved = { saved -> onSongClick(saved, listOf(saved)) },
            onOpenMySongs = onOpenMySongs,
            onPlayPlaylist = onPlayPlaylist,
            onDownloadPlaylist = onDownloadPlaylist,
            onSavePlaylist = onSavePlaylist,
            onOpenSavedPlaylistAlbum = onOpenSavedPlaylistAlbum,
            onOpenPlaylistUrl = onOpenPlaylistUrl,
            onClearYoutubeResult = onClearYoutubeResult,
            playlistSelectionMode = playlistSelectionMode,
            selectedPlaylistTrackIds = selectedPlaylistTrackIds,
            onTogglePlaylistSelectionMode = onTogglePlaylistSelectionMode,
            onTogglePlaylistTrackSelection = onTogglePlaylistTrackSelection,
            onSelectAllPlaylistTracks = onSelectAllPlaylistTracks,
            onClearPlaylistTrackSelection = onClearPlaylistTrackSelection,
            onDownloadSelectedPlaylistTracks = onDownloadSelectedPlaylistTracks,
            onSaveSelectedPlaylistTracks = onSaveSelectedPlaylistTracks,
            onCancelPlaylistDownloads = onCancelPlaylistDownloads,
            onOpenDownloadQueue = onOpenDownloadQueue,
            hasQueueItems = downloadQueueItems.isNotEmpty(),
            onSongClick = onSongClick,
            onFavoriteClick = onFavoriteClick,
            onTrackDownloadClick = onYoutubeSongDownloadClick,
            onReportError = onReportError,
            context = context
          )
        } else {
          Column(modifier = Modifier.fillMaxSize()) {
            // Frosted Liquid Glass Search Input Pill
            LiquidGlassSearchBar(
              query = searchQuery,
              placeholder = stringResource(
                id = if (mode == "youtube_search") R.string.search_yt_songs_placeholder else R.string.search_placeholder
              ),
              darkTheme = darkTheme,
              reducedMotion = reducedMotion,
              ambientPrimary = if (dynamicBlurGlass && hasPlayingSong) artC1 else null,
              ambientSecondary = if (dynamicBlurGlass && hasPlayingSong) artC2 else null,
              onQueryChanged = { nextQuery ->
                if (!showResultsPanel && nextQuery.isNotBlank()) showResultsPanel = true
                onSearchQueryChanged(nextQuery)
              },
              onClear = onClearSearch,
              onSubmit = {
                keyboardController?.hide()
                showResultsPanel = true
                if (searchQuery.isNotBlank()) onImmediateSearch(searchQuery)
              }
            )

            // Quick discovery glass chips with Open / Close control
            AnimatedVisibility(
              visible = searchQuery.isBlank(),
              enter = if (reducedMotion) fadeIn(snap()) else fadeIn(tween(180)) + expandVertically(),
              exit = if (reducedMotion) fadeOut(snap()) else fadeOut(tween(140)) + shrinkVertically()
            ) {
              Column {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Text(
                    text = stringResource(id = R.string.search_tags_title),
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                  OpenCloseGlassPill(
                    isOpen = showQuickTags,
                    onToggle = { showQuickTags = !showQuickTags },
                    darkTheme = darkTheme,
                    reducedMotion = reducedMotion,
                    testTag = "search_toggle_tags_button",
                    contentDescription = stringResource(
                      id = if (showQuickTags) R.string.section_close_cd else R.string.section_open_cd,
                      stringResource(id = R.string.search_tags_title)
                    )
                  )
                }

                AnimatedVisibility(
                  visible = showQuickTags,
                  enter = if (reducedMotion) fadeIn(snap()) else fadeIn(tween(180)) + expandVertically(),
                  exit = if (reducedMotion) fadeOut(snap()) else fadeOut(tween(140)) + shrinkVertically()
                ) {
                  Column {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                      modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                      horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                      quickTags.forEach { tag ->
                        GlassQuickTagChip(
                          label = tag,
                          darkTheme = darkTheme,
                          reducedMotion = reducedMotion,
                          onClick = {
                            keyboardController?.hide()
                            showResultsPanel = true
                            onImmediateSearch(tag)
                          }
                        )
                      }
                    }
                  }
                }
              }
            }

            Spacer(modifier = Modifier.height(12.dp))

            SearchResultsArea(
              uiState = searchUiState,
              searchMode = activeModeEnum,
              currentSong = currentSong,
              isPlaying = isPlaying,
              resolvingSongId = resolvingSongId,
              darkTheme = darkTheme,
              reducedMotion = reducedMotion,
              songDownloadProgress = songDownloadProgress,
              downloadedSongIds = downloadedSongIds,
              verifiedLyricsIds = verifiedLyricsIds,
              artColors = if (hasPlayingSong) (artC1 to artC2) else null,
              dynamicBlurGlass = dynamicBlurGlass,
              onToggleDynamicBlur = { dynamicBlurGlass = !dynamicBlurGlass },
              showResultsList = showResultsPanel,
              onToggleResultsList = { showResultsPanel = !showResultsPanel },
              onCheckLyrics = onCheckLyrics,
              onSongClick = onSongClick,
              onFavoriteClick = onFavoriteClick,
              onDownloadClick = { song ->
                if (song.source == "YouTube") onYoutubeSongDownloadClick(song) else onDownloadClick(song)
              },
              onRetry = onRetrySearch,
              onQuickSuggestionClick = { tag ->
                keyboardController?.hide()
                showResultsPanel = true
                onImmediateSearch(tag)
              },
              onClearSearch = onClearSearch,
              modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
            )
          }
        }
      }
    }

    if (formatSheetState !is YoutubeFormatSheetState.Closed) {
      YoutubeFormatPickerSheet(
        state = formatSheetState,
        darkTheme = darkTheme,
        reducedMotion = reducedMotion,
        onDismiss = onCloseFormatSheet,
        onSelectFormat = onSelectFormatSheetOption,
        onPlayNow = onPlayFromFormatSheet,
        onDownload = onDownloadFromFormatSheet,
        onRetry = onRetryFormatSheet,
        onOpenMySongs = onOpenMySongs
      )
    }
  }
}

@Composable
private fun LiquidGlassSearchBar(
  query: String,
  placeholder: String,
  darkTheme: Boolean,
  reducedMotion: Boolean,
  ambientPrimary: Color? = null,
  ambientSecondary: Color? = null,
  onQueryChanged: (String) -> Unit,
  onClear: () -> Unit,
  onSubmit: () -> Unit
) {
  var isFocused by remember { mutableStateOf(false) }

  val glassBrush = if (darkTheme) {
    Brush.linearGradient(
      colors = listOf(
        (ambientPrimary ?: Color(0xFF23304B)).copy(alpha = if (ambientPrimary != null) 0.24f else 0.80f),
        (ambientSecondary ?: Color(0xFF151F35)).copy(alpha = if (ambientSecondary != null) 0.16f else 0.72f),
        Color(0xFF101829).copy(alpha = 0.78f)
      )
    )
  } else {
    Brush.linearGradient(
      colors = listOf(
        Color.White.copy(alpha = 0.94f),
        (ambientPrimary ?: Color(0xFFF3F7FF)).copy(alpha = if (ambientPrimary != null) 0.14f else 0.82f),
        Color.White.copy(alpha = 0.90f)
      )
    )
  }

  val borderColor by animateColorAsState(
    targetValue = when {
      isFocused -> (ambientPrimary ?: MaterialTheme.colorScheme.primary).copy(alpha = 0.84f)
      ambientPrimary != null -> ambientPrimary.copy(alpha = if (darkTheme) 0.42f else 0.48f)
      darkTheme -> Color.White.copy(alpha = 0.24f)
      else -> Color.White
    },
    animationSpec = if (reducedMotion) snap() else tween(180),
    label = "search_bar_border"
  )
  val topSpecular = Color.White.copy(alpha = if (darkTheme) 0.34f else 0.96f)

  Box(
    modifier = Modifier
      .fillMaxWidth()
      .drawBehind {
        if (size.width <= 0f || size.height <= 0f) return@drawBehind
        val h = size.height
        val r = h / 2f
        drawRoundRect(
          brush = Brush.verticalGradient(
            colors = listOf(
              Color.Black.copy(alpha = if (darkTheme) 0.18f else 0.04f),
              Color.Black.copy(alpha = if (darkTheme) 0.28f else 0.08f)
            )
          ),
          topLeft = Offset(0f, 4.dp.toPx()),
          size = Size(size.width, h),
          cornerRadius = CornerRadius(r, r)
        )
      }
      .clip(CircleShape)
      .background(glassBrush)
      .border(width = if (isFocused) 1.4.dp else 1.dp, color = borderColor, shape = CircleShape)
      .drawBehind {
        val stroke = 1.2.dp.toPx()
        if (size.width <= stroke || size.height <= stroke) return@drawBehind
        val inset = stroke * 0.5f
        val r = (size.height / 2f - inset).coerceAtLeast(0f)
        drawRoundRect(
          brush = Brush.verticalGradient(
            colors = listOf(topSpecular, Color.Transparent),
            startY = 0f,
            endY = size.height * 0.45f
          ),
          topLeft = Offset(inset, inset),
          size = Size(size.width - stroke, size.height - stroke),
          cornerRadius = CornerRadius(r, r),
          style = Stroke(width = stroke)
        )
      }
  ) {
    var textFieldValue by remember {
      mutableStateOf(TextFieldValue(query, TextRange(query.length)))
    }
    LaunchedEffect(query) {
      if (textFieldValue.text != query) {
        textFieldValue = TextFieldValue(query, TextRange(query.length))
      }
    }

    TextField(
      value = textFieldValue,
      onValueChange = { updated ->
        textFieldValue = updated
        if (updated.text != query) {
          onQueryChanged(updated.text)
        }
      },
      placeholder = {
        Text(
          text = placeholder,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.80f)
        )
      },
      leadingIcon = {
        Icon(
          imageVector = Icons.Rounded.Search,
          contentDescription = null,
          tint = if (isFocused) MaterialTheme.colorScheme.primary else AurixTealAccent,
          modifier = Modifier.size(21.dp)
        )
      },
      trailingIcon = {
        if (query.isNotEmpty()) {
          IconButton(
            onClick = onClear,
            modifier = Modifier
              .minimumInteractiveComponentSize()
              .testTag("search_clear_button")
          ) {
            Icon(
              imageVector = Icons.Rounded.Close,
              contentDescription = "Clear search",
              tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }
      },
      keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
      keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
      colors = TextFieldDefaults.colors(
        focusedContainerColor = Color.Transparent,
        unfocusedContainerColor = Color.Transparent,
        focusedIndicatorColor = Color.Transparent,
        unfocusedIndicatorColor = Color.Transparent,
        disabledIndicatorColor = Color.Transparent
      ),
      modifier = Modifier
        .fillMaxWidth()
        .onFocusChanged { isFocused = it.isFocused }
        .testTag("search_input_field"),
      singleLine = true
    )
  }
}

@Composable
private fun GlassQuickTagChip(
  label: String,
  darkTheme: Boolean,
  reducedMotion: Boolean = false,
  onClick: () -> Unit
) {
  val interactionSource = remember { MutableInteractionSource() }
  val isPressed by interactionSource.collectIsPressedAsState()
  val isHovered by interactionSource.collectIsHoveredAsState()

  val chipScale by animateFloatAsState(
    targetValue = when {
      isPressed -> 0.945f
      isHovered -> 1.03f
      else -> 1f
    },
    animationSpec = if (reducedMotion) snap() else spring(
      dampingRatio = 0.68f,
      stiffness = Spring.StiffnessMedium
    ),
    label = "quick_tag_spring_scale"
  )
  val borderColor by animateColorAsState(
    targetValue = when {
      isPressed || isHovered -> MaterialTheme.colorScheme.primary.copy(alpha = 0.58f)
      darkTheme -> Color.White.copy(alpha = 0.22f)
      else -> Color.White
    },
    animationSpec = if (reducedMotion) snap() else spring(stiffness = Spring.StiffnessMediumLow),
    label = "quick_tag_border"
  )

  val chipBrush = if (darkTheme) {
    Brush.linearGradient(
      colors = listOf(Color(0xFF222F4A).copy(alpha = 0.72f), Color(0xFF151E33).copy(alpha = 0.62f))
    )
  } else {
    Brush.linearGradient(
      colors = listOf(Color.White.copy(alpha = 0.92f), Color(0xFFF3F6FF).copy(alpha = 0.78f))
    )
  }

  Box(
    modifier = Modifier
      .defaultMinSize(minHeight = 40.dp)
      .graphicsLayer {
        scaleX = chipScale
        scaleY = chipScale
      }
      .clip(CircleShape)
      .background(chipBrush)
      .border(1.dp, borderColor, CircleShape)
      .hoverable(interactionSource)
      .clickable(
        interactionSource = interactionSource,
        indication = LocalIndication.current
      ) { onClick() }
      .padding(horizontal = 15.dp, vertical = 9.dp),
    contentAlignment = Alignment.Center
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
      color = MaterialTheme.colorScheme.onSurface,
      maxLines = 1
    )
  }
}

@Composable
private fun YoutubeLinkGlassPane(
  youtubeUrl: String,
  youtubeAnalysis: YoutubeAnalysisResult?,
  youtubePlaylist: YoutubePlaylistResult?,
  selectedYoutubeFormatId: String?,
  youtubeDownloadState: YoutubeDownloadState,
  isAnalyzingYoutube: Boolean,
  youtubeError: String?,
  currentSong: Song?,
  isPlaying: Boolean,
  resolvingSongId: String?,
  darkTheme: Boolean,
  reducedMotion: Boolean,
  songDownloadProgress: Map<String, Float>,
  downloadedSongIds: Set<String>,
  verifiedLyricsIds: Set<String>,
  onCheckLyrics: (Song) -> Unit,
  onYoutubeUrlChanged: (String) -> Unit,
  onAnalyzeYoutube: () -> Unit,
  onSelectYoutubeFormat: (String) -> Unit,
  onPlayAnalyzedYoutube: () -> Unit,
  onDownloadYoutube: () -> Unit,
  onPlaySaved: (Song) -> Unit,
  onOpenMySongs: () -> Unit,
  onPlayPlaylist: (Boolean) -> Unit,
  onDownloadPlaylist: () -> Unit,
  onSavePlaylist: () -> Unit,
  onOpenSavedPlaylistAlbum: (String) -> Unit,
  onOpenPlaylistUrl: (String) -> Unit,
  onClearYoutubeResult: () -> Unit,
  playlistSelectionMode: Boolean,
  selectedPlaylistTrackIds: Set<String>,
  onTogglePlaylistSelectionMode: () -> Unit,
  onTogglePlaylistTrackSelection: (String) -> Unit,
  onSelectAllPlaylistTracks: () -> Unit,
  onClearPlaylistTrackSelection: () -> Unit,
  onDownloadSelectedPlaylistTracks: () -> Unit,
  onSaveSelectedPlaylistTracks: () -> Unit,
  onCancelPlaylistDownloads: () -> Unit,
  onOpenDownloadQueue: () -> Unit,
  hasQueueItems: Boolean,
  onSongClick: (Song, List<Song>) -> Unit,
  onFavoriteClick: (Song) -> Unit,
  onTrackDownloadClick: (Song) -> Unit,
  onReportError: (String, Throwable?) -> Unit = { _, _ -> },
  context: Context
) {
  val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
  val listBottomPadding = (if (currentSong != null) 205.dp else 135.dp) + navBarBottom
  val inputBg = if (darkTheme) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.85f)
  val inputBorder = if (darkTheme) Color.White.copy(alpha = 0.22f) else Color.White
  val pasteBg = if (darkTheme) Color.White.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.85f)
  var showYoutubeGuide by rememberSaveable { mutableStateOf(true) }

  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    verticalArrangement = Arrangement.spacedBy(16.dp),
    contentPadding = PaddingValues(bottom = listBottomPadding)
  ) {
    item {
      GlassCard(
        darkTheme = darkTheme,
        cornerRadius = 28.dp,
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(
          modifier = Modifier.padding(18.dp),
          verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
          ) {
            Box(
              modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(
                  Brush.linearGradient(
                    colors = listOf(
                      Color(0xFFEF4444).copy(alpha = 0.24f),
                      MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                    )
                  )
                )
                .border(1.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(14.dp)),
              contentAlignment = Alignment.Center
            ) {
              Icon(
                imageVector = Icons.Rounded.OndemandVideo,
                contentDescription = null,
                tint = Color(0xFFEF4444),
                modifier = Modifier.size(22.dp)
              )
            }
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = stringResource(id = R.string.search_yt_title),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold, lineHeight = 24.sp),
                color = MaterialTheme.colorScheme.onSurface
              )
              Text(
                text = stringResource(id = R.string.search_yt_subtitle),
                style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          }

          TextField(
            value = youtubeUrl,
            onValueChange = onYoutubeUrlChanged,
            placeholder = {
              Text(
                text = stringResource(id = R.string.search_yt_placeholder),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
            },
            leadingIcon = {
              Icon(
                imageVector = Icons.Rounded.Link,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
              )
            },
            trailingIcon = {
              if (youtubeUrl.isNotEmpty()) {
                IconButton(
                  onClick = { onYoutubeUrlChanged("") },
                  modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                  Icon(Icons.Rounded.Close, contentDescription = "Clear URL")
                }
              }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onAnalyzeYoutube() }),
            colors = TextFieldDefaults.colors(
              focusedContainerColor = inputBg,
              unfocusedContainerColor = inputBg,
              focusedIndicatorColor = Color.Transparent,
              unfocusedIndicatorColor = Color.Transparent,
              disabledIndicatorColor = Color.Transparent
            ),
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier
              .fillMaxWidth()
              .border(1.dp, inputBorder, RoundedCornerShape(18.dp))
              .clip(RoundedCornerShape(18.dp))
              .testTag("youtube_url_input"),
            singleLine = true
          )

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            Box(
              modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .clip(CircleShape)
                .background(pasteBg)
                .border(1.dp, inputBorder, CircleShape)
                .clickable {
                  runCatching {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    val primaryClip = cm?.primaryClip
                    val clip = if (primaryClip != null && primaryClip.itemCount > 0) {
                      primaryClip.getItemAt(0)?.coerceToText(context)?.toString()?.trim().orEmpty()
                    } else {
                      ""
                    }
                    if (clip.isNotBlank()) {
                      onYoutubeUrlChanged(clip)
                    } else {
                      Toast.makeText(context, context.getString(R.string.search_yt_clipboard_empty), Toast.LENGTH_SHORT).show()
                    }
                  }.onFailure { err ->
                    onReportError(context.getString(R.string.search_clipboard_error), err)
                  }
                },
              contentAlignment = Alignment.Center
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
              ) {
                Icon(
                  Icons.Rounded.ContentPaste,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.primary,
                  modifier = Modifier.size(17.dp)
                )
                Text(
                  text = stringResource(id = R.string.search_yt_paste),
                  style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, lineHeight = 18.sp),
                  color = MaterialTheme.colorScheme.onSurface
                )
              }
            }

            Button(
              onClick = onAnalyzeYoutube,
              enabled = !isAnalyzingYoutube,
              modifier = Modifier
                .weight(1.15f)
                .height(48.dp)
                .testTag("analyze_link_button"),
              shape = CircleShape,
              colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
              if (isAnalyzingYoutube) {
                CircularProgressIndicator(
                  modifier = Modifier.size(17.dp),
                  strokeWidth = 2.dp,
                  color = Color.White
                )
              } else {
                Icon(Icons.Rounded.Analytics, contentDescription = null, modifier = Modifier.size(17.dp))
              }
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = if (isAnalyzingYoutube) {
                  stringResource(id = R.string.search_yt_analyzing)
                } else {
                  stringResource(id = R.string.search_yt_analyze)
                },
                fontWeight = FontWeight.Bold
              )
            }
          }

          if (!youtubeError.isNullOrBlank()) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.error.copy(alpha = 0.14f))
                .padding(horizontal = 12.dp, vertical = 10.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              Icon(
                imageVector = Icons.Rounded.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(18.dp)
              )
              Text(
                text = youtubeError,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
              )
            }
          }
        }
      }
    }

    if (isAnalyzingYoutube) {
      item {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .semantics {
              liveRegion = LiveRegionMode.Polite
              contentDescription = "Searching"
            }
            .testTag("youtube_analyzing_orbit_loader")
        ) {
          StatusRow(
            text = stringResource(id = R.string.search_loading_link),
            runner = OrbiRunner.Search,
            outcome = OrbiStatusOutcome.Loading,
            reducedMotion = reducedMotion
          )
        }
      }
    } else if (youtubePlaylist != null) {
      item {
        YoutubePlaylistResultCard(
          playlist = youtubePlaylist,
          darkTheme = darkTheme,
          reducedMotion = reducedMotion,
          songDownloadProgress = songDownloadProgress,
          downloadedSongIds = downloadedSongIds,
          selectionMode = playlistSelectionMode,
          selectedCount = selectedPlaylistTrackIds.size,
          hasQueueItems = hasQueueItems,
          onPlayAll = { onPlayPlaylist(false) },
          onShuffle = { onPlayPlaylist(true) },
          onSaveOrOpen = {
            if (playlistSelectionMode && selectedPlaylistTrackIds.isNotEmpty()) {
              onSaveSelectedPlaylistTracks()
            } else {
              val savedId = youtubePlaylist.savedAlbumId
              if (savedId != null) {
                onOpenSavedPlaylistAlbum(savedId)
              } else {
                onSavePlaylist()
              }
            }
          },
          onDownloadAll = {
            if (playlistSelectionMode && selectedPlaylistTrackIds.isNotEmpty()) {
              onDownloadSelectedPlaylistTracks()
            } else {
              onDownloadPlaylist()
            }
          },
          onEndDownload = onCancelPlaylistDownloads,
          onOpenDownloadQueue = onOpenDownloadQueue
        )
      }

      item {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 4.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              text = stringResource(id = R.string.playlist_songs_header, youtubePlaylist.tracks.size),
              style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
              color = AurixTealAccent,
              modifier = Modifier.weight(1f)
            )
            Surface(
              onClick = onTogglePlaylistSelectionMode,
              shape = CircleShape,
              color = if (playlistSelectionMode) Color(0xFF2F8BFF).copy(alpha = 0.22f)
              else if (darkTheme) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.78f),
              border = BorderStroke(
                1.dp,
                if (playlistSelectionMode) Color(0xFF2F8BFF).copy(alpha = 0.55f)
                else if (darkTheme) Color.White.copy(alpha = 0.16f) else Color.White
              ),
              modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("playlist_select_toggle_button")
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
              ) {
                Icon(
                  imageVector = Icons.Rounded.Checklist,
                  contentDescription = null,
                  tint = if (playlistSelectionMode) Color(0xFF2F8BFF) else MaterialTheme.colorScheme.onSurfaceVariant,
                  modifier = Modifier.size(15.dp)
                )
                Text(
                  text = stringResource(
                    id = if (playlistSelectionMode) R.string.playlist_done_selecting else R.string.playlist_select_songs
                  ),
                  style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                  color = if (playlistSelectionMode) Color(0xFF2F8BFF) else MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
            }
            Surface(
              onClick = onClearYoutubeResult,
              shape = CircleShape,
              color = if (darkTheme) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.78f),
              border = BorderStroke(
                1.dp,
                if (darkTheme) Color.White.copy(alpha = 0.16f) else Color.White
              ),
              modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("playlist_clear_button")
            ) {
              Text(
                text = stringResource(id = R.string.search_quick_clear),
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
              )
            }
          }

          AnimatedVisibility(
            visible = playlistSelectionMode,
            enter = if (reducedMotion) fadeIn(snap()) else fadeIn(tween(180)) + expandVertically(),
            exit = if (reducedMotion) fadeOut(snap()) else fadeOut(tween(140)) + shrinkVertically()
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(
                  if (darkTheme) Color(0xFF1A233E).copy(alpha = 0.80f)
                  else Color.White.copy(alpha = 0.85f)
                )
                .border(1.dp, Color(0xFF2F8BFF).copy(alpha = 0.30f), RoundedCornerShape(18.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              Text(
                text = stringResource(id = R.string.playlist_selected_count, selectedPlaylistTrackIds.size),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
              )
              Surface(
                onClick = onSelectAllPlaylistTracks,
                shape = CircleShape,
                color = Color(0xFF2F8BFF).copy(alpha = 0.16f),
                border = BorderStroke(1.dp, Color(0xFF2F8BFF).copy(alpha = 0.40f)),
                modifier = Modifier
                  .minimumInteractiveComponentSize()
                  .testTag("playlist_select_all_button")
              ) {
                Text(
                  text = stringResource(id = R.string.playlist_select_all),
                  style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                  color = Color(0xFF2F8BFF),
                  modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp)
                )
              }
              Surface(
                onClick = onClearPlaylistTrackSelection,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                modifier = Modifier
                  .minimumInteractiveComponentSize()
                  .testTag("playlist_clear_selection_button")
              ) {
                Text(
                  text = stringResource(id = R.string.playlist_clear_selection),
                  style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp)
                )
              }
            }
          }
        }
      }

      itemsIndexed(youtubePlaylist.tracks, key = { index, song -> "pl_${index}_${song.id}" }) { index, song ->
        val isChecked = song.id in selectedPlaylistTrackIds
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          if (playlistSelectionMode) {
            IconButton(
              onClick = { onTogglePlaylistTrackSelection(song.id) },
              modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("playlist_track_checkbox_${song.id}")
            ) {
              Icon(
                imageVector = if (isChecked) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                contentDescription = song.title,
                tint = if (isChecked) Color(0xFF2F8BFF) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                modifier = Modifier.size(24.dp)
              )
            }
          }
          SongDownloadRow(
            song = song,
            index = index + 1,
            isPlaying = isPlaying,
            isCurrent = currentSong?.id == song.id,
            isResolving = resolvingSongId == song.id,
            reducedMotion = reducedMotion,
            darkTheme = darkTheme,
            hasLyrics = song.id in verifiedLyricsIds,
            downloadProgress = songDownloadProgress[song.id],
            isDownloaded = song.id in downloadedSongIds,
            onCheckLyrics = onCheckLyrics,
            onSongClick = {
              if (playlistSelectionMode) {
                onTogglePlaylistTrackSelection(song.id)
              } else {
                onSongClick(song, youtubePlaylist.tracks)
              }
            },
            onFavoriteClick = { onFavoriteClick(song) },
            onDownloadClick = { onTrackDownloadClick(song) },
            modifier = Modifier.weight(1f)
          )
        }
      }
    } else if (youtubeAnalysis != null) {
      item {
        YoutubeAnalysisResultCard(
          analysis = youtubeAnalysis,
          selectedFormatId = selectedYoutubeFormatId,
          downloadState = youtubeDownloadState,
          darkTheme = darkTheme,
          reducedMotion = reducedMotion,
          verifiedLyricsIds = verifiedLyricsIds,
          onCheckLyrics = onCheckLyrics,
          onSelectFormat = onSelectYoutubeFormat,
          onPlayClick = onPlayAnalyzedYoutube,
          onDownload = onDownloadYoutube,
          onPlaySaved = onPlaySaved,
          onOpenMySongs = onOpenMySongs,
          onOpenAssociatedPlaylist = { url -> onOpenPlaylistUrl(url) }
        )
      }
    } else {
      item {
        GlassCard(
          darkTheme = darkTheme,
          cornerRadius = 26.dp,
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text(
                text = stringResource(id = R.string.search_yt_guide_title),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, lineHeight = 20.sp),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
              )
              OpenCloseGlassPill(
                isOpen = showYoutubeGuide,
                onToggle = { showYoutubeGuide = !showYoutubeGuide },
                darkTheme = darkTheme,
                reducedMotion = reducedMotion,
                testTag = "search_yt_guide_toggle_button",
                contentDescription = stringResource(
                  id = if (showYoutubeGuide) R.string.section_close_cd else R.string.section_open_cd,
                  stringResource(id = R.string.search_yt_guide_title)
                )
              )
            }
            AnimatedVisibility(
              visible = showYoutubeGuide,
              enter = if (reducedMotion) fadeIn(snap()) else fadeIn(tween(180)) + expandVertically(),
              exit = if (reducedMotion) fadeOut(snap()) else fadeOut(tween(140)) + shrinkVertically()
            ) {
              Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                MatcherGuideRow(
                  icon = Icons.Rounded.Link,
                  title = stringResource(id = R.string.search_yt_step1_title),
                  subtitle = stringResource(id = R.string.search_yt_step1_sub),
                  iconTint = MaterialTheme.colorScheme.primary
                )
                MatcherGuideRow(
                  icon = Icons.Rounded.Tune,
                  title = stringResource(id = R.string.search_yt_step2_title),
                  subtitle = stringResource(id = R.string.search_yt_step2_sub),
                  iconTint = Color(0xFF14B8A6)
                )
                MatcherGuideRow(
                  icon = Icons.Rounded.Download,
                  title = stringResource(id = R.string.search_yt_step3_title),
                  subtitle = stringResource(id = R.string.search_yt_step3_sub),
                  iconTint = Color(0xFFEC4899)
                )
              }
            }
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchResultsArea(
  uiState: SearchUiState,
  searchMode: SearchMode = SearchMode.YouTubeSearch,
  currentSong: Song?,
  isPlaying: Boolean,
  resolvingSongId: String? = null,
  darkTheme: Boolean,
  reducedMotion: Boolean,
  songDownloadProgress: Map<String, Float> = emptyMap(),
  downloadedSongIds: Set<String> = emptySet(),
  verifiedLyricsIds: Set<String> = emptySet(),
  artColors: Pair<Color, Color>? = null,
  dynamicBlurGlass: Boolean = true,
  onToggleDynamicBlur: () -> Unit = {},
  showResultsList: Boolean = true,
  onToggleResultsList: () -> Unit = {},
  onCheckLyrics: (Song) -> Unit = {},
  onSongClick: (Song, List<Song>) -> Unit = { _, _ -> },
  onFavoriteClick: (Song) -> Unit = {},
  onDownloadClick: (Song) -> Unit = {},
  onRetry: () -> Unit = {},
  onQuickSuggestionClick: (String) -> Unit = {},
  onClearSearch: () -> Unit = {},
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val isInspection = LocalInspectionMode.current
  val systemAnimatorScale = remember(context, isInspection) {
    if (isInspection) 1f
    else runCatching {
      Settings.Global.getFloat(
        context.contentResolver,
        Settings.Global.ANIMATOR_DURATION_SCALE,
        1f
      )
    }.getOrDefault(1f)
  }
  val isStill = reducedMotion || systemAnimatorScale <= 0f

  val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
  val centerBottomPadding = (if (currentSong != null) 168.dp else 92.dp) + navBarBottom
  val listBottomPadding = (if (currentSong != null) 205.dp else 135.dp) + navBarBottom

  AnimatedContent(
    targetState = uiState,
    modifier = modifier,
    contentKey = { state ->
      when (state) {
        is SearchUiState.Loading -> "loading_${state.mode}"
        is SearchUiState.Results -> "results"
        else -> state::class
      }
    },
    transitionSpec = {
      if (isStill) {
        fadeIn(snap()) togetherWith fadeOut(snap())
      } else if (targetState is SearchUiState.Results) {
        (fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) +
          slideInVertically(spring(dampingRatio = 0.84f, stiffness = Spring.StiffnessMediumLow)) { it / 16 }) togetherWith
          fadeOut(tween(150))
      } else {
        fadeIn(tween(240)) togetherWith fadeOut(tween(160))
      }
    },
    label = "search_results_area"
  ) { state ->
    when (state) {
      SearchUiState.Idle -> {
        val isYtSearch = searchMode == SearchMode.YouTubeSearch
        val badgeColor = if (isYtSearch) Color(0xFFEF4444) else MaterialTheme.colorScheme.primary
        val starterSuggestions = remember(isYtSearch) {
          if (isYtSearch) listOf("VannDa", "Suly Pheng", "Chill Mix")
          else listOf("Pop Hits", "Weekend", "Lo-Fi")
        }
        Box(
          modifier = Modifier
            .fillMaxSize()
            .padding(bottom = centerBottomPadding),
          contentAlignment = Alignment.Center
        ) {
          GlassCard(
            darkTheme = darkTheme,
            cornerRadius = 28.dp,
            modifier = Modifier.fillMaxWidth()
          ) {
            Column(
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 26.dp),
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
              Box(
                modifier = Modifier
                  .size(58.dp)
                  .clip(CircleShape)
                  .background(
                    Brush.linearGradient(
                      colors = listOf(badgeColor.copy(alpha = 0.24f), AurixTealAccent.copy(alpha = 0.14f))
                    )
                  )
                  .border(1.dp, badgeColor.copy(alpha = 0.36f), CircleShape),
                contentAlignment = Alignment.Center
              ) {
                Icon(
                  imageVector = if (isYtSearch) Icons.Rounded.PlayCircle else Icons.Rounded.MusicNote,
                  contentDescription = null,
                  tint = badgeColor,
                  modifier = Modifier.size(28.dp)
                )
              }
              Text(
                text = stringResource(
                  id = if (isYtSearch) R.string.search_yt_idle_title else R.string.search_itunes_idle_title
                ),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
              )
              Text(
                text = stringResource(
                  id = if (isYtSearch) R.string.search_yt_idle_sub else R.string.search_itunes_idle_sub
                ),
                style = MaterialTheme.typography.bodySmall.copy(lineHeight = 19.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
              )
              FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 4.dp)
              ) {
                starterSuggestions.forEach { term ->
                  GlassQuickTagChip(
                    label = term,
                    darkTheme = darkTheme,
                    reducedMotion = reducedMotion,
                    onClick = { onQuickSuggestionClick(term) }
                  )
                }
              }
            }
          }
        }
      }

      is SearchUiState.Loading -> {
        SearchSkeletonLoadingView(
          darkTheme = darkTheme,
          reducedMotion = reducedMotion,
          modeText = when (state.mode) {
            SearchMode.YouTubeSearch -> stringResource(id = R.string.search_loading_yt)
            SearchMode.YouTubeLink -> stringResource(id = R.string.search_loading_link)
          }
        )
      }

      is SearchUiState.Results -> {
        val fallbackArtPair = remember { artworkHueColors("") }
        val activeArtPair = artColors ?: fallbackArtPair
        val useDynamicArt = dynamicBlurGlass && currentSong != null

        Column(
          modifier = Modifier
            .fillMaxSize()
            .padding(bottom = if (showResultsList) 0.dp else centerBottomPadding)
            .testTag("search_results_glass_panel")
        ) {
          val clearInteractionSource = remember { MutableInteractionSource() }
          val isClearPressed by clearInteractionSource.collectIsPressedAsState()
          val clearScale by animateFloatAsState(
            targetValue = if (isClearPressed) 0.93f else 1f,
            animationSpec = if (isStill) snap() else spring(dampingRatio = 0.68f, stiffness = Spring.StiffnessMedium),
            label = "clear_pill_spring"
          )

          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 4.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              text = stringResource(id = R.string.search_results_count, state.items.size),
              style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
              color = if (useDynamicArt) activeArtPair.first else AurixTealAccent,
              modifier = Modifier.weight(1f)
            )

            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
              Surface(
                onClick = onToggleDynamicBlur,
                shape = CircleShape,
                color = if (dynamicBlurGlass) {
                  (if (useDynamicArt) activeArtPair.first else AurixTealAccent).copy(alpha = if (darkTheme) 0.22f else 0.14f)
                } else if (darkTheme) {
                  Color.White.copy(alpha = 0.08f)
                } else {
                  Color.White.copy(alpha = 0.78f)
                },
                border = BorderStroke(
                  1.dp,
                  if (dynamicBlurGlass) {
                    (if (useDynamicArt) activeArtPair.first else AurixTealAccent).copy(alpha = 0.55f)
                  } else if (darkTheme) {
                    Color.White.copy(alpha = 0.16f)
                  } else {
                    Color.White
                  }
                ),
                modifier = Modifier
                  .minimumInteractiveComponentSize()
                  .testTag("search_blur_toggle_button")
              ) {
                Row(
                  modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                  Icon(
                    imageVector = if (dynamicBlurGlass) Icons.Rounded.BlurOn else Icons.Rounded.BlurOff,
                    contentDescription = stringResource(
                      id = if (dynamicBlurGlass) R.string.search_results_blur_on else R.string.search_results_blur_off
                    ),
                    tint = if (dynamicBlurGlass) {
                      if (useDynamicArt) activeArtPair.first else AurixTealAccent
                    } else {
                      MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(14.dp)
                  )
                }
              }

              OpenCloseGlassPill(
                isOpen = showResultsList,
                onToggle = onToggleResultsList,
                darkTheme = darkTheme,
                reducedMotion = isStill,
                testTag = "search_toggle_results_button",
                accentColor = if (useDynamicArt) activeArtPair.first else AurixTealAccent,
                contentDescription = stringResource(
                  id = if (showResultsList) R.string.section_close_cd else R.string.section_open_cd,
                  stringResource(id = R.string.search_results_count, state.items.size)
                )
              )

              Surface(
                shape = CircleShape,
                color = if (darkTheme) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.78f),
                border = BorderStroke(
                  1.dp,
                  if (darkTheme) Color.White.copy(alpha = 0.16f) else Color.White
                ),
                modifier = Modifier
                  .minimumInteractiveComponentSize()
                  .graphicsLayer {
                    scaleX = clearScale
                    scaleY = clearScale
                  }
                  .clip(CircleShape)
                  .clickable(
                    interactionSource = clearInteractionSource,
                    indication = LocalIndication.current
                  ) { onClearSearch() }
                  .testTag("search_close_results_button")
              ) {
                Text(
                  text = stringResource(id = R.string.search_quick_clear),
                  style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
              }
            }
          }

          if (showResultsList) {
            LazyColumn(
              modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(top = 8.dp),
              verticalArrangement = Arrangement.spacedBy(8.dp),
              contentPadding = PaddingValues(bottom = listBottomPadding)
            ) {
              itemsIndexed(state.items, key = { index, song -> "${index}_${song.id}" }) { index, song ->
                SongDownloadRow(
                  song = song,
                  index = index + 1,
                  isPlaying = isPlaying,
                  isCurrent = currentSong?.id == song.id,
                  isResolving = resolvingSongId == song.id,
                  reducedMotion = isStill,
                  darkTheme = darkTheme,
                  hasLyrics = song.id in verifiedLyricsIds,
                  downloadProgress = songDownloadProgress[song.id],
                  isDownloaded = song.id in downloadedSongIds,
                  ambientPrimaryColor = if (useDynamicArt) activeArtPair.first else null,
                  ambientSecondaryColor = if (useDynamicArt) activeArtPair.second else null,
                  onCheckLyrics = onCheckLyrics,
                  onSongClick = { onSongClick(song, state.items) },
                  onFavoriteClick = { onFavoriteClick(song) },
                  onDownloadClick = { onDownloadClick(song) }
                )
              }
            }
          }
        }
      }

      SearchUiState.Empty -> {
        Box(
          modifier = Modifier
            .fillMaxSize()
            .padding(bottom = centerBottomPadding)
            .testTag("search_empty_state"),
          contentAlignment = Alignment.Center
        ) {
          GlassCard(
            darkTheme = darkTheme,
            cornerRadius = 28.dp,
            modifier = Modifier.fillMaxWidth()
          ) {
            Column(
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 30.dp),
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
              OrbiView(
                state = OrbiState.Dim,
                size = 56.dp,
                runner = OrbiRunner.None,
                reducedMotion = reducedMotion
              )
              Text(
                text = stringResource(id = R.string.search_no_results_title),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
              )
              Text(
                text = stringResource(id = R.string.search_no_results_sub),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
              )
            }
          }
        }
      }

      is SearchUiState.Error -> {
        Box(
          modifier = Modifier
            .fillMaxSize()
            .padding(bottom = centerBottomPadding)
            .testTag("search_error_state"),
          contentAlignment = Alignment.Center
        ) {
          GlassCard(
            darkTheme = darkTheme,
            cornerRadius = 28.dp,
            modifier = Modifier.fillMaxWidth()
          ) {
            Column(
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 28.dp),
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
              OrbiView(
                state = OrbiState.Sad,
                size = 56.dp,
                runner = OrbiRunner.None,
                reducedMotion = reducedMotion
              )
              Text(
                text = state.message.ifBlank { "Can't reach YouTube" },
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
              )
              Button(
                onClick = onRetry,
                enabled = true,
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                  containerColor = AurixTealAccent,
                  contentColor = Color(0xFF06201C)
                ),
                contentPadding = PaddingValues(horizontal = 22.dp, vertical = 10.dp),
                modifier = Modifier
                  .defaultMinSize(minHeight = 48.dp)
                  .testTag("search_retry_button")
              ) {
                Icon(
                  imageVector = Icons.Rounded.Refresh,
                  contentDescription = null,
                  modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                  text = "Retry",
                  style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                )
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun YoutubeAnalysisResultCard(
  analysis: YoutubeAnalysisResult,
  selectedFormatId: String?,
  downloadState: YoutubeDownloadState,
  darkTheme: Boolean,
  reducedMotion: Boolean = false,
  verifiedLyricsIds: Set<String> = emptySet(),
  onCheckLyrics: (Song) -> Unit = {},
  onSelectFormat: (String) -> Unit,
  onPlayClick: () -> Unit,
  onDownload: () -> Unit,
  onPlaySaved: (Song) -> Unit,
  onOpenMySongs: () -> Unit,
  onOpenAssociatedPlaylist: (String) -> Unit = {}
) {
  val probeSong = remember(analysis.videoId, analysis.matchedTrackTitle, analysis.matchedArtist, analysis.durationSeconds) {
    Song(
      id = "yt_${analysis.videoId}",
      title = analysis.matchedTrackTitle,
      artist = analysis.matchedArtist,
      artworkUrl = analysis.thumbnailUrl,
      durationSeconds = analysis.durationSeconds,
      streamUrl = analysis.originalUrl,
      source = "Downloaded",
      fullDurationSeconds = analysis.durationSeconds
    )
  }
  LaunchedEffect(probeSong.id) {
    onCheckLyrics(probeSong)
  }
  val hasLyrics = probeSong.id in verifiedLyricsIds
  val (c1, c2) = rememberArtworkColors(analysis.thumbnailUrl, analysis.videoTitle)
  val cardShape = RoundedCornerShape(28.dp)
  val selectedFormat = analysis.formats.firstOrNull { it.id == selectedFormatId } ?: analysis.formats.firstOrNull()
  val isDownloading = downloadState is YoutubeDownloadState.Downloading

  Box(
    modifier = Modifier
      .fillMaxWidth()
      .clip(cardShape)
      .background(if (darkTheme) Color(0xFF172034).copy(alpha = 0.84f) else Color.White.copy(alpha = 0.90f))
      .border(1.dp, Color.White.copy(alpha = if (darkTheme) 0.28f else 0.96f), cardShape)
  ) {
    if (analysis.thumbnailUrl.isNotBlank()) {
      AsyncImage(
        model = analysis.thumbnailUrl,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
          .matchParentSize()
          .alpha(if (darkTheme) 0.22f else 0.14f)
          .blur(34.dp)
      )
    }
    Box(
      modifier = Modifier
        .matchParentSize()
        .background(
          Brush.horizontalGradient(
            colors = listOf(c1.copy(alpha = 0.22f), c2.copy(alpha = 0.12f), Color.Transparent)
          )
        )
    )

    Column(
      modifier = Modifier.padding(18.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
      if (analysis.thumbnailUrl.isNotBlank()) {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .height(176.dp)
            .clip(RoundedCornerShape(20.dp))
            .border(1.dp, Color.White.copy(alpha = 0.30f), RoundedCornerShape(20.dp))
        ) {
          AsyncImage(
            model = analysis.thumbnailUrl,
            contentDescription = analysis.videoTitle,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
          )
          Surface(
            shape = CircleShape,
            color = Color.Black.copy(alpha = 0.68f),
            modifier = Modifier
              .align(Alignment.TopStart)
              .padding(10.dp)
          ) {
            val durBadge = if (analysis.durationSeconds > 0L) {
              val min = analysis.durationSeconds / 60L
              val sec = analysis.durationSeconds % 60L
              String.format(Locale.US, "FULL SONG · %d:%02d", min, sec)
            } else {
              "FULL SONG AUDIO"
            }
            Text(
              text = durBadge,
              style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp),
              color = Color.White,
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
            )
          }
        }
      }

      Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(
            text = analysis.videoTitle,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
          )
          LyricsTag(
            visible = hasLyrics,
            size = LyricsTagSize.Standard,
            darkTheme = darkTheme,
            reducedMotion = reducedMotion,
            modifier = Modifier.padding(start = 8.dp)
          )
        }
        Text(
          text = analysis.channelName,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
      }

      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
          text = stringResource(id = R.string.yt_select_format_title),
          style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
          color = MaterialTheme.colorScheme.onSurface
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          analysis.formats.forEach { option ->
            val selected = option.id == selectedFormat?.id
            FormatOptionRow(
              option = option,
              selected = selected,
              enabled = !isDownloading,
              darkTheme = darkTheme,
              onClick = { onSelectFormat(option.id) }
            )
          }
        }
      }

      when (downloadState) {
        is YoutubeDownloadState.Downloading -> {
          val pct = (downloadState.progress * 100f).toInt().coerceIn(0, 100)
          val bytesText = if (downloadState.totalBytes > 0L) {
            String.format(
              Locale.US,
              "%.2f / %.2f MB",
              downloadState.downloadedBytes / (1024f * 1024f),
              downloadState.totalBytes / (1024f * 1024f)
            )
          } else {
            String.format(Locale.US, "%.2f MB", downloadState.downloadedBytes / (1024f * 1024f))
          }
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(18.dp))
              .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
              .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), RoundedCornerShape(18.dp))
              .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text(
                text = "Downloading ${downloadState.formatLabel}",
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = "$pct% ($bytesText)",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
              )
            }
            LinearProgressIndicator(
              progress = { downloadState.progress.coerceIn(0f, 1f) },
              modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(CircleShape),
              color = MaterialTheme.colorScheme.primary,
              trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.20f)
            )
          }
        }
        is YoutubeDownloadState.Saved -> {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(18.dp))
              .background(Color(0xFF10B981).copy(alpha = 0.14f))
              .border(1.dp, Color(0xFF10B981).copy(alpha = 0.40f), RoundedCornerShape(18.dp))
              .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              Icon(
                imageVector = Icons.Rounded.DownloadDone,
                contentDescription = null,
                tint = Color(0xFF10B981),
                modifier = Modifier.size(20.dp)
              )
              Text(
                text = stringResource(id = R.string.yt_saved_banner),
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
              )
            }
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
              Button(
                onClick = { onPlaySaved(downloadState.song) },
                modifier = Modifier
                  .weight(1f)
                  .height(48.dp),
                shape = CircleShape
              ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(id = R.string.home_play_now), fontWeight = FontWeight.Bold)
              }
              OutlinedButton(
                onClick = onOpenMySongs,
                modifier = Modifier
                  .weight(1f)
                  .height(48.dp),
                shape = CircleShape,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.50f))
              ) {
                Icon(Icons.Rounded.LibraryMusic, contentDescription = null, modifier = Modifier.size(17.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(id = R.string.my_songs), fontWeight = FontWeight.Bold)
              }
            }
          }
        }
        is YoutubeDownloadState.Error -> {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(14.dp))
              .background(MaterialTheme.colorScheme.error.copy(alpha = 0.14f))
              .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
            Text(downloadState.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
          }
        }
        YoutubeDownloadState.Idle -> Unit
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        OutlinedButton(
          onClick = onPlayClick,
          modifier = Modifier
            .weight(0.9f)
            .height(50.dp)
            .testTag("youtube_play_button"),
          shape = CircleShape,
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.60f)),
          colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (darkTheme) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.72f),
            contentColor = MaterialTheme.colorScheme.onSurface
          ),
          contentPadding = PaddingValues(horizontal = 12.dp)
        ) {
          Icon(
            Icons.Rounded.PlayArrow,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(19.dp)
          )
          Spacer(modifier = Modifier.width(4.dp))
          Text(
            text = stringResource(id = R.string.action_play),
            fontWeight = FontWeight.Bold,
            maxLines = 1
          )
        }

        Button(
          onClick = onDownload,
          enabled = !isDownloading && selectedFormat != null,
          modifier = Modifier
            .weight(1.45f)
            .height(50.dp)
            .testTag("youtube_download_button"),
          shape = CircleShape,
          contentPadding = PaddingValues(horizontal = 14.dp),
          colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
          if (isDownloading) {
            CircularProgressIndicator(
              modifier = Modifier.size(18.dp),
              strokeWidth = 2.dp,
              color = Color.White
            )
          } else {
            Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(19.dp))
          }
          Spacer(modifier = Modifier.width(6.dp))
          val ctaLabel = when {
            isDownloading -> "Downloading..."
            downloadState is YoutubeDownloadState.Saved -> stringResource(id = R.string.yt_download_another)
            selectedFormat != null -> "${selectedFormat.formatLabel} (${selectedFormat.qualityLabel})"
            else -> stringResource(id = R.string.album_track_download)
          }
          Text(text = ctaLabel, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
      }

      val playlistLink = analysis.associatedPlaylistUrl
      if (!playlistLink.isNullOrBlank()) {
        OutlinedButton(
          onClick = { onOpenAssociatedPlaylist(playlistLink) },
          modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .testTag("youtube_open_associated_playlist_button"),
          shape = CircleShape,
          border = BorderStroke(1.dp, Color(0xFFA78BFA).copy(alpha = 0.55f)),
          colors = ButtonDefaults.outlinedButtonColors(
            containerColor = Color(0xFFA78BFA).copy(alpha = if (darkTheme) 0.14f else 0.10f),
            contentColor = MaterialTheme.colorScheme.onSurface
          )
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
            contentDescription = null,
            tint = Color(0xFFA78BFA),
            modifier = Modifier.size(19.dp)
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = stringResource(id = R.string.playlist_open_from_video),
            fontWeight = FontWeight.Bold
          )
        }
      }
    }
  }
}

@Composable
private fun YoutubePlaylistResultCard(
  playlist: YoutubePlaylistResult,
  darkTheme: Boolean,
  reducedMotion: Boolean,
  songDownloadProgress: Map<String, Float>,
  downloadedSongIds: Set<String>,
  selectionMode: Boolean,
  selectedCount: Int,
  hasQueueItems: Boolean,
  onPlayAll: () -> Unit,
  onShuffle: () -> Unit,
  onSaveOrOpen: () -> Unit,
  onDownloadAll: () -> Unit,
  onEndDownload: () -> Unit,
  onOpenDownloadQueue: () -> Unit
) {
  val (c1, c2) = rememberArtworkColors(playlist.thumbnailUrl, playlist.title)
  val cardShape = RoundedCornerShape(28.dp)
  val totalDurSec = remember(playlist.tracks) {
    playlist.tracks.sumOf { it.durationSeconds.coerceAtLeast(0L) }
  }
  val durFormatted = remember(totalDurSec) {
    if (totalDurSec <= 0L) "" else {
      val hrs = totalDurSec / 3600L
      val mins = (totalDurSec % 3600L) / 60L
      if (hrs > 0L) "${hrs}h ${mins}m" else "${mins.coerceAtLeast(1L)}m"
    }
  }
  val activeDownloadCount = playlist.tracks.count { songDownloadProgress.containsKey(it.id) }
  val downloadedCount = playlist.tracks.count { it.id in downloadedSongIds }
  val allDownloaded = playlist.tracks.isNotEmpty() && downloadedCount == playlist.tracks.size
  val isSaved = playlist.savedAlbumId != null
  val hasSelectionActions = selectionMode && selectedCount > 0

  Box(
    modifier = Modifier
      .fillMaxWidth()
      .clip(cardShape)
      .background(if (darkTheme) Color(0xFF172034).copy(alpha = 0.86f) else Color.White.copy(alpha = 0.90f))
      .border(1.dp, Color.White.copy(alpha = if (darkTheme) 0.24f else 0.96f), cardShape)
      .testTag("youtube_playlist_card")
  ) {
    if (playlist.thumbnailUrl.isNotBlank()) {
      AsyncImage(
        model = playlist.thumbnailUrl,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
          .matchParentSize()
          .alpha(if (darkTheme) 0.22f else 0.14f)
          .blur(34.dp)
      )
    }
    Box(
      modifier = Modifier
        .matchParentSize()
        .background(
          Brush.linearGradient(
            colors = listOf(
              Color(0xFF2F8BFF).copy(alpha = 0.18f),
              c1.copy(alpha = 0.18f),
              Color(0xFFA78BFA).copy(alpha = 0.12f)
            )
          )
        )
    )

    Column(
      modifier = Modifier.padding(18.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Box(
          modifier = Modifier
            .size(88.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(
              Brush.linearGradient(
                colors = listOf(Color(0xFF2F8BFF), Color(0xFF6366F1), Color(0xFFA78BFA))
              )
            )
            .border(1.dp, Color.White.copy(alpha = 0.28f), RoundedCornerShape(18.dp)),
          contentAlignment = Alignment.Center
        ) {
          if (playlist.thumbnailUrl.isNotBlank()) {
            AsyncImage(
              model = playlist.thumbnailUrl,
              contentDescription = playlist.title,
              contentScale = ContentScale.Crop,
              modifier = Modifier.fillMaxSize()
            )
          } else {
            Icon(
              imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
              contentDescription = playlist.title,
              tint = Color.White,
              modifier = Modifier.size(38.dp)
            )
          }
        }

        Column(
          modifier = Modifier.weight(1f),
          verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
          Surface(
            shape = CircleShape,
            color = Color(0xFF2F8BFF).copy(alpha = if (darkTheme) 0.26f else 0.16f),
            border = BorderStroke(1.dp, Color(0xFF2F8BFF).copy(alpha = 0.45f))
          ) {
            Text(
              text = stringResource(id = R.string.playlist_badge),
              style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.ExtraBold,
                fontSize = 10.sp
              ),
              color = if (darkTheme) Color(0xFF93C5FD) else Color(0xFF1D4ED8),
              modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
            )
          }
          Text(
            text = playlist.title,
            style = MaterialTheme.typography.titleMedium.copy(
              fontWeight = FontWeight.ExtraBold,
              lineHeight = 22.sp
            ),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
          )
          val countText = stringResource(id = R.string.playlist_songs_count, playlist.tracks.size)
          val metaLine = buildString {
            if (playlist.author.isNotBlank()) {
              append(playlist.author)
              append(" · ")
            }
            append(countText)
            if (durFormatted.isNotBlank()) {
              append(" · ")
              append(durFormatted)
            }
          }
          Text(
            text = metaLine,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }
      }

      // Row 1: Listen as Playlist (Play All & Shuffle)
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Box(
          modifier = Modifier
            .weight(1f)
            .height(50.dp)
            .clip(CircleShape)
            .background(
              Brush.horizontalGradient(
                colors = listOf(Color(0xFF2F8BFF), Color(0xFF5B5CEB))
              )
            )
            .clickable(onClick = onPlayAll)
            .testTag("playlist_play_all_button"),
          contentAlignment = Alignment.Center
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(horizontal = 12.dp)
          ) {
            Icon(
              imageVector = Icons.Rounded.PlayArrow,
              contentDescription = null,
              tint = Color.White,
              modifier = Modifier.size(20.dp)
            )
            Text(
              text = stringResource(id = R.string.playlist_play_all),
              style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
              color = Color.White,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
          }
        }

        OutlinedButton(
          onClick = onShuffle,
          modifier = Modifier
            .weight(1f)
            .height(50.dp)
            .testTag("playlist_shuffle_button"),
          shape = CircleShape,
          border = BorderStroke(
            1.dp,
            if (darkTheme) Color.White.copy(alpha = 0.20f) else Color.White
          ),
          colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (darkTheme) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.72f),
            contentColor = MaterialTheme.colorScheme.onSurface
          ),
          contentPadding = PaddingValues(horizontal = 12.dp)
        ) {
          Icon(
            imageVector = Icons.Rounded.Shuffle,
            contentDescription = null,
            tint = Color(0xFF2F8BFF),
            modifier = Modifier.size(18.dp)
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = stringResource(id = R.string.playlist_shuffle),
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }
      }

      // Row 2: Save Playlist (or Save Selected) & Download All (or Download Selected)
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        OutlinedButton(
          onClick = onSaveOrOpen,
          modifier = Modifier
            .weight(1f)
            .height(48.dp)
            .testTag("playlist_save_button"),
          shape = CircleShape,
          border = BorderStroke(
            1.dp,
            Color(0xFFA78BFA).copy(alpha = if (isSaved && !hasSelectionActions) 0.72f else 0.45f)
          ),
          colors = ButtonDefaults.outlinedButtonColors(
            containerColor = Color(0xFFA78BFA).copy(alpha = if (isSaved && !hasSelectionActions) 0.22f else 0.12f),
            contentColor = MaterialTheme.colorScheme.onSurface
          ),
          contentPadding = PaddingValues(horizontal = 12.dp)
        ) {
          Icon(
            imageVector = if (isSaved && !hasSelectionActions) Icons.Rounded.LibraryAddCheck else Icons.Rounded.BookmarkAdd,
            contentDescription = null,
            tint = Color(0xFFA78BFA),
            modifier = Modifier.size(18.dp)
          )
          Spacer(modifier = Modifier.width(6.dp))
          val saveText = when {
            hasSelectionActions -> stringResource(id = R.string.playlist_save_selected, selectedCount)
            isSaved -> stringResource(id = R.string.playlist_saved_open)
            else -> stringResource(id = R.string.playlist_save)
          }
          Text(
            text = saveText,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }

        OutlinedButton(
          onClick = onDownloadAll,
          enabled = activeDownloadCount == 0 && (!allDownloaded || hasSelectionActions),
          modifier = Modifier
            .weight(1f)
            .height(48.dp)
            .testTag("playlist_download_all_button"),
          shape = CircleShape,
          border = BorderStroke(
            1.dp,
            when {
              allDownloaded && !hasSelectionActions -> Color(0xFF10B981).copy(alpha = 0.55f)
              else -> Color(0xFF2F8BFF).copy(alpha = 0.48f)
            }
          ),
          colors = ButtonDefaults.outlinedButtonColors(
            containerColor = when {
              allDownloaded && !hasSelectionActions -> Color(0xFF10B981).copy(alpha = 0.16f)
              else -> Color(0xFF2F8BFF).copy(alpha = 0.14f)
            },
            contentColor = MaterialTheme.colorScheme.onSurface
          ),
          contentPadding = PaddingValues(horizontal = 12.dp)
        ) {
          when {
            activeDownloadCount > 0 -> {
              CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = Color(0xFF2F8BFF)
              )
            }
            allDownloaded && !hasSelectionActions -> {
              Icon(
                imageVector = Icons.Rounded.CheckCircle,
                contentDescription = null,
                tint = Color(0xFF10B981),
                modifier = Modifier.size(18.dp)
              )
            }
            else -> {
              Icon(
                imageVector = Icons.Rounded.Download,
                contentDescription = null,
                tint = Color(0xFF2F8BFF),
                modifier = Modifier.size(18.dp)
              )
            }
          }
          Spacer(modifier = Modifier.width(6.dp))
          val downloadLabel = when {
            activeDownloadCount > 0 -> stringResource(id = R.string.playlist_downloading_count, activeDownloadCount)
            hasSelectionActions -> stringResource(id = R.string.playlist_download_selected, selectedCount)
            allDownloaded -> stringResource(id = R.string.playlist_downloaded_all)
            else -> stringResource(id = R.string.playlist_download_all)
          }
          Text(
            text = downloadLabel,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }
      }

      // Row 3: Immediate End Download control + Download Queue shortcut when downloads are active
      AnimatedVisibility(
        visible = activeDownloadCount > 0,
        enter = if (reducedMotion) fadeIn(snap()) else fadeIn(tween(180)) + expandVertically(),
        exit = if (reducedMotion) fadeOut(snap()) else fadeOut(tween(140)) + shrinkVertically()
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          OutlinedButton(
            onClick = onEndDownload,
            modifier = Modifier
              .weight(1f)
              .height(48.dp)
              .testTag("playlist_end_download_button"),
            shape = CircleShape,
            border = BorderStroke(1.dp, Color(0xFFF87171).copy(alpha = 0.58f)),
            colors = ButtonDefaults.outlinedButtonColors(
              containerColor = Color(0xFFF87171).copy(alpha = if (darkTheme) 0.18f else 0.14f),
              contentColor = Color(0xFFF87171)
            ),
            contentPadding = PaddingValues(horizontal = 14.dp)
          ) {
            Icon(
              imageVector = Icons.Rounded.StopCircle,
              contentDescription = null,
              tint = Color(0xFFF87171),
              modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(7.dp))
            Text(
              text = stringResource(id = R.string.playlist_end_download),
              fontWeight = FontWeight.Bold,
              color = Color(0xFFF87171),
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
          }

          if (hasQueueItems) {
            OutlinedButton(
              onClick = onOpenDownloadQueue,
              modifier = Modifier
                .height(48.dp)
                .testTag("playlist_open_queue_button"),
              shape = CircleShape,
              border = BorderStroke(1.dp, Color(0xFF2F8BFF).copy(alpha = 0.45f)),
              colors = ButtonDefaults.outlinedButtonColors(
                containerColor = Color(0xFF2F8BFF).copy(alpha = if (darkTheme) 0.16f else 0.12f),
                contentColor = Color(0xFF2F8BFF)
              ),
              contentPadding = PaddingValues(horizontal = 16.dp)
            ) {
              Text(
                text = stringResource(id = R.string.download_queue_title),
                fontWeight = FontWeight.Bold,
                maxLines = 1
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun MatcherGuideRow(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  title: String,
  subtitle: String,
  iconTint: Color
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    Box(
      modifier = Modifier
        .size(36.dp)
        .clip(RoundedCornerShape(11.dp))
        .background(iconTint.copy(alpha = 0.16f)),
      contentAlignment = Alignment.Center
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = iconTint,
        modifier = Modifier.size(18.dp)
      )
    }
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = title,
        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
        color = MaterialTheme.colorScheme.onSurface
      )
      Text(
        text = subtitle,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
  }
}

@Composable
private fun SearchPreviewBackdrop(content: @Composable () -> Unit) {
  AurixTheme(darkTheme = true) {
    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(
          Brush.verticalGradient(
            colors = listOf(Color(0xFF424E5B), Color(0xFF0A1426))
          )
        )
        .padding(horizontal = 20.dp)
    ) {
      content()
    }
  }
}

@Preview(name = "SearchResults - Loading", showBackground = true)
@Composable
private fun SearchResultsLoadingPreview() {
  SearchPreviewBackdrop {
    SearchResultsArea(
      uiState = SearchUiState.Loading(mode = SearchMode.YouTubeSearch, query = "about"),
      currentSong = null,
      isPlaying = false,
      darkTheme = true,
      reducedMotion = false
    )
  }
}

@Preview(name = "SearchResults - Results", showBackground = true)
@Composable
private fun SearchResultsListPreview() {
  val sample = listOf(
    Song("yt_1", "Heaven Sent", "Esthero", "", 210L, "https://www.youtube.com/watch?v=1", "YouTube", isFavorite = true),
    Song("yt_2", "About You", "The 1975", "", 240L, "https://www.youtube.com/watch?v=2", "YouTube", isFavorite = false)
  )
  SearchPreviewBackdrop {
    SearchResultsArea(
      uiState = SearchUiState.Results(sample),
      currentSong = sample.first(),
      isPlaying = true,
      darkTheme = true,
      reducedMotion = false
    )
  }
}

@Preview(name = "SearchResults - Empty", showBackground = true)
@Composable
private fun SearchResultsEmptyPreview() {
  SearchPreviewBackdrop {
    SearchResultsArea(
      uiState = SearchUiState.Empty,
      currentSong = null,
      isPlaying = false,
      darkTheme = true,
      reducedMotion = false
    )
  }
}

@Preview(name = "SearchResults - Error", showBackground = true)
@Composable
private fun SearchResultsErrorPreview() {
  SearchPreviewBackdrop {
    SearchResultsArea(
      uiState = SearchUiState.Error("Search failed. Check your connection."),
      currentSong = null,
      isPlaying = false,
      darkTheme = true,
      reducedMotion = false
    )
  }
}

@Composable
private fun SearchSkeletonLoadingView(
  @Suppress("UNUSED_PARAMETER") darkTheme: Boolean,
  reducedMotion: Boolean,
  modeText: String
) {
  OrbiSearchLoadingScreen(
    statusText = modeText,
    runner = OrbiRunner.Search,
    outcome = OrbiStatusOutcome.Loading,
    showSearchBarWithCaret = false,
    reducedMotion = reducedMotion,
    modifier = Modifier
      .fillMaxSize()
      .padding(top = 4.dp)
  )
}
