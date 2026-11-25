package com.example.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.graphics.Typeface
import android.text.Layout
import android.text.SpannableString
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.text.style.TypefaceSpan
import android.util.LruCache
import android.view.View
import androidx.core.content.res.ResourcesCompat
import com.example.R
import com.example.ui.theme.KhmerLyricsFont
import com.example.ui.theme.KhmerTypography
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * High-performance Canvas & LruCache renderer for the resizable AURIX Now Playing widget
 * across 3 sizes (Compact 4x1, Player 4x2, Lyrics 4x2+) and 10 distinct styles.
 */
object NowPlayingWidgetRenderer {
  private val cardBgCache = object : LruCache<String, Bitmap>(16) {}
  private val artFrameCache = object : LruCache<String, Bitmap>(16) {}

  fun clearCache() {
    cardBgCache.evictAll()
    artFrameCache.evictAll()
  }

  /**
   * Renders the style-specific card background bitmap full-bleed (hardware clipping and crisp 1dp
   * vector borders are applied by the RemoteViews container so corners never bleed or blur).
   */
  fun getOrRenderCardBackground(
    context: Context,
    style: NowPlayingWidgetStyle,
    size: NowPlayingWidgetSize,
    coverSet: CachedGlassCoverSet,
    rawArt: Bitmap?,
    tokens: NowPlayingWidgetThemeTokens,
    coverKey: String
  ): Bitmap {
    val w = 460
    val h = when (size) {
      NowPlayingWidgetSize.COMPACT -> 102
      NowPlayingWidgetSize.PLAYER -> 214
      NowPlayingWidgetSize.LYRICS -> 304
    }
    val key = "v2|${style.id}|${size.id}|$coverKey|${tokens.cardBg}|${tokens.accent}|${rawArt != null}"
    cardBgCache.get(key)?.let { return it }

    if (style == NowPlayingWidgetStyle.VINYL) {
      val vinylPaper = VinylWidgetRenderer.getOrRenderVinylPaperBackground(w, h, 0f)
      cardBgCache.put(key, vinylPaper)
      return vinylPaper
    }

    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565)
    val canvas = Canvas(bmp)
    val rect = RectF(0f, 0f, w.toFloat(), h.toFloat())

    when (style) {
      NowPlayingWidgetStyle.AURA -> {
        val scaledBlur = Bitmap.createScaledBitmap(coverSet.blurred26, w, h, true)
        canvas.drawBitmap(scaledBlur, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
        val scrim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
          shader = LinearGradient(
            0f,
            0f,
            w.toFloat(),
            h.toFloat(),
            intArrayOf(withAlpha(tokens.cardBg, 0xCC), withAlpha(tokens.cardSecondary, 0x99), withAlpha(tokens.cardBg, 0xDD)),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
          )
        }
        canvas.drawRect(rect, scrim)
        val bloom = Paint(Paint.ANTI_ALIAS_FLAG).apply {
          shader = RadialGradient(
            w * 0.18f,
            h * 0.25f,
            max(w, h) * 0.72f,
            intArrayOf(withAlpha(tokens.accent, 0x55), Color.TRANSPARENT),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP
          )
        }
        canvas.drawRect(rect, bloom)
      }

      NowPlayingWidgetStyle.RING -> {
        canvas.drawColor(tokens.cardBg)
        val orb = Paint(Paint.ANTI_ALIAS_FLAG).apply {
          shader = RadialGradient(
            w * 0.16f,
            h * 0.50f,
            max(w, h) * 0.65f,
            intArrayOf(withAlpha(tokens.accent, 0x3A), Color.TRANSPARENT),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP
          )
        }
        canvas.drawRect(rect, orb)
      }

      NowPlayingWidgetStyle.TYPE -> {
        canvas.drawColor(tokens.cardBg)
        val grad = Paint(Paint.ANTI_ALIAS_FLAG).apply {
          shader = LinearGradient(
            0f,
            0f,
            w.toFloat(),
            h.toFloat(),
            intArrayOf(tokens.cardBg, blend(tokens.cardBg, tokens.cardSecondary, 0.28f)),
            null,
            Shader.TileMode.CLAMP
          )
        }
        canvas.drawRect(rect, grad)
      }

      NowPlayingWidgetStyle.FROST -> {
        val scaledBlur = Bitmap.createScaledBitmap(coverSet.blurred26, w, h, true)
        canvas.drawBitmap(scaledBlur, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
        val tint = WidgetGlassRenderer.computeAutoContrastTint(WidgetGlassStyle.FROST_PILL, coverSet.luminance)
        canvas.drawColor(tint)
        val sheen = Paint(Paint.ANTI_ALIAS_FLAG).apply {
          shader = LinearGradient(
            0f,
            0f,
            w * 0.65f,
            h * 0.65f,
            intArrayOf(0x3DFFFFFF, 0x00FFFFFF),
            null,
            Shader.TileMode.CLAMP
          )
        }
        canvas.drawRect(rect, sheen)
      }

      NowPlayingWidgetStyle.LIGHT -> {
        canvas.drawColor(tokens.cardBg)
        val topWash = Paint(Paint.ANTI_ALIAS_FLAG).apply {
          shader = LinearGradient(
            0f,
            0f,
            0f,
            h.toFloat(),
            intArrayOf(0x40FFFFFF, withAlpha(tokens.cardSecondary, 0x66)),
            null,
            Shader.TileMode.CLAMP
          )
        }
        canvas.drawRect(rect, topWash)
      }

      NowPlayingWidgetStyle.NIGHT -> {
        canvas.drawColor(tokens.cardBg)
        val halo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
          shader = RadialGradient(
            w * 0.84f,
            h * 0.16f,
            max(w, h) * 0.55f,
            intArrayOf(withAlpha(tokens.accent, 0x24), Color.TRANSPARENT),
            null,
            Shader.TileMode.CLAMP
          )
        }
        canvas.drawRect(rect, halo)
      }

      NowPlayingWidgetStyle.VINYL -> {
        canvas.drawColor(VinylWidgetRenderer.PAPER_COLOR)
      }

      NowPlayingWidgetStyle.PIXEL -> {
        canvas.drawColor(tokens.cardBg)
        val gridPaint = Paint().apply {
          color = 0x12FFFFFF
          strokeWidth = 1f
        }
        var gx = 0f
        while (gx < w) {
          canvas.drawLine(gx, 0f, gx, h.toFloat(), gridPaint)
          gx += 20f
        }
        var gy = 0f
        while (gy < h) {
          canvas.drawLine(0f, gy, w.toFloat(), gy, gridPaint)
          gy += 20f
        }
      }

      NowPlayingWidgetStyle.PAPER -> {
        canvas.drawColor(tokens.cardBg)
        // Subtle editorial warm vignette & top-left highlight
        val editorialGlow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
          shader = RadialGradient(
            w * 0.88f,
            h * 0.12f,
            max(w, h) * 0.82f,
            intArrayOf(0x66FFFFFF, 0x00FFFFFF),
            null,
            Shader.TileMode.CLAMP
          )
        }
        canvas.drawRect(rect, editorialGlow)
        val rulePaint = Paint().apply {
          color = 0x12000000
          strokeWidth = 1.5f
        }
        canvas.drawLine(28f, 22f, w - 28f, 22f, rulePaint)
      }

      NowPlayingWidgetStyle.FLOAT -> {
        if (rawArt != null) {
          val coverScaled = scaleCenterCrop(rawArt, w, h)
          canvas.drawBitmap(coverScaled, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
        } else {
          val fallback = Bitmap.createScaledBitmap(coverSet.blurredStrong, w, h, true)
          canvas.drawBitmap(fallback, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
        }
        val scrim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
          shader = LinearGradient(
            0f,
            0f,
            0f,
            h.toFloat(),
            intArrayOf(0x520A0D16, 0xA80A0D16.toInt(), 0xDB0A0D16.toInt()),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP
          )
        }
        canvas.drawRect(rect, scrim)
      }
    }

    cardBgCache.put(key, bmp)
    return bmp
  }

  /**
   * Renders style-appropriate left/center artwork:
   * - RING: circular cover with live progress ring arc
   * - VINYL: half-cropped spinning vinyl disc
   * - PIXEL: sharp square pixel-framed cover
   * - Others: crisp rounded album art
   */
  fun renderArtSlotBitmap(
    context: Context,
    style: NowPlayingWidgetStyle,
    rawArt: Bitmap?,
    palette: VinylCoverPalette,
    tokens: NowPlayingWidgetThemeTokens,
    progressFraction: Float,
    positionMs: Long,
    isPlaying: Boolean,
    reducedMotion: Boolean,
    sizePx: Int,
    coverKey: String
  ): Bitmap {
    if (style == NowPlayingWidgetStyle.VINYL) {
      val angle = VinylWidgetRenderer.computeVinylAngleDegrees(positionMs, isPlaying, reducedMotion)
      return VinylWidgetRenderer.renderVinylDiscBitmap(
        diameterPx = sizePx,
        palette = palette,
        rotationDegrees = angle,
        cropHalfOnLeft = true
      )
    }

    if (style == NowPlayingWidgetStyle.RING) {
      val progressBucket = (progressFraction.coerceIn(0f, 1f) * 36f).roundToInt()
      val ringKey = "ring|$sizePx|$coverKey|$progressBucket|${tokens.accent}"
      artFrameCache.get(ringKey)?.let { return it }

      val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
      val canvas = Canvas(bmp)
      val c = sizePx * 0.5f
      val ringStroke = max(5f, sizePx * 0.065f)
      val ringRadius = c - ringStroke * 0.6f
      val artRadius = ringRadius - ringStroke * 1.15f

      val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.style = Paint.Style.STROKE
        strokeWidth = ringStroke
        color = tokens.progressTrack
        strokeCap = Paint.Cap.ROUND
      }
      canvas.drawCircle(c, c, ringRadius, trackPaint)

      val arcRect = RectF(c - ringRadius, c - ringRadius, c + ringRadius, c + ringRadius)
      val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.style = Paint.Style.STROKE
        strokeWidth = ringStroke
        color = tokens.accent
        strokeCap = Paint.Cap.ROUND
      }
      val sweep = (progressFraction.coerceIn(0f, 1f) * 360f).coerceAtLeast(8f)
      canvas.drawArc(arcRect, -90f, sweep, false, arcPaint)

      val artBmp = createSquareArt(context, rawArt, (artRadius * 2f).roundToInt().coerceAtLeast(24), palette)
      val circleArt = clipCircle(artBmp)
      val offset = (c - circleArt.width * 0.5f)
      canvas.drawBitmap(circleArt, offset, offset, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))

      artFrameCache.put(ringKey, bmp)
      return bmp
    }

    val cornerFraction = when (style) {
      NowPlayingWidgetStyle.PIXEL -> 0.06f
      NowPlayingWidgetStyle.PAPER -> 0.14f
      else -> 0.24f
    }
    val key = "art|${style.id}|$sizePx|$coverKey|${rawArt != null}|${tokens.border}"
    artFrameCache.get(key)?.let { return it }

    val square = createSquareArt(context, rawArt, sizePx, palette)
    val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val rect = RectF(0f, 0f, sizePx.toFloat(), sizePx.toFloat())
    val radius = sizePx * cornerFraction
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    canvas.drawRoundRect(rect, radius, radius, paint)
    paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
    canvas.drawBitmap(square, 0f, 0f, paint)
    paint.xfermode = null

    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      this.style = Paint.Style.STROKE
      strokeWidth = if (style == NowPlayingWidgetStyle.PIXEL) 3f else 2f
      color = tokens.border
    }
    canvas.drawRoundRect(RectF(1f, 1f, sizePx - 1f, sizePx - 1f), radius, radius, borderPaint)

    artFrameCache.put(key, bmp)
    return bmp
  }

  /**
   * Renders title, artist, or synced lyric line with style-authentic typography:
   * - Kantumruy Pro / Outfit for all standard styles & Khmer runs
   * - Monospace for Pixel style (Latin) + Kantumruy Pro for Khmer
   * - Playfair Display Serif for Paper style (Latin) + Kantumruy Pro for Khmer
   * Enforces >= 1.4x line spacing on lyrics and single-line ellipsis on titles.
   */
  fun renderStyledTextBitmap(
    context: Context,
    style: NowPlayingWidgetStyle,
    text: String,
    textColor: Int,
    baseTextSizeDp: Float,
    isBold: Boolean,
    widthDp: Float,
    maxLines: Int,
    isLyricLine: Boolean = false,
    centered: Boolean = false,
    shadowColor: Int = Color.TRANSPARENT
  ): Bitmap? {
    return runCatching {
      val clean = text.trim()
      if (clean.isEmpty()) return null
      val isRtl = context.resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL
      val maxScale = if (isLyricLine) 2.15f else 2.40f
      val scale = context.resources.displayMetrics.density.coerceIn(1.85f, maxScale)
      val widthPx = (widthDp * scale).roundToInt().coerceIn(160, 920)

      val baseTypeface = when (style) {
        NowPlayingWidgetStyle.PIXEL -> Typeface.create(Typeface.MONOSPACE, if (isBold) Typeface.BOLD else Typeface.NORMAL)
        NowPlayingWidgetStyle.PAPER -> {
          val serif = KhmerTypography.loadTypeface(context, R.font.playfair_display, if (isBold) Typeface.BOLD else Typeface.NORMAL)
          if (serif != Typeface.DEFAULT) serif else Typeface.SERIF
        }
        else -> {
          if (isBold && !isLyricLine) KhmerTypography.loadOutfitTypeface(context, isBold = true)
          else KhmerTypography.loadUiTypeface(context, isBold = isBold)
        }
      }

      val styledText: CharSequence = if (style == NowPlayingWidgetStyle.PIXEL || style == NowPlayingWidgetStyle.PAPER) {
        buildStyleMixedSpannable(context, clean, baseTypeface, isBold)
      } else {
        KhmerTypography.buildStyledSpannable(
          context = context,
          text = clean,
          isTitle = isBold && !isLyricLine,
          khmerLyricsFont = KhmerLyricsFont.KANTUMRUY_PRO,
          isBold = isBold
        )
      }

      val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = baseTypeface
        color = textColor
        textSize = baseTextSizeDp * scale
        if (shadowColor != Color.TRANSPARENT) {
          setShadowLayer(2.0f * scale, 0f, 0.8f * scale, shadowColor)
        }
      }
      val align = when {
        centered -> Layout.Alignment.ALIGN_CENTER
        isRtl -> Layout.Alignment.ALIGN_OPPOSITE
        else -> Layout.Alignment.ALIGN_NORMAL
      }

      val lineMult = if (isLyricLine) 1.24f else 1.10f
      val layout = StaticLayout.Builder
        .obtain(styledText, 0, styledText.length, paint, widthPx)
        .setAlignment(align)
        .setLineSpacing(if (isLyricLine) 2.5f * scale else 1.0f * scale, lineMult)
        .setIncludePad(true)
        .setMaxLines(maxLines)
        .setEllipsize(TextUtils.TruncateAt.END)
        .build()

      val padY = (1.5f * scale).roundToInt()
      val heightPx = (layout.height + padY * 2).coerceIn(22, 300)
      val bmp = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
      val canvas = Canvas(bmp)
      canvas.save()
      canvas.translate(0f, padY.toFloat())
      layout.draw(canvas)
      canvas.restore()
      bmp
    }.getOrNull()
  }

  private fun buildStyleMixedSpannable(
    context: Context,
    text: String,
    latinTypeface: Typeface,
    isBold: Boolean
  ): CharSequence {
    val khmerTf = KhmerTypography.loadTypeface(
      context,
      if (isBold) R.font.kantumruy_pro_bold else R.font.kantumruy_pro
    )
    val span = SpannableString(text)
    var i = 0
    while (i < text.length) {
      val ch = text[i].code
      val isKhmer = ch in 0x1780..0x17FF || ch in 0x19E0..0x19FF
      var j = i + 1
      while (j < text.length) {
        val nextCode = text[j].code
        val nextKhmer = nextCode in 0x1780..0x17FF || nextCode in 0x19E0..0x19FF
        if (nextKhmer != isKhmer) break
        j++
      }
      val tf = if (isKhmer) khmerTf else latinTypeface
      if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
        span.setSpan(TypefaceSpan(tf), i, j, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
      }
      i = j
    }
    return span
  }

  private fun createSquareArt(
    context: Context,
    rawArt: Bitmap?,
    sizePx: Int,
    palette: VinylCoverPalette
  ): Bitmap {
    if (rawArt != null) {
      return scaleCenterCrop(rawArt, sizePx, sizePx)
    }
    val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      shader = LinearGradient(
        0f,
        0f,
        sizePx.toFloat(),
        sizePx.toFloat(),
        intArrayOf(palette.coverBase, palette.coverLight, palette.coverDark),
        floatArrayOf(0f, 0.55f, 1f),
        Shader.TileMode.CLAMP
      )
    }
    canvas.drawRect(0f, 0f, sizePx.toFloat(), sizePx.toFloat(), paint)
    runCatching {
      val orbi = ResourcesCompat.getDrawable(context.resources, R.drawable.ic_widget_orbi, null)
      orbi?.let {
        val pad = (sizePx * 0.25f).roundToInt()
        it.setBounds(pad, pad, sizePx - pad, sizePx - pad)
        it.draw(canvas)
      }
    }
    return bmp
  }

  private fun clipCircle(square: Bitmap): Bitmap {
    val d = min(square.width, square.height)
    val out = Bitmap.createBitmap(d, d, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(out)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    val r = d * 0.5f
    canvas.drawCircle(r, r, r, paint)
    paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
    canvas.drawBitmap(square, 0f, 0f, paint)
    return out
  }

  private fun scaleCenterCrop(src: Bitmap, targetW: Int, targetH: Int): Bitmap {
    val bmp = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val scale = max(targetW.toFloat() / src.width.toFloat(), targetH.toFloat() / src.height.toFloat())
    val scaledW = src.width * scale
    val scaledH = src.height * scale
    val left = (targetW - scaledW) * 0.5f
    val top = (targetH - scaledH) * 0.5f
    val dst = RectF(left, top, left + scaledW, top + scaledH)
    canvas.drawBitmap(src, null, dst, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
    return bmp
  }

  private fun withAlpha(argb: Int, alpha: Int): Int =
    ((alpha and 0xFF) shl 24) or (argb and 0x00FFFFFF)

  private fun blend(c1: Int, c2: Int, ratio: Float): Int {
    val t = ratio.coerceIn(0f, 1f)
    val r = (((c1 ushr 16) and 0xFF) * (1f - t) + ((c2 ushr 16) and 0xFF) * t).roundToInt()
    val g = (((c1 ushr 8) and 0xFF) * (1f - t) + ((c2 ushr 8) and 0xFF) * t).roundToInt()
    val b = ((c1 and 0xFF) * (1f - t) + (c2 and 0xFF) * t).roundToInt()
    return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
  }
}
