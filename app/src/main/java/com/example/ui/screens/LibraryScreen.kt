package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.Song

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
  localSongs: List<Song>,
  favorites: List<Song>,
  recentlyPlayed: List<Song>,
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
      .padding(horizontal = 16.dp)
      .windowInsetsPadding(WindowInsets.safeDrawing),
  ) {
    Spacer(modifier = Modifier.height(16.dp))
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text(
        text = "Library",
        style = MaterialTheme.typography.displayLarge.copy(fontSize = 28.sp),
        color = MaterialTheme.colorScheme.onBackground
      )
      if (selectedTab == 0) {
        Button(onClick = { launcher.launch(arrayOf("audio/*")) }) {
          Icon(Icons.Rounded.Add, contentDescription = null)
          Spacer(modifier = Modifier.width(4.dp))
          Text("Add local")
        }
      }
    }

    Spacer(modifier = Modifier.height(16.dp))

    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
      tabs.forEachIndexed { index, title ->
        SegmentedButton(
          selected = selectedTab == index,
          onClick = { selectedTab = index },
          shape = SegmentedButtonDefaults.itemShape(index = index, count = tabs.size)
        ) {
          Text(title)
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
        Text(
          text = "No songs found in this tab. Add local music or like songs!",
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    } else {
      LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 100.dp)
      ) {
        items(currentList) { song ->
          SongRow(song = song, onSongClick = { onSongClick(song) }, onFavoriteClick = { onFavoriteClick(song) })
        }
      }
    }
  }
}
