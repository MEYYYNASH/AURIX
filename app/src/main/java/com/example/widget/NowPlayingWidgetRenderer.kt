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

  /**
   * Renders a complete, self-contained high-DPI home-screen widget card bitmap for any of the
   * 5 standard sizes (Small 2x2, Compact 4x1, Medium 4x2, Lyrics 4x2, Large 4x4) and 10 distinct
   * styles (Ring, Frost, Light, Night, Vinyl, Pixel, Paper, Aurora, Mono, Neon).
   */
  fun renderCompleteStudioCardBitmap(
    context: Context,
    style: NowPlayingWidgetStyle,
    studioSize: NowPlayingStudioSize,
    rawArt: Bitmap?,
    palette: VinylCoverPalette,
    titleRaw: String,
    artistRaw: String,
    prevLyricRaw: String,
    currentLyricRaw: String,
    nextLyricRaw: String,
    progressFractionRaw: Float,
    positionMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    reducedMotion: Boolean
  ): Bitmap {
    val density = 2.2f
    val w = (studioSize.widthDp * density).roundToInt().coerceAtLeast(200)
    val h = (studioSize.heightDp * density).roundToInt().coerceAtLeast(120)
    fun dp(v: Float): Float = v * density

    val title = titleRaw.trim().ifBlank { "Midnight Drive" }
    val artist = artistRaw.trim().ifBlank { "Nova Lane" }
    val prevLyric = prevLyricRaw.trim().ifBlank { "Streetlights bend across the glass" }
    val currLyric = currentLyricRaw.trim().ifBlank { "Keep the engine low and slow" }
    val nextLyric = nextLyricRaw.trim().ifBlank { "Every mile a little closer" }
    val progress = if (durationMs > 0L) {
      progressFractionRaw.coerceIn(0.08f, 1f)
    } else {
      0.62f
    }

    val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(out)
    val cardRect = RectF(0f, 0f, w.toFloat(), h.toFloat())
    val cornerRadius = when (style) {
      NowPlayingWidgetStyle.PIXEL -> dp(4f)
      NowPlayingWidgetStyle.TYPE -> dp(16f)
      NowPlayingWidgetStyle.PAPER -> dp(18f)
      NowPlayingWidgetStyle.FLOAT -> dp(20f)
      NowPlayingWidgetStyle.LIGHT, NowPlayingWidgetStyle.VINYL -> dp(22f)
      else -> dp(24f)
    }

    val clipPath = android.graphics.Path().apply {
      addRoundRect(cardRect, cornerRadius, cornerRadius, android.graphics.Path.Direction.CW)
    }
    canvas.save()
    canvas.clipPath(clipPath)

    // 1. Background per style
    val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    when (style) {
      NowPlayingWidgetStyle.RING -> {
        canvas.drawColor(0xFF13131A.toInt())
      }
      NowPlayingWidgetStyle.FROST -> {
        if (rawArt != null) {
          val blurred = scaleCenterCrop(rawArt, w, h)
          canvas.drawBitmap(blurred, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
          canvas.drawColor(0xAA3A1940.toInt())
        } else {
          bgPaint.shader = LinearGradient(
            0f, 0f, w.toFloat(), h.toFloat(),
            intArrayOf(0xFF5C1F42.toInt(), 0xFF3E1C47.toInt(), 0xFF241634.toInt()),
            floatArrayOf(0f, 0.52f, 1f),
            Shader.TileMode.CLAMP
          )
          canvas.drawRect(cardRect, bgPaint)
        }
      }
      NowPlayingWidgetStyle.LIGHT -> {
        canvas.drawColor(0xFFFFFFFF.toInt())
      }
      NowPlayingWidgetStyle.NIGHT -> {
        if (rawArt != null) {
          val poster = scaleCenterCrop(rawArt, w, h)
          canvas.drawBitmap(poster, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
        } else {
          bgPaint.shader = LinearGradient(
            0f, 0f, w.toFloat(), h.toFloat(),
            intArrayOf(0xFF2CC4AA.toInt(), 0xFF24688E.toInt(), 0xFF282B78.toInt()),
            floatArrayOf(0f, 0.48f, 1f),
            Shader.TileMode.CLAMP
          )
          canvas.drawRect(cardRect, bgPaint)
        }
        val scrim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
          shader = LinearGradient(
            0f, 0f, 0f, h.toFloat(),
            intArrayOf(0x14070910, 0x99070910.toInt(), 0xF2070910.toInt()),
            floatArrayOf(0f, 0.52f, 1f),
            Shader.TileMode.CLAMP
          )
        }
        canvas.drawRect(cardRect, scrim)
      }
      NowPlayingWidgetStyle.VINYL -> {
        canvas.drawColor(0xFFEAB32E.toInt())
      }
      NowPlayingWidgetStyle.PIXEL -> {
        canvas.drawColor(0xFF1C202B.toInt())
      }
      NowPlayingWidgetStyle.PAPER -> {
        canvas.drawColor(0xFFEFE8DC.toInt())
      }
      NowPlayingWidgetStyle.AURA -> {
        bgPaint.shader = LinearGradient(
          0f, 0f, w.toFloat(), h.toFloat(),
          intArrayOf(0xFF0B1D2C.toInt(), 0xFF143D4B.toInt(), 0xFF251E42.toInt()),
          floatArrayOf(0f, 0.52f, 1f),
          Shader.TileMode.CLAMP
        )
        canvas.drawRect(cardRect, bgPaint)
      }
      NowPlayingWidgetStyle.TYPE -> {
        canvas.drawColor(0xFF111215.toInt())
      }
      NowPlayingWidgetStyle.FLOAT -> {
        canvas.drawColor(0xFF090812.toInt())
      }
    }

    val primaryColor = when (style) {
      NowPlayingWidgetStyle.LIGHT -> 0xFF141418.toInt()
      NowPlayingWidgetStyle.VINYL -> 0xFF161618.toInt()
      NowPlayingWidgetStyle.PAPER -> 0xFF231F1C.toInt()
      NowPlayingWidgetStyle.TYPE -> 0xFFF4F4F0.toInt()
      else -> 0xFFFFFFFF.toInt()
    }
    val secondaryColor = when (style) {
      NowPlayingWidgetStyle.LIGHT -> 0xFF71717A.toInt()
      NowPlayingWidgetStyle.VINYL -> 0xFF4A380B.toInt()
      NowPlayingWidgetStyle.PAPER -> 0xFF7A7067.toInt()
      NowPlayingWidgetStyle.NIGHT -> 0xFFB4C6D0.toInt()
      NowPlayingWidgetStyle.PIXEL -> 0xFF9BA3B8.toInt()
      else -> 0xFF9C9CA9.toInt()
    }
    val accentColor = when (style) {
      NowPlayingWidgetStyle.RING -> 0xFFF27D52.toInt()
      NowPlayingWidgetStyle.FROST -> 0xFFFFFFFF.toInt()
      NowPlayingWidgetStyle.LIGHT -> 0xFFE11D48.toInt()
      NowPlayingWidgetStyle.NIGHT -> 0xFF4CE0B3.toInt()
      NowPlayingWidgetStyle.VINYL -> 0xFF161618.toInt()
      NowPlayingWidgetStyle.PIXEL -> 0xFFF4BD6A.toInt()
      NowPlayingWidgetStyle.PAPER -> 0xFFA84A24.toInt()
      NowPlayingWidgetStyle.AURA -> 0xFF5EEAD4.toInt()
      NowPlayingWidgetStyle.TYPE -> 0xFFFF5A36.toInt()
      NowPlayingWidgetStyle.FLOAT -> 0xFF00F0FF.toInt()
    }
    val trackColor = when (style) {
      NowPlayingWidgetStyle.LIGHT -> 0xFFE4E4E7.toInt()
      NowPlayingWidgetStyle.VINYL -> 0x33161618
      NowPlayingWidgetStyle.PIXEL -> 0xFF353B4B.toInt()
      NowPlayingWidgetStyle.PAPER -> 0x2E231F1C
      NowPlayingWidgetStyle.TYPE -> 0xFF262830.toInt()
      NowPlayingWidgetStyle.FLOAT -> 0xFF1E1B36.toInt()
      else -> 0x38FFFFFF
    }

    // Helper: draw vinyl clipped disc
    fun drawVinylDisc(cx: Float, cy: Float, radius: Float) {
      val discPaint = Paint(Paint.ANTI_ALIAS_FLAG)
      discPaint.color = 0xFF161618.toInt()
      canvas.drawCircle(cx, cy, radius, discPaint)
      val groovePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.style = Paint.Style.STROKE
        strokeWidth = dp(1.2f)
        color = 0xFF2A2A2E.toInt()
      }
      var r = radius * 0.42f
      while (r < radius * 0.92f) {
        canvas.drawCircle(cx, cy, r, groovePaint)
        r += dp(5.5f)
      }
      discPaint.color = 0xFFF08C6C.toInt()
      canvas.drawCircle(cx, cy, radius * 0.32f, discPaint)
      discPaint.color = 0xFF161618.toInt()
      canvas.drawCircle(cx, cy, radius * 0.09f, discPaint)
    }

    if (style == NowPlayingWidgetStyle.VINYL) {
      when (studioSize) {
        NowPlayingStudioSize.SMALL -> drawVinylDisc(w + dp(12f), dp(22f), dp(54f))
        NowPlayingStudioSize.COMPACT -> drawVinylDisc(w - dp(12f), h * 0.5f, dp(46f))
        NowPlayingStudioSize.MEDIUM, NowPlayingStudioSize.LYRICS -> drawVinylDisc(w - dp(16f), h * 0.5f, dp(76f))
        NowPlayingStudioSize.LARGE -> drawVinylDisc(w - dp(22f), dp(82f), dp(116f))
      }
    }

    // Helper: draw Pixel HUD header bar
    var topContentOffset = 0f
    if (style == NowPlayingWidgetStyle.PIXEL) {
      val barH = if (studioSize == NowPlayingStudioSize.COMPACT || studioSize == NowPlayingStudioSize.LYRICS) dp(15f) else dp(18f)
      val barPaint = Paint().apply { color = 0xFFF4BD6A.toInt() }
      canvas.drawRect(0f, 0f, w.toFloat(), barH, barPaint)
      val hudTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF1C202B.toInt()
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        textSize = dp(8.5f)
        letterSpacing = 0.14f
      }
      canvas.drawText("NOW PLAYING", dp(10f), barH * 0.72f, hudTextPaint)
      topContentOffset = barH
    }

    // Helper: draw style-specific cover art box
    fun drawStyleCover(left: Float, top: Float, size: Float) {
      val artBmp = createSquareArt(context, rawArt, size.roundToInt().coerceAtLeast(24), palette)
      when (style) {
        NowPlayingWidgetStyle.RING -> {
          val cx = left + size * 0.5f
          val cy = top + size * 0.5f
          val stroke = (size * 0.055f).coerceAtLeast(dp(3.5f))
          val r = size * 0.5f - stroke
          val ringTrack = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.style = Paint.Style.STROKE
            strokeWidth = stroke
            color = 0xFF2A2A35.toInt()
          }
          canvas.drawCircle(cx, cy, r, ringTrack)
          val ringFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.style = Paint.Style.STROKE
            strokeWidth = stroke
            strokeCap = Paint.Cap.ROUND
            color = 0xFFF27D52.toInt()
          }
          val arcRect = RectF(cx - r, cy - r, cx + r, cy + r)
          canvas.drawArc(arcRect, -90f, 360f * progress, false, ringFill)
          val innerD = (size - stroke * 3.4f).roundToInt().coerceAtLeast(16)
          val circleBmp = clipCircle(Bitmap.createScaledBitmap(artBmp, innerD, innerD, true))
          canvas.drawBitmap(circleBmp, cx - innerD * 0.5f, cy - innerD * 0.5f, Paint(Paint.ANTI_ALIAS_FLAG))
        }
        NowPlayingWidgetStyle.PAPER -> {
          canvas.save()
          val cx = left + size * 0.5f
          val cy = top + size * 0.5f
          canvas.rotate(-4f, cx, cy)
          val frameRect = RectF(left, top, left + size, top + size * 1.08f)
          val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x26000000 }
          canvas.drawRoundRect(RectF(frameRect.left + dp(2f), frameRect.top + dp(3f), frameRect.right + dp(2f), frameRect.bottom + dp(3f)), dp(3f), dp(3f), shadowPaint)
          val whitePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() }
          canvas.drawRoundRect(frameRect, dp(2f), dp(2f), whitePaint)
          val pad = size * 0.08f
          val innerS = (size - pad * 2f).roundToInt().coerceAtLeast(16)
          val scaled = Bitmap.createScaledBitmap(artBmp, innerS, innerS, true)
          canvas.drawBitmap(scaled, left + pad, top + pad, Paint(Paint.FILTER_BITMAP_FLAG))
          // Masking tape
          val tapeW = size * 0.36f
          val tapeH = dp(7f)
          val tapePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xD9E2D3B9.toInt() }
          canvas.drawRect(cx - tapeW * 0.5f, top - tapeH * 0.45f, cx + tapeW * 0.5f, top + tapeH * 0.55f, tapePaint)
          canvas.restore()
        }
        NowPlayingWidgetStyle.PIXEL -> {
          canvas.drawBitmap(artBmp, left, top, Paint(Paint.FILTER_BITMAP_FLAG))
          val borderP = Paint().apply {
            this.style = Paint.Style.STROKE
            strokeWidth = dp(2f)
            color = 0xFFFFFFFF.toInt()
          }
          canvas.drawRect(left, top, left + size, top + size, borderP)
        }
        NowPlayingWidgetStyle.AURA -> {
          val cx = left + size * 0.5f
          val cy = top + size * 0.5f
          val halo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.style = Paint.Style.STROKE
            strokeWidth = dp(1.5f)
            color = 0x335EEAD4
          }
          canvas.drawCircle(cx, cy, size * 0.54f, halo)
          val circleBmp = clipCircle(artBmp)
          canvas.drawBitmap(circleBmp, left, top, Paint(Paint.ANTI_ALIAS_FLAG))
        }
        else -> {
          val rad = if (style == NowPlayingWidgetStyle.FLOAT) dp(10f) else dp(14f)
          val rounded = Bitmap.createBitmap(size.roundToInt(), size.roundToInt(), Bitmap.Config.ARGB_8888)
          val rc = Canvas(rounded)
          val rp = Paint(Paint.ANTI_ALIAS_FLAG)
          rc.drawRoundRect(RectF(0f, 0f, size, size), rad, rad, rp)
          rp.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
          rc.drawBitmap(artBmp, 0f, 0f, rp)
          canvas.drawBitmap(rounded, left, top, null)
          if (style == NowPlayingWidgetStyle.FLOAT) {
            val neonB = Paint(Paint.ANTI_ALIAS_FLAG).apply {
              this.style = Paint.Style.STROKE
              strokeWidth = dp(1.5f)
              color = 0xFFFF2A85.toInt()
            }
            canvas.drawRoundRect(RectF(left, top, left + size, top + size), rad, rad, neonB)
          }
        }
      }
    }

    // Helper: draw style progress bar
    fun drawStyleProgress(left: Float, top: Float, width: Float) {
      if (style == NowPlayingWidgetStyle.RING) return
      if (style == NowPlayingWidgetStyle.PIXEL) {
        val segs = 20
        val gap = dp(2.2f)
        val segW = ((width - gap * (segs - 1)) / segs).coerceAtLeast(dp(2f))
        val segH = dp(6f)
        val active = (segs * progress).roundToInt().coerceIn(1, segs)
        val p = Paint()
        for (i in 0 until segs) {
          p.color = if (i < active) 0xFFF4BD6A.toInt() else 0xFF353B4B.toInt()
          val sx = left + i * (segW + gap)
          canvas.drawRect(sx, top, sx + segW, top + segH, p)
        }
        return
      }
      val barH = if (style == NowPlayingWidgetStyle.PAPER) dp(2.2f) else dp(3.8f)
      val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = trackColor }
      canvas.drawRoundRect(RectF(left, top, left + width, top + barH), barH * 0.5f, barH * 0.5f, tp)
      val fillW = (width * progress).coerceIn(barH, width)
      val fp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accentColor }
      canvas.drawRoundRect(RectF(left, top, left + fillW, top + barH), barH * 0.5f, barH * 0.5f, fp)
      if (style == NowPlayingWidgetStyle.FROST || style == NowPlayingWidgetStyle.PAPER) {
        val knobR = if (style == NowPlayingWidgetStyle.FROST) dp(4.8f) else dp(3.6f)
        canvas.drawCircle(left + fillW, top + barH * 0.5f, knobR, fp)
      }
    }

    // Helper: draw Play/Pause button
    fun drawPlayButton(cx: Float, cy: Float, radius: Float) {
      val btnBg = when (style) {
        NowPlayingWidgetStyle.RING -> 0xFFF27D52.toInt()
        NowPlayingWidgetStyle.FROST -> 0xFFFFFFFF.toInt()
        NowPlayingWidgetStyle.LIGHT -> 0xFFE11D48.toInt()
        NowPlayingWidgetStyle.NIGHT -> 0xFF4CE0B3.toInt()
        NowPlayingWidgetStyle.VINYL -> 0xFF161618.toInt()
        NowPlayingWidgetStyle.PIXEL -> 0xFFF4BD6A.toInt()
        NowPlayingWidgetStyle.PAPER -> Color.TRANSPARENT
        NowPlayingWidgetStyle.AURA -> 0xFF5EEAD4.toInt()
        NowPlayingWidgetStyle.TYPE -> 0xFFFF5A36.toInt()
        NowPlayingWidgetStyle.FLOAT -> 0x2800F0FF
      }
      val iconColor = when (style) {
        NowPlayingWidgetStyle.LIGHT -> 0xFFFFFFFF.toInt()
        NowPlayingWidgetStyle.VINYL -> 0xFFEAB32E.toInt()
        NowPlayingWidgetStyle.PAPER -> 0xFF231F1C.toInt()
        NowPlayingWidgetStyle.FLOAT -> 0xFF00F0FF.toInt()
        else -> 0xFF13131A.toInt()
      }
      val bgP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = btnBg }
      if (style == NowPlayingWidgetStyle.PIXEL) {
        canvas.drawRect(cx - radius, cy - radius, cx + radius, cy + radius, bgP)
      } else if (style == NowPlayingWidgetStyle.PAPER) {
        val ringP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
          this.style = Paint.Style.STROKE
          strokeWidth = dp(1.5f)
          color = 0xFF231F1C.toInt()
        }
        canvas.drawCircle(cx, cy, radius, ringP)
      } else {
        canvas.drawCircle(cx, cy, radius, bgP)
        if (style == NowPlayingWidgetStyle.FLOAT) {
          val ringP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.style = Paint.Style.STROKE
            strokeWidth = dp(1.5f)
            color = 0xFF00F0FF.toInt()
          }
          canvas.drawCircle(cx, cy, radius, ringP)
        }
      }
      val iconP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = iconColor }
      if (isPlaying) {
        val bw = radius * 0.22f
        val bh = radius * 0.42f
        val gap = radius * 0.14f
        canvas.drawRect(cx - gap - bw, cy - bh, cx - gap, cy + bh, iconP)
        canvas.drawRect(cx + gap, cy - bh, cx + gap + bw, cy + bh, iconP)
      } else {
        val path = android.graphics.Path().apply {
          moveTo(cx - radius * 0.24f, cy - radius * 0.36f)
          lineTo(cx + radius * 0.38f, cy)
          lineTo(cx - radius * 0.24f, cy + radius * 0.36f)
          close()
        }
        canvas.drawPath(path, iconP)
      }
    }

    // Helper: draw Prev/Play/Next cluster
    fun drawTransportCluster(leftX: Float, centerY: Float, btnRadius: Float, centered: Boolean = false) {
      val clusterW = dp(108f)
      val startX = if (centered) (w - clusterW) * 0.5f else leftX
      val prevCx = startX + dp(14f)
      val playCx = startX + clusterW * 0.5f
      val nextCx = startX + clusterW - dp(14f)
      val iconP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = primaryColor }
      val s = dp(5.5f)
      // Prev icon
      canvas.drawRect(prevCx - s, centerY - s, prevCx - s + dp(2f), centerY + s, iconP)
      val prevTri = android.graphics.Path().apply {
        moveTo(prevCx + s, centerY - s)
        lineTo(prevCx - s + dp(2.5f), centerY)
        lineTo(prevCx + s, centerY + s)
        close()
      }
      canvas.drawPath(prevTri, iconP)
      // Play/Pause
      drawPlayButton(playCx, centerY, btnRadius)
      // Next icon
      canvas.drawRect(nextCx + s - dp(2f), centerY - s, nextCx + s, centerY + s, iconP)
      val nextTri = android.graphics.Path().apply {
        moveTo(nextCx - s, centerY - s)
        lineTo(nextCx + s - dp(2.5f), centerY)
        lineTo(nextCx - s, centerY + s)
        close()
      }
      canvas.drawPath(nextTri, iconP)
    }

    // Helper: draw single-line text
    fun drawSingleLine(
      text: String,
      x: Float,
      y: Float,
      maxWidthPx: Float,
      sizeDp: Float,
      color: Int,
      bold: Boolean,
      centered: Boolean = false
    ) {
      val bmp = renderStyledTextBitmap(
        context = context,
        style = style,
        text = text,
        textColor = color,
        baseTextSizeDp = sizeDp,
        isBold = bold,
        widthDp = (maxWidthPx / density).coerceAtLeast(60f),
        maxLines = 1,
        centered = centered
      )
      if (bmp != null) {
        val drawX = if (centered) x - bmp.width * 0.5f else x
        canvas.drawBitmap(bmp, drawX, y, null)
      }
    }

    // 2. Layout composition per StudioSize
    when (studioSize) {
      NowPlayingStudioSize.SMALL -> {
        if (style == NowPlayingWidgetStyle.LIGHT) {
          val artH = h * 0.53f
          val artBmp = createSquareArt(context, rawArt, w, palette)
          canvas.drawBitmap(scaleCenterCrop(artBmp, w, artH.roundToInt()), 0f, 0f, null)
          drawPlayButton(w - dp(26f), artH, dp(17f))
          drawSingleLine(title, dp(12f), artH + dp(10f), w - dp(24f), 12.5f, primaryColor, true)
          drawSingleLine(artist, dp(12f), artH + dp(28f), w - dp(24f), 10.5f, secondaryColor, false)
          drawStyleProgress(dp(12f), h - dp(12f), w - dp(24f))
        } else if (style == NowPlayingWidgetStyle.RING) {
          val ringS = dp(86f)
          drawStyleCover((w - ringS) * 0.5f, dp(12f), ringS)
          drawPlayButton(w * 0.5f, dp(12f) + ringS * 0.5f, dp(16f))
          drawSingleLine(title, w * 0.5f, h - dp(44f), w - dp(20f), 12.5f, primaryColor, true, centered = true)
          drawSingleLine(artist, w * 0.5f, h - dp(25f), w - dp(20f), 10.5f, secondaryColor, false, centered = true)
        } else {
          val topY = topContentOffset + dp(12f)
          drawPlayButton(dp(28f), topY + dp(17f), dp(17f))
          if (style != NowPlayingWidgetStyle.VINYL && style != NowPlayingWidgetStyle.NIGHT && style != NowPlayingWidgetStyle.TYPE) {
            val artS = dp(56f)
            drawStyleCover(w - artS - dp(12f), topY, artS)
          }
          drawSingleLine(title, dp(12f), h - dp(54f), w - dp(24f), 12.5f, primaryColor, true)
          drawSingleLine(artist, dp(12f), h - dp(35f), w - dp(24f), 10.5f, secondaryColor, false)
          drawStyleProgress(dp(12f), h - dp(13f), w - dp(24f))
        }
      }

      NowPlayingStudioSize.COMPACT -> {
        if (style == NowPlayingWidgetStyle.LIGHT) {
          val artBmp = createSquareArt(context, rawArt, h, palette)
          canvas.drawBitmap(artBmp, 0f, 0f, null)
          val textX = h + dp(12f)
          val textW = w - textX - dp(56f)
          drawSingleLine(title, textX, dp(12f), textW, 13f, primaryColor, true)
          drawSingleLine(artist, textX, dp(31f), textW, 10.5f, secondaryColor, false)
          drawStyleProgress(textX, h - dp(14f), textW)
          drawPlayButton(w - dp(28f), h * 0.5f, dp(17f))
        } else {
          var textX = dp(14f)
          val cy = (h + topContentOffset) * 0.5f
          if (style != NowPlayingWidgetStyle.VINYL && style != NowPlayingWidgetStyle.NIGHT && style != NowPlayingWidgetStyle.TYPE) {
            val artS = dp(44f)
            drawStyleCover(dp(12f), cy - artS * 0.5f, artS)
            textX = dp(66f)
          }
          val rightPad = if (style == NowPlayingWidgetStyle.VINYL) dp(92f) else dp(56f)
          val textW = (w - textX - rightPad).coerceAtLeast(dp(80f))
          drawSingleLine(title, textX, topContentOffset + dp(10f), textW, 13f, primaryColor, true)
          drawSingleLine(artist, textX, topContentOffset + dp(28f), textW, 10.5f, secondaryColor, false)
          drawStyleProgress(textX, h - dp(13f), textW)
          drawPlayButton(w - (if (style == NowPlayingWidgetStyle.VINYL) dp(66f) else dp(28f)), cy, dp(17f))
        }
      }

      NowPlayingStudioSize.MEDIUM -> {
        if (style == NowPlayingWidgetStyle.RING) {
          val ringS = dp(110f)
          drawStyleCover(dp(16f), (h - ringS) * 0.5f, ringS)
          val rightX = dp(142f)
          val rightW = w - rightX - dp(16f)
          drawSingleLine(title, rightX, dp(24f), rightW, 16f, primaryColor, true)
          drawSingleLine(artist, rightX, dp(48f), rightW, 12f, secondaryColor, false)
          drawTransportCluster(rightX, h - dp(36f), dp(18f))
        } else if (style == NowPlayingWidgetStyle.LIGHT) {
          val bleedW = (w * 0.40f).roundToInt()
          val artBmp = createSquareArt(context, rawArt, max(bleedW, h), palette)
          canvas.drawBitmap(scaleCenterCrop(artBmp, bleedW, h), 0f, 0f, null)
          val rightX = bleedW + dp(16f)
          val rightW = w - rightX - dp(16f)
          drawSingleLine(title, rightX, dp(18f), rightW, 15.5f, primaryColor, true)
          drawSingleLine(artist, rightX, dp(40f), rightW, 12f, secondaryColor, false)
          drawStyleProgress(rightX, dp(76f), rightW)
          drawTransportCluster(rightX, h - dp(32f), dp(18f))
        } else {
          val topY = topContentOffset + dp(14f)
          val artOnLeft = style == NowPlayingWidgetStyle.PIXEL || style == NowPlayingWidgetStyle.PAPER
          val artS = if (style == NowPlayingWidgetStyle.PAPER) dp(78f) else dp(74f)
          var textX = dp(16f)
          var textW = w - dp(32f)
          if (artOnLeft) {
            drawStyleCover(dp(16f), topY, artS)
            textX = dp(16f) + artS + dp(14f)
            textW = w - textX - dp(16f)
          } else if (style != NowPlayingWidgetStyle.VINYL && style != NowPlayingWidgetStyle.NIGHT && style != NowPlayingWidgetStyle.TYPE) {
            drawStyleCover(w - artS - dp(16f), topY, artS)
            textW = w - artS - dp(44f)
          } else if (style == NowPlayingWidgetStyle.VINYL) {
            textW = w - dp(116f)
          }
          drawSingleLine(title, textX, topY + dp(2f), textW, if (style == NowPlayingWidgetStyle.TYPE) 20f else 16f, primaryColor, true)
          drawSingleLine(artist, textX, topY + dp(26f), textW, 12f, secondaryColor, false)
          drawStyleProgress(dp(16f), h - dp(54f), if (style == NowPlayingWidgetStyle.VINYL) w - dp(116f) else w - dp(32f))
          drawTransportCluster(dp(16f), h - dp(24f), dp(17f))
        }
      }

      NowPlayingStudioSize.LYRICS -> {
        val topY = topContentOffset + dp(10f)
        drawSingleLine("$title · $artist", dp(16f), topY, w - dp(68f), 11f, secondaryColor, true)
        drawPlayButton(w - dp(28f), topY + dp(10f), dp(13f))
        val lineW = if (style == NowPlayingWidgetStyle.VINYL) w - dp(96f) else w - dp(32f)
        val centerLyrics = style == NowPlayingWidgetStyle.RING
        val lx = if (centerLyrics) w * 0.5f else dp(16f)
        drawSingleLine(prevLyric, lx, topY + dp(30f), lineW, 11.5f, withAlpha(secondaryColor, 0x99), false, centered = centerLyrics)
        val currY = topY + dp(52f)
        if (style == NowPlayingWidgetStyle.VINYL) {
          val pillP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF161618.toInt() }
          canvas.drawRoundRect(RectF(dp(14f), currY - dp(2f), w - dp(76f), currY + dp(24f)), dp(6f), dp(6f), pillP)
          drawSingleLine(currLyric, dp(22f), currY, lineW - dp(16f), 13f, 0xFFEAB32E.toInt(), true)
        } else if (style == NowPlayingWidgetStyle.LIGHT) {
          val markerP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFECDD3.toInt() }
          canvas.drawRoundRect(RectF(dp(14f), currY + dp(8f), w - dp(28f), currY + dp(23f)), dp(3f), dp(3f), markerP)
          drawSingleLine(currLyric, dp(16f), currY, lineW, 13.5f, 0xFF141418.toInt(), true)
        } else {
          val prefixCurr = if (style == NowPlayingWidgetStyle.PIXEL) "> $currLyric" else currLyric
          val currColor = when (style) {
            NowPlayingWidgetStyle.RING, NowPlayingWidgetStyle.NIGHT, NowPlayingWidgetStyle.PIXEL,
            NowPlayingWidgetStyle.PAPER, NowPlayingWidgetStyle.AURA, NowPlayingWidgetStyle.FLOAT -> accentColor
            else -> primaryColor
          }
          drawSingleLine(prefixCurr, lx, currY, lineW, 13.5f, currColor, true, centered = centerLyrics)
        }
        drawSingleLine(nextLyric, lx, topY + dp(80f), lineW, 11.5f, withAlpha(secondaryColor, 0x99), false, centered = centerLyrics)
        drawStyleProgress(dp(16f), h - dp(13f), w - dp(32f))
      }

      NowPlayingStudioSize.LARGE -> {
        if (style == NowPlayingWidgetStyle.LIGHT) {
          val artH = (h * 0.55f).roundToInt()
          val artBmp = createSquareArt(context, rawArt, max(w, artH), palette)
          canvas.drawBitmap(scaleCenterCrop(artBmp, w, artH), 0f, 0f, null)
          drawSingleLine(title, dp(18f), artH + dp(16f), w - dp(36f), 18f, primaryColor, true)
          drawSingleLine(artist, dp(18f), artH + dp(42f), w - dp(36f), 13f, secondaryColor, false)
          drawStyleProgress(dp(18f), h - dp(72f), w - dp(36f))
          drawTransportCluster(0f, h - dp(34f), dp(19f), centered = true)
        } else {
          val topY = topContentOffset + dp(20f)
          if (style != NowPlayingWidgetStyle.VINYL && style != NowPlayingWidgetStyle.NIGHT && style != NowPlayingWidgetStyle.TYPE) {
            val artS = dp(170f)
            drawStyleCover((w - artS) * 0.5f, topY, artS)
          }
          val centered = style == NowPlayingWidgetStyle.RING
          val tx = if (centered) w * 0.5f else dp(20f)
          drawSingleLine(title, tx, h - dp(132f), w - dp(40f), if (style == NowPlayingWidgetStyle.TYPE) 28f else 19f, primaryColor, true, centered = centered)
          drawSingleLine(artist, tx, h - dp(100f), w - dp(40f), 13.5f, secondaryColor, false, centered = centered)
          drawStyleProgress(dp(20f), h - dp(68f), w - dp(40f))
          drawTransportCluster(dp(20f), h - dp(32f), dp(19f), centered = centered || style != NowPlayingWidgetStyle.VINYL)
        }
      }
    }

    canvas.restore()

    // Outer border
    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      this.style = Paint.Style.STROKE
      strokeWidth = if (style == NowPlayingWidgetStyle.PIXEL || style == NowPlayingWidgetStyle.FLOAT) dp(1.8f) else dp(1f)
      color = when (style) {
        NowPlayingWidgetStyle.FLOAT -> 0xFF00F0FF.toInt()
        NowPlayingWidgetStyle.PIXEL -> 0xFF353B4B.toInt()
        NowPlayingWidgetStyle.PAPER -> 0xFFDFD3C0.toInt()
        else -> 0x22FFFFFF
      }
    }
    val inset = borderPaint.strokeWidth * 0.5f
    canvas.drawRoundRect(RectF(inset, inset, w - inset, h - inset), cornerRadius, cornerRadius, borderPaint)

    return out
  }
}
