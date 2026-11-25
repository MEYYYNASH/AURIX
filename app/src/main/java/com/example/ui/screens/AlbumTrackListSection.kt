package com.example.ui.screens

import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.AlbumBannerCache
import com.example.data.CachedBannerVisual
import com.example.model.APP_ICON_ARTWORK
import com.example.model.AlbumInfo
import com.example.model.Song
import com.example.ui.theme.AurixTheme
import java.util.Locale

object AlbumDesignTokens {
  val Background = Color(0xFF0E1120)
  val Surface = Color(0xFF171B30)
  val RaisedSurface = Color(0xFF202642)
  val GlassFill = Color(0xFF1C2138).copy(alpha = 0.56f)
  val Text = Color(0xFFF4F3FF)
  val DimText = Color(0xFF8B90A8)
  val Accent = Color(0xFFB9A2FF)
  val Border = Color.White.copy(alpha = 0.14f)
  val TopHighlight = Color.White.copy(alpha = 0.24f)
  val SidePadding = 20.dp
  val CoverGradientStart = Color(0xFF7B61FF)
  val CoverGradientEnd = Color(0xFF2D1E69)
  val OutfitFontFamily = FontFamily(Font(R.font.outfit))
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

  val activeBannerArtworkUrl = remember(currentSong?.artworkUrl, resolvedCover) {
    currentSong?.artworkUrl?.trim()?.takeIf { it.isNotEmpty() }
      ?: resolvedCover?.trim()?.takeIf { it.isNotEmpty() }
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
        // Keep previous blurred banner visible until next blurred bitmap finishes to prevent empty flash
        val loaded = AlbumBannerCache.getOrCreate(context, activeBannerArtworkUrl)
        if (loaded != null) {
          cachedBanner = loaded
        } else {
          cachedBanner = null
        }
      }
    }
  }

  val dominantAccentTarget = cachedBanner?.dominantColor ?: AlbumDesignTokens.Accent
  val dominantAccent by animateColorAsState(
    targetValue = dominantAccentTarget,
    animationSpec = if (effectiveReducedMotion) snap() else tween(durationMillis = 450),
    label = "album_dominant_accent"
  )

  val listState = rememberLazyListState()
  val bannerHeightDp = 320.dp + statusTop
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
      .background(AlbumDesignTokens.Background)
  ) {
    // Full-width dynamic blurred Album Banner behind header & action icons
    AlbumBanner(
      bannerKey = activeBannerArtworkUrl,
      cachedVisual = cachedBanner,
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
      contentPadding = PaddingValues(
        top = statusTop + 20.dp,
        bottom = dockClearanceBottom
      )
    ) {
      item(key = "album_header") {
        AlbumHeader(
          albumTitle = resolvedTitle,
          artistName = resolvedArtist,
          albumYear = resolvedYear,
          trackCount = tracks.size,
          coverUrl = resolvedCover,
          isAddedToLibrary = isAddedToLibrary,
          isAlbumDownloaded = isAlbumDownloaded,
          isShuffleEnabled = isShuffleEnabled,
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
          }
        )
        Spacer(modifier = Modifier.height(22.dp))
      }

      item(key = "album_play_shuffle") {
        PlayShuffleRow(
          isShuffleEnabled = isShuffleEnabled,
          enabled = tracks.isNotEmpty(),
          accentColor = dominantAccent,
          onPlayClick = onPlayClick,
          onShuffleClick = onShuffleClick
        )
        Spacer(modifier = Modifier.height(18.dp))
      }

      if (tracks.isEmpty()) {
        item(key = "album_empty_tracks") {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(22.dp))
              .background(AlbumDesignTokens.GlassFill)
              .border(1.dp, AlbumDesignTokens.Border, RoundedCornerShape(22.dp))
              .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            Icon(
              imageVector = Icons.Rounded.MusicNote,
              contentDescription = null,
              tint = dominantAccent,
              modifier = Modifier.size(32.dp)
            )
            Text(
              text = stringResource(id = R.string.album_empty_tracks_title),
              style = MaterialTheme.typography.titleMedium.copy(
                fontFamily = AlbumDesignTokens.OutfitFontFamily,
                fontWeight = FontWeight.Bold
              ),
              color = AlbumDesignTokens.Text,
              textAlign = TextAlign.Center
            )
            Text(
              text = stringResource(id = R.string.album_empty_tracks_sub),
              style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = AlbumDesignTokens.OutfitFontFamily
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
                  contentColor = AlbumDesignTokens.Background
                ),
                modifier = Modifier.testTag("empty_album_add_songs_button")
              ) {
                Icon(Icons.Rounded.PlaylistAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                  text = stringResource(id = R.string.album_add_songs),
                  fontFamily = AlbumDesignTokens.OutfitFontFamily,
                  fontWeight = FontWeight.SemiBold
                )
              }
              OutlinedButton(
                onClick = { importAudioLauncher.launch(arrayOf("audio/*")) },
                shape = CircleShape,
                border = BorderStroke(1.dp, AlbumDesignTokens.Border),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AlbumDesignTokens.Text)
              ) {
                Text(
                  text = stringResource(id = R.string.library_add_local_short),
                  fontFamily = AlbumDesignTokens.OutfitFontFamily,
                  fontWeight = FontWeight.SemiBold
                )
              }
            }
          }
        }
      } else {
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
            reducedMotion = effectiveReducedMotion,
            onClick = { onTrackClick(song) },
            onToggleFavorite = { onTrackFavoriteClick(song) },
            onDownloadTrack = { onTrackDownloadClick(song) },
            onTrackDetails = { onTrackDetailsClick(song) },
            onRemoveFromAlbum = onRemoveTrackFromAlbum?.let { cb -> { cb(song) } }
          )
          if (index < tracks.lastIndex) {
            Spacer(modifier = Modifier.height(4.dp))
          }
        }
      }
    }
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
private fun AlbumBanner(
  bannerKey: String,
  cachedVisual: CachedBannerVisual?,
  scrollOffsetPx: Float,
  bannerHeightPx: Float,
  reducedMotion: Boolean,
  modifier: Modifier = Modifier
) {
  val safeHeight = bannerHeightPx.coerceAtLeast(1f)
  val parallaxOffsetY = if (reducedMotion) 0f else -scrollOffsetPx * 0.5f
  val scrollFade = if (reducedMotion) 1f else (1f - (scrollOffsetPx / safeHeight)).coerceIn(0f, 1f)

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
          fadeIn(tween(durationMillis = 500)) togetherWith fadeOut(tween(durationMillis = 500))
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
                  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Modifier.blur(20.dp) else Modifier
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
                  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Modifier.blur(40.dp) else Modifier
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
                      AlbumDesignTokens.CoverGradientStart.copy(alpha = 0.72f),
                      AlbumDesignTokens.CoverGradientEnd.copy(alpha = 0.56f),
                      AlbumDesignTokens.Background
                    )
                  )
                )
            )
          }
        }

        // 45% dark scrim + smooth bottom fade into #0E1120
        Box(
          modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .background(
              Brush.verticalGradient(
                0.0f to Color.Transparent,
                0.52f to AlbumDesignTokens.Background.copy(alpha = 0.42f),
                0.84f to AlbumDesignTokens.Background.copy(alpha = 0.88f),
                1.0f to AlbumDesignTokens.Background
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
  modifier: Modifier = Modifier
) {
  val coverShape = RoundedCornerShape(32.dp)
  var imageFailed by remember(coverUrl) { mutableStateOf(false) }

  Row(
    modifier = modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(16.dp)
  ) {
    Box(
      modifier = Modifier
        .size(130.dp)
        .shadow(
          elevation = 22.dp,
          shape = coverShape,
          ambientColor = Color.Black.copy(alpha = 0.65f),
          spotColor = AlbumDesignTokens.CoverGradientStart.copy(alpha = 0.40f)
        )
        .clip(coverShape)
        .background(
          Brush.linearGradient(
            colors = listOf(
              AlbumDesignTokens.CoverGradientStart,
              AlbumDesignTokens.CoverGradientEnd
            )
          )
        )
        .border(1.dp, AlbumDesignTokens.Border, coverShape)
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
                tint = AlbumDesignTokens.Text.copy(alpha = 0.90f),
                modifier = Modifier.size(46.dp)
              )
            }
          }
        }
      }
    }

    Column(
      modifier = Modifier.weight(1f),
      verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
      val cleanYear = albumYear.trim()
      val metaText = when {
        trackCount == 0 -> stringResource(id = R.string.album_meta_empty)
        trackCount == 1 && cleanYear.isNotEmpty() -> stringResource(id = R.string.album_meta_single_year, cleanYear)
        trackCount == 1 -> stringResource(id = R.string.album_meta_single)
        cleanYear.isNotEmpty() -> stringResource(id = R.string.album_meta_plural_year, trackCount, cleanYear)
        else -> stringResource(id = R.string.album_meta_plural, trackCount)
      }
      Text(
        text = metaText,
        style = MaterialTheme.typography.bodySmall.copy(
          fontFamily = AlbumDesignTokens.OutfitFontFamily,
          fontSize = 13.sp,
          lineHeight = 18.sp,
          fontWeight = FontWeight.Normal
        ),
        color = AlbumDesignTokens.DimText,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
      Text(
        text = albumTitle.ifBlank { stringResource(id = R.string.album_unknown_title) },
        style = MaterialTheme.typography.displayLarge.copy(
          fontFamily = AlbumDesignTokens.OutfitFontFamily,
          fontSize = 30.sp,
          lineHeight = 34.sp,
          fontWeight = FontWeight.Bold
        ),
        color = AlbumDesignTokens.Text,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
      )
      Text(
        text = artistName.ifBlank { stringResource(id = R.string.album_unknown_artist) },
        style = MaterialTheme.typography.bodyLarge.copy(
          fontFamily = AlbumDesignTokens.OutfitFontFamily,
          fontSize = 15.sp,
          lineHeight = 20.sp,
          fontWeight = FontWeight.Medium
        ),
        color = AlbumDesignTokens.Accent,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )

      Spacer(modifier = Modifier.height(6.dp))

      ActionButtons(
        isAddedToLibrary = isAddedToLibrary,
        isAlbumDownloaded = isAlbumDownloaded,
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

@Composable
fun ActionButtons(
  isAddedToLibrary: Boolean,
  isAlbumDownloaded: Boolean,
  onAddToLibraryClick: () -> Unit,
  onDownloadClick: () -> Unit,
  onAddSongsClick: () -> Unit = {},
  onMoreClick: () -> Unit,
  modifier: Modifier = Modifier,
  moreMenuContent: (@Composable () -> Unit)? = null
) {
  val libraryTint by animateColorAsState(
    targetValue = if (isAddedToLibrary) AlbumDesignTokens.Accent else AlbumDesignTokens.Text.copy(alpha = 0.88f),
    animationSpec = tween(durationMillis = 220),
    label = "album_library_tint"
  )
  val downloadTint by animateColorAsState(
    targetValue = if (isAlbumDownloaded) AlbumDesignTokens.Accent else AlbumDesignTokens.Text.copy(alpha = 0.88f),
    animationSpec = tween(durationMillis = 220),
    label = "album_download_tint"
  )

  Row(
    modifier = modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(10.dp),
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
      onClick = onAddToLibraryClick,
      testTag = "album_add_library_button"
    )
    AlbumHeaderIconButton(
      icon = if (isAlbumDownloaded) Icons.Rounded.DownloadDone else Icons.Rounded.Download,
      contentDescription = stringResource(id = R.string.album_download),
      tint = downloadTint,
      onClick = onDownloadClick,
      testTag = "album_download_button"
    )
    AlbumHeaderIconButton(
      icon = Icons.Rounded.PlaylistAdd,
      contentDescription = stringResource(id = R.string.album_add_songs),
      tint = AlbumDesignTokens.Accent,
      onClick = onAddSongsClick,
      testTag = "album_add_songs_button"
    )
    Box {
      AlbumHeaderIconButton(
        icon = Icons.Rounded.MoreHoriz,
        contentDescription = "More album options",
        tint = AlbumDesignTokens.Text.copy(alpha = 0.88f),
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
  onClick: () -> Unit,
  testTag: String
) {
  val interactionSource = remember { MutableInteractionSource() }
  val isFocused by interactionSource.collectIsFocusedAsState()

  Box(
    modifier = Modifier
      .size(44.dp)
      .clip(CircleShape)
      .background(AlbumDesignTokens.GlassFill)
      .border(
        width = if (isFocused) 2.dp else 1.dp,
        color = if (isFocused) AlbumDesignTokens.Accent else AlbumDesignTokens.Border,
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
        indication = ripple(bounded = false, radius = 22.dp, color = AlbumDesignTokens.Text),
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
      modifier = Modifier.size(20.dp)
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
  modifier: Modifier = Modifier
) {
  val buttonShape = CircleShape
  val playInteraction = remember { MutableInteractionSource() }
  val shuffleInteraction = remember { MutableInteractionSource() }
  val playFocused by playInteraction.collectIsFocusedAsState()
  val shuffleFocused by shuffleInteraction.collectIsFocusedAsState()

  val playLabel = stringResource(id = R.string.album_action_play)
  val shuffleLabel = stringResource(id = R.string.album_action_shuffle)

  Row(
    modifier = modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    Surface(
      shape = buttonShape,
      color = Color.White,
      border = if (playFocused) BorderStroke(2.dp, accentColor) else null,
      modifier = Modifier
        .weight(1f)
        .height(56.dp)
        .defaultMinSize(minHeight = 48.dp)
        .clip(buttonShape)
        .clickable(
          enabled = enabled,
          interactionSource = playInteraction,
          indication = ripple(color = AlbumDesignTokens.Background),
          role = Role.Button,
          onClickLabel = playLabel,
          onClick = onPlayClick
        )
        .testTag("album_play_button")
    ) {
      Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Icon(
          imageVector = Icons.Rounded.PlayArrow,
          contentDescription = null,
          tint = AlbumDesignTokens.Background,
          modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
          text = playLabel,
          style = MaterialTheme.typography.bodyLarge.copy(
            fontFamily = AlbumDesignTokens.OutfitFontFamily,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold
          ),
          color = AlbumDesignTokens.Background,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
      }
    }

    val shuffleContainer = if (isShuffleEnabled) {
      accentColor.copy(alpha = 0.20f)
    } else {
      AlbumDesignTokens.GlassFill
    }
    val shuffleBorderColor = when {
      shuffleFocused -> accentColor
      isShuffleEnabled -> accentColor.copy(alpha = 0.48f)
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
        .height(56.dp)
        .defaultMinSize(minHeight = 48.dp)
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
            fontFamily = AlbumDesignTokens.OutfitFontFamily,
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
  modifier: Modifier = Modifier
) {
  val rowShape = RoundedCornerShape(20.dp)
  val rowInteraction = remember { MutableInteractionSource() }
  val rowFocused by rowInteraction.collectIsFocusedAsState()

  val menuInteraction = remember { MutableInteractionSource() }
  val menuFocused by menuInteraction.collectIsFocusedAsState()
  var menuExpanded by remember { mutableStateOf(false) }

  val rowBg by animateColorAsState(
    targetValue = if (isCurrentTrack) accentColor.copy(alpha = 0.14f) else Color.Transparent,
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
      .height(72.dp)
      .clip(rowShape)
      .background(rowBg)
      .then(
        when {
          rowFocused -> Modifier.border(2.dp, accentColor, rowShape)
          isCurrentTrack -> Modifier
            .border(1.dp, accentColor.copy(alpha = 0.30f), rowShape)
            .drawBehind {
              val stroke = 1.dp.toPx()
              if (size.width <= stroke || size.height <= stroke) return@drawBehind
              val r = 20.dp.toPx()
              drawRoundRect(
                brush = Brush.verticalGradient(
                  colors = listOf(Color.White.copy(alpha = 0.20f), Color.Transparent),
                  startY = 0f,
                  endY = size.height * 0.45f
                ),
                topLeft = Offset(stroke * 0.5f, stroke * 0.5f),
                size = Size(size.width - stroke, size.height - stroke),
                cornerRadius = CornerRadius(r, r),
                style = Stroke(width = stroke)
              )
            }
          else -> Modifier
        }
      )
      .clickable(
        interactionSource = rowInteraction,
        indication = ripple(color = AlbumDesignTokens.Text),
        role = Role.Button,
        onClickLabel = track.title,
        onClick = onClick
      )
      .padding(start = 14.dp, end = 6.dp)
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
          fontFamily = AlbumDesignTokens.OutfitFontFamily,
          fontSize = 18.sp,
          fontWeight = FontWeight.Medium,
          lineHeight = 23.sp
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
            fontFamily = AlbumDesignTokens.OutfitFontFamily,
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
          .sizeIn(minWidth = 44.dp, minHeight = 44.dp)
          .clip(menuShape)
          .then(
            if (menuFocused) Modifier.border(2.dp, accentColor, menuShape)
            else Modifier
          )
          .clickable(
            interactionSource = menuInteraction,
            indication = ripple(bounded = false, radius = 22.dp, color = AlbumDesignTokens.Text),
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

@Preview(name = "Album Section - Playing", showBackground = true, backgroundColor = 0xFF0E1120)
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

@Preview(name = "Album Section - Paused", showBackground = true, backgroundColor = 0xFF0E1120)
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
