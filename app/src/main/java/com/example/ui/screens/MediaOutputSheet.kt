package com.example.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.playback.AudioOutputDeviceItem
import com.example.playback.AudioOutputRoute
import com.example.playback.AudioOutputRouteInfo
import com.example.playback.AudioOutputRouter
import com.example.playback.EqPreset
import com.example.playback.EqualizerUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaOutputSheet(
  outputInfo: AudioOutputRouteInfo,
  connectedDevices: List<AudioOutputDeviceItem>,
  equalizerState: EqualizerUiState,
  accentColor: Color,
  onToggleAutoRouteEq: (Boolean) -> Unit,
  onSelectRoutePreset: (AudioOutputRoute, EqPreset) -> Unit,
  onOpenEqualizer: () -> Unit,
  onDismiss: () -> Unit
) {
  val context = LocalContext.current
  val surfaceColor = MaterialTheme.colorScheme.surface
  val isDark = surfaceColor.luminance() < 0.5f || isSystemInDarkTheme()
  val sheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

  LaunchedEffect(Unit) {
    AudioOutputRouter.forceRefresh(context)
  }

  val routeDisplayName = when (outputInfo.route) {
    AudioOutputRoute.BLUETOOTH -> outputInfo.deviceName.ifBlank {
      stringResource(id = R.string.media_output_bluetooth_headphones)
    }
    AudioOutputRoute.HEADPHONES -> outputInfo.deviceName.ifBlank {
      stringResource(id = R.string.media_output_wired_headphones)
    }
    AudioOutputRoute.SPEAKER -> stringResource(id = R.string.media_output_phone_speaker)
  }

  val routeSubtitle = when (outputInfo.route) {
    AudioOutputRoute.BLUETOOTH -> outputInfo.deviceTypeLabel.ifBlank {
      stringResource(id = R.string.audio_route_bluetooth)
    }
    AudioOutputRoute.HEADPHONES -> outputInfo.deviceTypeLabel.ifBlank {
      stringResource(id = R.string.media_output_wired_headphones)
    }
    AudioOutputRoute.SPEAKER -> outputInfo.deviceTypeLabel.ifBlank {
      stringResource(id = R.string.audio_route_speaker)
    }
  }

  val routeIcon = when (outputInfo.route) {
    AudioOutputRoute.BLUETOOTH -> Icons.Rounded.BluetoothAudio
    AudioOutputRoute.HEADPHONES -> Icons.Rounded.Headphones
    AudioOutputRoute.SPEAKER -> Icons.Rounded.Speaker
  }

  val activePresetForRoute = remember(outputInfo.route, equalizerState) {
    val presetId = when (outputInfo.route) {
      AudioOutputRoute.BLUETOOTH -> equalizerState.bluetoothEqPresetId
      AudioOutputRoute.HEADPHONES -> equalizerState.headphonesEqPresetId
      AudioOutputRoute.SPEAKER -> equalizerState.speakerEqPresetId
    }
    EqPreset.fromId(presetId)
  }

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    shape = sheetShape,
    containerColor = Color.Transparent,
    contentColor = Color.White,
    scrimColor = Color.Black.copy(alpha = 0.62f),
    tonalElevation = 0.dp,
    dragHandle = null
  ) {
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .clip(sheetShape)
        .background(if (isDark) Color(0xFF0B101B) else Color(0xFF101726))
        .background(
          Brush.radialGradient(
            colors = listOf(accentColor.copy(alpha = 0.22f), Color.Transparent),
            radius = 750f
          )
        )
        .border(
          1.dp,
          Brush.verticalGradient(
            listOf(Color.White.copy(alpha = 0.25f), Color.White.copy(alpha = 0.05f))
          ),
          sheetShape
        )
        .testTag("media_output_sheet")
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .navigationBarsPadding()
          .padding(horizontal = 20.dp)
          .padding(top = 12.dp, bottom = 22.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
      ) {
        // Drag Handle
        Box(
          modifier = Modifier
            .align(Alignment.CenterHorizontally)
            .width(38.dp)
            .height(4.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.32f))
        )

        // Header
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = stringResource(id = R.string.media_output_title),
              style = MaterialTheme.typography.titleMedium.copy(
                fontSize = 19.sp,
                fontWeight = FontWeight.ExtraBold
              ),
              color = Color.White
            )
            Text(
              text = stringResource(id = R.string.media_output_subtitle),
              style = MaterialTheme.typography.bodySmall,
              color = Color.White.copy(alpha = 0.72f)
            )
          }
          IconButton(
            onClick = onDismiss,
            modifier = Modifier
              .minimumInteractiveComponentSize()
              .testTag("media_output_close_button")
          ) {
            Icon(
              imageVector = Icons.Rounded.Close,
              contentDescription = stringResource(id = R.string.cd_close_sheet),
              tint = Color.White.copy(alpha = 0.82f)
            )
          }
        }

        // Active Output Hero Card
        Surface(
          shape = RoundedCornerShape(22.dp),
          color = accentColor.copy(alpha = 0.16f),
          border = BorderStroke(1.2.dp, accentColor.copy(alpha = 0.65f)),
          modifier = Modifier.fillMaxWidth()
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
          ) {
            Surface(
              shape = CircleShape,
              color = accentColor.copy(alpha = 0.25f),
              border = BorderStroke(1.dp, accentColor.copy(alpha = 0.75f)),
              modifier = Modifier.size(48.dp)
            ) {
              Box(contentAlignment = Alignment.Center) {
                Icon(
                  imageVector = routeIcon,
                  contentDescription = null,
                  tint = Color.White,
                  modifier = Modifier.size(24.dp)
                )
              }
            }

            Column(modifier = Modifier.weight(1f)) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                Text(
                  text = routeDisplayName,
                  style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 16.sp
                  ),
                  color = Color.White,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                  modifier = Modifier.weight(1f, fill = false)
                )
                Surface(
                  shape = CircleShape,
                  color = accentColor.copy(alpha = 0.32f),
                  border = BorderStroke(1.dp, accentColor.copy(alpha = 0.85f))
                ) {
                  Text(
                    text = stringResource(id = R.string.media_output_active_badge),
                    style = MaterialTheme.typography.labelSmall.copy(
                      fontSize = 10.sp,
                      fontWeight = FontWeight.ExtraBold
                    ),
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                  )
                }
              }
              Spacer(modifier = Modifier.height(2.dp))
              Text(
                text = "$routeSubtitle · ${stringResource(id = R.string.media_output_auto_eq_pill, presetDisplayName(activePresetForRoute))}",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.82f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
            }
          }
        }

        // Connected Devices List (Bluetooth Headphones, Wired 3.5mm / USB-C Headphones, Phone Speaker)
        Column(
          modifier = Modifier.fillMaxWidth(),
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          val devicesToShow = if (connectedDevices.isNotEmpty()) {
            connectedDevices
          } else {
            listOf(
              AudioOutputDeviceItem(
                id = -1,
                route = outputInfo.route,
                displayName = routeDisplayName,
                typeSubtitle = routeSubtitle,
                isActive = true
              )
            )
          }

          devicesToShow.forEach { device ->
            val devIcon = when (device.route) {
              AudioOutputRoute.BLUETOOTH -> Icons.Rounded.BluetoothAudio
              AudioOutputRoute.HEADPHONES -> Icons.Rounded.Headphones
              AudioOutputRoute.SPEAKER -> Icons.Rounded.Speaker
            }
            val localizedName = when {
              device.route == AudioOutputRoute.SPEAKER -> stringResource(id = R.string.media_output_phone_speaker)
              device.route == AudioOutputRoute.HEADPHONES &&
                (device.displayName.startsWith("Wired Headphones") || device.displayName.isBlank()) ->
                stringResource(id = R.string.media_output_wired_headphones)
              device.route == AudioOutputRoute.BLUETOOTH &&
                (device.displayName.startsWith("Bluetooth Headphones") || device.displayName.isBlank()) ->
                stringResource(id = R.string.media_output_bluetooth_headphones)
              else -> device.displayName
            }

            Surface(
              onClick = {
                AudioOutputRouter.launchSystemMediaOutputSwitcher(context)
              },
              shape = RoundedCornerShape(18.dp),
              color = if (device.isActive) Color.White.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.06f),
              border = BorderStroke(
                1.dp,
                if (device.isActive) accentColor.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.12f)
              ),
              modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 56.dp)
                .testTag("media_output_device_${device.route.id}")
            ) {
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
              ) {
                Icon(
                  imageVector = devIcon,
                  contentDescription = null,
                  tint = if (device.isActive) accentColor else Color.White.copy(alpha = 0.72f),
                  modifier = Modifier.size(22.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = localizedName,
                    style = MaterialTheme.typography.bodyMedium.copy(
                      fontWeight = if (device.isActive) FontWeight.Bold else FontWeight.Medium
                    ),
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                  )
                  Text(
                    text = device.typeSubtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.65f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                  )
                }
                Icon(
                  imageVector = if (device.isActive) Icons.Rounded.CheckCircle else Icons.Rounded.SwapHoriz,
                  contentDescription = null,
                  tint = if (device.isActive) accentColor else Color.White.copy(alpha = 0.50f),
                  modifier = Modifier.size(20.dp)
                )
              }
            }
          }
        }

        // Per-Device Quick Auto-EQ Strip
        Surface(
          shape = RoundedCornerShape(18.dp),
          color = Color.White.copy(alpha = 0.07f),
          border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = stringResource(id = R.string.audio_pro_auto_bt_title),
                  style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                  color = Color.White
                )
                Text(
                  text = stringResource(id = R.string.audio_pro_auto_bt_sub),
                  style = MaterialTheme.typography.labelSmall,
                  color = Color.White.copy(alpha = 0.68f)
                )
              }
              Switch(
                checked = equalizerState.autoSwitchBluetoothEq,
                onCheckedChange = onToggleAutoRouteEq,
                modifier = Modifier.testTag("media_output_auto_eq_switch")
              )
            }

            Row(
              modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              EqPreset.selectablePresets.forEach { preset ->
                val selected = activePresetForRoute == preset
                Surface(
                  onClick = { onSelectRoutePreset(outputInfo.route, preset) },
                  shape = CircleShape,
                  color = if (selected) accentColor.copy(alpha = 0.28f) else Color.White.copy(alpha = 0.08f),
                  border = BorderStroke(
                    1.dp,
                    if (selected) accentColor else Color.White.copy(alpha = 0.16f)
                  )
                ) {
                  Text(
                    text = presetDisplayName(preset),
                    style = MaterialTheme.typography.labelMedium.copy(
                      fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                    ),
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                  )
                }
              }
            }
          }
        }

        // Action Buttons: System Output Switcher & Bluetooth Settings + Audio Pro EQ
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          Button(
            onClick = {
              AudioOutputRouter.launchSystemMediaOutputSwitcher(context)
            },
            colors = ButtonDefaults.buttonColors(
              containerColor = accentColor,
              contentColor = Color(0xFF07101C)
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
              .weight(1f)
              .defaultMinSize(minHeight = 48.dp)
              .testTag("media_output_system_switcher_button")
          ) {
            Icon(
              imageVector = Icons.Rounded.SpeakerGroup,
              contentDescription = null,
              modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = stringResource(id = R.string.media_output_switch_system),
              style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold),
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
          }

          OutlinedButton(
            onClick = {
              runCatching {
                context.startActivity(
                  Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                  }
                )
              }
            },
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
              .defaultMinSize(minHeight = 48.dp)
              .testTag("media_output_bluetooth_settings_button")
          ) {
            Icon(
              imageVector = Icons.Rounded.Bluetooth,
              contentDescription = stringResource(id = R.string.media_output_bluetooth_settings),
              modifier = Modifier.size(18.dp)
            )
          }

          OutlinedButton(
            onClick = {
              onDismiss()
              onOpenEqualizer()
            },
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
              .defaultMinSize(minHeight = 48.dp)
              .testTag("media_output_open_eq_button")
          ) {
            Icon(
              imageVector = Icons.Rounded.Equalizer,
              contentDescription = stringResource(id = R.string.eq_title),
              modifier = Modifier.size(18.dp)
            )
          }
        }
      }
    }
  }
}
