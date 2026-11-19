package com.example

import android.content.Context
import android.content.ContextWrapper
import android.content.res.AssetManager
import android.content.res.Configuration
import android.content.res.Resources
import java.util.Locale

object LocaleHelper {
  private const val PREFS_NAME = "aurix_locale_prefs"
  private const val KEY_LANGUAGE = "language"
  const val LANG_EN = "en"
  const val LANG_KM = "km"

  fun normalizeLanguage(lang: String?): String {
    return if (lang?.trim()?.lowercase(Locale.ROOT)?.startsWith(LANG_KM) == true) LANG_KM else LANG_EN
  }

  fun localeFor(lang: String?): Locale {
    return when (normalizeLanguage(lang)) {
      LANG_KM -> Locale.forLanguageTag("km")
      else -> Locale.ENGLISH
    }
  }

  fun getPersistedLanguage(context: Context): String {
    return runCatching {
      val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
      normalizeLanguage(prefs.getString(KEY_LANGUAGE, LANG_EN))
    }.getOrDefault(LANG_EN)
  }

  fun saveLanguageSync(context: Context, lang: String?) {
    val normalized = normalizeLanguage(lang)
    runCatching {
      context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putString(KEY_LANGUAGE, normalized)
        .apply()
      Locale.setDefault(localeFor(normalized))
    }
  }

  fun localizedConfiguration(base: Configuration, lang: String?): Configuration {
    val locale = localeFor(lang)
    return Configuration(base).apply {
      setLocale(locale)
      setLayoutDirection(locale)
    }
  }

  fun wrapBaseContext(base: Context, lang: String?): Context {
    val normalized = normalizeLanguage(lang)
    val locale = localeFor(normalized)
    Locale.setDefault(locale)
    val config = localizedConfiguration(base.resources.configuration, normalized)
    return base.createConfigurationContext(config)
  }

  fun wrapActivityContext(base: Context, config: Configuration, lang: String?): Context {
    val normalized = normalizeLanguage(lang)
    Locale.setDefault(localeFor(normalized))
    val localizedResources = base.createConfigurationContext(config).resources
    return object : ContextWrapper(base) {
      override fun getResources(): Resources = localizedResources
      override fun getAssets(): AssetManager = localizedResources.assets
    }
  }
}
