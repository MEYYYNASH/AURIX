package com.example.ui.screens

import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.model.Song
import com.example.ui.theme.HeartRed
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun MiniPlayer(
  song: Song?,
  isPlaying: Boolean,
  currentPositionMs: Long,
  durationMs: Long,
  darkTheme: Boolean,
  reducedMotion: Boolean = false,
  hasLyrics: Boolean = false,
  onCheckLyrics: ((Song) -> Unit)? = null,
  onPlayPauseClick: () -> Unit,
  onFavoriteClick: () -> Unit,
  onSwipeNext: () -> Unit = {},
  onSwipePrevious: () -> Unit = {},
  onClick: () -> Unit
) {
  if (song == null) return

  LaunchedEffect(song.id) {
    onCheckLyrics?.invoke(song)
  }

  val (artC1, artC2) = rememberArtworkColors(song.artworkUrl, song.title)
  val accentColor = MaterialTheme.colorScheme.primary

  val glassFill = if (darkTheme) Color(0xFF172034).copy(alpha = 0.82f) else Color.White.copy(alpha = 0.88f)
  val innerEdgeHighlight = Color.White.copy(alpha = if (darkTheme) 0.26f else 0.90f)
  val topBrightHighlight = Color.White.copy(alpha = if (darkTheme) 0.48f else 0.98f)
  val primaryTextColor = if (darkTheme) Color.White else Color(0xFF0F172A)
  val secondaryTextColor = if (darkTheme) Color(0xFFB3BFD4) else Color(0xFF52607A)
  val buttonBgColor = if (darkTheme) Color.White.copy(alpha = 0.95f) else MaterialTheme.colorScheme.primary
  val buttonIconColor = if (darkTheme) Color(0xFF0F172A) else Color.White

  val totalMs = durationMs.coerceAtLeast(song.durationSeconds * 1000L).coerceAtLeast(1L)
  val progress = (currentPositionMs.toFloat() / totalMs.toFloat()).coerceIn(0f, 1f)

  val heartScale by animateFloatAsState(
    targetValue = if (song.isFavorite) 1.2f else 1.0f,
    animationSpec = if (reducedMotion) snap() else spring(dampingRatio = Spring.DampingRatioMediumBouncy),
    label = "mini_heart"
  )

  val scope = rememberCoroutineScope()
  val dragOffsetX = remember { Animatable(0f) }
  val swipeThresholdPx = with(LocalDensity.current) { 72.dp.toPx() }
  val haptic = LocalHapticFeedback.current
  var thresholdCrossed by remember { mutableStateOf(false) }

  val swipeFraction = (dragOffsetX.value / swipeThresholdPx).coerceIn(-1.2f, 1.2f)
  val prevCueAlpha = (swipeFraction).coerceIn(0f, 1f)
  val nextCueAlpha = (-swipeFraction).coerceIn(0f, 1f)

  Box(
    modifier = Modifier
      .padding(horizontal = 22.dp)
      .widthIn(max = 390.dp)
      .fillMaxWidth()
      .height(64.dp),
    contentAlignment = Alignment.Center
  ) {
    // Directional swipe-to-skip cues revealed beneath the pill while dragging
    if (abs(dragOffsetX.value) > 4f) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 18.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Icon(
          imageVector = Icons.Rounded.SkipPrevious,
          contentDescription = null,
          tint = accentColor,
          modifier = Modifier
            .size(24.dp)
            .alpha(prevCueAlpha)
            .scale(0.85f + 0.25f * prevCueAlpha)
        )
        Icon(
          imageVector = Icons.Rounded.SkipNext,
          contentDescription = null,
          tint = accentColor,
          modifier = Modifier
            .size(24.dp)
            .alpha(nextCueAlpha)
            .scale(0.85f + 0.25f * nextCueAlpha)
        )
      }
    }

    // Soft ambient pill shadow
    Box(
      modifier = Modifier
        .matchParentSize()
        .offset { IntOffset(dragOffsetX.value.roundToInt(), 0) }
        .drawBehind {
          if (size.width <= 0f || size.height <= 0f) return@drawBehind
          val r = size.height * 0.5f
          drawRoundRect(
            brush = Brush.verticalGradient(
              colors = listOf(
                Color.Black.copy(alpha = if (darkTheme) 0.24f else 0.07f),
                Color.Black.copy(alpha = if (darkTheme) 0.36f else 0.13f)
              )
            ),
            topLeft = Offset(0f, 5.dp.toPx()),
            size = Size(size.width, size.height),
            cornerRadius = CornerRadius(r, r)
          )
        }
    )

    // Frosted Liquid Glass Capsule Container
    Box(
      modifier = Modifier
        .matchParentSize()
        .offset { IntOffset(dragOffsetX.value.roundToInt(), 0) }
        .clip(CircleShape)
        .pointerInput(song.id, reducedMotion) {
          detectHorizontalDragGestures(
            onDragStart = { thresholdCrossed = false },
            onHorizontalDrag = { change, dragAmount ->
              change.consume()
              val nextOffset = (dragOffsetX.value + dragAmount).coerceIn(-swipeThresholdPx * 1.35f, swipeThresholdPx * 1.35f)
              scope.launch { dragOffsetX.snapTo(nextOffset) }
              val crossed = abs(nextOffset) >= swipeThresholdPx
              if (crossed && !thresholdCrossed) {
                thresholdCrossed = true
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
              } else if (!crossed) {
                thresholdCrossed = false
              }
            },
            onDragEnd = {
              val releasedOffset = dragOffsetX.value
              scope.launch {
                dragOffsetX.animateTo(
                  targetValue = 0f,
                  animationSpec = if (reducedMotion) snap() else spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                  )
                )
              }
              if (releasedOffset <= -swipeThresholdPx) {
                onSwipeNext()
              } else if (releasedOffset >= swipeThresholdPx) {
                onSwipePrevious()
              }
            },
            onDragCancel = {
              scope.launch {
                dragOffsetX.animateTo(
                  targetValue = 0f,
                  animationSpec = if (reducedMotion) snap() else spring(dampingRatio = Spring.DampingRatioMediumBouncy)
                )
              }
            }
          )
        }
        .clickable { onClick() }
        .testTag("mini_player_card")
    ) {
      // Dynamic blurred album artwork + track-hue aura inside glass pill
      val miniArtModel = resolveArtworkModel(song.artworkUrl, song.source)
      if (miniArtModel != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val miniArtReq = com.example.ui.image.rememberCrispArtworkRequest(model = miniArtModel)
        AsyncImage(
          model = miniArtReq,
          contentDescription = null,
          contentScale = ContentScale.Crop,
          filterQuality = com.example.ui.image.AurixImagePipeline.ARTWORK_FILTER_QUALITY,
          modifier = Modifier
            .matchParentSize()
            .scale(1.24f)
            .alpha(if (darkTheme) 0.34f else 0.18f)
            .blur(22.dp)
        )
      }
      Box(
        modifier = Modifier
          .matchParentSize()
          .then(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Modifier.blur(18.dp) else Modifier)
          .background(
            Brush.horizontalGradient(
              colors = listOf(
                artC1.copy(alpha = if (darkTheme) 0.28f else 0.16f),
                artC2.copy(alpha = if (darkTheme) 0.18f else 0.12f),
                accentColor.copy(alpha = if (darkTheme) 0.14f else 0.08f)
              )
            )
          )
      )

      // Base translucent fill + art-tinted 1dp glass edge + bright 1.5dp top specular highlight
      val miniRimBrush = Brush.horizontalGradient(
        colors = listOf(
          artC1.copy(alpha = if (darkTheme) 0.48f else 0.42f),
          innerEdgeHighlight,
          artC2.copy(alpha = if (darkTheme) 0.42f else 0.34f)
        )
      )
      Box(
        modifier = Modifier
          .matchParentSize()
          .background(glassFill)
          .border(1.dp, miniRimBrush, CircleShape)
          .drawBehind {
            val stroke15 = 1.5.dp.toPx()
            if (size.width <= stroke15 || size.height <= stroke15) return@drawBehind
            val inset = stroke15 * 0.5f
            val r = ((size.height - stroke15) * 0.5f).coerceAtLeast(0f)
            drawRoundRect(
              brush = Brush.verticalGradient(
                colors = listOf(
                  topBrightHighlight,
                  topBrightHighlight.copy(alpha = 0.12f),
                  Color.Transparent
                ),
                startY = 0f,
                endY = size.height * 0.45f
              ),
              topLeft = Offset(inset, inset),
              size = Size(size.width - stroke15, size.height - stroke15),
              cornerRadius = CornerRadius(r, r),
              style = Stroke(width = stroke15)
            )
          }
      )

      // Foreground Row Content
      Row(
        modifier = Modifier
          .fillMaxSize()
          .padding(horizontal = 9.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Box(
          modifier = Modifier
            .size(46.dp)
            .clip(CircleShape)
            .border(1.dp, Color.White.copy(alpha = if (darkTheme) 0.30f else 0.85f), CircleShape)
        ) {
          SongArtwork(
            song = song,
            modifier = Modifier.fillMaxSize(),
            cornerRadius = 23.dp,
            iconSize = 20.dp
          )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            if (isPlaying) {
              EqualizerBars(
                isPlaying = true,
                color = accentColor,
                reducedMotion = reducedMotion,
                barWidth = 2.5.dp,
                maxHeight = 11.dp
              )
            }
            Text(
              text = com.example.ui.theme.rememberKhmerTitleText(
                text = song.title,
                baseFontSize = 14.sp,
                isBold = true
              ),
              style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              color = primaryTextColor,
              modifier = Modifier.weight(1f, fill = false)
            )
            LyricsTag(
              visible = hasLyrics,
              size = LyricsTagSize.Mini,
              darkTheme = darkTheme,
              reducedMotion = reducedMotion,
              modifier = Modifier.padding(start = 2.dp)
            )
          }
          Text(
            text = song.artist,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = secondaryTextColor
          )
        }

        IconButton(
          onClick = onFavoriteClick,
          modifier = Modifier
            .minimumInteractiveComponentSize()
            .testTag("mini_favorite_button")
        ) {
          Icon(
            imageVector = if (song.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
            contentDescription = "Favorite",
            tint = if (song.isFavorite) HeartRed else primaryTextColor.copy(alpha = 0.78f),
            modifier = Modifier.size(20.dp).scale(heartScale)
          )
        }

        // Circular Play/Pause Button surrounded by live progress ring
        Box(
          modifier = Modifier
            .size(46.dp)
            .clip(CircleShape)
            .clickable { onPlayPauseClick() }
            .testTag("mini_play_pause_button"),
          contentAlignment = Alignment.Center
        ) {
          Canvas(modifier = Modifier.matchParentSize().padding(1.5.dp)) {
            val strokePx = 2.5.dp.toPx()
            drawArc(
              color = primaryTextColor.copy(alpha = 0.14f),
              startAngle = 0f,
              sweepAngle = 360f,
              useCenter = false,
              style = Stroke(width = strokePx)
            )
            if (progress > 0.005f) {
              drawArc(
                color = accentColor,
                startAngle = -90f,
                sweepAngle = 360f * progress,
                useCenter = false,
                style = Stroke(width = strokePx, cap = StrokeCap.Round)
              )
            }
          }
          Box(
            modifier = Modifier
              .size(36.dp)
              .clip(CircleShape)
              .background(buttonBgColor),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
              contentDescription = "Play or Pause",
              tint = buttonIconColor,
              modifier = Modifier.size(20.dp)
            )
          }
        }
      }
    }
  }
}
