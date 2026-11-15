package com.example.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.playback.EqPreset
import com.example.playback.EqualizerUiState
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EqualizerSheet(
  state: EqualizerUiState,
  onToggleEnabled: (Boolean) -> Unit,
  onSelectPreset: (EqPreset) -> Unit,
  onBandLevelChanged: (Int, Int) -> Unit,
  onBassBoostChanged: (Int) -> Unit,
  onReset: () -> Unit,
  onDismiss: () -> Unit
) {
  val surfaceColor = MaterialTheme.colorScheme.surface
  val bgColor = MaterialTheme.colorScheme.background
  val isDark = surfaceColor.luminance() < 0.5f || isSystemInDarkTheme()
  val isAmoled = isDark && bgColor == Color.Black
  val accent = MaterialTheme.colorScheme.primary

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
              accent.copy(alpha = if (state.enabled) (if (isDark) 0.16f else 0.10f) else 0.05f),
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
          .padding(top = 10.dp, bottom = 18.dp)
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

        // Header Row
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
              color = accent.copy(alpha = if (state.enabled) 0.18f else 0.08f),
              border = BorderStroke(
                1.dp,
                if (state.enabled) accent.copy(alpha = 0.55f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
              ),
              modifier = Modifier.size(44.dp)
            ) {
              Box(contentAlignment = Alignment.Center) {
                Icon(
                  imageVector = Icons.Rounded.Equalizer,
                  contentDescription = null,
                  tint = if (state.enabled) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                  modifier = Modifier.size(22.dp)
                )
              }
            }

            Column {
              Text(
                text = stringResource(id = R.string.eq_title),
                style = MaterialTheme.typography.titleMedium.copy(
                  fontSize = 18.sp,
                  lineHeight = 25.sp,
                  fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
              val presetLabel = presetDisplayName(EqPreset.fromId(state.presetId))
              Text(
                text = if (state.enabled) {
                  "${stringResource(id = R.string.eq_on)} · $presetLabel"
                } else {
                  stringResource(id = R.string.eq_off)
                },
                style = MaterialTheme.typography.bodySmall.copy(
                  fontSize = 12.5.sp,
                  lineHeight = 18.sp
                ),
                color = if (state.enabled) accent else MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          }

          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
          ) {
            IconButton(
              onClick = onReset,
              modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("eq_reset_button")
            ) {
              Icon(
                imageVector = Icons.Rounded.RestartAlt,
                contentDescription = stringResource(id = R.string.eq_reset),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
            Switch(
              checked = state.enabled,
              onCheckedChange = onToggleEnabled,
              modifier = Modifier.testTag("eq_enable_switch")
            )
          }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Preset Pills
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          val activePreset = EqPreset.fromId(state.presetId)
          val chips = if (activePreset == EqPreset.CUSTOM) {
            EqPreset.selectablePresets + EqPreset.CUSTOM
          } else {
            EqPreset.selectablePresets
          }
          chips.forEach { preset ->
            val selected = activePreset == preset
            val chipBg by animateColorAsState(
              targetValue = when {
                selected -> accent.copy(alpha = if (isDark) 0.24f else 0.14f)
                isDark -> Color.White.copy(alpha = 0.06f)
                else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
              },
              animationSpec = tween(180),
              label = "eq_chip_bg"
            )
            Surface(
              shape = CircleShape,
              color = chipBg,
              border = BorderStroke(
                1.dp,
                if (selected) accent.copy(alpha = 0.72f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
              ),
              modifier = Modifier
                .clip(CircleShape)
                .clickable(enabled = preset != EqPreset.CUSTOM) { onSelectPreset(preset) }
                .testTag("eq_preset_${preset.id}")
            ) {
              Text(
                text = presetDisplayName(preset),
                style = MaterialTheme.typography.bodySmall.copy(
                  fontSize = 12.5.sp,
                  lineHeight = 18.sp,
                  fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                ),
                color = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Glass 5-Band Spectrum Panel with live Bezier curve + vertical interactive faders
        val glassPanelFill = if (isDark) Color.White.copy(alpha = 0.05f) else Color.Black.copy(alpha = 0.03f)
        val glassPanelBorder = if (isDark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.08f)
        val activeCurveColor = if (state.enabled) accent else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)

        Box(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(glassPanelFill)
            .border(1.dp, glassPanelBorder, RoundedCornerShape(22.dp))
            .padding(horizontal = 12.dp, vertical = 14.dp)
        ) {
          Column(modifier = Modifier.fillMaxWidth()) {
            // Top dB readout row
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              state.bandLevelsMb.forEach { levelMb ->
                val db = (levelMb / 100f)
                val formatted = when {
                  db > 0.05f -> "+${db.roundToInt()}dB"
                  db < -0.05f -> "${db.roundToInt()}dB"
                  else -> "0dB"
                }
                Text(
                  text = formatted,
                  style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    fontWeight = FontWeight.Bold
                  ),
                  color = if (state.enabled) activeCurveColor else MaterialTheme.colorScheme.onSurfaceVariant,
                  textAlign = TextAlign.Center,
                  modifier = Modifier.weight(1f)
                )
              }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Vertical Sliders + Smooth Frequency Response Curve behind them
            Box(
              modifier = Modifier
                .fillMaxWidth()
                .height(172.dp)
            ) {
              val minMb = state.minLevelMb
              val maxMb = state.maxLevelMb.coerceAtLeast(minMb + 100)
              val normalizedLevels = remember(state.bandLevelsMb, minMb, maxMb) {
                state.bandLevelsMb.map { lvl ->
                  ((lvl - minMb).toFloat() / (maxMb - minMb).toFloat()).coerceIn(0f, 1f)
                }
              }

              Canvas(modifier = Modifier.fillMaxSize()) {
                // Center 0dB reference hairline
                val midY = size.height * 0.5f
                drawLine(
                  color = activeCurveColor.copy(alpha = 0.18f),
                  start = Offset(0f, midY),
                  end = Offset(size.width, midY),
                  strokeWidth = 1.dp.toPx()
                )

                if (normalizedLevels.size >= 2) {
                  val count = normalizedLevels.size
                  val points = normalizedLevels.mapIndexed { idx, norm ->
                    val x = size.width * ((idx + 0.5f) / count.toFloat())
                    val y = size.height * (1f - norm)
                    Offset(x, y)
                  }

                  val linePath = Path().apply {
                    moveTo(points.first().x, points.first().y)
                    for (i in 0 until points.lastIndex) {
                      val p0 = points[i]
                      val p1 = points[i + 1]
                      val midX = (p0.x + p1.x) * 0.5f
                      cubicTo(midX, p0.y, midX, p1.y, p1.x, p1.y)
                    }
                  }

                  val fillPath = Path().apply {
                    addPath(linePath)
                    lineTo(points.last().x, size.height)
                    lineTo(points.first().x, size.height)
                    close()
                  }

                  drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                      colors = listOf(
                        activeCurveColor.copy(alpha = if (state.enabled) 0.24f else 0.08f),
                        Color.Transparent
                      )
                    )
                  )
                  drawPath(
                    path = linePath,
                    color = activeCurveColor.copy(alpha = if (state.enabled) 0.78f else 0.35f),
                    style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                  )
                }
              }

              Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceEvenly
              ) {
                state.bandLevelsMb.forEachIndexed { idx, levelMb ->
                  VerticalEqBandSlider(
                    valueMb = levelMb,
                    minMb = minMb,
                    maxMb = maxMb,
                    enabled = state.enabled,
                    accent = accent,
                    isDark = isDark,
                    testTag = "eq_band_$idx",
                    modifier = Modifier
                      .weight(1f)
                      .fillMaxHeight(),
                    onValueChange = { nextMb ->
                      onBandLevelChanged(idx, nextMb)
                    }
                  )
                }
              }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Bottom frequency labels
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              state.bandFrequenciesHz.forEach { hz ->
                Text(
                  text = formatBandFrequency(hz),
                  style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    fontWeight = FontWeight.Medium
                  ),
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  textAlign = TextAlign.Center,
                  modifier = Modifier.weight(1f)
                )
              }
            }
          }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Bass Boost Slider row
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
              imageVector = Icons.Rounded.GraphicEq,
              contentDescription = null,
              tint = if (state.enabled && state.bassBoostStrength > 0) accent else MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.size(18.dp)
            )
            Text(
              text = stringResource(id = R.string.eq_bass_boost),
              style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 14.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.SemiBold
              ),
              color = MaterialTheme.colorScheme.onSurface
            )
          }
          Text(
            text = "${(state.bassBoostStrength / 10f).roundToInt()}%",
            style = MaterialTheme.typography.bodySmall.copy(
              fontSize = 13.sp,
              lineHeight = 18.sp,
              fontWeight = FontWeight.Bold
            ),
            color = if (state.enabled && state.bassBoostStrength > 0) accent else MaterialTheme.colorScheme.onSurfaceVariant
          )
        }

        Slider(
          value = state.bassBoostStrength.toFloat(),
          onValueChange = { onBassBoostChanged(it.roundToInt()) },
          valueRange = 0f..1000f,
          colors = SliderDefaults.colors(
            thumbColor = if (state.enabled) accent else MaterialTheme.colorScheme.onSurfaceVariant,
            activeTrackColor = if (state.enabled) accent else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
          ),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("eq_bass_boost_slider")
        )

        if (!state.hardwareSupported && state.audioSessionId > 0) {
          Text(
            text = stringResource(id = R.string.eq_hardware_note),
            style = MaterialTheme.typography.labelSmall.copy(lineHeight = 16.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
              .fillMaxWidth()
              .padding(top = 4.dp)
          )
        }
      }
    }
  }
}

@Composable
private fun VerticalEqBandSlider(
  valueMb: Int,
  minMb: Int,
  maxMb: Int,
  enabled: Boolean,
  accent: Color,
  isDark: Boolean,
  testTag: String,
  modifier: Modifier = Modifier,
  onValueChange: (Int) -> Unit
) {
  var boxHeightPx by remember { mutableFloatStateOf(1f) }
  val range = (maxMb - minMb).coerceAtLeast(1)
  val fraction = ((valueMb - minMb).toFloat() / range.toFloat()).coerceIn(0f, 1f)
  val latestOnChange by rememberUpdatedState(onValueChange)

  fun mapYToMb(yPx: Float): Int {
    val paddingPx = 12f
    val usable = (boxHeightPx - paddingPx * 2f).coerceAtLeast(1f)
    val clamped = (yPx - paddingPx).coerceIn(0f, usable)
    val frac = 1f - (clamped / usable)
    return (minMb + frac * range).roundToInt().coerceIn(minMb, maxMb)
  }

  Box(
    modifier = modifier
      .minimumInteractiveComponentSize()
      .onSizeChanged { boxHeightPx = it.height.toFloat().coerceAtLeast(1f) }
      .pointerInput(minMb, maxMb) {
        detectTapGestures { offset ->
          latestOnChange(mapYToMb(offset.y))
        }
      }
      .pointerInput(minMb, maxMb) {
        detectVerticalDragGestures { change, _ ->
          change.consume()
          latestOnChange(mapYToMb(change.position.y))
        }
      }
      .testTag(testTag),
    contentAlignment = Alignment.Center
  ) {
    val trackColor = if (isDark) Color.White.copy(alpha = 0.14f) else Color.Black.copy(alpha = 0.10f)
    val activeColor = if (enabled) accent else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)

    Canvas(modifier = Modifier.fillMaxSize()) {
      val pad = 10.dp.toPx()
      val centerX = size.width * 0.5f
      val topY = pad
      val bottomY = (size.height - pad).coerceAtLeast(topY + 1f)
      val thumbY = bottomY - fraction * (bottomY - topY)
      val midY = (topY + bottomY) * 0.5f

      // Background track
      drawLine(
        color = trackColor,
        start = Offset(centerX, topY),
        end = Offset(centerX, bottomY),
        strokeWidth = 4.dp.toPx(),
        cap = StrokeCap.Round
      )

      // Active fill from 0dB center toward thumb
      drawLine(
        color = activeColor.copy(alpha = if (enabled) 0.85f else 0.45f),
        start = Offset(centerX, midY),
        end = Offset(centerX, thumbY),
        strokeWidth = 4.dp.toPx(),
        cap = StrokeCap.Round
      )

      // Soft outer halo on thumb
      if (enabled) {
        drawCircle(
          color = activeColor.copy(alpha = 0.25f),
          radius = 12.dp.toPx(),
          center = Offset(centerX, thumbY)
        )
      }

      // Crisp glass thumb
      drawCircle(
        color = Color.White,
        radius = 7.dp.toPx(),
        center = Offset(centerX, thumbY)
      )
      drawCircle(
        color = activeColor,
        radius = 4.dp.toPx(),
        center = Offset(centerX, thumbY)
      )
    }
  }
}

@Composable
fun presetDisplayName(preset: EqPreset): String {
  val resId = when (preset) {
    EqPreset.FLAT -> R.string.eq_preset_flat
    EqPreset.BASS_BOOST -> R.string.eq_preset_bass_boost
    EqPreset.VOCAL -> R.string.eq_preset_vocal
    EqPreset.POP -> R.string.eq_preset_pop
    EqPreset.ROCK -> R.string.eq_preset_rock
    EqPreset.CLUB -> R.string.eq_preset_club
    EqPreset.CUSTOM -> R.string.eq_preset_custom
  }
  return stringResource(id = resId)
}

private fun formatBandFrequency(hz: Int): String {
  return if (hz >= 1000) {
    val k = hz / 1000f
    if (k % 1f < 0.05f) "${k.toInt()}k" else String.format("%.1fk", k)
  } else {
    "${hz}Hz"
  }
}
