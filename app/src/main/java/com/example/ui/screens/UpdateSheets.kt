package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.BuildConfig
import com.example.R
import com.example.data.APP_RELEASE_DATE
import com.example.data.RemoteVersionInfo
import com.example.data.UpdateManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReleaseNotesBottomSheet(
  latestRelease: RemoteVersionInfo?,
  installedVersionName: String,
  installedVersionCode: Long,
  canDownloadUpdate: Boolean,
  onDownloadAndInstall: () -> Unit,
  onDismiss: () -> Unit
) {
  val versionLabel = latestRelease?.tagName ?: "v$installedVersionName"
  val versionCode = latestRelease?.versionCode ?: installedVersionCode
  val releaseDate = latestRelease?.releaseDate?.ifBlank { APP_RELEASE_DATE } ?: APP_RELEASE_DATE
  val notesList = latestRelease?.notes?.takeIf { it.isNotEmpty() } ?: UpdateManager.CURRENT_RELEASE_NOTES

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 24.dp, vertical = 14.dp)
        .windowInsetsPadding(WindowInsets.navigationBars),
      verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        Surface(
          shape = CircleShape,
          color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
          modifier = Modifier.size(44.dp)
        ) {
          Box(contentAlignment = Alignment.Center) {
            Icon(
              imageVector = Icons.Rounded.NewReleases,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(22.dp)
            )
          }
        }
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = stringResource(id = R.string.settings_release_notes_title, versionLabel, versionCode),
            style = MaterialTheme.typography.titleLarge.copy(
              fontWeight = FontWeight.Bold,
              fontSize = 20.sp,
              lineHeight = 26.sp
            ),
            color = MaterialTheme.colorScheme.onSurface
          )
          Text(
            text = stringResource(id = R.string.settings_release_date_inline, releaseDate),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }

      Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(
          modifier = Modifier.padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          if (notesList.isEmpty()) {
            Text(
              text = stringResource(id = R.string.settings_no_changelog),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          } else {
            notesList.forEach { note ->
              Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Top
              ) {
                Box(
                  modifier = Modifier
                    .padding(top = 7.dp)
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                )
                Text(
                  text = note,
                  style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 21.sp),
                  color = MaterialTheme.colorScheme.onSurface
                )
              }
            }
          }
        }
      }

      if (canDownloadUpdate && latestRelease != null) {
        Button(
          onClick = {
            onDismiss()
            onDownloadAndInstall()
          },
          modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .testTag("release_notes_download_button"),
          shape = RoundedCornerShape(16.dp)
        ) {
          Icon(
            imageVector = Icons.Rounded.SystemUpdate,
            contentDescription = null,
            modifier = Modifier.size(18.dp)
          )
          Spacer(modifier = Modifier.size(8.dp))
          Text(
            text = stringResource(id = R.string.settings_download_install),
            fontWeight = FontWeight.Bold
          )
        }
      } else {
        TextButton(
          onClick = onDismiss,
          modifier = Modifier
            .align(Alignment.End)
            .height(48.dp)
        ) {
          Text(
            text = stringResource(id = R.string.whats_new_got_it),
            fontWeight = FontWeight.SemiBold
          )
        }
      }

      Spacer(modifier = Modifier.height(6.dp))
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhatsNewBottomSheet(
  reducedMotion: Boolean,
  onOpenUpdatesTab: () -> Unit,
  onDismiss: () -> Unit
) {
  ModalBottomSheet(
    onDismissRequest = onDismiss,
    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 24.dp, vertical = 14.dp)
        .windowInsetsPadding(WindowInsets.navigationBars),
      verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
      ) {
        UpdateLogo(
          state = UpdateLogoState.Done,
          size = 60.dp,
          reducedMotion = reducedMotion,
          modifier = Modifier.testTag("whats_new_logo")
        )
        Column(modifier = Modifier.weight(1f)) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Surface(
              shape = RoundedCornerShape(999.dp),
              color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
              ) {
                Icon(
                  imageVector = Icons.Rounded.AutoAwesome,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.primary,
                  modifier = Modifier.size(13.dp)
                )
                Text(
                  text = "v${BuildConfig.VERSION_NAME}",
                  style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                  color = MaterialTheme.colorScheme.primary
                )
              }
            }
            Text(
              text = APP_RELEASE_DATE,
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
          Spacer(modifier = Modifier.height(4.dp))
          Text(
            text = stringResource(id = R.string.whats_new_title, BuildConfig.VERSION_NAME),
            style = MaterialTheme.typography.titleLarge.copy(
              fontWeight = FontWeight.Bold,
              fontSize = 21.sp,
              lineHeight = 27.sp
            ),
            color = MaterialTheme.colorScheme.onSurface
          )
        }
      }

      Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(
          modifier = Modifier.padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          UpdateManager.CURRENT_RELEASE_NOTES.forEach { bullet ->
            Row(
              horizontalArrangement = Arrangement.spacedBy(10.dp),
              verticalAlignment = Alignment.Top
            ) {
              Icon(
                imageVector = Icons.Rounded.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                  .padding(top = 2.dp)
                  .size(17.dp)
              )
              Text(
                text = bullet,
                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
                color = MaterialTheme.colorScheme.onSurface
              )
            }
          }
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        TextButton(
          onClick = {
            onDismiss()
            onOpenUpdatesTab()
          },
          modifier = Modifier
            .weight(1f)
            .height(48.dp)
            .testTag("whats_new_settings_button")
        ) {
          Text(
            text = stringResource(id = R.string.whats_new_view_updates),
            fontWeight = FontWeight.SemiBold
          )
        }
        Button(
          onClick = onDismiss,
          modifier = Modifier
            .weight(1.3f)
            .height(48.dp)
            .testTag("whats_new_dismiss_button"),
          shape = RoundedCornerShape(16.dp)
        ) {
          Text(
            text = stringResource(id = R.string.whats_new_got_it),
            fontWeight = FontWeight.Bold
          )
        }
      }

      Spacer(modifier = Modifier.height(6.dp))
    }
  }
}
