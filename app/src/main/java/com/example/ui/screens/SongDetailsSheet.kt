package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
  val (artPrimary, artSecondary) = rememberArtworkColors(song.artworkUrl, song.title)

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
    containerColor = MaterialTheme.colorScheme.surface,
    contentColor = MaterialTheme.colorScheme.onSurface,
    dragHandle = {
      Box(
        modifier = Modifier
          .padding(top = 12.dp, bottom = 6.dp)
          .width(42.dp)
          .height(4.dp)
          .clip(CircleShape)
          .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f))
      )
    }
  ) {
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .background(
          Brush.radialGradient(
            colors = listOf(
              artPrimary.copy(alpha = 0.22f),
              artSecondary.copy(alpha = 0.08f),
              Color.Transparent
            ),
            center = Offset(540f, 180f),
            radius = 720f
          )
        )
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .verticalScroll(rememberScrollState())
          .navigationBarsPadding()
          .padding(horizontal = 24.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        // Artwork with subtle glowing drop shadow container
        Surface(
          modifier = Modifier
            .size(132.dp)
            .border(
              1.dp,
              Color.White.copy(alpha = 0.16f),
              RoundedCornerShape(28.dp)
            ),
          shape = RoundedCornerShape(28.dp),
          shadowElevation = 18.dp,
          color = Color.Transparent
        ) {
          SongArtwork(
            song = song,
            modifier = Modifier.fillMaxSize(),
            cornerRadius = 28.dp,
            iconSize = 52.dp
          )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
          text = song.title,
          style = MaterialTheme.typography.titleLarge.copy(
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.2).sp
          ),
          color = MaterialTheme.colorScheme.onSurface,
          textAlign = TextAlign.Center,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
          text = song.artist,
          style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          textAlign = TextAlign.Center,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(22.dp))

        // Action Buttons Row (Play, Like, Share card, Delete)
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.48f))
            .border(
              1.dp,
              MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
              RoundedCornerShape(22.dp)
            )
            .padding(vertical = 12.dp, horizontal = 8.dp),
          horizontalArrangement = Arrangement.SpaceEvenly,
          verticalAlignment = Alignment.CenterVertically
        ) {
          IconButtonAction(
            icon = Icons.Rounded.PlayArrow,
            label = "Play",
            tint = MaterialTheme.colorScheme.primary,
            onClick = onPlay
          )
          IconButtonAction(
            icon = if (song.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
            label = if (song.isFavorite) "Liked" else "Like",
            tint = if (song.isFavorite) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            onClick = onFavorite
          )
          IconButtonAction(
            icon = Icons.Rounded.Share,
            label = "Share card",
            onClick = onShare,
            testTag = "details_share_lyrics_button"
          )
          if (onDelete != null) {
            IconButtonAction(
              icon = Icons.Rounded.DeleteOutline,
              label = "Delete",
              tint = MaterialTheme.colorScheme.error,
              onClick = { confirmDelete = true }
            )
          }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Track metadata info card
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.30f))
            .border(
              1.dp,
              MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
              RoundedCornerShape(20.dp)
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
          verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
          InfoRow(label = "Duration", value = "${song.durationSeconds / 60}:${String.format("%02d", song.durationSeconds % 60)}")
          HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
          InfoRow(label = "Source", value = song.source)
          HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
          InfoRow(label = "Queue Status", value = "Ready")
        }

        if (onDelete != null) {
          Spacer(modifier = Modifier.height(18.dp))
          OutlinedButton(
            onClick = { confirmDelete = true },
            shape = CircleShape,
            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.45f)),
            colors = ButtonDefaults.outlinedButtonColors(
              contentColor = MaterialTheme.colorScheme.error,
              containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.06f)
            ),
            modifier = Modifier
              .fillMaxWidth()
              .height(50.dp)
              .testTag("details_delete_song_button")
          ) {
            Icon(Icons.Rounded.DeleteOutline, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Delete from Library", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
          }
        }

        Spacer(modifier = Modifier.height(8.dp))
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
          tint = MaterialTheme.colorScheme.error,
          modifier = Modifier.size(28.dp)
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
fun IconButtonAction(
  icon: ImageVector,
  label: String,
  tint: Color = MaterialTheme.colorScheme.onSurface,
  testTag: String? = null,
  onClick: () -> Unit
) {
  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier = Modifier
      .minimumInteractiveComponentSize()
      .clip(RoundedCornerShape(14.dp))
      .clickable { onClick() }
      .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
      .padding(horizontal = 10.dp, vertical = 6.dp)
  ) {
    Surface(
      shape = CircleShape,
      color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
      modifier = Modifier.size(46.dp)
    ) {
      Box(contentAlignment = Alignment.Center) {
        Icon(imageVector = icon, contentDescription = label, tint = tint, modifier = Modifier.size(22.dp))
      }
    }
    Spacer(modifier = Modifier.height(6.dp))
    Text(
      text = label,
      style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )
  }
}

@Composable
fun InfoRow(label: String, value: String) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(vertical = 4.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
  ) {
    Text(text = label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(text = value, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface)
  }
}
