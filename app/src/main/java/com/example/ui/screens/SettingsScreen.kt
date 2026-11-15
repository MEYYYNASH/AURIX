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
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.ApkDownloadState
import com.example.data.GitHubRelease
import com.example.data.InstalledAppInfo
import com.example.data.UpdateCheckStatus
import com.example.playback.CrossfadeSettings
import com.example.playback.EqPreset
import com.example.playback.EqualizerUiState
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
  installedAppInfo: InstalledAppInfo,
  updateCheckStatus: UpdateCheckStatus,
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
  onReducedMotionChanged: (Boolean) -> Unit,
  equalizerState: EqualizerUiState = EqualizerUiState(),
  onToggleEqualizer: (Boolean) -> Unit = {},
  onSelectEqPreset: (EqPreset) -> Unit = {},
  onEqBandLevelChanged: (Int, Int) -> Unit = { _, _ -> },
  onEqBassBoostChanged: (Int) -> Unit = {},
  onResetEq: () -> Unit = {},
  crossfadeSettings: CrossfadeSettings = CrossfadeSettings(),
  onCrossfadeSecondsChanged: (Int) -> Unit = {},
  onCrossfadeManualSkipChanged: (Boolean) -> Unit = {},
  hiddenSongsCount: Int = 0,
  blockedArtistsCount: Int = 0,
  onClearHiddenAndBlocked: () -> Unit = {}
) {
  val context = LocalContext.current
  val accents = listOf(
    "#EF4444" to AccentRed,
    "#EC4899" to AccentPink,
    "#007AFF" to AccentBlue,
    "#22C55E" to AccentGreen,
    "#F59E0B" to AccentAmber,
    "#14B8A6" to AccentTeal,
    "#8B5CF6" to AccentPurple
  )

  var showReleaseNotesSheet by remember { mutableStateOf(false) }
  var showEqualizerSheet by remember { mutableStateOf(false) }
  var showCrossfadeSheet by remember { mutableStateOf(false) }

  val isDownloading = apkDownloadState is ApkDownloadState.Downloading
  val isValidatedReady = apkDownloadState is ApkDownloadState.ValidatedReady
  val isUpdateAvailable = updateCheckStatus is UpdateCheckStatus.UpdateAvailable
  val isMandatory = (updateCheckStatus as? UpdateCheckStatus.UpdateAvailable)?.isMandatory == true
  val isUpToDate = updateCheckStatus is UpdateCheckStatus.UpToDate
  val isDowngradeBlocked = updateCheckStatus is UpdateCheckStatus.DowngradeBlocked

  val animatedProgress by animateFloatAsState(
    targetValue = when (apkDownloadState) {
      is ApkDownloadState.Downloading -> if (apkDownloadState.progress >= 0f) apkDownloadState.progress else 0.35f
      is ApkDownloadState.ValidatedReady -> 1f
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
            text = stringResource(id = R.string.settings_title),
            style = MaterialTheme.typography.displayLarge.copy(
              fontSize = 27.sp,
              lineHeight = 38.sp,
              fontWeight = FontWeight.Bold
            ),
            color = MaterialTheme.colorScheme.onBackground
          )
        }
        Surface(
          shape = RoundedCornerShape(999.dp),
          color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
        ) {
          Text(
            text = "v${installedAppInfo.versionName}",
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
                text = stringResource(id = R.string.settings_update_title),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, lineHeight = 24.sp),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
              Text(
                text = stringResource(id = R.string.settings_update_subtitle),
                style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
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
                label = stringResource(id = R.string.settings_installed_version),
                value = "v${installedAppInfo.versionName} (code ${installedAppInfo.versionCode})"
              )
              UpdateSummaryRow(
                label = stringResource(id = R.string.settings_latest_release),
                value = latestRelease?.let { "${it.tagName} (code ${it.versionCode})" }
                  ?: stringResource(id = R.string.settings_not_checked)
              )
              UpdateSummaryRow(
                label = stringResource(id = R.string.settings_release_date),
                value = latestRelease?.releaseDate?.ifBlank { "-" } ?: "-"
              )
              UpdateSummaryRow(
                label = stringResource(id = R.string.settings_update_status),
                value = updateStatus,
                valueColor = when {
                  apkDownloadState is ApkDownloadState.Error -> MaterialTheme.colorScheme.error
                  isDowngradeBlocked -> AccentAmber
                  isUpToDate || isValidatedReady -> AccentGreen
                  isMandatory -> MaterialTheme.colorScheme.error
                  isUpdateAvailable -> MaterialTheme.colorScheme.primary
                  updateCheckStatus is UpdateCheckStatus.Error -> MaterialTheme.colorScheme.error
                  else -> MaterialTheme.colorScheme.primary
                }
              )
            }
          }

          if (isMandatory && isUpdateAvailable) {
            Surface(
              shape = RoundedCornerShape(14.dp),
              color = MaterialTheme.colorScheme.error.copy(alpha = 0.14f),
              modifier = Modifier.fillMaxWidth()
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                Icon(
                  imageVector = Icons.Rounded.WarningAmber,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.error,
                  modifier = Modifier.size(18.dp)
                )
                Text(
                  text = stringResource(id = R.string.settings_critical_release),
                  style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold, lineHeight = 18.sp),
                  color = MaterialTheme.colorScheme.error
                )
              }
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
                  text = stringResource(id = R.string.settings_downloading_verifying),
                  style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold, lineHeight = 18.sp),
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

          // Strict Smart Update Primary Action:
          // "Update Now" appears ONLY when latest versionCode > installed versionCode!
          if (isUpdateAvailable && latestRelease != null) {
            val updateLabel = when {
              isDownloading -> stringResource(id = R.string.settings_downloading_update)
              isValidatedReady -> stringResource(id = R.string.settings_install_verified_update, latestRelease.tagName)
              else -> stringResource(id = R.string.settings_update_now, latestRelease.tagName)
            }
            val updateIcon = when {
              isDownloading -> Icons.Rounded.Downloading
              isValidatedReady -> Icons.Rounded.InstallMobile
              else -> Icons.Rounded.SystemUpdate
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
                  if (isValidatedReady) onInstallUpdate(context) else onDownloadUpdate(context)
                }
                .testTag("update_now_button"),
              contentAlignment = Alignment.Center
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                Icon(
                  imageVector = updateIcon,
                  contentDescription = null,
                  tint = Color.White,
                  modifier = Modifier.size(19.dp)
                )
                Text(
                  text = updateLabel,
                  style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, lineHeight = 20.sp),
                  color = Color.White,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis
                )
              }
            }
          }

          // Secondary Action Pills (Check Updates always available; Release Notes & GitHub clean)
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            GlassActionPill(
              text = if (updateCheckStatus is UpdateCheckStatus.Checking) {
                stringResource(id = R.string.settings_checking)
              } else {
                stringResource(id = R.string.settings_check_updates)
              },
              icon = Icons.Rounded.Refresh,
              darkTheme = darkTheme,
              enabled = !isDownloading && updateCheckStatus !is UpdateCheckStatus.Checking,
              modifier = Modifier.weight(1f).testTag("check_update_button"),
              onClick = { onCheckUpdate(context) }
            )
            GlassActionPill(
              text = stringResource(id = R.string.settings_release_notes),
              icon = Icons.Rounded.Description,
              darkTheme = darkTheme,
              enabled = latestRelease != null,
              modifier = Modifier.weight(1f),
              onClick = { showReleaseNotesSheet = true }
            )
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            GlassActionPill(
              text = stringResource(id = R.string.settings_open_release_page),
              icon = Icons.AutoMirrored.Rounded.OpenInNew,
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
              text = stringResource(id = R.string.settings_theme),
              style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, lineHeight = 24.sp),
              color = MaterialTheme.colorScheme.onSurface
            )
            Surface(
              shape = RoundedCornerShape(999.dp),
              color = if (darkTheme) Color(0xFF0E1320).copy(alpha = 0.72f) else Color(0xFFE1E8F6),
              modifier = Modifier.fillMaxWidth()
            ) {
              val themeOptions = listOf(
                "system" to stringResource(id = R.string.settings_theme_system),
                "light" to stringResource(id = R.string.settings_theme_light),
                "dark" to stringResource(id = R.string.settings_theme_dark),
                "amoled" to stringResource(id = R.string.settings_theme_amoled)
              )
              Row(modifier = Modifier.padding(4.dp)) {
                themeOptions.forEach { (key, label) ->
                  val selected = currentTheme == key
                  Box(
                    modifier = Modifier
                      .weight(1f)
                      .clip(RoundedCornerShape(999.dp))
                      .background(if (selected) (if (darkTheme) Color(0xFF28334A) else Color.White) else Color.Transparent)
                      .clickable { onThemeChanged(key) }
                      .padding(vertical = 10.dp, horizontal = 4.dp),
                    contentAlignment = Alignment.Center
                  ) {
                    Text(
                      text = label,
                      style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold, lineHeight = 18.sp),
                      color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis
                    )
                  }
                }
              }
            }
          }

          Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
              text = stringResource(id = R.string.settings_accent),
              style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, lineHeight = 24.sp),
              color = MaterialTheme.colorScheme.onSurface
            )
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              accents.forEach { (hex, color) ->
                val selected = currentAccentHex.equals(hex, ignoreCase = true) ||
                  (hex == "#007AFF" && currentAccentHex.equals("#3B77F5", ignoreCase = true))
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
                text = stringResource(id = R.string.settings_dynamic_bg),
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium, lineHeight = 22.sp),
                color = MaterialTheme.colorScheme.onSurface
              )
              Text(
                text = stringResource(id = R.string.settings_dynamic_bg_desc),
                style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
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
                text = stringResource(id = R.string.settings_reduced_motion),
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium, lineHeight = 22.sp),
                color = MaterialTheme.colorScheme.onSurface
              )
              Text(
                text = stringResource(id = R.string.settings_reduced_motion_desc),
                style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
            Switch(
              checked = reducedMotion,
              onCheckedChange = onReducedMotionChanged,
              modifier = Modifier.testTag("reduced_motion_toggle")
            )
          }

          HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

          Row(
            modifier = Modifier
              .fillMaxWidth()
              .clickable { showEqualizerSheet = true }
              .padding(horizontal = 18.dp, vertical = 12.dp)
              .testTag("settings_equalizer_row"),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = stringResource(id = R.string.eq_title),
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium, lineHeight = 22.sp),
                color = MaterialTheme.colorScheme.onSurface
              )
              val presetName = presetDisplayName(EqPreset.fromId(equalizerState.presetId))
              Text(
                text = if (equalizerState.enabled) "${stringResource(id = R.string.eq_on)} · $presetName" else stringResource(id = R.string.eq_off),
                style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
                color = if (equalizerState.enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
            Icon(
              imageVector = Icons.Rounded.Equalizer,
              contentDescription = null,
              tint = if (equalizerState.enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
          }

          HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

          val crossfadeActive = crossfadeSettings.durationSeconds > 0
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .clickable { showCrossfadeSheet = true }
              .padding(horizontal = 18.dp, vertical = 12.dp)
              .testTag("settings_crossfade_row"),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = stringResource(id = R.string.crossfade_title),
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium, lineHeight = 22.sp),
                color = MaterialTheme.colorScheme.onSurface
              )
              Text(
                text = if (crossfadeActive) {
                  stringResource(id = R.string.crossfade_seconds_active, crossfadeSettings.durationSeconds)
                } else {
                  stringResource(id = R.string.crossfade_off)
                },
                style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
                color = if (crossfadeActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
            Icon(
              imageVector = Icons.Rounded.GraphicEq,
              contentDescription = null,
              tint = if (crossfadeActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
          }

          if (hiddenSongsCount > 0 || blockedArtistsCount > 0) {
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .clickable { onClearHiddenAndBlocked() }
                .padding(horizontal = 18.dp, vertical = 12.dp)
                .testTag("settings_unhide_row"),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = stringResource(id = R.string.settings_unhide_title),
                  style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium, lineHeight = 22.sp),
                  color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                  text = stringResource(id = R.string.settings_unhide_subtitle, hiddenSongsCount, blockedArtistsCount),
                  style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
              Icon(
                imageVector = Icons.Rounded.Restore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
              )
            }
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
            text = stringResource(id = R.string.settings_language),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, lineHeight = 24.sp),
            color = MaterialTheme.colorScheme.onSurface
          )
          Surface(
            shape = RoundedCornerShape(999.dp),
            color = if (darkTheme) Color(0xFF0E1320).copy(alpha = 0.72f) else Color(0xFFE1E8F6),
            modifier = Modifier.fillMaxWidth()
          ) {
            Row(modifier = Modifier.padding(4.dp)) {
              listOf(
                "en" to stringResource(id = R.string.lang_english),
                "km" to stringResource(id = R.string.lang_khmer)
              ).forEach { (code, label) ->
                val selected = currentLanguage.equals(code, ignoreCase = true)
                Box(
                  modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (selected) (if (darkTheme) Color(0xFF28334A) else Color.White) else Color.Transparent)
                    .clickable { onLanguageChanged(code) }
                    .testTag("settings_language_$code")
                    .padding(vertical = 10.dp),
                  contentAlignment = Alignment.Center
                ) {
                  Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold, lineHeight = 18.sp),
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
            text = stringResource(id = R.string.settings_about_title),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, lineHeight = 24.sp),
            color = MaterialTheme.colorScheme.onSurface
          )
          Text(
            text = stringResource(
              id = R.string.settings_about_body,
              installedAppInfo.versionName,
              installedAppInfo.versionCode
            ),
            style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
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
          text = stringResource(id = R.string.settings_release_notes_title, latestRelease.tagName, latestRelease.versionCode),
          style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, lineHeight = 30.sp),
          color = MaterialTheme.colorScheme.onSurface
        )
        Text(
          text = latestRelease.body.ifBlank { stringResource(id = R.string.settings_no_changelog) },
          style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (isUpdateAvailable) {
          Spacer(modifier = Modifier.height(6.dp))
          Button(
            onClick = { showReleaseNotesSheet = false; onDownloadUpdate(context) },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(16.dp)
          ) {
            Text(stringResource(id = R.string.settings_update_now, latestRelease.tagName), fontWeight = FontWeight.Bold)
          }
        }
        Spacer(modifier = Modifier.height(8.dp))
      }
    }
  }

  if (showEqualizerSheet) {
    EqualizerSheet(
      state = equalizerState,
      onToggleEnabled = onToggleEqualizer,
      onSelectPreset = onSelectEqPreset,
      onBandLevelChanged = onEqBandLevelChanged,
      onBassBoostChanged = onEqBassBoostChanged,
      onReset = onResetEq,
      onDismiss = { showEqualizerSheet = false }
    )
  }

  if (showCrossfadeSheet) {
    CrossfadeSheet(
      settings = crossfadeSettings,
      reducedMotion = reducedMotion,
      onDurationChanged = onCrossfadeSecondsChanged,
      onManualSkipChanged = onCrossfadeManualSkipChanged,
      onDismiss = { showCrossfadeSheet = false }
    )
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
