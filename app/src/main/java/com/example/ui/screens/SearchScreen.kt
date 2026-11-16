package com.example.ui.screens

import android.content.ClipboardManager
import android.content.Context
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.R
import com.example.model.AudioFormatOption
import com.example.model.SearchMode
import com.example.model.SearchUiState
import com.example.model.Song
import com.example.model.YoutubeAnalysisResult
import com.example.model.YoutubeDownloadState
import com.example.model.YoutubeFormatSheetState
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
        "itunes" -> SearchMode.ITunes30s
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
  onImmediateSearch: (String) -> Unit = onSearchQueryChanged
) {
  val context = LocalContext.current
  val keyboardController = LocalSoftwareKeyboardController.current
  val quickTags = listOf("TikTok Viral", "Khmer", "Phonk", "Pop", "R&B", "Hip-Hop")
  val activeModeEnum = when (searchMode) {
    "youtube" -> SearchMode.YouTubeLink
    "itunes" -> SearchMode.ITunes30s
    else -> SearchMode.YouTubeSearch
  }

  Box(modifier = Modifier.fillMaxSize()) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(horizontal = 20.dp)
        .windowInsetsPadding(WindowInsets.statusBars)
    ) {
      Spacer(modifier = Modifier.height(14.dp))

      Text(
        text = stringResource(id = R.string.search_title),
        style = MaterialTheme.typography.displayLarge.copy(fontSize = 28.sp, lineHeight = 38.sp, fontWeight = FontWeight.ExtraBold),
        color = MaterialTheme.colorScheme.onBackground
      )
      Text(
        text = stringResource(id = R.string.search_subtitle),
        style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )

      Spacer(modifier = Modifier.height(14.dp))

      // Unified Liquid Glass 3-Mode Switcher (YouTube | iTunes 30s | YT Link)
      GlassSegmentedBar(
        options = listOf(
          stringResource(id = R.string.search_mode_yt_search) to Icons.Rounded.PlayCircle,
          stringResource(id = R.string.search_mode_itunes) to Icons.Rounded.MusicNote,
          stringResource(id = R.string.search_mode_youtube) to Icons.Rounded.Link
        ),
        selectedIndex = when (searchMode) {
          "itunes" -> 1
          "youtube" -> 2
          else -> 0
        },
        darkTheme = darkTheme,
        onSelect = { idx ->
          val next = when (idx) {
            1 -> "itunes"
            2 -> "youtube"
            else -> "youtube_search"
          }
          onSearchModeChanged(next)
        },
        testTags = listOf("mode_youtube_search_button", "mode_itunes_button", "mode_youtube_button"),
        reducedMotion = reducedMotion
      )

      Spacer(modifier = Modifier.height(14.dp))

      AnimatedContent(
        targetState = searchMode,
        modifier = Modifier.weight(1f).fillMaxWidth(),
        transitionSpec = {
          fadeIn(tween(if (reducedMotion) 0 else 200)) togetherWith fadeOut(tween(if (reducedMotion) 0 else 160))
        },
        label = "search_mode_content"
      ) { mode ->
        if (mode == "youtube") {
          val inputBg = if (darkTheme) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.85f)
          val inputBorder = if (darkTheme) Color.White.copy(alpha = 0.18f) else Color.White
          val pasteBg = if (darkTheme) Color.White.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.85f)

          LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = if (currentSong != null) 205.dp else 135.dp)
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
                        IconButton(onClick = { onYoutubeUrlChanged("") }) {
                          Icon(Icons.Rounded.Close, contentDescription = "Clear URL")
                        }
                      }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                      keyboardController?.hide()
                      onAnalyzeYoutube()
                    }),
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
                          val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                          val clip = cm?.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()?.trim().orEmpty()
                          if (clip.isNotBlank()) {
                            onYoutubeUrlChanged(clip)
                          } else {
                            Toast.makeText(context, context.getString(R.string.search_yt_clipboard_empty), Toast.LENGTH_SHORT).show()
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
                      onClick = {
                        keyboardController?.hide()
                        onAnalyzeYoutube()
                      },
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
                    .height(260.dp)
                    .semantics {
                      liveRegion = LiveRegionMode.Polite
                      contentDescription = "Searching"
                    }
                    .testTag("youtube_analyzing_orbit_loader"),
                  contentAlignment = Alignment.Center
                ) {
                  Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                  ) {
                    OrbitLoader(
                      size = 84.dp,
                      ringColor = OrbitRingDefault,
                      planetColor = OrbitPlanetDefault,
                      eyeColor = OrbitEyeDefault,
                      cometColor = AurixTealAccent,
                      reducedMotion = reducedMotion
                    )
                    Text(
                      text = "Analyzing link…",
                      style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                      textAlign = TextAlign.Center
                    )
                  }
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
                  onPlaySaved = { saved -> onSongClick(saved, listOf(saved)) },
                  onOpenMySongs = onOpenMySongs
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
                    Text(
                      text = stringResource(id = R.string.search_yt_guide_title),
                      style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, lineHeight = 20.sp),
                      color = MaterialTheme.colorScheme.onSurface
                    )
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
        } else {
          Column(modifier = Modifier.fillMaxSize()) {
            val fieldBrush = if (darkTheme) {
              Brush.linearGradient(
                colors = listOf(Color(0xFF1F2B44).copy(alpha = 0.84f), Color(0xFF141D31).copy(alpha = 0.78f))
              )
            } else {
              Brush.linearGradient(
                colors = listOf(Color.White.copy(alpha = 0.94f), Color(0xFFF2F6FF).copy(alpha = 0.84f))
              )
            }
            val fieldBorder = Brush.verticalGradient(
              colors = if (darkTheme) {
                listOf(Color.White.copy(alpha = 0.30f), Color.White.copy(alpha = 0.10f))
              } else {
                listOf(Color.White, Color.White.copy(alpha = 0.70f))
              }
            )
            val placeholderId = if (mode == "youtube_search") {
              R.string.search_yt_songs_placeholder
            } else {
              R.string.search_placeholder
            }

            Box(
              modifier = Modifier
                .fillMaxWidth()
                .clip(CircleShape)
                .background(fieldBrush)
                .border(1.dp, fieldBorder, CircleShape)
            ) {
              TextField(
                value = searchQuery,
                onValueChange = onSearchQueryChanged,
                placeholder = {
                  Text(
                    text = stringResource(id = placeholderId),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                  )
                },
                leadingIcon = {
                  Icon(
                    imageVector = if (mode == "youtube_search") Icons.Rounded.PlayCircle else Icons.Rounded.Search,
                    contentDescription = null,
                    tint = if (mode == "youtube_search") Color(0xFFEF4444) else MaterialTheme.colorScheme.primary
                  )
                },
                trailingIcon = {
                  if (searchQuery.isNotEmpty()) {
                    IconButton(
                      onClick = onClearSearch,
                      modifier = Modifier.testTag("search_clear_button")
                    ) {
                      Icon(Icons.Rounded.Close, contentDescription = "Clear search")
                    }
                  }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                  keyboardController?.hide()
                  if (searchQuery.isNotBlank()) onImmediateSearch(searchQuery)
                }),
                colors = TextFieldDefaults.colors(
                  focusedContainerColor = Color.Transparent,
                  unfocusedContainerColor = Color.Transparent,
                  focusedIndicatorColor = Color.Transparent,
                  unfocusedIndicatorColor = Color.Transparent,
                  disabledIndicatorColor = Color.Transparent
                ),
                modifier = Modifier
                  .fillMaxWidth()
                  .testTag("search_input_field"),
                singleLine = true
              )
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (searchQuery.isBlank()) {
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                quickTags.forEach { tag ->
                  val chipBrush = if (darkTheme) {
                    Brush.linearGradient(
                      colors = listOf(Color.White.copy(alpha = 0.14f), Color.White.copy(alpha = 0.06f))
                    )
                  } else {
                    Brush.linearGradient(
                      colors = listOf(Color.White.copy(alpha = 0.92f), Color(0xFFF3F6FF).copy(alpha = 0.80f))
                    )
                  }
                  val chipBorder = if (darkTheme) Color.White.copy(alpha = 0.22f) else Color.White
                  Box(
                    modifier = Modifier
                      .clip(CircleShape)
                      .background(chipBrush)
                      .border(1.dp, chipBorder, CircleShape)
                      .clickable { onSearchQueryChanged(tag) }
                      .padding(horizontal = 14.dp, vertical = 9.dp)
                  ) {
                    Text(
                      text = tag,
                      style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                      color = MaterialTheme.colorScheme.onSurface
                    )
                  }
                }
              }
              Spacer(modifier = Modifier.height(12.dp))
            }

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
              onCheckLyrics = onCheckLyrics,
              onSongClick = onSongClick,
              onFavoriteClick = onFavoriteClick,
              onDownloadClick = { song ->
                if (song.source == "YouTube") onYoutubeSongDownloadClick(song) else onDownloadClick(song)
              },
              onRetry = onRetrySearch,
              modifier = Modifier.weight(1f).fillMaxWidth()
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
  onCheckLyrics: (Song) -> Unit = {},
  onSongClick: (Song, List<Song>) -> Unit = { _, _ -> },
  onFavoriteClick: (Song) -> Unit = {},
  onDownloadClick: (Song) -> Unit = {},
  onRetry: () -> Unit = {},
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
        (fadeIn(tween(350)) + slideInVertically(tween(350)) { it / 16 }) togetherWith
          fadeOut(tween(220))
      } else {
        fadeIn(tween(350)) togetherWith fadeOut(tween(220))
      }
    },
    label = "search_results_area"
  ) { state ->
    when (state) {
      SearchUiState.Idle -> {
        Box(
          modifier = Modifier
            .fillMaxSize()
            .padding(bottom = centerBottomPadding),
          contentAlignment = Alignment.Center
        ) {
          GlassCard(darkTheme = darkTheme, cornerRadius = 26.dp, modifier = Modifier.fillMaxWidth()) {
            Column(
              modifier = Modifier.fillMaxWidth().padding(24.dp),
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
              val isYtSearch = searchMode == SearchMode.YouTubeSearch
              val badgeColor = if (isYtSearch) Color(0xFFEF4444) else MaterialTheme.colorScheme.primary
              Box(
                modifier = Modifier
                  .size(56.dp)
                  .clip(CircleShape)
                  .background(badgeColor.copy(alpha = 0.16f))
                  .border(1.dp, badgeColor.copy(alpha = 0.30f), CircleShape),
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
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
              )
            }
          }
        }
      }

      is SearchUiState.Loading -> {
        Box(
          modifier = Modifier
            .fillMaxSize()
            .padding(bottom = centerBottomPadding)
            .semantics {
              liveRegion = LiveRegionMode.Polite
              contentDescription = "Searching"
            }
            .testTag("search_orbit_loader"),
          contentAlignment = Alignment.Center
        ) {
          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
          ) {
            OrbitLoader(
              size = 84.dp,
              ringColor = OrbitRingDefault,
              planetColor = OrbitPlanetDefault,
              eyeColor = OrbitEyeDefault,
              cometColor = AurixTealAccent,
              reducedMotion = reducedMotion
            )
            Text(
              text = when (state.mode) {
                SearchMode.YouTubeSearch -> stringResource(id = R.string.search_loading_yt)
                SearchMode.ITunes30s -> stringResource(id = R.string.search_loading_itunes)
                SearchMode.YouTubeLink -> stringResource(id = R.string.search_loading_link)
              },
              style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              textAlign = TextAlign.Center
            )
          }
        }
      }

      is SearchUiState.Results -> {
        LazyColumn(
          modifier = Modifier.fillMaxSize(),
          verticalArrangement = Arrangement.spacedBy(6.dp),
          contentPadding = PaddingValues(bottom = listBottomPadding)
        ) {
          itemsIndexed(state.items, key = { _, song -> song.id }) { index, song ->
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
              onSongClick = { onSongClick(song, state.items) },
              onFavoriteClick = { onFavoriteClick(song) },
              onDownloadClick = { onDownloadClick(song) }
            )
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
          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
          ) {
            OrbitLoader(
              size = 84.dp,
              ringColor = OrbitRingDefault,
              planetColor = OrbitPlanetDefault,
              eyeColor = OrbitEyeDefault,
              reducedMotion = true,
              isStatic = true,
              showComet = false
            )
            Text(
              text = "No songs found",
              style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
              color = MaterialTheme.colorScheme.onBackground,
              textAlign = TextAlign.Center
            )
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
          Column(
            modifier = Modifier.padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
          ) {
            Text(
              text = state.message.ifBlank { "Search failed. Check your connection." },
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
  onOpenMySongs: () -> Unit
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
      // Video Thumbnail + Title + Channel
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
              String.format(Locale.US, "FULL SONG • %d:%02d", min, sec)
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

      // Format / Quality Selector
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
          text = "Select format & quality",
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

      // Live Download Progress Section
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
                text = "Saved to App (My songs) & Music/AURIX",
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
                modifier = Modifier.weight(1f).height(44.dp),
                shape = CircleShape
              ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Play Now", fontWeight = FontWeight.Bold)
              }
              OutlinedButton(
                onClick = onOpenMySongs,
                modifier = Modifier.weight(1f).height(44.dp),
                shape = CircleShape,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.50f))
              ) {
                Icon(Icons.Rounded.LibraryMusic, contentDescription = null, modifier = Modifier.size(17.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("My songs", fontWeight = FontWeight.Bold)
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

      // Play Now + Download Selected Format CTAs
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
