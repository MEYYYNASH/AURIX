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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
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
import kotlin.math.exp
import kotlin.math.sqrt
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
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

/** Non-recomposition mutable physics holder for 60/120Hz zero-jank drag tracking. */
private class GlassDragPhysicsState {
  var isDragging: Boolean = false
  var targetX: Float = 0f
  var targetW: Float = 0f
  var currentX: Float = 0f
  var currentW: Float = 0f
  var velX: Float = 0f
  var velW: Float = 0f
  var smoothedVisualVelX: Float = 0f
  var dragFollowerJob: Job? = null
  var settleJob: Job? = null
}

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

internal fun applyLiquidEdgeResistance(
  rawLeft: Float,
  minLeft: Float,
  maxLeft: Float,
  maxOverdragPx: Float
): Float {
  if (maxOverdragPx <= 0f) return rawLeft.coerceIn(minLeft, maxLeft)
  return when {
    rawLeft < minLeft -> {
      val over = minLeft - rawLeft
      val resisted = maxOverdragPx * (1f - 1f / (1f + over / (maxOverdragPx * 2.5f)))
      minLeft - resisted
    }
    rawLeft > maxLeft -> {
      val over = rawLeft - maxLeft
      val resisted = maxOverdragPx * (1f - 1f / (1f + over / (maxOverdragPx * 2.5f)))
      maxLeft + resisted
    }
    else -> rawLeft
  }
}

internal fun stepGlassDragSpring(
  value: Float,
  velocity: Float,
  target: Float,
  dtSeconds: Float,
  stiffness: Float = 450f,
  dampingRatio: Float = 0.70f,
  subSteps: Int = 4
): Pair<Float, Float> {
  val clampedDt = dtSeconds.coerceIn(1f / 240f, 1f / 30f)
  val steps = subSteps.coerceAtLeast(1)
  val subDt = clampedDt / steps
  val damping = 2f * dampingRatio * sqrt(stiffness)
  var curVal = value
  var curVel = velocity
  repeat(steps) {
    val accel = stiffness * (target - curVal) - damping * curVel
    curVel += accel * subDt
    curVal += curVel * subDt
  }
  return curVal to curVel
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

  // Animatable drives tap & release settle with true Compose spring(0.45f, 200f);
  // render* states are updated in-frame (withFrameNanos / animateTo) and read ONLY in drawWithContent.
  val thumbX = remember { Animatable(0f) }
  val thumbWidth = remember { Animatable(0f) }
  val renderXState = remember { mutableFloatStateOf(0f) }
  val renderWState = remember { mutableFloatStateOf(0f) }
  val renderVelXState = remember { mutableFloatStateOf(0f) }
  val physics = remember { GlassDragPhysicsState() }
  val dragHoverIndexState = remember { mutableIntStateOf(-1) }
  val activeVisualIndexState = remember(options.size) {
    derivedStateOf {
      val hovered = dragHoverIndexState.intValue
      if (hovered in options.indices) hovered else currentSelectedIndex
    }
  }

  var hasInitializedThumb by remember { mutableStateOf(false) }
  var lastAnimatedIndex by remember { mutableIntStateOf(-1) }

  val velRefPx = with(density) { 1100.dp.toPx() }
  val tapPushPx = with(density) { 320.dp.toPx() }
  val maxReleaseVelPx = with(density) { 4200.dp.toPx() }
  val maxOverdragPx = with(density) { 8.dp.toPx() }
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

  fun launchSettleToIndex(
    targetIdx: Int,
    initialVelX: Float,
    initialVelW: Float = 0f
  ) {
    val safeIdx = targetIdx.coerceIn(0, options.lastIndex)
    val targetX = tabLefts[safeIdx]
    val targetW = tabWidths[safeIdx]
    if (targetW <= 0f) return

    lastAnimatedIndex = safeIdx
    physics.settleJob?.cancel()
    physics.settleJob = scope.launch {
      if (currentReducedMotion) {
        thumbX.snapTo(targetX)
        thumbWidth.snapTo(targetW)
        physics.currentX = targetX
        physics.currentW = targetW
        physics.velX = 0f
        physics.velW = 0f
        physics.smoothedVisualVelX = 0f
        renderXState.floatValue = targetX
        renderWState.floatValue = targetW
        renderVelXState.floatValue = 0f
      } else {
        // Sync Animatable start position seamlessly from live drag coordinates
        thumbX.snapTo(physics.currentX)
        thumbWidth.snapTo(physics.currentW.coerceAtLeast(1f))
        val springX = spring<Float>(dampingRatio = 0.45f, stiffness = 200f)
        val springW = spring<Float>(dampingRatio = 0.45f, stiffness = 200f)
        val xJob = launch {
          thumbX.animateTo(
            targetValue = targetX,
            initialVelocity = initialVelX,
            animationSpec = springX
          ) {
            physics.currentX = value
            physics.velX = velocity
            renderXState.floatValue = value
            physics.smoothedVisualVelX = physics.smoothedVisualVelX * 0.55f + velocity * 0.45f
            renderVelXState.floatValue = physics.smoothedVisualVelX
          }
          physics.velX = 0f
          physics.smoothedVisualVelX = 0f
          renderVelXState.floatValue = 0f
        }
        val wJob = launch {
          thumbWidth.animateTo(
            targetValue = targetW,
            initialVelocity = initialVelW,
            animationSpec = springW
          ) {
            physics.currentW = value
            physics.velW = velocity
            renderWState.floatValue = value
          }
          physics.velW = 0f
        }
        xJob.join()
        wJob.join()
      }
    }
  }

  // Animate or snap thumb when selection or re-measured tab bounds change
  LaunchedEffect(clampedSelectedIndex, layoutEpoch, configSignature, reducedMotion) {
    val allMeasured = tabWidths.all { it > 0f }
    if (!allMeasured) return@LaunchedEffect

    val targetX = tabLefts[clampedSelectedIndex]
    val targetW = tabWidths[clampedSelectedIndex]

    if (!hasInitializedThumb || reducedMotion) {
      physics.dragFollowerJob?.cancel()
      physics.settleJob?.cancel()
      thumbX.snapTo(targetX)
      thumbWidth.snapTo(targetW)
      physics.currentX = targetX
      physics.currentW = targetW
      physics.targetX = targetX
      physics.targetW = targetW
      physics.velX = 0f
      physics.velW = 0f
      physics.smoothedVisualVelX = 0f
      renderXState.floatValue = targetX
      renderWState.floatValue = targetW
      renderVelXState.floatValue = 0f
      hasInitializedThumb = true
      lastAnimatedIndex = clampedSelectedIndex
      return@LaunchedEffect
    }

    if (physics.isDragging) return@LaunchedEffect

    if (lastAnimatedIndex != clampedSelectedIndex) {
      val currentVel = if (abs(physics.velX) > 1f) physics.velX else thumbX.velocity
      val dir = if (targetX >= physics.currentX) 1f else -1f
      val initialVel = if (abs(currentVel) > tapPushPx * 0.5f) {
        currentVel
      } else {
        dir * tapPushPx
      }
      launchSettleToIndex(
        targetIdx = clampedSelectedIndex,
        initialVelX = initialVel,
        initialVelW = physics.velW
      )
    } else if (
      physics.settleJob?.isActive != true &&
      (abs(physics.currentX - targetX) > 0.5f || abs(physics.currentW - targetW) > 0.5f)
    ) {
      thumbX.snapTo(targetX)
      thumbWidth.snapTo(targetW)
      physics.currentX = targetX
      physics.currentW = targetW
      renderXState.floatValue = targetX
      renderWState.floatValue = targetW
      renderVelXState.floatValue = 0f
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

  // Pre-remembered draw objects to guarantee 0 allocations per 120Hz frame
  val thumbGradientColors = remember(thumbTopColor, thumbBottomColor) {
    listOf(thumbTopColor, thumbBottomColor)
  }
  val specularGradientColors = remember(specularColor) {
    listOf(Color.Transparent, specularColor, Color.Transparent)
  }
  val borderGradientColors = remember(thumbBorderTop, thumbBorderBottom) {
    listOf(thumbBorderTop, thumbBorderBottom)
  }
  val pillPath = remember { Path() }

  val selectedTextColor = MaterialTheme.colorScheme.onSurface
  val unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant

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
    ) {
      // Static frosted backdrop layer isolated from the 120Hz animated thumb layer
      Box(
        modifier = Modifier
          .matchParentSize()
          .then(if (supportsBackdropBlur) backdrop else Modifier)
          .background(trackFill)
          .border(1.dp, trackBorder, CircleShape)
      )

      // Interactive segmented track
      Box(
        modifier = Modifier
          .then(if (fillWidth) Modifier.fillMaxWidth() else Modifier.wrapContentWidth())
          .padding(4.dp)
          .semantics { selectableGroup() }
          .pointerInput(options.size, reducedMotion) {
            awaitEachGesture {
              val down = awaitFirstDown(requireUnconsumed = false)
              val pointerId = down.id
              val downX = down.position.x
              val downY = down.position.y
              val velocityTracker = VelocityTracker()
              velocityTracker.addPosition(down.uptimeMillis, down.position)

              val touchSlop = viewConfiguration.touchSlop
              var dragStarted = false
              var completedNormally = false
              var lastCrossedTab = currentSelectedIndex

              val startThumbW = physics.currentW.coerceAtLeast(1f)
              val startThumbX = physics.currentX
              val startedInThumb = downX in startThumbX..(startThumbX + startThumbW)
              // Tight horizontal threshold so drag begins immediately with zero snag
              val horizontalSlop = if (startedInThumb) {
                (touchSlop * 0.25f).coerceAtLeast(3f)
              } else {
                (touchSlop * 0.42f).coerceAtLeast(5f)
              }
              // Center-relative grab offset prevents sideways jumps when crossing uneven-width tabs
              val centerGrabOffset = if (startedInThumb) {
                downX - (startThumbX + startThumbW * 0.5f)
              } else {
                0f
              }

              try {
                while (true) {
                  val event = awaitPointerEvent()
                  val change = event.changes.firstOrNull { it.id == pointerId } ?: break

                  if (!change.pressed) {
                    velocityTracker.addPosition(change.uptimeMillis, change.position)
                    completedNormally = true
                    if (!dragStarted) {
                      val tappedIndex = findSegmentIndexAtX(downX, tabLefts, tabWidths)
                      if (tappedIndex != currentSelectedIndex) {
                        performTickHaptic()
                      }
                      currentOnSelect(tappedIndex)
                    } else {
                      physics.dragFollowerJob?.cancel()
                      physics.dragFollowerJob = null
                      physics.isDragging = false
                      dragHoverIndexState.intValue = -1

                      val rawTrackerVelX = velocityTracker.calculateVelocity().x
                      val trackerVelX = if (rawTrackerVelX.isFinite()) {
                        rawTrackerVelX.coerceIn(-maxReleaseVelPx, maxReleaseVelPx)
                      } else {
                        0f
                      }
                      // Preserve spring momentum smoothly while honoring fast flicks
                      val releaseVelX = if (abs(trackerVelX) > abs(physics.velX)) {
                        trackerVelX
                      } else {
                        physics.velX * 0.65f + trackerVelX * 0.35f
                      }.coerceIn(-maxReleaseVelPx, maxReleaseVelPx)

                      val totalTrackW = tabWidths.sum().coerceAtLeast(1f)
                      val projectedCenter = projectFlickCenter(
                        thumbLeftPx = physics.currentX,
                        thumbWidthPx = physics.currentW.coerceAtLeast(1f),
                        releaseVelocityPxPerSec = releaseVelX,
                        trackTotalWidthPx = totalTrackW,
                        projectionSeconds = 0.20f
                      )
                      val targetIdx = findNearestSegmentIndex(projectedCenter, tabLefts, tabWidths)
                      if (targetIdx != lastCrossedTab) {
                        performTickHaptic()
                      }
                      currentOnSelect(targetIdx)
                      launchSettleToIndex(
                        targetIdx = targetIdx,
                        initialVelX = releaseVelX,
                        initialVelW = physics.velW
                      )
                    }
                    break
                  }

                  val dx = change.position.x - downX
                  val dy = change.position.y - downY
                  velocityTracker.addPosition(change.uptimeMillis, change.position)

                  if (!dragStarted) {
                    // Yield cleanly to vertical scrolling (e.g., LazyColumn) when swiping vertically
                    if (abs(dy) > touchSlop && abs(dy) > abs(dx) * 1.15f) {
                      break
                    }
                    if (abs(dx) > horizontalSlop && abs(dx) >= abs(dy)) {
                      dragStarted = true
                      physics.isDragging = true
                      physics.settleJob?.cancel()
                      physics.settleJob = null
                      physics.currentX = renderXState.floatValue
                      physics.currentW = renderWState.floatValue.coerceAtLeast(1f)
                      physics.velX = if (abs(physics.velX) > 1f) physics.velX else thumbX.velocity
                      physics.targetX = physics.currentX
                      physics.targetW = physics.currentW
                      change.consume()

                      // Launch ONE zero-mutex, frame-locked 60/120Hz spring-damper loop for the entire drag
                      physics.dragFollowerJob?.cancel()
                      physics.dragFollowerJob = scope.launch {
                        var lastFrameNanos = 0L
                        while (isActive && physics.isDragging) {
                          withFrameNanos { frameNanos ->
                            if (!physics.isDragging) return@withFrameNanos
                            if (lastFrameNanos == 0L) {
                              lastFrameNanos = frameNanos
                              return@withFrameNanos
                            }
                            val dt = ((frameNanos - lastFrameNanos) / 1_000_000_000f)
                              .coerceIn(1f / 240f, 1f / 30f)
                            lastFrameNanos = frameNanos

                            if (currentReducedMotion) {
                              physics.currentX = physics.targetX
                              physics.currentW = physics.targetW
                              physics.velX = 0f
                              physics.velW = 0f
                              physics.smoothedVisualVelX = 0f
                            } else {
                              val (nextX, nextVx) = stepGlassDragSpring(
                                value = physics.currentX,
                                velocity = physics.velX,
                                target = physics.targetX,
                                dtSeconds = dt,
                                stiffness = 450f,
                                dampingRatio = 0.70f,
                                subSteps = 4
                              )
                              val (nextW, nextVw) = stepGlassDragSpring(
                                value = physics.currentW,
                                velocity = physics.velW,
                                target = physics.targetW,
                                dtSeconds = dt,
                                stiffness = 450f,
                                dampingRatio = 0.70f,
                                subSteps = 4
                              )
                              physics.currentX = nextX
                              physics.velX = nextVx
                              physics.currentW = nextW
                              physics.velW = nextVw
                              // Frame-rate-independent exponential velocity filter for silky squash/stretch
                              val smoothFactor = 1f - exp(-24f * dt)
                              physics.smoothedVisualVelX += (nextVx - physics.smoothedVisualVelX) * smoothFactor
                            }

                            // Write directly during the animation phase for zero-frame latency & zero mutex overhead
                            renderXState.floatValue = physics.currentX
                            renderWState.floatValue = physics.currentW
                            renderVelXState.floatValue = physics.smoothedVisualVelX
                          }
                        }
                      }
                    }
                  }

                  if (dragStarted) {
                    change.consume()
                    val totalTrackW = tabWidths.sum().coerceAtLeast(1f)
                    val pointerCenter = (change.position.x - centerGrabOffset).coerceIn(0f, totalTrackW)
                    val hoveredIdx = findNearestSegmentIndex(pointerCenter, tabLefts, tabWidths)

                    if (hoveredIdx != lastCrossedTab) {
                      lastCrossedTab = hoveredIdx
                      dragHoverIndexState.intValue = hoveredIdx
                      performTickHaptic()
                    } else if (dragHoverIndexState.intValue != hoveredIdx) {
                      dragHoverIndexState.intValue = hoveredIdx
                    }

                    val targetW = tabWidths[hoveredIdx].coerceAtLeast(startThumbW)
                    val maxLeft = (totalTrackW - targetW).coerceAtLeast(0f)
                    val rawDesiredLeft = (change.position.x - centerGrabOffset) - targetW * 0.5f
                    val desiredLeft = applyLiquidEdgeResistance(
                      rawLeft = rawDesiredLeft,
                      minLeft = 0f,
                      maxLeft = maxLeft,
                      maxOverdragPx = maxOverdragPx
                    )
                    physics.targetX = desiredLeft
                    physics.targetW = targetW
                  }
                }
              } finally {
                if (dragStarted && !completedNormally) {
                  // Gesture was cancelled mid-drag (e.g., system swipe) — smoothly spring back to current tab
                  physics.dragFollowerJob?.cancel()
                  physics.dragFollowerJob = null
                  physics.isDragging = false
                  dragHoverIndexState.intValue = -1
                  launchSettleToIndex(
                    targetIdx = currentSelectedIndex,
                    initialVelX = physics.velX,
                    initialVelW = physics.velW
                  )
                } else {
                  physics.isDragging = false
                  dragHoverIndexState.intValue = -1
                }
              }
            }
          }
      ) {
        // Liquid White Glass Thumb in its own hardware RenderNode (zero Haze re-blur & zero frame recomposition)
        Box(
          modifier = Modifier
            .matchParentSize()
            .graphicsLayer { clip = false }
            .drawWithContent {
              val w = renderWState.floatValue
              val h = size.height
              if (w > 0f && h > 0f) {
                val x = renderXState.floatValue
                val vel = renderVelXState.floatValue
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

                // 2. Crisp WHITE glass thumb pill + directional diagonal specular highlight (reused Path -> 0 allocs)
                pillPath.rewind()
                pillPath.addRoundRect(
                  RoundRect(
                    rect = Rect(
                      offset = Offset(drawnLeft, drawnTop),
                      size = Size(drawnW, drawnH)
                    ),
                    cornerRadius = CornerRadius(corner, corner)
                  )
                )

                clipPath(pillPath) {
                  drawRect(
                    brush = Brush.verticalGradient(
                      colors = thumbGradientColors,
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
                      colors = specularGradientColors,
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
                    colors = borderGradientColors,
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
            GlassSegmentTab(
              index = index,
              option = option,
              activeVisualIndexState = activeVisualIndexState,
              committedSelectedIndex = clampedSelectedIndex,
              fillWidth = fillWidth,
              reducedMotion = reducedMotion,
              selectedTextColor = selectedTextColor,
              unselectedTextColor = unselectedTextColor,
              onSelectIndex = {
                if (index != currentSelectedIndex) {
                  performTickHaptic()
                }
                currentOnSelect(index)
              },
              onMeasured = { newLeft, newWidth ->
                if (abs(tabLefts[index] - newLeft) > 0.5f || abs(tabWidths[index] - newWidth) > 0.5f) {
                  tabLefts[index] = newLeft
                  tabWidths[index] = newWidth
                  layoutEpoch++
                }
              },
              modifier = if (fillWidth) Modifier.weight(1f) else Modifier.wrapContentWidth()
            )
          }
        }
      }
    }
  }
}

@Composable
private fun GlassSegmentTab(
  index: Int,
  option: SegOption,
  activeVisualIndexState: State<Int>,
  committedSelectedIndex: Int,
  fillWidth: Boolean,
  reducedMotion: Boolean,
  selectedTextColor: Color,
  unselectedTextColor: Color,
  onSelectIndex: () -> Unit,
  onMeasured: (left: Float, width: Float) -> Unit,
  modifier: Modifier = Modifier
) {
  val isVisuallySelected by remember(index, activeVisualIndexState) {
    derivedStateOf { activeVisualIndexState.value == index }
  }
  val isCommittedSelected = committedSelectedIndex == index
  val colorAnimSpec = remember(reducedMotion) {
    if (reducedMotion) snap<Color>() else tween<Color>(durationMillis = 170, easing = FastOutSlowInEasing)
  }
  val contentColor by animateColorAsState(
    targetValue = if (isVisuallySelected) selectedTextColor else unselectedTextColor,
    animationSpec = colorAnimSpec,
    label = "glass_seg_color_$index"
  )
  val labelScaleState = animateFloatAsState(
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
    modifier = modifier
      .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
      .clip(CircleShape)
      .focusable()
      .onPreviewKeyEvent { keyEvent ->
        if (keyEvent.type == KeyEventType.KeyDown &&
          (keyEvent.key == Key.Enter || keyEvent.key == Key.DirectionCenter || keyEvent.key == Key.Spacebar)
        ) {
          onSelectIndex()
          true
        } else {
          false
        }
      }
      .onGloballyPositioned { coords ->
        onMeasured(coords.positionInParent().x, coords.size.width.toFloat())
      }
      .semantics {
        role = Role.Tab
        selected = isCommittedSelected
        contentDescription = option.label
        onClick(label = option.label) {
          onSelectIndex()
          true
        }
      }
      .then(if (!option.testTag.isNullOrBlank()) Modifier.testTag(option.testTag) else Modifier)
      .padding(horizontal = if (fillWidth) 8.dp else 12.dp, vertical = 8.dp),
    contentAlignment = Alignment.Center
  ) {
    Row(
      modifier = Modifier.graphicsLayer {
        val s = labelScaleState.value
        scaleX = s
        scaleY = s
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
