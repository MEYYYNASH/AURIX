package com.example.playback

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.provider.Settings
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
  val deviceName: String = "",
  val deviceTypeLabel: String = ""
)

data class AudioOutputDeviceItem(
  val id: Int,
  val route: AudioOutputRoute,
  val displayName: String,
  val typeSubtitle: String,
  val isActive: Boolean
)

/**
 * Zero-permission audio output route monitor backed by AudioManager.AudioDeviceCallback
 * and headset/Bluetooth routing broadcasts.
 * Automatically detects Bluetooth audio (A2DP / BLE / SCO / Hearing Aid), wired 3.5mm / USB-C
 * headphones, and phone speaker so AudioEqualizerController, NowPlayingSheet, and the
 * AURIX Dynamic Island can display live device names and switch EQ profiles seamlessly
 * without requiring dangerous BLUETOOTH_CONNECT permissions.
 */
object AudioOutputRouter {
  private val _outputInfo = MutableStateFlow(AudioOutputRouteInfo())
  val outputInfo: StateFlow<AudioOutputRouteInfo> = _outputInfo.asStateFlow()

  private val _connectedDevices = MutableStateFlow<List<AudioOutputDeviceItem>>(emptyList())
  val connectedDevices: StateFlow<List<AudioOutputDeviceItem>> = _connectedDevices.asStateFlow()

  @Volatile
  private var registered = false
  private var callback: AudioDeviceCallback? = null
  private var headsetReceiver: BroadcastReceiver? = null
  private var onRouteChangedListener: ((AudioOutputRouteInfo) -> Unit)? = null

  private const val ACTION_MEDIA_OUTPUT = "com.android.settings.panel.action.MEDIA_OUTPUT"
  private const val EXTRA_PACKAGE_NAME = "com.android.settings.panel.extra.PACKAGE_NAME"

  fun start(context: Context, onRouteChanged: (AudioOutputRouteInfo) -> Unit) {
    val appCtx = context.applicationContext
    onRouteChangedListener = onRouteChanged
    val audioManager = appCtx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
      ?: return

    val initial = resolveCurrentRoute(audioManager)
    _outputInfo.value = initial
    _connectedDevices.value = resolveAvailableOutputDevices(audioManager, initial)
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
      audioManager.registerAudioDeviceCallback(
        deviceCallback,
        android.os.Handler(android.os.Looper.getMainLooper())
      )
    }

    val receiver = object : BroadcastReceiver() {
      override fun onReceive(ctx: Context?, intent: Intent?) {
        refresh(audioManager)
      }
    }
    headsetReceiver = receiver
    runCatching {
      val filter = IntentFilter().apply {
        addAction(Intent.ACTION_HEADSET_PLUG)
        addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        addAction("android.bluetooth.a2dp.profile.action.CONNECTION_STATE_CHANGED")
        addAction("android.bluetooth.headset.profile.action.CONNECTION_STATE_CHANGED")
      }
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        appCtx.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
      } else {
        appCtx.registerReceiver(receiver, filter)
      }
    }
  }

  fun stop(context: Context) {
    val appCtx = context.applicationContext
    val cb = callback
    val audioManager = appCtx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    if (cb != null) {
      runCatching { audioManager?.unregisterAudioDeviceCallback(cb) }
    }
    headsetReceiver?.let { r ->
      runCatching { appCtx.unregisterReceiver(r) }
    }
    callback = null
    headsetReceiver = null
    registered = false
    onRouteChangedListener = null
  }

  fun forceRefresh(context: Context) {
    val audioManager = context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
    refresh(audioManager)
  }

  private fun refresh(audioManager: AudioManager) {
    val next = resolveCurrentRoute(audioManager)
    val prev = _outputInfo.value
    _outputInfo.value = next
    _connectedDevices.value = resolveAvailableOutputDevices(audioManager, next)
    if (next.route != prev.route || next.deviceName != prev.deviceName) {
      onRouteChangedListener?.invoke(next)
    }
  }

  private fun isBluetoothType(type: Int): Boolean {
    return type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
      type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
      (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && type == AudioDeviceInfo.TYPE_HEARING_AID) ||
      (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && (
        type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
          type == AudioDeviceInfo.TYPE_BLE_SPEAKER ||
          type == AudioDeviceInfo.TYPE_BLE_BROADCAST
        ))
  }

  private fun isWiredOrUsbHeadphoneType(type: Int): Boolean {
    return type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
      type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
      type == AudioDeviceInfo.TYPE_USB_HEADSET ||
      type == AudioDeviceInfo.TYPE_USB_DEVICE ||
      type == AudioDeviceInfo.TYPE_LINE_ANALOG ||
      type == AudioDeviceInfo.TYPE_LINE_DIGITAL
  }

  private fun sanitizeDeviceProductName(raw: CharSequence?, fallback: String): String {
    val cleaned = raw?.toString()?.trim().orEmpty()
    if (cleaned.isBlank()) return fallback
    // Avoid showing the raw phone model code when querying internal speaker or generic sink
    if (cleaned.equals(Build.MODEL, ignoreCase = true) || cleaned.equals(Build.DEVICE, ignoreCase = true)) {
      return fallback
    }
    return cleaned
  }

  fun resolveCurrentRoute(audioManager: AudioManager): AudioOutputRouteInfo {
    val devices = runCatching {
      audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
    }.getOrNull().orEmpty()

    // Prefer Bluetooth A2DP / BLE / Hearing Aid / SCO first
    val btDevice = devices.firstOrNull { d -> isBluetoothType(d.type) }
    if (btDevice != null) {
      val typeLabel = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && btDevice.type == AudioDeviceInfo.TYPE_BLE_HEADSET -> "LE Audio"
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && btDevice.type == AudioDeviceInfo.TYPE_BLE_SPEAKER -> "BLE Speaker"
        btDevice.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "Bluetooth A2DP"
        else -> "Bluetooth Audio"
      }
      val name = sanitizeDeviceProductName(btDevice.productName, "")
      return AudioOutputRouteInfo(
        route = AudioOutputRoute.BLUETOOTH,
        deviceName = name,
        deviceTypeLabel = typeLabel
      )
    }

    // Next check wired 3.5mm or USB-C headphones/headset
    val wiredDevice = devices.firstOrNull { d -> isWiredOrUsbHeadphoneType(d.type) }
    if (wiredDevice != null) {
      val isUsb = wiredDevice.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
        wiredDevice.type == AudioDeviceInfo.TYPE_USB_DEVICE
      val typeLabel = if (isUsb) "USB-C / DAC Headphones" else "3.5mm Wired Headphones"
      val name = sanitizeDeviceProductName(wiredDevice.productName, "")
      return AudioOutputRouteInfo(
        route = AudioOutputRoute.HEADPHONES,
        deviceName = name,
        deviceTypeLabel = typeLabel
      )
    }

    return AudioOutputRouteInfo(
      route = AudioOutputRoute.SPEAKER,
      deviceName = "",
      deviceTypeLabel = "Stereo Speaker"
    )
  }

  fun resolveAvailableOutputDevices(
    audioManager: AudioManager,
    activeRoute: AudioOutputRouteInfo = resolveCurrentRoute(audioManager)
  ): List<AudioOutputDeviceItem> {
    val devices = runCatching {
      audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
    }.getOrNull().orEmpty()

    val items = mutableListOf<AudioOutputDeviceItem>()

    // 1. Connected Bluetooth outputs
    devices.filter { isBluetoothType(it.type) }
      .distinctBy { "${it.type}_${it.productName}" }
      .forEach { d ->
        val name = sanitizeDeviceProductName(d.productName, "Bluetooth Headphones")
        val typeSub = when {
          Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && d.type == AudioDeviceInfo.TYPE_BLE_HEADSET -> "Bluetooth LE Audio"
          d.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "Bluetooth Hi-Res A2DP"
          else -> "Bluetooth Wireless"
        }
        items.add(
          AudioOutputDeviceItem(
            id = d.id,
            route = AudioOutputRoute.BLUETOOTH,
            displayName = name,
            typeSubtitle = typeSub,
            isActive = activeRoute.route == AudioOutputRoute.BLUETOOTH &&
              (activeRoute.deviceName.isBlank() || activeRoute.deviceName.equals(name, ignoreCase = true))
          )
        )
      }

    // 2. Connected Wired / USB-C Headphones
    devices.filter { isWiredOrUsbHeadphoneType(it.type) }
      .distinctBy { "${it.type}_${it.productName}" }
      .forEach { d ->
        val isUsb = d.type == AudioDeviceInfo.TYPE_USB_HEADSET || d.type == AudioDeviceInfo.TYPE_USB_DEVICE
        val fallback = if (isUsb) "USB-C Wired Headphones" else "Wired Headphones (3.5mm)"
        val name = sanitizeDeviceProductName(d.productName, fallback)
        val typeSub = if (isUsb) "USB-C Digital Audio" else "Wired 3.5mm Jack"
        items.add(
          AudioOutputDeviceItem(
            id = d.id,
            route = AudioOutputRoute.HEADPHONES,
            displayName = name,
            typeSubtitle = typeSub,
            isActive = activeRoute.route == AudioOutputRoute.HEADPHONES
          )
        )
      }

    // 3. Phone Speaker (always available)
    items.add(
      AudioOutputDeviceItem(
        id = -1,
        route = AudioOutputRoute.SPEAKER,
        displayName = "Phone Speaker",
        typeSubtitle = "Built-in Stereo Speaker",
        isActive = activeRoute.route == AudioOutputRoute.SPEAKER
      )
    )

    return items
  }

  /**
   * Opens the native Android 11+ System Media Output Switcher panel so the user can
   * switch seamlessly between Bluetooth headphones, wired headphones, and phone speaker.
   * Falls back gracefully to Bluetooth settings if the OEM ROM omits the Media Output panel.
   */
  fun launchSystemMediaOutputSwitcher(context: Context): Boolean {
    val pkg = context.packageName
    val candidates = listOf(
      Intent(ACTION_MEDIA_OUTPUT).apply {
        putExtra(EXTRA_PACKAGE_NAME, pkg)
        putExtra("android.media.extra.PACKAGE_NAME", pkg)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      },
      Intent("com.android.systemui.action.LAUNCH_MEDIA_OUTPUT_DIALOG").apply {
        setPackage("com.android.systemui")
        putExtra("package_name", pkg)
        putExtra(EXTRA_PACKAGE_NAME, pkg)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      },
      Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      },
      Intent(Settings.ACTION_SOUND_SETTINGS).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
    )
    for (intent in candidates) {
      val launched = runCatching {
        context.startActivity(intent)
        true
      }.getOrDefault(false)
      if (launched) return true
    }
    return false
  }
}
