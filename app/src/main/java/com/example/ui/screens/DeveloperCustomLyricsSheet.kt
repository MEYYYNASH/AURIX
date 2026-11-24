package com.example.ui.screens

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.CustomLyricsEntity
import com.example.lyrics.LrcCodec
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeveloperCustomLyricsSheet(
  customLyrics: List<CustomLyricsEntity>,
  onShareLrc: (CustomLyricsEntity) -> Unit,
  onDeleteCustomLyrics: (String) -> Unit,
  onDismiss: () -> Unit
) {
  val context = LocalContext.current
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  var pendingDelete by remember { mutableStateOf<CustomLyricsEntity?>(null) }
  val dateFormatter = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = Color(0xFF0E1525),
    contentColor = Color.White,
    modifier = Modifier.testTag("developer_custom_lyrics_sheet")
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .navigationBarsPadding()
        .padding(horizontal = 20.dp)
        .padding(bottom = 24.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = stringResource(id = R.string.dev_mode_custom_lyrics_manager_title),
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = Color.White
          )
          Text(
            text = stringResource(id = R.string.dev_mode_section_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.68f)
          )
        }
        Surface(
          shape = CircleShape,
          color = Color(0xFF43C7F4).copy(alpha = 0.16f),
          border = BorderStroke(1.dp, Color(0xFF43C7F4).copy(alpha = 0.4f))
        ) {
          Text(
            text = "${customLyrics.size}",
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            color = Color(0xFF43C7F4),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
          )
        }
      }

      if (customLyrics.isEmpty()) {
        Surface(
          shape = RoundedCornerShape(18.dp),
          color = Color(0xFF151F30),
          border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Icon(
              imageVector = Icons.Rounded.Lyrics,
              contentDescription = null,
              tint = Color(0xFF43C7F4),
              modifier = Modifier.size(30.dp)
            )
            Text(
              text = stringResource(id = R.string.dev_mode_custom_lyrics_empty),
              style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
              color = Color.White.copy(alpha = 0.78f)
            )
          }
        }
      } else {
        LazyColumn(
          modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 420.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          items(customLyrics, key = { it.songId }) { item ->
            val lineCount = remember(item.lrcText) {
              LrcCodec.parseForEditor(item.lrcText).first.size
            }
            Surface(
              shape = RoundedCornerShape(18.dp),
              color = Color(0xFF151F30),
              border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
              modifier = Modifier.fillMaxWidth()
            ) {
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
              ) {
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = item.title.ifBlank { item.songId },
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                  )
                  Text(
                    text = buildString {
                      if (item.artist.isNotBlank()) append(item.artist).append(" · ")
                      append(stringResource(id = R.string.lyrics_editor_lines_count, lineCount))
                      append(" · ")
                      append(dateFormatter.format(Date(item.updatedAt)))
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.65f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                  )
                }

                IconButton(
                  onClick = {
                    onShareLrc(item)
                    LrcCodec.createShareLrcIntent(
                      context = context,
                      title = item.title.ifBlank { item.songId },
                      artist = item.artist,
                      lrcContent = item.lrcText
                    )?.let { intent ->
                      runCatching {
                        context.startActivity(
                          Intent.createChooser(
                            intent,
                            context.getString(R.string.lyrics_editor_share_lrc)
                          )
                        )
                      }
                    }
                  },
                  modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                  Icon(
                    imageVector = Icons.Rounded.Share,
                    contentDescription = stringResource(id = R.string.lyrics_editor_share_lrc),
                    tint = Color(0xFF43C7F4)
                  )
                }

                IconButton(
                  onClick = { pendingDelete = item },
                  modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                  Icon(
                    imageVector = Icons.Rounded.DeleteOutline,
                    contentDescription = stringResource(id = R.string.lyrics_editor_delete_custom),
                    tint = Color(0xFFEF4444)
                  )
                }
              }
            }
          }
        }
      }
    }
  }

  pendingDelete?.let { target ->
    AlertDialog(
      onDismissRequest = { pendingDelete = null },
      containerColor = Color(0xFF151F30),
      titleContentColor = Color.White,
      textContentColor = Color.White.copy(alpha = 0.85f),
      title = {
        Text(
          text = stringResource(id = R.string.lyrics_editor_delete_confirm_title),
          style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
        )
      },
      text = {
        Text(
          text = stringResource(id = R.string.lyrics_editor_delete_confirm_msg),
          style = MaterialTheme.typography.bodyMedium
        )
      },
      confirmButton = {
        Button(
          onClick = {
            val id = target.songId
            pendingDelete = null
            onDeleteCustomLyrics(id)
          },
          shape = CircleShape,
          colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444), contentColor = Color.White)
        ) {
          Text(
            text = stringResource(id = R.string.lyrics_editor_delete_confirm_yes),
            fontWeight = FontWeight.Bold
          )
        }
      },
      dismissButton = {
        TextButton(onClick = { pendingDelete = null }) {
          Text(
            text = stringResource(id = R.string.settings_dynamic_bg_reset_confirm_no),
            color = Color.White
          )
        }
      }
    )
  }
}
