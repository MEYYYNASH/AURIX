package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Smartphone
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
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
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
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import com.example.R
import com.example.data.IconManager
import com.example.data.OrbiAppIcon
import com.example.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Calendar
import kotlin.math.PI
import kotlin.math.sin

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

  fun shortLabelRes(): Int = when (this) {
    LOFI_HEADPHONES -> R.string.orbi_mood_short_lofi
    PHONK_SHADES -> R.string.orbi_mood_short_phonk
    NIGHT_SLEEPY -> R.string.orbi_mood_short_night
    ROMANTIC_HEART -> R.string.orbi_mood_short_romantic
    PARTY_SPARK -> R.string.orbi_mood_short_party
    CLASSIC_IDLE -> R.string.orbi_mood_short_idle
  }
}

/**
 * Synced with the 5 official OrbiAppIcon launcher styles (Classic, Midnight, Ember, AMOLED, Pearl)
 * plus 1 milestone-exclusive Prismatic style.
 */
enum class OrbiCompanionTheme(
  val id: String,
  val primary: Color,
  val secondary: Color,
  val ringColor: Color,
  val bgTopColor: Color,
  val bgBottomColor: Color,
  val planetTopColor: Color,
  val planetBottomColor: Color,
  val eyeColor: Color,
  val ringGradientColors: List<Color>,
  val isThinRing: Boolean = false,
  val isAmoledCutout: Boolean = false,
  val officialIcon: OrbiAppIcon? = null,
  val badgeIdRequired: String?
) {
  CLASSIC(
    id = "classic",
    primary = Color(0xFF43C7F4),
    secondary = Color(0xFF6E7084),
    ringColor = Color(0xFF3A3B42),
    bgTopColor = OrbiAppIcon.CLASSIC.bgTopColor,
    bgBottomColor = OrbiAppIcon.CLASSIC.bgBottomColor,
    planetTopColor = OrbiAppIcon.CLASSIC.planetTopColor,
    planetBottomColor = OrbiAppIcon.CLASSIC.planetBottomColor,
    eyeColor = OrbiAppIcon.CLASSIC.eyeColor,
    ringGradientColors = OrbiAppIcon.CLASSIC.ringColors,
    officialIcon = OrbiAppIcon.CLASSIC,
    badgeIdRequired = null
  ),
  MIDNIGHT(
    id = "midnight",
    primary = Color(0xFF5CE6FF),
    secondary = Color(0xFF7A5CFF),
    ringColor = Color(0xFF5CE6FF),
    bgTopColor = OrbiAppIcon.MIDNIGHT.bgTopColor,
    bgBottomColor = OrbiAppIcon.MIDNIGHT.bgBottomColor,
    planetTopColor = OrbiAppIcon.MIDNIGHT.planetTopColor,
    planetBottomColor = OrbiAppIcon.MIDNIGHT.planetBottomColor,
    eyeColor = OrbiAppIcon.MIDNIGHT.eyeColor,
    ringGradientColors = OrbiAppIcon.MIDNIGHT.ringColors,
    officialIcon = OrbiAppIcon.MIDNIGHT,
    badgeIdRequired = "night_owl"
  ),
  EMBER(
    id = "ember",
    primary = Color(0xFFEF4444),
    secondary = Color(0xFFF97316),
    ringColor = Color(0xFFEF4444),
    bgTopColor = OrbiAppIcon.EMBER.bgTopColor,
    bgBottomColor = OrbiAppIcon.EMBER.bgBottomColor,
    planetTopColor = OrbiAppIcon.EMBER.planetTopColor,
    planetBottomColor = OrbiAppIcon.EMBER.planetBottomColor,
    eyeColor = OrbiAppIcon.EMBER.eyeColor,
    ringGradientColors = OrbiAppIcon.EMBER.ringColors,
    officialIcon = OrbiAppIcon.EMBER,
    badgeIdRequired = "chill_drifter"
  ),
  AMOLED(
    id = "amoled",
    primary = Color(0xFFE2E8F0),
    secondary = Color(0xFF94A3B8),
    ringColor = Color(0xFFFFFFFF),
    bgTopColor = OrbiAppIcon.AMOLED.bgTopColor,
    bgBottomColor = OrbiAppIcon.AMOLED.bgBottomColor,
    planetTopColor = OrbiAppIcon.AMOLED.planetTopColor,
    planetBottomColor = OrbiAppIcon.AMOLED.planetBottomColor,
    eyeColor = OrbiAppIcon.AMOLED.eyeColor,
    ringGradientColors = OrbiAppIcon.AMOLED.ringColors,
    isThinRing = true,
    isAmoledCutout = true,
    officialIcon = OrbiAppIcon.AMOLED,
    badgeIdRequired = "heart_collector"
  ),
  PEARL(
    id = "pearl",
    primary = Color(0xFF7C3AED),
    secondary = Color(0xFFA78BFA),
    ringColor = Color(0xFF7C3AED),
    bgTopColor = OrbiAppIcon.PEARL.bgTopColor,
    bgBottomColor = OrbiAppIcon.PEARL.bgBottomColor,
    planetTopColor = OrbiAppIcon.PEARL.planetTopColor,
    planetBottomColor = OrbiAppIcon.PEARL.planetBottomColor,
    eyeColor = OrbiAppIcon.PEARL.eyeColor,
    ringGradientColors = OrbiAppIcon.PEARL.ringColors,
    officialIcon = OrbiAppIcon.PEARL,
    badgeIdRequired = "artist_explorer"
  ),
  PRISMATIC(
    id = "prismatic",
    primary = Color(0xFF22D3EE),
    secondary = Color(0xFFEC4899),
    ringColor = Color(0xFFA855F7),
    bgTopColor = Color(0xFF141233),
    bgBottomColor = Color(0xFF09071A),
    planetTopColor = Color(0xFFFFFFFF),
    planetBottomColor = Color(0xFFE0E7FF),
    eyeColor = Color(0xFF0E1324),
    ringGradientColors = listOf(
      Color(0xFF22D3EE),
      Color(0xFFA855F7),
      Color(0xFFEC4899),
      Color(0xFFFBBF24)
    ),
    officialIcon = null,
    badgeIdRequired = "aurix_legend"
  );

  fun labelRes(): Int = when (this) {
    CLASSIC -> R.string.orbi_theme_classic
    MIDNIGHT -> R.string.orbi_theme_midnight
    EMBER -> R.string.orbi_theme_ember
    AMOLED -> R.string.orbi_theme_amoled
    PEARL -> R.string.orbi_theme_pearl
    PRISMATIC -> R.string.orbi_theme_prismatic
  }

  companion object {
    // Backward-compatible aliases so any existing callers/tests referencing AURORA/MINT_CLOUD/ROSE_VELVET/SUNSET_GOLD compile seamlessly
    val AURORA: OrbiCompanionTheme get() = CLASSIC
    val MINT_CLOUD: OrbiCompanionTheme get() = EMBER
    val ROSE_VELVET: OrbiCompanionTheme get() = AMOLED
    val SUNSET_GOLD: OrbiCompanionTheme get() = PEARL

    fun fromId(raw: String?): OrbiCompanionTheme {
      if (raw.isNullOrBlank()) return CLASSIC
      return when (raw.trim().lowercase()) {
        "classic", "aurora" -> CLASSIC
        "midnight" -> MIDNIGHT
        "ember", "mint" -> EMBER
        "amoled", "rose" -> AMOLED
        "pearl", "sunset" -> PEARL
        "prismatic" -> PRISMATIC
        else -> CLASSIC
      }
    }
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

/**
 * Cycles through null (Auto) -> LOFI_HEADPHONES -> PHONK_SHADES -> NIGHT_SLEEPY -> ROMANTIC_HEART -> PARTY_SPARK -> null.
 */
fun cycleNextOrbiMoodOverride(currentOverride: OrbiMood?): OrbiMood? {
  val sequence = listOf(
    null,
    OrbiMood.LOFI_HEADPHONES,
    OrbiMood.PHONK_SHADES,
    OrbiMood.NIGHT_SLEEPY,
    OrbiMood.ROMANTIC_HEART,
    OrbiMood.PARTY_SPARK
  )
  val idx = sequence.indexOf(currentOverride)
  return if (idx == -1 || idx == sequence.lastIndex) null else sequence[idx + 1]
}

/**
 * Extracts normalized low/mid beat energy (0f..1f) from real-time FFT spectrum bars
 * so Orbi's tilted orbit ring pulses gently to the beat when playing.
 */
fun computeOrbiBeatEnergy(
  spectrumBars: FloatArray?,
  isPlaying: Boolean,
  reducedMotion: Boolean = false
): Float {
  if (!isPlaying || reducedMotion || spectrumBars == null || spectrumBars.isEmpty()) return 0f
  val count = minOf(6, spectrumBars.size)
  var sum = 0f
  for (i in 0 until count) {
    sum += spectrumBars[i].coerceIn(0f, 1f)
  }
  return (sum / count.toFloat()).coerceIn(0f, 1f)
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
      unlockedTheme = OrbiCompanionTheme.CLASSIC,
      accentColor = Color(0xFF43C7F4)
    ),
    OrbiMilestoneBadge(
      id = "night_owl",
      titleRes = R.string.orbi_badge_night_owl,
      requirementRes = R.string.orbi_badge_night_owl_req,
      unlocked = (isNightNow && totalPlays >= 1) || totalPlays >= 5,
      progressFraction = if (isNightNow && totalPlays >= 1) 1f else (totalPlays / 5f).coerceIn(0f, 1f),
      unlockedTheme = OrbiCompanionTheme.MIDNIGHT,
      accentColor = Color(0xFF5CE6FF)
    ),
    OrbiMilestoneBadge(
      id = "chill_drifter",
      titleRes = R.string.orbi_badge_chill_drifter,
      requirementRes = R.string.orbi_badge_chill_drifter_req,
      unlocked = (chillSignal && totalPlays >= 2) || totalPlays >= 10,
      progressFraction = if (chillSignal && totalPlays >= 2) 1f else (totalPlays / 10f).coerceIn(0f, 1f),
      unlockedTheme = OrbiCompanionTheme.EMBER,
      accentColor = Color(0xFFEF4444)
    ),
    OrbiMilestoneBadge(
      id = "heart_collector",
      titleRes = R.string.orbi_badge_heart_collector,
      requirementRes = R.string.orbi_badge_heart_collector_req,
      unlocked = favoritesCount >= 5,
      progressFraction = (favoritesCount / 5f).coerceIn(0f, 1f),
      unlockedTheme = OrbiCompanionTheme.AMOLED,
      accentColor = Color(0xFFFB7185)
    ),
    OrbiMilestoneBadge(
      id = "artist_explorer",
      titleRes = R.string.orbi_badge_artist_explorer,
      requirementRes = R.string.orbi_badge_artist_explorer_req,
      unlocked = uniqueArtistsCount >= 5,
      progressFraction = (uniqueArtistsCount / 5f).coerceIn(0f, 1f),
      unlockedTheme = OrbiCompanionTheme.PEARL,
      accentColor = Color(0xFFA78BFA)
    ),
    OrbiMilestoneBadge(
      id = "aurix_legend",
      titleRes = R.string.orbi_badge_aurix_legend,
      requirementRes = R.string.orbi_badge_aurix_legend_req,
      unlocked = totalPlays >= 30,
      progressFraction = (totalPlays / 30f).coerceIn(0f, 1f),
      unlockedTheme = OrbiCompanionTheme.PRISMATIC,
      accentColor = Color(0xFF22D3EE)
    )
  )
}

fun isOrbiThemeUnlocked(
  theme: OrbiCompanionTheme,
  badges: List<OrbiMilestoneBadge>
): Boolean {
  val reqId = theme.badgeIdRequired ?: return true
  return badges.any { it.id == reqId && it.unlocked }
}

/**
 * Interactive Orbi Mini-Companion Avatar rendered using the official OrbiLauncherIconTile geometry:
 * - Authentic 108dp normalized coordinate system
 * - Tilted (-12°) planet sphere + two tall rounded-pill eyes (cy - 9.8*s)
 * - Split 3D orbit ring (back half 180..360 behind planet, front half 0..180 in front of planet)
 * - Modified with mood accessories (Lo-fi headphones, Phonk cyber shades, Late-Night sleepy moon,
 *   Romantic heart glow, Party star spark) and live beat-reactive orbit ring pulse.
 */
@Composable
fun OrbiCompanionAvatar(
  mood: OrbiMood,
  theme: OrbiCompanionTheme,
  isPlaying: Boolean,
  reducedMotion: Boolean,
  size: Dp = 44.dp,
  beatEnergy: Float = 0f,
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

  val animationsEnabled = rememberOrbiAnimationsEnabled(reducedMotion)
  val infinite = rememberInfiniteTransition(label = "orbi_official_logo_companion")

  val floatPhase = if (!animationsEnabled) {
    0f
  } else {
    val anim by infinite.animateFloat(
      initialValue = -1f,
      targetValue = 1f,
      animationSpec = infiniteRepeatable(
        animation = tween(durationMillis = if (isPlaying) 1450 else 2100, easing = OrbiEasing),
        repeatMode = RepeatMode.Reverse
      ),
      label = "orbi_logo_float"
    )
    anim
  }

  val blinkClock = if (!animationsEnabled) {
    0.5f
  } else {
    val anim by infinite.animateFloat(
      initialValue = 0f,
      targetValue = 1f,
      animationSpec = infiniteRepeatable(
        animation = tween(durationMillis = 3900, easing = LinearEasing),
        repeatMode = RepeatMode.Restart
      ),
      label = "orbi_logo_blink"
    )
    anim
  }
  val blinkScaleY = when {
    !animationsEnabled -> 1f
    blinkClock < 0.025f -> 1f - (blinkClock / 0.025f) * 0.90f
    blinkClock < 0.055f -> 0.10f + ((blinkClock - 0.025f) / 0.030f) * 0.90f
    else -> 1f
  }

  val glancePhase = if (!animationsEnabled) {
    0f
  } else {
    val anim by infinite.animateFloat(
      initialValue = 0f,
      targetValue = 1f,
      animationSpec = infiniteRepeatable(
        animation = tween(durationMillis = 4200, easing = LinearEasing),
        repeatMode = RepeatMode.Restart
      ),
      label = "orbi_logo_glance"
    )
    sin(anim * 2.0 * PI).toFloat()
  }

  val smoothedBeat by animateFloatAsState(
    targetValue = if (isPlaying && !reducedMotion) beatEnergy.coerceIn(0f, 1f) else 0f,
    animationSpec = if (reducedMotion) snap() else tween(110, easing = FastOutSlowInEasing),
    label = "orbi_beat_pulse"
  )

  val cd = stringResource(id = R.string.orbi_companion_cd)
  val corner = size * 0.285f
  val tileBorderColor = when (theme) {
    OrbiCompanionTheme.AMOLED -> Color.White.copy(alpha = 0.26f + 0.30f * smoothedBeat)
    OrbiCompanionTheme.PEARL -> Color(0xFFA78BFA).copy(alpha = 0.50f + 0.30f * smoothedBeat)
    else -> theme.primary.copy(alpha = 0.38f + 0.42f * smoothedBeat)
  }

  Box(
    modifier = modifier
      .size(size)
      .scale(tapScale)
      .clip(RoundedCornerShape(corner))
      .background(
        Brush.verticalGradient(
          colors = listOf(theme.bgTopColor, theme.bgBottomColor)
        )
      )
      .border(
        width = (1f + 0.7f * smoothedBeat).dp,
        color = tileBorderColor,
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
    Canvas(
      modifier = Modifier
        .fillMaxSize()
        .graphicsLayer {
          translationY = floatPhase * 1.6.dp.toPx()
        }
    ) {
      drawModifiedOfficialOrbiLogo(
        mood = mood,
        theme = theme,
        blinkScaleY = blinkScaleY,
        glancePhase = glancePhase,
        beatEnergy = smoothedBeat
      )
    }
  }
}

/**
 * Draws the official Orbi launcher logo (`OrbiLauncherIconTile` geometry) modified with
 * mood accessories and beat-reactive orbit ring pulse.
 */
internal fun DrawScope.drawModifiedOfficialOrbiLogo(
  mood: OrbiMood,
  theme: OrbiCompanionTheme,
  blinkScaleY: Float = 1f,
  glancePhase: Float = 0f,
  beatEnergy: Float = 0f
) {
  if (this.size.minDimension <= 0f) return
  val s = this.size.minDimension / 108f
  val cx = this.size.width / 2f
  val cy = this.size.height / 2f

  val planetR = 20.5f * s
  val ringRx = (29.5f + 1.8f * beatEnergy) * s
  val ringRy = (7.8f + 0.55f * beatEnergy) * s
  val baseRingStroke = if (theme.isThinRing) 2.4f else 3.8f
  val ringStroke = (baseRingStroke + 1.45f * beatEnergy) * s

  val ringBrush = Brush.linearGradient(
    colors = theme.ringGradientColors,
    start = Offset(cx - ringRx, cy - ringRy),
    end = Offset(cx + ringRx, cy + ringRy)
  )

  // Beat-reactive ambient halo behind the planet & ring
  if (beatEnergy > 0.04f || mood != OrbiMood.CLASSIC_IDLE) {
    val haloAlpha = (0.16f + 0.34f * beatEnergy).coerceIn(0f, 0.52f)
    drawCircle(
      brush = Brush.radialGradient(
        colors = listOf(theme.primary.copy(alpha = haloAlpha), Color.Transparent),
        center = Offset(cx, cy),
        radius = planetR * (1.75f + 0.35f * beatEnergy)
      ),
      radius = planetR * (1.75f + 0.35f * beatEnergy),
      center = Offset(cx, cy)
    )
  }

  // Official -12° tilted planet & 3D orbit ring
  rotate(degrees = -12f, pivot = Offset(cx, cy)) {
    // 1. Back half of orbit ring (180..360)
    if (beatEnergy > 0.08f) {
      drawArc(
        color = theme.primary.copy(alpha = 0.28f * beatEnergy),
        startAngle = 180f,
        sweepAngle = 180f,
        useCenter = false,
        topLeft = Offset(cx - ringRx, cy - ringRy),
        size = Size(ringRx * 2f, ringRy * 2f),
        style = Stroke(width = ringStroke * 1.9f, cap = StrokeCap.Round)
      )
    }
    drawArc(
      brush = ringBrush,
      startAngle = 180f,
      sweepAngle = 180f,
      useCenter = false,
      topLeft = Offset(cx - ringRx, cy - ringRy),
      size = Size(ringRx * 2f, ringRy * 2f),
      style = Stroke(width = ringStroke, cap = StrokeCap.Round)
    )

    // 2. Soft drop shadow + Official Planet Sphere
    if (!theme.isAmoledCutout) {
      drawCircle(
        color = Color.Black.copy(alpha = 0.18f),
        radius = planetR * 1.04f,
        center = Offset(cx, cy + 1.5f * s)
      )
    }
    drawCircle(
      brush = Brush.verticalGradient(
        colors = listOf(theme.planetTopColor, theme.planetBottomColor),
        startY = cy - planetR,
        endY = cy + planetR
      ),
      radius = planetR,
      center = Offset(cx, cy)
    )

    // 3. Official Two Tall Oval Eyes (cy - 9.8*s) or Mood Eye Modifications
    val gDx = glancePhase * 1.6f * s
    val eyeRx = 2.5f * s
    val eyeRy = 4.3f * s * blinkScaleY.coerceAtLeast(0.08f)
    val leftEyeCx = cx - 4.5f * s + gDx
    val rightEyeCx = cx + 4.5f * s + gDx
    val eyeCy = cy - 9.8f * s

    when (mood) {
      OrbiMood.NIGHT_SLEEPY -> {
        // Calm sleepy curved eyelids at the official upper-hemisphere eye position
        val lidStroke = (2.1f * s).coerceAtLeast(1.2f)
        val lidW = eyeRx * 2.2f
        val lidH = eyeRx * 1.5f
        drawArc(
          color = theme.eyeColor,
          startAngle = 15f,
          sweepAngle = 150f,
          useCenter = false,
          topLeft = Offset(leftEyeCx - lidW * 0.5f, eyeCy - lidH * 0.4f),
          size = Size(lidW, lidH),
          style = Stroke(width = lidStroke, cap = StrokeCap.Round)
        )
        drawArc(
          color = theme.eyeColor,
          startAngle = 15f,
          sweepAngle = 150f,
          useCenter = false,
          topLeft = Offset(rightEyeCx - lidW * 0.5f, eyeCy - lidH * 0.4f),
          size = Size(lidW, lidH),
          style = Stroke(width = lidStroke, cap = StrokeCap.Round)
        )
      }

      OrbiMood.PHONK_SHADES -> {
        // Draw official tall eyes first, then sleek cyber sunglasses across them
        drawRoundRect(
          color = theme.eyeColor,
          topLeft = Offset(leftEyeCx - eyeRx, eyeCy - eyeRy),
          size = Size(eyeRx * 2f, eyeRy * 2f),
          cornerRadius = CornerRadius(eyeRx, eyeRx)
        )
        drawRoundRect(
          color = theme.eyeColor,
          topLeft = Offset(rightEyeCx - eyeRx, eyeCy - eyeRy),
          size = Size(eyeRx * 2f, eyeRy * 2f),
          cornerRadius = CornerRadius(eyeRx, eyeRx)
        )
        val lensW = 9.2f * s
        val lensH = 5.6f * s
        val lensTop = eyeCy - lensH * 0.52f
        val leftLensLeft = leftEyeCx - lensW * 0.55f
        val rightLensLeft = rightEyeCx - lensW * 0.45f
        val shadeFill = Color(0xFF070B14)
        val glintColor = if (theme == OrbiCompanionTheme.EMBER) Color(0xFFEF4444) else Color(0xFF5CE6FF)

        drawRoundRect(
          color = shadeFill,
          topLeft = Offset(leftLensLeft, lensTop),
          size = Size(lensW, lensH),
          cornerRadius = CornerRadius(1.8f * s, 1.8f * s)
        )
        drawRoundRect(
          color = shadeFill,
          topLeft = Offset(rightLensLeft, lensTop),
          size = Size(lensW, lensH),
          cornerRadius = CornerRadius(1.8f * s, 1.8f * s)
        )
        // Neon rim & bridge
        drawRoundRect(
          color = glintColor.copy(alpha = 0.85f),
          topLeft = Offset(leftLensLeft, lensTop),
          size = Size(lensW, lensH),
          cornerRadius = CornerRadius(1.8f * s, 1.8f * s),
          style = Stroke(width = (1.1f * s).coerceAtLeast(0.8f))
        )
        drawRoundRect(
          color = glintColor.copy(alpha = 0.85f),
          topLeft = Offset(rightLensLeft, lensTop),
          size = Size(lensW, lensH),
          cornerRadius = CornerRadius(1.8f * s, 1.8f * s),
          style = Stroke(width = (1.1f * s).coerceAtLeast(0.8f))
        )
        drawLine(
          color = glintColor,
          start = Offset(leftLensLeft + lensW, lensTop + lensH * 0.38f),
          end = Offset(rightLensLeft, lensTop + lensH * 0.38f),
          strokeWidth = (1.4f * s).coerceAtLeast(1f)
        )
        // Diagonal lens reflection slash
        drawLine(
          color = Color.White.copy(alpha = 0.82f),
          start = Offset(leftLensLeft + 2.2f * s, lensTop + 1.3f * s),
          end = Offset(leftLensLeft + 4.8f * s, lensTop + lensH - 1.3f * s),
          strokeWidth = (1.1f * s).coerceAtLeast(0.8f),
          cap = StrokeCap.Round
        )
        drawLine(
          color = Color.White.copy(alpha = 0.82f),
          start = Offset(rightLensLeft + 2.2f * s, lensTop + 1.3f * s),
          end = Offset(rightLensLeft + 4.8f * s, lensTop + lensH - 1.3f * s),
          strokeWidth = (1.1f * s).coerceAtLeast(0.8f),
          cap = StrokeCap.Round
        )
      }

      else -> {
        // Exact official Orbi tall oval eyes
        drawRoundRect(
          color = theme.eyeColor,
          topLeft = Offset(leftEyeCx - eyeRx, eyeCy - eyeRy),
          size = Size(eyeRx * 2f, eyeRy * 2f),
          cornerRadius = CornerRadius(eyeRx, eyeRx)
        )
        drawRoundRect(
          color = theme.eyeColor,
          topLeft = Offset(rightEyeCx - eyeRx, eyeCy - eyeRy),
          size = Size(eyeRx * 2f, eyeRy * 2f),
          cornerRadius = CornerRadius(eyeRx, eyeRx)
        )
      }
    }

    // 3b. Lo-fi Headphones hugging the tilted Orbi planet crown
    if (mood == OrbiMood.LOFI_HEADPHONES) {
      val bandR = planetR * 1.13f
      val accentCol = if (theme == OrbiCompanionTheme.CLASSIC) Color(0xFF43C7F4) else theme.primary
      drawArc(
        color = accentCol,
        startAngle = 195f,
        sweepAngle = 150f,
        useCenter = false,
        topLeft = Offset(cx - bandR, cy - bandR),
        size = Size(bandR * 2f, bandR * 2f),
        style = Stroke(width = (3.1f * s).coerceAtLeast(1.4f), cap = StrokeCap.Round)
      )
      val cupW = 5.4f * s
      val cupH = 10.6f * s
      val cupY = cy - 7.5f * s
      val leftCupX = cx - planetR - cupW * 0.55f
      val rightCupX = cx + planetR - cupW * 0.45f
      drawRoundRect(
        color = Color(0xFF101726),
        topLeft = Offset(leftCupX, cupY),
        size = Size(cupW, cupH),
        cornerRadius = CornerRadius(cupW * 0.5f, cupW * 0.5f)
      )
      drawRoundRect(
        color = Color(0xFF101726),
        topLeft = Offset(rightCupX, cupY),
        size = Size(cupW, cupH),
        cornerRadius = CornerRadius(cupW * 0.5f, cupW * 0.5f)
      )
      drawRoundRect(
        color = accentCol,
        topLeft = Offset(leftCupX, cupY),
        size = Size(cupW, cupH),
        cornerRadius = CornerRadius(cupW * 0.5f, cupW * 0.5f),
        style = Stroke(width = (1.3f * s).coerceAtLeast(0.9f))
      )
      drawRoundRect(
        color = accentCol,
        topLeft = Offset(rightCupX, cupY),
        size = Size(cupW, cupH),
        cornerRadius = CornerRadius(cupW * 0.5f, cupW * 0.5f),
        style = Stroke(width = (1.3f * s).coerceAtLeast(0.9f))
      )
    }

    // 4. Front half of orbit ring (0..180)
    if (theme.isAmoledCutout) {
      drawArc(
        color = Color.Black,
        startAngle = 0f,
        sweepAngle = 180f,
        useCenter = false,
        topLeft = Offset(cx - ringRx, cy - ringRy),
        size = Size(ringRx * 2f, ringRy * 2f),
        style = Stroke(width = ringStroke * 2.15f, cap = StrokeCap.Round)
      )
    } else if (beatEnergy > 0.08f) {
      drawArc(
        color = theme.primary.copy(alpha = 0.34f * beatEnergy),
        startAngle = 0f,
        sweepAngle = 180f,
        useCenter = false,
        topLeft = Offset(cx - ringRx, cy - ringRy),
        size = Size(ringRx * 2f, ringRy * 2f),
        style = Stroke(width = ringStroke * 2.0f, cap = StrokeCap.Round)
      )
    }
    drawArc(
      brush = ringBrush,
      startAngle = 0f,
      sweepAngle = 180f,
      useCenter = false,
      topLeft = Offset(cx - ringRx, cy - ringRy),
      size = Size(ringRx * 2f, ringRy * 2f),
      style = Stroke(width = ringStroke, cap = StrokeCap.Round)
    )
  }

  // 5. Floating top-right orbital mood badges (Moon, Heart, Spark)
  when (mood) {
    OrbiMood.NIGHT_SLEEPY -> {
      val mx = cx + 24.5f * s
      val my = cy - 23.5f * s
      val mr = 6.6f * s
      val maskPath = Path().apply {
        addOval(androidx.compose.ui.geometry.Rect(mx - mr, my - mr, mx + mr, my + mr))
      }
      val cutoutPath = Path().apply {
        addOval(
          androidx.compose.ui.geometry.Rect(
            mx - mr * 0.35f,
            my - mr * 1.05f,
            mx + mr * 1.25f,
            my + mr * 0.55f
          )
        )
      }
      val crescent = Path().apply {
        op(maskPath, cutoutPath, androidx.compose.ui.graphics.PathOperation.Difference)
      }
      drawPath(path = crescent, color = Color(0xFFFDE047))
    }

    OrbiMood.ROMANTIC_HEART -> {
      val hx = cx + 24.5f * s
      val hy = cy - 23.0f * s
      val hs = 6.8f * s
      val heartPath = Path().apply {
        moveTo(hx, hy + hs * 0.58f)
        cubicTo(hx - hs, hy - hs * 0.05f, hx - hs * 0.72f, hy - hs * 0.88f, hx, hy - hs * 0.25f)
        cubicTo(hx + hs * 0.72f, hy - hs * 0.88f, hx + hs, hy - hs * 0.05f, hx, hy + hs * 0.58f)
        close()
      }
      drawPath(path = heartPath, color = Color(0xFFFB7185))
    }

    OrbiMood.PARTY_SPARK -> {
      val sx = cx + 24.5f * s
      val sy = cy - 23.5f * s
      val sr = 6.8f * s
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
 * Compact, non-intrusive reactive Orbi companion pill for Now Playing header and Standby Desk Clock.
 */
@Composable
fun OrbiMiniMoodBadge(
  mood: OrbiMood,
  theme: OrbiCompanionTheme,
  isPlaying: Boolean,
  reducedMotion: Boolean,
  beatEnergy: Float = 0f,
  showLabel: Boolean = false,
  onClick: (() -> Unit)? = null,
  modifier: Modifier = Modifier
) {
  Surface(
    shape = CircleShape,
    color = Color.White.copy(alpha = 0.10f),
    border = BorderStroke(1.dp, theme.primary.copy(alpha = 0.35f + 0.35f * beatEnergy.coerceIn(0f, 1f))),
    modifier = modifier
      .then(
        if (onClick != null) {
          Modifier.clickable(role = Role.Button) { onClick() }
        } else {
          Modifier
        }
      )
  ) {
    Row(
      modifier = Modifier.padding(
        start = 4.dp,
        end = if (showLabel) 10.dp else 4.dp,
        top = 4.dp,
        bottom = 4.dp
      ),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
      OrbiCompanionAvatar(
        mood = mood,
        theme = theme,
        isPlaying = isPlaying,
        reducedMotion = reducedMotion,
        beatEnergy = beatEnergy,
        size = 26.dp
      )
      if (showLabel) {
        Text(
          text = stringResource(id = mood.shortLabelRes()),
          style = MaterialTheme.typography.labelSmall.copy(
            fontWeight = FontWeight.SemiBold,
            fontSize = 11.sp
          ),
          color = Color.White.copy(alpha = 0.90f),
          maxLines = 1
        )
      }
    }
  }
}

/**
 * Clean, uncluttered bottom sheet opened by tapping the Orbi avatar on Home or Now Playing.
 * Includes live official Orbi logo preview, tap-to-cycle Mood Stickers row, official Orbi themes
 * (+ 1-tap "Apply as App Icon"), Shareable Orbi Sticker Card preview dialog, and link to Music Insights.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrbiCompanionSheet(
  mood: OrbiMood,
  moodOverride: OrbiMood? = null,
  selectedTheme: OrbiCompanionTheme,
  badges: List<OrbiMilestoneBadge>,
  isPlaying: Boolean,
  reducedMotion: Boolean,
  darkTheme: Boolean,
  beatEnergy: Float = 0f,
  totalPlays: Int = 0,
  favoritesCount: Int = 0,
  topVibeLabel: String = "AURIX",
  onSelectMoodOverride: (OrbiMood?) -> Unit = {},
  onSelectTheme: (OrbiCompanionTheme) -> Unit,
  onOpenInsights: () -> Unit,
  onDismiss: () -> Unit
) {
  val context = LocalContext.current
  var showShareCardDialog by remember { mutableStateOf(false) }
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
      verticalArrangement = Arrangement.spacedBy(16.dp)
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
          beatEnergy = beatEnergy,
          size = 64.dp,
          onClick = { onSelectMoodOverride(cycleNextOrbiMoodOverride(moodOverride)) }
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
          onClick = { showShareCardDialog = true },
          modifier = Modifier
            .minimumInteractiveComponentSize()
            .testTag("orbi_sheet_share_button")
        ) {
          Icon(
            imageVector = Icons.Rounded.Share,
            contentDescription = stringResource(id = R.string.orbi_share_sticker_card),
            tint = selectedTheme.primary
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

      // Tap-to-preview Mood Stickers row (Auto + 5 mood stickers)
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
          text = stringResource(id = R.string.orbi_mood_stickers_title),
          style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
          color = MaterialTheme.colorScheme.onSurface
        )
        OrbiMoodStickerPickerRow(
          activeMood = mood,
          moodOverride = moodOverride,
          selectedTheme = selectedTheme,
          darkTheme = darkTheme,
          onSelectMoodOverride = onSelectMoodOverride
        )
      }

      // Official Orbi Themes row
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = stringResource(id = R.string.orbi_themes_title),
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface
          )
          val official = selectedTheme.officialIcon
          if (official != null) {
            Surface(
              onClick = {
                if (IconManager.applyIcon(context, official)) {
                  Toast.makeText(
                    context,
                    context.getString(R.string.app_icon_changed_toast),
                    Toast.LENGTH_SHORT
                  ).show()
                }
              },
              shape = CircleShape,
              color = selectedTheme.primary.copy(alpha = 0.14f),
              border = BorderStroke(1.dp, selectedTheme.primary.copy(alpha = 0.35f)),
              modifier = Modifier.testTag("orbi_apply_launcher_icon_chip")
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
              ) {
                Icon(
                  imageVector = Icons.Rounded.Smartphone,
                  contentDescription = null,
                  tint = selectedTheme.primary,
                  modifier = Modifier.size(13.dp)
                )
                Text(
                  text = stringResource(id = R.string.orbi_sync_launcher_icon),
                  style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                  color = selectedTheme.primary
                )
              }
            }
          }
        }

        OrbiThemePickerRow(
          selectedTheme = selectedTheme,
          badges = badges,
          darkTheme = darkTheme,
          onSelectTheme = onSelectTheme
        )
      }

      // Actions: Share Sticker Card + View all in Insights
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        Surface(
          onClick = { showShareCardDialog = true },
          shape = RoundedCornerShape(18.dp),
          color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f)),
          modifier = Modifier
            .weight(1f)
            .testTag("orbi_sheet_share_card_cta")
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 13.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Icon(
              imageVector = Icons.Rounded.Share,
              contentDescription = null,
              tint = selectedTheme.primary,
              modifier = Modifier.size(17.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = stringResource(id = R.string.orbi_share_sticker_card),
              style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
              color = MaterialTheme.colorScheme.onSurface,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
          }
        }

        Surface(
          onClick = {
            onDismiss()
            onOpenInsights()
          },
          shape = RoundedCornerShape(18.dp),
          color = selectedTheme.primary.copy(alpha = 0.14f),
          border = BorderStroke(1.dp, selectedTheme.primary.copy(alpha = 0.35f)),
          modifier = Modifier
            .weight(1f)
            .testTag("orbi_sheet_open_insights_button")
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 13.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Icon(
              imageVector = Icons.Rounded.WorkspacePremium,
              contentDescription = null,
              tint = selectedTheme.primary,
              modifier = Modifier.size(17.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = stringResource(id = R.string.orbi_open_insights),
              style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
              color = MaterialTheme.colorScheme.onSurface,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
          }
        }
      }
    }
  }

  if (showShareCardDialog) {
    OrbiMoodStickerShareDialog(
      mood = mood,
      theme = selectedTheme,
      badges = badges,
      totalPlays = totalPlays,
      favoritesCount = favoritesCount,
      topVibeLabel = topVibeLabel,
      isPlaying = isPlaying,
      reducedMotion = reducedMotion,
      beatEnergy = beatEnergy,
      onDismiss = { showShareCardDialog = false }
    )
  }
}

/**
 * Clean collapsible card inside MusicInsightsScreen displaying live reactive official Orbi logo,
 * mood sticker preview pills, unlocked official theme swatches, share button, and 2-column milestone badge grid.
 */
@Composable
fun OrbiCompanionInsightsCard(
  mood: OrbiMood,
  moodOverride: OrbiMood? = null,
  selectedTheme: OrbiCompanionTheme,
  badges: List<OrbiMilestoneBadge>,
  isPlaying: Boolean,
  darkTheme: Boolean,
  reducedMotion: Boolean,
  beatEnergy: Float = 0f,
  totalPlays: Int = 0,
  favoritesCount: Int = 0,
  topVibeLabel: String = "AURIX",
  isExpanded: Boolean,
  onToggleExpand: () -> Unit,
  onSelectMoodOverride: (OrbiMood?) -> Unit = {},
  onSelectTheme: (OrbiCompanionTheme) -> Unit
) {
  val unlockedCount = remember(badges) { badges.count { it.unlocked } }
  var showShareCardDialog by remember { mutableStateOf(false) }

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
          beatEnergy = beatEnergy,
          size = 48.dp,
          onClick = { onSelectMoodOverride(cycleNextOrbiMoodOverride(moodOverride)) }
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
        IconButton(
          onClick = { showShareCardDialog = true },
          modifier = Modifier
            .size(36.dp)
            .testTag("insights_orbi_share_button")
        ) {
          Icon(
            imageVector = Icons.Rounded.Share,
            contentDescription = stringResource(id = R.string.orbi_share_sticker_card),
            tint = selectedTheme.primary,
            modifier = Modifier.size(18.dp)
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
          OrbiMoodStickerPickerRow(
            activeMood = mood,
            moodOverride = moodOverride,
            selectedTheme = selectedTheme,
            darkTheme = darkTheme,
            onSelectMoodOverride = onSelectMoodOverride
          )

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

  if (showShareCardDialog) {
    OrbiMoodStickerShareDialog(
      mood = mood,
      theme = selectedTheme,
      badges = badges,
      totalPlays = totalPlays,
      favoritesCount = favoritesCount,
      topVibeLabel = topVibeLabel,
      isPlaying = isPlaying,
      reducedMotion = reducedMotion,
      beatEnergy = beatEnergy,
      onDismiss = { showShareCardDialog = false }
    )
  }
}

@Composable
private fun OrbiMoodStickerPickerRow(
  activeMood: OrbiMood,
  moodOverride: OrbiMood?,
  selectedTheme: OrbiCompanionTheme,
  darkTheme: Boolean,
  onSelectMoodOverride: (OrbiMood?) -> Unit
) {
  val items: List<Pair<OrbiMood?, Int>> = listOf(
    null to R.string.orbi_mood_auto,
    OrbiMood.LOFI_HEADPHONES to R.string.orbi_mood_short_lofi,
    OrbiMood.PHONK_SHADES to R.string.orbi_mood_short_phonk,
    OrbiMood.NIGHT_SLEEPY to R.string.orbi_mood_short_night,
    OrbiMood.ROMANTIC_HEART to R.string.orbi_mood_short_romantic,
    OrbiMood.PARTY_SPARK to R.string.orbi_mood_short_party
  )
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .horizontalScroll(rememberScrollState()),
    horizontalArrangement = Arrangement.spacedBy(8.dp)
  ) {
    items.forEach { (optionMood, labelRes) ->
      val selected = moodOverride == optionMood
      val tagId = optionMood?.id ?: "auto"
      Surface(
        onClick = { onSelectMoodOverride(optionMood) },
        shape = CircleShape,
        color = if (selected) {
          selectedTheme.primary.copy(alpha = 0.18f)
        } else if (darkTheme) {
          Color.White.copy(alpha = 0.05f)
        } else {
          Color.Black.copy(alpha = 0.04f)
        },
        border = BorderStroke(
          width = if (selected) 1.5.dp else 1.dp,
          color = if (selected) selectedTheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
        ),
        modifier = Modifier.testTag("orbi_mood_sticker_chip_$tagId")
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          OrbiCompanionAvatar(
            mood = optionMood ?: activeMood,
            theme = selectedTheme,
            isPlaying = false,
            reducedMotion = true,
            size = 20.dp
          )
          Text(
            text = stringResource(id = labelRes),
            style = MaterialTheme.typography.labelMedium.copy(
              fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
            ),
            color = if (selected) selectedTheme.primary else MaterialTheme.colorScheme.onSurface
          )
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
          modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
          Box(contentAlignment = Alignment.Center) {
            OrbiCompanionAvatar(
              mood = OrbiMood.CLASSIC_IDLE,
              theme = theme,
              isPlaying = false,
              reducedMotion = true,
              size = 24.dp,
              modifier = Modifier.graphicsLayer { alpha = if (unlocked) 1f else 0.38f }
            )
            if (selected) {
              Box(
                modifier = Modifier
                  .size(14.dp)
                  .clip(CircleShape)
                  .background(theme.primary),
                contentAlignment = Alignment.Center
              ) {
                Icon(
                  imageVector = Icons.Rounded.Check,
                  contentDescription = null,
                  tint = Color.Black,
                  modifier = Modifier.size(10.dp)
                )
              }
            } else if (!unlocked) {
              Icon(
                imageVector = Icons.Rounded.Lock,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.9f),
                modifier = Modifier.size(12.dp)
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

/**
 * Shareable Orbi Mood & Music DNA Sticker Card preview dialog + PNG exporter.
 */
@Composable
fun OrbiMoodStickerShareDialog(
  mood: OrbiMood,
  theme: OrbiCompanionTheme,
  badges: List<OrbiMilestoneBadge>,
  totalPlays: Int,
  favoritesCount: Int,
  topVibeLabel: String,
  isPlaying: Boolean,
  reducedMotion: Boolean,
  beatEnergy: Float,
  onDismiss: () -> Unit
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val moodText = stringResource(id = mood.labelRes())
  val themeName = stringResource(id = theme.labelRes())
  val unlockedCount = badges.count { it.unlocked }
  val playsLabel = stringResource(id = R.string.orbi_stat_plays)
  val favsLabel = stringResource(id = R.string.orbi_stat_favorites)
  val vibeLabel = stringResource(id = R.string.orbi_stat_top_vibe)

  Dialog(onDismissRequest = onDismiss) {
    Surface(
      shape = RoundedCornerShape(28.dp),
      color = Color(0xFF0E1424),
      border = BorderStroke(1.dp, theme.primary.copy(alpha = 0.40f)),
      modifier = Modifier
        .fillMaxWidth()
        .testTag("orbi_sticker_share_dialog")
    ) {
      Column(
        modifier = Modifier.padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = stringResource(id = R.string.orbi_share_card_title),
              style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
              color = Color.White
            )
            Text(
              text = stringResource(id = R.string.orbi_share_card_sub),
              style = MaterialTheme.typography.labelSmall,
              color = Color.White.copy(alpha = 0.68f)
            )
          }
          IconButton(onClick = onDismiss) {
            Icon(
              imageVector = Icons.Rounded.Close,
              contentDescription = stringResource(id = R.string.cd_close_sheet),
              tint = Color.White.copy(alpha = 0.72f)
            )
          }
        }

        // Live Sticker Card Preview
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(
              Brush.verticalGradient(
                listOf(
                  Color(0xFF151E34),
                  Color(0xFF0A0F1D)
                )
              )
            )
            .border(
              width = 1.dp,
              brush = Brush.linearGradient(
                listOf(theme.primary.copy(alpha = 0.65f), theme.secondary.copy(alpha = 0.35f))
              ),
              shape = RoundedCornerShape(24.dp)
            )
            .padding(20.dp)
        ) {
          Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
          ) {
            OrbiCompanionAvatar(
              mood = mood,
              theme = theme,
              isPlaying = isPlaying,
              reducedMotion = reducedMotion,
              beatEnergy = beatEnergy,
              size = 88.dp
            )
            Text(
              text = moodText,
              style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
              color = Color.White
            )
            Surface(
              shape = CircleShape,
              color = theme.primary.copy(alpha = 0.16f),
              border = BorderStroke(1.dp, theme.primary.copy(alpha = 0.40f))
            ) {
              Text(
                text = "AURIX · $themeName · $unlockedCount/${badges.size}",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = theme.primary,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
              )
            }

            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
              horizontalArrangement = Arrangement.SpaceEvenly
            ) {
              StickerStatColumn(label = playsLabel, value = totalPlays.toString(), accent = theme.primary)
              StickerStatColumn(label = favsLabel, value = favoritesCount.toString(), accent = theme.primary)
              StickerStatColumn(label = vibeLabel, value = topVibeLabel.ifBlank { "Chill" }, accent = theme.primary)
            }
          }
        }

        Surface(
          onClick = {
            scope.launch {
              shareOrbiStickerCardPng(
                context = context,
                mood = mood,
                theme = theme,
                moodLabel = moodText,
                themeLabel = themeName,
                unlockedBadges = unlockedCount,
                totalBadges = badges.size,
                totalPlays = totalPlays,
                favoritesCount = favoritesCount,
                topVibeLabel = topVibeLabel.ifBlank { "Chill" }
              )
              onDismiss()
            }
          },
          shape = CircleShape,
          color = theme.primary,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("orbi_sticker_confirm_share_button")
        ) {
          Row(
            modifier = Modifier.padding(vertical = 13.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Icon(
              imageVector = Icons.Rounded.Share,
              contentDescription = null,
              tint = Color(0xFF070B14),
              modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = stringResource(id = R.string.orbi_share_action),
              style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
              color = Color(0xFF070B14)
            )
          }
        }
      }
    }
  }
}

@Composable
private fun StickerStatColumn(label: String, value: String, accent: Color) {
  Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Text(
      text = value,
      style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
      color = accent,
      maxLines = 1
    )
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall,
      color = Color.White.copy(alpha = 0.65f),
      maxLines = 1
    )
  }
}

/**
 * Renders a crisp 900x1120 PNG Sticker Card featuring the modified official Orbi logo,
 * active mood, theme badge, and Music DNA stats, then launches Android's Share Sheet.
 */
suspend fun shareOrbiStickerCardPng(
  context: Context,
  mood: OrbiMood,
  theme: OrbiCompanionTheme,
  moodLabel: String,
  themeLabel: String,
  unlockedBadges: Int,
  totalBadges: Int,
  totalPlays: Int,
  favoritesCount: Int,
  topVibeLabel: String
) = withContext(Dispatchers.IO) {
  runCatching {
    val w = 900
    val h = 1120
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    try {
      val canvas = android.graphics.Canvas(bmp)
      val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(
          0f, 0f, 0f, h.toFloat(),
          intArrayOf(0xFF141D33.toInt(), 0xFF090D1A.toInt()),
          null,
          Shader.TileMode.CLAMP
        )
      }
      canvas.drawRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), 64f, 64f, bgPaint)

      // Outer border
      val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        color = theme.primary.toArgb()
        alpha = 140
      }
      canvas.drawRoundRect(RectF(16f, 16f, w - 16f, h - 16f), 56f, 56f, borderPaint)

      // Official Orbi Logo Tile in center-top
      val tileSize = 320f
      val tileLeft = (w - tileSize) * 0.5f
      val tileTop = 120f
      val tilePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(
          0f, tileTop, 0f, tileTop + tileSize,
          intArrayOf(theme.bgTopColor.toArgb(), theme.bgBottomColor.toArgb()),
          null,
          Shader.TileMode.CLAMP
        )
      }
      canvas.drawRoundRect(
        RectF(tileLeft, tileTop, tileLeft + tileSize, tileTop + tileSize),
        tileSize * 0.285f,
        tileSize * 0.285f,
        tilePaint
      )

      val s = tileSize / 108f
      val cx = w * 0.5f
      val cy = tileTop + tileSize * 0.5f
      val planetR = 20.5f * s
      val ringRx = 29.5f * s
      val ringRy = 7.8f * s
      val ringStroke = (if (theme.isThinRing) 2.4f else 3.8f) * s

      val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = ringStroke
        strokeCap = Paint.Cap.ROUND
        color = theme.ringGradientColors.firstOrNull()?.toArgb() ?: theme.primary.toArgb()
      }

      canvas.save()
      canvas.rotate(-12f, cx, cy)
      val ringOval = RectF(cx - ringRx, cy - ringRy, cx + ringRx, cy + ringRy)
      canvas.drawArc(ringOval, 180f, 180f, false, ringPaint)

      val planetPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(
          0f, cy - planetR, 0f, cy + planetR,
          intArrayOf(theme.planetTopColor.toArgb(), theme.planetBottomColor.toArgb()),
          null,
          Shader.TileMode.CLAMP
        )
      }
      canvas.drawCircle(cx, cy, planetR, planetPaint)

      val eyePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = theme.eyeColor.toArgb() }
      val eyeRx = 2.5f * s
      val eyeRy = 4.3f * s
      val eyeCy = cy - 9.8f * s
      canvas.drawRoundRect(
        RectF(cx - 4.5f * s - eyeRx, eyeCy - eyeRy, cx - 4.5f * s + eyeRx, eyeCy + eyeRy),
        eyeRx, eyeRx, eyePaint
      )
      canvas.drawRoundRect(
        RectF(cx + 4.5f * s - eyeRx, eyeCy - eyeRy, cx + 4.5f * s + eyeRx, eyeCy + eyeRy),
        eyeRx, eyeRx, eyePaint
      )
      canvas.drawArc(ringOval, 0f, 180f, false, ringPaint)
      canvas.restore()

      val hasKhmer = moodLabel.any { it.code in 0x1780..0x17FF } || themeLabel.any { it.code in 0x1780..0x17FF }
      val baseTf = if (hasKhmer) {
        runCatching { ResourcesCompat.getFont(context, R.font.noto_sans_khmer) }.getOrNull() ?: Typeface.DEFAULT
      } else {
        Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
      }

      val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        textAlign = Paint.Align.CENTER
        textSize = 48f
        typeface = Typeface.create(baseTf, Typeface.BOLD)
      }
      canvas.drawText(moodLabel, cx, 535f, titlePaint)

      val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = theme.primary.toArgb()
        textAlign = Paint.Align.CENTER
        textSize = 32f
        typeface = Typeface.create(baseTf, Typeface.BOLD)
      }
      canvas.drawText("AURIX · $themeLabel · $unlockedBadges/$totalBadges Badges", cx, 600f, subPaint)

      // Stats footer card
      val statsRect = RectF(72f, 700f, w - 72f, 940f)
      val statsBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x18FFFFFF }
      canvas.drawRoundRect(statsRect, 36f, 36f, statsBg)

      val valPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        textAlign = Paint.Align.CENTER
        textSize = 52f
        typeface = Typeface.create(baseTf, Typeface.BOLD)
      }
      val lblPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xB0FFFFFF.toInt()
        textAlign = Paint.Align.CENTER
        textSize = 26f
        typeface = baseTf
      }

      val col1 = w * 0.23f
      val col2 = w * 0.50f
      val col3 = w * 0.77f
      canvas.drawText(totalPlays.toString(), col1, 810f, valPaint)
      canvas.drawText("PLAYS", col1, 865f, lblPaint)
      canvas.drawText(favoritesCount.toString(), col2, 810f, valPaint)
      canvas.drawText("LIKED", col2, 865f, lblPaint)
      canvas.drawText(topVibeLabel.take(10), col3, 810f, valPaint)
      canvas.drawText("VIBE", col3, 865f, lblPaint)

      val brandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x88FFFFFF.toInt()
        textAlign = Paint.Align.CENTER
        textSize = 24f
        typeface = Typeface.create(baseTf, Typeface.BOLD)
      }
      canvas.drawText("AURIX MUSIC · ORBI COMPANION", cx, 1035f, brandPaint)

      val outFile = File(context.cacheDir, "orbi_mood_sticker_${mood.id}.png")
      FileOutputStream(outFile).use { fos ->
        bmp.compress(Bitmap.CompressFormat.PNG, 95, fos)
      }
      val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", outFile)
      withContext(Dispatchers.Main) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
          type = "image/png"
          putExtra(Intent.EXTRA_STREAM, uri)
          putExtra(Intent.EXTRA_TEXT, "My Orbi Companion ($moodLabel · $themeLabel) on AURIX")
          addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, null))
      }
    } finally {
      if (!bmp.isRecycled) bmp.recycle()
    }
  }
}
