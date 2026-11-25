package com.example.ui.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.DisplayMetrics
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.res.ResourcesCompat
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Precision
import coil.size.Size as CoilSize
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Centralized high-DPI artwork & image loading pipeline for AURIX.
 *
 * Ensures visual assets render crisply on high-density displays by:
 * - Disabling Coil artwork downsampling ([CoilSize.ORIGINAL] + [Precision.EXACT]).
 * - Enforcing 32-bit [Bitmap.Config.ARGB_8888] without RGB_565 color quantization.
 * - Applying explicit high-density [BitmapFactory.Options] flags (`inScaled = false`,
 *   `inSampleSize = 1`, explicit `inDensity` / `inTargetDensity`).
 * - Exposing [ARTWORK_FILTER_QUALITY] (`FilterQuality.High`) for Compose image rendering.
 */
object AurixImagePipeline {
  const val MINIMUM_HIGH_DENSITY_DPI: Int = DisplayMetrics.DENSITY_XXXHIGH // 640 dpi
  const val MAX_SAFE_TEXTURE_DIMENSION_PX: Int = 4096
  val ARTWORK_FILTER_QUALITY: FilterQuality = FilterQuality.High

  fun resolveTargetDensityDpi(context: Context): Int {
    val displayDpi = runCatching { context.resources.displayMetrics.densityDpi }.getOrDefault(0)
    return maxOf(displayDpi, MINIMUM_HIGH_DENSITY_DPI)
  }

  fun createHighDensityBitmapOptions(
    context: Context,
    mutable: Boolean = false
  ): BitmapFactory.Options {
    val targetDpi = resolveTargetDensityDpi(context)
    val screenDpi = runCatching { context.resources.displayMetrics.densityDpi }
      .getOrDefault(DisplayMetrics.DENSITY_HIGH)
      .coerceAtLeast(DisplayMetrics.DENSITY_HIGH)
    return BitmapFactory.Options().apply {
      inScaled = false
      inSampleSize = 1
      inPreferredConfig = Bitmap.Config.ARGB_8888
      inDensity = targetDpi
      inTargetDensity = targetDpi
      inScreenDensity = screenDpi
      inMutable = mutable
    }
  }

  fun applyHighDensityMetadata(bitmap: Bitmap, context: Context): Bitmap {
    runCatching {
      bitmap.density = resolveTargetDensityDpi(context)
    }
    return bitmap
  }

  fun createCrispBitmapPaint(
    alpha: Int = 255,
    filterBitmap: Boolean = true
  ): Paint {
    var flags = Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG
    if (filterBitmap) {
      flags = flags or Paint.FILTER_BITMAP_FLAG
    }
    return Paint(flags).apply {
      this.alpha = alpha.coerceIn(0, 255)
      isFilterBitmap = filterBitmap
      isDither = true
    }
  }

  fun decodeHighDensityResourceDrawable(
    context: Context,
    @DrawableRes resId: Int
  ): Drawable? {
    val targetDpi = resolveTargetDensityDpi(context)
    return runCatching {
      ResourcesCompat.getDrawableForDensity(context.resources, resId, targetDpi, context.theme)
    }.getOrNull() ?: runCatching {
      ResourcesCompat.getDrawable(context.resources, resId, context.theme)
    }.getOrNull()
  }

  fun decodeHighDensityArtworkBytes(
    context: Context,
    bytes: ByteArray,
    guardGpuTextureLimit: Boolean = true
  ): Bitmap? {
    if (bytes.isEmpty()) return null
    val opts = createHighDensityBitmapOptions(context)
    val decoded = try {
      BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
    } catch (_: OutOfMemoryError) {
      System.gc()
      val recoveryOpts = createHighDensityBitmapOptions(context).apply {
        inSampleSize = 2
      }
      runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size, recoveryOpts) }.getOrNull()
    } ?: return null

    applyHighDensityMetadata(decoded, context)
    return if (guardGpuTextureLimit) {
      clampOnlyOversizedTexture(decoded, context)
    } else {
      decoded
    }
  }

  fun decodeHighDensityArtworkFile(
    context: Context,
    filePath: String,
    guardGpuTextureLimit: Boolean = true
  ): Bitmap? {
    if (filePath.isBlank()) return null
    val opts = createHighDensityBitmapOptions(context)
    val decoded = try {
      BitmapFactory.decodeFile(filePath, opts)
    } catch (_: OutOfMemoryError) {
      System.gc()
      val recoveryOpts = createHighDensityBitmapOptions(context).apply {
        inSampleSize = 2
      }
      runCatching { BitmapFactory.decodeFile(filePath, recoveryOpts) }.getOrNull()
    } ?: return null

    applyHighDensityMetadata(decoded, context)
    return if (guardGpuTextureLimit) {
      clampOnlyOversizedTexture(decoded, context)
    } else {
      decoded
    }
  }

  private fun clampOnlyOversizedTexture(bitmap: Bitmap, context: Context): Bitmap {
    val maxSide = max(bitmap.width, bitmap.height)
    if (maxSide <= MAX_SAFE_TEXTURE_DIMENSION_PX) return bitmap
    val ratio = MAX_SAFE_TEXTURE_DIMENSION_PX.toFloat() / maxSide.toFloat()
    val dstW = (bitmap.width * ratio).roundToInt().coerceAtLeast(1)
    val dstH = (bitmap.height * ratio).roundToInt().coerceAtLeast(1)
    val scaled = Bitmap.createBitmap(dstW, dstH, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(scaled)
    val matrix = Matrix().apply {
      setScale(dstW.toFloat() / bitmap.width.toFloat(), dstH.toFloat() / bitmap.height.toFloat())
    }
    canvas.drawBitmap(bitmap, matrix, createCrispBitmapPaint())
    if (scaled !== bitmap && !bitmap.isRecycled) {
      bitmap.recycle()
    }
    return applyHighDensityMetadata(scaled, context)
  }

  fun createImageLoader(context: Context): ImageLoader {
    val appCtx = context.applicationContext ?: context
    return ImageLoader.Builder(appCtx)
      .allowHardware(true)
      .allowRgb565(false)
      .precision(Precision.EXACT)
      .bitmapConfig(Bitmap.Config.ARGB_8888)
      .crossfade(false)
      .respectCacheHeaders(false)
      .memoryCache {
        MemoryCache.Builder(appCtx)
          .maxSizePercent(0.25)
          .build()
      }
      .diskCache {
        DiskCache.Builder()
          .directory(appCtx.cacheDir.resolve("aurix_artwork_cache"))
          .maxSizeBytes(128L * 1024L * 1024L)
          .build()
      }
      .build()
  }

  fun buildCrispArtworkRequest(
    context: Context,
    model: Any?,
    allowHardware: Boolean = true,
    crossfade: Boolean = false
  ): ImageRequest {
    val effectiveHardware = allowHardware && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
    return ImageRequest.Builder(context)
      .data(model)
      .size(CoilSize.ORIGINAL)
      .precision(Precision.EXACT)
      .allowHardware(effectiveHardware)
      .allowRgb565(false)
      .bitmapConfig(Bitmap.Config.ARGB_8888)
      .memoryCachePolicy(CachePolicy.ENABLED)
      .diskCachePolicy(CachePolicy.ENABLED)
      .crossfade(crossfade)
      .build()
  }

  fun buildColorSamplingRequest(
    context: Context,
    model: Any?,
    sampleSizePx: Int = 96
  ): ImageRequest {
    return ImageRequest.Builder(context)
      .data(model)
      .allowHardware(false)
      .bitmapConfig(Bitmap.Config.ARGB_8888)
      .size(sampleSizePx)
      .precision(Precision.INEXACT)
      .crossfade(false)
      .build()
  }
}

@Composable
fun rememberCrispArtworkRequest(
  model: Any?,
  crossfade: Boolean = false
): ImageRequest {
  val context = LocalContext.current
  return remember(context, model, crossfade) {
    AurixImagePipeline.buildCrispArtworkRequest(
      context = context,
      model = model,
      allowHardware = true,
      crossfade = crossfade
    )
  }
}
