package com.example.ui.screens

import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
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
import com.example.share.ShareCardAspect
import com.example.share.ShareCardData
import com.example.share.ShareCardExporter
import com.example.share.ShareCardMode
import com.example.share.ShareCardRenderer
import com.example.share.ShareGradientTheme
import com.example.share.ShareLyricsCard
import com.example.share.SharePaletteExtractor
import com.example.share.SharePaletteSwatches
import com.example.share.ShareThemeStyle
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
  positionProvider: () -> Long = { currentPositionMs },
  isPlaying: Boolean = false,
  lyricsAnimationStyle: com.example.lyrics.LyricsAnimationStyle = com.example.lyrics.LyricsAnimationStyle.SLIDE_UP,
  lyricOffsetMs: Long = 0L,
  reducedMotion: Boolean = false,
  onDismiss: () -> Unit
) {
  val context = LocalContext.current
  val haptics = LocalHapticFeedback.current
  val coroutineScope = rememberCoroutineScope()
  val accentColor = MaterialTheme.colorScheme.primary
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

  // 250ms slide-up + fade entrance animation
  var appeared by remember { mutableStateOf(false) }
  LaunchedEffect(Unit) { appeared = true }
  val enterAlpha by animateFloatAsState(
    targetValue = if (appeared) 1f else 0f,
    animationSpec = if (reducedMotion) snap() else tween(250, easing = FastOutSlowInEasing),
    label = "sheet_enter_alpha"
  )
  val enterSlidePx by animateFloatAsState(
    targetValue = if (appeared) 0f else 48f,
    animationSpec = if (reducedMotion) snap() else tween(250, easing = FastOutSlowInEasing),
    label = "sheet_enter_slide"
  )

  val parsedLyrics = remember(lyricsResult) {
    extractShareableLyricLines(lyricsResult)
  }
  val hasLyrics = parsedLyrics.isNotEmpty()

  val initialSelection = remember(parsedLyrics) {
    computeInitialLineSelection(
      lines = parsedLyrics,
      effectivePositionMs = (currentPositionMs + lyricOffsetMs).coerceAtLeast(0L)
    )
  }

  var cardMode by remember(hasLyrics) {
    mutableStateOf(if (hasLyrics) ShareCardMode.LYRICS else ShareCardMode.SONG)
  }
  var cardAspect by remember { mutableStateOf(ShareCardAspect.FEED_4_5) }
  var selectedIndices by remember(initialSelection) {
    mutableStateOf(initialSelection)
  }
  var isEditingLyrics by remember { mutableStateOf(false) }

  // Load album art + blurred 1.3x saturated art + Palette swatches/themes off main thread
  var artworkBitmap by remember(song.id, song.artworkUrl) { mutableStateOf<Bitmap?>(null) }
  var blurredArtworkBitmap by remember(song.id, song.artworkUrl) { mutableStateOf<Bitmap?>(null) }
  val (fallbackC1, fallbackC2) = remember(song.title) { artworkHueColors(song.title) }

  var swatches by remember(song.id, accentColor) {
    mutableStateOf(
      SharePaletteSwatches(
        vibrant = accentColor,
        muted = fallbackC1,
        darkVibrant = fallbackC2,
        dominant = accentColor
      )
    )
  }
  var gradientThemes by remember(song.id, accentColor) {
    mutableStateOf(
      listOf(
        ShareGradientTheme(
          id = "art",
          style = ShareThemeStyle.ART,
          name = "Art",
          swatchPrimary = accentColor,
          swatchSecondary = fallbackC1,
          swatchTertiary = fallbackC2,
          showBlurredArtwork = true
        ),
        ShareGradientTheme(
          id = "liquid_glass",
          style = ShareThemeStyle.LIQUID_GLASS,
          name = "Glass",
          swatchPrimary = Color(0xFF43C7F4),
          swatchSecondary = Color(0xFF7965FF),
          swatchTertiary = Color(0xFF080C14),
          showBlurredArtwork = false
        ),
        ShareGradientTheme(
          id = "vinyl",
          style = ShareThemeStyle.VINYL,
          name = "Vinyl",
          swatchPrimary = Color(0xFFE7D6B8),
          swatchSecondary = Color(0xFF3E352B),
          swatchTertiary = Color(0xFF14110E),
          showBlurredArtwork = false
        ),
        ShareGradientTheme(
          id = "vibrant",
          style = ShareThemeStyle.VIBRANT,
          name = "Vibrant",
          swatchPrimary = accentColor,
          swatchSecondary = fallbackC1,
          swatchTertiary = fallbackC2,
          showBlurredArtwork = false
        ),
        ShareGradientTheme(
          id = "dark",
          style = ShareThemeStyle.DARK,
          name = "Dark",
          swatchPrimary = fallbackC2,
          swatchSecondary = Color(0xFF0B0E17),
          swatchTertiary = Color(0xFF05070C),
          showBlurredArtwork = false
        ),
        ShareGradientTheme(
          id = "mono",
          style = ShareThemeStyle.MONO,
          name = "Mono",
          swatchPrimary = Color(0xFF2B2E36),
          swatchSecondary = Color(0xFF14161B),
          swatchTertiary = Color(0xFF060709),
          showBlurredArtwork = false
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
          val request = com.example.ui.image.AurixImagePipeline.buildCrispArtworkRequest(
            context = context,
            model = song.artworkUrl,
            allowHardware = false
          )
          val result = context.imageLoader.execute(request)
          (result as? SuccessResult)?.drawable?.toBitmap()?.let {
            com.example.ui.image.AurixImagePipeline.applyHighDensityMetadata(it, context)
          }
        } catch (_: Exception) {
          null
        }
      }
    }
    artworkBitmap = loadedBitmap
    blurredArtworkBitmap = SharePaletteExtractor.createBlurredSaturatedArtwork(loadedBitmap)
    val (extractedSwatches, extractedThemes) = SharePaletteExtractor.extractSwatchesAndThemes(
      bitmap = loadedBitmap,
      fallbackTitle = song.title,
      accentColor = accentColor
    )
    swatches = extractedSwatches
    gradientThemes = extractedThemes
  }

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
    blurredArtworkBitmap,
    orderedSelectedLines,
    cardMode,
    cardAspect,
    activeTheme,
    swatches
  ) {
    ShareCardData(
      title = song.title,
      artist = song.artist,
      artworkBitmap = artworkBitmap,
      blurredArtworkBitmap = blurredArtworkBitmap,
      selectedLines = orderedSelectedLines,
      mode = if (hasLyrics) cardMode else ShareCardMode.SONG,
      theme = activeTheme,
      swatches = swatches,
      brandName = "AURIX MUSIC",
      aspect = cardAspect
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
    BoxWithConstraints(
      modifier = Modifier
        .fillMaxSize()
        .windowInsetsPadding(WindowInsets.safeDrawing)
        .graphicsLayer {
          alpha = enterAlpha
          translationY = enterSlidePx
        }
    ) {
      val isCompactHeight = maxHeight < 640.dp || isEditingLyrics
      Column(
        modifier = Modifier
          .fillMaxSize()
          .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        // Header: "Share lyrics" + Close (X) button
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
              .size(38.dp)
              .clip(CircleShape)
              .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f))
              .testTag("share_lyrics_close_button")
          ) {
            Icon(
              imageVector = Icons.Rounded.Close,
              contentDescription = "Close",
              tint = MaterialTheme.colorScheme.onSurface,
              modifier = Modifier.size(20.dp)
            )
          }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Main flexible area (scrollable on small screens or when lyric picker is expanded)
        Column(
          modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .then(
              if (isCompactHeight) Modifier.verticalScroll(rememberScrollState())
              else Modifier
            ),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.Center
        ) {
          // 1. LARGE CARD PREVIEW (weight 1 when not editing lyrics, aspect ratio 4:5 at most)
          Box(
            modifier = Modifier
              .then(
                if (!isCompactHeight) Modifier.weight(1f, fill = true)
                else Modifier.heightIn(min = 240.dp, max = 380.dp)
              )
              .fillMaxWidth(),
            contentAlignment = Alignment.Center
          ) {
            val selectedLineTimestamps = remember(parsedLyrics, selectedIndices) {
              selectedIndices.sorted().mapNotNull { idx -> parsedLyrics.getOrNull(idx)?.timestampMs }
            }
            ShareLyricsCard(
              data = shareCardData,
              reducedMotion = reducedMotion,
              lyricsAnimationStyle = lyricsAnimationStyle,
              isPlaying = isPlaying,
              positionProvider = positionProvider,
              lyricOffsetMs = lyricOffsetMs,
              selectedLineTimestampsMs = selectedLineTimestamps,
              modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(
                  ratio = cardAspect.ratio,
                  matchHeightConstraintsFirst = !isCompactHeight
                )
                .testTag("share_lyrics_card_preview")
            )
          }

          Spacer(modifier = Modifier.height(14.dp))

          // 2. ONE TIDY ROW UNDER THE CARD:
          // Left: Song | Lyrics sliding segmented control (150ms pill slide)
          // Right: "Edit lyrics (2/5)" text button
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            SlidingModeSegmentedControl(
              cardMode = if (hasLyrics) cardMode else ShareCardMode.SONG,
              hasLyrics = hasLyrics,
              reducedMotion = reducedMotion,
              onSelectMode = { targetMode ->
                if (targetMode == ShareCardMode.LYRICS && !hasLyrics) {
                  Toast.makeText(
                    context,
                    "No lyrics available for this song",
                    Toast.LENGTH_SHORT
                  ).show()
                } else {
                  haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                  cardMode = targetMode
                  if (targetMode == ShareCardMode.SONG) {
                    isEditingLyrics = false
                  }
                }
              }
            )

            if (hasLyrics && cardMode == ShareCardMode.LYRICS) {
              TextButton(
                onClick = {
                  haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                  isEditingLyrics = !isEditingLyrics
                },
                shape = CircleShape,
                colors = ButtonDefaults.textButtonColors(
                  contentColor = if (isEditingLyrics) {
                    MaterialTheme.colorScheme.primary
                  } else {
                    MaterialTheme.colorScheme.onSurface
                  }
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier.testTag("share_edit_lyrics_button")
              ) {
                Icon(
                  imageVector = Icons.Rounded.EditNote,
                  contentDescription = null,
                  modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                  text = "Edit lyrics (${selectedIndices.size}/$MAX_SELECTED_LYRIC_LINES)",
                  style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold)
                )
              }
            }
            // 9:16 Story vs 4:5 Feed toggle pill
            Surface(
              onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                cardAspect = if (cardAspect == ShareCardAspect.FEED_4_5) {
                  ShareCardAspect.STORY_9_16
                } else {
                  ShareCardAspect.FEED_4_5
                }
              },
              shape = CircleShape,
              color = if (cardAspect == ShareCardAspect.STORY_9_16) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
              } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)
              },
              border = BorderStroke(
                1.dp,
                if (cardAspect == ShareCardAspect.STORY_9_16) {
                  MaterialTheme.colorScheme.primary.copy(alpha = 0.60f)
                } else {
                  MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                }
              ),
              modifier = Modifier
                .defaultMinSize(minHeight = 36.dp)
                .testTag("share_aspect_toggle_pill")
            ) {
              Text(
                text = if (cardAspect == ShareCardAspect.STORY_9_16) "Story 9:16" else "Feed 4:5",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = if (cardAspect == ShareCardAspect.STORY_9_16) {
                  MaterialTheme.colorScheme.primary
                } else {
                  MaterialTheme.colorScheme.onSurface
                },
                modifier = Modifier.padding(horizontal = 11.dp, vertical = 8.dp)
              )
            }
          }

          Spacer(modifier = Modifier.height(10.dp))

          // 3. COLOR SWATCHES (Art, Glass, Vinyl, Vibrant, Dark, Mono)
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Row(
              horizontalArrangement = Arrangement.spacedBy(14.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              gradientThemes.forEachIndexed { index, themeOption ->
                val isSelected = selectedThemeIndex == index
                val swatchInteraction = remember { MutableInteractionSource() }
                Column(
                  horizontalAlignment = Alignment.CenterHorizontally,
                  modifier = Modifier
                    .clickable(
                      interactionSource = swatchInteraction,
                      indication = null
                    ) {
                      if (selectedThemeIndex != index) {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        selectedThemeIndex = index
                      }
                    }
                    .testTag("share_theme_dot_$index")
                ) {
                  // Outer check ring + 28dp swatch
                  Box(
                    modifier = Modifier
                      .size(34.dp)
                      .then(
                        if (isSelected) {
                          Modifier.border(
                            width = 1.75.dp,
                            color = MaterialTheme.colorScheme.primary,
                            shape = CircleShape
                          )
                        } else {
                          Modifier
                        }
                      ),
                    contentAlignment = Alignment.Center
                  ) {
                    Box(
                      modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(
                          Brush.linearGradient(
                            colors = listOf(
                              themeOption.swatchPrimary,
                              themeOption.swatchSecondary,
                              themeOption.swatchTertiary
                            )
                          )
                        )
                        .border(
                          width = 1.dp,
                          color = Color.White.copy(alpha = if (isSelected) 0.55f else 0.18f),
                          shape = CircleShape
                        ),
                      contentAlignment = Alignment.Center
                    ) {
                      if (isSelected) {
                        Icon(
                          imageVector = Icons.Rounded.Check,
                          contentDescription = themeOption.name,
                          tint = Color.White,
                          modifier = Modifier.size(14.dp)
                        )
                      }
                    }
                  }
                  Spacer(modifier = Modifier.height(3.dp))
                  Text(
                    text = themeOption.name,
                    style = MaterialTheme.typography.labelSmall.copy(
                      fontSize = 10.sp,
                      fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    ),
                    color = if (isSelected) {
                      MaterialTheme.colorScheme.onSurface
                    } else {
                      MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
                    }
                  )
                }
              }
            }
          }

          // Hint when no lyrics are available
          if (!hasLyrics) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
              modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                .padding(horizontal = 12.dp, vertical = 6.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
              Icon(
                imageVector = Icons.Rounded.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(15.dp)
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
            enter = fadeIn(tween(180)) + expandVertically(tween(200)),
            exit = fadeOut(tween(150)) + shrinkVertically(tween(180))
          ) {
            val listState = rememberLazyListState(
              initialFirstVisibleItemIndex = (selectedIndices.minOrNull() ?: 0).coerceAtLeast(0)
            )
            Column(
              modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.48f))
                .border(
                  1.dp,
                  MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f),
                  RoundedCornerShape(18.dp)
                )
                .padding(vertical = 6.dp)
            ) {
              Text(
                text = "Tap up to 5 lines (shown in song order)",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
              )
              LazyColumn(
                state = listState,
                modifier = Modifier
                  .fillMaxWidth()
                  .heightIn(min = 140.dp, max = 210.dp)
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
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
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
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            selectedIndices = selectedIndices + index
                          }
                        }
                      }
                      .background(
                        if (isLineSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                        else Color.Transparent
                      )
                      .padding(horizontal = 14.dp, vertical = 9.dp)
                      .testTag("share_lyric_row_$index"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                  ) {
                    Icon(
                      imageVector = if (isLineSelected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                      contentDescription = if (isLineSelected) "Selected" else "Not selected",
                      tint = if (isLineSelected) {
                        MaterialTheme.colorScheme.primary
                      } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.60f)
                      },
                      modifier = Modifier.size(21.dp)
                    )
                    Text(
                      text = line.text,
                      style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = if (isLineSelected) FontWeight.Bold else FontWeight.Medium
                      ),
                      color = if (isLineSelected) {
                        MaterialTheme.colorScheme.onSurface
                      } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                      },
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

        // 4. PINNED BOTTOM ACTIONS:
        // Large "Share" button (accent filled) + square 56dp icon-only "Save to gallery" button
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(12.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
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
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(
              containerColor = MaterialTheme.colorScheme.primary,
              contentColor = Color.White
            ),
            modifier = Modifier
              .weight(1f)
              .height(56.dp)
              .testTag("share_card_submit_button")
          ) {
            if (isSharing) {
              CircularProgressIndicator(
                color = Color.White,
                strokeWidth = 2.5.dp,
                modifier = Modifier.size(20.dp)
              )
              Spacer(modifier = Modifier.width(10.dp))
              Text("Rendering...", fontWeight = FontWeight.Bold)
            } else {
              Icon(
                imageVector = Icons.Rounded.Share,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = "Share",
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
              )
            }
          }

          // Square 56dp icon-only "Save to gallery" button
          Surface(
            onClick = {
              if (isSaving || isSharing) return@Surface
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
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f)),
            modifier = Modifier
              .size(56.dp)
              .testTag("save_card_gallery_button")
          ) {
            Box(contentAlignment = Alignment.Center) {
              if (isSaving) {
                CircularProgressIndicator(
                  color = MaterialTheme.colorScheme.primary,
                  strokeWidth = 2.5.dp,
                  modifier = Modifier.size(20.dp)
                )
              } else {
                Icon(
                  imageVector = Icons.Rounded.Download,
                  contentDescription = "Save to gallery",
                  tint = MaterialTheme.colorScheme.onSurface,
                  modifier = Modifier.size(22.dp)
                )
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun SlidingModeSegmentedControl(
  cardMode: ShareCardMode,
  hasLyrics: Boolean,
  reducedMotion: Boolean,
  onSelectMode: (ShareCardMode) -> Unit
) {
  val tabWidth = 76.dp
  val tabHeight = 34.dp
  val isLyricsSelected = hasLyrics && cardMode == ShareCardMode.LYRICS
  val pillOffsetX by animateDpAsState(
    targetValue = if (isLyricsSelected) tabWidth else 0.dp,
    animationSpec = if (reducedMotion) snap() else tween(durationMillis = 150, easing = FastOutSlowInEasing),
    label = "mode_pill_slide"
  )

  Surface(
    shape = CircleShape,
    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.78f),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f))
  ) {
    Box(
      modifier = Modifier
        .padding(3.dp)
        .width(tabWidth * 2)
        .height(tabHeight)
    ) {
      // Sliding active highlight pill (150ms)
      Box(
        modifier = Modifier
          .offset(x = pillOffsetX)
          .width(tabWidth)
          .fillMaxHeight()
          .clip(CircleShape)
          .background(MaterialTheme.colorScheme.primary)
      )

      Row(modifier = Modifier.fillMaxSize()) {
        Box(
          modifier = Modifier
            .width(tabWidth)
            .fillMaxHeight()
            .clip(CircleShape)
            .clickable { onSelectMode(ShareCardMode.SONG) }
            .testTag("share_mode_song_pill"),
          contentAlignment = Alignment.Center
        ) {
          Text(
            text = "Song",
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
            color = if (!isLyricsSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
          )
        }

        Box(
          modifier = Modifier
            .width(tabWidth)
            .fillMaxHeight()
            .clip(CircleShape)
            .clickable { onSelectMode(ShareCardMode.LYRICS) }
            .testTag("share_mode_lyrics_pill"),
          contentAlignment = Alignment.Center
        ) {
          Text(
            text = "Lyrics",
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
            color = when {
              isLyricsSelected -> Color.White
              hasLyrics -> MaterialTheme.colorScheme.onSurfaceVariant
              else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
            }
          )
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
