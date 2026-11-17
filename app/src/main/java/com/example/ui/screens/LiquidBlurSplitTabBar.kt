package com.example.ui.screens

import android.os.Build
import android.provider.Settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
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
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

enum class AurixNavStyle(val id: String) {
  CLASSIC("classic"),
  BLUR("blur");

  companion object {
    fun fromId(id: String?): AurixNavStyle =
      entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: CLASSIC
  }
}

/**
 * Floating iOS-inspired "Liquid Blur" split navigation bar:
 * - Left frosted blur capsule holds primary destinations (Home, Library, Settings)
 *   with a glowing sliding orb indicator and real API 31+ backdrop blur.
 * - Right detached circular frosted Search orb floats beside the main capsule.
 *
 * Expects standard 4-tab order [Home, Search, Library, Settings]; falls back cleanly
 * if fewer than 2 tabs are passed.
 */
@Composable
fun LiquidBlurSplitTabBar(
  tabs: List<TabItem>,
  selectedIndex: Int,
  onSelect: (Int) -> Unit,
  modifier: Modifier = Modifier,
  reducedMotion: Boolean = false,
  ambientColors: Pair<Color, Color>? = null,
  darkThemeOverride: Boolean? = null
) {
  if (tabs.isEmpty()) return

  val searchSourceIdx = if (tabs.size >= 4) 1 else (tabs.size - 1).coerceAtLeast(0)
  val pillIndices = remember(tabs.size, searchSourceIdx) {
    if (tabs.size >= 2) tabs.indices.filter { it != searchSourceIdx } else tabs.indices.toList()
  }
  val searchTab = if (tabs.size >= 2) tabs[searchSourceIdx] else null
  val isSearchSelected = searchTab != null && selectedIndex == searchSourceIdx
  val activePillPos = pillIndices.indexOf(selectedIndex).takeIf { it >= 0 }

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

  val darkTheme = darkThemeOverride ?: (
    MaterialTheme.colorScheme.background.luminance() < 0.5f ||
      (isSystemInDarkTheme() && MaterialTheme.colorScheme.surface.luminance() < 0.5f)
    )
  val accentColor = MaterialTheme.colorScheme.primary

  val barHeight = 64.dp
  val orbButtonSize = 64.dp
  val glowStart = ambientColors?.first ?: accentColor
  val glowEnd = ambientColors?.second ?: if (darkTheme) Color(0xFF7C3AED) else Color(0xFFF59E0B)

  val pillFill = if (darkTheme) Color(0xFF171E2E).copy(alpha = 0.72f) else Color(0xFFFFFBF4).copy(alpha = 0.76f)
  val topSpecHighlight = Color.White.copy(alpha = if (darkTheme) 0.52f else 0.96f)
  val rimBaseColor = Color.White.copy(alpha = if (darkTheme) 0.24f else 0.85f)
  val inactiveIconColor = if (darkTheme) Color(0xFFE7ECF8).copy(alpha = 0.88f) else Color(0xFF1F2937).copy(alpha = 0.86f)

  val borderBrush = remember(ambientColors, glowStart, glowEnd, rimBaseColor, darkTheme) {
    if (ambientColors != null) {
      Brush.horizontalGradient(
        colors = listOf(
          glowStart.copy(alpha = if (darkTheme) 0.44f else 0.36f),
          rimBaseColor,
          glowEnd.copy(alpha = if (darkTheme) 0.38f else 0.34f)
        )
      )
    } else {
      Brush.horizontalGradient(listOf(rimBaseColor, topSpecHighlight, rimBaseColor))
    }
  }

  // Sliding orb state for main pill
  val pillTabCount = pillIndices.size.coerceAtLeast(1)
  var innerWidthPx by remember { mutableFloatStateOf(0f) }
  val slotWidthPx = if (innerWidthPx > 0f) innerWidthPx / pillTabCount else 0f
  val orbX = remember { Animatable(0f) }
  var lastPillPos by remember { mutableIntStateOf(activePillPos ?: 0) }
  if (activePillPos != null) {
    lastPillPos = activePillPos
  }
  var isDragging by remember { mutableStateOf(false) }

  val orbSpring = remember(skipAnimations) {
    if (skipAnimations) snap<Float>() else spring(dampingRatio = 0.80f, stiffness = 300f)
  }
  val dragSpring = remember(skipAnimations) {
    if (skipAnimations) snap<Float>() else spring(dampingRatio = 0.86f, stiffness = 420f)
  }

  LaunchedEffect(activePillPos, slotWidthPx) {
    if (slotWidthPx > 0f && !isDragging) {
      val target = (activePillPos ?: lastPillPos).coerceIn(0, pillTabCount - 1) * slotWidthPx
      if (abs(orbX.value - target) > 0.5f) {
        orbX.animateTo(target, orbSpring)
      } else {
        orbX.snapTo(target)
      }
    }
  }

  val pillOrbAlpha by animateFloatAsState(
    targetValue = if (activePillPos != null) 1f else 0f,
    animationSpec = if (skipAnimations) snap() else tween(200),
    label = "blur_pill_orb_alpha"
  )

  val pressScale = remember { Animatable(1f) }
  val currentSelected by rememberUpdatedState(selectedIndex)
  val currentOnSelect by rememberUpdatedState(onSelect)

  Row(
    modifier = modifier
      .padding(horizontal = 20.dp)
      .widthIn(max = 368.dp)
      .fillMaxWidth()
      .height(barHeight),
    horizontalArrangement = Arrangement.spacedBy(10.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    // 1. Left Frosted Liquid Blur Capsule (Home, Library, Settings)
    Box(
      modifier = Modifier
        .weight(1f)
        .fillMaxHeight()
    ) {
      // Soft drop shadow
      Box(
        modifier = Modifier
          .matchParentSize()
          .drawBehind {
            val r = size.height * 0.5f
            drawRoundRect(
              brush = Brush.verticalGradient(
                colors = listOf(
                  Color.Black.copy(alpha = if (darkTheme) 0.28f else 0.08f),
                  Color.Black.copy(alpha = if (darkTheme) 0.46f else 0.16f)
                )
              ),
              topLeft = Offset(0f, 6.dp.toPx()),
              size = size,
              cornerRadius = CornerRadius(r, r)
            )
          }
      )

      // Blurred ambient frosted glass backdrop
      Box(
        modifier = Modifier
          .matchParentSize()
          .clip(CircleShape)
      ) {
        Box(
          modifier = Modifier
            .matchParentSize()
            .then(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Modifier.blur(26.dp) else Modifier)
            .background(
              Brush.horizontalGradient(
                colors = if (darkTheme) {
                  listOf(
                    glowStart.copy(alpha = 0.28f),
                    Color(0xFF28344B).copy(alpha = 0.42f),
                    glowEnd.copy(alpha = 0.24f)
                  )
                } else {
                  listOf(
                    glowStart.copy(alpha = 0.22f),
                    Color(0xFFFFF4E3).copy(alpha = 0.62f),
                    glowEnd.copy(alpha = 0.24f)
                  )
                }
              )
            )
        )

        Box(
          modifier = Modifier
            .matchParentSize()
            .background(pillFill)
            .border(1.dp, borderBrush, CircleShape)
            .drawBehind {
              val stroke15 = 1.5.dp.toPx()
              val inset = stroke15 * 0.5f
              val r = (size.height - stroke15) * 0.5f
              drawRoundRect(
                brush = Brush.verticalGradient(
                  colors = listOf(
                    topSpecHighlight,
                    topSpecHighlight.copy(alpha = 0.12f),
                    Color.Transparent
                  ),
                  startY = 0f,
                  endY = size.height * 0.48f
                ),
                topLeft = Offset(inset, inset),
                size = Size(size.width - stroke15, size.height - stroke15),
                cornerRadius = CornerRadius(r, r),
                style = Stroke(width = stroke15)
              )
            }
        )
      }

      // Interactive Slot Container
      Box(
        modifier = Modifier
          .matchParentSize()
          .padding(5.dp)
          .onSizeChanged { innerWidthPx = it.width.toFloat() }
          .pointerInput(pillIndices, slotWidthPx, skipAnimations) {
            if (slotWidthPx <= 0f) return@pointerInput
            val maxOrbX = (pillTabCount - 1) * slotWidthPx
            val touchSlopSq = viewConfiguration.touchSlop * viewConfiguration.touchSlop

            coroutineScope {
              while (true) {
                awaitPointerEventScope {
                  val down = awaitPointerEvent().changes.firstOrNull { it.pressed } ?: return@awaitPointerEventScope
                  down.consume()
                  isDragging = true
                  val startPos = down.position
                  var lastPos = down.position
                  var didDrag = false
                  var activeSlot = pillIndices.indexOf(currentSelected).takeIf { it >= 0 } ?: -1

                  launch {
                    if (!skipAnimations) pressScale.animateTo(1.06f, dragSpring)
                  }

                  while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: event.changes.firstOrNull()
                    if (change == null || !change.pressed) {
                      val chosenSlot = if (!didDrag) {
                        (lastPos.x / slotWidthPx).toInt().coerceIn(0, pillTabCount - 1)
                      } else {
                        (orbX.value / slotWidthPx).roundToInt().coerceIn(0, pillTabCount - 1)
                      }
                      val targetOrigIndex = pillIndices[chosenSlot]
                      if (targetOrigIndex != currentSelected) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        currentOnSelect(targetOrigIndex)
                      }
                      isDragging = false
                      launch { orbX.animateTo(chosenSlot * slotWidthPx, orbSpring) }
                      launch { pressScale.animateTo(1f, orbSpring) }
                      break
                    }

                    change.consume()
                    lastPos = change.position
                    val dx = change.position.x - startPos.x
                    val dy = change.position.y - startPos.y
                    if (!didDrag && (dx * dx + dy * dy) > touchSlopSq) {
                      didDrag = true
                    }

                    if (didDrag || change.positionChange() != Offset.Zero) {
                      val rawX = change.position.x - slotWidthPx * 0.5f
                      val clampedX = when {
                        rawX < 0f -> rawX * 0.22f
                        rawX > maxOrbX -> maxOrbX + (rawX - maxOrbX) * 0.22f
                        else -> rawX
                      }
                      launch { orbX.animateTo(clampedX, dragSpring) }
                      val liveSlot = (clampedX / slotWidthPx).roundToInt().coerceIn(0, pillTabCount - 1)
                      if (liveSlot != activeSlot) {
                        activeSlot = liveSlot
                        val liveOrig = pillIndices[liveSlot]
                        if (liveOrig != currentSelected) {
                          haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                          currentOnSelect(liveOrig)
                        }
                      }
                    }
                  }
                }
              }
            }
          }
      ) {
        // Active glowing frosted orb indicator behind the selected main tab
        Box(
          modifier = Modifier
            .matchParentSize()
            .drawBehind {
              if (slotWidthPx <= 0f || pillOrbAlpha <= 0.01f) return@drawBehind
              val orbPad = 2.dp.toPx()
              val orbW = (slotWidthPx - orbPad * 2f).coerceAtLeast(size.height)
              val orbH = size.height
              val centerX = orbX.value + slotWidthPx * 0.5f
              val scaledW = orbW * pressScale.value
              val scaledH = orbH * (1f + (pressScale.value - 1f) * 0.5f)
              val left = centerX - scaledW * 0.5f
              val top = (size.height - scaledH) * 0.5f
              val corner = CornerRadius(scaledH * 0.5f, scaledH * 0.5f)

              // Soft radial glow like iOS Liquid Glass pill
              drawRoundRect(
                brush = Brush.radialGradient(
                  colors = listOf(
                    accentColor.copy(alpha = (if (darkTheme) 0.34f else 0.26f) * pillOrbAlpha),
                    glowEnd.copy(alpha = (if (darkTheme) 0.14f else 0.14f) * pillOrbAlpha),
                    Color.White.copy(alpha = (if (darkTheme) 0.10f else 0.42f) * pillOrbAlpha)
                  ),
                  center = Offset(centerX, size.height * 0.5f),
                  radius = scaledW * 0.72f
                ),
                topLeft = Offset(left, top),
                size = Size(scaledW, scaledH),
                cornerRadius = corner
              )

              // Frosted specular rim on active orb
              drawRoundRect(
                brush = Brush.verticalGradient(
                  colors = listOf(
                    Color.White.copy(alpha = (if (darkTheme) 0.38f else 0.90f) * pillOrbAlpha),
                    accentColor.copy(alpha = 0.18f * pillOrbAlpha)
                  )
                ),
                topLeft = Offset(left, top),
                size = Size(scaledW, scaledH),
                cornerRadius = corner,
                style = Stroke(width = 1.1.dp.toPx())
              )
            }
        )

        // Row of main capsule tabs
        Row(
          modifier = Modifier.matchParentSize(),
          verticalAlignment = Alignment.CenterVertically
        ) {
          pillIndices.forEach { origIdx ->
            val tab = tabs[origIdx]
            val isSelected = origIdx == selectedIndex
            val iconTint by animateColorAsState(
              targetValue = if (isSelected) accentColor else inactiveIconColor,
              animationSpec = if (skipAnimations) snap() else tween(200),
              label = "blur_tab_tint_$origIdx"
            )
            val iconScale by animateFloatAsState(
              targetValue = if (isSelected) 1.08f else 1.0f,
              animationSpec = if (skipAnimations) snap() else spring(dampingRatio = 0.75f, stiffness = 360f),
              label = "blur_tab_scale_$origIdx"
            )

            Box(
              modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                .semantics {
                  role = Role.Tab
                  selected = isSelected
                  contentDescription = tab.label
                }
                .testTag("nav_tab_${tab.label.lowercase()}"),
              contentAlignment = Alignment.Center
            ) {
              Icon(
                imageVector = tab.icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier
                  .size(24.dp)
                  .graphicsLayer {
                    scaleX = iconScale
                    scaleY = iconScale
                  }
              )
            }
          }
        }
      }
    }

    // 2. Right Detached Circular Frosted Search Orb
    if (searchTab != null) {
      val searchScale by animateFloatAsState(
        targetValue = if (isSearchSelected) 1.05f else 1.0f,
        animationSpec = if (skipAnimations) snap() else spring(dampingRatio = 0.72f, stiffness = 340f),
        label = "blur_search_orb_scale"
      )
      val searchTint by animateColorAsState(
        targetValue = if (isSearchSelected) accentColor else inactiveIconColor,
        animationSpec = if (skipAnimations) snap() else tween(200),
        label = "blur_search_orb_tint"
      )
      val interactionSource = remember { MutableInteractionSource() }

      Box(
        modifier = Modifier
          .size(orbButtonSize)
          .graphicsLayer {
            scaleX = searchScale
            scaleY = searchScale
          }
          .drawBehind {
            val r = size.height * 0.5f
            drawCircle(
              brush = Brush.verticalGradient(
                colors = listOf(
                  Color.Black.copy(alpha = if (darkTheme) 0.28f else 0.08f),
                  Color.Black.copy(alpha = if (darkTheme) 0.48f else 0.16f)
                )
              ),
              radius = r,
              center = Offset(size.width * 0.5f, size.height * 0.5f + 6.dp.toPx())
            )
          }
          .clip(CircleShape)
          .clickable(
            interactionSource = interactionSource,
            indication = ripple(bounded = true, color = accentColor)
          ) {
            if (selectedIndex != searchSourceIdx) {
              haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
              onSelect(searchSourceIdx)
            }
          }
          .semantics {
            role = Role.Tab
            selected = isSearchSelected
            contentDescription = searchTab.label
          }
          .testTag("nav_tab_${searchTab.label.lowercase()}"),
        contentAlignment = Alignment.Center
      ) {
        // Blurred orb backing
        Box(
          modifier = Modifier
            .matchParentSize()
            .then(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Modifier.blur(26.dp) else Modifier)
            .background(
              Brush.radialGradient(
                colors = if (isSearchSelected) {
                  listOf(
                    accentColor.copy(alpha = if (darkTheme) 0.42f else 0.30f),
                    glowEnd.copy(alpha = if (darkTheme) 0.26f else 0.22f)
                  )
                } else if (darkTheme) {
                  listOf(
                    glowEnd.copy(alpha = 0.26f),
                    Color(0xFF1F293D).copy(alpha = 0.55f)
                  )
                } else {
                  listOf(
                    glowStart.copy(alpha = 0.20f),
                    Color(0xFFFFF4E3).copy(alpha = 0.65f)
                  )
                }
              )
            )
        )

        val orbBorder = if (isSearchSelected) {
          Brush.verticalGradient(
            listOf(
              Color.White.copy(alpha = if (darkTheme) 0.55f else 0.95f),
              accentColor.copy(alpha = 0.55f)
            )
          )
        } else {
          borderBrush
        }

        Box(
          modifier = Modifier
            .matchParentSize()
            .background(pillFill)
            .border(if (isSearchSelected) 1.4.dp else 1.dp, orbBorder, CircleShape)
            .drawBehind {
              val stroke15 = 1.5.dp.toPx()
              drawCircle(
                brush = Brush.verticalGradient(
                  colors = listOf(
                    topSpecHighlight,
                    topSpecHighlight.copy(alpha = 0.14f),
                    Color.Transparent
                  ),
                  startY = 0f,
                  endY = size.height * 0.52f
                ),
                radius = (size.minDimension - stroke15) * 0.5f,
                style = Stroke(width = stroke15)
              )
            }
        )

        Icon(
          imageVector = searchTab.icon,
          contentDescription = null,
          tint = searchTint,
          modifier = Modifier.size(25.dp)
        )
      }
    }
  }
}
