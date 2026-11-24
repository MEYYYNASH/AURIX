package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.R
import com.example.lyrics.LyricsAnimationStyle
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
  isPlaying: Boolean = true,
  lyricsAnimationStyle: LyricsAnimationStyle = LyricsAnimationStyle.SLIDE_UP,
  accentColor: Color = Color.White,
  reducedMotion: Boolean = false,
  onLineClick: (Long) -> Unit,
  onAlignLine: (Long) -> Unit = {},
  onAdjustOffset: (Long) -> Unit = {},
  onResetOffset: () -> Unit = {},
  onRetryLyrics: () -> Unit = {},
  onShareLyrics: (() -> Unit)? = null,
  developerModeEnabled: Boolean = false,
  onOpenLyricsEditor: (() -> Unit)? = null,
  modifier: Modifier = Modifier
) {
  val noLyricsLocalized = stringResource(id = R.string.no_lyrics_found)
  if (lyricsResult == null) {
    Box(
      modifier = modifier
        .fillMaxSize()
        .padding(horizontal = 20.dp),
      contentAlignment = Alignment.Center
    ) {
      StatusRow(
        text = "Finding lyrics",
        runner = OrbiRunner.Music,
        outcome = OrbiStatusOutcome.Loading,
        reducedMotion = reducedMotion
      )
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
        if (developerModeEnabled && onOpenLyricsEditor != null) {
          Button(
            onClick = onOpenLyricsEditor,
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
              containerColor = Color(0xFF43C7F4),
              contentColor = Color(0xFF080C14)
            ),
            modifier = Modifier
              .padding(top = 4.dp)
              .minimumInteractiveComponentSize()
              .testTag("lyrics_add_custom_button")
          ) {
            Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = stringResource(id = R.string.lyrics_add_button),
              style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
            )
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

  val lifecycleOwner = LocalLifecycleOwner.current
  var isAppForeground by remember {
    mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
  }
  DisposableEffect(lifecycleOwner) {
    val observer = LifecycleEventObserver { _, event ->
      when (event) {
        Lifecycle.Event.ON_RESUME -> isAppForeground = true
        Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> isAppForeground = false
        else -> Unit
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
  }

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
        view.setAnimationStyle(lyricsAnimationStyle)
        view.setPlaybackAndForeground(playing = isPlaying, foreground = isAppForeground)
        if (rawLyricsText.isNullOrBlank()) {
          view.clear()
        } else {
          view.setLyrics(rawLyricsText)
          if (!isPlaying && isAppForeground) {
            view.syncPositionOnce()
          }
        }
      },
      onRelease = { view ->
        view.clear()
      }
    )

    if (lyricsResult.isCustom && developerModeEnabled) {
      Surface(
        shape = CircleShape,
        color = Color(0xFF43C7F4).copy(alpha = 0.20f),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF43C7F4).copy(alpha = 0.55f)),
        modifier = Modifier
          .align(Alignment.TopEnd)
          .padding(top = 4.dp, end = 6.dp)
          .testTag("lyrics_custom_source_badge")
      ) {
        Text(
          text = stringResource(id = R.string.dev_mode_custom_badge),
          style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
          color = Color(0xFF43C7F4),
          modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
      }
    }

    if (onShareLyrics != null || (developerModeEnabled && onOpenLyricsEditor != null)) {
      Row(
        modifier = Modifier
          .align(Alignment.BottomEnd)
          .padding(end = 6.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        if (developerModeEnabled && onOpenLyricsEditor != null) {
          Surface(
            onClick = onOpenLyricsEditor,
            shape = CircleShape,
            color = Color(0xFF43C7F4).copy(alpha = 0.22f),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF43C7F4).copy(alpha = 0.55f)),
            modifier = Modifier
              .minimumInteractiveComponentSize()
              .testTag("lyrics_edit_custom_button")
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
              Icon(
                imageVector = Icons.Rounded.Edit,
                contentDescription = stringResource(id = R.string.lyrics_edit_button),
                tint = Color.White,
                modifier = Modifier.size(16.dp)
              )
              Text(
                text = stringResource(id = R.string.lyrics_edit_button),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = Color.White
              )
            }
          }
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
