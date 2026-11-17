package com.example.ui.screens

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.Song

private data class QueuedTrackEntry(
  val queueIndex: Int,
  val song: Song
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpNextQueueSheet(
  currentSong: Song?,
  queue: List<Song>,
  isPlaying: Boolean,
  reducedMotion: Boolean = false,
  onJumpToIndex: (Int) -> Unit,
  onMoveItem: (Int, Int) -> Unit,
  onRemoveItem: (Int) -> Unit,
  onClearUpcoming: () -> Unit,
  onDismiss: () -> Unit
) {
  val surfaceColor = MaterialTheme.colorScheme.surface
  val bgColor = MaterialTheme.colorScheme.background
  val isDark = surfaceColor.luminance() < 0.5f || isSystemInDarkTheme()
  val isAmoled = isDark && bgColor == Color.Black
  val accent = MaterialTheme.colorScheme.primary

  val sheetFill = when {
    isAmoled -> Color(0xFF0A0E17).copy(alpha = 0.96f)
    isDark -> surfaceColor.copy(alpha = 0.94f)
    else -> surfaceColor.copy(alpha = 0.97f)
  }
  val topBorderBrush = Brush.verticalGradient(
    colors = if (isDark) {
      listOf(Color.White.copy(alpha = 0.24f), Color.White.copy(alpha = 0.04f))
    } else {
      listOf(Color.White.copy(alpha = 0.95f), Color.Black.copy(alpha = 0.06f))
    }
  )
  val sheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

  val activeIndex = remember(queue, currentSong?.id) {
    if (currentSong == null) -1 else queue.indexOfFirst { it.id == currentSong.id }
  }
  val upcomingEntries = remember(queue, activeIndex) {
    val startIdx = if (activeIndex >= 0) activeIndex + 1 else 0
    if (startIdx >= queue.size) emptyList()
    else (startIdx until queue.size).map { idx -> QueuedTrackEntry(queueIndex = idx, song = queue[idx]) }
  }

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    shape = sheetShape,
    containerColor = Color.Transparent,
    contentColor = MaterialTheme.colorScheme.onSurface,
    scrimColor = Color.Black.copy(alpha = 0.54f),
    tonalElevation = 0.dp,
    dragHandle = null
  ) {
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .heightIn(max = 620.dp)
        .clip(sheetShape)
        .background(sheetFill)
        .background(
          Brush.radialGradient(
            colors = listOf(
              accent.copy(alpha = if (isDark) 0.16f else 0.09f),
              Color.Transparent
            ),
            center = Offset(260f, 100f),
            radius = 700f
          )
        )
        .border(1.dp, topBorderBrush, sheetShape)
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .navigationBarsPadding()
          .padding(horizontal = 20.dp)
          .padding(top = 10.dp, bottom = 18.dp)
      ) {
        // Drag handle
        Box(
          modifier = Modifier
            .align(Alignment.CenterHorizontally)
            .padding(bottom = 14.dp)
            .width(38.dp)
            .height(4.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f))
        )

        // Header
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f)
          ) {
            Surface(
              shape = RoundedCornerShape(14.dp),
              color = accent.copy(alpha = 0.16f),
              border = BorderStroke(1.dp, accent.copy(alpha = 0.45f)),
              modifier = Modifier.size(44.dp)
            ) {
              Box(contentAlignment = Alignment.Center) {
                Icon(
                  imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
                  contentDescription = null,
                  tint = accent,
                  modifier = Modifier.size(22.dp)
                )
              }
            }
            Column {
              Text(
                text = stringResource(id = R.string.queue_title),
                style = MaterialTheme.typography.titleMedium.copy(
                  fontSize = 18.sp,
                  fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onSurface
              )
              Text(
                text = stringResource(id = R.string.queue_subtitle, queue.size),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          }

          if (upcomingEntries.isNotEmpty()) {
            TextButton(
              onClick = onClearUpcoming,
              modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("queue_clear_button")
            ) {
              Text(
                text = stringResource(id = R.string.queue_clear_upcoming),
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = accent
              )
            }
          }
        }

        if (currentSong != null) {
          Spacer(modifier = Modifier.height(14.dp))
          Text(
            text = stringResource(id = R.string.queue_now_playing),
            style = MaterialTheme.typography.labelMedium.copy(
              fontWeight = FontWeight.Bold,
              letterSpacing = 0.4.sp
            ),
            color = accent
          )
          Spacer(modifier = Modifier.height(6.dp))
          Surface(
            shape = RoundedCornerShape(18.dp),
            color = accent.copy(alpha = if (isDark) 0.14f else 0.08f),
            border = BorderStroke(1.dp, accent.copy(alpha = 0.42f)),
            modifier = Modifier
              .fillMaxWidth()
              .testTag("queue_now_playing_card")
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
              SongArtwork(
                song = currentSong,
                modifier = Modifier.size(46.dp),
                cornerRadius = 12.dp,
                iconSize = 20.dp
              )
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = currentSong.title,
                  style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
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
              EqualizerBars(
                isPlaying = isPlaying,
                color = accent,
                reducedMotion = reducedMotion,
                barWidth = 3.dp,
                maxHeight = 14.dp
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(
          text = stringResource(id = R.string.queue_up_next),
          style = MaterialTheme.typography.labelMedium.copy(
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.4.sp
          ),
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))

        if (upcomingEntries.isEmpty()) {
          Surface(
            shape = RoundedCornerShape(20.dp),
            color = if (isDark) Color.White.copy(alpha = 0.04f) else Color.Black.copy(alpha = 0.03f),
            border = BorderStroke(
              1.dp,
              if (isDark) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.06f)
            ),
            modifier = Modifier
              .fillMaxWidth()
              .testTag("queue_empty_state")
          ) {
            Column(
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 24.dp),
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
              Icon(
                imageVector = Icons.Rounded.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.size(28.dp)
              )
              Text(
                text = stringResource(id = R.string.queue_empty_title),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
              )
              Text(
                text = stringResource(id = R.string.queue_empty_sub),
                style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
              )
            }
          }
        } else {
          val firstUpcomingIdx = upcomingEntries.first().queueIndex
          val lastUpcomingIdx = upcomingEntries.last().queueIndex
          val cardFill = if (isDark) Color.White.copy(alpha = 0.05f) else Color.Black.copy(alpha = 0.035f)
          val cardBorder = if (isDark) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.07f)

          LazyColumn(
            modifier = Modifier
              .fillMaxWidth()
              .heightIn(max = 360.dp)
              .testTag("queue_upcoming_list"),
            verticalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            items(
              items = upcomingEntries,
              key = { "${it.queueIndex}_${it.song.id}" }
            ) { entry ->
              val qIdx = entry.queueIndex
              val track = entry.song
              Surface(
                shape = RoundedCornerShape(16.dp),
                color = cardFill,
                border = BorderStroke(1.dp, cardBorder),
                modifier = Modifier
                  .fillMaxWidth()
                  .animateItem(
                    placementSpec = if (reducedMotion) snap() else spring(
                      dampingRatio = Spring.DampingRatioNoBouncy,
                      stiffness = Spring.StiffnessMediumLow
                    )
                  )
                  .clip(RoundedCornerShape(16.dp))
                  .clickable { onJumpToIndex(qIdx) }
                  .testTag("queue_item_$qIdx")
              ) {
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 10.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  SongArtwork(
                    song = track,
                    modifier = Modifier.size(42.dp),
                    cornerRadius = 11.dp,
                    iconSize = 18.dp
                  )
                  Spacer(modifier = Modifier.width(10.dp))
                  Column(modifier = Modifier.weight(1f)) {
                    Text(
                      text = track.title,
                      style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                      color = MaterialTheme.colorScheme.onSurface,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis
                    )
                    Text(
                      text = track.artist,
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis
                    )
                  }
                  IconButton(
                    onClick = { onMoveItem(qIdx, qIdx - 1) },
                    enabled = qIdx > firstUpcomingIdx,
                    modifier = Modifier
                      .minimumInteractiveComponentSize()
                      .testTag("queue_move_up_$qIdx")
                  ) {
                    Icon(
                      imageVector = Icons.Rounded.KeyboardArrowUp,
                      contentDescription = stringResource(id = R.string.queue_move_up_cd),
                      tint = if (qIdx > firstUpcomingIdx) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                      } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)
                      },
                      modifier = Modifier.size(20.dp)
                    )
                  }
                  IconButton(
                    onClick = { onMoveItem(qIdx, qIdx + 1) },
                    enabled = qIdx < lastUpcomingIdx,
                    modifier = Modifier
                      .minimumInteractiveComponentSize()
                      .testTag("queue_move_down_$qIdx")
                  ) {
                    Icon(
                      imageVector = Icons.Rounded.KeyboardArrowDown,
                      contentDescription = stringResource(id = R.string.queue_move_down_cd),
                      tint = if (qIdx < lastUpcomingIdx) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                      } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)
                      },
                      modifier = Modifier.size(20.dp)
                    )
                  }
                  IconButton(
                    onClick = { onRemoveItem(qIdx) },
                    modifier = Modifier
                      .minimumInteractiveComponentSize()
                      .testTag("queue_remove_$qIdx")
                  ) {
                    Icon(
                      imageVector = Icons.Rounded.Close,
                      contentDescription = stringResource(id = R.string.queue_remove_cd),
                      tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
                      modifier = Modifier.size(18.dp)
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
