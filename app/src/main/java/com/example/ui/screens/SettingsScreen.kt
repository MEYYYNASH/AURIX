package com.example.ui.screens

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.BuildConfig
import com.example.data.ApkDownloadState
import com.example.data.GitHubRelease
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
  currentTheme: String,
  currentAccentHex: String,
  currentLanguage: String,
  dynamicBg: Boolean,
  reducedMotion: Boolean,
  darkTheme: Boolean,
  hasCurrentSong: Boolean,
  githubOwner: String,
  githubRepo: String,
  updateStatus: String,
  latestRelease: GitHubRelease?,
  apkDownloadState: ApkDownloadState,
  onGithubOwnerChanged: (String) -> Unit,
  onGithubRepoChanged: (String) -> Unit,
  onCheckUpdate: (Context) -> Unit,
  onDownloadUpdate: (Context) -> Unit,
  onInstallUpdate: (Context) -> Unit,
  onOpenGitHubRepo: (Context) -> Unit,
  onThemeChanged: (String) -> Unit,
  onAccentChanged: (String) -> Unit,
  onLanguageChanged: (String) -> Unit,
  onDynamicBgChanged: (Boolean) -> Unit,
  onReducedMotionChanged: (Boolean) -> Unit
) {
  val context = LocalContext.current
  val accents = listOf(
    "#EF4444" to AccentRed,
    "#EC4899" to AccentPink,
    "#3B77F5" to AccentBlue,
    "#22C55E" to AccentGreen,
    "#F59E0B" to AccentAmber,
    "#14B8A6" to AccentTeal,
    "#8B5CF6" to AccentPurple
  )

  var ownerInput by remember(githubOwner) { mutableStateOf(githubOwner) }
  var repoInput by remember(githubRepo) { mutableStateOf(githubRepo) }
  var showRepoConfig by remember { mutableStateOf(false) }
  var showReleaseNotesSheet by remember { mutableStateOf(false) }

  val isDownloading = apkDownloadState is ApkDownloadState.Downloading
  val isDownloaded = apkDownloadState is ApkDownloadState.Downloaded

  val animatedProgress by animateFloatAsState(
    targetValue = when (apkDownloadState) {
      is ApkDownloadState.Downloading -> if (apkDownloadState.progress >= 0f) apkDownloadState.progress else 0.35f
      is ApkDownloadState.Downloaded -> 1f
      else -> 0f
    },
    animationSpec = if (reducedMotion) snap() else tween(260),
    label = "apk_dl_progress"
  )

  LazyColumn(
    modifier = Modifier
      .fillMaxSize()
      .padding(horizontal = 18.dp),
    verticalArrangement = Arrangement.spacedBy(18.dp),
    contentPadding = PaddingValues(
      top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 16.dp,
      bottom = if (hasCurrentSong) 195.dp else 125.dp
    )
  ) {
    // Clean Glass Header with Animated Brand Logo
    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          AurixBrandLogo(
            size = 42.dp,
            cornerRadius = 13.dp,
            animated = true,
            reducedMotion = reducedMotion
          )
          Text(
            text = "Settings",
            style = MaterialTheme.typography.displayLarge.copy(fontSize = 27.sp, fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground
          )
        }
        Surface(
          shape = RoundedCornerShape(999.dp),
          color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
        ) {
          Text(
            text = "v${BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
          )
        }
      }
    }

    // Glass Update & Release Card (Uncluttered layout inspired by GlassNotes)
    item {
      GlassCard(
        darkTheme = darkTheme,
        cornerRadius = 26.dp,
        modifier = Modifier
          .fillMaxWidth()
          .animateContentSize(animationSpec = if (reducedMotion) snap() else tween(280))
      ) {
        Column(
          modifier = Modifier.padding(18.dp),
          verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
          // Card Top Header
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
          ) {
            AurixBrandLogo(
              size = 48.dp,
              cornerRadius = 15.dp,
              animated = false,
              reducedMotion = reducedMotion
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = "App Update & Release",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
              Text(
                text = "GitHub: ${ownerInput.ifBlank { "MEYYYNASH" }}/${repoInput.ifBlank { "AURIX" }}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
            }
            IconButton(
              onClick = { showRepoConfig = !showRepoConfig },
              modifier = Modifier.size(36.dp)
            ) {
              Icon(
                imageVector = if (showRepoConfig) Icons.Rounded.ExpandLess else Icons.Rounded.Edit,
                contentDescription = "Edit Repository",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
              )
            }
          }

          // Optional Repo Editor (hidden by default so card stays clean)
          AnimatedVisibility(
            visible = showRepoConfig,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
          ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
              OutlinedTextField(
                value = ownerInput,
                onValueChange = { ownerInput = it; onGithubOwnerChanged(it) },
                label = { Text("GitHub Owner") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                singleLine = true
              )
              OutlinedTextField(
                value = repoInput,
                onValueChange = { repoInput = it; onGithubRepoChanged(it) },
                label = { Text("GitHub Repository") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                singleLine = true
              )
            }
          }

          // Inner Key-Value Status Summary Panel
          Surface(
            shape = RoundedCornerShape(18.dp),
            color = if (darkTheme) Color.Black.copy(alpha = 0.24f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
            modifier = Modifier.fillMaxWidth()
          ) {
            Column(
              modifier = Modifier.padding(14.dp),
              verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
              UpdateSummaryRow(
                label = "Installed Version",
                value = "v${BuildConfig.VERSION_NAME} (code ${BuildConfig.VERSION_CODE})"
              )
              UpdateSummaryRow(
                label = "Latest GitHub Release",
                value = latestRelease?.tagName ?: "Not checked yet"
              )
              UpdateSummaryRow(
                label = "Release Date",
                value = latestRelease?.releaseDate?.ifBlank { "-" } ?: "-"
              )
              UpdateSummaryRow(
                label = "Update Status",
                value = updateStatus,
                valueColor = when {
                  apkDownloadState is ApkDownloadState.Error -> MaterialTheme.colorScheme.error
                  apkDownloadState is ApkDownloadState.Downloaded -> AccentGreen
                  updateStatus.startsWith("Update available") -> MaterialTheme.colorScheme.primary
                  else -> MaterialTheme.colorScheme.primary
                }
              )
            }
          }

          // Animated Download Progress Bar
          AnimatedVisibility(
            visible = isDownloading,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
          ) {
            val dl = apkDownloadState as? ApkDownloadState.Downloading
            val mbText = if (dl != null && dl.totalBytes > 0L) {
              val dMb = dl.downloadedBytes / (1024f * 1024f)
              val tMb = dl.totalBytes / (1024f * 1024f)
              String.format("%.1f / %.1f MB", dMb, tMb)
            } else if (dl != null) {
              String.format("%.1f MB", dl.downloadedBytes / (1024f * 1024f))
            } else ""

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
              ) {
                Text(
                  text = "Downloading APK...",
                  style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                  color = MaterialTheme.colorScheme.primary
                )
                Text(
                  text = mbText,
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
              LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier
                  .fillMaxWidth()
                  .height(7.dp)
                  .clip(RoundedCornerShape(999.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
              )
            }
          }

          // Full-Width Gradient Primary Action Button
          val primaryLabel = when {
            isDownloading -> "Downloading APK..."
            isDownloaded && latestRelease != null -> "Install Update (${latestRelease.tagName})"
            latestRelease != null -> "Download & Install ${latestRelease.tagName}"
            else -> "Check Update"
          }
          val primaryIcon = when {
            isDownloading -> Icons.Rounded.Downloading
            isDownloaded -> Icons.Rounded.InstallMobile
            latestRelease != null -> Icons.Rounded.Download
            else -> Icons.Rounded.NotificationsActive
          }

          Box(
            modifier = Modifier
              .fillMaxWidth()
              .height(50.dp)
              .clip(RoundedCornerShape(18.dp))
              .background(
                Brush.horizontalGradient(
                  colors = listOf(
                    MaterialTheme.colorScheme.primary,
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.78f)
                  )
                )
              )
              .clickable(enabled = !isDownloading) {
                when {
                  isDownloaded -> onInstallUpdate(context)
                  latestRelease != null -> onDownloadUpdate(context)
                  else -> onCheckUpdate(context)
                }
              }
              .testTag("check_update_button"),
            contentAlignment = Alignment.Center
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              Icon(
                imageVector = primaryIcon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(19.dp)
              )
              Text(
                text = primaryLabel,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
            }
          }

          // Clean 2-Column Pill Action Grid (Zero Text Wrapping)
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            GlassActionPill(
              text = "Download APK",
              icon = Icons.Rounded.Download,
              darkTheme = darkTheme,
              enabled = !isDownloading,
              modifier = Modifier.weight(1f).testTag("download_apk_button"),
              onClick = { onDownloadUpdate(context) }
            )
            GlassActionPill(
              text = "Install Update",
              icon = Icons.Rounded.SystemUpdate,
              darkTheme = darkTheme,
              enabled = !isDownloading,
              modifier = Modifier.weight(1f).testTag("install_apk_button"),
              onClick = { onInstallUpdate(context) }
            )
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            GlassActionPill(
              text = "Release Notes",
              icon = Icons.Rounded.Description,
              darkTheme = darkTheme,
              enabled = latestRelease != null,
              modifier = Modifier.weight(1f),
              onClick = { showReleaseNotesSheet = true }
            )
            GlassActionPill(
              text = "Open GitHub",
              icon = Icons.Rounded.OpenInNew,
              darkTheme = darkTheme,
              enabled = true,
              modifier = Modifier.weight(1f),
              onClick = { onOpenGitHubRepo(context) }
            )
          }
        }
      }
    }

    // Theme & Accent Glass Card
    item {
      GlassCard(darkTheme = darkTheme, cornerRadius = 24.dp, modifier = Modifier.fillMaxWidth()) {
        Column(
          modifier = Modifier.padding(18.dp),
          verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
          Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
              text = "Theme",
              style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
              color = MaterialTheme.colorScheme.onSurface
            )
            Surface(
              shape = RoundedCornerShape(999.dp),
              color = if (darkTheme) Color.Black.copy(alpha = 0.28f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
              modifier = Modifier.fillMaxWidth()
            ) {
              Row(modifier = Modifier.padding(4.dp)) {
                listOf("system" to "System", "light" to "Light", "dark" to "Dark").forEach { (key, label) ->
                  val selected = currentTheme == key
                  Box(
                    modifier = Modifier
                      .weight(1f)
                      .clip(RoundedCornerShape(999.dp))
                      .background(if (selected) MaterialTheme.colorScheme.surface else Color.Transparent)
                      .clickable { onThemeChanged(key) }
                      .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                  ) {
                    Text(
                      text = label,
                      style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                      color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                  }
                }
              }
            }
          }

          Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
              text = "Accent color",
              style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
              color = MaterialTheme.colorScheme.onSurface
            )
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              accents.forEach { (hex, color) ->
                val selected = currentAccentHex.equals(hex, ignoreCase = true)
                Box(
                  modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .then(
                      if (selected) Modifier.border(2.5.dp, MaterialTheme.colorScheme.onSurface, CircleShape).padding(3.dp)
                      else Modifier
                    )
                    .clip(CircleShape)
                    .background(color)
                    .clickable { onAccentChanged(hex) }
                    .testTag("accent_dot_$hex")
                )
              }
            }
          }
        }
      }
    }

    // Now Playing Preferences Glass Card
    item {
      GlassCard(darkTheme = darkTheme, cornerRadius = 24.dp, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(vertical = 6.dp)) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .clickable { onDynamicBgChanged(!dynamicBg) }
              .padding(horizontal = 18.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = "Dynamic artwork background",
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface
              )
              Text(
                text = "Ambient blurred album glow across screens",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
            Switch(
              checked = dynamicBg,
              onCheckedChange = onDynamicBgChanged,
              modifier = Modifier.testTag("dynamic_bg_toggle")
            )
          }

          HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

          Row(
            modifier = Modifier
              .fillMaxWidth()
              .clickable { onReducedMotionChanged(!reducedMotion) }
              .padding(horizontal = 18.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = "Reduced motion",
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface
              )
              Text(
                text = "Minimize glass transitions & spring effects",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
            Switch(
              checked = reducedMotion,
              onCheckedChange = onReducedMotionChanged,
              modifier = Modifier.testTag("reduced_motion_toggle")
            )
          }
        }
      }
    }

    // Language Glass Card
    item {
      GlassCard(darkTheme = darkTheme, cornerRadius = 24.dp, modifier = Modifier.fillMaxWidth()) {
        Column(
          modifier = Modifier.padding(18.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          Text(
            text = "Language",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface
          )
          Surface(
            shape = RoundedCornerShape(999.dp),
            color = if (darkTheme) Color.Black.copy(alpha = 0.28f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.fillMaxWidth()
          ) {
            Row(modifier = Modifier.padding(4.dp)) {
              listOf("en" to "English", "km" to "ភាសាខ្មែរ").forEach { (code, label) ->
                val selected = currentLanguage == code
                Box(
                  modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (selected) MaterialTheme.colorScheme.surface else Color.Transparent)
                    .clickable { onLanguageChanged(code) }
                    .padding(vertical = 10.dp),
                  contentAlignment = Alignment.Center
                ) {
                  Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                    color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                  )
                }
              }
            }
          }
        }
      }
    }

    // About Glass Card
    item {
      GlassCard(darkTheme = darkTheme, cornerRadius = 24.dp, modifier = Modifier.fillMaxWidth()) {
        Column(
          modifier = Modifier.padding(18.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          Text(
            text = "About AURIX",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface
          )
          Text(
            text = "AURIX ${BuildConfig.VERSION_NAME}. Streaming from Audius, synced lyrics from LRCLIB, OTA updates via GitHub.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }
    }
  }

  if (showReleaseNotesSheet && latestRelease != null) {
    ModalBottomSheet(
      onDismissRequest = { showReleaseNotesSheet = false },
      shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 24.dp, vertical = 16.dp)
          .windowInsetsPadding(WindowInsets.navigationBars),
        verticalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        Text(
          text = "Release Notes (${latestRelease.tagName})",
          style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
          color = MaterialTheme.colorScheme.onSurface
        )
        Text(
          text = latestRelease.body.ifBlank { "No changelog details provided." },
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(6.dp))
        Button(
          onClick = { showReleaseNotesSheet = false; onDownloadUpdate(context) },
          modifier = Modifier.fillMaxWidth().height(48.dp),
          shape = RoundedCornerShape(16.dp)
        ) {
          Text("Download & Install ${latestRelease.tagName}", fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(8.dp))
      }
    }
  }
}

@Composable
private fun UpdateSummaryRow(
  label: String,
  value: String,
  valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(modifier = Modifier.width(12.dp))
    Text(
      text = value,
      style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
      color = valueColor,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis
    )
  }
}

@Composable
private fun GlassActionPill(
  text: String,
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  darkTheme: Boolean,
  enabled: Boolean,
  modifier: Modifier = Modifier,
  onClick: () -> Unit
) {
  val bg = if (darkTheme) Color.White.copy(alpha = if (enabled) 0.08f else 0.04f) else Color.White.copy(alpha = if (enabled) 0.88f else 0.45f)
  val borderColor = if (darkTheme) Color.White.copy(alpha = 0.14f) else Color.Black.copy(alpha = 0.08f)
  val contentColor = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)

  Surface(
    shape = RoundedCornerShape(16.dp),
    color = bg,
    modifier = modifier
      .height(44.dp)
      .border(1.dp, borderColor, RoundedCornerShape(16.dp))
      .clip(RoundedCornerShape(16.dp))
      .clickable(enabled = enabled) { onClick() }
  ) {
    Row(
      modifier = Modifier
        .fillMaxSize()
        .padding(horizontal = 10.dp),
      horizontalArrangement = Arrangement.Center,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = contentColor,
        modifier = Modifier.size(16.dp)
      )
      Spacer(modifier = Modifier.width(6.dp))
      Text(
        text = text,
        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
        color = contentColor,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis
      )
    }
  }
}
