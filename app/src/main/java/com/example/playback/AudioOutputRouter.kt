package com.example.playback

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AudioOutputRoute(val id: String) {
  SPEAKER("speaker"),
  BLUETOOTH("bluetooth"),
  HEADPHONES("headphones");

  companion object {
    fun fromId(id: String?): AudioOutputRoute = entries.firstOrNull { it.id == id } ?: SPEAKER
  }
}

data class AudioOutputRouteInfo(
  val route: AudioOutputRoute = AudioOutputRoute.SPEAKER,
  val deviceName: String = ""
)

/**
 * Zero-permission audio output route monitor backed by AudioManager.AudioDeviceCallback.
 * Automatically detects Bluetooth audio, wired/USB headphones, and phone speaker so
 * AudioEqualizerController can switch EQ profiles seamlessly without requiring
 * dangerous BLUETOOTH_CONNECT permissions.
 */
object AudioOutputRouter {
  private val _outputInfo = MutableStateFlow(AudioOutputRouteInfo())
  val outputInfo: StateFlow<AudioOutputRouteInfo> = _outputInfo.asStateFlow()

  @Volatile
  private var registered = false
  private var callback: AudioDeviceCallback? = null
  private var onRouteChangedListener: ((AudioOutputRouteInfo) -> Unit)? = null

  fun start(context: Context, onRouteChanged: (AudioOutputRouteInfo) -> Unit) {
    onRouteChangedListener = onRouteChanged
    val audioManager = context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
      ?: return

    val initial = resolveCurrentRoute(audioManager)
    _outputInfo.value = initial
    onRouteChanged(initial)

    if (registered) return
    registered = true

    val deviceCallback = object : AudioDeviceCallback() {
      override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
        refresh(audioManager)
      }

      override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
        refresh(audioManager)
      }
    }
    callback = deviceCallback
    runCatching {
      audioManager.registerAudioDeviceCallback(deviceCallback, null)
    }
  }

  fun stop(context: Context) {
    val cb = callback ?: return
    val audioManager = context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    runCatching { audioManager?.unregisterAudioDeviceCallback(cb) }
    callback = null
    registered = false
    onRouteChangedListener = null
  }

  private fun refresh(audioManager: AudioManager) {
    val next = resolveCurrentRoute(audioManager)
    val prev = _outputInfo.value
    _outputInfo.value = next
    if (next.route != prev.route || next.deviceName != prev.deviceName) {
      onRouteChangedListener?.invoke(next)
    }
  }

  fun resolveCurrentRoute(audioManager: AudioManager): AudioOutputRouteInfo {
    val devices = runCatching {
      audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
    }.getOrNull().orEmpty()

    // Prefer Bluetooth A2DP / BLE / Hearing Aid / SCO first
    val btDevice = devices.firstOrNull { d ->
      d.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
        d.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
        (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && d.type == AudioDeviceInfo.TYPE_HEARING_AID) ||
        (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && (
          d.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
            d.type == AudioDeviceInfo.TYPE_BLE_SPEAKER ||
            d.type == AudioDeviceInfo.TYPE_BLE_BROADCAST
          ))
    }
    if (btDevice != null) {
      val name = btDevice.productName?.toString()?.trim().orEmpty()
      return AudioOutputRouteInfo(AudioOutputRoute.BLUETOOTH, name)
    }

    // Next check wired or USB headphones/headset
    val wiredDevice = devices.firstOrNull { d ->
      d.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
        d.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
        d.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
        d.type == AudioDeviceInfo.TYPE_USB_DEVICE
    }
    if (wiredDevice != null) {
      val name = wiredDevice.productName?.toString()?.trim().orEmpty()
      return AudioOutputRouteInfo(AudioOutputRoute.HEADPHONES, name)
    }

    return AudioOutputRouteInfo(AudioOutputRoute.SPEAKER, "")
  }
}
