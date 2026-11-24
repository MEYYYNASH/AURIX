package com.example.data

import android.content.Context
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

internal val AURIX_PREFS_CORRUPTION_HANDLER = ReplaceFileCorruptionHandler(
  produceNewData = { emptyPreferences() }
)

val Context.dataStore by preferencesDataStore(
  name = "aurix_prefs",
  corruptionHandler = AURIX_PREFS_CORRUPTION_HANDLER
)

class PreferencesManager(private val context: Context) {
  companion object {
    const val DEFAULT_DYNAMIC_BG_DIM = 0.40f
    const val DEFAULT_DYNAMIC_BG_GLASS = 0.50f
    const val DEFAULT_DYNAMIC_BG_BLUR = 60f
    const val MAX_LIBRARY_RECENT_SEARCHES = 5
    private const val RECENT_SEARCH_DELIMITER = "\u001F"

    fun isDefaultDynamicBg(dim: Float, glass: Float, blur: Float): Boolean {
      return kotlin.math.abs(dim - DEFAULT_DYNAMIC_BG_DIM) < 0.005f &&
        kotlin.math.abs(glass - DEFAULT_DYNAMIC_BG_GLASS) < 0.005f &&
        kotlin.math.abs(blur - DEFAULT_DYNAMIC_BG_BLUR) < 0.5f
    }

    val THEME_MODE = stringPreferencesKey("theme_mode")
    val ACCENT_COLOR = stringPreferencesKey("accent_color")
    val LANGUAGE = stringPreferencesKey("language")
    val DYNAMIC_BG = booleanPreferencesKey("dynamic_bg")
    val DYNAMIC_BG_DIM = floatPreferencesKey("dynamic_bg_dim")
    val DYNAMIC_BG_GLASS = floatPreferencesKey("dynamic_bg_glass")
    val DYNAMIC_BG_BLUR = floatPreferencesKey("dynamic_bg_blur")
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
    val EQ_VIRTUALIZER = intPreferencesKey("eq_virtualizer")
    val CROSSFADE_SECONDS = intPreferencesKey("crossfade_seconds")
    val CROSSFADE_MANUAL_SKIP = booleanPreferencesKey("crossfade_manual_skip")
    val LAST_SEEN_VERSION_CODE = longPreferencesKey("last_seen_version_code")
    val UPDATE_DOWNLOAD_META = stringPreferencesKey("update_download_meta")
    val MOOD_FLOW_ENABLED = booleanPreferencesKey("mood_flow_enabled")
    val NAV_STYLE = stringPreferencesKey("nav_style")
    val AUTO_CLEAN_YOUTUBE_TITLES = booleanPreferencesKey("auto_clean_youtube_titles")
    val NOW_PLAYING_VISUALIZER_ENABLED = booleanPreferencesKey("now_playing_visualizer_enabled")
    val LIBRARY_RECENT_SEARCHES = stringPreferencesKey("library_recent_searches")

    fun decodeRecentSearches(raw: String?): List<String> {
      if (raw.isNullOrBlank()) return emptyList()
      return raw.split(RECENT_SEARCH_DELIMITER)
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinctBy { it.lowercase() }
        .take(MAX_LIBRARY_RECENT_SEARCHES)
    }

    fun encodeRecentSearches(items: List<String>): String {
      return items
        .map { it.replace(RECENT_SEARCH_DELIMITER, " ").trim() }
        .filter { it.isNotEmpty() }
        .distinctBy { it.lowercase() }
        .take(MAX_LIBRARY_RECENT_SEARCHES)
        .joinToString(RECENT_SEARCH_DELIMITER)
    }

    fun updateRecentSearchesList(existing: List<String>, newQuery: String): List<String> {
      val cleaned = newQuery.replace(RECENT_SEARCH_DELIMITER, " ").trim()
      if (cleaned.isEmpty()) return existing.take(MAX_LIBRARY_RECENT_SEARCHES)
      val withoutDuplicate = existing.filterNot { it.equals(cleaned, ignoreCase = true) }
      return (listOf(cleaned) + withoutDuplicate).take(MAX_LIBRARY_RECENT_SEARCHES)
    }
  }

  private val safeDataFlow: Flow<Preferences> = context.dataStore.data.catch { exception ->
    if (exception is CancellationException) throw exception
    emit(emptyPreferences())
  }

  val themeMode: Flow<String> = safeDataFlow.map { prefs -> prefs[THEME_MODE] ?: "system" }
  val accentColor: Flow<String> = safeDataFlow.map { prefs -> prefs[ACCENT_COLOR] ?: "#007AFF" }
  val language: Flow<String> = safeDataFlow.map { prefs -> prefs[LANGUAGE] ?: "en" }
  val dynamicBg: Flow<Boolean> = safeDataFlow.map { prefs -> prefs[DYNAMIC_BG] ?: true }
  val dynamicBgDim: Flow<Float> = safeDataFlow.map { prefs ->
    (prefs[DYNAMIC_BG_DIM] ?: DEFAULT_DYNAMIC_BG_DIM).coerceIn(0f, 0.90f)
  }
  val dynamicBgGlass: Flow<Float> = safeDataFlow.map { prefs ->
    (prefs[DYNAMIC_BG_GLASS] ?: DEFAULT_DYNAMIC_BG_GLASS).coerceIn(0f, 1.00f)
  }
  val dynamicBgBlur: Flow<Float> = safeDataFlow.map { prefs ->
    (prefs[DYNAMIC_BG_BLUR] ?: DEFAULT_DYNAMIC_BG_BLUR).coerceIn(0f, 100f)
  }
  val reducedMotion: Flow<Boolean> = safeDataFlow.map { prefs -> prefs[REDUCED_MOTION] ?: false }
  val onboardingCompleted: Flow<Boolean> = safeDataFlow.map { prefs -> prefs[ONBOARDING_COMPLETED] ?: false }
  val githubOwner: Flow<String> = safeDataFlow.map { prefs -> prefs[GITHUB_OWNER] ?: "MEYYYNASH" }
  val githubRepo: Flow<String> = safeDataFlow.map { prefs -> prefs[GITHUB_REPO] ?: "AURIX" }
  val hiddenSongIds: Flow<Set<String>> = safeDataFlow.map { prefs -> prefs[HIDDEN_SONG_IDS] ?: emptySet() }
  val blockedArtists: Flow<Set<String>> = safeDataFlow.map { prefs -> prefs[BLOCKED_ARTISTS] ?: emptySet() }
  val eqEnabled: Flow<Boolean> = safeDataFlow.map { prefs -> prefs[EQ_ENABLED] ?: false }
  val eqPreset: Flow<String> = safeDataFlow.map { prefs -> prefs[EQ_PRESET] ?: "flat" }
  val eqBands: Flow<List<Int>> = safeDataFlow.map { prefs ->
    prefs[EQ_BANDS]?.split(",")?.mapNotNull { it.trim().toIntOrNull() } ?: emptyList()
  }
  val eqBassBoost: Flow<Int> = safeDataFlow.map { prefs -> prefs[EQ_BASS_BOOST] ?: 0 }
  val eqVirtualizer: Flow<Int> = safeDataFlow.map { prefs -> prefs[EQ_VIRTUALIZER] ?: 0 }
  val crossfadeSeconds: Flow<Int> = safeDataFlow.map { prefs -> prefs[CROSSFADE_SECONDS] ?: 0 }
  val crossfadeManualSkip: Flow<Boolean> = safeDataFlow.map { prefs -> prefs[CROSSFADE_MANUAL_SKIP] ?: true }
  val lastSeenVersionCode: Flow<Long> = safeDataFlow.map { prefs -> prefs[LAST_SEEN_VERSION_CODE] ?: 0L }
  val updateDownloadMeta: Flow<String> = safeDataFlow.map { prefs -> prefs[UPDATE_DOWNLOAD_META] ?: "" }
  val moodFlowEnabled: Flow<Boolean> = safeDataFlow.map { prefs -> prefs[MOOD_FLOW_ENABLED] ?: true }
  val navStyle: Flow<String> = safeDataFlow.map { prefs -> prefs[NAV_STYLE] ?: "classic" }
  val autoCleanYoutubeTitles: Flow<Boolean> = safeDataFlow.map { prefs -> prefs[AUTO_CLEAN_YOUTUBE_TITLES] ?: true }
  val nowPlayingVisualizerEnabled: Flow<Boolean> = safeDataFlow.map { prefs -> prefs[NOW_PLAYING_VISUALIZER_ENABLED] ?: true }
  val libraryRecentSearches: Flow<List<String>> = safeDataFlow.map { prefs ->
    decodeRecentSearches(prefs[LIBRARY_RECENT_SEARCHES])
  }

  suspend fun setThemeMode(mode: String) { context.dataStore.edit { it[THEME_MODE] = mode } }
  suspend fun setAccentColor(color: String) { context.dataStore.edit { it[ACCENT_COLOR] = color } }
  suspend fun setLanguage(lang: String) { context.dataStore.edit { it[LANGUAGE] = lang } }
  suspend fun setDynamicBg(enabled: Boolean) { context.dataStore.edit { it[DYNAMIC_BG] = enabled } }
  suspend fun setDynamicBgDim(value: Float) {
    context.dataStore.edit { it[DYNAMIC_BG_DIM] = value.coerceIn(0f, 0.90f) }
  }
  suspend fun setDynamicBgGlass(value: Float) {
    context.dataStore.edit { it[DYNAMIC_BG_GLASS] = value.coerceIn(0f, 1.00f) }
  }
  suspend fun setDynamicBgBlur(value: Float) {
    context.dataStore.edit { it[DYNAMIC_BG_BLUR] = value.coerceIn(0f, 100f) }
  }
  suspend fun resetDynamicBgCustomization() {
    context.dataStore.edit { prefs ->
      prefs[DYNAMIC_BG_DIM] = DEFAULT_DYNAMIC_BG_DIM
      prefs[DYNAMIC_BG_GLASS] = DEFAULT_DYNAMIC_BG_GLASS
      prefs[DYNAMIC_BG_BLUR] = DEFAULT_DYNAMIC_BG_BLUR
    }
  }
  suspend fun addLibraryRecentSearch(query: String) {
    val cleaned = query.trim()
    if (cleaned.isEmpty()) return
    context.dataStore.edit { prefs ->
      val current = decodeRecentSearches(prefs[LIBRARY_RECENT_SEARCHES])
      val updated = updateRecentSearchesList(current, cleaned)
      prefs[LIBRARY_RECENT_SEARCHES] = encodeRecentSearches(updated)
    }
  }
  suspend fun removeLibraryRecentSearch(query: String) {
    context.dataStore.edit { prefs ->
      val current = decodeRecentSearches(prefs[LIBRARY_RECENT_SEARCHES])
      val updated = current.filterNot { it.equals(query.trim(), ignoreCase = true) }
      if (updated.isEmpty()) {
        prefs.remove(LIBRARY_RECENT_SEARCHES)
      } else {
        prefs[LIBRARY_RECENT_SEARCHES] = encodeRecentSearches(updated)
      }
    }
  }
  suspend fun clearLibraryRecentSearches() {
    context.dataStore.edit { prefs ->
      prefs.remove(LIBRARY_RECENT_SEARCHES)
    }
  }
  suspend fun setReducedMotion(enabled: Boolean) { context.dataStore.edit { it[REDUCED_MOTION] = enabled } }
  suspend fun setOnboardingCompleted(completed: Boolean) { context.dataStore.edit { it[ONBOARDING_COMPLETED] = completed } }
  suspend fun setGithubOwner(owner: String) { context.dataStore.edit { it[GITHUB_OWNER] = owner } }
  suspend fun setGithubRepo(repo: String) { context.dataStore.edit { it[GITHUB_REPO] = repo } }
  suspend fun setNavStyle(style: String) { context.dataStore.edit { it[NAV_STYLE] = style } }

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
    bassBoostStrength: Int,
    virtualizerStrength: Int = 0
  ) {
    context.dataStore.edit { prefs ->
      prefs[EQ_ENABLED] = enabled
      prefs[EQ_PRESET] = presetId
      prefs[EQ_BANDS] = bandsMb.joinToString(",")
      prefs[EQ_BASS_BOOST] = bassBoostStrength
      prefs[EQ_VIRTUALIZER] = virtualizerStrength
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

  suspend fun setMoodFlowEnabled(enabled: Boolean) {
    context.dataStore.edit { prefs ->
      prefs[MOOD_FLOW_ENABLED] = enabled
    }
  }

  suspend fun setAutoCleanYoutubeTitles(enabled: Boolean) {
    context.dataStore.edit { prefs ->
      prefs[AUTO_CLEAN_YOUTUBE_TITLES] = enabled
    }
  }

  suspend fun setNowPlayingVisualizerEnabled(enabled: Boolean) {
    context.dataStore.edit { prefs ->
      prefs[NOW_PLAYING_VISUALIZER_ENABLED] = enabled
    }
  }
}
