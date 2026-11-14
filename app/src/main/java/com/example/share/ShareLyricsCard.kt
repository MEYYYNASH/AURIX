package com.example.share

import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.text.LineBreakConfig
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import com.example.ui.screens.artworkHueColors
import java.io.File
import java.io.FileOutputStream
import java.text.BreakIterator
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class ShareCardMode {
  LYRICS,
  SONG
}

data class ShareGradientTheme(
  val id: String,
  val name: String,
  val startColor: Color,
  val midColor: Color,
  val endColor: Color,
  val accentGlowColor: Color
)

data class ShareCardData(
  val title: String,
  val artist: String,
  val artworkBitmap: Bitmap?,
  val selectedLines: List<String>,
  val mode: ShareCardMode,
  val theme: ShareGradientTheme,
  val brandName: String = "DCEAY MUSIC"
)

/**
 * Reusable Compose preview that delegates directly to [ShareCardRenderer.draw] so the on-screen
 * card and the exported 1080px PNG are pixel-identical.
 */
@Composable
fun ShareLyricsCard(
  data: ShareCardData,
  modifier: Modifier = Modifier
) {
  Canvas(
    modifier = modifier
      .fillMaxWidth()
      .aspectRatio(ShareCardRenderer.CARD_ASPECT_RATIO)
      .shadow(elevation = 18.dp, shape = RoundedCornerShape(24.dp), clip = false)
      .clip(RoundedCornerShape(24.dp))
  ) {
    val w = size.width.roundToInt().coerceAtLeast(1)
    val h = size.height.roundToInt().coerceAtLeast(1)
    ShareCardRenderer.draw(
      canvas = drawContext.canvas.nativeCanvas,
      widthPx = w,
      heightPx = h,
      data = data
    )
  }
}

object SharePaletteExtractor {
  /**
   * Extracts 4 diagonal gradient themes:
   * 1. Album Art Dominant/Vibrant (via AndroidX Palette API, falling back to accent/hue colors)
   * 2. Midnight Electric
   * 3. Crimson Sunset
   * 4. Emerald Noir
   */
  suspend fun buildThemes(
    bitmap: Bitmap?,
    fallbackTitle: String,
    accentColor: Color
  ): List<ShareGradientTheme> = withContext(Dispatchers.Default) {
    val (fallbackC1, fallbackC2) = artworkHueColors(fallbackTitle)
    val defaultTheme = if (bitmap != null && !bitmap.isRecycled) {
      try {
        val palette = Palette.from(bitmap)
          .maximumColorCount(24)
          .clearFilters()
          .generate()

        val primaryInt = palette.vibrantSwatch?.rgb
          ?: palette.dominantSwatch?.rgb
          ?: palette.lightVibrantSwatch?.rgb
          ?: accentColor.toArgb()

        val secondaryInt = palette.darkVibrantSwatch?.rgb
          ?: palette.mutedSwatch?.rgb
          ?: palette.darkMutedSwatch?.rgb
          ?: fallbackC2.toArgb()

        val startArgb = ensureRichCardStop(primaryInt, minSat = 0.45f, targetLightnessRange = 0.28f..0.46f)
        val midArgb = ColorUtils.blendARGB(
          startArgb,
          ensureRichCardStop(secondaryInt, minSat = 0.38f, targetLightnessRange = 0.18f..0.32f),
          0.58f
        )
        val endArgb = ColorUtils.blendARGB(midArgb, android.graphics.Color.rgb(9, 12, 22), 0.68f)
        val glowArgb = ensureRichCardStop(primaryInt, minSat = 0.65f, targetLightnessRange = 0.48f..0.62f)

        ShareGradientTheme(
          id = "album_palette",
          name = "Album",
          startColor = Color(startArgb),
          midColor = Color(midArgb),
          endColor = Color(endArgb),
          accentGlowColor = Color(glowArgb)
        )
      } catch (_: Exception) {
        createFallbackDefaultTheme(accentColor, fallbackC1, fallbackC2)
      }
    } else {
      createFallbackDefaultTheme(accentColor, fallbackC1, fallbackC2)
    }

    listOf(
      defaultTheme,
      ShareGradientTheme(
        id = "midnight_blue",
        name = "Midnight",
        startColor = Color(0xFF1D4ED8),
        midColor = Color(0xFF1E1B4B),
        endColor = Color(0xFF090D1A),
        accentGlowColor = Color(0xFF38BDF8)
      ),
      ShareGradientTheme(
        id = "crimson_rose",
        name = "Sunset",
        startColor = Color(0xFFBE185D),
        midColor = Color(0xFF4C0519),
        endColor = Color(0xFF130712),
        accentGlowColor = Color(0xFFFB7185)
      ),
      ShareGradientTheme(
        id = "emerald_teal",
        name = "Emerald",
        startColor = Color(0xFF0F766E),
        midColor = Color(0xFF064E3B),
        endColor = Color(0xFF071717),
        accentGlowColor = Color(0xFF2DD4BF)
      )
    )
  }

  private fun createFallbackDefaultTheme(
    accentColor: Color,
    fallbackC1: Color,
    fallbackC2: Color
  ): ShareGradientTheme {
    val startArgb = ensureRichCardStop(
      ColorUtils.blendARGB(accentColor.toArgb(), fallbackC1.toArgb(), 0.45f),
      minSat = 0.50f,
      targetLightnessRange = 0.30f..0.45f
    )
    val midArgb = ensureRichCardStop(
      fallbackC2.toArgb(),
      minSat = 0.42f,
      targetLightnessRange = 0.18f..0.30f
    )
    val endArgb = ColorUtils.blendARGB(midArgb, android.graphics.Color.rgb(10, 12, 22), 0.72f)
    return ShareGradientTheme(
      id = "album_palette",
      name = "Album",
      startColor = Color(startArgb),
      midColor = Color(midArgb),
      endColor = Color(endArgb),
      accentGlowColor = accentColor
    )
  }

  private fun ensureRichCardStop(
    rgb: Int,
    minSat: Float,
    targetLightnessRange: ClosedFloatingPointRange<Float>
  ): Int {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(rgb, hsl)
    hsl[1] = hsl[1].coerceIn(minSat, 0.88f)
    hsl[2] = hsl[2].coerceIn(targetLightnessRange.start, targetLightnessRange.endInclusive)
    return ColorUtils.HSLToColor(hsl)
  }
}

object ShareCardRenderer {
  const val EXPORT_WIDTH_PX = 1080
  const val CARD_ASPECT_RATIO = 4f / 4.6f
  val EXPORT_HEIGHT_PX: Int = (EXPORT_WIDTH_PX / CARD_ASPECT_RATIO).roundToInt() // 1242 px

  fun draw(
    canvas: Canvas,
    widthPx: Int,
    heightPx: Int,
    data: ShareCardData
  ) {
    if (widthPx <= 0 || heightPx <= 0) return
    val scale = widthPx / 1080f
    val w = widthPx.toFloat()
    val h = heightPx.toFloat()
    val cornerRadius = 72f * scale // ~24dp at 1080w

    val saveCount = canvas.save()
    try {
      val cardRect = RectF(0f, 0f, w, h)
      val clipPath = Path().apply {
        addRoundRect(cardRect, cornerRadius, cornerRadius, Path.Direction.CW)
      }
      canvas.clipPath(clipPath)

      // 1. Diagonal gradient background
      val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        isDither = true
        shader = LinearGradient(
          0f,
          0f,
          w,
          h,
          intArrayOf(
            data.theme.startColor.toArgb(),
            data.theme.midColor.toArgb(),
            data.theme.endColor.toArgb()
          ),
          floatArrayOf(0f, 0.54f, 1f),
          Shader.TileMode.CLAMP
        )
      }
      canvas.drawRect(cardRect, bgPaint)

      // 2. Ambient radial glow accents for depth
      val glowColor = data.theme.accentGlowColor.toArgb()
      val topGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(
          w * 0.18f,
          h * 0.14f,
          w * 0.72f,
          ColorUtils.setAlphaComponent(glowColor, 76),
          android.graphics.Color.TRANSPARENT,
          Shader.TileMode.CLAMP
        )
      }
      canvas.drawRect(cardRect, topGlowPaint)

      val bottomGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(
          w * 0.88f,
          h * 0.86f,
          w * 0.64f,
          ColorUtils.setAlphaComponent(data.theme.startColor.toArgb(), 64),
          android.graphics.Color.TRANSPARENT,
          Shader.TileMode.CLAMP
        )
      }
      canvas.drawRect(cardRect, bottomGlowPaint)

      // 3. Content mode: Lyrics vs Song
      val padH = 76f * scale
      val padTop = 76f * scale
      val padBottom = 68f * scale
      val footerHeight = 52f * scale

      if (data.mode == ShareCardMode.LYRICS && data.selectedLines.isNotEmpty()) {
        drawLyricsMode(
          canvas = canvas,
          w = w,
          h = h,
          scale = scale,
          padH = padH,
          padTop = padTop,
          padBottom = padBottom,
          footerHeight = footerHeight,
          data = data
        )
      } else {
        drawSongMode(
          canvas = canvas,
          w = w,
          h = h,
          scale = scale,
          padH = padH,
          padTop = padTop,
          padBottom = padBottom,
          footerHeight = footerHeight,
          data = data
        )
      }

      // 4. Bottom-left brand mark: "DCEAY MUSIC"
      drawBrandFooter(
        canvas = canvas,
        scale = scale,
        x = padH,
        bottomY = h - padBottom,
        brandName = data.brandName
      )

      // 5. Subtle glass inner border
      val borderStroke = 2.5f * scale
      val borderInset = borderStroke * 0.5f
      val borderRect = RectF(borderInset, borderInset, w - borderInset, h - borderInset)
      val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = borderStroke
        color = android.graphics.Color.argb(46, 255, 255, 255)
      }
      canvas.drawRoundRect(
        borderRect,
        cornerRadius - borderInset,
        cornerRadius - borderInset,
        borderPaint
      )
    } finally {
      canvas.restoreToCount(saveCount)
    }
  }

  private fun drawLyricsMode(
    canvas: Canvas,
    w: Float,
    h: Float,
    scale: Float,
    padH: Float,
    padTop: Float,
    padBottom: Float,
    footerHeight: Float,
    data: ShareCardData
  ) {
    // Top row: 44dp (~132px at 1080w) rounded album art + bold title + "Song · Artist"
    val artSize = 132f * scale
    val artRadius = 30f * scale
    val artRect = RectF(padH, padTop, padH + artSize, padTop + artSize)
    drawAlbumArtOrFallback(
      canvas = canvas,
      rect = artRect,
      cornerRadius = artRadius,
      bitmap = data.artworkBitmap,
      theme = data.theme,
      scale = scale
    )

    val textLeft = artRect.right + 32f * scale
    val textRight = w - padH
    val textMaxWidth = (textRight - textLeft).toInt().coerceAtLeast(10)

    val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
      color = android.graphics.Color.WHITE
      typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
      textSize = 44f * scale
    }
    val subPaint = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
      color = android.graphics.Color.argb(198, 255, 255, 255)
      typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
      textSize = 34f * scale
    }

    val safeTitle = data.title.ifBlank { "Unknown Track" }
    val safeArtist = data.artist.ifBlank { "Unknown Artist" }
    val ellipsizedTitle = TextUtils.ellipsize(
      safeTitle,
      titlePaint,
      textMaxWidth.toFloat(),
      TextUtils.TruncateAt.END
    ).toString()
    val subtitleRaw = "Song · $safeArtist"
    val ellipsizedSubtitle = TextUtils.ellipsize(
      subtitleRaw,
      subPaint,
      textMaxWidth.toFloat(),
      TextUtils.TruncateAt.END
    ).toString()

    val titleLayout = buildStaticLayout(
      text = ellipsizedTitle,
      paint = titlePaint,
      width = textMaxWidth,
      alignment = Layout.Alignment.ALIGN_NORMAL,
      lineSpacingMult = 1.05f
    )
    val subLayout = buildStaticLayout(
      text = ellipsizedSubtitle,
      paint = subPaint,
      width = textMaxWidth,
      alignment = Layout.Alignment.ALIGN_NORMAL,
      lineSpacingMult = 1.05f
    )

    val gapBetweenTitleAndSub = 8f * scale
    val totalHeaderTextH = titleLayout.height + gapBetweenTitleAndSub + subLayout.height
    val headerTextStartY = artRect.top + (artSize - totalHeaderTextH) * 0.5f

    canvas.save()
    canvas.translate(textLeft, headerTextStartY)
    titleLayout.draw(canvas)
    canvas.translate(0f, titleLayout.height + gapBetweenTitleAndSub)
    subLayout.draw(canvas)
    canvas.restore()

    // Middle lyrics block: large bold white text (about 30-34sp -> 90-102px at 1080w),
    // auto-shrinking down to 16sp (48px) so long lines or 5-line selections never clip.
    val lyricsTopLimit = artRect.bottom + 54f * scale
    val lyricsBottomLimit = h - padBottom - footerHeight - 44f * scale
    val availableHeight = (lyricsBottomLimit - lyricsTopLimit).coerceAtLeast(100f * scale)
    val availableWidth = (w - padH * 2f).toInt().coerceAtLeast(20)

    val cleanedLines = data.selectedLines
      .map { it.trim() }
      .filter { it.isNotEmpty() }
      .take(5)

    if (cleanedLines.isEmpty()) return

    val maxFontPx = 98f * scale // ~32.6sp
    val minFontPx = 46f * scale // ~15.3sp
    val stepPx = (3f * scale).coerceAtLeast(1f)

    val lyricPaint = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
      color = android.graphics.Color.WHITE
      typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
      setShadowLayer(12f * scale, 0f, 4f * scale, android.graphics.Color.argb(55, 0, 0, 0))
    }

    var chosenSizePx = maxFontPx
    var chosenLayouts: List<StaticLayout> = emptyList()
    var chosenLineGap = 22f * scale
    var totalLyricsHeight = 0f

    var candidateSize = maxFontPx
    while (candidateSize >= minFontPx) {
      lyricPaint.textSize = candidateSize
      val lineGap = candidateSize * 0.26f
      val layouts = cleanedLines.map { lineText ->
        buildStaticLayout(
          text = lineText,
          paint = lyricPaint,
          width = availableWidth,
          alignment = Layout.Alignment.ALIGN_NORMAL,
          lineSpacingMult = 1.10f
        )
      }
      val sumHeight = layouts.sumOf { it.height } +
        ((layouts.size - 1).coerceAtLeast(0) * lineGap)
      chosenSizePx = candidateSize
      chosenLayouts = layouts
      chosenLineGap = lineGap
      totalLyricsHeight = sumHeight
      if (sumHeight <= availableHeight) {
        break
      }
      candidateSize -= stepPx
    }

    // If even at minFontPx the combined lines exceed availableHeight, truncate the last visible
    // line on a safe grapheme cluster boundary so Khmer character clusters are never split.
    if (totalLyricsHeight > availableHeight) {
      lyricPaint.textSize = minFontPx
      val fitted = mutableListOf<StaticLayout>()
      var usedH = 0f
      for (i in cleanedLines.indices) {
        val gap = if (fitted.isEmpty()) 0f else chosenLineGap
        val remainingH = availableHeight - usedH - gap
        if (remainingH <= minFontPx * 1.1f) break
        val fullLayout = chosenLayouts[i]
        if (fullLayout.height <= remainingH) {
          fitted.add(fullLayout)
          usedH += gap + fullLayout.height
        } else {
          val truncatedLayout = truncateAtGraphemeBoundaryToFit(
            text = cleanedLines[i],
            paint = lyricPaint,
            width = availableWidth,
            maxHeight = remainingH
          )
          if (truncatedLayout != null) {
            fitted.add(truncatedLayout)
            usedH += gap + truncatedLayout.height
          }
          break
        }
      }
      if (fitted.isNotEmpty()) {
        chosenLayouts = fitted
        totalLyricsHeight = usedH
      }
    }

    val startY = lyricsTopLimit + ((availableHeight - totalLyricsHeight).coerceAtLeast(0f) * 0.42f)
    var currentY = startY
    for (layout in chosenLayouts) {
      canvas.save()
      canvas.translate(padH, currentY)
      layout.draw(canvas)
      canvas.restore()
      currentY += layout.height + chosenLineGap
    }
  }

  private fun drawSongMode(
    canvas: Canvas,
    w: Float,
    h: Float,
    scale: Float,
    padH: Float,
    padTop: Float,
    padBottom: Float,
    footerHeight: Float,
    data: ShareCardData
  ) {
    // Large centered album art (~60% of card width), title & artist below it, and brand mark
    val artSize = w * 0.60f
    val artLeft = (w - artSize) * 0.5f
    val contentBottom = h - padBottom - footerHeight - 36f * scale
    val artTop = padTop + 32f * scale
    val artRect = RectF(artLeft, artTop, artLeft + artSize, artTop + artSize)
    val artRadius = 56f * scale

    // Subtle drop shadow behind large album art
    val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = android.graphics.Color.argb(80, 0, 0, 0)
      setShadowLayer(36f * scale, 0f, 18f * scale, android.graphics.Color.argb(115, 0, 0, 0))
    }
    canvas.drawRoundRect(artRect, artRadius, artRadius, shadowPaint)

    drawAlbumArtOrFallback(
      canvas = canvas,
      rect = artRect,
      cornerRadius = artRadius,
      bitmap = data.artworkBitmap,
      theme = data.theme,
      scale = scale
    )

    val textTop = artRect.bottom + 56f * scale
    val textMaxWidth = (w - padH * 2f).toInt().coerceAtLeast(20)

    val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
      color = android.graphics.Color.WHITE
      typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
      textSize = 58f * scale
    }
    val artistPaint = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
      color = android.graphics.Color.argb(208, 255, 255, 255)
      typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
      textSize = 38f * scale
    }

    val safeTitle = data.title.ifBlank { "Unknown Track" }
    val safeArtist = data.artist.ifBlank { "Unknown Artist" }

    val titleLayout = buildStaticLayout(
      text = safeTitle,
      paint = titlePaint,
      width = textMaxWidth,
      alignment = Layout.Alignment.ALIGN_CENTER,
      lineSpacingMult = 1.08f,
      maxLines = 2
    )
    val artistLayout = buildStaticLayout(
      text = safeArtist,
      paint = artistPaint,
      width = textMaxWidth,
      alignment = Layout.Alignment.ALIGN_CENTER,
      lineSpacingMult = 1.08f,
      maxLines = 1
    )

    val totalTextH = titleLayout.height + 14f * scale + artistLayout.height
    val availableBelowArt = (contentBottom - textTop).coerceAtLeast(totalTextH)
    val drawStartY = textTop + (availableBelowArt - totalTextH) * 0.25f

    canvas.save()
    canvas.translate(padH, drawStartY)
    titleLayout.draw(canvas)
    canvas.translate(0f, titleLayout.height + 14f * scale)
    artistLayout.draw(canvas)
    canvas.restore()
  }

  private fun drawAlbumArtOrFallback(
    canvas: Canvas,
    rect: RectF,
    cornerRadius: Float,
    bitmap: Bitmap?,
    theme: ShareGradientTheme,
    scale: Float
  ) {
    if (bitmap != null && !bitmap.isRecycled && bitmap.width > 0 && bitmap.height > 0) {
      val shader = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
      val matrix = Matrix()
      val scaleX = rect.width() / bitmap.width.toFloat()
      val scaleY = rect.height() / bitmap.height.toFloat()
      val maxScale = max(scaleX, scaleY)
      val dx = rect.left + (rect.width() - bitmap.width * maxScale) * 0.5f
      val dy = rect.top + (rect.height() - bitmap.height * maxScale) * 0.5f
      matrix.setScale(maxScale, maxScale)
      matrix.postTranslate(dx, dy)
      shader.setLocalMatrix(matrix)

      val artPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        this.shader = shader
      }
      canvas.drawRoundRect(rect, cornerRadius, cornerRadius, artPaint)
    } else {
      // Missing album art: draw a gradient square with a music note icon
      val fallbackGradPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(
          rect.left,
          rect.top,
          rect.right,
          rect.bottom,
          intArrayOf(
            ColorUtils.blendARGB(theme.accentGlowColor.toArgb(), android.graphics.Color.WHITE, 0.18f),
            theme.startColor.toArgb(),
            theme.endColor.toArgb()
          ),
          floatArrayOf(0f, 0.5f, 1f),
          Shader.TileMode.CLAMP
        )
      }
      canvas.drawRoundRect(rect, cornerRadius, cornerRadius, fallbackGradPaint)
      drawMusicNoteGlyph(canvas, rect, scale)
    }

    val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      style = Paint.Style.STROKE
      strokeWidth = 2f * scale
      color = android.graphics.Color.argb(56, 255, 255, 255)
    }
    canvas.drawRoundRect(rect, cornerRadius, cornerRadius, strokePaint)
  }

  private fun drawMusicNoteGlyph(canvas: Canvas, rect: RectF, scale: Float) {
    val cx = rect.centerX()
    val cy = rect.centerY()
    val r = min(rect.width(), rect.height()) * 0.11f
    val stemHeight = min(rect.width(), rect.height()) * 0.30f
    val stemWidth = (rect.width() * 0.045f).coerceAtLeast(3f * scale)

    val notePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = android.graphics.Color.argb(232, 255, 255, 255)
      style = Paint.Style.FILL
    }

    // Left and right note heads
    val leftHeadX = cx - r * 1.15f
    val leftHeadY = cy + stemHeight * 0.38f
    val rightHeadX = cx + r * 1.25f
    val rightHeadY = cy + stemHeight * 0.22f

    canvas.drawOval(
      RectF(leftHeadX - r, leftHeadY - r * 0.78f, leftHeadX + r, leftHeadY + r * 0.78f),
      notePaint
    )
    canvas.drawOval(
      RectF(rightHeadX - r, rightHeadY - r * 0.78f, rightHeadX + r, rightHeadY + r * 0.78f),
      notePaint
    )

    // Stems & top beam
    val leftStemRight = leftHeadX + r
    val leftStemLeft = leftStemRight - stemWidth
    val leftStemTop = leftHeadY - stemHeight

    val rightStemRight = rightHeadX + r
    val rightStemLeft = rightStemRight - stemWidth
    val rightStemTop = rightHeadY - stemHeight

    canvas.drawRect(leftStemLeft, leftStemTop, leftStemRight, leftHeadY, notePaint)
    canvas.drawRect(rightStemLeft, rightStemTop, rightStemRight, rightHeadY, notePaint)

    val beamPath = Path().apply {
      moveTo(leftStemLeft, leftStemTop)
      lineTo(rightStemRight, rightStemTop)
      lineTo(rightStemRight, rightStemTop + stemWidth * 1.45f)
      lineTo(leftStemLeft, leftStemTop + stemWidth * 1.45f)
      close()
    }
    canvas.drawPath(beamPath, notePaint)
  }

  private fun drawBrandFooter(
    canvas: Canvas,
    scale: Float,
    x: Float,
    bottomY: Float,
    brandName: String
  ) {
    // Small waveform bars icon + "DCEAY MUSIC"
    val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = android.graphics.Color.argb(235, 255, 255, 255)
      style = Paint.Style.FILL
    }
    val barWidth = 5.5f * scale
    val barGap = 4.5f * scale
    val heights = floatArrayOf(16f * scale, 28f * scale, 20f * scale, 12f * scale)
    val iconCenterY = bottomY - 14f * scale

    var bx = x
    for (bh in heights) {
      val top = iconCenterY - bh * 0.5f
      val bottom = iconCenterY + bh * 0.5f
      canvas.drawRoundRect(
        RectF(bx, top, bx + barWidth, bottom),
        barWidth * 0.5f,
        barWidth * 0.5f,
        barPaint
      )
      bx += barWidth + barGap
    }

    val brandPaint = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
      color = android.graphics.Color.argb(235, 255, 255, 255)
      typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
      textSize = 31f * scale
      letterSpacing = 0.11f
    }
    canvas.drawText(brandName, bx + 12f * scale, bottomY - 3f * scale, brandPaint)
  }

  private fun buildStaticLayout(
    text: String,
    paint: TextPaint,
    width: Int,
    alignment: Layout.Alignment,
    lineSpacingMult: Float = 1.10f,
    maxLines: Int = Int.MAX_VALUE
  ): StaticLayout {
    val safeWidth = width.coerceAtLeast(1)
    val builder = StaticLayout.Builder
      .obtain(text, 0, text.length, paint, safeWidth)
      .setAlignment(alignment)
      .setLineSpacing(0f, lineSpacingMult)
      .setIncludePad(true)
      .setEllipsize(TextUtils.TruncateAt.END)
      .setMaxLines(maxLines)

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      builder.setLineBreakConfig(
        LineBreakConfig.Builder()
          .setLineBreakWordStyle(LineBreakConfig.LINE_BREAK_WORD_STYLE_PHRASE)
          .build()
      )
    }
    return builder.build()
  }

  /**
   * Truncates [text] cleanly on a grapheme cluster boundary so Khmer consonant/subscript/vowel
   * clusters and emoji are never split in half.
   */
  private fun truncateAtGraphemeBoundaryToFit(
    text: String,
    paint: TextPaint,
    width: Int,
    maxHeight: Float
  ): StaticLayout? {
    val boundaries = computeGraphemeBoundaries(text)
    if (boundaries.size <= 1) return null

    var low = 1
    var high = boundaries.lastIndex
    var bestLayout: StaticLayout? = null

    while (low <= high) {
      val mid = (low + high) ushr 1
      val endIdx = boundaries[mid]
      val candidateText = if (endIdx < text.length) {
        text.substring(0, endIdx).trimEnd() + "…"
      } else {
        text
      }
      val layout = buildStaticLayout(
        text = candidateText,
        paint = paint,
        width = width,
        alignment = Layout.Alignment.ALIGN_NORMAL,
        lineSpacingMult = 1.10f
      )
      if (layout.height <= maxHeight) {
        bestLayout = layout
        low = mid + 1
      } else {
        high = mid - 1
      }
    }
    return bestLayout
  }

  fun computeGraphemeBoundaries(text: String): IntArray {
    if (text.isEmpty()) return intArrayOf(0)
    val iterator = BreakIterator.getCharacterInstance()
    iterator.setText(text)
    val list = ArrayList<Int>(text.length + 1)
    var b = iterator.first()
    while (b != BreakIterator.DONE) {
      list.add(b)
      b = iterator.next()
    }
    if (list.isEmpty() || list.last() != text.length) {
      list.add(text.length)
    }
    return list.toIntArray()
  }
}

object ShareCardExporter {
  /**
   * Renders the card off the main thread at 1080px wide (1080 x 1242).
   */
  suspend fun renderCardBitmapOffscreen(
    data: ShareCardData,
    widthPx: Int = ShareCardRenderer.EXPORT_WIDTH_PX
  ): Bitmap = withContext(Dispatchers.Default) {
    val heightPx = (widthPx / ShareCardRenderer.CARD_ASPECT_RATIO).roundToInt().coerceAtLeast(1)
    val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    ShareCardRenderer.draw(canvas, widthPx, heightPx, data)
    bitmap
  }

  /**
   * Renders the card to a 1080px PNG off the main thread and launches Android's share sheet
   * with a FileProvider URI (`image/png`) plus the lyric text as `EXTRA_TEXT`.
   */
  suspend fun shareCardImage(
    context: Context,
    data: ShareCardData
  ): Result<Unit> = runCatching {
    val bitmap = renderCardBitmapOffscreen(data)
    val uri = withContext(Dispatchers.IO) {
      val shareDir = File(context.cacheDir, "shared_lyrics").apply {
        if (!exists()) mkdirs()
        // Clean up older temporary PNGs
        listFiles()?.forEach { old -> runCatching { old.delete() } }
      }
      val outFile = File(shareDir, "dceay_lyrics_${System.currentTimeMillis()}.png")
      FileOutputStream(outFile).use { fos ->
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
        fos.flush()
      }
      FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        outFile
      )
    }

    val extraText = buildShareText(data)
    val sendIntent = Intent(Intent.ACTION_SEND).apply {
      type = "image/png"
      putExtra(Intent.EXTRA_STREAM, uri)
      putExtra(Intent.EXTRA_TEXT, extraText)
      clipData = ClipData.newRawUri(data.brandName, uri)
      addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val chooser = Intent.createChooser(sendIntent, "Share lyrics").apply {
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(chooser)
  }

  /**
   * Saves the rendered 1080px PNG card to the device Gallery using MediaStore
   * (zero storage permissions needed on Android 10+).
   */
  suspend fun saveCardToGallery(
    context: Context,
    data: ShareCardData
  ): Result<Uri> = runCatching {
    val bitmap = renderCardBitmapOffscreen(data)
    withContext(Dispatchers.IO) {
      val filename = "DCEAY_Lyrics_${System.currentTimeMillis()}.png"
      val resolver = context.contentResolver
      val contentValues = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, filename)
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
          put(
            MediaStore.Images.Media.RELATIVE_PATH,
            Environment.DIRECTORY_PICTURES + "/DCEAY MUSIC"
          )
          put(MediaStore.Images.Media.IS_PENDING, 1)
        }
      }

      val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
      } else {
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI
      }

      val itemUri = resolver.insert(collection, contentValues)
        ?: throw IllegalStateException("Failed to create MediaStore entry")

      try {
        resolver.openOutputStream(itemUri)?.use { os ->
          if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, os)) {
            throw IllegalStateException("Failed to encode PNG")
          }
          os.flush()
        } ?: throw IllegalStateException("Failed to open MediaStore output stream")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
          val publishValues = ContentValues().apply {
            put(MediaStore.Images.Media.IS_PENDING, 0)
          }
          resolver.update(itemUri, publishValues, null, null)
        }
        itemUri
      } catch (e: Exception) {
        runCatching { resolver.delete(itemUri, null, null) }
        throw e
      }
    }
  }

  private fun buildShareText(data: ShareCardData): String {
    return if (data.mode == ShareCardMode.LYRICS && data.selectedLines.isNotEmpty()) {
      val quoted = data.selectedLines.joinToString("\n")
      "\"$quoted\"\n— ${data.title} · ${data.artist} (${data.brandName})"
    } else {
      "${data.title} · ${data.artist} (${data.brandName})"
    }
  }
}
