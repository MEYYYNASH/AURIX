package com.example.ui.screens

import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.OrbiAppIcon
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Universal Orbi motion easing: cubic-bezier(.45, .05, .3, 1).
 * Used across all Orbi animations, status rows, and skeleton sweeps.
 */
val OrbiEasing = CubicBezierEasing(0.45f, 0.05f, 0.3f, 1f)

/**
 * Orbi Mascot States.
 */
enum class OrbiState {
  Idle,
  Scanning,
  Happy,
  Sad,
  Dim,
  Intro,
  Empty,
  NoInternet,
  PlaybackError,
  UpdateAvailable,
  UpdateDownloading,
  UpdateInstalling,
  UpdateSuccess,
  UpdateFailed
}

/**
 * Orbiting mini icons that travel along Orbi's tilted (-16 deg) ring ellipse,
 * passing behind the planet on the upper half and in front on the lower half.
 */
enum class OrbiRunner(
  val label: String,
  val startColor: Color,
  val endColor: Color,
  val glowColor: Color
) {
  None(
    label = "None",
    startColor = Color.Transparent,
    endColor = Color.Transparent,
    glowColor = Color.Transparent
  ),
  Search(
    label = "Search",
    startColor = Color(0xFF38E8FF),
    endColor = Color(0xFF3B7BFF),
    glowColor = Color(0xFF4CC9FF)
  ),
  Music(
    label = "Music",
    startColor = Color(0xFFFFD447),
    endColor = Color(0xFFFF7A1A),
    glowColor = Color(0xFFFFA938)
  ),
  Refresh(
    label = "Refresh",
    startColor = Color(0xFF9F6BFF),
    endColor = Color(0xFF4F7CFF),
    glowColor = Color(0xFF7A6BFF)
  ),
  SyncUp(
    label = "Sync Up",
    startColor = Color(0xFF42F2FF),
    endColor = Color(0xFF06B6D4),
    glowColor = Color(0xFF22D3EE)
  ),
  SyncDown(
    label = "Sync Down",
    startColor = Color(0xFFFF66B5),
    endColor = Color(0xFFE11D74),
    glowColor = Color(0xFFF43F8A)
  )
}

/**
 * Status row outcome state for smooth crossfade from loading to finished.
 */
sealed class OrbiStatusOutcome {
  data object Loading : OrbiStatusOutcome()
  data class Success(val message: String) : OrbiStatusOutcome()
  data class Error(val message: String) : OrbiStatusOutcome()
}

/**
 * Detects whether animations should be paused or disabled based on:
 * - System "Remove animations" setting (ANIMATOR_DURATION_SCALE == 0f)
 * - App-level `reducedMotion` preference
 * - Lifecycle visibility (stops animations when screen is not visible/started)
 */
@Composable
fun rememberOrbiAnimationsEnabled(reducedMotion: Boolean = false): Boolean {
  val context = LocalContext.current
  val isInspection = LocalInspectionMode.current
  val lifecycleOwner = LocalLifecycleOwner.current

  val systemAnimatorScale = remember(context, isInspection) {
    if (isInspection) 1f
    else runCatching {
      Settings.Global.getFloat(
        context.contentResolver,
        Settings.Global.ANIMATOR_DURATION_SCALE,
        1f
      )
    }.getOrDefault(1f)
  }

  var isLifecycleStarted by remember(lifecycleOwner) {
    mutableStateOf(
      lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
    )
  }

  DisposableEffect(lifecycleOwner) {
    val observer = LifecycleEventObserver { _, _ ->
      isLifecycleStarted = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose {
      lifecycleOwner.lifecycle.removeObserver(observer)
    }
  }

  return !reducedMotion && !isInspection && systemAnimatorScale > 0f && isLifecycleStarted
}

/**
 * ORBI MASCOT (One reusable composable: OrbiView(state, size, runner))
 *
 * Visual & Motion Specifications:
 * - White-lavender planet (vertical gradient white to #9F9EF4) with two tall
 *   black oval eyes, each with a small white highlight, tilted -12 degrees.
 * - Tilted ring (-16 degrees) with a gradient stroke (#5CE6FF, #4F7CFF, #7A5CFF, #A585FF).
 *   Back half is drawn behind the planet and front half in front.
 * - Always on: slow float (+-4dp, 3.4s), blink every ~4s.
 * - States:
 *   - Scanning: eyes glance left, up, right, down in turn
 *   - Happy / UpdateSuccess: eyes become arcs, a hop with squash and stretch, ring turns green
 *   - Sad / NoInternet / PlaybackError / UpdateFailed: a tear falls, a short shake, ring turns red
 *   - Dim / Empty: ring at 30% opacity
 * - Orbiting Mini Icons (OrbiRunner):
 *   - Travels along the ring's own -16 deg ellipse, passing behind the planet and then in front.
 *   - Glossy badge: gradient disc, 1.4dp white 55% outline, soft glow behind, highlight arc on top, bold white glyph.
 */
@Composable
fun OrbiView(
  state: OrbiState = OrbiState.Scanning,
  size: Dp = 88.dp,
  runner: OrbiRunner = OrbiRunner.None,
  modifier: Modifier = Modifier,
  reducedMotion: Boolean = false,
  downloadProgress: Float = 0.62f
) {
  val animationsEnabled = rememberOrbiAnimationsEnabled(reducedMotion)
  val accentColor = MaterialTheme.colorScheme.primary

  val isHappy = state == OrbiState.Happy || state == OrbiState.UpdateSuccess
  val isSad = state == OrbiState.Sad ||
    state == OrbiState.NoInternet ||
    state == OrbiState.PlaybackError ||
    state == OrbiState.UpdateFailed
  val isDim = state == OrbiState.Dim || state == OrbiState.Empty
  val isScanning = state == OrbiState.Scanning ||
    state == OrbiState.UpdateAvailable ||
    state == OrbiState.UpdateDownloading ||
    state == OrbiState.UpdateInstalling

  val happyWeight by animateFloatAsState(
    targetValue = if (isHappy) 1f else 0f,
    animationSpec = if (!animationsEnabled) snap() else tween(420, easing = OrbiEasing),
    label = "orbi_happy_weight"
  )
  val sadWeight by animateFloatAsState(
    targetValue = if (isSad) 1f else 0f,
    animationSpec = if (!animationsEnabled) snap() else tween(420, easing = OrbiEasing),
    label = "orbi_sad_weight"
  )
  val ringAlpha by animateFloatAsState(
    targetValue = if (isDim) 0.30f else 1f,
    animationSpec = if (!animationsEnabled) snap() else tween(420, easing = OrbiEasing),
    label = "orbi_ring_alpha"
  )

  val infiniteTransition = rememberInfiniteTransition(label = "orbi_mascot_infinite")

  // Always-on slow float: +-4dp over 3.4s cycle (1700ms half-wave with Reverse)
  val floatPhase = if (!animationsEnabled) {
    0f
  } else {
    val anim by infiniteTransition.animateFloat(
      initialValue = -1f,
      targetValue = 1f,
      animationSpec = infiniteRepeatable(
        animation = tween(durationMillis = 1700, easing = OrbiEasing),
        repeatMode = RepeatMode.Reverse
      ),
      label = "orbi_float_y"
    )
    anim
  }

  // Blink every ~4s (4000ms loop)
  val blinkClock = if (!animationsEnabled) {
    0.5f
  } else {
    val anim by infiniteTransition.animateFloat(
      initialValue = 0f,
      targetValue = 1f,
      animationSpec = infiniteRepeatable(
        animation = tween(durationMillis = 4000, easing = OrbiEasing),
        repeatMode = RepeatMode.Restart
      ),
      label = "orbi_blink_clock"
    )
    anim
  }
  val blinkScaleY = when {
    !animationsEnabled -> 1f
    blinkClock < 0.025f -> 1f - (blinkClock / 0.025f) * 0.90f
    blinkClock < 0.055f -> 0.10f + ((blinkClock - 0.025f) / 0.030f) * 0.90f
    else -> 1f
  }

  // Orbiting runner phase along the ring ellipse
  val orbitDuration = if (state == OrbiState.UpdateInstalling) 1400 else 2350
  val orbitPhase = if (!animationsEnabled) {
    0.16f
  } else {
    val anim by infiniteTransition.animateFloat(
      initialValue = 0f,
      targetValue = 1f,
      animationSpec = infiniteRepeatable(
        animation = tween(durationMillis = orbitDuration, easing = OrbiEasing),
        repeatMode = RepeatMode.Restart
      ),
      label = "orbi_runner_orbit"
    )
    anim
  }

  // Scanning: eyes glance left, up, right, down in turn
  val glanceX = remember { Animatable(0f) }
  val glanceY = remember { Animatable(0f) }

  LaunchedEffect(isScanning, animationsEnabled) {
    if (!animationsEnabled || !isScanning) {
      glanceX.snapTo(0f)
      glanceY.snapTo(0f)
      return@LaunchedEffect
    }
    while (true) {
      launch { glanceX.animateTo(-1f, tween(340, easing = OrbiEasing)) }
      glanceY.animateTo(0f, tween(340, easing = OrbiEasing))
      delay(220)
      launch { glanceX.animateTo(0f, tween(340, easing = OrbiEasing)) }
      glanceY.animateTo(-1f, tween(340, easing = OrbiEasing))
      delay(220)
      launch { glanceX.animateTo(1f, tween(340, easing = OrbiEasing)) }
      glanceY.animateTo(0f, tween(340, easing = OrbiEasing))
      delay(220)
      launch { glanceX.animateTo(0f, tween(340, easing = OrbiEasing)) }
      glanceY.animateTo(1f, tween(340, easing = OrbiEasing))
      delay(220)
      launch { glanceX.animateTo(0f, tween(280, easing = OrbiEasing)) }
      glanceY.animateTo(0f, tween(280, easing = OrbiEasing))
      delay(180)
    }
  }

  // Happy: hop with squash and stretch
  val hopOffsetY = remember { Animatable(0f) }
  val squashScaleX = remember { Animatable(1f) }
  val squashScaleY = remember { Animatable(1f) }

  LaunchedEffect(isHappy, state, animationsEnabled) {
    if (!animationsEnabled) {
      hopOffsetY.snapTo(0f)
      squashScaleX.snapTo(1f)
      squashScaleY.snapTo(1f)
      return@LaunchedEffect
    }
    if (isHappy || state == OrbiState.Intro) {
      while (true) {
        launch { squashScaleX.animateTo(1.12f, tween(130, easing = OrbiEasing)) }
        squashScaleY.animateTo(0.88f, tween(130, easing = OrbiEasing))
        launch { squashScaleX.animateTo(0.92f, tween(210, easing = OrbiEasing)) }
        launch { squashScaleY.animateTo(1.10f, tween(210, easing = OrbiEasing)) }
        hopOffsetY.animateTo(-1f, tween(220, easing = OrbiEasing))
        launch { squashScaleX.animateTo(1.0f, tween(140, easing = OrbiEasing)) }
        launch { squashScaleY.animateTo(1.0f, tween(140, easing = OrbiEasing)) }
        hopOffsetY.animateTo(0f, tween(230, easing = OrbiEasing))
        launch { squashScaleX.animateTo(1.09f, tween(110, easing = OrbiEasing)) }
        squashScaleY.animateTo(0.91f, tween(110, easing = OrbiEasing))
        launch { squashScaleX.animateTo(1f, tween(180, easing = OrbiEasing)) }
        squashScaleY.animateTo(1f, tween(180, easing = OrbiEasing))
        delay(1750)
      }
    } else {
      launch { hopOffsetY.animateTo(0f, tween(240, easing = OrbiEasing)) }
      launch { squashScaleX.animateTo(1f, tween(240, easing = OrbiEasing)) }
      squashScaleY.animateTo(1f, tween(240, easing = OrbiEasing))
    }
  }

  // Sad: short horizontal shake + falling tear
  val shakeOffsetX = remember { Animatable(0f) }
  val tearFallProgress = remember { Animatable(0f) }

  LaunchedEffect(isSad, animationsEnabled) {
    if (!animationsEnabled || !isSad) {
      shakeOffsetX.snapTo(0f)
      tearFallProgress.snapTo(if (isSad) 0.55f else 0f)
      return@LaunchedEffect
    }
    while (true) {
      launch {
        shakeOffsetX.animateTo(
          targetValue = 0f,
          animationSpec = keyframes {
            durationMillis = 520
            0f at 0 using OrbiEasing
            -1f at 90 using OrbiEasing
            1f at 190 using OrbiEasing
            -0.75f at 290 using OrbiEasing
            0.55f at 390 using OrbiEasing
            0f at 520 using OrbiEasing
          }
        )
      }
      tearFallProgress.snapTo(0f)
      tearFallProgress.animateTo(1f, tween(durationMillis = 1550, easing = OrbiEasing))
      delay(450)
    }
  }

  val effectiveRunner = when {
    runner != OrbiRunner.None -> runner
    state == OrbiState.UpdateAvailable || state == OrbiState.UpdateDownloading -> OrbiRunner.SyncDown
    state == OrbiState.UpdateInstalling -> OrbiRunner.Refresh
    else -> OrbiRunner.None
  }

  Canvas(
    modifier = modifier
      .size(size)
      .graphicsLayer {
        val floatPx = floatPhase * 4.dp.toPx()
        val hopPx = hopOffsetY.value * 8.dp.toPx()
        val shakePx = shakeOffsetX.value * 5.dp.toPx()
        translationX = shakePx
        translationY = floatPx + hopPx
        scaleX = squashScaleX.value
        scaleY = squashScaleY.value
      }
  ) {
    if (this.size.minDimension <= 0f) return@Canvas
    val s = this.size.minDimension / 100f
    val cx = this.size.width / 2f
    val cy = this.size.height / 2f

    val planetRadius = 25.5f * s
    val ringRx = 42.5f * s
    val ringRy = 13.8f * s
    val ringStrokeWidth = 5.4f * s

    val baseRingColors = listOf(
      Color(0xFF5CE6FF),
      Color(0xFF4F7CFF),
      Color(0xFF7A5CFF),
      Color(0xFFA585FF)
    )
    val happyRingColors = listOf(
      Color(0xFF34D399),
      Color(0xFF22C55E),
      Color(0xFF10B981),
      Color(0xFF6EE7B7)
    )
    val sadRingColors = listOf(
      Color(0xFFFB7185),
      Color(0xFFEF4444),
      Color(0xFFF43F5E),
      Color(0xFFFDA4AF)
    )

    val resolvedRingColors = baseRingColors.indices.map { i ->
      val c0 = baseRingColors[i]
      val c1 = lerpColor(c0, happyRingColors[i], happyWeight)
      lerpColor(c1, sadRingColors[i], sadWeight).copy(alpha = ringAlpha)
    }

    val ringBrush = Brush.linearGradient(
      colors = resolvedRingColors,
      start = Offset(cx - ringRx, cy - ringRy),
      end = Offset(cx + ringRx, cy + ringRy)
    )

    val haloColor = when {
      happyWeight > 0.5f -> Color(0xFF22C55E)
      sadWeight > 0.5f -> Color(0xFFEF4444)
      else -> accentColor
    }
    drawCircle(
      brush = Brush.radialGradient(
        colors = listOf(
          haloColor.copy(alpha = 0.16f * ringAlpha),
          Color(0xFF7A5CFF).copy(alpha = 0.06f * ringAlpha),
          Color.Transparent
        ),
        center = Offset(cx, cy),
        radius = planetRadius * 1.70f
      ),
      radius = planetRadius * 1.70f,
      center = Offset(cx, cy)
    )

    val runnerAngleRad = (orbitPhase * 2f * PI).toFloat()
    val runnerInBack = sin(runnerAngleRad.toDouble()) < 0.0

    // 1. BACK HALF OF TILTED RING (-16 degrees) + BACK ORBITING RUNNER
    rotate(degrees = -16f, pivot = Offset(cx, cy)) {
      drawArc(
        brush = ringBrush,
        startAngle = 180f,
        sweepAngle = 180f,
        useCenter = false,
        topLeft = Offset(cx - ringRx, cy - ringRy),
        size = Size(ringRx * 2f, ringRy * 2f),
        style = Stroke(width = ringStrokeWidth, cap = StrokeCap.Round)
      )
    }

    if (effectiveRunner != OrbiRunner.None && runnerInBack) {
      drawOrbitingRunnerOnEllipse(
        runner = effectiveRunner,
        cx = cx,
        cy = cy,
        ringRx = ringRx,
        ringRy = ringRy,
        ringTiltDeg = -16f,
        angleRad = runnerAngleRad,
        scaleUnit = s
      )
    }

    // 2. PLANET BODY (White-lavender vertical gradient white to #9F9EF4) & EYES (-12 deg)
    drawCircle(
      color = Color(0xFF0A0D1E).copy(alpha = 0.22f),
      radius = planetRadius * 1.02f,
      center = Offset(cx, cy + 2.4f * s)
    )

    val planetBrush = Brush.verticalGradient(
      colors = listOf(
        Color.White,
        Color(0xFFE7E7FD),
        Color(0xFF9F9EF4)
      ),
      startY = cy - planetRadius,
      endY = cy + planetRadius
    )
    drawCircle(
      brush = planetBrush,
      radius = planetRadius,
      center = Offset(cx, cy)
    )

    drawCircle(
      brush = Brush.radialGradient(
        colors = listOf(
          Color.White.copy(alpha = 0.70f),
          Color.Transparent
        ),
        center = Offset(cx - planetRadius * 0.32f, cy - planetRadius * 0.36f),
        radius = planetRadius * 0.72f
      ),
      radius = planetRadius,
      center = Offset(cx, cy)
    )

    drawCircle(
      color = Color.White.copy(alpha = 0.55f),
      radius = planetRadius,
      center = Offset(cx, cy),
      style = Stroke(width = 0.9f * s)
    )

    rotate(degrees = -12f, pivot = Offset(cx, cy)) {
      val gDx = glanceX.value * 4.2f * s
      val gDy = glanceY.value * 3.4f * s

      val leftEyeCx = cx - 7.2f * s + gDx
      val leftEyeCy = cy - 3.6f * s + gDy
      val rightEyeCx = cx + 7.2f * s + gDx
      val rightEyeCy = cy - 3.6f * s + gDy

      val eyeRx = 3.35f * s
      val eyeRy = 6.2f * s * blinkScaleY.coerceAtLeast(0.08f)
      val eyeColor = Color(0xFF141522)

      if (happyWeight > 0.5f) {
        val arcWidth = 7.4f * s
        val arcHeight = 6.2f * s
        val arcStroke = 2.4f * s

        drawArc(
          color = eyeColor,
          startAngle = 195f,
          sweepAngle = 150f,
          useCenter = false,
          topLeft = Offset(leftEyeCx - arcWidth / 2f, leftEyeCy - arcHeight / 2f),
          size = Size(arcWidth, arcHeight),
          style = Stroke(width = arcStroke, cap = StrokeCap.Round)
        )
        drawArc(
          color = eyeColor,
          startAngle = 195f,
          sweepAngle = 150f,
          useCenter = false,
          topLeft = Offset(rightEyeCx - arcWidth / 2f, rightEyeCy - arcHeight / 2f),
          size = Size(arcWidth, arcHeight),
          style = Stroke(width = arcStroke, cap = StrokeCap.Round)
        )
      } else {
        drawOval(
          color = eyeColor,
          topLeft = Offset(leftEyeCx - eyeRx, leftEyeCy - eyeRy),
          size = Size(eyeRx * 2f, eyeRy * 2f)
        )
        drawOval(
          color = eyeColor,
          topLeft = Offset(rightEyeCx - eyeRx, rightEyeCy - eyeRy),
          size = Size(eyeRx * 2f, eyeRy * 2f)
        )

        if (blinkScaleY > 0.45f) {
          val hlRadius = 1.25f * s
          drawCircle(
            color = Color.White.copy(alpha = 0.94f),
            radius = hlRadius,
            center = Offset(leftEyeCx - eyeRx * 0.26f, leftEyeCy - eyeRy * 0.36f)
          )
          drawCircle(
            color = Color.White.copy(alpha = 0.94f),
            radius = hlRadius,
            center = Offset(rightEyeCx - eyeRx * 0.26f, rightEyeCy - eyeRy * 0.36f)
          )
        }
      }

      if (sadWeight > 0.1f) {
        val tearT = tearFallProgress.value.coerceIn(0f, 1f)
        val tearAlpha = (when {
          tearT < 0.2f -> tearT / 0.2f
          tearT > 0.8f -> (1f - tearT) / 0.2f
          else -> 1f
        } * sadWeight).coerceIn(0f, 1f)

        val tearX = rightEyeCx + 2.2f * s
        val tearY = rightEyeCy + eyeRy + (tearT * 11.5f * s)
        val tearR = 2.15f * s

        val tearPath = Path().apply {
          moveTo(tearX, tearY - tearR * 1.45f)
          cubicTo(
            tearX + tearR * 1.15f,
            tearY - tearR * 0.2f,
            tearX + tearR * 1.05f,
            tearY + tearR,
            tearX,
            tearY + tearR
          )
          cubicTo(
            tearX - tearR * 1.05f,
            tearY + tearR,
            tearX - tearR * 1.15f,
            tearY - tearR * 0.2f,
            tearX,
            tearY - tearR * 1.45f
          )
          close()
        }
        drawPath(
          path = tearPath,
          brush = Brush.verticalGradient(
            colors = listOf(
              Color(0xFF93E8FF).copy(alpha = tearAlpha),
              Color(0xFF38BDF8).copy(alpha = tearAlpha)
            ),
            startY = tearY - tearR * 1.5f,
            endY = tearY + tearR
          )
        )
      }
    }

    // 3. FRONT HALF OF TILTED RING (-16 degrees) + FRONT ORBITING RUNNER
    rotate(degrees = -16f, pivot = Offset(cx, cy)) {
      drawArc(
        brush = ringBrush,
        startAngle = 0f,
        sweepAngle = 180f,
        useCenter = false,
        topLeft = Offset(cx - ringRx, cy - ringRy),
        size = Size(ringRx * 2f, ringRy * 2f),
        style = Stroke(width = ringStrokeWidth, cap = StrokeCap.Round)
      )

      if (state == OrbiState.UpdateDownloading) {
        val clampedProg = downloadProgress.coerceIn(0.05f, 1f)
        drawArc(
          color = Color(0xFF38F8D4),
          startAngle = 0f,
          sweepAngle = 180f * clampedProg,
          useCenter = false,
          topLeft = Offset(cx - ringRx, cy - ringRy),
          size = Size(ringRx * 2f, ringRy * 2f),
          style = Stroke(width = ringStrokeWidth * 1.08f, cap = StrokeCap.Round)
        )
      }
    }

    if (effectiveRunner != OrbiRunner.None && !runnerInBack) {
      drawOrbitingRunnerOnEllipse(
        runner = effectiveRunner,
        cx = cx,
        cy = cy,
        ringRx = ringRx,
        ringRy = ringRy,
        ringTiltDeg = -16f,
        angleRad = runnerAngleRad,
        scaleUnit = s
      )
    }
  }
}

private fun DrawScope.drawOrbitingRunnerOnEllipse(
  runner: OrbiRunner,
  cx: Float,
  cy: Float,
  ringRx: Float,
  ringRy: Float,
  ringTiltDeg: Float,
  angleRad: Float,
  scaleUnit: Float
) {
  val localX = ringRx * cos(angleRad)
  val localY = ringRy * sin(angleRad)
  val tiltRad = (ringTiltDeg * PI / 180.0).toFloat()
  val cosT = cos(tiltRad)
  val sinT = sin(tiltRad)

  val worldX = cx + (localX * cosT - localY * sinT)
  val worldY = cy + (localX * sinT + localY * cosT)

  val depthFactor = 0.90f + 0.14f * sin(angleRad)
  val badgeRadius = 9.4f * scaleUnit * depthFactor
  val badgeCenter = Offset(worldX, worldY)

  // 1. Soft glow behind
  drawCircle(
    brush = Brush.radialGradient(
      colors = listOf(
        runner.glowColor.copy(alpha = 0.52f),
        runner.glowColor.copy(alpha = 0.14f),
        Color.Transparent
      ),
      center = badgeCenter,
      radius = badgeRadius * 1.75f
    ),
    radius = badgeRadius * 1.75f,
    center = badgeCenter
  )

  // 2. Gradient disc
  drawCircle(
    brush = Brush.linearGradient(
      colors = listOf(runner.startColor, runner.endColor),
      start = Offset(badgeCenter.x - badgeRadius, badgeCenter.y - badgeRadius),
      end = Offset(badgeCenter.x + badgeRadius, badgeCenter.y + badgeRadius)
    ),
    radius = badgeRadius,
    center = badgeCenter
  )

  // 3. Highlight arc on top
  val hlInset = badgeRadius * 0.22f
  drawArc(
    brush = Brush.verticalGradient(
      colors = listOf(
        Color.White.copy(alpha = 0.62f),
        Color.White.copy(alpha = 0.05f)
      ),
      startY = badgeCenter.y - badgeRadius,
      endY = badgeCenter.y
    ),
    startAngle = 200f,
    sweepAngle = 140f,
    useCenter = false,
    topLeft = Offset(badgeCenter.x - badgeRadius + hlInset, badgeCenter.y - badgeRadius + hlInset),
    size = Size((badgeRadius - hlInset) * 2f, (badgeRadius - hlInset) * 2f),
    style = Stroke(width = 1.3f * scaleUnit, cap = StrokeCap.Round)
  )

  // 4. 1.4dp white 55% outline
  val outlineStrokePx = (1.4.dp.toPx()).coerceAtMost(1.75f * scaleUnit).coerceAtLeast(1f)
  drawCircle(
    color = Color.White.copy(alpha = 0.55f),
    radius = badgeRadius,
    center = badgeCenter,
    style = Stroke(width = outlineStrokePx)
  )

  // 5. Bold white glyph
  val glyphStroke = (1.95f * scaleUnit * depthFactor).coerceAtLeast(1.6f)
  when (runner) {
    OrbiRunner.Search -> {
      val loopR = badgeRadius * 0.34f
      val loopCenter = Offset(badgeCenter.x - badgeRadius * 0.10f, badgeCenter.y - badgeRadius * 0.10f)
      drawCircle(
        color = Color.White,
        radius = loopR,
        center = loopCenter,
        style = Stroke(width = glyphStroke)
      )
      drawLine(
        color = Color.White,
        start = Offset(loopCenter.x + loopR * 0.72f, loopCenter.y + loopR * 0.72f),
        end = Offset(badgeCenter.x + badgeRadius * 0.42f, badgeCenter.y + badgeRadius * 0.42f),
        strokeWidth = glyphStroke,
        cap = StrokeCap.Round
      )
    }

    OrbiRunner.Music -> {
      val noteR = badgeRadius * 0.21f
      val headCenter = Offset(badgeCenter.x - badgeRadius * 0.14f, badgeCenter.y + badgeRadius * 0.24f)
      drawCircle(
        color = Color.White,
        radius = noteR,
        center = headCenter
      )
      val stemX = headCenter.x + noteR * 0.82f
      val stemTopY = badgeCenter.y - badgeRadius * 0.42f
      val stemBottomY = headCenter.y
      drawLine(
        color = Color.White,
        start = Offset(stemX, stemBottomY),
        end = Offset(stemX, stemTopY),
        strokeWidth = glyphStroke,
        cap = StrokeCap.Round
      )
      drawLine(
        color = Color.White,
        start = Offset(stemX, stemTopY),
        end = Offset(stemX + badgeRadius * 0.34f, stemTopY + badgeRadius * 0.18f),
        strokeWidth = glyphStroke,
        cap = StrokeCap.Round
      )
    }

    OrbiRunner.Refresh -> {
      val arcR = badgeRadius * 0.42f
      drawArc(
        color = Color.White,
        startAngle = 35f,
        sweepAngle = 275f,
        useCenter = false,
        topLeft = Offset(badgeCenter.x - arcR, badgeCenter.y - arcR),
        size = Size(arcR * 2f, arcR * 2f),
        style = Stroke(width = glyphStroke, cap = StrokeCap.Round)
      )
      val tipX = badgeCenter.x + arcR * 0.85f
      val tipY = badgeCenter.y - arcR * 0.40f
      val headPath = Path().apply {
        moveTo(tipX - badgeRadius * 0.26f, tipY - badgeRadius * 0.06f)
        lineTo(tipX, tipY)
        lineTo(tipX + badgeRadius * 0.04f, tipY - badgeRadius * 0.28f)
      }
      drawPath(
        path = headPath,
        color = Color.White,
        style = Stroke(width = glyphStroke, cap = StrokeCap.Round, join = StrokeJoin.Round)
      )
    }

    OrbiRunner.SyncUp -> {
      val topY = badgeCenter.y - badgeRadius * 0.42f
      val botY = badgeCenter.y + badgeRadius * 0.42f
      val wing = badgeRadius * 0.34f
      drawLine(
        color = Color.White,
        start = Offset(badgeCenter.x, botY),
        end = Offset(badgeCenter.x, topY),
        strokeWidth = glyphStroke,
        cap = StrokeCap.Round
      )
      val arrowPath = Path().apply {
        moveTo(badgeCenter.x - wing, topY + wing * 0.85f)
        lineTo(badgeCenter.x, topY)
        lineTo(badgeCenter.x + wing, topY + wing * 0.85f)
      }
      drawPath(
        path = arrowPath,
        color = Color.White,
        style = Stroke(width = glyphStroke, cap = StrokeCap.Round, join = StrokeJoin.Round)
      )
    }

    OrbiRunner.SyncDown -> {
      val topY = badgeCenter.y - badgeRadius * 0.42f
      val botY = badgeCenter.y + badgeRadius * 0.42f
      val wing = badgeRadius * 0.34f
      drawLine(
        color = Color.White,
        start = Offset(badgeCenter.x, topY),
        end = Offset(badgeCenter.x, botY),
        strokeWidth = glyphStroke,
        cap = StrokeCap.Round
      )
      val arrowPath = Path().apply {
        moveTo(badgeCenter.x - wing, botY - wing * 0.85f)
        lineTo(badgeCenter.x, botY)
        lineTo(badgeCenter.x + wing, botY - wing * 0.85f)
      }
      drawPath(
        path = arrowPath,
        color = Color.White,
        style = Stroke(width = glyphStroke, cap = StrokeCap.Round, join = StrokeJoin.Round)
      )
    }

    OrbiRunner.None -> Unit
  }
}

private fun lerpColor(start: Color, stop: Color, fraction: Float): Color {
  val t = fraction.coerceIn(0f, 1f)
  return Color(
    red = start.red + (stop.red - start.red) * t,
    green = start.green + (stop.green - start.green) * t,
    blue = start.blue + (stop.blue - start.blue) * t,
    alpha = start.alpha + (stop.alpha - start.alpha) * t
  )
}

// =============================================================================
// SHARED SKELETON SHIMMER SYSTEM (ONE InfiniteTransition per screen)
// =============================================================================

private data class OrbiSharedShimmerState(
  val phase: Float,
  val animationsEnabled: Boolean
)

private val LocalOrbiSharedShimmer = compositionLocalOf<OrbiSharedShimmerState?> { null }

/**
 * Provides ONE shared InfiniteTransition (1.6s diagonal sweep) for all skeletons on a screen.
 */
@Composable
fun ProvideOrbiShimmer(
  reducedMotion: Boolean = false,
  content: @Composable () -> Unit
) {
  val animationsEnabled = rememberOrbiAnimationsEnabled(reducedMotion)
  val infiniteTransition = rememberInfiniteTransition(label = "orbi_shared_skeleton_shimmer")
  val phase = if (!animationsEnabled) {
    0.35f
  } else {
    val anim by infiniteTransition.animateFloat(
      initialValue = 0f,
      targetValue = 1f,
      animationSpec = infiniteRepeatable(
        animation = tween(durationMillis = 1600, easing = OrbiEasing),
        repeatMode = RepeatMode.Restart
      ),
      label = "orbi_shimmer_phase"
    )
    anim
  }

  val state = remember(phase, animationsEnabled) {
    OrbiSharedShimmerState(phase = phase, animationsEnabled = animationsEnabled)
  }

  CompositionLocalProvider(LocalOrbiSharedShimmer provides state) {
    content()
  }
}

/**
 * Skeleton shimmer primitive:
 * - Diagonal highlight sweep (1.6s) per element, staggered ~110ms per row (`staggerIndex`).
 * - Shares ONE infinite transition across the screen when inside [ProvideOrbiShimmer],
 *   or falls back cleanly to a local transition if called standalone.
 * - Uses theme tokens so shimmer follows the user's accent color across Dark, Light, and AMOLED.
 */
fun Modifier.shimmer(
  staggerIndex: Int = 0,
  reducedMotion: Boolean = false,
  cornerRadius: Dp = 12.dp
): Modifier = composed {
  val provided = LocalOrbiSharedShimmer.current
  val localEnabled = rememberOrbiAnimationsEnabled(reducedMotion)
  val animationsEnabled = provided?.animationsEnabled ?: localEnabled

  val rawPhase = if (provided != null) {
    provided.phase
  } else if (!animationsEnabled) {
    0.35f
  } else {
    val transition = rememberInfiniteTransition(label = "orbi_fallback_shimmer")
    val anim by transition.animateFloat(
      initialValue = 0f,
      targetValue = 1f,
      animationSpec = infiniteRepeatable(
        animation = tween(durationMillis = 1600, easing = OrbiEasing),
        repeatMode = RepeatMode.Restart
      ),
      label = "orbi_fallback_phase"
    )
    anim
  }

  val accentColor = MaterialTheme.colorScheme.primary
  val bgColor = MaterialTheme.colorScheme.background
  val isLight = bgColor.luminance() > 0.5f
  val isAmoled = bgColor.luminance() < 0.005f

  // Clean sketch-style translucent skeleton fill + diagonal shimmer sweep (no heavy card boxes)
  val baseColor = when {
    isLight -> Color(0xFF94A3B8).copy(alpha = 0.18f)
    isAmoled -> Color.White.copy(alpha = 0.08f)
    else -> Color.White.copy(alpha = 0.09f)
  }
  val borderColor = when {
    isLight -> Color(0xFF64748B).copy(alpha = 0.14f)
    else -> Color.White.copy(alpha = 0.06f)
  }
  val highlightColor = when {
    isLight -> lerpColor(Color.White, accentColor, 0.25f).copy(alpha = 0.78f)
    else -> lerpColor(Color(0xFF475569), accentColor, 0.35f).copy(alpha = 0.48f)
  }

  // Staggered ~110ms per row out of 1600ms cycle
  val staggerFraction = (staggerIndex * 110f) / 1600f
  val effectivePhase = ((rawPhase - staggerFraction) % 1f + 1f) % 1f

  this
    .clip(RoundedCornerShape(cornerRadius))
    .drawWithCache {
      val cr = CornerRadius(cornerRadius.toPx(), cornerRadius.toPx())
      val strokePx = 0.8.dp.toPx()
      onDrawBehind {
        drawRoundRect(
          color = baseColor,
          cornerRadius = cr
        )
        if (animationsEnabled) {
          val sweepSpan = size.width + size.height * 1.2f
          val startX = -sweepSpan * 0.45f + effectivePhase * (sweepSpan * 1.9f)
          val brush = Brush.linearGradient(
            colors = listOf(
              Color.Transparent,
              highlightColor.copy(alpha = highlightColor.alpha * 0.45f),
              highlightColor,
              highlightColor.copy(alpha = highlightColor.alpha * 0.45f),
              Color.Transparent
            ),
            start = Offset(startX - size.height * 0.4f, 0f),
            end = Offset(startX + size.height * 0.6f, size.height)
          )
          drawRoundRect(
            brush = brush,
            cornerRadius = cr
          )
        }
        drawRoundRect(
          color = borderColor,
          cornerRadius = cr,
          style = Stroke(width = strokePx)
        )
      }
    }
}

/**
 * Animates a skeleton row entrance: rows fade and slide in one after another (80ms apart)
 * using only transform and alpha with [OrbiEasing].
 */
fun Modifier.orbiStaggeredRowEntrance(
  rowIndex: Int,
  reducedMotion: Boolean = false
): Modifier = composed {
  val animationsEnabled = rememberOrbiAnimationsEnabled(reducedMotion)
  val enterProgress = remember(rowIndex, animationsEnabled) {
    Animatable(if (animationsEnabled) 0f else 1f)
  }

  LaunchedEffect(rowIndex, animationsEnabled) {
    if (!animationsEnabled) {
      enterProgress.snapTo(1f)
    } else {
      delay(rowIndex * 80L)
      enterProgress.animateTo(
        targetValue = 1f,
        animationSpec = tween(durationMillis = 420, easing = OrbiEasing)
      )
    }
  }

  this.graphicsLayer {
    val p = enterProgress.value.coerceIn(0f, 1f)
    alpha = p
    translationY = (1f - p) * 14.dp.toPx()
  }
}

// =============================================================================
// 1. INLINE STATUS ROW (Frameless / No Background Card — Orbi 88dp + text)
// =============================================================================

/**
 * Inline status row (Orbi 88dp + text) — Frameless & transparent (NO opaque card box!),
 * matching the reference screenshot where Orbi and "Searching YouTube..." float directly
 * on the screen surface.
 *
 * Features:
 * - Text is bold 22sp, gray-blue #8A93B4, with a light shimmer sweeping across (2.2s).
 * - Three dots bounce one after another (1.2s loop).
 * - A thin cyan-to-violet line slides along the bottom edge.
 * - On finish, the text crossfades with a blur and slide to
 *   "24 results found" (green, Orbi happy) or "Can't reach YouTube" (red, Orbi sad).
 */
@Composable
fun StatusRow(
  text: String,
  modifier: Modifier = Modifier,
  runner: OrbiRunner = OrbiRunner.Search,
  outcome: OrbiStatusOutcome = OrbiStatusOutcome.Loading,
  orbiSize: Dp = 34.dp,
  showBottomLine: Boolean = false,
  reducedMotion: Boolean = false
) {
  val animationsEnabled = rememberOrbiAnimationsEnabled(reducedMotion)
  val accentColor = MaterialTheme.colorScheme.primary
  val isLight = MaterialTheme.colorScheme.background.luminance() > 0.5f

  // Strip trailing "..." from text so we don't render double dots next to the animated bouncing dots
  val cleanLabel = remember(text) {
    text.trim().removeSuffix("...").removeSuffix("…").trim()
  }

  val derivedOrbiState = when (outcome) {
    is OrbiStatusOutcome.Loading -> OrbiState.Scanning
    is OrbiStatusOutcome.Success -> OrbiState.Happy
    is OrbiStatusOutcome.Error -> OrbiState.Sad
  }
  val activeRunner = when (outcome) {
    is OrbiStatusOutcome.Loading -> runner
    else -> OrbiRunner.None
  }

  val infiniteTransition = rememberInfiniteTransition(label = "orbi_status_row_transition")

  // Text light shimmer sweep across (2.2s)
  val textShimmerPhase = if (!animationsEnabled) {
    0.5f
  } else {
    val anim by infiniteTransition.animateFloat(
      initialValue = 0f,
      targetValue = 1f,
      animationSpec = infiniteRepeatable(
        animation = tween(durationMillis = 2200, easing = OrbiEasing),
        repeatMode = RepeatMode.Restart
      ),
      label = "status_text_shimmer"
    )
    anim
  }

  // Three dots bounce one after another (1.2s loop)
  val dotsPhase = if (!animationsEnabled) {
    0f
  } else {
    val anim by infiniteTransition.animateFloat(
      initialValue = 0f,
      targetValue = 1f,
      animationSpec = infiniteRepeatable(
        animation = tween(durationMillis = 1200, easing = OrbiEasing),
        repeatMode = RepeatMode.Restart
      ),
      label = "status_dots_bounce"
    )
    anim
  }

  // Thin cyan-to-violet line sliding along the bottom edge (optional)
  val bottomLinePhase = if (!animationsEnabled || !showBottomLine) {
    0.35f
  } else {
    val anim by infiniteTransition.animateFloat(
      initialValue = 0f,
      targetValue = 1f,
      animationSpec = infiniteRepeatable(
        animation = tween(durationMillis = 1850, easing = OrbiEasing),
        repeatMode = RepeatMode.Restart
      ),
      label = "status_bottom_line"
    )
    anim
  }

  Column(
    modifier = modifier
      .fillMaxWidth()
      .testTag("orbi_status_row")
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 4.dp, vertical = 2.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      OrbiView(
        state = derivedOrbiState,
        size = orbiSize,
        runner = activeRunner,
        reducedMotion = reducedMotion
      )

      Box(
        modifier = Modifier.weight(1f),
        contentAlignment = Alignment.CenterStart
      ) {
        AnimatedContent(
          targetState = outcome,
          transitionSpec = {
            if (!animationsEnabled) {
              fadeIn(snap()) togetherWith fadeOut(snap())
            } else {
              (fadeIn(tween(380, easing = OrbiEasing)) +
                slideInVertically(tween(380, easing = OrbiEasing)) { it / 2 }) togetherWith
                (fadeOut(tween(280, easing = OrbiEasing)) +
                  slideOutVertically(tween(280, easing = OrbiEasing)) { -it / 2 }) using
                SizeTransform(clip = false)
            }
          },
          label = "orbi_status_text_crossfade"
        ) { targetOutcome ->
          val transitionBlurAnim = remember(targetOutcome) {
            Animatable(if (animationsEnabled && targetOutcome !is OrbiStatusOutcome.Loading) 6f else 0f)
          }
          LaunchedEffect(targetOutcome, animationsEnabled) {
            if (animationsEnabled && transitionBlurAnim.value > 0f) {
              transitionBlurAnim.animateTo(0f, tween(340, easing = OrbiEasing))
            } else {
              transitionBlurAnim.snapTo(0f)
            }
          }

          when (targetOutcome) {
            is OrbiStatusOutcome.Loading -> {
              val baseGrayBlue = if (isLight) Color(0xFF5D678A) else Color(0xFF8A93B4)
              val shimmerPeak = if (isLight) Color(0xFF1E293B) else Color(0xFFEFF3FF)
              val shimmerCenter = -0.35f + textShimmerPhase * 1.70f
              val textBrush = Brush.linearGradient(
                colorStops = arrayOf(
                  0.0f to baseGrayBlue,
                  (shimmerCenter - 0.18f).coerceIn(0f, 1f) to baseGrayBlue,
                  shimmerCenter.coerceIn(0f, 1f) to shimmerPeak,
                  (shimmerCenter + 0.18f).coerceIn(0f, 1f) to baseGrayBlue,
                  1.0f to baseGrayBlue
                ),
                start = Offset(0f, 0f),
                end = Offset(340f, 24f)
              )

              Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(1.5.dp)
              ) {
                Text(
                  text = cleanLabel,
                  style = TextStyle(
                    brush = textBrush,
                    fontSize = 15.sp,
                    lineHeight = 19.sp,
                    fontWeight = FontWeight.Bold
                  ),
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                  modifier = Modifier.weight(1f, fill = false)
                )

                // Three mini dots bouncing one after another (1.2s loop)
                Row(
                  modifier = Modifier.padding(bottom = 3.dp, start = 1.5.dp),
                  horizontalArrangement = Arrangement.spacedBy(2.dp),
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  for (dotIdx in 0 until 3) {
                    val shifted = ((dotsPhase - dotIdx * 0.16f) % 1f + 1f) % 1f
                    val bounce = if (!animationsEnabled) {
                      0f
                    } else if (shifted < 0.45f) {
                      sin((shifted / 0.45f) * PI).toFloat()
                    } else {
                      0f
                    }
                    Box(
                      modifier = Modifier
                        .graphicsLayer {
                          translationY = -bounce * 3.dp.toPx()
                          alpha = 0.60f + 0.40f * bounce
                        }
                        .size(3.dp)
                        .clip(CircleShape)
                        .background(lerpColor(baseGrayBlue, shimmerPeak, bounce * 0.6f))
                    )
                  }
                }
              }
            }

            is OrbiStatusOutcome.Success -> {
              val greenColor = if (isLight) Color(0xFF15803D) else Color(0xFF34D399)
              Text(
                text = targetOutcome.message,
                style = TextStyle(
                  color = greenColor,
                  fontSize = 15.sp,
                  lineHeight = 19.sp,
                  fontWeight = FontWeight.Bold
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.blur(transitionBlurAnim.value.dp)
              )
            }

            is OrbiStatusOutcome.Error -> {
              val redColor = if (isLight) Color(0xFFB91C1C) else Color(0xFFFB7185)
              Text(
                text = targetOutcome.message,
                style = TextStyle(
                  color = redColor,
                  fontSize = 15.sp,
                  lineHeight = 19.sp,
                  fontWeight = FontWeight.Bold
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.blur(transitionBlurAnim.value.dp)
              )
            }
          }
        }
      }
    }

    if (showBottomLine) {
      // Optional thin cyan-to-violet line sliding along the bottom edge
      Canvas(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 8.dp)
          .height(1.5.dp)
      ) {
        val lineColors = when (outcome) {
          is OrbiStatusOutcome.Success -> listOf(
            Color.Transparent,
            Color(0xFF34D399),
            Color(0xFF22C55E),
            Color.Transparent
          )
          is OrbiStatusOutcome.Error -> listOf(
            Color.Transparent,
            Color(0xFFFB7185),
            Color(0xFFEF4444),
            Color.Transparent
          )
          is OrbiStatusOutcome.Loading -> listOf(
            Color.Transparent,
            Color(0xFF5CE6FF),
            lerpColor(Color(0xFF4F7CFF), accentColor, 0.35f),
            Color(0xFF8B5CF6),
            Color.Transparent
          )
        }

        val segmentW = size.width * 0.55f
        val startX = -segmentW + bottomLinePhase * (size.width + segmentW)
        drawRect(
          brush = Brush.horizontalGradient(
            colors = lineColors,
            startX = startX,
            endX = startX + segmentW
          ),
          topLeft = Offset(0f, 0f),
          size = size
        )
      }
    }
  }
}

/**
 * Alias for [StatusRow] so callers can use either `StatusRow` or `OrbiStatusRow`.
 */
@Composable
fun OrbiStatusRow(
  text: String,
  modifier: Modifier = Modifier,
  runner: OrbiRunner = OrbiRunner.Search,
  outcome: OrbiStatusOutcome = OrbiStatusOutcome.Loading,
  orbiSize: Dp = 34.dp,
  reducedMotion: Boolean = false
) {
  StatusRow(
    text = text,
    modifier = modifier,
    runner = runner,
    outcome = outcome,
    orbiSize = orbiSize,
    reducedMotion = reducedMotion
  )
}

// =============================================================================
// SKELETON ROW & ALBUM CARD PRIMITIVES (Mini Sketch-style — NO background card box!)
// =============================================================================

/**
 * Mini Sketch Skeleton Track Row:
 * - NO outer card background or border box.
 * - Sleek 46dp rounded cover, two compact text lines, and small 20dp circle floating directly on the screen.
 * - Diagonal shimmer staggered ~110ms per row, entrance staggered 80ms apart.
 */
@Composable
fun OrbiSkeletonTrackRow(
  rowIndex: Int,
  modifier: Modifier = Modifier,
  titleWidthFraction: Float = 0.72f,
  subtitleWidthFraction: Float = 0.46f,
  reducedMotion: Boolean = false
) {
  Row(
    modifier = modifier
      .fillMaxWidth()
      .orbiStaggeredRowEntrance(rowIndex = rowIndex, reducedMotion = reducedMotion)
      .padding(horizontal = 6.dp, vertical = 4.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    // 46dp compact cover skeleton
    Box(
      modifier = Modifier
        .size(46.dp)
        .shimmer(
          staggerIndex = rowIndex,
          reducedMotion = reducedMotion,
          cornerRadius = 12.dp
        )
    )

    // Two compact text lines
    Column(
      modifier = Modifier.weight(1f),
      verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
      Box(
        modifier = Modifier
          .fillMaxWidth(titleWidthFraction)
          .height(11.dp)
          .shimmer(
            staggerIndex = rowIndex,
            reducedMotion = reducedMotion,
            cornerRadius = 5.5.dp
          )
      )
      Box(
        modifier = Modifier
          .fillMaxWidth(subtitleWidthFraction)
          .height(8.5.dp)
          .shimmer(
            staggerIndex = rowIndex,
            reducedMotion = reducedMotion,
            cornerRadius = 4.5.dp
          )
      )
    }

    // Small circle on the right
    Box(
      modifier = Modifier
        .size(20.dp)
        .shimmer(
          staggerIndex = rowIndex,
          reducedMotion = reducedMotion,
          cornerRadius = 10.dp
        )
    )
  }
}

/**
 * Compact Sketch Skeleton Album Card for the Data Loading screen (Library / Home):
 * - NO outer card container background or border box; just the cover square and two text lines.
 */
@Composable
fun OrbiSkeletonAlbumCard(
  cardIndex: Int,
  modifier: Modifier = Modifier,
  reducedMotion: Boolean = false
) {
  Column(
    modifier = modifier
      .orbiStaggeredRowEntrance(rowIndex = cardIndex, reducedMotion = reducedMotion)
      .padding(vertical = 2.dp),
    verticalArrangement = Arrangement.spacedBy(6.dp)
  ) {
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .height(78.dp)
        .shimmer(
          staggerIndex = cardIndex,
          reducedMotion = reducedMotion,
          cornerRadius = 14.dp
        )
    )
    Box(
      modifier = Modifier
        .fillMaxWidth(0.76f)
        .height(10.dp)
        .shimmer(
          staggerIndex = cardIndex,
          reducedMotion = reducedMotion,
          cornerRadius = 5.dp
        )
    )
    Box(
      modifier = Modifier
        .fillMaxWidth(0.50f)
        .height(8.dp)
        .shimmer(
          staggerIndex = cardIndex,
          reducedMotion = reducedMotion,
          cornerRadius = 4.dp
        )
    )
  }
}

// =============================================================================
// 2. SEARCH LOADING SCREEN
// =============================================================================

/**
 * Search loading screen:
 * - Optional search bar header with blinking caret
 * - The frameless inline [StatusRow] (Orbi 88dp + "Searching YouTube" / "Searching Audius")
 * - 6 frameless sketch skeleton track rows (52dp cover, two text lines, small circle) sharing ONE InfiniteTransition.
 */
@Composable
fun OrbiSearchLoadingScreen(
  statusText: String = "Searching YouTube",
  queryText: String = "",
  runner: OrbiRunner = OrbiRunner.Search,
  outcome: OrbiStatusOutcome = OrbiStatusOutcome.Loading,
  showSearchBarWithCaret: Boolean = true,
  reducedMotion: Boolean = false,
  modifier: Modifier = Modifier
) {
  val animationsEnabled = rememberOrbiAnimationsEnabled(reducedMotion)
  val accentColor = MaterialTheme.colorScheme.primary
  val isLight = MaterialTheme.colorScheme.background.luminance() > 0.5f

  val infiniteTransition = rememberInfiniteTransition(label = "orbi_search_caret")
  val caretAlpha = if (!animationsEnabled) {
    1f
  } else {
    val anim by infiniteTransition.animateFloat(
      initialValue = 1f,
      targetValue = 0.08f,
      animationSpec = infiniteRepeatable(
        animation = tween(durationMillis = 530, easing = OrbiEasing),
        repeatMode = RepeatMode.Reverse
      ),
      label = "search_caret_alpha"
    )
    anim
  }

  val titleFractions = remember {
    listOf(0.78f, 0.64f, 0.84f, 0.58f, 0.74f, 0.68f)
  }
  val subtitleFractions = remember {
    listOf(0.46f, 0.52f, 0.40f, 0.48f, 0.38f, 0.50f)
  }

  ProvideOrbiShimmer(reducedMotion = reducedMotion) {
    Column(
      modifier = modifier
        .fillMaxWidth()
        .testTag("orbi_search_loading_screen"),
      verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      if (showSearchBarWithCaret) {
        val barBg = if (isLight) Color.White.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.06f)
        val barBorder = if (isLight) accentColor.copy(alpha = 0.25f) else accentColor.copy(alpha = 0.32f)
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(999.dp))
            .background(barBg)
            .border(1.dp, barBorder, RoundedCornerShape(999.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          Icon(
            imageVector = Icons.Rounded.Search,
            contentDescription = null,
            tint = accentColor,
            modifier = Modifier.size(20.dp)
          )
          Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
          ) {
            if (queryText.isNotBlank()) {
              Text(
                text = queryText,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
            }
            Box(
              modifier = Modifier
                .padding(start = 2.dp)
                .width(2.dp)
                .height(18.dp)
                .graphicsLayer { alpha = caretAlpha }
                .clip(RoundedCornerShape(1.dp))
                .background(accentColor)
            )
            if (queryText.isBlank()) {
              Text(
                text = "Search tracks, artists, playlists...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                modifier = Modifier.padding(start = 6.dp),
                maxLines = 1
              )
            }
          }
        }
      }

      StatusRow(
        text = statusText,
        runner = runner,
        outcome = outcome,
        reducedMotion = reducedMotion
      )

      // 6 frameless sketch skeleton track rows
      for (i in 0 until 6) {
        OrbiSkeletonTrackRow(
          rowIndex = i,
          titleWidthFraction = titleFractions[i],
          subtitleWidthFraction = subtitleFractions[i],
          reducedMotion = reducedMotion
        )
      }
    }
  }
}

// =============================================================================
// 3. DATA LOADING SCREEN (LIBRARY / HOME)
// =============================================================================

/**
 * Data loading screen (library/home):
 * - Title skeleton
 * - Frameless inline [StatusRow]
 * - 3 frameless album card skeletons
 * - 4 frameless track rows
 */
@Composable
fun OrbiDataLoadingScreen(
  statusText: String = "Syncing your library",
  runner: OrbiRunner = OrbiRunner.SyncDown,
  outcome: OrbiStatusOutcome = OrbiStatusOutcome.Loading,
  reducedMotion: Boolean = false,
  modifier: Modifier = Modifier
) {
  val titleFractions = remember { listOf(0.76f, 0.62f, 0.82f, 0.68f) }
  val subtitleFractions = remember { listOf(0.44f, 0.50f, 0.38f, 0.46f) }

  ProvideOrbiShimmer(reducedMotion = reducedMotion) {
    Column(
      modifier = modifier
        .fillMaxWidth()
        .testTag("orbi_data_loading_screen"),
      verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      // Title skeleton bar
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 4.dp)
          .orbiStaggeredRowEntrance(rowIndex = 0, reducedMotion = reducedMotion),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Box(
            modifier = Modifier
              .width(168.dp)
              .height(20.dp)
              .shimmer(staggerIndex = 0, reducedMotion = reducedMotion, cornerRadius = 10.dp)
          )
          Box(
            modifier = Modifier
              .width(110.dp)
              .height(11.dp)
              .shimmer(staggerIndex = 1, reducedMotion = reducedMotion, cornerRadius = 5.5.dp)
          )
        }
        Box(
          modifier = Modifier
            .size(34.dp)
            .shimmer(staggerIndex = 1, reducedMotion = reducedMotion, cornerRadius = 17.dp)
        )
      }

      // Frameless StatusRow
      StatusRow(
        text = statusText,
        runner = runner,
        outcome = outcome,
        reducedMotion = reducedMotion
      )

      // 3 frameless album card skeletons
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        for (i in 0 until 3) {
          OrbiSkeletonAlbumCard(
            cardIndex = i + 1,
            modifier = Modifier.weight(1f),
            reducedMotion = reducedMotion
          )
        }
      }

      // 4 frameless track rows
      for (i in 0 until 4) {
        OrbiSkeletonTrackRow(
          rowIndex = i + 4,
          titleWidthFraction = titleFractions[i],
          subtitleWidthFraction = subtitleFractions[i],
          reducedMotion = reducedMotion
        )
      }
    }
  }
}

// =============================================================================
// 5. ADDITIONAL ORBI STATE CARDS
// =============================================================================

@Composable
fun OrbiScenarioCard(
  state: OrbiState,
  title: String,
  subtitle: String,
  modifier: Modifier = Modifier,
  runner: OrbiRunner = OrbiRunner.None,
  badgeText: String? = null,
  actionLabel: String? = null,
  onActionClick: (() -> Unit)? = null,
  downloadProgress: Float = 0.64f,
  reducedMotion: Boolean = false
) {
  val accentColor = MaterialTheme.colorScheme.primary
  val isLight = MaterialTheme.colorScheme.background.luminance() > 0.5f
  val isAmoled = MaterialTheme.colorScheme.background.luminance() < 0.005f

  val cardBg = when {
    isLight -> Color.White.copy(alpha = 0.90f)
    isAmoled -> Color(0xFF0A0D16).copy(alpha = 0.95f)
    else -> Color(0xFF13192B).copy(alpha = 0.90f)
  }
  val highlightTone = when (state) {
    OrbiState.Happy, OrbiState.UpdateSuccess -> Color(0xFF22C55E)
    OrbiState.Sad, OrbiState.NoInternet, OrbiState.PlaybackError, OrbiState.UpdateFailed -> Color(0xFFEF4444)
    OrbiState.Dim, OrbiState.Empty -> Color(0xFF8A93B4)
    else -> accentColor
  }

  Column(
    modifier = modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(26.dp))
      .background(cardBg)
      .border(1.dp, highlightTone.copy(alpha = 0.26f), RoundedCornerShape(26.dp))
      .padding(horizontal = 20.dp, vertical = 22.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    if (!badgeText.isNullOrBlank()) {
      Surface(
        shape = CircleShape,
        color = highlightTone.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, highlightTone.copy(alpha = 0.32f))
      ) {
        Text(
          text = badgeText,
          style = MaterialTheme.typography.labelSmall.copy(
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp
          ),
          color = highlightTone,
          modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
        )
      }
    }

    OrbiView(
      state = state,
      size = 96.dp,
      runner = runner,
      downloadProgress = downloadProgress,
      reducedMotion = reducedMotion
    )

    Text(
      text = title,
      style = MaterialTheme.typography.titleMedium.copy(
        fontSize = 19.sp,
        fontWeight = FontWeight.Bold
      ),
      color = MaterialTheme.colorScheme.onSurface
    )

    Text(
      text = subtitle,
      style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    if (state == OrbiState.UpdateDownloading) {
      val clamped = downloadProgress.coerceIn(0f, 1f)
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(top = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Text(
            text = "Downloading package",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
          Text(
            text = "${(clamped * 100).roundToInt()}%",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = highlightTone
          )
        }
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(CircleShape)
            .background(highlightTone.copy(alpha = 0.16f))
        ) {
          Box(
            modifier = Modifier
              .fillMaxWidth(clamped)
              .height(6.dp)
              .clip(CircleShape)
              .background(
                Brush.horizontalGradient(
                  listOf(Color(0xFF5CE6FF), highlightTone, Color(0xFFA585FF))
                )
              )
          )
        }
      }
    }

    if (actionLabel != null && onActionClick != null) {
      Surface(
        onClick = onActionClick,
        shape = CircleShape,
        color = highlightTone.copy(alpha = 0.16f),
        border = BorderStroke(1.dp, highlightTone.copy(alpha = 0.45f)),
        modifier = Modifier.minimumInteractiveComponentSize()
      ) {
        Text(
          text = actionLabel,
          style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
          color = highlightTone,
          modifier = Modifier.padding(horizontal = 18.dp, vertical = 9.dp)
        )
      }
    }
  }
}

// =============================================================================
// INTERACTIVE PREVIEW / SHOWCASE SCREEN & SHEET SHOWING EVERY ORBI STATE
// =============================================================================

private enum class OrbiShowcaseTab(val title: String) {
  MascotAndRunners("Mascot & Badges"),
  InlineStatus("Status Rows"),
  SearchLoading("Search Screen"),
  DataLoading("Library Screen"),
  SystemStates("App & Update States")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrbiMascotShowcaseSheet(
  darkTheme: Boolean,
  reducedMotion: Boolean,
  onDismiss: () -> Unit
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  val isAmoled = MaterialTheme.colorScheme.background.luminance() < 0.005f
  val containerBg = when {
    !darkTheme -> Color(0xFFF3F6FC)
    isAmoled -> Color(0xFF05070C)
    else -> Color(0xFF0B0F1C)
  }

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = containerBg,
    dragHandle = null
  ) {
    OrbiMascotPreviewScreen(
      reducedMotion = reducedMotion,
      onClose = onDismiss
    )
  }
}

@Composable
fun OrbiMascotPreviewScreen(
  modifier: Modifier = Modifier,
  reducedMotion: Boolean = false,
  onClose: (() -> Unit)? = null
) {
  val accentColor = MaterialTheme.colorScheme.primary
  var selectedTab by remember { mutableStateOf(OrbiShowcaseTab.MascotAndRunners) }
  var selectedState by remember { mutableStateOf(OrbiState.Scanning) }
  var selectedRunner by remember { mutableStateOf(OrbiRunner.Search) }

  val statusPresets = remember {
    listOf(
      "Searching YouTube" to OrbiRunner.Search,
      "Searching Audius" to OrbiRunner.Music,
      "Finding lyrics" to OrbiRunner.Music,
      "Syncing your library" to OrbiRunner.SyncUp,
      "Checking for updates" to OrbiRunner.Refresh
    )
  }
  var activePresetIndex by remember { mutableStateOf(0) }
  var statusOutcome by remember { mutableStateOf<OrbiStatusOutcome>(OrbiStatusOutcome.Loading) }
  val simulatedDownloadProgress by remember { mutableFloatStateOf(0.64f) }

  Column(
    modifier = modifier
      .fillMaxSize()
      .padding(horizontal = 16.dp, vertical = 14.dp)
      .testTag("orbi_mascot_preview_screen"),
    verticalArrangement = Arrangement.spacedBy(14.dp)
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        OrbiView(
          state = selectedState,
          size = 44.dp,
          runner = selectedRunner,
          reducedMotion = reducedMotion
        )
        Column {
          Text(
            text = "Orbi Animation Lab",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
            color = MaterialTheme.colorScheme.onBackground
          )
          Text(
            text = "1.9.4 • Canvas + Animatable • 60fps",
            style = MaterialTheme.typography.labelSmall,
            color = accentColor
          )
        }
      }

      if (onClose != null) {
        IconButton(
          onClick = onClose,
          modifier = Modifier
            .minimumInteractiveComponentSize()
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            .testTag("orbi_preview_close_button")
        ) {
          Icon(
            imageVector = Icons.Rounded.Close,
            contentDescription = "Close Orbi preview",
            tint = MaterialTheme.colorScheme.onSurface
          )
        }
      }
    }

    Row(
      modifier = Modifier
        .fillMaxWidth()
        .horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      OrbiShowcaseTab.entries.forEach { tab ->
        val isSelected = selectedTab == tab
        Surface(
          onClick = { selectedTab = tab },
          shape = CircleShape,
          color = if (isSelected) accentColor.copy(alpha = 0.18f)
          else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
          border = BorderStroke(
            1.dp,
            if (isSelected) accentColor else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
          )
        ) {
          Text(
            text = tab.title,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = if (isSelected) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
          )
        }
      }
    }

    LazyColumn(
      modifier = Modifier
        .weight(1f)
        .fillMaxWidth(),
      verticalArrangement = Arrangement.spacedBy(14.dp),
      contentPadding = PaddingValues(bottom = 28.dp)
    ) {
      when (selectedTab) {
        OrbiShowcaseTab.MascotAndRunners -> {
          item {
            Column(
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(26.dp))
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                .border(
                  1.dp,
                  MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f),
                  RoundedCornerShape(26.dp)
                )
                .padding(20.dp),
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
              OrbiView(
                state = selectedState,
                size = 148.dp,
                runner = selectedRunner,
                downloadProgress = simulatedDownloadProgress,
                reducedMotion = reducedMotion
              )

              Text(
                text = "State: ${selectedState.name}  •  Runner: ${selectedRunner.label}",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
              )

              Text(
                text = "ORBI STATES",
                style = MaterialTheme.typography.labelSmall.copy(
                  fontWeight = FontWeight.Bold,
                  letterSpacing = 1.sp
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                listOf(
                  OrbiState.Scanning,
                  OrbiState.Happy,
                  OrbiState.Sad,
                  OrbiState.Dim,
                  OrbiState.Idle
                ).forEach { st ->
                  val active = selectedState == st
                  Surface(
                    onClick = { selectedState = st },
                    shape = CircleShape,
                    color = if (active) accentColor.copy(alpha = 0.22f)
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f),
                    border = BorderStroke(
                      1.dp,
                      if (active) accentColor else Color.Transparent
                    )
                  ) {
                    Text(
                      text = st.name,
                      style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                      color = if (active) accentColor else MaterialTheme.colorScheme.onSurface,
                      modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                  }
                }
              }

              Text(
                text = "ORBITING MINI ICONS (ELLIPSE RUNNERS)",
                style = MaterialTheme.typography.labelSmall.copy(
                  fontWeight = FontWeight.Bold,
                  letterSpacing = 1.sp
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                OrbiRunner.entries.forEach { r ->
                  val active = selectedRunner == r
                  Surface(
                    onClick = { selectedRunner = r },
                    shape = CircleShape,
                    color = if (active) accentColor.copy(alpha = 0.22f)
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f),
                    border = BorderStroke(
                      1.dp,
                      if (active) accentColor else Color.Transparent
                    )
                  ) {
                    Text(
                      text = r.label,
                      style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                      color = if (active) accentColor else MaterialTheme.colorScheme.onSurface,
                      modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                  }
                }
              }
            }
          }
        }

        OrbiShowcaseTab.InlineStatus -> {
          item {
            val (currentLabel, currentRunner) = statusPresets[activePresetIndex]
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
              StatusRow(
                text = currentLabel,
                runner = currentRunner,
                outcome = statusOutcome,
                reducedMotion = reducedMotion
              )

              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                Surface(
                  onClick = { statusOutcome = OrbiStatusOutcome.Loading },
                  shape = CircleShape,
                  color = if (statusOutcome is OrbiStatusOutcome.Loading) accentColor.copy(alpha = 0.22f)
                  else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f),
                  border = BorderStroke(1.dp, accentColor.copy(alpha = 0.4f))
                ) {
                  Text(
                    text = "Scanning...",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                  )
                }
                Surface(
                  onClick = { statusOutcome = OrbiStatusOutcome.Success("24 results found") },
                  shape = CircleShape,
                  color = Color(0xFF22C55E).copy(alpha = 0.18f),
                  border = BorderStroke(1.dp, Color(0xFF22C55E).copy(alpha = 0.45f))
                ) {
                  Text(
                    text = "Finish: 24 results found",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color(0xFF34D399),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                  )
                }
                Surface(
                  onClick = { statusOutcome = OrbiStatusOutcome.Error("Can't reach YouTube") },
                  shape = CircleShape,
                  color = Color(0xFFEF4444).copy(alpha = 0.18f),
                  border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.45f))
                ) {
                  Text(
                    text = "Error: Can't reach YouTube",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color(0xFFFB7185),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                  )
                }
              }

              Text(
                text = "ALL STATUS ROW PRESETS",
                style = MaterialTheme.typography.labelSmall.copy(
                  fontWeight = FontWeight.Bold,
                  letterSpacing = 1.sp
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp)
              )
              statusPresets.forEachIndexed { idx, (presetText, presetRunner) ->
                StatusRow(
                  text = presetText,
                  runner = presetRunner,
                  outcome = OrbiStatusOutcome.Loading,
                  reducedMotion = reducedMotion,
                  modifier = Modifier.clickable {
                    activePresetIndex = idx
                    statusOutcome = OrbiStatusOutcome.Loading
                  }
                )
              }
            }
          }
        }

        OrbiShowcaseTab.SearchLoading -> {
          item {
            OrbiSearchLoadingScreen(
              statusText = "Searching YouTube",
              queryText = "midnight synthwave",
              runner = OrbiRunner.Search,
              outcome = statusOutcome,
              showSearchBarWithCaret = true,
              reducedMotion = reducedMotion
            )
          }
        }

        OrbiShowcaseTab.DataLoading -> {
          item {
            OrbiDataLoadingScreen(
              statusText = "Syncing your library",
              runner = OrbiRunner.SyncUp,
              outcome = OrbiStatusOutcome.Loading,
              reducedMotion = reducedMotion
            )
          }
        }

        OrbiShowcaseTab.SystemStates -> {
          item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
              OrbiScenarioCard(
                state = OrbiState.Intro,
                title = "Welcome to AURIX",
                subtitle = "Orbi splash & intro choreography with floating lavender planet and -16° gradient ring.",
                runner = OrbiRunner.Music,
                badgeText = "SPLASH / INTRO",
                reducedMotion = reducedMotion
              )
              OrbiScenarioCard(
                state = OrbiState.Empty,
                title = "Nothing here yet",
                subtitle = "Your playlist or search returned zero tracks. Ring dimmed to 30% opacity.",
                runner = OrbiRunner.None,
                badgeText = "EMPTY STATE (DIM 30%)",
                reducedMotion = reducedMotion
              )
              OrbiScenarioCard(
                state = OrbiState.NoInternet,
                title = "No Internet Connection",
                subtitle = "Can't reach YouTube or Audius right now. Check your Wi-Fi or mobile data.",
                runner = OrbiRunner.None,
                badgeText = "NO INTERNET",
                reducedMotion = reducedMotion
              )
              OrbiScenarioCard(
                state = OrbiState.PlaybackError,
                title = "Playback Error",
                subtitle = "This audio stream could not be decoded or is temporarily unavailable.",
                runner = OrbiRunner.None,
                badgeText = "PLAYBACK ERROR",
                reducedMotion = reducedMotion
              )
              OrbiScenarioCard(
                state = OrbiState.UpdateAvailable,
                title = "Update 1.9.4 Available",
                subtitle = "Mini Orbi loading row, 5 custom launcher icons, and compact 60fps sketch skeleton shimmer.",
                runner = OrbiRunner.SyncDown,
                badgeText = "UPDATE AVAILABLE",
                reducedMotion = reducedMotion
              )
              OrbiScenarioCard(
                state = OrbiState.UpdateDownloading,
                title = "Downloading AURIX 1.9.4",
                subtitle = "Fetching signed APK package in the background...",
                runner = OrbiRunner.SyncDown,
                badgeText = "DOWNLOADING",
                downloadProgress = simulatedDownloadProgress,
                reducedMotion = reducedMotion
              )
              OrbiScenarioCard(
                state = OrbiState.UpdateInstalling,
                title = "Installing Update",
                subtitle = "Verifying checksum and preparing package installer...",
                runner = OrbiRunner.Refresh,
                badgeText = "INSTALLING",
                reducedMotion = reducedMotion
              )
              OrbiScenarioCard(
                state = OrbiState.UpdateSuccess,
                title = "You're Up to Date!",
                subtitle = "AURIX 1.9.4 installed cleanly. Orbi is hopping happy!",
                runner = OrbiRunner.None,
                badgeText = "UPDATE SUCCESS",
                reducedMotion = reducedMotion
              )
              OrbiScenarioCard(
                state = OrbiState.UpdateFailed,
                title = "Update Failed",
                subtitle = "Checksum mismatch or interrupted network connection. Tap retry in Settings.",
                runner = OrbiRunner.None,
                badgeText = "UPDATE FAILED",
                reducedMotion = reducedMotion
              )
            }
          }
        }
      }
    }
  }
}

// =============================================================================
// COMPOSE PREVIEWS FOR EVERY ORBI STATE, RUNNER, STATUS ROW & LOADING SCREEN
// =============================================================================

@Preview(name = "Orbi States - Scanning / Happy / Sad / Dim", showBackground = true, backgroundColor = 0xFF0B0F1C, widthDp = 420, heightDp = 160)
@Composable
private fun PreviewOrbiStatesRow() {
  MaterialTheme {
    Row(
      modifier = Modifier
        .fillMaxSize()
        .padding(16.dp),
      horizontalArrangement = Arrangement.SpaceEvenly,
      verticalAlignment = Alignment.CenterVertically
    ) {
      OrbiView(state = OrbiState.Scanning, size = 80.dp, runner = OrbiRunner.Search)
      OrbiView(state = OrbiState.Happy, size = 80.dp, runner = OrbiRunner.None)
      OrbiView(state = OrbiState.Sad, size = 80.dp, runner = OrbiRunner.None)
      OrbiView(state = OrbiState.Dim, size = 80.dp, runner = OrbiRunner.None)
    }
  }
}

@Preview(name = "Orbi Runners - Search / Music / Refresh / SyncUp / SyncDown", showBackground = true, backgroundColor = 0xFF0B0F1C, widthDp = 460, heightDp = 140)
@Composable
private fun PreviewOrbiRunnersRow() {
  MaterialTheme {
    Row(
      modifier = Modifier
        .fillMaxSize()
        .padding(12.dp),
      horizontalArrangement = Arrangement.SpaceEvenly,
      verticalAlignment = Alignment.CenterVertically
    ) {
      OrbiView(state = OrbiState.Scanning, size = 72.dp, runner = OrbiRunner.Search)
      OrbiView(state = OrbiState.Scanning, size = 72.dp, runner = OrbiRunner.Music)
      OrbiView(state = OrbiState.Scanning, size = 72.dp, runner = OrbiRunner.Refresh)
      OrbiView(state = OrbiState.Scanning, size = 72.dp, runner = OrbiRunner.SyncUp)
      OrbiView(state = OrbiState.Scanning, size = 72.dp, runner = OrbiRunner.SyncDown)
    }
  }
}

@Preview(name = "StatusRow - Searching / Success / Error", showBackground = true, backgroundColor = 0xFF0B0F1C, widthDp = 412, heightDp = 340)
@Composable
private fun PreviewOrbiStatusRowStates() {
  MaterialTheme {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
      StatusRow(text = "Searching YouTube", runner = OrbiRunner.Search, outcome = OrbiStatusOutcome.Loading)
      StatusRow(text = "Searching YouTube", runner = OrbiRunner.Search, outcome = OrbiStatusOutcome.Success("24 results found"))
      StatusRow(text = "Searching YouTube", runner = OrbiRunner.Search, outcome = OrbiStatusOutcome.Error("Can't reach YouTube"))
    }
  }
}

@Preview(name = "Search Loading Screen (Frameless Sketch Skeletons)", showBackground = true, backgroundColor = 0xFF0B0F1C, widthDp = 412, heightDp = 720)
@Composable
private fun PreviewOrbiSearchLoadingScreen() {
  MaterialTheme {
    Box(modifier = Modifier.padding(16.dp)) {
      OrbiSearchLoadingScreen(
        statusText = "Searching YouTube",
        queryText = "vannDa time to rise",
        runner = OrbiRunner.Search
      )
    }
  }
}

@Preview(name = "Data Loading Screen (Library / Home Sketch Skeletons)", showBackground = true, backgroundColor = 0xFF0B0F1C, widthDp = 412, heightDp = 720)
@Composable
private fun PreviewOrbiDataLoadingScreen() {
  MaterialTheme {
    Box(modifier = Modifier.padding(16.dp)) {
      OrbiDataLoadingScreen(
        statusText = "Syncing your library",
        runner = OrbiRunner.SyncUp
      )
    }
  }
}

@Preview(name = "Orbi Animation Lab - Dark", showBackground = true, backgroundColor = 0xFF0B0F1C, widthDp = 412, heightDp = 892)
@Composable
private fun OrbiMascotPreviewDark() {
  MaterialTheme {
    OrbiMascotPreviewScreen()
  }
}

@Preview(name = "Orbi Animation Lab - Light", showBackground = true, backgroundColor = 0xFFF3F6FC, widthDp = 412, heightDp = 892)
@Composable
private fun OrbiMascotPreviewLight() {
  MaterialTheme {
    OrbiMascotPreviewScreen()
  }
}
