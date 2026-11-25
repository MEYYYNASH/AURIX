package com.example.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.util.LruCache
import androidx.palette.graphics.Palette
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Palette extracted from album artwork (or curated presets when no art is available).
 */
data class VinylCoverPalette(
  val coverBase: Int,
  val coverLight: Int,
  val coverDark: Int
)

/**
 * Pure Canvas renderer for the AURIX "Vinyl" widget style:
 * - Cream paper (#EFE6D6), ink (#2A2118), top-right soft white highlight,
 *   1px horizontal paper fibers every 3px at rgba(120,90,50,.035),
 *   and 1px inner ring rgba(42,33,24,.08).
 * - Half-visible, vertically centered spinning vinyl disc (7s per turn):
 *   1. Grooves (#141414 2px / #262626 2px)
 *   2. Rotating conic light sheen (2 white bands ~17% and ~13%)
 *   3. Label (30% radius: 8-30% coverBase, 4-8% coverLight, 0-4% paper hole)
 *   4. Small #EFE6D6 dot at 40% from top (5% size) so spin is clearly visible
 *   5. Non-rotating 135-deg white highlight (20% -> 0% by 42%), 2px #0B0B0B rim, soft shadow.
 */
object VinylWidgetRenderer {
  const val PAPER_COLOR = 0xFFEFE6D6.toInt()
  const val INK_COLOR = 0xFF2A2118.toInt()
  const val INK_65_COLOR = 0xA62A2118.toInt()
  const val INK_60_COLOR = 0x992A2118.toInt()
  const val INK_55_COLOR = 0x8C2A2118.toInt()
  const val INK_50_COLOR = 0x802A2118.toInt()
  const val TRACK_COLOR = 0x2E2A2118
  const val INNER_RING_COLOR = 0x142A2118
  const val FIBER_COLOR = 0x09785A32 // rgba(120,90,50, 0.035)
  const val RIM_COLOR = 0xFF0B0B0B.toInt()
  const val GROOVE_DARK = 0xFF141414.toInt()
  const val GROOVE_LIGHT = 0xFF262626.toInt()
  const val TURN_DURATION_MS = 7_000L

  val PRESET_BLUE = VinylCoverPalette(
    coverBase = 0xFF3B6FB0.toInt(),
    coverLight = 0xFF8FC5FF.toInt(),
    coverDark = 0xFF18294A.toInt()
  )
  val PRESET_WARM = VinylCoverPalette(
    coverBase = 0xFFD9774A.toInt(),
    coverLight = 0xFFFFC08A.toInt(),
    coverDark = 0xFF421F12.toInt()
  )
  val PRESET_GREEN = VinylCoverPalette(
    coverBase = 0xFF2F9E7A.toInt(),
    coverLight = 0xFF8FF0C8.toInt(),
    coverDark = 0xFF103428.toInt()
  )

  private val paperBgCache = object : LruCache<String, Bitmap>(6) {}
  private val discCache = object : LruCache<String, Bitmap>(16) {}

  fun clearCache() {
    paperBgCache.evictAll()
    discCache.evictAll()
  }

  /**
   * 7s per turn linear rotation.
   * Pausing freezes the disc at its current angle; reducedMotion keeps it static at 0°.
   */
  fun computeVinylAngleDegrees(
    positionMs: Long,
    isPlaying: Boolean,
    reducedMotion: Boolean
  ): Float {
    if (reducedMotion) return 0f
    val clamped = positionMs.coerceAtLeast(0L)
    val mod = clamped % TURN_DURATION_MS
    // Both playing and paused derive from the frozen/advancing track position so pausing
    // freezes at the exact angle and resuming continues smoothly from that angle.
    val degrees = (mod.toFloat() / TURN_DURATION_MS.toFloat()) * 360f
    return if (isPlaying) degrees else degrees
  }

  fun resolveFallbackPreset(seedKey: String): VinylCoverPalette {
    val clean = seedKey.trim()
    if (clean.isEmpty()) return PRESET_BLUE
    return when (abs(clean.hashCode()) % 3) {
      0 -> PRESET_BLUE
      1 -> PRESET_WARM
      else -> PRESET_GREEN
    }
  }

  fun extractCoverPalette(rawArt: Bitmap?, seedKey: String = ""): VinylCoverPalette {
    if (rawArt == null) return resolveFallbackPreset(seedKey)
    val palette = runCatching { Palette.from(rawArt).generate() }.getOrNull()
      ?: return resolveFallbackPreset(seedKey)
    val base = palette.getVibrantColor(
      palette.getDominantColor(PRESET_BLUE.coverBase)
    )
    val light = palette.getLightVibrantColor(
      lightenColor(base, 0.42f)
    )
    val dark = palette.getDarkVibrantColor(
      palette.getDarkMutedColor(darkenColor(base, 0.56f))
    )
    return VinylCoverPalette(
      coverBase = forceOpaque(base),
      coverLight = forceOpaque(light),
      coverDark = forceOpaque(dark)
    )
  }

  /**
   * Renders the cream paper card surface (#EFE6D6):
   * - Top-right soft white highlight
   * - Horizontal paper fibers: 1px lines every 3px at rgba(120,90,50,.035)
   * - 1px inner ring rgba(42,33,24,.08)
   */
  fun getOrRenderVinylPaperBackground(
    widthPx: Int,
    heightPx: Int,
    cornerRadiusPx: Float = 56f
  ): Bitmap {
    val w = widthPx.coerceIn(120, 960)
    val h = heightPx.coerceIn(64, 640)
    val key = "paper|$w|$h|${cornerRadiusPx.roundToInt()}"
    paperBgCache.get(key)?.let { return it }

    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val rect = RectF(0f, 0f, w.toFloat(), h.toFloat())

    val clipPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    canvas.drawRoundRect(rect, cornerRadiusPx, cornerRadiusPx, clipPaint)
    clipPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)

    val layer = canvas.saveLayer(rect, clipPaint)
    canvas.drawColor(PAPER_COLOR)

    // Soft white highlight from top-right corner
    val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      shader = RadialGradient(
        w * 0.92f,
        h * 0.06f,
        max(w, h) * 0.88f,
        intArrayOf(0x88FFFFFF.toInt(), 0x28FFFFFF, 0x00FFFFFF),
        floatArrayOf(0f, 0.45f, 1f),
        Shader.TileMode.CLAMP
      )
    }
    canvas.drawRect(rect, highlightPaint)

    // Faint horizontal paper fibers: 1px every 3px at rgba(120,90,50,.035)
    val fiberPaint = Paint().apply {
      color = FIBER_COLOR
      strokeWidth = 1f
      isAntiAlias = false
    }
    var y = 0f
    while (y < h) {
      canvas.drawLine(0f, y, w.toFloat(), y, fiberPaint)
      y += 3f
    }

    // 1px inner ring rgba(42,33,24,.08)
    val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      style = Paint.Style.STROKE
      strokeWidth = 1.5f
      color = INNER_RING_COLOR
    }
    canvas.drawRoundRect(
      RectF(1f, 1f, w - 1f, h - 1f),
      max(2f, cornerRadiusPx - 1f),
      max(2f, cornerRadiusPx - 1f),
      ringPaint
    )

    canvas.restoreToCount(layer)
    paperBgCache.put(key, bmp)
    return bmp
  }

  /**
   * Renders either the exact left-cropped half vinyl disc (width = diameter/2, height = diameter,
   * center at x=0 so left half is clipped by the card's left edge) or the full disc.
   */
  fun renderVinylDiscBitmap(
    diameterPx: Int,
    palette: VinylCoverPalette,
    rotationDegrees: Float,
    cropHalfOnLeft: Boolean = true
  ): Bitmap {
    val d = diameterPx.coerceIn(72, 420)
    val quantizedAngle = ((rotationDegrees % 360f) + 360f) % 360f
    val bucketAngle = (quantizedAngle / 2f).roundToInt() * 2
    val cacheKey = "disc|$d|${palette.coverBase}|${palette.coverLight}|$bucketAngle|$cropHalfOnLeft"
    discCache.get(cacheKey)?.let { return it }

    val r = d * 0.5f
    val outW = if (cropHalfOnLeft) (d / 2).coerceAtLeast(36) else d
    val outH = d
    val bmp = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)

    val cx = if (cropHalfOnLeft) 0f else r
    val cy = r

    // Static outer soft shadow beneath the disc (non-rotating)
    val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      shader = RadialGradient(
        cx,
        cy + r * 0.04f,
        r,
        intArrayOf(0x73000000, 0x33000000, 0x00000000),
        floatArrayOf(0.84f, 0.94f, 1.0f),
        Shader.TileMode.CLAMP
      )
    }
    canvas.drawCircle(cx, cy + r * 0.03f, r, shadowPaint)

    val vinylRadius = r * 0.94f

    // --- ROTATING LAYERS ---
    canvas.save()
    canvas.rotate(quantizedAngle, cx, cy)

    // Base dark vinyl fill
    val baseDiscPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      style = Paint.Style.FILL
      color = GROOVE_DARK
    }
    canvas.drawCircle(cx, cy, vinylRadius, baseDiscPaint)

    // Grooves: repeating concentric rings, #141414 for 2px then #262626 for 2px
    val labelRadius = vinylRadius * 0.30f
    val groovePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      style = Paint.Style.STROKE
      strokeWidth = 2f
      color = GROOVE_LIGHT
    }
    var grooveR = labelRadius + 4f
    while (grooveR < vinylRadius - 3f) {
      canvas.drawCircle(cx, cy, grooveR, groovePaint)
      grooveR += 4f
    }

    // Conic light sheen: two soft white bands (~17% and ~13% opacity), rotating with the disc
    val conicPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      style = Paint.Style.FILL
      shader = SweepGradient(
        cx,
        cy,
        intArrayOf(
          0x00FFFFFF,
          0x2BFFFFFF, // ~17% white band 1
          0x00FFFFFF,
          0x00FFFFFF,
          0x21FFFFFF, // ~13% white band 2
          0x00FFFFFF
        ),
        floatArrayOf(0.0f, 0.14f, 0.30f, 0.50f, 0.65f, 0.84f)
      )
    }
    canvas.drawCircle(cx, cy, vinylRadius - 1f, conicPaint)

    // Label (radius 30% of the disc):
    // - 8..30% main label in coverBase
    // - 4..8% thin ring in coverLight
    // - 0..4% center hole in paper color (#EFE6D6)
    val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      style = Paint.Style.FILL
      color = palette.coverBase
    }
    canvas.drawCircle(cx, cy, labelRadius, labelPaint)

    val lightRingRadius = vinylRadius * 0.08f
    val lightRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      style = Paint.Style.FILL
      color = palette.coverLight
    }
    canvas.drawCircle(cx, cy, lightRingRadius, lightRingPaint)

    val holeRadius = max(3f, vinylRadius * 0.04f)
    val holePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      style = Paint.Style.FILL
      color = PAPER_COLOR
    }
    canvas.drawCircle(cx, cy, holeRadius, holePaint)

    // Small paper-colored dot on the label at 40% from the top (y = cy - 0.20*R), 5% disc diameter
    val spinDotRadius = max(2.2f, (vinylRadius * 2f) * 0.025f) // 5% diameter = 2.5% radius
    val dotOffsetX = vinylRadius * 0.11f // slightly right of vertical axis so half-disc reveals at 0°
    val dotOffsetY = -vinylRadius * 0.18f
    canvas.drawCircle(cx + dotOffsetX, cy + dotOffsetY, spinDotRadius, holePaint)

    canvas.restore()
    // --- END ROTATING LAYERS ---

    // Static layer 1: 135-degree white highlight (20% to 0% by 42%)
    val staticHighlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      style = Paint.Style.FILL
      shader = LinearGradient(
        cx - vinylRadius * 0.7f,
        cy - vinylRadius * 0.7f,
        cx + vinylRadius * 0.7f,
        cy + vinylRadius * 0.7f,
        intArrayOf(0x33FFFFFF, 0x00FFFFFF, 0x00FFFFFF),
        floatArrayOf(0.0f, 0.42f, 1.0f),
        Shader.TileMode.CLAMP
      )
    }
    canvas.drawCircle(cx, cy, vinylRadius, staticHighlightPaint)

    // Static layer 2: 2px #0B0B0B outer rim
    val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      style = Paint.Style.STROKE
      strokeWidth = 2.5f
      color = RIM_COLOR
    }
    canvas.drawCircle(cx, cy, vinylRadius - 1.25f, rimPaint)

    discCache.put(cacheKey, bmp)
    return bmp
  }

  fun formatTabularTime(ms: Long): String {
    val totalSec = (ms.coerceAtLeast(0L) / 1000L)
    val mins = totalSec / 60L
    val secs = totalSec % 60L
    return "$mins:${secs.toString().padStart(2, '0')}"
  }

  private fun forceOpaque(argb: Int): Int = (0xFF shl 24) or (argb and 0x00FFFFFF)

  internal fun lightenColor(argb: Int, fraction: Float): Int {
    val f = fraction.coerceIn(0f, 1f)
    val r = (argb ushr 16) and 0xFF
    val g = (argb ushr 8) and 0xFF
    val b = argb and 0xFF
    val nr = (r + ((255 - r) * f)).roundToInt().coerceIn(0, 255)
    val ng = (g + ((255 - g) * f)).roundToInt().coerceIn(0, 255)
    val nb = (b + ((255 - b) * f)).roundToInt().coerceIn(0, 255)
    return (0xFF shl 24) or (nr shl 16) or (ng shl 8) or nb
  }

  internal fun darkenColor(argb: Int, fraction: Float): Int {
    val f = (1f - fraction.coerceIn(0f, 1f))
    val r = (((argb ushr 16) and 0xFF) * f).roundToInt().coerceIn(0, 255)
    val g = (((argb ushr 8) and 0xFF) * f).roundToInt().coerceIn(0, 255)
    val b = ((argb and 0xFF) * f).roundToInt().coerceIn(0, 255)
    return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
  }
}
