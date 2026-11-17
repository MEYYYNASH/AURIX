package com.example.data

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
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
    AppNotificationHelper.ensureChannels(this)
    promoteToForeground(
      UpdateManagerHolder.getInstance(this).uiState.value,
      BackgroundDownloadRegistry.activeTrackNotifications.value
    )
    startObservingDownloads()
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    val updateManager = UpdateManagerHolder.getInstance(this)
    when (intent?.action) {
      ACTION_PAUSE_UPDATE -> updateManager.pauseDownload()
      ACTION_RESUME_UPDATE -> updateManager.startOrResumeDownload()
      ACTION_CANCEL_UPDATE -> updateManager.cancelDownload()
      else -> Unit
    }

    val currentUpdate = updateManager.uiState.value
    val activeTracks = BackgroundDownloadRegistry.activeTrackNotifications.value
    promoteToForeground(currentUpdate, activeTracks)

    if (!isAnyTransferActive(currentUpdate, activeTracks)) {
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
    val updateManager = UpdateManagerHolder.getInstance(this)
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
          val notif = AppNotificationHelper.buildBackgroundDownloadNotification(this@BackgroundDownloadService, updateState, tracks)
          if (AppNotificationHelper.canPostNotifications(this@BackgroundDownloadService)) {
            runCatching {
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
  ) {
    val notif = AppNotificationHelper.buildBackgroundDownloadNotification(this, updateState, tracks)
    val fgsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
    } else {
      0
    }
    runCatching {
      ServiceCompat.startForeground(
        this,
        AppNotificationHelper.NOTIF_ID_BACKGROUND_DOWNLOAD,
        notif,
        fgsType
      )
    }
  }

  private fun isAnyTransferActive(
    updateState: UpdateUiState,
    tracks: Map<String, TrackDownloadNotificationInfo>
  ): Boolean {
    val updateActive = (updateState.stage == UpdateStage.Downloading && !updateState.isPaused) ||
      updateState.stage == UpdateStage.Verifying
    return updateActive || tracks.isNotEmpty()
  }

  private fun stopSelfSafely() {
    runCatching {
      ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
    }
    stopSelf()
  }
}
