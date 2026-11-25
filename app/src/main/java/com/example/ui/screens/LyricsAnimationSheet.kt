package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.R
import com.example.lyrics.LyricsAnimationStyle
import com.example.lyrics.LyricsAnimationTarget
import java.text.BreakIterator
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private val BorderCyan = Color(0xFF43C7F4)
private val BorderPurple = Color(0xFF7965FF)
private val SelectedGradientBrush = Brush.linearGradient(
  colors = listOf(BorderCyan, BorderPurple)
)

@Composable
fun lyricsAnimDisplayName(style: LyricsAnimationStyle): String {
  return when (style) {
    LyricsAnimationStyle.SLIDE_UP -> stringResource(id = R.string.lyrics_anim_slide_up_name)
    LyricsAnimationStyle.BLUR_FADE -> stringResource(id = R.string.lyrics_anim_blur_fade_name)
    LyricsAnimationStyle.KARAOKE_FILL -> stringResource(id = R.string.lyrics_anim_karaoke_fill_name)
    LyricsAnimationStyle.TYPEWRITER -> stringResource(id = R.string.lyrics_anim_typewriter_name)
    LyricsAnimationStyle.POP_ZOOM -> stringResource(id = R.string.lyrics_anim_pop_zoom_name)
  }
}

@Composable
fun lyricsAnimDescription(style: LyricsAnimationStyle): String {
  return when (style) {
    LyricsAnimationStyle.SLIDE_UP -> stringResource(id = R.string.lyrics_anim_slide_up_desc)
    LyricsAnimationStyle.BLUR_FADE -> stringResource(id = R.string.lyrics_anim_blur_fade_desc)
    LyricsAnimationStyle.KARAOKE_FILL -> stringResource(id = R.string.lyrics_anim_karaoke_fill_desc)
    LyricsAnimationStyle.TYPEWRITER -> stringResource(id = R.string.lyrics_anim_typewriter_desc)
    LyricsAnimationStyle.POP_ZOOM -> stringResource(id = R.string.lyrics_anim_pop_zoom_desc)
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsAnimationSheet(
  appLyricsAnim: LyricsAnimationStyle,
  widgetLyricsAnim: LyricsAnimationStyle,
  khmerLyricsFont: com.example.ui.theme.KhmerLyricsFont = com.example.ui.theme.KhmerLyricsFont.KANTUMRUY_PRO,
  reducedMotion: Boolean,
  darkTheme: Boolean,
  onSelectBoth: (LyricsAnimationStyle) -> Unit,
  onSelectApp: (LyricsAnimationStyle) -> Unit,
  onSelectWidget: (LyricsAnimationStyle) -> Unit,
  onSelectKhmerLyricsFont: (com.example.ui.theme.KhmerLyricsFont) -> Unit = {},
  onDismiss: () -> Unit
) {
  val context = LocalContext.current
  val haptics = LocalHapticFeedback.current
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  val lifecycleOwner = LocalLifecycleOwner.current

  var isAppForeground by remember {
    mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
  }
  DisposableEffect(lifecycleOwner) {
    val observer = LifecycleEventObserver { _, event ->
      when (event) {
        Lifecycle.Event.ON_RESUME -> isAppForeground = true
        Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> isAppForeground = false
        else -> Unit
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
  }

  val motionDisabled = remember(reducedMotion, isAppForeground) {
    LyricsAnimationStyle.isMotionDisabled(context, reducedMotion)
  }

  var activeTarget by remember {
    mutableStateOf(
      if (appLyricsAnim == widgetLyricsAnim) LyricsAnimationTarget.BOTH
      else LyricsAnimationTarget.APP
    )
  }

  // Shared preview clock & replay trigger so the "Preview" button replays cleanly
  var replayNonce by remember { mutableIntStateOf(0) }
  var previewClockMs by remember { mutableLongStateOf(0L) }

  LaunchedEffect(isAppForeground, motionDisabled, replayNonce) {
    if (!isAppForeground) return@LaunchedEffect
    if (motionDisabled) {
      // In Remove Animations mode, toggle cleanly between lines every 2.2s with instant snap
      previewClockMs = 0L
      while (isActive) {
        kotlinx.coroutines.delay(2200L)
        previewClockMs += 2200L
      }
      return@LaunchedEffect
    }
    previewClockMs = 0L
    var startNanos = 0L
    while (isActive) {
      withFrameNanos { frameNanos ->
        if (startNanos == 0L) startNanos = frameNanos
        previewClockMs = (frameNanos - startNanos) / 1_000_000L
      }
    }
  }

  val sampleLine1 = stringResource(id = R.string.settings_lyrics_anim_sample_line_1)
  val sampleLine2 = stringResource(id = R.string.settings_lyrics_anim_sample_line_2)
  val sameStyle = appLyricsAnim == widgetLyricsAnim
  val selectedForCurrentTarget = when (activeTarget) {
    LyricsAnimationTarget.BOTH -> if (sameStyle) appLyricsAnim else null
    LyricsAnimationTarget.APP -> appLyricsAnim
    LyricsAnimationTarget.WIDGET -> widgetLyricsAnim
  }

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = if (darkTheme) Color(0xFF0E1525) else MaterialTheme.colorScheme.surface,
    contentColor = MaterialTheme.colorScheme.onSurface,
    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .navigationBarsPadding()
        .padding(horizontal = 20.dp)
        .testTag("lyrics_animation_sheet")
    ) {
      // Header row with title + Preview replay button
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = stringResource(id = R.string.settings_lyrics_anim_title),
            style = MaterialTheme.typography.titleLarge.copy(
              fontWeight = FontWeight.Bold,
              lineHeight = 28.sp
            ),
            color = MaterialTheme.colorScheme.onSurface
          )
          Spacer(modifier = Modifier.height(2.dp))
          Text(
            text = if (sameStyle) {
              stringResource(
                id = R.string.settings_lyrics_anim_summary_both,
                lyricsAnimDisplayName(appLyricsAnim)
              )
            } else {
              stringResource(
                id = R.string.settings_lyrics_anim_summary_split,
                lyricsAnimDisplayName(appLyricsAnim),
                lyricsAnimDisplayName(widgetLyricsAnim)
              )
            },
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.primary
          )
        }

        Spacer(modifier = Modifier.width(10.dp))

        Surface(
          onClick = {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            replayNonce++
          },
          shape = CircleShape,
          color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.34f)),
          modifier = Modifier
            .heightIn(min = 42.dp)
            .testTag("lyrics_anim_preview_button")
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            Icon(
              imageVector = Icons.Rounded.Replay,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(16.dp)
            )
            Text(
              text = stringResource(id = R.string.settings_lyrics_anim_preview_button),
              style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
              color = MaterialTheme.colorScheme.primary
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(10.dp))

      // Target segmented selector: Both (same) | App Lyrics | Widget (independent)
      Surface(
        shape = RoundedCornerShape(999.dp),
        color = if (darkTheme) Color(0xFF151F30) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
        border = BorderStroke(
          1.dp,
          if (darkTheme) Color(0xFF273348) else MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)
        ),
        modifier = Modifier
          .fillMaxWidth()
          .testTag("lyrics_anim_target_selector")
      ) {
        Row(
          modifier = Modifier.padding(4.dp),
          horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
          listOf(
            Triple(
              LyricsAnimationTarget.BOTH,
              stringResource(id = R.string.settings_lyrics_anim_target_both),
              Icons.Rounded.AutoAwesome
            ),
            Triple(
              LyricsAnimationTarget.APP,
              stringResource(id = R.string.settings_lyrics_anim_target_app),
              Icons.Rounded.PhoneAndroid
            ),
            Triple(
              LyricsAnimationTarget.WIDGET,
              stringResource(id = R.string.settings_lyrics_anim_target_widget),
              Icons.Rounded.Widgets
            )
          ).forEach { (target, label, icon) ->
            val isTargetSelected = activeTarget == target
            Box(
              modifier = Modifier
                .weight(1f)
                .heightIn(min = 40.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(
                  if (isTargetSelected) {
                    if (darkTheme) Color(0xFF24314A) else Color.White
                  } else {
                    Color.Transparent
                  }
                )
                .clickable {
                  haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                  activeTarget = target
                }
                .testTag("lyrics_anim_target_${target.name.lowercase()}")
                .padding(horizontal = 8.dp, vertical = 8.dp),
              contentAlignment = Alignment.Center
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
              ) {
                Icon(
                  imageVector = icon,
                  contentDescription = null,
                  tint = if (isTargetSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                  modifier = Modifier.size(14.dp)
                )
                Text(
                  text = label,
                  style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = if (isTargetSelected) FontWeight.Bold else FontWeight.Medium
                  ),
                  color = if (isTargetSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis
                )
              }
            }
          }
        }
      }

      if (motionDisabled) {
        Spacer(modifier = Modifier.height(10.dp))
        Surface(
          shape = RoundedCornerShape(14.dp),
          color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.24f)),
          modifier = Modifier.fillMaxWidth()
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Icon(
              imageVector = Icons.Rounded.Info,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(16.dp)
            )
            Text(
              text = stringResource(id = R.string.settings_lyrics_anim_reduced_motion_note),
              style = MaterialTheme.typography.bodySmall.copy(lineHeight = 17.sp),
              color = MaterialTheme.colorScheme.onSurface
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(10.dp))

      // Khmer Lyrics Font Picker (Kantumruy Pro, Koulen, Noto Sans Khmer) + Dangrek Title badge
      KhmerLyricsFontSelectorCard(
        selectedFont = khmerLyricsFont,
        darkTheme = darkTheme,
        onSelectFont = { font ->
          haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
          onSelectKhmerLyricsFont(font)
        }
      )

      Spacer(modifier = Modifier.height(12.dp))

      LazyColumn(
        modifier = Modifier
          .fillMaxWidth()
          .heightIn(max = 520.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        items(LyricsAnimationStyle.entries, key = { it.id }) { style ->
          val isSelected = selectedForCurrentTarget == style
          val showAppTag = !sameStyle && appLyricsAnim == style
          val showWidgetTag = !sameStyle && widgetLyricsAnim == style

          LyricsAnimationOptionCard(
            style = style,
            isSelected = isSelected,
            showAppTag = showAppTag,
            showWidgetTag = showWidgetTag,
            activeTarget = activeTarget,
            khmerLyricsFont = khmerLyricsFont,
            previewClockMs = previewClockMs,
            motionDisabled = motionDisabled,
            darkTheme = darkTheme,
            sampleLine1 = sampleLine1,
            sampleLine2 = sampleLine2,
            onClick = {
              haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
              when (activeTarget) {
                LyricsAnimationTarget.BOTH -> onSelectBoth(style)
                LyricsAnimationTarget.APP -> onSelectApp(style)
                LyricsAnimationTarget.WIDGET -> onSelectWidget(style)
              }
            }
          )
        }
      }
    }
  }
}

@Composable
fun KhmerLyricsFontSelectorCard(
  selectedFont: com.example.ui.theme.KhmerLyricsFont,
  darkTheme: Boolean,
  onSelectFont: (com.example.ui.theme.KhmerLyricsFont) -> Unit,
  modifier: Modifier = Modifier
) {
  Column(
    modifier = modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(18.dp))
      .background(
        if (darkTheme) Color(0xFF151F30) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.56f)
      )
      .border(
        1.dp,
        if (darkTheme) Color(0xFF273348) else MaterialTheme.colorScheme.outline.copy(alpha = 0.18f),
        RoundedCornerShape(18.dp)
      )
      .padding(horizontal = 14.dp, vertical = 11.dp)
      .testTag("khmer_lyrics_font_selector_card"),
    verticalArrangement = Arrangement.spacedBy(8.dp)
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = stringResource(id = R.string.settings_khmer_lyrics_font_title),
          style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
          color = MaterialTheme.colorScheme.onSurface
        )
        Text(
          text = stringResource(id = R.string.settings_khmer_lyrics_font_subtitle),
          style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, lineHeight = 15.sp),
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
      Spacer(modifier = Modifier.width(8.dp))
      Surface(
        shape = RoundedCornerShape(999.dp),
        color = BorderCyan.copy(alpha = 0.14f),
        border = BorderStroke(0.75.dp, BorderCyan.copy(alpha = 0.36f))
      ) {
        Text(
          text = stringResource(id = R.string.khmer_title_font_dangrek_badge),
          style = MaterialTheme.typography.labelSmall.copy(
            fontFamily = com.example.ui.theme.AurixFontFamilies.DangrekTitleFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 10.5.sp
          ),
          color = BorderCyan,
          modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
        )
      }
    }

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
      com.example.ui.theme.KhmerLyricsFont.entries.forEach { fontOption ->
        val selected = selectedFont == fontOption
        val fontFamily = com.example.ui.theme.AurixFontFamilies.composeFamilyForLyricsFont(fontOption)
        Box(
          modifier = Modifier
            .weight(1f)
            .heightIn(min = 42.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(
              if (selected) {
                Brush.linearGradient(
                  listOf(
                    BorderCyan.copy(alpha = if (darkTheme) 0.24f else 0.18f),
                    BorderPurple.copy(alpha = if (darkTheme) 0.24f else 0.18f)
                  )
                )
              } else {
                Brush.linearGradient(
                  listOf(
                    if (darkTheme) Color.White.copy(alpha = 0.05f) else Color.White.copy(alpha = 0.72f),
                    if (darkTheme) Color.White.copy(alpha = 0.05f) else Color.White.copy(alpha = 0.72f)
                  )
                )
              }
            )
            .border(
              width = if (selected) 1.5.dp else 1.dp,
              color = if (selected) BorderCyan else if (darkTheme) Color.White.copy(alpha = 0.12f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.18f),
              shape = RoundedCornerShape(13.dp)
            )
            .clickable { onSelectFont(fontOption) }
            .padding(horizontal = 6.dp, vertical = 7.dp)
            .testTag("khmer_lyrics_font_chip_${fontOption.id}"),
          contentAlignment = Alignment.Center
        ) {
          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(1.dp)
          ) {
            Text(
              text = fontOption.displayName,
              style = MaterialTheme.typography.labelMedium.copy(
                fontFamily = fontFamily,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                fontSize = 11.5.sp
              ),
              color = if (selected) BorderCyan else MaterialTheme.colorScheme.onSurface,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
            Text(
              text = fontOption.khmerLabel,
              style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = fontFamily,
                fontSize = 10.sp
              ),
              color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
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
private fun LyricsAnimationOptionCard(
  style: LyricsAnimationStyle,
  isSelected: Boolean,
  showAppTag: Boolean,
  showWidgetTag: Boolean,
  activeTarget: LyricsAnimationTarget,
  khmerLyricsFont: com.example.ui.theme.KhmerLyricsFont,
  previewClockMs: Long,
  motionDisabled: Boolean,
  darkTheme: Boolean,
  sampleLine1: String,
  sampleLine2: String,
  onClick: () -> Unit
) {
  val scope = rememberCoroutineScope()
  val tapScale = remember { Animatable(1f) }

  // Cyan-to-purple border fades in on selection and fades back to normal when deselected
  val borderAlpha by animateFloatAsState(
    targetValue = if (isSelected) 1f else 0f,
    animationSpec = if (motionDisabled) snap() else tween(durationMillis = 240, easing = FastOutSlowInEasing),
    label = "lyrics_anim_border_alpha_${style.id}"
  )

  val cardShape = RoundedCornerShape(20.dp)
  val baseCardColor = if (darkTheme) Color(0xFF151F30) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.50f)
  val unselectedBorderColor = if (darkTheme) Color(0xFF273348) else MaterialTheme.colorScheme.outline.copy(alpha = 0.20f)

  Box(
    modifier = Modifier
      .fillMaxWidth()
      .graphicsLayer {
        scaleX = tapScale.value
        scaleY = tapScale.value
      }
      .clip(cardShape)
      .background(baseCardColor)
      .border(1.dp, unselectedBorderColor, cardShape)
      .then(
        if (borderAlpha > 0.01f) {
          Modifier.border(
            width = 2.dp,
            brush = Brush.linearGradient(
              colors = listOf(
                BorderCyan.copy(alpha = borderAlpha),
                BorderPurple.copy(alpha = borderAlpha)
              )
            ),
            shape = cardShape
          )
        } else {
          Modifier
        }
      )
      .clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null
      ) {
        onClick()
        if (!motionDisabled) {
          scope.launch {
            tapScale.snapTo(1f)
            tapScale.animateTo(0.97f, animationSpec = tween(durationMillis = 60, easing = FastOutSlowInEasing))
            tapScale.animateTo(1f, animationSpec = tween(durationMillis = 60, easing = FastOutSlowInEasing))
          }
        }
      }
      .padding(14.dp)
      .testTag("lyrics_anim_card_${style.id}")
  ) {
    Column(
      modifier = Modifier.fillMaxWidth(),
      verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      // Mini-widget looping live preview (2 sample lines)
      LyricsMiniWidgetPreview(
        style = style,
        khmerLyricsFont = khmerLyricsFont,
        previewClockMs = previewClockMs,
        motionDisabled = motionDisabled,
        sampleLine1 = sampleLine1,
        sampleLine2 = sampleLine2
      )

      // Name, description, optional widget note, and animated check badge
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column(
          modifier = Modifier.weight(1f),
          verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            Text(
              text = lyricsAnimDisplayName(style),
              style = MaterialTheme.typography.titleSmall.copy(
                fontWeight = FontWeight.Bold,
                lineHeight = 20.sp
              ),
              color = MaterialTheme.colorScheme.onSurface
            )

            if (showAppTag) {
              TargetMiniTag(
                label = stringResource(id = R.string.settings_lyrics_anim_target_app),
                color = BorderCyan
              )
            }
            if (showWidgetTag) {
              TargetMiniTag(
                label = stringResource(id = R.string.settings_lyrics_anim_target_widget),
                color = BorderPurple
              )
            }
          }

          Text(
            text = lyricsAnimDescription(style),
            style = MaterialTheme.typography.bodySmall.copy(lineHeight = 17.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
          )

          if (style.isWidgetFallback) {
            Spacer(modifier = Modifier.height(2.dp))
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
              Surface(
                shape = RoundedCornerShape(999.dp),
                color = BorderCyan.copy(alpha = 0.13f),
                border = BorderStroke(0.75.dp, BorderCyan.copy(alpha = 0.32f))
              ) {
                Text(
                  text = stringResource(id = R.string.settings_lyrics_anim_full_effect_note),
                  style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 10.5.sp
                  ),
                  color = BorderCyan,
                  modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
              }
              if (activeTarget == LyricsAnimationTarget.WIDGET) {
                Text(
                  text = stringResource(id = R.string.settings_lyrics_anim_widget_fallback_hint),
                  style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
            }
          }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Check badge pops in with spring overshoot when selected
        Box(
          modifier = Modifier.size(28.dp),
          contentAlignment = Alignment.Center
        ) {
          androidx.compose.animation.AnimatedVisibility(
            visible = isSelected,
            enter = if (motionDisabled) {
              fadeIn(snap())
            } else {
              scaleIn(
                initialScale = 0.25f,
                animationSpec = spring(
                  dampingRatio = Spring.DampingRatioMediumBouncy,
                  stiffness = Spring.StiffnessMedium
                )
              ) + fadeIn(tween(150))
            },
            exit = if (motionDisabled) {
              fadeOut(snap())
            } else {
              scaleOut(targetScale = 0.5f, animationSpec = tween(140)) + fadeOut(tween(140))
            }
          ) {
            Box(
              modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(SelectedGradientBrush)
                .testTag("lyrics_anim_check_${style.id}"),
              contentAlignment = Alignment.Center
            ) {
              Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = stringResource(id = R.string.lyrics_anim_selected_cd),
                tint = Color.White,
                modifier = Modifier.size(16.dp)
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun TargetMiniTag(label: String, color: Color) {
  Surface(
    shape = RoundedCornerShape(999.dp),
    color = color.copy(alpha = 0.16f),
    border = BorderStroke(0.75.dp, color.copy(alpha = 0.36f))
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall.copy(
        fontWeight = FontWeight.Bold,
        fontSize = 9.5.sp
      ),
      color = color,
      modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.5.dp)
    )
  }
}

/**
 * Compact mini-widget preview box showing 2 sample lines looping through the exact
 * lyric line-change animation style (or instant swap when motionDisabled is true).
 */
@Composable
private fun LyricsMiniWidgetPreview(
  style: LyricsAnimationStyle,
  khmerLyricsFont: com.example.ui.theme.KhmerLyricsFont,
  previewClockMs: Long,
  motionDisabled: Boolean,
  sampleLine1: String,
  sampleLine2: String
) {
  val cycleMs = 2400L
  val phaseIndex = ((previewClockMs / cycleMs) % 2L).toInt()
  val elapsedMs = previewClockMs % cycleMs
  val currentLine = if (phaseIndex == 0) sampleLine1 else sampleLine2
  val nextLine = if (phaseIndex == 0) sampleLine2 else sampleLine1
  val previousLine = nextLine

  val styledCurrentLine = com.example.ui.theme.rememberKhmerLyricsText(
    text = currentLine,
    khmerLyricsFont = khmerLyricsFont,
    baseFontSize = 14.sp,
    isBold = true
  )
  val styledPreviousLine = com.example.ui.theme.rememberKhmerLyricsText(
    text = previousLine,
    khmerLyricsFont = khmerLyricsFont,
    baseFontSize = 14.sp,
    isBold = true
  )
  val styledNextLine = com.example.ui.theme.rememberKhmerLyricsText(
    text = nextLine,
    khmerLyricsFont = khmerLyricsFont,
    baseFontSize = 11.5.sp,
    isBold = false
  )

  val previewBgBrush = Brush.linearGradient(
    colors = listOf(
      Color(0xFF101B33),
      Color(0xFF1A2242),
      Color(0xFF21183D)
    )
  )
  val density = LocalDensity.current
  val slideOffsetPx = with(density) { 24.dp.toPx() }

  Box(
    modifier = Modifier
      .fillMaxWidth()
      .height(74.dp)
      .clip(RoundedCornerShape(15.dp))
      .background(previewBgBrush)
      .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(15.dp))
      .padding(horizontal = 12.dp, vertical = 10.dp)
  ) {
    Column(
      modifier = Modifier.fillMaxSize(),
      verticalArrangement = Arrangement.Center
    ) {
      // Active lyric line row: vertically aligned beside the accent bar, ONLY currentLine animates
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Box(
          modifier = Modifier
            .width(3.dp)
            .height(22.dp)
            .clip(CircleShape)
            .background(
              Brush.verticalGradient(listOf(BorderCyan, BorderPurple))
            )
        )
        Spacer(modifier = Modifier.width(10.dp))

        Box(modifier = Modifier.weight(1f)) {
          if (motionDisabled) {
            Text(
              text = styledCurrentLine,
              style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                lineHeight = 19.sp
              ),
              color = Color.White,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
          } else {
            when (style) {
              LyricsAnimationStyle.SLIDE_UP -> {
                val raw = (elapsedMs.toFloat() / 550f).coerceIn(0f, 1f)
                val eased = 1f - (1f - raw) * (1f - raw) * (1f - raw)
                val exitProgress = (elapsedMs.toFloat() / 420f).coerceIn(0f, 1f)
                val prevAlpha = (1f - exitProgress).coerceIn(0f, 1f)
                val prevBlurDp = (sin(exitProgress * Math.PI).toFloat() * 4.5f).coerceAtLeast(0f)

                Box(modifier = Modifier.fillMaxWidth()) {
                  if (prevAlpha > 0.02f) {
                    Text(
                      text = styledPreviousLine,
                      style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        lineHeight = 19.sp
                      ),
                      color = Color.White,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis,
                      modifier = Modifier
                        .blur(prevBlurDp.dp)
                        .graphicsLayer {
                          alpha = prevAlpha * 0.7f
                          translationY = -slideOffsetPx * 0.45f * exitProgress
                        }
                    )
                  }
                  Text(
                    text = styledCurrentLine,
                    style = MaterialTheme.typography.bodyMedium.copy(
                      fontWeight = FontWeight.Bold,
                      fontSize = 14.sp,
                      lineHeight = 19.sp
                    ),
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.graphicsLayer {
                      translationY = slideOffsetPx * (1f - eased)
                      alpha = eased
                    }
                  )
                }
              }

              LyricsAnimationStyle.BLUR_FADE -> {
                val raw = (elapsedMs.toFloat() / 700f).coerceIn(0f, 1f)
                val eased = 1f - (1f - raw) * (1f - raw) * (1f - raw)
                val inv = 1f - eased
                val blurRadius = (10f * inv).dp
                Text(
                  text = styledCurrentLine,
                  style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    lineHeight = 19.sp,
                    letterSpacing = (0.08f * inv).em
                  ),
                  color = Color.White,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                  modifier = Modifier
                    .blur(blurRadius)
                    .graphicsLayer { alpha = eased }
                )
              }

              LyricsAnimationStyle.KARAOKE_FILL -> {
                val sweep = (elapsedMs.toFloat() / (cycleMs - 250L).toFloat()).coerceIn(0f, 1f)
                Box(modifier = Modifier.fillMaxWidth()) {
                  Text(
                    text = styledCurrentLine,
                    style = MaterialTheme.typography.bodyMedium.copy(
                      fontWeight = FontWeight.Bold,
                      fontSize = 14.sp,
                      lineHeight = 19.sp
                    ),
                    color = Color.White.copy(alpha = 0.35f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                  )
                  Text(
                    text = styledCurrentLine,
                    style = MaterialTheme.typography.bodyMedium.copy(
                      fontWeight = FontWeight.Bold,
                      fontSize = 14.sp,
                      lineHeight = 19.sp
                    ),
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.drawWithContent {
                      clipRect(right = size.width * sweep) {
                        this@drawWithContent.drawContent()
                      }
                    }
                  )
                }
              }

              LyricsAnimationStyle.TYPEWRITER -> {
                val boundaries = remember(currentLine) {
                  val list = mutableListOf(0)
                  val bi = BreakIterator.getCharacterInstance()
                  bi.setText(currentLine)
                  var pos = bi.next()
                  while (pos != BreakIterator.DONE) {
                    list.add(pos)
                    pos = bi.next()
                  }
                  list
                }
                val frac = (elapsedMs.toFloat() / 1400f).coerceIn(0f, 1f)
                val idx = ((boundaries.size - 1) * frac).roundToInt().coerceIn(0, boundaries.lastIndex)
                val revealed = currentLine.substring(0, boundaries[idx].coerceIn(0, currentLine.length))
                val styledRevealed = com.example.ui.theme.rememberKhmerLyricsText(
                  text = revealed,
                  khmerLyricsFont = khmerLyricsFont,
                  baseFontSize = 14.sp,
                  isBold = true
                )
                val caretOn = ((previewClockMs / 440L) % 2L) == 0L

                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  modifier = Modifier.fillMaxWidth()
                ) {
                  Text(
                    text = styledRevealed,
                    style = MaterialTheme.typography.bodyMedium.copy(
                      fontWeight = FontWeight.Bold,
                      fontSize = 14.sp,
                      lineHeight = 19.sp
                    ),
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                  )
                  Spacer(modifier = Modifier.width(2.dp))
                  Box(
                    modifier = Modifier
                      .width(2.dp)
                      .height(16.dp)
                      .clip(CircleShape)
                      .background(if (caretOn) BorderCyan else Color.Transparent)
                  )
                }
              }

              LyricsAnimationStyle.POP_ZOOM -> {
                val raw = (elapsedMs.toFloat() / 600f).coerceIn(0f, 1f)
                val t = raw - 1f
                val tension = 1.35f
                val overshoot = t * t * ((tension + 1f) * t + tension) + 1f
                val scaleVal = (0.6f + 0.4f * overshoot).coerceAtLeast(0.6f)
                Text(
                  text = styledCurrentLine,
                  style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    lineHeight = 19.sp
                  ),
                  color = Color.White,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                  modifier = Modifier.graphicsLayer {
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
                    scaleX = scaleVal
                    scaleY = scaleVal
                    alpha = raw.coerceIn(0f, 1f)
                  }
                )
              }
            }
          }
        }
      }

      // Upcoming / Next lyric line: always static below the active line (never receives animation)
      Spacer(modifier = Modifier.height(3.dp))
      Text(
        text = styledNextLine,
        style = MaterialTheme.typography.bodySmall.copy(
          fontSize = 11.5.sp,
          lineHeight = 15.sp
        ),
        color = Color.White.copy(alpha = 0.52f),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(start = 13.dp)
      )
    }
  }
}
