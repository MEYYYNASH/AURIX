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
import com.example.widget.NowPlayingStudioSize
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
  var studioSize by remember { mutableStateOf(NowPlayingStudioSize.MEDIUM) }
  var showFullDesignMatrix by remember { mutableStateOf(false) }
  var lastToast by remember { mutableStateOf<Toast?>(null) }

  LaunchedEffect(Unit) {
    val (classic, savedSnap) = withContext(Dispatchers.IO) {
      val c = WidgetStyleStore.getLastChosenStyleForKind(context, WidgetKind.MUSIC)
      val s = AurixWidgetController.readSnapshot(context)
      c to s
    }
    selectedClassicStyle = classic
    studioSize = NowPlayingStudioSize.fromId(savedSnap.nowPlayingStudioSize)
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
    ?: "Midnight Drive"
  val displayArtist = currentSongArtist?.takeIf { it.isNotBlank() }
    ?: snapshot?.artist?.takeIf { it.isNotBlank() }
    ?: "Nova Lane"
  val displayArtworkUrl = currentSongArtworkUrl?.takeIf { it.isNotBlank() }
    ?: snapshot?.artworkUrl.orEmpty()
  val displayPrevLyric = "Streetlights bend across the glass"
  val displayCurrentLyric = snapshot?.currentLyricLine?.takeIf { snapshot?.hasLyrics == true && it.isNotBlank() }
    ?: "Keep the engine low and slow"
  val displayNextLyric = snapshot?.nextLyricLine?.takeIf { snapshot?.hasLyrics == true && it.isNotBlank() }
    ?: "Every mile a little closer"
  val isLiked = snapshot?.isFavorite == true

  if (showFullDesignMatrix) {
    androidx.compose.ui.window.Dialog(
      onDismissRequest = { showFullDesignMatrix = false },
      properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
      Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF17171A)
      ) {
        Column(modifier = Modifier.fillMaxSize()) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = "AURIX Now Playing · 5 Sizes × 10 Styles",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = Color.White
              )
              Text(
                text = "Small 2×2 · Compact 4×1 · Medium 4×2 · Lyrics 4×2 · Large 4×4",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF9CA3AF)
              )
            }
            Surface(
              onClick = { showFullDesignMatrix = false },
              shape = CircleShape,
              color = Color.White.copy(alpha = 0.12f),
              modifier = Modifier.defaultMinSize(minHeight = 40.dp)
            ) {
              Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                Text(
                  text = if (isKhmer) "បិទ" else "Close",
                  style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                  color = Color.White
                )
              }
            }
          }
          androidx.compose.ui.viewinterop.AndroidView(
            modifier = Modifier
              .fillMaxWidth()
              .weight(1f),
            factory = { ctx ->
              android.webkit.WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = false
                settings.allowFileAccess = true
                setBackgroundColor(android.graphics.Color.parseColor("#17171A"))
                loadUrl("file:///android_asset/aurix_now_playing_widgets.html")
              }
            }
          )
        }
      }
    }
  }

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

      // AURIX Now Playing (Resizable 5 sizes x 10 styles) Header + Full Matrix Button
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
          Text(
            text = stringResource(id = R.string.settings_widgets_np_title),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, lineHeight = 24.sp),
            color = MaterialTheme.colorScheme.onSurface
          )
          Text(
            text = "5 Sizes (2×2, 4×1, 4×2, Lyrics, 4×4) × 10 Distinct Layouts",
            style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Surface(
          onClick = { showFullDesignMatrix = true },
          shape = RoundedCornerShape(14.dp),
          color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
          modifier = Modifier
            .defaultMinSize(minHeight = 44.dp)
            .testTag("settings_np_open_5x10_matrix")
        ) {
          Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(
              text = if (isKhmer) "មើល 5×10 ទាំងអស់" else "5×10 Gallery",
              style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
              color = MaterialTheme.colorScheme.primary
            )
          }
        }
      }

      // 5-Size Horizontal Selector (Small 2×2 / Compact 4×1 / Medium 4×2 / Lyrics 4×2 / Large 4×4)
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        NowPlayingStudioSize.entries.forEach { sizeOption ->
          val selected = sizeOption == studioSize
          val sizeLabel = if (isKhmer) sizeOption.labelKm else sizeOption.labelEn
          SettingsWidgetChip(
            label = "$sizeLabel (${sizeOption.widthDp}×${sizeOption.heightDp})",
            selected = selected,
            darkTheme = darkTheme,
            modifier = Modifier.testTag("settings_np_size_${sizeOption.id}"),
            onClick = {
              studioSize = sizeOption
              AurixWidgetController.applyNowPlayingStudioToHomeScreen(
                context = context,
                style = nowPlayingWidgetStyle,
                studioSize = sizeOption,
                pinIfNoWidgetsPlaced = false
              )
            }
          )
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
        NowPlayingStudioLivePreview(
          style = nowPlayingWidgetStyle,
          studioSize = studioSize,
          matchSystemTheme = widgetMatchSystemTheme,
          showLikeButton = widgetShowLikeButton,
          showLyrics = widgetShowLyrics,
          reducedMotion = reducedMotion,
          title = displayTitle,
          artist = displayArtist,
          artworkUrl = displayArtworkUrl,
          rawArtwork = previewBitmap,
          prevLyric = displayPrevLyric,
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
            onClick = {
              onSelectNowPlayingWidgetStyle(npStyle)
              AurixWidgetController.applyNowPlayingStudioToHomeScreen(
                context = context,
                style = npStyle,
                studioSize = studioSize,
                pinIfNoWidgetsPlaced = false
              )
            }
          )
        }
      }

      // Apply to Home Screen & Pin Widget to Home Screen Action Row
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Surface(
          onClick = {
            val styleLabel = if (isKhmer) nowPlayingWidgetStyle.displayNameKm else nowPlayingWidgetStyle.displayNameEn
            val sizeLabel = if (isKhmer) studioSize.labelKm else studioSize.labelEn
            val updatedCount = AurixWidgetController.applyNowPlayingStudioToHomeScreen(
              context = context,
              style = nowPlayingWidgetStyle,
              studioSize = studioSize,
              pinIfNoWidgetsPlaced = true
            )
            lastToast?.cancel()
            val msg = if (updatedCount > 0) {
              if (isKhmer) {
                "បានអនុវត្ត $styleLabel ($sizeLabel) ទៅលើ Widget លើអេក្រង់ដើមទាំងអស់"
              } else {
                "Applied $styleLabel ($sizeLabel) to $updatedCount Home Screen widget(s)"
              }
            } else {
              if (isKhmer) {
                "បានរក្សាទុក $styleLabel ($sizeLabel) · សូមបន្ថែម Widget ទៅអេក្រង់ដើម"
              } else {
                "Saved $styleLabel ($sizeLabel) · Pinning widget to Home Screen"
              }
            }
            lastToast = Toast.makeText(context, msg, Toast.LENGTH_SHORT).also { it.show() }
          },
          shape = RoundedCornerShape(16.dp),
          color = MaterialTheme.colorScheme.primary,
          modifier = Modifier
            .weight(1f)
            .defaultMinSize(minHeight = 48.dp)
            .testTag("settings_np_apply_home_btn")
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Icon(
              imageVector = Icons.Rounded.Check,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.onPrimary,
              modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = if (isKhmer) "អនុវត្តទៅអេក្រង់ដើម" else "Apply to Home Screen",
              style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
              color = MaterialTheme.colorScheme.onPrimary,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
          }
        }

        Surface(
          onClick = {
            val pinned = AurixWidgetController.requestPinNowPlayingWidget(
              context = context,
              style = nowPlayingWidgetStyle,
              studioSize = studioSize
            )
            lastToast?.cancel()
            val msg = if (pinned) {
              if (isKhmer) "កំពុងបន្ថែម AURIX Now Playing ទៅអេក្រង់ដើម..." else "Adding AURIX Now Playing to Home Screen..."
            } else {
              if (isKhmer) {
                "សូមសង្កត់លើអេក្រង់ដើម → Widgets → ជ្រើសរើស AURIX Now Playing"
              } else {
                "Long-press your Home Screen → Widgets → Drag AURIX Now Playing"
              }
            }
            lastToast = Toast.makeText(context, msg, Toast.LENGTH_LONG).also { it.show() }
          },
          shape = RoundedCornerShape(16.dp),
          color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
          modifier = Modifier
            .defaultMinSize(minHeight = 48.dp)
            .testTag("settings_np_pin_home_btn")
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Icon(
              imageVector = Icons.Rounded.Widgets,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = if (isKhmer) "+ បន្ថែម Widget" else "+ Add Widget",
              style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
              color = MaterialTheme.colorScheme.primary,
              maxLines = 1
            )
          }
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
private fun NowPlayingStudioLivePreview(
  style: NowPlayingWidgetStyle,
  studioSize: NowPlayingStudioSize,
  matchSystemTheme: Boolean,
  showLikeButton: Boolean,
  showLyrics: Boolean,
  reducedMotion: Boolean,
  title: String,
  artist: String,
  artworkUrl: String,
  rawArtwork: Bitmap?,
  prevLyric: String,
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
    NowPlayingWidgetStyle.PIXEL -> 4.dp
    NowPlayingWidgetStyle.TYPE -> 16.dp
    NowPlayingWidgetStyle.PAPER -> 18.dp
    NowPlayingWidgetStyle.FLOAT -> 20.dp
    NowPlayingWidgetStyle.LIGHT, NowPlayingWidgetStyle.VINYL -> 22.dp
    else -> 24.dp
  }
  val effectiveFontFamily = when {
    style == NowPlayingWidgetStyle.PIXEL -> FontFamily.Monospace
    style == NowPlayingWidgetStyle.PAPER && !KhmerTypography.containsKhmer(title) -> PaperSerifFamily
    else -> AurixFontFamilies.AurixUiFamily
  }
  val progressFraction = if (durationMs > 0L) {
    (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0.12f, 1f)
  } else {
    0.62f
  }
  val effectiveSize = if (studioSize == NowPlayingStudioSize.LYRICS && !showLyrics) {
    NowPlayingStudioSize.MEDIUM
  } else {
    studioSize
  }
  val cardWidth = effectiveSize.widthDp.dp
  val cardHeight = effectiveSize.heightDp.dp

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

  val cardBgColor = when (style) {
    NowPlayingWidgetStyle.VINYL -> Color(0xFFEAB32E)
    NowPlayingWidgetStyle.RING -> Color(0xFF13131A)
    NowPlayingWidgetStyle.PIXEL -> Color(0xFF1C202B)
    NowPlayingWidgetStyle.TYPE -> Color(0xFF111215)
    NowPlayingWidgetStyle.FLOAT -> Color(0xFF090812)
    else -> Color(tokens.cardBg)
  }
  val primaryText = when (style) {
    NowPlayingWidgetStyle.VINYL -> Color(0xFF161618)
    NowPlayingWidgetStyle.LIGHT -> Color(0xFF141418)
    NowPlayingWidgetStyle.PAPER -> Color(0xFF231F1C)
    else -> Color(tokens.textPrimary)
  }
  val secondaryText = when (style) {
    NowPlayingWidgetStyle.VINYL -> Color(0xFF4A380B)
    NowPlayingWidgetStyle.LIGHT -> Color(0xFF71717A)
    NowPlayingWidgetStyle.PAPER -> Color(0xFF7A7067)
    else -> Color(tokens.textSecondary)
  }
  val accentColor = when (style) {
    NowPlayingWidgetStyle.RING -> Color(0xFFF27D52)
    NowPlayingWidgetStyle.FROST -> Color.White
    NowPlayingWidgetStyle.LIGHT -> Color(0xFFE11D48)
    NowPlayingWidgetStyle.NIGHT -> Color(0xFF4CE0B3)
    NowPlayingWidgetStyle.VINYL -> Color(0xFF161618)
    NowPlayingWidgetStyle.PIXEL -> Color(0xFFF4BD6A)
    NowPlayingWidgetStyle.PAPER -> Color(0xFFA84A24)
    NowPlayingWidgetStyle.AURA -> Color(0xFF5EEAD4)
    NowPlayingWidgetStyle.TYPE -> Color(0xFFFF5A36)
    NowPlayingWidgetStyle.FLOAT -> Color(0xFF00F0FF)
  }
  val playBg = when (style) {
    NowPlayingWidgetStyle.RING -> Color(0xFFF27D52)
    NowPlayingWidgetStyle.FROST -> Color.White
    NowPlayingWidgetStyle.LIGHT -> Color(0xFFE11D48)
    NowPlayingWidgetStyle.NIGHT -> Color(0xFF4CE0B3)
    NowPlayingWidgetStyle.VINYL -> Color(0xFF161618)
    NowPlayingWidgetStyle.PIXEL -> Color(0xFFF4BD6A)
    NowPlayingWidgetStyle.PAPER -> Color.Transparent
    NowPlayingWidgetStyle.AURA -> Color(0xFF5EEAD4)
    NowPlayingWidgetStyle.TYPE -> Color(0xFFFF5A36)
    NowPlayingWidgetStyle.FLOAT -> Color(0x2600F0FF)
  }
  val playIcon = when (style) {
    NowPlayingWidgetStyle.LIGHT -> Color.White
    NowPlayingWidgetStyle.VINYL -> Color(0xFFEAB32E)
    NowPlayingWidgetStyle.PAPER -> Color(0xFF231F1C)
    NowPlayingWidgetStyle.FLOAT -> Color(0xFF00F0FF)
    else -> Color(0xFF13131A)
  }
  val borderColor = when (style) {
    NowPlayingWidgetStyle.FLOAT -> Color(0xFF00F0FF)
    NowPlayingWidgetStyle.PIXEL -> Color(0xFF353B4B)
    else -> Color(tokens.border)
  }
  val trackColor = when (style) {
    NowPlayingWidgetStyle.LIGHT -> Color(0xFFE4E4E7)
    NowPlayingWidgetStyle.VINYL -> Color(0x33161618)
    NowPlayingWidgetStyle.PIXEL -> Color(0xFF353B4B)
    NowPlayingWidgetStyle.PAPER -> Color(0x2E231F1C)
    else -> Color.White.copy(alpha = 0.20f)
  }

  Box(
    modifier = Modifier
      .width(cardWidth)
      .height(cardHeight)
      .clip(RoundedCornerShape(corner))
      .background(
        when (style) {
          NowPlayingWidgetStyle.FROST -> Brush.linearGradient(
            listOf(Color(0xFF5C1F42), Color(0xFF3E1C47), Color(0xFF241634))
          )
          NowPlayingWidgetStyle.NIGHT -> Brush.verticalGradient(
            listOf(Color(0xFF2CC4AA), Color(0xFF244278), Color(0xFF070910))
          )
          NowPlayingWidgetStyle.AURA -> Brush.linearGradient(
            listOf(Color(0xFF0B1D2C), Color(0xFF143D4B), Color(0xFF251E42))
          )
          else -> Brush.linearGradient(listOf(cardBgColor, cardBgColor))
        }
      )
      .border(if (style == NowPlayingWidgetStyle.PIXEL || style == NowPlayingWidgetStyle.FLOAT) 1.5.dp else 1.dp, borderColor, RoundedCornerShape(corner))
      .clipToBounds()
  ) {
    // Clipped Vinyl record on the right/top-right edge (matching reference sheet)
    if (style == NowPlayingWidgetStyle.VINYL) {
      val discSize = when (effectiveSize) {
        NowPlayingStudioSize.SMALL -> 108.dp
        NowPlayingStudioSize.COMPACT -> 92.dp
        NowPlayingStudioSize.MEDIUM, NowPlayingStudioSize.LYRICS -> 150.dp
        NowPlayingStudioSize.LARGE -> 230.dp
      }
      SettingsVinylDiscCanvas(
        diameter = discSize,
        palette = palette,
        rotationDegrees = vinylSpin,
        modifier = Modifier
          .align(Alignment.TopEnd)
          .offset(
            x = discSize * 0.24f,
            y = if (effectiveSize == NowPlayingStudioSize.COMPACT) (-10).dp else (-18).dp
          )
          .zIndex(0f)
      )
    }

    Column(modifier = Modifier.fillMaxSize().zIndex(1f)) {
      if (style == NowPlayingWidgetStyle.PIXEL) {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .height(17.dp)
            .background(Color(0xFFF4BD6A))
            .padding(horizontal = 8.dp),
          contentAlignment = Alignment.CenterStart
        ) {
          Text(
            text = "NOW PLAYING",
            color = Color(0xFF1C202B),
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 8.5.sp,
            letterSpacing = 1.1.sp
          )
        }
      }

      when (effectiveSize) {
        NowPlayingStudioSize.SMALL -> {
          Column(
            modifier = Modifier
              .fillMaxSize()
              .padding(11.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = if (style == NowPlayingWidgetStyle.RING) Alignment.CenterHorizontally else Alignment.Start
          ) {
            if (style == NowPlayingWidgetStyle.RING) {
              Box(contentAlignment = Alignment.Center) {
                PreviewArtworkSlot(
                  style = style,
                  size = 82.dp,
                  artworkUrl = artworkUrl,
                  rawArtwork = rawArtwork,
                  progressFraction = progressFraction,
                  accentColor = accentColor,
                  trackColor = trackColor
                )
                StudioPlayButton(style = style, isPlaying = isPlaying, playBg = playBg, playIcon = playIcon, size = 32.dp)
              }
            } else {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
              ) {
                StudioPlayButton(style = style, isPlaying = isPlaying, playBg = playBg, playIcon = playIcon, size = 32.dp)
                if (style != NowPlayingWidgetStyle.VINYL && style != NowPlayingWidgetStyle.NIGHT && style != NowPlayingWidgetStyle.TYPE) {
                  PreviewArtworkSlot(
                    style = style,
                    size = 56.dp,
                    artworkUrl = artworkUrl,
                    rawArtwork = rawArtwork,
                    progressFraction = progressFraction,
                    accentColor = accentColor,
                    trackColor = trackColor
                  )
                }
              }
            }

            Column(
              modifier = Modifier.fillMaxWidth(),
              horizontalAlignment = if (style == NowPlayingWidgetStyle.RING) Alignment.CenterHorizontally else Alignment.Start
            ) {
              Text(
                text = title,
                color = primaryText,
                fontFamily = effectiveFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 11.2.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
              Text(
                text = artist,
                color = secondaryText,
                fontFamily = effectiveFontFamily,
                fontSize = 9.4.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
              if (style != NowPlayingWidgetStyle.RING) {
                Spacer(modifier = Modifier.height(5.dp))
                PreviewProgressBar(
                  style = style,
                  fraction = progressFraction,
                  fillColor = accentColor,
                  trackColor = trackColor
                )
              }
            }
          }
        }

        NowPlayingStudioSize.COMPACT -> {
          Row(
            modifier = Modifier
              .fillMaxSize()
              .padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            if (style != NowPlayingWidgetStyle.VINYL && style != NowPlayingWidgetStyle.TYPE && style != NowPlayingWidgetStyle.NIGHT) {
              PreviewArtworkSlot(
                style = style,
                size = 42.dp,
                artworkUrl = artworkUrl,
                rawArtwork = rawArtwork,
                progressFraction = progressFraction,
                accentColor = accentColor,
                trackColor = trackColor
              )
            }
            Column(
              modifier = Modifier
                .weight(1f)
                .padding(end = if (style == NowPlayingWidgetStyle.VINYL) 48.dp else 0.dp)
            ) {
              Text(
                text = title,
                color = primaryText,
                fontFamily = effectiveFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 11.2.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
              Text(
                text = artist,
                color = secondaryText,
                fontFamily = effectiveFontFamily,
                fontSize = 9.4.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
              if (style != NowPlayingWidgetStyle.RING) {
                Spacer(modifier = Modifier.height(4.dp))
                PreviewProgressBar(
                  style = style,
                  fraction = progressFraction,
                  fillColor = accentColor,
                  trackColor = trackColor
                )
              }
            }
            StudioPlayButton(style = style, isPlaying = isPlaying, playBg = playBg, playIcon = playIcon, size = 32.dp)
          }
        }

        NowPlayingStudioSize.MEDIUM -> {
          if (style == NowPlayingWidgetStyle.RING) {
            Row(
              modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
              PreviewArtworkSlot(
                style = style,
                size = 104.dp,
                artworkUrl = artworkUrl,
                rawArtwork = rawArtwork,
                progressFraction = progressFraction,
                accentColor = accentColor,
                trackColor = trackColor
              )
              Column(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceBetween
              ) {
                Column {
                  Text(
                    text = title,
                    color = primaryText,
                    fontFamily = effectiveFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.8.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                  )
                  Text(
                    text = artist,
                    color = secondaryText,
                    fontFamily = effectiveFontFamily,
                    fontSize = 10.6.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                  )
                }
                StudioTransportRow(
                  style = style,
                  isPlaying = isPlaying,
                  primaryText = primaryText,
                  playBg = playBg,
                  playIcon = playIcon
                )
              }
            }
          } else {
            Column(
              modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 11.dp),
              verticalArrangement = Arrangement.SpaceBetween
            ) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
              ) {
                val artOnLeft = style == NowPlayingWidgetStyle.LIGHT || style == NowPlayingWidgetStyle.PIXEL || style == NowPlayingWidgetStyle.PAPER
                if (artOnLeft) {
                  PreviewArtworkSlot(
                    style = style,
                    size = 64.dp,
                    artworkUrl = artworkUrl,
                    rawArtwork = rawArtwork,
                    progressFraction = progressFraction,
                    accentColor = accentColor,
                    trackColor = trackColor
                  )
                }
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = title,
                    color = primaryText,
                    fontFamily = effectiveFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = if (style == NowPlayingWidgetStyle.TYPE) 16.5.sp else 13.8.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                  )
                  Text(
                    text = artist,
                    color = secondaryText,
                    fontFamily = effectiveFontFamily,
                    fontSize = 10.6.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                  )
                }
                if (!artOnLeft && style != NowPlayingWidgetStyle.VINYL && style != NowPlayingWidgetStyle.NIGHT && style != NowPlayingWidgetStyle.TYPE) {
                  PreviewArtworkSlot(
                    style = style,
                    size = 64.dp,
                    artworkUrl = artworkUrl,
                    rawArtwork = rawArtwork,
                    progressFraction = progressFraction,
                    accentColor = accentColor,
                    trackColor = trackColor
                  )
                }
              }

              PreviewProgressBar(
                style = style,
                fraction = progressFraction,
                fillColor = accentColor,
                trackColor = trackColor
              )

              StudioTransportRow(
                style = style,
                isPlaying = isPlaying,
                primaryText = primaryText,
                playBg = playBg,
                playIcon = playIcon
              )
            }
          }
        }

        NowPlayingStudioSize.LYRICS -> {
          Column(
            modifier = Modifier
              .fillMaxSize()
              .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.SpaceBetween
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text(
                text = "$title · $artist",
                color = secondaryText,
                fontFamily = effectiveFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 9.8.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(end = 8.dp)
              )
              StudioPlayButton(style = style, isPlaying = isPlaying, playBg = playBg, playIcon = playIcon, size = 25.dp)
            }

            Column(
              modifier = Modifier.fillMaxWidth(),
              verticalArrangement = Arrangement.spacedBy(3.dp),
              horizontalAlignment = if (style == NowPlayingWidgetStyle.RING) Alignment.CenterHorizontally else Alignment.Start
            ) {
              Text(
                text = prevLyric,
                color = secondaryText.copy(alpha = 0.55f),
                fontFamily = effectiveFontFamily,
                fontSize = 10.2.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
              when (style) {
                NowPlayingWidgetStyle.VINYL -> {
                  Box(
                    modifier = Modifier
                      .clip(RoundedCornerShape(6.dp))
                      .background(Color(0xFF161618))
                      .padding(horizontal = 8.dp, vertical = 2.dp)
                  ) {
                    Text(
                      text = currentLyric,
                      color = Color(0xFFEAB32E),
                      fontFamily = effectiveFontFamily,
                      fontWeight = FontWeight.Bold,
                      fontSize = 11.4.sp,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis
                    )
                  }
                }
                NowPlayingWidgetStyle.LIGHT -> {
                  Box(
                    modifier = Modifier
                      .background(Color(0xFFFECDD3), RoundedCornerShape(4.dp))
                      .padding(horizontal = 6.dp, vertical = 1.dp)
                  ) {
                    Text(
                      text = currentLyric,
                      color = Color(0xFF141418),
                      fontFamily = effectiveFontFamily,
                      fontWeight = FontWeight.Bold,
                      fontSize = 11.6.sp,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis
                    )
                  }
                }
                else -> {
                  val displayCurr = if (style == NowPlayingWidgetStyle.PIXEL) "> $currentLyric" else currentLyric
                  val currColor = when (style) {
                    NowPlayingWidgetStyle.RING,
                    NowPlayingWidgetStyle.NIGHT,
                    NowPlayingWidgetStyle.PIXEL,
                    NowPlayingWidgetStyle.PAPER,
                    NowPlayingWidgetStyle.AURA,
                    NowPlayingWidgetStyle.FLOAT -> accentColor
                    else -> primaryText
                  }
                  Text(
                    text = displayCurr,
                    color = currColor,
                    fontFamily = effectiveFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.8.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                  )
                }
              }
              Text(
                text = nextLyric,
                color = secondaryText.copy(alpha = 0.55f),
                fontFamily = effectiveFontFamily,
                fontSize = 10.2.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
            }

            if (style != NowPlayingWidgetStyle.RING) {
              PreviewProgressBar(
                style = style,
                fraction = progressFraction,
                fillColor = accentColor,
                trackColor = trackColor
              )
            } else {
              Spacer(modifier = Modifier.height(2.dp))
            }
          }
        }

        NowPlayingStudioSize.LARGE -> {
          Column(
            modifier = Modifier
              .fillMaxSize()
              .padding(18.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
          ) {
            if (style != NowPlayingWidgetStyle.VINYL && style != NowPlayingWidgetStyle.NIGHT && style != NowPlayingWidgetStyle.TYPE) {
              PreviewArtworkSlot(
                style = style,
                size = 166.dp,
                artworkUrl = artworkUrl,
                rawArtwork = rawArtwork,
                progressFraction = progressFraction,
                accentColor = accentColor,
                trackColor = trackColor
              )
            } else {
              Spacer(modifier = Modifier.height(110.dp))
            }

            Column(
              modifier = Modifier.fillMaxWidth(),
              horizontalAlignment = if (style == NowPlayingWidgetStyle.RING) Alignment.CenterHorizontally else Alignment.Start
            ) {
              Text(
                text = title,
                color = primaryText,
                fontFamily = effectiveFontFamily,
                fontWeight = FontWeight.ExtraBold,
                fontSize = if (style == NowPlayingWidgetStyle.TYPE) 22.sp else 16.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
              Text(
                text = artist,
                color = secondaryText,
                fontFamily = effectiveFontFamily,
                fontSize = 11.8.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
            }

            if (style != NowPlayingWidgetStyle.RING) {
              PreviewProgressBar(
                style = style,
                fraction = progressFraction,
                fillColor = accentColor,
                trackColor = trackColor
              )
            }

            StudioTransportRow(
              style = style,
              isPlaying = isPlaying,
              primaryText = primaryText,
              playBg = playBg,
              playIcon = playIcon
            )
          }
        }
      }
    }
  }
}

@Composable
private fun StudioPlayButton(
  style: NowPlayingWidgetStyle,
  isPlaying: Boolean,
  playBg: Color,
  playIcon: Color,
  size: Dp
) {
  val shape = if (style == NowPlayingWidgetStyle.PIXEL) RoundedCornerShape(0.dp) else CircleShape
  Box(
    modifier = Modifier
      .size(size)
      .clip(shape)
      .background(playBg)
      .then(
        if (style == NowPlayingWidgetStyle.PAPER) {
          Modifier.border(1.5.dp, Color(0xFF231F1C), shape)
        } else if (style == NowPlayingWidgetStyle.FLOAT) {
          Modifier.border(1.5.dp, Color(0xFF00F0FF), shape)
        } else {
          Modifier
        }
      ),
    contentAlignment = Alignment.Center
  ) {
    Icon(
      imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
      contentDescription = null,
      tint = playIcon,
      modifier = Modifier.size(size * 0.52f)
    )
  }
}

@Composable
private fun StudioTransportRow(
  style: NowPlayingWidgetStyle,
  isPlaying: Boolean,
  primaryText: Color,
  playBg: Color,
  playIcon: Color
) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(14.dp)
  ) {
    Icon(Icons.Rounded.SkipPrevious, contentDescription = null, tint = primaryText, modifier = Modifier.size(20.dp))
    StudioPlayButton(style = style, isPlaying = isPlaying, playBg = playBg, playIcon = playIcon, size = 36.dp)
    Icon(Icons.Rounded.SkipNext, contentDescription = null, tint = primaryText, modifier = Modifier.size(20.dp))
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
