package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import coil.compose.AsyncImage
import com.example.R
import com.example.model.APP_ICON_ARTWORK
import com.example.model.AlbumInfo
import com.example.model.Song

@Composable
fun LibraryScreen(
  localSongs: List<Song>,
  favorites: List<Song>,
  recentlyPlayed: List<Song>,
  albums: List<AlbumInfo> = emptyList(),
  currentSong: Song?,
  isPlaying: Boolean,
  darkTheme: Boolean = false,
  reducedMotion: Boolean,
  verifiedLyricsIds: Set<String> = emptySet(),
  selectedTab: Int = 0,
  onTabSelected: (Int) -> Unit = {},
  onCheckLyrics: (Song) -> Unit = {},
  onAddLocalSong: (Uri) -> Unit,
  onAddLocalSongs: ((List<Uri>) -> Unit)? = null,
  onCustomizeSongPhoto: (String, Uri) -> Unit,
  onDeleteLocalSong: (Song) -> Unit = {},
  onRemoveRecentSong: (Song) -> Unit = {},
  onClearRecentHistory: () -> Unit = {},
  onOpenInsights: () -> Unit = {},
  onAlbumClick: (AlbumInfo) -> Unit = {},
  onSongClick: (Song) -> Unit,
  onFavoriteClick: (Song) -> Unit
) {
  var pendingPhotoSongId by remember { mutableStateOf<String?>(null) }
  var songPendingDelete by remember { mutableStateOf<Song?>(null) }

  val audioLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
    if (uris.isNotEmpty()) {
      val batchHandler = onAddLocalSongs
      if (batchHandler != null) {
        batchHandler(uris)
      } else {
        uris.forEach { onAddLocalSong(it) }
      }
    }
  }

  val photoPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { imageUri ->
    val targetId = pendingPhotoSongId
    pendingPhotoSongId = null
    if (imageUri != null && targetId != null) {
      onCustomizeSongPhoto(targetId, imageUri)
    }
  }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .padding(horizontal = 20.dp)
      .windowInsetsPadding(WindowInsets.statusBars),
  ) {
    Spacer(modifier = Modifier.height(16.dp))
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
        Text(
          text = stringResource(id = R.string.library_title),
          style = MaterialTheme.typography.displayLarge.copy(fontSize = 28.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold),
          color = MaterialTheme.colorScheme.onBackground
        )
        Text(
          text = stringResource(id = R.string.library_subtitle),
          style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
      }
      when {
        selectedTab == 0 || selectedTab == 3 -> {
          Button(
            onClick = { audioLauncher.launch(arrayOf("audio/*")) },
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            modifier = Modifier.testTag("library_add_local_button")
          ) {
            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(stringResource(id = R.string.library_add_local_short), fontWeight = FontWeight.SemiBold)
          }
        }
        selectedTab == 2 && recentlyPlayed.isNotEmpty() -> {
          Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(
              onClick = onOpenInsights,
              shape = CircleShape,
              contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
              modifier = Modifier.testTag("library_open_insights_button")
            ) {
              Text(stringResource(id = R.string.insights_pill_title), fontWeight = FontWeight.SemiBold)
            }
            OutlinedButton(
              onClick = onClearRecentHistory,
              shape = CircleShape,
              border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.45f)),
              colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
              modifier = Modifier.testTag("library_clear_recent_button")
            ) {
              Icon(Icons.Rounded.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(modifier = Modifier.width(4.dp))
              Text(stringResource(id = R.string.library_clear), fontWeight = FontWeight.SemiBold)
            }
          }
        }
      }
    }

    Spacer(modifier = Modifier.height(16.dp))

    GlassSegmentedBar(
      options = listOf(
        stringResource(id = R.string.library_tab_my_songs) to null,
        stringResource(id = R.string.library_tab_favorites) to null,
        stringResource(id = R.string.library_tab_recent) to null,
        stringResource(id = R.string.library_tab_albums) to null
      ),
      selectedIndex = selectedTab,
      darkTheme = darkTheme,
      onSelect = onTabSelected,
      testTags = listOf(
        "library_tab_songs",
        "library_tab_favorites",
        "library_tab_recent",
        "library_tab_albums"
      ),
      reducedMotion = reducedMotion
    )

    Spacer(modifier = Modifier.height(14.dp))

    AnimatedContent(
      targetState = selectedTab,
      modifier = Modifier.weight(1f).fillMaxWidth(),
      transitionSpec = {
        if (reducedMotion) {
          fadeIn(snap()) togetherWith fadeOut(snap())
        } else {
          val ease = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1.0f)
          val forward = targetState >= initialState
          (fadeIn(tween(240, easing = ease)) +
            slideInHorizontally(tween(260, easing = ease)) { w -> if (forward) w / 10 else -w / 10 }) togetherWith
            (fadeOut(tween(170)) +
              slideOutHorizontally(tween(220, easing = ease)) { w -> if (forward) -w / 14 else w / 14 })
        }
      },
      label = "library_tab_content"
    ) { activeTab ->
      if (activeTab == 3) {
        if (albums.isEmpty()) {
        Box(
          modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 110.dp),
          contentAlignment = Alignment.Center
        ) {
          GlassCard(
            darkTheme = darkTheme,
            cornerRadius = 26.dp,
            modifier = Modifier.fillMaxWidth()
          ) {
            Column(
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 28.dp),
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
              Box(
                modifier = Modifier
                  .size(56.dp)
                  .clip(CircleShape)
                  .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
              ) {
                Icon(
                  imageVector = Icons.Rounded.Album,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.primary,
                  modifier = Modifier.size(28.dp)
                )
              }
              Text(
                text = stringResource(id = R.string.library_empty_albums_title),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, lineHeight = 24.sp),
                color = MaterialTheme.colorScheme.onSurface
              )
              Text(
                text = stringResource(id = R.string.library_empty_albums_sub),
                style = MaterialTheme.typography.bodySmall.copy(lineHeight = 19.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
              )
            }
          }
        }
      } else {
        val albumRows = remember(albums) { albums.chunked(2) }
        LazyColumn(
          modifier = Modifier
            .fillMaxSize()
            .testTag("library_album_grid"),
          verticalArrangement = Arrangement.spacedBy(14.dp),
          contentPadding = PaddingValues(bottom = if (currentSong != null) 195.dp else 125.dp)
        ) {
          items(albumRows, key = { row -> row.joinToString("_") { "${it.albumId}_${it.coverUrl}" } }) { rowAlbums ->
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
              rowAlbums.forEach { album ->
                LibraryAlbumGridCard(
                  album = album,
                  darkTheme = darkTheme,
                  onClick = { onAlbumClick(album) },
                  modifier = Modifier.weight(1f)
                )
              }
              if (rowAlbums.size == 1) {
                Spacer(modifier = Modifier.weight(1f))
              }
            }
          }
        }
      }
    } else {
      val currentList = when (activeTab) {
        0 -> localSongs
        1 -> favorites
        else -> recentlyPlayed
      }

      if (currentList.isEmpty()) {
        Box(
          modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 110.dp),
          contentAlignment = Alignment.Center
        ) {
          GlassCard(
            darkTheme = darkTheme,
            cornerRadius = 26.dp,
            modifier = Modifier.fillMaxWidth()
          ) {
            Column(
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 28.dp),
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
              val (emptyIcon, emptyTitle, emptyMsg) = when (activeTab) {
                0 -> Triple(
                  Icons.Rounded.LibraryMusic,
                  stringResource(id = R.string.library_empty_local_title),
                  stringResource(id = R.string.library_empty_local_sub)
                )
                1 -> Triple(
                  Icons.Rounded.FavoriteBorder,
                  stringResource(id = R.string.library_empty_fav_title),
                  stringResource(id = R.string.library_empty_fav_sub)
                )
                else -> Triple(
                  Icons.Rounded.History,
                  stringResource(id = R.string.library_empty_recent_title),
                  stringResource(id = R.string.library_empty_recent_sub)
                )
              }

              Box(
                modifier = Modifier
                  .size(56.dp)
                  .clip(CircleShape)
                  .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
              ) {
                Icon(
                  imageVector = emptyIcon,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.primary,
                  modifier = Modifier.size(28.dp)
                )
              }

              Text(
                text = emptyTitle,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, lineHeight = 24.sp),
                color = MaterialTheme.colorScheme.onSurface
              )
              Text(
                text = emptyMsg,
                style = MaterialTheme.typography.bodySmall.copy(lineHeight = 19.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
              )

              if (activeTab == 0) {
                Spacer(modifier = Modifier.height(4.dp))
                Button(
                  onClick = { audioLauncher.launch(arrayOf("audio/*")) },
                  shape = CircleShape
                ) {
                  Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                  Spacer(modifier = Modifier.width(6.dp))
                  Text(stringResource(id = R.string.library_add_audio_file), fontWeight = FontWeight.SemiBold)
                }
              }
            }
          }
        }
      } else {
        LazyColumn(
          modifier = Modifier.fillMaxSize(),
          verticalArrangement = Arrangement.spacedBy(4.dp),
          contentPadding = PaddingValues(bottom = if (currentSong != null) 195.dp else 125.dp)
        ) {
          if (activeTab == 0) {
            item(key = "local_cover_tip") {
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(bottom = 8.dp)
                  .clip(RoundedCornerShape(14.dp))
                  .background(MaterialTheme.colorScheme.primary.copy(alpha = if (darkTheme) 0.14f else 0.10f))
                  .border(1.dp, Color.White.copy(alpha = if (darkTheme) 0.16f else 0.70f), RoundedCornerShape(14.dp))
                  .padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                Icon(
                  imageVector = Icons.Rounded.AddPhotoAlternate,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.primary,
                  modifier = Modifier.size(18.dp)
                )
                Text(
                  text = stringResource(id = R.string.library_cover_tip),
                  style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium, lineHeight = 18.sp),
                  color = MaterialTheme.colorScheme.onSurface
                )
              }
            }
          }

          items(currentList, key = { "${activeTab}_${it.id}_${it.artworkUrl}" }) { song ->
            SongRow(
              song = song,
              isPlaying = isPlaying,
              isCurrent = currentSong?.id == song.id,
              reducedMotion = reducedMotion,
              darkTheme = darkTheme,
              hasLyrics = song.id in verifiedLyricsIds,
              onCheckLyrics = onCheckLyrics,
              onSongClick = { onSongClick(song) },
              onFavoriteClick = { onFavoriteClick(song) },
              onEditPhotoClick = if (activeTab == 0) {
                {
                  pendingPhotoSongId = song.id
                  photoPickerLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                  )
                }
              } else {
                null
              },
              onDeleteClick = when (activeTab) {
                0 -> { { songPendingDelete = song } }
                1 -> { { onFavoriteClick(song) } }
                else -> { { onRemoveRecentSong(song) } }
              }
            )
          }
        }
      }
      }
    }
  }

  songPendingDelete?.let { target ->
    AlertDialog(
      onDismissRequest = { songPendingDelete = null },
      icon = {
        Icon(
          imageVector = Icons.Rounded.DeleteOutline,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.error
        )
      },
      title = {
        Text(
          text = stringResource(id = R.string.details_delete_title),
          style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, lineHeight = 24.sp)
        )
      },
      text = {
        Text(
          text = stringResource(id = R.string.library_delete_confirm_msg, target.title, target.artist),
          style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp)
        )
      },
      confirmButton = {
        Button(
          onClick = {
            songPendingDelete = null
            onDeleteLocalSong(target)
          },
          colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
          shape = CircleShape,
          modifier = Modifier.testTag("confirm_delete_song_button")
        ) {
          Text(stringResource(id = R.string.action_delete), color = Color.White, fontWeight = FontWeight.Bold)
        }
      },
      dismissButton = {
        TextButton(
          onClick = { songPendingDelete = null },
          modifier = Modifier.testTag("cancel_delete_song_button")
        ) {
          Text(stringResource(id = R.string.action_cancel))
        }
      }
    )
  }
}

@Composable
private fun LibraryAlbumGridCard(
  album: AlbumInfo,
  darkTheme: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  val cardShape = RoundedCornerShape(24.dp)
  val coverShape = RoundedCornerShape(20.dp)
  val coverUrl = album.coverUrl

  Column(
    modifier = modifier
      .clip(cardShape)
      .background(
        if (darkTheme) Color(0xFF172034).copy(alpha = 0.68f)
        else Color.White.copy(alpha = 0.78f)
      )
      .border(
        width = 1.dp,
        color = Color.White.copy(alpha = if (darkTheme) 0.16f else 0.90f),
        shape = cardShape
      )
      .clickable(
        role = Role.Button,
        onClickLabel = album.title,
        onClick = onClick
      )
      .padding(10.dp)
      .testTag("library_album_card_${album.albumId}"),
    verticalArrangement = Arrangement.spacedBy(8.dp)
  ) {
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .aspectRatio(1f)
        .clip(coverShape)
        .background(
          Brush.linearGradient(
            colors = listOf(Color(0xFF7B61FF), Color(0xFF2D1E69))
          )
        ),
      contentAlignment = Alignment.Center
    ) {
      when {
        coverUrl == APP_ICON_ARTWORK -> {
          Image(
            painter = painterResource(id = R.drawable.ic_launcher_foreground),
            contentDescription = album.title,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(0.84f)
          )
        }
        !coverUrl.isNullOrBlank() -> {
          AsyncImage(
            model = coverUrl,
            contentDescription = album.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
          )
        }
        else -> {
          Icon(
            imageVector = Icons.Rounded.MusicNote,
            contentDescription = album.title,
            tint = Color.White.copy(alpha = 0.88f),
            modifier = Modifier.size(36.dp)
          )
        }
      }
    }

    Column(
      modifier = Modifier.padding(horizontal = 2.dp),
      verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
      Text(
        text = album.title,
        style = MaterialTheme.typography.titleSmall.copy(
          fontWeight = FontWeight.SemiBold,
          lineHeight = 19.sp
        ),
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
      Text(
        text = album.artist,
        style = MaterialTheme.typography.bodySmall.copy(lineHeight = 16.sp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
      val countLabel = if (album.tracks.size == 1) {
        stringResource(id = R.string.album_meta_single)
      } else {
        stringResource(id = R.string.album_meta_plural, album.tracks.size)
      }
      Text(
        text = countLabel,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
    }
  }
}

