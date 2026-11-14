package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.R
import com.example.model.Song

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

  Box(
    modifier = modifier
      .clip(RoundedCornerShape(cornerRadius))
      .background(gradient),
    contentAlignment = Alignment.Center
  ) {
    if (song.artworkUrl.isNotBlank()) {
      AsyncImage(
        model = song.artworkUrl,
        contentDescription = song.title,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize()
      )
    } else {
      Icon(
        imageVector = Icons.Rounded.MusicNote,
        contentDescription = null,
        tint = Color.White.copy(alpha = 0.85f),
        modifier = Modifier.size(iconSize)
      )
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
  val fillColor = if (darkTheme) {
    Color(0xFF172034).copy(alpha = 0.68f)
  } else {
    Color.White.copy(alpha = 0.78f)
  }
  val borderBrush = Brush.verticalGradient(
    colors = if (darkTheme) {
      listOf(Color.White.copy(alpha = 0.24f), Color.White.copy(alpha = 0.06f))
    } else {
      listOf(Color.White.copy(alpha = 0.96f), Color.White.copy(alpha = 0.50f))
    }
  )
  val topSpecular = Color.White.copy(alpha = if (darkTheme) 0.30f else 0.95f)

  Box(
    modifier = modifier
      .clip(shape)
      .background(fillColor)
      .border(1.dp, borderBrush, shape)
      .drawBehind {
        val stroke15 = 1.5.dp.toPx()
        val inset = stroke15 * 0.5f
        val r = (cornerRadius.toPx() - inset).coerceAtLeast(0f)
        drawRoundRect(
          brush = Brush.verticalGradient(
            colors = listOf(topSpecular, Color.Transparent),
            startY = 0f,
            endY = size.height * 0.32f
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
  testTags: List<String> = emptyList()
) {
  val barBg = if (darkTheme) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.68f)
  val barBorder = if (darkTheme) Color.White.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.95f)
  val pillBg = if (darkTheme) Color(0xFF28344E) else Color.White

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
      Row(
        modifier = Modifier
          .weight(1f)
          .clip(CircleShape)
          .background(if (selected) pillBg else Color.Transparent)
          .then(
            if (selected) Modifier.border(
              0.75.dp,
              Color.White.copy(alpha = if (darkTheme) 0.18f else 0.9f),
              CircleShape
            ) else Modifier
          )
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
            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
          )
          Spacer(modifier = Modifier.width(6.dp))
        }
        Text(
          text = label,
          style = MaterialTheme.typography.bodySmall.copy(
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold
          ),
          color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
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
    targetValue = 1.06f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = 1900, easing = FastOutSlowInEasing),
      repeatMode = RepeatMode.Reverse
    ),
    label = "logo_scale"
  )
  val glowAlpha by infiniteTransition.animateFloat(
    initialValue = 0.28f,
    targetValue = 0.65f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = 1900, easing = FastOutSlowInEasing),
      repeatMode = RepeatMode.Reverse
    ),
    label = "logo_glow"
  )

  val activeScale = if (animated && !reducedMotion) pulseScale else 1.0f
  val activeGlow = if (animated && !reducedMotion) glowAlpha else 0.35f

  Box(
    modifier = modifier.size(size),
    contentAlignment = Alignment.Center
  ) {
    Box(
      modifier = Modifier
        .matchParentSize()
        .scale(activeScale * 1.08f)
        .clip(RoundedCornerShape(cornerRadius + 4.dp))
        .background(
          Brush.radialGradient(
            colors = listOf(
              MaterialTheme.colorScheme.primary.copy(alpha = activeGlow),
              Color.Transparent
            )
          )
        )
    )
    Image(
      painter = painterResource(id = R.drawable.aurix_clean_logo_1794625921343),
      contentDescription = "AURIX Logo",
      contentScale = ContentScale.Crop,
      modifier = Modifier
        .matchParentSize()
        .scale(activeScale)
        .clip(RoundedCornerShape(cornerRadius))
        .border(
          width = 1.dp,
          brush = Brush.verticalGradient(
            listOf(Color.White.copy(alpha = 0.45f), Color.White.copy(alpha = 0.08f))
          ),
          shape = RoundedCornerShape(cornerRadius)
        )
    )
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
  val targetAlpha = if (enabled && song != null) (if (darkTheme) 0.55f else 0.42f) else 0f
  val animatedAlpha by animateFloatAsState(
    targetValue = targetAlpha,
    animationSpec = if (reducedMotion) snap() else tween(durationMillis = 750),
    label = "dynamic_bg_alpha"
  )

  val infiniteTransition = rememberInfiniteTransition(label = "bg_glow")
  val driftScale by infiniteTransition.animateFloat(
    initialValue = 1.28f,
    targetValue = 1.40f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = 6500, easing = FastOutSlowInEasing),
      repeatMode = RepeatMode.Reverse
    ),
    label = "bg_drift_scale"
  )
  val activeScale = if (reducedMotion) 1.32f else driftScale

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
            colors = listOf(accentColor.copy(alpha = 0.22f), Color.Transparent),
            radius = 950f
          )
        )
    )

    if (animatedAlpha > 0.01f && song != null) {
      val (c1, c2) = remember(song.title) { artworkHueColors(song.title) }
      Box(modifier = Modifier.fillMaxSize().alpha(animatedAlpha)) {
        if (song.artworkUrl.isNotBlank()) {
          AsyncImage(
            model = song.artworkUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
              .fillMaxSize()
              .scale(activeScale)
              .blur(90.dp)
          )
        }
        Box(
          modifier = Modifier
            .fillMaxSize()
            .background(
              Brush.linearGradient(
                colors = listOf(
                  c1.copy(alpha = 0.55f),
                  c2.copy(alpha = 0.42f),
                  accentColor.copy(alpha = 0.28f)
                )
              )
            )
        )
        Box(
          modifier = Modifier
            .fillMaxSize()
            .background(
              if (darkTheme) {
                Color(0xFF0B0F1A).copy(alpha = 0.52f)
              } else {
                Color(0xFFF3F6FF).copy(alpha = 0.48f)
              }
            )
        )
      }
    }
  }
}

@Composable
fun SongRow(
  song: Song,
  isPlaying: Boolean = false,
  isCurrent: Boolean = false,
  reducedMotion: Boolean = false,
  onSongClick: () -> Unit,
  onFavoriteClick: () -> Unit
) {
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
        if (isCurrent) Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
        else Modifier
      )
      .clickable { onSongClick() }
      .padding(vertical = 8.dp, horizontal = 8.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Box(
      modifier = Modifier
        .size(48.dp)
        .clip(RoundedCornerShape(12.dp))
        .border(1.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(12.dp))
    ) {
      SongArtwork(song = song, modifier = Modifier.fillMaxSize(), cornerRadius = 12.dp)
    }
    Spacer(modifier = Modifier.width(12.dp))
    Column(modifier = Modifier.weight(1f)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
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
        Text(
          text = song.title,
          style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground
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
        if (song.source == "iTunes 30s") {
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
        }
      }
    }
    if (song.durationSeconds > 0 && song.source != "iTunes 30s") {
      Text(
        text = "${song.durationSeconds / 60}:${String.format("%02d", song.durationSeconds % 60)}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 6.dp)
      )
    }
    IconButton(
      onClick = onFavoriteClick,
      modifier = Modifier
        .minimumInteractiveComponentSize()
        .testTag("song_favorite_button_${song.id}")
    ) {
      Icon(
        imageVector = if (song.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
        contentDescription = "Favorite",
        tint = if (song.isFavorite) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.scale(heartScale)
      )
    }
  }
}

@Composable
fun SongDownloadRow(
  song: Song,
  index: Int,
  isPlaying: Boolean = false,
  isCurrent: Boolean = false,
  reducedMotion: Boolean = false,
  downloadProgress: Float? = null,
  isDownloaded: Boolean = false,
  onSongClick: () -> Unit,
  onFavoriteClick: () -> Unit,
  onDownloadClick: () -> Unit = {}
) {
  Surface(
    modifier = Modifier
      .fillMaxWidth()
      .padding(vertical = 3.dp)
      .clip(RoundedCornerShape(20.dp))
      .clickable { onSongClick() },
    color = if (isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
    shape = RoundedCornerShape(20.dp)
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .border(
          1.dp,
          Color.White.copy(alpha = 0.14f),
          RoundedCornerShape(20.dp)
        )
        .padding(horizontal = 12.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      if (isCurrent) {
        EqualizerBars(
          isPlaying = isPlaying,
          color = MaterialTheme.colorScheme.primary,
          reducedMotion = reducedMotion,
          modifier = Modifier.width(26.dp)
        )
      } else {
        Text(
          text = String.format("%02d", index),
          style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.width(26.dp)
        )
      }

      SongArtwork(song = song, modifier = Modifier.size(48.dp), cornerRadius = 12.dp)

      Spacer(modifier = Modifier.width(12.dp))
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = song.title,
          style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground
        )
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
          if (song.source == "iTunes 30s") {
            Surface(
              shape = RoundedCornerShape(6.dp),
              color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
            ) {
              Text(
                text = "30s",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
              )
            }
          }
        }
      }

      IconButton(
        onClick = onDownloadClick,
        modifier = Modifier
          .minimumInteractiveComponentSize()
          .testTag("song_download_button_${song.id}")
      ) {
        if (downloadProgress != null) {
          CircularProgressIndicator(
            progress = { downloadProgress.coerceIn(0f, 1f) },
            modifier = Modifier.size(20.dp),
            strokeWidth = 2.5.dp,
            color = MaterialTheme.colorScheme.primary
          )
        } else {
          Icon(
            imageVector = if (isDownloaded) Icons.Rounded.DownloadDone else Icons.Rounded.Download,
            contentDescription = "Download Preview",
            tint = if (isDownloaded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }

      IconButton(
        onClick = onFavoriteClick,
        modifier = Modifier.minimumInteractiveComponentSize()
      ) {
        Icon(
          imageVector = if (song.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
          contentDescription = "Favorite",
          tint = if (song.isFavorite) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
        )
      }

      IconButton(
        onClick = onSongClick,
        modifier = Modifier.minimumInteractiveComponentSize()
      ) {
        Icon(
          imageVector = if (isCurrent && isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
          contentDescription = "Play",
          tint = MaterialTheme.colorScheme.onBackground
        )
      }
    }
  }
}
