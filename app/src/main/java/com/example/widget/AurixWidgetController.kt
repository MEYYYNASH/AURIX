package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import androidx.palette.graphics.Palette
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.Layout
import android.text.SpannableString
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
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
import java.util.concurrent.ConcurrentHashMap
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
  val language: String = "en",
  val lyricsFlipperSlot: Int = 0,
  val prevLyricLine: String = "",
  val prevNextLyricLine: String = "",
  val reducedMotion: Boolean = false,
  val lyricsWidgetAnimStyle: String = com.example.lyrics.LyricsAnimationStyle.DEFAULT_ID,
  val khmerLyricsFont: String = com.example.ui.theme.KhmerLyricsFont.DEFAULT_ID,
  val widgetStyle: String = WidgetStyle.DEFAULT_ID
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
  const val COLOR_TEXT_DARK = 0xFFFFFFFF.toInt()
  const val COLOR_TEXT_LIGHT = 0xFF0E1726.toInt()
  const val COLOR_MUTED_DARK = 0xB8FFFFFF.toInt()
  const val COLOR_MUTED_LIGHT = 0xFF526077.toInt()
  const val COLOR_BORDER_DARK = 0xFF273348.toInt()
  const val COLOR_BORDER_LIGHT = 0xFFD0DCF0.toInt()
  const val COLOR_HEART_RED = 0xFFEF4444.toInt()

  // Below 3 cells wide (< 180dp), hide Like button
  const val MIN_WIDTH_DP_FOR_LIKE = 180

  // Minimum legible dim floor so text remains readable at every Dim setting (0-90%)
  const val MIN_LEGIBLE_DIM_FLOOR = 0.28f

  /**
   * Lead compensation (ms) shared by both the zero-polling scheduler and synced lyric line
   * resolver for home screen widgets. Compensates Binder IPC + launcher layout + the initial
   * ramp-in of the entrance animation so each lyric lands right on the vocal beat.
   */
  const val WIDGET_LYRIC_LEAD_MS = 280L

  private const val PREFS_WIDGET_SNAPSHOT = "aurix_widget_snapshot_prefs"
  private const val CACHE_DIR_NAME = "widget_cache"
  private const val CACHED_RAW_ART_FILE = "last_album_art_v2.png"
  private const val CACHED_GLOW_BG_FILE = "last_glow_bg_v3.png"
  private const val CACHED_ROUNDED_ART_FILE = "last_rounded_art_v3.png"
  private const val MAX_PARTIAL_UPDATES_BEFORE_FULL_SYNC = 64

  @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
  private val widgetScope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))
  private val snapshotLock = Any()

  /**
   * Registered by PlaybackService while active so that whenever async LRCLIB lyrics finish
   * loading, custom Room lyrics are saved/deleted, or the lyric offset changes, the zero-polling
   * widget lyric transition scheduler immediately restarts without waiting for the next track.
   */
  @Volatile
  var onLyricsSyncStateChanged: (() -> Unit)? = null

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

  @Volatile
  private var cachedRawLrcKey: String = ""

  @Volatile
  private var cachedParsedLrcLines: List<LyricLine> = emptyList()

  @Volatile
  private var lastFullLyricsLayoutRes: Int = 0

  private val lastFullLyricsLayoutByWidgetId = ConcurrentHashMap<Int, Int>()
  private val lastLyricsHasSideFrameByWidgetId = ConcurrentHashMap<Int, Boolean>()
  private val lastLyricsSizeTierByWidgetId = ConcurrentHashMap<Int, WidgetSizeTier>()
  private val lastLyricsWidgetWidthDpByWidgetId = ConcurrentHashMap<Int, Int>()
  private val lastCoverSignatureByWidgetId = ConcurrentHashMap<Int, String>()
  private val lastMiniSignatureByWidgetId = ConcurrentHashMap<Int, String>()

  @Volatile
  private var partialUpdatesSinceFullLyrics: Int = 0

  internal fun resolveWidgetWidthDp(options: Bundle?): Int {
    val raw = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0) ?: 0
    return if (raw in 120..520) raw else 0
  }

  internal fun computeLyricsTextWidthDp(
    sizeTier: WidgetSizeTier,
    hasSideFrame: Boolean,
    widgetWidthDp: Int = 0
  ): Float {
    val effectiveWidgetWidth = if (widgetWidthDp in 120..520) {
      widgetWidthDp.toFloat()
    } else {
      when (sizeTier) {
        WidgetSizeTier.SMALL -> 182f
        WidgetSizeTier.WIDE -> 340f
        WidgetSizeTier.LARGE -> 360f
      }
    }
    val nonTextChromeDp = when (sizeTier) {
      WidgetSizeTier.SMALL -> 35f
      WidgetSizeTier.WIDE -> 28f + (if (hasSideFrame) 94f else 0f) + 13.5f
      WidgetSizeTier.LARGE -> 32f + (if (hasSideFrame) 116f else 0f) + 14.5f
    }
    return (effectiveWidgetWidth - nonTextChromeDp).coerceIn(136f, 400f)
  }

  internal fun computeLyricsFooterWidthDp(
    sizeTier: WidgetSizeTier,
    hasSideFrame: Boolean,
    widgetWidthDp: Int = 0
  ): Float {
    val effectiveWidgetWidth = if (widgetWidthDp in 120..520) {
      widgetWidthDp.toFloat()
    } else {
      when (sizeTier) {
        WidgetSizeTier.SMALL -> 182f
        WidgetSizeTier.WIDE -> 340f
        WidgetSizeTier.LARGE -> 360f
      }
    }
    val miniArtAndPaddingDp = when (sizeTier) {
      WidgetSizeTier.SMALL -> 46f
      WidgetSizeTier.WIDE -> 28f + if (hasSideFrame) 0f else 25f
      WidgetSizeTier.LARGE -> 32f + if (hasSideFrame) 0f else 30f
    }
    return (effectiveWidgetWidth - miniArtAndPaddingDp).coerceIn(132f, 440f)
  }

  internal fun computeLyricsActiveTextSizeDp(sizeTier: WidgetSizeTier): Float = when (sizeTier) {
    WidgetSizeTier.SMALL -> 17.0f
    WidgetSizeTier.WIDE -> 22.0f
    WidgetSizeTier.LARGE -> 23.0f
  }

  internal fun computeLyricsNextTextSizeDp(sizeTier: WidgetSizeTier): Float = when (sizeTier) {
    WidgetSizeTier.SMALL -> 13.0f
    WidgetSizeTier.WIDE -> 15.0f
    WidgetSizeTier.LARGE -> 15.5f
  }

  fun shouldShowLikeButton(minWidthDp: Int): Boolean {
    return minWidthDp <= 0 || minWidthDp >= MIN_WIDTH_DP_FOR_LIKE
  }

  fun nextLyricFlipperSlot(
    currentSlot: Int,
    animateTransition: Boolean,
    reducedMotion: Boolean
  ): Int {
    val safeCurrent = if (currentSlot == 1) 1 else 0
    return if (animateTransition && !reducedMotion) {
      1 - safeCurrent
    } else {
      safeCurrent
    }
  }

  fun resolveLyricsWidgetLayoutRes(styleId: String, reducedMotion: Boolean): Int {
    if (reducedMotion) return R.layout.widget_aurix_lyrics_instant
    return when (com.example.lyrics.LyricsAnimationStyle.fromId(styleId).widgetEffectiveStyle()) {
      com.example.lyrics.LyricsAnimationStyle.BLUR_FADE -> R.layout.widget_aurix_lyrics_fade
      com.example.lyrics.LyricsAnimationStyle.POP_ZOOM -> R.layout.widget_aurix_lyrics_pop
      else -> R.layout.widget_aurix_lyrics
    }
  }

  fun resolveLyricsWidgetLayoutRes(
    styleId: String,
    reducedMotion: Boolean,
    sizeTier: WidgetSizeTier
  ): Int {
    return when (sizeTier) {
      WidgetSizeTier.SMALL -> R.layout.widget_aurix_lyrics_small
      WidgetSizeTier.LARGE -> R.layout.widget_aurix_lyrics_large
      WidgetSizeTier.WIDE -> resolveLyricsWidgetLayoutRes(styleId, reducedMotion)
    }
  }

  fun resolveWidgetSizeTier(options: Bundle?): WidgetSizeTier {
    val minW = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 280) ?: 280
    val minH = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 110) ?: 110
    return WidgetSizeTier.fromDimensionsDp(minW, minH)
  }

  fun readWidgetCustomizerConfig(context: Context): WidgetCustomizerConfig {
    val prefs = context.applicationContext.getSharedPreferences(PREFS_WIDGET_SNAPSHOT, Context.MODE_PRIVATE)
    return WidgetCustomizerConfig(
      samePhoto = prefs.getBoolean("wc_same_photo", WidgetCustomizerConfig.DEFAULT_SAME_PHOTO),
      frameSource = WidgetPhotoSource.fromId(prefs.getString("wc_frame_source", null)),
      bgSource = WidgetPhotoSource.fromId(prefs.getString("wc_bg_source", null)),
      frameStyle = WidgetFrameStyle.fromId(prefs.getString("wc_frame_style", null)),
      frameColorHex = prefs.getString("wc_frame_color", WidgetCustomizerConfig.DEFAULT_FRAME_COLOR_HEX)
        ?: WidgetCustomizerConfig.DEFAULT_FRAME_COLOR_HEX,
      cornerRadius = prefs.getInt("wc_corner", WidgetCustomizerConfig.DEFAULT_CORNER),
      borderWidth = prefs.getInt("wc_border", WidgetCustomizerConfig.DEFAULT_BORDER),
      motion = WidgetFrameMotion.fromId(prefs.getString("wc_motion", null)),
      bgType = WidgetBgType.fromId(prefs.getString("wc_bg_type", null)),
      bgColorHex = prefs.getString("wc_bg_color", WidgetCustomizerConfig.DEFAULT_BG_COLOR_HEX)
        ?: WidgetCustomizerConfig.DEFAULT_BG_COLOR_HEX,
      blur = prefs.getInt("wc_blur", WidgetCustomizerConfig.DEFAULT_BLUR),
      darkness = prefs.getInt("wc_darkness", WidgetCustomizerConfig.DEFAULT_DARKNESS),
      photoRevision = prefs.getLong("wc_photo_rev", 0L)
    ).normalized()
  }

  private fun saveWidgetCustomizerConfigSnapshot(context: Context, config: WidgetCustomizerConfig) {
    val norm = config.normalized()
    val prefs = context.applicationContext.getSharedPreferences(PREFS_WIDGET_SNAPSHOT, Context.MODE_PRIVATE)
    prefs.edit()
      .putBoolean("wc_same_photo", norm.samePhoto)
      .putString("wc_frame_source", norm.frameSource.id)
      .putString("wc_bg_source", norm.bgSource.id)
      .putString("wc_frame_style", norm.frameStyle.id)
      .putString("wc_frame_color", norm.frameColorHex)
      .putInt("wc_corner", norm.cornerRadius)
      .putInt("wc_border", norm.borderWidth)
      .putString("wc_motion", norm.motion.id)
      .putString("wc_bg_type", norm.bgType.id)
      .putString("wc_bg_color", norm.bgColorHex)
      .putInt("wc_blur", norm.blur)
      .putInt("wc_darkness", norm.darkness)
      .putLong("wc_photo_rev", norm.photoRevision)
      .apply()
  }

  fun syncWidgetCustomizerConfig(
    context: Context,
    config: WidgetCustomizerConfig,
    refreshWidgetsNow: Boolean = true
  ) {
    val appCtx = context.applicationContext
    synchronized(snapshotLock) {
      saveWidgetCustomizerConfigSnapshot(appCtx, config)
    }
    WidgetBitmapBuilder.clearCache()
    if (refreshWidgetsNow) {
      refreshAllWidgetsAsync(
        context = appCtx,
        refreshMusic = false,
        refreshLyrics = true,
        refreshCover = false,
        refreshMini = false,
        forceArtworkReload = false
      )
    }
  }

  fun resolveMusicWidgetLayoutRes(style: WidgetStyle): Int = when (style) {
    WidgetStyle.GLASS -> R.layout.widget_aurix_music
    WidgetStyle.COLOR -> R.layout.widget_aurix_music_color
    WidgetStyle.MONO -> R.layout.widget_aurix_music_mono
  }

  fun resolveMusicPreviewLayoutRes(style: WidgetStyle): Int = when (style) {
    WidgetStyle.GLASS -> R.layout.widget_preview_aurix_music_glass
    WidgetStyle.COLOR -> R.layout.widget_preview_aurix_music_color
    WidgetStyle.MONO -> R.layout.widget_preview_aurix_music_mono
  }

  fun resolveLyricsPreviewLayoutRes(style: WidgetStyle): Int = when (style) {
    WidgetStyle.GLASS -> R.layout.widget_preview_aurix_lyrics_glass
    WidgetStyle.COLOR -> R.layout.widget_preview_aurix_lyrics_color
    WidgetStyle.MONO -> R.layout.widget_preview_aurix_lyrics_mono
  }

  fun resolveCoverPreviewLayoutRes(style: WidgetStyle): Int = when (style) {
    WidgetStyle.GLASS -> R.layout.widget_preview_aurix_cover_glass
    WidgetStyle.COLOR -> R.layout.widget_preview_aurix_cover_color
    WidgetStyle.MONO -> R.layout.widget_preview_aurix_cover_mono
  }

  fun resolveMiniPreviewLayoutRes(style: WidgetStyle): Int = when (style) {
    WidgetStyle.GLASS -> R.layout.widget_preview_aurix_mini_glass
    WidgetStyle.COLOR -> R.layout.widget_preview_aurix_mini_color
    WidgetStyle.MONO -> R.layout.widget_preview_aurix_mini_mono
  }

  fun syncWidgetStyle(
    context: Context,
    styleId: String,
    refreshWidgetsNow: Boolean = true
  ) {
    val appCtx = context.applicationContext
    val resolved = WidgetStyle.fromId(styleId)
    synchronized(snapshotLock) {
      val current = readSnapshot(appCtx)
      saveSnapshot(appCtx, current.copy(widgetStyle = resolved.id))
    }
    updateWidgetPickerPreviews(appCtx, resolved)
    if (refreshWidgetsNow) {
      refreshAllWidgetsAsync(
        context = appCtx,
        refreshMusic = true,
        refreshLyrics = true,
        refreshCover = true,
        refreshMini = true,
        forceArtworkReload = false
      )
    }
  }

  fun updateWidgetPickerPreviews(
    context: Context,
    style: WidgetStyle
  ) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return
    val appCtx = context.applicationContext
    val manager = runCatching { AppWidgetManager.getInstance(appCtx) }.getOrNull() ?: return
    val category = AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN
    val pkg = appCtx.packageName
    runCatching {
      manager.setWidgetPreview(
        ComponentName(appCtx, AurixMusicWidgetProvider::class.java),
        category,
        RemoteViews(pkg, resolveMusicPreviewLayoutRes(style))
      )
    }
    runCatching {
      manager.setWidgetPreview(
        ComponentName(appCtx, AurixLyricsWidgetProvider::class.java),
        category,
        RemoteViews(pkg, resolveLyricsPreviewLayoutRes(style))
      )
    }
    runCatching {
      manager.setWidgetPreview(
        ComponentName(appCtx, AurixCoverWidgetProvider::class.java),
        category,
        RemoteViews(pkg, resolveCoverPreviewLayoutRes(style))
      )
    }
    runCatching {
      manager.setWidgetPreview(
        ComponentName(appCtx, AurixMiniWidgetProvider::class.java),
        category,
        RemoteViews(pkg, resolveMiniPreviewLayoutRes(style))
      )
    }
  }

  /**
   * Developer Mode utility: clears cached bitmaps, rebuilds the composite Lyrics widget bitmap
   * immediately on Dispatchers.IO, updates all placed Lyrics widgets, and returns total build time (ms).
   */
  suspend fun rebuildLyricsWidgetBitmapNow(context: Context): Long = kotlinx.coroutines.withContext(Dispatchers.IO) {
    val appCtx = context.applicationContext
    WidgetBitmapBuilder.clearCache()
    val snapshot = readSnapshot(appCtx)
    val customizer = readWidgetCustomizerConfig(appCtx)
    val rawArt = loadOrRestoreRawArtwork(appCtx, snapshot, forceReload = false)
    val roundedArt = getOrRenderRoundedAlbumArt(appCtx, rawArt)
    val manager = AppWidgetManager.getInstance(appCtx)
    val lyricsIds = runCatching {
      manager.getAppWidgetIds(ComponentName(appCtx, AurixLyricsWidgetProvider::class.java))
    }.getOrDefault(IntArray(0))
    val songKey = "${snapshot.songId}|${snapshot.artworkUrl}"

    if (lyricsIds.isEmpty()) {
      val result = WidgetBitmapBuilder.buildWidgetCompositeBitmap(
        context = appCtx,
        config = customizer,
        sizeTier = WidgetSizeTier.WIDE,
        albumArtBitmap = rawArt,
        songArtworkKey = songKey,
        includeFrameInComposite = false,
        auroraPhase = 0f,
        forceRebuild = true
      )
      return@withContext result.buildTimeMs
    }

    var totalBuildMs = 0L
    val reduceMotion = isReducedMotionActive(appCtx, snapshot.reducedMotion)
    val standaloneFrameBmp = buildStandaloneSideFrameIfNeeded(appCtx, customizer, rawArt)
    for (widgetId in lyricsIds) {
      val options = runCatching { manager.getAppWidgetOptions(widgetId) }.getOrNull()
      val sizeTier = resolveWidgetSizeTier(options)
      val widgetWidthDp = resolveWidgetWidthDp(options)
      val built = WidgetBitmapBuilder.buildWidgetCompositeBitmap(
        context = appCtx,
        config = customizer,
        sizeTier = sizeTier,
        albumArtBitmap = rawArt,
        songArtworkKey = songKey,
        includeFrameInComposite = false,
        auroraPhase = 0f,
        forceRebuild = true
      )
      totalBuildMs += built.buildTimeMs
      val layoutRes = resolveLyricsWidgetLayoutRes(snapshot.lyricsWidgetAnimStyle, reduceMotion, sizeTier)
      val views = buildLyricsWidgetRemoteViews(
        context = appCtx,
        state = snapshot,
        compositeBg = built.bitmap,
        roundedArt = roundedArt,
        standaloneFrameBmp = standaloneFrameBmp,
        customizer = customizer,
        sizeTier = sizeTier,
        widgetWidthDp = widgetWidthDp
      )
      runCatching { manager.updateAppWidget(widgetId, views) }
      lastFullLyricsLayoutByWidgetId[widgetId] = layoutRes
      lastLyricsSizeTierByWidgetId[widgetId] = sizeTier
      lastLyricsWidgetWidthDpByWidgetId[widgetId] = widgetWidthDp
      lastLyricsHasSideFrameByWidgetId[widgetId] =
        customizer.frameStyle != WidgetFrameStyle.NONE && sizeTier != WidgetSizeTier.SMALL && standaloneFrameBmp != null
      lastFullLyricsLayoutRes = layoutRes
    }
    partialUpdatesSinceFullLyrics = 0
    totalBuildMs.coerceAtLeast(1L)
  }

  fun hasMusicWidgets(context: Context): Boolean {
    return runCatching {
      val manager = AppWidgetManager.getInstance(context.applicationContext)
      manager.getAppWidgetIds(ComponentName(context.applicationContext, AurixMusicWidgetProvider::class.java)).isNotEmpty()
    }.getOrDefault(false)
  }

  fun hasLyricsWidgets(context: Context): Boolean {
    return runCatching {
      val manager = AppWidgetManager.getInstance(context.applicationContext)
      manager.getAppWidgetIds(ComponentName(context.applicationContext, AurixLyricsWidgetProvider::class.java)).isNotEmpty()
    }.getOrDefault(false)
  }

  fun hasCoverWidgets(context: Context): Boolean {
    return runCatching {
      val manager = AppWidgetManager.getInstance(context.applicationContext)
      manager.getAppWidgetIds(ComponentName(context.applicationContext, AurixCoverWidgetProvider::class.java)).isNotEmpty()
    }.getOrDefault(false)
  }

  fun hasMiniWidgets(context: Context): Boolean {
    return runCatching {
      val manager = AppWidgetManager.getInstance(context.applicationContext)
      manager.getAppWidgetIds(ComponentName(context.applicationContext, AurixMiniWidgetProvider::class.java)).isNotEmpty()
    }.getOrDefault(false)
  }

  /**
   * Computes exact delay (ms) until the next synced LRC line begins so PlaybackService
   * can schedule a single wake-up per lyric line with zero polling.
   * Returns null when the current track has no further synced LRC lines.
   */
  fun computeDelayUntilNextLyricLineMs(
    context: Context,
    positionMs: Long,
    playbackSpeed: Float = 1f
  ): Long? {
    val snapshot = readSnapshot(context.applicationContext)
    if (snapshot.songId.isBlank() || snapshot.rawLrc.isBlank()) return null
    val synced = parseRawLrcLines(snapshot.rawLrc)
    return computeDelayUntilNextLyricLineMsForLines(
      syncedLines = synced,
      positionMs = positionMs,
      lyricOffsetMs = snapshot.lyricOffsetMs,
      playbackSpeed = playbackSpeed,
      leadMs = WIDGET_LYRIC_LEAD_MS
    )
  }

  fun computeDelayUntilNextLyricLineMsForLines(
    syncedLines: List<LyricLine>,
    positionMs: Long,
    lyricOffsetMs: Long = 0L,
    playbackSpeed: Float = 1f,
    leadMs: Long = WIDGET_LYRIC_LEAD_MS
  ): Long? {
    val cleanSynced = syncedLines.filter { it.text.isNotBlank() }
    if (cleanSynced.isEmpty()) return null
    val effectivePos = (positionMs + lyricOffsetMs + leadMs).coerceAtLeast(0L)
    val nextLine = cleanSynced.firstOrNull { it.timestampMs > effectivePos } ?: return null
    val deltaTrackMs = (nextLine.timestampMs - effectivePos).coerceAtLeast(1L)
    val safeSpeed = playbackSpeed.coerceIn(0.25f, 4.0f)
    // Add +20ms wake margin so player.currentPosition + lyricOffsetMs + leadMs is guaranteed
    // to have crossed nextLine.timestampMs on the very first wake-up (zero skipped lines).
    return ((deltaTrackMs / safeSpeed).toLong() + 20L).coerceAtLeast(24L)
  }

  private fun isReducedMotionActive(context: Context, appReducedMotion: Boolean): Boolean {
    if (appReducedMotion) return true
    val scale = runCatching {
      Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    }.getOrDefault(1f)
    return scale <= 0.01f
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
    noLyricsLabel: String = "No lyrics for this song",
    leadMs: Long = 0L
  ): SyncedLyricLineSnapshot {
    val cleanSynced = syncedLines?.filter { it.text.isNotBlank() }.orEmpty()
    if (cleanSynced.isNotEmpty()) {
      val effectivePos = (positionMs + lyricOffsetMs + leadMs).coerceAtLeast(0L)
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
      language = savedLang,
      lyricsFlipperSlot = prefs.getInt("lyrics_flipper_slot", 0).coerceIn(0, 1),
      prevLyricLine = prefs.getString("prev_lyric_line", "").orEmpty(),
      prevNextLyricLine = prefs.getString("prev_next_lyric_line", "").orEmpty(),
      reducedMotion = prefs.getBoolean("reduced_motion", false),
      lyricsWidgetAnimStyle = com.example.lyrics.LyricsAnimationStyle.fromId(
        prefs.getString("lyrics_widget_anim", null)
      ).id,
      khmerLyricsFont = com.example.ui.theme.KhmerLyricsFont.fromId(
        prefs.getString("khmer_lyrics_font", null)
      ).id,
      widgetStyle = WidgetStyle.fromId(
        prefs.getString("widget_style", null)
      ).id
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
      .putInt("lyrics_flipper_slot", state.lyricsFlipperSlot.coerceIn(0, 1))
      .putString("prev_lyric_line", state.prevLyricLine)
      .putString("prev_next_lyric_line", state.prevNextLyricLine)
      .putBoolean("reduced_motion", state.reducedMotion)
      .putString("lyrics_widget_anim", state.lyricsWidgetAnimStyle)
      .putString("khmer_lyrics_font", state.khmerLyricsFont)
      .putString("widget_style", WidgetStyle.fromId(state.widgetStyle).id)
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
    var shouldRestartScheduler = false
    var shouldRefreshMusic = true
    var shouldFullRefreshLyrics = false
    var shouldAnimateLyricsOnly = false
    var forceArtReload = false
    var snapshotToAnimate: WidgetSnapshotState? = null

    synchronized(snapshotLock) {
      val current = readSnapshot(appCtx)
      val localizedCtx = LocaleHelper.wrapBaseContext(appCtx, current.language)
      val noLyricsLabel = localizedCtx.getString(R.string.widget_no_lyrics_for_song)

      if (song == null) {
        val updated = current.copy(
          isPlaying = false,
          positionMs = 0L
        )
        saveSnapshot(appCtx, updated)
        shouldFullRefreshLyrics = true
      } else {
        val rawLrc = lyricsResult?.rawLrc.orEmpty()
        val plainLyrics = lyricsResult?.plain.orEmpty()
        val syncedList = lyricsResult?.synced ?: parseRawLrcLines(rawLrc)
        val lyricSnap = resolveSyncedLyricLines(
          syncedLines = syncedList,
          plainLyrics = plainLyrics,
          positionMs = positionMs,
          lyricOffsetMs = lyricOffsetMs,
          noLyricsLabel = noLyricsLabel,
          leadMs = WIDGET_LYRIC_LEAD_MS
        )

        val trackChanged = current.songId != song.id || current.artworkUrl != song.artworkUrl
        val lyricsSourceChanged = current.songId != song.id ||
          current.rawLrc != rawLrc ||
          current.plainLyrics != plainLyrics ||
          current.lyricOffsetMs != lyricOffsetMs ||
          current.hasLyrics != lyricSnap.hasLyrics
        val lineTextChanged = current.currentLyricLine != lyricSnap.currentLine ||
          current.nextLyricLine != lyricSnap.nextLine ||
          current.activeLyricIndex != lyricSnap.activeIndex

        val reduceMotion = isReducedMotionActive(appCtx, current.reducedMotion)
        val shouldAnimateLine = lineTextChanged &&
          current.currentLyricLine.isNotBlank() &&
          lyricSnap.currentLine.isNotBlank()
        val nextSlot = nextLyricFlipperSlot(
          currentSlot = current.lyricsFlipperSlot,
          animateTransition = shouldAnimateLine,
          reducedMotion = reduceMotion
        )

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
          lyricOffsetMs = lyricOffsetMs,
          lyricsFlipperSlot = nextSlot,
          prevLyricLine = if (shouldAnimateLine) current.currentLyricLine else current.prevLyricLine,
          prevNextLyricLine = if (shouldAnimateLine) current.nextLyricLine else current.prevNextLyricLine
        )
        saveSnapshot(appCtx, updated)

        forceArtReload = trackChanged
        if (trackChanged || lyricsSourceChanged || lastFullLyricsLayoutByWidgetId.isEmpty()) {
          shouldFullRefreshLyrics = trackChanged || lyricsSourceChanged || lineTextChanged
        } else if (lineTextChanged && updated.hasLyrics && isPlaying) {
          shouldAnimateLyricsOnly = true
          snapshotToAnimate = updated
        } else if (lineTextChanged) {
          shouldFullRefreshLyrics = true
        }

        if (rawLrc.isNotBlank() && (trackChanged || current.rawLrc != rawLrc || current.lyricOffsetMs != lyricOffsetMs)) {
          shouldRestartScheduler = true
        }
      }
    }

    if (shouldRefreshMusic || shouldFullRefreshLyrics) {
      refreshAllWidgetsAsync(
        context = appCtx,
        refreshMusic = shouldRefreshMusic,
        refreshLyrics = shouldFullRefreshLyrics,
        refreshCover = shouldRefreshMusic,
        refreshMini = shouldRefreshMusic,
        forceArtworkReload = forceArtReload
      )
    }
    if (shouldAnimateLyricsOnly) {
      snapshotToAnimate?.let { animateLyricsLineTransitionAsync(appCtx, it) }
    }
    if (shouldRestartScheduler) {
      onLyricsSyncStateChanged?.invoke()
    }
  }

  /**
   * Called while playing only.
   * Throttles Music widget progress updates to ~1s (via allowMusicUpdate) while detecting
   * synced lyric line changes on the beat and smoothly flipping only the active ViewFlipper slot
   * via partiallyUpdateAppWidget with zero card flicker.
   */
  fun onPlaybackProgressTick(
    context: Context,
    positionMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    allowMusicUpdate: Boolean = true,
    allowLyricsUpdate: Boolean = true
  ) {
    if (!isPlaying) return
    val appCtx = context.applicationContext
    var lineChanged = false
    var playStateChanged = false
    var updatedSnapshot: WidgetSnapshotState? = null

    synchronized(snapshotLock) {
      val current = readSnapshot(appCtx)
      if (current.songId.isBlank()) return

      val localizedCtx = LocaleHelper.wrapBaseContext(appCtx, current.language)
      val noLyricsLabel = localizedCtx.getString(R.string.widget_no_lyrics_for_song)
      val syncedList = if (allowLyricsUpdate) parseRawLrcLines(current.rawLrc) else emptyList()
      val lyricSnap = if (allowLyricsUpdate) {
        resolveSyncedLyricLines(
          syncedLines = syncedList,
          plainLyrics = current.plainLyrics,
          positionMs = positionMs,
          lyricOffsetMs = current.lyricOffsetMs,
          noLyricsLabel = noLyricsLabel,
          leadMs = WIDGET_LYRIC_LEAD_MS
        )
      } else null

      lineChanged = lyricSnap != null &&
        lyricSnap.hasLyrics &&
        (lyricSnap.activeIndex != current.activeLyricIndex || lyricSnap.currentLine != current.currentLyricLine)
      playStateChanged = !current.isPlaying

      if (!lineChanged && !allowMusicUpdate && !playStateChanged) return

      val reduceMotion = isReducedMotionActive(appCtx, current.reducedMotion)
      val nextSlot = nextLyricFlipperSlot(
        currentSlot = current.lyricsFlipperSlot,
        animateTransition = lineChanged,
        reducedMotion = reduceMotion
      )

      val effectiveDur = durationMs.takeIf { it > 0L } ?: current.durationMs.coerceAtLeast(1000L)
      val updated = current.copy(
        isPlaying = true,
        positionMs = positionMs.coerceAtLeast(0L),
        durationMs = effectiveDur,
        hasLyrics = lyricSnap?.hasLyrics ?: current.hasLyrics,
        currentLyricLine = lyricSnap?.currentLine ?: current.currentLyricLine,
        nextLyricLine = lyricSnap?.nextLine ?: current.nextLyricLine,
        activeLyricIndex = lyricSnap?.activeIndex ?: current.activeLyricIndex,
        lyricsFlipperSlot = nextSlot,
        prevLyricLine = if (lineChanged) current.currentLyricLine else current.prevLyricLine,
        prevNextLyricLine = if (lineChanged) current.nextLyricLine else current.prevNextLyricLine
      )
      saveSnapshot(appCtx, updated)
      updatedSnapshot = updated
    }

    if (allowMusicUpdate) {
      refreshAllWidgetsAsync(
        context = appCtx,
        refreshMusic = true,
        refreshLyrics = false,
        refreshCover = playStateChanged,
        refreshMini = true,
        forceArtworkReload = false
      )
    }
    if (lineChanged) {
      updatedSnapshot?.let { animateLyricsLineTransitionAsync(appCtx, it) }
    }
  }

  fun syncFavoriteState(context: Context, songId: String, isFavorite: Boolean) {
    val appCtx = context.applicationContext
    val changed = synchronized(snapshotLock) {
      val current = readSnapshot(appCtx)
      if (current.songId == songId && current.isFavorite != isFavorite) {
        saveSnapshot(appCtx, current.copy(isFavorite = isFavorite))
        true
      } else false
    }
    if (changed) {
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
    reducedMotion: Boolean? = null,
    lyricsWidgetAnimStyle: String? = null,
    khmerLyricsFont: String? = null,
    refreshWidgetsNow: Boolean = true
  ) {
    val appCtx = context.applicationContext
    synchronized(snapshotLock) {
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
          noLyricsLabel = noLyricsLabel,
          leadMs = WIDGET_LYRIC_LEAD_MS
        )
      } else null

      val resolvedAnim = lyricsWidgetAnimStyle?.let {
        com.example.lyrics.LyricsAnimationStyle.fromId(it).id
      } ?: current.lyricsWidgetAnimStyle

      val resolvedKhmerFont = khmerLyricsFont?.let {
        com.example.ui.theme.KhmerLyricsFont.fromId(it).id
      } ?: current.khmerLyricsFont

      val updated = current.copy(
        ambientGlowEnabled = ambientGlowEnabled ?: current.ambientGlowEnabled,
        dimLevel = (dimLevel ?: current.dimLevel).coerceIn(0f, 0.90f),
        glassLevel = (glassLevel ?: current.glassLevel).coerceIn(0f, 1.00f),
        blurLevel = (blurLevel ?: current.blurLevel).coerceIn(0f, 100f),
        themeMode = themeMode ?: current.themeMode,
        language = nextLang,
        reducedMotion = reducedMotion ?: current.reducedMotion,
        lyricsWidgetAnimStyle = resolvedAnim,
        khmerLyricsFont = resolvedKhmerFont,
        currentLyricLine = lyricSnap?.currentLine ?: current.currentLyricLine,
        nextLyricLine = lyricSnap?.nextLine ?: current.nextLyricLine,
        hasLyrics = lyricSnap?.hasLyrics ?: current.hasLyrics
      )
      saveSnapshot(appCtx, updated)
    }
    if (refreshWidgetsNow) {
      refreshAllWidgetsAsync(appCtx, refreshMusic = true, refreshLyrics = true)
    }
  }

  /**
   * Uses partiallyUpdateAppWidget to write ONLY the incoming ViewFlipper slot and call
   * setDisplayedChild(targetSlot), triggering the hardware-accelerated XML in/out
   * transition on the launcher without flickering or reloading the ambient glow background.
   * Falls back to a full updateAppWidget whenever the hosted layoutRes changed (e.g. after APK
   * update or animation style switch) or every N partial updates to prevent action-queue bloat
   * while preserving ViewFlipper animation via RemoteViews.reapply().
   */
  private fun animateLyricsLineTransitionAsync(context: Context, state: WidgetSnapshotState) {
    val appCtx = context.applicationContext
    widgetScope.launch {
      runCatching {
        val manager = AppWidgetManager.getInstance(appCtx)
        val lyricsIds = manager.getAppWidgetIds(ComponentName(appCtx, AurixLyricsWidgetProvider::class.java))
        if (lyricsIds.isEmpty()) return@launch

        val hasLyrics = state.hasLyrics && state.currentLyricLine.isNotBlank()
        // Songs without lyrics show no animation and no tag
        if (!hasLyrics) return@launch

        val reduceMotion = isReducedMotionActive(appCtx, state.reducedMotion)

        // Ensure every placed widget hosts its matching layoutRes and periodically reset
        // AppWidgetService's merged action queue.
        val needsFullSync = partialUpdatesSinceFullLyrics >= MAX_PARTIAL_UPDATES_BEFORE_FULL_SYNC ||
          lyricsIds.any { widgetId ->
            val options = runCatching { manager.getAppWidgetOptions(widgetId) }.getOrNull()
            val tier = resolveWidgetSizeTier(options)
            val expected = resolveLyricsWidgetLayoutRes(state.lyricsWidgetAnimStyle, reduceMotion, tier)
            lastFullLyricsLayoutByWidgetId[widgetId] != expected
          }

        if (needsFullSync) {
          refreshWidgetsBlocking(
            context = appCtx,
            refreshMusic = false,
            refreshLyrics = true,
            forceArtworkReload = false
          )
          return@launch
        }

        val widgetStyle = WidgetStyle.fromId(state.widgetStyle)
        val primaryTextColor = when (widgetStyle) {
          WidgetStyle.COLOR -> 0xFFF3EEFF.toInt()
          else -> 0xFFFFFFFF.toInt()
        }
        val mutedTextColor = when (widgetStyle) {
          WidgetStyle.COLOR -> 0x4DF3EEFF.toInt()
          else -> 0x4DFFFFFF.toInt()
        }
        val displayCurrentLine = state.currentLyricLine
        val displayNextLine = state.nextLyricLine
        val targetSlot = state.lyricsFlipperSlot.coerceIn(0, 1)
        val khmerFont = com.example.ui.theme.KhmerLyricsFont.fromId(state.khmerLyricsFont)

        for (widgetId in lyricsIds) {
          val liveOptions = runCatching { manager.getAppWidgetOptions(widgetId) }.getOrNull()
          val tier = lastLyricsSizeTierByWidgetId[widgetId] ?: resolveWidgetSizeTier(liveOptions)
          val widgetWidthDp = lastLyricsWidgetWidthDpByWidgetId[widgetId]
            ?.takeIf { it > 0 }
            ?: resolveWidgetWidthDp(liveOptions)
          val hasSideFrame = lastLyricsHasSideFrameByWidgetId[widgetId] ?: false
          val textWidthDp = computeLyricsTextWidthDp(tier, hasSideFrame, widgetWidthDp)
          val activeTextSizeDp = computeLyricsActiveTextSizeDp(tier)
          val nextTextSizeDp = computeLyricsNextTextSizeDp(tier)

          val layoutRes = lastFullLyricsLayoutByWidgetId[widgetId]
            ?: resolveLyricsWidgetLayoutRes(state.lyricsWidgetAnimStyle, reduceMotion, tier)
          val partialViews = RemoteViews(appCtx.packageName, layoutRes)
          // Animate ONLY the active lyric line inside ViewFlipper
          bindLyricsFlipperSlot(
            context = appCtx,
            views = partialViews,
            slotIndex = targetSlot,
            currentLine = displayCurrentLine,
            khmerLyricsFont = khmerFont,
            primaryTextColor = primaryTextColor,
            textWidthDp = textWidthDp,
            activeTextSizeDp = activeTextSizeDp
          )
          // Update the upcoming lyric line OUTSIDE ViewFlipper statically (zero premature animation)
          bindStaticNextLyricLine(
            context = appCtx,
            views = partialViews,
            nextLine = displayNextLine,
            khmerLyricsFont = khmerFont,
            mutedTextColor = mutedTextColor,
            textWidthDp = textWidthDp,
            nextTextSizeDp = nextTextSizeDp
          )
          partialViews.setDisplayedChild(R.id.widget_lyrics_flipper, targetSlot)
          runCatching { manager.partiallyUpdateAppWidget(widgetId, partialViews) }
        }
        partialUpdatesSinceFullLyrics += 1
      }
    }
  }

  fun refreshAllWidgetsAsync(
    context: Context,
    refreshMusic: Boolean = true,
    refreshLyrics: Boolean = true,
    refreshCover: Boolean = refreshMusic,
    refreshMini: Boolean = refreshMusic,
    forceArtworkReload: Boolean = false
  ) {
    val appCtx = context.applicationContext
    widgetScope.launch {
      runCatching {
        refreshWidgetsBlocking(
          context = appCtx,
          refreshMusic = refreshMusic,
          refreshLyrics = refreshLyrics,
          refreshCover = refreshCover,
          refreshMini = refreshMini,
          forceArtworkReload = forceArtworkReload
        )
      }
    }
  }

  suspend fun refreshWidgetsBlocking(
    context: Context,
    refreshMusic: Boolean = true,
    refreshLyrics: Boolean = true,
    refreshCover: Boolean = refreshMusic,
    refreshMini: Boolean = refreshMusic,
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

    val coverIds = if (refreshCover) {
      runCatching {
        manager.getAppWidgetIds(ComponentName(appCtx, AurixCoverWidgetProvider::class.java))
      }.getOrDefault(IntArray(0))
    } else IntArray(0)

    val miniIds = if (refreshMini) {
      runCatching {
        manager.getAppWidgetIds(ComponentName(appCtx, AurixMiniWidgetProvider::class.java))
      }.getOrDefault(IntArray(0))
    } else IntArray(0)

    if (musicIds.isEmpty() && lyricsIds.isEmpty() && coverIds.isEmpty() && miniIds.isEmpty()) return

    val snapshot = readSnapshot(appCtx)
    val rawArt = loadOrRestoreRawArtwork(appCtx, snapshot, forceArtworkReload)
    val roundedArt = getOrRenderRoundedAlbumArt(appCtx, rawArt)
    val glowBg = getOrRenderBackgroundBitmap(appCtx, snapshot, rawArt)

    if (musicIds.isNotEmpty()) {
      for (widgetId in musicIds) {
        val options = runCatching { manager.getAppWidgetOptions(widgetId) }.getOrNull()
        val views = buildMusicWidgetRemoteViews(appCtx, snapshot, glowBg, roundedArt, options)
        runCatching { manager.updateAppWidget(widgetId, views) }
      }
    }

    if (coverIds.isNotEmpty()) {
      for (widgetId in coverIds) {
        val sig = "${snapshot.songId}|${snapshot.artworkUrl}|${snapshot.isPlaying}|${snapshot.widgetStyle}|${snapshot.language}"
        if (!forceArtworkReload && lastCoverSignatureByWidgetId[widgetId] == sig) continue
        val views = buildCoverWidgetRemoteViews(appCtx, snapshot, rawArt)
        runCatching { manager.updateAppWidget(widgetId, views) }
        lastCoverSignatureByWidgetId[widgetId] = sig
      }
    }

    if (miniIds.isNotEmpty()) {
      for (widgetId in miniIds) {
        val eqBucket = if (snapshot.isPlaying) (snapshot.positionMs / 500L) else -1L
        val sig = "${snapshot.songId}|${snapshot.title}|${snapshot.artist}|${snapshot.isPlaying}|$eqBucket|${snapshot.widgetStyle}|${snapshot.ambientGlowEnabled}|${snapshot.khmerLyricsFont}|${snapshot.language}"
        if (!forceArtworkReload && lastMiniSignatureByWidgetId[widgetId] == sig) continue
        val views = buildMiniWidgetRemoteViews(appCtx, snapshot, glowBg)
        runCatching { manager.updateAppWidget(widgetId, views) }
        lastMiniSignatureByWidgetId[widgetId] = sig
      }
    }

    if (lyricsIds.isNotEmpty()) {
      val customizer = readWidgetCustomizerConfig(appCtx)
      val reduceMotion = isReducedMotionActive(appCtx, snapshot.reducedMotion)
      val songKey = "${snapshot.songId}|${snapshot.artworkUrl}"
      val standaloneFrameBmp = buildStandaloneSideFrameIfNeeded(appCtx, customizer, rawArt)
      val useCustomizerBg = customizer.bgType != WidgetBgType.PHOTO ||
        (!customizer.samePhoto && customizer.bgSource == WidgetPhotoSource.CUSTOM)
      for (widgetId in lyricsIds) {
        val options = runCatching { manager.getAppWidgetOptions(widgetId) }.getOrNull()
        val sizeTier = resolveWidgetSizeTier(options)
        val widgetWidthDp = resolveWidgetWidthDp(options)
        val bgBitmap = if (useCustomizerBg) {
          WidgetBitmapBuilder.buildWidgetCompositeBitmap(
            context = appCtx,
            config = customizer,
            sizeTier = sizeTier,
            albumArtBitmap = rawArt,
            songArtworkKey = songKey,
            includeFrameInComposite = false,
            auroraPhase = 0f,
            forceRebuild = false
          ).bitmap
        } else {
          glowBg
        }
        val layoutRes = resolveLyricsWidgetLayoutRes(snapshot.lyricsWidgetAnimStyle, reduceMotion, sizeTier)
        val views = buildLyricsWidgetRemoteViews(
          context = appCtx,
          state = snapshot,
          compositeBg = bgBitmap,
          roundedArt = roundedArt,
          standaloneFrameBmp = standaloneFrameBmp,
          customizer = customizer,
          sizeTier = sizeTier,
          widgetWidthDp = widgetWidthDp
        )
        runCatching { manager.updateAppWidget(widgetId, views) }
        lastFullLyricsLayoutByWidgetId[widgetId] = layoutRes
        lastLyricsSizeTierByWidgetId[widgetId] = sizeTier
        lastLyricsWidgetWidthDpByWidgetId[widgetId] = widgetWidthDp
        lastLyricsHasSideFrameByWidgetId[widgetId] =
          customizer.frameStyle != WidgetFrameStyle.NONE && sizeTier != WidgetSizeTier.SMALL && standaloneFrameBmp != null
        lastFullLyricsLayoutRes = layoutRes
      }
      partialUpdatesSinceFullLyrics = 0
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

  fun updateSpecificCoverWidgets(
    context: Context,
    appWidgetManager: AppWidgetManager,
    appWidgetIds: IntArray
  ) {
    val appCtx = context.applicationContext
    widgetScope.launch {
      runCatching {
        val snapshot = readSnapshot(appCtx)
        val rawArt = loadOrRestoreRawArtwork(appCtx, snapshot, forceReload = false)
        for (widgetId in appWidgetIds) {
          val views = buildCoverWidgetRemoteViews(appCtx, snapshot, rawArt)
          runCatching { appWidgetManager.updateAppWidget(widgetId, views) }
          lastCoverSignatureByWidgetId[widgetId] =
            "${snapshot.songId}|${snapshot.artworkUrl}|${snapshot.isPlaying}|${snapshot.widgetStyle}|${snapshot.language}"
        }
      }
    }
  }

  fun updateSpecificMiniWidgets(
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
          val views = buildMiniWidgetRemoteViews(appCtx, snapshot, glowBg)
          runCatching { appWidgetManager.updateAppWidget(widgetId, views) }
          val eqBucket = if (snapshot.isPlaying) (snapshot.positionMs / 500L) else -1L
          lastMiniSignatureByWidgetId[widgetId] =
            "${snapshot.songId}|${snapshot.title}|${snapshot.artist}|${snapshot.isPlaying}|$eqBucket|${snapshot.widgetStyle}|${snapshot.ambientGlowEnabled}|${snapshot.khmerLyricsFont}|${snapshot.language}"
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
        val customizer = readWidgetCustomizerConfig(appCtx)
        val rawArt = loadOrRestoreRawArtwork(appCtx, snapshot, forceReload = false)
        val roundedArt = getOrRenderRoundedAlbumArt(appCtx, rawArt)
        val glowBg = getOrRenderBackgroundBitmap(appCtx, snapshot, rawArt)
        val reduceMotion = isReducedMotionActive(appCtx, snapshot.reducedMotion)
        val songKey = "${snapshot.songId}|${snapshot.artworkUrl}"
        val standaloneFrameBmp = buildStandaloneSideFrameIfNeeded(appCtx, customizer, rawArt)
        val useCustomizerBg = customizer.bgType != WidgetBgType.PHOTO ||
          (!customizer.samePhoto && customizer.bgSource == WidgetPhotoSource.CUSTOM)
        for (widgetId in appWidgetIds) {
          val options = runCatching { appWidgetManager.getAppWidgetOptions(widgetId) }.getOrNull()
          val sizeTier = resolveWidgetSizeTier(options)
          val widgetWidthDp = resolveWidgetWidthDp(options)
          val compositeBg = if (useCustomizerBg) {
            WidgetBitmapBuilder.buildWidgetCompositeBitmap(
              context = appCtx,
              config = customizer,
              sizeTier = sizeTier,
              albumArtBitmap = rawArt,
              songArtworkKey = songKey,
              includeFrameInComposite = false,
              auroraPhase = 0f,
              forceRebuild = false
            ).bitmap
          } else {
            glowBg
          }
          val layoutRes = resolveLyricsWidgetLayoutRes(snapshot.lyricsWidgetAnimStyle, reduceMotion, sizeTier)
          val views = buildLyricsWidgetRemoteViews(
            context = appCtx,
            state = snapshot,
            compositeBg = compositeBg,
            roundedArt = roundedArt,
            standaloneFrameBmp = standaloneFrameBmp,
            customizer = customizer,
            sizeTier = sizeTier,
            widgetWidthDp = widgetWidthDp
          )
          runCatching { appWidgetManager.updateAppWidget(widgetId, views) }
          lastFullLyricsLayoutByWidgetId[widgetId] = layoutRes
          lastLyricsSizeTierByWidgetId[widgetId] = sizeTier
          lastLyricsWidgetWidthDpByWidgetId[widgetId] = widgetWidthDp
          lastLyricsHasSideFrameByWidgetId[widgetId] =
            customizer.frameStyle != WidgetFrameStyle.NONE && sizeTier != WidgetSizeTier.SMALL && standaloneFrameBmp != null
          lastFullLyricsLayoutRes = layoutRes
        }
        partialUpdatesSinceFullLyrics = 0
        if (snapshot.isPlaying && snapshot.rawLrc.isNotBlank()) {
          onLyricsSyncStateChanged?.invoke()
        }
      }
    }
  }

  private fun buildStandaloneSideFrameIfNeeded(
    context: Context,
    customizer: WidgetCustomizerConfig,
    rawArt: Bitmap?
  ): Bitmap? {
    if (customizer.frameStyle == WidgetFrameStyle.NONE) return null
    val frameSource = WidgetBitmapBuilder.resolveFramePhotoBitmap(context, customizer, rawArt)
    return WidgetBitmapBuilder.buildStandaloneFrameBitmap(
      photo = frameSource,
      config = customizer,
      sizePx = 200
    )
  }

  private fun buildMusicWidgetRemoteViews(
    context: Context,
    state: WidgetSnapshotState,
    glowBg: Bitmap,
    roundedArt: Bitmap,
    options: Bundle?
  ): RemoteViews {
    val localizedCtx = LocaleHelper.wrapBaseContext(context, state.language)
    val style = WidgetStyle.fromId(state.widgetStyle)
    val layoutRes = resolveMusicWidgetLayoutRes(style)
    val views = RemoteViews(context.packageName, layoutRes)

    val titleColor = when (style) {
      WidgetStyle.COLOR -> 0xFF16121F.toInt()
      else -> 0xFFFFFFFF.toInt()
    }
    val artistColor = when (style) {
      WidgetStyle.COLOR -> 0x9416121F.toInt()
      else -> 0x94FFFFFF.toInt()
    }
    val controlIconTint = when (style) {
      WidgetStyle.COLOR -> 0xFF16121F.toInt()
      else -> 0x94FFFFFF.toInt()
    }
    val playIconTint = when (style) {
      WidgetStyle.GLASS -> 0xFF04120C.toInt()
      WidgetStyle.COLOR -> 0xFFF3EEFF.toInt()
      WidgetStyle.MONO -> 0xFF000000.toInt()
    }

    if (style == WidgetStyle.MONO || !state.ambientGlowEnabled) {
      views.setViewVisibility(R.id.widget_music_bg_glow, View.GONE)
    } else {
      views.setViewVisibility(R.id.widget_music_bg_glow, View.VISIBLE)
      views.setImageViewBitmap(R.id.widget_music_bg_glow, glowBg)
    }

    val openPlayerPi = buildOpenAppPendingIntent(
      context = context,
      requestCode = 7101,
      openScreen = AppNotificationHelper.SCREEN_NOW_PLAYING_PLAYER
    )
    val khmerFont = com.example.ui.theme.KhmerLyricsFont.fromId(state.khmerLyricsFont)

    if (state.songId.isBlank() || state.title.isBlank()) {
      views.setViewVisibility(R.id.widget_music_empty_container, View.VISIBLE)
      views.setViewVisibility(R.id.widget_music_content_container, View.GONE)
      val tapToPlayText = localizedCtx.getString(R.string.widget_tap_to_play)
      val emptyBmp = renderStyledUiBitmap(
        context = context,
        text = tapToPlayText,
        textColor = titleColor,
        baseTextSizeDp = 15.0f,
        isBold = true,
        widthDp = 210f,
        wrapToTextWidth = true
      )
      if (emptyBmp != null) {
        views.setViewVisibility(R.id.widget_music_empty_bitmap, View.VISIBLE)
        views.setViewVisibility(R.id.widget_music_empty_text, View.GONE)
        views.setImageViewBitmap(R.id.widget_music_empty_bitmap, emptyBmp)
      } else {
        views.setViewVisibility(R.id.widget_music_empty_bitmap, View.GONE)
        views.setViewVisibility(R.id.widget_music_empty_text, View.VISIBLE)
        views.setTextViewText(R.id.widget_music_empty_text, tapToPlayText)
        views.setTextColor(R.id.widget_music_empty_text, titleColor)
      }
      views.setOnClickPendingIntent(R.id.widget_music_root, openPlayerPi)
      views.setOnClickPendingIntent(R.id.widget_music_empty_container, openPlayerPi)
      return views
    }

    views.setViewVisibility(R.id.widget_music_empty_container, View.GONE)
    views.setViewVisibility(R.id.widget_music_content_container, View.VISIBLE)

    views.setImageViewBitmap(R.id.widget_music_album_art, roundedArt)
    views.setOnClickPendingIntent(R.id.widget_music_album_art, openPlayerPi)
    views.setOnClickPendingIntent(R.id.widget_music_meta_container, openPlayerPi)
    views.setOnClickPendingIntent(R.id.widget_music_root, openPlayerPi)

    val minWidthDp = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 280) ?: 280
    val showCornerOrbi = minWidthDp <= 0 || minWidthDp >= 210
    views.setViewVisibility(
      R.id.widget_music_brand_orbi,
      if (showCornerOrbi && style != WidgetStyle.MONO) View.VISIBLE else View.GONE
    )

    val artOccupiedDp = when (style) {
      WidgetStyle.COLOR -> 138
      WidgetStyle.MONO -> 128
      WidgetStyle.GLASS -> 108
    }
    val metaWidthDp = (minWidthDp - artOccupiedDp).coerceIn(120, 340).toFloat()
    val titleWidthDp = (metaWidthDp - if (showCornerOrbi && style != WidgetStyle.MONO) 24f else 0f).coerceAtLeast(100f)
    val cleanTitle = state.title.trim()
    val cleanArtist = state.artist.trim().ifBlank { "AURIX" }

    val titleBmp = renderStyledTitleOrFooterBitmap(
      context = context,
      titleText = cleanTitle,
      artistText = "",
      khmerLyricsFont = khmerFont,
      titleColor = titleColor,
      artistColor = artistColor,
      baseTextSizeDp = 17.0f,
      widthDp = titleWidthDp,
      maxLines = 1
    )
    if (titleBmp != null) {
      views.setViewVisibility(R.id.widget_music_title_bitmap, View.VISIBLE)
      views.setViewVisibility(R.id.widget_music_title, View.GONE)
      views.setImageViewBitmap(R.id.widget_music_title_bitmap, titleBmp)
    } else {
      views.setViewVisibility(R.id.widget_music_title_bitmap, View.GONE)
      views.setViewVisibility(R.id.widget_music_title, View.VISIBLE)
      views.setTextViewText(R.id.widget_music_title, cleanTitle)
      views.setTextColor(R.id.widget_music_title, titleColor)
    }

    val artistBmp = renderStyledUiBitmap(
      context = context,
      text = cleanArtist,
      textColor = artistColor,
      baseTextSizeDp = 13.0f,
      isBold = false,
      widthDp = metaWidthDp
    )
    if (artistBmp != null) {
      views.setViewVisibility(R.id.widget_music_artist_bitmap, View.VISIBLE)
      views.setViewVisibility(R.id.widget_music_artist, View.GONE)
      views.setImageViewBitmap(R.id.widget_music_artist_bitmap, artistBmp)
    } else {
      views.setViewVisibility(R.id.widget_music_artist_bitmap, View.GONE)
      views.setViewVisibility(R.id.widget_music_artist, View.VISIBLE)
      views.setTextViewText(R.id.widget_music_artist, cleanArtist)
      views.setTextColor(R.id.widget_music_artist, artistColor)
    }

    val progressPermille = if (state.durationMs > 0L) {
      ((state.positionMs.toDouble() / state.durationMs.toDouble()) * 1000.0)
        .roundToInt()
        .coerceIn(0, 1000)
    } else 0
    views.setProgressBar(R.id.widget_music_progress, 1000, progressPermille, false)

    views.setImageViewResource(
      R.id.widget_btn_play_pause_icon,
      if (state.isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play
    )
    views.setInt(R.id.widget_btn_play_pause_icon, "setColorFilter", playIconTint)
    views.setContentDescription(R.id.widget_music_album_art, localizedCtx.getString(R.string.widget_cd_album_art))
    views.setContentDescription(R.id.widget_btn_play_pause_icon, localizedCtx.getString(R.string.widget_cd_play_pause))

    views.setOnClickPendingIntent(
      R.id.widget_btn_play_pause,
      buildWidgetActionPendingIntent(context, ACTION_WIDGET_PLAY_PAUSE, 7202)
    )

    if (style != WidgetStyle.MONO) {
      views.setImageViewResource(
        R.id.widget_btn_like_icon,
        if (state.isFavorite) R.drawable.ic_widget_heart_filled else R.drawable.ic_widget_heart_outline
      )
      views.setInt(R.id.widget_btn_prev_icon, "setColorFilter", controlIconTint)
      views.setInt(R.id.widget_btn_next_icon, "setColorFilter", controlIconTint)
      views.setInt(
        R.id.widget_btn_like_icon,
        "setColorFilter",
        if (state.isFavorite) COLOR_HEART_RED else controlIconTint
      )

      views.setContentDescription(R.id.widget_btn_prev_icon, localizedCtx.getString(R.string.widget_cd_prev))
      views.setContentDescription(R.id.widget_btn_next_icon, localizedCtx.getString(R.string.widget_cd_next))
      views.setContentDescription(R.id.widget_btn_like_icon, localizedCtx.getString(R.string.widget_cd_like))

      views.setViewVisibility(
        R.id.widget_btn_like,
        if (shouldShowLikeButton(minWidthDp)) View.VISIBLE else View.GONE
      )

      views.setOnClickPendingIntent(
        R.id.widget_btn_prev,
        buildWidgetActionPendingIntent(context, ACTION_WIDGET_PREV, 7201)
      )
      views.setOnClickPendingIntent(
        R.id.widget_btn_next,
        buildWidgetActionPendingIntent(context, ACTION_WIDGET_NEXT, 7203)
      )
      views.setOnClickPendingIntent(
        R.id.widget_btn_like,
        buildWidgetActionPendingIntent(context, ACTION_WIDGET_LIKE, 7204)
      )
    }

    return views
  }

  private fun buildCoverWidgetRemoteViews(
    context: Context,
    state: WidgetSnapshotState,
    rawArt: Bitmap?
  ): RemoteViews {
    val localizedCtx = LocaleHelper.wrapBaseContext(context, state.language)
    val style = WidgetStyle.fromId(state.widgetStyle)
    val views = RemoteViews(context.packageName, R.layout.widget_aurix_cover)

    val clipRes = when (style) {
      WidgetStyle.COLOR -> R.drawable.widget_clip_32dp
      WidgetStyle.MONO -> R.drawable.widget_clip_22dp
      WidgetStyle.GLASS -> R.drawable.widget_clip_28dp
    }
    views.setInt(R.id.widget_cover_root, "setBackgroundResource", clipRes)

    val openPlayerPi = buildOpenAppPendingIntent(
      context = context,
      requestCode = 7401,
      openScreen = AppNotificationHelper.SCREEN_NOW_PLAYING_PLAYER
    )
    views.setOnClickPendingIntent(R.id.widget_cover_root, openPlayerPi)

    if (state.songId.isBlank() || state.title.isBlank()) {
      views.setViewVisibility(R.id.widget_cover_empty_container, View.VISIBLE)
      views.setViewVisibility(R.id.widget_cover_content_container, View.GONE)
      val tapToPlay = localizedCtx.getString(R.string.widget_tap_to_play)
      val emptyBmp = renderStyledUiBitmap(
        context = context,
        text = tapToPlay,
        textColor = 0xFFFFFFFF.toInt(),
        baseTextSizeDp = 13.0f,
        isBold = true,
        widthDp = 130f,
        wrapToTextWidth = true
      )
      if (emptyBmp != null) {
        views.setViewVisibility(R.id.widget_cover_empty_bitmap, View.VISIBLE)
        views.setViewVisibility(R.id.widget_cover_empty_text, View.GONE)
        views.setImageViewBitmap(R.id.widget_cover_empty_bitmap, emptyBmp)
      } else {
        views.setViewVisibility(R.id.widget_cover_empty_bitmap, View.GONE)
        views.setViewVisibility(R.id.widget_cover_empty_text, View.VISIBLE)
        views.setTextViewText(R.id.widget_cover_empty_text, tapToPlay)
      }
      views.setOnClickPendingIntent(R.id.widget_cover_empty_container, openPlayerPi)
      return views
    }

    views.setViewVisibility(R.id.widget_cover_empty_container, View.GONE)
    views.setViewVisibility(R.id.widget_cover_content_container, View.VISIBLE)

    val coverBmp = rawArt ?: getOrRenderRoundedAlbumArt(context, null)
    views.setImageViewBitmap(R.id.widget_cover_image, coverBmp)
    views.setOnClickPendingIntent(R.id.widget_cover_image, openPlayerPi)

    views.setInt(
      R.id.widget_cover_play_pause,
      "setBackgroundResource",
      if (style == WidgetStyle.COLOR) R.drawable.widget_circle_dark else R.drawable.widget_circle_blur_dark
    )
    views.setImageViewResource(
      R.id.widget_cover_play_pause_icon,
      if (state.isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play
    )
    views.setInt(
      R.id.widget_cover_play_pause_icon,
      "setColorFilter",
      if (style == WidgetStyle.COLOR) 0xFFF3EEFF.toInt() else 0xFFFFFFFF.toInt()
    )
    views.setContentDescription(R.id.widget_cover_image, localizedCtx.getString(R.string.widget_cd_album_art))
    views.setContentDescription(R.id.widget_cover_play_pause_icon, localizedCtx.getString(R.string.widget_cd_play_pause))
    views.setOnClickPendingIntent(
      R.id.widget_cover_play_pause,
      buildWidgetActionPendingIntent(
        context = context,
        action = ACTION_WIDGET_PLAY_PAUSE,
        requestCode = 7402,
        providerClass = AurixCoverWidgetProvider::class.java
      )
    )
    return views
  }

  private fun buildMiniWidgetRemoteViews(
    context: Context,
    state: WidgetSnapshotState,
    glowBg: Bitmap
  ): RemoteViews {
    val localizedCtx = LocaleHelper.wrapBaseContext(context, state.language)
    val style = WidgetStyle.fromId(state.widgetStyle)
    val views = RemoteViews(context.packageName, R.layout.widget_aurix_mini)

    val clipRes = when (style) {
      WidgetStyle.COLOR -> R.drawable.widget_clip_32dp
      WidgetStyle.MONO -> R.drawable.widget_clip_22dp
      WidgetStyle.GLASS -> R.drawable.widget_clip_28dp
    }
    val surfaceRes = when (style) {
      WidgetStyle.COLOR -> R.drawable.widget_music_color_bg
      WidgetStyle.MONO -> R.drawable.widget_music_mono_bg
      WidgetStyle.GLASS -> R.drawable.widget_music_glass_bg
    }
    views.setInt(R.id.widget_mini_root, "setBackgroundResource", clipRes)
    views.setInt(R.id.widget_mini_card_border, "setBackgroundResource", surfaceRes)

    if (style == WidgetStyle.MONO || !state.ambientGlowEnabled) {
      views.setViewVisibility(R.id.widget_mini_bg_glow, View.GONE)
    } else {
      views.setViewVisibility(R.id.widget_mini_bg_glow, View.VISIBLE)
      views.setImageViewBitmap(R.id.widget_mini_bg_glow, glowBg)
    }

    val titleColor = if (style == WidgetStyle.COLOR) 0xFF16121F.toInt() else 0xFFFFFFFF.toInt()
    val artistColor = if (style == WidgetStyle.COLOR) 0x9416121F.toInt() else 0x94FFFFFF.toInt()
    val eqColor = when (style) {
      WidgetStyle.GLASS -> 0xFF7EF0C5.toInt()
      WidgetStyle.COLOR -> 0xFF16121F.toInt()
      WidgetStyle.MONO -> 0xFFFFFFFF.toInt()
    }

    val openPlayerPi = buildOpenAppPendingIntent(
      context = context,
      requestCode = 7501,
      openScreen = AppNotificationHelper.SCREEN_NOW_PLAYING_PLAYER
    )
    views.setOnClickPendingIntent(R.id.widget_mini_root, openPlayerPi)

    if (state.songId.isBlank() || state.title.isBlank()) {
      views.setViewVisibility(R.id.widget_mini_empty_container, View.VISIBLE)
      views.setViewVisibility(R.id.widget_mini_content_container, View.GONE)
      val tapToPlay = localizedCtx.getString(R.string.widget_tap_to_play)
      val emptyBmp = renderStyledUiBitmap(
        context = context,
        text = tapToPlay,
        textColor = titleColor,
        baseTextSizeDp = 13.0f,
        isBold = true,
        widthDp = 130f,
        wrapToTextWidth = true
      )
      if (emptyBmp != null) {
        views.setViewVisibility(R.id.widget_mini_empty_bitmap, View.VISIBLE)
        views.setViewVisibility(R.id.widget_mini_empty_text, View.GONE)
        views.setImageViewBitmap(R.id.widget_mini_empty_bitmap, emptyBmp)
      } else {
        views.setViewVisibility(R.id.widget_mini_empty_bitmap, View.GONE)
        views.setViewVisibility(R.id.widget_mini_empty_text, View.VISIBLE)
        views.setTextViewText(R.id.widget_mini_empty_text, tapToPlay)
        views.setTextColor(R.id.widget_mini_empty_text, titleColor)
      }
      views.setOnClickPendingIntent(R.id.widget_mini_empty_container, openPlayerPi)
      return views
    }

    views.setViewVisibility(R.id.widget_mini_empty_container, View.GONE)
    views.setViewVisibility(R.id.widget_mini_content_container, View.VISIBLE)
    views.setOnClickPendingIntent(R.id.widget_mini_content_container, openPlayerPi)

    val phase = ((state.positionMs / 450L) % 6L).toInt()
    views.setImageViewBitmap(
      R.id.widget_mini_eq_bitmap,
      renderMiniEqualizerBitmap(eqColor = eqColor, isPlaying = state.isPlaying, phase = phase)
    )

    val khmerFont = com.example.ui.theme.KhmerLyricsFont.fromId(state.khmerLyricsFont)
    val cleanTitle = state.title.trim()
    val cleanArtist = state.artist.trim().ifBlank { "AURIX" }

    val titleBmp = renderStyledTitleOrFooterBitmap(
      context = context,
      titleText = cleanTitle,
      artistText = "",
      khmerLyricsFont = khmerFont,
      titleColor = titleColor,
      artistColor = artistColor,
      baseTextSizeDp = 16.0f,
      widthDp = 148f,
      maxLines = 2
    )
    if (titleBmp != null) {
      views.setViewVisibility(R.id.widget_mini_title_bitmap, View.VISIBLE)
      views.setViewVisibility(R.id.widget_mini_title, View.GONE)
      views.setImageViewBitmap(R.id.widget_mini_title_bitmap, titleBmp)
    } else {
      views.setViewVisibility(R.id.widget_mini_title_bitmap, View.GONE)
      views.setViewVisibility(R.id.widget_mini_title, View.VISIBLE)
      views.setTextViewText(R.id.widget_mini_title, cleanTitle)
      views.setTextColor(R.id.widget_mini_title, titleColor)
    }

    val artistBmp = renderStyledUiBitmap(
      context = context,
      text = cleanArtist,
      textColor = artistColor,
      baseTextSizeDp = 13.0f,
      isBold = false,
      widthDp = 148f
    )
    if (artistBmp != null) {
      views.setViewVisibility(R.id.widget_mini_artist_bitmap, View.VISIBLE)
      views.setViewVisibility(R.id.widget_mini_artist, View.GONE)
      views.setImageViewBitmap(R.id.widget_mini_artist_bitmap, artistBmp)
    } else {
      views.setViewVisibility(R.id.widget_mini_artist_bitmap, View.GONE)
      views.setViewVisibility(R.id.widget_mini_artist, View.VISIBLE)
      views.setTextViewText(R.id.widget_mini_artist, cleanArtist)
      views.setTextColor(R.id.widget_mini_artist, artistColor)
    }

    return views
  }

  private fun renderMiniEqualizerBitmap(
    eqColor: Int,
    isPlaying: Boolean,
    phase: Int
  ): Bitmap {
    val w = 78
    val h = 54
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = eqColor
      style = Paint.Style.FILL
    }
    val heights = if (!isPlaying) {
      floatArrayOf(0.32f, 0.42f, 0.36f, 0.28f)
    } else {
      when (phase.mod(4)) {
        0 -> floatArrayOf(0.54f, 0.96f, 0.74f, 0.44f)
        1 -> floatArrayOf(0.84f, 0.62f, 0.94f, 0.68f)
        2 -> floatArrayOf(0.46f, 0.88f, 0.56f, 0.92f)
        else -> floatArrayOf(0.72f, 0.48f, 0.86f, 0.58f)
      }
    }
    val barW = 11f
    val gap = 8f
    val startX = 4f
    val bottomY = h - 4f
    val maxBarH = h - 8f
    for (i in 0 until 4) {
      val left = startX + i * (barW + gap)
      val barH = (maxBarH * heights[i]).coerceAtLeast(10f)
      val rect = RectF(left, bottomY - barH, left + barW, bottomY)
      canvas.drawRoundRect(rect, barW * 0.5f, barW * 0.5f, paint)
    }
    return bmp
  }

  private fun buildLyricsWidgetRemoteViews(
    context: Context,
    state: WidgetSnapshotState,
    compositeBg: Bitmap,
    roundedArt: Bitmap,
    standaloneFrameBmp: Bitmap?,
    customizer: WidgetCustomizerConfig,
    sizeTier: WidgetSizeTier,
    widgetWidthDp: Int = 0
  ): RemoteViews {
    val localizedCtx = LocaleHelper.wrapBaseContext(context, state.language)
    val reduceMotion = isReducedMotionActive(context, state.reducedMotion)
    val layoutRes = resolveLyricsWidgetLayoutRes(state.lyricsWidgetAnimStyle, reduceMotion, sizeTier)
    val views = RemoteViews(context.packageName, layoutRes)
    val style = WidgetStyle.fromId(state.widgetStyle)

    val primaryTextColor = when (style) {
      WidgetStyle.COLOR -> 0xFFF3EEFF.toInt()
      else -> 0xFFFFFFFF.toInt()
    }
    val mutedTextColor = when (style) {
      WidgetStyle.COLOR -> 0x4DF3EEFF.toInt()
      else -> 0x4DFFFFFF.toInt()
    }
    val secondaryColor = when (style) {
      WidgetStyle.COLOR -> 0x94F3EEFF.toInt()
      else -> 0x94FFFFFF.toInt()
    }
    val khmerFont = com.example.ui.theme.KhmerLyricsFont.fromId(state.khmerLyricsFont)

    val clipRes = if (style == WidgetStyle.MONO) R.drawable.widget_clip_22dp else R.drawable.widget_clip_28dp
    val surfaceRes = when (style) {
      WidgetStyle.GLASS -> R.drawable.widget_music_glass_bg
      WidgetStyle.COLOR -> R.drawable.widget_lyrics_color_bg
      WidgetStyle.MONO -> R.drawable.widget_music_mono_bg
    }
    val barRes = when (style) {
      WidgetStyle.GLASS -> R.drawable.widget_lyric_mint_bar
      WidgetStyle.COLOR -> R.drawable.widget_lyric_lavender_bar
      WidgetStyle.MONO -> R.drawable.widget_lyric_white_bar
    }
    views.setInt(R.id.widget_lyrics_root, "setBackgroundResource", clipRes)
    views.setInt(R.id.widget_lyrics_card_border, "setBackgroundResource", surfaceRes)
    views.setInt(R.id.widget_lyrics_accent_bar, "setBackgroundResource", barRes)

    if (style == WidgetStyle.MONO || !state.ambientGlowEnabled) {
      views.setViewVisibility(R.id.widget_lyrics_bg_glow, View.GONE)
    } else {
      views.setViewVisibility(R.id.widget_lyrics_bg_glow, View.VISIBLE)
      views.setImageViewBitmap(R.id.widget_lyrics_bg_glow, compositeBg)
    }

    val openLyricsPi = buildOpenAppPendingIntent(
      context = context,
      requestCode = 7301,
      openScreen = AppNotificationHelper.SCREEN_NOW_PLAYING_LYRICS
    )
    views.setOnClickPendingIntent(R.id.widget_lyrics_root, openLyricsPi)

    if (state.songId.isBlank() || state.title.isBlank()) {
      views.setViewVisibility(R.id.widget_lyrics_empty_container, View.VISIBLE)
      views.setViewVisibility(R.id.widget_lyrics_content_container, View.GONE)
      val tapToPlay = localizedCtx.getString(R.string.widget_tap_to_play)
      val emptyBmp = renderStyledUiBitmap(
        context = context,
        text = tapToPlay,
        textColor = primaryTextColor,
        baseTextSizeDp = if (sizeTier == WidgetSizeTier.SMALL) 13.0f else 15.0f,
        isBold = true,
        widthDp = if (sizeTier == WidgetSizeTier.SMALL) 140f else 210f,
        wrapToTextWidth = true
      )
      if (emptyBmp != null) {
        views.setViewVisibility(R.id.widget_lyrics_empty_bitmap, View.VISIBLE)
        views.setViewVisibility(R.id.widget_lyrics_empty_text, View.GONE)
        views.setImageViewBitmap(R.id.widget_lyrics_empty_bitmap, emptyBmp)
      } else {
        views.setViewVisibility(R.id.widget_lyrics_empty_bitmap, View.GONE)
        views.setViewVisibility(R.id.widget_lyrics_empty_text, View.VISIBLE)
        views.setTextViewText(R.id.widget_lyrics_empty_text, tapToPlay)
        views.setTextColor(R.id.widget_lyrics_empty_text, primaryTextColor)
      }
      views.setOnClickPendingIntent(R.id.widget_lyrics_empty_container, openLyricsPi)
      return views
    }

    views.setViewVisibility(R.id.widget_lyrics_empty_container, View.GONE)
    views.setViewVisibility(R.id.widget_lyrics_content_container, View.VISIBLE)
    views.setOnClickPendingIntent(R.id.widget_lyrics_content_container, openLyricsPi)

    val sideFrame = standaloneFrameBmp?.takeIf {
      customizer.frameStyle != WidgetFrameStyle.NONE &&
        sizeTier != WidgetSizeTier.SMALL &&
        style != WidgetStyle.MONO &&
        (customizer.frameSource == WidgetPhotoSource.CUSTOM || customizer.frameStyle != WidgetFrameStyle.ROUNDED)
    }
    val showSideFrameSpacer = sideFrame != null
    if (sideFrame != null) {
      views.setViewVisibility(R.id.widget_lyrics_frame_spacer, View.VISIBLE)
      views.setImageViewBitmap(R.id.widget_lyrics_frame_spacer, sideFrame)
    } else {
      views.setViewVisibility(R.id.widget_lyrics_frame_spacer, View.GONE)
    }

    val cleanTitle = state.title.trim()
    val cleanArtist = state.artist.trim().ifBlank { "AURIX" }
    val showCornerOrbi = widgetWidthDp <= 0 || widgetWidthDp >= 210
    views.setViewVisibility(
      R.id.widget_lyrics_brand_orbi,
      if (showCornerOrbi) View.VISIBLE else View.GONE
    )

    // Glass & Color show compact 44dp art + title + artist header; Mono shows lyric first then title & artist footer
    if (style == WidgetStyle.MONO) {
      views.setViewVisibility(R.id.widget_lyrics_header_row, View.GONE)
    } else {
      views.setViewVisibility(R.id.widget_lyrics_header_row, View.VISIBLE)
      views.setImageViewBitmap(R.id.widget_lyrics_header_art, roundedArt)
      val headerWidthDp = ((if (widgetWidthDp in 120..520) widgetWidthDp else 320) - 120).coerceIn(110, 320).toFloat()
      val hTitleBmp = renderStyledTitleOrFooterBitmap(
        context = context,
        titleText = cleanTitle,
        artistText = "",
        khmerLyricsFont = khmerFont,
        titleColor = primaryTextColor,
        artistColor = secondaryColor,
        baseTextSizeDp = 14.5f,
        widthDp = headerWidthDp,
        maxLines = 1
      )
      if (hTitleBmp != null) {
        views.setViewVisibility(R.id.widget_lyrics_header_title_bitmap, View.VISIBLE)
        views.setViewVisibility(R.id.widget_lyrics_header_title, View.GONE)
        views.setImageViewBitmap(R.id.widget_lyrics_header_title_bitmap, hTitleBmp)
      } else {
        views.setViewVisibility(R.id.widget_lyrics_header_title_bitmap, View.GONE)
        views.setViewVisibility(R.id.widget_lyrics_header_title, View.VISIBLE)
        views.setTextViewText(R.id.widget_lyrics_header_title, cleanTitle)
        views.setTextColor(R.id.widget_lyrics_header_title, primaryTextColor)
      }

      val hArtistBmp = renderStyledUiBitmap(
        context = context,
        text = cleanArtist,
        textColor = secondaryColor,
        baseTextSizeDp = 12.0f,
        isBold = false,
        widthDp = headerWidthDp
      )
      if (hArtistBmp != null) {
        views.setViewVisibility(R.id.widget_lyrics_header_artist_bitmap, View.VISIBLE)
        views.setViewVisibility(R.id.widget_lyrics_header_artist, View.GONE)
        views.setImageViewBitmap(R.id.widget_lyrics_header_artist_bitmap, hArtistBmp)
      } else {
        views.setViewVisibility(R.id.widget_lyrics_header_artist_bitmap, View.GONE)
        views.setViewVisibility(R.id.widget_lyrics_header_artist, View.VISIBLE)
        views.setTextViewText(R.id.widget_lyrics_header_artist, cleanArtist)
        views.setTextColor(R.id.widget_lyrics_header_artist, secondaryColor)
      }
    }

    val textWidthDp = computeLyricsTextWidthDp(sizeTier, showSideFrameSpacer, widgetWidthDp)
    val footerWidthDp = computeLyricsFooterWidthDp(sizeTier, showSideFrameSpacer, widgetWidthDp)
    val activeTextSizeDp = computeLyricsActiveTextSizeDp(sizeTier)
    val nextTextSizeDp = computeLyricsNextTextSizeDp(sizeTier)
    val hasLyrics = state.hasLyrics && state.currentLyricLine.isNotBlank()

    if (!hasLyrics) {
      views.setViewVisibility(R.id.widget_lyrics_stage_row, View.GONE)
      views.setViewVisibility(R.id.widget_lyrics_footer_row, View.GONE)
      views.setViewVisibility(R.id.widget_lyrics_no_lyrics_container, View.VISIBLE)

      val noLyricsLabel = localizedCtx.getString(R.string.widget_no_lyrics_for_song)
      val effectiveWidgetWidth = if (widgetWidthDp in 120..520) {
        widgetWidthDp.toFloat()
      } else {
        when (sizeTier) {
          WidgetSizeTier.SMALL -> 182f
          WidgetSizeTier.WIDE -> 340f
          WidgetSizeTier.LARGE -> 360f
        }
      }
      val noLyricsWidthDp = (effectiveWidgetWidth - 36f - (if (showSideFrameSpacer) 84f else 0f)).coerceIn(132f, 400f)

      val noLabelBmp = renderStyledUiBitmap(
        context = context,
        text = noLyricsLabel,
        textColor = secondaryColor,
        baseTextSizeDp = 14.0f,
        isBold = false,
        widthDp = noLyricsWidthDp
      )
      if (noLabelBmp != null) {
        views.setViewVisibility(R.id.widget_lyrics_no_lyrics_label_bitmap, View.VISIBLE)
        views.setViewVisibility(R.id.widget_lyrics_no_lyrics_label, View.GONE)
        views.setImageViewBitmap(R.id.widget_lyrics_no_lyrics_label_bitmap, noLabelBmp)
      } else {
        views.setViewVisibility(R.id.widget_lyrics_no_lyrics_label_bitmap, View.GONE)
        views.setViewVisibility(R.id.widget_lyrics_no_lyrics_label, View.VISIBLE)
        views.setTextViewText(R.id.widget_lyrics_no_lyrics_label, noLyricsLabel)
        views.setTextColor(R.id.widget_lyrics_no_lyrics_label, secondaryColor)
      }

      val titleBmp = renderStyledTitleOrFooterBitmap(
        context = context,
        titleText = cleanTitle,
        artistText = "",
        khmerLyricsFont = khmerFont,
        titleColor = primaryTextColor,
        artistColor = secondaryColor,
        baseTextSizeDp = 17.0f,
        widthDp = noLyricsWidthDp,
        maxLines = 2
      )
      if (titleBmp != null) {
        views.setViewVisibility(R.id.widget_lyrics_no_lyrics_title_bitmap, View.VISIBLE)
        views.setViewVisibility(R.id.widget_lyrics_no_lyrics_title, View.GONE)
        views.setImageViewBitmap(R.id.widget_lyrics_no_lyrics_title_bitmap, titleBmp)
      } else {
        views.setViewVisibility(R.id.widget_lyrics_no_lyrics_title_bitmap, View.GONE)
        views.setViewVisibility(R.id.widget_lyrics_no_lyrics_title, View.VISIBLE)
        views.setTextViewText(R.id.widget_lyrics_no_lyrics_title, cleanTitle)
        views.setTextColor(R.id.widget_lyrics_no_lyrics_title, primaryTextColor)
      }

      views.setViewVisibility(R.id.widget_lyrics_no_lyrics_artist_bitmap, View.GONE)
      views.setViewVisibility(R.id.widget_lyrics_no_lyrics_artist, View.GONE)
      return views
    }

    views.setViewVisibility(R.id.widget_lyrics_no_lyrics_container, View.GONE)
    views.setViewVisibility(R.id.widget_lyrics_stage_row, View.VISIBLE)
    views.setViewVisibility(R.id.widget_lyrics_accent_bar, View.VISIBLE)

    val displayCurrentLine = state.currentLyricLine
    val displayNextLine = state.nextLyricLine
    val activeSlot = state.lyricsFlipperSlot.coerceIn(0, 1)
    val inactiveSlot = 1 - activeSlot

    val activeBmp = bindLyricsFlipperSlot(
      context = context,
      views = views,
      slotIndex = activeSlot,
      currentLine = displayCurrentLine,
      khmerLyricsFont = khmerFont,
      primaryTextColor = primaryTextColor,
      textWidthDp = textWidthDp,
      activeTextSizeDp = activeTextSizeDp
    )
    val outgoingLine = state.prevLyricLine.takeIf { it.isNotBlank() } ?: displayCurrentLine
    bindLyricsFlipperSlot(
      context = context,
      views = views,
      slotIndex = inactiveSlot,
      currentLine = outgoingLine,
      khmerLyricsFont = khmerFont,
      primaryTextColor = primaryTextColor,
      textWidthDp = textWidthDp,
      activeTextSizeDp = activeTextSizeDp,
      preRenderedBitmap = if (outgoingLine == displayCurrentLine) activeBmp else null
    )
    views.setDisplayedChild(R.id.widget_lyrics_flipper, activeSlot)

    bindStaticNextLyricLine(
      context = context,
      views = views,
      nextLine = displayNextLine,
      khmerLyricsFont = khmerFont,
      mutedTextColor = mutedTextColor,
      textWidthDp = textWidthDp,
      nextTextSizeDp = nextTextSizeDp
    )

    if (style == WidgetStyle.MONO) {
      views.setViewVisibility(R.id.widget_lyrics_footer_row, View.VISIBLE)
      val footerBmp = renderStyledTitleOrFooterBitmap(
        context = context,
        titleText = cleanTitle,
        artistText = cleanArtist,
        khmerLyricsFont = khmerFont,
        titleColor = secondaryColor,
        artistColor = mutedTextColor,
        baseTextSizeDp = 13.0f,
        widthDp = footerWidthDp,
        maxLines = 1
      )
      if (footerBmp != null) {
        views.setViewVisibility(R.id.widget_lyrics_footer_bitmap, View.VISIBLE)
        views.setViewVisibility(R.id.widget_lyrics_song_footer, View.GONE)
        views.setImageViewBitmap(R.id.widget_lyrics_footer_bitmap, footerBmp)
      } else {
        views.setViewVisibility(R.id.widget_lyrics_footer_bitmap, View.GONE)
        views.setViewVisibility(R.id.widget_lyrics_song_footer, View.VISIBLE)
        val full = "$cleanTitle · $cleanArtist"
        views.setTextViewText(R.id.widget_lyrics_song_footer, full)
        views.setTextColor(R.id.widget_lyrics_song_footer, secondaryColor)
      }
    } else {
      views.setViewVisibility(R.id.widget_lyrics_footer_row, View.GONE)
    }

    return views
  }

  /**
   * Binds ONLY the currently active lyric line inside the ViewFlipper slot.
   * Renders via Canvas with the user's selected Khmer lyrics font (Kantumruy Pro, Koulen, or Noto Sans Khmer)
   * and optically equalized English + Khmer glyph heights at exact 1:1 widget column width.
   */
  private fun bindLyricsFlipperSlot(
    context: Context,
    views: RemoteViews,
    slotIndex: Int,
    currentLine: String,
    khmerLyricsFont: com.example.ui.theme.KhmerLyricsFont,
    primaryTextColor: Int,
    textWidthDp: Float = 266f,
    activeTextSizeDp: Float = 17.2f,
    preRenderedBitmap: Bitmap? = null
  ): Bitmap? {
    val khmerImgId = if (slotIndex == 0) R.id.widget_lyrics_khmer_bitmap_a else R.id.widget_lyrics_khmer_bitmap_b
    val textContainerId = if (slotIndex == 0) R.id.widget_lyrics_text_container_a else R.id.widget_lyrics_text_container_b
    val currentTextId = if (slotIndex == 0) R.id.widget_lyrics_current_line_a else R.id.widget_lyrics_current_line_b
    val nextTextId = if (slotIndex == 0) R.id.widget_lyrics_next_line_a else R.id.widget_lyrics_next_line_b

    // Always hide legacy in-flipper next_line_a/b so upcoming lyrics never animate inside ViewFlipper
    views.setViewVisibility(nextTextId, View.GONE)

    val activeBmp = preRenderedBitmap ?: renderStyledActiveLyricBitmap(
      context = context,
      currentLine = currentLine,
      khmerLyricsFont = khmerLyricsFont,
      primaryColor = primaryTextColor,
      widthDp = textWidthDp,
      textSizeDp = activeTextSizeDp
    )
    if (activeBmp != null) {
      views.setViewVisibility(khmerImgId, View.VISIBLE)
      views.setViewVisibility(textContainerId, View.GONE)
      views.setImageViewBitmap(khmerImgId, activeBmp)
      return activeBmp
    }

    views.setViewVisibility(khmerImgId, View.GONE)
    views.setViewVisibility(textContainerId, View.VISIBLE)
    views.setTextViewText(currentTextId, currentLine)
    views.setTextColor(currentTextId, primaryTextColor)
    return null
  }

  /**
   * Binds the upcoming/next lyric line into the static container OUTSIDE ViewFlipper
   * so it never receives animation before becoming the active lyric line.
   */
  private fun bindStaticNextLyricLine(
    context: Context,
    views: RemoteViews,
    nextLine: String,
    khmerLyricsFont: com.example.ui.theme.KhmerLyricsFont,
    mutedTextColor: Int,
    textWidthDp: Float = 266f,
    nextTextSizeDp: Float = 12.8f
  ) {
    val trimmed = nextLine.trim()
    if (trimmed.isEmpty()) {
      views.setViewVisibility(R.id.widget_lyrics_next_static_container, View.GONE)
      views.setViewVisibility(R.id.widget_lyrics_next_khmer_bitmap, View.GONE)
      views.setViewVisibility(R.id.widget_lyrics_next_line_static, View.GONE)
      return
    }

    views.setViewVisibility(R.id.widget_lyrics_next_static_container, View.VISIBLE)
    val nextBmp = renderStyledNextLyricBitmap(
      context = context,
      nextLine = trimmed,
      khmerLyricsFont = khmerLyricsFont,
      mutedColor = mutedTextColor,
      widthDp = textWidthDp,
      textSizeDp = nextTextSizeDp
    )
    if (nextBmp != null) {
      views.setViewVisibility(R.id.widget_lyrics_next_khmer_bitmap, View.VISIBLE)
      views.setViewVisibility(R.id.widget_lyrics_next_line_static, View.GONE)
      views.setImageViewBitmap(R.id.widget_lyrics_next_khmer_bitmap, nextBmp)
    } else {
      views.setViewVisibility(R.id.widget_lyrics_next_khmer_bitmap, View.GONE)
      views.setViewVisibility(R.id.widget_lyrics_next_line_static, View.VISIBLE)
      views.setTextViewText(R.id.widget_lyrics_next_line_static, trimmed)
      views.setTextColor(R.id.widget_lyrics_next_line_static, mutedTextColor)
    }
  }

  private fun renderStyledActiveLyricBitmap(
    context: Context,
    currentLine: String,
    khmerLyricsFont: com.example.ui.theme.KhmerLyricsFont,
    primaryColor: Int,
    widthDp: Float = 266f,
    textSizeDp: Float = 14.5f
  ): Bitmap? {
    return runCatching {
      val trimmed = currentLine.trim().ifBlank { "♪" }
      val scale = context.resources.displayMetrics.density.coerceIn(1.35f, 1.60f)
      val widthPx = (widthDp * scale).roundToInt().coerceIn(200, 640)
      val styledText = com.example.ui.theme.KhmerTypography.buildStyledSpannable(
        context = context,
        text = trimmed,
        isTitle = false,
        khmerLyricsFont = khmerLyricsFont,
        isBold = true
      )

      val baseTypeface = com.example.ui.theme.KhmerTypography.loadInterTypeface(context, isBold = true)
      val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = baseTypeface
        color = primaryColor
        textSize = textSizeDp * scale
        setShadowLayer(2.4f * scale, 0f, 0.9f * scale, 0x66000000)
      }

      val layout = StaticLayout.Builder
        .obtain(styledText, 0, styledText.length, paint, widthPx)
        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
        .setLineSpacing(2.0f * scale, 1.12f)
        .setIncludePad(true)
        .setMaxLines(2)
        .setEllipsize(TextUtils.TruncateAt.END)
        .build()

      val padY = (2f * scale).roundToInt()
      val heightPx = (layout.height + padY * 2).coerceIn(22, 118)
      val bmp = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
      val canvas = Canvas(bmp)
      canvas.save()
      canvas.translate(0f, padY.toFloat())
      layout.draw(canvas)
      canvas.restore()
      bmp
    }.getOrNull()
  }

  private fun renderStyledNextLyricBitmap(
    context: Context,
    nextLine: String,
    khmerLyricsFont: com.example.ui.theme.KhmerLyricsFont,
    mutedColor: Int,
    widthDp: Float = 266f,
    textSizeDp: Float = 11.5f
  ): Bitmap? {
    return runCatching {
      val trimmed = nextLine.trim()
      if (trimmed.isEmpty()) return null
      val scale = context.resources.displayMetrics.density.coerceIn(1.35f, 1.60f)
      val widthPx = (widthDp * scale).roundToInt().coerceIn(200, 640)
      val styledText = com.example.ui.theme.KhmerTypography.buildStyledSpannable(
        context = context,
        text = trimmed,
        isTitle = false,
        khmerLyricsFont = khmerLyricsFont,
        isBold = false
      )

      val baseTypeface = com.example.ui.theme.KhmerTypography.loadInterTypeface(context, isBold = false)
      val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = baseTypeface
        color = mutedColor
        textSize = textSizeDp * scale
      }

      val layout = StaticLayout.Builder
        .obtain(styledText, 0, styledText.length, paint, widthPx)
        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
        .setLineSpacing(1.5f * scale, 1.08f)
        .setIncludePad(true)
        .setMaxLines(1)
        .setEllipsize(TextUtils.TruncateAt.END)
        .build()

      val padY = (1.5f * scale).roundToInt()
      val heightPx = (layout.height + padY * 2).coerceIn(18, 56)
      val bmp = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
      val canvas = Canvas(bmp)
      canvas.save()
      canvas.translate(0f, padY.toFloat())
      layout.draw(canvas)
      canvas.restore()
      bmp
    }.getOrNull()
  }

  /**
   * Renders song title (using bundled Dangrek for Khmer + optically equalized Outfit for English)
   * and optional artist suffix onto a crisp bitmap for RemoteViews.
   */
  private fun renderStyledTitleOrFooterBitmap(
    context: Context,
    titleText: String,
    artistText: String,
    khmerLyricsFont: com.example.ui.theme.KhmerLyricsFont,
    titleColor: Int,
    artistColor: Int,
    baseTextSizeDp: Float,
    widthDp: Float = 284f,
    maxLines: Int = 1
  ): Bitmap? {
    return runCatching {
      val cleanTitle = titleText.trim()
      if (cleanTitle.isEmpty()) return null
      val cleanArtist = artistText.trim()
      val fullText = if (cleanArtist.isNotEmpty()) "$cleanTitle · $cleanArtist" else cleanTitle

      val scale = context.resources.displayMetrics.density.coerceIn(1.35f, 1.60f)
      val widthPx = (widthDp * scale).roundToInt().coerceIn(180, 660)

      val styledSpan = SpannableString(
        com.example.ui.theme.KhmerTypography.buildStyledSpannable(
          context = context,
          text = fullText,
          isTitle = true,
          khmerLyricsFont = khmerLyricsFont,
          isBold = true
        )
      )
      styledSpan.setSpan(
        ForegroundColorSpan(titleColor),
        0,
        cleanTitle.length.coerceAtMost(styledSpan.length),
        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
      )
      if (cleanArtist.isNotEmpty() && cleanTitle.length < styledSpan.length) {
        styledSpan.setSpan(
          ForegroundColorSpan(artistColor),
          cleanTitle.length,
          styledSpan.length,
          Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
      }

      val baseTypeface = com.example.ui.theme.KhmerTypography.loadOutfitTypeface(context)
      val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = baseTypeface
        color = titleColor
        textSize = baseTextSizeDp * scale
        setShadowLayer(2.2f * scale, 0f, 0.8f * scale, 0x55000000)
      }

      val layout = StaticLayout.Builder
        .obtain(styledSpan, 0, styledSpan.length, paint, widthPx)
        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
        .setIncludePad(true)
        .setMaxLines(maxLines)
        .setEllipsize(TextUtils.TruncateAt.END)
        .build()

      val padY = (1.5f * scale).roundToInt()
      val heightPx = (layout.height + padY * 2).coerceIn(20, 96)
      val bmp = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
      val canvas = Canvas(bmp)
      canvas.save()
      canvas.translate(0f, padY.toFloat())
      layout.draw(canvas)
      canvas.restore()
      bmp
    }.getOrNull()
  }

  /**
   * Renders AURIX UI chrome text (widget tags, artist subtitles, empty CTA) using bundled
   * Kantumruy Pro (Khmer) + Inter (English) bitmaps so home screen widgets never fall back to
   * system fonts.
   */
  private fun renderStyledUiBitmap(
    context: Context,
    text: String,
    textColor: Int,
    baseTextSizeDp: Float,
    isBold: Boolean,
    widthDp: Float = 220f,
    wrapToTextWidth: Boolean = false
  ): Bitmap? {
    return runCatching {
      val trimmed = text.trim()
      if (trimmed.isEmpty()) return null
      val scale = context.resources.displayMetrics.density.coerceIn(1.35f, 1.60f)
      val maxWidthPx = (widthDp * scale).roundToInt().coerceIn(80, 480)
      val styled = com.example.ui.theme.KhmerTypography.buildStyledSpannable(
        context = context,
        text = trimmed,
        isTitle = false,
        khmerLyricsFont = com.example.ui.theme.KhmerLyricsFont.KANTUMRUY_PRO,
        isBold = isBold
      )
      val baseTypeface = com.example.ui.theme.KhmerTypography.loadUiTypeface(context, isBold = isBold)
      val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = baseTypeface
        color = textColor
        textSize = baseTextSizeDp * scale
        if (isBold) {
          setShadowLayer(1.6f * scale, 0f, 0.7f * scale, 0x44000000)
        }
      }
      val measuredPx = if (wrapToTextWidth) {
        (StaticLayout.getDesiredWidth(styled, paint).roundToInt() + (4f * scale).roundToInt())
          .coerceIn(28, maxWidthPx)
      } else {
        maxWidthPx
      }
      val layout = StaticLayout.Builder
        .obtain(styled, 0, styled.length, paint, measuredPx)
        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
        .setIncludePad(true)
        .setMaxLines(1)
        .setEllipsize(TextUtils.TruncateAt.END)
        .build()

      val padY = (1f * scale).roundToInt()
      val heightPx = (layout.height + padY * 2).coerceIn(16, 64)
      val bmp = Bitmap.createBitmap(measuredPx, heightPx, Bitmap.Config.ARGB_8888)
      val canvas = Canvas(bmp)
      canvas.save()
      canvas.translate(0f, padY.toFloat())
      layout.draw(canvas)
      canvas.restore()
      bmp
    }.getOrNull()
  }

  private fun parseRawLrcLines(rawLrc: String): List<LyricLine> {
    if (rawLrc.isBlank()) return emptyList()
    if (rawLrc == cachedRawLrcKey) return cachedParsedLrcLines
    val parsed = runCatching {
      val doc = LrcParser.parse(rawLrc)
      if (!doc.isSynced) emptyList()
      else doc.lines.map { LyricLine(it.timestampMs, it.text) }
    }.getOrDefault(emptyList())
    cachedRawLrcKey = rawLrc
    cachedParsedLrcLines = parsed
    return parsed
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
          .size(360)
          .build()
        val res = context.imageLoader.execute(req)
        val rawBmp = (res as? SuccessResult)?.drawable?.toBitmap(config = Bitmap.Config.ARGB_8888)
        rawBmp?.let { smartCropArtworkCore(it) }
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

  /**
   * Trims dark letterbox/pillarbox margins (common on YouTube thumbnails) and center-crops
   * to a clean square core so neither album art nor ambient glow ever shows dark side bars.
   */
  internal fun smartCropArtworkCore(source: Bitmap): Bitmap {
    val w = source.width
    val h = source.height
    if (w < 16 || h < 16) return source

    fun isDarkPixel(c: Int): Boolean {
      val r = (c shr 16) and 0xFF
      val g = (c shr 8) and 0xFF
      val b = c and 0xFF
      return (r + g + b) < 42
    }

    val maxTrimX = (w * 0.28f).roundToInt()
    val maxTrimY = (h * 0.22f).roundToInt()
    var left = 0
    while (left < maxTrimX) {
      val sampleCount = 8
      var dark = 0
      for (i in 1..sampleCount) {
        val sy = (h * i) / (sampleCount + 1)
        if (isDarkPixel(source.getPixel(left, sy))) dark++
      }
      if (dark >= sampleCount - 1) left++ else break
    }
    var right = w - 1
    while (w - 1 - right < maxTrimX && right > left + 16) {
      val sampleCount = 8
      var dark = 0
      for (i in 1..sampleCount) {
        val sy = (h * i) / (sampleCount + 1)
        if (isDarkPixel(source.getPixel(right, sy))) dark++
      }
      if (dark >= sampleCount - 1) right-- else break
    }
    var top = 0
    while (top < maxTrimY) {
      val sampleCount = 8
      var dark = 0
      for (i in 1..sampleCount) {
        val sx = left + ((right - left) * i) / (sampleCount + 1)
        if (isDarkPixel(source.getPixel(sx, top))) dark++
      }
      if (dark >= sampleCount - 1) top++ else break
    }
    var bottom = h - 1
    while (h - 1 - bottom < maxTrimY && bottom > top + 16) {
      val sampleCount = 8
      var dark = 0
      for (i in 1..sampleCount) {
        val sx = left + ((right - left) * i) / (sampleCount + 1)
        if (isDarkPixel(source.getPixel(sx, bottom))) dark++
      }
      if (dark >= sampleCount - 1) bottom-- else break
    }

    val trimmedW = (right - left + 1).coerceAtLeast(16)
    val trimmedH = (bottom - top + 1).coerceAtLeast(16)
    val side = min(trimmedW, trimmedH)
    val cropX = left + (trimmedW - side) / 2
    val cropY = top + (trimmedH - side) / 2

    val cropped = Bitmap.createBitmap(source, cropX, cropY, side, side)
    return if (side > 300) {
      Bitmap.createScaledBitmap(cropped, 300, 300, true)
    } else {
      cropped
    }
  }

  private fun getOrRenderRoundedAlbumArt(context: Context, rawArt: Bitmap?): Bitmap {
    if (rawArt != null && cachedRoundedArtBitmap != null) {
      return cachedRoundedArtBitmap!!
    }
    val cacheDir = File(context.filesDir, CACHE_DIR_NAME).apply { mkdirs() }
    val diskRoundedFile = File(cacheDir, CACHED_ROUNDED_ART_FILE)

    val sizePx = 180
    val cornerPx = sizePx * 0.24f
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
        intArrayOf(0xFF7965FF.toInt(), 0xFF7EF0C5.toInt(), 0xFF16121F.toInt()),
        floatArrayOf(0f, 0.55f, 1f),
        Shader.TileMode.CLAMP
      )
      canvas.drawRoundRect(rect, cornerPx, cornerPx, paint)
      paint.shader = null
      runCatching {
        val orbiDrawable = ResourcesCompat.getDrawable(context.resources, R.drawable.ic_widget_orbi, null)
        if (orbiDrawable != null) {
          val pad = 48
          orbiDrawable.setBounds(pad, pad, sizePx - pad, sizePx - pad)
          orbiDrawable.draw(canvas)
        }
      }
    }

    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      style = Paint.Style.STROKE
      strokeWidth = 2.0f
      color = 0x26FFFFFF
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
   * Pre-renders a small blurred, color-extracted bitmap of the cover art for the ambient album
   * glow layer behind Glass/Color surfaces, preserving high text contrast across styles.
   */
  private fun getOrRenderBackgroundBitmap(
    context: Context,
    state: WidgetSnapshotState,
    rawArt: Bitmap?
  ): Bitmap {
    val renderKey = buildString {
      append("v4|")
      append(state.songId)
      append('|').append(state.artworkUrl)
      append('|').append(state.ambientGlowEnabled)
      append('|').append((state.blurLevel).roundToInt())
      append('|').append(rawArt != null)
    }
    if (renderKey == cachedGlowRenderKey && cachedGlowBitmap != null) {
      return cachedGlowBitmap!!
    }

    val cacheDir = File(context.filesDir, CACHE_DIR_NAME).apply { mkdirs() }
    val diskGlowFile = File(cacheDir, CACHED_GLOW_BG_FILE)

    val widthPx = 220
    val heightPx = 128
    val rect = RectF(0f, 0f, widthPx.toFloat(), heightPx.toFloat())
    val bgConfig = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      Bitmap.Config.RGB_565
    } else {
      Bitmap.Config.ARGB_8888
    }
    val output = Bitmap.createBitmap(widthPx, heightPx, bgConfig)
    val layerCanvas = Canvas(output)
    layerCanvas.drawColor(0xFF0E1211.toInt())

    if (rawArt != null && state.ambientGlowEnabled) {
      val palette = runCatching { Palette.from(rawArt).generate() }.getOrNull()
      val vibrant = palette?.getVibrantColor(0xFF7EF0C5.toInt()) ?: 0xFF7EF0C5.toInt()
      val muted = palette?.getDarkVibrantColor(palette.getMutedColor(0xFFCFC4F2.toInt()))
        ?: 0xFFCFC4F2.toInt()

      val blurredArt = createBlurredGlowBitmap(rawArt, state.blurLevel.coerceAtLeast(50f), widthPx, heightPx)
      layerCanvas.drawBitmap(blurredArt, 0f, 0f, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))

      val bloom1 = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(
          widthPx * 0.24f,
          heightPx * 0.30f,
          widthPx * 0.72f,
          intArrayOf((vibrant and 0x00FFFFFF) or (0x99 shl 24), Color.TRANSPARENT),
          floatArrayOf(0f, 1f),
          Shader.TileMode.CLAMP
        )
      }
      layerCanvas.drawRect(rect, bloom1)

      val bloom2 = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(
          widthPx * 0.80f,
          heightPx * 0.78f,
          widthPx * 0.68f,
          intArrayOf((muted and 0x00FFFFFF) or (0x80 shl 24), Color.TRANSPARENT),
          floatArrayOf(0f, 1f),
          Shader.TileMode.CLAMP
        )
      }
      layerCanvas.drawRect(rect, bloom2)
    } else {
      val glow1 = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(
          widthPx * 0.20f,
          heightPx * 0.24f,
          widthPx * 0.75f,
          intArrayOf(0x667EF0C5, 0x33CFC4F2, Color.TRANSPARENT),
          floatArrayOf(0f, 0.55f, 1f),
          Shader.TileMode.CLAMP
        )
      }
      layerCanvas.drawRect(rect, glow1)
    }

    cachedGlowRenderKey = renderKey
    cachedGlowBitmap = output
    runCatching {
      FileOutputStream(diskGlowFile).use { out ->
        output.compress(Bitmap.CompressFormat.PNG, 90, out)
      }
    }
    return output
  }

  /**
   * Renders a sculpted translucent glass pill with top specular gradient and thin crisp border
   * for widget buttons and the "Lyrics" badge, controlled by Glass level (0-100%, default 50%).
   */
  private fun renderGlassPillBitmap(
    widthPx: Int,
    heightPx: Int,
    cornerRadiusPx: Float,
    glassLevel: Float,
    isHighlighted: Boolean,
    isLightTheme: Boolean,
    isTintedAccent: Boolean = false
  ): Bitmap {
    val bmp = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val clampedGlass = glassLevel.coerceIn(0f, 1f)
    val rect = RectF(1.2f, 1.2f, widthPx - 1.2f, heightPx - 1.2f)
    val radius = cornerRadiusPx.coerceAtMost(heightPx * 0.5f)

    // Translucent gradient fill scaled by glassLevel (0-100%)
    val topAlpha = (20 + (clampedGlass * 66f).roundToInt()).coerceIn(16, 98)
    val bottomAlpha = (10 + (clampedGlass * 42f).roundToInt()).coerceIn(8, 64)
    val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      style = Paint.Style.FILL
      shader = when {
        isHighlighted -> LinearGradient(
          0f,
          0f,
          widthPx.toFloat(),
          heightPx.toFloat(),
          intArrayOf(
            Color.argb((topAlpha + 52).coerceAtMost(168), 0x43, 0xC7, 0xF4),
            Color.argb((bottomAlpha + 44).coerceAtMost(142), 0x79, 0x65, 0xFF)
          ),
          null,
          Shader.TileMode.CLAMP
        )
        isTintedAccent -> LinearGradient(
          0f,
          0f,
          widthPx.toFloat(),
          heightPx.toFloat(),
          intArrayOf(
            Color.argb((topAlpha + 26).coerceAtMost(118), 0x43, 0xC7, 0xF4),
            Color.argb((bottomAlpha + 20).coerceAtMost(88), 0x79, 0x65, 0xFF)
          ),
          null,
          Shader.TileMode.CLAMP
        )
        isLightTheme -> LinearGradient(
          0f,
          0f,
          0f,
          heightPx.toFloat(),
          intArrayOf(
            Color.argb(topAlpha, 0x15, 0x1F, 0x30),
            Color.argb(bottomAlpha, 0x15, 0x1F, 0x30)
          ),
          null,
          Shader.TileMode.CLAMP
        )
        else -> LinearGradient(
          0f,
          0f,
          0f,
          heightPx.toFloat(),
          intArrayOf(
            Color.argb(topAlpha, 0xE2, 0xF2, 0xFF),
            Color.argb(bottomAlpha, 0xC8, 0xDC, 0xFF)
          ),
          null,
          Shader.TileMode.CLAMP
        )
      }
    }
    canvas.drawRoundRect(rect, radius, radius, fillPaint)

    // Hairline glass specular border scaled by glassLevel
    val borderTopAlpha = (36 + (clampedGlass * 112f).roundToInt()).coerceIn(26, 168)
    val borderBotAlpha = (14 + (clampedGlass * 54f).roundToInt()).coerceIn(12, 84)
    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      style = Paint.Style.STROKE
      strokeWidth = 2f
      shader = when {
        isHighlighted -> LinearGradient(
          0f,
          0f,
          widthPx.toFloat(),
          heightPx.toFloat(),
          intArrayOf(
            Color.argb((borderTopAlpha + 42).coerceAtMost(225), 0x7A, 0xEC, 0xFF),
            Color.argb((borderBotAlpha + 36).coerceAtMost(155), 0x9B, 0x7A, 0xFF)
          ),
          null,
          Shader.TileMode.CLAMP
        )
        isTintedAccent -> LinearGradient(
          0f,
          0f,
          widthPx.toFloat(),
          heightPx.toFloat(),
          intArrayOf(
            Color.argb((borderTopAlpha + 26).coerceAtMost(195), 0x80, 0xEE, 0xFF),
            Color.argb((borderBotAlpha + 18).coerceAtMost(120), 0x9B, 0x7A, 0xFF)
          ),
          null,
          Shader.TileMode.CLAMP
        )
        isLightTheme -> LinearGradient(
          0f,
          0f,
          0f,
          heightPx.toFloat(),
          intArrayOf(
            Color.argb(borderTopAlpha, 0x27, 0x33, 0x48),
            Color.argb(borderBotAlpha, 0x27, 0x33, 0x48)
          ),
          null,
          Shader.TileMode.CLAMP
        )
        else -> LinearGradient(
          0f,
          0f,
          0f,
          heightPx.toFloat(),
          intArrayOf(
            Color.argb(borderTopAlpha, 0xE2, 0xF2, 0xFF),
            Color.argb(borderBotAlpha, 0xC8, 0xDC, 0xFF)
          ),
          null,
          Shader.TileMode.CLAMP
        )
      }
    }
    canvas.drawRoundRect(rect, radius, radius, borderPaint)
    return bmp
  }

  /**
   * Lush, seamless edge-to-edge album glow: center-crops the clean artwork core (zoomed 1.18x
   * so no edge artifacts leak in), boosts color richness slightly, and applies a 4-pass blur
   * scaled smoothly by Blur level (0-100%, default 60%).
   */
  private fun createBlurredGlowBitmap(
    source: Bitmap,
    blurPercent: Float,
    targetWidth: Int,
    targetHeight: Int
  ): Bitmap {
    val clampedBlur = blurPercent.coerceIn(0f, 100f)
    val sampleW = 128
    val sampleH = 76
    val small = Bitmap.createBitmap(sampleW, sampleH, Bitmap.Config.ARGB_8888)
    val smallCanvas = Canvas(small)

    // Center-crop source onto sampleW x sampleH with 1.18x overscan zoom so edges are rich & seamless
    val srcW = source.width.toFloat()
    val srcH = source.height.toFloat()
    val targetRatio = sampleW.toFloat() / sampleH.toFloat()
    val srcRatio = srcW / srcH
    val cropW: Float
    val cropH: Float
    if (srcRatio > targetRatio) {
      cropH = srcH / 1.18f
      cropW = cropH * targetRatio
    } else {
      cropW = srcW / 1.18f
      cropH = cropW / targetRatio
    }
    val cx = srcW * 0.5f
    val cy = srcH * 0.5f
    val srcRect = Rect(
      (cx - cropW * 0.5f).roundToInt().coerceIn(0, source.width - 1),
      (cy - cropH * 0.5f).roundToInt().coerceIn(0, source.height - 1),
      (cx + cropW * 0.5f).roundToInt().coerceIn(1, source.width),
      (cy + cropH * 0.5f).roundToInt().coerceIn(1, source.height)
    )
    val dstRect = Rect(0, 0, sampleW, sampleH)

    // Gentle saturation boost (1.32x) so the ambient glow feels luminous, never muddy grey
    val satMatrix = ColorMatrix().apply { setSaturation(1.32f) }
    val artPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
      colorFilter = ColorMatrixColorFilter(satMatrix)
    }
    smallCanvas.drawBitmap(source, srcRect, dstRect, artPaint)

    val radius = ((clampedBlur / 100f) * 20f).roundToInt().coerceIn(1, 20)
    if (clampedBlur > 1f) {
      boxBlurInPlace(small, radius, passes = 4)
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
    requestCode: Int,
    providerClass: Class<*> = AurixMusicWidgetProvider::class.java
  ): PendingIntent {
    val intent = Intent(context, providerClass).apply {
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
