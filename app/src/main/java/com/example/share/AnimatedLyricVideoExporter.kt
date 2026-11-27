package com.example.share

import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.FileProvider
import com.example.lyrics.LyricsAnimationStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import kotlin.coroutines.coroutineContext
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

object AnimatedLyricVideoExporter {
  const val CLIP_DURATION_SECONDS = 10
  const val CLIP_DURATION_MS = 10_000L
  const val CLIP_FPS = 24
  const val TOTAL_FRAMES = CLIP_DURATION_SECONDS * CLIP_FPS // 240 frames
  const val VIDEO_WIDTH_PX = 720

  fun resolveVideoDimensions(aspect: ShareCardAspect): Pair<Int, Int> {
    val rawHeight = (VIDEO_WIDTH_PX / aspect.ratio).roundToInt()
    // H.264 encoders require even width and height (multiples of 16 preferred, multiples of 2 required)
    val evenWidth = (VIDEO_WIDTH_PX / 2) * 2
    val evenHeight = ((rawHeight + 1) / 2) * 2
    return evenWidth to evenHeight
  }

  fun computeVinylRotationDegrees(frameIndex: Int, totalFrames: Int = TOTAL_FRAMES): Float {
    if (totalFrames <= 0) return 0f
    // Two full 360-degree vinyl revolutions across the 10-second loop (720 degrees total)
    return ((frameIndex.toFloat() / totalFrames.toFloat()) * 720f) % 360f
  }

  fun computeMeshDriftPhaseRadians(frameIndex: Int, totalFrames: Int = TOTAL_FRAMES): Float {
    if (totalFrames <= 0) return 0f
    return (frameIndex.toFloat() / totalFrames.toFloat()) * (2.0 * PI).toFloat()
  }

  fun buildClipAnimFrameForTimestamp(
    data: ShareCardData,
    lyricsAnimationStyle: LyricsAnimationStyle,
    elapsedClipMs: Long,
    clipDurationMs: Long = CLIP_DURATION_MS
  ): ShareCardLyricsAnimFrame? {
    val lineCount = data.selectedLines.size.coerceIn(0, 5)
    if (data.mode != ShareCardMode.LYRICS || lineCount == 0) return null

    val safeDuration = clipDurationMs.coerceAtLeast(1000L)
    val perLineDurationMs = (safeDuration / lineCount).coerceAtLeast(500L)
    val clampedElapsed = elapsedClipMs.coerceIn(0L, safeDuration - 1L)
    val activeIdx = ((clampedElapsed / perLineDurationMs).toInt()).coerceIn(0, lineCount - 1)
    val prevIdx = if (activeIdx > 0) activeIdx - 1 else -1
    val lineElapsedMs = (clampedElapsed % perLineDurationMs).coerceAtLeast(0L)
    val caretVisible = ((clampedElapsed / 420L) % 2L) == 0L

    return ShareCardLyricsAnimFrame(
      style = lyricsAnimationStyle,
      activeLineIndex = activeIdx,
      previousLineIndex = prevIdx,
      elapsedMs = lineElapsedMs,
      lineDurationMs = perLineDurationMs,
      caretVisible = caretVisible
    )
  }

  fun renderAnimatedClipFrame(
    canvas: Canvas,
    widthPx: Int,
    heightPx: Int,
    data: ShareCardData,
    lyricsAnimationStyle: LyricsAnimationStyle,
    frameIndex: Int,
    totalFrames: Int = TOTAL_FRAMES
  ) {
    val elapsedMs = (frameIndex.toLong() * CLIP_DURATION_MS) / totalFrames.coerceAtLeast(1)
    val driftPhase = computeMeshDriftPhaseRadians(frameIndex, totalFrames)
    val animFrame = buildClipAnimFrameForTimestamp(
      data = data,
      lyricsAnimationStyle = lyricsAnimationStyle,
      elapsedClipMs = elapsedMs,
      clipDurationMs = CLIP_DURATION_MS
    )

    ShareCardRenderer.draw(
      canvas = canvas,
      widthPx = widthPx,
      heightPx = heightPx,
      data = data,
      driftPhaseRadians = driftPhase,
      lyricsAnimFrame = animFrame
    )

    // Draw live animated vinyl & waveform HUD badge in top-right corner of the video frame
    drawAnimatedVinylWaveformOverlay(
      canvas = canvas,
      widthPx = widthPx,
      heightPx = heightPx,
      data = data,
      frameIndex = frameIndex,
      totalFrames = totalFrames
    )
  }

  private fun drawAnimatedVinylWaveformOverlay(
    canvas: Canvas,
    widthPx: Int,
    heightPx: Int,
    data: ShareCardData,
    frameIndex: Int,
    totalFrames: Int
  ) {
    val scale = widthPx / 1080f
    val accentArgb = data.swatches.vibrant.toArgb()
    val progress = (frameIndex.toFloat() / totalFrames.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)
    val vinylAngle = computeVinylRotationDegrees(frameIndex, totalFrames)

    val pillWidth = 228f * scale
    val pillHeight = 68f * scale
    val pillRight = widthPx - 58f * scale
    val pillLeft = pillRight - pillWidth
    val pillTop = 58f * scale
    val pillBottom = pillTop + pillHeight
    val pillRect = RectF(pillLeft, pillTop, pillRight, pillBottom)

    val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = android.graphics.Color.argb(165, 10, 14, 24)
      style = Paint.Style.FILL
    }
    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = android.graphics.Color.argb(110, 255, 255, 255)
      style = Paint.Style.STROKE
      strokeWidth = 2f * scale
    }
    canvas.drawRoundRect(pillRect, pillHeight * 0.5f, pillHeight * 0.5f, bgPaint)
    canvas.drawRoundRect(pillRect, pillHeight * 0.5f, pillHeight * 0.5f, borderPaint)

    // Mini spinning vinyl disc inside left of badge
    val discRadius = 22f * scale
    val discCx = pillLeft + 34f * scale
    val discCy = pillRect.centerY()

    val save = canvas.save()
    canvas.rotate(vinylAngle, discCx, discCy)
    val discPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = android.graphics.Color.argb(245, 20, 23, 31)
      style = Paint.Style.FILL
    }
    canvas.drawCircle(discCx, discCy, discRadius, discPaint)

    val groovePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = android.graphics.Color.argb(90, 255, 255, 255)
      style = Paint.Style.STROKE
      strokeWidth = 1.6f * scale
    }
    canvas.drawArc(
      RectF(discCx - discRadius * 0.72f, discCy - discRadius * 0.72f, discCx + discRadius * 0.72f, discCy + discRadius * 0.72f),
      20f,
      110f,
      false,
      groovePaint
    )
    canvas.drawArc(
      RectF(discCx - discRadius * 0.72f, discCy - discRadius * 0.72f, discCx + discRadius * 0.72f, discCy + discRadius * 0.72f),
      200f,
      110f,
      false,
      groovePaint
    )

    val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = accentArgb
      style = Paint.Style.FILL
    }
    canvas.drawCircle(discCx, discCy, discRadius * 0.38f, labelPaint)
    val holePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = android.graphics.Color.BLACK
      style = Paint.Style.FILL
    }
    canvas.drawCircle(discCx, discCy, discRadius * 0.13f, holePaint)
    canvas.restoreToCount(save)

    // Progress ring around the mini vinyl
    val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = accentArgb
      style = Paint.Style.STROKE
      strokeWidth = 3f * scale
      strokeCap = Paint.Cap.ROUND
    }
    val arcRadius = discRadius + 3.5f * scale
    canvas.drawArc(
      RectF(discCx - arcRadius, discCy - arcRadius, discCx + arcRadius, discCy + arcRadius),
      -90f,
      360f * progress,
      false,
      arcPaint
    )

    // Animated 10-bar beat waveform on the right side of the badge
    val barCount = 10
    val barWidth = 6.2f * scale
    val barGap = 4.6f * scale
    val maxBarHeight = 32f * scale
    val minBarHeight = 8f * scale
    var barX = discCx + discRadius + 18f * scale
    val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = android.graphics.Color.WHITE
      style = Paint.Style.FILL
    }

    for (b in 0 until barCount) {
      val wave = 0.5f + 0.5f * sin(frameIndex * 0.34f + b * 0.75f)
      val barH = minBarHeight + (maxBarHeight - minBarHeight) * wave
      val top = discCy - barH * 0.5f
      val bottom = discCy + barH * 0.5f
      barPaint.color = if (b % 2 == 0) accentArgb else android.graphics.Color.argb(230, 255, 255, 255)
      canvas.drawRoundRect(
        RectF(barX, top, barX + barWidth, bottom),
        barWidth * 0.5f,
        barWidth * 0.5f,
        barPaint
      )
      barX += barWidth + barGap
    }
  }

  /**
   * Encodes a 10-second H.264 MP4 video clip using Android's native MediaCodec + MediaMuxer.
   */
  suspend fun renderAnimatedVideoFile(
    context: Context,
    data: ShareCardData,
    lyricsAnimationStyle: LyricsAnimationStyle = LyricsAnimationStyle.SLIDE_UP,
    onProgress: (Float) -> Unit = {}
  ): File = withContext(Dispatchers.Default) {
    val (widthPx, heightPx) = resolveVideoDimensions(data.aspect)
    val shareDir = File(context.cacheDir, "shared_lyrics_video").apply {
      if (!exists()) mkdirs()
      listFiles()?.forEach { old -> runCatching { old.delete() } }
    }
    val outFile = File(shareDir, "aurix_lyric_clip_${System.currentTimeMillis()}.mp4")

    val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, widthPx, heightPx).apply {
      setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
      setInteger(MediaFormat.KEY_BIT_RATE, 4_200_000)
      setInteger(MediaFormat.KEY_FRAME_RATE, CLIP_FPS)
      setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
    }

    val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
    var muxer: MediaMuxer? = null
    var inputSurface: android.view.Surface? = null
    var muxerStarted = false
    var trackIndex = -1

    try {
      encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
      val surface = encoder.createInputSurface()
      inputSurface = surface
      encoder.start()

      muxer = MediaMuxer(outFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
      val bufferInfo = MediaCodec.BufferInfo()

      fun drainEncoder(endOfStream: Boolean) {
        if (endOfStream) {
          encoder.signalEndOfInputStream()
        }
        while (true) {
          val outIdx = encoder.dequeueOutputBuffer(bufferInfo, 10_000L)
          if (outIdx == MediaCodec.INFO_TRY_AGAIN_LATER) {
            if (!endOfStream) break
          } else if (outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
            if (!muxerStarted) {
              trackIndex = muxer.addTrack(encoder.outputFormat)
              muxer.start()
              muxerStarted = true
            }
          } else if (outIdx >= 0) {
            val encodedData = encoder.getOutputBuffer(outIdx)
            if (encodedData != null) {
              if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                bufferInfo.size = 0
              }
              if (bufferInfo.size > 0 && muxerStarted) {
                encodedData.position(bufferInfo.offset)
                encodedData.limit(bufferInfo.offset + bufferInfo.size)
                muxer.writeSampleData(trackIndex, encodedData, bufferInfo)
              }
            }
            encoder.releaseOutputBuffer(outIdx, false)
            if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
              break
            }
          }
        }
      }

      for (frameIdx in 0 until TOTAL_FRAMES) {
        coroutineContext.ensureActive()
        val canvas = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
          runCatching { surface.lockHardwareCanvas() }.getOrElse { surface.lockCanvas(null) }
        } else {
          surface.lockCanvas(null)
        }
        try {
          renderAnimatedClipFrame(
            canvas = canvas,
            widthPx = widthPx,
            heightPx = heightPx,
            data = data,
            lyricsAnimationStyle = lyricsAnimationStyle,
            frameIndex = frameIdx,
            totalFrames = TOTAL_FRAMES
          )
        } finally {
          surface.unlockCanvasAndPost(canvas)
        }
        drainEncoder(endOfStream = false)
        onProgress(((frameIdx + 1).toFloat() / TOTAL_FRAMES.toFloat()).coerceIn(0f, 1f))
      }

      drainEncoder(endOfStream = true)
      outFile
    } finally {
      runCatching { inputSurface?.release() }
      runCatching { encoder.stop() }
      runCatching { encoder.release() }
      runCatching {
        if (muxerStarted) muxer?.stop()
        muxer?.release()
      }
    }
  }

  /**
   * Renders a 10-second animated MP4 lyric clip and opens Android's Share Sheet.
   */
  suspend fun shareAnimatedVideoClip(
    context: Context,
    data: ShareCardData,
    lyricsAnimationStyle: LyricsAnimationStyle = LyricsAnimationStyle.SLIDE_UP,
    onProgress: (Float) -> Unit = {}
  ): Result<Unit> = runCatching {
    val videoFile = renderAnimatedVideoFile(
      context = context,
      data = data,
      lyricsAnimationStyle = lyricsAnimationStyle,
      onProgress = onProgress
    )
    val uri = withContext(Dispatchers.IO) {
      FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        videoFile
      )
    }
    val extraText = if (data.mode == ShareCardMode.LYRICS && data.selectedLines.isNotEmpty()) {
      "\"${data.selectedLines.joinToString("\n")}\"\n— ${data.title} · ${data.artist} (${data.brandName})"
    } else {
      "${data.title} · ${data.artist} (${data.brandName})"
    }

    val sendIntent = Intent(Intent.ACTION_SEND).apply {
      type = "video/mp4"
      putExtra(Intent.EXTRA_STREAM, uri)
      putExtra(Intent.EXTRA_TEXT, extraText)
      clipData = ClipData.newRawUri(data.brandName, uri)
      addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val chooser = Intent.createChooser(sendIntent, "Share 10s lyric video clip").apply {
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching {
      context.startActivity(chooser)
    }
  }

  /**
   * Renders a 10-second animated MP4 lyric clip and saves it to the device Gallery via MediaStore.
   */
  suspend fun saveAnimatedVideoClipToGallery(
    context: Context,
    data: ShareCardData,
    lyricsAnimationStyle: LyricsAnimationStyle = LyricsAnimationStyle.SLIDE_UP,
    onProgress: (Float) -> Unit = {}
  ): Result<Uri> = runCatching {
    val videoFile = renderAnimatedVideoFile(
      context = context,
      data = data,
      lyricsAnimationStyle = lyricsAnimationStyle,
      onProgress = onProgress
    )
    withContext(Dispatchers.IO) {
      val filename = "AURIX_LyricClip_${System.currentTimeMillis()}.mp4"
      val resolver = context.contentResolver
      val values = ContentValues().apply {
        put(MediaStore.Video.Media.DISPLAY_NAME, filename)
        put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
          put(
            MediaStore.Video.Media.RELATIVE_PATH,
            Environment.DIRECTORY_MOVIES + "/AURIX MUSIC"
          )
          put(MediaStore.Video.Media.IS_PENDING, 1)
        }
      }
      val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
      } else {
        MediaStore.Video.Media.EXTERNAL_CONTENT_URI
      }
      val itemUri = resolver.insert(collection, values)
        ?: throw IllegalStateException("Failed to create MediaStore video entry")

      try {
        resolver.openOutputStream(itemUri)?.use { os ->
          FileInputStream(videoFile).use { fis ->
            fis.copyTo(os)
          }
          os.flush()
        } ?: throw IllegalStateException("Failed to open MediaStore video output stream")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
          val publish = ContentValues().apply {
            put(MediaStore.Video.Media.IS_PENDING, 0)
          }
          resolver.update(itemUri, publish, null, null)
        }
        itemUri
      } catch (e: Exception) {
        runCatching { resolver.delete(itemUri, null, null) }
        throw e
      }
    }
  }
}
