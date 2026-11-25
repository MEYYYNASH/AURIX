package com.example.ui.screens

import android.graphics.Paint as NativePaint
import android.graphics.Typeface
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.res.ResourcesCompat
import com.example.R
import java.util.Calendar
import java.util.Date
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * 10 Bedside Standby Clock Styles saved to PreferencesManager.
 */
enum class StandbyClockStyle(
  val id: String,
  val displayName: String,
  val tagLine: String
) {
  THIN("thin", "Thin", "Minimalist ultra-light"),
  BOLD("bold", "Bold", "Heavy geometric display"),
  MINECRAFT("minecraft", "Minecraft", "5×7 pixel blocks"),
  LED_DOTS("led_dots", "LED Dots", "5×7 dot-matrix array"),
  FLIP_CARDS("flip_cards", "Flip Cards", "Split mechanical cards"),
  NEON("neon", "Neon", "Orbitron cyber glow"),
  SERIF("serif", "Serif", "Playfair editorial"),
  ROUND("round", "Round", "Fredoka gradient"),
  STACK("stack", "Stack", "Vertical double deck"),
  OUTLINE("outline", "Outline", "Hollow vector stroke");

  companion object {
    const val DEFAULT_ID = "thin"

    fun fromId(raw: String?): StandbyClockStyle {
      if (raw.isNullOrBlank()) return THIN
      val cleaned = raw.trim().lowercase()
      return entries.firstOrNull { it.id == cleaned } ?: THIN
    }
  }
}

/**
 * 5 Standby Color Moods: Auto (live cover palette with smooth lerp) + Lo-fi, Sunset, Mint, Mono.
 */
enum class StandbyColorMood(
  val id: String,
  val displayNameEn: String,
  val displayNameKm: String
) {
  AUTO("auto", "Auto", "ស្វ័យប្រវត្តិ"),
  LOFI("lofi", "Lo-fi", "Lo-fi"),
  SUNSET("sunset", "Sunset", "Sunset"),
  MINT("mint", "Mint", "Mint"),
  MONO("mono", "Mono", "Mono");

  fun next(): StandbyColorMood {
    val list = entries
    return list[(ordinal + 1) % list.size]
  }

  companion object {
    const val DEFAULT_ID = "auto"

    fun fromId(raw: String?): StandbyColorMood {
      if (raw.isNullOrBlank()) return AUTO
      val cleaned = raw.trim().lowercase()
      return entries.firstOrNull { it.id == cleaned } ?: AUTO
    }
  }
}

data class StandbyMoodPalette(
  val background: Color,
  val blob1: Color,
  val blob2: Color,
  val blob3: Color,
  val blob4: Color,
  val accent: Color
)

/**
 * Resolves the target StandbyMoodPalette for [mood].
 * In [StandbyColorMood.AUTO], derives a dark atmospheric background, 4 harmonic blob colors,
 * and a luminous accent from the current song's cover art colors ([coverPrimary], [coverSecondary]).
 */
fun resolveStandbyMoodPalette(
  mood: StandbyColorMood,
  coverPrimary: Color,
  coverSecondary: Color,
  hasActiveSong: Boolean
): StandbyMoodPalette {
  return when (mood) {
    StandbyColorMood.LOFI -> StandbyMoodPalette(
      background = Color(0xFF080711),
      blob1 = Color(0xFF5E4B8B),
      blob2 = Color(0xFF8B6CEF),
      blob3 = Color(0xFF392E5C),
      blob4 = Color(0xFFB794F4),
      accent = Color(0xFFC4B5FD)
    )
    StandbyColorMood.SUNSET -> StandbyMoodPalette(
      background = Color(0xFF12070C),
      blob1 = Color(0xFFF97316),
      blob2 = Color(0xFFEC4899),
      blob3 = Color(0xFF881337),
      blob4 = Color(0xFFF59E0B),
      accent = Color(0xFFFDBA74)
    )
    StandbyColorMood.MINT -> StandbyMoodPalette(
      background = Color(0xFF05100D),
      blob1 = Color(0xFF10B981),
      blob2 = Color(0xFF06B6D4),
      blob3 = Color(0xFF064E3B),
      blob4 = Color(0xFF34D399),
      accent = Color(0xFF6EE7B7)
    )
    StandbyColorMood.MONO -> StandbyMoodPalette(
      background = Color(0xFF08090D),
      blob1 = Color(0xFF334155),
      blob2 = Color(0xFF475569),
      blob3 = Color(0xFF1E293B),
      blob4 = Color(0xFF64748B),
      accent = Color(0xFFCBD5E1)
    )
    StandbyColorMood.AUTO -> {
      if (!hasActiveSong) {
        // Fallback to AURIX grey-lavender signature palette when no song is loaded
        StandbyMoodPalette(
          background = Color(0xFF080912),
          blob1 = Color(0xFF5B4E8C),
          blob2 = Color(0xFF4361EE),
          blob3 = Color(0xFF2B2D42),
          blob4 = Color(0xFF9D8DF1),
          accent = Color(0xFFC4B5FD)
        )
      } else {
        val bg = lerpColor(Color(0xFF06070C), coverPrimary, 0.14f)
        val b1 = lerpColor(coverPrimary, Color(0xFF9D8DF1), 0.18f)
        val b2 = lerpColor(coverSecondary, Color(0xFF43C7F4), 0.16f)
        val b3 = lerpColor(coverPrimary, coverSecondary, 0.52f)
        val b4 = lerpColor(coverSecondary, Color.White, 0.24f)
        val acc = lerpColor(coverPrimary, Color.White, 0.34f)
        StandbyMoodPalette(
          background = bg,
          blob1 = b1,
          blob2 = b2,
          blob3 = b3,
          blob4 = b4,
          accent = acc
        )
      }
    }
  }
}

private fun lerpColor(start: Color, stop: Color, fraction: Float): Color {
  val t = fraction.coerceIn(0f, 1f)
  return Color(
    red = start.red + (stop.red - start.red) * t,
    green = start.green + (stop.green - start.green) * t,
    blue = start.blue + (stop.blue - start.blue) * t,
    alpha = start.alpha + (stop.alpha - start.alpha) * t
  )
}

@Composable
fun rememberAnimatedStandbyPalette(
  target: StandbyMoodPalette,
  reducedMotion: Boolean
): StandbyMoodPalette {
  val spec = if (reducedMotion) snap<Color>() else tween(durationMillis = 900, easing = FastOutSlowInEasing)
  val bg by animateColorAsState(targetValue = target.background, animationSpec = spec, label = "sb_bg")
  val b1 by animateColorAsState(targetValue = target.blob1, animationSpec = spec, label = "sb_b1")
  val b2 by animateColorAsState(targetValue = target.blob2, animationSpec = spec, label = "sb_b2")
  val b3 by animateColorAsState(targetValue = target.blob3, animationSpec = spec, label = "sb_b3")
  val b4 by animateColorAsState(targetValue = target.blob4, animationSpec = spec, label = "sb_b4")
  val acc by animateColorAsState(targetValue = target.accent, animationSpec = spec, label = "sb_acc")
  return remember(bg, b1, b2, b3, b4, acc) {
    StandbyMoodPalette(
      background = bg,
      blob1 = b1,
      blob2 = b2,
      blob3 = b3,
      blob4 = b4,
      accent = acc
    )
  }
}

private val KHMER_WEEKDAYS = arrayOf(
  "ថ្ងៃអាទិត្យ", // Sunday (Calendar.SUNDAY = 1)
  "ថ្ងៃចន្ទ",    // Monday
  "ថ្ងៃអង្គារ",   // Tuesday
  "ថ្ងៃពុធ",     // Wednesday
  "ថ្ងៃព្រហស្បតិ៍", // Thursday
  "ថ្ងៃសុក្រ",   // Friday
  "ថ្ងៃសៅរ៍"     // Saturday
)

private val KHMER_MONTHS = arrayOf(
  "មករា",
  "កុម្ភៈ",
  "មីនា",
  "មេសា",
  "ឧសភា",
  "មិថុនា",
  "កក្កដា",
  "សីហា",
  "កញ្ញា",
  "តុលា",
  "វិច្ឆិកា",
  "ធ្នូ"
)

/**
 * Formats the given timestamp into a Khmer date string with Khmer weekday and Khmer month names
 * (e.g., "ថ្ងៃអង្គារ 24 វិច្ឆិកា").
 */
fun formatKhmerStandbyDate(millis: Long): String {
  val cal = Calendar.getInstance().apply { time = Date(millis) }
  val dayOfWeekIdx = (cal.get(Calendar.DAY_OF_WEEK) - 1).coerceIn(0, 6)
  val dayOfMonth = cal.get(Calendar.DAY_OF_MONTH)
  val monthIdx = cal.get(Calendar.MONTH).coerceIn(0, 11)
  return "${KHMER_WEEKDAYS[dayOfWeekIdx]} $dayOfMonth ${KHMER_MONTHS[monthIdx]}"
}

data class StandbyTimeParts(
  val hours: String,
  val minutes: String,
  val amPm: String,
  val colonVisible: Boolean
)

fun formatStandbyClockParts(millis: Long, use24h: Boolean): StandbyTimeParts {
  val cal = Calendar.getInstance().apply { time = Date(millis) }
  val hour24 = cal.get(Calendar.HOUR_OF_DAY)
  val minute = cal.get(Calendar.MINUTE)
  val second = cal.get(Calendar.SECOND)
  val hoursStr = if (use24h) {
    String.format("%02d", hour24)
  } else {
    val h12 = when {
      hour24 == 0 -> 12
      hour24 > 12 -> hour24 - 12
      else -> hour24
    }
    String.format("%02d", h12)
  }
  val minutesStr = String.format("%02d", minute)
  val amPmStr = if (use24h) "" else if (hour24 < 12) "AM" else "PM"
  val colonVisible = (second % 2 == 0)
  return StandbyTimeParts(
    hours = hoursStr,
    minutes = minutesStr,
    amPm = amPmStr,
    colonVisible = colonVisible
  )
}

object StandbyClockFonts {
  val OrbitronFamily = FontFamily(
    Font(R.font.orbitron, FontWeight.Normal),
    Font(R.font.orbitron, FontWeight.Bold)
  )
  val PlayfairFamily = FontFamily(
    Font(R.font.playfair_display, FontWeight.Normal),
    Font(R.font.playfair_display, FontWeight.Bold)
  )
  val FredokaFamily = FontFamily(
    Font(R.font.fredoka, FontWeight.Normal),
    Font(R.font.fredoka, FontWeight.SemiBold),
    Font(R.font.fredoka, FontWeight.Bold)
  )
  val OutfitFamily = FontFamily(
    Font(R.font.outfit, FontWeight.Light),
    Font(R.font.outfit, FontWeight.Normal),
    Font(R.font.outfit, FontWeight.Bold),
    Font(R.font.outfit, FontWeight.ExtraBold)
  )
}

/**
 * 5×7 pixel matrix bitmaps for digits '0'..'9' and ':' used by Minecraft (pixel blocks) and LED Dots.
 */
private val PIXEL_5X7_GLYPHS: Map<Char, IntArray> = mapOf(
  '0' to intArrayOf(
    0b01110,
    0b10001,
    0b10011,
    0b10101,
    0b11001,
    0b10001,
    0b01110
  ),
  '1' to intArrayOf(
    0b00100,
    0b01100,
    0b00100,
    0b00100,
    0b00100,
    0b00100,
    0b01110
  ),
  '2' to intArrayOf(
    0b01110,
    0b10001,
    0b00001,
    0b00110,
    0b01000,
    0b10000,
    0b11111
  ),
  '3' to intArrayOf(
    0b11110,
    0b00001,
    0b00001,
    0b01110,
    0b00001,
    0b00001,
    0b11110
  ),
  '4' to intArrayOf(
    0b00010,
    0b00110,
    0b01010,
    0b10010,
    0b11111,
    0b00010,
    0b00010
  ),
  '5' to intArrayOf(
    0b11111,
    0b10000,
    0b11110,
    0b00001,
    0b00001,
    0b10001,
    0b01110
  ),
  '6' to intArrayOf(
    0b00110,
    0b01000,
    0b10000,
    0b11110,
    0b10001,
    0b10001,
    0b01110
  ),
  '7' to intArrayOf(
    0b11111,
    0b00001,
    0b00010,
    0b00100,
    0b01000,
    0b01000,
    0b01000
  ),
  '8' to intArrayOf(
    0b01110,
    0b10001,
    0b10001,
    0b01110,
    0b10001,
    0b10001,
    0b01110
  ),
  '9' to intArrayOf(
    0b01110,
    0b10001,
    0b10001,
    0b01111,
    0b00001,
    0b00010,
    0b01100
  ),
  ':' to intArrayOf(
    0b00000,
    0b00100,
    0b00100,
    0b00000,
    0b00100,
    0b00100,
    0b00000
  )
)

/**
 * Full-screen canvas of 4 slowly drifting radial gradient blobs + dark radial vignette.
 */
@Composable
fun StandbyDriftingBlobsBackground(
  palette: StandbyMoodPalette,
  reducedMotion: Boolean,
  modifier: Modifier = Modifier
) {
  val infinite = rememberInfiniteTransition(label = "standby_blobs_drift")
  val phase by infinite.animateFloat(
    initialValue = 0f,
    targetValue = (Math.PI * 2.0).toFloat(),
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = 26_000, easing = LinearEasing),
      repeatMode = RepeatMode.Restart
    ),
    label = "standby_blob_phase"
  )
  val t = if (reducedMotion) 0.6f else phase

  Canvas(modifier = modifier.fillMaxSize()) {
    drawRect(color = palette.background)

    val w = size.width
    val h = size.height
    val maxDim = max(w, h).coerceAtLeast(1f)

    val centers = listOf(
      Offset(
        x = w * (0.24f + 0.14f * cos(t)),
        y = h * (0.28f + 0.14f * sin(t * 0.8f))
      ) to palette.blob1,
      Offset(
        x = w * (0.76f + 0.13f * sin(t * 0.9f + 1.2f)),
        y = h * (0.32f + 0.15f * cos(t + 0.7f))
      ) to palette.blob2,
      Offset(
        x = w * (0.34f + 0.15f * sin(t + 2.4f)),
        y = h * (0.76f + 0.12f * cos(t * 0.7f + 1.8f))
      ) to palette.blob3,
      Offset(
        x = w * (0.72f + 0.12f * cos(t + 3.8f)),
        y = h * (0.72f + 0.14f * sin(t + 2.9f))
      ) to palette.blob4
    )

    centers.forEachIndexed { index, (center, blobColor) ->
      val radius = maxDim * (0.44f + (index % 2) * 0.08f)
      drawCircle(
        brush = Brush.radialGradient(
          colors = listOf(
            blobColor.copy(alpha = 0.34f),
            blobColor.copy(alpha = 0.12f),
            Color.Transparent
          ),
          center = center,
          radius = radius
        ),
        radius = radius,
        center = center
      )
    }

    // Soft dark vignette around the edges to frame the clock and keep OLED blacks deep
    val screenCenter = Offset(w * 0.5f, h * 0.5f)
    val vignetteRadius = maxDim * 0.78f
    drawRect(
      brush = Brush.radialGradient(
        colors = listOf(
          Color.Transparent,
          Color.Black.copy(alpha = 0.38f),
          Color.Black.copy(alpha = 0.76f)
        ),
        center = screenCenter,
        radius = vignetteRadius
      )
    )
  }
}

/**
 * Generated procedural album art fallback built from the active StandbyMoodPalette,
 * featuring harmonic radial gradients, subtle orbital rings, and AURIX's planet with the -16° tilted ring.
 */
@Composable
fun StandbyProceduralArtworkFallback(
  palette: StandbyMoodPalette,
  modifier: Modifier = Modifier
) {
  Canvas(modifier = modifier.fillMaxSize()) {
    val w = size.width
    val h = size.height
    val minDim = min(w, h).coerceAtLeast(1f)
    val c = Offset(w * 0.5f, h * 0.5f)

    // Base gradient
    drawRect(
      brush = Brush.linearGradient(
        colors = listOf(
          palette.background,
          lerpColor(palette.background, palette.blob1, 0.55f),
          lerpColor(palette.background, palette.blob2, 0.42f)
        ),
        start = Offset.Zero,
        end = Offset(w, h)
      )
    )

    // Procedural radial color clouds
    drawCircle(
      brush = Brush.radialGradient(
        colors = listOf(palette.blob1.copy(alpha = 0.65f), Color.Transparent),
        center = Offset(w * 0.28f, h * 0.30f),
        radius = minDim * 0.65f
      ),
      radius = minDim * 0.65f,
      center = Offset(w * 0.28f, h * 0.30f)
    )
    drawCircle(
      brush = Brush.radialGradient(
        colors = listOf(palette.blob4.copy(alpha = 0.50f), Color.Transparent),
        center = Offset(w * 0.75f, h * 0.72f),
        radius = minDim * 0.58f
      ),
      radius = minDim * 0.58f,
      center = Offset(w * 0.75f, h * 0.72f)
    )

    // Concentric fine acoustic rings
    for (i in 1..3) {
      drawCircle(
        color = Color.White.copy(alpha = 0.07f),
        radius = minDim * (0.22f + i * 0.09f),
        center = c,
        style = Stroke(width = 1.dp.toPx())
      )
    }

    // AURIX signature planet + -16° tilted orbit ring emblem
    val planetRadius = minDim * 0.17f
    // Back half of tilted ring
    rotate(degrees = -16f, pivot = c) {
      drawArc(
        color = palette.accent.copy(alpha = 0.45f),
        startAngle = 180f,
        sweepAngle = 180f,
        useCenter = false,
        topLeft = Offset(c.x - planetRadius * 1.85f, c.y - planetRadius * 0.58f),
        size = Size(planetRadius * 3.70f, planetRadius * 1.16f),
        style = Stroke(width = (minDim * 0.028f).coerceAtLeast(2f), cap = StrokeCap.Round)
      )
    }

    // Planet sphere
    drawCircle(
      brush = Brush.radialGradient(
        colors = listOf(
          Color.White.copy(alpha = 0.95f),
          palette.accent,
          palette.blob1
        ),
        center = Offset(c.x - planetRadius * 0.28f, c.y - planetRadius * 0.28f),
        radius = planetRadius * 1.35f
      ),
      radius = planetRadius,
      center = c
    )

    // Front half of tilted ring
    rotate(degrees = -16f, pivot = c) {
      drawArc(
        brush = Brush.horizontalGradient(
          colors = listOf(palette.blob2, Color.White, palette.accent)
        ),
        startAngle = 0f,
        sweepAngle = 180f,
        useCenter = false,
        topLeft = Offset(c.x - planetRadius * 1.85f, c.y - planetRadius * 0.58f),
        size = Size(planetRadius * 3.70f, planetRadius * 1.16f),
        style = Stroke(width = (minDim * 0.032f).coerceAtLeast(2.5f), cap = StrokeCap.Round)
      )
    }
  }
}

/**
 * Renders the Standby Clock in any of the 10 selectable [StandbyClockStyle]s with a blinking colon every second.
 */
@Composable
fun StandbyClockFace(
  timeParts: StandbyTimeParts,
  style: StandbyClockStyle,
  accent: Color,
  compact: Boolean,
  reducedMotion: Boolean,
  modifier: Modifier = Modifier
) {
  val colonAlpha by animateFloatAsState(
    targetValue = if (timeParts.colonVisible || reducedMotion) 1f else 0.16f,
    animationSpec = if (reducedMotion) snap() else tween(durationMillis = 220),
    label = "standby_colon_blink"
  )

  val baseSizeSp = if (compact) 68f else 82f

  Box(
    modifier = modifier.testTag("standby_clock_text"),
    contentAlignment = Alignment.Center
  ) {
    when (style) {
      StandbyClockStyle.THIN -> {
        StandbyStandardTextClock(
          timeParts = timeParts,
          colonAlpha = colonAlpha,
          fontFamily = StandbyClockFonts.OutfitFamily,
          fontWeight = FontWeight.Light,
          fontSizeSp = baseSizeSp,
          letterSpacingSp = -2f,
          textColor = Color.White,
          accent = accent
        )
      }

      StandbyClockStyle.BOLD -> {
        StandbyStandardTextClock(
          timeParts = timeParts,
          colonAlpha = colonAlpha,
          fontFamily = StandbyClockFonts.OutfitFamily,
          fontWeight = FontWeight.ExtraBold,
          fontSizeSp = baseSizeSp,
          letterSpacingSp = -1.5f,
          textColor = Color.White,
          accent = accent
        )
      }

      StandbyClockStyle.NEON -> {
        StandbyStandardTextClock(
          timeParts = timeParts,
          colonAlpha = colonAlpha,
          fontFamily = StandbyClockFonts.OrbitronFamily,
          fontWeight = FontWeight.Bold,
          fontSizeSp = baseSizeSp * 0.84f,
          letterSpacingSp = 2f,
          textColor = Color.White,
          accent = accent,
          shadow = Shadow(
            color = accent,
            offset = Offset.Zero,
            blurRadius = 32f
          )
        )
      }

      StandbyClockStyle.SERIF -> {
        StandbyStandardTextClock(
          timeParts = timeParts,
          colonAlpha = colonAlpha,
          fontFamily = StandbyClockFonts.PlayfairFamily,
          fontWeight = FontWeight.Bold,
          fontSizeSp = baseSizeSp * 0.96f,
          letterSpacingSp = -0.5f,
          textColor = Color.White,
          accent = accent
        )
      }

      StandbyClockStyle.ROUND -> {
        val gradientBrush = remember(accent) {
          Brush.verticalGradient(
            colors = listOf(Color.White, lerpColor(Color.White, accent, 0.78f), accent)
          )
        }
        StandbyStandardTextClock(
          timeParts = timeParts,
          colonAlpha = colonAlpha,
          fontFamily = StandbyClockFonts.FredokaFamily,
          fontWeight = FontWeight.Bold,
          fontSizeSp = baseSizeSp * 0.95f,
          letterSpacingSp = 0.5f,
          textColor = Color.White,
          accent = accent,
          brush = gradientBrush
        )
      }

      StandbyClockStyle.MINECRAFT -> {
        StandbyPixelMatrixClock(
          timeParts = timeParts,
          colonAlpha = colonAlpha,
          accent = accent,
          isLedDots = false,
          compact = compact
        )
      }

      StandbyClockStyle.LED_DOTS -> {
        StandbyPixelMatrixClock(
          timeParts = timeParts,
          colonAlpha = colonAlpha,
          accent = accent,
          isLedDots = true,
          compact = compact
        )
      }

      StandbyClockStyle.FLIP_CARDS -> {
        StandbyFlipCardsClock(
          timeParts = timeParts,
          colonAlpha = colonAlpha,
          accent = accent,
          compact = compact
        )
      }

      StandbyClockStyle.STACK -> {
        StandbyStackedClock(
          timeParts = timeParts,
          colonAlpha = colonAlpha,
          accent = accent,
          compact = compact
        )
      }

      StandbyClockStyle.OUTLINE -> {
        StandbyOutlineClock(
          timeParts = timeParts,
          colonAlpha = colonAlpha,
          accent = accent,
          compact = compact
        )
      }
    }
  }
}

@Composable
private fun StandbyStandardTextClock(
  timeParts: StandbyTimeParts,
  colonAlpha: Float,
  fontFamily: FontFamily,
  fontWeight: FontWeight,
  fontSizeSp: Float,
  letterSpacingSp: Float,
  textColor: Color,
  accent: Color,
  shadow: Shadow? = null,
  brush: Brush? = null
) {
  val baseStyle = TextStyle(
    fontFamily = fontFamily,
    fontWeight = fontWeight,
    fontSize = fontSizeSp.sp,
    letterSpacing = letterSpacingSp.sp,
    shadow = shadow,
    brush = brush
  )
  Row(
    verticalAlignment = Alignment.Bottom,
    horizontalArrangement = Arrangement.Center
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(
        text = timeParts.hours,
        style = baseStyle,
        color = if (brush == null) textColor else Color.Unspecified
      )
      Text(
        text = ":",
        style = baseStyle,
        color = if (brush == null) accent else Color.Unspecified,
        modifier = Modifier
          .padding(horizontal = 2.dp)
          .alpha(colonAlpha)
      )
      Text(
        text = timeParts.minutes,
        style = baseStyle,
        color = if (brush == null) textColor else Color.Unspecified
      )
    }
    if (timeParts.amPm.isNotBlank()) {
      Spacer(modifier = Modifier.width(6.dp))
      Text(
        text = timeParts.amPm,
        style = MaterialTheme.typography.labelMedium.copy(
          fontFamily = StandbyClockFonts.OutfitFamily,
          fontWeight = FontWeight.Bold,
          fontSize = 14.sp,
          letterSpacing = 1.sp
        ),
        color = accent.copy(alpha = 0.88f),
        modifier = Modifier.padding(bottom = (fontSizeSp * 0.16f).dp)
      )
    }
  }
}

/**
 * 5×7 matrix renderer for both MINECRAFT (crisp pixel blocks with subtle bevel)
 * and LED_DOTS (glowing circular LED array with dim unlit matrix dots).
 */
@Composable
private fun StandbyPixelMatrixClock(
  timeParts: StandbyTimeParts,
  colonAlpha: Float,
  accent: Color,
  isLedDots: Boolean,
  compact: Boolean
) {
  val chars = remember(timeParts.hours, timeParts.minutes) {
    "${timeParts.hours}:${timeParts.minutes}".toCharArray()
  }
  val boxHeight = if (compact) 62.dp else 74.dp
  val boxWidth = if (compact) 232.dp else 272.dp

  Row(
    verticalAlignment = Alignment.Bottom,
    horizontalArrangement = Arrangement.Center
  ) {
    Canvas(modifier = Modifier.size(width = boxWidth, height = boxHeight)) {
      val totalCols = 5 * 4 + 3 + 4 * 1 // 4 digits (5 cols each) + 1 colon (3 cols) + 4 gaps (1 col each) = 27 units
      val totalRows = 7
      val cellSize = min(size.width / totalCols, size.height / totalRows)
      val drawW = cellSize * totalCols
      val drawH = cellSize * totalRows
      val startX = (size.width - drawW) * 0.5f
      val startY = (size.height - drawH) * 0.5f

      var colCursor = 0
      chars.forEach { ch ->
        val isColon = ch == ':'
        val glyphCols = if (isColon) 3 else 5
        val rows = PIXEL_5X7_GLYPHS[ch] ?: PIXEL_5X7_GLYPHS['0']!!
        val alphaMultiplier = if (isColon) colonAlpha else 1f
        val activeColor = if (isColon) accent else Color.White

        for (r in 0 until totalRows) {
          val rowMask = rows[r]
          for (c in 0 until glyphCols) {
            val bitIndex = if (isColon) (3 - c) else (4 - c)
            val isLit = ((rowMask shr bitIndex) and 1) == 1
            val cellLeft = startX + (colCursor + c) * cellSize
            val cellTop = startY + r * cellSize

            if (isLedDots) {
              val center = Offset(cellLeft + cellSize * 0.5f, cellTop + cellSize * 0.5f)
              if (isLit) {
                drawCircle(
                  color = accent.copy(alpha = 0.28f * alphaMultiplier),
                  radius = cellSize * 0.56f,
                  center = center
                )
                drawCircle(
                  color = activeColor.copy(alpha = 0.96f * alphaMultiplier),
                  radius = cellSize * 0.38f,
                  center = center
                )
              } else if (!isColon) {
                drawCircle(
                  color = Color.White.copy(alpha = 0.08f),
                  radius = cellSize * 0.26f,
                  center = center
                )
              }
            } else {
              // Minecraft 5×7 square pixel blocks
              if (isLit) {
                val blockPad = cellSize * 0.07f
                val blockSize = cellSize - blockPad * 2f
                // Subtle pixel drop shadow
                drawRect(
                  color = accent.copy(alpha = 0.35f * alphaMultiplier),
                  topLeft = Offset(cellLeft + blockPad + 1.5f, cellTop + blockPad + 1.5f),
                  size = Size(blockSize, blockSize)
                )
                drawRect(
                  color = activeColor.copy(alpha = 0.96f * alphaMultiplier),
                  topLeft = Offset(cellLeft + blockPad, cellTop + blockPad),
                  size = Size(blockSize, blockSize)
                )
              }
            }
          }
        }
        colCursor += glyphCols + 1
      }
    }

    if (timeParts.amPm.isNotBlank()) {
      Spacer(modifier = Modifier.width(6.dp))
      Text(
        text = timeParts.amPm,
        style = MaterialTheme.typography.labelMedium.copy(
          fontFamily = StandbyClockFonts.OutfitFamily,
          fontWeight = FontWeight.Bold,
          fontSize = 13.sp
        ),
        color = accent.copy(alpha = 0.88f),
        modifier = Modifier.padding(bottom = 6.dp)
      )
    }
  }
}

@Composable
private fun StandbyFlipCardsClock(
  timeParts: StandbyTimeParts,
  colonAlpha: Float,
  accent: Color,
  compact: Boolean
) {
  val cardWidth = if (compact) 86.dp else 102.dp
  val cardHeight = if (compact) 74.dp else 88.dp
  val digitFontSize = if (compact) 46.sp else 56.sp

  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp)
  ) {
    FlipClockCardUnit(
      value = timeParts.hours,
      accent = accent,
      cardWidth = cardWidth,
      cardHeight = cardHeight,
      fontSize = digitFontSize
    )

    Column(
      modifier = Modifier.alpha(colonAlpha),
      verticalArrangement = Arrangement.spacedBy(10.dp),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      Box(
        modifier = Modifier
          .size(7.dp)
          .clip(CircleShape)
          .background(accent)
      )
      Box(
        modifier = Modifier
          .size(7.dp)
          .clip(CircleShape)
          .background(accent)
      )
    }

    FlipClockCardUnit(
      value = timeParts.minutes,
      accent = accent,
      cardWidth = cardWidth,
      cardHeight = cardHeight,
      fontSize = digitFontSize,
      badge = timeParts.amPm
    )
  }
}

@Composable
private fun FlipClockCardUnit(
  value: String,
  accent: Color,
  cardWidth: androidx.compose.ui.unit.Dp,
  cardHeight: androidx.compose.ui.unit.Dp,
  fontSize: androidx.compose.ui.unit.TextUnit,
  badge: String = ""
) {
  val shape = RoundedCornerShape(16.dp)
  Box(
    modifier = Modifier
      .size(width = cardWidth, height = cardHeight)
      .clip(shape)
      .background(
        Brush.verticalGradient(
          colors = listOf(
            Color(0xFF1D2233).copy(alpha = 0.90f),
            Color(0xFF10131E).copy(alpha = 0.95f)
          )
        )
      )
      .border(1.dp, accent.copy(alpha = 0.28f), shape),
    contentAlignment = Alignment.Center
  ) {
    Text(
      text = value,
      style = TextStyle(
        fontFamily = StandbyClockFonts.OutfitFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = fontSize,
        letterSpacing = (-1).sp
      ),
      color = Color.White
    )
    // Split mechanical horizontal crease across center
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .height(1.5.dp)
        .background(Color.Black.copy(alpha = 0.68f))
    )
    if (badge.isNotBlank()) {
      Text(
        text = badge,
        style = MaterialTheme.typography.labelSmall.copy(
          fontWeight = FontWeight.Bold,
          fontSize = 9.sp
        ),
        color = accent.copy(alpha = 0.85f),
        modifier = Modifier
          .align(Alignment.BottomEnd)
          .padding(horizontal = 7.dp, vertical = 4.dp)
      )
    }
  }
}

@Composable
private fun StandbyStackedClock(
  timeParts: StandbyTimeParts,
  colonAlpha: Float,
  accent: Color,
  compact: Boolean
) {
  val stackFontSize = if (compact) 52.sp else 64.sp
  val lineHeight = if (compact) 48.sp else 58.sp
  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(2.dp)
  ) {
    Text(
      text = timeParts.hours,
      style = TextStyle(
        fontFamily = StandbyClockFonts.OutfitFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = stackFontSize,
        lineHeight = lineHeight,
        letterSpacing = (-2).sp
      ),
      color = Color.White
    )
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
      Box(
        modifier = Modifier
          .width(26.dp)
          .height(3.dp)
          .clip(CircleShape)
          .alpha(colonAlpha)
          .background(accent)
      )
      if (timeParts.amPm.isNotBlank()) {
        Text(
          text = timeParts.amPm,
          style = MaterialTheme.typography.labelSmall.copy(
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp
          ),
          color = accent
        )
      }
    }
    Text(
      text = timeParts.minutes,
      style = TextStyle(
        fontFamily = StandbyClockFonts.OutfitFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = stackFontSize,
        lineHeight = lineHeight,
        letterSpacing = (-2).sp
      ),
      color = accent
    )
  }
}

@Composable
private fun StandbyOutlineClock(
  timeParts: StandbyTimeParts,
  colonAlpha: Float,
  accent: Color,
  compact: Boolean
) {
  val context = LocalContext.current
  val outfitBoldTf = remember(context) {
    runCatching { ResourcesCompat.getFont(context, R.font.outfit) }.getOrNull()
      ?.let { Typeface.create(it, Typeface.BOLD) }
      ?: Typeface.DEFAULT_BOLD
  }
  val boxHeight = if (compact) 72.dp else 86.dp
  val boxWidth = if (compact) 236.dp else 276.dp

  Row(
    verticalAlignment = Alignment.Bottom,
    horizontalArrangement = Arrangement.Center
  ) {
    Canvas(modifier = Modifier.size(width = boxWidth, height = boxHeight)) {
      val textPx = size.height * 0.82f
      val strokePaint = NativePaint(NativePaint.ANTI_ALIAS_FLAG).apply {
        typeface = outfitBoldTf
        textSize = textPx
        style = NativePaint.Style.STROKE
        strokeWidth = 2.4.dp.toPx()
        strokeJoin = NativePaint.Join.ROUND
        color = Color.White.toArgb()
      }
      val glowPaint = NativePaint(strokePaint).apply {
        strokeWidth = 5.5.dp.toPx()
        color = accent.copy(alpha = 0.25f).toArgb()
      }
      val colonPaint = NativePaint(strokePaint).apply {
        color = accent.copy(alpha = colonAlpha).toArgb()
      }

      val hW = strokePaint.measureText(timeParts.hours)
      val cW = strokePaint.measureText(":")
      val mW = strokePaint.measureText(timeParts.minutes)
      val gap = 4.dp.toPx()
      val totalW = hW + cW + mW + gap * 2f
      val startX = (size.width - totalW) * 0.5f
      val fm = strokePaint.fontMetrics
      val baselineY = (size.height - (fm.descent - fm.ascent)) * 0.5f - fm.ascent

      drawContext.canvas.nativeCanvas.apply {
        drawText(timeParts.hours, startX, baselineY, glowPaint)
        drawText(timeParts.hours, startX, baselineY, strokePaint)

        val colonX = startX + hW + gap
        drawText(":", colonX, baselineY, colonPaint)

        val minX = colonX + cW + gap
        drawText(timeParts.minutes, minX, baselineY, glowPaint)
        drawText(timeParts.minutes, minX, baselineY, strokePaint)
      }
    }

    if (timeParts.amPm.isNotBlank()) {
      Spacer(modifier = Modifier.width(4.dp))
      Text(
        text = timeParts.amPm,
        style = MaterialTheme.typography.labelMedium.copy(
          fontWeight = FontWeight.Bold,
          fontSize = 13.sp
        ),
        color = accent.copy(alpha = 0.88f),
        modifier = Modifier.padding(bottom = 10.dp)
      )
    }
  }
}

/**
 * Clock Style Picker Sheet with all 10 clock styles + 12h/24h toggle, persisted in PreferencesManager.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StandbyClockStylePickerSheet(
  selectedStyle: StandbyClockStyle,
  use24h: Boolean,
  accent: Color,
  onSelectStyle: (StandbyClockStyle) -> Unit,
  onToggleUse24h: (Boolean) -> Unit,
  onDismiss: () -> Unit
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = Color(0xFF10131D),
    contentColor = Color.White,
    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 20.dp)
        .padding(bottom = 28.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
      // Header + 12h/24h toggle pill
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp),
          modifier = Modifier.weight(1f)
        ) {
          Box(
            modifier = Modifier
              .size(38.dp)
              .clip(RoundedCornerShape(12.dp))
              .background(accent.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = Icons.Rounded.Schedule,
              contentDescription = null,
              tint = accent,
              modifier = Modifier.size(20.dp)
            )
          }
          Column {
            Text(
              text = stringResource(id = R.string.standby_clock_picker_title),
              style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
              color = Color.White
            )
            Text(
              text = stringResource(id = R.string.standby_clock_picker_subtitle),
              style = MaterialTheme.typography.bodySmall,
              color = Color.White.copy(alpha = 0.62f)
            )
          }
        }

        // 12h / 24h segmented glass switch
        Surface(
          shape = CircleShape,
          color = Color.White.copy(alpha = 0.08f),
          border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f))
        ) {
          Row(modifier = Modifier.padding(3.dp)) {
            listOf(
              false to stringResource(id = R.string.standby_time_12h),
              true to stringResource(id = R.string.standby_time_24h)
            ).forEach { (is24, label) ->
              val active = use24h == is24
              Box(
                modifier = Modifier
                  .clip(CircleShape)
                  .background(if (active) accent.copy(alpha = 0.28f) else Color.Transparent)
                  .clickable { onToggleUse24h(is24) }
                  .testTag(if (is24) "standby_24h_toggle" else "standby_12h_toggle")
                  .padding(horizontal = 12.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
              ) {
                Text(
                  text = label,
                  style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                  color = if (active) Color.White else Color.White.copy(alpha = 0.65f)
                )
              }
            }
          }
        }

        Spacer(modifier = Modifier.width(6.dp))

        IconButton(
          onClick = onDismiss,
          modifier = Modifier.minimumInteractiveComponentSize()
        ) {
          Icon(
            imageVector = Icons.Rounded.Close,
            contentDescription = stringResource(id = R.string.standby_exit_cd),
            tint = Color.White.copy(alpha = 0.78f)
          )
        }
      }

      // 2-column grid of all 10 clock styles
      LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier
          .fillMaxWidth()
          .height(370.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(vertical = 4.dp)
      ) {
        items(StandbyClockStyle.entries, key = { it.id }) { styleItem ->
          val isSelected = styleItem == selectedStyle
          val cardShape = RoundedCornerShape(18.dp)
          Surface(
            onClick = { onSelectStyle(styleItem) },
            shape = cardShape,
            color = if (isSelected) accent.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.06f),
            border = BorderStroke(
              width = if (isSelected) 1.5.dp else 1.dp,
              color = if (isSelected) accent else Color.White.copy(alpha = 0.12f)
            ),
            modifier = Modifier.testTag("standby_clock_style_${styleItem.id}")
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 13.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
              ) {
                Text(
                  text = styleItem.displayName,
                  style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                  color = if (isSelected) Color.White else Color.White.copy(alpha = 0.90f)
                )
                Text(
                  text = styleItem.tagLine,
                  style = MaterialTheme.typography.labelSmall,
                  color = Color.White.copy(alpha = 0.56f),
                  maxLines = 1
                )
              }
              if (isSelected) {
                Box(
                  modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(accent),
                  contentAlignment = Alignment.Center
                ) {
                  Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = Color(0xFF0A0D14),
                    modifier = Modifier.size(14.dp)
                  )
                }
              }
            }
          }
        }
      }
    }
  }
}
