package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
fun AurixTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  accentColor: Color = AccentBlue,
  content: @Composable () -> Unit,
) {
  val colorScheme =
    if (darkTheme) {
      darkColorScheme(
        primary = accentColor,
        onPrimary = Color.White,
        background = DarkBackground,
        onBackground = DarkText,
        surface = DarkSurface,
        onSurface = DarkText,
        surfaceVariant = DarkSurfaceVariant,
        onSurfaceVariant = DarkMuted,
        error = HeartRed,
      )
    } else {
      lightColorScheme(
        primary = accentColor,
        onPrimary = Color.White,
        background = LightBackground,
        onBackground = LightText,
        surface = LightSurface,
        onSurface = LightText,
        surfaceVariant = LightSurfaceVariant,
        onSurfaceVariant = LightMuted,
        error = HeartRed,
      )
    }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
