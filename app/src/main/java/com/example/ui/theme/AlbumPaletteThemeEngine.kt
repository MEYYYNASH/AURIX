package com.example.ui.theme

import android.graphics.Bitmap
import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette
import coil.imageLoader
import coil.request.SuccessResult
import com.example.model.Song
import com.example.ui.image.AurixImagePipeline
import com.example.ui.screens.artworkHueColors
import com.example.ui.screens.resolveArtworkModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Extracted Material You color seeds from the currently playing track's album art.
 */
data class AlbumDynamicPalette(
  val primary: Color,
  val secondary: Color,
  val tertiary: Color,
  val primaryContainerSeed: Color,
  val sourceKey: String = "",
  val isExtractedFromArt: Boolean = false
)

object AlbumPaletteThemeEngine {

  private val DefaultDarkInk = Color(0xFF0B0F1A)
  private val DefaultLightInk = Color(0xFFFFFFFF)

  fun fallbackPalette(
    accentColor: Color,
    sourceKey: String = ""
  ): AlbumDynamicPalette {
    val hsl = colorToHsl(accentColor)
    val sec = hslToColor((hsl[0] + 32f) % 360f, (hsl[1] * 0.72f).coerceIn(0.24f, 0.72f), hsl[2])
    val ter = hslToColor((hsl[0] + 68f) % 360f, (hsl[1] * 0.80f).coerceIn(0.28f, 0.82f), hsl[2])
    return AlbumDynamicPalette(
      primary = accentColor,
      secondary = sec,
      tertiary = ter,
      primaryContainerSeed = accentColor,
      sourceKey = sourceKey,
      isExtractedFromArt = false
    )
  }

  /**
   * Extracts vibrant & tonal Material You seed colors from an album art bitmap using AndroidX Palette.
   * Must be safe to call off the main thread.
   */
  fun extractFromBitmap(
    bitmap: Bitmap?,
    fallbackTitle: String,
    fallbackAccent: Color,
    sourceKey: String = ""
  ): AlbumDynamicPalette {
    if (bitmap == null || bitmap.isRecycled || bitmap.width <= 0 || bitmap.height <= 0) {
      return fallbackFromTitle(fallbackTitle, fallbackAccent, sourceKey)
    }

    val palette = runCatching {
      val filtered = Palette.from(bitmap)
        .maximumColorCount(24)
        .generate()
      if (filtered.vibrantSwatch != null || filtered.dominantSwatch != null || filtered.mutedSwatch != null) {
        filtered
      } else {
        Palette.from(bitmap)
          .maximumColorCount(16)
          .clearFilters()
          .generate()
      }
    }.getOrNull() ?: return fallbackFromTitle(fallbackTitle, fallbackAccent, sourceKey)

    val primaryRgb = palette.vibrantSwatch?.rgb
      ?: palette.lightVibrantSwatch?.rgb
      ?: palette.dominantSwatch?.rgb
      ?: palette.mutedSwatch?.rgb
      ?: palette.darkVibrantSwatch?.rgb
      ?: palette.darkMutedSwatch?.rgb
      ?: return fallbackFromTitle(fallbackTitle, fallbackAccent, sourceKey)

    val primaryColor = Color(primaryRgb)
    val primaryHsl = colorToHsl(primaryColor)

    val secondaryColor = (
      palette.mutedSwatch?.rgb
        ?: palette.lightMutedSwatch?.rgb
        ?: palette.darkMutedSwatch?.rgb
      )?.let { Color(it) }
      ?: hslToColor(
        hue = (primaryHsl[0] + 34f) % 360f,
        sat = (primaryHsl[1] * 0.65f).coerceIn(0.20f, 0.65f),
        light = primaryHsl[2].coerceIn(0.32f, 0.68f)
      )

    val tertiaryColor = (
      palette.darkVibrantSwatch?.rgb
        ?: palette.lightVibrantSwatch?.rgb
      )?.takeIf { it != primaryRgb && hueDistance(colorToHsl(Color(it))[0], primaryHsl[0]) >= 18f }
      ?.let { Color(it) }
      ?: hslToColor(
        hue = (primaryHsl[0] + 64f) % 360f,
        sat = (primaryHsl[1] * 0.78f).coerceIn(0.28f, 0.80f),
        light = primaryHsl[2].coerceIn(0.36f, 0.72f)
      )

    val containerSeed = (palette.dominantSwatch?.rgb ?: primaryRgb).let { Color(it) }

    return AlbumDynamicPalette(
      primary = primaryColor,
      secondary = secondaryColor,
      tertiary = tertiaryColor,
      primaryContainerSeed = containerSeed,
      sourceKey = sourceKey,
      isExtractedFromArt = true
    )
  }

  private fun fallbackFromTitle(
    fallbackTitle: String,
    fallbackAccent: Color,
    sourceKey: String
  ): AlbumDynamicPalette {
    if (fallbackTitle.isBlank()) {
      return fallbackPalette(fallbackAccent, sourceKey)
    }
    val (c1, c2) = artworkHueColors(fallbackTitle)
    val h1 = colorToHsl(c1)[0]
    val c3 = hslToColor((h1 + 92f) % 360f, 0.68f, 0.56f)
    return AlbumDynamicPalette(
      primary = c1,
      secondary = c2,
      tertiary = c3,
      primaryContainerSeed = c1,
      sourceKey = sourceKey,
      isExtractedFromArt = false
    )
  }

  /**
   * Builds a complete, WCAG-accessible Material 3 / Material You ColorScheme in pure Kotlin
   * from an [AlbumDynamicPalette]. Supports Light, Dark, and true-black AMOLED modes.
   */
  fun buildMaterialYouColorScheme(
    seedPalette: AlbumDynamicPalette,
    darkTheme: Boolean,
    amoledTheme: Boolean
  ): ColorScheme {
    val pHsl = colorToHsl(seedPalette.primary)
    val sHsl = colorToHsl(seedPalette.secondary)
    val tHsl = colorToHsl(seedPalette.tertiary)
    val cHsl = colorToHsl(seedPalette.primaryContainerSeed)

    val isNeutralSeed = pHsl[1] < 0.08f
    val neutralSatScale = if (isNeutralSeed) 0.10f else 1f

    return when {
      amoledTheme -> {
        val primary = hslToColor(
          hue = pHsl[0],
          sat = (pHsl[1].coerceIn(0.46f, 0.90f)) * neutralSatScale,
          light = pHsl[2].coerceIn(0.62f, 0.76f)
        )
        val onPrimary = pickContrastInk(primary)
        val primaryContainer = hslToColor(
          hue = cHsl[0],
          sat = (cHsl[1].coerceIn(0.32f, 0.68f)) * neutralSatScale,
          light = 0.22f
        )
        val onPrimaryContainer = pickContrastInk(
          background = primaryContainer,
          preferredLight = hslToColor(pHsl[0], (pHsl[1] * 0.75f).coerceIn(0.15f, 0.85f), 0.92f),
          preferredDark = DefaultDarkInk
        )
        val secondary = hslToColor(
          hue = sHsl[0],
          sat = (sHsl[1].coerceIn(0.24f, 0.65f)) * neutralSatScale,
          light = 0.68f
        )
        val onSecondary = pickContrastInk(secondary)
        val secondaryContainer = hslToColor(
          hue = sHsl[0],
          sat = (sHsl[1].coerceIn(0.22f, 0.52f)) * neutralSatScale,
          light = 0.20f
        )
        val onSecondaryContainer = pickContrastInk(
          background = secondaryContainer,
          preferredLight = hslToColor(sHsl[0], 0.50f * neutralSatScale, 0.90f),
          preferredDark = DefaultDarkInk
        )
        val tertiary = hslToColor(
          hue = tHsl[0],
          sat = (tHsl[1].coerceIn(0.32f, 0.78f)) * neutralSatScale,
          light = 0.70f
        )
        val onTertiary = pickContrastInk(tertiary)
        val tertiaryContainer = hslToColor(
          hue = tHsl[0],
          sat = (tHsl[1].coerceIn(0.26f, 0.58f)) * neutralSatScale,
          light = 0.22f
        )
        val onTertiaryContainer = pickContrastInk(
          background = tertiaryContainer,
          preferredLight = hslToColor(tHsl[0], 0.55f * neutralSatScale, 0.90f),
          preferredDark = DefaultDarkInk
        )

        val surfaceTintSat = (pHsl[1] * 0.26f).coerceIn(0.06f, 0.20f) * neutralSatScale
        val surface = hslToColor(pHsl[0], surfaceTintSat, 0.055f)
        val surfaceVariant = hslToColor(pHsl[0], surfaceTintSat, 0.125f)
        val outline = hslToColor(pHsl[0], surfaceTintSat, 0.24f)
        val onSurfaceMuted = hslToColor(pHsl[0], (surfaceTintSat * 1.1f).coerceIn(0.06f, 0.20f), 0.72f)

        darkColorScheme(
          primary = primary,
          onPrimary = onPrimary,
          primaryContainer = primaryContainer,
          onPrimaryContainer = onPrimaryContainer,
          inversePrimary = hslToColor(pHsl[0], (pHsl[1].coerceIn(0.45f, 0.85f)) * neutralSatScale, 0.40f),
          secondary = secondary,
          onSecondary = onSecondary,
          secondaryContainer = secondaryContainer,
          onSecondaryContainer = onSecondaryContainer,
          tertiary = tertiary,
          onTertiary = onTertiary,
          tertiaryContainer = tertiaryContainer,
          onTertiaryContainer = onTertiaryContainer,
          background = Color.Black,
          onBackground = DarkText,
          surface = surface,
          onSurface = DarkText,
          surfaceVariant = surfaceVariant,
          onSurfaceVariant = onSurfaceMuted,
          surfaceContainerLowest = Color.Black,
          surfaceContainerLow = hslToColor(pHsl[0], surfaceTintSat, 0.045f),
          surfaceContainer = surface,
          surfaceContainerHigh = hslToColor(pHsl[0], surfaceTintSat, 0.095f),
          surfaceContainerHighest = surfaceVariant,
          outline = outline,
          outlineVariant = hslToColor(pHsl[0], surfaceTintSat, 0.17f),
          error = HeartRed,
          onError = DefaultLightInk
        )
      }

      darkTheme -> {
        val primary = hslToColor(
          hue = pHsl[0],
          sat = (pHsl[1].coerceIn(0.46f, 0.88f)) * neutralSatScale,
          light = pHsl[2].coerceIn(0.60f, 0.74f)
        )
        val onPrimary = pickContrastInk(primary)
        val primaryContainer = hslToColor(
          hue = cHsl[0],
          sat = (cHsl[1].coerceIn(0.32f, 0.66f)) * neutralSatScale,
          light = 0.25f
        )
        val onPrimaryContainer = pickContrastInk(
          background = primaryContainer,
          preferredLight = hslToColor(pHsl[0], (pHsl[1] * 0.78f).coerceIn(0.18f, 0.85f), 0.92f),
          preferredDark = DefaultDarkInk
        )
        val secondary = hslToColor(
          hue = sHsl[0],
          sat = (sHsl[1].coerceIn(0.24f, 0.62f)) * neutralSatScale,
          light = 0.66f
        )
        val onSecondary = pickContrastInk(secondary)
        val secondaryContainer = hslToColor(
          hue = sHsl[0],
          sat = (sHsl[1].coerceIn(0.22f, 0.50f)) * neutralSatScale,
          light = 0.23f
        )
        val onSecondaryContainer = pickContrastInk(
          background = secondaryContainer,
          preferredLight = hslToColor(sHsl[0], 0.48f * neutralSatScale, 0.90f),
          preferredDark = DefaultDarkInk
        )
        val tertiary = hslToColor(
          hue = tHsl[0],
          sat = (tHsl[1].coerceIn(0.30f, 0.76f)) * neutralSatScale,
          light = 0.68f
        )
        val onTertiary = pickContrastInk(tertiary)
        val tertiaryContainer = hslToColor(
          hue = tHsl[0],
          sat = (tHsl[1].coerceIn(0.24f, 0.56f)) * neutralSatScale,
          light = 0.24f
        )
        val onTertiaryContainer = pickContrastInk(
          background = tertiaryContainer,
          preferredLight = hslToColor(tHsl[0], 0.52f * neutralSatScale, 0.90f),
          preferredDark = DefaultDarkInk
        )

        val bgSat = (pHsl[1] * 0.30f).coerceIn(0.08f, 0.24f) * neutralSatScale
        val background = hslToColor(pHsl[0], bgSat, 0.072f)
        val surface = hslToColor(pHsl[0], bgSat, 0.115f)
        val surfaceVariant = hslToColor(pHsl[0], bgSat, 0.175f)
        val onSurfaceMuted = hslToColor(pHsl[0], (bgSat * 0.90f).coerceIn(0.06f, 0.22f), 0.74f)

        darkColorScheme(
          primary = primary,
          onPrimary = onPrimary,
          primaryContainer = primaryContainer,
          onPrimaryContainer = onPrimaryContainer,
          inversePrimary = hslToColor(pHsl[0], (pHsl[1].coerceIn(0.45f, 0.85f)) * neutralSatScale, 0.40f),
          secondary = secondary,
          onSecondary = onSecondary,
          secondaryContainer = secondaryContainer,
          onSecondaryContainer = onSecondaryContainer,
          tertiary = tertiary,
          onTertiary = onTertiary,
          tertiaryContainer = tertiaryContainer,
          onTertiaryContainer = onTertiaryContainer,
          background = background,
          onBackground = DarkText,
          surface = surface,
          onSurface = DarkText,
          surfaceVariant = surfaceVariant,
          onSurfaceVariant = onSurfaceMuted,
          surfaceContainerLowest = hslToColor(pHsl[0], bgSat, 0.055f),
          surfaceContainerLow = hslToColor(pHsl[0], bgSat, 0.095f),
          surfaceContainer = surface,
          surfaceContainerHigh = hslToColor(pHsl[0], bgSat, 0.145f),
          surfaceContainerHighest = surfaceVariant,
          outline = hslToColor(pHsl[0], bgSat, 0.28f),
          outlineVariant = hslToColor(pHsl[0], bgSat, 0.20f),
          error = HeartRed,
          onError = DefaultLightInk
        )
      }

      else -> {
        val primary = hslToColor(
          hue = pHsl[0],
          sat = (pHsl[1].coerceIn(0.44f, 0.86f)) * neutralSatScale,
          light = pHsl[2].coerceIn(0.36f, 0.48f)
        )
        val onPrimary = pickContrastInk(primary)
        val primaryContainer = hslToColor(
          hue = cHsl[0],
          sat = (cHsl[1].coerceIn(0.36f, 0.78f)) * neutralSatScale,
          light = 0.89f
        )
        val onPrimaryContainer = pickContrastInk(
          background = primaryContainer,
          preferredLight = DefaultLightInk,
          preferredDark = hslToColor(pHsl[0], (pHsl[1] * 0.75f).coerceIn(0.20f, 0.85f), 0.15f)
        )
        val secondary = hslToColor(
          hue = sHsl[0],
          sat = (sHsl[1].coerceIn(0.28f, 0.66f)) * neutralSatScale,
          light = 0.42f
        )
        val onSecondary = pickContrastInk(secondary)
        val secondaryContainer = hslToColor(
          hue = sHsl[0],
          sat = (sHsl[1].coerceIn(0.26f, 0.60f)) * neutralSatScale,
          light = 0.90f
        )
        val onSecondaryContainer = pickContrastInk(
          background = secondaryContainer,
          preferredLight = DefaultLightInk,
          preferredDark = hslToColor(sHsl[0], 0.55f * neutralSatScale, 0.16f)
        )
        val tertiary = hslToColor(
          hue = tHsl[0],
          sat = (tHsl[1].coerceIn(0.32f, 0.78f)) * neutralSatScale,
          light = 0.44f
        )
        val onTertiary = pickContrastInk(tertiary)
        val tertiaryContainer = hslToColor(
          hue = tHsl[0],
          sat = (tHsl[1].coerceIn(0.28f, 0.64f)) * neutralSatScale,
          light = 0.89f
        )
        val onTertiaryContainer = pickContrastInk(
          background = tertiaryContainer,
          preferredLight = DefaultLightInk,
          preferredDark = hslToColor(tHsl[0], 0.58f * neutralSatScale, 0.16f)
        )

        val bgSat = (pHsl[1] * 0.24f).coerceIn(0.06f, 0.18f) * neutralSatScale
        val background = hslToColor(pHsl[0], bgSat, 0.945f)
        val surface = hslToColor(pHsl[0], (bgSat * 0.55f), 0.985f)
        val surfaceVariant = hslToColor(pHsl[0], bgSat, 0.895f)
        val onSurfaceInk = hslToColor(pHsl[0], (bgSat * 0.50f), 0.11f)
        val onSurfaceMuted = hslToColor(pHsl[0], (bgSat * 0.65f), 0.36f)

        lightColorScheme(
          primary = primary,
          onPrimary = onPrimary,
          primaryContainer = primaryContainer,
          onPrimaryContainer = onPrimaryContainer,
          inversePrimary = hslToColor(pHsl[0], (pHsl[1].coerceIn(0.45f, 0.88f)) * neutralSatScale, 0.72f),
          secondary = secondary,
          onSecondary = onSecondary,
          secondaryContainer = secondaryContainer,
          onSecondaryContainer = onSecondaryContainer,
          tertiary = tertiary,
          onTertiary = onTertiary,
          tertiaryContainer = tertiaryContainer,
          onTertiaryContainer = onTertiaryContainer,
          background = background,
          onBackground = onSurfaceInk,
          surface = surface,
          onSurface = onSurfaceInk,
          surfaceVariant = surfaceVariant,
          onSurfaceVariant = onSurfaceMuted,
          surfaceContainerLowest = Color.White,
          surfaceContainerLow = hslToColor(pHsl[0], bgSat * 0.6f, 0.965f),
          surfaceContainer = hslToColor(pHsl[0], bgSat * 0.8f, 0.935f),
          surfaceContainerHigh = hslToColor(pHsl[0], bgSat, 0.910f),
          surfaceContainerHighest = surfaceVariant,
          outline = hslToColor(pHsl[0], bgSat, 0.76f),
          outlineVariant = hslToColor(pHsl[0], bgSat, 0.84f),
          error = HeartRed,
          onError = DefaultLightInk
        )
      }
    }
  }

  /**
   * Builds a WCAG-contrast-aware ColorScheme when Dynamic Album Color is off
   * and the user selects a manual accent color.
   */
  fun buildManualAccentColorScheme(
    accentColor: Color,
    darkTheme: Boolean,
    amoledTheme: Boolean
  ): ColorScheme {
    val palette = fallbackPalette(accentColor)
    val tonal = buildMaterialYouColorScheme(palette, darkTheme = darkTheme, amoledTheme = amoledTheme)
    val onPrimary = pickContrastInk(accentColor)
    return when {
      amoledTheme -> darkColorScheme(
        primary = accentColor,
        onPrimary = onPrimary,
        primaryContainer = tonal.primaryContainer,
        onPrimaryContainer = tonal.onPrimaryContainer,
        inversePrimary = tonal.inversePrimary,
        secondary = tonal.secondary,
        onSecondary = tonal.onSecondary,
        secondaryContainer = tonal.secondaryContainer,
        onSecondaryContainer = tonal.onSecondaryContainer,
        tertiary = tonal.tertiary,
        onTertiary = tonal.onTertiary,
        tertiaryContainer = tonal.tertiaryContainer,
        onTertiaryContainer = tonal.onTertiaryContainer,
        background = Color.Black,
        onBackground = DarkText,
        surface = AurixCardDark,
        onSurface = DarkText,
        surfaceVariant = AurixBorderDark,
        onSurfaceVariant = DarkMuted,
        outline = AurixBorderDark,
        error = HeartRed,
        onError = DefaultLightInk
      )
      darkTheme -> darkColorScheme(
        primary = accentColor,
        onPrimary = onPrimary,
        primaryContainer = tonal.primaryContainer,
        onPrimaryContainer = tonal.onPrimaryContainer,
        inversePrimary = tonal.inversePrimary,
        secondary = tonal.secondary,
        onSecondary = tonal.onSecondary,
        secondaryContainer = tonal.secondaryContainer,
        onSecondaryContainer = tonal.onSecondaryContainer,
        tertiary = tonal.tertiary,
        onTertiary = tonal.onTertiary,
        tertiaryContainer = tonal.tertiaryContainer,
        onTertiaryContainer = tonal.onTertiaryContainer,
        background = DarkBackground,
        onBackground = DarkText,
        surface = DarkSurface,
        onSurface = DarkText,
        surfaceVariant = DarkSurfaceVariant,
        onSurfaceVariant = DarkMuted,
        outline = AurixBorderDark,
        error = HeartRed,
        onError = DefaultLightInk
      )
      else -> lightColorScheme(
        primary = accentColor,
        onPrimary = onPrimary,
        primaryContainer = tonal.primaryContainer,
        onPrimaryContainer = tonal.onPrimaryContainer,
        inversePrimary = tonal.inversePrimary,
        secondary = tonal.secondary,
        onSecondary = tonal.onSecondary,
        secondaryContainer = tonal.secondaryContainer,
        onSecondaryContainer = tonal.onSecondaryContainer,
        tertiary = tonal.tertiary,
        onTertiary = tonal.onTertiary,
        tertiaryContainer = tonal.tertiaryContainer,
        onTertiaryContainer = tonal.onTertiaryContainer,
        background = LightBackground,
        onBackground = LightText,
        surface = LightSurface,
        onSurface = LightText,
        surfaceVariant = LightSurfaceVariant,
        onSurfaceVariant = LightMuted,
        error = HeartRed,
        onError = DefaultLightInk
      )
    }
  }

  fun pickContrastInk(
    background: Color,
    preferredLight: Color = DefaultLightInk,
    preferredDark: Color = DefaultDarkInk,
    minContrastRatio: Float = 4.5f
  ): Color {
    val lightRatio = contrastRatio(preferredLight, background)
    val darkRatio = contrastRatio(preferredDark, background)
    if (lightRatio >= minContrastRatio && darkRatio >= minContrastRatio) {
      return if (lightRatio >= darkRatio) preferredLight else preferredDark
    }
    if (lightRatio >= minContrastRatio) return preferredLight
    if (darkRatio >= minContrastRatio) return preferredDark
    val pureWhiteRatio = contrastRatio(Color.White, background)
    val pureBlackRatio = contrastRatio(Color.Black, background)
    return if (pureWhiteRatio >= pureBlackRatio) Color.White else Color.Black
  }

  fun contrastRatio(foreground: Color, background: Color): Float {
    val l1 = relativeLuminance(foreground)
    val l2 = relativeLuminance(background)
    val lighter = max(l1, l2)
    val darker = min(l1, l2)
    return (lighter + 0.05f) / (darker + 0.05f)
  }

  fun relativeLuminance(color: Color): Float {
    fun linearize(channel: Float): Float {
      return if (channel <= 0.03928f) channel / 12.92f
      else ((channel + 0.055f) / 1.055f).pow(2.4f)
    }
    val r = linearize(color.red)
    val g = linearize(color.green)
    val b = linearize(color.blue)
    return 0.2126f * r + 0.7152f * g + 0.0722f * b
  }

  fun colorToHsl(color: Color): FloatArray {
    val r = color.red.coerceIn(0f, 1f)
    val g = color.green.coerceIn(0f, 1f)
    val b = color.blue.coerceIn(0f, 1f)
    val maxC = max(r, max(g, b))
    val minC = min(r, min(g, b))
    val l = (maxC + minC) * 0.5f
    val d = maxC - minC
    val s: Float
    var h: Float
    if (d <= 0.0001f) {
      h = 0f
      s = 0f
    } else {
      s = if (l > 0.5f) d / (2f - maxC - minC) else d / (maxC + minC)
      h = when (maxC) {
        r -> (g - b) / d + (if (g < b) 6f else 0f)
        g -> (b - r) / d + 2f
        else -> (r - g) / d + 4f
      }
      h *= 60f
    }
    return floatArrayOf(((h % 360f) + 360f) % 360f, s.coerceIn(0f, 1f), l.coerceIn(0f, 1f))
  }

  fun hslToColor(hue: Float, sat: Float, light: Float): Color {
    val h = ((hue % 360f) + 360f) % 360f
    val s = sat.coerceIn(0f, 1f)
    val l = light.coerceIn(0f, 1f)
    if (s <= 0.0001f) {
      return Color(l, l, l, 1f)
    }
    fun hueToRgb(p: Float, q: Float, tRaw: Float): Float {
      var t = tRaw
      if (t < 0f) t += 1f
      if (t > 1f) t -= 1f
      return when {
        t < 1f / 6f -> p + (q - p) * 6f * t
        t < 1f / 2f -> q
        t < 2f / 3f -> p + (q - p) * (2f / 3f - t) * 6f
        else -> p
      }
    }
    val q = if (l < 0.5f) l * (1f + s) else l + s - l * s
    val p = 2f * l - q
    val hk = h / 360f
    val r = hueToRgb(p, q, hk + 1f / 3f).coerceIn(0f, 1f)
    val g = hueToRgb(p, q, hk).coerceIn(0f, 1f)
    val b = hueToRgb(p, q, hk - 1f / 3f).coerceIn(0f, 1f)
    return Color(r, g, b, 1f)
  }

  private fun hueDistance(h1: Float, h2: Float): Float {
    val diff = abs(h1 - h2) % 360f
    return min(diff, 360f - diff)
  }

  fun colorToHex(color: Color): String {
    val argb = color.toArgb()
    return String.format(java.util.Locale.US, "#%06X", 0xFFFFFF and argb)
  }
}

/**
 * Observes [song] and extracts a live Material You [AlbumDynamicPalette] from its album artwork
 * via AndroidX Palette API whenever [enabled] is true.
 */
@Composable
fun rememberAlbumDynamicPalette(
  song: Song?,
  enabled: Boolean,
  fallbackAccent: Color
): AlbumDynamicPalette {
  val context = LocalContext.current
  val sourceKey = remember(song?.id, song?.artworkUrl) {
    if (song == null) "" else "${song.id}|${song.artworkUrl}"
  }
  val defaultSeed = remember(fallbackAccent, sourceKey) {
    AlbumPaletteThemeEngine.fallbackPalette(fallbackAccent, sourceKey)
  }
  var extracted by remember(enabled, sourceKey) {
    mutableStateOf(defaultSeed)
  }

  LaunchedEffect(enabled, sourceKey, fallbackAccent) {
    if (!enabled || song == null) {
      extracted = AlbumPaletteThemeEngine.fallbackPalette(fallbackAccent, sourceKey)
      return@LaunchedEffect
    }
    val model = resolveArtworkModel(song.artworkUrl, song.source)
    if (model == null) {
      extracted = AlbumPaletteThemeEngine.extractFromBitmap(
        bitmap = null,
        fallbackTitle = song.title,
        fallbackAccent = fallbackAccent,
        sourceKey = sourceKey
      )
      return@LaunchedEffect
    }
    val sampled = withContext(Dispatchers.IO) {
      try {
        val request = AurixImagePipeline.buildColorSamplingRequest(
          context = context,
          model = model,
          sampleSizePx = 96
        )
        val result = context.imageLoader.execute(request)
        val drawable = (result as? SuccessResult)?.drawable ?: return@withContext null
        val rawBitmap = runCatching { drawable.toBitmap() }.getOrNull() ?: return@withContext null
        val softBitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && rawBitmap.config == Bitmap.Config.HARDWARE) {
          runCatching { rawBitmap.copy(Bitmap.Config.ARGB_8888, false) }.getOrNull() ?: rawBitmap
        } else {
          rawBitmap
        }
        withContext(Dispatchers.Default) {
          AlbumPaletteThemeEngine.extractFromBitmap(
            bitmap = softBitmap,
            fallbackTitle = song.title,
            fallbackAccent = fallbackAccent,
            sourceKey = sourceKey
          )
        }
      } catch (ce: CancellationException) {
        throw ce
      } catch (_: Throwable) {
        null
      }
    }
    extracted = sampled ?: AlbumPaletteThemeEngine.extractFromBitmap(
      bitmap = null,
      fallbackTitle = song.title,
      fallbackAccent = fallbackAccent,
      sourceKey = sourceKey
    )
  }

  return extracted
}
