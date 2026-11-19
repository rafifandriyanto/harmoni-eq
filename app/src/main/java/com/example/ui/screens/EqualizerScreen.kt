package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.role
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.model.*
import com.example.ui.EqualizerViewModel
import com.example.ui.components.*
import com.example.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EqualizerScreen(state: EqualizerUiState, vm: EqualizerViewModel, onPresets: () -> Unit,
    onYouTube: () -> Unit, onDevices: () -> Unit, onSave: () -> Unit, modifier: Modifier = Modifier) {
    var showCurve by remember { mutableStateOf(false) }
    var showTools by remember { mutableStateOf(false) }
    val faders = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val activePreset = state.presets.find { it.id == state.activePresetId }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ConsolePanel(Modifier.testTag("output_status")) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(if (state.output.route?.kind == "Bluetooth") Icons.Rounded.BluetoothAudio else Icons.Rounded.Speaker,
                    null, tint = SageSignal, modifier = Modifier.size(28.dp))
                Column(Modifier.weight(1f)) {
                    Text("OUTPUT AUDIO", color = BoneMuted, style = MaterialTheme.typography.labelMedium)
                    Text(state.detectedOutputDevice, style = MaterialTheme.typography.titleMedium)
                }
                TextButton(onClick = onDevices, modifier = Modifier.testTag("output_manage")) { Text("Kelola") }
            }
            Text(when {
                !state.eqEnabled -> "Bypass • perubahan tetap bisa disimpan"
                !state.hardwareAttached -> "Menunggu efek yang kompatibel"
                state.audioSourceMode == AudioSourceMode.STUDIO_TEST -> "DSP audio uji tersedia • 20 band stereo"
                else -> "Efek terpasang • cek perbedaannya dengan Bypass"
            }, color = if (state.hardwareAttached && state.eqEnabled) SageSignal else BrassCaution, style = MaterialTheme.typography.bodyMedium)
        }
        val presetHeader: @Composable () -> Unit = {
            Column {
                Text("KARAKTER SUARA", style = MaterialTheme.typography.labelMedium, color = BoneMuted)
                Text(activePreset?.name ?: "Kurva kustom", style = MaterialTheme.typography.titleLarge)
                if (state.presetDirty) Text("Diubah • belum disimpan sebagai preset", color = BrassCaution, style = MaterialTheme.typography.bodySmall)
            }
        }
        val presetButton: @Composable () -> Unit = {
            Row(Modifier.clickable(onClick = onPresets).defaultMinSize(minHeight = 48.dp)
                .padding(horizontal = 8.dp).semantics { role = androidx.compose.ui.semantics.Role.Button },
                verticalAlignment = Alignment.CenterVertically) {
                Text("Preset", maxLines = 1, softWrap = false, style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary)
            }
        }
        if (androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.5f) {
            Column(Modifier.fillMaxWidth()) {
                presetHeader()
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { presetButton() }
            }
        } else Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { presetHeader() }
            presetButton()
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(state.simpleMode, { vm.setSimpleMode(true) }, { Text("Simple") }, Modifier.weight(1f).testTag("mode_simple"))
            FilterChip(!state.simpleMode, { vm.setSimpleMode(false) }, { Text("Advanced · 20") }, Modifier.weight(1f).testTag("mode_advanced"))
        }
        if (state.simpleMode) {
            ConsolePanel {
                val groups = listOf(0..6, 7..13, 14..19)
                val custom = groups.any { range -> state.bands.slice(range).flatMap { listOf(it.levelDb, it.rightLevelDb) }.distinct().size > 1 }
                if (custom) Text("Kurva kustom. Menggeser satu kontrol meratakan kelompok frekuensinya pada L/R. Gunakan Undo untuk kembali.",
                    color = BrassCaution, style = MaterialTheme.typography.bodyMedium)
                listOf("Bass", "Mid", "Treble").forEachIndexed { i, label ->
                    val value = state.bands.slice(groups[i]).map { (it.levelDb + it.rightLevelDb) / 2 }.average().toFloat()
                    ValueSlider(label, value, -15f..15f, dbLabel(value), "simple_${label.lowercase()}",
                        onChange = { vm.updateSimpleTone(i, it) }, onStart = vm::beginGesture, onFinish = vm::endGesture)
                }
            }
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StereoChannelFocus.entries.forEach { focus ->
                    FilterChip(state.activeChannelFocus == focus, { vm.selectChannelFocus(focus) }, { Text(focus.shortLabel) },
                        Modifier.testTag("stereo_channel_${focus.name.lowercase()}"))
                }
                Text("20 kontrol → ${state.hardwareBandCount.takeIf { it > 0 } ?: "?"} band", Modifier.padding(top = 16.dp),
                    color = BoneMuted, style = MaterialTheme.typography.labelMedium)
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(4) { group ->
                    val labels = listOf("Sub / Bass", "Low / Mid", "Presence", "Air / High")
                    FilterChip(faders.firstVisibleItemIndex / 5 == group,
                        { scope.launch { faders.animateScrollToItem(group * 5) } }, { Text(labels[group]) },
                        Modifier.testTag("bank_jump_${group * 5}"))
                }
            }
            LazyRow(state = faders, modifier = Modifier.fillMaxWidth().height(230.dp).testTag("eq_fader_bank"),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(state.bands, key = { _, band -> band.index }) { _, band ->
                    ProEqFader(band, true, state.stereoLinked, state.activeChannelFocus,
                        { vm.updateBandLevel(band.index, it) }, { vm.resetBandToZero(band.index) },
                        Modifier.fillMaxHeight(), vm::beginGesture, vm::endGesture)
                }
            }
            Text("Ketuk angka untuk nilai presisi · ketuk fader 2× untuk 0 dB", color = BoneMuted, style = MaterialTheme.typography.bodySmall)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = vm::undo, enabled = state.canUndo, modifier = Modifier.testTag("undo")) { Text("Undo") }
            OutlinedButton(onClick = vm::redo, enabled = state.canRedo, modifier = Modifier.testTag("redo")) { Text("Redo") }
            Button(onClick = onSave) { Text("Simpan suara") }
        }
        TextButton(onClick = { showCurve = !showCurve }, modifier = Modifier.testTag("show_curve")) { Text(if (showCurve) "Sembunyikan kurva" else "Tampilkan kurva EQ") }
        if (showCurve) FrequencyCurveCanvas(state.bands, state.eqEnabled, state.stereoLinked, state.activeChannelFocus,
            vm::selectChannelFocus, vm::updateBandLevel, onGestureStart = vm::beginGesture, onGestureEnd = vm::endGesture)
        TextButton(onClick = { showTools = !showTools }, modifier = Modifier.testTag("show_tools")) { Text(if (showTools) "Tutup alat lanjutan" else "A/B, reset & alat lanjutan") }
        if (showTools) ConsolePanel {
            Text("Bandingkan seluruh suara • slot ${state.activeAbSlot}", style = MaterialTheme.typography.titleSmall)
            Text("A/B menyimpan EQ dan efek, bukan master power. Slot baru dimulai dari suara yang sama; tidak ada loudness matching.", color = BoneMuted, style = MaterialTheme.typography.bodySmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(vm::toggleABSlot, Modifier.testTag("ab_compare_button")) { Text("Dengar ${if (state.activeAbSlot == "A") "B" else "A"}") }
                TextButton(vm::copyToOtherSlot) { Text("Salin ${state.activeAbSlot} → ${if (state.activeAbSlot == "A") "B" else "A"}") }
                TextButton(vm::smoothCurve, Modifier.testTag("smooth_curve_button")) { Text("Haluskan") }
                TextButton(vm::mirrorLeftToRight, Modifier.testTag("mirror_lr_button")) { Text("Salin L → R") }
                TextButton(vm::resetAllFlat) { Text("Reset EQ saja") }
            }
            Text("Menautkan L+R menyalin kiri ke kanan. Semua perubahan suara dapat di-Undo.", color = BoneMuted, style = MaterialTheme.typography.bodySmall)
        }
        ConsolePanel {
            Text("SUMBER & UJI DENGAR", color = BoneMuted, style = MaterialTheme.typography.labelMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AudioSourceMode.entries.forEach { mode ->
                    FilterChip(state.audioSourceMode == mode, { vm.switchAudioSourceMode(mode) },
                        { Text(if (mode == AudioSourceMode.GLOBAL_SYSTEM) "Aplikasi lain" else "Audio uji") })
                }
            }
            if (state.audioSourceMode == AudioSourceMode.STUDIO_TEST) {
                Text("Mulai dengan volume nyaman. Meter hanya mengukur sinyal uji internal.", style = MaterialTheme.typography.bodySmall, color = BoneMuted)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(vm::toggleTestAudioPlayback) { Text(if (state.isTestAudioPlaying) "Hentikan" else "Putar audio uji") }
                    TextButton(vm::cycleTestTrackStyle) { Text(state.testTrackStyle.label) }
                }
            } else {
                Text("Efek aplikasi lain bergantung pada sesi pemutar, Android, dan perangkat. Tidak semua pemutar mendukungnya.", color = BoneMuted, style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onYouTube, Modifier.testTag("launch_youtube_button")) { Text("Buka YouTube") }
            }
        }
    }
}
