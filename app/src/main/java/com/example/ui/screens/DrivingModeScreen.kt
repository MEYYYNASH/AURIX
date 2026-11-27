package com.example.ui.screens

import android.app.Activity
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.LyricsResult
import com.example.model.Song
import com.example.playback.AudioOutputRoute
import com.example.playback.AudioOutputRouteInfo
import com.example.ui.theme.AurixDesignTokens
import kotlinx.coroutines.delay

/**
 * Driving / Simple Mode: high-contrast, oversized touch targets (68–88dp) designed
 * for glanceable, low-distraction playback in a car or on a motorbike mount.
 * Keeps the screen awake while open and restores normal screen timeout on exit.
 */
@Composable
fun DrivingModeScreen(
  song: Song?,
  isPlaying: Boolean,
  isShuffle: Boolean,
  djAutoMixEnabled: Boolean,
  audioOutputInfo: AudioOutputRouteInfo,
  lyricsResult: LyricsResult?,
  positionProvider: () -> Long,
  lyricOffsetMs: Long,
  accentColor: Color,
  onPlayPause: () -> Unit,
  onNext: () -> Unit,
  onPrevious: () -> Unit,
  onToggleFavorite: () -> Unit,
  onToggleShuffle: () -> Unit,
  onToggleDjAutoMix: () -> Unit,
  onClose: () -> Unit
) {
  BackHandler { onClose() }

  val view = LocalView.current
  DisposableEffect(view) {
    val window = (view.context as? Activity)?.window
    window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    onDispose {
      window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
  }

  // Active synced lyric line (lightweight 350ms tick only when synced lines exist)
  var activeLyricText by remember(song?.id) { mutableStateOf("") }
  val syncedLines = lyricsResult?.synced.orEmpty()
  LaunchedEffect(song?.id, isPlaying, syncedLines, lyricOffsetMs) {
    if (syncedLines.isEmpty()) {
      activeLyricText = ""
      return@LaunchedEffect
    }
    while (true) {
      val pos = (positionProvider() + lyricOffsetMs).coerceAtLeast(0L)
      val line = syncedLines.lastOrNull { it.timestampMs <= pos + 160L }?.text.orEmpty()
      if (line != activeLyricText) activeLyricText = line
      delay(if (isPlaying) 320L else 900L)
    }
  }

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(Color(0xFF060910))
      .background(
        Brush.radialGradient(
          colors = listOf(accentColor.copy(alpha = 0.22f), Color.Transparent),
          radius = 950f
        )
      )
      .testTag("driving_mode_screen")
  ) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .windowInsetsPadding(WindowInsets.safeDrawing)
        .padding(horizontal = 24.dp, vertical = 18.dp),
      verticalArrangement = Arrangement.SpaceBetween,
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      // Top Bar: Output Route Pill + Large Exit Button
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        Surface(
          shape = CircleShape,
          color = Color.White.copy(alpha = 0.10f),
          border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f))
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Icon(
              imageVector = when (audioOutputInfo.route) {
                AudioOutputRoute.BLUETOOTH -> Icons.Rounded.BluetoothAudio
                AudioOutputRoute.HEADPHONES -> Icons.Rounded.Headphones
                AudioOutputRoute.SPEAKER -> Icons.Rounded.DirectionsCar
              },
              contentDescription = null,
              tint = accentColor,
              modifier = Modifier.size(20.dp)
            )
            Text(
              text = audioOutputInfo.deviceName.ifBlank {
                when (audioOutputInfo.route) {
                  AudioOutputRoute.BLUETOOTH -> stringResource(id = R.string.audio_route_bluetooth)
                  AudioOutputRoute.HEADPHONES -> stringResource(id = R.string.audio_route_headphones)
                  AudioOutputRoute.SPEAKER -> stringResource(id = R.string.driving_mode_title)
                }
              },
              style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
              color = Color.White,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
          }
        }

        Surface(
          onClick = onClose,
          shape = CircleShape,
          color = Color.White.copy(alpha = 0.12f),
          border = BorderStroke(1.dp, Color.White.copy(alpha = 0.22f)),
          modifier = Modifier
            .defaultMinSize(minWidth = 88.dp, minHeight = 48.dp)
            .testTag("driving_mode_exit_button")
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            Icon(
              imageVector = Icons.Rounded.Close,
              contentDescription = stringResource(id = R.string.driving_mode_exit),
              tint = Color.White,
              modifier = Modifier.size(20.dp)
            )
            Text(
              text = stringResource(id = R.string.driving_mode_exit),
              style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
              color = Color.White
            )
          }
        }
      }

      // Center Track Hero (Glanceable large typography + crisp cover)
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .weight(1f),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        if (song != null) {
          Box(
            modifier = Modifier
              .size(164.dp)
              .clip(RoundedCornerShape(30.dp))
              .border(1.5.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(30.dp))
          ) {
            SongArtwork(
              song = song,
              modifier = Modifier.fillMaxSize(),
              cornerRadius = 30.dp,
              iconSize = 56.dp
            )
          }

          Spacer(modifier = Modifier.height(22.dp))

          Text(
            text = song.title,
            style = MaterialTheme.typography.headlineMedium.copy(
              fontSize = 28.sp,
              lineHeight = 36.sp,
              fontWeight = FontWeight.ExtraBold
            ),
            color = Color.White,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
          )

          Spacer(modifier = Modifier.height(6.dp))

          Text(
            text = song.artist,
            style = MaterialTheme.typography.titleMedium.copy(
              fontSize = 18.sp,
              fontWeight = FontWeight.SemiBold
            ),
            color = Color.White.copy(alpha = 0.72f),
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )

          if (activeLyricText.isNotBlank()) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
              text = activeLyricText,
              style = MaterialTheme.typography.bodyLarge.copy(
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold
              ),
              color = accentColor,
              textAlign = TextAlign.Center,
              maxLines = 2,
              overflow = TextOverflow.Ellipsis,
              modifier = Modifier.padding(horizontal = 8.dp)
            )
          }
        } else {
          Icon(
            imageVector = Icons.Rounded.DirectionsCar,
            contentDescription = null,
            tint = accentColor,
            modifier = Modifier.size(64.dp)
          )
          Spacer(modifier = Modifier.height(14.dp))
          Text(
            text = stringResource(id = R.string.driving_mode_empty),
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = Color.White,
            textAlign = TextAlign.Center
          )
        }
      }

      // Giant Primary Transport Controls (68dp - 88dp touch targets)
      Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(22.dp),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceEvenly,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Surface(
            onClick = onPrevious,
            shape = CircleShape,
            color = Color.White.copy(alpha = 0.12f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.22f)),
            modifier = Modifier
              .size(72.dp)
              .testTag("driving_prev_button")
          ) {
            Box(contentAlignment = Alignment.Center) {
              Icon(
                imageVector = Icons.Rounded.SkipPrevious,
                contentDescription = "Previous",
                tint = Color.White,
                modifier = Modifier.size(38.dp)
              )
            }
          }

          Surface(
            onClick = onPlayPause,
            shape = CircleShape,
            color = Color.Transparent,
            modifier = Modifier
              .size(92.dp)
              .clip(CircleShape)
              .background(
                Brush.linearGradient(
                  listOf(accentColor, AurixDesignTokens.AccentB)
                )
              )
              .testTag("driving_play_pause_button")
          ) {
            Box(contentAlignment = Alignment.Center) {
              Icon(
                imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play",
                tint = Color(0xFF06101C),
                modifier = Modifier.size(48.dp)
              )
            }
          }

          Surface(
            onClick = onNext,
            shape = CircleShape,
            color = Color.White.copy(alpha = 0.12f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.22f)),
            modifier = Modifier
              .size(72.dp)
              .testTag("driving_next_button")
          ) {
            Box(contentAlignment = Alignment.Center) {
              Icon(
                imageVector = Icons.Rounded.SkipNext,
                contentDescription = "Next",
                tint = Color.White,
                modifier = Modifier.size(38.dp)
              )
            }
          }
        }

        // Secondary oversized pills: Love, Shuffle, DJ Auto-Mix
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          val isFav = song?.isFavorite == true
          DrivingQuickPill(
            icon = if (isFav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
            label = stringResource(id = if (isFav) R.string.action_liked else R.string.action_like),
            active = isFav,
            activeColor = Color(0xFFEF4444),
            testTag = "driving_love_button",
            onClick = onToggleFavorite,
            modifier = Modifier.weight(1f)
          )
          DrivingQuickPill(
            icon = Icons.Rounded.Shuffle,
            label = "Shuffle",
            active = isShuffle,
            activeColor = accentColor,
            testTag = "driving_shuffle_button",
            onClick = onToggleShuffle,
            modifier = Modifier.weight(1f)
          )
          DrivingQuickPill(
            icon = Icons.Rounded.GraphicEq,
            label = "DJ Mix",
            active = djAutoMixEnabled,
            activeColor = AurixDesignTokens.AccentB,
            testTag = "driving_dj_mix_button",
            onClick = onToggleDjAutoMix,
            modifier = Modifier.weight(1f)
          )
        }
      }
    }
  }
}

@Composable
private fun DrivingQuickPill(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  label: String,
  active: Boolean,
  activeColor: Color,
  testTag: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  Surface(
    onClick = onClick,
    shape = RoundedCornerShape(20.dp),
    color = if (active) activeColor.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.08f),
    border = BorderStroke(
      1.2.dp,
      if (active) activeColor.copy(alpha = 0.72f) else Color.White.copy(alpha = 0.16f)
    ),
    modifier = modifier
      .height(58.dp)
      .testTag(testTag)
  ) {
    Row(
      modifier = Modifier.fillMaxSize(),
      horizontalArrangement = Arrangement.Center,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Icon(
        imageVector = icon,
        contentDescription = label,
        tint = if (active) activeColor else Color.White,
        modifier = Modifier.size(22.dp)
      )
      Spacer(modifier = Modifier.width(6.dp))
      Text(
        text = label,
        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
        color = if (active) activeColor else Color.White,
        maxLines = 1
      )
    }
  }
}
