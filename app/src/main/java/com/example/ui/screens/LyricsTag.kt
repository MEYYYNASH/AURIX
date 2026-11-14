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
    height = 16.dp,
    startPadding = 5.dp,
    endPadding = 6.dp,
    iconSize = 9.5.dp,
    fontSize = 8.5.sp,
    spacing = 3.dp
  ),
  Standard(
    height = 18.dp,
    startPadding = 6.dp,
    endPadding = 8.dp,
    iconSize = 11.dp,
    fontSize = 10.sp,
    spacing = 4.dp
  ),
  Full(
    height = 22.dp,
    startPadding = 7.dp,
    endPadding = 9.dp,
    iconSize = 12.dp,
    fontSize = 11.sp,
    spacing = 4.dp
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
    enter = if (reducedMotion) fadeIn(snap()) else fadeIn(tween(durationMillis = 220)),
    exit = if (reducedMotion) fadeOut(snap()) else fadeOut(tween(durationMillis = 160)),
    modifier = modifier.wrapContentWidth(unbounded = false)
  ) {
    val fillColor = if (darkTheme) {
      AurixCardDark
    } else {
      MaterialTheme.colorScheme.surface
    }
    val contentTint = if (darkTheme) {
      Color(0xFFEBF7FF)
    } else {
      Color(0xFF1E293B)
    }
    val iconTint = if (darkTheme) {
      AurixAccentCyan
    } else {
      Color(0xFF0284C7)
    }
    val borderBrush = Brush.horizontalGradient(
      colors = listOf(AurixAccentCyan, AurixAccentViolet)
    )

    Surface(
      shape = CircleShape,
      color = fillColor,
      border = BorderStroke(1.dp, borderBrush),
      modifier = Modifier
        .height(size.height)
        .defaultMinSize(minWidth = 1.dp)
        .shadow(
          elevation = if (darkTheme) 6.dp else 3.dp,
          shape = CircleShape,
          ambientColor = AurixAccentCyan.copy(alpha = 0.55f),
          spotColor = AurixAccentViolet.copy(alpha = 0.55f)
        )
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
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.4.sp,
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
