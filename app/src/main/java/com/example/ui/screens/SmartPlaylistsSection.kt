package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.recommendation.SmartPlaylist
import com.example.recommendation.SmartPlaylistCategory
import com.example.ui.glass.GlassSegmented
import com.example.ui.glass.LocalAurixHazeState
import com.example.ui.glass.SegOption
import com.example.ui.glass.aurixGlassHazeStyle
import dev.chrisbanes.haze.hazeChild

@Composable
fun SmartPlaylistsSection(
  playlists: List<SmartPlaylist>,
  djAutoMixEnabled: Boolean,
  darkTheme: Boolean,
  reducedMotion: Boolean,
  onToggleDjAutoMix: (Boolean) -> Unit,
  onOpenPlaylist: (SmartPlaylist) -> Unit,
  onStartDjMix: (SmartPlaylist) -> Unit
) {
  if (playlists.isEmpty()) return

  var isExpanded by rememberSaveable { mutableStateOf(true) }
  var selectedCategoryIdx by rememberSaveable { mutableIntStateOf(0) }

  val filteredPlaylists = remember(playlists, selectedCategoryIdx) {
    when (selectedCategoryIdx) {
      1 -> playlists.filter { it.category == SmartPlaylistCategory.MOOD }
      2 -> playlists.filter { it.category == SmartPlaylistCategory.BPM }
      3 -> playlists.filter { it.category == SmartPlaylistCategory.ARTIST }
      else -> playlists
    }.ifEmpty { playlists }
  }

  val hazeState = LocalAurixHazeState.current
  val backdropBgColor = MaterialTheme.colorScheme.background
  val segBackdrop = if (hazeState != null) {
    Modifier.hazeChild(
      state = hazeState,
      style = aurixGlassHazeStyle(darkTheme = darkTheme, backgroundColor = backdropBgColor)
    )
  } else {
    Modifier
  }

  val sectionEnter = remember(reducedMotion) {
    if (reducedMotion) fadeIn(snap()) else expandVertically(tween(220)) + fadeIn(tween(180))
  }
  val sectionExit = remember(reducedMotion) {
    if (reducedMotion) fadeOut(snap()) else shrinkVertically(tween(180)) + fadeOut(tween(140))
  }

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .testTag("home_smart_playlists_section"),
    verticalArrangement = Arrangement.spacedBy(10.dp)
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = stringResource(id = R.string.smart_playlists_title),
          style = MaterialTheme.typography.titleMedium.copy(
            fontWeight = FontWeight.ExtraBold,
            fontSize = 18.sp
          ),
          color = MaterialTheme.colorScheme.onBackground,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
        Text(
          text = stringResource(id = R.string.smart_playlists_subtitle),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
      }

      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        Surface(
          onClick = { onToggleDjAutoMix(!djAutoMixEnabled) },
          shape = CircleShape,
          color = if (djAutoMixEnabled) {
            Color(0xFF43C7F4).copy(alpha = if (darkTheme) 0.24f else 0.16f)
          } else {
            MaterialTheme.colorScheme.onSurface.copy(alpha = if (darkTheme) 0.08f else 0.06f)
          },
          border = BorderStroke(
            1.dp,
            if (djAutoMixEnabled) Color(0xFF43C7F4).copy(alpha = 0.65f)
            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
          ),
          modifier = Modifier
            .defaultMinSize(minHeight = 48.dp)
            .testTag("home_dj_automix_toggle_chip")
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
          ) {
            Icon(
              imageVector = Icons.Rounded.GraphicEq,
              contentDescription = stringResource(id = R.string.dj_mode_toggle_cd),
              tint = if (djAutoMixEnabled) Color(0xFF43C7F4) else MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.size(16.dp)
            )
            Text(
              text = stringResource(id = R.string.smart_playlists_dj_mix_button),
              style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
              color = if (djAutoMixEnabled) Color(0xFF43C7F4) else MaterialTheme.colorScheme.onSurface
            )
          }
        }

        IconButton(
          onClick = { isExpanded = !isExpanded },
          modifier = Modifier
            .minimumInteractiveComponentSize()
            .testTag("home_toggle_smart_playlists_button")
        ) {
          Icon(
            imageVector = if (isExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
            contentDescription = stringResource(id = R.string.smart_playlists_title),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }
    }

    AnimatedVisibility(
      visible = isExpanded,
      enter = sectionEnter,
      exit = sectionExit
    ) {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val filterOptions = listOf(
          SegOption(stringResource(id = R.string.smart_playlists_filter_all)),
          SegOption(stringResource(id = R.string.smart_playlists_filter_mood)),
          SegOption(stringResource(id = R.string.smart_playlists_filter_bpm)),
          SegOption(stringResource(id = R.string.smart_playlists_filter_artist))
        )
        Box(modifier = Modifier.horizontalScroll(rememberScrollState())) {
          GlassSegmented(
            options = filterOptions,
            selectedIndex = selectedCategoryIdx,
            onSelect = { selectedCategoryIdx = it },
            fillWidth = false,
            darkTheme = darkTheme,
            backdrop = segBackdrop,
            modifier = Modifier.testTag("smart_playlists_category_segmented")
          )
        }

        Row(
          modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          filteredPlaylists.forEach { playlist ->
            SmartPlaylistCard(
              playlist = playlist,
              djAutoMixEnabled = djAutoMixEnabled,
              darkTheme = darkTheme,
              onClick = { onOpenPlaylist(playlist) },
              onDjMixClick = { onStartDjMix(playlist) }
            )
          }
        }
      }
    }
  }
}

@Composable
private fun SmartPlaylistCard(
  playlist: SmartPlaylist,
  djAutoMixEnabled: Boolean,
  darkTheme: Boolean,
  onClick: () -> Unit,
  onDjMixClick: () -> Unit
) {
  val accent = Color(playlist.accentColorHex)
  val secondary = Color(playlist.secondaryColorHex)
  val shape = RoundedCornerShape(22.dp)
  val baseColor = if (darkTheme) Color(0xFF0C1220) else Color(0xFF101829)

  Box(
    modifier = Modifier
      .width(214.dp)
      .clip(shape)
      .background(baseColor)
      .background(
        Brush.linearGradient(
          colors = listOf(
            accent.copy(alpha = 0.34f),
            secondary.copy(alpha = 0.20f),
            Color(0xFF080C14).copy(alpha = 0.92f)
          ),
          start = Offset(0f, 0f),
          end = Offset(480f, 520f)
        )
      )
      .border(
        width = 1.dp,
        brush = Brush.verticalGradient(
          listOf(Color.White.copy(alpha = 0.30f), Color.White.copy(alpha = 0.08f))
        ),
        shape = shape
      )
      .clickable(onClick = onClick)
      .padding(14.dp)
      .testTag("smart_playlist_card_${playlist.id}")
  ) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Surface(
          shape = CircleShape,
          color = Color.White.copy(alpha = 0.14f),
          border = BorderStroke(1.dp, Color.White.copy(alpha = 0.22f))
        ) {
          Text(
            text = playlist.badgeLabel,
            style = MaterialTheme.typography.labelSmall.copy(
              fontWeight = FontWeight.ExtraBold,
              letterSpacing = 0.7.sp,
              fontSize = 10.sp
            ),
            color = Color.White,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
          )
        }

        // Overlapping mini album artworks
        Row(horizontalArrangement = Arrangement.spacedBy((-8).dp)) {
          playlist.tracks.take(3).forEach { song ->
            SongArtwork(
              song = song,
              modifier = Modifier
                .size(28.dp)
                .border(1.dp, Color.White.copy(alpha = 0.5f), CircleShape),
              cornerRadius = 14.dp,
              iconSize = 12.dp
            )
          }
        }
      }

      Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
          text = playlist.title,
          style = MaterialTheme.typography.titleSmall.copy(
            fontWeight = FontWeight.ExtraBold,
            fontSize = 15.sp
          ),
          color = Color.White,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
        Text(
          text = stringResource(
            id = R.string.smart_playlists_tracks_bpm,
            playlist.tracks.size,
            playlist.bpmRangeLabel
          ),
          style = MaterialTheme.typography.labelSmall,
          color = Color.White.copy(alpha = 0.74f),
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
      }

      Button(
        onClick = onDjMixClick,
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
          containerColor = if (djAutoMixEnabled) accent else Color.White.copy(alpha = 0.18f),
          contentColor = if (djAutoMixEnabled) Color(0xFF06101C) else Color.White
        ),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
        modifier = Modifier
          .fillMaxWidth()
          .defaultMinSize(minHeight = 48.dp)
          .testTag("smart_playlist_dj_play_${playlist.id}")
      ) {
        Icon(
          imageVector = Icons.Rounded.GraphicEq,
          contentDescription = null,
          modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
          text = stringResource(id = R.string.smart_playlists_dj_mix_button),
          style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
          maxLines = 1
        )
      }
    }
  }
}
