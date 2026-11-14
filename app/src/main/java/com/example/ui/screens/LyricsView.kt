package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.lyrics.SyncedLyricsView
import com.example.model.LyricsResult
import java.util.Locale

@Composable
fun LyricsView(
  lyricsResult: LyricsResult?,
  currentPositionMs: Long,
  positionProvider: () -> Long = { currentPositionMs },
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
  val plainText = lyricsResult.plain?.takeIf { it.isNotBlank() }
  val isMissing = synced.isNullOrEmpty() &&
    (plainText == null || plainText.startsWith("No lyrics found", ignoreCase = true))

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
    return
  }

  val isSynced = !synced.isNullOrEmpty()
  val rawLyricsText = lyricsResult.rawLrc?.takeIf { it.isNotBlank() } ?: plainText

  val latestPositionProvider by rememberUpdatedState(positionProvider)
  val latestOnLineClick by rememberUpdatedState(onLineClick)
  val latestOnAlignLine by rememberUpdatedState(onAlignLine)
  val latestOffsetMs by rememberUpdatedState(lyricOffsetMs)
  val latestIsPreview by rememberUpdatedState(isPreviewClip)

  Column(modifier = modifier.fillMaxSize()) {
    if (isSynced) {
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
          SyncNudgeChip(label = "-5s", onClick = { onAdjustOffset(-5000L) })
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
          SyncNudgeChip(label = "+5s", onClick = { onAdjustOffset(5000L) })
        }
      }
    } else {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.padding(top = 6.dp, bottom = 10.dp)
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
    }

    AndroidView(
      modifier = Modifier
        .weight(1f)
        .fillMaxWidth()
        .testTag("synced_lyrics_recycler_host"),
      factory = { context ->
        val lyricsView = SyncedLyricsView(context)
        lyricsView.positionProvider = { latestPositionProvider() }
        lyricsView.onLineClick = { targetSeekMs ->
          if (latestIsPreview) {
            latestOnAlignLine(targetSeekMs + latestOffsetMs)
          } else {
            latestOnLineClick(targetSeekMs)
          }
        }
        lyricsView
      },
      update = { view ->
        view.positionProvider = { latestPositionProvider() }
        view.offsetMs = lyricOffsetMs
        view.setStyleColors(accentColor.toArgb(), android.graphics.Color.WHITE)
        view.setReducedMotion(reducedMotion)
        if (rawLyricsText.isNullOrBlank()) {
          view.clear()
        } else {
          view.setLyrics(rawLyricsText)
        }
      },
      onRelease = { view ->
        view.clear()
      }
    )
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
