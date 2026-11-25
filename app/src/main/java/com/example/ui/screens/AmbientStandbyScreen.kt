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
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.BatteryStd
import androidx.compose.material.icons.rounded.Close
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
import com.example.playback.AudioSpectrumBus
import com.example.widget.AurixWidgetController
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

private const val BURN_IN_DIM_TIMEOUT_MS = 120_000L // 2 minutes without touch
private const val MAX_TILT_DEGREES = 14f
const val STANDBY_PROGRESS_SEGMENTS = 17

/**
 * Full-screen Bedside & Charging "Standby Mode" screen for AURIX.
 *
 * - Keeps screen awake (FLAG_KEEP_SCREEN_ON) and hides system bars while open.
 * - Reuses AURIX's real player state (zero demo or fake data).
 * - Landscape: Left column has Big Clock -> Khmer Date & Chips -> Album Art; Right column has Song Info.
 * - Portrait: Single centered column: Clock -> Chips -> Album Art -> Song Info.
 * - 10 Clock Styles + 12h/24h toggle + blinking colon every second.
 * - 5 Color Moods (Auto cover palette with smooth lerp, Lo-fi, Sunset, Mint, Mono) + drifting radial blobs & vignette.
 * - 3D Gyro Parallax (up to 14° album art tilt with moving glare, opposite depth shift on clock & song info,
 *   slowly recentering neutral point when propped up, pointer fallback & gentle idle sway).
 * - Burn-in protection: slow pixel drift + automatic brightness dim after 2 minutes of no touch.
 * - Top-right glass circle buttons: Mood, Clock Style, Gyro (+ Exit); optional bottom row: Previous, Play/Pause, Next.
 * - Tap screen to show/hide controls; swipe down or press Back to exit.
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
  colorMood: StandbyColorMood = StandbyColorMood.AUTO,
  gyroEnabled: Boolean = true,
  orbiMood: OrbiMood = OrbiMood.CLASSIC_IDLE,
  orbiTheme: OrbiCompanionTheme = OrbiCompanionTheme.CLASSIC,
  khmerLyricsFont: com.example.ui.theme.KhmerLyricsFont = com.example.ui.theme.KhmerLyricsFont.KANTUMRUY_PRO,
  onSelectClockStyle: (StandbyClockStyle) -> Unit = {},
  onToggleUse24h: (Boolean) -> Unit = {},
  onSelectColorMood: (StandbyColorMood) -> Unit = {},
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

  // Live clock tick every 500ms for crisp 1-second blinking colon and exact minute rollovers
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

  // Auto-hide controls after 4.5 seconds of inactivity
  var controlsVisible by remember { mutableStateOf(true) }
  var lastInteractionToken by remember { mutableIntStateOf(0) }
  var showClockPickerSheet by remember { mutableStateOf(false) }

  fun registerUserTouch() {
    lastTouchTimestampMs = System.currentTimeMillis()
    lastInteractionToken += 1
  }

  LaunchedEffect(controlsVisible, lastInteractionToken, showClockPickerSheet) {
    if (controlsVisible && !showClockPickerSheet) {
      delay(4_500L)
      controlsVisible = false
    }
  }

  // Burn-in protection: dim brightness after 2 minutes of no touch
  val isBurnInDimmed = (nowMillis - lastTouchTimestampMs) >= BURN_IN_DIM_TIMEOUT_MS
  val burnInBrightnessAlpha by animateFloatAsState(
    targetValue = if (isBurnInDimmed) 0.42f else 1.0f,
    animationSpec = if (reducedMotion) snap() else tween(durationMillis = 1600),
    label = "standby_burn_in_dim"
  )

  // Burn-in protection: slow pixel drift (±5.dp over a 64-second orbital cycle)
  val infiniteTransition = rememberInfiniteTransition(label = "standby_infinite")
  val driftPhase by infiniteTransition.animateFloat(
    initialValue = 0f,
    targetValue = (Math.PI * 2.0).toFloat(),
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = 64_000, easing = LinearEasing),
      repeatMode = RepeatMode.Restart
    ),
    label = "standby_burn_in_drift"
  )
  val driftOffsetX = if (reducedMotion) 0.dp else (sin(driftPhase) * 5f).dp
  val driftOffsetY = if (reducedMotion) 0.dp else (cos(driftPhase * 0.75f) * 4f).dp

  // Gentle idle sway for 3D parallax when stationary
  val swayPhase by infiniteTransition.animateFloat(
    initialValue = 0f,
    targetValue = (Math.PI * 2.0).toFloat(),
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = 9_500, easing = LinearEasing),
      repeatMode = RepeatMode.Restart
    ),
    label = "standby_idle_sway"
  )

  // Real Hardware Gyro / Rotation / Accelerometer Parallax with Slowly Recentering Neutral Point
  var sensorTiltX by remember { mutableFloatStateOf(0f) } // normalized -1..1 (roll)
  var sensorTiltY by remember { mutableFloatStateOf(0f) } // normalized -1..1 (pitch)
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
            // orientationAngles[1] = pitch (radians), orientationAngles[2] = roll (radians)
            val rollDeg = Math.toDegrees(orientationAngles[2].toDouble()).toFloat()
            val pitchDeg = Math.toDegrees(orientationAngles[1].toDouble()).toFloat()
            rollDeg to pitchDeg
          }.getOrDefault(0f to 0f)
        } else {
          // Accelerometer fallback (m/s^2 -> approximate tilt degrees)
          val gx = event.values.getOrNull(0) ?: 0f
          val gy = event.values.getOrNull(1) ?: 0f
          (-gx * 4.5f) to (gy * 4.5f)
        }

        if (!initialized) {
          neutralX = rawX
          neutralY = rawY
          initialized = true
        } else {
          // Neutral point slowly follows the phone so it recenters when propped up
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

  val idleSwayX = if (gyroEnabled && !reducedMotion) sin(swayPhase) * 0.16f else 0f
  val idleSwayY = if (gyroEnabled && !reducedMotion) cos(swayPhase * 0.8f) * 0.14f else 0f
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

  // Smoothly animated tilt values for 60fps 3D parallax
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

  // Extract palette from real cover art for Auto mode and lerp smoothly when song or mood changes
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

  BoxWithConstraints(
    modifier = Modifier
      .fillMaxSize()
      .background(Color.Black)
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
            // Swipe down exits Standby Mode
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

    // 1. Drifting Radial Blobs + Vignette Canvas Background
    StandbyDriftingBlobsBackground(
      palette = palette,
      reducedMotion = reducedMotion,
      modifier = Modifier.alpha(burnInBrightnessAlpha)
    )

    // Opposite depth shifts for Clock (layer 1) and Song Info (layer 2)
    val clockParallaxX = (-smoothTiltX * 13f).dp
    val clockParallaxY = (-smoothTiltY * 13f).dp
    val infoParallaxX = (-smoothTiltX * 7.5f).dp
    val infoParallaxY = (-smoothTiltY * 7.5f).dp

    // 2. Main Bedside Layout with Burn-In Pixel Drift & Dimming
    Box(
      modifier = Modifier
        .fillMaxSize()
        .safeDrawingPadding()
        .offset(x = driftOffsetX, y = driftOffsetY)
        .alpha(burnInBrightnessAlpha)
    ) {
      if (isLandscape) {
        // LANDSCAPE: Left side = Big Clock -> Date & Chips -> Album Art; Right side = Song Info
        Row(
          modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp, vertical = 18.dp),
          horizontalArrangement = Arrangement.spacedBy(32.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column(
            modifier = Modifier
              .weight(0.48f)
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
                reducedMotion = reducedMotion
              )
            }

            Spacer(modifier = Modifier.height(10.dp))

            StandbyMetadataChipsRow(
              khmerDateText = khmerDateText,
              colorMood = colorMood,
              batteryPercent = batteryPercent,
              isCharging = isCharging,
              accent = palette.accent,
              onCycleMood = {
                registerUserTouch()
                onSelectColorMood(colorMood.next())
              }
            )

            Spacer(modifier = Modifier.height(16.dp))

            val artSize = (containerMaxHeight * 0.40f).coerceIn(108.dp, 172.dp)
            StandbyAlbumArtWithHalo(
              artworkModel = artworkModel,
              songTitle = song?.title,
              palette = palette,
              isPlaying = isPlaying,
              reducedMotion = reducedMotion,
              tiltX = smoothTiltX,
              tiltY = smoothTiltY,
              size = artSize,
              onTogglePlayPause = {
                registerUserTouch()
                onPlayPause()
              }
            )
          }

          Column(
            modifier = Modifier
              .weight(0.52f)
              .fillMaxHeight()
              .offset(x = infoParallaxX, y = infoParallaxY),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.Start
          ) {
            StandbySongInfoPanel(
              song = song,
              albumName = resolvedAlbumName,
              isPlaying = isPlaying,
              livePositionMs = livePositionMs,
              totalDurationMs = totalDurationMs,
              lyricSnap = lyricSnap,
              spectrumBars = spectrumBars,
              palette = palette,
              reducedMotion = reducedMotion,
              centered = false,
              khmerLyricsFont = khmerLyricsFont,
              onSeekFraction = { fraction ->
                registerUserTouch()
                if (totalDurationMs > 0L) {
                  val targetMs = (totalDurationMs * fraction).toLong().coerceIn(0L, totalDurationMs)
                  livePositionMs = targetMs
                  onSeek?.invoke(targetMs)
                }
              }
            )
          }
        }
      } else {
        // PORTRAIT: Single centered column: Clock -> Chips -> Art -> Song Info
        Column(
          modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 22.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.SpaceEvenly
        ) {
          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.offset(x = clockParallaxX, y = clockParallaxY)
          ) {
            StandbyClockFace(
              timeParts = timeParts,
              style = clockStyle,
              accent = palette.accent,
              compact = false,
              reducedMotion = reducedMotion
            )

            StandbyMetadataChipsRow(
              khmerDateText = khmerDateText,
              colorMood = colorMood,
              batteryPercent = batteryPercent,
              isCharging = isCharging,
              accent = palette.accent,
              onCycleMood = {
                registerUserTouch()
                onSelectColorMood(colorMood.next())
              }
            )
          }

          val artSize = (containerMaxWidth * 0.52f).coerceIn(156.dp, 226.dp)
          StandbyAlbumArtWithHalo(
            artworkModel = artworkModel,
            songTitle = song?.title,
            palette = palette,
            isPlaying = isPlaying,
            reducedMotion = reducedMotion,
            tiltX = smoothTiltX,
            tiltY = smoothTiltY,
            size = artSize,
            onTogglePlayPause = {
              registerUserTouch()
              onPlayPause()
            }
          )

          Box(modifier = Modifier.offset(x = infoParallaxX, y = infoParallaxY)) {
            StandbySongInfoPanel(
              song = song,
              albumName = resolvedAlbumName,
              isPlaying = isPlaying,
              livePositionMs = livePositionMs,
              totalDurationMs = totalDurationMs,
              lyricSnap = lyricSnap,
              spectrumBars = spectrumBars,
              palette = palette,
              reducedMotion = reducedMotion,
              centered = true,
              khmerLyricsFont = khmerLyricsFont,
              onSeekFraction = { fraction ->
                registerUserTouch()
                if (totalDurationMs > 0L) {
                  val targetMs = (totalDurationMs * fraction).toLong().coerceIn(0L, totalDurationMs)
                  livePositionMs = targetMs
                  onSeek?.invoke(targetMs)
                }
              }
            )
          }
        }
      }
    }

    // 3. Top-Right Glass Circle Buttons: Mood, Clock Style, Gyro (+ Close)
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
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
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
            modifier = Modifier.size(20.dp)
          )
        }

        // Clock Style picker glass circle button
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
            tint = Color.White,
            modifier = Modifier.size(20.dp)
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
            tint = if (gyroEnabled) palette.accent else Color.White.copy(alpha = 0.55f),
            modifier = Modifier.size(20.dp)
          )
        }

        // Exit button
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
            tint = Color.White,
            modifier = Modifier.size(20.dp)
          )
        }
      }
    }

    // 4. Optional Bottom Row: Previous, Play/Pause, Next
    AnimatedVisibility(
      visible = controlsVisible,
      enter = if (reducedMotion) fadeIn(snap()) else fadeIn(tween(220)) + slideInVertically { it / 2 },
      exit = if (reducedMotion) fadeOut(snap()) else fadeOut(tween(300)) + slideOutVertically { it / 2 },
      modifier = Modifier
        .align(Alignment.BottomCenter)
        .safeDrawingPadding()
        .padding(bottom = 16.dp)
    ) {
      Surface(
        shape = CircleShape,
        color = Color(0xCC10131E),
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
              tint = Color.White,
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
              tint = Color(0xFF090B12),
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
              tint = Color.White,
              modifier = Modifier.size(26.dp)
            )
          }
        }
      }
    }

    // 5. Clock Style Picker Bottom Sheet
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
      color = if (active) accent.copy(alpha = 0.48f) else Color.White.copy(alpha = 0.16f)
    ),
    modifier = Modifier
      .minimumInteractiveComponentSize()
      .size(44.dp)
      .testTag(testTag),
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
 * Chips Row:
 * 1) Khmer Date (Khmer weekday and Khmer month names)
 * 2) Current Mood Name (Auto, Lo-fi, Sunset, Mint, Mono)
 * 3) Battery %
 */
@Composable
private fun StandbyMetadataChipsRow(
  khmerDateText: String,
  colorMood: StandbyColorMood,
  batteryPercent: Int,
  isCharging: Boolean,
  accent: Color,
  onCycleMood: () -> Unit
) {
  Row(
    modifier = Modifier.horizontalScroll(rememberScrollState()),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp)
  ) {
    // 1. Khmer Date Chip
    Surface(
      shape = CircleShape,
      color = Color.White.copy(alpha = 0.08f),
      border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
      modifier = Modifier.testTag("standby_khmer_date_chip")
    ) {
      Text(
        text = com.example.ui.theme.rememberKhmerTitleText(
          text = khmerDateText,
          baseFontSize = 11.5.sp,
          isBold = false
        ),
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.5.sp),
        color = Color.White.copy(alpha = 0.88f),
        modifier = Modifier.padding(horizontal = 11.dp, vertical = 5.dp)
      )
    }

    // 2. Current Mood Name Chip
    Surface(
      onClick = onCycleMood,
      shape = CircleShape,
      color = accent.copy(alpha = 0.14f),
      border = BorderStroke(1.dp, accent.copy(alpha = 0.36f)),
      modifier = Modifier.testTag("standby_mood_chip")
    ) {
      Row(
        modifier = Modifier.padding(horizontal = 11.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
      ) {
        Box(
          modifier = Modifier
            .size(6.dp)
            .clip(CircleShape)
            .background(accent)
        )
        Text(
          text = colorMood.displayNameEn,
          style = MaterialTheme.typography.labelSmall.copy(
            fontSize = 11.5.sp,
            fontWeight = FontWeight.SemiBold
          ),
          color = Color.White.copy(alpha = 0.92f)
        )
      }
    }

    // 3. Battery % Chip
    Surface(
      shape = CircleShape,
      color = Color.White.copy(alpha = 0.08f),
      border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
      modifier = Modifier.testTag("standby_battery_chip")
    ) {
      Row(
        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
      ) {
        Icon(
          imageVector = if (isCharging) Icons.Rounded.BatteryChargingFull else Icons.Rounded.BatteryStd,
          contentDescription = null,
          tint = if (isCharging) Color(0xFF4ADE80) else accent.copy(alpha = 0.90f),
          modifier = Modifier.size(13.dp)
        )
        Text(
          text = "$batteryPercent%",
          style = MaterialTheme.typography.labelSmall.copy(
            fontSize = 11.5.sp,
            fontWeight = FontWeight.SemiBold
          ),
          color = Color.White.copy(alpha = 0.88f)
        )
      }
    }
  }
}

/**
 * Album Art with:
 * - Real cover (from song metadata or custom album cover) OR procedural art fallback built from the palette.
 * - Rounded corners (9%).
 * - Blurred rotating halo behind it.
 * - 3D Gyro Parallax tilt (up to 14 degrees) with a moving specular glare.
 * - Tapping the art toggles play/pause.
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
    initialValue = 0.95f,
    targetValue = 1.07f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = 3_400, easing = FastOutSlowInEasing),
      repeatMode = RepeatMode.Reverse
    ),
    label = "standby_halo_pulse"
  )

  val cornerShape = RoundedCornerShape(percent = 9)
  val pitchDeg = (-tiltY * MAX_TILT_DEGREES).coerceIn(-MAX_TILT_DEGREES, MAX_TILT_DEGREES)
  val rollDeg = (tiltX * MAX_TILT_DEGREES).coerceIn(-MAX_TILT_DEGREES, MAX_TILT_DEGREES)
  val artInteraction = remember { MutableInteractionSource() }

  Box(
    contentAlignment = Alignment.Center,
    modifier = Modifier.size(size + 36.dp)
  ) {
    // Blurred rotating halo behind the album art
    Canvas(
      modifier = Modifier
        .size(size + 28.dp)
        .scale(if (reducedMotion || !isPlaying) 1f else haloPulse)
        .graphicsLayer {
          rotationZ = if (reducedMotion) 0f else haloAngle
        }
        .blur(22.dp)
    ) {
      val r = this.size.minDimension * 0.5f
      val c = Offset(this.size.width * 0.5f, this.size.height * 0.5f)
      drawCircle(
        brush = Brush.sweepGradient(
          colors = listOf(
            palette.blob1.copy(alpha = 0.65f),
            palette.blob2.copy(alpha = 0.58f),
            palette.accent.copy(alpha = 0.70f),
            palette.blob4.copy(alpha = 0.55f),
            palette.blob1.copy(alpha = 0.65f)
          ),
          center = c
        ),
        radius = r,
        center = c
      )
    }

    // 3D tilting album art card (9% rounded corners) + moving glare
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
        .border(1.dp, Color.White.copy(alpha = 0.18f), cornerShape)
        .clickable(
          interactionSource = artInteraction,
          indication = null,
          onClick = onTogglePlayPause
        )
        .testTag("standby_album_art"),
      contentAlignment = Alignment.Center
    ) {
      if (artworkModel != null) {
        AsyncImage(
          model = artworkModel,
          contentDescription = songTitle,
          contentScale = ContentScale.Crop,
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
        val glareAlpha = (0.10f + tiltMagnitude * 0.14f).coerceIn(0.08f, 0.28f)

        drawCircle(
          brush = Brush.radialGradient(
            colors = listOf(
              Color.White.copy(alpha = glareAlpha),
              Color.White.copy(alpha = glareAlpha * 0.25f),
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

/**
 * Song Info Panel:
 * - Animated 4-bar equalizer with the label "NOW PLAYING" + AURIX mini planet emblem
 * - Real song title, artist, and album
 * - Live synced active lyric line (if available, active-only animation)
 * - Progress bar of 17 rounded segments
 * - Elapsed / Total time
 */
@Composable
private fun StandbySongInfoPanel(
  song: Song?,
  albumName: String,
  isPlaying: Boolean,
  livePositionMs: Long,
  totalDurationMs: Long,
  lyricSnap: com.example.widget.SyncedLyricLineSnapshot,
  spectrumBars: FloatArray,
  palette: StandbyMoodPalette,
  reducedMotion: Boolean,
  centered: Boolean,
  khmerLyricsFont: com.example.ui.theme.KhmerLyricsFont,
  onSeekFraction: (Float) -> Unit
) {
  val horizontalAlign = if (centered) Alignment.CenterHorizontally else Alignment.Start
  val textAlign = if (centered) TextAlign.Center else TextAlign.Start

  Column(
    modifier = Modifier.fillMaxWidth(),
    horizontalAlignment = horizontalAlign,
    verticalArrangement = Arrangement.spacedBy(10.dp)
  ) {
    // 1. Animated 4-bar equalizer + "NOW PLAYING" label + AURIX tilted planet emblem
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
          letterSpacing = 1.8.sp
        ),
        color = palette.accent
      )
      StandbyMiniPlanetLogo(
        accent = palette.accent,
        modifier = Modifier.size(16.dp)
      )
    }

    // 2. Real Title, Artist, and Album (zero fake/demo data)
    Column(
      horizontalAlignment = horizontalAlign,
      verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
      val displayTitle = song?.title?.takeIf { it.isNotBlank() }
        ?: stringResource(id = R.string.standby_no_track)
      Text(
        text = com.example.ui.theme.rememberKhmerTitleText(
          text = displayTitle,
          baseFontSize = 22.sp,
          isBold = true
        ),
        style = MaterialTheme.typography.titleLarge.copy(
          fontSize = 22.sp,
          fontWeight = FontWeight.Bold,
          lineHeight = 28.sp
        ),
        color = Color.White,
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
        text = com.example.ui.theme.rememberKhmerTitleText(
          text = artistAlbumLine,
          baseFontSize = 14.sp,
          isBold = false
        ),
        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
        color = Color.White.copy(alpha = 0.68f),
        textAlign = textAlign,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.testTag("standby_song_artist_album")
      )
    }

    // 3. Optional Active Synced Lyric Line (Active-only animation)
    if (song != null && lyricSnap.hasLyrics && lyricSnap.currentLine.isNotBlank()) {
      AnimatedContent(
        targetState = lyricSnap.currentLine,
        transitionSpec = {
          if (reducedMotion) {
            fadeIn(snap()) togetherWith fadeOut(snap())
          } else {
            (slideInVertically(tween(320, easing = FastOutSlowInEasing)) { it / 3 } + fadeIn(tween(240))) togetherWith
              (slideOutVertically(tween(240)) { -it / 3 } + fadeOut(tween(180)))
          }
        },
        label = "standby_lyric_transition"
      ) { currentLine ->
        Text(
          text = com.example.ui.theme.rememberKhmerLyricsText(
            text = currentLine,
            khmerLyricsFont = khmerLyricsFont,
            baseFontSize = 15.sp,
            isBold = true
          ),
          style = MaterialTheme.typography.bodyMedium.copy(
            fontWeight = FontWeight.SemiBold,
            lineHeight = 21.sp
          ),
          color = palette.accent.copy(alpha = 0.94f),
          textAlign = textAlign,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis
        )
      }
    }

    Spacer(modifier = Modifier.height(2.dp))

    // 4. Progress bar of 17 rounded segments + Elapsed / Total time
    val progressFraction = if (totalDurationMs > 0L && song != null) {
      (livePositionMs.toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 1f)
    } else {
      0f
    }

    Column(
      modifier = Modifier.fillMaxWidth(if (centered) 0.88f else 0.96f),
      verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
      StandbySegmentedProgressBar(
        progress = progressFraction,
        accent = palette.accent,
        onSelectFraction = onSeekFraction,
        modifier = Modifier
          .fillMaxWidth()
          .height(14.dp)
          .testTag("standby_17_segment_progress")
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
            fontSize = 11.5.sp
          ),
          color = Color.White.copy(alpha = 0.68f),
          modifier = Modifier.testTag("standby_elapsed_time")
        )
        Text(
          text = formatStandbyDuration(if (song != null) totalDurationMs else 0L),
          style = MaterialTheme.typography.labelSmall.copy(
            fontFamily = StandbyClockFonts.OutfitFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 11.5.sp
          ),
          color = Color.White.copy(alpha = 0.52f),
          modifier = Modifier.testTag("standby_total_time")
        )
      }
    }
  }
}

/**
 * Animated 4-bar equalizer synced to real AudioSpectrumBus bars.
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

  Canvas(modifier = Modifier.size(width = 16.dp, height = 14.dp)) {
    val barCount = 4
    val gap = 2.2.dp.toPx()
    val barWidth = ((size.width - gap * (barCount - 1)) / barCount).coerceAtLeast(1.8.dp.toPx())
    val hasSignal = bars.any { it > 0.04f }

    for (i in 0 until barCount) {
      val sampleIdx = (i * (bars.size / barCount.coerceAtLeast(1))).coerceIn(0, (bars.size - 1).coerceAtLeast(0))
      val level = when {
        !isPlaying -> 0.24f
        hasSignal -> bars.getOrElse(sampleIdx) { 0.35f }.coerceIn(0.22f, 1f)
        reducedMotion -> 0.55f
        else -> (0.30f + 0.65f * abs(sin(phase + i * 1.15f))).coerceIn(0.24f, 0.98f)
      }
      val barH = (size.height * level).coerceAtLeast(2.5.dp.toPx())
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
 * Progress bar of exactly 17 rounded segments.
 */
@Composable
private fun StandbySegmentedProgressBar(
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
    val segments = STANDBY_PROGRESS_SEGMENTS
    val gap = 3.5.dp.toPx()
    val totalGap = gap * (segments - 1)
    val segWidth = ((size.width - totalGap) / segments).coerceAtLeast(2.dp.toPx())
    val segHeight = 6.dp.toPx()
    val topY = (size.height - segHeight) * 0.5f
    val exactActive = clamped * segments

    for (i in 0 until segments) {
      val segFill = (exactActive - i).coerceIn(0f, 1f)
      val x = i * (segWidth + gap)
      val color = when {
        segFill >= 0.99f -> accent
        segFill > 0.05f -> androidx.compose.ui.graphics.lerp(Color.White.copy(alpha = 0.16f), accent, 0.45f + segFill * 0.55f)
        else -> Color.White.copy(alpha = 0.14f)
      }
      val currentH = if (segFill in 0.05f..0.99f) segHeight + 2.dp.toPx() else segHeight
      val currentY = (size.height - currentH) * 0.5f
      drawRoundRect(
        color = color,
        topLeft = Offset(x, currentY),
        size = Size(segWidth, currentH),
        cornerRadius = CornerRadius(currentH * 0.5f, currentH * 0.5f)
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
