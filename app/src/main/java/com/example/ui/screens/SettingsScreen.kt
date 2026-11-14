package com.example.ui.screens

import android.content.Context
import androidx.compose.foundation.background
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
  updateStatus: String,
  latestRelease: GitHubRelease?,
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
    "#3B77F5" to AccentBlue,
    "#EF4444" to AccentRed,
    "#EC4899" to AccentPink,
    "#22C55E" to AccentGreen,
    "#F59E0B" to AccentAmber,
    "#14B8A6" to AccentTeal,
    "#8B5CF6" to AccentPurple
  )

  LazyColumn(
    modifier = Modifier
      .fillMaxSize()
      .padding(horizontal = 16.dp)
      .windowInsetsPadding(WindowInsets.safeDrawing),
    verticalArrangement = Arrangement.spacedBy(24.dp),
    contentPadding = PaddingValues(top = 16.dp, bottom = 120.dp)
  ) {
    item {
      Text(
        text = "Settings",
        style = MaterialTheme.typography.displayLarge.copy(fontSize = 28.sp),
        color = MaterialTheme.colorScheme.onBackground
      )
    }

    // GitHub Updates
    item {
      SettingsSection(title = "GitHub Updates & Releases") {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
          Text(text = "Status: $updateStatus", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
          Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
              onClick = { onCheckUpdate(context) },
              shape = RoundedCornerShape(20.dp)
            ) {
              Text("Check for Update")
            }
            if (latestRelease != null) {
              Button(
                onClick = { onDownloadUpdate(context) },
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
              ) {
                Text("Download APK")
              }
              Button(
                onClick = { onInstallUpdate(context) },
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
              ) {
                Text("Install Update")
              }
            }
          }
        }
      }
    }

    item {
      SettingsSection(title = "Language") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          FilterChip(
            selected = currentLanguage == "en",
            onClick = { onLanguageChanged("en") },
            label = { Text("English") }
          )
          FilterChip(
            selected = currentLanguage == "km",
            onClick = { onLanguageChanged("km") },
            label = { Text("ភាសាខ្មែរ") }
          )
        }
      }
    }

    item {
      SettingsSection(title = "Theme") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          listOf("system" to "System", "light" to "Light", "dark" to "Dark").forEach { (key, label) ->
            FilterChip(
              selected = currentTheme == key,
              onClick = { onThemeChanged(key) },
              label = { Text(label) }
            )
          }
        }
      }
    }

    item {
      SettingsSection(title = "Accent color") {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          accents.forEach { (hex, color) ->
            Box(
              modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(color)
                .clickable { onAccentChanged(hex) },
              contentAlignment = Alignment.Center
            ) {
              if (currentAccentHex == hex) {
                Box(
                  modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                )
              }
            }
          }
        }
      }
    }

    item {
      SettingsSection(title = "Preferences") {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text("Dynamic artwork background", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground)
            Switch(checked = dynamicBg, onCheckedChange = onDynamicBgChanged)
          }
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text("Reduced motion", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground)
            Switch(checked = reducedMotion, onCheckedChange = onReducedMotionChanged)
          }
        }
      }
    }

    item {
      SettingsSection(title = "About") {
        Column {
          Text("AURIX / All Sound Finder v0.1.1", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onBackground)
          Spacer(modifier = Modifier.height(4.dp))
          Text("Powered by Audius and LRCLIB. GitHub OTA Update Enabled.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
      }
    }
  }
}

@Composable
fun SettingsSection(title: String, content: @Composable () -> Unit) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(22.dp))
      .background(MaterialTheme.colorScheme.surfaceVariant)
      .padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    Text(text = title, style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp), color = MaterialTheme.colorScheme.onSurface)
    content()
  }
}
