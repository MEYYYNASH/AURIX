package com.example.ui.screens

import android.os.Build
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

  val (artC1, artC2) = rememberArtworkColors(song.artworkUrl, song.title)
  val accentColor = MaterialTheme.colorScheme.primary

  val glassFill = if (darkTheme) Color(0xFF172034).copy(alpha = 0.82f) else Color.White.copy(alpha = 0.88f)
  val innerEdgeHighlight = Color.White.copy(alpha = if (darkTheme) 0.26f else 0.90f)
  val topBrightHighlight = Color.White.copy(alpha = if (darkTheme) 0.48f else 0.98f)
  val primaryTextColor = if (darkTheme) Color.White else Color(0xFF0F172A)
  val secondaryTextColor = if (darkTheme) Color(0xFFB3BFD4) else Color(0xFF52607A)
  val buttonBgColor = if (darkTheme) Color.White.copy(alpha = 0.95f) else MaterialTheme.colorScheme.primary
  val buttonIconColor = if (darkTheme) Color(0xFF0F172A) else Color.White

  val totalMs = durationMs.coerceAtLeast(song.durationSeconds * 1000L).coerceAtLeast(1L)
  val progress = (currentPositionMs.toFloat() / totalMs.toFloat()).coerceIn(0f, 1f)

  val heartScale by animateFloatAsState(
    targetValue = if (song.isFavorite) 1.2f else 1.0f,
    animationSpec = if (reducedMotion) snap() else spring(dampingRatio = Spring.DampingRatioMediumBouncy),
    label = "mini_heart"
  )

  Box(
    modifier = Modifier
      .padding(horizontal = 22.dp)
      .widthIn(max = 390.dp)
      .fillMaxWidth()
      .height(64.dp),
    contentAlignment = Alignment.Center
  ) {
    // Soft ambient pill shadow
    Box(
      modifier = Modifier
        .matchParentSize()
        .drawBehind {
          val r = size.height * 0.5f
          drawRoundRect(
            brush = Brush.verticalGradient(
              colors = listOf(
                Color.Black.copy(alpha = if (darkTheme) 0.24f else 0.07f),
                Color.Black.copy(alpha = if (darkTheme) 0.36f else 0.13f)
              )
            ),
            topLeft = Offset(0f, 5.dp.toPx()),
            size = Size(size.width, size.height),
            cornerRadius = CornerRadius(r, r)
          )
        }
    )

    // Frosted Liquid Glass Capsule Container
    Box(
      modifier = Modifier
        .matchParentSize()
        .clip(CircleShape)
        .clickable { onClick() }
        .testTag("mini_player_card")
    ) {
      // Subtle blurred track-hue aura inside glass pill
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Box(
          modifier = Modifier
            .matchParentSize()
            .blur(18.dp)
            .background(
              Brush.horizontalGradient(
                colors = listOf(
                  artC1.copy(alpha = if (darkTheme) 0.24f else 0.16f),
                  artC2.copy(alpha = if (darkTheme) 0.14f else 0.12f),
                  accentColor.copy(alpha = if (darkTheme) 0.16f else 0.10f)
                )
              )
            )
        )
      }

      // Base translucent fill + 1dp glass edge + bright 1.5dp top specular highlight
      Box(
        modifier = Modifier
          .matchParentSize()
          .background(glassFill)
          .border(1.dp, innerEdgeHighlight, CircleShape)
          .drawBehind {
            val stroke15 = 1.5.dp.toPx()
            val inset = stroke15 * 0.5f
            val r = (size.height - stroke15) * 0.5f
            drawRoundRect(
              brush = Brush.verticalGradient(
                colors = listOf(
                  topBrightHighlight,
                  topBrightHighlight.copy(alpha = 0.12f),
                  Color.Transparent
                ),
                startY = 0f,
                endY = size.height * 0.45f
              ),
              topLeft = Offset(inset, inset),
              size = Size(size.width - stroke15, size.height - stroke15),
              cornerRadius = CornerRadius(r, r),
              style = Stroke(width = stroke15)
            )
          }
      )

      // Foreground Row Content
      Row(
        modifier = Modifier
          .fillMaxSize()
          .padding(horizontal = 9.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Box(
          modifier = Modifier
            .size(46.dp)
            .clip(CircleShape)
            .border(1.dp, Color.White.copy(alpha = if (darkTheme) 0.30f else 0.85f), CircleShape)
        ) {
          SongArtwork(
            song = song,
            modifier = Modifier.fillMaxSize(),
            cornerRadius = 23.dp,
            iconSize = 20.dp
          )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            if (isPlaying) {
              EqualizerBars(
                isPlaying = true,
                color = accentColor,
                reducedMotion = reducedMotion,
                barWidth = 2.5.dp,
                maxHeight = 11.dp
              )
            }
            Text(
              text = song.title,
              style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              color = primaryTextColor
            )
          }
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
            tint = if (song.isFavorite) HeartRed else primaryTextColor.copy(alpha = 0.78f),
            modifier = Modifier.size(20.dp).scale(heartScale)
          )
        }

        // Circular Play/Pause Button surrounded by live progress ring
        Box(
          modifier = Modifier
            .size(46.dp)
            .clip(CircleShape)
            .clickable { onPlayPauseClick() }
            .testTag("mini_play_pause_button"),
          contentAlignment = Alignment.Center
        ) {
          Canvas(modifier = Modifier.matchParentSize().padding(1.5.dp)) {
            val strokePx = 2.5.dp.toPx()
            drawArc(
              color = primaryTextColor.copy(alpha = 0.14f),
              startAngle = 0f,
              sweepAngle = 360f,
              useCenter = false,
              style = Stroke(width = strokePx)
            )
            if (progress > 0.005f) {
              drawArc(
                color = accentColor,
                startAngle = -90f,
                sweepAngle = 360f * progress,
                useCenter = false,
                style = Stroke(width = strokePx, cap = StrokeCap.Round)
              )
            }
          }
          Box(
            modifier = Modifier
              .size(36.dp)
              .clip(CircleShape)
              .background(buttonBgColor),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
              contentDescription = "Play or Pause",
              tint = buttonIconColor,
              modifier = Modifier.size(20.dp)
            )
          }
        }
      }
    }
  }
}
