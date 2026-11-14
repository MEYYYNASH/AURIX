package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.model.LyricsResult
import com.example.model.Song

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingSheet(
  song: Song,
  isPlaying: Boolean,
  currentPositionMs: Long,
  lyricsResult: LyricsResult?,
  onPlayPause: () -> Unit,
  onSeek: (Long) -> Unit,
  onFavorite: () -> Unit,
  onClose: () -> Unit,
  onLineClick: (Long) -> Unit
) {
  var showLyrics by remember { mutableStateOf(false) }
  var sleepTimer by remember { mutableStateOf("Off") }
  var playbackSpeed by remember { mutableStateOf("1x") }

  Surface(
    modifier = Modifier.fillMaxSize(),
    color = MaterialTheme.colorScheme.background
  ) {
    Box(modifier = Modifier.fillMaxSize()) {
      if (song.artworkUrl.isNotBlank()) {
        AsyncImage(
          model = song.artworkUrl,
          contentDescription = null,
          contentScale = ContentScale.Crop,
          modifier = Modifier
            .fillMaxSize()
            .blur(64.dp)
        )
        Box(
          modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.85f))
        )
      }

      Column(
        modifier = Modifier
          .fillMaxSize()
          .windowInsetsPadding(WindowInsets.safeDrawing)
          .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          IconButton(onClick = onClose) {
            Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Close", tint = MaterialTheme.colorScheme.onBackground)
          }

          SingleChoiceSegmentedButtonRow {
            SegmentedButton(
              selected = !showLyrics,
              onClick = { showLyrics = false },
              shape = RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp)
            ) {
              Text("Player")
            }
            SegmentedButton(
              selected = showLyrics,
              onClick = { showLyrics = true },
              shape = RoundedCornerShape(topEnd = 20.dp, bottomEnd = 20.dp)
            ) {
              Text("Lyrics")
            }
          }

          IconButton(onClick = {}) {
            Icon(Icons.Rounded.MoreVert, contentDescription = "Details", tint = MaterialTheme.colorScheme.onBackground)
          }
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (showLyrics) {
          Box(modifier = Modifier.weight(1f)) {
            LyricsView(
              lyricsResult = lyricsResult,
              currentPositionMs = currentPositionMs,
              onLineClick = onLineClick
            )
          }
        } else {
          Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
          ) {
            Box(
              modifier = Modifier
                .size(300.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
              contentAlignment = Alignment.Center
            ) {
              if (song.artworkUrl.isNotBlank()) {
                AsyncImage(
                  model = song.artworkUrl,
                  contentDescription = null,
                  contentScale = ContentScale.Crop,
                  modifier = Modifier.fillMaxSize()
                )
              } else {
                Icon(Icons.Rounded.MusicNote, contentDescription = null, modifier = Modifier.size(96.dp), tint = MaterialTheme.colorScheme.primary)
              }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = song.title,
                  style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                  color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                  text = song.artist,
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
              IconButton(onClick = onFavorite) {
                Icon(
                  imageVector = if (song.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                  contentDescription = "Favorite",
                  tint = if (song.isFavorite) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onBackground
                )
              }
            }
          }
        }

        Spacer(modifier = Modifier.height(16.dp))

        val totalMs = song.durationSeconds * 1000
        val sliderPos = currentPositionMs.toFloat().coerceIn(0f, totalMs.toFloat().coerceAtLeast(1f))

        Slider(
          value = sliderPos,
          onValueChange = { onSeek(it.toLong()) },
          valueRange = 0f..(totalMs.toFloat().coerceAtLeast(1f)),
          colors = SliderDefaults.colors(
            thumbColor = MaterialTheme.colorScheme.primary,
            activeTrackColor = MaterialTheme.colorScheme.primary
          )
        )

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Text(text = formatTime(currentPositionMs), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
          Text(text = formatTime(totalMs), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceEvenly,
          verticalAlignment = Alignment.CenterVertically
        ) {
          IconButton(onClick = {}) {
            Icon(Icons.Rounded.Shuffle, contentDescription = "Shuffle", tint = MaterialTheme.colorScheme.onBackground)
          }
          IconButton(onClick = {}) {
            Icon(Icons.Rounded.SkipPrevious, contentDescription = "Previous", modifier = Modifier.size(36.dp), tint = MaterialTheme.colorScheme.onBackground)
          }
          FloatingActionButton(
            onClick = onPlayPause,
            shape = CircleShape,
            containerColor = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(72.dp)
          ) {
            Icon(
              imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
              contentDescription = "Play/Pause",
              tint = Color.White,
              modifier = Modifier.size(36.dp)
            )
          }
          IconButton(onClick = {}) {
            Icon(Icons.Rounded.SkipNext, contentDescription = "Next", modifier = Modifier.size(36.dp), tint = MaterialTheme.colorScheme.onBackground)
          }
          IconButton(onClick = {}) {
            Icon(Icons.Rounded.Repeat, contentDescription = "Repeat", tint = MaterialTheme.colorScheme.onBackground)
          }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceAround
        ) {
          TextButton(onClick = {
            sleepTimer = when (sleepTimer) {
              "Off" -> "15m"
              "15m" -> "30m"
              "30m" -> "60m"
              else -> "Off"
            }
          }) {
            Text("Sleep: $sleepTimer", color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
          TextButton(onClick = {
            playbackSpeed = when (playbackSpeed) {
              "1x" -> "1.25x"
              "1.25x" -> "1.5x"
              "1.5x" -> "0.75x"
              else -> "1x"
            }
          }) {
            Text("Speed: $playbackSpeed", color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        }
      }
    }
  }
}

fun formatTime(ms: Long): String {
  val totalSec = ms / 1000
  val min = totalSec / 60
  val sec = totalSec % 60
  return "$min:${String.format("%02d", sec)}"
}
