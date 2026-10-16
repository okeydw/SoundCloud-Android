package com.scd.android

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

data class OutputDevice(val key: String, val kind: String, val name: String)

object AudioOutput {
    const val AUTO = "auto"
    const val SPEAKER = "speaker"
    const val WIRED = "wired"
    const val BLUETOOTH = "bt"
    const val USB = "usb"
    const val HDMI = "hdmi"

    var revision by mutableIntStateOf(0)
        private set

    private var callback: AudioDeviceCallback? = null

    private fun kindOf(type: Int): String? = when (type) {
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> SPEAKER
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET -> WIRED
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> BLUETOOTH
        AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_DEVICE -> USB
        AudioDeviceInfo.TYPE_HDMI -> HDMI
        else -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            (type == AudioDeviceInfo.TYPE_BLE_HEADSET || type == AudioDeviceInfo.TYPE_BLE_SPEAKER)
        ) BLUETOOTH else null
    }

    private fun keyOf(d: AudioDeviceInfo, kind: String): String =
        if (kind == SPEAKER) SPEAKER else kind + ":" + d.productName.toString()

    private fun raw(context: Context): List<AudioDeviceInfo> {
        val am = context.getSystemService(AudioManager::class.java) ?: return emptyList()
        return runCatching { am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).toList() }.getOrDefault(emptyList())
    }

    fun outputs(context: Context): List<OutputDevice> {
        revision
        return raw(context).mapNotNull { d ->
            val kind = kindOf(d.type) ?: return@mapNotNull null
            OutputDevice(keyOf(d, kind), kind, d.productName?.toString().orEmpty())
        }.distinctBy { it.key }.sortedBy { listOf(SPEAKER, WIRED, BLUETOOTH, USB, HDMI).indexOf(it.kind) }
    }

    fun resolve(context: Context, key: String = Prefs.audioOutput): AudioDeviceInfo? {
        if (key == AUTO) return null
        val devices = raw(context)
        return devices.firstOrNull { d -> kindOf(d.type)?.let { keyOf(d, it) } == key }
            ?: devices.firstOrNull { d -> kindOf(d.type) == key.substringBefore(':') }
    }

    fun watch(context: Context, onChange: () -> Unit) {
        val am = context.getSystemService(AudioManager::class.java) ?: return
        unwatch(context)
        val cb = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
                revision++
                onChange()
            }

            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
                revision++
                onChange()
            }
        }
        callback = cb
        runCatching { am.registerAudioDeviceCallback(cb, Handler(Looper.getMainLooper())) }
    }

    fun unwatch(context: Context) {
        val cb = callback ?: return
        callback = null
        runCatching { context.getSystemService(AudioManager::class.java)?.unregisterAudioDeviceCallback(cb) }
    }
}
