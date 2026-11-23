package com.example.ui.screens

import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.NightlightRound
import androidx.compose.material.icons.rounded.Piano
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Whatshot
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import coil.compose.AsyncImage
import com.example.R
import com.example.model.APP_ICON_ARTWORK
import com.example.model.AlbumInfo
import com.example.model.Song
import com.example.model.computeAlbumId
import com.example.recommendation.TrackMetadataClassifier
import java.util.Locale
import kotlinx.coroutines.launch

internal const val GENRE_FILTER_ALL = "ALL"

internal val CANONICAL_LIBRARY_GENRES = listOf(
  "Pop",
  "Lo-fi",
  "Phonk",
  "R&B",
  "Hip-Hop",
  "Electronic",
  "Indie",
  "Rock",
  "Instrumental"
)

internal fun resolveLibraryTrackGenre(song: Song): String {
  val raw = song.genre.trim().ifBlank { TrackMetadataClassifier.enrich(song).genre.trim() }
  val lower = raw.lowercase(Locale.ROOT)
  return when {
    lower.contains("lo-fi") || lower.contains("lofi") || lower.contains("chillhop") || lower.contains("ambient") -> "Lo-fi"
    lower.contains("phonk") || lower.contains("drift") || lower.contains("montagem") -> "Phonk"
    lower.contains("r&b") || lower.contains("rnb") || lower.contains("soul") -> "R&B"
    lower.contains("hip-hop") || lower.contains("hip hop") || lower.contains("rap") || lower.contains("drill") -> "Hip-Hop"
    lower.contains("electronic") || lower.contains("edm") || lower.contains("dance") || lower.contains("house") || lower.contains("techno") -> "Electronic"
    lower.contains("indie") || lower.contains("acoustic") || lower.contains("folk") -> "Indie"
    lower.contains("rock") || lower.contains("metal") || lower.contains("punk") || lower.contains("alternative") -> "Rock"
    lower.contains("instrumental") || lower.contains("classical") || lower.contains("jazz") || lower.contains("piano") -> "Instrumental"
    lower.contains("pop") -> "Pop"
    raw.isNotBlank() -> raw.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
    else -> "Pop"
  }
}

internal fun libraryGenreTagKey(genre: String): String {
  return genre.trim()
    .lowercase(Locale.ROOT)
    .replace(Regex("[^a-z0-9]+"), "_")
    .trim('_')
    .ifBlank { "other" }
}

internal fun libraryGenreIcon(genre: String): ImageVector {
  return when (genre.lowercase(Locale.ROOT)) {
    "all" -> Icons.Rounded.LibraryMusic
    "pop" -> Icons.Rounded.AutoAwesome
    "lo-fi", "lofi" -> Icons.Rounded.Headphones
    "phonk" -> Icons.Rounded.Bolt
    "r&b", "rnb" -> Icons.Rounded.NightlightRound
    "hip-hop", "hip hop" -> Icons.Rounded.GraphicEq
    "electronic" -> Icons.Rounded.Album
    "indie" -> Icons.Rounded.MusicNote
    "rock" -> Icons.Rounded.Whatshot
    "instrumental" -> Icons.Rounded.Piano
    else -> Icons.Rounded.MusicNote
  }
}

@Composable
fun LibraryScreen(
  localSongs: List<Song>,
  favorites: List<Song>,
  recentlyPlayed: List<Song>,
  albums: List<AlbumInfo> = emptyList(),
  allAvailableSongs: List<Song> = emptyList(),
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
  onCreateAlbum: (title: String, artist: String, year: String) -> Unit = { _, _, _ -> },
  onAddSongsToAlbum: (AlbumInfo, List<Song>) -> Unit = { _, _ -> },
  onImportAudioToAlbum: (List<Uri>, AlbumInfo) -> Unit = { _, _ -> },
  onDeleteAlbum: (AlbumInfo, Boolean) -> Unit = { _, _ -> },
  onAddSongToAlbumClick: ((Song) -> Unit)? = null,
  onCreateSheetVisibilityChanged: (Boolean) -> Unit = {},
  onSongClick: (Song) -> Unit,
  onFavoriteClick: (Song) -> Unit
) {
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
  val effectiveReducedMotion = reducedMotion || systemAnimatorScale <= 0f

  var pendingPhotoSongId by remember { mutableStateOf<String?>(null) }
  var songPendingDelete by remember { mutableStateOf<Song?>(null) }
  var showCreateAlbumDialog by remember { mutableStateOf(false) }
  var createBackdropFactor by remember { mutableFloatStateOf(0f) }
  var flyingCoverPayload by remember { mutableStateOf<FlyingAlbumCoverPayload?>(null) }
  var pulsingAlbumId by remember { mutableStateOf<String?>(null) }
  val albumCoverBoundsMap = remember { mutableStateMapOf<String, Rect>() }

  LaunchedEffect(showCreateAlbumDialog) {
    onCreateSheetVisibilityChanged(showCreateAlbumDialog)
  }
  androidx.compose.runtime.DisposableEffect(Unit) {
    onDispose {
      onCreateSheetVisibilityChanged(false)
    }
  }

  var albumPendingHoldOptions by remember { mutableStateOf<AlbumInfo?>(null) }
  var albumPendingDelete by remember { mutableStateOf<AlbumInfo?>(null) }
  var albumPendingAddSongs by remember { mutableStateOf<AlbumInfo?>(null) }

  val animatedBackdropFactor by animateFloatAsState(
    targetValue = if (showCreateAlbumDialog) createBackdropFactor else 0f,
    animationSpec = if (effectiveReducedMotion) snap() else tween(
      durationMillis = 320,
      easing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)
    ),
    label = "library_backdrop_factor"
  )

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

  val backdropScale = lerp(1f, 0.93f, animatedBackdropFactor)
  val backdropCorner = lerp(0f, 26f, animatedBackdropFactor).dp
  val backdropBlur = if (!effectiveReducedMotion && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    (animatedBackdropFactor * 10f).dp
  } else {
    0.dp
  }

  Box(modifier = Modifier.fillMaxSize()) {
  Column(
    modifier = Modifier
      .fillMaxSize()
      .scale(backdropScale)
      .then(if (backdropCorner > 0.5.dp) Modifier.clip(RoundedCornerShape(backdropCorner)) else Modifier)
      .then(if (backdropBlur > 0.2.dp) Modifier.blur(backdropBlur) else Modifier)
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
        selectedTab == 0 -> {
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
        selectedTab == 3 -> {
          Button(
            onClick = { showCreateAlbumDialog = true },
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            modifier = Modifier.testTag("library_create_album_button")
          ) {
            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(stringResource(id = R.string.album_new_button), fontWeight = FontWeight.SemiBold)
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
              Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Button(
                  onClick = { showCreateAlbumDialog = true },
                  shape = CircleShape,
                  modifier = Modifier.testTag("empty_create_album_button")
                ) {
                  Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                  Spacer(modifier = Modifier.width(6.dp))
                  Text(stringResource(id = R.string.album_new_button), fontWeight = FontWeight.SemiBold)
                }
                OutlinedButton(
                  onClick = { audioLauncher.launch(arrayOf("audio/*")) },
                  shape = CircleShape
                ) {
                  Text(stringResource(id = R.string.library_add_audio_file), fontWeight = FontWeight.SemiBold)
                }
              }
            }
          }
        }
      } else {
        val gridEase = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)
        LazyVerticalGrid(
          columns = GridCells.Fixed(2),
          modifier = Modifier
            .fillMaxSize()
            .testTag("library_album_grid"),
          verticalArrangement = Arrangement.spacedBy(14.dp),
          horizontalArrangement = Arrangement.spacedBy(14.dp),
          contentPadding = PaddingValues(bottom = if (currentSong != null) 195.dp else 125.dp)
        ) {
          item(
            key = "album_hold_tip",
            span = { GridItemSpan(maxLineSpan) }
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = if (darkTheme) 0.14f else 0.10f))
                .border(1.dp, Color.White.copy(alpha = if (darkTheme) 0.16f else 0.70f), RoundedCornerShape(14.dp))
                .padding(horizontal = 12.dp, vertical = 9.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              Icon(
                imageVector = Icons.Rounded.TouchApp,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
              )
              Text(
                text = stringResource(id = R.string.album_hold_tip),
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium, lineHeight = 18.sp),
                color = MaterialTheme.colorScheme.onSurface
              )
            }
          }
          items(
            items = albums,
            key = { it.albumId }
          ) { album ->
            val isFlyingTarget = flyingCoverPayload?.albumId == album.albumId
            LibraryAlbumGridCard(
              album = album,
              darkTheme = darkTheme,
              reducedMotion = effectiveReducedMotion,
              isHiddenForFlight = isFlyingTarget,
              pulseRing = pulsingAlbumId == album.albumId,
              onCoverBoundsMeasured = { rect -> albumCoverBoundsMap[album.albumId] = rect },
              onPulseFinished = {
                if (pulsingAlbumId == album.albumId) {
                  pulsingAlbumId = null
                }
              },
              onClick = { onAlbumClick(album) },
              onLongClick = { albumPendingHoldOptions = album },
              modifier = Modifier.animateItem(
                fadeInSpec = if (effectiveReducedMotion) snap() else tween(260, easing = gridEase),
                placementSpec = if (effectiveReducedMotion) snap() else tween(380, easing = gridEase),
                fadeOutSpec = if (effectiveReducedMotion) snap() else tween(200)
              )
            )
          }
        }
      }
    } else {
      val currentList = when (activeTab) {
        0 -> localSongs
        1 -> favorites
        else -> recentlyPlayed
      }

      var selectedGenre by rememberSaveable(activeTab) { mutableStateOf(GENRE_FILTER_ALL) }
      var groupByGenre by rememberSaveable(activeTab) { mutableStateOf(false) }

      val resolvedGenresBySong = remember(currentList) {
        currentList.map { song -> song to resolveLibraryTrackGenre(song) }
      }
      val genreCounts = remember(resolvedGenresBySong) {
        val counts = LinkedHashMap<String, Int>()
        resolvedGenresBySong.forEach { (_, genre) ->
          counts[genre] = (counts[genre] ?: 0) + 1
        }
        counts
      }
      val orderedGenres = remember(genreCounts) {
        val presentGenres = genreCounts.entries
          .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
          .map { it.key }
        val extras = CANONICAL_LIBRARY_GENRES.filter { it !in presentGenres }
        presentGenres + extras
      }
      val filteredPairs = remember(resolvedGenresBySong, selectedGenre) {
        if (selectedGenre == GENRE_FILTER_ALL) {
          resolvedGenresBySong
        } else {
          resolvedGenresBySong.filter { (_, genre) -> genre.equals(selectedGenre, ignoreCase = true) }
        }
      }
      val groupedPairs = remember(filteredPairs, groupByGenre) {
        if (!groupByGenre) {
          emptyList()
        } else {
          filteredPairs
            .groupBy({ it.second }, { it.first })
            .entries
            .sortedWith(compareByDescending<Map.Entry<String, List<Song>>> { it.value.size }.thenBy { it.key })
            .map { it.key to it.value }
        }
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
        Column(modifier = Modifier.fillMaxSize()) {
          LibraryGenreFilterBar(
            totalCount = currentList.size,
            orderedGenres = orderedGenres,
            genreCounts = genreCounts,
            selectedGenre = selectedGenre,
            groupByGenre = groupByGenre,
            filteredCount = filteredPairs.size,
            darkTheme = darkTheme,
            onSelectGenre = { chosen ->
              selectedGenre = if (chosen == GENRE_FILTER_ALL || selectedGenre != chosen) {
                chosen
              } else {
                GENRE_FILTER_ALL
              }
            },
            onToggleGroupByGenre = { groupByGenre = !groupByGenre },
            onPlayFilteredGenre = {
              filteredPairs.firstOrNull()?.first?.let(onSongClick)
            },
            onClearGenreFilter = { selectedGenre = GENRE_FILTER_ALL }
          )

          Spacer(modifier = Modifier.height(8.dp))

          if (filteredPairs.isEmpty()) {
            Box(
              modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 110.dp),
              contentAlignment = Alignment.Center
            ) {
              GlassCard(
                darkTheme = darkTheme,
                cornerRadius = 26.dp,
                modifier = Modifier
                  .fillMaxWidth()
                  .testTag("library_genre_empty_card")
              ) {
                Column(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 26.dp),
                  horizontalAlignment = Alignment.CenterHorizontally,
                  verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                  Box(
                    modifier = Modifier
                      .size(54.dp)
                      .clip(CircleShape)
                      .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                  ) {
                    Icon(
                      imageVector = libraryGenreIcon(selectedGenre),
                      contentDescription = null,
                      tint = MaterialTheme.colorScheme.primary,
                      modifier = Modifier.size(26.dp)
                    )
                  }
                  Text(
                    text = stringResource(id = R.string.library_genre_empty_title, selectedGenre),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, lineHeight = 24.sp),
                    color = MaterialTheme.colorScheme.onSurface
                  )
                  Text(
                    text = stringResource(id = R.string.library_genre_empty_sub, selectedGenre),
                    style = MaterialTheme.typography.bodySmall.copy(lineHeight = 19.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                  )
                  OutlinedButton(
                    onClick = { selectedGenre = GENRE_FILTER_ALL },
                    shape = CircleShape,
                    modifier = Modifier.testTag("library_reset_genre_filter_button")
                  ) {
                    Text(stringResource(id = R.string.library_genre_clear_filter), fontWeight = FontWeight.SemiBold)
                  }
                }
              }
            }
          } else {
            LazyColumn(
              modifier = Modifier.fillMaxSize(),
              verticalArrangement = Arrangement.spacedBy(8.dp),
              contentPadding = PaddingValues(bottom = if (currentSong != null) 195.dp else 125.dp)
            ) {
              if (activeTab == 0) {
                item(key = "local_cover_tip") {
                  Row(
                    modifier = Modifier
                      .fillMaxWidth()
                      .padding(bottom = 4.dp)
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

              if (groupByGenre) {
                groupedPairs.forEach { (genreName, genreSongs) ->
                  val genreKey = libraryGenreTagKey(genreName)
                  item(key = "genre_header_${activeTab}_$genreKey") {
                    LibraryGenreCategoryHeader(
                      genre = genreName,
                      trackCount = genreSongs.size,
                      isFocused = selectedGenre.equals(genreName, ignoreCase = true),
                      darkTheme = darkTheme,
                      onFilterToggle = {
                        selectedGenre = if (selectedGenre.equals(genreName, ignoreCase = true)) {
                          GENRE_FILTER_ALL
                        } else {
                          genreName
                        }
                      },
                      onPlayCategory = {
                        genreSongs.firstOrNull()?.let(onSongClick)
                      },
                      modifier = Modifier.testTag("library_genre_header_$genreKey")
                    )
                  }
                  itemsIndexed(
                    items = genreSongs,
                    key = { idx, song -> "${activeTab}_grp_${genreKey}_${idx}_${song.id}_${song.artworkUrl}" }
                  ) { _, song ->
                    SongRow(
                      song = song,
                      isPlaying = isPlaying,
                      isCurrent = currentSong?.id == song.id,
                      reducedMotion = reducedMotion,
                      darkTheme = darkTheme,
                      hasLyrics = song.id in verifiedLyricsIds,
                      genreBadge = genreName,
                      onGenreBadgeClick = {
                        selectedGenre = if (selectedGenre.equals(genreName, ignoreCase = true)) {
                          GENRE_FILTER_ALL
                        } else {
                          genreName
                        }
                      },
                      onCheckLyrics = onCheckLyrics,
                      onSongClick = { onSongClick(song) },
                      onFavoriteClick = { onFavoriteClick(song) },
                      onAddToAlbumClick = onAddSongToAlbumClick?.let { callback -> { callback(song) } },
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
              } else {
                itemsIndexed(
                  items = filteredPairs,
                  key = { idx, pair -> "${activeTab}_${idx}_${pair.first.id}_${pair.first.artworkUrl}" }
                ) { _, (song, songGenre) ->
                  SongRow(
                    song = song,
                    isPlaying = isPlaying,
                    isCurrent = currentSong?.id == song.id,
                    reducedMotion = reducedMotion,
                    darkTheme = darkTheme,
                    hasLyrics = song.id in verifiedLyricsIds,
                    genreBadge = songGenre,
                    onGenreBadgeClick = {
                      selectedGenre = if (selectedGenre.equals(songGenre, ignoreCase = true)) {
                        GENRE_FILTER_ALL
                      } else {
                        songGenre
                      }
                    },
                    onCheckLyrics = onCheckLyrics,
                    onSongClick = { onSongClick(song) },
                    onFavoriteClick = { onFavoriteClick(song) },
                    onAddToAlbumClick = onAddSongToAlbumClick?.let { callback -> { callback(song) } },
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
      }
    }
  } // End scaled Library Column

  // Dimmed dark glass scrim when Create Album sheet is open
  if (animatedBackdropFactor > 0.01f) {
    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(Color(0xFF0A0810).copy(alpha = 0.64f * animatedBackdropFactor))
    )
  }

  // Layer 2: Redesigned Dark Glass Create Album Bottom Sheet
  if (showCreateAlbumDialog) {
    CreateAlbumBottomSheetOverlay(
      visible = showCreateAlbumDialog,
      reducedMotion = effectiveReducedMotion,
      hideInlineCoverForFlight = flyingCoverPayload != null,
      onBackdropProgressChanged = { createBackdropFactor = it },
      onDismiss = {
        showCreateAlbumDialog = false
        createBackdropFactor = 0f
      },
      onCommitAndFly = { title, artist, year, preset, coverRect ->
        val resolvedArtist = artist.ifBlank { "Unknown Artist" }
        val albumId = computeAlbumId(title, resolvedArtist)
        AlbumCoverPresetStore.savePreset(context, albumId, preset)
        onCreateAlbum(title, artist, year)
        if (selectedTab != 3) {
          onTabSelected(3)
        }
        if (coverRect != null && !effectiveReducedMotion) {
          flyingCoverPayload = FlyingAlbumCoverPayload(
            albumId = albumId,
            title = title,
            preset = preset,
            sourceRect = coverRect
          )
        } else {
          pulsingAlbumId = albumId
        }
      }
    )
  }

  // Layer 3: Shared-element lifted cover flying on a curve to its grid card
  flyingCoverPayload?.let { payload ->
    FlyingAlbumCoverOverlay(
      payload = payload,
      targetRect = albumCoverBoundsMap[payload.albumId],
      reducedMotion = effectiveReducedMotion,
      onFlightFinished = {
        flyingCoverPayload = null
        pulsingAlbumId = payload.albumId
      }
    )
  }
  } // End root Box

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

  albumPendingHoldOptions?.let { heldAlbum ->
    AlbumHoldOptionsSheet(
      album = heldAlbum,
      onDismiss = { albumPendingHoldOptions = null },
      onOpenAlbum = { onAlbumClick(heldAlbum) },
      onAddSongs = { albumPendingAddSongs = heldAlbum },
      onRequestDeleteAlbum = { albumPendingDelete = heldAlbum }
    )
  }

  albumPendingDelete?.let { deleteTarget ->
    DeleteAlbumConfirmDialog(
      album = deleteTarget,
      onDismiss = { albumPendingDelete = null },
      onConfirm = { alsoDeleteLocal ->
        albumPendingDelete = null
        onDeleteAlbum(deleteTarget, alsoDeleteLocal)
      }
    )
  }

  albumPendingAddSongs?.let { targetAlbum ->
    AddSongsToAlbumSheet(
      album = targetAlbum,
      availableSongs = allAvailableSongs,
      onDismiss = { albumPendingAddSongs = null },
      onAddSongs = { chosen -> onAddSongsToAlbum(targetAlbum, chosen) },
      onImportAudioFiles = { uris -> onImportAudioToAlbum(uris, targetAlbum) }
    )
  }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LibraryAlbumGridCard(
  album: AlbumInfo,
  darkTheme: Boolean,
  reducedMotion: Boolean,
  isHiddenForFlight: Boolean = false,
  pulseRing: Boolean = false,
  onCoverBoundsMeasured: (Rect) -> Unit = {},
  onPulseFinished: () -> Unit = {},
  onClick: () -> Unit,
  onLongClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val cardShape = RoundedCornerShape(24.dp)
  val coverShape = RoundedCornerShape(20.dp)
  val coverUrl = album.coverUrl
  val savedPreset = remember(album.albumId, coverUrl) {
    if (coverUrl.isNullOrBlank()) AlbumCoverPresetStore.getPresetOrNull(context, album.albumId) else null
  }
  val haptic = LocalHapticFeedback.current
  val interactionSource = remember { MutableInteractionSource() }
  val isPressed by interactionSource.collectIsPressedAsState()
  val pressScale by animateFloatAsState(
    targetValue = if (isPressed && !reducedMotion) 0.96f else 1f,
    animationSpec = if (reducedMotion) snap() else tween(durationMillis = 140),
    label = "album_card_press_scale"
  )

  // Violet ring pulse on newly created album landing
  val ringProgress = remember { Animatable(0f) }
  LaunchedEffect(pulseRing, reducedMotion) {
    if (!pulseRing) {
      ringProgress.snapTo(0f)
      return@LaunchedEffect
    }
    if (reducedMotion) {
      ringProgress.snapTo(0.5f)
      kotlinx.coroutines.delay(350L)
      ringProgress.snapTo(0f)
      onPulseFinished()
    } else {
      ringProgress.snapTo(0f)
      ringProgress.animateTo(
        targetValue = 1f,
        animationSpec = tween(durationMillis = 760, easing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f))
      )
      ringProgress.snapTo(0f)
      onPulseFinished()
    }
  }

  val primaryAccent = MaterialTheme.colorScheme.primary
  val lightAccent = androidx.compose.ui.graphics.lerp(primaryAccent, Color.White, 0.32f)

  Column(
    modifier = modifier
      .scale(pressScale)
      .drawBehind {
        if (pulseRing && ringProgress.value > 0.01f) {
          val p = ringProgress.value
          val alpha = (1f - p).coerceIn(0f, 1f)
          val expand = p * 8.dp.toPx()
          val corner = 24.dp.toPx() + expand * 0.4f
          drawRoundRect(
            brush = Brush.linearGradient(
              colors = listOf(primaryAccent, lightAccent)
            ),
            topLeft = Offset(-expand, -expand),
            size = androidx.compose.ui.geometry.Size(
              width = size.width + expand * 2f,
              height = size.height + expand * 2f
            ),
            cornerRadius = CornerRadius(corner, corner),
            alpha = alpha * 0.9f,
            style = Stroke(width = (3.5f - p * 2f).coerceAtLeast(1f).dp.toPx())
          )
        }
      }
      .clip(cardShape)
      .background(
        if (darkTheme) Color(0xFF172034).copy(alpha = 0.68f)
        else Color.White.copy(alpha = 0.78f)
      )
      .border(
        width = if (pulseRing && ringProgress.value > 0.01f) 1.5.dp else 1.dp,
        color = if (pulseRing && ringProgress.value > 0.01f) {
          lightAccent.copy(alpha = (1f - ringProgress.value * 0.6f).coerceIn(0.2f, 1f))
        } else {
          Color.White.copy(alpha = if (darkTheme) 0.16f else 0.90f)
        },
        shape = cardShape
      )
      .combinedClickable(
        interactionSource = interactionSource,
        indication = ripple(color = MaterialTheme.colorScheme.primary),
        role = Role.Button,
        onClickLabel = album.title,
        onLongClickLabel = stringResource(id = R.string.album_delete),
        onClick = onClick,
        onLongClick = {
          haptic.performHapticFeedback(HapticFeedbackType.LongPress)
          onLongClick()
        }
      )
      .padding(10.dp)
      .testTag("library_album_card_${album.albumId}"),
    verticalArrangement = Arrangement.spacedBy(8.dp)
  ) {
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .aspectRatio(1f)
        .onGloballyPositioned { coords -> onCoverBoundsMeasured(coords.boundsInRoot()) }
        .alpha(if (isHiddenForFlight) 0f else 1f)
        .clip(coverShape)
        .background(
          Brush.linearGradient(
            colors = if (savedPreset != null) {
              listOf(
                savedPreset.resolveStartColor(primaryAccent),
                savedPreset.resolveEndColor(primaryAccent)
              )
            } else {
              listOf(
                primaryAccent,
                androidx.compose.ui.graphics.lerp(primaryAccent, Color(0xFF090710), 0.65f)
              )
            }
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
        savedPreset != null -> {
          AlbumCoverArtBox(
            preset = savedPreset,
            firstLetter = album.title.trim().firstOrNull()?.uppercaseChar()?.toString().orEmpty(),
            isTypingBoosted = false,
            reducedMotion = true,
            showEditBadge = false,
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
      val countLabel = when (album.tracks.size) {
        0 -> stringResource(id = R.string.album_meta_empty)
        1 -> stringResource(id = R.string.album_meta_single)
        else -> stringResource(id = R.string.album_meta_plural, album.tracks.size)
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

@Composable
private fun LibraryGenreFilterBar(
  totalCount: Int,
  orderedGenres: List<String>,
  genreCounts: Map<String, Int>,
  selectedGenre: String,
  groupByGenre: Boolean,
  filteredCount: Int,
  darkTheme: Boolean,
  onSelectGenre: (String) -> Unit,
  onToggleGroupByGenre: () -> Unit,
  onPlayFilteredGenre: () -> Unit,
  onClearGenreFilter: () -> Unit
) {
  val primaryAccent = MaterialTheme.colorScheme.primary
  val lightAccent = androidx.compose.ui.graphics.lerp(primaryAccent, Color.White, 0.32f)
  val isAllSelected = selectedGenre == GENRE_FILTER_ALL

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .testTag("library_genre_filter_bar"),
    verticalArrangement = Arrangement.spacedBy(6.dp)
  ) {
    LazyRow(
      modifier = Modifier
        .fillMaxWidth()
        .testTag("library_genre_filter_row"),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      item(key = "genre_group_toggle") {
        val groupActive = groupByGenre
        val chipShape = CircleShape
        val bgBrush = if (groupActive) {
          Brush.horizontalGradient(
            listOf(
              primaryAccent.copy(alpha = 0.28f),
              lightAccent.copy(alpha = 0.18f)
            )
          )
        } else {
          Brush.horizontalGradient(
            listOf(
              if (darkTheme) Color(0xFF151221).copy(alpha = 0.86f) else Color.White.copy(alpha = 0.82f),
              if (darkTheme) Color(0xFF100E1A).copy(alpha = 0.86f) else Color(0xFFF3F6FF).copy(alpha = 0.82f)
            )
          )
        }
        val borderColor = if (groupActive) {
          lightAccent.copy(alpha = 0.72f)
        } else {
          Color.White.copy(alpha = if (darkTheme) 0.10f else 0.75f)
        }
        val contentColor = if (groupActive) {
          if (darkTheme) Color(0xFFF4F2FB) else primaryAccent
        } else {
          if (darkTheme) Color(0xFF8F8AA6) else MaterialTheme.colorScheme.onSurfaceVariant
        }

        Row(
          modifier = Modifier
            .clip(chipShape)
            .background(bgBrush)
            .border(1.dp, borderColor, chipShape)
            .clickable { onToggleGroupByGenre() }
            .padding(horizontal = 11.dp, vertical = 7.dp)
            .testTag("library_genre_group_toggle"),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
          Icon(
            imageVector = Icons.Rounded.Category,
            contentDescription = null,
            tint = if (groupActive) lightAccent else contentColor,
            modifier = Modifier.size(14.dp)
          )
          Text(
            text = stringResource(id = R.string.library_genre_group_by),
            style = MaterialTheme.typography.labelMedium.copy(
              fontSize = 12.sp,
              fontWeight = if (groupActive) FontWeight.Bold else FontWeight.Medium
            ),
            color = contentColor
          )
        }
      }

      item(key = "genre_chip_all") {
        LibraryGenreFilterChip(
          label = stringResource(id = R.string.library_genre_all),
          count = totalCount,
          icon = Icons.Rounded.LibraryMusic,
          selected = isAllSelected,
          darkTheme = darkTheme,
          primaryAccent = primaryAccent,
          lightAccent = lightAccent,
          onClick = { onSelectGenre(GENRE_FILTER_ALL) },
          modifier = Modifier.testTag("library_genre_chip_all")
        )
      }

      items(
        items = orderedGenres,
        key = { "genre_chip_${libraryGenreTagKey(it)}" }
      ) { genreName ->
        val count = genreCounts[genreName] ?: 0
        val isSelected = !isAllSelected && selectedGenre.equals(genreName, ignoreCase = true)
        val tagKey = libraryGenreTagKey(genreName)
        LibraryGenreFilterChip(
          label = genreName,
          count = count,
          icon = libraryGenreIcon(genreName),
          selected = isSelected,
          darkTheme = darkTheme,
          primaryAccent = primaryAccent,
          lightAccent = lightAccent,
          onClick = { onSelectGenre(genreName) },
          modifier = Modifier.testTag("library_genre_chip_$tagKey")
        )
      }
    }

    if (!isAllSelected) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(14.dp))
          .background(
            if (darkTheme) Color(0xFF13101E).copy(alpha = 0.88f)
            else primaryAccent.copy(alpha = 0.08f)
          )
          .border(
            1.dp,
            primaryAccent.copy(alpha = if (darkTheme) 0.36f else 0.24f),
            RoundedCornerShape(14.dp)
          )
          .padding(horizontal = 11.dp, vertical = 6.dp)
          .testTag("library_active_genre_banner"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(7.dp),
          modifier = Modifier.weight(1f)
        ) {
          Icon(
            imageVector = libraryGenreIcon(selectedGenre),
            contentDescription = null,
            tint = if (darkTheme) lightAccent else primaryAccent,
            modifier = Modifier.size(16.dp)
          )
          Text(
            text = stringResource(id = R.string.library_genre_filter_active, selectedGenre, filteredCount),
            style = MaterialTheme.typography.labelMedium.copy(
              fontWeight = FontWeight.SemiBold,
              fontSize = 12.sp
            ),
            color = if (darkTheme) Color(0xFFF4F2FB) else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }

        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          if (filteredCount > 0) {
            Row(
              modifier = Modifier
                .clip(CircleShape)
                .background(primaryAccent.copy(alpha = 0.22f))
                .border(0.8.dp, lightAccent.copy(alpha = 0.55f), CircleShape)
                .clickable { onPlayFilteredGenre() }
                .padding(horizontal = 9.dp, vertical = 4.dp)
                .testTag("library_genre_play_all_button"),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
              Icon(
                imageVector = Icons.Rounded.PlayArrow,
                contentDescription = null,
                tint = if (darkTheme) Color(0xFFF4F2FB) else primaryAccent,
                modifier = Modifier.size(14.dp)
              )
              Text(
                text = stringResource(id = R.string.album_action_play),
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = if (darkTheme) Color(0xFFF4F2FB) else primaryAccent
              )
            }
          }

          Row(
            modifier = Modifier
              .clip(CircleShape)
              .background(Color.White.copy(alpha = if (darkTheme) 0.08f else 0.72f))
              .border(0.8.dp, Color.White.copy(alpha = if (darkTheme) 0.14f else 0.9f), CircleShape)
              .clickable { onClearGenreFilter() }
              .padding(horizontal = 8.dp, vertical = 4.dp)
              .testTag("library_genre_clear_filter_button"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
          ) {
            Icon(
              imageVector = Icons.Rounded.Close,
              contentDescription = stringResource(id = R.string.library_genre_clear_filter),
              tint = if (darkTheme) Color(0xFF8F8AA6) else MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.size(13.dp)
            )
            Text(
              text = stringResource(id = R.string.library_genre_all),
              style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
              color = if (darkTheme) Color(0xFF8F8AA6) else MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }
      }
    }
  }
}

@Composable
private fun LibraryGenreFilterChip(
  label: String,
  count: Int,
  icon: ImageVector,
  selected: Boolean,
  darkTheme: Boolean,
  primaryAccent: Color,
  lightAccent: Color,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  val chipShape = CircleShape
  val bgBrush = if (selected) {
    Brush.horizontalGradient(
      colors = listOf(
        primaryAccent,
        androidx.compose.ui.graphics.lerp(primaryAccent, lightAccent, 0.55f)
      )
    )
  } else {
    Brush.horizontalGradient(
      colors = listOf(
        if (darkTheme) Color(0xFF141120).copy(alpha = 0.84f) else Color.White.copy(alpha = 0.84f),
        if (darkTheme) Color(0xFF0E0C17).copy(alpha = 0.84f) else Color(0xFFF3F6FF).copy(alpha = 0.84f)
      )
    )
  }
  val borderColor = if (selected) {
    lightAccent.copy(alpha = 0.82f)
  } else {
    Color.White.copy(alpha = if (darkTheme) 0.08f else 0.78f)
  }
  val textColor = if (selected) {
    Color.White
  } else if (darkTheme) {
    if (count > 0) Color(0xFFF4F2FB) else Color(0xFF8F8AA6)
  } else {
    if (count > 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
  }

  Row(
    modifier = modifier
      .clip(chipShape)
      .background(bgBrush)
      .border(1.dp, borderColor, chipShape)
      .clickable { onClick() }
      .padding(horizontal = 11.dp, vertical = 7.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(5.dp)
  ) {
    Icon(
      imageVector = icon,
      contentDescription = null,
      tint = if (selected) Color.White else if (count > 0) primaryAccent else textColor.copy(alpha = 0.7f),
      modifier = Modifier.size(14.dp)
    )
    Text(
      text = label,
      style = MaterialTheme.typography.labelMedium.copy(
        fontSize = 12.sp,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold
      ),
      color = textColor,
      maxLines = 1
    )
    if (count > 0 || selected) {
      Box(
        modifier = Modifier
          .clip(CircleShape)
          .background(
            if (selected) Color.Black.copy(alpha = 0.24f)
            else primaryAccent.copy(alpha = if (darkTheme) 0.18f else 0.12f)
          )
          .padding(horizontal = 6.dp, vertical = 1.dp),
        contentAlignment = Alignment.Center
      ) {
        Text(
          text = count.toString(),
          style = MaterialTheme.typography.labelSmall.copy(
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
          ),
          color = if (selected) Color.White else if (darkTheme) lightAccent else primaryAccent
        )
      }
    }
  }
}

@Composable
private fun LibraryGenreCategoryHeader(
  genre: String,
  trackCount: Int,
  isFocused: Boolean,
  darkTheme: Boolean,
  onFilterToggle: () -> Unit,
  onPlayCategory: () -> Unit,
  modifier: Modifier = Modifier
) {
  val primaryAccent = MaterialTheme.colorScheme.primary
  val lightAccent = androidx.compose.ui.graphics.lerp(primaryAccent, Color.White, 0.32f)
  val headerShape = RoundedCornerShape(18.dp)

  Row(
    modifier = modifier
      .fillMaxWidth()
      .padding(top = 4.dp, bottom = 2.dp)
      .clip(headerShape)
      .background(
        Brush.horizontalGradient(
          colors = if (darkTheme) {
            listOf(
              Color(0xFF151223).copy(alpha = 0.90f),
              Color(0xFF0E0B18).copy(alpha = 0.86f)
            )
          } else {
            listOf(
              Color.White.copy(alpha = 0.90f),
              primaryAccent.copy(alpha = 0.08f)
            )
          }
        )
      )
      .border(
        width = 1.dp,
        color = if (isFocused) lightAccent.copy(alpha = 0.55f)
        else Color.White.copy(alpha = if (darkTheme) 0.09f else 0.78f),
        shape = headerShape
      )
      .clickable { onFilterToggle() }
      .padding(horizontal = 12.dp, vertical = 9.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(10.dp),
      modifier = Modifier.weight(1f)
    ) {
      Box(
        modifier = Modifier
          .size(32.dp)
          .clip(RoundedCornerShape(10.dp))
          .background(primaryAccent.copy(alpha = if (darkTheme) 0.22f else 0.14f))
          .border(0.8.dp, lightAccent.copy(alpha = 0.45f), RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = libraryGenreIcon(genre),
          contentDescription = null,
          tint = if (darkTheme) lightAccent else primaryAccent,
          modifier = Modifier.size(17.dp)
        )
      }

      Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Text(
          text = genre,
          style = MaterialTheme.typography.titleSmall.copy(
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp
          ),
          color = if (darkTheme) Color(0xFFF4F2FB) else MaterialTheme.colorScheme.onSurface
        )
        Text(
          text = stringResource(id = R.string.mix_tracks_count, trackCount),
          style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
          color = if (darkTheme) Color(0xFF8F8AA6) else MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    }

    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
      Box(
        modifier = Modifier
          .clip(CircleShape)
          .background(primaryAccent.copy(alpha = if (darkTheme) 0.22f else 0.14f))
          .border(0.8.dp, lightAccent.copy(alpha = 0.45f), CircleShape)
          .clickable { onPlayCategory() }
          .padding(horizontal = 10.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
          Icon(
            imageVector = Icons.Rounded.PlayArrow,
            contentDescription = stringResource(id = R.string.album_action_play),
            tint = if (darkTheme) Color(0xFFF4F2FB) else primaryAccent,
            modifier = Modifier.size(14.dp)
          )
          Text(
            text = stringResource(id = R.string.album_action_play),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = if (darkTheme) Color(0xFFF4F2FB) else primaryAccent
          )
        }
      }
    }
  }
}


