package com.example.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color

@Composable
fun AurixTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  amoledTheme: Boolean = false,
  accentColor: Color = AccentBlue,
  dynamicPalette: AlbumDynamicPalette? = null,
  reducedMotion: Boolean = false,
  content: @Composable () -> Unit,
) {
  val targetScheme = remember(darkTheme, amoledTheme, accentColor, dynamicPalette) {
    if (dynamicPalette != null) {
      AlbumPaletteThemeEngine.buildMaterialYouColorScheme(
        seedPalette = dynamicPalette,
        darkTheme = darkTheme,
        amoledTheme = amoledTheme
      )
    } else {
      AlbumPaletteThemeEngine.buildManualAccentColorScheme(
        accentColor = accentColor,
        darkTheme = darkTheme,
        amoledTheme = amoledTheme
      )
    }
  }

  val animatedScheme = animateColorScheme(targetScheme, reducedMotion = reducedMotion)

  MaterialTheme(colorScheme = animatedScheme, typography = Typography, content = content)
}

@Composable
private fun animateColorScheme(
  target: ColorScheme,
  reducedMotion: Boolean
): ColorScheme {
  val spec = if (reducedMotion) snap<Color>() else tween<Color>(durationMillis = 550)
  val primary by animateColorAsState(target.primary, spec, label = "m3_primary")
  val onPrimary by animateColorAsState(target.onPrimary, spec, label = "m3_onPrimary")
  val primaryContainer by animateColorAsState(target.primaryContainer, spec, label = "m3_primaryContainer")
  val onPrimaryContainer by animateColorAsState(target.onPrimaryContainer, spec, label = "m3_onPrimaryContainer")
  val inversePrimary by animateColorAsState(target.inversePrimary, spec, label = "m3_inversePrimary")
  val secondary by animateColorAsState(target.secondary, spec, label = "m3_secondary")
  val onSecondary by animateColorAsState(target.onSecondary, spec, label = "m3_onSecondary")
  val secondaryContainer by animateColorAsState(target.secondaryContainer, spec, label = "m3_secondaryContainer")
  val onSecondaryContainer by animateColorAsState(target.onSecondaryContainer, spec, label = "m3_onSecondaryContainer")
  val tertiary by animateColorAsState(target.tertiary, spec, label = "m3_tertiary")
  val onTertiary by animateColorAsState(target.onTertiary, spec, label = "m3_onTertiary")
  val tertiaryContainer by animateColorAsState(target.tertiaryContainer, spec, label = "m3_tertiaryContainer")
  val onTertiaryContainer by animateColorAsState(target.onTertiaryContainer, spec, label = "m3_onTertiaryContainer")
  val background by animateColorAsState(target.background, spec, label = "m3_background")
  val onBackground by animateColorAsState(target.onBackground, spec, label = "m3_onBackground")
  val surface by animateColorAsState(target.surface, spec, label = "m3_surface")
  val onSurface by animateColorAsState(target.onSurface, spec, label = "m3_onSurface")
  val surfaceVariant by animateColorAsState(target.surfaceVariant, spec, label = "m3_surfaceVariant")
  val onSurfaceVariant by animateColorAsState(target.onSurfaceVariant, spec, label = "m3_onSurfaceVariant")
  val surfaceContainerLowest by animateColorAsState(target.surfaceContainerLowest, spec, label = "m3_surfaceContainerLowest")
  val surfaceContainerLow by animateColorAsState(target.surfaceContainerLow, spec, label = "m3_surfaceContainerLow")
  val surfaceContainer by animateColorAsState(target.surfaceContainer, spec, label = "m3_surfaceContainer")
  val surfaceContainerHigh by animateColorAsState(target.surfaceContainerHigh, spec, label = "m3_surfaceContainerHigh")
  val surfaceContainerHighest by animateColorAsState(target.surfaceContainerHighest, spec, label = "m3_surfaceContainerHighest")
  val outline by animateColorAsState(target.outline, spec, label = "m3_outline")
  val outlineVariant by animateColorAsState(target.outlineVariant, spec, label = "m3_outlineVariant")

  return target.copy(
    primary = primary,
    onPrimary = onPrimary,
    primaryContainer = primaryContainer,
    onPrimaryContainer = onPrimaryContainer,
    inversePrimary = inversePrimary,
    secondary = secondary,
    onSecondary = onSecondary,
    secondaryContainer = secondaryContainer,
    onSecondaryContainer = onSecondaryContainer,
    tertiary = tertiary,
    onTertiary = onTertiary,
    tertiaryContainer = tertiaryContainer,
    onTertiaryContainer = onTertiaryContainer,
    background = background,
    onBackground = onBackground,
    surface = surface,
    onSurface = onSurface,
    surfaceVariant = surfaceVariant,
    onSurfaceVariant = onSurfaceVariant,
    surfaceContainerLowest = surfaceContainerLowest,
    surfaceContainerLow = surfaceContainerLow,
    surfaceContainer = surfaceContainer,
    surfaceContainerHigh = surfaceContainerHigh,
    surfaceContainerHighest = surfaceContainerHighest,
    outline = outline,
    outlineVariant = outlineVariant
  )
}

