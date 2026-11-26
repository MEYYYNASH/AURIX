package com.example.ui.glass

import android.os.Build
import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selectableGroup
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AurixFontFamilies
import com.example.ui.theme.AurixTheme
import com.example.ui.theme.KhmerTypography
import com.example.ui.theme.ScriptRunType
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.LocalHazeStyle
import dev.chrisbanes.haze.haze
import dev.chrisbanes.haze.hazeChild
import kotlin.math.abs
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

val LocalAurixHazeState = compositionLocalOf<HazeState?> { null }
val LocalAurixReducedMotion = compositionLocalOf { false }

fun aurixGlassHazeStyle(
  darkTheme: Boolean,
  backgroundColor: Color
): HazeStyle {
  val safeBg = backgroundColor.takeOrElse {
    if (darkTheme) Color(0xFF0A0E1A) else Color(0xFFEAF0FF)
  }
  return HazeStyle(
    backgroundColor = safeBg,
    tint = HazeTint(Color.Transparent),
    blurRadius = 18.dp,
    noiseFactor = 0f
  )
}

@Immutable
data class SegOption(
  val label: String,
  val icon: ImageVector? = null,
  val testTag: String? = null
)

internal fun computeGlassVelocitySpeed(
  velocityPxPerSec: Float,
  referenceVelocityPxPerSec: Float
): Float {
  if (referenceVelocityPxPerSec <= 0f) return 0f
  return (abs(velocityPxPerSec) / referenceVelocityPxPerSec).coerceIn(0f, 1f)
}

internal fun projectFlickCenter(
  thumbLeftPx: Float,
  thumbWidthPx: Float,
  releaseVelocityPxPerSec: Float,
  trackTotalWidthPx: Float,
  projectionSeconds: Float = 0.20f
): Float {
  val currentCenter = thumbLeftPx + thumbWidthPx * 0.5f
  return (currentCenter + releaseVelocityPxPerSec * projectionSeconds)
    .coerceIn(0f, trackTotalWidthPx.coerceAtLeast(1f))
}

internal fun findNearestSegmentIndex(
  centerXPx: Float,
  tabLefts: FloatArray,
  tabWidths: FloatArray
): Int {
  if (tabLefts.isEmpty() || tabWidths.isEmpty()) return 0
  var bestIndex = 0
  var bestDist = Float.MAX_VALUE
  val count = minOf(tabLefts.size, tabWidths.size)
  for (i in 0 until count) {
    val w = tabWidths[i]
    if (w <= 0f) continue
    val tabCenter = tabLefts[i] + w * 0.5f
    val dist = abs(centerXPx - tabCenter)
    if (dist < bestDist) {
      bestDist = dist
      bestIndex = i
    }
  }
  return bestIndex
}

internal fun findSegmentIndexAtX(
  xPx: Float,
  tabLefts: FloatArray,
  tabWidths: FloatArray
): Int {
  val count = minOf(tabLefts.size, tabWidths.size)
  for (i in 0 until count) {
    val left = tabLefts[i]
    val width = tabWidths[i]
    if (width > 0f && xPx >= left && xPx <= left + width) {
      return i
    }
  }
  return findNearestSegmentIndex(xPx, tabLefts, tabWidths)
}

@Composable
private fun rememberGlassReducedMotion(): Boolean {
  val appReduced = LocalAurixReducedMotion.current
  val context = LocalContext.current
  val isInspection = LocalInspectionMode.current
  val systemAnimatorScale = remember(context, isInspection) {
    if (isInspection) {
      1f
    } else {
      runCatching {
        Settings.Global.getFloat(
          context.contentResolver,
          Settings.Global.ANIMATOR_DURATION_SCALE,
          1f
        )
      }.getOrDefault(1f)
    }
  }
  return appReduced || systemAnimatorScale <= 0f
}

@Composable
private fun rememberSegmentStyledLabel(label: String): AnnotatedString {
  return remember(label) {
    if (!KhmerTypography.containsKhmer(label)) {
      AnnotatedString(label)
    } else {
      val runs = KhmerTypography.segmentScriptRuns(label)
      buildAnnotatedString {
        runs.forEach { run ->
          val family = when (run.type) {
            ScriptRunType.KHMER, ScriptRunType.NEUTRAL -> AurixFontFamilies.NotoSansKhmerFamily
            ScriptRunType.LATIN -> AurixFontFamilies.AurixUiFamily
          }
          withStyle(SpanStyle(fontFamily = family)) {
            append(run.text)
          }
        }
      }
    }
  }
}

@Composable
fun GlassSegmented(
  options: List<SegOption>,
  selectedIndex: Int,
  onSelect: (Int) -> Unit,
  modifier: Modifier = Modifier,
  fillWidth: Boolean = true,
  darkTheme: Boolean,
  backdrop: Modifier = Modifier
) {
  if (options.isEmpty()) return

  val density = LocalDensity.current
  val configuration = LocalConfiguration.current
  val view = LocalView.current
  val haptic = LocalHapticFeedback.current
  val scope = rememberCoroutineScope()
  val reducedMotion = rememberGlassReducedMotion()

  val clampedSelectedIndex = selectedIndex.coerceIn(0, options.lastIndex)
  val currentSelectedIndex by rememberUpdatedState(clampedSelectedIndex)
  val currentOnSelect by rememberUpdatedState(onSelect)
  val currentReducedMotion by rememberUpdatedState(reducedMotion)

  val tabLefts = remember(options.size) { FloatArray(options.size) }
  val tabWidths = remember(options.size) { FloatArray(options.size) }
  var layoutEpoch by remember(options.size) { mutableIntStateOf(0) }

  // Force re-measurement synchronization when font scale, orientation, screen width, or labels change
  val configSignature = remember(
    options,
    configuration.fontScale,
    configuration.orientation,
    configuration.screenWidthDp
  ) {
    options.map { it.label } to Triple(
      configuration.fontScale,
      configuration.orientation,
      configuration.screenWidthDp
    )
  }

  val thumbX = remember { Animatable(0f) }
  val thumbWidth = remember { Animatable(0f) }
  var hasInitializedThumb by remember { mutableStateOf(false) }
  var lastAnimatedIndex by remember { mutableIntStateOf(-1) }
  var isDragging by remember { mutableStateOf(false) }
  var dragHoverIndex by remember { mutableIntStateOf(-1) }

  val velRefPx = with(density) { 1100.dp.toPx() }
  val tapPushPx = with(density) { 320.dp.toPx() }
  val baseShadowSpreadPx = with(density) { 5.dp.toPx() }
  val extraShadowSpreadPx = with(density) { 9.dp.toPx() }
  val baseShadowOffsetYPx = with(density) { 2.dp.toPx() }
  val extraShadowOffsetYPx = with(density) { 4.dp.toPx() }
  val borderStrokePx = with(density) { 1.dp.toPx() }

  val performTickHaptic = remember(view, haptic) {
    {
      runCatching {
        val performed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
          view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        } else {
          false
        }
        if (!performed) {
          haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
      }
      Unit
    }
  }

  // Animate or snap thumb when selection or re-measured tab bounds change
  LaunchedEffect(clampedSelectedIndex, layoutEpoch, configSignature, reducedMotion) {
    val allMeasured = tabWidths.all { it > 0f }
    if (!allMeasured) return@LaunchedEffect

    val targetX = tabLefts[clampedSelectedIndex]
    val targetW = tabWidths[clampedSelectedIndex]

    if (!hasInitializedThumb || reducedMotion) {
      thumbX.snapTo(targetX)
      thumbWidth.snapTo(targetW)
      hasInitializedThumb = true
      lastAnimatedIndex = clampedSelectedIndex
      return@LaunchedEffect
    }

    if (isDragging) return@LaunchedEffect

    if (lastAnimatedIndex != clampedSelectedIndex) {
      val dir = if (targetX >= thumbX.value) 1f else -1f
      val initialVel = if (abs(thumbX.velocity) > tapPushPx * 0.5f) {
        thumbX.velocity
      } else {
        dir * tapPushPx
      }
      lastAnimatedIndex = clampedSelectedIndex
      val targetSpringX = spring<Float>(dampingRatio = 0.45f, stiffness = 200f)
      val targetSpringW = spring<Float>(dampingRatio = 0.45f, stiffness = 200f)
      launch {
        thumbX.animateTo(
          targetValue = targetX,
          initialVelocity = initialVel,
          animationSpec = targetSpringX
        )
      }
      launch {
        thumbWidth.animateTo(
          targetValue = targetW,
          animationSpec = targetSpringW
        )
      }
    } else if (abs(thumbX.targetValue - targetX) > 0.5f || abs(thumbWidth.targetValue - targetW) > 0.5f) {
      thumbX.snapTo(targetX)
      thumbWidth.snapTo(targetW)
    }
  }

  val supportsBackdropBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
  val trackFill = if (supportsBackdropBlur) {
    Color.White.copy(alpha = if (darkTheme) 0.07f else 0.40f)
  } else {
    Color.White.copy(alpha = if (darkTheme) 0.10f else 0.46f)
  }
  val trackBorder = Color.White.copy(alpha = if (darkTheme) 0.18f else 0.68f)

  val thumbTopColor = Color.White.copy(alpha = if (darkTheme) 0.24f else 0.94f)
  val thumbBottomColor = Color.White.copy(alpha = if (darkTheme) 0.14f else 0.80f)
  val thumbBorderTop = Color.White.copy(alpha = if (darkTheme) 0.44f else 0.98f)
  val thumbBorderBottom = Color.White.copy(alpha = if (darkTheme) 0.18f else 0.60f)
  val specularColor = Color.White.copy(alpha = if (darkTheme) 0.30f else 0.62f)

  val selectedTextColor = MaterialTheme.colorScheme.onSurface
  val unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
  val colorAnimSpec = if (reducedMotion) snap<Color>() else tween<Color>(durationMillis = 190, easing = FastOutSlowInEasing)

  val activeVisualIndex = if (isDragging && dragHoverIndex in options.indices) {
    dragHoverIndex
  } else {
    clampedSelectedIndex
  }

  val currentHazeStyle = LocalHazeStyle.current
  val themeBackground = MaterialTheme.colorScheme.background
  val resolvedHazeStyle = remember(currentHazeStyle, darkTheme, themeBackground) {
    if (currentHazeStyle.backgroundColor.isSpecified && currentHazeStyle.blurRadius != Dp.Unspecified) {
      currentHazeStyle
    } else {
      aurixGlassHazeStyle(darkTheme = darkTheme, backgroundColor = themeBackground)
    }
  }

  CompositionLocalProvider(LocalHazeStyle provides resolvedHazeStyle) {
  Box(
    modifier = modifier
      .then(if (fillWidth) Modifier.fillMaxWidth() else Modifier.wrapContentWidth())
      .clip(CircleShape)
      .then(if (supportsBackdropBlur) backdrop else Modifier)
      .background(trackFill)
      .border(1.dp, trackBorder, CircleShape)
      .padding(4.dp)
  ) {
    Box(
      modifier = Modifier
        .then(if (fillWidth) Modifier.fillMaxWidth() else Modifier.wrapContentWidth())
        .height(IntrinsicSize.Min)
        .semantics { selectableGroup() }
        .pointerInput(options.size, reducedMotion) {
          awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val pointerId = down.id
            val downX = down.position.x
            val velocityTracker = VelocityTracker()
            velocityTracker.addPosition(down.uptimeMillis, down.position)

            val touchSlop = viewConfiguration.touchSlop
            var dragStarted = false
            var dragAnimJob: Job? = null
            var lastCrossedTab = currentSelectedIndex
            val startThumbW = thumbWidth.value.coerceAtLeast(1f)
            val startThumbX = thumbX.value
            val startedInThumb = downX in startThumbX..(startThumbX + startThumbW)
            val grabOffset = if (startedInThumb) (downX - startThumbX) else (startThumbW * 0.5f)

            try {
              while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                if (!change.pressed) {
                  velocityTracker.addPosition(change.uptimeMillis, change.position)
                  if (!dragStarted) {
                    val tappedIndex = findSegmentIndexAtX(downX, tabLefts, tabWidths)
                    if (tappedIndex != currentSelectedIndex) {
                      performTickHaptic()
                    }
                    currentOnSelect(tappedIndex)
                  } else {
                    dragAnimJob?.cancel()
                    isDragging = false
                    dragHoverIndex = -1

                    val releaseVelX = velocityTracker.calculateVelocity().x
                    val totalTrackW = tabWidths.sum().coerceAtLeast(1f)
                    val projectedCenter = projectFlickCenter(
                      thumbLeftPx = thumbX.value,
                      thumbWidthPx = thumbWidth.value,
                      releaseVelocityPxPerSec = releaseVelX,
                      trackTotalWidthPx = totalTrackW,
                      projectionSeconds = 0.20f
                    )
                    val targetIdx = findNearestSegmentIndex(projectedCenter, tabLefts, tabWidths)
                    if (targetIdx != lastCrossedTab) {
                      performTickHaptic()
                    }
                    val targetX = tabLefts[targetIdx]
                    val targetW = tabWidths[targetIdx]
                    lastAnimatedIndex = targetIdx
                    currentOnSelect(targetIdx)

                    scope.launch {
                      if (currentReducedMotion) {
                        thumbX.snapTo(targetX)
                        thumbWidth.snapTo(targetW)
                      } else {
                        val releaseSpringX = spring<Float>(dampingRatio = 0.45f, stiffness = 200f)
                        val releaseSpringW = spring<Float>(dampingRatio = 0.45f, stiffness = 200f)
                        launch {
                          thumbX.animateTo(
                            targetValue = targetX,
                            initialVelocity = releaseVelX,
                            animationSpec = releaseSpringX
                          )
                        }
                        launch {
                          thumbWidth.animateTo(
                            targetValue = targetW,
                            animationSpec = releaseSpringW
                          )
                        }
                      }
                    }
                  }
                  break
                }

                val dx = change.position.x - downX
                velocityTracker.addPosition(change.uptimeMillis, change.position)

                if (!dragStarted && abs(dx) > touchSlop) {
                  dragStarted = true
                  isDragging = true
                  change.consume()
                }

                if (dragStarted) {
                  change.consume()
                  val totalTrackW = tabWidths.sum().coerceAtLeast(1f)
                  val currentW = thumbWidth.value.coerceAtLeast(startThumbW)
                  val maxLeft = (totalTrackW - currentW).coerceAtLeast(0f)
                  val desiredLeft = (change.position.x - grabOffset).coerceIn(0f, maxLeft)
                  val thumbCenter = desiredLeft + currentW * 0.5f
                  val hoveredIdx = findNearestSegmentIndex(thumbCenter, tabLefts, tabWidths)

                  if (hoveredIdx != lastCrossedTab) {
                    lastCrossedTab = hoveredIdx
                    dragHoverIndex = hoveredIdx
                    performTickHaptic()
                  } else if (dragHoverIndex != hoveredIdx) {
                    dragHoverIndex = hoveredIdx
                  }

                  val targetW = tabWidths[hoveredIdx]
                  dragAnimJob?.cancel()
                  dragAnimJob = scope.launch {
                    if (currentReducedMotion) {
                      thumbX.snapTo(desiredLeft)
                      thumbWidth.snapTo(targetW)
                    } else {
                      val dragSpringX = spring<Float>(dampingRatio = 0.70f, stiffness = 450f)
                      val dragSpringW = spring<Float>(dampingRatio = 0.70f, stiffness = 450f)
                      launch { thumbX.animateTo(desiredLeft, animationSpec = dragSpringX) }
                      launch { thumbWidth.animateTo(targetW, animationSpec = dragSpringW) }
                    }
                  }
                }
              }
            } finally {
              isDragging = false
              dragHoverIndex = -1
            }
          }
        }
    ) {
      // Liquid White Glass Thumb drawn purely in the Draw phase (zero frame recomposition)
      Box(
        modifier = Modifier
          .matchParentSize()
          .drawWithContent {
            val w = thumbWidth.value
            val h = size.height
            if (w > 0f && h > 0f) {
              val x = thumbX.value
              val vel = thumbX.velocity
              val speed = if (reducedMotion) 0f else computeGlassVelocitySpeed(vel, velRefPx)
              val signedDir = if (reducedMotion) 0f else (vel / velRefPx).coerceIn(-1f, 1f)

              val sx = 1f + 0.30f * speed
              val sy = 1f - 0.20f * speed
              val drawnW = w * sx
              val drawnH = h * sy
              val drawnLeft = (x + w * 0.5f) - drawnW * 0.5f
              val drawnTop = (h - drawnH) * 0.5f
              val corner = drawnH * 0.5f

              // 1. Dynamic soft shadow growing with velocity
              val spread = baseShadowSpreadPx + extraShadowSpreadPx * speed
              val yOff = baseShadowOffsetYPx + extraShadowOffsetYPx * speed
              val baseAlpha = (if (darkTheme) 0.28f else 0.10f) + (if (darkTheme) 0.18f else 0.12f) * speed
              for (layer in 3 downTo 1) {
                val frac = layer / 3f
                val s = spread * frac
                val alphaLayer = (baseAlpha * (1.15f - frac * 0.65f) * 0.45f).coerceIn(0f, 0.45f)
                drawRoundRect(
                  color = Color.Black.copy(alpha = alphaLayer),
                  topLeft = Offset(drawnLeft - s * 0.45f, drawnTop + yOff * frac - s * 0.20f),
                  size = Size(drawnW + s * 0.90f, drawnH + s * 0.75f),
                  cornerRadius = CornerRadius(corner + s * 0.45f, corner + s * 0.45f)
                )
              }

              // 2. Crisp WHITE glass thumb pill + directional diagonal specular highlight
              val pillPath = Path().apply {
                addRoundRect(
                  RoundRect(
                    rect = Rect(
                      offset = Offset(drawnLeft, drawnTop),
                      size = Size(drawnW, drawnH)
                    ),
                    cornerRadius = CornerRadius(corner, corner)
                  )
                )
              }

              clipPath(pillPath) {
                drawRect(
                  brush = Brush.verticalGradient(
                    colors = listOf(thumbTopColor, thumbBottomColor),
                    startY = drawnTop,
                    endY = drawnTop + drawnH
                  ),
                  topLeft = Offset(drawnLeft, drawnTop),
                  size = Size(drawnW, drawnH)
                )

                // Diagonal specular highlight stripe shifting in the direction of motion
                val stripeShift = signedDir * drawnW * 0.24f
                val stripeCenterX = drawnLeft + drawnW * 0.40f + stripeShift
                val stripeHalfSpan = (drawnW * 0.24f).coerceAtLeast(18f)
                drawRect(
                  brush = Brush.linearGradient(
                    colors = listOf(
                      Color.Transparent,
                      specularColor,
                      Color.Transparent
                    ),
                    start = Offset(stripeCenterX - stripeHalfSpan, drawnTop),
                    end = Offset(stripeCenterX + stripeHalfSpan * 0.65f, drawnTop + drawnH)
                  ),
                  topLeft = Offset(drawnLeft, drawnTop),
                  size = Size(drawnW, drawnH)
                )
              }

              // 3. 1dp light border
              val halfStroke = borderStrokePx * 0.5f
              drawRoundRect(
                brush = Brush.verticalGradient(
                  colors = listOf(thumbBorderTop, thumbBorderBottom),
                  startY = drawnTop,
                  endY = drawnTop + drawnH
                ),
                topLeft = Offset(drawnLeft + halfStroke, drawnTop + halfStroke),
                size = Size(
                  (drawnW - borderStrokePx).coerceAtLeast(0f),
                  (drawnH - borderStrokePx).coerceAtLeast(0f)
                ),
                cornerRadius = CornerRadius(
                  (corner - halfStroke).coerceAtLeast(0f),
                  (corner - halfStroke).coerceAtLeast(0f)
                ),
                style = Stroke(width = borderStrokePx)
              )
            }
            drawContent()
          }
      )

      Row(
        modifier = if (fillWidth) Modifier.fillMaxWidth() else Modifier.wrapContentWidth(),
        verticalAlignment = Alignment.CenterVertically
      ) {
        options.forEachIndexed { index, option ->
          val isVisuallySelected = activeVisualIndex == index
          val isCommittedSelected = clampedSelectedIndex == index
          val contentColor by animateColorAsState(
            targetValue = if (isVisuallySelected) selectedTextColor else unselectedTextColor,
            animationSpec = colorAnimSpec,
            label = "glass_seg_color_$index"
          )
          val labelScale by animateFloatAsState(
            targetValue = if (isVisuallySelected) 1.05f else 1.0f,
            animationSpec = if (reducedMotion) {
              snap()
            } else {
              spring(dampingRatio = 0.52f, stiffness = 360f)
            },
            label = "glass_seg_label_scale_$index"
          )
          val styledLabel = rememberSegmentStyledLabel(option.label)
          val hasKhmer = remember(option.label) { KhmerTypography.containsKhmer(option.label) }

          Box(
            modifier = Modifier
              .then(if (fillWidth) Modifier.weight(1f) else Modifier.wrapContentWidth())
              .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
              .clip(CircleShape)
              .focusable()
              .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown &&
                  (keyEvent.key == Key.Enter || keyEvent.key == Key.DirectionCenter || keyEvent.key == Key.Spacebar)
                ) {
                  performTickHaptic()
                  onSelect(index)
                  true
                } else {
                  false
                }
              }
              .onGloballyPositioned { coords ->
                val newLeft = coords.positionInParent().x
                val newWidth = coords.size.width.toFloat()
                if (abs(tabLefts[index] - newLeft) > 0.5f || abs(tabWidths[index] - newWidth) > 0.5f) {
                  tabLefts[index] = newLeft
                  tabWidths[index] = newWidth
                  layoutEpoch++
                }
              }
              .semantics {
                role = Role.Tab
                selected = isCommittedSelected
                contentDescription = option.label
                onClick(label = option.label) {
                  if (index != currentSelectedIndex) {
                    performTickHaptic()
                  }
                  currentOnSelect(index)
                  true
                }
              }
              .then(if (!option.testTag.isNullOrBlank()) Modifier.testTag(option.testTag) else Modifier)
              .padding(horizontal = if (fillWidth) 8.dp else 12.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
          ) {
            Row(
              modifier = Modifier.graphicsLayer {
                scaleX = labelScale
                scaleY = labelScale
              },
              horizontalArrangement = Arrangement.Center,
              verticalAlignment = Alignment.CenterVertically
            ) {
              if (option.icon != null) {
                Icon(
                  imageVector = option.icon,
                  contentDescription = null,
                  tint = contentColor,
                  modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
              }
              Text(
                text = styledLabel,
                style = MaterialTheme.typography.labelLarge.copy(
                  fontFamily = if (hasKhmer) AurixFontFamilies.NotoSansKhmerFamily else AurixFontFamilies.AurixUiFamily,
                  fontSize = 13.sp,
                  lineHeight = 20.sp,
                  fontWeight = if (isVisuallySelected) FontWeight.Bold else FontWeight.SemiBold,
                  platformStyle = PlatformTextStyle(includeFontPadding = true)
                ),
                color = contentColor,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
              )
            }
          }
        }
      }
    }
  }
  }
}

@Preview(name = "GlassSegmented - Light", showBackground = true, backgroundColor = 0xFFEAF2FA, widthDp = 400)
@Composable
private fun GlassSegmentedLightPreview() {
  val hazeState = remember { HazeState() }
  var sourceIndex by remember { mutableIntStateOf(0) }
  var filterIndex by remember { mutableIntStateOf(0) }

  AurixTheme(darkTheme = false, amoledTheme = false) {
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .background(Color(0xFFEAF2FA))
    ) {
      Box(
        modifier = Modifier
          .matchParentSize()
          .haze(state = hazeState)
          .background(
            Brush.linearGradient(
              colors = listOf(Color(0xFFD8ECFF), Color(0xFFF3E7FF), Color(0xFFE5F6F2))
            )
          )
      )
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
      ) {
        val previewStyle = aurixGlassHazeStyle(darkTheme = false, backgroundColor = Color(0xFFEAF2FA))
        GlassSegmented(
          options = listOf(
            SegOption("YouTube", Icons.Rounded.PlayCircle),
            SegOption("YT Link", Icons.Rounded.Link)
          ),
          selectedIndex = sourceIndex,
          onSelect = { sourceIndex = it },
          fillWidth = true,
          darkTheme = false,
          backdrop = Modifier.hazeChild(state = hazeState, style = previewStyle)
        )
        GlassSegmented(
          options = listOf(
            SegOption("All"),
            SegOption("Songs"),
            SegOption("Albums"),
            SegOption("Artists")
          ),
          selectedIndex = filterIndex,
          onSelect = { filterIndex = it },
          fillWidth = false,
          darkTheme = false,
          backdrop = Modifier.hazeChild(state = hazeState, style = previewStyle)
        )
      }
    }
  }
}

@Preview(name = "GlassSegmented - Dark", showBackground = true, backgroundColor = 0xFF0B111E, widthDp = 400)
@Composable
private fun GlassSegmentedDarkPreview() {
  val hazeState = remember { HazeState() }
  var libraryIndex by remember { mutableIntStateOf(0) }

  AurixTheme(darkTheme = true, amoledTheme = false) {
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .background(Color(0xFF0B111E))
    ) {
      Box(
        modifier = Modifier
          .matchParentSize()
          .haze(state = hazeState)
          .background(
            Brush.linearGradient(
              colors = listOf(Color(0xFF10213A), Color(0xFF1D1538), Color(0xFF0B111E))
            )
          )
      )
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        val previewStyle = aurixGlassHazeStyle(darkTheme = true, backgroundColor = Color(0xFF0B111E))
        GlassSegmented(
          options = listOf(
            SegOption("My songs"),
            SegOption("Favorites"),
            SegOption("Recent"),
            SegOption("Albums")
          ),
          selectedIndex = libraryIndex,
          onSelect = { libraryIndex = it },
          fillWidth = true,
          darkTheme = true,
          backdrop = Modifier.hazeChild(state = hazeState, style = previewStyle)
        )
      }
    }
  }
}

@Preview(name = "GlassSegmented - AMOLED", showBackground = true, backgroundColor = 0xFF000000, widthDp = 400)
@Composable
private fun GlassSegmentedAmoledPreview() {
  val hazeState = remember { HazeState() }
  var themeIndex by remember { mutableIntStateOf(3) }

  AurixTheme(darkTheme = true, amoledTheme = true) {
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .background(Color(0xFF000000))
    ) {
      Box(
        modifier = Modifier
          .matchParentSize()
          .haze(state = hazeState)
          .background(Color(0xFF000000))
      )
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        val previewStyle = aurixGlassHazeStyle(darkTheme = true, backgroundColor = Color(0xFF000000))
        GlassSegmented(
          options = listOf(
            SegOption("System"),
            SegOption("Light"),
            SegOption("Dark"),
            SegOption("AMOLED")
          ),
          selectedIndex = themeIndex,
          onSelect = { themeIndex = it },
          fillWidth = true,
          darkTheme = true,
          backdrop = Modifier.hazeChild(state = hazeState, style = previewStyle)
        )
      }
    }
  }
}
