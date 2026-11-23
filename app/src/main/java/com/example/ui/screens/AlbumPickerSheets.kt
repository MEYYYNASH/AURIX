package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.R
import com.example.model.APP_ICON_ARTWORK
import com.example.model.AlbumInfo
import com.example.model.Song
import com.example.model.computeAlbumId

private val PickerGlassBg = Color(0xFF0A0810)
private val PickerGlassCard = Color(0xFF130F1F)
private val PickerGlassText = Color(0xFFF4F2FB)
private val PickerGlassMuted = Color(0xFF8F8AA6)
private val PickerGlassMagenta = Color(0xFFD946EF)

@Composable
private fun Modifier.darkGlassBottomSheetSurface(shape: RoundedCornerShape): Modifier {
  val pickerViolet = MaterialTheme.colorScheme.primary
  val pickerLightViolet = androidx.compose.ui.graphics.lerp(pickerViolet, Color.White, 0.32f)
  val pickerSecondaryGlow = androidx.compose.ui.graphics.lerp(pickerViolet, PickerGlassMagenta, 0.28f)
  val borderBrush = Brush.linearGradient(
    colors = listOf(
      Color.White.copy(alpha = 0.16f),
      pickerViolet.copy(alpha = 0.34f),
      pickerLightViolet.copy(alpha = 0.16f),
      Color.White.copy(alpha = 0.08f)
    )
  )
  return this
    .shadow(
      elevation = 24.dp,
      shape = shape,
      ambientColor = pickerViolet.copy(alpha = 0.30f),
      spotColor = pickerSecondaryGlow.copy(alpha = 0.24f)
    )
    .clip(shape)
    .background(PickerGlassBg)
    .drawBehind {
      drawCircle(
        brush = Brush.radialGradient(
          colors = listOf(pickerViolet.copy(alpha = 0.22f), Color.Transparent),
          center = Offset(size.width * 0.16f, size.height * 0.14f),
          radius = size.maxDimension * 0.65f
        ),
        radius = size.maxDimension * 0.65f,
        center = Offset(size.width * 0.16f, size.height * 0.14f)
      )
      drawCircle(
        brush = Brush.radialGradient(
          colors = listOf(pickerSecondaryGlow.copy(alpha = 0.14f), Color.Transparent),
          center = Offset(size.width * 0.88f, size.height * 0.78f),
          radius = size.maxDimension * 0.58f
        ),
        radius = size.maxDimension * 0.58f,
        center = Offset(size.width * 0.88f, size.height * 0.78f)
      )
      drawRect(
        brush = Brush.horizontalGradient(
          colors = listOf(
            Color.Transparent,
            pickerViolet.copy(alpha = 0.85f),
            pickerLightViolet,
            pickerViolet.copy(alpha = 0.85f),
            Color.Transparent
          )
        ),
        topLeft = Offset.Zero,
        size = Size(size.width, 2.5.dp.toPx())
      )
      drawRect(
        brush = Brush.verticalGradient(
          colors = listOf(pickerLightViolet.copy(alpha = 0.20f), Color.Transparent),
          startY = 0f,
          endY = 26.dp.toPx()
        ),
        topLeft = Offset.Zero,
        size = Size(size.width, 26.dp.toPx())
      )
    }
    .border(1.dp, borderBrush, shape)
}

@Composable
private fun DarkGlassDragHandle() {
  val pickerViolet = MaterialTheme.colorScheme.primary
  val pickerLightViolet = androidx.compose.ui.graphics.lerp(pickerViolet, Color.White, 0.32f)
  Box(
    modifier = Modifier
      .fillMaxWidth()
      .padding(top = 2.dp, bottom = 2.dp),
    contentAlignment = Alignment.Center
  ) {
    Box(
      modifier = Modifier
        .width(44.dp)
        .height(4.5.dp)
        .clip(CircleShape)
        .background(
          Brush.horizontalGradient(
            colors = listOf(
              pickerViolet.copy(alpha = 0.45f),
              pickerLightViolet.copy(alpha = 0.92f),
              pickerViolet.copy(alpha = 0.45f)
            )
          )
        )
    )
  }
}

@Composable
fun CreateAlbumDialog(
  defaultArtist: String = "",
  onDismiss: () -> Unit,
  onCreate: (title: String, artist: String, year: String) -> Unit
) {
  val context = LocalContext.current
  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(
      usePlatformDefaultWidth = false,
      decorFitsSystemWindows = false
    )
  ) {
    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(Color(0xFF0A0810).copy(alpha = 0.64f))
    ) {
      CreateAlbumBottomSheetOverlay(
        visible = true,
        defaultArtist = defaultArtist,
        reducedMotion = false,
        onDismiss = onDismiss,
        onCommitAndFly = { title, artist, year, preset, _ ->
          val resolvedArtist = artist.ifBlank { "Unknown Artist" }
          val albumId = computeAlbumId(title, resolvedArtist)
          AlbumCoverPresetStore.savePreset(context, albumId, preset)
          onCreate(title, artist, year)
        }
      )
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumHoldOptionsSheet(
  album: AlbumInfo,
  onDismiss: () -> Unit,
  onOpenAlbum: () -> Unit,
  onAddSongs: () -> Unit,
  onRequestDeleteAlbum: () -> Unit
) {
  val pickerLightViolet = androidx.compose.ui.graphics.lerp(MaterialTheme.colorScheme.primary, Color.White, 0.32f)
  val sheetShape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    shape = sheetShape,
    containerColor = Color.Transparent,
    contentColor = PickerGlassText,
    scrimColor = Color(0xFF05040A).copy(alpha = 0.70f),
    tonalElevation = 0.dp,
    dragHandle = null
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .darkGlassBottomSheetSurface(sheetShape)
        .navigationBarsPadding()
        .padding(horizontal = 20.dp, vertical = 14.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      DarkGlassDragHandle()

      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
      ) {
        AlbumMiniThumb(
          album = album,
          modifier = Modifier.size(56.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = album.title,
            style = MaterialTheme.typography.titleMedium.copy(
              fontFamily = AlbumDesignTokens.OutfitFontFamily,
              fontWeight = FontWeight.Bold
            ),
            color = PickerGlassText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
          val countText = when (album.tracks.size) {
            0 -> stringResource(id = R.string.album_meta_empty)
            1 -> stringResource(id = R.string.album_meta_single)
            else -> stringResource(id = R.string.album_meta_plural, album.tracks.size)
          }
          Text(
            text = "${album.artist} · $countText",
            style = MaterialTheme.typography.bodySmall.copy(
              fontFamily = AlbumDesignTokens.OutfitFontFamily
            ),
            color = PickerGlassMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }
        Surface(
          shape = CircleShape,
          color = Color.White.copy(alpha = 0.06f),
          border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
          modifier = Modifier
            .minimumInteractiveComponentSize()
            .size(38.dp)
            .clip(CircleShape)
            .clickable { onDismiss() }
            .testTag("album_hold_close_button")
        ) {
          Box(contentAlignment = Alignment.Center) {
            Icon(
              imageVector = Icons.Rounded.Close,
              contentDescription = stringResource(id = R.string.cd_close_sheet),
              tint = PickerGlassMuted,
              modifier = Modifier.size(18.dp)
            )
          }
        }
      }

      HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

      AlbumSheetActionRow(
        icon = Icons.Rounded.OpenInNew,
        label = stringResource(id = R.string.album_hold_open),
        tint = PickerGlassText,
        testTag = "album_hold_open_button",
        onClick = {
          onDismiss()
          onOpenAlbum()
        }
      )

      AlbumSheetActionRow(
        icon = Icons.Rounded.PlaylistAdd,
        label = stringResource(id = R.string.album_add_songs),
        tint = pickerLightViolet,
        testTag = "album_hold_add_songs_button",
        onClick = {
          onDismiss()
          onAddSongs()
        }
      )

      AlbumSheetActionRow(
        icon = Icons.Rounded.DeleteOutline,
        label = stringResource(id = R.string.album_delete),
        tint = Color(0xFFFB7185),
        testTag = "album_hold_delete_button",
        onClick = {
          onDismiss()
          onRequestDeleteAlbum()
        }
      )
    }
  }
}

@Composable
private fun AlbumSheetActionRow(
  icon: ImageVector,
  label: String,
  tint: Color,
  testTag: String,
  onClick: () -> Unit
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .defaultMinSize(minHeight = 52.dp)
      .clip(RoundedCornerShape(18.dp))
      .background(PickerGlassCard.copy(alpha = 0.85f))
      .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(18.dp))
      .clickable(onClick = onClick)
      .padding(horizontal = 16.dp, vertical = 14.dp)
      .testTag(testTag),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    Box(
      modifier = Modifier
        .size(34.dp)
        .clip(RoundedCornerShape(10.dp))
        .background(tint.copy(alpha = 0.14f))
        .border(1.dp, tint.copy(alpha = 0.28f), RoundedCornerShape(10.dp)),
      contentAlignment = Alignment.Center
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = tint,
        modifier = Modifier.size(18.dp)
      )
    }
    Text(
      text = label,
      style = MaterialTheme.typography.bodyLarge.copy(
        fontFamily = AlbumDesignTokens.OutfitFontFamily,
        fontWeight = FontWeight.SemiBold
      ),
      color = tint,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis
    )
  }
}

@Composable
fun DeleteAlbumConfirmDialog(
  album: AlbumInfo,
  onDismiss: () -> Unit,
  onConfirm: (alsoDeleteLocalSongs: Boolean) -> Unit
) {
  val localSongsCount = remember(album) {
    album.tracks.count {
      it.source == "Local" || it.source == "Downloaded" ||
        it.streamUrl.startsWith("file://") || it.streamUrl.startsWith("content://")
    }
  }
  var alsoDeleteLocal by remember(album.albumId) { mutableStateOf(false) }

  val primaryAccent = MaterialTheme.colorScheme.primary
  val dangerRose = Color(0xFFFB7185)
  val dangerCrimson = Color(0xFFE11D48)
  val cardShape = RoundedCornerShape(30.dp)
  val countLabel = when (album.tracks.size) {
    0 -> stringResource(id = R.string.album_meta_empty)
    1 -> stringResource(id = R.string.album_meta_single)
    else -> stringResource(id = R.string.album_meta_plural, album.tracks.size)
  }

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false)
  ) {
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 22.dp)
        .clip(cardShape)
        .background(Color(0xFF0A0810).copy(alpha = 0.98f))
        .drawBehind {
          drawCircle(
            brush = Brush.radialGradient(
              colors = listOf(
                dangerRose.copy(alpha = 0.22f),
                Color.Transparent
              ),
              center = Offset(size.width * 0.22f, size.height * 0.12f),
              radius = size.minDimension * 0.85f
            ),
            radius = size.minDimension * 0.85f,
            center = Offset(size.width * 0.22f, size.height * 0.12f)
          )
          drawCircle(
            brush = Brush.radialGradient(
              colors = listOf(
                primaryAccent.copy(alpha = 0.15f),
                Color.Transparent
              ),
              center = Offset(size.width * 0.86f, size.height * 0.88f),
              radius = size.minDimension * 0.78f
            ),
            radius = size.minDimension * 0.78f,
            center = Offset(size.width * 0.86f, size.height * 0.88f)
          )
          val lineInset = size.width * 0.18f
          drawLine(
            brush = Brush.horizontalGradient(
              colors = listOf(
                Color.Transparent,
                dangerRose.copy(alpha = 0.85f),
                Color.Transparent
              ),
              startX = lineInset,
              endX = size.width - lineInset
            ),
            start = Offset(lineInset, 1.dp.toPx()),
            end = Offset(size.width - lineInset, 1.dp.toPx()),
            strokeWidth = 2.dp.toPx()
          )
        }
        .border(
          width = 1.dp,
          brush = Brush.linearGradient(
            colors = listOf(
              dangerRose.copy(alpha = 0.42f),
              Color.White.copy(alpha = 0.10f),
              primaryAccent.copy(alpha = 0.24f)
            )
          ),
          shape = cardShape
        )
        .padding(horizontal = 22.dp, vertical = 22.dp)
    ) {
      Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
      ) {
        // Header row: Crimson glass trash icon capsule + Title & subtitle
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
          Box(
            modifier = Modifier
              .size(50.dp)
              .clip(RoundedCornerShape(16.dp))
              .background(
                Brush.linearGradient(
                  colors = listOf(
                    dangerRose.copy(alpha = 0.24f),
                    dangerCrimson.copy(alpha = 0.12f)
                  )
                )
              )
              .border(1.dp, dangerRose.copy(alpha = 0.44f), RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = Icons.Rounded.DeleteOutline,
              contentDescription = null,
              tint = dangerRose,
              modifier = Modifier.size(24.dp)
            )
          }

          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = stringResource(id = R.string.album_delete_title),
              style = MaterialTheme.typography.titleLarge.copy(
                fontFamily = AlbumDesignTokens.OutfitFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
              ),
              color = PickerGlassText
            )
            Text(
              text = countLabel,
              style = MaterialTheme.typography.labelMedium.copy(
                fontFamily = AlbumDesignTokens.OutfitFontFamily,
                color = dangerRose.copy(alpha = 0.88f),
                fontWeight = FontWeight.SemiBold
              )
            )
          }
        }

        // Embedded Album Mini-Preview Card
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF141122).copy(alpha = 0.90f))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(18.dp))
            .padding(12.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          AlbumMiniThumb(
            album = album,
            modifier = Modifier.size(48.dp)
          )
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = album.title,
              style = MaterialTheme.typography.bodyLarge.copy(
                fontFamily = AlbumDesignTokens.OutfitFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
              ),
              color = PickerGlassText,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
            Text(
              text = "${album.artist} · $countLabel",
              style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = AlbumDesignTokens.OutfitFontFamily,
                fontSize = 12.sp
              ),
              color = PickerGlassMuted,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
          }
        }

        Text(
          text = stringResource(id = R.string.album_delete_message, album.title, album.tracks.size),
          style = MaterialTheme.typography.bodyMedium.copy(
            fontFamily = AlbumDesignTokens.OutfitFontFamily,
            lineHeight = 20.sp,
            fontSize = 13.5.sp
          ),
          color = PickerGlassMuted
        )

        if (localSongsCount > 0) {
          val toggleShape = RoundedCornerShape(18.dp)
          val toggleBg = if (alsoDeleteLocal) {
            dangerRose.copy(alpha = 0.14f)
          } else {
            Color(0xFF131020).copy(alpha = 0.82f)
          }
          val toggleBorder = if (alsoDeleteLocal) {
            dangerRose.copy(alpha = 0.48f)
          } else {
            Color.White.copy(alpha = 0.09f)
          }

          Row(
            modifier = Modifier
              .fillMaxWidth()
              .clip(toggleShape)
              .background(toggleBg)
              .border(1.dp, toggleBorder, toggleShape)
              .clickable { alsoDeleteLocal = !alsoDeleteLocal }
              .padding(horizontal = 14.dp, vertical = 12.dp)
              .testTag("delete_album_also_local_toggle"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
          ) {
            Icon(
              imageVector = if (alsoDeleteLocal) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
              contentDescription = null,
              tint = if (alsoDeleteLocal) dangerRose else PickerGlassMuted,
              modifier = Modifier.size(22.dp)
            )
            Text(
              text = stringResource(id = R.string.album_delete_also_local, localSongsCount),
              style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = AlbumDesignTokens.OutfitFontFamily,
                fontWeight = if (alsoDeleteLocal) FontWeight.SemiBold else FontWeight.Medium,
                fontSize = 13.sp,
                lineHeight = 18.sp
              ),
              color = if (alsoDeleteLocal) PickerGlassText else PickerGlassMuted,
              modifier = Modifier.weight(1f)
            )
          }
        }

        // Side-by-side Dark Glass Cancel & Crimson Gradient Delete buttons
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(12.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Box(
            modifier = Modifier
              .weight(1f)
              .height(50.dp)
              .clip(RoundedCornerShape(16.dp))
              .background(Color.White.copy(alpha = 0.06f))
              .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
              .clickable { onDismiss() }
              .testTag("cancel_delete_album_button"),
            contentAlignment = Alignment.Center
          ) {
            Text(
              text = stringResource(id = R.string.action_cancel),
              style = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = AlbumDesignTokens.OutfitFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.5.sp
              ),
              color = PickerGlassText
            )
          }

          Box(
            modifier = Modifier
              .weight(1f)
              .height(50.dp)
              .clip(RoundedCornerShape(16.dp))
              .background(
                Brush.horizontalGradient(
                  colors = listOf(dangerCrimson, dangerRose)
                )
              )
              .border(1.dp, Color.White.copy(alpha = 0.24f), RoundedCornerShape(16.dp))
              .clickable { onConfirm(alsoDeleteLocal) }
              .testTag("confirm_delete_album_button"),
            contentAlignment = Alignment.Center
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
              Icon(
                imageVector = Icons.Rounded.DeleteOutline,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp)
              )
              Text(
                text = stringResource(id = R.string.action_delete),
                style = MaterialTheme.typography.bodyMedium.copy(
                  fontFamily = AlbumDesignTokens.OutfitFontFamily,
                  fontWeight = FontWeight.Bold,
                  fontSize = 14.5.sp
                ),
                color = Color.White
              )
            }
          }
        }
      }
    }
  }
}

@Composable
fun DeleteSongConfirmDialog(
  song: Song,
  onDismiss: () -> Unit,
  onConfirm: () -> Unit
) {
  val primaryAccent = MaterialTheme.colorScheme.primary
  val dangerRose = Color(0xFFFB7185)
  val dangerCrimson = Color(0xFFE11D48)
  val cardShape = RoundedCornerShape(30.dp)

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false)
  ) {
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 22.dp)
        .clip(cardShape)
        .background(Color(0xFF0A0810).copy(alpha = 0.98f))
        .drawBehind {
          drawCircle(
            brush = Brush.radialGradient(
              colors = listOf(
                dangerRose.copy(alpha = 0.22f),
                Color.Transparent
              ),
              center = Offset(size.width * 0.22f, size.height * 0.12f),
              radius = size.minDimension * 0.85f
            ),
            radius = size.minDimension * 0.85f,
            center = Offset(size.width * 0.22f, size.height * 0.12f)
          )
          drawCircle(
            brush = Brush.radialGradient(
              colors = listOf(
                primaryAccent.copy(alpha = 0.15f),
                Color.Transparent
              ),
              center = Offset(size.width * 0.86f, size.height * 0.88f),
              radius = size.minDimension * 0.78f
            ),
            radius = size.minDimension * 0.78f,
            center = Offset(size.width * 0.86f, size.height * 0.88f)
          )
          val lineInset = size.width * 0.18f
          drawLine(
            brush = Brush.horizontalGradient(
              colors = listOf(
                Color.Transparent,
                dangerRose.copy(alpha = 0.85f),
                Color.Transparent
              ),
              startX = lineInset,
              endX = size.width - lineInset
            ),
            start = Offset(lineInset, 1.dp.toPx()),
            end = Offset(size.width - lineInset, 1.dp.toPx()),
            strokeWidth = 2.dp.toPx()
          )
        }
        .border(
          width = 1.dp,
          brush = Brush.linearGradient(
            colors = listOf(
              dangerRose.copy(alpha = 0.42f),
              Color.White.copy(alpha = 0.10f),
              primaryAccent.copy(alpha = 0.24f)
            )
          ),
          shape = cardShape
        )
        .padding(horizontal = 22.dp, vertical = 22.dp)
    ) {
      Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
          Box(
            modifier = Modifier
              .size(50.dp)
              .clip(RoundedCornerShape(16.dp))
              .background(
                Brush.linearGradient(
                  colors = listOf(
                    dangerRose.copy(alpha = 0.24f),
                    dangerCrimson.copy(alpha = 0.12f)
                  )
                )
              )
              .border(1.dp, dangerRose.copy(alpha = 0.44f), RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = Icons.Rounded.DeleteOutline,
              contentDescription = null,
              tint = dangerRose,
              modifier = Modifier.size(24.dp)
            )
          }

          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = stringResource(id = R.string.details_delete_title),
              style = MaterialTheme.typography.titleLarge.copy(
                fontFamily = AlbumDesignTokens.OutfitFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
              ),
              color = PickerGlassText
            )
            Text(
              text = song.artist,
              style = MaterialTheme.typography.labelMedium.copy(
                fontFamily = AlbumDesignTokens.OutfitFontFamily,
                color = dangerRose.copy(alpha = 0.88f),
                fontWeight = FontWeight.SemiBold
              ),
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
          }
        }

        Text(
          text = stringResource(id = R.string.library_delete_confirm_msg, song.title, song.artist),
          style = MaterialTheme.typography.bodyMedium.copy(
            fontFamily = AlbumDesignTokens.OutfitFontFamily,
            lineHeight = 20.sp,
            fontSize = 13.5.sp
          ),
          color = PickerGlassMuted
        )

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(12.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Box(
            modifier = Modifier
              .weight(1f)
              .height(50.dp)
              .clip(RoundedCornerShape(16.dp))
              .background(Color.White.copy(alpha = 0.06f))
              .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
              .clickable { onDismiss() }
              .testTag("cancel_delete_song_button"),
            contentAlignment = Alignment.Center
          ) {
            Text(
              text = stringResource(id = R.string.action_cancel),
              style = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = AlbumDesignTokens.OutfitFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.5.sp
              ),
              color = PickerGlassText
            )
          }

          Box(
            modifier = Modifier
              .weight(1f)
              .height(50.dp)
              .clip(RoundedCornerShape(16.dp))
              .background(
                Brush.horizontalGradient(
                  colors = listOf(dangerCrimson, dangerRose)
                )
              )
              .border(1.dp, Color.White.copy(alpha = 0.24f), RoundedCornerShape(16.dp))
              .clickable { onConfirm() }
              .testTag("confirm_delete_song_button"),
            contentAlignment = Alignment.Center
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
              Icon(
                imageVector = Icons.Rounded.DeleteOutline,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp)
              )
              Text(
                text = stringResource(id = R.string.action_delete),
                style = MaterialTheme.typography.bodyMedium.copy(
                  fontFamily = AlbumDesignTokens.OutfitFontFamily,
                  fontWeight = FontWeight.Bold,
                  fontSize = 14.5.sp
                ),
                color = Color.White
              )
            }
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSongsToAlbumSheet(
  album: AlbumInfo,
  availableSongs: List<Song>,
  onDismiss: () -> Unit,
  onAddSongs: (List<Song>) -> Unit,
  onImportAudioFiles: (List<Uri>) -> Unit
) {
  val pickerViolet = MaterialTheme.colorScheme.primary
  val pickerLightViolet = androidx.compose.ui.graphics.lerp(pickerViolet, Color.White, 0.32f)
  var query by remember { mutableStateOf("") }
  val existingIds = remember(album.tracks) { album.tracks.map { it.id }.toSet() }
  var selectedIds by remember(album.albumId) { mutableStateOf<Set<String>>(emptySet()) }

  val filteredSongs = remember(availableSongs, query) {
    val clean = query.trim().lowercase()
    if (clean.isEmpty()) availableSongs
    else availableSongs.filter {
      it.title.lowercase().contains(clean) || it.artist.lowercase().contains(clean)
    }
  }

  val importAudioLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenMultipleDocuments()
  ) { uris ->
    if (uris.isNotEmpty()) {
      onImportAudioFiles(uris)
      onDismiss()
    }
  }

  val sheetShape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    shape = sheetShape,
    containerColor = Color.Transparent,
    contentColor = PickerGlassText,
    scrimColor = Color(0xFF05040A).copy(alpha = 0.70f),
    tonalElevation = 0.dp,
    dragHandle = null
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .heightIn(max = 580.dp)
        .darkGlassBottomSheetSurface(sheetShape)
        .navigationBarsPadding()
        .padding(horizontal = 20.dp, vertical = 14.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      DarkGlassDragHandle()

      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        Text(
          text = stringResource(id = R.string.album_add_songs_to, album.title),
          style = MaterialTheme.typography.titleMedium.copy(
            fontFamily = AlbumDesignTokens.OutfitFontFamily,
            fontWeight = FontWeight.Bold
          ),
          color = PickerGlassText,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onDismiss) {
          Icon(Icons.Rounded.Close, contentDescription = stringResource(id = R.string.action_cancel), tint = PickerGlassMuted)
        }
      }

      OutlinedButton(
        onClick = { importAudioLauncher.launch(arrayOf("audio/*")) },
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, pickerLightViolet.copy(alpha = 0.45f)),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = pickerLightViolet),
        modifier = Modifier
          .fillMaxWidth()
          .height(48.dp)
          .testTag("add_songs_import_audio_button")
      ) {
        Icon(Icons.Rounded.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(
          text = stringResource(id = R.string.album_add_songs_import),
          fontFamily = AlbumDesignTokens.OutfitFontFamily,
          fontWeight = FontWeight.SemiBold
        )
      }

      TextField(
        value = query,
        onValueChange = { query = it },
        placeholder = {
          Text(
            text = stringResource(id = R.string.album_add_songs_search_hint),
            fontFamily = AlbumDesignTokens.OutfitFontFamily,
            color = PickerGlassMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        },
        leadingIcon = {
          Icon(Icons.Rounded.Search, contentDescription = null, tint = pickerLightViolet)
        },
        trailingIcon = {
          if (query.isNotEmpty()) {
            IconButton(onClick = { query = "" }) {
              Icon(Icons.Rounded.Close, contentDescription = null, tint = PickerGlassMuted)
            }
          }
        },
        singleLine = true,
        shape = RoundedCornerShape(18.dp),
        colors = TextFieldDefaults.colors(
          focusedContainerColor = PickerGlassCard,
          unfocusedContainerColor = PickerGlassCard,
          focusedIndicatorColor = Color.Transparent,
          unfocusedIndicatorColor = Color.Transparent,
          focusedTextColor = PickerGlassText,
          unfocusedTextColor = PickerGlassText
        ),
        modifier = Modifier
          .fillMaxWidth()
          .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(18.dp))
          .testTag("add_songs_search_input")
      )

      if (filteredSongs.isEmpty()) {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .weight(1f, fill = false)
            .padding(vertical = 28.dp),
          contentAlignment = Alignment.Center
        ) {
          Text(
            text = stringResource(id = R.string.album_add_songs_empty),
            style = MaterialTheme.typography.bodyMedium.copy(
              fontFamily = AlbumDesignTokens.OutfitFontFamily
            ),
            color = PickerGlassMuted
          )
        }
      } else {
        LazyColumn(
          modifier = Modifier
            .fillMaxWidth()
            .weight(1f, fill = false),
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          itemsIndexed(
            items = filteredSongs,
            key = { idx, song -> "${idx}_${song.id}" }
          ) { _, song ->
            val alreadyIn = song.id in existingIds
            val isSelected = song.id in selectedIds
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(
                  when {
                    isSelected -> pickerViolet.copy(alpha = 0.18f)
                    else -> PickerGlassCard.copy(alpha = 0.78f)
                  }
                )
                .border(
                  width = 1.dp,
                  color = if (isSelected) pickerLightViolet.copy(alpha = 0.50f) else Color.White.copy(alpha = 0.08f),
                  shape = RoundedCornerShape(18.dp)
                )
                .clickable(enabled = !alreadyIn) {
                  selectedIds = if (isSelected) selectedIds - song.id else selectedIds + song.id
                }
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .testTag("add_songs_row_${song.id}"),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
              SongArtwork(
                song = song,
                modifier = Modifier.size(44.dp),
                cornerRadius = 12.dp,
                iconSize = 20.dp
              )
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = song.title,
                  style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = AlbumDesignTokens.OutfitFontFamily,
                    fontWeight = FontWeight.SemiBold
                  ),
                  color = PickerGlassText,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis
                )
                Text(
                  text = "${song.artist} · ${song.source}",
                  style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = AlbumDesignTokens.OutfitFontFamily
                  ),
                  color = PickerGlassMuted,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis
                )
              }
              if (alreadyIn) {
                Surface(
                  shape = CircleShape,
                  color = Color.White.copy(alpha = 0.08f)
                ) {
                  Text(
                    text = stringResource(id = R.string.album_add_songs_already_in),
                    style = MaterialTheme.typography.labelSmall,
                    color = PickerGlassMuted,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                  )
                }
              } else {
                Icon(
                  imageVector = if (isSelected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                  contentDescription = null,
                  tint = if (isSelected) pickerLightViolet else PickerGlassMuted,
                  modifier = Modifier.size(22.dp)
                )
              }
            }
          }
        }
      }

      val confirmEnabled = selectedIds.isNotEmpty()
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(52.dp)
          .clip(RoundedCornerShape(18.dp))
          .background(
            if (confirmEnabled) {
              Brush.horizontalGradient(listOf(pickerViolet, pickerLightViolet))
            } else {
              Brush.horizontalGradient(listOf(Color(0xFF272433), Color(0xFF211E2B)))
            }
          )
          .border(
            1.dp,
            if (confirmEnabled) Color.White.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.06f),
            RoundedCornerShape(18.dp)
          )
          .clickable(enabled = confirmEnabled) {
            val chosen = availableSongs.filter { it.id in selectedIds }
            if (chosen.isNotEmpty()) {
              onAddSongs(chosen)
              onDismiss()
            }
          }
          .testTag("add_songs_confirm_button"),
        contentAlignment = Alignment.Center
      ) {
        Text(
          text = if (selectedIds.size == 1) {
            stringResource(id = R.string.album_add_songs_confirm_single)
          } else {
            stringResource(id = R.string.album_add_songs_confirm, selectedIds.size)
          },
          fontFamily = AlbumDesignTokens.OutfitFontFamily,
          fontWeight = FontWeight.Bold,
          color = if (confirmEnabled) Color.White else PickerGlassMuted
        )
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSongToAlbumSheet(
  song: Song,
  albums: List<AlbumInfo>,
  onDismiss: () -> Unit,
  onSelectAlbum: (AlbumInfo) -> Unit,
  onCreateAlbumWithSong: (title: String, artist: String, year: String) -> Unit
) {
  val pickerViolet = MaterialTheme.colorScheme.primary
  val pickerLightViolet = androidx.compose.ui.graphics.lerp(pickerViolet, Color.White, 0.32f)
  var showCreateDialog by remember { mutableStateOf(false) }
  val sheetShape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    shape = sheetShape,
    containerColor = Color.Transparent,
    contentColor = PickerGlassText,
    scrimColor = Color(0xFF05040A).copy(alpha = 0.70f),
    tonalElevation = 0.dp,
    dragHandle = null
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .heightIn(max = 540.dp)
        .darkGlassBottomSheetSurface(sheetShape)
        .navigationBarsPadding()
        .padding(horizontal = 20.dp, vertical = 14.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      DarkGlassDragHandle()

      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = stringResource(id = R.string.album_pick_title),
            style = MaterialTheme.typography.titleMedium.copy(
              fontFamily = AlbumDesignTokens.OutfitFontFamily,
              fontWeight = FontWeight.Bold
            ),
            color = PickerGlassText
          )
          Text(
            text = "${song.title} · ${song.artist}",
            style = MaterialTheme.typography.bodySmall.copy(
              fontFamily = AlbumDesignTokens.OutfitFontFamily
            ),
            color = PickerGlassMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }
        IconButton(onClick = onDismiss) {
          Icon(Icons.Rounded.Close, contentDescription = stringResource(id = R.string.action_cancel), tint = PickerGlassMuted)
        }
      }

      OutlinedButton(
        onClick = { showCreateDialog = true },
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, pickerLightViolet.copy(alpha = 0.45f)),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = pickerLightViolet),
        modifier = Modifier
          .fillMaxWidth()
          .height(48.dp)
          .testTag("pick_album_create_new_button")
      ) {
        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(
          text = stringResource(id = R.string.album_pick_create_new),
          fontFamily = AlbumDesignTokens.OutfitFontFamily,
          fontWeight = FontWeight.SemiBold
        )
      }

      LazyColumn(
        modifier = Modifier
          .fillMaxWidth()
          .weight(1f, fill = false),
        verticalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        itemsIndexed(
          items = albums,
          key = { idx, album -> "${idx}_${album.albumId}" }
        ) { _, album ->
          val alreadyAdded = album.tracks.any { it.id == song.id }
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(18.dp))
              .background(PickerGlassCard.copy(alpha = 0.80f))
              .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(18.dp))
              .clickable(enabled = !alreadyAdded) {
                onSelectAlbum(album)
                onDismiss()
              }
              .padding(horizontal = 12.dp, vertical = 10.dp)
              .testTag("pick_album_row_${album.albumId}"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
          ) {
            AlbumMiniThumb(
              album = album,
              modifier = Modifier.size(46.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = album.title,
                style = MaterialTheme.typography.bodyMedium.copy(
                  fontFamily = AlbumDesignTokens.OutfitFontFamily,
                  fontWeight = FontWeight.SemiBold
                ),
                color = PickerGlassText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
              val countLabel = when (album.tracks.size) {
                0 -> stringResource(id = R.string.album_meta_empty)
                1 -> stringResource(id = R.string.album_meta_single)
                else -> stringResource(id = R.string.album_meta_plural, album.tracks.size)
              }
              Text(
                text = "${album.artist} · $countLabel",
                style = MaterialTheme.typography.bodySmall.copy(
                  fontFamily = AlbumDesignTokens.OutfitFontFamily
                ),
                color = PickerGlassMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
            }
            if (alreadyAdded) {
              Surface(
                shape = CircleShape,
                color = pickerViolet.copy(alpha = 0.18f),
                border = BorderStroke(1.dp, pickerLightViolet.copy(alpha = 0.30f))
              ) {
                Text(
                  text = stringResource(id = R.string.album_pick_already_added),
                  style = MaterialTheme.typography.labelSmall,
                  color = pickerLightViolet,
                  modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                )
              }
            }
          }
        }
      }
    }
  }

  if (showCreateDialog) {
    CreateAlbumDialog(
      defaultArtist = song.artist,
      onDismiss = { showCreateDialog = false },
      onCreate = { title, artist, year ->
        showCreateDialog = false
        onCreateAlbumWithSong(title, artist, year)
        onDismiss()
      }
    )
  }
}

@Composable
private fun AlbumMiniThumb(
  album: AlbumInfo,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val themeAccent = MaterialTheme.colorScheme.primary
  val shape = RoundedCornerShape(13.dp)
  val coverUrl = album.coverUrl
  val savedPreset = remember(album.albumId) {
    AlbumCoverPresetStore.getPresetOrNull(context, album.albumId)
  }
  val startColor = savedPreset?.resolveStartColor(themeAccent) ?: themeAccent
  val endColor = savedPreset?.resolveEndColor(themeAccent)
    ?: androidx.compose.ui.graphics.lerp(themeAccent, Color(0xFF090710), 0.65f)

  Box(
    modifier = modifier
      .clip(shape)
      .background(
        Brush.linearGradient(
          colors = listOf(startColor, endColor)
        )
      )
      .border(1.dp, Color.White.copy(alpha = 0.14f), shape),
    contentAlignment = Alignment.Center
  ) {
    when {
      coverUrl == APP_ICON_ARTWORK -> {
        Image(
          painter = painterResource(id = R.drawable.ic_launcher_foreground),
          contentDescription = album.title,
          contentScale = ContentScale.Fit,
          modifier = Modifier.fillMaxSize(0.84f)
        )
      }
      !coverUrl.isNullOrBlank() -> {
        AsyncImage(
          model = coverUrl,
          contentDescription = album.title,
          contentScale = ContentScale.Crop,
          modifier = Modifier.fillMaxSize()
        )
      }
      savedPreset != null -> {
        val letter = album.title.trim().firstOrNull()?.uppercaseChar()?.toString().orEmpty()
        Text(
          text = letter.ifEmpty { "A" },
          style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
          color = PickerGlassText
        )
      }
      else -> {
        Icon(
          imageVector = Icons.Rounded.MusicNote,
          contentDescription = album.title,
          tint = Color.White.copy(alpha = 0.88f),
          modifier = Modifier.size(22.dp)
        )
      }
    }
  }
}
