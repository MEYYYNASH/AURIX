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
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.Song
import com.example.ui.theme.HeartRed

@Composable
fun HomeScreen(
  featuredTracks: List<Song>,
  isLoadingFeatured: Boolean,
  featuredError: String?,
  favoritesCount: Int,
  localSongsCount: Int,
  recentlyPlayed: List<Song>,
  currentSong: Song?,
  isPlaying: Boolean,
  darkTheme: Boolean,
  reducedMotion: Boolean,
  onRetryFeatured: () -> Unit,
  onOpenSearch: () -> Unit,
  onOpenLibraryTab: (Int) -> Unit,
  onSongClick: (Song, List<Song>) -> Unit,
  onFavoriteClick: (Song) -> Unit
) {
  LazyColumn(
    modifier = Modifier
      .fillMaxSize()
      .padding(horizontal = 20.dp),
    verticalArrangement = Arrangement.spacedBy(20.dp),
    contentPadding = PaddingValues(
      top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 16.dp,
      bottom = if (currentSong != null) 195.dp else 125.dp
    )
  ) {
    // Minimal Brand Header
    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(
          modifier = Modifier.weight(1f).padding(end = 12.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          AurixBrandLogo(
            size = 40.dp,
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
              text = "Library & Featured",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
          }
        }

        val iconBg = if (darkTheme) Color.White.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.85f)
        val iconBorder = if (darkTheme) Color.White.copy(alpha = 0.18f) else Color.White
        IconButton(
          onClick = onOpenSearch,
          modifier = Modifier
            .minimumInteractiveComponentSize()
            .size(44.dp)
            .clip(CircleShape)
            .background(iconBg)
            .border(1.dp, iconBorder, CircleShape)
            .testTag("home_open_search_button")
        ) {
          Icon(
            imageVector = Icons.Rounded.Search,
            contentDescription = "Search iTunes",
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(20.dp)
          )
        }
      }
    }

    // Minimal Library Shortcuts (Local Songs & Favorites)
    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        HomeLibraryTile(
          title = "My Songs",
          subtitle = if (localSongsCount == 1) "1 track" else "$localSongsCount tracks",
          icon = Icons.Rounded.FolderOpen,
          iconTint = MaterialTheme.colorScheme.primary,
          darkTheme = darkTheme,
          modifier = Modifier.weight(1f).testTag("home_library_local_tile"),
          onClick = { onOpenLibraryTab(0) }
        )
        HomeLibraryTile(
          title = "Favorites",
          subtitle = if (favoritesCount == 1) "1 saved" else "$favoritesCount saved",
          icon = Icons.Rounded.FavoriteBorder,
          iconTint = HeartRed,
          darkTheme = darkTheme,
          modifier = Modifier.weight(1f).testTag("home_library_favorites_tile"),
          onClick = { onOpenLibraryTab(1) }
        )
      }
    }

    // Recently Played Shelf (shown only when user has listening history)
    if (recentlyPlayed.isNotEmpty()) {
      item {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              text = "Recent",
              style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
              color = MaterialTheme.colorScheme.onBackground
            )
            TextButton(onClick = { onOpenLibraryTab(2) }) {
              Text("See all", color = MaterialTheme.colorScheme.primary)
            }
          }

          Row(
            modifier = Modifier
              .fillMaxWidth()
              .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
          ) {
            recentlyPlayed.take(6).forEach { recent ->
              GlassCard(
                darkTheme = darkTheme,
                cornerRadius = 20.dp,
                modifier = Modifier
                  .width(136.dp)
                  .clickable { onSongClick(recent, recentlyPlayed) }
              ) {
                Column(modifier = Modifier.padding(10.dp)) {
                  SongArtwork(
                    song = recent,
                    modifier = Modifier.fillMaxWidth().height(110.dp),
                    cornerRadius = 14.dp,
                    iconSize = 26.dp
                  )
                  Spacer(modifier = Modifier.height(8.dp))
                  Text(
                    text = recent.title,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
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

    // Featured Section inside a cohesive Liquid Glass Card (Real iTunes 30s Previews, zero demo tracks)
    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = "Featured",
          style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
          color = MaterialTheme.colorScheme.onBackground
        )
        TextButton(onClick = onOpenSearch) {
          Text("Search", color = MaterialTheme.colorScheme.primary)
        }
      }
    }

    when {
      isLoadingFeatured && featuredTracks.isEmpty() -> {
        item {
          GlassCard(
            darkTheme = darkTheme,
            cornerRadius = 24.dp,
            modifier = Modifier.fillMaxWidth()
          ) {
            Box(
              modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 34.dp),
              contentAlignment = Alignment.Center
            ) {
              CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
          }
        }
      }
      featuredTracks.isEmpty() -> {
        item {
          GlassCard(
            darkTheme = darkTheme,
            cornerRadius = 24.dp,
            modifier = Modifier.fillMaxWidth()
          ) {
            Column(
              modifier = Modifier.fillMaxWidth().padding(22.dp),
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
              Text(
                text = featuredError ?: "No featured tracks available right now.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
              OutlinedButton(
                onClick = onRetryFeatured,
                shape = CircleShape
              ) {
                Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Retry")
              }
            }
          }
        }
      }
      else -> {
        item {
          val displayed = featuredTracks.take(10)
          GlassCard(
            darkTheme = darkTheme,
            cornerRadius = 26.dp,
            modifier = Modifier.fillMaxWidth()
          ) {
            Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
              displayed.forEachIndexed { idx, song ->
                SongRow(
                  song = song,
                  isPlaying = isPlaying,
                  isCurrent = currentSong?.id == song.id,
                  reducedMotion = reducedMotion,
                  onSongClick = { onSongClick(song, featuredTracks) },
                  onFavoriteClick = { onFavoriteClick(song) }
                )
                if (idx < displayed.lastIndex) {
                  HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
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

@Composable
private fun HomeLibraryTile(
  title: String,
  subtitle: String,
  icon: ImageVector,
  iconTint: Color,
  darkTheme: Boolean,
  modifier: Modifier = Modifier,
  onClick: () -> Unit
) {
  GlassCard(
    darkTheme = darkTheme,
    cornerRadius = 22.dp,
    modifier = modifier.clickable { onClick() }
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 14.dp, vertical = 14.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      Box(
        modifier = Modifier
          .size(38.dp)
          .clip(RoundedCornerShape(12.dp))
          .background(iconTint.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          tint = iconTint,
          modifier = Modifier.size(19.dp)
        )
      }
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = title,
          style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
          color = MaterialTheme.colorScheme.onSurface,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
        Text(
          text = subtitle,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
      }
    }
  }
}
