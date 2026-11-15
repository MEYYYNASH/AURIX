package com.example.ui.screens

import android.provider.Settings
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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.AurixTheme
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

enum class UpdateLogoState {
  Intro,
  Idle,
  Checking,
  Done
}

/**
 * Animated AURIX update icon drawn in a 208x208 design space (SVG viewBox 40 40 208 208),
 * scaled to `size` and shifted by (-40, -40).
 */
@Composable
fun UpdateLogo(
  state: UpdateLogoState,
  modifier: Modifier = Modifier,
  size: Dp = 84.dp,
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
  val isDonePreview = isInspection && state == UpdateLogoState.Done

  val tileAlpha = remember { Animatable(if (state == UpdateLogoState.Intro && !isStill && !isInspection) 0f else 1f) }
  val tileScale = remember { Animatable(if (state == UpdateLogoState.Intro && !isStill && !isInspection) 0.9f else if (isIntroPreview) 0.97f else 1f) }
  val topRingTrim = remember { Animatable(if (state == UpdateLogoState.Intro && !isStill && !isInspection) 0f else if (isIntroPreview) 0.85f else 1f) }
  val bottomRingTrim = remember { Animatable(if (state == UpdateLogoState.Intro && !isStill && !isInspection) 0f else if (isIntroPreview) 0.55f else 1f) }
  val planetPopScale = remember { Animatable(if (state == UpdateLogoState.Intro && !isStill && !isInspection) 0.4f else if (isIntroPreview) 0.94f else 1f) }
  val eyePopScale = remember { Animatable(if (state == UpdateLogoState.Intro && !isStill && !isInspection) 0f else if (isIntroPreview) 0.85f else 1f) }

  val planetBounceScale = remember { Animatable(1f) }
  val doneBadgeScale = remember { Animatable(if (isDonePreview || (isStill && state == UpdateLogoState.Done)) 1f else 0f) }

  val cometAlpha by animateFloatAsState(
    targetValue = if (state == UpdateLogoState.Checking) 1f else 0f,
    animationSpec = if (isStill || isInspection) snap() else tween(durationMillis = 350, easing = FastOutSlowInEasing),
    label = "update_logo_comet_alpha"
  )

  LaunchedEffect(state, isStill) {
    if (isStill) {
      tileAlpha.snapTo(1f)
      tileScale.snapTo(1f)
      topRingTrim.snapTo(1f)
      bottomRingTrim.snapTo(1f)
      planetPopScale.snapTo(1f)
      eyePopScale.snapTo(1f)
      planetBounceScale.snapTo(1f)
      doneBadgeScale.snapTo(if (state == UpdateLogoState.Done) 1f else 0f)
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
        doneBadgeScale.snapTo(0f)

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

      UpdateLogoState.Idle, UpdateLogoState.Checking -> {
        tileAlpha.snapTo(1f)
        tileScale.snapTo(1f)
        topRingTrim.snapTo(1f)
        bottomRingTrim.snapTo(1f)
        planetPopScale.snapTo(1f)
        eyePopScale.snapTo(1f)
        planetBounceScale.snapTo(1f)
        if (doneBadgeScale.value > 0f) {
          doneBadgeScale.animateTo(0f, tween(durationMillis = 180))
        }
      }

      UpdateLogoState.Done -> {
        tileAlpha.snapTo(1f)
        tileScale.snapTo(1f)
        topRingTrim.snapTo(1f)
        bottomRingTrim.snapTo(1f)
        planetPopScale.snapTo(1f)
        eyePopScale.snapTo(1f)

        coroutineScope {
          launch {
            planetBounceScale.snapTo(1f)
            planetBounceScale.animateTo(
              targetValue = 1f,
              animationSpec = keyframes {
                durationMillis = 600
                1f at 0 using FastOutSlowInEasing
                1.07f at 260 using FastOutSlowInEasing
                1f at 600 using FastOutSlowInEasing
              }
            )
          }
          launch {
            doneBadgeScale.snapTo(0f)
            doneBadgeScale.animateTo(
              targetValue = 1f,
              animationSpec = spring(
                dampingRatio = 0.50f,
                stiffness = Spring.StiffnessMedium
              )
            )
          }
        }
      }
    }
  }

  val infiniteTransition = rememberInfiniteTransition(label = "update_logo_infinite")
  val animateIdleLoops = !isStill && !isInspection && state != UpdateLogoState.Intro

  // Float up and down 4 units over 4.6 s, ease in and out
  val floatY = if (!animateIdleLoops) {
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

  // Eyes drift: 0 to +7 to -6 to 0 over 5 s
  val eyeDriftX = if (!animateIdleLoops) {
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
  val eyeBlinkScaleY = if (!animateIdleLoops) {
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

  // Blue comet: 1 lap per 1.3 s, linear
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

  val activeCometAlpha = if (isStill || isInspection) {
    if (state == UpdateLogoState.Checking) 1f else 0f
  } else {
    cometAlpha
  }

  Canvas(modifier = modifier.size(size)) {
    val scaleFactor = this.size.minDimension / 208f
    withTransform({
      scale(scaleFactor, scaleFactor, pivot = Offset.Zero)
      translate(left = -40f, top = -40f + floatY)
    }) {
      // Tile: rounded square 40..248, corner radius 54, vertical gradient #E1E3EE to #6C6E82
      withTransform({
        scale(tileScale.value, tileScale.value, pivot = Offset(144f, 144f))
      }) {
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

      // Back half of ring (180..360) + back comet dots inside -12 deg tilted frame
      rotate(degrees = -12f, pivot = Offset(144f, 144f)) {
        if (topRingTrim.value > 0.001f) {
          drawArc(
            color = Color(0xFF34353F),
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
            cometAlpha = activeCometAlpha
          )
        }
      }

      // Planet: circle at (144,144), radius 48, vertical gradient #FFFFFF to #D9DEEA
      val combinedPlanetScale = planetPopScale.value * planetBounceScale.value
      if (combinedPlanetScale > 0.001f) {
        val r = 48f * combinedPlanetScale
        drawCircle(
          brush = Brush.verticalGradient(
            colors = listOf(Color(0xFFFFFFFF), Color(0xFFD9DEEA)),
            startY = 144f - r,
            endY = 144f + r
          ),
          radius = r,
          center = Offset(144f, 144f),
          alpha = tileAlpha.value
        )
      }

      // Eyes: two ellipses at (134,133) and (157,130), 16x26, colour #34353F
      val ePop = eyePopScale.value
      if (ePop > 0.001f) {
        val eyeW = 16f * ePop
        val eyeH = 26f * ePop * eyeBlinkScaleY.coerceAtLeast(0.08f)
        drawOval(
          color = Color(0xFF34353F),
          topLeft = Offset(134f + eyeDriftX - eyeW / 2f, 133f - eyeH / 2f),
          size = Size(eyeW, eyeH),
          alpha = tileAlpha.value
        )
        drawOval(
          color = Color(0xFF34353F),
          topLeft = Offset(157f + eyeDriftX - eyeW / 2f, 130f - eyeH / 2f),
          size = Size(eyeW, eyeH),
          alpha = tileAlpha.value
        )
      }

      // Front half of ring (0..180) + front comet dots inside -12 deg tilted frame
      rotate(degrees = -12f, pivot = Offset(144f, 144f)) {
        if (bottomRingTrim.value > 0.001f) {
          drawArc(
            color = Color(0xFF34353F),
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
            cometAlpha = activeCometAlpha
          )
        }
      }

      // Done green badge at (212,212): #22C55E circle, radius 22, 4-wide #E9ECF5 outline, white check mark
      val badgeS = doneBadgeScale.value
      if (badgeS > 0.01f) {
        withTransform({
          scale(badgeS, badgeS, pivot = Offset(212f, 212f))
        }) {
          drawCircle(
            color = Color(0xFF22C55E),
            radius = 22f,
            center = Offset(212f, 212f)
          )
          drawCircle(
            color = Color(0xFFE9ECF5),
            radius = 22f,
            center = Offset(212f, 212f),
            style = Stroke(width = 4f)
          )
          val checkPath = Path().apply {
            moveTo(203f, 212.5f)
            lineTo(209.5f, 219f)
            lineTo(221.5f, 205.5f)
          }
          drawPath(
            path = checkPath,
            color = Color.White,
            style = Stroke(
              width = 4.5f,
              cap = StrokeCap.Round,
              join = StrokeJoin.Round
            )
          )
        }
      }
    }
  }
}

private fun DrawScope.drawUpdateCometDots(
  baseAngle: Float,
  drawBackHalf: Boolean,
  cometAlpha: Float
) {
  val cometBlue = Color(0xFF1F7AFF)
  val dotCount = 9
  for (i in (dotCount - 1) downTo 0) {
    val angle = baseAngle - i * 0.2f
    val sinA = sin(angle.toDouble())
    val isBack = sinA < 0.0
    if (isBack != drawBackHalf) continue

    val x = 144f + cos(angle) * 84f
    val y = 144f + sin(angle) * 26f
    val radius = 9f * (1f - (i / 9f) * 0.7f)
    val alpha = ((1f - (i / 9f) * 0.85f) * cometAlpha).coerceIn(0f, 1f)

    drawCircle(
      color = cometBlue.copy(alpha = alpha),
      radius = radius,
      center = Offset(x, y)
    )
  }
}

@Preview(name = "UpdateLogo - Intro", showBackground = true, backgroundColor = 0xFF0B0F1A)
@Composable
private fun UpdateLogoIntroPreview() {
  AurixTheme(darkTheme = true) {
    Box(
      modifier = Modifier
        .background(Color(0xFF151F30))
        .padding(16.dp),
      contentAlignment = Alignment.Center
    ) {
      UpdateLogo(state = UpdateLogoState.Intro, size = 84.dp)
    }
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

@Preview(name = "UpdateLogo - Checking", showBackground = true, backgroundColor = 0xFF0B0F1A)
@Composable
private fun UpdateLogoCheckingPreview() {
  AurixTheme(darkTheme = true) {
    Box(
      modifier = Modifier
        .background(Color(0xFF151F30))
        .padding(16.dp),
      contentAlignment = Alignment.Center
    ) {
      UpdateLogo(state = UpdateLogoState.Checking, size = 84.dp)
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
