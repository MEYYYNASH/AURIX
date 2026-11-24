package com.example.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.os.Build
import android.os.SystemClock
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

data class WidgetContrastReport(
  val averageLuminance: Float,
  val contrastRatioAgainstWhite: Float,
  val isLowContrast: Boolean
)

data class WidgetBitmapBuildResult(
  val bitmap: Bitmap,
  val contrastReport: WidgetContrastReport,
  val buildTimeMs: Long,
  val fromCache: Boolean
)

/**
 * Pure Canvas bitmap builder for the customizable AURIX Lyrics Widget.
 * Renders:
 * 1) Background (Photo / Aurora / Sunset / Ocean / Solid)
 * 2) Fast stack blur (0..60)
 * 3) Darkness scrim (0..100%)
 * 4) Styled Frame photo (9 styles: None, Rounded, Circle, Polaroid, Neon, Gradient ring, Film, Stamp, Vinyl)
 *
 * Also computes white-text readability contrast in the lyric region so the customizer can
 * advise raising Darkness when a photo is too bright.
 */
object WidgetBitmapBuilder {
  private val compositeCache = ConcurrentHashMap<String, WidgetBitmapBuildResult>()

  fun clearCache() {
    compositeCache.clear()
  }

  fun resolveFramePhotoBitmap(
    context: Context,
    config: WidgetCustomizerConfig,
    albumArtBitmap: Bitmap?
  ): Bitmap? {
    if (config.frameSource == WidgetPhotoSource.CUSTOM) {
      val custom = WidgetPhotoStorage.decodeSampledFile(
        WidgetPhotoStorage.frameFile(context),
        512
      )
      if (custom != null) return custom
    }
    return albumArtBitmap
  }

  fun resolveBackgroundPhotoBitmap(
    context: Context,
    config: WidgetCustomizerConfig,
    albumArtBitmap: Bitmap?
  ): Bitmap? {
    if (config.samePhoto) {
      return resolveFramePhotoBitmap(context, config, albumArtBitmap)
    }
    if (config.bgSource == WidgetPhotoSource.CUSTOM) {
      val cropped = WidgetPhotoStorage.decodeSampledFile(
        WidgetPhotoStorage.bgCroppedFile(context),
        900
      )
      if (cropped != null) return cropped
      val original = WidgetPhotoStorage.decodeSampledFile(
        WidgetPhotoStorage.bgOriginalFile(context),
        900
      )
      if (original != null) return original
      // Fallback: if different photos is on but no bg photo chosen yet, use frame photo
      val frameFallback = WidgetPhotoStorage.decodeSampledFile(
        WidgetPhotoStorage.frameFile(context),
        900
      )
      if (frameFallback != null) return frameFallback
    }
    return albumArtBitmap
  }

  fun buildWidgetCompositeBitmap(
    context: Context,
    config: WidgetCustomizerConfig,
    sizeTier: WidgetSizeTier,
    albumArtBitmap: Bitmap?,
    songArtworkKey: String,
    includeFrameInComposite: Boolean = true,
    auroraPhase: Float = 0f,
    forceRebuild: Boolean = false
  ): WidgetBitmapBuildResult {
    val norm = config.normalized()
    val cacheKey = "${sizeTier.id}|$includeFrameInComposite|${norm.cacheKey()}|$songArtworkKey"
    if (!forceRebuild && auroraPhase == 0f) {
      compositeCache[cacheKey]?.let { cached ->
        if (!cached.bitmap.isRecycled) {
          return cached.copy(fromCache = true, buildTimeMs = 0L)
        }
      }
    }

    val startMs = SystemClock.elapsedRealtime()
    val w = sizeTier.widthPx
    val h = sizeTier.heightPx
    val bmpConfig = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      Bitmap.Config.RGB_565
    } else {
      Bitmap.Config.ARGB_8888
    }
    val out = Bitmap.createBitmap(w, h, bmpConfig)
    val canvas = Canvas(out)

    // 1. Background layer
    val bgSourceBitmap = if (norm.bgType == WidgetBgType.PHOTO) {
      resolveBackgroundPhotoBitmap(context, norm, albumArtBitmap)
    } else null
    drawBackgroundLayer(canvas, w, h, norm, bgSourceBitmap, auroraPhase)

    // 2. Blur pass (0..60)
    if (norm.blur > 0 && norm.bgType == WidgetBgType.PHOTO) {
      val blurred = applyFastBlur(out, norm.blur)
      if (blurred !== out) {
        canvas.drawBitmap(blurred, 0f, 0f, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        blurred.recycle()
      }
    }

    // 3. Darkness overlay (0..100%)
    drawDarknessOverlay(canvas, w, h, norm.darkness)

    // 4. Analyze lyric zone contrast BEFORE drawing the side frame
    val contrastReport = analyzeLyricZoneContrast(out, hasSideFrame = norm.frameStyle != WidgetFrameStyle.NONE && sizeTier != WidgetSizeTier.SMALL)

    // 5. Optional styled frame photo on left side (for static home screen widget)
    if (includeFrameInComposite && norm.frameStyle != WidgetFrameStyle.NONE && sizeTier != WidgetSizeTier.SMALL) {
      val frameSourceBmp = resolveFramePhotoBitmap(context, norm, albumArtBitmap)
      val frameRect = computeWidgetFrameRect(w, h, sizeTier)
      drawStyledFrameOnCanvas(
        canvas = canvas,
        bounds = frameRect,
        photo = frameSourceBmp,
        config = norm
      )
    }

    val elapsedMs = (SystemClock.elapsedRealtime() - startMs).coerceAtLeast(1L)
    val result = WidgetBitmapBuildResult(
      bitmap = out,
      contrastReport = contrastReport,
      buildTimeMs = elapsedMs,
      fromCache = false
    )
    if (auroraPhase == 0f) {
      if (compositeCache.size > 12) compositeCache.clear()
      compositeCache[cacheKey] = result
    }
    return result
  }

  /**
   * Renders just the background + blur + darkness (without the side frame) for in-app live
   * previews where the frame photo itself animates smoothly (Float / Spin / Pulse).
   */
  fun buildBackgroundOnlyBitmap(
    context: Context,
    config: WidgetCustomizerConfig,
    widthPx: Int = 640,
    heightPx: Int = 320,
    albumArtBitmap: Bitmap?,
    auroraPhase: Float = 0f
  ): Pair<Bitmap, WidgetContrastReport> {
    val norm = config.normalized()
    val out = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(out)
    val bgSourceBitmap = if (norm.bgType == WidgetBgType.PHOTO) {
      resolveBackgroundPhotoBitmap(context, norm, albumArtBitmap)
    } else null
    drawBackgroundLayer(canvas, widthPx, heightPx, norm, bgSourceBitmap, auroraPhase)
    if (norm.blur > 0 && norm.bgType == WidgetBgType.PHOTO) {
      val blurred = applyFastBlur(out, norm.blur)
      if (blurred !== out) {
        canvas.drawBitmap(blurred, 0f, 0f, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        blurred.recycle()
      }
    }
    drawDarknessOverlay(canvas, widthPx, heightPx, norm.darkness)
    val report = analyzeLyricZoneContrast(out, hasSideFrame = norm.frameStyle != WidgetFrameStyle.NONE)
    return out to report
  }

  /**
   * Renders a standalone transparent-background framed photo bitmap for use inside
   * Compose animations (Float, Spin, Pulse) in the Customizer preview and Lyrics screen.
   */
  fun buildStandaloneFrameBitmap(
    photo: Bitmap?,
    config: WidgetCustomizerConfig,
    sizePx: Int = 260
  ): Bitmap {
    val out = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(out)
    val pad = sizePx * 0.08f
    drawStyledFrameOnCanvas(
      canvas = canvas,
      bounds = RectF(pad, pad, sizePx - pad, sizePx - pad),
      photo = photo,
      config = config.normalized()
    )
    return out
  }

  fun computeWidgetFrameRect(widthPx: Int, heightPx: Int, sizeTier: WidgetSizeTier): RectF {
    val frameSize = when (sizeTier) {
      WidgetSizeTier.SMALL -> min(widthPx, heightPx) * 0.36f
      WidgetSizeTier.WIDE -> heightPx * 0.56f
      WidgetSizeTier.LARGE -> min(widthPx * 0.30f, heightPx * 0.42f)
    }
    val left = widthPx * 0.055f
    val top = (heightPx - frameSize) * 0.50f
    return RectF(left, top, left + frameSize, top + frameSize)
  }

  private fun drawBackgroundLayer(
    canvas: Canvas,
    w: Int,
    h: Int,
    config: WidgetCustomizerConfig,
    photo: Bitmap?,
    auroraPhase: Float
  ) {
    val rect = RectF(0f, 0f, w.toFloat(), h.toFloat())
    when (config.bgType) {
      WidgetBgType.PHOTO -> {
        if (photo != null && !photo.isRecycled) {
          drawCenterCropBitmap(canvas, photo, rect)
        } else {
          drawAuroraGradient(canvas, w, h, 0f)
        }
      }
      WidgetBgType.AURORA -> drawAuroraGradient(canvas, w, h, auroraPhase)
      WidgetBgType.SUNSET -> {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
          shader = LinearGradient(
            0f, 0f, w.toFloat(), h.toFloat(),
            intArrayOf(0xFFFB7185.toInt(), 0xFFF97316.toInt(), 0xFFA855F7.toInt(), 0xFF1E1B4B.toInt()),
            floatArrayOf(0f, 0.34f, 0.70f, 1f),
            Shader.TileMode.CLAMP
          )
        }
        canvas.drawRect(rect, paint)
      }
      WidgetBgType.OCEAN -> {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
          shader = LinearGradient(
            0f, 0f, w.toFloat(), h.toFloat(),
            intArrayOf(0xFF06B6D4.toInt(), 0xFF0284C7.toInt(), 0xFF1D4ED8.toInt(), 0xFF0B132B.toInt()),
            floatArrayOf(0f, 0.35f, 0.72f, 1f),
            Shader.TileMode.CLAMP
          )
        }
        canvas.drawRect(rect, paint)
      }
      WidgetBgType.SOLID -> {
        val solidColor = WidgetCustomizerConfig.parseColorInt(config.bgColorHex, 0xFF151F30.toInt())
        canvas.drawColor(solidColor)
        val sheen = Paint(Paint.ANTI_ALIAS_FLAG).apply {
          shader = LinearGradient(
            0f, 0f, w * 0.7f, h * 0.7f,
            intArrayOf(0x1CFFFFFF, Color.TRANSPARENT),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP
          )
        }
        canvas.drawRect(rect, sheen)
      }
    }
  }

  private fun drawAuroraGradient(canvas: Canvas, w: Int, h: Int, phase: Float) {
    val wf = w.toFloat()
    val hf = h.toFloat()
    canvas.drawColor(0xFF0A1022.toInt())
    val angle = phase * (2.0 * PI)
    val dx1 = (cos(angle) * wf * 0.16).toFloat()
    val dy1 = (sin(angle) * hf * 0.18).toFloat()
    val dx2 = (sin(angle + 1.4) * wf * 0.18).toFloat()
    val dy2 = (cos(angle + 0.9) * hf * 0.16).toFloat()

    val orb1 = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      shader = RadialGradient(
        wf * 0.24f + dx1, hf * 0.28f + dy1, wf * 0.68f,
        intArrayOf(0xD843C7F4.toInt(), 0x807965FF.toInt(), Color.TRANSPARENT),
        floatArrayOf(0f, 0.48f, 1f),
        Shader.TileMode.CLAMP
      )
    }
    canvas.drawRect(0f, 0f, wf, hf, orb1)

    val orb2 = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      shader = RadialGradient(
        wf * 0.78f + dx2, hf * 0.76f + dy2, wf * 0.65f,
        intArrayOf(0xCCA855F7.toInt(), 0x70EC4899.toInt(), Color.TRANSPARENT),
        floatArrayOf(0f, 0.45f, 1f),
        Shader.TileMode.CLAMP
      )
    }
    canvas.drawRect(0f, 0f, wf, hf, orb2)

    val orb3 = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      shader = RadialGradient(
        wf * 0.52f - dx1 * 0.5f, hf * 0.18f + dy2 * 0.6f, wf * 0.52f,
        intArrayOf(0x6622D3EE.toInt(), Color.TRANSPARENT),
        floatArrayOf(0f, 1f),
        Shader.TileMode.CLAMP
      )
    }
    canvas.drawRect(0f, 0f, wf, hf, orb3)
  }

  private fun drawDarknessOverlay(canvas: Canvas, w: Int, h: Int, darknessPercent: Int) {
    val norm = darknessPercent.coerceIn(0, 100) / 100f
    if (norm <= 0.001f) return
    // 100% darkness gives a deep, cinema-grade legible scrim (~74% opacity) so the
    // blurred photo glow still breathes underneath while white lyrics stay razor-sharp.
    val topAlpha = (norm * 155f).roundToInt().coerceIn(0, 225)
    val midAlpha = (norm * 186f).roundToInt().coerceIn(0, 235)
    val botAlpha = (norm * 212f).roundToInt().coerceIn(0, 245)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      shader = LinearGradient(
        0f, 0f, 0f, h.toFloat(),
        intArrayOf(
          Color.argb(topAlpha, 7, 11, 20),
          Color.argb(midAlpha, 8, 12, 22),
          Color.argb(botAlpha, 5, 8, 16)
        ),
        floatArrayOf(0f, 0.5f, 1f),
        Shader.TileMode.CLAMP
      )
    }
    canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
  }

  fun drawStyledFrameOnCanvas(
    canvas: Canvas,
    bounds: RectF,
    photo: Bitmap?,
    config: WidgetCustomizerConfig
  ) {
    if (config.frameStyle == WidgetFrameStyle.NONE) return
    val accent = WidgetCustomizerConfig.parseColorInt(config.frameColorHex, 0xFF43C7F4.toInt())
    val minSide = min(bounds.width(), bounds.height())
    val cornerPx = (config.cornerRadius / 50f) * (minSide * 0.48f)
    val strokePx = (config.borderWidth.toFloat() * (minSide / 180f)).coerceAtLeast(0f)

    when (config.frameStyle) {
      WidgetFrameStyle.NONE -> Unit

      WidgetFrameStyle.ROUNDED -> {
        drawSoftDropShadow(canvas, bounds, cornerPx)
        drawClippedPhoto(canvas, bounds, photo, cornerPx, isCircle = false)
        if (strokePx > 0.5f) {
          val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = strokePx
            color = accent
          }
          val inset = strokePx * 0.5f
          canvas.drawRoundRect(
            RectF(bounds.left + inset, bounds.top + inset, bounds.right - inset, bounds.bottom - inset),
            (cornerPx - inset).coerceAtLeast(0f),
            (cornerPx - inset).coerceAtLeast(0f),
            borderPaint
          )
        }
      }

      WidgetFrameStyle.CIRCLE -> {
        drawSoftDropShadow(canvas, bounds, minSide * 0.5f)
        drawClippedPhoto(canvas, bounds, photo, minSide * 0.5f, isCircle = true)
        if (strokePx > 0.5f) {
          val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = strokePx
            color = accent
          }
          canvas.drawCircle(
            bounds.centerX(),
            bounds.centerY(),
            (minSide * 0.5f - strokePx * 0.5f).coerceAtLeast(2f),
            ringPaint
          )
        }
      }

      WidgetFrameStyle.POLAROID -> {
        val cardRadius = (minSide * 0.06f).coerceAtLeast(6f)
        drawSoftDropShadow(canvas, bounds, cardRadius)
        val paperPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFF7F8FA.toInt() }
        canvas.drawRoundRect(bounds, cardRadius, cardRadius, paperPaint)

        val padSide = minSide * 0.075f
        val padTop = minSide * 0.075f
        val padBottom = minSide * 0.22f
        val inner = RectF(bounds.left + padSide, bounds.top + padTop, bounds.right - padSide, bounds.bottom - padBottom)
        drawClippedPhoto(canvas, inner, photo, (cornerPx * 0.25f).coerceAtMost(10f), isCircle = false)

        // Subtle accent signature stripe on the Polaroid chin
        val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent }
        val barW = bounds.width() * 0.28f
        val barH = (minSide * 0.025f).coerceAtLeast(3f)
        val barTop = inner.bottom + (bounds.bottom - inner.bottom - barH) * 0.5f
        canvas.drawRoundRect(
          RectF(bounds.centerX() - barW * 0.5f, barTop, bounds.centerX() + barW * 0.5f, barTop + barH),
          barH, barH, accentPaint
        )
        if (strokePx > 0.5f) {
          val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = (strokePx * 0.6f).coerceAtLeast(1.5f)
            color = Color.argb(140, Color.red(accent), Color.green(accent), Color.blue(accent))
          }
          canvas.drawRoundRect(bounds, cardRadius, cardRadius, rim)
        }
      }

      WidgetFrameStyle.NEON -> {
        val effRadius = cornerPx.coerceAtLeast(minSide * 0.14f)
        // Outer multi-pass neon glow rings
        for (layer in 3 downTo 1) {
          val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = (strokePx.coerceAtLeast(3f) + layer * 4.5f)
            color = Color.argb(42 / layer + 24, Color.red(accent), Color.green(accent), Color.blue(accent))
          }
          canvas.drawRoundRect(bounds, effRadius, effRadius, glowPaint)
        }
        drawClippedPhoto(canvas, bounds, photo, effRadius, isCircle = false)
        val corePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
          style = Paint.Style.STROKE
          strokeWidth = strokePx.coerceAtLeast(3f)
          color = accent
        }
        canvas.drawRoundRect(bounds, effRadius, effRadius, corePaint)
        val innerHighlight = Paint(Paint.ANTI_ALIAS_FLAG).apply {
          style = Paint.Style.STROKE
          strokeWidth = (strokePx.coerceAtLeast(3f) * 0.42f).coerceAtLeast(1.2f)
          color = 0xE6FFFFFF.toInt()
        }
        canvas.drawRoundRect(bounds, effRadius, effRadius, innerHighlight)
      }

      WidgetFrameStyle.GRADIENT_RING -> {
        val ringW = strokePx.coerceAtLeast(minSide * 0.045f)
        val gap = (minSide * 0.025f).coerceAtLeast(2.5f)
        val inner = RectF(
          bounds.left + ringW + gap,
          bounds.top + ringW + gap,
          bounds.right - ringW - gap,
          bounds.bottom - ringW - gap
        )
        val isRound = config.cornerRadius >= 42
        drawClippedPhoto(canvas, inner, photo, cornerPx, isCircle = isRound)
        val gradPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
          style = Paint.Style.STROKE
          strokeWidth = ringW
          shader = SweepGradient(
            bounds.centerX(),
            bounds.centerY(),
            intArrayOf(accent, 0xFF7965FF.toInt(), 0xFFEC4899.toInt(), 0xFF43C7F4.toInt(), accent),
            null
          )
        }
        val rInset = ringW * 0.5f
        val ringBounds = RectF(bounds.left + rInset, bounds.top + rInset, bounds.right - rInset, bounds.bottom - rInset)
        if (isRound) {
          canvas.drawCircle(bounds.centerX(), bounds.centerY(), minSide * 0.5f - rInset, gradPaint)
        } else {
          canvas.drawRoundRect(ringBounds, cornerPx.coerceAtLeast(12f), cornerPx.coerceAtLeast(12f), gradPaint)
        }
      }

      WidgetFrameStyle.FILM -> {
        val filmRadius = (minSide * 0.05f).coerceAtLeast(5f)
        drawSoftDropShadow(canvas, bounds, filmRadius)
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF10131C.toInt() }
        canvas.drawRoundRect(bounds, filmRadius, filmRadius, bodyPaint)

        val railH = minSide * 0.125f
        val padX = minSide * 0.06f
        val inner = RectF(bounds.left + padX, bounds.top + railH, bounds.right - padX, bounds.bottom - railH)
        drawClippedPhoto(canvas, inner, photo, (minSide * 0.03f), isCircle = false)

        // Sprocket holes along top and bottom film rails
        val holePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xD8FFFFFF.toInt() }
        val holeCount = 5
        val holeW = (bounds.width() - padX * 2f) / (holeCount * 2f)
        val holeH = railH * 0.44f
        val topY = bounds.top + (railH - holeH) * 0.5f
        val botY = bounds.bottom - railH + (railH - holeH) * 0.5f
        for (i in 0 until holeCount) {
          val hx = bounds.left + padX + (i + 0.25f) * (holeW * 2f)
          canvas.drawRoundRect(RectF(hx, topY, hx + holeW, topY + holeH), 2.5f, 2.5f, holePaint)
          canvas.drawRoundRect(RectF(hx, botY, hx + holeW, botY + holeH), 2.5f, 2.5f, holePaint)
        }
        val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
          style = Paint.Style.STROKE
          strokeWidth = strokePx.coerceAtLeast(1.8f)
          color = accent
        }
        canvas.drawRoundRect(bounds, filmRadius, filmRadius, rimPaint)
      }

      WidgetFrameStyle.STAMP -> {
        drawSoftDropShadow(canvas, bounds, 6f)
        val paperPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFF8FAFC.toInt() }
        val notchRadius = (minSide * 0.038f).coerceIn(4f, 9f)
        val stampPath = buildStampPath(bounds, notchRadius)
        canvas.drawPath(stampPath, paperPaint)

        val inset = notchRadius * 1.9f
        val inner = RectF(bounds.left + inset, bounds.top + inset, bounds.right - inset, bounds.bottom - inset)
        drawClippedPhoto(canvas, inner, photo, (cornerPx * 0.25f).coerceAtMost(10f), isCircle = false)

        if (strokePx > 0.5f) {
          val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = (strokePx * 0.65f).coerceAtLeast(1.5f)
            color = accent
          }
          canvas.drawRect(inner, border)
        }
      }

      WidgetFrameStyle.VINYL -> {
        val cx = bounds.centerX()
        val cy = bounds.centerY()
        val outerR = minSide * 0.5f
        drawSoftDropShadow(canvas, bounds, outerR)

        // Deep vinyl disc base
        val discPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
          shader = RadialGradient(
            cx, cy, outerR,
            intArrayOf(0xFF1A1F2C.toInt(), 0xFF0A0D14.toInt(), 0xFF141925.toInt(), 0xFF06080E.toInt()),
            floatArrayOf(0f, 0.55f, 0.82f, 1f),
            Shader.TileMode.CLAMP
          )
        }
        canvas.drawCircle(cx, cy, outerR, discPaint)

        // Concentric vinyl grooves
        val groovePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
          style = Paint.Style.STROKE
          strokeWidth = 1.1f
          color = 0x26FFFFFF
        }
        for (step in 1..6) {
          val r = outerR * (0.56f + step * 0.064f)
          if (r < outerR - 2f) canvas.drawCircle(cx, cy, r, groovePaint)
        }

        // Specular light sheen sweep
        val sheenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
          shader = SweepGradient(
            cx, cy,
            intArrayOf(Color.TRANSPARENT, 0x2EFFFFFF, Color.TRANSPARENT, 0x22FFFFFF, Color.TRANSPARENT),
            floatArrayOf(0f, 0.14f, 0.34f, 0.64f, 1f)
          )
        }
        canvas.drawCircle(cx, cy, outerR * 0.96f, sheenPaint)

        // Outer accent rim
        if (strokePx > 0.5f) {
          val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = strokePx
            color = accent
          }
          canvas.drawCircle(cx, cy, outerR - strokePx * 0.5f, rimPaint)
        }

        // Center album-art label
        val labelR = outerR * 0.54f
        val labelRect = RectF(cx - labelR, cy - labelR, cx + labelR, cy + labelR)
        drawClippedPhoto(canvas, labelRect, photo, labelR, isCircle = true)

        val labelRing = Paint(Paint.ANTI_ALIAS_FLAG).apply {
          style = Paint.Style.STROKE
          strokeWidth = (strokePx * 0.7f).coerceAtLeast(2f)
          color = accent
        }
        canvas.drawCircle(cx, cy, labelR, labelRing)

        // Center spindle hole
        val holePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF0A0D14.toInt() }
        canvas.drawCircle(cx, cy, outerR * 0.09f, holePaint)
        val holeRim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
          style = Paint.Style.STROKE
          strokeWidth = 1.5f
          color = 0x80FFFFFF.toInt()
        }
        canvas.drawCircle(cx, cy, outerR * 0.09f, holeRim)
      }
    }
  }

  private fun buildStampPath(bounds: RectF, notchRadius: Float): Path {
    val rectPath = Path().apply { addRect(bounds, Path.Direction.CW) }
    val holesPath = Path()
    val step = notchRadius * 2.8f
    var x = bounds.left + step * 0.6f
    while (x < bounds.right - notchRadius) {
      holesPath.addCircle(x, bounds.top, notchRadius, Path.Direction.CW)
      holesPath.addCircle(x, bounds.bottom, notchRadius, Path.Direction.CW)
      x += step
    }
    var y = bounds.top + step * 0.6f
    while (y < bounds.bottom - notchRadius) {
      holesPath.addCircle(bounds.left, y, notchRadius, Path.Direction.CW)
      holesPath.addCircle(bounds.right, y, notchRadius, Path.Direction.CW)
      y += step
    }
    rectPath.op(holesPath, Path.Op.DIFFERENCE)
    return rectPath
  }

  private fun drawSoftDropShadow(canvas: Canvas, bounds: RectF, cornerRadius: Float) {
    val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x44000000 }
    val shadowRect = RectF(bounds.left, bounds.top + 4f, bounds.right, bounds.bottom + 6f)
    canvas.drawRoundRect(shadowRect, cornerRadius, cornerRadius, shadowPaint)
  }

  private fun drawClippedPhoto(
    canvas: Canvas,
    bounds: RectF,
    photo: Bitmap?,
    cornerRadius: Float,
    isCircle: Boolean
  ) {
    val saveCount = canvas.save()
    val clipPath = Path().apply {
      if (isCircle) {
        addOval(bounds, Path.Direction.CW)
      } else {
        addRoundRect(bounds, cornerRadius, cornerRadius, Path.Direction.CW)
      }
    }
    canvas.clipPath(clipPath)
    if (photo != null && !photo.isRecycled) {
      drawCenterCropBitmap(canvas, photo, bounds)
    } else {
      val placeholderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(
          bounds.left, bounds.top, bounds.right, bounds.bottom,
          intArrayOf(0xFF43C7F4.toInt(), 0xFF7965FF.toInt(), 0xFF151F30.toInt()),
          floatArrayOf(0f, 0.55f, 1f),
          Shader.TileMode.CLAMP
        )
      }
      canvas.drawRect(bounds, placeholderPaint)
    }
    canvas.restoreToCount(saveCount)
  }

  private fun drawCenterCropBitmap(canvas: Canvas, src: Bitmap, dst: RectF) {
    val srcW = src.width.toFloat().coerceAtLeast(1f)
    val srcH = src.height.toFloat().coerceAtLeast(1f)
    val dstAspect = dst.width() / dst.height().coerceAtLeast(1f)
    val srcAspect = srcW / srcH

    val srcRect = if (srcAspect > dstAspect) {
      val cropW = (srcH * dstAspect).roundToInt().coerceIn(1, src.width)
      val left = (src.width - cropW) / 2
      Rect(left, 0, left + cropW, src.height)
    } else {
      val cropH = (srcW / dstAspect).roundToInt().coerceIn(1, src.height)
      val top = (src.height - cropH) / 2
      Rect(0, top, src.width, top + cropH)
    }
    canvas.drawBitmap(src, srcRect, dst, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
  }

  /**
   * Samples luminance in the lyrics text area and computes WCAG contrast ratio against white (#FFFFFF).
   * Returns isLowContrast = true when white lyrics would be hard to read (ratio < 4.5).
   */
  fun analyzeLyricZoneContrast(bitmap: Bitmap, hasSideFrame: Boolean): WidgetContrastReport {
    val w = bitmap.width
    val h = bitmap.height
    if (w <= 4 || h <= 4) {
      return WidgetContrastReport(0.1f, 10f, false)
    }
    val startX = if (hasSideFrame) (w * 0.36f).roundToInt() else (w * 0.08f).roundToInt()
    val endX = (w * 0.92f).roundToInt().coerceAtLeast(startX + 1)
    val startY = (h * 0.24f).roundToInt()
    val endY = (h * 0.84f).roundToInt().coerceAtLeast(startY + 1)

    val stepX = max(1, (endX - startX) / 14)
    val stepY = max(1, (endY - startY) / 8)
    var lumSum = 0.0
    var count = 0
    var y = startY
    while (y < endY) {
      var x = startX
      while (x < endX) {
        val px = bitmap.getPixel(x, y)
        lumSum += relativeLuminance(px)
        count++
        x += stepX
      }
      y += stepY
    }
    val avgLum = if (count > 0) (lumSum / count).toFloat() else 0.1f
    val ratio = (1.05f / (avgLum + 0.05f)).coerceIn(1f, 21f)
    return WidgetContrastReport(
      averageLuminance = avgLum,
      contrastRatioAgainstWhite = ratio,
      isLowContrast = ratio < 4.5f
    )
  }

  fun relativeLuminance(colorInt: Int): Double {
    fun linearize(channel: Int): Double {
      val s = channel / 255.0
      return if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
    }
    val r = linearize(Color.red(colorInt))
    val g = linearize(Color.green(colorInt))
    val b = linearize(Color.blue(colorInt))
    return 0.2126 * r + 0.7152 * g + 0.0722 * b
  }

  /**
   * Fast downsampled box/stack blur mapped to the 0..60 blur slider.
   * Produces silky atmospheric blur in ~4-15ms without needing deprecated RenderScript.
   */
  private fun applyFastBlur(source: Bitmap, blurLevel: Int): Bitmap {
    val clamped = blurLevel.coerceIn(0, 60)
    if (clamped <= 0) return source
    val scale = when {
      clamped >= 42 -> 0.14f
      clamped >= 22 -> 0.20f
      else -> 0.30f
    }
    val smallW = (source.width * scale).roundToInt().coerceAtLeast(24)
    val smallH = (source.height * scale).roundToInt().coerceAtLeast(16)
    val rawSmall = Bitmap.createScaledBitmap(source, smallW, smallH, true)
    val small = if (rawSmall.isMutable && rawSmall.config == Bitmap.Config.ARGB_8888) {
      rawSmall
    } else {
      rawSmall.copy(Bitmap.Config.ARGB_8888, true).also {
        if (rawSmall !== source) rawSmall.recycle()
      }
    }
    val radius = ((clamped / 60f) * 14f).roundToInt().coerceIn(1, 14)
    boxBlurInPlace(small, radius)
    boxBlurInPlace(small, max(1, radius / 2))
    val result = Bitmap.createScaledBitmap(small, source.width, source.height, true)
    if (small !== result && small !== source) small.recycle()
    return result
  }

  private fun boxBlurInPlace(bitmap: Bitmap, radius: Int) {
    if (radius < 1) return
    val w = bitmap.width
    val h = bitmap.height
    val pixels = IntArray(w * h)
    val temp = IntArray(w * h)
    bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
    val div = radius * 2 + 1

    // Horizontal pass
    for (y in 0 until h) {
      var rSum = 0
      var gSum = 0
      var bSum = 0
      val rowOffset = y * w
      for (i in -radius..radius) {
        val px = pixels[rowOffset + i.coerceIn(0, w - 1)]
        rSum += (px shr 16) and 0xFF
        gSum += (px shr 8) and 0xFF
        bSum += px and 0xFF
      }
      for (x in 0 until w) {
        temp[rowOffset + x] = (0xFF shl 24) or ((rSum / div) shl 16) or ((gSum / div) shl 8) or (bSum / div)
        val outIdx = (x - radius).coerceIn(0, w - 1)
        val inIdx = (x + radius + 1).coerceIn(0, w - 1)
        val outPx = pixels[rowOffset + outIdx]
        val inPx = pixels[rowOffset + inIdx]
        rSum += ((inPx shr 16) and 0xFF) - ((outPx shr 16) and 0xFF)
        gSum += ((inPx shr 8) and 0xFF) - ((outPx shr 8) and 0xFF)
        bSum += (inPx and 0xFF) - (outPx and 0xFF)
      }
    }

    // Vertical pass
    for (x in 0 until w) {
      var rSum = 0
      var gSum = 0
      var bSum = 0
      for (i in -radius..radius) {
        val px = temp[i.coerceIn(0, h - 1) * w + x]
        rSum += (px shr 16) and 0xFF
        gSum += (px shr 8) and 0xFF
        bSum += px and 0xFF
      }
      for (y in 0 until h) {
        pixels[y * w + x] = (0xFF shl 24) or ((rSum / div) shl 16) or ((gSum / div) shl 8) or (bSum / div)
        val outIdx = (y - radius).coerceIn(0, h - 1)
        val inIdx = (y + radius + 1).coerceIn(0, h - 1)
        val outPx = temp[outIdx * w + x]
        val inPx = temp[inIdx * w + x]
        rSum += ((inPx shr 16) and 0xFF) - ((outPx shr 16) and 0xFF)
        gSum += ((inPx shr 8) and 0xFF) - ((outPx shr 8) and 0xFF)
        bSum += (inPx and 0xFF) - (outPx and 0xFF)
      }
    }
    bitmap.setPixels(pixels, 0, w, 0, 0, w, h)
  }
}
