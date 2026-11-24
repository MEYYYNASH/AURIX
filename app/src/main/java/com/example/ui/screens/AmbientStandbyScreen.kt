package com.example.ui.screens

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.BatteryStd
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil.compose.AsyncImage
import com.example.R
import com.example.model.LyricsResult
import com.example.model.Song
import com.example.playback.AudioSpectrumBus
import com.example.widget.AurixWidgetController
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

/**
 * Full-screen OLED Ambient Standby Mode ("Desk Clock Player").
 * Pure pitch-black canvas with subtle album-tinted aura, giant minimalist clock,
 * spinning vinyl or clean album art, live synced lyrics, subtle audio visualizer,
 * and auto-hiding translucent transport controls.
 */
@Composable
fun AmbientStandbyScreen(
  song: Song?,
  isPlaying: Boolean,
  positionProvider: () -> Long,
  lyricsResult: LyricsResult?,
  lyricOffsetMs: Long,
  spectrumBars: FloatArray,
  vinylStyle: Boolean,
  reducedMotion: Boolean,
  orbiMood: OrbiMood = OrbiMood.CLASSIC_IDLE,
  orbiTheme: OrbiCompanionTheme = OrbiCompanionTheme.CLASSIC,
  onToggleVinylStyle: (Boolean) -> Unit,
  onPlayPause: () -> Unit,
  onNext: () -> Unit,
  onPrevious: () -> Unit,
  onClose: () -> Unit
) {
  BackHandler { onClose() }

  val context = LocalContext.current
  val view = LocalView.current

  // Keep screen awake & enter immersive full-screen cleanly while in Standby Mode
  DisposableEffect(view) {
    val activity = view.context as? Activity
    val window = activity?.window
    window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    val controller = window?.let { WindowCompat.getInsetsController(it, view) }
    controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    controller?.hide(WindowInsetsCompat.Type.systemBars())

    onDispose {
      window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
      controller?.show(WindowInsetsCompat.Type.systemBars())
    }
  }

  // Real battery level & charging telemetry
  var batteryPercent by remember { mutableIntStateOf(-1) }
  var isCharging by remember { mutableStateOf(false) }
  DisposableEffect(context) {
    fun readBatteryIntent(intent: Intent?) {
      if (intent == null) return
      val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
      val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
      if (level >= 0) {
        batteryPercent = ((level * 100f) / scale).toInt().coerceIn(0, 100)
      }
      val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
      isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
        status == BatteryManager.BATTERY_STATUS_FULL
    }

    val receiver = object : BroadcastReceiver() {
      override fun onReceive(ctx: Context?, intent: Intent?) {
        readBatteryIntent(intent)
      }
    }
    val initial = runCatching {
      context.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    }.getOrNull()
    readBatteryIntent(initial)

    onDispose {
      runCatching { context.unregisterReceiver(receiver) }
    }
  }

  // Live clock tick (every second for exact minute rollovers)
  var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
  LaunchedEffect(Unit) {
    while (true) {
      nowMillis = System.currentTimeMillis()
      delay(1_000L)
    }
  }

  // Live synced lyric line polling (lightweight, runs only while screen is open)
  var livePositionMs by remember { mutableLongStateOf(positionProvider()) }
  LaunchedEffect(song?.id, isPlaying) {
    livePositionMs = positionProvider()
    while (isPlaying) {
      livePositionMs = positionProvider()
      delay(180L)
    }
  }

  // Auto-hide controls after 4 seconds of inactivity so the desk clock stays distraction-free
  var controlsVisible by remember { mutableStateOf(true) }
  var lastInteractionToken by remember { mutableIntStateOf(0) }
  LaunchedEffect(controlsVisible, lastInteractionToken) {
    if (controlsVisible) {
      delay(4_200L)
      controlsVisible = false
    }
  }

  val timeText = remember(nowMillis) {
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(nowMillis))
  }
  val dateText = remember(nowMillis) {
    SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(Date(nowMillis))
  }

  val (accentPrimary, accentSecondary) = rememberArtworkColors(
    song?.artworkUrl.orEmpty(),
    song?.title.orEmpty()
  )

  val lyricSnap = remember(lyricsResult, livePositionMs, lyricOffsetMs) {
    AurixWidgetController.resolveSyncedLyricLines(
      syncedLines = lyricsResult?.synced,
      plainLyrics = lyricsResult?.plain,
      positionMs = livePositionMs,
      lyricOffsetMs = lyricOffsetMs,
      noLyricsLabel = ""
    )
  }

  val rootInteraction = remember { MutableInteractionSource() }

  BoxWithConstraints(
    modifier = Modifier
      .fillMaxSize()
      .background(Color.Black)
      .drawBehind {
        // Ultra-subtle OLED-friendly ambient radial aura
        drawCircle(
          brush = Brush.radialGradient(
            colors = listOf(
              accentPrimary.copy(alpha = 0.16f),
              accentSecondary.copy(alpha = 0.06f),
              Color.Transparent
            ),
            center = Offset(size.width * 0.25f, size.height * 0.35f),
            radius = max(size.width, size.height) * 0.65f
          ),
          radius = max(size.width, size.height) * 0.65f,
          center = Offset(size.width * 0.25f, size.height * 0.35f)
        )
      }
      .clickable(interactionSource = rootInteraction, indication = null) {
        controlsVisible = !controlsVisible
        lastInteractionToken += 1
      }
      .safeDrawingPadding()
      .testTag("ambient_standby_screen")
  ) {
    val containerMaxWidth = maxWidth
    val containerMaxHeight = maxHeight
    val isLandscape = containerMaxWidth > containerMaxHeight

    if (isLandscape) {
      // Horizontal Desk Clock Layout (Side-by-side)
      Row(
        modifier = Modifier
          .fillMaxSize()
          .padding(horizontal = 36.dp, vertical = 22.dp),
        horizontalArrangement = Arrangement.spacedBy(36.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        // Left Column: Giant OLED Clock + Date + Battery + Spinning Vinyl / Art
        Column(
          modifier = Modifier
            .weight(0.46f)
            .fillMaxHeight(),
          verticalArrangement = Arrangement.Center,
          horizontalAlignment = Alignment.CenterHorizontally
        ) {
          StandbyClockHeader(
            timeText = timeText,
            dateText = dateText,
            batteryPercent = batteryPercent,
            isCharging = isCharging,
            accent = accentPrimary,
            compact = true,
            orbiMood = orbiMood,
            orbiTheme = orbiTheme,
            isPlaying = isPlaying,
            reducedMotion = reducedMotion,
            beatEnergy = computeOrbiBeatEnergy(spectrumBars, isPlaying, reducedMotion)
          )
          Spacer(modifier = Modifier.height(18.dp))
          StandbyArtworkDisc(
            song = song,
            isPlaying = isPlaying,
            vinylStyle = vinylStyle,
            reducedMotion = reducedMotion,
            accent = accentPrimary,
            size = (containerMaxHeight * 0.42f).coerceIn(112.dp, 180.dp)
          )
        }

        // Right Column: Live Synced Lyrics + Minimal Visualizer + Track Title
        Column(
          modifier = Modifier
            .weight(0.54f)
            .fillMaxHeight(),
          verticalArrangement = Arrangement.Center,
          horizontalAlignment = Alignment.Start
        ) {
          StandbyLyricsAndTrackPanel(
            song = song,
            lyricSnap = lyricSnap,
            spectrumBars = spectrumBars,
            isPlaying = isPlaying,
            accent = accentPrimary,
            reducedMotion = reducedMotion,
            centered = false
          )
        }
      }
    } else {
      // Portrait Dock Layout (Centered vertical flow, spacious & clean)
      Column(
        modifier = Modifier
          .fillMaxSize()
          .padding(horizontal = 28.dp, vertical = 30.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceEvenly
      ) {
        StandbyClockHeader(
          timeText = timeText,
          dateText = dateText,
          batteryPercent = batteryPercent,
          isCharging = isCharging,
          accent = accentPrimary,
          compact = false,
          orbiMood = orbiMood,
          orbiTheme = orbiTheme,
          isPlaying = isPlaying,
          reducedMotion = reducedMotion,
          beatEnergy = computeOrbiBeatEnergy(spectrumBars, isPlaying, reducedMotion)
        )

        StandbyArtworkDisc(
          song = song,
          isPlaying = isPlaying,
          vinylStyle = vinylStyle,
          reducedMotion = reducedMotion,
          accent = accentPrimary,
          size = (containerMaxWidth * 0.50f).coerceIn(156.dp, 224.dp)
        )

        StandbyLyricsAndTrackPanel(
          song = song,
          lyricSnap = lyricSnap,
          spectrumBars = spectrumBars,
          isPlaying = isPlaying,
          accent = accentPrimary,
          reducedMotion = reducedMotion,
          centered = true
        )
      }
    }

    // Minimal auto-hiding top-right controls (Vinyl/Cover toggle + Close)
    AnimatedVisibility(
      visible = controlsVisible,
      enter = if (reducedMotion) fadeIn(snap()) else fadeIn(tween(220)),
      exit = if (reducedMotion) fadeOut(snap()) else fadeOut(tween(300)),
      modifier = Modifier
        .align(Alignment.TopEnd)
        .padding(16.dp)
    ) {
      Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Surface(
          onClick = {
            lastInteractionToken += 1
            onToggleVinylStyle(!vinylStyle)
          },
          shape = CircleShape,
          color = Color.White.copy(alpha = 0.10f),
          border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)),
          modifier = Modifier.testTag("standby_toggle_vinyl_button")
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            Icon(
              imageVector = if (vinylStyle) Icons.Rounded.Album else Icons.Rounded.Image,
              contentDescription = null,
              tint = Color.White,
              modifier = Modifier.size(16.dp)
            )
            Text(
              text = stringResource(
                id = if (vinylStyle) R.string.standby_style_vinyl else R.string.standby_style_art
              ),
              style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
              color = Color.White
            )
          }
        }

        IconButton(
          onClick = onClose,
          modifier = Modifier
            .minimumInteractiveComponentSize()
            .size(44.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.10f))
            .border(1.dp, Color.White.copy(alpha = 0.16f), CircleShape)
            .testTag("standby_exit_button")
        ) {
          Icon(
            imageVector = Icons.Rounded.Close,
            contentDescription = stringResource(id = R.string.standby_exit_cd),
            tint = Color.White
          )
        }
      }
    }

    // Minimal auto-hiding floating transport dock at bottom
    AnimatedVisibility(
      visible = controlsVisible && song != null,
      enter = if (reducedMotion) fadeIn(snap()) else fadeIn(tween(220)) + slideInVertically { it / 2 },
      exit = if (reducedMotion) fadeOut(snap()) else fadeOut(tween(300)) + slideOutVertically { it / 2 },
      modifier = Modifier
        .align(Alignment.BottomCenter)
        .padding(bottom = 18.dp)
    ) {
      Surface(
        shape = CircleShape,
        color = Color(0xCC121722),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f))
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          IconButton(
            onClick = {
              lastInteractionToken += 1
              onPrevious()
            },
            modifier = Modifier
              .minimumInteractiveComponentSize()
              .testTag("standby_prev_button")
          ) {
            Icon(
              imageVector = Icons.Rounded.SkipPrevious,
              contentDescription = stringResource(id = R.string.widget_cd_prev),
              tint = Color.White,
              modifier = Modifier.size(26.dp)
            )
          }

          IconButton(
            onClick = {
              lastInteractionToken += 1
              onPlayPause()
            },
            modifier = Modifier
              .minimumInteractiveComponentSize()
              .size(48.dp)
              .clip(CircleShape)
              .background(Color.White)
              .testTag("standby_play_pause_button")
          ) {
            Icon(
              imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
              contentDescription = stringResource(id = R.string.widget_cd_play_pause),
              tint = Color.Black,
              modifier = Modifier.size(26.dp)
            )
          }

          IconButton(
            onClick = {
              lastInteractionToken += 1
              onNext()
            },
            modifier = Modifier
              .minimumInteractiveComponentSize()
              .testTag("standby_next_button")
          ) {
            Icon(
              imageVector = Icons.Rounded.SkipNext,
              contentDescription = stringResource(id = R.string.widget_cd_next),
              tint = Color.White,
              modifier = Modifier.size(26.dp)
            )
          }
        }
      }
    }
  }
}

@Composable
private fun StandbyClockHeader(
  timeText: String,
  dateText: String,
  batteryPercent: Int,
  isCharging: Boolean,
  accent: Color,
  compact: Boolean,
  orbiMood: OrbiMood = OrbiMood.CLASSIC_IDLE,
  orbiTheme: OrbiCompanionTheme = OrbiCompanionTheme.CLASSIC,
  isPlaying: Boolean = false,
  reducedMotion: Boolean = false,
  beatEnergy: Float = 0f
) {
  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(6.dp)
  ) {
    Text(
      text = timeText,
      style = MaterialTheme.typography.displayLarge.copy(
        fontSize = if (compact) 68.sp else 82.sp,
        fontWeight = FontWeight.Light,
        letterSpacing = (-2).sp
      ),
      color = Color.White,
      modifier = Modifier.testTag("standby_clock_text")
    )
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      Text(
        text = dateText,
        style = MaterialTheme.typography.bodyMedium.copy(
          fontWeight = FontWeight.Medium,
          letterSpacing = 0.4.sp
        ),
        color = Color.White.copy(alpha = 0.62f)
      )
      OrbiMiniMoodBadge(
        mood = orbiMood,
        theme = orbiTheme,
        isPlaying = isPlaying,
        reducedMotion = reducedMotion,
        beatEnergy = beatEnergy,
        showLabel = true,
        modifier = Modifier.testTag("standby_orbi_mini_badge")
      )
      if (batteryPercent >= 0) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(3.dp),
          modifier = Modifier
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.08f))
            .padding(horizontal = 8.dp, vertical = 3.dp)
        ) {
          Icon(
            imageVector = if (isCharging) Icons.Rounded.BatteryChargingFull else Icons.Rounded.BatteryStd,
            contentDescription = null,
            tint = if (isCharging) Color(0xFF4ADE80) else accent.copy(alpha = 0.85f),
            modifier = Modifier.size(13.dp)
          )
          Text(
            text = if (isCharging) {
              stringResource(id = R.string.standby_charging, batteryPercent)
            } else {
              stringResource(id = R.string.standby_battery, batteryPercent)
            },
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
            color = Color.White.copy(alpha = 0.78f)
          )
        }
      }
    }
  }
}

@Composable
private fun StandbyArtworkDisc(
  song: Song?,
  isPlaying: Boolean,
  vinylStyle: Boolean,
  reducedMotion: Boolean,
  accent: Color,
  size: Dp
) {
  var spinAngle by remember { mutableFloatStateOf(0f) }
  LaunchedEffect(isPlaying, vinylStyle, reducedMotion) {
    if (!vinylStyle || !isPlaying || reducedMotion) return@LaunchedEffect
    while (true) {
      delay(16L)
      spinAngle = (spinAngle + 0.45f) % 360f
    }
  }

  val artworkModel = remember(song?.artworkUrl, song?.source) {
    song?.let { resolveArtworkModel(it.artworkUrl, it.source) }
  }

  if (vinylStyle) {
    Box(
      modifier = Modifier
        .size(size)
        .graphicsLayer { rotationZ = spinAngle },
      contentAlignment = Alignment.Center
    ) {
      Canvas(modifier = Modifier.fillMaxSize()) {
        val r = this.size.minDimension * 0.5f
        val c = Offset(this.size.width * 0.5f, this.size.height * 0.5f)
        drawCircle(
          brush = Brush.radialGradient(
            colors = listOf(Color(0xFF1A202D), Color(0xFF0A0D14), Color(0xFF141822), Color(0xFF06080D)),
            center = c,
            radius = r
          ),
          radius = r,
          center = c
        )
        for (i in 1..5) {
          drawCircle(
            color = Color.White.copy(alpha = 0.08f),
            radius = r * (0.56f + i * 0.075f),
            center = c,
            style = Stroke(width = 1.dp.toPx())
          )
        }
        drawCircle(
          color = accent.copy(alpha = 0.42f),
          radius = r - 1.5.dp.toPx(),
          center = c,
          style = Stroke(width = 1.5.dp.toPx())
        )
      }

      Box(
        modifier = Modifier
          .size(size * 0.52f)
          .clip(CircleShape)
          .background(Color(0xFF151F30))
          .border(1.5.dp, accent.copy(alpha = 0.55f), CircleShape),
        contentAlignment = Alignment.Center
      ) {
        if (artworkModel != null) {
          AsyncImage(
            model = artworkModel,
            contentDescription = song?.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
          )
        } else {
          Icon(
            imageVector = Icons.Rounded.MusicNote,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.65f),
            modifier = Modifier.size(size * 0.22f)
          )
        }
        // Center spindle hole
        Box(
          modifier = Modifier
            .size(size * 0.09f)
            .clip(CircleShape)
            .background(Color.Black)
            .border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape)
        )
      }
    }
  } else {
    val shape = RoundedCornerShape(24.dp)
    Box(
      modifier = Modifier
        .size(size)
        .clip(shape)
        .background(Color(0xFF141A28))
        .border(1.dp, Color.White.copy(alpha = 0.14f), shape),
      contentAlignment = Alignment.Center
    ) {
      if (artworkModel != null) {
        AsyncImage(
          model = artworkModel,
          contentDescription = song?.title,
          contentScale = ContentScale.Crop,
          modifier = Modifier.fillMaxSize()
        )
      } else {
        Icon(
          imageVector = Icons.Rounded.MusicNote,
          contentDescription = null,
          tint = Color.White.copy(alpha = 0.65f),
          modifier = Modifier.size(size * 0.28f)
        )
      }
    }
  }
}

@Composable
private fun StandbyLyricsAndTrackPanel(
  song: Song?,
  lyricSnap: com.example.widget.SyncedLyricLineSnapshot,
  spectrumBars: FloatArray,
  isPlaying: Boolean,
  accent: Color,
  reducedMotion: Boolean,
  centered: Boolean
) {
  val horizontalAlign = if (centered) Alignment.CenterHorizontally else Alignment.Start
  val textAlign = if (centered) TextAlign.Center else TextAlign.Start

  Column(
    modifier = Modifier.fillMaxWidth(),
    horizontalAlignment = horizontalAlign,
    verticalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    if (song == null) {
      Text(
        text = stringResource(id = R.string.standby_no_track),
        style = MaterialTheme.typography.titleMedium,
        color = Color.White.copy(alpha = 0.55f),
        textAlign = textAlign
      )
      return
    }

    if (lyricSnap.hasLyrics && lyricSnap.currentLine.isNotBlank()) {
      AnimatedContent(
        targetState = lyricSnap.currentLine to lyricSnap.nextLine,
        transitionSpec = {
          if (reducedMotion) {
            fadeIn(snap()) togetherWith fadeOut(snap())
          } else {
            (slideInVertically(tween(340, easing = FastOutSlowInEasing)) { it / 3 } + fadeIn(tween(260))) togetherWith
              (slideOutVertically(tween(260)) { -it / 3 } + fadeOut(tween(200)))
          }
        },
        label = "standby_lyric_transition"
      ) { (currentLine, nextLine) ->
        Column(
          modifier = Modifier.fillMaxWidth(),
          horizontalAlignment = horizontalAlign,
          verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          Text(
            text = currentLine,
            style = MaterialTheme.typography.headlineSmall.copy(
              fontWeight = FontWeight.Bold,
              lineHeight = 32.sp
            ),
            color = Color.White,
            textAlign = textAlign,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
          )
          if (nextLine.isNotBlank()) {
            Text(
              text = nextLine,
              style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
              color = Color.White.copy(alpha = 0.44f),
              textAlign = textAlign,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
          }
        }
      }
    }

    // Subtle real-time minimalist audio visualizer strip
    StandbyMinimalVisualizer(
      bars = spectrumBars,
      isPlaying = isPlaying,
      accent = accent,
      reducedMotion = reducedMotion,
      modifier = Modifier
        .fillMaxWidth(if (centered) 0.62f else 0.72f)
        .height(22.dp)
    )

    // Clean Song Title + Artist
    Column(
      horizontalAlignment = horizontalAlign,
      verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
      Text(
        text = song.title,
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
        color = Color.White.copy(alpha = 0.92f),
        textAlign = textAlign,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
      Text(
        text = song.artist.ifBlank { "AURIX" },
        style = MaterialTheme.typography.bodySmall,
        color = Color.White.copy(alpha = 0.56f),
        textAlign = textAlign,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
    }
  }
}

@Composable
private fun StandbyMinimalVisualizer(
  bars: FloatArray,
  isPlaying: Boolean,
  accent: Color,
  reducedMotion: Boolean,
  modifier: Modifier = Modifier
) {
  val infinite = rememberInfiniteTransition(label = "standby_vis_idle")
  val breath by infinite.animateFloat(
    initialValue = 0.25f,
    targetValue = 0.72f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = 1200, easing = LinearEasing),
      repeatMode = RepeatMode.Reverse
    ),
    label = "standby_vis_breath"
  )

  Canvas(modifier = modifier) {
    val count = 18
    val gap = 4.dp.toPx()
    val barW = ((size.width - gap * (count - 1)) / count).coerceAtLeast(2.dp.toPx())
    val hasRealSignal = bars.any { it > 0.03f }

    for (i in 0 until count) {
      val mappedIdx = ((i.toFloat() / count) * AudioSpectrumBus.BAR_COUNT.coerceAtLeast(1))
        .toInt()
        .coerceIn(0, (bars.size - 1).coerceAtLeast(0))
      val rawLevel = when {
        !isPlaying -> 0.14f
        hasRealSignal -> bars[mappedIdx].coerceIn(0.14f, 1f)
        reducedMotion -> 0.30f
        else -> {
          val wave = kotlin.math.abs(kotlin.math.sin((i * 0.55f) + breath * 3.14f)).toFloat()
          (0.18f + wave * 0.58f).coerceIn(0.15f, 0.85f)
        }
      }
      val barH = (size.height * rawLevel).coerceAtLeast(3.dp.toPx())
      val x = i * (barW + gap)
      val y = (size.height - barH) * 0.5f
      drawRoundRect(
        color = accent.copy(alpha = if (isPlaying) 0.72f else 0.28f),
        topLeft = Offset(x, y),
        size = Size(barW, barH),
        cornerRadius = CornerRadius(barW * 0.5f, barW * 0.5f)
      )
    }
  }
}
