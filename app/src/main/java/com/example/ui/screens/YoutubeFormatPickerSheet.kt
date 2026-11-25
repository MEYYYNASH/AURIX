package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RadioButtonChecked
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.R
import com.example.model.AudioFormatOption
import com.example.model.Song
import com.example.model.YoutubeDownloadState
import com.example.model.YoutubeFormatSheetState
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YoutubeFormatPickerSheet(
  state: YoutubeFormatSheetState,
  darkTheme: Boolean,
  reducedMotion: Boolean,
  onDismiss: () -> Unit,
  onSelectFormat: (String) -> Unit,
  onPlayNow: () -> Unit,
  onDownload: () -> Unit,
  onRetry: () -> Unit,
  onOpenMySongs: () -> Unit
) {
  if (state is YoutubeFormatSheetState.Closed) return

  val song: Song = when (state) {
    is YoutubeFormatSheetState.Loading -> state.song
    is YoutubeFormatSheetState.Ready -> state.song
    is YoutubeFormatSheetState.Error -> state.song
    YoutubeFormatSheetState.Closed -> return
  }

  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  val (c1, c2) = rememberArtworkColors(song.artworkUrl, song.title)
  val sheetShape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
  val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

  val containerColor = if (darkTheme) Color(0xFF111829) else Color(0xFFF3F6FF)
  val cardBorder = if (darkTheme) Color.White.copy(alpha = 0.22f) else Color.White

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    shape = sheetShape,
    containerColor = containerColor,
    dragHandle = null,
    contentWindowInsets = { WindowInsets(0, 0, 0, 0) }
  ) {
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .background(
          Brush.verticalGradient(
            colors = if (darkTheme) {
              listOf(Color(0xFF1A243C), Color(0xFF111829), Color(0xFF0C1220))
            } else {
              listOf(Color.White, Color(0xFFF1F5FF), Color(0xFFE9EEFD))
            }
          )
        )
    ) {
      if (song.artworkUrl.isNotBlank()) {
        val formatBgReq = com.example.ui.image.rememberCrispArtworkRequest(model = song.artworkUrl)
        AsyncImage(
          model = formatBgReq,
          contentDescription = null,
          contentScale = ContentScale.Crop,
          filterQuality = com.example.ui.image.AurixImagePipeline.ARTWORK_FILTER_QUALITY,
          modifier = Modifier
            .matchParentSize()
            .alpha(if (darkTheme) 0.16f else 0.10f)
            .blur(42.dp)
        )
      }

      Box(
        modifier = Modifier
          .matchParentSize()
          .background(
            Brush.radialGradient(
              colors = listOf(
                c1.copy(alpha = if (darkTheme) 0.24f else 0.14f),
                c2.copy(alpha = if (darkTheme) 0.10f else 0.06f),
                Color.Transparent
              ),
              center = Offset(180f, 80f),
              radius = 900f
            )
          )
      )

      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 20.dp)
          .padding(top = 12.dp, bottom = 22.dp + navBottom),
        verticalArrangement = Arrangement.spacedBy(14.dp)
      ) {
        // Glass handle bar + Close
        Box(modifier = Modifier.fillMaxWidth()) {
          Box(
            modifier = Modifier
              .align(Alignment.Center)
              .width(42.dp)
              .height(4.dp)
              .clip(CircleShape)
              .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f))
          )
          IconButton(
            onClick = onDismiss,
            modifier = Modifier
              .align(Alignment.CenterEnd)
              .minimumInteractiveComponentSize()
              .testTag("format_sheet_close_button")
          ) {
            Icon(
              imageVector = Icons.Rounded.Close,
              contentDescription = stringResource(id = R.string.np_close_cd),
              tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }

        // Song Hero Header with Quick Play CTA
        GlassCard(
          darkTheme = darkTheme,
          cornerRadius = 24.dp,
          modifier = Modifier.fillMaxWidth()
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
          ) {
            Box(
              modifier = Modifier
                .size(58.dp)
                .clip(RoundedCornerShape(15.dp))
                .border(1.dp, cardBorder, RoundedCornerShape(15.dp))
            ) {
              SongArtwork(song = song, modifier = Modifier.fillMaxSize(), cornerRadius = 15.dp)
            }

            Column(
              modifier = Modifier.weight(1f),
              verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
              Text(
                text = song.title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
              Text(
                text = song.artist,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
              val durSec = (state as? YoutubeFormatSheetState.Ready)?.analysis?.durationSeconds
                ?.takeIf { it > 0L } ?: song.durationSeconds
              if (durSec > 0L) {
                Text(
                  text = String.format(Locale.US, "YouTube • %d:%02d", durSec / 60L, durSec % 60L),
                  style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                  color = MaterialTheme.colorScheme.primary
                )
              }
            }

            Surface(
              shape = CircleShape,
              color = MaterialTheme.colorScheme.primary,
              modifier = Modifier
                .minimumInteractiveComponentSize()
                .clip(CircleShape)
                .clickable { onPlayNow() }
                .testTag("sheet_play_button")
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
              ) {
                Icon(
                  imageVector = Icons.Rounded.PlayArrow,
                  contentDescription = null,
                  tint = Color.White,
                  modifier = Modifier.size(18.dp)
                )
                Text(
                  text = stringResource(id = R.string.action_play),
                  style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                  color = Color.White
                )
              }
            }
          }
        }

        // Dynamic Body
        when (state) {
          is YoutubeFormatSheetState.Loading -> {
            Box(
              modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
            ) {
              StatusRow(
                text = stringResource(id = R.string.yt_fetching_formats),
                runner = OrbiRunner.SyncDown,
                outcome = OrbiStatusOutcome.Loading,
                reducedMotion = reducedMotion
              )
            }
          }

          is YoutubeFormatSheetState.Error -> {
            GlassCard(
              darkTheme = darkTheme,
              cornerRadius = 22.dp,
              modifier = Modifier.fillMaxWidth()
            ) {
              Column(
                modifier = Modifier.padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
              ) {
                OrbiView(
                  state = OrbiState.Sad,
                  size = 76.dp,
                  runner = OrbiRunner.None,
                  reducedMotion = reducedMotion
                )
                Text(
                  text = state.message,
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  textAlign = TextAlign.Center
                )
                Button(
                  onClick = onRetry,
                  shape = CircleShape,
                  modifier = Modifier
                    .defaultMinSize(minHeight = 48.dp)
                    .testTag("sheet_retry_button")
                ) {
                  Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                  Spacer(modifier = Modifier.width(6.dp))
                  Text(text = stringResource(id = R.string.try_again), fontWeight = FontWeight.Bold)
                }
              }
            }
          }

          is YoutubeFormatSheetState.Ready -> {
            val selectedFormat = state.analysis.formats.firstOrNull { it.id == state.selectedFormatId }
              ?: state.analysis.formats.firstOrNull()
            val isDownloading = state.downloadState is YoutubeDownloadState.Downloading

            Text(
              text = stringResource(id = R.string.yt_select_format_title),
              style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
              color = MaterialTheme.colorScheme.onSurface
            )

            Column(
              modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 260.dp)
                .verticalScroll(rememberScrollState()),
              verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              state.analysis.formats.forEach { option ->
                FormatOptionRow(
                  option = option,
                  selected = option.id == selectedFormat?.id,
                  enabled = !isDownloading,
                  darkTheme = darkTheme,
                  onClick = { onSelectFormat(option.id) }
                )
              }
            }

            when (val dlState = state.downloadState) {
              is YoutubeDownloadState.Downloading -> {
                val pct = (dlState.progress * 100f).toInt().coerceIn(0, 100)
                val bytesText = if (dlState.totalBytes > 0L) {
                  String.format(
                    Locale.US,
                    "%.2f / %.2f MB",
                    dlState.downloadedBytes / (1024f * 1024f),
                    dlState.totalBytes / (1024f * 1024f)
                  )
                } else {
                  String.format(Locale.US, "%.2f MB", dlState.downloadedBytes / (1024f * 1024f))
                }
                Column(
                  modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.38f), RoundedCornerShape(18.dp))
                    .padding(14.dp),
                  verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    Text(
                      text = "Downloading ${dlState.formatLabel}",
                      style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                      color = MaterialTheme.colorScheme.onSurface,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis,
                      modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                      text = "$pct% ($bytesText)",
                      style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                      color = MaterialTheme.colorScheme.primary
                    )
                  }
                  LinearProgressIndicator(
                    progress = { dlState.progress.coerceIn(0f, 1f) },
                    modifier = Modifier
                      .fillMaxWidth()
                      .height(8.dp)
                      .clip(CircleShape),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                  )
                }
              }

              is YoutubeDownloadState.Saved -> {
                Column(
                  modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xFF10B981).copy(alpha = 0.15f))
                    .border(1.dp, Color(0xFF10B981).copy(alpha = 0.42f), RoundedCornerShape(18.dp))
                    .padding(14.dp),
                  verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                  Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                  ) {
                    Icon(
                      imageVector = Icons.Rounded.DownloadDone,
                      contentDescription = null,
                      tint = Color(0xFF10B981),
                      modifier = Modifier.size(20.dp)
                    )
                    Text(
                      text = stringResource(id = R.string.yt_saved_banner),
                      style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                      color = MaterialTheme.colorScheme.onSurface
                    )
                  }
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                  ) {
                    Button(
                      onClick = onPlayNow,
                      modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("sheet_play_offline_button"),
                      shape = CircleShape
                    ) {
                      Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                      Spacer(modifier = Modifier.width(4.dp))
                      Text(stringResource(id = R.string.yt_play_offline), fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                    OutlinedButton(
                      onClick = {
                        onDismiss()
                        onOpenMySongs()
                      },
                      modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("sheet_open_library_button"),
                      shape = CircleShape,
                      border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.50f))
                    ) {
                      Icon(Icons.Rounded.LibraryMusic, contentDescription = null, modifier = Modifier.size(17.dp))
                      Spacer(modifier = Modifier.width(6.dp))
                      Text(stringResource(id = R.string.my_songs), fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                  }
                }
              }

              is YoutubeDownloadState.Error -> {
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.error.copy(alpha = 0.14f))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                  Icon(
                    imageVector = Icons.Rounded.ErrorOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp)
                  )
                  Text(
                    text = dlState.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                  )
                }
              }

              YoutubeDownloadState.Idle -> Unit
            }

            Button(
              onClick = onDownload,
              enabled = !isDownloading && selectedFormat != null,
              modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("sheet_download_button"),
              shape = CircleShape,
              contentPadding = PaddingValues(horizontal = 18.dp),
              colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
              if (isDownloading) {
                CircularProgressIndicator(
                  modifier = Modifier.size(18.dp),
                  strokeWidth = 2.dp,
                  color = Color.White
                )
              } else {
                Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(19.dp))
              }
              Spacer(modifier = Modifier.width(8.dp))
              val ctaText = when {
                isDownloading -> stringResource(id = R.string.settings_downloading_update)
                state.downloadState is YoutubeDownloadState.Saved -> stringResource(id = R.string.yt_download_another)
                selectedFormat != null -> stringResource(
                  id = R.string.yt_install_format,
                  selectedFormat.formatLabel,
                  selectedFormat.qualityLabel
                )
                else -> stringResource(id = R.string.album_track_download)
              }
              Text(
                text = ctaText,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
            }
          }

          YoutubeFormatSheetState.Closed -> Unit
        }
      }
    }
  }
}

@Composable
internal fun FormatOptionRow(
  option: AudioFormatOption,
  selected: Boolean,
  enabled: Boolean,
  darkTheme: Boolean,
  onClick: () -> Unit
) {
  val accent = MaterialTheme.colorScheme.primary
  val shape = RoundedCornerShape(18.dp)
  val bgBrush = when {
    selected -> Brush.linearGradient(
      colors = listOf(
        accent.copy(alpha = if (darkTheme) 0.28f else 0.18f),
        accent.copy(alpha = if (darkTheme) 0.14f else 0.08f)
      )
    )
    darkTheme -> Brush.linearGradient(
      colors = listOf(Color(0xFF1F2B45).copy(alpha = 0.72f), Color(0xFF151E32).copy(alpha = 0.62f))
    )
    else -> Brush.linearGradient(
      colors = listOf(Color.White.copy(alpha = 0.90f), Color(0xFFF3F6FF).copy(alpha = 0.78f))
    )
  }
  val borderColor = when {
    selected -> accent
    darkTheme -> Color.White.copy(alpha = 0.20f)
    else -> Color.White
  }

  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clip(shape)
      .background(bgBrush)
      .border(if (selected) 1.5.dp else 1.dp, borderColor, shape)
      .drawBehind {
        if (!selected) {
          val stroke = 1.dp.toPx()
          if (size.width <= stroke || size.height <= stroke) return@drawBehind
          val inset = stroke * 0.5f
          val r = (18.dp.toPx() - inset).coerceAtLeast(0f)
          drawRoundRect(
            brush = Brush.verticalGradient(
              colors = listOf(Color.White.copy(alpha = if (darkTheme) 0.24f else 0.88f), Color.Transparent),
              startY = 0f,
              endY = size.height * 0.45f
            ),
            topLeft = Offset(inset, inset),
            size = Size(size.width - stroke, size.height - stroke),
            cornerRadius = CornerRadius(r, r),
            style = Stroke(width = stroke)
          )
        }
      }
      .clickable(enabled = enabled) { onClick() }
      .minimumInteractiveComponentSize()
      .testTag("youtube_format_${option.id}")
      .padding(horizontal = 14.dp, vertical = 12.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    Surface(
      shape = RoundedCornerShape(11.dp),
      color = if (selected) accent else accent.copy(alpha = 0.16f)
    ) {
      Text(
        text = option.formatLabel,
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold),
        color = if (selected) Color.White else accent,
        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
      )
    }

    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = option.qualityLabel,
        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.onSurface
      )
      Text(
        text = option.detailLabel,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
    }

    Icon(
      imageVector = if (selected) Icons.Rounded.RadioButtonChecked else Icons.Rounded.RadioButtonUnchecked,
      contentDescription = if (selected) "Selected" else "Select format",
      tint = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant
    )
  }
}
