package com.example.data

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class BackgroundDownloadService : Service() {
  companion object {
    const val ACTION_START_SYNC = "com.example.data.action.START_SYNC"
    const val ACTION_PAUSE_UPDATE = "com.example.data.action.PAUSE_UPDATE"
    const val ACTION_RESUME_UPDATE = "com.example.data.action.RESUME_UPDATE"
    const val ACTION_CANCEL_UPDATE = "com.example.data.action.CANCEL_UPDATE"

    fun startSync(context: Context) {
      val appContext = context.applicationContext
      runCatching {
        val intent = Intent(appContext, BackgroundDownloadService::class.java).apply {
          action = ACTION_START_SYNC
        }
        ContextCompat.startForegroundService(appContext, intent)
      }
    }
  }

  private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
  private var observeJob: Job? = null
  private var lastNotifUpdateMs = 0L

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onCreate() {
    super.onCreate()
    runCatching { AppNotificationHelper.ensureChannels(this) }
    val updateState = runCatching { UpdateManagerHolder.getInstance(this).uiState.value }
      .getOrDefault(defaultUpdateState())
    val activeTracks = runCatching { BackgroundDownloadRegistry.activeTrackNotifications.value }
      .getOrDefault(emptyMap())
    val promoted = promoteToForeground(updateState, activeTracks)
    if (!promoted) {
      stopSelfSafely()
      return
    }
    startObservingDownloads()
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    val updateManager = runCatching { UpdateManagerHolder.getInstance(this) }.getOrNull()
    if (updateManager == null) {
      promoteToForeground(defaultUpdateState(), emptyMap())
      stopSelfSafely()
      return START_NOT_STICKY
    }

    runCatching {
      when (intent?.action) {
        ACTION_PAUSE_UPDATE -> updateManager.pauseDownload()
        ACTION_RESUME_UPDATE -> updateManager.startOrResumeDownload()
        ACTION_CANCEL_UPDATE -> updateManager.cancelDownload()
        else -> Unit
      }
    }

    val currentUpdate = runCatching { updateManager.uiState.value }.getOrDefault(defaultUpdateState())
    val activeTracks = runCatching { BackgroundDownloadRegistry.activeTrackNotifications.value }
      .getOrDefault(emptyMap())
    val promoted = promoteToForeground(currentUpdate, activeTracks)

    if (!promoted || !isAnyTransferActive(currentUpdate, activeTracks)) {
      stopSelfSafely()
    }
    return START_NOT_STICKY
  }

  override fun onTimeout(startId: Int, fgsType: Int) {
    // Required Android 15+ safety: gracefully pause active update transfer & stop foreground service
    runCatching {
      UpdateManagerHolder.getInstance(this).pauseDownload()
    }
    stopSelfSafely()
  }

  override fun onDestroy() {
    observeJob?.cancel()
    serviceScope.cancel()
    super.onDestroy()
  }

  private fun startObservingDownloads() {
    observeJob?.cancel()
    val updateManager = runCatching { UpdateManagerHolder.getInstance(this) }.getOrNull() ?: return
    observeJob = serviceScope.launch {
      combine(
        updateManager.uiState,
        BackgroundDownloadRegistry.activeTrackNotifications
      ) { updateState, tracks ->
        updateState to tracks
      }.collect { (updateState, tracks) ->
        if (!isAnyTransferActive(updateState, tracks)) {
          stopSelfSafely()
          return@collect
        }

        val now = System.currentTimeMillis()
        val forceImmediate = updateState.stage == UpdateStage.Verifying || updateState.isPaused
        if (forceImmediate || now - lastNotifUpdateMs >= 450L) {
          lastNotifUpdateMs = now
          runCatching {
            val notif = AppNotificationHelper.buildBackgroundDownloadNotification(this@BackgroundDownloadService, updateState, tracks)
            if (AppNotificationHelper.canPostNotifications(this@BackgroundDownloadService)) {
              NotificationManagerCompat.from(this@BackgroundDownloadService)
                .notify(AppNotificationHelper.NOTIF_ID_BACKGROUND_DOWNLOAD, notif)
            }
          }
        }
      }
    }
  }

  private fun promoteToForeground(
    updateState: UpdateUiState,
    tracks: Map<String, TrackDownloadNotificationInfo>
  ): Boolean {
    val fgsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
    } else {
      0
    }
    val notif = runCatching {
      AppNotificationHelper.buildBackgroundDownloadNotification(this, updateState, tracks)
    }.getOrElse {
      NotificationCompat.Builder(this, AppNotificationHelper.CHANNEL_BACKGROUND_DOWNLOADS)
        .setSmallIcon(R.drawable.ic_stat_aurix)
        .setContentTitle("AURIX")
        .setContentText("Syncing downloads")
        .setOngoing(true)
        .setSilent(true)
        .build()
    }
    return runCatching {
      ServiceCompat.startForeground(
        this,
        AppNotificationHelper.NOTIF_ID_BACKGROUND_DOWNLOAD,
        notif,
        fgsType
      )
      true
    }.getOrDefault(false)
  }

  private fun isAnyTransferActive(
    updateState: UpdateUiState,
    tracks: Map<String, TrackDownloadNotificationInfo>
  ): Boolean {
    val updateActive = (updateState.stage == UpdateStage.Downloading && !updateState.isPaused) ||
      updateState.stage == UpdateStage.Verifying
    return updateActive || tracks.isNotEmpty()
  }

  private fun defaultUpdateState(): UpdateUiState {
    return UpdateUiState(installed = GitHubUpdater.getInstalledAppInfo(applicationContext))
  }

  private fun stopSelfSafely() {
    runCatching {
      ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
    }
    runCatching { stopSelf() }
  }
}
