package com.example

import android.app.Application
import android.content.ComponentName
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.data.PreferencesManager
import com.example.playback.PlaybackService
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.junit.runners.model.Statement
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MainActivityLaunchTest {

  private val composeTestRule = createAndroidComposeRule<MainActivity>()

  private val robolectricServiceSetupRule = TestRule { base, _ ->
    object : Statement() {
      override fun evaluate() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val shadowApp = Shadows.shadowOf(app)
        shadowApp.setBindServiceCallsOnServiceConnectedDirectly(false)
        shadowApp.declareComponentUnbindable(ComponentName(app, PlaybackService::class.java))
        base.evaluate()
      }
    }
  }

  @get:Rule
  val ruleChain: RuleChain = RuleChain
    .outerRule(robolectricServiceSetupRule)
    .around(composeTestRule)

  @Test
  fun coldLaunch_onboardingFalse_rendersWithoutCrash() {
    composeTestRule.waitForIdle()
    composeTestRule.mainClock.advanceTimeBy(3000)
    composeTestRule.waitForIdle()
    assertTrue(true)
  }

  @Test
  fun returningUser_homeWithWhatsNewSheet_rendersWithoutCrash() = runBlocking {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val prefs = PreferencesManager(app)
    prefs.setOnboardingCompleted(true)
    prefs.setLastSeenVersionCode(10800L)

    composeTestRule.activityRule.scenario.onActivity { activity ->
      activity.recreate()
    }
    composeTestRule.waitForIdle()
    composeTestRule.mainClock.advanceTimeBy(3000)
    composeTestRule.waitForIdle()
    assertTrue(true)
  }

  @Test
  fun khmerLocale_homeAndWhatsNew_rendersWithoutCrash() = runBlocking {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val prefs = PreferencesManager(app)
    prefs.setOnboardingCompleted(true)
    prefs.setLanguage("km")
    prefs.setNavStyle("blur")
    prefs.setLastSeenVersionCode(0L)

    composeTestRule.activityRule.scenario.onActivity { activity ->
      activity.recreate()
    }
    composeTestRule.waitForIdle()
    composeTestRule.mainClock.advanceTimeBy(3000)
    composeTestRule.waitForIdle()
    assertTrue(true)
  }

  @Test
  fun navigateAllMainTabs_enAndKm_rendersWithoutCrash() = runBlocking {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val prefs = PreferencesManager(app)
    prefs.setOnboardingCompleted(true)
    prefs.setLastSeenVersionCode(10900L)

    listOf("en", "km").forEach { lang ->
      prefs.setLanguage(lang)
      composeTestRule.activityRule.scenario.onActivity { activity ->
        activity.recreate()
      }
      composeTestRule.waitForIdle()
      composeTestRule.mainClock.advanceTimeBy(3000)
      composeTestRule.waitForIdle()

      listOf(1, 2, 3, 0).forEach { tabIdx ->
        val nodes = composeTestRule.onAllNodes(androidx.compose.ui.test.hasTestTag("nav_tab_$tabIdx")).fetchSemanticsNodes()
        if (nodes.isNotEmpty()) {
          composeTestRule.onNode(androidx.compose.ui.test.hasTestTag("nav_tab_$tabIdx")).performClick()
          composeTestRule.waitForIdle()
        }
      }
    }
    assertTrue(true)
  }
}
