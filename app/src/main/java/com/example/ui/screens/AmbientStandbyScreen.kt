package com.example.ui.screens

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.BatteryManager
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Palette
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.input.pointer.pointerInput
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
import com.example.model.APP_ICON_ARTWORK
import com.example.model.LyricsResult
import com.example.model.Song
import com.example.ui.theme.KhmerLyricsFont
import com.example.ui.theme.rememberKhmerLyricsText
import com.example.ui.theme.rememberKhmerTitleText
import com.example.widget.AurixWidgetController
import com.example.widget.SyncedLyricLineSnapshot
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

private const val BURN_IN_DIM_TIMEOUT_MS = 120_000L // 2 minutes without touch
private const val BURN_IN_DRIFT_CYCLE_MS = 90_000   // 90s slow orbital burn-in drift
private const val CONTROLS_AUTO_HIDE_MS = 5_000L    // 5 seconds inactivity hide
private const val MAX_TILT_DEGREES = 8f             // 8° max 3D gyro tilt
const val STANDBY_PROGRESS_SEGMENTS = 17

/**
 * Full-screen Bedside & Charging "Standby Mode" screen for AURIX.
 *
 * - Keeps screen awake (FLAG_KEEP_SCREEN_ON) and hides system bars while open.
 * - Reuses AURIX's real player state (zero demo or fake data).
 * - 3 live-switchable layouts saved to settings: Split (default), Poster, Lyrics.
 * - 10 Clock Styles + 12h/24h toggle + 2s soft blinking colon + tabular numbers.
 * - 5 Color Moods (Mint default, Lo-fi, Sunset, Mono, Auto cover palette lerp) + single 17% radial glow & vignette.
 * - 3D Gyro Parallax (up to 8° album art tilt with moving glare, opposite depth shift, slow neutral recentering).
 * - Burn-in protection: 90s pixel drift + ~40% dim after 2 minutes of inactivity.
 * - Top-right 44dp glass circle buttons: Layout, Clock Style, Mood, Gyro, Close.
 */
@Composable
fun AmbientStandbyScreen(
  song: Song?,
  isPlaying: Boolean,
  positionProvider: () -> Long,
  durationMs: Long = 0L,
  albumTitleOverride: String? = null,
  customAlbumCoverUrl: String? = null,
  lyricsResult: LyricsResult?,
  lyricOffsetMs: Long,
  spectrumBars: FloatArray,
  vinylStyle: Boolean = false,
  reducedMotion: Boolean,
  clockStyle: StandbyClockStyle = StandbyClockStyle.THIN,
  use24h: Boolean = true,
  colorMood: StandbyColorMood = StandbyColorMood.MINT,
  layout: StandbyLayout = StandbyLayout.SPLIT,
  gyroEnabled: Boolean = true,
  orbiMood: OrbiMood = OrbiMood.CLASSIC_IDLE,
  orbiTheme: OrbiCompanionTheme = OrbiCompanionTheme.CLASSIC,
  khmerLyricsFont: KhmerLyricsFont = KhmerLyricsFont.KANTUMRUY_PRO,
  onSelectClockStyle: (StandbyClockStyle) -> Unit = {},
  onToggleUse24h: (Boolean) -> Unit = {},
  onSelectColorMood: (StandbyColorMood) -> Unit = {},
  onSelectLayout: (StandbyLayout) -> Unit = {},
  onToggleGyroEnabled: (Boolean) -> Unit = {},
  onToggleVinylStyle: (Boolean) -> Unit = {},
  onSeek: ((Long) -> Unit)? = null,
  onPlayPause: () -> Unit,
  onNext: () -> Unit,
  onPrevious: () -> Unit,
  onClose: () -> Unit
) {
  BackHandler { onClose() }

  val context = LocalContext.current
  val view = LocalView.current

  // Keep screen awake (wake lock flag) & enter immersive full-screen while Standby Mode is open
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

  // Real battery percentage & charging state
  var batteryPercent by remember { mutableIntStateOf(100) }
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

  // Live clock tick every 500ms for crisp blinking colon & exact minute rollovers
  var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
  var lastTouchTimestampMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
  LaunchedEffect(Unit) {
    while (true) {
      nowMillis = System.currentTimeMillis()
      delay(500L)
    }
  }

  // Live playback position polling from real player state
  var livePositionMs by remember { mutableLongStateOf(positionProvider().coerceAtLeast(0L)) }
  LaunchedEffect(song?.id, isPlaying) {
    livePositionMs = positionProvider().coerceAtLeast(0L)
    while (isPlaying) {
      livePositionMs = positionProvider().coerceAtLeast(0L)
      delay(200L)
    }
  }

  // Auto-hide controls after 5 seconds of inactivity
  var controlsVisible by remember { mutableStateOf(true) }
  var lastInteractionToken by remember { mutableIntStateOf(0) }
  var showClockPickerSheet by remember { mutableStateOf(false) }

  fun registerUserTouch() {
    lastTouchTimestampMs = System.currentTimeMillis()
    lastInteractionToken += 1
  }

  LaunchedEffect(controlsVisible, lastInteractionToken, showClockPickerSheet) {
    if (controlsVisible && !showClockPickerSheet) {
      delay(CONTROLS_AUTO_HIDE_MS)
      controlsVisible = false
    }
  }

  // Burn-in protection: dim brightness to ~40% after 2 minutes of no touch
  val isBurnInDimmed = (nowMillis - lastTouchTimestampMs) >= BURN_IN_DIM_TIMEOUT_MS
  val burnInBrightnessAlpha by animateFloatAsState(
    targetValue = if (isBurnInDimmed) 0.42f else 1.0f,
    animationSpec = if (reducedMotion) snap() else tween(durationMillis = 1600),
    label = "standby_burn_in_dim"
  )

  // Burn-in protection: slow pixel drift over a 90s cycle
  val infiniteTransition = rememberInfiniteTransition(label = "standby_infinite")
  val driftPhase by infiniteTransition.animateFloat(
    initialValue = 0f,
    targetValue = (Math.PI * 2.0).toFloat(),
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = BURN_IN_DRIFT_CYCLE_MS, easing = LinearEasing),
      repeatMode = RepeatMode.Restart
    ),
    label = "standby_burn_in_drift"
  )
  val driftOffsetX = if (reducedMotion) 0.dp else (sin(driftPhase) * 4.5f).dp
  val driftOffsetY = if (reducedMotion) 0.dp else (cos(driftPhase * 0.75f) * 3.5f).dp

  // Gentle idle sway for 3D parallax when stationary
  val swayPhase by infiniteTransition.animateFloat(
    initialValue = 0f,
    targetValue = (Math.PI * 2.0).toFloat(),
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = 10_000, easing = LinearEasing),
      repeatMode = RepeatMode.Restart
    ),
    label = "standby_idle_sway"
  )

  // Real Hardware Gyro / Rotation / Accelerometer Parallax with Slowly Recentering Neutral Point (8° cap)
  var sensorTiltX by remember { mutableFloatStateOf(0f) }
  var sensorTiltY by remember { mutableFloatStateOf(0f) }
  var pointerTiltX by remember { mutableFloatStateOf(0f) }
  var pointerTiltY by remember { mutableFloatStateOf(0f) }

  DisposableEffect(context, gyroEnabled, reducedMotion) {
    if (!gyroEnabled || reducedMotion) {
      sensorTiltX = 0f
      sensorTiltY = 0f
      return@DisposableEffect onDispose {}
    }
    val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    val rotationSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
      ?: sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
      ?: sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    var neutralX = 0f
    var neutralY = 0f
    var initialized = false
    val rotationMatrix = FloatArray(9)
    val orientationAngles = FloatArray(3)

    val listener = object : SensorEventListener {
      override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return
        val (rawX, rawY) = if (
          event.sensor.type == Sensor.TYPE_GAME_ROTATION_VECTOR ||
          event.sensor.type == Sensor.TYPE_ROTATION_VECTOR
        ) {
          runCatching {
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
            SensorManager.getOrientation(rotationMatrix, orientationAngles)
            val rollDeg = Math.toDegrees(orientationAngles[2].toDouble()).toFloat()
            val pitchDeg = Math.toDegrees(orientationAngles[1].toDouble()).toFloat()
            rollDeg to pitchDeg
          }.getOrDefault(0f to 0f)
        } else {
          val gx = event.values.getOrNull(0) ?: 0f
          val gy = event.values.getOrNull(1) ?: 0f
          (-gx * 4.0f) to (gy * 4.0f)
        }

        if (!initialized) {
          neutralX = rawX
          neutralY = rawY
          initialized = true
        } else {
          neutralX += (rawX - neutralX) * 0.022f
          neutralY += (rawY - neutralY) * 0.022f
        }

        val deltaX = (rawX - neutralX).coerceIn(-MAX_TILT_DEGREES, MAX_TILT_DEGREES) / MAX_TILT_DEGREES
        val deltaY = (rawY - neutralY).coerceIn(-MAX_TILT_DEGREES, MAX_TILT_DEGREES) / MAX_TILT_DEGREES
        sensorTiltX = sensorTiltX * 0.82f + deltaX * 0.18f
        sensorTiltY = sensorTiltY * 0.82f + deltaY * 0.18f
      }

      override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    if (rotationSensor != null) {
      runCatching {
        sensorManager?.registerListener(listener, rotationSensor, SensorManager.SENSOR_DELAY_GAME)
      }
    }

    onDispose {
      runCatching { sensorManager?.unregisterListener(listener) }
    }
  }

  val idleSwayX = if (gyroEnabled && !reducedMotion) sin(swayPhase) * 0.14f else 0f
  val idleSwayY = if (gyroEnabled && !reducedMotion) cos(swayPhase * 0.8f) * 0.12f else 0f
  val effectiveTiltX = if (!gyroEnabled || reducedMotion) {
    0f
  } else {
    (sensorTiltX + pointerTiltX * 0.45f + idleSwayX).coerceIn(-1f, 1f)
  }
  val effectiveTiltY = if (!gyroEnabled || reducedMotion) {
    0f
  } else {
    (sensorTiltY + pointerTiltY * 0.45f + idleSwayY).coerceIn(-1f, 1f)
  }

  val smoothTiltX by animateFloatAsState(
    targetValue = effectiveTiltX,
    animationSpec = if (reducedMotion) snap() else tween(140, easing = LinearEasing),
    label = "standby_tilt_x"
  )
  val smoothTiltY by animateFloatAsState(
    targetValue = effectiveTiltY,
    animationSpec = if (reducedMotion) snap() else tween(140, easing = LinearEasing),
    label = "standby_tilt_y"
  )

  // Resolve real cover URL (song metadata cover or custom album cover)
  val effectiveCoverUrl = remember(song?.artworkUrl, customAlbumCoverUrl) {
    val custom = customAlbumCoverUrl?.takeIf { it.isNotBlank() && it != APP_ICON_ARTWORK }
    val songArt = song?.artworkUrl?.takeIf { it.isNotBlank() && it != APP_ICON_ARTWORK }
    custom ?: songArt
  }
  val artworkModel = remember(effectiveCoverUrl, song?.source) {
    effectiveCoverUrl?.let { resolveArtworkModel(it, song?.source ?: "Local") }
  }

  // Extract palette from real cover art for Auto mode and lerp smoothly (~600ms) when song/mood changes
  val (coverPrimary, coverSecondary) = rememberArtworkColors(
    effectiveCoverUrl.orEmpty(),
    song?.title.orEmpty()
  )
  val targetPalette = remember(colorMood, coverPrimary, coverSecondary, song != null) {
    resolveStandbyMoodPalette(
      mood = colorMood,
      coverPrimary = coverPrimary,
      coverSecondary = coverSecondary,
      hasActiveSong = song != null
    )
  }
  val palette = rememberAnimatedStandbyPalette(
    target = targetPalette,
    reducedMotion = reducedMotion
  )

  val timeParts = remember(nowMillis, use24h) {
    formatStandbyClockParts(nowMillis, use24h)
  }
  val khmerDateText = remember(nowMillis) {
    formatKhmerStandbyDate(nowMillis)
  }

  val lyricSnap = remember(lyricsResult, livePositionMs, lyricOffsetMs) {
    AurixWidgetController.resolveSyncedLyricLines(
      syncedLines = lyricsResult?.synced,
      plainLyrics = lyricsResult?.plain,
      positionMs = livePositionMs,
      lyricOffsetMs = lyricOffsetMs,
      noLyricsLabel = ""
    )
  }
  val previousLyricLine = remember(lyricsResult, lyricSnap.activeIndex, lyricSnap.hasLyrics) {
    if (!lyricSnap.hasLyrics || lyricSnap.activeIndex <= 0) {
      ""
    } else {
      val cleanSynced = lyricsResult?.synced?.filter { it.text.isNotBlank() }.orEmpty()
      if (cleanSynced.isNotEmpty()) {
        cleanSynced.getOrNull(lyricSnap.activeIndex - 1)?.text?.trim().orEmpty()
      } else {
        val cleanPlain = lyricsResult?.plain
          ?.lineSequence()
          ?.map { it.trim() }
          ?.filter { it.isNotEmpty() }
          ?.toList()
          .orEmpty()
        cleanPlain.getOrNull(lyricSnap.activeIndex - 1).orEmpty()
      }
    }
  }

  val totalDurationMs = remember(durationMs, song?.durationSeconds, song?.fullDurationSeconds) {
    val fromPlayer = durationMs.takeIf { it > 0L }
    val fromSong = ((song?.fullDurationSeconds?.takeIf { it > 0L } ?: song?.durationSeconds ?: 0L) * 1000L)
      .takeIf { it > 0L }
    fromPlayer ?: fromSong ?: 0L
  }

  val resolvedAlbumName = remember(albumTitleOverride, song?.album, song?.source) {
    albumTitleOverride?.takeIf { it.isNotBlank() }
      ?: song?.album?.takeIf { it.isNotBlank() }
      ?: song?.source?.takeIf { it.isNotBlank() }.orEmpty()
  }

  val progressFraction = if (totalDurationMs > 0L && song != null) {
    (livePositionMs.toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 1f)
  } else {
    0f
  }

  fun handleSeekFraction(fraction: Float) {
    registerUserTouch()
    if (totalDurationMs > 0L) {
      val targetMs = (totalDurationMs * fraction).toLong().coerceIn(0L, totalDurationMs)
      livePositionMs = targetMs
      onSeek?.invoke(targetMs)
    }
  }

  BoxWithConstraints(
    modifier = Modifier
      .fillMaxSize()
      .background(palette.background)
      .pointerInput(Unit) {
        detectTapGestures(
          onTap = { offset ->
            val wasDimmed = (System.currentTimeMillis() - lastTouchTimestampMs) >= BURN_IN_DIM_TIMEOUT_MS
            registerUserTouch()
            if (!wasDimmed) {
              controlsVisible = !controlsVisible
            }
            if (size.width > 0 && size.height > 0) {
              pointerTiltX = ((offset.x / size.width.toFloat()) - 0.5f) * 0.7f
              pointerTiltY = ((offset.y / size.height.toFloat()) - 0.5f) * 0.7f
            }
          }
        )
      }
      .pointerInput(Unit) {
        var totalDragY = 0f
        detectVerticalDragGestures(
          onDragStart = {
            totalDragY = 0f
            registerUserTouch()
          },
          onVerticalDrag = { change, dragAmount ->
            totalDragY += dragAmount
            if (size.width > 0 && size.height > 0) {
              pointerTiltX = ((change.position.x / size.width.toFloat()) - 0.5f) * 0.9f
              pointerTiltY = ((change.position.y / size.height.toFloat()) - 0.5f) * 0.9f
            }
          },
          onDragEnd = {
            pointerTiltX = 0f
            pointerTiltY = 0f
            if (totalDragY > 72.dp.toPx()) {
              onClose()
            }
          },
          onDragCancel = {
            pointerTiltX = 0f
            pointerTiltY = 0f
          }
        )
      }
      .testTag("ambient_standby_screen")
  ) {
    val containerMaxWidth = maxWidth
    val containerMaxHeight = maxHeight
    val isLandscape = containerMaxWidth > containerMaxHeight

    // 1. Single soft 17% radial glow + dark vignette background
    StandbyDriftingBlobsBackground(
      palette = palette,
      reducedMotion = reducedMotion,
      modifier = Modifier.alpha(burnInBrightnessAlpha)
    )

    // Opposite depth shifts for Clock and Song Info driven by Gyro Parallax
    val clockParallaxX = (-smoothTiltX * 11f).dp
    val clockParallaxY = (-smoothTiltY * 11f).dp
    val infoParallaxX = (-smoothTiltX * 6.5f).dp
    val infoParallaxY = (-smoothTiltY * 6.5f).dp

    // 2. Main Bedside Layout (Split / Poster / Lyrics) with Burn-In Pixel Drift & Dimming
    Box(
      modifier = Modifier
        .fillMaxSize()
        .safeDrawingPadding()
        .offset(x = driftOffsetX, y = driftOffsetY)
        .alpha(burnInBrightnessAlpha)
    ) {
      when (layout) {
        StandbyLayout.SPLIT -> {
          val splitClockSp = if (isLandscape) {
            (containerMaxWidth.value * 0.108f).coerceIn(50f, 88f)
          } else {
            (containerMaxWidth.value * 0.195f).coerceIn(56f, 84f)
          }
          val splitArtSize = if (isLandscape) {
            (containerMaxWidth * 0.155f).coerceIn(88.dp, minOf(containerMaxHeight * 0.36f, 152.dp))
          } else {
            (containerMaxWidth * 0.38f).coerceIn(136.dp, 196.dp)
          }
          val splitTitleSp = (containerMaxWidth.value * (if (isLandscape) 0.032f else 0.058f)).coerceIn(20f, 30f)

          if (isLandscape) {
            Row(
              modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp, vertical = 20.dp),
              horizontalArrangement = Arrangement.spacedBy(32.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Column(
                modifier = Modifier
                  .weight(0.45f)
                  .fillMaxHeight(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
              ) {
                Box(modifier = Modifier.offset(x = clockParallaxX, y = clockParallaxY)) {
                  StandbyClockFace(
                    timeParts = timeParts,
                    style = clockStyle,
                    accent = palette.accent,
                    compact = true,
                    reducedMotion = reducedMotion,
                    textColor = palette.textPrimary,
                    targetSizeSp = splitClockSp
                  )
                }

                Spacer(modifier = Modifier.height(6.dp))

                StandbyMutedMetaLine(
                  khmerDateText = khmerDateText,
                  batteryPercent = batteryPercent,
                  isCharging = isCharging,
                  textColor = palette.textPrimary
                )

                Spacer(modifier = Modifier.height(14.dp))

                StandbyAlbumArtWithHalo(
                  artworkModel = artworkModel,
                  songTitle = song?.title,
                  palette = palette,
                  isPlaying = isPlaying,
                  reducedMotion = reducedMotion,
                  tiltX = smoothTiltX,
                  tiltY = smoothTiltY,
                  size = splitArtSize,
                  cornerPercent = 22,
                  showHalo = true,
                  onTogglePlayPause = {
                    registerUserTouch()
                    onPlayPause()
                  }
                )
              }

              Column(
                modifier = Modifier
                  .weight(0.55f)
                  .fillMaxHeight()
                  .offset(x = infoParallaxX, y = infoParallaxY),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.Start
              ) {
                StandbySplitSongInfoPanel(
                  song = song,
                  albumName = resolvedAlbumName,
                  isPlaying = isPlaying,
                  livePositionMs = livePositionMs,
                  totalDurationMs = totalDurationMs,
                  progressFraction = progressFraction,
                  lyricSnap = lyricSnap,
                  spectrumBars = spectrumBars,
                  palette = palette,
                  reducedMotion = reducedMotion,
                  centered = false,
                  titleFontSp = splitTitleSp,
                  khmerLyricsFont = khmerLyricsFont,
                  onSeekFraction = ::handleSeekFraction
                )
              }
            }
          } else {
            Column(
              modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 20.dp),
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.SpaceEvenly
            ) {
              Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.offset(x = clockParallaxX, y = clockParallaxY)
              ) {
                StandbyClockFace(
                  timeParts = timeParts,
                  style = clockStyle,
                  accent = palette.accent,
                  compact = false,
                  reducedMotion = reducedMotion,
                  textColor = palette.textPrimary,
                  targetSizeSp = splitClockSp
                )
                StandbyMutedMetaLine(
                  khmerDateText = khmerDateText,
                  batteryPercent = batteryPercent,
                  isCharging = isCharging,
                  textColor = palette.textPrimary
                )
              }

              StandbyAlbumArtWithHalo(
                artworkModel = artworkModel,
                songTitle = song?.title,
                palette = palette,
                isPlaying = isPlaying,
                reducedMotion = reducedMotion,
                tiltX = smoothTiltX,
                tiltY = smoothTiltY,
                size = splitArtSize,
                cornerPercent = 22,
                showHalo = true,
                onTogglePlayPause = {
                  registerUserTouch()
                  onPlayPause()
                }
              )

              Box(modifier = Modifier.offset(x = infoParallaxX, y = infoParallaxY)) {
                StandbySplitSongInfoPanel(
                  song = song,
                  albumName = resolvedAlbumName,
                  isPlaying = isPlaying,
                  livePositionMs = livePositionMs,
                  totalDurationMs = totalDurationMs,
                  progressFraction = progressFraction,
                  lyricSnap = lyricSnap,
                  spectrumBars = spectrumBars,
                  palette = palette,
                  reducedMotion = reducedMotion,
                  centered = true,
                  titleFontSp = splitTitleSp,
                  khmerLyricsFont = khmerLyricsFont,
                  onSeekFraction = ::handleSeekFraction
                )
              }
            }
          }
        }

        StandbyLayout.POSTER -> {
          val posterClockSp = if (isLandscape) {
            (containerMaxWidth.value * 0.082f).coerceIn(38f, 64f)
          } else {
            (containerMaxWidth.value * 0.145f).coerceIn(44f, 68f)
          }
          val posterArtSize = if (isLandscape) {
            minOf(containerMaxWidth * 0.38f, containerMaxHeight * 0.72f).coerceIn(148.dp, 290.dp)
          } else {
            minOf(containerMaxWidth * 0.62f, containerMaxHeight * 0.36f).coerceIn(168.dp, 268.dp)
          }
          val posterTitleSp = (containerMaxWidth.value * (if (isLandscape) 0.030f else 0.056f)).coerceIn(19f, 28f)

          if (isLandscape) {
            Row(
              modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp, vertical = 20.dp),
              horizontalArrangement = Arrangement.spacedBy(32.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Box(
                modifier = Modifier
                  .weight(0.46f)
                  .fillMaxHeight(),
                contentAlignment = Alignment.Center
              ) {
                StandbyAlbumArtWithHalo(
                  artworkModel = artworkModel,
                  songTitle = song?.title,
                  palette = palette,
                  isPlaying = isPlaying,
                  reducedMotion = reducedMotion,
                  tiltX = smoothTiltX,
                  tiltY = smoothTiltY,
                  size = posterArtSize,
                  cornerPercent = 12,
                  showHalo = true,
                  onTogglePlayPause = {
                    registerUserTouch()
                    onPlayPause()
                  }
                )
              }

              Column(
                modifier = Modifier
                  .weight(0.54f)
                  .fillMaxHeight()
                  .offset(x = infoParallaxX, y = infoParallaxY),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.Start
              ) {
                Box(modifier = Modifier.offset(x = clockParallaxX * 0.5f, y = clockParallaxY * 0.5f)) {
                  StandbyClockFace(
                    timeParts = timeParts,
                    style = clockStyle,
                    accent = palette.accent,
                    compact = true,
                    reducedMotion = reducedMotion,
                    textColor = palette.textPrimary,
                    targetSizeSp = posterClockSp
                  )
                }

                Spacer(modifier = Modifier.height(6.dp))

                StandbyMutedMetaLine(
                  khmerDateText = khmerDateText,
                  batteryPercent = batteryPercent,
                  isCharging = isCharging,
                  textColor = palette.textPrimary
                )

                Spacer(modifier = Modifier.height(14.dp))

                StandbyTitleArtistBlock(
                  song = song,
                  albumName = resolvedAlbumName,
                  palette = palette,
                  titleFontSp = posterTitleSp,
                  centered = false
                )

                Spacer(modifier = Modifier.height(14.dp))

                StandbyThinProgressBarBlock(
                  song = song,
                  livePositionMs = livePositionMs,
                  totalDurationMs = totalDurationMs,
                  progressFraction = progressFraction,
                  palette = palette,
                  centered = false,
                  onSeekFraction = ::handleSeekFraction
                )

                Spacer(modifier = Modifier.height(16.dp))

                StandbyPosterTransportControls(
                  isPlaying = isPlaying,
                  accent = palette.accent,
                  textColor = palette.textPrimary,
                  onPrevious = {
                    registerUserTouch()
                    onPrevious()
                  },
                  onPlayPause = {
                    registerUserTouch()
                    onPlayPause()
                  },
                  onNext = {
                    registerUserTouch()
                    onNext()
                  }
                )
              }
            }
          } else {
            Column(
              modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 20.dp),
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.SpaceEvenly
            ) {
              StandbyAlbumArtWithHalo(
                artworkModel = artworkModel,
                songTitle = song?.title,
                palette = palette,
                isPlaying = isPlaying,
                reducedMotion = reducedMotion,
                tiltX = smoothTiltX,
                tiltY = smoothTiltY,
                size = posterArtSize,
                cornerPercent = 12,
                showHalo = true,
                onTogglePlayPause = {
                  registerUserTouch()
                  onPlayPause()
                }
              )

              Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.offset(x = clockParallaxX * 0.5f, y = clockParallaxY * 0.5f)
              ) {
                StandbyClockFace(
                  timeParts = timeParts,
                  style = clockStyle,
                  accent = palette.accent,
                  compact = true,
                  reducedMotion = reducedMotion,
                  textColor = palette.textPrimary,
                  targetSizeSp = posterClockSp
                )
                StandbyMutedMetaLine(
                  khmerDateText = khmerDateText,
                  batteryPercent = batteryPercent,
                  isCharging = isCharging,
                  textColor = palette.textPrimary
                )
              }

              StandbyTitleArtistBlock(
                song = song,
                albumName = resolvedAlbumName,
                palette = palette,
                titleFontSp = posterTitleSp,
                centered = true
              )

              StandbyThinProgressBarBlock(
                song = song,
                livePositionMs = livePositionMs,
                totalDurationMs = totalDurationMs,
                progressFraction = progressFraction,
                palette = palette,
                centered = true,
                onSeekFraction = ::handleSeekFraction
              )

              StandbyPosterTransportControls(
                isPlaying = isPlaying,
                accent = palette.accent,
                textColor = palette.textPrimary,
                onPrevious = {
                  registerUserTouch()
                  onPrevious()
                },
                onPlayPause = {
                  registerUserTouch()
                  onPlayPause()
                },
                onNext = {
                  registerUserTouch()
                  onNext()
                }
              )
            }
          }
        }

        StandbyLayout.LYRICS -> {
          val lyricsClockSp = if (isLandscape) {
            (containerMaxWidth.value * 0.048f).coerceIn(24f, 40f)
          } else {
            (containerMaxWidth.value * 0.095f).coerceIn(28f, 44f)
          }
          val lyricsThumbSize = (containerMaxWidth * (if (isLandscape) 0.070f else 0.125f))
            .coerceIn(44.dp, 62.dp)
          val activeLyricSp = (containerMaxWidth.value * (if (isLandscape) 0.040f else 0.068f))
            .coerceIn(22f, 34f)

          Column(
            modifier = Modifier
              .fillMaxSize()
              .padding(horizontal = if (isLandscape) 32.dp else 22.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.SpaceBetween
          ) {
            // Top row: small clock left, muted date & battery right
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(end = if (controlsVisible) 216.dp else 0.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Box(modifier = Modifier.offset(x = clockParallaxX * 0.4f, y = clockParallaxY * 0.4f)) {
                StandbyClockFace(
                  timeParts = timeParts,
                  style = clockStyle,
                  accent = palette.accent,
                  compact = true,
                  reducedMotion = reducedMotion,
                  textColor = palette.textPrimary,
                  targetSizeSp = lyricsClockSp
                )
              }

              if (!controlsVisible) {
                StandbyMutedMetaLine(
                  khmerDateText = khmerDateText,
                  batteryPercent = batteryPercent,
                  isCharging = isCharging,
                  textColor = palette.textPrimary
                )
              }
            }

            // Center hero: prev line (dim), active line (accent, semibold, active-only cross-fade), next line (dim)
            // Fallback when no lyrics: large centered title + artist
            Box(
              modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .offset(x = infoParallaxX, y = infoParallaxY),
              contentAlignment = Alignment.Center
            ) {
              if (song != null && lyricSnap.hasLyrics && lyricSnap.currentLine.isNotBlank()) {
                Column(
                  modifier = Modifier.fillMaxWidth(if (isLandscape) 0.86f else 0.96f),
                  horizontalAlignment = Alignment.CenterHorizontally,
                  verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                  if (previousLyricLine.isNotBlank()) {
                    Text(
                      text = rememberKhmerLyricsText(
                        text = previousLyricLine,
                        khmerLyricsFont = khmerLyricsFont,
                        baseFontSize = (activeLyricSp * 0.62f).sp,
                        isBold = false
                      ),
                      style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Medium,
                        lineHeight = (activeLyricSp * 0.86f).sp
                      ),
                      color = palette.textPrimary.copy(alpha = 0.30f),
                      textAlign = TextAlign.Center,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis
                    )
                  }

                  AnimatedContent(
                    targetState = lyricSnap.currentLine,
                    transitionSpec = {
                      if (reducedMotion) {
                        fadeIn(snap()) togetherWith fadeOut(snap())
                      } else {
                        (slideInVertically(tween(300, easing = FastOutSlowInEasing)) { it / 4 } + fadeIn(tween(220))) togetherWith
                          (slideOutVertically(tween(220)) { -it / 4 } + fadeOut(tween(180)))
                      }
                    },
                    label = "standby_lyrics_hero_active"
                  ) { currentLine ->
                    Text(
                      text = rememberKhmerLyricsText(
                        text = currentLine,
                        khmerLyricsFont = khmerLyricsFont,
                        baseFontSize = activeLyricSp.sp,
                        isBold = true
                      ),
                      style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        lineHeight = (activeLyricSp * 1.32f).sp
                      ),
                      color = palette.accent,
                      textAlign = TextAlign.Center,
                      maxLines = 2,
                      overflow = TextOverflow.Ellipsis,
                      modifier = Modifier.testTag("standby_lyrics_hero_line")
                    )
                  }

                  if (lyricSnap.nextLine.isNotBlank()) {
                    Text(
                      text = rememberKhmerLyricsText(
                        text = lyricSnap.nextLine,
                        khmerLyricsFont = khmerLyricsFont,
                        baseFontSize = (activeLyricSp * 0.64f).sp,
                        isBold = false
                      ),
                      style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Medium,
                        lineHeight = (activeLyricSp * 0.88f).sp
                      ),
                      color = palette.textPrimary.copy(alpha = 0.30f),
                      textAlign = TextAlign.Center,
                      maxLines = 2,
                      overflow = TextOverflow.Ellipsis
                    )
                  }
                }
              } else {
                Column(
                  horizontalAlignment = Alignment.CenterHorizontally,
                  verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                  StandbyTitleArtistBlock(
                    song = song,
                    albumName = resolvedAlbumName,
                    palette = palette,
                    titleFontSp = activeLyricSp.coerceIn(22f, 32f),
                    centered = true
                  )
                }
              }
            }

            // Bottom bar: small 7% art (22% corners), title & artist, full-width thin progress line
            Column(
              modifier = Modifier.fillMaxWidth(),
              verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
              ) {
                StandbyAlbumArtWithHalo(
                  artworkModel = artworkModel,
                  songTitle = song?.title,
                  palette = palette,
                  isPlaying = isPlaying,
                  reducedMotion = reducedMotion,
                  tiltX = smoothTiltX * 0.5f,
                  tiltY = smoothTiltY * 0.5f,
                  size = lyricsThumbSize,
                  cornerPercent = 22,
                  showHalo = false,
                  onTogglePlayPause = {
                    registerUserTouch()
                    onPlayPause()
                  }
                )

                Column(
                  modifier = Modifier.weight(1f),
                  verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                  val displayTitle = song?.title?.takeIf { it.isNotBlank() }
                    ?: stringResource(id = R.string.standby_no_track)
                  val displayArtist = song?.artist?.takeIf { it.isNotBlank() }
                    ?: stringResource(id = R.string.standby_no_artist)
                  Text(
                    text = rememberKhmerTitleText(
                      text = displayTitle,
                      baseFontSize = 15.sp,
                      isBold = true
                    ),
                    style = MaterialTheme.typography.titleSmall.copy(
                      fontSize = 15.sp,
                      fontWeight = FontWeight.SemiBold
                    ),
                    color = palette.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                  )
                  Text(
                    text = rememberKhmerTitleText(
                      text = displayArtist,
                      baseFontSize = 13.sp,
                      isBold = false
                    ),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                    color = palette.textPrimary.copy(alpha = 0.56f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                  )
                }

                StandbyFourBarEqualizer(
                  bars = spectrumBars,
                  isPlaying = isPlaying && song != null,
                  accent = palette.accent,
                  reducedMotion = reducedMotion
                )
              }

              StandbyThinProgressBarBlock(
                song = song,
                livePositionMs = livePositionMs,
                totalDurationMs = totalDurationMs,
                progressFraction = progressFraction,
                palette = palette,
                centered = false,
                fullWidth = true,
                onSeekFraction = ::handleSeekFraction
              )
            }
          }
        }
      }
    }

    // 3. Top-Right 44dp Round Glass Buttons: Layout, Clock Style, Mood, Gyro, Close
    AnimatedVisibility(
      visible = controlsVisible,
      enter = if (reducedMotion) fadeIn(snap()) else fadeIn(tween(220)),
      exit = if (reducedMotion) fadeOut(snap()) else fadeOut(tween(300)),
      modifier = Modifier
        .align(Alignment.TopEnd)
        .safeDrawingPadding()
        .padding(16.dp)
    ) {
      Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        // Layout switcher button (Split -> Poster -> Lyrics)
        StandbyTopGlassCircleButton(
          onClick = {
            registerUserTouch()
            onSelectLayout(layout.next())
          },
          active = layout != StandbyLayout.SPLIT,
          accent = palette.accent,
          testTag = "standby_layout_button",
          contentDescription = stringResource(id = R.string.standby_layout_cd)
        ) {
          Icon(
            imageVector = Icons.Rounded.Dashboard,
            contentDescription = null,
            tint = if (layout != StandbyLayout.SPLIT) palette.accent else palette.textPrimary,
            modifier = Modifier.size(19.dp)
          )
        }

        // Clock Style picker button
        StandbyTopGlassCircleButton(
          onClick = {
            registerUserTouch()
            showClockPickerSheet = true
          },
          active = showClockPickerSheet,
          accent = palette.accent,
          testTag = "standby_clock_style_button",
          contentDescription = stringResource(id = R.string.standby_clock_cd)
        ) {
          Icon(
            imageVector = Icons.Rounded.AccessTime,
            contentDescription = null,
            tint = palette.textPrimary,
            modifier = Modifier.size(19.dp)
          )
        }

        // Mood glass circle button
        StandbyTopGlassCircleButton(
          onClick = {
            registerUserTouch()
            onSelectColorMood(colorMood.next())
          },
          active = colorMood != StandbyColorMood.MONO,
          accent = palette.accent,
          testTag = "standby_mood_button",
          contentDescription = stringResource(id = R.string.standby_mood_cd)
        ) {
          Icon(
            imageVector = Icons.Rounded.Palette,
            contentDescription = null,
            tint = palette.accent,
            modifier = Modifier.size(19.dp)
          )
        }

        // Gyro Parallax on/off glass circle button
        StandbyTopGlassCircleButton(
          onClick = {
            registerUserTouch()
            onToggleGyroEnabled(!gyroEnabled)
          },
          active = gyroEnabled,
          accent = palette.accent,
          testTag = "standby_gyro_button",
          contentDescription = stringResource(id = R.string.standby_gyro_cd)
        ) {
          Icon(
            imageVector = Icons.Rounded.Explore,
            contentDescription = null,
            tint = if (gyroEnabled) palette.accent else palette.textPrimary.copy(alpha = 0.55f),
            modifier = Modifier.size(19.dp)
          )
        }

        // Close button
        StandbyTopGlassCircleButton(
          onClick = onClose,
          active = false,
          accent = palette.accent,
          testTag = "standby_exit_button",
          contentDescription = stringResource(id = R.string.standby_exit_cd)
        ) {
          Icon(
            imageVector = Icons.Rounded.Close,
            contentDescription = null,
            tint = palette.textPrimary,
            modifier = Modifier.size(19.dp)
          )
        }
      }
    }

    // 4. Playback Overlay Controls (Shown on tap in Split & Lyrics; Poster already shows always-visible controls)
    AnimatedVisibility(
      visible = controlsVisible && layout != StandbyLayout.POSTER,
      enter = if (reducedMotion) fadeIn(snap()) else fadeIn(tween(220)) + slideInVertically { it / 2 },
      exit = if (reducedMotion) fadeOut(snap()) else fadeOut(tween(300)) + slideOutVertically { it / 2 },
      modifier = Modifier
        .align(Alignment.BottomCenter)
        .safeDrawingPadding()
        .padding(bottom = 16.dp)
    ) {
      Surface(
        shape = CircleShape,
        color = Color(0xCC0E1211),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f))
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
          horizontalArrangement = Arrangement.spacedBy(12.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          IconButton(
            onClick = {
              registerUserTouch()
              onPrevious()
            },
            modifier = Modifier
              .minimumInteractiveComponentSize()
              .testTag("standby_prev_button")
          ) {
            Icon(
              imageVector = Icons.Rounded.SkipPrevious,
              contentDescription = stringResource(id = R.string.widget_cd_prev),
              tint = palette.textPrimary,
              modifier = Modifier.size(26.dp)
            )
          }

          IconButton(
            onClick = {
              registerUserTouch()
              onPlayPause()
            },
            modifier = Modifier
              .minimumInteractiveComponentSize()
              .size(48.dp)
              .clip(CircleShape)
              .background(palette.accent)
              .testTag("standby_play_pause_button")
          ) {
            Icon(
              imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
              contentDescription = stringResource(id = R.string.widget_cd_play_pause),
              tint = Color(0xFF04120C),
              modifier = Modifier.size(26.dp)
            )
          }

          IconButton(
            onClick = {
              registerUserTouch()
              onNext()
            },
            modifier = Modifier
              .minimumInteractiveComponentSize()
              .testTag("standby_next_button")
          ) {
            Icon(
              imageVector = Icons.Rounded.SkipNext,
              contentDescription = stringResource(id = R.string.widget_cd_next),
              tint = palette.textPrimary,
              modifier = Modifier.size(26.dp)
            )
          }
        }
      }
    }

    // 5. Clock Style Picker Bottom Sheet (auto-hides after 8s inactivity)
    if (showClockPickerSheet) {
      StandbyClockStylePickerSheet(
        selectedStyle = clockStyle,
        use24h = use24h,
        accent = palette.accent,
        onSelectStyle = { newStyle ->
          registerUserTouch()
          onSelectClockStyle(newStyle)
        },
        onToggleUse24h = { newUse24h ->
          registerUserTouch()
          onToggleUse24h(newUse24h)
        },
        onDismiss = {
          registerUserTouch()
          showClockPickerSheet = false
        }
      )
    }
  }
}

@Composable
private fun StandbyTopGlassCircleButton(
  onClick: () -> Unit,
  active: Boolean,
  accent: Color,
  testTag: String,
  contentDescription: String,
  content: @Composable () -> Unit
) {
  Surface(
    onClick = onClick,
    shape = CircleShape,
    color = if (active) accent.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.09f),
    border = BorderStroke(
      width = 1.dp,
      color = if (active) accent.copy(alpha = 0.44f) else Color.White.copy(alpha = 0.14f)
    ),
    modifier = Modifier
      .minimumInteractiveComponentSize()
      .size(44.dp)
      .testTag(testTag)
  ) {
    Box(
      contentAlignment = Alignment.Center,
      modifier = Modifier.fillMaxSize()
    ) {
      content()
    }
  }
}

/**
 * Single muted metadata line showing Khmer date + battery % (no boxed chips).
 */
@Composable
private fun StandbyMutedMetaLine(
  khmerDateText: String,
  batteryPercent: Int,
  isCharging: Boolean,
  textColor: Color
) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(6.dp),
    modifier = Modifier.testTag("standby_muted_meta_line")
  ) {
    Text(
      text = rememberKhmerTitleText(
        text = "$khmerDateText · $batteryPercent%",
        baseFontSize = 12.sp,
        isBold = false
      ),
      style = MaterialTheme.typography.labelMedium.copy(
        fontSize = 12.sp,
        fontFeatureSettings = "tnum"
      ),
      color = textColor.copy(alpha = 0.56f),
      maxLines = 1,
      overflow = TextOverflow.Ellipsis
    )
    if (isCharging) {
      Icon(
        imageVector = Icons.Rounded.BatteryChargingFull,
        contentDescription = null,
        tint = Color(0xFF7EF0C5).copy(alpha = 0.82f),
        modifier = Modifier.size(13.dp)
      )
    }
  }
}

/**
 * Album Art with rounded corners, blurred rotating halo behind it, 3D Gyro Parallax tilt (up to 8°),
 * moving specular glare, and tap-to-toggle play/pause.
 */
@Composable
private fun StandbyAlbumArtWithHalo(
  artworkModel: Any?,
  songTitle: String?,
  palette: StandbyMoodPalette,
  isPlaying: Boolean,
  reducedMotion: Boolean,
  tiltX: Float,
  tiltY: Float,
  size: Dp,
  cornerPercent: Int = 12,
  showHalo: Boolean = true,
  onTogglePlayPause: () -> Unit
) {
  val infinite = rememberInfiniteTransition(label = "standby_halo_spin")
  val haloAngle by infinite.animateFloat(
    initialValue = 0f,
    targetValue = 360f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = 16_000, easing = LinearEasing),
      repeatMode = RepeatMode.Restart
    ),
    label = "standby_halo_angle"
  )
  val haloPulse by infinite.animateFloat(
    initialValue = 0.96f,
    targetValue = 1.05f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = 3_400, easing = FastOutSlowInEasing),
      repeatMode = RepeatMode.Reverse
    ),
    label = "standby_halo_pulse"
  )

  val cornerShape = RoundedCornerShape(percent = cornerPercent)
  val pitchDeg = (-tiltY * MAX_TILT_DEGREES).coerceIn(-MAX_TILT_DEGREES, MAX_TILT_DEGREES)
  val rollDeg = (tiltX * MAX_TILT_DEGREES).coerceIn(-MAX_TILT_DEGREES, MAX_TILT_DEGREES)
  val artInteraction = remember { MutableInteractionSource() }
  val outerBoxSize = if (showHalo) size + 24.dp else size

  Box(
    contentAlignment = Alignment.Center,
    modifier = Modifier.size(outerBoxSize)
  ) {
    if (showHalo) {
      Canvas(
        modifier = Modifier
          .size(size + 18.dp)
          .scale(if (reducedMotion || !isPlaying) 1f else haloPulse)
          .graphicsLayer {
            rotationZ = if (reducedMotion) 0f else haloAngle
          }
          .blur(18.dp)
      ) {
        val r = this.size.minDimension * 0.5f
        val c = Offset(this.size.width * 0.5f, this.size.height * 0.5f)
        drawCircle(
          brush = Brush.sweepGradient(
            colors = listOf(
              palette.blob1.copy(alpha = 0.50f),
              palette.blob2.copy(alpha = 0.42f),
              palette.accent.copy(alpha = 0.56f),
              palette.blob4.copy(alpha = 0.40f),
              palette.blob1.copy(alpha = 0.50f)
            ),
            center = c
          ),
          radius = r,
          center = c
        )
      }
    }

    Box(
      modifier = Modifier
        .size(size)
        .graphicsLayer {
          rotationX = pitchDeg
          rotationY = rollDeg
          cameraDistance = 14f * density
        }
        .clip(cornerShape)
        .background(palette.background)
        .border(1.dp, Color.White.copy(alpha = 0.14f), cornerShape)
        .clickable(
          interactionSource = artInteraction,
          indication = null,
          onClick = onTogglePlayPause
        )
        .testTag("standby_album_art"),
      contentAlignment = Alignment.Center
    ) {
      if (artworkModel != null) {
        val standbyArtRequest = com.example.ui.image.rememberCrispArtworkRequest(model = artworkModel)
        AsyncImage(
          model = standbyArtRequest,
          contentDescription = songTitle,
          contentScale = ContentScale.Crop,
          filterQuality = com.example.ui.image.AurixImagePipeline.ARTWORK_FILTER_QUALITY,
          modifier = Modifier.fillMaxSize()
        )
      } else {
        StandbyProceduralArtworkFallback(
          palette = palette,
          modifier = Modifier.fillMaxSize()
        )
      }

      // Moving specular glare overlay driven by 3D tilt angle
      Canvas(modifier = Modifier.fillMaxSize()) {
        val glareCenterX = this.size.width * (0.5f - tiltX * 0.42f)
        val glareCenterY = this.size.height * (0.35f - tiltY * 0.42f)
        val glareRadius = this.size.maxDimension * 0.75f
        val tiltMagnitude = (abs(tiltX) + abs(tiltY)).coerceIn(0f, 1.4f)
        val glareAlpha = (0.08f + tiltMagnitude * 0.12f).coerceIn(0.06f, 0.24f)

        drawCircle(
          brush = Brush.radialGradient(
            colors = listOf(
              Color.White.copy(alpha = glareAlpha),
              Color.White.copy(alpha = glareAlpha * 0.22f),
              Color.Transparent
            ),
            center = Offset(glareCenterX, glareCenterY),
            radius = glareRadius
          ),
          radius = glareRadius,
          center = Offset(glareCenterX, glareCenterY)
        )
      }
    }
  }
}

@Composable
private fun StandbyTitleArtistBlock(
  song: Song?,
  albumName: String,
  palette: StandbyMoodPalette,
  titleFontSp: Float,
  centered: Boolean
) {
  val horizontalAlign = if (centered) Alignment.CenterHorizontally else Alignment.Start
  val textAlign = if (centered) TextAlign.Center else TextAlign.Start

  Column(
    horizontalAlignment = horizontalAlign,
    verticalArrangement = Arrangement.spacedBy(3.dp)
  ) {
    val displayTitle = song?.title?.takeIf { it.isNotBlank() }
      ?: stringResource(id = R.string.standby_no_track)
    Text(
      text = rememberKhmerTitleText(
        text = displayTitle,
        baseFontSize = titleFontSp.sp,
        isBold = true
      ),
      style = MaterialTheme.typography.titleLarge.copy(
        fontSize = titleFontSp.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = (titleFontSp * 1.24f).sp
      ),
      color = palette.textPrimary,
      textAlign = textAlign,
      maxLines = 2,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier.testTag("standby_song_title")
    )

    val artistText = song?.artist?.takeIf { it.isNotBlank() }
      ?: stringResource(id = R.string.standby_no_artist)
    val artistAlbumLine = if (song != null && albumName.isNotBlank() && !albumName.equals(artistText, ignoreCase = true)) {
      "$artistText · $albumName"
    } else {
      artistText
    }

    Text(
      text = rememberKhmerTitleText(
        text = artistAlbumLine,
        baseFontSize = 14.sp,
        isBold = false
      ),
      style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
      color = palette.textPrimary.copy(alpha = 0.56f),
      textAlign = textAlign,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier.testTag("standby_song_artist_album")
    )
  }
}

/**
 * Song info panel for the default SPLIT layout:
 * - Small 4-bar equalizer + "NOW PLAYING" + AURIX planet logo
 * - Real song title (semibold) & artist (muted)
 * - Current lyric (semibold) + next lyric line (dim 30%) with active-only transition
 * - Thin seekable progress line + tabular elapsed/total timestamps
 */
@Composable
private fun StandbySplitSongInfoPanel(
  song: Song?,
  albumName: String,
  isPlaying: Boolean,
  livePositionMs: Long,
  totalDurationMs: Long,
  progressFraction: Float,
  lyricSnap: SyncedLyricLineSnapshot,
  spectrumBars: FloatArray,
  palette: StandbyMoodPalette,
  reducedMotion: Boolean,
  centered: Boolean,
  titleFontSp: Float,
  khmerLyricsFont: KhmerLyricsFont,
  onSeekFraction: (Float) -> Unit
) {
  val horizontalAlign = if (centered) Alignment.CenterHorizontally else Alignment.Start
  val textAlign = if (centered) TextAlign.Center else TextAlign.Start

  Column(
    modifier = Modifier.fillMaxWidth(),
    horizontalAlignment = horizontalAlign,
    verticalArrangement = Arrangement.spacedBy(10.dp)
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      modifier = Modifier.testTag("standby_now_playing_header")
    ) {
      StandbyFourBarEqualizer(
        bars = spectrumBars,
        isPlaying = isPlaying && song != null,
        accent = palette.accent,
        reducedMotion = reducedMotion
      )
      Text(
        text = stringResource(id = R.string.standby_now_playing_label),
        style = MaterialTheme.typography.labelSmall.copy(
          fontFamily = StandbyClockFonts.OutfitFamily,
          fontWeight = FontWeight.ExtraBold,
          fontSize = 11.sp,
          letterSpacing = 1.6.sp
        ),
        color = palette.accent
      )
      StandbyMiniPlanetLogo(
        accent = palette.accent,
        modifier = Modifier.size(16.dp)
      )
    }

    StandbyTitleArtistBlock(
      song = song,
      albumName = albumName,
      palette = palette,
      titleFontSp = titleFontSp,
      centered = centered
    )

    if (song != null && lyricSnap.hasLyrics && lyricSnap.currentLine.isNotBlank()) {
      Column(
        horizontalAlignment = horizontalAlign,
        verticalArrangement = Arrangement.spacedBy(4.dp)
      ) {
        AnimatedContent(
          targetState = lyricSnap.currentLine,
          transitionSpec = {
            if (reducedMotion) {
              fadeIn(snap()) togetherWith fadeOut(snap())
            } else {
              (slideInVertically(tween(300, easing = FastOutSlowInEasing)) { it / 3 } + fadeIn(tween(220))) togetherWith
                (slideOutVertically(tween(220)) { -it / 3 } + fadeOut(tween(180)))
            }
          },
          label = "standby_lyric_transition"
        ) { currentLine ->
          Text(
            text = rememberKhmerLyricsText(
              text = currentLine,
              khmerLyricsFont = khmerLyricsFont,
              baseFontSize = 15.5.sp,
              isBold = true
            ),
            style = MaterialTheme.typography.bodyMedium.copy(
              fontWeight = FontWeight.SemiBold,
              lineHeight = 22.sp
            ),
            color = palette.accent,
            textAlign = textAlign,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
          )
        }

        if (lyricSnap.nextLine.isNotBlank()) {
          Text(
            text = rememberKhmerLyricsText(
              text = lyricSnap.nextLine,
              khmerLyricsFont = khmerLyricsFont,
              baseFontSize = 13.5.sp,
              isBold = false
            ),
            style = MaterialTheme.typography.bodySmall.copy(
              fontWeight = FontWeight.Normal,
              lineHeight = 19.sp
            ),
            color = palette.textPrimary.copy(alpha = 0.30f),
            textAlign = textAlign,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }
      }
    }

    Spacer(modifier = Modifier.height(2.dp))

    StandbyThinProgressBarBlock(
      song = song,
      livePositionMs = livePositionMs,
      totalDurationMs = totalDurationMs,
      progressFraction = progressFraction,
      palette = palette,
      centered = centered,
      onSeekFraction = onSeekFraction
    )
  }
}

@Composable
private fun StandbyThinProgressBarBlock(
  song: Song?,
  livePositionMs: Long,
  totalDurationMs: Long,
  progressFraction: Float,
  palette: StandbyMoodPalette,
  centered: Boolean,
  fullWidth: Boolean = false,
  onSeekFraction: (Float) -> Unit
) {
  Column(
    modifier = Modifier.fillMaxWidth(
      when {
        fullWidth -> 1.0f
        centered -> 0.88f
        else -> 0.96f
      }
    ),
    verticalArrangement = Arrangement.spacedBy(5.dp)
  ) {
    StandbyThinProgressLine(
      progress = progressFraction,
      accent = palette.accent,
      onSelectFraction = onSeekFraction,
      modifier = Modifier
        .fillMaxWidth()
        .height(14.dp)
        .testTag("standby_progress_bar")
    )

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text(
        text = formatStandbyDuration(if (song != null) livePositionMs else 0L),
        style = MaterialTheme.typography.labelSmall.copy(
          fontFamily = StandbyClockFonts.OutfitFamily,
          fontWeight = FontWeight.SemiBold,
          fontSize = 11.5.sp,
          fontFeatureSettings = "tnum"
        ),
        color = palette.textPrimary.copy(alpha = 0.56f),
        modifier = Modifier.testTag("standby_elapsed_time")
      )
      Text(
        text = formatStandbyDuration(if (song != null) totalDurationMs else 0L),
        style = MaterialTheme.typography.labelSmall.copy(
          fontFamily = StandbyClockFonts.OutfitFamily,
          fontWeight = FontWeight.SemiBold,
          fontSize = 11.5.sp,
          fontFeatureSettings = "tnum"
        ),
        color = palette.textPrimary.copy(alpha = 0.30f),
        modifier = Modifier.testTag("standby_total_time")
      )
    }
  }
}

/**
 * Always-visible inline transport controls used in POSTER layout.
 */
@Composable
private fun StandbyPosterTransportControls(
  isPlaying: Boolean,
  accent: Color,
  textColor: Color,
  onPrevious: () -> Unit,
  onPlayPause: () -> Unit,
  onNext: () -> Unit
) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(16.dp),
    modifier = Modifier.testTag("standby_poster_controls")
  ) {
    IconButton(
      onClick = onPrevious,
      modifier = Modifier
        .minimumInteractiveComponentSize()
        .testTag("standby_poster_prev_button")
    ) {
      Icon(
        imageVector = Icons.Rounded.SkipPrevious,
        contentDescription = stringResource(id = R.string.widget_cd_prev),
        tint = textColor.copy(alpha = 0.78f),
        modifier = Modifier.size(28.dp)
      )
    }

    IconButton(
      onClick = onPlayPause,
      modifier = Modifier
        .minimumInteractiveComponentSize()
        .size(52.dp)
        .clip(CircleShape)
        .background(accent)
        .testTag("standby_poster_play_pause_button")
    ) {
      Icon(
        imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
        contentDescription = stringResource(id = R.string.widget_cd_play_pause),
        tint = Color(0xFF04120C),
        modifier = Modifier.size(28.dp)
      )
    }

    IconButton(
      onClick = onNext,
      modifier = Modifier
        .minimumInteractiveComponentSize()
        .testTag("standby_poster_next_button")
    ) {
      Icon(
        imageVector = Icons.Rounded.SkipNext,
        contentDescription = stringResource(id = R.string.widget_cd_next),
        tint = textColor.copy(alpha = 0.78f),
        modifier = Modifier.size(28.dp)
      )
    }
  }
}

/**
 * Thin progress bar line with generous touch target for seeking.
 */
@Composable
private fun StandbyThinProgressLine(
  progress: Float,
  accent: Color,
  onSelectFraction: (Float) -> Unit,
  modifier: Modifier = Modifier
) {
  val clamped = progress.coerceIn(0f, 1f)
  Canvas(
    modifier = modifier.pointerInput(Unit) {
      detectTapGestures { offset ->
        if (size.width > 0) {
          onSelectFraction((offset.x / size.width.toFloat()).coerceIn(0f, 1f))
        }
      }
    }
  ) {
    val trackHeight = 3.dp.toPx()
    val topY = (size.height - trackHeight) * 0.5f
    val corner = CornerRadius(trackHeight * 0.5f, trackHeight * 0.5f)
    drawRoundRect(
      color = Color.White.copy(alpha = 0.14f),
      topLeft = Offset(0f, topY),
      size = Size(size.width, trackHeight),
      cornerRadius = corner
    )
    val fillWidth = (size.width * clamped).coerceAtLeast(if (clamped > 0f) trackHeight else 0f)
    if (fillWidth > 0f) {
      drawRoundRect(
        color = accent,
        topLeft = Offset(0f, topY),
        size = Size(fillWidth, trackHeight),
        cornerRadius = corner
      )
    }
  }
}

/**
 * Small 4-bar equalizer synced to AudioSpectrumBus (static when paused or reduced motion).
 */
@Composable
private fun StandbyFourBarEqualizer(
  bars: FloatArray,
  isPlaying: Boolean,
  accent: Color,
  reducedMotion: Boolean
) {
  val infinite = rememberInfiniteTransition(label = "standby_4bar_eq")
  val phase by infinite.animateFloat(
    initialValue = 0f,
    targetValue = (Math.PI * 2.0).toFloat(),
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = 780, easing = LinearEasing),
      repeatMode = RepeatMode.Restart
    ),
    label = "standby_4bar_phase"
  )

  Canvas(modifier = Modifier.size(width = 14.dp, height = 12.dp)) {
    val barCount = 4
    val gap = 1.8.dp.toPx()
    val barWidth = ((size.width - gap * (barCount - 1)) / barCount).coerceAtLeast(1.6.dp.toPx())
    val hasSignal = bars.any { it > 0.04f }
    val staticHeights = floatArrayOf(0.45f, 0.80f, 0.60f, 0.38f)

    for (i in 0 until barCount) {
      val sampleIdx = (i * (bars.size / barCount.coerceAtLeast(1))).coerceIn(0, (bars.size - 1).coerceAtLeast(0))
      val level = when {
        !isPlaying || reducedMotion -> staticHeights[i]
        hasSignal -> bars.getOrElse(sampleIdx) { 0.35f }.coerceIn(0.24f, 1f)
        else -> (0.30f + 0.65f * abs(sin(phase + i * 1.15f))).coerceIn(0.24f, 0.98f)
      }
      val barH = (size.height * level).coerceAtLeast(2.dp.toPx())
      val x = i * (barWidth + gap)
      val y = size.height - barH
      drawRoundRect(
        color = accent,
        topLeft = Offset(x, y),
        size = Size(barWidth, barH),
        cornerRadius = CornerRadius(barWidth * 0.5f, barWidth * 0.5f)
      )
    }
  }
}

/**
 * Miniature AURIX brand planet with the -16° tilted orbit ring.
 */
@Composable
private fun StandbyMiniPlanetLogo(
  accent: Color,
  modifier: Modifier = Modifier
) {
  Canvas(modifier = modifier) {
    val c = Offset(size.width * 0.5f, size.height * 0.5f)
    val r = size.minDimension * 0.25f
    drawCircle(
      color = accent.copy(alpha = 0.88f),
      radius = r,
      center = c
    )
    rotate(degrees = -16f, pivot = c) {
      drawArc(
        color = Color.White.copy(alpha = 0.90f),
        startAngle = -15f,
        sweepAngle = 210f,
        useCenter = false,
        topLeft = Offset(c.x - r * 1.75f, c.y - r * 0.55f),
        size = Size(r * 3.5f, r * 1.1f),
        style = Stroke(width = 1.3.dp.toPx(), cap = StrokeCap.Round)
      )
    }
  }
}

fun formatStandbyDuration(durationMs: Long): String {
  val totalSeconds = (durationMs.coerceAtLeast(0L) / 1000L)
  val minutes = totalSeconds / 60L
  val seconds = totalSeconds % 60L
  return String.format("%d:%02d", minutes, seconds)
}
