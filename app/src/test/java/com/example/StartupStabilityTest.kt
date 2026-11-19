package com.example

import android.app.Application
import android.content.ComponentName
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Search
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import com.example.data.AURIX_PREFS_CORRUPTION_HANDLER
import com.example.data.PreferencesManager
import com.example.playback.PlaybackService
import com.example.ui.screens.AurixBrandLogo
import com.example.ui.screens.AurixLaunchOverlay
import com.example.ui.screens.LiquidBlurSplitTabBar
import com.example.ui.screens.OrbitLoader
import com.example.ui.screens.TabItem
import com.example.ui.screens.UpdateLogo
import com.example.ui.screens.UpdateLogoState
import com.example.viewmodel.AurixViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class StartupStabilityTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  @Before
  fun setUp() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val shadowApp = Shadows.shadowOf(app)
    shadowApp.setBindServiceCallsOnServiceConnectedDirectly(false)
    shadowApp.declareComponentUnbindable(ComponentName(app, PlaybackService::class.java))
  }

  @Test
  fun corruptedDataStore_recoversToDefaultsWithoutCrash() = runBlocking {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val dsDir = File(app.cacheDir, "corrupted_ds_test")
    dsDir.mkdirs()
    val prefsFile = File(dsDir, "test_aurix_prefs.preferences_pb")
    prefsFile.writeBytes(byteArrayOf(0x7F, 0x13, 0x00, 0x55, 0x2A, 0x11))

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    try {
      val isolatedDataStore = PreferenceDataStoreFactory.create(
        corruptionHandler = AURIX_PREFS_CORRUPTION_HANDLER,
        scope = scope,
        produceFile = { prefsFile }
      )
      val prefs = isolatedDataStore.data.first()
      assertEquals("system", prefs[PreferencesManager.THEME_MODE] ?: "system")
      assertEquals("classic", prefs[PreferencesManager.NAV_STYLE] ?: "classic")
    } finally {
      scope.cancel()
      prefsFile.delete()
    }
  }

  @Test
  fun aurixViewModel_initializesAndCompletesStartup() = runBlocking {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val viewModel = AurixViewModel(app)

    val initialized = withTimeout(5_000L) {
      viewModel.isAppInitialized.first { it }
    }
    assertTrue(initialized)
  }

  @Test
  fun zeroSizeComposables_renderSafelyWithoutRadialGradientCrash() {
    val sampleTabs = listOf(
      TabItem("Home", Icons.Outlined.Home),
      TabItem("Search", Icons.Outlined.Search)
    )

    composeTestRule.setContent {
      Box(modifier = Modifier.size(0.dp)) {
        AurixBrandLogo(size = 0.dp, animated = false, reducedMotion = true)
        OrbitLoader(size = 0.dp, showComet = false, reducedMotion = true)
        UpdateLogo(state = UpdateLogoState.Available, size = 0.dp, reducedMotion = true)
        AurixLaunchOverlay(
          isAppInitialized = false,
          darkTheme = true,
          reducedMotion = true,
          onDismissed = {}
        )
        LiquidBlurSplitTabBar(
          tabs = sampleTabs,
          selectedIndex = 0,
          onSelect = {},
          reducedMotion = true
        )
      }
    }

    composeTestRule.waitForIdle()
  }
}
