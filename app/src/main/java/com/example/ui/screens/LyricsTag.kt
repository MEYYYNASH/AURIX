package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.AurixAccentCyan
import com.example.ui.theme.AurixAccentViolet
import com.example.ui.theme.AurixCardDark

enum class LyricsTagSize(
  val height: Dp,
  val startPadding: Dp,
  val endPadding: Dp,
  val iconSize: Dp,
  val fontSize: TextUnit,
  val spacing: Dp
) {
  Mini(
    height = 15.dp,
    startPadding = 4.5.dp,
    endPadding = 5.5.dp,
    iconSize = 8.5.dp,
    fontSize = 8.sp,
    spacing = 2.5.dp
  ),
  Standard(
    height = 17.dp,
    startPadding = 5.5.dp,
    endPadding = 7.dp,
    iconSize = 10.dp,
    fontSize = 9.2.sp,
    spacing = 3.dp
  ),
  Full(
    height = 20.dp,
    startPadding = 6.5.dp,
    endPadding = 8.5.dp,
    iconSize = 11.dp,
    fontSize = 10.2.sp,
    spacing = 3.5.dp
  )
}

@Composable
fun LyricsTag(
  visible: Boolean,
  modifier: Modifier = Modifier,
  size: LyricsTagSize = LyricsTagSize.Standard,
  darkTheme: Boolean = true,
  reducedMotion: Boolean = false,
  onClick: (() -> Unit)? = null
) {
  AnimatedVisibility(
    visible = visible,
    enter = if (reducedMotion) fadeIn(snap()) else fadeIn(tween(durationMillis = 200)),
    exit = if (reducedMotion) fadeOut(snap()) else fadeOut(tween(durationMillis = 150)),
    modifier = modifier.wrapContentWidth(unbounded = false)
  ) {
    val accent = MaterialTheme.colorScheme.primary
    val fillColor = if (darkTheme) {
      accent.copy(alpha = 0.14f)
    } else {
      accent.copy(alpha = 0.10f)
    }
    val contentTint = if (darkTheme) {
      androidx.compose.ui.graphics.lerp(accent, Color.White, 0.35f)
    } else {
      androidx.compose.ui.graphics.lerp(accent, Color(0xFF0F172A), 0.18f)
    }
    val iconTint = if (darkTheme) {
      androidx.compose.ui.graphics.lerp(accent, Color.White, 0.18f)
    } else {
      accent
    }
    val borderBrush = Brush.horizontalGradient(
      colors = listOf(
        accent.copy(alpha = if (darkTheme) 0.45f else 0.36f),
        AurixAccentViolet.copy(alpha = if (darkTheme) 0.38f else 0.28f)
      )
    )

    Surface(
      shape = CircleShape,
      color = fillColor,
      border = BorderStroke(0.75.dp, borderBrush),
      modifier = Modifier
        .height(size.height)
        .defaultMinSize(minWidth = 1.dp)
        .then(
          if (onClick != null) Modifier.clickable { onClick() } else Modifier
        )
        .testTag("lyrics_verified_tag")
    ) {
      Row(
        modifier = Modifier.padding(
          PaddingValues(start = size.startPadding, end = size.endPadding)
        ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(size.spacing)
      ) {
        Icon(
          painter = painterResource(id = R.drawable.ic_lyrics_tag),
          contentDescription = null,
          tint = iconTint,
          modifier = Modifier.size(size.iconSize)
        )
        Text(
          text = stringResource(id = R.string.lyrics_tag_label),
          style = MaterialTheme.typography.labelSmall.copy(
            fontSize = size.fontSize,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.25.sp,
            lineHeight = size.fontSize
          ),
          color = contentTint,
          maxLines = 1,
          softWrap = false,
          overflow = TextOverflow.Clip
        )
      }
    }
  }
}
