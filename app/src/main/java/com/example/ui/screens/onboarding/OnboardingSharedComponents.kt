package com.example.ui.screens.onboarding

import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.model.Song
import com.example.ui.theme.AurixDesignTokens
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeChild
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Pure swipe physics helpers matching Section 5:
 * - Drag starts after 8px (8dp) horizontal movement
 * - 30% resistance at first & last slide
 * - Release advances if drag exceeds 18% of viewport width
 */
object OnboardingSwipePhysics {
  const val DRAG_START_SLOP_DP = 8f
  const val EDGE_RESISTANCE = 0.30f
  const val ADVANCE_THRESHOLD_FRACTION = 0.18f

  fun applyEdgeResistance(rawDragPx: Float, currentIndex: Int, pageCount: Int): Float {
    val pushingLeftEdge = currentIndex == 0 && rawDragPx > 0f
    val pushingRightEdge = currentIndex == pageCount - 1 && rawDragPx < 0f
    return if (pushingLeftEdge || pushingRightEdge) rawDragPx * EDGE_RESISTANCE else rawDragPx
  }

  fun resolveTargetIndex(
    rawDragPx: Float,
    viewportWidthPx: Float,
    currentIndex: Int,
    pageCount: Int
  ): Int {
    if (viewportWidthPx <= 1f || pageCount <= 1) return currentIndex
    val threshold = viewportWidthPx * ADVANCE_THRESHOLD_FRACTION
    return when {
      rawDragPx <= -threshold && currentIndex < pageCount - 1 -> currentIndex + 1
      rawDragPx >= threshold && currentIndex > 0 -> currentIndex - 1
      else -> currentIndex
    }
  }
}

/**
 * Reads the real local file extension (MP3, FLAC, M4A, WAV, OGG, AAC) for Slide 1's "On your phone" badge.
 */
fun extractLocalAudioFormatBadge(song: Song?): String {
  val candidates = listOfNotNull(song?.streamUrl, song?.title)
  val known = listOf("FLAC", "MP3", "M4A", "WAV", "OGG", "AAC", "OPUS")
  for (raw in candidates) {
    val clean = raw.substringBefore('?').substringBefore('#')
    val ext = clean.substringAfterLast('.', "").trim().uppercase()
    if (ext in known) return ext
  }
  return "MP3"
}

/**
 * Shared dark Liquid Glass modifier:
 * 1dp border rgba(255,255,255,.14), fill rgba(255,255,255,.07), backdrop blur 14dp on Android 12+.
 */
fun Modifier.onboardingGlass(
  shape: Shape,
  hazeState: HazeState?,
  fillColor: Color = AurixDesignTokens.GlassFill,
  borderColor: Color = AurixDesignTokens.GlassBorder,
  borderWidth: Dp = 1.dp
): Modifier = composed {
  val hazeStyle = remember(fillColor) {
    HazeStyle(
      backgroundColor = AurixDesignTokens.Background,
      tint = HazeTint(fillColor),
      blurRadius = AurixDesignTokens.GlassBlurRadius,
      noiseFactor = 0f
    )
  }
  val base = this.clip(shape)
  val blurred = if (hazeState != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    base.hazeChild(state = hazeState, shape = shape, style = hazeStyle)
  } else {
    base.background(fillColor, shape)
  }
  blurred.border(borderWidth, borderColor, shape)
}

/**
 * Accessible keyboard/D-pad focus ring: 2dp solid white stroke offset 2dp outside the element.
 */
fun Modifier.onboardingFocusRing(shape: Shape): Modifier = composed {
  var isFocused by remember { mutableStateOf(false) }
  this
    .onFocusChanged { isFocused = it.isFocused }
    .drawWithContent {
      drawContent()
      if (isFocused) {
        val offsetPx = 2.dp.toPx()
        val strokePx = 2.dp.toPx()
        val outline = shape.createOutline(
          size = Size(size.width + offsetPx * 2f, size.height + offsetPx * 2f),
          layoutDirection = layoutDirection,
          density = this
        )
        withTransform({ translate(-offsetPx, -offsetPx) }) {
          drawOutline(
            outline = outline,
            color = Color.White,
            style = Stroke(width = strokePx)
          )
        }
      }
    }
}

/**
 * 4-bar animated equalizer matching Section 4 Slide 1 & Slide 4:
 * 3dp bars, Accent A color, scaleY 0.25 <-> 1, 800ms loop, staggered delays 0 / 200 / 400 / 100ms.
 */
@Composable
fun OnboardingEqualizer(
  accentColor: Color,
  isActive: Boolean,
  reducedMotion: Boolean,
  modifier: Modifier = Modifier,
  height: Dp = 16.dp
) {
  val delays = remember { intArrayOf(0, 200, 400, 100) }
  val staticHeights = remember { floatArrayOf(0.55f, 0.92f, 0.42f, 0.78f) }

  val transition = if (isActive && !reducedMotion) {
    rememberInfiniteTransition(label = "onboarding_eq")
  } else {
    null
  }
  val barStates = List(4) { idx ->
    transition?.animateFloat(
      initialValue = 0.25f,
      targetValue = 1.0f,
      animationSpec = infiniteRepeatable(
        animation = tween(durationMillis = 400, easing = FastOutSlowInEasing),
        repeatMode = RepeatMode.Reverse,
        initialStartOffset = androidx.compose.animation.core.StartOffset(delays[idx])
      ),
      label = "onboarding_eq_bar_$idx"
    )
  }

  Row(
    modifier = modifier.height(height),
    horizontalArrangement = Arrangement.spacedBy(2.5.dp),
    verticalAlignment = Alignment.Bottom
  ) {
    for (i in 0 until 4) {
      val state = barStates[i]
      Box(
        modifier = Modifier
          .width(3.dp)
          .height(height)
          .graphicsLayer {
            scaleY = state?.value ?: staticHeights[i]
            transformOrigin = TransformOrigin(0.5f, 1f)
          }
          .clip(RoundedCornerShape(99.dp))
          .background(accentColor)
      )
    }
  }
}

/**
 * Pixel-faithful Orbi Mascot (viewBox 120x120) matching Section 4 Slide 4:
 * - Light sphere (60, 62) r=30 radial gradient #FFFFFF -> #CFD8EA
 * - Two dark eyes #0B1220 ellipses rx=3.4 ry=4.6 at (50, 56) & (68, 56) blinking every 4s (scaleY 0.1 at 97%)
 * - Tilted (-15 deg) Saturn ring (46x12 ellipse, stroke #3B475C, width 5, round caps): back half behind, front half in front
 * - Headphones: cyan #43C7F4 arc r=31 from (29, 62) to (91, 62) stroke 5 + two ear cups (9x17, r=4 at x=23 & x=88, y=56)
 * - Entrance: scale 0.4 + rotate -25deg -> normal (1000ms SpringEasing), then floats up/down 7dp (3600ms loop).
 */
@Composable
fun OnboardingOrbiMascot(
  isActive: Boolean,
  reducedMotion: Boolean,
  replaySeed: Int,
  modifier: Modifier = Modifier
) {
  val enterScale = remember { Animatable(if (reducedMotion) 1f else 0.4f) }
  val enterRotate = remember { Animatable(if (reducedMotion) 0f else -25f) }

  LaunchedEffect(isActive, replaySeed, reducedMotion) {
    if (!isActive) return@LaunchedEffect
    if (reducedMotion) {
      enterScale.snapTo(1f)
      enterRotate.snapTo(0f)
    } else {
      enterScale.snapTo(0.4f)
      enterRotate.snapTo(-25f)
      launch {
        enterScale.animateTo(
          targetValue = 1f,
          animationSpec = tween(durationMillis = 1000, easing = AurixDesignTokens.SpringEasing)
        )
      }
      launch {
        enterRotate.animateTo(
          targetValue = 0f,
          animationSpec = tween(durationMillis = 1000, easing = AurixDesignTokens.SpringEasing)
        )
      }
    }
  }

  val loopTransition = if (isActive && !reducedMotion) {
    rememberInfiniteTransition(label = "orbi_mascot_loop")
  } else {
    null
  }
  val floatOffsetState = loopTransition?.animateFloat(
    initialValue = -7f,
    targetValue = 7f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = 1800, easing = FastOutSlowInEasing),
      repeatMode = RepeatMode.Reverse
    ),
    label = "orbi_float_y"
  )
  val eyeBlinkScaleYState = loopTransition?.animateFloat(
    initialValue = 1f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(
      animation = keyframes {
        durationMillis = 4000
        1.0f at 0
        1.0f at 3800
        0.1f at 3880 // 97% of 4000ms
        1.0f at 3980
        1.0f at 4000
      },
      repeatMode = RepeatMode.Restart
    ),
    label = "orbi_blink"
  )

  Canvas(
    modifier = modifier.graphicsLayer {
      val s = enterScale.value
      scaleX = s
      scaleY = s
      rotationZ = enterRotate.value
      translationY = (floatOffsetState?.value ?: 0f) * density
    }
  ) {
    val sx = size.width / 120f
    val sy = size.height / 120f
    val u = minOf(sx, sy)
    val ox = (size.width - 120f * u) * 0.5f
    val oy = (size.height - 120f * u) * 0.5f

    fun px(x: Float) = ox + x * u
    fun py(y: Float) = oy + y * u

    val ringRect = Rect(
      left = px(60f - 46f),
      top = py(62f - 12f),
      right = px(60f + 46f),
      bottom = py(62f + 12f)
    )
    val ringStroke = Stroke(width = 5f * u, cap = StrokeCap.Round)
    val ringColor = Color(0xFF3B475C)

    // 1) Back half of tilted (-15deg) Saturn ring (180..360 deg = top/back arc)
    rotate(degrees = -15f, pivot = Offset(px(60f), py(62f))) {
      drawArc(
        color = ringColor,
        startAngle = 180f,
        sweepAngle = 180f,
        useCenter = false,
        topLeft = ringRect.topLeft,
        size = ringRect.size,
        style = ringStroke
      )
    }

    // 2) Light sphere: center (60, 62), r=30, radial gradient #FFFFFF -> #CFD8EA
    drawCircle(
      brush = Brush.radialGradient(
        colors = listOf(Color(0xFFFFFFFF), Color(0xFFCFD8EA)),
        center = Offset(px(52f), py(50f)),
        radius = 40f * u
      ),
      radius = 30f * u,
      center = Offset(px(60f), py(62f))
    )

    // 3) Eyes #0B1220: ellipses rx=3.4 ry=4.6 at (50, 56) & (68, 56), blinking every 4s
    val blinkY = eyeBlinkScaleYState?.value ?: 1f
    val eyeColor = Color(0xFF0B1220)
    val eyeRx = 3.4f * u
    val eyeRy = (4.6f * blinkY).coerceAtLeast(0.45f) * u
    drawOval(
      color = eyeColor,
      topLeft = Offset(px(50f) - eyeRx, py(56f) - eyeRy),
      size = Size(eyeRx * 2f, eyeRy * 2f)
    )
    drawOval(
      color = eyeColor,
      topLeft = Offset(px(68f) - eyeRx, py(56f) - eyeRy),
      size = Size(eyeRx * 2f, eyeRy * 2f)
    )

    // 4) Front half of tilted (-15deg) Saturn ring (0..180 deg = bottom/front arc)
    rotate(degrees = -15f, pivot = Offset(px(60f), py(62f))) {
      drawArc(
        color = ringColor,
        startAngle = 0f,
        sweepAngle = 180f,
        useCenter = false,
        topLeft = ringRect.topLeft,
        size = ringRect.size,
        style = ringStroke
      )
    }

    // 5) Headphones: cyan #43C7F4 band arc r=31 from (29, 62) to (91, 62), stroke 5 + 2 ear cups (9x17 r=4 at x=23 & x=88, y=56)
    val headphoneColor = Color(0xFF43C7F4)
    val bandRect = Rect(
      left = px(60f - 31f),
      top = py(62f - 31f),
      right = px(60f + 31f),
      bottom = py(62f + 31f)
    )
    drawArc(
      color = headphoneColor,
      startAngle = 180f,
      sweepAngle = 180f,
      useCenter = false,
      topLeft = bandRect.topLeft,
      size = bandRect.size,
      style = Stroke(width = 5f * u, cap = StrokeCap.Round)
    )
    val cupRadius = CornerRadius(4f * u, 4f * u)
    val cupSize = Size(9f * u, 17f * u)
    drawRoundRect(
      color = headphoneColor,
      topLeft = Offset(px(23f), py(56f)),
      size = cupSize,
      cornerRadius = cupRadius
    )
    drawRoundRect(
      color = headphoneColor,
      topLeft = Offset(px(88f), py(56f)),
      size = cupSize,
      cornerRadius = cupRadius
    )
  }
}

/**
 * Custom crisp icons with exact stroke weights from the spec.
 */
@Composable
fun OnboardingArrowLeftIcon(
  color: Color,
  modifier: Modifier = Modifier.size(22.dp),
  strokeDp: Dp = 2.4.dp
) {
  Canvas(modifier = modifier) {
    val w = size.width
    val h = size.height
    val stroke = Stroke(width = strokeDp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
    drawLine(color, Offset(w * 0.78f, h * 0.5f), Offset(w * 0.22f, h * 0.5f), stroke.width, StrokeCap.Round)
    val chevron = Path().apply {
      moveTo(w * 0.48f, h * 0.22f)
      lineTo(w * 0.20f, h * 0.50f)
      lineTo(w * 0.48f, h * 0.78f)
    }
    drawPath(chevron, color, style = stroke)
  }
}

@Composable
fun OnboardingArrowRightIcon(
  color: Color,
  modifier: Modifier = Modifier.size(20.dp),
  strokeDp: Dp = 2.6.dp
) {
  Canvas(modifier = modifier) {
    val w = size.width
    val h = size.height
    val stroke = Stroke(width = strokeDp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
    drawLine(color, Offset(w * 0.20f, h * 0.5f), Offset(w * 0.78f, h * 0.5f), stroke.width, StrokeCap.Round)
    val chevron = Path().apply {
      moveTo(w * 0.52f, h * 0.22f)
      lineTo(w * 0.80f, h * 0.50f)
      lineTo(w * 0.52f, h * 0.78f)
    }
    drawPath(chevron, color, style = stroke)
  }
}

@Composable
fun OnboardingMagnifierIcon(
  color: Color = AurixDesignTokens.TextMuted,
  modifier: Modifier = Modifier.size(18.dp)
) {
  Canvas(modifier = modifier) {
    val w = size.width
    val h = size.height
    val strokePx = 1.9.dp.toPx()
    drawCircle(
      color = color,
      radius = w * 0.31f,
      center = Offset(w * 0.43f, h * 0.43f),
      style = Stroke(width = strokePx)
    )
    drawLine(
      color = color,
      start = Offset(w * 0.66f, h * 0.66f),
      end = Offset(w * 0.88f, h * 0.88f),
      strokeWidth = strokePx,
      cap = StrokeCap.Round
    )
  }
}

@Composable
fun OnboardingSpeakerIcon(
  soundOn: Boolean,
  color: Color,
  modifier: Modifier = Modifier.size(20.dp)
) {
  Canvas(modifier = modifier) {
    val w = size.width
    val h = size.height
    val stroke = Stroke(width = 1.9.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
    val body = Path().apply {
      moveTo(w * 0.16f, h * 0.38f)
      lineTo(w * 0.34f, h * 0.38f)
      lineTo(w * 0.56f, h * 0.20f)
      lineTo(w * 0.56f, h * 0.80f)
      lineTo(w * 0.34f, h * 0.62f)
      lineTo(w * 0.16f, h * 0.62f)
      close()
    }
    drawPath(body, color, style = stroke)
    if (soundOn) {
      drawArc(
        color = color,
        startAngle = -45f,
        sweepAngle = 90f,
        useCenter = false,
        topLeft = Offset(w * 0.52f, h * 0.32f),
        size = Size(w * 0.24f, h * 0.36f),
        style = stroke
      )
      drawArc(
        color = color,
        startAngle = -48f,
        sweepAngle = 96f,
        useCenter = false,
        topLeft = Offset(w * 0.58f, h * 0.18f),
        size = Size(w * 0.34f, h * 0.64f),
        style = stroke
      )
    }
  }
}

@Composable
fun OnboardingMusicNoteIcon(
  color: Color = Color.White,
  modifier: Modifier = Modifier.size(64.dp),
  strokeDp: Dp = 1.8.dp
) {
  Canvas(modifier = modifier) {
    val w = size.width
    val h = size.height
    val stroke = Stroke(width = strokeDp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
    // Two beamed eighth notes
    val stemPath = Path().apply {
      moveTo(w * 0.36f, h * 0.72f)
      lineTo(w * 0.36f, h * 0.25f)
      lineTo(w * 0.78f, h * 0.18f)
      lineTo(w * 0.78f, h * 0.64f)
    }
    drawPath(stemPath, color, style = stroke)
    drawLine(
      color = color,
      start = Offset(w * 0.36f, h * 0.37f),
      end = Offset(w * 0.78f, h * 0.30f),
      strokeWidth = stroke.width,
      cap = StrokeCap.Round
    )
    drawOval(
      color = color,
      topLeft = Offset(w * 0.19f, h * 0.65f),
      size = Size(w * 0.18f, h * 0.14f),
      style = stroke
    )
    drawOval(
      color = color,
      topLeft = Offset(w * 0.61f, h * 0.58f),
      size = Size(w * 0.18f, h * 0.14f),
      style = stroke
    )
  }
}
