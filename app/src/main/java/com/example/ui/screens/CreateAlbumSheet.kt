package com.example.ui.screens

import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.example.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val AurixEaseOut = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)
private val AurixSpringOut = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)

private val DarkGlassBg = Color(0xFF0A0810)
private val DarkGlassSurface = Color(0xFF130F1F)
private val DarkGlassDeepSurface = Color(0xFF0F0C1A)
private val DarkGlassText = Color(0xFFF4F2FB)
private val DarkGlassMuted = Color(0xFF8F8AA6)
private val DarkGlassMagenta = Color(0xFFD946EF)

enum class AlbumCoverPreset(
  val id: String,
  val labelRes: Int,
  val startColor: Color,
  val endColor: Color,
  val glowColor: Color
) {
  Violet(
    id = "violet",
    labelRes = R.string.album_color_violet,
    startColor = Color(0xFF8B5CF6),
    endColor = Color(0xFF3B1B7A),
    glowColor = Color(0xFFC084FC)
  ),
  Ocean(
    id = "ocean",
    labelRes = R.string.album_color_ocean,
    startColor = Color(0xFF0EA5E9),
    endColor = Color(0xFF172554),
    glowColor = Color(0xFF38BDF8)
  ),
  Sunset(
    id = "sunset",
    labelRes = R.string.album_color_sunset,
    startColor = Color(0xFFF97316),
    endColor = Color(0xFF831843),
    glowColor = Color(0xFFFB7185)
  ),
  Mint(
    id = "mint",
    labelRes = R.string.album_color_mint,
    startColor = Color(0xFF10B981),
    endColor = Color(0xFF064E3B),
    glowColor = Color(0xFF34D399)
  ),
  Night(
    id = "night",
    labelRes = R.string.album_color_night,
    startColor = Color(0xFF475569),
    endColor = Color(0xFF090710),
    glowColor = Color(0xFF94A3B8)
  );

  fun resolveStartColor(themeAccent: Color): Color =
    if (this == Violet) themeAccent else startColor

  fun resolveEndColor(themeAccent: Color): Color =
    if (this == Violet) lerp(themeAccent, Color(0xFF090710), 0.65f) else endColor

  fun resolveGlowColor(themeAccent: Color): Color =
    if (this == Violet) lerp(themeAccent, Color.White, 0.32f) else glowColor

  companion object {
    fun fromId(id: String?): AlbumCoverPreset {
      return entries.firstOrNull { it.id == id } ?: Violet
    }
  }
}

object AlbumCoverPresetStore {
  private const val PREFS_NAME = "aurix_album_cover_presets"

  fun savePreset(context: Context, albumId: String, preset: AlbumCoverPreset) {
    runCatching {
      context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putString(albumId, preset.id)
        .apply()
    }
  }

  fun getPresetOrNull(context: Context, albumId: String): AlbumCoverPreset? {
    return runCatching {
      val id = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getString(albumId, null)
      if (id.isNullOrBlank()) null else AlbumCoverPreset.fromId(id)
    }.getOrNull()
  }
}

data class FlyingAlbumCoverPayload(
  val albumId: String,
  val title: String,
  val preset: AlbumCoverPreset,
  val sourceRect: Rect
)

private enum class CreateAlbumSheetStage {
  Idle,
  Submitting,
  SuccessCheck,
  Closing
}

@Composable
private fun rememberEffectiveMotionReduced(reducedMotion: Boolean): Boolean {
  val context = LocalContext.current
  val isInspection = LocalInspectionMode.current
  val scale = remember(context, isInspection) {
    if (isInspection) 1f
    else runCatching {
      Settings.Global.getFloat(
        context.contentResolver,
        Settings.Global.ANIMATOR_DURATION_SCALE,
        1f
      )
    }.getOrDefault(1f)
  }
  return reducedMotion || scale <= 0f
}

@Composable
fun CreateAlbumBottomSheetOverlay(
  visible: Boolean,
  defaultArtist: String = "",
  reducedMotion: Boolean = false,
  hideInlineCoverForFlight: Boolean = false,
  onBackdropProgressChanged: (Float) -> Unit = {},
  onDismiss: () -> Unit,
  onCommitAndFly: (
    title: String,
    artist: String,
    year: String,
    preset: AlbumCoverPreset,
    coverBoundsInRoot: Rect?
  ) -> Unit,
  modifier: Modifier = Modifier
) {
  val isReduced = rememberEffectiveMotionReduced(reducedMotion)
  val density = LocalDensity.current
  val focusManager = LocalFocusManager.current
  val scope = rememberCoroutineScope()

  if (!visible) {
    LaunchedEffect(Unit) {
      onBackdropProgressChanged(0f)
    }
    return
  }

  val darkGlassViolet = MaterialTheme.colorScheme.primary
  val darkGlassLightViolet = lerp(darkGlassViolet, Color.White, 0.32f)
  val darkGlassSecondaryGlow = lerp(darkGlassViolet, DarkGlassMagenta, 0.28f)

  var title by remember { mutableStateOf("") }
  var artist by remember { mutableStateOf(defaultArtist) }
  var year by remember { mutableStateOf("") }
  var selectedPreset by remember { mutableStateOf(AlbumCoverPreset.Violet) }
  var stage by remember { mutableStateOf(CreateAlbumSheetStage.Idle) }
  var showStaggeredItems by remember { mutableStateOf(false) }
  var coverBoundsInRoot by remember { mutableStateOf<Rect?>(null) }
  var lastKeystrokeAtMs by remember { mutableLongStateOf(0L) }
  var isTypingBoosted by remember { mutableStateOf(false) }

  val titleFocusRequester = remember { FocusRequester() }
  val titleShakeX = remember { Animatable(0f) }
  val sheetOffsetFraction = remember { Animatable(1f) } // 1 = hidden at bottom, 0 = resting open
  val dragOffsetY = remember { Animatable(0f) }
  val checkDrawProgress = remember { Animatable(0f) }

  val dismissThresholdPx = with(density) { 125.dp.toPx() }
  val maxSlideDistancePx = with(density) { 640.dp.toPx() }

  val activeGlowColor by animateColorAsState(
    targetValue = selectedPreset.resolveGlowColor(darkGlassViolet),
    animationSpec = if (isReduced) snap() else tween(360, easing = AurixEaseOut),
    label = "sheet_active_glow"
  )

  // Launch entrance spring
  LaunchedEffect(visible, isReduced) {
    if (visible) {
      showStaggeredItems = true
      if (isReduced) {
        sheetOffsetFraction.snapTo(0f)
        onBackdropProgressChanged(1f)
      } else {
        sheetOffsetFraction.snapTo(1f)
        sheetOffsetFraction.animateTo(
          targetValue = 0f,
          animationSpec = tween(durationMillis = 440, easing = AurixSpringOut)
        )
      }
    }
  }

  // Update backdrop progress (1 -> 0 when dragged or closing)
  LaunchedEffect(sheetOffsetFraction.value, dragOffsetY.value, stage) {
    val dragFrac = (dragOffsetY.value / maxSlideDistancePx).coerceIn(0f, 1f)
    val openFactor = (1f - sheetOffsetFraction.value.coerceIn(0f, 1f)) * (1f - dragFrac)
    onBackdropProgressChanged(if (stage == CreateAlbumSheetStage.Closing) 0f else openFactor.coerceIn(0f, 1f))
  }

  // Typing boost timer for spinning vinyl disc
  LaunchedEffect(lastKeystrokeAtMs, isReduced) {
    if (isReduced || lastKeystrokeAtMs == 0L) {
      isTypingBoosted = false
      return@LaunchedEffect
    }
    isTypingBoosted = true
    delay(650L)
    isTypingBoosted = false
  }

  fun dismissWithAnimation() {
    if (stage != CreateAlbumSheetStage.Idle) return
    focusManager.clearFocus()
    scope.launch {
      if (!isReduced) {
        sheetOffsetFraction.animateTo(
          targetValue = 1f,
          animationSpec = tween(durationMillis = 280, easing = AurixEaseOut)
        )
      }
      onBackdropProgressChanged(0f)
      onDismiss()
    }
  }

  fun triggerEmptyTitleShake() {
    scope.launch {
      titleFocusRequester.requestFocus()
      if (isReduced) return@launch
      titleShakeX.snapTo(0f)
      titleShakeX.animateTo(
        targetValue = 0f,
        animationSpec = keyframes {
          durationMillis = 420
          0f at 0
          -18f at 60
          18f at 120
          -14f at 185
          12f at 250
          -6f at 320
          0f at 420
        }
      )
    }
  }

  fun submitAlbumCreation() {
    if (stage != CreateAlbumSheetStage.Idle) return
    val cleanTitle = title.trim()
    if (cleanTitle.isEmpty()) {
      triggerEmptyTitleShake()
      return
    }
    focusManager.clearFocus()
    val cleanArtist = artist.trim()
    val cleanYear = year.trim()

    scope.launch {
      // Step 1: Cancel collapses & Create expands to full width with spinner
      stage = CreateAlbumSheetStage.Submitting
      delay(if (isReduced) 60L else 420L)

      // Step 2: Spinner becomes a self-drawing green checkmark
      stage = CreateAlbumSheetStage.SuccessCheck
      if (isReduced) {
        checkDrawProgress.snapTo(1f)
        delay(80L)
      } else {
        checkDrawProgress.snapTo(0f)
        checkDrawProgress.animateTo(
          targetValue = 1f,
          animationSpec = tween(durationMillis = 340, easing = AurixEaseOut)
        )
        delay(200L)
      }

      // Step 3 & 4: Commit album, trigger curved cover flight, slide sheet down, unscale Library
      stage = CreateAlbumSheetStage.Closing
      onCommitAndFly(cleanTitle, cleanArtist, cleanYear, selectedPreset, coverBoundsInRoot)
      onBackdropProgressChanged(0f)
      if (!isReduced) {
        sheetOffsetFraction.animateTo(
          targetValue = 1f,
          animationSpec = tween(durationMillis = 320, easing = AurixEaseOut)
        )
      }
      onDismiss()
    }
  }

  BackHandler(enabled = visible && stage == CreateAlbumSheetStage.Idle) {
    dismissWithAnimation()
  }

  val sheetShape = RoundedCornerShape(topStart = 34.dp, topEnd = 34.dp)
  val sheetBorderBrush = Brush.linearGradient(
    colors = listOf(
      Color.White.copy(alpha = 0.16f),
      darkGlassViolet.copy(alpha = 0.34f),
      activeGlowColor.copy(alpha = 0.20f),
      Color.White.copy(alpha = 0.08f)
    )
  )
  val totalTranslationY = sheetOffsetFraction.value * maxSlideDistancePx + dragOffsetY.value.coerceAtLeast(-28f)
  val dragFadeAlpha = (1f - (dragOffsetY.value / (maxSlideDistancePx * 0.9f)).coerceIn(0f, 0.65f)) *
    (1f - sheetOffsetFraction.value * 0.45f)

  Box(
    modifier = modifier
      .fillMaxSize()
      .imePadding(),
    contentAlignment = Alignment.BottomCenter
  ) {
    // Scrim click area above sheet
    Box(
      modifier = Modifier
        .fillMaxSize()
        .clickable(
          interactionSource = remember { MutableInteractionSource() },
          indication = null,
          enabled = stage == CreateAlbumSheetStage.Idle
        ) {
          dismissWithAnimation()
        }
    )

    // Sheet container
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .graphicsLayer {
          translationY = totalTranslationY
          alpha = dragFadeAlpha.coerceIn(0f, 1f)
        }
        .shadow(
          elevation = 26.dp,
          shape = sheetShape,
          ambientColor = darkGlassViolet.copy(alpha = 0.36f),
          spotColor = darkGlassSecondaryGlow.copy(alpha = 0.30f)
        )
        .clip(sheetShape)
        .background(DarkGlassBg)
        .drawBehind {
          // User accent & reactive preset radial glows
          drawCircle(
            brush = Brush.radialGradient(
              colors = listOf(darkGlassViolet.copy(alpha = 0.25f), Color.Transparent),
              center = Offset(size.width * 0.16f, size.height * 0.12f),
              radius = size.maxDimension * 0.65f
            ),
            radius = size.maxDimension * 0.65f,
            center = Offset(size.width * 0.16f, size.height * 0.12f)
          )
          drawCircle(
            brush = Brush.radialGradient(
              colors = listOf(activeGlowColor.copy(alpha = 0.14f), Color.Transparent),
              center = Offset(size.width * 0.85f, size.height * 0.30f),
              radius = size.maxDimension * 0.48f
            ),
            radius = size.maxDimension * 0.48f,
            center = Offset(size.width * 0.85f, size.height * 0.30f)
          )
          drawCircle(
            brush = Brush.radialGradient(
              colors = listOf(darkGlassSecondaryGlow.copy(alpha = 0.15f), Color.Transparent),
              center = Offset(size.width * 0.88f, size.height * 0.78f),
              radius = size.maxDimension * 0.60f
            ),
            radius = size.maxDimension * 0.60f,
            center = Offset(size.width * 0.88f, size.height * 0.78f)
          )
          // Accent glow line along top edge
          drawRect(
            brush = Brush.horizontalGradient(
              colors = listOf(
                Color.Transparent,
                darkGlassViolet.copy(alpha = 0.88f),
                darkGlassLightViolet,
                activeGlowColor,
                darkGlassViolet.copy(alpha = 0.88f),
                Color.Transparent
              )
            ),
            topLeft = Offset(0f, 0f),
            size = Size(size.width, 2.5.dp.toPx())
          )
          drawRect(
            brush = Brush.verticalGradient(
              colors = listOf(darkGlassLightViolet.copy(alpha = 0.22f), Color.Transparent),
              startY = 0f,
              endY = 30.dp.toPx()
            ),
            topLeft = Offset(0f, 0f),
            size = Size(size.width, 30.dp.toPx())
          )
        }
        .border(
          width = 1.dp,
          brush = sheetBorderBrush,
          shape = sheetShape
        )
        .clickable(
          interactionSource = remember { MutableInteractionSource() },
          indication = null
        ) {
          // Consume clicks inside sheet so scrim doesn't dismiss
        }
        .navigationBarsPadding()
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(11.dp)
      ) {
        // Drag handle area with spring-back vertical drag gesture
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .pointerInput(stage) {
              if (stage != CreateAlbumSheetStage.Idle) return@pointerInput
              detectVerticalDragGestures(
                onVerticalDrag = { change, dragAmount ->
                  change.consume()
                  scope.launch {
                    val next = (dragOffsetY.value + dragAmount).coerceAtLeast(-26f)
                    dragOffsetY.snapTo(next)
                  }
                },
                onDragEnd = {
                  scope.launch {
                    if (dragOffsetY.value >= dismissThresholdPx) {
                      if (!isReduced) {
                        sheetOffsetFraction.animateTo(
                          targetValue = 1f,
                          animationSpec = tween(durationMillis = 250, easing = AurixEaseOut)
                        )
                      }
                      onBackdropProgressChanged(0f)
                      onDismiss()
                    } else {
                      dragOffsetY.animateTo(
                        targetValue = 0f,
                        animationSpec = if (isReduced) snap() else spring(
                          dampingRatio = 0.62f,
                          stiffness = Spring.StiffnessMedium
                        )
                      )
                    }
                  }
                },
                onDragCancel = {
                  scope.launch {
                    dragOffsetY.animateTo(
                      targetValue = 0f,
                      animationSpec = if (isReduced) snap() else spring(
                        dampingRatio = 0.68f,
                        stiffness = Spring.StiffnessMedium
                      )
                    )
                  }
                }
              )
            }
            .padding(top = 2.dp, bottom = 2.dp),
          contentAlignment = Alignment.Center
        ) {
          Box(
            modifier = Modifier
              .width(46.dp)
              .height(4.5.dp)
              .clip(CircleShape)
              .background(
                Brush.horizontalGradient(
                  colors = listOf(
                    darkGlassViolet.copy(alpha = 0.45f),
                    darkGlassLightViolet.copy(alpha = 0.95f),
                    darkGlassViolet.copy(alpha = 0.45f)
                  )
                )
              )
          )
        }

        // Scrollable body (Header, Live Preview, Color Chips, Form Fields)
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .weight(1f, fill = false)
            .verticalScroll(rememberScrollState()),
          verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          // Item 1: Header ("Create album" + "Name it, pick a cover, add songs later") with glass icon badge & close button
          StaggeredSheetItem(
            visible = showStaggeredItems,
            delayMillis = 0,
            reducedMotion = isReduced
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
              Box(
                modifier = Modifier
                  .size(44.dp)
                  .clip(RoundedCornerShape(14.dp))
                  .background(
                    Brush.linearGradient(
                      colors = listOf(
                        darkGlassViolet.copy(alpha = 0.28f),
                        activeGlowColor.copy(alpha = 0.14f)
                      )
                    )
                  )
                  .border(
                    1.dp,
                    darkGlassLightViolet.copy(alpha = 0.34f),
                    RoundedCornerShape(14.dp)
                  ),
                contentAlignment = Alignment.Center
              ) {
                Icon(
                  imageVector = Icons.Rounded.Album,
                  contentDescription = null,
                  tint = darkGlassLightViolet,
                  modifier = Modifier.size(22.dp)
                )
              }

              Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
              ) {
                Text(
                  text = stringResource(id = R.string.album_create_title),
                  style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    lineHeight = 28.sp
                  ),
                  color = DarkGlassText
                )
                Text(
                  text = stringResource(id = R.string.album_create_subtitle),
                  style = MaterialTheme.typography.bodySmall.copy(lineHeight = 17.sp),
                  color = DarkGlassMuted
                )
              }

              Surface(
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.06f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
                modifier = Modifier
                  .size(36.dp)
                  .clip(CircleShape)
                  .clickable(enabled = stage == CreateAlbumSheetStage.Idle) {
                    dismissWithAnimation()
                  }
              ) {
                Box(contentAlignment = Alignment.Center) {
                  Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = stringResource(id = R.string.action_cancel),
                    tint = DarkGlassMuted,
                    modifier = Modifier.size(17.dp)
                  )
                }
              }
            }
          }

          // Item 2: Live Preview (108.dp cover + spinning disc + shine + first-letter pop + equalizer pill)
          StaggeredSheetItem(
            visible = showStaggeredItems,
            delayMillis = 55,
            reducedMotion = isReduced
          ) {
            CreateAlbumLivePreviewCard(
              title = title,
              artist = artist,
              year = year,
              preset = selectedPreset,
              isTypingBoosted = isTypingBoosted,
              reducedMotion = isReduced,
              hideCoverForFlight = hideInlineCoverForFlight,
              onCoverBoundsChanged = { coverBoundsInRoot = it }
            )
          }

          // Item 3: Color Chips (Violet, Ocean, Sunset, Mint, Night) with sliding highlight inside glass rail
          StaggeredSheetItem(
            visible = showStaggeredItems,
            delayMillis = 110,
            reducedMotion = isReduced
          ) {
            AlbumColorChipRow(
              selectedPreset = selectedPreset,
              reducedMotion = isReduced,
              enabled = stage == CreateAlbumSheetStage.Idle,
              onSelect = { selectedPreset = it }
            )
          }

          // Item 4: Album Title field (shakes if Create tapped when empty)
          StaggeredSheetItem(
            visible = showStaggeredItems,
            delayMillis = 165,
            reducedMotion = isReduced
          ) {
            DarkGlassFloatingField(
              value = title,
              onValueChange = {
                title = it
                lastKeystrokeAtMs = System.currentTimeMillis()
              },
              label = stringResource(id = R.string.album_create_name_label),
              leadingIcon = Icons.Rounded.Album,
              reducedMotion = isReduced,
              enabled = stage == CreateAlbumSheetStage.Idle,
              imeAction = ImeAction.Next,
              keyboardType = KeyboardType.Text,
              focusRequester = titleFocusRequester,
              modifier = Modifier
                .offset { IntOffset(titleShakeX.value.roundToInt(), 0) }
                .testTag("create_album_title_input")
            )
          }

          // Item 5: Artist (optional) field
          StaggeredSheetItem(
            visible = showStaggeredItems,
            delayMillis = 215,
            reducedMotion = isReduced
          ) {
            DarkGlassFloatingField(
              value = artist,
              onValueChange = {
                artist = it
                lastKeystrokeAtMs = System.currentTimeMillis()
              },
              label = stringResource(id = R.string.album_create_artist_label),
              leadingIcon = Icons.Rounded.Mic,
              reducedMotion = isReduced,
              enabled = stage == CreateAlbumSheetStage.Idle,
              imeAction = ImeAction.Next,
              keyboardType = KeyboardType.Text,
              modifier = Modifier.testTag("create_album_artist_input")
            )
          }

          // Item 6: Year field (digits only, max 4)
          StaggeredSheetItem(
            visible = showStaggeredItems,
            delayMillis = 265,
            reducedMotion = isReduced
          ) {
            DarkGlassFloatingField(
              value = year,
              onValueChange = { raw ->
                val digits = raw.filter { it.isDigit() }.take(4)
                year = digits
                lastKeystrokeAtMs = System.currentTimeMillis()
              },
              label = stringResource(id = R.string.album_create_year_short),
              leadingIcon = Icons.Rounded.CalendarToday,
              reducedMotion = isReduced,
              enabled = stage == CreateAlbumSheetStage.Idle,
              imeAction = ImeAction.Done,
              keyboardType = KeyboardType.Number,
              maxLengthHint = 4,
              onImeDone = { submitAlbumCreation() },
              modifier = Modifier.testTag("create_album_year_input")
            )
          }
        }

        // Item 7: Pinned bottom Action buttons (Cancel collapses, Create album expands + spinner + self-drawing check)
        StaggeredSheetItem(
          visible = showStaggeredItems,
          delayMillis = 315,
          reducedMotion = isReduced
        ) {
          CreateAlbumActionButtonRow(
            canCreate = title.trim().isNotEmpty(),
            stage = stage,
            checkDrawProgress = checkDrawProgress.value,
            reducedMotion = isReduced,
            onCancel = { dismissWithAnimation() },
            onCreateClick = { submitAlbumCreation() }
          )
        }

        Spacer(modifier = Modifier.height(6.dp))
      }
    }
  }
}

@Composable
private fun StaggeredSheetItem(
  visible: Boolean,
  delayMillis: Int,
  reducedMotion: Boolean,
  content: @Composable () -> Unit
) {
  val density = LocalDensity.current
  val progress = remember { Animatable(if (reducedMotion) 1f else 0f) }

  LaunchedEffect(visible, reducedMotion) {
    if (!visible) return@LaunchedEffect
    if (reducedMotion) {
      progress.snapTo(1f)
    } else {
      progress.snapTo(0f)
      delay(delayMillis.toLong())
      progress.animateTo(
        targetValue = 1f,
        animationSpec = tween(durationMillis = 420, easing = AurixEaseOut)
      )
    }
  }

  val p = progress.value
  val slideOffsetPx = with(density) { 20.dp.toPx() } * (1f - p)
  val blurDp = if (!reducedMotion && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    ((1f - p) * 6f).coerceIn(0f, 6f).dp
  } else {
    0.dp
  }

  Box(
    modifier = Modifier
      .fillMaxWidth()
      .then(if (blurDp > 0.1.dp) Modifier.blur(blurDp) else Modifier)
      .graphicsLayer {
        alpha = p
        translationY = slideOffsetPx
      }
  ) {
    content()
  }
}

@Composable
private fun CreateAlbumLivePreviewCard(
  title: String,
  artist: String,
  year: String,
  preset: AlbumCoverPreset,
  isTypingBoosted: Boolean,
  reducedMotion: Boolean,
  hideCoverForFlight: Boolean,
  onCoverBoundsChanged: (Rect) -> Unit
) {
  val themeAccent = MaterialTheme.colorScheme.primary
  val untitledText = stringResource(id = R.string.album_create_untitled_preview)
  val unknownArtistText = stringResource(id = R.string.album_create_unknown_artist_preview)
  val defaultYearText = stringResource(id = R.string.album_create_default_year)

  val displayTitle = title.trim().ifBlank { untitledText }
  val displayArtist = artist.trim().ifBlank { unknownArtistText }
  val displayYear = year.trim().ifBlank { defaultYearText }
  val firstLetter = title.trim().firstOrNull()?.uppercaseChar()?.toString().orEmpty()

  val animatedGlow by animateColorAsState(
    targetValue = preset.resolveGlowColor(themeAccent),
    animationSpec = if (reducedMotion) snap() else tween(360, easing = AurixEaseOut),
    label = "preview_card_glow"
  )
  val animatedStart by animateColorAsState(
    targetValue = preset.resolveStartColor(themeAccent),
    animationSpec = if (reducedMotion) snap() else tween(360, easing = AurixEaseOut),
    label = "preview_card_start"
  )

  val cardShape = RoundedCornerShape(26.dp)
  val borderBrush = Brush.linearGradient(
    colors = listOf(
      animatedGlow.copy(alpha = 0.34f),
      Color.White.copy(alpha = 0.10f),
      animatedStart.copy(alpha = 0.18f),
      Color.White.copy(alpha = 0.08f)
    )
  )

  Surface(
    shape = cardShape,
    color = DarkGlassSurface.copy(alpha = 0.86f),
    border = BorderStroke(1.dp, borderBrush),
    modifier = Modifier
      .fillMaxWidth()
      .shadow(
        elevation = 14.dp,
        shape = cardShape,
        ambientColor = animatedGlow.copy(alpha = 0.22f),
        spotColor = animatedStart.copy(alpha = 0.20f)
      )
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .drawBehind {
          drawCircle(
            brush = Brush.radialGradient(
              colors = listOf(animatedGlow.copy(alpha = 0.18f), Color.Transparent),
              center = Offset(72.dp.toPx(), size.height * 0.5f),
              radius = size.maxDimension * 0.55f
            ),
            radius = size.maxDimension * 0.55f,
            center = Offset(72.dp.toPx(), size.height * 0.5f)
          )
        }
        .padding(14.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
      AlbumCoverArtBox(
        preset = preset,
        firstLetter = firstLetter,
        isTypingBoosted = isTypingBoosted,
        reducedMotion = reducedMotion,
        showEditBadge = true,
        modifier = Modifier
          .size(108.dp)
          .alpha(if (hideCoverForFlight) 0f else 1f)
          .onGloballyPositioned { coords ->
            onCoverBoundsChanged(coords.boundsInRoot())
          }
      )

      Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(7.dp)
      ) {
        AnimatedContent(
          targetState = displayTitle,
          transitionSpec = {
            if (reducedMotion) {
              fadeIn(snap()) togetherWith fadeOut(snap())
            } else {
              fadeIn(tween(220, easing = AurixEaseOut)) togetherWith
                fadeOut(tween(160, easing = AurixEaseOut))
            }
          },
          label = "create_album_preview_title"
        ) { animatedTitle ->
          Text(
            text = animatedTitle,
            style = MaterialTheme.typography.titleMedium.copy(
              fontWeight = FontWeight.Bold,
              fontSize = 18.sp,
              lineHeight = 23.sp
            ),
            color = if (title.trim().isBlank()) DarkGlassText.copy(alpha = 0.72f) else DarkGlassText,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
          )
        }

        Text(
          text = "$displayArtist · $displayYear",
          style = MaterialTheme.typography.bodySmall.copy(lineHeight = 17.sp),
          color = DarkGlassMuted,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )

        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          ZeroSongsEqualizerPill(reducedMotion = reducedMotion)

          Surface(
            shape = RoundedCornerShape(999.dp),
            color = animatedGlow.copy(alpha = 0.12f),
            border = BorderStroke(1.dp, animatedGlow.copy(alpha = 0.28f))
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
              Box(
                modifier = Modifier
                  .size(7.dp)
                  .clip(CircleShape)
                  .background(
                    Brush.linearGradient(colors = listOf(animatedGlow, animatedStart))
                  )
              )
              Text(
                text = stringResource(id = preset.labelRes),
                style = MaterialTheme.typography.labelSmall.copy(
                  fontWeight = FontWeight.SemiBold,
                  fontSize = 10.sp
                ),
                color = lerp(animatedGlow, Color.White, 0.25f),
                maxLines = 1
              )
            }
          }
        }
      }
    }
  }
}

@Composable
fun AlbumCoverArtBox(
  preset: AlbumCoverPreset,
  firstLetter: String,
  isTypingBoosted: Boolean = false,
  reducedMotion: Boolean = false,
  showEditBadge: Boolean = false,
  modifier: Modifier = Modifier
) {
  val themeAccent = MaterialTheme.colorScheme.primary
  val darkGlassLightViolet = lerp(themeAccent, Color.White, 0.32f)
  val startColor by animateColorAsState(
    targetValue = preset.resolveStartColor(themeAccent),
    animationSpec = if (reducedMotion) snap() else tween(360, easing = AurixEaseOut),
    label = "cover_start_color"
  )
  val endColor by animateColorAsState(
    targetValue = preset.resolveEndColor(themeAccent),
    animationSpec = if (reducedMotion) snap() else tween(360, easing = AurixEaseOut),
    label = "cover_end_color"
  )
  val glowColor by animateColorAsState(
    targetValue = preset.resolveGlowColor(themeAccent),
    animationSpec = if (reducedMotion) snap() else tween(360, easing = AurixEaseOut),
    label = "cover_glow_color"
  )

  // Continuous vinyl rotation (accelerates smoothly while typing)
  var discAngle by remember { mutableFloatStateOf(0f) }
  val typingWeight by animateFloatAsState(
    targetValue = if (isTypingBoosted) 1f else 0f,
    animationSpec = if (reducedMotion) snap() else tween(260, easing = AurixEaseOut),
    label = "disc_typing_boost"
  )

  LaunchedEffect(reducedMotion) {
    if (reducedMotion) return@LaunchedEffect
    var lastNanos = withFrameNanos { it }
    while (true) {
      withFrameNanos { now ->
        val dt = ((now - lastNanos) / 1_000_000_000f).coerceIn(0f, 0.05f)
        lastNanos = now
        val degPerSec = lerp(34f, 235f, typingWeight)
        discAngle = (discAngle + dt * degPerSec) % 360f
      }
    }
  }

  // Diagonal shine sweep
  val shinePhase = if (reducedMotion) {
    -1f
  } else {
    val infinite = rememberInfiniteTransition(label = "cover_shine")
    val anim by infinite.animateFloat(
      initialValue = -0.6f,
      targetValue = 1.6f,
      animationSpec = infiniteRepeatable(
        animation = tween(durationMillis = 3200, easing = FastOutSlowInEasing),
        repeatMode = RepeatMode.Restart
      ),
      label = "cover_shine_pos"
    )
    anim
  }

  // First-letter pop animation on cover when initial letter changes
  val letterScale = remember { Animatable(1f) }
  val letterRotation = remember { Animatable(0f) }
  val letterBlur = remember { Animatable(0f) }
  val letterAlpha = remember { Animatable(if (firstLetter.isNotEmpty()) 1f else 0f) }

  LaunchedEffect(firstLetter, reducedMotion) {
    if (firstLetter.isEmpty()) {
      letterAlpha.snapTo(0f)
      return@LaunchedEffect
    }
    if (reducedMotion) {
      letterScale.snapTo(1f)
      letterRotation.snapTo(0f)
      letterBlur.snapTo(0f)
      letterAlpha.snapTo(1f)
    } else {
      letterScale.snapTo(0.42f)
      letterRotation.snapTo(-18f)
      letterBlur.snapTo(8f)
      letterAlpha.snapTo(0f)
      launch { letterAlpha.animateTo(1f, tween(220, easing = AurixEaseOut)) }
      launch { letterScale.animateTo(1f, tween(380, easing = AurixSpringOut)) }
      launch { letterRotation.animateTo(0f, tween(360, easing = AurixEaseOut)) }
      launch { letterBlur.animateTo(0f, tween(280, easing = AurixEaseOut)) }
    }
  }

  val coverShape = RoundedCornerShape(24.dp)

  Box(
    modifier = modifier
      .shadow(
        elevation = 16.dp,
        shape = coverShape,
        ambientColor = glowColor.copy(alpha = 0.50f),
        spotColor = glowColor.copy(alpha = 0.45f)
      )
      .clip(coverShape)
      .background(Brush.linearGradient(colors = listOf(startColor, endColor)))
      .border(
        width = 1.dp,
        brush = Brush.linearGradient(
          colors = listOf(
            Color.White.copy(alpha = 0.28f),
            glowColor.copy(alpha = 0.30f),
            Color.White.copy(alpha = 0.12f)
          )
        ),
        shape = coverShape
      ),
    contentAlignment = Alignment.Center
  ) {
    Canvas(modifier = Modifier.fillMaxSize()) {
      val c = center
      val discRadius = size.minDimension * 0.38f

      // Ambient inner glow (brightens slightly while typing)
      drawCircle(
        brush = Brush.radialGradient(
          colors = listOf(
            glowColor.copy(alpha = 0.34f + typingWeight * 0.16f),
            Color.Transparent
          ),
          center = c,
          radius = size.minDimension * 0.64f
        ),
        radius = size.minDimension * 0.64f,
        center = c
      )

      // Spinning vinyl disc
      rotate(degrees = discAngle, pivot = c) {
        // Outer vinyl body
        drawCircle(
          color = Color(0xFF0A0810).copy(alpha = 0.90f),
          radius = discRadius,
          center = c
        )
        // Outer rim highlight
        drawCircle(
          color = glowColor.copy(alpha = 0.25f + typingWeight * 0.20f),
          radius = discRadius,
          center = c,
          style = Stroke(width = 1.1.dp.toPx())
        )
        // Concentric vinyl micro-grooves
        drawCircle(
          color = Color.White.copy(alpha = 0.11f),
          radius = discRadius * 0.86f,
          center = c,
          style = Stroke(width = 1.1.dp.toPx())
        )
        drawCircle(
          color = Color.White.copy(alpha = 0.07f),
          radius = discRadius * 0.74f,
          center = c,
          style = Stroke(width = 0.9.dp.toPx())
        )
        drawCircle(
          color = Color.White.copy(alpha = 0.09f),
          radius = discRadius * 0.61f,
          center = c,
          style = Stroke(width = 1.dp.toPx())
        )
        // Specular wedge highlights showing spin speed
        drawArc(
          color = glowColor.copy(alpha = 0.60f),
          startAngle = -40f,
          sweepAngle = 54f,
          useCenter = false,
          topLeft = Offset(c.x - discRadius * 0.82f, c.y - discRadius * 0.82f),
          size = Size(discRadius * 1.64f, discRadius * 1.64f),
          style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round)
        )
        drawArc(
          color = Color.White.copy(alpha = 0.30f),
          startAngle = 140f,
          sweepAngle = 48f,
          useCenter = false,
          topLeft = Offset(c.x - discRadius * 0.82f, c.y - discRadius * 0.82f),
          size = Size(discRadius * 1.64f, discRadius * 1.64f),
          style = Stroke(width = 1.7.dp.toPx(), cap = StrokeCap.Round)
        )
        // Center vinyl label
        drawCircle(
          brush = Brush.linearGradient(colors = listOf(glowColor, startColor)),
          radius = discRadius * 0.36f,
          center = c
        )
        drawCircle(
          color = Color.White.copy(alpha = 0.25f),
          radius = discRadius * 0.36f,
          center = c,
          style = Stroke(width = 0.8.dp.toPx())
        )
        drawCircle(
          color = DarkGlassBg,
          radius = discRadius * 0.11f,
          center = c
        )
      }

      // Diagonal shine sweep
      if (shinePhase >= -0.5f) {
        val xStart = size.width * shinePhase
        drawRect(
          brush = Brush.linearGradient(
            colors = listOf(
              Color.Transparent,
              Color.White.copy(alpha = 0.24f),
              Color.Transparent
            ),
            start = Offset(xStart - size.width * 0.35f, 0f),
            end = Offset(xStart + size.width * 0.15f, size.height)
          )
        )
      }
    }

    // First letter pop onto cover
    if (firstLetter.isNotEmpty()) {
      Box(
        modifier = Modifier
          .size(46.dp)
          .then(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !reducedMotion && letterBlur.value > 0.2f) {
              Modifier.blur(letterBlur.value.dp)
            } else {
              Modifier
            }
          )
          .graphicsLayer {
            scaleX = letterScale.value
            scaleY = letterScale.value
            rotationZ = letterRotation.value
            alpha = letterAlpha.value
          }
          .clip(CircleShape)
          .background(DarkGlassBg.copy(alpha = 0.76f))
          .border(
            width = 1.2.dp,
            brush = Brush.linearGradient(colors = listOf(Color.White.copy(alpha = 0.5f), glowColor)),
            shape = CircleShape
          ),
        contentAlignment = Alignment.Center
      ) {
        Text(
          text = firstLetter,
          style = MaterialTheme.typography.titleLarge.copy(
            fontWeight = FontWeight.ExtraBold,
            fontSize = 20.sp
          ),
          color = DarkGlassText
        )
      }
    }

    if (showEditBadge) {
      Surface(
        shape = CircleShape,
        color = DarkGlassBg.copy(alpha = 0.88f),
        border = BorderStroke(
          1.dp,
          Brush.linearGradient(colors = listOf(darkGlassLightViolet.copy(alpha = 0.55f), Color.White.copy(alpha = 0.16f)))
        ),
        modifier = Modifier
          .align(Alignment.BottomEnd)
          .padding(7.dp)
          .size(25.dp)
      ) {
        Box(contentAlignment = Alignment.Center) {
          Icon(
            imageVector = Icons.Rounded.Edit,
            contentDescription = null,
            tint = darkGlassLightViolet,
            modifier = Modifier.size(13.dp)
          )
        }
      }
    }
  }
}

@Composable
private fun ZeroSongsEqualizerPill(reducedMotion: Boolean) {
  val darkGlassViolet = MaterialTheme.colorScheme.primary
  val darkGlassLightViolet = lerp(darkGlassViolet, Color.White, 0.32f)
  val barScales = if (reducedMotion) {
    listOf(0.55f, 0.90f, 0.45f, 0.75f)
  } else {
    val infinite = rememberInfiniteTransition(label = "zero_songs_eq")
    val b0 by infinite.animateFloat(
      initialValue = 0.30f,
      targetValue = 1.0f,
      animationSpec = infiniteRepeatable(tween(420, easing = FastOutSlowInEasing), RepeatMode.Reverse),
      label = "eq0"
    )
    val b1 by infinite.animateFloat(
      initialValue = 0.95f,
      targetValue = 0.28f,
      animationSpec = infiniteRepeatable(tween(520, easing = FastOutSlowInEasing), RepeatMode.Reverse),
      label = "eq1"
    )
    val b2 by infinite.animateFloat(
      initialValue = 0.40f,
      targetValue = 0.92f,
      animationSpec = infiniteRepeatable(tween(370, easing = FastOutSlowInEasing), RepeatMode.Reverse),
      label = "eq2"
    )
    val b3 by infinite.animateFloat(
      initialValue = 0.85f,
      targetValue = 0.35f,
      animationSpec = infiniteRepeatable(tween(480, easing = FastOutSlowInEasing), RepeatMode.Reverse),
      label = "eq3"
    )
    listOf(b0, b1, b2, b3)
  }

  Surface(
    shape = RoundedCornerShape(999.dp),
    color = darkGlassViolet.copy(alpha = 0.14f),
    border = BorderStroke(1.dp, darkGlassLightViolet.copy(alpha = 0.26f))
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
      Canvas(modifier = Modifier.size(width = 14.dp, height = 11.dp)) {
        val barWidth = 2.2.dp.toPx()
        val gap = (size.width - barWidth * 4f) / 3f
        barScales.forEachIndexed { idx, frac ->
          val barH = (size.height * frac.coerceIn(0.22f, 1f))
          val x = idx * (barWidth + gap)
          val top = size.height - barH
          drawRoundRect(
            brush = Brush.verticalGradient(listOf(darkGlassLightViolet, darkGlassViolet)),
            topLeft = Offset(x, top),
            size = Size(barWidth, barH),
            cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
          )
        }
      }
      Text(
        text = stringResource(id = R.string.album_create_songs_zero),
        style = MaterialTheme.typography.labelSmall.copy(
          fontWeight = FontWeight.Bold,
          letterSpacing = 0.8.sp,
          fontSize = 10.sp
        ),
        color = darkGlassLightViolet,
        maxLines = 1
      )
    }
  }
}

@Composable
private fun AlbumColorChipRow(
  selectedPreset: AlbumCoverPreset,
  reducedMotion: Boolean,
  enabled: Boolean,
  onSelect: (AlbumCoverPreset) -> Unit
) {
  val themeAccent = MaterialTheme.colorScheme.primary
  val density = LocalDensity.current
  val chipRects = remember { mutableStateMapOf<AlbumCoverPreset, Rect>() }
  val selectedRect = chipRects[selectedPreset]

  val pillX by animateDpAsState(
    targetValue = with(density) { (selectedRect?.left ?: 0f).toDp() },
    animationSpec = if (reducedMotion) snap() else tween(300, easing = AurixSpringOut),
    label = "chip_highlight_x"
  )
  val pillW by animateDpAsState(
    targetValue = with(density) { (selectedRect?.width ?: 0f).toDp() },
    animationSpec = if (reducedMotion) snap() else tween(300, easing = AurixEaseOut),
    label = "chip_highlight_w"
  )
  val pillH by animateDpAsState(
    targetValue = with(density) { (selectedRect?.height ?: 0f).toDp() },
    animationSpec = snap(),
    label = "chip_highlight_h"
  )

  val chipGlow by animateColorAsState(
    targetValue = selectedPreset.resolveGlowColor(themeAccent),
    animationSpec = if (reducedMotion) snap() else tween(300, easing = AurixEaseOut),
    label = "chip_glow_color"
  )

  Surface(
    shape = RoundedCornerShape(20.dp),
    color = DarkGlassDeepSurface.copy(alpha = 0.88f),
    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
    modifier = Modifier.fillMaxWidth()
  ) {
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 5.dp, vertical = 5.dp)
    ) {
      // Sliding highlight background
      if (selectedRect != null && pillW > 0.dp && pillH > 0.dp) {
        Box(
          modifier = Modifier
            .offset(x = pillX)
            .size(width = pillW, height = pillH)
            .shadow(
              elevation = 8.dp,
              shape = RoundedCornerShape(999.dp),
              ambientColor = chipGlow.copy(alpha = 0.35f),
              spotColor = chipGlow.copy(alpha = 0.30f)
            )
            .clip(RoundedCornerShape(999.dp))
            .background(
              Brush.horizontalGradient(
                colors = listOf(
                  chipGlow.copy(alpha = 0.24f),
                  selectedPreset.resolveStartColor(themeAccent).copy(alpha = 0.18f)
                )
              )
            )
            .border(1.dp, chipGlow.copy(alpha = 0.55f), RoundedCornerShape(999.dp))
        )
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        AlbumCoverPreset.entries.forEach { preset ->
          val isSelected = preset == selectedPreset
          Row(
            modifier = Modifier
              .weight(1f)
              .onGloballyPositioned { coords ->
                chipRects[preset] = coords.boundsInParent()
              }
              .clip(RoundedCornerShape(999.dp))
              .background(if (isSelected) Color.Transparent else Color.White.copy(alpha = 0.03f))
              .border(
                width = 1.dp,
                color = if (isSelected) Color.Transparent else Color.White.copy(alpha = 0.06f),
                shape = RoundedCornerShape(999.dp)
              )
              .clickable(enabled = enabled) { onSelect(preset) }
              .padding(horizontal = 4.dp, vertical = 9.dp)
              .testTag("create_album_color_${preset.id}"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
          ) {
            Box(
              modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(
                  Brush.linearGradient(
                    colors = listOf(
                      preset.resolveGlowColor(themeAccent),
                      preset.resolveEndColor(themeAccent)
                    )
                  )
                )
                .border(
                  width = if (isSelected) 1.1.dp else 0.75.dp,
                  color = if (isSelected) Color.White.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.40f),
                  shape = CircleShape
                )
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
              text = stringResource(id = preset.labelRes),
              style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                fontSize = 11.5.sp
              ),
              color = if (isSelected) DarkGlassText else DarkGlassMuted,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
          }
        }
      }
    }
  }
}

@Composable
private fun DarkGlassFloatingField(
  value: String,
  onValueChange: (String) -> Unit,
  label: String,
  leadingIcon: ImageVector,
  reducedMotion: Boolean,
  enabled: Boolean,
  imeAction: ImeAction,
  keyboardType: KeyboardType,
  maxLengthHint: Int? = null,
  focusRequester: FocusRequester = remember { FocusRequester() },
  onImeDone: () -> Unit = {},
  modifier: Modifier = Modifier
) {
  val darkGlassViolet = MaterialTheme.colorScheme.primary
  val darkGlassLightViolet = lerp(darkGlassViolet, Color.White, 0.32f)
  var isFocused by remember { mutableStateOf(false) }
  val isFloating = isFocused || value.isNotEmpty()

  val labelFloat by animateFloatAsState(
    targetValue = if (isFloating) 1f else 0f,
    animationSpec = if (reducedMotion) snap() else tween(220, easing = AurixEaseOut),
    label = "floating_label_progress"
  )
  val borderColor by animateColorAsState(
    targetValue = if (isFocused) darkGlassViolet.copy(alpha = 0.88f) else Color.White.copy(alpha = 0.08f),
    animationSpec = if (reducedMotion) snap() else tween(220, easing = AurixEaseOut),
    label = "field_border_color"
  )
  val glowAlpha by animateFloatAsState(
    targetValue = if (isFocused) 0.25f else 0f,
    animationSpec = if (reducedMotion) snap() else tween(240, easing = AurixEaseOut),
    label = "field_focus_glow"
  )

  val shape = RoundedCornerShape(18.dp)

  Box(
    modifier = modifier
      .fillMaxWidth()
      .height(60.dp)
      .shadow(
        elevation = if (isFocused) 12.dp else 0.dp,
        shape = shape,
        ambientColor = darkGlassViolet.copy(alpha = glowAlpha),
        spotColor = darkGlassLightViolet.copy(alpha = glowAlpha)
      )
      .clip(shape)
      .background(DarkGlassSurface.copy(alpha = 0.90f))
      .drawBehind {
        if (glowAlpha > 0.01f) {
          drawRect(
            brush = Brush.radialGradient(
              colors = listOf(darkGlassViolet.copy(alpha = glowAlpha), Color.Transparent),
              center = Offset(size.width * 0.22f, size.height * 0.5f),
              radius = size.width * 0.72f
            )
          )
          // Subtle glowing bottom focus accent line
          drawRect(
            brush = Brush.horizontalGradient(
              colors = listOf(
                Color.Transparent,
                darkGlassLightViolet.copy(alpha = glowAlpha * 2.5f),
                darkGlassViolet.copy(alpha = glowAlpha * 2.5f),
                Color.Transparent
              )
            ),
            topLeft = Offset(size.width * 0.12f, size.height - 2.dp.toPx()),
            size = Size(size.width * 0.76f, 2.dp.toPx())
          )
        }
      }
      .border(width = if (isFocused) 1.3.dp else 1.dp, color = borderColor, shape = shape)
      .clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        enabled = enabled
      ) {
        focusRequester.requestFocus()
      }
      .padding(horizontal = 14.dp),
    contentAlignment = Alignment.CenterStart
  ) {
    Row(
      modifier = Modifier.fillMaxSize(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      // Frosted icon capsule
      Box(
        modifier = Modifier
          .size(36.dp)
          .clip(RoundedCornerShape(11.dp))
          .background(
            if (isFocused) {
              darkGlassViolet.copy(alpha = 0.20f)
            } else {
              Color.White.copy(alpha = 0.04f)
            }
          )
          .border(
            width = 1.dp,
            color = if (isFocused) darkGlassLightViolet.copy(alpha = 0.36f) else Color.White.copy(alpha = 0.07f),
            shape = RoundedCornerShape(11.dp)
          ),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = leadingIcon,
          contentDescription = null,
          tint = if (isFocused) darkGlassLightViolet else DarkGlassMuted,
          modifier = Modifier.size(18.dp)
        )
      }

      Box(
        modifier = Modifier
          .weight(1f)
          .height(44.dp),
        contentAlignment = Alignment.CenterStart
      ) {
        // Floating label glides up cleanly
        Text(
          text = label,
          style = MaterialTheme.typography.bodyMedium.copy(
            fontSize = lerp(14f, 11f, labelFloat).sp,
            fontWeight = if (isFocused) FontWeight.SemiBold else FontWeight.Medium
          ),
          color = if (isFocused) darkGlassLightViolet else DarkGlassMuted,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.graphicsLayer {
            translationY = lerp(0f, -12.dp.toPx(), labelFloat)
          }
        )

        BasicTextField(
          value = value,
          onValueChange = onValueChange,
          enabled = enabled,
          singleLine = true,
          textStyle = MaterialTheme.typography.bodyMedium.copy(
            color = DarkGlassText,
            fontWeight = FontWeight.Medium,
            fontSize = 15.sp
          ),
          cursorBrush = SolidColor(darkGlassLightViolet),
          keyboardOptions = KeyboardOptions(
            keyboardType = keyboardType,
            imeAction = imeAction
          ),
          keyboardActions = KeyboardActions(onDone = { onImeDone() }),
          modifier = Modifier
            .fillMaxWidth()
            .align(Alignment.BottomStart)
            .padding(bottom = 3.dp)
            .focusRequester(focusRequester)
            .onFocusChanged { isFocused = it.isFocused }
        )
      }

      if (maxLengthHint != null && (isFocused || value.isNotEmpty())) {
        Surface(
          shape = RoundedCornerShape(999.dp),
          color = if (value.length == maxLengthHint) {
            darkGlassViolet.copy(alpha = 0.18f)
          } else {
            Color.White.copy(alpha = 0.05f)
          },
          border = BorderStroke(
            1.dp,
            if (value.length == maxLengthHint) {
              darkGlassLightViolet.copy(alpha = 0.34f)
            } else {
              Color.White.copy(alpha = 0.08f)
            }
          )
        ) {
          Text(
            text = "${value.length}/$maxLengthHint",
            style = MaterialTheme.typography.labelSmall.copy(
              fontWeight = FontWeight.SemiBold,
              fontSize = 10.sp
            ),
            color = if (value.length == maxLengthHint) darkGlassLightViolet else DarkGlassMuted,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
          )
        }
      }
    }
  }
}

@Composable
private fun CreateAlbumActionButtonRow(
  canCreate: Boolean,
  stage: CreateAlbumSheetStage,
  checkDrawProgress: Float,
  reducedMotion: Boolean,
  onCancel: () -> Unit,
  onCreateClick: () -> Unit
) {
  val darkGlassViolet = MaterialTheme.colorScheme.primary
  val darkGlassLightViolet = lerp(darkGlassViolet, Color.White, 0.32f)
  val darkGlassSecondaryGlow = lerp(darkGlassViolet, DarkGlassMagenta, 0.28f)
  val isIdle = stage == CreateAlbumSheetStage.Idle
  val cancelWeight by animateFloatAsState(
    targetValue = if (isIdle) 1f else 0.0001f,
    animationSpec = if (reducedMotion) snap() else tween(260, easing = AurixEaseOut),
    label = "cancel_collapse_weight"
  )
  val cancelAlpha by animateFloatAsState(
    targetValue = if (isIdle) 1f else 0f,
    animationSpec = if (reducedMotion) snap() else tween(180, easing = AurixEaseOut),
    label = "cancel_fade"
  )
  val spacerWidth by animateDpAsState(
    targetValue = if (isIdle) 12.dp else 0.dp,
    animationSpec = if (reducedMotion) snap() else tween(240, easing = AurixEaseOut),
    label = "button_gap"
  )

  // Flowing accent gradient animation when enabled
  val gradientPhase = if (!canCreate || reducedMotion) {
    0f
  } else {
    val infinite = rememberInfiniteTransition(label = "create_btn_flow")
    val anim by infinite.animateFloat(
      initialValue = 0f,
      targetValue = 1f,
      animationSpec = infiniteRepeatable(
        animation = tween(durationMillis = 2200, easing = LinearEasing),
        repeatMode = RepeatMode.Reverse
      ),
      label = "create_btn_flow_phase"
    )
    anim
  }

  val buttonShape = RoundedCornerShape(18.dp)
  val createBrush = if (stage == CreateAlbumSheetStage.SuccessCheck) {
    Brush.horizontalGradient(colors = listOf(Color(0xFF16A34A), Color(0xFF22C55E)))
  } else if (canCreate || !isIdle) {
    val c1 = lerp(darkGlassViolet, lerp(darkGlassViolet, Color.Black, 0.18f), gradientPhase)
    val c2 = lerp(darkGlassLightViolet, darkGlassSecondaryGlow, gradientPhase * 0.45f)
    Brush.horizontalGradient(colors = listOf(c1, c2, c1))
  } else {
    Brush.horizontalGradient(
      colors = listOf(Color(0xFF272433), Color(0xFF211E2B))
    )
  }

  Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically
  ) {
    if (cancelWeight > 0.02f) {
      Surface(
        shape = buttonShape,
        color = Color.White.copy(alpha = 0.06f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.09f)),
        modifier = Modifier
          .weight(cancelWeight)
          .height(54.dp)
          .alpha(cancelAlpha)
          .clip(buttonShape)
          .clickable(enabled = isIdle) { onCancel() }
          .testTag("create_album_cancel_button")
      ) {
        Box(contentAlignment = Alignment.Center) {
          Text(
            text = stringResource(id = R.string.action_cancel),
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = DarkGlassText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }
      }
    }

    if (spacerWidth > 0.dp) {
      Spacer(modifier = Modifier.width(spacerWidth))
    }

    Box(
      modifier = Modifier
        .weight(1.35f)
        .height(54.dp)
        .shadow(
          elevation = if (canCreate || !isIdle) 16.dp else 0.dp,
          shape = buttonShape,
          ambientColor = if (stage == CreateAlbumSheetStage.SuccessCheck) {
            Color(0xFF22C55E).copy(alpha = 0.45f)
          } else {
            darkGlassViolet.copy(alpha = 0.48f)
          },
          spotColor = darkGlassLightViolet.copy(alpha = 0.45f)
        )
        .clip(buttonShape)
        .background(createBrush)
        .border(
          width = 1.dp,
          color = if (canCreate || !isIdle) Color.White.copy(alpha = 0.24f) else Color.White.copy(alpha = 0.06f),
          shape = buttonShape
        )
        .clickable(enabled = isIdle) { onCreateClick() }
        .testTag("create_album_confirm_button"),
      contentAlignment = Alignment.Center
    ) {
      AnimatedContent(
        targetState = stage,
        transitionSpec = {
          if (reducedMotion) {
            fadeIn(snap()) togetherWith fadeOut(snap())
          } else {
            fadeIn(tween(180, easing = AurixEaseOut)) togetherWith
              fadeOut(tween(140, easing = AurixEaseOut))
          }
        },
        label = "create_button_stage"
      ) { currentStage ->
        when (currentStage) {
          CreateAlbumSheetStage.Idle -> {
            Text(
              text = stringResource(id = R.string.album_create_confirm_full),
              style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
              color = if (canCreate) Color.White else DarkGlassMuted,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
          }
          CreateAlbumSheetStage.Submitting -> {
            CircularProgressIndicator(
              color = Color.White,
              strokeWidth = 2.4.dp,
              modifier = Modifier.size(22.dp)
            )
          }
          CreateAlbumSheetStage.SuccessCheck, CreateAlbumSheetStage.Closing -> {
            SelfDrawingCheckmark(
              progress = checkDrawProgress,
              modifier = Modifier.size(26.dp)
            )
          }
        }
      }
    }
  }
}

@Composable
private fun SelfDrawingCheckmark(
  progress: Float,
  modifier: Modifier = Modifier
) {
  Canvas(modifier = modifier) {
    val fullPath = Path().apply {
      moveTo(size.width * 0.20f, size.height * 0.54f)
      lineTo(size.width * 0.42f, size.height * 0.76f)
      lineTo(size.width * 0.82f, size.height * 0.26f)
    }
    val measure = PathMeasure().apply { setPath(fullPath, false) }
    val segment = Path()
    measure.getSegment(
      startDistance = 0f,
      stopDistance = measure.length * progress.coerceIn(0f, 1f),
      destination = segment,
      startWithMoveTo = true
    )
    drawPath(
      path = segment,
      color = Color.White,
      style = Stroke(
        width = 3.dp.toPx(),
        cap = StrokeCap.Round,
        join = StrokeJoin.Round
      )
    )
  }
}

@Composable
fun FlyingAlbumCoverOverlay(
  payload: FlyingAlbumCoverPayload,
  targetRect: Rect?,
  reducedMotion: Boolean,
  onFlightFinished: () -> Unit,
  modifier: Modifier = Modifier
) {
  val density = LocalDensity.current
  val progress = remember(payload.albumId) { Animatable(0f) }

  LaunchedEffect(payload.albumId, reducedMotion) {
    if (reducedMotion) {
      progress.snapTo(1f)
      onFlightFinished()
      return@LaunchedEffect
    }
    progress.snapTo(0f)
    progress.animateTo(
      targetValue = 1f,
      animationSpec = tween(durationMillis = 520, easing = AurixEaseOut)
    )
    onFlightFinished()
  }

  val fallbackTarget = remember(payload.sourceRect) {
    val targetSide = payload.sourceRect.width * 1.38f
    Rect(
      offset = Offset(payload.sourceRect.left * 0.35f, payload.sourceRect.top * 0.36f),
      size = Size(targetSide, targetSide)
    )
  }
  val dst = targetRect ?: fallbackTarget
  val src = payload.sourceRect

  var overlayOrigin by remember { mutableStateOf(Offset.Zero) }

  val t = progress.value
  val oneMinusT = 1f - t

  val p0 = src.center
  val p2 = dst.center
  // Curved arc control point lifted up and horizontally offset
  val liftY = minOf(p0.y, p2.y) - with(density) { 88.dp.toPx() }
  val ctrlX = (p0.x + p2.x) * 0.5f + with(density) { 42.dp.toPx() }
  val p1 = Offset(ctrlX, liftY)

  val curCenterX = oneMinusT * oneMinusT * p0.x + 2f * oneMinusT * t * p1.x + t * t * p2.x
  val curCenterY = oneMinusT * oneMinusT * p0.y + 2f * oneMinusT * t * p1.y + t * t * p2.y

  val curW = lerp(src.width, dst.width, t)
  val curH = lerp(src.height, dst.height, t)
  val curLeft = curCenterX - curW * 0.5f - overlayOrigin.x
  val curTop = curCenterY - curH * 0.5f - overlayOrigin.y

  val letter = payload.title.trim().firstOrNull()?.uppercaseChar()?.toString().orEmpty()

  Box(
    modifier = modifier
      .fillMaxSize()
      .onGloballyPositioned { overlayOrigin = it.boundsInRoot().topLeft }
  ) {
    AlbumCoverArtBox(
      preset = payload.preset,
      firstLetter = letter,
      isTypingBoosted = false,
      reducedMotion = true,
      showEditBadge = false,
      modifier = Modifier
        .offset { IntOffset(curLeft.roundToInt(), curTop.roundToInt()) }
        .size(
          width = with(density) { curW.toDp() },
          height = with(density) { curH.toDp() }
        )
    )
  }
}
