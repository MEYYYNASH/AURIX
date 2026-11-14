package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.Song

@Composable
fun SearchScreen(
  searchResults: List<Song>,
  isSearching: Boolean,
  currentSong: Song?,
  isPlaying: Boolean,
  reducedMotion: Boolean,
  onSearchQueryChanged: (String) -> Unit,
  onSongClick: (Song) -> Unit,
  onFavoriteClick: (Song) -> Unit
) {
  var query by remember { mutableStateOf("") }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .padding(horizontal = 20.dp)
      .windowInsetsPadding(WindowInsets.statusBars),
  ) {
    Spacer(modifier = Modifier.height(18.dp))
    Text(
      text = "Search",
      style = MaterialTheme.typography.displayLarge.copy(fontSize = 28.sp, fontWeight = FontWeight.Bold),
      color = MaterialTheme.colorScheme.onBackground
    )
    Spacer(modifier = Modifier.height(14.dp))
    OutlinedTextField(
      value = query,
      onValueChange = {
        query = it
        onSearchQueryChanged(it)
      },
      placeholder = { Text("Search songs and artists") },
      leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
      trailingIcon = {
        if (query.isNotEmpty()) {
          IconButton(onClick = { query = ""; onSearchQueryChanged("") }) {
            Icon(Icons.Rounded.Close, contentDescription = "Clear search")
          }
        }
      },
      shape = RoundedCornerShape(999.dp),
      modifier = Modifier.fillMaxWidth(),
      singleLine = true
    )

    Spacer(modifier = Modifier.height(16.dp))

    if (isSearching) {
      Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
      }
    } else if (searchResults.isEmpty() && query.isNotBlank()) {
      Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("No matches. Try a different title or artist.", color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
    } else if (searchResults.isEmpty()) {
      Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Type a song or an artist name.", color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
    } else {
      LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = PaddingValues(bottom = if (currentSong != null) 190.dp else 120.dp)
      ) {
        items(searchResults, key = { it.id }) { song ->
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
