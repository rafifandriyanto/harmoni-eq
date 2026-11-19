package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.model.*
import com.example.ui.EqualizerViewModel
import com.example.ui.components.*
import com.example.ui.theme.*

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EffectsScreen(state: EqualizerUiState, vm: EqualizerViewModel, modifier: Modifier = Modifier) {
    var showScope by remember { mutableStateOf(false) }
    val points by vm.stereoScopePoints.collectAsState()
    val vu by vm.vuLevels.collectAsState()
    fun supports(key: String) = key in state.supportedEffects
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionHeading("Bentuk suara, secukupnya.", "Efek yang tidak tersedia pada jalur ini dinonaktifkan, bukan disimulasikan.")
        if (!state.eqEnabled) Text("Bypass • pengaturan disimpan tanpa diterapkan", color = BrassCaution)
        ConsolePanel {
            SectionHeading("Headroom", "Ruang level sebelum sinyal digital terlalu tinggi.")
            ToggleRow("Preamp", if (supports("preamp")) "Gain input sebelum EQ" else "Tidak didukung oleh jalur ini", state.preampEnabled, vm::togglePreamp, supports("preamp"))
            ValueSlider("Gain input", state.preampGainDb, -12f..12f, dbLabel(state.preampGainDb), "preamp_knob",
                supports("preamp"), vm::updatePreampGain, vm::beginGesture, vm::endGesture, { vm.updatePreampGain(0f) })
            ToggleRow("Kompensasi otomatis", "Estimasi pengurangan gain; bukan jaminan bebas clipping.", state.autoHeadroom,
                vm::toggleAutoHeadroom, supports("preamp") && state.preampEnabled)
            if (state.autoHeadroom) {
                val effective = (state.preampGainDb + state.soundSnapshot().estimatedCompensationDb()).coerceIn(-12f, 12f)
                Text(if (supports("preamp") && state.preampEnabled) "Preamp efektif: ${dbLabel(effective)}" else "Kompensasi tersimpan, belum diterapkan",
                    color = SageSignal, style = MaterialTheme.typography.bodyMedium)
            }
            HorizontalDivider(color = SeamLine)
            ToggleRow("Soft limiter", if (supports("limiter")) "Audio uji: saturasi lunak (soft limiter). Jalur global: limiter DSP bila tersedia. Bukan pelindung speaker atau pendengaran." else "Limiter tidak tersedia pada jalur ini.",
                state.limiterEnabled, vm::toggleLimiter, supports("limiter"), "limiter_toggle")
            Text(if (state.isTestAudioPlaying) {
                if (state.measuredClipping) "Puncak audio uji melewati 0 dBFS sebelum limiter" else "Puncak audio uji: belum melewati batas digital"
            } else "Clipping aplikasi lain: tidak dapat diukur", color = if (state.measuredClipping) MadderAlert else BoneMuted, style = MaterialTheme.typography.bodySmall)
        }
        ConsolePanel {
            SectionHeading("Tonal")
            ToggleRow("Bass boost", if (supports("bass")) "Penguatan bass dari efek Android" else "Tidak tersedia; gunakan band bass pada EQ.", state.bassBoostEnabled,
                vm::toggleBassBoost, supports("bass"))
            ValueSlider("Kekuatan bass", state.bassBoostStrength, 0f..1f, "${(state.bassBoostStrength * 100).toInt()}%", "bass_boost_knob",
                supports("bass"), vm::updateBassBoost, vm::beginGesture, vm::endGesture, { vm.updateBassBoost(0f) })
        }
        ConsolePanel {
            SectionHeading("Stereo", "Lebar dan keseimbangan bukan kontrol volume perangkat.")
            ValueSlider("Lebar stereo", state.stereoWidth, 0f..2f, "${(state.stereoWidth * 100).toInt()}%", "stereo_width_knob",
                supports("width"), vm::updateStereoWidth, vm::beginGesture, vm::endGesture, { vm.updateStereoWidth(1f) })
            if (!supports("width")) Text("Mid/Side hanya diterapkan pada audio uji internal.", color = BoneMuted, style = MaterialTheme.typography.bodySmall)
            ValueSlider("Balance L / R", state.stereoBalance, -1f..1f,
                if (state.stereoBalance == 0f) "Tengah" else "${if (state.stereoBalance < 0f) "L" else "R"} ${kotlin.math.abs(state.stereoBalance * 100).toInt()}%",
                "stereo_balance", supports("stereo"), vm::updateStereoBalance, vm::beginGesture, vm::endGesture, { vm.updateStereoBalance(0f) })
            ToggleRow("Surround", if (supports("surround")) "Virtualizer Android • hasil bergantung perangkat" else "Virtualizer tidak tersedia pada jalur ini",
                state.virtualizerEnabled, vm::toggleVirtualizer, supports("surround"))
            ValueSlider("Kekuatan surround", state.virtualizerStrength, 0f..1f, "${(state.virtualizerStrength * 100).toInt()}%", "surround_knob",
                supports("surround"), vm::updateVirtualizer, vm::beginGesture, vm::endGesture, { vm.updateVirtualizer(0f) })
            TextButton({ showScope = !showScope }) { Text(if (showScope) "Tutup meter" else "Lihat meter audio uji") }
            if (showScope) {
                if (state.isTestAudioPlaying) {
                    Canvas(Modifier.fillMaxWidth().height(90.dp)) {
                        drawLine(SeamLine, Offset(size.width / 2, 0f), Offset(size.width / 2, size.height))
                        drawLine(SeamLine, Offset(0f, size.height / 2), Offset(size.width, size.height / 2))
                        for (i in 0 until points.size / 2) drawCircle(SageSignal, 1.5.dp.toPx(),
                            Offset(size.width / 2 + points[i * 2] * size.width / 2, size.height / 2 - points[i * 2 + 1] * size.height / 2))
                    }
                    Text("RMS internal L ${"%.3f".format(vu.first)} · R ${"%.3f".format(vu.second)}", style = MaterialTheme.typography.bodySmall, color = BoneMuted)
                } else Text("Meter tidak tersedia. Putar audio uji untuk mengukur sinyal internal; audio aplikasi lain tidak ditangkap.", color = BoneMuted)
            }
        }
        ConsolePanel {
            SectionHeading("Ruangan")
            Text("Reverb tidak diterapkan: jalur auxiliary pemutar lain tidak dapat dikendalikan dengan andal. Pilihan lama tetap disimpan.", color = BoneMuted, style = MaterialTheme.typography.bodyMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ReverbRoomPreset.entries.forEach { p -> FilterChip(state.reverbPreset == p, { vm.selectReverbPreset(p) }, { Text(p.label) },
                    Modifier.testTag("reverb_chip_${p.name.lowercase()}"), enabled = p == ReverbRoomPreset.NONE) }
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(vm::undo, enabled = state.canUndo) { Text("Undo") }
            OutlinedButton(vm::redo, enabled = state.canRedo) { Text("Redo") }
            TextButton(vm::resetAllEffects) { Text("Reset efek saja") }
        }
    }
}
