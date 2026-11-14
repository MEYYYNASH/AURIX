package com.example.ui.screens

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.model.LyricsResult
import com.example.model.Song
import com.example.ui.theme.HeartRed

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
  onLineClick: (Long) -> Unit
) {
  BackHandler { onClose() }

  var showLyrics by remember { mutableStateOf(false) }
  var showDetails by remember { mutableStateOf(false) }
  val context = LocalContext.current

  val (c1, c2) = remember(song.title) { artworkHueColors(song.title) }
  val bgAlpha by animateFloatAsState(
    targetValue = if (dynamicBg) 0.85f else 0f,
    animationSpec = if (reducedMotion) snap() else tween(500),
    label = "np_bg_alpha"
  )

  val heartScale by animateFloatAsState(
    targetValue = if (song.isFavorite) 1.25f else 1.0f,
    animationSpec = if (reducedMotion) snap() else spring(dampingRatio = Spring.DampingRatioMediumBouncy),
    label = "np_heart"
  )

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(Color(0xFF10131A))
  ) {
    // Dynamic artwork blurred backdrop
    if (bgAlpha > 0.01f) {
      Box(modifier = Modifier.fillMaxSize().alpha(bgAlpha)) {
        if (song.artworkUrl.isNotBlank()) {
          AsyncImage(
            model = song.artworkUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
              .fillMaxSize()
              .scale(1.2f)
              .blur(60.dp)
          )
        }
        Box(
          modifier = Modifier
            .fillMaxSize()
            .background(
              Brush.radialGradient(
                colors = listOf(
                  c2.copy(alpha = 0.55f),
                  c1.copy(alpha = 0.45f),
                  Color(0xFF0B1020).copy(alpha = 0.88f)
                )
              )
            )
        )
        Box(
          modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0D14).copy(alpha = 0.48f))
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
          color = Color.White.copy(alpha = 0.14f)
        ) {
          Row(modifier = Modifier.padding(3.dp)) {
            listOf(false to "Player", true to "Lyrics").forEach { (lyricsTab, label) ->
              val selected = showLyrics == lyricsTab
              Box(
                modifier = Modifier
                  .clip(RoundedCornerShape(999.dp))
                  .background(if (selected) Color.White.copy(alpha = 0.24f) else Color.Transparent)
                  .clickable { showLyrics = lyricsTab }
                  .padding(horizontal = 18.dp, vertical = 8.dp)
              ) {
                Text(
                  text = label,
                  color = if (selected) Color.White else Color.White.copy(alpha = 0.7f),
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

      Spacer(modifier = Modifier.height(16.dp))

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
          SyncedLyricsPane(
            lyricsResult = lyricsResult,
            currentPositionMs = currentPositionMs,
            reducedMotion = reducedMotion,
            onLineClick = onLineClick
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
                .size(280.dp)
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

            Spacer(modifier = Modifier.height(28.dp))

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
                  color = Color.White.copy(alpha = 0.72f)
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
          }
        }
      }

      Spacer(modifier = Modifier.height(12.dp))

      // Seek bar
      val totalMs = durationMs.coerceAtLeast(song.durationSeconds * 1000L).coerceAtLeast(1L)
      val sliderPos = currentPositionMs.toFloat().coerceIn(0f, totalMs.toFloat())

      Slider(
        value = sliderPos,
        onValueChange = { onSeek(it.toLong()) },
        valueRange = 0f..totalMs.toFloat(),
        colors = SliderDefaults.colors(
          thumbColor = Color.White,
          activeTrackColor = Color.White,
          inactiveTrackColor = Color.White.copy(alpha = 0.25f)
        )
      )

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        Text(text = formatTime(currentPositionMs), style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.72f))
        Text(text = formatTime(totalMs), style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.72f))
      }

      Spacer(modifier = Modifier.height(14.dp))

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
            tint = if (shuffleEnabled) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.65f)
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
            tint = if (repeatEnabled) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.65f)
          )
        }
      }

      Spacer(modifier = Modifier.height(12.dp))

      // Sleep timer & speed toggles
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceAround
      ) {
        TextButton(onClick = onCycleSleep) {
          val label = if (sleepMinutes == 0) "Sleep: Off" else "Sleep: ${sleepMinutes}m"
          Text(
            text = label,
            color = if (sleepMinutes > 0) Color.White else Color.White.copy(alpha = 0.65f),
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
            color = if (playbackSpeed != 1.0f) Color.White else Color.White.copy(alpha = 0.65f),
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

@Composable
fun SyncedLyricsPane(
  lyricsResult: LyricsResult?,
  currentPositionMs: Long,
  reducedMotion: Boolean,
  onLineClick: (Long) -> Unit
) {
  if (lyricsResult == null) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
      CircularProgressIndicator(color = Color.White)
    }
    return
  }

  if (lyricsResult.synced.isNullOrEmpty()) {
    LazyColumn(
      modifier = Modifier.fillMaxSize(),
      contentPadding = PaddingValues(vertical = 32.dp)
    ) {
      item {
        Text(
          text = "Not synced",
          style = MaterialTheme.typography.bodySmall,
          color = Color.White.copy(alpha = 0.6f),
          modifier = Modifier.padding(bottom = 16.dp)
        )
        Text(
          text = lyricsResult.plain ?: "No lyrics found for this song.",
          style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp, lineHeight = 30.sp),
          color = Color.White.copy(alpha = 0.88f)
        )
      }
    }
    return
  }

  val synced = lyricsResult.synced
  val activeIndex = synced.indexOfLast { it.timestampMs <= currentPositionMs }.coerceAtLeast(0)
  val listState = rememberLazyListState()

  LaunchedEffect(activeIndex) {
    if (activeIndex >= 0 && activeIndex < synced.size) {
      if (reducedMotion) {
        listState.scrollToItem(activeIndex)
      } else {
        listState.animateScrollToItem(activeIndex)
      }
    }
  }

  LazyColumn(
    state = listState,
    modifier = Modifier.fillMaxSize(),
    verticalArrangement = Arrangement.spacedBy(18.dp),
    contentPadding = PaddingValues(top = 24.dp, bottom = 180.dp)
  ) {
    item {
      Text(
        text = "Synced lyrics",
        style = MaterialTheme.typography.bodySmall,
        color = Color.White.copy(alpha = 0.6f)
      )
    }
    itemsIndexed(synced) { index, line ->
      val isActive = index == activeIndex
      val lineAlpha by animateFloatAsState(
        targetValue = if (isActive) 1.0f else 0.35f,
        animationSpec = if (reducedMotion) snap() else tween(350),
        label = "lyric_alpha"
      )
      Text(
        text = line.text.ifBlank { "..." },
        style = MaterialTheme.typography.displayLarge.copy(
          fontSize = 24.sp,
          lineHeight = 31.sp,
          fontWeight = FontWeight.Bold
        ),
        color = Color.White,
        textAlign = TextAlign.Start,
        modifier = Modifier
          .fillMaxWidth()
          .alpha(lineAlpha)
          .blur(if (isActive || reducedMotion) 0.dp else 1.dp)
          .clickable { onLineClick(line.timestampMs) }
          .padding(vertical = 4.dp)
      )
    }
  }
}

fun formatTime(ms: Long): String {
  val totalSec = (ms / 1000).coerceAtLeast(0)
  val min = totalSec / 60
  val sec = totalSec % 60
  return "$min:${String.format("%02d", sec)}"
}
