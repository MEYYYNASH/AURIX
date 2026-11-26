package com.example.ui.screens

import android.os.Build
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.LocaleHelper
import com.example.R
import com.example.model.LyricsResult
import com.example.model.Song
import com.example.ui.screens.onboarding.OnboardingArrowLeftIcon
import com.example.ui.screens.onboarding.OnboardingArrowRightIcon
import com.example.ui.screens.onboarding.OnboardingDemoAudioEngine
import com.example.ui.screens.onboarding.OnboardingSlide1Stage
import com.example.ui.screens.onboarding.OnboardingSlide2Stage
import com.example.ui.screens.onboarding.OnboardingSlide3Stage
import com.example.ui.screens.onboarding.OnboardingSlide4Stage
import com.example.ui.screens.onboarding.OnboardingSwipePhysics
import com.example.ui.screens.onboarding.extractLocalAudioFormatBadge
import com.example.ui.screens.onboarding.onboardingFocusRing
import com.example.ui.screens.onboarding.onboardingGlass
import com.example.ui.theme.AurixDesignTokens
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.hypot

internal const val ONBOARDING_SLIDE_COUNT = 4

@Composable
fun OnboardingScreen(
  currentLanguage: String = LocaleHelper.LANG_EN,
  initialAccentHex: String = AurixDesignTokens.AccentPresets.first().hex,
  initialDynamicBg: Boolean = true,
  initialDynamicBgDim: Float = 0.40f,
  localSongs: List<Song> = emptyList(),
  liveLyricsResult: LyricsResult? = null,
  audioSrc: String? = null,
  reducedMotion: Boolean = false,
  onLanguageChanged: (String) -> Unit = {},
  onAccentColorChanged: (String) -> Unit = {},
  onBackdropChanged: (AurixDesignTokens.BackdropPreset) -> Unit = {},
  onSkipped: () -> Unit = {},
  onFinished: () -> Unit
) {
  val scope = rememberCoroutineScope()
  val density = LocalDensity.current
  val hazeState = remember { HazeState() }

  val isKhmer = LocaleHelper.normalizeLanguage(currentLanguage) == LocaleHelper.LANG_KM
  val fontFamily = remember(isKhmer) { AurixDesignTokens.fontFamily(isKhmer) }

  var currentSlide by remember { mutableIntStateOf(0) }
  // Incremented each time a slide becomes current so entrance animations replay every time
  var slideReplaySeeds by remember { mutableStateOf(intArrayOf(1, 0, 0, 0)) }

  // Slide 3 live accent + backdrop state
  var selectedAccentPreset by remember {
    mutableStateOf(AurixDesignTokens.findPresetForHex(initialAccentHex))
  }
  var userChoseAccent by remember { mutableStateOf(false) }
  var selectedBackdrop by remember {
    mutableStateOf(AurixDesignTokens.BackdropPreset.fromSettings(initialDynamicBg, initialDynamicBgDim))
  }

  // Smooth 0.8s Accent A color transition across the entire screen
  val animatedAccentA by animateColorAsState(
    targetValue = selectedAccentPreset.color,
    animationSpec = if (reducedMotion) snap() else tween(durationMillis = 800, easing = AurixDesignTokens.MainEasing),
    label = "onboarding_accent_a"
  )

  // If user leaves Slide 3 without manually choosing an accent, reset back to default cyan blue
  var previousSlide by remember { mutableIntStateOf(0) }
  LaunchedEffect(currentSlide) {
    if (previousSlide == 2 && currentSlide != 2 && !userChoseAccent) {
      selectedAccentPreset = AurixDesignTokens.AccentPresets.first()
    }
    previousSlide = currentSlide
    val nextSeeds = slideReplaySeeds.copyOf()
    nextSeeds[currentSlide] = nextSeeds[currentSlide] + 1
    slideReplaySeeds = nextSeeds
  }

  // Shared Audio + Lyric Sync engine for Slide 2 (released cleanly on exit)
  val audioEngine = remember(audioSrc) { OnboardingDemoAudioEngine(audioSrc = audioSrc) }
  DisposableEffect(audioEngine) {
    onDispose { audioEngine.release() }
  }
  val demoLyrics = remember(liveLyricsResult) {
    if (liveLyricsResult?.synced?.isNotEmpty() == true) {
      OnboardingDemoAudioEngine.fromRealLyrics(liveLyricsResult)
    } else {
      OnboardingDemoAudioEngine.DEFAULT_LYRICS
    }
  }
  val localBadge = remember(localSongs) { extractLocalAudioFormatBadge(localSongs.firstOrNull()) }

  // Pager state: smooth 0.6s main easing when settling, instant finger follow when dragging
  val animatedPageOffset = remember { Animatable(0f) }
  var isDragging by remember { mutableStateOf(false) }
  var dragDeltaPx by remember { mutableFloatStateOf(0f) }

  fun navigateToSlide(targetIndex: Int) {
    val clamped = targetIndex.coerceIn(0, ONBOARDING_SLIDE_COUNT - 1)
    currentSlide = clamped
    scope.launch {
      if (reducedMotion) {
        animatedPageOffset.snapTo(clamped.toFloat())
      } else {
        animatedPageOffset.animateTo(
          targetValue = clamped.toFloat(),
          animationSpec = tween(durationMillis = 600, easing = AurixDesignTokens.MainEasing)
        )
      }
    }
  }

  // 4-second idle "ready" pulse on the main CTA button
  var isButtonReadyPulse by remember { mutableStateOf(false) }
  LaunchedEffect(currentSlide, reducedMotion) {
    isButtonReadyPulse = false
    if (!reducedMotion) {
      delay(4000L)
      isButtonReadyPulse = true
    }
  }

  // Finish circular reveal state
  var isFinishing by remember { mutableStateOf(false) }
  var buttonCenterInRoot by remember { mutableStateOf(Offset.Zero) }
  val finishRadiusFraction = remember { Animatable(0f) }
  val finishWordmarkProgress = remember { Animatable(0f) }

  fun triggerFinish() {
    if (isFinishing) return
    audioEngine.stop()
    isFinishing = true
    if (userChoseAccent) {
      onAccentColorChanged(selectedAccentPreset.hex)
    }
    onBackdropChanged(selectedBackdrop)
    scope.launch {
      if (reducedMotion) {
        finishRadiusFraction.snapTo(1.5f)
        finishWordmarkProgress.snapTo(1f)
        delay(220L)
        onFinished()
      } else {
        launch {
          finishRadiusFraction.animateTo(
            targetValue = 1.5f,
            animationSpec = tween(durationMillis = 850, easing = AurixDesignTokens.FinishRevealEasing)
          )
        }
        launch {
          delay(500L)
          finishWordmarkProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 600, easing = AurixDesignTokens.MainEasing)
          )
          delay(280L)
          onFinished()
        }
      }
    }
  }

  BoxWithConstraints(
    modifier = Modifier
      .fillMaxSize()
      .background(AurixDesignTokens.Background)
      .focusable()
      .onPreviewKeyEvent { keyEvent ->
        if (keyEvent.type == KeyEventType.KeyDown && !isFinishing) {
          when (keyEvent.key) {
            Key.DirectionRight -> {
              if (currentSlide < ONBOARDING_SLIDE_COUNT - 1) {
                navigateToSlide(currentSlide + 1)
                true
              } else false
            }
            Key.DirectionLeft -> {
              if (currentSlide > 0) {
                navigateToSlide(currentSlide - 1)
                true
              } else false
            }
            else -> false
          }
        } else {
          false
        }
      }
      .testTag("onboarding_root")
  ) {
    val rootMaxHeightDp = maxHeight
    val rootWidthPx = with(density) { maxWidth.toPx() }
    val rootHeightPx = with(density) { rootMaxHeightDp.toPx() }
    val isCompactHeight = rootMaxHeightDp <= 700.dp
    val isTallScreen = rootMaxHeightDp >= 780.dp

    // Background Layer (Haze source): Two blurred drifting circles (blur 80, opacity 0.42)
    Box(
      modifier = Modifier
        .fillMaxSize()
        .haze(state = hazeState)
        .background(AurixDesignTokens.Background)
    ) {
      OnboardingBackgroundGlows(
        currentSlide = currentSlide,
        accentA = animatedAccentA,
        backdropPreset = selectedBackdrop,
        reducedMotion = reducedMotion,
        screenHeightDp = rootMaxHeightDp.value
      )
    }

    // Centered max-width 460dp column (non-scrolling, safe-area insets respected)
    Column(
      modifier = Modifier
        .align(Alignment.Center)
        .fillMaxHeight()
        .widthIn(max = 460.dp)
        .fillMaxWidth()
        .statusBarsPadding()
        .navigationBarsPadding(),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      // b) Progress segments: 4 bars, gap 6dp, padding 16/20/0, height 3dp, radius 2dp
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(start = 20.dp, end = 20.dp, top = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        for (i in 0 until ONBOARDING_SLIDE_COUNT) {
          val fillFractionState = animateFloatAsState(
            targetValue = if (i <= currentSlide) 1f else 0f,
            animationSpec = if (reducedMotion) snap() else tween(550, easing = AurixDesignTokens.MainEasing),
            label = "progress_seg_$i"
          )
          Box(
            modifier = Modifier
              .weight(1f)
              .height(3.dp)
              .clip(RoundedCornerShape(2.dp))
              .background(AurixDesignTokens.ProgressTrack)
          ) {
            Box(
              modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth()
                .graphicsLayer {
                  scaleX = fillFractionState.value
                  transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
                }
                .clip(RoundedCornerShape(2.dp))
                .background(Color.White)
            )
          }
        }
      }

      // c) Top bar (padding 14/20/0): Skip glass pill (left, hidden on last slide) + EN / ខ្មែរ pill (right)
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(start = 20.dp, end = 20.dp, top = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        if (currentSlide < ONBOARDING_SLIDE_COUNT - 1) {
          val skipShape = RoundedCornerShape(99.dp)
          Box(
            modifier = Modifier
              .onboardingFocusRing(skipShape)
              .onboardingGlass(shape = skipShape, hazeState = hazeState)
              .clickable(role = Role.Button) {
                onSkipped()
                navigateToSlide(ONBOARDING_SLIDE_COUNT - 1)
              }
              .padding(horizontal = 16.dp, vertical = 9.dp)
              .testTag("onboarding_skip_button")
          ) {
            Text(
              text = stringResource(id = R.string.onboarding_skip),
              color = AurixDesignTokens.TextPrimary,
              fontFamily = fontFamily,
              fontSize = 13.sp,
              fontWeight = FontWeight.SemiBold
            )
          }
        } else {
          Spacer(modifier = Modifier.width(1.dp))
        }

        // Language toggle glass pill ("EN" / "ខ្មែរ", 12sp/600, padding 7x12)
        val langPillShape = RoundedCornerShape(99.dp)
        Row(
          modifier = Modifier
            .onboardingGlass(shape = langPillShape, hazeState = hazeState)
            .padding(3.dp)
            .testTag("onboarding_language_toggle"),
          verticalAlignment = Alignment.CenterVertically
        ) {
          LanguageSegmentButton(
            label = stringResource(id = R.string.onboarding_lang_en),
            active = !isKhmer,
            fontFamily = AurixDesignTokens.SoraFontFamily,
            onClick = { onLanguageChanged(LocaleHelper.LANG_EN) },
            testTag = "onboarding_lang_en"
          )
          LanguageSegmentButton(
            label = stringResource(id = R.string.onboarding_lang_km),
            active = isKhmer,
            fontFamily = AurixDesignTokens.NotoSansKhmerFontFamily,
            onClick = { onLanguageChanged(LocaleHelper.LANG_KM) },
            testTag = "onboarding_lang_km"
          )
        }
      }

      // d) Slide viewport: custom horizontal swipe pager following finger cleanly (8dp slop, 30% edge resistance, 18% advance)
      val dragSlopPx = with(density) { OnboardingSwipePhysics.DRAG_START_SLOP_DP.dp.toPx() }
      BoxWithConstraints(
        modifier = Modifier
          .weight(1f)
          .fillMaxWidth()
          .pointerInput(currentSlide, isFinishing, reducedMotion) {
            if (isFinishing) return@pointerInput
            val viewportWidth = size.width.toFloat().coerceAtLeast(1f)
            awaitEachGesture {
              val down = awaitFirstDown(requireUnconsumed = false)
              var totalDx = 0f
              var totalDy = 0f
              var horizontalLocked = false
              var verticalLocked = false

              while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) {
                  if (horizontalLocked) {
                    val target = OnboardingSwipePhysics.resolveTargetIndex(
                      rawDragPx = totalDx,
                      viewportWidthPx = viewportWidth,
                      currentIndex = currentSlide,
                      pageCount = ONBOARDING_SLIDE_COUNT
                    )
                    isDragging = false
                    dragDeltaPx = 0f
                    navigateToSlide(target)
                  }
                  break
                }
                val delta = change.positionChange()
                totalDx += delta.x
                totalDy += delta.y

                if (!horizontalLocked && !verticalLocked) {
                  if (abs(totalDx) > dragSlopPx && abs(totalDx) > abs(totalDy)) {
                    horizontalLocked = true
                    isDragging = true
                  } else if (abs(totalDy) > dragSlopPx && abs(totalDy) > abs(totalDx)) {
                    verticalLocked = true
                  }
                }

                if (horizontalLocked) {
                  change.consume()
                  val resisted = OnboardingSwipePhysics.applyEdgeResistance(
                    rawDragPx = totalDx,
                    currentIndex = currentSlide,
                    pageCount = ONBOARDING_SLIDE_COUNT
                  )
                  dragDeltaPx = resisted
                  scope.launch {
                    animatedPageOffset.snapTo(currentSlide - (resisted / viewportWidth))
                  }
                }
              }
            }
          }
      ) {
        val pageWidthDp = maxWidth
        val pageWidthPx = with(density) { pageWidthDp.toPx() }
        val stageWidth = minOf(320.dp, pageWidthDp * 0.84f)
        val stageHeight = minOf(330.dp, rootMaxHeightDp * 0.40f).coerceAtLeast(250.dp)
        val slideGap = if (isCompactHeight) 16.dp else 26.dp

        val titles = listOf(
          stringResource(id = R.string.onboarding_p1_title),
          stringResource(id = R.string.onboarding_p2_title),
          stringResource(id = R.string.onboarding_p3_title),
          stringResource(id = R.string.onboarding_p4_title)
        )
        val bodies = listOf(
          stringResource(id = R.string.onboarding_p1_desc),
          stringResource(id = R.string.onboarding_p2_desc),
          stringResource(id = R.string.onboarding_p3_desc),
          stringResource(id = R.string.onboarding_p4_desc)
        )

        for (pageIndex in 0 until ONBOARDING_SLIDE_COUNT) {
          val isCurrent = pageIndex == currentSlide
          val stageScaleState = animateFloatAsState(
            targetValue = if (isCurrent) 1.0f else 0.90f,
            animationSpec = if (reducedMotion) snap() else tween(600, easing = AurixDesignTokens.MainEasing),
            label = "stage_scale_$pageIndex"
          )
          val stageAlphaState = animateFloatAsState(
            targetValue = if (isCurrent) 1.0f else 0.30f,
            animationSpec = if (reducedMotion) snap() else tween(600, easing = AurixDesignTokens.MainEasing),
            label = "stage_alpha_$pageIndex"
          )

          Column(
            modifier = Modifier
              .fillMaxSize()
              .graphicsLayer {
                translationX = (pageIndex - animatedPageOffset.value) * pageWidthPx
              }
              .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(slideGap, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally
          ) {
            Box(
              modifier = Modifier
                .width(stageWidth)
                .height(stageHeight)
                .graphicsLayer {
                  val s = stageScaleState.value
                  scaleX = s
                  scaleY = s
                  alpha = stageAlphaState.value
                },
              contentAlignment = Alignment.Center
            ) {
              when (pageIndex) {
                0 -> OnboardingSlide1Stage(
                  isActive = isCurrent && !isFinishing,
                  replaySeed = slideReplaySeeds[0],
                  accentColor = animatedAccentA,
                  fontFamily = fontFamily,
                  hazeState = hazeState,
                  isCompactHeight = isCompactHeight,
                  isTallScreen = isTallScreen,
                  reducedMotion = reducedMotion,
                  localFormatBadge = localBadge
                )
                1 -> OnboardingSlide2Stage(
                  isActive = isCurrent && !isFinishing,
                  replaySeed = slideReplaySeeds[1],
                  accentColor = animatedAccentA,
                  fontFamily = fontFamily,
                  hazeState = hazeState,
                  reducedMotion = reducedMotion,
                  lyrics = demoLyrics,
                  audioEngine = audioEngine
                )
                2 -> OnboardingSlide3Stage(
                  isActive = isCurrent && !isFinishing,
                  accentColor = animatedAccentA,
                  selectedPreset = selectedAccentPreset,
                  selectedBackdrop = selectedBackdrop,
                  userChoseAccent = userChoseAccent,
                  fontFamily = fontFamily,
                  hazeState = hazeState,
                  reducedMotion = reducedMotion,
                  onSelectSwatch = { preset ->
                    selectedAccentPreset = preset
                    userChoseAccent = true
                    onAccentColorChanged(preset.hex)
                  },
                  onAutoCycleSwatch = { preset ->
                    if (!userChoseAccent) {
                      selectedAccentPreset = preset
                    }
                  },
                  onSelectBackdrop = { preset ->
                    selectedBackdrop = preset
                    onBackdropChanged(preset)
                  }
                )
                3 -> OnboardingSlide4Stage(
                  isActive = isCurrent && !isFinishing,
                  replaySeed = slideReplaySeeds[3],
                  accentColor = animatedAccentA,
                  fontFamily = fontFamily,
                  hazeState = hazeState,
                  reducedMotion = reducedMotion
                )
              }
            }

            // Text block: Title + Body with 700ms staggered entrance (delay 100ms / 250ms, fade + translateY 16 + blur 6->0)
            OnboardingSlideTextBlock(
              title = titles[pageIndex],
              body = bodies[pageIndex],
              isActive = isCurrent,
              replaySeed = slideReplaySeeds[pageIndex],
              isKhmer = isKhmer,
              isCompactHeight = isCompactHeight,
              fontFamily = fontFamily,
              reducedMotion = reducedMotion
            )
          }
        }
      }

      // f) Bottom bar (padding 6/24/26, gap 12): Back circle (58dp, slides 2..4) + Main gradient button (height 58, radius 29)
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(start = 24.dp, end = 24.dp, top = 6.dp, bottom = 26.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        if (currentSlide > 0) {
          val backCd = stringResource(id = R.string.onboarding_back_cd)
          Box(
            modifier = Modifier
              .size(58.dp)
              .onboardingFocusRing(CircleShape)
              .onboardingGlass(shape = CircleShape, hazeState = hazeState)
              .semantics { contentDescription = backCd }
              .clickable(role = Role.Button) { navigateToSlide(currentSlide - 1) }
              .testTag("onboarding_back_button"),
            contentAlignment = Alignment.Center
          ) {
            OnboardingArrowLeftIcon(color = AurixDesignTokens.TextPrimary, strokeDp = 2.4.dp)
          }
        }

        val isLastSlide = currentSlide == ONBOARDING_SLIDE_COUNT - 1
        val ctaLabel = stringResource(
          id = if (isLastSlide) R.string.onboarding_get_started else R.string.onboarding_next
        )
        OnboardingMainButton(
          label = ctaLabel,
          accentA = animatedAccentA,
          fontFamily = fontFamily,
          isLastSlide = isLastSlide,
          readyPulse = isButtonReadyPulse,
          reducedMotion = reducedMotion,
          onPositionedCenter = { center -> buttonCenterInRoot = center },
          onClick = {
            if (isLastSlide) {
              triggerFinish()
            } else {
              navigateToSlide(currentSlide + 1)
            }
          },
          modifier = Modifier
            .weight(1f)
            .height(58.dp)
            .testTag("onboarding_main_button")
        )
      }
    }

    // g) Finish transition overlay: circular gradient reveal (0 -> 150%, 850ms) + "AURIX" wordmark (40sp/800, 0.2em)
    if (isFinishing) {
      val maxRadius = hypot(rootWidthPx, rootHeightPx)
      val origin = if (buttonCenterInRoot == Offset.Zero) {
        Offset(rootWidthPx * 0.5f, rootHeightPx * 0.88f)
      } else {
        buttonCenterInRoot
      }
      Box(
        modifier = Modifier
          .fillMaxSize()
          .testTag("onboarding_finish_overlay"),
        contentAlignment = Alignment.Center
      ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
          val r = (maxRadius * finishRadiusFraction.value).coerceAtLeast(0f)
          if (r > 0f) {
            drawCircle(
              brush = Brush.linearGradient(
                colors = listOf(animatedAccentA, AurixDesignTokens.AccentB),
                start = Offset.Zero,
                end = Offset(size.width, size.height)
              ),
              radius = r,
              center = origin
            )
          }
        }
        val wm = finishWordmarkProgress.value
        Text(
          text = "AURIX",
          color = AurixDesignTokens.DarkInk,
          fontFamily = AurixDesignTokens.SoraFontFamily,
          fontSize = 40.sp,
          fontWeight = FontWeight.ExtraBold,
          letterSpacing = 8.sp, // 0.2em of 40sp
          modifier = Modifier.graphicsLayer {
            alpha = wm
            val s = 0.86f + 0.14f * wm
            scaleX = s
            scaleY = s
          }
        )
      }
    }
  }
}

@Composable
private fun LanguageSegmentButton(
  label: String,
  active: Boolean,
  fontFamily: FontFamily,
  onClick: () -> Unit,
  testTag: String
) {
  val shape = RoundedCornerShape(99.dp)
  Box(
    modifier = Modifier
      .onboardingFocusRing(shape)
      .clip(shape)
      .background(if (active) AurixDesignTokens.GlassActiveFill else Color.Transparent)
      .semantics { selected = active }
      .clickable(role = Role.Tab, onClick = onClick)
      .padding(horizontal = 12.dp, vertical = 7.dp)
      .testTag(testTag)
  ) {
    Text(
      text = label,
      color = if (active) AurixDesignTokens.TextPrimary else AurixDesignTokens.TextMuted,
      fontFamily = fontFamily,
      fontSize = 12.sp,
      fontWeight = FontWeight.SemiBold
    )
  }
}

@Composable
private fun OnboardingBackgroundGlows(
  currentSlide: Int,
  accentA: Color,
  backdropPreset: AurixDesignTokens.BackdropPreset,
  reducedMotion: Boolean,
  screenHeightDp: Float
) {
  val glowOpacity = when (backdropPreset) {
    AurixDesignTokens.BackdropPreset.AURORA -> AurixDesignTokens.GlowOpacity
    AurixDesignTokens.BackdropPreset.MIDNIGHT -> AurixDesignTokens.GlowOpacity * 0.62f
    AurixDesignTokens.BackdropPreset.MINIMAL -> 0.14f
  }

  val driftTransition = if (!reducedMotion) {
    rememberInfiniteTransition(label = "bg_glow_drift")
  } else {
    null
  }
  val driftXState = driftTransition?.animateFloat(
    initialValue = 0f,
    targetValue = 26f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = 11000, easing = FastOutSlowInEasing),
      repeatMode = RepeatMode.Reverse
    ),
    label = "glow_drift_x"
  )
  val driftYState = driftTransition?.animateFloat(
    initialValue = 0f,
    targetValue = -22f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = 11000, easing = FastOutSlowInEasing),
      repeatMode = RepeatMode.Reverse
    ),
    label = "glow_drift_y"
  )

  val slideX1State = animateFloatAsState(
    targetValue = currentSlide * 50f,
    animationSpec = if (reducedMotion) snap() else tween(1200, easing = AurixDesignTokens.MainEasing),
    label = "glow_slide_x1"
  )
  val slideY1State = animateFloatAsState(
    targetValue = if (currentSlide % 2 == 1) 60f else -20f,
    animationSpec = if (reducedMotion) snap() else tween(1200, easing = AurixDesignTokens.MainEasing),
    label = "glow_slide_y1"
  )
  val slideX2State = animateFloatAsState(
    targetValue = -currentSlide * 40f,
    animationSpec = if (reducedMotion) snap() else tween(1200, easing = AurixDesignTokens.MainEasing),
    label = "glow_slide_x2"
  )
  val slideY2State = animateFloatAsState(
    targetValue = if (currentSlide % 2 == 1) -50f else 30f,
    animationSpec = if (reducedMotion) snap() else tween(1200, easing = AurixDesignTokens.MainEasing),
    label = "glow_slide_y2"
  )

  Box(modifier = Modifier.fillMaxSize()) {
    // Circle 1: 300x300, left -90, top 10%, Accent A, blur 80, opacity 0.42
    val c1Top = (screenHeightDp * 0.10f).dp
    Box(
      modifier = Modifier
        .align(Alignment.TopStart)
        .offset(x = (-90).dp, y = c1Top)
        .size(300.dp)
        .graphicsLayer {
          translationX = (slideX1State.value + (driftXState?.value ?: 0f)) * density
          translationY = (slideY1State.value + (driftYState?.value ?: 0f)) * density
          alpha = glowOpacity
        }
        .then(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Modifier.blur(AurixDesignTokens.GlowBlurRadius) else Modifier)
        .background(
          brush = Brush.radialGradient(colors = listOf(accentA, accentA.copy(alpha = 0.25f), Color.Transparent)),
          shape = CircleShape
        )
    )

    // Circle 2: 280x280, right -100, top 34%, Accent B (#7965FF), blur 80, opacity 0.42 (reversed drift)
    val c2Top = (screenHeightDp * 0.34f).dp
    Box(
      modifier = Modifier
        .align(Alignment.TopEnd)
        .offset(x = 100.dp, y = c2Top)
        .size(280.dp)
        .graphicsLayer {
          translationX = (slideX2State.value - (driftXState?.value ?: 0f)) * density
          translationY = (slideY2State.value - (driftYState?.value ?: 0f)) * density
          alpha = glowOpacity
        }
        .then(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Modifier.blur(AurixDesignTokens.GlowBlurRadius) else Modifier)
        .background(
          brush = Brush.radialGradient(
            colors = listOf(AurixDesignTokens.AccentB, AurixDesignTokens.AccentB.copy(alpha = 0.25f), Color.Transparent)
          ),
          shape = CircleShape
        )
    )
  }
}

@Composable
private fun OnboardingSlideTextBlock(
  title: String,
  body: String,
  isActive: Boolean,
  replaySeed: Int,
  isKhmer: Boolean,
  isCompactHeight: Boolean,
  fontFamily: FontFamily,
  reducedMotion: Boolean
) {
  val titleProgress = remember { Animatable(if (reducedMotion) 1f else 0f) }
  val bodyProgress = remember { Animatable(if (reducedMotion) 1f else 0f) }

  LaunchedEffect(isActive, replaySeed, reducedMotion) {
    if (!isActive) return@LaunchedEffect
    if (reducedMotion) {
      titleProgress.snapTo(1f)
      bodyProgress.snapTo(1f)
    } else {
      titleProgress.snapTo(0f)
      bodyProgress.snapTo(0f)
      launch {
        delay(100L)
        titleProgress.animateTo(1f, tween(700, easing = AurixDesignTokens.MainEasing))
      }
      launch {
        delay(250L)
        bodyProgress.animateTo(1f, tween(700, easing = AurixDesignTokens.MainEasing))
      }
    }
  }

  val titleSize = if (isCompactHeight) 24.sp else 27.sp
  val bodySize = if (isCompactHeight) 13.5.sp else 14.5.sp
  val titleLineHeight = titleSize * if (isKhmer) AurixDesignTokens.TitleLineHeightKm else AurixDesignTokens.TitleLineHeightEn
  val bodyLineHeight = bodySize * if (isKhmer) AurixDesignTokens.BodyLineHeightKm else AurixDesignTokens.BodyLineHeightEn

  val tBlur = ((1f - titleProgress.value) * 6f).coerceAtLeast(0f).dp
  val bBlur = ((1f - bodyProgress.value) * 6f).coerceAtLeast(0f).dp

  Column(
    modifier = Modifier
      .widthIn(max = 320.dp)
      .fillMaxWidth()
      .padding(top = 6.dp),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    Text(
      text = title,
      color = AurixDesignTokens.TextPrimary,
      fontFamily = fontFamily,
      fontSize = titleSize,
      fontWeight = FontWeight.ExtraBold,
      lineHeight = titleLineHeight,
      letterSpacing = (-0.54).sp, // -0.02em of 27sp
      textAlign = TextAlign.Center,
      modifier = Modifier
        .graphicsLayer {
          val p = titleProgress.value
          alpha = p
          translationY = (1f - p) * 16f * density
        }
        .then(if (!reducedMotion && tBlur > 0.2.dp) Modifier.blur(tBlur) else Modifier)
    )
    Spacer(modifier = Modifier.height(10.dp))
    Text(
      text = body,
      color = AurixDesignTokens.TextMuted,
      fontFamily = fontFamily,
      fontSize = bodySize,
      fontWeight = FontWeight.Normal,
      lineHeight = bodyLineHeight,
      textAlign = TextAlign.Center,
      modifier = Modifier
        .graphicsLayer {
          val p = bodyProgress.value
          alpha = p
          translationY = (1f - p) * 16f * density
        }
        .then(if (!reducedMotion && bBlur > 0.2.dp) Modifier.blur(bBlur) else Modifier)
    )
  }
}

@Composable
private fun OnboardingMainButton(
  label: String,
  accentA: Color,
  fontFamily: FontFamily,
  isLastSlide: Boolean,
  readyPulse: Boolean,
  reducedMotion: Boolean,
  onPositionedCenter: (Offset) -> Unit,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  val interactionSource = remember { MutableInteractionSource() }
  val isPressed by interactionSource.collectIsPressedAsState()
  val pressScaleState = animateFloatAsState(
    targetValue = if (isPressed) 0.97f else 1f,
    animationSpec = if (reducedMotion) snap() else tween(140, easing = AurixDesignTokens.MainEasing),
    label = "main_btn_press"
  )

  val pulseTransition = if (readyPulse && !reducedMotion) {
    rememberInfiniteTransition(label = "main_btn_ready_pulse")
  } else {
    null
  }
  val pulseProgress = pulseTransition?.animateFloat(
    initialValue = 0f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = 800, easing = FastOutSlowInEasing),
      repeatMode = RepeatMode.Reverse
    ),
    label = "main_btn_pulse"
  )

  // Last slide diagonal white shine sweep (100deg, 2.4s loop, 1s delay)
  val shineOffset = remember { Animatable(-0.6f) }
  LaunchedEffect(isLastSlide, reducedMotion) {
    if (!isLastSlide || reducedMotion) {
      shineOffset.snapTo(-0.6f)
      return@LaunchedEffect
    }
    delay(1000L)
    while (true) {
      shineOffset.snapTo(-0.5f)
      shineOffset.animateTo(
        targetValue = 1.5f,
        animationSpec = tween(durationMillis = 1200, easing = AurixDesignTokens.MainEasing)
      )
      delay(1200L) // total 2.4s loop cadence
    }
  }

  val shape = RoundedCornerShape(29.dp)
  val glowElevation = if (readyPulse && !reducedMotion) {
    (18f + (pulseProgress?.value ?: 0f) * 14f).dp
  } else {
    16.dp
  }

  Box(
    modifier = modifier
      .onGloballyPositioned { coords ->
        val pos = coords.positionInRoot()
        onPositionedCenter(Offset(pos.x + coords.size.width * 0.5f, pos.y + coords.size.height * 0.5f))
      }
      .graphicsLayer {
        val s = pressScaleState.value
        scaleX = s
        scaleY = s
      }
      .shadow(
        elevation = glowElevation,
        shape = shape,
        ambientColor = accentA.copy(alpha = 0.72f),
        spotColor = accentA.copy(alpha = 0.78f)
      )
      .onboardingFocusRing(shape)
      .clip(shape)
      .background(
        Brush.linearGradient(
          colors = listOf(accentA, AurixDesignTokens.AccentB)
        )
      )
      .drawWithContent {
        drawContent()
        if (isLastSlide && !reducedMotion) {
          val x = size.width * shineOffset.value
          val band = size.width * 0.34f
          drawRect(
            brush = Brush.linearGradient(
              colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.38f), Color.Transparent),
              start = Offset(x - band, 0f),
              end = Offset(x + band, size.height * 0.25f)
            )
          )
        }
      }
      .clickable(
        interactionSource = interactionSource,
        indication = null,
        role = Role.Button,
        onClick = onClick
      ),
    contentAlignment = Alignment.Center
  ) {
    Row(
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text(
        text = label,
        color = AurixDesignTokens.DarkInk,
        fontFamily = fontFamily,
        fontSize = 16.sp,
        fontWeight = FontWeight.ExtraBold
      )
      OnboardingArrowRightIcon(color = AurixDesignTokens.DarkInk, strokeDp = 2.6.dp)
    }
  }
}
