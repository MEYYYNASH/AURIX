package com.example.ui.screens

import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.example.R
import com.example.ui.theme.AurixFontFamilies
import com.example.ui.theme.KhmerTypography
import com.example.widget.AurixWidgetController
import com.example.widget.NowPlayingThemeResolver
import com.example.widget.NowPlayingWidgetSize
import com.example.widget.NowPlayingWidgetStyle
import com.example.widget.VinylCoverPalette
import com.example.widget.VinylWidgetRenderer
import com.example.widget.WidgetGlassStyle
import com.example.widget.WidgetKind
import com.example.widget.WidgetStyleStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val PaperSerifFamily = FontFamily(
  Font(R.font.playfair_display, FontWeight.Normal),
  Font(R.font.playfair_display, FontWeight.Bold)
)

@Composable
fun SettingsWidgetsGlassCard(
  darkTheme: Boolean,
  isKhmer: Boolean,
  reducedMotion: Boolean,
  currentSongTitle: String?,
  currentSongArtist: String?,
  currentSongArtworkUrl: String?,
  isPlaying: Boolean,
  nowPlayingWidgetStyle: NowPlayingWidgetStyle,
  widgetMatchSystemTheme: Boolean,
  widgetShowLikeButton: Boolean,
  widgetShowLyrics: Boolean,
  onSelectNowPlayingWidgetStyle: (NowPlayingWidgetStyle) -> Unit,
  onWidgetMatchSystemThemeChanged: (Boolean) -> Unit,
  onWidgetShowLikeButtonChanged: (Boolean) -> Unit,
  onWidgetShowLyricsChanged: (Boolean) -> Unit,
  onApplyGlassStyleToAll: (WidgetGlassStyle) -> Unit
) {
  val context = LocalContext.current
  var selectedClassicStyle by remember { mutableStateOf(WidgetGlassStyle.FROST_PILL) }
  var previewSize by remember { mutableStateOf(NowPlayingWidgetSize.PLAYER) }
  var lastToast by remember { mutableStateOf<Toast?>(null) }

  LaunchedEffect(Unit) {
    selectedClassicStyle = withContext(Dispatchers.IO) {
      WidgetStyleStore.getLastChosenStyleForKind(context, WidgetKind.MUSIC)
    }
  }

  val snapshot by produceState<com.example.widget.WidgetSnapshotState?>(initialValue = null) {
    value = withContext(Dispatchers.IO) { AurixWidgetController.readSnapshot(context) }
  }
  val previewBitmap by produceState<Bitmap?>(
    initialValue = null,
    key1 = currentSongArtworkUrl,
    key2 = currentSongTitle
  ) {
    value = withContext(Dispatchers.IO) {
      val snap = AurixWidgetController.readSnapshot(context)
      AurixWidgetController.loadArtworkBitmapForPreview(context, snap)
    }
  }

  val displayTitle = currentSongTitle?.takeIf { it.isNotBlank() }
    ?: snapshot?.title?.takeIf { it.isNotBlank() }
    ?: stringResource(R.string.widget_preview_title)
  val displayArtist = currentSongArtist?.takeIf { it.isNotBlank() }
    ?: snapshot?.artist?.takeIf { it.isNotBlank() }
    ?: stringResource(R.string.widget_preview_artist)
  val displayArtworkUrl = currentSongArtworkUrl?.takeIf { it.isNotBlank() }
    ?: snapshot?.artworkUrl.orEmpty()
  val displayCurrentLyric = snapshot?.currentLyricLine?.takeIf { snapshot?.hasLyrics == true && it.isNotBlank() }
    ?: stringResource(R.string.widget_preview_lyric_current)
  val displayNextLyric = snapshot?.nextLyricLine?.takeIf { snapshot?.hasLyrics == true && it.isNotBlank() }
    ?: stringResource(R.string.widget_preview_lyric_next)
  val isLiked = snapshot?.isFavorite == true

  GlassCard(darkTheme = darkTheme, cornerRadius = 24.dp, modifier = Modifier.fillMaxWidth()) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(18.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
      // Section Header
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
          Text(
            text = stringResource(id = R.string.settings_widgets_section_title),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, lineHeight = 24.sp),
            color = MaterialTheme.colorScheme.onSurface
          )
          Text(
            text = stringResource(id = R.string.settings_widgets_classic_title),
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.primary
          )
          Text(
            text = stringResource(id = R.string.settings_widgets_classic_subtitle),
            style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Box(
          modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(
              Brush.linearGradient(
                listOf(
                  Color(0xFF43C7F4).copy(alpha = 0.22f),
                  Color(0xFF7965FF).copy(alpha = 0.22f)
                )
              )
            ),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = Icons.Rounded.Widgets,
            contentDescription = null,
            tint = Color(0xFF43C7F4),
            modifier = Modifier.size(22.dp)
          )
        }
      }

      // Classic 4 Widgets Quick-Apply Row (6 styles incl. Vinyl)
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        WidgetGlassStyle.entries.forEach { glassStyle ->
          val selected = glassStyle == selectedClassicStyle
          val label = if (isKhmer) glassStyle.displayNameKm else glassStyle.displayNameEn
          SettingsWidgetChip(
            label = label,
            selected = selected,
            darkTheme = darkTheme,
            modifier = Modifier.testTag("settings_classic_widget_style_${glassStyle.id}"),
            onClick = {
              selectedClassicStyle = glassStyle
              onApplyGlassStyleToAll(glassStyle)
              lastToast?.cancel()
              lastToast = Toast.makeText(
                context,
                context.getString(R.string.settings_widgets_applied_all_toast, label),
                Toast.LENGTH_SHORT
              ).also { it.show() }
            }
          )
        }
      }

      HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

      // AURIX Now Playing (Resizable 3 sizes x 10 styles) Header
      Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
          text = stringResource(id = R.string.settings_widgets_np_title),
          style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, lineHeight = 24.sp),
          color = MaterialTheme.colorScheme.onSurface
        )
        Text(
          text = stringResource(id = R.string.settings_widgets_np_subtitle),
          style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }

      // 3-Size Segmented Tab Selector (Compact / Player / Lyrics)
      Surface(
        shape = RoundedCornerShape(999.dp),
        color = if (darkTheme) Color(0xFF0E1320).copy(alpha = 0.78f) else Color(0xFFE1E8F6),
        modifier = Modifier.fillMaxWidth()
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(4.dp),
          horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
          NowPlayingWidgetSize.entries.forEach { sizeOption ->
            val selected = sizeOption == previewSize
            val sizeLabel = if (isKhmer) sizeOption.labelKm else sizeOption.labelEn
            Surface(
              onClick = { previewSize = sizeOption },
              shape = RoundedCornerShape(999.dp),
              color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
              modifier = Modifier
                .weight(1f)
                .defaultMinSize(minHeight = 48.dp)
                .testTag("settings_np_size_${sizeOption.id}")
            ) {
              Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp)) {
                Text(
                  text = sizeLabel,
                  style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                  ),
                  color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis
                )
              }
            }
          }
        }
      }

      // Live Interactive Widget Showcase
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(22.dp))
          .background(
            Brush.linearGradient(
              colors = listOf(Color(0xFF171F34), Color(0xFF0D121F), Color(0xFF1A142B))
            )
          )
          .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(22.dp))
          .padding(14.dp),
        contentAlignment = Alignment.Center
      ) {
        NowPlayingWidgetLivePreview(
          style = nowPlayingWidgetStyle,
          size = previewSize,
          matchSystemTheme = widgetMatchSystemTheme,
          showLikeButton = widgetShowLikeButton,
          showLyrics = widgetShowLyrics,
          reducedMotion = reducedMotion,
          title = displayTitle,
          artist = displayArtist,
          artworkUrl = displayArtworkUrl,
          rawArtwork = previewBitmap,
          currentLyric = displayCurrentLyric,
          nextLyric = displayNextLyric,
          isPlaying = isPlaying,
          isLiked = isLiked,
          positionMs = snapshot?.positionMs ?: 74_000L,
          durationMs = snapshot?.durationMs ?: 208_000L
        )
      }

      // 10 Style Horizontal Chips
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        NowPlayingWidgetStyle.entries.forEach { npStyle ->
          val selected = npStyle == nowPlayingWidgetStyle
          val styleLabel = if (isKhmer) npStyle.displayNameKm else npStyle.displayNameEn
          SettingsWidgetChip(
            label = styleLabel,
            selected = selected,
            darkTheme = darkTheme,
            modifier = Modifier.testTag("settings_np_style_${npStyle.id}"),
            onClick = { onSelectNowPlayingWidgetStyle(npStyle) }
          )
        }
      }

      HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

      // 3 Switch Toggles
      SettingsWidgetSwitchRow(
        title = stringResource(id = R.string.settings_widgets_match_system),
        subtitle = stringResource(id = R.string.settings_widgets_match_system_sub),
        checked = widgetMatchSystemTheme,
        onCheckedChange = onWidgetMatchSystemThemeChanged,
        testTag = "settings_np_toggle_match_system"
      )
      SettingsWidgetSwitchRow(
        title = stringResource(id = R.string.settings_widgets_show_like),
        subtitle = stringResource(id = R.string.settings_widgets_show_like_sub),
        checked = widgetShowLikeButton,
        onCheckedChange = onWidgetShowLikeButtonChanged,
        testTag = "settings_np_toggle_show_like"
      )
      SettingsWidgetSwitchRow(
        title = stringResource(id = R.string.settings_widgets_show_lyrics),
        subtitle = stringResource(id = R.string.settings_widgets_show_lyrics_sub),
        checked = widgetShowLyrics,
        onCheckedChange = onWidgetShowLyricsChanged,
        testTag = "settings_np_toggle_show_lyrics"
      )
    }
  }
}

@Composable
private fun SettingsWidgetChip(
  label: String,
  selected: Boolean,
  darkTheme: Boolean,
  modifier: Modifier = Modifier,
  onClick: () -> Unit
) {
  val bg by animateColorAsState(
    targetValue = if (selected) {
      MaterialTheme.colorScheme.primary
    } else if (darkTheme) {
      Color(0xFF141B2B)
    } else {
      Color(0xFFE7EDF8)
    },
    animationSpec = tween(180),
    label = "widgetChipBg"
  )
  val border by animateColorAsState(
    targetValue = if (selected) {
      MaterialTheme.colorScheme.primary
    } else {
      MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f)
    },
    animationSpec = tween(180),
    label = "widgetChipBorder"
  )
  val contentColor = if (selected) {
    MaterialTheme.colorScheme.onPrimary
  } else {
    MaterialTheme.colorScheme.onSurface
  }

  Surface(
    onClick = onClick,
    shape = RoundedCornerShape(16.dp),
    color = bg,
    border = BorderStroke(1.dp, border),
    modifier = modifier.defaultMinSize(minHeight = 48.dp)
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
      if (selected) {
        Icon(
          imageVector = Icons.Rounded.Check,
          contentDescription = null,
          tint = contentColor,
          modifier = Modifier.size(16.dp)
        )
      }
      Text(
        text = label,
        style = MaterialTheme.typography.labelLarge.copy(
          fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
        ),
        color = contentColor,
        maxLines = 1
      )
    }
  }
}

@Composable
private fun SettingsWidgetSwitchRow(
  title: String,
  subtitle: String,
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
  testTag: String
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .defaultMinSize(minHeight = 56.dp)
      .clip(RoundedCornerShape(16.dp))
      .clickable { onCheckedChange(!checked) }
      .padding(horizontal = 4.dp, vertical = 6.dp)
      .testTag(testTag),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween
  ) {
    Column(
      modifier = Modifier
        .weight(1f)
        .padding(end = 12.dp),
      verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
      Text(
        text = title,
        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
        color = MaterialTheme.colorScheme.onSurface
      )
      Text(
        text = subtitle,
        style = MaterialTheme.typography.bodySmall.copy(lineHeight = 17.sp),
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
    Switch(
      checked = checked,
      onCheckedChange = onCheckedChange
    )
  }
}

@Composable
private fun NowPlayingWidgetLivePreview(
  style: NowPlayingWidgetStyle,
  size: NowPlayingWidgetSize,
  matchSystemTheme: Boolean,
  showLikeButton: Boolean,
  showLyrics: Boolean,
  reducedMotion: Boolean,
  title: String,
  artist: String,
  artworkUrl: String,
  rawArtwork: Bitmap?,
  currentLyric: String,
  nextLyric: String,
  isPlaying: Boolean,
  isLiked: Boolean,
  positionMs: Long,
  durationMs: Long
) {
  val context = LocalContext.current
  val palette = remember(rawArtwork, title, artist) {
    NowPlayingThemeResolver.extractPaletteColors(rawArtwork, "$title|$artist")
  }
  val sysAccent = remember(matchSystemTheme) {
    if (matchSystemTheme) NowPlayingThemeResolver.readSystemAccentIfAvailable(context) else null
  }
  val sysLightBg = remember(matchSystemTheme) {
    if (matchSystemTheme) NowPlayingThemeResolver.readSystemLightSurfaceIfAvailable(context) else null
  }
  val tokens = remember(style, palette, matchSystemTheme, sysAccent, sysLightBg) {
    NowPlayingThemeResolver.resolveTokens(
      style = style,
      coverColors = palette,
      matchSystemTheme = matchSystemTheme,
      systemAccentColor = sysAccent,
      systemLightBg = sysLightBg
    )
  }

  val corner = when (style) {
    NowPlayingWidgetStyle.PIXEL -> 8.dp
    NowPlayingWidgetStyle.PAPER -> 18.dp
    NowPlayingWidgetStyle.FLOAT -> 24.dp
    else -> 28.dp
  }
  val effectiveFontFamily = when {
    style == NowPlayingWidgetStyle.PIXEL -> FontFamily.Monospace
    style == NowPlayingWidgetStyle.PAPER && !KhmerTypography.containsKhmer(title) -> PaperSerifFamily
    else -> AurixFontFamilies.AurixUiFamily
  }
  val progressFraction = if (durationMs > 0L) {
    (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0.06f, 1f)
  } else {
    0.38f
  }
  val effectiveSize = if (size == NowPlayingWidgetSize.LYRICS && !showLyrics) {
    NowPlayingWidgetSize.PLAYER
  } else {
    size
  }
  val cardHeight = when (effectiveSize) {
    NowPlayingWidgetSize.COMPACT -> 76.dp
    NowPlayingWidgetSize.PLAYER -> 148.dp
    NowPlayingWidgetSize.LYRICS -> 196.dp
  }

  val baseAngle = VinylWidgetRenderer.computeVinylAngleDegrees(positionMs, isPlaying, reducedMotion)
  val vinylSpin = if (style == NowPlayingWidgetStyle.VINYL && isPlaying && !reducedMotion) {
    val infinite = rememberInfiniteTransition(label = "settings_np_vinyl")
    val delta by infinite.animateFloat(
      initialValue = 0f,
      targetValue = 360f,
      animationSpec = infiniteRepeatable(
        animation = tween(VinylWidgetRenderer.TURN_DURATION_MS.toInt(), easing = LinearEasing),
        repeatMode = RepeatMode.Restart
      ),
      label = "settings_np_vinyl_deg"
    )
    (baseAngle + delta) % 360f
  } else {
    baseAngle
  }

  val cardBgColor = Color(tokens.cardBg)
  val secondaryBgColor = Color(tokens.cardSecondary)
  val primaryText = Color(tokens.textPrimary)
  val secondaryText = Color(tokens.textSecondary)
  val accentColor = Color(tokens.accent)
  val playBg = Color(tokens.playButtonBg)
  val playIcon = Color(tokens.playButtonIcon)
  val borderColor = Color(tokens.border)

  Box(
    modifier = Modifier
      .fillMaxWidth()
      .height(cardHeight)
      .clip(RoundedCornerShape(corner))
      .background(
        when (style) {
          NowPlayingWidgetStyle.AURA -> Brush.linearGradient(
            listOf(cardBgColor, secondaryBgColor.copy(alpha = 0.85f), cardBgColor)
          )
          else -> Brush.linearGradient(listOf(cardBgColor, cardBgColor))
        }
      )
      .border(1.dp, borderColor, RoundedCornerShape(corner))
      .clipToBounds()
  ) {
    // Paper fibers + top-right soft highlight for Vinyl & Paper styles
    if (style == NowPlayingWidgetStyle.VINYL || style == NowPlayingWidgetStyle.PAPER) {
      Canvas(modifier = Modifier.matchParentSize()) {
        drawRect(
          brush = Brush.radialGradient(
            colors = listOf(Color.White.copy(alpha = 0.45f), Color.Transparent),
            center = Offset(this.size.width * 0.92f, this.size.height * 0.08f),
            radius = this.size.maxDimension * 0.82f
          )
        )
        var y = 0f
        val step = 3.dp.toPx().coerceAtLeast(3f)
        while (y < this.size.height) {
          drawLine(
            color = Color(0xFF785A32).copy(alpha = 0.035f),
            start = Offset(0f, y),
            end = Offset(this.size.width, y),
            strokeWidth = 1f
          )
          y += step
        }
      }
    }

    // Half-visible Vinyl disc behind left edge when Vinyl style is active
    if (style == NowPlayingWidgetStyle.VINYL) {
      val discSize = when (effectiveSize) {
        NowPlayingWidgetSize.COMPACT -> 66.dp
        NowPlayingWidgetSize.PLAYER -> 136.dp
        NowPlayingWidgetSize.LYRICS -> 116.dp
      }
      SettingsVinylDiscCanvas(
        diameter = discSize,
        palette = palette,
        rotationDegrees = vinylSpin,
        modifier = Modifier
          .align(if (effectiveSize == NowPlayingWidgetSize.LYRICS) Alignment.TopStart else Alignment.CenterStart)
          .offset(
            x = -(discSize / 2),
            y = if (effectiveSize == NowPlayingWidgetSize.LYRICS) 18.dp else 0.dp
          )
          .zIndex(0f)
      )
    }

    val startPadding = if (style == NowPlayingWidgetStyle.VINYL) {
      when (effectiveSize) {
        NowPlayingWidgetSize.COMPACT -> 44.dp
        NowPlayingWidgetSize.PLAYER -> 78.dp
        NowPlayingWidgetSize.LYRICS -> 68.dp
      }
    } else {
      14.dp
    }

    when (effectiveSize) {
      NowPlayingWidgetSize.COMPACT -> {
        Row(
          modifier = Modifier
            .fillMaxSize()
            .zIndex(1f)
            .padding(start = startPadding, end = 12.dp, top = 10.dp, bottom = 10.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          if (style != NowPlayingWidgetStyle.VINYL && style != NowPlayingWidgetStyle.TYPE) {
            PreviewArtworkSlot(
              style = style,
              size = 50.dp,
              artworkUrl = artworkUrl,
              rawArtwork = rawArtwork,
              progressFraction = progressFraction,
              accentColor = accentColor,
              trackColor = Color(tokens.progressTrack)
            )
          }
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = title,
              color = primaryText,
              fontFamily = effectiveFontFamily,
              fontWeight = FontWeight.Bold,
              fontSize = 14.sp,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
            Text(
              text = artist,
              color = secondaryText,
              fontFamily = effectiveFontFamily,
              fontSize = 11.5.sp,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
          }
          Box(
            modifier = Modifier
              .size(38.dp)
              .clip(CircleShape)
              .background(playBg),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
              contentDescription = null,
              tint = playIcon,
              modifier = Modifier.size(20.dp)
            )
          }
          Icon(
            imageVector = Icons.Rounded.SkipNext,
            contentDescription = null,
            tint = primaryText,
            modifier = Modifier.size(22.dp)
          )
        }
      }

      NowPlayingWidgetSize.PLAYER -> {
        Column(
          modifier = Modifier
            .fillMaxSize()
            .zIndex(1f)
            .padding(start = startPadding, end = 14.dp, top = 12.dp, bottom = 10.dp),
          verticalArrangement = Arrangement.SpaceBetween
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
          ) {
            if (style != NowPlayingWidgetStyle.VINYL && style != NowPlayingWidgetStyle.TYPE) {
              PreviewArtworkSlot(
                style = style,
                size = 56.dp,
                artworkUrl = artworkUrl,
                rawArtwork = rawArtwork,
                progressFraction = progressFraction,
                accentColor = accentColor,
                trackColor = Color(tokens.progressTrack)
              )
            }
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = title,
                color = primaryText,
                fontFamily = effectiveFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 15.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
              Spacer(modifier = Modifier.height(2.dp))
              Text(
                text = artist,
                color = secondaryText,
                fontFamily = effectiveFontFamily,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
            }
          }

          Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            PreviewProgressBar(
              style = style,
              fraction = progressFraction,
              fillColor = Color(tokens.progressFill),
              trackColor = Color(tokens.progressTrack)
            )
            if (style == NowPlayingWidgetStyle.VINYL || style == NowPlayingWidgetStyle.PAPER) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
              ) {
                Text(
                  text = VinylWidgetRenderer.formatTabularTime(positionMs),
                  color = secondaryText,
                  fontSize = 10.sp
                )
                Text(
                  text = VinylWidgetRenderer.formatTabularTime(durationMs),
                  color = secondaryText,
                  fontSize = 10.sp
                )
              }
            }
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Icon(Icons.Rounded.SkipPrevious, contentDescription = null, tint = primaryText, modifier = Modifier.size(21.dp))
            Box(
              modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(playBg),
              contentAlignment = Alignment.Center
            ) {
              Icon(
                imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = null,
                tint = playIcon,
                modifier = Modifier.size(20.dp)
              )
            }
            Icon(Icons.Rounded.SkipNext, contentDescription = null, tint = primaryText, modifier = Modifier.size(21.dp))
            if (showLikeButton) {
              Icon(
                imageVector = if (isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                contentDescription = null,
                tint = if (isLiked) Color(0xFFEF4444) else primaryText,
                modifier = Modifier.size(19.dp)
              )
            } else {
              Spacer(modifier = Modifier.size(19.dp))
            }
          }
        }
      }

      NowPlayingWidgetSize.LYRICS -> {
        Column(
          modifier = Modifier
            .fillMaxSize()
            .zIndex(1f)
            .padding(start = startPadding, end = 14.dp, top = 12.dp, bottom = 12.dp),
          verticalArrangement = Arrangement.SpaceBetween
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            if (style != NowPlayingWidgetStyle.VINYL && style != NowPlayingWidgetStyle.TYPE) {
              PreviewArtworkSlot(
                style = style,
                size = 38.dp,
                artworkUrl = artworkUrl,
                rawArtwork = rawArtwork,
                progressFraction = progressFraction,
                accentColor = accentColor,
                trackColor = Color(tokens.progressTrack)
              )
            }
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = title,
                color = primaryText,
                fontFamily = effectiveFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 13.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
              Text(
                text = artist,
                color = secondaryText,
                fontFamily = effectiveFontFamily,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
            }
            if (showLikeButton) {
              Icon(
                imageVector = if (isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                contentDescription = null,
                tint = if (isLiked) Color(0xFFEF4444) else secondaryText,
                modifier = Modifier.size(18.dp)
              )
            }
          }

          Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
              text = currentLyric,
              color = primaryText,
              fontFamily = effectiveFontFamily,
              fontWeight = FontWeight.Bold,
              fontSize = 16.sp,
              lineHeight = 23.sp,
              maxLines = 2,
              overflow = TextOverflow.Ellipsis
            )
            Text(
              text = nextLyric,
              color = secondaryText,
              fontFamily = effectiveFontFamily,
              fontSize = 12.5.sp,
              lineHeight = 18.sp,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
          ) {
            Box(modifier = Modifier.weight(1f)) {
              PreviewProgressBar(
                style = style,
                fraction = progressFraction,
                fillColor = Color(tokens.progressFill),
                trackColor = Color(tokens.progressTrack)
              )
            }
            Row(
              horizontalArrangement = Arrangement.spacedBy(10.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Icon(Icons.Rounded.SkipPrevious, contentDescription = null, tint = primaryText, modifier = Modifier.size(19.dp))
              Box(
                modifier = Modifier
                  .size(32.dp)
                  .clip(CircleShape)
                  .background(playBg),
                contentAlignment = Alignment.Center
              ) {
                Icon(
                  imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                  contentDescription = null,
                  tint = playIcon,
                  modifier = Modifier.size(18.dp)
                )
              }
              Icon(Icons.Rounded.SkipNext, contentDescription = null, tint = primaryText, modifier = Modifier.size(19.dp))
            }
          }
        }
      }
    }
  }
}

@Composable
private fun PreviewArtworkSlot(
  style: NowPlayingWidgetStyle,
  size: Dp,
  artworkUrl: String,
  rawArtwork: Bitmap?,
  progressFraction: Float,
  accentColor: Color,
  trackColor: Color
) {
  val artCorner = when (style) {
    NowPlayingWidgetStyle.PIXEL -> 4.dp
    NowPlayingWidgetStyle.RING -> size / 2
    else -> (size * 0.24f)
  }

  Box(modifier = Modifier.size(size), contentAlignment = Alignment.Center) {
    if (style == NowPlayingWidgetStyle.RING) {
      Canvas(modifier = Modifier.matchParentSize()) {
        val strokeW = 3.dp.toPx()
        val inset = strokeW / 2f
        val arcSize = Size(this.size.width - strokeW, this.size.height - strokeW)
        drawArc(
          color = trackColor,
          startAngle = 0f,
          sweepAngle = 360f,
          useCenter = false,
          topLeft = Offset(inset, inset),
          size = arcSize,
          style = Stroke(width = strokeW, cap = StrokeCap.Round)
        )
        drawArc(
          color = accentColor,
          startAngle = -90f,
          sweepAngle = 360f * progressFraction.coerceIn(0.05f, 1f),
          useCenter = false,
          topLeft = Offset(inset, inset),
          size = arcSize,
          style = Stroke(width = strokeW, cap = StrokeCap.Round)
        )
      }
    }

    val innerSize = if (style == NowPlayingWidgetStyle.RING) size - 8.dp else size
    Box(
      modifier = Modifier
        .size(innerSize)
        .clip(RoundedCornerShape(artCorner))
        .background(Color(0xFF1F273B)),
      contentAlignment = Alignment.Center
    ) {
      when {
        rawArtwork != null -> Image(
          bitmap = rawArtwork.asImageBitmap(),
          contentDescription = null,
          contentScale = ContentScale.Crop,
          filterQuality = com.example.ui.image.AurixImagePipeline.ARTWORK_FILTER_QUALITY,
          modifier = Modifier.fillMaxSize()
        )
        artworkUrl.isNotBlank() -> {
          val previewArtReq = com.example.ui.image.rememberCrispArtworkRequest(model = artworkUrl)
          AsyncImage(
            model = previewArtReq,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            filterQuality = com.example.ui.image.AurixImagePipeline.ARTWORK_FILTER_QUALITY,
            modifier = Modifier.fillMaxSize()
          )
        }
        else -> Icon(
          imageVector = Icons.Rounded.MusicNote,
          contentDescription = null,
          tint = Color.White.copy(alpha = 0.72f),
          modifier = Modifier.size(innerSize * 0.45f)
        )
      }
    }
  }
}

@Composable
private fun PreviewProgressBar(
  style: NowPlayingWidgetStyle,
  fraction: Float,
  fillColor: Color,
  trackColor: Color
) {
  if (style == NowPlayingWidgetStyle.PIXEL) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .height(5.dp),
      horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
      val segments = 14
      val activeCount = (segments * fraction).toInt().coerceIn(1, segments)
      for (i in 0 until segments) {
        Box(
          modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .background(if (i < activeCount) fillColor else trackColor)
        )
      }
    }
  } else {
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .height(3.5.dp)
        .clip(CircleShape)
        .background(trackColor)
    ) {
      Box(
        modifier = Modifier
          .fillMaxHeight()
          .fillMaxWidth(fraction.coerceIn(0.05f, 1f))
          .background(fillColor)
      )
    }
  }
}

@Composable
private fun SettingsVinylDiscCanvas(
  diameter: Dp,
  palette: VinylCoverPalette,
  rotationDegrees: Float,
  modifier: Modifier = Modifier
) {
  Canvas(modifier = modifier.size(diameter)) {
    val d = size.minDimension
    val r = d * 0.5f
    val center = Offset(r, r)
    val vinylRadius = r * 0.94f

    rotate(degrees = rotationDegrees, pivot = center) {
      drawCircle(color = Color(0xFF141414), radius = vinylRadius, center = center)
      val labelRadius = vinylRadius * 0.30f
      val stepPx = 3.dp.toPx()
      var grooveR = labelRadius + 3.dp.toPx()
      while (grooveR < vinylRadius - 2.dp.toPx()) {
        drawCircle(
          color = Color(0xFF262626),
          radius = grooveR,
          center = center,
          style = Stroke(width = 1.1.dp.toPx())
        )
        grooveR += stepPx
      }
      drawCircle(
        brush = Brush.sweepGradient(
          colorStops = arrayOf(
            0.0f to Color.Transparent,
            0.14f to Color.White.copy(alpha = 0.17f),
            0.30f to Color.Transparent,
            0.50f to Color.Transparent,
            0.65f to Color.White.copy(alpha = 0.13f),
            0.84f to Color.Transparent
          ),
          center = center
        ),
        radius = vinylRadius,
        center = center
      )
      drawCircle(color = Color(palette.coverBase), radius = labelRadius, center = center)
      drawCircle(color = Color(palette.coverLight), radius = vinylRadius * 0.08f, center = center)
      drawCircle(color = Color(0xFFEFE6D6), radius = (vinylRadius * 0.04f).coerceAtLeast(2.dp.toPx()), center = center)
      drawCircle(
        color = Color(0xFFEFE6D6),
        radius = (vinylRadius * 0.05f).coerceAtLeast(2.dp.toPx()),
        center = Offset(center.x + vinylRadius * 0.11f, center.y - vinylRadius * 0.18f)
      )
    }

    drawCircle(
      brush = Brush.linearGradient(
        colorStops = arrayOf(
          0.0f to Color.White.copy(alpha = 0.20f),
          0.42f to Color.Transparent,
          1.0f to Color.Transparent
        ),
        start = Offset(center.x - vinylRadius * 0.7f, center.y - vinylRadius * 0.7f),
        end = Offset(center.x + vinylRadius * 0.7f, center.y + vinylRadius * 0.7f)
      ),
      radius = vinylRadius,
      center = center
    )
    drawCircle(
      color = Color(0xFF0B0B0B),
      radius = vinylRadius - 1.dp.toPx(),
      center = center,
      style = Stroke(width = 1.4.dp.toPx())
    )
  }
}
