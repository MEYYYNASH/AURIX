package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.LyricsResult

@Composable
fun LyricsView(
  lyricsResult: LyricsResult?,
  currentPositionMs: Long,
  onLineClick: (Long) -> Unit
) {
  if (lyricsResult == null) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
      CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
    return
  }

  if (lyricsResult.synced.isNullOrEmpty()) {
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(32.dp),
      contentAlignment = Alignment.Center
    ) {
      Text(
        text = lyricsResult.plain ?: "No lyrics available.",
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurface
      )
    }
    return
  }

  val synced = lyricsResult.synced
  val activeIndex = synced.indexOfLast { it.timestampMs <= currentPositionMs }.coerceAtLeast(0)

  LazyColumn(
    modifier = Modifier
      .fillMaxSize()
      .padding(horizontal = 24.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(24.dp),
    contentPadding = PaddingValues(vertical = 48.dp)
  ) {
    itemsIndexed(synced) { index, line ->
      val isActive = index == activeIndex
      Text(
        text = line.text,
        style = MaterialTheme.typography.displayLarge.copy(
          fontSize = if (isActive) 24.sp else 18.sp,
          fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
        ),
        color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
        textAlign = TextAlign.Center,
        modifier = Modifier
          .fillMaxWidth()
          .clickable { onLineClick(line.timestampMs) }
          .padding(vertical = 8.dp)
      )
    }
  }
}
