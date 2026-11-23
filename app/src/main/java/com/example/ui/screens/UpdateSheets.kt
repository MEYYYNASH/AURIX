package com.example.ui.screens

import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.BuildConfig
import com.example.R
import com.example.data.APP_RELEASE_DATE
import com.example.data.RemoteVersionInfo
import com.example.data.UpdateManager
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Locale

private val SheetEaseOut = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)

private val SheetGlassBg = Color(0xFF0A0810)
private val SheetGlassInner = Color(0xFF120E1D)
private val SheetGlassCard = Color(0xFF161124)
private val SheetGlassText = Color(0xFFF4F2FB)
private val SheetGlassMuted = Color(0xFF8F8AA6)
private val SheetEmerald = Color(0xFF10B981)

private data class ParsedReleaseNote(
  val indexLabel: String,
  val categoryTitle: String?,
  val bodyText: String,
  val icon: ImageVector,
  val accentColor: Color
)

private fun parseReleaseNotes(
  notes: List<String>,
  primaryAccent: Color,
  lightAccent: Color
): List<ParsedReleaseNote> {
  val palette = listOf(
    primaryAccent,
    lightAccent,
    Color(0xFF38BDF8),
    SheetEmerald,
    lerp(primaryAccent, Color(0xFFF472B6), 0.45f)
  )
  return notes.mapIndexed { idx, raw ->
    val clean = raw.trim().removePrefix("•").removePrefix("-").trim()
    val colonIdx = clean.indexOf(':')
    val dashIdx = clean.indexOf(" — ")
    val (title, body) = when {
      colonIdx in 3..72 -> {
        clean.substring(0, colonIdx).trim() to clean.substring(colonIdx + 1).trim()
      }
      dashIdx in 3..64 -> {
        clean.substring(0, dashIdx).trim() to clean.substring(dashIdx + 3).trim()
      }
      else -> null to clean
    }

    val lower = clean.lowercase(Locale.ROOT)
    val icon = when {
      lower.contains("album") || lower.contains("vinyl") || lower.contains("cover") -> Icons.Rounded.Album
      lower.contains("download") || lower.contains("queue") || lower.contains("ota") -> Icons.Rounded.Download
      lower.contains("visualizer") || lower.contains("equalizer") || lower.contains("audio") || lower.contains("play") -> Icons.Rounded.GraphicEq
      lower.contains("security") || lower.contains("sha") || lower.contains("shield") || lower.contains("fix") -> Icons.Rounded.VerifiedUser
      else -> Icons.Rounded.AutoAwesome
    }

    ParsedReleaseNote(
      indexLabel = "%02d".format(Locale.US, idx + 1),
      categoryTitle = title,
      bodyText = body.ifBlank { clean },
      icon = icon,
      accentColor = palette[idx % palette.size]
    )
  }
}

private fun formatReleaseSheetDate(rawDate: String): String {
  val trimmed = rawDate.trim()
  if (trimmed.isBlank()) return trimmed
  return runCatching {
    val parser = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    val parsed = parser.parse(trimmed) ?: return@runCatching trimmed
    SimpleDateFormat("MMM d, yyyy", Locale.US).format(parsed)
  }.getOrDefault(trimmed)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReleaseNotesBottomSheet(
  latestRelease: RemoteVersionInfo?,
  installedVersionName: String,
  installedVersionCode: Long,
  canDownloadUpdate: Boolean,
  onDownloadAndInstall: () -> Unit,
  onDismiss: () -> Unit,
  reducedMotion: Boolean = false
) {
  val sheetViolet = MaterialTheme.colorScheme.primary
  val sheetLightViolet = lerp(sheetViolet, Color.White, 0.32f)
  val sheetMagenta = lerp(sheetViolet, Color(0xFFD946EF), 0.28f)

  val versionNameClean = (latestRelease?.versionName ?: installedVersionName)
    .trim()
    .removePrefix("v")
    .removePrefix("V")
  val versionLabel = "v$versionNameClean"
  val versionCode = latestRelease?.versionCode ?: installedVersionCode
  val rawReleaseDate = latestRelease?.releaseDate?.ifBlank { APP_RELEASE_DATE } ?: APP_RELEASE_DATE
  val formattedDate = remember(rawReleaseDate) { formatReleaseSheetDate(rawReleaseDate) }
  val notesList = latestRelease?.notes?.takeIf { it.isNotEmpty() } ?: UpdateManager.CURRENT_RELEASE_NOTES
  val parsedNotes = remember(notesList, sheetViolet, sheetLightViolet) {
    parseReleaseNotes(notesList, sheetViolet, sheetLightViolet)
  }

  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  val sheetShape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)

  val borderBrush = Brush.linearGradient(
    colors = listOf(
      Color.White.copy(alpha = 0.18f),
      sheetViolet.copy(alpha = 0.38f),
      sheetLightViolet.copy(alpha = 0.16f),
      Color.White.copy(alpha = 0.08f)
    )
  )

  val pulseTransition = rememberInfiniteTransition(label = "release_notes_pulse")
  val dotAlpha by if (reducedMotion) {
    remember { androidx.compose.runtime.mutableFloatStateOf(0.9f) }
  } else {
    pulseTransition.animateFloat(
      initialValue = 0.45f,
      targetValue = 1f,
      animationSpec = infiniteRepeatable(
        animation = tween(durationMillis = 1100, easing = FastOutSlowInEasing),
        repeatMode = RepeatMode.Reverse
      ),
      label = "release_dot_alpha"
    )
  }

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    shape = sheetShape,
    containerColor = Color.Transparent,
    contentColor = SheetGlassText,
    scrimColor = Color(0xFF05040A).copy(alpha = 0.72f),
    tonalElevation = 0.dp,
    dragHandle = null
  ) {
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .shadow(
          elevation = 24.dp,
          shape = sheetShape,
          ambientColor = sheetViolet.copy(alpha = 0.32f),
          spotColor = sheetMagenta.copy(alpha = 0.26f)
        )
        .clip(sheetShape)
        .background(SheetGlassBg)
        .drawBehind {
          drawCircle(
            brush = Brush.radialGradient(
              colors = listOf(sheetViolet.copy(alpha = 0.24f), Color.Transparent),
              center = Offset(size.width * 0.16f, size.height * 0.12f),
              radius = size.maxDimension * 0.65f
            ),
            radius = size.maxDimension * 0.65f,
            center = Offset(size.width * 0.16f, size.height * 0.12f)
          )
          drawCircle(
            brush = Brush.radialGradient(
              colors = listOf(sheetMagenta.copy(alpha = 0.15f), Color.Transparent),
              center = Offset(size.width * 0.88f, size.height * 0.78f),
              radius = size.maxDimension * 0.60f
            ),
            radius = size.maxDimension * 0.60f,
            center = Offset(size.width * 0.88f, size.height * 0.78f)
          )
          // Top edge accent glow line
          drawRect(
            brush = Brush.horizontalGradient(
              colors = listOf(
                Color.Transparent,
                sheetViolet.copy(alpha = 0.85f),
                sheetLightViolet,
                sheetViolet.copy(alpha = 0.85f),
                Color.Transparent
              )
            ),
            topLeft = Offset.Zero,
            size = Size(size.width, 2.5.dp.toPx())
          )
          drawRect(
            brush = Brush.verticalGradient(
              colors = listOf(sheetLightViolet.copy(alpha = 0.20f), Color.Transparent),
              startY = 0f,
              endY = 28.dp.toPx()
            ),
            topLeft = Offset.Zero,
            size = Size(size.width, 28.dp.toPx())
          )
        }
        .border(width = 1.dp, brush = borderBrush, shape = sheetShape)
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 20.dp, vertical = 12.dp)
          .windowInsetsPadding(WindowInsets.navigationBars),
        verticalArrangement = Arrangement.spacedBy(14.dp)
      ) {
        // Glowing gradient drag handle
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.dp, bottom = 2.dp),
          contentAlignment = Alignment.Center
        ) {
          Box(
            modifier = Modifier
              .width(44.dp)
              .height(4.5.dp)
              .clip(CircleShape)
              .background(
                Brush.horizontalGradient(
                  colors = listOf(
                    sheetViolet.copy(alpha = 0.45f),
                    sheetLightViolet.copy(alpha = 0.92f),
                    sheetViolet.copy(alpha = 0.45f)
                  )
                )
              )
          )
        }

        // Staggered Item 1: Top Header Row
        ReleaseSheetStaggeredItem(delayMillis = 0, reducedMotion = reducedMotion) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
          ) {
            Box(
              modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(
                  Brush.linearGradient(
                    colors = listOf(
                      sheetViolet.copy(alpha = 0.28f),
                      sheetMagenta.copy(alpha = 0.14f)
                    )
                  )
                )
                .border(
                  1.dp,
                  sheetLightViolet.copy(alpha = 0.36f),
                  RoundedCornerShape(15.dp)
                ),
              contentAlignment = Alignment.Center
            ) {
              Icon(
                imageVector = Icons.Rounded.NewReleases,
                contentDescription = null,
                tint = sheetLightViolet,
                modifier = Modifier.size(22.dp)
              )
            }

            Column(
              modifier = Modifier.weight(1f),
              verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
              Text(
                text = stringResource(id = R.string.settings_release_notes),
                style = MaterialTheme.typography.titleLarge.copy(
                  fontWeight = FontWeight.Bold,
                  fontSize = 21.sp,
                  lineHeight = 26.sp
                ),
                color = SheetGlassText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
              Text(
                text = stringResource(
                  id = R.string.settings_release_notes_title,
                  versionLabel,
                  versionCode
                ),
                style = MaterialTheme.typography.bodySmall.copy(lineHeight = 17.sp),
                color = sheetLightViolet,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
            }

            Surface(
              shape = CircleShape,
              color = Color.White.copy(alpha = 0.06f),
              border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
              modifier = Modifier
                .minimumInteractiveComponentSize()
                .size(38.dp)
                .clip(CircleShape)
                .clickable { onDismiss() }
                .testTag("release_notes_close_button")
            ) {
              Box(contentAlignment = Alignment.Center) {
                Icon(
                  imageVector = Icons.Rounded.Close,
                  contentDescription = stringResource(id = R.string.cd_close_sheet),
                  tint = SheetGlassMuted,
                  modifier = Modifier.size(18.dp)
                )
              }
            }
          }
        }

        // Staggered Item 2: Hero Version & Metadata Dark Glass Panel
        ReleaseSheetStaggeredItem(delayMillis = 45, reducedMotion = reducedMotion) {
          val statusColor = if (canDownloadUpdate) Color(0xFFF59E0B) else SheetEmerald
          val statusLabel = if (canDownloadUpdate) {
            stringResource(id = R.string.settings_status_pill_available)
          } else {
            stringResource(id = R.string.settings_status_pill_up_to_date)
          }

          Surface(
            shape = RoundedCornerShape(24.dp),
            color = SheetGlassInner.copy(alpha = 0.88f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
            modifier = Modifier.fillMaxWidth()
          ) {
            Column(
              modifier = Modifier.padding(15.dp),
              verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
              ) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                  Text(
                    text = versionLabel,
                    style = MaterialTheme.typography.headlineMedium.copy(
                      fontWeight = FontWeight.ExtraBold,
                      fontSize = 27.sp,
                      lineHeight = 32.sp,
                      brush = Brush.linearGradient(
                        colors = listOf(SheetGlassText, sheetLightViolet, sheetViolet)
                      )
                    )
                  )
                  Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = sheetViolet.copy(alpha = 0.16f),
                    border = BorderStroke(1.dp, sheetLightViolet.copy(alpha = 0.28f))
                  ) {
                    Text(
                      text = "code $versionCode",
                      style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                      ),
                      color = sheetLightViolet,
                      modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                  }
                }

                Surface(
                  shape = RoundedCornerShape(999.dp),
                  color = statusColor.copy(alpha = 0.14f),
                  border = BorderStroke(1.dp, statusColor.copy(alpha = 0.30f))
                ) {
                  Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                  ) {
                    Box(
                      modifier = Modifier
                        .size(6.5.dp)
                        .clip(CircleShape)
                        .background(statusColor.copy(alpha = dotAlpha))
                    )
                    Text(
                      text = statusLabel,
                      style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                      ),
                      color = statusColor
                    )
                  }
                }
              }

              // Date & Verification Metadata Pills
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                Surface(
                  shape = RoundedCornerShape(12.dp),
                  color = Color.White.copy(alpha = 0.04f),
                  border = BorderStroke(1.dp, Color.White.copy(alpha = 0.07f)),
                  modifier = Modifier.weight(1f)
                ) {
                  Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                  ) {
                    Icon(
                      imageVector = Icons.Rounded.CalendarToday,
                      contentDescription = null,
                      tint = sheetLightViolet,
                      modifier = Modifier.size(14.dp)
                    )
                    Text(
                      text = formattedDate,
                      style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.5.sp
                      ),
                      color = SheetGlassText,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis
                    )
                  }
                }

                Surface(
                  shape = RoundedCornerShape(12.dp),
                  color = Color.White.copy(alpha = 0.04f),
                  border = BorderStroke(1.dp, Color.White.copy(alpha = 0.07f)),
                  modifier = Modifier.weight(1f)
                ) {
                  Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                  ) {
                    Icon(
                      imageVector = Icons.Rounded.VerifiedUser,
                      contentDescription = null,
                      tint = SheetEmerald,
                      modifier = Modifier.size(14.dp)
                    )
                    Text(
                      text = "${parsedNotes.size} Highlights",
                      style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.5.sp
                      ),
                      color = SheetGlassText,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis
                    )
                  }
                }
              }
            }
          }
        }

        // Staggered Item 3: Scrollable Release Notes Cards
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 340.dp)
            .verticalScroll(rememberScrollState()),
          verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          if (parsedNotes.isEmpty()) {
            Surface(
              shape = RoundedCornerShape(20.dp),
              color = SheetGlassCard.copy(alpha = 0.78f),
              border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
              modifier = Modifier.fillMaxWidth()
            ) {
              Text(
                text = stringResource(id = R.string.settings_no_changelog),
                style = MaterialTheme.typography.bodyMedium,
                color = SheetGlassMuted,
                modifier = Modifier.padding(18.dp)
              )
            }
          } else {
            parsedNotes.forEachIndexed { index, item ->
              ReleaseSheetStaggeredItem(
                delayMillis = 90 + index * 45,
                reducedMotion = reducedMotion
              ) {
                ReleaseNoteGlassItemCard(note = item)
              }
            }
          }
        }

        // Staggered Item 4: Action Buttons
        ReleaseSheetStaggeredItem(delayMillis = 210, reducedMotion = reducedMotion) {
          if (canDownloadUpdate && latestRelease != null) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
              Surface(
                shape = RoundedCornerShape(18.dp),
                color = Color.White.copy(alpha = 0.06f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                modifier = Modifier
                  .weight(0.9f)
                  .height(52.dp)
                  .clip(RoundedCornerShape(18.dp))
                  .clickable { onDismiss() }
              ) {
                Box(contentAlignment = Alignment.Center) {
                  Text(
                    text = stringResource(id = R.string.whats_new_got_it),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = SheetGlassText
                  )
                }
              }

              Box(
                modifier = Modifier
                  .weight(1.4f)
                  .height(52.dp)
                  .shadow(
                    elevation = 14.dp,
                    shape = RoundedCornerShape(18.dp),
                    ambientColor = sheetViolet.copy(alpha = 0.44f),
                    spotColor = sheetLightViolet.copy(alpha = 0.38f)
                  )
                  .clip(RoundedCornerShape(18.dp))
                  .background(
                    Brush.horizontalGradient(
                      colors = listOf(sheetViolet, sheetLightViolet)
                    )
                  )
                  .border(1.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(18.dp))
                  .clickable {
                    onDismiss()
                    onDownloadAndInstall()
                  }
                  .testTag("release_notes_download_button"),
                contentAlignment = Alignment.Center
              ) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                  Icon(
                    imageVector = Icons.Rounded.SystemUpdate,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(19.dp)
                  )
                  Text(
                    text = stringResource(id = R.string.settings_download_install),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                  )
                }
              }
            }
          } else {
            Box(
              modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .shadow(
                  elevation = 12.dp,
                  shape = RoundedCornerShape(18.dp),
                  ambientColor = sheetViolet.copy(alpha = 0.40f),
                  spotColor = sheetLightViolet.copy(alpha = 0.32f)
                )
                .clip(RoundedCornerShape(18.dp))
                .background(
                  Brush.horizontalGradient(
                    colors = listOf(sheetViolet, sheetLightViolet)
                  )
                )
                .border(1.dp, Color.White.copy(alpha = 0.20f), RoundedCornerShape(18.dp))
                .clickable { onDismiss() },
              contentAlignment = Alignment.Center
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                Icon(
                  imageVector = Icons.Rounded.CheckCircle,
                  contentDescription = null,
                  tint = Color.White,
                  modifier = Modifier.size(18.dp)
                )
                Text(
                  text = stringResource(id = R.string.whats_new_got_it),
                  style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                  color = Color.White
                )
              }
            }
          }
        }

        Spacer(modifier = Modifier.height(4.dp))
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhatsNewBottomSheet(
  reducedMotion: Boolean,
  onOpenUpdatesTab: () -> Unit,
  onPreviewNavigation: (() -> Unit)? = null,
  onDismiss: () -> Unit
) {
  val sheetViolet = MaterialTheme.colorScheme.primary
  val sheetLightViolet = lerp(sheetViolet, Color.White, 0.32f)
  val sheetMagenta = lerp(sheetViolet, Color(0xFFD946EF), 0.28f)

  val parsedNotes = remember(sheetViolet, sheetLightViolet) {
    parseReleaseNotes(UpdateManager.CURRENT_RELEASE_NOTES, sheetViolet, sheetLightViolet)
  }
  val formattedDate = remember { formatReleaseSheetDate(APP_RELEASE_DATE) }
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  val sheetShape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)

  val borderBrush = Brush.linearGradient(
    colors = listOf(
      Color.White.copy(alpha = 0.18f),
      sheetViolet.copy(alpha = 0.38f),
      sheetLightViolet.copy(alpha = 0.16f),
      Color.White.copy(alpha = 0.08f)
    )
  )

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    shape = sheetShape,
    containerColor = Color.Transparent,
    contentColor = SheetGlassText,
    scrimColor = Color(0xFF05040A).copy(alpha = 0.72f),
    tonalElevation = 0.dp,
    dragHandle = null
  ) {
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .shadow(
          elevation = 24.dp,
          shape = sheetShape,
          ambientColor = sheetViolet.copy(alpha = 0.32f),
          spotColor = sheetMagenta.copy(alpha = 0.26f)
        )
        .clip(sheetShape)
        .background(SheetGlassBg)
        .drawBehind {
          drawCircle(
            brush = Brush.radialGradient(
              colors = listOf(sheetViolet.copy(alpha = 0.24f), Color.Transparent),
              center = Offset(size.width * 0.18f, size.height * 0.14f),
              radius = size.maxDimension * 0.65f
            ),
            radius = size.maxDimension * 0.65f,
            center = Offset(size.width * 0.18f, size.height * 0.14f)
          )
          drawCircle(
            brush = Brush.radialGradient(
              colors = listOf(sheetMagenta.copy(alpha = 0.15f), Color.Transparent),
              center = Offset(size.width * 0.88f, size.height * 0.78f),
              radius = size.maxDimension * 0.60f
            ),
            radius = size.maxDimension * 0.60f,
            center = Offset(size.width * 0.88f, size.height * 0.78f)
          )
          drawRect(
            brush = Brush.horizontalGradient(
              colors = listOf(
                Color.Transparent,
                sheetViolet.copy(alpha = 0.85f),
                sheetLightViolet,
                sheetViolet.copy(alpha = 0.85f),
                Color.Transparent
              )
            ),
            topLeft = Offset.Zero,
            size = Size(size.width, 2.5.dp.toPx())
          )
          drawRect(
            brush = Brush.verticalGradient(
              colors = listOf(sheetLightViolet.copy(alpha = 0.20f), Color.Transparent),
              startY = 0f,
              endY = 28.dp.toPx()
            ),
            topLeft = Offset.Zero,
            size = Size(size.width, 28.dp.toPx())
          )
        }
        .border(width = 1.dp, brush = borderBrush, shape = sheetShape)
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 20.dp, vertical = 12.dp)
          .windowInsetsPadding(WindowInsets.navigationBars),
        verticalArrangement = Arrangement.spacedBy(14.dp)
      ) {
        // Glowing drag handle
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.dp, bottom = 2.dp),
          contentAlignment = Alignment.Center
        ) {
          Box(
            modifier = Modifier
              .width(44.dp)
              .height(4.5.dp)
              .clip(CircleShape)
              .background(
                Brush.horizontalGradient(
                  colors = listOf(
                    sheetViolet.copy(alpha = 0.45f),
                    sheetLightViolet.copy(alpha = 0.92f),
                    sheetViolet.copy(alpha = 0.45f)
                  )
                )
              )
          )
        }

        // Header with animated UpdateLogo
        ReleaseSheetStaggeredItem(delayMillis = 0, reducedMotion = reducedMotion) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
          ) {
            UpdateLogo(
              state = UpdateLogoState.Done,
              size = 64.dp,
              reducedMotion = reducedMotion,
              modifier = Modifier.testTag("whats_new_logo")
            )
            Column(
              modifier = Modifier.weight(1f),
              verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                Surface(
                  shape = RoundedCornerShape(999.dp),
                  color = sheetViolet.copy(alpha = 0.18f),
                  border = BorderStroke(1.dp, sheetLightViolet.copy(alpha = 0.34f))
                ) {
                  Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                  ) {
                    Icon(
                      imageVector = Icons.Rounded.AutoAwesome,
                      contentDescription = null,
                      tint = sheetLightViolet,
                      modifier = Modifier.size(13.dp)
                    )
                    Text(
                      text = "v${BuildConfig.VERSION_NAME}",
                      style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                      color = sheetLightViolet
                    )
                  }
                }
                Text(
                  text = formattedDate,
                  style = MaterialTheme.typography.labelSmall,
                  color = SheetGlassMuted
                )
              }
              Text(
                text = stringResource(id = R.string.whats_new_title, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.titleLarge.copy(
                  fontWeight = FontWeight.Bold,
                  fontSize = 21.sp,
                  lineHeight = 26.sp
                ),
                color = SheetGlassText
              )
            }

            Surface(
              shape = CircleShape,
              color = Color.White.copy(alpha = 0.06f),
              border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
              modifier = Modifier
                .minimumInteractiveComponentSize()
                .size(38.dp)
                .clip(CircleShape)
                .clickable { onDismiss() }
                .testTag("whats_new_close_button")
            ) {
              Box(contentAlignment = Alignment.Center) {
                Icon(
                  imageVector = Icons.Rounded.Close,
                  contentDescription = stringResource(id = R.string.cd_close_sheet),
                  tint = SheetGlassMuted,
                  modifier = Modifier.size(18.dp)
                )
              }
            }
          }
        }

        // Scrollable highlight cards
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 330.dp)
            .verticalScroll(rememberScrollState()),
          verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          parsedNotes.forEachIndexed { index, item ->
            ReleaseSheetStaggeredItem(
              delayMillis = 60 + index * 45,
              reducedMotion = reducedMotion
            ) {
              ReleaseNoteGlassItemCard(note = item)
            }
          }
        }

        if (onPreviewNavigation != null) {
          ReleaseSheetStaggeredItem(delayMillis = 180, reducedMotion = reducedMotion) {
            Surface(
              shape = RoundedCornerShape(18.dp),
              color = sheetViolet.copy(alpha = 0.14f),
              border = BorderStroke(1.dp, sheetLightViolet.copy(alpha = 0.34f)),
              modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(18.dp))
                .clickable {
                  onDismiss()
                  onPreviewNavigation()
                }
                .testTag("whats_new_preview_nav_button")
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
              ) {
                Icon(
                  imageVector = Icons.Rounded.AutoAwesome,
                  contentDescription = null,
                  tint = sheetLightViolet,
                  modifier = Modifier.size(17.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                  text = stringResource(id = R.string.nav_preview_whats_new_cta),
                  style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                  color = SheetGlassText
                )
              }
            }
          }
        }

        ReleaseSheetStaggeredItem(delayMillis = 220, reducedMotion = reducedMotion) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            Surface(
              shape = RoundedCornerShape(18.dp),
              color = Color.White.copy(alpha = 0.06f),
              border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
              modifier = Modifier
                .weight(1f)
                .height(50.dp)
                .clip(RoundedCornerShape(18.dp))
                .clickable {
                  onDismiss()
                  onOpenUpdatesTab()
                }
                .testTag("whats_new_settings_button")
            ) {
              Box(contentAlignment = Alignment.Center) {
                Text(
                  text = stringResource(id = R.string.whats_new_view_updates),
                  style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                  color = SheetGlassText
                )
              }
            }

            Box(
              modifier = Modifier
                .weight(1.3f)
                .height(50.dp)
                .shadow(
                  elevation = 14.dp,
                  shape = RoundedCornerShape(18.dp),
                  ambientColor = sheetViolet.copy(alpha = 0.42f),
                  spotColor = sheetLightViolet.copy(alpha = 0.36f)
                )
                .clip(RoundedCornerShape(18.dp))
                .background(
                  Brush.horizontalGradient(
                    colors = listOf(sheetViolet, sheetLightViolet)
                  )
                )
                .border(1.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(18.dp))
                .clickable { onDismiss() }
                .testTag("whats_new_dismiss_button"),
              contentAlignment = Alignment.Center
            ) {
              Text(
                text = stringResource(id = R.string.whats_new_got_it),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = Color.White
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(4.dp))
      }
    }
  }
}

@Composable
private fun ReleaseNoteGlassItemCard(note: ParsedReleaseNote) {
  val cardShape = RoundedCornerShape(20.dp)
  Surface(
    shape = cardShape,
    color = SheetGlassCard.copy(alpha = 0.86f),
    border = BorderStroke(
      width = 1.dp,
      brush = Brush.horizontalGradient(
        colors = listOf(
          note.accentColor.copy(alpha = 0.36f),
          Color.White.copy(alpha = 0.08f)
        )
      )
    ),
    modifier = Modifier.fillMaxWidth()
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .drawBehind {
          drawCircle(
            brush = Brush.radialGradient(
              colors = listOf(note.accentColor.copy(alpha = 0.14f), Color.Transparent),
              center = Offset(38.dp.toPx(), size.height * 0.45f),
              radius = 96.dp.toPx()
            ),
            radius = 96.dp.toPx(),
            center = Offset(38.dp.toPx(), size.height * 0.45f)
          )
        }
        .padding(14.dp),
      horizontalArrangement = Arrangement.spacedBy(12.dp),
      verticalAlignment = Alignment.Top
    ) {
      // Glowing vector icon capsule with index badge
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
      ) {
        Box(
          modifier = Modifier
            .size(38.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(
              Brush.linearGradient(
                colors = listOf(
                  note.accentColor.copy(alpha = 0.26f),
                  note.accentColor.copy(alpha = 0.10f)
                )
              )
            )
            .border(
              1.dp,
              note.accentColor.copy(alpha = 0.42f),
              RoundedCornerShape(13.dp)
            ),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = note.icon,
            contentDescription = null,
            tint = lerp(note.accentColor, Color.White, 0.28f),
            modifier = Modifier.size(19.dp)
          )
        }
        Text(
          text = note.indexLabel,
          style = MaterialTheme.typography.labelSmall.copy(
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp,
            letterSpacing = 0.6.sp
          ),
          color = note.accentColor.copy(alpha = 0.85f)
        )
      }

      Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(4.dp)
      ) {
        if (!note.categoryTitle.isNullOrBlank()) {
          Text(
            text = note.categoryTitle,
            style = MaterialTheme.typography.titleSmall.copy(
              fontWeight = FontWeight.Bold,
              fontSize = 14.5.sp,
              lineHeight = 19.sp
            ),
            color = SheetGlassText
          )
          Text(
            text = note.bodyText,
            style = MaterialTheme.typography.bodySmall.copy(
              fontSize = 13.sp,
              lineHeight = 19.sp
            ),
            color = SheetGlassMuted
          )
        } else {
          Text(
            text = note.bodyText,
            style = MaterialTheme.typography.bodyMedium.copy(
              fontWeight = FontWeight.Medium,
              fontSize = 13.5.sp,
              lineHeight = 20.sp
            ),
            color = SheetGlassText
          )
        }
      }
    }
  }
}

@Composable
private fun ReleaseSheetStaggeredItem(
  delayMillis: Int,
  reducedMotion: Boolean,
  content: @Composable () -> Unit
) {
  val density = LocalDensity.current
  val progress = remember { Animatable(if (reducedMotion) 1f else 0f) }

  LaunchedEffect(reducedMotion) {
    if (reducedMotion) {
      progress.snapTo(1f)
    } else {
      progress.snapTo(0f)
      delay(delayMillis.toLong())
      progress.animateTo(
        targetValue = 1f,
        animationSpec = tween(durationMillis = 380, easing = SheetEaseOut)
      )
    }
  }

  val p = progress.value
  val slidePx = with(density) { 18.dp.toPx() } * (1f - p)
  val blurDp = if (!reducedMotion && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    ((1f - p) * 5f).coerceIn(0f, 5f).dp
  } else {
    0.dp
  }

  Box(
    modifier = Modifier
      .fillMaxWidth()
      .then(if (blurDp > 0.1.dp) Modifier.blur(blurDp) else Modifier)
      .graphicsLayer {
        alpha = p
        translationY = slidePx
      }
  ) {
    content()
  }
}
