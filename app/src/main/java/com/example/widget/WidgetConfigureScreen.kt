package com.example.widget

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class PreviewArtBundle(
  val blurred26: ImageBitmap,
  val blurredStrong: ImageBitmap,
  val blurredLightFrost: ImageBitmap,
  val roundedCover: ImageBitmap,
  val warmRoundedCover: ImageBitmap,
  val orbDisc: ImageBitmap,
  val tiltedCover: ImageBitmap,
  val leftFadeCover: ImageBitmap,
  val posterCover: ImageBitmap,
  val posterPanel: ImageBitmap,
  val segmentedProgress: ImageBitmap,
  val waveformProgress: ImageBitmap,
  val eq3White: ImageBitmap,
  val eq3Dark: ImageBitmap,
  val eq4White: ImageBitmap,
  val eq4Dark: ImageBitmap,
  val eq3Ink: ImageBitmap,
  val eq4Ink: ImageBitmap,
  val vinylPalette: VinylCoverPalette,
  val luminance: Float
)

@Composable
fun WidgetConfigureScreen(
  kind: WidgetKind,
  selectedStyle: WidgetGlassStyle,
  snapshot: WidgetSnapshotState,
  rawArtwork: Bitmap?,
  isKhmer: Boolean,
  isSaving: Boolean,
  onSelectStyle: (WidgetGlassStyle) -> Unit,
  onApplyToThisWidget: () -> Unit,
  onApplyToAllWidgets: () -> Unit,
  onCancel: () -> Unit
) {
  BackHandler(enabled = !isSaving, onBack = onCancel)

  val context = LocalContext.current
  val progressFraction = if (snapshot.songId.isNotBlank() && snapshot.durationMs > 0L) {
    (snapshot.positionMs.toFloat() / snapshot.durationMs.toFloat()).coerceIn(0.05f, 1f)
  } else {
    0.44f
  }

  val previewBundle by produceState<PreviewArtBundle?>(
    initialValue = null,
    key1 = rawArtwork,
    key2 = snapshot.songId,
    key3 = progressFraction
  ) {
    value = withContext(Dispatchers.Default) {
      val coverSet = WidgetGlassRenderer.getOrCreateBlurredCoverSet(
        context = context,
        coverKey = "cfg|${snapshot.songId}|${snapshot.artworkUrl}",
        rawArt = rawArtwork
      )
      PreviewArtBundle(
        blurred26 = coverSet.blurred26.asImageBitmap(),
        blurredStrong = coverSet.blurredStrong.asImageBitmap(),
        blurredLightFrost = coverSet.blurredLightFrost.asImageBitmap(),
        roundedCover = WidgetGlassRenderer.renderRoundedCoverBitmap(
          context = context,
          rawArt = rawArtwork,
          sizePx = 192,
          cornerRadiusFraction = 20f / 66f,
          warmRim = false
        ).asImageBitmap(),
        warmRoundedCover = WidgetGlassRenderer.renderRoundedCoverBitmap(
          context = context,
          rawArt = rawArtwork,
          sizePx = 180,
          cornerRadiusFraction = 0.28f,
          warmRim = true
        ).asImageBitmap(),
        orbDisc = WidgetGlassRenderer.renderOrbDiscBitmap(
          context = context,
          rawArt = rawArtwork,
          sizePx = 200,
          rotationDegrees = 18f
        ).asImageBitmap(),
        tiltedCover = WidgetGlassRenderer.renderTiltedArtBitmap(
          context = context,
          rawArt = rawArtwork,
          artSizePx = 156,
          tiltDegrees = 6f
        ).asImageBitmap(),
        leftFadeCover = WidgetGlassRenderer.renderLeftEdgeFadeCoverBitmap(
          context = context,
          rawArt = rawArtwork,
          widthPx = 160,
          heightPx = 220,
          isRtl = false
        ).asImageBitmap(),
        posterCover = WidgetGlassRenderer.renderPosterCoverWithBottomGradient(
          context = context,
          rawArt = rawArtwork,
          widthPx = 360,
          heightPx = 190
        ).asImageBitmap(),
        posterPanel = WidgetGlassRenderer.renderPosterFloatingGlassPanelBitmap(
          blurredStrong = coverSet.blurredStrong,
          widthPx = 340,
          heightPx = 96,
          luminance = coverSet.luminance
        ).asImageBitmap(),
        segmentedProgress = WidgetGlassRenderer.renderSegmentedProgressBitmap(
          progressFraction = progressFraction
        ).asImageBitmap(),
        waveformProgress = WidgetGlassRenderer.renderWaveformProgressBitmap(
          progressFraction = progressFraction
        ).asImageBitmap(),
        eq3White = WidgetGlassRenderer.renderEqualizerBitmap(
          barCount = 3,
          color = 0xFFFFFFFF.toInt(),
          isPlaying = true,
          phase = 1
        ).asImageBitmap(),
        eq3Dark = WidgetGlassRenderer.renderEqualizerBitmap(
          barCount = 3,
          color = 0xFF14172A.toInt(),
          isPlaying = true,
          phase = 1
        ).asImageBitmap(),
        eq4White = WidgetGlassRenderer.renderEqualizerBitmap(
          barCount = 4,
          color = 0xFFFFFFFF.toInt(),
          isPlaying = true,
          phase = 2
        ).asImageBitmap(),
        eq4Dark = WidgetGlassRenderer.renderEqualizerBitmap(
          barCount = 4,
          color = 0xFF14172A.toInt(),
          isPlaying = true,
          phase = 2
        ).asImageBitmap(),
        eq3Ink = WidgetGlassRenderer.renderEqualizerBitmap(
          barCount = 3,
          color = VinylWidgetRenderer.INK_COLOR,
          isPlaying = true,
          phase = 1
        ).asImageBitmap(),
        eq4Ink = WidgetGlassRenderer.renderEqualizerBitmap(
          barCount = 4,
          color = VinylWidgetRenderer.INK_COLOR,
          isPlaying = true,
          phase = 2
        ).asImageBitmap(),
        vinylPalette = VinylWidgetRenderer.extractCoverPalette(
          rawArt = rawArtwork,
          seedKey = "${snapshot.songId}|${snapshot.title}|${snapshot.artist}"
        ),
        luminance = coverSet.luminance
      )
    }
  }

  val displayTitle = snapshot.title.takeIf { it.isNotBlank() }
    ?: stringResource(R.string.widget_preview_title)
  val displayArtist = snapshot.artist.takeIf { it.isNotBlank() }
    ?: stringResource(R.string.widget_preview_artist)
  val displayCurrentLyric = snapshot.currentLyricLine.takeIf { snapshot.hasLyrics && it.isNotBlank() }
    ?: stringResource(R.string.widget_preview_lyric_current)
  val displayNextLyric = snapshot.nextLyricLine.takeIf { snapshot.hasLyrics && it.isNotBlank() }
    ?: stringResource(R.string.widget_preview_lyric_next)
  val isPlaying = snapshot.isPlaying
  val isLiked = snapshot.isFavorite
  val kindLabel = if (isKhmer) kind.labelKm else kind.labelEn

  Scaffold(
    containerColor = Color(0xFF070A12),
    contentWindowInsets = WindowInsets.safeDrawing,
    bottomBar = {
      Surface(
        color = Color(0xEB0B101C),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 14.dp),
          horizontalAlignment = Alignment.CenterHorizontally
        ) {
          Column(
            modifier = Modifier.widthIn(max = 560.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            Button(
              onClick = onApplyToThisWidget,
              enabled = !isSaving,
              shape = RoundedCornerShape(18.dp),
              colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF7EF0C5),
                contentColor = Color(0xFF061510),
                disabledContainerColor = Color(0xFF7EF0C5).copy(alpha = 0.45f),
                disabledContentColor = Color(0xFF061510)
              ),
              modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("cfg_apply_widget_btn")
            ) {
              if (isSaving) {
                CircularProgressIndicator(
                  strokeWidth = 2.dp,
                  color = Color(0xFF061510),
                  modifier = Modifier.size(20.dp)
                )
              } else {
                Text(
                  text = stringResource(R.string.widget_cfg_apply_widget),
                  style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                )
              }
            }

            OutlinedButton(
              onClick = onApplyToAllWidgets,
              enabled = !isSaving,
              shape = RoundedCornerShape(18.dp),
              border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
              colors = ButtonDefaults.outlinedButtonColors(
                contentColor = Color.White.copy(alpha = 0.92f)
              ),
              modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("cfg_apply_all_btn")
            ) {
              Text(
                text = stringResource(R.string.widget_cfg_apply_all),
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
              )
            }
          }
        }
      }
    }
  ) { innerPadding ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(
          Brush.verticalGradient(
            colors = listOf(
              Color(0xFF0C1222),
              Color(0xFF070A12),
              Color(0xFF090D16)
            )
          )
        )
        .padding(innerPadding),
      contentAlignment = Alignment.TopCenter
    ) {
      Column(
        modifier = Modifier
          .fillMaxSize()
          .widthIn(max = 600.dp)
      ) {
        // Top Header
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 12.dp, top = 16.dp, bottom = 8.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              Surface(
                shape = CircleShape,
                color = Color(0xFF4FC3FF).copy(alpha = 0.14f),
                border = BorderStroke(1.dp, Color(0xFF4FC3FF).copy(alpha = 0.35f))
              ) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                  Image(
                    painter = painterResource(id = R.drawable.ic_widget_orbi),
                    contentDescription = null,
                    modifier = Modifier.size(14.dp)
                  )
                  Spacer(modifier = Modifier.width(6.dp))
                  Text(
                    text = kindLabel,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = Color(0xFF8BE2FF)
                  )
                }
              }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
              text = stringResource(R.string.widget_cfg_title),
              style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
              color = Color.White,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
            Text(
              text = stringResource(R.string.widget_cfg_subtitle),
              style = MaterialTheme.typography.bodySmall,
              color = Color.White.copy(alpha = 0.65f),
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
          }

          IconButton(
            onClick = onCancel,
            enabled = !isSaving,
            modifier = Modifier
              .minimumInteractiveComponentSize()
              .clip(CircleShape)
              .background(Color.White.copy(alpha = 0.08f))
              .testTag("cfg_close_btn")
          ) {
            Icon(
              imageVector = Icons.Rounded.Close,
              contentDescription = stringResource(R.string.widget_cfg_close_cd),
              tint = Color.White
            )
          }
        }

        Text(
          text = stringResource(R.string.widget_cfg_preview_section),
          style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
          color = Color.White.copy(alpha = 0.56f),
          modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
        )

        LazyColumn(
          modifier = Modifier
            .weight(1f)
            .fillMaxWidth(),
          contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 20.dp),
          verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
          items(WidgetGlassStyle.entries, key = { it.id }) { style ->
            val isSelected = style == selectedStyle
            StyleOptionCard(
              kind = kind,
              style = style,
              isSelected = isSelected,
              isKhmer = isKhmer,
              bundle = previewBundle,
              title = displayTitle,
              artist = displayArtist,
              currentLyric = displayCurrentLyric,
              nextLyric = displayNextLyric,
              progressFraction = progressFraction,
              positionMs = snapshot.positionMs,
              durationMs = snapshot.durationMs,
              reducedMotion = snapshot.reducedMotion,
              isPlaying = isPlaying,
              isLiked = isLiked,
              onSelect = { onSelectStyle(style) }
            )
          }
        }
      }
    }
  }
}

@Composable
private fun StyleOptionCard(
  kind: WidgetKind,
  style: WidgetGlassStyle,
  isSelected: Boolean,
  isKhmer: Boolean,
  bundle: PreviewArtBundle?,
  title: String,
  artist: String,
  currentLyric: String,
  nextLyric: String,
  progressFraction: Float,
  positionMs: Long,
  durationMs: Long,
  reducedMotion: Boolean,
  isPlaying: Boolean,
  isLiked: Boolean,
  onSelect: () -> Unit
) {
  val borderColor by animateColorAsState(
    targetValue = if (isSelected) Color(0xFF7EF0C5) else Color.White.copy(alpha = 0.12f),
    animationSpec = tween(220),
    label = "cfgCardBorder"
  )
  val cardBg by animateColorAsState(
    targetValue = if (isSelected) Color(0xFF131D2D) else Color(0xFF0E1420),
    animationSpec = tween(220),
    label = "cfgCardBg"
  )

  val styleName = if (isKhmer) style.displayNameKm else style.displayNameEn
  val styleSubtitle = if (isKhmer) style.subtitleKm else style.subtitleEn

  Surface(
    shape = RoundedCornerShape(24.dp),
    color = cardBg,
    border = BorderStroke(if (isSelected) 2.dp else 1.dp, borderColor),
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(24.dp))
      .clickable(onClick = onSelect)
      .testTag("cfg_style_card_${style.id}")
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Surface(
              shape = CircleShape,
              color = if (isSelected) Color(0xFF7EF0C5).copy(alpha = 0.18f) else Color.White.copy(alpha = 0.08f)
            ) {
              Text(
                text = stringResource(R.string.widget_cfg_style_badge, style.styleNumber),
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = if (isSelected) Color(0xFF7EF0C5) else Color.White.copy(alpha = 0.72f),
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
              )
            }
            Text(
              text = styleName,
              style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
              color = Color.White,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
          }
          Spacer(modifier = Modifier.height(2.dp))
          Text(
            text = styleSubtitle,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.62f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Box(
          modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(if (isSelected) Color(0xFF7EF0C5) else Color.White.copy(alpha = 0.08f))
            .border(
              width = 1.dp,
              color = if (isSelected) Color(0xFF7EF0C5) else Color.White.copy(alpha = 0.20f),
              shape = CircleShape
            ),
          contentAlignment = Alignment.Center
        ) {
          if (isSelected) {
            Icon(
              imageVector = Icons.Rounded.Check,
              contentDescription = null,
              tint = Color(0xFF061510),
              modifier = Modifier.size(16.dp)
            )
          }
        }
      }

      // Live Home-Screen Widget Preview Container
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(18.dp))
          .background(
            Brush.linearGradient(
              colors = listOf(
                Color(0xFF1A2236),
                Color(0xFF101522),
                Color(0xFF1A152C)
              )
            )
          )
          .padding(12.dp),
        contentAlignment = Alignment.Center
      ) {
        when (kind) {
          WidgetKind.MUSIC -> LiveMusicWidgetPreview(
            style = style,
            bundle = bundle,
            title = title,
            artist = artist,
            progressFraction = progressFraction,
            positionMs = positionMs,
            durationMs = durationMs,
            reducedMotion = reducedMotion,
            isPlaying = isPlaying,
            isLiked = isLiked
          )
          WidgetKind.LYRICS -> LiveLyricsWidgetPreview(
            style = style,
            bundle = bundle,
            title = title,
            artist = artist,
            currentLyric = currentLyric,
            nextLyric = nextLyric,
            positionMs = positionMs,
            reducedMotion = reducedMotion,
            isPlaying = isPlaying
          )
          WidgetKind.COVER -> LiveCoverWidgetPreview(
            style = style,
            bundle = bundle,
            positionMs = positionMs,
            reducedMotion = reducedMotion,
            isPlaying = isPlaying
          )
          WidgetKind.MINI -> LiveMiniWidgetPreview(
            style = style,
            bundle = bundle,
            title = title,
            artist = artist,
            positionMs = positionMs,
            reducedMotion = reducedMotion,
            isPlaying = isPlaying
          )
        }
      }
    }
  }
}

@Composable
private fun rememberVinylSpinAngle(
  positionMs: Long,
  isPlaying: Boolean,
  reducedMotion: Boolean
): Float {
  val baseAngle = VinylWidgetRenderer.computeVinylAngleDegrees(
    positionMs = positionMs,
    isPlaying = isPlaying,
    reducedMotion = reducedMotion
  )
  if (!isPlaying || reducedMotion) return baseAngle
  val infinite = rememberInfiniteTransition(label = "vinyl_spin")
  val sweep by infinite.animateFloat(
    initialValue = 0f,
    targetValue = 360f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = VinylWidgetRenderer.TURN_DURATION_MS.toInt(), easing = LinearEasing),
      repeatMode = RepeatMode.Restart
    ),
    label = "vinyl_rotation_deg"
  )
  return (baseAngle + sweep) % 360f
}

@Composable
private fun VinylDiscCanvas(
  diameter: Dp,
  palette: VinylCoverPalette,
  rotationDegrees: Float,
  modifier: Modifier = Modifier
) {
  Canvas(modifier = modifier.size(diameter)) {
    val d = size.minDimension
    val r = d * 0.5f
    val center = Offset(r, r)

    // Soft outer drop shadow (static)
    drawCircle(
      brush = Brush.radialGradient(
        colorStops = arrayOf(
          0.84f to Color(0x73000000),
          0.94f to Color(0x33000000),
          1.0f to Color.Transparent
        ),
        center = Offset(r, r + r * 0.03f),
        radius = r
      ),
      radius = r,
      center = Offset(r, r + r * 0.03f)
    )

    val vinylRadius = r * 0.94f
    rotate(degrees = rotationDegrees, pivot = center) {
      drawCircle(color = Color(0xFF141414), radius = vinylRadius, center = center)

      val labelRadius = vinylRadius * 0.30f
      val stepPx = 3.dp.toPx()
      var grooveR = labelRadius + 3.dp.toPx()
      while (grooveR < vinylRadius - 2.dp.toPx()) {
        drawCircle(
          color = Color(0xFF262626),
          radius = grooveR,
          center = center,
          style = Stroke(width = 1.2.dp.toPx())
        )
        grooveR += stepPx
      }

      // Rotating dual-band conic light sheen (~17% and ~13% opacity)
      drawCircle(
        brush = Brush.sweepGradient(
          colorStops = arrayOf(
            0.0f to Color.Transparent,
            0.14f to Color.White.copy(alpha = 0.17f),
            0.30f to Color.Transparent,
            0.50f to Color.Transparent,
            0.65f to Color.White.copy(alpha = 0.13f),
            0.84f to Color.Transparent
          ),
          center = center
        ),
        radius = vinylRadius,
        center = center
      )

      // Label (30% radius): 8..30% coverBase, 4..8% coverLight, 0..4% paper hole
      drawCircle(color = Color(palette.coverBase), radius = labelRadius, center = center)
      drawCircle(color = Color(palette.coverLight), radius = vinylRadius * 0.08f, center = center)
      drawCircle(color = Color(0xFFEFE6D6), radius = (vinylRadius * 0.04f).coerceAtLeast(2.5.dp.toPx()), center = center)

      // Spin indicator dot on label (5% diameter = 2.5% radius) at 40% from top
      drawCircle(
        color = Color(0xFFEFE6D6),
        radius = (vinylRadius * 0.05f).coerceAtLeast(2.dp.toPx()),
        center = Offset(center.x + vinylRadius * 0.11f, center.y - vinylRadius * 0.18f)
      )
    }

    // Non-rotating 135-deg highlight (20% to 0% by 42%)
    drawCircle(
      brush = Brush.linearGradient(
        colorStops = arrayOf(
          0.0f to Color.White.copy(alpha = 0.20f),
          0.42f to Color.Transparent,
          1.0f to Color.Transparent
        ),
        start = Offset(center.x - vinylRadius * 0.7f, center.y - vinylRadius * 0.7f),
        end = Offset(center.x + vinylRadius * 0.7f, center.y + vinylRadius * 0.7f)
      ),
      radius = vinylRadius,
      center = center
    )

    // Non-rotating 2px #0B0B0B rim
    drawCircle(
      color = Color(0xFF0B0B0B),
      radius = vinylRadius - 1.dp.toPx(),
      center = center,
      style = Stroke(width = 1.6.dp.toPx())
    )
  }
}

@Composable
private fun GlassWidgetShell(
  style: WidgetGlassStyle,
  bundle: PreviewArtBundle?,
  modifier: Modifier = Modifier,
  content: @Composable () -> Unit
) {
  val corner = when (style) {
    WidgetGlassStyle.SMOKED_NEON -> 24.dp
    WidgetGlassStyle.VINYL -> 28.dp
    else -> 26.dp
  }

  if (style == WidgetGlassStyle.VINYL) {
    Box(
      modifier = modifier
        .clip(RoundedCornerShape(corner))
        .background(Color(0xFFEFE6D6))
        .clipToBounds()
    ) {
      Canvas(modifier = Modifier.matchParentSize()) {
        drawRect(
          brush = Brush.radialGradient(
            colors = listOf(
              Color.White.copy(alpha = 0.52f),
              Color.White.copy(alpha = 0.14f),
              Color.Transparent
            ),
            center = Offset(size.width * 0.92f, size.height * 0.06f),
            radius = size.maxDimension * 0.85f
          )
        )
        var y = 0f
        val step = 3.dp.toPx().coerceAtLeast(3f)
        while (y < size.height) {
          drawLine(
            color = Color(0xFF785A32).copy(alpha = 0.035f),
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = 1f
          )
          y += step
        }
      }
      content()
      Box(
        modifier = Modifier
          .matchParentSize()
          .border(1.dp, Color(0xFF2A2118).copy(alpha = 0.08f), RoundedCornerShape(corner))
      )
    }
    return
  }

  val bgLayer = bundle?.let {
    when (style) {
      WidgetGlassStyle.SMOKED_NEON -> it.blurredStrong
      WidgetGlassStyle.LIGHT_FROST -> it.blurredLightFrost
      else -> it.blurred26
    }
  }
  val autoTintColor = Color(
    WidgetGlassRenderer.computeAutoContrastTint(
      style = style,
      coverLuminance = bundle?.luminance ?: 0.42f
    )
  )
  val borderBrush = when (style) {
    WidgetGlassStyle.SMOKED_NEON -> Brush.linearGradient(
      colors = listOf(Color(0x808A7BFF), Color(0x664FC3FF), Color(0x29FFFFFF))
    )
    WidgetGlassStyle.LIGHT_FROST -> Brush.verticalGradient(
      colors = listOf(Color(0xA6FFFFFF), Color(0x40FFFFFF))
    )
    else -> Brush.verticalGradient(
      colors = listOf(Color(0x73FFFFFF), Color(0x29FFFFFF))
    )
  }

  Box(
    modifier = modifier
      .clip(RoundedCornerShape(corner))
      .background(Color(0xFF101524))
  ) {
    if (style != WidgetGlassStyle.POSTER_GLASS && bgLayer != null) {
      Image(
        bitmap = bgLayer,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.matchParentSize()
      )
      Box(
        modifier = Modifier
          .matchParentSize()
          .background(autoTintColor)
      )
    }

    if (style != WidgetGlassStyle.POSTER_GLASS) {
      Box(
        modifier = Modifier
          .matchParentSize()
          .background(
            Brush.linearGradient(
              0.0f to Color.White.copy(alpha = if (style == WidgetGlassStyle.LIGHT_FROST) 0.34f else 0.22f),
              0.55f to Color.Transparent
            )
          )
      )
    }

    content()

    Box(
      modifier = Modifier
        .matchParentSize()
        .border(1.dp, borderBrush, RoundedCornerShape(corner))
    )
  }
}

@Composable
private fun LiveMusicWidgetPreview(
  style: WidgetGlassStyle,
  bundle: PreviewArtBundle?,
  title: String,
  artist: String,
  progressFraction: Float,
  positionMs: Long,
  durationMs: Long,
  reducedMotion: Boolean,
  isPlaying: Boolean,
  isLiked: Boolean
) {
  val isLight = style == WidgetGlassStyle.LIGHT_FROST || style == WidgetGlassStyle.VINYL
  val titleColor = when (style) {
    WidgetGlassStyle.VINYL -> Color(0xFF2A2118)
    WidgetGlassStyle.LIGHT_FROST -> Color(0xFF14172A)
    else -> Color.White
  }
  val artistColor = when (style) {
    WidgetGlassStyle.VINYL -> Color(0xFF2A2118).copy(alpha = 0.65f)
    WidgetGlassStyle.LIGHT_FROST -> Color(0x9E14172A)
    else -> Color.White.copy(alpha = 0.70f)
  }
  val iconTint = titleColor
  val spinAngle = if (style == WidgetGlassStyle.VINYL) {
    rememberVinylSpinAngle(positionMs = positionMs, isPlaying = isPlaying, reducedMotion = reducedMotion)
  } else 0f

  GlassWidgetShell(
    style = style,
    bundle = bundle,
    modifier = Modifier
      .fillMaxWidth()
      .height(142.dp)
  ) {
    when (style) {
      WidgetGlassStyle.VINYL -> {
        val discPalette = bundle?.vinylPalette ?: VinylWidgetRenderer.PRESET_BLUE
        val elapsedText = VinylWidgetRenderer.formatTabularTime(if (durationMs > 0L) positionMs else 74_000L)
        val totalText = VinylWidgetRenderer.formatTabularTime(if (durationMs > 0L) durationMs else 208_000L)
        Box(modifier = Modifier.fillMaxSize().clipToBounds()) {
          // Half-visible spinning vinyl disc sitting BEHIND the text on the left edge
          VinylDiscCanvas(
            diameter = 148.dp,
            palette = discPalette,
            rotationDegrees = spinAngle,
            modifier = Modifier
              .align(Alignment.CenterStart)
              .offset(x = (-74).dp)
              .zIndex(0f)
          )
          Column(
            modifier = Modifier
              .fillMaxSize()
              .zIndex(1f)
              .padding(start = 84.dp, end = 14.dp, top = 12.dp, bottom = 10.dp),
            verticalArrangement = Arrangement.SpaceBetween
          ) {
            Column {
              Text(
                text = title,
                color = titleColor,
                fontSize = 15.5.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
              Text(
                text = artist,
                color = artistColor,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
              Box(
                modifier = Modifier
                  .fillMaxWidth()
                  .height(3.dp)
                  .clip(CircleShape)
                  .background(Color(0xFF2A2118).copy(alpha = 0.18f))
              ) {
                Box(
                  modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(progressFraction)
                    .background(Color(0xFF2A2118))
                )
              }
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
              ) {
                Text(elapsedText, fontSize = 10.sp, color = Color(0xFF2A2118).copy(alpha = 0.58f))
                Text(totalText, fontSize = 10.sp, color = Color(0xFF2A2118).copy(alpha = 0.58f))
              }
            }
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Icon(Icons.Rounded.SkipPrevious, contentDescription = null, tint = titleColor, modifier = Modifier.size(21.dp))
              Box(
                modifier = Modifier
                  .size(40.dp)
                  .clip(CircleShape)
                  .background(Color(0xFF2A2118)),
                contentAlignment = Alignment.Center
              ) {
                Icon(
                  imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                  contentDescription = null,
                  tint = Color(0xFFEFE6D6),
                  modifier = Modifier.size(22.dp)
                )
              }
              Icon(Icons.Rounded.SkipNext, contentDescription = null, tint = titleColor, modifier = Modifier.size(21.dp))
              Icon(
                imageVector = if (isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                contentDescription = null,
                tint = if (isLiked) Color(0xFFD93838) else titleColor,
                modifier = Modifier.size(19.dp)
              )
            }
          }
        }
      }

      WidgetGlassStyle.POSTER_GLASS -> {
        Box(modifier = Modifier.fillMaxSize()) {
          bundle?.posterCover?.let {
            Image(
              bitmap = it,
              contentDescription = null,
              contentScale = ContentScale.Crop,
              modifier = Modifier.fillMaxSize()
            )
          }
          Box(
            modifier = Modifier
              .align(Alignment.BottomCenter)
              .fillMaxWidth()
              .padding(8.dp)
              .height(68.dp)
              .clip(RoundedCornerShape(20.dp))
          ) {
            bundle?.posterPanel?.let {
              Image(
                bitmap = it,
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.matchParentSize()
              )
            }
            Row(
              modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 8.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = title,
                  color = Color.White,
                  fontSize = 15.sp,
                  fontWeight = FontWeight.Bold,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis
                )
                Text(
                  text = artist,
                  color = Color.White.copy(alpha = 0.74f),
                  fontSize = 12.sp,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                  modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.25f))
                ) {
                  Box(
                    modifier = Modifier
                      .fillMaxHeight()
                      .fillMaxWidth(progressFraction)
                      .background(Color.White)
                  )
                }
              }
              Spacer(modifier = Modifier.width(10.dp))
              Box(
                modifier = Modifier
                  .size(36.dp)
                  .clip(CircleShape)
                  .background(Color.White),
                contentAlignment = Alignment.Center
              ) {
                Icon(
                  imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                  contentDescription = null,
                  tint = Color(0xFF121628),
                  modifier = Modifier.size(20.dp)
                )
              }
              Spacer(modifier = Modifier.width(4.dp))
              Icon(
                imageVector = Icons.Rounded.SkipNext,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(22.dp)
              )
            }
          }
        }
      }

      WidgetGlassStyle.LIGHT_FROST -> {
        Row(modifier = Modifier.fillMaxSize()) {
          BoxWithConstraints(modifier = Modifier.fillMaxHeight().weight(0.38f)) {
            bundle?.leftFadeCover?.let {
              Image(
                bitmap = it,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
              )
            }
          }
          Column(
            modifier = Modifier
              .weight(0.62f)
              .fillMaxHeight()
              .padding(start = 6.dp, end = 12.dp, top = 12.dp, bottom = 10.dp),
            verticalArrangement = Arrangement.SpaceBetween
          ) {
            Column {
              Text(
                text = title,
                color = titleColor,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
              Text(
                text = artist,
                color = artistColor,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
            }
            bundle?.waveformProgress?.let {
              Image(
                bitmap = it,
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier
                  .fillMaxWidth()
                  .height(18.dp)
              )
            }
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Icon(Icons.Rounded.SkipPrevious, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
              Box(
                modifier = Modifier
                  .size(34.dp)
                  .clip(CircleShape)
                  .background(Color(0xFF3B46E0)),
                contentAlignment = Alignment.Center
              ) {
                Icon(
                  imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                  contentDescription = null,
                  tint = Color.White,
                  modifier = Modifier.size(19.dp)
                )
              }
              Icon(Icons.Rounded.SkipNext, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
              Icon(
                imageVector = if (isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                contentDescription = null,
                tint = if (isLiked) Color(0xFFEF4444) else iconTint,
                modifier = Modifier.size(18.dp)
              )
            }
          }
        }
      }

      else -> {
        Column(
          modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 12.dp),
          verticalArrangement = Arrangement.SpaceBetween
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
          ) {
            when (style) {
              WidgetGlassStyle.ORB -> {
                bundle?.orbDisc?.let {
                  Image(
                    bitmap = it,
                    contentDescription = null,
                    modifier = Modifier.size(56.dp)
                  )
                }
              }
              WidgetGlassStyle.SMOKED_NEON -> {
                bundle?.tiltedCover?.let {
                  Image(
                    bitmap = it,
                    contentDescription = null,
                    modifier = Modifier.size(56.dp)
                  )
                }
              }
              else -> {
                bundle?.roundedCover?.let {
                  Image(
                    bitmap = it,
                    contentDescription = null,
                    modifier = Modifier.size(52.dp)
                  )
                }
              }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                  text = title,
                  color = titleColor,
                  fontSize = 15.5.sp,
                  fontWeight = FontWeight.Bold,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                  modifier = Modifier.weight(1f)
                )
                if (style == WidgetGlassStyle.FROST_PILL) {
                  Image(
                    painter = painterResource(id = R.drawable.ic_widget_orbi),
                    contentDescription = null,
                    modifier = Modifier.size(15.dp)
                  )
                }
              }
              Text(
                text = artist,
                color = artistColor,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
            }
          }

          if (style == WidgetGlassStyle.SMOKED_NEON) {
            bundle?.segmentedProgress?.let {
              Image(
                bitmap = it,
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier
                  .fillMaxWidth()
                  .height(8.dp)
              )
            }
          } else {
            Box(
              modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.20f))
            ) {
              Box(
                modifier = Modifier
                  .fillMaxHeight()
                  .fillMaxWidth(progressFraction)
                  .background(
                    if (style == WidgetGlassStyle.ORB) Color.White
                    else Color(0xFF7EF0C5)
                  )
              )
            }
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Icon(Icons.Rounded.SkipPrevious, contentDescription = null, tint = iconTint.copy(alpha = 0.85f), modifier = Modifier.size(21.dp))
            Box(
              modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(
                  when (style) {
                    WidgetGlassStyle.ORB -> Color.White
                    WidgetGlassStyle.SMOKED_NEON -> Color(0x384FC3FF)
                    else -> Color(0x36FFFFFF)
                  }
                )
                .border(
                  1.dp,
                  if (style == WidgetGlassStyle.SMOKED_NEON) Color(0x994FC3FF) else Color.White.copy(alpha = 0.35f),
                  CircleShape
                ),
              contentAlignment = Alignment.Center
            ) {
              Icon(
                imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = null,
                tint = if (style == WidgetGlassStyle.ORB) Color(0xFF121628) else Color.White,
                modifier = Modifier.size(20.dp)
              )
            }
            Icon(Icons.Rounded.SkipNext, contentDescription = null, tint = iconTint.copy(alpha = 0.85f), modifier = Modifier.size(21.dp))
            Icon(
              imageVector = if (isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
              contentDescription = null,
              tint = if (isLiked) Color(0xFFEF4444) else iconTint.copy(alpha = 0.85f),
              modifier = Modifier.size(19.dp)
            )
          }
        }
      }
    }
  }
}

@Composable
private fun LiveLyricsWidgetPreview(
  style: WidgetGlassStyle,
  bundle: PreviewArtBundle?,
  title: String,
  artist: String,
  currentLyric: String,
  nextLyric: String,
  positionMs: Long,
  reducedMotion: Boolean,
  isPlaying: Boolean
) {
  val isLight = style == WidgetGlassStyle.LIGHT_FROST || style == WidgetGlassStyle.VINYL
  val primaryColor = when (style) {
    WidgetGlassStyle.VINYL -> Color(0xFF2A2118)
    WidgetGlassStyle.LIGHT_FROST -> Color(0xFF14172A)
    else -> Color.White
  }
  val mutedColor = when (style) {
    WidgetGlassStyle.VINYL -> Color(0x802A2118)
    WidgetGlassStyle.LIGHT_FROST -> Color(0x8014172A)
    WidgetGlassStyle.SMOKED_NEON -> Color(0x73FFFFFF)
    WidgetGlassStyle.POSTER_GLASS -> Color(0xB3FFFFFF)
    else -> Color(0x9EFFFFFF)
  }
  val spinAngle = if (style == WidgetGlassStyle.VINYL) {
    rememberVinylSpinAngle(positionMs = positionMs, isPlaying = isPlaying, reducedMotion = reducedMotion)
  } else 0f

  GlassWidgetShell(
    style = style,
    bundle = bundle,
    modifier = Modifier
      .fillMaxWidth()
      .height(142.dp)
  ) {
    when (style) {
      WidgetGlassStyle.VINYL -> {
        val discPalette = bundle?.vinylPalette ?: VinylWidgetRenderer.PRESET_BLUE
        Box(modifier = Modifier.fillMaxSize().clipToBounds()) {
          VinylDiscCanvas(
            diameter = 116.dp,
            palette = discPalette,
            rotationDegrees = spinAngle,
            modifier = Modifier
              .align(Alignment.CenterStart)
              .offset(x = (-58).dp)
              .zIndex(0f)
          )
          Column(
            modifier = Modifier
              .fillMaxSize()
              .zIndex(1f)
              .padding(start = 68.dp, end = 14.dp, top = 12.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.SpaceBetween
          ) {
            Text(
              text = "$title · $artist",
              color = Color(0x992A2118),
              fontSize = 12.sp,
              fontWeight = FontWeight.SemiBold,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
            Column {
              Text(
                text = currentLyric,
                color = primaryColor,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 23.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
              )
              Spacer(modifier = Modifier.height(4.dp))
              Text(
                text = nextLyric,
                color = mutedColor,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
            }
          }
        }
      }

      WidgetGlassStyle.POSTER_GLASS -> {
        Box(modifier = Modifier.fillMaxSize()) {
          bundle?.posterCover?.let {
            Image(
              bitmap = it,
              contentDescription = null,
              contentScale = ContentScale.Crop,
              modifier = Modifier.fillMaxSize()
            )
          }
          Box(
            modifier = Modifier
              .matchParentSize()
              .background(
                Brush.verticalGradient(
                  colors = listOf(Color(0x55060912), Color(0xB3060912))
                )
              )
          )
          Column(
            modifier = Modifier
              .fillMaxSize()
              .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              Text(
                text = "$title · $artist",
                color = Color.White.copy(alpha = 0.86f),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
              )
              bundle?.eq3White?.let {
                Image(bitmap = it, contentDescription = null, modifier = Modifier.size(width = 22.dp, height = 14.dp))
              }
            }

            Column(
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0x520C101E))
                .border(1.dp, Color.White.copy(alpha = 0.24f), RoundedCornerShape(16.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
              Text(
                text = currentLyric,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
              )
              Spacer(modifier = Modifier.height(3.dp))
              Text(
                text = nextLyric,
                color = mutedColor,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
            }
          }
        }
      }

      WidgetGlassStyle.LIGHT_FROST -> {
        Row(modifier = Modifier.fillMaxSize()) {
          Box(modifier = Modifier.fillMaxHeight().weight(0.38f)) {
            bundle?.leftFadeCover?.let {
              Image(
                bitmap = it,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
              )
            }
          }
          Column(
            modifier = Modifier
              .weight(0.62f)
              .fillMaxHeight()
              .padding(start = 4.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.SpaceBetween
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              bundle?.eq3Dark?.let {
                Image(bitmap = it, contentDescription = null, modifier = Modifier.size(width = 20.dp, height = 14.dp))
              }
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = title,
                color = primaryColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
            }
            Column {
              Text(
                text = currentLyric,
                color = primaryColor,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
              )
              Spacer(modifier = Modifier.height(3.dp))
              Text(
                text = nextLyric,
                color = mutedColor,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
            }
            Text(
              text = artist,
              color = mutedColor,
              fontSize = 11.sp,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
          }
        }
      }

      WidgetGlassStyle.ORB -> {
        Column(
          modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 10.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.SpaceBetween
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            bundle?.orbDisc?.let {
              Image(bitmap = it, contentDescription = null, modifier = Modifier.size(30.dp))
            }
            Text(
              text = "$title · $artist",
              color = Color.White.copy(alpha = 0.85f),
              fontSize = 11.5.sp,
              fontWeight = FontWeight.SemiBold,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
          }
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
              text = currentLyric,
              color = primaryColor,
              fontSize = 17.sp,
              fontWeight = FontWeight.Bold,
              textAlign = TextAlign.Center,
              maxLines = 2,
              overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
              text = nextLyric,
              color = mutedColor,
              fontSize = 12.sp,
              textAlign = TextAlign.Center,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
          }
          bundle?.eq3White?.let {
            Image(bitmap = it, contentDescription = null, modifier = Modifier.size(width = 22.dp, height = 13.dp))
          }
        }
      }

      else -> {
        Row(
          modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 12.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column(
            modifier = Modifier
              .weight(1f)
              .fillMaxHeight(),
            verticalArrangement = Arrangement.SpaceBetween
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              bundle?.eq3White?.let {
                Image(bitmap = it, contentDescription = null, modifier = Modifier.size(width = 20.dp, height = 13.dp))
              }
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = "$title · $artist",
                color = Color.White.copy(alpha = 0.82f),
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
            }
            Column {
              Text(
                text = currentLyric,
                color = primaryColor,
                fontSize = if (style == WidgetGlassStyle.SMOKED_NEON) 18.sp else 16.5.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
              )
              Spacer(modifier = Modifier.height(4.dp))
              Text(
                text = nextLyric,
                color = mutedColor,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
            }
          }
          if (style == WidgetGlassStyle.FROST_PILL) {
            Spacer(modifier = Modifier.width(10.dp))
            bundle?.warmRoundedCover?.let {
              Image(bitmap = it, contentDescription = null, modifier = Modifier.size(72.dp))
            }
          }
        }
      }
    }
  }
}

@Composable
private fun LiveCoverWidgetPreview(
  style: WidgetGlassStyle,
  bundle: PreviewArtBundle?,
  positionMs: Long,
  reducedMotion: Boolean,
  isPlaying: Boolean
) {
  val isLight = style == WidgetGlassStyle.LIGHT_FROST || style == WidgetGlassStyle.VINYL
  val spinAngle = if (style == WidgetGlassStyle.VINYL) {
    rememberVinylSpinAngle(positionMs = positionMs, isPlaying = isPlaying, reducedMotion = reducedMotion)
  } else 0f
  GlassWidgetShell(
    style = style,
    bundle = bundle,
    modifier = Modifier.size(134.dp)
  ) {
    Box(modifier = Modifier.fillMaxSize()) {
      when (style) {
        WidgetGlassStyle.VINYL -> {
          val discPalette = bundle?.vinylPalette ?: VinylWidgetRenderer.PRESET_BLUE
          VinylDiscCanvas(
            diameter = 102.dp,
            palette = discPalette,
            rotationDegrees = spinAngle,
            modifier = Modifier.align(Alignment.Center)
          )
        }
        WidgetGlassStyle.POSTER_GLASS -> {
          bundle?.posterCover?.let {
            Image(
              bitmap = it,
              contentDescription = null,
              contentScale = ContentScale.Crop,
              modifier = Modifier.fillMaxSize()
            )
          }
        }
        WidgetGlassStyle.ORB -> {
          bundle?.orbDisc?.let {
            Image(
              bitmap = it,
              contentDescription = null,
              modifier = Modifier
                .size(94.dp)
                .align(Alignment.Center)
            )
          }
        }
        WidgetGlassStyle.SMOKED_NEON -> {
          bundle?.tiltedCover?.let {
            Image(
              bitmap = it,
              contentDescription = null,
              modifier = Modifier
                .size(98.dp)
                .align(Alignment.Center)
            )
          }
        }
        else -> {
          bundle?.roundedCover?.let {
            Image(
              bitmap = it,
              contentDescription = null,
              modifier = Modifier
                .size(94.dp)
                .align(Alignment.Center)
            )
          }
        }
      }

      Box(
        modifier = Modifier
          .align(Alignment.BottomEnd)
          .padding(10.dp)
          .size(38.dp)
          .clip(CircleShape)
          .background(
            when (style) {
              WidgetGlassStyle.ORB, WidgetGlassStyle.POSTER_GLASS -> Color.White
              WidgetGlassStyle.LIGHT_FROST -> Color(0xFF3B46E0)
              WidgetGlassStyle.SMOKED_NEON -> Color(0x454FC3FF)
              WidgetGlassStyle.FROST_PILL -> Color(0x66121628)
              WidgetGlassStyle.VINYL -> Color(0xFF2A2118)
            }
          )
          .border(
            width = 1.dp,
            color = if (isLight) Color.White.copy(alpha = 0.65f) else Color.White.copy(alpha = 0.45f),
            shape = CircleShape
          ),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
          contentDescription = null,
          tint = when (style) {
            WidgetGlassStyle.ORB, WidgetGlassStyle.POSTER_GLASS -> Color(0xFF121628)
            WidgetGlassStyle.VINYL -> Color(0xFFEFE6D6)
            else -> Color.White
          },
          modifier = Modifier.size(20.dp)
        )
      }
    }
  }
}

@Composable
private fun LiveMiniWidgetPreview(
  style: WidgetGlassStyle,
  bundle: PreviewArtBundle?,
  title: String,
  artist: String,
  positionMs: Long,
  reducedMotion: Boolean,
  isPlaying: Boolean
) {
  val isLight = style == WidgetGlassStyle.LIGHT_FROST || style == WidgetGlassStyle.VINYL
  val titleColor = when (style) {
    WidgetGlassStyle.VINYL -> Color(0xFF2A2118)
    WidgetGlassStyle.LIGHT_FROST -> Color(0xFF14172A)
    else -> Color.White
  }
  val artistColor = when (style) {
    WidgetGlassStyle.VINYL -> Color(0xA62A2118)
    WidgetGlassStyle.LIGHT_FROST -> Color(0x9E14172A)
    else -> Color.White.copy(alpha = 0.72f)
  }
  val eqBitmap = when (style) {
    WidgetGlassStyle.VINYL -> bundle?.eq4Ink
    WidgetGlassStyle.LIGHT_FROST -> bundle?.eq4Dark
    else -> bundle?.eq4White
  }
  val spinAngle = if (style == WidgetGlassStyle.VINYL) {
    rememberVinylSpinAngle(positionMs = positionMs, isPlaying = isPlaying, reducedMotion = reducedMotion)
  } else 0f

  GlassWidgetShell(
    style = style,
    bundle = bundle,
    modifier = Modifier.size(134.dp)
  ) {
    if (style == WidgetGlassStyle.POSTER_GLASS) {
      Box(modifier = Modifier.fillMaxSize()) {
        bundle?.posterCover?.let {
          Image(
            bitmap = it,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
          )
        }
        eqBitmap?.let {
          Image(
            bitmap = it,
            contentDescription = null,
            modifier = Modifier
              .align(Alignment.TopStart)
              .padding(12.dp)
              .size(width = 34.dp, height = 22.dp)
          )
        }
        Column(
          modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .padding(8.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0x660B101E))
            .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
            .padding(horizontal = 10.dp, vertical = 7.dp)
        ) {
          Text(
            text = title,
            color = Color.White,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
          Text(
            text = artist,
            color = Color.White.copy(alpha = 0.78f),
            fontSize = 10.5.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }
      }
    } else {
      Column(
        modifier = Modifier
          .fillMaxSize()
          .padding(12.dp),
        verticalArrangement = Arrangement.SpaceBetween
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          eqBitmap?.let {
            Image(
              bitmap = it,
              contentDescription = null,
              modifier = Modifier.size(width = 34.dp, height = 22.dp)
            )
          }
          if (style == WidgetGlassStyle.VINYL) {
            val discPalette = bundle?.vinylPalette ?: VinylWidgetRenderer.PRESET_BLUE
            VinylDiscCanvas(
              diameter = 32.dp,
              palette = discPalette,
              rotationDegrees = spinAngle
            )
          } else if (style == WidgetGlassStyle.ORB) {
            bundle?.orbDisc?.let {
              Image(bitmap = it, contentDescription = null, modifier = Modifier.size(28.dp))
            }
          } else {
            Image(
              painter = painterResource(id = R.drawable.ic_widget_orbi),
              contentDescription = null,
              modifier = Modifier.size(16.dp)
            )
          }
        }

        Column {
          Text(
            text = title,
            color = titleColor,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
          )
          Spacer(modifier = Modifier.height(2.dp))
          Text(
            text = artist,
            color = artistColor,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }
      }
    }
  }
}
