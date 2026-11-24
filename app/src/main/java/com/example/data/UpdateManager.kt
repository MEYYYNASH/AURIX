package com.example.data

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.Settings
import androidx.core.content.FileProvider
import com.example.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLHandshakeException
import kotlin.coroutines.coroutineContext

/**
 * Editable release manifest endpoint for AURIX OTA updates.
 * Expected JSON format:
 * {
 *   "versionName": "1.8.3",
 *   "versionCode": 10803,
 *   "releaseDate": "2026-11-18",
 *   "apkUrl": "https://github.com/MEYYYNASH/AURIX/releases/download/v1.8.3/AURIX-v1.8.3.apk",
 *   "sha256": "...",
 *   "notes": ["..."],
 *   "releasePageUrl": "https://github.com/MEYYYNASH/AURIX/releases/tag/v1.8.3"
 * }
 */
const val VERSION_JSON_URL = "https://raw.githubusercontent.com/MEYYYNASH/AURIX/main/version.json"

const val DEFAULT_RELEASE_PAGE_URL = "https://github.com/MEYYYNASH/AURIX/releases"
const val APP_RELEASE_DATE = BuildConfig.RELEASE_DATE

data class RemoteVersionInfo(
  val versionName: String,
  val versionCode: Long,
  val releaseDate: String,
  val apkUrl: String,
  val sha256: String,
  val notes: List<String>,
  val releasePageUrl: String,
  val isMandatory: Boolean = false
) {
  val tagName: String
    get() = if (versionName.startsWith("v", ignoreCase = true)) versionName else "v$versionName"

  val formattedNotes: String
    get() = if (notes.isEmpty()) "" else notes.joinToString("\n") { "• $it" }
}

enum class UpdateStage {
  Idle,
  Checking,
  UpToDate,
  Available,
  Downloading,
  Verifying,
  Installing,
  Done,
  Error
}

enum class FailedOperation {
  None,
  Check,
  Download,
  Install
}

enum class UpdateMainAction {
  None,
  DownloadAndInstall,
  ResumeDownload,
  InstallVerifiedApk,
  OpenInstallSettings,
  RestartApp,
  Retry
}

data class UpdateUiState(
  val stage: UpdateStage = UpdateStage.Idle,
  val installed: InstalledAppInfo,
  val installedReleaseDate: String = APP_RELEASE_DATE,
  val latestRelease: RemoteVersionInfo? = null,
  val subtitleMessage: String = "Over-the-air updates & release notes",
  val statusMessage: String = "Ready to check for updates",
  val progress: Float = 0f,
  val downloadedBytes: Long = 0L,
  val totalBytes: Long = 0L,
  val speedBytesPerSec: Long = 0L,
  val isPaused: Boolean = false,
  val canResumePartial: Boolean = false,
  val isValidatedReady: Boolean = false,
  val validatedApkPath: String? = null,
  val errorMessage: String? = null,
  val requiresUnknownAppsPermission: Boolean = false,
  val lastFailedOperation: FailedOperation = FailedOperation.None
) {
  val isBusy: Boolean
    get() = stage == UpdateStage.Checking ||
      (stage == UpdateStage.Downloading && !isPaused) ||
      stage == UpdateStage.Verifying ||
      stage == UpdateStage.Installing

  val mainAction: UpdateMainAction
    get() = when {
      stage == UpdateStage.Done -> UpdateMainAction.RestartApp
      stage == UpdateStage.Error -> UpdateMainAction.Retry
      requiresUnknownAppsPermission -> UpdateMainAction.OpenInstallSettings
      stage == UpdateStage.Available && isValidatedReady -> UpdateMainAction.DownloadAndInstall
      stage == UpdateStage.Available && (isPaused || canResumePartial) -> UpdateMainAction.ResumeDownload
      stage == UpdateStage.Available -> UpdateMainAction.DownloadAndInstall
      stage == UpdateStage.Downloading && isPaused -> UpdateMainAction.ResumeDownload
      else -> UpdateMainAction.None
    }
}

sealed class VersionComparisonOutcome {
  data class UpdateAvailable(val release: RemoteVersionInfo) : VersionComparisonOutcome()
  data class UpToDate(val release: RemoteVersionInfo) : VersionComparisonOutcome()
  data class NewerInstalled(val release: RemoteVersionInfo) : VersionComparisonOutcome()
}

class UpdateManager(
  private val context: Context,
  private val scope: CoroutineScope,
  private val versionJsonUrl: String = VERSION_JSON_URL
) {
  companion object {
    private const val MIN_EXTRA_STORAGE_BYTES = 24L * 1024L * 1024L
    private const val FALLBACK_REQUIRED_BYTES = 75L * 1024L * 1024L
    private const val APK_FILE_NAME = "aurix_update.apk"
    private const val PART_FILE_NAME = "aurix_update.apk.part"
    private const val META_FILE_NAME = "aurix_update.apk.meta.json"

    val CURRENT_RELEASE_NOTES = listOf(
      "Unified Frameless Library Redesign (v1.9.6): Added 3-column frameless albums row above tracks, clean frameless track rows with 50dp rounded covers and circular action buttons, 3-column Albums grid, and inline Orbi 'Syncing your library...' skeleton shimmer during refresh and audio import",
      "Smooth Playlist Select Mode Open/Close & Tap Motion (v1.9.5): Added 420ms ease-out selection bar expansion, 600ms spring-overshoot selection circle roll-in (-120° to 0°), 46dp card glide with 35ms/20ms visible-row stagger, 300ms Select/Done button slide, #43C7F4 spring pop + 250ms white checkmark stroke draw, 2.5dp accent card outline, and 380ms delayed close clear",
      "Mini Inline Orbi Loading & Compact Sketch Skeletons: Scaled StatusRow / OrbiStatusRow down to a sleek 34dp mini planet with a glowing orbiting bead, 15sp bold shimmer text, 3dp bouncing dots, and compact 46dp frameless sketch track skeletons",
      "App Icon Picker & 5 Orbi Launcher Icons: Switch between Classic, Midnight, Ember, AMOLED, and Pearl Orbi adaptive launcher icons in Settings > Appearance > App icon with Android 13+ monochrome themed icon layers, 512x512 Play Store PNG exports, confirmation bottom sheet, and Happy Orbi celebration"
    )

    fun parseVersionJson(rawJson: String): RemoteVersionInfo {
      val obj = JSONObject(rawJson.trim())
      val versionName = obj.optString("versionName", "")
        .trim()
        .removePrefix("v")
        .removePrefix("V")
      if (versionName.isBlank()) {
        throw IllegalArgumentException("version.json missing versionName")
      }
      val explicitCode = obj.optLong("versionCode", 0L)
      val versionCode = if (explicitCode > 0L) {
        explicitCode
      } else {
        GitHubUpdater.deriveVersionCodeFromTag(versionName)
      }
      val releaseDate = obj.optString("releaseDate", "").trim()
      val apkUrl = obj.optString("apkUrl", "").trim()
      val sha256 = obj.optString("sha256", "").trim().lowercase(Locale.ROOT)
      val releasePageUrl = obj.optString("releasePageUrl", DEFAULT_RELEASE_PAGE_URL)
        .trim()
        .ifBlank { DEFAULT_RELEASE_PAGE_URL }
      val isMandatory = obj.optBoolean("mandatory", false) || obj.optBoolean("critical", false)

      val notes = mutableListOf<String>()
      val notesArr = obj.optJSONArray("notes")
      if (notesArr != null) {
        for (i in 0 until notesArr.length()) {
          val item = notesArr.optString(i, "").trim()
          if (item.isNotBlank()) notes.add(item)
        }
      } else {
        val bodyStr = obj.optString("body", "").trim()
        if (bodyStr.isNotBlank()) {
          bodyStr.lines()
            .map { it.trim().removePrefix("- ").removePrefix("* ").removePrefix("• ").trim() }
            .filter { it.isNotBlank() }
            .forEach { notes.add(it) }
        }
      }

      return RemoteVersionInfo(
        versionName = versionName,
        versionCode = versionCode,
        releaseDate = releaseDate,
        apkUrl = apkUrl,
        sha256 = sha256,
        notes = notes,
        releasePageUrl = releasePageUrl,
        isMandatory = isMandatory
      )
    }

    fun compareVersions(
      installedVersionCode: Long,
      remote: RemoteVersionInfo
    ): VersionComparisonOutcome {
      return when {
        remote.versionCode > installedVersionCode -> VersionComparisonOutcome.UpdateAvailable(remote)
        remote.versionCode == installedVersionCode -> VersionComparisonOutcome.UpToDate(remote)
        else -> VersionComparisonOutcome.NewerInstalled(remote)
      }
    }

    fun computeSha256Hex(file: File): String {
      val digest = MessageDigest.getInstance("SHA-256")
      FileInputStream(file).use { input ->
        val buffer = ByteArray(32 * 1024)
        while (true) {
          val read = input.read(buffer)
          if (read == -1) break
          digest.update(buffer, 0, read)
        }
      }
      return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun hasEnoughStorageBytes(availableBytes: Long, remainingDownloadBytes: Long): Boolean {
      val needed = if (remainingDownloadBytes > 0L) {
        remainingDownloadBytes + MIN_EXTRA_STORAGE_BYTES
      } else {
        FALLBACK_REQUIRED_BYTES
      }
      return availableBytes >= needed
    }

    fun formatSpeed(bytesPerSec: Long): String {
      if (bytesPerSec <= 0L) return "0 KB/s"
      val kb = bytesPerSec / 1024.0
      return if (kb >= 1024.0) {
        String.format(Locale.US, "%.1f MB/s", kb / 1024.0)
      } else {
        String.format(Locale.US, "%.0f KB/s", kb)
      }
    }

    fun formatBytesMb(bytes: Long): String {
      if (bytes <= 0L) return "0.0 MB"
      return String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
    }

    fun parseContentRangeTotal(header: String?): Long? {
      if (header.isNullOrBlank()) return null
      val slashIdx = header.lastIndexOf('/')
      if (slashIdx == -1 || slashIdx >= header.lastIndex) return null
      val totalStr = header.substring(slashIdx + 1).trim()
      if (totalStr == "*") return null
      return totalStr.toLongOrNull()
    }

    private val sharedApiClient = OkHttpClient.Builder()
      .connectTimeout(15, TimeUnit.SECONDS)
      .readTimeout(25, TimeUnit.SECONDS)
      .followRedirects(true)
      .build()

    suspend fun fetchLatestReleaseDirect(
      context: Context,
      installed: InstalledAppInfo,
      owner: String,
      repo: String,
      versionJsonUrl: String = VERSION_JSON_URL
    ): Result<RemoteVersionInfo> = withContext(Dispatchers.IO) {
      try {
        val req = Request.Builder()
          .url(versionJsonUrl)
          .header("User-Agent", "AURIX-Android-Updater")
          .header("Cache-Control", "no-cache")
          .build()

        sharedApiClient.newCall(req).execute().use { response ->
          if (response.isSuccessful) {
            val body = response.body?.string().orEmpty()
            if (body.isNotBlank()) {
              return@withContext Result.success(parseVersionJson(body))
            }
          }
          if (response.code == 404) {
            return@withContext fallbackToGitHubReleasesDirect(context, owner, repo)
          }
          Result.failure(IOException("Update server returned HTTP ${response.code}"))
        }
      } catch (e: Exception) {
        if (e is UnknownHostException || e is SocketTimeoutException || e is ConnectException || e is SSLHandshakeException) {
          Result.failure(e)
        } else {
          fallbackToGitHubReleasesDirect(context, owner, repo)
        }
      }
    }

    private suspend fun fallbackToGitHubReleasesDirect(
      context: Context,
      owner: String,
      repo: String
    ): Result<RemoteVersionInfo> {
      return when (val gh = GitHubUpdater.checkForUpdates(context, owner, repo)) {
        is UpdateCheckStatus.UpdateAvailable -> Result.success(gh.release.toRemoteVersionInfoDirect(owner, repo))
        is UpdateCheckStatus.UpToDate -> Result.success(gh.release.toRemoteVersionInfoDirect(owner, repo))
        is UpdateCheckStatus.DowngradeBlocked -> Result.success(gh.release.toRemoteVersionInfoDirect(owner, repo))
        is UpdateCheckStatus.Error -> Result.failure(IOException(gh.message))
        else -> Result.failure(IOException("Unable to fetch release metadata"))
      }
    }

    private fun GitHubRelease.toRemoteVersionInfoDirect(owner: String, repo: String): RemoteVersionInfo {
      val parsedNotes = body.lines()
        .map { it.trim().removePrefix("- ").removePrefix("* ").removePrefix("• ").trim() }
        .filter {
          it.isNotBlank() &&
            !it.startsWith("versionCode:", ignoreCase = true) &&
            !it.startsWith("mandatory:", ignoreCase = true)
        }
      return RemoteVersionInfo(
        versionName = tagName.removePrefix("v").removePrefix("V"),
        versionCode = if (versionCode > 0L) versionCode else GitHubUpdater.deriveVersionCodeFromTag(tagName),
        releaseDate = releaseDate.ifBlank { APP_RELEASE_DATE },
        apkUrl = apkUrl,
        sha256 = "",
        notes = parsedNotes,
        releasePageUrl = "https://github.com/$owner/$repo/releases/tag/$tagName",
        isMandatory = isMandatory
      )
    }
  }

  private val downloadClient = OkHttpClient.Builder()
    .connectTimeout(20, TimeUnit.SECONDS)
    .readTimeout(90, TimeUnit.SECONDS)
    .followRedirects(true)
    .followSslRedirects(true)
    .build()

  private val _uiState = MutableStateFlow(
    UpdateUiState(installed = GitHubUpdater.getInstalledAppInfo(context))
  )
  val uiState: StateFlow<UpdateUiState> = _uiState.asStateFlow()

  private var checkJob: Job? = null
  private var downloadJob: Job? = null

  @Volatile
  private var activeCall: Call? = null

  @Volatile
  private var pauseRequested: Boolean = false

  @Volatile
  private var cancelRequested: Boolean = false

  @Volatile
  private var awaitingPermissionReturn: Boolean = false

  @Volatile
  private var awaitingInstallReturn: Boolean = false

  @Volatile
  private var pendingTargetVersionCode: Long = 0L

  init {
    scope.launch(Dispatchers.IO) {
      runCatching { restoreInterruptedOrValidatedState() }
    }
  }

  private fun getUpdateDir(): File {
    val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.cacheDir
    if (!dir.exists()) dir.mkdirs()
    return dir
  }

  private fun getFinalApkFile(): File = File(getUpdateDir(), APK_FILE_NAME)
  private fun getPartApkFile(): File = File(getUpdateDir(), PART_FILE_NAME)
  private fun getMetaFile(): File = File(getUpdateDir(), META_FILE_NAME)

  private fun saveDownloadMeta(release: RemoteVersionInfo, totalBytes: Long, downloadedBytes: Long) {
    try {
      val json = JSONObject().apply {
        put("versionName", release.versionName)
        put("versionCode", release.versionCode)
        put("releaseDate", release.releaseDate)
        put("apkUrl", release.apkUrl)
        put("sha256", release.sha256)
        put("releasePageUrl", release.releasePageUrl)
        put("isMandatory", release.isMandatory)
        put("totalBytes", totalBytes)
        put("downloadedBytes", downloadedBytes)
        put("notes", JSONArray(release.notes))
      }
      getMetaFile().writeText(json.toString())
    } catch (_: Exception) {
      // Best-effort sidecar persistence
    }
  }

  private data class PersistedDownloadMeta(
    val release: RemoteVersionInfo,
    val totalBytes: Long
  )

  private fun loadDownloadMeta(): PersistedDownloadMeta? {
    return try {
      val file = getMetaFile()
      if (!file.exists()) return null
      val raw = file.readText()
      if (raw.isBlank()) return null
      val release = parseVersionJson(raw)
      val totalBytes = JSONObject(raw).optLong("totalBytes", 0L)
      PersistedDownloadMeta(release = release, totalBytes = totalBytes)
    } catch (_: Exception) {
      null
    }
  }

  private fun clearPartialFiles(keepFinalApk: Boolean = false) {
    try {
      getPartApkFile().takeIf { it.exists() }?.delete()
      getMetaFile().takeIf { it.exists() }?.delete()
      if (!keepFinalApk) {
        getFinalApkFile().takeIf { it.exists() }?.delete()
      }
    } catch (_: Exception) {
    }
  }

  private fun restoreInterruptedOrValidatedState() {
    val installed = GitHubUpdater.getInstalledAppInfo(context)
    GitHubUpdater.cleanupObsoleteApks(context, installed.versionCode)

    val validated = GitHubUpdater.hasValidatedNewerApk(context)
    val persisted = loadDownloadMeta()
    val partFile = getPartApkFile()

    if (persisted != null && persisted.release.versionCode <= installed.versionCode) {
      clearPartialFiles(keepFinalApk = false)
    } else if (validated != null && (persisted == null || validated.apkVersionCode >= persisted.release.versionCode)) {
      val release = persisted?.release ?: RemoteVersionInfo(
        versionName = validated.tagName.removePrefix("v"),
        versionCode = validated.apkVersionCode,
        releaseDate = "",
        apkUrl = "",
        sha256 = "",
        notes = emptyList(),
        releasePageUrl = DEFAULT_RELEASE_PAGE_URL
      )
      _uiState.update {
        it.copy(
          stage = UpdateStage.Available,
          installed = installed,
          latestRelease = release,
          subtitleMessage = "Update ${release.tagName} verified & ready to install",
          statusMessage = "Update ready (${release.tagName})",
          progress = 1f,
          isValidatedReady = true,
          validatedApkPath = validated.filePath,
          canResumePartial = false,
          isPaused = false
        )
      }
      return
    } else if (persisted != null && partFile.exists() && partFile.length() > 0L) {
      val partBytes = partFile.length()
      val totalBytes = persisted.totalBytes
      val frac = if (totalBytes > 0L) (partBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 0.99f) else 0f
      val pct = (frac * 100).toInt()
      _uiState.update {
        it.copy(
          stage = UpdateStage.Available,
          installed = installed,
          latestRelease = persisted.release,
          subtitleMessage = "Paused at $pct% (${formatBytesMb(partBytes)}) — tap Resume Download",
          statusMessage = "Download paused at $pct%",
          progress = frac,
          downloadedBytes = partBytes,
          totalBytes = totalBytes,
          isPaused = true,
          canResumePartial = true
        )
      }
      return
    }

    _uiState.update {
      it.copy(installed = installed)
    }
  }

  @Suppress("DEPRECATION")
  private fun isNetworkAvailable(): Boolean {
    return runCatching {
      val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        ?: return true
      val network = cm.activeNetwork
      if (network != null) {
        val caps = cm.getNetworkCapabilities(network)
        if (caps != null) {
          return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }
      }
      cm.activeNetworkInfo?.isConnected == true
    }.getOrDefault(true)
  }

  fun checkForUpdates(
    githubOwner: String = "MEYYYNASH",
    githubRepo: String = "AURIX",
    silent: Boolean = false
  ) {
    val cur = _uiState.value
    if (cur.stage == UpdateStage.Checking || (cur.stage == UpdateStage.Downloading && !cur.isPaused)) return

    checkJob?.cancel()
    checkJob = scope.launch {
      val installed = withContext(Dispatchers.IO) { GitHubUpdater.getInstalledAppInfo(context) }

      if (!silent || cur.stage == UpdateStage.Idle || cur.stage == UpdateStage.Error) {
        _uiState.update {
          it.copy(
            stage = UpdateStage.Checking,
            installed = installed,
            subtitleMessage = "Contacting release server…",
            statusMessage = "Checking for updates…",
            errorMessage = null,
            lastFailedOperation = FailedOperation.None
          )
        }
      }

      if (!isNetworkAvailable()) {
        if (!silent) {
          _uiState.update {
            it.copy(
              stage = UpdateStage.Error,
              subtitleMessage = "No internet connection — connect and retry",
              statusMessage = "Offline — unable to check updates",
              errorMessage = "You appear to be offline. Check your Wi-Fi or mobile data and tap Retry.",
              lastFailedOperation = FailedOperation.Check
            )
          }
        } else {
          _uiState.update { it.copy(stage = UpdateStage.Idle, installed = installed) }
        }
        return@launch
      }

      val outcome = withContext(Dispatchers.IO) {
        fetchRemoteRelease(installed, githubOwner, githubRepo)
      }

      outcome.onSuccess { remote ->
        when (val cmp = compareVersions(installed.versionCode, remote)) {
          is VersionComparisonOutcome.UpdateAvailable -> {
            val partFile = getPartApkFile()
            val meta = loadDownloadMeta()
            val hasResumablePart = meta != null &&
              meta.release.versionCode == remote.versionCode &&
              meta.release.apkUrl == remote.apkUrl &&
              partFile.exists() &&
              partFile.length() > 0L

            val validated = GitHubUpdater.hasValidatedNewerApk(context)
            val isSameValidated = validated != null &&
              validated.apkVersionCode == remote.versionCode &&
              (remote.sha256.isBlank() || runCatching {
                computeSha256Hex(File(validated.filePath)).equals(remote.sha256, ignoreCase = true)
              }.getOrDefault(false))

            if (!hasResumablePart && meta != null && meta.release.versionCode != remote.versionCode) {
              clearPartialFiles(keepFinalApk = false)
            }

            val partLen = if (hasResumablePart) partFile.length() else 0L
            val totalLen = if (hasResumablePart) (meta?.totalBytes ?: 0L) else 0L
            val partFrac = if (hasResumablePart && totalLen > 0L) {
              (partLen.toFloat() / totalLen.toFloat()).coerceIn(0f, 0.99f)
            } else if (isSameValidated) 1f else 0f

            _uiState.update {
              it.copy(
                stage = UpdateStage.Available,
                installed = installed,
                latestRelease = remote,
                subtitleMessage = when {
                  isSameValidated -> "AURIX ${remote.tagName} is verified and ready to install"
                  hasResumablePart -> "Download paused at ${(partFrac * 100).toInt()}% — tap Resume Download"
                  else -> "AURIX ${remote.tagName} is ready to download"
                },
                statusMessage = "Update available: ${remote.tagName} (code ${remote.versionCode})",
                progress = partFrac,
                downloadedBytes = partLen,
                totalBytes = totalLen,
                speedBytesPerSec = 0L,
                isPaused = hasResumablePart,
                canResumePartial = hasResumablePart,
                isValidatedReady = isSameValidated,
                validatedApkPath = if (isSameValidated) validated?.filePath else null,
                errorMessage = null,
                lastFailedOperation = FailedOperation.None
              )
            }
            AppNotificationHelper.notifyUpdateAvailableIfNeeded(context, remote, force = false)
          }

          is VersionComparisonOutcome.UpToDate -> {
            clearPartialFiles(keepFinalApk = false)
            _uiState.update {
              it.copy(
                stage = UpdateStage.UpToDate,
                installed = installed,
                latestRelease = remote,
                subtitleMessage = "You're running the latest AURIX release",
                statusMessage = "You are on the latest version (v${installed.versionName})",
                progress = 0f,
                downloadedBytes = 0L,
                totalBytes = 0L,
                speedBytesPerSec = 0L,
                isPaused = false,
                canResumePartial = false,
                isValidatedReady = false,
                validatedApkPath = null,
                errorMessage = null,
                lastFailedOperation = FailedOperation.None
              )
            }
          }

          is VersionComparisonOutcome.NewerInstalled -> {
            clearPartialFiles(keepFinalApk = false)
            _uiState.update {
              it.copy(
                stage = UpdateStage.UpToDate,
                installed = installed,
                latestRelease = remote,
                subtitleMessage = "Installed build (code ${installed.versionCode}) is ahead of release",
                statusMessage = "Up to date (v${installed.versionName})",
                progress = 0f,
                isPaused = false,
                canResumePartial = false,
                isValidatedReady = false,
                errorMessage = null,
                lastFailedOperation = FailedOperation.None
              )
            }
          }
        }
      }.onFailure { err ->
        if (silent) {
          _uiState.update {
            it.copy(
              stage = UpdateStage.Idle,
              installed = installed
            )
          }
        } else {
          val msg = mapErrorMessage(err)
          _uiState.update {
            it.copy(
              stage = UpdateStage.Error,
              installed = installed,
              subtitleMessage = "Couldn't check for updates — tap Retry",
              statusMessage = msg,
              errorMessage = msg,
              lastFailedOperation = FailedOperation.Check
            )
          }
        }
      }
    }
  }

  private suspend fun fetchRemoteRelease(
    installed: InstalledAppInfo,
    owner: String,
    repo: String
  ): Result<RemoteVersionInfo> {
    return fetchLatestReleaseDirect(
      context = context,
      installed = installed,
      owner = owner,
      repo = repo,
      versionJsonUrl = versionJsonUrl
    )
  }

  fun startOrResumeDownload() {
    val state = _uiState.value
    if (state.stage == UpdateStage.Downloading && !state.isPaused) return
    val release = state.latestRelease ?: run {
      checkForUpdates()
      return
    }

    if (release.versionCode <= state.installed.versionCode) {
      _uiState.update {
        it.copy(
          stage = UpdateStage.UpToDate,
          subtitleMessage = "You're already on v${state.installed.versionName}",
          statusMessage = "Already up to date"
        )
      }
      return
    }

    if (state.isValidatedReady) {
      launchSystemInstaller()
      return
    }

    if (release.apkUrl.isBlank()) {
      _uiState.update {
        it.copy(
          stage = UpdateStage.Error,
          subtitleMessage = "Release has no downloadable APK URL",
          statusMessage = "Missing APK download link in version.json",
          errorMessage = "No APK URL provided for ${release.tagName}.",
          lastFailedOperation = FailedOperation.Download
        )
      }
      return
    }

    if (!isNetworkAvailable()) {
      _uiState.update {
        it.copy(
          stage = UpdateStage.Error,
          subtitleMessage = "No internet connection — connect and retry",
          statusMessage = "Offline — cannot download APK",
          errorMessage = "Device is offline. Reconnect and tap Retry.",
          lastFailedOperation = FailedOperation.Download
        )
      }
      return
    }

    downloadJob?.cancel()
    pauseRequested = false
    cancelRequested = false

    BackgroundDownloadService.startSync(context)
    downloadJob = scope.launch {
      downloadAndVerifyInternal(release)
    }
  }

  private suspend fun downloadAndVerifyInternal(release: RemoteVersionInfo) {
    val finalFile = getFinalApkFile()
    val partFile = getPartApkFile()
    val downloadDir = getUpdateDir()

    val existingMeta = loadDownloadMeta()
    val canAppend = existingMeta != null &&
      existingMeta.release.versionCode == release.versionCode &&
      existingMeta.release.apkUrl == release.apkUrl &&
      partFile.exists() &&
      partFile.length() > 0L

    if (!canAppend) {
      if (partFile.exists()) partFile.delete()
      if (finalFile.exists()) finalFile.delete()
    }

    var downloadedBytes = if (canAppend) partFile.length() else 0L
    var knownTotalBytes = if (canAppend) existingMeta?.totalBytes ?: 0L else 0L
    val initialProgress = if (knownTotalBytes > 0L) {
      (downloadedBytes.toFloat() / knownTotalBytes.toFloat()).coerceIn(0f, 0.99f)
    } else 0f

    _uiState.update {
      it.copy(
        stage = UpdateStage.Downloading,
        subtitleMessage = if (canAppend) "Resuming ${release.tagName} download…" else "Starting ${release.tagName} download…",
        statusMessage = "Connecting to server…",
        progress = initialProgress,
        downloadedBytes = downloadedBytes,
        totalBytes = knownTotalBytes,
        speedBytesPerSec = 0L,
        isPaused = false,
        canResumePartial = canAppend,
        errorMessage = null,
        requiresUnknownAppsPermission = false,
        lastFailedOperation = FailedOperation.None
      )
    }

    try {
      withContext(Dispatchers.IO) {
        val reqBuilder = Request.Builder()
          .url(release.apkUrl)
          .header("User-Agent", "AURIX-Android-Updater")
        if (downloadedBytes > 0L) {
          reqBuilder.header("Range", "bytes=$downloadedBytes-")
        }

        val call = downloadClient.newCall(reqBuilder.build())
        activeCall = call

        call.execute().use { response ->
          if (response.code == 416 && downloadedBytes > 0L) {
            if (knownTotalBytes > 0L && downloadedBytes == knownTotalBytes) {
              return@use
            }
            partFile.delete()
            downloadedBytes = 0L
            knownTotalBytes = 0L
            throw IOException("Partial range expired. Please tap Retry.")
          }

          if (!response.isSuccessful) {
            throw IOException("Download failed with HTTP ${response.code}")
          }

          val body = response.body ?: throw IOException("Empty download body from server")
          val isPartialResponse = response.code == 206 && downloadedBytes > 0L

          val totalFromHeader = if (isPartialResponse) {
            parseContentRangeTotal(response.header("Content-Range"))
              ?: body.contentLength().takeIf { it > 0L }?.let { downloadedBytes + it }
              ?: knownTotalBytes
          } else {
            downloadedBytes = 0L
            if (partFile.exists()) partFile.delete()
            body.contentLength().coerceAtLeast(0L)
          }
          knownTotalBytes = totalFromHeader

          val remainingBytes = if (knownTotalBytes > 0L) {
            (knownTotalBytes - downloadedBytes).coerceAtLeast(0L)
          } else {
            -1L
          }

          val availableBytes = StatFs(downloadDir.absolutePath).availableBytes
          if (!hasEnoughStorageBytes(availableBytes, remainingBytes)) {
            val needMb = if (remainingBytes > 0L) {
              (remainingBytes + MIN_EXTRA_STORAGE_BYTES) / (1024L * 1024L)
            } else {
              FALLBACK_REQUIRED_BYTES / (1024L * 1024L)
            }
            throw LowStorageException(
              "Low storage: need at least ${needMb} MB free to download AURIX ${release.tagName}."
            )
          }

          saveDownloadMeta(release, knownTotalBytes, downloadedBytes)

          body.byteStream().use { input ->
            FileOutputStream(partFile, isPartialResponse).use { output ->
              val buffer = ByteArray(16 * 1024)
              var lastEmitTime = System.currentTimeMillis()
              var bytesSinceLastSample = 0L
              var smoothedSpeed = 0L

              while (coroutineContext.isActive && !pauseRequested && !cancelRequested) {
                val read = input.read(buffer)
                if (read == -1) break
                output.write(buffer, 0, read)
                downloadedBytes += read
                bytesSinceLastSample += read

                val now = System.currentTimeMillis()
                val elapsedMs = now - lastEmitTime
                if (elapsedMs >= 250L) {
                  val instantSpeed = (bytesSinceLastSample * 1000L) / elapsedMs.coerceAtLeast(1L)
                  smoothedSpeed = if (smoothedSpeed == 0L) {
                    instantSpeed
                  } else {
                    ((smoothedSpeed * 6L) + (instantSpeed * 4L)) / 10L
                  }
                  lastEmitTime = now
                  bytesSinceLastSample = 0L

                  val progress = if (knownTotalBytes > 0L) {
                    (downloadedBytes.toFloat() / knownTotalBytes.toFloat()).coerceIn(0f, 1f)
                  } else -1f
                  val pctText = if (progress >= 0f) "${(progress * 100).toInt()}%" else formatBytesMb(downloadedBytes)
                  val speedLabel = formatSpeed(smoothedSpeed)

                  saveDownloadMeta(release, knownTotalBytes, downloadedBytes)
                  _uiState.update {
                    it.copy(
                      stage = UpdateStage.Downloading,
                      subtitleMessage = "Downloading ${release.tagName} · $speedLabel",
                      statusMessage = "Downloading $pctText ($speedLabel)",
                      progress = progress,
                      downloadedBytes = downloadedBytes,
                      totalBytes = knownTotalBytes,
                      speedBytesPerSec = smoothedSpeed,
                      isPaused = false,
                      canResumePartial = true
                    )
                  }
                }
              }
              output.flush()
            }
          }
        }
      }

      activeCall = null

      if (cancelRequested) {
        clearPartialFiles(keepFinalApk = false)
        _uiState.update {
          it.copy(
            stage = if (it.latestRelease != null && it.latestRelease.versionCode > it.installed.versionCode) {
              UpdateStage.Available
            } else {
              UpdateStage.Idle
            },
            subtitleMessage = "Download canceled",
            statusMessage = if (it.latestRelease != null) {
              "Update available: ${it.latestRelease.tagName}"
            } else {
              "Ready to check for updates"
            },
            progress = 0f,
            downloadedBytes = 0L,
            totalBytes = 0L,
            speedBytesPerSec = 0L,
            isPaused = false,
            canResumePartial = false
          )
        }
        return
      }

      if (pauseRequested) {
        val pct = if (knownTotalBytes > 0L) {
          ((downloadedBytes.toFloat() / knownTotalBytes.toFloat()) * 100).toInt().coerceIn(0, 99)
        } else 0
        _uiState.update {
          it.copy(
            stage = UpdateStage.Available,
            subtitleMessage = "Download paused at $pct% — tap Resume Download",
            statusMessage = "Paused at $pct% (${formatBytesMb(downloadedBytes)})",
            progress = if (knownTotalBytes > 0L) (downloadedBytes.toFloat() / knownTotalBytes.toFloat()).coerceIn(0f, 0.99f) else it.progress,
            downloadedBytes = downloadedBytes,
            totalBytes = knownTotalBytes,
            speedBytesPerSec = 0L,
            isPaused = true,
            canResumePartial = true
          )
        }
        return
      }

      if (knownTotalBytes > 0L && partFile.length() != knownTotalBytes) {
        saveDownloadMeta(release, knownTotalBytes, partFile.length())
        _uiState.update {
          it.copy(
            stage = UpdateStage.Error,
            subtitleMessage = "Connection interrupted — tap Retry to resume",
            statusMessage = "Incomplete transfer (${formatBytesMb(partFile.length())} / ${formatBytesMb(knownTotalBytes)})",
            downloadedBytes = partFile.length(),
            totalBytes = knownTotalBytes,
            speedBytesPerSec = 0L,
            canResumePartial = partFile.length() > 0L,
            errorMessage = "Download interrupted before completion. Tap Retry to resume.",
            lastFailedOperation = FailedOperation.Download
          )
        }
        return
      }

      if (!partFile.renameTo(finalFile)) {
        partFile.copyTo(finalFile, overwrite = true)
        partFile.delete()
      }

      _uiState.update {
        it.copy(
          stage = UpdateStage.Verifying,
          subtitleMessage = "Verifying SHA-256 checksum & APK signature…",
          statusMessage = "Verifying package integrity…",
          progress = 1f,
          speedBytesPerSec = 0L,
          canResumePartial = false,
          isPaused = false
        )
      }

      val verifyError = withContext(Dispatchers.IO) {
        if (release.sha256.isNotBlank()) {
          val actualSha = computeSha256Hex(finalFile)
          if (!actualSha.equals(release.sha256.trim(), ignoreCase = true)) {
            clearPartialFiles(keepFinalApk = false)
            return@withContext "SHA-256 verification failed. The corrupted file was deleted."
          }
        }
        when (val archiveCheck = GitHubUpdater.validateApkArchive(context, finalFile)) {
          is ApkValidationResult.Invalid -> {
            clearPartialFiles(keepFinalApk = false)
            archiveCheck.reason
          }
          is ApkValidationResult.Valid -> null
        }
      }

      if (verifyError != null) {
        _uiState.update {
          it.copy(
            stage = UpdateStage.Error,
            subtitleMessage = "Verification failed — file deleted",
            statusMessage = verifyError,
            progress = 0f,
            downloadedBytes = 0L,
            totalBytes = 0L,
            isValidatedReady = false,
            validatedApkPath = null,
            canResumePartial = false,
            errorMessage = verifyError,
            lastFailedOperation = FailedOperation.Download
          )
        }
        return
      }

      getMetaFile().takeIf { it.exists() }?.delete()
      _uiState.update {
        it.copy(
          stage = UpdateStage.Available,
          isValidatedReady = true,
          validatedApkPath = finalFile.absolutePath,
          progress = 1f,
          subtitleMessage = "Verified! Ready to install ${release.tagName}",
          statusMessage = "Verified ${release.tagName}",
          errorMessage = null
        )
      }
      AppNotificationHelper.notifyUpdateReadyToInstall(context, release)
      if (isAppInForeground()) {
        launchSystemInstaller()
      }
    } catch (ce: CancellationException) {
      activeCall = null
      if (!pauseRequested && !cancelRequested) throw ce
    } catch (e: LowStorageException) {
      activeCall = null
      val msg = e.message ?: "Low storage space"
      AppNotificationHelper.notifyUpdateDownloadFailed(context, release, msg)
      _uiState.update {
        it.copy(
          stage = UpdateStage.Error,
          subtitleMessage = "Not enough free storage space",
          statusMessage = msg,
          speedBytesPerSec = 0L,
          errorMessage = e.message,
          lastFailedOperation = FailedOperation.Download
        )
      }
    } catch (e: Exception) {
      activeCall = null
      if (pauseRequested || cancelRequested) return
      val hasPartial = partFile.exists() && partFile.length() > 0L
      val msg = mapErrorMessage(e)
      AppNotificationHelper.notifyUpdateDownloadFailed(context, release, msg)
      _uiState.update {
        it.copy(
          stage = UpdateStage.Error,
          subtitleMessage = if (hasPartial) "Interrupted — tap Retry to resume" else "Download failed — tap Retry",
          statusMessage = msg,
          downloadedBytes = if (hasPartial) partFile.length() else 0L,
          speedBytesPerSec = 0L,
          canResumePartial = hasPartial,
          errorMessage = msg,
          lastFailedOperation = FailedOperation.Download
        )
      }
    }
  }

  fun pauseDownload() {
    if (_uiState.value.stage != UpdateStage.Downloading) return
    pauseRequested = true
    activeCall?.cancel()
  }

  fun cancelDownload() {
    cancelRequested = true
    pauseRequested = false
    activeCall?.cancel()
    downloadJob?.cancel()
    clearPartialFiles(keepFinalApk = false)
    _uiState.update { current ->
      val readyRelease = current.latestRelease?.takeIf {
        it.versionCode > current.installed.versionCode
      }
      current.copy(
        stage = if (readyRelease != null) UpdateStage.Available else UpdateStage.Idle,
        subtitleMessage = if (readyRelease != null) {
          "AURIX ${readyRelease.tagName} is ready to download"
        } else {
          "Over-the-air updates & release notes"
        },
        statusMessage = if (readyRelease != null) {
          "Update available: ${readyRelease.tagName}"
        } else {
          "Download canceled"
        },
        progress = 0f,
        downloadedBytes = 0L,
        totalBytes = 0L,
        speedBytesPerSec = 0L,
        isPaused = false,
        canResumePartial = false,
        isValidatedReady = false,
        validatedApkPath = null,
        errorMessage = null
      )
    }
  }

  fun retryFailedAction() {
    val state = _uiState.value
    when {
      state.lastFailedOperation == FailedOperation.Check -> checkForUpdates()
      state.lastFailedOperation == FailedOperation.Install && state.isValidatedReady -> launchSystemInstaller()
      state.latestRelease != null && state.latestRelease.versionCode > state.installed.versionCode -> startOrResumeDownload()
      else -> checkForUpdates()
    }
  }

  fun launchSystemInstaller() {
    val file = getFinalApkFile()
    when (val archiveCheck = GitHubUpdater.validateApkArchive(context, file)) {
      is ApkValidationResult.Invalid -> {
        clearPartialFiles(keepFinalApk = false)
        _uiState.update {
          it.copy(
            stage = UpdateStage.Error,
            subtitleMessage = "Package check failed — tap Retry",
            statusMessage = archiveCheck.reason,
            isValidatedReady = false,
            validatedApkPath = null,
            errorMessage = archiveCheck.reason,
            lastFailedOperation = FailedOperation.Download
          )
        }
        return
      }
      is ApkValidationResult.Valid -> {
        pendingTargetVersionCode = archiveCheck.versionCode
      }
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
      awaitingPermissionReturn = true
      _uiState.update {
        it.copy(
          stage = UpdateStage.Available,
          requiresUnknownAppsPermission = true,
          subtitleMessage = "Allow \"Install unknown apps\" for AURIX in Settings",
          statusMessage = "Permission required: Install unknown apps",
          errorMessage = null
        )
      }
      openUnknownSourcesSettings()
      return
    }

    try {
      awaitingPermissionReturn = false
      awaitingInstallReturn = true
      val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
      val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "application/vnd.android.package-archive")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
      _uiState.update {
        it.copy(
          stage = UpdateStage.Installing,
          requiresUnknownAppsPermission = false,
          subtitleMessage = "Confirm update in Android package installer…",
          statusMessage = "Installing AURIX ${it.latestRelease?.tagName ?: ""}…",
          progress = 1f,
          errorMessage = null
        )
      }
      context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
      awaitingInstallReturn = false
      _uiState.update {
        it.copy(
          stage = UpdateStage.Error,
          subtitleMessage = "No package installer found on device",
          statusMessage = "Unable to open Android installer",
          errorMessage = "No system package installer available on this device.",
          lastFailedOperation = FailedOperation.Install
        )
      }
    } catch (_: SecurityException) {
      awaitingInstallReturn = false
      _uiState.update {
        it.copy(
          stage = UpdateStage.Error,
          subtitleMessage = "Installer blocked by device policy",
          statusMessage = "Install blocked by security policy",
          errorMessage = "Install blocked by system security policy.",
          lastFailedOperation = FailedOperation.Install
        )
      }
    } catch (e: Exception) {
      awaitingInstallReturn = false
      val msg = e.localizedMessage ?: "Failed to launch installer"
      _uiState.update {
        it.copy(
          stage = UpdateStage.Error,
          subtitleMessage = "Couldn't open installer — tap Retry",
          statusMessage = msg,
          errorMessage = msg,
          lastFailedOperation = FailedOperation.Install
        )
      }
    }
  }

  fun openUnknownSourcesSettings() {
    try {
      val permIntent = Intent(
        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
        Uri.parse("package:${context.packageName}")
      ).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
      context.startActivity(permIntent)
    } catch (_: Exception) {
      _uiState.update {
        it.copy(
          stage = UpdateStage.Error,
          subtitleMessage = "Open Android Settings -> Install unknown apps",
          statusMessage = "Unable to open permission settings",
          errorMessage = "Please allow 'Install unknown apps' for AURIX in Android Settings.",
          lastFailedOperation = FailedOperation.Install
        )
      }
    }
  }

  fun onHostResumed() {
    val refreshedInstalled = GitHubUpdater.getInstalledAppInfo(context)
    _uiState.update { it.copy(installed = refreshedInstalled) }

    if (awaitingPermissionReturn) {
      awaitingPermissionReturn = false
      if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()) {
        _uiState.update { it.copy(requiresUnknownAppsPermission = false) }
        if (_uiState.value.isValidatedReady) {
          launchSystemInstaller()
        }
        return
      } else {
        _uiState.update {
          it.copy(
            requiresUnknownAppsPermission = true,
            statusMessage = "Enable 'Install unknown apps' to continue"
          )
        }
        return
      }
    }

    if (awaitingInstallReturn) {
      awaitingInstallReturn = false
      val targetLabel = _uiState.value.latestRelease?.tagName ?: "v${refreshedInstalled.versionName}"
      _uiState.update {
        it.copy(
          stage = UpdateStage.Done,
          installed = refreshedInstalled,
          subtitleMessage = "Installer finished — tap Restart App to apply",
          statusMessage = "Ready to restart ($targetLabel)",
          progress = 1f,
          errorMessage = null
        )
      }
    }
  }

  fun openReleasePage(hostContext: Context = context) {
    val targetUrl = _uiState.value.latestRelease?.releasePageUrl?.takeIf { it.isNotBlank() }
      ?: DEFAULT_RELEASE_PAGE_URL
    try {
      val intent = Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl)).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
      hostContext.startActivity(intent)
    } catch (_: Exception) {
      _uiState.update {
        it.copy(
          statusMessage = "No browser app found to open release page"
        )
      }
    }
  }

  fun restartApp(hostContext: Context = context) {
    runCatching {
      val launchIntent = hostContext.packageManager.getLaunchIntentForPackage(hostContext.packageName)
      if (launchIntent != null) {
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        hostContext.startActivity(launchIntent)
        Runtime.getRuntime().exit(0)
      }
    }.onFailure { err ->
      _uiState.update {
        it.copy(
          stage = UpdateStage.Error,
          statusMessage = err.localizedMessage ?: "Unable to restart app",
          errorMessage = err.localizedMessage ?: "Unable to restart app"
        )
      }
    }
  }

  private fun isAppInForeground(): Boolean {
    return runCatching {
      val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
        ?: return true
      val appProcess = am.runningAppProcesses?.firstOrNull { it.processName == context.packageName }
        ?: return true
      appProcess.importance == android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND ||
        appProcess.importance == android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE
    }.getOrDefault(true)
  }

  private fun mapErrorMessage(e: Throwable): String {
    val raw = e.localizedMessage.orEmpty()
    return when {
      e is UnknownHostException -> "Offline — no internet connection."
      e is SocketTimeoutException -> "Connection timed out. Try again."
      e is ConnectException -> "Unable to reach update server."
      e is SSLHandshakeException -> "Secure connection (TLS) failed."
      raw.contains("ENOSPC", ignoreCase = true) || raw.contains("No space left", ignoreCase = true) ->
        "Low storage space on device."
      raw.isNotBlank() -> raw
      else -> "Update error occurred. Please retry."
    }
  }

  private class LowStorageException(message: String) : IOException(message)
}

object UpdateManagerHolder {
  private val appScope = CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.Main.immediate)

  @Volatile
  private var instance: UpdateManager? = null

  fun getInstance(context: Context): UpdateManager {
    return instance ?: synchronized(this) {
      instance ?: UpdateManager(
        context = context.applicationContext,
        scope = appScope
      ).also { instance = it }
    }
  }
}
