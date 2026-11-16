package com.example.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
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
    val HIDDEN_SONG_IDS = stringSetPreferencesKey("hidden_song_ids")
    val BLOCKED_ARTISTS = stringSetPreferencesKey("blocked_artists")
    val EQ_ENABLED = booleanPreferencesKey("eq_enabled")
    val EQ_PRESET = stringPreferencesKey("eq_preset")
    val EQ_BANDS = stringPreferencesKey("eq_bands")
    val EQ_BASS_BOOST = intPreferencesKey("eq_bass_boost")
    val CROSSFADE_SECONDS = intPreferencesKey("crossfade_seconds")
    val CROSSFADE_MANUAL_SKIP = booleanPreferencesKey("crossfade_manual_skip")
    val LAST_SEEN_VERSION_CODE = longPreferencesKey("last_seen_version_code")
    val UPDATE_DOWNLOAD_META = stringPreferencesKey("update_download_meta")
  }

  val themeMode: Flow<String> = context.dataStore.data.map { prefs -> prefs[THEME_MODE] ?: "system" }
  val accentColor: Flow<String> = context.dataStore.data.map { prefs -> prefs[ACCENT_COLOR] ?: "#007AFF" }
  val language: Flow<String> = context.dataStore.data.map { prefs -> prefs[LANGUAGE] ?: "en" }
  val dynamicBg: Flow<Boolean> = context.dataStore.data.map { prefs -> prefs[DYNAMIC_BG] ?: true }
  val reducedMotion: Flow<Boolean> = context.dataStore.data.map { prefs -> prefs[REDUCED_MOTION] ?: false }
  val onboardingCompleted: Flow<Boolean> = context.dataStore.data.map { prefs -> prefs[ONBOARDING_COMPLETED] ?: false }
  val githubOwner: Flow<String> = context.dataStore.data.map { prefs -> prefs[GITHUB_OWNER] ?: "MEYYYNASH" }
  val githubRepo: Flow<String> = context.dataStore.data.map { prefs -> prefs[GITHUB_REPO] ?: "AURIX" }
  val hiddenSongIds: Flow<Set<String>> = context.dataStore.data.map { prefs -> prefs[HIDDEN_SONG_IDS] ?: emptySet() }
  val blockedArtists: Flow<Set<String>> = context.dataStore.data.map { prefs -> prefs[BLOCKED_ARTISTS] ?: emptySet() }
  val eqEnabled: Flow<Boolean> = context.dataStore.data.map { prefs -> prefs[EQ_ENABLED] ?: false }
  val eqPreset: Flow<String> = context.dataStore.data.map { prefs -> prefs[EQ_PRESET] ?: "flat" }
  val eqBands: Flow<List<Int>> = context.dataStore.data.map { prefs ->
    prefs[EQ_BANDS]?.split(",")?.mapNotNull { it.trim().toIntOrNull() } ?: emptyList()
  }
  val eqBassBoost: Flow<Int> = context.dataStore.data.map { prefs -> prefs[EQ_BASS_BOOST] ?: 0 }
  val crossfadeSeconds: Flow<Int> = context.dataStore.data.map { prefs -> prefs[CROSSFADE_SECONDS] ?: 0 }
  val crossfadeManualSkip: Flow<Boolean> = context.dataStore.data.map { prefs -> prefs[CROSSFADE_MANUAL_SKIP] ?: true }
  val lastSeenVersionCode: Flow<Long> = context.dataStore.data.map { prefs -> prefs[LAST_SEEN_VERSION_CODE] ?: 0L }
  val updateDownloadMeta: Flow<String> = context.dataStore.data.map { prefs -> prefs[UPDATE_DOWNLOAD_META] ?: "" }

  suspend fun setThemeMode(mode: String) { context.dataStore.edit { it[THEME_MODE] = mode } }
  suspend fun setAccentColor(color: String) { context.dataStore.edit { it[ACCENT_COLOR] = color } }
  suspend fun setLanguage(lang: String) { context.dataStore.edit { it[LANGUAGE] = lang } }
  suspend fun setDynamicBg(enabled: Boolean) { context.dataStore.edit { it[DYNAMIC_BG] = enabled } }
  suspend fun setReducedMotion(enabled: Boolean) { context.dataStore.edit { it[REDUCED_MOTION] = enabled } }
  suspend fun setOnboardingCompleted(completed: Boolean) { context.dataStore.edit { it[ONBOARDING_COMPLETED] = completed } }
  suspend fun setGithubOwner(owner: String) { context.dataStore.edit { it[GITHUB_OWNER] = owner } }
  suspend fun setGithubRepo(repo: String) { context.dataStore.edit { it[GITHUB_REPO] = repo } }

  suspend fun addHiddenSongId(songId: String) {
    context.dataStore.edit { prefs ->
      val current = prefs[HIDDEN_SONG_IDS] ?: emptySet()
      prefs[HIDDEN_SONG_IDS] = current + songId
    }
  }

  suspend fun addBlockedArtist(normalizedArtist: String) {
    if (normalizedArtist.isBlank()) return
    context.dataStore.edit { prefs ->
      val current = prefs[BLOCKED_ARTISTS] ?: emptySet()
      prefs[BLOCKED_ARTISTS] = current + normalizedArtist
    }
  }

  suspend fun clearHiddenAndBlocked() {
    context.dataStore.edit { prefs ->
      prefs[HIDDEN_SONG_IDS] = emptySet()
      prefs[BLOCKED_ARTISTS] = emptySet()
    }
  }

  suspend fun saveEqualizerSettings(
    enabled: Boolean,
    presetId: String,
    bandsMb: List<Int>,
    bassBoostStrength: Int
  ) {
    context.dataStore.edit { prefs ->
      prefs[EQ_ENABLED] = enabled
      prefs[EQ_PRESET] = presetId
      prefs[EQ_BANDS] = bandsMb.joinToString(",")
      prefs[EQ_BASS_BOOST] = bassBoostStrength
    }
  }

  suspend fun saveCrossfadeSettings(durationSeconds: Int, fadeOnManualSkip: Boolean) {
    context.dataStore.edit { prefs ->
      prefs[CROSSFADE_SECONDS] = durationSeconds.coerceIn(0, 12)
      prefs[CROSSFADE_MANUAL_SKIP] = fadeOnManualSkip
    }
  }

  suspend fun setLastSeenVersionCode(versionCode: Long) {
    context.dataStore.edit { prefs ->
      prefs[LAST_SEEN_VERSION_CODE] = versionCode
    }
  }

  suspend fun setUpdateDownloadMeta(metaJson: String) {
    context.dataStore.edit { prefs ->
      if (metaJson.isBlank()) prefs.remove(UPDATE_DOWNLOAD_META)
      else prefs[UPDATE_DOWNLOAD_META] = metaJson
    }
  }
}
