package com.example.ui.theme

import android.content.Context
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.style.MetricAffectingSpan
import androidx.annotation.FontRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import androidx.core.content.res.ResourcesCompat
import com.example.R
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

/**
 * User-selectable Khmer fonts for Lyrics (In-App Lyrics, Standby Lyrics, Share Card & Home Screen Lyrics Widget).
 * Song titles with Khmer script always use Dangrek (R.font.dangrek).
 */
enum class KhmerLyricsFont(
  val id: String,
  val displayNameEn: String,
  val displayNameKm: String,
  @FontRes val fontRes: Int,
  /**
   * Calibrated optical scale ratios when Khmer and English appear together on the same line,
   * ensuring the Khmer consonant body height and Latin cap/x-height look visually equal in size.
   */
  val khmerMixedScale: Float,
  val englishMixedScale: Float
) {
  KANTUMRUY_PRO(
    id = "kantumruy_pro",
    displayNameEn = "Kantumruy Pro",
    displayNameKm = "Kantumruy Pro",
    fontRes = R.font.kantumruy_pro,
    khmerMixedScale = 1.06f,
    englishMixedScale = 0.96f
  ),
  KOULEN(
    id = "koulen",
    displayNameEn = "Koulen",
    displayNameKm = "Koulen",
    fontRes = R.font.koulen,
    khmerMixedScale = 1.02f,
    englishMixedScale = 0.96f
  ),
  NOTO_SANS_KHMER(
    id = "noto_sans_khmer",
    displayNameEn = "Noto Sans Khmer",
    displayNameKm = "Noto Sans Khmer",
    fontRes = R.font.noto_sans_khmer,
    khmerMixedScale = 1.05f,
    englishMixedScale = 0.96f
  );

  val displayName: String
    get() = displayNameEn

  val khmerLabel: String
    get() = when (this) {
      KANTUMRUY_PRO -> "កន្ទុំរុយ ប្រូ"
      KOULEN -> "គូលែន"
      NOTO_SANS_KHMER -> "ណូតូ សាន់ ខ្មែរ"
    }

  companion object {
    const val DEFAULT_ID = "kantumruy_pro"

    fun fromId(rawId: String?): KhmerLyricsFont {
      if (rawId.isNullOrBlank()) return KANTUMRUY_PRO
      val cleaned = rawId.trim().lowercase()
      return entries.firstOrNull { it.id == cleaned } ?: KANTUMRUY_PRO
    }
  }
}

/**
 * Script run classification for mixed Khmer + English strings.
 */
enum class ScriptRunType {
  KHMER,
  LATIN,
  NEUTRAL
}

data class ScriptTextRun(
  val text: String,
  val start: Int,
  val end: Int,
  val type: ScriptRunType
) {
  val isKhmer: Boolean
    get() = type == ScriptRunType.KHMER
}

/**
 * Compose FontFamilies backed by local bundled full-Unicode .ttf files in res/font.
 */
object AurixFontFamilies {
  val DangrekTitleFamily = FontFamily(
    Font(R.font.dangrek, FontWeight.Normal)
  )

  val KantumruyProFamily = FontFamily(
    Font(R.font.kantumruy_pro, FontWeight.Normal),
    Font(R.font.kantumruy_pro_semibold, FontWeight.Medium),
    Font(R.font.kantumruy_pro_semibold, FontWeight.SemiBold),
    Font(R.font.kantumruy_pro_bold, FontWeight.Bold),
    Font(R.font.kantumruy_pro_bold, FontWeight.ExtraBold)
  )

  val KoulenFamily = FontFamily(
    Font(R.font.koulen, FontWeight.Normal)
  )

  val NotoSansKhmerFamily = FontFamily(
    Font(R.font.noto_sans_khmer, FontWeight.Normal),
    Font(R.font.noto_sans_khmer_semibold, FontWeight.Medium),
    Font(R.font.noto_sans_khmer_semibold, FontWeight.SemiBold),
    Font(R.font.noto_sans_khmer_bold, FontWeight.Bold),
    Font(R.font.noto_sans_khmer_bold, FontWeight.ExtraBold)
  )

  val OutfitLatinTitleFamily = FontFamily(
    Font(R.font.outfit, FontWeight.Normal),
    Font(R.font.outfit_semibold, FontWeight.Medium),
    Font(R.font.outfit_semibold, FontWeight.SemiBold),
    Font(R.font.outfit_bold, FontWeight.Bold),
    Font(R.font.outfit_bold, FontWeight.ExtraBold)
  )

  val InterLatinLyricsFamily = FontFamily(
    Font(R.font.inter, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_medium, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
    Font(R.font.inter_bold, FontWeight.ExtraBold)
  )

  val WorkSansDisplayFamily = FontFamily(
    Font(R.font.work_sans, FontWeight.Normal),
    Font(R.font.work_sans, FontWeight.Medium),
    Font(R.font.work_sans, FontWeight.SemiBold),
    Font(R.font.work_sans, FontWeight.Bold),
    Font(R.font.work_sans, FontWeight.ExtraBold)
  )

  /**
   * Default bilingual UI FontFamily supporting both modern Khmer (Kantumruy Pro)
   * and geometric Latin across all app screens.
   */
  val AurixUiFamily: FontFamily = KantumruyProFamily

  fun composeFamilyForLyricsFont(font: KhmerLyricsFont): FontFamily = when (font) {
    KhmerLyricsFont.KANTUMRUY_PRO -> KantumruyProFamily
    KhmerLyricsFont.KOULEN -> KoulenFamily
    KhmerLyricsFont.NOTO_SANS_KHMER -> NotoSansKhmerFamily
  }
}

/**
 * Utilities for detecting Khmer vs English script runs and equalizing their visual font size
 * in both Titles (Dangrek + Outfit) and Lyrics (Kantumruy Pro / Koulen / Noto Sans Khmer + Inter).
 */
object KhmerTypography {
  // Optical scale constants for Title mixed Khmer (Dangrek) + English (Outfit)
  const val TITLE_KHMER_DANGREK_MIXED_SCALE = 1.05f
  const val TITLE_ENGLISH_MIXED_SCALE = 0.94f

  private val typefaceCache = ConcurrentHashMap<Int, Typeface>()
  private val opticalRatioCache = ConcurrentHashMap<String, Pair<Float, Float>>()

  fun isKhmerChar(ch: Char): Boolean {
    val code = ch.code
    return code in 0x1780..0x17FF || code in 0x19E0..0x19FF
  }

  fun isLatinChar(ch: Char): Boolean {
    val code = ch.code
    return (code in 0x0041..0x005A) ||
      (code in 0x0061..0x007A) ||
      (code in 0x00C0..0x024F) ||
      ch == '·' || ch == '•' || ch == '♪' || ch == '♫' || ch == '—' || ch == '–'
  }

  fun containsKhmer(text: CharSequence?): Boolean {
    if (text.isNullOrEmpty()) return false
    for (i in 0 until text.length) {
      if (isKhmerChar(text[i])) return true
    }
    return false
  }

  fun containsLatin(text: CharSequence?): Boolean {
    if (text.isNullOrEmpty()) return false
    for (i in 0 until text.length) {
      if (isLatinChar(text[i])) return true
    }
    return false
  }

  fun isMixedKhmerAndEnglish(text: CharSequence?): Boolean {
    return containsKhmer(text) && containsLatin(text)
  }

  fun isMixedKhmerAndLatin(text: CharSequence?): Boolean {
    return isMixedKhmerAndEnglish(text)
  }

  /**
   * Splits a string into contiguous KHMER and LATIN runs (attaching neutral spaces/digits/punctuation
   * to the surrounding active script run so fewer spans are created and kerning stays natural).
   */
  fun segmentScriptRuns(text: String): List<ScriptTextRun> {
    if (text.isEmpty()) return emptyList()
    val rawTypes = Array(text.length) { idx ->
      val ch = text[idx]
      when {
        isKhmerChar(ch) -> ScriptRunType.KHMER
        isLatinChar(ch) -> ScriptRunType.LATIN
        else -> ScriptRunType.NEUTRAL
      }
    }

    // Resolve NEUTRAL characters (spaces, numbers, punctuation) to nearest preceding or following script
    var lastConcrete: ScriptRunType? = null
    for (i in rawTypes.indices) {
      if (rawTypes[i] != ScriptRunType.NEUTRAL) {
        lastConcrete = rawTypes[i]
      } else if (lastConcrete != null) {
        rawTypes[i] = lastConcrete
      }
    }
    var nextConcrete: ScriptRunType? = null
    for (i in rawTypes.indices.reversed()) {
      if (rawTypes[i] != ScriptRunType.NEUTRAL) {
        nextConcrete = rawTypes[i]
      } else if (nextConcrete != null) {
        rawTypes[i] = nextConcrete
      }
    }

    val result = ArrayList<ScriptTextRun>()
    var runStart = 0
    var currentType = rawTypes[0]
    for (i in 1 until text.length) {
      if (rawTypes[i] != currentType) {
        result.add(
          ScriptTextRun(
            text = text.substring(runStart, i),
            start = runStart,
            end = i,
            type = currentType
          )
        )
        runStart = i
        currentType = rawTypes[i]
      }
    }
    result.add(
      ScriptTextRun(
        text = text.substring(runStart, text.length),
        start = runStart,
        end = text.length,
        type = currentType
      )
    )
    return result
  }

  private fun resolveWeightedFontRes(@FontRes baseFontRes: Int, style: Int): Int {
    val wantBold = (style and Typeface.BOLD) != 0
    return when (baseFontRes) {
      R.font.kantumruy_pro -> if (wantBold) R.font.kantumruy_pro_bold else R.font.kantumruy_pro
      R.font.noto_sans_khmer -> if (wantBold) R.font.noto_sans_khmer_bold else R.font.noto_sans_khmer
      R.font.outfit -> if (wantBold) R.font.outfit_bold else R.font.outfit
      R.font.inter -> if (wantBold) R.font.inter_bold else R.font.inter
      else -> baseFontRes
    }
  }

  fun loadTypeface(context: Context, @FontRes fontRes: Int, style: Int = Typeface.NORMAL): Typeface {
    val targetRes = resolveWeightedFontRes(fontRes, style)
    return typefaceCache[targetRes] ?: runCatching {
      ResourcesCompat.getFont(context, targetRes)
    }.getOrNull()?.also {
      typefaceCache[targetRes] = it
    } ?: Typeface.DEFAULT
  }

  fun loadInterTypeface(context: Context, isBold: Boolean = true): Typeface {
    return loadTypeface(
      context = context,
      fontRes = R.font.inter,
      style = if (isBold) Typeface.BOLD else Typeface.NORMAL
    )
  }

  fun loadOutfitTypeface(context: Context, isBold: Boolean = true): Typeface {
    return loadTypeface(
      context = context,
      fontRes = R.font.outfit,
      style = if (isBold) Typeface.BOLD else Typeface.NORMAL
    )
  }

  fun loadUiTypeface(context: Context, isBold: Boolean = true): Typeface {
    return loadTypeface(
      context = context,
      fontRes = R.font.kantumruy_pro,
      style = if (isBold) Typeface.BOLD else Typeface.NORMAL
    )
  }

  fun resolveEqualVisualScales(
    text: String,
    isTitle: Boolean,
    khmerLyricsFont: KhmerLyricsFont = KhmerLyricsFont.KANTUMRUY_PRO
  ): Pair<Float, Float> {
    if (!isMixedKhmerAndEnglish(text)) return 1.0f to 1.0f
    return if (isTitle) {
      TITLE_KHMER_DANGREK_MIXED_SCALE to TITLE_ENGLISH_MIXED_SCALE
    } else {
      khmerLyricsFont.khmerMixedScale to khmerLyricsFont.englishMixedScale
    }
  }

  /**
   * Measures actual visual consonant/cap glyph height of the Khmer typeface and Latin typeface
   * and returns (khmerScale, englishScale) so that mixed Khmer + English text has equal visual height.
   */
  fun resolveEqualVisualScales(
    khmerTypeface: Typeface?,
    latinTypeface: Typeface?,
    defaultKhmerScale: Float,
    defaultEnglishScale: Float,
    cacheKey: String
  ): Pair<Float, Float> {
    opticalRatioCache[cacheKey]?.let { return it }
    if (khmerTypeface == null || latinTypeface == null) {
      return defaultKhmerScale to defaultEnglishScale
    }
    val computed = runCatching {
      val testSizePx = 48f
      val khmerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = khmerTypeface
        textSize = testSizePx
      }
      val latinPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = latinTypeface
        textSize = testSizePx
      }
      val khmerBounds = Rect()
      val latinBounds = Rect()
      val khmerSample = "កខគឃងចឆជឈញ"
      val latinSample = "ABCDEFGHx"
      khmerPaint.getTextBounds(khmerSample, 0, khmerSample.length, khmerBounds)
      latinPaint.getTextBounds(latinSample, 0, latinSample.length, latinBounds)
      val khH = khmerBounds.height().toFloat()
      val latH = latinBounds.height().toFloat()
      if (khH > 4f && latH > 4f) {
        val midH = (khH + latH) * 0.5f
        val khScale = (midH / khH).coerceIn(0.92f, 1.12f)
        val enScale = (midH / latH).coerceIn(0.90f, 1.08f)
        khScale to enScale
      } else {
        defaultKhmerScale to defaultEnglishScale
      }
    }.getOrDefault(defaultKhmerScale to defaultEnglishScale)

    opticalRatioCache[cacheKey] = computed
    return computed
  }

  /**
   * Builds a SpannableString for Android TextView or Canvas StaticLayout:
   * - If [isTitle] is true: uses Dangrek (R.font.dangrek) for Khmer and Outfit (R.font.outfit/_bold) for English.
   * - If [isTitle] is false: uses [khmerLyricsFont] (Kantumruy Pro, Koulen, or Noto Sans Khmer) for Khmer
   *   and Inter (R.font.inter/_bold) for English.
   * - When both Khmer and English exist in [text], applies optical scaling so both scripts have equal visual size.
   */
  fun buildStyledSpannable(
    context: Context,
    text: String,
    isTitle: Boolean,
    khmerLyricsFont: KhmerLyricsFont = KhmerLyricsFont.KANTUMRUY_PRO,
    isBold: Boolean = true
  ): SpannableString {
    val span = SpannableString(text)
    if (text.isEmpty()) return span

    val hasKhmer = containsKhmer(text)
    val hasLatin = containsLatin(text)
    val isMixed = hasKhmer && hasLatin
    val styleInt = if (isBold) Typeface.BOLD else Typeface.NORMAL

    val khmerFontRes = if (isTitle) R.font.dangrek else khmerLyricsFont.fontRes
    val latinFontRes = if (isTitle) R.font.outfit else R.font.inter

    val khmerTf = loadTypeface(context, khmerFontRes, styleInt)
    val latinTf = loadTypeface(context, latinFontRes, styleInt)

    val (khmerScale, englishScale) = if (isMixed) {
      val defKh = if (isTitle) TITLE_KHMER_DANGREK_MIXED_SCALE else khmerLyricsFont.khmerMixedScale
      val defEn = if (isTitle) TITLE_ENGLISH_MIXED_SCALE else khmerLyricsFont.englishMixedScale
      resolveEqualVisualScales(
        khmerTypeface = khmerTf,
        latinTypeface = latinTf,
        defaultKhmerScale = defKh,
        defaultEnglishScale = defEn,
        cacheKey = "${if (isTitle) "title" else khmerLyricsFont.id}|$isBold"
      )
    } else {
      1.0f to 1.0f
    }

    val runs = segmentScriptRuns(text)
    for (run in runs) {
      val (tf, scale) = when (run.type) {
        ScriptRunType.KHMER -> khmerTf to khmerScale
        ScriptRunType.LATIN -> latinTf to englishScale
        ScriptRunType.NEUTRAL -> (if (hasKhmer) khmerTf else latinTf) to 1.0f
      }
      // Never smear single-weight Khmer display fonts (Dangrek/Koulen) or true static bold fonts with fakeBold
      span.setSpan(
        CustomTypefaceAndScaleSpan(typeface = tf, sizeMultiplier = scale, fakeBold = false),
        run.start,
        run.end,
        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
      )
    }
    return span
  }

  /**
   * Builds a Jetpack Compose AnnotatedString for song titles:
   * - Uses Dangrek for Khmer script runs
   * - Uses Outfit for English/Latin script runs
   * - Equalizes visual height when Khmer and English appear together in the same title.
   */
  fun buildMixedTitleAnnotatedString(
    text: String,
    baseFontSize: TextUnit = 16.sp,
    fontWeight: FontWeight = FontWeight.Bold
  ): AnnotatedString {
    if (text.isEmpty()) return AnnotatedString("")
    val hasKhmer = containsKhmer(text)
    val hasLatin = containsLatin(text)
    val isMixed = hasKhmer && hasLatin
    val baseSp = if (baseFontSize != TextUnit.Unspecified) baseFontSize.value else 16f

    val khmerSp = (if (isMixed) baseSp * TITLE_KHMER_DANGREK_MIXED_SCALE else baseSp).sp
    val englishSp = (if (isMixed) baseSp * TITLE_ENGLISH_MIXED_SCALE else baseSp).sp

    val runs = segmentScriptRuns(text)
    return buildAnnotatedString {
      for (run in runs) {
        when (run.type) {
          ScriptRunType.KHMER -> {
            withStyle(
              SpanStyle(
                fontFamily = AurixFontFamilies.DangrekTitleFamily,
                fontWeight = fontWeight,
                fontSize = khmerSp
              )
            ) {
              append(run.text)
            }
          }
          ScriptRunType.LATIN -> {
            withStyle(
              SpanStyle(
                fontFamily = AurixFontFamilies.OutfitLatinTitleFamily,
                fontWeight = fontWeight,
                fontSize = englishSp
              )
            ) {
              append(run.text)
            }
          }
          ScriptRunType.NEUTRAL -> {
            withStyle(
              SpanStyle(
                fontFamily = if (hasKhmer) AurixFontFamilies.DangrekTitleFamily else AurixFontFamilies.OutfitLatinTitleFamily,
                fontWeight = fontWeight,
                fontSize = baseSp.sp
              )
            ) {
              append(run.text)
            }
          }
        }
      }
    }
  }

  /**
   * Builds a Jetpack Compose AnnotatedString for lyric lines:
   * - Uses user-selected [khmerLyricsFont] (Kantumruy Pro / Koulen / Noto Sans Khmer) for Khmer runs
   * - Uses Inter for English/Latin runs
   * - Equalizes visual height when Khmer and English appear together in the same lyric line.
   */
  fun buildMixedLyricsAnnotatedString(
    text: String,
    khmerLyricsFont: KhmerLyricsFont,
    baseFontSize: TextUnit = 15.sp,
    fontWeight: FontWeight = FontWeight.Bold
  ): AnnotatedString {
    if (text.isEmpty()) return AnnotatedString("")
    val hasKhmer = containsKhmer(text)
    val hasLatin = containsLatin(text)
    val isMixed = hasKhmer && hasLatin
    val baseSp = if (baseFontSize != TextUnit.Unspecified) baseFontSize.value else 15f

    val khmerFamily = AurixFontFamilies.composeFamilyForLyricsFont(khmerLyricsFont)
    val khmerSp = (if (isMixed) baseSp * khmerLyricsFont.khmerMixedScale else baseSp).sp
    val englishSp = (if (isMixed) baseSp * khmerLyricsFont.englishMixedScale else baseSp).sp

    val runs = segmentScriptRuns(text)
    return buildAnnotatedString {
      for (run in runs) {
        when (run.type) {
          ScriptRunType.KHMER -> {
            withStyle(
              SpanStyle(
                fontFamily = khmerFamily,
                fontWeight = fontWeight,
                fontSize = khmerSp
              )
            ) {
              append(run.text)
            }
          }
          ScriptRunType.LATIN -> {
            withStyle(
              SpanStyle(
                fontFamily = AurixFontFamilies.InterLatinLyricsFamily,
                fontWeight = fontWeight,
                fontSize = englishSp
              )
            ) {
              append(run.text)
            }
          }
          ScriptRunType.NEUTRAL -> {
            withStyle(
              SpanStyle(
                fontFamily = if (hasKhmer) khmerFamily else AurixFontFamilies.InterLatinLyricsFamily,
                fontWeight = fontWeight,
                fontSize = baseSp.sp
              )
            ) {
              append(run.text)
            }
          }
        }
      }
    }
  }
}

class CustomTypefaceAndScaleSpan(
  private val typeface: Typeface,
  private val sizeMultiplier: Float = 1.0f,
  private val fakeBold: Boolean = false
) : MetricAffectingSpan() {
  override fun updateDrawState(ds: TextPaint) {
    apply(ds)
  }

  override fun updateMeasureState(paint: TextPaint) {
    apply(paint)
  }

  private fun apply(paint: TextPaint) {
    paint.typeface = typeface
    if (fakeBold && !typeface.isBold) {
      paint.isFakeBoldText = true
    }
    if (sizeMultiplier != 1.0f) {
      paint.textSize = (paint.textSize * sizeMultiplier * 100f).roundToInt() / 100f
    }
  }
}

@Composable
fun rememberKhmerTitleText(
  text: String,
  baseFontSize: TextUnit,
  isBold: Boolean = true,
  fontWeight: FontWeight = if (isBold) FontWeight.Bold else FontWeight.Medium
): AnnotatedString {
  return remember(text, baseFontSize, isBold, fontWeight) {
    KhmerTypography.buildMixedTitleAnnotatedString(
      text = text,
      baseFontSize = baseFontSize,
      fontWeight = fontWeight
    )
  }
}

@Composable
fun rememberKhmerLyricsText(
  text: String,
  khmerLyricsFont: KhmerLyricsFont,
  baseFontSize: TextUnit,
  isBold: Boolean = true,
  fontWeight: FontWeight = if (isBold) FontWeight.Bold else FontWeight.Medium
): AnnotatedString {
  return remember(text, khmerLyricsFont, baseFontSize, isBold, fontWeight) {
    KhmerTypography.buildMixedLyricsAnnotatedString(
      text = text,
      khmerLyricsFont = khmerLyricsFont,
      baseFontSize = baseFontSize,
      fontWeight = fontWeight
    )
  }
}
