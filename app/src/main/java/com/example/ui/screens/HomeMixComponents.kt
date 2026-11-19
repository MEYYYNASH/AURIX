package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.R
import com.example.model.APP_ICON_ARTWORK
import com.example.model.Song
import com.example.recommendation.*

@Composable
fun ContinueListeningBanner(
  entry: RecentPlayEntry,
  isCurrent: Boolean,
  isPlaying: Boolean,
  darkTheme: Boolean,
  reducedMotion: Boolean,
  onResumeClick: () -> Unit
) {
  val (artPrimary, artSecondary) = rememberArtworkColors(entry.song.artworkUrl, entry.song.title)
  GlassCard(
    darkTheme = darkTheme,
    cornerRadius = 22.dp,
    modifier = Modifier
      .fillMaxWidth()
      .clickable { onResumeClick() }
      .testTag("home_continue_listening_banner")
  ) {
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .background(
          Brush.horizontalGradient(
            colors = listOf(
              artPrimary.copy(alpha = if (darkTheme) 0.16f else 0.10f),
              artSecondary.copy(alpha = if (darkTheme) 0.08f else 0.04f),
              Color.Transparent
            )
          )
        )
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          SongArtwork(
            song = entry.song,
            modifier = Modifier.size(52.dp),
            cornerRadius = 14.dp
          )
          Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
              Text(
                text = stringResource(id = R.string.home_continue_listening).uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(
                  fontWeight = FontWeight.ExtraBold,
                  fontSize = 10.sp,
                  letterSpacing = 0.6.sp
                ),
                color = AurixTealAccent
              )
              Text(
                text = "· ${entry.timeAgoLabel}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
            Text(
              text = entry.song.title,
              style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
              color = MaterialTheme.colorScheme.onSurface,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
            Text(
              text = entry.song.artist,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
          }
          Surface(
            shape = CircleShape,
            color = AurixTealAccent,
            modifier = Modifier
              .minimumInteractiveComponentSize()
              .clip(CircleShape)
              .clickable { onResumeClick() }
              .testTag("home_continue_resume_button")
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
              if (isCurrent && isPlaying) {
                EqualizerBars(
                  isPlaying = true,
                  color = Color(0xFF06201C),
                  reducedMotion = reducedMotion,
                  barWidth = 2.dp,
                  maxHeight = 11.dp
                )
              } else {
                Icon(
                  imageVector = Icons.Rounded.PlayArrow,
                  contentDescription = stringResource(id = R.string.home_resume_now),
                  tint = Color(0xFF06201C),
                  modifier = Modifier.size(16.dp)
                )
              }
              Text(
                text = stringResource(id = R.string.home_resume_now),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = Color(0xFF06201C)
              )
            }
          }
        }
        if (entry.completedPercent > 0.02f) {
          LinearProgressIndicator(
            progress = { entry.completedPercent.coerceIn(0.05f, 1f) },
            modifier = Modifier
              .fillMaxWidth()
              .height(3.dp)
              .clip(CircleShape),
            color = AurixTealAccent,
            trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
          )
        }
      }
    }
  }
}

@Composable
fun ArtistMixCard(
  mix: ArtistMix,
  darkTheme: Boolean,
  onOpenMix: () -> Unit,
  onPlayMix: () -> Unit,
  onShuffleMix: () -> Unit
) {
  val (c1, c2) = rememberArtworkColors(mix.artworkUrl, mix.artistName)
  GlassCard(
    darkTheme = darkTheme,
    cornerRadius = 22.dp,
    modifier = Modifier
      .width(168.dp)
      .clickable { onOpenMix() }
      .testTag("artist_mix_card_${mix.id}")
  ) {
    Column(
      modifier = Modifier.padding(12.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(126.dp),
        contentAlignment = Alignment.Center
      ) {
        Box(
          modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(16.dp))
            .background(
              Brush.linearGradient(
                colors = listOf(c1.copy(alpha = 0.72f), c2.copy(alpha = 0.85f))
              )
            )
        )
        val fallbackSong = mix.tracks.firstOrNull()
        Box(
          modifier = Modifier
            .size(86.dp)
            .clip(CircleShape)
            .border(2.dp, Color.White.copy(alpha = 0.55f), CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
          contentAlignment = Alignment.Center
        ) {
          if (mix.artworkUrl.isNotBlank() && mix.artworkUrl != APP_ICON_ARTWORK) {
            AsyncImage(
              model = mix.artworkUrl,
              contentDescription = mix.artistName,
              contentScale = ContentScale.Crop,
              modifier = Modifier.fillMaxSize()
            )
          } else if (fallbackSong != null) {
            SongArtwork(song = fallbackSong, modifier = Modifier.fillMaxSize(), cornerRadius = 43.dp)
          } else {
            Icon(
              imageVector = Icons.Rounded.Person,
              contentDescription = mix.artistName,
              tint = Color.White,
              modifier = Modifier.size(34.dp)
            )
          }
        }
        Surface(
          shape = CircleShape,
          color = Color.Black.copy(alpha = 0.58f),
          modifier = Modifier
            .align(Alignment.TopStart)
            .padding(6.dp)
        ) {
          Text(
            text = mix.genreTag.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.ExtraBold),
            color = Color.White,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
          )
        }
        Surface(
          shape = CircleShape,
          color = AurixTealAccent,
          modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(6.dp)
            .minimumInteractiveComponentSize()
            .size(36.dp)
            .clip(CircleShape)
            .clickable { onPlayMix() }
            .testTag("artist_mix_play_${mix.id}")
        ) {
          Box(contentAlignment = Alignment.Center) {
            Icon(
              imageVector = Icons.Rounded.PlayArrow,
              contentDescription = stringResource(id = R.string.album_action_play),
              tint = Color(0xFF06201C),
              modifier = Modifier.size(20.dp)
            )
          }
        }
      }

      Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
          text = mix.artistName,
          style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
          color = MaterialTheme.colorScheme.onSurface,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
        Text(
          text = mix.subtitle,
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = stringResource(id = R.string.mix_tracks_count, mix.tracks.size),
          style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
          color = AurixTealAccent
        )
        IconButton(
          onClick = onShuffleMix,
          modifier = Modifier
            .minimumInteractiveComponentSize()
            .size(32.dp)
            .testTag("artist_mix_shuffle_${mix.id}")
        ) {
          Icon(
            imageVector = Icons.Rounded.Shuffle,
            contentDescription = stringResource(id = R.string.mix_shuffle),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
          )
        }
      }
    }
  }
}

@Composable
fun PersonalMixCard(
  mix: PersonalMix,
  darkTheme: Boolean,
  onOpenMix: () -> Unit,
  onPlayMix: () -> Unit,
  onShuffleMix: () -> Unit,
  onSaveMix: () -> Unit,
  onShareMix: () -> Unit
) {
  val accentColor = parseHexColor(mix.accentHex, darkTheme)
  GlassCard(
    darkTheme = darkTheme,
    cornerRadius = 22.dp,
    modifier = Modifier
      .width(178.dp)
      .clickable { onOpenMix() }
      .testTag("personal_mix_card_${mix.id}")
  ) {
    Column(
      modifier = Modifier.padding(12.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(114.dp)
          .clip(RoundedCornerShape(16.dp))
          .background(
            Brush.linearGradient(
              colors = listOf(accentColor.copy(alpha = 0.85f), accentColor.copy(alpha = 0.38f))
            )
          )
      ) {
        val coverTrack = mix.tracks.firstOrNull()
        if (mix.artworkUrl.isNotBlank() && mix.artworkUrl != APP_ICON_ARTWORK) {
          AsyncImage(
            model = mix.artworkUrl,
            contentDescription = mix.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
          )
        } else if (coverTrack != null) {
          SongArtwork(song = coverTrack, modifier = Modifier.fillMaxSize(), cornerRadius = 16.dp)
        }
        Box(
          modifier = Modifier
            .fillMaxSize()
            .background(
              Brush.verticalGradient(
                colors = listOf(Color.Black.copy(alpha = 0.16f), Color.Black.copy(alpha = 0.72f))
              )
            )
        )
        Surface(
          shape = CircleShape,
          color = accentColor.copy(alpha = 0.90f),
          modifier = Modifier
            .align(Alignment.TopStart)
            .padding(8.dp)
        ) {
          Text(
            text = mix.badgeLabel,
            style = MaterialTheme.typography.labelSmall.copy(
              fontWeight = FontWeight.ExtraBold,
              fontSize = 9.sp,
              letterSpacing = 0.5.sp
            ),
            color = Color.White,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
          )
        }
        Row(
          modifier = Modifier
            .align(Alignment.BottomStart)
            .fillMaxWidth()
            .padding(8.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = mix.title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
          )
          Surface(
            shape = CircleShape,
            color = Color.White,
            modifier = Modifier
              .minimumInteractiveComponentSize()
              .size(34.dp)
              .clip(CircleShape)
              .clickable { onPlayMix() }
              .testTag("personal_mix_play_${mix.id}")
          ) {
            Box(contentAlignment = Alignment.Center) {
              Icon(
                imageVector = Icons.Rounded.PlayArrow,
                contentDescription = stringResource(id = R.string.album_action_play),
                tint = Color(0xFF0A0E1A),
                modifier = Modifier.size(20.dp)
              )
            }
          }
        }
      }

      Text(
        text = mix.subtitle,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        minLines = 2
      )

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = stringResource(id = R.string.mix_tracks_count, mix.tracks.size),
          style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
          color = accentColor
        )
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
          IconButton(
            onClick = onShuffleMix,
            modifier = Modifier.minimumInteractiveComponentSize().size(32.dp)
          ) {
            Icon(
              imageVector = Icons.Rounded.Shuffle,
              contentDescription = stringResource(id = R.string.mix_shuffle),
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.size(16.dp)
            )
          }
          IconButton(
            onClick = onSaveMix,
            modifier = Modifier.minimumInteractiveComponentSize().size(32.dp)
          ) {
            Icon(
              imageVector = Icons.Rounded.BookmarkAdd,
              contentDescription = stringResource(id = R.string.mix_save_to_library),
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.size(16.dp)
            )
          }
          IconButton(
            onClick = onShareMix,
            modifier = Modifier.minimumInteractiveComponentSize().size(32.dp)
          ) {
            Icon(
              imageVector = Icons.Rounded.Share,
              contentDescription = stringResource(id = R.string.mix_share),
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.size(15.dp)
            )
          }
        }
      }
    }
  }
}

@Composable
fun NewForYouCard(
  item: NewForYouItem,
  darkTheme: Boolean,
  onClick: () -> Unit
) {
  val (c1, c2) = rememberArtworkColors(item.artworkUrl, item.title)
  GlassCard(
    darkTheme = darkTheme,
    cornerRadius = 20.dp,
    modifier = Modifier
      .width(156.dp)
      .clickable { onClick() }
      .testTag("new_for_you_card_${item.id}")
  ) {
    Column(
      modifier = Modifier.padding(10.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(128.dp)
          .clip(RoundedCornerShape(14.dp))
          .background(Brush.linearGradient(listOf(c1.copy(alpha = 0.7f), c2.copy(alpha = 0.85f))))
      ) {
        if (item.artworkUrl.isNotBlank() && item.artworkUrl != APP_ICON_ARTWORK) {
          AsyncImage(
            model = item.artworkUrl,
            contentDescription = item.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
          )
        } else if (item.song != null) {
          SongArtwork(song = item.song, modifier = Modifier.fillMaxSize(), cornerRadius = 14.dp)
        } else {
          Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(
              imageVector = if (item.album != null) Icons.Rounded.Album else Icons.Rounded.MusicNote,
              contentDescription = item.title,
              tint = Color.White,
              modifier = Modifier.size(32.dp)
            )
          }
        }

        Surface(
          shape = CircleShape,
          color = if (item.isFromFavoriteArtist) AurixTealAccent else Color.Black.copy(alpha = 0.62f),
          modifier = Modifier
            .align(Alignment.TopStart)
            .padding(6.dp)
        ) {
          Text(
            text = item.badgeLabel,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
            color = if (item.isFromFavoriteArtist) Color(0xFF06201C) else Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
          )
        }
      }

      Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
          text = item.title,
          style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
          color = MaterialTheme.colorScheme.onSurface,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
        Text(
          text = item.artist,
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
        Text(
          text = item.releaseDateLabel,
          style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
          color = AurixTealAccent,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
      }
    }
  }
}

@Composable
fun RecentPlayHistoryCard(
  entry: RecentPlayEntry,
  isCurrent: Boolean,
  isPlaying: Boolean,
  hasLyrics: Boolean,
  darkTheme: Boolean,
  reducedMotion: Boolean,
  onClick: () -> Unit,
  onRemoveClick: () -> Unit
) {
  GlassCard(
    darkTheme = darkTheme,
    cornerRadius = 20.dp,
    modifier = Modifier
      .width(142.dp)
      .then(
        if (isCurrent) {
          Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.78f), RoundedCornerShape(20.dp))
        } else Modifier
      )
      .clickable { onClick() }
      .testTag("recent_play_card_${entry.song.id}")
  ) {
    Column(
      modifier = Modifier.padding(9.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
      Box {
        SongArtwork(
          song = entry.song,
          modifier = Modifier
            .fillMaxWidth()
            .height(106.dp),
          cornerRadius = 14.dp,
          iconSize = 26.dp
        )
        Surface(
          shape = CircleShape,
          color = Color.Black.copy(alpha = 0.62f),
          modifier = Modifier
            .align(Alignment.TopStart)
            .padding(5.dp)
        ) {
          Text(
            text = entry.timeAgoLabel,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.SemiBold),
            color = Color.White,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
          )
        }
        IconButton(
          onClick = onRemoveClick,
          modifier = Modifier
            .align(Alignment.TopEnd)
            .size(26.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.55f))
        ) {
          Icon(
            imageVector = Icons.Rounded.Close,
            contentDescription = stringResource(id = R.string.mix_remove_recent_cd),
            tint = Color.White,
            modifier = Modifier.size(13.dp)
          )
        }
        if (isCurrent) {
          Box(
            modifier = Modifier
              .align(Alignment.BottomEnd)
              .padding(6.dp)
              .clip(CircleShape)
              .background(Color.Black.copy(alpha = 0.68f))
              .padding(horizontal = 6.dp, vertical = 4.dp)
          ) {
            EqualizerBars(
              isPlaying = isPlaying,
              color = AurixTealAccent,
              reducedMotion = reducedMotion,
              barWidth = 2.5.dp,
              maxHeight = 10.dp
            )
          }
        } else if (hasLyrics) {
          LyricsTag(
            visible = true,
            size = LyricsTagSize.Mini,
            darkTheme = darkTheme,
            reducedMotion = reducedMotion,
            modifier = Modifier
              .align(Alignment.BottomEnd)
              .padding(6.dp)
          )
        }
      }

      if (entry.completedPercent > 0.02f) {
        LinearProgressIndicator(
          progress = { entry.completedPercent.coerceIn(0.05f, 1f) },
          modifier = Modifier
            .fillMaxWidth()
            .height(2.5.dp)
            .clip(CircleShape),
          color = AurixTealAccent,
          trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
        )
      }

      Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Text(
          text = entry.song.title,
          style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
          color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
        Text(
          text = entry.song.artist,
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
      }
    }
  }
}

@Composable
fun MadeForYouMiniCard(
  title: String,
  subtitle: String,
  badge: String,
  icon: ImageVector,
  accentColor: Color,
  darkTheme: Boolean,
  testTag: String,
  onClick: () -> Unit
) {
  GlassCard(
    darkTheme = darkTheme,
    cornerRadius = 20.dp,
    modifier = Modifier
      .width(196.dp)
      .clickable { onClick() }
      .testTag(testTag)
  ) {
    Column(
      modifier = Modifier.padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Box(
          modifier = Modifier
            .size(38.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(accentColor.copy(alpha = 0.18f)),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = icon,
            contentDescription = null,
            tint = accentColor,
            modifier = Modifier.size(20.dp)
          )
        }
        Surface(
          shape = CircleShape,
          color = accentColor.copy(alpha = 0.14f),
          border = BorderStroke(0.5.dp, accentColor.copy(alpha = 0.35f))
        ) {
          Text(
            text = badge.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
            color = accentColor,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
          )
        }
      }
      Text(
        text = title,
        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
      Text(
        text = subtitle,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        minLines = 2
      )
    }
  }
}
