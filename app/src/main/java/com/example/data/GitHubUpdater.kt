package com.example.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

data class GitHubRelease(
  val tagName: String,
  val name: String,
  val body: String,
  val apkUrl: String,
  val releaseDate: String = ""
)

sealed class UpdateCheckResult {
  data class Success(val release: GitHubRelease) : UpdateCheckResult()
  data class Failure(val message: String) : UpdateCheckResult()
}

sealed class ApkDownloadState {
  object Idle : ApkDownloadState()
  data class Downloading(
    val progress: Float,
    val downloadedBytes: Long,
    val totalBytes: Long
  ) : ApkDownloadState()
  data class Downloaded(val filePath: String, val tagName: String) : ApkDownloadState()
  data class Error(val message: String) : ApkDownloadState()
}

object GitHubUpdater {
  private val apiClient = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    .followRedirects(true)
    .build()

  private val downloadClient = OkHttpClient.Builder()
    .connectTimeout(20, TimeUnit.SECONDS)
    .readTimeout(120, TimeUnit.SECONDS)
    .followRedirects(true)
    .followSslRedirects(true)
    .build()

  fun isNewerVersion(remoteTag: String, currentVersion: String): Boolean {
    val remoteParts = remoteTag.trim().removePrefix("v").removePrefix("V").split(".").mapNotNull { it.toIntOrNull() }
    val currentParts = currentVersion.trim().removePrefix("v").removePrefix("V").split(".").mapNotNull { it.toIntOrNull() }
    val maxLen = maxOf(remoteParts.size, currentParts.size)
    for (idx in 0 until maxLen) {
      val r = remoteParts.getOrElse(idx) { 0 }
      val c = currentParts.getOrElse(idx) { 0 }
      if (r > c) return true
      if (r < c) return false
    }
    return false
  }

  suspend fun checkForUpdates(owner: String, repo: String): UpdateCheckResult = withContext(Dispatchers.IO) {
    val cleanOwner = owner.trim()
    val cleanRepo = repo.trim()
    if (cleanOwner.isEmpty() || cleanRepo.isEmpty()) {
      return@withContext UpdateCheckResult.Failure("Owner and repository cannot be empty")
    }

    try {
      val apiUrl = "https://api.github.com/repos/$cleanOwner/$cleanRepo/releases/latest"
      val request = Request.Builder()
        .url(apiUrl)
        .header("User-Agent", "AURIX-Android-Updater")
        .header("Accept", "application/vnd.github+json")
        .build()

      apiClient.newCall(request).execute().use { response ->
        if (response.isSuccessful) {
          val bodyStr = response.body?.string().orEmpty()
          if (bodyStr.isNotBlank()) {
            val json = JSONObject(bodyStr)
            val tagName = json.optString("tag_name", "").trim()
            val name = json.optString("name", tagName).trim()
            val body = json.optString("body", "").trim()
            val publishedAt = json.optString("published_at", "").take(10)
            val assets = json.optJSONArray("assets")
            var apkUrl = ""
            if (assets != null) {
              for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                val assetName = asset.optString("name", "")
                if (assetName.endsWith(".apk", ignoreCase = true)) {
                  apkUrl = asset.optString("browser_download_url", "")
                  if (assetName.contains("AURIX", ignoreCase = true)) break
                }
              }
            }
            if (tagName.isNotBlank() && apkUrl.isNotBlank()) {
              return@withContext UpdateCheckResult.Success(
                GitHubRelease(
                  tagName = tagName,
                  name = name.ifBlank { tagName },
                  body = body,
                  apkUrl = apkUrl,
                  releaseDate = publishedAt
                )
              )
            }
          }
        }
      }
    } catch (_: Exception) {
      // Fall through to GitHub web fallback
    }

    // Rate-limit safe GitHub Web fallback
    return@withContext checkViaWebFallback(cleanOwner, cleanRepo)
  }

  private fun checkViaWebFallback(owner: String, repo: String): UpdateCheckResult {
    return try {
      val latestReq = Request.Builder()
        .url("https://github.com/$owner/$repo/releases/latest")
        .header("User-Agent", "AURIX-Android-Updater")
        .build()

      var resolvedTag = ""
      apiClient.newCall(latestReq).execute().use { res ->
        val finalUrl = res.request.url.toString()
        val tagIdx = finalUrl.indexOf("/releases/tag/")
        if (tagIdx != -1) {
          resolvedTag = finalUrl.substring(tagIdx + "/releases/tag/".length).substringBefore("?").substringBefore("#")
        }
      }

      if (resolvedTag.isBlank()) {
        return UpdateCheckResult.Failure("No GitHub releases found for $owner/$repo")
      }

      val assetsReq = Request.Builder()
        .url("https://github.com/$owner/$repo/releases/expanded_assets/$resolvedTag")
        .header("User-Agent", "AURIX-Android-Updater")
        .build()

      var apkUrl = ""
      apiClient.newCall(assetsReq).execute().use { res ->
        val html = res.body?.string().orEmpty()
        val regex = Regex("""href="(/[^"]+/releases/download/[^"]+\.apk)"""")
        val match = regex.findAll(html).map { it.groupValues[1] }.toList()
        val preferred = match.firstOrNull { it.contains("AURIX", ignoreCase = true) } ?: match.firstOrNull()
        if (preferred != null) {
          apkUrl = "https://github.com$preferred"
        }
      }

      if (apkUrl.isBlank()) {
        apkUrl = "https://github.com/$owner/$repo/releases/download/$resolvedTag/app-release.apk"
      }

      UpdateCheckResult.Success(
        GitHubRelease(
          tagName = resolvedTag,
          name = "AURIX $resolvedTag",
          body = "Latest release available on GitHub.",
          apkUrl = apkUrl,
          releaseDate = ""
        )
      )
    } catch (e: Exception) {
      UpdateCheckResult.Failure(e.localizedMessage ?: "Network error while checking releases")
    }
  }

  private fun getUpdateApkFile(context: Context): File {
    val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.cacheDir
    if (!dir.exists()) dir.mkdirs()
    return File(dir, "aurix_update.apk")
  }

  fun hasDownloadedApk(context: Context): Boolean {
    val file = getUpdateApkFile(context)
    return file.exists() && file.length() > 100_000L
  }

  suspend fun downloadApk(
    context: Context,
    apkUrl: String,
    tagName: String,
    onProgress: (ApkDownloadState) -> Unit
  ): ApkDownloadState = withContext(Dispatchers.IO) {
    val finalFile = getUpdateApkFile(context)
    val partFile = File(finalFile.parentFile, "aurix_update.apk.part")

    try {
      if (finalFile.exists()) finalFile.delete()
      if (partFile.exists()) partFile.delete()

      onProgress(ApkDownloadState.Downloading(0f, 0L, 0L))

      val request = Request.Builder()
        .url(apkUrl)
        .header("User-Agent", "AURIX-Android-Updater")
        .build()

      downloadClient.newCall(request).execute().use { response ->
        if (!response.isSuccessful) {
          val err = ApkDownloadState.Error("Download failed (HTTP ${response.code})")
          onProgress(err)
          return@withContext err
        }

        val body = response.body ?: run {
          val err = ApkDownloadState.Error("Empty APK download response")
          onProgress(err)
          return@withContext err
        }

        val totalBytes = body.contentLength()
        var downloadedBytes = 0L
        var lastEmitTime = 0L

        body.byteStream().use { input ->
          FileOutputStream(partFile).use { output ->
            val buffer = ByteArray(16 * 1024)
            while (true) {
              val read = input.read(buffer)
              if (read == -1) break
              output.write(buffer, 0, read)
              downloadedBytes += read

              val now = System.currentTimeMillis()
              if (now - lastEmitTime >= 120L) {
                lastEmitTime = now
                val progress = if (totalBytes > 0L) (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else -1f
                onProgress(ApkDownloadState.Downloading(progress, downloadedBytes, totalBytes))
              }
            }
            output.flush()
          }
        }

        if (partFile.length() < 100_000L) {
          partFile.delete()
          val err = ApkDownloadState.Error("Downloaded file was incomplete or invalid")
          onProgress(err)
          return@withContext err
        }

        if (!partFile.renameTo(finalFile)) {
          partFile.copyTo(finalFile, overwrite = true)
          partFile.delete()
        }

        val done = ApkDownloadState.Downloaded(finalFile.absolutePath, tagName)
        onProgress(done)
        done
      }
    } catch (e: Exception) {
      if (partFile.exists()) partFile.delete()
      val err = ApkDownloadState.Error(e.localizedMessage ?: "Failed to download APK")
      onProgress(err)
      err
    }
  }

  fun installDownloadedApk(context: Context): String {
    return try {
      val file = getUpdateApkFile(context)
      if (!file.exists() || file.length() < 100_000L) {
        return "APK not downloaded yet. Tap Download APK first."
      }

      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
        val permIntent = Intent(
          Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
          Uri.parse("package:${context.packageName}")
        ).apply {
          addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(permIntent)
        return "Enable 'Install unknown apps' for AURIX, then tap Install Update."
      }

      val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
      val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "application/vnd.android.package-archive")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
      context.startActivity(intent)
      "Opening Android installer..."
    } catch (e: Exception) {
      "Install error: ${e.localizedMessage ?: "Unable to launch installer"}"
    }
  }
}
