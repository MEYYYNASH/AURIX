package com.example.share

import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
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
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import java.util.Random
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class ShareCardMode {
  LYRICS,
  SONG
}

enum class ShareThemeStyle(val label: String) {
  ART("Art"),
  VIBRANT("Vibrant"),
  DARK("Dark"),
  MONO("Mono")
}

data class SharePaletteSwatches(
  val vibrant: Color,
  val muted: Color,
  val darkVibrant: Color,
  val dominant: Color
)

data class ShareGradientTheme(
  val id: String,
  val style: ShareThemeStyle,
  val name: String,
  val swatchPrimary: Color,
  val swatchSecondary: Color,
  val swatchTertiary: Color,
  val showBlurredArtwork: Boolean
)

data class ShareCardData(
  val title: String,
  val artist: String,
  val artworkBitmap: Bitmap?,
  val blurredArtworkBitmap: Bitmap?,
  val selectedLines: List<String>,
  val mode: ShareCardMode,
  val theme: ShareGradientTheme,
  val swatches: SharePaletteSwatches,
  val brandName: String = "AURIX MUSIC"
)

/**
 * Reusable Compose card preview with a 200ms theme crossfade and a 20-second looping mesh drift
 * (disabled when [reducedMotion] is true; exported 1080px PNG is always static).
 */
@Composable
fun ShareLyricsCard(
  data: ShareCardData,
  reducedMotion: Boolean = false,
  modifier: Modifier = Modifier
) {
  val infiniteTransition = rememberInfiniteTransition(label = "share_card_mesh_drift")
  val driftPhase by infiniteTransition.animateFloat(
    initialValue = 0f,
    targetValue = (2.0 * Math.PI).toFloat(),
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = 20_000, easing = LinearEasing),
      repeatMode = RepeatMode.Restart
    ),
    label = "mesh_phase"
  )

  val activePhase = if (reducedMotion) 0f else driftPhase

  Crossfade(
    targetState = data.theme,
    animationSpec = tween(durationMillis = 200),
    label = "card_theme_crossfade",
    modifier = modifier
      .shadow(
        elevation = 22.dp,
        shape = RoundedCornerShape(24.dp),
        ambientColor = Color.Black.copy(alpha = 0.55f),
        spotColor = Color.Black.copy(alpha = 0.65f),
        clip = false
      )
      .clip(RoundedCornerShape(24.dp))
  ) { animatedTheme ->
    val themedData = data.copy(theme = animatedTheme)
    Canvas(modifier = Modifier.fillMaxSize()) {
      val w = size.width.roundToInt().coerceAtLeast(1)
      val h = size.height.roundToInt().coerceAtLeast(1)
      ShareCardRenderer.draw(
        canvas = drawContext.canvas.nativeCanvas,
        widthPx = w,
        heightPx = h,
        data = themedData,
        driftPhaseRadians = activePhase
      )
    }
  }
}

object SharePaletteExtractor {
  /**
   * Builds the blurred, 1.3x saturation-boosted artwork bitmap so both the on-screen preview
   * and offscreen 1080px export look identical across API 24-36.
   */
  suspend fun createBlurredSaturatedArtwork(source: Bitmap?): Bitmap? = withContext(Dispatchers.Default) {
    if (source == null || source.isRecycled || source.width <= 0 || source.height <= 0) {
      return@withContext null
    }
    try {
      // 1. Downscale to 36x45 with 1.3x saturation boost
      val smallW = 36
      val smallH = 45
      val small = Bitmap.createBitmap(smallW, smallH, Bitmap.Config.ARGB_8888)
      val smallCanvas = Canvas(small)

      val satMatrix = ColorMatrix().apply { setSaturation(1.32f) }
      val satPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        colorFilter = ColorMatrixColorFilter(satMatrix)
      }

      val srcW = source.width.toFloat()
      val srcH = source.height.toFloat()
      val scale = max(smallW / srcW, smallH / srcH)
      val dx = (smallW - srcW * scale) * 0.5f
      val dy = (smallH - srcH * scale) * 0.5f
      val matrix = Matrix().apply {
        setScale(scale, scale)
        postTranslate(dx, dy)
      }
      smallCanvas.drawBitmap(source, matrix, satPaint)

      // 2. Multi-pass separable box blur (radius = 4 on 36x45 equals ~60dp Gaussian blur at full card size)
      boxBlurInPlace(small, radius = 4, iterations = 3)

      // 3. Smooth bilinear upscale to 288x360 so rendering onto any card size is silky-smooth
      val upW = 288
      val upH = 360
      Bitmap.createScaledBitmap(small, upW, upH, true)
    } catch (_: Exception) {
      null
    }
  }

  /**
   * Extracts Palette swatches (vibrant, muted, darkVibrant, dominant) and builds the 4 themes:
   * - "Art" (default, blurred artwork + Palette color mesh)
   * - "Vibrant" (vibrant-swatch mesh, no artwork)
   * - "Dark" (dark-vibrant to black)
   * - "Mono" (black with a tiny white glow)
   */
  suspend fun extractSwatchesAndThemes(
    bitmap: Bitmap?,
    fallbackTitle: String,
    accentColor: Color
  ): Pair<SharePaletteSwatches, List<ShareGradientTheme>> = withContext(Dispatchers.Default) {
    val (fallbackC1, fallbackC2) = artworkHueColors(fallbackTitle)
    val swatches = if (bitmap != null && !bitmap.isRecycled) {
      try {
        val palette = Palette.from(bitmap)
          .maximumColorCount(24)
          .clearFilters()
          .generate()

        val vibrantInt = palette.vibrantSwatch?.rgb
          ?: palette.lightVibrantSwatch?.rgb
          ?: palette.dominantSwatch?.rgb
          ?: accentColor.toArgb()

        val mutedInt = palette.mutedSwatch?.rgb
          ?: palette.lightMutedSwatch?.rgb
          ?: fallbackC2.toArgb()

        val darkVibrantInt = palette.darkVibrantSwatch?.rgb
          ?: palette.darkMutedSwatch?.rgb
          ?: ColorUtils.blendARGB(vibrantInt, android.graphics.Color.BLACK, 0.58f)

        val dominantInt = palette.dominantSwatch?.rgb ?: vibrantInt

        SharePaletteSwatches(
          vibrant = Color(boostSwatch(vibrantInt, minSat = 0.52f, lightnessRange = 0.42f..0.60f)),
          muted = Color(boostSwatch(mutedInt, minSat = 0.32f, lightnessRange = 0.30f..0.50f)),
          darkVibrant = Color(boostSwatch(darkVibrantInt, minSat = 0.40f, lightnessRange = 0.14f..0.28f)),
          dominant = Color(boostSwatch(dominantInt, minSat = 0.38f, lightnessRange = 0.24f..0.46f))
        )
      } catch (_: Exception) {
        defaultSwatches(accentColor, fallbackC1, fallbackC2)
      }
    } else {
      defaultSwatches(accentColor, fallbackC1, fallbackC2)
    }

    val themes = listOf(
      ShareGradientTheme(
        id = "art",
        style = ShareThemeStyle.ART,
        name = "Art",
        swatchPrimary = swatches.vibrant,
        swatchSecondary = swatches.muted,
        swatchTertiary = swatches.darkVibrant,
        showBlurredArtwork = true
      ),
      ShareGradientTheme(
        id = "vibrant",
        style = ShareThemeStyle.VIBRANT,
        name = "Vibrant",
        swatchPrimary = swatches.vibrant,
        swatchSecondary = swatches.muted,
        swatchTertiary = swatches.darkVibrant,
        showBlurredArtwork = false
      ),
      ShareGradientTheme(
        id = "dark",
        style = ShareThemeStyle.DARK,
        name = "Dark",
        swatchPrimary = swatches.darkVibrant,
        swatchSecondary = Color(ColorUtils.blendARGB(swatches.darkVibrant.toArgb(), android.graphics.Color.BLACK, 0.55f)),
        swatchTertiary = Color(0xFF05070C),
        showBlurredArtwork = false
      ),
      ShareGradientTheme(
        id = "mono",
        style = ShareThemeStyle.MONO,
        name = "Mono",
        swatchPrimary = Color(0xFF2B2E36),
        swatchSecondary = Color(0xFF14161B),
        swatchTertiary = Color(0xFF060709),
        showBlurredArtwork = false
      )
    )

    swatches to themes
  }

  private fun defaultSwatches(
    accentColor: Color,
    fallbackC1: Color,
    fallbackC2: Color
  ): SharePaletteSwatches {
    val vib = Color(boostSwatch(accentColor.toArgb(), minSat = 0.55f, lightnessRange = 0.45f..0.60f))
    val mut = Color(boostSwatch(fallbackC1.toArgb(), minSat = 0.38f, lightnessRange = 0.32f..0.48f))
    val darkVib = Color(boostSwatch(fallbackC2.toArgb(), minSat = 0.45f, lightnessRange = 0.14f..0.25f))
    return SharePaletteSwatches(
      vibrant = vib,
      muted = mut,
      darkVibrant = darkVib,
      dominant = vib
    )
  }

  private fun boostSwatch(
    rgb: Int,
    minSat: Float,
    lightnessRange: ClosedFloatingPointRange<Float>
  ): Int {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(rgb, hsl)
    hsl[1] = hsl[1].coerceIn(minSat, 0.92f)
    hsl[2] = hsl[2].coerceIn(lightnessRange.start, lightnessRange.endInclusive)
    return ColorUtils.HSLToColor(hsl)
  }

  private fun boxBlurInPlace(bitmap: Bitmap, radius: Int, iterations: Int) {
    val w = bitmap.width
    val h = bitmap.height
    val pixels = IntArray(w * h)
    val temp = IntArray(w * h)
    bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

    repeat(iterations) {
      // Horizontal pass
      for (y in 0 until h) {
        val rowOffset = y * w
        for (x in 0 until w) {
          var rSum = 0
          var gSum = 0
          var bSum = 0
          var count = 0
          for (dx in -radius..radius) {
            val nx = (x + dx).coerceIn(0, w - 1)
            val c = pixels[rowOffset + nx]
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
          for (dy in -radius..radius) {
            val ny = (y + dy).coerceIn(0, h - 1)
            val c = temp[ny * w + x]
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

object ShareCardRenderer {
  const val EXPORT_WIDTH_PX = 1080
  const val CARD_ASPECT_RATIO = 4f / 5f // 4:5 aspect ratio
  val EXPORT_HEIGHT_PX: Int = (EXPORT_WIDTH_PX / CARD_ASPECT_RATIO).roundToInt() // 1350 px

  // Lazily created 64x64 procedural noise tile (~4% alpha) to prevent gradient banding
  private val noiseShader: BitmapShader by lazy {
    val size = 64
    val noiseBmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val rng = Random(7331L)
    val pixels = IntArray(size * size)
    for (i in pixels.indices) {
      val isLight = rng.nextBoolean()
      val alpha = 8 + rng.nextInt(5) // ~4% alpha (8..12 / 255)
      val gray = if (isLight) 255 else 0
      pixels[i] = android.graphics.Color.argb(alpha, gray, gray, gray)
    }
    noiseBmp.setPixels(pixels, 0, size, 0, 0, size, size)
    BitmapShader(noiseBmp, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
  }

  private val noisePaint: Paint by lazy {
    Paint(Paint.ANTI_ALIAS_FLAG).apply {
      shader = noiseShader
    }
  }

  fun draw(
    canvas: Canvas,
    widthPx: Int,
    heightPx: Int,
    data: ShareCardData,
    driftPhaseRadians: Float = 0f
  ) {
    if (widthPx <= 0 || heightPx <= 0) return
    val scale = widthPx / 1080f
    val w = widthPx.toFloat()
    val h = heightPx.toFloat()
    val cornerRadius = 72f * scale // 24dp at 1080w

    val saveCount = canvas.save()
    try {
      val cardRect = RectF(0f, 0f, w, h)
      val clipPath = Path().apply {
        addRoundRect(cardRect, cornerRadius, cornerRadius, Path.Direction.CW)
      }
      canvas.clipPath(clipPath)

      // LAYER 1 (a & b): Base + Blurred Artwork + 3-Corner Palette Mesh
      drawLayeredBackground(
        canvas = canvas,
        cardRect = cardRect,
        w = w,
        h = h,
        data = data,
        driftPhaseRadians = driftPhaseRadians
      )

      // LAYER 1 (c): Dark scrim gradient + WCAG 4.5:1 luminance contrast check
      drawContrastVerifiedScrim(
        canvas = canvas,
        cardRect = cardRect,
        h = h,
        data = data
      )

      // LAYER 1 (d): Subtle 4% alpha grain/noise overlay to eliminate gradient banding
      canvas.drawRect(cardRect, noisePaint)

      // CONTENT: Lyrics Mode vs Song Mode
      val padH = 74f * scale
      val padTop = 74f * scale
      val padBottom = 66f * scale
      val footerHeight = 46f * scale

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

      // FOOTER: "AURIX MUSIC" mark at bottom left (85% alpha) + tiny equalizer icon
      drawBrandFooter(
        canvas = canvas,
        scale = scale,
        x = padH,
        bottomY = h - padBottom,
        brandName = data.brandName
      )

      // LAYER 1 (e): 1dp inner border (white, 12% alpha -> ~31/255)
      val borderStroke = (3f * scale).coerceAtLeast(1f) // 1dp at 1080w (3px)
      val inset = borderStroke * 0.5f
      val borderRect = RectF(inset, inset, w - inset, h - inset)
      val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = borderStroke
        color = android.graphics.Color.argb(31, 255, 255, 255) // 12% alpha white
      }
      canvas.drawRoundRect(
        borderRect,
        cornerRadius - inset,
        cornerRadius - inset,
        borderPaint
      )
    } finally {
      canvas.restoreToCount(saveCount)
    }
  }

  private fun drawLayeredBackground(
    canvas: Canvas,
    cardRect: RectF,
    w: Float,
    h: Float,
    data: ShareCardData,
    driftPhaseRadians: Float
  ) {
    val style = data.theme.style
    val swatches = data.swatches

    // Slow 20s mesh drift offsets (small translations; 0 when static or reducedMotion)
    val driftX = if (driftPhaseRadians == 0f) 0f else sin(driftPhaseRadians.toDouble()).toFloat() * (w * 0.055f)
    val driftY = if (driftPhaseRadians == 0f) 0f else cos(driftPhaseRadians.toDouble()).toFloat() * (h * 0.045f)

    when (style) {
      ShareThemeStyle.ART -> {
        // Base deep fill from darkVibrant
        val basePaint = Paint().apply {
          color = ColorUtils.blendARGB(swatches.darkVibrant.toArgb(), android.graphics.Color.BLACK, 0.35f)
        }
        canvas.drawRect(cardRect, basePaint)

        // (a) Album art scaled to fill card (centerCrop), heavily blurred (~60dp), saturation 1.3x
        val blurred = data.blurredArtworkBitmap
        if (blurred != null && !blurred.isRecycled && blurred.width > 0 && blurred.height > 0) {
          val matrix = Matrix()
          val sx = w / blurred.width.toFloat()
          val sy = h / blurred.height.toFloat()
          val maxScale = max(sx, sy) * 1.04f
          val dx = (w - blurred.width * maxScale) * 0.5f
          val dy = (h - blurred.height * maxScale) * 0.5f
          matrix.setScale(maxScale, maxScale)
          matrix.postTranslate(dx, dy)

          val artPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
          canvas.drawBitmap(blurred, matrix, artPaint)
        }

        // (b) 3 soft radial gradients from Palette (vibrant, muted, darkVibrant) at ~60% alpha
        drawThreeCornerMesh(
          canvas = canvas,
          cardRect = cardRect,
          w = w,
          h = h,
          c1 = swatches.vibrant.toArgb(),
          c2 = swatches.muted.toArgb(),
          c3 = swatches.darkVibrant.toArgb(),
          alpha = (255 * 0.60f).roundToInt(),
          driftX = driftX,
          driftY = driftY
        )
      }

      ShareThemeStyle.VIBRANT -> {
        // Rich base gradient from darkVibrant to deep midnight
        val basePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
          isDither = true
          shader = LinearGradient(
            0f,
            0f,
            w,
            h,
            intArrayOf(
              ColorUtils.blendARGB(swatches.vibrant.toArgb(), swatches.darkVibrant.toArgb(), 0.35f),
              swatches.darkVibrant.toArgb(),
              android.graphics.Color.rgb(8, 10, 18)
            ),
            floatArrayOf(0f, 0.58f, 1f),
            Shader.TileMode.CLAMP
          )
        }
        canvas.drawRect(cardRect, basePaint)

        // Vibrant-swatch 3-corner mesh (no artwork photo)
        drawThreeCornerMesh(
          canvas = canvas,
          cardRect = cardRect,
          w = w,
          h = h,
          c1 = swatches.vibrant.toArgb(),
          c2 = swatches.muted.toArgb(),
          c3 = swatches.darkVibrant.toArgb(),
          alpha = (255 * 0.72f).roundToInt(),
          driftX = driftX,
          driftY = driftY
        )
      }

      ShareThemeStyle.DARK -> {
        // Dark-vibrant to pure black
        val darkStart = ColorUtils.blendARGB(swatches.darkVibrant.toArgb(), android.graphics.Color.BLACK, 0.18f)
        val darkMid = ColorUtils.blendARGB(swatches.darkVibrant.toArgb(), android.graphics.Color.BLACK, 0.62f)
        val basePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
          isDither = true
          shader = LinearGradient(
            0f,
            0f,
            w,
            h,
            intArrayOf(darkStart, darkMid, android.graphics.Color.rgb(5, 6, 10)),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP
          )
        }
        canvas.drawRect(cardRect, basePaint)

        drawThreeCornerMesh(
          canvas = canvas,
          cardRect = cardRect,
          w = w,
          h = h,
          c1 = swatches.darkVibrant.toArgb(),
          c2 = ColorUtils.blendARGB(swatches.vibrant.toArgb(), android.graphics.Color.BLACK, 0.45f),
          c3 = android.graphics.Color.rgb(6, 8, 14),
          alpha = (255 * 0.55f).roundToInt(),
          driftX = driftX,
          driftY = driftY
        )
      }

      ShareThemeStyle.MONO -> {
        // Pure pitch black with a subtle white radial glow
        val basePaint = Paint().apply {
          color = android.graphics.Color.rgb(7, 8, 11)
        }
        canvas.drawRect(cardRect, basePaint)

        val glow1 = Paint(Paint.ANTI_ALIAS_FLAG).apply {
          shader = RadialGradient(
            w * 0.22f + driftX,
            h * 0.18f + driftY,
            w * 0.78f,
            android.graphics.Color.argb(36, 255, 255, 255),
            android.graphics.Color.TRANSPARENT,
            Shader.TileMode.CLAMP
          )
        }
        canvas.drawRect(cardRect, glow1)

        val glow2 = Paint(Paint.ANTI_ALIAS_FLAG).apply {
          shader = RadialGradient(
            w * 0.82f - driftX * 0.7f,
            h * 0.80f - driftY * 0.7f,
            w * 0.65f,
            android.graphics.Color.argb(20, 255, 255, 255),
            android.graphics.Color.TRANSPARENT,
            Shader.TileMode.CLAMP
          )
        }
        canvas.drawRect(cardRect, glow2)
      }
    }
  }

  private fun drawThreeCornerMesh(
    canvas: Canvas,
    cardRect: RectF,
    w: Float,
    h: Float,
    c1: Int,
    c2: Int,
    c3: Int,
    alpha: Int,
    driftX: Float,
    driftY: Float
  ) {
    // Corner 1: Top-left vibrant radial
    val mesh1 = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      isDither = true
      shader = RadialGradient(
        w * 0.14f + driftX,
        h * 0.14f + driftY,
        w * 0.88f,
        ColorUtils.setAlphaComponent(c1, alpha),
        android.graphics.Color.TRANSPARENT,
        Shader.TileMode.CLAMP
      )
    }
    canvas.drawRect(cardRect, mesh1)

    // Corner 2: Top-right / mid-right muted radial
    val mesh2 = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      isDither = true
      shader = RadialGradient(
        w * 0.88f - driftX * 0.8f,
        h * 0.36f + driftY * 0.6f,
        w * 0.82f,
        ColorUtils.setAlphaComponent(c2, alpha),
        android.graphics.Color.TRANSPARENT,
        Shader.TileMode.CLAMP
      )
    }
    canvas.drawRect(cardRect, mesh2)

    // Corner 3: Bottom-left dark-vibrant radial
    val mesh3 = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      isDither = true
      shader = RadialGradient(
        w * 0.22f + driftY * 0.6f,
        h * 0.88f - driftX * 0.7f,
        w * 0.90f,
        ColorUtils.setAlphaComponent(c3, (alpha * 1.08f).roundToInt().coerceIn(0, 255)),
        android.graphics.Color.TRANSPARENT,
        Shader.TileMode.CLAMP
      )
    }
    canvas.drawRect(cardRect, mesh3)
  }

  /**
   * Draws a dark scrim gradient (transparent/light at top to ~55% black at bottom) and checks the
   * composite background luminance so white text always achieves a WCAG contrast ratio >= 4.5:1.
   */
  private fun drawContrastVerifiedScrim(
    canvas: Canvas,
    cardRect: RectF,
    h: Float,
    data: ShareCardData
  ) {
    val swatches = data.swatches
    // Estimate upper/middle composite luminance from the active swatches + blurred art
    val rawLum = when (data.theme.style) {
      ShareThemeStyle.ART -> {
        val l1 = ColorUtils.calculateLuminance(swatches.vibrant.toArgb())
        val l2 = ColorUtils.calculateLuminance(swatches.muted.toArgb())
        val l3 = ColorUtils.calculateLuminance(swatches. dominant.toArgb())
        max(max(l1, l2), l3)
      }
      ShareThemeStyle.VIBRANT -> {
        max(
          ColorUtils.calculateLuminance(swatches.vibrant.toArgb()),
          ColorUtils.calculateLuminance(swatches.muted.toArgb())
        )
      }
      ShareThemeStyle.DARK -> ColorUtils.calculateLuminance(swatches.darkVibrant.toArgb())
      ShareThemeStyle.MONO -> 0.04
    }

    // For white text (L = 1.0) to have >= 4.5:1 contrast:
    // (1.0 + 0.05) / (L_bg + 0.05) >= 4.5  =>  L_bg <= 0.183
    val targetMaxLum = 0.165
    val extraTopDarkening = if (rawLum > targetMaxLum) {
      (1.0 - (targetMaxLum / rawLum)).toFloat().coerceIn(0.18f, 0.58f)
    } else {
      0.14f
    }

    val topAlpha = (extraTopDarkening * 255f).roundToInt().coerceIn(28, 155)
    val midAlpha = (max(0.34f, extraTopDarkening + 0.14f) * 255f).roundToInt().coerceIn(86, 175)
    val bottomAlpha = (max(0.56f, extraTopDarkening + 0.28f) * 255f).roundToInt().coerceIn(142, 205)

    val scrimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      isDither = true
      shader = LinearGradient(
        0f,
        0f,
        0f,
        h,
        intArrayOf(
          android.graphics.Color.argb(topAlpha, 0, 0, 0),
          android.graphics.Color.argb(midAlpha, 0, 0, 0),
          android.graphics.Color.argb(bottomAlpha, 0, 0, 0)
        ),
        floatArrayOf(0f, 0.50f, 1f),
        Shader.TileMode.CLAMP
      )
    }
    canvas.drawRect(cardRect, scrimPaint)
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
    // 4. TYPOGRAPHY & CARD CONTENT:
    // Header row: album art 48dp (144px at 1080w) with 14dp radius (42px),
    // title in bold 17sp (51px), max 2 lines with ellipsis,
    // "Song · Artist" in 13sp (39px) at 70% alpha (179/255).
    val artSize = 144f * scale // 48dp
    val artRadius = 42f * scale // 14dp
    val artRect = RectF(padH, padTop, padH + artSize, padTop + artSize)
    drawAlbumArtOrFallback(
      canvas = canvas,
      rect = artRect,
      cornerRadius = artRadius,
      bitmap = data.artworkBitmap,
      swatches = data.swatches,
      scale = scale
    )

    val textLeft = artRect.right + 34f * scale
    val textRight = w - padH
    val textMaxWidth = (textRight - textLeft).toInt().coerceAtLeast(10)

    val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
      color = android.graphics.Color.WHITE
      typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
      textSize = 51f * scale // 17sp
    }
    val subPaint = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
      color = android.graphics.Color.argb(179, 255, 255, 255) // 70% alpha
      typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
      textSize = 39f * scale // 13sp
    }

    val safeTitle = data.title.ifBlank { "Unknown Track" }
    val safeArtist = data.artist.ifBlank { "Unknown Artist" }
    val subtitleRaw = "Song · $safeArtist"

    val titleLayout = buildStaticLayout(
      text = safeTitle,
      paint = titlePaint,
      width = textMaxWidth,
      alignment = Layout.Alignment.ALIGN_NORMAL,
      lineSpacingMult = 1.08f,
      maxLines = 2
    )
    val subLayout = buildStaticLayout(
      text = subtitleRaw,
      paint = subPaint,
      width = textMaxWidth,
      alignment = Layout.Alignment.ALIGN_NORMAL,
      lineSpacingMult = 1.05f,
      maxLines = 1
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

    // Lyrics block:
    // Bold 32-36sp (96-108px at 1080w) white, left aligned, line height 1.2,
    // slight letter-spacing -0.01em, auto-shrinking down to 22sp (66px) for long text,
    // faint text shadow (0 2dp 12dp -> 0 6px 36px at 1080w, 25% black -> alpha 64).
    val headerBottom = max(artRect.bottom, headerTextStartY + totalHeaderTextH)
    val lyricsTopLimit = headerBottom + 56f * scale
    val lyricsBottomLimit = h - padBottom - footerHeight - 44f * scale
    val availableHeight = (lyricsBottomLimit - lyricsTopLimit).coerceAtLeast(120f * scale)
    val availableWidth = (w - padH * 2f).toInt().coerceAtLeast(20)

    val cleanedLines = data.selectedLines
      .map { it.trim() }
      .filter { it.isNotEmpty() }
      .take(5)

    if (cleanedLines.isEmpty()) return

    val maxFontPx = 104f * scale // ~34.6sp
    val preferredMinFontPx = 66f * scale // 22sp
    val hardFloorFontPx = 50f * scale // safety fallback for 5 multi-line paragraphs
    val stepPx = (3f * scale).coerceAtLeast(1f)

    val lyricPaint = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
      color = android.graphics.Color.WHITE
      typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
      letterSpacing = -0.01f
      // Shadow: 0 2dp 12dp, 25% black (alpha = 64)
      setShadowLayer(36f * scale, 0f, 6f * scale, android.graphics.Color.argb(64, 0, 0, 0))
    }

    var chosenLayouts: List<StaticLayout> = emptyList()
    var chosenLineGap = 24f * scale
    var totalLyricsHeight = 0f

    var candidateSize = maxFontPx
    val minTarget = if (cleanedLines.size <= 3) preferredMinFontPx else hardFloorFontPx
    while (candidateSize >= minTarget) {
      lyricPaint.textSize = candidateSize
      val lineGap = candidateSize * 0.28f
      val layouts = cleanedLines.map { lineText ->
        buildStaticLayout(
          text = lineText,
          paint = lyricPaint,
          width = availableWidth,
          alignment = Layout.Alignment.ALIGN_NORMAL,
          lineSpacingMult = 1.20f
        )
      }
      val sumHeight = layouts.sumOf { it.height } +
        ((layouts.size - 1).coerceAtLeast(0) * lineGap)
      chosenLayouts = layouts
      chosenLineGap = lineGap
      totalLyricsHeight = sumHeight
      if (sumHeight <= availableHeight) {
        break
      }
      candidateSize -= stepPx
    }

    // Grapheme-safe truncation if even at minTarget the text exceeds availableHeight
    if (totalLyricsHeight > availableHeight) {
      val fitted = mutableListOf<StaticLayout>()
      var usedH = 0f
      for (i in cleanedLines.indices) {
        val gap = if (fitted.isEmpty()) 0f else chosenLineGap
        val remainingH = availableHeight - usedH - gap
        if (remainingH <= lyricPaint.textSize * 1.15f) break
        val fullLayout = chosenLayouts[i]
        if (fullLayout.height <= remainingH) {
          fitted.add(fullLayout)
          usedH += gap + fullLayout.height
        } else {
          val truncated = truncateAtGraphemeBoundaryToFit(
            text = cleanedLines[i],
            paint = lyricPaint,
            width = availableWidth,
            maxHeight = remainingH
          )
          if (truncated != null) {
            fitted.add(truncated)
            usedH += gap + truncated.height
          }
          break
        }
      }
      if (fitted.isNotEmpty()) {
        chosenLayouts = fitted
        totalLyricsHeight = usedH
      }
    }

    val startY = lyricsTopLimit + ((availableHeight - totalLyricsHeight).coerceAtLeast(0f) * 0.44f)
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
    // Song mode: large centered artwork (~62% of the card width) with rounded corners
    // and a soft shadow, with the title and artist under it.
    val artSize = w * 0.62f
    val artLeft = (w - artSize) * 0.5f
    val contentBottom = h - padBottom - footerHeight - 36f * scale
    val artTop = padTop + 44f * scale
    val artRect = RectF(artLeft, artTop, artLeft + artSize, artTop + artSize)
    val artRadius = 56f * scale

    // Soft drop shadow behind centered album art
    val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = android.graphics.Color.argb(95, 0, 0, 0)
      setShadowLayer(42f * scale, 0f, 20f * scale, android.graphics.Color.argb(120, 0, 0, 0))
    }
    canvas.drawRoundRect(artRect, artRadius, artRadius, shadowPaint)

    drawAlbumArtOrFallback(
      canvas = canvas,
      rect = artRect,
      cornerRadius = artRadius,
      bitmap = data.artworkBitmap,
      swatches = data.swatches,
      scale = scale
    )

    val textTop = artRect.bottom + 58f * scale
    val textMaxWidth = (w - padH * 2f).toInt().coerceAtLeast(20)

    val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
      color = android.graphics.Color.WHITE
      typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
      textSize = 60f * scale
      setShadowLayer(24f * scale, 0f, 4f * scale, android.graphics.Color.argb(64, 0, 0, 0))
    }
    val artistPaint = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
      color = android.graphics.Color.argb(190, 255, 255, 255)
      typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
      textSize = 40f * scale
    }

    val safeTitle = data.title.ifBlank { "Unknown Track" }
    val safeArtist = data.artist.ifBlank { "Unknown Artist" }

    val titleLayout = buildStaticLayout(
      text = safeTitle,
      paint = titlePaint,
      width = textMaxWidth,
      alignment = Layout.Alignment.ALIGN_CENTER,
      lineSpacingMult = 1.12f,
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
    val drawStartY = textTop + (availableBelowArt - totalTextH) * 0.26f

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
    swatches: SharePaletteSwatches,
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
      val fallbackGradPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(
          rect.left,
          rect.top,
          rect.right,
          rect.bottom,
          intArrayOf(
            swatches.vibrant.toArgb(),
            swatches.muted.toArgb(),
            swatches.darkVibrant.toArgb()
          ),
          floatArrayOf(0f, 0.52f, 1f),
          Shader.TileMode.CLAMP
        )
      }
      canvas.drawRoundRect(rect, cornerRadius, cornerRadius, fallbackGradPaint)
      drawMusicNoteGlyph(canvas, rect, scale)
    }

    val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      style = Paint.Style.STROKE
      strokeWidth = 2f * scale
      color = android.graphics.Color.argb(45, 255, 255, 255)
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
    // Tiny equalizer icon + "AURIX MUSIC" mark at 85% alpha (217/255)
    val footerAlpha = 217
    val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = android.graphics.Color.argb(footerAlpha, 255, 255, 255)
      style = Paint.Style.FILL
    }
    val barWidth = 5f * scale
    val barGap = 4f * scale
    val heights = floatArrayOf(14f * scale, 26f * scale, 19f * scale, 11f * scale)
    val iconCenterY = bottomY - 12f * scale

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
      color = android.graphics.Color.argb(footerAlpha, 255, 255, 255)
      typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
      textSize = 29f * scale
      letterSpacing = 0.12f
    }
    canvas.drawText(brandName, bx + 11f * scale, bottomY - 2.5f * scale, brandPaint)
  }

  private fun buildStaticLayout(
    text: String,
    paint: TextPaint,
    width: Int,
    alignment: Layout.Alignment,
    lineSpacingMult: Float = 1.20f,
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
        lineSpacingMult = 1.20f
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
   * Renders the static card off the main thread at 1080px wide (1080 x 1350).
   */
  suspend fun renderCardBitmapOffscreen(
    data: ShareCardData,
    widthPx: Int = ShareCardRenderer.EXPORT_WIDTH_PX
  ): Bitmap = withContext(Dispatchers.Default) {
    val heightPx = (widthPx / ShareCardRenderer.CARD_ASPECT_RATIO).roundToInt().coerceAtLeast(1)
    val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    ShareCardRenderer.draw(
      canvas = canvas,
      widthPx = widthPx,
      heightPx = heightPx,
      data = data,
      driftPhaseRadians = 0f
    )
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
        listFiles()?.forEach { old -> runCatching { old.delete() } }
      }
      val outFile = File(shareDir, "aurix_lyrics_${System.currentTimeMillis()}.png")
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
    runCatching {
      context.startActivity(chooser)
    }
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
      val filename = "AURIX_Lyrics_${System.currentTimeMillis()}.png"
      val resolver = context.contentResolver
      val contentValues = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, filename)
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
          put(
            MediaStore.Images.Media.RELATIVE_PATH,
            Environment.DIRECTORY_PICTURES + "/AURIX MUSIC"
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
