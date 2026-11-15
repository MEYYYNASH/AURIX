package com.example.ui.screens

import android.provider.Settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.LibraryAdd
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.R
import com.example.model.Song
import com.example.ui.theme.AurixTheme
import java.util.Locale

object AlbumDesignTokens {
  val Background = Color(0xFF0D1019)
  val Surface = Color(0xFF161A26)
  val RaisedSurface = Color(0xFF1F2433)
  val Text = Color(0xFFF4F6FB)
  val DimText = Color(0xFF9BA4B9)
  val Accent = Color(0xFFC4B5FF)
  val Border = Color.White.copy(alpha = 0.08f)
  val SidePadding = 20.dp
  val CoverGradientStart = Color(0xFF7B61FF)
  val CoverGradientEnd = Color(0xFF2D1E69)
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
  coverUrl: String? = null,
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
  modifier: Modifier = Modifier
) {
  val effectiveReducedMotion = rememberEffectiveReducedMotion(reducedMotion)
  val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
  val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
  val dockClearanceBottom = (if (hasCurrentSong) 205.dp else 135.dp) + navBarBottom

  var showAlbumMenu by remember { mutableStateOf(false) }

  Box(
    modifier = modifier
      .fillMaxSize()
      .background(AlbumDesignTokens.Background)
  ) {
    LazyColumn(
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
          albumTitle = albumTitle,
          artistName = artistName,
          trackCount = tracks.size,
          coverUrl = coverUrl
        )
        Spacer(modifier = Modifier.height(16.dp))
      }

      item(key = "album_actions") {
        Box {
          ActionButtons(
            isAddedToLibrary = isAddedToLibrary,
            isAlbumDownloaded = isAlbumDownloaded,
            onAddToLibraryClick = onAddToLibraryClick,
            onDownloadClick = onDownloadAlbumClick,
            onMoreClick = { showAlbumMenu = true }
          )
          DropdownMenu(
            expanded = showAlbumMenu,
            onDismissRequest = { showAlbumMenu = false },
            containerColor = AlbumDesignTokens.RaisedSurface
          ) {
            DropdownMenuItem(
              text = {
                Text(
                  text = stringResource(id = R.string.album_action_play),
                  color = AlbumDesignTokens.Text,
                  fontFamily = MaterialTheme.typography.bodyMedium.fontFamily
                )
              },
              onClick = {
                showAlbumMenu = false
                onPlayClick()
              }
            )
            DropdownMenuItem(
              text = {
                Text(
                  text = stringResource(id = R.string.album_action_shuffle),
                  color = AlbumDesignTokens.Text,
                  fontFamily = MaterialTheme.typography.bodyMedium.fontFamily
                )
              },
              onClick = {
                showAlbumMenu = false
                onShuffleClick()
              }
            )
            DropdownMenuItem(
              text = {
                Text(
                  text = if (isAddedToLibrary) {
                    stringResource(id = R.string.album_remove_from_library)
                  } else {
                    stringResource(id = R.string.album_add_to_library)
                  },
                  color = AlbumDesignTokens.Text,
                  fontFamily = MaterialTheme.typography.bodyMedium.fontFamily
                )
              },
              onClick = {
                showAlbumMenu = false
                onAddToLibraryClick()
              }
            )
            DropdownMenuItem(
              text = {
                Text(
                  text = stringResource(id = R.string.album_download),
                  color = AlbumDesignTokens.Text,
                  fontFamily = MaterialTheme.typography.bodyMedium.fontFamily
                )
              },
              onClick = {
                showAlbumMenu = false
                onDownloadAlbumClick()
              }
            )
          }
        }
        Spacer(modifier = Modifier.height(14.dp))
      }

      item(key = "album_play_shuffle") {
        PlayShuffleRow(
          isShuffleEnabled = isShuffleEnabled,
          enabled = tracks.isNotEmpty(),
          onPlayClick = onPlayClick,
          onShuffleClick = onShuffleClick
        )
        Spacer(modifier = Modifier.height(18.dp))
      }

      itemsIndexed(
        items = tracks,
        key = { _, song -> song.id }
      ) { index, song ->
        val isCurrent = currentSong?.id == song.id
        TrackRow(
          trackNumber = index + 1,
          track = song,
          isCurrentTrack = isCurrent,
          isPlaying = isCurrent && isPlaying,
          reducedMotion = effectiveReducedMotion,
          onClick = { onTrackClick(song) },
          onToggleFavorite = { onTrackFavoriteClick(song) },
          onDownloadTrack = { onTrackDownloadClick(song) },
          onTrackDetails = { onTrackDetailsClick(song) }
        )
        if (index < tracks.lastIndex) {
          Spacer(modifier = Modifier.height(2.dp))
        }
      }
    }
  }
}

@Composable
fun AlbumHeader(
  albumTitle: String,
  artistName: String,
  trackCount: Int,
  coverUrl: String? = null,
  modifier: Modifier = Modifier
) {
  val coverShape = RoundedCornerShape(26.dp)
  var imageFailed by remember(coverUrl) { mutableStateOf(false) }

  Row(
    modifier = modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(16.dp)
  ) {
    Box(
      modifier = Modifier
        .size(136.dp)
        .shadow(
          elevation = 18.dp,
          shape = coverShape,
          ambientColor = Color.Black.copy(alpha = 0.55f),
          spotColor = AlbumDesignTokens.CoverGradientStart.copy(alpha = 0.35f)
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
      Icon(
        imageVector = Icons.Rounded.MusicNote,
        contentDescription = albumTitle,
        tint = AlbumDesignTokens.Text.copy(alpha = 0.88f),
        modifier = Modifier.size(46.dp)
      )

      val resolvedModel = remember(coverUrl) {
        coverUrl?.takeIf { it.isNotBlank() }?.let { resolveArtworkModel(it) }
      }
      if (resolvedModel != null && !imageFailed) {
        AsyncImage(
          model = resolvedModel,
          contentDescription = albumTitle,
          contentScale = ContentScale.Crop,
          onError = { imageFailed = true },
          modifier = Modifier.fillMaxSize()
        )
      }
    }

    Column(
      modifier = Modifier.weight(1f),
      verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
      val metaText = if (trackCount == 1) {
        stringResource(id = R.string.album_meta_single)
      } else {
        stringResource(id = R.string.album_meta_plural, trackCount)
      }
      Text(
        text = metaText,
        style = MaterialTheme.typography.bodySmall.copy(
          fontSize = 13.sp,
          lineHeight = 18.sp,
          fontWeight = FontWeight.Normal
        ),
        color = AlbumDesignTokens.DimText,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
      Text(
        text = albumTitle,
        style = MaterialTheme.typography.displayLarge.copy(
          fontSize = 30.sp,
          lineHeight = 35.sp,
          fontWeight = FontWeight.Bold
        ),
        color = AlbumDesignTokens.Text,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
      )
      Text(
        text = artistName,
        style = MaterialTheme.typography.bodyLarge.copy(
          fontSize = 16.sp,
          lineHeight = 22.sp,
          fontWeight = FontWeight.Medium
        ),
        color = AlbumDesignTokens.Accent,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
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
  onMoreClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  Row(
    modifier = modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    AlbumHeaderIconButton(
      icon = if (isAddedToLibrary) Icons.Rounded.Check else Icons.Rounded.LibraryAdd,
      contentDescription = if (isAddedToLibrary) {
        stringResource(id = R.string.album_remove_from_library)
      } else {
        stringResource(id = R.string.album_add_to_library)
      },
      tint = if (isAddedToLibrary) AlbumDesignTokens.Accent else AlbumDesignTokens.DimText,
      onClick = onAddToLibraryClick,
      testTag = "album_add_library_button"
    )
    AlbumHeaderIconButton(
      icon = if (isAlbumDownloaded) Icons.Rounded.DownloadDone else Icons.Rounded.Download,
      contentDescription = stringResource(id = R.string.album_download),
      tint = if (isAlbumDownloaded) AlbumDesignTokens.Accent else AlbumDesignTokens.DimText,
      onClick = onDownloadClick,
      testTag = "album_download_button"
    )
    AlbumHeaderIconButton(
      icon = Icons.Rounded.MoreHoriz,
      contentDescription = stringResource(id = R.string.album_more_options),
      tint = AlbumDesignTokens.DimText,
      onClick = onMoreClick,
      testTag = "album_more_button"
    )
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
  val focusShape = RoundedCornerShape(10.dp)

  Box(
    modifier = Modifier
      .sizeIn(minWidth = 44.dp, minHeight = 44.dp)
      .clip(focusShape)
      .then(
        if (isFocused) Modifier.border(2.dp, AlbumDesignTokens.Accent, focusShape)
        else Modifier
      )
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
    Box(
      modifier = Modifier.size(40.dp),
      contentAlignment = Alignment.Center
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = tint,
        modifier = Modifier.size(22.dp)
      )
    }
  }
}

@Composable
fun PlayShuffleRow(
  isShuffleEnabled: Boolean,
  enabled: Boolean = true,
  onPlayClick: () -> Unit,
  onShuffleClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  val buttonShape = RoundedCornerShape(16.dp)
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
      color = AlbumDesignTokens.Text,
      border = if (playFocused) BorderStroke(2.dp, AlbumDesignTokens.Accent) else null,
      modifier = Modifier
        .weight(1f)
        .height(52.dp)
        .defaultMinSize(minHeight = 44.dp)
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
      AlbumDesignTokens.RaisedSurface
    } else {
      AlbumDesignTokens.Surface
    }
    val shuffleBorderColor = when {
      shuffleFocused -> AlbumDesignTokens.Accent
      isShuffleEnabled -> AlbumDesignTokens.Accent.copy(alpha = 0.45f)
      else -> AlbumDesignTokens.Border
    }
    val shuffleContentColor = if (isShuffleEnabled) {
      AlbumDesignTokens.Accent
    } else {
      AlbumDesignTokens.Text
    }

    Surface(
      shape = buttonShape,
      color = shuffleContainer,
      border = BorderStroke(if (shuffleFocused) 2.dp else 1.dp, shuffleBorderColor),
      modifier = Modifier
        .weight(1f)
        .height(52.dp)
        .defaultMinSize(minHeight = 44.dp)
        .clip(buttonShape)
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
  reducedMotion: Boolean = false,
  onClick: () -> Unit,
  onToggleFavorite: () -> Unit = {},
  onDownloadTrack: () -> Unit = {},
  onTrackDetails: () -> Unit = {},
  modifier: Modifier = Modifier
) {
  val rowShape = RoundedCornerShape(16.dp)
  val rowInteraction = remember { MutableInteractionSource() }
  val rowFocused by rowInteraction.collectIsFocusedAsState()

  val menuInteraction = remember { MutableInteractionSource() }
  val menuFocused by menuInteraction.collectIsFocusedAsState()
  var menuExpanded by remember { mutableStateOf(false) }

  val rowBg by animateColorAsState(
    targetValue = if (isCurrentTrack) AlbumDesignTokens.RaisedSurface else Color.Transparent,
    animationSpec = if (reducedMotion) snap() else tween(durationMillis = 200),
    label = "track_row_bg_${track.id}"
  )
  val titleColor = if (isCurrentTrack) AlbumDesignTokens.Accent else AlbumDesignTokens.Text

  Row(
    modifier = modifier
      .fillMaxWidth()
      .height(64.dp)
      .clip(rowShape)
      .background(rowBg)
      .then(
        when {
          rowFocused -> Modifier.border(2.dp, AlbumDesignTokens.Accent, rowShape)
          isCurrentTrack -> Modifier.border(1.dp, AlbumDesignTokens.Border, rowShape)
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
      .padding(start = 12.dp, end = 4.dp)
      .testTag("album_track_row_${track.id}"),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Box(
      modifier = Modifier.width(30.dp),
      contentAlignment = Alignment.Center
    ) {
      if (isCurrentTrack) {
        AlbumEqualizerBars(
          isPlaying = isPlaying,
          color = AlbumDesignTokens.Accent,
          reducedMotion = reducedMotion
        )
      } else {
        Text(
          text = trackNumber.toString(),
          style = MaterialTheme.typography.bodyMedium.copy(
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
          ),
          color = AlbumDesignTokens.DimText,
          textAlign = TextAlign.Center,
          maxLines = 1
        )
      }
    }

    Spacer(modifier = Modifier.width(12.dp))

    Text(
      text = track.title,
      style = MaterialTheme.typography.bodyLarge.copy(
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 22.sp
      ),
      color = titleColor,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier.weight(1f)
    )

    Spacer(modifier = Modifier.width(12.dp))

    Text(
      text = formatTrackDuration(track.durationSeconds),
      style = MaterialTheme.typography.bodySmall.copy(
        fontSize = 13.sp,
        fontWeight = FontWeight.Normal
      ),
      color = AlbumDesignTokens.DimText,
      textAlign = TextAlign.End,
      maxLines = 1
    )

    Spacer(modifier = Modifier.width(4.dp))

    Box {
      val menuCd = stringResource(id = R.string.album_track_more_cd, track.title)
      val menuShape = RoundedCornerShape(10.dp)
      Box(
        modifier = Modifier
          .sizeIn(minWidth = 44.dp, minHeight = 44.dp)
          .clip(menuShape)
          .then(
            if (menuFocused) Modifier.border(2.dp, AlbumDesignTokens.Accent, menuShape)
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

      DropdownMenu(
        expanded = menuExpanded,
        onDismissRequest = { menuExpanded = false },
        containerColor = AlbumDesignTokens.RaisedSurface
      ) {
        DropdownMenuItem(
          text = {
            Text(
              text = if (isCurrentTrack && isPlaying) {
                stringResource(id = R.string.album_track_pause)
              } else {
                stringResource(id = R.string.album_action_play)
              },
              color = AlbumDesignTokens.Text,
              fontFamily = MaterialTheme.typography.bodyMedium.fontFamily
            )
          },
          onClick = {
            menuExpanded = false
            onClick()
          }
        )
        DropdownMenuItem(
          text = {
            Text(
              text = if (track.isFavorite) {
                stringResource(id = R.string.album_track_unfavorite)
              } else {
                stringResource(id = R.string.album_track_favorite)
              },
              color = AlbumDesignTokens.Text,
              fontFamily = MaterialTheme.typography.bodyMedium.fontFamily
            )
          },
          onClick = {
            menuExpanded = false
            onToggleFavorite()
          }
        )
        DropdownMenuItem(
          text = {
            Text(
              text = stringResource(id = R.string.album_track_download),
              color = AlbumDesignTokens.Text,
              fontFamily = MaterialTheme.typography.bodyMedium.fontFamily
            )
          },
          onClick = {
            menuExpanded = false
            onDownloadTrack()
          }
        )
        DropdownMenuItem(
          text = {
            Text(
              text = stringResource(id = R.string.album_track_details),
              color = AlbumDesignTokens.Text,
              fontFamily = MaterialTheme.typography.bodyMedium.fontFamily
            )
          },
          onClick = {
            menuExpanded = false
            onTrackDetails()
          }
        )
      }
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
    modifier = modifier.height(14.dp),
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
  val durations = listOf(192L, 218L, 174L, 245L, 203L, 189L, 231L, 260L)
  return durations.mapIndexed { idx, dur ->
    Song(
      id = "album_preview_${idx + 1}",
      title = "Track Title ${idx + 1}",
      artist = "Artist Name",
      artworkUrl = "",
      durationSeconds = dur,
      streamUrl = "https://example.com/track_${idx + 1}.m4a",
      source = "Album",
      fullDurationSeconds = dur
    )
  }
}

@Preview(name = "Album Section - Playing", showBackground = true, backgroundColor = 0xFF0D1019)
@Composable
private fun AlbumSectionPlayingPreview() {
  val tracks = remember { previewPlaceholderTracks() }
  AurixTheme(darkTheme = true) {
    AlbumTrackListSection(
      albumTitle = "Album Title",
      artistName = "Artist Name",
      coverUrl = null,
      tracks = tracks,
      currentSong = tracks[1],
      isPlaying = true,
      isShuffleEnabled = false,
      isAddedToLibrary = false
    )
  }
}

@Preview(name = "Album Section - Paused", showBackground = true, backgroundColor = 0xFF0D1019)
@Composable
private fun AlbumSectionPausedPreview() {
  val tracks = remember { previewPlaceholderTracks() }
  AurixTheme(darkTheme = true) {
    AlbumTrackListSection(
      albumTitle = "Album Title",
      artistName = "Artist Name",
      coverUrl = null,
      tracks = tracks,
      currentSong = tracks[0],
      isPlaying = false,
      isShuffleEnabled = true,
      isAddedToLibrary = true
    )
  }
}
