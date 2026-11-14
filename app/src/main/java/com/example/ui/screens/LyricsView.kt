package com.example.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.LyricsResult
import java.util.Locale

@Composable
fun LyricsView(
  lyricsResult: LyricsResult?,
  currentPositionMs: Long,
  lyricOffsetMs: Long = 0L,
  isPreviewClip: Boolean = false,
  accentColor: Color = Color.White,
  reducedMotion: Boolean = false,
  onLineClick: (Long) -> Unit,
  onAlignLine: (Long) -> Unit = {},
  onAdjustOffset: (Long) -> Unit = {},
  onResetOffset: () -> Unit = {},
  onRetryLyrics: () -> Unit = {},
  modifier: Modifier = Modifier
) {
  if (lyricsResult == null) {
    Box(
      modifier = modifier.fillMaxSize(),
      contentAlignment = Alignment.Center
    ) {
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        CircularProgressIndicator(color = Color.White, strokeWidth = 3.dp)
        Text(
          text = "Loading synced lyrics...",
          style = MaterialTheme.typography.bodySmall,
          color = Color.White.copy(alpha = 0.72f)
        )
      }
    }
    return
  }

  val synced = lyricsResult.synced
  if (synced.isNullOrEmpty()) {
    val plainText = lyricsResult.plain?.takeIf { it.isNotBlank() }
    val isMissing = plainText == null || plainText.startsWith("No lyrics found", ignoreCase = true)

    if (isMissing) {
      Box(
        modifier = modifier
          .fillMaxSize()
          .padding(24.dp),
        contentAlignment = Alignment.Center
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Color.White.copy(alpha = 0.10f))
            .border(1.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(24.dp))
            .padding(24.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          Icon(
            imageVector = Icons.Rounded.Lyrics,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.78f),
            modifier = Modifier.size(34.dp)
          )
          Text(
            text = "No lyrics found for this track",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = Color.White,
            textAlign = TextAlign.Center
          )
          Text(
            text = "Tap retry to search again or enjoy the visualizer.",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.68f),
            textAlign = TextAlign.Center
          )
          OutlinedButton(
            onClick = onRetryLyrics,
            shape = CircleShape,
            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.35f)),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
            modifier = Modifier.testTag("lyrics_retry_button")
          ) {
            Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Retry")
          }
        }
      }
    } else {
      LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 12.dp, bottom = 40.dp)
      ) {
        item {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(bottom = 14.dp)
          ) {
            Icon(
              imageVector = Icons.Rounded.Lyrics,
              contentDescription = null,
              tint = Color.White.copy(alpha = 0.68f),
              modifier = Modifier.size(15.dp)
            )
            Text(
              text = "Plain lyrics (unsynced)",
              style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
              color = Color.White.copy(alpha = 0.68f)
            )
          }
          Text(
            text = plainText ?: "",
            style = MaterialTheme.typography.titleLarge.copy(
              fontSize = 20.sp,
              lineHeight = 31.sp,
              fontWeight = FontWeight.SemiBold
            ),
            color = Color.White.copy(alpha = 0.90f)
          )
        }
      }
    }
    return
  }

  val effectivePositionMs = (currentPositionMs + lyricOffsetMs).coerceAtLeast(0L)
  val firstTimestampMs = synced.first().timestampMs
  val activeIndex = if (effectivePositionMs + 150L < firstTimestampMs) {
    -1
  } else {
    synced.indexOfLast { it.timestampMs <= effectivePositionMs + 150L }
  }

  val listState = rememberLazyListState()
  val density = LocalDensity.current

  BoxWithConstraints(modifier = modifier.fillMaxSize()) {
    val viewportHeightPx = with(density) { maxHeight.toPx() }
    val centerOffsetPx = -(viewportHeightPx * 0.28f).toInt()

    LaunchedEffect(activeIndex, centerOffsetPx) {
      if (activeIndex in synced.indices) {
        // +1 accounts for the sticky sync header item at index 0
        val targetListIndex = activeIndex + 1
        if (reducedMotion) {
          listState.scrollToItem(targetListIndex, centerOffsetPx)
        } else {
          listState.animateScrollToItem(targetListIndex, centerOffsetPx)
        }
      } else if (activeIndex == -1 && listState.firstVisibleItemIndex > 1) {
        listState.animateScrollToItem(0)
      }
    }

    LazyColumn(
      state = listState,
      modifier = Modifier.fillMaxSize(),
      verticalArrangement = Arrangement.spacedBy(10.dp),
      contentPadding = PaddingValues(top = 6.dp, bottom = 140.dp)
    ) {
      item(key = "lyrics_header") {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            Icon(
              imageVector = if (isPreviewClip) Icons.Rounded.Sync else Icons.Rounded.GraphicEq,
              contentDescription = null,
              tint = Color.White.copy(alpha = 0.78f),
              modifier = Modifier.size(15.dp)
            )
            Text(
              text = if (isPreviewClip) {
                "30s preview • Tap any line to align lyrics with the chorus"
              } else {
                "Synced with player • Tap any line to seek"
              },
              style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
              color = Color.White.copy(alpha = 0.78f),
              maxLines = 2,
              overflow = TextOverflow.Ellipsis
            )
          }

          // Compact Sync Timing Fine-Tune Strip
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .clip(CircleShape)
              .background(Color.White.copy(alpha = 0.10f))
              .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape)
              .padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            if (isPreviewClip) {
              SyncNudgeChip(label = "-5s", onClick = { onAdjustOffset(-5000L) })
            }
            SyncNudgeChip(label = "-0.5s", onClick = { onAdjustOffset(-500L) })

            val offsetLabel = if (lyricOffsetMs == 0L) {
              "Sync 0.0s"
            } else {
              val sec = lyricOffsetMs / 1000.0
              String.format(Locale.US, "%+.1fs (Reset)", sec)
            }
            Box(
              modifier = Modifier
                .clip(CircleShape)
                .clickable(enabled = lyricOffsetMs != 0L) { onResetOffset() }
                .padding(horizontal = 10.dp, vertical = 4.dp),
              contentAlignment = Alignment.Center
            ) {
              Text(
                text = offsetLabel,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = if (lyricOffsetMs == 0L) Color.White.copy(alpha = 0.80f) else accentColor.copy(alpha = 0.95f)
              )
            }

            SyncNudgeChip(label = "+0.5s", onClick = { onAdjustOffset(500L) })
            if (isPreviewClip) {
              SyncNudgeChip(label = "+5s", onClick = { onAdjustOffset(5000L) })
            }
          }
        }
      }

      itemsIndexed(synced, key = { idx, line -> "${idx}_${line.timestampMs}" }) { index, line ->
        val isActive = index == activeIndex
        val distance = if (activeIndex == -1) index + 1 else kotlin.math.abs(index - activeIndex)
        val targetAlpha = when {
          isActive -> 1.0f
          distance == 1 -> 0.58f
          else -> 0.36f
        }
        val lineAlpha by animateFloatAsState(
          targetValue = targetAlpha,
          animationSpec = if (reducedMotion) snap() else tween(220),
          label = "lyric_alpha_$index"
        )
        val lineScale by animateFloatAsState(
          targetValue = if (isActive && !reducedMotion) 1.02f else 0.98f,
          animationSpec = if (reducedMotion) snap() else tween(220),
          label = "lyric_scale_$index"
        )

        val pillModifier = if (isActive) {
          Modifier
            .background(
              Brush.horizontalGradient(
                colors = listOf(
                  accentColor.copy(alpha = 0.28f),
                  Color.White.copy(alpha = 0.14f)
                )
              ),
              RoundedCornerShape(18.dp)
            )
            .border(
              width = 1.dp,
              color = Color.White.copy(alpha = 0.32f),
              shape = RoundedCornerShape(18.dp)
            )
        } else {
          Modifier
        }

        Box(
          modifier = Modifier
            .fillMaxWidth()
            .scale(lineScale)
            .clip(RoundedCornerShape(18.dp))
            .then(pillModifier)
            .clickable {
              if (isPreviewClip) {
                onAlignLine(line.timestampMs)
              } else {
                onLineClick((line.timestampMs - lyricOffsetMs).coerceAtLeast(0L))
              }
            }
            .padding(horizontal = 14.dp, vertical = if (isActive) 12.dp else 8.dp)
        ) {
          Text(
            text = line.text.ifBlank { "..." },
            style = MaterialTheme.typography.titleLarge.copy(
              fontSize = if (isActive) 23.sp else 20.sp,
              lineHeight = if (isActive) 30.sp else 27.sp,
              fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.SemiBold
            ),
            color = Color.White,
            textAlign = TextAlign.Start,
            modifier = Modifier
              .fillMaxWidth()
              .alpha(lineAlpha)
              .blur(if (isActive || distance <= 1 || reducedMotion) 0.dp else 0.8.dp)
          )
        }
      }
    }
  }
}

@Composable
private fun SyncNudgeChip(
  label: String,
  onClick: () -> Unit
) {
  Box(
    modifier = Modifier
      .clip(CircleShape)
      .background(Color.White.copy(alpha = 0.14f))
      .clickable { onClick() }
      .padding(horizontal = 10.dp, vertical = 5.dp),
    contentAlignment = Alignment.Center
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
      color = Color.White
    )
  }
}
