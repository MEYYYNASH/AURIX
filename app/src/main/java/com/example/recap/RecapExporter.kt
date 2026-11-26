package com.example.recap

import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.FileProvider
import com.example.R
import com.example.ui.theme.KhmerTypography
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Exports AURIX Listening Recap cards as crisp 1080x1920 PNG bitmaps, saves to Photos
 * (MediaStore Pictures/AURIX on Android 10+, legacy Pictures/AURIX on Android 9-), and
 * shares single or all 6 cards via FileProvider without blocking the main thread.
 */
object RecapExporter {

  const val EXPORT_WIDTH = 1080
  const val EXPORT_HEIGHT = 1920
  private const val CACHE_DIR_NAME = "recap_exports"

  /**
   * Captures the currently displayed card via Compose [GraphicsLayer.toImageBitmap] and scales
   * cleanly to 1080x1920 on [Dispatchers.Default], falling back to the deterministic 1080x1920
   * offscreen renderer if capture is unavailable.
   */
  suspend fun captureOrRenderCard(
    context: Context,
    data: RecapData,
    cardIndex: Int,
    graphicsLayer: GraphicsLayer?
  ): Bitmap {
    val captured = if (graphicsLayer != null) {
      runCatching {
        graphicsLayer.toImageBitmap().asAndroidBitmap()
      }.getOrNull()
    } else {
      null
    }

    return withContext(Dispatchers.Default) {
      if (captured != null && captured.width > 0 && captured.height > 0) {
        val scaled = Bitmap.createScaledBitmap(captured, EXPORT_WIDTH, EXPORT_HEIGHT, true)
        if (scaled !== captured) {
          captured.recycle()
        }
        scaled
      } else {
        renderCardBitmap1080x1920(context = context, data = data, cardIndex = cardIndex)
      }
    }
  }

  /**
   * Saves a single recap card PNG to the device Photos gallery (Pictures/AURIX).
   * Returns true on success.
   */
  suspend fun saveCardToPhotos(
    context: Context,
    data: RecapData,
    cardIndex: Int,
    graphicsLayer: GraphicsLayer?
  ): Boolean {
    val bitmap = captureOrRenderCard(context, data, cardIndex, graphicsLayer)
    return withContext(Dispatchers.IO) {
      try {
        val fileName = "AURIX_Recap_${data.periodKey}_card${cardIndex + 1}_${System.currentTimeMillis()}.png"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
          val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/AURIX")
            put(MediaStore.Images.Media.IS_PENDING, 1)
          }
          val resolver = context.contentResolver
          val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: return@withContext false
          val wrote = resolver.openOutputStream(uri)?.use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
          } ?: false
          values.clear()
          values.put(MediaStore.Images.Media.IS_PENDING, 0)
          resolver.update(uri, values, null, null)
          wrote
        } else {
          @Suppress("DEPRECATION")
          val picturesDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            "AURIX"
          )
          if (!picturesDir.exists()) picturesDir.mkdirs()
          val outFile = File(picturesDir, fileName)
          FileOutputStream(outFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
          }
          MediaScannerConnection.scanFile(
            context,
            arrayOf(outFile.absolutePath),
            arrayOf("image/png"),
            null
          )
          true
        }
      } catch (_: Throwable) {
        false
      } finally {
        runCatching { bitmap.recycle() }
      }
    }
  }

  /**
   * Shares the current recap card via FileProvider and Intent.ACTION_SEND.
   */
  suspend fun shareSingleCard(
    context: Context,
    data: RecapData,
    cardIndex: Int,
    graphicsLayer: GraphicsLayer?
  ): Boolean {
    val bitmap = captureOrRenderCard(context, data, cardIndex, graphicsLayer)
    val uri = withContext(Dispatchers.IO) {
      try {
        writePngToCacheAndGetUri(context, bitmap, "aurix_recap_${data.periodKey}_card_${cardIndex + 1}.png")
      } finally {
        runCatching { bitmap.recycle() }
      }
    } ?: return false

    return withContext(Dispatchers.Main) {
      runCatching {
        val caption = context.getString(R.string.recap_share_caption)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
          type = "image/png"
          putExtra(Intent.EXTRA_STREAM, uri)
          putExtra(Intent.EXTRA_TEXT, caption)
          clipData = ClipData.newRawUri("AURIX Recap", uri)
          addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(shareIntent, context.getString(R.string.recap_share_chooser_title))
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
        true
      }.getOrDefault(false)
    }
  }

  /**
   * Renders all 6 story cards at 1080x1920 off the main thread and shares them via ACTION_SEND_MULTIPLE.
   */
  suspend fun shareAllSixCards(
    context: Context,
    data: RecapData
  ): Boolean {
    val uris = withContext(Dispatchers.Default) {
      val list = ArrayList<Uri>(RECAP_CARD_COUNT)
      for (idx in 0 until RECAP_CARD_COUNT) {
        val bmp = renderCardBitmap1080x1920(context = context, data = data, cardIndex = idx)
        try {
          val uri = withContext(Dispatchers.IO) {
            writePngToCacheAndGetUri(context, bmp, "aurix_recap_${data.periodKey}_card_${idx + 1}.png")
          }
          if (uri != null) list.add(uri)
        } finally {
          runCatching { bmp.recycle() }
        }
      }
      list
    }
    if (uris.isEmpty()) return false

    return withContext(Dispatchers.Main) {
      runCatching {
        val caption = context.getString(R.string.recap_share_caption)
        val shareIntent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
          type = "image/png"
          putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
          putExtra(Intent.EXTRA_TEXT, caption)
          val clip = ClipData.newRawUri("AURIX Recap", uris.first())
          for (i in 1 until uris.size) {
            clip.addItem(ClipData.Item(uris[i]))
          }
          clipData = clip
          addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(shareIntent, context.getString(R.string.recap_share_chooser_title))
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
        true
      }.getOrDefault(false)
    }
  }

  private fun writePngToCacheAndGetUri(context: Context, bitmap: Bitmap, fileName: String): Uri? {
    return runCatching {
      val dir = File(context.cacheDir, CACHE_DIR_NAME)
      if (!dir.exists()) dir.mkdirs()
      val file = File(dir, fileName)
      FileOutputStream(file).use { out ->
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
      }
      FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }.getOrNull()
  }

  /**
   * Deterministic 1080x1920 (3x scale of 360x640) high-resolution Canvas renderer using
   * the exact same palette, glow blobs, 28dp scaled solid-translucent glass panels, Work Sans
   * ExtraBold + Noto Sans Khmer fonts, and Orbi artwork.
   */
  fun renderCardBitmap1080x1920(
    context: Context,
    data: RecapData,
    cardIndex: Int
  ): Bitmap {
    val w = EXPORT_WIDTH
    val h = EXPORT_HEIGHT
    val s = w / 360f // 3.0f
    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val palette = paletteForRecapCard(cardIndex, data.mood)

    val latinBold = KhmerTypography.loadTypeface(context, R.font.work_sans, Typeface.BOLD)
    val latinRegular = KhmerTypography.loadTypeface(context, R.font.work_sans, Typeface.NORMAL)
    val khmerBold = KhmerTypography.loadTypeface(context, R.font.noto_sans_khmer, Typeface.BOLD)
    val khmerRegular = KhmerTypography.loadTypeface(context, R.font.noto_sans_khmer, Typeface.NORMAL)

    fun typefaceFor(text: String, bold: Boolean): Typeface {
      val kh = KhmerTypography.containsKhmer(text)
      return when {
        kh && bold -> khmerBold
        kh -> khmerRegular
        bold -> latinBold
        else -> latinRegular
      }
    }

    // 1. Dark base gradient + 2 glow blobs
    val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      shader = LinearGradient(
        0f, 0f, 0f, h.toFloat(),
        intArrayOf(
          0xFF080C14.toInt(),
          palette.topGlow.toArgb(),
          0xFF080C14.toInt(),
          palette.bottomGlow.toArgb(),
          0xFF080C14.toInt()
        ),
        floatArrayOf(0f, 0.25f, 0.52f, 0.78f, 1f),
        Shader.TileMode.CLAMP
      )
    }
    canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), bgPaint)

    fun drawBlob(centerFracX: Float, centerFracY: Float, color: Int, radiusFrac: Float) {
      val cx = w * centerFracX
      val cy = h * centerFracY
      val r = w * radiusFrac
      val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(
          cx, cy, r,
          intArrayOf(
            Color.argb(122, Color.red(color), Color.green(color), Color.blue(color)),
            Color.argb(46, Color.red(color), Color.green(color), Color.blue(color)),
            Color.TRANSPARENT
          ),
          floatArrayOf(0f, 0.5f, 1f),
          Shader.TileMode.CLAMP
        )
      }
      canvas.drawCircle(cx, cy, r, p)
    }

    drawBlob(palette.blob1CenterFraction.x, palette.blob1CenterFraction.y, palette.blob1.toArgb(), 0.72f)
    drawBlob(palette.blob2CenterFraction.x, palette.blob2CenterFraction.y, palette.blob2.toArgb(), 0.78f)

    // Subtle orbital rings
    val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      style = Paint.Style.STROKE
      color = Color.argb(12, 255, 255, 255)
      strokeWidth = 1.2f * s
    }
    canvas.drawCircle(w * 0.5f, h * 0.44f, w * 0.56f, ringPaint)
    canvas.drawCircle(w * 0.5f, h * 0.44f, w * 0.82f, ringPaint)

    val padH = 24f * s
    val contentW = (w - padH * 2f).roundToInt()

    // 2. Top bar: AURIX · MUSIC DNA + period chip
    val headerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
      color = Color.argb(220, 255, 255, 255)
      textSize = 11f * s
      typeface = latinBold
      letterSpacing = 0.12f
    }
    canvas.drawText(context.getString(R.string.recap_brand_header), padH, 42f * s, headerPaint)

    val chipText = data.label
    val chipTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
      color = Color.argb(240, 255, 255, 255)
      textSize = 10.5f * s
      typeface = typefaceFor(chipText, true)
    }
    val chipW = chipTextPaint.measureText(chipText) + 22f * s
    val chipH = 24f * s
    val chipRect = RectF(w - padH - chipW, 24f * s, w - padH, 24f * s + chipH)
    val chipFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(32, 255, 255, 255) }
    val chipBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      style = Paint.Style.STROKE
      strokeWidth = 1f * s
      color = Color.argb(64, 255, 255, 255)
    }
    canvas.drawRoundRect(chipRect, chipH / 2f, chipH / 2f, chipFill)
    canvas.drawRoundRect(chipRect, chipH / 2f, chipH / 2f, chipBorder)
    canvas.drawText(chipText, chipRect.left + 11f * s, chipRect.centerY() + 4f * s, chipTextPaint)

    // Helper for wrapped text with Khmer-safe StaticLayout
    fun drawWrapped(
      text: String,
      x: Float,
      y: Float,
      maxWidth: Int,
      sizeSp: Float,
      color: Int,
      bold: Boolean = true,
      align: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL,
      maxLines: Int = 2
    ): Float {
      var fontSize = sizeSp * s
      var layout: StaticLayout
      while (true) {
        val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
          this.color = color
          this.textSize = fontSize
          this.typeface = typefaceFor(text, bold)
        }
        layout = StaticLayout.Builder.obtain(text, 0, text.length, tp, maxWidth.coerceAtLeast(1))
          .setAlignment(align)
          .setIncludePad(true)
          .setMaxLines(maxLines)
          .build()
        if (layout.lineCount <= maxLines || fontSize <= 18f * s) break
        fontSize *= 0.90f
      }
      canvas.save()
      canvas.translate(x, y)
      layout.draw(canvas)
      canvas.restore()
      return y + layout.height
    }

    // Helper for solid translucent liquid glass panel (28dp scaled radius)
    fun drawGlassPanel(top: Float, heightPx: Float): RectF {
      val rect = RectF(padH, top, w - padH, top + heightPx)
      val rad = 28f * s
      val baseFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(148, 14, 21, 37)
      }
      val sheen = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(
          rect.left, rect.top, rect.left, rect.bottom,
          intArrayOf(Color.argb(66, 255, 255, 255), Color.argb(26, 255, 255, 255)),
          null,
          Shader.TileMode.CLAMP
        )
      }
      val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * s
        shader = LinearGradient(
          rect.left, rect.top, rect.left, rect.bottom,
          intArrayOf(Color.argb(122, 255, 255, 255), Color.argb(36, 255, 255, 255)),
          null,
          Shader.TileMode.CLAMP
        )
      }
      canvas.drawRoundRect(rect, rad, rad, baseFill)
      canvas.drawRoundRect(rect, rad, rad, sheen)
      canvas.drawRoundRect(rect, rad, rad, border)
      // Inner top specular highlight
      val hi = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(
          rect.left + 24f * s, rect.top, rect.right - 24f * s, rect.top,
          intArrayOf(Color.TRANSPARENT, Color.argb(160, 255, 255, 255), Color.TRANSPARENT),
          null,
          Shader.TileMode.CLAMP
        )
        strokeWidth = 2f * s
      }
      canvas.drawLine(rect.left + 24f * s, rect.top + 1.5f * s, rect.right - 24f * s, rect.top + 1.5f * s, hi)
      return rect
    }

    fun drawOrbi(cx: Float, cy: Float, diameter: Float) {
      val bodyR = diameter * 0.30f
      val pColor = data.mood.primaryColorHex.toInt()
      val sColor = data.mood.secondaryColorHex.toInt()
      val halo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(
          cx, cy, diameter * 0.50f,
          intArrayOf(
            Color.argb(128, Color.red(pColor), Color.green(pColor), Color.blue(pColor)),
            Color.argb(56, Color.red(sColor), Color.green(sColor), Color.blue(sColor)),
            Color.TRANSPARENT
          ),
          null,
          Shader.TileMode.CLAMP
        )
      }
      canvas.drawCircle(cx, cy, diameter * 0.50f, halo)

      canvas.save()
      canvas.rotate(-18f, cx, cy)
      val rx = diameter * 0.44f
      val ry = diameter * 0.16f
      val orbitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3.2f * s
        strokeCap = Paint.Cap.ROUND
        shader = LinearGradient(
          cx - rx, cy, cx + rx, cy,
          intArrayOf(pColor, Color.WHITE, sColor),
          null,
          Shader.TileMode.CLAMP
        )
      }
      canvas.drawOval(RectF(cx - rx, cy - ry, cx + rx, cy + ry), orbitPaint)
      val starPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
      canvas.drawCircle(cx + rx * cos(0.45).toFloat(), cy + ry * sin(0.45).toFloat(), 3.8f * s, starPaint)
      canvas.restore()

      val sphere = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(
          cx - bodyR * 0.28f, cy - bodyR * 0.28f, bodyR * 1.45f,
          intArrayOf(Color.WHITE, pColor, sColor, 0xFF0B1226.toInt()),
          null,
          Shader.TileMode.CLAMP
        )
      }
      canvas.drawCircle(cx, cy, bodyR, sphere)

      val visorW = bodyR * 1.24f
      val visorH = bodyR * 0.72f
      val visorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(235, 10, 16, 32) }
      canvas.drawRoundRect(
        RectF(cx - visorW / 2f, cy - visorH / 2f + bodyR * 0.04f, cx + visorW / 2f, cy + visorH / 2f + bodyR * 0.04f),
        visorH * 0.5f, visorH * 0.5f, visorPaint
      )

      val eyePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF43C7F4.toInt()
        strokeWidth = 2.8f * s
        strokeCap = Paint.Cap.ROUND
      }
      val eyeOffsetX = visorW * 0.22f
      val eyeY = cy + bodyR * 0.04f
      canvas.drawRoundRect(
        RectF(cx - eyeOffsetX - bodyR * 0.08f, eyeY - bodyR * 0.12f, cx - eyeOffsetX + bodyR * 0.08f, eyeY + bodyR * 0.12f),
        bodyR * 0.08f, bodyR * 0.08f, eyePaint
      )
      canvas.drawRoundRect(
        RectF(cx + eyeOffsetX - bodyR * 0.08f, eyeY - bodyR * 0.12f, cx + eyeOffsetX + bodyR * 0.08f, eyeY + bodyR * 0.12f),
        bodyR * 0.08f, bodyR * 0.08f, eyePaint
      )
    }

    val cyan = 0xFF43C7F4.toInt()
    val indigo = 0xFF7965FF.toInt()
    val purple = 0xFFA855F7.toInt()

    // 3. Per-card body
    when (cardIndex.coerceIn(0, RECAP_CARD_COUNT - 1)) {
      0 -> {
        drawWrapped(
          context.getString(R.string.recap_kicker_intro),
          padH, 128f * s, contentW, 11.5f, cyan, bold = true, align = Layout.Alignment.ALIGN_CENTER
        )
        drawOrbi(w * 0.5f, 240f * s, 156f * s)
        val title = if (data.period == RecapPeriod.YEARLY) {
          context.getString(R.string.recap_intro_title_year)
        } else {
          context.getString(R.string.recap_intro_title_month)
        }
        val afterTitle = drawWrapped(
          title, padH, 338f * s, contentW, 34f, Color.WHITE, bold = true, align = Layout.Alignment.ALIGN_CENTER, maxLines = 2
        )
        val panel = drawGlassPanel(afterTitle + 22f * s, 108f * s)
        val afterLabel = drawWrapped(
          data.label, panel.left + 18f * s, panel.top + 20f * s, (panel.width() - 36f * s).roundToInt(),
          20f, cyan, bold = true, align = Layout.Alignment.ALIGN_CENTER, maxLines = 1
        )
        drawWrapped(
          context.getString(R.string.recap_intro_subtitle),
          panel.left + 18f * s, afterLabel + 8f * s, (panel.width() - 36f * s).roundToInt(),
          13f, Color.argb(210, 255, 255, 255), bold = false, align = Layout.Alignment.ALIGN_CENTER, maxLines = 2
        )
      }
      1 -> {
        val lead = data.topArtists.firstOrNull()
        val y1 = drawWrapped(context.getString(R.string.recap_kicker_top_artist), padH, 112f * s, contentW, 11.5f, cyan)
        val y2 = drawWrapped(lead?.name ?: "AURIX", padH, y1 + 12f * s, contentW, 38f, Color.WHITE, maxLines = 2)
        val sub = when {
          lead == null -> context.getString(R.string.recap_empty_hint)
          lead.isTopOnePercent -> context.getString(R.string.recap_top_artist_top_one_percent, lead.plays)
          else -> context.getString(R.string.recap_top_artist_plays_only, lead.plays)
        }
        val y3 = drawWrapped(sub, padH, y2 + 10f * s, contentW, 15f, Color.argb(220, 255, 255, 255), bold = false, maxLines = 2)
        val panel = drawGlassPanel(y3 + 26f * s, 210f * s)
        var py = drawWrapped(
          context.getString(R.string.recap_top_artists_panel_title),
          panel.left + 18f * s, panel.top + 18f * s, (panel.width() - 36f * s).roundToInt(),
          12.5f, Color.argb(185, 255, 255, 255), maxLines = 1
        ) + 14f * s
        val maxPlays = (data.topArtists.maxOfOrNull { it.plays } ?: 1).coerceAtLeast(1)
        data.topArtists.take(3).forEachIndexed { idx, artist ->
          val rowText = "${idx + 1}. ${artist.name}  ·  ${context.getString(R.string.recap_plays_count, artist.plays)}"
          py = drawWrapped(rowText, panel.left + 18f * s, py, (panel.width() - 36f * s).roundToInt(), 13.5f, Color.WHITE, maxLines = 1) + 6f * s
          val barLeft = panel.left + 18f * s
          val barW = panel.width() - 36f * s
          val barH = 8f * s
          val trackP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(32, 255, 255, 255) }
          canvas.drawRoundRect(RectF(barLeft, py, barLeft + barW, py + barH), barH / 2f, barH / 2f, trackP)
          val fillW = barW * (artist.plays.toFloat() / maxPlays.toFloat()).coerceIn(0.14f, 1f)
          val fillP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
              barLeft, py, barLeft + fillW, py,
              intArrayOf(if (idx == 0) cyan else indigo, if (idx == 0) indigo else purple),
              null, Shader.TileMode.CLAMP
            )
          }
          canvas.drawRoundRect(RectF(barLeft, py, barLeft + fillW, py + barH), barH / 2f, barH / 2f, fillP)
          py += barH + 16f * s
        }
      }
      2 -> {
        val tr = data.topTrack
        val y1 = drawWrapped(context.getString(R.string.recap_kicker_top_track), padH, 128f * s, contentW, 11.5f, cyan)
        val y2 = drawWrapped(tr.title, padH, y1 + 12f * s, contentW, 36f, Color.WHITE, maxLines = 2)
        val y3 = drawWrapped(
          "${tr.artist}  ·  ${context.getString(R.string.recap_plays_count, tr.plays)}",
          padH, y2 + 10f * s, contentW, 15.5f, cyan, bold = true, maxLines = 1
        )
        val panel = drawGlassPanel(y3 + 28f * s, 116f * s)
        val colW = ((panel.width() - 36f * s) / 3f).roundToInt()
        val stats = listOf(
          context.getString(R.string.recap_stat_best_day) to tr.bestDay,
          context.getString(R.string.recap_stat_longest_streak) to context.getString(R.string.recap_streak_days, tr.longestStreak),
          context.getString(R.string.recap_stat_replays) to "${tr.replays}x"
        )
        stats.forEachIndexed { idx, (label, value) ->
          val cx = panel.left + 18f * s + idx * colW
          val ly = drawWrapped(label, cx, panel.top + 22f * s, colW - (6f * s).roundToInt(), 11f, Color.argb(180, 255, 255, 255), bold = false, maxLines = 1)
          drawWrapped(value, cx, ly + 8f * s, colW - (6f * s).roundToInt(), 16f, Color.WHITE, bold = true, maxLines = 1)
        }
      }
      3 -> {
        val hours = String.format(Locale.US, "%.1f", data.totalMinutes / 60f)
        val albums = (data.totalMinutes / 40f).roundToInt().coerceAtLeast(if (data.totalMinutes > 0) 1 else 0)
        val y1 = drawWrapped(context.getString(R.string.recap_kicker_minutes), padH, 122f * s, contentW, 11.5f, cyan)
        val y2 = drawWrapped(String.format(Locale.US, "%,d", data.totalMinutes), padH, y1 + 10f * s, contentW, 60f, Color.WHITE, maxLines = 1)
        val y3 = drawWrapped(context.getString(R.string.recap_minutes_unit), padH, y2 + 2f * s, contentW, 20f, cyan, maxLines = 1)
        val y4 = drawWrapped(
          context.getString(R.string.recap_minutes_comparison, hours, albums),
          padH, y3 + 10f * s, contentW, 14.5f, Color.argb(215, 255, 255, 255), bold = false, maxLines = 2
        )
        val panel = drawGlassPanel(y4 + 26f * s, 116f * s)
        val colW = ((panel.width() - 36f * s) / 3f).roundToInt()
        val stats = listOf(
          context.getString(R.string.recap_stat_daily_avg) to context.getString(R.string.recap_daily_avg_value, data.dailyAverage),
          context.getString(R.string.recap_stat_top_day) to data.topDay,
          context.getString(R.string.recap_stat_new_artists) to "${data.newArtists}"
        )
        stats.forEachIndexed { idx, (label, value) ->
          val cx = panel.left + 18f * s + idx * colW
          val ly = drawWrapped(label, cx, panel.top + 22f * s, colW - (6f * s).roundToInt(), 11f, Color.argb(180, 255, 255, 255), bold = false, maxLines = 1)
          drawWrapped(value, cx, ly + 8f * s, colW - (6f * s).roundToInt(), 16f, Color.WHITE, bold = true, maxLines = 1)
        }
      }
      4 -> {
        val p = data.personality
        val y1 = drawWrapped(context.getString(R.string.recap_kicker_personality), padH, 108f * s, contentW, 11.5f, cyan)
        val y2 = drawWrapped(context.getString(p.nameRes), padH, y1 + 10f * s, contentW, 36f, Color.WHITE, maxLines = 2)
        val y3 = drawWrapped(context.getString(p.descriptionRes), padH, y2 + 10f * s, contentW, 14.5f, Color.argb(215, 255, 255, 255), bold = false, maxLines = 3)
        val panel = drawGlassPanel(y3 + 22f * s, 198f * s)
        var py = panel.top + 20f * s
        p.traits.take(3).forEachIndexed { idx, trait ->
          val label = "${context.getString(trait.labelRes)}  ·  ${trait.percent}%"
          py = drawWrapped(label, panel.left + 18f * s, py, (panel.width() - 36f * s).roundToInt(), 13.5f, Color.WHITE, maxLines = 1) + 6f * s
          val barLeft = panel.left + 18f * s
          val barW = panel.width() - 36f * s
          val barH = 9f * s
          val trackP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(34, 255, 255, 255) }
          canvas.drawRoundRect(RectF(barLeft, py, barLeft + barW, py + barH), barH / 2f, barH / 2f, trackP)
          val fillW = barW * (trait.percent / 100f).coerceIn(0.08f, 1f)
          val fillP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
              barLeft, py, barLeft + fillW, py,
              intArrayOf(if (idx == 0) cyan else indigo, if (idx == 0) indigo else purple),
              null, Shader.TileMode.CLAMP
            )
          }
          canvas.drawRoundRect(RectF(barLeft, py, barLeft + fillW, py + barH), barH / 2f, barH / 2f, fillP)
          py += barH + 18f * s
        }
      }
      else -> {
        drawWrapped(
          context.getString(R.string.recap_kicker_orbi_mood),
          padH, 92f * s, contentW, 11.5f, cyan, align = Layout.Alignment.ALIGN_CENTER
        )
        drawOrbi(w * 0.5f, 194f * s, 142f * s)
        val moodTitle = "${data.mood.emoji} ${context.getString(data.mood.nameRes)}"
        val y2 = drawWrapped(moodTitle, padH, 274f * s, contentW, 34f, Color.WHITE, align = Layout.Alignment.ALIGN_CENTER, maxLines = 1)
        val chipStr = if (data.period == RecapPeriod.YEARLY) {
          context.getString(R.string.recap_mood_chip_year)
        } else {
          context.getString(R.string.recap_mood_chip_month)
        }
        val y3 = drawWrapped(chipStr, padH, y2 + 6f * s, contentW, 12.5f, cyan, align = Layout.Alignment.ALIGN_CENTER, maxLines = 1)
        val panel = drawGlassPanel(y3 + 18f * s, 168f * s)
        val rows = listOf(
          context.getString(R.string.recap_summary_artist) to (data.topArtists.firstOrNull()?.name ?: "—"),
          context.getString(R.string.recap_summary_track) to data.topTrack.title,
          context.getString(R.string.recap_summary_minutes) to "${String.format(Locale.US, "%,d", data.totalMinutes)} ${context.getString(R.string.recap_minutes_unit)}"
        )
        var py = panel.top + 20f * s
        rows.forEachIndexed { idx, (k, v) ->
          val rowStr = "$k:  $v"
          py = drawWrapped(rowStr, panel.left + 18f * s, py, (panel.width() - 36f * s).roundToInt(), 13.5f, Color.WHITE, maxLines = 1) + 14f * s
          if (idx < rows.lastIndex) {
            val div = Paint().apply { color = Color.argb(36, 255, 255, 255); strokeWidth = 1f * s }
            canvas.drawLine(panel.left + 18f * s, py - 6f * s, panel.right - 18f * s, py - 6f * s, div)
          }
        }
      }
    }

    // 4. Footer
    val footerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
      color = Color.argb(145, 255, 255, 255)
      textSize = 10f * s
      typeface = latinBold
      letterSpacing = 0.16f
      textAlign = Paint.Align.CENTER
    }
    canvas.drawText(context.getString(R.string.recap_footer_tagline), w * 0.5f, h - 28f * s, footerPaint)

    return bitmap
  }
}
