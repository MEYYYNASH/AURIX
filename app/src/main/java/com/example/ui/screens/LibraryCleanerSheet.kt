package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.LibraryCleanerEngine
import com.example.data.LibraryScanReport
import com.example.model.Song

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryCleanerSheet(
  localSongs: List<Song>,
  autoCleanEnabled: Boolean,
  accentColor: Color,
  onToggleAutoClean: (Boolean) -> Unit,
  onRunCleanNow: (LibraryScanReport) -> Unit,
  onDismiss: () -> Unit
) {
  val context = LocalContext.current
  val surfaceColor = MaterialTheme.colorScheme.surface
  val isDark = surfaceColor.luminance() < 0.5f || isSystemInDarkTheme()
  val sheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

  val report = remember(localSongs) {
    LibraryCleanerEngine.scanLibrary(localSongs)
  }

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    shape = sheetShape,
    containerColor = Color.Transparent,
    contentColor = Color.White,
    scrimColor = Color.Black.copy(alpha = 0.62f),
    tonalElevation = 0.dp,
    dragHandle = null
  ) {
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .clip(sheetShape)
        .background(if (isDark) Color(0xFF0B101B) else Color(0xFF101726))
        .background(
          Brush.radialGradient(
            colors = listOf(accentColor.copy(alpha = 0.22f), Color.Transparent),
            radius = 750f
          )
        )
        .border(
          1.dp,
          Brush.verticalGradient(
            listOf(Color.White.copy(alpha = 0.25f), Color.White.copy(alpha = 0.05f))
          ),
          sheetShape
        )
        .testTag("library_cleaner_sheet")
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .navigationBarsPadding()
          .padding(horizontal = 20.dp)
          .padding(top = 12.dp, bottom = 22.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
      ) {
        // Drag Handle
        Box(
          modifier = Modifier
            .align(Alignment.CenterHorizontally)
            .width(38.dp)
            .height(4.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.32f))
        )

        // Header
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = stringResource(id = R.string.library_cleaner_title),
              style = MaterialTheme.typography.titleMedium.copy(
                fontSize = 19.sp,
                fontWeight = FontWeight.ExtraBold
              ),
              color = Color.White
            )
            Text(
              text = stringResource(id = R.string.library_cleaner_subtitle),
              style = MaterialTheme.typography.bodySmall,
              color = Color.White.copy(alpha = 0.72f)
            )
          }
          IconButton(
            onClick = onDismiss,
            modifier = Modifier
              .minimumInteractiveComponentSize()
              .testTag("library_cleaner_close_button")
          ) {
            Icon(
              imageVector = Icons.Rounded.Close,
              contentDescription = stringResource(id = R.string.cd_close_sheet),
              tint = Color.White.copy(alpha = 0.82f)
            )
          }
        }

        // 3 Metric Stat Cards
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          CleanerStatTile(
            icon = Icons.Rounded.ContentCopy,
            value = report.duplicateTracks.size.toString(),
            label = stringResource(id = R.string.library_cleaner_duplicates_label),
            accent = if (report.duplicateTracks.isNotEmpty()) Color(0xFFF59E0B) else accentColor,
            modifier = Modifier.weight(1f)
          )
          CleanerStatTile(
            icon = Icons.Rounded.BrokenImage,
            value = report.missingFileTracks.size.toString(),
            label = stringResource(id = R.string.library_cleaner_missing_label),
            accent = if (report.missingFileTracks.isNotEmpty()) Color(0xFFEF4444) else accentColor,
            modifier = Modifier.weight(1f)
          )
          CleanerStatTile(
            icon = Icons.Rounded.Storage,
            value = report.reclaimableMbFormatted,
            label = stringResource(id = R.string.library_cleaner_reclaim_label),
            accent = Color(0xFF22C55E),
            modifier = Modifier.weight(1f)
          )
        }

        // Auto-Clean on Startup Switch Card
        Surface(
          shape = RoundedCornerShape(18.dp),
          color = Color.White.copy(alpha = 0.07f),
          border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
          modifier = Modifier.fillMaxWidth()
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = stringResource(id = R.string.library_cleaner_auto_title),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = Color.White
              )
              Text(
                text = stringResource(id = R.string.library_cleaner_auto_sub),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.68f)
              )
            }
            Switch(
              checked = autoCleanEnabled,
              onCheckedChange = onToggleAutoClean,
              modifier = Modifier.testTag("library_cleaner_auto_switch")
            )
          }
        }

        // Preview of Detected Issues or Spotless Banner
        if (!report.hasIssues) {
          Surface(
            shape = RoundedCornerShape(18.dp),
            color = Color(0xFF22C55E).copy(alpha = 0.14f),
            border = BorderStroke(1.dp, Color(0xFF22C55E).copy(alpha = 0.48f)),
            modifier = Modifier.fillMaxWidth()
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
              Icon(
                imageVector = Icons.Rounded.Verified,
                contentDescription = null,
                tint = Color(0xFF4ADE80),
                modifier = Modifier.size(24.dp)
              )
              Text(
                text = stringResource(id = R.string.library_cleaner_all_clean),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = Color.White
              )
            }
          }
        } else {
          LazyColumn(
            modifier = Modifier
              .fillMaxWidth()
              .heightIn(max = 200.dp)
              .clip(RoundedCornerShape(16.dp))
              .background(Color.White.copy(alpha = 0.05f))
              .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(16.dp)),
            contentPadding = PaddingValues(vertical = 6.dp)
          ) {
            items(report.missingFileTracks, key = { "missing_${it.id}" }) { song ->
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(horizontal = 14.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
              ) {
                Icon(
                  imageVector = Icons.Rounded.ErrorOutline,
                  contentDescription = null,
                  tint = Color(0xFFF87171),
                  modifier = Modifier.size(18.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = song.title,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                  )
                  Text(
                    text = "${song.artist} · ${stringResource(id = R.string.library_cleaner_missing_label)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFF87171).copy(alpha = 0.90f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                  )
                }
              }
            }
            items(report.duplicateTracks, key = { "dup_${it.id}" }) { song ->
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(horizontal = 14.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
              ) {
                Icon(
                  imageVector = Icons.Rounded.ContentCopy,
                  contentDescription = null,
                  tint = Color(0xFFFBBF24),
                  modifier = Modifier.size(18.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = song.title,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                  )
                  Text(
                    text = "${song.artist} · ${stringResource(id = R.string.library_cleaner_duplicates_label)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFFBBF24).copy(alpha = 0.90f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                  )
                }
              }
            }
          }
        }

        // Action Button
        val doneToastTemplate = stringResource(
          id = R.string.library_cleaner_done_toast,
          report.duplicateTracks.size,
          report.missingFileTracks.size
        )
        Button(
          onClick = {
            if (report.hasIssues) {
              onRunCleanNow(report)
              Toast.makeText(context, doneToastTemplate, Toast.LENGTH_SHORT).show()
            } else {
              onDismiss()
            }
          },
          colors = ButtonDefaults.buttonColors(
            containerColor = if (report.hasIssues) accentColor else Color.White.copy(alpha = 0.14f),
            contentColor = if (report.hasIssues) Color(0xFF07101C) else Color.White
          ),
          shape = RoundedCornerShape(16.dp),
          modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 50.dp)
            .testTag("library_cleaner_clean_now_button")
        ) {
          Icon(
            imageVector = if (report.hasIssues) Icons.Rounded.CleaningServices else Icons.Rounded.CheckCircle,
            contentDescription = null,
            modifier = Modifier.size(18.dp)
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = if (report.hasIssues) {
              stringResource(id = R.string.library_cleaner_clean_now, report.totalIssuesCount)
            } else {
              stringResource(id = R.string.library_cleaner_all_clean)
            },
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }
      }
    }
  }
}

@Composable
private fun CleanerStatTile(
  icon: ImageVector,
  value: String,
  label: String,
  accent: Color,
  modifier: Modifier = Modifier
) {
  Surface(
    shape = RoundedCornerShape(18.dp),
    color = accent.copy(alpha = 0.12f),
    border = BorderStroke(1.dp, accent.copy(alpha = 0.42f)),
    modifier = modifier
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 10.dp, vertical = 12.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = accent,
        modifier = Modifier.size(20.dp)
      )
      Text(
        text = value,
        style = MaterialTheme.typography.titleMedium.copy(
          fontWeight = FontWeight.ExtraBold,
          fontSize = 17.sp
        ),
        color = Color.White,
        maxLines = 1
      )
      Text(
        text = label,
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
        color = Color.White.copy(alpha = 0.72f),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
    }
  }
}
