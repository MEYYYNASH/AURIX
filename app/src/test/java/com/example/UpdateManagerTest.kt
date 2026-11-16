package com.example

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Environment
import androidx.test.core.app.ApplicationProvider
import com.example.data.InstalledAppInfo
import com.example.data.RemoteVersionInfo
import com.example.data.UpdateMainAction
import com.example.data.UpdateManager
import com.example.data.UpdateStage
import com.example.data.UpdateUiState
import com.example.data.VersionComparisonOutcome
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowNetworkCapabilities
import org.robolectric.shadows.ShadowStatFs
import java.io.File
import java.net.InetSocketAddress
import java.security.MessageDigest

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class UpdateManagerTest {

  @Test
  fun `parseVersionJson parses all expected fields and notes array`() {
    val raw = """
      {
        "versionName": "1.6.5",
        "versionCode": 10605,
        "releaseDate": "2026-11-16",
        "apkUrl": "https://example.com/AURIX-v1.6.5.apk",
        "sha256": "ABCDEF123456",
        "notes": [
          "Animated planet OTA updater",
          "Resumable SHA-256 downloads"
        ],
        "releasePageUrl": "https://github.com/MEYYYNASH/AURIX/releases/tag/v1.6.5"
      }
    """.trimIndent()

    val parsed = UpdateManager.parseVersionJson(raw)
    assertEquals("1.6.5", parsed.versionName)
    assertEquals(10605L, parsed.versionCode)
    assertEquals("2026-11-16", parsed.releaseDate)
    assertEquals("https://example.com/AURIX-v1.6.5.apk", parsed.apkUrl)
    assertEquals("abcdef123456", parsed.sha256)
    assertEquals(listOf("Animated planet OTA updater", "Resumable SHA-256 downloads"), parsed.notes)
    assertEquals("https://github.com/MEYYYNASH/AURIX/releases/tag/v1.6.5", parsed.releasePageUrl)
  }

  @Test
  fun `compareVersions correctly classifies higher equal and lower versionCodes`() {
    val remote = RemoteVersionInfo(
      versionName = "1.6.5",
      versionCode = 10605L,
      releaseDate = "2026-11-16",
      apkUrl = "https://example.com/app.apk",
      sha256 = "",
      notes = emptyList(),
      releasePageUrl = "https://example.com/releases"
    )

    assertTrue(UpdateManager.compareVersions(10603L, remote) is VersionComparisonOutcome.UpdateAvailable)
    assertTrue(UpdateManager.compareVersions(10605L, remote) is VersionComparisonOutcome.UpToDate)
    assertTrue(UpdateManager.compareVersions(10608L, remote) is VersionComparisonOutcome.NewerInstalled)
  }

  @Test
  fun `computeSha256Hex matches expected digest and detects mismatch`() {
    val tempFile = File.createTempFile("aurix_sha_test", ".bin")
    try {
      val payload = "AURIX-APK-PAYLOAD-1.6.5".toByteArray()
      tempFile.writeBytes(payload)

      val expected = MessageDigest.getInstance("SHA-256")
        .digest(payload)
        .joinToString("") { "%02x".format(it) }

      val computed = UpdateManager.computeSha256Hex(tempFile)
      assertEquals(expected, computed)
      assertFalse(computed.equals("0000000000000000", ignoreCase = true))
    } finally {
      tempFile.delete()
    }
  }

  @Test
  fun `hasEnoughStorageBytes enforces apk plus headroom threshold`() {
    val fortyMb = 40L * 1024L * 1024L
    assertFalse(UpdateManager.hasEnoughStorageBytes(availableBytes = fortyMb, remainingDownloadBytes = fortyMb))
    assertTrue(UpdateManager.hasEnoughStorageBytes(availableBytes = 70L * 1024L * 1024L, remainingDownloadBytes = fortyMb))
  }

  @Test
  fun `formatSpeed and parseContentRangeTotal format cleanly`() {
    assertEquals("512 KB/s", UpdateManager.formatSpeed(512L * 1024L))
    assertEquals("2.5 MB/s", UpdateManager.formatSpeed((2.5 * 1024 * 1024).toLong()))
    assertEquals(41943040L, UpdateManager.parseContentRangeTotal("bytes 1024-41943039/41943040"))
  }

  @Test
  fun `UpdateUiState maps stages to expected main action CTA`() {
    val installed = InstalledAppInfo("com.aurix.music", "1.6.3", 10603L, emptySet())
    assertEquals(
      UpdateMainAction.DownloadAndInstall,
      UpdateUiState(stage = UpdateStage.Available, installed = installed).mainAction
    )
    assertEquals(
      UpdateMainAction.ResumeDownload,
      UpdateUiState(stage = UpdateStage.Available, installed = installed, isPaused = true, canResumePartial = true).mainAction
    )
    assertEquals(
      UpdateMainAction.RestartApp,
      UpdateUiState(stage = UpdateStage.Done, installed = installed).mainAction
    )
    assertEquals(
      UpdateMainAction.Retry,
      UpdateUiState(stage = UpdateStage.Error, installed = installed).mainAction
    )
    assertEquals(
      UpdateMainAction.OpenInstallSettings,
      UpdateUiState(stage = UpdateStage.Available, installed = installed, requiresUnknownAppsPermission = true).mainAction
    )
  }

  @Test
  fun `sha256 mismatch deletes downloaded apk and transitions to Error state`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val shadowCm = Shadows.shadowOf(cm)
    val network = cm.activeNetwork
    if (network != null) {
      val caps = ShadowNetworkCapabilities.newInstance()
      Shadows.shadowOf(caps).addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
      shadowCm.setNetworkCapabilities(network, caps)
    }

    val downloadDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.cacheDir
    if (!downloadDir.exists()) downloadDir.mkdirs()
    ShadowStatFs.registerStats(downloadDir.absolutePath, 100_000, 80_000, 80_000)

    val apkPayload = ByteArray(160_000) { (it % 127).toByte() }
    val wrongSha = "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"

    val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    val port = server.address.port
    server.createContext("/version.json") { exchange ->
      val body = """
        {
          "versionName": "9.9.9",
          "versionCode": 99999,
          "releaseDate": "2026-11-16",
          "apkUrl": "http://127.0.0.1:$port/aurix.apk",
          "sha256": "$wrongSha",
          "notes": ["Test release"],
          "releasePageUrl": "http://127.0.0.1:$port/releases"
        }
      """.trimIndent().toByteArray()
      exchange.sendResponseHeaders(200, body.size.toLong())
      exchange.responseBody.use { it.write(body) }
    }
    server.createContext("/aurix.apk") { exchange ->
      exchange.responseHeaders.add("Content-Length", apkPayload.size.toString())
      exchange.sendResponseHeaders(200, apkPayload.size.toLong())
      exchange.responseBody.use { it.write(apkPayload) }
    }
    server.start()

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    try {
      val manager = UpdateManager(
        context = context,
        scope = scope,
        versionJsonUrl = "http://127.0.0.1:$port/version.json"
      )

      manager.checkForUpdates(silent = false)
      repeat(50) {
        val st = manager.uiState.value.stage
        if (st == UpdateStage.Available || st == UpdateStage.Error) return@repeat
        delay(50)
      }
      assertEquals(
        "Unexpected state after check: ${manager.uiState.value.errorMessage}",
        UpdateStage.Available,
        manager.uiState.value.stage
      )

      manager.startOrResumeDownload()
      repeat(60) {
        if (manager.uiState.value.stage == UpdateStage.Error) return@repeat
        delay(50)
      }

      val finalState = manager.uiState.value
      assertEquals("Final status: ${finalState.statusMessage}", UpdateStage.Error, finalState.stage)
      assertTrue(
        "Expected SHA-256 error, got: ${finalState.errorMessage}",
        finalState.errorMessage.orEmpty().contains("SHA-256", ignoreCase = true)
      )

      assertFalse(File(downloadDir, "aurix_update.apk").exists())
      assertFalse(File(downloadDir, "aurix_update.apk.part").exists())
    } finally {
      ShadowStatFs.reset()
      scope.cancel()
      server.stop(0)
    }
  }
}
