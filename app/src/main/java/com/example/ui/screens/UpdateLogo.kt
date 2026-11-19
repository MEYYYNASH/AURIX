package com.example.ui.screens

import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AurixTheme
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

enum class UpdateLogoState {
  Intro,
  Idle,
  Checking,
  Available,
  Downloading,
  Installing,
  Done,
  Error
}

private data class ConfettiSpec(
  val angleRad: Float,
  val maxDistance: Float,
  val size: Float,
  val color: Color,
  val spinOffset: Float
)

/**
 * Animated AURIX update icon drawn in a 208x208 design space (SVG viewBox 40 40 208 208),
 * scaled to `size` and shifted by (-40, -40).
 */
@Composable
fun UpdateLogo(
  state: UpdateLogoState,
  modifier: Modifier = Modifier,
  size: Dp = 84.dp,
  downloadProgress: Float = 0f,
  reducedMotion: Boolean = false
) {
  val context = LocalContext.current
  val isInspection = LocalInspectionMode.current
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
  val isStill = reducedMotion || systemAnimatorScale <= 0f

  val isIntroPreview = isInspection && state == UpdateLogoState.Intro

  val tileAlpha = remember { Animatable(if (state == UpdateLogoState.Intro && !isStill && !isInspection) 0f else 1f) }
  val tileScale = remember { Animatable(if (state == UpdateLogoState.Intro && !isStill && !isInspection) 0.9f else if (isIntroPreview) 0.97f else 1f) }
  val topRingTrim = remember { Animatable(if (state == UpdateLogoState.Intro && !isStill && !isInspection) 0f else if (isIntroPreview) 0.85f else 1f) }
  val bottomRingTrim = remember { Animatable(if (state == UpdateLogoState.Intro && !isStill && !isInspection) 0f else if (isIntroPreview) 0.55f else 1f) }
  val planetPopScale = remember { Animatable(if (state == UpdateLogoState.Intro && !isStill && !isInspection) 0.4f else if (isIntroPreview) 0.94f else 1f) }
  val eyePopScale = remember { Animatable(if (state == UpdateLogoState.Intro && !isStill && !isInspection) 0f else if (isIntroPreview) 0.85f else 1f) }

  val planetBounceScale = remember { Animatable(1f) }
  val planetHopY = remember { Animatable(0f) }
  val tileShakeX = remember { Animatable(0f) }
  val confettiProgress = remember { Animatable(0f) }
  val badgeScale = remember {
    Animatable(
      if (isInspection && state in listOf(
          UpdateLogoState.Available,
          UpdateLogoState.Downloading,
          UpdateLogoState.Done,
          UpdateLogoState.Error
        )
      ) 1f else 0f
    )
  }

  val cometAlpha by animateFloatAsState(
    targetValue = if (state == UpdateLogoState.Checking) 1f else 0f,
    animationSpec = if (isStill || isInspection) snap() else tween(durationMillis = 300, easing = FastOutSlowInEasing),
    label = "update_logo_comet_alpha"
  )

  val installingMoonsAlpha by animateFloatAsState(
    targetValue = if (state == UpdateLogoState.Installing) 1f else 0f,
    animationSpec = if (isStill || isInspection) snap() else tween(durationMillis = 260, easing = FastOutSlowInEasing),
    label = "update_logo_install_moons_alpha"
  )

  val ringColor by animateColorAsState(
    targetValue = if (state == UpdateLogoState.Installing) Color(0xFF10B981) else Color(0xFF34353F),
    animationSpec = if (isStill || isInspection) snap() else tween(durationMillis = 320),
    label = "update_logo_ring_color"
  )

  val smoothedDownloadProgress by animateFloatAsState(
    targetValue = if (state == UpdateLogoState.Downloading) downloadProgress.coerceIn(0f, 1f) else 0f,
    animationSpec = if (isStill || isInspection) snap() else tween(durationMillis = 220, easing = FastOutSlowInEasing),
    label = "update_logo_dl_progress"
  )

  val hasBottomRightBadge = state == UpdateLogoState.Available ||
    state == UpdateLogoState.Downloading ||
    state == UpdateLogoState.Done ||
    state == UpdateLogoState.Error

  LaunchedEffect(state, isStill) {
    if (isStill) {
      tileAlpha.snapTo(1f)
      tileScale.snapTo(1f)
      topRingTrim.snapTo(1f)
      bottomRingTrim.snapTo(1f)
      planetPopScale.snapTo(1f)
      eyePopScale.snapTo(1f)
      planetBounceScale.snapTo(1f)
      planetHopY.snapTo(0f)
      tileShakeX.snapTo(0f)
      confettiProgress.snapTo(0f)
      badgeScale.snapTo(if (hasBottomRightBadge) 1f else 0f)
      return@LaunchedEffect
    }

    when (state) {
      UpdateLogoState.Intro -> {
        tileAlpha.snapTo(0f)
        tileScale.snapTo(0.9f)
        topRingTrim.snapTo(0f)
        bottomRingTrim.snapTo(0f)
        planetPopScale.snapTo(0.4f)
        eyePopScale.snapTo(0f)
        planetBounceScale.snapTo(1f)
        planetHopY.snapTo(0f)
        tileShakeX.snapTo(0f)
        confettiProgress.snapTo(0f)
        badgeScale.snapTo(0f)

        coroutineScope {
          launch { tileAlpha.animateTo(1f, tween(durationMillis = 450, easing = FastOutSlowInEasing)) }
          launch { tileScale.animateTo(1f, tween(durationMillis = 550, easing = FastOutSlowInEasing)) }
          launch { topRingTrim.animateTo(1f, tween(durationMillis = 900, easing = FastOutSlowInEasing)) }
          launch {
            delay(300L)
            bottomRingTrim.animateTo(1f, tween(durationMillis = 900, easing = FastOutSlowInEasing))
          }
          launch {
            delay(150L)
            planetPopScale.animateTo(
              targetValue = 1f,
              animationSpec = spring(
                dampingRatio = 0.55f,
                stiffness = Spring.StiffnessMediumLow
              )
            )
          }
          launch {
            delay(600L)
            eyePopScale.animateTo(
              targetValue = 1f,
              animationSpec = spring(
                dampingRatio = 0.58f,
                stiffness = Spring.StiffnessMedium
              )
            )
          }
        }
      }

      UpdateLogoState.Idle, UpdateLogoState.Checking, UpdateLogoState.Installing -> {
        tileAlpha.snapTo(1f)
        tileScale.snapTo(1f)
        topRingTrim.snapTo(1f)
        bottomRingTrim.snapTo(1f)
        planetPopScale.snapTo(1f)
        eyePopScale.snapTo(1f)
        planetBounceScale.snapTo(1f)
        planetHopY.snapTo(0f)
        tileShakeX.snapTo(0f)
        confettiProgress.snapTo(0f)
        if (badgeScale.value > 0f) {
          badgeScale.animateTo(0f, tween(durationMillis = 180))
        }
      }

      UpdateLogoState.Available, UpdateLogoState.Downloading -> {
        tileAlpha.snapTo(1f)
        tileScale.snapTo(1f)
        topRingTrim.snapTo(1f)
        bottomRingTrim.snapTo(1f)
        planetPopScale.snapTo(1f)
        eyePopScale.snapTo(1f)
        planetBounceScale.snapTo(1f)
        planetHopY.snapTo(0f)
        tileShakeX.snapTo(0f)
        confettiProgress.snapTo(0f)
        badgeScale.snapTo(0f)
        badgeScale.animateTo(
          targetValue = 1f,
          animationSpec = spring(dampingRatio = 0.54f, stiffness = Spring.StiffnessMedium)
        )
      }

      UpdateLogoState.Done -> {
        tileAlpha.snapTo(1f)
        tileScale.snapTo(1f)
        topRingTrim.snapTo(1f)
        bottomRingTrim.snapTo(1f)
        planetPopScale.snapTo(1f)
        eyePopScale.snapTo(1f)
        tileShakeX.snapTo(0f)

        coroutineScope {
          launch {
            planetHopY.snapTo(0f)
            planetHopY.animateTo(
              targetValue = 0f,
              animationSpec = keyframes {
                durationMillis = 720
                0f at 0 using FastOutSlowInEasing
                -15f at 220 using FastOutSlowInEasing
                3f at 460 using FastOutSlowInEasing
                -5f at 580 using FastOutSlowInEasing
                0f at 720 using FastOutSlowInEasing
              }
            )
          }
          launch {
            planetBounceScale.snapTo(1f)
            planetBounceScale.animateTo(
              targetValue = 1f,
              animationSpec = keyframes {
                durationMillis = 650
                1f at 0 using FastOutSlowInEasing
                1.09f at 240 using FastOutSlowInEasing
                1f at 650 using FastOutSlowInEasing
              }
            )
          }
          launch {
            badgeScale.snapTo(0f)
            badgeScale.animateTo(
              targetValue = 1f,
              animationSpec = spring(
                dampingRatio = 0.50f,
                stiffness = Spring.StiffnessMedium
              )
            )
          }
          launch {
            confettiProgress.snapTo(0f)
            confettiProgress.animateTo(
              targetValue = 1f,
              animationSpec = tween(durationMillis = 1250, easing = FastOutSlowInEasing)
            )
          }
        }
      }

      UpdateLogoState.Error -> {
        tileAlpha.snapTo(1f)
        tileScale.snapTo(1f)
        topRingTrim.snapTo(1f)
        bottomRingTrim.snapTo(1f)
        planetPopScale.snapTo(1f)
        eyePopScale.snapTo(1f)
        planetBounceScale.snapTo(1f)
        planetHopY.snapTo(0f)
        confettiProgress.snapTo(0f)

        coroutineScope {
          launch {
            tileShakeX.snapTo(0f)
            tileShakeX.animateTo(
              targetValue = 0f,
              animationSpec = keyframes {
                durationMillis = 520
                0f at 0
                -12f at 70
                12f at 140
                -9f at 210
                9f at 280
                -5f at 350
                4f at 420
                0f at 520
              }
            )
          }
          launch {
            badgeScale.snapTo(0f)
            badgeScale.animateTo(
              targetValue = 1f,
              animationSpec = spring(dampingRatio = 0.52f, stiffness = Spring.StiffnessMedium)
            )
          }
        }
      }
    }
  }

  val infiniteTransition = rememberInfiniteTransition(label = "update_logo_infinite")
  val animateLoops = !isStill && !isInspection && state != UpdateLogoState.Intro

  // Float up and down 4 units over 4.6 s in Idle / Checking / Available
  val floatY = if (!animateLoops || state == UpdateLogoState.Error || state == UpdateLogoState.Installing) {
    0f
  } else {
    val anim by infiniteTransition.animateFloat(
      initialValue = 0f,
      targetValue = 0f,
      animationSpec = infiniteRepeatable(
        animation = keyframes {
          durationMillis = 4600
          0f at 0 using FastOutSlowInEasing
          -4f at 1150 using FastOutSlowInEasing
          4f at 3450 using FastOutSlowInEasing
          0f at 4600 using FastOutSlowInEasing
        },
        repeatMode = RepeatMode.Restart
      ),
      label = "update_logo_float_y"
    )
    anim
  }

  // Idle / Checking glancing eyes: 0 to +7 to -6 to 0 over 5 s
  val eyeDriftX = if (!animateLoops || state == UpdateLogoState.Downloading || state == UpdateLogoState.Done || state == UpdateLogoState.Error) {
    0f
  } else {
    val anim by infiniteTransition.animateFloat(
      initialValue = 0f,
      targetValue = 0f,
      animationSpec = infiniteRepeatable(
        animation = keyframes {
          durationMillis = 5000
          0f at 0 using FastOutSlowInEasing
          7f at 1650 using FastOutSlowInEasing
          -6f at 3350 using FastOutSlowInEasing
          0f at 5000 using FastOutSlowInEasing
        },
        repeatMode = RepeatMode.Restart
      ),
      label = "update_logo_eye_drift"
    )
    anim
  }

  // Eyes blink: scaleY down to 0.08 for a moment every 4 s
  val eyeBlinkScaleY = if (!animateLoops || state == UpdateLogoState.Done || state == UpdateLogoState.Error) {
    1f
  } else {
    val anim by infiniteTransition.animateFloat(
      initialValue = 1f,
      targetValue = 1f,
      animationSpec = infiniteRepeatable(
        animation = keyframes {
          durationMillis = 4000
          1f at 0
          1f at 3720
          0.08f at 3830
          1f at 3960
          1f at 4000
        },
        repeatMode = RepeatMode.Restart
      ),
      label = "update_logo_eye_blink"
    )
    anim
  }

  // Checking blue moon: 1 lap per 1.3 s, linear
  val cometBaseAngle = if (isStill || isInspection) {
    0.8f
  } else {
    val anim by infiniteTransition.animateFloat(
      initialValue = 0f,
      targetValue = (2.0 * PI).toFloat(),
      animationSpec = infiniteRepeatable(
        animation = tween(durationMillis = 1300, easing = LinearEasing),
        repeatMode = RepeatMode.Restart
      ),
      label = "update_logo_comet_angle"
    )
    anim
  }

  // Installing twin green moons: fast orbit (700ms per lap)
  val installOrbitAngle = if (isStill || isInspection) {
    0.5f
  } else {
    val anim by infiniteTransition.animateFloat(
      initialValue = 0f,
      targetValue = (2.0 * PI).toFloat(),
      animationSpec = infiniteRepeatable(
        animation = tween(durationMillis = 700, easing = LinearEasing),
        repeatMode = RepeatMode.Restart
      ),
      label = "update_logo_install_angle"
    )
    anim
  }

  // Installing planet wobble + loading bar sweep
  val installWobbleX = if (!animateLoops || state != UpdateLogoState.Installing) {
    0f
  } else {
    val anim by infiniteTransition.animateFloat(
      initialValue = -3.2f,
      targetValue = 3.2f,
      animationSpec = infiniteRepeatable(
        animation = tween(durationMillis = 210, easing = FastOutSlowInEasing),
        repeatMode = RepeatMode.Reverse
      ),
      label = "update_logo_install_wobble"
    )
    anim
  }

  val installBarPhase = if (!animateLoops || state != UpdateLogoState.Installing) {
    0.3f
  } else {
    val anim by infiniteTransition.animateFloat(
      initialValue = 0f,
      targetValue = 1f,
      animationSpec = infiniteRepeatable(
        animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
        repeatMode = RepeatMode.Restart
      ),
      label = "update_logo_install_bar"
    )
    anim
  }

  // Available amber glow pulse
  val amberPulse = if (!animateLoops || state != UpdateLogoState.Available) {
    0.55f
  } else {
    val anim by infiniteTransition.animateFloat(
      initialValue = 0.25f,
      targetValue = 1f,
      animationSpec = infiniteRepeatable(
        animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
        repeatMode = RepeatMode.Reverse
      ),
      label = "update_logo_amber_pulse"
    )
    anim
  }

  val activeCometAlpha = if (isStill || isInspection) {
    if (state == UpdateLogoState.Checking) 1f else 0f
  } else {
    cometAlpha
  }

  val activeInstallMoonsAlpha = if (isStill || isInspection) {
    if (state == UpdateLogoState.Installing) 1f else 0f
  } else {
    installingMoonsAlpha
  }

  val roundedProgressPath = remember {
    Path().apply {
      val left = 44f
      val top = 44f
      val right = 244f
      val bottom = 244f
      val radius = 50f
      val diameter = radius * 2f
      moveTo(144f, top)
      lineTo(right - radius, top)
      arcTo(Rect(right - diameter, top, right, top + diameter), -90f, 90f, false)
      lineTo(right, bottom - radius)
      arcTo(Rect(right - diameter, bottom - diameter, right, bottom), 0f, 90f, false)
      lineTo(left + radius, bottom)
      arcTo(Rect(left, bottom - diameter, left + diameter, bottom), 90f, 90f, false)
      lineTo(left, top + radius)
      arcTo(Rect(left, top, left + diameter, top + diameter), 180f, 90f, false)
      close()
    }
  }
  val progressMeasure = remember(roundedProgressPath) {
    PathMeasure().apply { setPath(roundedProgressPath, true) }
  }
  val progressSegmentPath = remember { Path() }

  val confettiSpecs = remember {
    listOf(
      ConfettiSpec(-2.7f, 88f, 6.5f, Color(0xFF22C55E), 0f),
      ConfettiSpec(-2.1f, 96f, 5.5f, Color(0xFF3B82F6), 0.2f),
      ConfettiSpec(-1.55f, 92f, 6f, Color(0xFFF59E0B), 0.4f),
      ConfettiSpec(-1.05f, 98f, 6.5f, Color(0xFFEC4899), 0.6f),
      ConfettiSpec(-0.55f, 90f, 5.5f, Color(0xFF8B5CF6), 0.8f),
      ConfettiSpec(0.15f, 86f, 5.5f, Color(0xFF14B8A6), 1.0f),
      ConfettiSpec(0.85f, 92f, 6.5f, Color(0xFFF59E0B), 1.2f),
      ConfettiSpec(1.65f, 90f, 5.5f, Color(0xFF3B82F6), 1.4f),
      ConfettiSpec(2.35f, 94f, 6f, Color(0xFFEC4899), 1.6f),
      ConfettiSpec(2.85f, 84f, 5f, Color(0xFFFFFFFF), 1.8f)
    )
  }

  val eyeLookDownOffsetY = when (state) {
    UpdateLogoState.Downloading -> 7.5f
    UpdateLogoState.Installing -> 2.5f
    else -> 0f
  }

  Box(modifier = modifier.size(size)) {
    Canvas(modifier = Modifier.matchParentSize()) {
      if (this.size.minDimension <= 0f) return@Canvas
      val scaleFactor = this.size.minDimension / 208f
      withTransform({
        scale(scaleFactor, scaleFactor, pivot = Offset.Zero)
        translate(left = -40f + tileShakeX.value, top = -40f + floatY)
      }) {
        // Tile: rounded square 40..248, corner radius 54, vertical gradient #E1E3EE to #6C6E82
        withTransform({
          scale(tileScale.value, tileScale.value, pivot = Offset(144f, 144f))
        }) {
          if (state == UpdateLogoState.Available) {
            drawCircle(
              brush = Brush.radialGradient(
                colors = listOf(
                  Color(0xFFF59E0B).copy(alpha = 0.42f * amberPulse),
                  Color.Transparent
                ),
                center = Offset(144f, 144f),
                radius = 104f
              ),
              radius = 104f,
              center = Offset(144f, 144f)
            )
          }

          drawRoundRect(
            brush = Brush.verticalGradient(
              colors = listOf(Color(0xFFE1E3EE), Color(0xFF6C6E82)),
              startY = 40f,
              endY = 248f
            ),
            topLeft = Offset(40f, 40f),
            size = Size(208f, 208f),
            cornerRadius = CornerRadius(54f, 54f),
            alpha = tileAlpha.value
          )
        }

        // Downloading: perimeter progress ring around the tile following real download %
        if (state == UpdateLogoState.Downloading) {
          drawRoundRect(
            color = Color(0xFF1F7AFF).copy(alpha = 0.22f),
            topLeft = Offset(44f, 44f),
            size = Size(200f, 200f),
            cornerRadius = CornerRadius(50f, 50f),
            style = Stroke(width = 7f)
          )
          val effectiveFrac = if (downloadProgress < 0f) 0.28f else smoothedDownloadProgress
          if (effectiveFrac > 0.005f) {
            progressSegmentPath.reset()
            progressMeasure.getSegment(
              startDistance = 0f,
              stopDistance = progressMeasure.length * effectiveFrac.coerceIn(0f, 1f),
              destination = progressSegmentPath,
              startWithMoveTo = true
            )
            drawPath(
              path = progressSegmentPath,
              color = Color(0xFF1F7AFF),
              style = Stroke(width = 8f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
          }
        }

        // Available amber highlight outline inside tile
        if (state == UpdateLogoState.Available) {
          drawRoundRect(
            color = Color(0xFFF59E0B).copy(alpha = 0.45f + 0.45f * amberPulse),
            topLeft = Offset(44f, 44f),
            size = Size(200f, 200f),
            cornerRadius = CornerRadius(50f, 50f),
            style = Stroke(width = 5f)
          )
        }

        // Back half of ring (180..360) + back orbiting moons inside -12 deg tilted frame
        rotate(degrees = -12f, pivot = Offset(144f, 144f)) {
          if (topRingTrim.value > 0.001f) {
            drawArc(
              color = ringColor,
              startAngle = 180f,
              sweepAngle = 180f * topRingTrim.value,
              useCenter = false,
              topLeft = Offset(144f - 84f, 144f - 26f),
              size = Size(168f, 52f),
              style = Stroke(width = 9f, cap = StrokeCap.Round),
              alpha = tileAlpha.value
            )
          }
          if (activeCometAlpha > 0.001f) {
            drawUpdateCometDots(
              baseAngle = cometBaseAngle,
              drawBackHalf = true,
              cometAlpha = activeCometAlpha,
              color = Color(0xFF1F7AFF)
            )
          }
          if (activeInstallMoonsAlpha > 0.001f) {
            drawUpdateCometDots(
              baseAngle = installOrbitAngle,
              drawBackHalf = true,
              cometAlpha = activeInstallMoonsAlpha,
              color = Color(0xFF10B981),
              dotCount = 6
            )
            drawUpdateCometDots(
              baseAngle = installOrbitAngle + PI.toFloat(),
              drawBackHalf = true,
              cometAlpha = activeInstallMoonsAlpha,
              color = Color(0xFF34D399),
              dotCount = 6
            )
          }
        }

        // Planet body (with hop & wobble)
        val planetCenterX = 144f + installWobbleX
        val planetCenterY = 144f + planetHopY.value
        val combinedPlanetScale = planetPopScale.value * planetBounceScale.value
        if (combinedPlanetScale > 0.001f) {
          val r = 48f * combinedPlanetScale
          drawCircle(
            brush = Brush.verticalGradient(
              colors = listOf(Color(0xFFFFFFFF), Color(0xFFD9DEEA)),
              startY = planetCenterY - r,
              endY = planetCenterY + r
            ),
            radius = r,
            center = Offset(planetCenterX, planetCenterY),
            alpha = tileAlpha.value
          )
        }

        // Eyes: happy curved eyes on Done, downward glance on Downloading, blinking/glancing ovals otherwise
        val ePop = eyePopScale.value
        if (ePop > 0.001f) {
          val leftEyeCenter = Offset(134f + eyeDriftX + installWobbleX, 133f + eyeLookDownOffsetY + planetHopY.value)
          val rightEyeCenter = Offset(157f + eyeDriftX + installWobbleX, 130f + eyeLookDownOffsetY + planetHopY.value)

          if (state == UpdateLogoState.Done) {
            val arcW = 16f * ePop
            val arcH = 12f * ePop
            val leftHappy = Path().apply {
              moveTo(leftEyeCenter.x - arcW / 2f, leftEyeCenter.y + 2f)
              quadraticTo(leftEyeCenter.x, leftEyeCenter.y - arcH, leftEyeCenter.x + arcW / 2f, leftEyeCenter.y + 2f)
            }
            val rightHappy = Path().apply {
              moveTo(rightEyeCenter.x - arcW / 2f, rightEyeCenter.y + 2f)
              quadraticTo(rightEyeCenter.x, rightEyeCenter.y - arcH, rightEyeCenter.x + arcW / 2f, rightEyeCenter.y + 2f)
            }
            drawPath(
              path = leftHappy,
              color = Color(0xFF34353F),
              style = Stroke(width = 5.5f, cap = StrokeCap.Round)
            )
            drawPath(
              path = rightHappy,
              color = Color(0xFF34353F),
              style = Stroke(width = 5.5f, cap = StrokeCap.Round)
            )
          } else {
            val eyeW = 16f * ePop
            val eyeH = 26f * ePop * eyeBlinkScaleY.coerceAtLeast(0.08f)
            drawOval(
              color = Color(0xFF34353F),
              topLeft = Offset(leftEyeCenter.x - eyeW / 2f, leftEyeCenter.y - eyeH / 2f),
              size = Size(eyeW, eyeH),
              alpha = tileAlpha.value
            )
            drawOval(
              color = Color(0xFF34353F),
              topLeft = Offset(rightEyeCenter.x - eyeW / 2f, rightEyeCenter.y - eyeH / 2f),
              size = Size(eyeW, eyeH),
              alpha = tileAlpha.value
            )
          }
        }

        // Front half of ring (0..180) + front orbiting moons inside -12 deg tilted frame
        rotate(degrees = -12f, pivot = Offset(144f, 144f)) {
          if (bottomRingTrim.value > 0.001f) {
            drawArc(
              color = ringColor,
              startAngle = 0f,
              sweepAngle = 180f * bottomRingTrim.value,
              useCenter = false,
              topLeft = Offset(144f - 84f, 144f - 26f),
              size = Size(168f, 52f),
              style = Stroke(width = 9f, cap = StrokeCap.Round),
              alpha = tileAlpha.value
            )
          }
          if (activeCometAlpha > 0.001f) {
            drawUpdateCometDots(
              baseAngle = cometBaseAngle,
              drawBackHalf = false,
              cometAlpha = activeCometAlpha,
              color = Color(0xFF1F7AFF)
            )
          }
          if (activeInstallMoonsAlpha > 0.001f) {
            drawUpdateCometDots(
              baseAngle = installOrbitAngle,
              drawBackHalf = false,
              cometAlpha = activeInstallMoonsAlpha,
              color = Color(0xFF10B981),
              dotCount = 6
            )
            drawUpdateCometDots(
              baseAngle = installOrbitAngle + PI.toFloat(),
              drawBackHalf = false,
              cometAlpha = activeInstallMoonsAlpha,
              color = Color(0xFF34D399),
              dotCount = 6
            )
          }
        }

        // Installing bottom loading bar inside tile
        if (state == UpdateLogoState.Installing) {
          val trackLeft = 78f
          val trackWidth = 132f
          val trackTop = 223f
          drawRoundRect(
            color = Color(0xFF1F2433).copy(alpha = 0.55f),
            topLeft = Offset(trackLeft, trackTop),
            size = Size(trackWidth, 8f),
            cornerRadius = CornerRadius(4f, 4f)
          )
          val pillW = 46f
          val travel = trackWidth - pillW
          val barStart = trackLeft + travel * installBarPhase
          drawRoundRect(
            color = Color(0xFF22C55E),
            topLeft = Offset(barStart, trackTop),
            size = Size(pillW, 8f),
            cornerRadius = CornerRadius(4f, 4f)
          )
        }

        // Done confetti burst (suppressed in Reduced Motion)
        if (state == UpdateLogoState.Done && !isStill && confettiProgress.value > 0.01f && confettiProgress.value < 0.99f) {
          val t = confettiProgress.value
          val fade = (1f - ((t - 0.55f) / 0.45f).coerceIn(0f, 1f))
          confettiSpecs.forEach { spec ->
            val dist = spec.maxDistance * t
            val x = 144f + cos(spec.angleRad) * dist
            val y = 144f + sin(spec.angleRad) * dist + (t * t * 22f)
            drawCircle(
              color = spec.color.copy(alpha = fade),
              radius = spec.size * (1f - t * 0.35f),
              center = Offset(x, y)
            )
          }
        }

        // Status badge at bottom-right (212, 212)
        val badgeS = badgeScale.value
        if (badgeS > 0.01f && hasBottomRightBadge) {
          val badgeColor = when (state) {
            UpdateLogoState.Available -> Color(0xFFF59E0B)
            UpdateLogoState.Downloading -> Color(0xFF1F7AFF)
            UpdateLogoState.Done -> Color(0xFF22C55E)
            UpdateLogoState.Error -> Color(0xFFEF4444)
            else -> Color(0xFF22C55E)
          }
          withTransform({
            scale(badgeS, badgeS, pivot = Offset(212f, 212f))
          }) {
            drawCircle(
              color = badgeColor,
              radius = 22f,
              center = Offset(212f, 212f)
            )
            drawCircle(
              color = Color(0xFFE9ECF5),
              radius = 22f,
              center = Offset(212f, 212f),
              style = Stroke(width = 4f)
            )

            when (state) {
              UpdateLogoState.Available -> {
                val upArrow = Path().apply {
                  moveTo(212f, 202f)
                  lineTo(212f, 222f)
                  moveTo(204f, 210f)
                  lineTo(212f, 202f)
                  lineTo(220f, 210f)
                }
                drawPath(
                  path = upArrow,
                  color = Color.White,
                  style = Stroke(width = 4.2f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
              }
              UpdateLogoState.Downloading -> {
                val downArrow = Path().apply {
                  moveTo(212f, 202f)
                  lineTo(212f, 222f)
                  moveTo(204f, 214f)
                  lineTo(212f, 222f)
                  lineTo(220f, 214f)
                }
                drawPath(
                  path = downArrow,
                  color = Color.White,
                  style = Stroke(width = 4.2f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
              }
              UpdateLogoState.Done -> {
                val checkPath = Path().apply {
                  moveTo(203f, 212.5f)
                  lineTo(209.5f, 219f)
                  lineTo(221.5f, 205.5f)
                }
                drawPath(
                  path = checkPath,
                  color = Color.White,
                  style = Stroke(width = 4.5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
              }
              UpdateLogoState.Error -> {
                val crossPath = Path().apply {
                  moveTo(204.5f, 204.5f)
                  lineTo(219.5f, 219.5f)
                  moveTo(219.5f, 204.5f)
                  lineTo(204.5f, 219.5f)
                }
                drawPath(
                  path = crossPath,
                  color = Color.White,
                  style = Stroke(width = 4.2f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
              }
              else -> Unit
            }
          }
        }
      }
    }

    // Crisp % label overlay during Downloading
    AnimatedVisibility(
      visible = state == UpdateLogoState.Downloading,
      enter = if (isStill) fadeIn(snap()) else fadeIn(tween(180)) + scaleIn(initialScale = 0.85f),
      exit = if (isStill) fadeOut(snap()) else fadeOut(tween(150)) + scaleOut(targetScale = 0.85f),
      modifier = Modifier
        .align(Alignment.TopEnd)
        .padding(top = 2.dp, end = 2.dp)
    ) {
      val pctText = if (downloadProgress >= 0f) {
        "${(downloadProgress * 100f).roundToInt().coerceIn(0, 100)}%"
      } else {
        "…"
      }
      Surface(
        shape = RoundedCornerShape(999.dp),
        color = Color(0xFF1F7AFF),
        shadowElevation = 2.dp
      ) {
        Text(
          text = pctText,
          color = Color.White,
          fontSize = 10.sp,
          lineHeight = 12.sp,
          fontWeight = FontWeight.Bold,
          modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
      }
    }
  }
}

private fun DrawScope.drawUpdateCometDots(
  baseAngle: Float,
  drawBackHalf: Boolean,
  cometAlpha: Float,
  color: Color,
  dotCount: Int = 9
) {
  for (i in (dotCount - 1) downTo 0) {
    val angle = baseAngle - i * 0.2f
    val sinA = sin(angle.toDouble())
    val isBack = sinA < 0.0
    if (isBack != drawBackHalf) continue

    val x = 144f + cos(angle) * 84f
    val y = 144f + sin(angle) * 26f
    val radius = 9f * (1f - (i / dotCount.toFloat()) * 0.7f)
    val alpha = ((1f - (i / dotCount.toFloat()) * 0.85f) * cometAlpha).coerceIn(0f, 1f)

    drawCircle(
      color = color.copy(alpha = alpha),
      radius = radius,
      center = Offset(x, y)
    )
  }
}

@Preview(name = "UpdateLogo - Idle", showBackground = true, backgroundColor = 0xFF0B0F1A)
@Composable
private fun UpdateLogoIdlePreview() {
  AurixTheme(darkTheme = true) {
    Box(
      modifier = Modifier
        .background(Color(0xFF151F30))
        .padding(16.dp),
      contentAlignment = Alignment.Center
    ) {
      UpdateLogo(state = UpdateLogoState.Idle, size = 84.dp)
    }
  }
}

@Preview(name = "UpdateLogo - Available", showBackground = true, backgroundColor = 0xFF0B0F1A)
@Composable
private fun UpdateLogoAvailablePreview() {
  AurixTheme(darkTheme = true) {
    Box(
      modifier = Modifier
        .background(Color(0xFF151F30))
        .padding(16.dp),
      contentAlignment = Alignment.Center
    ) {
      UpdateLogo(state = UpdateLogoState.Available, size = 84.dp)
    }
  }
}

@Preview(name = "UpdateLogo - Downloading", showBackground = true, backgroundColor = 0xFF0B0F1A)
@Composable
private fun UpdateLogoDownloadingPreview() {
  AurixTheme(darkTheme = true) {
    Box(
      modifier = Modifier
        .background(Color(0xFF151F30))
        .padding(16.dp),
      contentAlignment = Alignment.Center
    ) {
      UpdateLogo(state = UpdateLogoState.Downloading, downloadProgress = 0.64f, size = 84.dp)
    }
  }
}

@Preview(name = "UpdateLogo - Installing", showBackground = true, backgroundColor = 0xFF0B0F1A)
@Composable
private fun UpdateLogoInstallingPreview() {
  AurixTheme(darkTheme = true) {
    Box(
      modifier = Modifier
        .background(Color(0xFF151F30))
        .padding(16.dp),
      contentAlignment = Alignment.Center
    ) {
      UpdateLogo(state = UpdateLogoState.Installing, size = 84.dp)
    }
  }
}

@Preview(name = "UpdateLogo - Done", showBackground = true, backgroundColor = 0xFF0B0F1A)
@Composable
private fun UpdateLogoDonePreview() {
  AurixTheme(darkTheme = true) {
    Box(
      modifier = Modifier
        .background(Color(0xFF151F30))
        .padding(16.dp),
      contentAlignment = Alignment.Center
    ) {
      UpdateLogo(state = UpdateLogoState.Done, size = 84.dp)
    }
  }
}

@Preview(name = "UpdateLogo - Error", showBackground = true, backgroundColor = 0xFF0B0F1A)
@Composable
private fun UpdateLogoErrorPreview() {
  AurixTheme(darkTheme = true) {
    Box(
      modifier = Modifier
        .background(Color(0xFF151F30))
        .padding(16.dp),
      contentAlignment = Alignment.Center
    ) {
      UpdateLogo(state = UpdateLogoState.Error, size = 84.dp)
    }
  }
}
