package com.example

import android.app.Application
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.example.model.StereoChannelFocus
import com.example.model.ReverbRoomPreset
import com.example.ui.EqualizerViewModel
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SoundStateRegressionTest {
    private val store = ViewModelStore()
    private fun model(): EqualizerViewModel = EqualizerViewModel(
        ApplicationProvider.getApplicationContext<Application>()
    ).also { store.put("eq", it) }

    @After fun cleanup() { store.clear() }

    @Test fun `selecting a preset preserves master bypass`() {
        val vm = model()
        vm.toggleMasterPower()
        assertFalse(vm.uiState.value.eqEnabled)
        vm.selectPreset(vm.uiState.value.presets.first { it.id == "flat" })
        assertFalse("Preset must not turn processing back on", vm.uiState.value.eqEnabled)
    }

    @Test fun `custom preset restores independent channels and all effect settings`() {
        val vm = model()
        vm.selectChannelFocus(StereoChannelFocus.LEFT)
        vm.updateBandLevel(0, 4f)
        vm.selectChannelFocus(StereoChannelFocus.RIGHT)
        vm.updateBandLevel(0, -3f)
        vm.updateStereoBalance(-0.4f)
        vm.updatePreampGain(-5f)
        vm.toggleLimiter()
        vm.selectReverbPreset(ReverbRoomPreset.PLATE)
        vm.saveCustomPreset("Independent stereo")
        val saved = vm.uiState.value.presets.last()
        vm.resetAllFlat()
        vm.updateStereoBalance(0f)
        vm.updatePreampGain(0f)
        vm.selectReverbPreset(ReverbRoomPreset.NONE)
        vm.selectPreset(saved)
        with(vm.uiState.value) {
            assertEquals(4f, bands[0].levelDb, 0f)
            assertEquals(-3f, bands[0].rightLevelDb, 0f)
            assertFalse(stereoLinked)
            assertEquals(-0.4f, stereoBalance, 0f)
            assertEquals(-5f, preampGainDb, 0f)
            assertFalse(limiterEnabled)
            assertEquals(ReverbRoomPreset.PLATE, reverbPreset)
        }
    }

    @Test fun `one gesture creates one undo step and redo restores it`() {
        val vm = model()
        vm.selectPreset(vm.uiState.value.presets.first { it.id == "flat" })
        vm.beginGesture()
        vm.updateBandLevel(0, 1f)
        vm.updateBandLevel(0, 2f)
        vm.updateBandLevel(0, 3f)
        vm.endGesture()
        vm.undo()
        assertEquals(0f, vm.uiState.value.bands[0].levelDb, 0f)
        vm.redo()
        assertEquals(3f, vm.uiState.value.bands[0].levelDb, 0f)
    }

    @Test fun `reset EQ preserves effects and reset effects is undoable`() {
        val vm = model()
        vm.updatePreampGain(-4f)
        vm.updateBandLevel(0, 5f)
        vm.resetAllFlat()
        assertEquals(-4f, vm.uiState.value.preampGainDb, 0f)
        assertEquals(0f, vm.uiState.value.bands[0].levelDb, 0f)
        vm.resetAllEffects()
        assertEquals(0f, vm.uiState.value.preampGainDb, 0f)
        vm.undo()
        assertEquals(-4f, vm.uiState.value.preampGainDb, 0f)
    }

    @Test fun `invalid import leaves preset library untouched`() {
        val vm = model()
        val before = vm.uiState.value.presets
        assertTrue(vm.importPresets("{broken}", false).isFailure)
        assertEquals(before, vm.uiState.value.presets)
    }

    @Test fun `switching simple mode does not modify advanced curve`() {
        val vm = model()
        vm.updateBandLevel(4, 7.3f)
        val before = vm.uiState.value.bands
        vm.setSimpleMode(true)
        assertEquals(before, vm.uiState.value.bands)
        vm.setSimpleMode(false)
        assertEquals(before, vm.uiState.value.bands)
    }

    @Test fun `saving cannot grow library beyond importable limit`() {
        val vm = model()
        repeat(201) { vm.saveCustomPreset("Preset $it") }
        assertEquals(200, vm.uiState.value.presets.count { it.isCustom })
    }

    @Test fun `manual output profile is undoable and preserves bypass`() {
        val vm = model()
        vm.toggleMasterPower()
        vm.updateBandLevel(0, -4f)
        vm.bindOutputProfile(com.example.model.OutputDevice("manual:tws", "TWS", "Manual", false), "flat")
        vm.applyOutputProfile("manual:tws")
        assertFalse(vm.uiState.value.eqEnabled)
        assertEquals(0f, vm.uiState.value.bands[0].levelDb, 0f)
        vm.undo()
        assertEquals(-4f, vm.uiState.value.bands[0].levelDb, 0f)
    }

    @Test fun `AB restores effects as well as EQ`() {
        val vm = model()
        vm.updatePreampGain(-6f)
        vm.updateStereoBalance(-0.6f)
        vm.toggleABSlot()
        vm.updatePreampGain(-2f)
        vm.updateStereoBalance(0.2f)
        vm.toggleABSlot()
        assertEquals(-6f, vm.uiState.value.preampGainDb, 0f)
        assertEquals(-0.6f, vm.uiState.value.stereoBalance, 0f)
    }
}
