package com.example.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore by preferencesDataStore(name = "aurix_prefs")

class PreferencesManager(private val context: Context) {
  companion object {
    val THEME_MODE = stringPreferencesKey("theme_mode")
    val ACCENT_COLOR = stringPreferencesKey("accent_color")
    val LANGUAGE = stringPreferencesKey("language")
    val DYNAMIC_BG = booleanPreferencesKey("dynamic_bg")
    val REDUCED_MOTION = booleanPreferencesKey("reduced_motion")
    val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
    val GITHUB_OWNER = stringPreferencesKey("github_owner")
    val GITHUB_REPO = stringPreferencesKey("github_repo")
  }

  val themeMode: Flow<String> = context.dataStore.data.map { prefs -> prefs[THEME_MODE] ?: "system" }
  val accentColor: Flow<String> = context.dataStore.data.map { prefs -> prefs[ACCENT_COLOR] ?: "#3B77F5" }
  val language: Flow<String> = context.dataStore.data.map { prefs -> prefs[LANGUAGE] ?: "en" }
  val dynamicBg: Flow<Boolean> = context.dataStore.data.map { prefs -> prefs[DYNAMIC_BG] ?: true }
  val reducedMotion: Flow<Boolean> = context.dataStore.data.map { prefs -> prefs[REDUCED_MOTION] ?: false }
  val onboardingCompleted: Flow<Boolean> = context.dataStore.data.map { prefs -> prefs[ONBOARDING_COMPLETED] ?: false }
  val githubOwner: Flow<String> = context.dataStore.data.map { prefs -> prefs[GITHUB_OWNER] ?: "aurix-music" }
  val githubRepo: Flow<String> = context.dataStore.data.map { prefs -> prefs[GITHUB_REPO] ?: "aurix-android" }

  suspend fun setThemeMode(mode: String) { context.dataStore.edit { it[THEME_MODE] = mode } }
  suspend fun setAccentColor(color: String) { context.dataStore.edit { it[ACCENT_COLOR] = color } }
  suspend fun setLanguage(lang: String) { context.dataStore.edit { it[LANGUAGE] = lang } }
  suspend fun setDynamicBg(enabled: Boolean) { context.dataStore.edit { it[DYNAMIC_BG] = enabled } }
  suspend fun setReducedMotion(enabled: Boolean) { context.dataStore.edit { it[REDUCED_MOTION] = enabled } }
  suspend fun setOnboardingCompleted(completed: Boolean) { context.dataStore.edit { it[ONBOARDING_COMPLETED] = completed } }
  suspend fun setGithubOwner(owner: String) { context.dataStore.edit { it[GITHUB_OWNER] = owner } }
  suspend fun setGithubRepo(repo: String) { context.dataStore.edit { it[GITHUB_REPO] = repo } }
}
