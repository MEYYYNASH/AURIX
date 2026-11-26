package com.example.ui.screens

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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.Song
import com.example.ui.theme.HeartRed
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.math.abs

internal const val MINI_PLAYER_SLIDE_UP_DAMPING = 0.84f
internal const val MINI_PLAYER_SLIDE_UP_STIFFNESS = 420f
internal const val MINI_PLAYER_EXPAND_SCALE_DOWN = 0.92f
internal const val MINI_PLAYER_PRESS_SCALE = 0.972f
internal const val MINI_PLAYER_EXPAND_ALPHA = 0.40f
internal const val MINI_PLAYER_EXPAND_LIFT_DP = -10f
internal const val MINI_PLAYER_HIGH_REFRESH_OPTIMIZED = true

@Composable
fun MiniPlayer(
  song: Song?,
  isPlaying: Boolean,
  currentPositionMs: Long = 0L,
  positionFlow: StateFlow<Long>? = null,
  durationMs: Long,
  darkTheme: Boolean,
  reducedMotion: Boolean = false,
  isExpandingToFullPlayer: Boolean = false,
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

  val glassFill = if (darkTheme) Color(0xFF172034).copy(alpha = 0.84f) else Color.White.copy(alpha = 0.90f)
  val innerEdgeHighlight = Color.White.copy(alpha = if (darkTheme) 0.26f else 0.90f)
  val topBrightHighlight = Color.White.copy(alpha = if (darkTheme) 0.48f else 0.98f)
  val primaryTextColor = if (darkTheme) Color.White else Color(0xFF0F172A)
  val secondaryTextColor = if (darkTheme) Color(0xFFB3BFD4) else Color(0xFF52607A)
  val buttonBgColor = if (darkTheme) Color.White.copy(alpha = 0.95f) else MaterialTheme.colorScheme.primary
  val buttonIconColor = if (darkTheme) Color(0xFF0F172A) else MaterialTheme.colorScheme.onPrimary

  val totalMs = durationMs.coerceAtLeast(song.durationSeconds * 1000L).coerceAtLeast(1L)

  // All animation values are kept as State<Float> and read strictly inside graphicsLayer
  // so 120Hz press/expand/heart/swipe animations incur 0 recompositions & 0 layout passes.
  val heartScaleState = animateFloatAsState(
    targetValue = if (song.isFavorite) 1.2f else 1.0f,
    animationSpec = if (reducedMotion) snap() else spring(dampingRatio = Spring.DampingRatioMediumBouncy),
    label = "mini_heart"
  )

  val scope = rememberCoroutineScope()
  val density = LocalDensity.current
  val dragOffsetX = remember { Animatable(0f) }
  val swipeThresholdPx = with(density) { 72.dp.toPx() }
  val haptic = LocalHapticFeedback.current
  var thresholdCrossed by remember { mutableStateOf(false) }

  // Tactile press-in + smooth slight scale-down continuity when expanding into the full-screen player
  val cardInteractionSource = remember { MutableInteractionSource() }
  val isCardPressed by cardInteractionSource.collectIsPressedAsState()
  val expandLiftPx = with(density) { MINI_PLAYER_EXPAND_LIFT_DP.dp.toPx() }
  val transitionSpringSpec = remember(reducedMotion) {
    if (reducedMotion) snap<Float>() else spring(
      dampingRatio = MINI_PLAYER_SLIDE_UP_DAMPING,
      stiffness = 440f
    )
  }
  val containerScaleState = animateFloatAsState(
    targetValue = when {
      isExpandingToFullPlayer -> MINI_PLAYER_EXPAND_SCALE_DOWN
      isCardPressed && !reducedMotion -> MINI_PLAYER_PRESS_SCALE
      else -> 1f
    },
    animationSpec = transitionSpringSpec,
    label = "mini_player_expand_scale"
  )
  val containerAlphaState = animateFloatAsState(
    targetValue = if (isExpandingToFullPlayer) MINI_PLAYER_EXPAND_ALPHA else 1f,
    animationSpec = transitionSpringSpec,
    label = "mini_player_expand_alpha"
  )
  val containerLiftYState = animateFloatAsState(
    targetValue = if (isExpandingToFullPlayer && !reducedMotion) expandLiftPx else 0f,
    animationSpec = transitionSpringSpec,
    label = "mini_player_expand_lift"
  )

  // Smooth micro slide-up on track info whenever a new track starts while docked (pure GPU layer)
  val trackSlideStartPx = with(density) { 12.dp.toPx() }
  var lastAnimatedSongId by remember { mutableStateOf(song.id) }
  val trackStartOffsetY = remember { Animatable(0f) }
  val trackStartAlpha = remember { Animatable(1f) }
  LaunchedEffect(song.id, reducedMotion) {
    if (song.id != lastAnimatedSongId) {
      lastAnimatedSongId = song.id
      if (reducedMotion) {
        trackStartOffsetY.snapTo(0f)
        trackStartAlpha.snapTo(1f)
      } else {
        trackStartOffsetY.snapTo(trackSlideStartPx)
        trackStartAlpha.snapTo(0.45f)
        launch {
          trackStartOffsetY.animateTo(
            targetValue = 0f,
            animationSpec = spring(
              dampingRatio = MINI_PLAYER_SLIDE_UP_DAMPING,
              stiffness = 480f
            )
          )
        }
        launch {
          trackStartAlpha.animateTo(
            targetValue = 1f,
            animationSpec = spring(
              dampingRatio = Spring.DampingRatioNoBouncy,
              stiffness = 500f
            )
          )
        }
      }
    }
  }

  val auraBrush = remember(artC1, artC2, accentColor, darkTheme) {
    Brush.horizontalGradient(
      colors = listOf(
        artC1.copy(alpha = if (darkTheme) 0.42f else 0.24f),
        artC2.copy(alpha = if (darkTheme) 0.30f else 0.18f),
        accentColor.copy(alpha = if (darkTheme) 0.22f else 0.12f)
      )
    )
  }
  val miniRimBrush = remember(artC1, artC2, innerEdgeHighlight, darkTheme) {
    Brush.horizontalGradient(
      colors = listOf(
        artC1.copy(alpha = if (darkTheme) 0.50f else 0.44f),
        innerEdgeHighlight,
        artC2.copy(alpha = if (darkTheme) 0.44f else 0.36f)
      )
    )
  }
  val shadowBrush = remember(darkTheme) {
    Brush.verticalGradient(
      colors = listOf(
        Color.Black.copy(alpha = if (darkTheme) 0.24f else 0.07f),
        Color.Black.copy(alpha = if (darkTheme) 0.36f else 0.13f)
      )
    )
  }

  Box(
    modifier = Modifier
      .padding(horizontal = 22.dp)
      .widthIn(max = 390.dp)
      .fillMaxWidth()
      .height(64.dp)
      .graphicsLayer {
        val s = containerScaleState.value
        scaleX = s
        scaleY = s
        alpha = containerAlphaState.value
        translationY = containerLiftYState.value
        transformOrigin = TransformOrigin(0.5f, 0.5f)
      },
    contentAlignment = Alignment.Center
  ) {
    // Directional swipe-to-skip cues (pure graphicsLayer — never recomposes MiniPlayer while dragging)
    MiniPlayerSwipeCues(
      dragOffsetXProvider = { dragOffsetX.value },
      swipeThresholdPx = swipeThresholdPx,
      accentColor = accentColor,
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 18.dp)
    )

    // Soft ambient pill shadow (GPU translationX — zero layout passes during drag)
    Box(
      modifier = Modifier
        .matchParentSize()
        .graphicsLayer {
          translationX = dragOffsetX.value
        }
        .drawBehind {
          if (size.width <= 0f || size.height <= 0f) return@drawBehind
          val r = size.height * 0.5f
          drawRoundRect(
            brush = shadowBrush,
            topLeft = Offset(0f, 5.dp.toPx()),
            size = Size(size.width, size.height),
            cornerRadius = CornerRadius(r, r)
          )
        }
    )

    // Frosted Liquid Glass Capsule Container (GPU-only gradients — zero per-frame RenderEffect blur overhead)
    Box(
      modifier = Modifier
        .matchParentSize()
        .graphicsLayer {
          translationX = dragOffsetX.value
        }
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
        .clickable(
          interactionSource = cardInteractionSource,
          indication = ripple(bounded = true),
          onClick = onClick
        )
        .testTag("mini_player_card")
    ) {
      // Base frosted glass fill + smooth artwork-tinted aura + art-tinted 1dp rim + top specular highlight
      Box(
        modifier = Modifier
          .matchParentSize()
          .background(glassFill)
          .background(auraBrush)
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
        Row(
          modifier = Modifier
            .weight(1f)
            .graphicsLayer {
              translationY = trackStartOffsetY.value
              alpha = trackStartAlpha.value
            },
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
            modifier = Modifier
              .size(20.dp)
              .graphicsLayer {
                val hs = heartScaleState.value
                scaleX = hs
                scaleY = hs
              }
          )
        }

        // Circular Play/Pause Button surrounded by isolated live progress ring
        Box(
          modifier = Modifier
            .size(46.dp)
            .clip(CircleShape)
            .clickable { onPlayPauseClick() }
            .testTag("mini_play_pause_button"),
          contentAlignment = Alignment.Center
        ) {
          MiniPlayerProgressRing(
            positionFlow = positionFlow,
            fallbackPositionMs = currentPositionMs,
            totalMs = totalMs,
            trackColor = primaryTextColor.copy(alpha = 0.14f),
            accentColor = accentColor,
            modifier = Modifier
              .matchParentSize()
              .padding(1.5.dp)
          )
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

@Composable
private fun MiniPlayerSwipeCues(
  dragOffsetXProvider: () -> Float,
  swipeThresholdPx: Float,
  accentColor: Color,
  modifier: Modifier = Modifier
) {
  Row(
    modifier = modifier,
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
  ) {
    Icon(
      imageVector = Icons.Rounded.SkipPrevious,
      contentDescription = null,
      tint = accentColor,
      modifier = Modifier
        .size(24.dp)
        .graphicsLayer {
          val frac = (dragOffsetXProvider() / swipeThresholdPx).coerceIn(0f, 1f)
          alpha = if (frac > 0.04f) frac else 0f
          val s = 0.85f + 0.25f * frac
          scaleX = s
          scaleY = s
        }
    )
    Icon(
      imageVector = Icons.Rounded.SkipNext,
      contentDescription = null,
      tint = accentColor,
      modifier = Modifier
        .size(24.dp)
        .graphicsLayer {
          val frac = (-dragOffsetXProvider() / swipeThresholdPx).coerceIn(0f, 1f)
          alpha = if (frac > 0.04f) frac else 0f
          val s = 0.85f + 0.25f * frac
          scaleX = s
          scaleY = s
        }
    )
  }
}

@Composable
private fun MiniPlayerProgressRing(
  positionFlow: StateFlow<Long>?,
  fallbackPositionMs: Long,
  totalMs: Long,
  trackColor: Color,
  accentColor: Color,
  modifier: Modifier = Modifier
) {
  val livePositionState = positionFlow?.collectAsStateWithLifecycle()
  Canvas(modifier = modifier) {
    val posMs = livePositionState?.value ?: fallbackPositionMs
    val progress = (posMs.toFloat() / totalMs.toFloat()).coerceIn(0f, 1f)
    val strokePx = 2.5.dp.toPx()
    drawArc(
      color = trackColor,
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
}
