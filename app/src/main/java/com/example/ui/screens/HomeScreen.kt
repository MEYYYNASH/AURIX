package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.Song

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
  appMode: String,
  youtubeUrl: String,
  analyzedSong: Song?,
  trendingTracks: List<Song>,
  currentSong: Song?,
  isPlaying: Boolean,
  reducedMotion: Boolean,
  onAppModeChanged: (String) -> Unit,
  onYoutubeUrlChanged: (String) -> Unit,
  onAnalyzeYoutube: () -> Unit,
  onSongClick: (Song) -> Unit,
  onMoodClick: (String) -> Unit,
  onFavoriteClick: (Song) -> Unit
) {
  val quickFilters = listOf(
    "បទថ្មី 2026 · New Khmer" to "Khmer",
    "បទសេដ · Sad Song" to "Sad",
    "បទញាក់ · Remix & DJ" to "Remix"
  )
  val categories = listOf("All", "Trending New", "Songs", "Videos", "Artists")
  val khmerArtists = listOf(
    "VannDa · វណ្ណដា" to "VannDa",
    "Tena · តេណា" to "Tena",
    "Suly Pheng · ស៊ូលីផេង" to "Suly Pheng",
    "G-Devith" to "G-Devith"
  )
  val quickYoutube = listOf(
    "VannDa - Time To Rise" to "https://youtu.be/6j-8_eS0rN0",
    "The Weeknd - Blinding Lights" to "https://youtu.be/4NRXx6U8ABQ"
  )

  var selectedCategory by remember { mutableIntStateOf(0) }
  var quickSearchText by remember { mutableStateOf("") }

  LazyColumn(
    modifier = Modifier
      .fillMaxSize()
      .padding(horizontal = 16.dp),
    verticalArrangement = Arrangement.spacedBy(18.dp),
    contentPadding = PaddingValues(
      top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 14.dp,
      bottom = if (currentSong != null) 190.dp else 120.dp
    )
  ) {
    // Header
    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
          Text(
            text = if (appMode == "search") "All Sound Finder & Search" else "All Sound & Link Downloader",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, fontSize = 21.sp),
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
          Spacer(modifier = Modifier.height(2.dp))
          Text(
            text = "Songs, Remixes, Lyrics & Links (Audius · LRCLIB · YouTube)",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }
        Surface(
          shape = RoundedCornerShape(20.dp),
          color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f)
        ) {
          Row(
            modifier = Modifier
              .clickable { onAppModeChanged(if (appMode == "search") "youtube" else "search") }
              .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Icon(
              Icons.Rounded.GraphicEq,
              contentDescription = "Sound Finder",
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text("Sound Finder", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.onSurface)
          }
        }
      }
    }

    // Top Tabs Switcher (Search & Download vs YouTube Link)
    item {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(28.dp))
          .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f))
          .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
      ) {
        Button(
          onClick = { onAppModeChanged("search") },
          modifier = Modifier.weight(1f).testTag("mode_search_button"),
          colors = ButtonDefaults.buttonColors(
            containerColor = if (appMode == "search") MaterialTheme.colorScheme.surface else Color.Transparent,
            contentColor = if (appMode == "search") MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
          ),
          elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
          shape = RoundedCornerShape(24.dp)
        ) {
          Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text("Search & Play", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), maxLines = 1)
        }

        Button(
          onClick = { onAppModeChanged("youtube") },
          modifier = Modifier.weight(1f).testTag("mode_youtube_button"),
          colors = ButtonDefaults.buttonColors(
            containerColor = if (appMode == "youtube") MaterialTheme.colorScheme.surface else Color.Transparent,
            contentColor = if (appMode == "youtube") MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
          ),
          elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
          shape = RoundedCornerShape(24.dp)
        ) {
          Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text("YouTube Link", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), maxLines = 1)
        }
      }
    }

    item {
      AnimatedContent(
        targetState = appMode,
        transitionSpec = {
          fadeIn(tween(if (reducedMotion) 0 else 220)) togetherWith fadeOut(tween(if (reducedMotion) 0 else 180))
        },
        label = "home_mode"
      ) { mode ->
        if (mode == "youtube") {
          Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Surface(
              modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)),
              color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f),
              tonalElevation = 4.dp
            ) {
              Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
              ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                  Spacer(modifier = Modifier.width(8.dp))
                  Text(
                    "YouTube Video & MP3 Link Downloader",
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                  )
                }

                OutlinedTextField(
                  value = youtubeUrl,
                  onValueChange = onYoutubeUrlChanged,
                  placeholder = { Text("Paste YouTube link (youtu.be/... or youtube.com...)") },
                  leadingIcon = { Icon(Icons.Rounded.Link, contentDescription = null) },
                  modifier = Modifier.fillMaxWidth().testTag("youtube_url_input"),
                  shape = RoundedCornerShape(18.dp),
                  singleLine = true
                )

                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                  OutlinedButton(
                    onClick = { onYoutubeUrlChanged("https://youtu.be/6j-8_eS0rN0") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(20.dp)
                  ) {
                    Icon(Icons.Rounded.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Paste Link")
                  }
                  Button(
                    onClick = onAnalyzeYoutube,
                    modifier = Modifier.weight(1f).testTag("analyze_link_button"),
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                  ) {
                    Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Analyze Link")
                  }
                }

                Column {
                  Text("Quick Try YouTube Links:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                  Spacer(modifier = Modifier.height(8.dp))
                  Row(
                    modifier = Modifier
                      .fillMaxWidth()
                      .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                  ) {
                    quickYoutube.forEach { (label, url) ->
                      AssistChip(
                        onClick = { onYoutubeUrlChanged(url); onAnalyzeYoutube() },
                        label = { Text(label, maxLines = 1) },
                        shape = RoundedCornerShape(16.dp)
                      )
                    }
                  }
                }
              }
            }

            if (analyzedSong != null) {
              Surface(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f),
                tonalElevation = 6.dp
              ) {
                Column(
                  modifier = Modifier.padding(16.dp),
                  verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    SongArtwork(song = analyzedSong, modifier = Modifier.size(64.dp), cornerRadius = 16.dp)
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                      Text("World Trending · 3:30", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                      Text(analyzedSong.title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface)
                      Text(analyzedSong.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                  }

                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                  ) {
                    Button(
                      onClick = { onSongClick(analyzedSong) },
                      modifier = Modifier.weight(1f),
                      shape = RoundedCornerShape(20.dp)
                    ) {
                      Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                      Spacer(modifier = Modifier.width(4.dp))
                      Text("Play Audio")
                    }
                    Button(
                      onClick = { onSongClick(analyzedSong) },
                      modifier = Modifier.weight(1f),
                      shape = RoundedCornerShape(20.dp),
                      colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                      Icon(Icons.Rounded.Download, contentDescription = null)
                      Spacer(modifier = Modifier.width(4.dp))
                      Text("Save Audio")
                    }
                  }
                }
              }
            }
          }
        } else {
          Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            OutlinedTextField(
              value = quickSearchText,
              onValueChange = { quickSearchText = it },
              placeholder = { Text("Search any song without limits (VannDa, ណារ៉ា,...)") },
              leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
              trailingIcon = {
                IconButton(onClick = { if (quickSearchText.isNotBlank()) onMoodClick(quickSearchText) }) {
                  Icon(Icons.Rounded.GraphicEq, contentDescription = "Search", tint = MaterialTheme.colorScheme.primary)
                }
              },
              keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
              keyboardActions = KeyboardActions(onSearch = {
                if (quickSearchText.isNotBlank()) onMoodClick(quickSearchText)
              }),
              modifier = Modifier.fillMaxWidth().testTag("home_search_field"),
              shape = RoundedCornerShape(28.dp),
              singleLine = true
            )

            Row(
              modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              quickFilters.forEach { (label, query) ->
                AssistChip(
                  onClick = { onMoodClick(query) },
                  label = { Text(label) },
                  shape = RoundedCornerShape(16.dp)
                )
              }
            }

            Row(
              modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              categories.forEachIndexed { index, cat ->
                FilterChip(
                  selected = selectedCategory == index,
                  onClick = {
                    selectedCategory = index
                    if (index > 0) onMoodClick(cat)
                  },
                  label = { Text(cat) },
                  shape = RoundedCornerShape(16.dp)
                )
              }
            }

            Column {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Text(
                  "Khmer Artists · តារាចម្រៀងខ្មែរ",
                  style = MaterialTheme.typography.titleLarge.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold),
                  color = MaterialTheme.colorScheme.onBackground
                )
                TextButton(onClick = { onMoodClick("Khmer") }) {
                  Text("View All", color = MaterialTheme.colorScheme.primary)
                }
              }
              Spacer(modifier = Modifier.height(6.dp))
              Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                khmerArtists.forEach { (label, query) ->
                  AssistChip(
                    onClick = { onMoodClick(query) },
                    label = { Text(label) },
                    shape = RoundedCornerShape(20.dp)
                  )
                }
              }
            }

            // Featured Popular Track Card
            val featuredSong = trendingTracks.firstOrNull()
            if (featuredSong != null) {
              Surface(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f),
                tonalElevation = 6.dp
              ) {
                Column(
                  modifier = Modifier.padding(16.dp),
                  verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    SongArtwork(
                      song = featuredSong,
                      modifier = Modifier.size(88.dp),
                      cornerRadius = 20.dp,
                      iconSize = 36.dp
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                      Text(
                        "#1 Popular on Audius",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.primary
                      )
                      Spacer(modifier = Modifier.height(4.dp))
                      Text(
                        featuredSong.title,
                        style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                      )
                      Text(
                        featuredSong.artist,
                        style = MaterialTheme.typography.bodyMedium,
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
                      onClick = { onSongClick(featuredSong) },
                      modifier = Modifier.weight(1f),
                      shape = RoundedCornerShape(24.dp),
                      colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface)
                    ) {
                      Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                      Spacer(modifier = Modifier.width(4.dp))
                      Text("Play")
                    }
                    Button(
                      onClick = { onFavoriteClick(featuredSong) },
                      modifier = Modifier.weight(1f),
                      shape = RoundedCornerShape(24.dp),
                      colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                      Icon(
                        if (featuredSong.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        contentDescription = null
                      )
                      Spacer(modifier = Modifier.width(6.dp))
                      Text(if (featuredSong.isFavorite) "Saved" else "Save Song")
                    }
                  }
                }
              }
            }
          }
        }
      }
    }

    if (appMode == "search") {
      item {
        Text(
          "Popular Songs First · បទល្បីៗ",
          style = MaterialTheme.typography.titleLarge.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold),
          color = MaterialTheme.colorScheme.onBackground
        )
      }

      trendingTracks.forEachIndexed { index, song ->
        item(key = song.id) {
          SongDownloadRow(
            song = song,
            index = index + 1,
            isPlaying = isPlaying,
            isCurrent = currentSong?.id == song.id,
            reducedMotion = reducedMotion,
            onSongClick = { onSongClick(song) },
            onFavoriteClick = { onFavoriteClick(song) }
          )
        }
      }
    }
  }
}
