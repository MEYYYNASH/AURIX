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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ClearAll
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.DownloadQueueItem
import com.example.data.DownloadQueueStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadQueueSheet(
  items: List<DownloadQueueItem>,
  darkTheme: Boolean,
  onDismiss: () -> Unit,
  onCancelItem: (String) -> Unit,
  onRetryItem: (String) -> Unit,
  onCancelAll: () -> Unit,
  onRetryAllFailed: () -> Unit,
  onClearFinished: () -> Unit
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  val activeCount = items.count { it.isActive }
  val failedCount = items.count { it.status == DownloadQueueStatus.FAILED }
  val doneCount = items.count {
    it.status == DownloadQueueStatus.COMPLETED || it.status == DownloadQueueStatus.CANCELLED
  }

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = if (darkTheme) Color(0xFF141932) else Color(0xFFF6F8FF),
    contentColor = MaterialTheme.colorScheme.onSurface,
    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    modifier = Modifier.testTag("download_queue_sheet")
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .navigationBarsPadding()
        .padding(horizontal = 18.dp, vertical = 6.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      // Header
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(12.dp),
          modifier = Modifier.weight(1f)
        ) {
          Box(
            modifier = Modifier
              .size(40.dp)
              .clip(RoundedCornerShape(12.dp))
              .background(Color(0xFF2F8BFF).copy(alpha = 0.18f))
              .border(1.dp, Color(0xFF2F8BFF).copy(alpha = 0.35f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = Icons.Rounded.Download,
              contentDescription = null,
              tint = Color(0xFF2F8BFF),
              modifier = Modifier.size(20.dp)
            )
          }
          Column {
            Text(
              text = stringResource(id = R.string.download_queue_title),
              style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
              color = MaterialTheme.colorScheme.onSurface
            )
            val summary = buildList {
              if (activeCount > 0) add("$activeCount active")
              if (failedCount > 0) add("$failedCount failed")
              if (doneCount > 0) add("$doneCount finished")
            }.joinToString(" · ").ifBlank { "${items.size} items" }
            Text(
              text = summary,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }

        IconButton(
          onClick = onDismiss,
          modifier = Modifier
            .minimumInteractiveComponentSize()
            .testTag("download_queue_close_button")
        ) {
          Icon(
            imageVector = Icons.Rounded.Close,
            contentDescription = stringResource(id = R.string.error_dialog_dismiss),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }

      // Action Pills Row
      if (activeCount > 0 || failedCount > 0 || doneCount > 0) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          if (activeCount > 0) {
            QueueActionPill(
              icon = Icons.Rounded.StopCircle,
              label = stringResource(id = R.string.download_queue_end_all),
              tint = Color(0xFFF87171),
              containerColor = Color(0xFFF87171).copy(alpha = if (darkTheme) 0.18f else 0.14f),
              testTag = "download_queue_end_all_button",
              onClick = onCancelAll
            )
          }
          if (failedCount > 0) {
            QueueActionPill(
              icon = Icons.Rounded.Refresh,
              label = stringResource(id = R.string.download_queue_retry_failed),
              tint = Color(0xFF2F8BFF),
              containerColor = Color(0xFF2F8BFF).copy(alpha = if (darkTheme) 0.18f else 0.14f),
              testTag = "download_queue_retry_failed_button",
              onClick = onRetryAllFailed
            )
          }
          if (doneCount > 0) {
            QueueActionPill(
              icon = Icons.Rounded.ClearAll,
              label = stringResource(id = R.string.download_queue_clear_done),
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
              containerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
              testTag = "download_queue_clear_done_button",
              onClick = onClearFinished
            )
          }
        }
      }

      if (items.isEmpty()) {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(
              if (darkTheme) Color(0xFF1A223D).copy(alpha = 0.78f)
              else Color.White.copy(alpha = 0.85f)
            )
            .border(
              1.dp,
              MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f),
              RoundedCornerShape(22.dp)
            )
            .padding(24.dp),
          contentAlignment = Alignment.Center
        ) {
          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Icon(
              imageVector = Icons.Rounded.Download,
              contentDescription = null,
              tint = Color(0xFF2F8BFF).copy(alpha = 0.72f),
              modifier = Modifier.size(34.dp)
            )
            Text(
              text = stringResource(id = R.string.download_queue_empty_title),
              style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
              color = MaterialTheme.colorScheme.onSurface
            )
            Text(
              text = stringResource(id = R.string.download_queue_empty_sub),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }
      } else {
        LazyColumn(
          modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 420.dp),
          contentPadding = PaddingValues(bottom = 16.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          items(items, key = { it.key }) { item ->
            DownloadQueueItemRow(
              item = item,
              darkTheme = darkTheme,
              onCancel = { onCancelItem(item.key) },
              onRetry = { onRetryItem(item.key) }
            )
          }
        }
      }
      Spacer(modifier = Modifier.height(4.dp))
    }
  }
}

@Composable
private fun QueueActionPill(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  label: String,
  tint: Color,
  containerColor: Color,
  testTag: String,
  onClick: () -> Unit
) {
  Surface(
    onClick = onClick,
    shape = CircleShape,
    color = containerColor,
    border = BorderStroke(1.dp, tint.copy(alpha = 0.42f)),
    modifier = Modifier
      .heightIn(min = 48.dp)
      .testTag(testTag)
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = tint,
        modifier = Modifier.size(16.dp)
      )
      Text(
        text = label,
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
        color = tint,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
    }
  }
}

@Composable
private fun DownloadQueueItemRow(
  item: DownloadQueueItem,
  darkTheme: Boolean,
  onCancel: () -> Unit,
  onRetry: () -> Unit
) {
  val cardShape = RoundedCornerShape(20.dp)
  val statusColor = when (item.status) {
    DownloadQueueStatus.QUEUED -> Color(0xFFA78BFA)
    DownloadQueueStatus.DOWNLOADING -> Color(0xFF2F8BFF)
    DownloadQueueStatus.COMPLETED -> Color(0xFF10B981)
    DownloadQueueStatus.FAILED -> Color(0xFFF59E0B)
    DownloadQueueStatus.CANCELLED -> Color(0xFFF87171)
  }
  val pct = (item.progress * 100f).toInt().coerceIn(0, 100)
  val statusLabel = when (item.status) {
    DownloadQueueStatus.QUEUED -> stringResource(id = R.string.download_queue_status_queued)
    DownloadQueueStatus.DOWNLOADING -> if (pct > 0) "$pct%" else "..."
    DownloadQueueStatus.COMPLETED -> stringResource(id = R.string.download_queue_status_saved)
    DownloadQueueStatus.FAILED -> stringResource(id = R.string.download_queue_status_failed)
    DownloadQueueStatus.CANCELLED -> stringResource(id = R.string.download_queue_status_stopped)
  }

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .clip(cardShape)
      .background(
        if (darkTheme) Color(0xFF1A223D).copy(alpha = 0.84f)
        else Color.White.copy(alpha = 0.90f)
      )
      .border(1.dp, statusColor.copy(alpha = 0.24f), cardShape)
      .padding(horizontal = 12.dp, vertical = 10.dp)
      .testTag("download_queue_row_${item.key}"),
    verticalArrangement = Arrangement.spacedBy(8.dp)
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      SongArtwork(
        song = item.song,
        modifier = Modifier.size(46.dp),
        cornerRadius = 12.dp,
        iconSize = 22.dp
      )

      Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(2.dp)
      ) {
        Text(
          text = item.song.title,
          style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
          color = MaterialTheme.colorScheme.onSurface,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          Surface(
            shape = CircleShape,
            color = statusColor.copy(alpha = 0.16f),
            border = BorderStroke(1.dp, statusColor.copy(alpha = 0.40f))
          ) {
            Text(
              text = statusLabel,
              style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp
              ),
              color = statusColor,
              modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
            )
          }
          val detail = item.errorMessage?.takeIf { item.status == DownloadQueueStatus.FAILED }
            ?: item.statusHint.takeIf { it.isNotBlank() }
            ?: item.song.artist
          Text(
            text = detail,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }
      }

      when (item.status) {
        DownloadQueueStatus.QUEUED,
        DownloadQueueStatus.DOWNLOADING -> {
          IconButton(
            onClick = onCancel,
            modifier = Modifier
              .minimumInteractiveComponentSize()
              .clip(CircleShape)
              .background(Color(0xFFF87171).copy(alpha = 0.16f))
              .testTag("download_queue_cancel_${item.key}")
          ) {
            Icon(
              imageVector = Icons.Rounded.Close,
              contentDescription = stringResource(id = R.string.download_queue_cancel_cd),
              tint = Color(0xFFF87171),
              modifier = Modifier.size(18.dp)
            )
          }
        }
        DownloadQueueStatus.FAILED,
        DownloadQueueStatus.CANCELLED -> {
          IconButton(
            onClick = onRetry,
            modifier = Modifier
              .minimumInteractiveComponentSize()
              .clip(CircleShape)
              .background(Color(0xFF2F8BFF).copy(alpha = 0.16f))
              .testTag("download_queue_retry_${item.key}")
          ) {
            Icon(
              imageVector = Icons.Rounded.Refresh,
              contentDescription = stringResource(id = R.string.download_queue_retry_cd),
              tint = Color(0xFF2F8BFF),
              modifier = Modifier.size(18.dp)
            )
          }
        }
        DownloadQueueStatus.COMPLETED -> {
          Box(
            modifier = Modifier
              .size(36.dp)
              .clip(CircleShape)
              .background(Color(0xFF10B981).copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = Icons.Rounded.CheckCircle,
              contentDescription = null,
              tint = Color(0xFF10B981),
              modifier = Modifier.size(18.dp)
            )
          }
        }
      }
    }

    if (item.isActive) {
      LinearProgressIndicator(
        progress = { item.progress.coerceIn(0.02f, 1f) },
        modifier = Modifier
          .fillMaxWidth()
          .height(4.dp)
          .clip(CircleShape),
        color = statusColor,
        trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
      )
    }
  }
}
