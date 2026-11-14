package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
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
fun DynamicArtworkBackdrop(
  song: Song?,
  enabled: Boolean,
  darkTheme: Boolean,
  reducedMotion: Boolean,
  modifier: Modifier = Modifier
) {
  val targetAlpha = if (enabled && song != null) (if (darkTheme) 0.34f else 0.22f) else 0f
  val animatedAlpha by animateFloatAsState(
    targetValue = targetAlpha,
    animationSpec = if (reducedMotion) snap() else tween(durationMillis = 700),
    label = "dynamic_bg_alpha"
  )

  if (animatedAlpha > 0.01f && song != null) {
    val (c1, c2) = remember(song.title) { artworkHueColors(song.title) }
    Box(modifier = modifier.fillMaxSize().alpha(animatedAlpha)) {
      if (song.artworkUrl.isNotBlank()) {
        AsyncImage(
          model = song.artworkUrl,
          contentDescription = null,
          contentScale = ContentScale.Crop,
          modifier = Modifier
            .fillMaxSize()
            .scale(1.25f)
            .blur(72.dp)
        )
      }
      Box(
        modifier = Modifier
          .fillMaxSize()
          .background(
            Brush.radialGradient(
              colors = listOf(
                c1.copy(alpha = 0.55f),
                c2.copy(alpha = 0.30f),
                Color.Transparent
              )
            )
          )
      )
      Box(
        modifier = Modifier
          .fillMaxSize()
          .background(
            Brush.verticalGradient(
              colors = listOf(
                Color.Transparent,
                MaterialTheme.colorScheme.background.copy(alpha = 0.85f),
                MaterialTheme.colorScheme.background
              )
            )
          )
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
      .clickable { onSongClick() }
      .padding(vertical = 8.dp, horizontal = 4.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    SongArtwork(song = song, modifier = Modifier.size(48.dp), cornerRadius = 12.dp)
    Spacer(modifier = Modifier.width(14.dp))
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
      Text(
        text = song.artist,
        style = MaterialTheme.typography.bodySmall,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
    if (song.durationSeconds > 0) {
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
  onSongClick: () -> Unit,
  onFavoriteClick: () -> Unit
) {
  Surface(
    modifier = Modifier
      .fillMaxWidth()
      .padding(vertical = 4.dp)
      .clip(RoundedCornerShape(20.dp))
      .clickable { onSongClick() },
    color = if (isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
    shape = RoundedCornerShape(20.dp)
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
        Text(
          text = song.artist,
          style = MaterialTheme.typography.bodySmall,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }

      Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.padding(horizontal = 4.dp)
      ) {
        Text(
          text = "MP3",
          style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp),
          color = MaterialTheme.colorScheme.primary,
          modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
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
