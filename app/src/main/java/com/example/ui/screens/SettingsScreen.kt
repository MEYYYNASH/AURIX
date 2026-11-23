package com.example.ui.screens

import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.BuildConfig
import com.example.R
import com.example.data.InstalledAppInfo
import com.example.data.RemoteVersionInfo
import com.example.data.UpdateMainAction
import com.example.data.UpdateManager
import com.example.data.UpdateStage
import com.example.data.UpdateUiState
import com.example.playback.CrossfadeSettings
import com.example.playback.EqPreset
import com.example.playback.EqualizerUiState
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.roundToInt

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
  updateUiState: UpdateUiState,
  onGithubOwnerChanged: (String) -> Unit,
  onGithubRepoChanged: (String) -> Unit,
  onCheckUpdate: (Context) -> Unit,
  onDownloadOrInstallUpdate: (Context) -> Unit,
  onPauseDownload: () -> Unit,
  onCancelDownload: () -> Unit,
  onRetryUpdate: () -> Unit,
  onOpenInstallSettings: () -> Unit,
  onRestartApp: (Context) -> Unit,
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
  onEqVirtualizerChanged: (Int) -> Unit = {},
  onResetEq: () -> Unit = {},
  crossfadeSettings: CrossfadeSettings = CrossfadeSettings(),
  onCrossfadeSecondsChanged: (Int) -> Unit = {},
  onCrossfadeManualSkipChanged: (Boolean) -> Unit = {},
  hiddenSongsCount: Int = 0,
  blockedArtistsCount: Int = 0,
  onClearHiddenAndBlocked: () -> Unit = {},
  logoState: UpdateLogoState = UpdateLogoState.Idle,
  onStartUpdateLogoIntro: () -> Unit = {},
  navStyle: String = AurixNavStyle.CLASSIC.id,
  onOpenNavPreview: () -> Unit = {},
  nowPlayingVisualizerEnabled: Boolean = true,
  onNowPlayingVisualizerChanged: (Boolean) -> Unit = {},
  autoCleanYoutubeTitles: Boolean = true,
  onAutoCleanYoutubeTitlesChanged: (Boolean) -> Unit = {}
) {
  val context = LocalContext.current
  LaunchedEffect(Unit) {
    onStartUpdateLogoIntro()
  }
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

  val animatedProgress by animateFloatAsState(
    targetValue = when (updateUiState.stage) {
      UpdateStage.Downloading, UpdateStage.Available -> if (updateUiState.progress >= 0f) updateUiState.progress else 0.35f
      UpdateStage.Verifying, UpdateStage.Installing, UpdateStage.Done -> 1f
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
    // Static Header Brand Logo + Title
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
            animated = false,
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
            text = "v${updateUiState.installed.versionName}",
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
          )
        }
      }
    }

    // Glass Update & Release Card
    item {
      AppUpdateReleaseCard(
        logoState = logoState,
        updateUiState = updateUiState,
        animatedProgress = animatedProgress,
        darkTheme = darkTheme,
        reducedMotion = reducedMotion,
        onCheckUpdate = { onCheckUpdate(context) },
        onOpenReleaseNotes = { showReleaseNotesSheet = true },
        onMainAction = {
          when (updateUiState.mainAction) {
            UpdateMainAction.DownloadAndInstall,
            UpdateMainAction.ResumeDownload,
            UpdateMainAction.InstallVerifiedApk -> onDownloadOrInstallUpdate(context)
            UpdateMainAction.OpenInstallSettings -> onOpenInstallSettings()
            UpdateMainAction.RestartApp -> onRestartApp(context)
            UpdateMainAction.Retry -> onRetryUpdate()
            UpdateMainAction.None -> Unit
          }
        },
        onPauseDownload = onPauseDownload,
        onResumeDownload = { onDownloadOrInstallUpdate(context) },
        onCancelDownload = onCancelDownload,
        onOpenGitHubRepo = { onOpenGitHubRepo(context) }
      )
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
              .clickable { onNowPlayingVisualizerChanged(!nowPlayingVisualizerEnabled) }
              .padding(horizontal = 18.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = stringResource(id = R.string.settings_visualizer_title),
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium, lineHeight = 22.sp),
                color = MaterialTheme.colorScheme.onSurface
              )
              Text(
                text = stringResource(id = R.string.settings_visualizer_desc),
                style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
            Switch(
              checked = nowPlayingVisualizerEnabled,
              onCheckedChange = onNowPlayingVisualizerChanged,
              modifier = Modifier.testTag("np_visualizer_settings_toggle")
            )
          }

          HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

          Row(
            modifier = Modifier
              .fillMaxWidth()
              .clickable { onAutoCleanYoutubeTitlesChanged(!autoCleanYoutubeTitles) }
              .padding(horizontal = 18.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = stringResource(id = R.string.settings_clean_yt_titles_title),
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium, lineHeight = 22.sp),
                color = MaterialTheme.colorScheme.onSurface
              )
              Text(
                text = stringResource(id = R.string.settings_clean_yt_titles_desc),
                style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
            Switch(
              checked = autoCleanYoutubeTitles,
              onCheckedChange = onAutoCleanYoutubeTitlesChanged,
              modifier = Modifier.testTag("auto_clean_yt_titles_toggle")
            )
          }

          HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

          Row(
            modifier = Modifier
              .fillMaxWidth()
              .clickable { onOpenNavPreview() }
              .padding(horizontal = 18.dp, vertical = 12.dp)
              .testTag("settings_nav_style_row"),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = stringResource(id = R.string.nav_style_title),
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium, lineHeight = 22.sp),
                color = MaterialTheme.colorScheme.onSurface
              )
              val activeStyleLabel = if (AurixNavStyle.fromId(navStyle) == AurixNavStyle.BLUR) {
                stringResource(id = R.string.nav_style_blur)
              } else {
                stringResource(id = R.string.nav_style_classic)
              }
              Text(
                text = "$activeStyleLabel · ${stringResource(id = R.string.nav_style_subtitle)}",
                style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
                color = MaterialTheme.colorScheme.primary
              )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Surface(
              shape = CircleShape,
              color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
              modifier = Modifier
                .heightIn(min = 40.dp)
                .clip(CircleShape)
                .clickable { onOpenNavPreview() }
                .testTag("settings_nav_preview_button")
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
              ) {
                Icon(
                  imageVector = Icons.Rounded.Visibility,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.primary,
                  modifier = Modifier.size(16.dp)
                )
                Text(
                  text = stringResource(id = R.string.nav_preview_button),
                  style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                  color = MaterialTheme.colorScheme.primary
                )
              }
            }
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
              updateUiState.installed.versionName,
              updateUiState.installed.versionCode,
              BuildConfig.RELEASE_DATE
            ),
            style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }
    }
  }

  if (showReleaseNotesSheet) {
    val canDownloadUpdate = updateUiState.latestRelease != null &&
      updateUiState.latestRelease.versionCode > updateUiState.installed.versionCode &&
      updateUiState.stage != UpdateStage.Downloading &&
      updateUiState.stage != UpdateStage.Done
    ReleaseNotesBottomSheet(
      latestRelease = updateUiState.latestRelease,
      installedVersionName = updateUiState.installed.versionName,
      installedVersionCode = updateUiState.installed.versionCode,
      canDownloadUpdate = canDownloadUpdate,
      onDownloadAndInstall = { onDownloadOrInstallUpdate(context) },
      onDismiss = { showReleaseNotesSheet = false },
      reducedMotion = reducedMotion
    )
  }

  if (showEqualizerSheet) {
    EqualizerSheet(
      state = equalizerState,
      onToggleEnabled = onToggleEqualizer,
      onSelectPreset = onSelectEqPreset,
      onBandLevelChanged = onEqBandLevelChanged,
      onBassBoostChanged = onEqBassBoostChanged,
      onVirtualizerChanged = onEqVirtualizerChanged,
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
fun AppUpdateReleaseCard(
  logoState: UpdateLogoState,
  updateUiState: UpdateUiState,
  animatedProgress: Float = 0f,
  darkTheme: Boolean = true,
  reducedMotion: Boolean = false,
  onCheckUpdate: () -> Unit,
  onOpenReleaseNotes: () -> Unit,
  onMainAction: () -> Unit = {},
  onPauseDownload: () -> Unit = {},
  onResumeDownload: () -> Unit = {},
  onCancelDownload: () -> Unit = {},
  onOpenGitHubRepo: () -> Unit,
  modifier: Modifier = Modifier
) {
  val isBusy = updateUiState.isBusy
  val isDownloading = updateUiState.stage == UpdateStage.Downloading
  val isPausedPartial = (updateUiState.stage == UpdateStage.Available && (updateUiState.isPaused || updateUiState.canResumePartial))
  val isVerifyingOrInstalling = updateUiState.stage == UpdateStage.Verifying || updateUiState.stage == UpdateStage.Installing
  val isChecking = logoState == UpdateLogoState.Checking || updateUiState.stage == UpdateStage.Checking
  val isMandatory = updateUiState.latestRelease?.isMandatory == true &&
    updateUiState.stage == UpdateStage.Available

  // Continuous sweeping shine across the card title (faster while busy)
  var shinePhase by remember { mutableFloatStateOf(0f) }
  LaunchedEffect(isBusy, reducedMotion) {
    if (reducedMotion) return@LaunchedEffect
    var lastNanos = withFrameNanos { it }
    while (true) {
      withFrameNanos { now ->
        val dt = ((now - lastNanos) / 1_000_000_000f).coerceIn(0f, 0.08f)
        lastNanos = now
        val cyclesPerSec = if (isBusy) 0.92f else 0.34f
        shinePhase = (shinePhase + dt * cyclesPerSec) % 1f
      }
    }
  }

  val baseTitleColor = Color(0xFFF4F2FB)
  val mutedGlassText = Color(0xFF8F8AA6)
  val violetAccent = Color(0xFF8B5CF6)
  val lightVioletAccent = Color(0xFFC084FC)
  val shineAccentColor = lerp(violetAccent, Color.White, if (darkTheme) 0.55f else 0.45f)
  val titleBrush = remember(shinePhase, baseTitleColor, shineAccentColor, reducedMotion) {
    if (reducedMotion) {
      null
    } else {
      val center = -0.35f + shinePhase * 1.70f
      val start = (center - 0.24f).coerceIn(0f, 1f)
      val mid = center.coerceIn(0f, 1f)
      val end = (center + 0.24f).coerceIn(0f, 1f)
      if (end <= start) {
        Brush.linearGradient(listOf(baseTitleColor, baseTitleColor))
      } else {
        Brush.linearGradient(
          colorStops = arrayOf(
            0f to baseTitleColor,
            start to baseTitleColor,
            mid to shineAccentColor,
            end to baseTitleColor,
            1f to baseTitleColor
          ),
          start = Offset(0f, 0f),
          end = Offset(540f, 90f)
        )
      }
    }
  }

  // Pulsing status pill configuration
  val (pillLabelRes, pillColor) = when (updateUiState.stage) {
    UpdateStage.Idle, UpdateStage.UpToDate -> R.string.settings_status_pill_up_to_date to AccentGreen
    UpdateStage.Checking -> R.string.settings_status_pill_checking to lightVioletAccent
    UpdateStage.Available -> R.string.settings_status_pill_available to AccentAmber
    UpdateStage.Downloading -> if (updateUiState.isPaused) {
      R.string.settings_status_pill_available to AccentAmber
    } else {
      R.string.settings_status_pill_downloading to Color(0xFF3B82F6)
    }
    UpdateStage.Verifying, UpdateStage.Installing -> R.string.settings_status_pill_installing to Color(0xFF10B981)
    UpdateStage.Done -> R.string.settings_status_pill_updated to AccentGreen
    UpdateStage.Error -> R.string.settings_status_pill_failed to MaterialTheme.colorScheme.error
  }

  val pulseTransition = rememberInfiniteTransition(label = "status_pill_dot")
  val dotPulse by if (reducedMotion) {
    remember { mutableFloatStateOf(0.85f) }
  } else {
    pulseTransition.animateFloat(
      initialValue = 0.45f,
      targetValue = 1f,
      animationSpec = infiniteRepeatable(
        animation = tween(durationMillis = if (isBusy) 650 else 1250, easing = FastOutSlowInEasing),
        repeatMode = RepeatMode.Reverse
      ),
      label = "pill_dot_alpha"
    )
  }

  val cardShape = RoundedCornerShape(28.dp)
  val cardBorderBrush = Brush.linearGradient(
    colors = listOf(
      Color.White.copy(alpha = 0.16f),
      violetAccent.copy(alpha = 0.36f),
      lightVioletAccent.copy(alpha = 0.18f),
      Color.White.copy(alpha = 0.08f)
    )
  )
  val rawReleaseDate = updateUiState.latestRelease?.releaseDate?.ifBlank { updateUiState.installedReleaseDate }
    ?: updateUiState.installedReleaseDate
  val formattedReleaseDate = remember(rawReleaseDate) {
    formatUpdateReleaseDate(rawReleaseDate)
  }
  val stageStatusColor = when (updateUiState.stage) {
    UpdateStage.Error -> Color(0xFFFB7185)
    UpdateStage.UpToDate, UpdateStage.Done -> AccentGreen
    UpdateStage.Available -> if (isMandatory) Color(0xFFFB7185) else AccentAmber
    UpdateStage.Installing, UpdateStage.Verifying -> Color(0xFF10B981)
    else -> lightVioletAccent
  }

  Box(
    modifier = modifier
      .fillMaxWidth()
      .shadow(
        elevation = 18.dp,
        shape = cardShape,
        ambientColor = violetAccent.copy(alpha = 0.24f),
        spotColor = Color(0xFFD946EF).copy(alpha = 0.22f)
      )
      .clip(cardShape)
      .background(Color(0xFF0A0810))
      .drawBehind {
        drawCircle(
          brush = Brush.radialGradient(
            colors = listOf(violetAccent.copy(alpha = 0.22f), Color.Transparent),
            center = Offset(size.width * 0.14f, size.height * 0.12f),
            radius = size.maxDimension * 0.65f
          ),
          radius = size.maxDimension * 0.65f,
          center = Offset(size.width * 0.14f, size.height * 0.12f)
        )
        drawCircle(
          brush = Brush.radialGradient(
            colors = listOf(Color(0xFFD946EF).copy(alpha = 0.14f), Color.Transparent),
            center = Offset(size.width * 0.90f, size.height * 0.85f),
            radius = size.maxDimension * 0.58f
          ),
          radius = size.maxDimension * 0.58f,
          center = Offset(size.width * 0.90f, size.height * 0.85f)
        )
      }
      .border(width = 1.dp, brush = cardBorderBrush, shape = cardShape)
      .animateContentSize(animationSpec = if (reducedMotion) snap() else tween(280))
  ) {
    Column(
      modifier = Modifier.padding(18.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
      // Card Top Header: 88.dp animated UpdateLogo on left, sweeping title, sliding subtitle & status pill on right
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
      ) {
        UpdateLogo(
          state = logoState,
          size = 88.dp,
          downloadProgress = updateUiState.progress,
          reducedMotion = reducedMotion,
          modifier = Modifier.testTag("settings_update_logo")
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(
          modifier = Modifier.weight(1f),
          verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          Text(
            text = stringResource(id = R.string.settings_update_title),
            style = MaterialTheme.typography.titleMedium.copy(
              fontWeight = FontWeight.Bold,
              lineHeight = 24.sp,
              brush = titleBrush
            ),
            color = if (titleBrush == null) baseTitleColor else Color.Unspecified,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )

          AnimatedContent(
            targetState = updateUiState.subtitleMessage,
            transitionSpec = {
              if (reducedMotion) {
                fadeIn(snap()) togetherWith fadeOut(snap())
              } else {
                (fadeIn(tween(240)) + slideInVertically(tween(260)) { it / 3 }) togetherWith
                  (fadeOut(tween(180)) + slideOutVertically(tween(200)) { -it / 3 })
              }
            },
            label = "update_subtitle_transition"
          ) { subtitle ->
            Text(
              text = subtitle,
              style = MaterialTheme.typography.bodySmall.copy(lineHeight = 17.sp),
              color = lightVioletAccent,
              maxLines = 2,
              overflow = TextOverflow.Ellipsis
            )
          }

          Surface(
            shape = RoundedCornerShape(999.dp),
            color = pillColor.copy(alpha = 0.14f),
            border = androidx.compose.foundation.BorderStroke(1.dp, pillColor.copy(alpha = 0.28f)),
            modifier = Modifier.testTag("settings_update_status_pill")
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
              Box(
                modifier = Modifier
                  .size(7.dp)
                  .clip(CircleShape)
                  .background(pillColor.copy(alpha = dotPulse))
              )
              Text(
                text = stringResource(id = pillLabelRes),
                style = MaterialTheme.typography.labelSmall.copy(
                  fontWeight = FontWeight.Bold,
                  fontSize = 11.sp
                ),
                color = pillColor,
                maxLines = 1
              )
            }
          }
        }
      }

      // Dark Inner Panel: large gradient installed version + code chip, two tiles, shield status row
      Surface(
        shape = RoundedCornerShape(22.dp),
        color = Color(0xFF120E1D).copy(alpha = 0.84f),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(
          modifier = Modifier.padding(14.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
              text = stringResource(id = R.string.settings_installed_version),
              style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
              color = mutedGlassText
            )
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              Text(
                text = "v${updateUiState.installed.versionName}",
                style = MaterialTheme.typography.headlineMedium.copy(
                  fontWeight = FontWeight.ExtraBold,
                  fontSize = 28.sp,
                  lineHeight = 34.sp,
                  brush = Brush.linearGradient(
                    colors = listOf(baseTitleColor, lightVioletAccent, violetAccent)
                  )
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
              Surface(
                shape = RoundedCornerShape(999.dp),
                color = violetAccent.copy(alpha = 0.16f),
                border = androidx.compose.foundation.BorderStroke(
                  1.dp,
                  lightVioletAccent.copy(alpha = 0.28f)
                )
              ) {
                Text(
                  text = "code ${updateUiState.installed.versionCode}",
                  style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                  ),
                  color = lightVioletAccent,
                  modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                  maxLines = 1
                )
              }
            }
          }

          // Two tiles: Latest release & Released date
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            UpdateGlassInfoTile(
              label = stringResource(id = R.string.settings_latest_release),
              value = updateUiState.latestRelease?.let { "v${it.versionName} (code ${it.versionCode})" }
                ?: stringResource(id = R.string.settings_not_checked),
              mutedColor = mutedGlassText,
              textColor = baseTitleColor,
              modifier = Modifier.weight(1f)
            )
            UpdateGlassInfoTile(
              label = stringResource(id = R.string.settings_released_label),
              value = formattedReleaseDate,
              mutedColor = mutedGlassText,
              textColor = baseTitleColor,
              modifier = Modifier.weight(1f)
            )
          }

          // Status row with vector shield icon
          Surface(
            shape = RoundedCornerShape(14.dp),
            color = Color.White.copy(alpha = 0.04f),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
            modifier = Modifier.fillMaxWidth()
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(9.dp)
            ) {
              Icon(
                imageVector = Icons.Rounded.VerifiedUser,
                contentDescription = stringResource(id = R.string.settings_update_status),
                tint = stageStatusColor,
                modifier = Modifier.size(18.dp)
              )
              Text(
                text = updateUiState.statusMessage,
                style = MaterialTheme.typography.bodySmall.copy(
                  fontWeight = FontWeight.SemiBold,
                  lineHeight = 18.sp
                ),
                color = stageStatusColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                  .weight(1f)
                  .semantics { liveRegion = LiveRegionMode.Polite }
                  .testTag("settings_update_status_text")
              )
            }
          }
        }
      }

      if (isMandatory) {
        Surface(
          shape = RoundedCornerShape(14.dp),
          color = MaterialTheme.colorScheme.error.copy(alpha = 0.14f),
          border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.30f)),
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
              tint = Color(0xFFFB7185),
              modifier = Modifier.size(18.dp)
            )
            Text(
              text = stringResource(id = R.string.settings_critical_release),
              style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold, lineHeight = 18.sp),
              color = Color(0xFFFB7185)
            )
          }
        }
      }

      // Download Progress + Speed + Pause/Resume & Cancel Controls
      AnimatedVisibility(
        visible = isDownloading || isPausedPartial || isVerifyingOrInstalling,
        enter = if (reducedMotion) fadeIn(snap()) else expandVertically() + fadeIn(),
        exit = if (reducedMotion) fadeOut(snap()) else shrinkVertically() + fadeOut()
      ) {
        val dlText = buildString {
          if (updateUiState.totalBytes > 0L) {
            append(UpdateManager.formatBytesMb(updateUiState.downloadedBytes))
            append(" / ")
            append(UpdateManager.formatBytesMb(updateUiState.totalBytes))
          } else if (updateUiState.downloadedBytes > 0L) {
            append(UpdateManager.formatBytesMb(updateUiState.downloadedBytes))
          }
          if (isDownloading && !updateUiState.isPaused && updateUiState.speedBytesPerSec > 0L) {
            if (isNotEmpty()) append(" · ")
            append(UpdateManager.formatSpeed(updateUiState.speedBytesPerSec))
          }
        }

        val leftLabel = when (updateUiState.stage) {
          UpdateStage.Verifying -> stringResource(id = R.string.settings_verifying_hash)
          UpdateStage.Installing -> stringResource(id = R.string.settings_installing_update)
          else -> {
            val pct = if (updateUiState.progress >= 0f) " ${(updateUiState.progress * 100f).roundToInt().coerceIn(0, 100)}%" else ""
            stringResource(id = R.string.settings_downloading_verifying) + pct
          }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              text = leftLabel,
              style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold, lineHeight = 18.sp),
              color = if (isVerifyingOrInstalling) Color(0xFF10B981) else lightVioletAccent,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              modifier = Modifier.weight(1f)
            )
            if (dlText.isNotEmpty() && !isVerifyingOrInstalling) {
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = dlText,
                style = MaterialTheme.typography.bodySmall,
                color = mutedGlassText,
                maxLines = 1
              )
            }
          }

          if (updateUiState.stage == UpdateStage.Installing || updateUiState.stage == UpdateStage.Verifying) {
            LinearProgressIndicator(
              modifier = Modifier
                .fillMaxWidth()
                .height(7.dp)
                .clip(RoundedCornerShape(999.dp)),
              color = Color(0xFF10B981),
              trackColor = Color.White.copy(alpha = 0.08f)
            )
          } else {
            LinearProgressIndicator(
              progress = { animatedProgress },
              modifier = Modifier
                .fillMaxWidth()
                .height(7.dp)
                .clip(RoundedCornerShape(999.dp)),
              color = if (isPausedPartial) AccentAmber else violetAccent,
              trackColor = Color.White.copy(alpha = 0.08f)
            )
          }

          if (isDownloading || isPausedPartial) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
              GlassActionPill(
                text = if (isDownloading && !updateUiState.isPaused) {
                  stringResource(id = R.string.settings_pause)
                } else {
                  stringResource(id = R.string.settings_resume)
                },
                icon = if (isDownloading && !updateUiState.isPaused) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                darkTheme = darkTheme,
                enabled = true,
                modifier = Modifier
                  .weight(1f)
                  .testTag("pause_resume_update_button"),
                onClick = {
                  if (isDownloading && !updateUiState.isPaused) onPauseDownload()
                  else onResumeDownload()
                }
              )
              GlassActionPill(
                text = stringResource(id = R.string.action_cancel),
                icon = Icons.Rounded.Close,
                darkTheme = darkTheme,
                enabled = true,
                modifier = Modifier
                  .weight(1f)
                  .testTag("cancel_update_button"),
                onClick = onCancelDownload
              )
            }
          }
        }
      }

      // Main Dynamic CTA Button ("Download & Install", "Resume Download", "Restart App", "Retry", etc.)
      val mainAction = updateUiState.mainAction
      if (mainAction != UpdateMainAction.None) {
        val (ctaLabel, ctaIcon, ctaColor) = when (mainAction) {
          UpdateMainAction.DownloadAndInstall -> Triple(
            stringResource(id = R.string.settings_download_install),
            Icons.Rounded.SystemUpdate,
            violetAccent
          )
          UpdateMainAction.ResumeDownload -> Triple(
            stringResource(id = R.string.settings_resume_download),
            Icons.Rounded.DownloadForOffline,
            AccentAmber
          )
          UpdateMainAction.InstallVerifiedApk -> Triple(
            stringResource(id = R.string.settings_download_install),
            Icons.Rounded.InstallMobile,
            violetAccent
          )
          UpdateMainAction.OpenInstallSettings -> Triple(
            stringResource(id = R.string.settings_open_install_settings),
            Icons.Rounded.AdminPanelSettings,
            AccentAmber
          )
          UpdateMainAction.RestartApp -> Triple(
            stringResource(id = R.string.settings_restart_app),
            Icons.Rounded.RestartAlt,
            AccentGreen
          )
          UpdateMainAction.Retry -> Triple(
            stringResource(id = R.string.settings_retry_update),
            Icons.Rounded.Refresh,
            Color(0xFFEF4444)
          )
          UpdateMainAction.None -> Triple("", Icons.Rounded.SystemUpdate, violetAccent)
        }

        Box(
          modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(
              Brush.horizontalGradient(
                colors = listOf(ctaColor, lerp(ctaColor, Color.White, 0.16f))
              )
            )
            .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(18.dp))
            .clickable { onMainAction() }
            .testTag("update_now_button"),
          contentAlignment = Alignment.Center
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Icon(
              imageVector = ctaIcon,
              contentDescription = null,
              tint = Color.White,
              modifier = Modifier.size(19.dp)
            )
            Text(
              text = ctaLabel,
              style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, lineHeight = 20.sp),
              color = Color.White,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
          }
        }
      }

      // Check Updates wide violet-gradient button
      val checkEnabled = !isDownloading && !isChecking && !isVerifyingOrInstalling
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(50.dp)
          .shadow(
            elevation = if (checkEnabled) 12.dp else 0.dp,
            shape = RoundedCornerShape(18.dp),
            ambientColor = violetAccent.copy(alpha = 0.40f),
            spotColor = lightVioletAccent.copy(alpha = 0.36f)
          )
          .clip(RoundedCornerShape(18.dp))
          .background(
            brush = if (checkEnabled) {
              Brush.horizontalGradient(colors = listOf(violetAccent, lightVioletAccent))
            } else {
              Brush.horizontalGradient(
                colors = listOf(
                  Color.White.copy(alpha = 0.08f),
                  Color.White.copy(alpha = 0.06f)
                )
              )
            }
          )
          .border(
            width = 1.dp,
            color = if (checkEnabled) Color.White.copy(alpha = 0.20f) else Color.White.copy(alpha = 0.08f),
            shape = RoundedCornerShape(18.dp)
          )
          .clickable(enabled = checkEnabled) { if (!isChecking) onCheckUpdate() }
          .testTag("check_update_button"),
        contentAlignment = Alignment.Center
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Icon(
            imageVector = Icons.Rounded.Refresh,
            contentDescription = null,
            tint = if (checkEnabled) Color.White else mutedGlassText,
            modifier = Modifier.size(19.dp)
          )
          Text(
            text = if (isChecking) {
              stringResource(id = R.string.settings_checking)
            } else {
              stringResource(id = R.string.settings_check_updates)
            },
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, lineHeight = 20.sp),
            color = if (checkEnabled) Color.White else mutedGlassText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }
      }

      // Side-by-side glass buttons: Release Notes & Release Page
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        GlassActionPill(
          text = stringResource(id = R.string.settings_release_notes),
          icon = Icons.Rounded.Description,
          darkTheme = darkTheme,
          enabled = true,
          modifier = Modifier
            .weight(1f)
            .testTag("release_notes_button"),
          onClick = onOpenReleaseNotes
        )
        GlassActionPill(
          text = stringResource(id = R.string.settings_release_page),
          icon = Icons.AutoMirrored.Rounded.OpenInNew,
          darkTheme = darkTheme,
          enabled = true,
          modifier = Modifier
            .weight(1f)
            .testTag("open_release_page_button"),
          onClick = onOpenGitHubRepo
        )
      }
    }
  }
}

private fun formatUpdateReleaseDate(rawDate: String): String {
  val trimmed = rawDate.trim()
  if (trimmed.isBlank()) return trimmed
  return runCatching {
    val parser = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    val parsed = parser.parse(trimmed) ?: return@runCatching trimmed
    SimpleDateFormat("MMM d, yyyy", Locale.US).format(parsed)
  }.getOrDefault(trimmed)
}

@Composable
private fun UpdateGlassInfoTile(
  label: String,
  value: String,
  mutedColor: Color,
  textColor: Color,
  modifier: Modifier = Modifier
) {
  Surface(
    shape = RoundedCornerShape(16.dp),
    color = Color.White.copy(alpha = 0.04f),
    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
    modifier = modifier
  ) {
    Column(
      modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
      verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
      Text(
        text = label,
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium, fontSize = 11.sp),
        color = mutedColor,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
      Text(
        text = value,
        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, lineHeight = 17.sp),
        color = textColor,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
    }
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
  val bg = Color.White.copy(alpha = if (enabled) 0.06f else 0.03f)
  val borderColor = Color.White.copy(alpha = if (enabled) 0.08f else 0.05f)
  val contentColor = if (enabled) Color(0xFFF4F2FB) else Color(0xFF8F8AA6).copy(alpha = 0.6f)

  Surface(
    shape = RoundedCornerShape(16.dp),
    color = bg,
    modifier = modifier
      .heightIn(min = 48.dp)
      .border(1.dp, borderColor, RoundedCornerShape(16.dp))
      .clip(RoundedCornerShape(16.dp))
      .clickable(enabled = enabled) { onClick() }
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .heightIn(min = 48.dp)
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

@Composable
private fun UpdateCardPreviewHost(
  logoState: UpdateLogoState,
  stage: UpdateStage,
  subtitle: String,
  statusText: String,
  progress: Float = 0f,
  downloadedBytes: Long = 0L,
  totalBytes: Long = 0L,
  speedBytesPerSec: Long = 0L
) {
  val installed = InstalledAppInfo("com.aurix.music", "1.6.8", 10608L, emptySet())
  val release = RemoteVersionInfo(
    versionName = "1.6.8",
    versionCode = 10608L,
    releaseDate = "2026-11-16",
    apkUrl = "",
    sha256 = "",
    notes = UpdateManager.CURRENT_RELEASE_NOTES,
    releasePageUrl = "https://github.com/MEYYYNASH/AURIX/releases/tag/v1.6.8"
  )
  val uiState = UpdateUiState(
    stage = stage,
    installed = installed,
    latestRelease = release,
    subtitleMessage = subtitle,
    statusMessage = statusText,
    progress = progress,
    downloadedBytes = downloadedBytes,
    totalBytes = totalBytes,
    speedBytesPerSec = speedBytesPerSec
  )
  AurixTheme(darkTheme = true) {
    Box(
      modifier = Modifier
        .background(Color(0xFF0A0E1A))
        .padding(16.dp)
    ) {
      AppUpdateReleaseCard(
        logoState = logoState,
        updateUiState = uiState,
        animatedProgress = progress,
        darkTheme = true,
        onCheckUpdate = {},
        onOpenReleaseNotes = {},
        onOpenGitHubRepo = {}
      )
    }
  }
}

@Preview(name = "UpdateCard - Available", showBackground = true)
@Composable
private fun UpdateCardAvailablePreview() {
  UpdateCardPreviewHost(
    logoState = UpdateLogoState.Available,
    stage = UpdateStage.Available,
    subtitle = "AURIX v1.6.8 is ready to download",
    statusText = "Update available: v1.6.8 (code 10608)"
  )
}

@Preview(name = "UpdateCard - Downloading", showBackground = true)
@Composable
private fun UpdateCardDownloadingPreview() {
  UpdateCardPreviewHost(
    logoState = UpdateLogoState.Downloading,
    stage = UpdateStage.Downloading,
    subtitle = "Downloading v1.6.8 · 4.2 MB/s",
    statusText = "Downloading 62% (4.2 MB/s)",
    progress = 0.62f,
    downloadedBytes = 24L * 1024L * 1024L,
    totalBytes = 38L * 1024L * 1024L,
    speedBytesPerSec = 4L * 1024L * 1024L
  )
}

@Preview(name = "UpdateCard - Done", showBackground = true)
@Composable
private fun UpdateCardDonePreview() {
  UpdateCardPreviewHost(
    logoState = UpdateLogoState.Done,
    stage = UpdateStage.Done,
    subtitle = "Installer finished — tap Restart App to apply",
    statusText = "Ready to restart (v1.6.8)",
    progress = 1f
  )
}
