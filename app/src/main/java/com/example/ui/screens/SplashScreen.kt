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
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.BuildConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private val SplashLightBg = Color(0xFFECEDF2)
private val SplashDarkBg = Color(0xFF17181D)
private val OrbitRingLight = Color(0xFF3A3B42)
private val OrbitRingDark = Color(0xFF565864)
private val PlanetEyeDark = Color(0xFF0B0C10)

@Composable
fun AurixLaunchOverlay(
  isAppInitialized: Boolean,
  darkTheme: Boolean,
  reducedMotion: Boolean,
  onDismissed: () -> Unit
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
  val skipMotion = reducedMotion || systemAnimatorScale <= 0f

  val bgColor = if (darkTheme) SplashDarkBg else SplashLightBg
  val textColor = if (darkTheme) Color(0xFFE7E9F1) else Color(0xFF282A33)
  val dotColor = if (darkTheme) Color(0xFF9EA3B8) else Color(0xFF646778)
  val ringColor = if (darkTheme) OrbitRingDark else OrbitRingLight

  var exiting by remember { mutableStateOf(false) }

  // Entrance & choreography animatables
  val popScale = remember { Animatable(if (skipMotion) 1f else 0.44f) }
  val popRotation = remember { Animatable(if (skipMotion) 0f else -16f) }
  val ringDrawProgress = remember { Animatable(if (skipMotion) 1f else 0f) }
  val eyeBlinkScale = remember { Animatable(1f) }
  val eyeGlanceX = remember { Animatable(0f) }

  val letters = remember { listOf("A", "U", "R", "I", "X") }
  val letterAlphas = remember { List(letters.size) { Animatable(if (skipMotion) 1f else 0f) } }
  val letterOffsets = remember { List(letters.size) { Animatable(if (skipMotion) 0f else 18f) } }

  // Infinite gentle loops (disabled when reduced motion is active)
  val infiniteTransition = rememberInfiniteTransition(label = "aurix_splash_loops")
  val orbitPhase by if (skipMotion) {
    remember { mutableStateOf(0.18f) }
  } else {
    infiniteTransition.animateFloat(
      initialValue = 0f,
      targetValue = 1f,
      animationSpec = infiniteRepeatable(
        animation = tween(durationMillis = 1650, easing = LinearEasing),
        repeatMode = RepeatMode.Restart
      ),
      label = "orbit_dot_phase"
    )
  }

  val floatCycle by if (skipMotion) {
    remember { mutableStateOf(0f) }
  } else {
    infiniteTransition.animateFloat(
      initialValue = -1f,
      targetValue = 1f,
      animationSpec = infiniteRepeatable(
        animation = tween(durationMillis = 1500, easing = FastOutSlowInEasing),
        repeatMode = RepeatMode.Reverse
      ),
      label = "planet_float"
    )
  }

  val glowPulse by if (skipMotion) {
    remember { mutableStateOf(0.5f) }
  } else {
    infiniteTransition.animateFloat(
      initialValue = 0f,
      targetValue = 1f,
      animationSpec = infiniteRepeatable(
        animation = tween(durationMillis = 1400, easing = FastOutSlowInEasing),
        repeatMode = RepeatMode.Reverse
      ),
      label = "glow_pulse"
    )
  }

  val sparkleClock by if (skipMotion) {
    remember { mutableStateOf(0.4f) }
  } else {
    infiniteTransition.animateFloat(
      initialValue = 0f,
      targetValue = (2f * PI).toFloat(),
      animationSpec = infiniteRepeatable(
        animation = tween(durationMillis = 2000, easing = LinearEasing),
        repeatMode = RepeatMode.Restart
      ),
      label = "sparkle_clock"
    )
  }

  val waveClock by if (skipMotion) {
    remember { mutableStateOf(0f) }
  } else {
    infiniteTransition.animateFloat(
      initialValue = 0f,
      targetValue = (2f * PI).toFloat(),
      animationSpec = infiniteRepeatable(
        animation = tween(durationMillis = 900, easing = LinearEasing),
        repeatMode = RepeatMode.Restart
      ),
      label = "loader_wave"
    )
  }

  val latestInitState = remember { mutableStateOf(isAppInitialized) }
  latestInitState.value = isAppInitialized

  LaunchedEffect(skipMotion) {
    if (!skipMotion) {
      launch {
        popScale.animateTo(
          targetValue = 1f,
          animationSpec = spring(dampingRatio = 0.56f, stiffness = 270f)
        )
      }
      launch {
        popRotation.animateTo(
          targetValue = 0f,
          animationSpec = spring(dampingRatio = 0.68f, stiffness = 210f)
        )
      }
      launch {
        ringDrawProgress.animateTo(
          targetValue = 1f,
          animationSpec = tween(durationMillis = 720, delayMillis = 110, easing = FastOutSlowInEasing)
        )
      }
      letters.indices.forEach { idx ->
        launch {
          letterAlphas[idx].animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 320, delayMillis = 200 + idx * 65, easing = FastOutSlowInEasing)
          )
        }
        launch {
          letterOffsets[idx].animateTo(
            targetValue = 0f,
            animationSpec = tween(durationMillis = 360, delayMillis = 200 + idx * 65, easing = FastOutSlowInEasing)
          )
        }
      }
      launch {
        delay(420)
        // Quick first blink
        eyeBlinkScale.animateTo(0.08f, tween(70, easing = LinearEasing))
        eyeBlinkScale.animateTo(1f, tween(90, easing = FastOutSlowInEasing))
        delay(180)
        // Glance right, then left, then back center
        eyeGlanceX.animateTo(1f, tween(170, easing = FastOutSlowInEasing))
        delay(240)
        eyeGlanceX.animateTo(-1f, tween(220, easing = FastOutSlowInEasing))
        delay(240)
        eyeGlanceX.animateTo(0f, tween(160, easing = FastOutSlowInEasing))
        // Occasional blink loop if initialization takes longer
        while (true) {
          delay(1900)
          eyeBlinkScale.animateTo(0.08f, tween(70, easing = LinearEasing))
          eyeBlinkScale.animateTo(1f, tween(95, easing = FastOutSlowInEasing))
        }
      }
    }

    // Hold briefly for the entrance choreography (capped under ~2.5s), then exit as soon as init completes.
    val minShowcaseMs = if (skipMotion) 220L else 1850L
    delay(minShowcaseMs)
    if (!latestInitState.value) {
      snapshotFlow { latestInitState.value }.first { it }
    }
    exiting = true
    delay(if (skipMotion) 140L else 280L)
    onDismissed()
  }

  val exitAlpha by animateFloatAsState(
    targetValue = if (exiting) 0f else 1f,
    animationSpec = if (skipMotion) snap() else tween(durationMillis = 270, easing = FastOutSlowInEasing),
    label = "splash_exit_alpha"
  )
  val exitScale by animateFloatAsState(
    targetValue = if (exiting) 0.90f else 1f,
    animationSpec = if (skipMotion) snap() else tween(durationMillis = 270, easing = FastOutSlowInEasing),
    label = "splash_exit_scale"
  )

  val noIndicationSource = remember { MutableInteractionSource() }

  Box(
    modifier = Modifier
      .fillMaxSize()
      .alpha(exitAlpha)
      .background(bgColor)
      .clickable(
        interactionSource = noIndicationSource,
        indication = null,
        onClick = {}
      ),
    contentAlignment = Alignment.Center
  ) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
      modifier = Modifier.graphicsLayer {
        scaleX = exitScale
        scaleY = exitScale
      }
    ) {
      Box(
        modifier = Modifier
          .size(184.dp)
          .graphicsLayer {
            val floatPx = if (skipMotion) 0f else floatCycle * 4.5.dp.toPx()
            translationY = floatPx
            scaleX = popScale.value
            scaleY = popScale.value
            rotationZ = popRotation.value
          },
        contentAlignment = Alignment.Center
      ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
          if (size.minDimension <= 0f) return@Canvas
          val cx = size.width / 2f
          val cy = size.height / 2f
          val planetRadius = size.minDimension * 0.255f
          val ringRx = size.minDimension * 0.375f
          val ringRy = size.minDimension * 0.102f
          val ringStroke = size.minDimension * 0.044f

          // 1. Soft pulsing glow behind planet
          val glowRadius = (planetRadius * (1.75f + 0.16f * glowPulse)).coerceAtLeast(1f)
          val glowAlpha = if (darkTheme) {
            0.14f + 0.08f * glowPulse
          } else {
            0.38f + 0.16f * glowPulse
          }
          val glowCoreColor = if (darkTheme) Color(0xFF9EA6C4) else Color.White
          drawCircle(
            brush = Brush.radialGradient(
              colors = listOf(
                glowCoreColor.copy(alpha = glowAlpha),
                Color.Transparent
              ),
              center = Offset(cx, cy),
              radius = glowRadius
            ),
            radius = glowRadius,
            center = Offset(cx, cy)
          )

          // 2. Twinkling sparkles around the planet
          if (!skipMotion) {
            val sparkleColor = if (darkTheme) Color(0xFFD3D6E4) else Color(0xFF7D8197)
            val sparkles = listOf(
              Triple(-0.37f, -0.28f, 0.0f),
              Triple(0.39f, -0.23f, 1.4f),
              Triple(-0.41f, 0.21f, 2.7f),
              Triple(0.35f, 0.29f, 4.1f),
              Triple(0.06f, -0.41f, 5.2f)
            )
            sparkles.forEachIndexed { idx, (dxRatio, dyRatio, phaseOffset) ->
              val wave = ((sin(sparkleClock + phaseOffset) + 1f) * 0.5f)
              val alpha = 0.18f + 0.72f * wave
              val radius = size.minDimension * (0.015f + 0.014f * wave) * if (idx % 2 == 0) 1f else 0.85f
              drawSparkle(
                center = Offset(cx + size.width * dxRatio, cy + size.height * dyRatio),
                radius = radius,
                color = sparkleColor.copy(alpha = alpha)
              )
            }
          }

          // Orbit dot & trail calculations
          val dotAngle = orbitPhase * (2f * PI.toFloat())
          val dotFadeIn = ((ringDrawProgress.value - 0.35f) / 0.35f).coerceIn(0f, 1f)
          val trailSteps = 14

          rotate(degrees = -16f, pivot = Offset(cx, cy)) {
            // PASS 1 (BEHIND PLANET): Back half of self-drawing orbit ring (sin < 0)
            drawEllipticalArcSegments(
              cx = cx,
              cy = cy,
              rx = ringRx,
              ry = ringRy,
              progress = ringDrawProgress.value,
              drawBackHalf = true,
              color = ringColor,
              strokeWidth = ringStroke
            )

            // Back half of orbiting trail + dot (sin < 0 -> occluded behind planet)
            if (!skipMotion && dotFadeIn > 0.01f) {
              drawOrbitDotAndTrail(
                cx = cx,
                cy = cy,
                rx = ringRx,
                ry = ringRy,
                headAngle = dotAngle,
                trailSteps = trailSteps,
                drawBackHalf = true,
                alphaScale = dotFadeIn,
                baseRadius = ringStroke * 0.62f,
                darkTheme = darkTheme
              )
            }
          }

          // PASS 2 (PLANET): White-lavender vertical gradient (white to #9F9EF4) + -12 deg tilted oval eyes with highlights
          drawCircle(
            color = Color.Black.copy(alpha = if (darkTheme) 0.36f else 0.12f),
            radius = planetRadius * 1.03f,
            center = Offset(cx, cy + planetRadius * 0.08f)
          )
          drawCircle(
            brush = Brush.verticalGradient(
              colors = listOf(
                Color.White,
                Color(0xFFE7E7FD),
                Color(0xFF9F9EF4)
              ),
              startY = cy - planetRadius,
              endY = cy + planetRadius
            ),
            radius = planetRadius,
            center = Offset(cx, cy)
          )
          drawCircle(
            color = Color.White.copy(alpha = 0.55f),
            radius = planetRadius,
            center = Offset(cx, cy),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx())
          )

          rotate(degrees = -12f, pivot = Offset(cx, cy)) {
            val eyeOffsetX = planetRadius * 0.26f
            val eyeCenterY = cy - planetRadius * 0.15f
            val glanceShiftPx = eyeGlanceX.value * (planetRadius * 0.14f)
            val eyeWidth = planetRadius * 0.25f
            val eyeHeight = (planetRadius * 0.46f) * eyeBlinkScale.value.coerceAtLeast(0.06f)
            val cornerR = eyeWidth * 0.5f

            val leftCx = cx - eyeOffsetX + glanceShiftPx
            val rightCx = cx + eyeOffsetX + glanceShiftPx

            drawRoundRect(
              color = PlanetEyeDark,
              topLeft = Offset(leftCx - eyeWidth / 2f, eyeCenterY - eyeHeight / 2f),
              size = Size(eyeWidth, eyeHeight),
              cornerRadius = CornerRadius(cornerR, cornerR)
            )
            drawRoundRect(
              color = PlanetEyeDark,
              topLeft = Offset(rightCx - eyeWidth / 2f, eyeCenterY - eyeHeight / 2f),
              size = Size(eyeWidth, eyeHeight),
              cornerRadius = CornerRadius(cornerR, cornerR)
            )

            if (eyeBlinkScale.value > 0.45f) {
              val hlRadius = eyeWidth * 0.19f
              drawCircle(
                color = Color.White.copy(alpha = 0.94f),
                radius = hlRadius,
                center = Offset(leftCx - eyeWidth * 0.14f, eyeCenterY - eyeHeight * 0.20f)
              )
              drawCircle(
                color = Color.White.copy(alpha = 0.94f),
                radius = hlRadius,
                center = Offset(rightCx - eyeWidth * 0.14f, eyeCenterY - eyeHeight * 0.20f)
              )
            }
          }

          rotate(degrees = -16f, pivot = Offset(cx, cy)) {
            // PASS 3 (IN FRONT OF PLANET): Front half of self-drawing orbit ring (sin >= 0)
            drawEllipticalArcSegments(
              cx = cx,
              cy = cy,
              rx = ringRx,
              ry = ringRy,
              progress = ringDrawProgress.value,
              drawBackHalf = false,
              color = ringColor,
              strokeWidth = ringStroke
            )

            // Front half of orbiting trail + dot (sin >= 0 -> passes cleanly over sphere)
            if (!skipMotion && dotFadeIn > 0.01f) {
              drawOrbitDotAndTrail(
                cx = cx,
                cy = cy,
                rx = ringRx,
                ry = ringRy,
                headAngle = dotAngle,
                trailSteps = trailSteps,
                drawBackHalf = false,
                alphaScale = dotFadeIn,
                baseRadius = ringStroke * 0.62f,
                darkTheme = darkTheme
              )
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(22.dp))

      // Letter-by-letter rising "AURIX"
      Row(
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        letters.forEachIndexed { idx, letter ->
          val alpha = letterAlphas[idx].value
          val offsetY = letterOffsets[idx].value
          Text(
            text = letter,
            style = MaterialTheme.typography.titleMedium.copy(
              fontSize = 15.sp,
              fontWeight = FontWeight.SemiBold,
              letterSpacing = 2.sp
            ),
            color = textColor,
            modifier = Modifier.graphicsLayer {
              this.alpha = alpha
              this.translationY = offsetY
            }
          )
        }
      }

      Spacer(modifier = Modifier.height(16.dp))

      // Three-dot wave loading indicator
      Row(
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        for (i in 0 until 3) {
          val phase = waveClock - i * 0.85f
          val bounce = if (skipMotion) 0f else sin(phase.toDouble()).toFloat().coerceAtLeast(0f)
          val dotAlpha = if (skipMotion) 0.55f else 0.35f + 0.60f * bounce
          Box(
            modifier = Modifier
              .offset { IntOffset(0, (-bounce * 5.dp.toPx()).roundToInt()) }
              .size(5.dp)
              .clip(CircleShape)
              .background(dotColor.copy(alpha = dotAlpha))
          )
        }
      }

      Spacer(modifier = Modifier.height(14.dp))

      Text(
        text = "v${BuildConfig.VERSION_NAME}",
        style = MaterialTheme.typography.labelSmall.copy(
          fontSize = 11.sp,
          fontWeight = FontWeight.Medium,
          letterSpacing = 1.sp
        ),
        color = dotColor.copy(alpha = 0.72f),
        modifier = Modifier.graphicsLayer {
          this.alpha = letterAlphas.lastOrNull()?.value ?: 1f
        }
      )
    }
  }
}

private fun DrawScope.drawEllipticalArcSegments(
  cx: Float,
  cy: Float,
  rx: Float,
  ry: Float,
  progress: Float,
  drawBackHalf: Boolean,
  color: Color,
  strokeWidth: Float
) {
  if (progress <= 0.001f) return
  val totalSteps = 84
  val maxSteps = (totalSteps * progress.coerceIn(0f, 1f)).roundToInt().coerceAtLeast(1)
  val startAngle = PI.toFloat() // Start gracefully at the left side (-rx, 0)

  for (i in 0 until maxSteps) {
    val t0 = i.toFloat() / totalSteps
    val t1 = (i + 1).toFloat() / totalSteps
    val a0 = startAngle + t0 * (2f * PI.toFloat())
    val a1 = startAngle + t1 * (2f * PI.toFloat())
    val aMid = (a0 + a1) * 0.5f
    val isBack = sin(aMid.toDouble()) < 0.0
    if (isBack != drawBackHalf) continue

    val p0 = Offset(cx + rx * cos(a0), cy + ry * sin(a0))
    val p1 = Offset(cx + rx * cos(a1), cy + ry * sin(a1))
    drawLine(
      color = color,
      start = p0,
      end = p1,
      strokeWidth = strokeWidth,
      cap = StrokeCap.Round
    )
  }
}

private fun DrawScope.drawOrbitDotAndTrail(
  cx: Float,
  cy: Float,
  rx: Float,
  ry: Float,
  headAngle: Float,
  trailSteps: Int,
  drawBackHalf: Boolean,
  alphaScale: Float,
  baseRadius: Float,
  darkTheme: Boolean
) {
  val dotColor = if (darkTheme) Color(0xFFEAECEF) else Color(0xFFFFFFFF)
  val haloColor = if (darkTheme) Color(0xFFB5BACD) else Color(0xFF4F5262)

  // Draw fading trail from tail to head
  for (step in trailSteps downTo 1) {
    val fraction = 1f - (step.toFloat() / (trailSteps + 1))
    val angle = headAngle - step * 0.045f
    val isBack = sin(angle.toDouble()) < 0.0
    if (isBack != drawBackHalf) continue

    val pos = Offset(cx + rx * cos(angle), cy + ry * sin(angle))
    val trailAlpha = alphaScale * fraction * fraction * 0.55f
    drawCircle(
      color = dotColor.copy(alpha = trailAlpha),
      radius = baseRadius * (0.35f + 0.50f * fraction),
      center = pos
    )
  }

  // Draw leading dot
  val headIsBack = sin(headAngle.toDouble()) < 0.0
  if (headIsBack == drawBackHalf) {
    val headPos = Offset(cx + rx * cos(headAngle), cy + ry * sin(headAngle))
    drawCircle(
      color = haloColor.copy(alpha = alphaScale * 0.35f),
      radius = baseRadius * 1.65f,
      center = headPos
    )
    drawCircle(
      color = dotColor.copy(alpha = alphaScale * 0.96f),
      radius = baseRadius,
      center = headPos
    )
  }
}

private fun DrawScope.drawSparkle(
  center: Offset,
  radius: Float,
  color: Color
) {
  val inner = radius * 0.24f
  val path = Path().apply {
    moveTo(center.x, center.y - radius)
    quadraticTo(center.x + inner * 0.35f, center.y - inner * 0.35f, center.x + radius, center.y)
    quadraticTo(center.x + inner * 0.35f, center.y + inner * 0.35f, center.x, center.y + radius)
    quadraticTo(center.x - inner * 0.35f, center.y + inner * 0.35f, center.x - radius, center.y)
    quadraticTo(center.x - inner * 0.35f, center.y - inner * 0.35f, center.x, center.y - radius)
    close()
  }
  drawPath(path = path, color = color)
}
