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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Album
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
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.R
import com.example.model.APP_ICON_ARTWORK
import com.example.model.AlbumInfo
import com.example.model.Song

@Composable
fun CreateAlbumDialog(
  defaultArtist: String = "",
  onDismiss: () -> Unit,
  onCreate: (title: String, artist: String, year: String) -> Unit
) {
  var title by remember { mutableStateOf("") }
  var artist by remember { mutableStateOf(defaultArtist) }
  var year by remember { mutableStateOf("") }

  AlertDialog(
    onDismissRequest = onDismiss,
    icon = {
      Icon(
        imageVector = Icons.Rounded.Album,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary
      )
    },
    title = {
      Text(
        text = stringResource(id = R.string.album_create_title),
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
      )
    },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
          value = title,
          onValueChange = { title = it },
          label = { Text(stringResource(id = R.string.album_create_name_label)) },
          placeholder = { Text(stringResource(id = R.string.album_create_name_hint)) },
          singleLine = true,
          keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("create_album_title_input")
        )
        OutlinedTextField(
          value = artist,
          onValueChange = { artist = it },
          label = { Text(stringResource(id = R.string.album_create_artist_label)) },
          placeholder = { Text(stringResource(id = R.string.album_create_artist_hint)) },
          singleLine = true,
          keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("create_album_artist_input")
        )
        OutlinedTextField(
          value = year,
          onValueChange = { input ->
            year = input.filter { it.isDigit() }.take(4)
          },
          label = { Text(stringResource(id = R.string.album_create_year_label)) },
          singleLine = true,
          keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Number,
            imeAction = ImeAction.Done
          ),
          keyboardActions = KeyboardActions(onDone = {
            if (title.trim().isNotEmpty()) {
              onCreate(title.trim(), artist.trim(), year.trim())
            }
          }),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("create_album_year_input")
        )
      }
    },
    confirmButton = {
      Button(
        onClick = { onCreate(title.trim(), artist.trim(), year.trim()) },
        enabled = title.trim().isNotEmpty(),
        shape = CircleShape,
        modifier = Modifier.testTag("create_album_confirm_button")
      ) {
        Text(
          text = stringResource(id = R.string.album_create_confirm),
          fontWeight = FontWeight.Bold
        )
      }
    },
    dismissButton = {
      TextButton(
        onClick = onDismiss,
        modifier = Modifier.testTag("create_album_cancel_button")
      ) {
        Text(stringResource(id = R.string.action_cancel))
      }
    }
  )
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
  val sheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    shape = sheetShape,
    containerColor = AlbumDesignTokens.Surface,
    contentColor = AlbumDesignTokens.Text,
    scrimColor = Color.Black.copy(alpha = 0.56f),
    dragHandle = null
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .border(1.dp, AlbumDesignTokens.Border, sheetShape)
        .navigationBarsPadding()
        .padding(horizontal = 20.dp, vertical = 14.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      Box(
        modifier = Modifier
          .align(Alignment.CenterHorizontally)
          .width(40.dp)
          .height(4.dp)
          .clip(CircleShape)
          .background(Color.White.copy(alpha = 0.25f))
      )

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
            color = AlbumDesignTokens.Text,
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
            color = AlbumDesignTokens.DimText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }
        IconButton(
          onClick = onDismiss,
          modifier = Modifier
            .minimumInteractiveComponentSize()
            .testTag("album_hold_close_button")
        ) {
          Icon(
            imageVector = Icons.Rounded.Close,
            contentDescription = stringResource(id = R.string.cd_close_sheet),
            tint = AlbumDesignTokens.DimText
          )
        }
      }

      HorizontalDivider(color = AlbumDesignTokens.Border)

      AlbumSheetActionRow(
        icon = Icons.Rounded.OpenInNew,
        label = stringResource(id = R.string.album_hold_open),
        tint = AlbumDesignTokens.Text,
        testTag = "album_hold_open_button",
        onClick = {
          onDismiss()
          onOpenAlbum()
        }
      )

      AlbumSheetActionRow(
        icon = Icons.Rounded.PlaylistAdd,
        label = stringResource(id = R.string.album_add_songs),
        tint = AlbumDesignTokens.Accent,
        testTag = "album_hold_add_songs_button",
        onClick = {
          onDismiss()
          onAddSongs()
        }
      )

      AlbumSheetActionRow(
        icon = Icons.Rounded.DeleteOutline,
        label = stringResource(id = R.string.album_delete),
        tint = Color(0xFFFF6B6B),
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
      .defaultMinSize(minHeight = 48.dp)
      .clip(RoundedCornerShape(16.dp))
      .background(AlbumDesignTokens.RaisedSurface.copy(alpha = 0.72f))
      .border(1.dp, AlbumDesignTokens.Border, RoundedCornerShape(16.dp))
      .clickable(onClick = onClick)
      .padding(horizontal = 16.dp, vertical = 14.dp)
      .testTag(testTag),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    Icon(
      imageVector = icon,
      contentDescription = null,
      tint = tint,
      modifier = Modifier.size(22.dp)
    )
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

  AlertDialog(
    onDismissRequest = onDismiss,
    icon = {
      Icon(
        imageVector = Icons.Rounded.DeleteOutline,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.error
      )
    },
    title = {
      Text(
        text = stringResource(id = R.string.album_delete_title),
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
      )
    },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
          text = stringResource(id = R.string.album_delete_message, album.title, album.tracks.size),
          style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp)
        )
        if (localSongsCount > 0) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(12.dp))
              .clickable { alsoDeleteLocal = !alsoDeleteLocal }
              .padding(vertical = 4.dp)
              .testTag("delete_album_also_local_toggle"),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Checkbox(
              checked = alsoDeleteLocal,
              onCheckedChange = { alsoDeleteLocal = it }
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = stringResource(id = R.string.album_delete_also_local, localSongsCount),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }
      }
    },
    confirmButton = {
      Button(
        onClick = { onConfirm(alsoDeleteLocal) },
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
        shape = CircleShape,
        modifier = Modifier.testTag("confirm_delete_album_button")
      ) {
        Text(
          text = stringResource(id = R.string.action_delete),
          color = Color.White,
          fontWeight = FontWeight.Bold
        )
      }
    },
    dismissButton = {
      TextButton(
        onClick = onDismiss,
        modifier = Modifier.testTag("cancel_delete_album_button")
      ) {
        Text(stringResource(id = R.string.action_cancel))
      }
    }
  )
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

  val sheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    shape = sheetShape,
    containerColor = AlbumDesignTokens.Surface,
    contentColor = AlbumDesignTokens.Text,
    scrimColor = Color.Black.copy(alpha = 0.56f),
    dragHandle = null
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .heightIn(max = 580.dp)
        .border(1.dp, AlbumDesignTokens.Border, sheetShape)
        .navigationBarsPadding()
        .padding(horizontal = 20.dp, vertical = 14.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      Box(
        modifier = Modifier
          .align(Alignment.CenterHorizontally)
          .width(40.dp)
          .height(4.dp)
          .clip(CircleShape)
          .background(Color.White.copy(alpha = 0.25f))
      )

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
          color = AlbumDesignTokens.Text,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onDismiss) {
          Icon(Icons.Rounded.Close, contentDescription = stringResource(id = R.string.action_cancel), tint = AlbumDesignTokens.DimText)
        }
      }

      OutlinedButton(
        onClick = { importAudioLauncher.launch(arrayOf("audio/*")) },
        shape = CircleShape,
        border = BorderStroke(1.dp, AlbumDesignTokens.Accent.copy(alpha = 0.55f)),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = AlbumDesignTokens.Accent),
        modifier = Modifier
          .fillMaxWidth()
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
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        },
        leadingIcon = {
          Icon(Icons.Rounded.Search, contentDescription = null, tint = AlbumDesignTokens.DimText)
        },
        trailingIcon = {
          if (query.isNotEmpty()) {
            IconButton(onClick = { query = "" }) {
              Icon(Icons.Rounded.Close, contentDescription = null, tint = AlbumDesignTokens.DimText)
            }
          }
        },
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        colors = TextFieldDefaults.colors(
          focusedContainerColor = AlbumDesignTokens.RaisedSurface,
          unfocusedContainerColor = AlbumDesignTokens.RaisedSurface,
          focusedIndicatorColor = Color.Transparent,
          unfocusedIndicatorColor = Color.Transparent,
          focusedTextColor = AlbumDesignTokens.Text,
          unfocusedTextColor = AlbumDesignTokens.Text
        ),
        modifier = Modifier
          .fillMaxWidth()
          .border(1.dp, AlbumDesignTokens.Border, RoundedCornerShape(16.dp))
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
            color = AlbumDesignTokens.DimText
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
                .clip(RoundedCornerShape(16.dp))
                .background(
                  when {
                    isSelected -> AlbumDesignTokens.Accent.copy(alpha = 0.16f)
                    else -> AlbumDesignTokens.RaisedSurface.copy(alpha = 0.65f)
                  }
                )
                .border(
                  width = 1.dp,
                  color = if (isSelected) AlbumDesignTokens.Accent.copy(alpha = 0.55f) else AlbumDesignTokens.Border,
                  shape = RoundedCornerShape(16.dp)
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
                cornerRadius = 11.dp,
                iconSize = 20.dp
              )
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = song.title,
                  style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = AlbumDesignTokens.OutfitFontFamily,
                    fontWeight = FontWeight.SemiBold
                  ),
                  color = AlbumDesignTokens.Text,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis
                )
                Text(
                  text = "${song.artist} · ${song.source}",
                  style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = AlbumDesignTokens.OutfitFontFamily
                  ),
                  color = AlbumDesignTokens.DimText,
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
                    color = AlbumDesignTokens.DimText,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                  )
                }
              } else {
                Icon(
                  imageVector = if (isSelected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                  contentDescription = null,
                  tint = if (isSelected) AlbumDesignTokens.Accent else AlbumDesignTokens.DimText,
                  modifier = Modifier.size(22.dp)
                )
              }
            }
          }
        }
      }

      Button(
        onClick = {
          val chosen = availableSongs.filter { it.id in selectedIds }
          if (chosen.isNotEmpty()) {
            onAddSongs(chosen)
            onDismiss()
          }
        },
        enabled = selectedIds.isNotEmpty(),
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
          containerColor = AlbumDesignTokens.Accent,
          contentColor = AlbumDesignTokens.Background
        ),
        modifier = Modifier
          .fillMaxWidth()
          .height(50.dp)
          .testTag("add_songs_confirm_button")
      ) {
        Text(
          text = if (selectedIds.size == 1) {
            stringResource(id = R.string.album_add_songs_confirm_single)
          } else {
            stringResource(id = R.string.album_add_songs_confirm, selectedIds.size)
          },
          fontFamily = AlbumDesignTokens.OutfitFontFamily,
          fontWeight = FontWeight.Bold
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
  var showCreateDialog by remember { mutableStateOf(false) }
  val sheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    shape = sheetShape,
    containerColor = AlbumDesignTokens.Surface,
    contentColor = AlbumDesignTokens.Text,
    scrimColor = Color.Black.copy(alpha = 0.56f),
    dragHandle = null
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .heightIn(max = 540.dp)
        .border(1.dp, AlbumDesignTokens.Border, sheetShape)
        .navigationBarsPadding()
        .padding(horizontal = 20.dp, vertical = 14.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      Box(
        modifier = Modifier
          .align(Alignment.CenterHorizontally)
          .width(40.dp)
          .height(4.dp)
          .clip(CircleShape)
          .background(Color.White.copy(alpha = 0.25f))
      )

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
            color = AlbumDesignTokens.Text
          )
          Text(
            text = "${song.title} · ${song.artist}",
            style = MaterialTheme.typography.bodySmall.copy(
              fontFamily = AlbumDesignTokens.OutfitFontFamily
            ),
            color = AlbumDesignTokens.DimText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }
        IconButton(onClick = onDismiss) {
          Icon(Icons.Rounded.Close, contentDescription = stringResource(id = R.string.action_cancel), tint = AlbumDesignTokens.DimText)
        }
      }

      OutlinedButton(
        onClick = { showCreateDialog = true },
        shape = CircleShape,
        border = BorderStroke(1.dp, AlbumDesignTokens.Accent.copy(alpha = 0.55f)),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = AlbumDesignTokens.Accent),
        modifier = Modifier
          .fillMaxWidth()
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
              .clip(RoundedCornerShape(16.dp))
              .background(AlbumDesignTokens.RaisedSurface.copy(alpha = 0.72f))
              .border(1.dp, AlbumDesignTokens.Border, RoundedCornerShape(16.dp))
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
                color = AlbumDesignTokens.Text,
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
                color = AlbumDesignTokens.DimText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
            }
            if (alreadyAdded) {
              Surface(
                shape = CircleShape,
                color = AlbumDesignTokens.Accent.copy(alpha = 0.16f)
              ) {
                Text(
                  text = stringResource(id = R.string.album_pick_already_added),
                  style = MaterialTheme.typography.labelSmall,
                  color = AlbumDesignTokens.Accent,
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
  val shape = RoundedCornerShape(12.dp)
  val coverUrl = album.coverUrl
  Box(
    modifier = modifier
      .clip(shape)
      .background(
        Brush.linearGradient(
          colors = listOf(AlbumDesignTokens.CoverGradientStart, AlbumDesignTokens.CoverGradientEnd)
        )
      ),
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
