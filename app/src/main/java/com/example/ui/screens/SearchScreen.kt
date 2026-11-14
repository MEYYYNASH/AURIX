package com.example.ui.screens

import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.model.AudioFormatOption
import com.example.model.Song
import com.example.model.YoutubeAnalysisResult
import com.example.model.YoutubeDownloadState
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
  verifiedLyricsIds: Set<String> = emptySet(),
  onCheckLyrics: (Song) -> Unit = {},
  onSearchModeChanged: (String) -> Unit,
  onSearchQueryChanged: (String) -> Unit,
  onYoutubeUrlChanged: (String) -> Unit,
  onAnalyzeYoutube: () -> Unit,
  onSelectYoutubeFormat: (String) -> Unit,
  onDownloadYoutube: () -> Unit,
  onOpenMySongs: () -> Unit,
  onSongClick: (Song, List<Song>) -> Unit,
  onFavoriteClick: (Song) -> Unit,
  onDownloadClick: (Song) -> Unit
) {
  val context = LocalContext.current
  val keyboardController = LocalSoftwareKeyboardController.current
  val quickTags = listOf("TikTok Viral", "Khmer", "Phonk", "Pop", "R&B", "Hip-Hop")

  Box(modifier = Modifier.fillMaxSize()) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(horizontal = 20.dp)
        .windowInsetsPadding(WindowInsets.statusBars)
    ) {
      Spacer(modifier = Modifier.height(14.dp))

      Text(
        text = "Search",
        style = MaterialTheme.typography.displayLarge.copy(fontSize = 28.sp, fontWeight = FontWeight.ExtraBold),
        color = MaterialTheme.colorScheme.onBackground
      )
      Text(
        text = "Find songs, analyze YouTube links & save to app",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )

      Spacer(modifier = Modifier.height(14.dp))

      // Unified Liquid Glass Mode Switcher (ONLY in Search)
      GlassSegmentedBar(
        options = listOf(
          "iTunes 30s" to Icons.Rounded.Search,
          "YouTube Link" to Icons.Rounded.PlayArrow
        ),
        selectedIndex = if (searchMode == "youtube") 1 else 0,
        darkTheme = darkTheme,
        onSelect = { idx -> onSearchModeChanged(if (idx == 1) "youtube" else "itunes") },
        testTags = listOf("mode_search_button", "mode_youtube_button")
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
                        text = "YouTube Audio Downloader",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                        color = MaterialTheme.colorScheme.onSurface
                      )
                      Text(
                        text = "Analyze URL, choose full audio format & save to AURIX",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                      )
                    }
                  }

                  TextField(
                    value = youtubeUrl,
                    onValueChange = onYoutubeUrlChanged,
                    placeholder = {
                      Text(
                        text = "Paste YouTube URL (youtube.com / youtu.be)...",
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
                            Toast.makeText(context, "Clipboard is empty", Toast.LENGTH_SHORT).show()
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
                          text = "Paste URL",
                          style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
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
                        text = if (isAnalyzingYoutube) "Analyzing..." else "Analyze",
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

            if (youtubeAnalysis != null) {
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
                      text = "How download works",
                      style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                      color = MaterialTheme.colorScheme.onSurface
                    )
                    MatcherGuideRow(
                      icon = Icons.Rounded.Link,
                      title = "1. Paste YouTube URL & tap Analyze",
                      subtitle = "Loads the video title, channel and cover thumbnail",
                      iconTint = MaterialTheme.colorScheme.primary
                    )
                    MatcherGuideRow(
                      icon = Icons.Rounded.Tune,
                      title = "2. Select available format & quality",
                      subtitle = "Choose M4A, MP3, or high-bitrate audio streams",
                      iconTint = Color(0xFF14B8A6)
                    )
                    MatcherGuideRow(
                      icon = Icons.Rounded.Download,
                      title = "3. Download & save to the app",
                      subtitle = "Live progress bar saves tracks straight to My songs",
                      iconTint = Color(0xFFEC4899)
                    )
                  }
                }
              }
            }
          }
        } else {
          Column(modifier = Modifier.fillMaxSize()) {
            val fieldBg = if (darkTheme) Color(0xFF172034).copy(alpha = 0.78f) else Color.White.copy(alpha = 0.88f)
            val fieldBorder = if (darkTheme) Color.White.copy(alpha = 0.18f) else Color.White

            TextField(
              value = searchQuery,
              onValueChange = onSearchQueryChanged,
              placeholder = { Text("Search songs, artists or TikTok hits...") },
              leadingIcon = {
                Icon(
                  Icons.Rounded.Search,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.primary
                )
              },
              trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                  IconButton(onClick = { onSearchQueryChanged("") }) {
                    Icon(Icons.Rounded.Close, contentDescription = "Clear search")
                  }
                }
              },
              keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
              keyboardActions = KeyboardActions(onSearch = {
                keyboardController?.hide()
                if (searchQuery.isNotBlank()) onSearchQueryChanged(searchQuery)
              }),
              colors = TextFieldDefaults.colors(
                focusedContainerColor = fieldBg,
                unfocusedContainerColor = fieldBg,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent
              ),
              shape = CircleShape,
              modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, fieldBorder, CircleShape)
                .clip(CircleShape)
                .testTag("search_input_field"),
              singleLine = true
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (searchQuery.isBlank()) {
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                quickTags.forEach { tag ->
                  val chipBg = if (darkTheme) Color.White.copy(alpha = 0.09f) else Color.White.copy(alpha = 0.85f)
                  val chipBorder = if (darkTheme) Color.White.copy(alpha = 0.16f) else Color.White
                  Box(
                    modifier = Modifier
                      .clip(CircleShape)
                      .background(chipBg)
                      .border(1.dp, chipBorder, CircleShape)
                      .clickable { onSearchQueryChanged(tag) }
                      .padding(horizontal = 14.dp, vertical = 8.dp)
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

            when {
              isSearching -> {
                Box(modifier = Modifier.fillMaxSize().padding(bottom = 80.dp), contentAlignment = Alignment.Center) {
                  CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
              }
              !searchError.isNullOrBlank() -> {
                Box(modifier = Modifier.fillMaxSize().padding(bottom = 110.dp), contentAlignment = Alignment.Center) {
                  GlassCard(darkTheme = darkTheme, cornerRadius = 24.dp, modifier = Modifier.fillMaxWidth()) {
                    Column(
                      modifier = Modifier.fillMaxWidth().padding(22.dp),
                      horizontalAlignment = Alignment.CenterHorizontally,
                      verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                      Text(searchError, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                      OutlinedButton(onClick = { onSearchQueryChanged(searchQuery) }, shape = CircleShape) {
                        Text("Try Again")
                      }
                    }
                  }
                }
              }
              searchResults.isEmpty() && searchQuery.isNotBlank() -> {
                Box(modifier = Modifier.fillMaxSize().padding(bottom = 110.dp), contentAlignment = Alignment.Center) {
                  GlassCard(darkTheme = darkTheme, cornerRadius = 24.dp, modifier = Modifier.fillMaxWidth()) {
                    Column(
                      modifier = Modifier.fillMaxWidth().padding(24.dp),
                      horizontalAlignment = Alignment.CenterHorizontally,
                      verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                      Icon(Icons.Rounded.SearchOff, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp))
                      Text(
                        text = "No 30s previews found",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                      )
                      Text(
                        text = "Try searching another track title or artist name.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                      )
                    }
                  }
                }
              }
              searchResults.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize().padding(bottom = 110.dp), contentAlignment = Alignment.Center) {
                  GlassCard(darkTheme = darkTheme, cornerRadius = 24.dp, modifier = Modifier.fillMaxWidth()) {
                    Column(
                      modifier = Modifier.fillMaxWidth().padding(24.dp),
                      horizontalAlignment = Alignment.CenterHorizontally,
                      verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                      Box(
                        modifier = Modifier
                          .size(52.dp)
                          .clip(CircleShape)
                          .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                      ) {
                        Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(26.dp))
                      }
                      Text(
                        text = "iTunes 30s Music Search",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                      )
                      Text(
                        text = "Type a song or artist above, or pick a quick trend tag.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                      )
                    }
                  }
                }
              }
              else -> {
                LazyColumn(
                  modifier = Modifier.fillMaxSize(),
                  verticalArrangement = Arrangement.spacedBy(6.dp),
                  contentPadding = PaddingValues(bottom = if (currentSong != null) 205.dp else 135.dp)
                ) {
                  itemsIndexed(searchResults, key = { _, song -> song.id }) { index, song ->
                    SongDownloadRow(
                      song = song,
                      index = index + 1,
                      isPlaying = isPlaying,
                      isCurrent = currentSong?.id == song.id,
                      reducedMotion = reducedMotion,
                      darkTheme = darkTheme,
                      hasLyrics = song.id in verifiedLyricsIds,
                      downloadProgress = songDownloadProgress[song.id],
                      isDownloaded = song.id in downloadedSongIds,
                      onCheckLyrics = onCheckLyrics,
                      onSongClick = { onSongClick(song, searchResults) },
                      onFavoriteClick = { onFavoriteClick(song) },
                      onDownloadClick = { onDownloadClick(song) }
                    )
                  }
                }
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

      // Download CTA
      Button(
        onClick = onDownload,
        enabled = !isDownloading && selectedFormat != null,
        modifier = Modifier
          .fillMaxWidth()
          .height(50.dp)
          .testTag("youtube_download_button"),
        shape = CircleShape,
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
        Spacer(modifier = Modifier.width(8.dp))
        val ctaLabel = when {
          isDownloading -> "Downloading..."
          downloadState is YoutubeDownloadState.Saved -> "Download Another Format"
          selectedFormat != null -> "Download ${selectedFormat.formatLabel} (${selectedFormat.qualityLabel})"
          else -> "Download"
        }
        Text(text = ctaLabel, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
      }
    }
  }
}

@Composable
private fun FormatOptionRow(
  option: AudioFormatOption,
  selected: Boolean,
  enabled: Boolean,
  darkTheme: Boolean,
  onClick: () -> Unit
) {
  val bg = when {
    selected -> MaterialTheme.colorScheme.primary.copy(alpha = if (darkTheme) 0.22f else 0.14f)
    darkTheme -> Color.White.copy(alpha = 0.06f)
    else -> Color.White.copy(alpha = 0.72f)
  }
  val borderColor = when {
    selected -> MaterialTheme.colorScheme.primary
    darkTheme -> Color.White.copy(alpha = 0.14f)
    else -> Color.White
  }

  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(16.dp))
      .background(bg)
      .border(if (selected) 1.5.dp else 1.dp, borderColor, RoundedCornerShape(16.dp))
      .clickable(enabled = enabled) { onClick() }
      .testTag("youtube_format_${option.id}")
      .padding(horizontal = 14.dp, vertical = 12.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    Surface(
      shape = RoundedCornerShape(10.dp),
      color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
    ) {
      Text(
        text = option.formatLabel,
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold),
        color = if (selected) Color.White else MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
      )
    }

    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = option.qualityLabel,
        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.onSurface
      )
      Text(
        text = option.detailLabel,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }

    Icon(
      imageVector = if (selected) Icons.Rounded.RadioButtonChecked else Icons.Rounded.RadioButtonUnchecked,
      contentDescription = if (selected) "Selected" else "Select format",
      tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    )
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
