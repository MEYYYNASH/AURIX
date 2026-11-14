package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.Song

@Composable
fun HomeScreen(
  featuredTracks: List<Song>,
  isLoadingFeatured: Boolean,
  featuredError: String?,
  recentlyPlayed: List<Song>,
  currentSong: Song?,
  isPlaying: Boolean,
  darkTheme: Boolean,
  reducedMotion: Boolean,
  songDownloadProgress: Map<String, Float>,
  downloadedSongIds: Set<String>,
  onRetryFeatured: () -> Unit,
  onOpenSearch: (String?) -> Unit,
  onOpenLibrary: () -> Unit,
  onOpenNowPlaying: () -> Unit,
  onPlayPauseClick: () -> Unit,
  onSongClick: (Song, List<Song>) -> Unit,
  onFavoriteClick: (Song) -> Unit,
  onDownloadClick: (Song) -> Unit
) {
  val genres = listOf(
    "Top Hits" to "top hits 2026",
    "Khmer" to "khmer song",
    "Pop" to "pop hits",
    "R&B" to "r&b soul",
    "Lo-Fi" to "lofi beats",
    "Electronic" to "electronic dance"
  )

  LazyColumn(
    modifier = Modifier
      .fillMaxSize()
      .padding(horizontal = 16.dp),
    verticalArrangement = Arrangement.spacedBy(18.dp),
    contentPadding = PaddingValues(
      top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 14.dp,
      bottom = if (currentSong != null) 195.dp else 125.dp
    )
  ) {
    // Clean Brand Header
    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(
          modifier = Modifier.weight(1f).padding(end = 8.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          AurixBrandLogo(
            size = 42.dp,
            cornerRadius = 13.dp,
            animated = true,
            reducedMotion = reducedMotion
          )
          Column {
            Text(
              text = "AURIX",
              style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                letterSpacing = 0.6.sp
              ),
              color = MaterialTheme.colorScheme.onBackground,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
            Text(
              text = "iTunes 30s Previews & Library",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
          }
        }

        val pillBg = if (darkTheme) Color.White.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.85f)
        val pillBorder = if (darkTheme) Color.White.copy(alpha = 0.18f) else Color.White
        Row(
          modifier = Modifier
            .clip(CircleShape)
            .background(pillBg)
            .border(1.dp, pillBorder, CircleShape)
            .clickable { onOpenSearch(null) }
            .padding(horizontal = 14.dp, vertical = 9.dp)
            .testTag("home_open_search_button"),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          Icon(
            imageVector = Icons.Rounded.Search,
            contentDescription = "Search iTunes 30s",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(17.dp)
          )
          Text(
            text = "Search",
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface
          )
        }
      }
    }

    // Clean Hero Card (Currently Playing OR Discovery Launch Card)
    item {
      GlassCard(
        darkTheme = darkTheme,
        cornerRadius = 26.dp,
        modifier = Modifier.fillMaxWidth()
      ) {
        if (currentSong != null) {
          Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .clickable { onOpenNowPlaying() },
              verticalAlignment = Alignment.CenterVertically
            ) {
              SongArtwork(
                song = currentSong,
                modifier = Modifier.size(72.dp),
                cornerRadius = 18.dp,
                iconSize = 30.dp
              )
              Spacer(modifier = Modifier.width(14.dp))
              Column(modifier = Modifier.weight(1f)) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                  EqualizerBars(
                    isPlaying = isPlaying,
                    color = MaterialTheme.colorScheme.primary,
                    reducedMotion = reducedMotion,
                    maxHeight = 12.dp
                  )
                  Text(
                    text = if (isPlaying) "NOW PLAYING" else "PAUSED",
                    style = MaterialTheme.typography.labelSmall.copy(
                      fontWeight = FontWeight.Bold,
                      letterSpacing = 0.8.sp
                    ),
                    color = MaterialTheme.colorScheme.primary
                  )
                  if (currentSong.source == "iTunes 30s") {
                    Text(
                      text = "· 30s",
                      style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                      color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                  }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                  text = currentSong.title,
                  style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                  color = MaterialTheme.colorScheme.onSurface,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis
                )
                Text(
                  text = currentSong.artist,
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis
                )
              }
            }

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
              Button(
                onClick = onPlayPauseClick,
                modifier = Modifier.weight(1f).height(44.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
              ) {
                Icon(
                  imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                  contentDescription = null,
                  modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                  text = if (isPlaying) "Pause" else "Resume",
                  style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
                )
              }
              OutlinedButton(
                onClick = { onFavoriteClick(currentSong) },
                modifier = Modifier.weight(1f).height(44.dp),
                shape = RoundedCornerShape(16.dp)
              ) {
                Icon(
                  imageVector = if (currentSong.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                  contentDescription = null,
                  tint = if (currentSong.isFavorite) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                  modifier = Modifier.size(17.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                  text = if (currentSong.isFavorite) "Saved" else "Favorite",
                  style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                  color = MaterialTheme.colorScheme.onSurface
                )
              }
            }
          }
        } else {
          Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
              Box(
                modifier = Modifier
                  .size(40.dp)
                  .clip(RoundedCornerShape(12.dp))
                  .background(
                    Brush.linearGradient(
                      colors = listOf(
                        MaterialTheme.colorScheme.primary,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)
                      )
                    )
                  ),
                contentAlignment = Alignment.Center
              ) {
                Icon(
                  imageVector = Icons.Rounded.MusicNote,
                  contentDescription = null,
                  tint = Color.White,
                  modifier = Modifier.size(22.dp)
                )
              }
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = "Discover 30s Music Previews",
                  style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                  color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                  text = "Search iTunes tracks or play audio from your device",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
            }

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
              Button(
                onClick = { onOpenSearch(null) },
                modifier = Modifier.weight(1f).height(44.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
              ) {
                Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(17.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Search iTunes", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold))
              }
              OutlinedButton(
                onClick = onOpenLibrary,
                modifier = Modifier.weight(1f).height(44.dp),
                shape = RoundedCornerShape(16.dp)
              ) {
                Icon(Icons.Rounded.FolderOpen, contentDescription = null, modifier = Modifier.size(17.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("My Library", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold))
              }
            }
          }
        }
      }
    }

    // Quick Genre Pills (jumps straight to Search -> iTunes 30s)
    item {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
          text = "Browse iTunes 30s",
          style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
          color = MaterialTheme.colorScheme.onBackground
        )
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          genres.forEach { (label, query) ->
            val chipBg = if (darkTheme) Color.White.copy(alpha = 0.09f) else Color.White.copy(alpha = 0.85f)
            val chipBorder = if (darkTheme) Color.White.copy(alpha = 0.16f) else Color.White
            Box(
              modifier = Modifier
                .clip(CircleShape)
                .background(chipBg)
                .border(1.dp, chipBorder, CircleShape)
                .clickable { onOpenSearch(query) }
                .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
              Text(
                text = label,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
              )
            }
          }
        }
      }
    }

    // User's Real Recently Played Shelf (only shown when non-empty)
    if (recentlyPlayed.isNotEmpty()) {
      item {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Text(
            text = "Recently Played",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground
          )
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
          ) {
            recentlyPlayed.take(6).forEach { recent ->
              GlassCard(
                darkTheme = darkTheme,
                cornerRadius = 18.dp,
                modifier = Modifier
                  .width(136.dp)
                  .clickable { onSongClick(recent, recentlyPlayed) }
              ) {
                Column(modifier = Modifier.padding(10.dp)) {
                  SongArtwork(
                    song = recent,
                    modifier = Modifier.fillMaxWidth().height(112.dp),
                    cornerRadius = 12.dp,
                    iconSize = 28.dp
                  )
                  Spacer(modifier = Modifier.height(8.dp))
                  Text(
                    text = recent.title,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                  )
                  Text(
                    text = recent.artist,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                  )
                }
              }
            }
          }
        }
      }
    }

    // Live iTunes 30s Previews Header
    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = "Featured 30s Previews",
          style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
          color = MaterialTheme.colorScheme.onBackground
        )
        TextButton(onClick = { onOpenSearch(null) }) {
          Text("Search All", color = MaterialTheme.colorScheme.primary)
        }
      }
    }

    if (isLoadingFeatured && featuredTracks.isEmpty()) {
      item {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 28.dp),
          contentAlignment = Alignment.Center
        ) {
          CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
      }
    } else if (featuredTracks.isEmpty()) {
      item {
        GlassCard(
          darkTheme = darkTheme,
          cornerRadius = 22.dp,
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(
            modifier = Modifier.padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            Text(
              text = featuredError ?: "No previews loaded yet.",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(
              onClick = onRetryFeatured,
              shape = RoundedCornerShape(16.dp)
            ) {
              Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text("Retry iTunes")
            }
          }
        }
      }
    } else {
      featuredTracks.forEachIndexed { index, song ->
        item(key = song.id) {
          SongDownloadRow(
            song = song,
            index = index + 1,
            isPlaying = isPlaying,
            isCurrent = currentSong?.id == song.id,
            reducedMotion = reducedMotion,
            downloadProgress = songDownloadProgress[song.id],
            isDownloaded = song.id in downloadedSongIds,
            onSongClick = { onSongClick(song, featuredTracks) },
            onFavoriteClick = { onFavoriteClick(song) },
            onDownloadClick = { onDownloadClick(song) }
          )
        }
      }
    }
  }
}
