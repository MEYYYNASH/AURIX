package com.example.data

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File

data class GitHubRelease(
  val tagName: String,
  val name: String,
  val body: String,
  val apkUrl: String
)

object GitHubUpdater {
  private val client = OkHttpClient()

  suspend fun checkForUpdates(owner: String, repo: String): GitHubRelease? = withContext(Dispatchers.IO) {
    try {
      val url = "https://api.github.com/repos/$owner/$repo/releases/latest"
      val request = Request.Builder().url(url).header("Accept", "application/vnd.github+json").build()
      val response = client.newCall(request).execute()
      if (!response.isSuccessful) return@withContext null

      val bodyStr = response.body?.string() ?: return@withContext null
      val json = JSONObject(bodyStr)
      val tagName = json.getString("tag_name")
      val name = json.optString("name", tagName)
      val body = json.optString("body", "")

      val assets = json.getJSONArray("assets")
      var apkUrl = ""
      for (i in 0 until assets.length()) {
        val asset = assets.getJSONObject(i)
        val assetName = asset.getString("name")
        if (assetName.endsWith(".apk")) {
          apkUrl = asset.getString("browser_download_url")
          break
        }
      }

      if (apkUrl.isBlank()) return@withContext null

      GitHubRelease(tagName = tagName, name = name, body = body, apkUrl = apkUrl)
    } catch (e: Exception) {
      null
    }
  }

  fun downloadAndInstall(context: Context, apkUrl: String, versionName: String) {
    try {
      val request = DownloadManager.Request(Uri.parse(apkUrl))
        .setTitle("Downloading AURIX $versionName")
        .setDescription("Updating app to latest GitHub release...")
        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, "aurix_update.apk")

      val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
      manager.enqueue(request)
    } catch (e: Exception) {
      // fallback or toast
    }
  }

  fun installDownloadedApk(context: Context) {
    try {
      val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "aurix_update.apk")
      if (!file.exists()) return

      val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
      val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "application/vnd.android.package-archive")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
      context.startActivity(intent)
    } catch (e: Exception) {
      e.printStackTrace()
    }
  }
}
