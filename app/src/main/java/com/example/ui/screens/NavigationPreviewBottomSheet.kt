package com.example.ui.screens

import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.R
import com.example.model.Song

private enum class NavPreviewBackdrop {
  WARM,
  ART,
  DARK
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NavigationPreviewBottomSheet(
  currentNavStyle: String,
  currentSong: Song?,
  darkTheme: Boolean,
  reducedMotion: Boolean,
  onApplyNavStyle: (String) -> Unit,
  onDismiss: () -> Unit
) {
  var previewStyle by remember(currentNavStyle) {
    // When opening preview, default to showing Liquid Blur Split if user hasn't switched yet,
    // or show currently active style if already on blur so they can inspect it immediately.
    mutableStateOf(
      if (currentNavStyle == AurixNavStyle.CLASSIC.id) AurixNavStyle.BLUR.id else currentNavStyle
    )
  }
  var previewTabIndex by remember { mutableIntStateOf(0) }
  var backdropMode by remember(currentSong?.id) {
    mutableStateOf(if (currentSong?.artworkUrl?.isNotBlank() == true) NavPreviewBackdrop.ART else NavPreviewBackdrop.WARM)
  }

  val previewTabs = listOf(
    TabItem(stringResource(id = R.string.nav_home), Icons.Rounded.Home),
    TabItem(stringResource(id = R.string.nav_search), Icons.Rounded.Search),
    TabItem(stringResource(id = R.string.nav_library), Icons.Rounded.Folder),
    TabItem(stringResource(id = R.string.nav_settings), Icons.Rounded.Settings)
  )

  val songColors = currentSong?.let { rememberArtworkColors(it.artworkUrl, it.title) }
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 20.dp, vertical = 10.dp)
        .windowInsetsPadding(WindowInsets.navigationBars),
      verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
      // Header row with explicit Close (X) button
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        Surface(
          shape = CircleShape,
          color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
          modifier = Modifier.size(44.dp)
        ) {
          Box(contentAlignment = Alignment.Center) {
            Icon(
              imageVector = Icons.Rounded.BlurOn,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(22.dp)
            )
          }
        }
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = stringResource(id = R.string.nav_preview_sheet_title),
            style = MaterialTheme.typography.titleLarge.copy(
              fontWeight = FontWeight.Bold,
              fontSize = 20.sp,
              lineHeight = 26.sp
            ),
            color = MaterialTheme.colorScheme.onSurface
          )
          Text(
            text = stringResource(id = R.string.nav_preview_sheet_subtitle),
            style = MaterialTheme.typography.bodySmall.copy(lineHeight = 17.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
        IconButton(
          onClick = onDismiss,
          modifier = Modifier
            .minimumInteractiveComponentSize()
            .testTag("nav_preview_close_button")
        ) {
          Icon(
            imageVector = Icons.Rounded.Close,
            contentDescription = stringResource(id = R.string.cd_close_sheet),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }

      // Navigation Style Option Cards (Liquid Blur vs Classic Glass)
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        NavStyleOptionCard(
          title = stringResource(id = R.string.nav_style_blur),
          description = stringResource(id = R.string.nav_blur_desc),
          icon = Icons.Rounded.BlurCircular,
          isSelected = previewStyle == AurixNavStyle.BLUR.id,
          isActiveApplied = currentNavStyle == AurixNavStyle.BLUR.id,
          modifier = Modifier
            .weight(1f)
            .testTag("nav_preview_option_blur"),
          onClick = { previewStyle = AurixNavStyle.BLUR.id }
        )
        NavStyleOptionCard(
          title = stringResource(id = R.string.nav_style_classic),
          description = stringResource(id = R.string.nav_classic_desc),
          icon = Icons.Rounded.ViewAgenda,
          isSelected = previewStyle == AurixNavStyle.CLASSIC.id,
          isActiveApplied = currentNavStyle == AurixNavStyle.CLASSIC.id,
          modifier = Modifier
            .weight(1f)
            .testTag("nav_preview_option_classic"),
          onClick = { previewStyle = AurixNavStyle.CLASSIC.id }
        )
      }

      // Live Interactive Preview Stage
      val stageDark = when (backdropMode) {
        NavPreviewBackdrop.WARM -> false
        NavPreviewBackdrop.DARK -> true
        NavPreviewBackdrop.ART -> true
      }
      val previewAmbient = when (backdropMode) {
        NavPreviewBackdrop.WARM -> Color(0xFFF59E0B) to Color(0xFFFDE68A)
        NavPreviewBackdrop.ART -> songColors ?: (Color(0xFF007AFF) to Color(0xFF8B5CF6))
        NavPreviewBackdrop.DARK -> Color(0xFF38BDF8) to Color(0xFFA855F7)
      }

      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
          text = stringResource(id = R.string.nav_preview_stage_label),
          style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
          color = MaterialTheme.colorScheme.onSurface
        )
        // Backdrop mode switcher chips (scrollable for Khmer & narrow screens)
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          if (currentSong?.artworkUrl?.isNotBlank() == true) {
            BackdropModeChip(
              label = stringResource(id = R.string.nav_preview_backdrop_art),
              selected = backdropMode == NavPreviewBackdrop.ART,
              onClick = { backdropMode = NavPreviewBackdrop.ART }
            )
          }
          BackdropModeChip(
            label = stringResource(id = R.string.nav_preview_backdrop_warm),
            selected = backdropMode == NavPreviewBackdrop.WARM,
            onClick = { backdropMode = NavPreviewBackdrop.WARM }
          )
          BackdropModeChip(
            label = stringResource(id = R.string.nav_preview_backdrop_dark),
            selected = backdropMode == NavPreviewBackdrop.DARK,
            onClick = { backdropMode = NavPreviewBackdrop.DARK }
          )
        }

        Box(
          modifier = Modifier
            .fillMaxWidth()
            .height(210.dp)
            .clip(RoundedCornerShape(26.dp))
            .border(
              1.dp,
              MaterialTheme.colorScheme.onSurface.copy(alpha = if (darkTheme) 0.14f else 0.08f),
              RoundedCornerShape(26.dp)
            )
            .testTag("nav_preview_stage")
        ) {
          // Organic blurred background behind the preview navigation bar
          when (backdropMode) {
            NavPreviewBackdrop.ART -> {
              if (currentSong?.artworkUrl?.isNotBlank() == true) {
                val navPreviewReq = com.example.ui.image.rememberCrispArtworkRequest(model = currentSong.artworkUrl)
                AsyncImage(
                  model = navPreviewReq,
                  contentDescription = null,
                  contentScale = ContentScale.Crop,
                  filterQuality = com.example.ui.image.AurixImagePipeline.ARTWORK_FILTER_QUALITY,
                  modifier = Modifier
                    .matchParentSize()
                    .then(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Modifier.blur(32.dp) else Modifier)
                )
                Box(
                  modifier = Modifier
                    .matchParentSize()
                    .background(
                      Brush.verticalGradient(
                        listOf(
                          previewAmbient.first.copy(alpha = 0.25f),
                          Color(0xFF0A0E1A).copy(alpha = 0.56f)
                        )
                      )
                    )
                )
              } else {
                PreviewOrbBackdrop(
                  primary = previewAmbient.first,
                  secondary = previewAmbient.second,
                  baseColor = Color(0xFF0C1120)
                )
              }
            }
            NavPreviewBackdrop.WARM -> {
              PreviewOrbBackdrop(
                primary = Color(0xFFD97706),
                secondary = Color(0xFFFCD34D),
                baseColor = Color(0xFFF3ECE2)
              )
            }
            NavPreviewBackdrop.DARK -> {
              PreviewOrbBackdrop(
                primary = Color(0xFF1D4ED8),
                secondary = Color(0xFF7E22CE),
                baseColor = Color(0xFF0A0F1E)
              )
            }
          }

          // Top status indicator showing live tapped tab inside the preview
          val activeTab = previewTabs[previewTabIndex.coerceIn(0, previewTabs.lastIndex)]
          Surface(
            shape = CircleShape,
            color = if (stageDark) Color.Black.copy(alpha = 0.42f) else Color.White.copy(alpha = 0.72f),
            border = BorderStroke(
              1.dp,
              if (stageDark) Color.White.copy(alpha = 0.18f) else Color.Black.copy(alpha = 0.08f)
            ),
            modifier = Modifier
              .align(Alignment.TopCenter)
              .padding(top = 18.dp)
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              Icon(
                imageVector = activeTab.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
              )
              Text(
                text = stringResource(id = R.string.nav_preview_selected_tab, activeTab.label),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = if (stageDark) Color.White else Color(0xFF1E293B),
                maxLines = 1
              )
            }
          }

          // Live interactive Navigation Bar inside the preview stage
          Box(
            modifier = Modifier
              .align(Alignment.BottomCenter)
              .fillMaxWidth()
              .padding(bottom = 20.dp),
            contentAlignment = Alignment.Center
          ) {
            AnimatedContent(
              targetState = previewStyle,
              transitionSpec = {
                if (reducedMotion) fadeIn(snap()) togetherWith fadeOut(snap())
                else fadeIn(tween(220)) togetherWith fadeOut(tween(180))
              },
              label = "nav_preview_style_swap"
            ) { style ->
              if (style == AurixNavStyle.BLUR.id) {
                LiquidBlurSplitTabBar(
                  tabs = previewTabs,
                  selectedIndex = previewTabIndex,
                  onSelect = { previewTabIndex = it },
                  reducedMotion = reducedMotion,
                  ambientColors = previewAmbient,
                  darkThemeOverride = stageDark
                )
              } else {
                LiquidGlassTabBar(
                  tabs = previewTabs,
                  selectedIndex = previewTabIndex,
                  onSelect = { previewTabIndex = it },
                  reducedMotion = reducedMotion,
                  ambientColors = previewAmbient,
                  darkThemeOverride = stageDark
                )
              }
            }
          }
        }
      }

      // Action Buttons: Close & Apply
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        OutlinedButton(
          onClick = onDismiss,
          modifier = Modifier
            .weight(0.9f)
            .height(50.dp)
            .testTag("nav_preview_cancel_button"),
          shape = RoundedCornerShape(16.dp)
        ) {
          Text(
            text = stringResource(id = R.string.action_close),
            fontWeight = FontWeight.SemiBold
          )
        }
        Button(
          onClick = {
            onApplyNavStyle(previewStyle)
            onDismiss()
          },
          modifier = Modifier
            .weight(1.4f)
            .height(50.dp)
            .testTag("nav_preview_apply_button"),
          shape = RoundedCornerShape(16.dp)
        ) {
          Icon(
            imageVector = Icons.Rounded.Check,
            contentDescription = null,
            modifier = Modifier.size(18.dp)
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = stringResource(id = R.string.nav_preview_apply),
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }
      }

      Spacer(modifier = Modifier.height(6.dp))
    }
  }
}

@Composable
private fun NavStyleOptionCard(
  title: String,
  description: String,
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  isSelected: Boolean,
  isActiveApplied: Boolean,
  modifier: Modifier = Modifier,
  onClick: () -> Unit
) {
  val primary = MaterialTheme.colorScheme.primary
  Surface(
    shape = RoundedCornerShape(20.dp),
    color = if (isSelected) primary.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
    border = BorderStroke(
      width = if (isSelected) 1.5.dp else 1.dp,
      color = if (isSelected) primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
    ),
    modifier = modifier
      .clip(RoundedCornerShape(20.dp))
      .clickable { onClick() }
  ) {
    Column(
      modifier = Modifier.padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          tint = if (isSelected) primary else MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.size(20.dp)
        )
        if (isActiveApplied) {
          Surface(
            shape = CircleShape,
            color = primary.copy(alpha = 0.18f)
          ) {
            Text(
              text = stringResource(id = R.string.nav_preview_applied_badge),
              style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
              color = primary,
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
            )
          }
        }
      }
      Text(
        text = title,
        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
      Text(
        text = description,
        style = MaterialTheme.typography.bodySmall.copy(lineHeight = 16.sp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
      )
    }
  }
}

@Composable
private fun BackdropModeChip(
  label: String,
  selected: Boolean,
  onClick: () -> Unit
) {
  Surface(
    shape = CircleShape,
    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
    modifier = Modifier
      .heightIn(min = 38.dp)
      .clip(CircleShape)
      .clickable { onClick() }
  ) {
    Box(
      modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
      contentAlignment = Alignment.Center
    ) {
      Text(
        text = label,
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1
      )
    }
  }
}

@Composable
private fun BoxScope.PreviewOrbBackdrop(
  primary: Color,
  secondary: Color,
  baseColor: Color
) {
  Box(
    modifier = Modifier
      .matchParentSize()
      .background(baseColor)
  )
  Box(
    modifier = Modifier
      .matchParentSize()
      .then(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Modifier.blur(24.dp) else Modifier)
      .background(
        Brush.radialGradient(
          colors = listOf(primary.copy(alpha = 0.65f), Color.Transparent),
          center = Offset(720f, 380f),
          radius = 650f
        )
      )
      .background(
        Brush.radialGradient(
          colors = listOf(secondary.copy(alpha = 0.55f), Color.Transparent),
          center = Offset(180f, 140f),
          radius = 520f
        )
      )
  )
}
