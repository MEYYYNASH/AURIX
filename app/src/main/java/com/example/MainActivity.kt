package com.example

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.example.ui.screens.AurixApp
import com.example.viewmodel.AurixViewModel

class MainActivity : ComponentActivity() {
  private val viewModel: AurixViewModel by viewModels()

  override fun attachBaseContext(newBase: Context) {
    val lang = LocaleHelper.getPersistedLanguage(newBase)
    super.attachBaseContext(LocaleHelper.wrapBaseContext(newBase, lang))
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    val splashScreen = runCatching { installSplashScreen() }.getOrNull()
    super.onCreate(savedInstanceState)
    enableHighRefreshRate()

    // Remove the system splash immediately once the first Compose frame draws its matching background,
    // preventing any double logo before AurixLaunchOverlay animates in.
    runCatching {
      splashScreen?.setOnExitAnimationListener { provider ->
        runCatching { provider.remove() }
      }
    }

    // Never show the animated launch overlay again on configuration change or process recreation.
    if (savedInstanceState != null) {
      runCatching { viewModel.markLaunchSplashShown() }
    }

    try {
      viewModel.handleNotificationIntent(intent)
    } catch (t: Throwable) {
      viewModel.reportError(
        title = "Unable to Open Notification",
        throwable = t,
        fallbackMessage = "The notification shortcut could not be opened."
      )
    }

    runCatching { enableEdgeToEdge() }
    setContent {
      AurixApp(viewModel = viewModel)
    }
  }

  /**
   * Explicitly opt the window into the display's highest supported refresh rate (e.g. 120Hz / 144Hz)
   * at the current physical resolution so LTPO/OEM displays do not throttle Compose transitions to 60Hz.
   */
  @Suppress("DEPRECATION")
  private fun enableHighRefreshRate() {
    runCatching {
      if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
      val activeDisplay = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        display
      } else {
        windowManager.defaultDisplay
      } ?: return
      val currentMode = activeDisplay.mode ?: return
      val bestMode = activeDisplay.supportedModes
        ?.filter {
          it.physicalWidth == currentMode.physicalWidth &&
            it.physicalHeight == currentMode.physicalHeight
        }
        ?.maxByOrNull { it.refreshRate }
        ?: return
      if (bestMode.refreshRate > currentMode.refreshRate + 1f || bestMode.modeId != window.attributes.preferredDisplayModeId) {
        val params = window.attributes
        params.preferredDisplayModeId = bestMode.modeId
        window.attributes = params
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    try {
      setIntent(intent)
      viewModel.handleNotificationIntent(intent)
    } catch (t: Throwable) {
      viewModel.reportError(
        title = "Unable to Open Notification",
        throwable = t,
        fallbackMessage = "The notification shortcut could not be opened."
      )
    }
  }
}
