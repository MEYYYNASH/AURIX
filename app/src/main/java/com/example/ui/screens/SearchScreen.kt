package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.model.Song

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
  searchResults: List<Song>,
  isSearching: Boolean,
  onSearchQueryChanged: (String) -> Unit,
  onSongClick: (Song) -> Unit,
  onFavoriteClick: (Song) -> Unit
) {
  var query by remember { mutableStateOf("") }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .padding(horizontal = 16.dp)
      .windowInsetsPadding(WindowInsets.safeDrawing),
  ) {
    Spacer(modifier = Modifier.height(16.dp))
    OutlinedTextField(
      value = query,
      onValueChange = {
        query = it
        onSearchQueryChanged(it)
      },
      placeholder = { Text("Search songs, artists...") },
      leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
      shape = RoundedCornerShape(28.dp),
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
        Text("No results found.", color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
    } else {
      LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 100.dp)
      ) {
        items(searchResults) { song ->
          SongRow(song = song, onSongClick = { onSongClick(song) }, onFavoriteClick = { onFavoriteClick(song) })
        }
      }
    }
  }
}
