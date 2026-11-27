package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.model.LyricsResult
import com.example.model.Song
import com.example.ui.theme.KhmerLyricsFont
import com.example.ui.theme.rememberKhmerLyricsText
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.roundToInt

data class FloatingLyricSnapshot(
  val activeLine: String,
  val subLabel: String,
  val lineProgressFraction: Float,
  val hasLyrics: Boolean
)

internal fun resolveFloatingLyricSnapshot(
  song: Song,
  lyricsResult: LyricsResult?,
  positionMs: Long,
  lyricOffsetMs: Long,
  instrumentalLabel: String
): FloatingLyricSnapshot {
  val effectiveMs = (positionMs + lyricOffsetMs).coerceAtLeast(0L)
  val synced = lyricsResult?.synced.orEmpty()
  if (synced.isNotEmpty()) {
    var activeIdx = -1
    for (i in synced.indices) {
      if (synced[i].timestampMs <= effectiveMs + 150L) {
        activeIdx = i
      } else {
        break
      }
    }
    if (activeIdx >= 0) {
      val cur = synced[activeIdx]
      val nextTs = synced.getOrNull(activeIdx + 1)?.timestampMs ?: (cur.timestampMs + 3500L)
      val span = (nextTs - cur.timestampMs).coerceAtLeast(400L)
      val frac = ((effectiveMs - cur.timestampMs).toFloat() / span.toFloat()).coerceIn(0f, 1f)
      val nextText = synced.getOrNull(activeIdx + 1)?.text?.takeIf { it.isNotBlank() }
        ?: "${song.title} · ${song.artist}"
      return FloatingLyricSnapshot(
        activeLine = cur.text.ifBlank { "♪ · · ·" },
        subLabel = nextText,
        lineProgressFraction = frac,
        hasLyrics = true
      )
    } else {
      val firstLine = synced.first().text.ifBlank { song.title }
      return FloatingLyricSnapshot(
        activeLine = "♪ $firstLine",
        subLabel = "${song.title} · ${song.artist}",
        lineProgressFraction = 0f,
        hasLyrics = true
      )
    }
  }

  val plainLines = lyricsResult?.plain
    ?.lines()
    ?.map { it.trim() }
    ?.filter { it.isNotBlank() && !it.startsWith("No lyrics found", ignoreCase = true) }
    .orEmpty()

  if (plainLines.isNotEmpty()) {
    val totalMs = (song.durationSeconds * 1000L).coerceAtLeast(15_000L)
    val idx = ((effectiveMs.toDouble() / totalMs.toDouble()) * plainLines.size)
      .toInt()
      .coerceIn(0, plainLines.lastIndex)
    val nextLine = plainLines.getOrNull(idx + 1) ?: "${song.title} · ${song.artist}"
    return FloatingLyricSnapshot(
      activeLine = plainLines[idx],
      subLabel = nextLine,
      lineProgressFraction = ((effectiveMs % 3000L).toFloat() / 3000f).coerceIn(0f, 1f),
      hasLyrics = true
    )
  }

  return FloatingLyricSnapshot(
    activeLine = instrumentalLabel,
    subLabel = "${song.title} · ${song.artist}",
    lineProgressFraction = 0f,
    hasLyrics = false
  )
}

@Composable
fun FloatingMiniLyricsOverlay(
  visible: Boolean,
  song: Song?,
  isPlaying: Boolean,
  positionFlow: StateFlow<Long>,
  lyricsResult: LyricsResult?,
  lyricOffsetMs: Long,
  khmerLyricsFont: KhmerLyricsFont,
  accentColor: Color,
  reducedMotion: Boolean,
  onOpenFullLyrics: () -> Unit,
  onTogglePlayPause: () -> Unit,
  onClosePip: () -> Unit,
  modifier: Modifier = Modifier
) {
  val density = LocalDensity.current
  var containerSize by remember { mutableStateOf(IntSize.Zero) }
  var pillSize by remember { mutableStateOf(IntSize.Zero) }
  var dragOffsetX by remember { mutableFloatStateOf(0f) }
  var dragOffsetY by remember { mutableFloatStateOf(0f) }

  Box(
    modifier = modifier
      .fillMaxSize()
      .windowInsetsPadding(WindowInsets.safeDrawing)
      .onSizeChanged { containerSize = it }
  ) {
    AnimatedVisibility(
      visible = visible && song != null,
      enter = if (reducedMotion) {
        fadeIn(snap())
      } else {
        fadeIn(tween(200)) +
          scaleIn(initialScale = 0.88f, animationSpec = spring(dampingRatio = 0.78f, stiffness = 420f)) +
          slideInVertically(animationSpec = spring(dampingRatio = 0.82f, stiffness = 400f)) { -it / 2 }
      },
      exit = if (reducedMotion) {
        fadeOut(snap())
      } else {
        fadeOut(tween(150)) + scaleOut(targetScale = 0.88f, animationSpec = tween(160)) + slideOutVertically { -it / 2 }
      },
      modifier = Modifier
        .align(Alignment.TopCenter)
        .padding(top = 56.dp, start = 14.dp, end = 14.dp)
        .offset {
          val maxX = ((containerSize.width - pillSize.width).coerceAtLeast(0)) / 2f
          val minY = -with(density) { 44.dp.toPx() }
          val maxY = (containerSize.height - pillSize.height - with(density) { 188.dp.toPx() }).coerceAtLeast(0f)
          IntOffset(
            x = dragOffsetX.coerceIn(-maxX, maxX).roundToInt(),
            y = dragOffsetY.coerceIn(minY, maxY).roundToInt()
          )
        }
        .onSizeChanged { pillSize = it }
        .pointerInput(Unit) {
          detectDragGestures { change, dragAmount ->
            change.consume()
            val maxX = ((containerSize.width - pillSize.width).coerceAtLeast(0)) / 2f
            val minY = -44.dp.toPx()
            val maxY = (containerSize.height - pillSize.height - 188.dp.toPx()).coerceAtLeast(0f)
            dragOffsetX = (dragOffsetX + dragAmount.x).coerceIn(-maxX, maxX)
            dragOffsetY = (dragOffsetY + dragAmount.y).coerceIn(minY, maxY)
          }
        }
    ) {
      val activeSong = song ?: return@AnimatedVisibility
      val positionMs by positionFlow.collectAsStateWithLifecycle()
      val instrumentalFallback = stringResource(id = R.string.floating_lyrics_no_lyrics, activeSong.title)
      val snap = remember(activeSong.id, activeSong.title, lyricsResult, positionMs, lyricOffsetMs, instrumentalFallback) {
        resolveFloatingLyricSnapshot(
          song = activeSong,
          lyricsResult = lyricsResult,
          positionMs = positionMs,
          lyricOffsetMs = lyricOffsetMs,
          instrumentalLabel = instrumentalFallback
        )
      }

      val animatedKaraokeProgress by animateFloatAsState(
        targetValue = snap.lineProgressFraction,
        animationSpec = if (reducedMotion) snap() else tween(140, easing = FastOutSlowInEasing),
        label = "pip_karaoke_fill"
      )

      val pillShape = RoundedCornerShape(999.dp)
      Surface(
        shape = pillShape,
        color = Color(0xFF0A0F1A).copy(alpha = 0.94f),
        shadowElevation = 18.dp,
        border = BorderStroke(
          width = 1.2.dp,
          brush = Brush.horizontalGradient(
            colors = listOf(
              accentColor.copy(alpha = 0.82f),
              Color.White.copy(alpha = 0.25f),
              accentColor.copy(alpha = 0.55f)
            )
          )
        ),
        modifier = Modifier
          .widthIn(min = 240.dp, max = 380.dp)
          .clip(pillShape)
          .clickable { onOpenFullLyrics() }
          .testTag("floating_mini_lyrics_pill")
      ) {
        Column(modifier = Modifier.fillMaxWidth()) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .background(
                Brush.horizontalGradient(
                  colors = listOf(
                    accentColor.copy(alpha = 0.20f),
                    Color.Transparent,
                    accentColor.copy(alpha = 0.10f)
                  )
                )
              )
              .padding(start = 10.dp, end = 6.dp, top = 7.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            // Artwork Orb with Lyric Badge
            Box(
              modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(accentColor.copy(alpha = 0.24f))
                .border(1.dp, accentColor.copy(alpha = 0.72f), CircleShape),
              contentAlignment = Alignment.Center
            ) {
              SongArtwork(
                song = activeSong,
                modifier = Modifier.fillMaxSize(),
                cornerRadius = 999.dp,
                iconSize = 16.dp
              )
            }

            // 1-Line Active Karaoke Lyric + Compact Next Line
            Column(
              modifier = Modifier.weight(1f),
              verticalArrangement = Arrangement.Center
            ) {
              AnimatedContent(
                targetState = snap.activeLine,
                transitionSpec = {
                  if (reducedMotion) {
                    fadeIn(snap()) togetherWith fadeOut(snap())
                  } else {
                    (fadeIn(tween(180)) + slideInVertically(tween(200)) { it / 2 }) togetherWith
                      (fadeOut(tween(130)) + slideOutVertically(tween(150)) { -it / 2 })
                  }
                },
                label = "pip_lyric_line"
              ) { lineText ->
                Text(
                  text = rememberKhmerLyricsText(
                    text = lineText,
                    khmerLyricsFont = khmerLyricsFont,
                    baseFontSize = 13.sp,
                    isBold = true
                  ),
                  style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    lineHeight = 17.sp
                  ),
                  color = Color.White,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis
                )
              }

              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
              ) {
                Icon(
                  imageVector = Icons.Rounded.Lyrics,
                  contentDescription = null,
                  tint = accentColor,
                  modifier = Modifier.size(11.dp)
                )
                Text(
                  text = snap.subLabel,
                  style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Medium
                  ),
                  color = Color.White.copy(alpha = 0.68f),
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis
                )
              }
            }

            // Play/Pause Mini Button
            IconButton(
              onClick = onTogglePlayPause,
              modifier = Modifier
                .size(32.dp)
                .testTag("floating_mini_lyrics_play_pause")
            ) {
              Icon(
                imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play",
                tint = Color.White,
                modifier = Modifier.size(18.dp)
              )
            }

            // Close PiP Button
            IconButton(
              onClick = onClosePip,
              modifier = Modifier
                .size(30.dp)
                .testTag("floating_mini_lyrics_close_button")
            ) {
              Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = "Close mini lyrics",
                tint = Color.White.copy(alpha = 0.72f),
                modifier = Modifier.size(16.dp)
              )
            }
          }

          // Karaoke Fill Progress Bar at the bottom of the capsule
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .height(2.5.dp)
              .background(Color.White.copy(alpha = 0.12f))
          ) {
            Box(
              modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(animatedKaraokeProgress.coerceIn(0.04f, 1f))
                .background(
                  Brush.horizontalGradient(
                    colors = listOf(accentColor, Color.White)
                  )
                )
            )
          }
        }
      }
    }
  }
}
