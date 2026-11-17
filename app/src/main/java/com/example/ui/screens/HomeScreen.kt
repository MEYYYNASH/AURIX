package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.example.recommendation.*
import com.example.ui.theme.HeartRed
import com.example.viewmodel.DailyFeedCategory

private enum class DailyCardState { LOADING, ERROR, LOADED }

@Composable
fun HomeScreen(
  featuredTracks: List<Song>,
  isLoadingFeatured: Boolean,
  featuredError: String?,
  popularYoutubeSongs: List<Song> = emptyList(),
  tiktokViralChartSongs: List<Song> = emptyList(),
  isLoadingTiktokViralChart: Boolean = false,
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
  resolvingSongId: String? = null,
  downloadedSongIds: Set<String> = emptySet(),
  songDownloadProgress: Map<String, Float> = emptyMap(),
  onCheckLyrics: (Song) -> Unit = {},
  onSelectFeed: (DailyFeedCategory) -> Unit,
  onRefreshDailyFeed: () -> Unit,
  onOpenSearch: () -> Unit,
  onOpenLibraryTab: (Int) -> Unit,
  onOpenAlbum: () -> Unit = {},
  onOpenAllTiktokViral: () -> Unit = {},
  onRefreshTiktokViral: () -> Unit = {},
  onYoutubeSongDownloadClick: (Song) -> Unit = {},
  smartRecommendations: List<RecommendedTrack> = emptyList(),
  discoverWeekly: DiscoverWeeklyPlaylist? = null,
  moodPlaylists: List<MoodPlaylist> = emptyList(),
  musicDna: MusicDnaProfile? = null,
  onOpenInsights: () -> Unit = {},
  onOpenDiscoverWeekly: () -> Unit = {},
  onOpenMoodPlaylist: (MoodCategory) -> Unit = {},
  onSongClick: (Song, List<Song>) -> Unit,
  onFavoriteClick: (Song) -> Unit
) {
  val heroSongs = remember(tiktokViralChartSongs, popularYoutubeSongs) {
    (tiktokViralChartSongs + popularYoutubeSongs)
      .distinctBy { it.id }
      .take(4)
  }
  val heroPagerState = rememberPagerState(pageCount = { heroSongs.size.coerceAtLeast(1) })
  var showSpotlightSection by rememberSaveable { mutableStateOf(true) }
  var showQuickAccessSection by rememberSaveable { mutableStateOf(true) }
  var showTiktokChartsSection by rememberSaveable { mutableStateOf(true) }
  var showDailyFeedsSection by rememberSaveable { mutableStateOf(true) }
  var showRecentlyPlayedSection by rememberSaveable { mutableStateOf(true) }
  var showDiscoverMoodsSection by rememberSaveable { mutableStateOf(true) }
  var showSmartRecsSection by rememberSaveable { mutableStateOf(true) }

  val sectionEnter = remember(reducedMotion) {
    if (reducedMotion) fadeIn(snap()) else expandVertically(tween(240)) + fadeIn(tween(200))
  }
  val sectionExit = remember(reducedMotion) {
    if (reducedMotion) fadeOut(snap()) else shrinkVertically(tween(200)) + fadeOut(tween(150))
  }

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
      // Clean Editorial Header with Indicator Dots + Refresh & Search Circles
      item {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(
            modifier = Modifier
              .weight(1f)
              .padding(end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
          ) {
            AurixBrandLogo(
              size = 42.dp,
              cornerRadius = 13.dp,
              animated = true,
              reducedMotion = reducedMotion
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
              Text(
                text = "AURIX",
                style = MaterialTheme.typography.titleLarge.copy(
                  fontWeight = FontWeight.ExtraBold,
                  fontSize = 23.sp,
                  letterSpacing = 0.5.sp
                ),
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                val activeDotIndex = if (heroSongs.size > 1) heroPagerState.currentPage % 2 else 0
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                  Box(
                    modifier = Modifier
                      .width(if (activeDotIndex == 0) 14.dp else 5.dp)
                      .height(4.dp)
                      .clip(CircleShape)
                      .background(
                        if (activeDotIndex == 0) MaterialTheme.colorScheme.onBackground
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                      )
                  )
                  Box(
                    modifier = Modifier
                      .width(if (activeDotIndex == 1) 14.dp else 5.dp)
                      .height(4.dp)
                      .clip(CircleShape)
                      .background(
                        if (activeDotIndex == 1) MaterialTheme.colorScheme.onBackground
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                      )
                  )
                }
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
                  size = 28.dp,
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

      // Featured Spotlight Hero Banner (Clean Rounded Visual Card)
      if (heroSongs.isNotEmpty()) {
        item {
          Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val spotlightTitle = stringResource(id = R.string.home_spotlight_title)
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text(
                text = spotlightTitle,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground
              )
              OpenCloseGlassPill(
                isOpen = showSpotlightSection,
                onToggle = { showSpotlightSection = !showSpotlightSection },
                darkTheme = darkTheme,
                reducedMotion = reducedMotion,
                testTag = "home_toggle_hero_button",
                contentDescription = stringResource(
                  id = if (showSpotlightSection) R.string.section_close_cd else R.string.section_open_cd,
                  spotlightTitle
                ),
                accentColor = AurixTealAccent
              )
            }
            AnimatedVisibility(
              visible = showSpotlightSection,
              enter = sectionEnter,
              exit = sectionExit
            ) {
              Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                HorizontalPager(
                  state = heroPagerState,
                  pageSpacing = 12.dp,
                  modifier = Modifier
                    .fillMaxWidth()
                    .testTag("home_hero_pager")
                ) { page ->
                  val song = heroSongs[page]
                  val isCurrentHero = currentSong?.id == song.id
                  val badgeText = "${stringResource(id = R.string.home_featured_badge)} · YT"
                  HeroBannerCard(
                    song = song,
                    badgeLabel = badgeText,
                    isCurrent = isCurrentHero,
                    isPlaying = isPlaying,
                    darkTheme = darkTheme,
                    reducedMotion = reducedMotion,
                    onClick = { onSongClick(song, heroSongs) }
                  )
                }
                if (heroSongs.size > 1) {
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    repeat(heroSongs.size) { index ->
                      val active = heroPagerState.currentPage == index
                      Box(
                        modifier = Modifier
                          .padding(horizontal = 3.dp)
                          .width(if (active) 16.dp else 6.dp)
                          .height(5.dp)
                          .clip(CircleShape)
                          .background(
                            if (active) AurixTealAccent
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.28f)
                          )
                      )
                    }
                  }
                }
              }
            }
          }
        }
      }

      // Slim Library Quick Access 2x2 Grid
      item {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          val quickAccessTitle = stringResource(id = R.string.home_quick_access_title)
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              text = quickAccessTitle,
              style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
              color = MaterialTheme.colorScheme.onBackground
            )
            OpenCloseGlassPill(
              isOpen = showQuickAccessSection,
              onToggle = { showQuickAccessSection = !showQuickAccessSection },
              darkTheme = darkTheme,
              reducedMotion = reducedMotion,
              testTag = "home_toggle_quick_access_button",
              contentDescription = stringResource(
                id = if (showQuickAccessSection) R.string.section_close_cd else R.string.section_open_cd,
                quickAccessTitle
              ),
              accentColor = AurixTealAccent
            )
          }
          AnimatedVisibility(
            visible = showQuickAccessSection,
            enter = sectionEnter,
            exit = sectionExit
          ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                  modifier = Modifier
                    .weight(1f)
                    .testTag("home_library_local_tile"),
                  onClick = { onOpenLibraryTab(0) }
                )
                HomeLibraryPill(
                  title = stringResource(id = R.string.home_favorites),
                  countLabel = if (favoritesCount == 1) "1" else "$favoritesCount",
                  icon = Icons.Rounded.Favorite,
                  iconTint = HeartRed,
                  darkTheme = darkTheme,
                  modifier = Modifier
                    .weight(1f)
                    .testTag("home_library_favorites_tile"),
                  onClick = { onOpenLibraryTab(1) }
                )
              }
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
              ) {
                HomeLibraryPill(
                  title = stringResource(id = R.string.library_tab_albums),
                  countLabel = stringResource(id = R.string.album_nav_label),
                  icon = Icons.Rounded.Album,
                  iconTint = Color(0xFFC4B5FF),
                  darkTheme = darkTheme,
                  modifier = Modifier
                    .weight(1f)
                    .testTag("home_album_tile"),
                  onClick = onOpenAlbum
                )
                HomeLibraryPill(
                  title = stringResource(id = R.string.insights_pill_title),
                  countLabel = musicDna?.takeIf { it.hasEnoughHistory }?.let { "${it.totalQualifiedPlays}" }
                    ?: stringResource(id = R.string.insights_pill_sub),
                  icon = Icons.Rounded.GraphicEq,
                  iconTint = AurixTealAccent,
                  darkTheme = darkTheme,
                  modifier = Modifier
                    .weight(1f)
                    .testTag("home_insights_tile"),
                  onClick = onOpenInsights
                )
              }
            }
          }
        }
      }

      // TikTok Viral Chart Section (NewPipe Extractor — Full YouTube Chart Tracks)
      item {
        val tiktokChartsTitle = stringResource(id = R.string.home_tiktok_viral_charts_title)
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .testTag("home_tiktok_viral_chart_section"),
          verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          HomeSectionHeader(
            title = tiktokChartsTitle,
            badgeText = stringResource(id = R.string.home_tiktok_newpipe_badge),
            seeAllLabel = stringResource(id = R.string.home_see_all),
            seeAllTestTag = "home_tiktok_viral_see_all",
            onSeeAllClick = onOpenAllTiktokViral,
            isExpanded = showTiktokChartsSection,
            onToggleExpand = { showTiktokChartsSection = !showTiktokChartsSection },
            toggleTestTag = "home_toggle_tiktok_charts_button",
            darkTheme = darkTheme,
            reducedMotion = reducedMotion
          )

          AnimatedVisibility(
            visible = showTiktokChartsSection,
            enter = sectionEnter,
            exit = sectionExit
          ) {
            GlassCard(
              darkTheme = darkTheme,
              cornerRadius = 24.dp,
              modifier = Modifier.fillMaxWidth()
            ) {
              if (tiktokViralChartSongs.isEmpty()) {
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 20.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                  if (isLoadingTiktokViralChart) {
                    OrbitLoader(
                      size = 36.dp,
                      cardColor = if (darkTheme) Color(0xFF151F30) else Color.White,
                      ringColor = if (darkTheme) OrbitRingDefault else Color(0xFF3A3C48),
                      planetColor = if (darkTheme) OrbitPlanetDefault else Color.White,
                      reducedMotion = reducedMotion
                    )
                    Text(
                      text = stringResource(id = R.string.home_tiktok_viral_loading),
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                      modifier = Modifier.weight(1f)
                    )
                  } else {
                    Icon(
                      imageVector = Icons.Rounded.Whatshot,
                      contentDescription = null,
                      tint = AurixTealAccent,
                      modifier = Modifier.size(26.dp)
                    )
                    Text(
                      text = stringResource(id = R.string.home_tiktok_viral_empty),
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                      modifier = Modifier.weight(1f)
                    )
                    TextButton(
                      onClick = onRefreshTiktokViral,
                      modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("home_tiktok_viral_retry_button")
                    ) {
                      Text(
                        text = stringResource(id = R.string.home_retry),
                        color = AurixTealAccent,
                        fontWeight = FontWeight.Bold
                      )
                    }
                  }
                }
              } else {
                val chartPreviewList = tiktokViralChartSongs.take(5)
                Column(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                  chartPreviewList.forEachIndexed { idx, song ->
                    PopularYoutubeSongRow(
                      song = song,
                      isCurrent = currentSong?.id == song.id,
                      isPlaying = isPlaying,
                      isResolving = resolvingSongId == song.id,
                      darkTheme = darkTheme,
                      reducedMotion = reducedMotion,
                      hasLyrics = song.id in verifiedLyricsIds,
                      downloadProgress = songDownloadProgress[song.id],
                      isDownloaded = song.id in downloadedSongIds,
                      onCheckLyrics = onCheckLyrics,
                      onClick = { onSongClick(song, tiktokViralChartSongs) },
                      onFavoriteClick = { onFavoriteClick(song) },
                      onDownloadClick = { onYoutubeSongDownloadClick(song) }
                    )
                    if (idx < chartPreviewList.lastIndex) {
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

      // Daily Feeds Section (TikTok Viral / Daily Top / Phonk & Edits / Khmer & Asia)
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
                    fontSize = 21.sp,
                    lineHeight = 28.sp
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
                      text = when (selectedFeed) {
                        DailyFeedCategory.TIKTOK_VIRAL -> stringResource(id = R.string.home_itunes_badge).uppercase()
                        DailyFeedCategory.DAILY_CHARTS, DailyFeedCategory.KHMER_ASIA -> stringResource(id = R.string.home_itunes_yt_badge).uppercase()
                        DailyFeedCategory.PHONK_EDITS -> stringResource(id = R.string.home_yt_badge).uppercase()
                      },
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

            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
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
              val feedHeaderTitle = feedLocalizedLabel(selectedFeed)
              OpenCloseGlassPill(
                isOpen = showDailyFeedsSection,
                onToggle = { showDailyFeedsSection = !showDailyFeedsSection },
                darkTheme = darkTheme,
                reducedMotion = reducedMotion,
                testTag = "home_toggle_daily_feeds_button",
                contentDescription = stringResource(
                  id = if (showDailyFeedsSection) R.string.section_close_cd else R.string.section_open_cd,
                  feedHeaderTitle
                ),
                accentColor = AurixTealAccent
              )
            }
          }

          AnimatedVisibility(
            visible = showDailyFeedsSection,
            enter = sectionEnter,
            exit = sectionExit
          ) {
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
      }

      // Unified Daily / iTunes TikTok Viral Recommendations Card
      if (showDailyFeedsSection) {
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
                Modifier.heightIn(min = 240.dp)
              } else {
                Modifier.height(260.dp)
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
                    .height(260.dp)
                    .padding(horizontal = 24.dp),
                  horizontalAlignment = Alignment.CenterHorizontally,
                  verticalArrangement = Arrangement.Center
                ) {
                  Box(
                    modifier = Modifier
                      .size(52.dp)
                      .clip(CircleShape)
                      .background(AurixTealAccent.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                  ) {
                    Icon(
                      imageVector = Icons.Rounded.MusicNote,
                      contentDescription = null,
                      tint = AurixTealAccent,
                      modifier = Modifier.size(26.dp)
                    )
                  }
                  Spacer(modifier = Modifier.height(12.dp))
                  Text(
                    text = featuredError ?: stringResource(id = R.string.home_refresh_error),
                    style = MaterialTheme.typography.bodyMedium.copy(
                      fontWeight = FontWeight.Medium,
                      lineHeight = 20.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                  )
                  Spacer(modifier = Modifier.height(14.dp))
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
                    .height(260.dp)
                    .padding(horizontal = 24.dp),
                  horizontalAlignment = Alignment.CenterHorizontally,
                  verticalArrangement = Arrangement.Center
                ) {
                  OrbitLoader(
                    size = 72.dp,
                    cardColor = cardBgColor,
                    ringColor = if (darkTheme) OrbitRingDefault else Color(0xFF3A3C48),
                    planetColor = if (darkTheme) OrbitPlanetDefault else Color.White,
                    reducedMotion = reducedMotion,
                    modifier = Modifier.testTag("home_daily_orbit_loader")
                  )
                  Spacer(modifier = Modifier.height(14.dp))
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
                val displayTracks = featuredTracks.take(10)
                Column(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                  displayTracks.forEachIndexed { idx, song ->
                    SongRow(
                      song = song,
                      isPlaying = isPlaying,
                      isCurrent = currentSong?.id == song.id,
                      reducedMotion = reducedMotion,
                      darkTheme = darkTheme,
                      hasLyrics = song.id in verifiedLyricsIds,
                      rank = idx + 1,
                      showSourceBadge = true,
                      onCheckLyrics = onCheckLyrics,
                      onSongClick = { onSongClick(song, featuredTracks) },
                      onFavoriteClick = { onFavoriteClick(song) }
                    )
                    if (idx < displayTracks.lastIndex) {
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

      // Recently Played Shelf (Deduplicated & Live Playing Ring)
      if (recentlyPlayed.isNotEmpty()) {
        item {
          Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val recentTitle = stringResource(id = R.string.home_jump_back_in)
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text(
                text = recentTitle,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, lineHeight = 24.sp),
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f)
              )
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                Text(
                  text = stringResource(id = R.string.home_history),
                  style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold, lineHeight = 20.sp),
                  color = MaterialTheme.colorScheme.primary,
                  modifier = Modifier
                    .clip(CircleShape)
                    .clickable { onOpenLibraryTab(2) }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                )
                OpenCloseGlassPill(
                  isOpen = showRecentlyPlayedSection,
                  onToggle = { showRecentlyPlayedSection = !showRecentlyPlayedSection },
                  darkTheme = darkTheme,
                  reducedMotion = reducedMotion,
                  testTag = "home_toggle_recent_button",
                  contentDescription = stringResource(
                    id = if (showRecentlyPlayedSection) R.string.section_close_cd else R.string.section_open_cd,
                    recentTitle
                  ),
                  accentColor = AurixTealAccent
                )
              }
            }

            AnimatedVisibility(
              visible = showRecentlyPlayedSection,
              enter = sectionEnter,
              exit = sectionExit
            ) {
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
                          modifier = Modifier
                            .fillMaxWidth()
                            .height(106.dp),
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
      }

      // Discover Weekly + Mood Playlists Section
      item {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
          val discoverMoodsTitle = stringResource(id = R.string.home_discover_moods_title)
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              text = discoverMoodsTitle,
              style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, lineHeight = 24.sp),
              color = MaterialTheme.colorScheme.onBackground
            )
            OpenCloseGlassPill(
              isOpen = showDiscoverMoodsSection,
              onToggle = { showDiscoverMoodsSection = !showDiscoverMoodsSection },
              darkTheme = darkTheme,
              reducedMotion = reducedMotion,
              testTag = "home_toggle_discover_moods_button",
              contentDescription = stringResource(
                id = if (showDiscoverMoodsSection) R.string.section_close_cd else R.string.section_open_cd,
                discoverMoodsTitle
              ),
              accentColor = AurixTealAccent
            )
          }

          AnimatedVisibility(
            visible = showDiscoverMoodsSection,
            enter = sectionEnter,
            exit = sectionExit
          ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
              GlassCard(
                darkTheme = darkTheme,
                cornerRadius = 22.dp,
                modifier = Modifier
                  .fillMaxWidth()
                  .clickable { onOpenDiscoverWeekly() }
                  .testTag("home_discover_weekly_card")
              ) {
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                  Box(
                    modifier = Modifier
                      .size(48.dp)
                      .clip(RoundedCornerShape(14.dp))
                      .background(
                        Brush.linearGradient(
                          listOf(AurixTealAccent.copy(alpha = 0.85f), MaterialTheme.colorScheme.primary)
                        )
                      ),
                    contentAlignment = Alignment.Center
                  ) {
                    Icon(
                      imageVector = Icons.Rounded.AutoAwesome,
                      contentDescription = null,
                      tint = Color(0xFF06201C),
                      modifier = Modifier.size(24.dp)
                    )
                  }
                  Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                      text = stringResource(id = R.string.home_discover_weekly_title),
                      style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                      color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                      text = if (discoverWeekly != null && discoverWeekly.tracks.isNotEmpty()) {
                        stringResource(
                          id = R.string.home_discover_weekly_ready,
                          discoverWeekly.tracks.size,
                          discoverWeekly.matchCount,
                          discoverWeekly.discoveryCount
                        )
                      } else {
                        stringResource(id = R.string.home_discover_weekly_empty)
                      },
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                      maxLines = 2,
                      overflow = TextOverflow.Ellipsis
                    )
                  }
                  Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                }
              }

              Text(
                text = stringResource(id = R.string.home_moods_title),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, lineHeight = 24.sp),
                color = MaterialTheme.colorScheme.onBackground
              )
              val playlistsToShow = if (moodPlaylists.isNotEmpty()) {
                moodPlaylists
              } else {
                MoodCategory.entries.map { MoodPlaylist(it, emptyList()) }
              }
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
              ) {
                playlistsToShow.forEach { playlist ->
                  val mood = playlist.mood
                  GlassCard(
                    darkTheme = darkTheme,
                    cornerRadius = 18.dp,
                    modifier = Modifier
                      .width(142.dp)
                      .clickable { onOpenMoodPlaylist(mood) }
                      .testTag("home_mood_card_${mood.id}")
                  ) {
                    Column(
                      modifier = Modifier.padding(12.dp),
                      verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                      Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                      ) {
                        Text(
                          text = mood.displayName,
                          style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                          color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                          text = "${playlist.tracks.size}",
                          style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                          color = AurixTealAccent
                        )
                      }
                      Text(
                        text = "${mood.bpmRange.first}–${mood.bpmRange.last} BPM",
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

      // Smart Recommendations Section (shown only once user has personalized matches)
      if (smartRecommendations.isNotEmpty()) {
        item {
          Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val smartTitle = stringResource(id = R.string.home_smart_recs_title)
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text(
                text = smartTitle,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, lineHeight = 24.sp),
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f)
              )
              OpenCloseGlassPill(
                isOpen = showSmartRecsSection,
                onToggle = { showSmartRecsSection = !showSmartRecsSection },
                darkTheme = darkTheme,
                reducedMotion = reducedMotion,
                testTag = "home_toggle_smart_recs_button",
                contentDescription = stringResource(
                  id = if (showSmartRecsSection) R.string.section_close_cd else R.string.section_open_cd,
                  smartTitle
                ),
                accentColor = AurixTealAccent
              )
            }
            AnimatedVisibility(
              visible = showSmartRecsSection,
              enter = sectionEnter,
              exit = sectionExit
            ) {
              GlassCard(
                darkTheme = darkTheme,
                cornerRadius = 22.dp,
                modifier = Modifier
                  .fillMaxWidth()
                  .testTag("home_smart_recommendations_card")
              ) {
                val recQueue = smartRecommendations.map { it.song }
                val previewRecs = smartRecommendations.take(4)
                Column(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                  previewRecs.forEachIndexed { idx, rec ->
                    Column {
                      SongRow(
                        song = rec.song,
                        isPlaying = isPlaying,
                        isCurrent = currentSong?.id == rec.song.id,
                        reducedMotion = reducedMotion,
                        darkTheme = darkTheme,
                        hasLyrics = rec.song.id in verifiedLyricsIds,
                        showSourceBadge = false,
                        onCheckLyrics = onCheckLyrics,
                        onSongClick = { onSongClick(rec.song, recQueue) },
                        onFavoriteClick = { onFavoriteClick(rec.song) }
                      )
                      Text(
                        text = rec.reason,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = AurixTealAccent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 70.dp, end = 12.dp, bottom = 6.dp)
                      )
                    }
                    if (idx < previewRecs.lastIndex) {
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
private fun HomeSectionHeader(
  title: String,
  seeAllLabel: String,
  seeAllTestTag: String,
  badgeText: String? = null,
  onSeeAllClick: () -> Unit,
  isExpanded: Boolean? = null,
  onToggleExpand: (() -> Unit)? = null,
  toggleTestTag: String? = null,
  darkTheme: Boolean = true,
  reducedMotion: Boolean = false
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      modifier = Modifier.weight(1f)
    ) {
      Text(
        text = title,
        style = MaterialTheme.typography.titleLarge.copy(
          fontWeight = FontWeight.ExtraBold,
          fontSize = 20.sp,
          lineHeight = 26.sp
        ),
        color = MaterialTheme.colorScheme.onBackground,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
      if (!badgeText.isNullOrBlank()) {
        Surface(
          shape = CircleShape,
          color = AurixTealAccent.copy(alpha = 0.14f)
        ) {
          Text(
            text = badgeText,
            style = MaterialTheme.typography.labelSmall.copy(
              fontWeight = FontWeight.Bold,
              fontSize = 10.sp
            ),
            color = AurixTealAccent,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
          )
        }
      }
    }

    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier
          .heightIn(min = 36.dp)
          .clip(CircleShape)
          .clickable { onSeeAllClick() }
          .testTag(seeAllTestTag)
          .padding(horizontal = 8.dp, vertical = 4.dp)
      ) {
        Text(
          text = seeAllLabel,
          style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Icon(
          imageVector = Icons.Rounded.ChevronRight,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.size(16.dp)
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
}

@Composable
private fun HeroBannerCard(
  song: Song,
  badgeLabel: String,
  isCurrent: Boolean,
  isPlaying: Boolean,
  darkTheme: Boolean,
  reducedMotion: Boolean,
  onClick: () -> Unit
) {
  val (c1, c2) = rememberArtworkColors(song.artworkUrl, song.title)
  val bannerShape = RoundedCornerShape(24.dp)
  val borderColor = if (darkTheme) Color.White.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.85f)

  Box(
    modifier = Modifier
      .fillMaxWidth()
      .height(178.dp)
      .clip(bannerShape)
      .background(
        Brush.linearGradient(
          colors = listOf(c1.copy(alpha = 0.85f), c2.copy(alpha = 0.78f))
        )
      )
      .border(1.dp, borderColor, bannerShape)
      .clickable { onClick() }
      .testTag("home_hero_banner_card")
  ) {
    if (song.artworkUrl.isNotBlank()) {
      AsyncImage(
        model = song.artworkUrl,
        contentDescription = song.title,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize()
      )
    }

    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(
          Brush.verticalGradient(
            colors = listOf(
              Color.Black.copy(alpha = 0.12f),
              Color.Black.copy(alpha = 0.36f),
              Color.Black.copy(alpha = 0.82f)
            )
          )
        )
    )

    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(16.dp),
      verticalArrangement = Arrangement.SpaceBetween
    ) {
      Surface(
        shape = CircleShape,
        color = Color.Black.copy(alpha = 0.45f),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.22f))
      ) {
        Text(
          text = badgeLabel,
          style = MaterialTheme.typography.labelSmall.copy(
            fontWeight = FontWeight.ExtraBold,
            fontSize = 10.sp,
            letterSpacing = 0.5.sp
          ),
          color = Color.White,
          modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
          Text(
            text = song.title,
            style = MaterialTheme.typography.titleMedium.copy(
              fontWeight = FontWeight.ExtraBold,
              fontSize = 18.sp
            ),
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
          Text(
            text = song.artist,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.82f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }

        Surface(
          shape = CircleShape,
          color = AurixTealAccent,
          modifier = Modifier
            .minimumInteractiveComponentSize()
            .size(44.dp)
            .clip(CircleShape)
            .clickable { onClick() }
        ) {
          Box(contentAlignment = Alignment.Center) {
            if (isCurrent && isPlaying) {
              EqualizerBars(
                isPlaying = true,
                color = Color(0xFF06201C),
                reducedMotion = reducedMotion,
                barWidth = 2.5.dp,
                maxHeight = 13.dp
              )
            } else {
              Icon(
                imageVector = Icons.Rounded.PlayArrow,
                contentDescription = stringResource(id = R.string.home_play_now),
                tint = Color(0xFF06201C),
                modifier = Modifier.size(24.dp)
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun PopularYoutubeSongRow(
  song: Song,
  isCurrent: Boolean,
  isPlaying: Boolean,
  isResolving: Boolean,
  darkTheme: Boolean,
  reducedMotion: Boolean,
  hasLyrics: Boolean,
  downloadProgress: Float?,
  isDownloaded: Boolean,
  onCheckLyrics: (Song) -> Unit,
  onClick: () -> Unit,
  onFavoriteClick: () -> Unit,
  onDownloadClick: () -> Unit
) {
  LaunchedEffect(song.id) {
    onCheckLyrics(song)
  }

  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(16.dp))
      .then(
        if (isCurrent) Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
        else Modifier
      )
      .clickable { onClick() }
      .padding(horizontal = 8.dp, vertical = 8.dp)
      .testTag("home_yt_song_row_${song.id}"),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Box(
      modifier = Modifier
        .size(52.dp)
        .clip(RoundedCornerShape(14.dp))
        .border(1.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(14.dp))
    ) {
      SongArtwork(song = song, modifier = Modifier.fillMaxSize(), cornerRadius = 14.dp)
      if (isResolving) {
        Box(
          modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f)),
          contentAlignment = Alignment.Center
        ) {
          CircularProgressIndicator(
            modifier = Modifier.size(20.dp),
            strokeWidth = 2.dp,
            color = AurixTealAccent
          )
        }
      } else if (isCurrent) {
        Box(
          modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f)),
          contentAlignment = Alignment.Center
        ) {
          EqualizerBars(
            isPlaying = isPlaying,
            color = AurixTealAccent,
            reducedMotion = reducedMotion,
            barWidth = 2.5.dp,
            maxHeight = 14.dp
          )
        }
      }
    }

    Spacer(modifier = Modifier.width(12.dp))

    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
          text = song.title,
          style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
          color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f, fill = false)
        )
        LyricsTag(
          visible = hasLyrics,
          size = LyricsTagSize.Mini,
          darkTheme = darkTheme,
          reducedMotion = reducedMotion,
          modifier = Modifier.padding(start = 6.dp)
        )
      }
      val metaText = if (song.durationSeconds > 0L) {
        "${song.artist} · ${song.durationSeconds / 60}:${String.format("%02d", song.durationSeconds % 60)}"
      } else {
        song.artist
      }
      Text(
        text = metaText,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
    }

    IconButton(
      onClick = onDownloadClick,
      modifier = Modifier
        .minimumInteractiveComponentSize()
        .size(40.dp)
        .testTag("home_yt_download_${song.id}")
    ) {
      when {
        downloadProgress != null -> {
          CircularProgressIndicator(
            progress = { downloadProgress.coerceIn(0.05f, 1f) },
            modifier = Modifier.size(18.dp),
            strokeWidth = 2.dp,
            color = AurixTealAccent
          )
        }
        isDownloaded -> {
          Icon(
            imageVector = Icons.Rounded.CheckCircle,
            contentDescription = stringResource(id = R.string.yt_saved_banner),
            tint = AurixTealAccent,
            modifier = Modifier.size(20.dp)
          )
        }
        else -> {
          Icon(
            imageVector = Icons.Rounded.Download,
            contentDescription = stringResource(id = R.string.yt_select_format_title),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
          )
        }
      }
    }

    IconButton(
      onClick = onFavoriteClick,
      modifier = Modifier
        .minimumInteractiveComponentSize()
        .size(40.dp)
        .testTag("home_yt_favorite_${song.id}")
    ) {
      Icon(
        imageVector = if (song.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
        contentDescription = stringResource(id = R.string.home_favorites),
        tint = if (song.isFavorite) HeartRed else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(20.dp)
      )
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
