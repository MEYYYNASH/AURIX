package com.example.ui.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.GitHubRelease
import com.example.ui.theme.*

@Composable
fun SettingsScreen(
  currentTheme: String,
  currentAccentHex: String,
  currentLanguage: String,
  dynamicBg: Boolean,
  reducedMotion: Boolean,
  hasCurrentSong: Boolean,
  githubOwner: String,
  githubRepo: String,
  updateStatus: String,
  latestRelease: GitHubRelease?,
  onGithubOwnerChanged: (String) -> Unit,
  onGithubRepoChanged: (String) -> Unit,
  onCheckUpdate: (Context) -> Unit,
  onDownloadUpdate: (Context) -> Unit,
  onInstallUpdate: (Context) -> Unit,
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

  LazyColumn(
    modifier = Modifier
      .fillMaxSize()
      .padding(horizontal = 20.dp),
    verticalArrangement = Arrangement.spacedBy(20.dp),
    contentPadding = PaddingValues(
      top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 18.dp,
      bottom = if (hasCurrentSong) 190.dp else 120.dp
    )
  ) {
    item {
      Text(
        text = "Settings",
        style = MaterialTheme.typography.displayLarge.copy(fontSize = 28.sp, fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.onBackground
      )
    }

    // Theme segmented pill matching HTML design
    item {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
          text = "Theme",
          style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
          color = MaterialTheme.colorScheme.onBackground
        )
        Surface(
          shape = RoundedCornerShape(999.dp),
          color = MaterialTheme.colorScheme.surfaceVariant,
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
    }

    // Accent color circular dots
    item {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
          text = "Accent color",
          style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
          color = MaterialTheme.colorScheme.onBackground
        )
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          accents.forEach { (hex, color) ->
            val selected = currentAccentHex.equals(hex, ignoreCase = true)
            Box(
              modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .then(
                  if (selected) Modifier.border(2.5.dp, MaterialTheme.colorScheme.onBackground, CircleShape).padding(3.dp)
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

    // Now Playing toggles (Dynamic artwork background & Reduced motion)
    item {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
          text = "Now Playing",
          style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
          color = MaterialTheme.colorScheme.onBackground
        )
        Surface(
          shape = RoundedCornerShape(18.dp),
          color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
          modifier = Modifier.fillMaxWidth().clickable { onDynamicBgChanged(!dynamicBg) }
        ) {
          Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              "Dynamic artwork background",
              style = MaterialTheme.typography.bodyLarge,
              color = MaterialTheme.colorScheme.onSurface
            )
            Checkbox(
              checked = dynamicBg,
              onCheckedChange = onDynamicBgChanged,
              modifier = Modifier.testTag("dynamic_bg_toggle")
            )
          }
        }

        Surface(
          shape = RoundedCornerShape(18.dp),
          color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
          modifier = Modifier.fillMaxWidth().clickable { onReducedMotionChanged(!reducedMotion) }
        ) {
          Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              "Reduced motion",
              style = MaterialTheme.typography.bodyLarge,
              color = MaterialTheme.colorScheme.onSurface
            )
            Checkbox(
              checked = reducedMotion,
              onCheckedChange = onReducedMotionChanged,
              modifier = Modifier.testTag("reduced_motion_toggle")
            )
          }
        }
      }
    }

    // Language
    item {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
          text = "Language",
          style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
          color = MaterialTheme.colorScheme.onBackground
        )
        Surface(
          shape = RoundedCornerShape(999.dp),
          color = MaterialTheme.colorScheme.surfaceVariant,
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

    // GitHub Updates & Releases
    item {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
          text = "GitHub Updates & Releases",
          style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
          color = MaterialTheme.colorScheme.onBackground
        )
        Surface(
          shape = RoundedCornerShape(22.dp),
          color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
          ) {
            OutlinedTextField(
              value = ownerInput,
              onValueChange = { ownerInput = it; onGithubOwnerChanged(it) },
              label = { Text("GitHub Username / Owner") },
              modifier = Modifier.fillMaxWidth(),
              shape = RoundedCornerShape(16.dp),
              singleLine = true
            )
            OutlinedTextField(
              value = repoInput,
              onValueChange = { repoInput = it; onGithubRepoChanged(it) },
              label = { Text("GitHub Repository Name") },
              modifier = Modifier.fillMaxWidth(),
              shape = RoundedCornerShape(16.dp),
              singleLine = true
            )
            Text(
              text = "Status: $updateStatus",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (latestRelease != null && latestRelease.body.isNotBlank()) {
              Text(
                text = latestRelease.body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
              Button(
                onClick = { onCheckUpdate(context) },
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.testTag("check_update_button")
              ) {
                Text("Check for Update")
              }
              if (latestRelease != null) {
                Button(
                  onClick = { onDownloadUpdate(context) },
                  shape = RoundedCornerShape(20.dp)
                ) {
                  Text("Download APK")
                }
                Button(
                  onClick = { onInstallUpdate(context) },
                  shape = RoundedCornerShape(20.dp)
                ) {
                  Text("Install")
                }
              }
            }
          }
        }
      }
    }

    // About
    item {
      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
          text = "About",
          style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
          color = MaterialTheme.colorScheme.onBackground
        )
        Text(
          text = "AURIX 1.2.7. Music from Audius, lyrics from LRCLIB. GitHub OTA updates enabled.",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    }
  }
}
