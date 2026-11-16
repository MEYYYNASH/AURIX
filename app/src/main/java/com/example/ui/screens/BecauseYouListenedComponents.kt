package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.ThumbDown
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.Song
import com.example.recommendation.BecauseYouListenedResult
import com.example.recommendation.VocalRecommendationItem
import com.example.ui.theme.HeartRed

@Composable
fun BecauseYouListenedSection(
  result: BecauseYouListenedResult,
  currentSong: Song?,
  isPlaying: Boolean,
  darkTheme: Boolean,
  reducedMotion: Boolean,
  verifiedLyricsIds: Set<String> = emptySet(),
  onCheckLyrics: (Song) -> Unit = {},
  onPlayAll: (List<VocalRecommendationItem>) -> Unit,
  onPlayItem: (VocalRecommendationItem, List<VocalRecommendationItem>) -> Unit,
  onFeedback: (Song, Boolean) -> Unit,
  onOpenDetail: (Boolean) -> Unit
) {
  var selectedMixTab by rememberSaveable { mutableIntStateOf(0) } // 0 = Sad Vibes, 1 = Chill Mix
  val activeList = if (selectedMixTab == 0) result.sadVibes else result.chillMix

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .testTag("because_you_listened_section"),
    verticalArrangement = Arrangement.spacedBy(12.dp)
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
          horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          Icon(
            imageVector = Icons.Rounded.AutoAwesome,
            contentDescription = null,
            tint = AurixTealAccent,
            modifier = Modifier.size(18.dp)
          )
          Text(
            text = stringResource(id = R.string.because_section_title),
            style = MaterialTheme.typography.titleMedium.copy(
              fontWeight = FontWeight.ExtraBold,
              lineHeight = 24.sp
            ),
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }
        Text(
          text = stringResource(
            id = if (result.isColdStart) R.string.because_section_sub_cold else R.string.because_section_sub_warm
          ),
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
      }

      if (activeList.isNotEmpty()) {
        Button(
          onClick = { onPlayAll(activeList) },
          shape = CircleShape,
          colors = ButtonDefaults.buttonColors(
            containerColor = AurixTealAccent,
            contentColor = Color(0xFF06201C)
          ),
          contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
          modifier = Modifier
            .defaultMinSize(minHeight = 48.dp)
            .testTag("because_play_all_button")
        ) {
          Icon(
            imageVector = Icons.Rounded.PlayArrow,
            contentDescription = null,
            modifier = Modifier.size(16.dp)
          )
          Spacer(modifier = Modifier.width(4.dp))
          Text(
            text = stringResource(id = R.string.because_play_all),
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
          )
        }
      }
    }

    // Segmented Mix Switcher: Sad Vibes for You | Chill Mix for You
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      val tabs = listOf(
        0 to stringResource(id = R.string.because_tab_sad),
        1 to stringResource(id = R.string.because_tab_chill)
      )
      tabs.forEach { (idx, label) ->
        val selected = selectedMixTab == idx
        val chipBg by animateColorAsState(
          targetValue = when {
            selected -> MaterialTheme.colorScheme.primary
            darkTheme -> Color(0xFF151F30)
            else -> Color.White.copy(alpha = 0.86f)
          },
          animationSpec = if (reducedMotion) snap() else tween(200),
          label = "because_tab_bg_$idx"
        )
        val chipBorder by animateColorAsState(
          targetValue = when {
            selected -> MaterialTheme.colorScheme.primary
            darkTheme -> Color(0xFF273348)
            else -> Color.White
          },
          animationSpec = if (reducedMotion) snap() else tween(200),
          label = "because_tab_border_$idx"
        )
        val textColor = if (selected) {
          MaterialTheme.colorScheme.onPrimary
        } else {
          MaterialTheme.colorScheme.onSurface
        }

        Box(
          modifier = Modifier
            .weight(1f)
            .heightIn(min = 48.dp)
            .clip(CircleShape)
            .background(chipBg)
            .border(1.dp, chipBorder, CircleShape)
            .clickable { selectedMixTab = idx }
            .testTag(if (idx == 0) "because_tab_sad" else "because_tab_chill")
            .padding(horizontal = 12.dp, vertical = 10.dp),
          contentAlignment = Alignment.Center
        ) {
          Text(
            text = label,
            style = MaterialTheme.typography.labelLarge.copy(
              fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold
            ),
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }
      }

      if (activeList.isNotEmpty()) {
        Surface(
          onClick = { onOpenDetail(selectedMixTab == 0) },
          shape = CircleShape,
          color = if (darkTheme) Color(0xFF151F30) else Color.White.copy(alpha = 0.85f),
          border = BorderStroke(1.dp, if (darkTheme) Color(0xFF273348) else Color.White),
          modifier = Modifier
            .defaultMinSize(minHeight = 48.dp)
            .testTag("because_see_all_button")
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
          ) {
            Text(
              text = stringResource(id = R.string.because_see_all, activeList.size),
              style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
              color = AurixTealAccent,
              maxLines = 1
            )
            Icon(
              imageVector = Icons.Rounded.ChevronRight,
              contentDescription = null,
              tint = AurixTealAccent,
              modifier = Modifier.size(16.dp)
            )
          }
        }
      }
    }

    AnimatedContent(
      targetState = selectedMixTab to activeList,
      transitionSpec = {
        if (reducedMotion) fadeIn(snap()) togetherWith fadeOut(snap())
        else fadeIn(tween(220)) togetherWith fadeOut(tween(160))
      },
      label = "because_cards_switch"
    ) { (tabIdx, items) ->
      if (items.isEmpty()) {
        GlassCard(
          darkTheme = darkTheme,
          cornerRadius = 22.dp,
          modifier = Modifier.fillMaxWidth()
        ) {
          Text(
            text = stringResource(
              id = if (tabIdx == 0) R.string.because_empty_sad else R.string.because_empty_chill
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp)
          )
        }
      } else {
        LazyRow(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          items(
            items = items,
            key = { "${tabIdx}_${it.song.id}" }
          ) { item ->
            LaunchedEffect(item.song.id) {
              onCheckLyrics(item.song)
            }
            BecauseYouListenedCard(
              item = item,
              isCurrent = currentSong?.id == item.song.id,
              isPlaying = isPlaying,
              hasLyrics = item.song.id in verifiedLyricsIds,
              darkTheme = darkTheme,
              reducedMotion = reducedMotion,
              onPlayClick = { onPlayItem(item, items) },
              onThumbsUp = { onFeedback(item.song, true) },
              onThumbsDown = { onFeedback(item.song, false) }
            )
          }
        }
      }
    }
  }
}

@Composable
private fun BecauseYouListenedCard(
  item: VocalRecommendationItem,
  isCurrent: Boolean,
  isPlaying: Boolean,
  hasLyrics: Boolean,
  darkTheme: Boolean,
  reducedMotion: Boolean,
  onPlayClick: () -> Unit,
  onThumbsUp: () -> Unit,
  onThumbsDown: () -> Unit
) {
  val song = item.song
  GlassCard(
    darkTheme = darkTheme,
    cornerRadius = 22.dp,
    modifier = Modifier
      .width(214.dp)
      .then(
        if (isCurrent) Modifier.border(
          1.5.dp,
          MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
          RoundedCornerShape(22.dp)
        ) else Modifier
      )
      .clickable { onPlayClick() }
      .testTag("because_card_${song.id}")
  ) {
    Column(
      modifier = Modifier.padding(12.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      Box(modifier = Modifier.fillMaxWidth()) {
        SongArtwork(
          song = song,
          modifier = Modifier
            .fillMaxWidth()
            .height(124.dp),
          cornerRadius = 15.dp,
          iconSize = 32.dp
        )

        // Match % Badge
        Surface(
          shape = CircleShape,
          color = Color(0xFF06201C).copy(alpha = 0.86f),
          border = BorderStroke(1.dp, AurixTealAccent.copy(alpha = 0.72f)),
          modifier = Modifier
            .align(Alignment.TopStart)
            .padding(8.dp)
            .testTag("because_match_badge_${song.id}")
        ) {
          Text(
            text = stringResource(id = R.string.because_match_badge, item.matchPercent),
            style = MaterialTheme.typography.labelSmall.copy(
              fontWeight = FontWeight.ExtraBold,
              fontSize = 10.sp
            ),
            color = AurixTealAccent,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
          )
        }

        if (hasLyrics) {
          LyricsTag(
            visible = true,
            size = LyricsTagSize.Mini,
            darkTheme = darkTheme,
            reducedMotion = reducedMotion,
            modifier = Modifier
              .align(Alignment.TopEnd)
              .padding(8.dp)
          )
        }

        if (isCurrent) {
          Box(
            modifier = Modifier
              .align(Alignment.BottomEnd)
              .padding(8.dp)
              .clip(CircleShape)
              .background(Color.Black.copy(alpha = 0.72f))
              .padding(horizontal = 8.dp, vertical = 5.dp)
          ) {
            EqualizerBars(
              isPlaying = isPlaying,
              color = AurixTealAccent,
              reducedMotion = reducedMotion,
              barWidth = 2.5.dp,
              maxHeight = 11.dp
            )
          }
        }
      }

      Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
          text = song.title,
          style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
          color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
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
      }

      // Reason chip ("Because you listened to X")
      Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = if (darkTheme) 0.16f else 0.10f),
        modifier = Modifier.fillMaxWidth()
      ) {
        Text(
          text = item.reason,
          style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
          color = MaterialTheme.colorScheme.primary,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
        )
      }

      // Feedback controls (48dp touch targets) + quick play
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
          val liked = item.feedback == true
          val disliked = item.feedback == false
          IconButton(
            onClick = onThumbsUp,
            modifier = Modifier
              .minimumInteractiveComponentSize()
              .size(48.dp)
              .testTag("because_like_${song.id}")
          ) {
            Icon(
              imageVector = if (liked) Icons.Rounded.ThumbUp else Icons.Outlined.ThumbUp,
              contentDescription = stringResource(id = R.string.because_like_cd),
              tint = if (liked) AurixTealAccent else MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.size(18.dp)
            )
          }
          IconButton(
            onClick = onThumbsDown,
            modifier = Modifier
              .minimumInteractiveComponentSize()
              .size(48.dp)
              .testTag("because_dislike_${song.id}")
          ) {
            Icon(
              imageVector = if (disliked) Icons.Rounded.ThumbDown else Icons.Outlined.ThumbDown,
              contentDescription = stringResource(id = R.string.because_dislike_cd),
              tint = if (disliked) HeartRed else MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.size(18.dp)
            )
          }
        }

        Surface(
          onClick = onPlayClick,
          shape = CircleShape,
          color = if (isCurrent) MaterialTheme.colorScheme.primary else AurixTealAccent.copy(alpha = 0.22f),
          modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
        ) {
          Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(10.dp)) {
            Icon(
              imageVector = Icons.Rounded.PlayArrow,
              contentDescription = stringResource(id = R.string.because_play_all),
              tint = if (isCurrent) Color.White else AurixTealAccent,
              modifier = Modifier.size(20.dp)
            )
          }
        }
      }
    }
  }
}

@Composable
fun BecauseYouListenedDetailScreen(
  title: String,
  subtitle: String,
  items: List<VocalRecommendationItem>,
  emptyMessage: String,
  currentSong: Song?,
  isPlaying: Boolean,
  darkTheme: Boolean,
  reducedMotion: Boolean,
  verifiedLyricsIds: Set<String> = emptySet(),
  onBack: () -> Unit,
  onPlayAll: () -> Unit,
  onShuffleAll: () -> Unit,
  onCheckLyrics: (Song) -> Unit,
  onPlayItem: (VocalRecommendationItem) -> Unit,
  onFavoriteClick: (Song) -> Unit,
  onFeedback: (Song, Boolean) -> Unit
) {
  LazyColumn(
    modifier = Modifier
      .fillMaxSize()
      .padding(horizontal = 20.dp)
      .testTag("because_detail_screen"),
    verticalArrangement = Arrangement.spacedBy(12.dp),
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
            .size(48.dp)
            .clip(CircleShape)
            .background(iconBg)
            .border(1.dp, iconBorder, CircleShape)
            .testTag("because_detail_back_button")
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
            contentDescription = stringResource(id = R.string.insights_back_cd),
            tint = MaterialTheme.colorScheme.onSurface
          )
        }
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = stringResource(id = R.string.because_detail_badge),
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
      }
    }

    if (items.isNotEmpty()) {
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
              .testTag("because_detail_play_all")
          ) {
            Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(stringResource(id = R.string.because_play_all), fontWeight = FontWeight.Bold)
          }
          OutlinedButton(
            onClick = onShuffleAll,
            shape = CircleShape,
            border = BorderStroke(1.dp, AurixTealAccent.copy(alpha = 0.65f)),
            modifier = Modifier
              .weight(1f)
              .defaultMinSize(minHeight = 48.dp)
              .testTag("because_detail_shuffle_all")
          ) {
            Icon(Icons.Rounded.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp), tint = AurixTealAccent)
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = stringResource(id = R.string.moodflow_label),
              fontWeight = FontWeight.Bold,
              color = AurixTealAccent
            )
          }
        }
      }

      itemsIndexed(items, key = { _, item -> item.song.id }) { idx, item ->
        LaunchedEffect(item.song.id) {
          onCheckLyrics(item.song)
        }
        val song = item.song
        val isCurrent = currentSong?.id == song.id
        GlassCard(
          darkTheme = darkTheme,
          cornerRadius = 20.dp,
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)) {
            SongRow(
              song = song,
              isPlaying = isPlaying,
              isCurrent = isCurrent,
              reducedMotion = reducedMotion,
              darkTheme = darkTheme,
              hasLyrics = song.id in verifiedLyricsIds,
              rank = idx + 1,
              showSourceBadge = false,
              onCheckLyrics = onCheckLyrics,
              onSongClick = { onPlayItem(item) },
              onFavoriteClick = { onFavoriteClick(song) }
            )
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(start = 42.dp, end = 6.dp, bottom = 4.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                Surface(
                  shape = CircleShape,
                  color = AurixTealAccent.copy(alpha = 0.16f)
                ) {
                  Text(
                    text = stringResource(id = R.string.because_match_badge, item.matchPercent),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = AurixTealAccent,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                  )
                }
                Text(
                  text = item.reason,
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis
                )
              }
              Row {
                val liked = item.feedback == true
                val disliked = item.feedback == false
                IconButton(
                  onClick = { onFeedback(song, true) },
                  modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                  Icon(
                    imageVector = if (liked) Icons.Rounded.ThumbUp else Icons.Outlined.ThumbUp,
                    contentDescription = stringResource(id = R.string.because_like_cd),
                    tint = if (liked) AurixTealAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                  )
                }
                IconButton(
                  onClick = { onFeedback(song, false) },
                  modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                  Icon(
                    imageVector = if (disliked) Icons.Rounded.ThumbDown else Icons.Outlined.ThumbDown,
                    contentDescription = stringResource(id = R.string.because_dislike_cd),
                    tint = if (disliked) HeartRed else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                  )
                }
              }
            }
          }
        }
      }
    } else {
      item {
        GlassCard(darkTheme = darkTheme, cornerRadius = 22.dp, modifier = Modifier.fillMaxWidth()) {
          Text(
            text = emptyMessage,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(18.dp)
          )
        }
      }
    }
  }
}
