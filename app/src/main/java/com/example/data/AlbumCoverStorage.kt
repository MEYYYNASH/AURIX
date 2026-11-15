package com.example.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.media.ExifInterface
import android.net.Uri
import android.util.LruCache
import androidx.core.content.ContextCompat
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.example.R
import com.example.model.APP_ICON_ARTWORK
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class CachedBannerVisual(
  val blurredBitmap: ImageBitmap,
  val dominantColor: Color
)

object AlbumBannerCache {
  private val cache = LruCache<String, CachedBannerVisual>(24)

  fun get(key: String): CachedBannerVisual? = cache.get(key)

  fun clear() {
    cache.evictAll()
  }

  suspend fun getOrCreate(
    context: Context,
    artworkUrl: String
  ): CachedBannerVisual? {
    if (artworkUrl.isBlank()) return null
    cache.get(artworkUrl)?.let { return it }

    val created = withContext(Dispatchers.IO) {
      val sourceBitmap = AlbumCoverStorage.loadBitmapForUi(context, artworkUrl, targetSize = 180)
        ?: return@withContext null
      withContext(Dispatchers.Default) {
        val dominant = extractBannerDominantColor(sourceBitmap)
        val blurred = stackBlurBitmap(sourceBitmap, radius = 18)
        if (blurred !== sourceBitmap && !sourceBitmap.isRecycled) {
          sourceBitmap.recycle()
        }
        CachedBannerVisual(
          blurredBitmap = blurred.asImageBitmap(),
          dominantColor = dominant
        )
      }
    }

    if (created != null) {
      cache.put(artworkUrl, created)
    }
    return created
  }

  fun extractBannerDominantColor(bitmap: Bitmap): Color {
    val w = bitmap.width
    val h = bitmap.height
    if (w <= 0 || h <= 0) return Color(0xFFB9A2FF)

    val stepX = (w / 24).coerceAtLeast(1)
    val stepY = (h / 24).coerceAtLeast(1)
    val hsv = FloatArray(3)
    val hueBuckets = FloatArray(12)
    val hueR = FloatArray(12)
    val hueG = FloatArray(12)
    val hueB = FloatArray(12)

    var avgR = 0f
    var avgG = 0f
    var avgB = 0f
    var avgCount = 0

    var y = 0
    while (y < h) {
      var x = 0
      while (x < w) {
        val pixel = bitmap.getPixel(x, y)
        if (android.graphics.Color.alpha(pixel) >= 160) {
          val r = android.graphics.Color.red(pixel)
          val g = android.graphics.Color.green(pixel)
          val b = android.graphics.Color.blue(pixel)
          android.graphics.Color.RGBToHSV(r, g, b, hsv)
          val sat = hsv[1]
          val bri = hsv[2]
          if (bri > 0.12f) {
            avgR += r
            avgG += g
            avgB += b
            avgCount++
          }
          if (sat > 0.18f && bri > 0.18f) {
            val bucket = ((hsv[0] / 30f).toInt()).coerceIn(0, 11)
            val weight = sat * sat * (0.35f + bri)
            hueBuckets[bucket] += weight
            hueR[bucket] += r * weight
            hueG[bucket] += g * weight
            hueB[bucket] += b * weight
          }
        }
        x += stepX
      }
      y += stepY
    }

    val topBucket = (0..11).maxByOrNull { hueBuckets[it] } ?: 0
    val rawRgb = if (hueBuckets[topBucket] > 0.04f) {
      val weight = hueBuckets[topBucket]
      android.graphics.Color.rgb(
        (hueR[topBucket] / weight).toInt().coerceIn(0, 255),
        (hueG[topBucket] / weight).toInt().coerceIn(0, 255),
        (hueB[topBucket] / weight).toInt().coerceIn(0, 255)
      )
    } else if (avgCount > 0) {
      android.graphics.Color.rgb(
        (avgR / avgCount).toInt().coerceIn(0, 255),
        (avgG / avgCount).toInt().coerceIn(0, 255),
        (avgB / avgCount).toInt().coerceIn(0, 255)
      )
    } else {
      return Color(0xFFB9A2FF)
    }

    val outHsv = FloatArray(3)
    android.graphics.Color.colorToHSV(rawRgb, outHsv)
    // Ensure high legibility on dark #0e1120 rows & equalizer bars
    outHsv[1] = outHsv[1].coerceIn(0.38f, 0.85f)
    outHsv[2] = outHsv[2].coerceIn(0.76f, 0.96f)
    return Color(android.graphics.Color.HSVToColor(outHsv))
  }

  private fun stackBlurBitmap(source: Bitmap, radius: Int): Bitmap {
    val w = source.width
    val h = source.height
    if (w <= 0 || h <= 0 || radius < 1) return source

    val bitmap = source.copy(Bitmap.Config.ARGB_8888, true) ?: return source
    val pix = IntArray(w * h)
    bitmap.getPixels(pix, 0, w, 0, 0, w, h)

    val wm = w - 1
    val hm = h - 1
    val wh = w * h
    val div = radius + radius + 1

    val r = IntArray(wh)
    val g = IntArray(wh)
    val b = IntArray(wh)
    val vmin = IntArray(max(w, h))

    var divsum = (div + 1) shr 1
    divsum *= divsum
    val dv = IntArray(256 * divsum)
    for (i in dv.indices) {
      dv[i] = i / divsum
    }

    var yw = 0
    var yi = 0
    val stack = Array(div) { IntArray(3) }
    val r1 = radius + 1

    for (y in 0 until h) {
      var rinsum = 0
      var ginsum = 0
      var binsum = 0
      var routsum = 0
      var goutsum = 0
      var boutsum = 0
      var rsum = 0
      var gsum = 0
      var bsum = 0

      for (i in -radius..radius) {
        val p = pix[yi + min(wm, max(i, 0))]
        val sir = stack[i + radius]
        sir[0] = (p and 0xff0000) shr 16
        sir[1] = (p and 0x00ff00) shr 8
        sir[2] = p and 0x0000ff
        val rbs = r1 - kotlin.math.abs(i)
        rsum += sir[0] * rbs
        gsum += sir[1] * rbs
        bsum += sir[2] * rbs
        if (i > 0) {
          rinsum += sir[0]
          ginsum += sir[1]
          binsum += sir[2]
        } else {
          routsum += sir[0]
          goutsum += sir[1]
          boutsum += sir[2]
        }
      }
      var stackpointer = radius
      for (x in 0 until w) {
        r[yi] = dv[rsum]
        g[yi] = dv[gsum]
        b[yi] = dv[bsum]
        rsum -= routsum
        gsum -= goutsum
        bsum -= boutsum
        val stackstart = stackpointer - radius + div
        var sir = stack[stackstart % div]
        routsum -= sir[0]
        goutsum -= sir[1]
        boutsum -= sir[2]
        if (y == 0) {
          vmin[x] = min(x + radius + 1, wm)
        }
        val p = pix[yw + vmin[x]]
        sir[0] = (p and 0xff0000) shr 16
        sir[1] = (p and 0x00ff00) shr 8
        sir[2] = p and 0x0000ff
        rinsum += sir[0]
        ginsum += sir[1]
        binsum += sir[2]
        rsum += rinsum
        gsum += ginsum
        bsum += binsum
        stackpointer = (stackpointer + 1) % div
        sir = stack[stackpointer % div]
        routsum += sir[0]
        goutsum += sir[1]
        boutsum += sir[2]
        rinsum -= sir[0]
        ginsum -= sir[1]
        binsum -= sir[2]
        yi++
      }
      yw += w
    }

    for (x in 0 until w) {
      var rinsum = 0
      var ginsum = 0
      var binsum = 0
      var routsum = 0
      var goutsum = 0
      var boutsum = 0
      var rsum = 0
      var gsum = 0
      var bsum = 0
      var yp = -radius * w
      for (i in -radius..radius) {
        yi = max(0, yp) + x
        val sir = stack[i + radius]
        sir[0] = r[yi]
        sir[1] = g[yi]
        sir[2] = b[yi]
        val rbs = r1 - kotlin.math.abs(i)
        rsum += r[yi] * rbs
        gsum += g[yi] * rbs
        bsum += b[yi] * rbs
        if (i > 0) {
          rinsum += sir[0]
          ginsum += sir[1]
          binsum += sir[2]
        } else {
          routsum += sir[0]
          goutsum += sir[1]
          boutsum += sir[2]
        }
        if (i < hm) yp += w
      }
      yi = x
      var stackpointer = radius
      for (y in 0 until h) {
        pix[yi] = (-0x1000000) or (dv[rsum] shl 16) or (dv[gsum] shl 8) or dv[bsum]
        rsum -= routsum
        gsum -= goutsum
        bsum -= boutsum
        val stackstart = stackpointer - radius + div
        var sir = stack[stackstart % div]
        routsum -= sir[0]
        goutsum -= sir[1]
        boutsum -= sir[2]
        if (x == 0) {
          vmin[y] = min(y + r1, hm) * w
        }
        val p = x + vmin[y]
        sir[0] = r[p]
        sir[1] = g[p]
        sir[2] = b[p]
        rinsum += sir[0]
        ginsum += sir[1]
        binsum += sir[2]
        rsum += rinsum
        gsum += ginsum
        bsum += binsum
        stackpointer = (stackpointer + 1) % div
        sir = stack[stackpointer]
        routsum += sir[0]
        goutsum += sir[1]
        boutsum += sir[2]
        rinsum -= sir[0]
        ginsum -= sir[1]
        binsum -= sir[2]
        yi += w
      }
    }

    bitmap.setPixels(pix, 0, w, 0, 0, w, h)
    return bitmap
  }
}

object AlbumCoverStorage {
  const val COVER_OUTPUT_SIZE_PX = 1024

  fun computeMixGapPx(canvasSizePx: Int = COVER_OUTPUT_SIZE_PX): Float {
    // Proportional to 4px gap on a 176px preview -> ~23px on 1024px output
    return (canvasSizePx * (4f / 176f)).roundToInt().toFloat()
  }

  fun computeCenterCropSrcRect(
    srcWidth: Int,
    srcHeight: Int,
    dstWidth: Float,
    dstHeight: Float,
    offsetXFraction: Float = 0f,
    offsetYFraction: Float = 0f,
    zoom: Float = 1f
  ): Rect {
    if (srcWidth <= 0 || srcHeight <= 0 || dstWidth <= 0f || dstHeight <= 0f) {
      return Rect(0, 0, max(1, srcWidth), max(1, srcHeight))
    }
    val targetAspect = dstWidth / dstHeight
    val srcAspect = srcWidth.toFloat() / srcHeight.toFloat()

    val baseCropW: Float
    val baseCropH: Float
    if (srcAspect > targetAspect) {
      baseCropH = srcHeight.toFloat()
      baseCropW = baseCropH * targetAspect
    } else {
      baseCropW = srcWidth.toFloat()
      baseCropH = baseCropW / targetAspect
    }

    val safeZoom = zoom.coerceIn(1f, 3f)
    val cropW = (baseCropW / safeZoom).coerceAtLeast(1f)
    val cropH = (baseCropH / safeZoom).coerceAtLeast(1f)

    val maxShiftX = ((srcWidth - cropW) * 0.5f).coerceAtLeast(0f)
    val maxShiftY = ((srcHeight - cropH) * 0.5f).coerceAtLeast(0f)

    val cx = srcWidth * 0.5f - offsetXFraction.coerceIn(-1f, 1f) * maxShiftX
    val cy = srcHeight * 0.5f - offsetYFraction.coerceIn(-1f, 1f) * maxShiftY

    val left = (cx - cropW * 0.5f).roundToInt().coerceIn(0, max(0, srcWidth - cropW.roundToInt()))
    val top = (cy - cropH * 0.5f).roundToInt().coerceIn(0, max(0, srcHeight - cropH.roundToInt()))
    val right = (left + cropW.roundToInt()).coerceIn(left + 1, srcWidth)
    val bottom = (top + cropH.roundToInt()).coerceIn(top + 1, srcHeight)
    return Rect(left, top, right, bottom)
  }

  suspend fun renderAndSavePhotoCover(
    context: Context,
    albumId: String,
    imageSource: String,
    offsetXFraction: Float = 0f,
    offsetYFraction: Float = 0f,
    zoom: Float = 1f
  ): Result<String> = withContext(Dispatchers.IO) {
    runCatching {
      val srcBitmap = loadBitmapForUi(context, imageSource, targetSize = COVER_OUTPUT_SIZE_PX)
        ?: error("Unable to load selected image")
      val outBitmap = Bitmap.createBitmap(
        COVER_OUTPUT_SIZE_PX,
        COVER_OUTPUT_SIZE_PX,
        Bitmap.Config.ARGB_8888
      )
      try {
        val canvas = Canvas(outBitmap)
        val srcRect = computeCenterCropSrcRect(
          srcWidth = srcBitmap.width,
          srcHeight = srcBitmap.height,
          dstWidth = COVER_OUTPUT_SIZE_PX.toFloat(),
          dstHeight = COVER_OUTPUT_SIZE_PX.toFloat(),
          offsetXFraction = offsetXFraction,
          offsetYFraction = offsetYFraction,
          zoom = zoom
        )
        val dstRect = RectF(0f, 0f, COVER_OUTPUT_SIZE_PX.toFloat(), COVER_OUTPUT_SIZE_PX.toFloat())
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(srcBitmap, srcRect, dstRect, paint)
        writeAlbumCoverBitmap(context, albumId, outBitmap)
      } finally {
        if (!srcBitmap.isRecycled) srcBitmap.recycle()
        if (!outBitmap.isRecycled) outBitmap.recycle()
      }
    }
  }

  suspend fun renderAndSaveMixCover(
    context: Context,
    albumId: String,
    imageSources: List<String>
  ): Result<String> = withContext(Dispatchers.IO) {
    runCatching {
      val cleanSources = imageSources.filter { it.isNotBlank() }.take(4)
      require(cleanSources.isNotEmpty()) { "Select at least 1 image for Mix cover" }

      val size = COVER_OUTPUT_SIZE_PX.toFloat()
      val gap = computeMixGapPx(COVER_OUTPUT_SIZE_PX)
      val bitmaps = cleanSources.mapNotNull { src ->
        loadBitmapForUi(context, src, targetSize = COVER_OUTPUT_SIZE_PX)
      }
      require(bitmaps.isNotEmpty()) { "Unable to load selected mix images" }

      val outBitmap = Bitmap.createBitmap(
        COVER_OUTPUT_SIZE_PX,
        COVER_OUTPUT_SIZE_PX,
        Bitmap.Config.ARGB_8888
      )
      try {
        val canvas = Canvas(outBitmap)
        canvas.drawColor(android.graphics.Color.parseColor("#0E1120"))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        val slots: List<RectF> = when (bitmaps.size) {
          1 -> listOf(RectF(0f, 0f, size, size))
          2 -> {
            val halfW = (size - gap) * 0.5f
            listOf(
              RectF(0f, 0f, halfW, size),
              RectF(halfW + gap, 0f, size, size)
            )
          }
          3 -> {
            val halfW = (size - gap) * 0.5f
            val halfH = (size - gap) * 0.5f
            listOf(
              RectF(0f, 0f, halfW, size),
              RectF(halfW + gap, 0f, size, halfH),
              RectF(halfW + gap, halfH + gap, size, size)
            )
          }
          else -> {
            val half = (size - gap) * 0.5f
            listOf(
              RectF(0f, 0f, half, half),
              RectF(half + gap, 0f, size, half),
              RectF(0f, half + gap, half, size),
              RectF(half + gap, half + gap, size, size)
            )
          }
        }

        slots.forEachIndexed { idx, dstRect ->
          val bmp = bitmaps[idx]
          val srcRect = computeCenterCropSrcRect(
            srcWidth = bmp.width,
            srcHeight = bmp.height,
            dstWidth = dstRect.width(),
            dstHeight = dstRect.height()
          )
          canvas.drawBitmap(bmp, srcRect, dstRect, paint)
        }

        writeAlbumCoverBitmap(context, albumId, outBitmap)
      } finally {
        bitmaps.forEach { if (!it.isRecycled) it.recycle() }
        if (!outBitmap.isRecycled) outBitmap.recycle()
      }
    }
  }

  suspend fun renderAndSaveAppIconCover(
    context: Context,
    albumId: String
  ): Result<String> = withContext(Dispatchers.IO) {
    runCatching {
      val size = COVER_OUTPUT_SIZE_PX
      val outBitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
      try {
        val canvas = Canvas(outBitmap)
        val gradientPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
          shader = LinearGradient(
            0f,
            0f,
            size.toFloat(),
            size.toFloat(),
            intArrayOf(
              android.graphics.Color.parseColor("#7B61FF"),
              android.graphics.Color.parseColor("#2D1E69")
            ),
            null,
            Shader.TileMode.CLAMP
          )
        }
        canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), gradientPaint)

        val drawable = ContextCompat.getDrawable(context, R.drawable.ic_launcher_foreground)
        if (drawable != null) {
          val iconSize = (size * 0.86f).roundToInt()
          val left = (size - iconSize) / 2
          val top = (size - iconSize) / 2
          drawable.setBounds(left, top, left + iconSize, top + iconSize)
          drawable.draw(canvas)
        }
        writeAlbumCoverBitmap(context, albumId, outBitmap)
      } finally {
        if (!outBitmap.isRecycled) outBitmap.recycle()
      }
    }
  }

  private fun writeAlbumCoverBitmap(
    context: Context,
    albumId: String,
    bitmap: Bitmap
  ): String {
    val dir = File(context.filesDir, "album_covers").apply { if (!exists()) mkdirs() }
    val safeHash = albumId.hashCode().toUInt().toString(16)
    val outFile = File(dir, "album_${safeHash}_${System.currentTimeMillis()}.jpg")
    FileOutputStream(outFile).use { fos ->
      bitmap.compress(Bitmap.CompressFormat.JPEG, 92, fos)
      fos.flush()
    }
    return Uri.fromFile(outFile).toString()
  }

  fun deleteManagedAlbumCover(context: Context, uriString: String?) {
    if (uriString.isNullOrBlank()) return
    runCatching {
      val parsed = Uri.parse(uriString)
      if (parsed.scheme == "file") {
        val file = parsed.path?.let { File(it) }
        val albumCoverDir = File(context.filesDir, "album_covers").canonicalPath
        if (file != null && file.exists() && file.canonicalPath.startsWith(albumCoverDir)) {
          file.delete()
        }
      }
    }
  }

  suspend fun loadBitmapForUi(
    context: Context,
    artworkSource: String,
    targetSize: Int = 512
  ): Bitmap? = withContext(Dispatchers.IO) {
    if (artworkSource.isBlank()) return@withContext null
    if (artworkSource == APP_ICON_ARTWORK) {
      val drawable = ContextCompat.getDrawable(context, R.drawable.ic_launcher_foreground)
      return@withContext drawable?.toBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888)
    }

    val uri = Uri.parse(artworkSource)
    if (uri.scheme == "content" || uri.scheme == "file" || File(artworkSource).exists()) {
      val rawBytes = runCatching {
        if (File(artworkSource).exists()) {
          File(artworkSource).readBytes()
        } else {
          context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        }
      }.getOrNull()

      if (rawBytes != null && rawBytes.isNotEmpty()) {
        val decoded = decodeSampledBitmapWithExif(rawBytes, targetSize)
        if (decoded != null) return@withContext decoded
      }
    }

    runCatching {
      val request = ImageRequest.Builder(context)
        .data(artworkSource)
        .allowHardware(false)
        .size(targetSize)
        .build()
      val result = context.imageLoader.execute(request)
      (result as? SuccessResult)?.drawable?.toBitmap()?.copy(Bitmap.Config.ARGB_8888, false)
    }.getOrNull()
  }

  private fun decodeSampledBitmapWithExif(bytes: ByteArray, maxDimTarget: Int): Bitmap? {
    val orientation = runCatching {
      ByteArrayInputStream(bytes).use { stream ->
        ExifInterface(stream).getAttributeInt(
          ExifInterface.TAG_ORIENTATION,
          ExifInterface.ORIENTATION_NORMAL
        )
      }
    }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    val maxDim = max(bounds.outWidth, bounds.outHeight)
    var sampleSize = 1
    while (maxDim / sampleSize > maxDimTarget * 2) {
      sampleSize *= 2
    }
    val opts = BitmapFactory.Options().apply {
      inSampleSize = sampleSize
      inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts) ?: return null
    val degrees = when (orientation) {
      ExifInterface.ORIENTATION_ROTATE_90 -> 90f
      ExifInterface.ORIENTATION_ROTATE_180 -> 180f
      ExifInterface.ORIENTATION_ROTATE_270 -> 270f
      else -> 0f
    }
    if (degrees == 0f) return bitmap

    val matrix = Matrix().apply { postRotate(degrees) }
    val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    if (rotated !== bitmap && !bitmap.isRecycled) {
      bitmap.recycle()
    }
    return rotated
  }
}
