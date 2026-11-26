package com.example.recap

import android.os.Build
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.glass.aurixGlassHazeStyle
import com.example.ui.theme.AurixFontFamilies
import com.example.ui.theme.AurixTheme
import com.example.ui.theme.KhmerTypography
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.LocalHazeStyle
import dev.chrisbanes.haze.haze
import dev.chrisbanes.haze.hazeChild
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

const val RECAP_CARD_COUNT = 6

private val RecapBaseDark = Color(0xFF080C14)
private val RecapCyan = Color(0xFF43C7F4)
private val RecapIndigo = Color(0xFF7965FF)
private val RecapPurple = Color(0xFFA855F7)

internal data class RecapCardPalette(
  val topGlow: Color,
  val bottomGlow: Color,
  val blob1: Color,
  val blob2: Color,
  val blob1CenterFraction: Offset,
  val blob2CenterFraction: Offset
)

internal fun paletteForRecapCard(cardIndex: Int, mood: OrbiMood): RecapCardPalette = when (cardIndex) {
  0 -> RecapCardPalette(
    topGlow = Color(0xFF10203A),
    bottomGlow = Color(0xFF140D28),
    blob1 = RecapCyan,
    blob2 = RecapIndigo,
    blob1CenterFraction = Offset(0.18f, 0.24f),
    blob2CenterFraction = Offset(0.84f, 0.72f)
  )
  1 -> RecapCardPalette(
    topGlow = Color(0xFF1A1438),
    bottomGlow = Color(0xFF0D1E34),
    blob1 = RecapPurple,
    blob2 = RecapCyan,
    blob1CenterFraction = Offset(0.82f, 0.22f),
    blob2CenterFraction = Offset(0.20f, 0.78f)
  )
  2 -> RecapCardPalette(
    topGlow = Color(0xFF0E2538),
    bottomGlow = Color(0xFF1D1134),
    blob1 = RecapCyan,
    blob2 = RecapPurple,
    blob1CenterFraction = Offset(0.24f, 0.30f),
    blob2CenterFraction = Offset(0.80f, 0.76f)
  )
  3 -> RecapCardPalette(
    topGlow = Color(0xFF14183C),
    bottomGlow = Color(0xFF0A2434),
    blob1 = RecapIndigo,
    blob2 = RecapCyan,
    blob1CenterFraction = Offset(0.76f, 0.26f),
    blob2CenterFraction = Offset(0.24f, 0.74f)
  )
  4 -> RecapCardPalette(
    topGlow = Color(0xFF1F1238),
    bottomGlow = Color(0xFF0F1B36),
    blob1 = RecapPurple,
    blob2 = RecapIndigo,
    blob1CenterFraction = Offset(0.20f, 0.25f),
    blob2CenterFraction = Offset(0.82f, 0.78f)
  )
  else -> RecapCardPalette(
    topGlow = Color(mood.primaryColorHex).copy(alpha = 0.28f),
    bottomGlow = Color(mood.secondaryColorHex).copy(alpha = 0.22f),
    blob1 = Color(mood.primaryColorHex),
    blob2 = Color(mood.secondaryColorHex),
    blob1CenterFraction = Offset(0.50f, 0.28f),
    blob2CenterFraction = Offset(0.52f, 0.78f)
  )
}

/**
 * Renders any of the 6 Listening Recap story cards (0..5) in a proportional 9:16 frame.
 * Every size scales from cardWidth / 360.dp so phone layouts and 1080x1920 exports match 1:1.
 */
@Composable
fun RecapStoryCard(
  cardIndex: Int,
  data: RecapData,
  modifier: Modifier = Modifier,
  reducedMotion: Boolean = false,
  isExportMode: Boolean = false
) {
  when (cardIndex.coerceIn(0, RECAP_CARD_COUNT - 1)) {
    0 -> RecapIntroCard(data, modifier, reducedMotion, isExportMode)
    1 -> RecapTopArtistCard(data, modifier, reducedMotion, isExportMode)
    2 -> RecapTopTrackCard(data, modifier, reducedMotion, isExportMode)
    3 -> RecapMinutesCard(data, modifier, reducedMotion, isExportMode)
    4 -> RecapPersonalityCard(data, modifier, reducedMotion, isExportMode)
    else -> RecapOrbiMoodCard(data, modifier, reducedMotion, isExportMode)
  }
}

@Composable
private fun RecapCardFrame(
  cardIndex: Int,
  periodLabel: String,
  kickerText: String,
  mood: OrbiMood,
  modifier: Modifier = Modifier,
  isExportMode: Boolean = false,
  content: @Composable ColumnScope.(scale: Float, hazeState: HazeState?) -> Unit
) {
  val palette = remember(cardIndex, mood) { paletteForRecapCard(cardIndex, mood) }
  val canUseHaze = !isExportMode && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
  val cardHazeState = remember(canUseHaze) { if (canUseHaze) HazeState() else null }
  val safeHazeStyle = remember { aurixGlassHazeStyle(darkTheme = true, backgroundColor = RecapBaseDark) }

  BoxWithConstraints(
    modifier = modifier
      .aspectRatio(9f / 16f)
      .clip(RoundedCornerShape(if (isExportMode) 0.dp else 24.dp))
      .background(RecapBaseDark)
  ) {
    val scale = (maxWidth.value / 360f).coerceIn(0.65f, 4f)
    val padH = (24f * scale).dp
    val padTop = (26f * scale).dp
    val padBottom = (22f * scale).dp

    // Background layer with dark AURIX gradient + 2 blurred glow blobs for glass refraction
    val bgModifier = if (cardHazeState != null) {
      Modifier.fillMaxSize().haze(state = cardHazeState)
    } else {
      Modifier.fillMaxSize()
    }

    Box(modifier = bgModifier) {
      Canvas(modifier = Modifier.fillMaxSize()) {
        drawRect(
          brush = Brush.verticalGradient(
            colors = listOf(
              RecapBaseDark,
              palette.topGlow.copy(alpha = 0.85f),
              RecapBaseDark,
              palette.bottomGlow.copy(alpha = 0.88f),
              RecapBaseDark
            )
          )
        )

        // Glow blob 1
        val c1 = Offset(size.width * palette.blob1CenterFraction.x, size.height * palette.blob1CenterFraction.y)
        val r1 = size.width * 0.72f
        drawCircle(
          brush = Brush.radialGradient(
            colors = listOf(
              palette.blob1.copy(alpha = 0.48f),
              palette.blob1.copy(alpha = 0.18f),
              Color.Transparent
            ),
            center = c1,
            radius = r1
          ),
          radius = r1,
          center = c1
        )

        // Glow blob 2
        val c2 = Offset(size.width * palette.blob2CenterFraction.x, size.height * palette.blob2CenterFraction.y)
        val r2 = size.width * 0.78f
        drawCircle(
          brush = Brush.radialGradient(
            colors = listOf(
              palette.blob2.copy(alpha = 0.44f),
              palette.blob2.copy(alpha = 0.16f),
              Color.Transparent
            ),
            center = c2,
            radius = r2
          ),
          radius = r2,
          center = c2
        )

        // Subtle orbital contour rings in background
        drawCircle(
          color = Color.White.copy(alpha = 0.045f),
          radius = size.width * 0.56f,
          center = Offset(size.width * 0.5f, size.height * 0.44f),
          style = Stroke(width = 1.2f * scale)
        )
        drawCircle(
          color = Color.White.copy(alpha = 0.03f),
          radius = size.width * 0.82f,
          center = Offset(size.width * 0.5f, size.height * 0.44f),
          style = Stroke(width = 1f * scale)
        )
      }
    }

    // Foreground content layer
    CompositionLocalProvider(LocalHazeStyle provides safeHazeStyle) {
      Column(
        modifier = Modifier
          .fillMaxSize()
          .padding(start = padH, end = padH, top = padTop, bottom = padBottom),
        verticalArrangement = Arrangement.SpaceBetween
      ) {
        // Shared top bar: AURIX · MUSIC DNA + period label
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          RecapText(
            text = stringResource(id = R.string.recap_brand_header),
            fontSize = (11f * scale).sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color.White.copy(alpha = 0.86f),
            letterSpacing = (1.4f * scale).sp,
            maxLines = 1
          )
          Box(
            modifier = Modifier
              .clip(CircleShape)
              .background(Color.White.copy(alpha = 0.12f))
              .border((1f * scale).dp, Color.White.copy(alpha = 0.24f), CircleShape)
              .padding(horizontal = (10f * scale).dp, vertical = (4f * scale).dp)
          ) {
            RecapText(
              text = periodLabel,
              fontSize = (10.5f * scale).sp,
              fontWeight = FontWeight.Bold,
              color = Color.White.copy(alpha = 0.94f),
              maxLines = 1
            )
          }
        }

        // Main card body
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
            .padding(vertical = (14f * scale).dp),
          verticalArrangement = Arrangement.Center
        ) {
          // Bilingual Khmer + English kicker
          RecapText(
            text = kickerText,
            fontSize = (11.5f * scale).sp,
            fontWeight = FontWeight.Bold,
            color = RecapCyan.copy(alpha = 0.96f),
            letterSpacing = (0.8f * scale).sp,
            maxLines = 2
          )
          Spacer(modifier = Modifier.height((10f * scale).dp))
          content(scale, cardHazeState)
        }

        // Shared footer
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.Center,
          verticalAlignment = Alignment.CenterVertically
        ) {
          RecapText(
            text = stringResource(id = R.string.recap_footer_tagline),
            fontSize = (10f * scale).sp,
            fontWeight = FontWeight.Bold,
            color = Color.White.copy(alpha = 0.56f),
            letterSpacing = (1.8f * scale).sp,
            maxLines = 1
          )
        }
      }
    }
  }
}

/**
 * Liquid glass panel: 28dp scaled radius, 30% -> 10% translucent white gradient,
 * 1.5dp light border, inner top highlight, soft shadow, and Haze backdrop blur on Android 12+.
 */
@Composable
private fun RecapGlassPanel(
  scale: Float,
  hazeState: HazeState?,
  isExportMode: Boolean,
  modifier: Modifier = Modifier,
  content: @Composable ColumnScope.() -> Unit
) {
  val corner = (28f * scale).dp
  val shape = RoundedCornerShape(corner)
  val useLiveBlur = hazeState != null && !isExportMode && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
  // Higher-opacity frosted glass fill for < API 31 fallback and deterministic PNG export
  val topAlpha = if (useLiveBlur) 0.28f else 0.24f
  val bottomAlpha = if (useLiveBlur) 0.09f else 0.11f

  val baseModifier = modifier
    .fillMaxWidth()
    .shadow(
      elevation = (16f * scale).dp,
      shape = shape,
      ambientColor = Color.Black.copy(alpha = 0.45f),
      spotColor = Color.Black.copy(alpha = 0.50f)
    )
    .clip(shape)

  val blurModifier = if (useLiveBlur && hazeState != null) {
    baseModifier.hazeChild(
      state = hazeState,
      style = aurixGlassHazeStyle(darkTheme = true, backgroundColor = RecapBaseDark)
    )
  } else {
    baseModifier.background(Color(0xFF0E1525).copy(alpha = 0.58f))
  }

  Box(
    modifier = blurModifier
      .background(
        Brush.verticalGradient(
          colors = listOf(
            Color.White.copy(alpha = topAlpha),
            Color.White.copy(alpha = bottomAlpha)
          )
        )
      )
      .border(
        width = (1.5f * scale).dp,
        brush = Brush.verticalGradient(
          colors = listOf(
            Color.White.copy(alpha = 0.48f),
            Color.White.copy(alpha = 0.14f)
          )
        ),
        shape = shape
      )
  ) {
    // Inner top specular highlight line
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .height((2f * scale).dp)
        .padding(horizontal = (24f * scale).dp)
        .align(Alignment.TopCenter)
        .background(
          Brush.horizontalGradient(
            colors = listOf(
              Color.Transparent,
              Color.White.copy(alpha = 0.65f),
              Color.Transparent
            )
          )
        )
    )

    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding((18f * scale).dp),
      verticalArrangement = Arrangement.spacedBy((12f * scale).dp),
      content = content
    )
  }
}

/**
 * Work Sans ExtraBold for Latin & Noto Sans Khmer for Khmer with automatic shrinking so
 * long artist/track names always fit cleanly on 1–2 lines without clipping Khmer vowels.
 */
@Composable
private fun AutoResizeRecapTitle(
  text: String,
  maxFontSize: TextUnit,
  minFontSize: TextUnit,
  maxLines: Int = 2,
  color: Color = Color.White,
  textAlign: TextAlign = TextAlign.Start,
  modifier: Modifier = Modifier
) {
  val hasKhmer = remember(text) { KhmerTypography.containsKhmer(text) }
  val family = if (hasKhmer) AurixFontFamilies.NotoSansKhmerFamily else AurixFontFamilies.WorkSansDisplayFamily
  var currentSize by remember(text, maxFontSize) { mutableStateOf(maxFontSize) }

  Text(
    text = text,
    modifier = modifier,
    color = color,
    textAlign = textAlign,
    maxLines = maxLines,
    softWrap = true,
    overflow = TextOverflow.Ellipsis,
    style = TextStyle(
      fontFamily = family,
      fontWeight = FontWeight.ExtraBold,
      fontSize = currentSize,
      lineHeight = (currentSize.value * if (hasKhmer) 1.34f else 1.12f).sp,
      platformStyle = PlatformTextStyle(includeFontPadding = true)
    ),
    onTextLayout = { result ->
      if ((result.hasVisualOverflow || result.lineCount > maxLines) && currentSize.value > minFontSize.value) {
        currentSize = (currentSize.value * 0.90f).coerceAtLeast(minFontSize.value).sp
      }
    }
  )
}

@Composable
private fun RecapText(
  text: String,
  fontSize: TextUnit,
  fontWeight: FontWeight = FontWeight.Medium,
  color: Color = Color.White,
  letterSpacing: TextUnit = TextUnit.Unspecified,
  maxLines: Int = Int.MAX_VALUE,
  textAlign: TextAlign = TextAlign.Start,
  modifier: Modifier = Modifier
) {
  val hasKhmer = remember(text) { KhmerTypography.containsKhmer(text) }
  val family = if (hasKhmer) AurixFontFamilies.NotoSansKhmerFamily else AurixFontFamilies.WorkSansDisplayFamily
  Text(
    text = text,
    modifier = modifier,
    color = color,
    maxLines = maxLines,
    textAlign = textAlign,
    overflow = TextOverflow.Ellipsis,
    style = TextStyle(
      fontFamily = family,
      fontWeight = fontWeight,
      fontSize = fontSize,
      lineHeight = (fontSize.value * if (hasKhmer) 1.42f else 1.28f).sp,
      letterSpacing = if (hasKhmer) TextUnit.Unspecified else letterSpacing,
      platformStyle = PlatformTextStyle(includeFontPadding = true)
    )
  )
}

/**
 * Floating Orbi mascot with mood-specific expression and glowing orbital ring.
 * Reads float translation ONLY inside graphicsLayer for zero recomposition at 60/120 Hz.
 */
@Composable
internal fun RecapOrbiMascot(
  mood: OrbiMood,
  size: Dp,
  scale: Float,
  reducedMotion: Boolean,
  isExportMode: Boolean,
  showOrbitRing: Boolean = true,
  modifier: Modifier = Modifier
) {
  val animateFloat = !reducedMotion && !isExportMode
  val infiniteTransition = rememberInfiniteTransition(label = "recap_orbi_float")
  val floatPhase by if (animateFloat) {
    infiniteTransition.animateFloat(
      initialValue = -1f,
      targetValue = 1f,
      animationSpec = infiniteRepeatable(
        animation = tween(durationMillis = 2800, easing = FastOutSlowInEasing),
        repeatMode = RepeatMode.Reverse
      ),
      label = "orbi_bob"
    )
  } else {
    remember { mutableFloatStateOf(0f) }
  }

  val primary = Color(mood.primaryColorHex)
  val secondary = Color(mood.secondaryColorHex)

  Box(
    modifier = modifier
      .size(size)
      .graphicsLayer {
        translationY = if (animateFloat) floatPhase * (6f * scale) * density else 0f
      },
    contentAlignment = Alignment.Center
  ) {
    Canvas(modifier = Modifier.fillMaxSize()) {
      val cx = this.size.width / 2f
      val cy = this.size.height / 2f
      val minDim = this.size.minDimension
      val bodyR = minDim * 0.30f

      // Ambient halo
      drawCircle(
        brush = Brush.radialGradient(
          colors = listOf(primary.copy(alpha = 0.50f), secondary.copy(alpha = 0.22f), Color.Transparent),
          center = Offset(cx, cy),
          radius = minDim * 0.50f
        ),
        radius = minDim * 0.50f,
        center = Offset(cx, cy)
      )

      // Glowing tilted orbit ring
      if (showOrbitRing) {
        rotate(degrees = -18f, pivot = Offset(cx, cy)) {
          val rx = minDim * 0.44f
          val ry = minDim * 0.16f
          drawOval(
            brush = Brush.linearGradient(
              colors = listOf(primary.copy(alpha = 0.95f), Color.White.copy(alpha = 0.85f), secondary.copy(alpha = 0.95f)),
              start = Offset(cx - rx, cy),
              end = Offset(cx + rx, cy)
            ),
            topLeft = Offset(cx - rx, cy - ry),
            size = Size(rx * 2f, ry * 2f),
            style = Stroke(width = (3.2f * scale).dp.toPx(), cap = StrokeCap.Round)
          )
          // Orbiting sparkle star
          val starX = cx + rx * cos(0.45) .toFloat()
          val starY = cy + ry * sin(0.45).toFloat()
          drawCircle(
            color = Color.White,
            radius = (3.8f * scale).dp.toPx(),
            center = Offset(starX, starY)
          )
        }
      }

      // Main Orbi glass sphere body
      drawCircle(
        brush = Brush.radialGradient(
          colors = listOf(
            Color(0xFFFFFFFF),
            primary.copy(alpha = 0.85f),
            secondary.copy(alpha = 0.92f),
            Color(0xFF0B1226)
          ),
          center = Offset(cx - bodyR * 0.28f, cy - bodyR * 0.28f),
          radius = bodyR * 1.45f
        ),
        radius = bodyR,
        center = Offset(cx, cy)
      )

      // Inner dark visor face
      val visorW = bodyR * 1.24f
      val visorH = bodyR * 0.72f
      drawRoundRect(
        color = Color(0xFF0A1020).copy(alpha = 0.92f),
        topLeft = Offset(cx - visorW / 2f, cy - visorH / 2f + bodyR * 0.04f),
        size = Size(visorW, visorH),
        cornerRadius = CornerRadius(visorH * 0.5f, visorH * 0.5f)
      )

      // Specular highlight arc on top-left of sphere
      drawCircle(
        color = Color.White.copy(alpha = 0.42f),
        radius = bodyR * 0.16f,
        center = Offset(cx - bodyR * 0.36f, cy - bodyR * 0.44f)
      )

      // Expression eyes inside visor
      val eyeOffsetX = visorW * 0.22f
      val eyeY = cy + bodyR * 0.04f
      val eyeStroke = (2.8f * scale).dp.toPx()

      when (mood.expression) {
        OrbiExpression.DREAMY -> {
          // Gentle curved closed/happy crescent eyes
          listOf(-eyeOffsetX, eyeOffsetX).forEach { dx ->
            val path = Path().apply {
              moveTo(cx + dx - bodyR * 0.14f, eyeY + bodyR * 0.02f)
              quadraticTo(cx + dx, eyeY - bodyR * 0.13f, cx + dx + bodyR * 0.14f, eyeY + bodyR * 0.02f)
            }
            drawPath(path, color = RecapCyan, style = Stroke(width = eyeStroke, cap = StrokeCap.Round))
          }
        }
        OrbiExpression.ELECTRIC -> {
          // Bright diamond / spark eyes
          listOf(-eyeOffsetX, eyeOffsetX).forEach { dx ->
            val r = bodyR * 0.13f
            val path = Path().apply {
              moveTo(cx + dx, eyeY - r)
              lineTo(cx + dx + r * 0.8f, eyeY)
              lineTo(cx + dx, eyeY + r)
              lineTo(cx + dx - r * 0.8f, eyeY)
              close()
            }
            drawPath(path, color = Color(0xFF7DF9FF))
          }
        }
        OrbiExpression.HYPED -> {
          // Energetic chevron > < eyes
          val r = bodyR * 0.12f
          val left = Path().apply {
            moveTo(cx - eyeOffsetX - r, eyeY - r * 0.8f)
            lineTo(cx - eyeOffsetX + r * 0.6f, eyeY)
            lineTo(cx - eyeOffsetX - r, eyeY + r * 0.8f)
          }
          val right = Path().apply {
            moveTo(cx + eyeOffsetX + r, eyeY - r * 0.8f)
            lineTo(cx + eyeOffsetX - r * 0.6f, eyeY)
            lineTo(cx + eyeOffsetX + r, eyeY + r * 0.8f)
          }
          drawPath(left, color = Color(0xFFFF7AA2), style = Stroke(width = eyeStroke, cap = StrokeCap.Round))
          drawPath(right, color = Color(0xFFFF7AA2), style = Stroke(width = eyeStroke, cap = StrokeCap.Round))
        }
        OrbiExpression.FOCUSED -> {
          // Calm glowing horizontal pill eyes + tiny headphone arcs
          listOf(-eyeOffsetX, eyeOffsetX).forEach { dx ->
            drawRoundRect(
              color = RecapCyan,
              topLeft = Offset(cx + dx - bodyR * 0.13f, eyeY - bodyR * 0.055f),
              size = Size(bodyR * 0.26f, bodyR * 0.11f),
              cornerRadius = CornerRadius(bodyR * 0.06f, bodyR * 0.06f)
            )
          }
        }
        OrbiExpression.NOSTALGIC -> {
          // Soft glowing starry dot eyes with warm violet tint
          listOf(-eyeOffsetX, eyeOffsetX).forEach { dx ->
            drawCircle(color = Color(0xFFF4B6FF), radius = bodyR * 0.11f, center = Offset(cx + dx, eyeY))
            drawCircle(color = Color.White, radius = bodyR * 0.045f, center = Offset(cx + dx - bodyR * 0.03f, eyeY - bodyR * 0.03f))
          }
        }
        OrbiExpression.CHILL -> {
          // Friendly rounded vertical capsule eyes
          listOf(-eyeOffsetX, eyeOffsetX).forEach { dx ->
            drawRoundRect(
              color = Color(0xFF7DF9FF),
              topLeft = Offset(cx + dx - bodyR * 0.08f, eyeY - bodyR * 0.12f),
              size = Size(bodyR * 0.16f, bodyR * 0.24f),
              cornerRadius = CornerRadius(bodyR * 0.08f, bodyR * 0.08f)
            )
          }
        }
      }
    }
  }
}

// -----------------------------------------------------------------------------
// CARD 1: INTRO
// -----------------------------------------------------------------------------
@Composable
fun RecapIntroCard(
  data: RecapData,
  modifier: Modifier = Modifier,
  reducedMotion: Boolean = false,
  isExportMode: Boolean = false
) {
  RecapCardFrame(
    cardIndex = 0,
    periodLabel = data.label,
    kickerText = stringResource(id = R.string.recap_kicker_intro),
    mood = data.mood,
    modifier = modifier,
    isExportMode = isExportMode
  ) { scale, hazeState ->
    Column(
      modifier = Modifier.fillMaxWidth(),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy((18f * scale).dp)
    ) {
      RecapOrbiMascot(
        mood = data.mood,
        size = (156f * scale).dp,
        scale = scale,
        reducedMotion = reducedMotion,
        isExportMode = isExportMode,
        showOrbitRing = true
      )

      val titleText = if (data.period == RecapPeriod.YEARLY) {
        stringResource(id = R.string.recap_intro_title_year)
      } else {
        stringResource(id = R.string.recap_intro_title_month)
      }

      AutoResizeRecapTitle(
        text = titleText,
        maxFontSize = (36f * scale).sp,
        minFontSize = (24f * scale).sp,
        maxLines = 2,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
      )

      RecapGlassPanel(
        scale = scale,
        hazeState = hazeState,
        isExportMode = isExportMode
      ) {
        Column(
          modifier = Modifier.fillMaxWidth(),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy((6f * scale).dp)
        ) {
          RecapText(
            text = data.label,
            fontSize = (20f * scale).sp,
            fontWeight = FontWeight.ExtraBold,
            color = RecapCyan,
            textAlign = TextAlign.Center,
            maxLines = 1
          )
          RecapText(
            text = if (data.hasRealData) {
              stringResource(id = R.string.recap_intro_subtitle)
            } else {
              stringResource(id = R.string.recap_empty_hint)
            },
            fontSize = (13f * scale).sp,
            fontWeight = FontWeight.Medium,
            color = Color.White.copy(alpha = 0.80f),
            textAlign = TextAlign.Center,
            maxLines = 3
          )
        }
      }
    }
  }
}

// -----------------------------------------------------------------------------
// CARD 2: TOP ARTIST
// -----------------------------------------------------------------------------
@Composable
fun RecapTopArtistCard(
  data: RecapData,
  modifier: Modifier = Modifier,
  reducedMotion: Boolean = false,
  isExportMode: Boolean = false
) {
  val lead = data.topArtists.firstOrNull()
  RecapCardFrame(
    cardIndex = 1,
    periodLabel = data.label,
    kickerText = stringResource(id = R.string.recap_kicker_top_artist),
    mood = data.mood,
    modifier = modifier,
    isExportMode = isExportMode
  ) { scale, hazeState ->
    AutoResizeRecapTitle(
      text = lead?.name ?: "AURIX",
      maxFontSize = (40f * scale).sp,
      minFontSize = (24f * scale).sp,
      maxLines = 2,
      modifier = Modifier.fillMaxWidth()
    )

    Spacer(modifier = Modifier.height((8f * scale).dp))

    val subtitle = when {
      lead == null -> stringResource(id = R.string.recap_empty_hint)
      lead.isTopOnePercent -> stringResource(id = R.string.recap_top_artist_top_one_percent, lead.plays)
      else -> stringResource(id = R.string.recap_top_artist_plays_only, lead.plays)
    }
    RecapText(
      text = subtitle,
      fontSize = (15f * scale).sp,
      fontWeight = FontWeight.SemiBold,
      color = Color.White.copy(alpha = 0.86f),
      maxLines = 2
    )

    Spacer(modifier = Modifier.height((22f * scale).dp))

    RecapGlassPanel(
      scale = scale,
      hazeState = hazeState,
      isExportMode = isExportMode
    ) {
      RecapText(
        text = stringResource(id = R.string.recap_top_artists_panel_title),
        fontSize = (12.5f * scale).sp,
        fontWeight = FontWeight.Bold,
        color = Color.White.copy(alpha = 0.72f),
        maxLines = 1
      )

      val maxPlays = (data.topArtists.maxOfOrNull { it.plays } ?: 1).coerceAtLeast(1)
      if (data.topArtists.isEmpty()) {
        RecapText(
          text = stringResource(id = R.string.recap_empty_hint),
          fontSize = (13f * scale).sp,
          color = Color.White.copy(alpha = 0.75f),
          maxLines = 2
        )
      } else {
        data.topArtists.take(3).forEachIndexed { idx, artist ->
          val fraction = (artist.plays.toFloat() / maxPlays.toFloat()).coerceIn(0.14f, 1f)
          Column(verticalArrangement = Arrangement.spacedBy((5f * scale).dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              RecapText(
                text = "${idx + 1}. ${artist.name}",
                fontSize = (14f * scale).sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                modifier = Modifier.weight(1f)
              )
              Spacer(modifier = Modifier.width((8f * scale).dp))
              RecapText(
                text = stringResource(id = R.string.recap_plays_count, artist.plays),
                fontSize = (12f * scale).sp,
                fontWeight = FontWeight.SemiBold,
                color = RecapCyan,
                maxLines = 1
              )
            }
            Box(
              modifier = Modifier
                .fillMaxWidth()
                .height((8f * scale).dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.12f))
            ) {
              Box(
                modifier = Modifier
                  .fillMaxWidth(fraction)
                  .fillMaxHeight()
                  .clip(CircleShape)
                  .background(
                    Brush.horizontalGradient(
                      colors = when (idx) {
                        0 -> listOf(RecapCyan, RecapIndigo)
                        1 -> listOf(RecapIndigo, RecapPurple)
                        else -> listOf(RecapPurple, RecapCyan.copy(alpha = 0.8f))
                      }
                    )
                  )
              )
            }
          }
        }
      }
    }
  }
}

// -----------------------------------------------------------------------------
// CARD 3: TOP TRACK
// -----------------------------------------------------------------------------
@Composable
fun RecapTopTrackCard(
  data: RecapData,
  modifier: Modifier = Modifier,
  reducedMotion: Boolean = false,
  isExportMode: Boolean = false
) {
  val track = data.topTrack
  RecapCardFrame(
    cardIndex = 2,
    periodLabel = data.label,
    kickerText = stringResource(id = R.string.recap_kicker_top_track),
    mood = data.mood,
    modifier = modifier,
    isExportMode = isExportMode
  ) { scale, hazeState ->
    AutoResizeRecapTitle(
      text = track.title,
      maxFontSize = (38f * scale).sp,
      minFontSize = (22f * scale).sp,
      maxLines = 2,
      modifier = Modifier.fillMaxWidth()
    )

    Spacer(modifier = Modifier.height((6f * scale).dp))

    RecapText(
      text = track.artist,
      fontSize = (16f * scale).sp,
      fontWeight = FontWeight.SemiBold,
      color = Color.White.copy(alpha = 0.80f),
      maxLines = 1
    )

    Spacer(modifier = Modifier.height((12f * scale).dp))

    Box(
      modifier = Modifier
        .clip(CircleShape)
        .background(RecapCyan.copy(alpha = 0.22f))
        .border((1f * scale).dp, RecapCyan.copy(alpha = 0.55f), CircleShape)
        .padding(horizontal = (14f * scale).dp, vertical = (6f * scale).dp)
    ) {
      RecapText(
        text = stringResource(id = R.string.recap_plays_count, track.plays),
        fontSize = (13.5f * scale).sp,
        fontWeight = FontWeight.ExtraBold,
        color = Color.White,
        maxLines = 1
      )
    }

    Spacer(modifier = Modifier.height((24f * scale).dp))

    RecapGlassPanel(
      scale = scale,
      hazeState = hazeState,
      isExportMode = isExportMode
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy((10f * scale).dp)
      ) {
        RecapMetricColumn(
          label = stringResource(id = R.string.recap_stat_best_day),
          value = track.bestDay,
          scale = scale,
          modifier = Modifier.weight(1f)
        )
        RecapMetricColumn(
          label = stringResource(id = R.string.recap_stat_longest_streak),
          value = stringResource(id = R.string.recap_streak_days, track.longestStreak),
          scale = scale,
          modifier = Modifier.weight(1f)
        )
        RecapMetricColumn(
          label = stringResource(id = R.string.recap_stat_replays),
          value = "${track.replays}x",
          scale = scale,
          modifier = Modifier.weight(1f)
        )
      }
    }
  }
}

// -----------------------------------------------------------------------------
// CARD 4: MINUTES LISTENED
// -----------------------------------------------------------------------------
@Composable
fun RecapMinutesCard(
  data: RecapData,
  modifier: Modifier = Modifier,
  reducedMotion: Boolean = false,
  isExportMode: Boolean = false
) {
  val hoursFormatted = remember(data.totalMinutes) {
    String.format(Locale.US, "%.1f", data.totalMinutes / 60f)
  }
  val albumsApprox = remember(data.totalMinutes) {
    (data.totalMinutes / 40f).roundToInt().coerceAtLeast(if (data.totalMinutes > 0) 1 else 0)
  }
  val minutesFormatted = remember(data.totalMinutes) {
    String.format(Locale.US, "%,d", data.totalMinutes)
  }

  RecapCardFrame(
    cardIndex = 3,
    periodLabel = data.label,
    kickerText = stringResource(id = R.string.recap_kicker_minutes),
    mood = data.mood,
    modifier = modifier,
    isExportMode = isExportMode
  ) { scale, hazeState ->
    AutoResizeRecapTitle(
      text = minutesFormatted,
      maxFontSize = (64f * scale).sp,
      minFontSize = (38f * scale).sp,
      maxLines = 1,
      color = Color.White,
      modifier = Modifier.fillMaxWidth()
    )

    RecapText(
      text = stringResource(id = R.string.recap_minutes_unit),
      fontSize = (20f * scale).sp,
      fontWeight = FontWeight.Bold,
      color = RecapCyan,
      maxLines = 1
    )

    Spacer(modifier = Modifier.height((8f * scale).dp))

    RecapText(
      text = stringResource(id = R.string.recap_minutes_comparison, hoursFormatted, albumsApprox),
      fontSize = (14.5f * scale).sp,
      fontWeight = FontWeight.Medium,
      color = Color.White.copy(alpha = 0.84f),
      maxLines = 2
    )

    Spacer(modifier = Modifier.height((24f * scale).dp))

    RecapGlassPanel(
      scale = scale,
      hazeState = hazeState,
      isExportMode = isExportMode
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy((10f * scale).dp)
      ) {
        RecapMetricColumn(
          label = stringResource(id = R.string.recap_stat_daily_avg),
          value = stringResource(id = R.string.recap_daily_avg_value, data.dailyAverage),
          scale = scale,
          modifier = Modifier.weight(1f)
        )
        RecapMetricColumn(
          label = stringResource(id = R.string.recap_stat_top_day),
          value = data.topDay,
          scale = scale,
          modifier = Modifier.weight(1f)
        )
        RecapMetricColumn(
          label = stringResource(id = R.string.recap_stat_new_artists),
          value = "${data.newArtists}",
          scale = scale,
          modifier = Modifier.weight(1f)
        )
      }
    }
  }
}

// -----------------------------------------------------------------------------
// CARD 5: LISTENING PERSONALITY
// -----------------------------------------------------------------------------
@Composable
fun RecapPersonalityCard(
  data: RecapData,
  modifier: Modifier = Modifier,
  reducedMotion: Boolean = false,
  isExportMode: Boolean = false
) {
  val personality = data.personality
  RecapCardFrame(
    cardIndex = 4,
    periodLabel = data.label,
    kickerText = stringResource(id = R.string.recap_kicker_personality),
    mood = data.mood,
    modifier = modifier,
    isExportMode = isExportMode
  ) { scale, hazeState ->
    AutoResizeRecapTitle(
      text = stringResource(id = personality.nameRes),
      maxFontSize = (38f * scale).sp,
      minFontSize = (24f * scale).sp,
      maxLines = 2,
      modifier = Modifier.fillMaxWidth()
    )

    Spacer(modifier = Modifier.height((8f * scale).dp))

    RecapText(
      text = stringResource(id = personality.descriptionRes),
      fontSize = (14.5f * scale).sp,
      fontWeight = FontWeight.Medium,
      color = Color.White.copy(alpha = 0.85f),
      maxLines = 3
    )

    Spacer(modifier = Modifier.height((22f * scale).dp))

    RecapGlassPanel(
      scale = scale,
      hazeState = hazeState,
      isExportMode = isExportMode
    ) {
      personality.traits.take(3).forEachIndexed { idx, trait ->
        val pct = trait.percent.coerceIn(0, 100)
        Column(verticalArrangement = Arrangement.spacedBy((6f * scale).dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            RecapText(
              text = stringResource(id = trait.labelRes),
              fontSize = (13.5f * scale).sp,
              fontWeight = FontWeight.Bold,
              color = Color.White,
              maxLines = 1
            )
            RecapText(
              text = "$pct%",
              fontSize = (13.5f * scale).sp,
              fontWeight = FontWeight.ExtraBold,
              color = RecapCyan,
              maxLines = 1
            )
          }
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .height((9f * scale).dp)
              .clip(CircleShape)
              .background(Color.White.copy(alpha = 0.13f))
          ) {
            Box(
              modifier = Modifier
                .fillMaxWidth((pct / 100f).coerceIn(0.08f, 1f))
                .fillMaxHeight()
                .clip(CircleShape)
                .background(
                  Brush.horizontalGradient(
                    colors = when (idx) {
                      0 -> listOf(RecapCyan, RecapIndigo)
                      1 -> listOf(RecapIndigo, RecapPurple)
                      else -> listOf(RecapPurple, RecapCyan)
                    }
                  )
                )
            )
          }
        }
      }
    }
  }
}

// -----------------------------------------------------------------------------
// CARD 6: ORBI MOOD BADGE + SUMMARY
// -----------------------------------------------------------------------------
@Composable
fun RecapOrbiMoodCard(
  data: RecapData,
  modifier: Modifier = Modifier,
  reducedMotion: Boolean = false,
  isExportMode: Boolean = false
) {
  val mood = data.mood
  val chipText = if (data.period == RecapPeriod.YEARLY) {
    stringResource(id = R.string.recap_mood_chip_year)
  } else {
    stringResource(id = R.string.recap_mood_chip_month)
  }

  RecapCardFrame(
    cardIndex = 5,
    periodLabel = data.label,
    kickerText = stringResource(id = R.string.recap_kicker_orbi_mood),
    mood = mood,
    modifier = modifier,
    isExportMode = isExportMode
  ) { scale, hazeState ->
    Column(
      modifier = Modifier.fillMaxWidth(),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy((10f * scale).dp)
    ) {
      RecapOrbiMascot(
        mood = mood,
        size = (146f * scale).dp,
        scale = scale,
        reducedMotion = reducedMotion,
        isExportMode = isExportMode,
        showOrbitRing = true
      )

      Box(
        modifier = Modifier
          .clip(CircleShape)
          .background(Color(mood.primaryColorHex).copy(alpha = 0.24f))
          .border((1f * scale).dp, Color.White.copy(alpha = 0.35f), CircleShape)
          .padding(horizontal = (12f * scale).dp, vertical = (5f * scale).dp)
      ) {
        RecapText(
          text = chipText,
          fontSize = (11.5f * scale).sp,
          fontWeight = FontWeight.Bold,
          color = Color.White,
          maxLines = 1
        )
      }

      AutoResizeRecapTitle(
        text = "${mood.emoji} ${stringResource(id = mood.nameRes)}",
        maxFontSize = (36f * scale).sp,
        minFontSize = (24f * scale).sp,
        maxLines = 1,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
      )

      Spacer(modifier = Modifier.height((6f * scale).dp))

      RecapGlassPanel(
        scale = scale,
        hazeState = hazeState,
        isExportMode = isExportMode
      ) {
        RecapSummaryRow(
          label = stringResource(id = R.string.recap_summary_artist),
          value = data.topArtists.firstOrNull()?.name ?: "—",
          scale = scale
        )
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .height((1f * scale).dp)
            .background(Color.White.copy(alpha = 0.14f))
        )
        RecapSummaryRow(
          label = stringResource(id = R.string.recap_summary_track),
          value = data.topTrack.title,
          scale = scale
        )
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .height((1f * scale).dp)
            .background(Color.White.copy(alpha = 0.14f))
        )
        RecapSummaryRow(
          label = stringResource(id = R.string.recap_summary_minutes),
          value = "${String.format(Locale.US, "%,d", data.totalMinutes)} ${stringResource(id = R.string.recap_minutes_unit)}",
          scale = scale
        )
      }
    }
  }
}

@Composable
private fun RecapMetricColumn(
  label: String,
  value: String,
  scale: Float,
  modifier: Modifier = Modifier
) {
  Column(
    modifier = modifier,
    verticalArrangement = Arrangement.spacedBy((4f * scale).dp)
  ) {
    RecapText(
      text = label,
      fontSize = (11f * scale).sp,
      fontWeight = FontWeight.Medium,
      color = Color.White.copy(alpha = 0.68f),
      maxLines = 1
    )
    RecapText(
      text = value,
      fontSize = (16f * scale).sp,
      fontWeight = FontWeight.ExtraBold,
      color = Color.White,
      maxLines = 1
    )
  }
}

@Composable
private fun RecapSummaryRow(
  label: String,
  value: String,
  scale: Float
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
  ) {
    RecapText(
      text = label,
      fontSize = (12.5f * scale).sp,
      fontWeight = FontWeight.Medium,
      color = Color.White.copy(alpha = 0.72f),
      maxLines = 1
    )
    Spacer(modifier = Modifier.width((12f * scale).dp))
    RecapText(
      text = value,
      fontSize = (14f * scale).sp,
      fontWeight = FontWeight.ExtraBold,
      color = Color.White,
      maxLines = 1,
      textAlign = TextAlign.End,
      modifier = Modifier.weight(1f)
    )
  }
}

// -----------------------------------------------------------------------------
// SAMPLE DATA FOR @Preview ONLY (Never used at runtime)
// -----------------------------------------------------------------------------
internal val PREVIEW_SAMPLE_RECAP = RecapData(
  period = RecapPeriod.MONTHLY,
  periodKey = "2026-11",
  label = "November 2026",
  topArtists = listOf(
    ArtistStat(name = "VannDa · វណ្ណដា", plays = 412, sharePercent = 48, isTopOnePercent = true),
    ArtistStat(name = "The Weeknd", plays = 214, sharePercent = 25, isTopOnePercent = false),
    ArtistStat(name = "NIKI", plays = 128, sharePercent = 15, isTopOnePercent = false)
  ),
  topTrack = TrackStat(
    title = "Time To Rise",
    artist = "VannDa · វណ្ណដា",
    plays = 148,
    bestDay = "Nov 14",
    longestStreak = 11,
    replays = 147
  ),
  totalMinutes = 3420,
  dailyAverage = 114,
  topDay = "Nov 14",
  newArtists = 19,
  totalPlays = 858,
  hasRealData = true,
  personality = Personality(
    type = PersonalityType.NIGHT_OWL,
    traits = listOf(
      TraitMeter(R.string.recap_trait_night_energy, "Night Energy", 88),
      TraitMeter(R.string.recap_trait_loyalty, "Loyalty", 76),
      TraitMeter(R.string.recap_trait_endurance, "Endurance", 91)
    )
  ),
  mood = OrbiMood.DREAMY
)

@Preview(name = "Card 1 Intro - Dark", widthDp = 360, heightDp = 640)
@Composable
private fun PreviewRecapIntroDark() {
  AurixTheme(darkTheme = true) {
    RecapIntroCard(data = PREVIEW_SAMPLE_RECAP, isExportMode = true)
  }
}

@Preview(name = "Card 1 Intro - Light", widthDp = 360, heightDp = 640)
@Composable
private fun PreviewRecapIntroLight() {
  AurixTheme(darkTheme = false) {
    RecapIntroCard(data = PREVIEW_SAMPLE_RECAP, isExportMode = true)
  }
}

@Preview(name = "Card 2 Top Artist - Dark", widthDp = 360, heightDp = 640)
@Composable
private fun PreviewRecapTopArtistDark() {
  AurixTheme(darkTheme = true) {
    RecapTopArtistCard(data = PREVIEW_SAMPLE_RECAP, isExportMode = true)
  }
}

@Preview(name = "Card 2 Top Artist - Light", widthDp = 360, heightDp = 640)
@Composable
private fun PreviewRecapTopArtistLight() {
  AurixTheme(darkTheme = false) {
    RecapTopArtistCard(data = PREVIEW_SAMPLE_RECAP, isExportMode = true)
  }
}

@Preview(name = "Card 3 Top Track - Dark", widthDp = 360, heightDp = 640)
@Composable
private fun PreviewRecapTopTrackDark() {
  AurixTheme(darkTheme = true) {
    RecapTopTrackCard(data = PREVIEW_SAMPLE_RECAP, isExportMode = true)
  }
}

@Preview(name = "Card 3 Top Track - Light", widthDp = 360, heightDp = 640)
@Composable
private fun PreviewRecapTopTrackLight() {
  AurixTheme(darkTheme = false) {
    RecapTopTrackCard(data = PREVIEW_SAMPLE_RECAP, isExportMode = true)
  }
}

@Preview(name = "Card 4 Minutes - Dark", widthDp = 360, heightDp = 640)
@Composable
private fun PreviewRecapMinutesDark() {
  AurixTheme(darkTheme = true) {
    RecapMinutesCard(data = PREVIEW_SAMPLE_RECAP, isExportMode = true)
  }
}

@Preview(name = "Card 4 Minutes - Light", widthDp = 360, heightDp = 640)
@Composable
private fun PreviewRecapMinutesLight() {
  AurixTheme(darkTheme = false) {
    RecapMinutesCard(data = PREVIEW_SAMPLE_RECAP, isExportMode = true)
  }
}

@Preview(name = "Card 5 Personality - Dark", widthDp = 360, heightDp = 640)
@Composable
private fun PreviewRecapPersonalityDark() {
  AurixTheme(darkTheme = true) {
    RecapPersonalityCard(data = PREVIEW_SAMPLE_RECAP, isExportMode = true)
  }
}

@Preview(name = "Card 5 Personality - Light", widthDp = 360, heightDp = 640)
@Composable
private fun PreviewRecapPersonalityLight() {
  AurixTheme(darkTheme = false) {
    RecapPersonalityCard(data = PREVIEW_SAMPLE_RECAP, isExportMode = true)
  }
}

@Preview(name = "Card 6 Orbi Mood - Dark", widthDp = 360, heightDp = 640)
@Composable
private fun PreviewRecapOrbiMoodDark() {
  AurixTheme(darkTheme = true) {
    RecapOrbiMoodCard(data = PREVIEW_SAMPLE_RECAP, isExportMode = true)
  }
}

@Preview(name = "Card 6 Orbi Mood - Light", widthDp = 360, heightDp = 640)
@Composable
private fun PreviewRecapOrbiMoodLight() {
  AurixTheme(darkTheme = false) {
    RecapOrbiMoodCard(data = PREVIEW_SAMPLE_RECAP, isExportMode = true)
  }
}
