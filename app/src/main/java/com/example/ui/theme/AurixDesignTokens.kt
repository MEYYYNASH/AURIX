package com.example.ui.theme

import androidx.compose.animation.core.Easing
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import kotlin.math.abs

/**
 * Crash-proof double-precision CSS cubic-bezier(x1, y1, x2, y2) Easing.
 *
 * Jetpack Compose's stock CubicBezierEasing uses single-precision analytical root finding,
 * which throws IllegalArgumentException ("has no solution at 0.99999994") on valid overshoot
 * curves such as (0.34, 1.56, 0.64, 1.0). Solving B_x(t) = fraction in Double precision via
 * Newton-Raphson with guaranteed bisection fallback produces the exact same curve while
 * remaining 100% total and finite for every Float input.
 */
@Immutable
class SafeCubicBezierEasing(
  private val x1: Float,
  private val y1: Float,
  private val x2: Float,
  private val y2: Float
) : Easing {
  private val cx = 3.0 * x1.toDouble()
  private val bx = 3.0 * (x2.toDouble() - x1.toDouble()) - cx
  private val ax = 1.0 - cx - bx

  private val cy = 3.0 * y1.toDouble()
  private val by = 3.0 * (y2.toDouble() - y1.toDouble()) - cy
  private val ay = 1.0 - cy - by

  private fun sampleX(t: Double): Double = ((ax * t + bx) * t + cx) * t
  private fun sampleY(t: Double): Double = ((ay * t + by) * t + cy) * t
  private fun sampleDerivativeX(t: Double): Double = (3.0 * ax * t + 2.0 * bx) * t + cx

  override fun transform(fraction: Float): Float {
    if (fraction.isNaN() || fraction <= 0f) return 0f
    if (fraction >= 1f) return 1f

    val targetX = fraction.toDouble()
    var t = targetX
    for (i in 0 until 6) {
      val err = sampleX(t) - targetX
      if (abs(err) < 1e-7) {
        return sampleY(t).toFloat()
      }
      val slope = sampleDerivativeX(t)
      if (abs(slope) < 1e-6) break
      val nextT = t - err / slope
      if (nextT < 0.0 || nextT > 1.0) break
      t = nextT
    }

    // Guaranteed monotonic bisection fallback on [0.0, 1.0] (2^-28 < 4e-9)
    var lo = 0.0
    var hi = 1.0
    var mid = targetX
    for (i in 0 until 28) {
      val x = sampleX(mid)
      if (abs(x - targetX) < 1e-7) break
      if (x < targetX) lo = mid else hi = mid
      mid = 0.5 * (lo + hi)
    }
    return sampleY(mid).toFloat()
  }

  override fun equals(other: Any?): Boolean {
    return other is SafeCubicBezierEasing &&
      x1 == other.x1 && y1 == other.y1 && x2 == other.x2 && y2 == other.y2
  }

  override fun hashCode(): Int {
    var result = x1.hashCode()
    result = 31 * result + y1.hashCode()
    result = 31 * result + x2.hashCode()
    result = 31 * result + y2.hashCode()
    return result
  }
}

/**
 * Pixel-faithful design tokens for AURIX Onboarding & shared Dark Liquid Glass surfaces.
 */
object AurixDesignTokens {
  // Core Palette
  val Background = Color(0xFF080C14)
  val TextPrimary = Color(0xFFF7F9FF)
  val TextMuted = Color(0xFF929DB0)
  val AccentADefault = Color(0xFF43C7F4)
  val AccentB = Color(0xFF7965FF)
  val DarkInk = Color(0xFF06101C)

  // Glass surface tokens
  val GlassFill = Color.White.copy(alpha = 0.07f)
  val GlassBorder = Color.White.copy(alpha = 0.14f)
  val GlassActiveFill = Color.White.copy(alpha = 0.16f)
  val GlassPickedFill = Color.White.copy(alpha = 0.10f)
  val BadgeBorder = Color.White.copy(alpha = 0.16f)
  val ProgressTrack = Color.White.copy(alpha = 0.14f)
  val GlassBlurRadius = 14.dp
  val GlowBlurRadius = 80.dp
  const val GlowOpacity = 0.42f

  // Accent Presets (exact order & hex values)
  data class AccentPreset(
    val id: String,
    val hex: String,
    val color: Color,
    val labelRes: Int
  )

  val AccentPresets: List<AccentPreset> = listOf(
    AccentPreset("blue", "#43C7F4", Color(0xFF43C7F4), R.string.onboarding_color_blue),
    AccentPreset("purple", "#A855F7", Color(0xFFA855F7), R.string.onboarding_color_purple),
    AccentPreset("red", "#EF4444", Color(0xFFEF4444), R.string.onboarding_color_red),
    AccentPreset("green", "#22C55E", Color(0xFF22C55E), R.string.onboarding_color_green)
  )

  // Backdrop modes wired to real app Dynamic Background preferences
  enum class BackdropPreset(
    val id: String,
    val labelRes: Int,
    val dynamicBgEnabled: Boolean,
    val blurDp: Float,
    val dimLevel: Float
  ) {
    AURORA("aurora", R.string.onboarding_backdrop_aurora, true, 60f, 0.40f),
    MIDNIGHT("midnight", R.string.onboarding_backdrop_midnight, true, 80f, 0.65f),
    MINIMAL("minimal", R.string.onboarding_backdrop_minimal, false, 0f, 0.40f);

    companion object {
      fun fromSettings(dynamicBg: Boolean, dimLevel: Float): BackdropPreset {
        return when {
          !dynamicBg -> MINIMAL
          dimLevel >= 0.55f -> MIDNIGHT
          else -> AURORA
        }
      }
    }
  }

  // Fonts: Sora (400/600/800) for English; Noto Sans Khmer (400/600/800) for Khmer
  val SoraFontFamily = FontFamily(
    Font(R.font.sora, FontWeight.Normal),
    Font(R.font.sora, FontWeight.SemiBold),
    Font(R.font.sora, FontWeight.Bold),
    Font(R.font.sora, FontWeight.ExtraBold)
  )

  val NotoSansKhmerFontFamily = FontFamily(
    Font(R.font.noto_sans_khmer, FontWeight.Normal),
    Font(R.font.noto_sans_khmer_semibold, FontWeight.SemiBold),
    Font(R.font.noto_sans_khmer_bold, FontWeight.Bold),
    Font(R.font.noto_sans_khmer_bold, FontWeight.ExtraBold)
  )

  fun fontFamily(isKhmer: Boolean): FontFamily =
    if (isKhmer) NotoSansKhmerFontFamily else SoraFontFamily

  // Line-height multipliers (Khmer increases title 1.45, body 1.8)
  const val TitleLineHeightEn = 1.2f
  const val BodyLineHeightEn = 1.55f
  const val TitleLineHeightKm = 1.45f
  const val BodyLineHeightKm = 1.8f

  // Exact Motion Curves (crash-proof double-precision cubic-bezier)
  val MainEasing: Easing = SafeCubicBezierEasing(0.2f, 0.8f, 0.2f, 1.0f)
  val SpringEasing: Easing = SafeCubicBezierEasing(0.34f, 1.56f, 0.64f, 1.0f)
  val FinishRevealEasing: Easing = SafeCubicBezierEasing(0.7f, 0.0f, 0.2f, 1.0f)

  // Shared 135deg gradient helper (top-left -> bottom-right)
  fun accentGradientBrush(accentA: Color, accentB: Color = AccentB): Brush =
    Brush.linearGradient(colors = listOf(accentA, accentB))

  // HSL helper matching CSS hsl(h s% l%) for exact cover art gradients
  fun hslColor(hueDegrees: Float, saturation: Float, lightness: Float, alpha: Float = 1f): Color {
    val h = ((hueDegrees % 360f) + 360f) % 360f
    val s = saturation.coerceIn(0f, 1f)
    val l = lightness.coerceIn(0f, 1f)
    val c = (1f - kotlin.math.abs(2f * l - 1f)) * s
    val x = c * (1f - kotlin.math.abs((h / 60f) % 2f - 1f))
    val m = l - c / 2f
    val (r1, g1, b1) = when {
      h < 60f -> Triple(c, x, 0f)
      h < 120f -> Triple(x, c, 0f)
      h < 180f -> Triple(0f, c, x)
      h < 240f -> Triple(0f, x, c)
      h < 300f -> Triple(x, 0f, c)
      else -> Triple(c, 0f, x)
    }
    return Color(
      red = (r1 + m).coerceIn(0f, 1f),
      green = (g1 + m).coerceIn(0f, 1f),
      blue = (b1 + m).coerceIn(0f, 1f),
      alpha = alpha
    )
  }

  fun coverGradientBrush(baseHue: Float): Brush =
    Brush.linearGradient(
      colors = listOf(
        hslColor(baseHue, 0.85f, 0.62f),
        hslColor(baseHue + 50f, 0.80f, 0.45f)
      )
    )

  fun findPresetForHex(hex: String?): AccentPreset {
    if (hex.isNullOrBlank()) return AccentPresets.first()
    val clean = hex.trim().uppercase()
    return AccentPresets.firstOrNull { it.hex.equals(clean, ignoreCase = true) }
      ?: AccentPresets.first()
  }
}
