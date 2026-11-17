package com.example.ui.screens

import android.graphics.Bitmap
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.example.R
import com.example.model.APP_ICON_ARTWORK
import com.example.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

fun resolveArtworkModel(artworkUrl: String, source: String = ""): Any? {
  return when {
    artworkUrl == APP_ICON_ARTWORK -> R.drawable.ic_launcher_foreground
    artworkUrl.isNotBlank() -> artworkUrl
    else -> null
  }
}

fun artworkHueColors(title: String): Pair<Color, Color> {
  var h = 0
  for (c in title) {
    h = (h * 31 + c.code) % 360
  }
  val hue1 = ((h % 360) + 360) % 360f
  val hue2 = (hue1 + 50f) % 360f
  return Color.hsl(hue1, 0.70f, 0.52f) to Color.hsl(hue2, 0.80f, 0.62f)
}

@Composable
fun rememberArtworkColors(
  artworkUrl: String,
  fallbackTitle: String
): Pair<Color, Color> {
  val context = LocalContext.current
  val fallback = remember(fallbackTitle) { artworkHueColors(fallbackTitle) }
  var extracted by remember(artworkUrl, fallbackTitle) { mutableStateOf(fallback) }

  LaunchedEffect(artworkUrl, fallbackTitle) {
    val model = resolveArtworkModel(artworkUrl)
    if (model == null) {
      extracted = fallback
      return@LaunchedEffect
    }
    val sampled = withContext(Dispatchers.IO) {
      try {
        val request = ImageRequest.Builder(context)
          .data(model)
          .allowHardware(false)
          .size(64)
          .build()
        val result = context.imageLoader.execute(request)
        val bitmap = (result as? SuccessResult)?.drawable?.toBitmap() ?: return@withContext null
        withContext(Dispatchers.Default) {
          extractVibrantPair(bitmap)
        }
      } catch (_: Exception) {
        null
      }
    }
    extracted = sampled ?: fallback
  }

  val animatedPrimary by animateColorAsState(
    targetValue = extracted.first,
    animationSpec = tween(durationMillis = 600),
    label = "artwork_primary_color"
  )
  val animatedSecondary by animateColorAsState(
    targetValue = extracted.second,
    animationSpec = tween(durationMillis = 600),
    label = "artwork_secondary_color"
  )
  return animatedPrimary to animatedSecondary
}

private fun extractVibrantPair(bitmap: Bitmap): Pair<Color, Color>? {
  val width = bitmap.width
  val height = bitmap.height
  if (width <= 0 || height <= 0) return null

  val stepX = (width / 24).coerceAtLeast(1)
  val stepY = (height / 24).coerceAtLeast(1)
  val hsv = FloatArray(3)
  val hueBuckets = FloatArray(12)
  val hueR = FloatArray(12)
  val hueG = FloatArray(12)
  val hueB = FloatArray(12)

  var fallbackR = 0f
  var fallbackG = 0f
  var fallbackB = 0f
  var fallbackCount = 0

  var y = 0
  while (y < height) {
    var x = 0
    while (x < width) {
      val pixel = bitmap.getPixel(x, y)
      val a = android.graphics.Color.alpha(pixel)
      if (a >= 180) {
        val r = android.graphics.Color.red(pixel)
        val g = android.graphics.Color.green(pixel)
        val b = android.graphics.Color.blue(pixel)
        android.graphics.Color.RGBToHSV(r, g, b, hsv)
        val s = hsv[1]
        val v = hsv[2]
        if (v > 0.10f) {
          fallbackR += r
          fallbackG += g
          fallbackB += b
          fallbackCount++
        }
        // Favor saturated, mid-to-bright colors (avoids washed-out whites and pure blacks)
        if (s > 0.20f && v > 0.16f && !(s < 0.15f && v > 0.88f)) {
          val bucket = ((hsv[0] / 30f).toInt()).coerceIn(0, 11)
          val weight = s * s * (0.4f + v)
          hueBuckets[bucket] += weight
          hueR[bucket] += r * weight
          hueG[bucket] += g * weight
          hueB[bucket] += b * weight
        }
      }
      x += stepX
    }
    y += stepY
  }

  val sortedBuckets = (0..11).sortedByDescending { hueBuckets[it] }
  val top1 = sortedBuckets.first()
  if (hueBuckets[top1] > 0.05f) {
    val w1 = hueBuckets[top1]
    val c1 = boostColorVibrance(
      (hueR[top1] / w1).toInt().coerceIn(0, 255),
      (hueG[top1] / w1).toInt().coerceIn(0, 255),
      (hueB[top1] / w1).toInt().coerceIn(0, 255)
    )
    val top2 = sortedBuckets.firstOrNull { idx ->
      idx != top1 && hueBuckets[idx] > 0.03f && kotlin.math.abs(idx - top1) !in listOf(1, 11)
    } ?: sortedBuckets.getOrNull(1)

    val c2 = if (top2 != null && hueBuckets[top2] > 0.03f) {
      val w2 = hueBuckets[top2]
      boostColorVibrance(
        (hueR[top2] / w2).toInt().coerceIn(0, 255),
        (hueG[top2] / w2).toInt().coerceIn(0, 255),
        (hueB[top2] / w2).toInt().coerceIn(0, 255)
      )
    } else {
      val shifted = FloatArray(3)
      android.graphics.Color.colorToHSV(android.graphics.Color.rgb((c1.red * 255).toInt(), (c1.green * 255).toInt(), (c1.blue * 255).toInt()), shifted)
      Color.hsl((shifted[0] + 28f) % 360f, (shifted[1] * 0.9f).coerceIn(0.5f, 0.92f), 0.58f)
    }
    return c1 to c2
  }

  if (fallbackCount > 0) {
    val avg = boostColorVibrance(
      (fallbackR / fallbackCount).toInt().coerceIn(0, 255),
      (fallbackG / fallbackCount).toInt().coerceIn(0, 255),
      (fallbackB / fallbackCount).toInt().coerceIn(0, 255)
    )
    return avg to avg.copy(alpha = 0.85f)
  }
  return null
}

private fun boostColorVibrance(r: Int, g: Int, b: Int): Color {
  val hsl = FloatArray(3)
  val packed = android.graphics.Color.rgb(r, g, b)
  android.graphics.Color.colorToHSV(packed, hsl)
  val sat = hsl[1].coerceIn(0.45f, 0.92f)
  val valBoost = hsl[2].coerceIn(0.48f, 0.86f)
  return Color(android.graphics.Color.HSVToColor(floatArrayOf(hsl[0], sat, valBoost)))
}

@Composable
fun SongArtwork(
  song: Song,
  modifier: Modifier = Modifier,
  cornerRadius: Dp = 12.dp,
  iconSize: Dp = 22.dp
) {
  val (c1, c2) = remember(song.title) { artworkHueColors(song.title) }
  val gradient = remember(c1, c2) {
    Brush.linearGradient(colors = listOf(c1, c2, Color(0xFF0B1020)))
  }

  val usesAppIcon = song.artworkUrl == APP_ICON_ARTWORK
  val placeholderGradient = remember(c1, c2, song.artworkUrl) {
    if (song.artworkUrl.isBlank() || usesAppIcon) {
      Brush.linearGradient(colors = listOf(Color(0xFF7B61FF), Color(0xFF2D1E69)))
    } else {
      gradient
    }
  }

  Box(
    modifier = modifier
      .clip(RoundedCornerShape(cornerRadius))
      .background(placeholderGradient),
    contentAlignment = Alignment.Center
  ) {
    androidx.compose.animation.Crossfade(
      targetState = song.artworkUrl,
      animationSpec = tween(durationMillis = 280),
      label = "song_artwork_crossfade"
    ) { targetArt ->
      Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
      ) {
        when {
          targetArt == APP_ICON_ARTWORK -> {
            Image(
              painter = painterResource(id = R.drawable.ic_launcher_foreground),
              contentDescription = song.title,
              contentScale = ContentScale.Fit,
              modifier = Modifier.fillMaxSize(0.86f)
            )
          }
          targetArt.isNotBlank() -> {
            AsyncImage(
              model = targetArt,
              contentDescription = song.title,
              contentScale = ContentScale.Crop,
              modifier = Modifier.fillMaxSize()
            )
          }
          else -> {
            Icon(
              imageVector = Icons.Rounded.MusicNote,
              contentDescription = null,
              tint = Color.White.copy(alpha = 0.88f),
              modifier = Modifier.size(iconSize)
            )
          }
        }
      }
    }
  }
}

@Composable
fun EqualizerBars(
  isPlaying: Boolean,
  color: Color,
  reducedMotion: Boolean = false,
  modifier: Modifier = Modifier,
  barWidth: Dp = 3.dp,
  maxHeight: Dp = 14.dp
) {
  val infiniteTransition = rememberInfiniteTransition(label = "eq")
  val anim1 by infiniteTransition.animateFloat(
    initialValue = 0.3f,
    targetValue = 1.0f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = 480, easing = FastOutSlowInEasing),
      repeatMode = RepeatMode.Reverse
    ),
    label = "bar1"
  )
  val anim2 by infiniteTransition.animateFloat(
    initialValue = 0.9f,
    targetValue = 0.25f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = 560, easing = FastOutSlowInEasing),
      repeatMode = RepeatMode.Reverse
    ),
    label = "bar2"
  )
  val anim3 by infiniteTransition.animateFloat(
    initialValue = 0.4f,
    targetValue = 0.95f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = 420, easing = FastOutSlowInEasing),
      repeatMode = RepeatMode.Reverse
    ),
    label = "bar3"
  )

  val h1 = if (isPlaying && !reducedMotion) anim1 else 0.45f
  val h2 = if (isPlaying && !reducedMotion) anim2 else 0.85f
  val h3 = if (isPlaying && !reducedMotion) anim3 else 0.55f

  Row(
    modifier = modifier.height(maxHeight),
    horizontalArrangement = Arrangement.spacedBy(2.dp),
    verticalAlignment = Alignment.Bottom
  ) {
    listOf(h1, h2, h3).forEach { fraction ->
      Box(
        modifier = Modifier
          .width(barWidth)
          .fillMaxHeight(fraction)
          .clip(RoundedCornerShape(2.dp))
          .background(color)
      )
    }
  }
}

@Composable
fun GlassCard(
  modifier: Modifier = Modifier,
  darkTheme: Boolean,
  cornerRadius: Dp = 24.dp,
  content: @Composable ColumnScope.() -> Unit
) {
  val shape = RoundedCornerShape(cornerRadius)
  val glassBrush = if (darkTheme) {
    Brush.linearGradient(
      colors = listOf(
        Color(0xFF212D46).copy(alpha = 0.78f),
        Color(0xFF151E32).copy(alpha = 0.68f),
        Color(0xFF101728).copy(alpha = 0.76f)
      )
    )
  } else {
    Brush.linearGradient(
      colors = listOf(
        Color.White.copy(alpha = 0.90f),
        Color(0xFFF3F6FF).copy(alpha = 0.76f),
        Color.White.copy(alpha = 0.84f)
      )
    )
  }
  val borderBrush = Brush.verticalGradient(
    colors = if (darkTheme) {
      listOf(Color.White.copy(alpha = 0.32f), Color.White.copy(alpha = 0.12f), Color.White.copy(alpha = 0.05f))
    } else {
      listOf(Color.White, Color.White.copy(alpha = 0.68f), Color.White.copy(alpha = 0.42f))
    }
  )
  val topSpecular = Color.White.copy(alpha = if (darkTheme) 0.42f else 0.98f)

  Box(
    modifier = modifier
      .drawBehind {
        val r = cornerRadius.toPx()
        drawRoundRect(
          brush = Brush.verticalGradient(
            colors = listOf(
              Color.Black.copy(alpha = if (darkTheme) 0.22f else 0.05f),
              Color.Black.copy(alpha = if (darkTheme) 0.34f else 0.10f)
            )
          ),
          topLeft = Offset(0f, 5.dp.toPx()),
          size = Size(size.width, size.height),
          cornerRadius = CornerRadius(r, r)
        )
      }
      .clip(shape)
      .background(glassBrush)
      .border(1.dp, borderBrush, shape)
      .drawBehind {
        val stroke15 = 1.5.dp.toPx()
        val inset = stroke15 * 0.5f
        val r = (cornerRadius.toPx() - inset).coerceAtLeast(0f)
        drawRoundRect(
          brush = Brush.verticalGradient(
            colors = listOf(topSpecular, topSpecular.copy(alpha = 0.14f), Color.Transparent),
            startY = 0f,
            endY = size.height * 0.36f
          ),
          topLeft = Offset(inset, inset),
          size = Size(size.width - stroke15, size.height - stroke15),
          cornerRadius = CornerRadius(r, r),
          style = Stroke(width = stroke15)
        )
      }
  ) {
    Column(content = content)
  }
}

@Composable
fun GlassSegmentedBar(
  options: List<Pair<String, androidx.compose.ui.graphics.vector.ImageVector?>>,
  selectedIndex: Int,
  darkTheme: Boolean,
  onSelect: (Int) -> Unit,
  modifier: Modifier = Modifier,
  testTags: List<String> = emptyList(),
  reducedMotion: Boolean = false
) {
  val barBg = if (darkTheme) Color(0xFF151E32).copy(alpha = 0.72f) else Color.White.copy(alpha = 0.72f)
  val barBorder = if (darkTheme) Color.White.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.96f)
  val pillBg = if (darkTheme) Color(0xFF283652).copy(alpha = 0.94f) else Color.White.copy(alpha = 0.96f)
  val pillBorderColor = Color.White.copy(alpha = if (darkTheme) 0.28f else 0.98f)
  val colorSpec = if (reducedMotion) snap<Color>() else tween<Color>(durationMillis = 220, easing = FastOutSlowInEasing)

  Row(
    modifier = modifier
      .fillMaxWidth()
      .clip(CircleShape)
      .background(barBg)
      .border(1.dp, barBorder, CircleShape)
      .padding(4.dp),
    horizontalArrangement = Arrangement.spacedBy(4.dp)
  ) {
    options.forEachIndexed { index, (label, icon) ->
      val selected = selectedIndex == index
      val tag = testTags.getOrNull(index)
      val animatedBg by animateColorAsState(
        targetValue = if (selected) pillBg else Color.Transparent,
        animationSpec = colorSpec,
        label = "seg_bg_$index"
      )
      val animatedBorder by animateColorAsState(
        targetValue = if (selected) pillBorderColor else Color.Transparent,
        animationSpec = colorSpec,
        label = "seg_border_$index"
      )
      val animatedContentColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = colorSpec,
        label = "seg_text_$index"
      )
      val animatedIconColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = colorSpec,
        label = "seg_icon_$index"
      )

      Row(
        modifier = Modifier
          .weight(1f)
          .clip(CircleShape)
          .background(animatedBg)
          .border(0.75.dp, animatedBorder, CircleShape)
          .clickable { onSelect(index) }
          .then(if (tag != null) Modifier.testTag(tag) else Modifier)
          .padding(vertical = 10.dp, horizontal = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
      ) {
        if (icon != null) {
          Icon(
            imageVector = icon,
            contentDescription = null,
            tint = animatedIconColor,
            modifier = Modifier.size(16.dp)
          )
          Spacer(modifier = Modifier.width(6.dp))
        }
        Text(
          text = label,
          style = MaterialTheme.typography.bodySmall.copy(
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold
          ),
          color = animatedContentColor,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
      }
    }
  }
}

@Composable
fun AurixBrandLogo(
  size: Dp = 56.dp,
  cornerRadius: Dp = 18.dp,
  animated: Boolean = true,
  reducedMotion: Boolean = false,
  modifier: Modifier = Modifier
) {
  val infiniteTransition = rememberInfiniteTransition(label = "aurix_logo_anim")
  val pulseScale by infiniteTransition.animateFloat(
    initialValue = 1.0f,
    targetValue = 1.045f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = 1900, easing = FastOutSlowInEasing),
      repeatMode = RepeatMode.Reverse
    ),
    label = "logo_scale"
  )
  val glowAlpha by infiniteTransition.animateFloat(
    initialValue = 0.22f,
    targetValue = 0.48f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = 1900, easing = FastOutSlowInEasing),
      repeatMode = RepeatMode.Reverse
    ),
    label = "logo_glow"
  )

  val activeScale = if (animated && !reducedMotion) pulseScale else 1.0f
  val activeGlow = if (animated && !reducedMotion) glowAlpha else 0.26f

  Box(
    modifier = modifier.size(size),
    contentAlignment = Alignment.Center
  ) {
    Box(
      modifier = Modifier
        .matchParentSize()
        .scale(activeScale * 1.06f)
        .clip(RoundedCornerShape(cornerRadius + 4.dp))
        .background(
          Brush.radialGradient(
            colors = listOf(
              Color(0xFF9EA3B8).copy(alpha = activeGlow),
              Color.Transparent
            )
          )
        )
    )
    Box(
      modifier = Modifier
        .matchParentSize()
        .scale(activeScale)
        .clip(RoundedCornerShape(cornerRadius))
        .background(
          Brush.verticalGradient(
            colors = listOf(Color(0xFFD3D6E4), Color(0xFF6E7084))
          )
        )
        .border(
          width = 0.75.dp,
          brush = Brush.verticalGradient(
            listOf(Color.White.copy(alpha = 0.45f), Color.White.copy(alpha = 0.08f))
          ),
          shape = RoundedCornerShape(cornerRadius)
        )
    ) {
      Canvas(modifier = Modifier.fillMaxSize()) {
        val cx = this.size.width / 2f
        val cy = this.size.height / 2f
        val planetRadius = this.size.minDimension * 0.285f
        val ringRx = this.size.minDimension * 0.405f
        val ringRy = this.size.minDimension * 0.112f
        val ringStroke = this.size.minDimension * 0.052f
        val ringColor = Color(0xFF3A3B42)

        rotate(degrees = -12f, pivot = Offset(cx, cy)) {
          drawArc(
            color = ringColor,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(cx - ringRx, cy - ringRy),
            size = Size(ringRx * 2f, ringRy * 2f),
            style = Stroke(width = ringStroke, cap = StrokeCap.Round)
          )

          drawCircle(
            color = Color.Black.copy(alpha = 0.14f),
            radius = planetRadius * 1.04f,
            center = Offset(cx, cy + planetRadius * 0.09f)
          )
          drawCircle(
            color = Color.White,
            radius = planetRadius,
            center = Offset(cx, cy)
          )

          val eyeOffsetX = planetRadius * 0.215f
          val eyeCenterY = cy - planetRadius * 0.21f
          val eyeW = planetRadius * 0.245f
          val eyeH = planetRadius * 0.42f
          drawRoundRect(
            color = Color(0xFF0B0C10),
            topLeft = Offset(cx - eyeOffsetX - eyeW / 2f, eyeCenterY - eyeH / 2f),
            size = Size(eyeW, eyeH),
            cornerRadius = CornerRadius(eyeW * 0.5f, eyeW * 0.5f)
          )
          drawRoundRect(
            color = Color(0xFF0B0C10),
            topLeft = Offset(cx + eyeOffsetX - eyeW / 2f, eyeCenterY - eyeH / 2f),
            size = Size(eyeW, eyeH),
            cornerRadius = CornerRadius(eyeW * 0.5f, eyeW * 0.5f)
          )

          drawArc(
            color = ringColor,
            startAngle = 0f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(cx - ringRx, cy - ringRy),
            size = Size(ringRx * 2f, ringRy * 2f),
            style = Stroke(width = ringStroke, cap = StrokeCap.Round)
          )
        }
      }
    }
  }
}

@Composable
fun DynamicArtworkBackdrop(
  song: Song?,
  enabled: Boolean,
  darkTheme: Boolean,
  reducedMotion: Boolean,
  modifier: Modifier = Modifier
) {
  val accentColor = MaterialTheme.colorScheme.primary
  val targetAlpha = if (enabled && song != null) (if (darkTheme) 0.78f else 0.56f) else 0f
  val animatedAlpha by animateFloatAsState(
    targetValue = targetAlpha,
    animationSpec = if (reducedMotion) snap() else tween(durationMillis = 750),
    label = "dynamic_bg_alpha"
  )

  val (c1, c2) = rememberArtworkColors(
    artworkUrl = if (enabled) song?.artworkUrl.orEmpty() else "",
    fallbackTitle = song?.title.orEmpty()
  )

  val infiniteTransition = rememberInfiniteTransition(label = "bg_glow")
  val driftScale by infiniteTransition.animateFloat(
    initialValue = 1.32f,
    targetValue = 1.48f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = 7200, easing = FastOutSlowInEasing),
      repeatMode = RepeatMode.Reverse
    ),
    label = "bg_drift_scale"
  )
  val driftOffsetX by infiniteTransition.animateFloat(
    initialValue = -18f,
    targetValue = 18f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = 8600, easing = FastOutSlowInEasing),
      repeatMode = RepeatMode.Reverse
    ),
    label = "bg_drift_x"
  )
  val activeScale = if (reducedMotion) 1.36f else driftScale
  val activeOffsetX = if (reducedMotion) 0f else driftOffsetX

  Box(modifier = modifier.fillMaxSize()) {
    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(
          Brush.linearGradient(
            colors = if (darkTheme) {
              listOf(
                Color(0xFF0A0E1A),
                Color(0xFF10172B),
                Color(0xFF13132B)
              )
            } else {
              listOf(
                Color(0xFFEAF0FF),
                Color(0xFFECE4FF),
                Color(0xFFDFF1FF)
              )
            }
          )
        )
    )

    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(
          Brush.radialGradient(
            colors = listOf(accentColor.copy(alpha = 0.20f), Color.Transparent),
            radius = 950f
          )
        )
    )

    if (animatedAlpha > 0.01f && song != null) {
      Box(modifier = Modifier.fillMaxSize().alpha(animatedAlpha)) {
        val bgModel = resolveArtworkModel(song.artworkUrl, song.source)
        if (bgModel != null) {
          AsyncImage(
            model = bgModel,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
              .fillMaxSize()
              .offset(x = activeOffsetX.dp)
              .scale(activeScale)
              .blur(68.dp)
          )
        }
        Box(
          modifier = Modifier
            .fillMaxSize()
            .background(
              Brush.radialGradient(
                colors = listOf(
                  c1.copy(alpha = if (darkTheme) 0.56f else 0.42f),
                  c2.copy(alpha = if (darkTheme) 0.36f else 0.28f),
                  Color.Transparent
                ),
                center = Offset(240f, 320f),
                radius = 1200f
              )
            )
        )
        Box(
          modifier = Modifier
            .fillMaxSize()
            .background(
              Brush.verticalGradient(
                colors = if (darkTheme) {
                  listOf(
                    Color(0xFF0B0F1A).copy(alpha = 0.36f),
                    Color(0xFF0B0F1A).copy(alpha = 0.50f),
                    Color(0xFF080B14).copy(alpha = 0.68f)
                  )
                } else {
                  listOf(
                    Color(0xFFF3F6FF).copy(alpha = 0.42f),
                    Color(0xFFF3F6FF).copy(alpha = 0.52f),
                    Color(0xFFEBF0FF).copy(alpha = 0.62f)
                  )
                }
              )
            )
        )
      }
    }
  }
}

@Composable
fun DynamicArtGlassBackdropLayer(
  song: Song?,
  artColors: Pair<Color, Color>,
  enabled: Boolean,
  darkTheme: Boolean,
  reducedMotion: Boolean = false,
  blurRadius: Dp = 32.dp,
  artAlpha: Float = if (darkTheme) 0.30f else 0.20f,
  scrimAlpha: Float = if (darkTheme) 0.58f else 0.72f,
  modifier: Modifier = Modifier
) {
  val layerAlpha by animateFloatAsState(
    targetValue = if (enabled && song != null) 1f else 0f,
    animationSpec = if (reducedMotion) snap() else tween(450),
    label = "dynamic_glass_layer_alpha"
  )
  val (c1, c2) = artColors

  Box(modifier = modifier) {
    if (layerAlpha > 0.01f && song != null) {
      Box(modifier = Modifier.matchParentSize().alpha(layerAlpha)) {
        val model = resolveArtworkModel(song.artworkUrl, song.source)
        if (model != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
          AsyncImage(
            model = model,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
              .matchParentSize()
              .scale(1.18f)
              .alpha(artAlpha)
              .blur(blurRadius)
          )
        }
        Box(
          modifier = Modifier
            .matchParentSize()
            .background(
              Brush.linearGradient(
                colors = listOf(
                  c1.copy(alpha = if (darkTheme) 0.34f else 0.22f),
                  c2.copy(alpha = if (darkTheme) 0.22f else 0.16f),
                  Color.Transparent
                )
              )
            )
        )
        Box(
          modifier = Modifier
            .matchParentSize()
            .background(
              Brush.radialGradient(
                colors = listOf(
                  c2.copy(alpha = if (darkTheme) 0.28f else 0.16f),
                  Color.Transparent
                ),
                center = Offset(720f, 360f),
                radius = 780f
              )
            )
        )
      }
    }
    Box(
      modifier = Modifier
        .matchParentSize()
        .background(
          if (darkTheme) Color(0xFF0C1222).copy(alpha = scrimAlpha)
          else Color.White.copy(alpha = scrimAlpha)
        )
    )
  }
}

@Composable
fun OpenCloseGlassPill(
  isOpen: Boolean,
  onToggle: () -> Unit,
  darkTheme: Boolean,
  reducedMotion: Boolean = false,
  testTag: String,
  openLabel: String = stringResource(id = R.string.action_open),
  closeLabel: String = stringResource(id = R.string.action_close),
  contentDescription: String,
  accentColor: Color = AurixTealAccent,
  modifier: Modifier = Modifier
) {
  val interactionSource = remember { MutableInteractionSource() }
  val isPressed by interactionSource.collectIsPressedAsState()
  val scale by animateFloatAsState(
    targetValue = if (isPressed) 0.92f else 1f,
    animationSpec = if (reducedMotion) snap() else spring(
      dampingRatio = 0.68f,
      stiffness = Spring.StiffnessMedium
    ),
    label = "open_close_pill_scale"
  )
  val bgColor = when {
    !isOpen -> accentColor.copy(alpha = if (darkTheme) 0.20f else 0.14f)
    darkTheme -> Color.White.copy(alpha = 0.08f)
    else -> Color.White.copy(alpha = 0.80f)
  }
  val borderColor = when {
    !isOpen -> accentColor.copy(alpha = 0.55f)
    darkTheme -> Color.White.copy(alpha = 0.16f)
    else -> Color.White
  }
  val contentColor = if (!isOpen) accentColor else MaterialTheme.colorScheme.onSurfaceVariant

  Box(
    modifier = modifier
      .minimumInteractiveComponentSize()
      .graphicsLayer {
        scaleX = scale
        scaleY = scale
      }
      .clip(CircleShape)
      .background(bgColor)
      .border(1.dp, borderColor, CircleShape)
      .clickable(
        interactionSource = interactionSource,
        indication = LocalIndication.current
      ) { onToggle() }
      .testTag(testTag),
    contentAlignment = Alignment.Center
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
      Icon(
        imageVector = if (isOpen) Icons.Rounded.Close else Icons.Rounded.KeyboardArrowDown,
        contentDescription = contentDescription,
        tint = contentColor,
        modifier = Modifier.size(14.dp)
      )
      Text(
        text = if (isOpen) closeLabel else openLabel,
        style = MaterialTheme.typography.labelSmall.copy(
          fontWeight = FontWeight.Bold,
          fontSize = 11.sp
        ),
        color = contentColor,
        maxLines = 1
      )
    }
  }
}

@Composable
fun SongRow(
  song: Song,
  isPlaying: Boolean = false,
  isCurrent: Boolean = false,
  reducedMotion: Boolean = false,
  darkTheme: Boolean = true,
  hasLyrics: Boolean = false,
  rank: Int? = null,
  showSourceBadge: Boolean = true,
  onCheckLyrics: ((Song) -> Unit)? = null,
  onSongClick: () -> Unit,
  onFavoriteClick: () -> Unit,
  onAddToAlbumClick: (() -> Unit)? = null,
  onEditPhotoClick: (() -> Unit)? = null,
  onDeleteClick: (() -> Unit)? = null
) {
  LaunchedEffect(song.id) {
    onCheckLyrics?.invoke(song)
  }

  val heartScale by animateFloatAsState(
    targetValue = if (song.isFavorite) 1.15f else 1.0f,
    animationSpec = if (reducedMotion) snap() else spring(dampingRatio = Spring.DampingRatioMediumBouncy),
    label = "heart_pop"
  )

  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(16.dp))
      .then(
        if (isCurrent) Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
        else Modifier
      )
      .clickable { onSongClick() }
      .padding(vertical = 8.dp, horizontal = 8.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    if (rank != null) {
      Box(
        modifier = Modifier.width(28.dp),
        contentAlignment = Alignment.Center
      ) {
        if (isCurrent) {
          EqualizerBars(
            isPlaying = isPlaying,
            color = MaterialTheme.colorScheme.primary,
            reducedMotion = reducedMotion,
            maxHeight = 14.dp
          )
        } else {
          Text(
            text = String.format("%02d", rank),
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            color = if (rank <= 3) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f)
          )
        }
      }
      Spacer(modifier = Modifier.width(6.dp))
    }

    Box(
      modifier = Modifier
        .size(50.dp)
        .clip(RoundedCornerShape(13.dp))
        .border(1.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(13.dp))
    ) {
      SongArtwork(song = song, modifier = Modifier.fillMaxSize(), cornerRadius = 13.dp)
    }
    Spacer(modifier = Modifier.width(12.dp))
    Column(modifier = Modifier.weight(1f)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        if (rank == null) {
          AnimatedVisibility(visible = isCurrent, enter = fadeIn(), exit = fadeOut()) {
            Row {
              EqualizerBars(
                isPlaying = isPlaying,
                color = MaterialTheme.colorScheme.primary,
                reducedMotion = reducedMotion,
                maxHeight = 13.dp
              )
              Spacer(modifier = Modifier.width(8.dp))
            }
          }
        }
        Text(
          text = song.title,
          style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
          modifier = Modifier.weight(1f, fill = false)
        )
        LyricsTag(
          visible = hasLyrics,
          size = LyricsTagSize.Standard,
          darkTheme = darkTheme,
          reducedMotion = reducedMotion,
          modifier = Modifier.padding(start = 8.dp)
        )
      }
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        Text(
          text = song.artist,
          style = MaterialTheme.typography.bodySmall,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.weight(1f, fill = false)
        )
        if (showSourceBadge && song.source == "iTunes 30s") {
          Surface(
            shape = RoundedCornerShape(6.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
          ) {
            Text(
              text = "30s",
              style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp),
              color = MaterialTheme.colorScheme.primary,
              modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
            )
          }
        } else if (showSourceBadge && song.source == "YouTube") {
          Surface(
            shape = RoundedCornerShape(6.dp),
            color = Color(0xFFEF4444).copy(alpha = if (darkTheme) 0.20f else 0.14f)
          ) {
            Text(
              text = "YT",
              style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp),
              color = if (darkTheme) Color(0xFFFF8A8A) else Color(0xFFDC2626),
              modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
            )
          }
        }
      }
    }
    if (song.durationSeconds > 0 && song.source != "iTunes 30s" && rank == null) {
      Text(
        text = "${song.durationSeconds / 60}:${String.format("%02d", song.durationSeconds % 60)}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 4.dp)
      )
    }
    if (onAddToAlbumClick != null) {
      IconButton(
        onClick = onAddToAlbumClick,
        modifier = Modifier
          .minimumInteractiveComponentSize()
          .size(40.dp)
          .testTag("song_add_album_button_${song.id}")
      ) {
        Icon(
          imageVector = Icons.Rounded.PlaylistAdd,
          contentDescription = "Add to album",
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(20.dp)
        )
      }
    }
    if (onEditPhotoClick != null) {
      IconButton(
        onClick = onEditPhotoClick,
        modifier = Modifier
          .minimumInteractiveComponentSize()
          .size(40.dp)
          .testTag("song_edit_photo_button_${song.id}")
      ) {
        Icon(
          imageVector = Icons.Rounded.AddPhotoAlternate,
          contentDescription = "Customize song photo",
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(20.dp)
        )
      }
    }
    IconButton(
      onClick = onFavoriteClick,
      modifier = Modifier
        .minimumInteractiveComponentSize()
        .size(40.dp)
        .testTag("song_favorite_button_${song.id}")
    ) {
      Icon(
        imageVector = if (song.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
        contentDescription = "Favorite",
        tint = if (song.isFavorite) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.scale(heartScale).size(20.dp)
      )
    }
    if (onDeleteClick != null) {
      IconButton(
        onClick = onDeleteClick,
        modifier = Modifier
          .minimumInteractiveComponentSize()
          .size(40.dp)
          .testTag("song_delete_button_${song.id}")
      ) {
        Icon(
          imageVector = Icons.Rounded.DeleteOutline,
          contentDescription = "Delete song",
          tint = MaterialTheme.colorScheme.error.copy(alpha = 0.85f),
          modifier = Modifier.size(20.dp)
        )
      }
    }
  }
}

@Composable
fun SongDownloadRow(
  song: Song,
  index: Int,
  isPlaying: Boolean = false,
  isCurrent: Boolean = false,
  isResolving: Boolean = false,
  reducedMotion: Boolean = false,
  darkTheme: Boolean = true,
  hasLyrics: Boolean = false,
  downloadProgress: Float? = null,
  isDownloaded: Boolean = false,
  ambientPrimaryColor: Color? = null,
  ambientSecondaryColor: Color? = null,
  onCheckLyrics: ((Song) -> Unit)? = null,
  onSongClick: () -> Unit,
  onFavoriteClick: () -> Unit,
  onDownloadClick: () -> Unit = {},
  modifier: Modifier = Modifier
) {
  LaunchedEffect(song.id) {
    onCheckLyrics?.invoke(song)
  }

  val rowInteractionSource = remember { MutableInteractionSource() }
  val isRowPressed by rowInteractionSource.collectIsPressedAsState()
  val isRowHovered by rowInteractionSource.collectIsHoveredAsState()

  val dlInteractionSource = remember { MutableInteractionSource() }
  val isDlPressed by dlInteractionSource.collectIsPressedAsState()

  val favInteractionSource = remember { MutableInteractionSource() }
  val isFavPressed by favInteractionSource.collectIsPressedAsState()

  val playInteractionSource = remember { MutableInteractionSource() }
  val isPlayPressed by playInteractionSource.collectIsPressedAsState()

  val rowScale by animateFloatAsState(
    targetValue = when {
      isRowPressed -> 0.978f
      isRowHovered -> 1.008f
      else -> 1f
    },
    animationSpec = if (reducedMotion) snap() else spring(
      dampingRatio = 0.72f,
      stiffness = Spring.StiffnessMediumLow
    ),
    label = "song_download_row_scale"
  )
  val glassLuminance by animateFloatAsState(
    targetValue = when {
      isRowPressed -> 1f
      isCurrent -> 0.85f
      isRowHovered -> 0.50f
      else -> 0f
    },
    animationSpec = if (reducedMotion) snap() else spring(
      dampingRatio = 0.86f,
      stiffness = Spring.StiffnessMediumLow
    ),
    label = "song_download_row_luminance"
  )
  val artScale by animateFloatAsState(
    targetValue = if (isRowPressed || isCurrent) 1.045f else 1f,
    animationSpec = if (reducedMotion) snap() else spring(
      dampingRatio = 0.68f,
      stiffness = Spring.StiffnessMedium
    ),
    label = "song_download_art_scale"
  )
  val dlScale by animateFloatAsState(
    targetValue = when {
      isDlPressed -> 0.86f
      isDownloaded -> 1.08f
      else -> 1f
    },
    animationSpec = if (reducedMotion) snap() else spring(
      dampingRatio = Spring.DampingRatioMediumBouncy,
      stiffness = Spring.StiffnessMedium
    ),
    label = "song_download_btn_scale"
  )
  val favScale by animateFloatAsState(
    targetValue = when {
      isFavPressed -> 0.84f
      song.isFavorite -> 1.16f
      else -> 1f
    },
    animationSpec = if (reducedMotion) snap() else spring(
      dampingRatio = Spring.DampingRatioMediumBouncy,
      stiffness = Spring.StiffnessMedium
    ),
    label = "song_download_fav_scale"
  )
  val playScale by animateFloatAsState(
    targetValue = when {
      isPlayPressed -> 0.88f
      isCurrent && isPlaying -> 1.05f
      else -> 1f
    },
    animationSpec = if (reducedMotion) snap() else spring(
      dampingRatio = 0.62f,
      stiffness = Spring.StiffnessMedium
    ),
    label = "song_download_play_scale"
  )

  val rowShape = RoundedCornerShape(22.dp)
  val accentColor = MaterialTheme.colorScheme.primary
  val rimTint = ambientPrimaryColor ?: accentColor
  val secondaryTint = ambientSecondaryColor ?: rimTint
  val glassBrush = when {
    isCurrent -> Brush.linearGradient(
      colors = listOf(
        rimTint.copy(alpha = if (darkTheme) 0.30f else 0.18f),
        secondaryTint.copy(alpha = if (darkTheme) 0.18f else 0.10f),
        if (darkTheme) Color(0xFF162036).copy(alpha = 0.78f) else Color.White.copy(alpha = 0.86f)
      )
    )
    ambientPrimaryColor != null && darkTheme -> Brush.linearGradient(
      colors = listOf(
        ambientPrimaryColor.copy(alpha = 0.16f + 0.06f * glassLuminance),
        secondaryTint.copy(alpha = 0.10f + 0.05f * glassLuminance),
        Color(0xFF131B2E).copy(alpha = 0.64f + 0.08f * glassLuminance)
      )
    )
    ambientPrimaryColor != null && !darkTheme -> Brush.linearGradient(
      colors = listOf(
        Color.White.copy(alpha = (0.82f + 0.08f * glassLuminance).coerceAtMost(1f)),
        ambientPrimaryColor.copy(alpha = 0.09f + 0.04f * glassLuminance),
        Color(0xFFF2F6FF).copy(alpha = (0.74f + 0.10f * glassLuminance).coerceAtMost(1f))
      )
    )
    darkTheme -> Brush.linearGradient(
      colors = listOf(
        Color(0xFF1D2840).copy(alpha = 0.76f + 0.08f * glassLuminance),
        Color(0xFF131B2E).copy(alpha = 0.68f + 0.08f * glassLuminance)
      )
    )
    else -> Brush.linearGradient(
      colors = listOf(
        Color.White.copy(alpha = (0.88f + 0.08f * glassLuminance).coerceAtMost(1f)),
        Color(0xFFF2F6FF).copy(alpha = (0.78f + 0.10f * glassLuminance).coerceAtMost(1f))
      )
    )
  }
  val borderBrush = Brush.verticalGradient(
    colors = if (isCurrent) {
      listOf(
        rimTint.copy(alpha = 0.60f + 0.20f * glassLuminance),
        secondaryTint.copy(alpha = 0.22f + 0.12f * glassLuminance)
      )
    } else if (darkTheme) {
      listOf(
        (ambientPrimaryColor ?: Color.White).copy(alpha = 0.28f + 0.16f * glassLuminance),
        Color.White.copy(alpha = 0.08f + 0.10f * glassLuminance)
      )
    } else {
      listOf(
        Color.White,
        (ambientPrimaryColor ?: Color.White).copy(alpha = 0.45f + 0.25f * glassLuminance)
      )
    }
  )

  Box(
    modifier = modifier
      .fillMaxWidth()
      .padding(vertical = 3.dp)
      .graphicsLayer {
        scaleX = rowScale
        scaleY = rowScale
      }
      .clip(rowShape)
      .background(glassBrush)
      .background(Color.White.copy(alpha = (if (darkTheme) 0.06f else 0.16f) * glassLuminance))
      .border(1.dp, borderBrush, rowShape)
      .drawBehind {
        val stroke = 1.2.dp.toPx()
        val inset = stroke * 0.5f
        val r = (22.dp.toPx() - inset).coerceAtLeast(0f)
        drawRoundRect(
          brush = Brush.verticalGradient(
            colors = listOf(
              Color.White.copy(
                alpha = (if (darkTheme) 0.28f else 0.92f) + (if (darkTheme) 0.16f else 0.08f) * glassLuminance
              ),
              Color.Transparent
            ),
            startY = 0f,
            endY = size.height * 0.42f
          ),
          topLeft = Offset(inset, inset),
          size = Size(size.width - stroke, size.height - stroke),
          cornerRadius = CornerRadius(r, r),
          style = Stroke(width = stroke)
        )
      }
      .hoverable(rowInteractionSource)
      .clickable(
        interactionSource = rowInteractionSource,
        indication = LocalIndication.current
      ) { onSongClick() }
      .testTag("song_row_${song.id}")
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 12.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      if (isCurrent) {
        EqualizerBars(
          isPlaying = isPlaying,
          color = accentColor,
          reducedMotion = reducedMotion,
          modifier = Modifier.width(24.dp)
        )
      } else {
        Text(
          text = String.format("%02d", index),
          style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.width(24.dp)
        )
      }

      Box(
        modifier = Modifier
          .size(50.dp)
          .graphicsLayer {
            scaleX = artScale
            scaleY = artScale
          }
          .clip(RoundedCornerShape(13.dp))
          .border(1.dp, Color.White.copy(alpha = if (darkTheme) 0.24f else 0.8f), RoundedCornerShape(13.dp))
      ) {
        SongArtwork(song = song, modifier = Modifier.fillMaxSize(), cornerRadius = 13.dp)
      }

      Spacer(modifier = Modifier.width(12.dp))
      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(
            text = song.title,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = if (isCurrent) accentColor else MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f, fill = false)
          )
          LyricsTag(
            visible = hasLyrics,
            size = LyricsTagSize.Standard,
            darkTheme = darkTheme,
            reducedMotion = reducedMotion,
            modifier = Modifier.padding(start = 6.dp)
          )
        }
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          Text(
            text = song.artist,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f, fill = false)
          )
          if (song.source == "YouTube") {
            val durStr = if (song.durationSeconds > 0L) {
              "${song.durationSeconds / 60}:${String.format("%02d", song.durationSeconds % 60)}"
            } else {
              ""
            }
            Surface(
              shape = RoundedCornerShape(6.dp),
              color = Color(0xFFEF4444).copy(alpha = if (darkTheme) 0.20f else 0.14f)
            ) {
              Text(
                text = if (durStr.isNotBlank()) "YT • $durStr" else "YT",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp),
                color = if (darkTheme) Color(0xFFFF8A8A) else Color(0xFFDC2626),
                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
              )
            }
          } else if (song.source == "iTunes 30s") {
            Surface(
              shape = RoundedCornerShape(6.dp),
              color = accentColor.copy(alpha = 0.16f)
            ) {
              Text(
                text = "30s",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp),
                color = accentColor,
                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
              )
            }
          }
        }
      }

      IconButton(
        onClick = onDownloadClick,
        interactionSource = dlInteractionSource,
        modifier = Modifier
          .minimumInteractiveComponentSize()
          .testTag("song_download_button_${song.id}")
      ) {
        if (downloadProgress != null) {
          CircularProgressIndicator(
            progress = { downloadProgress.coerceIn(0f, 1f) },
            modifier = Modifier.size(20.dp),
            strokeWidth = 2.5.dp,
            color = accentColor
          )
        } else {
          Icon(
            imageVector = if (isDownloaded) Icons.Rounded.DownloadDone else Icons.Rounded.Download,
            contentDescription = if (song.source == "YouTube") "Install / Select format" else "Download Preview",
            tint = if (isDownloaded) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.graphicsLayer {
              scaleX = dlScale
              scaleY = dlScale
            }
          )
        }
      }

      IconButton(
        onClick = onFavoriteClick,
        interactionSource = favInteractionSource,
        modifier = Modifier.minimumInteractiveComponentSize()
      ) {
        Icon(
          imageVector = if (song.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
          contentDescription = "Favorite",
          tint = if (song.isFavorite) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.graphicsLayer {
            scaleX = favScale
            scaleY = favScale
          }
        )
      }

      Box(
        modifier = Modifier
          .size(40.dp)
          .graphicsLayer {
            scaleX = playScale
            scaleY = playScale
          }
          .clip(CircleShape)
          .background(
            if (isCurrent) accentColor
            else if (darkTheme) Color.White.copy(alpha = 0.12f)
            else Color.Black.copy(alpha = 0.06f)
          )
          .border(
            1.dp,
            Color.White.copy(alpha = if (isCurrent) 0.45f else if (darkTheme) 0.18f else 0.7f),
            CircleShape
          )
          .clickable(
            interactionSource = playInteractionSource,
            indication = LocalIndication.current
          ) { onSongClick() }
          .testTag("song_play_button_${song.id}"),
        contentAlignment = Alignment.Center
      ) {
        if (isResolving) {
          CircularProgressIndicator(
            modifier = Modifier.size(16.dp),
            strokeWidth = 2.dp,
            color = if (isCurrent) Color.White else accentColor
          )
        } else {
          Icon(
            imageVector = if (isCurrent && isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
            contentDescription = "Play",
            tint = if (isCurrent) Color.White else MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.size(20.dp)
          )
        }
      }
    }
  }
}
