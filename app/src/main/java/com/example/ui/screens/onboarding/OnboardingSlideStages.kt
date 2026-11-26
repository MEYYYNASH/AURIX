package com.example.ui.screens.onboarding

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.Song
import com.example.ui.theme.AurixDesignTokens
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.sin

/**
 * SLIDE 1 — "What's your favourite music?"
 * Required favourite-song picker:
 * - Shows NO random songs initially; displays a clean glass search box & prompt inviting the user
 *   to type their favourite song or artist
 * - Live debounced search across local phone audio & YouTube shows real matching songs to select
 * - Selecting a song auto-adds it to App Favourites (Love), begins soft low-volume ambient preview
 *   (~24% volume), hydrates its real synced lyrics for Slides 2–4, and unlocks the Next button
 * - Every row and the selected mini-player include an animated Love/Un-love heart toggle
 */
@Composable
fun OnboardingSlide1Stage(
  isActive: Boolean,
  searchQuery: String,
  onSearchQueryChange: (String) -> Unit,
  searchResults: List<OnboardingSearchRowData>,
  isSearchingSongs: Boolean,
  showcase: OnboardingShowcaseTrack,
  hasUserPickedSong: Boolean,
  favoriteIds: Set<String>,
  isPreviewPlaying: Boolean,
  isResolvingPreview: Boolean,
  accentColor: Color,
  fontFamily: FontFamily,
  hazeState: HazeState?,
  isCompactHeight: Boolean,
  isKeyboardOpen: Boolean,
  reducedMotion: Boolean,
  onSelectSong: (Song) -> Unit,
  onToggleLove: (Song) -> Unit,
  onTogglePreviewSound: () -> Unit,
  modifier: Modifier = Modifier
) {
  val focusManager = LocalFocusManager.current
  val searchHint = stringResource(id = R.string.onboarding_search_hint)
  val clearCd = stringResource(id = R.string.onboarding_clear_search_cd)
  val loveCd = stringResource(id = R.string.onboarding_love_cd)
  val unloveCd = stringResource(id = R.string.onboarding_unlove_cd)
  val speakerCd = stringResource(id = R.string.onboarding_play_demo_cd)
  val lovedStatus = stringResource(id = R.string.onboarding_loved_status)
  val pickHint = stringResource(id = R.string.onboarding_pick_hint)
  val typeToSearchText = stringResource(id = R.string.onboarding_type_to_search)
  val pickToContinueText = stringResource(id = R.string.onboarding_pick_to_continue)
  val noResultsText = stringResource(id = R.string.onboarding_no_results)

  val displayedRows = if (searchQuery.isNotBlank()) searchResults else emptyList()
  val maxRows = when {
    isKeyboardOpen -> 3
    isCompactHeight -> 2
    else -> 3
  }
  val rowGap = if (isCompactHeight || isKeyboardOpen) 8.dp else 12.dp
  val searchShape = RoundedCornerShape(99.dp)
  val showIdleHero = searchQuery.isBlank() && !hasUserPickedSong && !isKeyboardOpen

  val pulseTransition = if (isActive && showIdleHero && !reducedMotion) {
    rememberInfiniteTransition(label = "s1_idle_pulse")
  } else {
    null
  }
  val pulseDotAlpha = pulseTransition?.animateFloat(
    initialValue = 0.40f,
    targetValue = 1.0f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
      repeatMode = RepeatMode.Reverse
    ),
    label = "s1_dot_alpha"
  )

  Column(
    modifier = modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(rowGap, Alignment.CenterVertically),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    // Airy floating 3D Orbi emblem above the search pill when idle (ZERO heavy box border)
    androidx.compose.animation.AnimatedVisibility(
      visible = showIdleHero,
      enter = androidx.compose.animation.fadeIn(tween(260)) + androidx.compose.animation.expandVertically(),
      exit = androidx.compose.animation.fadeOut(tween(180)) + androidx.compose.animation.shrinkVertically()
    ) {
      val orbHeroSize = if (isCompactHeight) 82.dp else 104.dp
      Box(
        modifier = Modifier
          .padding(bottom = 4.dp)
          .size(orbHeroSize + 36.dp),
        contentAlignment = Alignment.Center
      ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
          val r = size.minDimension * 0.5f
          drawCircle(
            brush = Brush.radialGradient(
              colorStops = arrayOf(
                0.00f to accentColor.copy(alpha = 0.24f),
                0.55f to AurixDesignTokens.AccentB.copy(alpha = 0.12f),
                1.00f to Color.Transparent
              ),
              center = center,
              radius = r
            ),
            radius = r,
            center = center
          )
        }
        OnboardingOrbiMascot(
          isActive = isActive && showIdleHero,
          reducedMotion = reducedMotion,
          replaySeed = 1,
          accentColor = accentColor,
          modifier = Modifier.size(orbHeroSize)
        )
      }
    }

    // Sleek pill-shaped Liquid Glass search input
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .heightIn(min = 52.dp)
        .shadow(
          elevation = if (searchQuery.isNotBlank() || hasUserPickedSong) 16.dp else 10.dp,
          shape = searchShape,
          ambientColor = accentColor.copy(alpha = 0.36f),
          spotColor = accentColor.copy(alpha = 0.36f)
        )
        .onboardingGlass(
          shape = searchShape,
          hazeState = hazeState,
          fillColor = Color.White.copy(alpha = 0.085f),
          borderColor = if (searchQuery.isNotBlank() || hasUserPickedSong) {
            accentColor.copy(alpha = 0.80f)
          } else {
            Color.White.copy(alpha = 0.18f)
          }
        )
        .padding(horizontal = 18.dp, vertical = 13.dp)
        .testTag("onboarding_s1_search_bar"),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(11.dp)
    ) {
      OnboardingMagnifierIcon(
        color = if (searchQuery.isNotBlank()) accentColor else AurixDesignTokens.TextMuted,
        modifier = Modifier.size(18.dp)
      )
      Box(
        modifier = Modifier.weight(1f),
        contentAlignment = Alignment.CenterStart
      ) {
        if (searchQuery.isEmpty()) {
          Text(
            text = searchHint,
            color = AurixDesignTokens.TextMuted.copy(alpha = 0.78f),
            fontFamily = fontFamily,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }
        BasicTextField(
          value = searchQuery,
          onValueChange = onSearchQueryChange,
          singleLine = true,
          textStyle = TextStyle(
            color = AurixDesignTokens.TextPrimary,
            fontFamily = fontFamily,
            fontSize = 14.5.sp,
            fontWeight = FontWeight.SemiBold
          ),
          cursorBrush = SolidColor(accentColor),
          keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
          keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("onboarding_s1_search_input")
        )
      }
      if (isSearchingSongs) {
        CircularProgressIndicator(
          modifier = Modifier.size(16.dp),
          strokeWidth = 1.8.dp,
          color = accentColor
        )
      } else if (searchQuery.isNotEmpty()) {
        Box(
          modifier = Modifier
            .size(26.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.13f))
            .semantics { contentDescription = clearCd }
            .clickable(role = Role.Button) { onSearchQueryChange("") }
            .testTag("onboarding_s1_clear_search"),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = Icons.Rounded.Close,
            contentDescription = null,
            tint = AurixDesignTokens.TextPrimary,
            modifier = Modifier.size(14.dp)
          )
        }
      }
    }

    // Minimalist airy guidance pill when empty (ZERO random songs, ZERO clunky empty box)
    if (searchQuery.isBlank() && !hasUserPickedSong) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(top = 4.dp)
          .testTag("onboarding_s1_empty_prompt"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        Row(
          modifier = Modifier
            .clip(RoundedCornerShape(99.dp))
            .background(Color.White.copy(alpha = 0.055f))
            .border(0.8.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(99.dp))
            .padding(horizontal = 14.dp, vertical = 7.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Box(
            modifier = Modifier
              .size(6.dp)
              .graphicsLayer { alpha = pulseDotAlpha?.value ?: 0.85f }
              .clip(CircleShape)
              .background(accentColor)
          )
          Text(
            text = pickToContinueText,
            color = AurixDesignTokens.TextPrimary.copy(alpha = 0.84f),
            fontFamily = fontFamily,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }
        Text(
          text = typeToSearchText,
          color = AurixDesignTokens.TextMuted.copy(alpha = 0.72f),
          fontFamily = fontFamily,
          fontSize = 12.sp,
          textAlign = TextAlign.Center,
          modifier = Modifier.padding(horizontal = 12.dp)
        )
      }
    } else if (searchQuery.isNotBlank() && displayedRows.isEmpty() && !isSearchingSongs) {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .onboardingGlass(shape = RoundedCornerShape(18.dp), hazeState = hazeState)
          .padding(horizontal = 16.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center
      ) {
        Text(
          text = noResultsText,
          color = AurixDesignTokens.TextMuted,
          fontFamily = fontFamily,
          fontSize = 12.5.sp,
          textAlign = TextAlign.Center
        )
      }
    } else if (displayedRows.isNotEmpty()) {
      val visibleRows = displayedRows.take(maxRows)
      visibleRows.forEachIndexed { idx, row ->
        val rowSong = row.song ?: showcase.song
        val isPicked = hasUserPickedSong && rowSong.id == showcase.song.id
        val isLoved = rowSong.id in favoriteIds
        val rowShape = RoundedCornerShape(18.dp)
        val scaleState = animateFloatAsState(
          targetValue = if (isPicked) 1.015f else 1.0f,
          animationSpec = if (reducedMotion) snap() else tween(320, easing = AurixDesignTokens.MainEasing),
          label = "s1_row_scale_$idx"
        )
        val borderColor by animateColorAsState(
          targetValue = if (isPicked) accentColor.copy(alpha = 0.88f) else Color.White.copy(alpha = 0.11f),
          animationSpec = if (reducedMotion) snap() else tween(260),
          label = "s1_row_border_$idx"
        )

        Row(
          modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
              val s = scaleState.value
              scaleX = s
              scaleY = s
            }
            .then(
              if (isPicked) {
                Modifier.shadow(
                  elevation = 14.dp,
                  shape = rowShape,
                  ambientColor = accentColor.copy(alpha = 0.45f),
                  spotColor = accentColor.copy(alpha = 0.45f)
                )
              } else {
                Modifier
              }
            )
            .onboardingGlass(
              shape = rowShape,
              hazeState = hazeState,
              fillColor = if (isPicked) AurixDesignTokens.GlassPickedFill else Color.White.copy(alpha = 0.065f),
              borderColor = borderColor
            )
            .clickable(role = Role.Button) {
              focusManager.clearFocus()
              onSelectSong(rowSong)
            }
            .padding(horizontal = 12.dp, vertical = 9.dp)
            .testTag("onboarding_s1_song_row_$idx"),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(11.dp)
        ) {
          OnboardingArtworkBox(
            artworkUrl = row.artworkUrl,
            baseHue = row.baseHue,
            contentDescription = row.title,
            modifier = Modifier
              .size(46.dp)
              .clip(RoundedCornerShape(12.dp))
          )
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = row.title,
              color = AurixDesignTokens.TextPrimary,
              fontFamily = fontFamily,
              fontSize = 13.5.sp,
              fontWeight = FontWeight.SemiBold,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
              Text(
                text = row.subtitle,
                color = AurixDesignTokens.TextMuted,
                fontFamily = fontFamily,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
              )
              Box(
                modifier = Modifier
                  .clip(RoundedCornerShape(99.dp))
                  .background(Color.White.copy(alpha = 0.06f))
                  .border(0.7.dp, AurixDesignTokens.BadgeBorder, RoundedCornerShape(99.dp))
                  .padding(horizontal = 6.dp, vertical = 1.dp)
              ) {
                Text(
                  text = row.badge,
                  color = AurixDesignTokens.TextMuted,
                  fontFamily = fontFamily,
                  fontSize = 9.sp,
                  fontWeight = FontWeight.SemiBold
                )
              }
            }
          }
          OnboardingLoveButton(
            isLoved = isLoved,
            loveCd = loveCd,
            unloveCd = unloveCd,
            reducedMotion = reducedMotion,
            onClick = { onToggleLove(rowSong) },
            modifier = Modifier.testTag("onboarding_s1_love_btn_$idx")
          )
        }
      }
    }

    // Selected song floating ambient mini-player — shown ONLY after user selects their favourite song
    if (hasUserPickedSong && !isKeyboardOpen) {
      val activeSong = showcase.song
      val activeIsLoved = activeSong.id in favoriteIds
      val miniShape = RoundedCornerShape(22.dp)
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .shadow(
            elevation = 16.dp,
            shape = miniShape,
            ambientColor = accentColor.copy(alpha = 0.36f),
            spotColor = accentColor.copy(alpha = 0.36f)
          )
          .onboardingGlass(
            shape = miniShape,
            hazeState = hazeState,
            fillColor = Color.White.copy(alpha = 0.09f),
            borderColor = accentColor.copy(alpha = 0.65f)
          )
          .padding(horizontal = 12.dp, vertical = 10.dp)
          .testTag("onboarding_s1_mini_player"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        OnboardingArtworkBox(
          artworkUrl = activeSong.artworkUrl,
          baseHue = showcase.accentBaseHue,
          contentDescription = activeSong.title,
          modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(11.dp))
        )
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = activeSong.title,
            color = AurixDesignTokens.TextPrimary,
            fontFamily = fontFamily,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
          Text(
            text = if (activeIsLoved) lovedStatus else activeSong.artist,
            color = if (activeIsLoved) accentColor.copy(alpha = 0.94f) else AurixDesignTokens.TextMuted,
            fontFamily = fontFamily,
            fontSize = 10.5.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }
        if (isPreviewPlaying) {
          OnboardingEqualizer(
            accentColor = accentColor,
            isActive = isActive && isPreviewPlaying,
            reducedMotion = reducedMotion,
            height = 14.dp
          )
        }
        OnboardingLoveButton(
          isLoved = activeIsLoved,
          loveCd = loveCd,
          unloveCd = unloveCd,
          reducedMotion = reducedMotion,
          onClick = { onToggleLove(activeSong) },
          modifier = Modifier.testTag("onboarding_s1_mini_love_btn")
        )
        Box(
          modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(if (isPreviewPlaying) accentColor else Color.White.copy(alpha = 0.08f))
            .semantics { contentDescription = speakerCd }
            .clickable(role = Role.Button, enabled = !isResolvingPreview) { onTogglePreviewSound() }
            .testTag("onboarding_s1_sound_toggle"),
          contentAlignment = Alignment.Center
        ) {
          if (isResolvingPreview) {
            CircularProgressIndicator(
              modifier = Modifier.size(15.dp),
              strokeWidth = 1.8.dp,
              color = accentColor
            )
          } else {
            OnboardingSpeakerIcon(
              soundOn = isPreviewPlaying,
              color = if (isPreviewPlaying) AurixDesignTokens.DarkInk else AurixDesignTokens.TextPrimary,
              modifier = Modifier.size(17.dp)
            )
          }
        }
      }
    } else if (searchQuery.isNotBlank() && displayedRows.isNotEmpty() && !hasUserPickedSong && !isKeyboardOpen) {
      Text(
        text = pickHint,
        color = AurixDesignTokens.TextMuted.copy(alpha = 0.78f),
        fontFamily = fontFamily,
        fontSize = 11.sp,
        textAlign = TextAlign.Center
      )
    }
  }
}

/**
 * SLIDE 2 — "Lyrics that move with you."
 * Real karaoke-filled synced lyrics matched directly to the user's chosen favourite song (or random
 * showcase song). Continues seamless low-volume ambient playback from Slide 1 without interruption!
 */
@Composable
fun OnboardingSlide2Stage(
  isActive: Boolean,
  replaySeed: Int,
  showcase: OnboardingShowcaseTrack,
  isLoadingShowcase: Boolean,
  isLoved: Boolean,
  isPreviewPlaying: Boolean,
  isResolvingPreview: Boolean,
  accentColor: Color,
  fontFamily: FontFamily,
  hazeState: HazeState?,
  reducedMotion: Boolean,
  audioEngine: OnboardingDemoAudioEngine,
  onToggleLove: () -> Unit,
  onTogglePreviewSound: () -> Unit,
  onShuffleSong: () -> Unit,
  modifier: Modifier = Modifier
) {
  val captionText = stringResource(id = R.string.onboarding_tap2)
  val speakerCd = stringResource(id = R.string.onboarding_play_demo_cd)
  val loveCd = stringResource(id = R.string.onboarding_love_cd)
  val unloveCd = stringResource(id = R.string.onboarding_unlove_cd)
  val syncingLabel = stringResource(id = R.string.onboarding_syncing_lyrics)
  val shuffleLabel = stringResource(id = R.string.onboarding_another_song)

  val activeLyrics = remember(showcase.seedKey, showcase.lyrics) {
    if (showcase.lyrics.isNotEmpty()) showcase.lyrics else OnboardingDemoAudioEngine.DEFAULT_LYRICS
  }
  val windowStartSec = remember(showcase.seedKey, showcase.lyricWindowStartSec, showcase.lyrics) {
    if (showcase.lyrics.isNotEmpty()) showcase.lyricWindowStartSec else 0f
  }
  val totalSec = remember(showcase.seedKey, showcase.durationSeconds, showcase.lyrics) {
    if (showcase.lyrics.isNotEmpty()) {
      showcase.durationSeconds.coerceAtLeast(windowStartSec + 12f)
    } else {
      OnboardingDemoAudioEngine.LOOP_DURATION_SECONDS
    }
  }

  var clockSec by remember(showcase.seedKey, windowStartSec) { mutableFloatStateOf(windowStartSec) }

  // Reset clock only when muted and entering Slide 2 fresh
  LaunchedEffect(isActive, replaySeed, showcase.seedKey, windowStartSec) {
    if (isActive && !isPreviewPlaying) {
      clockSec = windowStartSec
    }
  }

  // Frame-synced clock (follows live shared ambient audio when playing; loops cleanly over lyric span when muted)
  LaunchedEffect(isActive, isPreviewPlaying, totalSec, windowStartSec) {
    if (!isActive) return@LaunchedEffect
    var lastNanos = 0L
    val loopEnd = if (showcase.lyrics.isNotEmpty()) {
      minOf(totalSec, (activeLyrics.lastOrNull()?.seconds ?: totalSec) + 4f)
    } else {
      totalSec
    }
    while (true) {
      withFrameNanos { frameNanos ->
        if (isPreviewPlaying && audioEngine.isPlaying) {
          val audioNow = audioEngine.currentTimeSeconds()
          clockSec = if (audioEngine.totalDurationSeconds <= OnboardingDemoAudioEngine.LOOP_DURATION_SECONDS + 0.5f &&
            showcase.lyrics.isNotEmpty() && windowStartSec > 0f
          ) {
            val span = (loopEnd - windowStartSec).coerceAtLeast(12f)
            windowStartSec + (audioNow % span)
          } else {
            audioNow
          }
        } else {
          if (lastNanos != 0L) {
            val dt = ((frameNanos - lastNanos) / 1_000_000_000f).coerceIn(0f, 0.1f)
            val next = clockSec + dt
            clockSec = if (next >= loopEnd) windowStartSec else next
          }
          lastNanos = frameNanos
        }
      }
    }
  }

  val activeIdx = OnboardingDemoAudioEngine.activeLineIndex(clockSec, activeLyrics)
  val scrollYState = animateFloatAsState(
    targetValue = 66f - activeIdx * 44f,
    animationSpec = if (reducedMotion) snap() else tween(600, easing = AurixDesignTokens.MainEasing),
    label = "s2_lyrics_scroll"
  )

  Column(
    modifier = modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    // Now-playing header: cover + title/artist + love toggle + speaker button
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      val coverShape = RoundedCornerShape(16.dp)
      OnboardingArtworkBox(
        artworkUrl = showcase.song.artworkUrl,
        baseHue = showcase.accentBaseHue,
        contentDescription = showcase.song.title,
        modifier = Modifier
          .size(58.dp)
          .shadow(
            elevation = 14.dp,
            shape = coverShape,
            ambientColor = accentColor.copy(alpha = 0.45f),
            spotColor = accentColor.copy(alpha = 0.45f)
          )
          .clip(coverShape)
      )
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = showcase.song.title,
          color = AurixDesignTokens.TextPrimary,
          fontFamily = fontFamily,
          fontSize = 16.sp,
          fontWeight = FontWeight.Bold,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
        Text(
          text = showcase.song.artist,
          color = AurixDesignTokens.TextMuted,
          fontFamily = fontFamily,
          fontSize = 12.sp,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
      }
      OnboardingLoveButton(
        isLoved = isLoved,
        loveCd = loveCd,
        unloveCd = unloveCd,
        reducedMotion = reducedMotion,
        onClick = onToggleLove,
        modifier = Modifier.testTag("onboarding_s2_love_button")
      )
      Box(
        modifier = Modifier
          .size(42.dp)
          .onboardingFocusRing(CircleShape)
          .then(
            if (isPreviewPlaying) {
              Modifier
                .clip(CircleShape)
                .background(accentColor)
            } else {
              Modifier.onboardingGlass(shape = CircleShape, hazeState = hazeState)
            }
          )
          .semantics { contentDescription = speakerCd }
          .clickable(role = Role.Button, enabled = !isResolvingPreview) { onTogglePreviewSound() }
          .testTag("onboarding_s2_speaker_button"),
        contentAlignment = Alignment.Center
      ) {
        if (isResolvingPreview) {
          CircularProgressIndicator(
            modifier = Modifier.size(18.dp),
            strokeWidth = 2.dp,
            color = accentColor
          )
        } else {
          OnboardingSpeakerIcon(
            soundOn = isPreviewPlaying,
            color = if (isPreviewPlaying) AurixDesignTokens.DarkInk else AurixDesignTokens.TextPrimary
          )
        }
      }
    }

    // Lyrics window: 176dp tall, smooth alpha fade top & bottom (Unbounded blur so zero rectangular corners!)
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
              0.28f to Color.Black,
              0.72f to Color.Black,
              1.0f to Color.Transparent
            ),
            blendMode = BlendMode.DstIn
          )
        },
      contentAlignment = Alignment.TopCenter
    ) {
      if (isLoadingShowcase && showcase.lyrics.isEmpty()) {
        Row(
          modifier = Modifier.align(Alignment.Center),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          CircularProgressIndicator(
            modifier = Modifier.size(16.dp),
            strokeWidth = 1.8.dp,
            color = accentColor
          )
          Text(
            text = syncingLabel,
            color = AurixDesignTokens.TextMuted,
            fontFamily = fontFamily,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
          )
        }
      } else {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
              translationY = scrollYState.value * density
            },
          horizontalAlignment = Alignment.CenterHorizontally
        ) {
          activeLyrics.forEachIndexed { idx, line ->
            val isCurrentLine = idx == activeIdx
            val fillFraction = OnboardingDemoAudioEngine.computeKaraokeFill(
              timeSec = clockSec,
              lineIndex = idx,
              lines = activeLyrics,
              totalSeconds = totalSec
            )
            val lineScaleState = animateFloatAsState(
              targetValue = if (isCurrentLine) 1.07f else 0.90f,
              animationSpec = if (reducedMotion) snap() else tween(450, easing = AurixDesignTokens.MainEasing),
              label = "s2_line_scale_$idx"
            )
            val lineAlphaState = animateFloatAsState(
              targetValue = when {
                isCurrentLine -> 1.0f
                reducedMotion -> 0.70f
                else -> 0.42f
              },
              animationSpec = if (reducedMotion) snap() else tween(450, easing = AurixDesignTokens.MainEasing),
              label = "s2_line_alpha_$idx"
            )
            val lineBlurMod = if (!isCurrentLine && !reducedMotion && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
              Modifier.blur(2.5.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded)
            } else {
              Modifier
            }

            Box(
              modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .padding(horizontal = 4.dp)
                .graphicsLayer {
                  alpha = lineAlphaState.value
                  val s = lineScaleState.value
                  scaleX = s
                  scaleY = s
                }
                .then(lineBlurMod),
              contentAlignment = Alignment.Center
            ) {
              Text(
                text = line.text,
                color = Color.White.copy(alpha = 0.32f),
                fontFamily = fontFamily,
                fontSize = 17.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
              )
              if (fillFraction > 0.001f) {
                Text(
                  text = line.text,
                  color = Color.White,
                  fontFamily = fontFamily,
                  fontSize = 17.sp,
                  fontWeight = FontWeight.ExtraBold,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
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

    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      Text(
        text = captionText,
        color = AurixDesignTokens.TextMuted.copy(alpha = 0.72f),
        fontFamily = fontFamily,
        fontSize = 10.5.sp
      )
      OnboardingShuffleChip(
        label = shuffleLabel,
        accentColor = accentColor,
        fontFamily = fontFamily,
        hazeState = hazeState,
        enabled = !isLoadingShowcase,
        onClick = onShuffleSong,
        testTag = "onboarding_s2_shuffle_button"
      )
    }
  }
}

/**
 * SLIDE 3 — "Make it yours."
 * Live theme & backdrop customizer featuring the user's chosen song artwork, live play/pause preview
 * toggle, 4 accent swatches, and full unclipped Aurora / Midnight / Minimal backdrop picker.
 */
@Composable
fun OnboardingSlide3Stage(
  isActive: Boolean,
  showcase: OnboardingShowcaseTrack,
  isPreviewPlaying: Boolean,
  accentColor: Color,
  selectedPreset: AurixDesignTokens.AccentPreset,
  selectedBackdrop: AurixDesignTokens.BackdropPreset,
  userChoseAccent: Boolean,
  fontFamily: FontFamily,
  hazeState: HazeState?,
  isCompactHeight: Boolean,
  reducedMotion: Boolean,
  onTogglePreviewSound: () -> Unit,
  onSelectSwatch: (AurixDesignTokens.AccentPreset) -> Unit,
  onAutoCycleSwatch: (AurixDesignTokens.AccentPreset) -> Unit,
  onSelectBackdrop: (AurixDesignTokens.BackdropPreset) -> Unit,
  modifier: Modifier = Modifier
) {
  val captionText = stringResource(id = R.string.onboarding_tap)
  val speakerCd = stringResource(id = R.string.onboarding_play_demo_cd)
  val tileScale = remember { Animatable(1f) }
  var popTrigger by remember { mutableIntStateOf(0) }

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

  LaunchedEffect(isActive, userChoseAccent, reducedMotion) {
    if (!isActive || userChoseAccent || reducedMotion) return@LaunchedEffect
    var idx = AurixDesignTokens.AccentPresets.indexOf(selectedPreset).coerceAtLeast(0)
    while (true) {
      delay(1100L)
      idx = (idx + 1) % AurixDesignTokens.AccentPresets.size
      onAutoCycleSwatch(AurixDesignTokens.AccentPresets[idx])
    }
  }

  val coverSize = if (isCompactHeight) 114.dp else 134.dp
  val verticalGap = if (isCompactHeight) 8.dp else 11.dp

  Column(
    modifier = modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(verticalGap, Alignment.CenterVertically),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    val tileShape = RoundedCornerShape(28.dp)
    Box(
      modifier = Modifier
        .size(coverSize)
        .graphicsLayer {
          val s = tileScale.value
          scaleX = s
          scaleY = s
        }
        .shadow(
          elevation = 20.dp,
          shape = tileShape,
          ambientColor = accentColor.copy(alpha = 0.62f),
          spotColor = accentColor.copy(alpha = 0.62f)
        )
        .clip(tileShape)
        .background(AurixDesignTokens.accentGradientBrush(accentColor))
        .border(2.dp, accentColor.copy(alpha = 0.72f), tileShape),
      contentAlignment = Alignment.Center
    ) {
      if (showcase.song.artworkUrl.isNotBlank()) {
        OnboardingArtworkBox(
          artworkUrl = showcase.song.artworkUrl,
          baseHue = showcase.accentBaseHue,
          contentDescription = showcase.song.title,
          modifier = Modifier.fillMaxSize()
        )
        Box(
          modifier = Modifier
            .fillMaxSize()
            .background(
              Brush.verticalGradient(
                colors = listOf(
                  accentColor.copy(alpha = 0.12f),
                  Color.Transparent,
                  accentColor.copy(alpha = 0.30f)
                )
              )
            )
        )
      } else {
        OnboardingMusicNoteIcon(color = Color.White, modifier = Modifier.size(56.dp), strokeDp = 1.8.dp)
      }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
      Text(
        text = showcase.song.title,
        color = AurixDesignTokens.TextPrimary,
        fontFamily = fontFamily,
        fontSize = 15.5.sp,
        fontWeight = FontWeight.ExtraBold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
      Text(
        text = showcase.song.artist,
        color = AurixDesignTokens.TextMuted,
        fontFamily = fontFamily,
        fontSize = 12.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
    }

    // Transport controls: real play/pause toggle for soft ambient preview
    Row(
      horizontalArrangement = Arrangement.spacedBy(22.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Icon(
        imageVector = Icons.Rounded.SkipPrevious,
        contentDescription = null,
        tint = AurixDesignTokens.TextPrimary.copy(alpha = 0.75f),
        modifier = Modifier.size(24.dp)
      )
      Box(
        modifier = Modifier
          .size(48.dp)
          .clip(CircleShape)
          .background(accentColor)
          .semantics { contentDescription = speakerCd }
          .clickable(role = Role.Button, onClick = onTogglePreviewSound)
          .testTag("onboarding_s3_play_toggle"),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = if (isPreviewPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
          contentDescription = null,
          tint = AurixDesignTokens.DarkInk,
          modifier = Modifier.size(26.dp)
        )
      }
      Icon(
        imageVector = Icons.Rounded.SkipNext,
        contentDescription = null,
        tint = AurixDesignTokens.TextPrimary.copy(alpha = 0.75f),
        modifier = Modifier.size(24.dp)
      )
    }

    // Four 30dp color swatches
    Row(
      horizontalArrangement = Arrangement.spacedBy(14.dp),
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

    // Real Backdrop picker wired to app Dynamic Background settings (generous height, ZERO text clipping)
    Row(
      modifier = Modifier
        .onboardingGlass(shape = RoundedCornerShape(99.dp), hazeState = hazeState)
        .padding(4.dp),
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
            .padding(horizontal = 12.dp, vertical = 7.dp)
            .testTag("onboarding_backdrop_${preset.id}"),
          contentAlignment = Alignment.Center
        ) {
          Text(
            text = stringResource(id = preset.labelRes),
            color = if (isPicked) AurixDesignTokens.TextPrimary else AurixDesignTokens.TextMuted,
            fontFamily = fontFamily,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
          )
        }
      }
    }

    Text(
      text = captionText,
      color = AurixDesignTokens.TextMuted.copy(alpha = 0.72f),
      fontFamily = fontFamily,
      fontSize = 10.5.sp
    )
  }
}

/**
 * SLIDE 4 — "Ready to listen?"
 * 3D sculpted official AURIX Logo Orb with rotating Saturn ring, unclipped seamless radial aura,
 * 2 expanding ripples, 5 graceful swaying musical notes, and a glass track pill showing the user's
 * favourite song.
 */
@Composable
fun OnboardingSlide4Stage(
  isActive: Boolean,
  replaySeed: Int,
  showcase: OnboardingShowcaseTrack,
  isLoved: Boolean,
  isPreviewPlaying: Boolean,
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
    initialValue = 0.96f,
    targetValue = 1.08f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = 1400, easing = FastOutSlowInEasing),
      repeatMode = RepeatMode.Reverse
    ),
    label = "s4_glow_scale"
  )
  val glowBreathAlphaState = loopTransition?.animateFloat(
    initialValue = 0.34f,
    targetValue = 0.52f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = 1400, easing = FastOutSlowInEasing),
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
      initialStartOffset = StartOffset(1400)
    ),
    label = "s4_ripple_2"
  )

  val noteDelays = remember { intArrayOf(0, 650, 1300, 1950, 2550) }
  val noteSymbols = remember { listOf("♪", "♫", "♩", "♬", "♪") }
  val noteColors = remember(accentColor) {
    listOf(accentColor, AurixDesignTokens.AccentB, Color(0xFFE0F2FE), accentColor, AurixDesignTokens.AccentB)
  }
  val noteOffsetsX = remember { listOf((-82).dp, 78.dp, (-64).dp, 68.dp, 18.dp) }
  val noteOffsetsY = remember { listOf(30.dp, 16.dp, (-20).dp, (-28).dp, (-54).dp) }
  val noteSizes = remember { listOf(22.sp, 20.sp, 18.sp, 21.sp, 17.sp) }
  val noteProgressStates = List(5) { i ->
    loopTransition?.animateFloat(
      initialValue = 0f,
      targetValue = 1f,
      animationSpec = infiniteRepeatable(
        animation = tween(durationMillis = 3200, easing = LinearEasing),
        repeatMode = RepeatMode.Restart,
        initialStartOffset = StartOffset(noteDelays[i])
      ),
      label = "s4_note_$i"
    )
  }

  Column(
    modifier = modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterVertically),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    Box(
      modifier = Modifier.size(240.dp),
      contentAlignment = Alignment.Center
    ) {
      // Seamless radial aura fading fully to transparent well before bounds (ZERO square edges!)
      Canvas(
        modifier = Modifier
          .size(240.dp)
          .graphicsLayer {
            val s = glowBreathScaleState?.value ?: 1.0f
            scaleX = s
            scaleY = s
            alpha = glowBreathAlphaState?.value ?: 0.42f
            clip = false
          }
      ) {
        drawCircle(
          brush = Brush.radialGradient(
            colorStops = arrayOf(
              0.00f to accentColor.copy(alpha = 0.65f),
              0.34f to accentColor.copy(alpha = 0.28f),
              0.66f to AurixDesignTokens.AccentB.copy(alpha = 0.10f),
              0.90f to Color.Transparent,
              1.00f to Color.Transparent
            ),
            radius = size.minDimension * 0.5f
          )
        )
      }

      // Two 2dp accent ripple rings expanding (scale 0.7 -> 1.55, fading smoothly)
      listOf(ripple1Progress, ripple2Progress).forEach { progressState ->
        Canvas(
          modifier = Modifier
            .size(168.dp)
            .graphicsLayer {
              val p = progressState?.value ?: 0.35f
              val s = 0.72f + p * 0.86f
              scaleX = s
              scaleY = s
              alpha = if (reducedMotion) 0.18f else (1f - p) * 0.50f
              clip = false
            }
        ) {
          drawCircle(
            color = accentColor,
            style = Stroke(width = 2.dp.toPx())
          )
        }
      }

      // 170x170 orb with 1.5dp rotating dashed ring
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

      // 148x148 3D sculpted official AURIX Logo Orb with rotating Saturn ring
      OnboardingOrbiMascot(
        isActive = isActive,
        reducedMotion = reducedMotion,
        replaySeed = replaySeed,
        accentColor = accentColor,
        modifier = Modifier
          .size(148.dp)
          .testTag("onboarding_s4_orbi")
      )

      // Five floating musical notes with glow halos
      for (i in 0 until 5) {
        val pState = noteProgressStates[i]
        val noteColor = noteColors[i]
        Box(
          modifier = Modifier
            .offset(x = noteOffsetsX[i], y = noteOffsetsY[i])
            .graphicsLayer {
              val p = pState?.value ?: (0.25f + i * 0.12f)
              val swaySign = if (i % 2 == 0) 1f else -1f
              val swayX = if (reducedMotion) 0f else sin(p * 2.0 * PI).toFloat() * 10f * swaySign
              translationX = swayX * density
              translationY = if (reducedMotion) 0f else -74f * p * density
              rotationZ = if (reducedMotion) 0f else 16f * swaySign * sin(p * PI).toFloat()
              val scale = if (reducedMotion) 1f else 0.72f + 0.36f * sin(p * PI).toFloat()
              scaleX = scale
              scaleY = scale
              alpha = if (reducedMotion) {
                if (i < 3) 0.72f else 0f
              } else {
                when {
                  p < 0.16f -> p / 0.16f
                  p > 0.72f -> ((1f - p) / 0.28f).coerceIn(0f, 1f)
                  else -> 1f
                }
              }
            },
          contentAlignment = Alignment.Center
        ) {
          Canvas(modifier = Modifier.size(28.dp)) {
            drawCircle(
              brush = Brush.radialGradient(
                colors = listOf(noteColor.copy(alpha = 0.40f), Color.Transparent)
              )
            )
          }
          Text(
            text = noteSymbols[i],
            color = noteColor,
            fontSize = noteSizes[i],
            fontWeight = FontWeight.Bold
          )
        }
      }
    }

    // Glass pill under orb: equalizer + chosen favourite song + subtle loved badge
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
        isActive = isActive && (isPreviewPlaying || !reducedMotion),
        reducedMotion = reducedMotion,
        height = 14.dp
      )
      Text(
        text = "${showcase.song.title} · ${showcase.song.artist}",
        color = AurixDesignTokens.TextPrimary,
        fontFamily = fontFamily,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f, fill = false)
      )
      if (isLoved) {
        Icon(
          imageVector = Icons.Rounded.Favorite,
          contentDescription = null,
          tint = Color(0xFFFF4D7D),
          modifier = Modifier.size(14.dp)
        )
      }
    }
  }
}

@Composable
private fun OnboardingShuffleChip(
  label: String,
  accentColor: Color,
  fontFamily: FontFamily,
  hazeState: HazeState?,
  enabled: Boolean,
  onClick: () -> Unit,
  testTag: String
) {
  val chipShape = RoundedCornerShape(99.dp)
  Row(
    modifier = Modifier
      .onboardingFocusRing(chipShape)
      .onboardingGlass(
        shape = chipShape,
        hazeState = hazeState,
        borderColor = accentColor.copy(alpha = 0.35f)
      )
      .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
      .padding(horizontal = 9.dp, vertical = 4.dp)
      .testTag(testTag),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(4.dp)
  ) {
    Icon(
      imageVector = Icons.Rounded.Shuffle,
      contentDescription = null,
      tint = accentColor,
      modifier = Modifier.size(12.dp)
    )
    Text(
      text = label,
      color = AurixDesignTokens.TextPrimary.copy(alpha = 0.9f),
      fontFamily = fontFamily,
      fontSize = 10.sp,
      fontWeight = FontWeight.SemiBold,
      maxLines = 1
    )
  }
}
