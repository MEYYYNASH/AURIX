package com.example.data

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R

object AppNotificationHelper {
  const val CHANNEL_UPDATE_RELEASES = "aurix_update_releases"
  const val CHANNEL_BACKGROUND_DOWNLOADS = "aurix_background_downloads"

  const val NOTIF_ID_UPDATE_AVAILABLE = 4001
  const val NOTIF_ID_BACKGROUND_DOWNLOAD = 4002
  const val NOTIF_ID_UPDATE_STATUS = 4003

  const val EXTRA_OPEN_SCREEN = "aurix_open_screen"
  const val EXTRA_UPDATE_ACTION = "aurix_update_action"
  const val EXTRA_OPEN_LIBRARY_TAB = "aurix_library_tab"

  const val SCREEN_SETTINGS = "settings"
  const val SCREEN_LIBRARY = "library"
  const val SCREEN_NOW_PLAYING_PLAYER = "now_playing_player"
  const val SCREEN_NOW_PLAYING_LYRICS = "now_playing_lyrics"

  const val ACTION_VIEW_UPDATE = "view"
  const val ACTION_DOWNLOAD_UPDATE = "download_update"
  const val ACTION_INSTALL_UPDATE = "install_update"
  const val ACTION_RETRY_UPDATE = "retry_update"

  private const val PREFS_NAME = "aurix_update_notif_prefs"
  private const val KEY_LAST_NOTIFIED_VERSION_CODE = "last_notified_version_code"

  fun ensureChannels(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

    val updatesChannel = NotificationChannel(
      CHANNEL_UPDATE_RELEASES,
      context.getString(R.string.notif_channel_updates_name),
      NotificationManager.IMPORTANCE_DEFAULT
    ).apply {
      description = context.getString(R.string.notif_channel_updates_desc)
      enableVibration(true)
    }

    val downloadsChannel = NotificationChannel(
      CHANNEL_BACKGROUND_DOWNLOADS,
      context.getString(R.string.notif_channel_downloads_name),
      NotificationManager.IMPORTANCE_LOW
    ).apply {
      description = context.getString(R.string.notif_channel_downloads_desc)
      setShowBadge(false)
    }

    manager.createNotificationChannel(updatesChannel)
    manager.createNotificationChannel(downloadsChannel)
  }

  fun canPostNotifications(context: Context): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      val granted = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.POST_NOTIFICATIONS
      ) == PackageManager.PERMISSION_GRANTED
      if (!granted) return false
    }
    return NotificationManagerCompat.from(context).areNotificationsEnabled()
  }

  fun getLastNotifiedVersionCode(context: Context): Long {
    val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    return prefs.getLong(KEY_LAST_NOTIFIED_VERSION_CODE, 0L)
  }

  fun setLastNotifiedVersionCode(context: Context, versionCode: Long) {
    val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit().putLong(KEY_LAST_NOTIFIED_VERSION_CODE, versionCode).apply()
  }

  fun clearLastNotifiedVersionCode(context: Context) {
    val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit().remove(KEY_LAST_NOTIFIED_VERSION_CODE).apply()
  }

  private fun mainActivityPendingIntent(
    context: Context,
    requestCode: Int,
    openScreen: String,
    updateAction: String? = null,
    libraryTab: Int = 0
  ): PendingIntent {
    val intent = Intent(context, MainActivity::class.java).apply {
      flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
      putExtra(EXTRA_OPEN_SCREEN, openScreen)
      if (!updateAction.isNullOrBlank()) {
        putExtra(EXTRA_UPDATE_ACTION, updateAction)
      }
      putExtra(EXTRA_OPEN_LIBRARY_TAB, libraryTab)
    }
    return PendingIntent.getActivity(
      context,
      requestCode,
      intent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
  }

  fun notifyUpdateAvailableIfNeeded(
    context: Context,
    release: RemoteVersionInfo,
    force: Boolean = false
  ): Boolean {
    if (!force && release.versionCode <= getLastNotifiedVersionCode(context)) {
      return false
    }
    ensureChannels(context)
    if (!canPostNotifications(context)) return false

    val title = context.getString(R.string.notif_update_available_title, release.tagName)
    val summaryLine = release.notes.firstOrNull()?.takeIf { it.isNotBlank() }
      ?: context.getString(R.string.notif_update_available_body)
    val expandedNotes = if (release.notes.isNotEmpty()) {
      release.notes.take(4).joinToString("\n") { "• $it" }
    } else {
      summaryLine
    }

    val openIntent = mainActivityPendingIntent(
      context = context,
      requestCode = 4101,
      openScreen = SCREEN_SETTINGS,
      updateAction = ACTION_VIEW_UPDATE
    )
    val downloadIntent = mainActivityPendingIntent(
      context = context,
      requestCode = 4102,
      openScreen = SCREEN_SETTINGS,
      updateAction = ACTION_DOWNLOAD_UPDATE
    )

    val notification = NotificationCompat.Builder(context, CHANNEL_UPDATE_RELEASES)
      .setSmallIcon(R.drawable.ic_stat_aurix)
      .setContentTitle(title)
      .setContentText(summaryLine)
      .setStyle(NotificationCompat.BigTextStyle().bigText(expandedNotes))
      .setContentIntent(openIntent)
      .addAction(0, context.getString(R.string.notif_update_download_action), downloadIntent)
      .setAutoCancel(true)
      .setOnlyAlertOnce(!force)
      .setPriority(NotificationCompat.PRIORITY_DEFAULT)
      .build()

    return runCatching {
      NotificationManagerCompat.from(context).notify(NOTIF_ID_UPDATE_AVAILABLE, notification)
      setLastNotifiedVersionCode(context, release.versionCode)
      true
    }.getOrDefault(false)
  }

  fun notifyUpdateReadyToInstall(context: Context, release: RemoteVersionInfo) {
    ensureChannels(context)
    if (!canPostNotifications(context)) return

    val installIntent = mainActivityPendingIntent(
      context = context,
      requestCode = 4103,
      openScreen = SCREEN_SETTINGS,
      updateAction = ACTION_INSTALL_UPDATE
    )

    val notification = NotificationCompat.Builder(context, CHANNEL_UPDATE_RELEASES)
      .setSmallIcon(R.drawable.ic_stat_aurix)
      .setContentTitle(context.getString(R.string.notif_update_ready_title, release.tagName))
      .setContentText(context.getString(R.string.notif_update_ready_body))
      .setContentIntent(installIntent)
      .setAutoCancel(true)
      .setPriority(NotificationCompat.PRIORITY_DEFAULT)
      .build()

    runCatching {
      NotificationManagerCompat.from(context).notify(NOTIF_ID_UPDATE_STATUS, notification)
    }
  }

  fun notifyUpdateDownloadFailed(context: Context, release: RemoteVersionInfo?, message: String) {
    ensureChannels(context)
    if (!canPostNotifications(context)) return

    val retryIntent = mainActivityPendingIntent(
      context = context,
      requestCode = 4104,
      openScreen = SCREEN_SETTINGS,
      updateAction = ACTION_RETRY_UPDATE
    )

    val label = release?.tagName?.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty()
    val notification = NotificationCompat.Builder(context, CHANNEL_UPDATE_RELEASES)
      .setSmallIcon(R.drawable.ic_stat_aurix)
      .setContentTitle(context.getString(R.string.notif_update_failed_title) + label)
      .setContentText(message)
      .setContentIntent(retryIntent)
      .setAutoCancel(true)
      .setPriority(NotificationCompat.PRIORITY_DEFAULT)
      .build()

    runCatching {
      NotificationManagerCompat.from(context).notify(NOTIF_ID_UPDATE_STATUS, notification)
    }
  }

  fun buildBackgroundDownloadNotification(
    context: Context,
    updateState: UpdateUiState,
    activeTracks: Map<String, TrackDownloadNotificationInfo>
  ): Notification {
    ensureChannels(context)

    val isUpdateDownloading = updateState.stage == UpdateStage.Downloading && !updateState.isPaused
    val isUpdatePaused = updateState.stage == UpdateStage.Downloading && updateState.isPaused
    val isUpdateVerifying = updateState.stage == UpdateStage.Verifying
    val isUpdateActive = isUpdateDownloading || isUpdatePaused || isUpdateVerifying

    if (isUpdateActive) {
      val tag = updateState.latestRelease?.tagName ?: "Update"
      val pct = if (updateState.progress >= 0f) (updateState.progress * 100f).toInt().coerceIn(0, 100) else -1
      val title = when {
        isUpdateVerifying -> context.getString(R.string.notif_verifying_update_title, tag)
        isUpdatePaused -> context.getString(R.string.notif_download_paused_title, if (pct >= 0) "$pct%" else "...")
        else -> context.getString(R.string.notif_downloading_update_title, tag)
      }
      val text = when {
        isUpdateVerifying -> context.getString(R.string.notif_verifying_update_body)
        else -> buildString {
          if (updateState.totalBytes > 0L) {
            append(UpdateManager.formatBytesMb(updateState.downloadedBytes))
            append(" / ")
            append(UpdateManager.formatBytesMb(updateState.totalBytes))
          } else if (updateState.downloadedBytes > 0L) {
            append(UpdateManager.formatBytesMb(updateState.downloadedBytes))
          }
          if (!isUpdatePaused && updateState.speedBytesPerSec > 0L) {
            if (isNotEmpty()) append(" · ")
            append(UpdateManager.formatSpeed(updateState.speedBytesPerSec))
          }
          if (activeTracks.isNotEmpty()) {
            if (isNotEmpty()) append(" · ")
            append("+${activeTracks.size} track(s)")
          }
        }.ifBlank { updateState.statusMessage }
      }

      val openSettingsIntent = mainActivityPendingIntent(
        context = context,
        requestCode = 4110,
        openScreen = SCREEN_SETTINGS
      )

      val builder = NotificationCompat.Builder(context, CHANNEL_BACKGROUND_DOWNLOADS)
        .setSmallIcon(R.drawable.ic_stat_aurix)
        .setContentTitle(title)
        .setContentText(text)
        .setContentIntent(openSettingsIntent)
        .setOngoing(!isUpdatePaused)
        .setOnlyAlertOnce(true)
        .setSilent(true)
        .setPriority(NotificationCompat.PRIORITY_LOW)

      if (isUpdateVerifying) {
        builder.setProgress(0, 0, true)
      } else if (pct >= 0) {
        builder.setProgress(100, pct, false)
      } else {
        builder.setProgress(0, 0, true)
      }

      if (!isUpdateVerifying) {
        val toggleIntent = Intent(context, BackgroundDownloadService::class.java).apply {
          action = if (isUpdatePaused) BackgroundDownloadService.ACTION_RESUME_UPDATE else BackgroundDownloadService.ACTION_PAUSE_UPDATE
        }
        val togglePi = PendingIntent.getService(
          context,
          4111,
          toggleIntent,
          PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val cancelIntent = Intent(context, BackgroundDownloadService::class.java).apply {
          action = BackgroundDownloadService.ACTION_CANCEL_UPDATE
        }
        val cancelPi = PendingIntent.getService(
          context,
          4112,
          cancelIntent,
          PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        builder.addAction(
          0,
          context.getString(if (isUpdatePaused) R.string.notif_action_resume else R.string.notif_action_pause),
          togglePi
        )
        builder.addAction(0, context.getString(R.string.notif_action_cancel), cancelPi)
      }

      return builder.build()
    }

    val tracksList = activeTracks.values.toList()
    val firstTrack = tracksList.firstOrNull()
    val avgFraction = if (tracksList.isEmpty()) 0f else tracksList.map { it.fraction }.average().toFloat()
    val pct = (avgFraction * 100f).toInt().coerceIn(0, 100)

    val title = when {
      tracksList.size > 1 -> context.getString(R.string.notif_download_songs_multi_title, tracksList.size)
      firstTrack != null -> context.getString(R.string.notif_download_song_title, firstTrack.title)
      else -> context.getString(R.string.notif_channel_downloads_name)
    }
    val subText = when {
      tracksList.size > 1 -> tracksList.joinToString(", ") { it.title }.take(80)
      firstTrack != null -> buildString {
        if (firstTrack.artist.isNotBlank()) append(firstTrack.artist)
        if (firstTrack.statusHint.isNotBlank()) {
          if (isNotEmpty()) append(" · ")
          append(firstTrack.statusHint)
        } else if (pct > 0) {
          if (isNotEmpty()) append(" · ")
          append("$pct%")
        }
      }
      else -> ""
    }

    val openLibraryIntent = mainActivityPendingIntent(
      context = context,
      requestCode = 4115,
      openScreen = SCREEN_LIBRARY,
      libraryTab = 0
    )

    return NotificationCompat.Builder(context, CHANNEL_BACKGROUND_DOWNLOADS)
      .setSmallIcon(R.drawable.ic_stat_aurix)
      .setContentTitle(title)
      .setContentText(subText)
      .setContentIntent(openLibraryIntent)
      .setProgress(100, pct, pct <= 0)
      .setOngoing(true)
      .setOnlyAlertOnce(true)
      .setSilent(true)
      .setPriority(NotificationCompat.PRIORITY_LOW)
      .build()
  }

  fun notifySongDownloadComplete(context: Context, songKey: String, title: String, artist: String) {
    ensureChannels(context)
    if (!canPostNotifications(context)) return

    val openLibraryIntent = mainActivityPendingIntent(
      context = context,
      requestCode = 4200 + (songKey.hashCode() and 0x03FF),
      openScreen = SCREEN_LIBRARY,
      libraryTab = 0
    )
    val notifId = 4300 + (songKey.hashCode() and 0x03FF)
    val text = if (artist.isNotBlank()) "$title — $artist" else title

    val notification = NotificationCompat.Builder(context, CHANNEL_BACKGROUND_DOWNLOADS)
      .setSmallIcon(R.drawable.ic_stat_aurix)
      .setContentTitle(context.getString(R.string.notif_download_song_complete_title))
      .setContentText(text)
      .setContentIntent(openLibraryIntent)
      .setAutoCancel(true)
      .setPriority(NotificationCompat.PRIORITY_LOW)
      .build()

    runCatching {
      NotificationManagerCompat.from(context).notify(notifId, notification)
    }
  }

  fun notifySongDownloadFailed(context: Context, songKey: String, title: String, message: String) {
    ensureChannels(context)
    if (!canPostNotifications(context)) return

    val notifId = 4300 + (songKey.hashCode() and 0x03FF)
    val notification = NotificationCompat.Builder(context, CHANNEL_BACKGROUND_DOWNLOADS)
      .setSmallIcon(R.drawable.ic_stat_aurix)
      .setContentTitle(context.getString(R.string.notif_download_song_failed_title))
      .setContentText("$title: $message")
      .setAutoCancel(true)
      .setPriority(NotificationCompat.PRIORITY_LOW)
      .build()

    runCatching {
      NotificationManagerCompat.from(context).notify(notifId, notification)
    }
  }
}
