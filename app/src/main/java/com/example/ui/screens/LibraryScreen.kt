package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
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
  reducedMotion: Boolean,
  onAddLocalSong: (Uri) -> Unit,
  onSongClick: (Song) -> Unit,
  onFavoriteClick: (Song) -> Unit
) {
  var selectedTab by remember { mutableIntStateOf(0) }
  val tabs = listOf("My songs", "Favorites", "Recent")

  val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
    if (uri != null) {
      onAddLocalSong(uri)
    }
  }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .padding(horizontal = 20.dp)
      .windowInsetsPadding(WindowInsets.statusBars),
  ) {
    Spacer(modifier = Modifier.height(18.dp))
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text(
        text = "Library",
        style = MaterialTheme.typography.displayLarge.copy(fontSize = 28.sp, fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.onBackground
      )
      if (selectedTab == 0) {
        Button(
          onClick = { launcher.launch(arrayOf("audio/*")) },
          shape = RoundedCornerShape(999.dp)
        ) {
          Icon(Icons.Rounded.Add, contentDescription = null)
          Spacer(modifier = Modifier.width(4.dp))
          Text("Add local")
        }
      }
    }

    Spacer(modifier = Modifier.height(16.dp))

    Surface(
      shape = RoundedCornerShape(999.dp),
      color = MaterialTheme.colorScheme.surfaceVariant,
      modifier = Modifier.fillMaxWidth()
    ) {
      Row(modifier = Modifier.padding(4.dp)) {
        tabs.forEachIndexed { index, title ->
          val selected = selectedTab == index
          Box(
            modifier = Modifier
              .weight(1f)
              .clip(RoundedCornerShape(999.dp))
              .background(if (selected) MaterialTheme.colorScheme.surface else Color.Transparent)
              .clickable { selectedTab = index }
              .padding(vertical = 10.dp),
            contentAlignment = Alignment.Center
          ) {
            Text(
              text = title,
              style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
              color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }
      }
    }

    Spacer(modifier = Modifier.height(16.dp))

    val currentList = when (selectedTab) {
      0 -> localSongs
      1 -> favorites
      else -> recentlyPlayed
    }

    if (currentList.isEmpty()) {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .padding(32.dp),
        contentAlignment = Alignment.Center
      ) {
        val emptyMsg = when (selectedTab) {
          0 -> "Songs you add to the app show up here."
          1 -> "Tap the heart on any song to keep it here."
          else -> "Songs you play show up here."
        }
        Text(
          text = emptyMsg,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    } else {
      LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = PaddingValues(bottom = if (currentSong != null) 190.dp else 120.dp)
      ) {
        items(currentList, key = { "${selectedTab}_${it.id}_${it.title}" }) { song ->
          SongRow(
            song = song,
            isPlaying = isPlaying,
            isCurrent = currentSong?.id == song.id,
            reducedMotion = reducedMotion,
            onSongClick = { onSongClick(song) },
            onFavoriteClick = { onFavoriteClick(song) }
          )
        }
      }
    }
  }
}
