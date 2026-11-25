package com.example.widget

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 10 distinct styles for the resizable AURIX Now Playing home-screen widget.
 */
enum class NowPlayingWidgetStyle(
  val id: String,
  val displayNameEn: String,
  val displayNameKm: String,
  val usesSystemDynamicColors: Boolean = false
) {
  AURA("widget_style_aura", "Aura", "អូរ៉ា"),
  RING("widget_style_ring", "Ring", "រង្វង់"),
  TYPE("widget_style_type", "Type", "អក្សរ"),
  FROST("widget_style_frost", "Frost", "កញ្ចក់"),
  LIGHT("widget_style_light", "Light", "ភ្លឺ", usesSystemDynamicColors = true),
  NIGHT("widget_style_night", "Night", "រាត្រី"),
  VINYL("widget_style_vinyl", "Vinyl", "វីនីល"),
  PIXEL("widget_style_pixel", "Pixel", "ភីកសែល"),
  PAPER("widget_style_paper", "Paper", "ក្រដាស", usesSystemDynamicColors = true),
  FLOAT("widget_style_float", "Float", "អណ្តែត");

  companion object {
    const val DEFAULT_ID = "widget_style_aura"

    fun fromId(raw: String?): NowPlayingWidgetStyle {
      if (raw.isNullOrBlank()) return AURA
      val key = raw.trim().lowercase()
      return entries.firstOrNull {
        it.id == key || it.id.removePrefix("widget_style_") == key
      } ?: AURA
    }
  }
}

/**
 * 3 responsive sizes for the resizable AURIX Now Playing widget.
 */
enum class NowPlayingWidgetSize(
  val id: String,
  val labelEn: String,
  val labelKm: String
) {
  COMPACT("widget_size_compact", "Compact", "តូច 4×1"),
  PLAYER("widget_size_player", "Player", "ចាក់តន្ត្រី 4×2"),
  LYRICS("widget_size_lyrics", "Lyrics", "ទំនុកច្រៀង 4×2+");

  companion object {
    /**
     * Resolves the effective layout size from Android widget dimensions and settings:
     * - Compact (4x1): height < 96dp
     * - Lyrics (tall 4x2+): height >= 165dp when showLyrics && hasLyrics, else falls back to Player
     * - Player (4x2): everything between 96dp and 164dp (or fallback when no lyrics / showLyrics off)
     */
    fun resolveSize(
      @Suppress("UNUSED_PARAMETER") minWidthDp: Int,
      minHeightDp: Int,
      showLyrics: Boolean,
      hasLyrics: Boolean
    ): NowPlayingWidgetSize {
      return when {
        minHeightDp in 1..95 -> COMPACT
        minHeightDp >= 165 && showLyrics && hasLyrics -> LYRICS
        else -> PLAYER
      }
    }
  }
}

data class NowPlayingWidgetThemeTokens(
  val cardBg: Int,
  val cardSecondary: Int,
  val textPrimary: Int,
  val textSecondary: Int,
  val accent: Int,
  val playButtonBg: Int,
  val playButtonIcon: Int,
  val border: Int,
  val progressTrack: Int,
  val progressFill: Int,
  val isLightSurface: Boolean
)

/**
 * Pure JVM color math & Material You / Palette token resolver for AURIX Now Playing widget.
 */
object NowPlayingThemeResolver {
  val DEFAULT_COVER_COLORS = VinylCoverPalette(
    coverBase = 0xFF3B6FB0.toInt(),
    coverLight = 0xFF8FC5FF.toInt(),
    coverDark = 0xFF18294A.toInt()
  )

  fun extractPaletteColors(rawArt: Bitmap?, seedKey: String = ""): VinylCoverPalette {
    if (rawArt == null) {
      return if (seedKey.isBlank()) DEFAULT_COVER_COLORS
      else VinylWidgetRenderer.resolveFallbackPreset(seedKey)
    }
    return VinylWidgetRenderer.extractCoverPalette(rawArt, seedKey)
  }

  /**
   * Resolves WCAG >= 4.5:1 verified color tokens for each of the 10 styles.
   * Material You dynamic colors apply ONLY to Light & Paper when matchSystemTheme is true.
   */
  fun resolveTokens(
    style: NowPlayingWidgetStyle,
    coverColors: VinylCoverPalette,
    matchSystemTheme: Boolean,
    systemAccentColor: Int? = null,
    systemLightBg: Int? = null
  ): NowPlayingWidgetThemeTokens {
    val useDyn = matchSystemTheme && style.usesSystemDynamicColors
    val dynAccent = if (useDyn && systemAccentColor != null) systemAccentColor else coverColors.coverBase
    val dynLightSurface = if (useDyn && systemLightBg != null) systemLightBg else null

    return when (style) {
      NowPlayingWidgetStyle.AURA -> NowPlayingWidgetThemeTokens(
        cardBg = coverColors.coverDark,
        cardSecondary = coverColors.coverBase,
        textPrimary = 0xFFFFFFFF.toInt(),
        textSecondary = 0xCCFFFFFF.toInt(),
        accent = ensureContrastOnDark(coverColors.coverLight, coverColors.coverDark),
        playButtonBg = 0xFFFFFFFF.toInt(),
        playButtonIcon = 0xFF101522.toInt(),
        border = 0x38FFFFFF,
        progressTrack = 0x36FFFFFF,
        progressFill = ensureContrastOnDark(coverColors.coverLight, coverColors.coverDark),
        isLightSurface = false
      )
      NowPlayingWidgetStyle.RING -> NowPlayingWidgetThemeTokens(
        cardBg = 0xFF111624.toInt(),
        cardSecondary = coverColors.coverDark,
        textPrimary = 0xFFFFFFFF.toInt(),
        textSecondary = 0xB8FFFFFF.toInt(),
        accent = ensureContrastOnDark(coverColors.coverLight, 0xFF111624.toInt()),
        playButtonBg = ensureContrastOnDark(coverColors.coverLight, 0xFF111624.toInt()),
        playButtonIcon = 0xFF0A101D.toInt(),
        border = 0x2EFFFFFF,
        progressTrack = 0x2EFFFFFF,
        progressFill = ensureContrastOnDark(coverColors.coverLight, 0xFF111624.toInt()),
        isLightSurface = false
      )
      NowPlayingWidgetStyle.TYPE -> NowPlayingWidgetThemeTokens(
        cardBg = 0xFF141826.toInt(),
        cardSecondary = coverColors.coverBase,
        textPrimary = 0xFFFFFFFF.toInt(),
        textSecondary = 0xB8FFFFFF.toInt(),
        accent = ensureContrastOnDark(coverColors.coverLight, 0xFF141826.toInt()),
        playButtonBg = 0xFFFFFFFF.toInt(),
        playButtonIcon = 0xFF141826.toInt(),
        border = 0x28FFFFFF,
        progressTrack = 0x2EFFFFFF,
        progressFill = 0xFFFFFFFF.toInt(),
        isLightSurface = false
      )
      NowPlayingWidgetStyle.FROST -> NowPlayingWidgetThemeTokens(
        cardBg = 0xFF161C2E.toInt(),
        cardSecondary = coverColors.coverBase,
        textPrimary = 0xFFFFFFFF.toInt(),
        textSecondary = 0xBDFFFFFF.toInt(),
        accent = 0xFF7EF0C5.toInt(),
        playButtonBg = 0x38FFFFFF,
        playButtonIcon = 0xFFFFFFFF.toInt(),
        border = 0x45FFFFFF,
        progressTrack = 0x33FFFFFF,
        progressFill = 0xFF7EF0C5.toInt(),
        isLightSurface = false
      )
      NowPlayingWidgetStyle.LIGHT -> {
        val bg = dynLightSurface ?: 0xFFF3F6FC.toInt()
        val primaryInk = ensureContrastOnLight(0xFF14172A.toInt(), bg)
        val accentInk = ensureContrastOnLight(dynAccent, bg)
        NowPlayingWidgetThemeTokens(
          cardBg = bg,
          cardSecondary = 0xFFE6ECF8.toInt(),
          textPrimary = primaryInk,
          textSecondary = withAlpha(primaryInk, 0xA8),
          accent = accentInk,
          playButtonBg = accentInk,
          playButtonIcon = 0xFFFFFFFF.toInt(),
          border = 0x2414172A,
          progressTrack = 0x2614172A,
          progressFill = accentInk,
          isLightSurface = true
        )
      }
      NowPlayingWidgetStyle.NIGHT -> NowPlayingWidgetThemeTokens(
        cardBg = 0xFF07090F.toInt(),
        cardSecondary = 0xFF0E1320.toInt(),
        textPrimary = 0xFFFFFFFF.toInt(),
        textSecondary = 0xADFFFFFF.toInt(),
        accent = ensureContrastOnDark(coverColors.coverLight, 0xFF07090F.toInt()),
        playButtonBg = ensureContrastOnDark(coverColors.coverLight, 0xFF07090F.toInt()),
        playButtonIcon = 0xFF07090F.toInt(),
        border = 0x1FFFFFFF,
        progressTrack = 0x24FFFFFF,
        progressFill = ensureContrastOnDark(coverColors.coverLight, 0xFF07090F.toInt()),
        isLightSurface = false
      )
      NowPlayingWidgetStyle.VINYL -> NowPlayingWidgetThemeTokens(
        cardBg = VinylWidgetRenderer.PAPER_COLOR,
        cardSecondary = coverColors.coverBase,
        textPrimary = VinylWidgetRenderer.INK_COLOR,
        textSecondary = VinylWidgetRenderer.INK_65_COLOR,
        accent = VinylWidgetRenderer.INK_COLOR,
        playButtonBg = VinylWidgetRenderer.INK_COLOR,
        playButtonIcon = VinylWidgetRenderer.PAPER_COLOR,
        border = VinylWidgetRenderer.INNER_RING_COLOR,
        progressTrack = VinylWidgetRenderer.TRACK_COLOR,
        progressFill = VinylWidgetRenderer.INK_COLOR,
        isLightSurface = true
      )
      NowPlayingWidgetStyle.PIXEL -> NowPlayingWidgetThemeTokens(
        cardBg = 0xFF10131C.toInt(),
        cardSecondary = 0xFF1B2030.toInt(),
        textPrimary = 0xFFF4F7FF.toInt(),
        textSecondary = 0xB8F4F7FF.toInt(),
        accent = 0xFF7EF0C5.toInt(),
        playButtonBg = 0xFF7EF0C5.toInt(),
        playButtonIcon = 0xFF09120D.toInt(),
        border = 0xFF425270.toInt(),
        progressTrack = 0x33FFFFFF,
        progressFill = 0xFF7EF0C5.toInt(),
        isLightSurface = false
      )
      NowPlayingWidgetStyle.PAPER -> {
        val bg = dynLightSurface ?: 0xFFF6F0E4.toInt()
        val ink = ensureContrastOnLight(0xFF231B15.toInt(), bg)
        val accentInk = ensureContrastOnLight(dynAccent, bg)
        NowPlayingWidgetThemeTokens(
          cardBg = bg,
          cardSecondary = 0xFFEAE1CF.toInt(),
          textPrimary = ink,
          textSecondary = withAlpha(ink, 0x9E),
          accent = accentInk,
          playButtonBg = ink,
          playButtonIcon = bg,
          border = 0x26231B15,
          progressTrack = 0x26231B15,
          progressFill = ink,
          isLightSurface = true
        )
      }
      NowPlayingWidgetStyle.FLOAT -> NowPlayingWidgetThemeTokens(
        cardBg = 0xFF0A0D16.toInt(),
        cardSecondary = 0xB80A0D16.toInt(),
        textPrimary = 0xFFFFFFFF.toInt(),
        textSecondary = 0xCCF5F7FF.toInt(),
        accent = 0xFFFFFFFF.toInt(),
        playButtonBg = 0xFFFFFFFF.toInt(),
        playButtonIcon = 0xFF0C101B.toInt(),
        border = 0x3DFFFFFF,
        progressTrack = 0x42FFFFFF,
        progressFill = 0xFFFFFFFF.toInt(),
        isLightSurface = false
      )
    }
  }

  fun readSystemAccentIfAvailable(context: Context): Int? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
    return runCatching {
      context.getColor(android.R.color.system_accent1_600)
    }.getOrNull()
  }

  fun readSystemLightSurfaceIfAvailable(context: Context): Int? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
    return runCatching {
      context.getColor(android.R.color.system_neutral1_50)
    }.getOrNull()
  }

  internal fun ensureContrastOnLight(fg: Int, lightBg: Int): Int {
    var candidate = forceOpaque(fg)
    val bgOpaque = forceOpaque(lightBg)
    if (WidgetGlassRenderer.contrastRatio(candidate, bgOpaque) >= 4.5) return candidate
    for (step in 1..12) {
      candidate = VinylWidgetRenderer.darkenColor(candidate, 0.14f)
      if (WidgetGlassRenderer.contrastRatio(candidate, bgOpaque) >= 4.5) return candidate
    }
    return 0xFF14172A.toInt()
  }

  internal fun ensureContrastOnDark(fg: Int, darkBg: Int): Int {
    var candidate = forceOpaque(fg)
    val bgOpaque = forceOpaque(darkBg)
    if (WidgetGlassRenderer.contrastRatio(candidate, bgOpaque) >= 4.5) return candidate
    for (step in 1..12) {
      candidate = VinylWidgetRenderer.lightenColor(candidate, 0.16f)
      if (WidgetGlassRenderer.contrastRatio(candidate, bgOpaque) >= 4.5) return candidate
    }
    return 0xFFFFFFFF.toInt()
  }

  private fun forceOpaque(argb: Int): Int = (0xFF shl 24) or (argb and 0x00FFFFFF)

  private fun withAlpha(argb: Int, alpha: Int): Int =
    ((alpha and 0xFF) shl 24) or (argb and 0x00FFFFFF)
}
