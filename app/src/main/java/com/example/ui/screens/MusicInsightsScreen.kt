package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.TrendingDown
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.R
import com.example.model.FeaturedAlbumItem
import com.example.model.Song
import com.example.recommendation.*

@Composable
fun MusicInsightsScreen(
  musicDna: MusicDnaProfile,
  mostPlayedFilter: MostPlayedFilter,
  mostPlayed: List<RankedTrack>,
  trendingTracks: List<RankedTrack>,
  risingArtists: List<RisingArtist>,
  currentSong: Song?,
  isPlaying: Boolean,
  darkTheme: Boolean,
  reducedMotion: Boolean,
  verifiedLyricsIds: Set<String> = emptySet(),
  onBack: () -> Unit,
  onSelectMostPlayedFilter: (MostPlayedFilter) -> Unit,
  onCheckLyrics: (Song) -> Unit,
  onSongClick: (Song, List<Song>) -> Unit,
  onFavoriteClick: (Song) -> Unit
) {
  var showDnaCard by rememberSaveable { mutableStateOf(true) }
  var showMostPlayedCard by rememberSaveable { mutableStateOf(true) }
  var showTrendingCard by rememberSaveable { mutableStateOf(true) }
  var showRisingArtistsCard by rememberSaveable { mutableStateOf(true) }

  val cardEnter = remember(reducedMotion) {
    if (reducedMotion) fadeIn(snap()) else expandVertically(tween(240)) + fadeIn(tween(200))
  }
  val cardExit = remember(reducedMotion) {
    if (reducedMotion) fadeOut(snap()) else shrinkVertically(tween(200)) + fadeOut(tween(150))
  }

  LazyColumn(
    modifier = Modifier
      .fillMaxSize()
      .padding(horizontal = 20.dp)
      .testTag("music_insights_screen"),
    verticalArrangement = Arrangement.spacedBy(18.dp),
    contentPadding = PaddingValues(
      top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 14.dp,
      bottom = if (currentSong != null) 205.dp else 135.dp
    )
  ) {
    // Top Header
    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        val iconBg = if (darkTheme) Color(0xFF151F30) else Color.White.copy(alpha = 0.85f)
        val iconBorder = if (darkTheme) Color(0xFF273348) else Color.White
        IconButton(
          onClick = onBack,
          modifier = Modifier
            .minimumInteractiveComponentSize()
            .size(44.dp)
            .clip(CircleShape)
            .background(iconBg)
            .border(1.dp, iconBorder, CircleShape)
            .testTag("insights_back_button")
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
            contentDescription = stringResource(id = R.string.insights_back_cd),
            tint = MaterialTheme.colorScheme.onSurface
          )
        }
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = stringResource(id = R.string.insights_title),
            style = MaterialTheme.typography.titleLarge.copy(
              fontWeight = FontWeight.ExtraBold,
              fontSize = 24.sp
            ),
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
          Text(
            text = stringResource(id = R.string.insights_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }
      }
    }

    // 1. Music DNA Card
    item {
      MusicDnaCard(
        profile = musicDna,
        darkTheme = darkTheme,
        reducedMotion = reducedMotion,
        isExpanded = showDnaCard,
        onToggleExpand = { showDnaCard = !showDnaCard }
      )
    }

    // 2. Most Played Card (Week / Month / All time)
    item {
      val queue = mostPlayed.map { it.song }
      GlassCard(
        darkTheme = darkTheme,
        cornerRadius = 24.dp,
        modifier = Modifier
          .fillMaxWidth()
          .testTag("insights_most_played_card")
      ) {
        Column(
          modifier = Modifier.padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          SectionTitleRow(
            title = stringResource(id = R.string.insights_most_played_title),
            subtitle = stringResource(id = R.string.insights_most_played_sub),
            icon = Icons.Rounded.Leaderboard,
            isExpanded = showMostPlayedCard,
            onToggleExpand = { showMostPlayedCard = !showMostPlayedCard },
            darkTheme = darkTheme,
            reducedMotion = reducedMotion,
            toggleTestTag = "insights_toggle_most_played_button"
          )

          AnimatedVisibility(
            visible = showMostPlayedCard,
            enter = cardEnter,
            exit = cardExit
          ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
              GlassSegmentedBar(
                options = MostPlayedFilter.entries.map { it.label to null },
                selectedIndex = MostPlayedFilter.entries.indexOf(mostPlayedFilter).coerceAtLeast(0),
                darkTheme = darkTheme,
                onSelect = { idx -> onSelectMostPlayedFilter(MostPlayedFilter.entries[idx]) },
                testTags = MostPlayedFilter.entries.map { "most_played_filter_${it.name.lowercase()}" }
              )

              if (mostPlayed.isEmpty()) {
                InsightsEmptyHint(
                  message = stringResource(id = R.string.insights_empty_most_played),
                  darkTheme = darkTheme
                )
              } else {
                mostPlayed.take(10).forEachIndexed { idx, entry ->
                  RankedSongEntryRow(
                    entry = entry,
                    trailingBadge = "${entry.playCount}x",
                    direction = null,
                    isCurrent = currentSong?.id == entry.song.id,
                    isPlaying = isPlaying,
                    darkTheme = darkTheme,
                    reducedMotion = reducedMotion,
                    hasLyrics = entry.song.id in verifiedLyricsIds,
                    onCheckLyrics = onCheckLyrics,
                    onSongClick = { onSongClick(entry.song, queue) },
                    onFavoriteClick = { onFavoriteClick(entry.song) }
                  )
                  if (idx < minOf(9, mostPlayed.lastIndex)) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                  }
                }
              }
            }
          }
        }
      }
    }

    // 3. Trending Tracks Card
    item {
      val queue = trendingTracks.map { it.song }
      GlassCard(
        darkTheme = darkTheme,
        cornerRadius = 24.dp,
        modifier = Modifier
          .fillMaxWidth()
          .testTag("insights_trending_card")
      ) {
        Column(
          modifier = Modifier.padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          SectionTitleRow(
            title = stringResource(id = R.string.insights_trending_title),
            subtitle = stringResource(id = R.string.insights_trending_sub),
            icon = Icons.Rounded.Whatshot,
            isExpanded = showTrendingCard,
            onToggleExpand = { showTrendingCard = !showTrendingCard },
            darkTheme = darkTheme,
            reducedMotion = reducedMotion,
            toggleTestTag = "insights_toggle_trending_button"
          )

          AnimatedVisibility(
            visible = showTrendingCard,
            enter = cardEnter,
            exit = cardExit
          ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
              if (trendingTracks.isEmpty()) {
                InsightsEmptyHint(
                  message = stringResource(id = R.string.insights_empty_history),
                  darkTheme = darkTheme
                )
              } else {
                trendingTracks.take(10).forEachIndexed { idx, entry ->
                  RankedSongEntryRow(
                    entry = entry,
                    trailingBadge = "${entry.playCount} plays",
                    direction = entry.rankDirection,
                    isCurrent = currentSong?.id == entry.song.id,
                    isPlaying = isPlaying,
                    darkTheme = darkTheme,
                    reducedMotion = reducedMotion,
                    hasLyrics = entry.song.id in verifiedLyricsIds,
                    onCheckLyrics = onCheckLyrics,
                    onSongClick = { onSongClick(entry.song, queue) },
                    onFavoriteClick = { onFavoriteClick(entry.song) }
                  )
                  if (idx < minOf(9, trendingTracks.lastIndex)) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                  }
                }
              }
            }
          }
        }
      }
    }

    // 4. Rising Artists Card
    item {
      GlassCard(
        darkTheme = darkTheme,
        cornerRadius = 24.dp,
        modifier = Modifier
          .fillMaxWidth()
          .testTag("insights_rising_artists_card")
      ) {
        Column(
          modifier = Modifier.padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          SectionTitleRow(
            title = stringResource(id = R.string.insights_rising_artists_title),
            subtitle = stringResource(id = R.string.insights_rising_artists_sub),
            icon = Icons.Rounded.AutoGraph,
            isExpanded = showRisingArtistsCard,
            onToggleExpand = { showRisingArtistsCard = !showRisingArtistsCard },
            darkTheme = darkTheme,
            reducedMotion = reducedMotion,
            toggleTestTag = "insights_toggle_rising_artists_button"
          )

          AnimatedVisibility(
            visible = showRisingArtistsCard,
            enter = cardEnter,
            exit = cardExit
          ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
              if (risingArtists.isEmpty()) {
                InsightsEmptyHint(
                  message = stringResource(id = R.string.insights_empty_history),
                  darkTheme = darkTheme
                )
              } else {
                risingArtists.take(8).forEachIndexed { idx, artist ->
                  Row(
                    modifier = Modifier
                      .fillMaxWidth()
                      .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                  ) {
                    Text(
                      text = String.format("%02d", idx + 1),
                      style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                      color = if (idx < 3) AurixTealAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                      modifier = Modifier.width(26.dp)
                    )
                    SongArtwork(
                      song = Song(
                        id = "artist_${artist.artist}",
                        title = artist.artist,
                        artist = artist.artist,
                        artworkUrl = artist.artworkUrl,
                        durationSeconds = 0L,
                        streamUrl = ""
                      ),
                      modifier = Modifier.size(42.dp),
                      cornerRadius = 12.dp,
                      iconSize = 18.dp
                    )
                    Column(modifier = Modifier.weight(1f)) {
                      Text(
                        text = artist.artist,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                      )
                      Text(
                        text = "${artist.recentPlays} plays recently" +
                          if (artist.previousPlays > 0) " · prev ${artist.previousPlays}" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                      )
                    }
                    Surface(
                      shape = CircleShape,
                      color = AurixTealAccent.copy(alpha = 0.16f)
                    ) {
                      Text(
                        text = "+${artist.growthPercentage}%",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                        color = AurixTealAccent,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                      )
                    }
                  }
                  if (idx < minOf(7, risingArtists.lastIndex)) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
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
private fun MusicDnaCard(
  profile: MusicDnaProfile,
  darkTheme: Boolean,
  reducedMotion: Boolean = false,
  isExpanded: Boolean = true,
  onToggleExpand: (() -> Unit)? = null
) {
  GlassCard(
    darkTheme = darkTheme,
    cornerRadius = 26.dp,
    modifier = Modifier
      .fillMaxWidth()
      .testTag("music_dna_card")
  ) {
    Column(
      modifier = Modifier.padding(18.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
      SectionTitleRow(
        title = stringResource(id = R.string.insights_dna_title),
        subtitle = stringResource(id = R.string.insights_dna_sub),
        icon = Icons.Rounded.GraphicEq,
        isExpanded = isExpanded,
        onToggleExpand = onToggleExpand,
        darkTheme = darkTheme,
        reducedMotion = reducedMotion,
        toggleTestTag = "insights_toggle_dna_button"
      )

      AnimatedVisibility(
        visible = isExpanded,
        enter = if (reducedMotion) fadeIn(snap()) else expandVertically(tween(240)) + fadeIn(tween(200)),
        exit = if (reducedMotion) fadeOut(snap()) else shrinkVertically(tween(200)) + fadeOut(tween(150))
      ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
          if (!profile.hasEnoughHistory) {
            InsightsEmptyHint(
              message = stringResource(id = R.string.insights_empty_dna),
              darkTheme = darkTheme
            )
          } else {
        // Key Metrics Row + Discovery Ring
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          DiscoveryGauge(
            percentage = profile.discoveryPercentage,
            darkTheme = darkTheme
          )
          Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            DnaStatChip(
              label = "Qualified plays (30s+)",
              value = "${profile.totalQualifiedPlays} plays · ${profile.totalMinutesListened}m",
              darkTheme = darkTheme
            )
            profile.favoriteBpmRange?.let { bpm ->
              DnaStatChip(
                label = "Favorite BPM range",
                value = "${bpm.label} (avg ${bpm.averageBpm})",
                darkTheme = darkTheme
              )
            }
            DnaStatChip(
              label = "Peak listening window",
              value = profile.peakHourRangeLabel,
              darkTheme = darkTheme
            )
          }
        }

        // Listening Hours 24h Bar Chart
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Text(
            text = "Listening hours",
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface
          )
          ListeningHoursBarChart(
            hours = profile.listeningHours,
            accentColor = AurixTealAccent,
            trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
          )
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            listOf("00:00", "06:00", "12:00", "18:00", "23:00").forEach { hr ->
              Text(
                text = hr,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          }
        }

        // Top Genres
        if (profile.topGenres.isNotEmpty()) {
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
              text = "Top genres",
              style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
              color = MaterialTheme.colorScheme.onSurface
            )
            profile.topGenres.forEach { stat ->
              Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween
                ) {
                  Text(
                    text = stat.genre,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                  )
                  Text(
                    text = "${stat.percentage}% · ${stat.playCount} plays",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                }
                LinearProgressIndicator(
                  progress = { (stat.percentage / 100f).coerceIn(0.05f, 1f) },
                  modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape),
                  color = AurixTealAccent,
                  trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                )
              }
            }
          }
        }

        // Mood Distribution
        if (profile.moodDistribution.isNotEmpty()) {
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
              text = "Mood distribution",
              style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
              color = MaterialTheme.colorScheme.onSurface
            )
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              profile.moodDistribution.forEach { mood ->
                Surface(
                  shape = RoundedCornerShape(14.dp),
                  color = MaterialTheme.colorScheme.primary.copy(alpha = if (darkTheme) 0.18f else 0.10f),
                  border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
                ) {
                  Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                  ) {
                    Text(
                      text = mood.mood,
                      style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                      color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                      text = "${mood.percentage}%",
                      style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                      color = MaterialTheme.colorScheme.primary
                    )
                  }
                }
              }
            }
          }
        }

        // Favorite Artists
        if (profile.favoriteArtists.isNotEmpty()) {
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
              text = "Favorite artists",
              style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
              color = MaterialTheme.colorScheme.onSurface
            )
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
              horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
              profile.favoriteArtists.forEach { artist ->
                Surface(
                  shape = RoundedCornerShape(16.dp),
                  color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (darkTheme) 0.07f else 0.05f)
                ) {
                  Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                  ) {
                    SongArtwork(
                      song = Song(
                        id = "fav_artist_${artist.artist}",
                        title = artist.artist,
                        artist = artist.artist,
                        artworkUrl = artist.artworkUrl,
                        durationSeconds = 0L,
                        streamUrl = ""
                      ),
                      modifier = Modifier.size(30.dp),
                      cornerRadius = 15.dp,
                      iconSize = 14.dp
                    )
                    Column {
                      Text(
                        text = artist.artist,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                      )
                      Text(
                        text = "${artist.playCount} plays",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
  }
}

@Composable
private fun DiscoveryGauge(
  percentage: Int,
  darkTheme: Boolean
) {
  val trackBg = MaterialTheme.colorScheme.onSurface.copy(alpha = if (darkTheme) 0.12f else 0.08f)
  Box(
    modifier = Modifier.size(92.dp),
    contentAlignment = Alignment.Center
  ) {
    Canvas(modifier = Modifier.fillMaxSize()) {
      val strokePx = 9.dp.toPx()
      val pad = strokePx / 2f
      val arcSize = Size(size.width - strokePx, size.height - strokePx)
      drawArc(
        color = trackBg,
        startAngle = 0f,
        sweepAngle = 360f,
        useCenter = false,
        topLeft = Offset(pad, pad),
        size = arcSize,
        style = Stroke(width = strokePx, cap = StrokeCap.Round)
      )
      drawArc(
        color = AurixTealAccent,
        startAngle = -90f,
        sweepAngle = (percentage / 100f).coerceIn(0.03f, 1f) * 360f,
        useCenter = false,
        topLeft = Offset(pad, pad),
        size = arcSize,
        style = Stroke(width = strokePx, cap = StrokeCap.Round)
      )
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
      Text(
        text = "$percentage%",
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
        color = MaterialTheme.colorScheme.onSurface
      )
      Text(
        text = "Discovery",
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
  }
}

@Composable
private fun ListeningHoursBarChart(
  hours: List<Int>,
  accentColor: Color,
  trackColor: Color
) {
  val buckets = if (hours.size == 24) hours else List(24) { 0 }
  val maxVal = (buckets.maxOrNull() ?: 1).coerceAtLeast(1)
  Canvas(
    modifier = Modifier
      .fillMaxWidth()
      .height(58.dp)
  ) {
    val gap = 4.dp.toPx()
    val barWidth = ((size.width - gap * 23f) / 24f).coerceAtLeast(2f)
    buckets.forEachIndexed { idx, count ->
      val x = idx * (barWidth + gap)
      drawRoundRect(
        color = trackColor,
        topLeft = Offset(x, 0f),
        size = Size(barWidth, size.height),
        cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
      )
      if (count > 0) {
        val ratio = (count.toFloat() / maxVal.toFloat()).coerceIn(0.18f, 1f)
        val barH = size.height * ratio
        drawRoundRect(
          color = accentColor,
          topLeft = Offset(x, size.height - barH),
          size = Size(barWidth, barH),
          cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
        )
      }
    }
  }
}

@Composable
private fun DnaStatChip(
  label: String,
  value: String,
  darkTheme: Boolean
) {
  Surface(
    shape = RoundedCornerShape(12.dp),
    color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (darkTheme) 0.07f else 0.05f),
    modifier = Modifier.fillMaxWidth()
  ) {
    Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
      Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
      Text(
        text = value,
        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
    }
  }
}

@Composable
private fun RankedSongEntryRow(
  entry: RankedTrack,
  trailingBadge: String,
  direction: RankDirection?,
  isCurrent: Boolean,
  isPlaying: Boolean,
  darkTheme: Boolean,
  reducedMotion: Boolean,
  hasLyrics: Boolean,
  onCheckLyrics: (Song) -> Unit,
  onSongClick: () -> Unit,
  onFavoriteClick: () -> Unit
) {
  Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
    SongRow(
      song = entry.song,
      isPlaying = isPlaying,
      isCurrent = isCurrent,
      reducedMotion = reducedMotion,
      darkTheme = darkTheme,
      hasLyrics = hasLyrics,
      rank = entry.rank,
      showSourceBadge = false,
      onCheckLyrics = onCheckLyrics,
      onSongClick = onSongClick,
      onFavoriteClick = onFavoriteClick
    )
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(start = 40.dp, end = 8.dp, bottom = 4.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
      ) {
        Text(
          text = trailingBadge,
          style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
          color = MaterialTheme.colorScheme.primary,
          modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        )
      }
      if (entry.song.genre.isNotBlank()) {
        Text(
          text = "${entry.song.genre} · ${entry.song.bpm} BPM",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
      Spacer(modifier = Modifier.weight(1f))
      if (direction != null) {
        RankDirectionBadge(direction)
      }
    }
  }
}

@Composable
private fun RankDirectionBadge(direction: RankDirection) {
  when (direction) {
    is RankDirection.Up -> {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
      ) {
        Icon(
          imageVector = Icons.AutoMirrored.Rounded.TrendingUp,
          contentDescription = "Moved up ${direction.delta}",
          tint = AurixTealAccent,
          modifier = Modifier.size(16.dp)
        )
        Text(
          text = "+${direction.delta}",
          style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
          color = AurixTealAccent
        )
      }
    }
    is RankDirection.Down -> {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
      ) {
        Icon(
          imageVector = Icons.AutoMirrored.Rounded.TrendingDown,
          contentDescription = "Moved down ${direction.delta}",
          tint = MaterialTheme.colorScheme.error,
          modifier = Modifier.size(16.dp)
        )
        Text(
          text = "-${direction.delta}",
          style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
          color = MaterialTheme.colorScheme.error
        )
      }
    }
    RankDirection.New -> {
      Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
      ) {
        Text(
          text = "NEW",
          style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold, fontSize = 10.sp),
          color = MaterialTheme.colorScheme.primary,
          modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
        )
      }
    }
    RankDirection.Same -> {
      Text(
        text = "—",
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
  }
}

@Composable
fun PlaylistDetailScreen(
  title: String,
  subtitle: String,
  badgeLabel: String,
  tracks: List<Song>,
  emptyMessage: String,
  showRefreshButton: Boolean = false,
  currentSong: Song?,
  isPlaying: Boolean,
  darkTheme: Boolean,
  reducedMotion: Boolean,
  verifiedLyricsIds: Set<String> = emptySet(),
  resolvingSongId: String? = null,
  downloadedSongIds: Set<String> = emptySet(),
  songDownloadProgress: Map<String, Float> = emptyMap(),
  onBack: () -> Unit,
  onRefresh: () -> Unit = {},
  onPlayAll: () -> Unit,
  onShuffleAll: () -> Unit,
  onCheckLyrics: (Song) -> Unit,
  onSongClick: (Song, List<Song>) -> Unit,
  onFavoriteClick: (Song) -> Unit,
  onYoutubeSongDownloadClick: ((Song) -> Unit)? = null
) {
  LazyColumn(
    modifier = Modifier
      .fillMaxSize()
      .padding(horizontal = 20.dp)
      .testTag("playlist_detail_screen"),
    verticalArrangement = Arrangement.spacedBy(14.dp),
    contentPadding = PaddingValues(
      top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 14.dp,
      bottom = if (currentSong != null) 205.dp else 135.dp
    )
  ) {
    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        val iconBg = if (darkTheme) Color(0xFF151F30) else Color.White.copy(alpha = 0.85f)
        val iconBorder = if (darkTheme) Color(0xFF273348) else Color.White
        IconButton(
          onClick = onBack,
          modifier = Modifier
            .minimumInteractiveComponentSize()
            .size(44.dp)
            .clip(CircleShape)
            .background(iconBg)
            .border(1.dp, iconBorder, CircleShape)
            .testTag("playlist_detail_back_button")
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
            contentDescription = stringResource(id = R.string.insights_back_cd),
            tint = MaterialTheme.colorScheme.onSurface
          )
        }
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = badgeLabel.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(
              fontWeight = FontWeight.ExtraBold,
              letterSpacing = 0.8.sp
            ),
            color = AurixTealAccent
          )
          Text(
            text = title,
            style = MaterialTheme.typography.titleLarge.copy(
              fontWeight = FontWeight.ExtraBold,
              fontSize = 24.sp
            ),
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
          Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
          )
        }
        if (showRefreshButton) {
          IconButton(
            onClick = onRefresh,
            modifier = Modifier
              .minimumInteractiveComponentSize()
              .size(44.dp)
              .clip(CircleShape)
              .background(iconBg)
              .border(1.dp, iconBorder, CircleShape)
              .testTag("discover_weekly_refresh_button")
          ) {
            Icon(
              imageVector = Icons.Rounded.Refresh,
              contentDescription = stringResource(id = R.string.playlist_refresh_cd),
              tint = MaterialTheme.colorScheme.onSurface
            )
          }
        }
      }
    }

    if (tracks.isNotEmpty()) {
      item {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          Button(
            onClick = onPlayAll,
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
              containerColor = AurixTealAccent,
              contentColor = Color(0xFF06201C)
            ),
            modifier = Modifier
              .weight(1f)
              .defaultMinSize(minHeight = 48.dp)
              .testTag("playlist_play_all_button")
          ) {
            Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(stringResource(id = R.string.album_action_play), fontWeight = FontWeight.Bold)
          }
          OutlinedButton(
            onClick = onShuffleAll,
            shape = CircleShape,
            modifier = Modifier
              .weight(1f)
              .defaultMinSize(minHeight = 48.dp)
              .testTag("playlist_shuffle_button")
          ) {
            Icon(Icons.Rounded.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(stringResource(id = R.string.album_action_shuffle), fontWeight = FontWeight.Bold)
          }
        }
      }

      item {
        if (onYoutubeSongDownloadClick != null) {
          Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            tracks.forEachIndexed { idx, track ->
              SongDownloadRow(
                song = track,
                index = idx,
                isPlaying = isPlaying,
                isCurrent = currentSong?.id == track.id,
                isResolving = resolvingSongId == track.id,
                reducedMotion = reducedMotion,
                darkTheme = darkTheme,
                hasLyrics = track.id in verifiedLyricsIds,
                downloadProgress = songDownloadProgress[track.id],
                isDownloaded = track.id in downloadedSongIds,
                onCheckLyrics = onCheckLyrics,
                onSongClick = { onSongClick(track, tracks) },
                onFavoriteClick = { onFavoriteClick(track) },
                onDownloadClick = { onYoutubeSongDownloadClick(track) }
              )
            }
          }
        } else {
          GlassCard(
            darkTheme = darkTheme,
            cornerRadius = 24.dp,
            modifier = Modifier.fillMaxWidth()
          ) {
            Column(
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
              tracks.forEachIndexed { idx, track ->
                SongRow(
                  song = track,
                  isPlaying = isPlaying,
                  isCurrent = currentSong?.id == track.id,
                  reducedMotion = reducedMotion,
                  darkTheme = darkTheme,
                  hasLyrics = track.id in verifiedLyricsIds,
                  rank = idx + 1,
                  showSourceBadge = false,
                  onCheckLyrics = onCheckLyrics,
                  onSongClick = { onSongClick(track, tracks) },
                  onFavoriteClick = { onFavoriteClick(track) }
                )
                if (idx < tracks.lastIndex) {
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
    } else {
      item {
        GlassCard(
          darkTheme = darkTheme,
          cornerRadius = 24.dp,
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 22.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            Icon(
              imageVector = Icons.Rounded.MusicNote,
              contentDescription = null,
              tint = AurixTealAccent,
              modifier = Modifier.size(32.dp)
            )
            Text(
              text = emptyMessage,
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              textAlign = TextAlign.Center
            )
          }
        }
      }
    }
  }
}

@Composable
private fun SectionTitleRow(
  title: String,
  subtitle: String,
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  isExpanded: Boolean? = null,
  onToggleExpand: (() -> Unit)? = null,
  darkTheme: Boolean = true,
  reducedMotion: Boolean = false,
  toggleTestTag: String? = null
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(10.dp)
  ) {
    Box(
      modifier = Modifier
        .size(36.dp)
        .clip(CircleShape)
        .background(AurixTealAccent.copy(alpha = 0.16f)),
      contentAlignment = Alignment.Center
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = AurixTealAccent,
        modifier = Modifier.size(18.dp)
      )
    }
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = title,
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.onSurface
      )
      Text(
        text = subtitle,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
    if (isExpanded != null && onToggleExpand != null && toggleTestTag != null) {
      OpenCloseGlassPill(
        isOpen = isExpanded,
        onToggle = onToggleExpand,
        darkTheme = darkTheme,
        reducedMotion = reducedMotion,
        testTag = toggleTestTag,
        contentDescription = stringResource(
          id = if (isExpanded) R.string.section_close_cd else R.string.section_open_cd,
          title
        ),
        accentColor = AurixTealAccent
      )
    }
  }
}

@Composable
private fun InsightsEmptyHint(
  message: String,
  darkTheme: Boolean
) {
  Box(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(16.dp))
      .background(MaterialTheme.colorScheme.onSurface.copy(alpha = if (darkTheme) 0.06f else 0.04f))
      .padding(horizontal = 16.dp, vertical = 20.dp),
    contentAlignment = Alignment.Center
  ) {
    Text(
      text = message,
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      textAlign = TextAlign.Center
    )
  }
}

@Composable
fun NewAlbumsDetailScreen(
  albums: List<FeaturedAlbumItem>,
  hasCurrentSong: Boolean,
  darkTheme: Boolean,
  onBack: () -> Unit,
  onRefresh: () -> Unit,
  onAlbumClick: (FeaturedAlbumItem) -> Unit
) {
  LazyColumn(
    modifier = Modifier
      .fillMaxSize()
      .padding(horizontal = 20.dp)
      .testTag("new_albums_detail_screen"),
    verticalArrangement = Arrangement.spacedBy(14.dp),
    contentPadding = PaddingValues(
      top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 14.dp,
      bottom = if (hasCurrentSong) 205.dp else 135.dp
    )
  ) {
    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        val iconBg = if (darkTheme) Color(0xFF151F30) else Color.White.copy(alpha = 0.85f)
        val iconBorder = if (darkTheme) Color(0xFF273348) else Color.White
        IconButton(
          onClick = onBack,
          modifier = Modifier
            .minimumInteractiveComponentSize()
            .size(44.dp)
            .clip(CircleShape)
            .background(iconBg)
            .border(1.dp, iconBorder, CircleShape)
            .testTag("new_albums_back_button")
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
            contentDescription = stringResource(id = R.string.insights_back_cd),
            tint = MaterialTheme.colorScheme.onSurface
          )
        }
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = stringResource(id = R.string.home_itunes_badge).uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(
              fontWeight = FontWeight.ExtraBold,
              letterSpacing = 0.8.sp
            ),
            color = AurixTealAccent
          )
          Text(
            text = stringResource(id = R.string.home_new_albums_title),
            style = MaterialTheme.typography.titleLarge.copy(
              fontWeight = FontWeight.ExtraBold,
              fontSize = 24.sp
            ),
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }
        IconButton(
          onClick = onRefresh,
          modifier = Modifier
            .minimumInteractiveComponentSize()
            .size(44.dp)
            .clip(CircleShape)
            .background(iconBg)
            .border(1.dp, iconBorder, CircleShape)
            .testTag("new_albums_refresh_button")
        ) {
          Icon(
            imageVector = Icons.Rounded.Refresh,
            contentDescription = stringResource(id = R.string.home_refresh_cd),
            tint = MaterialTheme.colorScheme.onSurface
          )
        }
      }
    }

    if (albums.isEmpty()) {
      item {
        InsightsEmptyHint(
          message = stringResource(id = R.string.home_new_albums_empty),
          darkTheme = darkTheme
        )
      }
    } else {
      val rows = albums.chunked(2)
      itemsIndexed(rows) { _, pair ->
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
          pair.forEach { album ->
            GlassCard(
              darkTheme = darkTheme,
              cornerRadius = 20.dp,
              modifier = Modifier
                .weight(1f)
                .clickable { onAlbumClick(album) }
                .testTag("new_album_grid_card_${album.id}")
            ) {
              Column(modifier = Modifier.padding(10.dp)) {
                Box(
                  modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                    .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(14.dp)),
                  contentAlignment = Alignment.Center
                ) {
                  if (album.artworkUrl.isNotBlank()) {
                    AsyncImage(
                      model = album.artworkUrl,
                      contentDescription = album.title,
                      contentScale = ContentScale.Crop,
                      modifier = Modifier.fillMaxSize()
                    )
                  } else {
                    Icon(
                      imageVector = Icons.Rounded.Album,
                      contentDescription = null,
                      tint = MaterialTheme.colorScheme.onSurfaceVariant,
                      modifier = Modifier.size(36.dp)
                    )
                  }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                  text = album.title,
                  style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                  color = MaterialTheme.colorScheme.onSurface,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis
                )
                Text(
                  text = if (album.year.isNotBlank()) "${album.artist} · ${album.year}" else album.artist,
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis
                )
              }
            }
          }
          if (pair.size == 1) {
            Spacer(modifier = Modifier.weight(1f))
          }
        }
      }
    }
  }
}
