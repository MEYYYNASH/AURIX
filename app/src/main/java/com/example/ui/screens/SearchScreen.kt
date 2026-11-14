package com.example.ui.screens

import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.Song

@Composable
fun SearchScreen(
  searchMode: String,
  searchQuery: String,
  searchResults: List<Song>,
  isSearching: Boolean,
  searchError: String?,
  youtubeUrl: String,
  analyzedSong: Song?,
  isAnalyzingYoutube: Boolean,
  youtubeError: String?,
  currentSong: Song?,
  isPlaying: Boolean,
  darkTheme: Boolean,
  reducedMotion: Boolean,
  songDownloadProgress: Map<String, Float>,
  downloadedSongIds: Set<String>,
  onSearchModeChanged: (String) -> Unit,
  onSearchQueryChanged: (String) -> Unit,
  onYoutubeUrlChanged: (String) -> Unit,
  onAnalyzeYoutube: () -> Unit,
  onSongClick: (Song, List<Song>) -> Unit,
  onFavoriteClick: (Song) -> Unit,
  onDownloadClick: (Song) -> Unit
) {
  val context = LocalContext.current
  val keyboardController = LocalSoftwareKeyboardController.current
  val quickTags = listOf("Top Hits", "Khmer", "Pop", "R&B", "Hip-Hop", "Lo-Fi")

  Column(
    modifier = Modifier
      .fillMaxSize()
      .padding(horizontal = 16.dp)
      .windowInsetsPadding(WindowInsets.statusBars)
  ) {
    Spacer(modifier = Modifier.height(14.dp))

    Text(
      text = "Search",
      style = MaterialTheme.typography.displayLarge.copy(fontSize = 28.sp, fontWeight = FontWeight.Bold),
      color = MaterialTheme.colorScheme.onBackground
    )
    Text(
      text = "iTunes 30s previews & link lookup",
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Spacer(modifier = Modifier.height(14.dp))

    // Mode Switcher (ONLY in Search, moved out of Home)
    val switcherBg = if (darkTheme) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.72f)
    val switcherBorder = if (darkTheme) Color.White.copy(alpha = 0.14f) else Color.White
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(28.dp))
        .background(switcherBg)
        .border(1.dp, switcherBorder, RoundedCornerShape(28.dp))
        .padding(4.dp),
      horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
      Button(
        onClick = { onSearchModeChanged("itunes") },
        modifier = Modifier.weight(1f).testTag("mode_search_button"),
        colors = ButtonDefaults.buttonColors(
          containerColor = if (searchMode == "itunes") MaterialTheme.colorScheme.surface else Color.Transparent,
          contentColor = if (searchMode == "itunes") MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(24.dp)
      ) {
        Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text("iTunes 30s", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), maxLines = 1)
      }

      Button(
        onClick = { onSearchModeChanged("youtube") },
        modifier = Modifier.weight(1f).testTag("mode_youtube_button"),
        colors = ButtonDefaults.buttonColors(
          containerColor = if (searchMode == "youtube") MaterialTheme.colorScheme.surface else Color.Transparent,
          contentColor = if (searchMode == "youtube") MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(24.dp)
      ) {
        Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text("YouTube Link", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), maxLines = 1)
      }
    }

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
        // YouTube Link Matcher Card (ONLY in Search)
        LazyColumn(
          modifier = Modifier.fillMaxSize(),
          verticalArrangement = Arrangement.spacedBy(16.dp),
          contentPadding = PaddingValues(bottom = if (currentSong != null) 195.dp else 125.dp)
        ) {
          item {
            GlassCard(
              darkTheme = darkTheme,
              cornerRadius = 26.dp,
              modifier = Modifier.fillMaxWidth()
            ) {
              Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
              ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                  Spacer(modifier = Modifier.width(8.dp))
                  Text(
                    text = "YouTube Video & Link Matcher",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                  )
                }

                Text(
                  text = "Paste a YouTube link to identify the track and stream a matching 30-second iTunes preview.",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                  value = youtubeUrl,
                  onValueChange = onYoutubeUrlChanged,
                  placeholder = { Text("Paste YouTube link (youtu.be/... or youtube.com...)") },
                  leadingIcon = { Icon(Icons.Rounded.Link, contentDescription = null) },
                  trailingIcon = {
                    if (youtubeUrl.isNotEmpty()) {
                      IconButton(onClick = { onYoutubeUrlChanged("") }) {
                        Icon(Icons.Rounded.Close, contentDescription = "Clear link")
                      }
                    }
                  },
                  modifier = Modifier.fillMaxWidth().testTag("youtube_url_input"),
                  shape = RoundedCornerShape(18.dp),
                  singleLine = true
                )

                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                  OutlinedButton(
                    onClick = {
                      val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                      val clip = cm?.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()?.trim().orEmpty()
                      if (clip.isNotBlank()) {
                        onYoutubeUrlChanged(clip)
                      } else {
                        Toast.makeText(context, "Clipboard is empty", Toast.LENGTH_SHORT).show()
                      }
                    },
                    modifier = Modifier.weight(1f).height(46.dp),
                    shape = RoundedCornerShape(20.dp)
                  ) {
                    Icon(Icons.Rounded.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Paste Link")
                  }

                  Button(
                    onClick = {
                      keyboardController?.hide()
                      onAnalyzeYoutube()
                    },
                    enabled = !isAnalyzingYoutube,
                    modifier = Modifier.weight(1f).height(46.dp).testTag("analyze_link_button"),
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                  ) {
                    if (isAnalyzingYoutube) {
                      CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = Color.White
                      )
                    } else {
                      Icon(Icons.Rounded.GraphicEq, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (isAnalyzingYoutube) "Matching..." else "Analyze Link")
                  }
                }

                if (!youtubeError.isNullOrBlank()) {
                  Text(
                    text = youtubeError,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                  )
                }
              }
            }
          }

          if (analyzedSong != null) {
            item {
              GlassCard(
                darkTheme = darkTheme,
                cornerRadius = 26.dp,
                modifier = Modifier.fillMaxWidth()
              ) {
                Column(
                  modifier = Modifier.padding(18.dp),
                  verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    SongArtwork(song = analyzedSong, modifier = Modifier.size(64.dp), cornerRadius = 16.dp)
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                      Text(
                        text = "iTunes 30s Preview Match",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.primary
                      )
                      Text(
                        text = analyzedSong.title,
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                      )
                      Text(
                        text = analyzedSong.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                      )
                    }
                  }

                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                  ) {
                    Button(
                      onClick = { onSongClick(analyzedSong, listOf(analyzedSong)) },
                      modifier = Modifier.weight(1f).height(46.dp),
                      shape = RoundedCornerShape(20.dp)
                    ) {
                      Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                      Spacer(modifier = Modifier.width(4.dp))
                      Text("Play 30s")
                    }
                    Button(
                      onClick = { onDownloadClick(analyzedSong) },
                      modifier = Modifier.weight(1f).height(46.dp),
                      shape = RoundedCornerShape(20.dp),
                      colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                      Icon(Icons.Rounded.Download, contentDescription = null)
                      Spacer(modifier = Modifier.width(4.dp))
                      Text("Save 30s")
                    }
                  }
                }
              }
            }
          }
        }
      } else {
        // Real iTunes Search (30-second Previews)
        Column(modifier = Modifier.fillMaxSize()) {
          OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChanged,
            placeholder = { Text("Search any song or artist (iTunes 30s)") },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
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
            shape = RoundedCornerShape(999.dp),
            modifier = Modifier.fillMaxWidth().testTag("search_input_field"),
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
              Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
              }
            }
            !searchError.isNullOrBlank() -> {
              Box(modifier = Modifier.fillMaxSize().padding(bottom = 80.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                  Text(searchError, color = MaterialTheme.colorScheme.onSurfaceVariant)
                  OutlinedButton(onClick = { onSearchQueryChanged(searchQuery) }) {
                    Text("Try Again")
                  }
                }
              }
            }
            searchResults.isEmpty() && searchQuery.isNotBlank() -> {
              Box(modifier = Modifier.fillMaxSize().padding(bottom = 80.dp), contentAlignment = Alignment.Center) {
                Text("No 30s iTunes previews found. Try another title or artist.", color = MaterialTheme.colorScheme.onSurfaceVariant)
              }
            }
            searchResults.isEmpty() -> {
              Box(modifier = Modifier.fillMaxSize().padding(bottom = 80.dp), contentAlignment = Alignment.Center) {
                Text("Search iTunes for 30-second song previews.", color = MaterialTheme.colorScheme.onSurfaceVariant)
              }
            }
            else -> {
              LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(bottom = if (currentSong != null) 195.dp else 125.dp)
              ) {
                itemsIndexed(searchResults, key = { _, song -> song.id }) { index, song ->
                  SongDownloadRow(
                    song = song,
                    index = index + 1,
                    isPlaying = isPlaying,
                    isCurrent = currentSong?.id == song.id,
                    reducedMotion = reducedMotion,
                    downloadProgress = songDownloadProgress[song.id],
                    isDownloaded = song.id in downloadedSongIds,
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
