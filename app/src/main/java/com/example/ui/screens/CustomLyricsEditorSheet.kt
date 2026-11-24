package com.example.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.R
import com.example.lyrics.EditableLyricRow
import com.example.lyrics.LrcCodec
import com.example.lyrics.LyricsAnimationStyle
import com.example.lyrics.LyricsEditorDraft
import com.example.lyrics.SyncedLyricsView
import com.example.model.LyricsResult
import com.example.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToLong

private val EditorBg = Color(0xFF080C14)
private val EditorCard = Color(0xFF151F30)
private val EditorCyan = Color(0xFF43C7F4)
private val EditorPurple = Color(0xFF7965FF)
private val EditorDanger = Color(0xFFEF4444)
private val EditorAccentBrush = Brush.horizontalGradient(listOf(EditorCyan, EditorPurple))

private data class SyncUndoSnapshot(
  val rows: List<EditableLyricRow>,
  val activeIndex: Int
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomLyricsEditorScreen(
  song: Song,
  currentLyrics: LyricsResult?,
  initialGlobalOffsetMs: Long,
  isPlaying: Boolean,
  playbackSpeed: Float,
  positionProvider: () -> Long,
  lyricsAnimationStyle: LyricsAnimationStyle,
  reducedMotion: Boolean,
  loadInitialCustomLrc: suspend () -> String?,
  loadDraft: suspend () -> Pair<LyricsEditorDraft, Long>?,
  onAutoSaveDraft: (LyricsEditorDraft) -> Unit,
  onClearDraft: () -> Unit,
  onPlayPause: () -> Unit,
  onSeekTo: (Long) -> Unit,
  onSetPlaybackSpeed: (Float) -> Unit,
  onSaveCustomLyrics: (lrcText: String, globalOffsetMs: Long) -> Unit,
  onDeleteCustomLyrics: () -> Unit,
  onClose: () -> Unit
) {
  val context = LocalContext.current
  val clipboardManager = LocalClipboardManager.current
  val haptic = LocalHapticFeedback.current
  val scope = rememberCoroutineScope()

  // Remember player speed prior to opening editor so we restore cleanly on close
  val originalSpeed = remember { playbackSpeed }
  DisposableEffect(Unit) {
    onDispose {
      if (originalSpeed > 0f && playbackSpeed != originalSpeed) {
        onSetPlaybackSpeed(originalSpeed)
      }
    }
  }

  var isLoaded by remember(song.id) { mutableStateOf(false) }
  var hasExistingCustom by remember(song.id) { mutableStateOf(currentLyrics?.isCustom == true) }
  var restoredDraftBanner by remember(song.id) { mutableStateOf(false) }
  var draftSavedNotice by remember(song.id) { mutableStateOf(false) }

  var currentStep by remember(song.id) { mutableIntStateOf(0) } // 0 = Write, 1 = Sync
  var isPreviewMode by remember(song.id) { mutableStateOf(false) }

  var rows by remember(song.id) { mutableStateOf<List<EditableLyricRow>>(emptyList()) }
  var writeText by remember(song.id) { mutableStateOf("") }
  var globalOffsetMs by remember(song.id) {
    mutableLongStateOf(
      initialGlobalOffsetMs.coerceIn(LrcCodec.MIN_GLOBAL_OFFSET_MS, LrcCodec.MAX_GLOBAL_OFFSET_MS)
    )
  }

  // Baseline signature used to determine unsaved changes accurately
  var savedBaselineSignature by remember(song.id) { mutableStateOf("") }

  var activeLineIndex by remember(song.id) { mutableIntStateOf(0) }
  var undoStack by remember(song.id) { mutableStateOf<List<SyncUndoSnapshot>>(emptyList()) }
  var fineAdjustRowIndex by remember(song.id) { mutableStateOf<Int?>(null) }

  var showOverflowMenu by remember { mutableStateOf(false) }
  var showUnsavedDialog by remember { mutableStateOf(false) }
  var showDeleteConfirmDialog by remember { mutableStateOf(false) }

  // Live player clock for Sync step (refreshes smoothly while Sync/Preview is visible)
  var livePositionMs by remember { mutableLongStateOf(positionProvider().coerceAtLeast(0L)) }
  LaunchedEffect(isPlaying, currentStep, isPreviewMode) {
    livePositionMs = positionProvider().coerceAtLeast(0L)
    while (isActive && (currentStep == 1 || isPreviewMode)) {
      livePositionMs = positionProvider().coerceAtLeast(0L)
      delay(if (isPlaying) 60L else 250L)
    }
  }

  // Load saved custom lyrics / LRCLIB baseline / auto-saved draft offline
  LaunchedEffect(song.id) {
    val savedCustomRaw = withContext(Dispatchers.IO) { loadInitialCustomLrc() }
    val draftPair = withContext(Dispatchers.IO) { loadDraft() }
    val clampedOffset = initialGlobalOffsetMs.coerceIn(
      LrcCodec.MIN_GLOBAL_OFFSET_MS,
      LrcCodec.MAX_GLOBAL_OFFSET_MS
    )

    hasExistingCustom = !savedCustomRaw.isNullOrBlank()
    val seedRaw = savedCustomRaw?.takeIf { it.isNotBlank() }
      ?: currentLyrics?.rawLrc?.takeIf { it.isNotBlank() }
      ?: currentLyrics?.plain?.takeIf {
        it.isNotBlank() && !it.startsWith("No lyrics found", ignoreCase = true)
      }

    val (seedRows, seedOffset) = LrcCodec.parseForEditor(seedRaw)
    val effectiveSeedOffset = if (clampedOffset != 0L) clampedOffset else seedOffset

    val baselineLrc = LrcCodec.writeLrc(
      rows = seedRows,
      title = song.title,
      artist = song.artist,
      globalOffsetMs = effectiveSeedOffset,
      includeOffsetTag = false
    )
    savedBaselineSignature = "$baselineLrc||$effectiveSeedOffset"

    if (draftPair != null && draftPair.first.rows.isNotEmpty()) {
      val draft = draftPair.first
      rows = draft.rows
      writeText = LrcCodec.rowsToPlainText(draft.rows)
      currentStep = draft.step.coerceIn(0, 1)
      globalOffsetMs = draft.globalOffsetMs.coerceIn(
        LrcCodec.MIN_GLOBAL_OFFSET_MS,
        LrcCodec.MAX_GLOBAL_OFFSET_MS
      )
      restoredDraftBanner = true
    } else {
      rows = seedRows
      writeText = LrcCodec.rowsToPlainText(seedRows)
      currentStep = 0
      globalOffsetMs = effectiveSeedOffset
      restoredDraftBanner = false
    }

    val firstUnsynced = rows.indexOfFirst { it.timestampMs == null }
    activeLineIndex = when {
      rows.isEmpty() -> 0
      firstUnsynced >= 0 -> firstUnsynced
      else -> 0
    }
    isLoaded = true
  }

  val currentSignature by remember(rows, globalOffsetMs, song.title, song.artist) {
    derivedStateOf {
      val lrc = LrcCodec.writeLrc(
        rows = rows,
        title = song.title,
        artist = song.artist,
        globalOffsetMs = globalOffsetMs,
        includeOffsetTag = false
      )
      "$lrc||$globalOffsetMs"
    }
  }
  val hasUnsavedChanges = isLoaded && (currentSignature != savedBaselineSignature || restoredDraftBanner)

  // Auto-save draft offline (debounced) whenever user edits lines, timestamps, step, or offset
  LaunchedEffect(isLoaded, rows, currentStep, globalOffsetMs) {
    if (!isLoaded) return@LaunchedEffect
    if (!hasUnsavedChanges) return@LaunchedEffect
    delay(550L)
    onAutoSaveDraft(
      LyricsEditorDraft(
        rows = rows,
        step = currentStep,
        globalOffsetMs = globalOffsetMs
      )
    )
    draftSavedNotice = true
  }

  // Zero-permission SAF Import (.lrc / .txt)
  val importLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenDocument()
  ) { uri ->
    if (uri == null) return@rememberLauncherForActivityResult
    scope.launch {
      val rawContent = withContext(Dispatchers.IO) {
        runCatching {
          context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
        }.getOrNull()
      }
      if (rawContent.isNullOrBlank()) {
        Toast.makeText(
          context,
          context.getString(R.string.lyrics_editor_import_empty_toast),
          Toast.LENGTH_SHORT
        ).show()
        return@launch
      }
      val (parsedRows, parsedOffset) = LrcCodec.parseForEditor(rawContent)
      if (parsedRows.isEmpty()) {
        Toast.makeText(
          context,
          context.getString(R.string.lyrics_editor_import_empty_toast),
          Toast.LENGTH_SHORT
        ).show()
        return@launch
      }
      rows = parsedRows
      writeText = LrcCodec.rowsToPlainText(parsedRows)
      if (parsedOffset != 0L) {
        globalOffsetMs = parsedOffset
      }
      activeLineIndex = parsedRows.indexOfFirst { it.timestampMs == null }.let { if (it >= 0) it else 0 }
      undoStack = emptyList()
      Toast.makeText(
        context,
        context.getString(R.string.lyrics_editor_imported_toast, parsedRows.size),
        Toast.LENGTH_SHORT
      ).show()
    }
  }

  // Zero-permission SAF Export (.lrc)
  val exportLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.CreateDocument("text/plain")
  ) { uri ->
    if (uri == null) return@rememberLauncherForActivityResult
    val content = LrcCodec.writeLrc(
      rows = rows,
      title = song.title,
      artist = song.artist,
      globalOffsetMs = globalOffsetMs,
      includeOffsetTag = true
    )
    if (content.isBlank()) return@rememberLauncherForActivityResult
    scope.launch {
      val ok = withContext(Dispatchers.IO) {
        runCatching {
          context.contentResolver.openOutputStream(uri)?.use { out ->
            out.write(content.toByteArray(Charsets.UTF_8))
          }
          true
        }.getOrDefault(false)
      }
      if (ok) {
        Toast.makeText(
          context,
          context.getString(R.string.lyrics_editor_exported_toast),
          Toast.LENGTH_SHORT
        ).show()
      }
    }
  }

  fun performSaveAndClose(closeAfter: Boolean = true) {
    val lrcText = LrcCodec.writeLrc(
      rows = rows,
      title = song.title,
      artist = song.artist,
      globalOffsetMs = globalOffsetMs,
      includeOffsetTag = false
    )
    if (lrcText.isBlank()) {
      Toast.makeText(
        context,
        context.getString(R.string.lyrics_editor_nothing_to_save_toast),
        Toast.LENGTH_SHORT
      ).show()
      return
    }
    onSaveCustomLyrics(lrcText, globalOffsetMs)
    hasExistingCustom = true
    restoredDraftBanner = false
    savedBaselineSignature = "$lrcText||$globalOffsetMs"
    if (closeAfter) {
      onClose()
    }
  }

  fun requestClose() {
    if (isPreviewMode) {
      isPreviewMode = false
      return
    }
    if (hasUnsavedChanges) {
      showUnsavedDialog = true
    } else {
      onClose()
    }
  }

  BackHandler {
    requestClose()
  }

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(EditorBg)
      .statusBarsPadding()
      .navigationBarsPadding()
      .testTag("custom_lyrics_editor_root")
  ) {
    Column(modifier = Modifier.fillMaxSize()) {
      // Top Header Bar
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        IconButton(
          onClick = { requestClose() },
          modifier = Modifier
            .minimumInteractiveComponentSize()
            .testTag("lyrics_editor_close_button")
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
            contentDescription = stringResource(id = R.string.lyrics_editor_close_cd),
            tint = Color.White
          )
        }

        Column(modifier = Modifier.weight(1f).padding(horizontal = 4.dp)) {
          Text(
            text = stringResource(id = R.string.lyrics_editor_title),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
          Text(
            text = "${song.title} · ${song.artist}",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.68f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }

        // Overflow menu (Export .lrc, Share .lrc, Delete custom lyrics)
        Box {
          IconButton(
            onClick = { showOverflowMenu = true },
            modifier = Modifier
              .minimumInteractiveComponentSize()
              .testTag("lyrics_editor_more_button")
          ) {
            Icon(
              imageVector = Icons.Rounded.MoreVert,
              contentDescription = stringResource(id = R.string.lyrics_editor_more_cd),
              tint = Color.White
            )
          }

          DropdownMenu(
            expanded = showOverflowMenu,
            onDismissRequest = { showOverflowMenu = false },
            containerColor = EditorCard
          ) {
            DropdownMenuItem(
              text = {
                Text(
                  text = stringResource(id = R.string.lyrics_editor_export_lrc),
                  color = Color.White
                )
              },
              leadingIcon = {
                Icon(Icons.Rounded.FileDownload, contentDescription = null, tint = EditorCyan)
              },
              enabled = rows.isNotEmpty(),
              onClick = {
                showOverflowMenu = false
                exportLauncher.launch(LrcCodec.suggestedLrcFileName(song.title, song.artist))
              }
            )
            DropdownMenuItem(
              text = {
                Text(
                  text = stringResource(id = R.string.lyrics_editor_share_lrc),
                  color = Color.White
                )
              },
              leadingIcon = {
                Icon(Icons.Rounded.Share, contentDescription = null, tint = EditorCyan)
              },
              enabled = rows.isNotEmpty(),
              onClick = {
                showOverflowMenu = false
                val lrc = LrcCodec.writeLrc(
                  rows = rows,
                  title = song.title,
                  artist = song.artist,
                  globalOffsetMs = globalOffsetMs,
                  includeOffsetTag = true
                )
                LrcCodec.createShareLrcIntent(context, song.title, song.artist, lrc)?.let { intent ->
                  runCatching {
                    context.startActivity(
                      Intent.createChooser(
                        intent,
                        context.getString(R.string.lyrics_editor_share_lrc)
                      )
                    )
                  }
                }
              }
            )
            if (hasExistingCustom) {
              HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
              DropdownMenuItem(
                text = {
                  Text(
                    text = stringResource(id = R.string.lyrics_editor_delete_custom),
                    color = EditorDanger
                  )
                },
                leadingIcon = {
                  Icon(Icons.Rounded.DeleteOutline, contentDescription = null, tint = EditorDanger)
                },
                onClick = {
                  showOverflowMenu = false
                  showDeleteConfirmDialog = true
                }
              )
            }
          }
        }

        // Save CTA Pill
        Surface(
          onClick = { performSaveAndClose(closeAfter = true) },
          shape = CircleShape,
          color = Color.Transparent,
          modifier = Modifier
            .padding(start = 4.dp)
            .clip(CircleShape)
            .background(if (rows.isNotEmpty()) EditorAccentBrush else Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.14f), Color.White.copy(alpha = 0.14f))))
            .minimumInteractiveComponentSize()
            .testTag("lyrics_editor_save_button")
        ) {
          Text(
            text = stringResource(id = R.string.lyrics_editor_save),
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            color = if (rows.isNotEmpty()) Color.White else Color.White.copy(alpha = 0.45f),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
          )
        }
      }

      // Mode Bar: 1. Write | 2. Sync | Preview
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Surface(
          shape = CircleShape,
          color = EditorCard,
          border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
          modifier = Modifier.weight(1f)
        ) {
          Row(modifier = Modifier.padding(3.dp)) {
            listOf(
              0 to stringResource(id = R.string.lyrics_editor_step_write),
              1 to stringResource(id = R.string.lyrics_editor_step_sync)
            ).forEach { (stepIdx, label) ->
              val selected = !isPreviewMode && currentStep == stepIdx
              Box(
                modifier = Modifier
                  .weight(1f)
                  .clip(CircleShape)
                  .background(
                    if (selected) EditorAccentBrush
                    else Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent))
                  )
                  .clickable {
                    if (stepIdx == 1 && currentStep == 0) {
                      rows = LrcCodec.mergeWriteTextWithExistingRows(writeText, rows)
                      if (activeLineIndex > rows.lastIndex) activeLineIndex = 0
                    } else if (stepIdx == 0) {
                      writeText = LrcCodec.rowsToPlainText(rows)
                    }
                    isPreviewMode = false
                    currentStep = stepIdx
                  }
                  .testTag(if (stepIdx == 0) "lyrics_editor_tab_write" else "lyrics_editor_tab_sync")
                  .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
              ) {
                Text(
                  text = label,
                  style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                  color = if (selected) Color.White else Color.White.copy(alpha = 0.68f),
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis
                )
              }
            }
          }
        }

        // Preview Mode Toggle Pill
        Surface(
          onClick = {
            if (!isPreviewMode && currentStep == 0) {
              rows = LrcCodec.mergeWriteTextWithExistingRows(writeText, rows)
            }
            isPreviewMode = !isPreviewMode
          },
          shape = CircleShape,
          color = if (isPreviewMode) EditorCyan.copy(alpha = 0.22f) else EditorCard,
          border = BorderStroke(
            1.dp,
            if (isPreviewMode) EditorCyan else Color.White.copy(alpha = 0.14f)
          ),
          modifier = Modifier
            .minimumInteractiveComponentSize()
            .testTag("lyrics_editor_preview_toggle")
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            Icon(
              imageVector = if (isPreviewMode) Icons.Rounded.Edit else Icons.Rounded.Visibility,
              contentDescription = null,
              tint = if (isPreviewMode) EditorCyan else Color.White,
              modifier = Modifier.size(16.dp)
            )
            Text(
              text = stringResource(
                id = if (isPreviewMode) R.string.lyrics_editor_exit_preview
                else R.string.lyrics_editor_preview
              ),
              style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
              color = if (isPreviewMode) EditorCyan else Color.White,
              maxLines = 1
            )
          }
        }
      }

      // Draft restored banner
      if (restoredDraftBanner) {
        Surface(
          shape = RoundedCornerShape(14.dp),
          color = EditorPurple.copy(alpha = 0.16f),
          border = BorderStroke(1.dp, EditorPurple.copy(alpha = 0.42f)),
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              modifier = Modifier.weight(1f)
            ) {
              Icon(
                imageVector = Icons.Rounded.History,
                contentDescription = null,
                tint = EditorCyan,
                modifier = Modifier.size(16.dp)
              )
              Text(
                text = stringResource(id = R.string.lyrics_editor_draft_restored),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White
              )
            }
            TextButton(
              onClick = {
                scope.launch {
                  onClearDraft()
                  val savedCustomRaw = withContext(Dispatchers.IO) { loadInitialCustomLrc() }
                  val seedRaw = savedCustomRaw?.takeIf { it.isNotBlank() }
                    ?: currentLyrics?.rawLrc?.takeIf { it.isNotBlank() }
                    ?: currentLyrics?.plain?.takeIf {
                      it.isNotBlank() && !it.startsWith("No lyrics found", ignoreCase = true)
                    }
                  val (freshRows, _) = LrcCodec.parseForEditor(seedRaw)
                  rows = freshRows
                  writeText = LrcCodec.rowsToPlainText(freshRows)
                  globalOffsetMs = initialGlobalOffsetMs.coerceIn(
                    LrcCodec.MIN_GLOBAL_OFFSET_MS,
                    LrcCodec.MAX_GLOBAL_OFFSET_MS
                  )
                  restoredDraftBanner = false
                  draftSavedNotice = false
                  activeLineIndex = 0
                  undoStack = emptyList()
                }
              }
            ) {
              Text(
                text = stringResource(id = R.string.lyrics_editor_discard_draft),
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = EditorCyan
              )
            }
          }
        }
      }

      // Main Content Area: Write | Sync | Preview
      AnimatedContent(
        targetState = if (isPreviewMode) 2 else currentStep,
        modifier = Modifier.weight(1f).fillMaxWidth(),
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "editor_mode_content"
      ) { mode ->
        when (mode) {
          0 -> WriteStepContent(
            writeText = writeText,
            lineCount = rows.size,
            draftSaved = draftSavedNotice,
            onTextChanged = { updated ->
              writeText = updated
              rows = LrcCodec.mergeWriteTextWithExistingRows(updated, rows)
            },
            onImportClick = {
              importLauncher.launch(arrayOf("text/*", "application/octet-stream", "*/*"))
            },
            onPasteClick = {
              val clip = clipboardManager.getText()?.text
              if (!clip.isNullOrBlank()) {
                val merged = LrcCodec.mergeWriteTextWithExistingRows(clip, rows)
                rows = merged
                writeText = LrcCodec.rowsToPlainText(merged)
              }
            },
            onClearClick = {
              writeText = ""
              rows = emptyList()
              activeLineIndex = 0
              undoStack = emptyList()
            },
            onNextSync = {
              rows = LrcCodec.mergeWriteTextWithExistingRows(writeText, rows)
              if (activeLineIndex > rows.lastIndex) activeLineIndex = 0
              currentStep = 1
            }
          )

          1 -> SyncStepContent(
            rows = rows,
            activeIndex = activeLineIndex,
            livePositionMs = livePositionMs,
            isPlaying = isPlaying,
            playbackSpeed = playbackSpeed,
            canUndo = undoStack.isNotEmpty(),
            globalOffsetMs = globalOffsetMs,
            onSelectRow = { idx -> activeLineIndex = idx.coerceIn(0, rows.lastIndex.coerceAtLeast(0)) },
            onTapTimestamp = { idx -> fineAdjustRowIndex = idx },
            onPlayPause = onPlayPause,
            onBack3s = {
              val target = (positionProvider() - 3_000L).coerceAtLeast(0L)
              onSeekTo(target)
              livePositionMs = target
            },
            onBackOneLine = {
              if (rows.isNotEmpty()) {
                val prevIdx = (activeLineIndex - 1).coerceAtLeast(0)
                activeLineIndex = prevIdx
                rows.getOrNull(prevIdx)?.timestampMs?.let { ts ->
                  val seekTarget = (ts - 800L).coerceAtLeast(0L)
                  onSeekTo(seekTarget)
                  livePositionMs = seekTarget
                }
              }
            },
            onUndo = {
              val last = undoStack.lastOrNull() ?: return@SyncStepContent
              undoStack = undoStack.dropLast(1)
              rows = last.rows
              activeLineIndex = last.activeIndex.coerceIn(0, rows.lastIndex.coerceAtLeast(0))
            },
            onToggleSpeed = {
              val next = if (playbackSpeed < 0.9f) 1.0f else 0.75f
              onSetPlaybackSpeed(next)
            },
            onGlobalOffsetChange = { newOffset ->
              globalOffsetMs = newOffset.coerceIn(
                LrcCodec.MIN_GLOBAL_OFFSET_MS,
                LrcCodec.MAX_GLOBAL_OFFSET_MS
              )
            },
            onTapStamp = {
              if (rows.isEmpty()) return@SyncStepContent
              haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
              val idx = activeLineIndex.coerceIn(0, rows.lastIndex)
              val stampedMs = positionProvider().coerceAtLeast(0L)
              undoStack = (undoStack + SyncUndoSnapshot(rows = rows, activeIndex = idx)).takeLast(40)
              rows = rows.mapIndexed { i, row ->
                if (i == idx) row.copy(timestampMs = stampedMs) else row
              }
              if (idx < rows.lastIndex) {
                activeLineIndex = idx + 1
              }
            },
            onGoToWrite = {
              writeText = LrcCodec.rowsToPlainText(rows)
              currentStep = 0
            }
          )

          else -> PreviewStepContent(
            song = song,
            rows = rows,
            globalOffsetMs = globalOffsetMs,
            isPlaying = isPlaying,
            positionProvider = positionProvider,
            lyricsAnimationStyle = lyricsAnimationStyle,
            reducedMotion = reducedMotion,
            onPlayPause = onPlayPause,
            onBack3s = {
              val target = (positionProvider() - 3_000L).coerceAtLeast(0L)
              onSeekTo(target)
              livePositionMs = target
            },
            onSeekTo = onSeekTo,
            onGlobalOffsetChange = { newOffset ->
              globalOffsetMs = newOffset.coerceIn(
                LrcCodec.MIN_GLOBAL_OFFSET_MS,
                LrcCodec.MAX_GLOBAL_OFFSET_MS
              )
            }
          )
        }
      }
    }

    // Fine Adjustment Dialog (-0.1 s / +0.1 s) when tapping a row's [mm:ss.xx] pill
    val adjustIdx = fineAdjustRowIndex
    if (adjustIdx != null && adjustIdx in rows.indices) {
      val row = rows[adjustIdx]
      val currentTs = row.timestampMs ?: positionProvider().coerceAtLeast(0L)
      AlertDialog(
        onDismissRequest = { fineAdjustRowIndex = null },
        containerColor = EditorCard,
        titleContentColor = Color.White,
        textContentColor = Color.White,
        modifier = Modifier.testTag("lyrics_editor_fine_adjust_dialog"),
        title = {
          Text(
            text = stringResource(id = R.string.lyrics_editor_fine_adjust_title),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
          )
        },
        text = {
          Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
          ) {
            Text(
              text = row.text,
              style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 24.sp),
              color = Color.White.copy(alpha = 0.86f),
              textAlign = TextAlign.Center,
              maxLines = 3,
              overflow = TextOverflow.Ellipsis
            )
            Text(
              text = LrcCodec.formatTimestamp(currentTs),
              style = MaterialTheme.typography.headlineSmall.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.ExtraBold
              ),
              color = EditorCyan,
              modifier = Modifier.testTag("lyrics_editor_fine_timestamp_value")
            )
            // Primary -0.1 s / +0.1 s buttons
            Row(
              horizontalArrangement = Arrangement.spacedBy(10.dp),
              modifier = Modifier.fillMaxWidth()
            ) {
              FilledTonalButton(
                onClick = {
                  val nextTs = (currentTs - LrcCodec.FINE_STEP_MS).coerceAtLeast(0L)
                  undoStack = (undoStack + SyncUndoSnapshot(rows, activeLineIndex)).takeLast(40)
                  rows = rows.mapIndexed { i, r -> if (i == adjustIdx) r.copy(timestampMs = nextTs) else r }
                },
                colors = ButtonDefaults.filledTonalButtonColors(
                  containerColor = EditorCyan.copy(alpha = 0.16f),
                  contentColor = EditorCyan
                ),
                modifier = Modifier
                  .weight(1f)
                  .minimumInteractiveComponentSize()
                  .testTag("lyrics_editor_fine_minus_01")
              ) {
                Text(
                  text = stringResource(id = R.string.lyrics_editor_fine_minus_01),
                  fontWeight = FontWeight.Bold
                )
              }
              FilledTonalButton(
                onClick = {
                  val nextTs = currentTs + LrcCodec.FINE_STEP_MS
                  undoStack = (undoStack + SyncUndoSnapshot(rows, activeLineIndex)).takeLast(40)
                  rows = rows.mapIndexed { i, r -> if (i == adjustIdx) r.copy(timestampMs = nextTs) else r }
                },
                colors = ButtonDefaults.filledTonalButtonColors(
                  containerColor = EditorCyan.copy(alpha = 0.16f),
                  contentColor = EditorCyan
                ),
                modifier = Modifier
                  .weight(1f)
                  .minimumInteractiveComponentSize()
                  .testTag("lyrics_editor_fine_plus_01")
              ) {
                Text(
                  text = stringResource(id = R.string.lyrics_editor_fine_plus_01),
                  fontWeight = FontWeight.Bold
                )
              }
            }
            // Secondary -0.5 s / +0.5 s quick nudges
            Row(
              horizontalArrangement = Arrangement.spacedBy(10.dp),
              modifier = Modifier.fillMaxWidth()
            ) {
              OutlinedButton(
                onClick = {
                  val nextTs = (currentTs - 500L).coerceAtLeast(0L)
                  undoStack = (undoStack + SyncUndoSnapshot(rows, activeLineIndex)).takeLast(40)
                  rows = rows.mapIndexed { i, r -> if (i == adjustIdx) r.copy(timestampMs = nextTs) else r }
                },
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                modifier = Modifier.weight(1f)
              ) {
                Text(stringResource(id = R.string.lyrics_editor_fine_minus_05))
              }
              OutlinedButton(
                onClick = {
                  val nextTs = currentTs + 500L
                  undoStack = (undoStack + SyncUndoSnapshot(rows, activeLineIndex)).takeLast(40)
                  rows = rows.mapIndexed { i, r -> if (i == adjustIdx) r.copy(timestampMs = nextTs) else r }
                },
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                modifier = Modifier.weight(1f)
              ) {
                Text(stringResource(id = R.string.lyrics_editor_fine_plus_05))
              }
            }
            if (row.timestampMs != null) {
              TextButton(
                onClick = {
                  undoStack = (undoStack + SyncUndoSnapshot(rows, activeLineIndex)).takeLast(40)
                  rows = rows.mapIndexed { i, r -> if (i == adjustIdx) r.copy(timestampMs = null) else r }
                  fineAdjustRowIndex = null
                }
              ) {
                Text(
                  text = stringResource(id = R.string.lyrics_editor_fine_clear_ts),
                  color = EditorDanger
                )
              }
            }
          }
        },
        confirmButton = {
          TextButton(onClick = { fineAdjustRowIndex = null }) {
            Text(
              text = stringResource(id = R.string.lyrics_editor_fine_done),
              color = EditorCyan,
              fontWeight = FontWeight.Bold
            )
          }
        }
      )
    }

    // Unsaved Changes Confirmation Dialog
    if (showUnsavedDialog) {
      AlertDialog(
        onDismissRequest = { showUnsavedDialog = false },
        containerColor = EditorCard,
        titleContentColor = Color.White,
        textContentColor = Color.White.copy(alpha = 0.85f),
        modifier = Modifier.testTag("lyrics_editor_unsaved_dialog"),
        title = {
          Text(
            text = stringResource(id = R.string.lyrics_editor_unsaved_title),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
          )
        },
        text = {
          Text(
            text = stringResource(id = R.string.lyrics_editor_unsaved_msg),
            style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp)
          )
        },
        confirmButton = {
          Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TextButton(
              onClick = {
                showUnsavedDialog = false
                onClose()
              },
              modifier = Modifier.testTag("lyrics_editor_unsaved_leave_button")
            ) {
              Text(
                text = stringResource(id = R.string.lyrics_editor_unsaved_leave),
                color = EditorDanger,
                fontWeight = FontWeight.Bold
              )
            }
            if (rows.isNotEmpty()) {
              Button(
                onClick = {
                  showUnsavedDialog = false
                  performSaveAndClose(closeAfter = true)
                },
                colors = ButtonDefaults.buttonColors(containerColor = EditorCyan, contentColor = EditorBg),
                shape = CircleShape
              ) {
                Text(
                  text = stringResource(id = R.string.lyrics_editor_unsaved_save_and_leave),
                  fontWeight = FontWeight.Bold
                )
              }
            }
          }
        },
        dismissButton = {
          TextButton(onClick = { showUnsavedDialog = false }) {
            Text(
              text = stringResource(id = R.string.lyrics_editor_unsaved_keep),
              color = Color.White
            )
          }
        }
      )
    }

    // Delete Custom Lyrics Confirmation Dialog
    if (showDeleteConfirmDialog) {
      AlertDialog(
        onDismissRequest = { showDeleteConfirmDialog = false },
        containerColor = EditorCard,
        titleContentColor = Color.White,
        textContentColor = Color.White.copy(alpha = 0.85f),
        modifier = Modifier.testTag("lyrics_editor_delete_dialog"),
        title = {
          Text(
            text = stringResource(id = R.string.lyrics_editor_delete_confirm_title),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
          )
        },
        text = {
          Text(
            text = stringResource(id = R.string.lyrics_editor_delete_confirm_msg),
            style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp)
          )
        },
        confirmButton = {
          Button(
            onClick = {
              showDeleteConfirmDialog = false
              onDeleteCustomLyrics()
              onClose()
            },
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(containerColor = EditorDanger, contentColor = Color.White),
            modifier = Modifier.testTag("lyrics_editor_delete_confirm_button")
          ) {
            Text(
              text = stringResource(id = R.string.lyrics_editor_delete_confirm_yes),
              fontWeight = FontWeight.Bold
            )
          }
        },
        dismissButton = {
          TextButton(onClick = { showDeleteConfirmDialog = false }) {
            Text(
              text = stringResource(id = R.string.settings_dynamic_bg_reset_confirm_no),
              color = Color.White
            )
          }
        }
      )
    }
  }
}

@Composable
private fun WriteStepContent(
  writeText: String,
  lineCount: Int,
  draftSaved: Boolean,
  onTextChanged: (String) -> Unit,
  onImportClick: () -> Unit,
  onPasteClick: () -> Unit,
  onClearClick: () -> Unit,
  onNextSync: () -> Unit
) {
  Column(
    modifier = Modifier
      .fillMaxSize()
      .padding(horizontal = 16.dp, vertical = 10.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp)
  ) {
    // Action Toolbar: Import .lrc/.txt | Paste | Clear
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Surface(
        onClick = onImportClick,
        shape = CircleShape,
        color = EditorCyan.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, EditorCyan.copy(alpha = 0.45f)),
        modifier = Modifier
          .minimumInteractiveComponentSize()
          .testTag("lyrics_editor_import_button")
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          Icon(
            imageVector = Icons.Rounded.UploadFile,
            contentDescription = null,
            tint = EditorCyan,
            modifier = Modifier.size(16.dp)
          )
          Text(
            text = stringResource(id = R.string.lyrics_editor_import),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = EditorCyan
          )
        }
      }

      Surface(
        onClick = onPasteClick,
        shape = CircleShape,
        color = EditorCard,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
        modifier = Modifier.minimumInteractiveComponentSize()
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
          Icon(
            imageVector = Icons.Rounded.ContentPaste,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(15.dp)
          )
          Text(
            text = stringResource(id = R.string.lyrics_editor_paste),
            style = MaterialTheme.typography.labelMedium,
            color = Color.White
          )
        }
      }

      Spacer(modifier = Modifier.weight(1f))

      if (writeText.isNotEmpty()) {
        IconButton(
          onClick = onClearClick,
          modifier = Modifier.minimumInteractiveComponentSize()
        ) {
          Icon(
            imageVector = Icons.Rounded.ClearAll,
            contentDescription = stringResource(id = R.string.lyrics_editor_clear),
            tint = Color.White.copy(alpha = 0.72f)
          )
        }
      }
    }

    // Status Row: Line count + Auto-save badge
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text(
        text = stringResource(id = R.string.lyrics_editor_lines_count, lineCount),
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
        color = Color.White.copy(alpha = 0.72f)
      )
      if (draftSaved) {
        Text(
          text = stringResource(id = R.string.lyrics_editor_draft_saved),
          style = MaterialTheme.typography.labelSmall,
          color = EditorCyan.copy(alpha = 0.85f)
        )
      }
    }

    // Multi-line Write TextField (one line per row)
    OutlinedTextField(
      value = writeText,
      onValueChange = onTextChanged,
      placeholder = {
        Text(
          text = stringResource(id = R.string.lyrics_editor_write_hint),
          color = Color.White.copy(alpha = 0.42f),
          style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 24.sp)
        )
      },
      textStyle = MaterialTheme.typography.bodyLarge.copy(
        color = Color.White,
        lineHeight = 28.sp
      ),
      colors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = EditorCard,
        unfocusedContainerColor = EditorCard,
        focusedBorderColor = EditorCyan,
        unfocusedBorderColor = Color.White.copy(alpha = 0.14f),
        cursorColor = EditorCyan
      ),
      shape = RoundedCornerShape(20.dp),
      modifier = Modifier
        .weight(1f)
        .fillMaxWidth()
        .testTag("lyrics_editor_write_input")
    )

    // Next: Sync Button
    Button(
      onClick = onNextSync,
      enabled = lineCount > 0,
      shape = RoundedCornerShape(18.dp),
      colors = ButtonDefaults.buttonColors(
        containerColor = Color.Transparent,
        disabledContainerColor = Color.White.copy(alpha = 0.08f)
      ),
      contentPadding = PaddingValues(0.dp),
      modifier = Modifier
        .fillMaxWidth()
        .height(54.dp)
        .testTag("lyrics_editor_next_sync_button")
    ) {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .background(
            if (lineCount > 0) EditorAccentBrush
            else Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.08f), Color.White.copy(alpha = 0.08f)))
          ),
        contentAlignment = Alignment.Center
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Text(
            text = stringResource(id = R.string.lyrics_editor_next_sync),
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = if (lineCount > 0) Color.White else Color.White.copy(alpha = 0.4f)
          )
          Icon(
            imageVector = Icons.Rounded.Timer,
            contentDescription = null,
            tint = if (lineCount > 0) Color.White else Color.White.copy(alpha = 0.4f),
            modifier = Modifier.size(18.dp)
          )
        }
      }
    }
  }
}

@Composable
private fun SyncStepContent(
  rows: List<EditableLyricRow>,
  activeIndex: Int,
  livePositionMs: Long,
  isPlaying: Boolean,
  playbackSpeed: Float,
  canUndo: Boolean,
  globalOffsetMs: Long,
  onSelectRow: (Int) -> Unit,
  onTapTimestamp: (Int) -> Unit,
  onPlayPause: () -> Unit,
  onBack3s: () -> Unit,
  onBackOneLine: () -> Unit,
  onUndo: () -> Unit,
  onToggleSpeed: () -> Unit,
  onGlobalOffsetChange: (Long) -> Unit,
  onTapStamp: () -> Unit,
  onGoToWrite: () -> Unit
) {
  if (rows.isEmpty()) {
    Box(
      modifier = Modifier.fillMaxSize().padding(24.dp),
      contentAlignment = Alignment.Center
    ) {
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        Icon(
          imageVector = Icons.Rounded.EditNote,
          contentDescription = null,
          tint = EditorCyan,
          modifier = Modifier.size(44.dp)
        )
        Text(
          text = stringResource(id = R.string.lyrics_editor_no_lines_yet),
          style = MaterialTheme.typography.bodyLarge,
          color = Color.White,
          textAlign = TextAlign.Center
        )
        Button(
          onClick = onGoToWrite,
          shape = CircleShape,
          colors = ButtonDefaults.buttonColors(containerColor = EditorCyan, contentColor = EditorBg)
        ) {
          Text(
            text = stringResource(id = R.string.lyrics_editor_step_write),
            fontWeight = FontWeight.Bold
          )
        }
      }
    }
    return
  }

  val safeActiveIdx = activeIndex.coerceIn(0, rows.lastIndex)
  val syncedCount = remember(rows) { rows.count { it.timestampMs != null } }
  val listState = rememberLazyListState()

  LaunchedEffect(safeActiveIdx) {
    val target = (safeActiveIdx - 1).coerceAtLeast(0)
    runCatching { listState.animateScrollToItem(target) }
  }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .padding(horizontal = 16.dp, vertical = 6.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp)
  ) {
    // Active Line Hero Card (Current line big + live player time)
    Surface(
      shape = RoundedCornerShape(22.dp),
      color = EditorCard,
      border = BorderStroke(1.dp, EditorCyan.copy(alpha = 0.35f)),
      modifier = Modifier.fillMaxWidth()
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = stringResource(id = R.string.lyrics_editor_line_progress, safeActiveIdx + 1, rows.size),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = EditorCyan
          )
          Text(
            text = LrcCodec.formatTimestamp(livePositionMs),
            style = MaterialTheme.typography.labelMedium.copy(
              fontFamily = FontFamily.Monospace,
              fontWeight = FontWeight.Bold
            ),
            color = Color.White
          )
        }

        val prevText = rows.getOrNull(safeActiveIdx - 1)?.text
        if (!prevText.isNullOrBlank()) {
          Text(
            text = prevText,
            style = MaterialTheme.typography.bodySmall.copy(lineHeight = 20.sp),
            color = Color.White.copy(alpha = 0.40f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }

        // Big current lyric line
        Text(
          text = rows[safeActiveIdx].text,
          style = MaterialTheme.typography.titleLarge.copy(
            fontWeight = FontWeight.ExtraBold,
            lineHeight = 32.sp
          ),
          color = Color.White,
          maxLines = 3,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.testTag("lyrics_editor_active_line_text")
        )

        val nextText = rows.getOrNull(safeActiveIdx + 1)?.text
        if (!nextText.isNullOrBlank()) {
          Text(
            text = nextText,
            style = MaterialTheme.typography.bodySmall.copy(lineHeight = 20.sp),
            color = Color.White.copy(alpha = 0.52f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        } else if (syncedCount == rows.size) {
          Text(
            text = stringResource(id = R.string.lyrics_editor_all_done),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = EditorCyan
          )
        }
      }
    }

    // Playback & Sync Controls Strip: Play/Pause, -3s, Prev line, Undo, Speed 0.75x/1x
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(6.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      SyncControlChip(
        icon = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
        label = null,
        highlighted = isPlaying,
        testTag = "lyrics_editor_play_pause",
        onClick = onPlayPause,
        modifier = Modifier.weight(1f)
      )
      SyncControlChip(
        icon = Icons.Rounded.Replay,
        label = stringResource(id = R.string.lyrics_editor_back_3s),
        highlighted = false,
        testTag = "lyrics_editor_back_3s",
        onClick = onBack3s,
        modifier = Modifier.weight(1f)
      )
      SyncControlChip(
        icon = Icons.Rounded.KeyboardArrowUp,
        label = stringResource(id = R.string.lyrics_editor_back_line),
        highlighted = false,
        testTag = "lyrics_editor_back_line",
        onClick = onBackOneLine,
        modifier = Modifier.weight(1.25f)
      )
      SyncControlChip(
        icon = Icons.AutoMirrored.Rounded.Undo,
        label = stringResource(id = R.string.lyrics_editor_undo),
        highlighted = false,
        enabled = canUndo,
        testTag = "lyrics_editor_undo",
        onClick = onUndo,
        modifier = Modifier.weight(1.05f)
      )
      val isSlow = playbackSpeed < 0.9f
      SyncControlChip(
        icon = Icons.Rounded.Speed,
        label = stringResource(
          id = if (isSlow) R.string.lyrics_editor_speed_075 else R.string.lyrics_editor_speed_100
        ),
        highlighted = isSlow,
        testTag = "lyrics_editor_speed_toggle",
        onClick = onToggleSpeed,
        modifier = Modifier.weight(1.1f)
      )
    }

    // Scrollable Rows with tappable [mm:ss.xx] pills
    LazyColumn(
      state = listState,
      modifier = Modifier
        .weight(1f)
        .fillMaxWidth()
        .clip(RoundedCornerShape(18.dp))
        .background(EditorCard.copy(alpha = 0.65f))
        .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(18.dp)),
      contentPadding = PaddingValues(vertical = 6.dp, horizontal = 8.dp),
      verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
      itemsIndexed(rows, key = { _, item -> item.id }) { idx, row ->
        val isActive = idx == safeActiveIdx
        val hasTs = row.timestampMs != null
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
              when {
                isActive -> EditorCyan.copy(alpha = 0.16f)
                else -> Color.Transparent
              }
            )
            .border(
              width = if (isActive) 1.dp else 0.dp,
              color = if (isActive) EditorCyan.copy(alpha = 0.55f) else Color.Transparent,
              shape = RoundedCornerShape(12.dp)
            )
            .clickable { onSelectRow(idx) }
            .padding(horizontal = 8.dp, vertical = 8.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          // Tappable [mm:ss.xx] badge opens fine adjustment (-0.1s / +0.1s)
          Surface(
            onClick = { onTapTimestamp(idx) },
            shape = RoundedCornerShape(8.dp),
            color = if (hasTs) EditorPurple.copy(alpha = 0.24f) else Color.White.copy(alpha = 0.08f),
            border = BorderStroke(
              1.dp,
              if (hasTs) EditorCyan.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.14f)
            ),
            modifier = Modifier.testTag("lyrics_editor_ts_pill_$idx")
          ) {
            Text(
              text = row.timestampMs?.let { LrcCodec.formatTimestamp(it) } ?: "[--:--.--]",
              style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
              ),
              color = if (hasTs) EditorCyan else Color.White.copy(alpha = 0.50f),
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
            )
          }

          Text(
            text = row.text,
            style = MaterialTheme.typography.bodyMedium.copy(
              fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
              lineHeight = 22.sp
            ),
            color = if (isActive) Color.White else Color.White.copy(alpha = 0.80f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
          )
        }
      }
    }

    // Global Time Offset Control (-5 s to +5 s)
    GlobalOffsetCard(
      globalOffsetMs = globalOffsetMs,
      onOffsetChange = onGlobalOffsetChange
    )

    // Large "TAP" Button to Stamp Current Time & Move to Next Line
    Surface(
      onClick = onTapStamp,
      shape = RoundedCornerShape(22.dp),
      color = Color.Transparent,
      shadowElevation = 8.dp,
      modifier = Modifier
        .fillMaxWidth()
        .height(66.dp)
        .clip(RoundedCornerShape(22.dp))
        .background(EditorAccentBrush)
        .testTag("lyrics_editor_tap_button")
    ) {
      Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
      ) {
        Text(
          text = stringResource(id = R.string.lyrics_editor_tap_button),
          style = MaterialTheme.typography.titleLarge.copy(
            fontWeight = FontWeight.Black,
            letterSpacing = 2.sp
          ),
          color = Color.White
        )
        Text(
          text = "${stringResource(id = R.string.lyrics_editor_tap_hint)} · ${stringResource(id = R.string.lyrics_editor_synced_count, syncedCount, rows.size)}",
          style = MaterialTheme.typography.labelSmall,
          color = Color.White.copy(alpha = 0.88f)
        )
      }
    }
  }
}

@Composable
private fun PreviewStepContent(
  song: Song,
  rows: List<EditableLyricRow>,
  globalOffsetMs: Long,
  isPlaying: Boolean,
  positionProvider: () -> Long,
  lyricsAnimationStyle: LyricsAnimationStyle,
  reducedMotion: Boolean,
  onPlayPause: () -> Unit,
  onBack3s: () -> Unit,
  onSeekTo: (Long) -> Unit,
  onGlobalOffsetChange: (Long) -> Unit
) {
  val previewLrc = remember(rows, song.title, song.artist) {
    LrcCodec.writeLrc(
      rows = rows,
      title = song.title,
      artist = song.artist,
      globalOffsetMs = 0L,
      includeOffsetTag = false
    )
  }
  val latestPositionProvider by rememberUpdatedState(positionProvider)
  val latestOnSeekTo by rememberUpdatedState(onSeekTo)

  val styleName = when (lyricsAnimationStyle) {
    LyricsAnimationStyle.SLIDE_UP -> stringResource(id = R.string.lyrics_anim_slide_up_name)
    LyricsAnimationStyle.BLUR_FADE -> stringResource(id = R.string.lyrics_anim_blur_fade_name)
    LyricsAnimationStyle.KARAOKE_FILL -> stringResource(id = R.string.lyrics_anim_karaoke_fill_name)
    LyricsAnimationStyle.TYPEWRITER -> stringResource(id = R.string.lyrics_anim_typewriter_name)
    LyricsAnimationStyle.POP_ZOOM -> stringResource(id = R.string.lyrics_anim_pop_zoom_name)
  }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .padding(horizontal = 16.dp, vertical = 8.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp)
  ) {
    Surface(
      shape = RoundedCornerShape(14.dp),
      color = EditorCyan.copy(alpha = 0.14f),
      border = BorderStroke(1.dp, EditorCyan.copy(alpha = 0.35f)),
      modifier = Modifier.fillMaxWidth()
    ) {
      Row(
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        Icon(
          imageVector = Icons.Rounded.AutoAwesome,
          contentDescription = null,
          tint = EditorCyan,
          modifier = Modifier.size(16.dp)
        )
        Text(
          text = stringResource(id = R.string.lyrics_editor_preview_banner, styleName),
          style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
          color = Color.White
        )
      }
    }

    // Real SyncedLyricsView driven by real player & real chosen animation style
    Box(
      modifier = Modifier
        .weight(1f)
        .fillMaxWidth()
        .clip(RoundedCornerShape(22.dp))
        .background(EditorCard)
        .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(22.dp))
    ) {
      if (previewLrc.isBlank()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          Text(
            text = stringResource(id = R.string.lyrics_editor_no_lines_yet),
            color = Color.White.copy(alpha = 0.65f),
            style = MaterialTheme.typography.bodyMedium
          )
        }
      } else {
        AndroidView(
          modifier = Modifier
            .fillMaxSize()
            .testTag("lyrics_editor_live_preview_view"),
          factory = { ctx ->
            val lyricsView = SyncedLyricsView(ctx)
            lyricsView.positionProvider = { latestPositionProvider() }
            lyricsView.onLineClick = { targetMs -> latestOnSeekTo(targetMs) }
            lyricsView
          },
          update = { view ->
            view.positionProvider = { latestPositionProvider() }
            view.offsetMs = globalOffsetMs
            view.setStyleColors(EditorCyan.toArgb(), android.graphics.Color.WHITE)
            view.setReducedMotion(reducedMotion)
            view.setAnimationStyle(lyricsAnimationStyle)
            view.setPlaybackAndForeground(playing = isPlaying, foreground = true)
            view.setLyrics(previewLrc)
            if (!isPlaying) {
              view.syncPositionOnce()
            }
          },
          onRelease = { view -> view.clear() }
        )
      }
    }

    GlobalOffsetCard(
      globalOffsetMs = globalOffsetMs,
      onOffsetChange = onGlobalOffsetChange
    )

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      FilledTonalButton(
        onClick = onBack3s,
        colors = ButtonDefaults.filledTonalButtonColors(
          containerColor = EditorCard,
          contentColor = Color.White
        ),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
          .weight(1f)
          .height(50.dp)
      ) {
        Icon(Icons.Rounded.Replay, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(stringResource(id = R.string.lyrics_editor_back_3s), fontWeight = FontWeight.Bold)
      }

      Button(
        onClick = onPlayPause,
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = EditorCyan, contentColor = EditorBg),
        modifier = Modifier
          .weight(1.4f)
          .height(50.dp)
      ) {
        Icon(
          imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
          contentDescription = null,
          modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
          text = if (isPlaying) "Pause" else "Play",
          fontWeight = FontWeight.Bold
        )
      }
    }
  }
}

@Composable
private fun SyncControlChip(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  label: String?,
  highlighted: Boolean,
  testTag: String,
  enabled: Boolean = true,
  modifier: Modifier = Modifier,
  onClick: () -> Unit
) {
  Surface(
    onClick = onClick,
    enabled = enabled,
    shape = RoundedCornerShape(14.dp),
    color = when {
      !enabled -> EditorCard.copy(alpha = 0.4f)
      highlighted -> EditorCyan.copy(alpha = 0.22f)
      else -> EditorCard
    },
    border = BorderStroke(
      1.dp,
      when {
        !enabled -> Color.White.copy(alpha = 0.06f)
        highlighted -> EditorCyan
        else -> Color.White.copy(alpha = 0.12f)
      }
    ),
    modifier = modifier
      .height(48.dp)
      .testTag(testTag)
  ) {
    Row(
      modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.Center
    ) {
      Icon(
        imageVector = icon,
        contentDescription = label,
        tint = when {
          !enabled -> Color.White.copy(alpha = 0.30f)
          highlighted -> EditorCyan
          else -> Color.White
        },
        modifier = Modifier.size(18.dp)
      )
      if (!label.isNullOrBlank()) {
        Spacer(modifier = Modifier.width(3.dp))
        Text(
          text = label,
          style = MaterialTheme.typography.labelSmall.copy(
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp
          ),
          color = when {
            !enabled -> Color.White.copy(alpha = 0.30f)
            highlighted -> EditorCyan
            else -> Color.White
          },
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
      }
    }
  }
}

@Composable
private fun GlobalOffsetCard(
  globalOffsetMs: Long,
  onOffsetChange: (Long) -> Unit
) {
  Surface(
    shape = RoundedCornerShape(16.dp),
    color = EditorCard,
    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
    modifier = Modifier.fillMaxWidth()
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 12.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        Text(
          text = stringResource(id = R.string.lyrics_editor_global_offset_title),
          style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
          color = Color.White.copy(alpha = 0.88f)
        )
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          Text(
            text = LrcCodec.formatOffsetSeconds(globalOffsetMs),
            style = MaterialTheme.typography.labelMedium.copy(
              fontFamily = FontFamily.Monospace,
              fontWeight = FontWeight.Bold
            ),
            color = if (globalOffsetMs == 0L) Color.White.copy(alpha = 0.7f) else EditorCyan,
            modifier = Modifier.testTag("lyrics_editor_global_offset_value")
          )
          if (globalOffsetMs != 0L) {
            Text(
              text = stringResource(id = R.string.lyrics_editor_reset_offset),
              style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
              color = EditorPurple,
              modifier = Modifier
                .clip(CircleShape)
                .clickable { onOffsetChange(0L) }
                .padding(horizontal = 6.dp, vertical = 2.dp)
            )
          }
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        Text(
          text = "-0.1s",
          style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
          color = EditorCyan,
          modifier = Modifier
            .clip(CircleShape)
            .clickable { onOffsetChange((globalOffsetMs - 100L).coerceIn(LrcCodec.MIN_GLOBAL_OFFSET_MS, LrcCodec.MAX_GLOBAL_OFFSET_MS)) }
            .padding(horizontal = 6.dp, vertical = 4.dp)
        )
        Slider(
          value = globalOffsetMs.toFloat(),
          onValueChange = { raw ->
            val snapped = (raw / 100f).roundToLong() * 100L
            onOffsetChange(snapped.coerceIn(LrcCodec.MIN_GLOBAL_OFFSET_MS, LrcCodec.MAX_GLOBAL_OFFSET_MS))
          },
          valueRange = LrcCodec.MIN_GLOBAL_OFFSET_MS.toFloat()..LrcCodec.MAX_GLOBAL_OFFSET_MS.toFloat(),
          colors = SliderDefaults.colors(
            thumbColor = EditorCyan,
            activeTrackColor = EditorCyan,
            inactiveTrackColor = Color.White.copy(alpha = 0.16f)
          ),
          modifier = Modifier
            .weight(1f)
            .testTag("lyrics_editor_global_offset_slider")
        )
        Text(
          text = "+0.1s",
          style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
          color = EditorCyan,
          modifier = Modifier
            .clip(CircleShape)
            .clickable { onOffsetChange((globalOffsetMs + 100L).coerceIn(LrcCodec.MIN_GLOBAL_OFFSET_MS, LrcCodec.MAX_GLOBAL_OFFSET_MS)) }
            .padding(horizontal = 6.dp, vertical = 4.dp)
        )
      }
    }
  }
}
