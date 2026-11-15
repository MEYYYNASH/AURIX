package com.example

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LocaleHelperTest {

  @Test
  fun normalizeLanguageHandlesKhmerAndFallback() {
    assertEquals("km", LocaleHelper.normalizeLanguage("km"))
    assertEquals("km", LocaleHelper.normalizeLanguage("KM-KH"))
    assertEquals("en", LocaleHelper.normalizeLanguage("en"))
    assertEquals("en", LocaleHelper.normalizeLanguage(null))
    assertEquals("en", LocaleHelper.normalizeLanguage("fr"))
  }

  @Test
  fun saveLanguageSyncPersistsAndResolvesKhmerResources() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    LocaleHelper.saveLanguageSync(app, "km")
    assertEquals("km", LocaleHelper.getPersistedLanguage(app))

    val kmConfig = LocaleHelper.localizedConfiguration(app.resources.configuration, "km")
    val kmContext = LocaleHelper.wrapActivityContext(app, kmConfig, "km")
    assertEquals("ការកំណត់", kmContext.getString(R.string.settings_title))
    assertEquals("ទំព័រដើម", kmContext.getString(R.string.nav_home))

    LocaleHelper.saveLanguageSync(app, "en")
    assertEquals("en", LocaleHelper.getPersistedLanguage(app))
    val enConfig = LocaleHelper.localizedConfiguration(app.resources.configuration, "en")
    val enContext = LocaleHelper.wrapActivityContext(app, enConfig, "en")
    assertEquals("Settings", enContext.getString(R.string.settings_title))
    assertEquals("Home", enContext.getString(R.string.nav_home))
  }
}
