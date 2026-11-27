package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
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
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.AppNotificationHelper
import com.example.data.DownloadQueueItem
import com.example.data.DownloadQueueStatus
import com.example.data.UpdateManager
import com.example.data.UpdateStage
import com.example.data.UpdateUiState
import com.example.playback.AudioOutputRoute
import com.example.playback.AudioOutputRouteInfo
import com.example.playback.EqPreset
import com.example.playback.EqualizerUiState
import kotlinx.coroutines.delay

private sealed class DynamicIslandVisualState(val key: String) {
  data class AudioRouteConnected(
    val route: AudioOutputRoute,
    val deviceName: String,
    val typeLabel: String,
    val eqPresetName: String
  ) : DynamicIslandVisualState("route_${route.id}_$deviceName")

  data class SongDownloadComplete(
    val songId: String,
    val title: String,
    val artist: String
  ) : DynamicIslandVisualState("dl_done_$songId")

  data class AppUpdateProgress(
    val tag: String,
    val stage: UpdateStage,
    val progressFraction: Float,
    val speedLabel: String,
    val isReadyToInstall: Boolean
  ) : DynamicIslandVisualState("upd_${stage.name}_$tag")

  data class SongDownloading(
    val activeCount: Int,
    val primaryTitle: String,
    val primaryArtist: String,
    val progressFraction: Float,
    val statusHint: String
  ) : DynamicIslandVisualState("dl_active_$activeCount")

  data class PersistentHeadphoneBadge(
    val route: AudioOutputRoute,
    val deviceName: String,
    val isPlaying: Boolean
  ) : DynamicIslandVisualState("badge_${route.id}_$deviceName")
}

/**
 * AURIX Dynamic Island Live Activity Capsule:
 * Morphing top pill overlay that surfaces:
 *  1. Bluetooth & Wired (3.5mm / USB-C) headphone connections with exact device name & Auto-EQ preset
 *  2. Live Song & Playlist Download progress + completion celebration
 *  3. Live App Update download progress, verification & one-tap Ready-to-Install prompt
 *  4. Compact active Audio Output indicator while playing on Bluetooth or Wired Headphones
 */
@Composable
fun AurixDynamicIslandOverlay(
  audioOutputInfo: AudioOutputRouteInfo,
  equalizerState: EqualizerUiState,
  downloadQueueItems: List<DownloadQueueItem>,
  updateUiState: UpdateUiState,
  isPlaying: Boolean,
  accentColor: Color,
  reducedMotion: Boolean,
  onOpenMediaOutput: () -> Unit,
  onOpenDownloadQueue: () -> Unit,
  onOpenUpdateSettings: () -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current

  // 1. Detect Audio Output route changes (Bluetooth or Wired headphones connected / switched)
  var initialRouteSeen by remember { mutableStateOf(false) }
  var lastRoute by remember { mutableStateOf(audioOutputInfo.route) }
  var lastDeviceName by remember { mutableStateOf(audioOutputInfo.deviceName) }
  var transientRouteBanner by remember { mutableStateOf<DynamicIslandVisualState.AudioRouteConnected?>(null) }

  val btFallbackName = stringResource(id = R.string.media_output_bluetooth_headphones)
  val wiredFallbackName = stringResource(id = R.string.media_output_wired_headphones)
  val speakerFallbackName = stringResource(id = R.string.media_output_phone_speaker)

  val activePresetForRoute = when (audioOutputInfo.route) {
    AudioOutputRoute.BLUETOOTH -> EqPreset.fromId(equalizerState.bluetoothEqPresetId)
    AudioOutputRoute.HEADPHONES -> EqPreset.fromId(equalizerState.headphonesEqPresetId)
    AudioOutputRoute.SPEAKER -> EqPreset.fromId(equalizerState.speakerEqPresetId)
  }
  val activeEqLabel = presetDisplayName(activePresetForRoute)

  LaunchedEffect(audioOutputInfo.route, audioOutputInfo.deviceName) {
    val currentRoute = audioOutputInfo.route
    val currentName = audioOutputInfo.deviceName
    val resolvedName = when (currentRoute) {
      AudioOutputRoute.BLUETOOTH -> currentName.ifBlank { btFallbackName }
      AudioOutputRoute.HEADPHONES -> currentName.ifBlank { wiredFallbackName }
      AudioOutputRoute.SPEAKER -> speakerFallbackName
    }

    if (!initialRouteSeen) {
      initialRouteSeen = true
      lastRoute = currentRoute
      lastDeviceName = currentName
      // If user launches app with Bluetooth or Wired Headphones already plugged in, flash the capsule briefly
      if (currentRoute != AudioOutputRoute.SPEAKER) {
        transientRouteBanner = DynamicIslandVisualState.AudioRouteConnected(
          route = currentRoute,
          deviceName = resolvedName,
          typeLabel = audioOutputInfo.deviceTypeLabel,
          eqPresetName = activeEqLabel
        )
        delay(3_600L)
        transientRouteBanner = null
      }
      return@LaunchedEffect
    }

    if (currentRoute != lastRoute || currentName != lastDeviceName) {
      lastRoute = currentRoute
      lastDeviceName = currentName
      transientRouteBanner = DynamicIslandVisualState.AudioRouteConnected(
        route = currentRoute,
        deviceName = resolvedName,
        typeLabel = audioOutputInfo.deviceTypeLabel,
        eqPresetName = activeEqLabel
      )
      if (currentRoute != AudioOutputRoute.SPEAKER) {
        AppNotificationHelper.notifyAudioDeviceConnected(
          context = context,
          deviceName = resolvedName,
          routeLabel = audioOutputInfo.deviceTypeLabel.ifBlank { resolvedName },
          eqPresetLabel = activeEqLabel
        )
      }
      delay(4_200L)
      transientRouteBanner = null
    }
  }

  // 2. Detect newly completed song downloads for a 3.2s Dynamic Island celebration
  var seenCompletedKeys by remember {
    mutableStateOf(
      downloadQueueItems
        .filter { it.status == DownloadQueueStatus.COMPLETED }
        .map { it.key }
        .toSet()
    )
  }
  var transientCompletedDownload by remember {
    mutableStateOf<DynamicIslandVisualState.SongDownloadComplete?>(null)
  }

  LaunchedEffect(downloadQueueItems) {
    val completedNow = downloadQueueItems.filter { it.status == DownloadQueueStatus.COMPLETED }
    val newlyDone = completedNow.firstOrNull { it.key !in seenCompletedKeys }
    seenCompletedKeys = completedNow.map { it.key }.toSet()
    if (newlyDone != null) {
      transientCompletedDownload = DynamicIslandVisualState.SongDownloadComplete(
        songId = newlyDone.songId,
        title = newlyDone.song.title,
        artist = newlyDone.song.artist
      )
      delay(3_200L)
      transientCompletedDownload = null
    }
  }

  // 3. Active Song Downloads
  val activeDownloads = remember(downloadQueueItems) {
    downloadQueueItems.filter {
      it.status == DownloadQueueStatus.DOWNLOADING || it.status == DownloadQueueStatus.QUEUED
    }
  }
  val activeDownloadState = remember(activeDownloads) {
    if (activeDownloads.isEmpty()) {
      null
    } else {
      val lead = activeDownloads.firstOrNull { it.status == DownloadQueueStatus.DOWNLOADING }
        ?: activeDownloads.first()
      val avgProgress = activeDownloads.map { it.progress.coerceIn(0f, 1f) }.average().toFloat()
      DynamicIslandVisualState.SongDownloading(
        activeCount = activeDownloads.size,
        primaryTitle = lead.song.title,
        primaryArtist = lead.song.artist,
        progressFraction = avgProgress,
        statusHint = lead.statusHint
      )
    }
  }

  // 4. App Update state (Downloading, Verifying, Ready to Install, or Update Available)
  var dismissedAvailableUpdateTag by remember { mutableStateOf<String?>(null) }
  val updateVisualState = remember(updateUiState, dismissedAvailableUpdateTag) {
    val rel = updateUiState.latestRelease
    val isNewer = rel != null && UpdateManager.isReleaseNewerThanInstalled(updateUiState.installed, rel)
    when {
      updateUiState.stage == UpdateStage.Downloading || updateUiState.stage == UpdateStage.Verifying -> {
        DynamicIslandVisualState.AppUpdateProgress(
          tag = rel?.tagName ?: "v2.4.8",
          stage = updateUiState.stage,
          progressFraction = updateUiState.progress.coerceIn(0f, 1f),
          speedLabel = if (updateUiState.speedBytesPerSec > 0L) {
            UpdateManager.formatSpeed(updateUiState.speedBytesPerSec)
          } else "",
          isReadyToInstall = false
        )
      }
      updateUiState.isValidatedReady && rel != null -> {
        DynamicIslandVisualState.AppUpdateProgress(
          tag = rel.tagName,
          stage = updateUiState.stage,
          progressFraction = 1f,
          speedLabel = "",
          isReadyToInstall = true
        )
      }
      updateUiState.stage == UpdateStage.Available && isNewer && rel?.tagName != dismissedAvailableUpdateTag -> {
        DynamicIslandVisualState.AppUpdateProgress(
          tag = rel?.tagName.orEmpty(),
          stage = UpdateStage.Available,
          progressFraction = 0f,
          speedLabel = "",
          isReadyToInstall = false
        )
      }
      else -> null
    }
  }

  // 5. Compact persistent Bluetooth / Wired Headphones live capsule when playing music
  val persistentHeadphoneState = remember(
    audioOutputInfo.route,
    audioOutputInfo.deviceName,
    isPlaying,
    btFallbackName,
    wiredFallbackName
  ) {
    if (isPlaying && audioOutputInfo.route != AudioOutputRoute.SPEAKER) {
      val name = when (audioOutputInfo.route) {
        AudioOutputRoute.BLUETOOTH -> audioOutputInfo.deviceName.ifBlank { btFallbackName }
        AudioOutputRoute.HEADPHONES -> audioOutputInfo.deviceName.ifBlank { wiredFallbackName }
        AudioOutputRoute.SPEAKER -> ""
      }
      DynamicIslandVisualState.PersistentHeadphoneBadge(
        route = audioOutputInfo.route,
        deviceName = name,
        isPlaying = true
      )
    } else {
      null
    }
  }

  // Priority order for the Dynamic Island capsule
  val currentIslandState: DynamicIslandVisualState? =
    transientRouteBanner
      ?: updateVisualState
      ?: activeDownloadState
      ?: transientCompletedDownload
      ?: persistentHeadphoneState

  Box(
    modifier = modifier
      .fillMaxWidth()
      .statusBarsPadding()
      .padding(top = 6.dp, start = 16.dp, end = 16.dp),
    contentAlignment = Alignment.TopCenter
  ) {
    AnimatedVisibility(
      visible = currentIslandState != null,
      enter = if (reducedMotion) {
        fadeIn(snap())
      } else {
        slideInVertically(
          animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
          )
        ) { -it } +
          scaleIn(
            initialScale = 0.82f,
            transformOrigin = TransformOrigin(0.5f, 0f),
            animationSpec = spring(
              dampingRatio = Spring.DampingRatioMediumBouncy,
              stiffness = Spring.StiffnessMediumLow
            )
          ) +
          fadeIn(tween(180))
      },
      exit = if (reducedMotion) {
        fadeOut(snap())
      } else {
        slideOutVertically(tween(200)) { -it } +
          scaleOut(
            targetScale = 0.85f,
            transformOrigin = TransformOrigin(0.5f, 0f),
            animationSpec = tween(180)
          ) +
          fadeOut(tween(150))
      }
    ) {
      val activeState = currentIslandState ?: return@AnimatedVisibility
      DynamicIslandCapsuleSurface(
        state = activeState,
        accentColor = accentColor,
        reducedMotion = reducedMotion,
        onOpenMediaOutput = onOpenMediaOutput,
        onOpenDownloadQueue = onOpenDownloadQueue,
        onOpenUpdateSettings = {
          if (activeState is DynamicIslandVisualState.AppUpdateProgress &&
            activeState.stage == UpdateStage.Available
          ) {
            dismissedAvailableUpdateTag = activeState.tag
          }
          onOpenUpdateSettings()
        }
      )
    }
  }
}

@Composable
private fun DynamicIslandCapsuleSurface(
  state: DynamicIslandVisualState,
  accentColor: Color,
  reducedMotion: Boolean,
  onOpenMediaOutput: () -> Unit,
  onOpenDownloadQueue: () -> Unit,
  onOpenUpdateSettings: () -> Unit
) {
  val isCompactBadge = state is DynamicIslandVisualState.PersistentHeadphoneBadge
  val pillShape = RoundedCornerShape(999.dp)
  val borderBrush = Brush.horizontalGradient(
    colors = listOf(
      accentColor.copy(alpha = 0.85f),
      Color.White.copy(alpha = 0.30f),
      accentColor.copy(alpha = 0.70f)
    )
  )

  Surface(
    shape = pillShape,
    color = Color(0xFF070A12).copy(alpha = 0.94f),
    shadowElevation = 18.dp,
    border = BorderStroke(1.2.dp, borderBrush),
    modifier = Modifier
      .widthIn(min = if (isCompactBadge) 140.dp else 220.dp, max = 380.dp)
      .clip(pillShape)
      .clickable {
        when (state) {
          is DynamicIslandVisualState.AudioRouteConnected,
          is DynamicIslandVisualState.PersistentHeadphoneBadge -> onOpenMediaOutput()
          is DynamicIslandVisualState.SongDownloading,
          is DynamicIslandVisualState.SongDownloadComplete -> onOpenDownloadQueue()
          is DynamicIslandVisualState.AppUpdateProgress -> onOpenUpdateSettings()
        }
      }
      .testTag("aurix_dynamic_island_capsule")
  ) {
    AnimatedContent(
      targetState = state,
      transitionSpec = {
        if (reducedMotion) {
          fadeIn(snap()) togetherWith fadeOut(snap())
        } else {
          (fadeIn(tween(220)) + scaleIn(tween(220), initialScale = 0.94f)) togetherWith
            (fadeOut(tween(150)) + scaleOut(tween(150), targetScale = 0.94f)) using
            SizeTransform(clip = false)
        }
      },
      contentKey = { it.key },
      label = "dynamic_island_morph"
    ) { target ->
      when (target) {
        is DynamicIslandVisualState.AudioRouteConnected -> {
          val icon = when (target.route) {
            AudioOutputRoute.BLUETOOTH -> Icons.Rounded.BluetoothAudio
            AudioOutputRoute.HEADPHONES -> Icons.Rounded.Headphones
            AudioOutputRoute.SPEAKER -> Icons.Rounded.Speaker
          }
          val headline = if (target.route == AudioOutputRoute.SPEAKER) {
            stringResource(id = R.string.island_output_switched, target.deviceName)
          } else {
            stringResource(id = R.string.island_connected_title, target.deviceName)
          }
          val subtitle = buildString {
            if (target.typeLabel.isNotBlank()) append(target.typeLabel)
            if (target.eqPresetName.isNotBlank()) {
              if (isNotEmpty()) append(" · ")
              append(stringResource(id = R.string.media_output_auto_eq_pill, target.eqPresetName))
            }
          }
          ExpandedIslandRow(
            icon = icon,
            accentColor = accentColor,
            title = headline,
            subtitle = subtitle,
            progressFraction = null,
            showEqWave = true,
            reducedMotion = reducedMotion
          )
        }

        is DynamicIslandVisualState.SongDownloading -> {
          val pct = (target.progressFraction * 100f).toInt().coerceIn(0, 100)
          val title = if (target.activeCount > 1) {
            stringResource(id = R.string.island_downloading_multi, target.activeCount, pct)
          } else {
            stringResource(id = R.string.island_downloading_song, target.primaryTitle)
          }
          val sub = buildString {
            if (target.primaryArtist.isNotBlank()) append(target.primaryArtist)
            if (target.statusHint.isNotBlank()) {
              if (isNotEmpty()) append(" · ")
              append(target.statusHint)
            }
            if (isNotEmpty()) append(" · ")
            append("$pct%")
          }
          ExpandedIslandRow(
            icon = Icons.Rounded.Download,
            accentColor = Color(0xFF38BDF8),
            title = title,
            subtitle = sub,
            progressFraction = target.progressFraction,
            showEqWave = false,
            reducedMotion = reducedMotion
          )
        }

        is DynamicIslandVisualState.SongDownloadComplete -> {
          ExpandedIslandRow(
            icon = Icons.Rounded.DownloadDone,
            accentColor = Color(0xFF22C55E),
            title = stringResource(id = R.string.island_download_saved, target.title),
            subtitle = target.artist,
            progressFraction = 1f,
            showEqWave = false,
            reducedMotion = reducedMotion
          )
        }

        is DynamicIslandVisualState.AppUpdateProgress -> {
          val pct = (target.progressFraction * 100f).toInt().coerceIn(0, 100)
          val title = when {
            target.isReadyToInstall -> stringResource(id = R.string.island_update_ready_install, target.tag)
            target.stage == UpdateStage.Verifying -> stringResource(id = R.string.island_update_verifying, target.tag)
            target.stage == UpdateStage.Available -> stringResource(id = R.string.island_update_available, target.tag)
            else -> stringResource(id = R.string.island_update_downloading, pct)
          }
          val sub = when {
            target.isReadyToInstall -> stringResource(id = R.string.notif_update_ready_body)
            target.stage == UpdateStage.Verifying -> stringResource(id = R.string.notif_verifying_update_body)
            target.speedLabel.isNotBlank() -> "${target.tag} · ${target.speedLabel}"
            else -> target.tag
          }
          ExpandedIslandRow(
            icon = if (target.isReadyToInstall) Icons.Rounded.SystemUpdateAlt else Icons.Rounded.CloudDownload,
            accentColor = if (target.isReadyToInstall) Color(0xFF22C55E) else accentColor,
            title = title,
            subtitle = sub,
            progressFraction = if (target.stage == UpdateStage.Downloading) target.progressFraction else null,
            showEqWave = false,
            reducedMotion = reducedMotion
          )
        }

        is DynamicIslandVisualState.PersistentHeadphoneBadge -> {
          val icon = when (target.route) {
            AudioOutputRoute.BLUETOOTH -> Icons.Rounded.BluetoothAudio
            AudioOutputRoute.HEADPHONES -> Icons.Rounded.Headphones
            AudioOutputRoute.SPEAKER -> Icons.Rounded.Speaker
          }
          Row(
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Icon(
              imageVector = icon,
              contentDescription = null,
              tint = accentColor,
              modifier = Modifier.size(15.dp)
            )
            Text(
              text = target.deviceName,
              style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
              ),
              color = Color.White,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              modifier = Modifier.widthIn(max = 175.dp)
            )
            IslandMiniWaveform(
              accentColor = accentColor,
              reducedMotion = reducedMotion
            )
          }
        }
      }
    }
  }
}

@Composable
private fun ExpandedIslandRow(
  icon: ImageVector,
  accentColor: Color,
  title: String,
  subtitle: String,
  progressFraction: Float?,
  showEqWave: Boolean,
  reducedMotion: Boolean
) {
  Column(
    modifier = Modifier
      .padding(horizontal = 14.dp, vertical = 10.dp)
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      Box(
        modifier = Modifier
          .size(32.dp)
          .clip(CircleShape)
          .background(accentColor.copy(alpha = 0.22f))
          .border(1.dp, accentColor.copy(alpha = 0.70f), CircleShape),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          tint = accentColor,
          modifier = Modifier.size(17.dp)
        )
      }

      Column(modifier = Modifier.weight(1f, fill = false)) {
        Text(
          text = title,
          style = MaterialTheme.typography.labelLarge.copy(
            fontWeight = FontWeight.ExtraBold,
            fontSize = 13.sp
          ),
          color = Color.White,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
        if (subtitle.isNotBlank()) {
          Text(
            text = subtitle,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
            color = Color.White.copy(alpha = 0.74f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }
      }

      if (showEqWave) {
        IslandMiniWaveform(
          accentColor = accentColor,
          reducedMotion = reducedMotion
        )
      } else if (progressFraction != null) {
        val animatedProgress by animateFloatAsState(
          targetValue = progressFraction.coerceIn(0f, 1f),
          animationSpec = if (reducedMotion) snap() else tween(240),
          label = "island_ring_pct"
        )
        Box(
          modifier = Modifier.size(26.dp),
          contentAlignment = Alignment.Center
        ) {
          CircularProgressIndicator(
            progress = { animatedProgress },
            strokeWidth = 2.5.dp,
            color = accentColor,
            trackColor = Color.White.copy(alpha = 0.16f),
            modifier = Modifier.fillMaxSize()
          )
        }
      }
    }
  }
}

@Composable
private fun IslandMiniWaveform(
  accentColor: Color,
  reducedMotion: Boolean
) {
  val transition = if (!reducedMotion) {
    rememberInfiniteTransition(label = "island_wave")
  } else {
    null
  }
  val phase = transition?.animateFloat(
    initialValue = 0f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = 720, easing = FastOutSlowInEasing),
      repeatMode = RepeatMode.Reverse
    ),
    label = "island_wave_phase"
  )?.value ?: 0.6f

  Canvas(
    modifier = Modifier
      .width(18.dp)
      .height(14.dp)
  ) {
    val barCount = 4
    val spacing = 2.dp.toPx()
    val barWidth = ((size.width - spacing * (barCount - 1)) / barCount).coerceAtLeast(2f)
    val multipliers = floatArrayOf(0.45f, 0.95f, 0.65f, 0.80f)
    for (i in 0 until barCount) {
      val dyn = if (i % 2 == 0) phase else (1f - phase * 0.55f)
      val h = (size.height * (0.28f + 0.72f * multipliers[i] * dyn)).coerceIn(3f, size.height)
      val x = i * (barWidth + spacing)
      val y = (size.height - h) / 2f
      drawRoundRect(
        color = accentColor,
        topLeft = Offset(x, y),
        size = Size(barWidth, h),
        cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
      )
    }
  }
}
