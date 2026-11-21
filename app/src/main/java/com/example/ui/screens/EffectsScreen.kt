package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.EqualizerUiState
import com.example.model.ReverbRoomPreset
import com.example.model.estimatedCompensationDb
import com.example.model.soundSnapshot
import com.example.ui.EqualizerViewModel
import com.example.ui.components.ConsolePanel
import com.example.ui.components.SectionHeading
import com.example.ui.components.ToggleRow
import com.example.ui.components.ValueSlider
import com.example.ui.components.dbLabel
import com.example.ui.theme.GlassTokens
import com.example.ui.theme.LocalHarmoniPalette

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EffectsScreen(
    state: EqualizerUiState,
    vm: EqualizerViewModel,
    modifier: Modifier = Modifier,
    onScrolledUnderChange: (Boolean) -> Unit = {}
) {
    val palette = LocalHarmoniPalette.current
    val scrollState = rememberScrollState()
    var showScope by remember { mutableStateOf(false) }
    val points by vm.stereoScopePoints.collectAsStateWithLifecycle()
    val vu by vm.vuLevels.collectAsStateWithLifecycle()

    LaunchedEffect(scrollState.value) {
        onScrolledUnderChange(scrollState.value > 8)
    }

    fun supports(key: String) = key in state.supportedEffects

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(GlassTokens.Space16),
        verticalArrangement = Arrangement.spacedBy(GlassTokens.Space16)
    ) {
        SectionHeading(
            "Bentuk suara secukupnya",
            "Efek yang tidak tersedia pada jalur ini dinonaktifkan, bukan disimulasikan."
        )
        if (!state.eqEnabled) {
            Text("Bypass aktif. Pengaturan disimpan tanpa diterapkan.", color = palette.statusWarning)
        }

        ConsolePanel {
            SectionHeading("Headroom", "Ruang level sebelum sinyal digital terlalu tinggi.")
            ToggleRow(
                "Preamp",
                if (supports("preamp")) "Gain input sebelum EQ" else "Tidak didukung oleh jalur ini",
                state.preampEnabled,
                vm::togglePreamp,
                supports("preamp")
            )
            ValueSlider(
                "Gain input",
                state.preampGainDb,
                -12f..12f,
                dbLabel(state.preampGainDb),
                "preamp_knob",
                supports("preamp"),
                vm::updatePreampGain,
                vm::beginGesture,
                vm::endGesture,
                { vm.updatePreampGain(0f) }
            )
            ToggleRow(
                "Kompensasi otomatis",
                "Estimasi pengurangan gain; bukan jaminan bebas clipping.",
                state.autoHeadroom,
                vm::toggleAutoHeadroom,
                supports("preamp") && state.preampEnabled
            )
            if (state.autoHeadroom) {
                val effective = (state.preampGainDb + state.soundSnapshot().estimatedCompensationDb()).coerceIn(-12f, 12f)
                Text(
                    if (supports("preamp") && state.preampEnabled) "Preamp efektif: ${dbLabel(effective)}"
                    else "Kompensasi tersimpan, belum diterapkan",
                    color = palette.accent,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            HorizontalDivider(color = palette.borderSubtle)
            ToggleRow(
                "Limiter",
                if (supports("limiter")) "Membatasi puncak digital; bukan pelindung speaker atau pendengaran."
                else "Limiter tidak tersedia pada jalur ini.",
                state.limiterEnabled,
                vm::toggleLimiter,
                supports("limiter"),
                "limiter_toggle"
            )
            Text(
                if (state.isTestAudioPlaying) {
                    if (state.measuredClipping) "Puncak audio uji melewati 0 dBFS sebelum limiter"
                    else "Puncak audio uji belum melewati batas digital"
                } else "Clipping aplikasi lain tidak dapat diukur",
                color = if (state.measuredClipping) palette.statusDanger else palette.textMuted,
                style = MaterialTheme.typography.bodySmall
            )
        }

        ConsolePanel {
            SectionHeading("Tonal")
            ToggleRow(
                "Bass boost",
                if (supports("bass")) "Penguatan bass dari efek Android" else "Tidak tersedia; gunakan band bass pada EQ.",
                state.bassBoostEnabled,
                vm::toggleBassBoost,
                supports("bass")
            )
            ValueSlider(
                "Kekuatan bass",
                state.bassBoostStrength,
                0f..1f,
                "${(state.bassBoostStrength * 100).toInt()}%",
                "bass_boost_knob",
                supports("bass"),
                vm::updateBassBoost,
                vm::beginGesture,
                vm::endGesture,
                { vm.updateBassBoost(0f) }
            )
        }

        ConsolePanel {
            SectionHeading("Stereo", "Lebar dan keseimbangan bukan kontrol volume perangkat.")
            ValueSlider(
                "Lebar stereo",
                state.stereoWidth,
                0f..2f,
                "${(state.stereoWidth * 100).toInt()}%",
                "stereo_width_knob",
                supports("width"),
                vm::updateStereoWidth,
                vm::beginGesture,
                vm::endGesture,
                { vm.updateStereoWidth(1f) }
            )
            if (!supports("width")) {
                Text("Mid/Side hanya diterapkan pada audio uji internal.", color = palette.textMuted, style = MaterialTheme.typography.bodySmall)
            }
            ValueSlider(
                "Balance L / R",
                state.stereoBalance,
                -1f..1f,
                if (state.stereoBalance == 0f) "Tengah"
                else "${if (state.stereoBalance < 0f) "L" else "R"} ${kotlin.math.abs(state.stereoBalance * 100).toInt()}%",
                "stereo_balance",
                supports("stereo"),
                vm::updateStereoBalance,
                vm::beginGesture,
                vm::endGesture,
                { vm.updateStereoBalance(0f) }
            )
            ToggleRow(
                "Surround",
                if (supports("surround")) "Virtualizer Android, hasil bergantung perangkat"
                else "Virtualizer tidak tersedia pada jalur ini",
                state.virtualizerEnabled,
                vm::toggleVirtualizer,
                supports("surround")
            )
            ValueSlider(
                "Kekuatan surround",
                state.virtualizerStrength,
                0f..1f,
                "${(state.virtualizerStrength * 100).toInt()}%",
                "surround_knob",
                supports("surround"),
                vm::updateVirtualizer,
                vm::beginGesture,
                vm::endGesture,
                { vm.updateVirtualizer(0f) }
            )
            TextButton({ showScope = !showScope }) {
                Text(if (showScope) "Tutup meter" else "Lihat meter audio uji")
            }
            if (showScope) {
                if (state.isTestAudioPlaying) {
                    Canvas(Modifier.fillMaxWidth().height(92.dp)) {
                        drawLine(palette.border, Offset(size.width / 2, 0f), Offset(size.width / 2, size.height))
                        drawLine(palette.border, Offset(0f, size.height / 2), Offset(size.width, size.height / 2))
                        for (i in 0 until points.size / 2) {
                            drawCircle(
                                palette.accent,
                                1.5.dp.toPx(),
                                Offset(size.width / 2 + points[i * 2] * size.width / 2, size.height / 2 - points[i * 2 + 1] * size.height / 2)
                            )
                        }
                    }
                    Text(
                        "RMS internal L ${"%.3f".format(vu.first)} / R ${"%.3f".format(vu.second)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = palette.textMuted
                    )
                } else {
                    Text(
                        "Meter tidak tersedia. Putar audio uji untuk mengukur sinyal internal.",
                        color = palette.textMuted,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        ConsolePanel {
            SectionHeading("Ruangan")
            Text(
                "Reverb tidak diterapkan karena jalur auxiliary pemutar lain tidak dapat dikendalikan dengan andal.",
                color = palette.textMuted,
                style = MaterialTheme.typography.bodyMedium
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(GlassTokens.Space8)) {
                ReverbRoomPreset.entries.forEach { p ->
                    FilterChip(
                        selected = state.reverbPreset == p,
                        onClick = { vm.selectReverbPreset(p) },
                        label = { Text(p.label) },
                        modifier = Modifier.testTag("reverb_chip_${p.name.lowercase()}"),
                        enabled = p == ReverbRoomPreset.NONE
                    )
                }
            }
        }

        FlowRow(horizontalArrangement = Arrangement.spacedBy(GlassTokens.Space8)) {
            OutlinedButton(vm::undo, enabled = state.canUndo) { Text("Undo") }
            OutlinedButton(vm::redo, enabled = state.canRedo) { Text("Redo") }
            TextButton(vm::resetAllEffects) { Text("Reset efek saja") }
        }
    }
}
