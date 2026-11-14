package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.Song

@Composable
fun LibraryScreen(
  localSongs: List<Song>,
  favorites: List<Song>,
  recentlyPlayed: List<Song>,
  currentSong: Song?,
  isPlaying: Boolean,
  darkTheme: Boolean = false,
  reducedMotion: Boolean,
  selectedTab: Int = 0,
  onTabSelected: (Int) -> Unit = {},
  onAddLocalSong: (Uri) -> Unit,
  onCustomizeSongPhoto: (String, Uri) -> Unit,
  onDeleteLocalSong: (Song) -> Unit = {},
  onRemoveRecentSong: (Song) -> Unit = {},
  onClearRecentHistory: () -> Unit = {},
  onSongClick: (Song) -> Unit,
  onFavoriteClick: (Song) -> Unit
) {
  var pendingPhotoSongId by remember { mutableStateOf<String?>(null) }
  var songPendingDelete by remember { mutableStateOf<Song?>(null) }

  val audioLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
    if (uri != null) {
      onAddLocalSong(uri)
    }
  }

  val photoPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { imageUri ->
    val targetId = pendingPhotoSongId
    pendingPhotoSongId = null
    if (imageUri != null && targetId != null) {
      onCustomizeSongPhoto(targetId, imageUri)
    }
  }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .padding(horizontal = 20.dp)
      .windowInsetsPadding(WindowInsets.statusBars),
  ) {
    Spacer(modifier = Modifier.height(16.dp))
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
        Text(
          text = "Library",
          style = MaterialTheme.typography.displayLarge.copy(fontSize = 28.sp, fontWeight = FontWeight.Bold),
          color = MaterialTheme.colorScheme.onBackground
        )
        Text(
          text = "Local audio, saved downloads & custom covers",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
      }
      when {
        selectedTab == 0 -> {
          Button(
            onClick = { audioLauncher.launch(arrayOf("audio/*")) },
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            modifier = Modifier.testTag("library_add_local_button")
          ) {
            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Add local", fontWeight = FontWeight.SemiBold)
          }
        }
        selectedTab == 2 && recentlyPlayed.isNotEmpty() -> {
          OutlinedButton(
            onClick = onClearRecentHistory,
            shape = CircleShape,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.45f)),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            modifier = Modifier.testTag("library_clear_recent_button")
          ) {
            Icon(Icons.Rounded.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Clear", fontWeight = FontWeight.SemiBold)
          }
        }
      }
    }

    Spacer(modifier = Modifier.height(16.dp))

    GlassSegmentedBar(
      options = listOf(
        "My songs" to null,
        "Favorites" to null,
        "Recent" to null
      ),
      selectedIndex = selectedTab,
      darkTheme = darkTheme,
      onSelect = onTabSelected
    )

    Spacer(modifier = Modifier.height(14.dp))

    val currentList = when (selectedTab) {
      0 -> localSongs
      1 -> favorites
      else -> recentlyPlayed
    }

    if (currentList.isEmpty()) {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .padding(bottom = 110.dp),
        contentAlignment = Alignment.Center
      ) {
        GlassCard(
          darkTheme = darkTheme,
          cornerRadius = 26.dp,
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
          ) {
            val (emptyIcon, emptyTitle, emptyMsg) = when (selectedTab) {
              0 -> Triple(
                Icons.Rounded.LibraryMusic,
                "No local songs yet",
                "Import audio from your phone (uses the AURIX icon by default) or download analyzed tracks."
              )
              1 -> Triple(
                Icons.Rounded.FavoriteBorder,
                "No favorites yet",
                "Tap the heart on any track to save it here."
              )
              else -> Triple(
                Icons.Rounded.History,
                "No recent plays",
                "Tracks you listen to will appear here automatically."
              )
            }

            Box(
              modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
              contentAlignment = Alignment.Center
            ) {
              Icon(
                imageVector = emptyIcon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
              )
            }

            Text(
              text = emptyTitle,
              style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
              color = MaterialTheme.colorScheme.onSurface
            )
            Text(
              text = emptyMsg,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              textAlign = TextAlign.Center
            )

            if (selectedTab == 0) {
              Spacer(modifier = Modifier.height(4.dp))
              Button(
                onClick = { audioLauncher.launch(arrayOf("audio/*")) },
                shape = CircleShape
              ) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Add audio file", fontWeight = FontWeight.SemiBold)
              }
            }
          }
        }
      }
    } else {
      LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        contentPadding = PaddingValues(bottom = if (currentSong != null) 195.dp else 125.dp)
      ) {
        if (selectedTab == 0) {
          item(key = "local_cover_tip") {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = if (darkTheme) 0.14f else 0.10f))
                .border(1.dp, Color.White.copy(alpha = if (darkTheme) 0.16f else 0.70f), RoundedCornerShape(14.dp))
                .padding(horizontal = 12.dp, vertical = 9.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              Icon(
                imageVector = Icons.Rounded.AddPhotoAlternate,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
              )
              Text(
                text = "Tap photo icon to set cover • Tap trash icon to delete song",
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface
              )
            }
          }
        }

        items(currentList, key = { "${selectedTab}_${it.id}_${it.artworkUrl}" }) { song ->
          SongRow(
            song = song,
            isPlaying = isPlaying,
            isCurrent = currentSong?.id == song.id,
            reducedMotion = reducedMotion,
            onSongClick = { onSongClick(song) },
            onFavoriteClick = { onFavoriteClick(song) },
            onEditPhotoClick = if (selectedTab == 0) {
              {
                pendingPhotoSongId = song.id
                photoPickerLauncher.launch(
                  PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
              }
            } else {
              null
            },
            onDeleteClick = when (selectedTab) {
              0 -> { { songPendingDelete = song } }
              1 -> { { onFavoriteClick(song) } }
              else -> { { onRemoveRecentSong(song) } }
            }
          )
        }
      }
    }
  }

  songPendingDelete?.let { target ->
    AlertDialog(
      onDismissRequest = { songPendingDelete = null },
      icon = {
        Icon(
          imageVector = Icons.Rounded.DeleteOutline,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.error
        )
      },
      title = {
        Text(
          text = "Delete song?",
          style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
        )
      },
      text = {
        Text(
          text = "Remove \"${target.title}\" by ${target.artist} from My songs and delete its saved audio file from AURIX?",
          style = MaterialTheme.typography.bodyMedium
        )
      },
      confirmButton = {
        Button(
          onClick = {
            songPendingDelete = null
            onDeleteLocalSong(target)
          },
          colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
          shape = CircleShape,
          modifier = Modifier.testTag("confirm_delete_song_button")
        ) {
          Text("Delete", color = Color.White, fontWeight = FontWeight.Bold)
        }
      },
      dismissButton = {
        TextButton(
          onClick = { songPendingDelete = null },
          modifier = Modifier.testTag("cancel_delete_song_button")
        ) {
          Text("Cancel")
        }
      }
    )
  }
}
