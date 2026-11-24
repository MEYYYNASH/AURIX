package com.example.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

sealed class PhotoOperationResult {
  data class Success(val file: File) : PhotoOperationResult()
  data class Error(val isOutOfMemory: Boolean, val message: String) : PhotoOperationResult()
}

/**
 * Manages private offline widget photos inside filesDir/widget/:
 * - frame.jpg (max 1024 px)
 * - bg_original.jpg (original uncropped background photo, preserved for re-cropping)
 * - bg_cropped.jpg (800x400 JPEG quality 90)
 *
 * Photo Picker content:// URIs are never persisted since temporary URI grants can expire.
 */
object WidgetPhotoStorage {
  private const val WIDGET_DIR = "widget"
  private const val FILE_FRAME = "frame.jpg"
  private const val FILE_BG_ORIGINAL = "bg_original.jpg"
  private const val FILE_BG_CROPPED = "bg_cropped.jpg"

  const val MAX_FRAME_DIM_PX = 1024
  const val MAX_BG_ORIGINAL_DIM_PX = 1600
  const val CROP_OUTPUT_WIDTH = 800
  const val CROP_OUTPUT_HEIGHT = 400
  const val JPEG_QUALITY = 90

  fun widgetDir(context: Context): File =
    File(context.applicationContext.filesDir, WIDGET_DIR).apply { mkdirs() }

  fun frameFile(context: Context): File = File(widgetDir(context), FILE_FRAME)
  fun bgOriginalFile(context: Context): File = File(widgetDir(context), FILE_BG_ORIGINAL)
  fun bgCroppedFile(context: Context): File = File(widgetDir(context), FILE_BG_CROPPED)

  fun hasFramePhoto(context: Context): Boolean = frameFile(context).let { it.exists() && it.length() > 0L }
  fun hasBgOriginalPhoto(context: Context): Boolean = bgOriginalFile(context).let { it.exists() && it.length() > 0L }
  fun hasBgCroppedPhoto(context: Context): Boolean = bgCroppedFile(context).let { it.exists() && it.length() > 0L }

  suspend fun importFramePhoto(context: Context, uri: Uri): PhotoOperationResult =
    withContext(Dispatchers.IO) {
      importUriToFile(
        context = context.applicationContext,
        uri = uri,
        targetFile = frameFile(context),
        maxDimPx = MAX_FRAME_DIM_PX
      )
    }

  suspend fun importBackgroundPhoto(context: Context, uri: Uri): PhotoOperationResult =
    withContext(Dispatchers.IO) {
      val result = importUriToFile(
        context = context.applicationContext,
        uri = uri,
        targetFile = bgOriginalFile(context),
        maxDimPx = MAX_BG_ORIGINAL_DIM_PX
      )
      if (result is PhotoOperationResult.Success) {
        // Clear any stale crop belonging to the previous background photo
        runCatching { bgCroppedFile(context).delete() }
      }
      result
    }

  /**
   * Copies the picked URI ONCE into a private temp file (prevents multi-open failures on
   * Samsung Gallery / cloud Photo Picker URIs), then decodes using ImageDecoder on API 28+
   * (native HEIC/HEIF/AVIF/WebP/JPEG/PNG + automatic EXIF rotation + OOM-safe target sizing)
   * with a local-file BitmapFactory + ExifInterface fallback.
   */
  private fun importUriToFile(
    context: Context,
    uri: Uri,
    targetFile: File,
    maxDimPx: Int
  ): PhotoOperationResult {
    val stagingFile = File(context.cacheDir, "widget_pick_${System.nanoTime()}.tmp")
    try {
      val copied = copyUriToTempFile(context, uri, stagingFile)
      if (!copied || !stagingFile.exists() || stagingFile.length() == 0L) {
        return PhotoOperationResult.Error(false, "Unable to open selected photo.")
      }

      var attempts = 0
      var activeMaxDim = maxDimPx
      while (attempts < 2) {
        attempts++
        try {
          val decodedBmp = decodeStagingFile(stagingFile, activeMaxDim)
            ?: return PhotoOperationResult.Error(false, "Selected file is not a supported image.")

          val finalBmp = scaleDownIfNeeded(decodedBmp, activeMaxDim)
          if (finalBmp !== decodedBmp) decodedBmp.recycle()

          val parent = targetFile.parentFile
          if (parent != null && !parent.exists()) parent.mkdirs()

          val tempOut = File(parent ?: context.filesDir, "${targetFile.name}.tmp")
          FileOutputStream(tempOut).use { out ->
            val ok = finalBmp.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            out.flush()
            if (!ok) {
              finalBmp.recycle()
              tempOut.delete()
              return PhotoOperationResult.Error(false, "Could not save photo to device storage.")
            }
          }
          finalBmp.recycle()

          if (targetFile.exists()) targetFile.delete()
          if (!tempOut.renameTo(targetFile)) {
            tempOut.copyTo(targetFile, overwrite = true)
            tempOut.delete()
          }
          return PhotoOperationResult.Success(targetFile)
        } catch (oom: OutOfMemoryError) {
          System.gc()
          activeMaxDim = (activeMaxDim / 2).coerceAtLeast(512)
          if (attempts >= 2) {
            return PhotoOperationResult.Error(
              isOutOfMemory = true,
              message = "This photo is too large for available memory. Try a smaller image."
            )
          }
        } catch (t: Throwable) {
          return PhotoOperationResult.Error(
            isOutOfMemory = false,
            message = t.message ?: "Failed to import photo."
          )
        }
      }
      return PhotoOperationResult.Error(true, "Unable to process photo due to low memory.")
    } finally {
      runCatching { stagingFile.delete() }
    }
  }

  private fun copyUriToTempFile(context: Context, uri: Uri, dest: File): Boolean {
    val resolver = context.contentResolver
    val stream = runCatching { resolver.openInputStream(uri) }.getOrNull()
      ?: runCatching { resolver.openAssetFileDescriptor(uri, "r")?.createInputStream() }.getOrNull()
      ?: return false
    return runCatching {
      stream.use { input ->
        FileOutputStream(dest).use { output ->
          input.copyTo(output, bufferSize = 32 * 1024)
          output.flush()
        }
      }
      true
    }.getOrDefault(false)
  }

  private fun decodeStagingFile(file: File, maxDimPx: Int): Bitmap? {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
      val imageDecoderResult = runCatching {
        val source = ImageDecoder.createSource(file)
        ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
          decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
          decoder.isMutableRequired = false
          val srcW = info.size.width.coerceAtLeast(1)
          val srcH = info.size.height.coerceAtLeast(1)
          val maxSide = max(srcW, srcH)
          if (maxSide > maxDimPx) {
            val scale = maxDimPx.toFloat() / maxSide.toFloat()
            val dstW = (srcW * scale).roundToInt().coerceAtLeast(1)
            val dstH = (srcH * scale).roundToInt().coerceAtLeast(1)
            decoder.setTargetSize(dstW, dstH)
          }
        }
      }.getOrNull()
      if (imageDecoderResult != null) return imageDecoderResult
    }

    // Fallback for API 24-27 or non-ImageDecoder formats
    val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, boundsOpts)
    val srcW = boundsOpts.outWidth
    val srcH = boundsOpts.outHeight
    if (srcW <= 0 || srcH <= 0) return null

    val decodeOpts = BitmapFactory.Options().apply {
      inSampleSize = computeInSampleSize(srcW, srcH, maxDimPx)
      inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    val decoded = BitmapFactory.decodeFile(file.absolutePath, decodeOpts) ?: return null
    val rotation = readExifRotationDegreesFromFile(file)
    return if (rotation != 0) {
      val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
      val rotated = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
      if (rotated !== decoded) decoded.recycle()
      rotated
    } else {
      decoded
    }
  }

  private fun readExifRotationDegreesFromFile(file: File): Int {
    return runCatching {
      val exif = ExifInterface(file.absolutePath)
      when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90
        ExifInterface.ORIENTATION_ROTATE_180 -> 180
        ExifInterface.ORIENTATION_ROTATE_270 -> 270
        else -> 0
      }
    }.getOrDefault(0)
  }

  /**
   * Swaps frame.jpg and bg_original.jpg on disk and clears stale crop when photos exchange.
   */
  suspend fun swapCustomPhotos(context: Context): Boolean = withContext(Dispatchers.IO) {
    runCatching {
      val dir = widgetDir(context)
      val frame = frameFile(context)
      val bgOrig = bgOriginalFile(context)
      val bgCrop = bgCroppedFile(context)
      val temp = File(dir, "swap_temp.jpg")

      val hasF = frame.exists() && frame.length() > 0L
      val hasB = bgOrig.exists() && bgOrig.length() > 0L

      when {
        hasF && hasB -> {
          frame.copyTo(temp, overwrite = true)
          bgOrig.copyTo(frame, overwrite = true)
          temp.copyTo(bgOrig, overwrite = true)
          temp.delete()
          bgCrop.delete()
        }
        hasF && !hasB -> {
          frame.copyTo(bgOrig, overwrite = true)
          frame.delete()
          bgCrop.delete()
        }
        !hasF && hasB -> {
          bgOrig.copyTo(frame, overwrite = true)
          bgOrig.delete()
          bgCrop.delete()
        }
      }
      true
    }.getOrDefault(false)
  }

  /**
   * Renders an 800x400 JPEG (quality 90) crop from the original background photo
   * (or seeds bg_original.jpg from frame.jpg / fallbackBitmap if not yet created so
   * the original is always preserved and frame.jpg is never modified).
   *
   * @param normalizedOffsetX pan offset in [-1f, 1f] along available overflow width
   * @param normalizedOffsetY pan offset in [-1f, 1f] along available overflow height
   * @param zoom zoom factor in [1f, 3f]
   */
  suspend fun applyBackgroundCrop(
    context: Context,
    normalizedOffsetX: Float,
    normalizedOffsetY: Float,
    zoom: Float,
    fallbackBitmap: Bitmap? = null
  ): PhotoOperationResult = withContext(Dispatchers.IO) {
    try {
      val bgOrig = bgOriginalFile(context)
      if (!bgOrig.exists() || bgOrig.length() == 0L) {
        val frame = frameFile(context)
        if (frame.exists() && frame.length() > 0L) {
          frame.copyTo(bgOrig, overwrite = true)
        } else if (fallbackBitmap != null && !fallbackBitmap.isRecycled) {
          FileOutputStream(bgOrig).use { out ->
            fallbackBitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
          }
        } else {
          return@withContext PhotoOperationResult.Error(false, "No background photo available to crop.")
        }
      }

      val source = decodeSampledFile(bgOrig, MAX_BG_ORIGINAL_DIM_PX)
        ?: return@withContext PhotoOperationResult.Error(false, "Could not decode background photo.")

      val safeZoom = zoom.coerceIn(1f, 3f)
      val outW = CROP_OUTPUT_WIDTH.toFloat()
      val outH = CROP_OUTPUT_HEIGHT.toFloat()

      val baseScale = max(outW / source.width.toFloat(), outH / source.height.toFloat())
      val finalScale = baseScale * safeZoom
      val drawW = source.width * finalScale
      val drawH = source.height * finalScale

      val maxOffsetX = ((drawW - outW) * 0.5f).coerceAtLeast(0f)
      val maxOffsetY = ((drawH - outH) * 0.5f).coerceAtLeast(0f)

      val clampedNormX = normalizedOffsetX.coerceIn(-1f, 1f)
      val clampedNormY = normalizedOffsetY.coerceIn(-1f, 1f)

      val left = (outW - drawW) * 0.5f + clampedNormX * maxOffsetX
      val top = (outH - drawH) * 0.5f + clampedNormY * maxOffsetY

      val output = Bitmap.createBitmap(CROP_OUTPUT_WIDTH, CROP_OUTPUT_HEIGHT, Bitmap.Config.ARGB_8888)
      val canvas = Canvas(output)
      val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
      canvas.drawBitmap(source, null, RectF(left, top, left + drawW, top + drawH), paint)
      source.recycle()

      val croppedFile = bgCroppedFile(context)
      val tempFile = File(croppedFile.parentFile, "${croppedFile.name}.tmp")
      FileOutputStream(tempFile).use { out ->
        output.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
        out.flush()
      }
      output.recycle()

      if (croppedFile.exists()) croppedFile.delete()
      if (!tempFile.renameTo(croppedFile)) {
        tempFile.copyTo(croppedFile, overwrite = true)
        tempFile.delete()
      }
      PhotoOperationResult.Success(croppedFile)
    } catch (oom: OutOfMemoryError) {
      System.gc()
      PhotoOperationResult.Error(
        isOutOfMemory = true,
        message = "Not enough memory to crop this photo. Try a smaller image."
      )
    } catch (t: Throwable) {
      PhotoOperationResult.Error(false, t.message ?: "Crop failed.")
    }
  }

  suspend fun resetBackgroundCrop(context: Context): Boolean = withContext(Dispatchers.IO) {
    runCatching {
      val f = bgCroppedFile(context)
      if (f.exists()) f.delete() else true
    }.getOrDefault(false)
  }

  fun decodeSampledFile(file: File, maxDimPx: Int): Bitmap? {
    if (!file.exists() || file.length() == 0L) return null
    return try {
      val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
      BitmapFactory.decodeFile(file.absolutePath, bounds)
      if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
      val opts = BitmapFactory.Options().apply {
        inSampleSize = computeInSampleSize(bounds.outWidth, bounds.outHeight, maxDimPx)
        inPreferredConfig = Bitmap.Config.ARGB_8888
      }
      BitmapFactory.decodeFile(file.absolutePath, opts)
    } catch (_: OutOfMemoryError) {
      System.gc()
      runCatching {
        val fallbackOpts = BitmapFactory.Options().apply {
          inSampleSize = 4
          inPreferredConfig = Bitmap.Config.RGB_565
        }
        BitmapFactory.decodeFile(file.absolutePath, fallbackOpts)
      }.getOrNull()
    } catch (_: Throwable) {
      null
    }
  }

  fun computeInSampleSize(width: Int, height: Int, reqMaxDim: Int): Int {
    if (width <= 0 || height <= 0 || reqMaxDim <= 0) return 1
    var inSampleSize = 1
    val maxSide = max(width, height)
    while (maxSide / (inSampleSize * 2) >= reqMaxDim) {
      inSampleSize *= 2
    }
    return inSampleSize.coerceAtLeast(1)
  }

  private fun scaleDownIfNeeded(src: Bitmap, maxDimPx: Int): Bitmap {
    val maxSide = max(src.width, src.height)
    if (maxSide <= maxDimPx) return src
    val ratio = maxDimPx.toFloat() / maxSide.toFloat()
    val dstW = (src.width * ratio).roundToInt().coerceAtLeast(1)
    val dstH = (src.height * ratio).roundToInt().coerceAtLeast(1)
    return Bitmap.createScaledBitmap(src, dstW, dstH, true)
  }

  private fun readExifRotationDegrees(context: Context, uri: Uri): Int {
    return runCatching {
      context.contentResolver.openInputStream(uri)?.use { stream ->
        val exif = ExifInterface(stream)
        when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
          ExifInterface.ORIENTATION_ROTATE_90 -> 90
          ExifInterface.ORIENTATION_ROTATE_180 -> 180
          ExifInterface.ORIENTATION_ROTATE_270 -> 270
          else -> 0
        }
      } ?: 0
    }.getOrDefault(0)
  }
}
