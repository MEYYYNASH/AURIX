package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.AlbumCoverStorage
import com.example.data.CoverStudioLayout
import com.example.data.CoverStudioPalette
import com.example.model.APP_ICON_ARTWORK
import com.example.model.AlbumInfo
import com.example.model.CustomCoverMode
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditAlbumCoverSheet(
  albumInfo: AlbumInfo,
  reducedMotion: Boolean = false,
  onDismiss: () -> Unit,
  onSavePhoto: (imageUri: String, offsetXFraction: Float, offsetYFraction: Float, zoom: Float) -> Unit,
  onSaveMix: (imageSources: List<String>) -> Unit,
  onSaveStudio: (
    title: String,
    subtitle: String,
    paletteId: String,
    layoutId: String,
    backdropSource: String?,
    blurAmount: Float
  ) -> Unit = { _, _, _, _, _, _ -> },
  onSaveAppIcon: () -> Unit,
  onResetCover: () -> Unit
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = Color(0xFF14182B),
    contentColor = AlbumDesignTokens.Text,
    shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
    dragHandle = {
      Box(
        modifier = Modifier
          .padding(top = 12.dp, bottom = 6.dp)
          .width(42.dp)
          .height(4.dp)
          .clip(CircleShape)
          .background(Color.White.copy(alpha = 0.28f))
      )
    }
  ) {
    EditAlbumCoverSheetContent(
      albumInfo = albumInfo,
      reducedMotion = reducedMotion,
      onDismiss = onDismiss,
      onSavePhoto = onSavePhoto,
      onSaveMix = onSaveMix,
      onSaveStudio = onSaveStudio,
      onSaveAppIcon = onSaveAppIcon,
      onResetCover = onResetCover
    )
  }
}

@Composable
internal fun EditAlbumCoverSheetContent(
  albumInfo: AlbumInfo,
  reducedMotion: Boolean = false,
  onDismiss: () -> Unit,
  onSavePhoto: (imageUri: String, offsetXFraction: Float, offsetYFraction: Float, zoom: Float) -> Unit,
  onSaveMix: (imageSources: List<String>) -> Unit,
  onSaveStudio: (
    title: String,
    subtitle: String,
    paletteId: String,
    layoutId: String,
    backdropSource: String?,
    blurAmount: Float
  ) -> Unit = { _, _, _, _, _, _ -> },
  onSaveAppIcon: () -> Unit,
  onResetCover: () -> Unit
) {
  var selectedMode by remember(albumInfo.albumId) {
    mutableStateOf(albumInfo.customCoverMode ?: CustomCoverMode.PHOTO)
  }

  val initialPhoto = remember(albumInfo.albumId) {
    when {
      albumInfo.customCoverMode == CustomCoverMode.PHOTO && !albumInfo.coverUrl.isNullOrBlank() -> albumInfo.coverUrl
      albumInfo.automaticCoverUrl?.isNotBlank() == true -> albumInfo.automaticCoverUrl
      else -> ""
    }
  }
  var selectedPhotoUri by remember(albumInfo.albumId) { mutableStateOf(initialPhoto) }
  var photoOffsetX by remember(albumInfo.albumId) { mutableFloatStateOf(0f) }
  var photoOffsetY by remember(albumInfo.albumId) { mutableFloatStateOf(0f) }
  var photoZoom by remember(albumInfo.albumId) { mutableFloatStateOf(1f) }

  val trackCoverCandidates = remember(albumInfo.tracks, albumInfo.automaticCoverUrl) {
    buildList {
      albumInfo.automaticCoverUrl?.takeIf { it.isNotBlank() && it != APP_ICON_ARTWORK }?.let { add(it) }
      albumInfo.tracks.forEach { t ->
        val art = t.artworkUrl.trim()
        if (art.isNotBlank() && art != APP_ICON_ARTWORK && art != albumInfo.coverUrl) {
          add(art)
        }
      }
    }.distinct()
  }

  val mixImages = remember(albumInfo.albumId) {
    mutableStateListOf<String>().apply {
      val initialMix = if (albumInfo.customCoverMode == CustomCoverMode.MIX && albumInfo.customSourceUris.isNotEmpty()) {
        albumInfo.customSourceUris
      } else {
        trackCoverCandidates.take(4)
      }
      addAll(initialMix.take(4))
    }
  }
  var mixMaxErrorVisible by remember { mutableStateOf(false) }
  var activeSlotTarget by remember { mutableIntStateOf(-1) }
  var draggingSlotIndex by remember { mutableIntStateOf(-1) }
  var dragOffsetXPx by remember { mutableFloatStateOf(0f) }
  var slotWidthPx by remember { mutableFloatStateOf(0f) }
  val density = LocalDensity.current

  var appIconArmed by remember(albumInfo.albumId) {
    mutableStateOf(albumInfo.customCoverMode == CustomCoverMode.APP_ICON)
  }

  // Blur Studio state
  var studioTitle by remember(albumInfo.albumId) { mutableStateOf(albumInfo.title.take(36)) }
  var studioPalette by remember(albumInfo.albumId) { mutableStateOf(CoverStudioPalette.AURORA) }
  var studioLayout by remember(albumInfo.albumId) { mutableStateOf(CoverStudioLayout.CENTER) }
  var studioUseBackdrop by remember(albumInfo.albumId) {
    mutableStateOf(trackCoverCandidates.isNotEmpty())
  }
  var studioBlurAmount by remember(albumInfo.albumId) { mutableFloatStateOf(0.55f) }
  val studioBackdropSource = remember(trackCoverCandidates, studioUseBackdrop) {
    if (studioUseBackdrop) trackCoverCandidates.firstOrNull() else null
  }

  fun handleSinglePhotoResult(uriStr: String?) {
    if (!uriStr.isNullOrBlank()) {
      selectedPhotoUri = uriStr
      photoOffsetX = 0f
      photoOffsetY = 0f
      photoZoom = 1f
    }
  }

  fun handleMixPhotoResult(uriStr: String?) {
    val targetIdx = activeSlotTarget
    activeSlotTarget = -1
    if (!uriStr.isNullOrBlank()) {
      if (targetIdx in 0 until mixImages.size) {
        mixImages[targetIdx] = uriStr
        mixMaxErrorVisible = false
      } else if (mixImages.size < 4) {
        mixImages.add(uriStr)
        mixMaxErrorVisible = false
      } else {
        mixMaxErrorVisible = true
      }
    }
  }

  val singlePhotoFallback = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.GetContent()
  ) { uri -> handleSinglePhotoResult(uri?.toString()) }

  val singlePhotoLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.PickVisualMedia()
  ) { uri -> handleSinglePhotoResult(uri?.toString()) }

  val mixPhotoFallback = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.GetContent()
  ) { uri -> handleMixPhotoResult(uri?.toString()) }

  val mixPhotoLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.PickVisualMedia()
  ) { uri -> handleMixPhotoResult(uri?.toString()) }

  fun launchSinglePhotoPicker() {
    runCatching {
      singlePhotoLauncher.launch(
        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
      )
    }.onFailure {
      runCatching { singlePhotoFallback.launch("image/*") }
    }
  }

  fun launchMixPhotoPicker() {
    runCatching {
      mixPhotoLauncher.launch(
        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
      )
    }.onFailure {
      runCatching { mixPhotoFallback.launch("image/*") }
    }
  }

  val canSave = when (selectedMode) {
    CustomCoverMode.PHOTO -> selectedPhotoUri.isNotBlank()
    CustomCoverMode.MIX -> mixImages.isNotEmpty()
    CustomCoverMode.STUDIO -> true
    CustomCoverMode.APP_ICON -> appIconArmed || selectedMode == CustomCoverMode.APP_ICON
  }

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .verticalScroll(rememberScrollState())
      .then(
        if (reducedMotion) Modifier
        else Modifier.animateContentSize(
          animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)
        )
      )
      .padding(horizontal = 20.dp)
      .navigationBarsPadding()
      .padding(bottom = 20.dp),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text(
        text = stringResource(id = R.string.edit_cover_title),
        style = MaterialTheme.typography.titleLarge.copy(
          fontFamily = AlbumDesignTokens.OutfitFontFamily,
          fontSize = 20.sp,
          fontWeight = FontWeight.Bold
        ),
        color = AlbumDesignTokens.Text,
        modifier = Modifier.weight(1f)
      )
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
      ) {
        if (albumInfo.customCoverMode != null) {
          TextButton(
            onClick = {
              onResetCover()
              onDismiss()
            },
            modifier = Modifier.testTag("edit_cover_reset_button")
          ) {
            Icon(
              imageVector = Icons.Rounded.Refresh,
              contentDescription = null,
              tint = AlbumDesignTokens.Accent,
              modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
              text = stringResource(id = R.string.album_reset_cover),
              fontFamily = AlbumDesignTokens.OutfitFontFamily,
              color = AlbumDesignTokens.Accent,
              fontSize = 13.sp,
              fontWeight = FontWeight.SemiBold
            )
          }
        }
        IconButton(
          onClick = onDismiss,
          modifier = Modifier
            .minimumInteractiveComponentSize()
            .testTag("edit_cover_close_button")
        ) {
          Icon(
            imageVector = Icons.Rounded.Close,
            contentDescription = stringResource(id = R.string.cd_close_sheet),
            tint = AlbumDesignTokens.DimText
          )
        }
      }
    }

    Spacer(modifier = Modifier.height(14.dp))

    // Live 32dp-radius Square Cover Preview on top
    val previewShape = RoundedCornerShape(32.dp)
    Box(
      modifier = Modifier
        .size(176.dp)
        .shadow(
          elevation = 20.dp,
          shape = previewShape,
          ambientColor = Color.Black.copy(alpha = 0.6f),
          spotColor = AlbumDesignTokens.CoverGradientStart.copy(alpha = 0.4f)
        )
        .clip(previewShape)
        .background(
          Brush.linearGradient(
            colors = listOf(
              AlbumDesignTokens.CoverGradientStart,
              AlbumDesignTokens.CoverGradientEnd
            )
          )
        )
        .border(1.dp, Color.White.copy(alpha = 0.16f), previewShape)
        .testTag("edit_cover_preview_square"),
      contentAlignment = Alignment.Center
    ) {
      AnimatedContent(
        targetState = selectedMode,
        transitionSpec = {
          if (reducedMotion) {
            fadeIn(snap()) togetherWith fadeOut(snap())
          } else {
            (fadeIn(tween(240, easing = FastOutSlowInEasing)) +
              scaleIn(tween(240, easing = FastOutSlowInEasing), initialScale = 0.95f)) togetherWith
              (fadeOut(tween(180)) + scaleOut(tween(180), targetScale = 0.97f))
          }
        },
        label = "edit_cover_preview_mode"
      ) { previewMode ->
        when (previewMode) {
          CustomCoverMode.PHOTO -> {
            if (selectedPhotoUri.isBlank()) {
              Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                  imageVector = Icons.Rounded.MusicNote,
                  contentDescription = null,
                  tint = AlbumDesignTokens.Text.copy(alpha = 0.88f),
                  modifier = Modifier.size(52.dp)
                )
              }
            } else {
              AsyncImage(
                model = selectedPhotoUri,
                contentDescription = albumInfo.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                  .fillMaxSize()
                  .pointerInput(selectedPhotoUri, photoZoom) {
                    detectDragGestures { change, dragAmount ->
                      change.consume()
                      val scaleFactor = 1f / (size.width.toFloat().coerceAtLeast(1f) * 0.45f)
                      photoOffsetX = (photoOffsetX + dragAmount.x * scaleFactor).coerceIn(-1f, 1f)
                      photoOffsetY = (photoOffsetY + dragAmount.y * scaleFactor).coerceIn(-1f, 1f)
                    }
                  }
                  .graphicsLayer {
                    scaleX = photoZoom
                    scaleY = photoZoom
                    translationX = photoOffsetX * 36.dp.toPx()
                    translationY = photoOffsetY * 36.dp.toPx()
                  }
              )
            }
          }

          CustomCoverMode.MIX -> {
            MixCollagePreview(images = mixImages)
          }

          CustomCoverMode.STUDIO -> {
            StudioCoverPreview(
              title = studioTitle.ifBlank { albumInfo.title },
              subtitle = albumInfo.artist.ifBlank { "AURIX" },
              palette = studioPalette,
              layout = studioLayout,
              backdropSource = studioBackdropSource,
              blurAmount = studioBlurAmount
            )
          }

          CustomCoverMode.APP_ICON -> {
            Box(
              modifier = Modifier
                .fillMaxSize()
                .background(
                  Brush.linearGradient(
                    colors = listOf(
                      AlbumDesignTokens.CoverGradientStart,
                      AlbumDesignTokens.CoverGradientEnd
                    )
                  )
                ),
              contentAlignment = Alignment.Center
            ) {
              Image(
                painter = painterResource(id = R.drawable.ic_launcher_foreground),
                contentDescription = stringResource(id = R.string.edit_cover_use_app_icon),
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(0.86f)
              )
            }
          }
        }
      }
    }

    Spacer(modifier = Modifier.height(16.dp))

    // Three glass mode tabs: Photo, Mix, App icon
    EditCoverModeTabs(
      selectedMode = selectedMode,
      reducedMotion = reducedMotion,
      onSelectMode = {
        selectedMode = it
        mixMaxErrorVisible = false
        activeSlotTarget = -1
        if (it == CustomCoverMode.APP_ICON) {
          appIconArmed = true
        }
      }
    )

    Spacer(modifier = Modifier.height(16.dp))

    AnimatedContent(
      targetState = selectedMode,
      modifier = Modifier.fillMaxWidth(),
      transitionSpec = {
        if (reducedMotion) {
          fadeIn(snap()) togetherWith fadeOut(snap())
        } else {
          fadeIn(tween(220, easing = FastOutSlowInEasing)) togetherWith
            fadeOut(tween(150))
        }
      },
      label = "edit_cover_controls_mode"
    ) { activeControlsMode ->
      Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        when (activeControlsMode) {
      CustomCoverMode.PHOTO -> {
        GlassPillActionButton(
          label = if (selectedPhotoUri.isBlank()) {
            stringResource(id = R.string.edit_cover_choose_photo)
          } else {
            stringResource(id = R.string.edit_cover_change_photo)
          },
          icon = Icons.Rounded.PhotoLibrary,
          onClick = { launchSinglePhotoPicker() },
          modifier = Modifier
            .fillMaxWidth()
            .testTag("edit_cover_choose_photo_button")
        )
        if (selectedPhotoUri.isNotBlank()) {
          Spacer(modifier = Modifier.height(10.dp))
          Text(
            text = stringResource(id = R.string.edit_cover_crop_hint),
            fontFamily = AlbumDesignTokens.OutfitFontFamily,
            fontSize = 12.sp,
            color = AlbumDesignTokens.DimText
          )
          Slider(
            value = photoZoom,
            onValueChange = { photoZoom = it },
            valueRange = 1f..2.5f,
            colors = SliderDefaults.colors(
              thumbColor = Color.White,
              activeTrackColor = AlbumDesignTokens.Accent,
              inactiveTrackColor = Color.White.copy(alpha = 0.16f)
            ),
            modifier = Modifier.fillMaxWidth()
          )
        }
      }

      CustomCoverMode.MIX -> {
        Text(
          text = stringResource(id = R.string.edit_cover_mix_hint),
          fontFamily = AlbumDesignTokens.OutfitFontFamily,
          fontSize = 12.sp,
          color = AlbumDesignTokens.DimText,
          textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(12.dp))

        // 4 Glass Image Slots (+ to add, tap to replace, X to remove, drag to reorder)
        val slotGapPx = with(density) { 10.dp.toPx() }
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          for (slotIdx in 0 until 4) {
            val imageUri = mixImages.getOrNull(slotIdx)
            val slotShape = RoundedCornerShape(16.dp)
            val isDragging = draggingSlotIndex == slotIdx
            Box(
              modifier = Modifier
                .weight(1f)
                .aspectRatio(1f)
                .onSizeChanged { if (it.width > 0) slotWidthPx = it.width.toFloat() }
                .offset {
                  if (isDragging) IntOffset(dragOffsetXPx.roundToInt(), 0) else IntOffset.Zero
                }
                .graphicsLayer {
                  if (isDragging) {
                    scaleX = 1.07f
                    scaleY = 1.07f
                    shadowElevation = 12f
                  }
                }
                .clip(slotShape)
                .background(AlbumDesignTokens.GlassFill)
                .border(
                  width = if (activeSlotTarget == slotIdx || isDragging) 1.5.dp else 1.dp,
                  color = if (activeSlotTarget == slotIdx || isDragging) {
                    AlbumDesignTokens.Accent
                  } else {
                    AlbumDesignTokens.Border
                  },
                  shape = slotShape
                )
                .then(
                  if (imageUri != null) {
                    Modifier.pointerInput(mixImages.size, slotIdx) {
                      detectDragGesturesAfterLongPress(
                        onDragStart = {
                          draggingSlotIndex = slotIdx
                          dragOffsetXPx = 0f
                        },
                        onDragCancel = {
                          draggingSlotIndex = -1
                          dragOffsetXPx = 0f
                        },
                        onDragEnd = {
                          draggingSlotIndex = -1
                          dragOffsetXPx = 0f
                        },
                        onDrag = { change, dragAmount ->
                          change.consume()
                          dragOffsetXPx += dragAmount.x
                          val stepPx = (slotWidthPx + slotGapPx).coerceAtLeast(48f)
                          val currentIdx = draggingSlotIndex
                          if (currentIdx in 0 until mixImages.size) {
                            if (dragOffsetXPx > stepPx * 0.55f && currentIdx < mixImages.lastIndex) {
                              val moved = mixImages.removeAt(currentIdx)
                              mixImages.add(currentIdx + 1, moved)
                              draggingSlotIndex = currentIdx + 1
                              dragOffsetXPx -= stepPx
                            } else if (dragOffsetXPx < -stepPx * 0.55f && currentIdx > 0) {
                              val moved = mixImages.removeAt(currentIdx)
                              mixImages.add(currentIdx - 1, moved)
                              draggingSlotIndex = currentIdx - 1
                              dragOffsetXPx += stepPx
                            }
                          }
                        }
                      )
                    }
                  } else {
                    Modifier
                  }
                )
                .clickable(
                  role = Role.Button,
                  onClickLabel = stringResource(id = R.string.edit_cover_add_slot_cd, slotIdx + 1)
                ) {
                  activeSlotTarget = slotIdx
                  mixMaxErrorVisible = false
                }
                .testTag("edit_cover_mix_slot_$slotIdx"),
              contentAlignment = Alignment.Center
            ) {
              if (imageUri == null) {
                Icon(
                  imageVector = Icons.Rounded.Add,
                  contentDescription = stringResource(id = R.string.edit_cover_add_slot_cd, slotIdx + 1),
                  tint = AlbumDesignTokens.DimText,
                  modifier = Modifier.size(24.dp)
                )
              } else {
                AsyncImage(
                  model = imageUri,
                  contentDescription = null,
                  contentScale = ContentScale.Crop,
                  modifier = Modifier.fillMaxSize()
                )
                Box(
                  modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(4.dp)
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f)),
                  contentAlignment = Alignment.Center
                ) {
                  Icon(
                    imageVector = Icons.Rounded.DragIndicator,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(12.dp)
                  )
                }
                val removeCd = stringResource(id = R.string.edit_cover_remove_slot_cd)
                Box(
                  modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.72f))
                    .clickable(
                      role = Role.Button,
                      onClickLabel = removeCd
                    ) {
                      if (slotIdx in 0 until mixImages.size) {
                        mixImages.removeAt(slotIdx)
                        mixMaxErrorVisible = false
                        if (activeSlotTarget == slotIdx) activeSlotTarget = -1
                      }
                    }
                    .semantics { contentDescription = removeCd }
                    .testTag("edit_cover_mix_remove_$slotIdx"),
                  contentAlignment = Alignment.Center
                ) {
                  Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(13.dp)
                  )
                }
              }
            }
          }
        }

        AnimatedVisibility(visible = mixMaxErrorVisible) {
          Text(
            text = stringResource(id = R.string.edit_cover_mix_max_error),
            fontFamily = AlbumDesignTokens.OutfitFontFamily,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFFFF8A8A),
            modifier = Modifier
              .padding(top = 8.dp)
              .testTag("mix_max_error_text")
          )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Source chooser row: Gallery + Album song covers
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .border(1.dp, AlbumDesignTokens.Border, RoundedCornerShape(18.dp))
            .padding(12.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            GlassPillActionButton(
              label = stringResource(id = R.string.edit_cover_mix_gallery),
              icon = Icons.Rounded.PhotoLibrary,
              onClick = {
                if (activeSlotTarget !in 0 until mixImages.size && mixImages.size >= 4) {
                  mixMaxErrorVisible = true
                } else {
                  mixMaxErrorVisible = false
                  launchMixPhotoPicker()
                }
              },
              modifier = Modifier
                .weight(1f)
                .testTag("edit_cover_mix_add_gallery")
            )
            if (trackCoverCandidates.size >= 2) {
              GlassPillActionButton(
                label = stringResource(id = R.string.edit_cover_mix_auto_fill),
                icon = Icons.Rounded.GridView,
                isHighlighted = true,
                onClick = {
                  mixImages.clear()
                  mixImages.addAll(trackCoverCandidates.take(4))
                  activeSlotTarget = -1
                  mixMaxErrorVisible = false
                },
                modifier = Modifier.testTag("edit_cover_mix_auto_fill_button")
              )
            }
          }

          if (trackCoverCandidates.isNotEmpty()) {
            Text(
              text = stringResource(id = R.string.edit_cover_mix_from_album),
              fontFamily = AlbumDesignTokens.OutfitFontFamily,
              fontSize = 12.sp,
              fontWeight = FontWeight.Medium,
              color = AlbumDesignTokens.DimText
            )
            LazyRow(
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              contentPadding = PaddingValues(vertical = 2.dp)
            ) {
              itemsIndexed(trackCoverCandidates) { idx, candidateUri ->
                Box(
                  modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, AlbumDesignTokens.Border, RoundedCornerShape(12.dp))
                    .clickable {
                      val replaceIdx = activeSlotTarget
                      if (replaceIdx in 0 until mixImages.size) {
                        mixImages[replaceIdx] = candidateUri
                        activeSlotTarget = -1
                        mixMaxErrorVisible = false
                      } else if (mixImages.size < 4) {
                        mixImages.add(candidateUri)
                        activeSlotTarget = -1
                        mixMaxErrorVisible = false
                      } else {
                        mixMaxErrorVisible = true
                      }
                    }
                    .testTag("edit_cover_mix_track_candidate_$idx")
                ) {
                  AsyncImage(
                    model = candidateUri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                  )
                }
              }
            }
          }
        }
      }

      CustomCoverMode.STUDIO -> {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .border(1.dp, AlbumDesignTokens.Border, RoundedCornerShape(20.dp))
            .padding(14.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          // 6 curated gradient palette swatches
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              text = stringResource(id = R.string.edit_cover_studio_palette),
              fontFamily = AlbumDesignTokens.OutfitFontFamily,
              fontSize = 12.sp,
              fontWeight = FontWeight.SemiBold,
              color = AlbumDesignTokens.DimText
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              CoverStudioPalette.entries.forEach { pal ->
                val isSelected = pal == studioPalette
                Box(
                  modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(
                      Brush.linearGradient(
                        listOf(Color(pal.startColorInt), Color(pal.midColorInt), Color(pal.endColorInt))
                      )
                    )
                    .border(
                      width = if (isSelected) 2.dp else 1.dp,
                      color = if (isSelected) Color.White else Color.White.copy(alpha = 0.22f),
                      shape = CircleShape
                    )
                    .clickable(role = Role.RadioButton) { studioPalette = pal }
                    .testTag("edit_cover_studio_palette_${pal.id}")
                )
              }
            }
          }

          // 3 minimalist typography layouts
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            val layouts = listOf(
              CoverStudioLayout.CENTER to stringResource(id = R.string.edit_cover_studio_layout_center),
              CoverStudioLayout.EDITORIAL to stringResource(id = R.string.edit_cover_studio_layout_editorial),
              CoverStudioLayout.MONOGRAM to stringResource(id = R.string.edit_cover_studio_layout_monogram)
            )
            layouts.forEach { (layout, label) ->
              val selected = layout == studioLayout
              Box(
                modifier = Modifier
                  .weight(1f)
                  .sizeIn(minHeight = 38.dp)
                  .clip(CircleShape)
                  .background(
                    if (selected) AlbumDesignTokens.Accent.copy(alpha = 0.20f)
                    else Color.White.copy(alpha = 0.05f)
                  )
                  .border(
                    width = 1.dp,
                    color = if (selected) AlbumDesignTokens.Accent.copy(alpha = 0.65f) else AlbumDesignTokens.Border,
                    shape = CircleShape
                  )
                  .clickable(role = Role.RadioButton) { studioLayout = layout }
                  .testTag("edit_cover_studio_layout_${layout.id}"),
                contentAlignment = Alignment.Center
              ) {
                Text(
                  text = label,
                  fontFamily = AlbumDesignTokens.OutfitFontFamily,
                  fontSize = 12.sp,
                  fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                  color = if (selected) Color.White else AlbumDesignTokens.DimText,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis
                )
              }
            }
          }

          // Optional blurred artwork backdrop
          if (trackCoverCandidates.isNotEmpty()) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text(
                text = stringResource(id = R.string.edit_cover_studio_blur_backdrop),
                fontFamily = AlbumDesignTokens.OutfitFontFamily,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = AlbumDesignTokens.Text
              )
              Switch(
                checked = studioUseBackdrop,
                onCheckedChange = { studioUseBackdrop = it },
                colors = SwitchDefaults.colors(
                  checkedThumbColor = Color.White,
                  checkedTrackColor = AlbumDesignTokens.Accent
                ),
                modifier = Modifier.testTag("edit_cover_studio_blur_switch")
              )
            }
            if (studioUseBackdrop) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
              ) {
                Text(
                  text = stringResource(id = R.string.edit_cover_studio_blur_label),
                  fontFamily = AlbumDesignTokens.OutfitFontFamily,
                  fontSize = 12.sp,
                  color = AlbumDesignTokens.DimText
                )
                Slider(
                  value = studioBlurAmount,
                  onValueChange = { studioBlurAmount = it },
                  valueRange = 0.1f..1f,
                  colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = AlbumDesignTokens.Accent,
                    inactiveTrackColor = Color.White.copy(alpha = 0.16f)
                  ),
                  modifier = Modifier.weight(1f)
                )
              }
            }
          }

          // Editable cover title
          OutlinedTextField(
            value = studioTitle,
            onValueChange = { if (it.length <= 36) studioTitle = it },
            singleLine = true,
            label = {
              Text(
                text = stringResource(id = R.string.edit_cover_studio_title_label),
                fontSize = 12.sp
              )
            },
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
              focusedTextColor = AlbumDesignTokens.Text,
              unfocusedTextColor = AlbumDesignTokens.Text,
              focusedBorderColor = AlbumDesignTokens.Accent,
              unfocusedBorderColor = AlbumDesignTokens.Border,
              focusedLabelColor = AlbumDesignTokens.Accent,
              unfocusedLabelColor = AlbumDesignTokens.DimText,
              cursorColor = AlbumDesignTokens.Accent
            ),
            modifier = Modifier
              .fillMaxWidth()
              .testTag("edit_cover_studio_title_input")
          )
        }
      }

      CustomCoverMode.APP_ICON -> {
        Text(
          text = stringResource(id = R.string.edit_cover_app_icon_desc),
          fontFamily = AlbumDesignTokens.OutfitFontFamily,
          fontSize = 13.sp,
          color = AlbumDesignTokens.DimText,
          textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(12.dp))
        GlassPillActionButton(
          label = stringResource(id = R.string.edit_cover_use_app_icon),
          icon = null,
          isHighlighted = appIconArmed,
          onClick = { appIconArmed = true },
          modifier = Modifier
            .fillMaxWidth()
            .testTag("edit_cover_use_app_icon_button")
        )
      }
        }
      }
    }

    Spacer(modifier = Modifier.height(20.dp))

    // Save (solid white pill) & Cancel (glass pill)
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      val cancelLabel = stringResource(id = R.string.edit_cover_cancel)
      Surface(
        shape = CircleShape,
        color = AlbumDesignTokens.GlassFill,
        modifier = Modifier
          .weight(1f)
          .height(52.dp)
          .clip(CircleShape)
          .border(1.dp, AlbumDesignTokens.Border, CircleShape)
          .clickable(role = Role.Button, onClickLabel = cancelLabel, onClick = onDismiss)
          .testTag("edit_cover_cancel_button")
      ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          Text(
            text = cancelLabel,
            fontFamily = AlbumDesignTokens.OutfitFontFamily,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = AlbumDesignTokens.Text
          )
        }
      }

      val saveLabel = stringResource(id = R.string.edit_cover_save)
      Surface(
        shape = CircleShape,
        color = if (canSave) Color.White else Color.White.copy(alpha = 0.35f),
        modifier = Modifier
          .weight(1f)
          .height(52.dp)
          .clip(CircleShape)
          .clickable(
            enabled = canSave,
            role = Role.Button,
            onClickLabel = saveLabel
          ) {
            when (selectedMode) {
              CustomCoverMode.PHOTO -> {
                if (selectedPhotoUri.isNotBlank()) {
                  onSavePhoto(selectedPhotoUri, photoOffsetX, photoOffsetY, photoZoom)
                  onDismiss()
                }
              }
              CustomCoverMode.MIX -> {
                if (mixImages.isNotEmpty()) {
                  onSaveMix(mixImages.toList())
                  onDismiss()
                }
              }
              CustomCoverMode.STUDIO -> {
                onSaveStudio(
                  studioTitle.ifBlank { albumInfo.title },
                  albumInfo.artist.ifBlank { "AURIX" },
                  studioPalette.id,
                  studioLayout.id,
                  studioBackdropSource,
                  studioBlurAmount
                )
                onDismiss()
              }
              CustomCoverMode.APP_ICON -> {
                onSaveAppIcon()
                onDismiss()
              }
            }
          }
          .testTag("edit_cover_save_button")
      ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          Text(
            text = saveLabel,
            fontFamily = AlbumDesignTokens.OutfitFontFamily,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = AlbumDesignTokens.Background
          )
        }
      }
    }
  }
}

@Composable
internal fun MixCollagePreview(
  images: List<String>,
  modifier: Modifier = Modifier
) {
  val clean = images.filter { it.isNotBlank() }.take(4)
  val gap = 4.dp
  Box(
    modifier = modifier
      .fillMaxSize()
      .background(AlbumDesignTokens.Background),
    contentAlignment = Alignment.Center
  ) {
    when (clean.size) {
      0 -> {
        Box(
          modifier = Modifier
            .fillMaxSize()
            .background(
              Brush.linearGradient(
                colors = listOf(
                  AlbumDesignTokens.CoverGradientStart,
                  AlbumDesignTokens.CoverGradientEnd
                )
              )
            ),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = Icons.Rounded.MusicNote,
            contentDescription = null,
            tint = AlbumDesignTokens.Text.copy(alpha = 0.88f),
            modifier = Modifier.size(52.dp)
          )
        }
      }
      1 -> {
        MixSlotImage(clean[0], Modifier.fillMaxSize())
      }
      2 -> {
        Row(
          modifier = Modifier.fillMaxSize(),
          horizontalArrangement = Arrangement.spacedBy(gap)
        ) {
          MixSlotImage(clean[0], Modifier.weight(1f).fillMaxHeight())
          MixSlotImage(clean[1], Modifier.weight(1f).fillMaxHeight())
        }
      }
      3 -> {
        Row(
          modifier = Modifier.fillMaxSize(),
          horizontalArrangement = Arrangement.spacedBy(gap)
        ) {
          MixSlotImage(clean[0], Modifier.weight(1f).fillMaxHeight())
          Column(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(gap)
          ) {
            MixSlotImage(clean[1], Modifier.fillMaxWidth().weight(1f))
            MixSlotImage(clean[2], Modifier.fillMaxWidth().weight(1f))
          }
        }
      }
      else -> {
        Column(
          modifier = Modifier.fillMaxSize(),
          verticalArrangement = Arrangement.spacedBy(gap)
        ) {
          Row(
            modifier = Modifier.fillMaxWidth().weight(1f),
            horizontalArrangement = Arrangement.spacedBy(gap)
          ) {
            MixSlotImage(clean[0], Modifier.weight(1f).fillMaxHeight())
            MixSlotImage(clean[1], Modifier.weight(1f).fillMaxHeight())
          }
          Row(
            modifier = Modifier.fillMaxWidth().weight(1f),
            horizontalArrangement = Arrangement.spacedBy(gap)
          ) {
            MixSlotImage(clean[2], Modifier.weight(1f).fillMaxHeight())
            MixSlotImage(clean[3], Modifier.weight(1f).fillMaxHeight())
          }
        }
      }
    }
  }
}

@Composable
private fun MixSlotImage(source: String, modifier: Modifier) {
  if (source == APP_ICON_ARTWORK) {
    Box(
      modifier = modifier.background(
        Brush.linearGradient(
          colors = listOf(AlbumDesignTokens.CoverGradientStart, AlbumDesignTokens.CoverGradientEnd)
        )
      ),
      contentAlignment = Alignment.Center
    ) {
      Image(
        painter = painterResource(id = R.drawable.ic_launcher_foreground),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = Modifier.fillMaxSize(0.82f)
      )
    }
  } else {
    AsyncImage(
      model = source,
      contentDescription = null,
      contentScale = ContentScale.Crop,
      modifier = modifier
    )
  }
}

@Composable
private fun EditCoverModeTabs(
  selectedMode: CustomCoverMode,
  reducedMotion: Boolean = false,
  onSelectMode: (CustomCoverMode) -> Unit
) {
  val tabs = listOf(
    CustomCoverMode.PHOTO to stringResource(id = R.string.edit_cover_tab_photo),
    CustomCoverMode.MIX to stringResource(id = R.string.edit_cover_tab_mix),
    CustomCoverMode.STUDIO to stringResource(id = R.string.edit_cover_tab_studio),
    CustomCoverMode.APP_ICON to stringResource(id = R.string.edit_cover_tab_app_icon)
  )
  val colorSpec = if (reducedMotion) snap<Color>() else tween<Color>(durationMillis = 220, easing = FastOutSlowInEasing)

  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clip(CircleShape)
      .background(Color.White.copy(alpha = 0.07f))
      .border(1.dp, AlbumDesignTokens.Border, CircleShape)
      .padding(4.dp),
    horizontalArrangement = Arrangement.spacedBy(4.dp)
  ) {
    tabs.forEach { (mode, label) ->
      val selected = selectedMode == mode
      val tag = when (mode) {
        CustomCoverMode.PHOTO -> "edit_cover_tab_photo"
        CustomCoverMode.MIX -> "edit_cover_tab_mix"
        CustomCoverMode.STUDIO -> "edit_cover_tab_studio"
        CustomCoverMode.APP_ICON -> "edit_cover_tab_app_icon"
      }
      val pillBg by animateColorAsState(
        targetValue = if (selected) Color(0xFF282E4A) else Color.Transparent,
        animationSpec = colorSpec,
        label = "mode_tab_bg_${mode.id}"
      )
      val pillBorder by animateColorAsState(
        targetValue = if (selected) Color.White.copy(alpha = 0.2f) else Color.Transparent,
        animationSpec = colorSpec,
        label = "mode_tab_border_${mode.id}"
      )
      val textColor by animateColorAsState(
        targetValue = if (selected) AlbumDesignTokens.Text else AlbumDesignTokens.DimText,
        animationSpec = colorSpec,
        label = "mode_tab_text_${mode.id}"
      )

      Box(
        modifier = Modifier
          .weight(1f)
          .sizeIn(minHeight = 40.dp)
          .clip(CircleShape)
          .background(pillBg)
          .border(1.dp, pillBorder, CircleShape)
          .clickable(role = Role.Tab) { onSelectMode(mode) }
          .testTag(tag),
        contentAlignment = Alignment.Center
      ) {
        Text(
          text = label,
          fontFamily = AlbumDesignTokens.OutfitFontFamily,
          fontSize = 12.sp,
          fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
          color = textColor,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
      }
    }
  }
}

@Composable
private fun StudioCoverPreview(
  title: String,
  subtitle: String,
  palette: CoverStudioPalette,
  layout: CoverStudioLayout,
  backdropSource: String?,
  blurAmount: Float
) {
  val startColor = Color(palette.startColorInt)
  val midColor = Color(palette.midColorInt)
  val endColor = Color(palette.endColorInt)
  val cleanTitle = title.trim().ifBlank { "AURIX" }

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(Brush.linearGradient(listOf(startColor, midColor, endColor)))
  ) {
    if (!backdropSource.isNullOrBlank() && backdropSource != APP_ICON_ARTWORK) {
      AsyncImage(
        model = backdropSource,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
          .fillMaxSize()
          .blur((blurAmount * 18f).dp)
          .graphicsLayer { alpha = 0.72f }
      )
      Box(
        modifier = Modifier
          .fillMaxSize()
          .background(
            Brush.linearGradient(
              listOf(startColor.copy(alpha = 0.56f), endColor.copy(alpha = 0.76f))
            )
          )
      )
    }

    when (layout) {
      CoverStudioLayout.CENTER -> {
        Column(
          modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.Center
        ) {
          Text(
            text = cleanTitle,
            fontFamily = AlbumDesignTokens.OutfitFontFamily,
            fontSize = if (cleanTitle.length > 14) 16.sp else 20.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color.White,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
          )
          if (subtitle.isNotBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
              text = subtitle.uppercase(),
              fontSize = 9.sp,
              letterSpacing = 1.sp,
              color = Color.White.copy(alpha = 0.78f),
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
          }
        }
      }

      CoverStudioLayout.EDITORIAL -> {
        Box(
          modifier = Modifier
            .fillMaxSize()
            .background(
              Brush.verticalGradient(
                listOf(Color.Transparent, Color(0xB8060A14)),
                startY = 120f
              )
            )
        )
        Column(
          modifier = Modifier
            .align(Alignment.BottomStart)
            .padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
          Box(
            modifier = Modifier
              .width(22.dp)
              .height(3.dp)
              .clip(CircleShape)
              .background(Color.White.copy(alpha = 0.9f))
          )
          Text(
            text = cleanTitle,
            fontFamily = AlbumDesignTokens.OutfitFontFamily,
            fontSize = 16.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
          )
          if (subtitle.isNotBlank()) {
            Text(
              text = subtitle,
              fontSize = 10.sp,
              color = Color.White.copy(alpha = 0.78f),
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
          }
        }
      }

      CoverStudioLayout.MONOGRAM -> {
        Column(
          modifier = Modifier
            .fillMaxSize()
            .padding(14.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.Center
        ) {
          Box(
            modifier = Modifier
              .size(86.dp)
              .clip(CircleShape)
              .background(Color.White.copy(alpha = 0.16f))
              .border(1.5.dp, Color.White.copy(alpha = 0.38f), CircleShape),
            contentAlignment = Alignment.Center
          ) {
            Text(
              text = AlbumCoverStorage.buildMonogramText(cleanTitle),
              fontFamily = AlbumDesignTokens.OutfitFontFamily,
              fontSize = 30.sp,
              fontWeight = FontWeight.Black,
              color = Color.White
            )
          }
          Spacer(modifier = Modifier.height(10.dp))
          Text(
            text = cleanTitle,
            fontFamily = AlbumDesignTokens.OutfitFontFamily,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White.copy(alpha = 0.92f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }
      }
    }
  }
}

@Composable
private fun GlassPillActionButton(
  label: String,
  icon: androidx.compose.ui.graphics.vector.ImageVector?,
  isHighlighted: Boolean = false,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  Row(
    modifier = modifier
      .sizeIn(minHeight = 48.dp)
      .clip(CircleShape)
      .background(
        if (isHighlighted) AlbumDesignTokens.Accent.copy(alpha = 0.18f)
        else AlbumDesignTokens.GlassFill
      )
      .border(
        width = 1.dp,
        color = if (isHighlighted) AlbumDesignTokens.Accent.copy(alpha = 0.55f) else AlbumDesignTokens.Border,
        shape = CircleShape
      )
      .drawBehind {
        val stroke = 1.dp.toPx()
        if (size.width <= stroke || size.height <= stroke) return@drawBehind
        val r = ((size.height - stroke) * 0.5f).coerceAtLeast(0f)
        drawRoundRect(
          brush = Brush.verticalGradient(
            listOf(Color.White.copy(alpha = 0.22f), Color.Transparent),
            startY = 0f,
            endY = size.height * 0.5f
          ),
          topLeft = Offset(stroke * 0.5f, stroke * 0.5f),
          size = Size(size.width - stroke, size.height - stroke),
          cornerRadius = CornerRadius(r, r),
          style = Stroke(width = stroke)
        )
      }
      .clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
      .padding(horizontal = 16.dp, vertical = 12.dp),
    horizontalArrangement = Arrangement.Center,
    verticalAlignment = Alignment.CenterVertically
  ) {
    if (icon != null) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = AlbumDesignTokens.Accent,
        modifier = Modifier.size(18.dp)
      )
      Spacer(modifier = Modifier.width(8.dp))
    }
    Text(
      text = label,
      fontFamily = AlbumDesignTokens.OutfitFontFamily,
      fontSize = 14.sp,
      fontWeight = FontWeight.SemiBold,
      color = AlbumDesignTokens.Text
    )
  }
}
