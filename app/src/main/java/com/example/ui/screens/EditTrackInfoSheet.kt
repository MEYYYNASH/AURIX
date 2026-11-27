package com.example.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.TrackMetadataOverride
import com.example.data.TrackMetadataOverrideStore
import com.example.model.Song
import kotlinx.coroutines.launch

private val QUICK_EDIT_GENRES = listOf(
  "Pop", "R&B", "Lo-fi", "Hip-Hop", "Khmer", "Acoustic", "Indie", "Electronic", "Phonk"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditTrackInfoSheet(
  song: Song,
  existingOverride: TrackMetadataOverride?,
  onSave: (TrackMetadataOverride) -> Unit,
  onReset: () -> Unit,
  onDismiss: () -> Unit
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f || isSystemInDarkTheme()
  val accent = MaterialTheme.colorScheme.primary

  var title by remember(song.id, existingOverride) {
    mutableStateOf(existingOverride?.title ?: song.title)
  }
  var artist by remember(song.id, existingOverride) {
    mutableStateOf(existingOverride?.artist ?: song.artist)
  }
  var album by remember(song.id, existingOverride) {
    mutableStateOf(existingOverride?.album.orEmpty())
  }
  var year by remember(song.id, existingOverride) {
    mutableStateOf(existingOverride?.year ?: song.year)
  }
  var genre by remember(song.id, existingOverride) {
    mutableStateOf(existingOverride?.genre ?: song.genre)
  }
  var artworkPath by remember(song.id, existingOverride) {
    mutableStateOf(existingOverride?.artworkPath ?: song.artworkUrl)
  }
  var isImportingCover by remember { mutableStateOf(false) }

  // Zero-permission Android Photo Picker
  val photoPickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.PickVisualMedia()
  ) { uri: Uri? ->
    if (uri != null) {
      isImportingCover = true
      scope.launch {
        val saved = TrackMetadataOverrideStore.importTrackCoverFromUri(context, song.id, uri)
        isImportingCover = false
        if (saved != null) {
          artworkPath = saved
        } else {
          Toast.makeText(context, "Could not load selected photo", Toast.LENGTH_SHORT).show()
        }
      }
    }
  }

  val sheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
  val sheetFill = if (isDark) Color(0xFF0E1424).copy(alpha = 0.97f) else MaterialTheme.colorScheme.surface

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    shape = sheetShape,
    containerColor = Color.Transparent,
    scrimColor = Color.Black.copy(alpha = 0.56f),
    dragHandle = null
  ) {
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .clip(sheetShape)
        .background(sheetFill)
        .border(
          1.dp,
          Brush.verticalGradient(
            listOf(Color.White.copy(alpha = if (isDark) 0.22f else 0.7f), Color.Transparent)
          ),
          sheetShape
        )
        .testTag("edit_track_info_sheet")
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .verticalScroll(rememberScrollState())
          .navigationBarsPadding()
          .imePadding()
          .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
      ) {
        // Drag handle
        Box(
          modifier = Modifier
            .align(Alignment.CenterHorizontally)
            .width(38.dp)
            .height(4.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f))
        )

        // Header
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            Box(
              modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(accent.copy(alpha = 0.16f)),
              contentAlignment = Alignment.Center
            ) {
              Icon(
                imageVector = Icons.Rounded.Edit,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(20.dp)
              )
            }
            Column {
              Text(
                text = stringResource(id = R.string.edit_track_info_title),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
              )
              Text(
                text = stringResource(id = R.string.edit_track_info_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          }
          Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (existingOverride != null) {
              IconButton(
                onClick = {
                  onReset()
                  onDismiss()
                },
                modifier = Modifier
                  .minimumInteractiveComponentSize()
                  .testTag("edit_track_reset_button")
              ) {
                Icon(
                  imageVector = Icons.Rounded.RestartAlt,
                  contentDescription = stringResource(id = R.string.eq_reset),
                  tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
            }
            IconButton(
              onClick = onDismiss,
              modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("edit_track_close_button")
            ) {
              Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = stringResource(id = R.string.cd_close_sheet),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          }
        }

        // Cover Art Picker Row
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = if (isDark) 0.06f else 0.04f))
            .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f), RoundedCornerShape(18.dp))
            .clickable {
              photoPickerLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
              )
            }
            .padding(12.dp)
            .testTag("edit_track_pick_cover_row"),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
          Box(
            modifier = Modifier
              .size(64.dp)
              .clip(RoundedCornerShape(14.dp))
              .background(accent.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
          ) {
            if (isImportingCover) {
              CircularProgressIndicator(strokeWidth = 2.5.dp, modifier = Modifier.size(24.dp))
            } else if (artworkPath.isNotBlank()) {
              AsyncImage(
                model = artworkPath,
                contentDescription = stringResource(id = R.string.edit_track_change_cover),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
              )
            } else {
              SongArtwork(song = song, modifier = Modifier.fillMaxSize(), cornerRadius = 14.dp, iconSize = 26.dp)
            }
          }
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = stringResource(id = R.string.edit_track_change_cover),
              style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
              color = MaterialTheme.colorScheme.onSurface
            )
            Text(
              text = stringResource(id = R.string.edit_track_change_cover_sub),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
          Icon(
            imageVector = Icons.Rounded.PhotoLibrary,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(22.dp)
          )
        }

        // Metadata Text Fields
        OutlinedTextField(
          value = title,
          onValueChange = { title = it },
          label = { Text(stringResource(id = R.string.edit_track_field_title)) },
          singleLine = true,
          shape = RoundedCornerShape(16.dp),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("edit_track_title_input")
        )

        OutlinedTextField(
          value = artist,
          onValueChange = { artist = it },
          label = { Text(stringResource(id = R.string.edit_track_field_artist)) },
          singleLine = true,
          shape = RoundedCornerShape(16.dp),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("edit_track_artist_input")
        )

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          OutlinedTextField(
            value = album,
            onValueChange = { album = it },
            label = { Text(stringResource(id = R.string.edit_track_field_album)) },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
              .weight(1.4f)
              .testTag("edit_track_album_input")
          )
          OutlinedTextField(
            value = year,
            onValueChange = { if (it.length <= 4) year = it.filter { ch -> ch.isDigit() } },
            label = { Text(stringResource(id = R.string.edit_track_field_year)) },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
              .weight(0.8f)
              .testTag("edit_track_year_input")
          )
        }

        // Quick Genre Chips
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(
            text = stringResource(id = R.string.edit_track_field_genre),
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            QUICK_EDIT_GENRES.forEach { g ->
              val selected = genre.equals(g, ignoreCase = true)
              Surface(
                onClick = { genre = g },
                shape = CircleShape,
                color = if (selected) accent.copy(alpha = 0.22f)
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
                border = BorderStroke(
                  1.dp,
                  if (selected) accent.copy(alpha = 0.72f)
                  else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                ),
                modifier = Modifier.defaultMinSize(minHeight = 36.dp)
              ) {
                Text(
                  text = g,
                  style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                  ),
                  color = if (selected) accent else MaterialTheme.colorScheme.onSurface,
                  modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                )
              }
            }
          }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Save Action Button
        Button(
          onClick = {
            val cleanedTitle = title.trim().ifBlank { song.title }
            val cleanedArtist = artist.trim().ifBlank { song.artist }
            onSave(
              TrackMetadataOverride(
                songId = song.id,
                title = cleanedTitle,
                artist = cleanedArtist,
                album = album.trim().takeIf { it.isNotEmpty() },
                year = year.trim().takeIf { it.isNotEmpty() },
                genre = genre.trim().takeIf { it.isNotEmpty() },
                artworkPath = artworkPath.takeIf { it.isNotBlank() && it != song.artworkUrl }
                  ?: existingOverride?.artworkPath,
                updatedAtMs = System.currentTimeMillis()
              )
            )
            onDismiss()
          },
          shape = RoundedCornerShape(18.dp),
          modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .testTag("edit_track_save_button")
        ) {
          Text(
            text = stringResource(id = R.string.edit_track_save_button),
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
          )
        }
      }
    }
  }
}
