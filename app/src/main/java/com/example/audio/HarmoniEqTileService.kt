package com.example.audio

import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.example.R
import com.example.data.PresetRepository
import com.example.model.AudioSourceMode
import com.example.model.default20Bands
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Android Quick Settings Tile allowing users to see Harmoni EQ status and toggle
 * Master EQ on/bypass directly from the notification drawer without opening the app.
 */
class HarmoniEqTileService : TileService() {

    private val tileScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onStartListening() {
        super.onStartListening()
        syncTileFromPersistence()
    }

    override fun onTileAdded() {
        super.onTileAdded()
        syncTileFromPersistence()
    }

    override fun onClick() {
        super.onClick()
        tileScope.launch {
            runCatching {
                val repo = PresetRepository(applicationContext)
                val current = repo.settingsFlow.first()
                val nextEnabled = !current.eqEnabled
                repo.saveSettings(current.copy(eqEnabled = nextEnabled))

                // Keep DSP parameters in sync even if the Activity/ViewModel is not active.
                val bands = default20Bands().mapIndexed { idx, band ->
                    band.copy(
                        levelDb = current.leftBandGainsDb.getOrElse(idx) { 0f },
                        rightLevelDb = current.rightBandGainsDb.getOrElse(idx) { 0f }
                    )
                }
                StudioAudioEngine.bindContext(applicationContext)
                StudioAudioEngine.attachEffects(AudioSourceMode.GLOBAL_SYSTEM)
                StudioAudioEngine.updateParameters(
                    eqEnabled = nextEnabled,
                    bands = bands,
                    bassBoostEnabled = current.bassBoostEnabled,
                    bassBoostStrength = current.bassBoostStrength,
                    virtualizerEnabled = current.virtualizerEnabled,
                    virtualizerStrength = current.virtualizerStrength,
                    stereoWidth = current.stereoWidth,
                    stereoBalance = current.stereoBalance,
                    preampEnabled = current.preampEnabled,
                    preampGainDb = current.preampGainDb,
                    limiterEnabled = current.limiterEnabled,
                    reverbPreset = current.reverbPreset
                )

                val presetLabel = (repo.builtInPresets + current.customPresets)
                    .firstOrNull { it.id == current.activePresetId }
                    ?.name
                    ?: "Custom"

                EqPowerBridge.dispatchExternalToggle(applicationContext, nextEnabled)
                EqPowerBridge.publishState(applicationContext, nextEnabled, presetLabel)

                if (AudioForegroundService.isRunning.value || EqualizerService.isRunning.value) {
                    AudioForegroundService.notifyStatusChanged(applicationContext)
                }
                updateTileUi(nextEnabled, presetLabel)
            }
        }
    }

    private fun syncTileFromPersistence() {
        updateTileUi(EqPowerBridge.eqEnabled.value, EqPowerBridge.activePresetName.value)
        tileScope.launch {
            runCatching {
                val repo = PresetRepository(applicationContext)
                val saved = repo.settingsFlow.first()
                val presetLabel = (repo.builtInPresets + saved.customPresets)
                    .firstOrNull { it.id == saved.activePresetId }
                    ?.name
                    ?: "Custom"
                updateTileUi(saved.eqEnabled, presetLabel)
            }
        }
    }

    private fun updateTileUi(enabled: Boolean, presetName: String) {
        val tile = qsTile ?: return
        tile.icon = Icon.createWithResource(this, R.drawable.ic_qs_eq)
        tile.label = getString(R.string.qs_tile_label)
        tile.state = if (enabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = if (enabled) {
                getString(R.string.qs_tile_subtitle_on, presetName)
            } else {
                getString(R.string.qs_tile_subtitle_off)
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            tile.stateDescription = if (enabled) {
                getString(R.string.eq_status_active)
            } else {
                getString(R.string.eq_status_bypass)
            }
        }
        tile.updateTile()
    }

    override fun onDestroy() {
        tileScope.cancel()
        super.onDestroy()
    }
}
