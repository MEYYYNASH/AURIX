package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
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
  favorites: List<Song>,
  recentlyPlayed: List<Song>,
  localSongs: List<Song>,
  onAppModeChanged: (String) -> Unit,
  onYoutubeUrlChanged: (String) -> Unit,
  onAnalyzeYoutube: () -> Unit,
  onSongClick: (Song) -> Unit,
  onMoodClick: (String) -> Unit,
  onFavoriteClick: (Song) -> Unit
) {
  val quickFilters = listOf("បទថ្មី 2026 · New Khmer", "បទសេដ · Sad Song", "បទញាក់ · Remix & DJ")
  val categories = listOf("All", "Trending New", "Songs", "Videos", "Artists")
  val khmerArtists = listOf("VannDa · វណ្ណដា", "Tena · ទេសា", "Suly Pheng · សុលីផេង", "G-Devith")
  val quickYoutube = listOf("VannDa - Time To Rise", "The Weeknd - Blinding Lights")

  LazyColumn(
    modifier = Modifier
      .fillMaxSize()
      .padding(horizontal = 16.dp),
    verticalArrangement = Arrangement.spacedBy(20.dp),
    contentPadding = PaddingValues(top = 16.dp, bottom = 120.dp)
  ) {
    // Header
    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = if (appMode == "search") "All Sound Finder & Search" else "All Sound & Link Downloader",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, fontSize = 20.sp),
            color = MaterialTheme.colorScheme.onBackground
          )
          Spacer(modifier = Modifier.height(2.dp))
          Text(
            text = "Supports all sounds: Songs, TikTok Sounds, Remixes, SFX & Links (YouTube · iTunes · Deezer · SoundCloud)",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }
        Button(
          onClick = {},
          colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
          shape = RoundedCornerShape(20.dp),
          contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
        ) {
          Icon(Icons.Rounded.GraphicEq, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(4.dp))
          Text("Sound Finder", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
        }
      }
    }

    // Top Tabs Switcher (Search & Download vs YouTube Link)
    item {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(28.dp))
          .background(MaterialTheme.colorScheme.surfaceVariant)
          .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
      ) {
        Button(
          onClick = { onAppModeChanged("search") },
          modifier = Modifier.weight(1f),
          colors = ButtonDefaults.buttonColors(
            containerColor = if (appMode == "search") MaterialTheme.colorScheme.surface else Color.Transparent,
            contentColor = if (appMode == "search") MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
          ),
          shape = RoundedCornerShape(24.dp)
        ) {
          Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text("Search & Download", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
        }

        Button(
          onClick = { onAppModeChanged("youtube") },
          modifier = Modifier.weight(1f),
          colors = ButtonDefaults.buttonColors(
            containerColor = if (appMode == "youtube") MaterialTheme.colorScheme.surface else Color.Transparent,
            contentColor = if (appMode == "youtube") MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
          ),
          shape = RoundedCornerShape(24.dp)
        ) {
          Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text("YouTube Link", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
        }
      }
    }

    if (appMode == "youtube") {
      item {
        Surface(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp)),
          color = MaterialTheme.colorScheme.surfaceVariant,
          tonalElevation = 4.dp
        ) {
          Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.error)
              Spacer(modifier = Modifier.width(8.dp))
              Text("YouTube Video & MP3 Link Downloader", style = MaterialTheme.typography.titleLarge.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface)
            }

            OutlinedTextField(
              value = youtubeUrl,
              onValueChange = onYoutubeUrlChanged,
              placeholder = { Text("Paste YouTube link (youtu.be/... or youtube.com...)") },
              leadingIcon = { Icon(Icons.Rounded.Link, contentDescription = null) },
              modifier = Modifier.fillMaxWidth(),
              shape = RoundedCornerShape(16.dp),
              singleLine = true
            )

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
              OutlinedButton(
                onClick = { onYoutubeUrlChanged("https://youtu.be/dQw4w9WgXcQ") },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(20.dp)
              ) {
                Icon(Icons.Rounded.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Paste Link")
              }
              Button(
                onClick = onAnalyzeYoutube,
                modifier = Modifier.weight(1f),
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
                quickYoutube.forEach { link ->
                  AssistChip(
                    onClick = { onYoutubeUrlChanged("https://youtube.com/watch?v=demo"); onAnalyzeYoutube() },
                    label = { Text(link, maxLines = 1) },
                    shape = RoundedCornerShape(16.dp)
                  )
                }
              }
            }
          }
        }
      }

      if (analyzedSong != null) {
        item {
          Surface(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(26.dp)),
            color = MaterialTheme.colorScheme.surfaceVariant,
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
                Box(
                  modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                  contentAlignment = Alignment.Center
                ) {
                  Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
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
                  Text("Download MP4")
                }
                Button(
                  onClick = { onSongClick(analyzedSong) },
                  modifier = Modifier.weight(1f),
                  shape = RoundedCornerShape(20.dp),
                  colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                  Icon(Icons.Rounded.Download, contentDescription = null)
                  Spacer(modifier = Modifier.width(4.dp))
                  Text("Save Audio (MP3)")
                }
              }
            }
          }
        }
      }

    } else {
      // Search & Download Mode
      item {
        OutlinedTextField(
          value = "",
          onValueChange = {},
          placeholder = { Text("Search any song without limits (VannDa, ណារ៉ា,... )") },
          leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
          trailingIcon = { Icon(Icons.Rounded.GraphicEq, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(28.dp),
          singleLine = true
        )
      }

      item {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          quickFilters.forEach { filter ->
            AssistChip(
              onClick = {},
              label = { Text(filter) },
              shape = RoundedCornerShape(16.dp)
            )
          }
        }
      }

      item {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          categories.forEachIndexed { index, cat ->
            FilterChip(
              selected = index == 0,
              onClick = {},
              label = { Text(cat) },
              shape = RoundedCornerShape(16.dp)
            )
          }
        }
      }

      item {
        Column {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text("Khmer Artists · កាមុខចម្រៀងខ្មែរ", style = MaterialTheme.typography.titleLarge.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onBackground)
            TextButton(onClick = {}) { Text("View All (32)", color = MaterialTheme.colorScheme.primary) }
          }
          Spacer(modifier = Modifier.height(8.dp))
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            khmerArtists.forEach { artist ->
              AssistChip(
                onClick = {},
                label = { Text(artist) },
                shape = RoundedCornerShape(20.dp)
              )
            }
          }
        }
      }

      item {
        Surface(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp)),
          color = MaterialTheme.colorScheme.surfaceVariant,
          tonalElevation = 6.dp
        ) {
          Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Box(
                modifier = Modifier
                  .size(90.dp)
                  .clip(RoundedCornerShape(20.dp))
                  .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
              ) {
                Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
              }
              Spacer(modifier = Modifier.width(16.dp))
              Column(modifier = Modifier.weight(1f)) {
                Text("#1 Popular · 15.4M global plays · 33 songs", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                Text("Die with a smile (tiktok remix)", style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("Lady Gaga, Bruno Mars", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
              }
            }

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
              Button(
                onClick = { if (trendingTracks.isNotEmpty()) onSongClick(trendingTracks[0]) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(24.dp)
              ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Play")
              }
              Button(
                onClick = { if (trendingTracks.isNotEmpty()) onSongClick(trendingTracks[0]) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
              ) {
                Icon(Icons.Rounded.Download, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Download Song")
              }
            }
          }
        }
      }

      item {
        Column {
          Text("Popular Songs First · បទចម្រៀង", style = MaterialTheme.typography.titleLarge.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onBackground)
          Spacer(modifier = Modifier.height(12.dp))
          trendingTracks.forEachIndexed { index, song ->
            SongDownloadRow(song = song, index = index + 1, onSongClick = { onSongClick(song) }, onFavoriteClick = { onFavoriteClick(song) })
          }
        }
      }
    }
  }
}
