package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(onTimeout: () -> Unit) {
  var started by remember { mutableStateOf(false) }

  LaunchedEffect(Unit) {
    started = true
    delay(1700)
    onTimeout()
  }

  val logoScale by animateFloatAsState(
    targetValue = if (started) 1f else 0.72f,
    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
    label = "splash_logo_scale"
  )
  val logoAlpha by animateFloatAsState(
    targetValue = if (started) 1f else 0f,
    animationSpec = tween(durationMillis = 500),
    label = "splash_logo_alpha"
  )

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.background)
      .background(
        Brush.radialGradient(
          colors = listOf(
            MaterialTheme.colorScheme.primary.copy(alpha = 0.24f),
            Color.Transparent
          )
        )
      ),
    contentAlignment = Alignment.Center
  ) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center
    ) {
      AurixBrandLogo(
        size = 104.dp,
        cornerRadius = 28.dp,
        animated = true,
        reducedMotion = false,
        modifier = Modifier
          .scale(logoScale)
          .alpha(logoAlpha)
      )
      Spacer(modifier = Modifier.height(26.dp))
      AnimatedVisibility(
        visible = started,
        enter = fadeIn(tween(600, delayMillis = 150)) + slideInVertically(tween(600, delayMillis = 150)) { it / 3 }
      ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          Text(
            text = "AURIX",
            style = MaterialTheme.typography.displayLarge.copy(
              fontWeight = FontWeight.Bold,
              letterSpacing = 5.sp
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
  }
}
