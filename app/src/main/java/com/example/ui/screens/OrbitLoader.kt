package com.example.ui.screens

import android.provider.Settings
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

val AurixTealAccent = Color(0xFF2EC4B0)

/**
 * Plain minimalist Orbit loader matching viewBox "40 40 208 208":
 * - White sphere (r=46)
 * - Tilted -12 deg ellipse ring (84x26, stroke 10) with back/front occlusion & 24-wide card-color gap
 * - Dark oval eyes glancing left/right (3.6s loop) & blinking (2.8s loop)
 * - 9 teal (#2EC4B0) comet dots orbiting behind upper half and in front of lower half (1.25s lap)
 * - Still frame when reducedMotion or system ANIMATOR_DURATION_SCALE == 0
 */
@Composable
fun OrbitLoader(
  size: Dp,
  cardColor: Color,
  ringColor: Color = Color(0xFF3A3C48),
  eyeColor: Color = Color(0xFF1A1D28),
  cometColor: Color = AurixTealAccent,
  reducedMotion: Boolean = false,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val systemAnimatorScale = remember(context) {
    runCatching {
      Settings.Global.getFloat(
        context.contentResolver,
        Settings.Global.ANIMATOR_DURATION_SCALE,
        1f
      )
    }.getOrDefault(1f)
  }
  val isStill = reducedMotion || systemAnimatorScale <= 0f

  val infiniteTransition = rememberInfiniteTransition(label = "orbit_loader_transition")

  // Comet: 1 lap every 1.25s (1250 ms)
  val cometPhase = if (isStill) {
    0.18f
  } else {
    val anim by infiniteTransition.animateFloat(
      initialValue = 0f,
      targetValue = 1f,
      animationSpec = infiniteRepeatable(
        animation = tween(durationMillis = 1250, easing = LinearEasing),
        repeatMode = RepeatMode.Restart
      ),
      label = "comet_phase"
    )
    anim
  }

  // Eyes look left and right: 3.6s loop (3600 ms)
  val glancePhase = if (isStill) {
    0f
  } else {
    val anim by infiniteTransition.animateFloat(
      initialValue = 0f,
      targetValue = 1f,
      animationSpec = infiniteRepeatable(
        animation = tween(durationMillis = 3600, easing = LinearEasing),
        repeatMode = RepeatMode.Restart
      ),
      label = "eye_glance_phase"
    )
    sin(anim * 2.0 * PI).toFloat()
  }

  // Eyes blink every 2.8s (2800 ms)
  val blinkPhase = if (isStill) {
    0.5f
  } else {
    val anim by infiniteTransition.animateFloat(
      initialValue = 0f,
      targetValue = 1f,
      animationSpec = infiniteRepeatable(
        animation = tween(durationMillis = 2800, easing = LinearEasing),
        repeatMode = RepeatMode.Restart
      ),
      label = "eye_blink_phase"
    )
    anim
  }

  val blinkScaleY = when {
    isStill -> 1f
    blinkPhase < 0.03f -> 1f - (blinkPhase / 0.03f) * 0.90f
    blinkPhase < 0.065f -> 0.10f + ((blinkPhase - 0.03f) / 0.035f) * 0.90f
    else -> 1f
  }

  Canvas(modifier = modifier.size(size)) {
    val scale = this.size.minDimension / 208f
    val cx = this.size.width / 2f
    val cy = this.size.height / 2f

    val planetRadius = 46f * scale
    val ringRx = 84f * scale
    val ringRy = 26f * scale
    val ringStroke = 10f * scale
    val gapStroke = 24f * scale

    val headAngle = cometPhase * (2f * PI.toFloat())

    rotate(degrees = -12f, pivot = Offset(cx, cy)) {
      // Back half of ring (top arc behind sphere)
      drawArc(
        color = ringColor,
        startAngle = 180f,
        sweepAngle = 180f,
        useCenter = false,
        topLeft = Offset(cx - ringRx, cy - ringRy),
        size = Size(ringRx * 2f, ringRy * 2f),
        style = Stroke(width = ringStroke, cap = StrokeCap.Round)
      )

      // Comet dots on upper half (behind planet: sin < 0)
      drawOrbitCometDots(
        cx = cx,
        cy = cy,
        rx = ringRx,
        ry = ringRy,
        headAngle = headAngle,
        scale = scale,
        drawBackHalf = true,
        cometColor = cometColor
      )
    }

    // Planet sphere (white circle centered at 144,144 -> 104,104 in 0..208 space)
    drawCircle(
      color = Color.White,
      radius = planetRadius,
      center = Offset(cx, cy)
    )

    // Plain dark eyes at (136,133) -> (96,93) and (158,130) -> (118,90), rx=7.5, ry=12
    val glanceDx = glancePhase * 5.2f * scale
    val eyeRx = 7.5f * scale
    val eyeRy = 12f * scale * blinkScaleY.coerceAtLeast(0.08f)

    val leftEyeCx = (96f * scale) + glanceDx
    val leftEyeCy = 93f * scale
    val rightEyeCx = (118f * scale) + glanceDx
    val rightEyeCy = 90f * scale

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

    rotate(degrees = -12f, pivot = Offset(cx, cy)) {
      // 24-wide card-color gap stroke underneath the front arc
      drawArc(
        color = cardColor,
        startAngle = 0f,
        sweepAngle = 180f,
        useCenter = false,
        topLeft = Offset(cx - ringRx, cy - ringRy),
        size = Size(ringRx * 2f, ringRy * 2f),
        style = Stroke(width = gapStroke, cap = StrokeCap.Round)
      )

      // Front half of ring (bottom arc in front of sphere)
      drawArc(
        color = ringColor,
        startAngle = 0f,
        sweepAngle = 180f,
        useCenter = false,
        topLeft = Offset(cx - ringRx, cy - ringRy),
        size = Size(ringRx * 2f, ringRy * 2f),
        style = Stroke(width = ringStroke, cap = StrokeCap.Round)
      )

      // Comet dots on lower half (in front of planet: sin >= 0)
      drawOrbitCometDots(
        cx = cx,
        cy = cy,
        rx = ringRx,
        ry = ringRy,
        headAngle = headAngle,
        scale = scale,
        drawBackHalf = false,
        cometColor = cometColor
      )
    }
  }
}

private fun DrawScope.drawOrbitCometDots(
  cx: Float,
  cy: Float,
  rx: Float,
  ry: Float,
  headAngle: Float,
  scale: Float,
  drawBackHalf: Boolean,
  cometColor: Color
) {
  val dotCount = 9
  for (i in (dotCount - 1) downTo 0) {
    val angle = headAngle - i * 0.145f
    val isBack = sin(angle.toDouble()) < 0.0
    if (isBack != drawBackHalf) continue

    val progress = 1f - (i.toFloat() / dotCount)
    val dotRadius = (2.1f + 4.4f * progress) * scale
    val dotAlpha = 0.12f + 0.88f * progress * progress
    val px = cx + rx * cos(angle)
    val py = cy + ry * sin(angle)

    drawCircle(
      color = cometColor.copy(alpha = dotAlpha.coerceIn(0f, 1f)),
      radius = dotRadius,
      center = Offset(px, py)
    )
  }
}
