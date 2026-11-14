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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.model.Song
import com.example.ui.theme.HeartRed
import com.example.viewmodel.DailyFeedCategory

@Composable
fun HomeScreen(
  featuredTracks: List<Song>,
  isLoadingFeatured: Boolean,
  featuredError: String?,
  selectedFeed: DailyFeedCategory,
  dailyUpdatedLabel: String,
  favoritesCount: Int,
  localSongsCount: Int,
  recentlyPlayed: List<Song>,
  currentSong: Song?,
  isPlaying: Boolean,
  darkTheme: Boolean,
  reducedMotion: Boolean,
  onSelectFeed: (DailyFeedCategory) -> Unit,
  onRefreshDailyFeed: () -> Unit,
  onOpenSearch: () -> Unit,
  onOpenLibraryTab: (Int) -> Unit,
  onSongClick: (Song, List<Song>) -> Unit,
  onFavoriteClick: (Song) -> Unit
) {
  Box(modifier = Modifier.fillMaxSize()) {
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .padding(horizontal = 20.dp),
      verticalArrangement = Arrangement.spacedBy(18.dp),
      contentPadding = PaddingValues(
        top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 14.dp,
        bottom = if (currentSong != null) 205.dp else 135.dp
      )
    ) {
      // Minimal Editorial Brand Header + Refresh & Search Controls
      item {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(
            modifier = Modifier.weight(1f).padding(end = 10.dp),
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
                  fontWeight = FontWeight.ExtraBold,
                  fontSize = 22.sp,
                  letterSpacing = 0.5.sp
                ),
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
              Text(
                text = dailyUpdatedLabel,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
            }
          }

          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val iconBg = if (darkTheme) Color.White.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.85f)
            val iconBorder = if (darkTheme) Color.White.copy(alpha = 0.18f) else Color.White

            IconButton(
              onClick = onRefreshDailyFeed,
              modifier = Modifier
                .minimumInteractiveComponentSize()
                .size(44.dp)
                .clip(CircleShape)
                .background(iconBg)
                .border(1.dp, iconBorder, CircleShape)
                .testTag("home_refresh_feed_button")
            ) {
              Icon(
                imageVector = Icons.Rounded.Refresh,
                contentDescription = "Refresh daily recommendations",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(20.dp)
              )
            }

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
                contentDescription = "Search music",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(20.dp)
              )
            }
          }
        }
      }

      // Slim Library Quick Access Pills
      item {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          HomeLibraryPill(
            title = "My Songs",
            countLabel = if (localSongsCount == 1) "1" else "$localSongsCount",
            icon = Icons.Rounded.FolderOpen,
            iconTint = MaterialTheme.colorScheme.primary,
            darkTheme = darkTheme,
            modifier = Modifier.weight(1f).testTag("home_library_local_tile"),
            onClick = { onOpenLibraryTab(0) }
          )
          HomeLibraryPill(
            title = "Favorites",
            countLabel = if (favoritesCount == 1) "1" else "$favoritesCount",
            icon = Icons.Rounded.Favorite,
            iconTint = HeartRed,
            darkTheme = darkTheme,
            modifier = Modifier.weight(1f).testTag("home_library_favorites_tile"),
            onClick = { onOpenLibraryTab(1) }
          )
        }
      }

      // Recently Played Shelf (Deduplicated & Live Playing Ring)
      if (recentlyPlayed.isNotEmpty()) {
        item {
          Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text(
                text = "Jump Back In",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground
              )
              Text(
                text = "History",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                  .clip(CircleShape)
                  .clickable { onOpenLibraryTab(2) }
                  .padding(horizontal = 8.dp, vertical = 4.dp)
              )
            }

            Row(
              modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
              horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
              recentlyPlayed.take(6).forEach { recent ->
                val isRecentPlaying = currentSong?.id == recent.id
                GlassCard(
                  darkTheme = darkTheme,
                  cornerRadius = 20.dp,
                  modifier = Modifier
                    .width(126.dp)
                    .then(
                      if (isRecentPlaying) Modifier.border(
                        1.5.dp,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.75f),
                        RoundedCornerShape(20.dp)
                      ) else Modifier
                    )
                    .clickable { onSongClick(recent, recentlyPlayed) }
                ) {
                  Column(modifier = Modifier.padding(9.dp)) {
                    Box {
                      SongArtwork(
                        song = recent,
                        modifier = Modifier.fillMaxWidth().height(106.dp),
                        cornerRadius = 14.dp,
                        iconSize = 26.dp
                      )
                      if (isRecentPlaying) {
                        Box(
                          modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(6.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.65f))
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                        ) {
                          EqualizerBars(
                            isPlaying = isPlaying,
                            color = MaterialTheme.colorScheme.primary,
                            reducedMotion = reducedMotion,
                            barWidth = 2.5.dp,
                            maxHeight = 10.dp
                          )
                        }
                      }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                      text = recent.title,
                      style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                      color = if (isRecentPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
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

      // Daily Feed Header & Viral Category Filter Chips
      item {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                Text(
                  text = selectedFeed.label,
                  style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold, fontSize = 20.sp),
                  color = MaterialTheme.colorScheme.onBackground
                )
                Surface(
                  shape = CircleShape,
                  color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                ) {
                  Text(
                    text = "DAILY",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                  )
                }
              }
              Text(
                text = selectedFeed.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          }

          Row(
            modifier = Modifier
              .fillMaxWidth()
              .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            DailyFeedCategory.entries.forEach { category ->
              val selected = category == selectedFeed
              val chipBg = if (selected) {
                MaterialTheme.colorScheme.primary
              } else if (darkTheme) {
                Color.White.copy(alpha = 0.08f)
              } else {
                Color.White.copy(alpha = 0.78f)
              }
              val chipBorder = if (selected) {
                MaterialTheme.colorScheme.primary
              } else if (darkTheme) {
                Color.White.copy(alpha = 0.16f)
              } else {
                Color.White
              }

              Box(
                modifier = Modifier
                  .clip(CircleShape)
                  .background(chipBg)
                  .border(1.dp, chipBorder, CircleShape)
                  .clickable { onSelectFeed(category) }
                  .testTag("home_feed_chip_${category.id}")
                  .padding(horizontal = 14.dp, vertical = 8.dp)
              ) {
                Text(
                  text = category.label,
                  style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold
                  ),
                  color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface
                )
              }
            }
          }
        }
      }

      // Main Daily Recommendations Content (#1 Spotlight Hero + Ranked Editorial Chart)
      when {
        isLoadingFeatured && featuredTracks.isEmpty() -> {
          item {
            GlassCard(
              darkTheme = darkTheme,
              cornerRadius = 26.dp,
              modifier = Modifier.fillMaxWidth()
            ) {
              Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 38.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
              ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                Text(
                  text = "Fetching today's ${selectedFeed.label} sounds...",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
            }
          }
        }
        featuredTracks.isEmpty() -> {
          item {
            GlassCard(
              darkTheme = darkTheme,
              cornerRadius = 26.dp,
              modifier = Modifier.fillMaxWidth()
            ) {
              Column(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
              ) {
                Icon(
                  imageVector = Icons.Rounded.MusicNote,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.primary,
                  modifier = Modifier.size(30.dp)
                )
                Text(
                  text = featuredError ?: "No daily tracks available right now.",
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(
                  onClick = onRefreshDailyFeed,
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
          val heroSong = featuredTracks.first()
          val restTracks = featuredTracks.drop(1).take(19)

          // #1 Spotlight Hero Card
          item {
            ViralSpotlightCard(
              song = heroSong,
              badgeLabel = "#1 ${selectedFeed.badgeText.uppercase()}",
              isCurrent = currentSong?.id == heroSong.id,
              isPlaying = isPlaying,
              darkTheme = darkTheme,
              reducedMotion = reducedMotion,
              onPlayClick = { onSongClick(heroSong, featuredTracks) },
              onFavoriteClick = { onFavoriteClick(heroSong) }
            )
          }

          // Ranked Viral List Card (#02 .. #20)
          if (restTracks.isNotEmpty()) {
            item {
              GlassCard(
                darkTheme = darkTheme,
                cornerRadius = 26.dp,
                modifier = Modifier.fillMaxWidth()
              ) {
                Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
                  restTracks.forEachIndexed { idx, song ->
                    SongRow(
                      song = song,
                      isPlaying = isPlaying,
                      isCurrent = currentSong?.id == song.id,
                      reducedMotion = reducedMotion,
                      rank = idx + 2,
                      showSourceBadge = false,
                      onSongClick = { onSongClick(song, featuredTracks) },
                      onFavoriteClick = { onFavoriteClick(song) }
                    )
                    if (idx < restTracks.lastIndex) {
                      HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 14.dp),
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

    // Subtle status bar gradient scrim so scrolled cards never collide visually with clock & battery icons
    val statusBg = if (darkTheme) Color(0xFF0A0E1A) else Color(0xFFEAF0FF)
    Box(
      modifier = Modifier
        .align(Alignment.TopCenter)
        .fillMaxWidth()
        .height(WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 18.dp)
        .background(
          Brush.verticalGradient(
            colors = listOf(
              statusBg.copy(alpha = 0.85f),
              statusBg.copy(alpha = 0.35f),
              Color.Transparent
            )
          )
        )
    )
  }
}

@Composable
private fun ViralSpotlightCard(
  song: Song,
  badgeLabel: String,
  isCurrent: Boolean,
  isPlaying: Boolean,
  darkTheme: Boolean,
  reducedMotion: Boolean,
  onPlayClick: () -> Unit,
  onFavoriteClick: () -> Unit
) {
  val (c1, c2) = rememberArtworkColors(song.artworkUrl, song.title)
  val shape = RoundedCornerShape(26.dp)

  Box(
    modifier = Modifier
      .fillMaxWidth()
      .clip(shape)
      .background(if (darkTheme) Color(0xFF172034).copy(alpha = 0.78f) else Color.White.copy(alpha = 0.86f))
      .border(
        width = 1.dp,
        brush = Brush.verticalGradient(
          colors = listOf(Color.White.copy(alpha = if (darkTheme) 0.32f else 0.96f), Color.White.copy(alpha = 0.08f))
        ),
        shape = shape
      )
      .clickable { onPlayClick() }
      .testTag("home_hero_spotlight")
  ) {
    // Soft ambient artwork wash behind the hero card
    if (song.artworkUrl.isNotBlank()) {
      AsyncImage(
        model = song.artworkUrl,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
          .matchParentSize()
          .alpha(if (darkTheme) 0.28f else 0.18f)
          .blur(36.dp)
      )
    }
    Box(
      modifier = Modifier
        .matchParentSize()
        .background(
          Brush.horizontalGradient(
            colors = listOf(
              c1.copy(alpha = if (darkTheme) 0.26f else 0.16f),
              c2.copy(alpha = if (darkTheme) 0.14f else 0.08f),
              Color.Transparent
            )
          )
        )
    )

    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(14.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
      Box(
        modifier = Modifier
          .size(88.dp)
          .clip(RoundedCornerShape(18.dp))
          .border(1.dp, Color.White.copy(alpha = 0.34f), RoundedCornerShape(18.dp))
      ) {
        SongArtwork(
          song = song,
          modifier = Modifier.fillMaxSize(),
          cornerRadius = 18.dp,
          iconSize = 32.dp
        )
      }

      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Surface(
          shape = CircleShape,
          color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
        ) {
          Text(
            text = badgeLabel,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold, fontSize = 10.sp),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
          )
        }

        Text(
          text = song.title,
          style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
          color = MaterialTheme.colorScheme.onSurface,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
        Text(
          text = song.artist,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(2.dp))

        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
              .clip(CircleShape)
              .clickable { onPlayClick() }
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
              if (isCurrent && isPlaying) {
                EqualizerBars(
                  isPlaying = true,
                  color = Color.White,
                  reducedMotion = reducedMotion,
                  barWidth = 2.dp,
                  maxHeight = 11.dp
                )
              } else {
                Icon(
                  imageVector = Icons.Rounded.PlayArrow,
                  contentDescription = "Play",
                  tint = Color.White,
                  modifier = Modifier.size(16.dp)
                )
              }
              Text(
                text = if (isCurrent && isPlaying) "Playing" else "Play Now",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = Color.White
              )
            }
          }

          IconButton(
            onClick = onFavoriteClick,
            modifier = Modifier.size(36.dp)
          ) {
            Icon(
              imageVector = if (song.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
              contentDescription = "Favorite",
              tint = if (song.isFavorite) HeartRed else MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.size(20.dp)
            )
          }
        }
      }
    }
  }
}

@Composable
private fun HomeLibraryPill(
  title: String,
  countLabel: String,
  icon: ImageVector,
  iconTint: Color,
  darkTheme: Boolean,
  modifier: Modifier = Modifier,
  onClick: () -> Unit
) {
  GlassCard(
    darkTheme = darkTheme,
    cornerRadius = 20.dp,
    modifier = modifier.clickable { onClick() }
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 12.dp, vertical = 11.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      Box(
        modifier = Modifier
          .size(32.dp)
          .clip(CircleShape)
          .background(iconTint.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          tint = iconTint,
          modifier = Modifier.size(17.dp)
        )
      }
      Text(
        text = title,
        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f)
      )
      Text(
        text = countLabel,
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
  }
}
