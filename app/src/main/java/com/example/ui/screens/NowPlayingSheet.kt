package com.example.ui.screens

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.model.LyricsResult
import com.example.model.Song
import com.example.ui.theme.HeartRed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingSheet(
  song: Song,
  isPlaying: Boolean,
  currentPositionMs: Long,
  durationMs: Long,
  lyricsResult: LyricsResult?,
  dynamicBg: Boolean,
  reducedMotion: Boolean,
  shuffleEnabled: Boolean,
  repeatEnabled: Boolean,
  playbackSpeed: Float,
  sleepMinutes: Int,
  onPlayPause: () -> Unit,
  onNext: () -> Unit,
  onPrevious: () -> Unit,
  onToggleShuffle: () -> Unit,
  onToggleRepeat: () -> Unit,
  onCycleSpeed: () -> Unit,
  onCycleSleep: () -> Unit,
  onSeek: (Long) -> Unit,
  onFavorite: () -> Unit,
  onClose: () -> Unit,
  onLineClick: (Long) -> Unit,
  onRetryLyrics: () -> Unit = {}
) {
  BackHandler { onClose() }

  var showLyrics by remember { mutableStateOf(false) }
  var showDetails by remember { mutableStateOf(false) }
  val context = LocalContext.current

  val (c1, c2) = rememberArtworkColors(song.artworkUrl, song.title)
  val bgAlpha by animateFloatAsState(
    targetValue = if (dynamicBg) 1.0f else 0f,
    animationSpec = if (reducedMotion) snap() else tween(500),
    label = "np_bg_alpha"
  )

  val infiniteTransition = rememberInfiniteTransition(label = "np_backdrop_drift")
  val backdropScale by infiniteTransition.animateFloat(
    initialValue = 1.28f,
    targetValue = 1.44f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = 6800, easing = FastOutSlowInEasing),
      repeatMode = RepeatMode.Reverse
    ),
    label = "np_drift_scale"
  )
  val backdropShiftX by infiniteTransition.animateFloat(
    initialValue = -16f,
    targetValue = 16f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = 8200, easing = FastOutSlowInEasing),
      repeatMode = RepeatMode.Reverse
    ),
    label = "np_drift_x"
  )
  val activeBgScale = if (reducedMotion) 1.32f else backdropScale
  val activeBgShiftX = if (reducedMotion) 0f else backdropShiftX

  val heartScale by animateFloatAsState(
    targetValue = if (song.isFavorite) 1.25f else 1.0f,
    animationSpec = if (reducedMotion) snap() else spring(dampingRatio = Spring.DampingRatioMediumBouncy),
    label = "np_heart"
  )

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(Color(0xFF0C0F16))
  ) {
    // True Dynamic Artwork Background sampled from actual album cover colors + blurred drifting art
    if (bgAlpha > 0.01f) {
      Box(modifier = Modifier.fillMaxSize().alpha(bgAlpha)) {
        if (song.artworkUrl.isNotBlank()) {
          AsyncImage(
            model = song.artworkUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
              .fillMaxSize()
              .offset(x = activeBgShiftX.dp)
              .scale(activeBgScale)
              .blur(54.dp)
          )
        }
        Box(
          modifier = Modifier
            .fillMaxSize()
            .background(
              Brush.radialGradient(
                colors = listOf(
                  c1.copy(alpha = 0.62f),
                  c2.copy(alpha = 0.34f),
                  Color.Transparent
                ),
                center = Offset(320f, 380f),
                radius = 1250f
              )
            )
        )
        Box(
          modifier = Modifier
            .fillMaxSize()
            .background(
              Brush.radialGradient(
                colors = listOf(
                  c2.copy(alpha = 0.44f),
                  Color.Transparent
                ),
                center = Offset(780f, 1550f),
                radius = 1000f
              )
            )
        )
        // Balanced contrast gradient so cover art colors glow vibrantly while keeping white controls crisp
        Box(
          modifier = Modifier
            .fillMaxSize()
            .background(
              Brush.verticalGradient(
                colors = listOf(
                  Color(0xFF090C14).copy(alpha = 0.32f),
                  Color(0xFF090C14).copy(alpha = 0.42f),
                  Color(0xFF080A11).copy(alpha = 0.72f)
                )
              )
            )
        )
      }
    }

    Column(
      modifier = Modifier
        .fillMaxSize()
        .windowInsetsPadding(WindowInsets.safeDrawing)
        .padding(horizontal = 24.dp, vertical = 12.dp),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      // Header
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        IconButton(
          onClick = onClose,
          modifier = Modifier.minimumInteractiveComponentSize().testTag("np_close_button")
        ) {
          Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(28.dp))
        }

        // Glass Switch: Player | Lyrics
        Surface(
          shape = RoundedCornerShape(999.dp),
          color = Color.White.copy(alpha = 0.14f),
          border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.22f))
        ) {
          Row(modifier = Modifier.padding(3.dp)) {
            listOf(false to "Player", true to "Lyrics").forEach { (lyricsTab, label) ->
              val selected = showLyrics == lyricsTab
              Box(
                modifier = Modifier
                  .clip(RoundedCornerShape(999.dp))
                  .background(if (selected) Color.White.copy(alpha = 0.26f) else Color.Transparent)
                  .clickable { showLyrics = lyricsTab }
                  .testTag(if (lyricsTab) "np_tab_lyrics" else "np_tab_player")
                  .padding(horizontal = 18.dp, vertical = 8.dp)
              ) {
                Text(
                  text = label,
                  color = if (selected) Color.White else Color.White.copy(alpha = 0.72f),
                  style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold)
                )
              }
            }
          }
        }

        IconButton(
          onClick = { showDetails = true },
          modifier = Modifier.minimumInteractiveComponentSize().testTag("np_details_button")
        ) {
          Icon(Icons.Rounded.MoreVert, contentDescription = "Details", tint = Color.White)
        }
      }

      Spacer(modifier = Modifier.height(12.dp))

      val syncedLines = lyricsResult?.synced
      val activeLyricLine = remember(syncedLines, currentPositionMs) {
        syncedLines?.lastOrNull { it.timestampMs <= currentPositionMs } ?: syncedLines?.firstOrNull()
      }

      // Pane Switcher with smooth fade
      AnimatedContent(
        targetState = showLyrics,
        modifier = Modifier.weight(1f).fillMaxWidth(),
        transitionSpec = {
          if (reducedMotion) fadeIn(snap()) togetherWith fadeOut(snap())
          else fadeIn(tween(260)) togetherWith fadeOut(tween(200))
        },
        label = "np_pane"
      ) { lyricsVisible ->
        if (lyricsVisible) {
          LyricsView(
            lyricsResult = lyricsResult,
            currentPositionMs = currentPositionMs,
            accentColor = c1,
            reducedMotion = reducedMotion,
            onLineClick = onLineClick,
            onRetryLyrics = onRetryLyrics
          )
        } else {
          Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
          ) {
            val artScale by animateFloatAsState(
              targetValue = if (isPlaying) 1.0f else 0.94f,
              animationSpec = if (reducedMotion) snap() else spring(dampingRatio = Spring.DampingRatioLowBouncy),
              label = "art_scale"
            )

            Surface(
              modifier = Modifier
                .size(if (activeLyricLine != null) 252.dp else 280.dp)
                .scale(artScale),
              shape = RoundedCornerShape(28.dp),
              shadowElevation = 24.dp,
              color = Color.Transparent
            ) {
              SongArtwork(
                song = song,
                modifier = Modifier.fillMaxSize(),
                cornerRadius = 28.dp,
                iconSize = 84.dp
              )
            }

            Spacer(modifier = Modifier.height(22.dp))

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = song.title,
                  style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp, fontWeight = FontWeight.Bold),
                  maxLines = 2,
                  overflow = TextOverflow.Ellipsis,
                  color = Color.White
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                  text = song.artist,
                  style = MaterialTheme.typography.bodyMedium,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                  color = Color.White.copy(alpha = 0.78f)
                )
              }
              IconButton(
                onClick = onFavorite,
                modifier = Modifier.minimumInteractiveComponentSize().testTag("np_favorite_button")
              ) {
                Icon(
                  imageVector = if (song.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                  contentDescription = "Favorite",
                  tint = if (song.isFavorite) HeartRed else Color.White,
                  modifier = Modifier.scale(heartScale)
                )
              }
            }

            if (activeLyricLine != null) {
              Spacer(modifier = Modifier.height(12.dp))
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .clip(RoundedCornerShape(16.dp))
                  .background(Color.White.copy(alpha = 0.12f))
                  .border(1.dp, Color.White.copy(alpha = 0.20f), RoundedCornerShape(16.dp))
                  .clickable { showLyrics = true }
                  .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                Icon(
                  imageVector = Icons.Rounded.Lyrics,
                  contentDescription = null,
                  tint = Color.White.copy(alpha = 0.85f),
                  modifier = Modifier.size(16.dp)
                )
                Text(
                  text = activeLyricLine.text,
                  style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                  color = Color.White.copy(alpha = 0.94f),
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                  modifier = Modifier.weight(1f)
                )
              }
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(10.dp))

      // Seek bar with sleek circular white thumb (replaces tall vertical M3 thumb)
      val totalMs = durationMs.coerceAtLeast(song.durationSeconds * 1000L).coerceAtLeast(1L)
      val sliderPos = currentPositionMs.toFloat().coerceIn(0f, totalMs.toFloat())
      val sliderInteraction = remember { MutableInteractionSource() }

      Slider(
        value = sliderPos,
        onValueChange = { onSeek(it.toLong()) },
        valueRange = 0f..totalMs.toFloat(),
        interactionSource = sliderInteraction,
        thumb = {
          Box(
            modifier = Modifier
              .size(16.dp)
              .clip(CircleShape)
              .background(Color.White)
              .border(1.dp, c1.copy(alpha = 0.45f), CircleShape)
          )
        },
        track = { sliderState ->
          val fraction = ((sliderState.value - sliderState.valueRange.start) /
            (sliderState.valueRange.endInclusive - sliderState.valueRange.start).coerceAtLeast(1f))
            .coerceIn(0f, 1f)
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .height(5.dp)
              .clip(CircleShape)
              .background(Color.White.copy(alpha = 0.24f))
          ) {
            Box(
              modifier = Modifier
                .fillMaxWidth(fraction)
                .fillMaxHeight()
                .clip(CircleShape)
                .background(
                  Brush.horizontalGradient(
                    colors = listOf(Color.White, Color.White.copy(alpha = 0.92f))
                  )
                )
            )
          }
        }
      )

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        Text(text = formatTime(currentPositionMs), style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.78f))
        Text(text = formatTime(totalMs), style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.78f))
      }

      Spacer(modifier = Modifier.height(12.dp))

      // Playback controls
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
      ) {
        IconButton(onClick = onToggleShuffle, modifier = Modifier.minimumInteractiveComponentSize()) {
          Icon(
            Icons.Rounded.Shuffle,
            contentDescription = "Shuffle",
            tint = if (shuffleEnabled) c1 else Color.White.copy(alpha = 0.70f)
          )
        }
        IconButton(onClick = onPrevious, modifier = Modifier.minimumInteractiveComponentSize()) {
          Icon(Icons.Rounded.SkipPrevious, contentDescription = "Previous", modifier = Modifier.size(36.dp), tint = Color.White)
        }
        Surface(
          modifier = Modifier
            .size(72.dp)
            .clip(CircleShape)
            .clickable { onPlayPause() }
            .testTag("np_play_pause_button"),
          shape = CircleShape,
          color = Color.White,
          shadowElevation = 12.dp
        ) {
          Box(contentAlignment = Alignment.Center) {
            Icon(
              imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
              contentDescription = "Play or Pause",
              tint = Color(0xFF111111),
              modifier = Modifier.size(34.dp)
            )
          }
        }
        IconButton(onClick = onNext, modifier = Modifier.minimumInteractiveComponentSize()) {
          Icon(Icons.Rounded.SkipNext, contentDescription = "Next", modifier = Modifier.size(36.dp), tint = Color.White)
        }
        IconButton(onClick = onToggleRepeat, modifier = Modifier.minimumInteractiveComponentSize()) {
          Icon(
            Icons.Rounded.Repeat,
            contentDescription = "Repeat",
            tint = if (repeatEnabled) c1 else Color.White.copy(alpha = 0.70f)
          )
        }
      }

      Spacer(modifier = Modifier.height(10.dp))

      // Sleep timer & speed toggles
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceAround
      ) {
        TextButton(onClick = onCycleSleep) {
          val label = if (sleepMinutes == 0) "Sleep: Off" else "Sleep: ${sleepMinutes}m"
          Text(
            text = label,
            color = if (sleepMinutes > 0) Color.White else Color.White.copy(alpha = 0.70f),
            textDecoration = if (sleepMinutes > 0) TextDecoration.Underline else TextDecoration.None
          )
        }
        TextButton(onClick = onCycleSpeed) {
          val speedLabel = when (playbackSpeed) {
            1.25f -> "1.25x"
            1.5f -> "1.5x"
            0.75f -> "0.75x"
            else -> "1x"
          }
          Text(
            text = "Speed: $speedLabel",
            color = if (playbackSpeed != 1.0f) Color.White else Color.White.copy(alpha = 0.70f),
            textDecoration = if (playbackSpeed != 1.0f) TextDecoration.Underline else TextDecoration.None
          )
        }
      }
    }
  }

  if (showDetails) {
    SongDetailsSheet(
      song = song,
      onDismiss = { showDetails = false },
      onPlay = {
        showDetails = false
        if (!isPlaying) onPlayPause()
      },
      onFavorite = onFavorite,
      onShare = {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
          type = "text/plain"
          putExtra(Intent.EXTRA_TEXT, "Listening to ${song.title} by ${song.artist} on AURIX")
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share song"))
        showDetails = false
      }
    )
  }
}

fun formatTime(ms: Long): String {
  val totalSec = (ms / 1000).coerceAtLeast(0)
  val min = totalSec / 60
  val sec = totalSec % 60
  return "$min:${String.format("%02d", sec)}"
}
