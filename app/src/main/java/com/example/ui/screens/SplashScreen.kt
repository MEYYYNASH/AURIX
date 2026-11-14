package com.example.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(onTimeout: () -> Unit) {
  LaunchedEffect(Unit) {
    delay(1800)
    onTimeout()
  }

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(
        Brush.verticalGradient(
          colors = listOf(
            MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
            MaterialTheme.colorScheme.background
          )
        )
      ),
    contentAlignment = Alignment.Center
  ) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center
    ) {
      Box(
        modifier = Modifier
          .size(96.dp)
          .clip(RoundedCornerShape(28.dp))
          .background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = Icons.Rounded.MusicNote,
          contentDescription = "Logo",
          tint = Color.White,
          modifier = Modifier.size(48.dp)
        )
      }
      Spacer(modifier = Modifier.height(24.dp))
      Text(
        text = "AURIX",
        style = MaterialTheme.typography.displayLarge.copy(
          fontWeight = FontWeight.Bold,
          letterSpacing = 4.sp
        ),
        color = MaterialTheme.colorScheme.onBackground
      )
      Spacer(modifier = Modifier.height(8.dp))
      Text(
        text = "ONE APP. ALL YOUR MUSIC.",
        style = MaterialTheme.typography.labelMedium.copy(
          letterSpacing = 2.sp
        ),
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
  }
}
