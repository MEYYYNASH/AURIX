package com.example.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.playback.CrossfadeController
import com.example.playback.CrossfadeSettings
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private val CROSSFADE_PRESETS = listOf(0, 2, 4, 6, 8, 12)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CrossfadeSheet(
  settings: CrossfadeSettings,
  reducedMotion: Boolean = false,
  onDurationChanged: (Int) -> Unit,
  onManualSkipChanged: (Boolean) -> Unit,
  onDismiss: () -> Unit
) {
  val surfaceColor = MaterialTheme.colorScheme.surface
  val bgColor = MaterialTheme.colorScheme.background
  val isDark = surfaceColor.luminance() < 0.5f || isSystemInDarkTheme()
  val isAmoled = isDark && bgColor == Color.Black
  val accent = MaterialTheme.colorScheme.primary
  val secondaryAccent = MaterialTheme.colorScheme.secondary
  val haptics = LocalHapticFeedback.current

  val isActive = settings.durationSeconds > 0
  val animatedOverlapFraction by animateFloatAsState(
    targetValue = if (isActive) {
      (settings.durationSeconds / CrossfadeController.MAX_SECONDS.toFloat()).coerceIn(0.18f, 1f)
    } else {
      0f
    },
    animationSpec = if (reducedMotion) snap() else tween(220),
    label = "crossfade_overlap"
  )

  val sheetFill = when {
    isAmoled -> Color(0xFF0A0E17).copy(alpha = 0.96f)
    isDark -> surfaceColor.copy(alpha = 0.94f)
    else -> surfaceColor.copy(alpha = 0.97f)
  }
  val topBorderBrush = Brush.verticalGradient(
    colors = if (isDark) {
      listOf(Color.White.copy(alpha = 0.24f), Color.White.copy(alpha = 0.04f))
    } else {
      listOf(Color.White.copy(alpha = 0.95f), Color.Black.copy(alpha = 0.06f))
    }
  )
  val sheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    shape = sheetShape,
    containerColor = Color.Transparent,
    contentColor = MaterialTheme.colorScheme.onSurface,
    scrimColor = Color.Black.copy(alpha = 0.52f),
    tonalElevation = 0.dp,
    dragHandle = null
  ) {
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .clip(sheetShape)
        .background(sheetFill)
        .background(
          Brush.radialGradient(
            colors = listOf(
              accent.copy(alpha = if (isActive) (if (isDark) 0.16f else 0.10f) else 0.05f),
              Color.Transparent
            ),
            center = Offset(300f, 120f),
            radius = 700f
          )
        )
        .border(1.dp, topBorderBrush, sheetShape)
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .verticalScroll(rememberScrollState())
          .navigationBarsPadding()
          .padding(horizontal = 20.dp)
          .padding(top = 10.dp, bottom = 20.dp)
      ) {
        // Drag handle
        Box(
          modifier = Modifier
            .align(Alignment.CenterHorizontally)
            .padding(bottom = 14.dp)
            .width(38.dp)
            .height(4.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f))
        )

        // Header row
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f)
          ) {
            Surface(
              shape = RoundedCornerShape(14.dp),
              color = accent.copy(alpha = if (isActive) 0.18f else 0.08f),
              border = BorderStroke(
                1.dp,
                if (isActive) accent.copy(alpha = 0.55f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
              ),
              modifier = Modifier.size(44.dp)
            ) {
              Box(contentAlignment = Alignment.Center) {
                Icon(
                  imageVector = Icons.Rounded.GraphicEq,
                  contentDescription = null,
                  tint = if (isActive) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                  modifier = Modifier.size(22.dp)
                )
              }
            }

            Column {
              Text(
                text = stringResource(id = R.string.crossfade_title),
                style = MaterialTheme.typography.titleMedium.copy(
                  fontSize = 18.sp,
                  lineHeight = 25.sp,
                  fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
              val statusText = if (isActive) {
                stringResource(id = R.string.crossfade_seconds_active, settings.durationSeconds)
              } else {
                stringResource(id = R.string.crossfade_off)
              }
              Text(
                text = statusText,
                style = MaterialTheme.typography.bodySmall.copy(
                  fontSize = 12.5.sp,
                  lineHeight = 18.sp
                ),
                color = if (isActive) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
            }
          }

          IconButton(
            onClick = {
              haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
              onDurationChanged(0)
            },
            enabled = isActive,
            modifier = Modifier
              .minimumInteractiveComponentSize()
              .testTag("crossfade_reset_button")
          ) {
            Icon(
              imageVector = Icons.Rounded.RestartAlt,
              contentDescription = stringResource(id = R.string.eq_reset),
              tint = if (isActive) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
            )
          }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
          text = stringResource(id = R.string.crossfade_subtitle),
          style = MaterialTheme.typography.bodySmall.copy(
            fontSize = 13.sp,
            lineHeight = 19.sp
          ),
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Live Equal-Power Curve Visualizer Panel
        val glassPanelFill = if (isDark) Color.White.copy(alpha = 0.05f) else Color.Black.copy(alpha = 0.03f)
        val glassPanelBorder = if (isDark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.08f)
        val mutedCurveColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)

        Box(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(glassPanelFill)
            .border(1.dp, glassPanelBorder, RoundedCornerShape(22.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
          Column(modifier = Modifier.fillMaxWidth()) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
              ) {
                Box(
                  modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (isActive) secondaryAccent else mutedCurveColor)
                )
                Text(
                  text = stringResource(id = R.string.crossfade_track_out),
                  style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.5.sp,
                    lineHeight = 16.sp,
                    fontWeight = FontWeight.SemiBold
                  ),
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }

              Surface(
                shape = CircleShape,
                color = if (isActive) accent.copy(alpha = 0.18f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
                border = BorderStroke(
                  1.dp,
                  if (isActive) accent.copy(alpha = 0.50f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                )
              ) {
                Text(
                  text = if (isActive) {
                    stringResource(id = R.string.crossfade_seconds_short, settings.durationSeconds)
                  } else {
                    stringResource(id = R.string.crossfade_off)
                  },
                  style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    fontWeight = FontWeight.Bold
                  ),
                  color = if (isActive) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                  modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                )
              }

              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
              ) {
                Text(
                  text = stringResource(id = R.string.crossfade_track_in),
                  style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.5.sp,
                    lineHeight = 16.sp,
                    fontWeight = FontWeight.SemiBold
                  ),
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Box(
                  modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (isActive) accent else mutedCurveColor)
                )
              }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Canvas(
              modifier = Modifier
                .fillMaxWidth()
                .height(88.dp)
            ) {
              val w = size.width
              val h = size.height
              val padY = 8.dp.toPx()
              val usableH = (h - padY * 2f).coerceAtLeast(1f)
              val topY = padY
              val bottomY = h - padY

              // Baseline hairline
              drawLine(
                color = mutedCurveColor.copy(alpha = 0.28f),
                start = Offset(0f, bottomY),
                end = Offset(w, bottomY),
                strokeWidth = 1.dp.toPx()
              )

              val dashEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f)
              if (animatedOverlapFraction <= 0.01f) {
                // Sharp sequential cut at midpoint when Off
                val midX = w * 0.5f
                drawLine(
                  color = mutedCurveColor,
                  start = Offset(0f, topY),
                  end = Offset(midX, topY),
                  strokeWidth = 2.dp.toPx(),
                  cap = StrokeCap.Round
                )
                drawLine(
                  color = mutedCurveColor,
                  start = Offset(midX, topY),
                  end = Offset(midX, bottomY),
                  strokeWidth = 1.5.dp.toPx(),
                  pathEffect = dashEffect
                )
                drawLine(
                  color = accent.copy(alpha = 0.65f),
                  start = Offset(midX, bottomY),
                  end = Offset(midX, topY),
                  strokeWidth = 1.5.dp.toPx(),
                  pathEffect = dashEffect
                )
                drawLine(
                  color = accent.copy(alpha = 0.65f),
                  start = Offset(midX, topY),
                  end = Offset(w, topY),
                  strokeWidth = 2.dp.toPx(),
                  cap = StrokeCap.Round
                )
                return@Canvas
              }

              val overlapWidth = w * (0.28f + 0.56f * animatedOverlapFraction)
              val overlapStart = (w - overlapWidth) * 0.5f
              val overlapEnd = overlapStart + overlapWidth

              // Subtle overlap region highlight
              drawRect(
                brush = Brush.horizontalGradient(
                  colors = listOf(
                    Color.Transparent,
                    accent.copy(alpha = 0.12f),
                    Color.Transparent
                  ),
                  startX = overlapStart,
                  endX = overlapEnd
                ),
                topLeft = Offset(overlapStart, topY),
                size = androidx.compose.ui.geometry.Size(overlapWidth, usableH)
              )

              val steps = 40
              val outPath = Path().apply {
                moveTo(0f, topY)
                lineTo(overlapStart, topY)
                for (i in 0..steps) {
                  val t = i / steps.toFloat()
                  val x = overlapStart + overlapWidth * t
                  val gain = cos(t * (PI / 2.0)).toFloat()
                  val y = bottomY - usableH * gain
                  lineTo(x, y)
                }
              }

              val outFillPath = Path().apply {
                addPath(outPath)
                lineTo(overlapEnd, bottomY)
                lineTo(0f, bottomY)
                close()
              }

              val inPath = Path().apply {
                for (i in 0..steps) {
                  val t = i / steps.toFloat()
                  val x = overlapStart + overlapWidth * t
                  val gain = sin(t * (PI / 2.0)).toFloat()
                  val y = bottomY - usableH * gain
                  if (i == 0) moveTo(x, y) else lineTo(x, y)
                }
                lineTo(w, topY)
              }

              val inFillPath = Path().apply {
                addPath(inPath)
                lineTo(w, bottomY)
                lineTo(overlapStart, bottomY)
                close()
              }

              drawPath(
                path = outFillPath,
                brush = Brush.verticalGradient(
                  colors = listOf(secondaryAccent.copy(alpha = 0.22f), Color.Transparent),
                  startY = topY,
                  endY = bottomY
                )
              )
              drawPath(
                path = inFillPath,
                brush = Brush.verticalGradient(
                  colors = listOf(accent.copy(alpha = 0.24f), Color.Transparent),
                  startY = topY,
                  endY = bottomY
                )
              )
              drawPath(
                path = outPath,
                color = secondaryAccent.copy(alpha = 0.90f),
                style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
              )
              drawPath(
                path = inPath,
                color = accent,
                style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
              )

              // Equal-power intersection dot at midpoint (0.707 gain)
              val midGain = 0.7071f
              val crossPoint = Offset(w * 0.5f, bottomY - usableH * midGain)
              drawCircle(
                color = accent.copy(alpha = 0.28f),
                radius = 7.dp.toPx(),
                center = crossPoint
              )
              drawCircle(
                color = Color.White,
                radius = 3.dp.toPx(),
                center = crossPoint
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Slider Row
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Icon(
              imageVector = Icons.Rounded.Tune,
              contentDescription = null,
              tint = if (isActive) accent else MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.size(18.dp)
            )
            Text(
              text = stringResource(id = R.string.crossfade_duration_label),
              style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 14.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.SemiBold
              ),
              color = MaterialTheme.colorScheme.onSurface
            )
          }
          Text(
            text = if (isActive) {
              stringResource(id = R.string.crossfade_seconds_short, settings.durationSeconds)
            } else {
              stringResource(id = R.string.crossfade_off)
            },
            style = MaterialTheme.typography.bodySmall.copy(
              fontSize = 13.sp,
              lineHeight = 18.sp,
              fontWeight = FontWeight.Bold
            ),
            color = if (isActive) accent else MaterialTheme.colorScheme.onSurfaceVariant
          )
        }

        Slider(
          value = settings.durationSeconds.toFloat(),
          onValueChange = { raw ->
            val next = raw.roundToInt().coerceIn(CrossfadeController.MIN_SECONDS, CrossfadeController.MAX_SECONDS)
            if (next != settings.durationSeconds) {
              haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
              onDurationChanged(next)
            }
          },
          valueRange = 0f..CrossfadeController.MAX_SECONDS.toFloat(),
          steps = CrossfadeController.MAX_SECONDS - 1,
          colors = SliderDefaults.colors(
            thumbColor = accent,
            activeTrackColor = accent,
            inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f)
          ),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("crossfade_slider")
        )

        Spacer(modifier = Modifier.height(6.dp))

        // Quick preset chips
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          CROSSFADE_PRESETS.forEach { sec ->
            val selected = settings.durationSeconds == sec
            val chipBg by animateColorAsState(
              targetValue = when {
                selected -> accent.copy(alpha = if (isDark) 0.24f else 0.14f)
                isDark -> Color.White.copy(alpha = 0.06f)
                else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
              },
              animationSpec = if (reducedMotion) snap() else tween(180),
              label = "crossfade_chip_bg"
            )
            Surface(
              shape = CircleShape,
              color = chipBg,
              border = BorderStroke(
                1.dp,
                if (selected) accent.copy(alpha = 0.72f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
              ),
              modifier = Modifier
                .defaultMinSize(minHeight = 36.dp)
                .clip(CircleShape)
                .clickable {
                  haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                  onDurationChanged(sec)
                }
                .testTag("crossfade_preset_$sec")
            ) {
              Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.padding(horizontal = 15.dp, vertical = 8.dp)
              ) {
                Text(
                  text = if (sec == 0) {
                    stringResource(id = R.string.crossfade_off)
                  } else {
                    stringResource(id = R.string.crossfade_seconds_short, sec)
                  },
                  style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 12.5.sp,
                    lineHeight = 18.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                  ),
                  color = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
            }
          }
        }

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
        Spacer(modifier = Modifier.height(12.dp))

        // Smooth skip fade-in toggle
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .clickable { onManualSkipChanged(!settings.fadeOnManualSkip) }
            .padding(vertical = 4.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
              text = stringResource(id = R.string.crossfade_manual_skip_title),
              style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 14.5.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.SemiBold
              ),
              color = MaterialTheme.colorScheme.onSurface
            )
            Text(
              text = stringResource(id = R.string.crossfade_manual_skip_subtitle),
              style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 12.5.sp,
                lineHeight = 18.sp
              ),
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
          Switch(
            checked = settings.fadeOnManualSkip,
            onCheckedChange = onManualSkipChanged,
            modifier = Modifier.testTag("crossfade_manual_skip_switch")
          )
        }
      }
    }
  }
}
