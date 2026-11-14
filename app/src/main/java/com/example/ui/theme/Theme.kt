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
  amoledTheme: Boolean = false,
  accentColor: Color = AccentBlue,
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      amoledTheme -> darkColorScheme(
        primary = accentColor,
        onPrimary = Color.White,
        background = Color.Black,
        onBackground = DarkText,
        surface = AurixCardDark,
        onSurface = DarkText,
        surfaceVariant = AurixBorderDark,
        onSurfaceVariant = DarkMuted,
        outline = AurixBorderDark,
        error = HeartRed,
      )
      darkTheme -> darkColorScheme(
        primary = accentColor,
        onPrimary = Color.White,
        background = DarkBackground,
        onBackground = DarkText,
        surface = DarkSurface,
        onSurface = DarkText,
        surfaceVariant = DarkSurfaceVariant,
        onSurfaceVariant = DarkMuted,
        outline = AurixBorderDark,
        error = HeartRed,
      )
      else -> lightColorScheme(
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
