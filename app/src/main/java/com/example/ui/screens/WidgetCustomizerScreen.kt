package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Crop
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.widget.PhotoOperationResult
import com.example.widget.WidgetBgType
import com.example.widget.WidgetContrastReport
import com.example.widget.WidgetCustomizerConfig
import com.example.widget.WidgetFrameMotion
import com.example.widget.WidgetFrameStyle
import com.example.widget.WidgetPhotoSource
import com.example.widget.WidgetPhotoStorage
import com.example.widget.WidgetSizeTier
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun WidgetCustomizerScreen(
  initialConfig: WidgetCustomizerConfig,
  artworkUrl: String?,
  songTitle: String,
  songArtist: String,
  currentLyricLine: String,
  nextLyricLine: String,
  hasLyrics: Boolean,
  reducedMotion: Boolean,
  developerModeEnabled: Boolean,
  onSaveConfig: (WidgetCustomizerConfig) -> Unit,
  onResetConfig: () -> Unit,
  onRebuildWidgetBitmap: suspend () -> Long,
  onClose: () -> Unit
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val systemNoAnim = remember(context) { isSystemRemoveAnimationsEnabled(context) }
  val motionDisabled = reducedMotion || systemNoAnim

  var draft by remember(initialConfig) { mutableStateOf(initialConfig.normalized()) }
  var previewSizeTier by remember { mutableStateOf(WidgetSizeTier.WIDE) }
  var contrastReport by remember { mutableStateOf(WidgetContrastReport(0.08f, 12f, false)) }
  var photoErrorMsg by remember { mutableStateOf<String?>(null) }
  var showCropDialog by remember { mutableStateOf(false) }
  var showUnsavedDialog by remember { mutableStateOf(false) }
  var showResetDialog by remember { mutableStateOf(false) }
  var hasDiskPhotoChanges by remember { mutableStateOf(false) }

  val hasUnsavedChanges = remember(draft, initialConfig, hasDiskPhotoChanges) {
    !draft.contentEqualsIgnoringRevision(initialConfig) || hasDiskPhotoChanges
  }

  val albumArtBitmap = rememberSongAlbumArtBitmap(artworkUrl)
  val hasCroppedBg = remember(draft.photoRevision) { WidgetPhotoStorage.hasBgCroppedPhoto(context) }
  val hasBgOriginal = remember(draft.photoRevision) { WidgetPhotoStorage.hasBgOriginalPhoto(context) }

  val handleBackAttempt: () -> Unit = {
    if (hasUnsavedChanges) {
      showUnsavedDialog = true
    } else {
      onClose()
    }
  }

  BackHandler(onBack = handleBackAttempt)

  val handlePickedFrameUri: (android.net.Uri?) -> Unit = { uri ->
    if (uri != null) {
      scope.launch {
        when (val res = WidgetPhotoStorage.importFramePhoto(context, uri)) {
          is PhotoOperationResult.Success -> {
            photoErrorMsg = null
            hasDiskPhotoChanges = true
            draft = draft.copy(
              frameSource = WidgetPhotoSource.CUSTOM,
              photoRevision = System.currentTimeMillis()
            )
            Toast.makeText(context, context.getString(R.string.wc_photo_saved_toast), Toast.LENGTH_SHORT).show()
          }
          is PhotoOperationResult.Error -> {
            photoErrorMsg = if (res.isOutOfMemory) {
              context.getString(R.string.wc_photo_error_oom)
            } else {
              context.getString(R.string.wc_photo_error_generic)
            }
          }
        }
      }
    }
  }

  val handlePickedBgUri: (android.net.Uri?) -> Unit = { uri ->
    if (uri != null) {
      scope.launch {
        when (val res = WidgetPhotoStorage.importBackgroundPhoto(context, uri)) {
          is PhotoOperationResult.Success -> {
            photoErrorMsg = null
            hasDiskPhotoChanges = true
            draft = draft.copy(
              samePhoto = false,
              bgSource = WidgetPhotoSource.CUSTOM,
              photoRevision = System.currentTimeMillis()
            )
            Toast.makeText(context, context.getString(R.string.wc_photo_saved_toast), Toast.LENGTH_SHORT).show()
          }
          is PhotoOperationResult.Error -> {
            photoErrorMsg = if (res.isOutOfMemory) {
              context.getString(R.string.wc_photo_error_oom)
            } else {
              context.getString(R.string.wc_photo_error_generic)
            }
          }
        }
      }
    }
  }

  // Zero-permission Android Photo Picker launchers with automatic GetContent("image/*") fallback
  val framePhotoPicker = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.PickVisualMedia(),
    onResult = handlePickedFrameUri
  )
  val framePhotoFallbackPicker = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.GetContent(),
    onResult = handlePickedFrameUri
  )

  val bgPhotoPicker = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.PickVisualMedia(),
    onResult = handlePickedBgUri
  )
  val bgPhotoFallbackPicker = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.GetContent(),
    onResult = handlePickedBgUri
  )

  val launchFramePicker: () -> Unit = {
    val launched = runCatching {
      framePhotoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
      true
    }.getOrDefault(false)
    if (!launched) {
      runCatching { framePhotoFallbackPicker.launch("image/*") }
        .onFailure { photoErrorMsg = context.getString(R.string.wc_photo_error_generic) }
    }
  }

  val launchBgPicker: () -> Unit = {
    val launched = runCatching {
      bgPhotoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
      true
    }.getOrDefault(false)
    if (!launched) {
      runCatching { bgPhotoFallbackPicker.launch("image/*") }
        .onFailure { photoErrorMsg = context.getString(R.string.wc_photo_error_generic) }
    }
  }

  Surface(
    modifier = Modifier.fillMaxSize(),
    color = MaterialTheme.colorScheme.background
  ) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .windowInsetsPadding(WindowInsets.statusBars)
    ) {
      // Top Bar
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        IconButton(
          onClick = handleBackAttempt,
          modifier = Modifier.testTag("wc_back_button")
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
            contentDescription = stringResource(R.string.wc_back_cd),
            tint = MaterialTheme.colorScheme.onBackground
          )
        }
        Text(
          text = stringResource(R.string.wc_title),
          style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
          color = MaterialTheme.colorScheme.onBackground,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f)
        )

        // Size preview selector chips
        Row(
          horizontalArrangement = Arrangement.spacedBy(4.dp),
          modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .padding(3.dp)
        ) {
          listOf(
            WidgetSizeTier.SMALL to R.string.wc_size_small,
            WidgetSizeTier.WIDE to R.string.wc_size_wide,
            WidgetSizeTier.LARGE to R.string.wc_size_large
          ).forEach { (tier, labelRes) ->
            val selected = previewSizeTier == tier
            Box(
              modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(
                  if (selected) Color(0xFF43C7F4) else Color.Transparent
                )
                .clickable { previewSizeTier = tier }
                .padding(horizontal = 8.dp, vertical = 5.dp)
                .testTag("wc_size_${tier.id}")
            ) {
              Text(
                text = stringResource(labelRes),
                color = if (selected) Color(0xFF080C14) else MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
              )
            }
          }
        }
      }

      // Pinned Live Widget Preview
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        LiveWidgetPreviewCard(
          config = draft,
          sizeTier = previewSizeTier,
          albumArtBitmap = albumArtBitmap,
          songTitle = songTitle,
          songArtist = songArtist,
          currentLyricLine = currentLyricLine,
          nextLyricLine = nextLyricLine,
          hasLyrics = hasLyrics,
          reducedMotion = reducedMotion,
          onContrastReport = { contrastReport = it },
          modifier = Modifier.testTag("wc_live_preview_card")
        )

        // Contrast readability warning banner when background is bright
        AnimatedVisibility(visible = contrastReport.isLowContrast) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(14.dp))
              .background(Color(0x33F59E0B))
              .border(1.dp, Color(0x88F59E0B), RoundedCornerShape(14.dp))
              .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Icon(
              imageVector = Icons.Rounded.Warning,
              contentDescription = null,
              tint = Color(0xFFFBBF24),
              modifier = Modifier.size(18.dp)
            )
            Text(
              text = stringResource(R.string.wc_contrast_warning),
              color = Color.White,
              fontSize = 11.5.sp,
              lineHeight = 15.sp,
              modifier = Modifier.weight(1f)
            )
            TextButton(
              onClick = { draft = draft.copy(darkness = (draft.darkness + 25).coerceAtMost(100)) },
              contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
              modifier = Modifier.testTag("wc_auto_darken_btn")
            ) {
              Text(
                text = stringResource(R.string.wc_contrast_fix_button),
                color = Color(0xFFFBBF24),
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold
              )
            }
          }
        }

        // Photo error banner (OOM / invalid image)
        AnimatedVisibility(visible = photoErrorMsg != null) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(14.dp))
              .background(Color(0x33EF4444))
              .border(1.dp, Color(0x88EF4444), RoundedCornerShape(14.dp))
              .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Icon(
              imageVector = Icons.Rounded.Warning,
              contentDescription = null,
              tint = Color(0xFFEF4444),
              modifier = Modifier.size(18.dp)
            )
            Text(
              text = photoErrorMsg.orEmpty(),
              color = Color.White,
              fontSize = 12.sp,
              modifier = Modifier.weight(1f)
            )
          }
        }
      }

      // Scrollable Controls
      LazyColumn(
        modifier = Modifier
          .weight(1f)
          .fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
      ) {
        // 1. PHOTOS CARD
        item {
          CustomizerSectionCard(
            title = stringResource(R.string.wc_section_photos),
            icon = Icons.Rounded.PhotoLibrary
          ) {
            // Same photo / Different photos segmented toggle
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White.copy(alpha = 0.06f))
                .padding(4.dp),
              horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
              CustomizerSegmentPill(
                label = stringResource(R.string.wc_same_photo_mode),
                selected = draft.samePhoto,
                onClick = { draft = draft.copy(samePhoto = true) },
                modifier = Modifier
                  .weight(1f)
                  .testTag("wc_mode_same_photo")
              )
              CustomizerSegmentPill(
                label = stringResource(R.string.wc_diff_photo_mode),
                selected = !draft.samePhoto,
                onClick = { draft = draft.copy(samePhoto = false) },
                modifier = Modifier
                  .weight(1f)
                  .testTag("wc_mode_diff_photo")
              )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Frame photo row
            PhotoSourceRow(
              title = stringResource(R.string.wc_frame_photo_label),
              subtitle = if (draft.frameSource == WidgetPhotoSource.ALBUM_ART) {
                stringResource(R.string.wc_using_album_art)
              } else {
                stringResource(R.string.wc_using_custom_photo)
              },
              isAlbumArt = draft.frameSource == WidgetPhotoSource.ALBUM_ART,
              onPickPhoto = launchFramePicker,
              onUseAlbumArt = {
                draft = draft.copy(
                  frameSource = WidgetPhotoSource.ALBUM_ART,
                  photoRevision = System.currentTimeMillis()
                )
              },
              pickTag = "wc_pick_frame_photo",
              albumTag = "wc_frame_use_album_art"
            )

            // Conditional Background photo row when "Different photos" is enabled
            AnimatedVisibility(visible = !draft.samePhoto) {
              Column {
                Spacer(modifier = Modifier.height(10.dp))
                PhotoSourceRow(
                  title = stringResource(R.string.wc_bg_photo_label),
                  subtitle = when {
                    draft.bgSource == WidgetPhotoSource.ALBUM_ART -> stringResource(R.string.wc_using_album_art)
                    hasBgOriginal || hasCroppedBg -> stringResource(R.string.wc_using_custom_photo)
                    else -> stringResource(R.string.wc_using_frame_fallback)
                  },
                  isAlbumArt = draft.bgSource == WidgetPhotoSource.ALBUM_ART,
                  onPickPhoto = launchBgPicker,
                  onUseAlbumArt = {
                    draft = draft.copy(
                      bgSource = WidgetPhotoSource.ALBUM_ART,
                      photoRevision = System.currentTimeMillis()
                    )
                  },
                  pickTag = "wc_pick_bg_photo",
                  albumTag = "wc_bg_use_album_art"
                )

                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(
                  onClick = {
                    scope.launch {
                      WidgetPhotoStorage.swapCustomPhotos(context)
                      hasDiskPhotoChanges = true
                      draft = draft.copy(
                        frameSource = draft.bgSource,
                        bgSource = draft.frameSource,
                        photoRevision = System.currentTimeMillis()
                      )
                      Toast.makeText(context, context.getString(R.string.wc_swapped_toast), Toast.LENGTH_SHORT).show()
                    }
                  },
                  shape = RoundedCornerShape(14.dp),
                  modifier = Modifier
                    .fillMaxWidth()
                    .testTag("wc_swap_photos_btn")
                ) {
                  Icon(Icons.Rounded.SwapHoriz, contentDescription = null, modifier = Modifier.size(18.dp))
                  Spacer(modifier = Modifier.width(6.dp))
                  Text(stringResource(R.string.wc_swap_photos), fontWeight = FontWeight.SemiBold)
                }
              }
            }
          }
        }

        // 2. FRAME STYLE CARD
        item {
          CustomizerSectionCard(
            title = stringResource(R.string.wc_section_frame),
            icon = Icons.Rounded.AutoAwesome
          ) {
            // 9 Frame Style pills
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              WidgetFrameStyle.entries.forEach { style ->
                val selected = draft.frameStyle == style
                CustomizerChoiceChip(
                  label = stringResource(frameStyleLabelRes(style)),
                  selected = selected,
                  onClick = { draft = draft.copy(frameStyle = style) },
                  modifier = Modifier.testTag("wc_frame_style_${style.id}")
                )
              }
            }

            AnimatedVisibility(visible = draft.frameStyle != WidgetFrameStyle.NONE) {
              Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Spacer(modifier = Modifier.height(6.dp))

                // 6 Accent colors
                Text(
                  text = stringResource(R.string.wc_accent_color),
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  fontSize = 12.5.sp,
                  fontWeight = FontWeight.SemiBold
                )
                ColorSwatchRow(
                  hexColors = WidgetCustomizerConfig.FRAME_ACCENT_COLORS,
                  selectedHex = draft.frameColorHex,
                  onSelect = { draft = draft.copy(frameColorHex = it) },
                  tagPrefix = "wc_frame_color"
                )

                // Corner radius slider (0-50)
                CustomizerSliderRow(
                  label = stringResource(R.string.wc_corner_radius),
                  valueText = "${draft.cornerRadius}",
                  value = draft.cornerRadius.toFloat(),
                  valueRange = 0f..50f,
                  onValueChange = { draft = draft.copy(cornerRadius = it.roundToInt()) },
                  testTag = "wc_corner_slider"
                )

                // Border width slider (0-10)
                CustomizerSliderRow(
                  label = stringResource(R.string.wc_border_width),
                  valueText = "${draft.borderWidth}",
                  value = draft.borderWidth.toFloat(),
                  valueRange = 0f..10f,
                  onValueChange = { draft = draft.copy(borderWidth = it.roundToInt()) },
                  testTag = "wc_border_slider"
                )

                // Frame motion selector (Still, Float, Spin, Pulse)
                Text(
                  text = stringResource(R.string.wc_frame_motion),
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  fontSize = 12.5.sp,
                  fontWeight = FontWeight.SemiBold
                )
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                  WidgetFrameMotion.entries.forEach { motion ->
                    CustomizerChoiceChip(
                      label = stringResource(frameMotionLabelRes(motion)),
                      selected = draft.motion == motion,
                      onClick = { draft = draft.copy(motion = motion) },
                      modifier = Modifier
                        .weight(1f)
                        .testTag("wc_motion_${motion.id}")
                    )
                  }
                }
                Text(
                  text = if (motionDisabled) {
                    stringResource(R.string.wc_reduced_motion_note)
                  } else {
                    stringResource(R.string.wc_motion_note)
                  },
                  color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
                  fontSize = 11.5.sp,
                  lineHeight = 15.sp
                )
              }
            }
          }
        }

        // 3. BACKGROUND CARD
        item {
          CustomizerSectionCard(
            title = stringResource(R.string.wc_section_background),
            icon = Icons.Rounded.Image
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              WidgetBgType.entries.forEach { bgType ->
                CustomizerChoiceChip(
                  label = stringResource(bgTypeLabelRes(bgType)),
                  selected = draft.bgType == bgType,
                  onClick = { draft = draft.copy(bgType = bgType) },
                  modifier = Modifier.testTag("wc_bg_type_${bgType.id}")
                )
              }
            }

            if (draft.bgType == WidgetBgType.AURORA) {
              Spacer(modifier = Modifier.height(8.dp))
              Text(
                text = stringResource(R.string.wc_aurora_note),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                fontSize = 11.5.sp
              )
            }

            AnimatedVisibility(visible = draft.bgType == WidgetBgType.SOLID) {
              Column(modifier = Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                  text = stringResource(R.string.wc_solid_color_label),
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  fontSize = 12.5.sp,
                  fontWeight = FontWeight.SemiBold
                )
                ColorSwatchRow(
                  hexColors = WidgetCustomizerConfig.SOLID_BG_COLORS,
                  selectedHex = draft.bgColorHex,
                  onSelect = { draft = draft.copy(bgColorHex = it) },
                  tagPrefix = "wc_bg_color"
                )
              }
            }

            AnimatedVisibility(visible = draft.bgType == WidgetBgType.PHOTO) {
              Column(modifier = Modifier.padding(top = 10.dp)) {
                CustomizerSliderRow(
                  label = stringResource(R.string.wc_blur_label),
                  valueText = "${draft.blur}",
                  value = draft.blur.toFloat(),
                  valueRange = 0f..60f,
                  onValueChange = { draft = draft.copy(blur = it.roundToInt()) },
                  testTag = "wc_blur_slider"
                )
              }
            }

            Spacer(modifier = Modifier.height(8.dp))
            CustomizerSliderRow(
              label = stringResource(R.string.wc_darkness_label),
              valueText = "${draft.darkness}%",
              value = draft.darkness.toFloat(),
              valueRange = 0f..100f,
              onValueChange = { draft = draft.copy(darkness = it.roundToInt()) },
              testTag = "wc_darkness_slider"
            )
          }
        }

        // 4. CROP BACKGROUND CARD (only when Background type is Photo)
        if (draft.bgType == WidgetBgType.PHOTO) {
          item {
            CustomizerSectionCard(
              title = stringResource(R.string.wc_section_crop),
              icon = Icons.Rounded.Crop,
              badgeText = if (hasCroppedBg) stringResource(R.string.wc_crop_active_badge) else null
            ) {
              Text(
                text = stringResource(R.string.wc_crop_subtitle),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                lineHeight = 16.sp
              )
              Spacer(modifier = Modifier.height(10.dp))
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
              ) {
                Button(
                  onClick = { showCropDialog = true },
                  shape = RoundedCornerShape(14.dp),
                  colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF43C7F4)),
                  modifier = Modifier
                    .weight(1f)
                    .testTag("wc_open_crop_btn")
                ) {
                  Icon(Icons.Rounded.Crop, contentDescription = null, tint = Color(0xFF080C14), modifier = Modifier.size(18.dp))
                  Spacer(modifier = Modifier.width(6.dp))
                  Text(
                    text = stringResource(R.string.wc_crop_open_button),
                    color = Color(0xFF080C14),
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                  )
                }

                if (hasCroppedBg) {
                  OutlinedButton(
                    onClick = {
                      scope.launch {
                        WidgetPhotoStorage.resetBackgroundCrop(context)
                        hasDiskPhotoChanges = true
                        draft = draft.copy(photoRevision = System.currentTimeMillis())
                        Toast.makeText(context, context.getString(R.string.wc_crop_reset_toast), Toast.LENGTH_SHORT).show()
                      }
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.testTag("wc_reset_crop_inline_btn")
                  ) {
                    Text(stringResource(R.string.wc_crop_reset_button))
                  }
                }
              }
            }
          }
        }

        // 5. DEVELOPER MODE TOOLS (only visible when Developer mode is enabled)
        if (developerModeEnabled) {
          item {
            CustomizerSectionCard(
              title = stringResource(R.string.wc_dev_section_title),
              icon = Icons.Rounded.Build
            ) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
              ) {
                OutlinedButton(
                  onClick = {
                    val json = draft.normalized().toJsonString()
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    cm?.setPrimaryClip(ClipData.newPlainText("aurix_widget_settings.json", json))
                    Toast.makeText(context, context.getString(R.string.wc_dev_exported_toast), Toast.LENGTH_SHORT).show()
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                      type = "application/json"
                      putExtra(Intent.EXTRA_TEXT, json)
                      putExtra(Intent.EXTRA_SUBJECT, "AURIX Widget Settings")
                    }
                    runCatching {
                      context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.wc_dev_export_json)))
                    }
                  },
                  shape = RoundedCornerShape(14.dp),
                  modifier = Modifier
                    .weight(1f)
                    .testTag("wc_dev_export_btn")
                ) {
                  Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                  Spacer(modifier = Modifier.width(6.dp))
                  Text(
                    text = stringResource(R.string.wc_dev_export_json),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                  )
                }

                Button(
                  onClick = {
                    scope.launch {
                      // Persist latest draft first so rebuild measures exact active config
                      onSaveConfig(draft.normalized())
                      hasDiskPhotoChanges = false
                      val ms = onRebuildWidgetBitmap()
                      Toast.makeText(
                        context,
                        context.getString(R.string.wc_dev_rebuilt_toast, ms),
                        Toast.LENGTH_SHORT
                      ).show()
                    }
                  },
                  shape = RoundedCornerShape(14.dp),
                  colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7965FF)),
                  modifier = Modifier
                    .weight(1f)
                    .testTag("wc_dev_rebuild_btn")
                ) {
                  Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                  Spacer(modifier = Modifier.width(6.dp))
                  Text(
                    text = stringResource(R.string.wc_dev_rebuild_bitmap),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                  )
                }
              }
            }
          }
        }

        item { Spacer(modifier = Modifier.height(10.dp)) }
      }

      // Pinned Bottom Save & Reset Bar
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.96f))
          .border(0.75.dp, Color.White.copy(alpha = 0.08f))
          .navigationBarsPadding()
          .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        OutlinedButton(
          onClick = { showResetDialog = true },
          shape = RoundedCornerShape(16.dp),
          modifier = Modifier
            .weight(0.42f)
            .height(48.dp)
            .testTag("wc_reset_button")
        ) {
          Text(
            text = stringResource(R.string.wc_reset),
            fontWeight = FontWeight.SemiBold
          )
        }

        Box(
          modifier = Modifier
            .weight(0.58f)
            .height(48.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(
              Brush.horizontalGradient(listOf(Color(0xFF43C7F4), Color(0xFF7965FF)))
            )
            .clickable {
              onSaveConfig(draft.normalized())
              hasDiskPhotoChanges = false
              Toast.makeText(context, context.getString(R.string.wc_saved_toast), Toast.LENGTH_SHORT).show()
              onClose()
            }
            .testTag("wc_save_button"),
          contentAlignment = Alignment.Center
        ) {
          Text(
            text = stringResource(R.string.wc_save),
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
          )
        }
      }
    }
  }

  // Crop Dialog
  if (showCropDialog) {
    WidgetCropDialog(
      albumArtBitmap = albumArtBitmap,
      onDismiss = { showCropDialog = false },
      onCropApplied = {
        showCropDialog = false
        hasDiskPhotoChanges = true
        // Applying background crop automatically switches to Different photos & keeps frame.jpg untouched
        draft = draft.copy(
          samePhoto = false,
          bgSource = WidgetPhotoSource.CUSTOM,
          photoRevision = System.currentTimeMillis()
        )
        Toast.makeText(context, context.getString(R.string.wc_crop_applied_toast), Toast.LENGTH_SHORT).show()
      },
      onCropReset = {
        showCropDialog = false
        hasDiskPhotoChanges = true
        draft = draft.copy(photoRevision = System.currentTimeMillis())
        Toast.makeText(context, context.getString(R.string.wc_crop_reset_toast), Toast.LENGTH_SHORT).show()
      },
      onError = { msg ->
        showCropDialog = false
        photoErrorMsg = msg
      }
    )
  }

  // Unsaved Changes Confirmation Dialog
  if (showUnsavedDialog) {
    AlertDialog(
      onDismissRequest = { showUnsavedDialog = false },
      title = { Text(stringResource(R.string.wc_unsaved_title), fontWeight = FontWeight.Bold) },
      text = { Text(stringResource(R.string.wc_unsaved_msg)) },
      confirmButton = {
        Button(
          onClick = {
            showUnsavedDialog = false
            onSaveConfig(draft.normalized())
            hasDiskPhotoChanges = false
            Toast.makeText(context, context.getString(R.string.wc_saved_toast), Toast.LENGTH_SHORT).show()
            onClose()
          }
        ) {
          Text(stringResource(R.string.wc_unsaved_save))
        }
      },
      dismissButton = {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
          TextButton(onClick = { showUnsavedDialog = false }) {
            Text(stringResource(R.string.wc_unsaved_keep))
          }
          TextButton(
            onClick = {
              showUnsavedDialog = false
              onClose()
            }
          ) {
            Text(stringResource(R.string.wc_unsaved_discard), color = Color(0xFFEF4444))
          }
        }
      }
    )
  }

  // Reset Confirmation Dialog
  if (showResetDialog) {
    AlertDialog(
      onDismissRequest = { showResetDialog = false },
      title = { Text(stringResource(R.string.wc_reset_confirm_title), fontWeight = FontWeight.Bold) },
      text = { Text(stringResource(R.string.wc_reset_confirm_msg)) },
      confirmButton = {
        Button(
          onClick = {
            showResetDialog = false
            scope.launch {
              WidgetPhotoStorage.resetBackgroundCrop(context)
              draft = WidgetCustomizerConfig.DEFAULT.copy(photoRevision = System.currentTimeMillis())
              onResetConfig()
              hasDiskPhotoChanges = false
              Toast.makeText(context, context.getString(R.string.wc_reset_toast), Toast.LENGTH_SHORT).show()
            }
          },
          colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
        ) {
          Text(stringResource(R.string.wc_reset), color = Color.White)
        }
      },
      dismissButton = {
        TextButton(onClick = { showResetDialog = false }) {
          Text(stringResource(R.string.wc_crop_cancel))
        }
      }
    )
  }
}

@Composable
private fun CustomizerSectionCard(
  title: String,
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  badgeText: String? = null,
  content: @Composable () -> Unit
) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(22.dp))
      .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.84f))
      .border(1.dp, Color.White.copy(alpha = 0.09f), RoundedCornerShape(22.dp))
      .padding(16.dp)
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(bottom = 12.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = Color(0xFF43C7F4),
        modifier = Modifier.size(19.dp)
      )
      Text(
        text = title,
        color = MaterialTheme.colorScheme.onSurface,
        fontSize = 15.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.weight(1f)
      )
      if (!badgeText.isNullOrBlank()) {
        Box(
          modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Color(0x3343C7F4))
            .padding(horizontal = 8.dp, vertical = 3.dp)
        ) {
          Text(
            text = badgeText,
            color = Color(0xFF43C7F4),
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Bold
          )
        }
      }
    }
    content()
  }
}

@Composable
private fun CustomizerSegmentPill(
  label: String,
  selected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  Box(
    modifier = modifier
      .clip(RoundedCornerShape(11.dp))
      .background(
        if (selected) Brush.horizontalGradient(listOf(Color(0xFF43C7F4), Color(0xFF7965FF)))
        else Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent))
      )
      .clickable(onClick = onClick)
      .padding(vertical = 10.dp, horizontal = 8.dp),
    contentAlignment = Alignment.Center
  ) {
    Text(
      text = label,
      color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
      fontSize = 12.5.sp,
      fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis
    )
  }
}

@Composable
private fun PhotoSourceRow(
  title: String,
  subtitle: String,
  isAlbumArt: Boolean,
  onPickPhoto: () -> Unit,
  onUseAlbumArt: () -> Unit,
  pickTag: String,
  albumTag: String
) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(16.dp))
      .background(Color.White.copy(alpha = 0.04f))
      .border(0.75.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
      .padding(12.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp)
  ) {
    Text(
      text = title,
      color = MaterialTheme.colorScheme.onSurface,
      fontSize = 13.5.sp,
      fontWeight = FontWeight.SemiBold
    )
    Text(
      text = subtitle,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      fontSize = 11.5.sp
    )
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      Button(
        onClick = onPickPhoto,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
          containerColor = if (!isAlbumArt) Color(0xFF43C7F4) else Color.White.copy(alpha = 0.12f)
        ),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
        modifier = Modifier
          .weight(1f)
          .testTag(pickTag)
      ) {
        Icon(
          Icons.Rounded.Image,
          contentDescription = null,
          tint = if (!isAlbumArt) Color(0xFF080C14) else Color.White,
          modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
          text = stringResource(if (isAlbumArt) R.string.wc_pick_photo else R.string.wc_change_photo),
          color = if (!isAlbumArt) Color(0xFF080C14) else Color.White,
          fontSize = 12.sp,
          fontWeight = FontWeight.Bold,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
      }

      OutlinedButton(
        onClick = onUseAlbumArt,
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
        modifier = Modifier
          .weight(1f)
          .border(
            width = if (isAlbumArt) 1.5.dp else 0.dp,
            color = if (isAlbumArt) Color(0xFF43C7F4) else Color.Transparent,
            shape = RoundedCornerShape(12.dp)
          )
          .testTag(albumTag)
      ) {
        Icon(
          Icons.Rounded.MusicNote,
          contentDescription = null,
          tint = if (isAlbumArt) Color(0xFF43C7F4) else MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(
          text = stringResource(R.string.wc_use_album_art),
          color = if (isAlbumArt) Color(0xFF43C7F4) else MaterialTheme.colorScheme.onSurface,
          fontSize = 12.sp,
          fontWeight = if (isAlbumArt) FontWeight.Bold else FontWeight.Medium,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
      }
    }
  }
}

@Composable
private fun CustomizerChoiceChip(
  label: String,
  selected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  Box(
    modifier = modifier
      .clip(RoundedCornerShape(12.dp))
      .background(
        if (selected) Color(0x3343C7F4) else Color.White.copy(alpha = 0.06f)
      )
      .border(
        width = if (selected) 1.5.dp else 0.75.dp,
        color = if (selected) Color(0xFF43C7F4) else Color.White.copy(alpha = 0.10f),
        shape = RoundedCornerShape(12.dp)
      )
      .clickable(onClick = onClick)
      .padding(horizontal = 12.dp, vertical = 9.dp),
    contentAlignment = Alignment.Center
  ) {
    Text(
      text = label,
      color = if (selected) Color(0xFF43C7F4) else MaterialTheme.colorScheme.onSurface,
      fontSize = 12.5.sp,
      fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis
    )
  }
}

@Composable
private fun ColorSwatchRow(
  hexColors: List<String>,
  selectedHex: String,
  onSelect: (String) -> Unit,
  tagPrefix: String
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    hexColors.forEachIndexed { idx, hex ->
      val colorInt = WidgetCustomizerConfig.parseColorInt(hex, 0xFF43C7F4.toInt())
      val selected = hex.equals(selectedHex, ignoreCase = true)
      Box(
        modifier = Modifier
          .size(36.dp)
          .clip(CircleShape)
          .background(Color(colorInt))
          .border(
            width = if (selected) 2.5.dp else 1.dp,
            color = if (selected) Color.White else Color.White.copy(alpha = 0.28f),
            shape = CircleShape
          )
          .clickable { onSelect(hex) }
          .testTag("${tagPrefix}_$idx"),
        contentAlignment = Alignment.Center
      ) {
        if (selected) {
          Icon(
            imageVector = Icons.Rounded.Check,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(18.dp)
          )
        }
      }
    }
  }
}

@Composable
private fun CustomizerSliderRow(
  label: String,
  valueText: String,
  value: Float,
  valueRange: ClosedFloatingPointRange<Float>,
  onValueChange: (Float) -> Unit,
  testTag: String
) {
  Column(modifier = Modifier.fillMaxWidth()) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text(
        text = label,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 12.5.sp,
        fontWeight = FontWeight.SemiBold
      )
      Text(
        text = valueText,
        color = Color(0xFF43C7F4),
        fontSize = 12.5.sp,
        fontWeight = FontWeight.Bold
      )
    }
    Slider(
      value = value.coerceIn(valueRange.start, valueRange.endInclusive),
      onValueChange = onValueChange,
      valueRange = valueRange,
      colors = SliderDefaults.colors(
        thumbColor = Color(0xFF43C7F4),
        activeTrackColor = Color(0xFF43C7F4),
        inactiveTrackColor = Color.White.copy(alpha = 0.14f)
      ),
      modifier = Modifier.testTag(testTag)
    )
  }
}

private fun frameStyleLabelRes(style: WidgetFrameStyle): Int = when (style) {
  WidgetFrameStyle.NONE -> R.string.wc_frame_none
  WidgetFrameStyle.ROUNDED -> R.string.wc_frame_rounded
  WidgetFrameStyle.CIRCLE -> R.string.wc_frame_circle
  WidgetFrameStyle.POLAROID -> R.string.wc_frame_polaroid
  WidgetFrameStyle.NEON -> R.string.wc_frame_neon
  WidgetFrameStyle.GRADIENT_RING -> R.string.wc_frame_gradient_ring
  WidgetFrameStyle.FILM -> R.string.wc_frame_film
  WidgetFrameStyle.STAMP -> R.string.wc_frame_stamp
  WidgetFrameStyle.VINYL -> R.string.wc_frame_vinyl
}

private fun frameMotionLabelRes(motion: WidgetFrameMotion): Int = when (motion) {
  WidgetFrameMotion.STILL -> R.string.wc_motion_still
  WidgetFrameMotion.FLOAT -> R.string.wc_motion_float
  WidgetFrameMotion.SPIN -> R.string.wc_motion_spin
  WidgetFrameMotion.PULSE -> R.string.wc_motion_pulse
}

private fun bgTypeLabelRes(type: WidgetBgType): Int = when (type) {
  WidgetBgType.PHOTO -> R.string.wc_bg_photo
  WidgetBgType.AURORA -> R.string.wc_bg_aurora
  WidgetBgType.SUNSET -> R.string.wc_bg_sunset
  WidgetBgType.OCEAN -> R.string.wc_bg_ocean
  WidgetBgType.SOLID -> R.string.wc_bg_solid
}
