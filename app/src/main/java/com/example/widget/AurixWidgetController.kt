package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Bundle
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.example.LocaleHelper
import com.example.MainActivity
import com.example.R
import com.example.data.AppNotificationHelper
import com.example.data.PreferencesManager
import com.example.lyrics.LrcParser
import com.example.model.APP_ICON_ARTWORK
import com.example.model.LyricLine
import com.example.model.LyricsResult
import com.example.model.Song
import com.example.playback.PlaybackService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class SyncedLyricLineSnapshot(
  val activeIndex: Int,
  val currentLine: String,
  val nextLine: String,
  val hasLyrics: Boolean
)

data class WidgetSnapshotState(
  val songId: String = "",
  val title: String = "",
  val artist: String = "",
  val artworkUrl: String = "",
  val isPlaying: Boolean = false,
  val isFavorite: Boolean = false,
  val positionMs: Long = 0L,
  val durationMs: Long = 0L,
  val hasLyrics: Boolean = false,
  val currentLyricLine: String = "",
  val nextLyricLine: String = "",
  val activeLyricIndex: Int = -1,
  val rawLrc: String = "",
  val plainLyrics: String = "",
  val lyricOffsetMs: Long = 0L,
  val ambientGlowEnabled: Boolean = true,
  val dimLevel: Float = PreferencesManager.DEFAULT_DYNAMIC_BG_DIM,
  val glassLevel: Float = PreferencesManager.DEFAULT_DYNAMIC_BG_GLASS,
  val blurLevel: Float = PreferencesManager.DEFAULT_DYNAMIC_BG_BLUR,
  val themeMode: String = "dark",
  val language: String = "en"
)

object AurixWidgetController {
  const val ACTION_WIDGET_PREV = "com.example.aurix.widget.ACTION_PREV"
  const val ACTION_WIDGET_PLAY_PAUSE = "com.example.aurix.widget.ACTION_PLAY_PAUSE"
  const val ACTION_WIDGET_NEXT = "com.example.aurix.widget.ACTION_NEXT"
  const val ACTION_WIDGET_LIKE = "com.example.aurix.widget.ACTION_LIKE"
  const val ACTION_WIDGET_REFRESH = "com.example.aurix.widget.ACTION_REFRESH"

  // Design palette constants
  const val COLOR_BG_AMOLED = 0xFF080C14.toInt()
  const val COLOR_CARD_DARK = 0xFF151F30.toInt()
  const val COLOR_CARD_LIGHT = 0xFFF0F4FC.toInt()
  const val COLOR_ACCENT = 0xFF43C7F4.toInt()
  const val COLOR_SECOND_ACCENT = 0xFF7965FF.toInt()
  const val COLOR_HIGHLIGHT = 0xFFA855F7.toInt()
  const val COLOR_TEXT_DARK = 0xFFF7F9FF.toInt()
  const val COLOR_TEXT_LIGHT = 0xFF0E1726.toInt()
  const val COLOR_MUTED_DARK = 0xFF929DB0.toInt()
  const val COLOR_MUTED_LIGHT = 0xFF526077.toInt()
  const val COLOR_BORDER_DARK = 0xFF273348.toInt()
  const val COLOR_BORDER_LIGHT = 0xFFD0DCF0.toInt()
  const val COLOR_HEART_RED = 0xFFEF4444.toInt()

  // Below 3 cells wide (< 180dp), hide Like button
  const val MIN_WIDTH_DP_FOR_LIKE = 180

  // Minimum legible dim floor so text remains readable at every Dim setting (0-90%)
  const val MIN_LEGIBLE_DIM_FLOOR = 0.28f

  private const val PREFS_WIDGET_SNAPSHOT = "aurix_widget_snapshot_prefs"
  private const val CACHE_DIR_NAME = "widget_cache"
  private const val CACHED_RAW_ART_FILE = "last_album_art.png"
  private const val CACHED_GLOW_BG_FILE = "last_glow_bg.png"
  private const val CACHED_ROUNDED_ART_FILE = "last_rounded_art.png"

  private val widgetScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  @Volatile
  private var lastLoadedArtworkKey: String = ""

  @Volatile
  private var cachedRawArtworkBitmap: Bitmap? = null

  @Volatile
  private var cachedGlowRenderKey: String = ""

  @Volatile
  private var cachedGlowBitmap: Bitmap? = null

  @Volatile
  private var cachedRoundedArtBitmap: Bitmap? = null

  fun shouldShowLikeButton(minWidthDp: Int): Boolean {
    return minWidthDp <= 0 || minWidthDp >= MIN_WIDTH_DP_FOR_LIKE
  }

  fun containsKhmerScript(text: String?): Boolean {
    if (text.isNullOrBlank()) return false
    for (ch in text) {
      val code = ch.code
      if (code in 0x1780..0x17FF || code in 0x19E0..0x19FF) {
        return true
      }
    }
    return false
  }

  fun resolveSyncedLyricLines(
    syncedLines: List<LyricLine>?,
    plainLyrics: String?,
    positionMs: Long,
    lyricOffsetMs: Long = 0L,
    noLyricsLabel: String = "No lyrics for this song"
  ): SyncedLyricLineSnapshot {
    val cleanSynced = syncedLines?.filter { it.text.isNotBlank() }.orEmpty()
    if (cleanSynced.isNotEmpty()) {
      val effectivePos = (positionMs + lyricOffsetMs).coerceAtLeast(0L)
      var activeIdx = -1
      for (i in cleanSynced.indices) {
        if (cleanSynced[i].timestampMs <= effectivePos) {
          activeIdx = i
        } else {
          break
        }
      }
      val resolvedIdx = if (activeIdx >= 0) activeIdx else 0
      val currentText = cleanSynced.getOrNull(resolvedIdx)?.text?.trim().orEmpty()
      val nextText = cleanSynced.getOrNull(resolvedIdx + 1)?.text?.trim().orEmpty()
      return SyncedLyricLineSnapshot(
        activeIndex = resolvedIdx,
        currentLine = currentText.ifBlank { "♪" },
        nextLine = nextText,
        hasLyrics = true
      )
    }

    val cleanPlain = plainLyrics
      ?.lineSequence()
      ?.map { it.trim() }
      ?.filter {
        it.isNotEmpty() &&
          !it.equals(noLyricsLabel, ignoreCase = true) &&
          !it.equals("No lyrics found for this track", ignoreCase = true) &&
          !it.equals("រកមិនឃើញទំនុកច្រៀងសម្រាប់បទនេះទេ", ignoreCase = true)
      }
      ?.toList()
      .orEmpty()

    if (cleanPlain.isNotEmpty()) {
      return SyncedLyricLineSnapshot(
        activeIndex = 0,
        currentLine = cleanPlain.first(),
        nextLine = cleanPlain.getOrNull(1).orEmpty(),
        hasLyrics = true
      )
    }

    return SyncedLyricLineSnapshot(
      activeIndex = -1,
      currentLine = noLyricsLabel,
      nextLine = "",
      hasLyrics = false
    )
  }

  fun readSnapshot(context: Context): WidgetSnapshotState {
    val prefs = context.applicationContext.getSharedPreferences(PREFS_WIDGET_SNAPSHOT, Context.MODE_PRIVATE)
    val savedLang = prefs.getString("language", null) ?: LocaleHelper.getPersistedLanguage(context)
    return WidgetSnapshotState(
      songId = prefs.getString("song_id", "").orEmpty(),
      title = prefs.getString("title", "").orEmpty(),
      artist = prefs.getString("artist", "").orEmpty(),
      artworkUrl = prefs.getString("artwork_url", "").orEmpty(),
      isPlaying = prefs.getBoolean("is_playing", false),
      isFavorite = prefs.getBoolean("is_favorite", false),
      positionMs = prefs.getLong("position_ms", 0L),
      durationMs = prefs.getLong("duration_ms", 0L),
      hasLyrics = prefs.getBoolean("has_lyrics", false),
      currentLyricLine = prefs.getString("current_lyric_line", "").orEmpty(),
      nextLyricLine = prefs.getString("next_lyric_line", "").orEmpty(),
      activeLyricIndex = prefs.getInt("active_lyric_index", -1),
      rawLrc = prefs.getString("raw_lrc", "").orEmpty(),
      plainLyrics = prefs.getString("plain_lyrics", "").orEmpty(),
      lyricOffsetMs = prefs.getLong("lyric_offset_ms", 0L),
      ambientGlowEnabled = prefs.getBoolean("ambient_glow_enabled", true),
      dimLevel = prefs.getFloat("dim_level", PreferencesManager.DEFAULT_DYNAMIC_BG_DIM).coerceIn(0f, 0.90f),
      glassLevel = prefs.getFloat("glass_level", PreferencesManager.DEFAULT_DYNAMIC_BG_GLASS).coerceIn(0f, 1.00f),
      blurLevel = prefs.getFloat("blur_level", PreferencesManager.DEFAULT_DYNAMIC_BG_BLUR).coerceIn(0f, 100f),
      themeMode = prefs.getString("theme_mode", "dark").orEmpty().ifBlank { "dark" },
      language = savedLang
    )
  }

  private fun saveSnapshot(context: Context, state: WidgetSnapshotState) {
    val prefs = context.applicationContext.getSharedPreferences(PREFS_WIDGET_SNAPSHOT, Context.MODE_PRIVATE)
    prefs.edit()
      .putString("song_id", state.songId)
      .putString("title", state.title)
      .putString("artist", state.artist)
      .putString("artwork_url", state.artworkUrl)
      .putBoolean("is_playing", state.isPlaying)
      .putBoolean("is_favorite", state.isFavorite)
      .putLong("position_ms", state.positionMs)
      .putLong("duration_ms", state.durationMs)
      .putBoolean("has_lyrics", state.hasLyrics)
      .putString("current_lyric_line", state.currentLyricLine)
      .putString("next_lyric_line", state.nextLyricLine)
      .putInt("active_lyric_index", state.activeLyricIndex)
      .putString("raw_lrc", state.rawLrc)
      .putString("plain_lyrics", state.plainLyrics)
      .putLong("lyric_offset_ms", state.lyricOffsetMs)
      .putBoolean("ambient_glow_enabled", state.ambientGlowEnabled)
      .putFloat("dim_level", state.dimLevel.coerceIn(0f, 0.90f))
      .putFloat("glass_level", state.glassLevel.coerceIn(0f, 1.00f))
      .putFloat("blur_level", state.blurLevel.coerceIn(0f, 100f))
      .putString("theme_mode", state.themeMode)
      .putString("language", state.language)
      .apply()
  }

  fun syncTrackAndLyricsState(
    context: Context,
    song: Song?,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    lyricsResult: LyricsResult?,
    lyricOffsetMs: Long = 0L
  ) {
    val appCtx = context.applicationContext
    val current = readSnapshot(appCtx)
    val localizedCtx = LocaleHelper.wrapBaseContext(appCtx, current.language)
    val noLyricsLabel = localizedCtx.getString(R.string.widget_no_lyrics_for_song)

    if (song == null) {
      val updated = current.copy(
        isPlaying = false,
        positionMs = 0L
      )
      saveSnapshot(appCtx, updated)
      refreshAllWidgetsAsync(appCtx, refreshMusic = true, refreshLyrics = true)
      return
    }

    val rawLrc = lyricsResult?.rawLrc.orEmpty()
    val plainLyrics = lyricsResult?.plain.orEmpty()
    val syncedList = lyricsResult?.synced ?: parseRawLrcLines(rawLrc)
    val lyricSnap = resolveSyncedLyricLines(
      syncedLines = syncedList,
      plainLyrics = plainLyrics,
      positionMs = positionMs,
      lyricOffsetMs = lyricOffsetMs,
      noLyricsLabel = noLyricsLabel
    )

    val trackChanged = current.songId != song.id || current.artworkUrl != song.artworkUrl
    val lyricsChanged = current.hasLyrics != lyricSnap.hasLyrics ||
      current.currentLyricLine != lyricSnap.currentLine ||
      current.nextLyricLine != lyricSnap.nextLine ||
      current.songId != song.id

    val effectiveDur = durationMs.takeIf { it > 0L }
      ?: (song.fullDurationSeconds.takeIf { it > 0L } ?: song.durationSeconds) * 1000L

    val updated = current.copy(
      songId = song.id,
      title = song.title,
      artist = song.artist,
      artworkUrl = song.artworkUrl,
      isPlaying = isPlaying,
      isFavorite = song.isFavorite,
      positionMs = positionMs.coerceAtLeast(0L),
      durationMs = effectiveDur.coerceAtLeast(1000L),
      hasLyrics = lyricSnap.hasLyrics,
      currentLyricLine = lyricSnap.currentLine,
      nextLyricLine = lyricSnap.nextLine,
      activeLyricIndex = lyricSnap.activeIndex,
      rawLrc = rawLrc,
      plainLyrics = plainLyrics,
      lyricOffsetMs = lyricOffsetMs
    )
    saveSnapshot(appCtx, updated)
    refreshAllWidgetsAsync(
      context = appCtx,
      refreshMusic = true,
      refreshLyrics = trackChanged || lyricsChanged,
      forceArtworkReload = trackChanged
    )
  }

  /**
   * Called ~every 1 second ONLY while playing.
   * Updates Music widget progress bar and updates Lyrics widget ONLY if the synced line changed.
   */
  fun onPlaybackProgressTick(
    context: Context,
    positionMs: Long,
    durationMs: Long,
    isPlaying: Boolean
  ) {
    if (!isPlaying) return
    val appCtx = context.applicationContext
    val current = readSnapshot(appCtx)
    if (current.songId.isBlank()) return

    val localizedCtx = LocaleHelper.wrapBaseContext(appCtx, current.language)
    val noLyricsLabel = localizedCtx.getString(R.string.widget_no_lyrics_for_song)
    val syncedList = parseRawLrcLines(current.rawLrc)
    val lyricSnap = resolveSyncedLyricLines(
      syncedLines = syncedList,
      plainLyrics = current.plainLyrics,
      positionMs = positionMs,
      lyricOffsetMs = current.lyricOffsetMs,
      noLyricsLabel = noLyricsLabel
    )

    val lineChanged = lyricSnap.activeIndex != current.activeLyricIndex ||
      lyricSnap.currentLine != current.currentLyricLine

    val effectiveDur = durationMs.takeIf { it > 0L } ?: current.durationMs.coerceAtLeast(1000L)
    val updated = current.copy(
      isPlaying = true,
      positionMs = positionMs.coerceAtLeast(0L),
      durationMs = effectiveDur,
      hasLyrics = lyricSnap.hasLyrics,
      currentLyricLine = lyricSnap.currentLine,
      nextLyricLine = lyricSnap.nextLine,
      activeLyricIndex = lyricSnap.activeIndex
    )
    saveSnapshot(appCtx, updated)
    refreshAllWidgetsAsync(
      context = appCtx,
      refreshMusic = true,
      refreshLyrics = lineChanged,
      forceArtworkReload = false
    )
  }

  fun syncFavoriteState(context: Context, songId: String, isFavorite: Boolean) {
    val appCtx = context.applicationContext
    val current = readSnapshot(appCtx)
    if (current.songId == songId && current.isFavorite != isFavorite) {
      saveSnapshot(appCtx, current.copy(isFavorite = isFavorite))
      refreshAllWidgetsAsync(appCtx, refreshMusic = true, refreshLyrics = false)
    }
  }

  fun syncAmbientGlowAndSettings(
    context: Context,
    ambientGlowEnabled: Boolean? = null,
    dimLevel: Float? = null,
    glassLevel: Float? = null,
    blurLevel: Float? = null,
    themeMode: String? = null,
    language: String? = null,
    refreshWidgetsNow: Boolean = true
  ) {
    val appCtx = context.applicationContext
    val current = readSnapshot(appCtx)
    val nextLang = language ?: current.language
    val localizedCtx = LocaleHelper.wrapBaseContext(appCtx, nextLang)
    val noLyricsLabel = localizedCtx.getString(R.string.widget_no_lyrics_for_song)

    val syncedList = parseRawLrcLines(current.rawLrc)
    val lyricSnap = if (current.songId.isNotBlank()) {
      resolveSyncedLyricLines(
        syncedLines = syncedList,
        plainLyrics = current.plainLyrics,
        positionMs = current.positionMs,
        lyricOffsetMs = current.lyricOffsetMs,
        noLyricsLabel = noLyricsLabel
      )
    } else null

    val updated = current.copy(
      ambientGlowEnabled = ambientGlowEnabled ?: current.ambientGlowEnabled,
      dimLevel = (dimLevel ?: current.dimLevel).coerceIn(0f, 0.90f),
      glassLevel = (glassLevel ?: current.glassLevel).coerceIn(0f, 1.00f),
      blurLevel = (blurLevel ?: current.blurLevel).coerceIn(0f, 100f),
      themeMode = themeMode ?: current.themeMode,
      language = nextLang,
      currentLyricLine = lyricSnap?.currentLine ?: current.currentLyricLine,
      nextLyricLine = lyricSnap?.nextLine ?: current.nextLyricLine,
      hasLyrics = lyricSnap?.hasLyrics ?: current.hasLyrics
    )
    saveSnapshot(appCtx, updated)
    if (refreshWidgetsNow) {
      refreshAllWidgetsAsync(appCtx, refreshMusic = true, refreshLyrics = true)
    }
  }

  fun refreshAllWidgetsAsync(
    context: Context,
    refreshMusic: Boolean = true,
    refreshLyrics: Boolean = true,
    forceArtworkReload: Boolean = false
  ) {
    val appCtx = context.applicationContext
    widgetScope.launch {
      runCatching {
        refreshWidgetsBlocking(
          context = appCtx,
          refreshMusic = refreshMusic,
          refreshLyrics = refreshLyrics,
          forceArtworkReload = forceArtworkReload
        )
      }
    }
  }

  suspend fun refreshWidgetsBlocking(
    context: Context,
    refreshMusic: Boolean = true,
    refreshLyrics: Boolean = true,
    forceArtworkReload: Boolean = false
  ) {
    val appCtx = context.applicationContext
    val manager = AppWidgetManager.getInstance(appCtx)
    val musicIds = if (refreshMusic) {
      runCatching {
        manager.getAppWidgetIds(ComponentName(appCtx, AurixMusicWidgetProvider::class.java))
      }.getOrDefault(IntArray(0))
    } else IntArray(0)

    val lyricsIds = if (refreshLyrics) {
      runCatching {
        manager.getAppWidgetIds(ComponentName(appCtx, AurixLyricsWidgetProvider::class.java))
      }.getOrDefault(IntArray(0))
    } else IntArray(0)

    if (musicIds.isEmpty() && lyricsIds.isEmpty()) return

    val snapshot = readSnapshot(appCtx)
    val rawArt = loadOrRestoreRawArtwork(appCtx, snapshot, forceArtworkReload)
    val glowBg = getOrRenderBackgroundBitmap(appCtx, snapshot, rawArt)
    val roundedArt = getOrRenderRoundedAlbumArt(appCtx, rawArt)

    if (musicIds.isNotEmpty()) {
      for (widgetId in musicIds) {
        val options = runCatching { manager.getAppWidgetOptions(widgetId) }.getOrNull()
        val views = buildMusicWidgetRemoteViews(appCtx, snapshot, glowBg, roundedArt, options)
        runCatching { manager.updateAppWidget(widgetId, views) }
      }
    }

    if (lyricsIds.isNotEmpty()) {
      for (widgetId in lyricsIds) {
        val views = buildLyricsWidgetRemoteViews(appCtx, snapshot, glowBg)
        runCatching { manager.updateAppWidget(widgetId, views) }
      }
    }
  }

  fun updateSpecificMusicWidgets(
    context: Context,
    appWidgetManager: AppWidgetManager,
    appWidgetIds: IntArray
  ) {
    val appCtx = context.applicationContext
    widgetScope.launch {
      runCatching {
        val snapshot = readSnapshot(appCtx)
        val rawArt = loadOrRestoreRawArtwork(appCtx, snapshot, forceReload = false)
        val glowBg = getOrRenderBackgroundBitmap(appCtx, snapshot, rawArt)
        val roundedArt = getOrRenderRoundedAlbumArt(appCtx, rawArt)
        for (widgetId in appWidgetIds) {
          val options = runCatching { appWidgetManager.getAppWidgetOptions(widgetId) }.getOrNull()
          val views = buildMusicWidgetRemoteViews(appCtx, snapshot, glowBg, roundedArt, options)
          runCatching { appWidgetManager.updateAppWidget(widgetId, views) }
        }
      }
    }
  }

  fun updateSpecificLyricsWidgets(
    context: Context,
    appWidgetManager: AppWidgetManager,
    appWidgetIds: IntArray
  ) {
    val appCtx = context.applicationContext
    widgetScope.launch {
      runCatching {
        val snapshot = readSnapshot(appCtx)
        val rawArt = loadOrRestoreRawArtwork(appCtx, snapshot, forceReload = false)
        val glowBg = getOrRenderBackgroundBitmap(appCtx, snapshot, rawArt)
        for (widgetId in appWidgetIds) {
          val views = buildLyricsWidgetRemoteViews(appCtx, snapshot, glowBg)
          runCatching { appWidgetManager.updateAppWidget(widgetId, views) }
        }
      }
    }
  }

  private fun buildMusicWidgetRemoteViews(
    context: Context,
    state: WidgetSnapshotState,
    glowBg: Bitmap,
    roundedArt: Bitmap,
    options: Bundle?
  ): RemoteViews {
    val localizedCtx = LocaleHelper.wrapBaseContext(context, state.language)
    val views = RemoteViews(context.packageName, R.layout.widget_aurix_music)
    val isLight = isLightThemeActive(context, state.themeMode) && !state.ambientGlowEnabled
    val primaryTextColor = if (isLight) COLOR_TEXT_LIGHT else COLOR_TEXT_DARK
    val mutedTextColor = if (isLight) COLOR_MUTED_LIGHT else COLOR_MUTED_DARK

    views.setImageViewBitmap(R.id.widget_music_bg_glow, glowBg)

    val openPlayerPi = buildOpenAppPendingIntent(
      context = context,
      requestCode = 7101,
      openScreen = AppNotificationHelper.SCREEN_NOW_PLAYING_PLAYER
    )

    if (state.songId.isBlank() || state.title.isBlank()) {
      views.setViewVisibility(R.id.widget_music_empty_container, View.VISIBLE)
      views.setViewVisibility(R.id.widget_music_content_container, View.GONE)
      views.setTextViewText(
        R.id.widget_music_empty_text,
        localizedCtx.getString(R.string.widget_open_aurix)
      )
      views.setTextColor(R.id.widget_music_empty_text, primaryTextColor)
      views.setOnClickPendingIntent(R.id.widget_music_root, openPlayerPi)
      views.setOnClickPendingIntent(R.id.widget_music_empty_container, openPlayerPi)
      return views
    }

    views.setViewVisibility(R.id.widget_music_empty_container, View.GONE)
    views.setViewVisibility(R.id.widget_music_content_container, View.VISIBLE)

    views.setImageViewBitmap(R.id.widget_music_album_art, roundedArt)
    views.setOnClickPendingIntent(R.id.widget_music_album_art, openPlayerPi)
    views.setOnClickPendingIntent(R.id.widget_music_meta_container, openPlayerPi)

    views.setTextViewText(R.id.widget_music_title, state.title)
    views.setTextViewText(R.id.widget_music_artist, state.artist.ifBlank { "AURIX" })
    views.setTextColor(R.id.widget_music_title, primaryTextColor)
    views.setTextColor(R.id.widget_music_artist, mutedTextColor)

    val progressPermille = if (state.durationMs > 0L) {
      ((state.positionMs.toDouble() / state.durationMs.toDouble()) * 1000.0)
        .roundToInt()
        .coerceIn(0, 1000)
    } else 0
    views.setProgressBar(R.id.widget_music_progress, 1000, progressPermille, false)

    // Translucent glass fill + thin border on control buttons
    val standardBtnGlass = renderGlassPillBitmap(
      widthPx = 140,
      heightPx = 76,
      cornerRadiusPx = 38f,
      glassLevel = state.glassLevel,
      isHighlighted = false,
      isLightTheme = isLight
    )
    val playBtnGlass = renderGlassPillBitmap(
      widthPx = 160,
      heightPx = 76,
      cornerRadiusPx = 38f,
      glassLevel = state.glassLevel,
      isHighlighted = state.isPlaying,
      isLightTheme = isLight
    )

    views.setImageViewBitmap(R.id.widget_btn_prev_bg, standardBtnGlass)
    views.setImageViewBitmap(R.id.widget_btn_play_pause_bg, playBtnGlass)
    views.setImageViewBitmap(R.id.widget_btn_next_bg, standardBtnGlass)
    views.setImageViewBitmap(R.id.widget_btn_like_bg, standardBtnGlass)

    views.setImageViewResource(
      R.id.widget_btn_play_pause_icon,
      if (state.isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play
    )
    views.setImageViewResource(
      R.id.widget_btn_like_icon,
      if (state.isFavorite) R.drawable.ic_widget_heart_filled else R.drawable.ic_widget_heart_outline
    )

    // Content descriptions localized to selected language
    views.setContentDescription(R.id.widget_music_album_art, localizedCtx.getString(R.string.widget_cd_album_art))
    views.setContentDescription(R.id.widget_btn_prev_icon, localizedCtx.getString(R.string.widget_cd_prev))
    views.setContentDescription(R.id.widget_btn_play_pause_icon, localizedCtx.getString(R.string.widget_cd_play_pause))
    views.setContentDescription(R.id.widget_btn_next_icon, localizedCtx.getString(R.string.widget_cd_next))
    views.setContentDescription(R.id.widget_btn_like_icon, localizedCtx.getString(R.string.widget_cd_like))

    // Responsive width: Below 3 cells wide (< 180dp), hide Like
    val minWidthDp = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250) ?: 250
    views.setViewVisibility(
      R.id.widget_btn_like,
      if (shouldShowLikeButton(minWidthDp)) View.VISIBLE else View.GONE
    )

    views.setOnClickPendingIntent(
      R.id.widget_btn_prev,
      buildWidgetActionPendingIntent(context, ACTION_WIDGET_PREV, 7201)
    )
    views.setOnClickPendingIntent(
      R.id.widget_btn_play_pause,
      buildWidgetActionPendingIntent(context, ACTION_WIDGET_PLAY_PAUSE, 7202)
    )
    views.setOnClickPendingIntent(
      R.id.widget_btn_next,
      buildWidgetActionPendingIntent(context, ACTION_WIDGET_NEXT, 7203)
    )
    views.setOnClickPendingIntent(
      R.id.widget_btn_like,
      buildWidgetActionPendingIntent(context, ACTION_WIDGET_LIKE, 7204)
    )

    return views
  }

  private fun buildLyricsWidgetRemoteViews(
    context: Context,
    state: WidgetSnapshotState,
    glowBg: Bitmap
  ): RemoteViews {
    val localizedCtx = LocaleHelper.wrapBaseContext(context, state.language)
    val views = RemoteViews(context.packageName, R.layout.widget_aurix_lyrics)
    val isLight = isLightThemeActive(context, state.themeMode) && !state.ambientGlowEnabled
    val primaryTextColor = if (isLight) COLOR_TEXT_LIGHT else COLOR_TEXT_DARK
    val mutedTextColor = if (isLight) COLOR_MUTED_LIGHT else COLOR_MUTED_DARK

    views.setImageViewBitmap(R.id.widget_lyrics_bg_glow, glowBg)

    val openLyricsPi = buildOpenAppPendingIntent(
      context = context,
      requestCode = 7301,
      openScreen = AppNotificationHelper.SCREEN_NOW_PLAYING_LYRICS
    )
    views.setOnClickPendingIntent(R.id.widget_lyrics_root, openLyricsPi)

    if (state.songId.isBlank() || state.title.isBlank()) {
      views.setViewVisibility(R.id.widget_lyrics_empty_container, View.VISIBLE)
      views.setViewVisibility(R.id.widget_lyrics_content_container, View.GONE)
      views.setTextViewText(
        R.id.widget_lyrics_empty_text,
        localizedCtx.getString(R.string.widget_open_aurix)
      )
      views.setTextColor(R.id.widget_lyrics_empty_text, primaryTextColor)
      views.setOnClickPendingIntent(R.id.widget_lyrics_empty_container, openLyricsPi)
      return views
    }

    views.setViewVisibility(R.id.widget_lyrics_empty_container, View.GONE)
    views.setViewVisibility(R.id.widget_lyrics_content_container, View.VISIBLE)
    views.setOnClickPendingIntent(R.id.widget_lyrics_content_container, openLyricsPi)

    val noLyricsLabel = localizedCtx.getString(R.string.widget_no_lyrics_for_song)
    val hasLyrics = state.hasLyrics && state.currentLyricLine.isNotBlank()
    val displayCurrentLine = if (hasLyrics) state.currentLyricLine else noLyricsLabel
    val displayNextLine = if (hasLyrics) state.nextLyricLine else ""

    if (hasLyrics) {
      views.setViewVisibility(R.id.widget_lyrics_tag_container, View.VISIBLE)
      views.setTextViewText(
        R.id.widget_lyrics_tag_text,
        localizedCtx.getString(R.string.widget_lyrics_tag)
      )
      val tagGlassBitmap = renderGlassPillBitmap(
        widthPx = 150,
        heightPx = 46,
        cornerRadiusPx = 23f,
        glassLevel = state.glassLevel,
        isHighlighted = true,
        isLightTheme = isLight
      )
      views.setImageViewBitmap(R.id.widget_lyrics_tag_bg, tagGlassBitmap)
    } else {
      views.setViewVisibility(R.id.widget_lyrics_tag_container, View.GONE)
    }

    // Use bundled Noto Sans Khmer typeface with extra line height when Khmer text is displayed
    val usesKhmerFont = containsKhmerScript(displayCurrentLine) ||
      containsKhmerScript(displayNextLine) ||
      (!hasLyrics && state.language.equals("km", ignoreCase = true))

    if (usesKhmerFont) {
      val khmerBmp = renderKhmerLyricsLinesBitmap(
        context = context,
        currentLine = displayCurrentLine,
        nextLine = displayNextLine,
        primaryColor = primaryTextColor,
        mutedColor = mutedTextColor
      )
      if (khmerBmp != null) {
        views.setViewVisibility(R.id.widget_lyrics_khmer_bitmap, View.VISIBLE)
        views.setViewVisibility(R.id.widget_lyrics_text_container, View.GONE)
        views.setImageViewBitmap(R.id.widget_lyrics_khmer_bitmap, khmerBmp)
      } else {
        bindStandardLyricsTextViews(views, displayCurrentLine, displayNextLine, primaryTextColor, mutedTextColor)
      }
    } else {
      bindStandardLyricsTextViews(views, displayCurrentLine, displayNextLine, primaryTextColor, mutedTextColor)
    }

    val footer = if (state.artist.isNotBlank()) "${state.title} · ${state.artist}" else state.title
    views.setTextViewText(R.id.widget_lyrics_song_footer, footer)
    views.setTextColor(R.id.widget_lyrics_song_footer, mutedTextColor)

    return views
  }

  private fun bindStandardLyricsTextViews(
    views: RemoteViews,
    currentLine: String,
    nextLine: String,
    primaryTextColor: Int,
    mutedTextColor: Int
  ) {
    views.setViewVisibility(R.id.widget_lyrics_khmer_bitmap, View.GONE)
    views.setViewVisibility(R.id.widget_lyrics_text_container, View.VISIBLE)
    views.setTextViewText(R.id.widget_lyrics_current_line, currentLine)
    views.setTextColor(R.id.widget_lyrics_current_line, primaryTextColor)
    if (nextLine.isNotBlank()) {
      views.setViewVisibility(R.id.widget_lyrics_next_line, View.VISIBLE)
      views.setTextViewText(R.id.widget_lyrics_next_line, nextLine)
      views.setTextColor(R.id.widget_lyrics_next_line, mutedTextColor)
    } else {
      views.setViewVisibility(R.id.widget_lyrics_next_line, View.GONE)
    }
  }

  private fun renderKhmerLyricsLinesBitmap(
    context: Context,
    currentLine: String,
    nextLine: String,
    primaryColor: Int,
    mutedColor: Int
  ): Bitmap? {
    return runCatching {
      val density = context.resources.displayMetrics.density.coerceAtLeast(1.5f)
      val widthPx = (310f * density).roundToInt().coerceIn(360, 960)
      val heightPx = (66f * density).roundToInt().coerceIn(96, 240)
      val bmp = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
      val canvas = Canvas(bmp)

      val khmerTypeface = runCatching {
        ResourcesCompat.getFont(context, R.font.noto_sans_khmer)
      }.getOrNull() ?: Typeface.DEFAULT

      val currentPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(khmerTypeface, Typeface.BOLD)
        color = primaryColor
        textSize = 15.5f * density
      }

      val currentLayout = StaticLayout.Builder
        .obtain(currentLine, 0, currentLine.length, currentPaint, widthPx)
        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
        .setLineSpacing(3.5f * density, 1.24f)
        .setIncludePad(true)
        .setMaxLines(if (nextLine.isNotBlank()) 1 else 2)
        .setEllipsize(TextUtils.TruncateAt.END)
        .build()

      val nextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(khmerTypeface, Typeface.NORMAL)
        color = mutedColor
        textSize = 12.5f * density
      }

      val nextLayout = if (nextLine.isNotBlank()) {
        StaticLayout.Builder
          .obtain(nextLine, 0, nextLine.length, nextPaint, widthPx)
          .setAlignment(Layout.Alignment.ALIGN_NORMAL)
          .setLineSpacing(3f * density, 1.22f)
          .setIncludePad(true)
          .setMaxLines(1)
          .setEllipsize(TextUtils.TruncateAt.END)
          .build()
      } else null

      val gapPx = if (nextLayout != null) (3f * density) else 0f
      val totalTextHeight = currentLayout.height + (nextLayout?.height ?: 0) + gapPx
      val startY = ((heightPx - totalTextHeight) * 0.5f).coerceAtLeast(0f)

      canvas.save()
      canvas.translate(0f, startY)
      currentLayout.draw(canvas)
      if (nextLayout != null) {
        canvas.translate(0f, currentLayout.height + gapPx)
        nextLayout.draw(canvas)
      }
      canvas.restore()
      bmp
    }.getOrNull()
  }

  private fun parseRawLrcLines(rawLrc: String): List<LyricLine> {
    if (rawLrc.isBlank()) return emptyList()
    return runCatching {
      val doc = LrcParser.parse(rawLrc)
      if (!doc.isSynced) emptyList()
      else doc.lines.map { LyricLine(it.timestampMs, it.text) }
    }.getOrDefault(emptyList())
  }

  private fun isLightThemeActive(context: Context, themeMode: String): Boolean {
    return when (themeMode.lowercase()) {
      "light" -> true
      "dark", "amoled" -> false
      else -> {
        val nightMask = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        nightMask != Configuration.UI_MODE_NIGHT_YES
      }
    }
  }

  private suspend fun loadOrRestoreRawArtwork(
    context: Context,
    state: WidgetSnapshotState,
    forceReload: Boolean
  ): Bitmap? {
    val cacheDir = File(context.filesDir, CACHE_DIR_NAME).apply { mkdirs() }
    val diskArtFile = File(cacheDir, CACHED_RAW_ART_FILE)
    val artKey = "${state.songId}|${state.artworkUrl}"

    if (!forceReload && artKey == lastLoadedArtworkKey && cachedRawArtworkBitmap != null) {
      return cachedRawArtworkBitmap
    }

    if (state.artworkUrl.isNotBlank() && state.artworkUrl != APP_ICON_ARTWORK) {
      val fetched = runCatching {
        val req = ImageRequest.Builder(context)
          .data(state.artworkUrl)
          .allowHardware(false)
          .size(256)
          .build()
        val res = context.imageLoader.execute(req)
        (res as? SuccessResult)?.drawable?.toBitmap(256, 256, Bitmap.Config.ARGB_8888)
      }.getOrNull()

      if (fetched != null) {
        lastLoadedArtworkKey = artKey
        cachedRawArtworkBitmap = fetched
        cachedRoundedArtBitmap = null
        runCatching {
          FileOutputStream(diskArtFile).use { out ->
            fetched.compress(Bitmap.CompressFormat.PNG, 92, out)
          }
        }
        return fetched
      }
    }

    if (diskArtFile.exists() && state.songId.isNotBlank()) {
      val restored = runCatching { BitmapFactory.decodeFile(diskArtFile.absolutePath) }.getOrNull()
      if (restored != null) {
        lastLoadedArtworkKey = artKey
        cachedRawArtworkBitmap = restored
        return restored
      }
    }

    return null
  }

  private fun getOrRenderRoundedAlbumArt(context: Context, rawArt: Bitmap?): Bitmap {
    if (rawArt != null && cachedRoundedArtBitmap != null) {
      return cachedRoundedArtBitmap!!
    }
    val cacheDir = File(context.filesDir, CACHE_DIR_NAME).apply { mkdirs() }
    val diskRoundedFile = File(cacheDir, CACHED_ROUNDED_ART_FILE)

    val sizePx = 168
    val cornerPx = 32f
    val output = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(output)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val rect = RectF(0f, 0f, sizePx.toFloat(), sizePx.toFloat())

    if (rawArt != null) {
      canvas.drawRoundRect(rect, cornerPx, cornerPx, paint)
      paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
      val scaled = Bitmap.createScaledBitmap(rawArt, sizePx, sizePx, true)
      canvas.drawBitmap(scaled, 0f, 0f, paint)
      paint.xfermode = null
    } else {
      paint.shader = LinearGradient(
        0f,
        0f,
        sizePx.toFloat(),
        sizePx.toFloat(),
        intArrayOf(COLOR_SECOND_ACCENT, COLOR_ACCENT, COLOR_CARD_DARK),
        floatArrayOf(0f, 0.55f, 1f),
        Shader.TileMode.CLAMP
      )
      canvas.drawRoundRect(rect, cornerPx, cornerPx, paint)
      paint.shader = null
      runCatching {
        val orbiDrawable = ResourcesCompat.getDrawable(context.resources, R.drawable.ic_widget_orbi, null)
        if (orbiDrawable != null) {
          val pad = 34
          orbiDrawable.setBounds(pad, pad, sizePx - pad, sizePx - pad)
          orbiDrawable.draw(canvas)
        }
      }
    }

    // Subtle 1dp inner border
    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      style = Paint.Style.STROKE
      strokeWidth = 2f
      color = 0x38FFFFFF
    }
    canvas.drawRoundRect(RectF(1f, 1f, sizePx - 1f, sizePx - 1f), cornerPx, cornerPx, borderPaint)

    if (rawArt != null) {
      cachedRoundedArtBitmap = output
      runCatching {
        FileOutputStream(diskRoundedFile).use { out ->
          output.compress(Bitmap.CompressFormat.PNG, 92, out)
        }
      }
    }
    return output
  }

  /**
   * Pre-renders the 26dp rounded, 1dp bordered blurred & dimmed album glow bitmap
   * (or solid #151F30 background when Ambient glow is off) and caches it on disk
   * so the widget is never blank after a reboot.
   */
  private fun getOrRenderBackgroundBitmap(
    context: Context,
    state: WidgetSnapshotState,
    rawArt: Bitmap?
  ): Bitmap {
    val renderKey = buildString {
      append(state.songId)
      append('|').append(state.artworkUrl)
      append('|').append(state.ambientGlowEnabled)
      append('|').append((state.dimLevel * 100).roundToInt())
      append('|').append((state.blurLevel).roundToInt())
      append('|').append(state.themeMode)
      append('|').append(rawArt != null)
    }
    if (renderKey == cachedGlowRenderKey && cachedGlowBitmap != null) {
      return cachedGlowBitmap!!
    }

    val cacheDir = File(context.filesDir, CACHE_DIR_NAME).apply { mkdirs() }
    val diskGlowFile = File(cacheDir, CACHED_GLOW_BG_FILE)

    val widthPx = 640
    val heightPx = 300
    val density = context.resources.displayMetrics.density.coerceAtLeast(2f)
    val cornerRadiusPx = (26f * density).coerceIn(44f, 78f)
    val borderStrokePx = (1f * density).coerceIn(2f, 3.5f)

    val output = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(output)
    val rect = RectF(0f, 0f, widthPx.toFloat(), heightPx.toFloat())
    val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    // Draw 26dp rounded mask
    canvas.drawRoundRect(rect, cornerRadiusPx, cornerRadiusPx, maskPaint)
    val contentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
    }

    val layerBmp = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
    val layerCanvas = Canvas(layerBmp)

    val isLight = isLightThemeActive(context, state.themeMode) && !state.ambientGlowEnabled
    val isAmoled = state.themeMode.equals("amoled", ignoreCase = true)

    if (!state.ambientGlowEnabled) {
      // Ambient glow OFF: solid #151F30 background (or theme base)
      val solidBg = when {
        isLight -> COLOR_CARD_LIGHT
        isAmoled -> COLOR_BG_AMOLED
        else -> COLOR_CARD_DARK // #151F30
      }
      layerCanvas.drawColor(solidBg)
    } else {
      // Base deep background (#080C14 / #151F30)
      layerCanvas.drawColor(if (isAmoled) COLOR_BG_AMOLED else COLOR_CARD_DARK)

      if (rawArt != null) {
        val blurredArt = createBlurredGlowBitmap(rawArt, state.blurLevel, widthPx, heightPx)
        layerCanvas.drawBitmap(blurredArt, 0f, 0f, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
      } else {
        // Soft ambient palette glow (#43C7F4, #7965FF, #A855F7) when no cover bitmap is loaded
        val glowPaint1 = Paint(Paint.ANTI_ALIAS_FLAG).apply {
          shader = RadialGradient(
            widthPx * 0.24f,
            heightPx * 0.28f,
            widthPx * 0.72f,
            intArrayOf(0xAA43C7F4.toInt(), 0x557965FF, Color.TRANSPARENT),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP
          )
        }
        layerCanvas.drawRect(rect, glowPaint1)

        val glowPaint2 = Paint(Paint.ANTI_ALIAS_FLAG).apply {
          shader = RadialGradient(
            widthPx * 0.80f,
            heightPx * 0.76f,
            widthPx * 0.65f,
            intArrayOf(0x88A855F7.toInt(), Color.TRANSPARENT),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP
          )
        }
        layerCanvas.drawRect(rect, glowPaint2)
      }

      // Soft accent bloom overlay so album glow always feels cohesive with AURIX palette
      val bloomAlpha = ((1f - state.dimLevel * 0.5f) * 68f).roundToInt().coerceIn(22, 76)
      val bloomPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(
          0f,
          0f,
          widthPx.toFloat(),
          heightPx.toFloat(),
          intArrayOf(
            Color.argb(bloomAlpha, 0x43, 0xC7, 0xF4),
            Color.argb(bloomAlpha, 0x79, 0x65, 0xFF),
            Color.argb((bloomAlpha * 0.8f).roundToInt(), 0xA8, 0x55, 0xF7)
          ),
          floatArrayOf(0f, 0.5f, 1f),
          Shader.TileMode.CLAMP
        )
      }
      layerCanvas.drawRect(rect, bloomPaint)

      // Dim overlay (0-90%, default 40%) clamped to MIN_LEGIBLE_DIM_FLOOR so text is always readable
      val effectiveDim = state.dimLevel.coerceIn(0f, 0.90f).coerceAtLeast(MIN_LEGIBLE_DIM_FLOOR)
      val topDimAlpha = (effectiveDim * 235f).roundToInt().coerceIn(68, 232)
      val bottomDimAlpha = ((effectiveDim + 0.14f).coerceAtMost(0.94f) * 245f).roundToInt().coerceIn(96, 242)
      val dimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(
          0f,
          0f,
          0f,
          heightPx.toFloat(),
          intArrayOf(
            Color.argb(topDimAlpha, 0x08, 0x0C, 0x14),
            Color.argb(bottomDimAlpha, 0x08, 0x0C, 0x14)
          ),
          null,
          Shader.TileMode.CLAMP
        )
      }
      layerCanvas.drawRect(rect, dimPaint)
    }

    canvas.drawBitmap(layerBmp, 0f, 0f, contentPaint)

    // 1dp border (#273348 in Dark/AMOLED, #D0DCF0 in Light)
    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      style = Paint.Style.STROKE
      strokeWidth = borderStrokePx
      color = if (isLight) COLOR_BORDER_LIGHT else COLOR_BORDER_DARK
    }
    val inset = borderStrokePx * 0.5f
    canvas.drawRoundRect(
      RectF(inset, inset, widthPx - inset, heightPx - inset),
      cornerRadiusPx - inset,
      cornerRadiusPx - inset,
      borderPaint
    )

    cachedGlowRenderKey = renderKey
    cachedGlowBitmap = output
    runCatching {
      FileOutputStream(diskGlowFile).use { out ->
        output.compress(Bitmap.CompressFormat.PNG, 92, out)
      }
    }
    return output
  }

  /**
   * Renders a translucent white fill and thin 1dp border for widget buttons and the "Lyrics" tag,
   * controlled by Glass level (0-100%, default 50%).
   */
  private fun renderGlassPillBitmap(
    widthPx: Int,
    heightPx: Int,
    cornerRadiusPx: Float,
    glassLevel: Float,
    isHighlighted: Boolean,
    isLightTheme: Boolean
  ): Bitmap {
    val bmp = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val clampedGlass = glassLevel.coerceIn(0f, 1f)

    // Translucent fill scaled by glassLevel (0-100%)
    val baseFillAlpha = (14 + (clampedGlass * 68f).roundToInt()).coerceIn(10, 96)
    val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      style = Paint.Style.FILL
      color = when {
        isHighlighted -> Color.argb((baseFillAlpha + 18).coerceAtMost(115), 0x43, 0xC7, 0xF4)
        isLightTheme -> Color.argb(baseFillAlpha, 0x15, 0x1F, 0x30)
        else -> Color.argb(baseFillAlpha, 0xFF, 0xFF, 0xFF)
      }
    }
    val rect = RectF(1f, 1f, widthPx - 1f, heightPx - 1f)
    canvas.drawRoundRect(rect, cornerRadiusPx, cornerRadiusPx, fillPaint)

    // Thin 1dp translucent border scaled by glassLevel
    val borderAlpha = (24 + (clampedGlass * 110f).roundToInt()).coerceIn(20, 150)
    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      style = Paint.Style.STROKE
      strokeWidth = 2f
      color = when {
        isHighlighted -> Color.argb((borderAlpha + 28).coerceAtMost(190), 0x43, 0xC7, 0xF4)
        isLightTheme -> Color.argb(borderAlpha, 0x27, 0x33, 0x48)
        else -> Color.argb(borderAlpha, 0xFF, 0xFF, 0xFF)
      }
    }
    canvas.drawRoundRect(rect, cornerRadiusPx, cornerRadiusPx, borderPaint)
    return bmp
  }

  /**
   * Fast multi-pass box/stack blur on a downsampled copy of the album art,
   * scaled by Blur level (0-100%, default 60%).
   */
  private fun createBlurredGlowBitmap(
    source: Bitmap,
    blurPercent: Float,
    targetWidth: Int,
    targetHeight: Int
  ): Bitmap {
    val clampedBlur = blurPercent.coerceIn(0f, 100f)
    val sampleW = 96
    val sampleH = 54
    val small = Bitmap.createScaledBitmap(source, sampleW, sampleH, true)
      .copy(Bitmap.Config.ARGB_8888, true)

    val radius = ((clampedBlur / 100f) * 18f).roundToInt().coerceIn(1, 18)
    if (clampedBlur > 1f) {
      boxBlurInPlace(small, radius, passes = 3)
    }
    return Bitmap.createScaledBitmap(small, targetWidth, targetHeight, true)
  }

  private fun boxBlurInPlace(bitmap: Bitmap, radius: Int, passes: Int) {
    val w = bitmap.width
    val h = bitmap.height
    if (w <= 2 || h <= 2 || radius <= 0) return
    val pixels = IntArray(w * h)
    val temp = IntArray(w * h)
    bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

    repeat(passes) {
      // Horizontal pass
      for (y in 0 until h) {
        val rowOffset = y * w
        for (x in 0 until w) {
          var rSum = 0
          var gSum = 0
          var bSum = 0
          var count = 0
          val xStart = max(0, x - radius)
          val xEnd = min(w - 1, x + radius)
          for (ix in xStart..xEnd) {
            val c = pixels[rowOffset + ix]
            rSum += (c shr 16) and 0xFF
            gSum += (c shr 8) and 0xFF
            bSum += c and 0xFF
            count++
          }
          temp[rowOffset + x] = (0xFF shl 24) or
            ((rSum / count) shl 16) or
            ((gSum / count) shl 8) or
            (bSum / count)
        }
      }
      // Vertical pass
      for (x in 0 until w) {
        for (y in 0 until h) {
          var rSum = 0
          var gSum = 0
          var bSum = 0
          var count = 0
          val yStart = max(0, y - radius)
          val yEnd = min(h - 1, y + radius)
          for (iy in yStart..yEnd) {
            val c = temp[iy * w + x]
            rSum += (c shr 16) and 0xFF
            gSum += (c shr 8) and 0xFF
            bSum += c and 0xFF
            count++
          }
          pixels[y * w + x] = (0xFF shl 24) or
            ((rSum / count) shl 16) or
            ((gSum / count) shl 8) or
            (bSum / count)
        }
      }
    }
    bitmap.setPixels(pixels, 0, w, 0, 0, w, h)
  }

  private fun buildOpenAppPendingIntent(
    context: Context,
    requestCode: Int,
    openScreen: String
  ): PendingIntent {
    val intent = Intent(context, MainActivity::class.java).apply {
      flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
      putExtra(AppNotificationHelper.EXTRA_OPEN_SCREEN, openScreen)
    }
    return PendingIntent.getActivity(
      context,
      requestCode,
      intent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
  }

  private fun buildWidgetActionPendingIntent(
    context: Context,
    action: String,
    requestCode: Int
  ): PendingIntent {
    val intent = Intent(context, AurixMusicWidgetProvider::class.java).apply {
      this.action = action
    }
    return PendingIntent.getBroadcast(
      context,
      requestCode,
      intent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
  }

  fun dispatchWidgetControlToPlaybackService(context: Context, action: String) {
    val appCtx = context.applicationContext
    val serviceIntent = Intent(appCtx, PlaybackService::class.java).apply {
      this.action = action
    }
    runCatching {
      appCtx.startService(serviceIntent)
    }
  }
}
