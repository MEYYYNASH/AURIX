package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.R
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
  onReportWrongLyrics: (() -> Unit)? = null,
  onShareLyrics: (() -> Unit)? = null,
  modifier: Modifier = Modifier
) {
  val noLyricsLocalized = stringResource(id = R.string.no_lyrics_found)
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
  val isMissing = synced.isNullOrEmpty() && (
    plainText == null ||
      plainText.startsWith("No lyrics found", ignoreCase = true) ||
      plainText == noLyricsLocalized
    )

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
          text = noLyricsLocalized,
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
        Row(
          horizontalArrangement = Arrangement.spacedBy(10.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
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
          if (onShareLyrics != null) {
            OutlinedButton(
              onClick = onShareLyrics,
              shape = CircleShape,
              border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.35f)),
              colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
              modifier = Modifier.testTag("lyrics_empty_share_button")
            ) {
              Icon(Icons.Rounded.Share, contentDescription = "Share lyrics", modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text("Share card")
            }
          }
        }
      }
    }
    return
  }

  val rawLyricsText = lyricsResult.rawLrc?.takeIf { it.isNotBlank() } ?: plainText

  val latestPositionProvider by rememberUpdatedState(positionProvider)
  val latestOnLineClick by rememberUpdatedState(onLineClick)
  val latestOnAlignLine by rememberUpdatedState(onAlignLine)
  val latestOffsetMs by rememberUpdatedState(lyricOffsetMs)
  val latestIsPreview by rememberUpdatedState(isPreviewClip)

  Box(modifier = modifier.fillMaxSize()) {
    AndroidView(
      modifier = Modifier
        .fillMaxSize()
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

    if (onReportWrongLyrics != null || onShareLyrics != null) {
      Row(
        modifier = Modifier
          .align(Alignment.BottomCenter)
          .fillMaxWidth()
          .padding(horizontal = 4.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        if (onReportWrongLyrics != null) {
          Surface(
            onClick = onReportWrongLyrics,
            shape = CircleShape,
            color = Color(0xFF151F30).copy(alpha = 0.84f),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF273348)),
            modifier = Modifier
              .weight(1f, fill = false)
              .padding(end = 8.dp)
              .minimumInteractiveComponentSize()
              .testTag("lyrics_report_wrong_button")
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
              Icon(
                imageVector = Icons.Rounded.Flag,
                contentDescription = stringResource(id = R.string.report_wrong_lyrics),
                tint = Color(0xFFFCA5A5),
                modifier = Modifier.size(15.dp)
              )
              Text(
                text = stringResource(id = R.string.report_wrong_lyrics),
                style = MaterialTheme.typography.labelSmall.copy(
                  fontSize = 11.sp,
                  fontWeight = FontWeight.SemiBold
                ),
                color = Color.White.copy(alpha = 0.92f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
            }
          }
        } else {
          Spacer(modifier = Modifier.width(1.dp))
        }

        if (onShareLyrics != null) {
          Surface(
            onClick = onShareLyrics,
            shape = CircleShape,
            color = Color.White.copy(alpha = 0.14f),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.26f)),
            modifier = Modifier
              .minimumInteractiveComponentSize()
              .testTag("lyrics_share_button")
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
              Icon(
                imageVector = Icons.Rounded.Share,
                contentDescription = "Share lyrics",
                tint = Color.White,
                modifier = Modifier.size(17.dp)
              )
              Text(
                text = "Share",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = Color.White
              )
            }
          }
        }
      }
    }
  }
}
