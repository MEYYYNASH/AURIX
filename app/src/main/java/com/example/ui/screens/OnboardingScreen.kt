package com.example.ui.screens

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
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
import com.example.ui.screens.onboarding.OnboardingOrbiMascot
import com.example.ui.screens.onboarding.OnboardingSearchRowData
import com.example.ui.screens.onboarding.OnboardingShowcaseRepository
import com.example.ui.screens.onboarding.OnboardingSlide1Stage
import com.example.ui.screens.onboarding.OnboardingSlide2Stage
import com.example.ui.screens.onboarding.OnboardingSlide3Stage
import com.example.ui.screens.onboarding.OnboardingSlide4Stage
import com.example.ui.screens.onboarding.OnboardingSwipePhysics
import com.example.ui.screens.onboarding.onboardingFocusRing
import com.example.ui.screens.onboarding.onboardingGlass
import com.example.ui.theme.AurixDesignTokens
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
  favoriteIds: Set<String> = emptySet(),
  liveLyricsResult: LyricsResult? = null,
  audioSrc: String? = null,
  reducedMotion: Boolean = false,
  onLanguageChanged: (String) -> Unit = {},
  onAccentColorChanged: (String) -> Unit = {},
  onBackdropChanged: (AurixDesignTokens.BackdropPreset) -> Unit = {},
  onSetFavorite: (Song, Boolean) -> Unit = { _, _ -> },
  onToggleFavorite: (Song) -> Unit = {},
  onRevealAppUnderneath: () -> Unit = {},
  onFinished: () -> Unit
) {
  val scope = rememberCoroutineScope()
  val density = LocalDensity.current
  val focusManager = LocalFocusManager.current
  val hazeState = remember { HazeState() }

  val isKhmer = LocaleHelper.normalizeLanguage(currentLanguage) == LocaleHelper.LANG_KM
  val fontFamily = remember(isKhmer) { AurixDesignTokens.fontFamily(isKhmer) }

  var currentSlide by remember { mutableIntStateOf(0) }
  var slideReplaySeeds by remember { mutableStateOf(intArrayOf(1, 0, 0, 0)) }

  // Slide 3 live accent + backdrop state
  var selectedAccentPreset by remember {
    mutableStateOf(AurixDesignTokens.findPresetForHex(initialAccentHex))
  }
  var userChoseAccent by remember { mutableStateOf(false) }
  var selectedBackdrop by remember {
    mutableStateOf(AurixDesignTokens.BackdropPreset.fromSettings(initialDynamicBg, initialDynamicBgDim))
  }

  val animatedAccentA by animateColorAsState(
    targetValue = selectedAccentPreset.color,
    animationSpec = if (reducedMotion) snap() else tween(durationMillis = 800, easing = AurixDesignTokens.MainEasing),
    label = "onboarding_accent_a"
  )

  var previousSlide by remember { mutableIntStateOf(0) }
  LaunchedEffect(currentSlide) {
    if (currentSlide != 0) {
      focusManager.clearFocus()
    }
    if (previousSlide == 2 && currentSlide != 2 && !userChoseAccent) {
      selectedAccentPreset = AurixDesignTokens.AccentPresets.first()
    }
    previousSlide = currentSlide
    val nextSeeds = slideReplaySeeds.copyOf()
    nextSeeds[currentSlide] = nextSeeds[currentSlide] + 1
    slideReplaySeeds = nextSeeds
  }

  val context = LocalContext.current
  val onPhoneLabel = stringResource(id = R.string.onboarding_location)

  // Live song showcase shared across Slides 1, 2, 3, and 4
  var showcaseTrack by remember {
    mutableStateOf(
      OnboardingShowcaseRepository.buildInitialRandomShowcase(
        localSongs = localSongs,
        onPhoneLabel = onPhoneLabel
      )
    )
  }
  var isLoadingShowcase by remember { mutableStateOf(true) }
  var shuffleRequestCount by remember { mutableIntStateOf(0) }
  var hasUserPickedSong by remember { mutableStateOf(false) }

  // Slide 1 interactive search state
  var userSearchQuery by remember { mutableStateOf("") }
  var userSearchResults by remember { mutableStateOf<List<OnboardingSearchRowData>>(emptyList()) }
  var isSearchingUserSongs by remember { mutableStateOf(false) }

  // Debounced live YouTube + local search when the user types their favourite music
  LaunchedEffect(userSearchQuery, localSongs, onPhoneLabel) {
    val trimmed = userSearchQuery.trim()
    if (trimmed.isEmpty()) {
      userSearchResults = emptyList()
      isSearchingUserSongs = false
      return@LaunchedEffect
    }
    isSearchingUserSongs = true
    delay(320L)
    val results = OnboardingShowcaseRepository.searchUserFavoriteSongs(
      query = trimmed,
      localSongs = localSongs,
      onPhoneLabel = onPhoneLabel,
      limit = 6
    )
    userSearchResults = results
    isSearchingUserSongs = false
  }

  // Initial real random song fetch (only applies if the user hasn't already picked their own favourite)
  LaunchedEffect(shuffleRequestCount) {
    if (hasUserPickedSong && shuffleRequestCount == 0) return@LaunchedEffect
    isLoadingShowcase = true
    val excludeKey = if (shuffleRequestCount > 0) showcaseTrack.seedKey else null
    val initialPick = if (shuffleRequestCount > 0) {
      OnboardingShowcaseRepository.buildInitialRandomShowcase(
        localSongs = localSongs,
        onPhoneLabel = onPhoneLabel,
        excludeSeedKey = excludeKey
      ).also { showcaseTrack = it }
    } else {
      showcaseTrack
    }
    val liveTrack = OnboardingShowcaseRepository.loadLiveRandomShowcase(
      context = context,
      localSongs = localSongs,
      onPhoneLabel = onPhoneLabel,
      excludeSeedKey = excludeKey,
      preferredInitial = initialPick
    )
    if (!hasUserPickedSong || shuffleRequestCount > 0) {
      showcaseTrack = liveTrack
    }
    isLoadingShowcase = false
  }

  // Keep local row subtitles localized when switching EN / Khmer
  LaunchedEffect(onPhoneLabel) {
    val updatedRows = showcaseTrack.searchRows.map { row ->
      if (row.isLocal) {
        val artistPrefix = row.subtitle.substringBefore(" · ")
        row.copy(subtitle = "$artistPrefix · $onPhoneLabel")
      } else {
        row
      }
    }
    if (updatedRows != showcaseTrack.searchRows) {
      showcaseTrack = showcaseTrack.copy(searchRows = updatedRows)
    }
  }

  // Shared ambient audio engine spanning all 4 slides so selected favourite music plays softly throughout
  val audioEngine = remember(audioSrc) { OnboardingDemoAudioEngine(audioSrc = audioSrc) }
  var isPreviewPlaying by remember { mutableStateOf(false) }
  var isResolvingPreview by remember { mutableStateOf(false) }
  var resolvedStreamCache by remember { mutableStateOf<Pair<String, String?>?>(null) }
  var previewJob by remember { mutableStateOf<Job?>(null) }
  var hydrateSongJob by remember { mutableStateOf<Job?>(null) }

  DisposableEffect(audioEngine) {
    onDispose {
      previewJob?.cancel()
      hydrateSongJob?.cancel()
      audioEngine.release()
    }
  }

  fun startSoftAmbientPreview(song: Song, startAtSec: Float) {
    previewJob?.cancel()
    previewJob = scope.launch {
      isResolvingPreview = true
      val cached = resolvedStreamCache?.takeIf { it.first == song.id }?.second
      val streamUrl = cached ?: OnboardingShowcaseRepository.resolvePlayablePreviewUrl(song).also {
        resolvedStreamCache = song.id to it
      }
      val started = withContext(Dispatchers.IO) {
        audioEngine.start(
          initialTimeSec = startAtSec,
          overrideAudioSrc = streamUrl,
          context = context,
          volume = OnboardingDemoAudioEngine.AMBIENT_VOLUME,
          fadeInMs = 560L
        )
      }
      isResolvingPreview = false
      isPreviewPlaying = started
    }
  }

  fun toggleAmbientPreview() {
    if (isPreviewPlaying) {
      previewJob?.cancel()
      audioEngine.fadeOutAndStop(260L)
      isPreviewPlaying = false
      isResolvingPreview = false
    } else {
      startSoftAmbientPreview(
        song = showcaseTrack.song,
        startAtSec = showcaseTrack.lyricWindowStartSec
      )
    }
  }

  fun handleUserSelectFavoriteSong(pickedSong: Song) {
    hasUserPickedSong = true
    // Auto-add picked song to App Favourites (Love)
    onSetFavorite(pickedSong, true)

    // Immediately show the picked song across Slides 1-4 while hydrating real lyrics + audio stream
    val activeRows = if (userSearchQuery.isNotBlank()) userSearchResults else showcaseTrack.searchRows
    val immediateHue = OnboardingShowcaseRepository.deterministicHue("${pickedSong.title}|${pickedSong.artist}")
    showcaseTrack = showcaseTrack.copy(
      seedKey = "picked_${pickedSong.id}",
      song = pickedSong,
      searchQuery = userSearchQuery.ifBlank { OnboardingShowcaseRepository.deriveSearchQueryFromTitle(pickedSong.title) },
      searchRows = activeRows.ifEmpty { showcaseTrack.searchRows },
      lyrics = emptyList(),
      lyricWindowStartSec = 0f,
      durationSeconds = pickedSong.durationSeconds.takeIf { it > 10L }?.toFloat()
        ?: OnboardingDemoAudioEngine.LOOP_DURATION_SECONDS,
      hasVerifiedRealLyrics = false,
      accentBaseHue = immediateHue
    )

    // Hydrate real synced lyrics + ensure playable YouTube track, then start soft ambient preview
    hydrateSongJob?.cancel()
    hydrateSongJob = scope.launch {
      isLoadingShowcase = true
      val hydrated = OnboardingShowcaseRepository.buildShowcaseFromSelectedSong(
        context = context,
        selectedSong = pickedSong,
        currentQuery = userSearchQuery,
        currentRows = activeRows,
        onPhoneLabel = onPhoneLabel
      )
      showcaseTrack = hydrated
      isLoadingShowcase = false
      // If placeholder seed resolved to real YouTube song ID, also ensure the resolved song is Loved
      if (hydrated.song.id != pickedSong.id) {
        onSetFavorite(hydrated.song, true)
      }
      startSoftAmbientPreview(
        song = hydrated.song,
        startAtSec = hydrated.lyricWindowStartSec
      )
    }
  }

  fun shuffleShowcaseSong() {
    hasUserPickedSong = false
    previewJob?.cancel()
    hydrateSongJob?.cancel()
    audioEngine.stop()
    isPreviewPlaying = false
    isResolvingPreview = false
    shuffleRequestCount++
  }

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
          animationSpec = tween(durationMillis = 560, easing = AurixDesignTokens.MainEasing)
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

  // Clean dark-glass Start transition state (slides settle -> 3D Orbi emblem bloom -> seamless cross-depth reveal into Home)
  var isFinishing by remember { mutableStateOf(false) }
  var isRevealingApp by remember { mutableStateOf(false) }
  var buttonCenterInRoot by remember { mutableStateOf(Offset.Zero) }
  val finishSlidesExitProgress = remember { Animatable(0f) }
  val finishRingProgress = remember { Animatable(0f) }
  val finishEmblemProgress = remember { Animatable(0f) }
  val finishDissolveAlpha = remember { Animatable(1f) }
  val finishVeilZoomScale = remember { Animatable(1f) }

  fun triggerFinish() {
    if (isFinishing) return
    focusManager.clearFocus()
    previewJob?.cancel()
    audioEngine.fadeOutAndStop(420L)
    isPreviewPlaying = false
    isFinishing = true
    if (userChoseAccent) {
      onAccentColorChanged(selectedAccentPreset.hex)
    }
    onBackdropChanged(selectedBackdrop)
    scope.launch {
      if (reducedMotion) {
        finishSlidesExitProgress.snapTo(1f)
        finishEmblemProgress.snapTo(1f)
        onRevealAppUnderneath()
        isRevealingApp = true
        delay(160L)
        finishDissolveAlpha.snapTo(0f)
        onFinished()
      } else {
        // Phase 1: Gracefully settle slides away while dark aurora backdrop stays continuous (zero flash!)
        launch {
          finishSlidesExitProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 320, easing = AurixDesignTokens.MainEasing)
          )
        }
        // Subtle expanding luminous hairline aura ring
        launch {
          delay(60L)
          finishRingProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 680, easing = AurixDesignTokens.MainEasing)
          )
        }
        // Phase 2: 3D Orbi emblem + clean AURIX wordmark glide to center-stage
        launch {
          delay(120L)
          finishEmblemProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 420, easing = AurixDesignTokens.MainEasing)
          )
          // Mount Home silently underneath while dark obsidian veil covers viewport (100% GPU pre-warmed)
          onRevealAppUnderneath()
          delay(220L)
          isRevealingApp = true
          // Phase 3: Cross-depth zoom-dissolve into the music app
          launch {
            finishVeilZoomScale.animateTo(
              targetValue = 1.07f,
              animationSpec = tween(durationMillis = 460, easing = AurixDesignTokens.MainEasing)
            )
          }
          finishDissolveAlpha.animateTo(
            targetValue = 0f,
            animationSpec = tween(durationMillis = 440, easing = AurixDesignTokens.MainEasing)
          )
          onFinished()
        }
      }
    }
  }

  val canAdvanceFromCurrentSlide = currentSlide > 0 || hasUserPickedSong
  val isKeyboardOpen = WindowInsets.ime.getBottom(density) > 0

  BoxWithConstraints(
    modifier = Modifier
      .fillMaxSize()
      .graphicsLayer {
        alpha = if (isRevealingApp) finishDissolveAlpha.value else 1f
        val z = if (isRevealingApp) finishVeilZoomScale.value else 1f
        scaleX = z
        scaleY = z
      }
      .background(if (isRevealingApp) Color.Transparent else AurixDesignTokens.Background)
      .focusable()
      .onPreviewKeyEvent { keyEvent ->
        if (keyEvent.type == KeyEventType.KeyDown && !isFinishing) {
          when (keyEvent.key) {
            Key.DirectionRight -> {
              if (currentSlide < ONBOARDING_SLIDE_COUNT - 1 && canAdvanceFromCurrentSlide) {
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

    // Continuous dark obsidian aurora backdrop (stays clean through handoff, dissolves with veil into Home)
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
        reducedMotion = reducedMotion
      )
    }

    // Onboarding slides column — scales down & fades smoothly when transitioning to app
    if (finishSlidesExitProgress.value < 0.995f) {
      Column(
        modifier = Modifier
          .align(Alignment.Center)
          .fillMaxHeight()
          .widthIn(max = 460.dp)
          .fillMaxWidth()
          .graphicsLayer {
            val exitT = finishSlidesExitProgress.value
            alpha = 1f - exitT
            val s = 1f - 0.06f * exitT
            scaleX = s
            scaleY = s
          }
          .statusBarsPadding()
          .navigationBarsPadding()
          .imePadding(),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        // Progress segments
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 14.dp),
          horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          for (i in 0 until ONBOARDING_SLIDE_COUNT) {
            val fillFractionState = animateFloatAsState(
              targetValue = if (i <= currentSlide) 1f else 0f,
              animationSpec = if (reducedMotion) snap() else tween(520, easing = AurixDesignTokens.MainEasing),
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
                    transformOrigin = TransformOrigin(0f, 0.5f)
                  }
                  .clip(RoundedCornerShape(2.dp))
                  .background(Color.White)
              )
            }
          }
        }

        // Top bar: EN / ខ្មែរ language toggle
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 12.dp),
          horizontalArrangement = Arrangement.End,
          verticalAlignment = Alignment.CenterVertically
        ) {
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

        // Slide viewport: smooth horizontal swipe pager
        val dragSlopPx = with(density) { OnboardingSwipePhysics.DRAG_START_SLOP_DP.dp.toPx() }
        BoxWithConstraints(
          modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .pointerInput(currentSlide, hasUserPickedSong, isFinishing, reducedMotion) {
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
                      val rawTarget = OnboardingSwipePhysics.resolveTargetIndex(
                        rawDragPx = totalDx,
                        viewportWidthPx = viewportWidth,
                        currentIndex = currentSlide,
                        pageCount = ONBOARDING_SLIDE_COUNT
                      )
                      val target = if (currentSlide == 0 && !hasUserPickedSong && rawTarget > 0) 0 else rawTarget
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
                    val resisted = if (currentSlide == 0 && !hasUserPickedSong && totalDx < 0f) {
                      totalDx * OnboardingSwipePhysics.EDGE_RESISTANCE
                    } else {
                      OnboardingSwipePhysics.applyEdgeResistance(
                        rawDragPx = totalDx,
                        currentIndex = currentSlide,
                        pageCount = ONBOARDING_SLIDE_COUNT
                      )
                    }
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
          val stageMaxWidth = minOf(352.dp, pageWidthDp * 0.90f)
          val slideGap = if (isCompactHeight) 10.dp else 16.dp

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
              targetValue = if (isCurrent) 1.0f else 0.92f,
              animationSpec = if (reducedMotion) snap() else tween(560, easing = AurixDesignTokens.MainEasing),
              label = "stage_scale_$pageIndex"
            )
            val stageAlphaState = animateFloatAsState(
              targetValue = if (isCurrent) 1.0f else 0.28f,
              animationSpec = if (reducedMotion) snap() else tween(560, easing = AurixDesignTokens.MainEasing),
              label = "stage_alpha_$pageIndex"
            )

            Column(
              modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                  translationX = (pageIndex - animatedPageOffset.value) * pageWidthPx
                }
                .padding(horizontal = 20.dp, vertical = 6.dp),
              verticalArrangement = Arrangement.spacedBy(slideGap, Alignment.CenterVertically),
              horizontalAlignment = Alignment.CenterHorizontally
            ) {
              // Flexible stage container — gives every slide generous space with ZERO vertical clipping!
              Box(
                modifier = Modifier
                  .weight(1f)
                  .widthIn(max = stageMaxWidth)
                  .fillMaxWidth()
                  .graphicsLayer {
                    val s = stageScaleState.value
                    scaleX = s
                    scaleY = s
                    alpha = stageAlphaState.value
                    clip = false
                  },
                contentAlignment = Alignment.Center
              ) {
                when (pageIndex) {
                  0 -> OnboardingSlide1Stage(
                    isActive = isCurrent && !isFinishing,
                    searchQuery = userSearchQuery,
                    onSearchQueryChange = { userSearchQuery = it },
                    searchResults = userSearchResults,
                    isSearchingSongs = isSearchingUserSongs,
                    showcase = showcaseTrack,
                    hasUserPickedSong = hasUserPickedSong,
                    favoriteIds = favoriteIds,
                    isPreviewPlaying = isPreviewPlaying,
                    isResolvingPreview = isResolvingPreview,
                    accentColor = animatedAccentA,
                    fontFamily = fontFamily,
                    hazeState = hazeState,
                    isCompactHeight = isCompactHeight,
                    isKeyboardOpen = isKeyboardOpen,
                    reducedMotion = reducedMotion,
                    onSelectSong = { song -> handleUserSelectFavoriteSong(song) },
                    onToggleLove = { song -> onToggleFavorite(song) },
                    onTogglePreviewSound = { toggleAmbientPreview() }
                  )
                  1 -> OnboardingSlide2Stage(
                    isActive = isCurrent && !isFinishing,
                    replaySeed = slideReplaySeeds[1],
                    showcase = showcaseTrack,
                    isLoadingShowcase = isLoadingShowcase,
                    isLoved = showcaseTrack.song.id in favoriteIds,
                    isPreviewPlaying = isPreviewPlaying,
                    isResolvingPreview = isResolvingPreview,
                    accentColor = animatedAccentA,
                    fontFamily = fontFamily,
                    hazeState = hazeState,
                    reducedMotion = reducedMotion,
                    audioEngine = audioEngine,
                    onToggleLove = { onToggleFavorite(showcaseTrack.song) },
                    onTogglePreviewSound = { toggleAmbientPreview() },
                    onShuffleSong = { shuffleShowcaseSong() }
                  )
                  2 -> OnboardingSlide3Stage(
                    isActive = isCurrent && !isFinishing,
                    showcase = showcaseTrack,
                    isPreviewPlaying = isPreviewPlaying,
                    accentColor = animatedAccentA,
                    selectedPreset = selectedAccentPreset,
                    selectedBackdrop = selectedBackdrop,
                    userChoseAccent = userChoseAccent,
                    fontFamily = fontFamily,
                    hazeState = hazeState,
                    isCompactHeight = isCompactHeight,
                    reducedMotion = reducedMotion,
                    onTogglePreviewSound = { toggleAmbientPreview() },
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
                    showcase = showcaseTrack,
                    isLoved = showcaseTrack.song.id in favoriteIds,
                    isPreviewPlaying = isPreviewPlaying,
                    accentColor = animatedAccentA,
                    fontFamily = fontFamily,
                    hazeState = hazeState,
                    reducedMotion = reducedMotion
                  )
                }
              }

              // Hide bottom title/description cleanly while keyboard is active on Slide 1 so input never squeezes
              AnimatedVisibility(
                visible = !(pageIndex == 0 && isKeyboardOpen),
                enter = fadeIn(tween(220)) + expandVertically(),
                exit = fadeOut(tween(150)) + shrinkVertically()
              ) {
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
        }

        // Bottom bar: Back circle + Main gradient CTA button
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(
              start = 24.dp,
              end = 24.dp,
              top = 6.dp,
              bottom = if (isKeyboardOpen) 10.dp else 22.dp
            ),
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
            enabled = canAdvanceFromCurrentSlide,
            readyPulse = isButtonReadyPulse && canAdvanceFromCurrentSlide,
            reducedMotion = reducedMotion,
            onPositionedCenter = { center -> buttonCenterInRoot = center },
            onClick = {
              if (!canAdvanceFromCurrentSlide) return@OnboardingMainButton
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
    }

    // Clean dark-glass 3D Orbi handoff overlay -> glides seamlessly into AurixMainShell
    if (isFinishing) {
      val ringT = finishRingProgress.value
      val emT = finishEmblemProgress.value
      Box(
        modifier = Modifier
          .fillMaxSize()
          .testTag("onboarding_finish_overlay"),
        contentAlignment = Alignment.Center
      ) {
        // Subtle expanding luminous hairline aura ring (clean, zero solid wall flash)
        Canvas(modifier = Modifier.fillMaxSize()) {
          val maxR = hypot(size.width, size.height) * 0.48f
          val ringR = (38.dp.toPx() + maxR * ringT).coerceAtLeast(1f)
          val ringAlpha = ((1f - ringT) * 0.42f).coerceIn(0f, 1f)
          if (ringAlpha > 0.01f) {
            drawCircle(
              brush = Brush.sweepGradient(
                colors = listOf(
                  animatedAccentA.copy(alpha = ringAlpha),
                  AurixDesignTokens.AccentB.copy(alpha = ringAlpha),
                  Color.White.copy(alpha = ringAlpha * 0.7f),
                  animatedAccentA.copy(alpha = ringAlpha)
                ),
                center = center
              ),
              radius = ringR,
              center = center,
              style = androidx.compose.ui.graphics.drawscope.Stroke(width = (1.6.dp.toPx() * (1f - ringT * 0.5f)))
            )
          }
          // Soft centered radial aura behind the 3D emblem
          val glowR = 136.dp.toPx() * (0.7f + 0.3f * emT)
          drawCircle(
            brush = Brush.radialGradient(
              colors = listOf(
                animatedAccentA.copy(alpha = 0.24f * emT),
                AurixDesignTokens.AccentB.copy(alpha = 0.14f * emT),
                Color.Transparent
              ),
              center = center,
              radius = glowR
            ),
            radius = glowR,
            center = center
          )
        }

        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(18.dp),
          modifier = Modifier.graphicsLayer {
            alpha = emT
            val s = 0.84f + 0.16f * emT
            scaleX = s
            scaleY = s
            translationY = (1f - emT) * 18.dp.toPx()
          }
        ) {
          OnboardingOrbiMascot(
            isActive = true,
            reducedMotion = reducedMotion,
            replaySeed = 99,
            accentColor = animatedAccentA,
            modifier = Modifier.size(118.dp)
          )
          Text(
            text = "AURIX",
            color = AurixDesignTokens.TextPrimary,
            fontFamily = AurixDesignTokens.SoraFontFamily,
            fontSize = 24.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (11f - 4f * emT).sp
          )
        }
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

/**
 * Full-screen seamless GPU radial aurora glows.
 * Replaces bounded Modifier.blur(80.dp) boxes so there are ZERO rectangular clipping edges or seams
 * across all Android versions while rendering at silky 120fps.
 */
@Composable
private fun OnboardingBackgroundGlows(
  currentSlide: Int,
  accentA: Color,
  backdropPreset: AurixDesignTokens.BackdropPreset,
  reducedMotion: Boolean
) {
  val glowOpacity = when (backdropPreset) {
    AurixDesignTokens.BackdropPreset.AURORA -> 0.44f
    AurixDesignTokens.BackdropPreset.MIDNIGHT -> 0.27f
    AurixDesignTokens.BackdropPreset.MINIMAL -> 0.13f
  }

  val driftTransition = if (!reducedMotion) {
    rememberInfiniteTransition(label = "bg_glow_drift")
  } else {
    null
  }
  val driftXState = driftTransition?.animateFloat(
    initialValue = 0f,
    targetValue = 28f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = 11000, easing = FastOutSlowInEasing),
      repeatMode = RepeatMode.Reverse
    ),
    label = "glow_drift_x"
  )
  val driftYState = driftTransition?.animateFloat(
    initialValue = 0f,
    targetValue = -24f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = 11000, easing = FastOutSlowInEasing),
      repeatMode = RepeatMode.Reverse
    ),
    label = "glow_drift_y"
  )

  val slideX1State = animateFloatAsState(
    targetValue = currentSlide * 46f,
    animationSpec = if (reducedMotion) snap() else tween(1200, easing = AurixDesignTokens.MainEasing),
    label = "glow_slide_x1"
  )
  val slideY1State = animateFloatAsState(
    targetValue = if (currentSlide % 2 == 1) 52f else -18f,
    animationSpec = if (reducedMotion) snap() else tween(1200, easing = AurixDesignTokens.MainEasing),
    label = "glow_slide_y1"
  )
  val slideX2State = animateFloatAsState(
    targetValue = -currentSlide * 38f,
    animationSpec = if (reducedMotion) snap() else tween(1200, easing = AurixDesignTokens.MainEasing),
    label = "glow_slide_x2"
  )
  val slideY2State = animateFloatAsState(
    targetValue = if (currentSlide % 2 == 1) -46f else 28f,
    animationSpec = if (reducedMotion) snap() else tween(1200, easing = AurixDesignTokens.MainEasing),
    label = "glow_slide_y2"
  )

  Canvas(modifier = Modifier.fillMaxSize()) {
    val d = density
    val driftX = (driftXState?.value ?: 0f) * d
    val driftY = (driftYState?.value ?: 0f) * d

    // Orb 1: Accent A smooth aurora halo (huge soft falloff, zero square bounds)
    val c1Center = Offset(
      x = size.width * 0.16f + slideX1State.value * d + driftX,
      y = size.height * 0.24f + slideY1State.value * d + driftY
    )
    val c1Radius = maxOf(size.width * 0.86f, 340f * d)
    drawCircle(
      brush = Brush.radialGradient(
        colorStops = arrayOf(
          0.00f to accentA.copy(alpha = glowOpacity),
          0.32f to accentA.copy(alpha = glowOpacity * 0.56f),
          0.62f to accentA.copy(alpha = glowOpacity * 0.18f),
          0.86f to accentA.copy(alpha = glowOpacity * 0.03f),
          1.00f to Color.Transparent
        ),
        center = c1Center,
        radius = c1Radius
      ),
      radius = c1Radius,
      center = c1Center
    )

    // Orb 2: Accent B (#7965FF) smooth aurora halo (opposite drift)
    val c2Center = Offset(
      x = size.width * 0.86f + slideX2State.value * d - driftX,
      y = size.height * 0.48f + slideY2State.value * d - driftY
    )
    val c2Radius = maxOf(size.width * 0.82f, 320f * d)
    drawCircle(
      brush = Brush.radialGradient(
        colorStops = arrayOf(
          0.00f to AurixDesignTokens.AccentB.copy(alpha = glowOpacity * 0.96f),
          0.34f to AurixDesignTokens.AccentB.copy(alpha = glowOpacity * 0.50f),
          0.64f to AurixDesignTokens.AccentB.copy(alpha = glowOpacity * 0.16f),
          0.88f to AurixDesignTokens.AccentB.copy(alpha = glowOpacity * 0.02f),
          1.00f to Color.Transparent
        ),
        center = c2Center,
        radius = c2Radius
      ),
      radius = c2Radius,
      center = c2Center
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
        delay(80L)
        titleProgress.animateTo(1f, tween(640, easing = AurixDesignTokens.MainEasing))
      }
      launch {
        delay(210L)
        bodyProgress.animateTo(1f, tween(640, easing = AurixDesignTokens.MainEasing))
      }
    }
  }

  val titleSize = if (isCompactHeight) 22.sp else 25.sp
  val bodySize = if (isCompactHeight) 13.sp else 14.sp
  val titleLineHeight = titleSize * if (isKhmer) AurixDesignTokens.TitleLineHeightKm else AurixDesignTokens.TitleLineHeightEn
  val bodyLineHeight = bodySize * if (isKhmer) AurixDesignTokens.BodyLineHeightKm else AurixDesignTokens.BodyLineHeightEn

  val tBlur = ((1f - titleProgress.value) * 5f).coerceAtLeast(0f).dp
  val bBlur = ((1f - bodyProgress.value) * 5f).coerceAtLeast(0f).dp

  Column(
    modifier = Modifier
      .widthIn(max = 330.dp)
      .fillMaxWidth()
      .padding(top = 4.dp, bottom = 4.dp),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    Text(
      text = title,
      color = AurixDesignTokens.TextPrimary,
      fontFamily = fontFamily,
      fontSize = titleSize,
      fontWeight = FontWeight.ExtraBold,
      lineHeight = titleLineHeight,
      letterSpacing = (-0.45).sp,
      textAlign = TextAlign.Center,
      modifier = Modifier
        .graphicsLayer {
          val p = titleProgress.value
          alpha = p
          translationY = (1f - p) * 14f * density
        }
        .then(
          if (!reducedMotion && tBlur > 0.2.dp && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Modifier.blur(tBlur, edgeTreatment = BlurredEdgeTreatment.Unbounded)
          } else {
            Modifier
          }
        )
    )
    Spacer(modifier = Modifier.height(8.dp))
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
          translationY = (1f - p) * 14f * density
        }
        .then(
          if (!reducedMotion && bBlur > 0.2.dp && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Modifier.blur(bBlur, edgeTreatment = BlurredEdgeTreatment.Unbounded)
          } else {
            Modifier
          }
        )
    )
  }
}

@Composable
private fun OnboardingMainButton(
  label: String,
  accentA: Color,
  fontFamily: FontFamily,
  isLastSlide: Boolean,
  enabled: Boolean,
  readyPulse: Boolean,
  reducedMotion: Boolean,
  onPositionedCenter: (Offset) -> Unit,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  val interactionSource = remember { MutableInteractionSource() }
  val isPressed by interactionSource.collectIsPressedAsState()
  val pressScaleState = animateFloatAsState(
    targetValue = if (isPressed && enabled) 0.97f else 1f,
    animationSpec = if (reducedMotion) snap() else tween(140, easing = AurixDesignTokens.MainEasing),
    label = "main_btn_press"
  )
  val enabledAlphaState = animateFloatAsState(
    targetValue = if (enabled) 1f else 0.42f,
    animationSpec = if (reducedMotion) snap() else tween(280, easing = AurixDesignTokens.MainEasing),
    label = "main_btn_enabled_alpha"
  )

  val pulseTransition = if (readyPulse && enabled && !reducedMotion) {
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

  val shineOffset = remember { Animatable(-0.6f) }
  LaunchedEffect(isLastSlide, enabled, reducedMotion) {
    if (!isLastSlide || !enabled || reducedMotion) {
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
      delay(1200L)
    }
  }

  val shape = RoundedCornerShape(29.dp)
  val glowElevation = when {
    !enabled -> 0.dp
    readyPulse && !reducedMotion -> (18f + (pulseProgress?.value ?: 0f) * 14f).dp
    else -> 16.dp
  }
  val inkColor = if (enabled) AurixDesignTokens.DarkInk else AurixDesignTokens.TextMuted

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
        alpha = enabledAlphaState.value
      }
      .then(
        if (enabled) {
          Modifier.shadow(
            elevation = glowElevation,
            shape = shape,
            ambientColor = accentA.copy(alpha = 0.72f),
            spotColor = accentA.copy(alpha = 0.78f)
          )
        } else {
          Modifier
        }
      )
      .onboardingFocusRing(shape)
      .clip(shape)
      .then(
        if (enabled) {
          Modifier.background(
            Brush.linearGradient(
              colors = listOf(accentA, AurixDesignTokens.AccentB)
            )
          )
        } else {
          Modifier
            .background(Color.White.copy(alpha = 0.10f), shape)
        }
      )
      .drawWithContent {
        drawContent()
        if (isLastSlide && enabled && !reducedMotion) {
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
        enabled = enabled,
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
        color = inkColor,
        fontFamily = fontFamily,
        fontSize = 16.sp,
        fontWeight = FontWeight.ExtraBold
      )
      OnboardingArrowRightIcon(color = inkColor, strokeDp = 2.6.dp)
    }
  }
}
