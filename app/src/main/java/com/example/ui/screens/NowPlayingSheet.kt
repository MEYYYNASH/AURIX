package com.example.ui.screens

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.R
import com.example.model.LyricsResult
import com.example.model.Song
import com.example.playback.AudioSpectrumBus
import com.example.playback.EqPreset
import com.example.playback.EqualizerUiState
import com.example.recommendation.MoodFlowPick
import com.example.recommendation.SimilarTrack
import com.example.ui.theme.HeartRed
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingSheet(
  song: Song,
  isPlaying: Boolean,
  currentPositionMs: Long,
  positionProvider: () -> Long = { currentPositionMs },
  durationMs: Long,
  lyricsResult: LyricsResult?,
  lyricOffsetMs: Long = 0L,
  dynamicBg: Boolean,
  reducedMotion: Boolean,
  shuffleEnabled: Boolean,
  repeatEnabled: Boolean,
  playbackSpeed: Float,
  sleepMinutes: Int,
  sleepRemainingSeconds: Long = 0L,
  sleepEndOfTrack: Boolean = false,
  onPlayPause: () -> Unit,
  onNext: () -> Unit,
  onPrevious: () -> Unit,
  onToggleShuffle: () -> Unit,
  onToggleRepeat: () -> Unit,
  onCycleSpeed: () -> Unit,
  onSetPlaybackSpeed: (Float) -> Unit = {},
  onCycleSleep: () -> Unit,
  onSetSleepMinutes: (Int) -> Unit = {},
  onSetSleepEndOfTrack: () -> Unit = {},
  onCancelSleep: () -> Unit = {},
  onSeek: (Long) -> Unit,
  onFavorite: () -> Unit,
  onClose: () -> Unit,
  onLineClick: (Long) -> Unit,
  onAlignLine: (Long) -> Unit = {},
  onAdjustOffset: (Long) -> Unit = {},
  onResetOffset: () -> Unit = {},
  onRetryLyrics: () -> Unit = {},
  hasLyrics: Boolean = false,
  equalizerState: EqualizerUiState = EqualizerUiState(),
  onToggleEqualizer: (Boolean) -> Unit = {},
  onSelectEqPreset: (EqPreset) -> Unit = {},
  onEqBandLevelChanged: (Int, Int) -> Unit = { _, _ -> },
  onEqBassBoostChanged: (Int) -> Unit = {},
  onEqVirtualizerChanged: (Int) -> Unit = {},
  onResetEq: () -> Unit = {},
  queue: List<Song> = emptyList(),
  onJumpToQueueIndex: (Int) -> Unit = {},
  onMoveQueueItem: (Int, Int) -> Unit = { _, _ -> },
  onRemoveQueueItem: (Int) -> Unit = {},
  onClearUpcomingQueue: () -> Unit = {},
  onPlayNextSong: ((Song) -> Unit)? = null,
  onAddToQueueSong: ((Song) -> Unit)? = null,
  onHideSong: () -> Unit = {},
  onBlockArtist: () -> Unit = {},
  onCheckLyrics: ((Song) -> Unit)? = null,
  onDeleteSong: (() -> Unit)? = null,
  onAddToAlbum: ((Song) -> Unit)? = null,
  similarSongsProvider: (Song) -> List<SimilarTrack> = { emptyList() },
  onPlaySimilarSong: (Song) -> Unit = {},
  moodFlowEnabled: Boolean = true,
  moodFlowPick: MoodFlowPick? = null,
  onToggleMoodFlow: () -> Unit = {},
  visualizerEnabled: Boolean = true,
  onToggleVisualizer: (Boolean) -> Unit = {},
  spectrumBars: FloatArray = FloatArray(AudioSpectrumBus.BAR_COUNT),
  initialShowLyrics: Boolean = false,
  openViewRequestTrigger: Int = 0,
  lyricsAnimationStyle: com.example.lyrics.LyricsAnimationStyle = com.example.lyrics.LyricsAnimationStyle.SLIDE_UP,
  developerModeEnabled: Boolean = false,
  onOpenLyricsEditor: (() -> Unit)? = null,
  onOpenStandby: () -> Unit = {},
  orbiMood: OrbiMood = OrbiMood.CLASSIC_IDLE,
  orbiTheme: OrbiCompanionTheme = OrbiCompanionTheme.CLASSIC,
  orbiBeatEnergy: Float = 0f,
  onOpenOrbiCompanion: () -> Unit = {},
  widgetCustomizerConfig: com.example.widget.WidgetCustomizerConfig = com.example.widget.WidgetCustomizerConfig.DEFAULT
) {
  BackHandler { onClose() }
  LaunchedEffect(song.id) {
    onCheckLyrics?.invoke(song)
  }

  var showLyrics by remember { mutableStateOf(initialShowLyrics) }
  LaunchedEffect(openViewRequestTrigger, initialShowLyrics) {
    if (openViewRequestTrigger > 0) {
      showLyrics = initialShowLyrics
    }
  }
  var showDetails by remember { mutableStateOf(false) }
  var showShareLyrics by remember { mutableStateOf(false) }
  var showEqualizer by remember { mutableStateOf(false) }
  var showQueue by remember { mutableStateOf(false) }
  var showSleepSpeed by remember { mutableStateOf(false) }
  var showControlsExtras by rememberSaveable { mutableStateOf(false) }
  var dynamicControlsBlur by rememberSaveable { mutableStateOf(true) }

  val (c1, c2) = rememberArtworkColors(song.artworkUrl, song.title)
  val bgAlpha by animateFloatAsState(
    targetValue = if (dynamicBg) 1.0f else 0f,
    animationSpec = if (reducedMotion) snap() else tween(500),
    label = "np_bg_alpha"
  )

  val infiniteTransition = rememberInfiniteTransition(label = "np_backdrop_drift")
  val backdropScale by infiniteTransition.animateFloat(
    initialValue = 1.28f,
    targetValue = 1.44f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = 6800, easing = FastOutSlowInEasing),
      repeatMode = RepeatMode.Reverse
    ),
    label = "np_drift_scale"
  )
  val backdropShiftX by infiniteTransition.animateFloat(
    initialValue = -16f,
    targetValue = 16f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = 8200, easing = FastOutSlowInEasing),
      repeatMode = RepeatMode.Reverse
    ),
    label = "np_drift_x"
  )
  val activeBgScale = if (reducedMotion) 1.32f else backdropScale
  val activeBgShiftX = if (reducedMotion) 0f else backdropShiftX

  val heartScale by animateFloatAsState(
    targetValue = if (song.isFavorite) 1.25f else 1.0f,
    animationSpec = if (reducedMotion) snap() else spring(dampingRatio = Spring.DampingRatioMediumBouncy),
    label = "np_heart"
  )

  val rootInteraction = remember { MutableInteractionSource() }
  BoxWithConstraints(
    modifier = Modifier
      .fillMaxSize()
      .background(Color(0xFF0C0F16))
      .clickable(interactionSource = rootInteraction, indication = null) {}
  ) {
    val maxDetailsSheetHeight = (maxHeight * 0.55f).coerceAtLeast(300.dp)
    val bgCustom = LocalDynamicBgCustomization.current
    val npBlurDp = (bgCustom.blurRadiusDp.coerceIn(0f, 100f) * 0.80f).coerceIn(0f, 92f).dp
    val npDimEffective = bgCustom.dimLevel.coerceIn(0f, 0.90f).coerceAtLeast(0.22f)
    val npDimRatio = (npDimEffective / com.example.data.PreferencesManager.DEFAULT_DYNAMIC_BG_DIM)
      .coerceIn(0.45f, 2.25f)
    // True Dynamic Artwork Background sampled from actual album cover colors + blurred drifting art
    if (bgAlpha > 0.01f) {
      Box(modifier = Modifier.fillMaxSize().alpha(bgAlpha)) {
        val bgModel = resolveArtworkModel(song.artworkUrl, song.source)
        if (bgModel != null) {
          AsyncImage(
            model = bgModel,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
              .fillMaxSize()
              .offset(x = activeBgShiftX.dp)
              .scale(activeBgScale)
              .then(if (npBlurDp > 0.dp) Modifier.blur(npBlurDp) else Modifier)
          )
        }
        Box(
          modifier = Modifier
            .fillMaxSize()
            .background(
              Brush.radialGradient(
                colors = listOf(
                  c1.copy(alpha = 0.62f),
                  c2.copy(alpha = 0.34f),
                  Color.Transparent
                ),
                center = Offset(320f, 380f),
                radius = 1250f
              )
            )
        )
        Box(
          modifier = Modifier
            .fillMaxSize()
            .background(
              Brush.radialGradient(
                colors = listOf(
                  c2.copy(alpha = 0.44f),
                  Color.Transparent
                ),
                center = Offset(780f, 1550f),
                radius = 1000f
              )
            )
        )
        // Balanced contrast gradient so cover art colors glow vibrantly while keeping white controls crisp
        Box(
          modifier = Modifier
            .fillMaxSize()
            .background(
              Brush.verticalGradient(
                colors = listOf(
                  Color(0xFF090C14).copy(alpha = (0.32f * npDimRatio).coerceIn(0.06f, 0.86f)),
                  Color(0xFF090C14).copy(alpha = (0.42f * npDimRatio).coerceIn(0.08f, 0.90f)),
                  Color(0xFF080A11).copy(alpha = (0.72f * npDimRatio).coerceIn(0.16f, 0.96f))
                )
              )
            )
        )
      }
    }

    Column(
      modifier = Modifier
        .fillMaxSize()
        .windowInsetsPadding(WindowInsets.safeDrawing)
        .padding(horizontal = 24.dp, vertical = 12.dp),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      // Header
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        IconButton(
          onClick = onClose,
          modifier = Modifier.minimumInteractiveComponentSize().testTag("np_close_button")
        ) {
          Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = stringResource(id = R.string.np_close_cd), tint = Color.White, modifier = Modifier.size(28.dp))
        }

        // Glass Switch: Player | Lyrics
        Surface(
          shape = RoundedCornerShape(999.dp),
          color = Color.White.copy(alpha = 0.14f),
          border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.22f))
        ) {
          val tabs = listOf(
            false to stringResource(id = R.string.np_tab_player),
            true to stringResource(id = R.string.np_tab_lyrics)
          )
          Row(modifier = Modifier.padding(3.dp)) {
            tabs.forEach { (lyricsTab, label) ->
              val selected = showLyrics == lyricsTab
              Box(
                modifier = Modifier
                  .clip(RoundedCornerShape(999.dp))
                  .background(if (selected) Color.White.copy(alpha = 0.26f) else Color.Transparent)
                  .clickable { showLyrics = lyricsTab }
                  .testTag(if (lyricsTab) "np_tab_lyrics" else "np_tab_player")
                  .padding(horizontal = 18.dp, vertical = 8.dp)
              ) {
                Text(
                  text = label,
                  color = if (selected) Color.White else Color.White.copy(alpha = 0.72f),
                  style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold, lineHeight = 18.sp)
                )
              }
            }
          }
        }

        IconButton(
          onClick = { showDetails = true },
          modifier = Modifier.minimumInteractiveComponentSize().testTag("np_details_button")
        ) {
          Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(id = R.string.np_details_cd), tint = Color.White)
        }
      }

      Spacer(modifier = Modifier.height(12.dp))

      val syncedLines = lyricsResult?.synced
      val effectivePositionMs = (currentPositionMs + lyricOffsetMs).coerceAtLeast(0L)
      val isPreviewClip = song.durationSeconds <= 35L &&
        !song.streamUrl.startsWith("file://") &&
        !song.streamUrl.startsWith("content://")
      val activeLyricLine = remember(syncedLines, effectivePositionMs) {
        val firstTs = syncedLines?.firstOrNull()?.timestampMs ?: 0L
        if (syncedLines.isNullOrEmpty() || effectivePositionMs + 150L < firstTs) null
        else syncedLines.lastOrNull { it.timestampMs <= effectivePositionMs + 150L }
      }

      // Pane Switcher with smooth fade
      AnimatedContent(
        targetState = showLyrics,
        modifier = Modifier.weight(1f).fillMaxWidth(),
        transitionSpec = {
          if (reducedMotion) fadeIn(snap()) togetherWith fadeOut(snap())
          else fadeIn(tween(260)) togetherWith fadeOut(tween(200))
        },
        label = "np_pane"
      ) { lyricsVisible ->
        if (lyricsVisible) {
          LyricsView(
            lyricsResult = lyricsResult,
            currentPositionMs = currentPositionMs,
            positionProvider = positionProvider,
            lyricOffsetMs = lyricOffsetMs,
            isPreviewClip = isPreviewClip,
            isPlaying = isPlaying,
            lyricsAnimationStyle = lyricsAnimationStyle,
            accentColor = c1,
            reducedMotion = reducedMotion,
            onLineClick = onLineClick,
            onAlignLine = onAlignLine,
            onAdjustOffset = onAdjustOffset,
            onResetOffset = onResetOffset,
            onRetryLyrics = onRetryLyrics,
            onShareLyrics = { showShareLyrics = true },
            developerModeEnabled = developerModeEnabled,
            onOpenLyricsEditor = onOpenLyricsEditor,
            widgetCustomizerConfig = widgetCustomizerConfig,
            songArtworkUrl = song.artworkUrl
          )
        } else {
          Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
          ) {
            val artScale by animateFloatAsState(
              targetValue = if (isPlaying) 1.0f else 0.94f,
              animationSpec = if (reducedMotion) snap() else spring(dampingRatio = Spring.DampingRatioLowBouncy),
              label = "art_scale"
            )
            val auraPulse by infiniteTransition.animateFloat(
              initialValue = 0.96f,
              targetValue = 1.08f,
              animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 2800, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
              ),
              label = "art_aura_pulse"
            )
            val artSize = if (activeLyricLine != null) 246.dp else 274.dp

            Box(contentAlignment = Alignment.Center) {
              // Ambient Artwork Aura behind the cover art while actively playing
              Box(
                modifier = Modifier
                  .size(artSize + 36.dp)
                  .scale(
                    when {
                      !isPlaying -> 0.88f
                      reducedMotion -> 1.0f
                      else -> auraPulse
                    }
                  )
                  .alpha(if (isPlaying) 0.72f else 0.25f)
                  .drawBehind {
                    if (size.minDimension <= 0f) return@drawBehind
                    drawCircle(
                      brush = Brush.radialGradient(
                        colors = listOf(
                          c1.copy(alpha = 0.50f),
                          c2.copy(alpha = 0.24f),
                          Color.Transparent
                        ),
                        center = Offset(size.width * 0.5f, size.height * 0.5f),
                        radius = (size.maxDimension * 0.5f).coerceAtLeast(1f)
                      ),
                      radius = (size.minDimension * 0.5f).coerceAtLeast(1f),
                      center = Offset(size.width * 0.5f, size.height * 0.5f)
                    )
                  }
              )

              Surface(
                modifier = Modifier
                  .size(artSize)
                  .scale(artScale),
                shape = RoundedCornerShape(28.dp),
                shadowElevation = 24.dp,
                color = Color.Transparent
              ) {
                SongArtwork(
                  song = song,
                  modifier = Modifier.fillMaxSize(),
                  cornerRadius = 28.dp,
                  iconSize = 84.dp
                )
              }
            }

            Spacer(modifier = Modifier.height(22.dp))

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Text(
                    text = song.title,
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp, fontWeight = FontWeight.Bold),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = Color.White,
                    modifier = Modifier.weight(1f, fill = false)
                  )
                  LyricsTag(
                    visible = hasLyrics,
                    size = LyricsTagSize.Full,
                    darkTheme = true,
                    reducedMotion = reducedMotion,
                    onClick = { showLyrics = true },
                    modifier = Modifier.padding(start = 8.dp)
                  )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                  text = song.artist,
                  style = MaterialTheme.typography.bodyMedium,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                  color = Color.White.copy(alpha = 0.78f)
                )
              }
              IconButton(
                onClick = onFavorite,
                modifier = Modifier.minimumInteractiveComponentSize().testTag("np_favorite_button")
              ) {
                Icon(
                  imageVector = if (song.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                  contentDescription = "Favorite",
                  tint = if (song.isFavorite) HeartRed else Color.White,
                  modifier = Modifier.scale(heartScale)
                )
              }
            }

            if (activeLyricLine != null) {
              Spacer(modifier = Modifier.height(12.dp))
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .clip(RoundedCornerShape(16.dp))
                  .background(Color.White.copy(alpha = 0.12f))
                  .border(1.dp, Color.White.copy(alpha = 0.20f), RoundedCornerShape(16.dp))
                  .clickable { showLyrics = true }
                  .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                Icon(
                  imageVector = Icons.Rounded.Lyrics,
                  contentDescription = null,
                  tint = Color.White.copy(alpha = 0.85f),
                  modifier = Modifier.size(16.dp)
                )
                Text(
                  text = activeLyricLine.text,
                  style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                  color = Color.White.copy(alpha = 0.94f),
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                  modifier = Modifier.weight(1f)
                )
              }
            }

            AnimatedVisibility(
              visible = visualizerEnabled,
              enter = if (reducedMotion) fadeIn(snap()) else expandVertically(tween(220)) + fadeIn(tween(200)),
              exit = if (reducedMotion) fadeOut(snap()) else shrinkVertically(tween(180)) + fadeOut(tween(150))
            ) {
              Column(modifier = Modifier.fillMaxWidth()) {
                Spacer(modifier = Modifier.height(10.dp))
                BeatReactiveVisualizerStrip(
                  bars = spectrumBars,
                  isPlaying = isPlaying,
                  reducedMotion = reducedMotion,
                  accentStart = c1,
                  accentEnd = c2,
                  modifier = Modifier
                    .fillMaxWidth()
                    .testTag("np_beat_visualizer")
                )
              }
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(10.dp))

      // Player Controls Dock with Dynamic Album-Art-Color Blurred Glassmorphism + Open/Close Feature Toggle
      val controlsShape = RoundedCornerShape(28.dp)
      val controlsBlurActive = dynamicControlsBlur && dynamicBg
      val controlsBorderBrush = Brush.verticalGradient(
        colors = if (controlsBlurActive) {
          listOf(
            c1.copy(alpha = 0.54f),
            c2.copy(alpha = 0.24f),
            Color.White.copy(alpha = 0.14f)
          )
        } else {
          listOf(
            Color.White.copy(alpha = 0.24f),
            Color.White.copy(alpha = 0.10f)
          )
        }
      )

      Box(
        modifier = Modifier
          .fillMaxWidth()
          .clip(controlsShape)
          .border(1.dp, controlsBorderBrush, controlsShape)
          .testTag("np_controls_glass_card")
      ) {
        DynamicArtGlassBackdropLayer(
          song = song,
          artColors = c1 to c2,
          enabled = controlsBlurActive,
          darkTheme = true,
          reducedMotion = reducedMotion,
          blurRadius = 34.dp,
          artAlpha = 0.38f,
          scrimAlpha = if (controlsBlurActive) 0.46f else 0.56f,
          modifier = Modifier.matchParentSize()
        )

        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
          horizontalAlignment = Alignment.CenterHorizontally
        ) {
          // Seek bar with sleek circular white thumb
          val totalMs = durationMs.coerceAtLeast(song.durationSeconds * 1000L).coerceAtLeast(1L)
          val sliderPos = currentPositionMs.toFloat().coerceIn(0f, totalMs.toFloat())
          val sliderInteraction = remember { MutableInteractionSource() }

          Slider(
            value = sliderPos,
            onValueChange = { onSeek(it.toLong()) },
            valueRange = 0f..totalMs.toFloat(),
            interactionSource = sliderInteraction,
            thumb = {
              Box(
                modifier = Modifier
                  .size(16.dp)
                  .clip(CircleShape)
                  .background(Color.White)
                  .border(1.dp, c1.copy(alpha = 0.45f), CircleShape)
              )
            },
            track = { sliderState ->
              val fraction = ((sliderState.value - sliderState.valueRange.start) /
                (sliderState.valueRange.endInclusive - sliderState.valueRange.start).coerceAtLeast(1f))
                .coerceIn(0f, 1f)
              Box(
                modifier = Modifier
                  .fillMaxWidth()
                  .height(5.dp)
                  .clip(CircleShape)
                  .background(Color.White.copy(alpha = 0.24f))
              ) {
                Box(
                  modifier = Modifier
                    .fillMaxWidth(fraction)
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(
                      Brush.horizontalGradient(
                        colors = listOf(Color.White, c1.copy(alpha = 0.85f), Color.White.copy(alpha = 0.92f))
                      )
                    )
                )
              }
            }
          )

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Text(text = formatTime(currentPositionMs), style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.78f))
            Text(text = formatTime(totalMs), style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.78f))
          }

          Spacer(modifier = Modifier.height(6.dp))

          // Playback controls
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
          ) {
            IconButton(onClick = onToggleShuffle, modifier = Modifier.minimumInteractiveComponentSize()) {
              Icon(
                Icons.Rounded.Shuffle,
                contentDescription = "Shuffle",
                tint = if (shuffleEnabled) c1 else Color.White.copy(alpha = 0.70f)
              )
            }
            IconButton(onClick = onPrevious, modifier = Modifier.minimumInteractiveComponentSize()) {
              Icon(Icons.Rounded.SkipPrevious, contentDescription = "Previous", modifier = Modifier.size(34.dp), tint = Color.White)
            }
            Surface(
              modifier = Modifier
                .size(66.dp)
                .clip(CircleShape)
                .clickable { onPlayPause() }
                .testTag("np_play_pause_button"),
              shape = CircleShape,
              color = Color.White,
              shadowElevation = 12.dp
            ) {
              Box(contentAlignment = Alignment.Center) {
                Icon(
                  imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                  contentDescription = "Play or Pause",
                  tint = Color(0xFF111111),
                  modifier = Modifier.size(32.dp)
                )
              }
            }
            IconButton(onClick = onNext, modifier = Modifier.minimumInteractiveComponentSize()) {
              Icon(Icons.Rounded.SkipNext, contentDescription = "Next", modifier = Modifier.size(34.dp), tint = Color.White)
            }
            IconButton(onClick = onToggleRepeat, modifier = Modifier.minimumInteractiveComponentSize()) {
              Icon(
                Icons.Rounded.Repeat,
                contentDescription = "Repeat",
                tint = if (repeatEnabled) c1 else Color.White.copy(alpha = 0.70f)
              )
            }
          }

          Spacer(modifier = Modifier.height(6.dp))

          // Controls Utility Header: Art Blur Toggle + Beat Visualizer Toggle + Open/Close Feature Dock Toggle
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
              IconButton(
                onClick = { dynamicControlsBlur = !dynamicControlsBlur },
                modifier = Modifier
                  .minimumInteractiveComponentSize()
                  .testTag("np_toggle_controls_blur_button")
              ) {
                Icon(
                  imageVector = if (controlsBlurActive) Icons.Rounded.BlurOn else Icons.Rounded.BlurOff,
                  contentDescription = stringResource(id = R.string.np_controls_blur_cd),
                  tint = if (controlsBlurActive) Color.White else Color.White.copy(alpha = 0.62f),
                  modifier = Modifier.size(20.dp)
                )
              }

              IconButton(
                onClick = { onToggleVisualizer(!visualizerEnabled) },
                modifier = Modifier
                  .minimumInteractiveComponentSize()
                  .testTag("np_toggle_visualizer_button")
              ) {
                Icon(
                  imageVector = Icons.Rounded.GraphicEq,
                  contentDescription = stringResource(id = R.string.np_visualizer_toggle_cd),
                  tint = if (visualizerEnabled) c1 else Color.White.copy(alpha = 0.62f),
                  modifier = Modifier.size(20.dp)
                )
              }
            }

            Surface(
              onClick = { showControlsExtras = !showControlsExtras },
              shape = CircleShape,
              color = if (showControlsExtras) Color.White.copy(alpha = 0.10f) else c1.copy(alpha = 0.28f),
              border = BorderStroke(
                1.dp,
                if (showControlsExtras) Color.White.copy(alpha = 0.18f) else c1.copy(alpha = 0.72f)
              ),
              modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("np_toggle_extras_button")
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
              ) {
                Icon(
                  imageVector = if (showControlsExtras) Icons.Rounded.Close else Icons.Rounded.KeyboardArrowUp,
                  contentDescription = stringResource(
                    id = if (showControlsExtras) R.string.np_controls_hide else R.string.np_controls_show
                  ),
                  tint = Color.White,
                  modifier = Modifier.size(15.dp)
                )
                Text(
                  text = stringResource(
                    id = if (showControlsExtras) R.string.np_controls_hide else R.string.np_controls_show
                  ),
                  style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                  color = Color.White
                )
              }
            }
          }

          AnimatedVisibility(
            visible = showControlsExtras,
            enter = if (reducedMotion) fadeIn(snap()) else expandVertically(tween(240)) + fadeIn(tween(200)),
            exit = if (reducedMotion) fadeOut(snap()) else shrinkVertically(tween(200)) + fadeOut(tween(150))
          ) {
            Column(
              modifier = Modifier.fillMaxWidth(),
              horizontalAlignment = Alignment.CenterHorizontally
            ) {
              Spacer(modifier = Modifier.height(6.dp))

              // MoodFlow Intelligent Mood Shuffle Toggle Chip + Live Match % Badge
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Surface(
                  onClick = onToggleMoodFlow,
                  shape = CircleShape,
                  color = if (moodFlowEnabled) c1.copy(alpha = 0.26f) else Color.White.copy(alpha = 0.12f),
                  border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (moodFlowEnabled) c1.copy(alpha = 0.80f) else Color.White.copy(alpha = 0.20f)
                  ),
                  modifier = Modifier
                    .defaultMinSize(minHeight = 48.dp)
                    .testTag("np_moodflow_chip")
                ) {
                  Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                  ) {
                    Icon(
                      imageVector = Icons.Rounded.AutoAwesome,
                      contentDescription = stringResource(id = R.string.moodflow_toggle_cd),
                      tint = if (moodFlowEnabled) Color.White else Color.White.copy(alpha = 0.72f),
                      modifier = Modifier.size(16.dp)
                    )
                    Text(
                      text = stringResource(
                        id = if (moodFlowEnabled) R.string.moodflow_on_label else R.string.moodflow_off_label
                      ),
                      style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                      color = if (moodFlowEnabled) Color.White else Color.White.copy(alpha = 0.78f)
                    )
                    if (moodFlowEnabled && moodFlowPick != null) {
                      Surface(
                        shape = CircleShape,
                        color = Color.White.copy(alpha = 0.20f),
                        modifier = Modifier.testTag("np_moodflow_match_badge")
                      ) {
                        Text(
                          text = stringResource(id = R.string.moodflow_match_badge, moodFlowPick.matchPercent),
                          style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 11.sp
                          ),
                          color = Color.White,
                          modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                      }
                    }
                  }
                }
              }

              Spacer(modifier = Modifier.height(6.dp))

              // Unified Liquid Glass Control Dock: Sleep, EQ, Queue, Speed
              val activeQueueIdx = remember(queue, song.id) { queue.indexOfFirst { it.id == song.id } }
              val upcomingCount = remember(queue, activeQueueIdx) {
                if (queue.isEmpty()) 0
                else if (activeQueueIdx >= 0) (queue.size - activeQueueIdx - 1).coerceAtLeast(0)
                else queue.size
              }
              val sleepActive = sleepEndOfTrack || sleepRemainingSeconds > 0L || sleepMinutes > 0
              val sleepChipText = when {
                sleepEndOfTrack -> stringResource(id = R.string.np_sleep_end_short)
                sleepRemainingSeconds > 0L -> "${sleepRemainingSeconds / 60}:${String.format("%02d", sleepRemainingSeconds % 60)}"
                sleepMinutes > 0 -> "${sleepMinutes}m"
                else -> stringResource(id = R.string.np_sleep_chip_off)
              }
              val eqActive = equalizerState.enabled
              val eqPresetName = presetDisplayName(EqPreset.fromId(equalizerState.presetId))
              val speedActive = abs(playbackSpeed - 1.0f) > 0.01f
              val speedText = formatPlaybackSpeed(playbackSpeed)

              Surface(
                shape = RoundedCornerShape(22.dp),
                color = Color.White.copy(alpha = 0.10f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
                modifier = Modifier.fillMaxWidth()
              ) {
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(4.dp),
                  horizontalArrangement = Arrangement.spacedBy(5.dp),
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  DockControlPill(
                    icon = Icons.Rounded.Bedtime,
                    label = sleepChipText,
                    active = sleepActive,
                    accent = c1,
                    contentDescription = stringResource(id = R.string.sleep_sheet_title),
                    testTag = "np_sleep_button",
                    modifier = Modifier.weight(1f),
                    onClick = { showSleepSpeed = true }
                  )
                  DockControlPill(
                    icon = Icons.Rounded.Equalizer,
                    label = if (eqActive) eqPresetName else stringResource(id = R.string.eq_button_short),
                    active = eqActive,
                    accent = c1,
                    contentDescription = stringResource(id = R.string.eq_title),
                    testTag = "np_equalizer_button",
                    modifier = Modifier.weight(1f),
                    onClick = { showEqualizer = true }
                  )
                  DockControlPill(
                    icon = Icons.AutoMirrored.Rounded.QueueMusic,
                    label = if (upcomingCount > 0) "${stringResource(id = R.string.np_queue_button)} · $upcomingCount" else stringResource(id = R.string.np_queue_button),
                    active = upcomingCount > 0,
                    accent = c1,
                    contentDescription = stringResource(id = R.string.queue_title),
                    testTag = "np_queue_button",
                    modifier = Modifier.weight(1f),
                    onClick = { showQueue = true }
                  )
                  DockControlPill(
                    icon = Icons.Rounded.Speed,
                    label = speedText,
                    active = speedActive,
                    accent = c1,
                    contentDescription = stringResource(id = R.string.speed_sheet_title),
                    testTag = "np_speed_button",
                    modifier = Modifier.weight(1f),
                    onClick = { showSleepSpeed = true }
                  )
                }
              }
            }
          }
        }
      }
    }

    if (showDetails) {
      SongDetailsSheet(
        song = song,
        hasLyrics = hasLyrics,
        isPlaying = isPlaying,
        reducedMotion = reducedMotion,
        maxSheetHeight = maxDetailsSheetHeight,
        similarSongs = remember(song) { similarSongsProvider(song) },
        onSelectSimilarSong = { similar ->
          showDetails = false
          onPlaySimilarSong(similar)
        },
        onDismiss = { showDetails = false },
        onPlay = {
          showDetails = false
          if (!isPlaying) onPlayPause()
        },
        onFavorite = onFavorite,
        onShare = {
          showDetails = false
          showShareLyrics = true
        },
        onHideSong = {
          showDetails = false
          onHideSong()
        },
        onBlockArtist = {
          showDetails = false
          onBlockArtist()
        },
        onAddToAlbum = onAddToAlbum?.let { cb -> { cb(song) } },
        onPlayNext = onPlayNextSong?.let { cb -> { cb(song) } },
        onAddToQueue = onAddToQueueSong?.let { cb -> { cb(song) } },
        onOpenStandby = onOpenStandby,
        onOpenOrbiCompanion = onOpenOrbiCompanion,
        onEditLyrics = if (developerModeEnabled) onOpenLyricsEditor else null,
        isCustomLyrics = lyricsResult?.isCustom == true && developerModeEnabled,
        onDelete = onDeleteSong
      )
    }

    if (showEqualizer) {
      EqualizerSheet(
        state = equalizerState,
        onToggleEnabled = onToggleEqualizer,
        onSelectPreset = onSelectEqPreset,
        onBandLevelChanged = onEqBandLevelChanged,
        onBassBoostChanged = onEqBassBoostChanged,
        onVirtualizerChanged = onEqVirtualizerChanged,
        onReset = onResetEq,
        onDismiss = { showEqualizer = false }
      )
    }

    if (showQueue) {
      UpNextQueueSheet(
        currentSong = song,
        queue = queue,
        isPlaying = isPlaying,
        reducedMotion = reducedMotion,
        onJumpToIndex = { idx ->
          onJumpToQueueIndex(idx)
        },
        onMoveItem = onMoveQueueItem,
        onRemoveItem = onRemoveQueueItem,
        onClearUpcoming = onClearUpcomingQueue,
        onDismiss = { showQueue = false }
      )
    }

    if (showSleepSpeed) {
      SleepAndSpeedStudioSheet(
        sleepMinutes = sleepMinutes,
        sleepRemainingSeconds = sleepRemainingSeconds,
        sleepEndOfTrack = sleepEndOfTrack,
        playbackSpeed = playbackSpeed,
        onSetSleepMinutes = onSetSleepMinutes,
        onSetSleepEndOfTrack = onSetSleepEndOfTrack,
        onCancelSleep = onCancelSleep,
        onSetPlaybackSpeed = onSetPlaybackSpeed,
        onDismiss = { showSleepSpeed = false }
      )
    }

    if (showShareLyrics) {
      ShareLyricsSheet(
        song = song,
        lyricsResult = lyricsResult,
        currentPositionMs = positionProvider(),
        positionProvider = positionProvider,
        isPlaying = isPlaying,
        lyricsAnimationStyle = lyricsAnimationStyle,
        lyricOffsetMs = lyricOffsetMs,
        reducedMotion = reducedMotion,
        onDismiss = { showShareLyrics = false }
      )
    }
  }
}

@Composable
private fun DockControlPill(
  icon: ImageVector,
  label: String,
  active: Boolean,
  accent: Color,
  contentDescription: String,
  testTag: String,
  modifier: Modifier = Modifier,
  onClick: () -> Unit
) {
  Surface(
    onClick = onClick,
    shape = RoundedCornerShape(20.dp),
    color = if (active) accent.copy(alpha = 0.28f) else Color.White.copy(alpha = 0.06f),
    border = BorderStroke(
      1.dp,
      if (active) accent.copy(alpha = 0.78f) else Color.White.copy(alpha = 0.10f)
    ),
    modifier = modifier
      .defaultMinSize(minHeight = 48.dp)
      .testTag(testTag)
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 6.dp, vertical = 10.dp),
      horizontalArrangement = Arrangement.Center,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Icon(
        imageVector = icon,
        contentDescription = contentDescription,
        tint = if (active) Color.White else Color.White.copy(alpha = 0.78f),
        modifier = Modifier.size(15.dp)
      )
      Spacer(modifier = Modifier.width(4.dp))
      Text(
        text = label,
        style = MaterialTheme.typography.labelSmall.copy(
          fontSize = 11.5.sp,
          fontWeight = if (active) FontWeight.Bold else FontWeight.SemiBold,
          lineHeight = 14.sp
        ),
        color = if (active) Color.White else Color.White.copy(alpha = 0.84f),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SleepAndSpeedStudioSheet(
  sleepMinutes: Int,
  sleepRemainingSeconds: Long,
  sleepEndOfTrack: Boolean,
  playbackSpeed: Float,
  onSetSleepMinutes: (Int) -> Unit,
  onSetSleepEndOfTrack: () -> Unit,
  onCancelSleep: () -> Unit,
  onSetPlaybackSpeed: (Float) -> Unit,
  onDismiss: () -> Unit
) {
  val surfaceColor = MaterialTheme.colorScheme.surface
  val bgColor = MaterialTheme.colorScheme.background
  val isDark = surfaceColor.luminance() < 0.5f || isSystemInDarkTheme()
  val isAmoled = isDark && bgColor == Color.Black
  val accent = MaterialTheme.colorScheme.primary

  val sheetFill = when {
    isAmoled -> Color(0xFF0A0E17).copy(alpha = 0.96f)
    isDark -> surfaceColor.copy(alpha = 0.94f)
    else -> surfaceColor.copy(alpha = 0.97f)
  }
  val topBorderBrush = Brush.verticalGradient(
    colors = if (isDark) {
      listOf(Color.White.copy(alpha = 0.24f), Color.White.copy(alpha = 0.04f))
    } else {
      listOf(Color.White.copy(alpha = 0.95f), Color.Black.copy(alpha = 0.06f))
    }
  )
  val sheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    shape = sheetShape,
    containerColor = Color.Transparent,
    contentColor = MaterialTheme.colorScheme.onSurface,
    scrimColor = Color.Black.copy(alpha = 0.54f),
    tonalElevation = 0.dp,
    dragHandle = null
  ) {
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .clip(sheetShape)
        .background(sheetFill)
        .background(
          Brush.radialGradient(
            colors = listOf(
              accent.copy(alpha = if (isDark) 0.16f else 0.09f),
              Color.Transparent
            ),
            center = Offset(240f, 100f),
            radius = 650f
          )
        )
        .border(1.dp, topBorderBrush, sheetShape)
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .verticalScroll(rememberScrollState())
          .navigationBarsPadding()
          .padding(horizontal = 20.dp)
          .padding(top = 10.dp, bottom = 20.dp)
      ) {
        Box(
          modifier = Modifier
            .align(Alignment.CenterHorizontally)
            .padding(bottom = 14.dp)
            .width(38.dp)
            .height(4.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f))
        )

        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = stringResource(id = R.string.sleep_sheet_title),
              style = MaterialTheme.typography.titleMedium.copy(
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
              ),
              color = MaterialTheme.colorScheme.onSurface
            )
            Text(
              text = stringResource(id = R.string.sleep_sheet_subtitle),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
          ) {
            if (sleepEndOfTrack || sleepRemainingSeconds > 0L) {
              Surface(
                shape = CircleShape,
                color = accent.copy(alpha = 0.18f),
                border = BorderStroke(1.dp, accent.copy(alpha = 0.55f))
              ) {
                val badgeText = if (sleepEndOfTrack) {
                  stringResource(id = R.string.sleep_end_of_track)
                } else {
                  "${sleepRemainingSeconds / 60}:${String.format("%02d", sleepRemainingSeconds % 60)}"
                }
                Text(
                  text = badgeText,
                  style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                  color = accent,
                  modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                )
              }
            }
            IconButton(
              onClick = onDismiss,
              modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("sleep_speed_close_button")
            ) {
              Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = stringResource(id = R.string.cd_close_sheet),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(14.dp))

        val sleepOptions = listOf(
          0 to stringResource(id = R.string.sleep_off),
          15 to stringResource(id = R.string.sleep_minutes_option, 15),
          30 to stringResource(id = R.string.sleep_minutes_option, 30),
          45 to stringResource(id = R.string.sleep_minutes_option, 45),
          60 to stringResource(id = R.string.sleep_minutes_option, 60),
          -1 to stringResource(id = R.string.sleep_end_of_track)
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          sleepOptions.chunked(3).forEach { rowItems ->
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              rowItems.forEach { (code, label) ->
                val selected = when (code) {
                  0 -> !sleepEndOfTrack && sleepMinutes == 0 && sleepRemainingSeconds == 0L
                  -1 -> sleepEndOfTrack
                  else -> !sleepEndOfTrack && sleepMinutes == code
                }
                StudioOptionChip(
                  label = label,
                  selected = selected,
                  accent = accent,
                  isDark = isDark,
                  testTag = "sleep_option_$code",
                  modifier = Modifier.weight(1f),
                  onClick = {
                    when (code) {
                      0 -> onCancelSleep()
                      -1 -> onSetSleepEndOfTrack()
                      else -> onSetSleepMinutes(code)
                    }
                  }
                )
              }
            }
          }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Text(
            text = stringResource(id = R.string.speed_sheet_title),
            style = MaterialTheme.typography.titleSmall.copy(
              fontSize = 15.sp,
              fontWeight = FontWeight.Bold
            ),
            color = MaterialTheme.colorScheme.onSurface
          )
          Text(
            text = formatPlaybackSpeed(playbackSpeed),
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            color = accent
          )
        }

        Spacer(modifier = Modifier.height(10.dp))

        val speeds = listOf(0.75f, 0.9f, 1.0f, 1.15f, 1.25f, 1.5f)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          speeds.chunked(3).forEach { rowItems ->
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              rowItems.forEach { speed ->
                val selected = abs(playbackSpeed - speed) < 0.02f
                StudioOptionChip(
                  label = formatPlaybackSpeed(speed),
                  selected = selected,
                  accent = accent,
                  isDark = isDark,
                  testTag = "speed_option_$speed",
                  modifier = Modifier.weight(1f),
                  onClick = { onSetPlaybackSpeed(speed) }
                )
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun StudioOptionChip(
  label: String,
  selected: Boolean,
  accent: Color,
  isDark: Boolean,
  testTag: String,
  modifier: Modifier = Modifier,
  onClick: () -> Unit
) {
  Surface(
    onClick = onClick,
    shape = RoundedCornerShape(16.dp),
    color = when {
      selected -> accent.copy(alpha = if (isDark) 0.24f else 0.14f)
      isDark -> Color.White.copy(alpha = 0.06f)
      else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
    },
    border = BorderStroke(
      1.dp,
      if (selected) accent.copy(alpha = 0.78f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
    ),
    modifier = modifier
      .defaultMinSize(minHeight = 48.dp)
      .testTag(testTag)
  ) {
    Box(
      contentAlignment = Alignment.Center,
      modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp)
    ) {
      Text(
        text = label,
        style = MaterialTheme.typography.bodySmall.copy(
          fontSize = 13.sp,
          fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
        ),
        color = if (selected) accent else MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
    }
  }
}

private fun formatPlaybackSpeed(speed: Float): String {
  return when {
    abs(speed - 1.0f) < 0.02f -> "1x"
    abs(speed - 0.75f) < 0.02f -> "0.75x"
    abs(speed - 0.9f) < 0.02f -> "0.9x"
    abs(speed - 1.15f) < 0.02f -> "1.15x"
    abs(speed - 1.25f) < 0.02f -> "1.25x"
    abs(speed - 1.5f) < 0.02f -> "1.5x"
    else -> String.format("%.2fx", speed)
  }
}

fun formatTime(ms: Long): String {
  val totalSec = (ms / 1000).coerceAtLeast(0)
  val min = totalSec / 60
  val sec = totalSec % 60
  return "$min:${String.format("%02d", sec)}"
}

@Composable
private fun BeatReactiveVisualizerStrip(
  bars: FloatArray,
  isPlaying: Boolean,
  reducedMotion: Boolean,
  accentStart: Color,
  accentEnd: Color,
  modifier: Modifier = Modifier
) {
  val barCount = AudioSpectrumBus.BAR_COUNT
  val animatedLevels = List(barCount) { idx ->
    val target = if (!isPlaying) {
      0.14f
    } else if (reducedMotion) {
      0.16f + (bars.getOrElse(idx) { 0f }.coerceIn(0f, 1f) * 0.48f)
    } else {
      bars.getOrElse(idx) { 0f }.coerceIn(0.10f, 1f)
    }
    animateFloatAsState(
      targetValue = target,
      animationSpec = if (reducedMotion) snap() else tween(durationMillis = 75, easing = FastOutSlowInEasing),
      label = "viz_bar_$idx"
    ).value
  }

  Box(
    modifier = modifier
      .height(34.dp)
      .clip(RoundedCornerShape(14.dp))
      .background(Color.White.copy(alpha = 0.08f))
      .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(14.dp))
      .padding(horizontal = 12.dp, vertical = 6.dp)
  ) {
    Canvas(modifier = Modifier.fillMaxSize()) {
      val count = animatedLevels.size
      if (count <= 0) return@Canvas
      val totalWidth = size.width
      val totalHeight = size.height
      val gap = 4.dp.toPx()
      val barWidth = ((totalWidth - gap * (count - 1)) / count).coerceAtLeast(2.dp.toPx())
      val corner = CornerRadius(barWidth / 2f, barWidth / 2f)
      val barBrush = Brush.horizontalGradient(
        colors = listOf(
          accentStart.copy(alpha = 0.95f),
          Color.White.copy(alpha = 0.92f),
          accentEnd.copy(alpha = 0.95f)
        )
      )

      for (i in 0 until count) {
        val level = animatedLevels[i].coerceIn(0.10f, 1f)
        val barHeight = (totalHeight * level).coerceAtLeast(3.dp.toPx())
        val left = i * (barWidth + gap)
        val top = (totalHeight - barHeight) / 2f
        drawRoundRect(
          brush = barBrush,
          topLeft = Offset(left, top),
          size = Size(barWidth, barHeight),
          cornerRadius = corner,
          alpha = if (isPlaying) 0.92f else 0.42f
        )
      }
    }
  }
}
