package com.example.data

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLHandshakeException

data class InstalledAppInfo(
  val packageName: String,
  val versionName: String,
  val versionCode: Long,
  val signingSha256Set: Set<String>
)

data class GitHubRelease(
  val tagName: String,
  val name: String,
  val body: String,
  val apkUrl: String,
  val releaseDate: String = "",
  val versionCode: Long = 0L,
  val isMandatory: Boolean = false
)

sealed class UpdateCheckStatus {
  object Idle : UpdateCheckStatus()
  object Checking : UpdateCheckStatus()
  data class UpdateAvailable(
    val installed: InstalledAppInfo,
    val release: GitHubRelease,
    val isMandatory: Boolean
  ) : UpdateCheckStatus()
  data class UpToDate(
    val installed: InstalledAppInfo,
    val release: GitHubRelease
  ) : UpdateCheckStatus()
  data class DowngradeBlocked(
    val installed: InstalledAppInfo,
    val release: GitHubRelease
  ) : UpdateCheckStatus()
  data class Error(
    val installed: InstalledAppInfo,
    val message: String
  ) : UpdateCheckStatus()
}

sealed class ApkDownloadState {
  object Idle : ApkDownloadState()
  data class Downloading(
    val progress: Float,
    val downloadedBytes: Long,
    val totalBytes: Long
  ) : ApkDownloadState()
  data class ValidatedReady(
    val filePath: String,
    val tagName: String,
    val apkVersionCode: Long
  ) : ApkDownloadState()
  data class Error(val message: String) : ApkDownloadState()
}

data class InstallResult(
  val launched: Boolean,
  val requiresPermission: Boolean = false,
  val message: String
)

sealed class ApkValidationResult {
  data class Valid(val versionCode: Long, val versionName: String) : ApkValidationResult()
  data class Invalid(val reason: String) : ApkValidationResult()
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

  fun getInstalledAppInfo(context: Context): InstalledAppInfo {
    val pm = context.packageManager
    val pkgName = context.packageName
    return try {
      val info = getPackageInfoWithSignatures(pm, pkgName)
      val vCode = if (info != null) PackageInfoCompat.getLongVersionCode(info) else BuildConfig.VERSION_CODE.toLong()
      val vName = info?.versionName?.takeIf { it.isNotBlank() } ?: BuildConfig.VERSION_NAME
      val certs = extractSigningSha256Set(info)
      InstalledAppInfo(
        packageName = pkgName,
        versionName = vName,
        versionCode = vCode,
        signingSha256Set = certs
      )
    } catch (_: Exception) {
      InstalledAppInfo(
        packageName = pkgName,
        versionName = BuildConfig.VERSION_NAME,
        versionCode = BuildConfig.VERSION_CODE.toLong(),
        signingSha256Set = emptySet()
      )
    }
  }

  @Suppress("DEPRECATION")
  private fun getPackageInfoWithSignatures(pm: PackageManager, packageName: String): PackageInfo? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      pm.getPackageInfo(
        packageName,
        PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong())
      )
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
      pm.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
    } else {
      pm.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
    }
  }

  @Suppress("DEPRECATION")
  private fun getArchiveInfoWithSignatures(pm: PackageManager, apkPath: String): PackageInfo? {
    val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
      PackageManager.GET_SIGNING_CERTIFICATES or PackageManager.GET_SIGNATURES
    } else {
      PackageManager.GET_SIGNATURES
    }
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      pm.getPackageArchiveInfo(apkPath, PackageManager.PackageInfoFlags.of(flags.toLong()))
    } else {
      pm.getPackageArchiveInfo(apkPath, flags)
    }
  }

  @Suppress("DEPRECATION")
  private fun extractSigningSha256Set(info: PackageInfo?): Set<String> {
    if (info == null) return emptySet()
    val sigList = mutableListOf<Signature>()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
      val signingInfo = info.signingInfo
      if (signingInfo != null) {
        val contents = if (signingInfo.hasMultipleSigners()) {
          signingInfo.apkContentsSigners
        } else {
          signingInfo.signingCertificateHistory
        }
        if (contents != null) sigList.addAll(contents)
      }
    }
    @Suppress("DEPRECATION")
    val legacySigs = info.signatures
    if (sigList.isEmpty() && legacySigs != null) {
      sigList.addAll(legacySigs)
    }
    val digest = MessageDigest.getInstance("SHA-256")
    return sigList.map { sig ->
      digest.digest(sig.toByteArray()).joinToString("") { "%02x".format(it) }
    }.toSet()
  }

  fun deriveVersionCodeFromTag(tag: String): Long {
    val parts = tag.trim().removePrefix("v").removePrefix("V")
      .substringBefore("-")
      .split(".")
      .mapNotNull { it.toLongOrNull() }
    val major = parts.getOrElse(0) { 0L }
    val minor = parts.getOrElse(1) { 0L }
    val patch = parts.getOrElse(2) { 0L }
    return if (parts.size >= 4) {
      val hotfix = parts.getOrElse(3) { 0L }
      major * 10000L + minor * 100L + patch * 10L + hotfix
    } else {
      major * 10000L + minor * 100L + patch
    }
  }

  private fun parseVersionCodeFromText(text: String): Long? {
    val match = Regex("""versionCode\s*[:=]\s*(\d+)""", RegexOption.IGNORE_CASE).find(text)
    return match?.groupValues?.get(1)?.toLongOrNull()
  }

  private fun parseMandatoryFlag(text: String): Boolean {
    return Regex("""(mandatory\s*[:=]\s*true|critical\s*[:=]\s*true|\[MANDATORY\]|\[CRITICAL\])""", RegexOption.IGNORE_CASE)
      .containsMatchIn(text)
  }

  fun evaluateUpdate(installed: InstalledAppInfo, release: GitHubRelease): UpdateCheckStatus {
    val remoteCode = if (release.versionCode > 0L) release.versionCode else deriveVersionCodeFromTag(release.tagName)
    val resolvedRelease = release.copy(versionCode = remoteCode)

    return when {
      remoteCode > installed.versionCode -> UpdateCheckStatus.UpdateAvailable(
        installed = installed,
        release = resolvedRelease,
        isMandatory = resolvedRelease.isMandatory
      )
      remoteCode == installed.versionCode -> UpdateCheckStatus.UpToDate(
        installed = installed,
        release = resolvedRelease
      )
      else -> UpdateCheckStatus.DowngradeBlocked(
        installed = installed,
        release = resolvedRelease
      )
    }
  }

  fun statusTextFor(status: UpdateCheckStatus): String {
    return when (status) {
      is UpdateCheckStatus.Idle -> "Ready to check for updates"
      is UpdateCheckStatus.Checking -> "Checking GitHub releases..."
      is UpdateCheckStatus.UpdateAvailable -> {
        if (status.isMandatory) {
          "Critical update required: ${status.release.tagName} (code ${status.release.versionCode})"
        } else {
          "Update available: ${status.release.tagName} (code ${status.release.versionCode})"
        }
      }
      is UpdateCheckStatus.UpToDate -> "You are already using the latest version."
      is UpdateCheckStatus.DowngradeBlocked ->
        "Downgrade blocked: ${status.release.tagName} (code ${status.release.versionCode}) is older than installed (code ${status.installed.versionCode})."
      is UpdateCheckStatus.Error -> status.message
    }
  }

  suspend fun checkForUpdates(context: Context, owner: String, repo: String): UpdateCheckStatus = withContext(Dispatchers.IO) {
    val installed = getInstalledAppInfo(context)
    cleanupObsoleteApks(context, installed.versionCode)

    val cleanOwner = owner.trim()
    val cleanRepo = repo.trim()
    if (cleanOwner.isEmpty() || cleanRepo.isEmpty()) {
      return@withContext UpdateCheckStatus.Error(installed, "GitHub owner and repository cannot be empty.")
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
            var metadataJsonUrl = ""
            if (assets != null) {
              for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                val assetName = asset.optString("name", "")
                val downloadUrl = asset.optString("browser_download_url", "")
                if (assetName.endsWith(".apk", ignoreCase = true)) {
                  if (apkUrl.isBlank() || assetName.contains("AURIX", ignoreCase = true)) {
                    apkUrl = downloadUrl
                  }
                } else if (assetName.equals("output-metadata.json", ignoreCase = true) ||
                  assetName.equals("version.json", ignoreCase = true)
                ) {
                  metadataJsonUrl = downloadUrl
                }
              }
            }

            if (tagName.isNotBlank() && apkUrl.isNotBlank()) {
              var remoteCode = parseVersionCodeFromText("$name\n$body") ?: 0L
              var isMandatory = parseMandatoryFlag("$name\n$body")

              if (metadataJsonUrl.isNotBlank()) {
                val meta = fetchRemoteMetadataJson(metadataJsonUrl)
                if (meta != null) {
                  if (meta.first > 0L) remoteCode = meta.first
                  if (meta.second) isMandatory = true
                }
              }
              if (remoteCode <= 0L) {
                remoteCode = deriveVersionCodeFromTag(tagName)
              }

              val release = GitHubRelease(
                tagName = tagName,
                name = name.ifBlank { tagName },
                body = body,
                apkUrl = apkUrl,
                releaseDate = publishedAt,
                versionCode = remoteCode,
                isMandatory = isMandatory
              )
              return@withContext evaluateUpdate(installed, release)
            }
          }
        } else if (response.code == 404) {
          return@withContext UpdateCheckStatus.Error(installed, "No published releases found for $cleanOwner/$cleanRepo.")
        } else if (response.code != 403 && response.code != 429) {
          return@withContext UpdateCheckStatus.Error(installed, "GitHub returned HTTP ${response.code}. Please try again.")
        }
      }
    } catch (e: Exception) {
      if (e is UnknownHostException || e is SocketTimeoutException || e is ConnectException || e is SSLHandshakeException) {
        return@withContext UpdateCheckStatus.Error(installed, mapNetworkError(e))
      }
    }

    // Rate-limit safe GitHub Web fallback
    return@withContext checkViaWebFallback(installed, cleanOwner, cleanRepo)
  }

  private fun fetchRemoteMetadataJson(url: String): Pair<Long, Boolean>? {
    return try {
      val req = Request.Builder().url(url).header("User-Agent", "AURIX-Android-Updater").build()
      apiClient.newCall(req).execute().use { res ->
        if (!res.isSuccessful) return null
        val text = res.body?.string().orEmpty()
        if (text.isBlank()) return null
        val obj = JSONObject(text)
        var code = obj.optLong("versionCode", 0L)
        if (code <= 0L) {
          val elements = obj.optJSONArray("elements")
          if (elements != null && elements.length() > 0) {
            code = elements.getJSONObject(0).optLong("versionCode", 0L)
          }
        }
        val mandatory = obj.optBoolean("mandatory", false) || obj.optBoolean("critical", false)
        code to mandatory
      }
    } catch (_: Exception) {
      null
    }
  }

  private fun checkViaWebFallback(installed: InstalledAppInfo, owner: String, repo: String): UpdateCheckStatus {
    return try {
      val latestReq = Request.Builder()
        .url("https://github.com/$owner/$repo/releases/latest")
        .header("User-Agent", "AURIX-Android-Updater")
        .build()

      var resolvedTag = ""
      var pageHtml = ""
      apiClient.newCall(latestReq).execute().use { res ->
        val finalUrl = res.request.url.toString()
        val tagIdx = finalUrl.indexOf("/releases/tag/")
        if (tagIdx != -1) {
          resolvedTag = finalUrl.substring(tagIdx + "/releases/tag/".length).substringBefore("?").substringBefore("#")
        }
        pageHtml = res.body?.string().orEmpty()
      }

      if (resolvedTag.isBlank()) {
        return UpdateCheckStatus.Error(installed, "No GitHub releases found for $owner/$repo.")
      }

      val assetsReq = Request.Builder()
        .url("https://github.com/$owner/$repo/releases/expanded_assets/$resolvedTag")
        .header("User-Agent", "AURIX-Android-Updater")
        .build()

      var apkUrl = ""
      var metaUrl = ""
      apiClient.newCall(assetsReq).execute().use { res ->
        val html = res.body?.string().orEmpty()
        val apkRegex = Regex("""href="(/[^"]+/releases/download/[^"]+\.apk)"""")
        val matches = apkRegex.findAll(html).map { it.groupValues[1] }.toList()
        val preferred = matches.firstOrNull { it.contains("AURIX", ignoreCase = true) } ?: matches.firstOrNull()
        if (preferred != null) {
          apkUrl = "https://github.com$preferred"
        }

        val metaRegex = Regex("""href="(/[^"]+/releases/download/[^"]+(?:output-metadata|version)\.json)"""")
        val metaMatch = metaRegex.find(html)?.groupValues?.get(1)
        if (metaMatch != null) {
          metaUrl = "https://github.com$metaMatch"
        }
      }

      if (apkUrl.isBlank()) {
        apkUrl = "https://github.com/$owner/$repo/releases/download/$resolvedTag/app-release.apk"
      }

      var remoteCode = parseVersionCodeFromText(pageHtml) ?: 0L
      var isMandatory = parseMandatoryFlag(pageHtml)
      if (metaUrl.isNotBlank()) {
        val meta = fetchRemoteMetadataJson(metaUrl)
        if (meta != null) {
          if (meta.first > 0L) remoteCode = meta.first
          if (meta.second) isMandatory = true
        }
      }
      if (remoteCode <= 0L) {
        remoteCode = deriveVersionCodeFromTag(resolvedTag)
      }

      val release = GitHubRelease(
        tagName = resolvedTag,
        name = "AURIX $resolvedTag",
        body = "Latest release available on GitHub.",
        apkUrl = apkUrl,
        releaseDate = "",
        versionCode = remoteCode,
        isMandatory = isMandatory
      )
      evaluateUpdate(installed, release)
    } catch (e: Exception) {
      UpdateCheckStatus.Error(installed, mapNetworkError(e))
    }
  }

  private fun mapNetworkError(e: Exception): String {
    return when (e) {
      is UnknownHostException -> "No internet connection. Check your network and try again."
      is SocketTimeoutException -> "Connection timed out while contacting GitHub."
      is ConnectException -> "Unable to reach GitHub servers. Please try again later."
      is SSLHandshakeException -> "Secure connection (TLS) failed while checking updates."
      else -> e.localizedMessage ?: "Network error while checking updates."
    }
  }

  private fun getUpdateApkFile(context: Context): File {
    val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.cacheDir
    if (!dir.exists()) dir.mkdirs()
    return File(dir, "aurix_update.apk")
  }

  fun cleanupObsoleteApks(context: Context, installedVersionCode: Long) {
    try {
      val file = getUpdateApkFile(context)
      if (!file.exists()) return
      val archive = getArchiveInfoWithSignatures(context.packageManager, file.absolutePath)
      val apkCode = if (archive != null) PackageInfoCompat.getLongVersionCode(archive) else 0L
      if (archive == null || archive.packageName != context.packageName || apkCode <= installedVersionCode) {
        file.delete()
      }
    } catch (_: Exception) {
      // Ignore cleanup errors
    }
  }

  fun validateApkArchive(context: Context, file: File): ApkValidationResult {
    if (!file.exists() || file.length() < 100_000L) {
      return ApkValidationResult.Invalid("APK file is missing or incomplete.")
    }

    val installed = getInstalledAppInfo(context)
    val pm = context.packageManager
    val archiveInfo = getArchiveInfoWithSignatures(pm, file.absolutePath)
      ?: return ApkValidationResult.Invalid("Downloaded file is not a valid Android APK.")

    if (archiveInfo.packageName != installed.packageName) {
      return ApkValidationResult.Invalid(
        "Security check failed: package mismatch (${archiveInfo.packageName} != ${installed.packageName})."
      )
    }

    val apkVersionCode = PackageInfoCompat.getLongVersionCode(archiveInfo)
    val apkVersionName = archiveInfo.versionName ?: "unknown"

    if (apkVersionCode == installed.versionCode) {
      return ApkValidationResult.Invalid("You are already using the latest version. Reinstall blocked.")
    }
    if (apkVersionCode < installed.versionCode) {
      return ApkValidationResult.Invalid(
        "Downgrade blocked: downloaded APK (code $apkVersionCode) is older than installed (code ${installed.versionCode})."
      )
    }

    // Verify APK signing certificate matches the installed app signature
    val archiveSigners = extractSigningSha256Set(archiveInfo)
    if (installed.signingSha256Set.isNotEmpty()) {
      if (archiveSigners.isEmpty()) {
        return ApkValidationResult.Invalid("Security check failed: APK signature could not be verified.")
      }
      if ((archiveSigners intersect installed.signingSha256Set).isEmpty()) {
        return ApkValidationResult.Invalid("Security check failed: APK signature does not match the installed app.")
      }
    }

    return ApkValidationResult.Valid(versionCode = apkVersionCode, versionName = apkVersionName)
  }

  fun hasValidatedNewerApk(context: Context): ApkDownloadState.ValidatedReady? {
    val file = getUpdateApkFile(context)
    return when (val check = validateApkArchive(context, file)) {
      is ApkValidationResult.Valid -> ApkDownloadState.ValidatedReady(
        filePath = file.absolutePath,
        tagName = "v${check.versionName}",
        apkVersionCode = check.versionCode
      )
      is ApkValidationResult.Invalid -> null
    }
  }

  suspend fun downloadApk(
    context: Context,
    release: GitHubRelease,
    onProgress: (ApkDownloadState) -> Unit
  ): ApkDownloadState = withContext(Dispatchers.IO) {
    val installed = getInstalledAppInfo(context)
    val remoteCode = if (release.versionCode > 0L) release.versionCode else deriveVersionCodeFromTag(release.tagName)
    if (remoteCode == installed.versionCode) {
      val err = ApkDownloadState.Error("You are already using the latest version.")
      onProgress(err)
      return@withContext err
    }
    if (remoteCode < installed.versionCode) {
      val err = ApkDownloadState.Error("Downgrade blocked: release code $remoteCode is older than installed code ${installed.versionCode}.")
      onProgress(err)
      return@withContext err
    }

    val finalFile = getUpdateApkFile(context)
    val partFile = File(finalFile.parentFile, "aurix_update.apk.part")

    try {
      if (finalFile.exists()) finalFile.delete()
      if (partFile.exists()) partFile.delete()

      onProgress(ApkDownloadState.Downloading(0f, 0L, 0L))

      val request = Request.Builder()
        .url(release.apkUrl)
        .header("User-Agent", "AURIX-Android-Updater")
        .build()

      downloadClient.newCall(request).execute().use { response ->
        if (!response.isSuccessful) {
          android.util.Log.w(
            "GitHubUpdater",
            "Download failed with HTTP ${response.code} for ${release.apkUrl}"
          )
          val resId = when {
            response.code == 404 -> com.example.R.string.update_error_404_building
            response.code in 500..599 -> com.example.R.string.update_error_server_5xx
            else -> com.example.R.string.update_error_generic
          }
          val msg = runCatching {
            val lang = com.example.LocaleHelper.getPersistedLanguage(context)
            com.example.LocaleHelper.wrapBaseContext(context, lang).getString(resId)
          }.getOrElse {
            context.getString(resId)
          }
          val err = ApkDownloadState.Error(msg)
          onProgress(err)
          return@withContext err
        }

        val body = response.body ?: run {
          val err = ApkDownloadState.Error("Empty APK download response from server.")
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

        if (totalBytes > 0L && partFile.length() != totalBytes) {
          partFile.delete()
          val err = ApkDownloadState.Error("Download interrupted: incomplete APK (${partFile.length()} of $totalBytes bytes).")
          onProgress(err)
          return@withContext err
        }

        if (!partFile.renameTo(finalFile)) {
          partFile.copyTo(finalFile, overwrite = true)
          partFile.delete()
        }

        when (val validation = validateApkArchive(context, finalFile)) {
          is ApkValidationResult.Valid -> {
            val done = ApkDownloadState.ValidatedReady(
              filePath = finalFile.absolutePath,
              tagName = release.tagName,
              apkVersionCode = validation.versionCode
            )
            onProgress(done)
            done
          }
          is ApkValidationResult.Invalid -> {
            finalFile.delete()
            val err = ApkDownloadState.Error(validation.reason)
            onProgress(err)
            err
          }
        }
      }
    } catch (e: Exception) {
      if (partFile.exists()) partFile.delete()
      if (finalFile.exists()) finalFile.delete()
      val err = ApkDownloadState.Error(mapNetworkError(e))
      onProgress(err)
      err
    }
  }

  fun installDownloadedApk(context: Context): InstallResult {
    val file = getUpdateApkFile(context)
    when (val validation = validateApkArchive(context, file)) {
      is ApkValidationResult.Invalid -> {
        if (file.exists()) file.delete()
        return InstallResult(launched = false, message = validation.reason)
      }
      is ApkValidationResult.Valid -> {
        // Passed package name, versionCode > installedVersionCode, and signature verification
      }
    }

    return try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
        val permIntent = Intent(
          Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
          Uri.parse("package:${context.packageName}")
        ).apply {
          addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(permIntent)
        return InstallResult(
          launched = false,
          requiresPermission = true,
          message = "Allow 'Install unknown apps' for AURIX, then tap Update Now again."
        )
      }

      val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
      val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "application/vnd.android.package-archive")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
      context.startActivity(intent)
      InstallResult(launched = true, message = "Opening Android installer...")
    } catch (_: ActivityNotFoundException) {
      InstallResult(launched = false, message = "No system package installer available on this device.")
    } catch (_: SecurityException) {
      InstallResult(launched = false, message = "Install blocked by system security policy.")
    } catch (e: Exception) {
      InstallResult(launched = false, message = "Installation failed: ${e.localizedMessage ?: "Unknown error"}")
    }
  }
}
