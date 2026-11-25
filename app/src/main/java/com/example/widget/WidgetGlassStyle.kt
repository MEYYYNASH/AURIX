package com.example.widget

/**
 * 5 per-widget glass design styles saved independently per appWidgetId.
 */
enum class WidgetGlassStyle(
  val id: String,
  val styleNumber: Int,
  val displayNameEn: String,
  val displayNameKm: String,
  val subtitleEn: String,
  val subtitleKm: String
) {
  FROST_PILL(
    id = "frost_pill",
    styleNumber = 1,
    displayNameEn = "Frost Pill",
    displayNameKm = "ហ្វ្រស ភីល",
    subtitleEn = "Frosted glass & glowing pill controls",
    subtitleKm = "កញ្ចក់ព្រិល និងប៊ូតុងភ្លឺរលោង"
  ),
  ORB(
    id = "orb",
    styleNumber = 2,
    displayNameEn = "Orb",
    displayNameKm = "អរប",
    subtitleEn = "Rotating vinyl disc & bubble glass",
    subtitleKm = "ឌីសវិល និងប៊ូតុងមូលកញ្ចក់"
  ),
  SMOKED_NEON(
    id = "smoked_neon",
    styleNumber = 3,
    displayNameEn = "Smoked Neon",
    displayNameKm = "ណេអុងងងឹត",
    subtitleEn = "Smoked glass, purple rim & cyan glow",
    subtitleKm = "កញ្ចក់ងងឹត គែមស្វាយ និងពន្លឺស៊ីយ៉ាន"
  ),
  LIGHT_FROST(
    id = "light_frost",
    styleNumber = 4,
    displayNameEn = "Light Frost",
    displayNameKm = "ហ្វ្រសភ្លឺ",
    subtitleEn = "Bright saturated frost & waveform",
    subtitleKm = "កញ្ចក់ភ្លឺស្រទន់ និងរលកសំឡេង"
  ),
  POSTER_GLASS(
    id = "poster_glass",
    styleNumber = 5,
    displayNameEn = "Poster Glass",
    displayNameKm = "កញ្ចក់ផូស្ទ័រ",
    subtitleEn = "Full sharp cover & floating glass bar",
    subtitleKm = "រូបពេញច្បាស់ និងរបារកញ្ចក់អណ្តែត"
  );

  fun next(): WidgetGlassStyle = entries[(ordinal + 1) % entries.size]

  companion object {
    const val DEFAULT_ID = "frost_pill"

    fun fromId(raw: String?): WidgetGlassStyle {
      if (raw.isNullOrBlank()) return FROST_PILL
      return when (raw.trim().lowercase()) {
        "frost_pill", "glass", "style_1", "1" -> FROST_PILL
        "orb", "style_2", "2" -> ORB
        "smoked_neon", "mono", "style_3", "3" -> SMOKED_NEON
        "light_frost", "color", "style_4", "4" -> LIGHT_FROST
        "poster_glass", "style_5", "5" -> POSTER_GLASS
        else -> FROST_PILL
      }
    }
  }
}

enum class WidgetKind(
  val id: String,
  val labelEn: String,
  val labelKm: String
) {
  MUSIC("music", "AURIX Music", "AURIX តន្ត្រី"),
  LYRICS("lyrics", "AURIX Lyrics", "AURIX ទំនុកច្រៀង"),
  COVER("cover", "AURIX Cover", "AURIX គម្របបទ"),
  MINI("mini", "AURIX Mini", "AURIX ខ្នាតតូច");

  companion object {
    fun fromProviderClassName(className: String?): WidgetKind {
      return when {
        className.isNullOrBlank() -> MUSIC
        className.contains("AurixLyricsWidgetProvider", ignoreCase = true) -> LYRICS
        className.contains("AurixCoverWidgetProvider", ignoreCase = true) -> COVER
        className.contains("AurixMiniWidgetProvider", ignoreCase = true) -> MINI
        else -> MUSIC
      }
    }
  }
}
