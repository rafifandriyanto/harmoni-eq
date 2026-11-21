package com.example.audio

import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.service.quicksettings.TileService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Lightweight reactive bridge keeping the app ViewModel, foreground EqualizerService,
 * and Quick Settings Tile synchronized on the master EQ enabled state without altering DSP logic.
 */
object EqPowerBridge {
    private val _eqEnabled = MutableStateFlow(true)
    val eqEnabled: StateFlow<Boolean> = _eqEnabled.asStateFlow()

    private val _activePresetName = MutableStateFlow("Harmoni Studio")
    val activePresetName: StateFlow<String> = _activePresetName.asStateFlow()

    @Volatile
    private var externalToggleListener: ((Boolean) -> Unit)? = null

    fun registerListener(listener: (Boolean) -> Unit) {
        externalToggleListener = listener
    }

    fun unregisterListener(listener: (Boolean) -> Unit) {
        if (externalToggleListener === listener) {
            externalToggleListener = null
        }
    }

    fun publishState(context: Context, enabled: Boolean, presetName: String? = null) {
        _eqEnabled.value = enabled
        if (!presetName.isNullOrBlank()) {
            _activePresetName.value = presetName
        }
        requestTileSync(context)
    }

    fun dispatchExternalToggle(context: Context, newEnabled: Boolean) {
        _eqEnabled.value = newEnabled
        val listener = externalToggleListener
        if (listener != null) {
            listener(newEnabled)
        }
        requestTileSync(context)
    }

    fun requestTileSync(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            runCatching {
                TileService.requestListeningState(
                    context.applicationContext,
                    ComponentName(context.applicationContext, HarmoniEqTileService::class.java)
                )
            }
        }
    }
}
