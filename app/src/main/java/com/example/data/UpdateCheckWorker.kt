package com.example.data

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.flow.firstOrNull
import java.util.concurrent.TimeUnit

class UpdateCheckWorker(
  appContext: Context,
  params: WorkerParameters
) : CoroutineWorker(appContext, params) {

  override suspend fun doWork(): Result {
    return try {
      val prefs = PreferencesManager(applicationContext)
      val owner = prefs.githubOwner.firstOrNull()?.trim()?.ifBlank { "MEYYYNASH" } ?: "MEYYYNASH"
      val repo = prefs.githubRepo.firstOrNull()?.trim()?.ifBlank { "AURIX" } ?: "AURIX"
      val installed = GitHubUpdater.getInstalledAppInfo(applicationContext)

      val outcome = UpdateManager.fetchLatestReleaseDirect(
        context = applicationContext,
        installed = installed,
        owner = owner,
        repo = repo
      )

      outcome.onSuccess { remote ->
        if (UpdateManager.compareVersions(installed.versionCode, remote) is VersionComparisonOutcome.UpdateAvailable) {
          AppNotificationHelper.notifyUpdateAvailableIfNeeded(applicationContext, remote, force = false)
        }
      }

      if (outcome.isSuccess) Result.success() else Result.retry()
    } catch (_: Exception) {
      Result.retry()
    }
  }
}

object UpdateCheckScheduler {
  private const val UNIQUE_PERIODIC_UPDATE_WORK = "aurix_periodic_update_check"

  fun schedulePeriodicChecks(context: Context) {
    val appContext = context.applicationContext
    runCatching {
      val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

      val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(6, TimeUnit.HOURS)
        .setConstraints(constraints)
        .build()

      WorkManager.getInstance(appContext).enqueueUniquePeriodicWork(
        UNIQUE_PERIODIC_UPDATE_WORK,
        ExistingPeriodicWorkPolicy.KEEP,
        request
      )
    }
  }
}
