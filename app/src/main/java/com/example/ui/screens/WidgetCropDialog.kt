package com.example.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.widget.PhotoOperationResult
import com.example.widget.WidgetPhotoStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Interactive fixed 2:1 background photo cropper.
 * Drag to move, slider to zoom (100%–300%), photo is always clamped to cover the 2:1 frame.
 * Apply renders an 800×400 JPEG (quality 90) to filesDir/widget/bg_cropped.jpg without touching frame.jpg.
 */
@Composable
fun WidgetCropDialog(
  albumArtBitmap: Bitmap?,
  onDismiss: () -> Unit,
  onCropApplied: () -> Unit,
  onCropReset: () -> Unit,
  onError: (String) -> Unit
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()

  val sourceBitmap by produceState<Bitmap?>(initialValue = null, key1 = albumArtBitmap) {
    value = withContext(Dispatchers.IO) {
      WidgetPhotoStorage.decodeSampledFile(WidgetPhotoStorage.bgOriginalFile(context), 1200)
        ?: WidgetPhotoStorage.decodeSampledFile(WidgetPhotoStorage.frameFile(context), 1200)
        ?: albumArtBitmap
    }
  }

  var zoom by remember { mutableFloatStateOf(1f) }
  var normOffsetX by remember { mutableFloatStateOf(0f) }
  var normOffsetY by remember { mutableFloatStateOf(0f) }
  var viewportSize by remember { mutableStateOf(IntSize(1, 1)) }
  var isApplying by remember { mutableStateOf(false) }

  Dialog(
    onDismissRequest = { if (!isApplying) onDismiss() },
    properties = DialogProperties(usePlatformDefaultWidth = false)
  ) {
    Surface(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 18.dp),
      shape = RoundedCornerShape(26.dp),
      color = Color(0xFF121A2A),
      tonalElevation = 8.dp
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(26.dp))
          .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
      ) {
        Text(
          text = stringResource(R.string.wc_crop_dialog_title),
          color = Color.White,
          fontSize = 18.sp,
          fontWeight = FontWeight.Bold
        )
        Text(
          text = stringResource(R.string.wc_crop_drag_hint),
          color = Color.White.copy(alpha = 0.68f),
          fontSize = 12.5.sp
        )

        // Fixed 2:1 crop viewport
        val boxShape = RoundedCornerShape(18.dp)
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(2f)
            .clip(boxShape)
            .background(Color(0xFF080C14))
            .border(1.5.dp, Color(0xFF43C7F4), boxShape)
            .onSizeChanged { viewportSize = it }
            .pointerInput(sourceBitmap, zoom, viewportSize) {
              detectDragGestures { change, dragAmount ->
                change.consume()
                val bmp = sourceBitmap ?: return@detectDragGestures
                val vw = viewportSize.width.toFloat().coerceAtLeast(1f)
                val vh = viewportSize.height.toFloat().coerceAtLeast(1f)
                val baseScale = max(vw / bmp.width.toFloat(), vh / bmp.height.toFloat())
                val drawW = bmp.width * baseScale * zoom
                val drawH = bmp.height * baseScale * zoom
                val maxPanX = ((drawW - vw) * 0.5f).coerceAtLeast(0f)
                val maxPanY = ((drawH - vh) * 0.5f).coerceAtLeast(0f)
                if (maxPanX > 1f) {
                  normOffsetX = (normOffsetX + dragAmount.x / maxPanX).coerceIn(-1f, 1f)
                }
                if (maxPanY > 1f) {
                  normOffsetY = (normOffsetY + dragAmount.y / maxPanY).coerceIn(-1f, 1f)
                }
              }
            }
            .testTag("widget_crop_viewport"),
          contentAlignment = Alignment.Center
        ) {
          val bmp = sourceBitmap
          if (bmp != null && !bmp.isRecycled) {
            val imageBitmap = remember(bmp) { bmp.asImageBitmap() }
            Canvas(modifier = Modifier.fillMaxSize()) {
              val vw = size.width
              val vh = size.height
              val baseScale = max(vw / bmp.width.toFloat(), vh / bmp.height.toFloat())
              val finalScale = baseScale * zoom
              val drawW = bmp.width * finalScale
              val drawH = bmp.height * finalScale
              val maxPanX = ((drawW - vw) * 0.5f).coerceAtLeast(0f)
              val maxPanY = ((drawH - vh) * 0.5f).coerceAtLeast(0f)
              val left = (vw - drawW) * 0.5f + normOffsetX.coerceIn(-1f, 1f) * maxPanX
              val top = (vh - drawH) * 0.5f + normOffsetY.coerceIn(-1f, 1f) * maxPanY

              drawImage(
                image = imageBitmap,
                dstOffset = IntOffset(left.roundToInt(), top.roundToInt()),
                dstSize = IntSize(drawW.roundToInt().coerceAtLeast(1), drawH.roundToInt().coerceAtLeast(1)),
                filterQuality = com.example.ui.image.AurixImagePipeline.ARTWORK_FILTER_QUALITY
              )

              // Rule-of-thirds guides
              val guideColor = Color.White.copy(alpha = 0.28f)
              drawLine(guideColor, Offset(vw / 3f, 0f), Offset(vw / 3f, vh), strokeWidth = 1f)
              drawLine(guideColor, Offset(vw * 2f / 3f, 0f), Offset(vw * 2f / 3f, vh), strokeWidth = 1f)
              drawLine(guideColor, Offset(0f, vh / 3f), Offset(vw, vh / 3f), strokeWidth = 1f)
              drawLine(guideColor, Offset(0f, vh * 2f / 3f), Offset(vw, vh * 2f / 3f), strokeWidth = 1f)
            }
          } else {
            CircularProgressIndicator(
              color = Color(0xFF43C7F4),
              modifier = Modifier.size(28.dp),
              strokeWidth = 2.5.dp
            )
          }
        }

        // Zoom slider (100% - 300%)
        Column(modifier = Modifier.fillMaxWidth()) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              text = stringResource(R.string.wc_crop_zoom_label),
              color = Color.White.copy(alpha = 0.85f),
              fontSize = 13.sp,
              fontWeight = FontWeight.Medium
            )
            Text(
              text = "${(zoom * 100).roundToInt()}%",
              color = Color(0xFF43C7F4),
              fontSize = 13.sp,
              fontWeight = FontWeight.Bold
            )
          }
          Slider(
            value = zoom,
            onValueChange = { zoom = it.coerceIn(1f, 3f) },
            valueRange = 1f..3f,
            colors = SliderDefaults.colors(
              thumbColor = Color(0xFF43C7F4),
              activeTrackColor = Color(0xFF43C7F4),
              inactiveTrackColor = Color.White.copy(alpha = 0.16f)
            ),
            modifier = Modifier.testTag("widget_crop_zoom_slider")
          )
        }

        // Actions
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          TextButton(
            onClick = {
              if (!isApplying) {
                scope.launch {
                  WidgetPhotoStorage.resetBackgroundCrop(context)
                  onCropReset()
                }
              }
            },
            modifier = Modifier.testTag("widget_crop_reset_btn")
          ) {
            Text(
              text = stringResource(R.string.wc_crop_reset_button),
              color = Color(0xFFF59E0B),
              fontWeight = FontWeight.SemiBold
            )
          }

          Spacer(modifier = Modifier.weight(1f))

          OutlinedButton(
            onClick = { if (!isApplying) onDismiss() },
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.testTag("widget_crop_cancel_btn")
          ) {
            Text(
              text = stringResource(R.string.wc_crop_cancel),
              color = Color.White
            )
          }

          Button(
            onClick = {
              if (isApplying) return@Button
              isApplying = true
              scope.launch {
                val res = WidgetPhotoStorage.applyBackgroundCrop(
                  context = context,
                  normalizedOffsetX = normOffsetX,
                  normalizedOffsetY = normOffsetY,
                  zoom = zoom,
                  fallbackBitmap = albumArtBitmap
                )
                isApplying = false
                when (res) {
                  is PhotoOperationResult.Success -> onCropApplied()
                  is PhotoOperationResult.Error -> onError(res.message)
                }
              }
            },
            enabled = !isApplying && sourceBitmap != null,
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF43C7F4)),
            modifier = Modifier.testTag("widget_crop_apply_btn")
          ) {
            if (isApplying) {
              CircularProgressIndicator(
                color = Color(0xFF080C14),
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp
              )
            } else {
              Text(
                text = stringResource(R.string.wc_crop_apply),
                color = Color(0xFF080C14),
                fontWeight = FontWeight.Bold
              )
            }
          }
        }
      }
    }
  }
}
