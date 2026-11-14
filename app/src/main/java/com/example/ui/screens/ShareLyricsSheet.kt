package com.example.ui.screens

import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.example.lyrics.LrcParser
import com.example.model.APP_ICON_ARTWORK
import com.example.model.LyricsResult
import com.example.model.Song
import com.example.share.ShareCardData
import com.example.share.ShareCardExporter
import com.example.share.ShareCardMode
import com.example.share.ShareGradientTheme
import com.example.share.ShareLyricsCard
import com.example.share.SharePaletteExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val MAX_SELECTED_LYRIC_LINES = 5

private data class ShareableLyricLine(
  val index: Int,
  val timestampMs: Long,
  val text: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareLyricsSheet(
  song: Song,
  lyricsResult: LyricsResult?,
  currentPositionMs: Long,
  lyricOffsetMs: Long = 0L,
  onDismiss: () -> Unit
) {
  val context = LocalContext.current
  val coroutineScope = rememberCoroutineScope()
  val accentColor = MaterialTheme.colorScheme.primary
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

  // Parse lyric lines from rawLrc, synced lines, or plain text
  val parsedLyrics = remember(lyricsResult) {
    extractShareableLyricLines(lyricsResult)
  }
  val hasLyrics = parsedLyrics.isNotEmpty()

  // Compute initial active line + next line whenever the sheet opens
  val initialSelection = remember(parsedLyrics) {
    computeInitialLineSelection(
      lines = parsedLyrics,
      effectivePositionMs = (currentPositionMs + lyricOffsetMs).coerceAtLeast(0L)
    )
  }

  var cardMode by remember(hasLyrics) {
    mutableStateOf(if (hasLyrics) ShareCardMode.LYRICS else ShareCardMode.SONG)
  }
  var selectedIndices by remember(initialSelection) {
    mutableStateOf(initialSelection)
  }
  var isEditingLyrics by remember { mutableStateOf(false) }

  // Load album art bitmap (if available) and extract 4 Palette-driven gradient themes off main thread
  var artworkBitmap by remember(song.id, song.artworkUrl) { mutableStateOf<Bitmap?>(null) }
  val (fallbackC1, fallbackC2) = remember(song.title) { artworkHueColors(song.title) }
  var gradientThemes by remember(song.id, accentColor) {
    mutableStateOf(
      listOf(
        ShareGradientTheme(
          id = "album_palette",
          name = "Album",
          startColor = fallbackC1,
          midColor = fallbackC2,
          endColor = Color(0xFF0A0D18),
          accentGlowColor = accentColor
        ),
        ShareGradientTheme(
          id = "midnight_blue",
          name = "Midnight",
          startColor = Color(0xFF1D4ED8),
          midColor = Color(0xFF1E1B4B),
          endColor = Color(0xFF090D1A),
          accentGlowColor = Color(0xFF38BDF8)
        ),
        ShareGradientTheme(
          id = "crimson_rose",
          name = "Sunset",
          startColor = Color(0xFFBE185D),
          midColor = Color(0xFF4C0519),
          endColor = Color(0xFF130712),
          accentGlowColor = Color(0xFFFB7185)
        ),
        ShareGradientTheme(
          id = "emerald_teal",
          name = "Emerald",
          startColor = Color(0xFF0F766E),
          midColor = Color(0xFF064E3B),
          endColor = Color(0xFF071717),
          accentGlowColor = Color(0xFF2DD4BF)
        )
      )
    )
  }
  var selectedThemeIndex by remember { mutableIntStateOf(0) }

  LaunchedEffect(song.artworkUrl, song.source, song.title, accentColor) {
    val loadedBitmap = withContext(Dispatchers.IO) {
      val hasRealArtwork = song.artworkUrl.isNotBlank() && song.artworkUrl != APP_ICON_ARTWORK
      if (!hasRealArtwork) {
        null
      } else {
        try {
          val request = ImageRequest.Builder(context)
            .data(song.artworkUrl)
            .allowHardware(false)
            .size(640)
            .build()
          val result = context.imageLoader.execute(request)
          (result as? SuccessResult)?.drawable?.toBitmap()
        } catch (_: Exception) {
          null
        }
      }
    }
    artworkBitmap = loadedBitmap
    gradientThemes = SharePaletteExtractor.buildThemes(
      bitmap = loadedBitmap,
      fallbackTitle = song.title,
      accentColor = accentColor
    )
  }

  // Always order selected lines in song order regardless of tap order
  val orderedSelectedLines = remember(selectedIndices, parsedLyrics) {
    selectedIndices
      .sorted()
      .mapNotNull { idx -> parsedLyrics.getOrNull(idx)?.text }
  }

  val activeTheme = gradientThemes.getOrElse(selectedThemeIndex) { gradientThemes.first() }
  val shareCardData = remember(
    song.title,
    song.artist,
    artworkBitmap,
    orderedSelectedLines,
    cardMode,
    activeTheme
  ) {
    ShareCardData(
      title = song.title,
      artist = song.artist,
      artworkBitmap = artworkBitmap,
      selectedLines = orderedSelectedLines,
      mode = if (hasLyrics) cardMode else ShareCardMode.SONG,
      theme = activeTheme,
      brandName = "DCEAY MUSIC"
    )
  }

  var isSharing by remember { mutableStateOf(false) }
  var isSaving by remember { mutableStateOf(false) }

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    containerColor = MaterialTheme.colorScheme.surface,
    contentColor = MaterialTheme.colorScheme.onSurface,
    dragHandle = null
  ) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .windowInsetsPadding(WindowInsets.safeDrawing)
        .padding(horizontal = 20.dp, vertical = 14.dp),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      // Top Sheet Header: "Share lyrics" + Close (X) button
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = "Share lyrics",
          style = MaterialTheme.typography.titleLarge.copy(
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
          ),
          color = MaterialTheme.colorScheme.onSurface
        )
        IconButton(
          onClick = onDismiss,
          modifier = Modifier
            .minimumInteractiveComponentSize()
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f))
            .testTag("share_lyrics_close_button")
        ) {
          Icon(
            imageVector = Icons.Rounded.Close,
            contentDescription = "Close",
            tint = MaterialTheme.colorScheme.onSurface
          )
        }
      }

      Spacer(modifier = Modifier.height(10.dp))

      // Scrollable middle content so both the card preview and optional lyric picker fit comfortably
      Column(
        modifier = Modifier
          .weight(1f)
          .fillMaxWidth()
          .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        // Card Preview (constrained width when lyric editor is open so both stay visible)
        val cardMaxWidthFraction = if (isEditingLyrics && hasLyrics && cardMode == ShareCardMode.LYRICS) 0.68f else 0.90f
        Box(
          modifier = Modifier.fillMaxWidth(cardMaxWidthFraction),
          contentAlignment = Alignment.Center
        ) {
          ShareLyricsCard(
            data = shareCardData,
            modifier = Modifier.testTag("share_lyrics_card_preview")
          )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 4 Gradient Color Dots
        Row(
          horizontalArrangement = Arrangement.spacedBy(14.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          gradientThemes.forEachIndexed { index, themeOption ->
            val isSelected = selectedThemeIndex == index
            Box(
              modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(
                  Brush.linearGradient(
                    colors = listOf(themeOption.startColor, themeOption.midColor, themeOption.endColor)
                  )
                )
                .border(
                  width = if (isSelected) 2.5.dp else 1.dp,
                  color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.28f),
                  shape = CircleShape
                )
                .clickable { selectedThemeIndex = index }
                .testTag("share_theme_dot_$index"),
              contentAlignment = Alignment.Center
            ) {
              if (isSelected) {
                Icon(
                  imageVector = Icons.Rounded.Check,
                  contentDescription = themeOption.name,
                  tint = Color.White,
                  modifier = Modifier.size(18.dp)
                )
              }
            }
          }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Mode Pill Toggle ("Song" | "Lyrics") + "Edit lyrics" Pill Button
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.Center,
          verticalAlignment = Alignment.CenterVertically
        ) {
          // Pill toggle: "Song" | "Lyrics"
          Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
          ) {
            Row(modifier = Modifier.padding(3.dp)) {
              // Song option
              val songSelected = cardMode == ShareCardMode.SONG || !hasLyrics
              Box(
                modifier = Modifier
                  .clip(CircleShape)
                  .background(
                    if (songSelected) MaterialTheme.colorScheme.primary else Color.Transparent
                  )
                  .clickable {
                    cardMode = ShareCardMode.SONG
                    isEditingLyrics = false
                  }
                  .testTag("share_mode_song_pill")
                  .padding(horizontal = 18.dp, vertical = 8.dp)
              ) {
                Text(
                  text = "Song",
                  style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                  color = if (songSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                )
              }

              // Lyrics option (selected by default when lyrics exist)
              val lyricsSelected = hasLyrics && cardMode == ShareCardMode.LYRICS
              Box(
                modifier = Modifier
                  .clip(CircleShape)
                  .background(
                    if (lyricsSelected) MaterialTheme.colorScheme.primary else Color.Transparent
                  )
                  .clickable {
                    if (hasLyrics) {
                      cardMode = ShareCardMode.LYRICS
                    } else {
                      Toast.makeText(
                        context,
                        "No lyrics available for this song",
                        Toast.LENGTH_SHORT
                      ).show()
                    }
                  }
                  .testTag("share_mode_lyrics_pill")
                  .padding(horizontal = 18.dp, vertical = 8.dp)
              ) {
                Text(
                  text = "Lyrics",
                  style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                  color = when {
                    lyricsSelected -> Color.White
                    hasLyrics -> MaterialTheme.colorScheme.onSurfaceVariant
                    else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.42f)
                  }
                )
              }
            }
          }

          // "Edit lyrics" pill button when Lyrics mode is active
          if (hasLyrics && cardMode == ShareCardMode.LYRICS) {
            Spacer(modifier = Modifier.width(10.dp))
            Surface(
              shape = CircleShape,
              color = if (isEditingLyrics) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
              } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f)
              },
              border = BorderStroke(
                1.dp,
                if (isEditingLyrics) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
              ),
              modifier = Modifier
                .clip(CircleShape)
                .clickable { isEditingLyrics = !isEditingLyrics }
                .testTag("share_edit_lyrics_button")
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
              ) {
                Icon(
                  imageVector = Icons.Rounded.EditNote,
                  contentDescription = null,
                  tint = if (isEditingLyrics) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                  modifier = Modifier.size(18.dp)
                )
                Text(
                  text = "Edit lyrics (${selectedIndices.size}/$MAX_SELECTED_LYRIC_LINES)",
                  style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                  color = if (isEditingLyrics) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
              }
            }
          }
        }

        // Hint when no lyrics are available
        if (!hasLyrics) {
          Spacer(modifier = Modifier.height(10.dp))
          Row(
            modifier = Modifier
              .clip(RoundedCornerShape(12.dp))
              .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
              .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Icon(
              imageVector = Icons.Rounded.Info,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.size(16.dp)
            )
            Text(
              text = "Lyrics unavailable for this song — showing Song card",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }

        // Expandable Scrollable Lyric Line Picker
        AnimatedVisibility(
          visible = isEditingLyrics && hasLyrics && cardMode == ShareCardMode.LYRICS,
          enter = fadeIn() + expandVertically(),
          exit = fadeOut() + shrinkVertically()
        ) {
          val listState = rememberLazyListState(
            initialFirstVisibleItemIndex = (selectedIndices.minOrNull() ?: 0).coerceAtLeast(0)
          )
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .padding(top = 12.dp)
              .clip(RoundedCornerShape(20.dp))
              .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.48f))
              .border(
                1.dp,
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f),
                RoundedCornerShape(20.dp)
              )
              .padding(vertical = 8.dp)
          ) {
            Text(
              text = "Tap up to 5 lines (shown in song order)",
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
            LazyColumn(
              state = listState,
              modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 160.dp, max = 240.dp)
                .testTag("share_lyrics_line_picker")
            ) {
              itemsIndexed(parsedLyrics, key = { idx, _ -> idx }) { index, line ->
                val isLineSelected = index in selectedIndices
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                      if (isLineSelected) {
                        if (selectedIndices.size <= 1) {
                          Toast.makeText(
                            context,
                            "Keep at least 1 line selected",
                            Toast.LENGTH_SHORT
                          ).show()
                        } else {
                          selectedIndices = selectedIndices - index
                        }
                      } else {
                        if (selectedIndices.size >= MAX_SELECTED_LYRIC_LINES) {
                          Toast.makeText(
                            context,
                            "You can select up to 5 lines",
                            Toast.LENGTH_SHORT
                          ).show()
                        } else {
                          selectedIndices = selectedIndices + index
                        }
                      }
                    }
                    .background(
                      if (isLineSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                      else Color.Transparent
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp)
                    .testTag("share_lyric_row_$index"),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                  Icon(
                    imageVector = if (isLineSelected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                    contentDescription = if (isLineSelected) "Selected" else "Not selected",
                    tint = if (isLineSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                    modifier = Modifier.size(22.dp)
                  )
                  Text(
                    text = line.text,
                    style = MaterialTheme.typography.bodyMedium.copy(
                      fontWeight = if (isLineSelected) FontWeight.Bold else FontWeight.Medium
                    ),
                    color = if (isLineSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                  )
                }
              }
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(12.dp))

      // Bottom Action Buttons: Full-width "Share" + "Save to gallery"
      Button(
        onClick = {
          if (isSharing || isSaving) return@Button
          isSharing = true
          coroutineScope.launch {
            val result = ShareCardExporter.shareCardImage(context, shareCardData)
            isSharing = false
            result.onFailure {
              Toast.makeText(context, "Could not share card", Toast.LENGTH_SHORT).show()
            }
          }
        },
        enabled = !isSharing && !isSaving,
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
          containerColor = MaterialTheme.colorScheme.primary,
          contentColor = Color.White
        ),
        modifier = Modifier
          .fillMaxWidth()
          .height(52.dp)
          .testTag("share_card_submit_button")
      ) {
        if (isSharing) {
          CircularProgressIndicator(
            color = Color.White,
            strokeWidth = 2.5.dp,
            modifier = Modifier.size(20.dp)
          )
          Spacer(modifier = Modifier.width(10.dp))
          Text("Rendering 1080p Card...", fontWeight = FontWeight.Bold)
        } else {
          Icon(
            imageVector = Icons.Rounded.Share,
            contentDescription = null,
            modifier = Modifier.size(20.dp)
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text("Share", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold))
        }
      }

      Spacer(modifier = Modifier.height(8.dp))

      OutlinedButton(
        onClick = {
          if (isSaving || isSharing) return@OutlinedButton
          isSaving = true
          coroutineScope.launch {
            val result = ShareCardExporter.saveCardToGallery(context, shareCardData)
            isSaving = false
            result.onSuccess {
              Toast.makeText(context, "Saved to Gallery", Toast.LENGTH_SHORT).show()
            }.onFailure {
              Toast.makeText(context, "Failed to save to Gallery", Toast.LENGTH_SHORT).show()
            }
          }
        },
        enabled = !isSaving && !isSharing,
        shape = CircleShape,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.22f)),
        colors = ButtonDefaults.outlinedButtonColors(
          contentColor = MaterialTheme.colorScheme.onSurface
        ),
        modifier = Modifier
          .fillMaxWidth()
          .height(48.dp)
          .testTag("save_card_gallery_button")
      ) {
        if (isSaving) {
          CircularProgressIndicator(
            color = MaterialTheme.colorScheme.primary,
            strokeWidth = 2.5.dp,
            modifier = Modifier.size(18.dp)
          )
          Spacer(modifier = Modifier.width(10.dp))
          Text("Saving...", fontWeight = FontWeight.SemiBold)
        } else {
          Icon(
            imageVector = Icons.Rounded.Download,
            contentDescription = null,
            modifier = Modifier.size(18.dp)
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text("Save to gallery", fontWeight = FontWeight.SemiBold)
        }
      }
    }
  }
}

private fun extractShareableLyricLines(lyricsResult: LyricsResult?): List<ShareableLyricLine> {
  if (lyricsResult == null) return emptyList()
  val raw = lyricsResult.rawLrc?.takeIf { it.isNotBlank() }
    ?: lyricsResult.plain?.takeIf {
      it.isNotBlank() && !it.startsWith("No lyrics found", ignoreCase = true)
    }

  if (!raw.isNullOrBlank()) {
    val parsed = LrcParser.parse(raw)
    if (parsed.lines.isNotEmpty()) {
      return parsed.lines.mapIndexedNotNull { index, line ->
        val cleaned = line.text.trim()
        if (cleaned.isEmpty()) null
        else ShareableLyricLine(
          index = index,
          timestampMs = if (parsed.isSynced) line.timestampMs else -1L,
          text = cleaned
        )
      }
    }
  }

  val synced = lyricsResult.synced.orEmpty()
  if (synced.isNotEmpty()) {
    return synced.mapIndexedNotNull { index, line ->
      val cleaned = line.text.trim()
      if (cleaned.isEmpty()) null
      else ShareableLyricLine(
        index = index,
        timestampMs = line.timestampMs,
        text = cleaned
      )
    }
  }
  return emptyList()
}

private fun computeInitialLineSelection(
  lines: List<ShareableLyricLine>,
  effectivePositionMs: Long
): Set<Int> {
  if (lines.isEmpty()) return emptySet()
  // Binary-search active line when timestamps exist
  val isSynced = lines.any { it.timestampMs >= 0L }
  val activeIdx = if (isSynced) {
    var low = 0
    var high = lines.lastIndex
    var found = -1
    while (low <= high) {
      val mid = (low + high) ushr 1
      if (lines[mid].timestampMs <= effectivePositionMs + 150L) {
        found = mid
        low = mid + 1
      } else {
        high = mid - 1
      }
    }
    found.coerceAtLeast(0)
  } else {
    0
  }

  val nextIdx = (activeIdx + 1).takeIf { it in lines.indices }
  return if (nextIdx != null) {
     linkedSetOf(activeIdx, nextIdx)
  } else {
    linkedSetOf(activeIdx)
  }
}
