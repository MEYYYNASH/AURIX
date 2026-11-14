package com.example.ui.screens

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.Song
import com.example.ui.theme.HeartRed

@Composable
fun MiniPlayer(
  song: Song?,
  isPlaying: Boolean,
  currentPositionMs: Long,
  durationMs: Long,
  darkTheme: Boolean,
  reducedMotion: Boolean = false,
  onPlayPauseClick: () -> Unit,
  onFavoriteClick: () -> Unit,
  onClick: () -> Unit
) {
  if (song == null) return

  val containerColor = if (darkTheme) Color(0xFF1A2234).copy(alpha = 0.86f) else Color.White.copy(alpha = 0.88f)
  val borderColor = if (darkTheme) Color.White.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.96f)
  val primaryTextColor = if (darkTheme) Color.White else Color(0xFF0F172A)
  val secondaryTextColor = if (darkTheme) Color(0xFF9AA4BA) else Color(0xFF64748B)
  val buttonBgColor = if (darkTheme) Color.White else MaterialTheme.colorScheme.primary
  val buttonIconColor = if (darkTheme) Color(0xFF0F172A) else Color.White

  val totalMs = durationMs.coerceAtLeast(song.durationSeconds * 1000L).coerceAtLeast(1L)
  val progress = (currentPositionMs.toFloat() / totalMs.toFloat()).coerceIn(0f, 1f)

  val heartScale by animateFloatAsState(
    targetValue = if (song.isFavorite) 1.2f else 1.0f,
    animationSpec = if (reducedMotion) snap() else spring(dampingRatio = Spring.DampingRatioMediumBouncy),
    label = "mini_heart"
  )

  Surface(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 18.dp)
      .clip(RoundedCornerShape(24.dp))
      .clickable { onClick() }
      .testTag("mini_player_card"),
    color = containerColor,
    shadowElevation = 0.dp,
    border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
    shape = RoundedCornerShape(24.dp)
  ) {
    Column(modifier = Modifier.fillMaxWidth()) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        SongArtwork(
          song = song,
          modifier = Modifier.width(64.dp).height(44.dp),
          cornerRadius = 14.dp,
          iconSize = 20.dp
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = song.title,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = primaryTextColor
          )
          Text(
            text = song.artist,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = secondaryTextColor
          )
        }

        IconButton(
          onClick = onFavoriteClick,
          modifier = Modifier
            .minimumInteractiveComponentSize()
            .testTag("mini_favorite_button")
        ) {
          Icon(
            imageVector = if (song.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
            contentDescription = "Favorite",
            tint = if (song.isFavorite) HeartRed else primaryTextColor.copy(alpha = 0.75f),
            modifier = Modifier.scale(heartScale)
          )
        }

        Box(
          modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(buttonBgColor)
            .clickable { onPlayPauseClick() }
            .testTag("mini_play_pause_button"),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
            contentDescription = "Play or Pause",
            tint = buttonIconColor,
            modifier = Modifier.size(22.dp)
          )
        }
      }

      // Thin accent progress line
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(3.dp)
          .background(primaryTextColor.copy(alpha = 0.08f))
      ) {
        Box(
          modifier = Modifier
            .fillMaxWidth(progress)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.primary)
        )
      }
    }
  }
}
