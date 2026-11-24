package com.example.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.provider.Settings
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.example.R
import com.example.model.APP_ICON_ARTWORK
import com.example.widget.WidgetBgType
import com.example.widget.WidgetBitmapBuilder
import com.example.widget.WidgetContrastReport
import com.example.widget.WidgetCustomizerConfig
import com.example.widget.WidgetFrameMotion
import com.example.widget.WidgetFrameStyle
import com.example.widget.WidgetSizeTier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

fun isSystemRemoveAnimationsEnabled(context: Context): Boolean {
  val scale = runCatching {
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
  }.getOrDefault(1f)
  return scale <= 0.01f
}

@Composable
fun rememberSongAlbumArtBitmap(artworkUrl: String?): Bitmap? {
  val context = LocalContext.current
  val bitmapState = produceState<Bitmap?>(initialValue = null, key1 = artworkUrl) {
    if (artworkUrl.isNullOrBlank() || artworkUrl == APP_ICON_ARTWORK) {
      value = null
      return@produceState
    }
    value = withContext(Dispatchers.IO) {
      runCatching {
        val req = ImageRequest.Builder(context)
          .data(artworkUrl)
          .allowHardware(false)
          .size(480)
          .build()
        val result = context.imageLoader.execute(req)
        (result as? SuccessResult)?.drawable?.toBitmap(config = Bitmap.Config.ARGB_8888)
      }.getOrNull()
    }
  }
  return bitmapState.value
}

@Composable
fun LiveWidgetPreviewCard(
  config: WidgetCustomizerConfig,
  sizeTier: WidgetSizeTier,
  albumArtBitmap: Bitmap?,
  songTitle: String,
  songArtist: String,
  currentLyricLine: String,
  nextLyricLine: String,
  hasLyrics: Boolean,
  reducedMotion: Boolean,
  modifier: Modifier = Modifier,
  onContrastReport: ((WidgetContrastReport) -> Unit)? = null
) {
  val context = LocalContext.current
  val systemNoAnim = remember(context) { isSystemRemoveAnimationsEnabled(context) }
  val motionDisabled = reducedMotion || systemNoAnim
  val norm = remember(config) { config.normalized() }

  // Build static background (Photo / Sunset / Ocean / Solid) + compute WCAG contrast report
  val bgAndContrast by produceState<Pair<Bitmap?, WidgetContrastReport>>(
    initialValue = null to WidgetContrastReport(0.08f, 12f, false),
    key1 = norm.cacheKey(),
    key2 = albumArtBitmap,
    key3 = sizeTier
  ) {
    value = withContext(Dispatchers.Default) {
      val (w, h) = when (sizeTier) {
        WidgetSizeTier.SMALL -> 360 to 360
        WidgetSizeTier.WIDE -> 640 to 320
        WidgetSizeTier.LARGE -> 640 to 480
      }
      val (bmp, report) = WidgetBitmapBuilder.buildBackgroundOnlyBitmap(
        context = context,
        config = norm,
        widthPx = w,
        heightPx = h,
        albumArtBitmap = albumArtBitmap,
        auroraPhase = 0f
      )
      bmp to report
    }
  }

  LaunchedEffect(bgAndContrast.second) {
    onContrastReport?.invoke(bgAndContrast.second)
  }

  // Standalone styled frame bitmap for smooth in-app Float / Spin / Pulse animation
  val frameBitmap by produceState<Bitmap?>(
    initialValue = null,
    key1 = norm.frameStyle,
    key2 = norm.frameSource,
    key3 = "${norm.frameColorHex}|${norm.cornerRadius}|${norm.borderWidth}|${norm.photoRevision}|${albumArtBitmap?.hashCode()}"
  ) {
    if (norm.frameStyle == WidgetFrameStyle.NONE || sizeTier == WidgetSizeTier.SMALL) {
      value = null
      return@produceState
    }
    value = withContext(Dispatchers.Default) {
      val src = WidgetBitmapBuilder.resolveFramePhotoBitmap(context, norm, albumArtBitmap)
      WidgetBitmapBuilder.buildStandaloneFrameBitmap(src, norm, sizePx = 280)
    }
  }

  val infinite = rememberInfiniteTransition(label = "widget_preview_motion")
  val auroraPhase by infinite.animateFloat(
    initialValue = 0f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = 9000, easing = LinearEasing),
      repeatMode = RepeatMode.Restart
    ),
    label = "aurora_phase"
  )
  val floatOffsetY by infinite.animateFloat(
    initialValue = -5f,
    targetValue = 5f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = 2400, easing = FastOutSlowInEasing),
      repeatMode = RepeatMode.Reverse
    ),
    label = "frame_float"
  )
  val spinAngle by infinite.animateFloat(
    initialValue = 0f,
    targetValue = 360f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = 8000, easing = LinearEasing),
      repeatMode = RepeatMode.Restart
    ),
    label = "frame_spin"
  )
  val pulseScale by infinite.animateFloat(
    initialValue = 0.94f,
    targetValue = 1.05f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = 1700, easing = FastOutSlowInEasing),
      repeatMode = RepeatMode.Reverse
    ),
    label = "frame_pulse"
  )

  val aspect = when (sizeTier) {
    WidgetSizeTier.SMALL -> 1f
    WidgetSizeTier.WIDE -> 2f
    WidgetSizeTier.LARGE -> 1.38f
  }
  val widthModifier = when (sizeTier) {
    WidgetSizeTier.SMALL -> modifier.widthIn(max = 188.dp).fillMaxWidth()
    else -> modifier.fillMaxWidth()
  }

  val shape = RoundedCornerShape(24.dp)
  val showSideFrame = norm.frameStyle != WidgetFrameStyle.NONE && sizeTier != WidgetSizeTier.SMALL
  val previewTitle = songTitle.ifBlank { "Midnight Horizon" }
  val previewArtist = songArtist.ifBlank { "AURIX" }
  val sampleLine1 = stringResource(R.string.settings_lyrics_anim_sample_line_1)
  val sampleLine2 = stringResource(R.string.settings_lyrics_anim_sample_line_2)
  val showLyricsContent = hasLyrics || songTitle.isBlank()
  val displayCurrent = currentLyricLine.ifBlank { sampleLine1 }
  val displayNext = nextLyricLine.ifBlank { sampleLine2 }

  Box(
    modifier = widthModifier
      .aspectRatio(aspect)
      .clip(shape)
      .border(1.dp, Color.White.copy(alpha = 0.16f), shape)
      .background(Color(0xFF0E1624))
  ) {
    // 1. Background layer (live animated Aurora in app preview, Canvas bitmap for others)
    if (norm.bgType == WidgetBgType.AURORA) {
      val activePhase = if (motionDisabled) 0f else auroraPhase
      LiveAuroraBackgroundCanvas(
        phase = activePhase,
        darknessPercent = norm.darkness,
        modifier = Modifier.fillMaxSize()
      )
    } else {
      val bgBmp = bgAndContrast.first
      if (bgBmp != null && !bgBmp.isRecycled) {
        Image(
          bitmap = bgBmp.asImageBitmap(),
          contentDescription = null,
          contentScale = ContentScale.Crop,
          modifier = Modifier.fillMaxSize()
        )
      }
    }

    // 2. Foreground content matching home screen widget layout
    Row(
      modifier = Modifier
        .fillMaxSize()
        .padding(
          horizontal = if (sizeTier == WidgetSizeTier.SMALL) 12.dp else 14.dp,
          vertical = if (sizeTier == WidgetSizeTier.SMALL) 11.dp else 12.dp
        ),
      verticalAlignment = Alignment.CenterVertically
    ) {
      if (showSideFrame && frameBitmap != null) {
        val frameDim = if (sizeTier == WidgetSizeTier.LARGE) 108.dp else 94.dp
        val effectiveMotion = if (motionDisabled) WidgetFrameMotion.STILL else norm.motion
        Box(
          modifier = Modifier
            .size(frameDim)
            .graphicsLayer {
              when (effectiveMotion) {
                WidgetFrameMotion.STILL -> Unit
                WidgetFrameMotion.FLOAT -> translationY = floatOffsetY
                WidgetFrameMotion.SPIN -> rotationZ = spinAngle
                WidgetFrameMotion.PULSE -> {
                  scaleX = pulseScale
                  scaleY = pulseScale
                }
              }
            },
          contentAlignment = Alignment.Center
        ) {
          Image(
            bitmap = frameBitmap!!.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize()
          )
        }
        Spacer(modifier = Modifier.width(12.dp))
      }

      Column(
        modifier = Modifier
          .weight(1f)
          .fillMaxHeight(),
        verticalArrangement = Arrangement.SpaceBetween
      ) {
        // Top pill row
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          if (showLyricsContent) {
            Row(
              modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(Color.White.copy(alpha = 0.16f))
                .border(0.75.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(50))
                .padding(horizontal = 8.dp, vertical = 3.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
              Icon(
                imageVector = Icons.Rounded.GraphicEq,
                contentDescription = null,
                tint = Color(0xFF43C7F4),
                modifier = Modifier.size(11.dp)
              )
              Text(
                text = stringResource(R.string.widget_lyrics_tag),
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
              )
            }
          } else {
            Spacer(modifier = Modifier.width(1.dp))
          }
        }

        // Center lyric or title-only stage
        if (!showLyricsContent) {
          Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.Center
          ) {
            Text(
              text = previewTitle,
              color = Color.White,
              fontSize = if (sizeTier == WidgetSizeTier.SMALL) 14.sp else 16.sp,
              fontWeight = FontWeight.Bold,
              maxLines = 2,
              overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
              text = previewArtist,
              color = Color.White.copy(alpha = 0.72f),
              fontSize = 12.sp,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
          }
        } else {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Box(
              modifier = Modifier
                .width(3.dp)
                .height(if (sizeTier == WidgetSizeTier.SMALL) 24.dp else 32.dp)
                .clip(RoundedCornerShape(50))
                .background(
                  Brush.verticalGradient(listOf(Color(0xFF43C7F4), Color(0xFF7965FF)))
                )
            )
            Spacer(modifier = Modifier.width(9.dp))
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = displayCurrent,
                color = Color.White,
                fontSize = when (sizeTier) {
                  WidgetSizeTier.SMALL -> 13.5.sp
                  WidgetSizeTier.WIDE -> 15.5.sp
                  WidgetSizeTier.LARGE -> 17.5.sp
                },
                fontWeight = FontWeight.Bold,
                lineHeight = when (sizeTier) {
                  WidgetSizeTier.SMALL -> 18.sp
                  WidgetSizeTier.WIDE -> 21.sp
                  WidgetSizeTier.LARGE -> 23.sp
                },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
              )
              if (displayNext.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                  text = displayNext,
                  color = Color.White.copy(alpha = 0.66f),
                  fontSize = if (sizeTier == WidgetSizeTier.SMALL) 10.5.sp else 12.sp,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis
                )
              }
            }
          }
        }

        // Bottom song footer (hidden when song has no lyrics since title+artist is centered)
        if (showLyricsContent) {
          Text(
            text = "$previewTitle · $previewArtist",
            color = Color.White.copy(alpha = 0.78f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        } else {
          Spacer(modifier = Modifier.height(2.dp))
        }
      }
    }
  }
}

@Composable
private fun LiveAuroraBackgroundCanvas(
  phase: Float,
  darknessPercent: Int,
  modifier: Modifier = Modifier
) {
  val normDark = darknessPercent.coerceIn(0, 100) / 100f
  Canvas(modifier = modifier) {
    val w = size.width
    val h = size.height
    drawRect(Color(0xFF0A1022))

    val angle = phase * (2.0 * PI)
    val dx1 = (cos(angle) * w * 0.16).toFloat()
    val dy1 = (sin(angle) * h * 0.18).toFloat()
    val dx2 = (sin(angle + 1.4) * w * 0.18).toFloat()
    val dy2 = (cos(angle + 0.9) * h * 0.16).toFloat()

    drawRect(
      brush = Brush.radialGradient(
        colors = listOf(Color(0xD843C7F4), Color(0x807965FF), Color.Transparent),
        center = Offset(w * 0.24f + dx1, h * 0.28f + dy1),
        radius = w * 0.68f
      )
    )
    drawRect(
      brush = Brush.radialGradient(
        colors = listOf(Color(0xCCA855F7), Color(0x70EC4899), Color.Transparent),
        center = Offset(w * 0.78f + dx2, h * 0.76f + dy2),
        radius = w * 0.65f
      )
    )
    drawRect(
      brush = Brush.radialGradient(
        colors = listOf(Color(0x6622D3EE), Color.Transparent),
        center = Offset(w * 0.52f - dx1 * 0.5f, h * 0.18f + dy2 * 0.6f),
        radius = w * 0.52f
      )
    )
    if (normDark > 0.001f) {
      drawRect(
        brush = Brush.verticalGradient(
          colors = listOf(
            Color(0xFF070B14).copy(alpha = (normDark * 0.61f).coerceIn(0f, 0.88f)),
            Color(0xFF080C16).copy(alpha = (normDark * 0.73f).coerceIn(0f, 0.92f)),
            Color(0xFF050810).copy(alpha = (normDark * 0.83f).coerceIn(0f, 0.96f))
          )
        )
      )
    }
  }
}

/**
 * Compact 2:1 live widget preview displayed inside Settings > Widgets.
 */
@Composable
fun SettingsWidgetPreviewThumb(
  config: WidgetCustomizerConfig,
  artworkUrl: String?,
  songTitle: String,
  songArtist: String,
  reducedMotion: Boolean,
  modifier: Modifier = Modifier
) {
  val albumArt = rememberSongAlbumArtBitmap(artworkUrl)
  LiveWidgetPreviewCard(
    config = config,
    sizeTier = WidgetSizeTier.WIDE,
    albumArtBitmap = albumArt,
    songTitle = songTitle,
    songArtist = songArtist,
    currentLyricLine = stringResource(R.string.settings_lyrics_anim_sample_line_1),
    nextLyricLine = stringResource(R.string.settings_lyrics_anim_sample_line_2),
    hasLyrics = true,
    reducedMotion = reducedMotion,
    modifier = modifier
  )
}

/**
 * Compact animated framed photo displayed on the in-app Lyrics screen so the user's
 * chosen Frame Style & Motion (Float / Spin / Pulse) also shine during playback.
 */
@Composable
fun LyricsScreenFramedArtwork(
  config: WidgetCustomizerConfig,
  artworkUrl: String?,
  isPlaying: Boolean,
  reducedMotion: Boolean,
  size: Dp = 56.dp,
  modifier: Modifier = Modifier
) {
  if (config.frameStyle == WidgetFrameStyle.NONE) return
  val context = LocalContext.current
  val systemNoAnim = remember(context) { isSystemRemoveAnimationsEnabled(context) }
  val albumArt = rememberSongAlbumArtBitmap(artworkUrl)
  val norm = remember(config) { config.normalized() }

  val frameBitmap by produceState<Bitmap?>(
    initialValue = null,
    key1 = norm.cacheKey(),
    key2 = albumArt
  ) {
    value = withContext(Dispatchers.Default) {
      val src = WidgetBitmapBuilder.resolveFramePhotoBitmap(context, norm, albumArt)
      WidgetBitmapBuilder.buildStandaloneFrameBitmap(src, norm, sizePx = 200)
    }
  }

  val allowMotion = isPlaying && !reducedMotion && !systemNoAnim && norm.motion != WidgetFrameMotion.STILL
  val infinite = rememberInfiniteTransition(label = "lyrics_screen_frame_motion")
  val floatY by infinite.animateFloat(
    initialValue = -3.5f,
    targetValue = 3.5f,
    animationSpec = infiniteRepeatable(
      animation = tween(2300, easing = FastOutSlowInEasing),
      repeatMode = RepeatMode.Reverse
    ),
    label = "ls_float"
  )
  val spinDeg by infinite.animateFloat(
    initialValue = 0f,
    targetValue = 360f,
    animationSpec = infiniteRepeatable(
      animation = tween(9000, easing = LinearEasing),
      repeatMode = RepeatMode.Restart
    ),
    label = "ls_spin"
  )
  val pulse by infinite.animateFloat(
    initialValue = 0.94f,
    targetValue = 1.06f,
    animationSpec = infiniteRepeatable(
      animation = tween(1600, easing = FastOutSlowInEasing),
      repeatMode = RepeatMode.Reverse
    ),
    label = "ls_pulse"
  )

  val bmp = frameBitmap ?: return
  Box(
    modifier = modifier
      .size(size)
      .graphicsLayer {
        if (allowMotion) {
          when (norm.motion) {
            WidgetFrameMotion.STILL -> Unit
            WidgetFrameMotion.FLOAT -> translationY = floatY
            WidgetFrameMotion.SPIN -> rotationZ = spinDeg
            WidgetFrameMotion.PULSE -> {
              scaleX = pulse
              scaleY = pulse
            }
          }
        }
      },
    contentAlignment = Alignment.Center
  ) {
    Image(
      bitmap = bmp.asImageBitmap(),
      contentDescription = null,
      contentScale = ContentScale.Fit,
      modifier = Modifier.fillMaxSize()
    )
  }
}
