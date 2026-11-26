package com.example.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.R
import com.example.model.Song
import com.example.recommendation.SmartPlaylistEngine
import com.example.recommendation.TrackMetadataClassifier

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
  djAutoMixEnabled: Boolean = false,
  beatfadeSeconds: Int = 4,
  bpmMatchPercent: Int = 92,
  onToggleDjAutoMix: (Boolean) -> Unit = {},
  onOptimizeQueueForDj: () -> Unit = {},
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
  val djCyan = Color(0xFF43C7F4)
  val haptics = LocalHapticFeedback.current
  val density = LocalDensity.current

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

  // Drag-to-reorder state (reads translationY only inside graphicsLayer for smooth 60/120 Hz)
  var draggedQueueIndex by remember { mutableStateOf<Int?>(null) }
  var dragOffsetY by remember { mutableFloatStateOf(0f) }
  val rowStepPx = with(density) { 68.dp.toPx() }
  val latestUpcoming by rememberUpdatedState(upcomingEntries)
  val latestOnMove by rememberUpdatedState(onMoveItem)

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
        .heightIn(max = 680.dp)
        .clip(sheetShape)
        .background(sheetFill)
        .background(
          Brush.radialGradient(
            colors = listOf(
              (if (djAutoMixEnabled) djCyan else accent).copy(alpha = if (isDark) 0.16f else 0.09f),
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

          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
          ) {
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
            IconButton(
              onClick = onDismiss,
              modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("queue_close_button")
            ) {
              Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = stringResource(id = R.string.cd_close_sheet),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // DJ Auto-Mix Live Control Strip
        Surface(
          shape = RoundedCornerShape(18.dp),
          color = if (djAutoMixEnabled) {
            djCyan.copy(alpha = if (isDark) 0.14f else 0.10f)
          } else {
            if (isDark) Color.White.copy(alpha = 0.05f) else Color.Black.copy(alpha = 0.035f)
          },
          border = BorderStroke(
            1.dp,
            if (djAutoMixEnabled) djCyan.copy(alpha = 0.45f)
            else if (isDark) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.07f)
          ),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("queue_dj_automix_card")
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            Icon(
              imageVector = Icons.Rounded.GraphicEq,
              contentDescription = null,
              tint = if (djAutoMixEnabled) djCyan else MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.size(20.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = stringResource(id = R.string.dj_mode_title),
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
              )
              Text(
                text = if (djAutoMixEnabled) {
                  stringResource(id = R.string.dj_mode_beatfade_badge, beatfadeSeconds.coerceIn(2, 6), bpmMatchPercent)
                } else {
                  stringResource(id = R.string.dj_mode_subtitle)
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (djAutoMixEnabled) djCyan else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
            }
            if (upcomingEntries.size >= 2) {
              OutlinedButton(
                onClick = {
                  haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                  onOptimizeQueueForDj()
                },
                shape = CircleShape,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                modifier = Modifier
                  .defaultMinSize(minHeight = 40.dp)
                  .testTag("queue_dj_bpm_sort_button")
              ) {
                Icon(
                  imageVector = Icons.Rounded.AutoAwesome,
                  contentDescription = stringResource(id = R.string.dj_mode_optimize_queue_cd),
                  modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                  text = stringResource(id = R.string.dj_mode_optimize_queue),
                  style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                )
              }
            }
            Switch(
              checked = djAutoMixEnabled,
              onCheckedChange = { enabled ->
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onToggleDjAutoMix(enabled)
              },
              modifier = Modifier.testTag("queue_dj_automix_switch")
            )
          }
        }

        if (currentSong != null) {
          val curBpm = remember(currentSong.id, currentSong.bpm) {
            if (currentSong.bpm > 0) currentSong.bpm else TrackMetadataClassifier.enrich(currentSong).bpm
          }
          Spacer(modifier = Modifier.height(12.dp))
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
                  text = if (djAutoMixEnabled) "${currentSong.artist} · $curBpm BPM" else currentSong.artist,
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

        Spacer(modifier = Modifier.height(14.dp))
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = stringResource(id = R.string.queue_up_next),
            style = MaterialTheme.typography.labelMedium.copy(
              fontWeight = FontWeight.Bold,
              letterSpacing = 0.4.sp
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
          if (upcomingEntries.isNotEmpty()) {
            Text(
              text = stringResource(id = R.string.queue_gesture_hint),
              style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
              color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
          }
        }
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
          val cardFill = if (isDark) Color.White.copy(alpha = 0.05f) else Color.Black.copy(alpha = 0.035f)
          val cardBorder = if (isDark) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.07f)

          LazyColumn(
            modifier = Modifier
              .fillMaxWidth()
              .heightIn(max = 360.dp)
              .testTag("queue_upcoming_list"),
            verticalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            itemsIndexed(
              items = upcomingEntries,
              key = { _, item -> "${item.song.id}_${item.queueIndex}" }
            ) { listIdx, entry ->
              val qIdx = entry.queueIndex
              val track = entry.song
              val isBeingDragged = draggedQueueIndex == qIdx
              val prevSong = if (listIdx == 0) currentSong else upcomingEntries.getOrNull(listIdx - 1)?.song
              val transition = remember(djAutoMixEnabled, prevSong?.id, track.id) {
                if (djAutoMixEnabled && prevSong != null) {
                  SmartPlaylistEngine.describeTransition(prevSong, track)
                } else null
              }

              val dismissState = rememberSwipeToDismissBoxState(
                confirmValueChange = { value ->
                  if (value == SwipeToDismissBoxValue.EndToStart) {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onRemoveItem(qIdx)
                    true
                  } else {
                    false
                  }
                }
              )

              Column(
                modifier = Modifier
                  .fillMaxWidth()
                  .zIndex(if (isBeingDragged) 2f else 0f)
                  .graphicsLayer {
                    translationY = if (isBeingDragged) dragOffsetY else 0f
                    scaleX = if (isBeingDragged) 1.02f else 1f
                    scaleY = if (isBeingDragged) 1.02f else 1f
                  }
                  .animateItem(
                    placementSpec = if (reducedMotion) snap() else spring(
                      dampingRatio = Spring.DampingRatioNoBouncy,
                      stiffness = Spring.StiffnessMediumLow
                    )
                  ),
                verticalArrangement = Arrangement.spacedBy(4.dp)
              ) {
                if (transition != null) {
                  Row(
                    modifier = Modifier.padding(start = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                  ) {
                    Icon(
                      imageVector = Icons.Rounded.GraphicEq,
                      contentDescription = null,
                      tint = djCyan.copy(alpha = 0.85f),
                      modifier = Modifier.size(12.dp)
                    )
                    Text(
                      text = stringResource(
                        id = R.string.dj_mode_beatfade_badge,
                        transition.beatfadeSeconds,
                        transition.bpmMatchPercent
                      ),
                      style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold
                      ),
                      color = djCyan.copy(alpha = 0.90f)
                    )
                  }
                }

                SwipeToDismissBox(
                  state = dismissState,
                  enableDismissFromStartToEnd = false,
                  enableDismissFromEndToStart = draggedQueueIndex == null,
                  backgroundContent = {
                    val bgColorAnim by animateColorAsState(
                      targetValue = if (dismissState.targetValue == SwipeToDismissBoxValue.EndToStart) {
                        MaterialTheme.colorScheme.error.copy(alpha = 0.88f)
                      } else {
                        MaterialTheme.colorScheme.error.copy(alpha = 0.25f)
                      },
                      label = "queue_swipe_bg"
                    )
                    Box(
                      modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(16.dp))
                        .background(bgColorAnim)
                        .padding(horizontal = 18.dp),
                      contentAlignment = Alignment.CenterEnd
                    ) {
                      Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                      ) {
                        Text(
                          text = stringResource(id = R.string.queue_swipe_remove_label),
                          style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                          color = Color.White
                        )
                        Icon(
                          imageVector = Icons.Rounded.DeleteOutline,
                          contentDescription = stringResource(id = R.string.queue_remove_cd),
                          tint = Color.White,
                          modifier = Modifier.size(20.dp)
                        )
                      }
                    }
                  }
                ) {
                  Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (isBeingDragged) {
                      accent.copy(alpha = if (isDark) 0.24f else 0.14f)
                    } else {
                      cardFill
                    },
                    border = BorderStroke(
                      width = if (isBeingDragged) 1.5.dp else 1.dp,
                      color = if (isBeingDragged) accent.copy(alpha = 0.72f) else cardBorder
                    ),
                    modifier = Modifier
                      .fillMaxWidth()
                      .clip(RoundedCornerShape(16.dp))
                      .clickable { onJumpToIndex(qIdx) }
                      .testTag("queue_item_$qIdx")
                  ) {
                    Row(
                      modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 6.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                      verticalAlignment = Alignment.CenterVertically
                    ) {
                      // Dedicated drag-to-reorder handle (48dp touch target)
                      Box(
                        modifier = Modifier
                          .size(44.dp)
                          .pointerInput(qIdx) {
                            detectDragGestures(
                              onDragStart = {
                                draggedQueueIndex = qIdx
                                dragOffsetY = 0f
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                              },
                              onDragEnd = {
                                draggedQueueIndex = null
                                dragOffsetY = 0f
                              },
                              onDragCancel = {
                                draggedQueueIndex = null
                                dragOffsetY = 0f
                              },
                              onDrag = { change, dragAmount ->
                                change.consume()
                                val currentDragIdx = draggedQueueIndex ?: return@detectDragGestures
                                val entries = latestUpcoming
                                if (entries.size <= 1) return@detectDragGestures
                                val minIdx = entries.first().queueIndex
                                val maxIdx = entries.last().queueIndex
                                dragOffsetY += dragAmount.y
                                if (dragOffsetY > rowStepPx * 0.72f && currentDragIdx < maxIdx) {
                                  val target = currentDragIdx + 1
                                  latestOnMove(currentDragIdx, target)
                                  draggedQueueIndex = target
                                  dragOffsetY -= rowStepPx
                                  haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                } else if (dragOffsetY < -rowStepPx * 0.72f && currentDragIdx > minIdx) {
                                  val target = currentDragIdx - 1
                                  latestOnMove(currentDragIdx, target)
                                  draggedQueueIndex = target
                                  dragOffsetY += rowStepPx
                                  haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                              }
                            )
                          }
                          .testTag("queue_drag_handle_$qIdx"),
                        contentAlignment = Alignment.Center
                      ) {
                        Icon(
                          imageVector = Icons.Rounded.DragHandle,
                          contentDescription = stringResource(id = R.string.queue_drag_handle_cd),
                          tint = if (isBeingDragged) accent else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                          modifier = Modifier.size(20.dp)
                        )
                      }

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
                        val trackBpm = remember(track.id, track.bpm) {
                          if (track.bpm > 0) track.bpm else TrackMetadataClassifier.enrich(track).bpm
                        }
                        Text(
                          text = if (djAutoMixEnabled) "${track.artist} · $trackBpm BPM" else track.artist,
                          style = MaterialTheme.typography.bodySmall,
                          color = MaterialTheme.colorScheme.onSurfaceVariant,
                          maxLines = 1,
                          overflow = TextOverflow.Ellipsis
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
  }
}
