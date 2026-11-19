package com.example.audio

import android.media.*
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.example.model.OutputDevice
import com.example.model.OutputState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.MessageDigest

/** Observes media policy, never changes system volume or switches music to call/SCO mode. */
class AudioOutputMonitor(private val manager: AudioManager) {
    private val _state = MutableStateFlow(OutputState())
    val state = _state.asStateFlow()
    private val callback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) { refresh() }
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) { refresh() }
    }
    init { runCatching { manager.registerAudioDeviceCallback(callback, Handler(Looper.getMainLooper())) }; refresh() }
    fun refresh(ownTrack: AudioTrack? = null) {
        val available = runCatching { manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).map(::device) }.getOrDefault(emptyList())
        val actual = runCatching { ownTrack?.takeIf { it.playState == AudioTrack.PLAYSTATE_PLAYING }?.routedDevice }.getOrNull()
        val anticipated = if (Build.VERSION.SDK_INT >= 33) runCatching {
            manager.getAudioDevicesForAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()).singleOrNull()
        }.getOrNull() else null
        val route = actual ?: anticipated
        _state.value = OutputState(available, route?.let(::device), when {
            actual != null -> "Output audio uji terkonfirmasi"
            anticipated != null -> "Rute media Android (perkiraan, bukan bukti audio aplikasi lain)"
            else -> "Rute tidak tersedia; pilih profil manual, bukan pengalihan output."
        }, runCatching { manager.isMusicActive }.getOrDefault(false))
    }
    fun close() { runCatching { manager.unregisterAudioDeviceCallback(callback) } }
    private fun device(d: AudioDeviceInfo): OutputDevice {
        val kind = when (d.type) {
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLE_HEADSET,
            AudioDeviceInfo.TYPE_BLE_SPEAKER, AudioDeviceInfo.TYPE_HEARING_AID -> "Bluetooth"
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Bluetooth panggilan"
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Kabel"
            AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET -> "USB"
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "Speaker perangkat"
            else -> "Output audio"
        }
        val address = if (Build.VERSION.SDK_INT >= 28) runCatching { d.address }.getOrDefault("") else ""
        val stable = address.isNotBlank() && address != "00:00:00:00:00:00" && address != "02:00:00:00:00:00"
        val builtin = d.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
        val identity = if (stable) "${d.type}:$address" else if (builtin) "builtin-speaker" else "session:${d.id}"
        val key = MessageDigest.getInstance("SHA-256").digest(identity.toByteArray()).joinToString("") { "%02x".format(it) }
        val label = if (builtin) kind else runCatching { d.productName.toString() }.getOrDefault(kind).ifBlank { kind }
        return OutputDevice(key, label, kind, stable || builtin)
    }
}
