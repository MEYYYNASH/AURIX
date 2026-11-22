package com.example.ui.screens

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.example.R
import kotlinx.coroutines.launch

object GlassMenuTokens {
  val CardBackground = Color(24, 30, 58).copy(alpha = 0.72f)
  val CardBorder = Color.White.copy(alpha = 0.10f)
  val CardCornerRadius = 26.dp
  val CardPadding = 8.dp
  val BlueAccent = Color(0xFF2F8BFF)
  val IndigoAccent = Color(0xFF4F46E5)
  val PurpleAccent = Color(0xFFA78BFA)
  val DestructiveRed = Color(0xFFFF6B6B)
  val TextPrimary = Color(0xFFF4F3FF)
  val HintGray = Color(0xFF94A3B8)
  val DividerColor = Color.White.copy(alpha = 0.09f)
  val PressHighlight = Color.White.copy(alpha = 0.09f)
  val IconTileSize = 34.dp
  val IconTileRadius = 11.dp
}

private class GlassMenuPositionProvider(
  private val horizontalAlignment: Alignment.Horizontal,
  private val anchorMarginPx: Int,
  private val screenMarginPx: Int,
  private val shadowPadPx: Int,
  private val onTransformOriginCalculated: (TransformOrigin) -> Unit
) : PopupPositionProvider {
  override fun calculatePosition(
    anchorBounds: IntRect,
    windowSize: IntSize,
    layoutDirection: LayoutDirection,
    popupContentSize: IntSize
  ): IntOffset {
    val cardW = (popupContentSize.width - shadowPadPx * 2).coerceAtLeast(1)
    val cardH = (popupContentSize.height - shadowPadPx * 2).coerceAtLeast(1)

    val minX = screenMarginPx
    val maxX = (windowSize.width - screenMarginPx - cardW).coerceAtLeast(minX)
    val desiredX = when (horizontalAlignment) {
      Alignment.End -> anchorBounds.right - cardW
      Alignment.CenterHorizontally -> anchorBounds.center.x - cardW / 2
      else -> anchorBounds.left
    }
    val cardX = desiredX.coerceIn(minX, maxX)

    val pivotX = when (horizontalAlignment) {
      Alignment.End -> ((anchorBounds.right - cardX).toFloat() / cardW.toFloat()).coerceIn(0f, 1f)
      Alignment.CenterHorizontally -> ((anchorBounds.center.x - cardX).toFloat() / cardW.toFloat()).coerceIn(0f, 1f)
      else -> ((anchorBounds.left - cardX).toFloat() / cardW.toFloat()).coerceIn(0f, 1f)
    }

    val minY = screenMarginPx
    val maxY = (windowSize.height - screenMarginPx - cardH).coerceAtLeast(minY)
    val belowY = anchorBounds.bottom + anchorMarginPx
    val aboveY = anchorBounds.top - anchorMarginPx - cardH

    val fitsBelow = belowY + cardH <= windowSize.height - screenMarginPx
    val fitsAbove = aboveY >= screenMarginPx
    val spaceBelow = windowSize.height - screenMarginPx - (anchorBounds.bottom + anchorMarginPx)
    val spaceAbove = anchorBounds.top - anchorMarginPx - screenMarginPx

    val openBelow = when {
      fitsBelow -> true
      fitsAbove -> false
      else -> spaceBelow >= spaceAbove
    }
    val cardY = if (openBelow) {
      belowY.coerceIn(minY, maxY)
    } else {
      aboveY.coerceIn(minY, maxY)
    }
    val pivotY = if (openBelow) 0f else 1f

    onTransformOriginCalculated(TransformOrigin(pivotX, pivotY))
    return IntOffset(x = cardX - shadowPadPx, y = cardY - shadowPadPx)
  }
}

@Composable
fun GlassPopupMenuContainer(
  expanded: Boolean,
  onDismissRequest: () -> Unit,
  horizontalAlignment: Alignment.Horizontal,
  menuWidth: Dp,
  reducedMotion: Boolean = false,
  testTag: String,
  content: @Composable ColumnScope.() -> Unit
) {
  if (!expanded) return

  val density = LocalDensity.current
  val shadowPad = 16.dp
  val shadowPadPx = with(density) { shadowPad.roundToPx() }
  val anchorMarginPx = with(density) { 8.dp.roundToPx() }
  val screenMarginPx = with(density) { 12.dp.roundToPx() }

  var transformOrigin by remember {
    mutableStateOf(
      TransformOrigin(
        pivotFractionX = if (horizontalAlignment == Alignment.End) 1f else 0f,
        pivotFractionY = 0f
      )
    )
  }

  val positionProvider = remember(
    horizontalAlignment,
    anchorMarginPx,
    screenMarginPx,
    shadowPadPx
  ) {
    GlassMenuPositionProvider(
      horizontalAlignment = horizontalAlignment,
      anchorMarginPx = anchorMarginPx,
      screenMarginPx = screenMarginPx,
      shadowPadPx = shadowPadPx,
      onTransformOriginCalculated = { transformOrigin = it }
    )
  }

  val scaleAnim = remember { Animatable(if (reducedMotion) 1f else 0.88f) }
  val alphaAnim = remember { Animatable(if (reducedMotion) 1f else 0f) }

  LaunchedEffect(expanded, reducedMotion) {
    if (reducedMotion) {
      scaleAnim.snapTo(1f)
      alphaAnim.snapTo(1f)
    } else {
      scaleAnim.snapTo(0.88f)
      alphaAnim.snapTo(0f)
      val overshootEasing = CubicBezierEasing(0.34f, 1.38f, 0.64f, 1f)
      launch {
        scaleAnim.animateTo(
          targetValue = 1f,
          animationSpec = tween(durationMillis = 220, easing = overshootEasing)
        )
      }
      launch {
        alphaAnim.animateTo(
          targetValue = 1f,
          animationSpec = tween(durationMillis = 220, easing = LinearOutSlowInEasing)
        )
      }
    }
  }

  Popup(
    popupPositionProvider = positionProvider,
    onDismissRequest = onDismissRequest,
    properties = PopupProperties(
      focusable = true,
      dismissOnBackPress = true,
      dismissOnClickOutside = true,
      clippingEnabled = false
    )
  ) {
    BackHandler(enabled = expanded) {
      onDismissRequest()
    }

    val cardShape = RoundedCornerShape(GlassMenuTokens.CardCornerRadius)
    val dismissInteraction = remember { MutableInteractionSource() }
    val cardInteraction = remember { MutableInteractionSource() }

    Box(
      modifier = Modifier
        .clickable(
          interactionSource = dismissInteraction,
          indication = null,
          onClick = onDismissRequest
        )
        .padding(shadowPad)
    ) {
      Box(
        modifier = Modifier
          .widthIn(min = 216.dp, max = menuWidth)
          .fillMaxWidth()
          .graphicsLayer {
            scaleX = scaleAnim.value
            scaleY = scaleAnim.value
            alpha = alphaAnim.value
            this.transformOrigin = transformOrigin
          }
          .shadow(
            elevation = 24.dp,
            shape = cardShape,
            ambientColor = Color(0xFF03050F).copy(alpha = 0.88f),
            spotColor = Color(0xFF05081A).copy(alpha = 0.92f)
          )
          .clip(cardShape)
          .background(GlassMenuTokens.CardBackground)
          .clickable(
            interactionSource = cardInteraction,
            indication = null,
            onClick = {}
          )
          .testTag(testTag)
      ) {
        // Internal 28dp frosted glass refraction layer
        Box(
          modifier = Modifier
            .matchParentSize()
            .then(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Modifier.blur(28.dp) else Modifier)
        ) {
          Box(
            modifier = Modifier
              .size(136.dp)
              .offset(x = (-24).dp, y = (-24).dp)
              .clip(CircleShape)
              .background(GlassMenuTokens.BlueAccent.copy(alpha = 0.14f))
          )
          Box(
            modifier = Modifier
              .size(128.dp)
              .align(Alignment.BottomEnd)
              .offset(x = 26.dp, y = 24.dp)
              .clip(CircleShape)
              .background(GlassMenuTokens.PurpleAccent.copy(alpha = 0.13f))
          )
        }

        // Subtle glass specular rim & sheen overlay
        Box(
          modifier = Modifier
            .matchParentSize()
            .background(
              Brush.verticalGradient(
                0.0f to Color.White.copy(alpha = 0.08f),
                0.30f to Color.Transparent,
                1.0f to Color.Black.copy(alpha = 0.10f)
              )
            )
            .border(
              width = 1.dp,
              color = GlassMenuTokens.CardBorder,
              shape = cardShape
            )
        )

        Column(
          modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(GlassMenuTokens.CardPadding),
          verticalArrangement = Arrangement.spacedBy(2.dp),
          content = content
        )
      }
    }
  }
}

@Composable
fun GlassMenuDivider(modifier: Modifier = Modifier) {
  Box(
    modifier = modifier
      .fillMaxWidth()
      .padding(horizontal = 8.dp, vertical = 4.dp)
      .height(1.dp)
      .background(GlassMenuTokens.DividerColor)
  )
}

@Composable
fun GlassMenuRow(
  icon: ImageVector,
  label: String,
  tileColor: Color,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  hint: String? = null,
  isDestructive: Boolean = false,
  fontFamily: FontFamily = FontFamily.Default
) {
  val interactionSource = remember { MutableInteractionSource() }
  val isPressed by interactionSource.collectIsPressedAsState()
  val rowBg by animateColorAsState(
    targetValue = if (isPressed) {
      if (isDestructive) GlassMenuTokens.DestructiveRed.copy(alpha = 0.12f)
      else GlassMenuTokens.PressHighlight
    } else {
      Color.Transparent
    },
    animationSpec = tween(durationMillis = 120),
    label = "glass_menu_row_bg"
  )

  val rowShape = RoundedCornerShape(16.dp)
  val tileShape = RoundedCornerShape(GlassMenuTokens.IconTileRadius)
  val labelColor = if (isDestructive) GlassMenuTokens.DestructiveRed else GlassMenuTokens.TextPrimary

  Row(
    modifier = modifier
      .fillMaxWidth()
      .heightIn(min = if (hint != null) 52.dp else 50.dp)
      .clip(rowShape)
      .background(rowBg)
      .clickable(
        interactionSource = interactionSource,
        indication = ripple(color = if (isDestructive) GlassMenuTokens.DestructiveRed else Color.White.copy(alpha = 0.24f)),
        role = Role.Button,
        onClick = onClick
      )
      .padding(horizontal = 8.dp, vertical = if (hint != null) 8.dp else 7.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Box(
      modifier = Modifier
        .size(GlassMenuTokens.IconTileSize)
        .clip(tileShape)
        .background(tileColor.copy(alpha = 0.17f))
        .border(0.75.dp, tileColor.copy(alpha = 0.24f), tileShape),
      contentAlignment = Alignment.Center
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = tileColor,
        modifier = Modifier.size(18.dp)
      )
    }

    Spacer(modifier = Modifier.width(12.dp))

    Column(
      modifier = Modifier.weight(1f),
      verticalArrangement = Arrangement.Center
    ) {
      Text(
        text = label,
        fontFamily = fontFamily,
        fontSize = 15.sp,
        fontWeight = FontWeight.Medium,
        lineHeight = 19.sp,
        color = labelColor,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
      if (!hint.isNullOrBlank()) {
        Spacer(modifier = Modifier.height(1.dp))
        Text(
          text = hint,
          fontFamily = fontFamily,
          fontSize = 12.sp,
          fontWeight = FontWeight.Normal,
          lineHeight = 15.sp,
          color = GlassMenuTokens.HintGray,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
      }
    }
  }
}

@Composable
private fun AlbumHeroActionButton(
  icon: ImageVector,
  label: String,
  isPrimaryGradient: Boolean,
  isActive: Boolean = false,
  reducedMotion: Boolean = false,
  fontFamily: FontFamily,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  val interactionSource = remember { MutableInteractionSource() }
  val isPressed by interactionSource.collectIsPressedAsState()
  val scale by animateFloatAsState(
    targetValue = if (isPressed && !reducedMotion) 0.96f else 1f,
    animationSpec = if (reducedMotion) snap() else tween(durationMillis = 120),
    label = "album_menu_hero_scale"
  )
  val buttonShape = RoundedCornerShape(18.dp)

  val backgroundModifier = if (isPrimaryGradient) {
    Modifier.background(
      Brush.linearGradient(
        colors = listOf(
          GlassMenuTokens.BlueAccent,
          GlassMenuTokens.IndigoAccent
        )
      )
    )
  } else {
    Modifier.background(
      if (isActive) GlassMenuTokens.PurpleAccent.copy(alpha = 0.20f)
      else Color.White.copy(alpha = 0.08f)
    )
  }

  val borderColor = when {
    isPrimaryGradient -> Color.White.copy(alpha = 0.22f)
    isActive -> GlassMenuTokens.PurpleAccent.copy(alpha = 0.45f)
    else -> Color.White.copy(alpha = 0.12f)
  }

  Box(
    modifier = modifier
      .scale(scale)
      .height(68.dp)
      .clip(buttonShape)
      .then(backgroundModifier)
      .border(BorderStroke(1.dp, borderColor), buttonShape)
      .clickable(
        interactionSource = interactionSource,
        indication = ripple(color = Color.White.copy(alpha = 0.28f)),
        role = Role.Button,
        onClick = onClick
      )
      .padding(horizontal = 8.dp, vertical = 8.dp),
    contentAlignment = Alignment.Center
  ) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = when {
          isPrimaryGradient -> Color.White
          isActive -> GlassMenuTokens.PurpleAccent
          else -> GlassMenuTokens.TextPrimary
        },
        modifier = Modifier.size(22.dp)
      )
      Spacer(modifier = Modifier.height(4.dp))
      Text(
        text = label,
        fontFamily = fontFamily,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 16.sp,
        color = Color.White,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
    }
  }
}

/**
 * Menu 1: Album "..." Popup Menu
 * Opens under the three-dot button, aligned right.
 */
@Composable
fun AlbumMoreGlassMenu(
  expanded: Boolean,
  isAddedToLibrary: Boolean,
  isAlbumDownloaded: Boolean,
  isShuffleEnabled: Boolean,
  canResetCover: Boolean,
  reducedMotion: Boolean,
  onDismissRequest: () -> Unit,
  onPlayClick: () -> Unit,
  onShuffleClick: () -> Unit,
  onAddSongsClick: () -> Unit,
  onImportAudioClick: () -> Unit,
  onEditCoverClick: () -> Unit,
  onResetCoverClick: () -> Unit,
  onToggleLibraryClick: () -> Unit,
  onDownloadAlbumClick: () -> Unit,
  onDeleteAlbumClick: (() -> Unit)? = null
) {
  val fontFamily = AlbumDesignTokens.OutfitFontFamily

  GlassPopupMenuContainer(
    expanded = expanded,
    onDismissRequest = onDismissRequest,
    horizontalAlignment = Alignment.End,
    menuWidth = 264.dp,
    reducedMotion = reducedMotion,
    testTag = "album_more_glass_menu"
  ) {
    // 1. Top: Play (blue->indigo gradient, filled) & Shuffle (glass) side-by-side
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(bottom = 6.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      AlbumHeroActionButton(
        icon = Icons.Rounded.PlayArrow,
        label = stringResource(id = R.string.album_action_play),
        isPrimaryGradient = true,
        reducedMotion = reducedMotion,
        fontFamily = fontFamily,
        onClick = {
          onDismissRequest()
          onPlayClick()
        },
        modifier = Modifier
          .weight(1f)
          .testTag("album_menu_play")
      )
      AlbumHeroActionButton(
        icon = Icons.Rounded.Shuffle,
        label = stringResource(id = R.string.album_action_shuffle),
        isPrimaryGradient = false,
        isActive = isShuffleEnabled,
        reducedMotion = reducedMotion,
        fontFamily = fontFamily,
        onClick = {
          onDismissRequest()
          onShuffleClick()
        },
        modifier = Modifier
          .weight(1f)
          .testTag("album_menu_shuffle")
      )
    }

    // 2. Rows: Add songs, Import audio files, Edit cover, (Reset cover if customized), Add to library, Download album
    GlassMenuRow(
      icon = Icons.AutoMirrored.Rounded.PlaylistAdd,
      label = stringResource(id = R.string.album_add_songs),
      tileColor = GlassMenuTokens.BlueAccent,
      fontFamily = fontFamily,
      onClick = {
        onDismissRequest()
        onAddSongsClick()
      },
      modifier = Modifier.testTag("album_menu_add_songs")
    )

    GlassMenuRow(
      icon = Icons.Rounded.FolderOpen,
      label = stringResource(id = R.string.album_import_audio),
      tileColor = GlassMenuTokens.BlueAccent,
      fontFamily = fontFamily,
      onClick = {
        onDismissRequest()
        onImportAudioClick()
      },
      modifier = Modifier.testTag("album_menu_import_audio")
    )

    GlassMenuRow(
      icon = Icons.Rounded.Edit,
      label = stringResource(id = R.string.album_edit_cover),
      tileColor = GlassMenuTokens.PurpleAccent,
      fontFamily = fontFamily,
      onClick = {
        onDismissRequest()
        onEditCoverClick()
      },
      modifier = Modifier.testTag("album_menu_edit_cover")
    )

    if (canResetCover) {
      GlassMenuRow(
        icon = Icons.Rounded.Refresh,
        label = stringResource(id = R.string.album_reset_cover),
        tileColor = GlassMenuTokens.PurpleAccent,
        fontFamily = fontFamily,
        onClick = {
          onDismissRequest()
          onResetCoverClick()
        },
        modifier = Modifier.testTag("album_menu_reset_cover")
      )
    }

    GlassMenuRow(
      icon = if (isAddedToLibrary) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
      label = if (isAddedToLibrary) {
        stringResource(id = R.string.album_remove_from_library)
      } else {
        stringResource(id = R.string.album_add_to_library)
      },
      tileColor = GlassMenuTokens.BlueAccent,
      fontFamily = fontFamily,
      onClick = {
        onDismissRequest()
        onToggleLibraryClick()
      },
      modifier = Modifier.testTag("album_menu_toggle_library")
    )

    GlassMenuRow(
      icon = if (isAlbumDownloaded) Icons.Rounded.DownloadDone else Icons.Rounded.Download,
      label = stringResource(id = R.string.album_download),
      tileColor = GlassMenuTokens.BlueAccent,
      fontFamily = fontFamily,
      onClick = {
        onDismissRequest()
        onDownloadAlbumClick()
      },
      modifier = Modifier.testTag("album_menu_download")
    )

    // 3. Divider + 4. Last row alone: Delete album
    if (onDeleteAlbumClick != null) {
      GlassMenuDivider()
      GlassMenuRow(
        icon = Icons.Rounded.DeleteOutline,
        label = stringResource(id = R.string.album_delete),
        tileColor = GlassMenuTokens.DestructiveRed,
        isDestructive = true,
        fontFamily = fontFamily,
        onClick = {
          onDismissRequest()
          onDeleteAlbumClick()
        },
        modifier = Modifier.testTag("album_menu_delete_album")
      )
    }
  }
}

/**
 * Menu 2: Library Song-Row Popup Menu
 * Opens from the song's cover image.
 */
@Composable
fun LibrarySongGlassMenu(
  expanded: Boolean,
  reducedMotion: Boolean,
  onDismissRequest: () -> Unit,
  onAddToAlbumClick: (() -> Unit)?,
  onEditPhotoClick: (() -> Unit)?,
  onDeleteClick: (() -> Unit)?
) {
  val hasUpperItems = onAddToAlbumClick != null || onEditPhotoClick != null

  GlassPopupMenuContainer(
    expanded = expanded,
    onDismissRequest = onDismissRequest,
    horizontalAlignment = Alignment.Start,
    menuWidth = 248.dp,
    reducedMotion = reducedMotion,
    testTag = "library_song_glass_menu"
  ) {
    if (onAddToAlbumClick != null) {
      GlassMenuRow(
        icon = Icons.AutoMirrored.Rounded.PlaylistAdd,
        label = stringResource(id = R.string.song_menu_add_to_album),
        hint = stringResource(id = R.string.song_menu_add_to_album_hint),
        tileColor = GlassMenuTokens.BlueAccent,
        onClick = {
          onDismissRequest()
          onAddToAlbumClick()
        },
        modifier = Modifier.testTag("song_menu_add_to_album")
      )
    }

    if (onEditPhotoClick != null) {
      GlassMenuRow(
        icon = Icons.Rounded.AddPhotoAlternate,
        label = stringResource(id = R.string.song_menu_edit_cover),
        hint = stringResource(id = R.string.song_menu_edit_cover_hint),
        tileColor = GlassMenuTokens.PurpleAccent,
        onClick = {
          onDismissRequest()
          onEditPhotoClick()
        },
        modifier = Modifier.testTag("song_menu_edit_cover")
      )
    }

    if (onDeleteClick != null) {
      if (hasUpperItems) {
        GlassMenuDivider()
      }
      GlassMenuRow(
        icon = Icons.Rounded.DeleteOutline,
        label = stringResource(id = R.string.song_menu_remove),
        hint = stringResource(id = R.string.song_menu_remove_hint),
        tileColor = GlassMenuTokens.DestructiveRed,
        isDestructive = true,
        onClick = {
          onDismissRequest()
          onDeleteClick()
        },
        modifier = Modifier.testTag("song_menu_remove")
      )
    }
  }
}
