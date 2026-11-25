package com.example.widget

import android.graphics.Color
import org.json.JSONObject

enum class WidgetStyle(
  val id: String,
  val displayNameEn: String,
  val displayNameKm: String
) {
  GLASS("glass", "Glass", "Glass"),
  COLOR("color", "Color", "Color"),
  MONO("mono", "Mono", "Mono");

  fun next(): WidgetStyle = entries[(ordinal + 1) % entries.size]

  companion object {
    const val DEFAULT_ID = "glass"

    fun fromId(raw: String?): WidgetStyle {
      if (raw.isNullOrBlank()) return GLASS
      val cleaned = raw.trim().lowercase()
      return entries.firstOrNull { it.id == cleaned } ?: GLASS
    }
  }
}

enum class WidgetPhotoSource(val id: String) {
  ALBUM_ART("album_art"),
  CUSTOM("custom");

  companion object {
    fun fromId(raw: String?): WidgetPhotoSource =
      entries.firstOrNull { it.id.equals(raw, ignoreCase = true) } ?: ALBUM_ART
  }
}

enum class WidgetFrameStyle(val id: String) {
  NONE("none"),
  ROUNDED("rounded"),
  CIRCLE("circle"),
  POLAROID("polaroid"),
  NEON("neon"),
  GRADIENT_RING("gradient_ring"),
  FILM("film"),
  STAMP("stamp"),
  VINYL("vinyl");

  companion object {
    fun fromId(raw: String?): WidgetFrameStyle =
      entries.firstOrNull { it.id.equals(raw, ignoreCase = true) } ?: ROUNDED
  }
}

enum class WidgetFrameMotion(val id: String) {
  STILL("still"),
  FLOAT("float"),
  SPIN("spin"),
  PULSE("pulse");

  companion object {
    fun fromId(raw: String?): WidgetFrameMotion =
      entries.firstOrNull { it.id.equals(raw, ignoreCase = true) } ?: STILL
  }
}

enum class WidgetBgType(val id: String) {
  PHOTO("photo"),
  AURORA("aurora"),
  SUNSET("sunset"),
  OCEAN("ocean"),
  SOLID("solid");

  companion object {
    fun fromId(raw: String?): WidgetBgType =
      entries.firstOrNull { it.id.equals(raw, ignoreCase = true) } ?: PHOTO
  }
}

enum class WidgetSizeTier(val id: String, val widthPx: Int, val heightPx: Int) {
  SMALL("small", 320, 320),
  WIDE("wide", 560, 280),
  LARGE("large", 520, 380);

  companion object {
    fun fromDimensionsDp(minWidthDp: Int, minHeightDp: Int): WidgetSizeTier {
      return when {
        minWidthDp in 1..205 -> SMALL
        minHeightDp >= 220 -> LARGE
        else -> WIDE
      }
    }
  }
}

data class WidgetCustomizerConfig(
  val samePhoto: Boolean = DEFAULT_SAME_PHOTO,
  val frameSource: WidgetPhotoSource = DEFAULT_FRAME_SOURCE,
  val bgSource: WidgetPhotoSource = DEFAULT_BG_SOURCE,
  val frameStyle: WidgetFrameStyle = DEFAULT_FRAME_STYLE,
  val frameColorHex: String = DEFAULT_FRAME_COLOR_HEX,
  val cornerRadius: Int = DEFAULT_CORNER,
  val borderWidth: Int = DEFAULT_BORDER,
  val motion: WidgetFrameMotion = DEFAULT_MOTION,
  val bgType: WidgetBgType = DEFAULT_BG_TYPE,
  val bgColorHex: String = DEFAULT_BG_COLOR_HEX,
  val blur: Int = DEFAULT_BLUR,
  val darkness: Int = DEFAULT_DARKNESS,
  val photoRevision: Long = 0L
) {
  fun normalized(): WidgetCustomizerConfig = copy(
    frameColorHex = normalizeHex(frameColorHex, DEFAULT_FRAME_COLOR_HEX),
    cornerRadius = cornerRadius.coerceIn(0, 50),
    borderWidth = borderWidth.coerceIn(0, 10),
    bgColorHex = normalizeHex(bgColorHex, DEFAULT_BG_COLOR_HEX),
    blur = blur.coerceIn(0, 60),
    darkness = darkness.coerceIn(0, 100)
  )

  fun contentEqualsIgnoringRevision(other: WidgetCustomizerConfig): Boolean {
    return samePhoto == other.samePhoto &&
      frameSource == other.frameSource &&
      bgSource == other.bgSource &&
      frameStyle == other.frameStyle &&
      frameColorHex.equals(other.frameColorHex, ignoreCase = true) &&
      cornerRadius == other.cornerRadius &&
      borderWidth == other.borderWidth &&
      motion == other.motion &&
      bgType == other.bgType &&
      bgColorHex.equals(other.bgColorHex, ignoreCase = true) &&
      blur == other.blur &&
      darkness == other.darkness
  }

  fun toJsonString(): String {
    val json = JSONObject().apply {
      put("same_photo", samePhoto)
      put("frame_source", frameSource.id)
      put("bg_source", bgSource.id)
      put("frame_style", frameStyle.id)
      put("frame_color", frameColorHex)
      put("corner", cornerRadius)
      put("border", borderWidth)
      put("motion", motion.id)
      put("bg_type", bgType.id)
      put("bg_color", bgColorHex)
      put("blur", blur)
      put("darkness", darkness)
      put("photo_revision", photoRevision)
    }
    return json.toString(2)
  }

  fun cacheKey(): String =
    "$samePhoto|${frameSource.id}|${bgSource.id}|${frameStyle.id}|$frameColorHex|" +
      "$cornerRadius|$borderWidth|${bgType.id}|$bgColorHex|$blur|$darkness|$photoRevision"

  companion object {
    const val DEFAULT_SAME_PHOTO = true
    val DEFAULT_FRAME_SOURCE = WidgetPhotoSource.ALBUM_ART
    val DEFAULT_BG_SOURCE = WidgetPhotoSource.ALBUM_ART
    val DEFAULT_FRAME_STYLE = WidgetFrameStyle.ROUNDED
    const val DEFAULT_FRAME_COLOR_HEX = "#43C7F4"
    const val DEFAULT_CORNER = 18
    const val DEFAULT_BORDER = 3
    val DEFAULT_MOTION = WidgetFrameMotion.STILL
    val DEFAULT_BG_TYPE = WidgetBgType.PHOTO
    const val DEFAULT_BG_COLOR_HEX = "#151F30"
    const val DEFAULT_BLUR = 30
    const val DEFAULT_DARKNESS = 100

    val FRAME_ACCENT_COLORS = listOf(
      "#43C7F4", // Cyan (default)
      "#7965FF", // Purple
      "#EC4899", // Pink
      "#EF4444", // Coral Red
      "#F59E0B", // Amber Gold
      "#22C55E"  // Mint Green
    )

    val SOLID_BG_COLORS = listOf(
      "#151F30", // Deep Navy (default)
      "#080C14", // AMOLED Obsidian
      "#1B1438", // Midnight Plum
      "#0F2933", // Abyssal Teal
      "#281822", // Velvet Rose
      "#1D263B"  // Slate Indigo
    )

    val DEFAULT = WidgetCustomizerConfig()

    fun normalizeHex(raw: String?, fallback: String): String {
      if (raw.isNullOrBlank()) return fallback
      val cleaned = if (raw.startsWith("#")) raw.trim() else "#${raw.trim()}"
      return runCatching {
        Color.parseColor(cleaned)
        cleaned.uppercase()
      }.getOrDefault(fallback)
    }

    fun parseColorInt(hex: String, fallback: Int): Int {
      return runCatching { Color.parseColor(hex) }.getOrDefault(fallback)
    }
  }
}
