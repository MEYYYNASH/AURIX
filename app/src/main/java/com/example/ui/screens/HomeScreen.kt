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
import com.example.R
import com.example.model.Song
import com.example.recommendation.*
import com.example.ui.theme.HeartRed
import com.example.viewmodel.DailyFeedCategory
import java.util.Calendar

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
  artistMixes: List<ArtistMix> = emptyList(),
  myMixes: List<PersonalMix> = emptyList(),
  dailyDiscovery: PersonalMix? = null,
  favoritesMix: PersonalMix? = null,
  newForYouItems: List<NewForYouItem> = emptyList(),
  recentPlayEntries: List<RecentPlayEntry> = emptyList(),
  recentlyAddedCount: Int = 0,
  onOpenInsights: () -> Unit = {},
  onOpenDiscoverWeekly: () -> Unit = {},
  onOpenMoodPlaylist: (MoodCategory) -> Unit = {},
  onOpenArtistMix: (ArtistMix) -> Unit = {},
  onOpenPersonalMix: (PersonalMix) -> Unit = {},
  onPlayTracks: (List<Song>) -> Unit = {},
  onShuffleTracks: (List<Song>) -> Unit = {},
  onSaveMix: (String, List<Song>) -> Unit = { _, _ -> },
  onShareMix: (String, String, List<Song>) -> Unit = { _, _, _ -> },
  onRefreshMixes: () -> Unit = {},
  onOpenNewForYouItem: (NewForYouItem) -> Unit = {},
  onRemoveRecentSong: (Song) -> Unit = {},
  onOpenDailyDiscovery: () -> Unit = {},
  onOpenFavoritesMix: () -> Unit = {},
  onOpenRecentlyAdded: () -> Unit = {},
  onSongClick: (Song, List<Song>) -> Unit,
  onFavoriteClick: (Song) -> Unit
) {
  var selectedNewFilter by rememberSaveable { mutableStateOf(NewForYouFilter.ALL) }
  var showQuickAccessSection by rememberSaveable { mutableStateOf(true) }
  var showArtistMixesSection by rememberSaveable { mutableStateOf(true) }
  var showMyMixSection by rememberSaveable { mutableStateOf(true) }
  var showNewForYouSection by rememberSaveable { mutableStateOf(true) }
  var showRecentPlaySection by rememberSaveable { mutableStateOf(true) }
  var showMoodsSection by rememberSaveable { mutableStateOf(true) }
  var showMadeForYouSection by rememberSaveable { mutableStateOf(true) }
  var showBecauseSection by rememberSaveable { mutableStateOf(true) }
  var showTrendingSection by rememberSaveable { mutableStateOf(true) }

  val sectionEnter = remember(reducedMotion) {
    if (reducedMotion) fadeIn(snap()) else expandVertically(tween(240)) + fadeIn(tween(200))
  }
  val sectionExit = remember(reducedMotion) {
    if (reducedMotion) fadeOut(snap()) else shrinkVertically(tween(200)) + fadeOut(tween(150))
  }

  val greetingRes = remember {
    when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
      in 5..11 -> R.string.home_greeting_morning
      in 12..16 -> R.string.home_greeting_afternoon
      in 17..21 -> R.string.home_greeting_evening
      else -> R.string.home_greeting_night
    }
  }

  val filteredNewItems = remember(newForYouItems, selectedNewFilter) {
    when (selectedNewFilter) {
      NewForYouFilter.ALL -> newForYouItems
      NewForYouFilter.SONGS -> newForYouItems.filter { it.song != null }
      NewForYouFilter.ALBUMS -> newForYouItems.filter { it.album != null }
      NewForYouFilter.ARTIST_RELEASES -> newForYouItems.filter { it.isFromFavoriteArtist }
    }
  }

  val trendingSongs = remember(featuredTracks, tiktokViralChartSongs, selectedFeed) {
    if (selectedFeed == DailyFeedCategory.TIKTOK_VIRAL && tiktokViralChartSongs.isNotEmpty()) {
      (tiktokViralChartSongs + featuredTracks).distinctBy { it.id }
    } else {
      featuredTracks
    }
  }

  Box(
    modifier = Modifier
      .fillMaxSize()
      .windowInsetsPadding(WindowInsets.statusBars)
  ) {
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .padding(horizontal = 20.dp),
      verticalArrangement = Arrangement.spacedBy(20.dp),
      contentPadding = PaddingValues(
        top = 14.dp,
        bottom = if (currentSong != null) 205.dp else 135.dp
      )
    ) {
      // 0. Editorial Header with Personalized Time-of-Day Greeting + Actions
      item {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(
            modifier = Modifier
              .weight(1f)
              .padding(end = 8.dp),
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
                text = stringResource(id = greetingRes),
                style = MaterialTheme.typography.titleLarge.copy(
                  fontWeight = FontWeight.ExtraBold,
                  fontSize = 22.sp,
                  letterSpacing = 0.2.sp
                ),
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("home_greeting_title")
              )
              val statusText = if (dailyUpdatedJustNow) {
                stringResource(id = R.string.home_updated_just_now)
              } else {
                dailyUpdatedLabel
              }
              Text(
                text = "AURIX · $statusText",
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
              onClick = onOpenInsights,
              modifier = Modifier
                .minimumInteractiveComponentSize()
                .size(44.dp)
                .clip(CircleShape)
                .background(iconBg)
                .border(1.dp, iconBorder, CircleShape)
                .testTag("home_insights_header_button")
            ) {
              Icon(
                imageVector = Icons.Rounded.GraphicEq,
                contentDescription = stringResource(id = R.string.insights_pill_title),
                tint = AurixTealAccent,
                modifier = Modifier.size(20.dp)
              )
            }

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

      // Continue Listening Instant Resume Banner (shown whenever listening history exists)
      val latestPlay = recentPlayEntries.firstOrNull()
      if (latestPlay != null) {
        item {
          ContinueListeningBanner(
            entry = latestPlay,
            isCurrent = currentSong?.id == latestPlay.song.id,
            isPlaying = isPlaying,
            darkTheme = darkTheme,
            reducedMotion = reducedMotion,
            onResumeClick = {
              onSongClick(latestPlay.song, recentlyPlayed.ifEmpty { listOf(latestPlay.song) })
            }
          )
        }
      }

      // Compact Library & Music DNA Quick Access
      item {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          val quickAccessTitle = stringResource(id = R.string.home_quick_access_title)
          CleanSectionHeader(
            title = quickAccessTitle,
            isOpen = showQuickAccessSection,
            onToggle = { showQuickAccessSection = !showQuickAccessSection },
            toggleTestTag = "home_toggle_quick_access_button",
            darkTheme = darkTheme,
            reducedMotion = reducedMotion
          )
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
                  countLabel = "$localSongsCount",
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
                  countLabel = "$favoritesCount",
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

      // 1. ARTIST MIXES FOR YOU
      item {
        val isPersonalized = artistMixes.any { it.isPersonalized }
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .testTag("home_artist_mixes_section"),
          verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          CleanSectionHeader(
            title = stringResource(id = R.string.home_artist_mixes_title),
            subtitle = stringResource(
              id = if (isPersonalized) R.string.home_artist_mixes_sub else R.string.home_artist_mixes_starter_sub
            ),
            badgeText = if (artistMixes.isNotEmpty()) {
              stringResource(id = if (isPersonalized) R.string.mix_personalized_badge else R.string.mix_starter_badge)
            } else null,
            isOpen = showArtistMixesSection,
            onToggle = { showArtistMixesSection = !showArtistMixesSection },
            toggleTestTag = "home_toggle_artist_mixes_button",
            darkTheme = darkTheme,
            reducedMotion = reducedMotion
          )
          AnimatedVisibility(
            visible = showArtistMixesSection,
            enter = sectionEnter,
            exit = sectionExit
          ) {
            if (artistMixes.isEmpty()) {
              EmptyMixPlaceholderCard(
                message = stringResource(id = R.string.mix_empty_loading),
                darkTheme = darkTheme
              )
            } else {
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
              ) {
                artistMixes.forEach { mix ->
                  ArtistMixCard(
                    mix = mix,
                    darkTheme = darkTheme,
                    onOpenMix = { onOpenArtistMix(mix) },
                    onPlayMix = { onPlayTracks(mix.tracks) },
                    onShuffleMix = { onShuffleTracks(mix.tracks) }
                  )
                }
              }
            }
          }
        }
      }

      // 2. MY MIX (My Mix 1 .. My Mix 4)
      item {
        val isPersonalized = myMixes.any { it.isPersonalized }
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .testTag("home_my_mix_section"),
          verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          CleanSectionHeader(
            title = stringResource(id = R.string.home_my_mix_title),
            subtitle = stringResource(
              id = if (isPersonalized) R.string.home_my_mix_sub else R.string.home_my_mix_starter_sub
            ),
            actionLabel = if (myMixes.isNotEmpty()) stringResource(id = R.string.mix_refresh) else null,
            onActionClick = onRefreshMixes,
            actionTestTag = "home_refresh_my_mix_button",
            isOpen = showMyMixSection,
            onToggle = { showMyMixSection = !showMyMixSection },
            toggleTestTag = "home_toggle_my_mix_button",
            darkTheme = darkTheme,
            reducedMotion = reducedMotion
          )
          AnimatedVisibility(
            visible = showMyMixSection,
            enter = sectionEnter,
            exit = sectionExit
          ) {
            if (myMixes.isEmpty()) {
              EmptyMixPlaceholderCard(
                message = stringResource(id = R.string.mix_empty_loading),
                darkTheme = darkTheme
              )
            } else {
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
              ) {
                myMixes.forEach { mix ->
                  PersonalMixCard(
                    mix = mix,
                    darkTheme = darkTheme,
                    onOpenMix = { onOpenPersonalMix(mix) },
                    onPlayMix = { onPlayTracks(mix.tracks) },
                    onShuffleMix = { onShuffleTracks(mix.tracks) },
                    onSaveMix = { onSaveMix(mix.title, mix.tracks) },
                    onShareMix = { onShareMix(mix.title, mix.subtitle, mix.tracks) }
                  )
                }
              }
            }
          }
        }
      }

      // 3. NEW FOR YOU (All | Songs | Albums | Artists)
      item {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .testTag("home_new_for_you_section"),
          verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          CleanSectionHeader(
            title = stringResource(id = R.string.home_new_for_you_title),
            subtitle = stringResource(id = R.string.home_new_for_you_sub),
            isOpen = showNewForYouSection,
            onToggle = { showNewForYouSection = !showNewForYouSection },
            toggleTestTag = "home_toggle_new_for_you_button",
            darkTheme = darkTheme,
            reducedMotion = reducedMotion
          )
          AnimatedVisibility(
            visible = showNewForYouSection,
            enter = sectionEnter,
            exit = sectionExit
          ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                NewForYouFilter.entries.forEach { filter ->
                  val selected = filter == selectedNewFilter
                  val labelRes = when (filter) {
                    NewForYouFilter.ALL -> R.string.nfy_filter_all
                    NewForYouFilter.SONGS -> R.string.nfy_filter_songs
                    NewForYouFilter.ALBUMS -> R.string.nfy_filter_albums
                    NewForYouFilter.ARTIST_RELEASES -> R.string.nfy_filter_artists
                  }
                  val chipBg by animateColorAsState(
                    targetValue = when {
                      selected -> AurixTealAccent
                      darkTheme -> Color(0xFF151F30)
                      else -> Color.White.copy(alpha = 0.85f)
                    },
                    animationSpec = if (reducedMotion) snap() else tween(180),
                    label = "nfy_chip_${filter.name}"
                  )
                  Surface(
                    shape = CircleShape,
                    color = chipBg,
                    border = androidx.compose.foundation.BorderStroke(
                      1.dp,
                      if (selected) AurixTealAccent else if (darkTheme) Color(0xFF273348) else Color.White
                    ),
                    modifier = Modifier
                      .heightIn(min = 38.dp)
                      .clip(CircleShape)
                      .clickable { selectedNewFilter = filter }
                      .testTag("nfy_filter_chip_${filter.name.lowercase()}")
                  ) {
                    Text(
                      text = stringResource(id = labelRes),
                      style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold
                      ),
                      color = if (selected) Color(0xFF06201C) else MaterialTheme.colorScheme.onSurface,
                      modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                  }
                }
              }

              if (filteredNewItems.isEmpty()) {
                EmptyMixPlaceholderCard(
                  message = stringResource(id = R.string.nfy_empty_filter),
                  darkTheme = darkTheme
                )
              } else {
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                  horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                  filteredNewItems.forEach { item ->
                    NewForYouCard(
                      item = item,
                      darkTheme = darkTheme,
                      onClick = { onOpenNewForYouItem(item) }
                    )
                  }
                }
              }
            }
          }
        }
      }

      // 4. RECENT PLAY (Chronological Shelf with Timestamps & Instant Resume)
      if (recentPlayEntries.isNotEmpty() || recentlyPlayed.isNotEmpty()) {
        item {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .testTag("home_recent_play_section"),
            verticalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            CleanSectionHeader(
              title = stringResource(id = R.string.home_recent_play_title),
              subtitle = stringResource(id = R.string.home_recent_play_sub),
              actionLabel = stringResource(id = R.string.home_history),
              onActionClick = { onOpenLibraryTab(2) },
              actionTestTag = "home_recent_history_link",
              isOpen = showRecentPlaySection,
              onToggle = { showRecentPlaySection = !showRecentPlaySection },
              toggleTestTag = "home_toggle_recent_button",
              darkTheme = darkTheme,
              reducedMotion = reducedMotion
            )
            AnimatedVisibility(
              visible = showRecentPlaySection,
              enter = sectionEnter,
              exit = sectionExit
            ) {
              val entriesToShow = if (recentPlayEntries.isNotEmpty()) {
                recentPlayEntries
              } else {
                recentlyPlayed.take(10).map {
                  RecentPlayEntry(it, System.currentTimeMillis(), "Recent", 0f)
                }
              }
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
              ) {
                entriesToShow.forEach { entry ->
                  LaunchedEffect(entry.song.id) {
                    onCheckLyrics(entry.song)
                  }
                  RecentPlayHistoryCard(
                    entry = entry,
                    isCurrent = currentSong?.id == entry.song.id,
                    isPlaying = isPlaying,
                    hasLyrics = entry.song.id in verifiedLyricsIds,
                    darkTheme = darkTheme,
                    reducedMotion = reducedMotion,
                    onClick = { onSongClick(entry.song, recentlyPlayed.ifEmpty { listOf(entry.song) }) },
                    onRemoveClick = { onRemoveRecentSong(entry.song) }
                  )
                }
              }
            }
          }
        }
      }

      // MOOD MIX (Chill, Focus, Sleep, Happy, Sad, Romantic, Workout, Party, Night Drive, Rainy Day)
      item {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          CleanSectionHeader(
            title = stringResource(id = R.string.home_mood_mix_title),
            isOpen = showMoodsSection,
            onToggle = { showMoodsSection = !showMoodsSection },
            toggleTestTag = "home_toggle_discover_moods_button",
            darkTheme = darkTheme,
            reducedMotion = reducedMotion
          )
          AnimatedVisibility(
            visible = showMoodsSection,
            enter = sectionEnter,
            exit = sectionExit
          ) {
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
                    .width(148.dp)
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
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
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

      // MADE FOR YOU (Daily Discovery, Discover Weekly, Favorites Mix, Recently Added)
      item {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          CleanSectionHeader(
            title = stringResource(id = R.string.home_made_for_you_title),
            isOpen = showMadeForYouSection,
            onToggle = { showMadeForYouSection = !showMadeForYouSection },
            toggleTestTag = "home_toggle_made_for_you_button",
            darkTheme = darkTheme,
            reducedMotion = reducedMotion
          )
          AnimatedVisibility(
            visible = showMadeForYouSection,
            enter = sectionEnter,
            exit = sectionExit
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
              horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
              MadeForYouMiniCard(
                title = stringResource(id = R.string.home_daily_discovery_title),
                subtitle = dailyDiscovery?.subtitle ?: stringResource(id = R.string.mix_empty_loading),
                badge = dailyDiscovery?.badgeLabel ?: "DISCOVERY",
                icon = Icons.Rounded.Explore,
                accentColor = AurixTealAccent,
                darkTheme = darkTheme,
                testTag = "home_daily_discovery_card",
                onClick = onOpenDailyDiscovery
              )
              MadeForYouMiniCard(
                title = stringResource(id = R.string.home_discover_weekly_title),
                subtitle = if (discoverWeekly != null && discoverWeekly.tracks.isNotEmpty()) {
                  stringResource(
                    id = R.string.home_discover_weekly_ready,
                    discoverWeekly.tracks.size,
                    discoverWeekly.matchCount,
                    discoverWeekly.discoveryCount
                  )
                } else {
                  stringResource(id = R.string.home_discover_weekly_empty)
                },
                badge = discoverWeekly?.weekKey ?: "WEEKLY",
                icon = Icons.Rounded.AutoAwesome,
                accentColor = MaterialTheme.colorScheme.primary,
                darkTheme = darkTheme,
                testTag = "home_discover_weekly_card",
                onClick = onOpenDiscoverWeekly
              )
              if (favoritesMix != null) {
                MadeForYouMiniCard(
                  title = stringResource(id = R.string.home_favorites_mix_title),
                  subtitle = favoritesMix.subtitle,
                  badge = favoritesMix.badgeLabel,
                  icon = Icons.Rounded.Favorite,
                  accentColor = HeartRed,
                  darkTheme = darkTheme,
                  testTag = "home_favorites_mix_card",
                  onClick = onOpenFavoritesMix
                )
              }
              if (recentlyAddedCount > 0) {
                MadeForYouMiniCard(
                  title = stringResource(id = R.string.home_recently_added_title),
                  subtitle = stringResource(id = R.string.home_recently_added_sub, recentlyAddedCount),
                  badge = "LIBRARY",
                  icon = Icons.Rounded.LibraryMusic,
                  accentColor = Color(0xFF9F7AEA),
                  darkTheme = darkTheme,
                  testTag = "home_recently_added_card",
                  onClick = onOpenRecentlyAdded
                )
              }
            }
          }
        }
      }

      // BECAUSE YOU LISTEN (Smart Recommendations)
      if (smartRecommendations.isNotEmpty()) {
        item {
          Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            CleanSectionHeader(
              title = stringResource(id = R.string.home_smart_recs_title),
              isOpen = showBecauseSection,
              onToggle = { showBecauseSection = !showBecauseSection },
              toggleTestTag = "home_toggle_smart_recs_button",
              darkTheme = darkTheme,
              reducedMotion = reducedMotion
            )
            AnimatedVisibility(
              visible = showBecauseSection,
              enter = sectionEnter,
              exit = sectionExit
            ) {
              val recQueue = smartRecommendations.map { it.song }
              val previewRecs = smartRecommendations.take(4)
              Column(
                modifier = Modifier
                  .fillMaxWidth()
                  .testTag("home_smart_recommendations_card"),
                verticalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                previewRecs.forEach { rec ->
                  Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
                      modifier = Modifier.padding(horizontal = 14.dp)
                    )
                  }
                }
              }
            }
          }
        }
      }

      // UNIFIED TRENDING MUSIC & REGIONAL CHARTS (Zero Duplicate Headers)
      item {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .testTag("home_trending_music_section"),
          verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          CleanSectionHeader(
            title = stringResource(id = R.string.home_trending_music_title),
            subtitle = feedLocalizedSubtitle(selectedFeed),
            actionLabel = if (tiktokViralChartSongs.isNotEmpty()) stringResource(id = R.string.home_see_all) else null,
            onActionClick = onOpenAllTiktokViral,
            actionTestTag = "home_tiktok_viral_see_all",
            isOpen = showTrendingSection,
            onToggle = { showTrendingSection = !showTrendingSection },
            toggleTestTag = "home_toggle_daily_feeds_button",
            darkTheme = darkTheme,
            reducedMotion = reducedMotion
          )

          AnimatedVisibility(
            visible = showTrendingSection,
            enter = sectionEnter,
            exit = sectionExit
          ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
              Box(modifier = Modifier.fillMaxWidth()) {
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(end = 24.dp),
                  horizontalArrangement = Arrangement.spacedBy(6.dp)
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
                      horizontalArrangement = Arrangement.spacedBy(5.dp),
                      modifier = Modifier
                        .heightIn(min = 34.dp)
                        .clip(CircleShape)
                        .background(chipBg)
                        .border(1.dp, chipBorder, CircleShape)
                        .clickable { onSelectFeed(category) }
                        .testTag("home_feed_chip_${category.id}")
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                      Icon(
                        imageVector = feedCategoryIcon(category),
                        contentDescription = null,
                        tint = if (selected) Color(0xFF06201C) else AurixTealAccent,
                        modifier = Modifier.size(13.dp)
                      )
                      Text(
                        text = feedLocalizedLabel(category),
                        style = MaterialTheme.typography.bodySmall.copy(
                          fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                          fontSize = 12.sp,
                          lineHeight = 16.sp
                        ),
                        color = chipTextColor
                      )
                    }
                  }
                }
                Box(
                  modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .width(36.dp)
                    .fillMaxHeight()
                    .background(
                      Brush.horizontalGradient(
                        colors = listOf(Color.Transparent, if (darkTheme) Color(0xFF0D121F) else Color.White)
                      )
                    )
                )
              }

              val cardBgColor = if (darkTheme) Color(0xFF151F30) else Color.White.copy(alpha = 0.92f)
              val cardBorderColor = if (darkTheme) Color(0xFF273348) else Color.White
              val cardShape = RoundedCornerShape(24.dp)
              val cardState = when {
                isLoadingFeatured && trendingSongs.isEmpty() -> DailyCardState.LOADING
                trendingSongs.isEmpty() -> DailyCardState.ERROR
                else -> DailyCardState.LOADED
              }

              Box(
                modifier = Modifier
                  .fillMaxWidth()
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
                      (fadeIn(tween(320)) + slideInVertically(tween(320)) { it / 16 }) togetherWith fadeOut(tween(180))
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
                          .height(220.dp)
                          .clip(cardShape)
                          .background(cardBgColor)
                          .border(1.dp, cardBorderColor, cardShape)
                          .padding(horizontal = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                      ) {
                        Icon(
                          imageVector = Icons.Rounded.MusicNote,
                          contentDescription = null,
                          tint = AurixTealAccent,
                          modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                          text = featuredError ?: stringResource(id = R.string.home_refresh_error),
                          style = MaterialTheme.typography.bodyMedium,
                          color = MaterialTheme.colorScheme.onSurfaceVariant,
                          textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                          onClick = {
                            if (!isLoadingFeatured) onRefreshDailyFeed()
                            if (!isLoadingTiktokViralChart) onRefreshTiktokViral()
                          },
                          shape = CircleShape,
                          colors = ButtonDefaults.buttonColors(
                            containerColor = AurixTealAccent,
                            contentColor = Color(0xFF06201C)
                          ),
                          modifier = Modifier
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("home_daily_retry_button")
                        ) {
                          Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                          Spacer(modifier = Modifier.width(6.dp))
                          Text(stringResource(id = R.string.home_retry), fontWeight = FontWeight.Bold)
                        }
                      }
                    }

                    DailyCardState.LOADING -> {
                      Column(
                        modifier = Modifier
                          .fillMaxWidth()
                          .height(220.dp)
                          .clip(cardShape)
                          .background(cardBgColor)
                          .border(1.dp, cardBorderColor, cardShape)
                          .padding(horizontal = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                      ) {
                        OrbitLoader(
                          size = 64.dp,
                          cardColor = cardBgColor,
                          ringColor = if (darkTheme) OrbitRingDefault else Color(0xFF3A3C48),
                          planetColor = if (darkTheme) OrbitPlanetDefault else Color.White,
                          reducedMotion = reducedMotion,
                          modifier = Modifier.testTag("home_daily_orbit_loader")
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                          text = stringResource(id = R.string.home_refreshing_title),
                          style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                          color = MaterialTheme.colorScheme.onSurface,
                          textAlign = TextAlign.Center
                        )
                      }
                    }

                    DailyCardState.LOADED -> {
                      val displayTracks = trendingSongs.take(8)
                      Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                      ) {
                        displayTracks.forEachIndexed { idx, song ->
                          if (song.id.startsWith("yt_")) {
                            SongDownloadRow(
                              song = song,
                              index = idx + 1,
                              isPlaying = isPlaying,
                              isCurrent = currentSong?.id == song.id,
                              isResolving = resolvingSongId == song.id,
                              reducedMotion = reducedMotion,
                              darkTheme = darkTheme,
                              hasLyrics = song.id in verifiedLyricsIds,
                              downloadProgress = songDownloadProgress[song.id],
                              isDownloaded = song.id in downloadedSongIds,
                              onCheckLyrics = onCheckLyrics,
                              onSongClick = { onSongClick(song, trendingSongs) },
                              onFavoriteClick = { onFavoriteClick(song) },
                              onDownloadClick = { onYoutubeSongDownloadClick(song) },
                              modifier = Modifier.testTag("home_yt_song_row_${song.id}")
                            )
                          } else {
                            SongRow(
                              song = song,
                              isPlaying = isPlaying,
                              isCurrent = currentSong?.id == song.id,
                              reducedMotion = reducedMotion,
                              darkTheme = darkTheme,
                              hasLyrics = song.id in verifiedLyricsIds,
                              rank = idx + 1,
                              showSourceBadge = false,
                              onCheckLyrics = onCheckLyrics,
                              onSongClick = { onSongClick(song, trendingSongs) },
                              onFavoriteClick = { onFavoriteClick(song) }
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
}

@Composable
private fun CleanSectionHeader(
  title: String,
  subtitle: String? = null,
  badgeText: String? = null,
  actionLabel: String? = null,
  onActionClick: (() -> Unit)? = null,
  actionTestTag: String? = null,
  isOpen: Boolean,
  onToggle: () -> Unit,
  toggleTestTag: String,
  darkTheme: Boolean,
  reducedMotion: Boolean
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
  ) {
    Column(
      modifier = Modifier
        .weight(1f)
        .padding(end = 8.dp),
      verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        Text(
          text = title,
          style = MaterialTheme.typography.titleMedium.copy(
            fontWeight = FontWeight.ExtraBold,
            fontSize = 18.sp
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
              style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
              color = AurixTealAccent,
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
            )
          }
        }
      }
      if (!subtitle.isNullOrBlank()) {
        Text(
          text = subtitle,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
      }
    }

    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
      if (!actionLabel.isNullOrBlank() && onActionClick != null) {
        Text(
          text = actionLabel,
          style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
          color = AurixTealAccent,
          modifier = Modifier
            .clip(CircleShape)
            .clickable { onActionClick() }
            .then(if (actionTestTag != null) Modifier.testTag(actionTestTag) else Modifier)
            .padding(horizontal = 8.dp, vertical = 6.dp)
        )
      }
    }
  }
}

@Composable
private fun EmptyMixPlaceholderCard(
  message: String,
  darkTheme: Boolean
) {
  GlassCard(
    darkTheme = darkTheme,
    cornerRadius = 18.dp,
    modifier = Modifier.fillMaxWidth()
  ) {
    Text(
      text = message,
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(horizontal = 16.dp, vertical = 18.dp)
    )
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
