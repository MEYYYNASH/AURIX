package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.R
import com.example.model.Song
import com.example.ui.theme.HeartRed
import com.example.viewmodel.DailyFeedCategory

private enum class DailyCardState { LOADING, ERROR, LOADED }

@Composable
fun HomeScreen(
  featuredTracks: List<Song>,
  isLoadingFeatured: Boolean,
  featuredError: String?,
  selectedFeed: DailyFeedCategory,
  dailyUpdatedLabel: String,
  dailyUpdatedJustNow: Boolean = false,
  favoritesCount: Int,
  localSongsCount: Int,
  recentlyPlayed: List<Song>,
  currentSong: Song?,
  isPlaying: Boolean,
  darkTheme: Boolean,
  reducedMotion: Boolean,
  verifiedLyricsIds: Set<String> = emptySet(),
  onCheckLyrics: (Song) -> Unit = {},
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
              val statusText = if (dailyUpdatedJustNow) {
                stringResource(id = R.string.home_updated_just_now)
              } else {
                dailyUpdatedLabel
              }
              Text(
                text = statusText,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                color = AurixTealAccent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("home_header_status_label")
              )
            }
          }

          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val iconBg = if (darkTheme) Color(0xFF151F30) else Color.White.copy(alpha = 0.85f)
            val iconBorder = if (darkTheme) Color(0xFF273348) else Color.White

            IconButton(
              onClick = {
                if (!isLoadingFeatured) {
                  onRefreshDailyFeed()
                }
              },
              enabled = !isLoadingFeatured,
              modifier = Modifier
                .minimumInteractiveComponentSize()
                .size(44.dp)
                .clip(CircleShape)
                .background(iconBg)
                .border(1.dp, iconBorder, CircleShape)
                .testTag("home_refresh_feed_button")
            ) {
              if (isLoadingFeatured) {
                OrbitLoader(
                  size = 30.dp,
                  cardColor = iconBg,
                  ringColor = if (darkTheme) OrbitRingDefault else Color(0xFF3A3C48),
                  planetColor = if (darkTheme) OrbitPlanetDefault else Color.White,
                  reducedMotion = reducedMotion
                )
              } else {
                Icon(
                  imageVector = Icons.Rounded.Refresh,
                  contentDescription = stringResource(id = R.string.home_refresh_cd),
                  tint = MaterialTheme.colorScheme.onSurface,
                  modifier = Modifier.size(20.dp)
                )
              }
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
                contentDescription = stringResource(id = R.string.home_search_cd),
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
            title = stringResource(id = R.string.my_songs),
            countLabel = if (localSongsCount == 1) "1" else "$localSongsCount",
            icon = Icons.Rounded.FolderOpen,
            iconTint = MaterialTheme.colorScheme.primary,
            darkTheme = darkTheme,
            modifier = Modifier.weight(1f).testTag("home_library_local_tile"),
            onClick = { onOpenLibraryTab(0) }
          )
          HomeLibraryPill(
            title = stringResource(id = R.string.home_favorites),
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
                text = stringResource(id = R.string.home_jump_back_in),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, lineHeight = 24.sp),
                color = MaterialTheme.colorScheme.onBackground
              )
              Text(
                text = stringResource(id = R.string.home_history),
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold, lineHeight = 20.sp),
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
                LaunchedEffect(recent.id) {
                  onCheckLyrics(recent)
                }
                val isRecentPlaying = currentSong?.id == recent.id
                val recentHasLyrics = recent.id in verifiedLyricsIds
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
                      if (recentHasLyrics) {
                        LyricsTag(
                          visible = true,
                          size = LyricsTagSize.Mini,
                          darkTheme = darkTheme,
                          reducedMotion = reducedMotion,
                          modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                        )
                      }
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

      // Daily Feed Editorial Header & Category Filter Pills
      item {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column(
              modifier = Modifier.weight(1f),
              verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
              val feedLabel = feedLocalizedLabel(selectedFeed)
              val feedSubtitle = feedLocalizedSubtitle(selectedFeed)
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                Text(
                  text = feedLabel,
                  style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 22.sp,
                    lineHeight = 30.sp
                  ),
                  color = MaterialTheme.colorScheme.onBackground,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis
                )
                Surface(
                  shape = CircleShape,
                  color = AurixTealAccent.copy(alpha = 0.14f)
                ) {
                  Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                  ) {
                    Box(
                      modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(AurixTealAccent)
                    )
                    Text(
                      text = stringResource(id = R.string.home_daily_badge),
                      style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        lineHeight = 14.sp
                      ),
                      color = AurixTealAccent
                    )
                  }
                }
              }
              Text(
                text = feedSubtitle,
                style = MaterialTheme.typography.bodySmall.copy(
                  fontSize = 13.sp,
                  lineHeight = 19.sp
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
            }

            if (!isLoadingFeatured && featuredTracks.isNotEmpty()) {
              Surface(
                shape = CircleShape,
                color = if (darkTheme) Color(0xFF151F30) else Color.White.copy(alpha = 0.85f),
                border = androidx.compose.foundation.BorderStroke(
                  1.dp,
                  if (darkTheme) Color(0xFF273348) else Color.White
                )
              ) {
                Row(
                  modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                  Icon(
                    imageVector = Icons.Rounded.QueueMusic,
                    contentDescription = null,
                    tint = AurixTealAccent,
                    modifier = Modifier.size(14.dp)
                  )
                  Text(
                    text = featuredTracks.take(20).size.toString(),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                  )
                }
              }
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
              val chipBg by animateColorAsState(
                targetValue = when {
                  selected -> AurixTealAccent
                  darkTheme -> Color(0xFF151F30)
                  else -> Color.White.copy(alpha = 0.84f)
                },
                animationSpec = if (reducedMotion) snap() else tween(200),
                label = "chip_bg_${category.id}"
              )
              val chipBorder by animateColorAsState(
                targetValue = when {
                  selected -> AurixTealAccent
                  darkTheme -> Color(0xFF273348)
                  else -> Color.White
                },
                animationSpec = if (reducedMotion) snap() else tween(200),
                label = "chip_border_${category.id}"
              )
              val chipTextColor = if (selected) Color(0xFF06201C) else MaterialTheme.colorScheme.onSurface

              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                  .heightIn(min = 40.dp)
                  .clip(CircleShape)
                  .background(chipBg)
                  .border(1.dp, chipBorder, CircleShape)
                  .clickable { onSelectFeed(category) }
                  .testTag("home_feed_chip_${category.id}")
                  .padding(horizontal = 14.dp, vertical = 8.dp)
              ) {
                Icon(
                  imageVector = feedCategoryIcon(category),
                  contentDescription = null,
                  tint = if (selected) Color(0xFF06201C) else AurixTealAccent,
                  modifier = Modifier.size(15.dp)
                )
                Text(
                  text = feedLocalizedLabel(category),
                  style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                  ),
                  color = chipTextColor
                )
              }
            }
          }
        }
      }

      // Unified Daily Recommendations Card (zero card-inside-card, fixed min height during Loading/Error)
      item {
        val cardBgColor = if (darkTheme) Color(0xFF151F30) else Color.White.copy(alpha = 0.92f)
        val cardBorderColor = if (darkTheme) Color(0xFF273348) else Color.White
        val cardShape = RoundedCornerShape(28.dp)
        val cardState = when {
          isLoadingFeatured -> DailyCardState.LOADING
          featuredTracks.isEmpty() -> DailyCardState.ERROR
          else -> DailyCardState.LOADED
        }

        Box(
          modifier = Modifier
            .fillMaxWidth()
            .then(
              if (cardState == DailyCardState.LOADED) {
                Modifier.heightIn(min = 420.dp)
              } else {
                Modifier.height(420.dp)
              }
            )
            .clip(cardShape)
            .background(cardBgColor)
            .border(1.dp, cardBorderColor, cardShape)
            .semantics { liveRegion = LiveRegionMode.Polite }
            .testTag("home_daily_feed_card")
        ) {
          AnimatedContent(
            targetState = cardState,
            modifier = Modifier.fillMaxWidth(),
            transitionSpec = {
              if (reducedMotion) {
                fadeIn(snap()) togetherWith fadeOut(snap())
              } else if (targetState == DailyCardState.LOADED) {
                (fadeIn(tween(350)) + slideInVertically(tween(350)) { it / 16 }) togetherWith fadeOut(tween(180))
              } else {
                fadeIn(tween(220)) togetherWith fadeOut(tween(160))
              }
            },
            label = "daily_card_state_transition"
          ) { state ->
            when (state) {
              DailyCardState.ERROR -> {
                Column(
                  modifier = Modifier
                    .fillMaxWidth()
                    .height(420.dp)
                    .padding(horizontal = 24.dp),
                  horizontalAlignment = Alignment.CenterHorizontally,
                  verticalArrangement = Arrangement.Center
                ) {
                  Box(
                    modifier = Modifier
                      .size(58.dp)
                      .clip(CircleShape)
                      .background(AurixTealAccent.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                  ) {
                    Icon(
                      imageVector = Icons.Rounded.MusicNote,
                      contentDescription = null,
                      tint = AurixTealAccent,
                      modifier = Modifier.size(30.dp)
                    )
                  }
                  Spacer(modifier = Modifier.height(14.dp))
                  Text(
                    text = featuredError ?: stringResource(id = R.string.home_refresh_error),
                    style = MaterialTheme.typography.bodyMedium.copy(
                      fontWeight = FontWeight.Medium,
                      lineHeight = 22.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                  )
                  Spacer(modifier = Modifier.height(18.dp))
                  Button(
                    onClick = {
                      if (!isLoadingFeatured) {
                        onRefreshDailyFeed()
                      }
                    },
                    enabled = !isLoadingFeatured,
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                      containerColor = AurixTealAccent,
                      contentColor = Color(0xFF06201C)
                    ),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
                    modifier = Modifier
                      .defaultMinSize(minHeight = 48.dp)
                      .testTag("home_daily_retry_button")
                  ) {
                    Icon(
                      imageVector = Icons.Rounded.Refresh,
                      contentDescription = null,
                      modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                      text = stringResource(id = R.string.home_retry),
                      style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.Bold,
                        lineHeight = 20.sp
                      )
                    )
                  }
                }
              }

              DailyCardState.LOADING -> {
                Column(
                  modifier = Modifier
                    .fillMaxWidth()
                    .height(420.dp)
                    .padding(horizontal = 24.dp),
                  horizontalAlignment = Alignment.CenterHorizontally,
                  verticalArrangement = Arrangement.Center
                ) {
                  OrbitLoader(
                    size = 84.dp,
                    cardColor = cardBgColor,
                    ringColor = if (darkTheme) OrbitRingDefault else Color(0xFF3A3C48),
                    planetColor = if (darkTheme) OrbitPlanetDefault else Color.White,
                    reducedMotion = reducedMotion,
                    modifier = Modifier.testTag("home_daily_orbit_loader")
                  )
                  Spacer(modifier = Modifier.height(16.dp))
                  Text(
                    text = stringResource(id = R.string.home_refreshing_title),
                    style = MaterialTheme.typography.titleMedium.copy(
                      fontWeight = FontWeight.Bold,
                      lineHeight = 24.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                  )
                  Spacer(modifier = Modifier.height(4.dp))
                  Text(
                    text = stringResource(id = R.string.home_refreshing_subtitle),
                    style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                  )
                }
              }

              DailyCardState.LOADED -> {
                val heroSong = featuredTracks.first()
                val restTracks = featuredTracks.drop(1).take(19)
                Column(modifier = Modifier.fillMaxWidth()) {
                  IntegratedHeroSpotlight(
                    song = heroSong,
                    badgeLabel = "#1 ${selectedFeed.badgeText.uppercase()}",
                    isCurrent = currentSong?.id == heroSong.id,
                    isPlaying = isPlaying,
                    darkTheme = darkTheme,
                    reducedMotion = reducedMotion,
                    hasLyrics = heroSong.id in verifiedLyricsIds,
                    onCheckLyrics = onCheckLyrics,
                    onPlayClick = { onSongClick(heroSong, featuredTracks) },
                    onFavoriteClick = { onFavoriteClick(heroSong) }
                  )

                  if (restTracks.isNotEmpty()) {
                    HorizontalDivider(
                      color = if (darkTheme) Color(0xFF273348).copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                    )
                    Column(
                      modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                      restTracks.forEachIndexed { idx, song ->
                        SongRow(
                          song = song,
                          isPlaying = isPlaying,
                          isCurrent = currentSong?.id == song.id,
                          reducedMotion = reducedMotion,
                          darkTheme = darkTheme,
                          hasLyrics = song.id in verifiedLyricsIds,
                          rank = idx + 2,
                          showSourceBadge = false,
                          onCheckLyrics = onCheckLyrics,
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
private fun IntegratedHeroSpotlight(
  song: Song,
  badgeLabel: String,
  isCurrent: Boolean,
  isPlaying: Boolean,
  darkTheme: Boolean,
  reducedMotion: Boolean,
  hasLyrics: Boolean = false,
  onCheckLyrics: (Song) -> Unit = {},
  onPlayClick: () -> Unit,
  onFavoriteClick: () -> Unit
) {
  LaunchedEffect(song.id) {
    onCheckLyrics(song)
  }
  val (c1, c2) = rememberArtworkColors(song.artworkUrl, song.title)

  Box(
    modifier = Modifier
      .fillMaxWidth()
      .clickable { onPlayClick() }
      .testTag("home_hero_spotlight")
  ) {
    if (song.artworkUrl.isNotBlank()) {
      AsyncImage(
        model = song.artworkUrl,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
          .matchParentSize()
          .alpha(if (darkTheme) 0.24f else 0.14f)
          .blur(38.dp)
      )
    }
    Box(
      modifier = Modifier
        .matchParentSize()
        .background(
          Brush.horizontalGradient(
            colors = listOf(
              c1.copy(alpha = if (darkTheme) 0.22f else 0.12f),
              c2.copy(alpha = if (darkTheme) 0.10f else 0.05f),
              Color.Transparent
            )
          )
        )
    )

    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
      Box(
        modifier = Modifier
          .size(84.dp)
          .clip(RoundedCornerShape(18.dp))
          .border(1.dp, Color.White.copy(alpha = 0.26f), RoundedCornerShape(18.dp))
      ) {
        SongArtwork(
          song = song,
          modifier = Modifier.fillMaxSize(),
          cornerRadius = 18.dp,
          iconSize = 30.dp
        )
      }

      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Surface(
          shape = CircleShape,
          color = AurixTealAccent.copy(alpha = 0.16f)
        ) {
          Text(
            text = badgeLabel,
            style = MaterialTheme.typography.labelSmall.copy(
              fontWeight = FontWeight.ExtraBold,
              fontSize = 10.sp
            ),
            color = AurixTealAccent,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
          )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(
            text = song.title,
            style = MaterialTheme.typography.titleMedium.copy(
              fontWeight = FontWeight.ExtraBold,
              lineHeight = 22.sp
            ),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
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
          text = song.artist,
          style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
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
            color = AurixTealAccent,
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
                  color = Color(0xFF06201C),
                  reducedMotion = reducedMotion,
                  barWidth = 2.dp,
                  maxHeight = 11.dp
                )
              } else {
                Icon(
                  imageVector = Icons.Rounded.PlayArrow,
                  contentDescription = "Play",
                  tint = Color(0xFF06201C),
                  modifier = Modifier.size(16.dp)
                )
              }
              Text(
                text = if (isCurrent && isPlaying) {
                  stringResource(id = R.string.home_playing_now)
                } else {
                  stringResource(id = R.string.home_play_now)
                },
                style = MaterialTheme.typography.labelMedium.copy(
                  fontWeight = FontWeight.Bold,
                  lineHeight = 17.sp
                ),
                color = Color(0xFF06201C)
              )
            }
          }

          IconButton(
            onClick = onFavoriteClick,
            modifier = Modifier
              .minimumInteractiveComponentSize()
              .size(40.dp)
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

private fun feedCategoryIcon(category: DailyFeedCategory): ImageVector {
  return when (category) {
    DailyFeedCategory.TIKTOK_VIRAL -> Icons.Rounded.Whatshot
    DailyFeedCategory.DAILY_CHARTS -> Icons.Rounded.Public
    DailyFeedCategory.PHONK_EDITS -> Icons.Rounded.Bolt
    DailyFeedCategory.KHMER_ASIA -> Icons.Rounded.GraphicEq
  }
}

@Composable
private fun feedLocalizedLabel(category: DailyFeedCategory): String {
  return when (category) {
    DailyFeedCategory.TIKTOK_VIRAL -> stringResource(id = R.string.home_feed_tiktok)
    DailyFeedCategory.DAILY_CHARTS -> stringResource(id = R.string.home_feed_charts)
    DailyFeedCategory.PHONK_EDITS -> stringResource(id = R.string.home_feed_phonk)
    DailyFeedCategory.KHMER_ASIA -> stringResource(id = R.string.home_feed_khmer)
  }
}

@Composable
private fun feedLocalizedSubtitle(category: DailyFeedCategory): String {
  return when (category) {
    DailyFeedCategory.TIKTOK_VIRAL -> stringResource(id = R.string.home_feed_tiktok_sub)
    DailyFeedCategory.DAILY_CHARTS -> stringResource(id = R.string.home_feed_charts_sub)
    DailyFeedCategory.PHONK_EDITS -> stringResource(id = R.string.home_feed_phonk_sub)
    DailyFeedCategory.KHMER_ASIA -> stringResource(id = R.string.home_feed_khmer_sub)
  }
}
