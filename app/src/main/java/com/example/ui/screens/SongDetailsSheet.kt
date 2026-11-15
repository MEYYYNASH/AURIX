package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.Song
import com.example.recommendation.SimilarTrack
import com.example.recommendation.TrackMetadataClassifier

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongDetailsSheet(
  song: Song,
  hasLyrics: Boolean = false,
  isPlaying: Boolean = false,
  reducedMotion: Boolean = false,
  maxSheetHeight: Dp = 540.dp,
  similarSongs: List<SimilarTrack> = emptyList(),
  onSelectSimilarSong: (Song) -> Unit = {},
  onDismiss: () -> Unit,
  onPlay: () -> Unit,
  onFavorite: () -> Unit,
  onShare: () -> Unit,
  onHideSong: () -> Unit,
  onBlockArtist: () -> Unit,
  onAddToAlbum: (() -> Unit)? = null,
  onDelete: (() -> Unit)? = null
) {
  val enrichedSong = remember(song) { TrackMetadataClassifier.enrich(song) }
  var confirmDelete by remember { mutableStateOf(false) }
  val (artPrimary, artSecondary) = rememberArtworkColors(song.artworkUrl, song.title)

  val surfaceColor = MaterialTheme.colorScheme.surface
  val bgColor = MaterialTheme.colorScheme.background
  val isDark = surfaceColor.luminance() < 0.5f || isSystemInDarkTheme()
  val isAmoled = isDark && bgColor == Color.Black

  val sheetFill = when {
    isAmoled -> Color(0xFF0A0E17).copy(alpha = 0.96f)
    isDark -> surfaceColor.copy(alpha = 0.92f)
    else -> surfaceColor.copy(alpha = 0.96f)
  }
  val topBorderBrush = Brush.verticalGradient(
    colors = if (isDark) {
      listOf(Color.White.copy(alpha = 0.24f), Color.White.copy(alpha = 0.03f))
    } else {
      listOf(Color.White.copy(alpha = 0.95f), Color.Black.copy(alpha = 0.06f))
    }
  )
  val dividerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
  val sheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    shape = sheetShape,
    containerColor = Color.Transparent,
    contentColor = MaterialTheme.colorScheme.onSurface,
    scrimColor = Color.Black.copy(alpha = 0.52f),
    tonalElevation = 0.dp,
    dragHandle = null
  ) {
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .heightIn(max = maxSheetHeight)
        .clip(sheetShape)
        .background(sheetFill)
        .background(
          Brush.radialGradient(
            colors = listOf(
              artPrimary.copy(alpha = if (isDark) 0.16f else 0.10f),
              artSecondary.copy(alpha = if (isDark) 0.06f else 0.04f),
              Color.Transparent
            ),
            center = Offset(180f, 120f),
            radius = 560f
          )
        )
        .border(1.dp, topBorderBrush, sheetShape)
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .verticalScroll(rememberScrollState())
          .navigationBarsPadding()
          .padding(horizontal = 20.dp)
          .padding(top = 10.dp, bottom = 16.dp)
      ) {
        // Drag handle
        Box(
          modifier = Modifier
            .align(Alignment.CenterHorizontally)
            .padding(bottom = 14.dp)
            .width(38.dp)
            .height(4.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f))
        )

        // Header: 64dp artwork on left + title (with LyricsTag) & artist on right
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
          Box(
            modifier = Modifier
              .size(64.dp)
              .clip(RoundedCornerShape(16.dp))
              .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                shape = RoundedCornerShape(16.dp)
              )
          ) {
            SongArtwork(
              song = song,
              modifier = Modifier.fillMaxSize(),
              cornerRadius = 16.dp,
              iconSize = 28.dp
            )
          }

          Column(modifier = Modifier.weight(1f)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text(
                text = song.title,
                style = MaterialTheme.typography.titleMedium.copy(
                  fontSize = 17.sp,
                  lineHeight = 24.sp,
                  fontWeight = FontWeight.Bold,
                  letterSpacing = (-0.2).sp
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
              )
              LyricsTag(
                visible = hasLyrics,
                size = LyricsTagSize.Standard,
                darkTheme = isDark,
                reducedMotion = reducedMotion,
                modifier = Modifier.padding(start = 8.dp)
              )
            }
            Spacer(modifier = Modifier.height(3.dp))
            Text(
              text = song.artist,
              style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 13.5.sp,
                lineHeight = 19.sp
              ),
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
          }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Actions: one equal row of 5 round 48dp buttons with small labels, no outer card
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(4.dp),
          verticalAlignment = Alignment.Top
        ) {
          SheetActionCircleButton(
            icon = Icons.Rounded.PlayArrow,
            label = stringResource(id = R.string.action_play),
            isDark = isDark,
            active = false,
            testTag = "details_play_button",
            modifier = Modifier.weight(1f),
            onClick = onPlay
          )
          SheetActionCircleButton(
            icon = if (song.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
            label = stringResource(id = if (song.isFavorite) R.string.action_liked else R.string.action_like),
            isDark = isDark,
            active = song.isFavorite,
            testTag = "details_like_button",
            modifier = Modifier.weight(1f),
            onClick = onFavorite
          )
          SheetActionCircleButton(
            icon = Icons.Rounded.Share,
            label = stringResource(id = R.string.action_share),
            isDark = isDark,
            active = false,
            testTag = "details_share_lyrics_button",
            modifier = Modifier.weight(1f),
            onClick = onShare
          )
          if (onAddToAlbum != null) {
            SheetActionCircleButton(
              icon = Icons.Rounded.PlaylistAdd,
              label = stringResource(id = R.string.details_add_to_album),
              isDark = isDark,
              active = false,
              testTag = "details_add_to_album_button",
              modifier = Modifier.weight(1f),
              onClick = {
                onDismiss()
                onAddToAlbum()
              }
            )
          }
          SheetActionCircleButton(
            icon = Icons.Rounded.VisibilityOff,
            label = stringResource(id = R.string.action_hide_song),
            isDark = isDark,
            active = false,
            testTag = "details_hide_song_button",
            modifier = Modifier.weight(1f),
            onClick = {
              onDismiss()
              onHideSong()
            }
          )
          SheetActionCircleButton(
            icon = Icons.Rounded.Block,
            label = stringResource(id = R.string.action_block_artist),
            isDark = isDark,
            active = false,
            testTag = "details_block_artist_button",
            modifier = Modifier.weight(1f),
            onClick = {
              onDismiss()
              onBlockArtist()
            }
          )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Details: simple rows with 1dp dividers, no outer box or border
        val displayDurSec = song.fullDurationSeconds.takeIf { it > 0L } ?: song.durationSeconds
        DetailInfoRow(
          label = stringResource(id = R.string.details_duration),
          value = "${displayDurSec / 60}:${String.format("%02d", displayDurSec % 60)}"
        )
        HorizontalDivider(thickness = 1.dp, color = dividerColor)
        DetailInfoRow(
          label = stringResource(id = R.string.details_source),
          value = song.source
        )
        HorizontalDivider(thickness = 1.dp, color = dividerColor)
        DetailInfoRow(
          label = stringResource(id = R.string.details_queue_status),
          value = stringResource(
            id = if (isPlaying) R.string.details_queue_playing else R.string.details_queue_ready
          )
        )
        HorizontalDivider(thickness = 1.dp, color = dividerColor)
        DetailInfoRow(
          label = stringResource(id = R.string.details_genre),
          value = enrichedSong.genre
        )
        HorizontalDivider(thickness = 1.dp, color = dividerColor)
        DetailInfoRow(
          label = stringResource(id = R.string.details_bpm),
          value = "${enrichedSong.bpm} BPM"
        )
        HorizontalDivider(thickness = 1.dp, color = dividerColor)
        DetailInfoRow(
          label = stringResource(id = R.string.details_mood),
          value = enrichedSong.mood
        )

        Spacer(modifier = Modifier.height(14.dp))
        Text(
          text = stringResource(id = R.string.details_similar_songs),
          style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
          color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        if (similarSongs.isEmpty()) {
          Text(
            text = stringResource(id = R.string.details_similar_empty),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp)
          )
        } else {
          Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            similarSongs.forEach { item ->
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .clip(RoundedCornerShape(14.dp))
                  .background(
                    MaterialTheme.colorScheme.onSurface.copy(alpha = if (isDark) 0.06f else 0.04f)
                  )
                  .clickable {
                    onDismiss()
                    onSelectSimilarSong(item.song)
                  }
                  .padding(horizontal = 10.dp, vertical = 8.dp)
                  .testTag("similar_song_row_${item.song.id}"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
              ) {
                SongArtwork(
                  song = item.song,
                  modifier = Modifier.size(40.dp),
                  cornerRadius = 10.dp,
                  iconSize = 18.dp
                )
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = item.song.title,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                  )
                  Text(
                    text = "${item.song.artist} · ${item.reason}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                  )
                }
                Surface(
                  shape = CircleShape,
                  color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                ) {
                  Text(
                    text = "${item.similarityScore}%",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                  )
                }
              }
            }
          }
        }

        if (onDelete != null) {
          Spacer(modifier = Modifier.height(10.dp))
          TextButton(
            onClick = { confirmDelete = true },
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            modifier = Modifier
              .align(Alignment.CenterHorizontally)
              .minimumInteractiveComponentSize()
              .testTag("details_delete_song_button")
          ) {
            Icon(Icons.Rounded.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = stringResource(id = R.string.details_delete_from_library),
              style = MaterialTheme.typography.bodySmall.copy(
                fontWeight = FontWeight.SemiBold,
                lineHeight = 18.sp
              )
            )
          }
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
          tint = MaterialTheme.colorScheme.error,
          modifier = Modifier.size(28.dp)
        )
      },
      title = {
        Text(
          text = stringResource(id = R.string.details_delete_title),
          style = MaterialTheme.typography.titleMedium.copy(
            fontWeight = FontWeight.Bold,
            lineHeight = 25.sp
          )
        )
      },
      text = {
        Text(
          text = stringResource(id = R.string.details_delete_message, song.title, song.artist),
          style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp)
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
          Text(
            text = stringResource(id = R.string.action_delete),
            color = Color.White,
            fontWeight = FontWeight.Bold
          )
        }
      },
      dismissButton = {
        TextButton(onClick = { confirmDelete = false }) {
          Text(text = stringResource(id = R.string.action_cancel))
        }
      }
    )
  }
}

@Composable
private fun SheetActionCircleButton(
  icon: ImageVector,
  label: String,
  isDark: Boolean,
  active: Boolean,
  testTag: String,
  modifier: Modifier = Modifier,
  onClick: () -> Unit
) {
  val accent = MaterialTheme.colorScheme.primary
  val circleFill = when {
    active -> accent.copy(alpha = if (isDark) 0.24f else 0.15f)
    isDark -> Color.White.copy(alpha = 0.08f)
    else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
  }
  val circleBorder = when {
    active -> accent.copy(alpha = 0.72f)
    isDark -> Color.White.copy(alpha = 0.14f)
    else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
  }
  val iconTint = if (active) accent else MaterialTheme.colorScheme.onSurface
  val labelTint = if (active) accent else MaterialTheme.colorScheme.onSurfaceVariant

  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier = modifier
      .minimumInteractiveComponentSize()
      .clip(RoundedCornerShape(14.dp))
      .clickable { onClick() }
      .testTag(testTag)
      .padding(horizontal = 2.dp, vertical = 4.dp)
  ) {
    Surface(
      shape = CircleShape,
      color = circleFill,
      border = BorderStroke(1.dp, circleBorder),
      modifier = Modifier.size(48.dp)
    ) {
      Box(contentAlignment = Alignment.Center) {
        Icon(
          imageVector = icon,
          contentDescription = label,
          tint = iconTint,
          modifier = Modifier.size(22.dp)
        )
      }
    }
    Spacer(modifier = Modifier.height(6.dp))
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall.copy(
        fontSize = 11.sp,
        lineHeight = 16.sp,
        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium
      ),
      color = labelTint,
      textAlign = TextAlign.Center,
      maxLines = 2,
      overflow = TextOverflow.Ellipsis
    )
  }
}

@Composable
private fun DetailInfoRow(label: String, value: String) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(vertical = 10.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.bodySmall.copy(
        fontSize = 13.sp,
        lineHeight = 19.sp
      ),
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(modifier = Modifier.width(12.dp))
    Text(
      text = value,
      style = MaterialTheme.typography.bodySmall.copy(
        fontSize = 13.sp,
        lineHeight = 19.sp,
        fontWeight = FontWeight.Bold
      ),
      color = MaterialTheme.colorScheme.onSurface,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis
    )
  }
}
