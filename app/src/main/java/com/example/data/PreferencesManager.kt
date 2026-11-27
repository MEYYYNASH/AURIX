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
  private val onboardingSyncPrefs by lazy {
    context.getSharedPreferences("aurix_onboarding_sync", Context.MODE_PRIVATE)
  }

  fun isOnboardingCompletedSync(): Boolean =
    runCatching { onboardingSyncPrefs.getBoolean("onboarding_completed", false) }.getOrDefault(false)

  fun setOnboardingCompletedSync(completed: Boolean) {
    runCatching {
      onboardingSyncPrefs.edit().putBoolean("onboarding_completed", completed).apply()
    }
  }

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
    val DYNAMIC_ALBUM_THEME = booleanPreferencesKey("dynamic_album_theme")
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
    val LYRICS_ANIM = stringPreferencesKey("lyrics_anim")
    val LYRICS_WIDGET_ANIM = stringPreferencesKey("lyrics_widget_anim")
    val DEVELOPER_MODE_UNLOCKED = booleanPreferencesKey("developer_mode_unlocked")
    val DEVELOPER_MODE_ENABLED = booleanPreferencesKey("developer_mode_enabled")

    // Widget Customizer DataStore keys
    val WIDGET_SAME_PHOTO = booleanPreferencesKey("same_photo")
    val WIDGET_FRAME_SOURCE = stringPreferencesKey("frame_source")
    val WIDGET_BG_SOURCE = stringPreferencesKey("bg_source")
    val WIDGET_FRAME_STYLE = stringPreferencesKey("frame_style")
    val WIDGET_FRAME_COLOR = stringPreferencesKey("frame_color")
    val WIDGET_CORNER = intPreferencesKey("corner")
    val WIDGET_BORDER = intPreferencesKey("border")
    val WIDGET_MOTION = stringPreferencesKey("motion")
    val WIDGET_BG_TYPE = stringPreferencesKey("bg_type")
    val WIDGET_BG_COLOR = stringPreferencesKey("bg_color")
    val WIDGET_BLUR = intPreferencesKey("blur")
    val WIDGET_DARKNESS = intPreferencesKey("darkness")
    val WIDGET_PHOTO_REV = longPreferencesKey("widget_photo_rev")
    val WIDGET_STYLE = stringPreferencesKey("widget_style")
    val NOW_PLAYING_WIDGET_STYLE = stringPreferencesKey("now_playing_widget_style")
    val WIDGET_MATCH_SYSTEM_THEME = booleanPreferencesKey("widget_match_system_theme")
    val WIDGET_SHOW_LIKE_BUTTON = booleanPreferencesKey("widget_show_like_button")
    val WIDGET_SHOW_LYRICS = booleanPreferencesKey("widget_show_lyrics")

    // Orbi Companion & Ambient Standby Mode keys
    val ORBI_COMPANION_THEME = stringPreferencesKey("orbi_companion_theme")
    val STANDBY_VINYL_STYLE = booleanPreferencesKey("standby_vinyl_style")
    val STANDBY_CLOCK_STYLE = stringPreferencesKey("standby_clock_style")
    val STANDBY_USE_24H = booleanPreferencesKey("standby_use_24h")
    val STANDBY_MOOD = stringPreferencesKey("standby_mood")
    val STANDBY_LAYOUT = stringPreferencesKey("standby_layout")
    val STANDBY_GYRO_ENABLED = booleanPreferencesKey("standby_gyro_enabled")
    val STANDBY_AUTO_LAUNCH = booleanPreferencesKey("standby_auto_launch")
    val KHMER_LYRICS_FONT = stringPreferencesKey("khmer_lyrics_font")
    val DJ_AUTO_MIX_ENABLED = booleanPreferencesKey("dj_auto_mix_enabled")

    // Audio Pro Suite (v2.4.4)
    val EQ_AUTO_BT_SWITCH = booleanPreferencesKey("eq_auto_bt_switch")
    val EQ_BT_PRESET = stringPreferencesKey("eq_bt_preset")
    val EQ_HEADPHONES_PRESET = stringPreferencesKey("eq_headphones_preset")
    val EQ_SPEAKER_PRESET = stringPreferencesKey("eq_speaker_preset")
    val LOUDNESS_NORM_ENABLED = booleanPreferencesKey("loudness_norm_enabled")
    val LOUDNESS_TARGET_GAIN_MB = intPreferencesKey("loudness_target_gain_mb")
    val SLEEP_TIMER_FADE_OUT = booleanPreferencesKey("sleep_timer_fade_out")

    // Offline Download Controls (v2.4.4)
    val DOWNLOAD_WIFI_ONLY = booleanPreferencesKey("download_wifi_only")
    val DOWNLOAD_STORAGE_CAP_MB = intPreferencesKey("download_storage_cap_mb")

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
  val dynamicAlbumTheme: Flow<Boolean> = safeDataFlow.map { prefs -> prefs[DYNAMIC_ALBUM_THEME] ?: true }
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
  val eqAutoBtSwitch: Flow<Boolean> = safeDataFlow.map { prefs -> prefs[EQ_AUTO_BT_SWITCH] ?: false }
  val eqBtPreset: Flow<String> = safeDataFlow.map { prefs -> prefs[EQ_BT_PRESET] ?: "bass_boost" }
  val eqHeadphonesPreset: Flow<String> = safeDataFlow.map { prefs -> prefs[EQ_HEADPHONES_PRESET] ?: "pop" }
  val eqSpeakerPreset: Flow<String> = safeDataFlow.map { prefs -> prefs[EQ_SPEAKER_PRESET] ?: "vocal" }
  val loudnessNormEnabled: Flow<Boolean> = safeDataFlow.map { prefs -> prefs[LOUDNESS_NORM_ENABLED] ?: false }
  val loudnessTargetGainMb: Flow<Int> = safeDataFlow.map { prefs -> (prefs[LOUDNESS_TARGET_GAIN_MB] ?: 350).coerceIn(0, 900) }
  val sleepTimerFadeOut: Flow<Boolean> = safeDataFlow.map { prefs -> prefs[SLEEP_TIMER_FADE_OUT] ?: true }
  val downloadWifiOnly: Flow<Boolean> = safeDataFlow.map { prefs -> prefs[DOWNLOAD_WIFI_ONLY] ?: false }
  val downloadStorageCapMb: Flow<Int> = safeDataFlow.map { prefs -> (prefs[DOWNLOAD_STORAGE_CAP_MB] ?: 0).coerceAtLeast(0) }
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
  val lyricsAnim: Flow<String> = safeDataFlow.map { prefs ->
    com.example.lyrics.LyricsAnimationStyle.fromId(prefs[LYRICS_ANIM]).id
  }
  val orbiCompanionTheme: Flow<String> = safeDataFlow.map { prefs ->
    prefs[ORBI_COMPANION_THEME] ?: "aurora"
  }
  val standbyVinylStyle: Flow<Boolean> = safeDataFlow.map { prefs ->
    prefs[STANDBY_VINYL_STYLE] ?: true
  }
  val standbyClockStyle: Flow<String> = safeDataFlow.map { prefs ->
    prefs[STANDBY_CLOCK_STYLE] ?: "thin"
  }
  val standbyUse24h: Flow<Boolean> = safeDataFlow.map { prefs ->
    prefs[STANDBY_USE_24H] ?: true
  }
  val standbyMood: Flow<String> = safeDataFlow.map { prefs ->
    prefs[STANDBY_MOOD] ?: com.example.ui.screens.StandbyColorMood.DEFAULT_ID
  }
  val standbyLayout: Flow<String> = safeDataFlow.map { prefs ->
    com.example.ui.screens.StandbyLayout.fromId(prefs[STANDBY_LAYOUT]).id
  }
  val standbyGyroEnabled: Flow<Boolean> = safeDataFlow.map { prefs ->
    prefs[STANDBY_GYRO_ENABLED] ?: true
  }
  val standbyAutoLaunch: Flow<Boolean> = safeDataFlow.map { prefs ->
    prefs[STANDBY_AUTO_LAUNCH] ?: false
  }
  val widgetStyle: Flow<String> = safeDataFlow.map { prefs ->
    com.example.widget.WidgetStyle.fromId(prefs[WIDGET_STYLE]).id
  }
  val nowPlayingWidgetStyle: Flow<String> = safeDataFlow.map { prefs ->
    com.example.widget.NowPlayingWidgetStyle.fromId(prefs[NOW_PLAYING_WIDGET_STYLE]).id
  }
  val widgetMatchSystemTheme: Flow<Boolean> = safeDataFlow.map { prefs ->
    prefs[WIDGET_MATCH_SYSTEM_THEME] ?: false
  }
  val widgetShowLikeButton: Flow<Boolean> = safeDataFlow.map { prefs ->
    prefs[WIDGET_SHOW_LIKE_BUTTON] ?: true
  }
  val widgetShowLyrics: Flow<Boolean> = safeDataFlow.map { prefs ->
    prefs[WIDGET_SHOW_LYRICS] ?: true
  }
  val lyricsWidgetAnim: Flow<String> = safeDataFlow.map { prefs ->
    com.example.lyrics.LyricsAnimationStyle.fromId(prefs[LYRICS_WIDGET_ANIM] ?: prefs[LYRICS_ANIM]).id
  }
  val khmerLyricsFont: Flow<String> = safeDataFlow.map { prefs ->
    com.example.ui.theme.KhmerLyricsFont.fromId(prefs[KHMER_LYRICS_FONT]).id
  }
  val developerModeUnlocked: Flow<Boolean> = safeDataFlow.map { prefs ->
    prefs[DEVELOPER_MODE_UNLOCKED] ?: false
  }
  val developerModeEnabled: Flow<Boolean> = safeDataFlow.map { prefs ->
    (prefs[DEVELOPER_MODE_UNLOCKED] ?: false) && (prefs[DEVELOPER_MODE_ENABLED] ?: false)
  }
  val djAutoMixEnabled: Flow<Boolean> = safeDataFlow.map { prefs ->
    prefs[DJ_AUTO_MIX_ENABLED] ?: false
  }

  val widgetCustomizerConfig: Flow<com.example.widget.WidgetCustomizerConfig> = safeDataFlow.map { prefs ->
    com.example.widget.WidgetCustomizerConfig(
      samePhoto = prefs[WIDGET_SAME_PHOTO] ?: com.example.widget.WidgetCustomizerConfig.DEFAULT_SAME_PHOTO,
      frameSource = com.example.widget.WidgetPhotoSource.fromId(prefs[WIDGET_FRAME_SOURCE]),
      bgSource = com.example.widget.WidgetPhotoSource.fromId(prefs[WIDGET_BG_SOURCE]),
      frameStyle = com.example.widget.WidgetFrameStyle.fromId(prefs[WIDGET_FRAME_STYLE]),
      frameColorHex = prefs[WIDGET_FRAME_COLOR] ?: com.example.widget.WidgetCustomizerConfig.DEFAULT_FRAME_COLOR_HEX,
      cornerRadius = prefs[WIDGET_CORNER] ?: com.example.widget.WidgetCustomizerConfig.DEFAULT_CORNER,
      borderWidth = prefs[WIDGET_BORDER] ?: com.example.widget.WidgetCustomizerConfig.DEFAULT_BORDER,
      motion = com.example.widget.WidgetFrameMotion.fromId(prefs[WIDGET_MOTION]),
      bgType = com.example.widget.WidgetBgType.fromId(prefs[WIDGET_BG_TYPE]),
      bgColorHex = prefs[WIDGET_BG_COLOR] ?: com.example.widget.WidgetCustomizerConfig.DEFAULT_BG_COLOR_HEX,
      blur = prefs[WIDGET_BLUR] ?: com.example.widget.WidgetCustomizerConfig.DEFAULT_BLUR,
      darkness = prefs[WIDGET_DARKNESS] ?: com.example.widget.WidgetCustomizerConfig.DEFAULT_DARKNESS,
      photoRevision = prefs[WIDGET_PHOTO_REV] ?: 0L
    ).normalized()
  }

  suspend fun setThemeMode(mode: String) { context.dataStore.edit { it[THEME_MODE] = mode } }
  suspend fun setAccentColor(color: String) { context.dataStore.edit { it[ACCENT_COLOR] = color } }
  suspend fun setDynamicAlbumTheme(enabled: Boolean) { context.dataStore.edit { it[DYNAMIC_ALBUM_THEME] = enabled } }
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
  suspend fun setOnboardingCompleted(completed: Boolean) {
    setOnboardingCompletedSync(completed)
    context.dataStore.edit { it[ONBOARDING_COMPLETED] = completed }
  }
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

  suspend fun setLyricsAnimBoth(styleId: String) {
    val normalized = com.example.lyrics.LyricsAnimationStyle.fromId(styleId).id
    context.dataStore.edit { prefs ->
      prefs[LYRICS_ANIM] = normalized
      prefs[LYRICS_WIDGET_ANIM] = normalized
    }
  }

  suspend fun setLyricsAnimApp(styleId: String) {
    val normalized = com.example.lyrics.LyricsAnimationStyle.fromId(styleId).id
    context.dataStore.edit { prefs ->
      // Preserve current widget style if it was previously inheriting lyrics_anim
      if (prefs[LYRICS_WIDGET_ANIM] == null) {
        prefs[LYRICS_WIDGET_ANIM] = com.example.lyrics.LyricsAnimationStyle.fromId(prefs[LYRICS_ANIM]).id
      }
      prefs[LYRICS_ANIM] = normalized
    }
  }

  suspend fun setLyricsAnimWidget(styleId: String) {
    val normalized = com.example.lyrics.LyricsAnimationStyle.fromId(styleId).id
    context.dataStore.edit { prefs ->
      if (prefs[LYRICS_ANIM] == null) {
        prefs[LYRICS_ANIM] = com.example.lyrics.LyricsAnimationStyle.DEFAULT_ID
      }
      prefs[LYRICS_WIDGET_ANIM] = normalized
    }
  }

  suspend fun setKhmerLyricsFont(fontId: String) {
    val normalized = com.example.ui.theme.KhmerLyricsFont.fromId(fontId).id
    context.dataStore.edit { prefs ->
      prefs[KHMER_LYRICS_FONT] = normalized
    }
  }

  /**
   * Unlocks Developer mode (after 7 Build number taps) and turns it on immediately.
   */
  suspend fun unlockDeveloperMode() {
    context.dataStore.edit { prefs ->
      prefs[DEVELOPER_MODE_UNLOCKED] = true
      prefs[DEVELOPER_MODE_ENABLED] = true
    }
  }

  /**
   * Toggles Developer mode visibility on/off without ever deleting or resetting
   * saved custom lyrics, drafts, offsets, or unlock status.
   */
  suspend fun setDeveloperModeEnabled(enabled: Boolean) {
    context.dataStore.edit { prefs ->
      prefs[DEVELOPER_MODE_UNLOCKED] = true
      prefs[DEVELOPER_MODE_ENABLED] = enabled
    }
  }

  suspend fun saveWidgetCustomizerConfig(config: com.example.widget.WidgetCustomizerConfig) {
    val norm = config.normalized()
    context.dataStore.edit { prefs ->
      prefs[WIDGET_SAME_PHOTO] = norm.samePhoto
      prefs[WIDGET_FRAME_SOURCE] = norm.frameSource.id
      prefs[WIDGET_BG_SOURCE] = norm.bgSource.id
      prefs[WIDGET_FRAME_STYLE] = norm.frameStyle.id
      prefs[WIDGET_FRAME_COLOR] = norm.frameColorHex
      prefs[WIDGET_CORNER] = norm.cornerRadius
      prefs[WIDGET_BORDER] = norm.borderWidth
      prefs[WIDGET_MOTION] = norm.motion.id
      prefs[WIDGET_BG_TYPE] = norm.bgType.id
      prefs[WIDGET_BG_COLOR] = norm.bgColorHex
      prefs[WIDGET_BLUR] = norm.blur
      prefs[WIDGET_DARKNESS] = norm.darkness
      prefs[WIDGET_PHOTO_REV] = norm.photoRevision
    }
  }

  suspend fun resetWidgetCustomizerConfig() {
    saveWidgetCustomizerConfig(
      com.example.widget.WidgetCustomizerConfig.DEFAULT.copy(
        photoRevision = System.currentTimeMillis()
      )
    )
  }

  suspend fun setOrbiCompanionTheme(themeId: String) {
    context.dataStore.edit { prefs ->
      prefs[ORBI_COMPANION_THEME] = themeId
    }
  }

  suspend fun setStandbyVinylStyle(isVinyl: Boolean) {
    context.dataStore.edit { prefs ->
      prefs[STANDBY_VINYL_STYLE] = isVinyl
    }
  }

  suspend fun setStandbyClockStyle(styleId: String) {
    context.dataStore.edit { prefs ->
      prefs[STANDBY_CLOCK_STYLE] = styleId
    }
  }

  suspend fun setStandbyUse24h(use24h: Boolean) {
    context.dataStore.edit { prefs ->
      prefs[STANDBY_USE_24H] = use24h
    }
  }

  suspend fun setStandbyMood(moodId: String) {
    context.dataStore.edit { prefs ->
      prefs[STANDBY_MOOD] = moodId
    }
  }

  suspend fun setStandbyLayout(layoutId: String) {
    val normalized = com.example.ui.screens.StandbyLayout.fromId(layoutId).id
    context.dataStore.edit { prefs ->
      prefs[STANDBY_LAYOUT] = normalized
    }
  }

  suspend fun setStandbyGyroEnabled(enabled: Boolean) {
    context.dataStore.edit { prefs ->
      prefs[STANDBY_GYRO_ENABLED] = enabled
    }
  }

  suspend fun setStandbyAutoLaunch(enabled: Boolean) {
    context.dataStore.edit { prefs ->
      prefs[STANDBY_AUTO_LAUNCH] = enabled
    }
  }

  suspend fun setWidgetStyle(styleId: String) {
    val normalized = com.example.widget.WidgetStyle.fromId(styleId).id
    context.dataStore.edit { prefs ->
      prefs[WIDGET_STYLE] = normalized
    }
  }

  suspend fun setNowPlayingWidgetStyle(styleId: String) {
    val normalized = com.example.widget.NowPlayingWidgetStyle.fromId(styleId).id
    context.dataStore.edit { prefs ->
      prefs[NOW_PLAYING_WIDGET_STYLE] = normalized
    }
  }

  suspend fun setWidgetMatchSystemTheme(enabled: Boolean) {
    context.dataStore.edit { prefs ->
      prefs[WIDGET_MATCH_SYSTEM_THEME] = enabled
    }
  }

  suspend fun setWidgetShowLikeButton(enabled: Boolean) {
    context.dataStore.edit { prefs ->
      prefs[WIDGET_SHOW_LIKE_BUTTON] = enabled
    }
  }

  suspend fun setWidgetShowLyrics(enabled: Boolean) {
    context.dataStore.edit { prefs ->
      prefs[WIDGET_SHOW_LYRICS] = enabled
    }
  }

  suspend fun setDjAutoMixEnabled(enabled: Boolean) {
    context.dataStore.edit { prefs ->
      prefs[DJ_AUTO_MIX_ENABLED] = enabled
    }
  }

  suspend fun setEqAutoBtSwitch(enabled: Boolean) {
    context.dataStore.edit { prefs -> prefs[EQ_AUTO_BT_SWITCH] = enabled }
  }

  suspend fun setEqRoutePreset(route: com.example.playback.AudioOutputRoute, presetId: String) {
    context.dataStore.edit { prefs ->
      when (route) {
        com.example.playback.AudioOutputRoute.BLUETOOTH -> prefs[EQ_BT_PRESET] = presetId
        com.example.playback.AudioOutputRoute.HEADPHONES -> prefs[EQ_HEADPHONES_PRESET] = presetId
        com.example.playback.AudioOutputRoute.SPEAKER -> prefs[EQ_SPEAKER_PRESET] = presetId
      }
    }
  }

  suspend fun setLoudnessNormEnabled(enabled: Boolean) {
    context.dataStore.edit { prefs -> prefs[LOUDNESS_NORM_ENABLED] = enabled }
  }

  suspend fun setLoudnessTargetGainMb(gainMb: Int) {
    context.dataStore.edit { prefs -> prefs[LOUDNESS_TARGET_GAIN_MB] = gainMb.coerceIn(0, 900) }
  }

  suspend fun setSleepTimerFadeOut(enabled: Boolean) {
    context.dataStore.edit { prefs -> prefs[SLEEP_TIMER_FADE_OUT] = enabled }
  }

  suspend fun setDownloadWifiOnly(wifiOnly: Boolean) {
    context.dataStore.edit { prefs -> prefs[DOWNLOAD_WIFI_ONLY] = wifiOnly }
  }

  suspend fun setDownloadStorageCapMb(capMb: Int) {
    context.dataStore.edit { prefs -> prefs[DOWNLOAD_STORAGE_CAP_MB] = capMb.coerceAtLeast(0) }
  }
}
