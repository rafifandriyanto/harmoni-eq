package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.*
import com.example.ui.EqualizerViewModel
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun OutputSettingsSheet(state: EqualizerUiState, vm: EqualizerViewModel, onDismiss: () -> Unit,
    onBluetoothSettings: () -> Unit, onService: () -> Unit) {
    var manualName by rememberSaveable { mutableStateOf("") }
    // Simpan key (String), bukan objek OutputDevice: survive process death dan
    // daftar perangkat yang berubah; derive ulang dari state saat ini.
    var pickingKey by rememberSaveable { mutableStateOf<String?>(null) }
    val pickingFor = state.output.available.find { it.key == pickingKey }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            SectionHeading("Output & kompatibilitas", "Pasangkan TWS, headphone, atau speaker lewat Android. Profil di sini mengubah EQ, bukan rute audio.")
            OutlinedButton(onBluetoothSettings) { Text("Buka pengaturan Bluetooth") }
            Text(state.output.routeDescription, color = BrassCaution)
            Text(state.hardwareStatusMessage, style = MaterialTheme.typography.bodyMedium)
            Text("Sesi terdeteksi: ${state.detectedSessions.joinToString().ifBlank { "belum ada" }}", color = BoneMuted)
            ToggleRow("Layanan latar belakang", "Layanan berjalan tidak berarti semua aplikasi bisa diproses.", state.backgroundServiceActive, onService)
            ConsolePanel {
                SectionHeading("Kemampuan jalur saat ini")
                listOf("eq" to "Equalizer", "stereo" to "Kanal L/R & balance", "preamp" to "Preamp",
                    "limiter" to "Limiter", "bass" to "Bass boost", "surround" to "Surround", "width" to "Mid/Side", "reverb" to "Reverb").forEach { (key, label) ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        Text(if (key in state.supportedEffects) "Tersedia" else "Tidak tersedia", style = MaterialTheme.typography.bodyMedium,
                            color = if (key in state.supportedEffects) SageSignal else BoneMuted)
                    }
                }
                if (state.hardwareBandCount in 1..19) Text("20 kontrol dipetakan ke ${state.hardwareBandCount} band hardware; L/R dirata-ratakan pada fallback.", color = BrassCaution)
                state.audioErrors.forEach { Text(it, color = MadderAlert, style = MaterialTheme.typography.bodySmall) }
            }
            ToggleRow("Profil otomatis (opsional)", "Mengikuti rute media Android saat identitas stabil tersedia. Rute aplikasi lain bisa berbeda. Tidak mengubah volume sistem.",
                state.autoProfiles, vm::toggleAutoProfiles)
            SectionHeading("Perangkat tersedia", "Terhubung tidak selalu berarti sedang dipakai. Identitas yang tidak stabil hanya cocok untuk pemilihan manual.")
            state.output.available.forEach { device ->
                ConsolePanel {
                    Text(device.label, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${device.kind} · ${if (device.stableIdentity) "identitas stabil" else "profil manual"}", color = BoneMuted)
                    OutlinedButton({ pickingKey = device.key }) { Text("Hubungkan preset") }
                }
            }
            if (state.output.available.isEmpty()) Text("Android belum melaporkan perangkat. Kamu tetap bisa menyimpan profil manual.", color = BoneMuted)
            OutlinedTextField(manualName, { manualName = it.take(40) }, Modifier.fillMaxWidth(), label = { Text("Nama profil manual, mis. TWS saya") }, singleLine = true)
            TextButton({
                vm.bindOutputProfile(OutputDevice("manual:${java.util.UUID.randomUUID()}", manualName.trim(), "Manual", false), state.activePresetId)
                manualName = ""
            }, enabled = manualName.isNotBlank() && state.presets.any { it.id == state.activePresetId } && !state.presetDirty) { Text("Hubungkan ke preset aktif") }
            if (state.presetDirty) Text("Simpan kurva yang diubah menjadi preset sebelum menghubungkannya.", color = BrassCaution, style = MaterialTheme.typography.bodySmall)
            state.outputProfiles.forEach { p ->
                ConsolePanel {
                    Text(p.deviceLabel, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(state.presets.find { it.id == p.presetId }?.name ?: "Preset sudah dihapus", color = BoneMuted)
                    FlowRow {
                        TextButton({ vm.applyOutputProfile(p.deviceKey) }, enabled = state.presets.any { it.id == p.presetId }) { Text("Terapkan manual") }
                        TextButton({ vm.removeOutputProfile(p.deviceKey) }) { Text("Lepaskan profil") }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
    pickingFor?.let { device ->
        ModalBottomSheet(onDismissRequest = { pickingKey = null }) {
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionHeading("Hubungkan preset", "Pilih preset untuk perangkat ini.")
                Text(device.label, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 460.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(state.presets, key = { it.id }) { p ->
                        TextButton({ vm.bindOutputProfile(device, p.id); pickingKey = null }, Modifier.fillMaxWidth()) {
                            Text(p.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}
