package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.IconManager
import com.example.data.OrbiAppIcon
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.sin

/**
 * Animated rounded-square preview of one of the 5 Orbi launcher icons:
 * - Renders the exact background gradient, planet gradient, tilted eyes (-12°),
 *   and back/front orbit ring for Classic, Midnight, Ember, AMOLED, or Pearl.
 * - Includes gentle eye blinking and floating planet animation when [animated] is true.
 */
@Composable
fun OrbiLauncherIconTile(
  icon: OrbiAppIcon,
  modifier: Modifier = Modifier,
  size: Dp = 64.dp,
  cornerRadius: Dp = 18.dp,
  animated: Boolean = true,
  reducedMotion: Boolean = false
) {
  val animationsEnabled = rememberOrbiAnimationsEnabled(reducedMotion) && animated
  val infiniteTransition = rememberInfiniteTransition(label = "orbi_launcher_tile_${icon.id}")

  val floatPhase = if (!animationsEnabled) {
    0f
  } else {
    val anim by infiniteTransition.animateFloat(
      initialValue = -1f,
      targetValue = 1f,
      animationSpec = infiniteRepeatable(
        animation = tween(durationMillis = 1750, easing = OrbiEasing),
        repeatMode = RepeatMode.Reverse
      ),
      label = "tile_float"
    )
    anim
  }

  val blinkClock = if (!animationsEnabled) {
    0.5f
  } else {
    val anim by infiniteTransition.animateFloat(
      initialValue = 0f,
      targetValue = 1f,
      animationSpec = infiniteRepeatable(
        animation = tween(durationMillis = 3900, easing = LinearEasing),
        repeatMode = RepeatMode.Restart
      ),
      label = "tile_blink"
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
    val anim by infiniteTransition.animateFloat(
      initialValue = 0f,
      targetValue = 1f,
      animationSpec = infiniteRepeatable(
        animation = tween(durationMillis = 4200, easing = LinearEasing),
        repeatMode = RepeatMode.Restart
      ),
      label = "tile_glance"
    )
    sin(anim * 2.0 * PI).toFloat()
  }

  val tileBorderColor = when (icon) {
    OrbiAppIcon.AMOLED -> Color.White.copy(alpha = 0.24f)
    OrbiAppIcon.PEARL -> Color(0xFFA78BFA).copy(alpha = 0.45f)
    else -> Color.White.copy(alpha = 0.22f)
  }

  Box(
    modifier = modifier
      .size(size)
      .clip(RoundedCornerShape(cornerRadius))
      .background(
        Brush.verticalGradient(
          colors = listOf(icon.bgTopColor, icon.bgBottomColor)
        )
      )
      .border(
        width = 1.dp,
        color = tileBorderColor,
        shape = RoundedCornerShape(cornerRadius)
      ),
    contentAlignment = Alignment.Center
  ) {
    Canvas(
      modifier = Modifier
        .fillMaxSize()
        .graphicsLayer {
          translationY = floatPhase * 1.8.dp.toPx()
        }
    ) {
      if (this.size.minDimension <= 0f) return@Canvas
      val s = this.size.minDimension / 108f
      val cx = this.size.width / 2f
      val cy = this.size.height / 2f

      val planetR = 20.5f * s
      val ringRx = 29.5f * s
      val ringRy = 7.8f * s
      val ringStroke = (if (icon.isThinRing) 2.4f else 3.8f) * s

      val ringBrush = Brush.linearGradient(
        colors = icon.ringColors,
        start = Offset(cx - ringRx, cy - ringRy),
        end = Offset(cx + ringRx, cy + ringRy)
      )

      rotate(degrees = -12f, pivot = Offset(cx, cy)) {
        // 1. Back half of orbit ring (180..360)
        drawArc(
          brush = ringBrush,
          startAngle = 180f,
          sweepAngle = 180f,
          useCenter = false,
          topLeft = Offset(cx - ringRx, cy - ringRy),
          size = Size(ringRx * 2f, ringRy * 2f),
          style = Stroke(width = ringStroke, cap = StrokeCap.Round)
        )

        // 2. Soft drop shadow + Planet sphere
        if (icon != OrbiAppIcon.AMOLED) {
          drawCircle(
            color = Color.Black.copy(alpha = 0.16f),
            radius = planetR * 1.04f,
            center = Offset(cx, cy + 1.5f * s)
          )
        }
        drawCircle(
          brush = Brush.verticalGradient(
            colors = listOf(icon.planetTopColor, icon.planetBottomColor),
            startY = cy - planetR,
            endY = cy + planetR
          ),
          radius = planetR,
          center = Offset(cx, cy)
        )

        // 3. Two tall oval eyes
        val gDx = glancePhase * 1.6f * s
        val eyeRx = 2.5f * s
        val eyeRy = 4.3f * s * blinkScaleY.coerceAtLeast(0.08f)
        val leftEyeCx = cx - 4.5f * s + gDx
        val rightEyeCx = cx + 4.5f * s + gDx
        val eyeCy = cy - 9.8f * s

        drawRoundRect(
          color = icon.eyeColor,
          topLeft = Offset(leftEyeCx - eyeRx, eyeCy - eyeRy),
          size = Size(eyeRx * 2f, eyeRy * 2f),
          cornerRadius = CornerRadius(eyeRx, eyeRx)
        )
        drawRoundRect(
          color = icon.eyeColor,
          topLeft = Offset(rightEyeCx - eyeRx, eyeCy - eyeRy),
          size = Size(eyeRx * 2f, eyeRy * 2f),
          cornerRadius = CornerRadius(eyeRx, eyeRx)
        )

        // 4. Front half of orbit ring (0..180)
        if (icon == OrbiAppIcon.AMOLED) {
          drawArc(
            color = Color.Black,
            startAngle = 0f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(cx - ringRx, cy - ringRy),
            size = Size(ringRx * 2f, ringRy * 2f),
            style = Stroke(width = ringStroke * 2.15f, cap = StrokeCap.Round)
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
    }
  }
}

/**
 * PICKER UI (Settings > Appearance > App icon):
 * - 5 rounded-square previews with name under each (arranged in a responsive 5-item row / grid).
 * - Selected one has an accent ring and check badge.
 * - Tap: preview scales up slightly, then a confirm bottom sheet says
 *   "Change app icon to Midnight?" with Apply and Cancel.
 * - After Apply: Orbi happy animation and a toast "Icon changed".
 * - Shows note: "Your home screen may take a few seconds to update. Some launchers close the app when the icon changes."
 * - Works in Dark, Light, and AMOLED themes.
 */
@Composable
fun AppIconPickerSection(
  modifier: Modifier = Modifier,
  reducedMotion: Boolean = false,
  onActiveIconChanged: ((OrbiAppIcon) -> Unit)? = null
) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  val accentColor = MaterialTheme.colorScheme.primary
  val isLight = MaterialTheme.colorScheme.background.luminance() > 0.5f
  val isAmoled = MaterialTheme.colorScheme.background.luminance() < 0.005f

  var activeIcon by remember {
    mutableStateOf(IconManager.getCurrentIcon(context))
  }
  var pendingConfirmIcon by remember {
    mutableStateOf<OrbiAppIcon?>(null)
  }
  var tappedIcon by remember {
    mutableStateOf<OrbiAppIcon?>(null)
  }
  var showAppliedBannerFor by remember {
    mutableStateOf<OrbiAppIcon?>(null)
  }

  // Re-read real state from PackageManager on resume so picker always shows true choice
  DisposableEffect(lifecycleOwner, context) {
    val observer = LifecycleEventObserver { _, event ->
      if (event == Lifecycle.Event.ON_RESUME) {
        val current = IconManager.getCurrentIcon(context)
        activeIcon = current
        onActiveIconChanged?.invoke(current)
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose {
      lifecycleOwner.lifecycle.removeObserver(observer)
    }
  }

  // Clear temporary tap scale-up after sheet opens
  LaunchedEffect(tappedIcon) {
    if (tappedIcon != null) {
      delay(160)
      pendingConfirmIcon = tappedIcon
      delay(220)
      tappedIcon = null
    }
  }

  // Hide celebratory Orbi Happy banner after 3.4 seconds
  LaunchedEffect(showAppliedBannerFor) {
    if (showAppliedBannerFor != null) {
      delay(3400)
      showAppliedBannerFor = null
    }
  }

  Column(
    modifier = modifier
      .fillMaxWidth()
      .testTag("settings_app_icon_picker_section"),
    verticalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
          text = "App icon",
          style = MaterialTheme.typography.titleMedium.copy(
            fontWeight = FontWeight.Bold,
            lineHeight = 24.sp
          ),
          color = MaterialTheme.colorScheme.onSurface
        )
        Text(
          text = "Pick your Orbi launcher icon on the home screen",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }

      Surface(
        shape = CircleShape,
        color = accentColor.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.32f))
      ) {
        Text(
          text = activeIcon.displayName,
          style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
          color = accentColor,
          modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        )
      }
    }

    // Celebratory Orbi Happy animation banner after applying a new icon
    AnimatedVisibility(
      visible = showAppliedBannerFor != null,
      enter = fadeIn(tween(280, easing = OrbiEasing)) + scaleIn(tween(280, easing = OrbiEasing), initialScale = 0.92f),
      exit = fadeOut(tween(220, easing = OrbiEasing)) + scaleOut(tween(220, easing = OrbiEasing), targetScale = 0.94f)
    ) {
      val applied = showAppliedBannerFor ?: activeIcon
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(20.dp))
          .background(Color(0xFF22C55E).copy(alpha = if (isLight) 0.14f else 0.18f))
          .border(1.dp, Color(0xFF22C55E).copy(alpha = 0.45f), RoundedCornerShape(20.dp))
          .padding(horizontal = 14.dp, vertical = 10.dp)
          .testTag("app_icon_changed_happy_banner"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        OrbiView(
          state = OrbiState.Happy,
          size = 54.dp,
          runner = OrbiRunner.None,
          reducedMotion = reducedMotion
        )
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "Icon changed to ${applied.displayName}",
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = if (isLight) Color(0xFF15803D) else Color(0xFF34D399)
          )
          Text(
            text = "Orbi updated your home screen launcher alias.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }
    }

    // 5 Rounded-Square Previews Row with Name under each, Accent Ring & Check Badge on selected
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.Top
    ) {
      OrbiAppIcon.entries.forEach { icon ->
        val isSelected = icon == activeIcon
        val isTapped = icon == tappedIcon
        val interactionSource = remember { MutableInteractionSource() }
        val isPressed by interactionSource.collectIsPressedAsState()

        val scale by animateFloatAsState(
          targetValue = when {
            isTapped -> 1.10f
            isPressed -> 1.07f
            isSelected -> 1.02f
            else -> 1f
          },
          animationSpec = if (reducedMotion) snap() else tween(220, easing = OrbiEasing),
          label = "icon_preview_scale_${icon.id}"
        )

        Column(
          modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(18.dp))
            .clickable(
              interactionSource = interactionSource,
              indication = null
            ) {
              if (icon != activeIcon) {
                tappedIcon = icon
              } else {
                // Still allow opening confirmation preview sheet if tapped
                tappedIcon = icon
              }
            }
            .padding(vertical = 4.dp)
            .testTag("app_icon_option_${icon.id}"),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          Box(
            modifier = Modifier
              .size(62.dp)
              .graphicsLayer {
                scaleX = scale
                scaleY = scale
              },
            contentAlignment = Alignment.Center
          ) {
            // Outer accent ring when selected
            val ringShape = RoundedCornerShape(20.dp)
            Box(
              modifier = Modifier
                .size(58.dp)
                .then(
                  if (isSelected) {
                    Modifier
                      .border(2.2.dp, accentColor, ringShape)
                      .padding(3.5.dp)
                  } else {
                    Modifier.padding(2.dp)
                  }
                ),
              contentAlignment = Alignment.Center
            ) {
              OrbiLauncherIconTile(
                icon = icon,
                size = 51.dp,
                cornerRadius = 15.dp,
                animated = isSelected,
                reducedMotion = reducedMotion
              )
            }

            // Check badge in top-right corner of selected icon
            if (isSelected) {
              Box(
                modifier = Modifier
                  .align(Alignment.TopEnd)
                  .size(20.dp)
                  .clip(CircleShape)
                  .background(accentColor)
                  .border(
                    1.5.dp,
                    if (isLight) Color.White else Color(0xFF0E1320),
                    CircleShape
                  )
                  .testTag("app_icon_selected_badge_${icon.id}"),
                contentAlignment = Alignment.Center
              ) {
                Icon(
                  imageVector = Icons.Rounded.Check,
                  contentDescription = "Selected icon",
                  tint = Color.White,
                  modifier = Modifier.size(12.dp)
                )
              }
            }
          }

          Text(
            text = icon.displayName,
            style = MaterialTheme.typography.labelSmall.copy(
              fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
              fontSize = 11.5.sp
            ),
            color = if (isSelected) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
          )
        }
      }
    }

    // Informative launcher note
    val noteBg = when {
      isLight -> Color(0xFFEFF4FC)
      isAmoled -> Color(0xFF0A0D14)
      else -> Color(0xFF0E1320).copy(alpha = 0.72f)
    }
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(14.dp))
        .background(noteBg)
        .border(
          1.dp,
          MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f),
          RoundedCornerShape(14.dp)
        )
        .padding(horizontal = 12.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      Icon(
        imageVector = Icons.Rounded.Info,
        contentDescription = null,
        tint = accentColor.copy(alpha = 0.85f),
        modifier = Modifier.size(16.dp)
      )
      Text(
        text = "Your home screen may take a few seconds to update. Some launchers close the app when the icon changes.",
        style = MaterialTheme.typography.bodySmall.copy(
          fontSize = 11.5.sp,
          lineHeight = 16.sp
        ),
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
  }

  // Confirm bottom sheet: "Change app icon to Midnight?" with Apply and Cancel
  val candidate = pendingConfirmIcon
  if (candidate != null) {
    ConfirmAppIconBottomSheet(
      targetIcon = candidate,
      currentIcon = activeIcon,
      reducedMotion = reducedMotion,
      onApply = {
        val ok = IconManager.applyIcon(context, candidate)
        if (ok) {
          activeIcon = candidate
          onActiveIconChanged?.invoke(candidate)
          showAppliedBannerFor = candidate
          Toast.makeText(context, "Icon changed", Toast.LENGTH_SHORT).show()
        }
        pendingConfirmIcon = null
      },
      onCancel = {
        pendingConfirmIcon = null
      }
    )
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfirmAppIconBottomSheet(
  targetIcon: OrbiAppIcon,
  currentIcon: OrbiAppIcon,
  reducedMotion: Boolean = false,
  onApply: () -> Unit,
  onCancel: () -> Unit
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  val accentColor = MaterialTheme.colorScheme.primary
  val isLight = MaterialTheme.colorScheme.background.luminance() > 0.5f
  val isAmoled = MaterialTheme.colorScheme.background.luminance() < 0.005f

  val sheetBg = when {
    isLight -> Color(0xFFF8FAFF)
    isAmoled -> Color(0xFF06080E)
    else -> Color(0xFF0E1220)
  }

  val popScale = remember { Animatable(if (reducedMotion) 1f else 0.86f) }
  LaunchedEffect(targetIcon, reducedMotion) {
    if (!reducedMotion) {
      popScale.animateTo(1f, tween(340, easing = OrbiEasing))
    }
  }

  ModalBottomSheet(
    onDismissRequest = onCancel,
    sheetState = sheetState,
    containerColor = sheetBg,
    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 24.dp)
        .padding(bottom = 28.dp)
        .testTag("confirm_app_icon_bottom_sheet"),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
      Box(
        modifier = Modifier.graphicsLayer {
          scaleX = popScale.value
          scaleY = popScale.value
        }
      ) {
        OrbiLauncherIconTile(
          icon = targetIcon,
          size = 88.dp,
          cornerRadius = 24.dp,
          animated = true,
          reducedMotion = reducedMotion
        )
      }

      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        Text(
          text = "Change app icon to ${targetIcon.displayName}?",
          style = MaterialTheme.typography.titleLarge.copy(
            fontWeight = FontWeight.ExtraBold,
            fontSize = 21.sp
          ),
          color = MaterialTheme.colorScheme.onSurface,
          textAlign = TextAlign.Center
        )
        Text(
          text = targetIcon.subtitle,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          textAlign = TextAlign.Center
        )
      }

      Text(
        text = "Your home screen may take a few seconds to update. Some launchers close the app when the icon changes.",
        style = MaterialTheme.typography.bodySmall.copy(lineHeight = 17.sp),
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
        textAlign = TextAlign.Center
      )

      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        OutlinedButton(
          onClick = onCancel,
          shape = RoundedCornerShape(999.dp),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.20f)),
          modifier = Modifier
            .weight(1f)
            .height(48.dp)
            .testTag("confirm_app_icon_cancel_button")
        ) {
          Text(
            text = "Cancel",
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface
          )
        }

        Button(
          onClick = onApply,
          shape = RoundedCornerShape(999.dp),
          colors = ButtonDefaults.buttonColors(
            containerColor = accentColor,
            contentColor = Color.White
          ),
          modifier = Modifier
            .weight(1f)
            .height(48.dp)
            .testTag("confirm_app_icon_apply_button")
        ) {
          Text(
            text = if (targetIcon == currentIcon) "Keep ${targetIcon.displayName}" else "Apply",
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
          )
        }
      }
    }
  }
}

@Preview(name = "App Icon Picker - Dark Theme", showBackground = true, backgroundColor = 0xFF0E1320, widthDp = 412)
@Composable
private fun PreviewAppIconPickerDark() {
  MaterialTheme {
    Box(modifier = Modifier.padding(18.dp)) {
      AppIconPickerSection()
    }
  }
}

@Preview(name = "App Icon Picker - Light Theme", showBackground = true, backgroundColor = 0xFFF3F6FC, widthDp = 412)
@Composable
private fun PreviewAppIconPickerLight() {
  MaterialTheme {
    Box(modifier = Modifier.padding(18.dp)) {
      AppIconPickerSection()
    }
  }
}
