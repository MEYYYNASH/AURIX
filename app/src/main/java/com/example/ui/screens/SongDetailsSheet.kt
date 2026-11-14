package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.model.Song

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongDetailsSheet(
  song: Song,
  onDismiss: () -> Unit,
  onPlay: () -> Unit,
  onFavorite: () -> Unit,
  onShare: () -> Unit,
  onDelete: (() -> Unit)? = null
) {
  var confirmDelete by remember { mutableStateOf(false) }

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = rememberModalBottomSheetState(),
    shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(24.dp)
        .windowInsetsPadding(WindowInsets.safeDrawing),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      SongArtwork(
        song = song,
        modifier = Modifier.size(120.dp),
        cornerRadius = 24.dp,
        iconSize = 44.dp
      )

      Spacer(modifier = Modifier.height(16.dp))

      Text(
        text = song.title,
        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center
      )
      Text(
        text = song.artist,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
      )

      Spacer(modifier = Modifier.height(24.dp))

      HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

      Spacer(modifier = Modifier.height(16.dp))

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
      ) {
        IconButtonAction(icon = Icons.Rounded.PlayArrow, label = "Play", onClick = onPlay)
        IconButtonAction(
          icon = if (song.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
          label = "Like",
          tint = if (song.isFavorite) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
          onClick = onFavorite
        )
        IconButtonAction(icon = Icons.Rounded.Share, label = "Share", onClick = onShare)
        if (onDelete != null) {
          IconButtonAction(
            icon = Icons.Rounded.DeleteOutline,
            label = "Delete",
            tint = MaterialTheme.colorScheme.error,
            onClick = { confirmDelete = true }
          )
        }
      }

      Spacer(modifier = Modifier.height(24.dp))

      InfoRow(label = "Duration", value = "${song.durationSeconds / 60}:${String.format("%02d", song.durationSeconds % 60)}")
      InfoRow(label = "Source", value = song.source)
      InfoRow(label = "Queue Status", value = "Ready")

      if (onDelete != null) {
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedButton(
          onClick = { confirmDelete = true },
          shape = CircleShape,
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
          colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
          modifier = Modifier.fillMaxWidth().testTag("details_delete_song_button")
        ) {
          Icon(Icons.Rounded.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text("Delete from Library", fontWeight = FontWeight.Bold)
        }
      }
    }
  }

  if (confirmDelete && onDelete != null) {
    AlertDialog(
      onDismissRequest = { confirmDelete = false },
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
          text = "Remove \"${song.title}\" by ${song.artist} from your library?",
          style = MaterialTheme.typography.bodyMedium
        )
      },
      confirmButton = {
        Button(
          onClick = {
            confirmDelete = false
            onDismiss()
            onDelete()
          },
          colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
          shape = CircleShape
        ) {
          Text("Delete", color = Color.White, fontWeight = FontWeight.Bold)
        }
      },
      dismissButton = {
        TextButton(onClick = { confirmDelete = false }) {
          Text("Cancel")
        }
      }
    )
  }
}

@Composable
fun IconButtonAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, tint: Color = MaterialTheme.colorScheme.onSurface, onClick: () -> Unit) {
  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier = Modifier.clickable { onClick() }.padding(8.dp)
  ) {
    IconButton(onClick = onClick) {
      Icon(imageVector = icon, contentDescription = label, tint = tint)
    }
    Text(text = label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
  }
}

@Composable
fun InfoRow(label: String, value: String) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(vertical = 8.dp),
    horizontalArrangement = Arrangement.SpaceBetween
  ) {
    Text(text = label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(text = value, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface)
  }
}
