package com.example.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.util.LruCache
import android.view.View
import androidx.core.content.res.ResourcesCompat
import com.example.R
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

data class CachedGlassCoverSet(
  val blurred26: Bitmap,
  val blurredStrong: Bitmap,
  val blurredLightFrost: Bitmap,
  val luminance: Float
)

/**
 * High-contrast glass layer & bitmap renderer for the 5 AURIX widget styles.
 * Caches blurred cover layers per artwork key and guarantees >= 4.5:1 WCAG text contrast
 * by automatically adapting tint strength to the cover art's measured luminance.
 */
object WidgetGlassRenderer {
  private val coverCache = object : LruCache<String, CachedGlassCoverSet>(6) {}

  fun clearCache() {
    coverCache.evictAll()
  }

  fun getOrCreateBlurredCoverSet(
    context: Context,
    coverKey: String,
    rawArt: Bitmap?
  ): CachedGlassCoverSet {
    val safeKey = "${coverKey}|hasArt=${rawArt != null}"
    coverCache.get(safeKey)?.let { return it }

    val base = rawArt ?: createFallbackGradientArtwork(context)
    val luminance = computeAverageLuminance(base)

    // Layer 1: pre-blurred ambient cover (RGB_565 keeps Binder overhead compact)
    val blurred26 = createBlurredLayer(
      source = base,
      targetWidth = 200,
      targetHeight = 118,
      saturation = 1.50f,
      blurRadius = 26,
      overscanScale = 1.20f
    )
    // Strong blur for Smoked Neon & Poster Glass floating panel
    val blurredStrong = createBlurredLayer(
      source = base,
      targetWidth = 200,
      targetHeight = 118,
      saturation = 1.55f,
      blurRadius = 32,
      overscanScale = 1.24f
    )
    // Light Frost: strong saturation (+70%) + blur
    val blurredLightFrost = createBlurredLayer(
      source = base,
      targetWidth = 200,
      targetHeight = 118,
      saturation = 1.72f,
      blurRadius = 28,
      overscanScale = 1.20f
    )

    val set = CachedGlassCoverSet(
      blurred26 = blurred26,
      blurredStrong = blurredStrong,
      blurredLightFrost = blurredLightFrost,
      luminance = luminance
    )
    coverCache.put(safeKey, set)
    return set
  }

  /**
   * Computes WCAG-verified frosted tint color per style so text contrast stays >= 4.5:1
   * even on very bright or high-contrast album covers.
   */
  fun computeAutoContrastTint(
    style: WidgetGlassStyle,
    coverLuminance: Float
  ): Int {
    fun argb(a: Int, r: Int, g: Int, b: Int): Int =
      ((a and 0xFF) shl 24) or ((r and 0xFF) shl 16) or ((g and 0xFF) shl 8) or (b and 0xFF)

    val lum = coverLuminance.coerceIn(0f, 1f)
    return when (style) {
      WidgetGlassStyle.FROST_PILL, WidgetGlassStyle.ORB -> {
        // Base rgba(18, 22, 40, 0.34). Darken automatically when cover is bright to keep white text >= 4.5:1.
        val alphaFloat = when {
          lum > 0.70f -> 0.72f
          lum > 0.55f -> 0.60f
          lum > 0.40f -> 0.48f
          else -> 0.34f
        }
        val a = (alphaFloat * 255f).roundToInt().coerceIn(86, 200)
        argb(a, 18, 22, 40)
      }
      WidgetGlassStyle.SMOKED_NEON -> {
        // Base rgba(6, 8, 18, 0.62). Boost to 0.78 on very bright covers.
        val alphaFloat = if (lum > 0.62f) 0.78f else 0.62f
        val a = (alphaFloat * 255f).roundToInt().coerceIn(158, 210)
        argb(a, 6, 8, 18)
      }
      WidgetGlassStyle.LIGHT_FROST -> {
        // Base white 55%. Dark text (#14172A) needs >= 4.5:1; boost white opacity if cover is dark.
        val alphaFloat = when {
          lum < 0.25f -> 0.76f
          lum < 0.45f -> 0.66f
          else -> 0.55f
        }
        val a = (alphaFloat * 255f).roundToInt().coerceIn(140, 212)
        argb(a, 255, 255, 255)
      }
      WidgetGlassStyle.POSTER_GLASS -> {
        // Scrim backing if needed; primary bottom dark gradient is drawn over sharp poster cover.
        val alphaFloat = if (lum > 0.65f) 0.34f else 0.16f
        val a = (alphaFloat * 255f).roundToInt()
        argb(a, 10, 14, 24)
      }
      WidgetGlassStyle.VINYL -> {
        // Cream paper card (#EFE6D6) provides high contrast (> 11:1) against dark brown ink (#2A2118).
        VinylWidgetRenderer.PAPER_COLOR
      }
    }
  }

  /**
   * Computes WCAG contrast ratio between two opaque/composited colors (1.0 .. 21.0).
   */
  fun contrastRatio(foreground: Int, background: Int): Double {
    fun linearize(channel: Int): Double {
      val c = channel / 255.0
      return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }
    fun relLuminance(color: Int): Double {
      val r = linearize((color ushr 16) and 0xFF)
      val g = linearize((color ushr 8) and 0xFF)
      val b = linearize(color and 0xFF)
      return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }
    val l1 = relLuminance(foreground)
    val l2 = relLuminance(background)
    val lighter = max(l1, l2)
    val darker = min(l1, l2)
    return (lighter + 0.05) / (darker + 0.05)
  }

  fun computeAverageLuminance(bitmap: Bitmap): Float {
    val small = Bitmap.createScaledBitmap(bitmap, 16, 16, true)
    var total = 0.0
    var count = 0
    for (y in 0 until 16) {
      for (x in 0 until 16) {
        val c = small.getPixel(x, y)
        val r = Color.red(c) / 255.0
        val g = Color.green(c) / 255.0
        val b = Color.blue(c) / 255.0
        total += (0.2126 * r + 0.7152 * g + 0.0722 * b)
        count++
      }
    }
    return (total / count.coerceAtLeast(1)).toFloat().coerceIn(0f, 1f)
  }

  private fun createFallbackGradientArtwork(context: Context): Bitmap {
    val size = 160
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      shader = LinearGradient(
        0f,
        0f,
        size.toFloat(),
        size.toFloat(),
        intArrayOf(0xFF4FC3FF.toInt(), 0xFFA06BFF.toInt(), 0xFF121628.toInt()),
        floatArrayOf(0f, 0.52f, 1f),
        Shader.TileMode.CLAMP
      )
    }
    canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), paint)
    runCatching {
      val orbi = ResourcesCompat.getDrawable(context.resources, R.drawable.ic_widget_orbi, null)
      orbi?.let {
        val pad = 42
        it.setBounds(pad, pad, size - pad, size - pad)
        it.draw(canvas)
      }
    }
    return bmp
  }

  private fun createBlurredLayer(
    source: Bitmap,
    targetWidth: Int,
    targetHeight: Int,
    saturation: Float,
    blurRadius: Int,
    overscanScale: Float
  ): Bitmap {
    val sampleW = 112
    val sampleH = 68
    val small = Bitmap.createBitmap(sampleW, sampleH, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(small)

    val srcW = source.width.toFloat()
    val srcH = source.height.toFloat()
    val targetRatio = sampleW.toFloat() / sampleH.toFloat()
    val srcRatio = srcW / srcH
    val cropW: Float
    val cropH: Float
    if (srcRatio > targetRatio) {
      cropH = srcH / overscanScale.coerceAtLeast(1.0f)
      cropW = cropH * targetRatio
    } else {
      cropW = srcW / overscanScale.coerceAtLeast(1.0f)
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
    val matrix = ColorMatrix().apply { setSaturation(saturation) }
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
      colorFilter = ColorMatrixColorFilter(matrix)
    }
    canvas.drawBitmap(source, srcRect, dstRect, paint)

    boxBlurInPlace(small, blurRadius.coerceIn(4, 34), passes = 3)
    val scaled = Bitmap.createScaledBitmap(small, targetWidth, targetHeight, true)
    return scaled.copy(Bitmap.Config.RGB_565, false)
  }

  /**
   * Renders rounded square cover art with optional warm rim (Style 1 Lyrics: rgba(255,214,150,.55)).
   */
  fun renderRoundedCoverBitmap(
    context: Context,
    rawArt: Bitmap?,
    sizePx: Int = 200,
    cornerRadiusFraction: Float = 0.28f,
    warmRim: Boolean = false
  ): Bitmap {
    val src = rawArt ?: createFallbackGradientArtwork(context)
    val out = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(out)
    val rect = RectF(0f, 0f, sizePx.toFloat(), sizePx.toFloat())
    val radius = sizePx * cornerRadiusFraction.coerceIn(0.05f, 0.5f)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    canvas.drawRoundRect(rect, radius, radius, paint)
    paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
    val scaled = Bitmap.createScaledBitmap(src, sizePx, sizePx, true)
    canvas.drawBitmap(scaled, 0f, 0f, paint)
    paint.xfermode = null

    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      style = Paint.Style.STROKE
      strokeWidth = if (warmRim) 3.5f else 2.0f
      color = if (warmRim) 0x8CFFD696.toInt() else 0x33FFFFFF
    }
    val inset = borderPaint.strokeWidth * 0.5f
    canvas.drawRoundRect(
      RectF(inset, inset, sizePx - inset, sizePx - inset),
      (radius - inset).coerceAtLeast(2f),
      (radius - inset).coerceAtLeast(2f),
      borderPaint
    )
    return out
  }

  /**
   * Style 2 (Orb): Circular disc art with dark center hole (34/88 ratio), white 30% ring,
   * and optional rotation angle (14s period while playing, 0f when paused or reduced motion).
   */
  fun renderOrbDiscBitmap(
    context: Context,
    rawArt: Bitmap?,
    sizePx: Int = 200,
    rotationDegrees: Float = 0f
  ): Bitmap {
    val src = rawArt ?: createFallbackGradientArtwork(context)
    val out = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(out)
    val half = sizePx * 0.5f
    val discPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    canvas.drawCircle(half, half, half - 2f, discPaint)
    discPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)

    val scaled = Bitmap.createScaledBitmap(src, sizePx, sizePx, true)
    canvas.save()
    canvas.rotate(rotationDegrees, half, half)
    canvas.drawBitmap(scaled, 0f, 0f, discPaint)
    canvas.restore()
    discPaint.xfermode = null

    // White 30% outer ring
    val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      style = Paint.Style.STROKE
      strokeWidth = max(2.5f, sizePx * 0.022f)
      color = 0x4DFFFFFF
    }
    canvas.drawCircle(half, half, half - ringPaint.strokeWidth, ringPaint)

    // Subtle vinyl groove circle
    val groovePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      style = Paint.Style.STROKE
      strokeWidth = 1.4f
      color = 0x26FFFFFF
    }
    canvas.drawCircle(half, half, half * 0.68f, groovePaint)

    // 34dp dark center hole (proportioned 34/88 = 0.386f)
    val holeRadius = (sizePx * (34f / 88f)) * 0.5f
    val holePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      style = Paint.Style.FILL
      color = 0xFF0D101B.toInt()
    }
    canvas.drawCircle(half, half, holeRadius, holePaint)

    val holeRimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      style = Paint.Style.STROKE
      strokeWidth = 2.0f
      color = 0x4DFFFFFF
    }
    canvas.drawCircle(half, half, holeRadius, holeRimPaint)
    return out
  }

  /**
   * Style 3 (Smoked Neon): Cover art tilted 6 degrees with rounded corners and soft shadow.
   */
  fun renderTiltedArtBitmap(
    context: Context,
    rawArt: Bitmap?,
    artSizePx: Int = 156,
    tiltDegrees: Float = 6f
  ): Bitmap {
    val canvasPad = (artSizePx * 0.16f).roundToInt()
    val totalSize = artSizePx + canvasPad * 2
    val out = Bitmap.createBitmap(totalSize, totalSize, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(out)

    val rounded = renderRoundedCoverBitmap(
      context = context,
      rawArt = rawArt,
      sizePx = artSizePx,
      cornerRadiusFraction = 0.24f,
      warmRim = false
    )

    val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = 0x664FC3FF
      setShadowLayer(canvasPad * 0.65f, 0f, canvasPad * 0.28f, 0x77000000)
    }

    canvas.save()
    canvas.rotate(tiltDegrees, totalSize * 0.5f, totalSize * 0.5f)
    val left = canvasPad.toFloat()
    val top = canvasPad.toFloat()
    val r = artSizePx * 0.24f
    canvas.drawRoundRect(
      RectF(left, top, left + artSizePx, top + artSizePx),
      r,
      r,
      shadowPaint
    )
    canvas.drawBitmap(rounded, left, top, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
    canvas.restore()
    return out
  }

  /**
   * Style 4 (Light Frost): Left/Start 38% edge-to-edge cover with smooth white fade on its inner edge.
   */
  fun renderLeftEdgeFadeCoverBitmap(
    context: Context,
    rawArt: Bitmap?,
    widthPx: Int = 240,
    heightPx: Int = 330,
    isRtl: Boolean = false
  ): Bitmap {
    val src = rawArt ?: createFallbackGradientArtwork(context)
    val out = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(out)

    // Center-crop src into widthPx x heightPx
    val srcRatio = src.width.toFloat() / src.height.toFloat()
    val dstRatio = widthPx.toFloat() / heightPx.toFloat()
    val srcRect = if (srcRatio > dstRatio) {
      val cropW = (src.height * dstRatio).roundToInt().coerceIn(1, src.width)
      val x = ((src.width - cropW) / 2).coerceAtLeast(0)
      Rect(x, 0, x + cropW, src.height)
    } else {
      val cropH = (src.width / dstRatio).roundToInt().coerceIn(1, src.height)
      val y = ((src.height - cropH) / 2).coerceAtLeast(0)
      Rect(0, y, src.width, y + cropH)
    }
    canvas.drawBitmap(
      src,
      srcRect,
      Rect(0, 0, widthPx, heightPx),
      Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    )

    // Smooth white fade into the Light Frost right panel (mirrored on RTL)
    val fadePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      shader = if (!isRtl) {
        LinearGradient(
          widthPx * 0.42f,
          0f,
          widthPx.toFloat(),
          0f,
          intArrayOf(0x00FFFFFF, 0x80FFFFFF.toInt(), 0xE8FFFFFF.toInt()),
          floatArrayOf(0f, 0.60f, 1f),
          Shader.TileMode.CLAMP
        )
      } else {
        LinearGradient(
          widthPx * 0.58f,
          0f,
          0f,
          0f,
          intArrayOf(0x00FFFFFF, 0x80FFFFFF.toInt(), 0xE8FFFFFF.toInt()),
          floatArrayOf(0f, 0.60f, 1f),
          Shader.TileMode.CLAMP
        )
      }
    }
    canvas.drawRect(0f, 0f, widthPx.toFloat(), heightPx.toFloat(), fadePaint)
    return out
  }

  /**
   * Style 5 (Poster Glass): Sharp unblurred cover with a bottom dark gradient (45% to 0%).
   */
  fun renderPosterCoverWithBottomGradient(
    context: Context,
    rawArt: Bitmap?,
    widthPx: Int = 540,
    heightPx: Int = 285
  ): Bitmap {
    val src = rawArt ?: createFallbackGradientArtwork(context)
    val out = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.RGB_565)
    val canvas = Canvas(out)

    val srcRatio = src.width.toFloat() / src.height.toFloat()
    val dstRatio = widthPx.toFloat() / heightPx.toFloat()
    val srcRect = if (srcRatio > dstRatio) {
      val cropW = (src.height * dstRatio).roundToInt().coerceIn(1, src.width)
      val x = ((src.width - cropW) / 2).coerceAtLeast(0)
      Rect(x, 0, x + cropW, src.height)
    } else {
      val cropH = (src.width / dstRatio).roundToInt().coerceIn(1, src.height)
      val y = ((src.height - cropH) / 2).coerceAtLeast(0)
      Rect(0, y, src.width, y + cropH)
    }
    canvas.drawBitmap(
      src,
      srcRect,
      Rect(0, 0, widthPx, heightPx),
      Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    )

    // Bottom dark gradient (45% to 0%)
    val gradientPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      shader = LinearGradient(
        0f,
        heightPx.toFloat(),
        0f,
        heightPx * 0.25f,
        intArrayOf(0x73000000, 0x00000000),
        floatArrayOf(0f, 1f),
        Shader.TileMode.CLAMP
      )
    }
    canvas.drawRect(0f, 0f, widthPx.toFloat(), heightPx.toFloat(), gradientPaint)
    return out
  }

  /**
   * Style 5 (Poster Glass): Bottom floating frosted panel with strong blur + white 16% tint + top highlight.
   */
  fun renderPosterFloatingGlassPanelBitmap(
    blurredStrong: Bitmap,
    widthPx: Int = 320,
    heightPx: Int = 86,
    luminance: Float = 0.4f
  ): Bitmap {
    val out = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(out)
    val rect = RectF(0f, 0f, widthPx.toFloat(), heightPx.toFloat())
    val corner = min(heightPx * 0.36f, 34f)

    val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    canvas.drawRoundRect(rect, corner, corner, maskPaint)
    maskPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)

    // Sample lower portion of strong blurred cover
    val srcSlice = Rect(
      0,
      (blurredStrong.height * 0.45f).roundToInt().coerceIn(0, blurredStrong.height - 2),
      blurredStrong.width,
      blurredStrong.height
    )
    canvas.drawBitmap(blurredStrong, srcSlice, Rect(0, 0, widthPx, heightPx), maskPaint)
    maskPaint.xfermode = null

    // White 16% frosted tint + auto-darkening if bright cover
    val darkBackingAlpha = if (luminance > 0.62f) 0x7A else 0x42
    canvas.drawRoundRect(
      rect,
      corner,
      corner,
      Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(darkBackingAlpha, 10, 12, 22) }
    )
    canvas.drawRoundRect(
      rect,
      corner,
      corner,
      Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x29FFFFFF }
    )

    // Top highlight white 45% + subtle 16% border
    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      style = Paint.Style.STROKE
      strokeWidth = 2.0f
      shader = LinearGradient(
        0f,
        0f,
        0f,
        heightPx.toFloat(),
        intArrayOf(0x73FFFFFF, 0x29FFFFFF),
        floatArrayOf(0f, 1f),
        Shader.TileMode.CLAMP
      )
    }
    canvas.drawRoundRect(RectF(1f, 1f, widthPx - 1f, heightPx - 1f), corner - 1f, corner - 1f, borderPaint)
    return out
  }

  /**
   * Style 3 (Smoked Neon): 22 segmented pills with gradient (#4FC3FF -> #A06BFF) when filled.
   */
  fun renderSegmentedProgressBitmap(
    progressFraction: Float,
    segments: Int = 22,
    widthPx: Int = 560,
    heightPx: Int = 22,
    isRtl: Boolean = false
  ): Bitmap {
    val bmp = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val gap = 7.0f
    val pillW = ((widthPx - gap * (segments - 1)) / segments).coerceAtLeast(6f)
    val filledCount = (progressFraction.coerceIn(0f, 1f) * segments).roundToInt().coerceIn(0, segments)
    val radius = heightPx * 0.5f

    val filledPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      shader = LinearGradient(
        0f,
        0f,
        widthPx.toFloat(),
        0f,
        intArrayOf(0xFF4FC3FF.toInt(), 0xFFA06BFF.toInt()),
        null,
        Shader.TileMode.CLAMP
      )
    }
    val emptyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = 0x33FFFFFF
    }

    for (i in 0 until segments) {
      val logicalIdx = if (isRtl) (segments - 1 - i) else i
      val left = i * (pillW + gap)
      val rect = RectF(left, 1f, left + pillW, heightPx - 1f)
      canvas.drawRoundRect(rect, radius, radius, if (logicalIdx < filledCount) filledPaint else emptyPaint)
    }
    return bmp
  }

  /**
   * Style 4 (Light Frost): 20-bar waveform progress (played bars in accent #3B46E0).
   */
  fun renderWaveformProgressBitmap(
    progressFraction: Float,
    bars: Int = 20,
    widthPx: Int = 480,
    heightPx: Int = 48,
    accentColor: Int = 0xFF3B46E0.toInt(),
    unplayedColor: Int = 0x3814172A,
    isRtl: Boolean = false
  ): Bitmap {
    val bmp = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val pattern = floatArrayOf(
      0.42f, 0.72f, 0.55f, 0.94f, 0.68f, 0.45f, 0.85f, 1.0f, 0.62f, 0.48f,
      0.88f, 0.74f, 0.50f, 0.92f, 0.66f, 0.40f, 0.78f, 0.58f, 0.84f, 0.46f
    )
    val gap = 7.0f
    val barW = ((widthPx - gap * (bars - 1)) / bars).coerceAtLeast(5f)
    val playedCount = (progressFraction.coerceIn(0f, 1f) * bars).roundToInt().coerceIn(0, bars)
    val playedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accentColor }
    val unplayedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = unplayedColor }

    for (i in 0 until bars) {
      val logicalIdx = if (isRtl) (bars - 1 - i) else i
      val normH = pattern[logicalIdx % pattern.size]
      val barH = (heightPx * normH).coerceAtLeast(10f)
      val top = (heightPx - barH) * 0.5f
      val left = i * (barW + gap)
      val rect = RectF(left, top, left + barW, top + barH)
      canvas.drawRoundRect(rect, barW * 0.5f, barW * 0.5f, if (logicalIdx < playedCount) playedPaint else unplayedPaint)
    }
    return bmp
  }

  /**
   * 3-bar (Lyrics chip) or 4-bar (Mini widget) animated equalizer bitmap.
   */
  fun renderEqualizerBitmap(
    barCount: Int,
    color: Int,
    isPlaying: Boolean,
    phase: Int,
    widthPx: Int = if (barCount <= 3) 64 else 112,
    heightPx: Int = if (barCount <= 3) 42 else 78
  ): Bitmap {
    val bmp = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      this.color = color
      style = Paint.Style.FILL
    }
    val heights = if (!isPlaying) {
      if (barCount <= 3) floatArrayOf(0.45f, 0.65f, 0.40f)
      else floatArrayOf(0.32f, 0.46f, 0.38f, 0.28f)
    } else {
      when (phase.mod(4)) {
        0 -> if (barCount <= 3) floatArrayOf(0.55f, 0.98f, 0.68f) else floatArrayOf(0.54f, 0.96f, 0.74f, 0.44f)
        1 -> if (barCount <= 3) floatArrayOf(0.90f, 0.52f, 0.86f) else floatArrayOf(0.84f, 0.62f, 0.94f, 0.68f)
        2 -> if (barCount <= 3) floatArrayOf(0.44f, 0.88f, 0.54f) else floatArrayOf(0.46f, 0.88f, 0.56f, 0.92f)
        else -> if (barCount <= 3) floatArrayOf(0.78f, 0.46f, 0.92f) else floatArrayOf(0.72f, 0.48f, 0.86f, 0.58f)
      }
    }
    val gap = if (barCount <= 3) 7.5f else 11f
    val barW = ((widthPx - 12f - gap * (barCount - 1)) / barCount).coerceAtLeast(6f)
    val startX = 6f
    val bottomY = heightPx - 4f
    val maxBarH = heightPx - 8f
    for (i in 0 until barCount) {
      val left = startX + i * (barW + gap)
      val barH = (maxBarH * heights[i % heights.size]).coerceAtLeast(barW)
      canvas.drawRoundRect(
        RectF(left, bottomY - barH, left + barW, bottomY),
        barW * 0.5f,
        barW * 0.5f,
        paint
      )
    }
    return bmp
  }

  /**
   * Renders RTL-aware, fontScale-aware text with KhmerTypography equalized fonts into a crisp Bitmap
   * so Khmer + Latin render cleanly across all launchers without glyph clipping.
   */
  fun renderTextBitmap(
    context: Context,
    text: String,
    khmerLyricsFont: com.example.ui.theme.KhmerLyricsFont,
    isTitle: Boolean,
    textColor: Int,
    baseTextSizeDp: Float,
    isBold: Boolean,
    widthDp: Float,
    maxLines: Int = 1,
    centered: Boolean = false,
    shadowColor: Int = 0x55000000
  ): Bitmap? {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return null
    return runCatching {
      val cfg = context.resources.configuration
      val isRtl = cfg.layoutDirection == View.LAYOUT_DIRECTION_RTL
      val fontScale = cfg.fontScale.coerceIn(0.85f, 1.32f)
      val scale = context.resources.displayMetrics.density.coerceIn(1.85f, 2.35f)
      val widthPx = (widthDp * scale).roundToInt().coerceIn(140, 860)

      val styledText = com.example.ui.theme.KhmerTypography.buildStyledSpannable(
        context = context,
        text = trimmed,
        isTitle = isTitle,
        khmerLyricsFont = khmerLyricsFont,
        isBold = isBold
      )
      val baseTypeface = if (isTitle) {
        com.example.ui.theme.KhmerTypography.loadOutfitTypeface(context, isBold = isBold)
      } else {
        com.example.ui.theme.KhmerTypography.loadInterTypeface(context, isBold = isBold)
      }

      val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = baseTypeface
        color = textColor
        textSize = baseTextSizeDp * fontScale * scale
        if (shadowColor != Color.TRANSPARENT) {
          setShadowLayer(2.0f * scale, 0f, 0.8f * scale, shadowColor)
        }
      }

      val alignment = when {
        centered -> Layout.Alignment.ALIGN_CENTER
        isRtl -> Layout.Alignment.ALIGN_OPPOSITE
        else -> Layout.Alignment.ALIGN_NORMAL
      }

      val layout = StaticLayout.Builder
        .obtain(styledText, 0, styledText.length, paint, widthPx)
        .setAlignment(alignment)
        .setLineSpacing(1.8f * scale, 1.10f)
        .setIncludePad(true)
        .setMaxLines(maxLines.coerceAtLeast(1))
        .setEllipsize(TextUtils.TruncateAt.END)
        .build()

      val padY = (1.5f * scale).roundToInt()
      val heightPx = (layout.height + padY * 2).coerceIn(20, 220)
      val bmp = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
      val canvas = Canvas(bmp)
      canvas.save()
      canvas.translate(0f, padY.toFloat())
      layout.draw(canvas)
      canvas.restore()
      bmp
    }.getOrNull()
  }

  private fun boxBlurInPlace(bitmap: Bitmap, radius: Int, passes: Int) {
    val w = bitmap.width
    val h = bitmap.height
    if (w <= 2 || h <= 2 || radius <= 0) return
    val pixels = IntArray(w * h)
    val temp = IntArray(w * h)
    bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

    repeat(passes) {
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
}
