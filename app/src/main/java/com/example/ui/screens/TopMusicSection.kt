package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.R
import com.example.model.SearchUiState
import com.example.model.Song
import com.example.topmusic.CountryRankBreakdown
import com.example.topmusic.TopMusicBadge
import com.example.topmusic.TopMusicFilter
import com.example.topmusic.TopMusicRankedItem
import com.example.topmusic.TopMusicRegion
import com.example.topmusic.TopMusicUiState
import com.example.ui.theme.AccentAmber
import java.util.Locale

private val GoldPlanetAccent = Color(0xFFF5B041)
private val GoldPlanetDeep = Color(0xFFD97706)
private val SilverRankColor = Color(0xFFE2E8F0)
private val BronzeRankColor = Color(0xFFDF8B56)
private val RisingGreen = Color(0xFF34D399)
private val DropCoral = Color(0xFFF87171)

private val NotoSansKhmerFamily = FontFamily(
  Font(R.font.noto_sans_khmer, FontWeight.Normal),
  Font(R.font.noto_sans_khmer, FontWeight.Medium),
  Font(R.font.noto_sans_khmer, FontWeight.SemiBold),
  Font(R.font.noto_sans_khmer, FontWeight.Bold)
)

private fun isKhmerCodePoint(code: Int): Boolean {
  return (code in 0x1780..0x17FF) || (code in 0x19E0..0x19FF)
}

fun buildMixedKhmerText(text: String): AnnotatedString {
  if (text.none { isKhmerCodePoint(it.code) }) {
    return AnnotatedString(text)
  }
  return buildAnnotatedString {
    var i = 0
    while (i < text.length) {
      val start = i
      val isKhmer = isKhmerCodePoint(text[i].code)
      while (i < text.length && isKhmerCodePoint(text[i].code) == isKhmer) {
        i++
      }
      val segment = text.substring(start, i)
      if (isKhmer) {
        pushStyle(SpanStyle(fontFamily = NotoSansKhmerFamily))
        append(segment)
        pop()
      } else {
        append(segment)
      }
    }
  }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TopMusicSection(
  uiState: TopMusicUiState,
  currentSong: Song?,
  isPlaying: Boolean,
  resolvingSongId: String?,
  darkTheme: Boolean,
  reducedMotion: Boolean,
  remoteSearchUiState: SearchUiState = SearchUiState.Idle,
  verifiedLyricsIds: Set<String> = emptySet(),
  songDownloadProgress: Map<String, Float> = emptyMap(),
  downloadedSongIds: Set<String> = emptySet(),
  onRegionSelected: (TopMusicRegion) -> Unit,
  onFilterSelected: (TopMusicFilter) -> Unit,
  onRefreshClick: () -> Unit,
  onClearSearch: () -> Unit,
  onPlayTopMusicItem: (TopMusicRankedItem) -> Unit,
  onCheckLyrics: (Song) -> Unit = {},
  onRemoteSongClick: (Song, List<Song>) -> Unit = { _, _ -> },
  onFavoriteClick: (Song) -> Unit = {},
  onYoutubeSongDownloadClick: (Song) -> Unit = {},
  modifier: Modifier = Modifier
) {
  var expandedItemKey by rememberSaveable(uiState.selectedRegion, uiState.selectedFilter) {
    mutableStateOf<String?>(null)
  }
  val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
  val listBottomPadding = (if (currentSong != null) 205.dp else 135.dp) + navBarBottom

  Column(
    modifier = modifier.fillMaxSize(),
    verticalArrangement = Arrangement.spacedBy(10.dp)
  ) {
    // Header: Top Music title + Sample data badge + Updated date / Refresh
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.weight(1f)
      ) {
        Box(
          modifier = Modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(
              Brush.linearGradient(
                colors = listOf(GoldPlanetAccent.copy(alpha = 0.28f), AurixTealAccent.copy(alpha = 0.16f))
              )
            )
            .border(1.dp, GoldPlanetAccent.copy(alpha = 0.45f), CircleShape),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = Icons.Rounded.Public,
            contentDescription = null,
            tint = GoldPlanetAccent,
            modifier = Modifier.size(16.dp)
          )
        }
        Text(
          text = stringResource(id = R.string.top_music_title),
          style = MaterialTheme.typography.titleMedium.copy(
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-0.2).sp
          ),
          color = MaterialTheme.colorScheme.onBackground,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
        if (uiState.isSampleData) {
          Surface(
            shape = CircleShape,
            color = GoldPlanetAccent.copy(alpha = if (darkTheme) 0.16f else 0.18f),
            border = BorderStroke(1.dp, GoldPlanetAccent.copy(alpha = 0.40f)),
            modifier = Modifier.testTag("top_music_sample_badge")
          ) {
            Text(
              text = stringResource(id = R.string.top_music_sample_data_badge),
              style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
              ),
              color = if (darkTheme) GoldPlanetAccent else GoldPlanetDeep,
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
            )
          }
        }
      }

      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
      ) {
        if (uiState.lastUpdatedDate.isNotBlank() && !uiState.isOffline) {
          Text(
            text = uiState.lastUpdatedDate,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
          )
        }
        IconButton(
          onClick = onRefreshClick,
          modifier = Modifier
            .minimumInteractiveComponentSize()
            .testTag("top_music_refresh_button")
        ) {
          Icon(
            imageVector = Icons.Rounded.Refresh,
            contentDescription = stringResource(id = R.string.top_music_refresh_cd),
            tint = GoldPlanetAccent,
            modifier = Modifier.size(20.dp)
          )
        }
      }
    }

    // Offline banner showing last cached chart date
    AnimatedVisibility(
      visible = uiState.isOffline && uiState.lastUpdatedDate.isNotBlank(),
      enter = if (reducedMotion) fadeIn(snap()) else expandVertically() + fadeIn(),
      exit = if (reducedMotion) fadeOut(snap()) else shrinkVertically() + fadeOut()
    ) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(14.dp))
          .background(GoldPlanetAccent.copy(alpha = 0.14f))
          .border(1.dp, GoldPlanetAccent.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
          .padding(horizontal = 12.dp, vertical = 8.dp)
          .testTag("top_music_offline_banner"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        Icon(
          imageVector = Icons.Rounded.CloudOff,
          contentDescription = null,
          tint = GoldPlanetAccent,
          modifier = Modifier.size(16.dp)
        )
        Text(
          text = stringResource(id = R.string.top_music_offline_cached, uiState.lastUpdatedDate),
          style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
          color = if (darkTheme) GoldPlanetAccent else GoldPlanetDeep,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
      }
    }

    // 2. 4 Region Tabs: Khmer | Asia | Europe | Global
    TopMusicRegionTabs(
      selectedRegion = uiState.selectedRegion,
      darkTheme = darkTheme,
      reducedMotion = reducedMotion,
      onSelect = onRegionSelected
    )

    // 3. Horizontal Filter Chips: Trending Today | Rising Fast | New Today | Top Songs | Top Music Videos
    TopMusicFilterChipsRow(
      selectedFilter = uiState.selectedFilter,
      darkTheme = darkTheme,
      reducedMotion = reducedMotion,
      onSelect = onFilterSelected
    )

    // 4. One-line description of the current filter
    val filterDescRes = when (uiState.selectedFilter) {
      TopMusicFilter.TRENDING_TODAY -> {
        if (uiState.selectedRegion == TopMusicRegion.KHMER) {
          R.string.top_music_desc_trending_khmer
        } else {
          R.string.top_music_desc_trending
        }
      }
      TopMusicFilter.RISING_FAST -> R.string.top_music_desc_rising
      TopMusicFilter.NEW_TODAY -> R.string.top_music_desc_new
      TopMusicFilter.TOP_SONGS -> R.string.top_music_desc_top_songs
      TopMusicFilter.TOP_MUSIC_VIDEOS -> R.string.top_music_desc_music_videos
    }
    AnimatedContent(
      targetState = filterDescRes,
      transitionSpec = {
        if (reducedMotion) {
          fadeIn(snap()) togetherWith fadeOut(snap())
        } else {
          (fadeIn(tween(180)) + slideInVertically(tween(180)) { it / 3 }) togetherWith
            (fadeOut(tween(120)) + slideOutVertically(tween(120)) { -it / 3 })
        }
      },
      label = "top_music_filter_desc"
    ) { resId ->
      Text(
        text = stringResource(id = resId),
        style = MaterialTheme.typography.bodySmall.copy(
          fontSize = 12.sp,
          lineHeight = 16.sp,
          fontWeight = FontWeight.Medium
        ),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 2.dp)
          .testTag("top_music_filter_description")
      )
    }

    // 5. Ranked list + Loading / Empty states
    AnimatedContent(
      targetState = Triple(uiState.isLoading, uiState.selectedRegion, uiState.selectedFilter),
      modifier = Modifier
        .weight(1f)
        .fillMaxWidth(),
      transitionSpec = {
        if (reducedMotion) {
          fadeIn(snap()) togetherWith fadeOut(snap())
        } else {
          (fadeIn(tween(240, easing = FastOutSlowInEasing)) +
            slideInVertically(tween(240, easing = FastOutSlowInEasing)) { it / 20 }) togetherWith
            fadeOut(tween(150))
        }
      },
      label = "top_music_list_transition"
    ) { (isLoading, _, _) ->
      when {
        isLoading -> {
          TopMusicLoadingSkeleton(
            darkTheme = darkTheme,
            reducedMotion = reducedMotion,
            bottomPadding = listBottomPadding
          )
        }
        uiState.items.isEmpty() -> {
          LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = listBottomPadding),
            verticalArrangement = Arrangement.spacedBy(14.dp)
          ) {
            item {
              TopMusicEmptyStateCard(
                searchQuery = uiState.searchQuery,
                selectedRegion = uiState.selectedRegion,
                darkTheme = darkTheme,
                onTryGlobal = { onRegionSelected(TopMusicRegion.GLOBAL) },
                onClearSearch = onClearSearch
              )
            }
            if (uiState.searchQuery.isNotBlank() && remoteSearchUiState is SearchUiState.Results) {
              item {
                Text(
                  text = stringResource(id = R.string.top_music_more_youtube_results),
                  style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  modifier = Modifier.padding(top = 6.dp, start = 2.dp)
                )
              }
              itemsIndexed(remoteSearchUiState.items, key = { _, song -> "remote_${song.id}" }) { index, song ->
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
                  onSongClick = { onRemoteSongClick(song, remoteSearchUiState.items) },
                  onFavoriteClick = { onFavoriteClick(song) },
                  onDownloadClick = { onYoutubeSongDownloadClick(song) }
                )
              }
            }
          }
        }
        else -> {
          LazyColumn(
            modifier = Modifier
              .fillMaxSize()
              .testTag("top_music_list"),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = listBottomPadding)
          ) {
            itemsIndexed(
              items = uiState.items,
              key = { _, item -> "${item.chartType.id}_${item.videoId}_${item.title}" }
            ) { _, item ->
              val rowKey = "${item.chartType.id}_${item.videoId}_${item.title}"
              val isExpanded = expandedItemKey == rowKey
              val songId = "yt_${item.videoId}"
              TopMusicRankedRow(
                item = item,
                expanded = isExpanded,
                isCurrent = currentSong?.id == songId,
                isPlaying = isPlaying,
                isResolving = resolvingSongId == songId,
                darkTheme = darkTheme,
                reducedMotion = reducedMotion,
                onToggleExpand = {
                  expandedItemKey = if (isExpanded) null else rowKey
                },
                onPlayClick = { onPlayTopMusicItem(item) },
                modifier = Modifier.animateItem(
                  fadeInSpec = if (reducedMotion) null else tween(200),
                  fadeOutSpec = if (reducedMotion) null else tween(140),
                  placementSpec = if (reducedMotion) null else spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow
                  )
                )
              )
            }

            if (uiState.searchQuery.isNotBlank() && remoteSearchUiState is SearchUiState.Results) {
              item {
                Text(
                  text = stringResource(id = R.string.top_music_more_youtube_results),
                  style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  modifier = Modifier.padding(top = 8.dp, start = 2.dp)
                )
              }
              itemsIndexed(remoteSearchUiState.items, key = { _, song -> "remote_${song.id}" }) { index, song ->
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
                  onSongClick = { onRemoteSongClick(song, remoteSearchUiState.items) },
                  onFavoriteClick = { onFavoriteClick(song) },
                  onDownloadClick = { onYoutubeSongDownloadClick(song) }
                )
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun TopMusicRegionTabs(
  selectedRegion: TopMusicRegion,
  darkTheme: Boolean,
  reducedMotion: Boolean,
  onSelect: (TopMusicRegion) -> Unit
) {
  val regions = TopMusicRegion.entries
  val selectedIndex = regions.indexOf(selectedRegion).coerceAtLeast(0)
  val containerBg = if (darkTheme) Color(0xFF0E1525).copy(alpha = 0.88f) else Color(0xFFE7EEF9)
  val containerBorder = if (darkTheme) Color.White.copy(alpha = 0.14f) else Color.White

  BoxWithConstraints(
    modifier = Modifier
      .fillMaxWidth()
      .height(50.dp)
      .clip(RoundedCornerShape(18.dp))
      .background(containerBg)
      .border(1.dp, containerBorder, RoundedCornerShape(18.dp))
      .padding(4.dp)
  ) {
    val tabWidth = maxWidth / regions.size
    val indicatorOffset by animateDpAsState(
      targetValue = tabWidth * selectedIndex,
      animationSpec = if (reducedMotion) snap() else spring(
        dampingRatio = 0.78f,
        stiffness = 440f
      ),
      label = "region_tab_indicator_offset"
    )

    // Sliding Gold / Planet active capsule
    Box(
      modifier = Modifier
        .offset(x = indicatorOffset)
        .width(tabWidth)
        .fillMaxHeight()
        .clip(RoundedCornerShape(14.dp))
        .background(
          Brush.linearGradient(
            colors = if (darkTheme) {
              listOf(
                GoldPlanetAccent.copy(alpha = 0.28f),
                AccentAmber.copy(alpha = 0.14f)
              )
            } else {
              listOf(Color.White, Color(0xFFFFF8EC))
            }
          )
        )
        .border(
          width = 1.dp,
          color = GoldPlanetAccent.copy(alpha = if (darkTheme) 0.55f else 0.70f),
          shape = RoundedCornerShape(14.dp)
        )
    )

    Row(modifier = Modifier.fillMaxSize()) {
      regions.forEach { region ->
        val selected = region == selectedRegion
        var isFocused by remember { mutableStateOf(false) }
        val labelRes = when (region) {
          TopMusicRegion.KHMER -> R.string.top_music_tab_khmer
          TopMusicRegion.ASIA -> R.string.top_music_tab_asia
          TopMusicRegion.EUROPE -> R.string.top_music_tab_europe
          TopMusicRegion.GLOBAL -> R.string.top_music_tab_global
        }
        val textColor by animateColorAsState(
          targetValue = when {
            selected && darkTheme -> GoldPlanetAccent
            selected -> GoldPlanetDeep
            else -> MaterialTheme.colorScheme.onSurfaceVariant
          },
          animationSpec = if (reducedMotion) snap() else tween(180),
          label = "region_tab_text_color"
        )
        Box(
          modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .clip(RoundedCornerShape(14.dp))
            .then(
              if (isFocused) {
                Modifier.border(1.5.dp, GoldPlanetAccent, RoundedCornerShape(14.dp))
              } else {
                Modifier
              }
            )
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .clickable { onSelect(region) }
            .testTag("top_music_tab_${region.id}"),
          contentAlignment = Alignment.Center
        ) {
          Text(
            text = stringResource(id = labelRes),
            style = MaterialTheme.typography.labelLarge.copy(
              fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
              fontSize = 13.sp
            ),
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }
      }
    }
  }
}

@Composable
private fun TopMusicFilterChipsRow(
  selectedFilter: TopMusicFilter,
  darkTheme: Boolean,
  reducedMotion: Boolean,
  onSelect: (TopMusicFilter) -> Unit
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .horizontalScroll(rememberScrollState()),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    TopMusicFilter.entries.forEach { filter ->
      val selected = filter == selectedFilter
      var isFocused by remember { mutableStateOf(false) }
      val chipScale by animateFloatAsState(
        targetValue = if (selected) 1.02f else 1f,
        animationSpec = if (reducedMotion) snap() else spring(
          dampingRatio = 0.7f,
          stiffness = 500f
        ),
        label = "filter_chip_scale"
      )
      val bgColor by animateColorAsState(
        targetValue = when {
          selected && darkTheme -> GoldPlanetAccent.copy(alpha = 0.22f)
          selected -> GoldPlanetAccent.copy(alpha = 0.20f)
          darkTheme -> Color.White.copy(alpha = 0.08f)
          else -> Color.White.copy(alpha = 0.85f)
        },
        animationSpec = if (reducedMotion) snap() else tween(180),
        label = "filter_chip_bg"
      )
      val borderColor by animateColorAsState(
        targetValue = when {
          isFocused -> OrbitPlanetDefault
          selected -> GoldPlanetAccent.copy(alpha = 0.78f)
          darkTheme -> Color.White.copy(alpha = 0.16f)
          else -> Color.White
        },
        animationSpec = if (reducedMotion) snap() else tween(180),
        label = "filter_chip_border"
      )

      val labelRes = when (filter) {
        TopMusicFilter.TRENDING_TODAY -> R.string.top_music_filter_trending
        TopMusicFilter.RISING_FAST -> R.string.top_music_filter_rising
        TopMusicFilter.NEW_TODAY -> R.string.top_music_filter_new
        TopMusicFilter.TOP_SONGS -> R.string.top_music_filter_top_songs
        TopMusicFilter.TOP_MUSIC_VIDEOS -> R.string.top_music_filter_music_videos
      }

      Box(
        modifier = Modifier
          .scale(chipScale)
          .defaultMinSize(minHeight = 40.dp)
          .clip(CircleShape)
          .background(bgColor)
          .border(width = if (isFocused || selected) 1.25.dp else 1.dp, color = borderColor, shape = CircleShape)
          .onFocusChanged { isFocused = it.isFocused }
          .focusable()
          .clickable { onSelect(filter) }
          .padding(horizontal = 14.dp, vertical = 9.dp)
          .testTag("top_music_filter_${filter.id}"),
        contentAlignment = Alignment.Center
      ) {
        Text(
          text = stringResource(id = labelRes),
          style = MaterialTheme.typography.bodySmall.copy(
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
            fontSize = 12.5.sp
          ),
          color = when {
            selected && darkTheme -> GoldPlanetAccent
            selected -> GoldPlanetDeep
            else -> MaterialTheme.colorScheme.onSurface
          },
          maxLines = 1
        )
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TopMusicRankedRow(
  item: TopMusicRankedItem,
  expanded: Boolean,
  isCurrent: Boolean,
  isPlaying: Boolean,
  isResolving: Boolean,
  darkTheme: Boolean,
  reducedMotion: Boolean,
  onToggleExpand: () -> Unit,
  onPlayClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  var isFocused by remember { mutableStateOf(false) }
  val chevronRotation by animateFloatAsState(
    targetValue = if (expanded) 180f else 0f,
    animationSpec = if (reducedMotion) snap() else spring(
      dampingRatio = 0.8f,
      stiffness = 420f
    ),
    label = "expand_chevron_rotation"
  )

  val cardShape = RoundedCornerShape(20.dp)
  val cardBg = when {
    isCurrent && darkTheme -> Color(0xFF1B243A).copy(alpha = 0.96f)
    expanded && darkTheme -> Color(0xFF172136).copy(alpha = 0.94f)
    darkTheme -> Color(0xFF12192A).copy(alpha = 0.86f)
    expanded -> Color.White.copy(alpha = 0.96f)
    else -> Color.White.copy(alpha = 0.86f)
  }
  val borderColor = when {
    isFocused -> GoldPlanetAccent
    isCurrent -> AurixTealAccent.copy(alpha = 0.72f)
    expanded || item.rank <= 3 -> GoldPlanetAccent.copy(alpha = if (darkTheme) 0.42f else 0.55f)
    darkTheme -> Color.White.copy(alpha = 0.12f)
    else -> Color.White
  }

  Column(
    modifier = modifier
      .fillMaxWidth()
      .clip(cardShape)
      .background(cardBg)
      .border(width = if (expanded || isFocused || isCurrent) 1.25.dp else 1.dp, color = borderColor, shape = cardShape)
      .onFocusChanged { isFocused = it.isFocused }
      .focusable()
      .animateContentSize(
        animationSpec = if (reducedMotion) snap() else spring(
          dampingRatio = 0.82f,
          stiffness = 380f
        )
      )
      .clickable { onToggleExpand() }
      .padding(horizontal = 12.dp, vertical = 12.dp)
      .testTag("top_music_row_${item.rank}_${item.videoId}")
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      // Rank badge
      val rankColor = when (item.rank) {
        1 -> GoldPlanetAccent
        2 -> SilverRankColor
        3 -> BronzeRankColor
        else -> MaterialTheme.colorScheme.onSurfaceVariant
      }
      Box(
        modifier = Modifier
          .width(30.dp)
          .defaultMinSize(minHeight = 30.dp)
          .clip(RoundedCornerShape(10.dp))
          .background(
            if (item.rank <= 3) rankColor.copy(alpha = 0.16f) else Color.Transparent
          ),
        contentAlignment = Alignment.Center
      ) {
        Text(
          text = "#${item.rank}",
          style = MaterialTheme.typography.titleSmall.copy(
            fontWeight = FontWeight.ExtraBold,
            fontSize = if (item.rank <= 3) 14.sp else 13.sp
          ),
          color = if (item.rank <= 3 || darkTheme) rankColor else MaterialTheme.colorScheme.onSurface,
          maxLines = 1
        )
      }

      // Artwork thumbnail with tap-to-play action (48.dp touch target)
      Box(
        modifier = Modifier
          .size(48.dp)
          .clip(RoundedCornerShape(12.dp))
          .background(if (darkTheme) Color(0xFF1F2B44) else Color(0xFFE2E8F0))
          .border(
            1.dp,
            if (isCurrent) AurixTealAccent.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.16f),
            RoundedCornerShape(12.dp)
          )
          .clickable { onPlayClick() }
          .testTag("top_music_artwork_play_${item.videoId}"),
        contentAlignment = Alignment.Center
      ) {
        if (item.artworkUrl.isNotBlank()) {
          AsyncImage(
            model = item.artworkUrl,
            contentDescription = stringResource(id = R.string.top_music_play_cd, item.title),
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
          )
        } else {
          Icon(
            imageVector = Icons.Rounded.MusicNote,
            contentDescription = stringResource(id = R.string.top_music_play_cd, item.title),
            tint = GoldPlanetAccent
          )
        }
        Box(
          modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = if (isCurrent || isResolving) 0.44f else 0.24f)),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = Icons.Rounded.PlayArrow,
            contentDescription = null,
            tint = if (isCurrent && isPlaying) AurixTealAccent else Color.White,
            modifier = Modifier.size(22.dp)
          )
        }
      }

      // Title (with Noto Sans Khmer mixed fallback) & Artist
      Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(2.dp)
      ) {
        Text(
          text = buildMixedKhmerText(item.title),
          style = MaterialTheme.typography.bodyMedium.copy(
            fontWeight = FontWeight.Bold,
            fontSize = 14.5.sp,
            lineHeight = 21.sp
          ),
          color = if (isCurrent) GoldPlanetAccent else MaterialTheme.colorScheme.onSurface,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis
        )
        Text(
          text = buildMixedKhmerText(item.artist),
          style = MaterialTheme.typography.bodySmall.copy(
            fontSize = 12.5.sp,
            lineHeight = 17.sp
          ),
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
      }

      // Context badge (↑ places gained, New, or number of countries)
      TopMusicBadgePill(badge = item.badge, darkTheme = darkTheme)

      // Expand indicator
      Icon(
        imageVector = Icons.Rounded.ExpandMore,
        contentDescription = stringResource(id = R.string.top_music_expand_cd),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
          .size(20.dp)
          .rotate(chevronRotation)
      )
    }

    // Expanded Country Breakdown & Explainable Regional Score
    if (expanded) {
      Spacer(modifier = Modifier.height(12.dp))
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(1.dp)
          .background(
            if (darkTheme) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f)
          )
      )
      Spacer(modifier = Modifier.height(10.dp))

      // Country rank chips: flag + #rank + movement
      FlowRow(
        modifier = Modifier
          .fillMaxWidth()
          .testTag("top_music_country_breakdown_${item.videoId}"),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        item.countryBreakdowns.forEach { cb ->
          CountryRankChip(
            breakdown = cb,
            showSmoothed = item.chartType == com.example.topmusic.TopMusicChartType.WEEKLY_TOP_SONGS,
            darkTheme = darkTheme
          )
        }
      }

      Spacer(modifier = Modifier.height(10.dp))

      // Explainable Score Panel + Play CTA
      val exp = item.scoreExplanation
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(14.dp))
          .background(
            if (darkTheme) Color.Black.copy(alpha = 0.28f) else Color(0xFFF3F6FC)
          )
          .border(
            1.dp,
            GoldPlanetAccent.copy(alpha = if (darkTheme) 0.24f else 0.35f),
            RoundedCornerShape(14.dp)
          )
          .padding(12.dp)
          .testTag("top_music_score_explanation_${item.videoId}"),
        verticalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = if (exp.isKhmerDirect) {
              "Cambodia Direct Rank"
            } else {
              "Regional Score"
            },
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface
          )
          Text(
            text = if (exp.isKhmerDirect) {
              "#${exp.khmerDirectRank} KH"
            } else {
              "${formatDecimal(exp.finalScore)} pts"
            },
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
            color = if (darkTheme) GoldPlanetAccent else GoldPlanetDeep
          )
        }

        val formulaText = if (exp.isKhmerDirect) {
          "Cambodia rank #${exp.khmerDirectRank} directly (no cross-country bonus)."
        } else {
          val countryContrib = item.countryBreakdowns.joinToString(" + ") {
            "${it.flagEmoji} ${formatDecimal(it.points)}"
          }
          val bonusNote = if (exp.countriesCharting > 1) {
            " × ${formatMultiplier(exp.multiplier)} (${exp.countriesCharting} countries)"
          } else {
            " × 1.00 (1 country)"
          }
          "$countryContrib = ${formatDecimal(exp.basePoints)}$bonusNote = ${formatDecimal(exp.finalScore)}"
        }
        Text(
          text = formulaText,
          style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp, lineHeight = 16.sp),
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(2.dp))

        Button(
          onClick = onPlayClick,
          shape = CircleShape,
          colors = ButtonDefaults.buttonColors(
            containerColor = GoldPlanetAccent,
            contentColor = Color(0xFF1A1204)
          ),
          contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
          modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .testTag("top_music_play_button_${item.videoId}")
        ) {
          Icon(
            imageVector = Icons.Rounded.PlayArrow,
            contentDescription = null,
            modifier = Modifier.size(18.dp)
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = stringResource(id = R.string.top_music_play_track),
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold)
          )
        }
      }
    }
  }
}

@Composable
private fun TopMusicBadgePill(
  badge: TopMusicBadge,
  darkTheme: Boolean
) {
  val (label, tint, bg) = when (badge) {
    is TopMusicBadge.PlacesGained -> Triple(
      "↑ ${badge.places}",
      RisingGreen,
      RisingGreen.copy(alpha = 0.16f)
    )
    TopMusicBadge.NewEntry -> Triple(
      stringResource(id = R.string.top_music_badge_new),
      AurixTealAccent,
      AurixTealAccent.copy(alpha = 0.16f)
    )
    is TopMusicBadge.CountryCount -> {
      val text = if (badge.count == 1) {
        stringResource(id = R.string.top_music_badge_countries_one)
      } else {
        stringResource(id = R.string.top_music_badge_countries_many, badge.count)
      }
      Triple(
        text,
        if (darkTheme) GoldPlanetAccent else GoldPlanetDeep,
        GoldPlanetAccent.copy(alpha = 0.16f)
      )
    }
    is TopMusicBadge.KhmerRankMove -> {
      val move = badge.movement
      when {
        move == null -> Triple(
          stringResource(id = R.string.top_music_badge_new),
          AurixTealAccent,
          AurixTealAccent.copy(alpha = 0.16f)
        )
        move > 0 -> Triple("↑ $move", RisingGreen, RisingGreen.copy(alpha = 0.16f))
        move < 0 -> Triple("↓ ${-move}", DropCoral, DropCoral.copy(alpha = 0.14f))
        else -> Triple("—", MaterialTheme.colorScheme.onSurfaceVariant, Color.Gray.copy(alpha = 0.15f))
      }
    }
  }

  Surface(
    shape = CircleShape,
    color = bg,
    border = BorderStroke(1.dp, tint.copy(alpha = 0.38f))
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall.copy(
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold
      ),
      color = tint,
      maxLines = 1,
      modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
    )
  }
}

@Composable
private fun CountryRankChip(
  breakdown: CountryRankBreakdown,
  showSmoothed: Boolean,
  darkTheme: Boolean
) {
  val moveText: String
  val moveColor: Color
  when {
    breakdown.isNewEntry -> {
      moveText = "NEW"
      moveColor = AurixTealAccent
    }
    (breakdown.movement ?: 0) > 0 -> {
      moveText = "▲+${breakdown.movement}"
      moveColor = RisingGreen
    }
    (breakdown.movement ?: 0) < 0 -> {
      moveText = "▼${breakdown.movement}"
      moveColor = DropCoral
    }
    else -> {
      moveText = "—"
      moveColor = MaterialTheme.colorScheme.onSurfaceVariant
    }
  }

  val rankText = if (showSmoothed && breakdown.smoothedRank != breakdown.rankToday.toDouble()) {
    "#${breakdown.rankToday} (avg #${formatDecimal(breakdown.smoothedRank)})"
  } else {
    "#${breakdown.rankToday}"
  }

  Row(
    modifier = Modifier
      .clip(RoundedCornerShape(12.dp))
      .background(if (darkTheme) Color.White.copy(alpha = 0.07f) else Color.White)
      .border(
        1.dp,
        if (darkTheme) Color.White.copy(alpha = 0.12f) else Color(0xFFDCE4F2),
        RoundedCornerShape(12.dp)
      )
      .padding(horizontal = 10.dp, vertical = 6.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(5.dp)
  ) {
    Text(text = breakdown.flagEmoji, fontSize = 13.sp)
    Text(
      text = "${breakdown.countryCode} $rankText",
      style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 11.5.sp),
      color = MaterialTheme.colorScheme.onSurface
    )
    Text(
      text = moveText,
      style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold, fontSize = 11.sp),
      color = moveColor
    )
  }
}

@Composable
private fun TopMusicLoadingSkeleton(
  darkTheme: Boolean,
  reducedMotion: Boolean,
  bottomPadding: androidx.compose.ui.unit.Dp
) {
  val shimmerShift = if (reducedMotion) {
    0.4f
  } else {
    val transition = rememberInfiniteTransition(label = "top_music_skeleton")
    val anim by transition.animateFloat(
      initialValue = -0.3f,
      targetValue = 1.3f,
      animationSpec = infiniteRepeatable(
        animation = tween(durationMillis = 1200, easing = LinearEasing),
        repeatMode = RepeatMode.Restart
      ),
      label = "top_music_shimmer_shift"
    )
    anim
  }
  val base = if (darkTheme) Color(0xFF1A2338) else Color(0xFFDDE5F4)
  val highlight = if (darkTheme) Color(0xFF283552) else Color(0xFFF3F7FF)

  LazyColumn(
    modifier = Modifier
      .fillMaxSize()
      .testTag("top_music_loading_skeleton"),
    verticalArrangement = Arrangement.spacedBy(10.dp),
    contentPadding = PaddingValues(bottom = bottomPadding)
  ) {
    items(6) {
      val brush = Brush.linearGradient(
        colors = listOf(base, highlight, base),
        start = Offset(x = shimmerShift * 300f - 200f, y = 0f),
        end = Offset(x = shimmerShift * 300f + 200f, y = 120f)
      )
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(20.dp))
          .background(if (darkTheme) Color(0xFF12192A).copy(alpha = 0.72f) else Color.White.copy(alpha = 0.75f))
          .border(1.dp, Color.White.copy(alpha = if (darkTheme) 0.08f else 0.9f), RoundedCornerShape(20.dp))
          .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        Box(
          modifier = Modifier
            .size(28.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(brush)
        )
        Box(
          modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(brush)
        )
        Column(
          modifier = Modifier.weight(1f),
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Box(
            modifier = Modifier
              .fillMaxWidth(0.72f)
              .height(14.dp)
              .clip(CircleShape)
              .background(brush)
          )
          Box(
            modifier = Modifier
              .fillMaxWidth(0.45f)
              .height(11.dp)
              .clip(CircleShape)
              .background(brush)
          )
        }
        Box(
          modifier = Modifier
            .width(56.dp)
            .height(22.dp)
            .clip(CircleShape)
            .background(brush)
        )
      }
    }
  }
}

@Composable
private fun TopMusicEmptyStateCard(
  searchQuery: String,
  selectedRegion: TopMusicRegion,
  darkTheme: Boolean,
  onTryGlobal: () -> Unit,
  onClearSearch: () -> Unit
) {
  GlassCard(
    darkTheme = darkTheme,
    cornerRadius = 24.dp,
    modifier = Modifier
      .fillMaxWidth()
      .testTag("top_music_empty_state")
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(22.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      Box(
        modifier = Modifier
          .size(50.dp)
          .clip(CircleShape)
          .background(GoldPlanetAccent.copy(alpha = 0.16f))
          .border(1.dp, GoldPlanetAccent.copy(alpha = 0.40f), CircleShape),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = if (searchQuery.isNotBlank()) Icons.Rounded.SearchOff else Icons.Rounded.AutoAwesome,
          contentDescription = null,
          tint = GoldPlanetAccent,
          modifier = Modifier.size(24.dp)
        )
      }
      Text(
        text = stringResource(id = R.string.top_music_empty_title),
        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center
      )
      Text(
        text = stringResource(id = R.string.top_music_empty_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
      )
      Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        if (searchQuery.isNotBlank()) {
          Button(
            onClick = onClearSearch,
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
              containerColor = GoldPlanetAccent,
              contentColor = Color(0xFF1A1204)
            ),
            modifier = Modifier.defaultMinSize(minHeight = 48.dp)
          ) {
            Text(text = "Clear search", fontWeight = FontWeight.Bold)
          }
        }
        if (selectedRegion != TopMusicRegion.GLOBAL) {
          Button(
            onClick = onTryGlobal,
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
              containerColor = if (darkTheme) Color.White.copy(alpha = 0.12f) else Color(0xFFE2E8F0),
              contentColor = MaterialTheme.colorScheme.onSurface
            ),
            modifier = Modifier.defaultMinSize(minHeight = 48.dp)
          ) {
            Text(text = "Try Global", fontWeight = FontWeight.Bold)
          }
        }
      }
    }
  }
}

private fun formatDecimal(value: Double): String {
  val roundedInt = value.toInt()
  return if (kotlin.math.abs(value - roundedInt) < 0.001) {
    roundedInt.toString()
  } else {
    String.format(Locale.US, "%.1f", value)
  }
}

private fun formatMultiplier(value: Double): String {
  return String.format(Locale.US, "%.2f", value)
}
