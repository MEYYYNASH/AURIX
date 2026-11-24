package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.NightlightRound
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.WorkspacePremium
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.Song
import kotlinx.coroutines.delay
import java.util.Calendar

enum class OrbiMood(val id: String) {
  LOFI_HEADPHONES("lofi"),
  PHONK_SHADES("phonk"),
  NIGHT_SLEEPY("night"),
  ROMANTIC_HEART("romantic"),
  PARTY_SPARK("party"),
  CLASSIC_IDLE("idle");

  fun labelRes(): Int = when (this) {
    LOFI_HEADPHONES -> R.string.orbi_mood_lofi
    PHONK_SHADES -> R.string.orbi_mood_phonk
    NIGHT_SLEEPY -> R.string.orbi_mood_night
    ROMANTIC_HEART -> R.string.orbi_mood_romantic
    PARTY_SPARK -> R.string.orbi_mood_party
    CLASSIC_IDLE -> R.string.orbi_mood_idle
  }
}

enum class OrbiCompanionTheme(
  val id: String,
  val primary: Color,
  val secondary: Color,
  val ringColor: Color,
  val badgeIdRequired: String?
) {
  AURORA(
    id = "aurora",
    primary = Color(0xFF43C7F4),
    secondary = Color(0xFF7965FF),
    ringColor = Color(0xFF43C7F4),
    badgeIdRequired = null
  ),
  MIDNIGHT(
    id = "midnight",
    primary = Color(0xFF6366F1),
    secondary = Color(0xFF1E1B4B),
    ringColor = Color(0xFF818CF8),
    badgeIdRequired = "night_owl"
  ),
  MINT_CLOUD(
    id = "mint",
    primary = Color(0xFF2DD4BF),
    secondary = Color(0xFF0EA5E9),
    ringColor = Color(0xFF5EEAD4),
    badgeIdRequired = "chill_drifter"
  ),
  ROSE_VELVET(
    id = "rose",
    primary = Color(0xFFFB7185),
    secondary = Color(0xFFA855F7),
    ringColor = Color(0xFFF472B6),
    badgeIdRequired = "heart_collector"
  ),
  SUNSET_GOLD(
    id = "sunset",
    primary = Color(0xFFF59E0B),
    secondary = Color(0xFFEF4444),
    ringColor = Color(0xFFFBBF24),
    badgeIdRequired = "artist_explorer"
  ),
  PRISMATIC(
    id = "prismatic",
    primary = Color(0xFF22D3EE),
    secondary = Color(0xFFEC4899),
    ringColor = Color(0xFFA855F7),
    badgeIdRequired = "aurix_legend"
  );

  fun labelRes(): Int = when (this) {
    AURORA -> R.string.orbi_theme_aurora
    MIDNIGHT -> R.string.orbi_theme_midnight
    MINT_CLOUD -> R.string.orbi_theme_mint
    ROSE_VELVET -> R.string.orbi_theme_rose
    SUNSET_GOLD -> R.string.orbi_theme_sunset
    PRISMATIC -> R.string.orbi_theme_prismatic
  }

  companion object {
    fun fromId(raw: String?): OrbiCompanionTheme =
      entries.firstOrNull { it.id.equals(raw, ignoreCase = true) } ?: AURORA
  }
}

data class OrbiMilestoneBadge(
  val id: String,
  val titleRes: Int,
  val requirementRes: Int,
  val unlocked: Boolean,
  val progressFraction: Float,
  val unlockedTheme: OrbiCompanionTheme,
  val accentColor: Color
)

fun resolveOrbiMood(
  song: Song?,
  isPlaying: Boolean,
  sleepTimerActive: Boolean = false,
  hourOfDay: Int = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
): OrbiMood {
  val isLateNight = hourOfDay >= 22 || hourOfDay < 5
  if (sleepTimerActive) return OrbiMood.NIGHT_SLEEPY
  if (song == null) {
    return if (isLateNight) OrbiMood.NIGHT_SLEEPY else OrbiMood.CLASSIC_IDLE
  }

  val haystack = "${song.title} ${song.artist} ${song.genre} ${song.mood}".lowercase()
  return when {
    haystack.contains("phonk") ||
      haystack.contains("drift") ||
      haystack.contains("trap") ||
      haystack.contains("drill") ||
      haystack.contains("bass boosted") ||
      (song.bpm >= 134 && song.energetic >= 0.65f) -> OrbiMood.PHONK_SHADES

    haystack.contains("lo-fi") ||
      haystack.contains("lofi") ||
      haystack.contains("chill") ||
      haystack.contains("study") ||
      haystack.contains("ambient") ||
      haystack.contains("sleep") ||
      haystack.contains("rain") ||
      song.chill >= 0.68f ||
      song.calm >= 0.72f -> OrbiMood.LOFI_HEADPHONES

    isLateNight && !isPlaying -> OrbiMood.NIGHT_SLEEPY

    haystack.contains("love") ||
      haystack.contains("romantic") ||
      haystack.contains("ballad") ||
      song.romantic >= 0.65f ||
      song.isFavorite -> OrbiMood.ROMANTIC_HEART

    isPlaying && (song.energetic >= 0.58f || song.happy >= 0.62f || song.bpm >= 118) -> OrbiMood.PARTY_SPARK

    isLateNight -> OrbiMood.NIGHT_SLEEPY

    else -> OrbiMood.CLASSIC_IDLE
  }
}

fun computeOrbiMilestoneBadges(
  totalPlays: Int,
  favoritesCount: Int,
  uniqueArtistsCount: Int,
  dominantMoodOrGenre: String = "",
  hourOfDay: Int = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
): List<OrbiMilestoneBadge> {
  val isNightNow = hourOfDay >= 22 || hourOfDay < 5
  val chillSignal = dominantMoodOrGenre.contains("chill", ignoreCase = true) ||
    dominantMoodOrGenre.contains("lo-fi", ignoreCase = true) ||
    dominantMoodOrGenre.contains("calm", ignoreCase = true)

  return listOf(
    OrbiMilestoneBadge(
      id = "first_beat",
      titleRes = R.string.orbi_badge_first_beat,
      requirementRes = R.string.orbi_badge_first_beat_req,
      unlocked = totalPlays >= 1,
      progressFraction = (totalPlays / 1f).coerceIn(0f, 1f),
      unlockedTheme = OrbiCompanionTheme.AURORA,
      accentColor = Color(0xFF43C7F4)
    ),
    OrbiMilestoneBadge(
      id = "night_owl",
      titleRes = R.string.orbi_badge_night_owl,
      requirementRes = R.string.orbi_badge_night_owl_req,
      unlocked = (isNightNow && totalPlays >= 1) || totalPlays >= 5,
      progressFraction = if (isNightNow && totalPlays >= 1) 1f else (totalPlays / 5f).coerceIn(0f, 1f),
      unlockedTheme = OrbiCompanionTheme.MIDNIGHT,
      accentColor = Color(0xFF818CF8)
    ),
    OrbiMilestoneBadge(
      id = "chill_drifter",
      titleRes = R.string.orbi_badge_chill_drifter,
      requirementRes = R.string.orbi_badge_chill_drifter_req,
      unlocked = totalPlays >= 10 || (chillSignal && totalPlays >= 1),
      progressFraction = if (chillSignal && totalPlays >= 1) 1f else (totalPlays / 10f).coerceIn(0f, 1f),
      unlockedTheme = OrbiCompanionTheme.MINT_CLOUD,
      accentColor = Color(0xFF2DD4BF)
    ),
    OrbiMilestoneBadge(
      id = "heart_collector",
      titleRes = R.string.orbi_badge_heart_collector,
      requirementRes = R.string.orbi_badge_heart_collector_req,
      unlocked = favoritesCount >= 5,
      progressFraction = (favoritesCount / 5f).coerceIn(0f, 1f),
      unlockedTheme = OrbiCompanionTheme.ROSE_VELVET,
      accentColor = Color(0xFFFB7185)
    ),
    OrbiMilestoneBadge(
      id = "artist_explorer",
      titleRes = R.string.orbi_badge_artist_explorer,
      requirementRes = R.string.orbi_badge_artist_explorer_req,
      unlocked = uniqueArtistsCount >= 5,
      progressFraction = (uniqueArtistsCount / 5f).coerceIn(0f, 1f),
      unlockedTheme = OrbiCompanionTheme.SUNSET_GOLD,
      accentColor = Color(0xFFF59E0B)
    ),
    OrbiMilestoneBadge(
      id = "aurix_legend",
      titleRes = R.string.orbi_badge_aurix_legend,
      requirementRes = R.string.orbi_badge_aurix_legend_req,
      unlocked = totalPlays >= 30,
      progressFraction = (totalPlays / 30f).coerceIn(0f, 1f),
      unlockedTheme = OrbiCompanionTheme.PRISMATIC,
      accentColor = Color(0xFFEC4899)
    )
  )
}

fun isOrbiThemeUnlocked(
  theme: OrbiCompanionTheme,
  badges: List<OrbiMilestoneBadge>
): Boolean {
  val req = theme.badgeIdRequired ?: return true
  return badges.any { it.id == req && it.unlocked }
}

/**
 * Clean, minimal interactive Orbi avatar that subtly wears the active music mood accessory
 * (Lo-fi headphones, Phonk shades, Night sleepy moon, Romantic heart glow, or Party spark).
 */
@Composable
fun OrbiCompanionAvatar(
  mood: OrbiMood,
  theme: OrbiCompanionTheme,
  isPlaying: Boolean,
  reducedMotion: Boolean,
  size: Dp = 44.dp,
  onClick: (() -> Unit)? = null,
  modifier: Modifier = Modifier
) {
  var tapped by remember { mutableStateOf(false) }
  LaunchedEffect(tapped) {
    if (tapped) {
      delay(160)
      tapped = false
    }
  }
  val tapScale by animateFloatAsState(
    targetValue = if (tapped) 0.90f else 1f,
    animationSpec = if (reducedMotion) snap() else spring(
      dampingRatio = Spring.DampingRatioMediumBouncy,
      stiffness = Spring.StiffnessMedium
    ),
    label = "orbi_tap_scale"
  )

  val infinite = rememberInfiniteTransition(label = "orbi_companion_float")
  val bob by infinite.animateFloat(
    initialValue = -1.6f,
    targetValue = 1.6f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = if (isPlaying) 1400 else 2800, easing = FastOutSlowInEasing),
      repeatMode = RepeatMode.Reverse
    ),
    label = "orbi_bob"
  )
  val pulse by infinite.animateFloat(
    initialValue = 0.72f,
    targetValue = 1.0f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = 2000, easing = FastOutSlowInEasing),
      repeatMode = RepeatMode.Reverse
    ),
    label = "orbi_glow_pulse"
  )
  val activeBob = if (reducedMotion) 0f else bob
  val activePulse = if (reducedMotion) 0.85f else pulse

  val cd = stringResource(id = R.string.orbi_companion_cd)
  val corner = size * 0.32f
  Box(
    modifier = modifier
      .size(size)
      .scale(tapScale)
      .clip(RoundedCornerShape(corner))
      .background(
        Brush.linearGradient(
          colors = listOf(
            theme.primary.copy(alpha = 0.24f),
            theme.secondary.copy(alpha = 0.30f)
          )
        )
      )
      .border(
        width = 1.dp,
        brush = Brush.linearGradient(
          colors = listOf(
            theme.primary.copy(alpha = 0.65f * activePulse),
            theme.secondary.copy(alpha = 0.45f)
          )
        ),
        shape = RoundedCornerShape(corner)
      )
      .then(
        if (onClick != null) {
          Modifier
            .clickable(role = Role.Button, onClickLabel = cd) {
              tapped = true
              onClick()
            }
            .semantics { contentDescription = cd }
        } else {
          Modifier
        }
      ),
    contentAlignment = Alignment.Center
  ) {
    Canvas(modifier = Modifier.fillMaxSize()) {
      drawOrbiCompanionCharacter(
        mood = mood,
        theme = theme,
        bobPx = activeBob * (this.size.minDimension / 44f),
        glowAlpha = activePulse
      )
    }
  }
}

private fun DrawScope.drawOrbiCompanionCharacter(
  mood: OrbiMood,
  theme: OrbiCompanionTheme,
  bobPx: Float,
  glowAlpha: Float
) {
  val w = size.width
  val h = size.height
  val cx = w * 0.5f
  val cy = h * 0.54f + bobPx
  val bodyR = minOf(w, h) * 0.29f

  // Soft ambient halo behind Orbi
  drawCircle(
    brush = Brush.radialGradient(
      colors = listOf(theme.primary.copy(alpha = 0.38f * glowAlpha), Color.Transparent),
      center = Offset(cx, cy),
      radius = bodyR * 1.95f
    ),
    radius = bodyR * 1.95f,
    center = Offset(cx, cy)
  )

  // Signature orbital ring behind Orbi
  drawOval(
    brush = Brush.linearGradient(listOf(theme.ringColor.copy(alpha = 0.85f), theme.secondary.copy(alpha = 0.75f))),
    topLeft = Offset(cx - bodyR * 1.32f, cy - bodyR * 0.34f),
    size = Size(bodyR * 2.64f, bodyR * 0.72f),
    style = Stroke(width = (w * 0.045f).coerceAtLeast(1.4f))
  )

  // Crisp glossy Orbi body
  drawCircle(
    brush = Brush.radialGradient(
      colors = listOf(Color.White, Color(0xFFE7F2FF)),
      center = Offset(cx - bodyR * 0.25f, cy - bodyR * 0.30f),
      radius = bodyR * 1.35f
    ),
    radius = bodyR,
    center = Offset(cx, cy)
  )

  // Subtle colored cheek tint
  val cheekY = cy + bodyR * 0.16f
  val cheekOffset = bodyR * 0.52f
  val cheekColor = if (mood == OrbiMood.ROMANTIC_HEART) Color(0xFFFB7185) else theme.primary
  drawCircle(
    color = cheekColor.copy(alpha = 0.32f),
    radius = bodyR * 0.17f,
    center = Offset(cx - cheekOffset, cheekY)
  )
  drawCircle(
    color = cheekColor.copy(alpha = 0.32f),
    radius = bodyR * 0.17f,
    center = Offset(cx + cheekOffset, cheekY)
  )

  // Face expression (varies cleanly by mood)
  val eyeOffsetX = bodyR * 0.33f
  val eyeY = cy - bodyR * 0.06f
  val eyeR = bodyR * 0.135f
  val inkColor = Color(0xFF0E1726)

  when (mood) {
    OrbiMood.NIGHT_SLEEPY -> {
      // Calm closed sleepy eyes (curved arcs)
      val strokeW = (bodyR * 0.14f).coerceAtLeast(1.4f)
      drawArc(
        color = inkColor,
        startAngle = 15f,
        sweepAngle = 150f,
        useCenter = false,
        topLeft = Offset(cx - eyeOffsetX - eyeR, eyeY - eyeR * 0.6f),
        size = Size(eyeR * 2f, eyeR * 1.6f),
        style = Stroke(width = strokeW, cap = StrokeCap.Round)
      )
      drawArc(
        color = inkColor,
        startAngle = 15f,
        sweepAngle = 150f,
        useCenter = false,
        topLeft = Offset(cx + eyeOffsetX - eyeR, eyeY - eyeR * 0.6f),
        size = Size(eyeR * 2f, eyeR * 1.6f),
        style = Stroke(width = strokeW, cap = StrokeCap.Round)
      )
    }

    OrbiMood.PHONK_SHADES -> {
      // Sleek cyber shades
      val lensW = bodyR * 0.64f
      val lensH = bodyR * 0.40f
      val topY = eyeY - lensH * 0.48f
      drawRoundRect(
        color = Color(0xFF0A0F1E),
        topLeft = Offset(cx - lensW - bodyR * 0.05f, topY),
        size = Size(lensW, lensH),
        cornerRadius = CornerRadius(lensH * 0.35f, lensH * 0.35f)
      )
      drawRoundRect(
        color = Color(0xFF0A0F1E),
        topLeft = Offset(cx + bodyR * 0.05f, topY),
        size = Size(lensW, lensH),
        cornerRadius = CornerRadius(lensH * 0.35f, lensH * 0.35f)
      )
      drawLine(
        color = Color(0xFF0A0F1E),
        start = Offset(cx - bodyR * 0.07f, topY + lensH * 0.35f),
        end = Offset(cx + bodyR * 0.07f, topY + lensH * 0.35f),
        strokeWidth = (bodyR * 0.12f).coerceAtLeast(1.4f)
      )
      // Cyan lens glint
      drawLine(
        color = theme.primary.copy(alpha = 0.85f),
        start = Offset(cx - lensW * 0.72f, topY + lensH * 0.28f),
        end = Offset(cx - lensW * 0.35f, topY + lensH * 0.72f),
        strokeWidth = (bodyR * 0.08f).coerceAtLeast(1.1f),
        cap = StrokeCap.Round
      )
      drawLine(
        color = theme.primary.copy(alpha = 0.85f),
        start = Offset(cx + lensW * 0.28f, topY + lensH * 0.28f),
        end = Offset(cx + lensW * 0.65f, topY + lensH * 0.72f),
        strokeWidth = (bodyR * 0.08f).coerceAtLeast(1.1f),
        cap = StrokeCap.Round
      )
    }

    else -> {
      // Bright friendly Orbi eyes with white highlight dot
      drawCircle(color = inkColor, radius = eyeR, center = Offset(cx - eyeOffsetX, eyeY))
      drawCircle(color = inkColor, radius = eyeR, center = Offset(cx + eyeOffsetX, eyeY))
      drawCircle(
        color = Color.White,
        radius = eyeR * 0.36f,
        center = Offset(cx - eyeOffsetX - eyeR * 0.26f, eyeY - eyeR * 0.26f)
      )
      drawCircle(
        color = Color.White,
        radius = eyeR * 0.36f,
        center = Offset(cx + eyeOffsetX - eyeR * 0.26f, eyeY - eyeR * 0.26f)
      )
    }
  }

  // Accessories (minimalist & crisp, never noisy)
  when (mood) {
    OrbiMood.LOFI_HEADPHONES -> {
      val bandR = bodyR * 1.12f
      drawArc(
        color = theme.primary,
        startAngle = 192f,
        sweepAngle = 156f,
        useCenter = false,
        topLeft = Offset(cx - bandR, cy - bandR),
        size = Size(bandR * 2f, bandR * 2f),
        style = Stroke(width = (bodyR * 0.18f).coerceAtLeast(1.8f), cap = StrokeCap.Round)
      )
      val cupW = bodyR * 0.28f
      val cupH = bodyR * 0.56f
      drawRoundRect(
        color = Color(0xFF151F30),
        topLeft = Offset(cx - bodyR - cupW * 0.55f, cy - cupH * 0.5f),
        size = Size(cupW, cupH),
        cornerRadius = CornerRadius(cupW * 0.5f, cupW * 0.5f)
      )
      drawRoundRect(
        color = Color(0xFF151F30),
        topLeft = Offset(cx + bodyR - cupW * 0.45f, cy - cupH * 0.5f),
        size = Size(cupW, cupH),
        cornerRadius = CornerRadius(cupW * 0.5f, cupW * 0.5f)
      )
      drawRoundRect(
        color = theme.primary,
        topLeft = Offset(cx - bodyR - cupW * 0.55f, cy - cupH * 0.5f),
        size = Size(cupW, cupH),
        cornerRadius = CornerRadius(cupW * 0.5f, cupW * 0.5f),
        style = Stroke(width = (bodyR * 0.08f).coerceAtLeast(1f))
      )
      drawRoundRect(
        color = theme.primary,
        topLeft = Offset(cx + bodyR - cupW * 0.45f, cy - cupH * 0.5f),
        size = Size(cupW, cupH),
        cornerRadius = CornerRadius(cupW * 0.5f, cupW * 0.5f),
        style = Stroke(width = (bodyR * 0.08f).coerceAtLeast(1f))
      )
    }

    OrbiMood.NIGHT_SLEEPY -> {
      // Minimalist glowing crescent moon in top-right corner
      val mx = cx + bodyR * 0.92f
      val my = cy - bodyR * 0.92f
      val mr = bodyR * 0.34f
      drawCircle(color = Color(0xFFFDE047), radius = mr, center = Offset(mx, my))
      drawCircle(color = Color(0xFF161D36), radius = mr * 0.85f, center = Offset(mx + mr * 0.36f, my - mr * 0.16f))
    }

    OrbiMood.ROMANTIC_HEART -> {
      // Crisp tiny floating heart top-right
      val hx = cx + bodyR * 0.88f
      val hy = cy - bodyR * 0.88f
      val hs = bodyR * 0.34f
      val heartPath = Path().apply {
        moveTo(hx, hy + hs * 0.55f)
        cubicTo(hx - hs, hy - hs * 0.05f, hx - hs * 0.72f, hy - hs * 0.85f, hx, hy - hs * 0.25f)
        cubicTo(hx + hs * 0.72f, hy - hs * 0.85f, hx + hs, hy - hs * 0.05f, hx, hy + hs * 0.55f)
        close()
      }
      drawPath(path = heartPath, color = Color(0xFFFB7185))
    }

    OrbiMood.PARTY_SPARK -> {
      // Tiny diamond star spark top-right
      val sx = cx + bodyR * 0.92f
      val sy = cy - bodyR * 0.90f
      val sr = bodyR * 0.32f
      val spark = Path().apply {
        moveTo(sx, sy - sr)
        lineTo(sx + sr * 0.34f, sy - sr * 0.34f)
        lineTo(sx + sr, sy)
        lineTo(sx + sr * 0.34f, sy + sr * 0.34f)
        lineTo(sx, sy + sr)
        lineTo(sx - sr * 0.34f, sy + sr * 0.34f)
        lineTo(sx - sr, sy)
        lineTo(sx - sr * 0.34f, sy - sr * 0.34f)
        close()
      }
      drawPath(path = spark, color = Color(0xFFFBBF24))
    }

    else -> Unit
  }
}

/**
 * Clean, uncluttered bottom sheet opened by tapping the Orbi avatar on Home.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrbiCompanionSheet(
  mood: OrbiMood,
  selectedTheme: OrbiCompanionTheme,
  badges: List<OrbiMilestoneBadge>,
  isPlaying: Boolean,
  reducedMotion: Boolean,
  darkTheme: Boolean,
  onSelectTheme: (OrbiCompanionTheme) -> Unit,
  onOpenInsights: () -> Unit,
  onDismiss: () -> Unit
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = if (darkTheme) Color(0xFF101726) else Color(0xFFF6F8FD),
    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 20.dp)
        .navigationBarsPadding()
        .padding(bottom = 24.dp),
      verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
      ) {
        OrbiCompanionAvatar(
          mood = mood,
          theme = selectedTheme,
          isPlaying = isPlaying,
          reducedMotion = reducedMotion,
          size = 64.dp
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
          Text(
            text = stringResource(id = R.string.orbi_companion_title),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface
          )
          Text(
            text = stringResource(id = mood.labelRes()),
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
            color = selectedTheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
          val unlockedCount = badges.count { it.unlocked }
          Text(
            text = stringResource(id = R.string.orbi_badges_progress, unlockedCount, badges.size),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
        IconButton(
          onClick = onDismiss,
          modifier = Modifier
            .minimumInteractiveComponentSize()
            .testTag("orbi_sheet_close_button")
        ) {
          Icon(
            imageVector = Icons.Rounded.Close,
            contentDescription = stringResource(id = R.string.cd_close_sheet),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }

      Text(
        text = stringResource(id = R.string.orbi_themes_title),
        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
        color = MaterialTheme.colorScheme.onSurface
      )

      OrbiThemePickerRow(
        selectedTheme = selectedTheme,
        badges = badges,
        darkTheme = darkTheme,
        onSelectTheme = onSelectTheme
      )

      Surface(
        onClick = {
          onDismiss()
          onOpenInsights()
        },
        shape = RoundedCornerShape(18.dp),
        color = selectedTheme.primary.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, selectedTheme.primary.copy(alpha = 0.35f)),
        modifier = Modifier
          .fillMaxWidth()
          .testTag("orbi_sheet_open_insights_button")
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
          horizontalArrangement = Arrangement.Center,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Icon(
            imageVector = Icons.Rounded.WorkspacePremium,
            contentDescription = null,
            tint = selectedTheme.primary,
            modifier = Modifier.size(18.dp)
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = stringResource(id = R.string.orbi_open_insights),
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface
          )
        }
      }
    }
  }
}

/**
 * Clean collapsible card inside MusicInsightsScreen displaying live reactive Orbi,
 * unlocked theme swatches, and a crisp 2-column milestone badge grid.
 */
@Composable
fun OrbiCompanionInsightsCard(
  mood: OrbiMood,
  selectedTheme: OrbiCompanionTheme,
  badges: List<OrbiMilestoneBadge>,
  isPlaying: Boolean,
  darkTheme: Boolean,
  reducedMotion: Boolean,
  isExpanded: Boolean,
  onToggleExpand: () -> Unit,
  onSelectTheme: (OrbiCompanionTheme) -> Unit
) {
  val unlockedCount = remember(badges) { badges.count { it.unlocked } }
  val cardEnter = remember(reducedMotion) {
    if (reducedMotion) fadeIn(snap()) else expandVertically(tween(240)) + fadeIn(tween(200))
  }
  val cardExit = remember(reducedMotion) {
    if (reducedMotion) fadeOut(snap()) else shrinkVertically(tween(200)) + fadeOut(tween(150))
  }

  GlassCard(
    darkTheme = darkTheme,
    cornerRadius = 24.dp,
    modifier = Modifier
      .fillMaxWidth()
      .testTag("insights_orbi_companion_card")
  ) {
    Column(
      modifier = Modifier.padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .clickable { onToggleExpand() }
          .testTag("insights_toggle_orbi_button"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        OrbiCompanionAvatar(
          mood = mood,
          theme = selectedTheme,
          isPlaying = isPlaying,
          reducedMotion = reducedMotion,
          size = 48.dp
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
          Text(
            text = stringResource(id = R.string.orbi_companion_title),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface
          )
          Text(
            text = stringResource(id = mood.labelRes()),
            style = MaterialTheme.typography.bodySmall,
            color = selectedTheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }
        Surface(
          shape = CircleShape,
          color = selectedTheme.primary.copy(alpha = 0.16f),
          border = BorderStroke(1.dp, selectedTheme.primary.copy(alpha = 0.35f))
        ) {
          Text(
            text = "$unlockedCount/${badges.size}",
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = selectedTheme.primary,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
          )
        }
      }

      AnimatedVisibility(visible = isExpanded, enter = cardEnter, exit = cardExit) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
          OrbiThemePickerRow(
            selectedTheme = selectedTheme,
            badges = badges,
            darkTheme = darkTheme,
            onSelectTheme = onSelectTheme
          )

          // Clean 2-column milestone badge grid (uncluttered, crisp progress)
          Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            badges.chunked(2).forEach { rowBadges ->
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
              ) {
                rowBadges.forEach { badge ->
                  OrbiBadgeTile(
                    badge = badge,
                    darkTheme = darkTheme,
                    onClick = {
                      if (badge.unlocked) onSelectTheme(badge.unlockedTheme)
                    },
                    modifier = Modifier.weight(1f)
                  )
                }
                if (rowBadges.size == 1) {
                  Spacer(modifier = Modifier.weight(1f))
                }
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun OrbiThemePickerRow(
  selectedTheme: OrbiCompanionTheme,
  badges: List<OrbiMilestoneBadge>,
  darkTheme: Boolean,
  onSelectTheme: (OrbiCompanionTheme) -> Unit
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .horizontalScroll(rememberScrollState()),
    horizontalArrangement = Arrangement.spacedBy(10.dp)
  ) {
    OrbiCompanionTheme.entries.forEach { theme ->
      val unlocked = isOrbiThemeUnlocked(theme, badges)
      val selected = selectedTheme == theme
      val borderCol by animateColorAsState(
        targetValue = when {
          selected -> theme.primary
          unlocked -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f)
          else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
        },
        label = "orbi_theme_border_${theme.id}"
      )
      Surface(
        onClick = { if (unlocked) onSelectTheme(theme) },
        enabled = unlocked,
        shape = RoundedCornerShape(16.dp),
        color = if (selected) {
          theme.primary.copy(alpha = 0.16f)
        } else if (darkTheme) {
          Color.White.copy(alpha = if (unlocked) 0.05f else 0.025f)
        } else {
          Color.Black.copy(alpha = if (unlocked) 0.04f else 0.02f)
        },
        border = BorderStroke(if (selected) 1.5.dp else 1.dp, borderCol),
        modifier = Modifier.testTag("orbi_theme_chip_${theme.id}")
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
          Box(
            modifier = Modifier
              .size(16.dp)
              .clip(CircleShape)
              .background(
                Brush.linearGradient(
                  listOf(
                    theme.primary.copy(alpha = if (unlocked) 1f else 0.35f),
                    theme.secondary.copy(alpha = if (unlocked) 1f else 0.35f)
                  )
                )
              ),
            contentAlignment = Alignment.Center
          ) {
            if (selected) {
              Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(11.dp)
              )
            } else if (!unlocked) {
              Icon(
                imageVector = Icons.Rounded.Lock,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.size(10.dp)
              )
            }
          }
          Text(
            text = stringResource(id = theme.labelRes()),
            style = MaterialTheme.typography.labelMedium.copy(
              fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
            ),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (unlocked) 1f else 0.45f),
            maxLines = 1
          )
        }
      }
    }
  }
}

@Composable
private fun OrbiBadgeTile(
  badge: OrbiMilestoneBadge,
  darkTheme: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  val icon: ImageVector = when (badge.id) {
    "first_beat" -> Icons.Rounded.MusicNote
    "night_owl" -> Icons.Rounded.NightlightRound
    "chill_drifter" -> Icons.Rounded.Headphones
    "heart_collector" -> Icons.Rounded.Favorite
    "artist_explorer" -> Icons.Rounded.People
    else -> Icons.Rounded.AutoAwesome
  }
  val bg = if (badge.unlocked) {
    badge.accentColor.copy(alpha = if (darkTheme) 0.13f else 0.10f)
  } else {
    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f)
  }
  val border = if (badge.unlocked) {
    badge.accentColor.copy(alpha = 0.38f)
  } else {
    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
  }

  Surface(
    onClick = onClick,
    enabled = badge.unlocked,
    shape = RoundedCornerShape(18.dp),
    color = bg,
    border = BorderStroke(1.dp, border),
    modifier = modifier.testTag("orbi_badge_${badge.id}")
  ) {
    Column(
      modifier = Modifier.padding(12.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Box(
          modifier = Modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(
              if (badge.unlocked) badge.accentColor.copy(alpha = 0.22f)
              else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
            ),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = if (badge.unlocked) icon else Icons.Rounded.Lock,
            contentDescription = null,
            tint = if (badge.unlocked) badge.accentColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
            modifier = Modifier.size(16.dp)
          )
        }
        Text(
          text = if (badge.unlocked) {
            stringResource(id = R.string.orbi_badge_unlocked)
          } else {
            "${(badge.progressFraction * 100).toInt()}%"
          },
          style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 10.sp),
          color = if (badge.unlocked) badge.accentColor else MaterialTheme.colorScheme.onSurfaceVariant
        )
      }

      Text(
        text = stringResource(id = badge.titleRes),
        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (badge.unlocked) 1f else 0.65f),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
      Text(
        text = stringResource(id = badge.requirementRes),
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, lineHeight = 14.sp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
      )
    }
  }
}
