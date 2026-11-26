package com.example.ui.screens.onboarding

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.AurixDesignTokens
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private data class Slide1DemoRow(
  val title: String,
  val subtitle: String,
  val badge: String,
  val baseHue: Float
)

/**
 * SLIDE 1 — "All your music. One place."
 * Animated YouTube + local phone library search demo with exact timings:
 * - Types "Hong" starting at 500ms, 190ms/char + blinking 2dp accent caret
 * - 3 rows fade/slide in (translateY 16 -> 0, scale 0.97 -> 1, 550ms) at 1.4s, 1.6s, 1.8s
 * - Row 1 picked at 2.5s (fill .10, accent border, scale 1.02, glow; others opacity .4, blur 2.5dp)
 * - Mini-player rises at 3.1s with 34dp cover & 4-bar equalizer
 */
@Composable
fun OnboardingSlide1Stage(
  isActive: Boolean,
  replaySeed: Int,
  accentColor: Color,
  fontFamily: FontFamily,
  hazeState: HazeState?,
  isCompactHeight: Boolean,
  isTallScreen: Boolean,
  reducedMotion: Boolean,
  localFormatBadge: String,
  modifier: Modifier = Modifier
) {
  val targetQuery = "Hong"
  val onPhoneLabel = stringResource(id = R.string.onboarding_location)
  val demoCaption = stringResource(id = R.string.onboarding_demo)

  var typedCount by remember { mutableIntStateOf(if (reducedMotion) targetQuery.length else 0) }
  var row1Visible by remember { mutableStateOf(reducedMotion) }
  var row2Visible by remember { mutableStateOf(reducedMotion) }
  var row3Visible by remember { mutableStateOf(reducedMotion) }
  var row1Picked by remember { mutableStateOf(reducedMotion) }
  var miniPlayerVisible by remember { mutableStateOf(reducedMotion) }

  LaunchedEffect(isActive, replaySeed, reducedMotion) {
    if (!isActive) return@LaunchedEffect
    if (reducedMotion) {
      typedCount = targetQuery.length
      row1Visible = true
      row2Visible = true
      row3Visible = true
      row1Picked = true
      miniPlayerVisible = true
      return@LaunchedEffect
    }
    typedCount = 0
    row1Visible = false
    row2Visible = false
    row3Visible = false
    row1Picked = false
    miniPlayerVisible = false

    // Timeline anchored cleanly from 0ms when slide becomes active
    launch {
      delay(500L)
      for (i in 1..targetQuery.length) {
        typedCount = i
        if (i < targetQuery.length) delay(190L)
      }
    }
    launch { delay(1400L); row1Visible = true }
    launch { delay(1600L); row2Visible = true }
    launch { delay(1800L); row3Visible = true }
    launch { delay(2500L); row1Picked = true }
    launch { delay(3100L); miniPlayerVisible = true }
  }

  val caretTransition = if (isActive && !reducedMotion) {
    rememberInfiniteTransition(label = "slide1_caret")
  } else {
    null
  }
  val caretAlphaState = caretTransition?.animateFloat(
    initialValue = 1f,
    targetValue = 0f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = 520, easing = LinearEasing),
      repeatMode = RepeatMode.Reverse
    ),
    label = "slide1_caret_alpha"
  )

  val rows = remember(onPhoneLabel, localFormatBadge) {
    listOf(
      Slide1DemoRow("Hong Kong", "The Weeknd · YouTube", "HD", 160f),
      Slide1DemoRow("Hong Kong Nights", "Kora Vale · YouTube", "HD", 20f),
      Slide1DemoRow("Hong Kong", "The Weeknd · $onPhoneLabel", localFormatBadge, 290f)
    )
  }
  val visibleFlags = listOf(row1Visible, row2Visible, row3Visible)
  val rowGap = if (isTallScreen) 10.dp else 7.dp

  Column(
    modifier = modifier.fillMaxSize(),
    verticalArrangement = Arrangement.spacedBy(rowGap, Alignment.CenterVertically),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    // Glass search bar (radius 18, padding 12x16, 15sp/600, minHeight 46, glow shadow Accent A)
    val searchShape = RoundedCornerShape(18.dp)
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .heightIn(min = 46.dp)
        .shadow(
          elevation = 16.dp,
          shape = searchShape,
          ambientColor = accentColor.copy(alpha = 0.55f),
          spotColor = accentColor.copy(alpha = 0.55f)
        )
        .onboardingGlass(shape = searchShape, hazeState = hazeState)
        .padding(horizontal = 16.dp, vertical = 12.dp)
        .testTag("onboarding_s1_search_bar"),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      OnboardingMagnifierIcon(color = AurixDesignTokens.TextMuted, modifier = Modifier.size(18.dp))
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
          text = targetQuery.take(typedCount),
          color = AurixDesignTokens.TextPrimary,
          fontFamily = fontFamily,
          fontSize = 15.sp,
          fontWeight = FontWeight.SemiBold,
          maxLines = 1
        )
        Spacer(modifier = Modifier.width(2.dp))
        Box(
          modifier = Modifier
            .width(2.dp)
            .height(17.dp)
            .graphicsLayer { alpha = caretAlphaState?.value ?: 1f }
            .background(accentColor, RoundedCornerShape(1.dp))
        )
      }
    }

    // 3 result rows (hide 3rd row on compact height <= 700dp)
    val rowCount = if (isCompactHeight) 2 else 3
    for (idx in 0 until rowCount) {
      val row = rows[idx]
      val shown = visibleFlags[idx]
      val isPicked = idx == 0 && row1Picked
      val isDimmed = idx != 0 && row1Picked

      val alphaState = animateFloatAsState(
        targetValue = when {
          !shown -> 0f
          isDimmed -> 0.40f
          else -> 1f
        },
        animationSpec = if (reducedMotion) snap() else tween(550, easing = AurixDesignTokens.MainEasing),
        label = "s1_row_alpha_$idx"
      )
      val offsetYState = animateFloatAsState(
        targetValue = if (shown) 0f else 16f,
        animationSpec = if (reducedMotion) snap() else tween(550, easing = AurixDesignTokens.MainEasing),
        label = "s1_row_y_$idx"
      )
      val scaleState = animateFloatAsState(
        targetValue = when {
          !shown -> 0.97f
          isPicked -> 1.02f
          else -> 1f
        },
        animationSpec = if (reducedMotion) snap() else tween(550, easing = AurixDesignTokens.MainEasing),
        label = "s1_row_scale_$idx"
      )

      val rowShape = RoundedCornerShape(18.dp)
      val blurMod = if (isDimmed && !reducedMotion) Modifier.blur(2.5.dp) else Modifier
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .graphicsLayer {
            alpha = alphaState.value
            translationY = offsetYState.value * density
            val s = scaleState.value
            scaleX = s
            scaleY = s
          }
          .then(blurMod)
          .then(
            if (isPicked) {
              Modifier.shadow(
                elevation = 14.dp,
                shape = rowShape,
                ambientColor = accentColor.copy(alpha = 0.55f),
                spotColor = accentColor.copy(alpha = 0.55f)
              )
            } else {
              Modifier
            }
          )
          .onboardingGlass(
            shape = rowShape,
            hazeState = hazeState,
            fillColor = if (isPicked) AurixDesignTokens.GlassPickedFill else AurixDesignTokens.GlassFill,
            borderColor = if (isPicked) accentColor else AurixDesignTokens.GlassBorder
          )
          .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        Box(
          modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(AurixDesignTokens.coverGradientBrush(row.baseHue))
        )
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = row.title,
            color = AurixDesignTokens.TextPrimary,
            fontFamily = fontFamily,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
          Text(
            text = row.subtitle,
            color = AurixDesignTokens.TextMuted,
            fontFamily = fontFamily,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }
        Box(
          modifier = Modifier
            .clip(RoundedCornerShape(99.dp))
            .border(1.dp, AurixDesignTokens.BadgeBorder, RoundedCornerShape(99.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
        ) {
          Text(
            text = row.badge,
            color = AurixDesignTokens.TextMuted,
            fontFamily = fontFamily,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold
          )
        }
      }
    }

    // Mini-player rising at 3.1s (radius 20, padding 10x14, 34dp cover hsl 160->210, 4-bar equalizer)
    val miniAlphaState = animateFloatAsState(
      targetValue = if (miniPlayerVisible) 1f else 0f,
      animationSpec = if (reducedMotion) snap() else tween(550, easing = AurixDesignTokens.MainEasing),
      label = "s1_mini_alpha"
    )
    val miniYState = animateFloatAsState(
      targetValue = if (miniPlayerVisible) 0f else 18f,
      animationSpec = if (reducedMotion) snap() else tween(550, easing = AurixDesignTokens.SpringEasing),
      label = "s1_mini_y"
    )
    val miniShape = RoundedCornerShape(20.dp)
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .graphicsLayer {
          alpha = miniAlphaState.value
          translationY = miniYState.value * density
        }
        .onboardingGlass(shape = miniShape, hazeState = hazeState)
        .padding(horizontal = 14.dp, vertical = 10.dp)
        .testTag("onboarding_s1_mini_player"),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      Box(
        modifier = Modifier
          .size(34.dp)
          .clip(RoundedCornerShape(10.dp))
          .background(AurixDesignTokens.coverGradientBrush(160f))
      )
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = "Hong Kong",
          color = AurixDesignTokens.TextPrimary,
          fontFamily = fontFamily,
          fontSize = 13.sp,
          fontWeight = FontWeight.SemiBold,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
        Text(
          text = "The Weeknd",
          color = AurixDesignTokens.TextMuted,
          fontFamily = fontFamily,
          fontSize = 11.sp,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
      }
      OnboardingEqualizer(
        accentColor = accentColor,
        isActive = isActive && miniPlayerVisible,
        reducedMotion = reducedMotion,
        height = 16.dp
      )
    }

    Text(
      text = demoCaption,
      color = AurixDesignTokens.TextMuted.copy(alpha = 0.7f),
      fontFamily = fontFamily,
      fontSize = 10.sp
    )
  }
}

/**
 * SLIDE 2 — "Lyrics that move with you."
 * Real karaoke-filled synced lyrics window (176dp, 44dp rows, top/bottom alpha mask, active scale 1.08,
 * inactive opacity .45 & blur 3.5dp, scroll translateY = 66 - activeIndex*44) + opt-in audio engine.
 */
@Composable
fun OnboardingSlide2Stage(
  isActive: Boolean,
  replaySeed: Int,
  accentColor: Color,
  fontFamily: FontFamily,
  hazeState: HazeState?,
  reducedMotion: Boolean,
  lyrics: List<OnboardingLyricLine>,
  audioEngine: OnboardingDemoAudioEngine,
  modifier: Modifier = Modifier
) {
  val captionText = stringResource(id = R.string.onboarding_tap2)
  val speakerCd = stringResource(id = R.string.onboarding_play_demo_cd)

  var soundOn by remember { mutableStateOf(false) }
  var clockSec by remember { mutableFloatStateOf(0f) }
  val totalSec = remember(soundOn, audioEngine.totalDurationSeconds) {
    if (soundOn) audioEngine.totalDurationSeconds else OnboardingDemoAudioEngine.LOOP_DURATION_SECONDS
  }

  // Turn off demo sound whenever leaving Slide 2 or disposing
  LaunchedEffect(isActive) {
    if (!isActive && soundOn) {
      audioEngine.stop()
      soundOn = false
    }
  }
  DisposableEffect(Unit) {
    onDispose {
      audioEngine.stop()
    }
  }

  // Reset clock when re-entering slide while muted
  LaunchedEffect(isActive, replaySeed) {
    if (isActive && !soundOn) {
      clockSec = 0f
    }
  }

  // Frame-synced clock (follows audio currentTime when sound is on; timer loop when muted)
  LaunchedEffect(isActive, soundOn, totalSec) {
    if (!isActive) return@LaunchedEffect
    var lastNanos = 0L
    while (true) {
      withFrameNanos { frameNanos ->
        if (soundOn && audioEngine.isPlaying) {
          clockSec = audioEngine.currentTimeSeconds()
        } else {
          if (lastNanos != 0L) {
            val dt = ((frameNanos - lastNanos) / 1_000_000_000f).coerceIn(0f, 0.1f)
            clockSec = (clockSec + dt) % totalSec
          }
          lastNanos = frameNanos
        }
      }
    }
  }

  val activeIdx = OnboardingDemoAudioEngine.activeLineIndex(clockSec, lyrics)
  val scrollYState = animateFloatAsState(
    targetValue = 66f - activeIdx * 44f,
    animationSpec = if (reducedMotion) snap() else tween(600, easing = AurixDesignTokens.MainEasing),
    label = "s2_lyrics_scroll"
  )

  Column(
    modifier = modifier.fillMaxSize(),
    verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    // Now-playing header: 62x62 radius-16 cover with glow + title/artist + 44dp speaker button
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      val coverShape = RoundedCornerShape(16.dp)
      Box(
        modifier = Modifier
          .size(62.dp)
          .shadow(
            elevation = 16.dp,
            shape = coverShape,
            ambientColor = accentColor.copy(alpha = 0.5f),
            spotColor = accentColor.copy(alpha = 0.5f)
          )
          .clip(coverShape)
          .background(AurixDesignTokens.coverGradientBrush(160f))
      )
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = "Hong Kong",
          color = AurixDesignTokens.TextPrimary,
          fontFamily = fontFamily,
          fontSize = 17.sp,
          fontWeight = FontWeight.Bold,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
        Text(
          text = "The Weeknd",
          color = AurixDesignTokens.TextMuted,
          fontFamily = fontFamily,
          fontSize = 12.sp,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
      }
      Box(
        modifier = Modifier
          .size(44.dp)
          .onboardingFocusRing(CircleShape)
          .then(
            if (soundOn) {
              Modifier
                .clip(CircleShape)
                .background(accentColor)
            } else {
              Modifier.onboardingGlass(shape = CircleShape, hazeState = hazeState)
            }
          )
          .semantics { contentDescription = speakerCd }
          .clickable(role = Role.Button) {
            if (soundOn) {
              audioEngine.stop()
              soundOn = false
            } else {
              val started = audioEngine.start(clockSec)
              soundOn = started
            }
          }
          .testTag("onboarding_s2_speaker_button"),
        contentAlignment = Alignment.Center
      ) {
        OnboardingSpeakerIcon(
          soundOn = soundOn,
          color = if (soundOn) AurixDesignTokens.DarkInk else AurixDesignTokens.TextPrimary
        )
      }
    }

    // Lyrics window: 176dp tall, masked top & bottom (0->1 at 30%, 1->0 at 70%)
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .height(176.dp)
        .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
        .drawWithContent {
          drawContent()
          drawRect(
            brush = Brush.verticalGradient(
              0.0f to Color.Transparent,
              0.30f to Color.Black,
              0.70f to Color.Black,
              1.0f to Color.Transparent
            ),
            blendMode = BlendMode.DstIn
          )
        },
      contentAlignment = Alignment.TopCenter
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .graphicsLayer {
            translationY = scrollYState.value * density
          },
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        lyrics.forEachIndexed { idx, line ->
          val isCurrentLine = idx == activeIdx
          val fillFraction = OnboardingDemoAudioEngine.computeKaraokeFill(clockSec, idx, lyrics, totalSec)
          val lineScaleState = animateFloatAsState(
            targetValue = if (isCurrentLine) 1.08f else 0.90f,
            animationSpec = if (reducedMotion) snap() else tween(450, easing = AurixDesignTokens.MainEasing),
            label = "s2_line_scale_$idx"
          )
          val lineAlphaState = animateFloatAsState(
            targetValue = when {
              isCurrentLine -> 1.0f
              reducedMotion -> 0.70f
              else -> 0.45f
            },
            animationSpec = if (reducedMotion) snap() else tween(450, easing = AurixDesignTokens.MainEasing),
            label = "s2_line_alpha_$idx"
          )
          val lineBlurMod = if (!isCurrentLine && !reducedMotion) Modifier.blur(3.5.dp) else Modifier

          Box(
            modifier = Modifier
              .fillMaxWidth()
              .height(44.dp)
              .graphicsLayer {
                alpha = lineAlphaState.value
                val s = lineScaleState.value
                scaleX = s
                scaleY = s
              }
              .then(lineBlurMod),
            contentAlignment = Alignment.Center
          ) {
            // Base unfilled line (rgba(255,255,255,.32))
            Text(
              text = line.text,
              color = Color.White.copy(alpha = 0.32f),
              fontFamily = fontFamily,
              fontSize = 18.sp,
              fontWeight = FontWeight.ExtraBold,
              maxLines = 1,
              softWrap = false,
              textAlign = TextAlign.Center
            )
            // Karaoke left-to-right white fill clipped cleanly on GPU
            if (fillFraction > 0.001f) {
              Text(
                text = line.text,
                color = Color.White,
                fontFamily = fontFamily,
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                softWrap = false,
                textAlign = TextAlign.Center,
                modifier = Modifier.drawWithContent {
                  clipRect(right = size.width * fillFraction) {
                    this@drawWithContent.drawContent()
                  }
                }
              )
            }
          }
        }
      }
    }

    // Progress bar (5dp, radius 3dp, track rgba(255,255,255,.14), fill gradient A -> B)
    val progress = (clockSec / totalSec.coerceAtLeast(1f)).coerceIn(0f, 1f)
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(5.dp)
          .clip(RoundedCornerShape(3.dp))
          .background(AurixDesignTokens.ProgressTrack)
      ) {
        Box(
          modifier = Modifier
            .fillMaxWidth(progress)
            .fillMaxHeight()
            .clip(RoundedCornerShape(3.dp))
            .background(AurixDesignTokens.accentGradientBrush(accentColor))
        )
      }
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        Text(
          text = OnboardingDemoAudioEngine.formatSecondsTabular(clockSec),
          style = TextStyle(
            color = AurixDesignTokens.TextMuted,
            fontFamily = fontFamily,
            fontSize = 10.sp,
            fontFeatureSettings = "tnum"
          )
        )
        Text(
          text = OnboardingDemoAudioEngine.formatSecondsTabular(totalSec),
          style = TextStyle(
            color = AurixDesignTokens.TextMuted,
            fontFamily = fontFamily,
            fontSize = 10.sp,
            fontFeatureSettings = "tnum"
          )
        )
      }
    }

    Text(
      text = captionText,
      color = AurixDesignTokens.TextMuted.copy(alpha = 0.7f),
      fontFamily = fontFamily,
      fontSize = 10.sp
    )
  }
}

/**
 * SLIDE 3 — "Make it yours."
 * 150x150 radius-30 gradient cover tile with pop animation, transport controls,
 * 4 x 30dp accent swatches (auto-cycles every 1.1s until chosen; resets on leave if unchosen),
 * plus a compact real Backdrop picker wired to app settings.
 */
@Composable
fun OnboardingSlide3Stage(
  isActive: Boolean,
  accentColor: Color,
  selectedPreset: AurixDesignTokens.AccentPreset,
  selectedBackdrop: AurixDesignTokens.BackdropPreset,
  userChoseAccent: Boolean,
  fontFamily: FontFamily,
  hazeState: HazeState?,
  reducedMotion: Boolean,
  onSelectSwatch: (AurixDesignTokens.AccentPreset) -> Unit,
  onAutoCycleSwatch: (AurixDesignTokens.AccentPreset) -> Unit,
  onSelectBackdrop: (AurixDesignTokens.BackdropPreset) -> Unit,
  modifier: Modifier = Modifier
) {
  val captionText = stringResource(id = R.string.onboarding_tap)
  val tileScale = remember { Animatable(1f) }
  var popTrigger by remember { mutableIntStateOf(0) }

  // Pop tile (0.92 -> 1, 450ms SpringEasing) whenever swatch is tapped
  LaunchedEffect(popTrigger, reducedMotion) {
    if (popTrigger == 0) return@LaunchedEffect
    if (reducedMotion) {
      tileScale.snapTo(1f)
    } else {
      tileScale.snapTo(0.92f)
      tileScale.animateTo(
        targetValue = 1f,
        animationSpec = tween(durationMillis = 450, easing = AurixDesignTokens.SpringEasing)
      )
    }
  }

  // Auto-cycle accent every 1.1s while Slide 3 is visible until user picks one
  LaunchedEffect(isActive, userChoseAccent, reducedMotion) {
    if (!isActive || userChoseAccent || reducedMotion) return@LaunchedEffect
    var idx = AurixDesignTokens.AccentPresets.indexOf(selectedPreset).coerceAtLeast(0)
    while (true) {
      delay(1100L)
      idx = (idx + 1) % AurixDesignTokens.AccentPresets.size
      onAutoCycleSwatch(AurixDesignTokens.AccentPresets[idx])
    }
  }

  Column(
    modifier = modifier.fillMaxSize(),
    verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    // 150x150 radius-30 gradient cover tile
    val tileShape = RoundedCornerShape(30.dp)
    Box(
      modifier = Modifier
        .size(150.dp)
        .graphicsLayer {
          val s = tileScale.value
          scaleX = s
          scaleY = s
        }
        .shadow(
          elevation = 24.dp,
          shape = tileShape,
          ambientColor = accentColor.copy(alpha = 0.65f),
          spotColor = accentColor.copy(alpha = 0.65f)
        )
        .clip(tileShape)
        .background(AurixDesignTokens.accentGradientBrush(accentColor)),
      contentAlignment = Alignment.Center
    ) {
      OnboardingMusicNoteIcon(color = Color.White, modifier = Modifier.size(64.dp), strokeDp = 1.8.dp)
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
      Text(
        text = "Hong Kong",
        color = AurixDesignTokens.TextPrimary,
        fontFamily = fontFamily,
        fontSize = 16.sp,
        fontWeight = FontWeight.ExtraBold,
        maxLines = 1
      )
      Text(
        text = "The Weeknd",
        color = AurixDesignTokens.TextMuted,
        fontFamily = fontFamily,
        fontSize = 12.sp,
        maxLines = 1
      )
    }

    // Transport controls (gap 22dp): prev, 50dp accent circle with dark play icon, next
    Row(
      horizontalArrangement = Arrangement.spacedBy(22.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Icon(
        imageVector = Icons.Rounded.SkipPrevious,
        contentDescription = null,
        tint = AurixDesignTokens.TextPrimary.copy(alpha = 0.85f),
        modifier = Modifier.size(26.dp)
      )
      Box(
        modifier = Modifier
          .size(50.dp)
          .clip(CircleShape)
          .background(accentColor),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = Icons.Rounded.PlayArrow,
          contentDescription = null,
          tint = AurixDesignTokens.DarkInk,
          modifier = Modifier.size(28.dp)
        )
      }
      Icon(
        imageVector = Icons.Rounded.SkipNext,
        contentDescription = null,
        tint = AurixDesignTokens.TextPrimary.copy(alpha = 0.85f),
        modifier = Modifier.size(26.dp)
      )
    }

    // Four 30dp color swatches (gap 12dp): #43C7F4, #A855F7, #EF4444, #22C55E
    Row(
      horizontalArrangement = Arrangement.spacedBy(12.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      AurixDesignTokens.AccentPresets.forEach { preset ->
        val isSelected = preset.id == selectedPreset.id
        val swatchLabel = stringResource(id = preset.labelRes)
        val swatchScaleState = animateFloatAsState(
          targetValue = if (isSelected) 1.22f else 1.0f,
          animationSpec = if (reducedMotion) snap() else tween(320, easing = AurixDesignTokens.SpringEasing),
          label = "swatch_scale_${preset.id}"
        )
        Box(
          modifier = Modifier
            .size(30.dp)
            .graphicsLayer {
              val s = swatchScaleState.value
              scaleX = s
              scaleY = s
            }
            .onboardingFocusRing(CircleShape)
            .clip(CircleShape)
            .background(preset.color)
            .then(if (isSelected) Modifier.border(2.dp, Color.White, CircleShape) else Modifier)
            .semantics {
              contentDescription = swatchLabel
              selected = isSelected
            }
            .clickable(role = Role.RadioButton) {
              popTrigger++
              onSelectSwatch(preset)
            }
            .testTag("onboarding_swatch_${preset.id}")
        )
      }
    }

    // Real Backdrop picker wired to app Dynamic Background settings
    Row(
      modifier = Modifier
        .onboardingGlass(shape = RoundedCornerShape(99.dp), hazeState = hazeState)
        .padding(3.dp),
      horizontalArrangement = Arrangement.spacedBy(4.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      AurixDesignTokens.BackdropPreset.entries.forEach { preset ->
        val isPicked = preset == selectedBackdrop
        val bgColor by animateColorAsState(
          targetValue = if (isPicked) AurixDesignTokens.GlassActiveFill else Color.Transparent,
          animationSpec = if (reducedMotion) snap() else tween(220),
          label = "backdrop_chip_${preset.id}"
        )
        Box(
          modifier = Modifier
            .onboardingFocusRing(RoundedCornerShape(99.dp))
            .clip(RoundedCornerShape(99.dp))
            .background(bgColor)
            .clickable(role = Role.RadioButton) { onSelectBackdrop(preset) }
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .testTag("onboarding_backdrop_${preset.id}")
        ) {
          Text(
            text = stringResource(id = preset.labelRes),
            color = if (isPicked) AurixDesignTokens.TextPrimary else AurixDesignTokens.TextMuted,
            fontFamily = fontFamily,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
          )
        }
      }
    }

    Text(
      text = captionText,
      color = AurixDesignTokens.TextMuted.copy(alpha = 0.7f),
      fontFamily = fontFamily,
      fontSize = 10.sp
    )
  }
}

/**
 * SLIDE 4 — "Ready to listen?"
 * 170x170 Orb with 130x130 Orbi mascot, 1.5dp rotating dashed ring (12s), breathing accent glow (2.4s),
 * 2 expanding 2dp ripple rings (2.8s / 1.4s delay), 3 floating notes "♪ ♫ ♪" (3s), and glass track pill.
 */
@Composable
fun OnboardingSlide4Stage(
  isActive: Boolean,
  replaySeed: Int,
  accentColor: Color,
  fontFamily: FontFamily,
  hazeState: HazeState?,
  reducedMotion: Boolean,
  modifier: Modifier = Modifier
) {
  val loopTransition = if (isActive && !reducedMotion) {
    rememberInfiniteTransition(label = "s4_orb_loop")
  } else {
    null
  }

  val dashedRotState = loopTransition?.animateFloat(
    initialValue = 0f,
    targetValue = 360f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = 12000, easing = LinearEasing),
      repeatMode = RepeatMode.Restart
    ),
    label = "s4_dashed_rot"
  )
  val glowBreathScaleState = loopTransition?.animateFloat(
    initialValue = 1.0f,
    targetValue = 1.15f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
      repeatMode = RepeatMode.Reverse
    ),
    label = "s4_glow_scale"
  )
  val glowBreathAlphaState = loopTransition?.animateFloat(
    initialValue = 0.35f,
    targetValue = 0.55f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
      repeatMode = RepeatMode.Reverse
    ),
    label = "s4_glow_alpha"
  )
  val ripple1Progress = loopTransition?.animateFloat(
    initialValue = 0f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = 2800, easing = LinearEasing),
      repeatMode = RepeatMode.Restart
    ),
    label = "s4_ripple_1"
  )
  val ripple2Progress = loopTransition?.animateFloat(
    initialValue = 0f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = 2800, easing = LinearEasing),
      repeatMode = RepeatMode.Restart,
      initialStartOffset = androidx.compose.animation.core.StartOffset(1400)
    ),
    label = "s4_ripple_2"
  )

  // Three floating notes: "♪ ♫ ♪" (20sp, accent / violet / accent, rising 70dp, 14deg tilt, 3s, delays 0 / 900 / 1800ms)
  val noteDelays = remember { intArrayOf(0, 900, 1800) }
  val noteSymbols = remember { listOf("♪", "♫", "♪") }
  val noteColors = remember(accentColor) { listOf(accentColor, AurixDesignTokens.AccentB, accentColor) }
  val noteOffsetsX = remember { listOf((-78).dp, 76.dp, (-62).dp) }
  val noteOffsetsY = remember { listOf(28.dp, 8.dp, (-28).dp) }
  val noteProgressStates = List(3) { i ->
    loopTransition?.animateFloat(
      initialValue = 0f,
      targetValue = 1f,
      animationSpec = infiniteRepeatable(
        animation = tween(durationMillis = 3000, easing = LinearEasing),
        repeatMode = RepeatMode.Restart,
        initialStartOffset = androidx.compose.animation.core.StartOffset(noteDelays[i])
      ),
      label = "s4_note_$i"
    )
  }

  Column(
    modifier = modifier.fillMaxSize(),
    verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    Box(
      modifier = Modifier.size(214.dp),
      contentAlignment = Alignment.Center
    ) {
      // Soft accent radial glow (inset -18dp => 206dp around 170dp orb)
      Canvas(
        modifier = Modifier
          .size(206.dp)
          .graphicsLayer {
            val s = glowBreathScaleState?.value ?: 1.05f
            scaleX = s
            scaleY = s
            alpha = glowBreathAlphaState?.value ?: 0.45f
          }
      ) {
        drawCircle(
          brush = Brush.radialGradient(
            colors = listOf(accentColor, accentColor.copy(alpha = 0.25f), Color.Transparent)
          )
        )
      }

      // Two 2dp accent ripple rings expanding (scale 0.7 -> 1.7, fading)
      listOf(ripple1Progress, ripple2Progress).forEach { progressState ->
        Canvas(
          modifier = Modifier
            .size(170.dp)
            .graphicsLayer {
              val p = progressState?.value ?: 0.35f
              val s = 0.7f + p * 1.0f
              scaleX = s
              scaleY = s
              alpha = if (reducedMotion) 0.18f else (1f - p) * 0.55f
            }
        ) {
          drawCircle(
            color = accentColor,
            style = Stroke(width = 2.dp.toPx())
          )
        }
      }

      // 170x170 orb with 1.5dp rotating dashed ring (rgba(255,255,255,.25))
      Canvas(
        modifier = Modifier
          .size(170.dp)
          .graphicsLayer {
            rotationZ = dashedRotState?.value ?: 0f
          }
      ) {
        drawCircle(
          color = Color.White.copy(alpha = 0.25f),
          style = Stroke(
            width = 1.5.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 12f), 0f)
          )
        )
      }

      // 130x130 Orbi mascot
      OnboardingOrbiMascot(
        isActive = isActive,
        reducedMotion = reducedMotion,
        replaySeed = replaySeed,
        modifier = Modifier
          .size(130.dp)
          .testTag("onboarding_s4_orbi")
      )

      // Three floating notes "♪ ♫ ♪"
      for (i in 0 until 3) {
        val pState = noteProgressStates[i]
        Text(
          text = noteSymbols[i],
          color = noteColors[i],
          fontSize = 20.sp,
          fontWeight = FontWeight.Bold,
          modifier = Modifier
            .offset(x = noteOffsetsX[i], y = noteOffsetsY[i])
            .graphicsLayer {
              val p = pState?.value ?: 0.35f
              translationY = if (reducedMotion) 0f else -70f * p * density
              rotationZ = if (reducedMotion) 0f else 14f * (if (i % 2 == 0) 1f else -1f) * p
              alpha = if (reducedMotion) {
                0.7f
              } else {
                when {
                  p < 0.15f -> p / 0.15f
                  p > 0.75f -> ((1f - p) / 0.25f).coerceIn(0f, 1f)
                  else -> 1f
                }
              }
            }
        )
      }
    }

    // Glass pill under orb: equalizer + "Hong Kong · The Weeknd" (13sp/600)
    Row(
      modifier = Modifier
        .onboardingGlass(shape = RoundedCornerShape(99.dp), hazeState = hazeState)
        .padding(horizontal = 16.dp, vertical = 10.dp)
        .testTag("onboarding_s4_track_pill"),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      OnboardingEqualizer(
        accentColor = accentColor,
        isActive = isActive,
        reducedMotion = reducedMotion,
        height = 14.dp
      )
      Text(
        text = "Hong Kong · The Weeknd",
        color = AurixDesignTokens.TextPrimary,
        fontFamily = fontFamily,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1
      )
    }
  }
}
