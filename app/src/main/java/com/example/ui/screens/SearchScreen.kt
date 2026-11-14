package com.example.ui.screens

import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.model.Song

@Composable
fun SearchScreen(
  searchMode: String,
  searchQuery: String,
  searchResults: List<Song>,
  isSearching: Boolean,
  searchError: String?,
  youtubeUrl: String,
  analyzedSong: Song?,
  isAnalyzingYoutube: Boolean,
  youtubeError: String?,
  currentSong: Song?,
  isPlaying: Boolean,
  darkTheme: Boolean,
  reducedMotion: Boolean,
  songDownloadProgress: Map<String, Float>,
  downloadedSongIds: Set<String>,
  onSearchModeChanged: (String) -> Unit,
  onSearchQueryChanged: (String) -> Unit,
  onYoutubeUrlChanged: (String) -> Unit,
  onAnalyzeYoutube: () -> Unit,
  onSongClick: (Song, List<Song>) -> Unit,
  onFavoriteClick: (Song) -> Unit,
  onDownloadClick: (Song) -> Unit
) {
  val context = LocalContext.current
  val keyboardController = LocalSoftwareKeyboardController.current
  val quickTags = listOf("TikTok Viral", "Khmer", "Phonk", "Pop", "R&B", "Hip-Hop")

  Box(modifier = Modifier.fillMaxSize()) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(horizontal = 20.dp)
        .windowInsetsPadding(WindowInsets.statusBars)
    ) {
      Spacer(modifier = Modifier.height(14.dp))

      Text(
        text = "Search",
        style = MaterialTheme.typography.displayLarge.copy(fontSize = 28.sp, fontWeight = FontWeight.ExtraBold),
        color = MaterialTheme.colorScheme.onBackground
      )
      Text(
        text = "Find songs, artists & YouTube links",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )

      Spacer(modifier = Modifier.height(14.dp))

      // Unified Liquid Glass Mode Switcher (ONLY in Search)
      GlassSegmentedBar(
        options = listOf(
          "iTunes 30s" to Icons.Rounded.Search,
          "YouTube Link" to Icons.Rounded.PlayArrow
        ),
        selectedIndex = if (searchMode == "youtube") 1 else 0,
        darkTheme = darkTheme,
        onSelect = { idx -> onSearchModeChanged(if (idx == 1) "youtube" else "itunes") },
        testTags = listOf("mode_search_button", "mode_youtube_button")
      )

      Spacer(modifier = Modifier.height(14.dp))

      AnimatedContent(
        targetState = searchMode,
        modifier = Modifier.weight(1f).fillMaxWidth(),
        transitionSpec = {
          fadeIn(tween(if (reducedMotion) 0 else 200)) togetherWith fadeOut(tween(if (reducedMotion) 0 else 160))
        },
        label = "search_mode_content"
      ) { mode ->
        if (mode == "youtube") {
          val inputBg = if (darkTheme) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.85f)
          val inputBorder = if (darkTheme) Color.White.copy(alpha = 0.18f) else Color.White
          val pasteBg = if (darkTheme) Color.White.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.85f)

          LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = if (currentSong != null) 205.dp else 135.dp)
          ) {
            item {
              GlassCard(
                darkTheme = darkTheme,
                cornerRadius = 28.dp,
                modifier = Modifier.fillMaxWidth()
              ) {
                Column(
                  modifier = Modifier.padding(18.dp),
                  verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                  Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                  ) {
                    Box(
                      modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                          Brush.linearGradient(
                            colors = listOf(
                              Color(0xFFEF4444).copy(alpha = 0.24f),
                              MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                            )
                          )
                        )
                        .border(1.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(14.dp)),
                      contentAlignment = Alignment.Center
                    ) {
                      Icon(
                        imageVector = Icons.Rounded.OndemandVideo,
                        contentDescription = null,
                        tint = Color(0xFFEF4444),
                        modifier = Modifier.size(22.dp)
                      )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                      Text(
                        text = "YouTube Link Matcher",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                        color = MaterialTheme.colorScheme.onSurface
                      )
                      Text(
                        text = "Identify any music video & play its 30s preview",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                      )
                    }
                  }

                  // Sleek single-line Liquid Glass URL input
                  TextField(
                    value = youtubeUrl,
                    onValueChange = onYoutubeUrlChanged,
                    placeholder = {
                      Text(
                        text = "Paste YouTube or youtu.be link...",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                      )
                    },
                    leadingIcon = {
                      Icon(
                        imageVector = Icons.Rounded.Link,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                      )
                    },
                    trailingIcon = {
                      if (youtubeUrl.isNotEmpty()) {
                        IconButton(onClick = { onYoutubeUrlChanged("") }) {
                          Icon(Icons.Rounded.Close, contentDescription = "Clear link")
                        }
                      }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                      keyboardController?.hide()
                      onAnalyzeYoutube()
                    }),
                    colors = TextFieldDefaults.colors(
                      focusedContainerColor = inputBg,
                      unfocusedContainerColor = inputBg,
                      focusedIndicatorColor = Color.Transparent,
                      unfocusedIndicatorColor = Color.Transparent,
                      disabledIndicatorColor = Color.Transparent
                    ),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier
                      .fillMaxWidth()
                      .border(1.dp, inputBorder, RoundedCornerShape(18.dp))
                      .clip(RoundedCornerShape(18.dp))
                      .testTag("youtube_url_input"),
                    singleLine = true
                  )

                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                  ) {
                    Box(
                      modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .clip(CircleShape)
                        .background(pasteBg)
                        .border(1.dp, inputBorder, CircleShape)
                        .clickable {
                          val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                          val clip = cm?.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()?.trim().orEmpty()
                          if (clip.isNotBlank()) {
                            onYoutubeUrlChanged(clip)
                          } else {
                            Toast.makeText(context, "Clipboard is empty", Toast.LENGTH_SHORT).show()
                          }
                        },
                      contentAlignment = Alignment.Center
                    ) {
                      Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                      ) {
                        Icon(
                          Icons.Rounded.ContentPaste,
                          contentDescription = null,
                          tint = MaterialTheme.colorScheme.primary,
                          modifier = Modifier.size(17.dp)
                        )
                        Text(
                          text = "Paste Link",
                          style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                          color = MaterialTheme.colorScheme.onSurface
                        )
                      }
                    }

                    Button(
                      onClick = {
                        keyboardController?.hide()
                        onAnalyzeYoutube()
                      },
                      enabled = !isAnalyzingYoutube,
                      modifier = Modifier
                        .weight(1.15f)
                        .height(48.dp)
                        .testTag("analyze_link_button"),
                      shape = CircleShape,
                      colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                      if (isAnalyzingYoutube) {
                        CircularProgressIndicator(
                          modifier = Modifier.size(17.dp),
                          strokeWidth = 2.dp,
                          color = Color.White
                        )
                      } else {
                        Icon(Icons.Rounded.GraphicEq, contentDescription = null, modifier = Modifier.size(17.dp))
                      }
                      Spacer(modifier = Modifier.width(6.dp))
                      Text(
                        text = if (isAnalyzingYoutube) "Matching..." else "Match Track",
                        fontWeight = FontWeight.Bold
                      )
                    }
                  }

                  if (!youtubeError.isNullOrBlank()) {
                    Row(
                      modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.error.copy(alpha = 0.14f))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                      verticalAlignment = Alignment.CenterVertically,
                      horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                      Icon(
                        imageVector = Icons.Rounded.ErrorOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                      )
                      Text(
                        text = youtubeError,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                      )
                    }
                  }
                }
              }
            }

            if (analyzedSong != null) {
              item {
                val (c1, c2) = rememberArtworkColors(analyzedSong.artworkUrl, analyzedSong.title)
                val cardShape = RoundedCornerShape(28.dp)
                val isMatchedCurrent = currentSong?.id == analyzedSong.id
                val dlProgress = songDownloadProgress[analyzedSong.id]
                val isSaved = analyzedSong.id in downloadedSongIds

                Box(
                  modifier = Modifier
                    .fillMaxWidth()
                    .clip(cardShape)
                    .background(if (darkTheme) Color(0xFF172034).copy(alpha = 0.80f) else Color.White.copy(alpha = 0.88f))
                    .border(1.dp, Color.White.copy(alpha = if (darkTheme) 0.28f else 0.96f), cardShape)
                ) {
                  if (analyzedSong.artworkUrl.isNotBlank()) {
                    AsyncImage(
                      model = analyzedSong.artworkUrl,
                      contentDescription = null,
                      contentScale = ContentScale.Crop,
                      modifier = Modifier
                        .matchParentSize()
                        .alpha(if (darkTheme) 0.25f else 0.16f)
                        .blur(34.dp)
                    )
                  }
                  Box(
                    modifier = Modifier
                      .matchParentSize()
                      .background(
                        Brush.horizontalGradient(
                          colors = listOf(c1.copy(alpha = 0.22f), c2.copy(alpha = 0.12f), Color.Transparent)
                        )
                      )
                  )

                  Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                  ) {
                    Row(
                      modifier = Modifier.fillMaxWidth(),
                      verticalAlignment = Alignment.CenterVertically
                    ) {
                      Box(
                        modifier = Modifier
                          .size(76.dp)
                          .clip(RoundedCornerShape(18.dp))
                          .border(1.dp, Color.White.copy(alpha = 0.34f), RoundedCornerShape(18.dp))
                      ) {
                        SongArtwork(song = analyzedSong, modifier = Modifier.fillMaxSize(), cornerRadius = 18.dp)
                      }
                      Spacer(modifier = Modifier.width(14.dp))
                      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Surface(
                          shape = CircleShape,
                          color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                        ) {
                          Text(
                            text = "MATCHED PREVIEW",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                          )
                        }
                        Text(
                          text = analyzedSong.title,
                          style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                          color = MaterialTheme.colorScheme.onSurface,
                          maxLines = 1,
                          overflow = TextOverflow.Ellipsis
                        )
                        Text(
                          text = analyzedSong.artist,
                          style = MaterialTheme.typography.bodySmall,
                          color = MaterialTheme.colorScheme.onSurfaceVariant,
                          maxLines = 1,
                          overflow = TextOverflow.Ellipsis
                        )
                      }
                    }

                    Row(
                      modifier = Modifier.fillMaxWidth(),
                      horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                      Button(
                        onClick = { onSongClick(analyzedSong, listOf(analyzedSong)) },
                        modifier = Modifier.weight(1f).height(46.dp),
                        shape = CircleShape
                      ) {
                        Icon(
                          imageVector = if (isMatchedCurrent && isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                          contentDescription = null,
                          modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (isMatchedCurrent && isPlaying) "Playing" else "Play 30s", fontWeight = FontWeight.Bold)
                      }

                      OutlinedButton(
                        onClick = { onDownloadClick(analyzedSong) },
                        modifier = Modifier.weight(1f).height(46.dp),
                        shape = CircleShape,
                        border = androidx.compose.foundation.BorderStroke(
                          1.dp,
                          MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                        )
                      ) {
                        if (dlProgress != null) {
                          CircularProgressIndicator(
                            progress = { dlProgress.coerceIn(0f, 1f) },
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                          )
                        } else {
                          Icon(
                            imageVector = if (isSaved) Icons.Rounded.DownloadDone else Icons.Rounded.Download,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                          )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (isSaved) "Saved" else "Save 30s", fontWeight = FontWeight.Bold)
                      }
                    }
                  }
                }
              }
            } else {
              // Intentional, sleek tips card so the lower canvas feels balanced rather than empty
              item {
                GlassCard(
                  darkTheme = darkTheme,
                  cornerRadius = 26.dp,
                  modifier = Modifier.fillMaxWidth()
                ) {
                  Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                  ) {
                    Text(
                      text = "How it works",
                      style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                      color = MaterialTheme.colorScheme.onSurface
                    )
                    MatcherGuideRow(
                      icon = Icons.Rounded.ContentCopy,
                      title = "Copy any YouTube share link",
                      subtitle = "Supports youtube.com, youtu.be, and music.youtube.com",
                      iconTint = MaterialTheme.colorScheme.primary
                    )
                    MatcherGuideRow(
                      icon = Icons.Rounded.GraphicEq,
                      title = "Instant 30s audio match",
                      subtitle = "Matches the official track with cover art & synced lyrics",
                      iconTint = Color(0xFF14B8A6)
                    )
                    MatcherGuideRow(
                      icon = Icons.Rounded.Download,
                      title = "Save offline anytime",
                      subtitle = "Keep matched 30s clips directly in Music/AURIX",
                      iconTint = Color(0xFFEC4899)
                    )
                  }
                }
              }
            }
          }
        } else {
          Column(modifier = Modifier.fillMaxSize()) {
            val fieldBg = if (darkTheme) Color(0xFF172034).copy(alpha = 0.78f) else Color.White.copy(alpha = 0.88f)
            val fieldBorder = if (darkTheme) Color.White.copy(alpha = 0.18f) else Color.White

            TextField(
              value = searchQuery,
              onValueChange = onSearchQueryChanged,
              placeholder = { Text("Search songs, artists or TikTok hits...") },
              leadingIcon = {
                Icon(
                  Icons.Rounded.Search,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.primary
                )
              },
              trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                  IconButton(onClick = { onSearchQueryChanged("") }) {
                    Icon(Icons.Rounded.Close, contentDescription = "Clear search")
                  }
                }
              },
              keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
              keyboardActions = KeyboardActions(onSearch = {
                keyboardController?.hide()
                if (searchQuery.isNotBlank()) onSearchQueryChanged(searchQuery)
              }),
              colors = TextFieldDefaults.colors(
                focusedContainerColor = fieldBg,
                unfocusedContainerColor = fieldBg,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent
              ),
              shape = CircleShape,
              modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, fieldBorder, CircleShape)
                .clip(CircleShape)
                .testTag("search_input_field"),
              singleLine = true
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (searchQuery.isBlank()) {
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                quickTags.forEach { tag ->
                  val chipBg = if (darkTheme) Color.White.copy(alpha = 0.09f) else Color.White.copy(alpha = 0.85f)
                  val chipBorder = if (darkTheme) Color.White.copy(alpha = 0.16f) else Color.White
                  Box(
                    modifier = Modifier
                      .clip(CircleShape)
                      .background(chipBg)
                      .border(1.dp, chipBorder, CircleShape)
                      .clickable { onSearchQueryChanged(tag) }
                      .padding(horizontal = 14.dp, vertical = 8.dp)
                  ) {
                    Text(
                      text = tag,
                      style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                      color = MaterialTheme.colorScheme.onSurface
                    )
                  }
                }
              }
              Spacer(modifier = Modifier.height(12.dp))
            }

            when {
              isSearching -> {
                Box(modifier = Modifier.fillMaxSize().padding(bottom = 80.dp), contentAlignment = Alignment.Center) {
                  CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
              }
              !searchError.isNullOrBlank() -> {
                Box(modifier = Modifier.fillMaxSize().padding(bottom = 110.dp), contentAlignment = Alignment.Center) {
                  GlassCard(darkTheme = darkTheme, cornerRadius = 24.dp, modifier = Modifier.fillMaxWidth()) {
                    Column(
                      modifier = Modifier.fillMaxWidth().padding(22.dp),
                      horizontalAlignment = Alignment.CenterHorizontally,
                      verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                      Text(searchError, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                      OutlinedButton(onClick = { onSearchQueryChanged(searchQuery) }, shape = CircleShape) {
                        Text("Try Again")
                      }
                    }
                  }
                }
              }
              searchResults.isEmpty() && searchQuery.isNotBlank() -> {
                Box(modifier = Modifier.fillMaxSize().padding(bottom = 110.dp), contentAlignment = Alignment.Center) {
                  GlassCard(darkTheme = darkTheme, cornerRadius = 24.dp, modifier = Modifier.fillMaxWidth()) {
                    Column(
                      modifier = Modifier.fillMaxWidth().padding(24.dp),
                      horizontalAlignment = Alignment.CenterHorizontally,
                      verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                      Icon(Icons.Rounded.SearchOff, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp))
                      Text(
                        text = "No 30s previews found",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                      )
                      Text(
                        text = "Try searching another track title or artist name.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                      )
                    }
                  }
                }
              }
              searchResults.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize().padding(bottom = 110.dp), contentAlignment = Alignment.Center) {
                  GlassCard(darkTheme = darkTheme, cornerRadius = 24.dp, modifier = Modifier.fillMaxWidth()) {
                    Column(
                      modifier = Modifier.fillMaxWidth().padding(24.dp),
                      horizontalAlignment = Alignment.CenterHorizontally,
                      verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                      Box(
                        modifier = Modifier
                          .size(52.dp)
                          .clip(CircleShape)
                          .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                      ) {
                        Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(26.dp))
                      }
                      Text(
                        text = "iTunes 30s Music Search",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                      )
                      Text(
                        text = "Type a song or artist above, or pick a quick trend tag.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                      )
                    }
                  }
                }
              }
              else -> {
                LazyColumn(
                  modifier = Modifier.fillMaxSize(),
                  verticalArrangement = Arrangement.spacedBy(6.dp),
                  contentPadding = PaddingValues(bottom = if (currentSong != null) 205.dp else 135.dp)
                ) {
                  itemsIndexed(searchResults, key = { _, song -> song.id }) { index, song ->
                    SongDownloadRow(
                      song = song,
                      index = index + 1,
                      isPlaying = isPlaying,
                      isCurrent = currentSong?.id == song.id,
                      reducedMotion = reducedMotion,
                      downloadProgress = songDownloadProgress[song.id],
                      isDownloaded = song.id in downloadedSongIds,
                      onSongClick = { onSongClick(song, searchResults) },
                      onFavoriteClick = { onFavoriteClick(song) },
                      onDownloadClick = { onDownloadClick(song) }
                    )
                  }
                }
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun MatcherGuideRow(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  title: String,
  subtitle: String,
  iconTint: Color
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    Box(
      modifier = Modifier
        .size(36.dp)
        .clip(RoundedCornerShape(11.dp))
        .background(iconTint.copy(alpha = 0.16f)),
      contentAlignment = Alignment.Center
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = iconTint,
        modifier = Modifier.size(18.dp)
      )
    }
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = title,
        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
        color = MaterialTheme.colorScheme.onSurface
      )
      Text(
        text = subtitle,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
  }
}
