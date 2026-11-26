package com.example.ui.screens

import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.AlbumBannerCache
import com.example.data.CachedBannerVisual
import com.example.model.APP_ICON_ARTWORK
import com.example.model.AlbumInfo
import com.example.model.Song
import com.example.ui.theme.AlbumPaletteThemeEngine
import com.example.ui.theme.AurixFontFamilies
import com.example.ui.theme.AurixTheme
import java.util.Locale

object AlbumDesignTokens {
  val Background = Color(0xFF0A0D16)
  val Surface = Color(0xFF15192A)
  val RaisedSurface = Color(0xFF1E243B)
  val GlassFill = Color(0xFF1A1F33).copy(alpha = 0.62f)
  val Text = Color(0xFFF6F7FF)
  val DimText = Color(0xFFA0A7C0)
  val Accent = Color(0xFFB9A2FF)
  val Border = Color.White.copy(alpha = 0.14f)
  val TopHighlight = Color.White.copy(alpha = 0.24f)
  val SidePadding = 20.dp
  val CoverGradientStart = Color(0xFF7B61FF)
  val CoverGradientEnd = Color(0xFF2D1E69)
  val OutfitFontFamily = FontFamily(Font(R.font.outfit))
}

internal data class AlbumResolvedPalette(
  val accent: Color,
  val secondaryAccent: Color,
  val deepBackground: Color,
  val surfaceGlass: Color,
  val playButtonInk: Color
)

internal fun resolveAlbumVisualPalette(
  extractedSeed: Color?,
  fallbackPrimary: Color
): AlbumResolvedPalette {
  val seed = extractedSeed ?: fallbackPrimary
  val hsl = AlbumPaletteThemeEngine.colorToHsl(seed)
  val isNeutral = hsl[1] < 0.08f
  val deepBg = if (isNeutral) {
    AlbumDesignTokens.Background
  } else {
    AlbumPaletteThemeEngine.hslToColor(
      hue = hsl[0],
      sat = (hsl[1] * 0.42f).coerceIn(0.10f, 0.30f),
      light = 0.065f
    )
  }
  var accent = if (isNeutral) {
    Color(0xFFD8DEF2)
  } else {
    AlbumPaletteThemeEngine.hslToColor(
      hue = hsl[0],
      sat = hsl[1].coerceIn(0.46f, 0.84f),
      light = hsl[2].coerceIn(0.68f, 0.78f)
    )
  }
  if (!isNeutral && AlbumPaletteThemeEngine.contrastRatio(accent, deepBg) < 4.6f) {
    var l = 0.72f
    val sat = hsl[1].coerceIn(0.44f, 0.82f)
    while (l <= 0.94f && AlbumPaletteThemeEngine.contrastRatio(accent, deepBg) < 4.6f) {
      accent = AlbumPaletteThemeEngine.hslToColor(hue = hsl[0], sat = sat, light = l)
      l += 0.03f
    }
  }
  val secondary = if (isNeutral) {
    Color(0xFFA8B5D8)
  } else {
    AlbumPaletteThemeEngine.hslToColor(
      hue = (hsl[0] + 38f) % 360f,
      sat = (hsl[1] * 0.85f).coerceIn(0.36f, 0.78f),
      light = hsl[2].coerceIn(0.56f, 0.72f)
    )
  }
  val glassFill = AlbumPaletteThemeEngine.hslToColor(
    hue = hsl[0],
    sat = if (isNeutral) 0.10f else (hsl[1] * 0.34f).coerceIn(0.10f, 0.28f),
    light = 0.135f
  ).copy(alpha = 0.72f)
  val playInk = AlbumPaletteThemeEngine.pickContrastInk(accent)
  return AlbumResolvedPalette(
    accent = accent,
    secondaryAccent = secondary,
    deepBackground = deepBg,
    surfaceGlass = glassFill,
    playButtonInk = playInk
  )
}

@Composable
private fun rememberEffectiveReducedMotion(reducedMotion: Boolean): Boolean {
  val context = LocalContext.current
  val isInspection = LocalInspectionMode.current
  val systemAnimatorScale = remember(context, isInspection) {
    if (isInspection) 1f
    else runCatching {
      Settings.Global.getFloat(
        context.contentResolver,
        Settings.Global.ANIMATOR_DURATION_SCALE,
        1f
      )
    }.getOrDefault(1f)
  }
  return reducedMotion || systemAnimatorScale <= 0f
}

@Composable
fun AlbumTrackListSection(
  albumTitle: String = stringResource(id = R.string.album_placeholder_title),
  artistName: String = stringResource(id = R.string.album_placeholder_artist),
  albumYear: String = "",
  coverUrl: String? = null,
  albumInfo: AlbumInfo? = null,
  tracks: List<Song>,
  currentSong: Song?,
  isPlaying: Boolean,
  isShuffleEnabled: Boolean = false,
  isAddedToLibrary: Boolean = false,
  isAlbumDownloaded: Boolean = false,
  hasCurrentSong: Boolean = currentSong != null,
  reducedMotion: Boolean = false,
  onAddToLibraryClick: () -> Unit = {},
  onDownloadAlbumClick: () -> Unit = {},
  onPlayClick: () -> Unit = {},
  onShuffleClick: () -> Unit = {},
  onTrackClick: (Song) -> Unit = {},
  onTrackFavoriteClick: (Song) -> Unit = {},
  onTrackDownloadClick: (Song) -> Unit = {},
  onTrackDetailsClick: (Song) -> Unit = {},
  onImportAudioFiles: (List<Uri>) -> Unit = {},
  availableSongsToAdd: List<Song> = emptyList(),
  onAddSongsToAlbum: (List<Song>) -> Unit = {},
  onRemoveTrackFromAlbum: ((Song) -> Unit)? = null,
  onDeleteAlbum: ((alsoDeleteLocalSongs: Boolean) -> Unit)? = null,
  onSaveCoverPhoto: (imageUri: String, offsetXFraction: Float, offsetYFraction: Float, zoom: Float) -> Unit = { _, _, _, _ -> },
  onSaveCoverMix: (imageSources: List<String>) -> Unit = {},
  onSaveCoverStudio: (
    title: String,
    subtitle: String,
    paletteId: String,
    layoutId: String,
    backdropSource: String?,
    blurAmount: Float
  ) -> Unit = { _, _, _, _, _, _ -> },
  onSaveCoverAppIcon: () -> Unit = {},
  onResetCover: () -> Unit = {},
  onBackClick: (() -> Unit)? = null,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val effectiveReducedMotion = rememberEffectiveReducedMotion(reducedMotion)
  val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
  val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
  val dockClearanceBottom = (if (hasCurrentSong) 205.dp else 135.dp) + navBarBottom

  val resolvedTitle = albumInfo?.title ?: albumTitle
  val resolvedArtist = albumInfo?.artist ?: artistName
  val resolvedYear = albumInfo?.year ?: albumYear
  val resolvedCover = albumInfo?.coverUrl ?: coverUrl

  val effectiveAlbumInfo = remember(albumInfo, resolvedTitle, resolvedArtist, resolvedYear, resolvedCover, tracks) {
    albumInfo ?: AlbumInfo(
      albumId = "default_album",
      title = resolvedTitle,
      artist = resolvedArtist,
      year = resolvedYear,
      coverUrl = resolvedCover,
      automaticCoverUrl = resolvedCover,
      tracks = tracks
    )
  }

  val isThisAlbumActive = remember(currentSong?.id, tracks) {
    currentSong != null && tracks.any { it.id == currentSong.id }
  }
  val isAlbumPlayingNow = isThisAlbumActive && isPlaying

  val activeBannerArtworkUrl = remember(resolvedCover, currentSong?.artworkUrl, isThisAlbumActive, tracks) {
    resolvedCover?.trim()?.takeIf { it.isNotEmpty() }
      ?: currentSong?.takeIf { isThisAlbumActive }?.artworkUrl?.trim()?.takeIf { it.isNotEmpty() }
      ?: tracks.firstOrNull { it.artworkUrl.isNotBlank() }?.artworkUrl?.trim()
      ?: ""
  }

  var cachedBanner by remember {
    mutableStateOf(
      if (activeBannerArtworkUrl.isNotBlank()) AlbumBannerCache.get(activeBannerArtworkUrl) else null
    )
  }
  LaunchedEffect(activeBannerArtworkUrl) {
    if (activeBannerArtworkUrl.isBlank()) {
      cachedBanner = null
    } else {
      val syncHit = AlbumBannerCache.get(activeBannerArtworkUrl)
      if (syncHit != null) {
        cachedBanner = syncHit
      } else {
        val loaded = AlbumBannerCache.getOrCreate(context, activeBannerArtworkUrl)
        cachedBanner = loaded
      }
    }
  }

  val fallbackPrimary = MaterialTheme.colorScheme.primary
  val targetPalette = remember(cachedBanner?.dominantColor, fallbackPrimary) {
    resolveAlbumVisualPalette(
      extractedSeed = cachedBanner?.dominantColor,
      fallbackPrimary = fallbackPrimary
    )
  }
  val colorAnimSpec = remember(effectiveReducedMotion) {
    if (effectiveReducedMotion) snap<Color>() else tween<Color>(durationMillis = 480, easing = FastOutSlowInEasing)
  }
  val dominantAccent by animateColorAsState(targetPalette.accent, colorAnimSpec, label = "album_dominant_accent")
  val secondaryAccent by animateColorAsState(targetPalette.secondaryAccent, colorAnimSpec, label = "album_secondary_accent")
  val deepBackground by animateColorAsState(targetPalette.deepBackground, colorAnimSpec, label = "album_deep_bg")
  val surfaceGlass by animateColorAsState(targetPalette.surfaceGlass, colorAnimSpec, label = "album_surface_glass")
  val playButtonInk by animateColorAsState(targetPalette.playButtonInk, colorAnimSpec, label = "album_play_ink")

  val listState = rememberLazyListState()
  val bannerHeightDp = 390.dp + statusTop
  val density = LocalDensity.current
  val bannerHeightPx = with(density) { bannerHeightDp.toPx() }
  val scrollOffsetPx by remember(bannerHeightPx) {
    derivedStateOf {
      if (listState.firstVisibleItemIndex == 0) {
        listState.firstVisibleItemScrollOffset.toFloat()
      } else {
        bannerHeightPx
      }
    }
  }
  val collapseFraction by remember(bannerHeightPx) {
    derivedStateOf {
      val triggerPx = (bannerHeightPx * 0.56f).coerceAtLeast(1f)
      (scrollOffsetPx / triggerPx).coerceIn(0f, 1f)
    }
  }

  val totalDurationSeconds = remember(tracks) {
    tracks.sumOf { (it.fullDurationSeconds.takeIf { d -> d > 0L } ?: it.durationSeconds).coerceAtLeast(0L) }
  }

  var showAlbumMenu by remember { mutableStateOf(false) }
  var showEditCoverSheet by remember { mutableStateOf(false) }
  var showAddSongsSheet by remember { mutableStateOf(false) }
  var showDeleteAlbumDialog by remember { mutableStateOf(false) }

  val importAudioLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenMultipleDocuments()
  ) { uris ->
    if (uris.isNotEmpty()) {
      onImportAudioFiles(uris)
    }
  }

  Box(
    modifier = modifier
      .fillMaxSize()
      .background(deepBackground)
  ) {
    // Full-bleed parallax blurred cover banner + breathing aurora wash
    AlbumBanner(
      bannerKey = activeBannerArtworkUrl,
      cachedVisual = cachedBanner,
      accentColor = dominantAccent,
      secondaryAccentColor = secondaryAccent,
      backgroundColor = deepBackground,
      isAlbumPlaying = isAlbumPlayingNow,
      scrollOffsetPx = scrollOffsetPx,
      bannerHeightPx = bannerHeightPx,
      reducedMotion = effectiveReducedMotion,
      modifier = Modifier
        .fillMaxWidth()
        .height(bannerHeightDp)
        .align(Alignment.TopCenter)
        .testTag("album_banner")
    )

    LazyColumn(
      state = listState,
      modifier = Modifier
        .fillMaxSize()
        .padding(horizontal = AlbumDesignTokens.SidePadding),
      horizontalAlignment = Alignment.CenterHorizontally,
      contentPadding = PaddingValues(
        top = statusTop + if (onBackClick != null) 54.dp else 18.dp,
        bottom = dockClearanceBottom
      )
    ) {
      item(key = "album_header") {
        AlbumHeader(
          albumTitle = resolvedTitle,
          artistName = resolvedArtist,
          albumYear = resolvedYear,
          trackCount = tracks.size,
          totalDurationSeconds = totalDurationSeconds,
          coverUrl = resolvedCover,
          isAddedToLibrary = isAddedToLibrary,
          isAlbumDownloaded = isAlbumDownloaded,
          isShuffleEnabled = isShuffleEnabled,
          isAlbumPlaying = isAlbumPlayingNow,
          accentColor = dominantAccent,
          secondaryAccentColor = secondaryAccent,
          glassColor = surfaceGlass,
          showAlbumMenu = showAlbumMenu,
          canResetCover = effectiveAlbumInfo.customCoverMode != null,
          reducedMotion = effectiveReducedMotion,
          onAddToLibraryClick = onAddToLibraryClick,
          onDownloadClick = onDownloadAlbumClick,
          onAddSongsClick = {
            showAlbumMenu = false
            showAddSongsSheet = true
          },
          onMoreClick = { showAlbumMenu = true },
          onDismissMenu = { showAlbumMenu = false },
          onEditCoverClick = {
            showAlbumMenu = false
            showEditCoverSheet = true
          },
          onResetCoverClick = {
            showAlbumMenu = false
            onResetCover()
          },
          onImportAudioClick = {
            showAlbumMenu = false
            importAudioLauncher.launch(arrayOf("audio/*"))
          },
          onDeleteAlbumClick = if (onDeleteAlbum != null) {
            {
              showAlbumMenu = false
              showDeleteAlbumDialog = true
            }
          } else {
            null
          },
          onPlayClick = {
            showAlbumMenu = false
            onPlayClick()
          },
          onShuffleClick = {
            showAlbumMenu = false
            onShuffleClick()
          },
          modifier = Modifier.widthIn(max = 760.dp)
        )
        Spacer(modifier = Modifier.height(18.dp))
      }

      item(key = "album_play_shuffle") {
        PlayShuffleRow(
          isShuffleEnabled = isShuffleEnabled,
          enabled = tracks.isNotEmpty(),
          accentColor = dominantAccent,
          secondaryAccentColor = secondaryAccent,
          playContentInk = playButtonInk,
          glassColor = surfaceGlass,
          reducedMotion = effectiveReducedMotion,
          onPlayClick = onPlayClick,
          onShuffleClick = onShuffleClick,
          modifier = Modifier.widthIn(max = 760.dp)
        )
        Spacer(modifier = Modifier.height(20.dp))
      }

      if (tracks.isEmpty()) {
        item(key = "album_empty_tracks") {
          Column(
            modifier = Modifier
              .widthIn(max = 760.dp)
              .fillMaxWidth()
              .clip(RoundedCornerShape(24.dp))
              .background(surfaceGlass)
              .border(1.dp, AlbumDesignTokens.Border, RoundedCornerShape(24.dp))
              .padding(horizontal = 20.dp, vertical = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            Box(
              modifier = Modifier
                .size(54.dp)
                .clip(CircleShape)
                .background(dominantAccent.copy(alpha = 0.16f))
                .border(1.dp, dominantAccent.copy(alpha = 0.35f), CircleShape),
              contentAlignment = Alignment.Center
            ) {
              Icon(
                imageVector = Icons.Rounded.MusicNote,
                contentDescription = null,
                tint = dominantAccent,
                modifier = Modifier.size(28.dp)
              )
            }
            Text(
              text = stringResource(id = R.string.album_empty_tracks_title),
              style = MaterialTheme.typography.titleMedium.copy(
                fontFamily = AurixFontFamilies.AurixUiFamily,
                fontWeight = FontWeight.Bold
              ),
              color = AlbumDesignTokens.Text,
              textAlign = TextAlign.Center
            )
            Text(
              text = stringResource(id = R.string.album_empty_tracks_sub),
              style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = AurixFontFamilies.AurixUiFamily
              ),
              color = AlbumDesignTokens.DimText,
              textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
              horizontalArrangement = Arrangement.spacedBy(10.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Button(
                onClick = { showAddSongsSheet = true },
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                  containerColor = dominantAccent,
                  contentColor = playButtonInk
                ),
                modifier = Modifier
                  .defaultMinSize(minHeight = 48.dp)
                  .testTag("empty_album_add_songs_button")
              ) {
                Icon(Icons.Rounded.PlaylistAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                  text = stringResource(id = R.string.album_add_songs),
                  fontFamily = AurixFontFamilies.AurixUiFamily,
                  fontWeight = FontWeight.SemiBold
                )
              }
              OutlinedButton(
                onClick = { importAudioLauncher.launch(arrayOf("audio/*")) },
                shape = CircleShape,
                border = BorderStroke(1.dp, AlbumDesignTokens.Border),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AlbumDesignTokens.Text),
                modifier = Modifier.defaultMinSize(minHeight = 48.dp)
              ) {
                Text(
                  text = stringResource(id = R.string.library_add_local_short),
                  fontFamily = AurixFontFamilies.AurixUiFamily,
                  fontWeight = FontWeight.SemiBold
                )
              }
            }
          }
        }
      } else {
        item(key = "album_tracks_divider_header") {
          AlbumTracksSectionHeader(
            trackCount = tracks.size,
            totalDurationSeconds = totalDurationSeconds,
            accentColor = dominantAccent,
            modifier = Modifier
              .widthIn(max = 760.dp)
              .padding(bottom = 10.dp)
          )
        }

        itemsIndexed(
          items = tracks,
          key = { index, song -> "${index}_${song.id}" }
        ) { index, song ->
          val isCurrent = currentSong?.id == song.id
          val displayNumber = song.trackNumber.takeIf { it > 0 } ?: (index + 1)
          TrackRow(
            trackNumber = displayNumber,
            track = song,
            isCurrentTrack = isCurrent,
            isPlaying = isCurrent && isPlaying,
            accentColor = dominantAccent,
            glassColor = surfaceGlass,
            reducedMotion = effectiveReducedMotion,
            onClick = { onTrackClick(song) },
            onToggleFavorite = { onTrackFavoriteClick(song) },
            onDownloadTrack = { onTrackDownloadClick(song) },
            onTrackDetails = { onTrackDetailsClick(song) },
            onRemoveFromAlbum = onRemoveTrackFromAlbum?.let { cb -> { cb(song) } },
            modifier = Modifier
              .widthIn(max = 760.dp)
              .animateItem(
                fadeInSpec = if (effectiveReducedMotion) snap() else tween(260, easing = FastOutSlowInEasing),
                placementSpec = if (effectiveReducedMotion) snap() else spring(
                  dampingRatio = Spring.DampingRatioNoBouncy,
                  stiffness = Spring.StiffnessMediumLow
                ),
                fadeOutSpec = if (effectiveReducedMotion) snap() else tween(180)
              )
          )
          if (index < tracks.lastIndex) {
            Spacer(modifier = Modifier.height(5.dp))
          }
        }
      }
    }

    // Floating sticky frosted header bar when scrolling or when Back navigation is provided
    AlbumStickyTopBar(
      title = resolvedTitle.ifBlank { stringResource(id = R.string.album_unknown_title) },
      artist = resolvedArtist.ifBlank { stringResource(id = R.string.album_unknown_artist) },
      coverUrl = resolvedCover,
      collapseFraction = collapseFraction,
      statusTop = statusTop,
      accentColor = dominantAccent,
      backgroundColor = deepBackground,
      glassColor = surfaceGlass,
      onBackClick = onBackClick,
      modifier = Modifier.align(Alignment.TopCenter)
    )
  }

  if (showEditCoverSheet) {
    EditAlbumCoverSheet(
      albumInfo = effectiveAlbumInfo,
      reducedMotion = effectiveReducedMotion,
      onDismiss = { showEditCoverSheet = false },
      onSavePhoto = onSaveCoverPhoto,
      onSaveMix = onSaveCoverMix,
      onSaveStudio = onSaveCoverStudio,
      onSaveAppIcon = onSaveCoverAppIcon,
      onResetCover = onResetCover
    )
  }

  if (showAddSongsSheet) {
    AddSongsToAlbumSheet(
      album = effectiveAlbumInfo,
      availableSongs = availableSongsToAdd,
      onDismiss = { showAddSongsSheet = false },
      onAddSongs = onAddSongsToAlbum,
      onImportAudioFiles = onImportAudioFiles
    )
  }

  if (showDeleteAlbumDialog && onDeleteAlbum != null) {
    DeleteAlbumConfirmDialog(
      album = effectiveAlbumInfo,
      onDismiss = { showDeleteAlbumDialog = false },
      onConfirm = { alsoDeleteLocal ->
        showDeleteAlbumDialog = false
        onDeleteAlbum(alsoDeleteLocal)
      }
    )
  }
}

@Composable
private fun AlbumStickyTopBar(
  title: String,
  artist: String,
  coverUrl: String?,
  collapseFraction: Float,
  statusTop: Dp,
  accentColor: Color,
  backgroundColor: Color,
  glassColor: Color,
  onBackClick: (() -> Unit)?,
  modifier: Modifier = Modifier
) {
  val titleAlpha = ((collapseFraction - 0.35f) / 0.55f).coerceIn(0f, 1f)
  val barBgAlpha = (collapseFraction * 0.90f).coerceIn(0f, 0.92f)
  if (onBackClick == null && titleAlpha <= 0.02f) return

  Box(
    modifier = modifier
      .fillMaxWidth()
      .background(
        Brush.verticalGradient(
          colors = listOf(
            backgroundColor.copy(alpha = barBgAlpha),
            backgroundColor.copy(alpha = barBgAlpha * 0.78f),
            Color.Transparent
          )
        )
      )
      .padding(top = statusTop + 6.dp, bottom = 8.dp, start = 14.dp, end = 14.dp)
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .height(48.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      if (onBackClick != null) {
        val backCd = stringResource(id = R.string.album_back_cd)
        val backInteraction = remember { MutableInteractionSource() }
        val backPressed by backInteraction.collectIsPressedAsState()
        val backScale by animateFloatAsState(
          targetValue = if (backPressed) 0.90f else 1f,
          animationSpec = spring(dampingRatio = 0.65f, stiffness = Spring.StiffnessMedium),
          label = "album_back_scale"
        )
        Box(
          modifier = Modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .scale(backScale)
            .clip(CircleShape)
            .background(glassColor.copy(alpha = 0.78f))
            .border(1.dp, AlbumDesignTokens.Border, CircleShape)
            .clickable(
              interactionSource = backInteraction,
              indication = ripple(bounded = false, radius = 24.dp, color = AlbumDesignTokens.Text),
              role = Role.Button,
              onClickLabel = backCd,
              onClick = onBackClick
            )
            .semantics { contentDescription = backCd }
            .testTag("album_back_button"),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
            contentDescription = null,
            tint = AlbumDesignTokens.Text,
            modifier = Modifier.size(20.dp)
          )
        }
      }

      if (titleAlpha > 0.02f) {
        Row(
          modifier = Modifier
            .weight(1f)
            .alpha(titleAlpha)
            .clip(CircleShape)
            .background(glassColor.copy(alpha = 0.84f))
            .border(1.dp, accentColor.copy(alpha = 0.24f), CircleShape)
            .padding(horizontal = 10.dp, vertical = 6.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          if (!coverUrl.isNullOrBlank() && coverUrl != APP_ICON_ARTWORK) {
            val miniReq = com.example.ui.image.rememberCrispArtworkRequest(model = coverUrl)
            AsyncImage(
              model = miniReq,
              contentDescription = null,
              contentScale = ContentScale.Crop,
              filterQuality = com.example.ui.image.AurixImagePipeline.ARTWORK_FILTER_QUALITY,
              modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(8.dp))
            )
          } else {
            Box(
              modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(accentColor.copy(alpha = 0.24f)),
              contentAlignment = Alignment.Center
            ) {
              Icon(
                imageVector = Icons.Rounded.Album,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(16.dp)
              )
            }
          }
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = title,
              style = MaterialTheme.typography.labelLarge.copy(
                fontFamily = AurixFontFamilies.AurixUiFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 13.5.sp
              ),
              color = AlbumDesignTokens.Text,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
            Text(
              text = artist,
              style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = AurixFontFamilies.AurixUiFamily,
                fontSize = 11.sp
              ),
              color = accentColor,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
          }
        }
      } else {
        Spacer(modifier = Modifier.weight(1f))
      }
    }
  }
}

@Composable
private fun AlbumBanner(
  bannerKey: String,
  cachedVisual: CachedBannerVisual?,
  accentColor: Color,
  secondaryAccentColor: Color,
  backgroundColor: Color,
  isAlbumPlaying: Boolean,
  scrollOffsetPx: Float,
  bannerHeightPx: Float,
  reducedMotion: Boolean,
  modifier: Modifier = Modifier
) {
  val safeHeight = bannerHeightPx.coerceAtLeast(1f)
  val parallaxOffsetY = if (reducedMotion) 0f else -scrollOffsetPx * 0.42f
  val scrollFade = if (reducedMotion) 1f else (1f - (scrollOffsetPx / safeHeight)).coerceIn(0f, 1f)

  val auroraPulse = if (isAlbumPlaying && !reducedMotion) {
    val transition = rememberInfiniteTransition(label = "album_aurora_pulse")
    val phase by transition.animateFloat(
      initialValue = 0f,
      targetValue = 1f,
      animationSpec = infiniteRepeatable(
        animation = tween(durationMillis = 4800, easing = FastOutSlowInEasing),
        repeatMode = RepeatMode.Reverse
      ),
      label = "album_aurora_phase"
    )
    phase
  } else {
    0.35f
  }

  Box(
    modifier = modifier
      .graphicsLayer {
        translationY = parallaxOffsetY
        alpha = scrollFade
      }
  ) {
    AnimatedContent(
      targetState = bannerKey to cachedVisual,
      transitionSpec = {
        if (reducedMotion) {
          fadeIn(snap()) togetherWith fadeOut(snap())
        } else {
          fadeIn(tween(durationMillis = 520)) togetherWith fadeOut(tween(durationMillis = 520))
        }
      },
      contentKey = { (key, visual) -> "${key}_${visual != null}" },
      label = "album_banner_crossfade"
    ) { (key, visual) ->
      Box(modifier = Modifier.fillMaxSize()) {
        when {
          visual != null -> {
            Image(
              bitmap = visual.blurredBitmap,
              contentDescription = null,
              contentScale = ContentScale.Crop,
              filterQuality = com.example.ui.image.AurixImagePipeline.ARTWORK_FILTER_QUALITY,
              modifier = Modifier
                .fillMaxSize()
                .then(
                  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Modifier.blur(24.dp) else Modifier
                )
            )
          }
          key.isNotBlank() && key != APP_ICON_ARTWORK -> {
            val bannerArtReq = com.example.ui.image.rememberCrispArtworkRequest(model = key)
            AsyncImage(
              model = bannerArtReq,
              contentDescription = null,
              contentScale = ContentScale.Crop,
              filterQuality = com.example.ui.image.AurixImagePipeline.ARTWORK_FILTER_QUALITY,
              modifier = Modifier
                .fillMaxSize()
                .then(
                  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Modifier.blur(42.dp) else Modifier
                )
            )
          }
          else -> {
            Box(
              modifier = Modifier
                .fillMaxSize()
                .background(
                  Brush.verticalGradient(
                    colors = listOf(
                      accentColor.copy(alpha = 0.42f),
                      secondaryAccentColor.copy(alpha = 0.28f),
                      backgroundColor
                    )
                  )
                )
            )
          }
        }

        // Dual-tone atmospheric aurora glow synced to cover accent
        Canvas(modifier = Modifier.fillMaxSize()) {
          val w = size.width
          val h = size.height
          val driftX = (auroraPulse - 0.5f) * w * 0.14f
          drawCircle(
            brush = Brush.radialGradient(
              colors = listOf(
                accentColor.copy(alpha = 0.30f + auroraPulse * 0.10f),
                Color.Transparent
              ),
              center = Offset(w * 0.36f + driftX, h * 0.30f),
              radius = w * 0.68f
            ),
            radius = w * 0.68f,
            center = Offset(w * 0.36f + driftX, h * 0.30f)
          )
          drawCircle(
            brush = Brush.radialGradient(
              colors = listOf(
                secondaryAccentColor.copy(alpha = 0.22f + (1f - auroraPulse) * 0.08f),
                Color.Transparent
              ),
              center = Offset(w * 0.72f - driftX, h * 0.42f),
              radius = w * 0.55f
            ),
            radius = w * 0.55f,
            center = Offset(w * 0.72f - driftX, h * 0.42f)
          )
        }

        // Scrim & smooth bottom dissolve into adaptive deep canvas background
        Box(
          modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.38f))
            .background(
              Brush.verticalGradient(
                0.0f to Color.Transparent,
                0.48f to backgroundColor.copy(alpha = 0.48f),
                0.82f to backgroundColor.copy(alpha = 0.90f),
                1.0f to backgroundColor
              )
            )
        )
      }
    }
  }
}

@Composable
fun AlbumHeader(
  albumTitle: String,
  artistName: String,
  albumYear: String = "",
  trackCount: Int,
  coverUrl: String? = null,
  isAddedToLibrary: Boolean = false,
  isAlbumDownloaded: Boolean = false,
  isShuffleEnabled: Boolean = false,
  showAlbumMenu: Boolean = false,
  canResetCover: Boolean = false,
  reducedMotion: Boolean = false,
  onAddToLibraryClick: () -> Unit = {},
  onDownloadClick: () -> Unit = {},
  onAddSongsClick: () -> Unit = {},
  onMoreClick: () -> Unit = {},
  onDismissMenu: () -> Unit = {},
  onEditCoverClick: () -> Unit = {},
  onResetCoverClick: () -> Unit = {},
  onImportAudioClick: () -> Unit = {},
  onDeleteAlbumClick: (() -> Unit)? = null,
  onPlayClick: () -> Unit = {},
  onShuffleClick: () -> Unit = {},
  modifier: Modifier = Modifier,
  totalDurationSeconds: Long = 0L,
  isAlbumPlaying: Boolean = false,
  accentColor: Color = AlbumDesignTokens.Accent,
  secondaryAccentColor: Color = AlbumDesignTokens.CoverGradientStart,
  glassColor: Color = AlbumDesignTokens.GlassFill
) {
  val cleanYear = albumYear.trim()
  val metaText = when {
    trackCount == 0 -> stringResource(id = R.string.album_meta_empty)
    trackCount == 1 && cleanYear.isNotEmpty() -> stringResource(id = R.string.album_meta_single_year, cleanYear)
    trackCount == 1 -> stringResource(id = R.string.album_meta_single)
    cleanYear.isNotEmpty() -> stringResource(id = R.string.album_meta_plural_year, trackCount, cleanYear)
    else -> stringResource(id = R.string.album_meta_plural, trackCount)
  }
  val durationChipText = formatAlbumTotalDuration(totalDurationSeconds)

  BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
    val wide = maxWidth >= 560.dp
    val coverSize = if (wide) 168.dp else 148.dp

    if (wide) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(24.dp)
      ) {
        AlbumCoverVinylShowcase(
          albumTitle = albumTitle,
          coverUrl = coverUrl,
          coverSize = coverSize,
          isAlbumPlaying = isAlbumPlaying,
          accentColor = accentColor,
          secondaryAccentColor = secondaryAccentColor,
          reducedMotion = reducedMotion
        )
        Column(
          modifier = Modifier.weight(1f),
          verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          AlbumHeroMetaPill(
            metaText = metaText,
            durationText = durationChipText,
            isAlbumPlaying = isAlbumPlaying,
            accentColor = accentColor,
            glassColor = glassColor
          )
          Text(
            text = albumTitle.ifBlank { stringResource(id = R.string.album_unknown_title) },
            style = MaterialTheme.typography.displayLarge.copy(
              fontFamily = AurixFontFamilies.AurixUiFamily,
              fontSize = 30.sp,
              lineHeight = 36.sp,
              fontWeight = FontWeight.Bold
            ),
            color = AlbumDesignTokens.Text,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
          )
          Text(
            text = artistName.ifBlank { stringResource(id = R.string.album_unknown_artist) },
            style = MaterialTheme.typography.bodyLarge.copy(
              fontFamily = AurixFontFamilies.AurixUiFamily,
              fontSize = 16.sp,
              lineHeight = 22.sp,
              fontWeight = FontWeight.SemiBold
            ),
            color = accentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
          Spacer(modifier = Modifier.height(6.dp))
          ActionButtons(
            isAddedToLibrary = isAddedToLibrary,
            isAlbumDownloaded = isAlbumDownloaded,
            accentColor = accentColor,
            glassColor = glassColor,
            reducedMotion = reducedMotion,
            onAddToLibraryClick = onAddToLibraryClick,
            onDownloadClick = onDownloadClick,
            onAddSongsClick = onAddSongsClick,
            onMoreClick = onMoreClick,
            moreMenuContent = {
              AlbumMoreGlassMenu(
                expanded = showAlbumMenu,
                isAddedToLibrary = isAddedToLibrary,
                isAlbumDownloaded = isAlbumDownloaded,
                isShuffleEnabled = isShuffleEnabled,
                canResetCover = canResetCover,
                reducedMotion = reducedMotion,
                onDismissRequest = onDismissMenu,
                onPlayClick = onPlayClick,
                onShuffleClick = onShuffleClick,
                onAddSongsClick = onAddSongsClick,
                onImportAudioClick = onImportAudioClick,
                onEditCoverClick = onEditCoverClick,
                onResetCoverClick = onResetCoverClick,
                onToggleLibraryClick = onAddToLibraryClick,
                onDownloadAlbumClick = onDownloadClick,
                onDeleteAlbumClick = onDeleteAlbumClick
              )
            }
          )
        }
      }
    } else {
      Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        AlbumCoverVinylShowcase(
          albumTitle = albumTitle,
          coverUrl = coverUrl,
          coverSize = coverSize,
          isAlbumPlaying = isAlbumPlaying,
          accentColor = accentColor,
          secondaryAccentColor = secondaryAccentColor,
          reducedMotion = reducedMotion
        )
        Spacer(modifier = Modifier.height(14.dp))
        AlbumHeroMetaPill(
          metaText = metaText,
          durationText = durationChipText,
          isAlbumPlaying = isAlbumPlaying,
          accentColor = accentColor,
          glassColor = glassColor
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
          text = albumTitle.ifBlank { stringResource(id = R.string.album_unknown_title) },
          style = MaterialTheme.typography.displayLarge.copy(
            fontFamily = AurixFontFamilies.AurixUiFamily,
            fontSize = 26.sp,
            lineHeight = 32.sp,
            fontWeight = FontWeight.Bold
          ),
          color = AlbumDesignTokens.Text,
          textAlign = TextAlign.Center,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
          text = artistName.ifBlank { stringResource(id = R.string.album_unknown_artist) },
          style = MaterialTheme.typography.bodyLarge.copy(
            fontFamily = AurixFontFamilies.AurixUiFamily,
            fontSize = 15.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.SemiBold
          ),
          color = accentColor,
          textAlign = TextAlign.Center,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(14.dp))
        ActionButtons(
          isAddedToLibrary = isAddedToLibrary,
          isAlbumDownloaded = isAlbumDownloaded,
          accentColor = accentColor,
          glassColor = glassColor,
          reducedMotion = reducedMotion,
          centered = true,
          onAddToLibraryClick = onAddToLibraryClick,
          onDownloadClick = onDownloadClick,
          onAddSongsClick = onAddSongsClick,
          onMoreClick = onMoreClick,
          moreMenuContent = {
            AlbumMoreGlassMenu(
              expanded = showAlbumMenu,
              isAddedToLibrary = isAddedToLibrary,
              isAlbumDownloaded = isAlbumDownloaded,
              isShuffleEnabled = isShuffleEnabled,
              canResetCover = canResetCover,
              reducedMotion = reducedMotion,
              onDismissRequest = onDismissMenu,
              onPlayClick = onPlayClick,
              onShuffleClick = onShuffleClick,
              onAddSongsClick = onAddSongsClick,
              onImportAudioClick = onImportAudioClick,
              onEditCoverClick = onEditCoverClick,
              onResetCoverClick = onResetCoverClick,
              onToggleLibraryClick = onAddToLibraryClick,
              onDownloadAlbumClick = onDownloadClick,
              onDeleteAlbumClick = onDeleteAlbumClick
            )
          }
        )
      }
    }
  }
}

@Composable
private fun AlbumCoverVinylShowcase(
  albumTitle: String,
  coverUrl: String?,
  coverSize: Dp,
  isAlbumPlaying: Boolean,
  accentColor: Color,
  secondaryAccentColor: Color,
  reducedMotion: Boolean
) {
  val coverShape = RoundedCornerShape(26.dp)
  var imageFailed by remember(coverUrl) { mutableStateOf(false) }

  // Smooth spring slide-out when playing
  val vinylPeekOffset by animateDpAsState(
    targetValue = if (isAlbumPlaying) 36.dp else 22.dp,
    animationSpec = if (reducedMotion) snap() else spring(
      dampingRatio = 0.72f,
      stiffness = Spring.StiffnessMediumLow
    ),
    label = "album_vinyl_peek"
  )

  // Continuous linear rotation only while actively playing & not reduced motion; freezes cleanly on pause
  var vinylAngle by remember { mutableFloatStateOf(0f) }
  LaunchedEffect(isAlbumPlaying, reducedMotion) {
    if (!isAlbumPlaying || reducedMotion) return@LaunchedEffect
    var lastNanos = 0L
    while (true) {
      withFrameNanos { frameNanos ->
        if (lastNanos != 0L) {
          val dtSec = ((frameNanos - lastNanos).coerceAtLeast(0L)) / 1_000_000_000f
          // 10 seconds per revolution (36 deg/s)
          vinylAngle = (vinylAngle + dtSec * 36f) % 360f
        }
        lastNanos = frameNanos
      }
    }
  }

  val coverScale by animateFloatAsState(
    targetValue = if (isAlbumPlaying && !reducedMotion) 1.02f else 1f,
    animationSpec = if (reducedMotion) snap() else spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessLow),
    label = "album_cover_playing_scale"
  )

  Box(
    modifier = Modifier
      .width(coverSize + 40.dp)
      .height(coverSize),
    contentAlignment = Alignment.CenterStart
  ) {
    // Peeking grooved vinyl disc behind the album sleeve
    Canvas(
      modifier = Modifier
        .padding(start = 10.dp)
        .offset(x = vinylPeekOffset)
        .size(coverSize * 0.88f)
        .shadow(
          elevation = 16.dp,
          shape = CircleShape,
          ambientColor = Color.Black.copy(alpha = 0.60f),
          spotColor = accentColor.copy(alpha = 0.35f)
        )
    ) {
      val radius = size.minDimension * 0.5f
      val center = Offset(size.width * 0.5f, size.height * 0.5f)
      rotate(degrees = vinylAngle, pivot = center) {
        drawCircle(color = Color(0xFF101116), radius = radius, center = center)
        // Subtle vinyl grooves
        var r = radius * 0.92f
        val minGroove = radius * 0.38f
        var toggle = false
        while (r > minGroove) {
          drawCircle(
            color = if (toggle) Color(0xFF1D202A) else Color(0xFF12141B),
            radius = r,
            center = center,
            style = Stroke(width = 1.6.dp.toPx())
          )
          r -= 3.2.dp.toPx()
          toggle = !toggle
        }
        // Rotating light sheen wedge so spin is visible
        drawArc(
          brush = Brush.sweepGradient(
            0.0f to Color.Transparent,
            0.12f to Color.White.copy(alpha = 0.14f),
            0.25f to Color.Transparent,
            0.55f to Color.White.copy(alpha = 0.10f),
            0.70f to Color.Transparent,
            1.0f to Color.Transparent
          ),
          startAngle = 0f,
          sweepAngle = 360f,
          useCenter = true
        )
        // Center vinyl label tinted with cover color
        drawCircle(
          brush = Brush.radialGradient(
            colors = listOf(accentColor, secondaryAccentColor),
            center = center,
            radius = radius * 0.33f
          ),
          radius = radius * 0.33f,
          center = center
        )
        // Small marker dot on label
        drawCircle(
          color = Color.White.copy(alpha = 0.72f),
          radius = radius * 0.045f,
          center = Offset(center.x, center.y - radius * 0.21f)
        )
        // Center spindle hole
        drawCircle(
          color = Color(0xFF0A0D16),
          radius = radius * 0.075f,
          center = center
        )
      }
      // Static outer vinyl rim & crisp highlight
      drawCircle(
        color = Color.White.copy(alpha = 0.16f),
        radius = radius - 0.5.dp.toPx(),
        center = center,
        style = Stroke(width = 1.dp.toPx())
      )
    }

    // Crisp elevated Album Cover sleeve in front
    Box(
      modifier = Modifier
        .size(coverSize)
        .scale(coverScale)
        .shadow(
          elevation = 24.dp,
          shape = coverShape,
          ambientColor = Color.Black.copy(alpha = 0.75f),
          spotColor = accentColor.copy(alpha = 0.55f)
        )
        .clip(coverShape)
        .background(
          Brush.linearGradient(
            colors = listOf(accentColor.copy(alpha = 0.85f), secondaryAccentColor.copy(alpha = 0.85f))
          )
        )
        .border(1.dp, Color.White.copy(alpha = 0.22f), coverShape)
        .drawBehind {
          val stroke = 1.2.dp.toPx()
          val corner = 26.dp.toPx()
          drawRoundRect(
            brush = Brush.verticalGradient(
              colors = listOf(Color.White.copy(alpha = 0.32f), Color.Transparent),
              startY = 0f,
              endY = size.height * 0.35f
            ),
            topLeft = Offset(stroke * 0.5f, stroke * 0.5f),
            size = Size(size.width - stroke, size.height - stroke),
            cornerRadius = CornerRadius(corner, corner),
            style = Stroke(width = stroke)
          )
        }
        .testTag("album_cover_art"),
      contentAlignment = Alignment.Center
    ) {
      Crossfade(
        targetState = coverUrl.orEmpty(),
        animationSpec = if (reducedMotion) snap() else tween(durationMillis = 320, easing = FastOutSlowInEasing),
        label = "album_header_cover_crossfade"
      ) { activeCover ->
        Box(
          modifier = Modifier.fillMaxSize(),
          contentAlignment = Alignment.Center
        ) {
          when {
            activeCover == APP_ICON_ARTWORK -> {
              Image(
                painter = painterResource(id = R.drawable.ic_launcher_foreground),
                contentDescription = albumTitle,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(0.86f)
              )
            }
            activeCover.isNotBlank() && !imageFailed -> {
              val headerArtReq = com.example.ui.image.rememberCrispArtworkRequest(model = activeCover)
              AsyncImage(
                model = headerArtReq,
                contentDescription = albumTitle,
                contentScale = ContentScale.Crop,
                filterQuality = com.example.ui.image.AurixImagePipeline.ARTWORK_FILTER_QUALITY,
                onError = { imageFailed = true },
                modifier = Modifier.fillMaxSize()
              )
            }
            else -> {
              Icon(
                imageVector = Icons.Rounded.MusicNote,
                contentDescription = albumTitle,
                tint = Color.White.copy(alpha = 0.92f),
                modifier = Modifier.size(50.dp)
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun AlbumHeroMetaPill(
  metaText: String,
  durationText: String,
  isAlbumPlaying: Boolean,
  accentColor: Color,
  glassColor: Color
) {
  val fullLabel = if (durationText.isNotBlank()) "$metaText · $durationText" else metaText
  Row(
    modifier = Modifier
      .clip(CircleShape)
      .background(glassColor)
      .border(1.dp, accentColor.copy(alpha = if (isAlbumPlaying) 0.42f else 0.20f), CircleShape)
      .padding(horizontal = 12.dp, vertical = 5.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(6.dp)
  ) {
    AnimatedVisibility(visible = isAlbumPlaying) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
      ) {
        Box(
          modifier = Modifier
            .size(7.dp)
            .clip(CircleShape)
            .background(accentColor)
        )
        Text(
          text = stringResource(id = R.string.album_playing_badge),
          style = MaterialTheme.typography.labelSmall.copy(
            fontFamily = AurixFontFamilies.AurixUiFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp
          ),
          color = accentColor,
          maxLines = 1
        )
        Text(
          text = "·",
          style = MaterialTheme.typography.labelSmall,
          color = AlbumDesignTokens.DimText
        )
      }
    }
    Text(
      text = fullLabel,
      style = MaterialTheme.typography.bodySmall.copy(
        fontFamily = AurixFontFamilies.AurixUiFamily,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium
      ),
      color = AlbumDesignTokens.DimText,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis
    )
  }
}

@Composable
fun ActionButtons(
  isAddedToLibrary: Boolean,
  isAlbumDownloaded: Boolean,
  onAddToLibraryClick: () -> Unit,
  onDownloadClick: () -> Unit,
  onAddSongsClick: () -> Unit = {},
  onMoreClick: () -> Unit,
  modifier: Modifier = Modifier,
  moreMenuContent: (@Composable () -> Unit)? = null,
  accentColor: Color = AlbumDesignTokens.Accent,
  glassColor: Color = AlbumDesignTokens.GlassFill,
  reducedMotion: Boolean = false,
  centered: Boolean = false
) {
  val libraryTint by animateColorAsState(
    targetValue = if (isAddedToLibrary) accentColor else AlbumDesignTokens.Text.copy(alpha = 0.90f),
    animationSpec = if (reducedMotion) snap() else tween(durationMillis = 220),
    label = "album_library_tint"
  )
  val downloadTint by animateColorAsState(
    targetValue = if (isAlbumDownloaded) accentColor else AlbumDesignTokens.Text.copy(alpha = 0.90f),
    animationSpec = if (reducedMotion) snap() else tween(durationMillis = 220),
    label = "album_download_tint"
  )

  Row(
    modifier = modifier.fillMaxWidth(),
    horizontalArrangement = if (centered) Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally) else Arrangement.spacedBy(10.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    AlbumHeaderIconButton(
      icon = if (isAddedToLibrary) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
      contentDescription = if (isAddedToLibrary) {
        stringResource(id = R.string.album_remove_from_library)
      } else {
        stringResource(id = R.string.album_add_to_library)
      },
      tint = libraryTint,
      active = isAddedToLibrary,
      accentColor = accentColor,
      glassColor = glassColor,
      reducedMotion = reducedMotion,
      onClick = onAddToLibraryClick,
      testTag = "album_add_library_button"
    )
    AlbumHeaderIconButton(
      icon = if (isAlbumDownloaded) Icons.Rounded.DownloadDone else Icons.Rounded.Download,
      contentDescription = stringResource(id = R.string.album_download),
      tint = downloadTint,
      active = isAlbumDownloaded,
      accentColor = accentColor,
      glassColor = glassColor,
      reducedMotion = reducedMotion,
      onClick = onDownloadClick,
      testTag = "album_download_button"
    )
    AlbumHeaderIconButton(
      icon = Icons.Rounded.PlaylistAdd,
      contentDescription = stringResource(id = R.string.album_add_songs),
      tint = accentColor,
      active = false,
      accentColor = accentColor,
      glassColor = glassColor,
      reducedMotion = reducedMotion,
      onClick = onAddSongsClick,
      testTag = "album_add_songs_button"
    )
    Box {
      AlbumHeaderIconButton(
        icon = Icons.Rounded.MoreHoriz,
        contentDescription = stringResource(id = R.string.album_more_options),
        tint = AlbumDesignTokens.Text.copy(alpha = 0.90f),
        active = false,
        accentColor = accentColor,
        glassColor = glassColor,
        reducedMotion = reducedMotion,
        onClick = onMoreClick,
        testTag = "album_more_button"
      )
      moreMenuContent?.invoke()
    }
  }
}

@Composable
private fun AlbumHeaderIconButton(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  contentDescription: String,
  tint: Color,
  active: Boolean,
  accentColor: Color,
  glassColor: Color,
  reducedMotion: Boolean,
  onClick: () -> Unit,
  testTag: String
) {
  val interactionSource = remember { MutableInteractionSource() }
  val isFocused by interactionSource.collectIsFocusedAsState()
  val isPressed by interactionSource.collectIsPressedAsState()
  val scale by animateFloatAsState(
    targetValue = if (isPressed && !reducedMotion) 0.88f else 1f,
    animationSpec = if (reducedMotion) snap() else spring(dampingRatio = 0.58f, stiffness = 520f),
    label = "album_icon_btn_scale"
  )

  val bg = if (active) accentColor.copy(alpha = 0.16f) else glassColor
  val borderColor = when {
    isFocused -> accentColor
    active -> accentColor.copy(alpha = 0.45f)
    else -> AlbumDesignTokens.Border
  }

  Box(
    modifier = Modifier
      .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
      .scale(scale)
      .clip(CircleShape)
      .background(bg)
      .border(
        width = if (isFocused) 2.dp else 1.dp,
        color = borderColor,
        shape = CircleShape
      )
      .drawBehind {
        val stroke = 1.dp.toPx()
        if (size.width <= stroke || size.height <= stroke) return@drawBehind
        val inset = stroke * 0.5f
        val r = ((size.minDimension - stroke) * 0.5f).coerceAtLeast(0f)
        drawRoundRect(
          brush = Brush.verticalGradient(
            colors = listOf(AlbumDesignTokens.TopHighlight, Color.Transparent),
            startY = 0f,
            endY = size.height * 0.5f
          ),
          topLeft = Offset(inset, inset),
          size = Size(size.width - stroke, size.height - stroke),
          cornerRadius = CornerRadius(r, r),
          style = Stroke(width = stroke)
        )
      }
      .clickable(
        interactionSource = interactionSource,
        indication = ripple(bounded = false, radius = 24.dp, color = AlbumDesignTokens.Text),
        role = Role.Button,
        onClickLabel = contentDescription,
        onClick = onClick
      )
      .semantics { this.contentDescription = contentDescription }
      .testTag(testTag),
    contentAlignment = Alignment.Center
  ) {
    Icon(
      imageVector = icon,
      contentDescription = null,
      tint = tint,
      modifier = Modifier.size(21.dp)
    )
  }
}

@Composable
fun PlayShuffleRow(
  isShuffleEnabled: Boolean,
  enabled: Boolean = true,
  accentColor: Color = AlbumDesignTokens.Accent,
  onPlayClick: () -> Unit,
  onShuffleClick: () -> Unit,
  modifier: Modifier = Modifier,
  secondaryAccentColor: Color = accentColor,
  playContentInk: Color = AlbumDesignTokens.Background,
  glassColor: Color = AlbumDesignTokens.GlassFill,
  reducedMotion: Boolean = false
) {
  val buttonShape = CircleShape
  val playInteraction = remember { MutableInteractionSource() }
  val shuffleInteraction = remember { MutableInteractionSource() }
  val playFocused by playInteraction.collectIsFocusedAsState()
  val shuffleFocused by shuffleInteraction.collectIsFocusedAsState()
  val playPressed by playInteraction.collectIsPressedAsState()
  val shufflePressed by shuffleInteraction.collectIsPressedAsState()

  val playScale by animateFloatAsState(
    targetValue = if (playPressed && !reducedMotion) 0.96f else 1f,
    animationSpec = if (reducedMotion) snap() else spring(dampingRatio = 0.64f, stiffness = 480f),
    label = "album_play_scale"
  )
  val shuffleScale by animateFloatAsState(
    targetValue = if (shufflePressed && !reducedMotion) 0.96f else 1f,
    animationSpec = if (reducedMotion) snap() else spring(dampingRatio = 0.64f, stiffness = 480f),
    label = "album_shuffle_scale"
  )

  val playLabel = stringResource(id = R.string.album_action_play)
  val shuffleLabel = stringResource(id = R.string.album_action_shuffle)
  val playEndColor = lerp(accentColor, secondaryAccentColor, 0.35f)

  Row(
    modifier = modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    Box(
      modifier = Modifier
        .weight(1f)
        .height(54.dp)
        .defaultMinSize(minHeight = 48.dp)
        .scale(playScale)
        .shadow(
          elevation = if (enabled) 16.dp else 0.dp,
          shape = buttonShape,
          ambientColor = accentColor.copy(alpha = 0.30f),
          spotColor = accentColor.copy(alpha = 0.50f)
        )
        .clip(buttonShape)
        .background(
          brush = Brush.horizontalGradient(
            colors = if (enabled) {
              listOf(accentColor, playEndColor)
            } else {
              listOf(accentColor.copy(alpha = 0.42f), playEndColor.copy(alpha = 0.42f))
            }
          )
        )
        .then(
          if (playFocused) Modifier.border(2.dp, Color.White, buttonShape)
          else Modifier.border(1.dp, Color.White.copy(alpha = 0.28f), buttonShape)
        )
        .clickable(
          enabled = enabled,
          interactionSource = playInteraction,
          indication = ripple(color = playContentInk),
          role = Role.Button,
          onClickLabel = playLabel,
          onClick = onPlayClick
        )
        .testTag("album_play_button"),
      contentAlignment = Alignment.Center
    ) {
      Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Icon(
          imageVector = Icons.Rounded.PlayArrow,
          contentDescription = null,
          tint = playContentInk,
          modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
          text = playLabel,
          style = MaterialTheme.typography.bodyLarge.copy(
            fontFamily = AurixFontFamilies.AurixUiFamily,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
          ),
          color = playContentInk,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
      }
    }

    val shuffleContainer = if (isShuffleEnabled) {
      accentColor.copy(alpha = 0.22f)
    } else {
      glassColor
    }
    val shuffleBorderColor = when {
      shuffleFocused -> accentColor
      isShuffleEnabled -> accentColor.copy(alpha = 0.56f)
      else -> AlbumDesignTokens.Border
    }
    val shuffleContentColor = if (isShuffleEnabled) {
      accentColor
    } else {
      AlbumDesignTokens.Text
    }

    Surface(
      shape = buttonShape,
      color = shuffleContainer,
      border = BorderStroke(if (shuffleFocused) 2.dp else 1.dp, shuffleBorderColor),
      modifier = Modifier
        .weight(1f)
        .height(54.dp)
        .defaultMinSize(minHeight = 48.dp)
        .scale(shuffleScale)
        .clip(buttonShape)
        .drawBehind {
          val stroke = 1.dp.toPx()
          if (size.width <= stroke || size.height <= stroke) return@drawBehind
          val inset = stroke * 0.5f
          val r = ((size.height - stroke) * 0.5f).coerceAtLeast(0f)
          drawRoundRect(
            brush = Brush.verticalGradient(
              colors = listOf(AlbumDesignTokens.TopHighlight, Color.Transparent),
              startY = 0f,
              endY = size.height * 0.45f
            ),
            topLeft = Offset(inset, inset),
            size = Size(size.width - stroke, size.height - stroke),
            cornerRadius = CornerRadius(r, r),
            style = Stroke(width = stroke)
          )
        }
        .clickable(
          enabled = enabled,
          interactionSource = shuffleInteraction,
          indication = ripple(color = AlbumDesignTokens.Text),
          role = Role.Button,
          onClickLabel = shuffleLabel,
          onClick = onShuffleClick
        )
        .testTag("album_shuffle_button")
    ) {
      Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Icon(
          imageVector = Icons.Rounded.Shuffle,
          contentDescription = null,
          tint = shuffleContentColor,
          modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
          text = shuffleLabel,
          style = MaterialTheme.typography.bodyLarge.copy(
            fontFamily = AurixFontFamilies.AurixUiFamily,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold
          ),
          color = shuffleContentColor,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
      }
    }
  }
}

@Composable
private fun AlbumTracksSectionHeader(
  trackCount: Int,
  totalDurationSeconds: Long,
  accentColor: Color,
  modifier: Modifier = Modifier
) {
  val durationText = formatAlbumTotalDuration(totalDurationSeconds)
  Column(
    modifier = modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(8.dp)
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        Text(
          text = stringResource(id = R.string.album_tracks_heading),
          style = MaterialTheme.typography.titleMedium.copy(
            fontFamily = AurixFontFamilies.AurixUiFamily,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
          ),
          color = AlbumDesignTokens.Text
        )
        Box(
          modifier = Modifier
            .clip(CircleShape)
            .background(accentColor.copy(alpha = 0.16f))
            .padding(horizontal = 8.dp, vertical = 2.dp)
        ) {
          Text(
            text = trackCount.toString(),
            style = MaterialTheme.typography.labelSmall.copy(
              fontFamily = AlbumDesignTokens.OutfitFontFamily,
              fontWeight = FontWeight.SemiBold,
              fontSize = 11.5.sp
            ),
            color = accentColor
          )
        }
      }
      if (durationText.isNotBlank()) {
        Text(
          text = durationText,
          style = MaterialTheme.typography.bodySmall.copy(
            fontFamily = AurixFontFamilies.AurixUiFamily,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
          ),
          color = AlbumDesignTokens.DimText
        )
      }
    }
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .height(1.dp)
        .background(
          Brush.horizontalGradient(
            colors = listOf(
              accentColor.copy(alpha = 0.38f),
              AlbumDesignTokens.Border,
              Color.Transparent
            )
          )
        )
    )
  }
}

@Composable
fun TrackRow(
  trackNumber: Int,
  track: Song,
  isCurrentTrack: Boolean,
  isPlaying: Boolean,
  accentColor: Color = AlbumDesignTokens.Accent,
  reducedMotion: Boolean = false,
  onClick: () -> Unit,
  onToggleFavorite: () -> Unit = {},
  onDownloadTrack: () -> Unit = {},
  onTrackDetails: () -> Unit = {},
  onRemoveFromAlbum: (() -> Unit)? = null,
  modifier: Modifier = Modifier,
  glassColor: Color = AlbumDesignTokens.GlassFill
) {
  val rowShape = RoundedCornerShape(20.dp)
  val rowInteraction = remember { MutableInteractionSource() }
  val rowFocused by rowInteraction.collectIsFocusedAsState()
  val rowPressed by rowInteraction.collectIsPressedAsState()

  val menuInteraction = remember { MutableInteractionSource() }
  val menuFocused by menuInteraction.collectIsFocusedAsState()
  var menuExpanded by remember { mutableStateOf(false) }

  val pressScale by animateFloatAsState(
    targetValue = if (rowPressed && !reducedMotion) 0.985f else 1f,
    animationSpec = if (reducedMotion) snap() else spring(dampingRatio = 0.7f, stiffness = 500f),
    label = "track_row_scale_${track.id}"
  )
  val rowBg by animateColorAsState(
    targetValue = if (isCurrentTrack) accentColor.copy(alpha = 0.16f) else glassColor.copy(alpha = 0.28f),
    animationSpec = if (reducedMotion) snap() else tween(durationMillis = 240, easing = FastOutSlowInEasing),
    label = "track_row_bg_${track.id}"
  )
  val titleColor by animateColorAsState(
    targetValue = if (isCurrentTrack) accentColor else AlbumDesignTokens.Text,
    animationSpec = if (reducedMotion) snap() else tween(durationMillis = 240, easing = FastOutSlowInEasing),
    label = "track_title_color_${track.id}"
  )
  val formattedNumber = remember(trackNumber) {
    String.format(Locale.US, "%02d", trackNumber.coerceAtLeast(1))
  }
  val artistText = track.artist.ifBlank { stringResource(id = R.string.album_unknown_artist) }
  val durationText = formatTrackDuration(track.durationSeconds)

  Row(
    modifier = modifier
      .fillMaxWidth()
      .scale(pressScale)
      .defaultMinSize(minHeight = 70.dp)
      .clip(rowShape)
      .background(rowBg)
      .then(
        when {
          rowFocused -> Modifier.border(2.dp, accentColor, rowShape)
          isCurrentTrack -> Modifier
            .border(1.dp, accentColor.copy(alpha = 0.36f), rowShape)
            .drawBehind {
              val stroke = 1.dp.toPx()
              if (size.width <= stroke || size.height <= stroke) return@drawBehind
              val r = 20.dp.toPx()
              drawRoundRect(
                brush = Brush.verticalGradient(
                  colors = listOf(Color.White.copy(alpha = 0.22f), Color.Transparent),
                  startY = 0f,
                  endY = size.height * 0.45f
                ),
                topLeft = Offset(stroke * 0.5f, stroke * 0.5f),
                size = Size(size.width - stroke, size.height - stroke),
                cornerRadius = CornerRadius(r, r),
                style = Stroke(width = stroke)
              )
            }
          else -> Modifier.border(0.6.dp, Color.White.copy(alpha = 0.06f), rowShape)
        }
      )
      .clickable(
        interactionSource = rowInteraction,
        indication = ripple(color = AlbumDesignTokens.Text),
        role = Role.Button,
        onClickLabel = track.title,
        onClick = onClick
      )
      .padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 10.dp)
      .testTag("album_track_row_${track.id}"),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Box(
      modifier = Modifier.width(34.dp),
      contentAlignment = Alignment.Center
    ) {
      AnimatedContent(
        targetState = isCurrentTrack,
        transitionSpec = {
          if (reducedMotion) {
            fadeIn(snap()) togetherWith fadeOut(snap())
          } else {
            (fadeIn(tween(200)) + scaleIn(tween(200), initialScale = 0.8f)) togetherWith
              (fadeOut(tween(150)) + scaleOut(tween(150), targetScale = 0.8f))
          }
        },
        label = "track_eq_or_number"
      ) { active ->
        if (active) {
          AlbumEqualizerBars(
            isPlaying = isPlaying,
            color = accentColor,
            reducedMotion = reducedMotion
          )
        } else {
          Text(
            text = formattedNumber,
            style = MaterialTheme.typography.bodyMedium.copy(
              fontFamily = AlbumDesignTokens.OutfitFontFamily,
              fontSize = 14.sp,
              fontWeight = FontWeight.Medium
            ),
            color = AlbumDesignTokens.DimText,
            textAlign = TextAlign.Center,
            maxLines = 1
          )
        }
      }
    }

    Spacer(modifier = Modifier.width(12.dp))

    Column(
      modifier = Modifier.weight(1f),
      verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
      Text(
        text = track.title,
        style = MaterialTheme.typography.bodyLarge.copy(
          fontFamily = AurixFontFamilies.AurixUiFamily,
          fontSize = 16.5.sp,
          fontWeight = if (isCurrentTrack) FontWeight.Bold else FontWeight.Medium,
          lineHeight = 22.sp
        ),
        color = titleColor,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = artistText,
          style = MaterialTheme.typography.bodySmall.copy(
            fontFamily = AurixFontFamilies.AurixUiFamily,
            fontSize = 13.sp,
            lineHeight = 18.sp
          ),
          color = AlbumDesignTokens.DimText,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f, fill = false)
        )
        Text(
          text = " · ",
          style = MaterialTheme.typography.bodySmall.copy(
            fontFamily = AlbumDesignTokens.OutfitFontFamily,
            fontSize = 13.sp,
            lineHeight = 18.sp
          ),
          color = AlbumDesignTokens.DimText,
          maxLines = 1
        )
        Text(
          text = durationText,
          style = MaterialTheme.typography.bodySmall.copy(
            fontFamily = AlbumDesignTokens.OutfitFontFamily,
            fontSize = 13.sp,
            lineHeight = 18.sp
          ),
          color = AlbumDesignTokens.DimText,
          maxLines = 1
        )
      }
    }

    Spacer(modifier = Modifier.width(6.dp))

    Box {
      val menuCd = stringResource(id = R.string.album_track_more_cd, track.title)
      val menuShape = CircleShape
      Box(
        modifier = Modifier
          .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
          .clip(menuShape)
          .then(
            if (menuFocused) Modifier.border(2.dp, accentColor, menuShape)
            else Modifier
          )
          .clickable(
            interactionSource = menuInteraction,
            indication = ripple(bounded = false, radius = 24.dp, color = AlbumDesignTokens.Text),
            role = Role.Button,
            onClickLabel = menuCd,
            onClick = { menuExpanded = true }
          )
          .semantics { contentDescription = menuCd }
          .testTag("album_track_menu_${track.id}"),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = Icons.Rounded.MoreVert,
          contentDescription = null,
          tint = AlbumDesignTokens.DimText,
          modifier = Modifier.size(20.dp)
        )
      }

      AlbumTrackGlassMenu(
        expanded = menuExpanded,
        track = track,
        isCurrentTrack = isCurrentTrack,
        isPlaying = isPlaying,
        reducedMotion = reducedMotion,
        onDismissRequest = { menuExpanded = false },
        onPlayPauseClick = onClick,
        onToggleFavoriteClick = onToggleFavorite,
        onDownloadTrackClick = onDownloadTrack,
        onTrackDetailsClick = onTrackDetails,
        onRemoveFromAlbumClick = onRemoveFromAlbum
      )
    }
  }
}

@Composable
fun AlbumEqualizerBars(
  isPlaying: Boolean,
  color: Color,
  reducedMotion: Boolean = false,
  modifier: Modifier = Modifier
) {
  val animateBars = isPlaying && !reducedMotion
  val heights = if (animateBars) {
    val transition = rememberInfiniteTransition(label = "album_track_eq")
    val bar1 by transition.animateFloat(
      initialValue = 0.28f,
      targetValue = 1.0f,
      animationSpec = infiniteRepeatable(
        animation = tween(durationMillis = 420, easing = FastOutSlowInEasing),
        repeatMode = RepeatMode.Reverse
      ),
      label = "album_eq_1"
    )
    val bar2 by transition.animateFloat(
      initialValue = 0.95f,
      targetValue = 0.30f,
      animationSpec = infiniteRepeatable(
        animation = tween(durationMillis = 540, easing = FastOutSlowInEasing),
        repeatMode = RepeatMode.Reverse
      ),
      label = "album_eq_2"
    )
    val bar3 by transition.animateFloat(
      initialValue = 0.36f,
      targetValue = 0.92f,
      animationSpec = infiniteRepeatable(
        animation = tween(durationMillis = 380, easing = FastOutSlowInEasing),
        repeatMode = RepeatMode.Reverse
      ),
      label = "album_eq_3"
    )
    listOf(bar1, bar2, bar3)
  } else {
    listOf(0.42f, 0.82f, 0.56f)
  }

  Row(
    modifier = modifier.height(15.dp),
    horizontalArrangement = Arrangement.spacedBy(2.5.dp),
    verticalAlignment = Alignment.Bottom
  ) {
    heights.forEach { fraction ->
      Box(
        modifier = Modifier
          .width(3.dp)
          .fillMaxHeight(fraction.coerceIn(0.2f, 1f))
          .clip(RoundedCornerShape(1.5.dp))
          .background(color)
      )
    }
  }
}

internal fun formatTrackDuration(durationSeconds: Long): String {
  val safeSeconds = durationSeconds.coerceAtLeast(0L)
  val minutes = safeSeconds / 60L
  val seconds = safeSeconds % 60L
  return String.format(Locale.US, "%d:%02d", minutes, seconds)
}

@Composable
private fun formatAlbumTotalDuration(totalSeconds: Long): String {
  if (totalSeconds <= 0L) return ""
  val totalMinutes = (totalSeconds / 60L).coerceAtLeast(1L).toInt()
  val hours = totalMinutes / 60
  val mins = totalMinutes % 60
  return if (hours > 0) {
    stringResource(id = R.string.album_total_duration_hours, hours, mins)
  } else {
    stringResource(id = R.string.album_total_duration_minutes, totalMinutes)
  }
}

private fun previewPlaceholderTracks(): List<Song> {
  val durations = listOf(192L, 218L, 174L, 245L, 203L)
  return durations.mapIndexed { idx, dur ->
    Song(
      id = "album_preview_${idx + 1}",
      title = "Track Title ${idx + 1}",
      artist = "Artist Name",
      artworkUrl = "",
      durationSeconds = dur,
      streamUrl = "https://example.com/track_${idx + 1}.m4a",
      source = "Album",
      fullDurationSeconds = dur,
      album = "Album Title",
      trackNumber = idx + 1,
      year = "2013"
    )
  }
}

@Preview(name = "Album Section - Playing", showBackground = true, backgroundColor = 0xFF0A0D16)
@Composable
private fun AlbumSectionPlayingPreview() {
  val tracks = remember { previewPlaceholderTracks() }
  AurixTheme(darkTheme = true) {
    AlbumTrackListSection(
      albumTitle = "Album Title",
      artistName = "Artist Name",
      albumYear = "2013",
      coverUrl = null,
      tracks = tracks,
      currentSong = tracks[2],
      isPlaying = true,
      isShuffleEnabled = false,
      isAddedToLibrary = false
    )
  }
}

@Preview(name = "Album Section - Paused", showBackground = true, backgroundColor = 0xFF0A0D16)
@Composable
private fun AlbumSectionPausedPreview() {
  val tracks = remember { previewPlaceholderTracks() }
  AurixTheme(darkTheme = true) {
    AlbumTrackListSection(
      albumTitle = "Album Title",
      artistName = "Artist Name",
      albumYear = "2013",
      coverUrl = null,
      tracks = tracks,
      currentSong = tracks[0],
      isPlaying = false,
      isShuffleEnabled = true,
      isAddedToLibrary = true
    )
  }
}
