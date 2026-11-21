package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BluetoothAudio
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.model.AudioSourceMode
import com.example.model.EqualizerUiState
import com.example.model.StereoChannelFocus
import com.example.ui.EqualizerViewModel
import com.example.ui.components.ConsolePanel
import com.example.ui.components.FrequencyCurveCanvas
import com.example.ui.components.ProEqFader
import com.example.ui.components.ValueSlider
import com.example.ui.components.dbLabel
import com.example.ui.components.glass.GlassAbToolbar
import com.example.ui.components.glass.GlassPrimaryPillButton
import com.example.ui.theme.GlassTokens
import com.example.ui.theme.LocalHarmoniPalette
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EqualizerScreen(
    state: EqualizerUiState,
    vm: EqualizerViewModel,
    onPresets: () -> Unit,
    onYouTube: () -> Unit,
    onDevices: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
    onInteractionChange: (Boolean) -> Unit = {},
    onScrolledUnderChange: (Boolean) -> Unit = {}
) {
    val palette = LocalHarmoniPalette.current
    var showCurve by remember { mutableStateOf(false) }
    var showTools by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    val faders = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val activePreset = state.presets.find { it.id == state.activePresetId }
    // Hanya chip bank yang recompose saat grup berpindah, bukan seluruh layar.
    val faderBankGroup by remember { derivedStateOf { faders.firstVisibleItemIndex / 5 } }

    // snapshotFlow: baca offset scroll di luar komposisi agar tidak recompose per piksel.
    LaunchedEffect(scrollState, faders) {
        launch {
            snapshotFlow { scrollState.value > 8 }
                .distinctUntilChanged()
                .collect { onScrolledUnderChange(it) }
        }
        launch {
            snapshotFlow { scrollState.isScrollInProgress || faders.isScrollInProgress }
                .distinctUntilChanged()
                .collect { onInteractionChange(it) }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(GlassTokens.Space16),
        verticalArrangement = Arrangement.spacedBy(GlassTokens.Space16)
    ) {
        ConsolePanel(Modifier.testTag("output_status")) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(GlassTokens.Space12)
            ) {
                Icon(
                    if (state.output.route?.kind == "Bluetooth") Icons.Rounded.BluetoothAudio else Icons.Rounded.Speaker,
                    null,
                    tint = palette.accent,
                    modifier = Modifier.size(24.dp)
                )
                Column(Modifier.weight(1f)) {
                    Text("Output audio", color = palette.textMuted, style = MaterialTheme.typography.labelMedium)
                    Text(state.detectedOutputDevice, style = MaterialTheme.typography.titleMedium, color = palette.textPrimary)
                }
                TextButton(onClick = onDevices, modifier = Modifier.testTag("output_manage")) { Text("Kelola") }
            }
            Text(
                when {
                    !state.eqEnabled -> "Bypass aktif. Perubahan tetap bisa disimpan."
                    !state.hardwareAttached -> "Menunggu efek yang kompatibel"
                    state.audioSourceMode == AudioSourceMode.STUDIO_TEST -> "DSP audio uji tersedia, 20 band stereo"
                    else -> "Efek terpasang. Cek perbedaannya dengan Bypass."
                },
                color = if (state.hardwareAttached && state.eqEnabled) palette.statusSuccess else palette.statusWarning,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        val presetHeader: @Composable () -> Unit = {
            Column {
                Text("Karakter suara", style = MaterialTheme.typography.labelMedium, color = palette.textMuted)
                Text(activePreset?.name ?: "Kurva kustom", style = MaterialTheme.typography.titleLarge, color = palette.textPrimary)
                if (state.presetDirty) {
                    Text("Diubah, belum disimpan sebagai preset", color = palette.statusWarning, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        val presetButton: @Composable () -> Unit = {
            Row(
                Modifier
                    .clickable(onClick = onPresets)
                    .defaultMinSize(minHeight = 48.dp)
                    .padding(horizontal = GlassTokens.Space8)
                    .semantics { role = Role.Button },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Preset",
                    maxLines = 1,
                    softWrap = false,
                    style = MaterialTheme.typography.labelLarge,
                    color = palette.accent
                )
            }
        }

        if (LocalDensity.current.fontScale >= 1.5f) {
            Column(Modifier.fillMaxWidth()) {
                presetHeader()
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { presetButton() }
            }
        } else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { presetHeader() }
                presetButton()
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(GlassTokens.Space8)) {
            FilterChip(
                selected = state.simpleMode,
                onClick = { vm.setSimpleMode(true) },
                label = { Text("Simple") },
                modifier = Modifier.weight(1f).testTag("mode_simple")
            )
            FilterChip(
                selected = !state.simpleMode,
                onClick = { vm.setSimpleMode(false) },
                label = { Text("Advanced 20") },
                modifier = Modifier.weight(1f).testTag("mode_advanced")
            )
        }

        if (state.simpleMode) {
            ConsolePanel {
                val groups = listOf(0..6, 7..13, 14..19)
                val custom = groups.any { range ->
                    state.bands.slice(range).flatMap { listOf(it.levelDb, it.rightLevelDb) }.distinct().size > 1
                }
                if (custom) {
                    Text(
                        "Kurva kustom. Menggeser satu kontrol meratakan kelompok frekuensinya pada L/R. Gunakan Undo untuk kembali.",
                        color = palette.statusWarning,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                listOf("Bass", "Mid", "Treble").forEachIndexed { i, label ->
                    val value = state.bands.slice(groups[i]).map { (it.levelDb + it.rightLevelDb) / 2 }.average().toFloat()
                    ValueSlider(
                        title = label,
                        value = value,
                        range = -15f..15f,
                        label = dbLabel(value),
                        tag = "simple_${label.lowercase()}",
                        onChange = { vm.updateSimpleTone(i, it) },
                        onStart = { onInteractionChange(true); vm.beginGesture() },
                        onFinish = { vm.endGesture(); onInteractionChange(false) }
                    )
                }
            }
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(GlassTokens.Space8)) {
                StereoChannelFocus.entries.forEach { focus ->
                    FilterChip(
                        selected = state.activeChannelFocus == focus,
                        onClick = { vm.selectChannelFocus(focus) },
                        label = { Text(focus.shortLabel) },
                        modifier = Modifier.testTag("stereo_channel_${focus.name.lowercase()}")
                    )
                }
                Text(
                    "20 kontrol -> ${state.hardwareBandCount.takeIf { it > 0 } ?: "?"} band",
                    modifier = Modifier.padding(top = GlassTokens.Space16),
                    color = palette.textMuted,
                    style = MaterialTheme.typography.labelMedium
                )
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(GlassTokens.Space8)) {
                items(4) { group ->
                    val labels = listOf("Sub / Bass", "Low / Mid", "Presence", "Air / High")
                    FilterChip(
                        selected = faderBankGroup == group,
                        onClick = { scope.launch { faders.animateScrollToItem(group * 5) } },
                        label = { Text(labels[group]) },
                        modifier = Modifier.testTag("bank_jump_${group * 5}")
                    )
                }
            }
            LazyRow(
                state = faders,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(232.dp)
                    .testTag("eq_fader_bank"),
                horizontalArrangement = Arrangement.spacedBy(GlassTokens.Space8)
            ) {
                itemsIndexed(state.bands, key = { _, band -> band.index }) { _, band ->
                    ProEqFader(
                        band = band,
                        enabled = true,
                        stereoLinked = state.stereoLinked,
                        channelFocus = state.activeChannelFocus,
                        onValueChange = { vm.updateBandLevel(band.index, it) },
                        onDoubleTapReset = { vm.resetBandToZero(band.index) },
                        modifier = Modifier.fillMaxHeight(),
                        onGestureStart = { onInteractionChange(true); vm.beginGesture() },
                        onGestureEnd = { vm.endGesture(); onInteractionChange(false) }
                    )
                }
            }
            Text(
                "Ketuk angka untuk nilai presisi, ketuk fader 2x untuk 0 dB",
                color = palette.textMuted,
                style = MaterialTheme.typography.bodySmall
            )
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(GlassTokens.Space8),
            verticalArrangement = Arrangement.spacedBy(GlassTokens.Space8)
        ) {
            OutlinedButton(onClick = vm::undo, enabled = state.canUndo, modifier = Modifier.testTag("undo")) { Text("Undo") }
            OutlinedButton(onClick = vm::redo, enabled = state.canRedo, modifier = Modifier.testTag("redo")) { Text("Redo") }
            GlassPrimaryPillButton(text = "Simpan suara", onClick = onSave)
        }

        TextButton(onClick = { showCurve = !showCurve }, modifier = Modifier.testTag("show_curve")) {
            Text(if (showCurve) "Sembunyikan kurva" else "Tampilkan kurva EQ")
        }
        if (showCurve) {
            FrequencyCurveCanvas(
                bands = state.bands,
                eqEnabled = state.eqEnabled,
                stereoLinked = state.stereoLinked,
                activeChannelFocus = state.activeChannelFocus,
                spectrumBars = FloatArray(32),
                onSelectChannelFocus = vm::selectChannelFocus,
                onBandLevelChange = vm::updateBandLevel,
                onGestureStart = { onInteractionChange(true); vm.beginGesture() },
                onGestureEnd = { vm.endGesture(); onInteractionChange(false) }
            )
        }

        TextButton(onClick = { showTools = !showTools }, modifier = Modifier.testTag("show_tools")) {
            Text(if (showTools) "Tutup alat lanjutan" else "A/B, reset & alat lanjutan")
        }
        if (showTools) {
            GlassAbToolbar(
                activeSlot = state.activeAbSlot,
                onToggleSlot = vm::toggleABSlot,
                onCopyToOther = vm::copyToOtherSlot
            )
            ConsolePanel {
                Text(
                    "A/B menyimpan EQ dan efek, bukan master power. Tidak ada loudness matching.",
                    color = palette.textMuted,
                    style = MaterialTheme.typography.bodySmall
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(GlassTokens.Space8)) {
                    TextButton(vm::smoothCurve, Modifier.testTag("smooth_curve_button")) { Text("Haluskan") }
                    TextButton(vm::mirrorLeftToRight, Modifier.testTag("mirror_lr_button")) { Text("Salin L ke R") }
                    TextButton(vm::resetAllFlat) { Text("Reset EQ saja") }
                }
            }
        }

        ConsolePanel {
            Text("Sumber & uji dengar", color = palette.textMuted, style = MaterialTheme.typography.labelMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(GlassTokens.Space8)) {
                AudioSourceMode.entries.forEach { mode ->
                    FilterChip(
                        selected = state.audioSourceMode == mode,
                        onClick = { vm.switchAudioSourceMode(mode) },
                        label = { Text(if (mode == AudioSourceMode.GLOBAL_SYSTEM) "Aplikasi lain" else "Audio uji") }
                    )
                }
            }
            if (state.audioSourceMode == AudioSourceMode.STUDIO_TEST) {
                Text(
                    "Mulai dengan volume nyaman. Meter hanya mengukur sinyal uji internal.",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.textMuted
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(GlassTokens.Space8)) {
                    com.example.ui.components.glass.GlassPlayPauseButton(
                        isPlaying = state.isTestAudioPlaying,
                        onClick = vm::toggleTestAudioPlayback,
                        modifier = Modifier.testTag("test_audio_play_button")
                    )
                    TextButton(vm::cycleTestTrackStyle) { Text(state.testTrackStyle.label) }
                }
            } else {
                Text(
                    "Efek aplikasi lain bergantung pada sesi pemutar, Android, dan perangkat.",
                    color = palette.textMuted,
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedButton(onYouTube, Modifier.testTag("launch_youtube_button")) { Text("Buka YouTube") }
            }
        }
    }
}
