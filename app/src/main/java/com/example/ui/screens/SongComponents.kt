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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
        val drawable = (result as? SuccessResult)?.drawable ?: return@withContext null
        val rawBitmap = runCatching { drawable.toBitmap() }.getOrNull() ?: return@withContext null
        val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && rawBitmap.config == Bitmap.Config.HARDWARE) {
          runCatching { rawBitmap.copy(Bitmap.Config.ARGB_8888, false) }.getOrNull() ?: rawBitmap
        } else {
          rawBitmap
        }
        withContext(Dispatchers.Default) {
          runCatching { extractVibrantPair(bitmap) }.getOrNull()
        }
      } catch (ce: CancellationException) {
        throw ce
      } catch (_: Throwable) {
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
  val context = LocalContext.current
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
    when {
      song.artworkUrl == APP_ICON_ARTWORK -> {
        Image(
          painter = painterResource(id = R.drawable.ic_launcher_foreground),
          contentDescription = song.title,
          contentScale = ContentScale.Fit,
          modifier = Modifier.fillMaxSize(0.86f)
        )
      }
      song.artworkUrl.isNotBlank() -> {
        val imageRequest = remember(context, song.artworkUrl) {
          ImageRequest.Builder(context)
            .data(song.artworkUrl)
            .size(240)
            .crossfade(false)
            .build()
        }
        AsyncImage(
          model = imageRequest,
          contentDescription = song.title,
          contentScale = ContentScale.Crop,
          alignment = Alignment.Center,
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

@Immutable
data class DynamicBgCustomization(
  val dimLevel: Float = com.example.data.PreferencesManager.DEFAULT_DYNAMIC_BG_DIM,
  val glassLevel: Float = com.example.data.PreferencesManager.DEFAULT_DYNAMIC_BG_GLASS,
  val blurRadiusDp: Float = com.example.data.PreferencesManager.DEFAULT_DYNAMIC_BG_BLUR
)

val LocalDynamicBgCustomization = compositionLocalOf { DynamicBgCustomization() }

@Composable
fun GlassCard(
  modifier: Modifier = Modifier,
  darkTheme: Boolean,
  cornerRadius: Dp = 24.dp,
  content: @Composable ColumnScope.() -> Unit
) {
  val custom = LocalDynamicBgCustomization.current
  val glassScale = (custom.glassLevel / com.example.data.PreferencesManager.DEFAULT_DYNAMIC_BG_GLASS)
    .coerceIn(0.28f, 1.32f)
  val shape = RoundedCornerShape(cornerRadius)
  val glassBrush = if (darkTheme) {
    Brush.linearGradient(
      colors = listOf(
        Color(0xFF212D46).copy(alpha = (0.78f * glassScale).coerceIn(0.18f, 0.96f)),
        Color(0xFF151E32).copy(alpha = (0.68f * glassScale).coerceIn(0.15f, 0.92f)),
        Color(0xFF101728).copy(alpha = (0.76f * glassScale).coerceIn(0.18f, 0.95f))
      )
    )
  } else {
    Brush.linearGradient(
      colors = listOf(
        Color.White.copy(alpha = (0.90f * glassScale).coerceIn(0.24f, 0.98f)),
        Color(0xFFF3F6FF).copy(alpha = (0.76f * glassScale).coerceIn(0.20f, 0.94f)),
        Color.White.copy(alpha = (0.84f * glassScale).coerceIn(0.22f, 0.96f))
      )
    )
  }
  val borderBrush = Brush.verticalGradient(
    colors = if (darkTheme) {
      listOf(
        Color.White.copy(alpha = (0.32f * glassScale).coerceIn(0.10f, 0.52f)),
        Color.White.copy(alpha = (0.12f * glassScale).coerceIn(0.04f, 0.24f)),
        Color.White.copy(alpha = (0.05f * glassScale).coerceIn(0.02f, 0.12f))
      )
    } else {
      listOf(
        Color.White.copy(alpha = (1.0f * glassScale).coerceIn(0.35f, 1.0f)),
        Color.White.copy(alpha = (0.68f * glassScale).coerceIn(0.22f, 0.90f)),
        Color.White.copy(alpha = (0.42f * glassScale).coerceIn(0.14f, 0.65f))
      )
    }
  )
  val topSpecular = Color.White.copy(
    alpha = ((if (darkTheme) 0.42f else 0.98f) * glassScale).coerceIn(0.12f, 1.0f)
  )

  Box(
    modifier = modifier
      .drawBehind {
        if (size.width <= 0f || size.height <= 0f) return@drawBehind
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
        if (size.width > stroke15 && size.height > stroke15) {
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
  if (options.isEmpty()) return
  val custom = LocalDynamicBgCustomization.current
  val glassScale = (custom.glassLevel / com.example.data.PreferencesManager.DEFAULT_DYNAMIC_BG_GLASS)
    .coerceIn(0.32f, 1.30f)
  val barBg = if (darkTheme) {
    Color(0xFF151E32).copy(alpha = (0.72f * glassScale).coerceIn(0.22f, 0.94f))
  } else {
    Color.White.copy(alpha = (0.74f * glassScale).coerceIn(0.26f, 0.96f))
  }
  val barBorder = if (darkTheme) {
    Color.White.copy(alpha = (0.22f * glassScale).coerceIn(0.08f, 0.38f))
  } else {
    Color.White.copy(alpha = (0.96f * glassScale).coerceIn(0.36f, 1.0f))
  }
  val primaryAccent = MaterialTheme.colorScheme.primary
  val pillBrush = if (darkTheme) {
    Brush.linearGradient(
      colors = listOf(
        Color(0xFF33466B).copy(alpha = (0.95f * glassScale).coerceIn(0.35f, 0.98f)),
        primaryAccent.copy(alpha = 0.34f),
        Color(0xFF24324E).copy(alpha = (0.96f * glassScale).coerceIn(0.36f, 0.98f))
      )
    )
  } else {
    Brush.linearGradient(
      colors = listOf(
        Color.White.copy(alpha = (0.98f * glassScale).coerceIn(0.45f, 1.0f)),
        primaryAccent.copy(alpha = 0.12f),
        Color.White.copy(alpha = (0.95f * glassScale).coerceIn(0.42f, 0.99f))
      )
    )
  }
  val pillBorderBrush = Brush.verticalGradient(
    colors = listOf(
      Color.White.copy(alpha = if (darkTheme) 0.42f else 1.0f),
      primaryAccent.copy(alpha = if (darkTheme) 0.32f else 0.24f)
    )
  )
  val clampedIndex = selectedIndex.coerceIn(0, options.lastIndex)
  val gap = 4.dp
  val outerPad = 4.dp
  val colorSpec = if (reducedMotion) snap<Color>() else tween<Color>(durationMillis = 220, easing = FastOutSlowInEasing)

  BoxWithConstraints(
    modifier = modifier
      .fillMaxWidth()
      .clip(CircleShape)
      .background(barBg)
      .border(1.dp, barBorder, CircleShape)
      .padding(outerPad)
  ) {
    val count = options.size
    val totalGap = gap * (count - 1).coerceAtLeast(0)
    val slotWidth = ((maxWidth - totalGap) / count).coerceAtLeast(1.dp)
    val targetOffset = (slotWidth + gap) * clampedIndex
    val pillOffset by animateDpAsState(
      targetValue = targetOffset,
      animationSpec = if (reducedMotion) {
        snap()
      } else {
        spring(
          dampingRatio = 0.78f,
          stiffness = 460f
        )
      },
      label = "seg_pill_offset"
    )

    Box(
      modifier = Modifier
        .fillMaxWidth()
        .height(IntrinsicSize.Min)
    ) {
      // Sliding Frosted Liquid Glass Pill Indicator
      Box(
        modifier = Modifier
          .offset(x = pillOffset)
          .width(slotWidth)
          .fillMaxHeight()
          .clip(CircleShape)
          .background(pillBrush)
          .border(0.9.dp, pillBorderBrush, CircleShape)
      )

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(gap),
        verticalAlignment = Alignment.CenterVertically
      ) {
        options.forEachIndexed { index, (label, icon) ->
          val selected = clampedIndex == index
          val tag = testTags.getOrNull(index)
          val animatedContentColor by animateColorAsState(
            targetValue = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            animationSpec = colorSpec,
            label = "seg_text_$index"
          )
          val animatedIconColor by animateColorAsState(
            targetValue = if (selected) primaryAccent else MaterialTheme.colorScheme.onSurfaceVariant,
            animationSpec = colorSpec,
            label = "seg_icon_$index"
          )
          val itemScale by animateFloatAsState(
            targetValue = if (selected) 1.0f else 0.96f,
            animationSpec = if (reducedMotion) snap() else spring(dampingRatio = 0.8f, stiffness = 500f),
            label = "seg_scale_$index"
          )

          Row(
            modifier = Modifier
              .weight(1f)
              .defaultMinSize(minHeight = 48.dp)
              .clip(CircleShape)
              .clickable { onSelect(index) }
              .then(if (tag != null) Modifier.testTag(tag) else Modifier)
              .graphicsLayer {
                scaleX = itemScale
                scaleY = itemScale
              }
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
  val context = androidx.compose.ui.platform.LocalContext.current
  val isInspection = androidx.compose.ui.platform.LocalInspectionMode.current
  val activeIcon = remember(context, isInspection) {
    if (isInspection) com.example.data.OrbiAppIcon.CLASSIC
    else com.example.data.IconManager.getCurrentIcon(context)
  }

  OrbiLauncherIconTile(
    icon = activeIcon,
    size = size,
    cornerRadius = cornerRadius,
    animated = animated,
    reducedMotion = reducedMotion,
    modifier = modifier
  )
}

@Composable
fun DynamicArtworkBackdrop(
  song: Song?,
  enabled: Boolean,
  darkTheme: Boolean,
  reducedMotion: Boolean,
  modifier: Modifier = Modifier
) {
  val custom = LocalDynamicBgCustomization.current
  val effectiveBlurDp = custom.blurRadiusDp.coerceIn(12f, 100f).dp
  val dimLevel = custom.dimLevel.coerceIn(0.05f, 0.90f)
  // Scale dim factor relative to default 0.45f so user adjustments visibly brighten or darken the ambient glow
  val dimRatio = (dimLevel / com.example.data.PreferencesManager.DEFAULT_DYNAMIC_BG_DIM).coerceIn(0.12f, 2.0f)
  val glowBoost = (1.18f - (dimLevel - 0.25f) * 0.55f).coerceIn(0.45f, 1.35f)

  val accentColor = MaterialTheme.colorScheme.primary
  val targetAlpha = if (enabled && song != null) (if (darkTheme) 0.84f else 0.62f) else 0f
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
            colors = listOf(accentColor.copy(alpha = (0.20f * glowBoost).coerceIn(0.06f, 0.34f)), Color.Transparent),
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
              .blur(effectiveBlurDp)
          )
        }
        Box(
          modifier = Modifier
            .fillMaxSize()
            .background(
              Brush.radialGradient(
                colors = listOf(
                  c1.copy(alpha = ((if (darkTheme) 0.56f else 0.42f) * glowBoost).coerceIn(0.14f, 0.78f)),
                  c2.copy(alpha = ((if (darkTheme) 0.36f else 0.28f) * glowBoost).coerceIn(0.10f, 0.56f)),
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
                    Color(0xFF0B0F1A).copy(alpha = (0.36f * dimRatio).coerceIn(0.05f, 0.88f)),
                    Color(0xFF0B0F1A).copy(alpha = (0.50f * dimRatio).coerceIn(0.08f, 0.92f)),
                    Color(0xFF080B14).copy(alpha = (0.68f * dimRatio).coerceIn(0.12f, 0.96f))
                  )
                } else {
                  listOf(
                    Color(0xFFF3F6FF).copy(alpha = (0.42f * dimRatio).coerceIn(0.06f, 0.88f)),
                    Color(0xFFF3F6FF).copy(alpha = (0.52f * dimRatio).coerceIn(0.10f, 0.92f)),
                    Color(0xFFEBF0FF).copy(alpha = (0.62f * dimRatio).coerceIn(0.14f, 0.95f))
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
  val custom = LocalDynamicBgCustomization.current
  val blurScale = (custom.blurRadiusDp / com.example.data.PreferencesManager.DEFAULT_DYNAMIC_BG_BLUR).coerceIn(0.25f, 1.5f)
  val glassScale = (custom.glassLevel / com.example.data.PreferencesManager.DEFAULT_DYNAMIC_BG_GLASS).coerceIn(0.30f, 1.30f)
  val effectiveBlurRadius = (blurRadius.value * blurScale).coerceIn(6f, 80f).dp
  val effectiveScrimAlpha = (scrimAlpha * glassScale).coerceIn(0.18f, 0.94f)
  val layerAlpha by animateFloatAsState(
    targetValue = if (enabled && song != null) 1f else 0f,
    animationSpec = if (reducedMotion) snap() else tween(450),
    label = "dynamic_glass_layer_alpha"
  )
  val (c1, c2) = artColors

  val context = LocalContext.current
  Box(modifier = modifier) {
    if (layerAlpha > 0.01f && song != null) {
      Box(modifier = Modifier.matchParentSize().alpha(layerAlpha)) {
        val model = resolveArtworkModel(song.artworkUrl, song.source)
        if (model != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
          val blurReq = remember(context, model) {
            ImageRequest.Builder(context)
              .data(model)
              .size(96)
              .crossfade(false)
              .build()
          }
          AsyncImage(
            model = blurReq,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
              .matchParentSize()
              .scale(1.18f)
              .alpha(artAlpha)
              .then(if (effectiveBlurRadius > 0.dp) Modifier.blur(effectiveBlurRadius) else Modifier)
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
          if (darkTheme) Color(0xFF0C1222).copy(alpha = effectiveScrimAlpha)
          else Color.White.copy(alpha = effectiveScrimAlpha)
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

@OptIn(ExperimentalFoundationApi::class)
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
  genreBadge: String? = null,
  onGenreBadgeClick: (() -> Unit)? = null,
  onCheckLyrics: ((Song) -> Unit)? = null,
  onSongClick: () -> Unit,
  onFavoriteClick: () -> Unit,
  onAddToAlbumClick: (() -> Unit)? = null,
  onEditPhotoClick: (() -> Unit)? = null,
  onDeleteClick: (() -> Unit)? = null,
  modifier: Modifier = Modifier
) {
  StandaloneSongCard(
    song = song,
    isPlaying = isPlaying,
    isCurrent = isCurrent,
    isResolving = false,
    reducedMotion = reducedMotion,
    darkTheme = darkTheme,
    downloadProgress = null,
    isDownloaded = false,
    genreBadge = genreBadge,
    onGenreBadgeClick = onGenreBadgeClick,
    onCheckLyrics = onCheckLyrics,
    onSongClick = onSongClick,
    onFavoriteClick = onFavoriteClick,
    onDownloadClick = null,
    onAddToAlbumClick = onAddToAlbumClick,
    onEditPhotoClick = onEditPhotoClick,
    onDeleteClick = onDeleteClick,
    modifier = modifier
  )
}

/**
 * Clean Frameless Library Track Row matching the reference Library layout:
 * - No heavy outer glass pill card container.
 * - Left: 52dp rounded-square cover artwork (14dp corner radius) with active equalizer overlay.
 * - Center: Bold single-line track title + secondary artist/duration + inline genre badge pill.
 * - Right: Compact favorite heart + circular action button (36dp circle) matching the right circle in the photo.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryFramelessTrackRow(
  song: Song,
  isPlaying: Boolean = false,
  isCurrent: Boolean = false,
  reducedMotion: Boolean = false,
  darkTheme: Boolean = true,
  hasLyrics: Boolean = false,
  genreBadge: String? = null,
  onGenreBadgeClick: (() -> Unit)? = null,
  onCheckLyrics: ((Song) -> Unit)? = null,
  onSongClick: () -> Unit,
  onFavoriteClick: () -> Unit,
  onAddToAlbumClick: (() -> Unit)? = null,
  onEditPhotoClick: (() -> Unit)? = null,
  onDeleteClick: (() -> Unit)? = null,
  modifier: Modifier = Modifier
) {
  LaunchedEffect(song.id) {
    if (onCheckLyrics != null) {
      delay(350L)
      onCheckLyrics.invoke(song)
    }
  }

  var showContextMenu by remember(song.id) { mutableStateOf(false) }
  val accentColor = MaterialTheme.colorScheme.primary
  val hasMenuActions = onAddToAlbumClick != null || onEditPhotoClick != null || onDeleteClick != null

  val heartScale by animateFloatAsState(
    targetValue = if (song.isFavorite) 1.15f else 1.0f,
    animationSpec = if (reducedMotion) snap() else spring(dampingRatio = Spring.DampingRatioMediumBouncy),
    label = "frameless_heart_pop"
  )

  val activeTintBg = when {
    isCurrent && darkTheme -> accentColor.copy(alpha = 0.12f)
    isCurrent -> accentColor.copy(alpha = 0.08f)
    else -> Color.Transparent
  }

  Row(
    modifier = modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(18.dp))
      .background(activeTintBg)
      .combinedClickable(
        onClick = onSongClick,
        onLongClick = { if (hasMenuActions) showContextMenu = true }
      )
      .padding(horizontal = 6.dp, vertical = 7.dp)
      .testTag("song_row_${song.id}"),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    // 52dp rounded-square cover art (14dp corner radius) matching the reference screenshot
    Box {
      Box(
        modifier = Modifier
          .size(50.dp)
          .clip(RoundedCornerShape(14.dp))
          .border(
            width = if (isCurrent) 1.4.dp else 0.8.dp,
            color = if (isCurrent) accentColor.copy(alpha = 0.78f)
            else Color.White.copy(alpha = if (darkTheme) 0.14f else 0.72f),
            shape = RoundedCornerShape(14.dp)
          ),
        contentAlignment = Alignment.Center
      ) {
        SongArtwork(song = song, modifier = Modifier.fillMaxSize(), cornerRadius = 14.dp)
        if (isCurrent) {
          Box(
            modifier = Modifier
              .fillMaxSize()
              .background(Color.Black.copy(alpha = 0.40f)),
            contentAlignment = Alignment.Center
          ) {
            EqualizerBars(
              isPlaying = isPlaying,
              color = accentColor,
              reducedMotion = reducedMotion,
              barWidth = 2.8.dp,
              maxHeight = 13.dp
            )
          }
        }
      }

      if (hasMenuActions) {
        LibrarySongGlassMenu(
          expanded = showContextMenu,
          reducedMotion = reducedMotion,
          onDismissRequest = { showContextMenu = false },
          onAddToAlbumClick = onAddToAlbumClick,
          onEditPhotoClick = onEditPhotoClick,
          onDeleteClick = onDeleteClick
        )
      }
    }

    // Center: Two clean text lines (Title + Artist/Genre/Duration)
    Column(
      modifier = Modifier.weight(1f),
      verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
      Text(
        text = song.title,
        style = MaterialTheme.typography.bodyMedium.copy(
          fontSize = 15.sp,
          lineHeight = 19.sp,
          fontWeight = FontWeight.Bold
        ),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        color = if (isCurrent) accentColor else MaterialTheme.colorScheme.onBackground
      )

      val durationStr = if (song.durationSeconds > 0L) {
        "${song.durationSeconds / 60}:${String.format("%02d", song.durationSeconds % 60)}"
      } else ""
      val subtitleText = if (durationStr.isNotBlank()) "${song.artist} · $durationStr" else song.artist

      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        if (!genreBadge.isNullOrBlank()) {
          Box(
            modifier = Modifier
              .clip(CircleShape)
              .background(accentColor.copy(alpha = if (darkTheme) 0.15f else 0.11f))
              .border(
                width = 0.7.dp,
                color = accentColor.copy(alpha = if (darkTheme) 0.38f else 0.28f),
                shape = CircleShape
              )
              .then(
                if (onGenreBadgeClick != null) Modifier.clickable { onGenreBadgeClick() }
                else Modifier
              )
              .padding(horizontal = 6.5.dp, vertical = 1.dp)
              .testTag("song_genre_badge_${song.id}"),
            contentAlignment = Alignment.Center
          ) {
            Text(
              text = genreBadge,
              style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 9.5.sp,
                lineHeight = 12.sp,
                fontWeight = FontWeight.SemiBold
              ),
              color = if (darkTheme) {
                androidx.compose.ui.graphics.lerp(accentColor, Color.White, 0.28f)
              } else {
                accentColor
              },
              maxLines = 1
            )
          }
        }

        Text(
          text = subtitleText,
          style = MaterialTheme.typography.bodySmall.copy(
            fontSize = 12.5.sp,
            lineHeight = 16.sp
          ),
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.weight(1f, fill = false)
        )
      }
    }

    // Right: Subtle favorite heart + clean circular play/action pill matching the reference photo
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
      Box(
        modifier = Modifier
          .size(32.dp)
          .clip(CircleShape)
          .clickable { onFavoriteClick() }
          .testTag("song_favorite_button_${song.id}"),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = if (song.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
          contentDescription = "Favorite",
          tint = if (song.isFavorite) {
            MaterialTheme.colorScheme.error
          } else if (darkTheme) {
            Color.White.copy(alpha = 0.55f)
          } else {
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.70f)
          },
          modifier = Modifier
            .scale(heartScale)
            .size(18.dp)
        )
      }

      Surface(
        shape = CircleShape,
        color = when {
          isCurrent -> accentColor.copy(alpha = 0.22f)
          darkTheme -> Color.White.copy(alpha = 0.10f)
          else -> Color(0xFF94A3B8).copy(alpha = 0.18f)
        },
        border = BorderStroke(
          0.9.dp,
          when {
            isCurrent -> accentColor.copy(alpha = 0.55f)
            darkTheme -> Color.White.copy(alpha = 0.12f)
            else -> Color(0xFF64748B).copy(alpha = 0.14f)
          }
        ),
        modifier = Modifier
          .size(34.dp)
          .clip(CircleShape)
          .clickable { onSongClick() }
          .testTag("song_play_button_${song.id}")
      ) {
        Box(contentAlignment = Alignment.Center) {
          Icon(
            imageVector = if (isCurrent && isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
            contentDescription = if (isCurrent && isPlaying) "Pause" else "Play",
            tint = if (isCurrent) accentColor else if (darkTheme) Color.White.copy(alpha = 0.90f) else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(18.dp)
          )
        }
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
  StandaloneSongCard(
    song = song,
    isPlaying = isPlaying,
    isCurrent = isCurrent,
    isResolving = isResolving,
    reducedMotion = reducedMotion,
    darkTheme = darkTheme,
    downloadProgress = downloadProgress,
    isDownloaded = isDownloaded,
    onCheckLyrics = onCheckLyrics,
    onSongClick = onSongClick,
    onFavoriteClick = onFavoriteClick,
    onDownloadClick = onDownloadClick,
    onAddToAlbumClick = null,
    onEditPhotoClick = null,
    onDeleteClick = null,
    modifier = modifier
  )
}

internal fun Modifier.standaloneNavyCardSurface(
  darkTheme: Boolean,
  isCurrent: Boolean = false,
  accentColor: Color = AurixTealAccent,
  cornerRadius: Dp = 34.dp
): Modifier {
  val cardBg = if (darkTheme) {
    Brush.horizontalGradient(
      listOf(
        Color(0xFF1A253D).copy(alpha = 0.68f),
        Color(0xFF10182B).copy(alpha = 0.62f)
      )
    )
  } else {
    Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.90f), Color(0xFFF3F6FF).copy(alpha = 0.86f)))
  }
  val glassRimBrush = if (darkTheme) {
    Brush.verticalGradient(
      0.0f to Color(0xFFE3ECFF).copy(alpha = 0.56f),
      0.32f to Color.White.copy(alpha = 0.16f),
      0.72f to Color.White.copy(alpha = 0.14f),
      1.0f to Color(0xFFC2D0EC).copy(alpha = 0.30f)
    )
  } else {
    Brush.verticalGradient(
      0.0f to Color.White,
      0.5f to Color(0xFFCBD5E1).copy(alpha = 0.55f),
      1.0f to Color.White.copy(alpha = 0.85f)
    )
  }
  val softShadowColor = if (darkTheme) {
    Color(0xFF040814).copy(alpha = 0.44f)
  } else {
    Color(0xFF64748B).copy(alpha = 0.14f)
  }

  return this
    .graphicsLayer { clip = false }
    .drawBehind {
      if (size.width <= 0f || size.height <= 0f) return@drawBehind
      val cornerRadiusPx = cornerRadius.toPx().coerceAtMost(minOf(size.width, size.height) / 2f)
      val shadowOffsetY = 4.dp.toPx()

      // Soft ambient drop shadow below pill box
      drawRoundRect(
        color = softShadowColor,
        topLeft = Offset(0f, shadowOffsetY),
        size = size,
        cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx)
      )

      // Translucent dark-navy glass fill
      drawRoundRect(
        brush = cardBg,
        topLeft = Offset.Zero,
        size = size,
        cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx)
      )

      // Continuous crisp liquid-glass specular rim around entire pill
      val rimStrokePx = 1.15.dp.toPx()
      val rimInset = rimStrokePx * 0.5f
      if (size.width > rimStrokePx * 2f && size.height > rimStrokePx * 2f) {
        drawRoundRect(
          brush = glassRimBrush,
          topLeft = Offset(rimInset, rimInset),
          size = Size(size.width - rimStrokePx, size.height - rimStrokePx),
          cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx),
          style = Stroke(width = rimStrokePx)
        )
      }

      if (isCurrent) {
        val activeStrokePx = 1.5.dp.toPx()
        if (size.width > activeStrokePx * 2f && size.height > activeStrokePx * 2f) {
          drawRoundRect(
            color = accentColor.copy(alpha = 0.72f),
            topLeft = Offset(activeStrokePx * 0.5f, activeStrokePx * 0.5f),
            size = Size(size.width - activeStrokePx, size.height - activeStrokePx),
            cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx),
            style = Stroke(width = activeStrokePx)
          )
        }
      }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StandaloneSongCard(
  song: Song,
  isPlaying: Boolean,
  isCurrent: Boolean,
  isResolving: Boolean,
  reducedMotion: Boolean,
  darkTheme: Boolean,
  downloadProgress: Float?,
  isDownloaded: Boolean,
  genreBadge: String? = null,
  onGenreBadgeClick: (() -> Unit)? = null,
  onCheckLyrics: ((Song) -> Unit)?,
  onSongClick: () -> Unit,
  onFavoriteClick: () -> Unit,
  onDownloadClick: (() -> Unit)?,
  onAddToAlbumClick: (() -> Unit)?,
  onEditPhotoClick: (() -> Unit)?,
  onDeleteClick: (() -> Unit)?,
  modifier: Modifier = Modifier
) {
  LaunchedEffect(song.id) {
    if (onCheckLyrics != null) {
      delay(350L)
      onCheckLyrics.invoke(song)
    }
  }

  var showContextMenu by remember(song.id) { mutableStateOf(false) }

  val heartScale by animateFloatAsState(
    targetValue = if (song.isFavorite) 1.15f else 1.0f,
    animationSpec = if (reducedMotion) snap() else spring(dampingRatio = Spring.DampingRatioMediumBouncy),
    label = "heart_pop"
  )

  val accentColor = MaterialTheme.colorScheme.primary
  val hasMenuActions = onAddToAlbumClick != null ||
    onEditPhotoClick != null ||
    onDeleteClick != null

  Box(
    modifier = modifier
      .fillMaxWidth()
      .padding(bottom = 5.dp)
  ) {
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .standaloneNavyCardSurface(
          darkTheme = darkTheme,
          isCurrent = isCurrent,
          accentColor = accentColor
        )
        .clip(RoundedCornerShape(34.dp))
        .combinedClickable(
          onClick = onSongClick,
          onLongClick = { if (hasMenuActions) showContextMenu = true }
        )
        .padding(horizontal = 14.dp, vertical = 12.dp)
        .testTag("song_row_${song.id}"),
      contentAlignment = Alignment.Center
    ) {
      Column(modifier = Modifier.fillMaxWidth()) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          // Cover art: rounded square, radius 16dp, 56dp, object-fit cover centered
          Box {
            Box(
              modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(16.dp))
                .border(
                  1.dp,
                  Color.White.copy(alpha = if (darkTheme) 0.16f else 0.75f),
                  RoundedCornerShape(16.dp)
                ),
              contentAlignment = Alignment.Center
            ) {
              SongArtwork(song = song, modifier = Modifier.fillMaxSize(), cornerRadius = 16.dp)
              if (isResolving) {
                Box(
                  modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.52f)),
                  contentAlignment = Alignment.Center
                ) {
                  CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = accentColor
                  )
                }
              } else if (isCurrent) {
                Box(
                  modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.42f)),
                  contentAlignment = Alignment.Center
                ) {
                  EqualizerBars(
                    isPlaying = isPlaying,
                    color = accentColor,
                    reducedMotion = reducedMotion,
                    barWidth = 3.dp,
                    maxHeight = 14.dp
                  )
                }
              }
            }

            if (hasMenuActions) {
              LibrarySongGlassMenu(
                expanded = showContextMenu,
                reducedMotion = reducedMotion,
                onDismissRequest = { showContextMenu = false },
                onAddToAlbumClick = onAddToAlbumClick,
                onEditPhotoClick = onEditPhotoClick,
                onDeleteClick = onDeleteClick
              )
            }
          }

          // Two lines: Title + "artist · duration" + optional genre pill
          Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
          ) {
            Text(
              text = song.title,
              style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              color = if (isCurrent) accentColor else MaterialTheme.colorScheme.onBackground
            )

            val durationStr = if (song.durationSeconds > 0L) {
              "${song.durationSeconds / 60}:${String.format("%02d", song.durationSeconds % 60)}"
            } else ""
            val baseMeta = if (durationStr.isNotBlank()) "${song.artist} · $durationStr" else song.artist
            val subtitleText = when {
              downloadProgress != null -> "${(downloadProgress * 100f).toInt().coerceIn(0, 100)}% · $baseMeta"
              isDownloaded -> "$baseMeta · Saved"
              else -> baseMeta
            }

            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
              if (!genreBadge.isNullOrBlank()) {
                val pillModifier = Modifier
                  .clip(CircleShape)
                  .background(accentColor.copy(alpha = if (darkTheme) 0.16f else 0.12f))
                  .border(
                    width = 0.8.dp,
                    color = accentColor.copy(alpha = if (darkTheme) 0.42f else 0.32f),
                    shape = CircleShape
                  )
                  .then(
                    if (onGenreBadgeClick != null) {
                      Modifier.clickable { onGenreBadgeClick() }
                    } else {
                      Modifier
                    }
                  )
                  .padding(horizontal = 7.dp, vertical = 1.5.dp)
                  .testTag("song_genre_badge_${song.id}")

                Box(modifier = pillModifier, contentAlignment = Alignment.Center) {
                  Text(
                    text = genreBadge,
                    style = MaterialTheme.typography.labelSmall.copy(
                      fontSize = 10.sp,
                      lineHeight = 13.sp,
                      fontWeight = FontWeight.SemiBold
                    ),
                    color = if (darkTheme) {
                      androidx.compose.ui.graphics.lerp(accentColor, Color.White, 0.28f)
                    } else {
                      accentColor
                    },
                    maxLines = 1
                  )
                }
              }

              Text(
                text = subtitleText,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (downloadProgress != null) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f, fill = false)
              )
            }
          }

          // Right-side action cluster: Download (when available), Love, and circular Play
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
          ) {
            if (onDownloadClick != null) {
              Box(
                modifier = Modifier
                  .size(38.dp)
                  .clip(CircleShape)
                  .clickable(enabled = downloadProgress == null) { onDownloadClick() }
                  .testTag("song_download_button_${song.id}"),
                contentAlignment = Alignment.Center
              ) {
                when {
                  downloadProgress != null -> {
                    CircularProgressIndicator(
                      progress = { downloadProgress.coerceIn(0.05f, 1f) },
                      modifier = Modifier.size(18.dp),
                      strokeWidth = 2.dp,
                      color = accentColor
                    )
                  }
                  isDownloaded -> {
                    Icon(
                      imageVector = Icons.Rounded.DownloadDone,
                      contentDescription = "Downloaded",
                      tint = accentColor,
                      modifier = Modifier.size(20.dp)
                    )
                  }
                  else -> {
                    Icon(
                      imageVector = Icons.Rounded.Download,
                      contentDescription = "Download",
                      tint = if (darkTheme) Color.White.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant,
                      modifier = Modifier.size(20.dp)
                    )
                  }
                }
              }
            }

            Box(
              modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .clickable { onFavoriteClick() }
                .testTag("song_favorite_button_${song.id}"),
              contentAlignment = Alignment.Center
            ) {
              Icon(
                imageVector = if (song.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                contentDescription = "Favorite",
                tint = if (song.isFavorite) {
                  MaterialTheme.colorScheme.error
                } else if (darkTheme) {
                  Color.White.copy(alpha = 0.85f)
                } else {
                  MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier
                  .scale(heartScale)
                  .size(20.dp)
              )
            }

            Spacer(modifier = Modifier.width(2.dp))

            Surface(
              shape = CircleShape,
              color = when {
                isCurrent -> accentColor.copy(alpha = 0.24f)
                darkTheme -> Color.White.copy(alpha = 0.12f)
                else -> Color.Black.copy(alpha = 0.08f)
              },
              border = BorderStroke(
                1.dp,
                if (isCurrent) accentColor.copy(alpha = 0.55f) else Color.White.copy(alpha = if (darkTheme) 0.14f else 0.70f)
              ),
              modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .clickable { onSongClick() }
                .testTag("song_play_button_${song.id}")
            ) {
              Box(contentAlignment = Alignment.Center) {
                Icon(
                  imageVector = if (isCurrent && isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                  contentDescription = if (isCurrent && isPlaying) "Pause" else "Play",
                  tint = if (isCurrent) accentColor else if (darkTheme) Color.White else MaterialTheme.colorScheme.onBackground,
                  modifier = Modifier.size(21.dp)
                )
              }
            }
          }
        }

        if (downloadProgress != null) {
          Spacer(modifier = Modifier.height(8.dp))
          LinearProgressIndicator(
            progress = { downloadProgress.coerceIn(0.02f, 1f) },
            modifier = Modifier
              .fillMaxWidth()
              .height(3.dp)
              .clip(CircleShape),
            color = accentColor,
            trackColor = accentColor.copy(alpha = 0.18f)
          )
        }
      }
    }
  }
}
