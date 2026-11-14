package com.example.ui.screens

import android.os.Build
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

data class TabItem(
  val label: String,
  val icon: ImageVector
)

private class LensClipShape(
  private val getRect: (Size) -> RoundRect
) : Shape {
  override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
    return Outline.Rounded(getRect(size))
  }
}

@Composable
fun LiquidGlassTabBar(
  tabs: List<TabItem>,
  selectedIndex: Int,
  onSelect: (Int) -> Unit,
  modifier: Modifier = Modifier,
  reducedMotion: Boolean = false
) {
  if (tabs.isEmpty()) return

  val context = LocalContext.current
  val density = LocalDensity.current
  val haptic = LocalHapticFeedback.current

  val systemAnimScale = remember(context) {
    try {
      Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1.0f)
    } catch (_: Exception) {
      1.0f
    }
  }
  val skipAnimations = reducedMotion || systemAnimScale == 0f

  val darkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f ||
    (isSystemInDarkTheme() && MaterialTheme.colorScheme.surface.luminance() < 0.5f)
  val accentColor = MaterialTheme.colorScheme.primary

  val barInnerHeightPx = with(density) { 54.dp.toPx() }
  val restMarginPx = with(density) { 4.dp.toPx() }
  val extraWidthPressedPx = with(density) { 26.dp.toPx() }
  val extraHeightPressedPx = with(density) { 30.dp.toPx() }

  var innerWidthPx by remember { mutableFloatStateOf(0f) }
  val tabCount = tabs.size
  val tabWidthPx = if (innerWidthPx > 0f) innerWidthPx / tabCount else 0f

  // Animation state kept strictly inside Animatable / SnapshotState so frames never trigger full recomposition
  val lensX = remember { Animatable(0f) }
  val pressProgress = remember { Animatable(0f) }
  var velocityX by remember { mutableFloatStateOf(0f) }
  var isPointerActive by remember { mutableStateOf(false) }

  val currentSelected by rememberUpdatedState(selectedIndex)
  val currentOnSelect by rememberUpdatedState(onSelect)

  val dragXSpring = remember(skipAnimations) {
    if (skipAnimations) snap<Float>() else spring(dampingRatio = 0.87f, stiffness = 380f)
  }
  val settleXSpring = remember(skipAnimations) {
    if (skipAnimations) snap<Float>() else spring(dampingRatio = 0.87f, stiffness = 220f)
  }
  val pressSpring = remember(skipAnimations) {
    if (skipAnimations) snap<Float>() else spring(dampingRatio = 0.74f, stiffness = 260f)
  }

  LaunchedEffect(selectedIndex, tabWidthPx) {
    if (tabWidthPx > 0f && !isPointerActive) {
      val target = selectedIndex.coerceIn(0, tabCount - 1) * tabWidthPx
      if (abs(lensX.value - target) > 0.5f) {
        lensX.animateTo(target, settleXSpring)
      } else {
        lensX.snapTo(target)
      }
    }
  }

  fun computeLensRoundRect(rowSize: Size): RoundRect {
    if (tabWidthPx <= 0f) return RoundRect(0f, 0f, 0f, 0f, CornerRadius.Zero)
    val p = pressProgress.value.coerceIn(0f, 1f)
    val speedStretch = if (skipAnimations) {
      0f
    } else {
      min(abs(velocityX) / 4000f, 0.1f) * tabWidthPx * 0.6f
    }
    val expandW = if (skipAnimations) 0f else extraWidthPressedPx * p
    val expandH = if (skipAnimations) 0f else extraHeightPressedPx * p

    val lensW = (tabWidthPx - restMarginPx) + expandW + speedStretch
    val lensH = (barInnerHeightPx - restMarginPx) + expandH
    val centerX = lensX.value + tabWidthPx * 0.5f
    val centerY = rowSize.height * 0.5f

    val left = centerX - lensW * 0.5f
    val top = centerY - lensH * 0.5f
    val radius = lensH * 0.5f
    return RoundRect(
      rect = Rect(left, top, left + lensW, top + lensH),
      cornerRadius = CornerRadius(radius, radius)
    )
  }

  val lensClipShape = remember(tabWidthPx, skipAnimations) {
    LensClipShape { rowSize -> computeLensRoundRect(rowSize) }
  }

  val glassBaseFill = if (darkTheme) Color(0xFF424B5E).copy(alpha = 0.42f) else Color.White.copy(alpha = 0.30f)
  val innerEdgeHighlight = Color.White.copy(alpha = if (darkTheme) 0.24f else 0.62f)
  val topEdgeBrightHighlight = Color.White.copy(alpha = if (darkTheme) 0.48f else 0.90f)
  val baseTabColor = if (darkTheme) Color(0xFFE5EAF5) else Color(0xFF1E293B)

  Box(
    modifier = modifier
      .padding(horizontal = 22.dp)
      .widthIn(max = 350.dp)
      .fillMaxWidth()
      .height(64.dp),
    contentAlignment = Alignment.Center
  ) {
    // 1. Soft Ambient Pill Shadow
    Box(
      modifier = Modifier
        .matchParentSize()
        .drawBehind {
          val r = size.height * 0.5f
          drawRoundRect(
            brush = Brush.verticalGradient(
              colors = listOf(
                Color.Black.copy(alpha = if (darkTheme) 0.28f else 0.08f),
                Color.Black.copy(alpha = if (darkTheme) 0.42f else 0.14f)
              )
            ),
            topLeft = Offset(0f, 6.dp.toPx()),
            size = Size(size.width, size.height),
            cornerRadius = CornerRadius(r, r)
          )
        }
    )

    // 2. Glass Pill Backdrop & Inner Highlights (clipped cleanly to CircleShape)
    Box(
      modifier = Modifier
        .matchParentSize()
        .clip(CircleShape)
    ) {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Box(
          modifier = Modifier
            .matchParentSize()
            .blur(20.dp)
            .background(
              Brush.horizontalGradient(
                colors = if (darkTheme) {
                  listOf(
                    accentColor.copy(alpha = 0.14f),
                    Color.White.copy(alpha = 0.10f),
                    accentColor.copy(alpha = 0.12f)
                  )
                } else {
                  listOf(
                    accentColor.copy(alpha = 0.12f),
                    Color.White.copy(alpha = 0.46f),
                    Color(0xFFC4B5FD).copy(alpha = 0.24f)
                  )
                }
              )
            )
        )
      }

      Box(
        modifier = Modifier
          .matchParentSize()
          .background(glassBaseFill)
          .border(1.dp, innerEdgeHighlight, CircleShape)
          .drawBehind {
            val stroke15 = 1.5.dp.toPx()
            val inset = stroke15 * 0.5f
            val r = (size.height - stroke15) * 0.5f
            drawRoundRect(
              brush = Brush.verticalGradient(
                colors = listOf(
                  topEdgeBrightHighlight,
                  topEdgeBrightHighlight.copy(alpha = 0.14f),
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
    }

    // 3. Interactive Content Container (5dp padding all around -> 54dp inner height; unclipped so lens lifts freely)
    Box(
      modifier = Modifier
        .matchParentSize()
        .padding(5.dp)
        .onSizeChanged { innerWidthPx = it.width.toFloat() }
        .pointerInput(tabCount, tabWidthPx, skipAnimations) {
          if (tabWidthPx <= 0f) return@pointerInput
          val maxLensX = (tabCount - 1) * tabWidthPx
          val touchSlopSq = viewConfiguration.touchSlop * viewConfiguration.touchSlop
          var releaseJob: Job? = null

          coroutineScope {
            while (true) {
              awaitPointerEventScope {
                val down = awaitPointerEvent().changes.firstOrNull { it.pressed } ?: return@awaitPointerEventScope
                down.consume()
                releaseJob?.cancel()
                val pressStartMs = System.currentTimeMillis()
                isPointerActive = true
                val startPos = down.position
                var didDrag = false
                var activeIndex = currentSelected.coerceIn(0, tabCount - 1)
                val velocityTracker = VelocityTracker()
                velocityTracker.addPosition(down.uptimeMillis, down.position)

                launch { pressProgress.animateTo(1f, pressSpring) }

                val pointerId = down.id
                var lastPos = down.position

                while (true) {
                  val event = awaitPointerEvent()
                  val change = event.changes.firstOrNull { it.id == pointerId } ?: event.changes.firstOrNull()
                  if (change == null || !change.pressed) {
                    val vX = velocityTracker.calculateVelocity().x
                    velocityX = vX
                    val elapsed = System.currentTimeMillis() - pressStartMs

                    if (!didDrag) {
                      val tappedIdx = (lastPos.x / tabWidthPx).toInt().coerceIn(0, tabCount - 1)
                      if (tappedIdx != currentSelected) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        currentOnSelect(tappedIdx)
                      }
                      val targetX = tappedIdx * tabWidthPx
                      launch {
                        lensX.animateTo(targetX, settleXSpring)
                      }
                      releaseJob = launch {
                        val remainingHold = (180L - elapsed).coerceAtLeast(0L)
                        if (remainingHold > 0L) delay(remainingHold)
                        isPointerActive = false
                        velocityX = 0f
                        pressProgress.animateTo(0f, pressSpring)
                      }
                    } else {
                      val finalIdx = (lensX.value / tabWidthPx).roundToInt().coerceIn(0, tabCount - 1)
                      if (finalIdx != currentSelected) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        currentOnSelect(finalIdx)
                      }
                      val targetX = finalIdx * tabWidthPx
                      isPointerActive = false
                      velocityX = 0f
                      launch { lensX.animateTo(targetX, settleXSpring) }
                      releaseJob = launch { pressProgress.animateTo(0f, pressSpring) }
                    }
                    break
                  }

                  change.consume()
                  lastPos = change.position
                  velocityTracker.addPosition(change.uptimeMillis, change.position)

                  val dxTotal = change.position.x - startPos.x
                  val dyTotal = change.position.y - startPos.y
                  if (!didDrag && (dxTotal * dxTotal + dyTotal * dyTotal) > touchSlopSq) {
                    didDrag = true
                  }

                  if (didDrag || change.positionChange() != Offset.Zero) {
                    velocityX = velocityTracker.calculateVelocity().x
                    val rawX = change.position.x - tabWidthPx * 0.5f
                    val rubberX = when {
                      rawX < 0f -> rawX * 0.25f
                      rawX > maxLensX -> maxLensX + (rawX - maxLensX) * 0.25f
                      else -> rawX
                    }
                    launch { lensX.animateTo(rubberX, dragXSpring) }

                    val liveIdx = (rubberX / tabWidthPx).roundToInt().coerceIn(0, tabCount - 1)
                    if (liveIdx != activeIndex) {
                      activeIndex = liveIdx
                      haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                      currentOnSelect(liveIdx)
                    }
                  }
                }
              }
            }
          }
        }
    ) {
      // A. Signature Liquid Glass Lens Background Layer
      Box(
        modifier = Modifier
          .matchParentSize()
          .drawBehind {
            val rr = computeLensRoundRect(size)
            if (rr.width <= 0f || rr.height <= 0f) return@drawBehind
            val p = pressProgress.value.coerceIn(0f, 1f)
            val corner = CornerRadius(rr.topLeftCornerRadius.x, rr.topLeftCornerRadius.y)
            val topLeft = Offset(rr.left, rr.top)
            val lensSize = Size(rr.width, rr.height)

            val fillAlpha = if (darkTheme) (0.16f + 0.18f * p) else (0.44f + 0.30f * p)
            drawRoundRect(
              color = Color.White.copy(alpha = fillAlpha),
              topLeft = topLeft,
              size = lensSize,
              cornerRadius = corner
            )

            if (p > 0.02f) {
              drawRoundRect(
                brush = Brush.linearGradient(
                  colors = listOf(
                    Color.White.copy(alpha = 0.45f * p),
                    Color.White.copy(alpha = 0.08f * p),
                    Color.Transparent
                  ),
                  start = topLeft,
                  end = Offset(rr.right, rr.bottom)
                ),
                topLeft = topLeft,
                size = lensSize,
                cornerRadius = corner
              )
            }

            drawRoundRect(
              color = Color.White.copy(alpha = if (darkTheme) (0.20f + 0.42f * p) else (0.50f + 0.42f * p)),
              topLeft = topLeft,
              size = lensSize,
              cornerRadius = corner,
              style = Stroke(width = (1f + 0.8f * p).dp.toPx())
            )

            if (p > 0.03f) {
              val rimWidth = 2.dp.toPx()
              drawRoundRect(
                brush = Brush.horizontalGradient(
                  colors = listOf(
                    Color(0xFFFF4D9D).copy(alpha = 0.30f * p),
                    Color.Transparent,
                    Color(0xFF007AFF).copy(alpha = 0.30f * p)
                  ),
                  startX = rr.left,
                  endX = rr.right
                ),
                topLeft = topLeft,
                size = lensSize,
                cornerRadius = corner,
                style = Stroke(width = rimWidth)
              )
              drawRoundRect(
                brush = Brush.verticalGradient(
                  colors = listOf(
                    Color(0xFFFFD60A).copy(alpha = 0.30f * p),
                    Color.Transparent,
                    Color(0xFFBF5AF2).copy(alpha = 0.30f * p)
                  ),
                  startY = rr.top,
                  endY = rr.bottom
                ),
                topLeft = topLeft,
                size = lensSize,
                cornerRadius = corner,
                style = Stroke(width = rimWidth)
              )
            }
          }
      )

      // B. Base Row of 4 Equal Tabs — punches out exact Lens RoundRect with BlendMode.Clear so copies NEVER overlap!
      Row(
        modifier = Modifier
          .matchParentSize()
          .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
          .drawWithContent {
            drawContent()
            val rr = computeLensRoundRect(size)
            if (rr.width > 0f && rr.height > 0f) {
              drawRoundRect(
                color = Color.Black,
                topLeft = Offset(rr.left, rr.top),
                size = Size(rr.width, rr.height),
                cornerRadius = CornerRadius(rr.topLeftCornerRadius.x, rr.topLeftCornerRadius.y),
                blendMode = BlendMode.Clear
              )
            }
          },
        verticalAlignment = Alignment.CenterVertically
      ) {
        tabs.forEachIndexed { index, tab ->
          Box(
            modifier = Modifier
              .weight(1f)
              .fillMaxHeight()
              .semantics {
                role = Role.Tab
                selected = (index == selectedIndex)
                contentDescription = tab.label
              }
              .testTag("nav_tab_${tab.label.lowercase()}"),
            contentAlignment = Alignment.Center
          ) {
            LiquidGlassTabContent(
              tab = tab,
              color = baseTabColor
            )
          }
        }
      }

      // C. Magnifier Copy in Accent Blue, strictly clipped to Lens Shape and scaled up around Lens Center
      Row(
        modifier = Modifier
          .matchParentSize()
          .graphicsLayer {
            // Read animated state values here so the outline clip tracks every animation frame
            val unusedX = lensX.value
            val unusedP = pressProgress.value
            val unusedV = velocityX
            if (unusedX.isFinite() && unusedP.isFinite() && unusedV.isFinite()) {
              clip = true
              shape = lensClipShape
            }
          }
          .graphicsLayer {
            val scale = if (skipAnimations) 1.0f else (1.0f + 0.2f * pressProgress.value.coerceIn(0f, 1f))
            if (size.width > 0f && tabWidthPx > 0f) {
              val lensCenterX = lensX.value + tabWidthPx * 0.5f
              transformOrigin = TransformOrigin(
                pivotFractionX = (lensCenterX / size.width).coerceIn(0f, 1f),
                pivotFractionY = 0.5f
              )
            }
            scaleX = scale
            scaleY = scale
          },
        verticalAlignment = Alignment.CenterVertically
      ) {
        tabs.forEach { tab ->
          Box(
            modifier = Modifier
              .weight(1f)
              .fillMaxHeight(),
            contentAlignment = Alignment.Center
          ) {
            LiquidGlassTabContent(
              tab = tab,
              color = accentColor
            )
          }
        }
      }
    }
  }
}

@Composable
private fun LiquidGlassTabContent(
  tab: TabItem,
  color: Color
) {
  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center
  ) {
    Icon(
      imageVector = tab.icon,
      contentDescription = null,
      tint = color,
      modifier = Modifier.size(23.dp)
    )
    Spacer(modifier = Modifier.height(2.dp))
    Text(
      text = tab.label,
      fontSize = 10.sp,
      lineHeight = 12.sp,
      fontWeight = FontWeight.SemiBold,
      color = color,
      maxLines = 1,
      softWrap = false,
      overflow = TextOverflow.Ellipsis
    )
  }
}
